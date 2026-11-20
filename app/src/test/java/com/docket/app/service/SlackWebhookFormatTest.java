package com.docket.app.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackWebhookFormatTest {

    @Test
    void recognizesSlackIncomingWebhooksOnly() {
        assertTrue(WebhookService.isSlack("https://hooks.slack.com/services/T000/B000/XXXX"));
        assertFalse(WebhookService.isSlack("https://hooks.slack.com.evil.example/services/x"));
        assertFalse(WebhookService.isSlack("https://example.com/hooks.slack.com"));
    }

    @Test
    void sendsSlackAReadableTextMessage() {
        String message = WebhookService.slackMessage("document.uploaded",
                "{\"type\":\"document.uploaded\",\"data\":{\"targetType\":\"DOCUMENT\",\"targetId\":\"d1\"}}");

        assertTrue(message.startsWith("{\"text\":"));
        assertTrue(message.contains("A document was uploaded"));
        assertTrue(message.contains("document d1"));
    }
}
