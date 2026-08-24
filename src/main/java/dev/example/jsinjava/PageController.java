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
    private final String template;

    PageController(VueSsrRenderer renderer, ObjectMapper objectMapper) {
        this.renderer = renderer;
        this.objectMapper = objectMapper;
        this.template = loadTemplate();
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
        // Escape sequences that would let the inlined JSON escape its script tag:
        // "</" covers "</script>", "<!--" would switch the parser into the
        // script-data-escaped state. Both stay valid inside JS string literals.
        String safeStateJson = stateJson
                .replace("</", "<\\/")
                .replace("<!--", "<\\!--");
        // State first: appHtml is rendered from arbitrary data and could
        // otherwise contain the state placeholder itself.
        return template
                .replace("<!--state-outlet-->", safeStateJson)
                .replace("<!--ssr-outlet-->", appHtml);
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource("templates/page.html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load page template", e);
        }
    }
}
