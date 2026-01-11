package com.collaborative.editing.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Gateway Controller Tests")
class GatewayControllerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Gateway controller bean should be created")
    void testGatewayControllerBeanExists() {
        // Verify that GatewayController is configured as a bean
        assertTrue(applicationContext.containsBean("route"), 
            "GatewayController route bean should exist");
    }

    @Test
    @DisplayName("Gateway controller configuration should be valid")
    void testGatewayControllerConfiguration() {
        // Verify that the controller class exists and is properly configured
        GatewayController controller = applicationContext.getBean(GatewayController.class);
        assertNotNull(controller, "GatewayController should be configured");
    }
}
