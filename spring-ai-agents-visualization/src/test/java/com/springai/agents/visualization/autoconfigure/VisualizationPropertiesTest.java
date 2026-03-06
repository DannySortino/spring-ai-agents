package com.springai.agents.visualization.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VisualizationProperties")
class VisualizationPropertiesTest {

    @Test
    @DisplayName("defaults are sensible")
    void defaultValues() {
        VisualizationProperties props = new VisualizationProperties();

        assertTrue(props.isEnabled());
        assertEquals("/agents-ui", props.getPathPrefix());
        assertTrue(props.getDashboard().isEnabled());
        assertTrue(props.getDag().isEnabled());
        assertTrue(props.getLive().isEnabled());
        assertTrue(props.getHistory().isEnabled());
        assertEquals(500, props.getHistory().getMaxEntries());
        assertTrue(props.getTesting().isEnabled());
        assertTrue(props.getComparison().isEnabled());
        assertTrue(props.getAnalytics().isEnabled());
        assertEquals(100, props.getAnalytics().getWindowSize());
    }

    @Nested
    @DisplayName("customization")
    class Customization {

        @Test
        @DisplayName("can disable features")
        void disableFeatures() {
            VisualizationProperties props = new VisualizationProperties();
            props.setEnabled(false);
            props.getDashboard().setEnabled(false);
            props.getHistory().setEnabled(false);
            props.getAnalytics().setEnabled(false);

            assertFalse(props.isEnabled());
            assertFalse(props.getDashboard().isEnabled());
            assertFalse(props.getHistory().isEnabled());
            assertFalse(props.getAnalytics().isEnabled());
        }

        @Test
        @DisplayName("can set custom path prefix")
        void customPathPrefix() {
            VisualizationProperties props = new VisualizationProperties();
            props.setPathPrefix("/custom-ui");

            assertEquals("/custom-ui", props.getPathPrefix());
        }

        @Test
        @DisplayName("can set custom history max entries")
        void customHistoryMaxEntries() {
            VisualizationProperties props = new VisualizationProperties();
            props.getHistory().setMaxEntries(1000);

            assertEquals(1000, props.getHistory().getMaxEntries());
        }

        @Test
        @DisplayName("can set custom analytics window size")
        void customAnalyticsWindow() {
            VisualizationProperties props = new VisualizationProperties();
            props.getAnalytics().setWindowSize(200);

            assertEquals(200, props.getAnalytics().getWindowSize());
        }
    }
}

