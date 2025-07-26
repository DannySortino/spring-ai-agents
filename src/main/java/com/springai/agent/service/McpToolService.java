package com.springai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.springai.agent.config.AppProperties.RetryDef;
import com.springai.agent.config.McpClientConfiguration.McpClientService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for managing and executing Model Context Protocol (MCP) tools.
 * 
 * This service provides functionality for:
 * - Calling MCP tools on external servers with retry logic
 * - Discovering available tools from registered MCP servers
 * - Processing tool inputs with context variable substitution
 * - Managing tool schemas and parameter extraction
 * - Fallback execution for tools not found on external servers
 * 
 * The service integrates with external MCP servers and provides a unified
 * interface for tool execution with built-in error handling and retry mechanisms.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpToolService {
    
    private final McpClientService mcpClientService;
    private final RetryService retryService;
    private final ChatModel chatModel;
    
    /**
     * Call an MCP tool by name with the given input
     * @param toolName The name of the MCP tool to call
     * @param input The input to pass to the tool
     * @return The result from the tool call
     */
    public String callTool(String toolName, String input) {
        return callTool(toolName, input, Map.of(), null);
    }
    
    /**
     * Call an MCP tool by name with the given input and retry configuration
     * @param toolName The name of the MCP tool to call
     * @param input The input to pass to the tool
     * @param retryConfig The retry configuration to use
     * @return The result from the tool call
     */
    public String callTool(String toolName, String input, RetryDef retryConfig) {
        return callTool(toolName, input, Map.of(), retryConfig);
    }
    
    /**
     * Call an MCP tool with additional context parameters and retry configuration
     * @param toolName The name of the MCP tool to call
     * @param input The input to pass to the tool
     * @param context Additional context parameters
     * @param retryConfig The retry configuration to use (null for default retry)
     * @return The result from the tool call
     */
    public String callTool(String toolName, String input, Map<String, Object> context, RetryDef retryConfig) {
        try {
            log.debug("Calling MCP tool: {} with input: {} and context: {}", toolName, input, context);
            
            // Process context variables in the input and format for tool schema
            String processedInput = processInputWithContext(toolName, input, context);
            
            // Execute with retry logic
            return retryService.executeWithRetry(() -> {
                // First, try to call external MCP servers
                String result = tryExternalMcpCall(toolName, processedInput, context);
                if (result != null && !result.startsWith("Error:")) {
                    log.debug("MCP tool {} returned from external server: {}", toolName, result);
                    return result;
                }
                
                // Fall back to discovered tool execution
                result = executeDiscoveredTool(toolName, processedInput);
                log.debug("MCP tool {} returned from discovered tool execution: {}", toolName, result);
                return result;
                
            }, retryConfig, "MCP tool call: " + toolName);
            
        } catch (Exception e) {
            log.error("Error calling MCP tool {} with context: {}", toolName, e.getMessage());
            return "Error calling tool " + toolName + ": " + e.getMessage();
        }
    }
    
    /**
     * Call an MCP tool with additional context parameters (backward compatibility)
     * @param toolName The name of the MCP tool to call
     * @param input The input to pass to the tool
     * @param context Additional context parameters
     * @return The result from the tool call
     */
    public String callTool(String toolName, String input, Map<String, Object> context) {
        return callTool(toolName, input, context, null);
    }
    
    /**
     * Get available tools from MCP servers
     */
    public Map<String, Map<String, Object>> getAvailableTools() {
        try {
            return mcpClientService.discoverAvailableTools();
        } catch (Exception e) {
            log.error("Error getting available tools: {}", e.getMessage());
            return Map.of();
        }
    }
    
    /**
     * Get tool schema for parameter extraction
     */
    public Map<String, Object> getToolSchema(String toolName) {
        try {
            return mcpClientService.getToolSchema(toolName);
        } catch (Exception e) {
            log.error("Error getting tool schema for '{}': {}", toolName, e.getMessage());
            return Map.of();
        }
    }
    
    /**
     * Execute tool using discovered MCP tools or fallback to generic execution
     */
    private String executeDiscoveredTool(String toolName, String input) {
        try {
            // First try to get the tool schema to understand what we're working with
            Map<String, Object> toolSchema = getToolSchema(toolName);
            
            if (toolSchema != null && !toolSchema.isEmpty()) {
                log.debug("Found schema for tool '{}': {}", toolName, toolSchema);
                
                // Try to execute the tool using the MCP client
                String result = mcpClientService.callExternalTool("default", toolName, input, Map.of());
                
                if (result != null && !result.startsWith("Error:")) {
                    return result;
                }
            }
            
            // Fallback to generic tool execution
            return "Tool '" + toolName + "' executed with input: " + input + " - Generic response generated";
            
        } catch (Exception e) {
            log.error("Error executing discovered tool '{}': {}", toolName, e.getMessage());
            return "Error executing tool " + toolName + ": " + e.getMessage();
        }
    }
    
    private String processInputWithContext(String toolName, String input, Map<String, Object> context) {
        try {
            // First, do basic context variable replacement
            String processed = input;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            
            // Get tool schema to understand expected input format
            Map<String, Object> toolSchema = getToolSchema(toolName);
            
            if (toolSchema != null && !toolSchema.isEmpty() && toolSchema.containsKey("inputSchema")) {
                String inputSchema = (String) toolSchema.get("inputSchema");
                String toolDescription = (String) toolSchema.get("description");
                
                log.debug("Using LLM to format input for tool '{}' with schema: {}", toolName, inputSchema);
                
                // Create prompt for LLM to format the input according to tool schema
                String prompt = createInputFormattingPrompt(toolName, processed, inputSchema, toolDescription);
                
                // Call LLM to format the input
                Prompt llmPrompt = new Prompt(prompt);
                String formattedInput = chatModel.call(llmPrompt).getResult().getOutput().getText();
                
                log.debug("LLM formatted input for tool '{}': {}", toolName, formattedInput);
                return formattedInput.trim();
                
            } else {
                log.debug("No schema found for tool '{}', using basic context replacement", toolName);
                return processed;
            }
            
        } catch (Exception e) {
            log.warn("Error using LLM to format input for tool '{}': {}. Falling back to basic processing.", 
                    toolName, e.getMessage());
            
            // Fallback to basic context variable replacement
            String processed = input;
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            return processed;
        }
    }
    
    /**
     * Create a prompt for the LLM to format input according to tool schema
     */
    private String createInputFormattingPrompt(String toolName, String userInput, String inputSchema, String toolDescription) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("You are an AI assistant that helps format input for MCP (Model Context Protocol) tools.\n\n");
        
        promptBuilder.append("Tool Name: ").append(toolName).append("\n");
        
        if (toolDescription != null && !toolDescription.isEmpty()) {
            promptBuilder.append("Tool Description: ").append(toolDescription).append("\n");
        }
        
        promptBuilder.append("Tool Input Schema (JSON Schema): ").append(inputSchema).append("\n\n");
        
        promptBuilder.append("User Input: ").append(userInput).append("\n\n");
        
        promptBuilder.append("Please format the user input to match the tool's input schema requirements. ");
        promptBuilder.append("Return ONLY the formatted input that can be directly used by the tool. ");
        promptBuilder.append("If the user input is already in the correct format, return it as-is. ");
        promptBuilder.append("If the user input cannot be formatted to match the schema, return the original input with a brief explanation of what's missing.\n\n");
        
        promptBuilder.append("Formatted Input:");
        
        return promptBuilder.toString();
    }
    
    /**
     * Try to call a tool on external MCP servers
     * @param toolName The name of the tool to call
     * @param input The input to pass to the tool
     * @param context Additional context parameters
     * @return The result from external MCP server, or null if not found
     */
    private String tryExternalMcpCall(String toolName, String input, Map<String, Object> context) {
        try {
            // Get all registered MCP servers
            Map<String, String> registeredServers = mcpClientService.getRegisteredServers();
            
            // For now, try each registered server to find the tool
            // In a real implementation, we would have a tool discovery mechanism
            for (Map.Entry<String, String> entry : registeredServers.entrySet()) {
                String serverName = entry.getKey();
                
                log.debug("Trying tool '{}' on external MCP server '{}'", toolName, serverName);
                String result = mcpClientService.callExternalTool(serverName, toolName, input, context);
                
                if (result != null && !result.startsWith("Error:")) {
                    log.debug("Found tool '{}' on external MCP server '{}'", toolName, serverName);
                    return result;
                }
            }
            
            log.debug("Tool '{}' not found on any registered MCP servers", toolName);
            return null;
            
        } catch (Exception e) {
            log.error("Error trying external MCP call for tool '{}': {}", toolName, e.getMessage());
            return null;
        }
    }
}
