package com.springai.agents.visualization.autoconfigure;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.autoconfigure.AgentsAutoConfiguration;
import com.springai.agents.visualization.config.WebSocketConfig;
import com.springai.agents.visualization.controller.*;
import com.springai.agents.visualization.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Auto-configuration for the visualization module.
 * Activates when AgentRegistry class is on the classpath and visualization is enabled.
 * Runs after AgentsAutoConfiguration which creates the AgentRegistry bean.
 */
@Slf4j
@AutoConfiguration(after = AgentsAutoConfiguration.class)
@ConditionalOnClass(AgentRegistry.class)
@ConditionalOnProperty(name = "spring.ai.agents.visualization.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(VisualizationProperties.class)
@Import(WebSocketConfig.class)
public class VisualizationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentIntrospectionService agentIntrospectionService(AgentRegistry agentRegistry) {
        log.info("Visualization: AgentIntrospectionService configured");
        return new AgentIntrospectionService(agentRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.visualization.history.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutionHistoryService executionHistoryService(VisualizationProperties props) {
        log.info("Visualization: ExecutionHistoryService configured (max {} entries)", props.getHistory().getMaxEntries());
        return new ExecutionHistoryService(props.getHistory().getMaxEntries());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.visualization.live.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutionTrackingService executionTrackingService(ExecutionHistoryService historyService,
                                                             SimpMessagingTemplate messagingTemplate) {
        log.info("Visualization: ExecutionTrackingService configured (WebSocket live events)");
        return new ExecutionTrackingService(historyService, messagingTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.visualization.analytics.enabled", havingValue = "true", matchIfMissing = true)
    public PerformanceAnalyticsService performanceAnalyticsService(ExecutionHistoryService historyService,
                                                                    VisualizationProperties props) {
        log.info("Visualization: PerformanceAnalyticsService configured (window {})", props.getAnalytics().getWindowSize());
        return new PerformanceAnalyticsService(historyService, props.getAnalytics().getWindowSize());
    }

    // ── Controllers ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public VisualizationPageController visualizationPageController(
            AgentIntrospectionService introspectionService,
            VisualizationProperties props) {
        return new VisualizationPageController(introspectionService, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentApiController agentApiController(AgentIntrospectionService introspectionService) {
        return new AgentApiController(introspectionService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.visualization.testing.enabled", havingValue = "true", matchIfMissing = true)
    public ExecutionApiController executionApiController(AgentRegistry agentRegistry,
                                                          ExecutionHistoryService historyService) {
        return new ExecutionApiController(agentRegistry, historyService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.ai.agents.visualization.analytics.enabled", havingValue = "true", matchIfMissing = true)
    public AnalyticsApiController analyticsApiController(PerformanceAnalyticsService analyticsService) {
        return new AnalyticsApiController(analyticsService);
    }
}

