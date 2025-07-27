package com.springai.agent.config;

import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.controller.VisualizationController;
import com.springai.agent.controller.GraphCreatorController;
import com.springai.agent.controller.VisualizationWebController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration class for visualization features.
 * Uses conditional bean registration based on feature flags to avoid overhead
 * when visualization features are disabled.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, VisualizationProperties.class})
public class VisualizationConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualizationConfiguration.class);
    
    /**
     * Graph structure visualization service.
     * Only enabled when visualization.graphStructure=true
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "visualization", 
        name = "graphStructure", 
        havingValue = "true"
    )
    public GraphVisualizationService graphVisualizationService(AgentsProperties agentsProperties) {
        logger.info("Enabling graph structure visualization service");
        return new GraphVisualizationService(agentsProperties);
    }
    
    /**
     * Execution status tracking service.
     * Only enabled when visualization.realTimeStatus=true
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "visualization", 
        name = "realTimeStatus", 
        havingValue = "true"
    )
    public ExecutionStatusService executionStatusService() {
        logger.info("Enabling real-time execution status service");
        return new ExecutionStatusService();
    }
    
    /**
     * Visualization REST controller.
     * Only enabled when any visualization feature is enabled.
     */
    @Bean
    @ConditionalOnExpression("'${visualization.graphStructure:false}'.toLowerCase() == 'true' || '${visualization.realTimeStatus:false}'.toLowerCase() == 'true'")
    public VisualizationController visualizationController(
            VisualizationProperties visualizationProperties,
            AgentsProperties agentsProperties,
            @Autowired(required = false) GraphVisualizationService graphVisualizationService,
            @Autowired(required = false) ExecutionStatusService executionStatusService) {
        logger.info("Enabling visualization REST controller");
        return new VisualizationController(visualizationProperties, agentsProperties, graphVisualizationService, executionStatusService);
    }
    
    /**
     * Interactive graph creator controller.
     * Only enabled when visualization.interactiveCreator=true
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "visualization", 
        name = "interactiveCreator", 
        havingValue = "true"
    )
    public GraphCreatorController graphCreatorController(AppProperties appProperties) {
        logger.info("Enabling interactive graph creator controller");
        return new GraphCreatorController(appProperties);
    }
    
    /**
     * Visualization web controller for serving HTML templates.
     * Only enabled when any visualization feature is enabled.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "visualization", 
        name = {"graphStructure", "realTimeStatus", "interactiveCreator"}, 
        matchIfMissing = false
    )
    public VisualizationWebController visualizationWebController() {
        logger.info("Enabling visualization web controller");
        return new VisualizationWebController();
    }
    
    /**
     * WebSocket configuration for real-time updates.
     * Only enabled when visualization.realTimeStatus=true
     */
    @Configuration
    @EnableWebSocketMessageBroker
    @ConditionalOnProperty(
        prefix = "visualization", 
        name = "realTimeStatus", 
        havingValue = "true"
    )
    public static class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
        
        private static final Logger wsLogger = LoggerFactory.getLogger(WebSocketConfig.class);
        
        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            wsLogger.info("Configuring WebSocket message broker for real-time status updates");
            config.enableSimpleBroker("/topic");
            config.setApplicationDestinationPrefixes("/app");
        }
        
        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            wsLogger.info("Registering STOMP endpoints for WebSocket connections");
            registry.addEndpoint("/ws/status")
                    .setAllowedOriginPatterns("*")
                    .withSockJS();
        }
    }
}