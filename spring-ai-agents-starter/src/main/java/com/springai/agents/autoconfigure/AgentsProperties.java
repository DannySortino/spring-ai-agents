package com.springai.agents.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Spring AI Agents framework.
 * <p>
 * Prefix: {@code spring.ai.agents}
 *
 * <pre>
 * spring:
 *   ai:
 *     agents:
 *       reactive: false
 *       mcp-server:
 *         enabled: true
 *       parallel-threads: 0
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spring.ai.agents")
public class AgentsProperties {

    /** Whether to use reactive execution mode. Default: false (synchronous). */
    private boolean reactive = false;

    /** Number of threads for parallel node execution. 0 = cached thread pool (default). */
    private int parallelThreads = 0;

    /** MCP Server configuration. */
    private McpServer mcpServer = new McpServer();

    /**
     * MCP Server sub-properties.
     */
    @Getter
    @Setter
    public static class McpServer {
        /** Whether to expose agents as MCP server tools. Default: false. */
        private boolean enabled = false;
    }
}

