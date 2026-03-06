package com.springai.agents.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

/**
 * Resolves MCP tools by name for use by
 * {@link com.springai.agents.executor.ToolExecutor}.
 * <p>
 * Aggregates tools from all registered {@link ToolCallbackProvider} beans, which may
 * include locally exposed agents and externally connected MCP servers. Tools are
 * lazily indexed by name on first access to avoid circular dependency issues during
 * auto-configuration.
 */
@Slf4j
public class McpClientToolResolver {

    private final ObjectProvider<ToolCallbackProvider> providerSource;
    private volatile Map<String, ToolCallback> toolMap;

    public McpClientToolResolver(ObjectProvider<ToolCallbackProvider> providerSource) {
        this.providerSource = providerSource;
    }

    /**
     * Resolve a tool by name.
     *
     * @return The ToolCallback, or {@code null} if not found.
     */
    public ToolCallback resolve(String toolName) {
        return getToolMap().get(toolName);
    }

    /** Check if a tool with the given name is available. */
    public boolean hasToolAvailable(String toolName) {
        return getToolMap().containsKey(toolName);
    }

    /** Get all available tool names. */
    public Set<String> getAvailableToolNames() {
        return Collections.unmodifiableSet(getToolMap().keySet());
    }

    /**
     * Lazily build the tool map on first access to avoid circular dependency
     * issues during Spring context initialization.
     */
    private Map<String, ToolCallback> getToolMap() {
        if (toolMap == null) {
            synchronized (this) {
                if (toolMap == null) {
                    Map<String, ToolCallback> map = new HashMap<>();
                    providerSource.orderedStream().forEach(provider -> {
                        try {
                            for (ToolCallback callback : provider.getToolCallbacks()) {
                                String name = callback.getToolDefinition().name();
                                map.put(name, callback);
                                log.debug("Indexed MCP tool '{}' from {}",
                                        name, provider.getClass().getSimpleName());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load tools from provider {}: {}",
                                    provider.getClass().getSimpleName(), e.getMessage());
                        }
                    });
                    toolMap = map;
                    log.info("McpClientToolResolver initialized with {} tools", map.size());
                }
            }
        }
        return toolMap;
    }
}
