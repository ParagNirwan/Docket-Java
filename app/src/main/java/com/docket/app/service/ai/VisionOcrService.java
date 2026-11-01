package com.docket.app.service.ai;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.vision.v1.AnnotateFileResponse;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.AsyncAnnotateFileRequest;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.GcsDestination;
import com.google.cloud.vision.v1.GcsSource;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageSource;
import com.google.cloud.vision.v1.InputConfig;
import com.google.cloud.vision.v1.OutputConfig;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Reads text from images and scanned PDFs with Cloud Vision. Files are passed by their
// gs:// address, so a 50 MB upload is never held in memory or sent over the request.
@Service
@Profile("gcp")
public class VisionOcrService {

    private static final Logger log = LoggerFactory.getLogger(VisionOcrService.class);
    private static final String OUTPUT_PREFIX = "ocr-output/";
    private static final int PAGES_PER_OUTPUT_FILE = 20;
    private static final Feature TEXT_DETECTION = Feature.newBuilder()
            .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
            .build();

    private final ImageAnnotatorClient client;
    private final Storage storage;
    private final String bucket;
    private final int timeoutMinutes;

    public VisionOcrService(
            ImageAnnotatorClient client,
            Storage storage,
            @Value("${docket.storage.gcs.bucket}") String bucket,
            @Value("${docket.ai.ocr.timeout-minutes}") int timeoutMinutes) {
        this.client = client;
        this.storage = storage;
        this.bucket = bucket;
        this.timeoutMinutes = timeoutMinutes;
    }

    public List<TextSegment> readImage(String storedFileName) {
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .setImage(Image.newBuilder()
                        .setSource(ImageSource.newBuilder().setGcsImageUri(gcsUri(storedFileName))))
                .addFeatures(TEXT_DETECTION)
                .build();

        AnnotateImageResponse response = client.batchAnnotateImages(List.of(request)).getResponses(0);
        if (response.hasError()) {
            throw new IllegalStateException("Vision rejected the image: " + response.getError().getMessage());
        }

        String text = response.getFullTextAnnotation().getText().strip();
        return text.isEmpty() ? List.of() : List.of(new TextSegment(text, null, null));
    }

    public List<TextSegment> readPdf(String storedFileName) throws Exception {
        String outputPrefix = OUTPUT_PREFIX + UUID.randomUUID() + "/";

        AsyncAnnotateFileRequest request = AsyncAnnotateFileRequest.newBuilder()
                .setInputConfig(InputConfig.newBuilder()
                        .setMimeType("application/pdf")
                        .setGcsSource(GcsSource.newBuilder().setUri(gcsUri(storedFileName))))
                .addFeatures(TEXT_DETECTION)
                .setOutputConfig(OutputConfig.newBuilder()
                        .setBatchSize(PAGES_PER_OUTPUT_FILE)
                        .setGcsDestination(GcsDestination.newBuilder().setUri(gcsUri(outputPrefix))))
                .build();

        try {
            client.asyncBatchAnnotateFilesAsync(List.of(request)).get(timeoutMinutes, TimeUnit.MINUTES);
            return readOutput(outputPrefix);
        } finally {
            deleteOutput(outputPrefix);
        }
    }

    private List<TextSegment> readOutput(String outputPrefix) throws Exception {
        List<AnnotateImageResponse> pages = new ArrayList<>();

        for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(outputPrefix)).iterateAll()) {
            AnnotateFileResponse.Builder file = AnnotateFileResponse.newBuilder();
            JsonFormat.parser().ignoringUnknownFields()
                    .merge(new String(blob.getContent(), StandardCharsets.UTF_8), file);
            pages.addAll(file.getResponsesList());
        }

        pages.sort(Comparator.comparingInt(page -> page.getContext().getPageNumber()));

        List<TextSegment> segments = new ArrayList<>();
        for (AnnotateImageResponse page : pages) {
            int pageNumber = page.getContext().getPageNumber();
            if (page.hasError()) {
                throw new IllegalStateException(
                        "Vision failed on page " + pageNumber + ": " + page.getError().getMessage());
            }
            String text = page.getFullTextAnnotation().getText().strip();
            if (!text.isEmpty()) {
                segments.add(new TextSegment(text, pageNumber, null));
            }
        }
        return segments;
    }

    // The output holds the document's text. A pod dying before this runs leaves it behind,
    // which is what the bucket lifecycle rule in docs/ai-search.md is for.
    private void deleteOutput(String outputPrefix) {
        try {
            for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(outputPrefix)).iterateAll()) {
                blob.delete();
            }
        } catch (RuntimeException e) {
            log.warn("Could not delete OCR output at {}: {}", outputPrefix, e.getMessage());
        }
    }

    private String gcsUri(String objectName) {
        return "gs://" + bucket + "/" + objectName;
    }
}
