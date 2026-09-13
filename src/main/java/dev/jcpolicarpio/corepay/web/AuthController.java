package dev.jcpolicarpio.corepay.web;

import dev.jcpolicarpio.corepay.config.CorepayProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues a short-lived token for the demo users in configuration.
 *
 * A real deployment would federate to the bank's identity provider; this
 * exists so the API is explorable from Swagger without extra infrastructure,
 * and it is deliberately the only unauthenticated write endpoint.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final CorepayProperties properties;
    private final JwtEncoder jwtEncoder;

    public AuthController(CorepayProperties properties, JwtEncoder jwtEncoder) {
        this.properties = properties;
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/token")
    @SecurityRequirements
    @Operation(summary = "Exchange demo credentials for a bearer token")
    public ResponseEntity<Api.TokenResponse> token(@Valid @RequestBody Api.TokenRequest request) {
        Optional<CorepayProperties.DemoUser> user = properties.getSecurity().getDemoUsers().stream()
                .filter(candidate -> candidate.getUsername().equals(request.username()))
                .filter(candidate -> candidate.getPassword().equals(request.password()))
                .findFirst();

        if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        String scope = scopesFor(user.get().getRoles());
        long validitySeconds = properties.getSecurity().getTokenValidityMinutes() * 60L;
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("corepay")
                .issuedAt(now)
                .expiresAt(now.plus(validitySeconds, ChronoUnit.SECONDS))
                .subject(request.username())
                .claim("scope", scope)
                .build();

        // The key is a shared secret, so the algorithm has to be stated:
        // NimbusJwtEncoder defaults to RS256 and would find no matching key.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();        return ResponseEntity.ok(new Api.TokenResponse(token, "Bearer", validitySeconds, scope));
    }

    /** A teller may move money; an auditor may only look at it. */
    private String scopesFor(String roles) {
        if (roles != null && roles.contains("TELLER")) {
            return "payments:read payments:write accounts:read accounts:write";
        }
        return "payments:read accounts:read";
    }
}
