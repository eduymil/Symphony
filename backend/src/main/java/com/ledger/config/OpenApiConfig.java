package com.ledger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Internal Ledger System API")
                                                .version("1.0.0")
                                                .description("""
                                                                Financial ledger system that records account transactions and maintains accurate balances.

                                                                ## Authentication
                                                                All endpoints (except login) require the `X-Username` header with a valid username.

                                                                ## Idempotency
                                                                The `POST /api/transactions` endpoint requires an `Idempotency-Key` header to prevent duplicate transactions.

                                                                ## Debug Endpoints
                                                                Endpoints tagged with **[DEBUG]** exist solely for testing and demonstration purposes.
                                                                They allow intentional manipulation of data to verify reconciliation and ledger verification features.
                                                                """)
                                                .contact(new Contact()
                                                                .name("Ledger System")))
                                .addSecurityItem(new SecurityRequirement().addList("UsernameAuth"))
                                .components(new Components()
                                        .addSecuritySchemes("UsernameAuth", new SecurityScheme()
                                                .name("X-Username")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description("Enter a valid username (e.g. alice, bob) to authenticate requests")));
        }
}
