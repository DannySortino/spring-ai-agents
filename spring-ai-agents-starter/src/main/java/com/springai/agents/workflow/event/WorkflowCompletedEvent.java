package com.springai.agents.workflow.event;

/**
 * Published when a workflow completes execution.
 */
public class WorkflowCompletedEvent extends WorkflowEvent {

    private final long durationMs;
    private final String output;

    public WorkflowCompletedEvent(Object source, String workflowName, String input,
                                   long durationMs, String output) {
        super(source, workflowName, input);
        this.durationMs = durationMs;
        this.output = output;
    }

    public long getDurationMs() { return durationMs; }
    public String getOutput() { return output; }
}

