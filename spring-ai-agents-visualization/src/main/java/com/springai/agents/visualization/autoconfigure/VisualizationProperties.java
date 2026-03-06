package com.springai.agents.visualization.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the visualization module.
 * Prefix: {@code spring.ai.agents.visualization}
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spring.ai.agents.visualization")
public class VisualizationProperties {

    /** Master kill-switch — disables all visualization beans. */
    private boolean enabled = true;

    /** Base path for all UI routes and API endpoints. */
    private String pathPrefix = "/agents-ui";

    /** Dashboard feature toggle. */
    private Dashboard dashboard = new Dashboard();

    /** DAG visualization toggle. */
    private Dag dag = new Dag();

    /** Live WebSocket monitoring toggle. */
    private Live live = new Live();

    /** Execution history settings. */
    private History history = new History();

    /** Interactive testing toggle. */
    private Testing testing = new Testing();

    /** Workflow comparison toggle. */
    private Comparison comparison = new Comparison();

    /** Performance analytics settings. */
    private Analytics analytics = new Analytics();

    @Getter @Setter
    public static class Dashboard {
        private boolean enabled = true;
    }

    @Getter @Setter
    public static class Dag {
        private boolean enabled = true;
    }

    @Getter @Setter
    public static class Live {
        private boolean enabled = true;
    }

    @Getter @Setter
    public static class History {
        private boolean enabled = true;
        private int maxEntries = 500;
    }

    @Getter @Setter
    public static class Testing {
        private boolean enabled = true;
    }

    @Getter @Setter
    public static class Comparison {
        private boolean enabled = true;
    }

    @Getter @Setter
    public static class Analytics {
        private boolean enabled = true;
        private int windowSize = 100;
    }
}

