/**
 * Comparison view — renders two workflows side-by-side with diff highlighting.
 */
function renderComparison(agent) {
    const container = document.getElementById('compare-container');
    if (!agent || !agent.workflows || agent.workflows.length < 2) {
        container.innerHTML = '<p style="color:var(--text-muted);padding:20px;">Need at least 2 workflows to compare.</p>';
        return;
    }

    // Default: compare first two workflows; could add selectors later
    const wfA = agent.workflows[0];
    const wfB = agent.workflows[1];

    // Compute shared and unique node IDs
    const idsA = new Set(wfA.nodes.map(n => n.id));
    const idsB = new Set(wfB.nodes.map(n => n.id));
    const shared = new Set([...idsA].filter(id => idsB.has(id)));
    const uniqueA = new Set([...idsA].filter(id => !shared.has(id)));
    const uniqueB = new Set([...idsB].filter(id => !shared.has(id)));

    // Build left panel
    container.innerHTML = `
        <div class="compare-panel">
            <div class="compare-panel-header">${wfA.name} <span class="badge badge-gray" style="margin-left:8px;">${wfA.nodes.length} nodes</span></div>
            <div class="dag-container" style="border:none;">
                <svg id="compare-svg-left"></svg>
                <div class="dag-tooltip" id="compare-tooltip-left"></div>
            </div>
        </div>
        <div class="compare-panel">
            <div class="compare-panel-header">${wfB.name} <span class="badge badge-gray" style="margin-left:8px;">${wfB.nodes.length} nodes</span></div>
            <div class="dag-container" style="border:none;">
                <svg id="compare-svg-right"></svg>
                <div class="dag-tooltip" id="compare-tooltip-right"></div>
            </div>
        </div>`;

    // Render left DAG
    const rendererA = new DagRenderer('compare-svg-left', 'compare-tooltip-left', { nodeWidth: 130, nodeHeight: 44 });
    rendererA.render(wfA, { shared, uniqueLeft: uniqueA });

    // Render right DAG
    const rendererB = new DagRenderer('compare-svg-right', 'compare-tooltip-right', { nodeWidth: 130, nodeHeight: 44 });
    rendererB.render(wfB, { shared, uniqueRight: uniqueB });

    // Apply highlight colors to unique nodes
    applyComparisonColors('compare-svg-left', uniqueA, 'var(--orange)');
    applyComparisonColors('compare-svg-right', uniqueB, 'var(--purple)');
    applySharedColors('compare-svg-left', shared, 'var(--green)');
    applySharedColors('compare-svg-right', shared, 'var(--green)');
}

function applyComparisonColors(svgId, uniqueIds, color) {
    const svg = d3.select('#' + svgId);
    uniqueIds.forEach(id => {
        const node = svg.select(`[data-node-id="${id}"]`);
        node.selectAll('rect, polygon, path')
            .attr('stroke', color)
            .attr('stroke-width', 2.5)
            .attr('stroke-dasharray', '6 3');
    });
}

function applySharedColors(svgId, sharedIds, color) {
    const svg = d3.select('#' + svgId);
    sharedIds.forEach(id => {
        const node = svg.select(`[data-node-id="${id}"]`);
        node.selectAll('rect, polygon, path')
            .attr('stroke', color)
            .attr('stroke-width', 2.5);
    });
}

