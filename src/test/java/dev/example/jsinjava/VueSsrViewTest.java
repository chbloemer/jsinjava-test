package dev.example.jsinjava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

class VueSsrViewTest {

    private static VueSsrView viewFor(String pageName, VueSsrRenderer renderer) {
        return new VueSsrView(renderer, new ObjectMapper(),
                PageTemplate.load("templates/shell.html"), pageName);
    }

    @Test
    void composesTheRenderedAppHydrationStateAndPageEntryIntoThePage() throws Exception {
        VueSsrRenderer renderer = mock(VueSsrRenderer.class);
        when(renderer.render(eq("home"), anyString()))
                .thenReturn("<main><h1>rendered</h1></main>");
        var response = new MockHttpServletResponse();

        viewFor("home", renderer).render(
                Map.of("state", Map.of("greeting", "hi", "items", List.of(), "count", 3)),
                new MockHttpServletRequest(), response);

        assertThat(response.getContentAsString())
                .contains("<div id=\"app\"><main><h1>rendered</h1></main></div>")
                .contains("window.__INITIAL_STATE__ = {")
                .contains("\"count\":3")
                .contains("<script type=\"module\" src=\"/assets/home.js\"></script>")
                .doesNotContain("<!--ssr-outlet-->", "<!--state-outlet-->", "<!--entry-outlet-->");
    }

    @Test
    void loadsTheClientEntryMatchingThePage() throws Exception {
        VueSsrRenderer renderer = mock(VueSsrRenderer.class);
        when(renderer.render(eq("about"), anyString())).thenReturn("<main/>");
        var response = new MockHttpServletResponse();

        viewFor("about", renderer).render(Map.of("state", Map.of("heading", "x")),
                new MockHttpServletRequest(), response);

        assertThat(response.getContentAsString())
                .contains("<script type=\"module\" src=\"/assets/about.js\"></script>");
    }

    @Test
    void shipsOnlyTheDedicatedStateAttributeToTheClient() throws Exception {
        VueSsrRenderer renderer = mock(VueSsrRenderer.class);
        when(renderer.render(anyString(), anyString())).thenReturn("<main/>");
        var response = new MockHttpServletResponse();

        viewFor("home", renderer).render(Map.of(
                        "state", Map.of("count", 1),
                        "_csrf", "secret-token",
                        "org.springframework.validation.BindingResult.state", "internal"),
                new MockHttpServletRequest(), response);

        assertThat(response.getContentAsString())
                .contains("\"count\":1")
                .doesNotContain("BindingResult", "internal", "_csrf", "secret-token");
    }

    @Test
    void escapesMarkupAndJavaScriptLineSeparatorsInInlineJson() {
        String hostileJson = "{\"value\":\"</ScRiPt><script>alert(1)</script><!--&\u2028\u2029\"}";

        String escaped = VueSsrView.escapeJsonForHtmlScript(hostileJson);

        assertThat(escaped)
                .doesNotContain("<", "&", "\u2028", "\u2029")
                .contains("\\u003c/ScRiPt", "\\u003cscript", "\\u003c!--", "\\u0026")
                .contains("\\u2028", "\\u2029");
    }
}
