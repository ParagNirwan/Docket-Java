package com.docket.app.service;

import com.docket.app.dto.TwoFactorEnableResponseDTO;
import com.docket.app.dto.TwoFactorSetupResponseDTO;
import com.docket.app.dto.TwoFactorStatusResponseDTO;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.TokenHasher;
import com.docket.app.security.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TwoFactorService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public TwoFactorSetupResponseDTO setup(Authentication authentication) {
        User user = currentUser(authentication);
        String secret = TotpUtil.generateSecret();
        // Temporarily store secret on user so verify can check it, or user can keep it in session
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        String qrUri = TotpUtil.getQrUri(secret, user.getEmail());
        return new TwoFactorSetupResponseDTO(secret, qrUri);
    }

    @Transactional
    public TwoFactorEnableResponseDTO enable(String code, Authentication authentication) {
        User user = currentUser(authentication);
        if (user.getTwoFactorSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Must initiate 2FA setup first");
        }

        if (!TotpUtil.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 6-digit verification code");
        }

        user.setTwoFactorEnabled(true);
        List<String> rawBackupCodes = TotpUtil.generateBackupCodes(8);
        List<String> hashedBackupCodes = rawBackupCodes.stream().map(TokenHasher::hash).toList();
        user.setTwoFactorBackupCodes(String.join(",", hashedBackupCodes));
        userRepository.save(user);

        auditService.record((UUID) null, user.getId(), AuditAction.TWO_FACTOR_ENABLED, "USER", user.getId(), java.util.Map.of());

        return new TwoFactorEnableResponseDTO(rawBackupCodes);
    }

    @Transactional
    public void disable(String password, Authentication authentication) {
        User user = currentUser(authentication);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorBackupCodes(null);
        userRepository.save(user);

        auditService.record((UUID) null, user.getId(), AuditAction.TWO_FACTOR_DISABLED, "USER", user.getId(), java.util.Map.of());
    }

    public TwoFactorStatusResponseDTO getStatus(Authentication authentication) {
        User user = currentUser(authentication);
        return new TwoFactorStatusResponseDTO(user.isTwoFactorEnabled());
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

