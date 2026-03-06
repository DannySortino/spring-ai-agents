package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.dto.NodePerfDto;
import com.springai.agents.visualization.dto.NodeStatusDto;
import com.springai.agents.visualization.dto.PerformanceStatsDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes performance analytics from completed execution records.
 */
@Slf4j
@RequiredArgsConstructor
public class PerformanceAnalyticsService {

    private final ExecutionHistoryService historyService;
    private final int windowSize;

    /**
     * Get stats for all agents.
     */
    public List<PerformanceStatsDto> getAllStats() {
        List<ExecutionRecordDto> all = historyService.getAll();
        Map<String, List<ExecutionRecordDto>> byAgent = all.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.COMPLETED)
                .collect(Collectors.groupingBy(ExecutionRecordDto::getAgentName));

        return byAgent.entrySet().stream()
                .map(e -> computeStats(e.getKey(), null, limitWindow(e.getValue())))
                .toList();
    }

    /**
     * Get stats for a specific agent.
     */
    public PerformanceStatsDto getAgentStats(String agentName) {
        List<ExecutionRecordDto> records = historyService.getCompletedByAgent(agentName);
        return computeStats(agentName, null, limitWindow(records));
    }

    /**
     * Get stats for a specific agent workflow.
     */
    public PerformanceStatsDto getWorkflowStats(String agentName, String workflowName) {
        List<ExecutionRecordDto> records = historyService.getCompletedByAgent(agentName).stream()
                .filter(r -> workflowName.equals(r.getWorkflowName()))
                .toList();
        return computeStats(agentName, workflowName, limitWindow(records));
    }

    private List<ExecutionRecordDto> limitWindow(List<ExecutionRecordDto> records) {
        if (records.size() <= windowSize) return records;
        return records.subList(0, windowSize);
    }

    private PerformanceStatsDto computeStats(String agentName, String workflowName,
                                              List<ExecutionRecordDto> records) {
        if (records.isEmpty()) {
            return PerformanceStatsDto.builder()
                    .agentName(agentName)
                    .workflowName(workflowName)
                    .totalRuns(0)
                    .avgDurationMs(0)
                    .p95DurationMs(0)
                    .maxDurationMs(0)
                    .nodeStats(List.of())
                    .build();
        }

        long[] durations = records.stream().mapToLong(ExecutionRecordDto::getDurationMs).toArray();
        Arrays.sort(durations);

        double avg = Arrays.stream(durations).average().orElse(0);
        int p95Index = Math.max(0, (int) Math.ceil(durations.length * 0.95) - 1);
        double p95 = durations[Math.min(p95Index, durations.length - 1)];
        long max = durations[durations.length - 1];

        // Aggregate node-level stats
        Map<String, List<NodeStatusDto>> nodeData = new HashMap<>();
        for (ExecutionRecordDto record : records) {
            for (NodeStatusDto ns : record.getNodeStatuses().values()) {
                nodeData.computeIfAbsent(ns.getNodeId(), k -> new ArrayList<>()).add(ns);
            }
        }

        List<NodePerfDto> nodeStats = nodeData.entrySet().stream()
                .map(e -> {
                    String nodeId = e.getKey();
                    List<NodeStatusDto> statuses = e.getValue();
                    long[] nodeDurations = statuses.stream().mapToLong(NodeStatusDto::getDurationMs).toArray();
                    Arrays.sort(nodeDurations);

                    double nodeAvg = Arrays.stream(nodeDurations).average().orElse(0);
                    int nodeP95Idx = Math.max(0, (int) Math.ceil(nodeDurations.length * 0.95) - 1);

                    return NodePerfDto.builder()
                            .nodeId(nodeId)
                            .nodeType(statuses.getFirst().getNodeType())
                            .avgMs(nodeAvg)
                            .p95Ms(nodeDurations[Math.min(nodeP95Idx, nodeDurations.length - 1)])
                            .maxMs(nodeDurations[nodeDurations.length - 1])
                            .minMs(nodeDurations[0])
                            .executionCount(nodeDurations.length)
                            .build();
                })
                .toList();

        return PerformanceStatsDto.builder()
                .agentName(agentName)
                .workflowName(workflowName)
                .totalRuns(records.size())
                .avgDurationMs(avg)
                .p95DurationMs(p95)
                .maxDurationMs(max)
                .nodeStats(nodeStats)
                .build();
    }
}

