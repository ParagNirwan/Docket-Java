package com.docket.app.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// What the model said about a document, after validation. The model's output is untrusted
// (the document it read may contain instructions), so everything is clipped to a fixed
// shape: a known type, a few short tags, a handful of short text fields, an ISO date.
public record ClassificationResult(
        String documentType,
        List<String> suggestedTags,
        Map<String, Object> fields,
        LocalDate keyDate
) {
    public static final Set<String> DOCUMENT_TYPES = Set.of(
            "contract", "invoice", "receipt", "policy", "correspondence", "report",
            "form", "legal_filing", "financial_statement", "presentation", "other");
    static final int MAX_TAGS = 5;
    static final int MAX_TAG_LENGTH = 40;
    static final int MAX_LIST_ITEMS = 10;
    static final int MAX_TEXT_LENGTH = 300;
    private static final List<String> FIELD_KEYS = List.of("parties", "amounts", "keyClauses", "effectiveDate", "expiryDate");

    public static ClassificationResult parse(String json, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json.strip());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Classification wasn't valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Classification wasn't a JSON object");
        }

        String type = root.path("documentType").asText("other").trim().toLowerCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            type = "other";
        }

        List<String> tags = new ArrayList<>();
        for (JsonNode tag : root.path("suggestedTags")) {
            String value = clean(tag.asText(""), MAX_TAG_LENGTH);
            if (!value.isEmpty() && tags.stream().noneMatch(value::equalsIgnoreCase)) {
                tags.add(value);
            }
            if (tags.size() == MAX_TAGS) break;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        JsonNode rawFields = root.path("fields");
        for (String key : FIELD_KEYS) {
            JsonNode value = rawFields.path(key);
            if (value.isArray()) {
                List<String> items = new ArrayList<>();
                for (JsonNode item : value) {
                    String text = clean(item.asText(""), MAX_TEXT_LENGTH);
                    if (!text.isEmpty()) items.add(text);
                    if (items.size() == MAX_LIST_ITEMS) break;
                }
                if (!items.isEmpty()) fields.put(key, items);
            } else if (value.isTextual()) {
                LocalDate date = parseDate(value.asText());
                if (key.endsWith("Date")) {
                    if (date != null) fields.put(key, date.toString());
                } else {
                    String text = clean(value.asText(), MAX_TEXT_LENGTH);
                    if (!text.isEmpty()) fields.put(key, text);
                }
            }
        }

        LocalDate keyDate = parseDate(root.path("keyDate").asText(null));
        return new ClassificationResult(type, List.copyOf(tags), fields, keyDate);
    }

    private static String clean(String value, int max) {
        String collapsed = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return collapsed.length() > max ? collapsed.substring(0, max).trim() : collapsed;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            // Reject nonsense like year 0003 or 9999.
            return date.getYear() >= 1900 && date.getYear() <= 2200 ? date : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
