package com.docket.app.service;

import com.docket.app.dto.LoginRequestDTO;
import com.docket.app.dto.RegisterRequestDTO;
import com.docket.app.entity.User;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private AuthService authService;

    @Test
    void registerNormalizesEmailUsesBothNamesAndStoresEncodedPassword() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("ADA@Example.com");
        request.setPassword("plain-password");

        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(jwtService.generateToken(any(User.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("token");

        assertEquals("token", authService.register(request).getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("Ada Lovelace", userCaptor.getValue().getName());
        assertEquals("ada@example.com", userCaptor.getValue().getEmail());
        assertEquals("encoded-password", userCaptor.getValue().getPasswordHash());
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
    void loginFindsEmailCaseInsensitivelyAndChecksPassword() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("ADA@Example.com");
        request.setPassword("plain-password");
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-password");

        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user, null)).thenReturn("token");

        assertEquals("token", authService.login(request).getToken());
    }

    @Test
    void loginRejectsUnknownEmailAndWrongPassword() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("missing@example.com");
        request.setPassword("plain-password");
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException unknownUser = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unknownUser.getStatusCode());

        User user = new User("Ada Lovelace", "missing@example.com", "encoded-password");
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(false);

        ResponseStatusException wrongPassword = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
    }
}
