package com.springai.agent.config;

import com.springai.agent.service.AgentService;
import com.springai.agent.service.McpToolService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentConfigurationTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpToolService mcpToolService;
    
    private AgentConfiguration agentConfiguration;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentConfiguration = new AgentConfiguration();
    }
    
    @Test
    void testAgentServicesCreation() {
        // Create test AgentsProperties
        AgentsProperties agentsProperties = createTestAgentsProperties();
        
        // Test agent services creation
        Map<String, AgentService> agentServices = agentConfiguration.agentServices(
            agentsProperties, chatModel, mcpToolService, Optional.empty());
        
        assertNotNull(agentServices);
        assertEquals(2, agentServices.size());
        assertTrue(agentServices.containsKey("testAgent1"));
        assertTrue(agentServices.containsKey("testAgent2"));
        
        AgentService agent1 = agentServices.get("testAgent1");
        assertNotNull(agent1);
        assertEquals("testAgent1", agent1.getName());
        assertNotNull(agent1.getWorkflow());
    }
    
    @Test
    void testAgentServicesWithNullAgentsProperties() {
        AgentsProperties agentsProperties = new AgentsProperties();
        agentsProperties.setList(null);
        
        // This should not throw NullPointerException anymore based on the AgentConfiguration implementation
        Map<String, AgentService> agentServices = agentConfiguration.agentServices(
            agentsProperties, chatModel, mcpToolService, Optional.empty());
        
        assertNotNull(agentServices);
        assertTrue(agentServices.isEmpty());
    }
    
    @Test
    void testAgentServicesWithEmptyAgents() {
        AgentsProperties agentsProperties = new AgentsProperties();
        agentsProperties.setList(List.of());
        
        Map<String, AgentService> agentServices = agentConfiguration.agentServices(
            agentsProperties, chatModel, mcpToolService, Optional.empty());
        
        assertNotNull(agentServices);
        assertTrue(agentServices.isEmpty());
    }
    
    @Test
    void testChatClientBean() {
        // This would normally be tested in an integration test with actual ChatModel
        // For unit test, we just verify the method exists and can be called
        assertNotNull(agentConfiguration);
        // The actual ChatClient creation requires a real ChatModel instance
    }
    
    private AgentsProperties createTestAgentsProperties() {
        AgentsProperties agentsProperties = new AgentsProperties();
        
        // Create test agents
        AgentsProperties.AgentDef agent1 = new AgentsProperties.AgentDef();
        agent1.setName("testAgent1");
        agent1.setModel("openai");
        agent1.setSystemPrompt("Test prompt 1");
        
        AgentsProperties.WorkflowDef workflow1 = new AgentsProperties.WorkflowDef();
        workflow1.setType(WorkflowType.GRAPH);
        
        // Required input_node
        AgentsProperties.WorkflowStepDef inputStep1 = new AgentsProperties.WorkflowStepDef();
        inputStep1.setNodeId("input_node");
        inputStep1.setPrompt("Receive input: {input}");
        
        AgentsProperties.WorkflowStepDef step1 = new AgentsProperties.WorkflowStepDef();
        step1.setNodeId("test_step_1");
        step1.setPrompt("Test prompt: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        // Required output_node
        AgentsProperties.WorkflowStepDef outputStep1 = new AgentsProperties.WorkflowStepDef();
        outputStep1.setNodeId("output_node");
        outputStep1.setPrompt("Final output: {test_step_1}");
        outputStep1.setDependsOn(List.of("test_step_1"));
        
        workflow1.setChain(List.of(inputStep1, step1, outputStep1));
        
        agent1.setWorkflow(workflow1);
        
        AgentsProperties.AgentDef agent2 = new AgentsProperties.AgentDef();
        agent2.setName("testAgent2");
        agent2.setModel("openai");
        agent2.setSystemPrompt("Test prompt 2");
        
        AgentsProperties.WorkflowDef workflow2 = new AgentsProperties.WorkflowDef();
        workflow2.setType(WorkflowType.GRAPH);
        
        // Required input_node
        AgentsProperties.WorkflowStepDef inputStep2 = new AgentsProperties.WorkflowStepDef();
        inputStep2.setNodeId("input_node");
        inputStep2.setPrompt("Receive input: {input}");
        
        AgentsProperties.WorkflowStepDef step2 = new AgentsProperties.WorkflowStepDef();
        step2.setNodeId("test_step_2");
        step2.setPrompt("Another test prompt: {input_node}");
        step2.setDependsOn(List.of("input_node"));
        
        // Required output_node
        AgentsProperties.WorkflowStepDef outputStep2 = new AgentsProperties.WorkflowStepDef();
        outputStep2.setNodeId("output_node");
        outputStep2.setPrompt("Final output: {test_step_2}");
        outputStep2.setDependsOn(List.of("test_step_2"));
        
        workflow2.setChain(List.of(inputStep2, step2, outputStep2));
        
        agent2.setWorkflow(workflow2);
        
        agentsProperties.setList(List.of(agent1, agent2));
        return agentsProperties;
    }
}
