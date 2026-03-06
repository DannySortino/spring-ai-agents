package com.springai.agents.sample.agents;

import com.springai.agents.agent.Agent;
import com.springai.agents.node.*;
import com.springai.agents.workflow.Workflow;
import com.springai.agents.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A parallel fan-out/fan-in agent that researches topics from multiple angles concurrently.
 * <p>
 * Demonstrates: node-reference edges (type-safe), batch {@code .nodes()},
 * workflow name/description, beforeExecute/afterExecute hooks,
 * custom output handler, and error handling with CONTINUE_WITH_DEFAULT.
 * <p>
 * Workflow:
 * <pre>
 *              ┌─── factual ────┐
 *   input ─────┤                ├──── output (custom handler)
 *              └─── analysis ───┤
 *                   guidelines ─┘
 * </pre>
 */
@Slf4j
@Component
public class ResearchAgent implements Agent {

    @Override
    public String getName() {
        return "research-agent";
    }

    @Override
    public String getDescription() {
        return "Researches topics from multiple angles in parallel and synthesizes a comprehensive report";
    }

    @Override
    public Workflow buildWorkflow(WorkflowBuilder builder) {
        // Define nodes as variables for type-safe edge references
        var input = InputNode.builder().id("input").build();

        var factual = LlmNode.builder()
                .id("factual")
                .promptTemplate("Research and list factual information about: {input}")
                .systemPrompt("You are a factual research assistant. Provide verified, sourced facts.")
                .hooks(NodeHooks.builder()
                        .beforeExecute(ctx -> log.info("Gathering factual data for: {}",
                                ctx.getResolvedInput().substring(0, Math.min(50, ctx.getResolvedInput().length()))))
                        .afterExecute((ctx, result) -> log.info("Factual research complete: {} facts gathered",
                                String.valueOf(result).split("\n").length))
                        .build())
                .config(NodeConfig.builder()
                        .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                        .defaultValue("Factual data unavailable")
                        .build())
                .build();

        var analysis = LlmNode.builder()
                .id("analysis")
                .promptTemplate("Provide an analytical perspective and critical analysis of: {input}")
                .systemPrompt("You are a critical analyst. Provide balanced, in-depth analysis.")
                .hooks(NodeHooks.builder()
                        .beforeExecute(ctx -> log.info("Starting critical analysis..."))
                        .afterExecute((ctx, result) -> log.info("Analysis complete: {} chars of insight produced",
                                String.valueOf(result).length()))
                        .build())
                .config(NodeConfig.builder()
                        .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                        .defaultValue("Analysis unavailable")
                        .build())
                .build();

        var guidelines = ContextNode.builder()
                .id("guidelines")
                .contextText("Research guidelines: Be thorough and objective. Cite sources where possible.")
                .build();

        var output = OutputNode.builder()
                .id("output")
                .outputHandler(ctx -> {
                    String facts = ctx.getDependencyResultAsString(factual.getId());
                    String analysisText = ctx.getDependencyResultAsString(analysis.getId());
                    String guidelinesText = ctx.getDependencyResultAsString(guidelines.getId());
                    return """
                            ## Research Report
                            
                            ### Facts
                            %s
                            
                            ### Analysis
                            %s
                            
                            ---
                            *%s*
                            """.formatted(facts, analysisText, guidelinesText);
                })
                .build();

        return builder
                .name("research")
                .description("Parallel multi-angle research synthesis")
                .nodes(input, factual, analysis, guidelines, output)
                .edge(input, factual)
                .edge(input, analysis)
                .edge(factual, output)
                .edge(analysis, output)
                .edge(guidelines, output)
                .build();
    }
}
