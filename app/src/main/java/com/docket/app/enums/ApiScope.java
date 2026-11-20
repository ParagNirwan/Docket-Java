package com.docket.app.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// What an API key or service account may do. Machine credentials get nothing by default:
// each permission has to come from a scope listed here. Keep in step with the frontend's
// API_SCOPES list in services/developerService.ts.
@Getter
public enum ApiScope {
    DOCUMENTS_READ("documents:read", Set.of(Permission.TENANT_READ, Permission.DOCUMENTS_READ)),
    DOCUMENTS_WRITE("documents:write", Set.of(Permission.DOCUMENTS_CREATE, Permission.DOCUMENTS_UPDATE));

    private final String code;
    private final Set<Permission> permissions;

    ApiScope(String code, Set<Permission> permissions) {
        this.code = code;
        this.permissions = permissions;
    }

    public static boolean grants(Collection<String> scopes, Permission permission) {
        if (scopes == null || permission == null) {
            return false;
        }
        return scopes.stream()
                .map(ApiScope::fromCode)
                .anyMatch(scope -> scope != null && scope.permissions.contains(permission));
    }

    // Validates and normalizes what an admin typed or picked. Unknown scopes are rejected so a
    // typo can't silently produce a key with fewer rights than intended.
    public static List<String> normalize(Collection<String> scopes) {
        if (scopes == null) {
            return List.of();
        }
        return scopes.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .distinct()
                .peek(s -> {
                    if (fromCode(s) == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scope: " + s
                                + ". Allowed: " + Arrays.stream(values()).map(ApiScope::getCode).toList());
                    }
                })
                .toList();
    }

    private static ApiScope fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.code.equals(normalized)).findFirst().orElse(null);
    }
}
