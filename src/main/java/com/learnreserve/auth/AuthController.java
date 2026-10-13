package com.learnreserve.auth;

import com.learnreserve.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Accounts", description = "Register a learner, then authenticate using HTTP Basic")
public class AuthController {
    private final AuthService service;
    private final UserRepository users;

    public AuthController(AuthService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @io.swagger.v3.oas.annotations.media.Schema(format = "password", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
            @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "RegisterRequest[name=" + name + ", email=" + email + ", password=[REDACTED]]"; }
    }

    public record UserView(Long id, String name, String email, User.Role role) {
        public static UserView from(User user) {
            return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register a learner account", description = "Public registration always creates a LEARNER; roles cannot be supplied.")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(service.register(request));
    }

    @GetMapping("/me")
    @Operation(summary = "View the authenticated account")
    public UserView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return UserView.from(users.findById(principal.id()).orElseThrow(() -> ApiException.notFound("Account")));
    }
}
