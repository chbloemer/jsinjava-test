package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// One renderer for the whole class: engine build + SSR bundle eval is the
// expensive part, and the concurrency test below proves instances are safe
// to share across renders.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VueSsrRendererTest {

    private static final int POOL_SIZE = 2;

    private VueSsrRenderer renderer;

    @BeforeAll
    void setUp() {
        renderer = new VueSsrRenderer(POOL_SIZE);
        renderer.init();
    }

    @AfterAll
    void tearDown() {
        renderer.shutdown();
    }

    @Test
    void rendersVueAppToHtml() {
        String html = renderer.render("home",
                "{\"greeting\":\"Hallo GraalJS\",\"items\":[\"eins\",\"zwei\"],\"count\":7}");

        assertThat(html).contains("Hallo GraalJS");
        assertThat(html).contains("eins");
        assertThat(html).contains("Clicked 7 times");
    }

    @Test
    void rendersDifferentPagesFromTheSameBundle() {
        String html = renderer.render("about",
                "{\"heading\":\"Über\",\"facts\":[\"f1\"],\"details\":\"d\"}");

        assertThat(html).contains("Über");
        assertThat(html).contains("f1");
        assertThat(html).doesNotContain("Clicked");
    }

    @Test
    void exposesTheRegisteredPageNames() {
        assertThat(renderer.hasPage("home")).isTrue();
        assertThat(renderer.hasPage("about")).isTrue();
        assertThat(renderer.hasPage("nope")).isFalse();
    }

    @Test
    void failsCleanlyForAnUnknownPage() {
        assertThatThrownBy(() -> renderer.render("nope", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown page: nope");
    }

    // A polyglot Context throws IllegalStateException on multi-threaded access,
    // so this passing with more threads than pooled contexts proves the pool
    // hands each concurrent render its own context — no global lock involved.
    @Test
    void rendersInParallelWithoutCrossingRequestState() throws Exception {
        int requests = 48;
        try (var executor = Executors.newFixedThreadPool(POOL_SIZE * 4)) {
            var renders = IntStream.range(0, requests)
                    .mapToObj(index -> executor.submit(() -> renderer.render("home",
                            "{\"greeting\":\"request-" + index
                                    + "\",\"items\":[],\"count\":" + index + "}")))
                    .toList();

            for (int index = 0; index < requests; index++) {
                assertThat(renders.get(index).get())
                        .contains("request-" + index)
                        .contains("Clicked " + index + " times");
            }
        }
    }

    // A render that fails inside JavaScript must not poison the pool: the
    // guest failure is an input problem, the context stays pooled, and
    // subsequent renders keep working at full pool capacity.
    @Test
    void recoversFromFailedRenders() throws Exception {
        for (int i = 0; i < POOL_SIZE + 1; i++) {
            assertThatThrownBy(() -> renderer.render("home", "this is not json"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SSR render failed");
        }

        try (var executor = Executors.newFixedThreadPool(POOL_SIZE * 2)) {
            var renders = IntStream.range(0, POOL_SIZE * 4)
                    .mapToObj(index -> executor.submit(() -> renderer.render("home",
                            "{\"greeting\":\"after-failure\",\"items\":[],\"count\":1}")))
                    .toList();
            for (var render : renders) {
                assertThat(render.get()).contains("after-failure");
            }
        }
    }
}
