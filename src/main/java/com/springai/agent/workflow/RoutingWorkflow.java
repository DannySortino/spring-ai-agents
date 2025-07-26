package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.RouteDef;
import com.springai.agent.config.AppProperties.ConditionalStepDef;
import com.springai.agent.config.AppProperties.ConditionDef;
import com.springai.agent.service.McpToolService;
import com.springai.agent.workflow.dependency.DependencyManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final DependencyManager dependencyManager;
    private final Map<String, List<String>> dependencies;
    private final boolean executeAllRoutes;
    
    /**
     * Creates a new RoutingWorkflow with the specified routes and MCP tool service.
     * Only one route will be selected and executed based on the input.
     * 
     * @param chatModel The chat model to use
     * @param routes Map of route names to route definitions
     * @param mcpToolService The MCP tool service to use for tool calls
     */
    public RoutingWorkflow(ChatModel chatModel, Map<String, RouteDef> routes, McpToolService mcpToolService) {
        this.chatModel = chatModel;
        this.routes = routes;
        this.mcpToolService = mcpToolService;
        this.dependencyManager = new DependencyManager();
        this.dependencies = new HashMap<>();
        this.executeAllRoutes = false;
    }
    
    /**
     * Creates a new RoutingWorkflow with the specified routes, dependencies, and MCP tool service.
     * Multiple routes may be executed in an order that respects their dependencies.
     * 
     * @param chatModel The chat model to use
     * @param routes Map of route names to route definitions
     * @param dependencies Map of route names to their dependency route names
     * @param mcpToolService The MCP tool service to use for tool calls
     * @param executeAllRoutes Whether to execute all routes or just the selected one
     */
    public RoutingWorkflow(ChatModel chatModel, Map<String, RouteDef> routes, 
                          Map<String, List<String>> dependencies, McpToolService mcpToolService,
                          boolean executeAllRoutes) {
        this.chatModel = chatModel;
        this.routes = routes;
        this.dependencies = dependencies;
        this.mcpToolService = mcpToolService;
        this.dependencyManager = new DependencyManager();
        this.executeAllRoutes = executeAllRoutes;
        
        // Validate dependencies
        validateDependencies();
    }
    
    /**
     * Backward compatibility constructor
     */
    public RoutingWorkflow(ChatModel chatModel, Map<String, RouteDef> routes) {
        this.chatModel = chatModel;
        this.routes = routes;
        this.mcpToolService = null;
        this.dependencyManager = new DependencyManager();
        this.dependencies = new HashMap<>();
        this.executeAllRoutes = false;
    }
    
    /**
     * Validates that all dependencies are correctly configured.
     * Throws an IllegalArgumentException if there are any validation errors.
     */
    private void validateDependencies() {
        if (routes.isEmpty() || dependencies.isEmpty()) {
            return;
        }
        
        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(
            routes.keySet(), dependencies);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Dependency validation failed: " + result.getErrorMessage());
        }
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        // Create a map to store route results
        Map<String, String> routeResults = new HashMap<>();
        
        // Create a shared context for routes
        Map<String, Object> sharedContext = new HashMap<>(context);
        
        // Determine the initial route based on input
        String selectedRoute = determineRoute(input);
        
        // If we're executing all routes with dependencies
        if (executeAllRoutes && !dependencies.isEmpty()) {
            try {
                // Get topological sort of routes (respects dependencies)
                List<String> executionOrder = dependencyManager.getTopologicalOrder(dependencies);
                
                // Execute routes in dependency order
                for (String routeName : executionOrder) {
                    // Skip routes that don't exist
                    if (!routes.containsKey(routeName)) {
                        continue;
                    }
                    
                    // Process dependencies for this route
                    processDependencies(routeName, sharedContext, routeResults);
                    
                    // Execute the route
                    String result = executeRoute(routeName, input, sharedContext);
                    
                    // Store the result
                    routeResults.put(routeName, result);
                }
                
                // Return the result of the selected route, or the last route if no selected route
                if (selectedRoute != null && routeResults.containsKey(selectedRoute)) {
                    return routeResults.get(selectedRoute);
                } else if (!routeResults.isEmpty()) {
                    // Return the last executed route's result
                    String lastRoute = executionOrder.stream()
                        .filter(routeResults::containsKey)
                        .reduce((first, second) -> second)
                        .orElse(null);
                    if (lastRoute != null) {
                        return routeResults.get(lastRoute);
                    }
                }
            } catch (IllegalArgumentException e) {
                // If there's a cycle in the dependencies, fall back to single route execution
                return executeSingleRoute(selectedRoute, input, sharedContext);
            }
        } else {
            // Execute only the selected route (traditional behavior)
            return executeSingleRoute(selectedRoute, input, sharedContext);
        }
        
        // Default fallback
        Prompt fallbackPrompt = new Prompt("I don't understand the request: " + input);
        return chatModel.call(fallbackPrompt).getResult().getOutput().getText();
    }
    
    /**
     * Executes a single route based on the route name.
     */
    private String executeSingleRoute(String routeName, String input, Map<String, Object> context) {
        if (routeName != null && routes.containsKey(routeName)) {
            return executeRoute(routeName, input, context);
        }
        
        // Default fallback
        Prompt fallbackPrompt = new Prompt("I don't understand the request: " + input);
        return chatModel.call(fallbackPrompt).getResult().getOutput().getText();
    }
    
    /**
     * Executes a specific route and returns the result.
     */
    private String executeRoute(String routeName, String input, Map<String, Object> context) {
        RouteDef route = routes.get(routeName);
        
        // Call the tool if specified in the route
        if (route.getTool() != null && !route.getTool().isEmpty()) {
            if (mcpToolService != null) {
                String toolResult = mcpToolService.callTool(route.getTool(), input, context);
                return "Route: " + routeName + " - Tool result: " + toolResult;
            } else {
                return "Route: " + routeName + " - Tool: " + route.getTool() + " (McpToolService not available)";
            }
        } else {
            // If no tool, use LLM to process the input directly
            String processedPrompt = processPrompt(route.getPrompt(), input, context);
            Prompt prompt = new Prompt(processedPrompt);
            String result = chatModel.call(prompt).getResult().getOutput().getText();
            return "Route: " + routeName + " - Result: " + result;
        }
    }
    
    /**
     * Processes dependencies for a route by making the results of its dependencies
     * available in the context.
     */
    private void processDependencies(String routeName, Map<String, Object> context, Map<String, String> routeResults) {
        List<String> routeDependencies = dependencies.getOrDefault(routeName, List.of());
        if (routeDependencies.isEmpty()) {
            return;
        }
        
        // Create a map of dependency results
        Map<String, Object> dependencyResults = new HashMap<>();
        for (String dependencyName : routeDependencies) {
            String result = routeResults.get(dependencyName);
            if (result != null) {
                dependencyResults.put(dependencyName, result);
            }
        }
        
        // Add dependency results to the context
        context.put("dependencyResults", dependencyResults);
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
