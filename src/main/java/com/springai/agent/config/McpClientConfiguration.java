package com.springai.agent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for Spring AI MCP Client that connects to external MCP servers.
 * Uses the official spring-ai-mcp dependency for MCP client functionality.
 * 
 * The Spring AI MCP client auto-configuration will handle the actual MCP client creation
 * and connection management. This configuration provides supporting services.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpClientConfiguration {
    
    /**
     * WebClient builder for MCP client connections
     */
    @Bean
    public WebClient.Builder mcpWebClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)); // 1MB
    }
    
    /**
     * MCP Client Service for managing connections to external MCP servers
     * This service will work with Spring AI's auto-configured MCP clients
     * Only created when MCP client is enabled
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true", matchIfMissing = false)
    public McpClientService mcpClientService(@Autowired(required = false) SyncMcpToolCallbackProvider syncMcpToolCallbackProvider) {
        log.info("Spring AI MCP Client is enabled - creating McpClientService");
        return new McpClientService(syncMcpToolCallbackProvider);
    }
    
    /**
     * Service class for managing MCP client connections
     * Now uses actual Spring AI MCP client integration
     */
    public static class McpClientService {
        
        private final Map<String, String> serverConnections = new ConcurrentHashMap<>();
        private final SyncMcpToolCallbackProvider syncMcpToolCallbackProvider;
        
        public McpClientService(SyncMcpToolCallbackProvider syncMcpToolCallbackProvider) {
            this.syncMcpToolCallbackProvider = syncMcpToolCallbackProvider;
        }
        
        /**
         * Register an external MCP server connection
         */
        public boolean connectToServer(String serverName, String serverUrl) {
            try {
                log.info("Registering MCP server '{}' at {}", serverName, serverUrl);
                
                // Store the server connection info
                // Spring AI auto-configuration will handle the actual connection
                serverConnections.put(serverName, serverUrl);
                
                log.info("Successfully registered MCP server '{}'", serverName);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to register MCP server '{}': {}", serverName, e.getMessage(), e);
                return false;
            }
        }
        
        /**
         * Discover available tools from MCP servers
         */
        public Map<String, Map<String, Object>> discoverAvailableTools() {
            Map<String, Map<String, Object>> discoveredTools = new ConcurrentHashMap<>();
            
            if (syncMcpToolCallbackProvider != null) {
                try {
                    ToolCallback[] mcpTools = syncMcpToolCallbackProvider.getToolCallbacks();
                    log.info("Discovering {} available MCP tools", mcpTools.length);
                    
                    for (var toolCallback : mcpTools) {
                        try {
                            // Extract tool information from the callback
                            String toolName = toolCallback.getToolDefinition().name();
                            Map<String, Object> toolSchema = extractToolSchema(toolCallback);
                            
                            if (toolSchema != null) {
                                discoveredTools.put(toolName, toolSchema);
                                log.debug("Discovered tool '{}' with schema: {}", toolName, toolSchema);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to extract tool information from callback: {}", e.getMessage());
                        }
                    }
                    
                    log.info("Successfully discovered {} MCP tools", discoveredTools.size());
                } catch (Exception e) {
                    log.error("Error discovering MCP tools: {}", e.getMessage(), e);
                }
            } else {
                log.warn("SyncMcpToolCallbackProvider not available - cannot discover tools");
            }
            
            return discoveredTools;
        }
        
        /**
         * Get schema for a specific tool
         */
        public Map<String, Object> getToolSchema(String toolName) {
            Map<String, Map<String, Object>> allTools = discoverAvailableTools();
            return allTools.get(toolName);
        }
        
        /**
         * Extract tool schema from tool callback using proper ToolCallback API
         */
        private Map<String, Object> extractToolSchema(ToolCallback toolCallback) {
            Map<String, Object> schema = new ConcurrentHashMap<>();
            
            try {
                // Use proper ToolCallback API to get tool definition
                var toolDefinition = toolCallback.getToolDefinition();
                
                // Extract description using proper API
                String description = toolDefinition.description();
                if (!description.isEmpty()) {
                    schema.put("description", description);
                }
                
                // Extract input schema using proper API
                String inputSchema = toolDefinition.inputSchema();
                if (!inputSchema.isEmpty()) {
                    schema.put("inputSchema", inputSchema);
                }
                
                // Extract tool name for reference
                String toolName = toolDefinition.name();
                if (!toolName.isEmpty()) {
                    schema.put("name", toolName);
                }
                
                // Add tool metadata if available
                try {
                    var toolMetadata = toolCallback.getToolMetadata();
                    schema.put("metadata", toolMetadata.toString());
                } catch (Exception e) {
                    log.debug("Could not extract tool metadata: {}", e.getMessage());
                }
                
                return schema.isEmpty() ? null : schema;
                
            } catch (Exception e) {
                log.error("Error extracting tool schema using ToolCallback API: {}", e.getMessage(), e);
                return null;
            }
        }

        /**
         * Call a tool on an external MCP server using Spring AI MCP client integration
         */
        public String callExternalTool(String serverName, String toolName, String input, Map<String, Object> context) {
            try {
                String serverUrl = serverConnections.get(serverName);
                if (serverUrl == null) {
                    return "Error: MCP server '" + serverName + "' not registered";
                }
                
                log.info("Calling external tool '{}' on server '{}' with input: {}", 
                    toolName, serverName, input);
                
                // Use actual Spring AI MCP client integration
                if (syncMcpToolCallbackProvider != null) {
                    try {
                        // Get available MCP tools from the provider
                        var mcpTools = syncMcpToolCallbackProvider.getToolCallbacks();
                        
                        log.info("Spring AI MCP Client is active with {} available tools", mcpTools.length);
                        
                        // Try to find and execute the specific tool
                        for (var toolCallback : mcpTools) {
                            // Use proper ToolCallback API to get tool name
                            String callbackToolName = toolCallback.getToolDefinition().name();
                            if (toolName.equals(callbackToolName)) {
                                // Found the tool, try to execute it
                                try {
                                    // Use proper ToolCallback API to call the tool
                                    String result = toolCallback.call(input);
                                    
                                    log.info("External tool '{}' on server '{}' completed successfully", 
                                        toolName, serverName);
                                    
                                    return result;
                                    
                                } catch (Exception e) {
                                    log.error("Error executing tool callback for '{}': {}", toolName, e.getMessage());
                                    return "Error executing tool: " + e.getMessage();
                                }
                            }
                        }
                        
                        // Tool not found
                        return "Error: Tool '" + toolName + "' not found on MCP server '" + serverName + "'";
                        
                    } catch (Exception e) {
                        log.error("Error executing MCP tool '{}' on server '{}': {}", 
                            toolName, serverName, e.getMessage(), e);
                        return "Error executing MCP tool: " + e.getMessage();
                    }
                } else {
                    log.warn("SyncMcpToolCallbackProvider not available - MCP client may not be properly configured");
                    return "Error: MCP client not properly configured";
                }
                
            } catch (Exception e) {
                log.error("Error calling external tool '{}' on server '{}': {}", 
                    toolName, serverName, e.getMessage(), e);
                return "Error calling external tool: " + e.getMessage();
            }
        }
        
        /**
         * Get list of registered servers
         */
        public Map<String, String> getRegisteredServers() {
            return Map.copyOf(serverConnections);
        }
        
        /**
         * Unregister an MCP server
         */
        public boolean disconnectFromServer(String serverName) {
            String serverUrl = serverConnections.remove(serverName);
            if (serverUrl != null) {
                log.info("Unregistered MCP server '{}'", serverName);
                return true;
            }
            return false;
        }
        
        /**
         * Check if a server is registered
         */
        public boolean isServerConnected(String serverName) {
            return serverConnections.containsKey(serverName);
        }
    }
}
