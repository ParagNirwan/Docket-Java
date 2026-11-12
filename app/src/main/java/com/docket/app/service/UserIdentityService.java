package com.docket.app.service;

import com.docket.app.entity.User;
import com.docket.app.entity.UserIdentity;
import com.docket.app.repository.UserIdentityRepository;
import com.docket.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserIdentityService {
    private final UserIdentityRepository userIdentityRepository;
    private final UserRepository userRepository;

    public Optional<User> findUserByIdentity(String provider, String subject) {
        return userIdentityRepository.findByProviderAndSubject(provider, subject)
                .map(UserIdentity::getUserId)
                .flatMap(userRepository::findById);
    }

    @Transactional
    public UserIdentity linkIdentity(User user, String provider, String subject, String email) {
        Optional<UserIdentity> existing = userIdentityRepository.findByUserIdAndProvider(user.getId(), provider);
        UserIdentity identity = existing.orElseGet(UserIdentity::new);
        identity.setUserId(user.getId());
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setEmail(email);
        return userIdentityRepository.save(identity);
    }

    public boolean hasIdentity(UUID userId, String provider) {
        return userIdentityRepository.findByUserIdAndProvider(userId, provider).isPresent();
    }
}
