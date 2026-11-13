package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "docket.ai")
public class DocketAiProperties {

    private Conversation conversation = new Conversation();
    private Query query = new Query();
    private Chunking chunking = new Chunking();
    private Hybrid hybrid = new Hybrid();
    private Summary summary = new Summary();
    private EmbeddingBatch embeddingBatch = new EmbeddingBatch();
    private RateLimit rateLimit = new RateLimit();
    private Rewrite rewrite = new Rewrite();

    @Data
    public static class Conversation {
        public static final int DEFAULT_HISTORY_MESSAGES = 6;
        public static final int DEFAULT_MAX_HISTORY_MESSAGE_LENGTH = 1000;

        private int historyMessages = DEFAULT_HISTORY_MESSAGES;
        private int maxHistoryMessageLength = DEFAULT_MAX_HISTORY_MESSAGE_LENGTH;
    }

    @Data
    public static class Query {
        public static final int DEFAULT_TOP_K_SPECIFIC = 5;
        public static final int DEFAULT_TOP_K_BROAD = 200;
        public static final int DEFAULT_SUMMARY_MAP_GROUP_SIZE = 10;
        public static final int DEFAULT_MAX_CHUNKS_PER_DOCUMENT = 3;
        public static final int DEFAULT_MAX_SUMMARY_SOURCES = 3;

        private int topKSpecific = DEFAULT_TOP_K_SPECIFIC;
        private int topKBroad = DEFAULT_TOP_K_BROAD;
        private int summaryMapGroupSize = DEFAULT_SUMMARY_MAP_GROUP_SIZE;
        private int maxChunksPerDocument = DEFAULT_MAX_CHUNKS_PER_DOCUMENT;
        private int maxSummarySources = DEFAULT_MAX_SUMMARY_SOURCES;
    }

    @Data
    public static class Chunking {
        public static final int DEFAULT_TOKENS_PER_CHUNK = 500;
        public static final int DEFAULT_OVERLAP_TOKENS = 50;
        public static final int DEFAULT_MAX_ATOMIC_TABLE_TOKENS = 1000;

        private int tokensPerChunk = DEFAULT_TOKENS_PER_CHUNK;
        private int overlapTokens = DEFAULT_OVERLAP_TOKENS;
        private int maxAtomicTableTokens = DEFAULT_MAX_ATOMIC_TABLE_TOKENS;
    }

    @Data
    public static class Hybrid {
        public static final int DEFAULT_CANDIDATES_PER_SEARCH = 30;
        public static final int DEFAULT_RRF_K = 60;
        public static final int DEFAULT_MIN_IDENTIFIER_LENGTH = 3;
        public static final int DEFAULT_MIN_DIGITS_ONLY_IDENTIFIER_LENGTH = 5;

        private int candidatesPerSearch = DEFAULT_CANDIDATES_PER_SEARCH;
        private int rrfK = DEFAULT_RRF_K;
        private int minIdentifierLength = DEFAULT_MIN_IDENTIFIER_LENGTH;
        private int minDigitsOnlyIdentifierLength = DEFAULT_MIN_DIGITS_ONLY_IDENTIFIER_LENGTH;
    }

    @Data
    public static class Summary {
        public static final int DEFAULT_SINGLE_PASS_CHARS = 48_000;
        public static final int DEFAULT_MAP_GROUP_SIZE = 10;
        public static final int DEFAULT_MAX_CHUNKS = 200;
        public static final int DEFAULT_MAX_LENGTH = 2_000;
        public static final int DEFAULT_MAX_KEY_POINTS = 7;
        public static final int DEFAULT_MAX_KEY_POINT_LENGTH = 300;

        private int singlePassChars = DEFAULT_SINGLE_PASS_CHARS;
        private int mapGroupSize = DEFAULT_MAP_GROUP_SIZE;
        private int maxChunks = DEFAULT_MAX_CHUNKS;
        private int maxLength = DEFAULT_MAX_LENGTH;
        private int maxKeyPoints = DEFAULT_MAX_KEY_POINTS;
        private int maxKeyPointLength = DEFAULT_MAX_KEY_POINT_LENGTH;
    }

    @Data
    public static class EmbeddingBatch {
        public static final int DEFAULT_MAX_BATCH_SIZE = 250;
        public static final int DEFAULT_MAX_BATCH_CHARS = 15_000;
        public static final int DEFAULT_MAX_RETRIES = 3;

        private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
        private int maxBatchChars = DEFAULT_MAX_BATCH_CHARS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
    }

    @Data
    public static class RateLimit {
        public static final int DEFAULT_MAX_REQUESTS = 10;
        public static final long DEFAULT_WINDOW_MILLIS = 60_000L;

        private int maxRequests = DEFAULT_MAX_REQUESTS;
        private long windowMillis = DEFAULT_WINDOW_MILLIS;
    }

    @Data
    public static class Rewrite {
        public static final int DEFAULT_MAX_REWRITE_LENGTH = 1000;

        private int maxRewriteLength = DEFAULT_MAX_REWRITE_LENGTH;
    }
}
