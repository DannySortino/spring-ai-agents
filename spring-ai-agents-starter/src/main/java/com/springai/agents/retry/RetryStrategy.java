package com.springai.agents.retry;

/**
 * Retry strategy enumeration for configuring backoff behavior.
 */
public enum RetryStrategy {
    /** No retries — fail immediately on error. */
    NONE,
    /** Fixed delay between each retry attempt. */
    FIXED_DELAY,
    /** Delay increases linearly with each attempt. */
    LINEAR,
    /** Delay doubles with each attempt (exponential backoff). */
    EXPONENTIAL,
    /** Exponential backoff with random jitter to prevent thundering herd. */
    EXPONENTIAL_RANDOM,
    /** Random delay between initialDelay and maxDelay. */
    RANDOM
}

