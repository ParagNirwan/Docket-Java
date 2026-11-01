package com.docket.app.service.email;

import lombok.Getter;

// Every email the app can send. templateName points at
// resources/templates/email/{name}.html and {name}.txt. The invite, retention and
// notification templates are ready for the features that will use them (#56, #47, #86).
@Getter
public enum EmailTemplate {
    INVITE("invite", "You've been invited to Docket"),
    PASSWORD_RESET("password-reset", "Reset your Docket password"),
    EMAIL_VERIFICATION("email-verification", "Verify your email for Docket"),
    RETENTION_EXPIRING("retention-expiring", "Documents are reaching the end of their retention period"),
    NOTIFICATION("notification", "New notification from Docket");

    private final String templateName;
    private final String defaultSubject;

    EmailTemplate(String templateName, String defaultSubject) {
        this.templateName = templateName;
        this.defaultSubject = defaultSubject;
    }
}
