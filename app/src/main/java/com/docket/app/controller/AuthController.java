package com.docket.app.controller;

import com.docket.app.dto.AuthResponseDTO;
import com.docket.app.dto.ForgotPasswordRequestDTO;
import com.docket.app.dto.LoginRequestDTO;
import com.docket.app.dto.RegisterRequestDTO;
import com.docket.app.dto.ResetPasswordRequestDTO;
import com.docket.app.dto.VerifyEmailRequestDTO;
import com.docket.app.security.ClientRequestInfo;
import com.docket.app.service.AccountRecoveryService;
import com.docket.app.service.AuthRateLimiter;
import com.docket.app.service.AuthService;
import com.docket.app.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "docket_refresh";
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    private final AuthService authService;
    private final SessionService sessionService;
    private final AccountRecoveryService accountRecoveryService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientRequestInfo clientRequestInfo;

    @Value("${docket.auth.cookie-secure}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @Valid
            @RequestBody
            RegisterRequestDTO request
    ){
        limitByIp("register", 10);
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping(value = "/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest){
        return sessionResponse(authService.login(loginRequest));
    }

    // POST /api/auth/refresh
    // Swaps the refresh cookie for a new one and a new access token.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        limitByIp("refresh", 120);
        Optional<SessionService.IssuedSession> session = refreshToken == null
                ? Optional.empty()
                : sessionService.rotate(refreshToken);
        return session.map(this::sessionResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                        .build());
    }

    // POST /api/auth/logout
    // Identified by the cookie rather than the access token, so that someone whose
    // access token has already expired can still end their session.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            sessionService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .build();
    }

    // POST /api/auth/forgot-password
    // Answers the same whether or not the email is registered. Going over the
    // per-email limit is silent too: the extra emails just aren't sent.
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        limitByIp("forgot-password", 5);
        String accountKey = "forgot-password:account:" + request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (authRateLimiter.tryAcquire(accountKey, 3, FIFTEEN_MINUTES)) {
            accountRecoveryService.requestPasswordReset(request.getEmail());
        }
        return ResponseEntity.ok().build();
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        limitByIp("reset-password", 10);
        accountRecoveryService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    // POST /api/auth/verify-email
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO request) {
        limitByIp("verify-email", 20);
        accountRecoveryService.verifyEmail(request.getToken());
        return ResponseEntity.noContent().build();
    }

    // POST /api/auth/resend-verification
    // Needs a logged-in user, even though /api/auth/** is otherwise public.
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        authRateLimiter.check("resend-verification:account:" + authentication.getName(), 3, FIFTEEN_MINUTES);
        accountRecoveryService.resendVerificationEmail(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private void limitByIp(String endpoint, int limitPerMinute) {
        authRateLimiter.check(endpoint + ":ip:" + clientRequestInfo.ip(), limitPerMinute, ONE_MINUTE);
    }

    private ResponseEntity<AuthResponseDTO> sessionResponse(SessionService.IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), sessionService.refreshTtl()))
                .body(new AuthResponseDTO(session.accessToken()));
    }

    // httpOnly keeps the token away from JavaScript. SameSite=Strict means browsers won't
    // attach it to requests started by other sites. It's only ever sent to /api/auth.
    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
