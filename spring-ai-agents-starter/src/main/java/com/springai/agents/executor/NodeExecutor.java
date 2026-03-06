package com.springai.agents.executor;

import com.springai.agents.node.Node;

/**
 * Synchronous executor interface for processing a specific type of workflow node.
 * <p>
 * Each implementation handles exactly one {@link Node} subtype and encapsulates the
 * runtime behavior for that node type (LLM calls, REST calls, tool invocations, etc.).
 * <p>
 * Executors are stateless — all required state is passed via the {@link NodeContext}.
 * Users can override any default executor by providing their own {@code @Bean}.
 * <p>
 * Return type is {@code Object} to support typed results. Executors that produce
 * string output simply return a {@code String} (which is an {@code Object}).
 *
 * @param <T> The specific Node subtype this executor handles.
 * @see ReactiveNodeExecutor
 */
public interface NodeExecutor<T extends Node> {

    /**
     * Execute the given node synchronously and return its output.
     *
     * @param node    The node to execute (carries configuration).
     * @param context Execution context with resolved input, dependency results, and metadata.
     * @return The output produced by this node (String, POJO, or any Object).
     */
    Object execute(T node, NodeContext context);

    /**
     * Returns the {@link Node} subtype this executor handles.
     * Used by {@link NodeExecutorRegistry} for type-based dispatch.
     */
    Class<T> getNodeType();
}

