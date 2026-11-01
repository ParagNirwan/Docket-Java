package com.docket.app.config;

import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@org.springframework.context.annotation.Profile("gcp")
public class VertexAiConfig {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.location}")
    private String location;

    @Bean
    public PredictionServiceClient predictionServiceClient() throws IOException {
        String endpoint = location + "-aiplatform.googleapis.com:443";
        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder()
                .setEndpoint(endpoint)
                .build();
        return PredictionServiceClient.create(settings);
    }

    @Bean
    public ImageAnnotatorClient imageAnnotatorClient(
            @Value("${docket.ai.ocr.endpoint}") String ocrEndpoint) throws IOException {
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setEndpoint(ocrEndpoint)
                .build();
        return ImageAnnotatorClient.create(settings);
    }

    @Bean
    public Storage ocrStorage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    @Bean
    public VertexAI vertexAI() throws IOException {
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation("global")
                .setApiEndpoint("aiplatform.googleapis.com")
                .build();
    }
}
