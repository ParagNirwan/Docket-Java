package com.docket.app.service.ai;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private PredictionServiceClient client;

    private EmbeddingService embeddingService;
    private final List<List<Value>> requests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(client);
        ReflectionTestUtils.setField(embeddingService, "projectId", "project");
        ReflectionTestUtils.setField(embeddingService, "location", "europe-west3");
        ReflectionTestUtils.setField(embeddingService, "modelId", "text-embedding-005");

        when(client.predict(any(EndpointName.class), any(), any(Value.class))).thenAnswer(call -> {
            List<Value> instances = call.getArgument(1);
            requests.add(instances);
            PredictResponse.Builder response = PredictResponse.newBuilder();
            instances.forEach(instance -> response.addPredictions(prediction()));
            return response.build();
        });
    }

    private Value prediction() {
        Value values = Value.newBuilder().setListValue(
                ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(0.5))).build();
        Value embeddings = Value.newBuilder().setStructValue(Struct.newBuilder().putFields("values", values)).build();
        return Value.newBuilder().setStructValue(Struct.newBuilder().putFields("embeddings", embeddings)).build();
    }

    @Test
    void embed_sendsTaskTypeWithEachText() {
        embeddingService.embed(List.of("question"), EmbeddingTaskType.RETRIEVAL_QUERY);

        var fields = requests.get(0).get(0).getStructValue().getFieldsMap();
        assertEquals("question", fields.get("content").getStringValue());
        assertEquals("RETRIEVAL_QUERY", fields.get("task_type").getStringValue());
    }

    @Test
    void embed_splitsLargeDocumentsToStayUnderTheTokenLimit() {
        List<String> chunks = Collections.nCopies(20, "x".repeat(2000));

        List<float[]> result = embeddingService.embed(chunks, EmbeddingTaskType.RETRIEVAL_DOCUMENT);

        assertEquals(20, result.size());
        assertEquals(3, requests.size());
        requests.forEach(request -> assertTrue(request.size() <= 7));
    }

    @Test
    void embed_splitsByCountWhenTextsAreShort() {
        List<String> texts = Collections.nCopies(251, "x");

        assertEquals(251, embeddingService.embed(texts, EmbeddingTaskType.RETRIEVAL_DOCUMENT).size());
        assertEquals(250, requests.get(0).size());
        assertEquals(1, requests.get(1).size());
    }

    @Test
    void embed_textLargerThanTheBudgetStillGoesAlone() {
        List<float[]> result = embeddingService.embed(
                List.of("x".repeat(20_000), "y"), EmbeddingTaskType.RETRIEVAL_DOCUMENT);

        assertEquals(2, result.size());
        assertEquals(2, requests.size());
    }
}
