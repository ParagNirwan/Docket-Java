package com.docket.app.service.ai;

// A run of extracted text from one place in a document. Page is null for formats
// without pages, heading is null when no heading comes before the text.
public record TextSegment(String text, Integer page, String heading) {
}
