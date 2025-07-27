package com.springai.agent.controller;

import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.VisualizationProperties;
import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for visualization endpoints.
 * Provides APIs for graph structure visualization and execution status tracking.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@RestController
@RequestMapping("${spring.ai.agents.visualization.base-path:/visualization}")
@CrossOrigin(origins = "*")
public class VisualizationController {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualizationController.class);
    
    private final VisualizationProperties visualizationProperties;
    private final AgentsProperties agentsProperties;
    private final GraphVisualizationService graphVisualizationService;
    private final ExecutionStatusService executionStatusService;
    
    public VisualizationController(VisualizationProperties visualizationProperties,
                                 AgentsProperties agentsProperties,
                                 GraphVisualizationService graphVisualizationService,
                                 ExecutionStatusService executionStatusService) {
        this.visualizationProperties = visualizationProperties;
        this.agentsProperties = agentsProperties;
        this.graphVisualizationService = graphVisualizationService;
        this.executionStatusService = executionStatusService;
    }
    
    /**
     * Get visualization configuration and feature flags.
     */
    @GetMapping("/config")
    public ResponseEntity<VisualizationConfig> getConfig() {
        VisualizationConfig config = new VisualizationConfig(
            visualizationProperties.isGraphStructure(),
            visualizationProperties.isRealTimeStatus(),
            visualizationProperties.isInteractiveCreator(),
            visualizationProperties.getBasePath(),
            visualizationProperties.getWebsocketEndpoint()
        );
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Get all agent graphs for structure visualization.
     */
    @GetMapping("/graphs")
    public ResponseEntity<List<GraphVisualizationService.AgentGraphData>> getAllGraphs() {
        logger.debug("Fetching all agent graphs");
        List<GraphVisualizationService.AgentGraphData> graphs = graphVisualizationService.getAllAgentGraphs();
        return ResponseEntity.ok(graphs);
    }
    
    /**
     * Get specific agent graph structure.
     */
    @GetMapping("/graphs/{agentName}")
    public ResponseEntity<GraphVisualizationService.AgentGraphData> getAgentGraph(@PathVariable String agentName) {
        logger.debug("Fetching graph for agent: {}", agentName);
        Optional<GraphVisualizationService.AgentGraphData> graph = graphVisualizationService.getAgentGraph(agentName);
        
        return graph.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get all active executions.
     */
    @GetMapping("/executions/active")
    public ResponseEntity<List<ExecutionStatusService.ExecutionStatusData>> getActiveExecutions() {
        logger.debug("Fetching active executions");
        List<ExecutionStatusService.ExecutionStatusData> executions = executionStatusService.getAllActiveExecutions();
        return ResponseEntity.ok(executions);
    }
    
    /**
     * Get execution history.
     */
    @GetMapping("/executions/history")
    public ResponseEntity<List<ExecutionStatusService.ExecutionStatusData>> getExecutionHistory(
            @RequestParam(defaultValue = "50") int limit) {
        logger.debug("Fetching execution history with limit: {}", limit);
        List<ExecutionStatusService.ExecutionStatusData> history = executionStatusService.getExecutionHistory(limit);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Get specific execution status.
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ExecutionStatusService.ExecutionStatusData> getExecutionStatus(@PathVariable String executionId) {
        logger.debug("Fetching execution status for: {}", executionId);
        Optional<ExecutionStatusService.ExecutionStatusData> status = executionStatusService.getExecutionStatus(executionId);
        
        return status.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        HealthStatus status = new HealthStatus(
            "UP",
            System.currentTimeMillis(),
            visualizationProperties.isGraphStructure(),
            visualizationProperties.isRealTimeStatus(),
            visualizationProperties.isInteractiveCreator()
        );
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get available agents list.
     */
    @GetMapping("/agents")
    public ResponseEntity<List<AgentInfo>> getAgents() {
        logger.debug("Fetching available agents");
        
        if (agentsProperties.getList() == null) {
            return ResponseEntity.ok(List.of());
        }
        
        List<AgentInfo> agents = agentsProperties.getList().stream()
                .map(agent -> new AgentInfo(
                    agent.getName(),
                    agent.getSystemPrompt(),
                    agent.getWorkflow() != null ? agent.getWorkflow().getType().name() : "NONE",
                    agent.getWorkflow() != null && agent.getWorkflow().getChain() != null ? 
                        agent.getWorkflow().getChain().size() : 0
                ))
                .toList();
        
        return ResponseEntity.ok(agents);
    }

    /**
         * Configuration data class.
         */
        public record VisualizationConfig(boolean graphStructureEnabled, boolean realTimeStatusEnabled,
                                          boolean interactiveCreatorEnabled, String basePath, String websocketEndpoint) {
    }

    /**
         * Health status data class.
         */
        public record HealthStatus(String status, long timestamp, boolean graphStructureEnabled,
                                   boolean realTimeStatusEnabled, boolean interactiveCreatorEnabled) {
    }

    /**
         * Agent information data class.
         */
        public record AgentInfo(String name, String systemPrompt, String workflowType, int nodeCount) {
    }
}