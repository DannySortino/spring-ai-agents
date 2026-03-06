package com.springai.agents.executor;

import com.springai.agents.mcp.McpClientToolResolver;
import com.springai.agents.node.ToolNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static com.springai.agents.executor.PromptInterpolator.interpolate;

/**
 * Executor for {@link ToolNode}. Resolves the MCP tool by name via
 * {@link McpClientToolResolver}, then uses the LLM to construct a tool call
 * that matches the tool's input schema.
 * <p>
 * The executor reads the tool's {@link ToolDefinition#inputSchema()} and asks
 * the LLM to map the available inputs (dependency results, user input, and
 * optional guidance) into a valid JSON payload conforming to that schema.
 * <p>
 * If the node has {@code guidance}, it is included in the LLM prompt to steer
 * how inputs are mapped onto the tool's parameters.
 */
@Slf4j
@RequiredArgsConstructor
public class ToolExecutor implements NodeExecutor<ToolNode> {

    private final McpClientToolResolver toolResolver;
    private final ChatModel chatModel;

    private static final String TOOL_CALL_SYSTEM_PROMPT = """
            You are a tool-call constructor. Your ONLY job is to produce a valid JSON \
            object that conforms to the tool's input schema. Do NOT add any explanation, \
            markdown fencing, or text outside the JSON object. Respond with ONLY the raw \
            JSON object.""";

    private static final String TOOL_CALL_USER_TEMPLATE = """
            Construct a JSON tool-call input for the following tool.
            
            Tool name: %s
            Tool description: %s
            Tool input schema:
            %s
            
            Available context / user input:
            %s
            %s
            Respond with ONLY the JSON object matching the schema above.""";

    @Override
    public Object execute(ToolNode node, NodeContext context) {
        if (toolResolver == null) {
            throw new IllegalStateException(
                    "McpClientToolResolver is not configured. Cannot execute ToolNode '" + node.getId() + "'");
        }

        ToolCallback callback = toolResolver.resolve(node.getToolName());
        if (callback == null) {
            throw new IllegalStateException("MCP tool not found: " + node.getToolName());
        }

        // Use the resolved input from dependencies
        String resolvedInput = context.getResolvedInput();

        // Build the tool call JSON via LLM
        String toolCallJson = buildToolCallJson(node, callback, resolvedInput, context);

        log.debug("ToolNode '{}': calling tool '{}' with constructed input: {}",
                node.getId(), node.getToolName(), toolCallJson);

        return callback.call(toolCallJson);
    }

    @Override
    public Class<ToolNode> getNodeType() {
        return ToolNode.class;
    }

    /**
     * Uses the LLM to construct a JSON tool call that matches the tool's input schema.
     */
    private String buildToolCallJson(ToolNode node, ToolCallback callback,
                                     String resolvedInput, NodeContext context) {
        ToolDefinition toolDef = callback.getToolDefinition();
        String inputSchema = toolDef.inputSchema();
        String toolDescription = toolDef.description() != null ? toolDef.description() : "(no description)";

        // Resolve guidance if provided
        String guidanceSection = "";
        if (node.getGuidance() != null && !node.getGuidance().isBlank()) {
            String resolvedGuidance = interpolate(
                    node.getGuidance(), context.getDependencyResults(), context.getExecutionContext());
            guidanceSection = "\nTool usage guidance:\n" + resolvedGuidance + "\n";
        }

        String userPrompt = TOOL_CALL_USER_TEMPLATE.formatted(
                node.getToolName(),
                toolDescription,
                inputSchema,
                resolvedInput,
                guidanceSection
        );

        String fullPrompt = TOOL_CALL_SYSTEM_PROMPT + "\n\n" + userPrompt;

        log.debug("ToolNode '{}': asking LLM to construct tool call for schema: {}",
                node.getId(), inputSchema);

        String llmResponse = chatModel.call(new Prompt(fullPrompt))
                .getResult().getOutput().getText().trim();

        // Strip markdown fences if the LLM added them despite instructions
        llmResponse = stripMarkdownFences(llmResponse);

        return llmResponse;
    }

    /** Strips ```json ... ``` fences if present. */
    private static String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        return text;
    }
}
