package com.springai.agents.executor;

import com.springai.agents.node.Node;
import reactor.core.publisher.Mono;

/**
 * Reactive executor interface for processing a specific type of workflow node.
 * <p>
 * Provides non-blocking, reactive execution using Project Reactor's {@link Mono}.
 * Used by the {@link com.springai.agents.workflow.ReactiveWorkflowExecutor} when
 * the application is configured for reactive mode.
 * <p>
 * Executors that have native reactive support (e.g., REST via WebClient, LLM streaming)
 * should implement this interface directly. For sync-only executors, the framework
 * automatically wraps the synchronous {@link NodeExecutor#execute} in a {@code Mono}.
 * <p>
 * Return type is {@code Mono<Object>} to support typed results.
 *
 * @param <T> The specific Node subtype this executor handles.
 * @see NodeExecutor
 */
public interface ReactiveNodeExecutor<T extends Node> {

    /**
     * Execute the given node reactively and return its output as a {@link Mono}.
     *
     * @param node    The node to execute (carries configuration).
     * @param context Execution context with resolved input, dependency results, and metadata.
     * @return A Mono emitting the output produced by this node.
     */
    Mono<Object> executeReactive(T node, NodeContext context);
}

