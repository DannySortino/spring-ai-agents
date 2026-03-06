package com.springai.agents.workflow.event;

/**
 * Published when a node starts execution.
 */
public class NodeStartedEvent extends WorkflowEvent {

    private final String nodeId;
    private final String nodeType;

    public NodeStartedEvent(Object source, String workflowName, String input,
                            String nodeId, String nodeType) {
        super(source, workflowName, input);
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }

    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
}

