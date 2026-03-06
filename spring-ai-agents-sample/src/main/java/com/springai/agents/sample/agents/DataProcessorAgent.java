package com.springai.agents.sample.agents;

import com.springai.agents.agent.Agent;
import com.springai.agents.node.*;
import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A multi-workflow agent that routes requests to the most appropriate workflow.
 * <p>
 * Demonstrates: multiple workflows per agent, node-reference edges (type-safe),
 * batch {@code .nodes()}, workflow routing, and custom output handler.
 * <p>
 * Workflows:
 * <ul>
 *   <li><b>extract</b>: Extracts key data points (sequential)</li>
 *   <li><b>analyze</b>: Performs deep analysis (sequential)</li>
 * </ul>
 */
@Component
public class DataProcessorAgent implements Agent {

    @Override
    public String getName() {
        return "data-processor";
    }

    @Override
    public String getDescription() {
        return "Processes data by extracting key points or performing deep analysis based on the request";
    }

    @Override
    public List<Workflow> buildWorkflows() {
        return List.of(
                buildExtractionWorkflow(),
                buildAnalysisWorkflow()
        );
    }

    private Workflow buildExtractionWorkflow() {
        var input = InputNode.builder().id("input").build();
        var extract = LlmNode.builder()
                .id("extract")
                .promptTemplate("Extract the key data points and facts from the following text:\n\n{input}")
                .systemPrompt("You are a data extraction specialist. Output structured, clear data points.")
                .build();
        var output = OutputNode.builder()
                .id("output")
                .outputHandler(ctx -> {
                    String extracted = ctx.getDependencyResult(extract.getId(), String.class);
                    return "## Extracted Data Points\n\n" + extracted;
                })
                .build();

        return WorkflowBuilder.create()
                .name("extract")
                .description("Extracts key data points, facts, and structured information from text")
                .nodes(input, extract, output)
                .edge(input, extract)
                .edge(extract, output)
                .build();
    }

    private Workflow buildAnalysisWorkflow() {
        var input = InputNode.builder().id("input").build();
        var extract = LlmNode.builder()
                .id("extract")
                .promptTemplate("Extract the key data points from:\n\n{input}")
                .build();
        var analyze = LlmNode.builder()
                .id("analyze")
                .promptTemplate("Analyze the following extracted data and provide insights:\n\n{extract}")
                .systemPrompt("You are a data analysis expert. Provide clear, structured analysis with recommendations.")
                .build();
        var output = OutputNode.builder().id("output").build();

        return WorkflowBuilder.create()
                .name("analyze")
                .description("Performs deep analysis with insights, trends, and recommendations")
                .nodes(input, extract, analyze, output)
                .edge(input, extract)
                .edge(extract, analyze)
                .edge(analyze, output)
                .build();
    }
}
