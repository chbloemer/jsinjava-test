package dev.example.jsinjava;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import tools.jackson.databind.ObjectMapper;

/**
 * Resolves a view name to a {@link VueSsrView} when the SSR bundle registered
 * a page component under that name. Controllers stay ordinary Spring MVC
 * controllers — they return a view name plus model and never see the SSR
 * machinery. View name = page component = client entry, all rendered into the
 * shared HTML shell. Returns null for unknown names so other resolvers get
 * their turn.
 */
@Component
class VueSsrViewResolver implements ViewResolver {

    private final VueSsrRenderer renderer;
    private final ObjectMapper objectMapper;
    private final PageTemplate shell = PageTemplate.load("templates/shell.html");
    private final Map<String, View> viewsByName = new ConcurrentHashMap<>();

    VueSsrViewResolver(VueSsrRenderer renderer, ObjectMapper objectMapper) {
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Override
    public View resolveViewName(String viewName, Locale locale) {
        if (!renderer.hasPage(viewName)) {
            return null;
        }
        return viewsByName.computeIfAbsent(viewName, name ->
                new VueSsrView(renderer, objectMapper, shell, name));
    }
}
