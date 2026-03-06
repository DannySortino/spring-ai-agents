package com.springai.agents.node;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A node that sends an interpolated prompt to a Large Language Model and returns the response.
 * <p>
 * The {@code promptTemplate} supports placeholder interpolation:
 * <ul>
 *   <li>{@code {input}} — the raw user input</li>
 *   <li>{@code {nodeId}} — the output of a dependency node, where nodeId matches a predecessor's ID</li>
 * </ul>
 *
 * <pre>{@code
 * LlmNode.builder()
 *     .id("analyze")
 *     .promptTemplate("Analyze the following text and extract key themes: {user-input}")
 *     .systemPrompt("You are a text analysis expert.")
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class LlmNode extends Node {

    /** Unique identifier for this LLM node. */
    @NonNull
    String id;

    /**
     * Prompt template sent to the LLM. Supports {@code {nodeId}} and {@code {input}} placeholders.
     */
    @NonNull
    String promptTemplate;

    /**
     * Optional system prompt prepended to the LLM call.
     * Sets the behavior/persona of the LLM for this node.
     */
    String systemPrompt;
}
