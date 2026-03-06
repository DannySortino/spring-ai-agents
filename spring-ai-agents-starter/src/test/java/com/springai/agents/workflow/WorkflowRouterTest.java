package com.springai.agents.workflow;

import com.springai.agents.node.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowRouter implementations")
class WorkflowRouterTest {

    private Workflow buildWorkflow(String name, String description) {
        return WorkflowBuilder.create()
                .name(name)
                .description(description)
                .node(InputNode.builder().id("input").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();
    }

    @Test
    @DisplayName("DefaultWorkflowRouter returns first workflow")
    void defaultRouterReturnsFirst() {
        var router = new DefaultWorkflowRouter();
        var w1 = buildWorkflow("first", "First workflow");
        var w2 = buildWorkflow("second", "Second workflow");

        Workflow selected = router.selectWorkflow(List.of(w1, w2), "any input");
        assertEquals("first", selected.getName());
    }

    @Test
    @DisplayName("DefaultWorkflowRouter works with single workflow")
    void defaultRouterSingleWorkflow() {
        var router = new DefaultWorkflowRouter();
        var w1 = buildWorkflow("only", "Only workflow");

        Workflow selected = router.selectWorkflow(List.of(w1), "test");
        assertEquals("only", selected.getName());
    }

    @Test
    @DisplayName("LlmWorkflowRouter returns first workflow when only one exists")
    void llmRouterSingleWorkflow() {
        // With a single workflow, LlmWorkflowRouter should skip the LLM call
        var router = new LlmWorkflowRouter(null); // null ChatModel is fine for single workflow
        var w1 = buildWorkflow("only", "Only workflow");

        Workflow selected = router.selectWorkflow(List.of(w1), "test");
        assertEquals("only", selected.getName());
    }
}

