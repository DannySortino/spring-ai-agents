package com.springai.agents.retry;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable configuration for retry behavior.
 *
 * <pre>{@code
 * RetryConfig config = RetryConfig.builder()
 *     .strategy(RetryStrategy.EXPONENTIAL)
 *     .maxAttempts(3)
 *     .initialDelayMs(1000)
 *     .maxDelayMs(10000)
 *     .multiplier(2.0)
 *     .build();
 * }</pre>
 */
@Value
@Builder
public class RetryConfig {

    /** Default: 3 attempts, exponential backoff, 1s → 10s, 2× multiplier. */
    public static final RetryConfig DEFAULT = RetryConfig.builder()
            .strategy(RetryStrategy.EXPONENTIAL)
            .maxAttempts(3)
            .initialDelayMs(1000L)
            .maxDelayMs(10000L)
            .multiplier(2.0)
            .build();

    /** No retries — fail immediately. */
    public static final RetryConfig NONE = RetryConfig.builder()
            .strategy(RetryStrategy.NONE)
            .maxAttempts(1)
            .initialDelayMs(0L)
            .maxDelayMs(0L)
            .multiplier(1.0)
            .build();

    /** Retry strategy to use. */
    @lombok.NonNull
    @Builder.Default
    RetryStrategy strategy = RetryStrategy.EXPONENTIAL;

    /** Maximum number of attempts (including the initial attempt). Must be ≥ 1. */
    @Builder.Default
    int maxAttempts = 3;

    /** Initial delay in milliseconds between retries. Must be ≥ 0. */
    @Builder.Default
    long initialDelayMs = 1000L;

    /** Maximum delay in milliseconds (caps exponential/linear growth). */
    @Builder.Default
    long maxDelayMs = 10000L;

    /** Multiplier for exponential backoff. */
    @Builder.Default
    double multiplier = 2.0;

    /** Whether retries are enabled. */
    public boolean isEnabled() {
        return strategy != RetryStrategy.NONE && maxAttempts > 1;
    }
}

