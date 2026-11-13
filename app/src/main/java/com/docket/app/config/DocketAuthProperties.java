package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "docket.auth")
public class DocketAuthProperties {

    public static final int DEFAULT_LOGIN_LIMIT_PER_IP = 20;
    public static final int DEFAULT_LOGIN_LIMIT_PER_ACCOUNT = 5;
    public static final int DEFAULT_LOCKOUT_FAILURES = 10;
    public static final int DEFAULT_LOCKOUT_WINDOW_MINUTES = 15;

    private int loginLimitPerIp = DEFAULT_LOGIN_LIMIT_PER_IP;
    private int loginLimitPerAccount = DEFAULT_LOGIN_LIMIT_PER_ACCOUNT;
    private int lockoutFailures = DEFAULT_LOCKOUT_FAILURES;
    private int lockoutWindowMinutes = DEFAULT_LOCKOUT_WINDOW_MINUTES;

    public Duration getLockoutWindow() {
        return Duration.ofMinutes(lockoutWindowMinutes);
    }
}
