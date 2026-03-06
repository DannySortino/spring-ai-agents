package com.springai.agents.node;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all workflow nodes in the DAG.
 * <p>
 * A node represents a single unit of work within a workflow graph. Nodes are
 * immutable data descriptions — they define WHAT to do, not HOW. Behavior is
 * provided by the corresponding {@link com.springai.agents.executor.NodeExecutor}.
 * <p>
 * All concrete node implementations use Lombok {@code @Value @SuperBuilder} for
 * clean construction and immutability. The {@link NodeHooks} and {@link NodeConfig}
 * fields are defined here so every node type inherits them automatically.
 * <p>
 * Custom node types should extend this class (annotated with
 * {@code @Value @SuperBuilder}) and provide a corresponding
 * {@link com.springai.agents.executor.NodeExecutor} registered as a Spring bean.
 *
 * @see InputNode
 * @see OutputNode
 * @see LlmNode
 * @see RestNode
 * @see ContextNode
 * @see ToolNode
 * @see NodeHooks
 * @see NodeConfig
 */
@Getter
@SuperBuilder
public abstract class Node {

    /**
     * Unique identifier for this node within a workflow.
     * Used for edge references, dependency resolution, and result lookups.
     * Must be non-null and non-blank.
     */
    public abstract String getId();

    /**
     * Optional lifecycle hooks for this node.
     * Defaults to {@code null} if not set via the builder.
     *
     * @see NodeHooks
     */
    NodeHooks hooks;

    /**
     * Optional execution configuration for this node (e.g. error strategy).
     * Defaults to {@code null} if not set, in which case defaults apply.
     *
     * @see NodeConfig
     * @see ErrorStrategy
     */
    NodeConfig config;
}
