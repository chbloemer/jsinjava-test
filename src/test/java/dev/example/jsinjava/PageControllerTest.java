package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PageControllerTest {

    @Test
    void composesTheRenderedAppAndHydrationStateIntoThePage() {
        VueSsrRenderer renderer = mock(VueSsrRenderer.class);
        when(renderer.render(anyString()))
                .thenReturn("<main><h1>rendered</h1></main>");

        String page = new PageController(renderer, new ObjectMapper()).index();

        assertThat(page)
                .contains("<div id=\"app\"><main><h1>rendered</h1></main></div>")
                .contains("window.__INITIAL_STATE__ = {")
                .contains("<script type=\"module\" src=\"/assets/app.js\"></script>")
                .doesNotContain("<!--ssr-outlet-->", "<!--state-outlet-->");
    }

    @Test
    void escapesMarkupAndJavaScriptLineSeparatorsInInlineJson() {
        String hostileJson = "{\"value\":\"</ScRiPt><script>alert(1)</script><!--&\u2028\u2029\"}";

        String escaped = PageController.escapeJsonForHtmlScript(hostileJson);

        assertThat(escaped)
                .doesNotContain("<", "&", "\u2028", "\u2029")
                .contains("\\u003c/ScRiPt", "\\u003cscript", "\\u003c!--", "\\u0026")
                .contains("\\u2028", "\\u2029");
    }
}
