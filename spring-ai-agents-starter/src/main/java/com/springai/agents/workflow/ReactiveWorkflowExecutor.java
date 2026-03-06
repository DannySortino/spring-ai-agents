package com.springai.agents.workflow;

import com.springai.agents.executor.NodeContext;
import com.springai.agents.executor.NodeExecutorRegistry;
import com.springai.agents.node.ErrorStrategy;
import com.springai.agents.node.InputNode;
import com.springai.agents.node.Node;
import com.springai.agents.node.NodeConfig;
import com.springai.agents.node.NodeHooks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static java.util.Map.copyOf;
import static java.util.Objects.nonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive workflow executor that runs a {@link Workflow} DAG using Project Reactor.
 * <p>
 * Execution model:
 * <ol>
 *   <li>Compute level groups from topological sort</li>
 *   <li>Execute each level as a reactive stage</li>
 *   <li>Nodes at the same level execute concurrently via {@link Flux#flatMap}</li>
 *   <li>Levels execute sequentially (each level waits for the previous to complete)</li>
 *   <li>Results are stored in a {@link ConcurrentHashMap}</li>
 *   <li>The first output node's result is emitted as the workflow output</li>
 * </ol>
 * <p>
 * Activated when {@code spring.ai.agents.reactive=true}.
 *
 * @see WorkflowExecutor
 */
@Slf4j
@RequiredArgsConstructor
public class ReactiveWorkflowExecutor {

    private final NodeExecutorRegistry executorRegistry;

    /**
     * Execute the workflow reactively.
     *
     * @param workflow The validated workflow DAG.
     * @param input    The raw user input string.
     * @return A Mono emitting the workflow result.
     */
    public Mono<WorkflowResult> execute(Workflow workflow, String input) {
        return execute(workflow, input, Map.of());
    }

    /**
     * Execute the workflow reactively with additional context.
     */
    public Mono<WorkflowResult> execute(Workflow workflow, String input, Map<String, Object> additionalContext) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> nodeResults = new ConcurrentHashMap<>();

        Map<String, Object> context = new ConcurrentHashMap<>(additionalContext);
        context.put("currentInput", input);
        context.put("timestamp", System.currentTimeMillis());

        Map<Integer, List<String>> levelGroups = workflow.getLevelGroups();
        log.debug("Reactive execution: {} nodes across {} levels", workflow.size(), levelGroups.size());

        // Chain levels sequentially, execute nodes within each level concurrently
        Mono<Void> execution = Mono.empty();
        for (var levelEntry : levelGroups.entrySet()) {
            List<String> nodesAtLevel = levelEntry.getValue();
            execution = execution.then(
                    Flux.fromIterable(nodesAtLevel)
                            .flatMap(nodeId -> executeNodeReactive(workflow, nodeId, input, nodeResults, context))
                            .then()
            );
        }

        return execution.then(Mono.fromCallable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            String output = workflow.getOutputNodeIds().stream()
                    .map(nodeResults::get)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .findFirst()
                    .orElse("");

            log.debug("Reactive workflow completed in {}ms", duration);
            return WorkflowResult.builder()
                    .output(output)
                    .nodeResults(copyOf(nodeResults))
                    .durationMs(duration)
                    .build();
        }));
    }

    /**
     * Execute a single node reactively, firing before/after hooks.
     */
    private Mono<Object> executeNodeReactive(Workflow workflow, String nodeId, String rawInput,
                                              Map<String, Object> nodeResults, Map<String, Object> context) {
        Node node = workflow.getNode(nodeId);
        Set<String> dependencies = workflow.getDependencies(nodeId);

        Map<String, Object> depResults = new HashMap<>();
        for (String dep : dependencies) {
            depResults.put(dep, nodeResults.get(dep));
        }

        String resolvedInput = resolveInput(node, rawInput, depResults);

        NodeContext nodeContext = NodeContext.builder()
                .resolvedInput(resolvedInput)
                .dependencyResults(depResults)
                .executionContext(context)
                .build();

        // Fire beforeExecute hook
        NodeHooks hooks = node.getHooks();
        if (nonNull(hooks) && nonNull(hooks.getBeforeExecute())) {
            hooks.getBeforeExecute().accept(nodeContext);
        }

        return executorRegistry.executeReactive(node, nodeContext)
                .doOnSuccess(result -> {
                    nodeResults.put(nodeId, result);

                    // Fire afterExecute hook
                    if (nonNull(hooks) && nonNull(hooks.getAfterExecute())) {
                        hooks.getAfterExecute().accept(nodeContext, result);
                    }

                    log.debug("Node '{}' completed reactively (result length={})",
                            nodeId, result != null ? String.valueOf(result).length() : 0);
                })
                .onErrorResume(e -> {
                    NodeConfig config = node.getConfig();
                    ErrorStrategy strategy = nonNull(config) ? config.getErrorStrategy() : ErrorStrategy.FAIL_FAST;

                    return switch (strategy) {
                        case CONTINUE_WITH_DEFAULT -> {
                            Object defaultVal = nonNull(config) ? config.getDefaultValue() : "";
                            log.warn("Node '{}' failed, continuing with default value: {}", nodeId, e.getMessage());
                            nodeResults.put(nodeId, defaultVal);
                            yield Mono.just(defaultVal);
                        }
                        case SKIP -> {
                            log.warn("Node '{}' failed, skipping: {}", nodeId, e.getMessage());
                            yield Mono.just("");
                        }
                        default -> {
                            log.error("Node '{}' failed (FAIL_FAST): {}", nodeId, e.getMessage());
                            yield Mono.error(e);
                        }
                    };
                });
    }

    private String resolveInput(Node node, String rawInput, Map<String, Object> depResults) {
        if (node instanceof InputNode) return rawInput;
        if (depResults.isEmpty()) return "";
        if (depResults.size() == 1) return String.valueOf(depResults.values().iterator().next());

        var combined = new StringBuilder();
        for (var entry : depResults.entrySet()) {
            if (!combined.isEmpty()) combined.append("\n\n");
            combined.append("Result from ").append(entry.getKey()).append(": ")
                    .append(String.valueOf(entry.getValue()));
        }
        return combined.toString();
    }
}

