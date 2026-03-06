# Spring AI Agents — Visualization Module

A drop-in web dashboard for visualizing, testing, and monitoring your Spring AI agent workflows. Add the dependency, start your app, and browse to `/agents-ui/`.

## Quick Start

### 1. Add the Dependency

In your application's `pom.xml`, alongside the starter:

```xml
<dependency>
    <groupId>com.springai</groupId>
    <artifactId>spring-ai-agents-visualization</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. (Optional) Configure

The dashboard is enabled by default with sensible defaults. No configuration is required. If you want to customize, add to `application.yml`:

```yaml
spring:
  ai:
    agents:
      visualization:
        enabled: true            # master switch (default: true)
        history:
          enabled: true          # execution history tracking
          max-entries: 500       # max records kept in memory
        live:
          enabled: true          # WebSocket live event streaming
        testing:
          enabled: true          # interactive test panel on agent pages
        analytics:
          enabled: true          # performance analytics page
          window-size: 100       # sliding window for stats
        dashboard:
          enabled: true          # dashboard home page
        dag:
          enabled: true          # DAG graph rendering
        comparison:
          enabled: true          # side-by-side workflow comparison
```

### 3. Start Your Application

```bash
mvn spring-boot:run -pl spring-ai-agents-sample
```

### 4. Open the Dashboard

Navigate to: **http://localhost:8086/agents-ui/**

---

## Using with the Sample Agents

The `spring-ai-agents-sample` module already includes the visualization dependency. To use it:

```bash
# 1. Build the project
mvn clean install

# 2. Run the sample (uses LM Studio / local model by default)
mvn spring-boot:run -pl spring-ai-agents-sample

# Or with OpenAI:
OPENAI_API_KEY=sk-xxx OPENAI_BASE_URL=https://api.openai.com OPENAI_MODEL=gpt-4o \
  mvn spring-boot:run -pl spring-ai-agents-sample
