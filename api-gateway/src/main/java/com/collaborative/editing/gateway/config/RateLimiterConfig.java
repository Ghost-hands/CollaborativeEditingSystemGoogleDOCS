package com.collaborative.editing.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {
    
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String ipAddress = (remoteAddress != null && remoteAddress.getAddress() != null) ?
                remoteAddress.getAddress().getHostAddress() :
                "unknown";
            return Mono.just(ipAddress);
        };
    }
}

