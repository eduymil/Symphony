package com.ledger.auth;

import com.ledger.account.AppUser;
import com.ledger.account.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Simple header-based login and logout")
public class AuthController {

    private final AppUserRepository userRepository;

    public AuthController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login with username and password",
        description = "Validates credentials and returns user info. The frontend should then include `X-Username` header in all subsequent requests.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"username": "alice", "password": "password"}
                    """)
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
        }
    )
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<AppUser> user = userRepository.findByUsername(request.username());
        if (user.isEmpty() || !user.get().getPassword().equals(request.password())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password"));
        }

        AppUser u = user.get();
        return ResponseEntity.ok(Map.of(
                "username", u.getUsername(),
                "userId", u.getId(),
                "message", "Login successful. Include X-Username header in all subsequent requests."
        ));
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout",
        description = "Simple logout. The frontend should clear the stored username.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Logout successful")
        }
    )
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    public record LoginRequest(String username, String password) {}
}
