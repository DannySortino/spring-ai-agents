package com.springai.agent.config;

import com.springai.agent.config.AppProperties.WorkflowDef;
import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.TaskDef;
import com.springai.agent.service.McpToolService;
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
        WorkflowDef graphWorkflow = WorkflowDef.builder()
            .type(WorkflowType.GRAPH)
            .chain(Arrays.asList(
                WorkflowStepDef.builder()
                    .nodeId("A")
                    .prompt("Step A: {input}")
                    .build(),
                WorkflowStepDef.builder()
                    .nodeId("B")
                    .dependsOn(List.of("A"))
                    .prompt("Step B: {A}")
                    .build()
            ))
            .build();

        // When: Building the workflow
        Workflow result = agentConfiguration.buildWorkflow(graphWorkflow, chatModel, mcpToolService);

        // Then: Should return GraphWorkflow instance
        assertNotNull(result);
        assertInstanceOf(GraphWorkflow.class, result);
    }

}