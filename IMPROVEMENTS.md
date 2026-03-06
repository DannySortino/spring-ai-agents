# Spring AI Agents — Comprehensive Improvement Plan

> **Generated:** 2026-03-06  
> **Framework Version:** 1.0-SNAPSHOT  
> **Stack:** Spring Boot 3.2.5 / Java 21 / Spring AI 1.0.0

This document provides a detailed, actionable improvement plan for the Spring AI Agents framework. Each item includes motivation, implementation design, affected files, code sketches, and testing guidance so that a developer or LLM agent can execute each suggestion independently.

---

## Table of Contents

1. [Conditional Branching (ConditionNode & Predicate Edges)](#1-conditional-branching)
2. [Iterative Loops (LoopNode with Termination Conditions)](#2-iterative-loops)
3. [Sub-Workflow Composition (SubWorkflowNode)](#3-sub-workflow-composition)
4. [Human-in-the-Loop (ApprovalNode & Suspend/Resume)](#4-human-in-the-loop)
5. [Workflow State Persistence](#5-workflow-state-persistence)
6. [Structured Output / Schema Validation Node](#6-structured-output--schema-validation)
7. [VectorStore Node (RAG Integration)](#7-vectorstore-node-rag-integration)
8. [TransformNode (Code/Script Execution)](#8-transformnode-codescript-execution)
9. [Streaming Support (Token-level SSE)](#9-streaming-support)
10. [Result Caching](#10-result-caching)
11. [RetryConfig Integration into NodeConfig](#11-retryconfig-integration-into-nodeconfig)
12. [Timeout Enforcement on All Nodes](#12-timeout-enforcement-on-all-nodes)
13. [OpenTelemetry / Micrometer Observability](#13-opentelemetry--micrometer-observability)
14. [Cost & Token Tracking](#14-cost--token-tracking)
15. [Workflow Unit Testing DSL & Mock Mode](#15-workflow-unit-testing-dsl--mock-mode)
16. [Agent REST API Controller (Auto-Exposed HTTP Endpoints)](#16-agent-rest-api-controller)
17. [Conversation Memory & Multi-Turn Chat](#17-conversation-memory--multi-turn-chat)
18. [Hierarchical Agent Orchestration (Agent-calls-Agent)](#18-hierarchical-agent-orchestration)
19. [Guardrails & Input/Output Validation](#19-guardrails--inputoutput-validation)
20. [Workflow Versioning & Hot-Reload](#20-workflow-versioning--hot-reload)
21. [Parallel Thread Pool Improvements](#21-parallel-thread-pool-improvements)
22. [Spring AI ChatClient Migration](#22-spring-ai-chatclient-migration)
23. [Model Selection Per Node](#23-model-selection-per-node)
24. [Event Enrichment (Node Input/Output in Events)](#24-event-enrichment)
25. [Edge Metadata & Labels](#25-edge-metadata--labels)
26. [WorkflowBuilder Validation Warnings](#26-workflowbuilder-validation-warnings)
27. [Graceful Shutdown & Cancellation](#27-graceful-shutdown--cancellation)
28. [Multi-Modal Input Support (Images, Files)](#28-multi-modal-input-support)
29. [Plugin / Extension Point Registry](#29-plugin--extension-point-registry)
30. [Sample Agent Expansion & Cookbook](#30-sample-agent-expansion--cookbook)

---

## 1. Conditional Branching

### Motivation
Currently all edges are unconditional — every downstream node executes if its dependencies complete. Real-world workflows need if/else branching: "If the sentiment is negative, route to the escalation node; otherwise, route to the standard reply node."

### Design

**Option A — Predicate Edges (recommended):**  
Add an optional `Predicate<NodeContext>` to the `Edge` record. The `WorkflowExecutor` evaluates it before scheduling the downstream node. If the predicate returns `false`, the downstream node is skipped for that execution path.

**Option B — Dedicated ConditionNode:**  
A new node type whose executor evaluates a condition and returns a routing label. Downstream edges are tagged with matching labels.

Option A is simpler and more composable. Option B is more explicit for complex branching.

### Affected Files

| File | Change |
|------|--------|
| `workflow/Edge.java` | Add optional `Predicate<NodeContext> condition` field |
| `workflow/WorkflowBuilder.java` | Add `edge(Node, Node, Predicate)` overload |
| `workflow/WorkflowExecutor.java` | Evaluate predicate before scheduling downstream nodes |
| `workflow/ReactiveWorkflowExecutor.java` | Same predicate evaluation in reactive path |
| `node/ConditionNode.java` | *(Option B only)* New node type |
| `executor/ConditionExecutor.java` | *(Option B only)* New executor |

### Implementation — Option A

**Step 1: Update `Edge.java`**
```java
package com.springai.agents.workflow;

import com.springai.agents.executor.NodeContext;
import lombok.NonNull;
import java.util.function.Predicate;

public record Edge(
    @NonNull String from,
    @NonNull String to,
    Predicate<NodeContext> condition  // null = unconditional (always execute)
) {
    // Backward-compatible constructor
    public Edge(@NonNull String from, @NonNull String to) {
        this(from, to, null);
    }

    public boolean isConditional() {
        return condition != null;
    }

    public static EdgeBuilder from(String from) {
        return new EdgeBuilder(from);
    }

    public record EdgeBuilder(String from) {
        public Edge to(String to) {
            return new Edge(from, to, null);
        }
        public Edge to(String to, Predicate<NodeContext> condition) {
            return new Edge(from, to, condition);
        }
    }
}
```

**Step 2: Add builder overloads in `WorkflowBuilder.java`**
```java
public WorkflowBuilder edge(Node from, Node to, Predicate<NodeContext> condition) {
    edges.add(new Edge(from.getId(), to.getId(), condition));
    return this;
}
```

**Step 3: Update `WorkflowExecutor.executeNode()` — skip nodes whose incoming conditional edges all evaluate to `false`**

In `WorkflowExecutor.execute()`, before executing a node, check all incoming edges. If ANY incoming edge has a condition, evaluate it. If ALL conditional incoming edges evaluate to `false`, skip the node entirely.

```java
private boolean shouldExecute(Workflow workflow, String nodeId,
                               Map<String, Object> nodeResults, Map<String, Object> context) {
    // Find all incoming edges to this node
    List<Edge> incomingEdges = workflow.getEdges().stream()
            .filter(e -> e.to().equals(nodeId))
            .toList();

    if (incomingEdges.isEmpty()) return true; // root nodes always execute

    // Check conditional edges
    List<Edge> conditionalEdges = incomingEdges.stream()
            .filter(Edge::isConditional)
            .toList();

    if (conditionalEdges.isEmpty()) return true; // no conditions = always execute

    // At least one conditional edge must pass
    return conditionalEdges.stream().anyMatch(edge -> {
        Map<String, Object> depResults = new HashMap<>();
        depResults.put(edge.from(), nodeResults.get(edge.from()));
        NodeContext ctx = NodeContext.builder()
                .resolvedInput(String.valueOf(nodeResults.getOrDefault(edge.from(), "")))
                .dependencyResults(depResults)
                .executionContext(context)
                .build();
        return edge.condition().test(ctx);
    });
}
```

**Step 4: Integrate into the level-loop in `execute()`**

Before `executeNode(...)`, call `shouldExecute(...)`. If `false`, skip and store a sentinel or `null`.

### Usage Example
```java
var input = InputNode.builder().id("input").build();
var classify = LlmNode.builder().id("classify")
        .promptTemplate("Classify the sentiment of: {input}. Reply POSITIVE or NEGATIVE only.")
        .build();
var handlePositive = LlmNode.builder().id("positive")
        .promptTemplate("Write a thank-you response to: {input}").build();
var handleNegative = LlmNode.builder().id("negative")
        .promptTemplate("Write an empathetic escalation response to: {input}").build();
var output = OutputNode.builder().id("output").build();

builder.nodes(input, classify, handlePositive, handleNegative, output)
    .edge(input, classify)
    .edge(classify, handlePositive, ctx ->
        ctx.getDependencyResult("classify", String.class).contains("POSITIVE"))
    .edge(classify, handleNegative, ctx ->
        ctx.getDependencyResult("classify", String.class).contains("NEGATIVE"))
    .edge(handlePositive, output)
    .edge(handleNegative, output)
    .build();
```

### Tests to Write
- `ConditionalEdgeTest`: Verify only the matching branch executes.
- `AllConditionsFalseTest`: Verify node is skipped when no conditional edge passes.
- `MixedConditionalUnconditionalTest`: Verify unconditional edges always execute.
- `ConditionalEdgeWithErrorStrategyTest`: Verify error strategy applies when condition evaluation throws.

---

## 2. Iterative Loops

### Motivation
Many agent patterns require iterative refinement: "Generate a draft → evaluate quality → if not good enough, regenerate." Current DAG validation forbids cycles.

### Design
Introduce a `LoopNode` that wraps a sub-section of the graph and re-executes it until a termination condition is met or a max iteration count is reached. This avoids modifying the core cycle-detection logic.

### Affected Files

| File | Change |
|------|--------|
| `node/LoopNode.java` | **New** — holds `maxIterations`, `terminationCondition` |
| `executor/LoopExecutor.java` | **New** — re-executes referenced nodes |
| `workflow/WorkflowExecutor.java` | Recognize `LoopNode` during level execution |
| `autoconfigure/AgentsAutoConfiguration.java` | Register `LoopExecutor` bean |

### Implementation

**`LoopNode.java`**
```java
package com.springai.agents.node;

import com.springai.agents.executor.NodeContext;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.function.BiPredicate;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class LoopNode extends Node {
    @NonNull String id;

    /** IDs of nodes that form the loop body (must be a valid sub-DAG within the workflow). */
    @NonNull
    java.util.List<String> bodyNodeIds;

    /** Maximum iterations to prevent infinite loops. Default: 5. */
    @Builder.Default
    int maxIterations = 5;

    /**
     * Termination predicate: (iterationCount, lastResult) → shouldStop.
     * If null, always loops maxIterations times.
     */
    @Builder.Default
    transient BiPredicate<Integer, Object> terminationCondition = null;
}
```

**`LoopExecutor.java`** — Execute the body node IDs in topological order within the loop, passing the last iteration's output as input to the next iteration. Return the final iteration's result.

### Usage Example
```java
var refine = LlmNode.builder().id("refine")
        .promptTemplate("Improve this text: {draft}").build();
var evaluate = LlmNode.builder().id("evaluate")
        .promptTemplate("Rate quality 1-10: {refine}").build();

var loop = LoopNode.builder()
        .id("refinement-loop")
        .bodyNodeIds(List.of("refine", "evaluate"))
        .maxIterations(3)
        .terminationCondition((iteration, result) -> {
            String rating = String.valueOf(result);
            return rating.contains("9") || rating.contains("10");
        })
        .build();
```

### Tests
- `LoopExecutorTest`: Verify loop runs until termination condition is met.
- `MaxIterationsTest`: Verify loop stops at `maxIterations` even if condition is never met.
- `SingleIterationTest`: Verify loop with immediate termination returns first result.

---

## 3. Sub-Workflow Composition

### Motivation
As agents grow complex, you want to reuse entire workflows as building blocks inside other workflows. Currently there's no way to nest a workflow inside another.

### Affected Files

| File | Change |
|------|--------|
| `node/SubWorkflowNode.java` | **New** — references a `Workflow` by name or direct reference |
| `executor/SubWorkflowExecutor.java` | **New** — delegates to `WorkflowExecutor` |
| `autoconfigure/AgentsAutoConfiguration.java` | Register bean |

### Implementation

**`SubWorkflowNode.java`**
```java
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class SubWorkflowNode extends Node {
    @NonNull String id;
    
    /** Direct workflow reference (preferred). */
    Workflow workflow;
    
    /** Alternatively, reference by agent name + workflow name (resolved at runtime). */
    String agentName;
    String workflowName;
}
```

**`SubWorkflowExecutor.java`**
```java
@RequiredArgsConstructor
public class SubWorkflowExecutor implements NodeExecutor<SubWorkflowNode> {
    private final WorkflowExecutor workflowExecutor;
    private final AgentRegistry agentRegistry; // for name-based resolution

    @Override
    public Object execute(SubWorkflowNode node, NodeContext context) {
        Workflow subWorkflow = resolveWorkflow(node);
        WorkflowResult result = workflowExecutor.execute(subWorkflow, context.getResolvedInput(),
                context.getExecutionContext());
        return result.getOutput();
    }
    
    private Workflow resolveWorkflow(SubWorkflowNode node) {
        if (node.getWorkflow() != null) return node.getWorkflow();
        // Resolve from registry by agent name + workflow name
        AgentRuntime runtime = agentRegistry.getSyncAgent(node.getAgentName());
        return runtime.getWorkflows().stream()
                .filter(w -> w.getName().equals(node.getWorkflowName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Workflow '%s' not found in agent '%s'".formatted(node.getWorkflowName(), node.getAgentName())));
    }

    @Override
    public Class<SubWorkflowNode> getNodeType() { return SubWorkflowNode.class; }
}
```

### Usage Example
```java
// Reuse an existing workflow inline
Workflow sharedPreprocess = WorkflowBuilder.create()
        .name("preprocess").description("Cleans and normalizes input")
        .nodes(input, clean, normalize, output)
        .edge(input, clean).edge(clean, normalize).edge(normalize, output)
        .build();

// Use it as a node inside another workflow
var preprocess = SubWorkflowNode.builder()
        .id("preprocess")
        .workflow(sharedPreprocess)
        .build();
```

### Tests
- Verify sub-workflow executes and its output is available to downstream nodes.
- Verify nested sub-workflows (sub-workflow within a sub-workflow) work correctly.
- Verify error propagation from sub-workflow to parent.

---

## 4. Human-in-the-Loop

### Motivation
Production AI workflows often need human approval before taking irreversible actions (sending emails, making purchases, modifying databases). The workflow must pause, persist state, and resume when approved.

### Design
Introduce an `ApprovalNode` whose executor returns a `SUSPENDED` status. The `WorkflowExecutor` detects this, persists current state (requires persistence — see item 5), and returns a `WorkflowResult` with status `SUSPENDED` and a `resumeToken`. A REST endpoint or programmatic API accepts the token + approval decision to resume.

### Affected Files

| File | Change |
|------|--------|
| `node/ApprovalNode.java` | **New** — `id`, `approvalMessage`, `timeout` |
| `executor/ApprovalExecutor.java` | **New** — throws `WorkflowSuspendedException` |
| `workflow/WorkflowResult.java` | Add `status` enum field (`COMPLETED`, `SUSPENDED`, `FAILED`) |
| `workflow/WorkflowExecutor.java` | Catch `WorkflowSuspendedException`, persist state |
| `workflow/WorkflowResumeService.java` | **New** — resumes from saved state |
| `workflow/WorkflowState.java` | **New** — serializable execution snapshot |

### Implementation Sketch

**`ApprovalNode.java`**
```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class ApprovalNode extends Node {
    @NonNull String id;
    
    /** Message shown to the human approver. Supports {nodeId} interpolation. */
    @NonNull String approvalMessage;
    
    /** Optional timeout — auto-reject after this duration. */
    Duration timeout;
}
```

**`WorkflowState.java`** — Serializable snapshot:
```java
@Value @Builder
public class WorkflowState implements Serializable {
    String workflowName;
    String input;
    String suspendedAtNodeId;
    Map<String, Object> nodeResults;       // must be serializable
    Map<String, Object> executionContext;
    long startTime;
    String resumeToken;                    // UUID
}
```

**`WorkflowResult.java`** — Add status:
```java
@Value @Builder
public class WorkflowResult {
    String output;
    Map<String, Object> nodeResults;
    long durationMs;
    
    @Builder.Default
    ExecutionStatus status = ExecutionStatus.COMPLETED;
    
    /** Non-null when status is SUSPENDED. */
    String resumeToken;
    
    /** Message for the human approver when SUSPENDED. */
    String approvalMessage;
    
    // ...existing getNodeResult()
}

public enum ExecutionStatus {
    COMPLETED, SUSPENDED, FAILED
}
```

### Resume Flow
1. Client receives `WorkflowResult` with `status=SUSPENDED` and `resumeToken="abc-123"`.
2. Client calls `workflowResumeService.resume("abc-123", ApprovalDecision.APPROVED)`.
3. Service loads `WorkflowState` from persistence, restores `nodeResults` and `executionContext`.
4. Execution continues from the node after the `ApprovalNode`.

### Tests
- Verify workflow suspends at `ApprovalNode` and returns `SUSPENDED` status.
- Verify resume with `APPROVED` continues execution.
- Verify resume with `REJECTED` returns early with rejection message.
- Verify timeout auto-rejects after configured duration.

---

## 5. Workflow State Persistence

### Motivation
Required by Human-in-the-Loop (item 4), long-running workflows, and crash recovery. Currently all state lives in memory within `ConcurrentHashMap`.

### Design
Define a `WorkflowStateRepository` SPI with pluggable implementations.

### Affected Files

| File | Change |
|------|--------|
| `persistence/WorkflowStateRepository.java` | **New** — SPI interface |
| `persistence/InMemoryWorkflowStateRepository.java` | **New** — default in-memory impl |
| `persistence/JdbcWorkflowStateRepository.java` | **New** — JDBC-backed impl |
| `autoconfigure/AgentsAutoConfiguration.java` | Register repository bean |

### Interface
```java
package com.springai.agents.persistence;

import java.util.Optional;

public interface WorkflowStateRepository {
    void save(WorkflowState state);
    Optional<WorkflowState> findByResumeToken(String resumeToken);
    void deleteByResumeToken(String resumeToken);
    List<WorkflowState> findSuspended();
    List<WorkflowState> findByAgentName(String agentName);
}
```

### In-Memory Implementation
```java
public class InMemoryWorkflowStateRepository implements WorkflowStateRepository {
    private final Map<String, WorkflowState> store = new ConcurrentHashMap<>();
    
    @Override
    public void save(WorkflowState state) {
        store.put(state.getResumeToken(), state);
    }
    // ...
}
```

### JDBC Implementation
Schema:
```sql
CREATE TABLE workflow_state (
    resume_token  VARCHAR(36) PRIMARY KEY,
    agent_name    VARCHAR(255) NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    input_text    TEXT,
    suspended_at  VARCHAR(255),
    node_results  TEXT,         -- JSON serialized
    context       TEXT,         -- JSON serialized
    status        VARCHAR(20),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Auto-Configuration
```java
@Bean
@ConditionalOnMissingBean
public WorkflowStateRepository workflowStateRepository() {
    return new InMemoryWorkflowStateRepository();
}

@Bean
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = "spring.ai.agents.persistence.type", havingValue = "jdbc")
public WorkflowStateRepository jdbcWorkflowStateRepository(JdbcClient jdbcClient, ObjectMapper mapper) {
    return new JdbcWorkflowStateRepository(jdbcClient, mapper);
}
```

### Configuration
```yaml
spring:
  ai:
    agents:
      persistence:
        type: in-memory   # or "jdbc"
```

---

## 6. Structured Output / Schema Validation

### Motivation
LLM outputs are unstructured strings. Many workflows need structured JSON output conforming to a schema — e.g., extracting entities, classification results, or API payloads. Spring AI 1.0 supports structured output via `BeanOutputConverter`.

### Design
Add optional `outputSchema` / `responseType` to `LlmNode` so the executor can request structured output from the LLM and validate it automatically.

### Affected Files

| File | Change |
|------|--------|
| `node/LlmNode.java` | Add `Class<?> responseType` and `String outputSchema` fields |
| `executor/LlmExecutor.java` | Use `BeanOutputConverter` when `responseType` is set |

### Implementation

**`LlmNode.java` — Add fields:**
```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class LlmNode extends Node {
    @NonNull String id;
    @NonNull String promptTemplate;
    String systemPrompt;
    
    /**
     * Optional: expected response Java type. When set, the executor uses
     * Spring AI's BeanOutputConverter to parse the LLM response into this type.
     * The node result will be an instance of this class instead of a String.
     */
    Class<?> responseType;
    
    /**
     * Optional: JSON Schema string for the expected output format.
     * Appended to the prompt as format instructions.
     * Ignored if responseType is set (responseType takes priority).
     */
    String outputSchema;
}
```

**`LlmExecutor.java` — Structured output path:**
```java
@Override
public Object execute(LlmNode node, NodeContext context) {
    String processedPrompt = buildPrompt(node, context);
    
    if (node.getResponseType() != null) {
        // Use Spring AI structured output
        BeanOutputConverter<?> converter = new BeanOutputConverter<>(node.getResponseType());
        String formatInstructions = converter.getFormat();
        String fullPrompt = processedPrompt + "\n\n" + formatInstructions;
        
        String rawResponse = chatModel.call(new Prompt(fullPrompt))
                .getResult().getOutput().getText();
        return converter.convert(rawResponse);
    }
    
    if (node.getOutputSchema() != null) {
        processedPrompt += "\n\nRespond using this JSON format:\n" + node.getOutputSchema();
    }
    
    return chatModel.call(new Prompt(processedPrompt)).getResult().getOutput().getText();
}
```

### Usage Example
```java
public record SentimentResult(String sentiment, double confidence, List<String> keywords) {}

var classify = LlmNode.builder()
        .id("classify")
        .promptTemplate("Classify the sentiment of: {input}")
        .responseType(SentimentResult.class)
        .build();

// Downstream node gets typed access:
var output = OutputNode.builder().id("output")
        .outputHandler(ctx -> {
            SentimentResult result = ctx.getDependencyResult("classify", SentimentResult.class);
            return "Sentiment: " + result.sentiment() + " (" + result.confidence() + ")";
        })
        .build();
```

### Tests
- Verify `responseType` produces a typed POJO result, not a raw String.
- Verify `outputSchema` appends format instructions to the prompt.
- Verify fallback to plain string when neither is set (backward compatible).
- Verify `ClassCastException` is thrown with a clear message on parse failure.

---

## 7. VectorStore Node (RAG Integration)

### Motivation
Retrieval-Augmented Generation is the most common AI pattern. A first-class node for similarity search eliminates boilerplate and makes RAG workflows declarative.

### Affected Files

| File | Change |
|------|--------|
| `node/VectorStoreNode.java` | **New** |
| `executor/VectorStoreExecutor.java` | **New** |
| `autoconfigure/AgentsAutoConfiguration.java` | Register executor conditionally on `VectorStore` bean |
| `starter/pom.xml` | Add optional `spring-ai-vector-store` dependency |

### Implementation

**`VectorStoreNode.java`**
```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class VectorStoreNode extends Node {
    @NonNull String id;
    
    /** Query template for similarity search. Supports {nodeId} and {input} placeholders. */
    @NonNull String queryTemplate;
    
    /** Number of similar documents to retrieve. Default: 5. */
    @Builder.Default int topK = 5;
    
    /** Optional metadata filter expression (Spring AI Filter.Expression syntax). */
    String filterExpression;
    
    /** Similarity threshold (0.0 to 1.0). Default: 0.0 (no threshold). */
    @Builder.Default double similarityThreshold = 0.0;
}
```

**`VectorStoreExecutor.java`**
```java
@RequiredArgsConstructor
public class VectorStoreExecutor implements NodeExecutor<VectorStoreNode> {
    private final VectorStore vectorStore;

    @Override
    public Object execute(VectorStoreNode node, NodeContext context) {
        String query = PromptInterpolator.interpolate(
                node.getQueryTemplate(), context.getDependencyResults(), context.getExecutionContext());
        
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(node.getTopK())
                .similarityThreshold(node.getSimilarityThreshold())
                .build();
        
        List<Document> results = vectorStore.similaritySearch(request);
        
        // Return formatted context string for downstream LLM nodes
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public Class<VectorStoreNode> getNodeType() { return VectorStoreNode.class; }
}
```

### Usage Example — RAG Workflow
```java
var input = InputNode.builder().id("input").build();
var retrieve = VectorStoreNode.builder()
        .id("retrieve")
        .queryTemplate("{input}")
        .topK(5)
        .similarityThreshold(0.7)
        .build();
var answer = LlmNode.builder()
        .id("answer")
        .promptTemplate("""
            Answer the question based on the following context:
            
            Context:
            {retrieve}
            
            Question: {input}
            """)
        .systemPrompt("Answer only based on the provided context. Say 'I don't know' if the context doesn't contain the answer.")
        .build();
var output = OutputNode.builder().id("output").build();

builder.nodes(input, retrieve, answer, output)
    .edge(input, retrieve)
    .edge(input, answer)     // answer also needs raw input
    .edge(retrieve, answer)  // answer needs retrieved context
    .edge(answer, output)
    .build();
```

---

## 8. TransformNode (Code/Script Execution)

### Motivation
Not everything needs an LLM. Data transformation, formatting, filtering, calculations — these are better done with deterministic code. Currently users must create a custom `Node` + `NodeExecutor` pair for every transformation. A `TransformNode` with an inline `Function` would cover 90% of cases.

### Affected Files

| File | Change |
|------|--------|
| `node/TransformNode.java` | **New** |
| `executor/TransformExecutor.java` | **New** |
| `autoconfigure/AgentsAutoConfiguration.java` | Register executor |

### Implementation

**`TransformNode.java`**
```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class TransformNode extends Node {
    @NonNull String id;
    
    /**
     * Transform function that receives the NodeContext and returns any Object.
     * This is the primary way to do deterministic data processing in a workflow.
     */
    @NonNull
    transient Function<NodeContext, Object> transformer;
    
    /** Optional human-readable description of what this transform does. */
    String description;
}
```

**`TransformExecutor.java`**
```java
@Slf4j
public class TransformExecutor implements NodeExecutor<TransformNode> {
    @Override
    public Object execute(TransformNode node, NodeContext context) {
        log.debug("TransformNode '{}': executing transform function", node.getId());
        return node.getTransformer().apply(context);
    }

    @Override
    public Class<TransformNode> getNodeType() { return TransformNode.class; }
}
```

### Usage Example
```java
var transform = TransformNode.builder()
        .id("parse-json")
        .description("Parses the API response JSON into a domain object")
        .transformer(ctx -> {
            String json = ctx.getDependencyResult("api-call", String.class);
            return objectMapper.readValue(json, MyDataModel.class);
        })
        .build();

var format = TransformNode.builder()
        .id("format-output")
        .description("Formats the final report as markdown")
        .transformer(ctx -> {
            MyDataModel data = ctx.getDependencyResult("parse-json", MyDataModel.class);
            return "## Report\n\n- Name: %s\n- Score: %d".formatted(data.name(), data.score());
        })
        .build();
```

### Tests
- Verify transform function receives correct dependency results.
- Verify typed return value is stored and accessible downstream.
- Verify error in transform function is handled by `ErrorStrategy`.

---

## 9. Streaming Support

### Motivation
LLM responses can take seconds. Streaming tokens as they arrive dramatically reduces perceived latency (Time to First Token). Critical for user-facing chat applications.

### Design
Add a `StreamingLlmNode` or a `streaming` flag on `LlmNode`. The executor uses `ChatModel.stream()` and returns a `Flux<String>`. The `OutputNode` must also support `Flux<String>` pass-through. This only works in reactive mode.

### Affected Files

| File | Change |
|------|--------|
| `node/LlmNode.java` | Add `boolean streaming` field |
| `executor/LlmExecutor.java` | Use `chatModel.stream()` when `streaming=true` |
| `executor/ReactiveNodeExecutor.java` | Add `Flux<String> executeStreaming()` method |
| `workflow/ReactiveWorkflowExecutor.java` | Handle streaming nodes differently |

### Implementation Sketch

**`LlmNode.java`**:
```java
/** Whether to stream LLM tokens. Only effective in reactive mode. Default: false. */
@Builder.Default
boolean streaming = false;
```

**`LlmExecutor.java`** — Add streaming support:
```java
public Flux<String> executeStreaming(LlmNode node, NodeContext context) {
    String processedPrompt = buildPrompt(node, context);
    return chatModel.stream(new Prompt(processedPrompt))
            .map(response -> response.getResult().getOutput().getText())
            .filter(Objects::nonNull);
}
```

### Notes
- Streaming is inherently incompatible with downstream nodes that need the complete output. The framework should buffer the full response for dependency resolution but stream tokens to the client simultaneously.
- Consider a `StreamingWorkflowResult` that holds both a `Flux<String>` for the output stream and a `Mono<WorkflowResult>` for the final result.

---

## 10. Result Caching

### Motivation
Identical inputs to deterministic nodes (REST calls, context injection, even LLM calls with temperature=0) produce identical outputs. Caching saves money (LLM tokens) and time.

### Design
Add `cacheable` flag and optional `cacheKey` strategy to `NodeConfig`. The `WorkflowExecutor` checks a cache before executing the node.

### Affected Files

| File | Change |
|------|--------|
| `node/NodeConfig.java` | Add `boolean cacheable`, `Duration cacheTtl` |
| `workflow/WorkflowExecutor.java` | Check/populate cache around `executeNode()` |
| `cache/NodeResultCache.java` | **New** — cache abstraction |
| `cache/InMemoryNodeResultCache.java` | **New** — default Caffeine/ConcurrentHashMap impl |
| `autoconfigure/AgentsAutoConfiguration.java` | Register cache bean |

### Implementation

**`NodeConfig.java`** — Add cache fields:
```java
@Value @Builder
public class NodeConfig {
    @Builder.Default ErrorStrategy errorStrategy = ErrorStrategy.FAIL_FAST;
    @Builder.Default Object defaultValue = "";
    
    /** Whether this node's results should be cached. Default: false. */
    @Builder.Default boolean cacheable = false;
    
    /** Cache TTL. Default: 1 hour. Only used when cacheable=true. */
    @Builder.Default Duration cacheTtl = Duration.ofHours(1);
}
```

**`NodeResultCache.java`**:
```java
public interface NodeResultCache {
    Optional<Object> get(String cacheKey);
    void put(String cacheKey, Object value, Duration ttl);
    void evict(String cacheKey);
    void clear();
}
```

**Cache key generation** — Hash of `(nodeId, nodeConfig, resolvedInput)`:
```java
private String buildCacheKey(String nodeId, String resolvedInput, Map<String, Object> depResults) {
    String content = nodeId + ":" + resolvedInput + ":" + depResults.hashCode();
    return DigestUtils.sha256Hex(content);
}
```

**Integration in `WorkflowExecutor.executeNode()`**:
```java
NodeConfig config = node.getConfig();
if (config != null && config.isCacheable()) {
    String cacheKey = buildCacheKey(nodeId, resolvedInput, depResults);
    Optional<Object> cached = cache.get(cacheKey);
    if (cached.isPresent()) {
        log.debug("Node '{}': cache HIT", nodeId);
        nodeResults.put(nodeId, cached.get());
        return;
    }
    // ... execute normally ...
    cache.put(cacheKey, result, config.getCacheTtl());
}
```

### Configuration
```yaml
spring:
  ai:
    agents:
      cache:
        enabled: true
        max-size: 1000
        default-ttl: PT1H
```

---

## 11. RetryConfig Integration into NodeConfig

### Motivation
`RetryService` and `RetryConfig` exist but are NOT integrated into the node execution pipeline. Users must call `retryService.executeWithRetry()` manually in custom executors. Retry should be declarative per-node.

### Affected Files

| File | Change |
|------|--------|
| `node/NodeConfig.java` | Add `RetryConfig retryConfig` field |
| `workflow/WorkflowExecutor.java` | Wrap `executeNode` call in `retryService.executeWithRetry()` |
| `workflow/ReactiveWorkflowExecutor.java` | Same for reactive path |

### Implementation

**`NodeConfig.java`**:
```java
@Value @Builder
public class NodeConfig {
    @Builder.Default ErrorStrategy errorStrategy = ErrorStrategy.FAIL_FAST;
    @Builder.Default Object defaultValue = "";
    @Builder.Default boolean cacheable = false;
    @Builder.Default Duration cacheTtl = Duration.ofHours(1);
    
    /** Per-node retry configuration. Null = no retries. */
    RetryConfig retryConfig;
}
```

**`WorkflowExecutor.java`** — In `executeNode()`, wrap the executor call:
```java
try {
    Object result;
    RetryConfig retryConfig = (config != null) ? config.getRetryConfig() : null;
    if (retryConfig != null && retryConfig.isEnabled()) {
        result = retryService.executeWithRetry(
                () -> executorRegistry.execute(node, nodeContext),
                retryConfig,
                "node:" + nodeId
        );
    } else {
        result = executorRegistry.execute(node, nodeContext);
    }
    nodeResults.put(nodeId, result);
    // ...
}
```

This requires the `WorkflowExecutor` to receive a `RetryService` in its constructor. Update `AgentsAutoConfiguration` accordingly.

### Usage Example
```java
var fetchData = RestNode.builder()
        .id("fetch-data")
        .url("https://api.example.com/data")
        .method(HttpMethod.GET)
        .config(NodeConfig.builder()
                .retryConfig(RetryConfig.builder()
                        .strategy(RetryStrategy.EXPONENTIAL)
                        .maxAttempts(3)
                        .initialDelayMs(500)
                        .build())
                .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                .defaultValue("{}")
                .build())
        .build();
```

### Tests
- Verify retry fires on transient failure and succeeds on 2nd attempt.
- Verify `ErrorStrategy` kicks in after all retry attempts are exhausted.
- Verify no retries when `retryConfig` is null.

---

## 12. Timeout Enforcement on All Nodes

### Motivation
`RestNode` has a `timeout` field, but `LlmNode` and `ToolNode` do not. A slow LLM call can hang the entire workflow indefinitely. All nodes should support configurable timeouts.

### Affected Files

| File | Change |
|------|--------|
| `node/NodeConfig.java` | Add `Duration timeout` field |
| `workflow/WorkflowExecutor.java` | Enforce timeout on each `executeNode()` call |

### Implementation

**`NodeConfig.java`**:
```java
/** Maximum execution time for this node. Null = no timeout (default). */
Duration timeout;
```

**`WorkflowExecutor.java`** — Enforce via `CompletableFuture.orTimeout()`:
```java
if (config != null && config.getTimeout() != null) {
    result = CompletableFuture.supplyAsync(() -> executorRegistry.execute(node, nodeContext), threadPool)
            .orTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .join();
} else {
    result = executorRegistry.execute(node, nodeContext);
}
```

### Usage Example
```java
var slowLlm = LlmNode.builder()
        .id("slow-analysis")
        .promptTemplate("Write a 5000-word essay on: {input}")
        .config(NodeConfig.builder()
                .timeout(Duration.ofSeconds(60))
                .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                .defaultValue("Analysis timed out")
                .build())
        .build();
```

---

## 13. OpenTelemetry / Micrometer Observability

### Motivation
Production systems need distributed tracing, metrics, and dashboards. The framework fires Spring events, but doesn't produce standard observability signals.

### Design
Use Micrometer `ObservationRegistry` to create spans for workflows and nodes. Export via OpenTelemetry.

### Affected Files

| File | Change |
|------|--------|
| `observability/WorkflowObservation.java` | **New** — Observation conventions |
| `observability/ObservableWorkflowExecutor.java` | **New** — decorator wrapping `WorkflowExecutor` |
| `autoconfigure/AgentsAutoConfiguration.java` | Conditionally wrap executor |
| `starter/pom.xml` | Add optional `micrometer-observation` dependency |

### Implementation Sketch

**`ObservableWorkflowExecutor.java`**:
```java
@RequiredArgsConstructor
public class ObservableWorkflowExecutor {
    private final WorkflowExecutor delegate;
    private final ObservationRegistry observationRegistry;

    public WorkflowResult execute(Workflow workflow, String input, Map<String, Object> context) {
        Observation observation = Observation.createNotStarted("spring.ai.agents.workflow", observationRegistry)
                .lowCardinalityKeyValue("workflow.name", workflow.getName())
                .lowCardinalityKeyValue("node.count", String.valueOf(workflow.size()));
        
        return observation.observe(() -> delegate.execute(workflow, input, context));
    }
}
```

### Metrics to Export
| Metric | Type | Description |
|--------|------|-------------|
| `spring.ai.agents.workflow.duration` | Timer | Total workflow execution time |
| `spring.ai.agents.workflow.active` | Gauge | Currently executing workflows |
| `spring.ai.agents.node.duration` | Timer | Per-node execution time (tagged by node type) |
| `spring.ai.agents.node.errors` | Counter | Node execution errors (tagged by error strategy) |
| `spring.ai.agents.llm.tokens` | Counter | Token usage (prompt + completion) |
| `spring.ai.agents.cache.hits` | Counter | Node result cache hits |
| `spring.ai.agents.cache.misses` | Counter | Node result cache misses |

### Configuration
```yaml
spring:
  ai:
    agents:
      observability:
        enabled: true  # auto-detected from ObservationRegistry on classpath
```

---

## 14. Cost & Token Tracking

### Motivation
LLM calls cost money. Tracking token usage per node, per workflow, and per agent is essential for budgeting and optimization.

### Design
Capture `Usage` metadata from `ChatResponse` in `LlmExecutor` and store it alongside the node result.

### Affected Files

| File | Change |
|------|--------|
| `executor/LlmExecutor.java` | Capture `Usage` from `ChatResponse` |
| `workflow/WorkflowResult.java` | Add `TokenUsage tokenUsage` aggregate |
| `workflow/TokenUsage.java` | **New** — `promptTokens`, `completionTokens`, `totalTokens` |
| `workflow/WorkflowExecutor.java` | Aggregate token usage from all LLM nodes |

### Implementation

**`TokenUsage.java`**:
```java
@Value @Builder
public class TokenUsage {
    @Builder.Default long promptTokens = 0;
    @Builder.Default long completionTokens = 0;
    @Builder.Default long totalTokens = 0;
    
    /** Estimated cost in USD based on configured price per 1K tokens. */
    @Builder.Default double estimatedCostUsd = 0.0;
    
    /** Per-node breakdown: nodeId → usage. */
    @Builder.Default Map<String, TokenUsage> nodeBreakdown = Map.of();
    
    public TokenUsage add(TokenUsage other) {
        return TokenUsage.builder()
                .promptTokens(this.promptTokens + other.promptTokens)
                .completionTokens(this.completionTokens + other.completionTokens)
                .totalTokens(this.totalTokens + other.totalTokens)
                .estimatedCostUsd(this.estimatedCostUsd + other.estimatedCostUsd)
                .build();
    }
}
```

**`LlmExecutor.java`** — Capture usage:
```java
@Override
public Object execute(LlmNode node, NodeContext context) {
    String processedPrompt = buildPrompt(node, context);
    ChatResponse response = chatModel.call(new Prompt(processedPrompt));
    
    // Store token usage in execution context for aggregation
    Usage usage = response.getMetadata().getUsage();
    if (usage != null) {
        Map<String, Object> execCtx = context.getExecutionContext();
        @SuppressWarnings("unchecked")
        Map<String, TokenUsage> usageMap = (Map<String, TokenUsage>)
                execCtx.computeIfAbsent("_tokenUsage", k -> new ConcurrentHashMap<>());
        usageMap.put(node.getId(), TokenUsage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build());
    }
    
    return response.getResult().getOutput().getText();
}
```

**`WorkflowResult`** — Add aggregate:
```java
@Builder.Default
TokenUsage tokenUsage = null;
```

Aggregate in `WorkflowExecutor.execute()` after all nodes complete:
```java
@SuppressWarnings("unchecked")
Map<String, TokenUsage> usageMap = (Map<String, TokenUsage>) context.get("_tokenUsage");
TokenUsage aggregate = usageMap != null
        ? usageMap.values().stream().reduce(TokenUsage.builder().build(), TokenUsage::add)
        : null;

return WorkflowResult.builder()
        .output(output)
        .nodeResults(new LinkedHashMap<>(nodeResults))
        .durationMs(duration)
        .tokenUsage(aggregate)
        .build();
```

### Configuration — Price per 1K tokens
```yaml
spring:
  ai:
    agents:
      cost:
        price-per-1k-prompt-tokens: 0.005
        price-per-1k-completion-tokens: 0.015
```

---

## 15. Workflow Unit Testing DSL & Mock Mode

### Motivation
Testing workflows currently requires manually creating a `NodeExecutorRegistry` with mock executors. This is verbose and error-prone. A testing DSL would dramatically improve the developer experience.

### Design
Create a `spring-ai-agents-test` module (or a test support package in the starter) with a fluent `AgentTester` and `MockNodeExecutor`.

### New Files

| File | Purpose |
|------|---------|
| `test/AgentTester.java` | Fluent test builder |
| `test/MockExecutorRegistry.java` | Pre-configured registry with mock executors |
| `test/NodeAssertion.java` | Assertion DSL for node results |
| `test/WorkflowTestResult.java` | Rich test result with assertions |

### Implementation

**`AgentTester.java`**:
```java
public class AgentTester {
    private final Agent agent;
    private final Map<String, Object> mockResponses = new HashMap<>();
    private String input;
    
    public static AgentTester forAgent(Agent agent) {
        return new AgentTester(agent);
    }
    
    public AgentTester withInput(String input) {
        this.input = input;
        return this;
    }
    
    /** Mock an LLM node to return a fixed response (no actual LLM call). */
    public AgentTester mockNode(String nodeId, Object response) {
        mockResponses.put(nodeId, response);
        return this;
    }
    
    /** Mock ALL LLM nodes to echo their resolved input. */
    public AgentTester mockAllLlmNodes() {
        // Configured in MockExecutorRegistry
        return this;
    }
    
    public WorkflowTestResult execute() {
        MockExecutorRegistry registry = new MockExecutorRegistry(mockResponses);
        WorkflowExecutor executor = new WorkflowExecutor(registry);
        List<Workflow> workflows = agent.buildWorkflows();
        Workflow workflow = workflows.getFirst(); // or select by name
        WorkflowResult result = executor.execute(workflow, input);
        return new WorkflowTestResult(result, workflow);
    }
}
```

**`WorkflowTestResult.java`**:
```java
public class WorkflowTestResult {
    private final WorkflowResult result;
    private final Workflow workflow;
    
    public WorkflowTestResult expectOutput(String expected) {
        assertEquals(expected, result.getOutput());
        return this;
    }
    
    public WorkflowTestResult expectOutputContains(String substring) {
        assertTrue(result.getOutput().contains(substring),
                "Expected output to contain '%s' but was '%s'".formatted(substring, result.getOutput()));
        return this;
    }
    
    public WorkflowTestResult expectNodeExecuted(String nodeId) {
        assertTrue(result.getNodeResults().containsKey(nodeId),
                "Expected node '%s' to have been executed".formatted(nodeId));
        return this;
    }
    
    public WorkflowTestResult expectNodeNotExecuted(String nodeId) {
        assertFalse(result.getNodeResults().containsKey(nodeId),
                "Expected node '%s' to NOT have been executed".formatted(nodeId));
        return this;
    }
    
    public <T> WorkflowTestResult expectNodeResult(String nodeId, Class<T> type, Consumer<T> assertion) {
        T nodeResult = result.getNodeResult(nodeId, type);
        assertNotNull(nodeResult);
        assertion.accept(nodeResult);
        return this;
    }
    
    public WorkflowTestResult expectCompletedWithin(Duration maxDuration) {
        assertTrue(result.getDurationMs() <= maxDuration.toMillis());
        return this;
    }
}
```

### Usage Example
```java
@Test
void researchAgentProducesReport() {
    AgentTester.forAgent(new ResearchAgent())
        .withInput("Quantum Computing")
        .mockNode("factual", "Quantum computers use qubits...")
        .mockNode("analysis", "The field is rapidly evolving...")
        .execute()
        .expectOutputContains("## Research Report")
        .expectOutputContains("Quantum computers use qubits")
        .expectNodeExecuted("factual")
        .expectNodeExecuted("analysis")
        .expectNodeExecuted("guidelines")
        .expectCompletedWithin(Duration.ofSeconds(1));
}
```

### Dry-Run Mode
```java
AgentTester.forAgent(myAgent)
    .withInput("test input")
    .mockAllLlmNodes()  // all LLM nodes return "MOCK[resolvedInput]"
    .execute()
    .expectNodeExecuted("step1")
    .expectNodeExecuted("step2");
```

---

## 16. Agent REST API Controller

### Motivation
Currently, agents are only accessible via MCP tools or programmatic invocation. Most applications need simple HTTP endpoints for invoking agents.

### Design
Add an auto-configured REST controller that exposes all registered agents via HTTP endpoints.

### Affected Files

| File | Change |
|------|--------|
| `api/AgentRestController.java` | **New** |
| `api/AgentInvokeRequest.java` | **New** — request DTO |
| `api/AgentInvokeResponse.java` | **New** — response DTO |
| `autoconfigure/AgentsAutoConfiguration.java` | Register controller conditionally |

### Implementation

**`AgentRestController.java`**:
```java
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.agents.api.enabled", havingValue = "true", matchIfMissing = true)
public class AgentRestController {
    private final AgentRegistry agentRegistry;

    /** List all agents. */
    @GetMapping
    public List<AgentInfo> listAgents() {
        return agentRegistry.getAgentNames().stream()
                .map(name -> {
                    AgentRuntime runtime = agentRegistry.getSyncAgent(name);
                    return new AgentInfo(name, runtime.getDescription(),
                            runtime.getWorkflows().size(), runtime.getInvocationCount());
                })
                .toList();
    }

    /** Invoke an agent. */
    @PostMapping("/{agentName}/invoke")
    public AgentInvokeResponse invoke(@PathVariable String agentName,
                                       @RequestBody AgentInvokeRequest request) {
        AgentRuntime runtime = agentRegistry.getSyncAgent(agentName);
        if (runtime == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentName);
        }
        
        long start = System.currentTimeMillis();
        WorkflowResult result = runtime.invokeWithResult(request.input());
        
        return new AgentInvokeResponse(
                result.getOutput(),
                result.getDurationMs(),
                result.getNodeResults().keySet(),
                result.getTokenUsage()   // null if not tracked
        );
    }

    /** Get agent details including workflow structure. */
    @GetMapping("/{agentName}")
    public AgentDetail getAgent(@PathVariable String agentName) { ... }
}
```

**Request/Response DTOs**:
```java
public record AgentInvokeRequest(String input, Map<String, Object> context) {
    public AgentInvokeRequest(String input) { this(input, Map.of()); }
}

public record AgentInvokeResponse(
    String output,
    long durationMs,
    Set<String> executedNodes,
    TokenUsage tokenUsage
) {}

public record AgentInfo(String name, String description, int workflowCount, int invocationCount) {}
```

### Configuration
```yaml
spring:
  ai:
    agents:
      api:
        enabled: true       # auto-expose REST endpoints (default: true)
        base-path: /api/agents
```

---

## 17. Conversation Memory & Multi-Turn Chat

### Motivation
Current agents are stateless per-invocation (aside from `persistentContext`). Real chat assistants need conversation history so the LLM can reference prior messages.

### Design
Integrate with Spring AI's `ChatMemory` interface. Add a `memoryEnabled` flag to `Agent` or `LlmNode`. When enabled, the executor includes prior messages in the prompt.

### Affected Files

| File | Change |
|------|--------|
| `agent/Agent.java` | Add `default boolean isMemoryEnabled() { return false; }` |
| `agent/AgentRuntime.java` | Maintain `ChatMemory` per runtime |
| `executor/LlmExecutor.java` | Prepend conversation history when memory is enabled |
| `autoconfigure/AgentsAutoConfiguration.java` | Wire `ChatMemory` bean |

### Implementation

**`AgentRuntime.java`** — Add memory:
```java
private final ChatMemory chatMemory; // nullable — only when memory is enabled

public String invoke(String input, Map<String, Object> additionalContext) {
    // ... existing code ...
    
    if (agent.isMemoryEnabled() && chatMemory != null) {
        context.put("conversationHistory", chatMemory.get(getName(), 20));
    }
    
    WorkflowResult result = workflowExecutor.execute(workflow, input, context);
    
    if (agent.isMemoryEnabled() && chatMemory != null) {
        chatMemory.add(getName(), List.of(
                new UserMessage(input),
                new AssistantMessage(result.getOutput())
        ));
    }
    
    // ... existing code ...
}
```

**`LlmExecutor.java`** — Include history:
```java
private String buildPrompt(LlmNode node, NodeContext context) {
    String prompt = PromptInterpolator.interpolate(
            node.getPromptTemplate(), context.getDependencyResults(), context.getExecutionContext());
    
    // Prepend conversation history if available
    @SuppressWarnings("unchecked")
    List<Message> history = (List<Message>) context.getExecutionContext().get("conversationHistory");
    if (history != null && !history.isEmpty()) {
        String historyText = history.stream()
                .map(m -> m.getMessageType().name() + ": " + m.getText())
                .collect(Collectors.joining("\n"));
        prompt = "Previous conversation:\n" + historyText + "\n\nCurrent request:\n" + prompt;
    }
    
    if (node.getSystemPrompt() != null && !node.getSystemPrompt().isBlank()) {
        prompt = node.getSystemPrompt() + "\n\n" + prompt;
    }
    return prompt;
}
```

### Configuration
```yaml
spring:
  ai:
    agents:
      memory:
        enabled: true
        max-messages: 50
        provider: in-memory  # or "jdbc", "redis"
```

---

## 18. Hierarchical Agent Orchestration

### Motivation
Complex tasks benefit from a manager agent that decomposes a problem and delegates sub-tasks to specialist agents. Currently agents can't invoke other agents within a workflow.

### Design
Create an `AgentNode` that invokes another registered agent by name. This enables manager-worker patterns.

### Affected Files

| File | Change |
|------|--------|
| `node/AgentNode.java` | **New** — references another agent by name |
| `executor/AgentNodeExecutor.java` | **New** — invokes via `AgentRegistry` |
| `autoconfigure/AgentsAutoConfiguration.java` | Register executor |

### Implementation

**`AgentNode.java`**:
```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class AgentNode extends Node {
    @NonNull String id;
    
    /** Name of the target agent to invoke (must be registered in AgentRegistry). */
    @NonNull String targetAgentName;
    
    /** Optional input template. If null, passes resolved input directly. */
    String inputTemplate;
}
```

**`AgentNodeExecutor.java`**:
```java
@RequiredArgsConstructor
public class AgentNodeExecutor implements NodeExecutor<AgentNode> {
    private final AgentRegistry agentRegistry;

    @Override
    public Object execute(AgentNode node, NodeContext context) {
        AgentRuntime target = agentRegistry.getSyncAgent(node.getTargetAgentName());
        if (target == null) {
            throw new IllegalStateException("Agent not found: " + node.getTargetAgentName());
        }
        
        String input;
        if (node.getInputTemplate() != null) {
            input = PromptInterpolator.interpolate(
                    node.getInputTemplate(), context.getDependencyResults(), context.getExecutionContext());
        } else {
            input = context.getResolvedInput();
        }
        
        return target.invoke(input, context.getExecutionContext());
    }

    @Override
    public Class<AgentNode> getNodeType() { return AgentNode.class; }
}
```

### Usage Example — Manager-Worker Pattern
```java
@Component
public class ManagerAgent implements Agent {
    public String getName() { return "manager"; }
    public String getDescription() { return "Coordinates specialist agents"; }

    public Workflow buildWorkflow(WorkflowBuilder builder) {
        var input = InputNode.builder().id("input").build();
        var plan = LlmNode.builder().id("plan")
                .promptTemplate("Break down this task into sub-tasks: {input}")
                .build();
        var research = AgentNode.builder().id("research")
                .targetAgentName("research-agent")
                .inputTemplate("{plan}")
                .build();
        var dataProcess = AgentNode.builder().id("process")
                .targetAgentName("data-processor")
                .inputTemplate("{plan}")
                .build();
        var synthesize = LlmNode.builder().id("synthesize")
                .promptTemplate("Synthesize these results:\nResearch: {research}\nData: {process}")
                .build();
        var output = OutputNode.builder().id("output").build();

        return builder.nodes(input, plan, research, dataProcess, synthesize, output)
                .edge(input, plan)
                .edge(plan, research).edge(plan, dataProcess)  // parallel delegation
                .edge(research, synthesize).edge(dataProcess, synthesize)
                .edge(synthesize, output)
                .build();
    }
}
```

### Safeguards
- Add cycle detection: agent A → agent B → agent A must be detected and rejected.
- Add max depth configuration: `spring.ai.agents.max-delegation-depth=3`.

---

## 19. Guardrails & Input/Output Validation

### Motivation
Production AI agents need guardrails to prevent harmful, off-topic, or malformed inputs/outputs. This is especially critical when agents are exposed via MCP or REST.

### Design
Add `inputGuardrail` and `outputGuardrail` functions to the `Agent` interface or `NodeConfig`.

### Affected Files

| File | Change |
|------|--------|
| `guardrail/Guardrail.java` | **New** — functional interface |
| `guardrail/GuardrailResult.java` | **New** — pass/fail with reason |
| `guardrail/ContentFilterGuardrail.java` | **New** — built-in content filter |
| `guardrail/LengthGuardrail.java` | **New** — max length validation |
| `agent/AgentRuntime.java` | Apply guardrails before/after workflow execution |

### Implementation

**`Guardrail.java`**:
```java
@FunctionalInterface
public interface Guardrail {
    GuardrailResult evaluate(String content, Map<String, Object> context);
    
    default Guardrail and(Guardrail other) {
        return (content, ctx) -> {
            GuardrailResult result = this.evaluate(content, ctx);
            if (!result.passed()) return result;
            return other.evaluate(content, ctx);
        };
    }
}

public record GuardrailResult(boolean passed, String reason) {
    public static GuardrailResult pass() { return new GuardrailResult(true, null); }
    public static GuardrailResult fail(String reason) { return new GuardrailResult(false, reason); }
}
```

**Built-in guardrails**:
```java
public class LengthGuardrail implements Guardrail {
    private final int maxLength;
    
    public GuardrailResult evaluate(String content, Map<String, Object> context) {
        if (content.length() > maxLength) {
            return GuardrailResult.fail("Input exceeds max length of " + maxLength);
        }
        return GuardrailResult.pass();
    }
}

public class RegexGuardrail implements Guardrail {
    private final Pattern blockedPattern;
    private final String reason;
    
    public GuardrailResult evaluate(String content, Map<String, Object> context) {
        if (blockedPattern.matcher(content).find()) {
            return GuardrailResult.fail(reason);
        }
        return GuardrailResult.pass();
    }
}
```

**`Agent.java`** — Add guardrail hooks:
```java
public interface Agent {
    // ...existing methods...
    
    /** Input guardrail applied before workflow execution. Default: no guardrail. */
    default Guardrail getInputGuardrail() { return null; }
    
    /** Output guardrail applied after workflow execution. Default: no guardrail. */
    default Guardrail getOutputGuardrail() { return null; }
}
```

**`AgentRuntime.invoke()`** — Apply guardrails:
```java
public String invoke(String input, Map<String, Object> additionalContext) {
    // Input guardrail
    Guardrail inputGuardrail = agent.getInputGuardrail();
    if (inputGuardrail != null) {
        GuardrailResult check = inputGuardrail.evaluate(input, additionalContext);
        if (!check.passed()) {
            log.warn("Agent '{}' input guardrail rejected: {}", getName(), check.reason());
            return "Request rejected: " + check.reason();
        }
    }
    
    // ... execute workflow ...
    
    // Output guardrail
    Guardrail outputGuardrail = agent.getOutputGuardrail();
    if (outputGuardrail != null) {
        GuardrailResult check = outputGuardrail.evaluate(result.getOutput(), context);
        if (!check.passed()) {
            log.warn("Agent '{}' output guardrail rejected: {}", getName(), check.reason());
            return "Output filtered: " + check.reason();
        }
    }
    
    return result.getOutput();
}
```

---

## 20. Workflow Versioning & Hot-Reload

### Motivation
In production, you want to update an agent's workflow without restarting the application. Also, you want to track which version of a workflow produced which results.

### Design
Add version metadata to workflows and a mechanism to reload agent definitions at runtime.

### Affected Files

| File | Change |
|------|--------|
| `workflow/Workflow.java` | Add `String version` field |
| `workflow/WorkflowBuilder.java` | Add `.version("v2")` method |
| `agent/AgentRegistry.java` | Add `reloadAgent(Agent)` method |
| `api/AgentRestController.java` | Add `POST /api/agents/{name}/reload` endpoint |

### Implementation

**`Workflow.java`** — Add version:
```java
@Getter
public final class Workflow {
    private final String name;
    private final String description;
    private final String version;  // e.g., "1.0.0", "v2", or git SHA
    // ... rest unchanged
}
```

**`WorkflowBuilder.java`**:
```java
private String version = "1.0.0";

public WorkflowBuilder version(String version) {
    this.version = version;
    return this;
}
```

**`AgentRegistry.java`** — Add reload:
```java
public void reloadAgent(Agent agent, WorkflowExecutor workflowExecutor, WorkflowRouter workflowRouter) {
    List<Workflow> workflows = agent.buildWorkflows();
    AgentRuntime runtime = new AgentRuntime(agent, workflows, workflowExecutor, workflowRouter);
    syncAgents.put(agent.getName(), runtime);
    log.info("Reloaded agent '{}' with {} workflow(s)", agent.getName(), workflows.size());
}
```

This requires making the `syncAgents` map mutable (use `ConcurrentHashMap` instead of `unmodifiableMap` during construction, or wrap with a reload-aware accessor).

---

## 21. Parallel Thread Pool Improvements

### Motivation
The current `parallelThreads` config only supports `0` (cached) or a fixed number. Production systems need virtual threads (Project Loom), named threads for debugging, and proper shutdown hooks.

### Affected Files

| File | Change |
|------|--------|
| `autoconfigure/AgentsProperties.java` | Add `threadPoolType` enum property |
| `autoconfigure/AgentsAutoConfiguration.java` | Create thread pool based on type |

### Implementation

**`AgentsProperties.java`**:
```java
public enum ThreadPoolType { CACHED, FIXED, VIRTUAL }

@Builder.Default
private ThreadPoolType threadPoolType = ThreadPoolType.VIRTUAL;
```

**`AgentsAutoConfiguration.java`**:
```java
ExecutorService threadPool = switch (properties.getThreadPoolType()) {
    case FIXED -> Executors.newFixedThreadPool(properties.getParallelThreads(),
            Thread.ofPlatform().name("agent-node-", 0).factory());
    case VIRTUAL -> Executors.newVirtualThreadPerTaskExecutor();
    case CACHED -> Executors.newCachedThreadPool(
            Thread.ofPlatform().name("agent-node-", 0).factory());
};
```

### Configuration
```yaml
spring:
  ai:
    agents:
      thread-pool-type: virtual  # cached | fixed | virtual
      parallel-threads: 10       # only for 'fixed'
```

---

## 22. Spring AI ChatClient Migration

### Motivation
The current `LlmExecutor` uses `ChatModel.call(new Prompt(...))` directly. Spring AI 1.0 introduced the `ChatClient` fluent API which supports advisors, function calling, output converters, and more. Migrating unlocks the full Spring AI ecosystem.

### Affected Files

| File | Change |
|------|--------|
| `executor/LlmExecutor.java` | Refactor to use `ChatClient` |
| `autoconfigure/AgentsAutoConfiguration.java` | Inject `ChatClient.Builder` |

### Implementation

**`LlmExecutor.java`** — Refactored:
```java
@RequiredArgsConstructor
public class LlmExecutor implements NodeExecutor<LlmNode>, ReactiveNodeExecutor<LlmNode> {
    private final ChatClient.Builder chatClientBuilder;

    @Override
    public Object execute(LlmNode node, NodeContext context) {
        String processedPrompt = buildPrompt(node, context);
        
        ChatClient.CallResponseSpec response = chatClientBuilder.build()
                .prompt()
                .system(s -> {
                    if (node.getSystemPrompt() != null) s.text(node.getSystemPrompt());
                })
                .user(processedPrompt)
                .call();
        
        if (node.getResponseType() != null) {
            return response.entity(node.getResponseType());
        }
        
        return response.content();
    }
}
```

### Benefits
- Structured output via `.entity(MyPojo.class)` (replaces manual `BeanOutputConverter`).
- Advisor chain support (logging, content filtering, RAG).
- Automatic tool/function calling.
- Fluent, testable API.

---

## 23. Model Selection Per Node

### Motivation
Different nodes may need different LLM models: a cheap/fast model for classification, a powerful model for generation, a specialized model for code. Currently all `LlmNode`s use the same `ChatModel`.

### Affected Files

| File | Change |
|------|--------|
| `node/LlmNode.java` | Add `String modelName` field |
| `executor/LlmExecutor.java` | Look up model by name from a registry |
| `autoconfigure/AgentsAutoConfiguration.java` | Wire model registry |

### Implementation

**`LlmNode.java`**:
```java
/**
 * Optional model name override. When set, the executor uses this specific model
 * instead of the default ChatModel. Must match a registered model name.
 * Example: "gpt-4o-mini", "claude-3-haiku"
 */
String modelName;

/** Optional temperature override for this specific node. */
Double temperature;
```

**Model resolution in `LlmExecutor`**:
```java
private ChatModel resolveModel(LlmNode node) {
    if (node.getModelName() == null) return defaultChatModel;
    ChatModel resolved = modelRegistry.get(node.getModelName());
    if (resolved == null) {
        log.warn("Model '{}' not found, falling back to default", node.getModelName());
        return defaultChatModel;
    }
    return resolved;
}
```

### Configuration
```yaml
spring:
  ai:
    agents:
      models:
        fast: 
          provider: openai
          model: gpt-4o-mini
        powerful:
          provider: openai
          model: gpt-4o
        code:
          provider: anthropic
          model: claude-3-5-sonnet
```

---

## 24. Event Enrichment

### Motivation
Current workflow events (`NodeCompletedEvent`, etc.) carry minimal data — just IDs and timing. They don't include the actual input/output, which makes debugging difficult. The visualization module has to reconstruct this data.

### Affected Files

| File | Change |
|------|--------|
| `workflow/event/NodeStartedEvent.java` | Add `resolvedInput` field |
| `workflow/event/NodeCompletedEvent.java` | Add `result` (Object) field |
| `workflow/event/WorkflowCompletedEvent.java` | Add `nodeResults`, `tokenUsage` |
| `workflow/event/NodeFailedEvent.java` | **New** — fired on node error |
| `workflow/WorkflowExecutor.java` | Pass enriched data to events |

### Implementation

**`NodeCompletedEvent.java`** — Enriched:
```java
public class NodeCompletedEvent extends WorkflowEvent {
    private final String nodeId;
    private final String nodeType;
    private final long durationMs;
    private final String resolvedInput;    // NEW
    private final Object result;           // NEW
    private final int resultLength;        // NEW — for safe logging
    // ...
}
```

**`NodeFailedEvent.java`** — New:
```java
public class NodeFailedEvent extends WorkflowEvent {
    private final String nodeId;
    private final String nodeType;
    private final String errorMessage;
    private final ErrorStrategy appliedStrategy;
    // ...
}
```

---

## 25. Edge Metadata & Labels

### Motivation
For visualization and debugging, edges should carry optional metadata: a label (e.g., "on success"), a description, or a color hint.

### Affected Files

| File | Change |
|------|--------|
| `workflow/Edge.java` | Add `String label`, `Map<String, String> metadata` |

### Implementation
```java
public record Edge(
    @NonNull String from,
    @NonNull String to,
    Predicate<NodeContext> condition,
    String label,                          // "on success", "if negative", etc.
    Map<String, String> metadata           // arbitrary KV for visualization hints
) {
    // Backward-compatible constructors
    public Edge(@NonNull String from, @NonNull String to) {
        this(from, to, null, null, Map.of());
    }
    // ...
}
```

The visualization module can use `label` to render text on edges in the DAG UI, and `metadata` for styling hints like `{"color": "red", "style": "dashed"}`.

---

## 26. WorkflowBuilder Validation Warnings

### Motivation
The builder currently validates hard errors (cycles, missing nodes). It should also warn about potential issues that aren't errors but are likely bugs.

### Warnings to Add
1. **Disconnected nodes** — nodes that have no incoming or outgoing edges (except Input/Output).
2. **Unreachable output nodes** — output nodes that cannot be reached from any input node.
3. **Redundant edges** — edges that are implied by transitivity.
4. **Missing dependencies in prompt templates** — `{nodeId}` references in `promptTemplate` that don't match any dependency node.

### Affected Files

| File | Change |
|------|--------|
| `workflow/WorkflowBuilder.java` | Add `validateWarnings()` called from `build()` |
| `workflow/WorkflowValidationWarning.java` | **New** — warning record |
| `workflow/Workflow.java` | Store `List<WorkflowValidationWarning> warnings` |

### Implementation
```java
public record WorkflowValidationWarning(String code, String message, String nodeId) {}

// In WorkflowBuilder.build():
List<WorkflowValidationWarning> warnings = validateWarnings();
if (!warnings.isEmpty()) {
    warnings.forEach(w -> log.warn("Workflow '{}' validation warning: {} — {}",
            name, w.code(), w.message()));
}
```

**Template reference validation**:
```java
private List<WorkflowValidationWarning> checkTemplateReferences() {
    List<WorkflowValidationWarning> warnings = new ArrayList<>();
    for (Node node : nodes.values()) {
        if (node instanceof LlmNode llm) {
            Set<String> referenced = extractPlaceholders(llm.getPromptTemplate());
            Set<String> dependencies = edges.stream()
                    .filter(e -> e.to().equals(node.getId()))
                    .map(Edge::from)
                    .collect(Collectors.toSet());
            for (String ref : referenced) {
                if (!"input".equals(ref) && !dependencies.contains(ref)) {
                    warnings.add(new WorkflowValidationWarning("UNRESOLVED_PLACEHOLDER",
                            "Prompt template references {%s} but no edge connects '%s' → '%s'"
                                    .formatted(ref, ref, node.getId()), node.getId()));
                }
            }
        }
    }
    return warnings;
}
```

---

## 27. Graceful Shutdown & Cancellation

### Motivation
When the application shuts down, in-flight workflows should complete gracefully rather than being abruptly killed. Users should also be able to cancel a running workflow.

### Affected Files

| File | Change |
|------|--------|
| `workflow/WorkflowExecutor.java` | Add cancellation token support, shutdown hook |
| `workflow/CancellationToken.java` | **New** — thread-safe cancellation signal |
| `agent/AgentRuntime.java` | Add `cancel()` method |
| `autoconfigure/AgentsAutoConfiguration.java` | Register shutdown hook |

### Implementation

**`CancellationToken.java`**:
```java
public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
    public void throwIfCancelled() {
        if (cancelled.get()) throw new WorkflowCancelledException("Workflow cancelled");
    }
}
```

**Integration in `WorkflowExecutor`**:
```java
public WorkflowResult execute(Workflow workflow, String input,
                               Map<String, Object> context, CancellationToken cancellationToken) {
    // ... level loop ...
    for (var levelEntry : levelGroups.entrySet()) {
        cancellationToken.throwIfCancelled();  // check before each level
        // ... execute nodes ...
    }
}
```

**Shutdown hook**:
```java
@Bean
public SmartLifecycle agentShutdownHook(WorkflowExecutor executor) {
    return new SmartLifecycle() {
        public void stop(Runnable callback) {
            executor.shutdown();  // wait for in-flight workflows
            callback.run();
        }
        // ...
    };
}
```

---

## 28. Multi-Modal Input Support

### Motivation
Modern LLMs support images, audio, and files. The framework currently only handles `String` input. Supporting multi-modal input enables vision-based agents, document processing, etc.

### Design
Change the input model from a raw `String` to a richer `AgentInput` type that can carry multiple modalities.

### Affected Files

| File | Change |
|------|--------|
| `agent/AgentInput.java` | **New** — multi-modal input record |
| `agent/AgentRuntime.java` | Add `invoke(AgentInput)` overload |
| `executor/NodeContext.java` | Add `AgentInput agentInput` field |
| `executor/LlmExecutor.java` | Build multi-modal `Prompt` when media is present |

### Implementation

**`AgentInput.java`**:
```java
@Value @Builder
public class AgentInput {
    /** Primary text input (always present). */
    @NonNull String text;
    
    /** Optional images (base64 or URLs). */
    @Builder.Default List<Media> media = List.of();
    
    /** Optional file attachments. */
    @Builder.Default List<Resource> attachments = List.of();
    
    /** Optional metadata. */
    @Builder.Default Map<String, Object> metadata = Map.of();
    
    /** Convenience: create from plain text. */
    public static AgentInput of(String text) {
        return AgentInput.builder().text(text).build();
    }
}
```

**`LlmExecutor`** — Multi-modal prompt:
```java
if (agentInput != null && !agentInput.getMedia().isEmpty()) {
    List<Message> messages = new ArrayList<>();
    UserMessage userMessage = new UserMessage(processedPrompt, agentInput.getMedia());
    messages.add(userMessage);
    if (node.getSystemPrompt() != null) {
        messages.addFirst(new SystemMessage(node.getSystemPrompt()));
    }
    return chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
}
```

---

## 29. Plugin / Extension Point Registry

### Motivation
As the framework grows, third-party developers should be able to contribute node types, executors, guardrails, and routers as reusable plugins without modifying core code.

### Design
Define a `AgentPlugin` SPI that bundles related extensions.

### Affected Files

| File | Change |
|------|--------|
| `plugin/AgentPlugin.java` | **New** — SPI interface |
| `autoconfigure/AgentsAutoConfiguration.java` | Auto-discover and apply plugins |

### Implementation

**`AgentPlugin.java`**:
```java
public interface AgentPlugin {
    /** Plugin name for logging. */
    String getName();
    
    /** Custom node executors provided by this plugin. */
    default List<NodeExecutor<?>> getNodeExecutors() { return List.of(); }
    
    /** Custom guardrails provided by this plugin. */
    default List<Guardrail> getGuardrails() { return List.of(); }
    
    /** Custom workflow routers provided by this plugin. */
    default Optional<WorkflowRouter> getWorkflowRouter() { return Optional.empty(); }
    
    /** Called after all beans are initialized. */
    default void onStartup(AgentRegistry registry) {}
}
```

**Auto-configuration**:
```java
@Bean
public AgentPluginRegistrar pluginRegistrar(Optional<List<AgentPlugin>> plugins,
                                             NodeExecutorRegistry executorRegistry) {
    plugins.ifPresent(list -> list.forEach(plugin -> {
        plugin.getNodeExecutors().forEach(executorRegistry::register);
        log.info("Loaded agent plugin: '{}' ({} executors)",
                plugin.getName(), plugin.getNodeExecutors().size());
    }));
    return new AgentPluginRegistrar();
}
```

---

## 30. Sample Agent Expansion & Cookbook

### Motivation
The current sample module has two agents. A comprehensive cookbook with diverse patterns accelerates adoption.

### New Sample Agents to Add

| Agent | Pattern | Demonstrates |
|-------|---------|-------------|
| `ChatAgent` | Single sequential | Simplest possible agent, conversation memory |
| `RagAgent` | Fan-in with VectorStore | RAG pattern with `VectorStoreNode` |
| `CodeReviewAgent` | Diamond pattern | Parallel style + logic checks, merged output |
| `EmailDraftAgent` | Conditional branching | Sentiment → formal/casual response |
| `DataPipelineAgent` | REST + Transform + LLM | Multi-step: fetch API → transform → analyze |
| `ManagerWorkerAgent` | Hierarchical | Manager delegates to `research-agent` and `data-processor` |
| `GuardedAgent` | Guardrails | Input/output validation with content filtering |
| `CachedAgent` | Caching | Same queries return cached responses |

### File Structure
```
spring-ai-agents-sample/src/main/java/com/springai/agents/sample/agents/
├── ChatAgent.java
├── RagAgent.java
├── CodeReviewAgent.java
├── EmailDraftAgent.java
├── DataPipelineAgent.java
├── ManagerWorkerAgent.java
├── GuardedAgent.java
├── CachedAgent.java
├── DataProcessorAgent.java   (existing)
└── ResearchAgent.java        (existing)
```

Each agent file should include:
1. A class-level Javadoc with an ASCII DAG diagram (like `ResearchAgent` already does).
2. Demonstrated feature annotations in the doc comment.
3. Complete, runnable code with no external dependencies beyond the framework.

---

## Priority Matrix

| # | Improvement | Impact | Effort | Priority |
|---|------------|--------|--------|----------|
| 1 | Conditional Branching | 🔴 High | 🟡 Medium | **P0** |
| 8 | TransformNode | 🔴 High | 🟢 Low | **P0** |
| 11 | RetryConfig in NodeConfig | 🔴 High | 🟢 Low | **P0** |
| 12 | Timeout Enforcement | 🔴 High | 🟢 Low | **P0** |
| 15 | Testing DSL | 🔴 High | 🟡 Medium | **P0** |
| 6 | Structured Output | 🔴 High | 🟢 Low | **P1** |
| 7 | VectorStore Node (RAG) | 🔴 High | 🟡 Medium | **P1** |
| 14 | Token Tracking | 🔴 High | 🟡 Medium | **P1** |
| 16 | REST API Controller | 🟡 Medium | 🟢 Low | **P1** |
| 18 | Agent-calls-Agent | 🔴 High | 🟡 Medium | **P1** |
| 22 | ChatClient Migration | 🟡 Medium | 🟡 Medium | **P1** |
| 23 | Model Selection Per Node | 🔴 High | 🟡 Medium | **P1** |
| 24 | Event Enrichment | 🟡 Medium | 🟢 Low | **P1** |
| 26 | Validation Warnings | 🟡 Medium | 🟢 Low | **P1** |
| 10 | Result Caching | 🟡 Medium | 🟡 Medium | **P2** |
| 13 | OpenTelemetry | 🟡 Medium | 🟡 Medium | **P2** |
| 17 | Conversation Memory | 🟡 Medium | 🟡 Medium | **P2** |
| 19 | Guardrails | 🟡 Medium | 🟡 Medium | **P2** |
| 21 | Virtual Threads | 🟡 Medium | 🟢 Low | **P2** |
| 25 | Edge Metadata | 🟢 Low | 🟢 Low | **P2** |
| 27 | Graceful Shutdown | 🟡 Medium | 🟡 Medium | **P2** |
| 30 | Sample Expansion | 🟡 Medium | 🟡 Medium | **P2** |
| 2 | Iterative Loops | 🟡 Medium | 🔴 High | **P3** |
| 3 | Sub-Workflow Composition | 🟡 Medium | 🟡 Medium | **P3** |
| 4 | Human-in-the-Loop | 🟡 Medium | 🔴 High | **P3** |
| 5 | State Persistence | 🟡 Medium | 🔴 High | **P3** |
| 9 | Streaming Support | 🟡 Medium | 🔴 High | **P3** |
| 20 | Workflow Versioning | 🟢 Low | 🟡 Medium | **P3** |
| 28 | Multi-Modal Input | 🟢 Low | 🟡 Medium | **P3** |
| 29 | Plugin Registry | 🟢 Low | 🟡 Medium | **P3** |

---

## Suggested Execution Order

### Sprint 1 — Foundation (P0 items)
1. **#8 TransformNode** — Simplest new node, high value, < 1 hour
2. **#11 RetryConfig in NodeConfig** — Wire existing `RetryService` into executor pipeline
3. **#12 Timeout Enforcement** — Add `Duration timeout` to `NodeConfig`, enforce in executor
4. **#1 Conditional Branching** — Add `Predicate<NodeContext>` to `Edge`, update executors
5. **#15 Testing DSL** — Create `AgentTester` class for frictionless testing

### Sprint 2 — Intelligence (P1 items)
6. **#6 Structured Output** — Add `responseType` to `LlmNode`
7. **#24 Event Enrichment** — Add input/output to events
8. **#26 Validation Warnings** — Detect disconnected nodes, unresolved placeholders
9. **#16 REST API Controller** — Auto-expose `/api/agents` endpoints
10. **#14 Token Tracking** — Capture `Usage` from `ChatResponse`
11. **#7 VectorStore Node** — RAG in a single node
12. **#18 Agent-calls-Agent** — `AgentNode` for hierarchical orchestration
13. **#23 Model Selection Per Node** — Different models for different tasks
14. **#22 ChatClient Migration** — Modernize LLM integration

### Sprint 3 — Production Readiness (P2 items)
15. **#21 Virtual Threads** — Thread pool type configuration
16. **#25 Edge Metadata** — Labels for visualization
17. **#10 Result Caching** — Cache node results
18. **#13 OpenTelemetry** — Observability
19. **#17 Conversation Memory** — Multi-turn chat
20. **#19 Guardrails** — Input/output validation
21. **#27 Graceful Shutdown** — Cancellation tokens
22. **#30 Sample Expansion** — More example agents

### Sprint 4 — Advanced (P3 items)
23. **#3 Sub-Workflow Composition**
24. **#2 Iterative Loops**
25. **#4 Human-in-the-Loop** + **#5 State Persistence** (co-dependent)
26. **#9 Streaming Support**
27. **#20 Workflow Versioning**
28. **#28 Multi-Modal Input**
29. **#29 Plugin Registry**

