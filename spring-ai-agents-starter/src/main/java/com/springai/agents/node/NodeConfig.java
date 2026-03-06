package com.springai.agents.node;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for node execution behavior, separate from lifecycle hooks.
 * <p>
 * Controls how the workflow executor handles errors during node execution.
 * Set on any node via the {@code config} builder field.
 *
 * <pre>{@code
 * LlmNode.builder()
 *     .id("analyze")
 *     .promptTemplate("Analyze: {input}")
 *     .config(NodeConfig.builder()
 *         .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
 *         .defaultValue("fallback result")
 *         .build())
 *     .build();
 * }</pre>
 *
 * @see Node#getConfig()
 * @see ErrorStrategy
 */
@Value
@Builder
public class NodeConfig {

    /**
     * Error handling strategy for this node. Defaults to {@link ErrorStrategy#FAIL_FAST}.
     */
    @Builder.Default
    ErrorStrategy errorStrategy = ErrorStrategy.FAIL_FAST;

    /**
     * Default value to use as the node's result when {@code errorStrategy} is
     * {@link ErrorStrategy#CONTINUE_WITH_DEFAULT} and the node execution fails.
     */
    @Builder.Default
    Object defaultValue = "";
}

