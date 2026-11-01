package com.docket.app.service.ai;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.Value;
import com.google.protobuf.ListValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@org.springframework.context.annotation.Profile("gcp")
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int MAX_BATCH_SIZE = 250;
    // A request over 20,000 tokens is rejected. A character is at most about one token,
    // even in scripts like Chinese, so this stays under the limit for any language.
    private static final int MAX_BATCH_CHARS = 15_000;
    private static final int MAX_RETRIES = 3;

    private final PredictionServiceClient predictionClient;

    @org.springframework.beans.factory.annotation.Value("${gcp.project-id}")
    private String projectId;

    @org.springframework.beans.factory.annotation.Value("${gcp.location}")
    private String location;

    @org.springframework.beans.factory.annotation.Value("${docket.ai.embedding.model}")
    private String modelId;

    @Autowired
    public EmbeddingService(PredictionServiceClient predictionClient) {
        this.predictionClient = predictionClient;
    }

    public List<float[]> embed(List<String> texts, EmbeddingTaskType taskType) {
        List<float[]> allEmbeddings = new ArrayList<>();
        int batchStart = 0;
        int batchChars = 0;

        for (int i = 0; i < texts.size(); i++) {
            int length = texts.get(i).length();
            boolean batchFull = i - batchStart == MAX_BATCH_SIZE || batchChars + length > MAX_BATCH_CHARS;
            if (batchFull && i > batchStart) {
                allEmbeddings.addAll(embedBatchWithRetry(texts.subList(batchStart, i), taskType));
                batchStart = i;
                batchChars = 0;
            }
            batchChars += length;
        }
        if (batchStart < texts.size()) {
            allEmbeddings.addAll(embedBatchWithRetry(texts.subList(batchStart, texts.size()), taskType));
        }

        return allEmbeddings;
    }

    private List<float[]> embedBatchWithRetry(List<String> texts, EmbeddingTaskType taskType) {
        int attempt = 0;
        while (true) {
            try {
                return embedBatch(texts, taskType);
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("Embedding failed after " + MAX_RETRIES + " retries", e);
                }
                long backoff = (long) Math.pow(2, attempt) * 1000;
                log.warn("Embedding attempt {} failed, retrying in {}ms: {}", attempt, backoff, e.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during backoff", ie);
                }
            }
        }
    }

    private List<float[]> embedBatch(List<String> texts, EmbeddingTaskType taskType) {
        EndpointName endpoint = EndpointName.ofProjectLocationPublisherModelName(
                projectId, location, "google", modelId);

        List<Value> instances = texts.stream()
                .map(text -> Value.newBuilder()
                        .setStructValue(com.google.protobuf.Struct.newBuilder()
                                .putFields("content", Value.newBuilder().setStringValue(text).build())
                                .putFields("task_type", Value.newBuilder().setStringValue(taskType.name()).build())
                                .build())
                        .build())
                .toList();

        Value parameters = Value.newBuilder()
                .setStructValue(com.google.protobuf.Struct.newBuilder()
                        .putFields("outputDimensionality",
                                Value.newBuilder().setNumberValue(768).build())
                        .build())
                .build();

        PredictResponse response = predictionClient.predict(endpoint, instances, parameters);

        List<float[]> embeddings = new ArrayList<>();
        for (Value prediction : response.getPredictionsList()) {
            ListValue values = prediction.getStructValue()
                    .getFieldsOrThrow("embeddings")
                    .getStructValue()
                    .getFieldsOrThrow("values")
                    .getListValue();

            float[] vector = new float[values.getValuesCount()];
            for (int i = 0; i < values.getValuesCount(); i++) {
                vector[i] = (float) values.getValues(i).getNumberValue();
            }
            embeddings.add(vector);
        }

        return embeddings;
    }
}
