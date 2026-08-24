package dev.example.jsinjava;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

/**
 * The HTML shell split once at its three placeholders. Rendering is pure
 * concatenation, so inserted content is never rescanned — a payload that
 * happens to contain a placeholder cannot hijack another slot, and a missing
 * placeholder fails at load time instead of silently not replacing.
 */
record PageTemplate(String beforeApp, String betweenAppAndState,
        String betweenStateAndEntry, String afterEntry) {

    static PageTemplate load(String resourcePath) {
        String html = loadTemplateHtml(resourcePath);
        String[] atApp = splitOnce(html, "<!--ssr-outlet-->");
        String[] atState = splitOnce(atApp[1], "<!--state-outlet-->");
        String[] atEntry = splitOnce(atState[1], "<!--entry-outlet-->");
        return new PageTemplate(atApp[0], atState[0], atEntry[0], atEntry[1]);
    }

    String render(String appHtml, String safeStateJson, String entryName) {
        return beforeApp + appHtml
                + betweenAppAndState + safeStateJson
                + betweenStateAndEntry + entryName
                + afterEntry;
    }

    private static String loadTemplateHtml(String resourcePath) {
        try {
            return new ClassPathResource(resourcePath)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load page template " + resourcePath, e);
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
