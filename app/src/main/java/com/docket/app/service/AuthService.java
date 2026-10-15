package com.docket.app.service;

import com.docket.app.dto.AuthResponseDTO;
import com.docket.app.dto.LoginRequestDTO;
import com.docket.app.dto.RegisterRequestDTO;
import com.docket.app.entity.User;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists.");
        }

        User user = new User(
                request.getFirstName().trim() + " " + request.getLastName().trim(),
                email,
                passwordEncoder.encode(request.getPassword())
        );

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists.", exception);
        }

        String token = jwtService.generateToken(user, null);

        return new AuthResponseDTO(token);
    }

    public AuthResponseDTO login(LoginRequestDTO loginRequestDTO) {

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(loginRequestDTO.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(
                loginRequestDTO.getPassword(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtService.generateToken(user, null);

        return new AuthResponseDTO(token);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
