package com.springai.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for agents.
 * Handles the top-level 'agents:' configuration in application.yml.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "agents")
public class AgentsProperties {

    // Getters and setters
    private List<AgentDef> list;
    
    // Default constructor
    public AgentsProperties() {}

    @Override
    public String toString() {
        return "AgentsProperties{" +
                "list=" + list +
                '}';
    }
    
    /**
     * Agent definition class.
     */
    @Setter
    @Getter
    public static class AgentDef {
        // Getters and setters
        private String name;
        private String model;
        private String systemPrompt;
        
        @NestedConfigurationProperty
        private WorkflowDef workflow;
        
        @NestedConfigurationProperty
        private McpServerDef mcpServer;
        
        @NestedConfigurationProperty
        private RetryDef retry;
        
        // Default constructor
        public AgentDef() {}

    }
    
    /**
     * MCP Server definition class.
     */
    @Setter
    @Getter
    public static class McpServerDef {
        // Getters and setters
        private boolean enabled = false;
        private Integer port;
        private String baseUrl;
        private String sseEndpoint = "/sse";
        private String sseMessageEndpoint = "/message";
        private String description;
        private String version = "1.0.0";
        
        // Default constructor
        public McpServerDef() {}

    }
    
    /**
     * Workflow definition class.
     */
    @Setter
    @Getter
    public static class WorkflowDef {
        // Getters and setters
        private WorkflowType type;
        private List<WorkflowStepDef> chain;
        private List<TaskDef> tasks;
        private String aggregator;
        private String managerPrompt;
        private List<WorkerDef> workers;
        private String synthesizerPrompt;
        private Map<String, RouteDef> routes;
        
        @NestedConfigurationProperty
        private WorkflowDef nestedWorkflow;
        
        // Default constructor
        public WorkflowDef() {}

    }
    
    /**
     * Workflow step definition class.
     */
    @Setter
    @Getter
    public static class WorkflowStepDef {
        // Getters and setters
        private String prompt;
        private String tool;
        private String nodeId;
        private List<String> dependsOn;
        
        @NestedConfigurationProperty
        private WorkflowDef nestedWorkflow;
        
        @NestedConfigurationProperty
        private ConditionalStepDef conditional;
        
        @NestedConfigurationProperty
        private RetryDef retry;
        
        @NestedConfigurationProperty
        private ContextManagementDef contextManagement;
        
        // Default constructor
        public WorkflowStepDef() {}

    }
    
    /**
     * Conditional step definition class.
     */
    @Setter
    @Getter
    public static class ConditionalStepDef {
        // Getters and setters
        @NestedConfigurationProperty
        private ConditionDef condition;
        
        @NestedConfigurationProperty
        private WorkflowStepDef thenStep;
        
        @NestedConfigurationProperty
        private WorkflowStepDef elseStep;
        
        // Default constructor
        public ConditionalStepDef() {}

    }
    
    /**
     * Context management definition class.
     */
    @Setter
    @Getter
    public static class ContextManagementDef {
        // Getters and setters
        private boolean clearBefore = false;
        private boolean clearAfter = false;
        private List<String> preserveKeys;
        private List<String> removeKeys;
        
        // Default constructor
        public ContextManagementDef() {}

    }
    
    /**
     * Condition definition class.
     */
    @Setter
    @Getter
    public static class ConditionDef {
        // Getters and setters
        private ConditionType type;
        private String field;
        private String value;
        private boolean ignoreCase = false;
        
        // Default constructor
        public ConditionDef() {}

    }
    
    /**
     * Task definition class.
     */
    @Setter
    @Getter
    public static class TaskDef {
        // Getters and setters
        private String name;
        
        @NestedConfigurationProperty
        private WorkflowDef workflow;
        
        // Default constructor
        public TaskDef() {}

    }
    
    /**
     * Worker definition class.
     */
    @Setter
    @Getter
    public static class WorkerDef {
        // Getters and setters
        private String name;
        
        @NestedConfigurationProperty
        private WorkflowDef workflow;
        
        // Default constructor
        public WorkerDef() {}

    }
    
    /**
     * Route definition class.
     */
    @Setter
    @Getter
    public static class RouteDef {
        // Getters and setters
        private String prompt;
        private String tool;
        
        @NestedConfigurationProperty
        private RetryDef retry;
        
        // Default constructor
        public RouteDef() {}

    }
    
    /**
     * Retry definition class.
     */
    @Setter
    @Getter
    public static class RetryDef {
        // Getters and setters
        private RetryStrategy strategy = RetryStrategy.NONE;
        private boolean enabled = false;
        private int maxAttempts = 3;
        private long initialDelay = 1000L;
        private long maxDelay = 30000L;
        private double multiplier = 2.0;
        private double jitterFactor = 0.1;
        private List<Class<? extends Exception>> retryableExceptions;
        private List<Class<? extends Exception>> nonRetryableExceptions;
        
        // Default constructor
        public RetryDef() {}

    }
}