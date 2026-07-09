package com.flowpay.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI flowPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowPay Core API")
                        .description("Real-Time Payment Processing Platform API. " +
                                "Provides endpoints for payment transactions, account management, " +
                                "authentication, notifications, and webhook configuration.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FlowPay Engineering")
                                .email("engineering@flowpay.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://flowpay.com/terms")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development"),
                        new Server().url("https://api.flowpay.com").description("Production")
                ))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"));
    }
}
