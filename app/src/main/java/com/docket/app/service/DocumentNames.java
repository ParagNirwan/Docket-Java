package com.docket.app.service;

import com.docket.app.enums.FileStatus;
import com.docket.app.repository.DocumentRepository;

import java.util.UUID;

final class DocumentNames {
    private DocumentNames() {
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    // A name that's free in the folder, like "report (1).pdf" if "report.pdf" is taken.
    static String available(DocumentRepository documents, UUID tenantId, UUID folderId, String name) {
        String extension = extensionOf(name);
        String base = name.substring(0, name.length() - extension.length());
        String candidate = name;
        for (int i = 1; documents.existsByTenantIdAndFolderIdAndFileStatusAndOriginalFileNameIgnoreCase(
                tenantId, folderId, FileStatus.ACTIVE, candidate); i++) {
            candidate = base + " (" + i + ")" + extension;
        }
        return candidate;
    }
}
