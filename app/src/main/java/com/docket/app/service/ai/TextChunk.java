package com.docket.app.service.ai;

public record TextChunk(String text, Integer pageStart, Integer pageEnd, String heading, int charStart, int charEnd) {
}
