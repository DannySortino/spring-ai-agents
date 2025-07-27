package com.springai.agent.config;

/**
 * Enum representing different retry strategies that can be applied to workflow steps and tool calls.
 * Each strategy defines how retry attempts are spaced and executed.
 */
public enum RetryStrategy {
    
    /**
     * No retry strategy - operations fail immediately on first error.
     * <p>
     * Use cases:
     * - Operations that should not be retried (e.g., validation errors)
     * - Critical operations where immediate failure is preferred
     * - Testing scenarios where retry behavior should be disabled
     * <p>
     * Configuration:
     * - maxAttempts: 1 (no retries)
     * - delay: N/A
     */
    NONE,
    
    /**
     * Fixed delay retry strategy - waits a constant amount of time between retry attempts.
     * <p>
     * Use cases:
     * - Simple retry scenarios with predictable timing
     * - Operations where consistent delay is acceptable
     * - Low-traffic scenarios where timing precision isn't critical
     * <p>
     * Configuration:
     * - maxAttempts: configurable (default: 3)
     * - delay: fixed delay in milliseconds (default: 1000ms)
     * <p>
     * Example: 1000ms → 1000ms → 1000ms
     */
    FIXED_DELAY,
    
    /**
     * Linear backoff retry strategy - increases delay by a fixed amount for each retry attempt.
     * <p>
     * Use cases:
     * - Gradual backoff scenarios
     * - Operations where moderate delay increase is sufficient
     * - Predictable load management
     * <p>
     * Configuration:
     * - maxAttempts: configurable (default: 3)
     * - initialDelay: starting delay in milliseconds (default: 1000ms)
     * - increment: delay increase per attempt in milliseconds (default: 1000ms)
     * <p>
     * Example: 1000ms → 2000ms → 3000ms → 4000ms
     */
    LINEAR,
    
    /**
     * Exponential backoff retry strategy - doubles the delay for each retry attempt.
     * Recommended for most distributed system interactions and external API calls.
     * <p>
     * Use cases:
     * - External API calls and web services
     * - Database connection retries
     * - Network-related operations
     * - High-traffic scenarios requiring load reduction
     * - Distributed system interactions
     * <p>
     * Configuration:
     * - maxAttempts: configurable (default: 3)
     * - initialDelay: starting delay in milliseconds (default: 1000ms)
     * - multiplier: backoff multiplier (default: 2.0)
     * - maxDelay: maximum delay cap in milliseconds (default: 30000ms)
     * <p>
     * Example: 1000ms → 2000ms → 4000ms → 8000ms
     */
    EXPONENTIAL,
    
    /**
     * Exponential backoff with jitter - adds randomization to exponential backoff.
     * Prevents thundering herd problems in distributed systems.
     * <p>
     * Use cases:
     * - High-concurrency distributed systems
     * - Multiple agents/services retrying simultaneously
     * - External services that may be overwhelmed by synchronized retries
     * - Production systems requiring optimal load distribution
     * <p>
     * Configuration:
     * - maxAttempts: configurable (default: 3)
     * - initialDelay: starting delay in milliseconds (default: 1000ms)
     * - multiplier: backoff multiplier (default: 2.0)
     * - maxDelay: maximum delay cap in milliseconds (default: 30000ms)
     * - jitterFactor: randomization factor 0.0-1.0 (default: 0.1)
     * <p>
     * Example: 1000ms±100ms → 2000ms±200ms → 4000ms±400ms
     */
    EXPONENTIAL_JITTER,
    
    /**
     * Custom retry strategy - allows for completely customized retry behavior.
     * Requires custom implementation of retry logic.
     * <p>
     * Use cases:
     * - Complex business-specific retry requirements
     * - Integration with external retry systems
     * - Advanced retry patterns not covered by standard strategies
     * - Custom backoff algorithms
     * <p>
     * Configuration:
     * - Fully customizable through configuration properties
     * - Requires custom retry template or implementation
     * <p>
     * Example: Custom algorithm based on business rules
     */
    CUSTOM;
    
    /**
     * Get the default retry strategy for the application.
     * This is used when no specific strategy is configured.
     * 
     * @return The default retry strategy (EXPONENTIAL)
     */
    public static RetryStrategy getDefault() {
        return EXPONENTIAL;
    }
    
    /**
     * Check if this strategy requires retry attempts.
     * 
     * @return true if the strategy performs retries, false otherwise
     */
    public boolean isRetryEnabled() {
        return this != NONE;
    }
    
    /**
     * Get the recommended maximum attempts for this strategy.
     * 
     * @return recommended maximum retry attempts
     */
    public int getRecommendedMaxAttempts() {
        return switch (this) {
            case NONE -> 1;
            case FIXED_DELAY, LINEAR -> 3;
            case EXPONENTIAL, EXPONENTIAL_JITTER -> 5;
            case CUSTOM -> 3; // Default for custom, should be overridden
        };
    }
    
    /**
     * Get the recommended initial delay for this strategy in milliseconds.
     * 
     * @return recommended initial delay in milliseconds
     */
    public long getRecommendedInitialDelay() {
        return switch (this) {
            case NONE -> 0L;
            case FIXED_DELAY -> 1000L;
            case LINEAR -> 1000L;
            case EXPONENTIAL, EXPONENTIAL_JITTER -> 1000L;
            case CUSTOM -> 1000L; // Default for custom, should be overridden
        };
    }
    
    /**
     * Get a human-readable description of this retry strategy.
     * 
     * @return description of the retry strategy
     */
    public String getDescription() {
        return switch (this) {
            case NONE -> "No retries - fail immediately on first error";
            case FIXED_DELAY -> "Fixed delay between retry attempts";
            case LINEAR -> "Linear increase in delay between retry attempts";
            case EXPONENTIAL -> "Exponential backoff - doubles delay each attempt";
            case EXPONENTIAL_JITTER -> "Exponential backoff with randomized jitter";
            case CUSTOM -> "Custom retry strategy with configurable behavior";
        };
    }
}
