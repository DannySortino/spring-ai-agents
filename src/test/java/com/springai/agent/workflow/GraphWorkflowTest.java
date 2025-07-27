package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for GraphWorkflow implementation focusing on core validation functionality.
 * 
 * Tests cover:
 * - Graph construction and validation
 * - Cycle detection
 * - Dependency validation
 * - Error handling
 */
class GraphWorkflowTest {

    @Test
    void testGraphWorkflowCreation() {
        // Given: Simple linear graph input_node -> A -> B -> C -> output_node
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("A", "Process input: {input_node}", Arrays.asList("input_node")),
            createStep("B", "Analyze result: {A}", Arrays.asList("A")),
            createStep("C", "Finalize: {B}", Arrays.asList("B")),
            createStep("output_node", "Final output: {C}", Arrays.asList("C"))
        );

        // When: Create GraphWorkflow
        GraphWorkflow workflow = new GraphWorkflow(null, steps, null);
        
        // Then: Workflow should be created successfully
        assertNotNull(workflow);
    }

    @Test
    void testComplexDependencyPattern() {
        // Given: input_node -> A -> B, B -> C, A -> C -> output_node (as specified in the issue)
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("A", "Process A: {input_node}", Arrays.asList("input_node")),
            createStep("B", "Process B from A: {A}", Arrays.asList("A")),
            createStep("C", "Process C from A and B: {A} and {B}", Arrays.asList("A", "B")),
            createStep("output_node", "Final output: {C}", Arrays.asList("C"))
        );

        // When: Create GraphWorkflow
        GraphWorkflow workflow = new GraphWorkflow(null, steps, null);
        
        // Then: Workflow should be created successfully
        assertNotNull(workflow);
    }

    @Test
    void testCycleDetection() {
        // Given: A -> B -> C -> A (creates a cycle)
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("A", "Process A: {input} and {C}", Arrays.asList("C")),
            createStep("B", "Process B: {A}", Arrays.asList("A")),
            createStep("C", "Process C: {B}", Arrays.asList("B"))
        );

        // When/Then: Creating GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, steps, null);
        });
    }

    @Test
    void testSelfCycleDetection() {
        // Given: A -> A (self cycle)
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("A", "Process A: {input} and {A}", Arrays.asList("A"))
        );

        // When/Then: Creating GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, steps, null);
        });
    }

    @Test
    void testMissingNodeDependency() {
        // Given: A depends on non-existent node B
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("A", "Process A: {B}", Arrays.asList("B"))
        );

        // When/Then: Creating GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, steps, null);
        });
    }

    @Test
    void testDuplicateNodeIds() {
        // Given: Two nodes with same ID
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("A", "Process A1: {input}", null),
            createStep("A", "Process A2: {input}", null)
        );

        // When/Then: Creating GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, steps, null);
        });
    }

    @Test
    void testMissingNodeId() {
        // Given: Node without ID
        WorkflowStepDef step = new WorkflowStepDef();
        step.setPrompt("Process: {input}");
        // nodeId is null
        
        List<WorkflowStepDef> steps = Arrays.asList(step);

        // When/Then: Creating GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, steps, null);
        });
    }

    @Test
    void testEmptyWorkflow() {
        // Given: Empty workflow (should require at least input_node and output_node)
        // When/Then: Creating empty GraphWorkflow should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new GraphWorkflow(null, Collections.emptyList(), null);
        });
    }

    @Test
    void testSingleNodeWorkflow() {
        // Given: Single node workflow with required input_node and output_node
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("A", "Process: {input_node}", Arrays.asList("input_node")),
            createStep("output_node", "Final output: {A}", Arrays.asList("A"))
        );

        // When: Create GraphWorkflow
        GraphWorkflow workflow = new GraphWorkflow(null, steps, null);
        
        // Then: Workflow should be created successfully
        assertNotNull(workflow);
    }

    @Test
    void testDiamondDependencyPattern() {
        // Given: input_node -> A -> B, A -> C, B -> D, C -> D -> output_node (diamond pattern)
        List<WorkflowStepDef> steps = Arrays.asList(
            createStep("input_node", "Receive input: {input}", null),
            createStep("A", "Start: {input_node}", Arrays.asList("input_node")),
            createStep("B", "Branch B from A: {A}", Arrays.asList("A")),
            createStep("C", "Branch C from A: {A}", Arrays.asList("A")),
            createStep("D", "Merge B and C: {B} and {C}", Arrays.asList("B", "C")),
            createStep("output_node", "Final output: {D}", Arrays.asList("D"))
        );

        // When: Create GraphWorkflow
        GraphWorkflow workflow = new GraphWorkflow(null, steps, null);
        
        // Then: Workflow should be created successfully
        assertNotNull(workflow);
    }

    private WorkflowStepDef createStep(String nodeId, String prompt, List<String> dependsOn) {
        WorkflowStepDef step = new WorkflowStepDef();
        step.setNodeId(nodeId);
        step.setPrompt(prompt);
        step.setDependsOn(dependsOn);
        return step;
    }
}