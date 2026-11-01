package com.docket.app.service.email;

public record EmailMessage(String to, String subject, String htmlBody, String textBody) {
}
