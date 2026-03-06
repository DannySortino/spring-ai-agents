package com.springai.agents.workflow;

import com.springai.agents.executor.NodeContext;
import com.springai.agents.executor.NodeExecutorRegistry;
import com.springai.agents.node.ErrorStrategy;
import com.springai.agents.node.InputNode;
import com.springai.agents.node.Node;
import com.springai.agents.node.NodeConfig;
import com.springai.agents.node.NodeHooks;
import com.springai.agents.workflow.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Map.copyOf;
import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;

import java.util.*;
import java.util.concurrent.*;

/**
 * Synchronous workflow executor that runs a {@link Workflow} DAG to completion.
 * <p>
 * Execution model:
 * <ol>
 *   <li>Compute level groups from topological sort</li>
 *   <li>Execute nodes level-by-level</li>
 *   <li>Nodes at the same level (no mutual dependencies) run in parallel via {@link CompletableFuture}</li>
 *   <li>Single-node levels execute directly on the calling thread</li>
 *   <li>Results are stored in a {@link ConcurrentHashMap} and available to downstream nodes</li>
 *   <li>The first output node's result is returned as the workflow output</li>
 * </ol>
 * <p>
 * Supports per-node error handling via {@link ErrorStrategy} and publishes
 * {@link WorkflowEvent}s when an {@link ApplicationEventPublisher} is provided.
 *
 * @see ReactiveWorkflowExecutor
 */
@Slf4j
public class WorkflowExecutor {

    private final NodeExecutorRegistry executorRegistry;
    private final ExecutorService threadPool;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowExecutor(NodeExecutorRegistry executorRegistry) {
        this(executorRegistry, Executors.newCachedThreadPool(), null);
    }

    public WorkflowExecutor(NodeExecutorRegistry executorRegistry, ExecutorService threadPool) {
        this(executorRegistry, threadPool, null);
    }

