package dev.example.jsinjava;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Renders the Vue app to an HTML string by running the Vite-built SSR bundle
 * inside the JVM via GraalJS. A single Context is shared and guarded with
 * synchronized — good enough for this experiment; production code would use a
 * Context pool over the shared Engine.
 */
@Component
public class VueSsrRenderer {

    private Engine engine;
    private Context context;
    private Value renderFn;

    @PostConstruct
    void init() {
        engine = Engine.newBuilder()
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.NONE)
                .build();
        context.eval(loadClasspathSource("ssr/ssr-polyfills.js"));
        context.eval(loadClasspathSource("ssr/server-bundle.iife.js"));
        renderFn = context.getBindings("js").getMember("SSR").getMember("render");
        if (renderFn == null || !renderFn.canExecute()) {
            throw new IllegalStateException("SSR bundle did not expose SSR.render");
        }
    }

    public synchronized String render(String initialStateJson) {
        Value promise = renderFn.execute(initialStateJson);
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
            throw new IllegalStateException("SSR render failed: " + error.get());
        }
        if (result.get() == null) {
            throw new IllegalStateException("SSR promise did not settle synchronously");
        }
        return result.get();
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

    // Same monitor as render() so the context cannot be closed mid-render.
    @PreDestroy
    synchronized void shutdown() {
        if (context != null) {
            context.close();
        }
        if (engine != null) {
            engine.close();
        }
    }
}
