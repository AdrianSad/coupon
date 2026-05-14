package com.example.coupons.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI couponsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Coupons API")
                .version("1.0.0")
                .description("Coupon redemption service - case-insensitive codes, country-bound, "
                        + "rate-limited, idempotent."));
    }
}
