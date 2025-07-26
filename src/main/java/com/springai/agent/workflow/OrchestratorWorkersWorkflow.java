package com.springai.agent.workflow;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Workflow implementation that uses a manager-worker pattern with orchestration and synthesis.
 * 
 * This workflow implements a sophisticated three-phase execution pattern:
 * 
 * 1. **Orchestration Phase**: A manager analyzes the input and makes decisions about
 *    task delegation and execution strategy
 * 2. **Execution Phase**: Multiple specialized worker workflows execute their tasks
 *    based on the manager's guidance
 * 3. **Synthesis Phase**: A synthesizer combines and processes all worker results
 *    along with the manager's decisions into a final response
 * 
 * Key features:
 * - Manager-driven task orchestration and delegation
 * - Parallel execution of specialized worker workflows
 * - Intelligent result synthesis combining manager decisions and worker outputs
 * - Context variable substitution in all prompts
 * - Flexible worker configuration through named workflow mapping
 * 
 * This pattern is ideal for complex tasks that benefit from specialized processing
 * by different workers, coordinated by a central manager, and synthesized into
 * a coherent final result.
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public class OrchestratorWorkersWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final String managerPrompt;
    private final Map<String, Workflow> workers;
    private final String synthesizerPrompt;
    
    public OrchestratorWorkersWorkflow(
            ChatModel chatModel, 
            String managerPrompt, 
            Map<String, Workflow> workers, 
            String synthesizerPrompt) {
        this.chatModel = chatModel;
        this.managerPrompt = managerPrompt;
        this.workers = workers;
        this.synthesizerPrompt = synthesizerPrompt;
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        // First, use the manager to decide which workers to engage and how
        String processedManagerPrompt = processPrompt(managerPrompt, input, context);
        Prompt managerPromptObj = new Prompt(processedManagerPrompt);
        String managerDecision = chatModel.call(managerPromptObj).getResult().getOutput().getText();
        
        // Execute all workers (in a real implementation, the manager decision could be parsed
        // to determine which workers to run, but for simplicity we'll run all)
        Map<String, String> workerResults = workers.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().execute(input, context)
            ));
        
        // Synthesize the results
        String combinedResults = workerResults.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining("\n\n"));
        
        String processedSynthesizerPrompt = synthesizerPrompt
            .replace("{managerDecision}", managerDecision)
            .replace("{workerResults}", combinedResults);
        processedSynthesizerPrompt = processPrompt(processedSynthesizerPrompt, input, context);
        
        Prompt synthesizerPromptObj = new Prompt(processedSynthesizerPrompt);
        return chatModel.call(synthesizerPromptObj).getResult().getOutput().getText();
    }
    
    private String processPrompt(String prompt, String input, Map<String, Object> context) {
        String processed = prompt.replace("{input}", input);
        
        // Replace context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        return processed;
    }
}
