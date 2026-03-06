package com.springai.agents.workflow;

import java.util.List;

/**
 * Default workflow router that simply returns the first workflow in the list.
 * <p>
 * Used as a fallback when no {@link org.springframework.ai.chat.model.ChatModel}
 * is available or when the agent has only one workflow.
 */
public class DefaultWorkflowRouter implements WorkflowRouter {

    @Override
    public Workflow selectWorkflow(List<Workflow> workflows, String input) {
        return workflows.getFirst();
    }
}

