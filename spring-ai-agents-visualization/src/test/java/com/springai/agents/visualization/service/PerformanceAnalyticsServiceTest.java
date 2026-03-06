package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.dto.NodePerfDto;
import com.springai.agents.visualization.dto.NodeStatusDto;
import com.springai.agents.visualization.dto.PerformanceStatsDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PerformanceAnalyticsService")
class PerformanceAnalyticsServiceTest {

    private ExecutionHistoryService historyService;
    private PerformanceAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        historyService = new ExecutionHistoryService(100);
        analyticsService = new PerformanceAnalyticsService(historyService, 50);
    }

    // ── getAgentStats ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getAgentStats")
    class GetAgentStats {

        @Test
        @DisplayName("returns zero stats when no records exist")
        void emptyStats() {
            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals("agent-a", stats.getAgentName());
            assertNull(stats.getWorkflowName());
            assertEquals(0, stats.getTotalRuns());
            assertEquals(0, stats.getAvgDurationMs());
            assertEquals(0, stats.getP95DurationMs());
            assertEquals(0, stats.getMaxDurationMs());
            assertTrue(stats.getNodeStats().isEmpty());
        }

        @Test
        @DisplayName("computes correct average duration")
        void averageDuration() {
            saveCompletedRecord("agent-a", 100);
            saveCompletedRecord("agent-a", 200);
            saveCompletedRecord("agent-a", 300);

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals(3, stats.getTotalRuns());
            assertEquals(200.0, stats.getAvgDurationMs(), 0.01);
        }

        @Test
        @DisplayName("computes correct max duration")
        void maxDuration() {
            saveCompletedRecord("agent-a", 50);
            saveCompletedRecord("agent-a", 500);
            saveCompletedRecord("agent-a", 200);

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals(500, stats.getMaxDurationMs());
        }

        @Test
        @DisplayName("computes p95 correctly")
        void p95Duration() {
            // Add 20 records with durations 1..20
            for (int i = 1; i <= 20; i++) {
                saveCompletedRecord("agent-a", i * 10);
            }

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals(20, stats.getTotalRuns());
            // p95 index = ceil(20 * 0.95) - 1 = 18 (0-based), sorted durations[18] = 190
            assertEquals(190.0, stats.getP95DurationMs(), 0.01);
        }

        @Test
        @DisplayName("excludes non-completed records")
        void excludesNonCompleted() {
            saveCompletedRecord("agent-a", 100);
            // Save a RUNNING record
            ExecutionRecordDto running = ExecutionRecordDto.builder()
                    .id("run-" + System.nanoTime())
                    .agentName("agent-a")
                    .input("x")
                    .status(ExecutionStatus.RUNNING)
                    .startedAt(System.currentTimeMillis())
                    .durationMs(999)
                    .build();
            historyService.save(running);

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");
            assertEquals(1, stats.getTotalRuns());
            assertEquals(100.0, stats.getAvgDurationMs(), 0.01);
        }

        @Test
        @DisplayName("single record has p95 equal to its own duration")
        void singleRecordP95() {
            saveCompletedRecord("agent-a", 150);

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals(1, stats.getTotalRuns());
            assertEquals(150.0, stats.getAvgDurationMs(), 0.01);
            assertEquals(150.0, stats.getP95DurationMs(), 0.01);
            assertEquals(150, stats.getMaxDurationMs());
        }
    }

    // ── getWorkflowStats ────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowStats")
    class GetWorkflowStats {

        @Test
        @DisplayName("filters by workflow name")
        void filtersByWorkflow() {
            saveCompletedRecordWithWorkflow("agent-a", "analyze", 100);
            saveCompletedRecordWithWorkflow("agent-a", "summarize", 200);
            saveCompletedRecordWithWorkflow("agent-a", "analyze", 150);

            PerformanceStatsDto stats = analyticsService.getWorkflowStats("agent-a", "analyze");

            assertEquals(2, stats.getTotalRuns());
            assertEquals(125.0, stats.getAvgDurationMs(), 0.01);
        }

        @Test
        @DisplayName("returns empty stats for unknown workflow")
        void emptyForUnknown() {
            saveCompletedRecordWithWorkflow("agent-a", "analyze", 100);

            PerformanceStatsDto stats = analyticsService.getWorkflowStats("agent-a", "nonexistent");

            assertEquals(0, stats.getTotalRuns());
        }
    }

    // ── getAllStats ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllStats")
    class GetAllStats {

        @Test
        @DisplayName("returns stats grouped by agent")
        void groupedByAgent() {
            saveCompletedRecord("agent-a", 100);
            saveCompletedRecord("agent-a", 200);
            saveCompletedRecord("agent-b", 300);

            List<PerformanceStatsDto> allStats = analyticsService.getAllStats();

            assertEquals(2, allStats.size());
        }

        @Test
        @DisplayName("returns empty list when no completed records")
        void emptyWhenNone() {
            assertTrue(analyticsService.getAllStats().isEmpty());
        }
    }

    // ── Node-Level Stats ────────────────────────────────────────────────

    @Nested
    @DisplayName("node-level stats")
    class NodeLevelStats {

        @Test
        @DisplayName("aggregates node-level performance data")
        void aggregatesNodeStats() {
            saveCompletedRecordWithNodes("agent-a", Map.of(
                    "input", nodeStatus("input", "InputNode", 10),
                    "process", nodeStatus("process", "LlmNode", 80)
            ), 100);
            saveCompletedRecordWithNodes("agent-a", Map.of(
                    "input", nodeStatus("input", "InputNode", 5),
                    "process", nodeStatus("process", "LlmNode", 120)
            ), 130);

            PerformanceStatsDto stats = analyticsService.getAgentStats("agent-a");

            assertEquals(2, stats.getTotalRuns());
            assertFalse(stats.getNodeStats().isEmpty());

            // Find the "process" node stats
            NodePerfDto processStats = stats.getNodeStats().stream()
                    .filter(n -> "process".equals(n.getNodeId()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("LlmNode", processStats.getNodeType());
            assertEquals(100.0, processStats.getAvgMs(), 0.01);
            assertEquals(2, processStats.getExecutionCount());
            assertEquals(80, processStats.getMinMs());
            assertEquals(120, processStats.getMaxMs());
        }
    }

    // ── Window Size ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("window size limiting")
    class WindowSize {

        @Test
        @DisplayName("limits analysis to windowSize most recent records")
        void limitsToWindow() {
            PerformanceAnalyticsService smallWindow = new PerformanceAnalyticsService(historyService, 3);

            for (int i = 1; i <= 10; i++) {
                saveCompletedRecord("agent-a", i * 100);
            }

            PerformanceStatsDto stats = smallWindow.getAgentStats("agent-a");

            // Should only analyze up to 3 records (the most recent ones)
            assertEquals(3, stats.getTotalRuns());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void saveCompletedRecord(String agentName, long durationMs) {
        ExecutionRecordDto record = ExecutionRecordDto.builder()
                .id("exec-" + System.nanoTime())
                .agentName(agentName)
                .input("test")
                .status(ExecutionStatus.COMPLETED)
                .startedAt(System.currentTimeMillis())
                .completedAt(System.currentTimeMillis() + durationMs)
                .durationMs(durationMs)
                .build();
        historyService.save(record);
    }

    private void saveCompletedRecordWithWorkflow(String agentName, String workflowName, long durationMs) {
        ExecutionRecordDto record = ExecutionRecordDto.builder()
                .id("exec-" + System.nanoTime())
                .agentName(agentName)
                .workflowName(workflowName)
                .input("test")
                .status(ExecutionStatus.COMPLETED)
                .startedAt(System.currentTimeMillis())
                .completedAt(System.currentTimeMillis() + durationMs)
                .durationMs(durationMs)
                .build();
        historyService.save(record);
    }

    private void saveCompletedRecordWithNodes(String agentName,
                                               Map<String, NodeStatusDto> nodeStatuses,
                                               long durationMs) {
        ExecutionRecordDto record = ExecutionRecordDto.builder()
                .id("exec-" + System.nanoTime())
                .agentName(agentName)
                .input("test")
                .status(ExecutionStatus.COMPLETED)
                .startedAt(System.currentTimeMillis())
                .completedAt(System.currentTimeMillis() + durationMs)
                .durationMs(durationMs)
                .nodeStatuses(new ConcurrentHashMap<>(nodeStatuses))
                .build();
        historyService.save(record);
    }

    private NodeStatusDto nodeStatus(String nodeId, String nodeType, long durationMs) {
        return NodeStatusDto.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .status(NodeStatus.COMPLETED)
                .durationMs(durationMs)
                .build();
    }
}

