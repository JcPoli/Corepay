package dev.jcpolicarpio.corepay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI corepayOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Get one from POST /api/v1/auth/token, then click Authorize.");

        return new OpenAPI()
                .info(new Info()
                        .title("corepay")
                        .version("0.1.0")
                        .description("""
                                Payments core with a double-entry ledger.

                                Balances are derived from postings, never stored. Every transfer is a
                                balanced journal entry, retries are idempotent, and a posted payment is
                                corrected by reversal rather than by editing history.

                                Demo credentials: teller / teller-demo (read and write),
                                auditor / auditor-demo (read only).
                                """)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearer-jwt", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
