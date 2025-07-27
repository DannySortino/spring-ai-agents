package com.springai.agent.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for visualization features.
 * Handles the root-level 'visualization:' configuration in application.yml.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ConfigurationProperties(prefix = "visualization")
public class VisualizationProperties {
    
    /**
     * Enable/disable graph structure visualization.
     * Shows the configured graph structure with nodes and dependencies.
     * Default: false (disabled to avoid overhead)
     */
    private boolean graphStructure = false;
    
    /**
     * Enable/disable real-time execution status tracking.
     * Shows which nodes have executed successfully, failed, or not yet run.
     * Default: false (disabled to avoid overhead)
     */
    private boolean realTimeStatus = false;
    
    /**
     * Enable/disable interactive graph creation web interface.
     * Provides a web UI for creating graph .yml files interactively.
     * Default: false (disabled to avoid overhead)
     */
    private boolean interactiveCreator = false;
    
    /**
     * Port for the visualization web interface.
     * Default: 8081 (different from main application port)
     */
    @Builder.Default
    private int port = 8081;
    
    /**
     * Base path for visualization endpoints.
     * Default: "/visualization"
     */
    @Builder.Default
    private String basePath = "/visualization";
    
    /**
     * WebSocket endpoint for real-time updates.
     * Default: "/ws/status"
     */
    @Builder.Default
    private String websocketEndpoint = "/ws/status";
}