package com.docket.app.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditServiceTest {

    @Test
    void csvQuotesValuesAndDoublesQuotes() {
        assertEquals("\"a,b\"", AuditService.csv("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", AuditService.csv("say \"hi\""));
        assertEquals("", AuditService.csv(null));
    }

    @Test
    void csvDefusesSpreadsheetFormulas() {
        assertEquals("\"'=HYPERLINK(1)\"", AuditService.csv("=HYPERLINK(1)"));
        assertEquals("\"'+1\"", AuditService.csv("+1"));
        assertEquals("\"'-1\"", AuditService.csv("-1"));
        assertEquals("\"'@x\"", AuditService.csv("@x"));
    }
}
