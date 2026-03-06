package com.springai.agents.node;

/**
 * Strategy for handling errors during node execution.
 * <p>
 * Set on {@link NodeConfig#getErrorStrategy()} to control how the workflow
 * executor reacts when a node fails.
 *
 * <pre>{@code
 * NodeConfig.builder()
 *     .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
 *     .defaultValue("N/A")
 *     .build();
 * }</pre>
 *
 * @see NodeConfig
 */
public enum ErrorStrategy {

    /** Fail immediately and propagate the exception. This is the default behavior. */
    FAIL_FAST,

    /**
     * Catch the exception and store a default value as the node's result.
     * The workflow continues executing downstream nodes.
     * Set the default value via {@link NodeConfig#getDefaultValue()}.
     */
    CONTINUE_WITH_DEFAULT,

    /**
     * Catch the exception and skip this node entirely (store {@code null}).
     * Downstream nodes will see {@code null} in their dependency results.
     */
    SKIP
}

