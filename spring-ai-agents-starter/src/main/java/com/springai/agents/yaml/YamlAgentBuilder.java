package com.springai.agents.yaml;

import com.springai.agents.agent.Agent;
import com.springai.agents.node.*;
import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                    
            case "rest" -> {
                    var restBuilder = RestNode.builder()
                            .id(nodeDef.getId())
                            .method(HttpMethod.valueOf(nodeDef.getMethod() != null ? nodeDef.getMethod().toUpperCase() : "GET"))
                            .url(nodeDef.getUrl())
                            .bodyTemplate(nodeDef.getBody())
                            .config(config);
                    Map<String, String> headers = nodeDef.getHeaders();
                    if (headers != null) {
                        headers.forEach(restBuilder::header);
                    }
                    yield restBuilder.build();
                }
                    
            case "tool" -> ToolNode.builder()
                    .id(nodeDef.getId())
                    .toolName(nodeDef.getToolName())
                    .guidance(nodeDef.getGuidance())
                    .config(config)
                    .build();
                    
            case "context" -> ContextNode.builder()
                    .id(nodeDef.getId())
                    .contextText(nodeDef.getContextText())
                    .config(config)
                    .build();
                    
            default -> throw new IllegalArgumentException(
                    "Unknown node type: " + type + ". Supported: input, output, llm, rest, tool, context");
        };
    }
}
