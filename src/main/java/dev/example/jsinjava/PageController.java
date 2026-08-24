package dev.example.jsinjava;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import tools.jackson.databind.ObjectMapper;

// Serves an HTML page, not a REST resource — hence @Controller + @ResponseBody.
@Controller
public class PageController {

    record InitialState(String greeting, List<String> items, int count) {
    }

    private final VueSsrRenderer renderer;
    private final ObjectMapper objectMapper;
    private final PageTemplate template;

    PageController(VueSsrRenderer renderer, ObjectMapper objectMapper) {
        this.renderer = renderer;
        this.objectMapper = objectMapper;
        this.template = PageTemplate.load();
    }

    @GetMapping(value = "/", produces = "text/html")
    @ResponseBody
    String index() {
        var state = new InitialState(
                "Hello from the JVM — rendered by Vue inside GraalJS",
                List.of("Spring Boot", "GraalJS", "Vue 3", "No Node at runtime"),
                0);
        String stateJson = objectMapper.writeValueAsString(state);
        String appHtml = renderer.render(stateJson);
        return template.render(appHtml, escapeJsonForHtmlScript(stateJson));
    }

    /**
     * Makes JSON safe to embed as a JavaScript expression in an HTML script
     * element. Escaping every less-than sign prevents case-insensitive
     * {@code </script>} end tags and HTML comment openers from being parsed as
     * markup; escaping ampersands keeps the payload inert if it ever ends up
     * in a non-script context. The line-separator escapes keep the result
     * valid on JavaScript engines that treat them specially.
     */
    static String escapeJsonForHtmlScript(String json) {
        return json
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(" ", "\\u2028")
                .replace(" ", "\\u2029");
    }

    /**
     * The page template split once at its two placeholders. Rendering is pure
     * concatenation, so inserted content is never rescanned — a payload that
     * happens to contain a placeholder cannot hijack the other slot, and a
     * missing placeholder fails at startup instead of silently not replacing.
     */
    private record PageTemplate(String beforeApp, String betweenAppAndState, String afterState) {

        static PageTemplate load() {
            String html = loadTemplateHtml();
            String[] atApp = splitOnce(html, "<!--ssr-outlet-->");
            String[] atState = splitOnce(atApp[1], "<!--state-outlet-->");
            return new PageTemplate(atApp[0], atState[0], atState[1]);
        }

        String render(String appHtml, String safeStateJson) {
            return beforeApp + appHtml + betweenAppAndState + safeStateJson + afterState;
        }

        private static String loadTemplateHtml() {
            try {
                return new ClassPathResource("templates/page.html")
                        .getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load page template", e);
            }
        }

        private static String[] splitOnce(String html, String placeholder) {
            int at = html.indexOf(placeholder);
            if (at < 0) {
                throw new IllegalStateException("Template placeholder missing: " + placeholder);
            }
            return new String[] {html.substring(0, at), html.substring(at + placeholder.length())};
        }
    }
}
