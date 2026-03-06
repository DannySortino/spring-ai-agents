package com.springai.agents.node;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A node that calls an MCP (Model Context Protocol) tool by name.
 * <p>
 * The tool is resolved at runtime via the
 * {@link com.springai.agents.mcp.McpClientToolResolver}. The executor uses
 * an LLM to construct a JSON tool-call input that conforms to the tool's
 * input schema, mapping the available context (dependency results, user input)
 * onto the tool's parameters.
 * <p>
 * An optional {@code guidance} field can steer how the LLM maps inputs onto
 * the tool's schema — for example, specifying which fields to populate or
 * what values to use.
 *
 * <pre>{@code
 * ToolNode.builder()
 *     .id("search")
 *     .toolName("web_search")
 *     .guidance("Use the 'query' field for the user question and set 'maxResults' to 5")
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ToolNode extends Node {

    /** Unique identifier for this tool node. */
    @NonNull
    String id;

    /** Name of the MCP tool to call. Must match a registered tool name. */
    @NonNull
    String toolName;

    /**
     * Optional guidance for the LLM when constructing the tool call.
     * <p>
     * When provided, the executor includes this guidance in the LLM prompt to steer
     * how available inputs are mapped onto the tool's input schema.
     * Supports {@code {nodeId}} placeholders.
     * <p>
     * Example: {@code "Use the 'query' field for the user question and set 'maxResults' to 5"}
     */
    String guidance;
}
