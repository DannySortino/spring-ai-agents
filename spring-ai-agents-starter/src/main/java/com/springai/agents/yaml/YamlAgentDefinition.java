package com.springai.agents.yaml;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * POJO representing a YAML-defined agent configuration.
 * <p>
 * Enables no-code/low-code agent creation via declarative YAML files.
 * 
 * <pre>{@code
 * # agents/customer-support.yaml
 * name: customer-support
 * description: Handles customer inquiries with sentiment analysis
 * 
 * workflows:
 *   - name: support-flow
 *     description: Main support workflow
 *     nodes:
 *       - id: input
 *         type: input
 *       - id: analyze-sentiment
 *         type: llm
 *         prompt: "Analyze the sentiment of: {input}"
 *         systemPrompt: "You are a sentiment analyzer. Respond with: positive, negative, or neutral"
 *       - id: generate-response
 *         type: llm
 *         prompt: "Given sentiment '{analyze-sentiment}', respond helpfully to: {input}"
 *         systemPrompt: "You are a friendly customer support agent"
 *       - id: output
 *         type: output
 *         
 *     edges:
 *       - from: input
 *         to: analyze-sentiment
 *       - from: analyze-sentiment
 *         to: generate-response
 *       - from: generate-response
 *         to: output
 * }</pre>
 */
@Data
public class YamlAgentDefinition {
    
    /** Unique name for this agent. Used for routing and MCP tool exposure. */
    private String name;
    
    /** Description of what this agent does. Used for LLM-based routing. */
    private String description;
    
    /** List of workflow definitions for this agent. */
    private List<WorkflowDefinition> workflows;
    
    /** Optional metadata tags. */
    private Map<String, String> metadata;
    
    @Data
    public static class WorkflowDefinition {
        /** Workflow name. */
        private String name;
        
        /** Workflow description for routing. */
        private String description;
        
        /** Nodes in this workflow. */
        private List<NodeDefinition> nodes;
        
        /** Edges connecting nodes. */
        private List<EdgeDefinition> edges;
    }
    
    @Data
    public static class NodeDefinition {
        /** Unique node ID within the workflow. */
        private String id;
        
        /** Node type: input, output, llm, rest, tool, context */
        private String type;
        
        // LLM node properties
        /** Prompt template for LLM nodes. Supports {nodeId} and {input} placeholders. */
        private String prompt;
        
        /** System prompt for LLM nodes. */
        private String systemPrompt;
        
        // REST node properties
        /** HTTP method for REST nodes. */
        private String method;
        
        /** URL template for REST nodes. */
        private String url;
        
        /** Request body template for REST nodes. */
        private String body;
        
        /** Headers for REST nodes. */
        private Map<String, String> headers;
        
        // Tool node properties
        /** Tool name for tool nodes. */
        private String toolName;
        
        /** Tool arguments template for tool nodes. */
        private String toolArgs;
        
        // Context node properties
        /** Context key for context nodes. */
        private String contextKey;
        
        /** Context value for context nodes. */
        private String contextValue;
        
        // Error handling
        /** Error strategy: FAIL_FAST, CONTINUE_WITH_DEFAULT, SKIP */
        private String errorStrategy;
        
        /** Default value when using CONTINUE_WITH_DEFAULT strategy. */
        private String defaultValue;
    }
    
    @Data
    public static class EdgeDefinition {
        /** Source node ID. */
        private String from;
        
        /** Target node ID. */
        private String to;
        
        /** Optional condition expression (SpEL). */
        private String condition;
    }
}
