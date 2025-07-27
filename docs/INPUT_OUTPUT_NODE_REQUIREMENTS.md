# Input and Output Node Requirements

## Overview

As of the latest version, **ALL agent workflows MUST include both `input_node` and `output_node`** in their workflow definitions. This is a mandatory requirement that ensures proper workflow validation and execution.

## What are Input and Output Nodes?

### input_node
- **Purpose**: Defines where the agent receives inbound messages from `agentService.invoke()`
- **Behavior**: Acts as the entry point for all user requests
- **Data Flow**: Only the `input_node` receives the original user input; all other nodes receive results from their dependencies
- **Required**: Yes - workflow validation will fail without this node

### output_node  
- **Purpose**: Defines what the agent returns as the final result to the user
- **Behavior**: Acts as the exit point that provides the final response
- **Data Flow**: Should depend on the final processing node(s) in your workflow
- **Required**: Yes - workflow validation will fail without this node

## Configuration Structure

All examples must use the correct configuration structure:

```yaml
# CORRECT - New structure
agents:
  list:
    - name: "your-agent-name"
      systemPrompt: "Your system prompt"
      workflow:
        type: graph
        chain:
          # REQUIRED: input_node
          - nodeId: "input_node"
            prompt: "Receive user request: {input}"
          
          # Your processing nodes here
          - nodeId: "process"
            dependsOn: ["input_node"]
            prompt: "Process the request: {input_node}"
          
          # REQUIRED: output_node
          - nodeId: "output_node"
            dependsOn: ["process"]
            prompt: "Present final result: {process}"

# INCORRECT - Old structure (will not work)
app:
  agents:
    - name: "your-agent-name"
      model: openai
      system-prompt: "Your system prompt"
```

## Example Patterns

### Simple Linear Workflow
```yaml
agents:
  list:
    - name: "simple-agent"
      systemPrompt: "You are a helpful assistant."
      workflow:
        type: graph
        chain:
          - nodeId: "input_node"
            prompt: "Receive user question: {input}"
          - nodeId: "process"
            dependsOn: ["input_node"]
            prompt: "Process the question: {input_node}"
          - nodeId: "output_node"
            dependsOn: ["process"]
            prompt: "Present answer: {process}"
```

### Parallel Processing Workflow
```yaml
agents:
  list:
    - name: "parallel-agent"
      systemPrompt: "You are a multi-task processor."
      workflow:
        type: graph
        chain:
          - nodeId: "input_node"
            prompt: "Receive and categorize request: {input}"
          - nodeId: "task_a"
            dependsOn: ["input_node"]
            prompt: "Process task A: {input_node}"
          - nodeId: "task_b"
            dependsOn: ["input_node"]
            prompt: "Process task B: {input_node}"
          - nodeId: "combine"
            dependsOn: ["task_a", "task_b"]
            prompt: "Combine results: A={task_a}, B={task_b}"
          - nodeId: "output_node"
            dependsOn: ["combine"]
            prompt: "Present final result: {combine}"
```

### Tool Integration Workflow
```yaml
agents:
  list:
    - name: "tool-agent"
      systemPrompt: "You are a tool-enabled assistant."
      workflow:
        type: graph
        chain:
          - nodeId: "input_node"
            prompt: "Receive tool request: {input}"
          - nodeId: "tool_call"
            dependsOn: ["input_node"]
            tool: "someTool"
          - nodeId: "process_result"
            dependsOn: ["tool_call"]
            prompt: "Process tool result: {tool_call}"
          - nodeId: "output_node"
            dependsOn: ["process_result"]
            prompt: "Present final response: {process_result}"
```

### Conditional Logic Workflow
```yaml
agents:
  list:
    - name: "conditional-agent"
      systemPrompt: "You are a smart routing assistant."
      workflow:
        type: graph
        chain:
          - nodeId: "input_node"
            prompt: "Receive and categorize request: {input}"
          - nodeId: "router"
            dependsOn: ["input_node"]
            conditional:
              condition:
                type: CONTAINS
                field: "input_node"
                value: "urgent"
                ignoreCase: true
              thenStep:
                prompt: "Handle urgent request: {input_node}"
              elseStep:
                prompt: "Handle normal request: {input_node}"
          - nodeId: "output_node"
            dependsOn: ["router"]
            prompt: "Present routed response: {router}"
```

## Files That Need Updating

The following files contain examples that need to be updated with input_node and output_node:

### Partially Updated (need completion)
- `docs/examples/comprehensive-agents-example.yml` - 3 agents updated, several more need updating
- `docs/examples/development-application.yml` - 1 agent updated, 3 more need updating

### Need Complete Updates
- `docs/examples/graph-workflow-example.yml` - Multiple complex agents need input/output nodes
- `docs/examples/production-application.yml` - Production examples need updating
- `docs/examples/context-management-examples.yml` - Context examples need updating
- `docs/examples/dynamic-routes-example.yml` - Routing examples need updating
- `docs/examples/retry-configuration-examples.yml` - Retry examples need updating
- `docs/examples/simple-retry-examples.yml` - Simple retry examples need updating
- `docs/examples/visualization-example.yml` - Visualization examples need updating
- `docs/workflow-examples.yml` - All workflow examples need updating

## Validation Errors

If you don't include the required nodes, you'll see these errors:

```
Workflow graph must contain an 'input_node' - this defines where the agent receives inbound messages
```

```
Workflow graph must contain an 'output_node' - this defines what the agent returns at the end
```

## Migration Checklist

For each agent in your configuration:

- [ ] Change `app:` to `agents:` structure
- [ ] Change `system-prompt:` to `systemPrompt:`
- [ ] Remove `model:` property (handled at framework level)
- [ ] Add `input_node` as the first node in the workflow
- [ ] Ensure all processing nodes depend on `input_node` or other nodes (not root-level)
- [ ] Add `output_node` as the final node in the workflow
- [ ] Update any conditional field references from `"input"` to `"input_node"`
- [ ] Fix property naming (e.g., `mcp-server` → `mcpServer`, `base-url` → `baseUrl`)

## Integration with SpringAiToolConfiguration

When `SpringAiToolConfiguration` calls `agentService.invoke(toolInput, context)`, the `toolInput` flows directly to the `input_node`, ensuring proper workflow execution. This makes the `input_node` the effective entry point for all agent invocations.

## Summary

The input_node and output_node requirements ensure:
1. **Consistent entry points** - All user requests start at input_node
2. **Predictable data flow** - Clear path from input to output
3. **Proper validation** - System can verify workflow completeness
4. **Tool integration** - SpringAiToolConfiguration works correctly
5. **Debugging support** - Clear workflow visualization and tracking

All existing examples must be updated to include these required nodes to function properly with the current system.