package com.springai.agent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI MCP Server that exposes agents as MCP tools.
 * Uses the official spring-ai-mcp dependency for MCP server functionality.
 * 
 * NOTE: This configuration has been simplified to avoid conflicts with AgentMcpServerConfiguration.
 * All MCP function bean creation is now handled by AgentMcpServerConfiguration which creates
 * dynamic function beans based on actual agent configuration.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpToolConfiguration {
    
    // This configuration class is kept for potential future MCP tool utilities
    // but all agent function bean creation is now handled by AgentMcpServerConfiguration
    
}
