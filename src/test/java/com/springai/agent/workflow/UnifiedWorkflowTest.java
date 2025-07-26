package com.springai.agent.workflow;

import com.springai.agent.config.AgentConfiguration;
import com.springai.agent.config.AppProperties.*;
import com.springai.agent.config.ConditionType;
import com.springai.agent.config.WorkflowType;
import com.springai.agent.service.McpToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for the unified workflow system that incorporates orchestrator, routing,
 * and conditional logic patterns into GraphWorkflow.
 */
@ExtendWith(MockitoExtension.class)
class UnifiedWorkflowTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpToolService mcpToolService;
    
    @Mock
    private ChatResponse chatResponse;
    
    @Mock
    private Generation generation;
    
    @Mock
    private AssistantMessage assistantMessage;

    private AgentConfiguration agentConfiguration;

    @BeforeEach
    void setUp() {
        // Setup mock responses
        lenient().when(assistantMessage.getText()).thenReturn("Mock response");
        lenient().when(generation.getOutput()).thenReturn(assistantMessage);
        lenient().when(chatResponse.getResult()).thenReturn(generation);
        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        lenient().when(mcpToolService.callTool(anyString(), anyString(), any(Map.class))).thenReturn("Tool response");
        
        agentConfiguration = new AgentConfiguration();
    }



    @Test
    void testConditionalLogicInGraphWorkflow() {
        // Given: A GRAPH workflow with conditional logic
        ConditionDef urgentCondition = ConditionDef.builder()
            .type(ConditionType.CONTAINS)
            .field("input")
            .value("urgent")
            .ignoreCase(true)
            .build();

        ConditionalStepDef conditional = ConditionalStepDef.builder()
            .condition(urgentCondition)
            .thenStep(WorkflowStepDef.builder()
                .prompt("URGENT: Prioritizing immediate response: {input}")
                .build())
            .elseStep(WorkflowStepDef.builder()
                .prompt("Standard processing: {input}")
                .build())
            .build();

        WorkflowStepDef conditionalStep = WorkflowStepDef.builder()
            .nodeId("conditional_processor")
            .conditional(conditional)
            .build();

        WorkflowDef graphWorkflow = WorkflowDef.builder()
            .type(WorkflowType.GRAPH)
            .chain(List.of(conditionalStep))
            .build();

        // When: Building the workflow
        Workflow result = agentConfiguration.buildWorkflow(graphWorkflow, chatModel, mcpToolService);

        // Then: Should return GraphWorkflow instance
        assertNotNull(result);
        assertInstanceOf(GraphWorkflow.class, result);
        
        // Execute with urgent input
        Map<String, Object> context = new HashMap<>();
        String urgentOutput = result.execute("This is urgent!", context);
        assertNotNull(urgentOutput);
        
        // Execute with normal input
        context.clear();
        String normalOutput = result.execute("Normal request", context);
        assertNotNull(normalOutput);
    }

    @Test
    void testComplexGraphWorkflowWithMultiplePatterns() {
        // Given: A complex GRAPH workflow combining conditional logic and dependencies
        ConditionDef typeCondition = ConditionDef.builder()
            .type(ConditionType.REGEX)
            .field("input")
            .value(".*analysis.*|.*report.*")
            .build();

        ConditionalStepDef routingConditional = ConditionalStepDef.builder()
            .condition(typeCondition)
            .thenStep(WorkflowStepDef.builder()
                .prompt("Detected analysis request: {input}")
                .build())
            .elseStep(WorkflowStepDef.builder()
                .prompt("General processing: {input}")
                .build())
            .build();

        List<WorkflowStepDef> steps = Arrays.asList(
            // Initial routing step
            WorkflowStepDef.builder()
                .nodeId("router")
                .conditional(routingConditional)
                .build(),
            
            // Parallel processing steps
            WorkflowStepDef.builder()
                .nodeId("processor_a")
                .dependsOn(List.of("router"))
                .prompt("Process A: {router}")
                .build(),
            
            WorkflowStepDef.builder()
                .nodeId("processor_b")
                .dependsOn(List.of("router"))
                .prompt("Process B: {router}")
                .build(),
            
            // Final synthesis
            WorkflowStepDef.builder()
                .nodeId("synthesizer")
                .dependsOn(List.of("processor_a", "processor_b"))
                .prompt("Combine A: {processor_a} and B: {processor_b}")
                .build()
        );

        WorkflowDef complexWorkflow = WorkflowDef.builder()
            .type(WorkflowType.GRAPH)
            .chain(steps)
            .build();

        // When: Building the workflow
        Workflow result = agentConfiguration.buildWorkflow(complexWorkflow, chatModel, mcpToolService);

        // Then: Should return GraphWorkflow instance and execute successfully
        assertNotNull(result);
        assertInstanceOf(GraphWorkflow.class, result);
        
        Map<String, Object> context = new HashMap<>();
        String output = result.execute("Generate analysis report", context);
        assertNotNull(output);
    }

}