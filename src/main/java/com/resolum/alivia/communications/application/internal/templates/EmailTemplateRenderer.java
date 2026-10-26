package com.resolum.alivia.communications.application.internal.templates;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the email templates stored in {@code src/main/resources/templates/email/}.
 *
 * <p>Each email is a single {@code <name>.html} file. Placeholders use the
 * {@code {{variable}}} form and values are HTML-escaped. The plain-text alternative is
 * derived from the rendered HTML.</p>
 */
@Component
public class EmailTemplateRenderer {

    private static final String TEMPLATE_PATH = "templates/email/%s.html";

    /**
     * Renders a template.
     *
     * @param template  the template name, e.g. {@code verify-email}
     * @param variables the placeholder values, escaped before insertion
     * @return the rendered HTML
     * @throws IllegalStateException when the template does not exist
     */
    public String renderHtml(String template, Map<String, String> variables) {
        var content = load(TEMPLATE_PATH.formatted(template));
        for (var variable : variables.entrySet()) {
            content = content.replace("{{" + variable.getKey() + "}}", escapeHtml(variable.getValue()));
        }
        return content;
    }

    /**
     * Derives the plain-text alternative from rendered HTML.
     *
     * <p>{@code <br>} becomes a line break and the end of each paragraph, heading or table
     * cell becomes a blank line, so the text keeps the layout of the HTML.</p>
     *
     * @param html the rendered HTML
     * @return the plain-text body
     */
    public String toPlainText(String html) {
        var text = html
                .replaceAll("(?is)<head>.*?</head>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|h[1-6]|td)>", "\n\n")
                .replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(text).lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private static String load(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Email template could not be loaded: " + path, exception);
        }
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
