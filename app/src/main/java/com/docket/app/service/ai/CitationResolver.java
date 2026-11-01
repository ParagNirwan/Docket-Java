package com.docket.app.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Works out which sources the model really used. Markers are renumbered from 1 in the order
// they appear, so that [1] in the answer is always the first source returned to the user.
final class CitationResolver {

    private static final Pattern MARKER = Pattern.compile("\\[\\s*(\\d{1,4}(?:\\s*,\\s*\\d{1,4})*)\\s*]");

    // cited holds positions in the list of sources the model was given, starting at 0
    record ResolvedAnswer(String answer, List<Integer> cited) {
    }

    private CitationResolver() {
    }

    static ResolvedAnswer resolve(String modelOutput, int sourceCount, ObjectMapper objectMapper) {
        String answer = modelOutput.strip();
        List<Integer> declared = new ArrayList<>();

        try {
            JsonNode json = objectMapper.readTree(answer);
            if (json != null && json.isObject() && json.hasNonNull("answer")) {
                answer = json.get("answer").asText();
                json.path("citations").forEach(citation -> declared.add(citation.path("sourceId").asInt(0)));
            } else if (json != null && json.isContainerNode()) {
                throw new IllegalStateException("The model's answer has the wrong structure");
            }
        } catch (JsonProcessingException e) {
            // Unfinished JSON would show the user raw syntax. Anything else is a plain answer.
            if (answer.startsWith("{")) {
                throw new IllegalStateException("The model returned malformed JSON", e);
            }
        }

        // Maps the model's number to the new one. Numbers that match no source are dropped.
        Map<Integer, Integer> renumbered = new LinkedHashMap<>();

        StringBuilder rewritten = new StringBuilder();
        Matcher matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String number : matcher.group(1).split(",")) {
                int id = Integer.parseInt(number.strip());
                if (id >= 1 && id <= sourceCount) {
                    replacement.append('[').append(assign(renumbered, id)).append(']');
                }
            }
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(rewritten);

        // Declared but never marked in the text: add the marker, so that every source is visible
        for (int id : declared) {
            if (id >= 1 && id <= sourceCount && !renumbered.containsKey(id)) {
                rewritten.append(" [").append(assign(renumbered, id)).append(']');
            }
        }

        return new ResolvedAnswer(rewritten.toString(), renumbered.keySet().stream().map(id -> id - 1).toList());
    }

    private static int assign(Map<Integer, Integer> renumbered, int id) {
        return renumbered.computeIfAbsent(id, key -> renumbered.size() + 1);
    }
}
