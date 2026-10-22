package com.docket.app.config;

import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@org.springframework.context.annotation.Profile("gcp")
public class VertexAiConfig {

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
}
