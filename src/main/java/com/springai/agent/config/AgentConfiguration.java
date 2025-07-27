package com.springai.agent.config;

import com.springai.agent.service.AgentService;
import com.springai.agent.service.McpToolService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.workflow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring configuration class for creating and managing AI agents.
 * <p>
 * This configuration class is responsible for:
 * - Creating AgentService beans from application properties
 * - Building different types of workflows (chain, parallel, orchestrator, routing)
 * - Integrating workflows with chat models and MCP tool services
 * <p>
 * The class reads agent definitions from AppProperties and creates corresponding
 * AgentService instances with their associated workflows.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, AgentsProperties.class})
public class AgentConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfiguration.class);
    
    @Bean
    public Map<String, AgentService> agentServices(AgentsProperties agentsProperties, ChatModel chatModel, McpToolService mcpToolService,
                                                  Optional<ExecutionStatusService> executionStatusService) {
        logger.info("=== AGENT CONFIGURATION DEBUG ===");
        logger.info("AgentsProperties instance: {}", agentsProperties);
        logger.info("AgentsProperties class: {}", agentsProperties.getClass().getName());
        logger.info("Agents list: {}", agentsProperties.getList());
        logger.info("Loading agents: {}", (agentsProperties.getList() != null ? agentsProperties.getList().size() : "null"));
        
        // Handle null or empty agents list
        if (agentsProperties.getList() == null || agentsProperties.getList().isEmpty()) {
            logger.warn("No agents configured - returning empty agent services map");
            logger.warn("AgentsProperties toString: {}", agentsProperties);
            return new HashMap<>();
        }
        
        for (AgentsProperties.AgentDef agent : agentsProperties.getList()) {
            logger.info("Agent: {}, workflow: {}", agent.getName(), agent.getWorkflow());
            if (agent.getWorkflow() != null) {
                logger.info("Workflow type: {}", agent.getWorkflow().getType());
            }
        }
        
        return agentsProperties.getList().stream()
            .collect(Collectors.toMap(
                AgentsProperties.AgentDef::getName,
                agentDef -> new AgentService(
                    agentDef.getName(),
                    agentDef.getSystemPrompt(),
                    buildWorkflow(agentDef.getWorkflow(), chatModel, mcpToolService, executionStatusService.orElse(null))
                )
            ));
    }
    
    
    public Workflow buildWorkflow(AgentsProperties.WorkflowDef workflowDef, ChatModel chatModel, McpToolService mcpToolService, ExecutionStatusService executionStatusService) {
        if (workflowDef == null) {
            throw new IllegalArgumentException("WorkflowDef cannot be null");
        }
        
        WorkflowType type = workflowDef.getType();
        logger.info("Building workflow type: {}", type);
        
        if (WorkflowType.GRAPH.equals(type)) {
            List<AgentsProperties.WorkflowStepDef> chain = workflowDef.getChain();
            if (chain != null && !chain.isEmpty()) {
                // Convert AgentsProperties.WorkflowStepDef to AppProperties.WorkflowStepDef for GraphWorkflow
                List<AppProperties.WorkflowStepDef> convertedChain = chain.stream()
                    .map(this::convertWorkflowStepDef)
                    .collect(Collectors.toList());
                return new GraphWorkflow(chatModel, convertedChain, mcpToolService, executionStatusService);
            } else {
                // Fallback for empty graph
                return new GraphWorkflow(chatModel, List.of(), mcpToolService, executionStatusService);
            }
        } else {
            throw new IllegalArgumentException("Unknown workflow type: " + type);
        }
    }
    
    /**
     * Convert AgentsProperties.WorkflowStepDef to AppProperties.WorkflowStepDef for compatibility.
     */
    private AppProperties.WorkflowStepDef convertWorkflowStepDef(AgentsProperties.WorkflowStepDef agentStep) {
        AppProperties.WorkflowStepDef appStep = new AppProperties.WorkflowStepDef();
        appStep.setPrompt(agentStep.getPrompt());
        appStep.setTool(agentStep.getTool());
        appStep.setNodeId(agentStep.getNodeId());
        appStep.setDependsOn(agentStep.getDependsOn());
        // Note: Not converting nested workflow, conditional, retry, contextManagement for now
        // These can be added if needed
        return appStep;
    }
}
