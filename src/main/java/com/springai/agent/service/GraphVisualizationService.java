package com.springai.agent.service;

import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.AgentsProperties.AgentDef;
import com.springai.agent.config.AgentsProperties.WorkflowDef;
import com.springai.agent.config.AgentsProperties.WorkflowStepDef;
import com.springai.agent.config.AgentsProperties.ConditionalStepDef;
import com.springai.agent.config.WorkflowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for extracting and preparing graph structure data for visualization.
 * Analyzes workflow configurations and creates visualization-friendly data structures.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Service
public class GraphVisualizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphVisualizationService.class);
    
    private final AgentsProperties agentsProperties;
    
    public GraphVisualizationService(AgentsProperties agentsProperties) {
        this.agentsProperties = agentsProperties;
    }
    
    /**
     * Get all available agents with their graph structures.
     */
    public List<AgentGraphData> getAllAgentGraphs() {
        if (agentsProperties.getList() == null) {
            return Collections.emptyList();
        }
        
        return agentsProperties.getList().stream()
                .filter(agent -> agent.getWorkflow() != null)
                .filter(agent -> WorkflowType.GRAPH.equals(agent.getWorkflow().getType()))
                .map(this::extractAgentGraphData)
                .collect(Collectors.toList());
    }
    
    /**
     * Get graph structure for a specific agent.
     */
    public Optional<AgentGraphData> getAgentGraph(String agentName) {
        if (agentsProperties.getList() == null) {
            return Optional.empty();
        }
        
        return agentsProperties.getList().stream()
                .filter(agent -> agentName.equals(agent.getName()))
                .filter(agent -> agent.getWorkflow() != null)
                .filter(agent -> WorkflowType.GRAPH.equals(agent.getWorkflow().getType()))
                .map(this::extractAgentGraphData)
                .findFirst();
    }
    
    /**
     * Extract graph data from an agent definition.
     */
    private AgentGraphData extractAgentGraphData(AgentDef agent) {
        logger.debug("Extracting graph data for agent: {}", agent.getName());
        
        WorkflowDef workflow = agent.getWorkflow();
        List<NodeData> nodes = new ArrayList<>();
        List<EdgeData> edges = new ArrayList<>();
        
        if (workflow.getChain() != null) {
            for (WorkflowStepDef step : workflow.getChain()) {
                // Extract node data
                NodeData node = extractNodeData(step);
                nodes.add(node);
                
                // Extract edge data (dependencies)
                if (step.getDependsOn() != null) {
                    for (String dependency : step.getDependsOn()) {
                        EdgeData edge = new EdgeData(dependency, step.getNodeId());
                        edges.add(edge);
                    }
                }
                
                // Handle conditional steps
                if (step.getConditional() != null) {
                    extractConditionalNodes(step.getConditional(), step.getNodeId(), nodes, edges);
                }
            }
        }
        
        return new AgentGraphData(agent.getName(), agent.getSystemPrompt(), nodes, edges);
    }
    
    /**
     * Extract node data from a workflow step.
     */
    private NodeData extractNodeData(WorkflowStepDef step) {
        String nodeType = determineNodeType(step);
        String label = step.getNodeId() != null ? step.getNodeId() : "unnamed";
        String description = step.getPrompt() != null ? step.getPrompt() : "";
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("hasPrompt", step.getPrompt() != null);
        properties.put("hasTool", step.getTool() != null);
        properties.put("hasConditional", step.getConditional() != null);
        properties.put("hasRetry", step.getRetry() != null);
        properties.put("hasContextManagement", step.getContextManagement() != null);
        
        if (step.getTool() != null) {
            properties.put("tool", step.getTool());
        }
        
        return new NodeData(step.getNodeId(), label, description, nodeType, properties);
    }
    
    /**
     * Determine the type of node based on its configuration.
     */
    private String determineNodeType(WorkflowStepDef step) {
        // Check for special input/output nodes first
        if ("input_node".equals(step.getNodeId())) {
            return "input_node";
        } else if ("output_node".equals(step.getNodeId())) {
            return "output_node";
        } else if (step.getConditional() != null) {
            return "conditional";
        } else if (step.getTool() != null) {
            return "tool";
        } else if (step.getPrompt() != null) {
            return "prompt";
        } else {
            return "unknown";
        }
    }
    
    /**
     * Extract nodes and edges from conditional steps.
     */
    private void extractConditionalNodes(ConditionalStepDef conditional, String parentNodeId, 
                                       List<NodeData> nodes, List<EdgeData> edges) {
        if (conditional.getThenStep() != null) {
            String thenNodeId = parentNodeId + "_then";
            NodeData thenNode = new NodeData(
                thenNodeId,
                "Then: " + thenNodeId,
                conditional.getThenStep().getPrompt(),
                "conditional_then",
                Map.of("condition", "true")
            );
            nodes.add(thenNode);
            edges.add(new EdgeData(parentNodeId, thenNodeId));
        }
        
        if (conditional.getElseStep() != null) {
            String elseNodeId = parentNodeId + "_else";
            NodeData elseNode = new NodeData(
                elseNodeId,
                "Else: " + elseNodeId,
                conditional.getElseStep().getPrompt(),
                "conditional_else",
                Map.of("condition", "false")
            );
            nodes.add(elseNode);
            edges.add(new EdgeData(parentNodeId, elseNodeId));
        }
    }

    /**
         * Data class representing an agent's graph structure.
         */
        public record AgentGraphData(String agentName, String systemPrompt, List<NodeData> nodes, List<EdgeData> edges) {
    }

    /**
         * Data class representing a graph node.
         */
        public record NodeData(String id, String label, String description, String type, Map<String, Object> properties) {
    }

    /**
         * Data class representing a graph edge.
         */
        public record EdgeData(String source, String target) {
    }
}