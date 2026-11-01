package com.docket.app.service;

import com.docket.app.dto.LoginRequestDTO;
import com.docket.app.dto.RegisterRequestDTO;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.ClientRequestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AccountRecoveryService accountRecoveryService;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthRateLimiter authRateLimiter;

    @Mock
    private ClientRequestInfo clientRequestInfo;

    @InjectMocks
    private AuthService authService;

    private LoginRequestDTO loginRequest(String email) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword("plain-password");
        return request;
    }

    @Test
    void registerNormalizesEmailUsesBothNamesStoresEncodedPasswordAndSendsVerification() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("ADA@Example.com");
        request.setPassword("plain-password");

        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(jwtService.generateAccessToken(any(User.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("token");

        assertEquals("token", authService.register(request).getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("Ada Lovelace", userCaptor.getValue().getName());
        assertEquals("ada@example.com", userCaptor.getValue().getEmail());
        assertEquals("encoded-password", userCaptor.getValue().getPasswordHash());
        verify(accountRecoveryService).sendVerificationEmail(userCaptor.getValue());
    }

    @Test
    void registerStillSucceedsWhenVerificationEmailCannotBeQueued() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("ada@example.com");
        request.setPassword("plain-password");
        when(jwtService.generateAccessToken(any(User.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("token");
        doThrow(new IllegalStateException("template broken"))
                .when(accountRecoveryService).sendVerificationEmail(any(User.class));

        assertEquals("token", authService.register(request).getToken());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("ada@example.com");
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void loginFindsEmailCaseInsensitivelyStartsSessionAndClearsFailures() {
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        SessionService.IssuedSession session = new SessionService.IssuedSession("token", "refresh");

        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(sessionService.start(user)).thenReturn(session);

        assertEquals(session, authService.login(loginRequest("ADA@Example.com")));

        verify(authRateLimiter).reset(AuthService.lockoutKey("ada@example.com"));
        verify(auditService).recordAccountEvent(any(), eq(AuditAction.LOGIN), eq("USER"), any(), any());
    }

    @Test
    void loginWithUnknownEmailStillChecksAPasswordAndRecordsTheFailure() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
        // Even a matching dummy must not log anyone in.
        when(passwordEncoder.matches("plain-password", "dummy-hash")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest("missing@example.com"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(passwordEncoder).matches("plain-password", "dummy-hash");
        verify(auditService).recordAccountEvent(
                org.mockito.ArgumentMatchers.isNull(), eq(AuditAction.LOGIN_FAILED), eq("USER"),
                org.mockito.ArgumentMatchers.isNull(), eq(Map.of("email", "missing@example.com")));
        verify(sessionService, never()).start(any());
    }

    @Test
    void loginWithWrongPasswordIsRejectedAndCountsTowardLockout() {
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(false);
        when(authRateLimiter.increment(eq(AuthService.lockoutKey("ada@example.com")), any(Duration.class))).thenReturn(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest("ada@example.com"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(auditService, never()).recordAccountEvent(any(), eq(AuditAction.ACCOUNT_LOCKED), any(), any(), any());
    }

    @Test
    void reachingTheFailureLimitRecordsOneLockoutEvent() {
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(authRateLimiter.increment(any(), any(Duration.class))).thenReturn(10);

        assertThrows(ResponseStatusException.class, () -> authService.login(loginRequest("ada@example.com")));

        verify(auditService).recordAccountEvent(any(), eq(AuditAction.ACCOUNT_LOCKED), eq("USER"), any(), any());
    }

    @Test
    void lockedOutAccountIsRefusedBeforeThePasswordIsChecked() {
        doThrow(new RateLimitExceededException(60))
                .when(authRateLimiter).requireBelow(eq(AuthService.lockoutKey("ada@example.com")), eq(10), any(Duration.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest("ada@example.com"))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertEquals("60", exception.getHeaders().getFirst("Retry-After"));
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
