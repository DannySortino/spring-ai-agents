package com.springai.agents.agent;

import com.springai.agents.executor.*;
import com.springai.agents.node.*;
import com.springai.agents.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentRuntime")
class AgentRuntimeTest {

    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new ContextExecutor());
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return "Processed: " + context.getResolvedInput();
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        WorkflowExecutor workflowExecutor = new WorkflowExecutor(registry);
        DefaultWorkflowRouter router = new DefaultWorkflowRouter();

        Agent agent = new Agent() {
            @Override
            public String getName() { return "test-agent"; }
            @Override
            public String getDescription() { return "A test agent"; }
            @Override
            public Workflow buildWorkflow(WorkflowBuilder builder) {
                return builder
                        .name("main")
                        .description("Main workflow")
                        .node(InputNode.builder().id("input").build())
                        .node(OutputNode.builder().id("output").build())
                        .edge("input", "output")
                        .build();
            }
        };

        List<Workflow> workflows = agent.buildWorkflows();
        runtime = new AgentRuntime(agent, workflows, workflowExecutor, router);
    }

    @Test
    @DisplayName("invoke returns output")
    void invoke() {
        String result = runtime.invoke("hello");
        assertEquals("Processed: hello", result);
    }

    @Test
    @DisplayName("tracks invocation count")
    void invocationCount() {
        assertEquals(0, runtime.getInvocationCount());
        runtime.invoke("first");
        assertEquals(1, runtime.getInvocationCount());
        runtime.invoke("second");
        assertEquals(2, runtime.getInvocationCount());
    }

    @Test
    @DisplayName("stores persistent context")
    void persistentContext() {
        runtime.invoke("test input");

        assertEquals("test input", runtime.getContextValue("lastInput"));
        assertEquals("Processed: test input", runtime.getContextValue("lastOutput"));
        assertEquals(1, runtime.getContextValue("invocationCount"));
    }

    @Test
    @DisplayName("invokeWithResult returns full result")
    void invokeWithResult() {
        WorkflowResult result = runtime.invokeWithResult("test");

        assertNotNull(result);
        assertEquals("Processed: test", result.getOutput());
        assertTrue(result.getDurationMs() >= 0);
        assertEquals(2, result.getNodeResults().size());
    }

    @Test
    @DisplayName("reset clears context and invocation count")
    void reset() {
        runtime.invoke("test");
        assertEquals(1, runtime.getInvocationCount());

        runtime.reset();
        assertEquals(0, runtime.getInvocationCount());
        assertNull(runtime.getContextValue("lastInput"));
    }

    @Test
    @DisplayName("getName and getDescription delegate to agent")
    void delegation() {
        assertEquals("test-agent", runtime.getName());
        assertEquals("A test agent", runtime.getDescription());
    }

    @Test
    @DisplayName("custom context values are preserved across invocations")
    void customContextValues() {
        runtime.setContextValue("customKey", "customValue");
        runtime.invoke("test");
        assertEquals("customValue", runtime.getContextValue("customKey"));
    }
}

