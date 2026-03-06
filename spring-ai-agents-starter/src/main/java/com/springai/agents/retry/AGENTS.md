# Retry Package — `com.springai.agents.retry`

## Purpose
Provides configurable retry logic for resilient execution of external calls
(LLM, REST, MCP tools). Built on Spring Retry.

## Key Classes

| Class | Role |
|-------|------|
| `RetryStrategy` | Enum of backoff strategies |
| `RetryConfig` | Immutable `@Value @Builder` configuration record |
| `RetryService` | Executes operations with configurable retry/backoff |

## Configuration
`RetryConfig` provides presets and a fluent builder:
```java
RetryConfig.DEFAULT    // 3 attempts, exponential, 1s→10s, 2× multiplier
RetryConfig.NONE       // no retries

RetryConfig.builder()
    .strategy(RetryStrategy.EXPONENTIAL_RANDOM)
    .maxAttempts(5)
    .initialDelayMs(500)
    .maxDelayMs(30000)
    .multiplier(3.0)
    .build();
```

## Strategies

| Strategy | Description |
|----------|-------------|
| `NONE` | No retries |
| `FIXED_DELAY` | Same delay between each retry |
| `LINEAR` | Delay increases linearly |
| `EXPONENTIAL` | Delay doubles each retry |
| `EXPONENTIAL_RANDOM` | Exponential with random jitter |
| `RANDOM` | Random delay between min/max |

## Integration
`RetryService` is registered as a bean by auto-configuration.
Users can provide their own `@Bean` to customize behavior.

