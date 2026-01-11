package com.collaborative.editing.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

@Configuration
public class GatewayController {
    
    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);

    @Bean
    public RouterFunction<ServerResponse> route() {
        return RouterFunctions
            .route(GET("/").and(accept(MediaType.APPLICATION_JSON)), request -> {
                logger.debug("API Gateway info request received from: {}", getClientAddress(request));
                Map<String, Object> apiInfo = new HashMap<>();
                apiInfo.put("service", "API Gateway");
                apiInfo.put("status", "running");
                apiInfo.put("version", "1.0.0");
                
                Map<String, String> endpoints = new HashMap<>();
                endpoints.put("User Management", "/api/users");
                endpoints.put("Document Editing", "/api/documents");
                endpoints.put("Version Control", "/api/versions");
                endpoints.put("Health Check", "/actuator/health");
                endpoints.put("Gateway Routes", "/actuator/gateway/routes");
                
                apiInfo.put("availableEndpoints", endpoints);
                
                Map<String, String> examples = new HashMap<>();
                examples.put("Get all users", "GET /api/users");
                examples.put("Register user", "POST /api/users/register");
                examples.put("Authenticate", "POST /api/users/authenticate");
                examples.put("Get user by ID", "GET /api/users/{id}");
                examples.put("Get documents", "GET /api/documents");
                examples.put("Get versions", "GET /api/versions");
                
                apiInfo.put("exampleEndpoints", examples);
                
                logger.debug("Returning API Gateway info");
                MediaType jsonMediaType = Objects.requireNonNull(MediaType.APPLICATION_JSON);
                return ServerResponse.ok()
                    .contentType(jsonMediaType)
                    .bodyValue(apiInfo);
            });
    }
    
    private String getClientAddress(ServerRequest request) {
        return request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
    }
}

