package com.springai.agent.config;

import com.springai.agent.config.AppProperties.AgentDef;
import com.springai.agent.config.AppProperties.WorkflowDef;
import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.TaskDef;
import com.springai.agent.config.AppProperties.WorkerDef;
import com.springai.agent.config.AppProperties.RouteDef;
import com.springai.agent.config.WorkflowType;
import com.springai.agent.service.AgentService;
import com.springai.agent.service.McpToolService;
import com.springai.agent.workflow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring configuration class for creating and managing AI agents.
 * 
 * This configuration class is responsible for:
 * - Creating AgentService beans from application properties
 * - Building different types of workflows (chain, parallel, orchestrator, routing)
 * - Integrating workflows with chat models and MCP tool services
 * 
 * The class reads agent definitions from AppProperties and creates corresponding
 * AgentService instances with their associated workflows.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Configuration
public class AgentConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfiguration.class);
    
    @Bean
    public Map<String, AgentService> agentServices(AppProperties appProperties, ChatModel chatModel, McpToolService mcpToolService) {
        logger.info("Loading agents: {}", (appProperties.getAgents() != null ? appProperties.getAgents().size() : "null"));
        if (appProperties.getAgents() != null) {
            for (AgentDef agent : appProperties.getAgents()) {
                logger.info("Agent: {}, workflow: {}", agent.getName(), agent.getWorkflow());
                if (agent.getWorkflow() != null) {
                    logger.info("Workflow type: {}", agent.getWorkflow().getClass().getSimpleName());
                }
            }
        }
        
        return appProperties.getAgents().stream()
            .collect(Collectors.toMap(
                AgentDef::getName,
                agentDef -> new AgentService(
                    agentDef.getName(),
                    agentDef.getSystemPrompt(),
                    buildWorkflow(agentDef.getWorkflow(), chatModel, mcpToolService)
                )
            ));
    }
    
    
    public Workflow buildWorkflow(WorkflowDef workflowDef, ChatModel chatModel, McpToolService mcpToolService) {
        if (workflowDef == null) {
            throw new IllegalArgumentException("WorkflowDef cannot be null");
        }
        
        WorkflowType type = workflowDef.getType();
        logger.info("Building workflow type: {}", type);
        
        if (WorkflowType.GRAPH.equals(type)) {
            List<WorkflowStepDef> chain = workflowDef.getChain();
            if (chain != null && !chain.isEmpty()) {
                return new GraphWorkflow(chatModel, chain, mcpToolService);
            } else {
                // Fallback for empty graph
                return new GraphWorkflow(chatModel, List.of());
            }
        } else {
            throw new IllegalArgumentException("Unknown workflow type: " + type);
        }
    }
}
