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
 * - ChainWorkflow: Sequential execution of workflow steps
 * - ParallelizationWorkflow: Parallel execution of multiple tasks
 * - OrchestratorWorkersWorkflow: Manager-worker pattern with synthesis
 * - RoutingWorkflow: Conditional routing based on input analysis
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public interface Workflow {
    String execute(String input, Map<String, Object> context);
}
