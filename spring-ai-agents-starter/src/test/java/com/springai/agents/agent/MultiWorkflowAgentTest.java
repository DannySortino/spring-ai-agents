package com.springai.agents.agent;

import com.springai.agents.executor.*;
import com.springai.agents.node.*;
import com.springai.agents.workflow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Multi-workflow Agent")
class MultiWorkflowAgentTest {

    private WorkflowExecutor createExecutor() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new ContextExecutor());
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return context.getResolvedInput();
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });
        return new WorkflowExecutor(registry);
    }

    @Test
    @DisplayName("agent with buildWorkflows() creates multiple workflows")
    void multiWorkflowAgent() {
        Agent agent = new Agent() {
            @Override
            public String getName() { return "multi-agent"; }
            @Override
            public String getDescription() { return "Has two workflows"; }
            @Override
            public List<Workflow> buildWorkflows() {
                return List.of(
                        WorkflowBuilder.create()
                                .name("analyze")
                                .description("Analyzes data")
                                .node(InputNode.builder().id("input").build())
                                .node(ContextNode.builder().id("ctx").contextText("ANALYSIS").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .edge("ctx", "output")
                                .build(),
                        WorkflowBuilder.create()
                                .name("summarize")
                                .description("Summarizes text")
                                .node(InputNode.builder().id("input").build())
                                .node(ContextNode.builder().id("ctx").contextText("SUMMARY").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .edge("ctx", "output")
                                .build()
                );
            }
        };

        List<Workflow> workflows = agent.buildWorkflows();
        assertEquals(2, workflows.size());
        assertEquals("analyze", workflows.get(0).getName());
        assertEquals("summarize", workflows.get(1).getName());
    }

    @Test
    @DisplayName("single-workflow agent uses buildWorkflow via default buildWorkflows")
    void singleWorkflowBackwardCompatibility() {
        Agent agent = new Agent() {
            @Override
            public String getName() { return "simple"; }
            @Override
            public String getDescription() { return "Simple"; }
            @Override
            public Workflow buildWorkflow(WorkflowBuilder builder) {
                return builder
                        .node(InputNode.builder().id("input").build())
                        .node(OutputNode.builder().id("output").build())
                        .edge("input", "output")
                        .build();
            }
        };

        List<Workflow> workflows = agent.buildWorkflows();
        assertEquals(1, workflows.size());
        assertEquals("default", workflows.getFirst().getName());
    }

    @Test
    @DisplayName("DefaultWorkflowRouter selects first workflow for multi-workflow agent")
    void routerSelectsWorkflow() {
        Agent agent = new Agent() {
            @Override
            public String getName() { return "multi"; }
            @Override
            public String getDescription() { return "Multi"; }
            @Override
            public List<Workflow> buildWorkflows() {
                return List.of(
                        WorkflowBuilder.create()
                                .name("first")
                                .description("First workflow")
                                .node(InputNode.builder().id("input").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .build(),
                        WorkflowBuilder.create()
                                .name("second")
                                .description("Second workflow")
                                .node(InputNode.builder().id("input").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .build()
                );
            }
        };

        List<Workflow> workflows = agent.buildWorkflows();
        WorkflowRouter router = new DefaultWorkflowRouter();
        AgentRuntime runtime = new AgentRuntime(agent, workflows, createExecutor(), router);

        String result = runtime.invoke("test input");
        assertNotNull(result);
        assertEquals("first", runtime.getContextValue("lastWorkflow"));
    }

    @Test
    @DisplayName("custom WorkflowRouter can route to second workflow")
    void customRouter() {
        Agent agent = new Agent() {
            @Override
            public String getName() { return "routed"; }
            @Override
            public String getDescription() { return "Routed agent"; }
            @Override
            public List<Workflow> buildWorkflows() {
                return List.of(
                        WorkflowBuilder.create()
                                .name("workflow-a")
                                .description("Workflow A")
                                .node(InputNode.builder().id("input").build())
                                .node(ContextNode.builder().id("tag").contextText("A").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .edge("tag", "output")
                                .build(),
                        WorkflowBuilder.create()
                                .name("workflow-b")
                                .description("Workflow B")
                                .node(InputNode.builder().id("input").build())
                                .node(ContextNode.builder().id("tag").contextText("B").build())
                                .node(OutputNode.builder().id("output").build())
                                .edge("input", "output")
                                .edge("tag", "output")
                                .build()
                );
            }
        };

        // Custom router that always picks the second workflow
        WorkflowRouter customRouter = (workflows, input) -> workflows.get(1);
        List<Workflow> workflows = agent.buildWorkflows();
        AgentRuntime runtime = new AgentRuntime(agent, workflows, createExecutor(), customRouter);

        runtime.invoke("test");
        assertEquals("workflow-b", runtime.getContextValue("lastWorkflow"));
    }
}

