/**
 * DagRenderer — Renders a workflow DAG as SVG using D3.js.
 * Uses a custom layered (Sugiyama-style) layout based on levelGroups from the server.
 */
class DagRenderer {
    constructor(svgId, tooltipId, options = {}) {
        this.svgId = svgId;
        this.tooltipId = tooltipId;
        this.svg = null;
        this.g = null;
        this.zoom = null;
        this.workflow = null;
        this.nodePositions = {};
        this.options = {
            nodeWidth: 150,
            nodeHeight: 50,
            levelGap: 100,
            nodeGap: 40,
            padding: 60,
            ...options
        };
    }

    render(workflow, highlights) {
        this.workflow = workflow;
        const svgEl = document.getElementById(this.svgId);
        if (!svgEl) return;

        const container = svgEl.parentElement;
        const width = container.clientWidth || 800;

        // Clear previous
        d3.select(svgEl).selectAll('*').remove();

        this.svg = d3.select(svgEl)
            .attr('width', width)
            .attr('height', 500);

        // Zoom/pan group
        this.g = this.svg.append('g');
        this.zoom = d3.zoom()
            .scaleExtent([0.3, 3])
            .on('zoom', (event) => this.g.attr('transform', event.transform));
        this.svg.call(this.zoom);

        // Arrow marker
        this.svg.append('defs').append('marker')
            .attr('id', 'arrowhead')
            .attr('viewBox', '0 0 10 8')
            .attr('refX', 9)
            .attr('refY', 4)
            .attr('markerWidth', 8)
            .attr('markerHeight', 6)
            .attr('orient', 'auto')
            .append('path')
            .attr('d', 'M0,0 L10,4 L0,8 Z')
            .attr('class', 'dag-edge-arrow');

        this._computeLayout(width);
        this._renderEdges(workflow.edges);
        this._renderNodes(workflow.nodes, highlights);
        this._fitToView(width);
    }

    _computeLayout(containerWidth) {
        const { nodeWidth, nodeHeight, levelGap, nodeGap, padding } = this.options;
        const wf = this.workflow;

        // Build level map from server-side levelGroups or from node.level
        const levelGroups = wf.levelGroups || {};
        let levels = Object.keys(levelGroups).map(Number).sort((a, b) => a - b);

        if (levels.length === 0) {
            // Fallback: use node.level field
            const grouped = {};
            wf.nodes.forEach(n => {
                const l = n.level || 0;
                (grouped[l] = grouped[l] || []).push(n.id);
            });
            Object.keys(grouped).map(Number).sort((a, b) => a - b)
                .forEach(l => { levelGroups[l] = grouped[l]; });
            levels = Object.keys(levelGroups).map(Number).sort((a, b) => a - b);
        }

        // Sink floating roots (non-INPUT nodes with no predecessors) closer to
        // their successors so edges don't span across intermediate levels.
        this._sinkFloatingRoots(wf, levelGroups);
        levels = Object.keys(levelGroups).map(Number).sort((a, b) => a - b);

        // Position nodes: top-to-bottom, centered per level
        this.nodePositions = {};
        levels.forEach(level => {
            const ids = levelGroups[level];
            const totalWidth = ids.length * nodeWidth + (ids.length - 1) * nodeGap;
            const startX = (containerWidth - totalWidth) / 2;
            const y = padding + level * (nodeHeight + levelGap);

            ids.forEach((id, i) => {
                this.nodePositions[id] = {
                    x: startX + i * (nodeWidth + nodeGap),
                    y: y,
                    cx: startX + i * (nodeWidth + nodeGap) + nodeWidth / 2,
                    cy: y + nodeHeight / 2
                };
            });
        });
    }

