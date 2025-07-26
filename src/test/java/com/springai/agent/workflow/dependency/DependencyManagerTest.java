package com.springai.agent.workflow.dependency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DependencyManager class.
 */
public class DependencyManagerTest {

    private DependencyManager dependencyManager;

    @BeforeEach
    public void setUp() {
        dependencyManager = new DependencyManager();
    }

    @Test
    public void testValidateDependencies_ValidDependencies() {
        // Set up test data
        Set<String> stepIds = new HashSet<>(Arrays.asList("A", "B", "C"));
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("B", Arrays.asList("A"));
        dependencies.put("C", Arrays.asList("A", "B"));

        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(stepIds, dependencies);

        // Assert
        assertTrue(result.isValid(), "Dependencies should be valid");
        assertEquals(0, result.getErrors().size(), "There should be no errors");
    }

    @Test
    public void testValidateDependencies_MissingStepId() {
        // Set up test data
        Set<String> stepIds = new HashSet<>(Arrays.asList("A", "B"));
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("C", Arrays.asList("A", "B")); // C is not in stepIds

        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(stepIds, dependencies);

        // Assert
        assertFalse(result.isValid(), "Dependencies should be invalid");
        assertEquals(1, result.getErrors().size(), "There should be one error");
        assertTrue(result.getErrorMessage().contains("Step ID 'C' is not defined"), 
                "Error message should mention missing step ID");
    }

    @Test
    public void testValidateDependencies_MissingDependencyId() {
        // Set up test data
        Set<String> stepIds = new HashSet<>(Arrays.asList("A", "B"));
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("B", Arrays.asList("A", "C")); // C is not in stepIds

        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(stepIds, dependencies);

        // Assert
        assertFalse(result.isValid(), "Dependencies should be invalid");
        assertEquals(1, result.getErrors().size(), "There should be one error");
        assertTrue(result.getErrorMessage().contains("Dependency ID 'C' referenced by step 'B' is not defined"), 
                "Error message should mention missing dependency ID");
    }

    @Test
    public void testDetectCycles_NoCycles() {
        // Set up test data
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("A", Collections.emptyList());
        dependencies.put("B", Arrays.asList("A"));
        dependencies.put("C", Arrays.asList("A", "B"));

        // Detect cycles
        DependencyManager.CycleDetectionResult result = dependencyManager.detectCycles(dependencies);

        // Assert
        assertFalse(result.hasCycle(), "There should be no cycles");
        assertEquals("", result.getCyclePath(), "Cycle path should be empty");
    }

    @Test
    public void testDetectCycles_DirectCycle() {
        // Set up test data with a direct cycle: A -> B -> A
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("A", Arrays.asList("B"));
        dependencies.put("B", Arrays.asList("A"));

        // Detect cycles
        DependencyManager.CycleDetectionResult result = dependencyManager.detectCycles(dependencies);

        // Assert
        assertTrue(result.hasCycle(), "There should be a cycle");
        assertFalse(result.getCyclePath().isEmpty(), "Cycle path should not be empty");
        assertTrue(result.getCyclePath().contains("A") && result.getCyclePath().contains("B"), 
                "Cycle path should include both A and B");
    }

    @Test
    public void testDetectCycles_IndirectCycle() {
        // Set up test data with an indirect cycle: A -> B -> C -> A
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("A", Arrays.asList("C"));
        dependencies.put("B", Arrays.asList("A"));
        dependencies.put("C", Arrays.asList("B"));

        // Detect cycles
        DependencyManager.CycleDetectionResult result = dependencyManager.detectCycles(dependencies);

        // Assert
        assertTrue(result.hasCycle(), "There should be a cycle");
        assertFalse(result.getCyclePath().isEmpty(), "Cycle path should not be empty");
        assertTrue(result.getCyclePath().contains("A") && 
                   result.getCyclePath().contains("B") && 
                   result.getCyclePath().contains("C"), 
                "Cycle path should include A, B, and C");
    }

    @Test
    public void testGetTopologicalOrder_ValidDependencies() {
        // Set up test data
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("A", Collections.emptyList());
        dependencies.put("B", Arrays.asList("A"));
        dependencies.put("C", Arrays.asList("A", "B"));

        // Get topological order
        List<String> order = dependencyManager.getTopologicalOrder(dependencies);

        // Assert
        assertEquals(3, order.size(), "Order should contain all steps");
        assertTrue(order.indexOf("A") < order.indexOf("B"), "A should come before B");
        assertTrue(order.indexOf("B") < order.indexOf("C"), "B should come before C");
    }

    @Test
    public void testGetTopologicalOrder_WithCycle() {
        // Set up test data with a cycle
        Map<String, List<String>> dependencies = new HashMap<>();
        dependencies.put("A", Arrays.asList("C"));
        dependencies.put("B", Arrays.asList("A"));
        dependencies.put("C", Arrays.asList("B"));

        // Assert that getting topological order throws an exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            dependencyManager.getTopologicalOrder(dependencies);
        });

        assertTrue(exception.getMessage().contains("Cannot create topological order"), 
                "Exception message should mention that topological order cannot be created");
    }

    @Test
    public void testProcessDependencies() {
        // Set up test data
        String stepId = "C";
        List<String> dependencies = Arrays.asList("A", "B");
        Map<String, String> resultMapping = new HashMap<>();
        resultMapping.put("resultA", "A");
        resultMapping.put("resultB", "B");
        
        Map<String, Object> stepResults = new HashMap<>();
        stepResults.put("A", "Result from A");
        stepResults.put("B", "Result from B");
        
        Map<String, Object> context = new HashMap<>();

        // Process dependencies
        dependencyManager.processDependencies(stepId, dependencies, resultMapping, stepResults, context);

        // Assert
        assertEquals("Result from A", context.get("resultA"), "Context should contain mapped result for A");
        assertEquals("Result from B", context.get("resultB"), "Context should contain mapped result for B");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> dependencyResults = (Map<String, Object>) context.get("dependencyResults");
        assertNotNull(dependencyResults, "Context should contain dependencyResults map");
        assertEquals("Result from A", dependencyResults.get("A"), "dependencyResults should contain result for A");
        assertEquals("Result from B", dependencyResults.get("B"), "dependencyResults should contain result for B");
    }
}