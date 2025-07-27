package com.springai.agent.config;

import com.springai.agent.config.AgentsProperties.WorkflowDef;
import com.springai.agent.config.AgentsProperties.WorkflowStepDef;
import com.springai.agent.config.AgentsProperties.TaskDef;
import com.springai.agent.service.McpToolService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.workflow.GraphWorkflow;
import com.springai.agent.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for GraphWorkflow functionality.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowConversionTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpToolService mcpToolService;
    
    private AgentConfiguration agentConfiguration;

    @BeforeEach
    void setUp() {
        agentConfiguration = new AgentConfiguration();
    }


    @Test
    void testGraphWorkflowRemainsUnchanged() {
        // Given: A GRAPH workflow configuration
        WorkflowDef graphWorkflow = new WorkflowDef();
        graphWorkflow.setType(WorkflowType.GRAPH);
        
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("A");
        step1.setPrompt("Step A: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("B");
        step2.setDependsOn(List.of("A"));
        step2.setPrompt("Step B: {A}");
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {B}");
        outputNode.setDependsOn(List.of("B"));
        
        graphWorkflow.setChain(Arrays.asList(inputNode, step1, step2, outputNode));

        // When: Building the workflow
        Workflow result = agentConfiguration.buildWorkflow(graphWorkflow, chatModel, mcpToolService, null);

        // Then: Should return GraphWorkflow instance
        assertNotNull(result);
        assertInstanceOf(GraphWorkflow.class, result);
    }

}