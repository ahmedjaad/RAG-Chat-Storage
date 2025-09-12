package com.rag.chatstorage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${security.api-key.header:X-API-KEY}")
    private String apiKeyHeader;

    @Bean
    public OpenAPI openAPI() {
        String schemeName = "ApiKeyAuth";
        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(apiKeyHeader)
                .description("Provide your API key in the " + apiKeyHeader + " header");

        return new OpenAPI()
                .info(new Info().title("RAG Chat Storage API").version("v1"))
                .components(new Components().addSecuritySchemes(schemeName, apiKeyScheme))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
