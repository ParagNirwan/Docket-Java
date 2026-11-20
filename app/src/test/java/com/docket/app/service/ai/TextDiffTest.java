package com.docket.app.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDiffTest {

    @Test
    void marksChangedLinesAsRemovedThenAdded() {
        TextDiff.Result r = TextDiff.diff(
                "Term: 12 months\nRent: $4,000\nNotice: 60 days",
                "Term: 12 months\nRent: $4,500\nNotice: 60 days");

        assertEquals(List.of(
                new TextDiff.Op(TextDiff.Kind.EQUAL, "Term: 12 months"),
                new TextDiff.Op(TextDiff.Kind.REMOVED, "Rent: $4,000"),
                new TextDiff.Op(TextDiff.Kind.ADDED, "Rent: $4,500"),
                new TextDiff.Op(TextDiff.Kind.EQUAL, "Notice: 60 days")), r.ops());
        assertEquals(1, r.added());
        assertEquals(1, r.removed());
        assertFalse(r.truncated());
    }

    @Test
    void handlesInsertionsDeletionsAndEmptySides() {
        TextDiff.Result inserted = TextDiff.diff("a\nc", "a\nb\nc");
        assertEquals(1, inserted.added());
        assertEquals(0, inserted.removed());

        TextDiff.Result fromNothing = TextDiff.diff("", "x\ny");
        assertEquals(List.of(new TextDiff.Op(TextDiff.Kind.ADDED, "x\ny")), fromNothing.ops());
    }

    @Test
    void capsVeryLongDocuments() {
        String big = "line\n".repeat(TextDiff.MAX_LINES + 50);
        assertTrue(TextDiff.diff(big, big + "extra").truncated());
    }

    @Test
    void summaryPromptOnlyCarriesTheChangedLines() {
        String prompt = DocumentCompareService.summaryPrompt(TextDiff.diff("same\nold", "same\nnew"));
        assertTrue(prompt.contains("- old"));
        assertTrue(prompt.contains("+ new"));
        assertFalse(prompt.contains("same"));
    }
}
