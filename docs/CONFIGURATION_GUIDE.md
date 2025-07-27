# Spring AI Agent Platform - Configuration Guide

## Table of Contents
1. [Overview](#overview)
2. [Basic Configuration](#basic-configuration)
3. [Agent Configuration](#agent-configuration)
4. [Workflow Types](#workflow-types)
5. [Context Management](#context-management)
6. [Template Variables](#template-variables)
7. [MCP Integration](#mcp-integration)
8. [Spring AI Configuration](#spring-ai-configuration)
9. [Production Configuration](#production-configuration)
10. [Examples](#examples)
11. [Best Practices](#best-practices)
12. [Troubleshooting](#troubleshooting)

## Overview

The Spring AI Agent Platform is a configuration-driven system that allows you to create sophisticated AI agents with various workflow patterns. This guide covers all supported configuration options and how to use them effectively.

### Key Features
- **Unified Graph Workflow System**: Single, powerful workflow engine that handles all execution patterns
- **Advanced Patterns**: Conditional logic, parallel execution, dependency management, and complex data flows
- **Flexible Execution**: Sequential chains, parallel processing, orchestrator patterns, and conditional routing
- **MCP Integration**: Model Context Protocol for external tool integration
- **Flexible Agent Configuration**: System prompts, models, and workflow definitions
- **Production Ready**: SSL, monitoring, logging, and security features

## Basic Configuration

### Minimal Configuration
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

# IMPORTANT: All agents MUST include both 'input_node' and 'output_node'
agents:
  list:
    - name: simpleAgent
      systemPrompt: "You are a helpful assistant."
      workflow:
        type: graph
        chain:
          # REQUIRED: input_node - entry point for user requests
          - nodeId: "input_node"
            prompt: "Receive user request: {input}"
          - nodeId: "response"
            dependsOn: ["input_node"]
            prompt: "Respond to: {input_node}"
          # REQUIRED: output_node - final result returned to user
          - nodeId: "output_node"
            dependsOn: ["response"]
            prompt: "Present response: {response}"
```

### Configuration Structure
```yaml
spring:                    # Spring Boot configuration
  application:
    name: your-app-name
  ai:                     # Spring AI configuration
    openai:               # OpenAI provider settings
    mcp:                  # MCP client/server settings
server:                   # Web server configuration
logging:                  # Logging configuration
management:               # Actuator endpoints
app:                      # Application-specific configuration
  agents:                 # Agent definitions
```

## Agent Configuration

### Agent Definition Structure
```yaml
app:
  agents:
    - name: string              # Required: Unique agent identifier
      model: string             # Required: AI model to use ("openai")
      system-prompt: string     # Optional: System prompt for the agent
      workflow:                 # Required: Workflow definition
        type: string            # Required: Workflow type
        # ... workflow-specific properties
      mcp-server:              # Optional: MCP server configuration
        enabled: boolean
        port: integer
        description: string
        base-url: string
        sse-endpoint: string
        sse-message-endpoint: string
        version: string
```

### Agent Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `name` | String | Yes | Unique identifier for the agent |
| `model` | String | Yes | AI model provider (currently "openai") |
| `system-prompt` | String | No | Initial system prompt for the agent |
| `workflow` | Object | Yes | Workflow definition (see Workflow Types) |
| `mcp-server` | Object | No | MCP server configuration for this agent |

### System Prompts
System prompts define the agent's personality, role, and behavior:

```yaml
system-prompt: |
  You are a professional customer service representative.
  Always be polite, helpful, and solution-oriented.
  Follow company policies and escalate when necessary.
```

## Graph Workflow

The Graph Workflow is the unified workflow engine that supports all execution patterns through a single, powerful dependency-based approach. It can handle sequential chains, parallel processing, orchestrator patterns, conditional routing, and complex combinations of all these patterns.

### Graph Workflow
Executes steps based on dependency relationships between nodes, enabling complex data flow patterns with arbitrary dependencies.

```yaml
workflow:
  type: graph
  chain:
    - nodeId: "extract_data"
      prompt: "Extract key data points from: {input}"
      
    - nodeId: "statistical_analysis"
      dependsOn: ["extract_data"]
      prompt: "Perform statistical analysis on: {extract_data}"
      
    - nodeId: "trend_analysis"
      dependsOn: ["extract_data"]
      prompt: "Identify trends in: {extract_data}"
      
    - nodeId: "generate_report"
      dependsOn: ["statistical_analysis", "trend_analysis"]
      prompt: "Generate report combining stats: {statistical_analysis} and trends: {trend_analysis}"
```

**Graph Properties:**
- `chain`: List of workflow steps with node IDs and dependencies
- Each step must have:
  - `nodeId`: Unique identifier for the node
  - `prompt` or `tool`: Processing instruction or tool to call
  - `dependsOn` (optional): List of node IDs this step depends on

**Key Features:**
- **Arbitrary Dependencies**: Support complex patterns like A→B, B→C, A→C
- **Parallel Execution**: Independent nodes execute concurrently
- **Cycle Detection**: Prevents infinite loops with validation
- **Result Passing**: Results from dependencies are passed to dependent nodes
- **Topological Sorting**: Automatic execution order determination

**Use Cases:**
- Complex data processing pipelines with multiple dependencies
- Workflows requiring both sequential and parallel execution
- Multi-step analysis where later steps need results from multiple earlier steps
- Extensible dependency management for evolving workflows

**Example Dependency Patterns:**

*Linear Chain*: A → B → C
```yaml
chain:
  - nodeId: "A"
    prompt: "Step A: {input}"
  - nodeId: "B"
    dependsOn: ["A"]
    prompt: "Step B: {A}"
  - nodeId: "C"
    dependsOn: ["B"]
    prompt: "Step C: {B}"
```

*Diamond Pattern*: A → B, A → C, B → D, C → D
```yaml
chain:
  - nodeId: "A"
    prompt: "Root: {input}"
  - nodeId: "B"
    dependsOn: ["A"]
    prompt: "Branch B: {A}"
  - nodeId: "C"
    dependsOn: ["A"]
    prompt: "Branch C: {A}"
  - nodeId: "D"
    dependsOn: ["B", "C"]
    prompt: "Merge: {B} and {C}"
```

*Complex Dependencies*: A → B, B → C, A → C
```yaml
chain:
  - nodeId: "A"
    prompt: "Initial: {input}"
  - nodeId: "B"
    dependsOn: ["A"]
    prompt: "Process: {A}"
  - nodeId: "C"
    dependsOn: ["A", "B"]
    prompt: "Synthesize: {A} and {B}"
```

## Retry Configuration

The Spring AI Agent Platform includes a comprehensive retry system that provides resilient execution of workflows and tool calls. The retry system supports multiple strategies and can be configured at different levels.

### Retry Strategies

The platform supports several retry strategies, each optimized for different use cases:

#### NONE
No retry strategy - operations fail immediately on first error.
```yaml
retry:
  strategy: NONE
  maxAttempts: 1
```

**Use cases**: Operations that should not be retried (validation errors), critical operations where immediate failure is preferred.

#### FIXED_DELAY
Fixed delay between retry attempts.
```yaml
retry:
  strategy: FIXED_DELAY
  maxAttempts: 3
  initialDelay: 1000  # 1 second between attempts
```

**Use cases**: Simple retry scenarios, low-traffic scenarios where timing precision isn't critical.

#### LINEAR
Linear increase in delay between retry attempts.
```yaml
retry:
  strategy: LINEAR
  maxAttempts: 3
  initialDelay: 1000   # Start with 1 second
  increment: 1000      # Add 1 second each attempt
```

**Example delays**: 1000ms → 2000ms → 3000ms → 4000ms

**Use cases**: Gradual backoff scenarios, predictable load management.

#### EXPONENTIAL (Recommended)
Exponential backoff - doubles the delay for each retry attempt.
```yaml
retry:
  strategy: EXPONENTIAL
  maxAttempts: 5
  initialDelay: 1000   # Start with 1 second
  multiplier: 2.0      # Double each time
  maxDelay: 30000      # Cap at 30 seconds
```

**Example delays**: 1000ms → 2000ms → 4000ms → 8000ms → 16000ms

**Use cases**: External API calls, database connections, network operations, distributed systems (recommended for most scenarios).

#### EXPONENTIAL_JITTER
Exponential backoff with randomization to prevent thundering herd problems.
```yaml
retry:
  strategy: EXPONENTIAL_JITTER
  maxAttempts: 5
  initialDelay: 1000
  multiplier: 2.0
  maxDelay: 30000
  jitterFactor: 0.1    # 10% randomization
```

**Example delays**: 1000ms±100ms → 2000ms±200ms → 4000ms±400ms

**Use cases**: High-concurrency distributed systems, multiple agents retrying simultaneously.

#### CUSTOM
Custom retry strategy for specialized requirements.
```yaml
retry:
  strategy: CUSTOM
  maxAttempts: 3
  customProperties:
    algorithm: "business-specific"
    parameters: "custom-values"
```

**Use cases**: Complex business-specific retry requirements, integration with external retry systems.

### Retry Configuration Levels

Retry can be configured at multiple levels with inheritance:

#### 1. Application-Wide Default
```yaml
app:
  defaultRetry:
    strategy: EXPONENTIAL
    maxAttempts: 3
    initialDelay: 1000
    maxDelay: 30000
    multiplier: 2.0
```

#### 2. Agent-Level Override
```yaml
app:
  agents:
    - name: billingAgent
      retry:
        strategy: EXPONENTIAL_JITTER
        maxAttempts: 5
        jitterFactor: 0.2
      workflow:
        # ... workflow configuration
```

#### 3. Workflow Step-Level Override
```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: {input}"
      retry:
        strategy: FIXED_DELAY
        maxAttempts: 2
        initialDelay: 500
    - tool: "externalAPI"
      retry:
        strategy: EXPONENTIAL
        maxAttempts: 5
```

#### 4. Route-Level Override (for routing workflows)
```yaml
workflow:
  type: routing
  routes:
    criticalOperation:
      prompt: "Handle critical operation: {input}"
      tool: "criticalTool"
      retry:
        strategy: EXPONENTIAL_JITTER
        maxAttempts: 7
        initialDelay: 500
```

### Exception Classification

Control which exceptions trigger retries:

```yaml
retry:
  strategy: EXPONENTIAL
  maxAttempts: 3
  # Only retry these specific exceptions
  retryableExceptions:
    - "RuntimeException"
    - "IOException"
    - "TimeoutException"
  # Never retry these exceptions (takes precedence)
  nonRetryableExceptions:
    - "IllegalArgumentException"
    - "SecurityException"
```

### Complete Retry Configuration Example

```yaml
app:
  # Application-wide default retry configuration
  defaultRetry:
    strategy: EXPONENTIAL
    maxAttempts: 3
    initialDelay: 1000
    maxDelay: 30000
    multiplier: 2.0
    enabled: true
    retryableExceptions:
      - "RuntimeException"
      - "IOException"
    nonRetryableExceptions:
      - "IllegalArgumentException"
      - "SecurityException"
  
  agents:
    - name: resilientAgent
      model: openai
      systemPrompt: "You are a resilient agent with comprehensive retry capabilities."
      # Agent-level retry override
      retry:
        strategy: EXPONENTIAL_JITTER
        maxAttempts: 5
        jitterFactor: 0.1
      workflow:
        type: chain
        chain:
          - prompt: "Step 1 - Initial processing: {input}"
            # Step-level retry override for critical operations
            retry:
              strategy: EXPONENTIAL
              maxAttempts: 7
              initialDelay: 500
          - tool: "externalAPI"
            # Different retry strategy for external calls
            retry:
              strategy: EXPONENTIAL_JITTER
              maxAttempts: 5
              jitterFactor: 0.2
          - prompt: "Step 3 - Final processing based on results"
```

### Retry Configuration Properties

| Property | Type | Description | Default |
|----------|------|-------------|---------|
| `strategy` | RetryStrategy | Retry strategy to use | EXPONENTIAL |
| `maxAttempts` | Integer | Maximum retry attempts | Strategy-dependent |
| `initialDelay` | Long | Initial delay in milliseconds | 1000 |
| `maxDelay` | Long | Maximum delay cap in milliseconds | 30000 |
| `multiplier` | Double | Backoff multiplier for exponential strategies | 2.0 |
| `increment` | Long | Delay increment for linear strategy | 1000 |
| `jitterFactor` | Double | Randomization factor (0.0-1.0) | 0.1 |
| `enabled` | Boolean | Enable/disable retry for this configuration | true |
| `retryableExceptions` | List<String> | Exception types that trigger retries | All exceptions |
| `nonRetryableExceptions` | List<String> | Exception types that never retry | None |
| `customProperties` | Map<String, Object> | Custom properties for CUSTOM strategy | Empty |

### Default Retry Behavior

When no retry configuration is specified, the system uses these defaults:
- **Strategy**: EXPONENTIAL
- **Max Attempts**: 3
- **Initial Delay**: 1000ms (1 second)
- **Max Delay**: 10000ms (10 seconds)
- **Multiplier**: 2.0
- **Enabled**: true

### Retry Logging

The retry system provides comprehensive logging to help monitor and debug retry behavior:

```
DEBUG: Executing operationName - attempt 1 of 3
WARN:  Operation operationName failed on attempt 1 with error: ConnectionTimeout
DEBUG: Exponential backoff: sleeping for 1000ms
DEBUG: Executing operationName - attempt 2 of 3
INFO:  Operation operationName succeeded on attempt 2 after 1 retries
```

**Log Levels:**
- `DEBUG`: Attempt counts, backoff delays, retry strategy details
- `INFO`: Success after retries
- `WARN`: Individual attempt failures
- `ERROR`: Final failure after all retries exhausted

### Programmatic Retry Usage

For programmatic usage, the RetryService provides convenient methods:

#### executeWithDefaultRetry()
A convenience method that applies default retry configuration without requiring explicit configuration:

```java
// Uses default retry: EXPONENTIAL strategy, 3 attempts, 1000ms initial delay
retryService.executeWithDefaultRetry(() -> {
    // Your operation here
    return someOperation();
}, "operationName");
```

**Default Configuration:**
- Strategy: EXPONENTIAL
- Max Attempts: 3
- Initial Delay: 1000ms
- Max Delay: 10000ms
- Multiplier: 2.0
- Enabled: true

#### executeWithRetry()
For custom retry configuration:

```java
RetryDef customRetry = RetryDef.builder()
    .strategy(RetryStrategy.EXPONENTIAL_JITTER)
    .maxAttempts(5)
    .initialDelay(500L)
    .jitterFactor(0.2)
    .build();

retryService.executeWithRetry(() -> {
    // Your operation here
    return someOperation();
}, customRetry, "operationName");
```

## Context Management

Context management allows you to control how context data is preserved or cleared between workflow steps. By default, all context is preserved throughout workflow execution, but you can configure specific steps to clear context before or after execution for security, memory management, or workflow isolation purposes.

### Overview

Context in workflows contains:
- **System data**: `systemPrompt`, `agentName`, `isFirstInvocation`, etc.
- **Execution data**: `previousResult`, `timestamp`, `currentInput`, etc.
- **Custom data**: Any additional context passed by the caller or added during execution

### Context Management Configuration

Context management is configured at the individual workflow step level using the `contextManagement` property:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: {input}"
    - prompt: "Step 2: {input}"
      contextManagement:
        clearBefore: true
        clearAfter: false
        preserveKeys:
          - "systemPrompt"
          - "agentName"
        removeKeys:
          - "sensitiveData"
          - "temporaryToken"
```

### Configuration Properties

#### `clearBefore`
**Type**: `boolean`  
**Default**: `false`  
**Description**: Clears all context before executing the step, except for keys specified in `preserveKeys`.

```yaml
contextManagement:
  clearBefore: true
  preserveKeys:
    - "systemPrompt"
    - "agentName"
```

#### `clearAfter`
**Type**: `boolean`  
**Default**: `false`  
**Description**: Clears all context after executing the step, except for keys specified in `preserveKeys`.

```yaml
contextManagement:
  clearAfter: true
  preserveKeys:
    - "systemPrompt"
    - "finalResult"
```

#### `preserveKeys`
**Type**: `List<String>`  
**Default**: `null`  
**Description**: List of context keys to preserve when clearing context. Only effective when `clearBefore` or `clearAfter` is `true`.

**Common keys to preserve**:
- `systemPrompt` - Agent's system prompt
- `agentName` - Agent identifier
- `isFirstInvocation` - First invocation flag
- `userId` - User identifier
- `sessionId` - Session identifier

```yaml
contextManagement:
  clearBefore: true
  preserveKeys:
    - "systemPrompt"
    - "agentName"
    - "userId"
    - "sessionId"
```

#### `removeKeys`
**Type**: `List<String>`  
**Default**: `null`  
**Description**: List of specific context keys to remove. Takes precedence over `clearBefore`/`clearAfter` if specified. Only the specified keys are removed, all others are preserved.

```yaml
contextManagement:
  removeKeys:
    - "password"
    - "apiKey"
    - "temporaryData"
```

### Usage Patterns

#### 1. Default Behavior (No Configuration)
By default, all context is preserved throughout workflow execution:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: Collect data: {input}"
    - prompt: "Step 2: Process with context: {previousResult}"
    - prompt: "Step 3: Final result using all context"
    # All context preserved throughout
```

#### 2. Security-Focused Context Clearing
Clear sensitive data before processing:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: Collect user credentials: {input}"
    - prompt: "Step 2: Process securely"
      contextManagement:
        clearBefore: true
        preserveKeys:
          - "systemPrompt"
          - "agentName"
          - "userId"
    - prompt: "Step 3: Return sanitized result"
```

#### 3. Memory Management
Clear temporary data after processing:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: Generate large analysis: {input}"
      contextManagement:
        clearAfter: true
        preserveKeys:
          - "systemPrompt"
          - "agentName"
          - "summaryResult"
    - prompt: "Step 2: Work with clean context"
```

#### 4. Selective Key Removal
Remove only specific sensitive keys:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: Process with credentials: {input}"
    - prompt: "Step 2: Continue processing"
      contextManagement:
        removeKeys:
          - "password"
          - "apiKey"
          - "temporaryToken"
    - prompt: "Step 3: Safe to continue with remaining context"
```

#### 5. Tool Steps with Context Management
Context management works with tool calls as well:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: Prepare data: {input}"
    - tool: "external-api-call"
      contextManagement:
        clearAfter: true
        preserveKeys:
          - "systemPrompt"
          - "agentName"
          - "apiResult"
    - prompt: "Step 3: Process clean result: {apiResult}"
```

### Best Practices

#### Security
- Always preserve `systemPrompt` and `agentName` unless specifically needed otherwise
- Clear sensitive data like passwords, API keys, and personal information
- Use `removeKeys` for targeted removal of known sensitive fields
- Use `clearBefore` for complete isolation when processing sensitive data

#### Performance
- Clear large temporary data after processing to manage memory
- Use `clearAfter` to clean up intermediate results that won't be needed
- Consider context size when designing multi-step workflows

#### Debugging
- Preserve debugging information like `timestamp` and `invocationCount` when troubleshooting
- Keep `previousResult` when you need to trace step-by-step execution
- Use selective removal rather than complete clearing during development

### Context Management in Different Workflow Types

#### Chain Workflows
Context management is applied to each step in the chain:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1: {input}"
    - prompt: "Step 2: {input}"
      contextManagement:
        clearBefore: true
    - prompt: "Step 3: {input}"
```

#### Parallel Workflows
Context management applies to steps within each task's workflow:

```yaml
workflow:
  type: parallel
  tasks:
    - name: task1
      workflow:
        type: chain
        chain:
          - prompt: "Process: {input}"
          - prompt: "Clean up"
            contextManagement:
              clearAfter: true
              preserveKeys: ["result"]
```

#### Orchestrator Workflows
Context management applies to steps within worker workflows:

```yaml
workflow:
  type: orchestrator
  workers:
    - name: worker1
      workflow:
        type: chain
        chain:
          - prompt: "Process: {input}"
          - prompt: "Secure step"
            contextManagement:
              clearBefore: true
              preserveKeys: ["systemPrompt", "agentName"]
```

### Examples

For comprehensive examples of context management in various scenarios, see:
- [Context Management Examples](examples/context-management-examples.yml)

## Template Variables

Template variables allow dynamic content injection into prompts and configuration strings. Variables are enclosed in curly braces `{variableName}` and are replaced at runtime with actual values.

### Core Variables

These variables are available in all workflow types:

#### `{input}`
**Description**: The primary input text provided to the agent or workflow step.
**Availability**: All workflow types, all steps
**Usage**: Used in prompts to reference the current input being processed

**Examples**:
```yaml
# Basic usage in chain workflows
workflow:
  type: chain
  chain:
    - prompt: "Analyze the request: {input}"
    - prompt: "Process the following data: {input}"
```

```yaml
# In routing workflows
workflow:
  type: routing
  routes:
    billing:
      prompt: "Handle billing inquiry: {input}"
```

#### Context Variables `{contextKey}`
**Description**: Any value stored in the execution context can be used as a template variable.
**Availability**: All workflow types
**Usage**: Access context data using the context key name

**System-Provided Context Variables**:
- `{agentName}` - Name of the current agent
- `{timestamp}` - Current timestamp (milliseconds since epoch)
- `{currentInput}` - Current input being processed
- `{systemPrompt}` - Agent's system prompt (available on first invocation)
- `{isFirstInvocation}` - Boolean indicating if this is the first call
- `{conversationHistory}` - Map of previous interactions
- `{invocationCount}` - Number of times agent has been invoked

**Examples**:
```yaml
# Using system context variables
- prompt: "Agent {agentName} processing at {timestamp}: {input}"
- prompt: "This is invocation #{invocationCount} for: {input}"

# Custom context variables (passed by caller)
- prompt: "User {userId} in session {sessionId} asks: {input}"
```

### Workflow-Specific Variables

#### Parallel Workflows: `{results}`
**Description**: Combined results from all parallel tasks, joined with double newlines.
**Availability**: Only in `aggregator` prompts of parallel workflows
**Usage**: Aggregate and synthesize results from parallel task execution

**Examples**:
```yaml
workflow:
  type: parallel
  tasks:
    - name: task1
      workflow:
        type: chain
        chain:
          - prompt: "Analyze aspect A of: {input}"
    - name: task2
      workflow:
        type: chain
        chain:
          - prompt: "Analyze aspect B of: {input}"
  aggregator: "Combine the analysis results: {results}"
```

#### Orchestrator Workflows: `{managerDecision}` and `{workerResults}`
**Description**: 
- `{managerDecision}`: Output from the manager's decision-making process
- `{workerResults}`: Combined outputs from all worker workflows

**Availability**: Only in `synthesizerPrompt` of orchestrator workflows
**Usage**: Synthesize manager decisions with worker outputs

**Examples**:
```yaml
workflow:
  type: orchestrator
  managerPrompt: "Analyze the request and decide approach: {input}"
  workers:
    - name: analyst
      workflow:
        type: chain
        chain:
          - prompt: "Perform analysis on: {input}"
    - name: validator
      workflow:
        type: chain
        chain:
          - prompt: "Validate the data: {input}"
  synthesizerPrompt: |
    Manager Assessment: {managerDecision}
    Worker Results: {workerResults}
    
    Provide final recommendation based on above analysis.
```

### Conditional Logic Variables

These variables are used in conditional step evaluation (not in templates):

#### Field References for Conditions
- `input` - Current input text
- `previousResult` - Result from the previous workflow step
- `context.{key}` - Access specific context values (e.g., `context.userId`)
- `{contextKey}` - Direct context key access

**Examples**:
```yaml
workflow:
  type: chain
  chain:
    - prompt: "Analyzing request: {input}"
    - conditional:
        condition:
          type: contains
          field: "input"              # Reference to input
          value: "urgent"
          ignoreCase: true
        thenStep:
          prompt: "URGENT: {input}"
        elseStep:
          prompt: "Standard: {input}"
    - conditional:
        condition:
          type: contains
          field: "previousResult"     # Reference to previous step result
          value: "URGENT"
        thenStep:
          tool: "emergencyTool"
        elseStep:
          tool: "standardTool"
```

### Variable Processing Rules

1. **Replacement Order**: Variables are processed in the following order:
   - `{input}` is replaced first
   - Context variables are replaced next
   - Workflow-specific variables are replaced last

2. **Missing Variables**: If a variable is not found, it's replaced with an empty string

3. **Nested Variables**: Variables are not recursively processed (no variable-in-variable substitution)

4. **Case Sensitivity**: Variable names are case-sensitive

### LLM-Parsable Agent Generation Guide

This section provides structured information for LLM systems to generate agent configurations:

#### Agent Generation Schema
```yaml
# Required Structure for Agent Generation
agent:
  name: string                    # Unique identifier
  model: "openai"                # Currently only "openai" supported
  systemPrompt: string           # Agent personality and instructions
  workflow:                      # Choose one workflow type
    type: "chain|parallel|orchestrator|routing"
    # Workflow-specific configuration follows
  mcpServer:                     # Optional MCP server exposure
    enabled: boolean
    description: string
```

#### Template Variable Usage Patterns
- **Input Processing**: Always use `{input}` for primary data
- **Context Access**: Use `{contextKey}` for additional data
- **Result Aggregation**: Use `{results}` in parallel workflows
- **Decision Synthesis**: Use `{managerDecision}` and `{workerResults}` in orchestrator workflows
- **Conditional Logic**: Reference fields without braces in conditions

#### Common Patterns for LLM Generation

**Simple Q&A Agent**:
```yaml
workflow:
  type: chain
  chain:
    - prompt: "Answer the question: {input}"
```

**Analysis Agent**:
```yaml
workflow:
  type: chain
  chain:
    - prompt: "Analyze the following: {input}"
    - prompt: "Based on the analysis, provide recommendations"
```

**Routing Agent**:
```yaml
workflow:
  type: routing
  routes:
    category1:
      prompt: "Handle {input} as category 1"
    category2:
      prompt: "Handle {input} as category 2"
```

**Multi-step Processing**:
```yaml
workflow:
  type: chain
  chain:
    - prompt: "Step 1 - Initial processing: {input}"
    - prompt: "Step 2 - Detailed analysis of previous result"
    - prompt: "Step 3 - Final recommendations"
```

## MCP Integration

### MCP Client Configuration
Configure external MCP servers for tool integration:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true                    # Enable MCP client
        name: "toolsClient"             # Client name
        type: SYNC                      # Client type (SYNC/ASYNC)
        sse:
          connections:
            dataServer:
              url: "http://localhost:8081"
              sse-endpoint: "/mcp/sse"
              message-endpoint: "/mcp/message"
        request-timeout: 30s
```

### MCP Server Configuration
Configure agents to expose MCP servers:

```yaml
app:
  agents:
    - name: myAgent
      # ... other properties
      mcp-server:
        enabled: true
        port: 8090
        description: "Agent MCP server"
        base-url: "/api/agents/myAgent/mcp"
        sse-endpoint: "/sse"
        sse-message-endpoint: "/message"
        version: "1.0.0"
```

### Tool Integration
Use tools in workflows:

```yaml
workflow:
  type: chain
  chain:
    - prompt: "Analyzing: {input}"
    - tool: "invoiceTool"              # Call external MCP tool
    - prompt: "Based on tool result: {input}"
```

## Spring AI Configuration

### OpenAI Configuration

#### For OpenAI API
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4                  # Model to use
          temperature: 0.7              # Creativity (0.0-2.0)
          max-tokens: 2048              # Maximum response length
          timeout: 60s                  # Request timeout
```

#### For Local LLM (LMStudio)
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:local-api-key}
      base-url: ${OPENAI_BASE_URL:http://localhost:1234/v1}
      chat:
        options:
          model: ${OPENAI_MODEL:local-model}
          temperature: 0.7
          timeout: 60s
```

### Model Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `api-key` | String | - | API key (or "local-api-key" for LMStudio) |
| `base-url` | String | - | API base URL (e.g., http://localhost:1234/v1 for LMStudio) |
| `model` | String | gpt-4 | Model name (use actual model name in LMStudio) |
| `temperature` | Float | 0.7 | Response creativity (0.0-2.0) |
| `max-tokens` | Integer | - | Maximum response tokens |
| `timeout` | Duration | 60s | Request timeout |

### Local LLM Setup with LMStudio

To use the Spring AI Agent Platform with LMStudio:

1. **Install and Start LMStudio**:
   - Download LMStudio from https://lmstudio.ai/
   - Load your preferred local model
   - Start the local server (usually runs on http://localhost:1234)

2. **Configure Environment Variables**:
   ```bash
   # For LMStudio (recommended)
   export OPENAI_API_KEY=local-api-key
   export OPENAI_BASE_URL=http://localhost:1234/v1
   export OPENAI_MODEL=your-model-name
   
   # Or for OpenAI API
   export OPENAI_API_KEY=your-actual-openai-key
   # Leave OPENAI_BASE_URL unset to use OpenAI's servers
   export OPENAI_MODEL=gpt-4
   ```

3. **Model Name Configuration**:
   - In LMStudio, check the "Local Server" tab for the exact model name
   - Use this exact name in the `OPENAI_MODEL` environment variable
   - Common examples: `llama-2-7b-chat`, `mistral-7b-instruct`, etc.

4. **Verify Connection**:
   - Ensure LMStudio's local server is running
   - Test the endpoint: `curl http://localhost:1234/v1/models`
   - Start your Spring AI Agent Platform application

## Production Configuration

### Security Configuration
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### Logging Configuration
```yaml
logging:
  level:
    org.example: INFO
    org.springframework.ai: WARN
    root: WARN
  file:
    name: /var/log/spring-ai-agent/application.log
    max-size: 100MB
    max-history: 30
```

### Monitoring Configuration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

## Examples

### Example Files
The platform includes several example configurations:

1. **`docs/examples/basic-application.yml`** - Simple, minimal configuration
2. **`docs/examples/development-application.yml`** - Development with MCP integration
3. **`docs/examples/production-application.yml`** - Production-ready configuration
4. **`docs/examples/dynamic-routes-example.yml`** - Dynamic route discovery examples
5. **`docs/workflow-examples.yml`** - Comprehensive workflow examples

### Retry Configuration Examples

The platform provides comprehensive retry configuration examples to help you implement resilient workflows:

#### **`docs/examples/simple-retry-examples.yml`** - Beginner-Friendly Retry Examples
Perfect for getting started with retry functionality. Includes:
- **Basic Application-Wide Retry**: Set default retry behavior for all agents
- **Agent-Level Custom Retry**: Override defaults for specific agents
- **Step-Level Retry Configuration**: Different retry strategies for different workflow steps
- **Route-Specific Retry**: Tailored retry behavior for different request types
- **Exception-Based Retry Control**: Smart retry based on error types
- **Development vs Production**: Different retry strategies for different environments
- **No Retry Configuration**: How to disable retry when needed

#### **`docs/examples/retry-configuration-examples.yml`** - Comprehensive Retry Examples
Advanced retry configurations for complex scenarios. Includes:
- **Production-Ready Enterprise Setup**: Comprehensive retry with exception handling
- **All Retry Strategies Showcase**: Demonstrates NONE, FIXED_DELAY, LINEAR, EXPONENTIAL, EXPONENTIAL_JITTER, and CUSTOM strategies
- **High-Availability Configuration**: Maximum resilience for critical systems
- **Complex Workflow Retry**: Retry configurations for orchestrator and parallel workflows
- **Performance-Optimized Retry**: Balanced retry strategies for different performance requirements

**Usage Examples:**

```yaml
# Simple exponential retry with jitter
app:
  defaultRetry:
    strategy: EXPONENTIAL_JITTER
    maxAttempts: 5
    initialDelay: 1000
    jitterFactor: 0.1

  agents:
    - name: resilientAgent
      model: openai
      system-prompt: "You are a resilient agent."
      workflow:
        type: chain
        chain:
          - prompt: "Process: {input}"
          - tool: "externalService"  # Uses default retry
```

```yaml
# Route-specific retry strategies
workflow:
  type: routing
  routes:
    critical:
      prompt: "Handle critical request: {input}"
      tool: "criticalService"
      retry:
        strategy: EXPONENTIAL_JITTER
        maxAttempts: 7
        jitterFactor: 0.2
    analytics:
      prompt: "Generate analytics: {input}"
      tool: "analyticsService"
      retry:
        strategy: NONE  # Fast fail for performance
```

### Quick Start Examples

#### Simple Chat Agent
```yaml
app:
  agents:
    - name: chatBot
      model: openai
      system-prompt: "You are a friendly chatbot."
      workflow:
        type: chain
        chain:
          - prompt: "Respond to: {input}"
```

#### Multi-Step Analysis Agent
```yaml
app:
  agents:
    - name: analyst
      model: openai
      system-prompt: "You are a data analyst."
      workflow:
        type: chain
        chain:
          - prompt: "Analyze the data: {input}"
          - prompt: "Identify key insights from the analysis"
          - prompt: "Provide actionable recommendations"
```

#### Customer Service Router
```yaml
app:
  agents:
    - name: customerService
      model: openai
      system-prompt: "You are a customer service representative."
      workflow:
        type: routing
        routes:
          billing:
            prompt: "Handle billing question: {input}"
          technical:
            prompt: "Provide technical support: {input}"
          general:
            prompt: "General customer service: {input}"
```

## Best Practices

### 1. Agent Design
- **Clear System Prompts**: Define the agent's role and behavior clearly
- **Appropriate Workflow Types**: Choose the right workflow for your use case
- **Meaningful Names**: Use descriptive names for agents and workflows

### 2. Workflow Design
- **Chain Workflows**: Best for sequential processing
- **Parallel Workflows**: Use for independent tasks that can run simultaneously
- **Orchestrator Workflows**: Ideal for complex, multi-specialist scenarios
- **Routing Workflows**: Perfect for request classification and routing

### 3. Tool Integration
- **External Tools**: Use MCP integration for external system access
- **Error Handling**: Always handle tool failures gracefully
- **Context Passing**: Ensure proper context flow between steps

### 4. Production Deployment
- **Security**: Always use SSL in production
- **Monitoring**: Enable health checks and metrics
- **Logging**: Configure appropriate log levels
- **Resource Management**: Set proper timeouts and limits

### 5. Performance Optimization
- **Temperature Settings**: Lower temperature (0.1-0.3) for consistent responses
- **Token Limits**: Set appropriate max-tokens to control costs
- **Parallel Processing**: Use parallel workflows for independent tasks
- **Caching**: Consider caching for frequently accessed data

## Troubleshooting

### Common Issues

#### 1. Agent Not Loading
**Problem**: Agent doesn't appear in the application
**Solutions**:
- Check YAML syntax and indentation
- Verify agent name is unique
- Ensure required properties are present
- Check application logs for errors

#### 2. Workflow Execution Errors
**Problem**: Workflow fails during execution
**Solutions**:
- Verify workflow type is supported
- Check all required properties are configured
- Ensure proper context variable usage (`{input}`, `{results}`)
- Review system prompts for clarity

#### 3. MCP Tool Integration Issues
**Problem**: Tools not working or not found
**Solutions**:
- Verify MCP client is enabled
- Check MCP server connections
- Ensure tool names match exactly
- Review MCP server logs

#### 4. OpenAI API Issues
**Problem**: API calls failing or slow
**Solutions**:
- Verify API key is correct and active
- Check rate limits and quotas
- Adjust timeout settings
- Monitor API usage

### Debug Configuration
Enable detailed logging for troubleshooting:

```yaml
logging:
  level:
    org.example: DEBUG
    org.springframework.ai: DEBUG
    org.springframework.ai.mcp: TRACE
```

### Health Checks
Monitor application health:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

## Configuration Reference

### Complete Configuration Template
```yaml
spring:
  application:
    name: spring-ai-agent-platform
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
          max-tokens: 2048
          timeout: 60s
    mcp:
      server:
        enabled: true
        base-url: /api/agents/mcp
        sse-endpoint: /sse
        sse-message-endpoint: /message
      client:
        enabled: true
        name: toolsClient
        type: SYNC
        sse:
          connections:
            server1:
              url: http://localhost:8081
              sse-endpoint: /mcp/sse
              message-endpoint: /mcp/message
        request-timeout: 30s

server:
  port: 8080
  ssl:
    enabled: false

logging:
  level:
    org.example: INFO
    org.springframework.ai: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

app:
  agents:
    - name: exampleAgent
      model: openai
      system-prompt: "You are a helpful assistant."
      workflow:
        type: chain
        chain:
          - prompt: "Process: {input}"
      mcp-server:
        enabled: false
        port: 8090
        description: "Example agent MCP server"
        base-url: "/api/agents/exampleAgent/mcp"
        sse-endpoint: "/sse"
        sse-message-endpoint: "/message"
        version: "1.0.0"
```

This configuration guide provides comprehensive coverage of all supported options in the Spring AI Agent Platform. Use the examples and best practices to build sophisticated AI agent systems tailored to your specific needs.