    /**
     * Push non-INPUT root nodes down so they sit one level above their earliest
     * successor. This prevents long edges that visually cross intermediate levels.
     *
     * Example: guidelines (CONTEXT, no predecessors) connects only to output (level 2).
     * Without sinking, guidelines sits at level 0 and its edge crosses level 1.
     * After sinking, guidelines moves to level 1, sitting beside factual/analysis.
     */
    _sinkFloatingRoots(wf, levelGroups) {
        // Build node type lookup
        const nodeType = {};
        wf.nodes.forEach(n => { nodeType[n.id] = n.type; });

        // Build predecessor/successor maps
        const predecessors = {};
        const successors = {};
        wf.nodes.forEach(n => { predecessors[n.id] = []; successors[n.id] = []; });
        wf.edges.forEach(e => {
            predecessors[e.to].push(e.from);
            successors[e.from].push(e.to);
        });

        // Build current nodeId → level map
        const nodeLevel = {};
        for (const [lvl, ids] of Object.entries(levelGroups)) {
            ids.forEach(id => { nodeLevel[id] = Number(lvl); });
        }

        // Iteratively sink nodes until stable
        let changed = true;
        while (changed) {
            changed = false;
            for (const node of wf.nodes) {
                const id = node.id;
                // Only sink non-INPUT nodes that have no predecessors
                if (nodeType[id] === 'INPUT' || predecessors[id].length > 0) continue;
                if (successors[id].length === 0) continue;

                const minSuccLevel = Math.min(...successors[id].map(s => nodeLevel[s]));
                const targetLevel = minSuccLevel - 1;

                if (targetLevel > nodeLevel[id]) {
                    // Remove from old level
                    const oldLvl = nodeLevel[id];
                    levelGroups[oldLvl] = levelGroups[oldLvl].filter(x => x !== id);
                    if (levelGroups[oldLvl].length === 0) delete levelGroups[oldLvl];

                    // Add to target level
                    nodeLevel[id] = targetLevel;
                    if (!levelGroups[targetLevel]) levelGroups[targetLevel] = [];
                    levelGroups[targetLevel].push(id);
                    changed = true;
                }
            }
        }
    }

    _renderEdges(edges) {
        const { nodeWidth, nodeHeight } = this.options;
        const positions = this.nodePositions;

        edges.forEach(edge => {
            const from = positions[edge.from];
            const to = positions[edge.to];
            if (!from || !to) return;

            const x1 = from.cx;
            const y1 = from.y + nodeHeight;
            const x2 = to.cx;
            const y2 = to.y;
            const midY = (y1 + y2) / 2;

            this.g.append('path')
                .attr('d', `M${x1},${y1} C${x1},${midY} ${x2},${midY} ${x2},${y2}`)
                .attr('class', 'dag-edge')
                .attr('data-from', edge.from)
                .attr('data-to', edge.to)
                .attr('marker-end', 'url(#arrowhead)');
        });
    }

    _renderNodes(nodes, highlights) {
        const { nodeWidth, nodeHeight } = this.options;
        const positions = this.nodePositions;
        const tooltip = document.getElementById(this.tooltipId);

        nodes.forEach(node => {
            const pos = positions[node.id];
            if (!pos) return;

            const group = this.g.append('g')
                .attr('class', 'dag-node')
                .attr('data-node-id', node.id)
                .attr('transform', `translate(${pos.x}, ${pos.y})`);

            // Determine highlight class (for comparison view)
            let highlightClass = '';
            if (highlights) {
                if (highlights.shared && highlights.shared.has(node.id)) highlightClass = ' node-shared';
                else if (highlights.uniqueLeft && highlights.uniqueLeft.has(node.id)) highlightClass = ' node-unique';
                else if (highlights.uniqueRight && highlights.uniqueRight.has(node.id)) highlightClass = ' node-unique';
            }
            if (highlightClass) group.classed(highlightClass.trim(), true);

            // Draw shape based on type
            this._drawNodeShape(group, node, nodeWidth, nodeHeight);

            // Node label (ID)
            group.append('text')
                .attr('x', nodeWidth / 2)
                .attr('y', nodeHeight / 2 + 1)
                .attr('text-anchor', 'middle')
                .attr('dominant-baseline', 'middle')
                .text(node.id);

            // Type label above
            group.append('text')
                .attr('class', 'node-type-label')
                .attr('x', nodeWidth / 2)
                .attr('y', -6)
                .attr('text-anchor', 'middle')
                .text(node.type);

            // Tooltip events
            if (tooltip) {
                group.on('mouseenter', (event) => this._showTooltip(event, node, tooltip));
                group.on('mouseleave', () => { tooltip.classList.remove('visible'); });
            }
        });
    }