```

Then open **http://localhost:8086/agents-ui/** to see:

- **`research-agent`** — A parallel fan-out/fan-in workflow (factual + analysis + guidelines → synthesized output)
- **`data-processor`** — A multi-workflow agent with two workflows (extract and analyze), routed by the LLM

### What You'll See

1. **Dashboard** — Both agents appear as cards showing workflow count and invocation stats
2. **Click an agent** — See the DAG visualization with nodes rendered by type:
   - Green rounded rect = `InputNode`
   - Blue rounded rect = `OutputNode`  
   - Purple hexagon = `LlmNode`
   - Gray note shape = `ContextNode`
3. **Test Panel** — Type input and click **Run** to execute the agent live
4. **Live DAG overlay** — Watch nodes pulse yellow (RUNNING) → turn green (COMPLETED) in real time
5. **Multi-workflow tabs** — `data-processor` shows tabs for "extract" and "analyze" workflows
6. **Compare** — Click **Compare** on `data-processor` to see both workflows side-by-side with shared/unique node highlighting

---

## Pages & Features

### Dashboard (`/agents-ui/`)

The home page lists all registered agents as cards. Each card shows:

- Agent name and description
- Number of workflows
- Total invocations
- Badge for multi-workflow agents

A **live activity ticker** at the top indicates how many executions are currently running (via WebSocket).

### Agent Detail (`/agents-ui/agent/{name}`)

Shows everything about a single agent:

| Section | Description |
|---------|-------------|
| **Workflow Tabs** | Switch between workflows (multi-workflow agents only) |
| **DAG Graph** | Interactive SVG visualization with zoom/pan and node tooltips |
| **Test Panel** | Enter input, click Run, see live output |
| **Live Overlay** | Nodes animate through PENDING → RUNNING → COMPLETED/FAILED |
| **Node Results** | Expandable cards showing per-node output after execution |

**DAG Node Shapes:**

| Shape | Node Type | Color |
|-------|-----------|-------|
| Rounded rectangle | `InputNode` | Green |
| Rounded rectangle | `OutputNode` | Blue |
| Hexagon | `LlmNode` | Purple |
| Parallelogram | `RestNode` | Orange |
| Note (folded corner) | `ContextNode` | Gray |
| Octagon | `ToolNode` | Teal |

**Tooltips:** Hover over any node to see its configuration (prompt template, URL, tool name, error strategy, etc.).

**Zoom controls:** Use the toolbar buttons (+, −, reset) or scroll to zoom, drag to pan.

### Execution History (`/agents-ui/history`)

Searchable, filterable table of all past executions:

- Filter by agent, status (COMPLETED / RUNNING / FAILED), or free-text search
- Click any row to expand full detail: input, output, per-node status table
- **Clear All** button to reset history

### Performance Analytics (`/agents-ui/analytics`)

D3-powered charts showing:

- **Agent-level bar chart** — Average, P95, and Max execution times per agent
- **Click to drill down** — Node-level breakdown with avg bar + max whisker
- Sliding window over last N executions (configurable via `window-size`)

### Workflow Comparison (`/agents-ui/compare/{name}`)

Side-by-side DAG rendering for multi-workflow agents:

- Shared nodes highlighted in **green** (solid border)
- Unique-to-left nodes highlighted in **orange** (dashed border)
- Unique-to-right nodes highlighted in **purple** (dashed border)

Available via the **Compare** button on multi-workflow agent detail pages.

---

## Architecture

### Backend

| Layer | Component | Role |
|-------|-----------|------|
| **Auto-config** | `VisualizationAutoConfiguration` | Conditional bean registration (`@ConditionalOnBean(AgentRegistry.class)`) |
| **Properties** | `VisualizationProperties` | Feature toggles under `spring.ai.agents.visualization.*` |
| **Service** | `AgentIntrospectionService` | Reads `AgentRegistry`, converts `Workflow` → `WorkflowDto` with level groups |
| **Service** | `ExecutionHistoryService` | Thread-safe bounded in-memory store for execution records |
| **Service** | `ExecutionTrackingService` | `@EventListener` for workflow events → updates history + pushes STOMP messages |
| **Service** | `PerformanceAnalyticsService` | Sliding-window stats (avg/p95/max) per agent, workflow, and node |
| **Controller** | `VisualizationPageController` | Serves Thymeleaf pages |
| **Controller** | `AgentApiController` | REST API: `/agents-ui/api/agents/**` |
| **Controller** | `ExecutionApiController` | REST API: execute agents + browse history |
| **Controller** | `AnalyticsApiController` | REST API: performance stats |
| **Config** | `WebSocketConfig` | STOMP over SockJS at `/agents-ui/ws` |

### Frontend

| File | Purpose |
|------|---------|
| `layout.html` | Shared Thymeleaf layout with sidebar, nav, theme toggle |
| `dashboard.html` | Agent grid + live ticker |
| `agent-detail.html` | DAG + test panel + workflow tabs |
| `history.html` | Execution history table |
| `analytics.html` | D3 charts |
| `compare.html` | Side-by-side comparison |
| `dag-renderer.js` | D3 SVG DAG renderer with type-specific shapes, zoom, tooltips, status overlays |
| `websocket-client.js` | STOMP/SockJS client for live events |
| `execution-panel.js` | Async agent execution with live DAG updates |
| `history-table.js` | Paginated history with search/filter |
| `analytics-charts.js` | D3 grouped bar charts with drill-down |
| `comparison-view.js` | Dual DAG renderer with diff highlighting |
| `theme.js` | Dark/light toggle (persisted in localStorage) |
| `styles.css` | Full dark/light theme via CSS custom properties |

### External Dependencies (CDN, no build step)

- [D3.js v7](https://d3js.org/) — SVG rendering and charts
- [SockJS Client](https://github.com/sockjs/sockjs-client) — WebSocket fallback
- [STOMP.js](https://stomp-js.github.io/) — STOMP protocol over WebSocket

---

## REST API Reference

All endpoints are under `/agents-ui/api/`.

### Agents

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/agents-ui/api/agents` | List all agents (summary) |
| `GET` | `/agents-ui/api/agents/{name}` | Agent detail with all workflows |
| `GET` | `/agents-ui/api/agents/{name}/workflows/{workflow}` | Single workflow structure |

### Execution

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/agents-ui/api/agents/{name}/execute` | Start async execution (body: `{"input": "..."}`) |
| `GET` | `/agents-ui/api/executions` | List history (query params: `agent`, `status`, `q`, `page`, `size`) |
| `GET` | `/agents-ui/api/executions/{id}` | Single execution detail |
| `DELETE` | `/agents-ui/api/executions` | Clear all history |

### Analytics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/agents-ui/api/analytics` | All agent stats |
| `GET` | `/agents-ui/api/analytics/{agent}` | Stats for one agent |
| `GET` | `/agents-ui/api/analytics/{agent}/{workflow}` | Stats for one workflow |

### WebSocket

| Endpoint | Protocol | Topics |
|----------|----------|--------|
| `/agents-ui/ws` | STOMP over SockJS | `/topic/executions/all`, `/topic/executions/{agentName}` |

Event payloads include: `eventType`, `executionId`, `agentName`, `workflowName`, `nodeId`, `nodeType`, `durationMs`, `status`, `output`.

---

## Configuration Reference

All properties under `spring.ai.agents.visualization`:

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Master switch — disables all visualization beans |
| `dashboard.enabled` | `true` | Dashboard home page |
| `dag.enabled` | `true` | DAG graph rendering |
| `live.enabled` | `true` | WebSocket live event streaming |
| `history.enabled` | `true` | Execution history tracking |
| `history.max-entries` | `500` | Max records kept in memory |
| `testing.enabled` | `true` | Interactive test panel on agent pages |
| `comparison.enabled` | `true` | Side-by-side workflow comparison |
| `analytics.enabled` | `true` | Performance analytics page |
| `analytics.window-size` | `100` | Sliding window size for aggregations |

### Production Recommendations

For production deployments, you may want to disable the interactive testing panel and limit history:

```yaml
spring:
  ai:
    agents:
      visualization:
        testing:
          enabled: false        # no ad-hoc executions in prod
        history:
          max-entries: 100      # keep memory bounded
        analytics:
          window-size: 50
```

### Disabling Entirely

```yaml
spring:
  ai:
    agents:
      visualization:
        enabled: false
```

---

## Theme

The UI supports **dark** (default) and **light** themes. Toggle via the button in the bottom-left sidebar. The choice is persisted in `localStorage`.

