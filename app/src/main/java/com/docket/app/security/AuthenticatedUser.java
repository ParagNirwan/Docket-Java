package com.docket.app.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

// The logged-in user, along with the token version the JWT filter checks against.
@Getter
public class AuthenticatedUser extends User {
    private final int tokenVersion;

    public AuthenticatedUser(
            String email,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities,
            int tokenVersion
    ) {
        super(email, passwordHash, authorities);
        this.tokenVersion = tokenVersion;
    }
}
