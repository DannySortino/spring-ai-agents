package com.springai.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {CustomAppPropertiesConfiguration.class})
@TestPropertySource(properties = {
    "app.agents[0].name=testAgent",
    "app.agents[0].model=openai",
    "app.agents[0].systemPrompt=Test prompt",
    "app.agents[0].workflow.type=graph",
    "app.agents[0].workflow.aggregator=Test aggregator",
    "app.agents[0].workflow.tasks[0].name=testTask",
    "app.agents[0].workflow.tasks[0].workflow.type=graph",
    "app.agents[0].workflow.tasks[0].workflow.chain[0].nodeId=test_node",
    "app.agents[0].workflow.tasks[0].workflow.chain[0].prompt=Test nested prompt"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void testNestedWorkflowDeserialization() {
        // Use the injected AppProperties that was configured via CustomAppPropertiesConfiguration

        // Verify basic properties
        assertNotNull(appProperties);
        assertNotNull(appProperties.getAgents());
        assertTrue(appProperties.getAgents().size() >= 1, "Should have at least one agent");

        // Find the test agent we configured
        AppProperties.AgentDef agent = appProperties.getAgents().stream()
            .filter(a -> "testAgent".equals(a.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(agent, "Should find testAgent in the configuration");
        assertEquals("testAgent", agent.getName());
        assertEquals("openai", agent.getModel());
        assertEquals("Test prompt", agent.getSystemPrompt());

        // Verify workflow is not null
        assertNotNull(agent.getWorkflow(), "Agent workflow should not be null");
        assertEquals(WorkflowType.GRAPH, agent.getWorkflow().getType());
        assertEquals("Test aggregator", agent.getWorkflow().getAggregator());

        // Verify tasks are not null
        assertNotNull(agent.getWorkflow().getTasks(), "Workflow tasks should not be null");
        assertEquals(1, agent.getWorkflow().getTasks().size());

        AppProperties.TaskDef task = agent.getWorkflow().getTasks().get(0);
        assertEquals("testTask", task.getName());

        // This is the critical test - verify nested workflow is not null
        assertNotNull(task.getWorkflow(), "Task workflow should not be null - this is the main issue we're fixing");
        assertEquals(WorkflowType.GRAPH, task.getWorkflow().getType());
        
        // Verify nested chain
        assertNotNull(task.getWorkflow().getChain());
        assertEquals(1, task.getWorkflow().getChain().size());
        assertEquals("Test nested prompt", task.getWorkflow().getChain().get(0).getPrompt());
    }

    @Test
    void testSimpleWorkflowDeserialization() {
        // This test uses the same injected AppProperties, so we verify the basic workflow structure
        assertNotNull(appProperties);
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertNotNull(agent.getWorkflow());
        assertEquals(WorkflowType.GRAPH, agent.getWorkflow().getType()); // Updated to match the test properties
        assertEquals("Test aggregator", agent.getWorkflow().getAggregator());
    }
}
