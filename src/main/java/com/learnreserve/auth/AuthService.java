package com.learnreserve.auth;

import com.learnreserve.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public AuthController.UserView register(AuthController.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists");
        }
        validatePassword(request.password());
        var user = users.saveAndFlush(new User(request.name().strip(), email,
                encoder.encode(request.password()), User.Role.LEARNER, clock.instant()));
        return AuthController.UserView.from(user);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalid("Password must have at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
