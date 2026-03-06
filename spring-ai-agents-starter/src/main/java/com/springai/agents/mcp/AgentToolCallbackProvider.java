package com.springai.agents.mcp;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.agent.AgentRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes all registered agents as MCP server tools via Spring AI's {@link ToolCallbackProvider}.
 * <p>
 * Each agent becomes a callable MCP tool with:
 * <ul>
 *   <li>Tool name = agent name</li>
 *   <li>Tool description = agent description</li>
 *   <li>Tool input = JSON object with a single "input" string field</li>
 *   <li>Tool execution = delegates to {@link AgentRuntime#invoke(String)}</li>
 * </ul>
 * <p>
 * Spring AI's MCP Server Boot Starter automatically discovers {@link ToolCallbackProvider}
 * beans and registers their tools — no manual wiring required.
 * <p>
 * Conditionally created when {@code spring.ai.agents.mcp-server.enabled=true}.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentToolCallbackProvider implements ToolCallbackProvider {

    private final AgentRegistry agentRegistry;

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();

        for (AgentRuntime runtime : agentRegistry.getSyncAgents().values()) {
            callbacks.add(createToolCallback(runtime));
            log.info("Exposed agent '{}' as MCP tool: {}", runtime.getName(), runtime.getDescription());
        }

        return callbacks.toArray(new ToolCallback[0]);
    }

    private ToolCallback createToolCallback(AgentRuntime runtime) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                String inputSchema = """
                        {
                            "type": "object",
                            "properties": {
                                "input": {
                                    "type": "string",
                                    "description": "Input text for the %s agent"
                                }
                            },
                            "required": ["input"]
                        }
                        """.formatted(runtime.getName());

                return ToolDefinition.builder()
                        .name(runtime.getName())
                        .description(runtime.getDescription())
                        .inputSchema(inputSchema)
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                log.debug("MCP tool call → agent '{}', input length={}", runtime.getName(), toolInput.length());
                try {
                    return runtime.invoke(toolInput);
                } catch (Exception e) {
                    log.error("MCP tool call failed for agent '{}': {}", runtime.getName(), e.getMessage(), e);
                    return "Error executing agent '%s': %s".formatted(runtime.getName(), e.getMessage());
                }
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}

