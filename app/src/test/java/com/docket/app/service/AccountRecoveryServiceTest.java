package com.docket.app.service;

import com.docket.app.entity.AccountToken;
import com.docket.app.entity.User;
import com.docket.app.enums.AccountTokenPurpose;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.AccountTokenRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.docket.app.service.email.EmailOutboxService;
import com.docket.app.service.email.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {
    private static final String RAW_TOKEN = "raw-token";

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailOutboxService emailOutboxService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthRateLimiter authRateLimiter;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountRecoveryService service;

    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "https://docket.test");
        user = new User("Ada", "ada@example.com", "old-hash");
        user.setId(userId);
    }

    private AccountToken storedToken(AccountTokenPurpose purpose) {
        AccountToken token = new AccountToken();
        token.setUserId(userId);
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        when(accountTokenRepository.findByTokenHashAndPurpose(TokenHasher.hash(RAW_TOKEN), purpose))
                .thenReturn(Optional.of(token));
        return token;
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetRequestEmailsALinkAndStoresOnlyTheHash() {
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset(" ADA@example.com ");

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(emailOutboxService).enqueue(eq("ada@example.com"), eq(EmailTemplate.PASSWORD_RESET), variables.capture());
        String link = (String) variables.getValue().get("link");
        assertTrue(link.startsWith("https://docket.test/reset-password?token="));

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(saved.capture());
        String rawToken = link.substring(link.indexOf('=') + 1);
        assertEquals(TokenHasher.hash(rawToken), saved.getValue().getTokenHash());
        // Thirty minutes, as promised in the email.
        assertTrue(saved.getValue().getExpiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(31))));
    }

    @Test
    void resetRequestForUnknownEmailDoesNothing() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@example.com");

        verifyNoInteractions(accountTokenRepository, emailOutboxService, auditService);
    }

    @Test
    void resetChangesPasswordThenEndsEverySession() {
        AccountToken token = storedToken(AccountTokenPurpose.PASSWORD_RESET);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password1")).thenReturn("new-hash");

        service.resetPassword(RAW_TOKEN, "new-password1");

        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(token.getConsumedAt());
        assertNotNull(user.getEmailVerifiedAt());
        InOrder order = inOrder(userRepository, sessionService);
        order.verify(userRepository).save(user);
        order.verify(sessionService).revokeAllSessions(userId);
        verify(auditService).recordAccountEvent(userId, AuditAction.PASSWORD_RESET, "USER", userId, null);
    }

    @Test
    void usedExpiredAndUnknownTokensAreRefused() {
        AccountToken token = storedToken(AccountTokenPurpose.PASSWORD_RESET);

        token.setConsumedAt(Instant.now());
        assertRefused();

        token.setConsumedAt(null);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        assertRefused();

        when(accountTokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.empty());
        assertRefused();

        verifyNoInteractions(sessionService);
    }

    private void assertRefused() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(RAW_TOKEN, "new-password1"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void verificationMarksTheAddressAsVerified() {
        storedToken(AccountTokenPurpose.EMAIL_VERIFICATION);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.verifyEmail(RAW_TOKEN);

        assertNotNull(user.getEmailVerifiedAt());
        verify(auditService).recordAccountEvent(userId, AuditAction.EMAIL_VERIFIED, "USER", userId, null);
    }

    @Test
    void resendDoesNothingOnceVerified() {
        user.setEmailVerifiedAt(Instant.now());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        service.resendVerificationEmail("ada@example.com");

        verifyNoInteractions(emailOutboxService);
    }
}