    _drawNodeShape(group, node, w, h) {
        const colors = {
            INPUT:   { fill: 'var(--green-bg)',  stroke: 'var(--green)' },
            OUTPUT:  { fill: 'rgba(88,166,255,0.15)', stroke: 'var(--accent)' },
            LLM:     { fill: 'var(--purple-bg)', stroke: 'var(--purple)' },
            REST:    { fill: 'var(--orange-bg)', stroke: 'var(--orange)' },
            CONTEXT: { fill: 'rgba(139,148,158,0.15)', stroke: 'var(--gray)' },
            TOOL:    { fill: 'var(--teal-bg)',   stroke: 'var(--teal)' },
            CUSTOM:  { fill: 'var(--yellow-bg)', stroke: 'var(--yellow)' }
        };
        const c = colors[node.type] || colors.CUSTOM;

        switch (node.type) {
            case 'LLM':
                // Hexagon
                const hx = w / 2, hy = h / 2;
                const pts = [
                    [w * 0.25, 0], [w * 0.75, 0], [w, hy],
                    [w * 0.75, h], [w * 0.25, h], [0, hy]
                ].map(p => p.join(',')).join(' ');
                group.append('polygon')
                    .attr('points', pts)
                    .attr('fill', c.fill)
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1.5);
                break;

            case 'REST':
                // Parallelogram
                const skew = 15;
                group.append('polygon')
                    .attr('points', `${skew},0 ${w},0 ${w - skew},${h} 0,${h}`)
                    .attr('fill', c.fill)
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1.5);
                break;

