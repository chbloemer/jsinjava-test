package dev.example.jsinjava;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

@RestController
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
        // Escape </script> sequences so the inlined JSON cannot break out of its script tag.
        String safeStateJson = stateJson.replace("</", "<\\/");
        return template
                .replace("<!--ssr-outlet-->", appHtml)
                .replace("<!--state-outlet-->", safeStateJson);
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
