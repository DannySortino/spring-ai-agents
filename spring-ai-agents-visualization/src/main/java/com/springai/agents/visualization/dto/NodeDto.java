package com.springai.agents.visualization.dto;

import com.springai.agents.node.*;
import lombok.Builder;
import lombok.Value;

/**
 * DTO for a single node in a workflow graph.
 */
@Value
@Builder
public class NodeDto {
    String id;
    String type;
    int level;

    // LlmNode fields
    String promptTemplate;
    String systemPrompt;

    // RestNode fields
    String url;
    String method;

    // ContextNode fields
    String contextText;

    // ToolNode fields
    String toolName;
    String guidance;

    // OutputNode fields
    String postProcessPrompt;
    boolean hasOutputHandler;
    String outputStrategy;

    // Hooks info
    boolean hasHooks;
    String errorStrategy;

    /**
     * Convert a domain Node + level into a NodeDto using Java 21 pattern matching.
     */
    public static NodeDto from(Node node, int level) {
        var b = NodeDto.builder().id(node.getId()).level(level);

        // Hooks info
        NodeHooks hooks = node.getHooks();
        if (hooks != null) {
            b.hasHooks(true);
        }

        // Error strategy from config
        NodeConfig config = node.getConfig();
        if (config != null && config.getErrorStrategy() != null) {
            b.errorStrategy(config.getErrorStrategy().name());
        }

        return switch (node) {
            case InputNode n -> b.type("INPUT").build();
            case OutputNode n -> {
                boolean hasHandler = n.getOutputHandler() != null;
                String strategy = hasHandler ? "Custom Handler"
                        : n.getPostProcessPrompt() != null ? "LLM Post-Process"
                        : "Pass-Through";
                yield b.type("OUTPUT")
                        .postProcessPrompt(n.getPostProcessPrompt())
                        .hasOutputHandler(hasHandler)
                        .outputStrategy(strategy)
                        .build();
            }
            case LlmNode n -> b.type("LLM")
                    .promptTemplate(n.getPromptTemplate())
                    .systemPrompt(n.getSystemPrompt())
                    .build();
            case RestNode n -> b.type("REST")
                    .url(n.getUrl())
                    .method(n.getMethod().name())
                    .build();
            case ContextNode n -> b.type("CONTEXT")
                    .contextText(n.getContextText())
                    .build();
            case ToolNode n -> b.type("TOOL")
                    .toolName(n.getToolName())
                    .guidance(n.getGuidance())
                    .build();
            default -> b.type("CUSTOM").build();
        };
    }
}

