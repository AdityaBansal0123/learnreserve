package com.learnreserve.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(Long id, String email, String passwordHash, User.Role role) implements UserDetails {
    public static AccountPrincipal from(User user) {
        return new AccountPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole());
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public String toString() { return "AccountPrincipal[id=" + id + ", role=" + role + "]"; }
}
