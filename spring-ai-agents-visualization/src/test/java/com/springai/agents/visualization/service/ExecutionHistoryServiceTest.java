package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.dto.NodeStatusDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExecutionHistoryService")
class ExecutionHistoryServiceTest {

    private ExecutionHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionHistoryService(5);
    }

    // ── Save & Retrieve ─────────────────────────────────────────────────

    @Nested
    @DisplayName("save and retrieve")
    class SaveAndRetrieve {

        @Test
        @DisplayName("save stores a record retrievable by ID")
        void saveAndGetById() {
            ExecutionRecordDto record = buildRecord("exec-1", "agent-a", "hello");
            service.save(record);

            ExecutionRecordDto found = service.getById("exec-1");
            assertNotNull(found);
            assertEquals("exec-1", found.getId());
            assertEquals("agent-a", found.getAgentName());
            assertEquals("hello", found.getInput());
        }

        @Test
        @DisplayName("getById returns null for unknown ID")
        void getByIdReturnsNull() {
            assertNull(service.getById("nonexistent"));
        }

        @Test
        @DisplayName("getAll returns records in most-recent-first order")
        void getAllReturnsRecentFirst() {
            service.save(buildRecord("exec-1", "agent-a", "first"));
            service.save(buildRecord("exec-2", "agent-a", "second"));
            service.save(buildRecord("exec-3", "agent-b", "third"));

            List<ExecutionRecordDto> all = service.getAll();
            assertEquals(3, all.size());
            assertEquals("exec-3", all.get(0).getId());
            assertEquals("exec-2", all.get(1).getId());
            assertEquals("exec-1", all.get(2).getId());
        }

        @Test
        @DisplayName("size reflects number of stored records")
        void sizeTracksRecords() {
            assertEquals(0, service.size());
            service.save(buildRecord("exec-1", "a", "x"));
            assertEquals(1, service.size());
            service.save(buildRecord("exec-2", "a", "y"));
            assertEquals(2, service.size());
        }
    }

    // ── Eviction ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eviction")
    class Eviction {

        @Test
        @DisplayName("evicts oldest entries when exceeding maxEntries")
        void evictsOldest() {
            for (int i = 1; i <= 7; i++) {
                service.save(buildRecord("exec-" + i, "agent", "input-" + i));
            }

            assertEquals(5, service.size());
            // Oldest (exec-1 and exec-2) should be evicted
            assertNull(service.getById("exec-1"));
            assertNull(service.getById("exec-2"));
            // Newest should remain
            assertNotNull(service.getById("exec-7"));
            assertNotNull(service.getById("exec-6"));
            assertNotNull(service.getById("exec-3"));
        }

        @Test
        @DisplayName("keeps exactly maxEntries records at the limit")
        void keepsExactlyMaxEntries() {
            for (int i = 1; i <= 5; i++) {
                service.save(buildRecord("exec-" + i, "agent", "input-" + i));
            }
            assertEquals(5, service.size());
            assertNotNull(service.getById("exec-1"));
            assertNotNull(service.getById("exec-5"));
        }
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("update modifies the record in the index")
        void updateModifiesRecord() {
            ExecutionRecordDto record = buildRecord("exec-1", "agent-a", "hello");
            record.setStatus(ExecutionStatus.RUNNING);
            service.save(record);

            record.setStatus(ExecutionStatus.COMPLETED);
            record.setOutput("result");
            record.setDurationMs(150);
            service.update(record);

            ExecutionRecordDto found = service.getById("exec-1");
            assertNotNull(found);
            assertEquals(ExecutionStatus.COMPLETED, found.getStatus());
            assertEquals("result", found.getOutput());
            assertEquals(150, found.getDurationMs());
        }
    }

    // ── Search ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search")
    class Search {

        @BeforeEach
        void populate() {
            ExecutionRecordDto r1 = buildRecord("exec-1", "agent-a", "hello world");
            r1.setStatus(ExecutionStatus.COMPLETED);
            r1.setOutput("result one");
            service.save(r1);

            ExecutionRecordDto r2 = buildRecord("exec-2", "agent-b", "foo bar");
            r2.setStatus(ExecutionStatus.RUNNING);
            service.save(r2);

            ExecutionRecordDto r3 = buildRecord("exec-3", "agent-a", "hello again");
            r3.setStatus(ExecutionStatus.COMPLETED);
            r3.setOutput("result three");
            service.save(r3);

            ExecutionRecordDto r4 = buildRecord("exec-4", "agent-a", "other input");
            r4.setStatus(ExecutionStatus.FAILED);
            r4.setOutput("Error: something broke");
            service.save(r4);
        }

        @Test
        @DisplayName("filter by agent name")
        void filterByAgent() {
            List<ExecutionRecordDto> results = service.search("agent-a", null, null, 0, 50);
            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(r -> "agent-a".equals(r.getAgentName())));
        }

        @Test
        @DisplayName("filter by status")
        void filterByStatus() {
            List<ExecutionRecordDto> results = service.search(null, ExecutionStatus.COMPLETED, null, 0, 50);
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> r.getStatus() == ExecutionStatus.COMPLETED));
        }

        @Test
        @DisplayName("filter by text query in input")
        void filterByQueryInInput() {
            List<ExecutionRecordDto> results = service.search(null, null, "hello", 0, 50);
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("filter by text query in output")
        void filterByQueryInOutput() {
            List<ExecutionRecordDto> results = service.search(null, null, "broke", 0, 50);
            assertEquals(1, results.size());
            assertEquals("exec-4", results.get(0).getId());
        }

        @Test
        @DisplayName("combined filters")
        void combinedFilters() {
            List<ExecutionRecordDto> results = service.search("agent-a", ExecutionStatus.COMPLETED, "hello", 0, 50);
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("pagination works")
        void pagination() {
            List<ExecutionRecordDto> page0 = service.search(null, null, null, 0, 2);
            assertEquals(2, page0.size());

            List<ExecutionRecordDto> page1 = service.search(null, null, null, 1, 2);
            assertEquals(2, page1.size());

            List<ExecutionRecordDto> page2 = service.search(null, null, null, 2, 2);
            assertEquals(0, page2.size());
        }

        @Test
        @DisplayName("empty/blank filters are treated as no filter")
        void blankFiltersIgnored() {
            List<ExecutionRecordDto> results = service.search("", null, "  ", 0, 50);
            assertEquals(4, results.size());
        }

        @Test
        @DisplayName("query is case-insensitive")
        void caseInsensitiveQuery() {
            List<ExecutionRecordDto> results = service.search(null, null, "HELLO", 0, 50);
            assertEquals(2, results.size());
        }
    }

    // ── getCompletedByAgent ─────────────────────────────────────────────

    @Nested
    @DisplayName("getCompletedByAgent")
    class CompletedByAgent {

        @Test
        @DisplayName("returns only completed records for the specified agent")
        void returnsOnlyCompleted() {
            ExecutionRecordDto r1 = buildRecord("e1", "agent-a", "x");
            r1.setStatus(ExecutionStatus.COMPLETED);
            service.save(r1);

            ExecutionRecordDto r2 = buildRecord("e2", "agent-a", "y");
            r2.setStatus(ExecutionStatus.RUNNING);
            service.save(r2);

            ExecutionRecordDto r3 = buildRecord("e3", "agent-b", "z");
            r3.setStatus(ExecutionStatus.COMPLETED);
            service.save(r3);

            List<ExecutionRecordDto> results = service.getCompletedByAgent("agent-a");
            assertEquals(1, results.size());
            assertEquals("e1", results.get(0).getId());
        }

        @Test
        @DisplayName("returns empty list for unknown agent")
        void emptyForUnknown() {
            assertTrue(service.getCompletedByAgent("nonexistent").isEmpty());
        }
    }

    // ── Clear ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearAll")
    class ClearAll {

        @Test
        @DisplayName("clears all records and index")
        void clearsEverything() {
            service.save(buildRecord("e1", "a", "x"));
            service.save(buildRecord("e2", "b", "y"));
            assertEquals(2, service.size());

            service.clearAll();

            assertEquals(0, service.size());
            assertNull(service.getById("e1"));
            assertNull(service.getById("e2"));
            assertTrue(service.getAll().isEmpty());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ExecutionRecordDto buildRecord(String id, String agentName, String input) {
        return ExecutionRecordDto.builder()
                .id(id)
                .agentName(agentName)
                .input(input)
                .status(ExecutionStatus.RUNNING)
                .startedAt(System.currentTimeMillis())
                .build();
    }
}