    public WorkflowExecutor(NodeExecutorRegistry executorRegistry, ExecutorService threadPool,
                            ApplicationEventPublisher eventPublisher) {
        this.executorRegistry = executorRegistry;
        this.threadPool = threadPool;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Execute the workflow synchronously.
     *
     * @param workflow The validated workflow DAG.
     * @param input    The raw user input string.
     * @return The workflow result.
     */
    public WorkflowResult execute(Workflow workflow, String input) {
        return execute(workflow, input, Map.of());
    }

    /**
     * Execute the workflow synchronously with additional context.
     *
     * @param workflow          The validated workflow DAG.
     * @param input             The raw user input string.
     * @param additionalContext Additional context entries passed to all executors.
     * @return The workflow result.
     */
    public WorkflowResult execute(Workflow workflow, String input, Map<String, Object> additionalContext) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> nodeResults = new ConcurrentHashMap<>();

        // Build shared execution context
        Map<String, Object> context = new ConcurrentHashMap<>(additionalContext);
        context.put("currentInput", input);
        context.put("timestamp", System.currentTimeMillis());

        // Get level-grouped execution plan
        Map<Integer, List<String>> levelGroups = workflow.getLevelGroups();
        log.debug("Executing workflow '{}': {} nodes across {} levels",
                workflow.getName(), workflow.size(), levelGroups.size());

        publishEvent(new WorkflowStartedEvent(this, workflow.getName(), input, workflow.size()));

        // Execute level by level
        for (var levelEntry : levelGroups.entrySet()) {
            List<String> nodesAtLevel = levelEntry.getValue();

            if (nodesAtLevel.size() == 1) {
                // Single node — execute directly on calling thread
                executeNode(workflow, nodesAtLevel.getFirst(), input, nodeResults, context);
            } else {
                // Multiple independent nodes — execute in parallel
                List<CompletableFuture<Void>> futures = nodesAtLevel.stream()
                        .map(nodeId -> runAsync(
                                () -> executeNode(workflow, nodeId, input, nodeResults, context),
                                threadPool))
                        .toList();
                allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Return the first output node's result
        String output = workflow.getOutputNodeIds().stream()
                .map(nodeResults::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .findFirst()
                .orElse("");

        log.debug("Workflow '{}' completed in {}ms", workflow.getName(), duration);
        publishEvent(new WorkflowCompletedEvent(this, workflow.getName(), input, duration, output));

        return WorkflowResult.builder()
                .output(output)
                .nodeResults(new java.util.LinkedHashMap<>(nodeResults))
                .durationMs(duration)
                .build();
    }

    /**
     * Execute a single node: resolve input, fire hooks, dispatch to executor, store result.
     */
    private void executeNode(Workflow workflow, String nodeId, String rawInput,
                             Map<String, Object> nodeResults, Map<String, Object> context) {
        Node node = workflow.getNode(nodeId);
        Set<String> dependencies = workflow.getDependencies(nodeId);

        // Build dependency results for this node
        Map<String, Object> depResults = new HashMap<>();
        for (String dep : dependencies) {
            depResults.put(dep, nodeResults.get(dep));
        }

        // Resolve the input string
        String resolvedInput = resolveInput(node, rawInput, depResults);

        // Build node context
        NodeContext nodeContext = NodeContext.builder()
                .resolvedInput(resolvedInput)
                .dependencyResults(depResults)
                .executionContext(context)
                .build();

        NodeHooks hooks = node.getHooks();

        // Fire beforeExecute hook
        if (nonNull(hooks) && nonNull(hooks.getBeforeExecute())) {
            hooks.getBeforeExecute().accept(nodeContext);
        }

        publishEvent(new NodeStartedEvent(this, workflow.getName(), rawInput,
                nodeId, node.getClass().getSimpleName()));

        long nodeStart = System.currentTimeMillis();

        try {
            Object result = executorRegistry.execute(node, nodeContext);
            nodeResults.put(nodeId, result);

            // Fire afterExecute hook
            if (nonNull(hooks) && nonNull(hooks.getAfterExecute())) {
                hooks.getAfterExecute().accept(nodeContext, result);
            }

            long nodeDuration = System.currentTimeMillis() - nodeStart;
            publishEvent(new NodeCompletedEvent(this, workflow.getName(), rawInput,
                    nodeId, node.getClass().getSimpleName(), nodeDuration));

            log.debug("Node '{}' completed (result length={})", nodeId,
                    result != null ? String.valueOf(result).length() : 0);

        } catch (Exception e) {
            NodeConfig config = node.getConfig();
            ErrorStrategy strategy = nonNull(config) ? config.getErrorStrategy() : ErrorStrategy.FAIL_FAST;

            switch (strategy) {
                case CONTINUE_WITH_DEFAULT -> {
                    Object defaultVal = nonNull(config) ? config.getDefaultValue() : "";
                    log.warn("Node '{}' failed, continuing with default value: {}", nodeId, e.getMessage());
                    nodeResults.put(nodeId, defaultVal);
                }
                case SKIP -> {
                    log.warn("Node '{}' failed, skipping: {}", nodeId, e.getMessage());
                    // ConcurrentHashMap doesn't allow null values — don't store anything
                }
                default -> {
                    log.error("Node '{}' failed (FAIL_FAST): {}", nodeId, e.getMessage());
                    throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Resolve the input for a node based on its type and dependencies.
     */
    private String resolveInput(Node node, String rawInput, Map<String, Object> depResults) {
        // Input nodes receive raw user input
        if (node instanceof InputNode) {
            return rawInput;
        }
        // No dependencies — empty input
        if (depResults.isEmpty()) {
            return "";
        }
        // Single dependency — use its result directly
        if (depResults.size() == 1) {
            Object val = depResults.values().iterator().next();
            return val != null ? String.valueOf(val) : "";
        }
        // Multiple dependencies — combine results
        var combined = new StringBuilder();
        for (var entry : depResults.entrySet()) {
            if (!combined.isEmpty()) combined.append("\n\n");
            combined.append("Result from ").append(entry.getKey()).append(": ")
                    .append(String.valueOf(entry.getValue()));
        }
        return combined.toString();
    }

    private void publishEvent(WorkflowEvent event) {
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish workflow event: {}", e.getMessage());
            }
        }
    }
}

