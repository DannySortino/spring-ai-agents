package com.springai.agent.config;

/**
 * Enum representing the different types of workflows that can be executed by agents.
 * Each workflow type defines a different execution pattern and behavior.
 */
public enum WorkflowType {
    
    /**
     * Chain workflow executes steps sequentially, one after another.
     * Each step receives the output of the previous step as input.
     * Supports prompts, tool calls, and conditional logic within the chain.
     * 
     * Configuration properties:
     * - chain: List of WorkflowStepDef objects defining the sequence
     * 
     * Use cases:
     * - Sequential processing where each step depends on the previous
     * - Simple linear workflows
     * - Step-by-step analysis or processing
     * 
     * Example: User input → Analysis → Tool call → Final response
     */
    CHAIN,
    
    /**
     * Parallel workflow executes multiple tasks simultaneously and aggregates results.
     * All tasks run concurrently and their results are combined using an aggregator.
     * Ideal for independent operations that can be parallelized.
     * 
     * Configuration properties:
     * - tasks: List of TaskDef objects to execute in parallel
     * - aggregator: Prompt template to combine results from all tasks
     * 
     * Use cases:
     * - Independent data processing tasks
     * - Concurrent analysis from multiple perspectives
     * - Performance optimization through parallelization
     * 
     * Example: Input → [Task1, Task2, Task3] (parallel) → Aggregator → Final result
     */
    PARALLEL,
    
    /**
     * Orchestrator workflow uses a manager to coordinate multiple specialized workers.
     * The manager analyzes the input and decides which workers to engage.
     * Workers are specialized agents with their own workflows.
     * A synthesizer combines the worker outputs into a final response.
     * 
     * Configuration properties:
     * - managerPrompt: Template for the manager's decision-making process
     * - workers: List of WorkerDef objects with specialized workflows
     * - synthesizerPrompt: Template to combine worker results
     * 
     * Use cases:
     * - Complex multi-specialist scenarios
     * - Enterprise-grade processing with role separation
     * - Hierarchical decision-making workflows
     * 
     * Example: Input → Manager decision → [Worker1, Worker2] → Synthesizer → Result
     */
    ORCHESTRATOR,
    
    /**
     * Routing workflow directs requests to different processing paths based on content.
     * Uses keyword-based or pattern-based routing to select appropriate handlers.
     * Each route can have its own prompt and tool configuration.
     * 
     * Configuration properties:
     * - routes: Map of route names to RouteDef objects
     * 
     * Use cases:
     * - Request classification and routing
     * - Content-based workflow selection
     * - Multi-purpose agents with specialized handling
     * 
     * Example: Input → Route detection → Specialized handler → Response
     */
    ROUTING
}
