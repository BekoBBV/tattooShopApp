package com.tattoostudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** API docs, browsable at /swagger-ui.html. Staff endpoints: sign in, then paste the token under "Authorize". */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI tattooStudioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tattoo Studio API")
                        .description("Booking, catalog and staff API for the tattoo studio website")
                        .version("0.1"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
