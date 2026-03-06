package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Thread-safe in-memory store for execution records.
 * Uses a bounded ConcurrentLinkedDeque — oldest entries are evicted when maxEntries is exceeded.
 */
@Slf4j
public class ExecutionHistoryService {

    private final ConcurrentLinkedDeque<ExecutionRecordDto> records = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ExecutionRecordDto> recordIndex = new ConcurrentHashMap<>();
    private final int maxEntries;

    public ExecutionHistoryService(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Store a new execution record.
     */
    public void save(ExecutionRecordDto record) {
        records.addFirst(record);
        recordIndex.put(record.getId(), record);

        // Evict oldest if over limit
        while (records.size() > maxEntries) {
            ExecutionRecordDto evicted = records.pollLast();
            if (evicted != null) {
                recordIndex.remove(evicted.getId());
            }
        }
    }

    /**
     * Update an existing record in place (e.g. completion).
     */
    public void update(ExecutionRecordDto record) {
        recordIndex.put(record.getId(), record);
    }

    /**
     * Get a single record by ID.
     */
    public ExecutionRecordDto getById(String id) {
        return recordIndex.get(id);
    }

    /**
     * Get all records, most recent first.
     */
    public List<ExecutionRecordDto> getAll() {
        return new ArrayList<>(records);
    }

    /**
     * Search and filter execution records.
     */
    public List<ExecutionRecordDto> search(String agentName, ExecutionStatus status,
                                            String query, int page, int size) {
        Stream<ExecutionRecordDto> stream = records.stream();

        if (agentName != null && !agentName.isBlank()) {
            stream = stream.filter(r -> agentName.equals(r.getAgentName()));
        }
        if (status != null) {
            stream = stream.filter(r -> status == r.getStatus());
        }
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            stream = stream.filter(r ->
                    (r.getInput() != null && r.getInput().toLowerCase().contains(q)) ||
                    (r.getOutput() != null && r.getOutput().toLowerCase().contains(q)));
        }

        return stream.skip((long) page * size).limit(size).toList();
    }

    /**
     * Get completed records for a specific agent.
     */
    public List<ExecutionRecordDto> getCompletedByAgent(String agentName) {
        return records.stream()
                .filter(r -> agentName.equals(r.getAgentName()))
                .filter(r -> r.getStatus() == ExecutionStatus.COMPLETED)
                .toList();
    }

    /**
     * Clear all history.
     */
    public void clearAll() {
        records.clear();
        recordIndex.clear();
        log.info("Execution history cleared");
    }

    public int size() {
        return records.size();
    }
}

