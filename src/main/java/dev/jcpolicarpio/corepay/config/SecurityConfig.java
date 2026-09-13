package dev.jcpolicarpio.corepay.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT with a symmetric key.
 *
 * Scopes, not roles: a payments API is easier to reason about when the token
 * says what it may do ("payments:write") rather than who holds it. Spring maps
 * the "scope" claim to SCOPE_ authorities, so no custom converter is needed.
 */
@Configuration
public class SecurityConfig {

    private static final String READ_PAYMENTS = "SCOPE_payments:read";
    private static final String WRITE_PAYMENTS = "SCOPE_payments:write";
    private static final String READ_ACCOUNTS = "SCOPE_accounts:read";
    private static final String WRITE_ACCOUNTS = "SCOPE_accounts:write";

    private final CorepayProperties properties;

    public SecurityConfig(CorepayProperties properties) {
        this.properties = properties;
    }

    private SecretKeySpec key() {
        return new SecretKeySpec(
                properties.getSecurity().getJwtSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/api/v1/auth/token",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/**")
                        .hasAuthority(WRITE_PAYMENTS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/**")
                        .hasAnyAuthority(READ_PAYMENTS, WRITE_PAYMENTS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/**")
                        .hasAuthority(WRITE_ACCOUNTS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/**")
                        .hasAnyAuthority(READ_ACCOUNTS, WRITE_ACCOUNTS)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
