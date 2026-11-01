package com.docket.app.service.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

// Sends through the Resend HTTP API. https://resend.com/docs/api-reference/emails/send-email
@Service
@ConditionalOnProperty(name = "docket.email.provider", havingValue = "resend")
public class ResendEmailService implements EmailService {
    private final RestClient restClient;
    private final String from;

    @Autowired
    public ResendEmailService(
            @Value("${docket.email.resend.api-key}") String apiKey,
            @Value("${docket.email.from}") String from
    ) {
        this(RestClient.builder(), apiKey, from);
    }

    ResendEmailService(RestClient.Builder restClientBuilder, String apiKey, String from) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is required when EMAIL_PROVIDER is resend");
        }
        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(message.to()),
                            "subject", message.subject(),
                            "html", message.htmlBody(),
                            "text", message.textBody()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 4xx means the request itself is wrong, except when we're being throttled.
            boolean permanent = e.getStatusCode().is4xxClientError()
                    && !e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
            throw new EmailSendException("Resend answered " + e.getStatusCode().value(), permanent, e);
        } catch (RestClientException e) {
            throw new EmailSendException("Could not reach Resend", false, e);
        }
    }
}
