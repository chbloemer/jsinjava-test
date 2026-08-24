package dev.example.jsinjava;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.View;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring MVC {@link View} for one page of the Vue app. The controller opts
 * into client state via a single {@code "state"} model attribute: it is
 * serialized to JSON, passed to {@code SSR.render(pageName, state)} for the
 * server-side markup, and inlined into the page as
 * {@code window.__INITIAL_STATE__} so the client entry for the same page
 * hydrates against identical data.
 */
final class VueSsrView implements View {

    /** Model attribute holding the state that is shipped to the browser. */
    static final String STATE_ATTRIBUTE = "state";

    private final VueSsrRenderer renderer;
    private final ObjectMapper objectMapper;
    private final PageTemplate template;
    private final String pageName;

    VueSsrView(VueSsrRenderer renderer, ObjectMapper objectMapper, PageTemplate template,
            String pageName) {
        this.renderer = renderer;
        this.objectMapper = objectMapper;
        this.template = template;
        this.pageName = pageName;
    }

    @Override
    public String getContentType() {
        return MediaType.TEXT_HTML_VALUE;
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        String stateJson = objectMapper.writeValueAsString(stateOf(model));
        String appHtml = renderer.render(pageName, stateJson);
        String page = template.render(appHtml, escapeJsonForHtmlScript(stateJson), pageName);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(page);
    }

    // Only the dedicated state attribute reaches the browser. Everything else
    // in the model (framework attributes, additions from advice or security
    // integrations such as _csrf) stays server-side by default.
    private static Object stateOf(Map<String, ?> model) {
        Object state = model.get(STATE_ATTRIBUTE);
        return state != null ? state : Map.of();
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
}
