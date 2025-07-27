package com.springai.agent.service;

import lombok.extern.slf4j.Slf4j;
import com.springai.agent.config.AppProperties.RetryDef;
import com.springai.agent.config.RetryStrategy;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.Callable;

/**
 * Service for creating and executing retry operations with configurable strategies.
 * <p>
 * This service provides comprehensive retry functionality including:
 * - Multiple retry strategies (fixed delay, exponential backoff, linear backoff, random)
 * - Custom retry policies with exception classification
 * - Configurable backoff policies and timing
 * - Integration with Spring Retry framework
 * - Detailed logging and monitoring of retry attempts
 * <p>
 * The service supports both default retry configurations and per-operation
 * custom retry settings, allowing fine-grained control over retry behavior
 * across different parts of the application.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Service
@Slf4j
public class RetryService {
    
    private final Random random = new Random();
    
    /**
     * Execute a callable operation with retry logic based on the provided retry configuration.
     * 
     * @param operation The operation to execute with retry
     * @param retryConfig The retry configuration to use
     * @param operationName Name of the operation for logging purposes
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception if all retry attempts fail
     */
    public <T> T executeWithRetry(Callable<T> operation, RetryDef retryConfig, String operationName) throws Exception {
        if (retryConfig == null || !retryConfig.isRetryEnabled()) {
            log.debug("Retry disabled for operation: {}", operationName);
            return operation.call();
        }
        
        RetryTemplate retryTemplate = createRetryTemplate(retryConfig);
        
        return retryTemplate.execute(new RetryCallback<T, Exception>() {
            @Override
            public T doWithRetry(RetryContext context) throws Exception {
                int attemptCount = context.getRetryCount() + 1;
                log.debug("Executing {} - attempt {} of {}", operationName, attemptCount, retryConfig.getEffectiveMaxAttempts());
                
                try {
                    T result = operation.call();
                    if (attemptCount > 1) {
                        log.info("Operation {} succeeded on attempt {} after {} retries", 
                            operationName, attemptCount, context.getRetryCount());
                    }
                    return result;
                } catch (Exception e) {
                    log.warn("Operation {} failed on attempt {} with error: {}", 
                        operationName, attemptCount, e.getMessage());
                    throw e; // Let CustomRetryPolicy handle the retry decision
                }
            }
        });
    }
    
    /**
     * Create a RetryTemplate based on the retry configuration.
     */
    private RetryTemplate createRetryTemplate(RetryDef retryConfig) {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Set custom retry policy that respects our exception classification
        CustomRetryPolicy retryPolicy = new CustomRetryPolicy(retryConfig);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Set backoff policy based on strategy
        BackOffPolicy backOffPolicy = createBackOffPolicy(retryConfig);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }
    
    /**
     * Create a BackOffPolicy based on the retry strategy.
     */
    private BackOffPolicy createBackOffPolicy(RetryDef retryConfig) {
        RetryStrategy strategy = retryConfig.getEffectiveStrategy();
        long initialDelay = retryConfig.getEffectiveInitialDelay();
        
        return switch (strategy) {
            case NONE -> {
                // No backoff needed for no retry
                FixedBackOffPolicy policy = new FixedBackOffPolicy();
                policy.setBackOffPeriod(0);
                yield policy;
            }
            
            case FIXED_DELAY -> {
                FixedBackOffPolicy policy = new FixedBackOffPolicy();
                policy.setBackOffPeriod(initialDelay);
                log.debug("Created FixedBackOffPolicy with delay: {}ms", initialDelay);
                yield policy;
            }
            
            case LINEAR -> {
                // Spring Retry doesn't have built-in linear backoff, so we'll use a custom implementation
                long increment = retryConfig.getIncrement() != null ? retryConfig.getIncrement() : 1000L;
                yield new LinearBackOffPolicy(initialDelay, increment);
            }
            
            case EXPONENTIAL -> {
                ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
                policy.setInitialInterval(initialDelay);
                double multiplier = retryConfig.getMultiplier() != null ? retryConfig.getMultiplier() : 2.0;
                long maxDelay = retryConfig.getMaxDelay() != null ? retryConfig.getMaxDelay() : 30000L;
                policy.setMultiplier(multiplier);
                policy.setMaxInterval(maxDelay);
                log.debug("Created ExponentialBackOffPolicy with initial: {}ms, multiplier: {}, max: {}ms", 
                    initialDelay, multiplier, maxDelay);
                yield policy;
            }
            
            case EXPONENTIAL_JITTER -> {
                ExponentialRandomBackOffPolicy policy = new ExponentialRandomBackOffPolicy();
                policy.setInitialInterval(initialDelay);
                double multiplier = retryConfig.getMultiplier() != null ? retryConfig.getMultiplier() : 2.0;
                long maxDelay = retryConfig.getMaxDelay() != null ? retryConfig.getMaxDelay() : 30000L;
                policy.setMultiplier(multiplier);
                policy.setMaxInterval(maxDelay);
                log.debug("Created ExponentialRandomBackOffPolicy with initial: {}ms, multiplier: {}, max: {}ms", 
                    initialDelay, multiplier, maxDelay);
                yield policy;
            }
            
            case CUSTOM -> {
                // For custom strategy, use exponential as default but allow override through customProperties
                ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
                policy.setInitialInterval(initialDelay);
                double multiplier = retryConfig.getMultiplier() != null ? retryConfig.getMultiplier() : 2.0;
                long maxDelay = retryConfig.getMaxDelay() != null ? retryConfig.getMaxDelay() : 30000L;
                policy.setMultiplier(multiplier);
                policy.setMaxInterval(maxDelay);
                log.debug("Created custom BackOffPolicy (using exponential as base) for custom strategy");
                yield policy;
            }
        };
    }
    
    
    /**
     * Custom LinearBackOffPolicy implementation for linear backoff strategy.
     */
    private static class LinearBackOffPolicy implements BackOffPolicy {
        private final long initialDelay;
        private final long increment;
        
        public LinearBackOffPolicy(long initialDelay, long increment) {
            this.initialDelay = initialDelay;
            this.increment = increment;
        }
        
        @Override
        public BackOffContext start(RetryContext context) {
            return new LinearBackOffContext();
        }
        
        @Override
        public void backOff(BackOffContext backOffContext) {
            LinearBackOffContext context = (LinearBackOffContext) backOffContext;
            long delay = initialDelay + (context.attemptCount * increment);
            context.attemptCount++;
            
            log.debug("Linear backoff: sleeping for {}ms (attempt {})", delay, context.attemptCount);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during backoff", e);
            }
        }
        
        private static class LinearBackOffContext implements BackOffContext {
            private int attemptCount = 0;
        }
    }
    
    /**
     * Execute a simple operation with default retry configuration.
     * Uses exponential backoff with 3 attempts.
     */
    public <T> T executeWithDefaultRetry(Callable<T> operation, String operationName) throws Exception {
        RetryDef defaultRetry = new RetryDef();
        defaultRetry.setStrategy(RetryStrategy.EXPONENTIAL);
        defaultRetry.setMaxAttempts(3);
        defaultRetry.setInitialDelay(1000L);
        defaultRetry.setMaxDelay(10000L);
        defaultRetry.setMultiplier(2.0);
        defaultRetry.setEnabled(true);
            
        return executeWithRetry(operation, defaultRetry, operationName);
    }
    
    /**
     * Custom retry policy that integrates our exception classification logic
     */
    private static class CustomRetryPolicy implements RetryPolicy {
        private final RetryDef retryConfig;
        private final int maxAttempts;
        
        public CustomRetryPolicy(RetryDef retryConfig) {
            this.retryConfig = retryConfig;
            this.maxAttempts = retryConfig.getEffectiveMaxAttempts();
        }
        
        @Override
        public boolean canRetry(RetryContext context) {
            // Check if we haven't exceeded max attempts
            if (context.getRetryCount() >= maxAttempts) {
                return false;
            }
            
            // Check if there's an exception and if it should be retried
            Throwable lastThrowable = context.getLastThrowable();
            if (lastThrowable instanceof Exception) {
                return shouldRetryException((Exception) lastThrowable);
            }
            
            // If no exception, allow retry (shouldn't happen in normal flow)
            return true;
        }
        
        @Override
        public RetryContext open(RetryContext parent) {
            return new CustomRetryContext(parent);
        }
        
        @Override
        public void close(RetryContext context) {
            // No cleanup needed
        }
        
        @Override
        public void registerThrowable(RetryContext context, Throwable throwable) {
            if (context instanceof CustomRetryContext) {
                ((CustomRetryContext) context).registerThrowable(throwable);
            }
        }
        
        /**
         * Simple RetryContext implementation
         */
        private static class CustomRetryContext implements RetryContext {
            private final RetryContext parent;
            private int retryCount = 0;
            private Throwable lastThrowable;
            
            public CustomRetryContext(RetryContext parent) {
                this.parent = parent;
            }
            
            @Override
            public RetryContext getParent() {
                return parent;
            }
            
            @Override
            public int getRetryCount() {
                return retryCount;
            }
            
            @Override
            public Throwable getLastThrowable() {
                return lastThrowable;
            }
            
            @Override
            public boolean isExhaustedOnly() {
                return false;
            }
            
            @Override
            public void setExhaustedOnly() {
                // Not implemented
            }
            
            @Override
            public String getAttribute(String name) {
                return null;
            }
            
            @Override
            public void setAttribute(String name, Object value) {
                // Not implemented
            }
            
            @Override
            public Object removeAttribute(String name) {
                // Not implemented
                return null;
            }
            
            @Override
            public boolean hasAttribute(String name) {
                return false;
            }
            
            @Override
            public String[] attributeNames() {
                return new String[0];
            }
            
            public void registerThrowable(Throwable throwable) {
                this.lastThrowable = throwable;
                this.retryCount++;
            }
        }
        
        private boolean shouldRetryException(Exception exception) {
            String exceptionClassName = exception.getClass().getSimpleName();
            
            // Check non-retryable exceptions first (takes precedence)
            if (retryConfig.getNonRetryableExceptions() != null && 
                retryConfig.getNonRetryableExceptions().contains(exceptionClassName)) {
                return false;
            }
            
            // Check retryable exceptions
            if (retryConfig.getRetryableExceptions() != null && !retryConfig.getRetryableExceptions().isEmpty()) {
                return retryConfig.getRetryableExceptions().contains(exceptionClassName);
            }
            
            // Default: retry all exceptions
            return true;
        }
    }
}
