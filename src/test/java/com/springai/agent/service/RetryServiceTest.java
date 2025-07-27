package com.springai.agent.service;

import com.springai.agent.config.AppProperties.RetryDef;
import com.springai.agent.config.RetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryService functionality.
 * Tests different retry strategies, configurations, and error handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    private RetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new RetryService();
    }

    @Test
    void testExecuteWithRetryDisabled() throws Exception {
        // Given
        RetryDef retryConfig = new RetryDef(); // TODO: Manual fix needed for builder chain
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            return "success";
        };

        // When
        String result = retryService.executeWithRetry(operation, retryConfig, "test-operation");

        // Then
        assertEquals("success", result);
        assertEquals(1, callCount.get(), "Should execute only once when retry is disabled");
    }

    @Test
    void testExecuteWithRetryNullConfig() throws Exception {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            return "success";
        };

        // When
        String result = retryService.executeWithRetry(operation, null, "test-operation");

        // Then
        assertEquals("success", result);
        assertEquals(1, callCount.get(), "Should execute only once when retry config is null");
    }

    @Test
    void testExecuteWithRetrySuccessOnFirstAttempt() throws Exception {
        // Given
        RetryDef retryConfig = new RetryDef(); // TODO: Manual fix needed for builder chain
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            return "success";
        };

        // When
        String result = retryService.executeWithRetry(operation, retryConfig, "test-operation");

        // Then
        assertEquals("success", result);
        assertEquals(1, callCount.get(), "Should execute only once when successful on first attempt");
    }

    @Test
    void testExecuteWithRetrySuccessAfterFailures() throws Exception {
        // Given
        RetryDef retryConfig = new RetryDef(); // TODO: Manual fix needed for builder chain
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int count = callCount.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("Temporary failure " + count);
            }
            return "success after " + count + " attempts";
        };

        // When
        String result = retryService.executeWithRetry(operation, retryConfig, "test-operation");

        // Then
        assertEquals("success after 3 attempts", result);
        assertEquals(3, callCount.get(), "Should execute 3 times before success");
    }

    @Test
    void testExecuteWithRetryExhaustsAllAttempts() {
        // Given
        RetryDef retryConfig = new RetryDef();
        retryConfig.setStrategy(RetryStrategy.EXPONENTIAL);
        retryConfig.setMaxAttempts(2);
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Always fails");
        };

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            retryService.executeWithRetry(operation, retryConfig, "test-operation");
        });
        
        assertEquals("Always fails", exception.getMessage());
        assertEquals(2, callCount.get(), "Should execute exactly maxAttempts times");
    }

    @Test
    void testExecuteWithRetryNonRetryableException() {
        // Given
        RetryDef retryConfig = new RetryDef();
        retryConfig.setStrategy(RetryStrategy.EXPONENTIAL);
        retryConfig.setMaxAttempts(3);
        retryConfig.setRetryableExceptions(List.of("java.lang.RuntimeException")); // Only retry RuntimeException
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            throw new IllegalArgumentException("Non-retryable error");
        };

        // When & Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            retryService.executeWithRetry(operation, retryConfig, "test-operation");
        });
        
        assertEquals("Non-retryable error", exception.getMessage());
        assertEquals(1, callCount.get(), "Should execute only once for non-retryable exception");
    }

    @Test
    void testExecuteWithRetrySpecificRetryableException() throws Exception {
        // Given
        RetryDef retryConfig = new RetryDef(); // TODO: Manual fix needed for builder chain
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int count = callCount.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("Retryable error " + count);
            }
            return "success";
        };

        // When
        String result = retryService.executeWithRetry(operation, retryConfig, "test-operation");

        // Then
        assertEquals("success", result);
        assertEquals(2, callCount.get(), "Should retry RuntimeException and succeed");
    }

    @Test
    void testExecuteWithRetryNonListedRetryableException() {
        // Given
        RetryDef retryConfig = new RetryDef();
        retryConfig.setStrategy(RetryStrategy.EXPONENTIAL);
        retryConfig.setMaxAttempts(3);
        retryConfig.setRetryableExceptions(List.of("java.lang.RuntimeException")); // Only retry RuntimeException, not IllegalStateException
        
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            callCount.incrementAndGet();
            throw new IllegalStateException("Not in retryable list");
        };

        // When & Then
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            retryService.executeWithRetry(operation, retryConfig, "test-operation");
        });
        
        assertEquals("Not in retryable list", exception.getMessage());
        assertEquals(1, callCount.get(), "Should not retry exception not in retryable list");
    }

    @Test
    void testExecuteWithDefaultRetry() throws Exception {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int count = callCount.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("Temporary failure");
            }
            return "success with default retry";
        };

        // When
        String result = retryService.executeWithDefaultRetry(operation, "test-operation");

        // Then
        assertEquals("success with default retry", result);
        assertEquals(2, callCount.get(), "Should retry with default configuration");
    }

    @Test
    void testRetryDefEffectiveMethods() {
        // Test effective max attempts
        RetryDef retryConfig1 = new RetryDef();
        retryConfig1.setStrategy(RetryStrategy.EXPONENTIAL);
        assertEquals(5, retryConfig1.getEffectiveMaxAttempts());

        RetryDef retryConfig2 = new RetryDef();
        retryConfig2.setStrategy(RetryStrategy.EXPONENTIAL);
        assertEquals(5, retryConfig2.getEffectiveMaxAttempts()); // Default for EXPONENTIAL

        // Test effective initial delay
        RetryDef retryConfig3 = new RetryDef();
        retryConfig3.setStrategy(RetryStrategy.EXPONENTIAL);
        retryConfig3.setInitialDelay(2000L);
        assertEquals(2000L, retryConfig3.getEffectiveInitialDelay());

        RetryDef retryConfig4 = new RetryDef();
        retryConfig4.setStrategy(RetryStrategy.FIXED_DELAY);
        assertEquals(1000L, retryConfig4.getEffectiveInitialDelay()); // Default for FIXED_DELAY

        // Test retry enabled
        RetryDef retryConfig5 = new RetryDef();
        retryConfig5.setStrategy(RetryStrategy.NONE);
        assertFalse(retryConfig5.isRetryEnabled());

        RetryDef retryConfig6 = new RetryDef();
        retryConfig6.setStrategy(RetryStrategy.NONE);
        assertFalse(retryConfig6.isRetryEnabled());

        RetryDef retryConfig7 = new RetryDef();
        retryConfig7.setStrategy(RetryStrategy.EXPONENTIAL);
        assertTrue(retryConfig7.isRetryEnabled());
    }

    @Test
    void testRetryStrategyDefaults() {
        // Test default strategy
        assertEquals(RetryStrategy.EXPONENTIAL, RetryStrategy.getDefault());

        // Test retry enabled for different strategies
        assertTrue(RetryStrategy.EXPONENTIAL.isRetryEnabled());
        assertTrue(RetryStrategy.LINEAR.isRetryEnabled());
        assertTrue(RetryStrategy.FIXED_DELAY.isRetryEnabled());
        assertFalse(RetryStrategy.NONE.isRetryEnabled());

        // Test recommended max attempts
        assertEquals(1, RetryStrategy.NONE.getRecommendedMaxAttempts());
        assertEquals(3, RetryStrategy.FIXED_DELAY.getRecommendedMaxAttempts());
        assertEquals(3, RetryStrategy.LINEAR.getRecommendedMaxAttempts());
        assertEquals(5, RetryStrategy.EXPONENTIAL.getRecommendedMaxAttempts());
        assertEquals(5, RetryStrategy.EXPONENTIAL_JITTER.getRecommendedMaxAttempts());

        // Test recommended initial delay
        assertEquals(0L, RetryStrategy.NONE.getRecommendedInitialDelay());
        assertEquals(1000L, RetryStrategy.FIXED_DELAY.getRecommendedInitialDelay());
        assertEquals(1000L, RetryStrategy.LINEAR.getRecommendedInitialDelay());
        assertEquals(1000L, RetryStrategy.EXPONENTIAL.getRecommendedInitialDelay());
        assertEquals(1000L, RetryStrategy.EXPONENTIAL_JITTER.getRecommendedInitialDelay());

        // Test descriptions
        assertNotNull(RetryStrategy.NONE.getDescription());
        assertNotNull(RetryStrategy.EXPONENTIAL.getDescription());
        assertTrue(RetryStrategy.EXPONENTIAL.getDescription().toLowerCase().contains("exponential"));
    }
}



