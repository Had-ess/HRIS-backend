package com.hris.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
    // Retry enabled via @EnableRetry
    // Individual methods use @Retryable annotation
}
