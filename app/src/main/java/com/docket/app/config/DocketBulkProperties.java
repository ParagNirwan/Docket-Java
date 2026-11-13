package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "docket.bulk")
public class DocketBulkProperties {

    public static final int DEFAULT_MAX_ROWS = 200;
    public static final int DEFAULT_MAX_CSV_LENGTH = 100_000;
    public static final int DEFAULT_INVITE_LIMIT_PER_HOUR = 5;

    private int maxRows = DEFAULT_MAX_ROWS;
    private int maxCsvLength = DEFAULT_MAX_CSV_LENGTH;
    private int inviteLimitPerHour = DEFAULT_INVITE_LIMIT_PER_HOUR;

    public Duration getInviteRateLimitWindow() {
        return Duration.ofHours(1);
    }
}
