package com.springai.agent.controller;

import com.springai.agent.config.AppProperties;
import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web controller for serving visualization HTML templates and handling web-specific functionality.
 * Provides proper MVC controller methods for different views, model attributes, and navigation.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Controller
@RequestMapping("${spring.ai.agents.visualization.base-path:/visualization}")
public class VisualizationWebController {
    
    private static final Logger logger = LoggerFactory.getLogger(VisualizationWebController.class);
    
    @Autowired
    private AppProperties appProperties;
    
    @Autowired(required = false)
    private GraphVisualizationService graphVisualizationService;
    
    @Autowired(required = false)
    private ExecutionStatusService executionStatusService;
    
    /**
     * Serve the main visualization interface with model attributes.
     */
    @GetMapping
    public String visualization(Model model) {
        logger.debug("Serving main visualization interface");
        
        // Add configuration to model
        AppProperties.VisualizationDef viz = appProperties.getVisualization();
        if (viz != null) {
            model.addAttribute("graphStructureEnabled", viz.isGraphStructure());
            model.addAttribute("realTimeStatusEnabled", viz.isRealTimeStatus());
            model.addAttribute("interactiveCreatorEnabled", viz.isInteractiveCreator());
            model.addAttribute("basePath", viz.getBasePath());
            model.addAttribute("websocketEndpoint", viz.getWebsocketEndpoint());
        } else {
            // Default values
            model.addAttribute("graphStructureEnabled", false);
            model.addAttribute("realTimeStatusEnabled", false);
            model.addAttribute("interactiveCreatorEnabled", false);
            model.addAttribute("basePath", "/visualization");
            model.addAttribute("websocketEndpoint", "/ws/status");
        }
        
        // Add agent count to model
        int agentCount = (appProperties.getAgents() != null) ? appProperties.getAgents().size() : 0;
        model.addAttribute("agentCount", agentCount);
        
        // Add service availability to model
        model.addAttribute("graphServiceAvailable", graphVisualizationService != null);
        model.addAttribute("statusServiceAvailable", executionStatusService != null);
        
        return "visualization";
    }
    
    /**
     * Alternative endpoint for the visualization interface.
     */
    @GetMapping("/")
    public String visualizationRoot(Model model) {
        return visualization(model);
    }
    
    /**
     * Serve the graph structure view specifically.
     */
    @GetMapping("/graph")
    public String graphView(Model model, @RequestParam(required = false) String agent) {
        logger.debug("Serving graph view for agent: {}", agent);
        
        model.addAttribute("selectedAgent", agent);
        model.addAttribute("activeTab", "structure");
        
        return visualization(model);
    }
    
    /**
     * Serve the execution status view specifically.
     */
    @GetMapping("/status")
    public String statusView(Model model) {
        logger.debug("Serving status view");
        
        model.addAttribute("activeTab", "status");
        
        return visualization(model);
    }
    
    /**
     * Serve the graph creator view specifically.
     */
    @GetMapping("/creator")
    public String creatorView(Model model) {
        logger.debug("Serving creator view");
        
        model.addAttribute("activeTab", "creator");
        
        return visualization(model);
    }
    
    /**
     * Handle agent selection and redirect to graph view.
     */
    @PostMapping("/select-agent")
    public String selectAgent(@RequestParam String agentName, RedirectAttributes redirectAttributes) {
        logger.debug("Agent selected: {}", agentName);
        
        redirectAttributes.addAttribute("agent", agentName);
        return "redirect:/visualization/graph";
    }
    
    /**
     * Handle navigation between tabs.
     */
    @GetMapping("/navigate/{tab}")
    public String navigateToTab(@PathVariable String tab, Model model) {
        logger.debug("Navigating to tab: {}", tab);
        
        // Validate tab name
        if (!isValidTab(tab)) {
            logger.warn("Invalid tab requested: {}", tab);
            return "redirect:/visualization";
        }
        
        model.addAttribute("activeTab", tab);
        return visualization(model);
    }
    
    /**
     * Error handling for visualization-specific errors.
     */
    @GetMapping("/error")
    public String errorPage(Model model, @RequestParam(required = false) String message) {
        logger.error("Visualization error page requested with message: {}", message);
        
        model.addAttribute("errorMessage", message != null ? message : "An error occurred in the visualization system");
        model.addAttribute("activeTab", "error");
        
        return "visualization";
    }
    
    
    /**
     * Validate if the requested tab is valid.
     */
    private boolean isValidTab(String tab) {
        return (
                "structure".equals(tab) ||
                        "status".equals(tab) ||
                        "creator".equals(tab) ||
                        "health".equals(tab)
        );
    }
}