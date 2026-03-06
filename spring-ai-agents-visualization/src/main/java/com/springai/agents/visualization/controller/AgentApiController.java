package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.dto.AgentDetailDto;
import com.springai.agents.visualization.dto.AgentSummaryDto;
import com.springai.agents.visualization.dto.WorkflowDto;
import com.springai.agents.visualization.service.AgentIntrospectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for agent introspection.
 */
@RestController
@RequestMapping("/agents-ui/api/agents")
@RequiredArgsConstructor
public class AgentApiController {

    private final AgentIntrospectionService introspectionService;

    @GetMapping
    public List<AgentSummaryDto> listAgents() {
        return introspectionService.getAllAgents();
    }

    @GetMapping("/{name}")
    public ResponseEntity<AgentDetailDto> getAgent(@PathVariable String name) {
        AgentDetailDto detail = introspectionService.getAgentDetail(name);
        return detail != null ? ResponseEntity.ok(detail) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{name}/workflows/{workflow}")
    public ResponseEntity<WorkflowDto> getWorkflow(@PathVariable String name,
                                                    @PathVariable String workflow) {
        WorkflowDto dto = introspectionService.getWorkflow(name, workflow);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }
}

