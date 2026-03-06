package com.springai.agents.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryService & RetryConfig")
class RetryServiceTest {

    private final RetryService retryService = new RetryService();

    @Nested
    @DisplayName("RetryConfig")
    class RetryConfigTests {

        @Test
        @DisplayName("DEFAULT preset has expected values")
        void defaultPreset() {
            assertEquals(RetryStrategy.EXPONENTIAL, RetryConfig.DEFAULT.getStrategy());
            assertEquals(3, RetryConfig.DEFAULT.getMaxAttempts());
            assertEquals(1000L, RetryConfig.DEFAULT.getInitialDelayMs());
            assertEquals(10000L, RetryConfig.DEFAULT.getMaxDelayMs());
            assertEquals(2.0, RetryConfig.DEFAULT.getMultiplier());
            assertTrue(RetryConfig.DEFAULT.isEnabled());
        }

        @Test
        @DisplayName("NONE preset disables retries")
        void nonePreset() {
            assertEquals(RetryStrategy.NONE, RetryConfig.NONE.getStrategy());
            assertEquals(1, RetryConfig.NONE.getMaxAttempts());
            assertFalse(RetryConfig.NONE.isEnabled());
        }

        @Test
        @DisplayName("custom builder creates config")
        void customBuilder() {
            RetryConfig config = RetryConfig.builder()
                    .strategy(RetryStrategy.FIXED_DELAY)
                    .maxAttempts(5)
                    .initialDelayMs(200L)
                    .maxDelayMs(5000L)
                    .multiplier(1.5)
                    .build();

            assertEquals(RetryStrategy.FIXED_DELAY, config.getStrategy());
            assertEquals(5, config.getMaxAttempts());
            assertEquals(200L, config.getInitialDelayMs());
        }
    }

    @Nested
    @DisplayName("RetryService execution")
    class ExecutionTests {

        @Test
        @DisplayName("succeeds on first attempt without retry")
        void successNoRetry() throws Exception {
            String result = retryService.executeWithRetry(
                    () -> "success",
                    RetryConfig.DEFAULT,
                    "test-op");

            assertEquals("success", result);
        }

        @Test
        @DisplayName("succeeds after retries")
        void successAfterRetries() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = retryService.executeWithRetry(
                    () -> {
                        if (attempts.incrementAndGet() < 3) {
                            throw new RuntimeException("Transient failure");
                        }
                        return "recovered";
                    },
                    RetryConfig.builder()
                            .strategy(RetryStrategy.FIXED_DELAY)
                            .maxAttempts(5)
                            .initialDelayMs(10L)
                            .build(),
                    "retry-op");

            assertEquals("recovered", result);
            assertEquals(3, attempts.get());
        }

        @Test
        @DisplayName("throws after all attempts exhausted")
        void allAttemptsExhausted() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RuntimeException.class, () ->
                    retryService.executeWithRetry(
                            () -> {
                                attempts.incrementAndGet();
                                throw new RuntimeException("Permanent failure");
                            },
                            RetryConfig.builder()
                                    .strategy(RetryStrategy.FIXED_DELAY)
                                    .maxAttempts(3)
                                    .initialDelayMs(10L)
                                    .build(),
                            "fail-op"));

            assertEquals(3, attempts.get());
        }

        @Test
        @DisplayName("skips retry when config is null")
        void nullConfig() throws Exception {
            String result = retryService.executeWithRetry(
                    () -> "direct",
                    null,
                    "no-retry-op");

            assertEquals("direct", result);
        }

        @Test
        @DisplayName("skips retry when config is NONE")
        void noneConfig() throws Exception {
            String result = retryService.executeWithRetry(
                    () -> "direct",
                    RetryConfig.NONE,
                    "none-op");

            assertEquals("direct", result);
        }
    }
}

