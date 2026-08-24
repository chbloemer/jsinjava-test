package dev.example.jsinjava;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Renders the Vue app to an HTML string by running the Vite-built SSR bundle
 * inside the JVM via GraalJS.
 *
 * <p>A polyglot {@link Context} only permits single-threaded access, so this
 * renderer keeps a fixed pool of contexts and lets each render borrow one for
 * its duration. Up to pool-size renders run truly in parallel; further
 * requests queue on the pool. All contexts share one {@link Engine}, so the
 * SSR bundle is parsed and JIT-compiled once and the compiled code is reused
 * across the pool — additional contexts are cheap in both startup time and
 * memory.
 *
 * <p>A context whose render fails on the host side (engine trouble, internal
 * errors) is replaced by a freshly initialized one instead of being returned
 * to the pool, since it may be left in an unusable state. A failure inside the
 * page's own JavaScript ({@link GuestRenderException}) is an input problem,
 * not engine corruption — the context stays in the pool.
 */
@Component
public class VueSsrRenderer {

    private static final Duration BORROW_TIMEOUT = Duration.ofSeconds(10);

    private final int poolSize;
    private final AtomicBoolean closed = new AtomicBoolean();
    // Pool slots lost because a replacement context could not be created;
    // refilled lazily on the next render instead of shrinking the pool forever.
    private final AtomicInteger missingContexts = new AtomicInteger();
    private Engine engine;
    private Source polyfillsSource;
    private Source bundleSource;
    private BlockingQueue<RenderContext> pool;
    private Set<String> pageNames;

    VueSsrRenderer(
            @org.springframework.beans.factory.annotation.Value("${ssr.context-pool-size:0}")
            int configuredPoolSize) {
        this.poolSize = configuredPoolSize > 0
                ? configuredPoolSize
                : Runtime.getRuntime().availableProcessors();
    }

    @PostConstruct
    void init() {
        engine = Engine.newBuilder()
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        polyfillsSource = loadClasspathSource("ssr/ssr-polyfills.js");
        bundleSource = loadClasspathSource("ssr/server-bundle.iife.js");
        pool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.add(createRenderContext());
        }
        // init() is single-threaded, so peeking a pooled context is safe here.
        pageNames = pool.peek().pageNames();
    }

    /** True if the SSR bundle registered a page component under this name. */
    public boolean hasPage(String pageName) {
        return pageNames.contains(pageName);
    }

    public String render(String pageName, String initialStateJson) {
        if (closed.get()) {
            throw new IllegalStateException("SSR renderer is shut down");
        }
        refillPool();
        RenderContext renderContext = borrow();
        try {
            String html = renderContext.render(pageName, initialStateJson);
            release(renderContext);
            return html;
        } catch (GuestRenderException e) {
            // The page's own code failed for this input; the context is intact.
            release(renderContext);
            throw e;
        } catch (Throwable t) {
            replace(renderContext, t);
            throw t;
        }
    }

    private RenderContext borrow() {
        try {
            RenderContext renderContext = pool.poll(BORROW_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (renderContext == null) {
                throw new IllegalStateException(
                        "No SSR context available within " + BORROW_TIMEOUT);
            }
            return renderContext;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an SSR context", e);
        }
    }

    private void release(RenderContext renderContext) {
        if (closed.get()) {
            renderContext.closeQuietly();
            return;
        }
        pool.add(renderContext);
    }

    private void replace(RenderContext broken, Throwable renderFailure) {
        broken.closeQuietly();
        if (closed.get()) {
            return;
        }
        try {
            pool.add(createRenderContext());
        } catch (RuntimeException e) {
            // Keep the render failure primary; refillPool() retries later.
            missingContexts.incrementAndGet();
            renderFailure.addSuppressed(e);
        }
    }

    private void refillPool() {
        int missing;
        while ((missing = missingContexts.get()) > 0) {
            if (!missingContexts.compareAndSet(missing, missing - 1)) {
                continue;
            }
            try {
                pool.add(createRenderContext());
            } catch (RuntimeException e) {
                missingContexts.incrementAndGet();
                return;
            }
        }
    }

    private RenderContext createRenderContext() {
        Context context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .build();
        try {
            context.eval(polyfillsSource);
            context.eval(bundleSource);
            Value renderFn = context.getBindings("js").getMember("SSR").getMember("render");
            if (renderFn == null || !renderFn.canExecute()) {
                throw new IllegalStateException("SSR bundle did not expose SSR.render");
            }
            return new RenderContext(context, renderFn);
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    private Source loadClasspathSource(String path) {
        var stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Classpath resource not found: " + path);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return Source.newBuilder("js", reader, path).build();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + path, e);
        }
    }

    // Setting the closed flag first stops new renders from borrowing and makes
    // in-flight renders close (instead of re-add) their context on release.
    // Draining then waits for the contexts still in circulation; closing the
    // engine with cancelIfExecuting covers any straggler past the timeout.
    @PreDestroy
    void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        int expected = poolSize - missingContexts.get();
        for (int i = 0; i < expected; i++) {
            try {
                RenderContext renderContext =
                        pool.poll(BORROW_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                if (renderContext != null) {
                    renderContext.closeQuietly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (engine != null) {
            engine.close(true);
        }
    }

    /** One pooled context; used by at most one thread at a time via the pool. */
    private record RenderContext(Context context, Value renderFn) {

        String render(String pageName, String initialStateJson) {
            Value promise;
            try {
                promise = renderFn.execute(pageName, initialStateJson);
            } catch (PolyglotException e) {
                // Errors thrown before the first await surface synchronously.
                if (e.isGuestException() && !e.isInternalError()) {
                    throw new GuestRenderException("SSR render failed: " + e.getMessage(), e);
                }
                throw new IllegalStateException("SSR render failed: " + e.getMessage(), e);
            }
            var result = new AtomicReference<String>();
            var error = new AtomicReference<String>();
            promise.invokeMember("then",
                    (ProxyExecutable) args -> {
                        result.set(args[0].asString());
                        return null;
                    },
                    (ProxyExecutable) args -> {
                        error.set(args[0].toString());
                        return null;
                    });
            // GraalJS drains the microtask queue before invokeMember returns, so a
            // render without real async I/O has settled by now.
            if (error.get() != null) {
                // A rejected promise is the page's own code failing.
                throw new GuestRenderException("SSR render failed: " + error.get());
            }
            if (result.get() == null) {
                throw new IllegalStateException("SSR promise did not settle synchronously");
            }
            return result.get();
        }

        Set<String> pageNames() {
            Value names = context.getBindings("js").getMember("SSR").getMember("pageNames");
            if (names == null || !names.hasArrayElements()) {
                throw new IllegalStateException("SSR bundle did not expose SSR.pageNames");
            }
            var result = new HashSet<String>();
            for (long i = 0; i < names.getArraySize(); i++) {
                result.add(names.getArrayElement(i).asString());
            }
            return Set.copyOf(result);
        }

        void closeQuietly() {
            try {
                context.close(true);
            } catch (PolyglotException e) {
                // A cancelled in-flight execution surfaces here; the context is closed.
            }
        }
    }

    /**
     * The page's own JavaScript failed for the given input. The context is
     * still healthy, so the renderer returns it to the pool instead of paying
     * for a full replacement.
     */
    static final class GuestRenderException extends IllegalStateException {

        GuestRenderException(String message) {
            super(message);
        }

        GuestRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
