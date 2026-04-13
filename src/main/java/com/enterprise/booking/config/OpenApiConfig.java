package com.enterprise.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hotel Booking Agent API")
                        .description("API for enterprise booking flow with preview and confirmation gating.")
                        .version("v1")
                        .contact(new Contact().name("Enterprise Booking Team"))
                        .license(new License().name("Proprietary")));
    }
}
