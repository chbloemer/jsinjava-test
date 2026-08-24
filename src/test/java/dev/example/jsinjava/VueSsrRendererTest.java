package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;

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

    private VueSsrRenderer renderer;

    @BeforeAll
    void setUp() {
        renderer = new VueSsrRenderer();
        renderer.init();
    }

    @AfterAll
    void tearDown() {
        renderer.shutdown();
    }

    @Test
    void rendersVueAppToHtml() {
        String html = renderer.render(
                "{\"greeting\":\"Hallo GraalJS\",\"items\":[\"eins\",\"zwei\"],\"count\":7}");

        assertThat(html).contains("Hallo GraalJS");
        assertThat(html).contains("eins");
        assertThat(html).contains("Clicked 7 times");
    }

    @Test
    void serializesConcurrentRendersWithoutCrossingRequestState() throws Exception {
        int requests = 12;
        try (var executor = Executors.newFixedThreadPool(4)) {
            var renders = IntStream.range(0, requests)
                    .mapToObj(index -> executor.submit(() -> renderer.render(
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
}