            case 'CONTEXT':
                // Note shape (folded corner)
                const fold = 12;
                group.append('path')
                    .attr('d', `M0,0 L${w - fold},0 L${w},${fold} L${w},${h} L0,${h} Z`)
                    .attr('fill', c.fill)
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1.5);
                group.append('path')
                    .attr('d', `M${w - fold},0 L${w - fold},${fold} L${w},${fold}`)
                    .attr('fill', 'none')
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1);
                break;

            case 'TOOL':
                // Gear-ish octagon
                const ox = 8;
                group.append('polygon')
                    .attr('points', `${ox},0 ${w - ox},0 ${w},${ox} ${w},${h - ox} ${w - ox},${h} ${ox},${h} 0,${h - ox} 0,${ox}`)
                    .attr('fill', c.fill)
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1.5);
                break;

            default:
                // Rounded rectangle (INPUT, OUTPUT, CUSTOM)
                group.append('rect')
                    .attr('width', w)
                    .attr('height', h)
                    .attr('rx', 8)
                    .attr('ry', 8)
                    .attr('fill', c.fill)
                    .attr('stroke', c.stroke)
                    .attr('stroke-width', 1.5);
                break;
        }
    }

    _showTooltip(event, node, tooltip) {
        let html = `<h4>${node.id}</h4>`;
        html += `<div class="tt-row"><span class="tt-label">Type</span><span class="tt-value">${node.type}</span></div>`;
        html += `<div class="tt-row"><span class="tt-label">Level</span><span class="tt-value">${node.level}</span></div>`;

        if (node.promptTemplate) html += `<div class="tt-row"><span class="tt-label">Prompt</span><span class="tt-value">${this._truncate(node.promptTemplate, 100)}</span></div>`;
        if (node.systemPrompt) html += `<div class="tt-row"><span class="tt-label">System</span><span class="tt-value">${this._truncate(node.systemPrompt, 80)}</span></div>`;
        if (node.url) html += `<div class="tt-row"><span class="tt-label">URL</span><span class="tt-value">${node.url}</span></div>`;
        if (node.method) html += `<div class="tt-row"><span class="tt-label">Method</span><span class="tt-value">${node.method}</span></div>`;
        if (node.toolName) html += `<div class="tt-row"><span class="tt-label">Tool</span><span class="tt-value">${node.toolName}</span></div>`;
        if (node.contextText) html += `<div class="tt-row"><span class="tt-label">Context</span><span class="tt-value">${this._truncate(node.contextText, 80)}</span></div>`;
        if (node.outputStrategy) html += `<div class="tt-row"><span class="tt-label">Output</span><span class="tt-value">${node.outputStrategy}</span></div>`;
        if (node.postProcessPrompt) html += `<div class="tt-row"><span class="tt-label">Post</span><span class="tt-value">${this._truncate(node.postProcessPrompt, 80)}</span></div>`;
        if (node.hasOutputHandler) html += `<div class="tt-row"><span class="tt-label">Handler</span><span class="tt-value">✓ Custom Function</span></div>`;
        if (node.hasHooks) html += `<div class="tt-row"><span class="tt-label">Hooks</span><span class="tt-value">✓ ${node.errorStrategy || 'FAIL_FAST'}</span></div>`;

        tooltip.innerHTML = html;
        tooltip.classList.add('visible');

        // Position tooltip, flipping upward if it would clip below the container
        const container = tooltip.parentElement;
        const containerRect = container.getBoundingClientRect();
        const mx = event.clientX - containerRect.left + 12;
        const my = event.clientY - containerRect.top + 12;

        tooltip.style.left = mx + 'px';
        tooltip.style.top = my + 'px';

        // After render, check if it overflows and flip if needed
        requestAnimationFrame(() => {
            const ttRect = tooltip.getBoundingClientRect();
            // Flip upward if bottom overflows
            if (ttRect.bottom > containerRect.bottom - 4) {
                const flippedY = event.clientY - containerRect.top - ttRect.height - 12;
                tooltip.style.top = Math.max(4, flippedY) + 'px';
            }
            // Flip left if right overflows
            if (ttRect.right > containerRect.right - 4) {
                const flippedX = event.clientX - containerRect.left - ttRect.width - 12;
                tooltip.style.left = Math.max(4, flippedX) + 'px';
            }
        });
    }

    _truncate(s, max) {
        if (!s) return '';
        const escaped = s.replace(/</g, '&lt;').replace(/>/g, '&gt;');
        return escaped.length <= max ? escaped : escaped.substring(0, max) + '…';
    }

    _fitToView(containerWidth) {
        // Auto-fit the DAG into the visible area
        const bbox = this.g.node().getBBox();
        if (bbox.width === 0 || bbox.height === 0) return;

        const svgHeight = Math.max(bbox.height + 80, 400);
        this.svg.attr('height', svgHeight);

        const scale = Math.min(
            (containerWidth - 40) / bbox.width,
            (svgHeight - 40) / bbox.height,
            1.2
        );
        const tx = (containerWidth - bbox.width * scale) / 2 - bbox.x * scale;
        const ty = (svgHeight - bbox.height * scale) / 2 - bbox.y * scale;

        this.svg.call(this.zoom.transform,
            d3.zoomIdentity.translate(tx, ty).scale(scale));
    }

    // ── Status overlay updates (called from execution-panel) ────

    setNodeStatus(nodeId, status, durationMs) {
        const group = this.g.select(`[data-node-id="${nodeId}"]`);
        if (group.empty()) return;

        // Remove old status classes
        group.classed('status-pending', false)
             .classed('status-running', false)
             .classed('status-completed', false)
             .classed('status-failed', false)
             .classed('status-skipped', false);

        group.classed('status-' + status.toLowerCase(), true);

        // Show duration badge on completion
        if (status === 'COMPLETED' && durationMs !== undefined) {
            group.selectAll('.node-duration').remove();
            const pos = this.nodePositions[nodeId];
            if (pos) {
                group.append('text')
                    .attr('class', 'node-duration')
                    .attr('x', this.options.nodeWidth / 2)
                    .attr('y', this.options.nodeHeight + 16)
                    .attr('text-anchor', 'middle')
                    .text(durationMs + 'ms');
            }
        }
    }

    setEdgeStatus(from, to, status) {
        this.g.selectAll('.dag-edge')
            .filter(function() {
                return this.getAttribute('data-from') === from &&
                       this.getAttribute('data-to') === to;
            })
            .classed('status-running', status === 'RUNNING')
            .classed('status-completed', status === 'COMPLETED')
            .classed('status-failed', status === 'FAILED');
    }

    clearAllStatuses() {
        this.g.selectAll('.dag-node')
            .classed('status-pending', false)
            .classed('status-running', false)
            .classed('status-completed', false)
            .classed('status-failed', false)
            .classed('status-skipped', false);

        this.g.selectAll('.node-duration').remove();

        this.g.selectAll('.dag-edge')
            .classed('status-running', false)
            .classed('status-completed', false)
            .classed('status-failed', false);
    }

    // ── Zoom controls ───────────────────────────────────────────

    zoomIn() {
        this.svg.transition().duration(300).call(this.zoom.scaleBy, 1.3);
    }

    zoomOut() {
        this.svg.transition().duration(300).call(this.zoom.scaleBy, 0.7);
    }

    resetZoom() {
        const width = this.svg.node().parentElement.clientWidth || 800;
        this._fitToView(width);
    }
}

// Export for use in other modules
window.DagRenderer = DagRenderer;

