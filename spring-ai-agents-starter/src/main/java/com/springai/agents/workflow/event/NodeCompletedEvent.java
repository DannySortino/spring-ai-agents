package com.springai.agents.workflow.event;

/**
 * Published when a node completes execution.
 */
public class NodeCompletedEvent extends WorkflowEvent {

    private final String nodeId;
    private final String nodeType;
    private final long durationMs;

    public NodeCompletedEvent(Object source, String workflowName, String input,
                              String nodeId, String nodeType, long durationMs) {
        super(source, workflowName, input);
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.durationMs = durationMs;
    }

    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
    public long getDurationMs() { return durationMs; }
}

