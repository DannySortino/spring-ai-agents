package com.springai.agents.yaml;

import com.springai.agents.agent.Agent;
import com.springai.agents.node.*;
import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link Agent} instances from {@link YamlAgentDefinition} configurations.
 * <p>
 * Enables no-code agent creation by parsing YAML definitions and constructing
 * the corresponding workflow DAGs with properly typed nodes.
 */
@Slf4j
public class YamlAgentBuilder {

    /**
     * Build an Agent from a YAML definition.
     *
     * @param definition The parsed YAML agent definition
     * @return A fully configured Agent instance
     */
    public Agent build(YamlAgentDefinition definition) {
        log.info("Building agent '{}' from YAML definition", definition.getName());
        
        return new Agent() {
            @Override
            public String getName() {
                return definition.getName();
            }

            @Override
            public String getDescription() {
                return definition.getDescription();
            }

            @Override
            public List<Workflow> buildWorkflows() {
                List<Workflow> workflows = new ArrayList<>();
                
                for (YamlAgentDefinition.WorkflowDefinition wfDef : definition.getWorkflows()) {
                    Workflow workflow = buildWorkflow(wfDef);
                    workflows.add(workflow);
                    log.debug("Built workflow '{}' with {} nodes", 
                            wfDef.getName(), wfDef.getNodes().size());
                }
                
                return workflows;
            }
        };
    }

    private Workflow buildWorkflow(YamlAgentDefinition.WorkflowDefinition wfDef) {
        WorkflowBuilder builder = WorkflowBuilder.create()
                .name(wfDef.getName())
                .description(wfDef.getDescription());

        // Build and add all nodes
        for (YamlAgentDefinition.NodeDefinition nodeDef : wfDef.getNodes()) {
            Node node = buildNode(nodeDef);
            builder.node(node);
        }

        // Add all edges
        for (YamlAgentDefinition.EdgeDefinition edgeDef : wfDef.getEdges()) {
            builder.edge(edgeDef.getFrom(), edgeDef.getTo());
        }

        return builder.build();
    }

    private Node buildNode(YamlAgentDefinition.NodeDefinition nodeDef) {
        String type = nodeDef.getType().toLowerCase();
        
        // Build node config if error handling specified
        NodeConfig config = null;
        if (nodeDef.getErrorStrategy() != null) {
            ErrorStrategy strategy = ErrorStrategy.valueOf(nodeDef.getErrorStrategy().toUpperCase());
            config = NodeConfig.builder()
                    .errorStrategy(strategy)
                    .defaultValue(nodeDef.getDefaultValue())
                    .build();
        }

        return switch (type) {
            case "input" -> InputNode.builder()
                    .id(nodeDef.getId())
                    .config(config)
                    .build();
                    
            case "output" -> OutputNode.builder()
                    .id(nodeDef.getId())
                    .config(config)
                    .build();
                    
            case "llm" -> LlmNode.builder()
                    .id(nodeDef.getId())
                    .promptTemplate(nodeDef.getPrompt())
                    .systemPrompt(nodeDef.getSystemPrompt())
                    .config(config)
                    .build();
                    
            case "rest" -> RestNode.builder()
                    .id(nodeDef.getId())
                    .method(nodeDef.getMethod() != null ? nodeDef.getMethod() : "GET")
                    .urlTemplate(nodeDef.getUrl())
                    .bodyTemplate(nodeDef.getBody())
                    .headers(nodeDef.getHeaders())
                    .config(config)
                    .build();
                    
            case "tool" -> ToolNode.builder()
                    .id(nodeDef.getId())
                    .toolName(nodeDef.getToolName())
                    .argumentsTemplate(nodeDef.getToolArgs())
                    .config(config)
                    .build();
                    
            case "context" -> ContextNode.builder()
                    .id(nodeDef.getId())
                    .contextKey(nodeDef.getContextKey())
                    .contextValue(nodeDef.getContextValue())
                    .config(config)
                    .build();
                    
            default -> throw new IllegalArgumentException(
                    "Unknown node type: " + type + ". Supported: input, output, llm, rest, tool, context");
        };
    }
}
