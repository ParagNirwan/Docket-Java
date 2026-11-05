package com.docket.app.service;

import com.docket.app.dto.TwoFactorEnableResponseDTO;
import com.docket.app.dto.TwoFactorSetupResponseDTO;
import com.docket.app.dto.TwoFactorStatusResponseDTO;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TotpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TwoFactorService twoFactorService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Ada Lovelace", "ada@example.com", "encoded-pw");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void setup_generatesSecretAndQrUri() {
        TwoFactorSetupResponseDTO setup = twoFactorService.setup(authentication);

        assertNotNull(setup.secret());
        assertTrue(setup.qrUri().startsWith("otpauth://totp/"));
        assertEquals(setup.secret(), user.getTwoFactorSecret());
        verify(userRepository).save(user);
    }

    @Test
    void getStatus_reflectsState() {
        user.setTwoFactorEnabled(false);
        TwoFactorStatusResponseDTO status = twoFactorService.getStatus(authentication);
        assertFalse(status.enabled());

        user.setTwoFactorEnabled(true);
        status = twoFactorService.getStatus(authentication);
        assertTrue(status.enabled());
    }

    @Test
    void disable_requiresValidPassword() {
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("secret");
        when(passwordEncoder.matches("wrong-pw", "encoded-pw")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> twoFactorService.disable("wrong-pw", authentication));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(user.isTwoFactorEnabled());
    }

    @Test
    void disable_succeedsWithCorrectPassword() {
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("secret");
        when(passwordEncoder.matches("correct-pw", "encoded-pw")).thenReturn(true);

        twoFactorService.disable("correct-pw", authentication);

        assertFalse(user.isTwoFactorEnabled());
        assertNull(user.getTwoFactorSecret());
        verify(userRepository).save(user);
        verify(auditService).record(eq((UUID) null), eq(user.getId()), eq(AuditAction.TWO_FACTOR_DISABLED), eq("USER"), eq(user.getId()), any());
    }
}

