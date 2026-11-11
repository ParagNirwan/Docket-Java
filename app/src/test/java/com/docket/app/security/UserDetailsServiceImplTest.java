package com.docket.app.security;

import com.docket.app.entity.User;
import com.docket.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsername_cachesUserDetailsForShortTtl() {
        User user = new User("Ada Lovelace", "ada@example.com", "encoded-pw");
        user.setTokenVersion(3);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        UserDetails first = userDetailsService.loadUserByUsername("ada@example.com");
        UserDetails second = userDetailsService.loadUserByUsername("ADA@example.com");

        assertSame(first, second);
        verify(userRepository).findByEmailIgnoreCase("ada@example.com");
        verifyNoMoreInteractions(userRepository);
    }
}
