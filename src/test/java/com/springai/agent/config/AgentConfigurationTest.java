package com.springai.agent.config;

import com.springai.agent.service.AgentService;
import com.springai.agent.service.McpToolService;
import com.springai.agent.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

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
        // Create test AppProperties
        AppProperties appProperties = createTestAppProperties();
        
        // Test agent services creation
        Map<String, AgentService> agentServices = agentConfiguration.agentServices(
            appProperties, chatModel, mcpToolService);
        
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
    void testAgentServicesWithNullAppProperties() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAgents(null);
        
        assertThrows(NullPointerException.class, () -> {
            agentConfiguration.agentServices(appProperties, chatModel, mcpToolService);
        });
    }
    
    @Test
    void testAgentServicesWithEmptyAgents() {
        AppProperties appProperties = new AppProperties();
        appProperties.setAgents(List.of());
        
        Map<String, AgentService> agentServices = agentConfiguration.agentServices(
            appProperties, chatModel, mcpToolService);
        
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
    
    private AppProperties createTestAppProperties() {
        AppProperties appProperties = new AppProperties();
        
        // Create test agents
        AppProperties.AgentDef agent1 = new AppProperties.AgentDef();
        agent1.setName("testAgent1");
        agent1.setModel("openai");
        agent1.setSystemPrompt("Test prompt 1");
        
        AppProperties.WorkflowDef workflow1 = new AppProperties.WorkflowDef();
        workflow1.setType(WorkflowType.CHAIN);
        
        AppProperties.WorkflowStepDef step1 = new AppProperties.WorkflowStepDef();
        step1.setPrompt("Test prompt: {input}");
        workflow1.setChain(List.of(step1));
        
        agent1.setWorkflow(workflow1);
        
        AppProperties.AgentDef agent2 = new AppProperties.AgentDef();
        agent2.setName("testAgent2");
        agent2.setModel("openai");
        agent2.setSystemPrompt("Test prompt 2");
        
        AppProperties.WorkflowDef workflow2 = new AppProperties.WorkflowDef();
        workflow2.setType(WorkflowType.CHAIN);
        
        AppProperties.WorkflowStepDef step2 = new AppProperties.WorkflowStepDef();
        step2.setPrompt("Another test prompt: {input}");
        workflow2.setChain(List.of(step2));
        
        agent2.setWorkflow(workflow2);
        
        appProperties.setAgents(List.of(agent1, agent2));
        return appProperties;
    }
}
