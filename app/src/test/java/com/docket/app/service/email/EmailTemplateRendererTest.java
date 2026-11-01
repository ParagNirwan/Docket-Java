package com.docket.app.service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {
    private static final String LINK = "https://docket.test/x?token=abc";
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    private static final Map<String, Object> VARIABLES = Map.of(
            "name", "Ada",
            "link", LINK,
            "expiryMinutes", 30,
            "expiryHours", 48,
            "inviterName", "Grace",
            "tenantName", "Acme",
            "documentCount", 3,
            "expiryDate", "2026-12-01",
            "message", "Hello there");

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void everyTemplateRendersHtmlAndPlainTextWithItsLink(EmailTemplate template) {
        EmailTemplateRenderer.Rendered rendered = renderer.render(template, VARIABLES);

        assertTrue(rendered.html().contains("href=\"" + LINK + "\""), rendered.html());
        assertTrue(rendered.text().contains(LINK), rendered.text());
        assertFalse(rendered.text().contains("<"), rendered.text());
        assertFalse(rendered.text().contains("[("), rendered.text());
    }

    @Test
    void valuesAreEscapedInHtml() {
        Map<String, Object> variables = new java.util.HashMap<>(VARIABLES);
        variables.put("name", "<script>alert(1)</script>");

        String html = renderer.render(EmailTemplate.PASSWORD_RESET, variables).html();

        assertFalse(html.contains("<script>"), html);
    }
}
