package com.springai.agents.visualization.controller;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.agent.AgentRuntime;
import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.dto.ExecutionRequestDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.service.ExecutionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API for executing agents and browsing execution history.
 */
@Slf4j
@RestController
@RequestMapping("/agents-ui/api")
@RequiredArgsConstructor
public class ExecutionApiController {

    private final AgentRegistry agentRegistry;
    private final ExecutionHistoryService historyService;

    /**
     * Start an async execution of the given agent.
     * Returns immediately with a RUNNING status record.
     * WebSocket pushes live updates.
     */
    @PostMapping("/agents/{name}/execute")
    public ResponseEntity<ExecutionRecordDto> execute(@PathVariable String name,
                                                       @RequestBody ExecutionRequestDto request) {
        AgentRuntime runtime = agentRegistry.getSyncAgent(name);
        if (runtime == null) {
            return ResponseEntity.notFound().build();
        }

        String executionId = UUID.randomUUID().toString();
        ExecutionRecordDto record = ExecutionRecordDto.builder()
                .id(executionId)
                .agentName(name)
                .input(request.getInput())
                .status(ExecutionStatus.RUNNING)
                .startedAt(System.currentTimeMillis())
                .build();

        historyService.save(record);

        // Execute async — WebSocket events will push live updates
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> additionalContext = Map.of("executionId", executionId);
                String output = runtime.invoke(request.getInput(), additionalContext);
                record.setOutput(output);
                record.setStatus(ExecutionStatus.COMPLETED);
                record.setCompletedAt(System.currentTimeMillis());
                record.setDurationMs(record.getCompletedAt() - record.getStartedAt());
                historyService.update(record);
            } catch (Exception e) {
                log.error("Execution {} failed: {}", executionId, e.getMessage(), e);
                record.setOutput("Error: " + e.getMessage());
                record.setStatus(ExecutionStatus.FAILED);
                record.setCompletedAt(System.currentTimeMillis());
                record.setDurationMs(record.getCompletedAt() - record.getStartedAt());
                historyService.update(record);
            }
        });

        return ResponseEntity.ok(record);
    }

    @GetMapping("/executions")
    public List<ExecutionRecordDto> listExecutions(
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        ExecutionStatus st = null;
        if (status != null && !status.isBlank()) {
            try { st = ExecutionStatus.valueOf(status); } catch (Exception ignored) {}
        }
        return historyService.search(agent, st, q, page, size);
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<ExecutionRecordDto> getExecution(@PathVariable String id) {
        ExecutionRecordDto record = historyService.getById(id);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/executions")
    public ResponseEntity<Void> clearHistory() {
        historyService.clearAll();
        return ResponseEntity.noContent().build();
    }
}

