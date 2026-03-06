package com.springai.agents.workflow.event;

import com.springai.agents.workflow.Workflow;
import org.springframework.context.ApplicationEvent;

/**
 * Base class for workflow lifecycle events.
 * Published via {@link org.springframework.context.ApplicationEventPublisher}
 * during workflow execution.
 * <p>
 * Subscribe with {@code @EventListener} to observe workflow lifecycle:
 * <pre>{@code
 * @EventListener
 * public void onNodeCompleted(NodeCompletedEvent event) {
 *     log.info("Node '{}' completed in workflow '{}'", event.getNodeId(), event.getWorkflowName());
 * }
 * }</pre>
 */
public abstract class WorkflowEvent extends ApplicationEvent {

    private final String workflowName;
    private final String input;

    protected WorkflowEvent(Object source, String workflowName, String input) {
        super(source);
        this.workflowName = workflowName;
        this.input = input;
    }

    public String getWorkflowName() { return workflowName; }
    public String getInput() { return input; }
}

