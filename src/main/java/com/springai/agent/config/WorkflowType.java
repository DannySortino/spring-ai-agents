package com.springai.agent.config;

/**
 * Enum representing the unified workflow type for agent execution.
 * The GRAPH workflow type supports all execution patterns including sequential chains,
 * parallel processing, orchestrator patterns, and conditional routing through a
 * unified dependency-based approach.
 */
public enum WorkflowType {
    
    /**
     * Graph workflow executes steps based on dependency relationships between nodes.
     * Each step is a node with a unique ID, and edges represent data dependencies.
     * Execution order is determined by topological sorting of the dependency graph.
     * <p>
     * This unified workflow type supports all execution patterns:
     * - Sequential chains: Steps with linear dependencies (A→B→C)
     * - Parallel processing: Independent steps executed concurrently
     * - Orchestrator patterns: Manager→Workers→Synthesizer with proper dependencies
     * - Conditional routing: If/then/else logic with conditional step definitions
     * - Complex dependencies: Arbitrary dependency graphs (A→B, A→C, B→D, C→D)
     * <p>
     * Configuration properties:
     * - chain: List of WorkflowStepDef objects with nodeId and dependsOn fields
     * - conditional: ConditionalStepDef for if/then/else branching logic
     * <p>
     * Use cases:
     * - All workflow patterns through unified configuration
     * - Complex workflows with arbitrary dependencies
     * - Parallel execution of independent nodes
     * - Data flow between specific steps
     * - Extensible dependency management
     * - Conditional logic and routing
     * <p>
     * Example: NodeA → [NodeB, NodeC] → NodeD (parallel B,C depend on A, D depends on both)
     */
    GRAPH
}
