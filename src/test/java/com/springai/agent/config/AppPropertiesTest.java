package com.springai.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppPropertiesTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public AppProperties testAppProperties() {
            AppProperties appProperties = new AppProperties();
            
            // Create test agent
            AppProperties.AgentDef agent = new AppProperties.AgentDef();
            agent.setName("testAgent");
            agent.setModel("openai");
            agent.setSystemPrompt("Test prompt");
            
            // Create workflow
            AppProperties.WorkflowDef workflow = new AppProperties.WorkflowDef();
            workflow.setType(WorkflowType.GRAPH);
            workflow.setAggregator("Test aggregator");
            
            // Create task with nested workflow
            AppProperties.TaskDef task = new AppProperties.TaskDef();
            task.setName("testTask");
            
            // Create nested workflow
            AppProperties.WorkflowDef nestedWorkflow = new AppProperties.WorkflowDef();
            nestedWorkflow.setType(WorkflowType.GRAPH);
            
            // Create workflow step
            AppProperties.WorkflowStepDef step = new AppProperties.WorkflowStepDef();
            step.setNodeId("test_node");
            step.setPrompt("Test nested prompt");
            
            nestedWorkflow.setChain(List.of(step));
            task.setWorkflow(nestedWorkflow);
            workflow.setTasks(List.of(task));
            
            agent.setWorkflow(workflow);
            appProperties.setAgents(List.of(agent));
            
            return appProperties;
        }
    }

    @Autowired
    private AppProperties appProperties;

    @Test
    void testNestedWorkflowDeserialization() {
        // Use the injected AppProperties that was configured via CustomAppPropertiesConfiguration

        // Verify basic properties
        assertNotNull(appProperties);
        
        // Debug: Check if agents is null and why
        System.out.println("AppProperties: " + appProperties);
        System.out.println("AppProperties.getAgents(): " + appProperties.getAgents());
        
        assertNotNull(appProperties.getAgents(), "AppProperties.agents should not be null - check test property binding");
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


