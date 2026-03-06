package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.autoconfigure.VisualizationProperties;
import com.springai.agents.visualization.dto.AgentDetailDto;
import com.springai.agents.visualization.service.AgentIntrospectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves Thymeleaf HTML pages for the visualization dashboard.
 */
@Controller
@RequestMapping("/agents-ui")
@RequiredArgsConstructor
public class VisualizationPageController {

    private final AgentIntrospectionService introspectionService;
    private final VisualizationProperties props;

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("agents", introspectionService.getAllAgents());
        model.addAttribute("props", props);
        return "dashboard";
    }

    @GetMapping("/agent/{name}")
    public String agentDetail(@PathVariable String name, Model model) {
        AgentDetailDto agent = introspectionService.getAgentDetail(name);
        if (agent == null) return "redirect:/agents-ui/";
        model.addAttribute("agent", agent);
        model.addAttribute("props", props);
        return "agent-detail";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("props", props);
        return "history";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("props", props);
        return "analytics";
    }

    @GetMapping("/compare/{name}")
    public String compare(@PathVariable String name, Model model) {
        AgentDetailDto agent = introspectionService.getAgentDetail(name);
        if (agent == null || !agent.isMultiWorkflow()) return "redirect:/agents-ui/";
        model.addAttribute("agent", agent);
        model.addAttribute("props", props);
        return "compare";
    }
}

