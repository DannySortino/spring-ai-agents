package com.springai.agents.agent;

import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowBuilder;

import static java.util.List.of;

import java.util.List;

/**
 * Core interface that users implement to define an AI agent.
 * <p>
 * An Agent is a named, self-describing unit that builds one or more DAG-based workflows for
 * processing user input. Agents are auto-discovered as Spring beans and automatically:
 * <ul>
 *   <li>Wrapped in an {@link AgentRuntime} or {@link ReactiveAgentRuntime}</li>
 *   <li>Registered in the {@link AgentRegistry}</li>
 *   <li>Exposed as MCP server tools (when {@code spring.ai.agents.mcp-server.enabled=true})</li>
 * </ul>
 *
 * <h3>Single-workflow agent:</h3>
 * <pre>{@code
 * @Component
 * public class SimpleAgent implements Agent {
 *     public String getName() { return "simple-agent"; }
 *     public String getDescription() { return "Answers questions"; }
 *     public Workflow buildWorkflow(WorkflowBuilder builder) {
 *         return builder
 *             .node(InputNode.builder().id("input").build())
 *             .node(LlmNode.builder().id("think").promptTemplate("Answer: {input}").build())
 *             .node(OutputNode.builder().id("output").build())
 *             .edge("input", "think").edge("think", "output")
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <h3>Multi-workflow agent:</h3>
 * <pre>{@code
 * @Component
 * public class SmartAgent implements Agent {
 *     public String getName() { return "smart-agent"; }
 *     public String getDescription() { return "Routes to the best workflow"; }
 *     public List<Workflow> buildWorkflows() {
 *         return List.of(
 *             WorkflowBuilder.create().name("analyze").description("Analyzes data")
 *                 .node(...).edge(...).build(),
 *             WorkflowBuilder.create().name("summarize").description("Summarizes text")
 *                 .node(...).edge(...).build()
 *         );
 *     }
 * }
 * }</pre>
 */
public interface Agent {

    /**
     * Unique name for this agent. Used as the MCP tool name and registry key.
     * Should use kebab-case (e.g., "research-agent").
     */
    String getName();

    /**
     * Human-readable description of what this agent does.
     * Used as the MCP tool description and for documentation.
     */
    String getDescription();

    /**
     * Build a single workflow DAG that defines this agent's processing logic.
     * <p>
     * Override this for single-workflow agents. For multi-workflow agents,
     * override {@link #buildWorkflows()} instead.
     *
     * @param builder A fresh WorkflowBuilder instance.
     * @return A fully built and validated Workflow.
     */
    default Workflow buildWorkflow(WorkflowBuilder builder) {
        throw new UnsupportedOperationException(
                "Agent must override either buildWorkflow(WorkflowBuilder) or buildWorkflows()");
    }

    /**
     * Build multiple workflows for this agent. The framework uses a
     * {@link com.springai.agents.workflow.WorkflowRouter} to select the most
     * appropriate workflow for each incoming request.
     * <p>
     * Default implementation delegates to {@link #buildWorkflow(WorkflowBuilder)}
     * for backward compatibility with single-workflow agents.
     *
     * @return A non-empty list of validated workflows.
     */
    default List<Workflow> buildWorkflows() {
        return of(buildWorkflow(WorkflowBuilder.create()));
    }
}
