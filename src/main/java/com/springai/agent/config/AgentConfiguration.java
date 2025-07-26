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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
@Configuration
public class AgentConfiguration {
    
    @Bean
    public Map<String, AgentService> agentServices(AppProperties appProperties, ChatModel chatModel, McpToolService mcpToolService) {
        System.out.println("Loading agents: " + (appProperties.getAgents() != null ? appProperties.getAgents().size() : "null"));
        if (appProperties.getAgents() != null) {
            for (AgentDef agent : appProperties.getAgents()) {
                System.out.println("Agent: " + agent.getName() + ", workflow: " + agent.getWorkflow());
                if (agent.getWorkflow() != null) {
                    System.out.println("Workflow type: " + agent.getWorkflow().getClass().getSimpleName());
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
    
    
    private Workflow buildWorkflow(WorkflowDef workflowDef, ChatModel chatModel, McpToolService mcpToolService) {
        if (workflowDef == null) {
            throw new IllegalArgumentException("WorkflowDef cannot be null");
        }
        
        WorkflowType type = workflowDef.getType();
        System.out.println("Building workflow type: " + type);
        
        if (WorkflowType.CHAIN.equals(type)) {
            // Use WorkflowStepDef objects directly to support tool calls
            List<WorkflowStepDef> chain = workflowDef.getChain();
            if (chain != null && !chain.isEmpty()) {
                return new ChainWorkflow(chatModel, chain, mcpToolService);
            } else {
                // Fallback for empty chain
                return new ChainWorkflow(chatModel, List.of());
            }
        } else if (WorkflowType.PARALLEL.equals(type)) {
            List<TaskDef> tasks = workflowDef.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                System.out.println("Warning: Parallel workflow has no tasks, creating empty workflow");
                return new ParallelizationWorkflow(chatModel)
                    .parallel(List.of(), workflowDef.getAggregator());
            }
            List<Workflow> childWorkflows = tasks.stream()
                .map(task -> {
                    if (task.getWorkflow() == null) {
                        System.out.println("Warning: Task " + task.getName() + " has null workflow, creating simple chain");
                        return new ChainWorkflow(chatModel, List.of("Simple task: " + task.getName()));
                    }
                    return buildWorkflow(task.getWorkflow(), chatModel, mcpToolService);
                })
                .collect(Collectors.toList());
            return new ParallelizationWorkflow(chatModel)
                .parallel(childWorkflows, workflowDef.getAggregator());
        } else if (WorkflowType.ORCHESTRATOR.equals(type)) {
            List<WorkerDef> workers = workflowDef.getWorkers();
            if (workers == null || workers.isEmpty()) {
                System.out.println("Warning: Orchestrator workflow has no workers, creating empty workflow");
                return new OrchestratorWorkersWorkflow(
                    chatModel,
                    workflowDef.getManagerPrompt(),
                    Map.of(),
                    workflowDef.getSynthesizerPrompt()
                );
            }
            Map<String, Workflow> workerMap = workers.stream()
                .collect(Collectors.toMap(
                    WorkerDef::getName,
                    worker -> {
                        if (worker.getWorkflow() == null) {
                            System.out.println("Warning: Worker " + worker.getName() + " has null workflow, creating simple chain");
                            return new ChainWorkflow(chatModel, List.of("Simple worker: " + worker.getName()));
                        }
                        return buildWorkflow(worker.getWorkflow(), chatModel, mcpToolService);
                    }
                ));
            return new OrchestratorWorkersWorkflow(
                chatModel,
                workflowDef.getManagerPrompt(),
                workerMap,
                workflowDef.getSynthesizerPrompt()
            );
        } else if (WorkflowType.ROUTING.equals(type)) {
            Map<String, RouteDef> routes = workflowDef.getRoutes();
            if (routes == null || routes.isEmpty()) {
                System.out.println("Warning: Routing workflow has no routes, creating empty workflow");
                return new RoutingWorkflow(chatModel, Map.of(), mcpToolService);
            }
            return new RoutingWorkflow(chatModel, routes, mcpToolService);
        } else {
            throw new IllegalArgumentException("Unknown workflow type: " + type);
        }
    }
}
