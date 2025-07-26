# Data Dependency Management in Spring AI Agents

## Overview

The Spring AI Agents framework supports comprehensive data dependency management across all workflow types. This feature allows you to:

1. Define dependencies between workflow steps, routes, workers, and even entire workflows
2. Pass results from dependencies to dependent components
3. Reference dependency results in prompts and tool calls
4. Validate dependencies and detect cycles at configuration time

This document explains how to use these features to create complex, powerful, and reliable agent workflows.

## Conceptual Model

In the Spring AI Agents framework, workflows are treated as directed graphs:

- **Nodes** are the components to be executed (steps, routes, workers, or entire workflows)
- **Edges** are the data dependencies between them

Each node may depend on the results of other nodes. When a node is executed, the results of its dependencies are made available to it, allowing for complex workflows where components build on the results of others.

## Dependency Validation and Cycle Detection

The framework automatically validates all dependencies at initialization time:

1. **Existence Validation**: Ensures all referenced dependency IDs actually exist
2. **Cycle Detection**: Prevents circular dependencies that would cause infinite loops

If any validation errors are detected, an `IllegalArgumentException` is thrown with a detailed error message.

## Configuration

### Step Dependencies

To define dependencies between steps in a chain workflow:

```yaml
workflow:
  type: CHAIN
  chain:
    - id: step1
      prompt: "Extract data from: {input}"
    
    - id: step2
      prompt: "Analyze this data: {step.step1}"
      dependencies:
        - step1
```

### Cross-Workflow Dependencies

You can define dependencies between different workflows:

```yaml
workflow:
  type: PARALLELIZATION
  id: main-workflow
  tasks:
    - name: extraction
      workflow:
        id: extraction-workflow
        type: CHAIN
        chain:
          - prompt: "Extract data from: {input}"
    
    - name: analysis
      workflow:
        id: analysis-workflow
        type: CHAIN
        dependencies:
          - extraction-workflow
        chain:
          - prompt: "Analyze this data: {dependencyResults.extraction-workflow}"
```

### Worker Dependencies

For orchestrator-workers workflows, you can define dependencies between workers:

```yaml
workflow:
  type: ORCHESTRATOR_WORKERS
  workers:
    - name: data-worker
      workflow:
        id: data-workflow
        type: CHAIN
        chain:
          - prompt: "Extract data from: {input}"
    
    - name: analysis-worker
      workflow:
        id: analysis-workflow
        dependencies:
          - data-workflow
        type: CHAIN
        chain:
          - prompt: "Analyze this data: {dependencyResults.data-workflow}"
```

### Route Dependencies

For routing workflows, you can define dependencies between routes:

```yaml
workflow:
  type: ROUTING
  id: routing-workflow
  executeAllRoutes: true
  routes:
    data-route:
      prompt: "Extract data from: {input}"
    
    analysis-route:
      prompt: "Analyze this data: {dependencyResults.data-route}"
  
  dependencies:
    analysis-route:
      - data-route
```

### Result Mappings

You can map dependency results to more meaningful variable names:

```yaml
- id: summary-step
  prompt: |
    Create a summary with:
    User Profile: {userData}
    Billing Details: {billingData}
  dependencies:
    - user-step
    - billing-step
  resultMapping:
    userData: user-step
    billingData: billing-step
```

## Accessing Dependency Results

Dependency results can be accessed in several ways:

### 1. Through Result Mappings

Result mappings make dependency results available as named variables in the context:

```
User Profile: {userData}
```

### 2. Through the `dependencyResults` Map

All dependency results are available in a `dependencyResults` map:

```
User Data: {dependencyResults.user-step}
```

### 3. Direct Step References

For chain workflows, you can directly reference step results:

```
User Data: {step.user-step}
```

## Execution Order with Dependencies

When dependencies are defined, the framework ensures components are executed in an order that respects those dependencies:

1. **Chain Workflow**: Steps are executed in the order they appear, but dependencies are validated
2. **Parallelization Workflow**: Independent workflows are executed in parallel, while dependent workflows wait for their dependencies
3. **Orchestrator-Workers Workflow**: Workers are executed in dependency order
4. **Routing Workflow**: When `executeAllRoutes` is true, routes are executed in dependency order

## Java Configuration

You can also configure dependencies programmatically:

```java
// Create workflow with dependencies
WorkflowDef workflow1 = new WorkflowDef();
workflow1.setId("workflow1");
workflow1.setType(WorkflowType.CHAIN);
// ... configure workflow1

WorkflowDef workflow2 = new WorkflowDef();
workflow2.setId("workflow2");
workflow2.setType(WorkflowType.CHAIN);
workflow2.setDependencies(List.of("workflow1"));
workflow2.setResultMapping(Map.of("data", "workflow1"));
// ... configure workflow2
```

## Example Workflows

For complete examples of different dependency patterns, see the [dependency-management-examples.yml](examples/dependency-management-examples.yml) file.

## Best Practices

1. **Use meaningful IDs**: Choose IDs that clearly describe the purpose of each component
2. **Use result mappings for clarity**: Map dependency results to meaningful variable names
3. **Minimize dependencies**: Only include dependencies that are actually needed
4. **Avoid deep dependency chains**: Keep dependency graphs as flat as possible
5. **Test your workflows**: Use unit tests to verify that dependencies are correctly processed
6. **Handle potential cycles**: Design your workflows to avoid circular dependencies

## Conclusion

Enhanced data dependency management enables complex and powerful agent workflows by allowing components to build on the results of others. With automatic validation and cycle detection, you can create reliable workflows that process information in a structured and modular way.