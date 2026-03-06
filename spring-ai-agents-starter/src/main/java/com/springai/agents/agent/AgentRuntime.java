package com.springai.agents.agent;

import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowExecutor;
import com.springai.agents.workflow.WorkflowResult;
import com.springai.agents.workflow.WorkflowRouter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.util.Collections.unmodifiableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronous runtime wrapper around an {@link Agent} that manages execution state.
 * <p>
 * Maintains persistent context across invocations including conversation history,
 * invocation count, and custom user-set values.
 * <p>
 * Supports multi-workflow agents — uses a {@link WorkflowRouter} to select the
 * most appropriate workflow for each invocation.
 * <p>
 * Each {@link Agent} bean is wrapped in exactly one {@code AgentRuntime} by auto-configuration.
 *
 * @see ReactiveAgentRuntime
 */
@Slf4j
@Getter
public class AgentRuntime {

    private final Agent agent;
    private final List<Workflow> workflows;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRouter workflowRouter;
    private final Map<String, Object> persistentContext = new ConcurrentHashMap<>();
    private int invocationCount = 0;

    public AgentRuntime(Agent agent, List<Workflow> workflows,
                        WorkflowExecutor workflowExecutor, WorkflowRouter workflowRouter) {
        this.agent = agent;
        this.workflows = unmodifiableList(workflows);
        this.workflowExecutor = workflowExecutor;
        this.workflowRouter = workflowRouter;
    }

    /** Get the agent's name. */
    public String getName() { return agent.getName(); }

    /** Get the agent's description. */
    public String getDescription() { return agent.getDescription(); }

    /**
     * Invoke the agent synchronously with the given input.
     *
     * @param input The raw user input.
     * @return The output string from the workflow.
     */
    public String invoke(String input) {
        return invoke(input, Map.of());
    }

    /**
     * Invoke the agent synchronously with input and additional context.
     *
     * @param input             The raw user input.
     * @param additionalContext Additional context entries merged into the execution context.
     * @return The output string from the workflow.
     */
    public String invoke(String input, Map<String, Object> additionalContext) {
        log.debug("Invoking agent '{}' (invocation #{})", getName(), invocationCount + 1);

        // Select the appropriate workflow
        Workflow workflow = workflowRouter.selectWorkflow(workflows, input);
        log.debug("Agent '{}': selected workflow '{}'", getName(), workflow.getName());

        Map<String, Object> context = new HashMap<>(persistentContext);
        context.put("agentName", getName());
        context.put("agentDescription", getDescription());
        context.put("selectedWorkflow", workflow.getName());
        context.putAll(additionalContext);

        WorkflowResult result = workflowExecutor.execute(workflow, input, context);

        invocationCount++;
        persistentContext.put("invocationCount", invocationCount);
        persistentContext.put("lastInput", input);
        persistentContext.put("lastOutput", result.getOutput());
        persistentContext.put("lastWorkflow", workflow.getName());

        log.debug("Agent '{}' completed in {}ms", getName(), result.getDurationMs());
        return result.getOutput();
    }

    /** Get the last workflow result details. */
    public WorkflowResult invokeWithResult(String input) {
        return invokeWithResult(input, Map.of());
    }

    /** Get the last workflow result details with additional context. */
    public WorkflowResult invokeWithResult(String input, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>(persistentContext);
        context.put("agentName", getName());
        context.put("agentDescription", getDescription());
        context.putAll(additionalContext);

        Workflow workflow = workflowRouter.selectWorkflow(workflows, input);

        WorkflowResult result = workflowExecutor.execute(workflow, input, context);

        invocationCount++;
        persistentContext.put("invocationCount", invocationCount);
        persistentContext.put("lastInput", input);
        persistentContext.put("lastOutput", result.getOutput());
        persistentContext.put("lastWorkflow", workflow.getName());

        return result;
    }

    /** Clear all persistent context and reset invocation count. */
    public void reset() {
        persistentContext.clear();
        invocationCount = 0;
    }

    /** Set a value in persistent context. */
    public void setContextValue(String key, Object value) {
        persistentContext.put(key, value);
    }

    /** Get a value from persistent context. */
    public Object getContextValue(String key) {
        return persistentContext.get(key);
    }
}
