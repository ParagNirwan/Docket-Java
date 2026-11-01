package com.docket.app.service.ai;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.AsyncAnnotateFileRequest;
import com.google.cloud.vision.v1.AsyncBatchAnnotateFilesResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.OperationMetadata;
import com.google.cloud.vision.v1.TextAnnotation;
import com.google.rpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionOcrServiceTest {

    @Mock private ImageAnnotatorClient client;
    @Mock private Storage storage;
    @Mock private OperationFuture<AsyncBatchAnnotateFilesResponse, OperationMetadata> operation;
    @Mock private Page<Blob> outputPage;

    private VisionOcrService service;

    @BeforeEach
    void setUp() {
        service = new VisionOcrService(client, storage, "bucket", 10);
    }

    private void imageResponse(AnnotateImageResponse response) {
        when(client.batchAnnotateImages(anyList()))
                .thenReturn(BatchAnnotateImagesResponse.newBuilder().addResponses(response).build());
    }

    private Blob outputFile(String json) {
        Blob blob = mock(Blob.class);
        when(blob.getContent()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return blob;
    }

    private String page(int number, String text) {
        return """
                {"context": {"pageNumber": %d}, "fullTextAnnotation": {"text": "%s", "unknownField": 1}}
                """.formatted(number, text);
    }

    @Test
    void readImage_returnsTextWithoutPage() {
        imageResponse(AnnotateImageResponse.newBuilder()
                .setFullTextAnnotation(TextAnnotation.newBuilder().setText("Total 12.50\n")).build());

        assertEquals(List.of(new TextSegment("Total 12.50", null, null)), service.readImage("receipt.png"));
    }

    @Test
    void readImage_noText_returnsNothing() {
        imageResponse(AnnotateImageResponse.getDefaultInstance());

        assertTrue(service.readImage("photo.png").isEmpty());
    }

    @Test
    void readImage_visionError_throws() {
        imageResponse(AnnotateImageResponse.newBuilder()
                .setError(Status.newBuilder().setMessage("Bad image")).build());

        assertThrows(IllegalStateException.class, () -> service.readImage("photo.png"));
    }

    @Test
    void readPdf_returnsPagesInOrderAndDeletesTheOutput() throws Exception {
        when(client.asyncBatchAnnotateFilesAsync(anyList())).thenReturn(operation);
        Blob second = outputFile("{\"responses\": [" + page(3, "Third") + "]}");
        Blob first = outputFile("{\"responses\": [" + page(1, "First") + "," + page(2, " ") + "]}");
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class))).thenReturn(outputPage);
        when(outputPage.iterateAll()).thenReturn(List.of(second, first));

        List<TextSegment> result = service.readPdf("scan.pdf");

        assertEquals(List.of(new TextSegment("First", 1, null), new TextSegment("Third", 3, null)), result);
        verify(operation).get(10, TimeUnit.MINUTES);
        verify(first).delete();
        verify(second).delete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AsyncAnnotateFileRequest>> request = ArgumentCaptor.forClass(List.class);
        verify(client).asyncBatchAnnotateFilesAsync(request.capture());
        AsyncAnnotateFileRequest sent = request.getValue().get(0);
        assertEquals("gs://bucket/scan.pdf", sent.getInputConfig().getGcsSource().getUri());
        assertEquals("application/pdf", sent.getInputConfig().getMimeType());
        assertEquals(Feature.Type.DOCUMENT_TEXT_DETECTION, sent.getFeatures(0).getType());
        assertTrue(sent.getOutputConfig().getGcsDestination().getUri().startsWith("gs://bucket/ocr-output/"));
    }

    @Test
    void readPdf_pageError_throwsAndStillDeletesTheOutput() throws Exception {
        when(client.asyncBatchAnnotateFilesAsync(anyList())).thenReturn(operation);
        Blob output = outputFile("""
                {"responses": [{"context": {"pageNumber": 1}, "error": {"message": "Bad page"}}]}
                """);
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class))).thenReturn(outputPage);
        when(outputPage.iterateAll()).thenReturn(List.of(output));

        assertThrows(IllegalStateException.class, () -> service.readPdf("scan.pdf"));

        verify(output).delete();
    }

    @Test
    void readPdf_operationTimesOut_throws() throws Exception {
        when(client.asyncBatchAnnotateFilesAsync(anyList())).thenReturn(operation);
        when(operation.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        when(storage.list(eq("bucket"), any(Storage.BlobListOption.class))).thenReturn(outputPage);
        when(outputPage.iterateAll()).thenReturn(List.of());

        assertThrows(TimeoutException.class, () -> service.readPdf("scan.pdf"));
    }
}
