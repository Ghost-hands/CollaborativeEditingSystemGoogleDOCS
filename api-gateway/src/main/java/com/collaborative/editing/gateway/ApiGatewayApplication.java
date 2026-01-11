package com.collaborative.editing.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// @EnableDiscoveryClient  
@ComponentScan(basePackages = {"com.collaborative.editing.gateway", "com.collaborative.editing.common"})
public class ApiGatewayApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayApplication.class);
    
    public static void main(String[] args) {
        logger.info("Starting API Gateway Application...");
        SpringApplication.run(ApiGatewayApplication.class, args);
        logger.info("API Gateway Application started successfully");
    }
}

