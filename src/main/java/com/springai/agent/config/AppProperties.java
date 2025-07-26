package com.springai.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties class for the Spring AI Agent application.
 * 
 * This class defines the structure for all application configuration including:
 * - Agent definitions with their workflows and MCP server configurations
 * - Default retry configurations that can be overridden at various levels
 * - Nested classes for different configuration aspects (workflows, tasks, workers, routes)
 * 
 * The configuration supports complex workflow types including chain, parallel,
 * orchestrator, and routing workflows with conditional logic and retry mechanisms.
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppProperties {
    private List<AgentDef> agents;
    
    @NestedConfigurationProperty
    private RetryDef defaultRetry; // Application-wide default retry configuration
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgentDef {
        private String name;
        private String model;
        private String systemPrompt;
        
        @NestedConfigurationProperty
        private WorkflowDef workflow;
        
        @NestedConfigurationProperty
        private McpServerDef mcpServer;
        
        @NestedConfigurationProperty
        private RetryDef retry; // Per-agent retry override
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class McpServerDef {
        private boolean enabled = false;
        private Integer port;
        private String baseUrl;
        private String sseEndpoint = "/sse";
        private String sseMessageEndpoint = "/message";
        private String description;
        private String version = "1.0.0";
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkflowDef {
        private WorkflowType type;
        private List<WorkflowStepDef> chain;
        private List<TaskDef> tasks;
        private String aggregator;
        private String managerPrompt;
        private List<WorkerDef> workers;
        private String synthesizerPrompt;
        private Map<String, RouteDef> routes;
        
        @NestedConfigurationProperty
        private WorkflowDef nestedWorkflow; // Support for nested workflows
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkflowStepDef {
        private String prompt;
        private String tool; // MCP tool name to call
        
        @NestedConfigurationProperty
        private WorkflowDef nestedWorkflow; // Support for nested workflows in steps
        
        @NestedConfigurationProperty
        private ConditionalStepDef conditional; // Support for conditional logic
        
        @NestedConfigurationProperty
        private RetryDef retry; // Per-workflow-step retry override
        
        @NestedConfigurationProperty
        private ContextManagementDef contextManagement; // Context clearing configuration
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConditionalStepDef {
        @NestedConfigurationProperty
        private ConditionDef condition; // The condition to evaluate
        
        @NestedConfigurationProperty
        private WorkflowStepDef thenStep; // Step to execute if condition is true
        
        @NestedConfigurationProperty
        private WorkflowStepDef elseStep; // Step to execute if condition is false (optional)
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextManagementDef {
        /**
         * Whether to clear context before executing this step.
         * Default: false (keep all context)
         */
        private boolean clearBefore = false;
        
        /**
         * Whether to clear context after executing this step.
         * Default: false (keep all context)
         */
        private boolean clearAfter = false;
        
        /**
         * List of specific context keys to preserve when clearing context.
         * These keys will not be removed even if clearBefore or clearAfter is true.
         * Common keys to preserve: "systemPrompt", "isFirstInvocation", "agentName"
         */
        private List<String> preserveKeys;
        
        /**
         * List of specific context keys to remove when clearing context.
         * Only these keys will be removed, others will be preserved.
         * This takes precedence over clearBefore/clearAfter if specified.
         */
        private List<String> removeKeys;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConditionDef {
        private ConditionType type;
        private String field; // Field to check (e.g., "input", "context.userId", "previousResult")
        private String value; // Value to compare against
        private boolean ignoreCase = false; // For string comparisons
    }
    
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskDef {
        private String name;

        @NestedConfigurationProperty
        private WorkflowDef workflow;

    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerDef {
        private String name;
        
        @NestedConfigurationProperty
        private WorkflowDef workflow;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteDef {
        private String prompt;
        private String tool;
        
        @NestedConfigurationProperty
        private RetryDef retry; // Per-route retry override
    }
    
    /**
     * Retry configuration definition supporting multiple retry strategies.
     * Can be used at application-wide, agent-level, workflow-step, or route level.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetryDef {
        /**
         * Retry strategy to use (NONE, FIXED_DELAY, LINEAR, EXPONENTIAL, EXPONENTIAL_JITTER, CUSTOM)
         */
        private RetryStrategy strategy = RetryStrategy.EXPONENTIAL;
        
        /**
         * Maximum number of retry attempts (including the initial attempt)
         * Default: varies by strategy (see RetryStrategy.getRecommendedMaxAttempts())
         */
        private Integer maxAttempts;
        
        /**
         * Initial delay before first retry attempt in milliseconds
         * Default: varies by strategy (see RetryStrategy.getRecommendedInitialDelay())
         */
        private Long initialDelay;
        
        /**
         * Maximum delay between retry attempts in milliseconds
         * Used to cap exponential backoff growth
         * Default: 30000ms (30 seconds)
         */
        private Long maxDelay = 30000L;
        
        /**
         * Multiplier for exponential backoff strategies
         * Default: 2.0 (doubles the delay each attempt)
         */
        private Double multiplier = 2.0;
        
        /**
         * Fixed increment for linear backoff strategy in milliseconds
         * Default: 1000ms (1 second)
         */
        private Long increment = 1000L;
        
        /**
         * Jitter factor for EXPONENTIAL_JITTER strategy (0.0 to 1.0)
         * Adds randomization to prevent thundering herd problems
         * Default: 0.1 (10% jitter)
         */
        private Double jitterFactor = 0.1;
        
        /**
         * Custom retry configuration properties for CUSTOM strategy
         * Allows for completely customized retry behavior
         */
        private Map<String, Object> customProperties;
        
        /**
         * List of exception types that should trigger retries
         * If empty, all exceptions will trigger retries
         */
        private List<String> retryableExceptions;
        
        /**
         * List of exception types that should NOT trigger retries
         * Takes precedence over retryableExceptions
         */
        private List<String> nonRetryableExceptions;
        
        /**
         * Whether to enable retry for this configuration
         * Default: true (unless strategy is NONE)
         */
        private Boolean enabled;
        
        /**
         * Get the effective maximum attempts, using strategy defaults if not specified
         */
        public int getEffectiveMaxAttempts() {
            if (maxAttempts != null) {
                return maxAttempts;
            }
            return strategy != null ? strategy.getRecommendedMaxAttempts() : RetryStrategy.getDefault().getRecommendedMaxAttempts();
        }
        
        /**
         * Get the effective initial delay, using strategy defaults if not specified
         */
        public long getEffectiveInitialDelay() {
            if (initialDelay != null) {
                return initialDelay;
            }
            return strategy != null ? strategy.getRecommendedInitialDelay() : RetryStrategy.getDefault().getRecommendedInitialDelay();
        }
        
        /**
         * Check if retry is enabled for this configuration
         */
        public boolean isRetryEnabled() {
            if (enabled != null) {
                return enabled;
            }
            return strategy != null ? strategy.isRetryEnabled() : RetryStrategy.getDefault().isRetryEnabled();
        }
        
        /**
         * Get the effective retry strategy, using default if not specified
         */
        public RetryStrategy getEffectiveStrategy() {
            return strategy != null ? strategy : RetryStrategy.getDefault();
        }
    }
}
