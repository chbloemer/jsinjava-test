package dev.example.jsinjava;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Plain Spring MVC controller: returns a view name plus model and knows
 * nothing about server-side rendering. The single {@code "state"} attribute
 * holds everything the page ships to the browser; the
 * {@link VueSsrViewResolver} maps each view name to the Vue page component of
 * the same name.
 */
@Controller
public class PageController {

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("state", Map.of(
                "greeting", "Hello from the JVM — rendered by Vue inside GraalJS",
                "items", List.of("Spring Boot", "GraalJS", "Vue 3", "No Node at runtime"),
                "count", 0));
        return "home";
    }

    @GetMapping("/about")
    String about(Model model) {
        model.addAttribute("state", Map.of(
                "heading", "About this experiment",
                "facts", List.of(
                        "Spring owns the routing — every page is a normal MVC controller",
                        "Each page is its own Vue component with its own client entry",
                        "The SSR bundle renders any registered page: SSR.render(name, state)"),
                "details",
                "The view name returned by the controller selects the page component, "
                        + "the client entry script, and nothing else — the SSR machinery "
                        + "lives entirely in the view layer."));
        return "about";
    }
}
