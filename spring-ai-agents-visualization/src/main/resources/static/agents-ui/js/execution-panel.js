/**
 * Execution panel — handles agent testing from the agent-detail page.
 * Posts to the execute API, subscribes to WebSocket for live DAG overlay updates.
 */
let executionWs = null;
let currentExecutionId = null;

function executeAgent() {
    const input = document.getElementById('exec-input').value.trim();
    if (!input) return;

    const runBtn = document.getElementById('exec-run-btn');
    const statusDiv = document.getElementById('exec-status');
    const outputDiv = document.getElementById('exec-output');
    const nodeResults = document.getElementById('node-results');

    // Disable button and show spinner
    runBtn.disabled = true;
    statusDiv.style.display = 'flex';
    outputDiv.style.display = 'none';
    nodeResults.innerHTML = '';
    document.getElementById('exec-status-label').textContent = 'Starting...';

    // Clear previous DAG statuses
    if (typeof dagRenderer !== 'undefined' && dagRenderer) {
        dagRenderer.clearAllStatuses();
    }

    // Set all nodes to PENDING
    const wf = agentWorkflows[currentWorkflowIndex];
    if (dagRenderer && wf) {
        wf.nodes.forEach(n => dagRenderer.setNodeStatus(n.id, 'PENDING'));
    }

    // Connect WebSocket if not connected
    if (!executionWs) {
        executionWs = new AgentsWebSocket();
        executionWs.connect();
        executionWs.onAgentEvent(agentName, handleExecutionEvent);
    }

    // POST execute request
    fetch(`/agents-ui/api/agents/${encodeURIComponent(agentName)}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ input: input })
    })
    .then(res => res.json())
    .then(record => {
        currentExecutionId = record.id;
        document.getElementById('exec-status-label').textContent =
            `Running... (${record.id.substring(0, 8)})`;
    })
    .catch(err => {
        runBtn.disabled = false;
        statusDiv.style.display = 'none';
        outputDiv.style.display = 'block';
        outputDiv.textContent = 'Error: ' + err.message;
    });
}

function handleExecutionEvent(event) {
    // Only handle events for our current execution
    if (currentExecutionId && event.executionId !== currentExecutionId) return;

    const statusLabel = document.getElementById('exec-status-label');

    switch (event.eventType) {
        case 'WORKFLOW_STARTED':
            statusLabel.textContent = `Workflow "${event.workflowName}" started (${event.nodeCount} nodes)`;
            break;

        case 'NODE_STARTED':
            statusLabel.textContent = `Running node: ${event.nodeId} (${event.nodeType})`;
            if (dagRenderer) {
                dagRenderer.setNodeStatus(event.nodeId, 'RUNNING');
                // Mark incoming edges as running
                const wf = agentWorkflows[currentWorkflowIndex];
                if (wf) {
                    wf.edges.filter(e => e.to === event.nodeId)
                        .forEach(e => dagRenderer.setEdgeStatus(e.from, e.to, 'RUNNING'));
                }
            }
            break;

        case 'NODE_COMPLETED':
            if (dagRenderer) {
                dagRenderer.setNodeStatus(event.nodeId, 'COMPLETED', event.durationMs);
                const wf = agentWorkflows[currentWorkflowIndex];
                if (wf) {
                    wf.edges.filter(e => e.to === event.nodeId)
                        .forEach(e => dagRenderer.setEdgeStatus(e.from, e.to, 'COMPLETED'));
                }
            }
            statusLabel.textContent = `Node "${event.nodeId}" completed (${event.durationMs}ms)`;
            break;

        case 'WORKFLOW_COMPLETED':
            document.getElementById('exec-run-btn').disabled = false;
            document.getElementById('exec-status').style.display = 'none';

            const outputDiv = document.getElementById('exec-output');
            outputDiv.style.display = 'block';
            outputDiv.textContent = event.output || '(empty output)';

            // Fetch full execution details for per-node results
            if (currentExecutionId) {
                fetchExecutionDetail(currentExecutionId);
            }
            break;
    }
}

function fetchExecutionDetail(executionId) {
    fetch(`/agents-ui/api/executions/${executionId}`)
        .then(res => res.json())
        .then(record => {
            const container = document.getElementById('node-results');
            if (!record.nodeStatuses || Object.keys(record.nodeStatuses).length === 0) return;

            let html = '<h3 style="font-size:14px;margin-top:16px;margin-bottom:8px;">Node Results</h3>';
            Object.entries(record.nodeStatuses).forEach(([nodeId, ns]) => {
                const statusBadge = ns.status === 'COMPLETED'
                    ? '<span class="badge badge-green">COMPLETED</span>'
                    : ns.status === 'FAILED'
                        ? '<span class="badge badge-red">FAILED</span>'
                        : `<span class="badge badge-gray">${ns.status}</span>`;

                html += `
                    <div class="node-result-card" onclick="this.classList.toggle('expanded')">
                        <div class="node-result-header">
                            <span class="mono">${nodeId} <span style="color:var(--text-muted);font-size:11px;">${ns.nodeType}</span></span>
                            <span>${statusBadge} <span style="color:var(--text-muted);font-size:12px;">${ns.durationMs || 0}ms</span></span>
                        </div>
                        <div class="node-result-body">${ns.resultPreview || '(no result)'}</div>
                    </div>`;
            });
            container.innerHTML = html;
        })
        .catch(() => {});
}

