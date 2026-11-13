package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "docket.platform")
public class DocketPlatformProperties {

    private Archive archive = new Archive();
    private Audit audit = new Audit();
    private Search search = new Search();
    private Trash trash = new Trash();
    private Email email = new Email();
    private LegalHold legalHold = new LegalHold();
    private Retention retention = new Retention();
    private Notification notification = new Notification();

    @Data
    public static class Archive {
        public static final int DEFAULT_MAX_BULK = 100;

        private int maxBulk = DEFAULT_MAX_BULK;
    }

    @Data
    public static class Audit {
        public static final int DEFAULT_MAX_PAGE_SIZE = 100;
        public static final int DEFAULT_EXPORT_BATCH_SIZE = 1000;

        private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
        private int exportBatchSize = DEFAULT_EXPORT_BATCH_SIZE;
    }

    @Data
    public static class Search {
        public static final int DEFAULT_MIN_QUERY_LENGTH = 2;
        public static final int DEFAULT_MAX_QUERY_LENGTH = 200;
        public static final int DEFAULT_MAX_PAGE_SIZE = 50;

        private int minQueryLength = DEFAULT_MIN_QUERY_LENGTH;
        private int maxQueryLength = DEFAULT_MAX_QUERY_LENGTH;
        private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    }

    @Data
    public static class Trash {
        public static final int DEFAULT_MAX_PAGE_SIZE = 100;
        public static final int DEFAULT_EMPTY_BATCH_SIZE = 1000;

        private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
        private int emptyBatchSize = DEFAULT_EMPTY_BATCH_SIZE;
    }

    @Data
    public static class Email {
        public static final int DEFAULT_BATCH_SIZE = 20;
        public static final int DEFAULT_LEASE_MINUTES = 5;
        public static final int DEFAULT_MAX_ATTEMPTS = 6;

        private int batchSize = DEFAULT_BATCH_SIZE;
        private int leaseMinutes = DEFAULT_LEASE_MINUTES;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

        public Duration getLeaseDuration() {
            return Duration.ofMinutes(leaseMinutes);
        }
    }

    @Data
    public static class LegalHold {
        public static final int DEFAULT_SEARCH_PAGE_SIZE = 200;
        public static final int DEFAULT_MAX_SEARCH_DOCUMENTS = 10_000;

        private int searchPageSize = DEFAULT_SEARCH_PAGE_SIZE;
        private int maxSearchDocuments = DEFAULT_MAX_SEARCH_DOCUMENTS;
    }

    @Data
    public static class Retention {
        public static final int DEFAULT_MAX_REASON_LENGTH = 500;
        public static final int DEFAULT_EXPIRING_LIST_SIZE = 20;

        private int maxReasonLength = DEFAULT_MAX_REASON_LENGTH;
        private int expiringListSize = DEFAULT_EXPIRING_LIST_SIZE;
    }

    @Data
    public static class Notification {
        public static final int DEFAULT_DIGEST_LINES = 10;

        private int digestLines = DEFAULT_DIGEST_LINES;
    }
}
