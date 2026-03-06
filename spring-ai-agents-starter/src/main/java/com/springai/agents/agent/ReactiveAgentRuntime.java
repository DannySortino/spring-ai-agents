package com.springai.agents.agent;

import com.springai.agents.workflow.ReactiveWorkflowExecutor;
import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowResult;
import com.springai.agents.workflow.WorkflowRouter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static java.util.Collections.unmodifiableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive runtime wrapper around an {@link Agent} that manages execution state.
 * <p>
 * All invocations return {@link Mono} for non-blocking execution. Activated when
 * {@code spring.ai.agents.reactive=true}.
 * <p>
 * Supports multi-workflow agents — uses a {@link WorkflowRouter} to select the
 * most appropriate workflow for each invocation.
 *
 * @see AgentRuntime
 */
@Slf4j
@Getter
public class ReactiveAgentRuntime {

    private final Agent agent;
    private final List<Workflow> workflows;
    private final ReactiveWorkflowExecutor workflowExecutor;
    private final WorkflowRouter workflowRouter;
    private final Map<String, Object> persistentContext = new ConcurrentHashMap<>();
    private int invocationCount = 0;

    public ReactiveAgentRuntime(Agent agent, List<Workflow> workflows,
                                 ReactiveWorkflowExecutor workflowExecutor, WorkflowRouter workflowRouter) {
        this.agent = agent;
        this.workflows = unmodifiableList(workflows);
        this.workflowExecutor = workflowExecutor;
        this.workflowRouter = workflowRouter;
    }

    public String getName() { return agent.getName(); }
    public String getDescription() { return agent.getDescription(); }

    /**
     * Invoke the agent reactively.
     *
     * @param input The raw user input.
     * @return A Mono emitting the output string.
     */
    public Mono<String> invoke(String input) {
        return invoke(input, Map.of());
    }

    /**
     * Invoke the agent reactively with additional context.
     */
    public Mono<String> invoke(String input, Map<String, Object> additionalContext) {
        log.debug("Reactive invocation of agent '{}' (invocation #{})", getName(), invocationCount + 1);

        Workflow workflow = workflowRouter.selectWorkflow(workflows, input);
        log.debug("Agent '{}': selected workflow '{}'", getName(), workflow.getName());

        Map<String, Object> context = new HashMap<>(persistentContext);
        context.put("agentName", getName());
        context.put("agentDescription", getDescription());
        context.put("selectedWorkflow", workflow.getName());
        context.putAll(additionalContext);

        return workflowExecutor.execute(workflow, input, context)
                .doOnSuccess(result -> {
                    invocationCount++;
                    persistentContext.put("invocationCount", invocationCount);
                    persistentContext.put("lastInput", input);
                    persistentContext.put("lastOutput", result.getOutput());
                    persistentContext.put("lastWorkflow", workflow.getName());
                    log.debug("Agent '{}' completed reactively in {}ms", getName(), result.getDurationMs());
                })
                .map(WorkflowResult::getOutput);
    }

    /**
     * Invoke the agent reactively and return the full result.
     */
    public Mono<WorkflowResult> invokeWithResult(String input) {
        return invokeWithResult(input, Map.of());
    }

    public Mono<WorkflowResult> invokeWithResult(String input, Map<String, Object> additionalContext) {
        Workflow workflow = workflowRouter.selectWorkflow(workflows, input);

        Map<String, Object> context = new HashMap<>(persistentContext);
        context.put("agentName", getName());
        context.put("agentDescription", getDescription());
        context.put("selectedWorkflow", workflow.getName());
        context.putAll(additionalContext);

        return workflowExecutor.execute(workflow, input, context)
                .doOnSuccess(result -> {
                    invocationCount++;
                    persistentContext.put("invocationCount", invocationCount);
                    persistentContext.put("lastInput", input);
                    persistentContext.put("lastOutput", result.getOutput());
                    persistentContext.put("lastWorkflow", workflow.getName());
                });
    }

    /** Clear all persistent context and reset invocation count. */
    public void reset() {
        persistentContext.clear();
        invocationCount = 0;
    }

    public void setContextValue(String key, Object value) {
        persistentContext.put(key, value);
    }

    public Object getContextValue(String key) {
        return persistentContext.get(key);
    }
}
