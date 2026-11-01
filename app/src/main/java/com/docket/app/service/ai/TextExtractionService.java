package com.docket.app.service.ai;

import com.docket.app.service.StorageService;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Service
@Profile("gcp")
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private static final String PDF = "application/pdf";

    private final StorageService storageService;
    private final VisionOcrService visionOcrService;
    private final Parser parser;

    // MIME types each strategy handles
    private static final Set<String> TIKA_TYPES = Set.of(
        PDF,
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
        "image/webp",
        "image/tiff"
    );

    private static final Set<String> NOT_PROCESSABLE_TYPES = Set.of(
        "image/svg+xml",
        "application/zip",
        "application/x-rar-compressed",
        "application/x-7z-compressed"
    );

    @Value("${docket.ai.extraction.max-chars}")
    private int maxChars = 2_000_000;

    @Value("${docket.ai.ocr.max-pages}")
    private int ocrMaxPages = 200;

    // Fewer visible characters per page than this means the PDF is a scan
    @Value("${docket.ai.ocr.min-chars-per-page}")
    private int ocrMinCharsPerPage = 50;

    public TextExtractionService(StorageService storageService, VisionOcrService visionOcrService) {
        this.storageService = storageService;
        this.visionOcrService = visionOcrService;
        this.parser = new AutoDetectParser();
    }

    public ExtractionResult extract(String storedFileName, String contentType) {
        try {
            if (NOT_PROCESSABLE_TYPES.contains(contentType)) {
                log.info("Document not processable: {}", contentType);
                return ExtractionResult.notProcessable();
            }

            if (VISION_TYPES.contains(contentType)) {
                return ExtractionResult.success(visionOcrService.readImage(storedFileName));
            }

            if (!TIKA_TYPES.contains(contentType)) {
                log.warn("Unknown content type: {}", contentType);
                return ExtractionResult.notProcessable();
            }

            StructuredTextHandler handler = extractWithTika(storedFileName, contentType);
            return looksScanned(handler)
                    ? extractScannedPdf(storedFileName, handler)
                    : ExtractionResult.success(handler.segments());

        } catch (Exception e) {
            log.error("Text extraction failed for {}: {}", storedFileName, e.getMessage(), e);
            return ExtractionResult.failed();
        }
    }

    private StructuredTextHandler extractWithTika(String storedFileName, String contentType) throws Exception {
        // Page divs only mean pages in a PDF. A spreadsheet uses them for its sheets.
        StructuredTextHandler handler = new StructuredTextHandler(PDF.equals(contentType), maxChars);

        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        try (InputStream is = storageService.retrieve(storedFileName);
             TikaInputStream tikaStream = TikaInputStream.get(is)) {
            parser.parse(tikaStream, handler, new Metadata(), context);
        } catch (Exception e) {
            if (!handler.limitReached()) {
                throw e;
            }
            log.warn("Indexing only the first {} characters of {}", maxChars, storedFileName);
        }
        return handler;
    }

    private boolean looksScanned(StructuredTextHandler handler) {
        return handler.pageCount() > 0
                && handler.visibleChars() / handler.pageCount() < ocrMinCharsPerPage;
    }

    private ExtractionResult extractScannedPdf(String storedFileName, StructuredTextHandler handler) throws Exception {
        // Vision can't be asked for only part of a file, so a longer one isn't sent at all.
        if (handler.pageCount() > ocrMaxPages) {
            List<TextSegment> embeddedText = handler.segments();
            log.warn("Skipping OCR for {}: {} pages is over the limit of {}",
                    storedFileName, handler.pageCount(), ocrMaxPages);
            return embeddedText.isEmpty() ? ExtractionResult.failed() : ExtractionResult.success(embeddedText);
        }
        return ExtractionResult.success(visionOcrService.readPdf(storedFileName));
    }
}
