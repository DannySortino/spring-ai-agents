# Data Dependency Management in Spring AI Agents

## Overview

The Spring AI Agents framework now supports data dependency management between workflow steps. This feature allows you to:

1. Define dependencies between workflow steps
2. Pass results from previous steps to dependent steps
3. Reference dependency results in prompts and tool calls

This document explains how to use this functionality to create more complex and powerful agent workflows.

## Conceptual Model

In the Spring AI Agents framework, a workflow can be thought of as a graph:

- **Nodes** are the tool calls to be executed
- **Edges** are the data dependencies between them

Each node (workflow step) may depend on the results of previous steps. When a step is executed, the results of its dependencies are made available to it, allowing for more complex workflows where steps build on the results of previous steps.

## Configuration

### Step Configuration

To use data dependency management, you need to configure your workflow steps with the following properties:

1. `id`: A unique identifier for the step
2. `dependencies`: A list of step IDs that this step depends on
3. `resultMapping`: A map of variable names to dependency step IDs

Example YAML configuration:

```yaml
agents:
  - name: data-dependency-example
    model: gpt-3.5-turbo
    workflow:
      type: CHAIN
      chain:
        - id: user-info-step
          prompt: "Extract user information from: {input}"
        
        - id: billing-info-step
          prompt: "Extract billing information from: {input}"
        
        - id: summary-step
          tool: summarize-tool
          dependencies:
            - user-info-step
            - billing-info-step
          resultMapping:
            userInfo: user-info-step
            billingInfo: billing-info-step
```

### Java Configuration

You can also configure data dependencies programmatically:

```java
// Create workflow steps with dependencies
WorkflowStepDef step1 = new WorkflowStepDef();
step1.setId("user-info-step");
step1.setPrompt("Extract user information from: {input}");

WorkflowStepDef step2 = new WorkflowStepDef();
step2.setId("billing-info-step");
step2.setPrompt("Extract billing information from: {input}");

WorkflowStepDef step3 = new WorkflowStepDef();
step3.setId("summary-step");
step3.setTool("summarize-tool");
step3.setDependencies(List.of("user-info-step", "billing-info-step"));
step3.setResultMapping(Map.of(
    "userInfo", "user-info-step",
    "billingInfo", "billing-info-step"
));
```

## Accessing Dependency Results

Dependency results can be accessed in several ways:

### 1. Through Result Mappings

Result mappings make dependency results available as named variables in the context. For example, with the configuration above, the results of `user-info-step` would be available as `userInfo` in the context.

### 2. Through the `dependencyResults` Map

All dependency results are also available in a `dependencyResults` map in the context, keyed by the dependency step ID.

### 3. In Prompts

You can reference dependency results in prompts using placeholders:

- `{variableName}`: References a context variable (including mapped dependency results)
- `{step.stepId}`: Directly references a step result by ID
- `{dependency.dependencyId}`: References a dependency result from the dependencyResults map

Example prompt:

```
Summarize the following information:
User Info: {userInfo}
Billing Info: {billingInfo}
```

Or using direct step references:

```
Compare these results:
First result: {step.user-info-step}
Second result: {step.billing-info-step}
```

## Example Workflow

Here's a complete example of a workflow that uses data dependency management:

```yaml
agents:
  - name: customer-support-agent
    model: gpt-3.5-turbo
    workflow:
      type: CHAIN
      chain:
        - id: user-info-extraction
          prompt: "Extract user information (name, email, account ID) from: {input}"
        
        - id: issue-classification
          prompt: "Classify the customer issue from: {input}"
        
        - id: knowledge-retrieval
          tool: knowledge-base-search
          dependencies:
            - issue-classification
          resultMapping:
            issueType: issue-classification
          prompt: "Search for solutions related to: {issueType}"
        
        - id: response-generation
          prompt: |
            Generate a customer support response using the following information:
            User Information: {step.user-info-extraction}
            Issue Type: {step.issue-classification}
            Knowledge Base Results: {step.knowledge-retrieval}
            
            Make sure to address the customer by name and provide a solution to their issue.
```

In this example:
1. The first step extracts user information
2. The second step classifies the issue
3. The third step retrieves knowledge based on the issue classification
4. The final step generates a response using all the previous results

## Best Practices

1. **Use meaningful step IDs**: Choose IDs that clearly describe the purpose of the step
2. **Use result mappings for clarity**: Map dependency results to meaningful variable names
3. **Minimize dependencies**: Only include dependencies that are actually needed
4. **Consider the execution order**: Steps are executed in the order they appear in the configuration
5. **Test your workflows**: Use unit tests to verify that dependencies are correctly processed

## Conclusion

Data dependency management enables more complex and powerful agent workflows by allowing steps to build on the results of previous steps. By defining dependencies between steps and using result mappings, you can create workflows that process information in a structured and modular way.