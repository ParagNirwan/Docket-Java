package com.docket.app.service.ai;

import com.docket.app.service.StorageService;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final StorageService storageService;
    private final Tika tika;

    // MIME types each strategy handles
    private static final Set<String> TIKA_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/csv"
    );

    private static final Set<String> VISION_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp"
    );

    private static final Set<String> NOT_PROCESSABLE_TYPES = Set.of(
        "image/svg+xml",
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed"
    );

    public TextExtractionService(StorageService storageService) {
        this.storageService = storageService;
        this.tika = new Tika();
    }

public ExtractionResult extract(String storedFileName, String contentType) {
    try {
        if (NOT_PROCESSABLE_TYPES.contains(contentType)) {
            log.info("Document not processable: {}", contentType);
            return ExtractionResult.notProcessable();
        }

        String text;

        if (TIKA_TYPES.contains(contentType)) {
            text = extractWithTika(storedFileName);
        } else if (VISION_TYPES.contains(contentType)) {
            text = extractWithVision(storedFileName);
        } else {
            log.warn("Unknown content type: {}", contentType);
            return ExtractionResult.notProcessable();
        }

        return ExtractionResult.success(text);

    } catch (Exception e) {
        log.error("Text extraction failed for {}: {}", storedFileName, e.getMessage(), e);
        return ExtractionResult.failed();
    }
}
private String extractWithTika(String storedFileName) throws Exception {
    try (InputStream is = storageService.retrieve(storedFileName)) {
        return tika.parseToString(is);
    }
}

private String extractWithVision(String storedFileName) {
    throw new UnsupportedOperationException("Cloud Vision OCR not yet implemented");
}


}
