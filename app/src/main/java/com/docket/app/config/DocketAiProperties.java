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

    // Legacy/pre-refactor sub-namespaces still read via @Value in AI services.
    // Mapped here so @ConfigurationPropertiesScan doesn't throw on unknown properties.
    private Embedding embedding = new Embedding();
    private Chunk chunk = new Chunk();
    private Gemini gemini = new Gemini();
    private Reindex reindex = new Reindex();
    private Retrieval retrieval = new Retrieval();
    private Extraction extraction = new Extraction();
    private Ocr ocr = new Ocr();
    private Enrichment enrichment = new Enrichment();
    private Rerank rerank = new Rerank();

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

        private boolean enabled = true;
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

    // ── Legacy sub-namespaces (still used via @Value) ──────────────────────────

    @Data
    public static class Embedding {
        private String model = "text-embedding-005";
        private int dimensions = 768;
        private int version = 1;
    }

    @Data
    public static class Chunk {
        private int size = 2000;
        private int overlap = 200;
    }

    @Data
    public static class Gemini {
        private String model = "gemini-3.5-flash-lite";
        private String rewriteModel = "gemini-3.5-flash-lite";
    }

    @Data
    public static class Reindex {
        private boolean enabled = true;
        private int batchSize = 10;
        private long intervalMs = 60_000L;
        private int maxAttempts = 3;
    }

    @Data
    public static class Retrieval {
        private double maxDistance = 0.45;
        private double fallbackMaxDistance = 0.6;
        private int minResults = 1;
        private boolean hybrid = true;
    }

    @Data
    public static class Extraction {
        private int maxChars = 2_000_000;
    }

    @Data
    public static class Ocr {
        private String endpoint = "vision.googleapis.com:443";
        private int maxPages = 200;
        private int minCharsPerPage = 50;
        private int timeoutMinutes = 10;
    }

    @Data
    public static class Enrichment {
        private LlmContext llmContext = new LlmContext();

        @Data
        public static class LlmContext {
            private boolean enabled = false;
            private int maxDocumentChars = 40_000;
        }
    }

    @Data
    public static class Rerank {
        private boolean enabled = false;
        private String model = "semantic-ranker-default@latest";
        private String location = "global";
        private int timeoutMs = 500;
        private int maxCandidates = 30;
        private double minScore = 0.3;
    }
}
