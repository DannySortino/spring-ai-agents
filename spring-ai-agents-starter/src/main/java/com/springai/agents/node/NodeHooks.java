package com.springai.agents.node;

import com.springai.agents.executor.NodeContext;
import lombok.Builder;
import lombok.Value;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Optional lifecycle hooks for node execution.
 * <p>
 * Hooks allow users to inject custom logic before and after a node executes,
 * without modifying the executor itself. Common use cases include:
 * <ul>
 *   <li>Logging and metrics</li>
 *   <li>Sending progress tokens back to clients</li>
 *   <li>Auditing and tracing</li>
 *   <li>Validating inputs before execution</li>
 *   <li>Transforming or caching results after execution</li>
 * </ul>
 *
 * <pre>{@code
 * NodeHooks hooks = NodeHooks.builder()
 *     .beforeExecute(ctx -> System.out.println("Starting with: " + ctx.getResolvedInput()))
 *     .afterExecute((ctx, result) -> System.out.println("Produced: " + result))
 *     .build();
 *
 * LlmNode.builder()
 *     .id("analyze")
 *     .promptTemplate("Analyze: {input}")
 *     .hooks(hooks)
 *     .build();
 * }</pre>
 *
 * For error handling configuration, see {@link NodeConfig}.
 *
 * @see Node#getHooks()
 * @see NodeConfig
 */
@Value
@Builder
public class NodeHooks {

    /**
     * Callback invoked immediately before the node's executor runs.
     * Receives the {@link NodeContext} that will be passed to the executor.
     */
    Consumer<NodeContext> beforeExecute;

    /**
     * Callback invoked immediately after the node's executor completes successfully.
     * Receives the {@link NodeContext} and the result object produced by the executor.
     */
    BiConsumer<NodeContext, Object> afterExecute;
}

