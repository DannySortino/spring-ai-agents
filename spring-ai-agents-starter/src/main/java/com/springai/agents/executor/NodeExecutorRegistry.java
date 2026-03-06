package com.springai.agents.executor;

import com.springai.agents.node.Node;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static reactor.core.scheduler.Schedulers.boundedElastic;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps {@link Node} subtypes to their corresponding {@link NodeExecutor}.
 * <p>
 * Used by workflow executors to dispatch node execution to the correct handler.
 * Supports both synchronous and reactive dispatch — if an executor implements
 * {@link ReactiveNodeExecutor}, its native reactive method is used; otherwise
 * the sync method is wrapped in a {@link Mono}.
 * <p>
 * Populated by auto-configuration. Users can override any executor by providing
 * their own {@code @Bean}.
 */
@Slf4j
public class NodeExecutorRegistry {

    private final Map<Class<? extends Node>, NodeExecutor<?>> executors = new HashMap<>();

    /**
     * Register an executor for a specific node type.
     */
    public <T extends Node> void register(NodeExecutor<T> executor) {
        executors.put(executor.getNodeType(), executor);
        log.debug("Registered executor {} for node type {}",
                executor.getClass().getSimpleName(), executor.getNodeType().getSimpleName());
    }

    /**
     * Execute a node synchronously using the registered executor.
     *
     * @throws IllegalStateException if no executor is registered for the node type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> Object execute(T node, NodeContext context) {
        NodeExecutor<T> executor = (NodeExecutor<T>) executors.get(node.getClass());
        if (executor == null) {
            throw new IllegalStateException(
                    "No executor registered for node type: " + node.getClass().getSimpleName());
        }
        return executor.execute(node, context);
    }

    /**
     * Execute a node reactively using the registered executor.
     * <p>
     * If the executor implements {@link ReactiveNodeExecutor}, its native reactive method
     * is used. Otherwise, the sync method is wrapped in a {@link Mono} on the
     * {@code boundedElastic} scheduler.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> Mono<Object> executeReactive(T node, NodeContext context) {
        NodeExecutor<T> executor = (NodeExecutor<T>) executors.get(node.getClass());
        if (executor == null) {
            return Mono.error(new IllegalStateException(
                    "No executor registered for node type: " + node.getClass().getSimpleName()));
        }

        if (executor instanceof ReactiveNodeExecutor<?> reactive) {
            return ((ReactiveNodeExecutor<T>) reactive).executeReactive(node, context);
        }

        // Wrap sync executor in Mono
        return Mono.fromCallable(() -> executor.execute(node, context))
                .subscribeOn(boundedElastic());
    }

    /**
     * Check whether an executor is registered for the given node type.
     */
    public boolean hasExecutor(Class<? extends Node> nodeType) {
        return executors.containsKey(nodeType);
    }
}
