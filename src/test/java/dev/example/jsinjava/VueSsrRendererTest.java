package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;

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
}
