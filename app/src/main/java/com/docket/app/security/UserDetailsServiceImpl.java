package com.docket.app.security;

import com.docket.app.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    // Keep token-version revocation checks current while avoiding a database lookup per request.
    private final Cache<String, UserDetails> userDetailsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userDetailsCache.get(email.toLowerCase(Locale.ROOT), key -> {
            com.docket.app.entity.User user = userRepository.findByEmailIgnoreCase(key)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return new AuthenticatedUser(
                    user.getEmail(),
                    user.getPasswordHash(),
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    user.getTokenVersion()
            );
        });
    }
}
