package com.springai.agent.workflow.dependency;

import java.util.*;

/**
 * Central manager for workflow step dependencies.
 * 
 * This class provides functionality for:
 * 1. Validating dependency configurations
 * 2. Detecting cyclic dependencies
 * 3. Building and managing dependency graphs
 * 4. Processing dependencies at runtime
 * 
 * The DependencyManager treats workflows as directed graphs where:
 * - Nodes are workflow steps identified by their IDs
 * - Edges are dependencies between steps
 * 
 * @author Spring AI Agent Team
 * @since 1.1.0
 */
public class DependencyManager {
    
    /**
     * Validates a dependency configuration by:
     * 1. Checking that all referenced dependency IDs exist
     * 2. Detecting cyclic dependencies
     * 
     * @param stepIds Set of all valid step IDs in the workflow
     * @param dependencies Map of step IDs to their dependency IDs
     * @return ValidationResult containing validation status and any error messages
     */
    public ValidationResult validateDependencies(Set<String> stepIds, Map<String, List<String>> dependencies) {
        ValidationResult result = new ValidationResult();
        
        // Check that all referenced dependency IDs exist
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            String stepId = entry.getKey();
            List<String> stepDependencies = entry.getValue();
            
            if (!stepIds.contains(stepId)) {
                result.addError("Step ID '" + stepId + "' is not defined in the workflow");
            }
            
            for (String dependencyId : stepDependencies) {
                if (!stepIds.contains(dependencyId)) {
                    result.addError("Dependency ID '" + dependencyId + "' referenced by step '" + 
                                   stepId + "' is not defined in the workflow");
                }
            }
        }
        
        // Check for cyclic dependencies
        CycleDetectionResult cycleResult = detectCycles(dependencies);
        if (cycleResult.hasCycle()) {
            result.addError("Cyclic dependency detected: " + cycleResult.getCyclePath());
        }
        
        return result;
    }
    
    /**
     * Detects cycles in the dependency graph using depth-first search.
     * 
     * @param dependencies Map of step IDs to their dependency IDs
     * @return CycleDetectionResult containing cycle detection status and cycle path if found
     */
    public CycleDetectionResult detectCycles(Map<String, List<String>> dependencies) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> cyclePath = new ArrayList<>();
        
        for (String stepId : dependencies.keySet()) {
            if (detectCyclesDFS(stepId, dependencies, visited, recursionStack, cyclePath)) {
                return new CycleDetectionResult(true, String.join(" -> ", cyclePath));
            }
        }
        
        return new CycleDetectionResult(false, "");
    }
    
    /**
     * Helper method for cycle detection using depth-first search.
     */
    private boolean detectCyclesDFS(String stepId, Map<String, List<String>> dependencies, 
                                   Set<String> visited, Set<String> recursionStack, List<String> cyclePath) {
        // If this node is already in the recursion stack, we found a cycle
        if (recursionStack.contains(stepId)) {
            // Build the cycle path
            cyclePath.add(stepId);
            return true;
        }
        
        // If we've already visited this node and found no cycles, skip it
        if (visited.contains(stepId)) {
            return false;
        }
        
        // Mark the current node as visited and add to recursion stack
        visited.add(stepId);
        recursionStack.add(stepId);
        
        // Visit all dependencies
        List<String> stepDependencies = dependencies.getOrDefault(stepId, Collections.emptyList());
        for (String dependencyId : stepDependencies) {
            if (detectCyclesDFS(dependencyId, dependencies, visited, recursionStack, cyclePath)) {
                // Add this step to the cycle path
                cyclePath.add(0, stepId);
                return true;
            }
        }
        
        // Remove the node from recursion stack
        recursionStack.remove(stepId);
        
        return false;
    }
    
    /**
     * Builds a topological sort of the dependency graph.
     * This provides an execution order that respects all dependencies.
     * 
     * @param dependencies Map of step IDs to their dependency IDs
     * @return List of step IDs in topological order
     * @throws IllegalArgumentException if the graph contains cycles
     */
    public List<String> getTopologicalOrder(Map<String, List<String>> dependencies) {
        // First check for cycles
        CycleDetectionResult cycleResult = detectCycles(dependencies);
        if (cycleResult.hasCycle()) {
            throw new IllegalArgumentException("Cannot create topological order: " + cycleResult.getCyclePath());
        }
        
        // Build the topological sort
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // Make sure all steps are included, even those without dependencies
        Set<String> allSteps = new HashSet<>(dependencies.keySet());
        for (List<String> deps : dependencies.values()) {
            allSteps.addAll(deps);
        }
        
        for (String stepId : allSteps) {
            if (!visited.contains(stepId)) {
                topologicalSortDFS(stepId, dependencies, visited, result);
            }
        }
        
        // Reverse the result to get the correct order
        Collections.reverse(result);
        return result;
    }
    
    /**
     * Helper method for topological sort using depth-first search.
     */
    private void topologicalSortDFS(String stepId, Map<String, List<String>> dependencies, 
                                   Set<String> visited, List<String> result) {
        visited.add(stepId);
        
        List<String> stepDependencies = dependencies.getOrDefault(stepId, Collections.emptyList());
        for (String dependencyId : stepDependencies) {
            if (!visited.contains(dependencyId)) {
                topologicalSortDFS(dependencyId, dependencies, visited, result);
            }
        }
        
        result.add(stepId);
    }
    
    /**
     * Processes dependencies for a step at runtime.
     * 
     * @param stepId ID of the current step
     * @param dependencies List of dependency IDs for this step
     * @param resultMapping Map of variable names to dependency step IDs
     * @param stepResults Map of step IDs to their results
     * @param context Context map to update with dependency results
     */
    public void processDependencies(String stepId, List<String> dependencies, 
                                   Map<String, String> resultMapping,
                                   Map<String, Object> stepResults, 
                                   Map<String, Object> context) {
        // Process result mappings if defined
        if (resultMapping != null && !resultMapping.isEmpty()) {
            for (Map.Entry<String, String> mapping : resultMapping.entrySet()) {
                String variableName = mapping.getKey();
                String dependencyId = mapping.getValue();
                
                Object dependencyResult = stepResults.get(dependencyId);
                if (dependencyResult != null) {
                    context.put(variableName, dependencyResult);
                }
            }
        }
        
        // Process direct dependencies if defined
        if (dependencies != null && !dependencies.isEmpty()) {
            Map<String, Object> dependencyResults = new HashMap<>();
            
            for (String dependencyId : dependencies) {
                Object dependencyResult = stepResults.get(dependencyId);
                if (dependencyResult != null) {
                    dependencyResults.put(dependencyId, dependencyResult);
                }
            }
            
            context.put("dependencyResults", dependencyResults);
        }
    }
    
    /**
     * Result class for dependency validation.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
        
        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
    
    /**
     * Result class for cycle detection.
     */
    public static class CycleDetectionResult {
        private final boolean hasCycle;
        private final String cyclePath;
        
        public CycleDetectionResult(boolean hasCycle, String cyclePath) {
            this.hasCycle = hasCycle;
            this.cyclePath = cyclePath;
        }
        
        public boolean hasCycle() {
            return hasCycle;
        }
        
        public String getCyclePath() {
            return cyclePath;
        }
    }
}