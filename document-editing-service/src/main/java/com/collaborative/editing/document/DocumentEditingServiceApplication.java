package com.collaborative.editing.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// @EnableDiscoveryClient 
@ComponentScan(basePackages = {"com.collaborative.editing.document", "com.collaborative.editing.common"})
public class DocumentEditingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentEditingServiceApplication.class, args);
    }
}

