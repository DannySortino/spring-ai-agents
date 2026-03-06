package com.springai.agents.autoconfigure;

import com.springai.agents.agent.*;
import com.springai.agents.executor.*;
import com.springai.agents.mcp.AgentToolCallbackProvider;
import com.springai.agents.mcp.McpClientToolResolver;
import com.springai.agents.retry.RetryService;
import com.springai.agents.workflow.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring Boot auto-configuration for the Spring AI Agents framework.
 * <p>
 * Automatically discovers {@link Agent} beans, builds their workflows, creates
 * executors, and populates the {@link AgentRegistry}. Supports both synchronous
 * and reactive execution modes via the {@code spring.ai.agents.reactive} property.
 * <p>
 * All executor beans use {@code @ConditionalOnMissingBean} so users can override
 * any default by providing their own bean.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(AgentsProperties.class)
public class AgentsAutoConfiguration {

    // ── Executors ───────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public InputExecutor inputExecutor() {
        return new InputExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutputExecutor outputExecutor(@Lazy ChatModel chatModel) {
        return new OutputExecutor(chatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmExecutor llmExecutor(@Lazy ChatModel chatModel) {
        return new LlmExecutor(chatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestExecutor restExecutor(WebClient.Builder webClientBuilder) {
        return new RestExecutor(webClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextExecutor contextExecutor() {
        return new ContextExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(Optional<McpClientToolResolver> toolResolver, @Lazy ChatModel chatModel) {
        return new ToolExecutor(toolResolver.orElse(null), chatModel);
    }

    // ── Executor Registry ───────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public NodeExecutorRegistry nodeExecutorRegistry(
            InputExecutor inputExecutor,
            OutputExecutor outputExecutor,
            LlmExecutor llmExecutor,
            RestExecutor restExecutor,
            ContextExecutor contextExecutor,
            ToolExecutor toolExecutor,
            Optional<List<NodeExecutor<?>>> customExecutors) {

        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(inputExecutor);
        registry.register(outputExecutor);
        registry.register(llmExecutor);
        registry.register(restExecutor);
        registry.register(contextExecutor);
        registry.register(toolExecutor);

        // Register any user-provided custom executors
        customExecutors.ifPresent(executors -> executors.forEach(executor -> {
            if (!(executor instanceof InputExecutor) && !(executor instanceof OutputExecutor)
                    && !(executor instanceof LlmExecutor) && !(executor instanceof RestExecutor)
                    && !(executor instanceof ContextExecutor) && !(executor instanceof ToolExecutor)) {
                registry.register(executor);
                log.info("Registered custom executor: {} for node type {}",
                        executor.getClass().getSimpleName(), executor.getNodeType().getSimpleName());
            }
        }));

        log.info("NodeExecutorRegistry initialized with {} executors", 6 + customExecutors.map(List::size).orElse(0));
        return registry;
    }

    // ── Workflow Router ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public WorkflowRouter workflowRouter(@Lazy ChatModel chatModel) {
        log.info("Using LLM-based workflow router");
        return new LlmWorkflowRouter(chatModel);
    }

    // ── Workflow Executors ──────────────────────────────────────────────

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "workflowThreadPool")
    @ConditionalOnProperty(name = "spring.ai.agents.reactive", havingValue = "false", matchIfMissing = true)
    public ExecutorService workflowThreadPool(AgentsProperties properties) {
        ExecutorService threadPool = properties.getParallelThreads() > 0
                ? Executors.newFixedThreadPool(properties.getParallelThreads())
                : Executors.newCachedThreadPool();
        log.info("Created workflow thread pool: {}", 
                properties.getParallelThreads() > 0 
                    ? "fixed(" + properties.getParallelThreads() + ")" 
                    : "cached");
        return threadPool;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.reactive", havingValue = "false", matchIfMissing = true)
    public WorkflowExecutor workflowExecutor(NodeExecutorRegistry executorRegistry, 
                                             ExecutorService workflowThreadPool,
                                             Optional<ApplicationEventPublisher> eventPublisher) {
        return new WorkflowExecutor(executorRegistry, workflowThreadPool, eventPublisher.orElse(null));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.reactive", havingValue = "true")
    public ReactiveWorkflowExecutor reactiveWorkflowExecutor(NodeExecutorRegistry executorRegistry) {
        return new ReactiveWorkflowExecutor(executorRegistry);
    }

    // ── Agent Registry ──────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.reactive", havingValue = "false", matchIfMissing = true)
    public AgentRegistry syncAgentRegistry(List<Agent> agents, WorkflowExecutor workflowExecutor,
                                           WorkflowRouter workflowRouter) {
        log.info("Discovering {} Agent beans (sync mode)", agents.size());
        Map<String, AgentRuntime> runtimeMap = buildSyncRuntimes(agents, workflowExecutor, workflowRouter);
        return AgentRegistry.ofSync(runtimeMap);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.reactive", havingValue = "true")
    public AgentRegistry reactiveAgentRegistry(List<Agent> agents, ReactiveWorkflowExecutor workflowExecutor,
                                                WorkflowRouter workflowRouter) {
        log.info("Discovering {} Agent beans (reactive mode)", agents.size());
        Map<String, ReactiveAgentRuntime> runtimeMap = buildReactiveRuntimes(agents, workflowExecutor, workflowRouter);
        return AgentRegistry.ofReactive(runtimeMap);
    }

    // ── MCP Integration ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "spring.ai.agents.mcp-server.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    @Lazy
    public AgentToolCallbackProvider agentToolCallbackProvider(AgentRegistry agentRegistry) {
        log.info("MCP Server enabled — exposing {} agents as MCP tools", agentRegistry.size());
        return new AgentToolCallbackProvider(agentRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpClientToolResolver mcpClientToolResolver(
            ObjectProvider<ToolCallbackProvider> providers) {
        return new McpClientToolResolver(providers);
    }

    // ── Retry ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public RetryService retryService() {
        return new RetryService();
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private Map<String, AgentRuntime> buildSyncRuntimes(List<Agent> agents,
                                                        WorkflowExecutor workflowExecutor,
                                                        WorkflowRouter workflowRouter) {
        Map<String, AgentRuntime> map = new LinkedHashMap<>();
        for (Agent agent : agents) {
            try {
                List<Workflow> workflows = agent.buildWorkflows();
                AgentRuntime runtime = new AgentRuntime(agent, workflows, workflowExecutor, workflowRouter);
                map.put(agent.getName(), runtime);
                log.info("Registered agent: '{}' — {} ({} workflow(s))",
                        agent.getName(), agent.getDescription(), workflows.size());
                workflows.forEach(w -> log.info("  └── Workflow '{}': {} — {} nodes, {} edges",
                        w.getName(), w.getDescription(), w.size(), w.getEdges().size()));
            } catch (Exception e) {
                log.error("Failed to build workflows for agent '{}': {}", agent.getName(), e.getMessage(), e);
                throw new IllegalStateException("Agent '%s' workflow build failed".formatted(agent.getName()), e);
            }
        }
        return map;
    }

    private Map<String, ReactiveAgentRuntime> buildReactiveRuntimes(List<Agent> agents,
                                                                     ReactiveWorkflowExecutor workflowExecutor,
                                                                     WorkflowRouter workflowRouter) {
        Map<String, ReactiveAgentRuntime> map = new LinkedHashMap<>();
        for (Agent agent : agents) {
            try {
                List<Workflow> workflows = agent.buildWorkflows();
                ReactiveAgentRuntime runtime = new ReactiveAgentRuntime(agent, workflows, workflowExecutor, workflowRouter);
                map.put(agent.getName(), runtime);
                log.info("Registered reactive agent: '{}' — {} ({} workflow(s))",
                        agent.getName(), agent.getDescription(), workflows.size());
                workflows.forEach(w -> log.info("  └── Workflow '{}': {} — {} nodes, {} edges",
                        w.getName(), w.getDescription(), w.size(), w.getEdges().size()));
            } catch (Exception e) {
                log.error("Failed to build workflows for agent '{}': {}", agent.getName(), e.getMessage(), e);
                throw new IllegalStateException("Agent '%s' workflow build failed".formatted(agent.getName()), e);
            }
        }
        return map;
    }
}

