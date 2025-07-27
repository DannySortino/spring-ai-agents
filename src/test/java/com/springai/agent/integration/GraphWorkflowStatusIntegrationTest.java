package com.springai.agent.integration;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatusData;
import com.springai.agent.service.ExecutionStatusService.NodeExecutionStatus;
import com.springai.agent.service.ExecutionStatusService.NodeState;
import com.springai.agent.service.ExecutionStatusService.ExecutionState;
import com.springai.agent.service.McpToolService;
import com.springai.agent.workflow.GraphWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for GraphWorkflow with ExecutionStatusService.
 * Tests the real-time status tracking during workflow execution.
 */
class GraphWorkflowStatusIntegrationTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpToolService mcpToolService;
    
    private ExecutionStatusService executionStatusService;
    private GraphWorkflow workflow;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executionStatusService = new ExecutionStatusService();
        
        // Mock chat model responses
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("Mock response");
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getResults()).thenReturn(Arrays.asList(mockGeneration));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }

    @Test
    @DisplayName("Should track execution status for simple sequential workflow")
    void testSimpleSequentialWorkflowStatusTracking() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("step2", "Analyze: {step1}", Arrays.asList("step1")),
            createStep("step3", "Finalize: {step2}", Arrays.asList("step2")),
            createStep("output_node", "Final output: {step3}", Arrays.asList("step3"))
        );
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "test-agent");

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        assertEquals("Mock response", result);
        
        // Verify execution tracking was started and completed
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
        
        ExecutionStatusService.ExecutionStatusData execution = history.get(0);
        assertEquals("test-agent", execution.execution().agentName());
        assertEquals(ExecutionState.COMPLETED, execution.execution().state());
        
        // Verify all nodes were tracked
        List<NodeExecutionStatus> nodes = execution.nodes();
        assertEquals(5, nodes.size());
        
        // All nodes should be completed
        for (NodeExecutionStatus node : nodes) {
            assertEquals(NodeState.COMPLETED, node.state());
            assertNotNull(node.result());
            assertNull(node.error());
            assertNotNull(node.durationMs());
            assertTrue(node.durationMs() >= 0);
        }
    }

    @Test
    @DisplayName("Should track execution status for parallel workflow")
    void testParallelWorkflowStatusTracking() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("root", "Root step: {input_node}", Arrays.asList("input_node")),
            createStep("branch_a", "Branch A: {root}", Arrays.asList("root")),
            createStep("branch_b", "Branch B: {root}", Arrays.asList("root")),
            createStep("merge", "Merge: A={branch_a}, B={branch_b}", Arrays.asList("branch_a", "branch_b")),
            createStep("output_node", "Final output: {merge}", Arrays.asList("merge"))
        );
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "parallel-agent");

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        
        // Verify execution tracking
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
        
        ExecutionStatusService.ExecutionStatusData execution = history.get(0);
        assertEquals("parallel-agent", execution.execution().agentName());
        assertEquals(ExecutionState.COMPLETED, execution.execution().state());
        
        // Verify all nodes were tracked
        List<NodeExecutionStatus> nodes = execution.nodes();
        assertEquals(6, nodes.size());
        
        // All nodes should be completed
        for (NodeExecutionStatus node : nodes) {
            assertEquals(NodeState.COMPLETED, node.state());
            assertNotNull(node.result());
        }
    }

    @Test
    @DisplayName("Should track execution status when workflow fails")
    void testFailedWorkflowStatusTracking() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("step2", "This will fail", Arrays.asList("step1")),
            createStep("output_node", "Final output: {step2}", Arrays.asList("step2"))
        );
        
        // Mock failure for step2 - reset the mock first
        reset(chatModel);
        ChatResponse inputResponse = createMockResponse("Input processed");
        ChatResponse step1Response = createMockResponse("Step 1 success");
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(inputResponse)      // input_node call
            .thenReturn(step1Response)      // step1 call
            .thenThrow(new RuntimeException("Simulated failure")); // step2 call fails
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "failing-agent");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            workflow.execute("test input", context);
        });
        
        // Verify execution tracking shows failure
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
        
        ExecutionStatusService.ExecutionStatusData execution = history.get(0);
        assertEquals("failing-agent", execution.execution().agentName());
        assertEquals(ExecutionState.FAILED, execution.execution().state());
        
        // Verify node states
        List<NodeExecutionStatus> nodes = execution.nodes();
        assertEquals(4, nodes.size());
        
        NodeExecutionStatus inputNode = findNodeById(nodes, "input_node");
        assertNotNull(inputNode);
        assertEquals(NodeState.COMPLETED, inputNode.state());
        assertNotNull(inputNode.result());
        assertNull(inputNode.error());
        
        NodeExecutionStatus step1 = findNodeById(nodes, "step1");
        assertNotNull(step1);
        assertEquals(NodeState.COMPLETED, step1.state());
        assertNotNull(step1.result());
        assertNull(step1.error());
        
        NodeExecutionStatus step2 = findNodeById(nodes, "step2");
        assertNotNull(step2);
        assertEquals(NodeState.FAILED, step2.state());
        assertNull(step2.result());
        assertNotNull(step2.error());
        assertTrue(step2.error().contains("Simulated failure"));
        
        // output_node should remain in PENDING state since it never executed due to step2 failure
        NodeExecutionStatus outputNode = findNodeById(nodes, "output_node");
        assertNotNull(outputNode);
        assertEquals(NodeState.PENDING, outputNode.state());
        assertNull(outputNode.result());
        assertNull(outputNode.error());
    }

    @Test
    @DisplayName("Should work without ExecutionStatusService")
    void testWorkflowWithoutStatusService() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("step2", "Analyze: {step1}", Arrays.asList("step1")),
            createStep("output_node", "Final output: {step2}", Arrays.asList("step2"))
        );
        
        // Create workflow without status service
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, null);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "no-tracking-agent");

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        assertEquals("Mock response", result);
        
        // Verify no execution tracking occurred
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Should work without agent name in context")
    void testWorkflowWithoutAgentName() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("output_node", "Final output: {step1}", Arrays.asList("step1"))
        );
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        // No agentName in context

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        assertEquals("Mock response", result);
        
        // Verify no execution tracking occurred (no agent name)
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Should track tool execution in workflow")
    void testToolExecutionStatusTracking() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("search", null, Arrays.asList("input_node"), "web_search"),
            createStep("analyze", "Analyze results: {search}", Arrays.asList("search")),
            createStep("output_node", "Final output: {analyze}", Arrays.asList("analyze"))
        );
        
        // Mock tool service response
        when(mcpToolService.callTool(eq("web_search"), anyString(), any(Map.class))).thenReturn("Tool result");
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "tool-agent");

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        
        // Verify execution tracking
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
        
        ExecutionStatusService.ExecutionStatusData execution = history.get(0);
        assertEquals("tool-agent", execution.execution().agentName());
        assertEquals(ExecutionState.COMPLETED, execution.execution().state());
        
        // Verify all nodes were tracked
        List<NodeExecutionStatus> nodes = execution.nodes();
        assertEquals(4, nodes.size());
        
        NodeExecutionStatus searchNode = findNodeById(nodes, "search");
        assertNotNull(searchNode);
        assertEquals(NodeState.COMPLETED, searchNode.state());
        assertEquals("Tool result", searchNode.result());
        
        NodeExecutionStatus analyzeNode = findNodeById(nodes, "analyze");
        assertNotNull(analyzeNode);
        assertEquals(NodeState.COMPLETED, analyzeNode.state());
        assertEquals("Mock response", analyzeNode.result());
    }

    @Test
    @DisplayName("Should track multiple concurrent executions")
    void testMultipleConcurrentExecutions() throws InterruptedException {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process: {input_node}", Arrays.asList("input_node")),
            createStep("output_node", "Final output: {step1}", Arrays.asList("step1"))
        );
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);

        // When - Execute multiple workflows concurrently
        Thread thread1 = new Thread(() -> {
            Map<String, Object> context1 = new HashMap<>();
            context1.put("agentName", "agent1");
            workflow.execute("input1", context1);
        });
        
        Thread thread2 = new Thread(() -> {
            Map<String, Object> context2 = new HashMap<>();
            context2.put("agentName", "agent2");
            workflow.execute("input2", context2);
        });
        
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(2, history.size());
        
        // Verify both executions were tracked
        boolean foundAgent1 = false, foundAgent2 = false;
        for (ExecutionStatusService.ExecutionStatusData execution : history) {
            if ("agent1".equals(execution.execution().agentName())) {
                foundAgent1 = true;
            } else if ("agent2".equals(execution.execution().agentName())) {
                foundAgent2 = true;
            }
            assertEquals(ExecutionState.COMPLETED, execution.execution().state());
        }
        
        assertTrue(foundAgent1);
        assertTrue(foundAgent2);
    }

    @Test
    @DisplayName("Should provide real-time status updates during execution")
    void testRealTimeStatusUpdates() {
        // Given
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("step1", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("step2", "Analyze: {step1}", Arrays.asList("step1")),
            createStep("output_node", "Final output: {step2}", Arrays.asList("step2"))
        );
        
        workflow = new GraphWorkflow(chatModel, steps, mcpToolService, executionStatusService);
        
        Map<String, Object> context = new HashMap<>();
        context.put("agentName", "realtime-agent");

        // When
        String result = workflow.execute("test input", context);

        // Then
        assertNotNull(result);
        
        // Verify we can get the execution status
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(1);
        assertEquals(1, history.size());
        
        ExecutionStatusService.ExecutionStatusData execution = history.get(0);
        String executionId = execution.execution().executionId();
        
        // Should be able to retrieve the same execution by ID
        Optional<ExecutionStatusService.ExecutionStatusData> retrieved = 
            executionStatusService.getExecutionStatus(executionId);
        assertTrue(retrieved.isPresent());
        assertEquals(executionId, retrieved.get().execution().executionId());
    }

    // Helper methods
    private WorkflowStepDef createStep(String nodeId, String prompt, List<String> dependsOn) {
        return createStep(nodeId, prompt, dependsOn, null);
    }
    
    private WorkflowStepDef createStep(String nodeId, String prompt, List<String> dependsOn, String tool) {
        WorkflowStepDef step = new WorkflowStepDef();
        step.setNodeId(nodeId);
        step.setPrompt(prompt);
        step.setDependsOn(dependsOn);
        step.setTool(tool);
        return step;
    }
    
    private ChatResponse createMockResponse(String content) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage(content);
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockResponse.getResults()).thenReturn(Arrays.asList(mockGeneration));
        return mockResponse;
    }
    
    private NodeExecutionStatus findNodeById(List<NodeExecutionStatus> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId()))
                .findFirst()
                .orElse(null);
    }
}


