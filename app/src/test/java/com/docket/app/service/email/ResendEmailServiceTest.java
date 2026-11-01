package com.docket.app.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailServiceTest {
    private static final EmailMessage MESSAGE = new EmailMessage("ada@example.com", "Subject", "<p>Hi</p>", "Hi");

    private MockRestServiceServer server;
    private ResendEmailService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ResendEmailService(builder, "api-key", "Docket <no-reply@docket.test>");
    }

    @Test
    void sendsTheMessageWithTheApiKey() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer api-key"))
                .andExpect(jsonPath("$.to[0]").value("ada@example.com"))
                .andExpect(jsonPath("$.html").value("<p>Hi</p>"))
                .andExpect(jsonPath("$.text").value("Hi"))
                .andRespond(withSuccess());

        service.send(MESSAGE);

        server.verify();
    }

    @Test
    void rejectedRequestIsPermanent() {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertTrue(assertThrows(EmailSendException.class, () -> service.send(MESSAGE)).isPermanent());
    }

    @Test
    void throttlingAndServerErrorsAreRetried() {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertFalse(assertThrows(EmailSendException.class, () -> service.send(MESSAGE)).isPermanent());

        server.reset();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertFalse(assertThrows(EmailSendException.class, () -> service.send(MESSAGE)).isPermanent());
    }

    @Test
    void refusesToStartWithoutAnApiKey() {
        assertThrows(IllegalStateException.class, () -> new ResendEmailService(" ", "from"));
    }
}
