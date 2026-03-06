package com.springai.agents.node;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A node that injects static text context into the workflow without making external calls.
 * <p>
 * The {@code contextText} is returned as-is as the node's output, making it available
 * to downstream nodes via {@code {nodeId}} placeholders.
 * <p>
 * Use cases:
 * <ul>
 *   <li>Injecting system instructions or domain knowledge</li>
 *   <li>Adding static reference data for LLM nodes</li>
 *   <li>Providing configuration or guidelines to downstream processing</li>
 * </ul>
 *
 * <pre>{@code
 * ContextNode.builder()
 *     .id("guidelines")
 *     .contextText("Always respond in a professional tone. Cite sources where possible.")
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ContextNode extends Node {

    /** Unique identifier for this context node. */
    @NonNull
    String id;

    /** The static text content to inject into the workflow. */
    @NonNull
    String contextText;
}
