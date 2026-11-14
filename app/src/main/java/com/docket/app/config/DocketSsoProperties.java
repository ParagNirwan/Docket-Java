package com.docket.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "docket.sso")
public class DocketSsoProperties {

    private Provider google = new Provider();
    private Provider microsoft = new Provider();

    @Data
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}