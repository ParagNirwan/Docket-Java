package com.docket.app.service.ai;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Narrows an AI search. Every part is optional, and the parts that are set must all match.
// It can only narrow: tenant, status and tray visibility are always applied as well.
public record AiSearchScope(
        UUID trayId,
        // Includes every folder below it
        UUID folderId,
        @Size(max = 50, message = "At most 50 documents can be selected") List<UUID> documentIds,
        @Size(max = 20, message = "At most 20 content types can be selected") List<String> contentTypes,
        Instant uploadedAfter,
        Instant uploadedBefore,
        @Size(max = 20, message = "At most 20 tags can be selected") List<UUID> tagIds) {

    public static final AiSearchScope NONE = new AiSearchScope(null, null, null, null, null, null, null);

    public AiSearchScope {
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
        contentTypes = contentTypes == null ? List.of()
                : contentTypes.stream().filter(java.util.Objects::nonNull)
                        .map(type -> type.toLowerCase(Locale.ROOT)).toList();
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }

    public static AiSearchScope ofDocument(UUID documentId) {
        return new AiSearchScope(null, null, List.of(documentId), null, null, null, null);
    }

    public static AiSearchScope orNone(AiSearchScope scope) {
        return scope == null ? NONE : scope;
    }
}
