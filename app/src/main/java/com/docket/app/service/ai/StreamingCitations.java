package com.docket.app.service.ai;

import java.util.function.Consumer;

// Renumbers citation markers while an answer is still being written, so that the text the
// user watches arrive is the text that gets stored. The model's pieces can end in the middle
// of a marker ("[1" then "2]"), so anything after an unclosed bracket waits for the rest.
final class StreamingCitations {

    // A marker is never this long. Past it, an open bracket is ordinary text.
    private static final int MAX_PENDING_MARKER = 40;

    private final int sourceCount;
    private final Consumer<String> onDelta;
    private final StringBuilder raw = new StringBuilder();
    private int emitted;

    StreamingCitations(int sourceCount, Consumer<String> onDelta) {
        this.sourceCount = sourceCount;
        this.onDelta = onDelta;
    }

    void accept(String piece) {
        raw.append(piece);
        int open = raw.lastIndexOf("[");
        boolean markerPending = open >= 0 && raw.indexOf("]", open) < 0 && raw.length() - open <= MAX_PENDING_MARKER;
        String settled = markerPending ? raw.substring(0, open) : raw.toString();
        emit(CitationResolver.resolveText(settled, sourceCount).answer());
    }

    CitationResolver.ResolvedAnswer finish() {
        CitationResolver.ResolvedAnswer resolved = CitationResolver.resolveText(raw.toString(), sourceCount);
        emit(resolved.answer());
        return resolved;
    }

    // Markers are numbered in the order they appear, so resolving a longer text only ever
    // adds to the end of what resolving a shorter one produced.
    private void emit(String resolvedSoFar) {
        if (resolvedSoFar.length() > emitted) {
            onDelta.accept(resolvedSoFar.substring(emitted));
            emitted = resolvedSoFar.length();
        }
    }
}
