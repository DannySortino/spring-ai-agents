package com.springai.agents.workflow.event;

/**
 * Published when a workflow starts execution.
 */
public class WorkflowStartedEvent extends WorkflowEvent {

    private final int nodeCount;

    public WorkflowStartedEvent(Object source, String workflowName, String input, int nodeCount) {
        super(source, workflowName, input);
        this.nodeCount = nodeCount;
    }

    public int getNodeCount() { return nodeCount; }
}

