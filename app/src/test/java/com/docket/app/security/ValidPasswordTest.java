package com.docket.app.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidPasswordTest {
    private final ValidPassword.Validator validator = new ValidPassword.Validator();

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }

    @Test
    void acceptsAPasswordMeetingTheRules() {
        assertTrue(valid("correct1horse"));
    }

    @Test
    void rejectsTooShortMissingLetterOrDigitAndNull() {
        assertFalse(valid("abc1234"));
        assertFalse(valid("12345678"));
        assertFalse(valid("abcdefgh"));
        assertFalse(valid(null));
    }

    @Test
    void limitCountsBytesBecauseThatsWhatBcryptTakes() {
        assertTrue(valid("a1" + "x".repeat(70)));
        assertFalse(valid("a1" + "x".repeat(71)));
        // Only 30 characters, but 3 bytes each.
        assertFalse(valid("a1" + "\u6f22".repeat(28)));
    }
}
