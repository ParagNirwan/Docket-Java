package com.docket.app.service.ai;

import com.google.api.core.ApiFuture;
import com.google.cloud.discoveryengine.v1.RankRequest;
import com.google.cloud.discoveryengine.v1.RankResponse;
import com.google.cloud.discoveryengine.v1.RankServiceClient;
import com.google.cloud.discoveryengine.v1.RankServiceSettings;
import com.google.cloud.discoveryengine.v1.RankingRecord;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// The Vertex AI Ranking API. Kept apart from RerankService so that it can be replaced in tests.
@Service
@Profile("gcp")
public class RankClient {

    public record Record(String id, String title, String content) {
    }

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${docket.ai.rerank.location}")
    private String location;

    @Value("${docket.ai.rerank.model}")
    private String model;

    private volatile RankServiceClient client;

    // Scores each record against the query, from 0 to 1, by record id.
    // Gives up after the timeout, so that a slow ranker can't hold an answer back.
    public Map<String, Double> rank(String query, List<Record> records, Duration timeout) throws Exception {
        RankRequest request = RankRequest.newBuilder()
                .setRankingConfig("projects/%s/locations/%s/rankingConfigs/default_ranking_config"
                        .formatted(projectId, location))
                .setModel(model)
                .setQuery(query)
                .setTopN(records.size())
                .setIgnoreRecordDetailsInResponse(true)
                .addAllRecords(records.stream()
                        .map(record -> RankingRecord.newBuilder()
                                .setId(record.id())
                                .setTitle(record.title())
                                .setContent(record.content())
                                .build())
                        .toList())
                .build();

        ApiFuture<RankResponse> future = client().rankCallable().futureCall(request);
        try {
            RankResponse response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<String, Double> scores = new HashMap<>();
            response.getRecordsList().forEach(record -> scores.put(record.getId(), (double) record.getScore()));
            return scores;
        } finally {
            future.cancel(true);
        }
    }

    // Created on first use, so that a deployment with re-ranking switched off never needs the API
    private RankServiceClient client() throws IOException {
        RankServiceClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                RankServiceSettings.Builder settings = RankServiceSettings.newBuilder();
                if (!"global".equals(location)) {
                    settings.setEndpoint(location + "-discoveryengine.googleapis.com:443");
                }
                client = RankServiceClient.create(settings.build());
            }
            return client;
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
