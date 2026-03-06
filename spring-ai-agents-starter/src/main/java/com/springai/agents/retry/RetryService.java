package com.springai.agents.retry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.*;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.Callable;

/**
 * Service for executing operations with configurable retry logic.
 * <p>
 * Supports multiple {@link RetryStrategy} types including exponential backoff,
 * linear delay, fixed delay, and random jitter. Built on Spring Retry.
 */
@Slf4j
public class RetryService {


    /**
     * Execute an operation with retry logic.
     *
     * @param operation     The operation to execute.
     * @param retryConfig   The retry configuration (null = no retry).
     * @param operationName Name for logging purposes.
     * @param <T>           Return type.
     * @return The operation result.
     * @throws Exception if all attempts fail.
     */
    public <T> T executeWithRetry(Callable<T> operation, RetryConfig retryConfig, String operationName)
            throws Exception {
        if (retryConfig == null || !retryConfig.isEnabled()) {
            log.debug("Retry disabled for '{}'", operationName);
            return operation.call();
        }

        RetryTemplate template = createRetryTemplate(retryConfig);

        return template.execute((RetryCallback<T, Exception>) context -> {
            int attempt = context.getRetryCount() + 1;
            log.debug("Executing '{}' — attempt {} of {}", operationName, attempt, retryConfig.getMaxAttempts());
            try {
                T result = operation.call();
                if (attempt > 1) {
                    log.info("'{}' succeeded on attempt {} after {} retries",
                            operationName, attempt, context.getRetryCount());
                }
                return result;
            } catch (Exception e) {
                log.warn("'{}' failed on attempt {}: {}", operationName, attempt, e.getMessage());
                throw e;
            }
        });
    }

    private RetryTemplate createRetryTemplate(RetryConfig config) {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(config.getMaxAttempts()));
        template.setBackOffPolicy(createBackOffPolicy(config));
        return template;
    }

    private BackOffPolicy createBackOffPolicy(RetryConfig config) {
        return switch (config.getStrategy()) {
            case FIXED_DELAY -> {
                FixedBackOffPolicy policy = new FixedBackOffPolicy();
                policy.setBackOffPeriod(config.getInitialDelayMs());
                yield policy;
            }
            case EXPONENTIAL -> {
                ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
                policy.setInitialInterval(config.getInitialDelayMs());
                policy.setMaxInterval(config.getMaxDelayMs());
                policy.setMultiplier(config.getMultiplier());
                yield policy;
            }
            case EXPONENTIAL_RANDOM -> {
                ExponentialRandomBackOffPolicy policy = new ExponentialRandomBackOffPolicy();
                policy.setInitialInterval(config.getInitialDelayMs());
                policy.setMaxInterval(config.getMaxDelayMs());
                policy.setMultiplier(config.getMultiplier());
                yield policy;
            }
            case LINEAR -> {
                // Linear: delay = initialDelay * attemptNumber, capped at maxDelay
                ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
                policy.setInitialInterval(config.getInitialDelayMs());
                policy.setMaxInterval(config.getMaxDelayMs());
                policy.setMultiplier(1.0); // linear growth
                yield policy;
            }
            case RANDOM -> {
                UniformRandomBackOffPolicy policy = new UniformRandomBackOffPolicy();
                policy.setMinBackOffPeriod(config.getInitialDelayMs());
                policy.setMaxBackOffPeriod(config.getMaxDelayMs());
                yield policy;
            }
            case NONE -> new NoBackOffPolicy();
        };
    }
}

