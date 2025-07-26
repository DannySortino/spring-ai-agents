package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.RouteDef;
import com.springai.agent.config.AppProperties.ConditionalStepDef;
import com.springai.agent.config.AppProperties.ConditionDef;
import com.springai.agent.service.McpToolService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Workflow implementation that routes input to different processing paths based on content analysis.
 * 
 * This workflow analyzes the input and dynamically selects the most appropriate route
 * for processing. It supports multiple routing strategies and execution modes:
 * 
 * Key features:
 * - Keyword-based route determination from input content
 * - Support for both tool-based and prompt-based route execution
 * - MCP tool integration for external tool calls
 * - Context variable substitution in prompts
 * - Fallback handling for unmatched inputs
 * - Backward compatibility with different constructor options
 * 
 * Route Selection Process:
 * 1. Analyzes input content for keyword matches
 * 2. Selects the first matching route based on configured routes
 * 3. Falls back to the first available route if no match is found
 * 4. Provides default error handling for completely unmatched inputs
 * 
 * Each route can specify either:
 * - A tool name for MCP tool execution
 * - A prompt template for chat model processing
 * 
 * This workflow is ideal for applications that need to handle diverse input types
 * and route them to specialized processing logic.
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public class RoutingWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final Map<String, RouteDef> routes;
    private final McpToolService mcpToolService;
    
    public RoutingWorkflow(ChatModel chatModel, Map<String, RouteDef> routes, McpToolService mcpToolService) {
        this.chatModel = chatModel;
        this.routes = routes;
        this.mcpToolService = mcpToolService;
    }
    
    // Backward compatibility constructor
    public RoutingWorkflow(ChatModel chatModel, Map<String, RouteDef> routes) {
        this.chatModel = chatModel;
        this.routes = routes;
        this.mcpToolService = null;
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        // For simplicity, we'll determine the route based on keywords in the input
        // In a real implementation, this could use more sophisticated routing logic
        String selectedRoute = determineRoute(input);
        
        if (selectedRoute != null && routes.containsKey(selectedRoute)) {
            RouteDef route = routes.get(selectedRoute);
            
            // Call the tool if specified in the route - pass input directly without field extraction
            if (route.getTool() != null && !route.getTool().isEmpty()) {
                if (mcpToolService != null) {
                    String toolResult = mcpToolService.callTool(route.getTool(), input, context);
                    return "Route: " + selectedRoute + " - Tool result: " + toolResult;
                } else {
                    return "Route: " + selectedRoute + " - Tool: " + route.getTool() + " (McpToolService not available)";
                }
            } else {
                // If no tool, use LLM to process the input directly
                String processedPrompt = processPrompt(route.getPrompt(), input, context);
                Prompt prompt = new Prompt(processedPrompt);
                String result = chatModel.call(prompt).getResult().getOutput().getText();
                return "Route: " + selectedRoute + " - Result: " + result;
            }
        }
        
        // Default fallback
        Prompt fallbackPrompt = new Prompt("I don't understand the request: " + input);
        return chatModel.call(fallbackPrompt).getResult().getOutput().getText();
    }
    
    private String determineRoute(String input) {
        String lowerInput = input.toLowerCase();
        
        // Simple keyword-based routing
        for (String routeName : routes.keySet()) {
            if (lowerInput.contains(routeName.toLowerCase())) {
                return routeName;
            }
        }
        
        // Return the first route as default if no match found
        return routes.keySet().iterator().hasNext() ? 
            routes.keySet().iterator().next() : null;
    }
    
    
    private String processPrompt(String prompt, String input, Map<String, Object> context) {
        String processed = prompt.replace("{input}", input);
        
        // Replace context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        return processed;
    }
}
