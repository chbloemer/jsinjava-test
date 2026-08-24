package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VueSsrRendererTest {

    private VueSsrRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new VueSsrRenderer();
        renderer.init();
    }

    @AfterEach
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
        try (var executor = Executors.newFixedThreadPool(4)) {
            var renders = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(index -> executor.submit(() -> renderer.render(
                            "{\"greeting\":\"request-" + index
                                    + "\",\"items\":[],\"count\":" + index + "}")))
                    .toList();

            for (int index = 0; index < renders.size(); index++) {
                assertThat(renders.get(index).get())
                        .contains("request-" + index)
                        .contains("Clicked " + index + " times");
            }
        }
    }
}
