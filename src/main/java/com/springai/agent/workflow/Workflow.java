package com.springai.agent.workflow;

import java.util.Map;

/**
 * Base interface for all AI agent workflow implementations.
 * 
 * This interface defines the contract that all workflow types must implement.
 * Workflows are responsible for processing user input through various execution
 * patterns such as sequential chains, parallel processing, orchestrated workers,
 * or conditional routing.
 * 
 * Implementations include:
 * - GraphWorkflow: Unified workflow engine supporting all execution patterns including:
 *   - Graph-based execution with arbitrary dependencies and parallel processing
 *   - Manager-worker orchestration pattern with synthesis
 *   - Conditional routing based on input analysis
 *   - Complex conditional logic with if/then/else branching
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
public interface Workflow {
    String execute(String input, Map<String, Object> context);
}
