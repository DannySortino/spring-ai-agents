package com.springai.agents.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

/**
 * Workflow router that uses an LLM to select the most appropriate workflow
 * based on user input and workflow descriptions.
 * <p>
 * The LLM is given a numbered list of workflow names and descriptions,
 * along with the user input, and asked to return only the number of the
 * best-matching workflow.
 * <p>
 * Falls back to the first workflow if the LLM response cannot be parsed.
 */
@Slf4j
@RequiredArgsConstructor
public class LlmWorkflowRouter implements WorkflowRouter {

    private final ChatModel chatModel;

    @Override
    public Workflow selectWorkflow(List<Workflow> workflows, String input) {
        if (workflows.size() == 1) {
            return workflows.getFirst();
        }

        String workflowList = IntStream.range(0, workflows.size())
                .mapToObj(i -> "%d. %s — %s".formatted(i + 1, workflows.get(i).getName(),
                        workflows.get(i).getDescription()))
                .collect(joining("\n"));

        String routingPrompt = """
                You are a workflow router. Given the user's input and the available workflows below, \
                respond with ONLY the number of the most appropriate workflow. Nothing else.
                
                Available workflows:
                %s
                
                User input: %s
                
                Best workflow number:""".formatted(workflowList, input);

        try {
            String response = chatModel.call(new Prompt(routingPrompt))
                    .getResult().getOutput().getText().trim();
            int index = Integer.parseInt(response.replaceAll("[^0-9]", "")) - 1;
            if (index >= 0 && index < workflows.size()) {
                Workflow selected = workflows.get(index);
                log.debug("LLM router selected workflow '{}' for input: {}", selected.getName(), input);
                return selected;
            }
        } catch (Exception e) {
            log.warn("LLM workflow routing failed, falling back to first workflow: {}", e.getMessage());
        }

        return workflows.getFirst();
    }
}

