/**
 * Analytics charts — D3-powered bar charts for performance stats.
 */

function loadAnalytics() {
    fetch('/agents-ui/api/analytics')
        .then(res => res.json())
        .then(stats => {
            if (!stats || stats.length === 0) {
                document.getElementById('analytics-overview').style.display = 'none';
                document.getElementById('analytics-empty').style.display = 'block';
                return;
            }
            document.getElementById('analytics-empty').style.display = 'none';
            document.getElementById('analytics-overview').style.display = 'block';
            renderOverviewChart(stats);
        })
        .catch(err => {
            console.error('Analytics load error:', err);
            document.getElementById('analytics-empty').style.display = 'block';
        });
}

function renderOverviewChart(stats) {
    const svgEl = document.getElementById('overview-chart');
    const container = svgEl.parentElement;
    const width = container.clientWidth - 40 || 600;
    const height = 280;
    const margin = { top: 20, right: 30, bottom: 60, left: 60 };
    const inner = { w: width - margin.left - margin.right, h: height - margin.top - margin.bottom };

    d3.select(svgEl).selectAll('*').remove();
    const svg = d3.select(svgEl)
        .attr('width', width)
        .attr('height', height);

    const g = svg.append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

    // Scales
    const x0 = d3.scaleBand()
        .domain(stats.map(s => s.agentName))
        .range([0, inner.w])
        .padding(0.3);

    const x1 = d3.scaleBand()
        .domain(['avg', 'p95', 'max'])
        .range([0, x0.bandwidth()])
        .padding(0.05);

    const maxVal = d3.max(stats, s => Math.max(s.avgDurationMs, s.p95DurationMs, s.maxDurationMs)) || 100;
    const y = d3.scaleLinear()
        .domain([0, maxVal * 1.1])
        .range([inner.h, 0]);

    // Axes
    g.append('g')
        .attr('class', 'chart-axis')
        .attr('transform', `translate(0,${inner.h})`)
        .call(d3.axisBottom(x0))
        .selectAll('text')
        .style('font-family', 'var(--font-mono)')
        .style('font-size', '11px')
        .attr('transform', 'rotate(-20)')
        .attr('text-anchor', 'end');

    g.append('g')
        .attr('class', 'chart-axis')
        .call(d3.axisLeft(y).ticks(5).tickFormat(d => d + 'ms'));

    // Grid lines
    g.append('g')
        .attr('class', 'chart-grid')
        .call(d3.axisLeft(y).ticks(5).tickSize(-inner.w).tickFormat(''));

    // Colors
    const colors = { avg: 'var(--accent)', p95: 'var(--orange)', max: 'var(--red)' };

    // Grouped bars
    stats.forEach(s => {
        const agentG = g.append('g')
            .attr('transform', `translate(${x0(s.agentName)},0)`);

        [
            { key: 'avg', val: s.avgDurationMs },
            { key: 'p95', val: s.p95DurationMs },
            { key: 'max', val: s.maxDurationMs }
        ].forEach(d => {
            agentG.append('rect')
                .attr('class', 'chart-bar bar-' + d.key)
                .attr('x', x1(d.key))
                .attr('y', y(d.val))
                .attr('width', x1.bandwidth())
                .attr('height', inner.h - y(d.val))
                .attr('fill', colors[d.key])
                .attr('rx', 2)
                .style('cursor', 'pointer')
                .on('click', () => drillDown(s.agentName));
        });

        // Run count label
        agentG.append('text')
            .attr('x', x0.bandwidth() / 2)
            .attr('y', inner.h + 14)
            .attr('text-anchor', 'middle')
            .style('font-size', '10px')
            .style('fill', 'var(--text-muted)')
            .text(s.totalRuns + ' runs');
    });

    // Legend
    const legend = svg.append('g')
        .attr('transform', `translate(${width - 180}, ${margin.top})`);

    [
        { label: 'Avg', color: colors.avg },
        { label: 'P95', color: colors.p95 },
        { label: 'Max', color: colors.max }
    ].forEach((d, i) => {
        legend.append('rect')
            .attr('x', i * 55)
            .attr('width', 10)
            .attr('height', 10)
            .attr('rx', 2)
            .attr('fill', d.color);
        legend.append('text')
            .attr('x', i * 55 + 14)
            .attr('y', 9)
            .style('font-size', '11px')
            .style('fill', 'var(--text-secondary)')
            .text(d.label);
    });
}

function drillDown(agentName) {
    fetch(`/agents-ui/api/analytics/${encodeURIComponent(agentName)}`)
        .then(res => res.json())
        .then(stats => {
            document.getElementById('drill-agent-name').textContent = agentName;
            document.getElementById('agent-drill-down').style.display = 'block';
            renderNodeChart(stats);
        })
        .catch(err => console.error('Drill-down error:', err));
}

function renderNodeChart(stats) {
    if (!stats.nodeStats || stats.nodeStats.length === 0) return;

    const svgEl = document.getElementById('node-chart');
    const container = svgEl.parentElement;
    const width = container.clientWidth - 40 || 600;
    const height = 280;
    const margin = { top: 20, right: 30, bottom: 80, left: 60 };
    const inner = { w: width - margin.left - margin.right, h: height - margin.top - margin.bottom };

    d3.select(svgEl).selectAll('*').remove();
    const svg = d3.select(svgEl)
        .attr('width', width)
        .attr('height', height);

    const g = svg.append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

    const nodeStats = stats.nodeStats;

    const x = d3.scaleBand()
        .domain(nodeStats.map(n => n.nodeId))
        .range([0, inner.w])
        .padding(0.3);

    const maxVal = d3.max(nodeStats, n => n.maxMs) || 100;
    const y = d3.scaleLinear()
        .domain([0, maxVal * 1.1])
        .range([inner.h, 0]);

    g.append('g')
        .attr('class', 'chart-axis')
        .attr('transform', `translate(0,${inner.h})`)
        .call(d3.axisBottom(x))
        .selectAll('text')
        .style('font-family', 'var(--font-mono)')
        .style('font-size', '10px')
        .attr('transform', 'rotate(-30)')
        .attr('text-anchor', 'end');

    g.append('g')
        .attr('class', 'chart-axis')
        .call(d3.axisLeft(y).ticks(5).tickFormat(d => d + 'ms'));

    g.append('g')
        .attr('class', 'chart-grid')
        .call(d3.axisLeft(y).ticks(5).tickSize(-inner.w).tickFormat(''));

    // Avg bar with max whisker
    nodeStats.forEach(n => {
        // Avg bar
        g.append('rect')
            .attr('x', x(n.nodeId))
            .attr('y', y(n.avgMs))
            .attr('width', x.bandwidth())
            .attr('height', inner.h - y(n.avgMs))
            .attr('fill', 'var(--accent)')
            .attr('rx', 2);

        // Max whisker
        const cx = x(n.nodeId) + x.bandwidth() / 2;
        g.append('line')
            .attr('x1', cx).attr('x2', cx)
            .attr('y1', y(n.maxMs)).attr('y2', y(n.avgMs))
            .attr('stroke', 'var(--red)')
            .attr('stroke-width', 1.5);
        g.append('line')
            .attr('x1', cx - 4).attr('x2', cx + 4)
            .attr('y1', y(n.maxMs)).attr('y2', y(n.maxMs))
            .attr('stroke', 'var(--red)')
            .attr('stroke-width', 2);

        // Type label
        g.append('text')
            .attr('x', cx)
            .attr('y', inner.h + 12)
            .attr('text-anchor', 'middle')
            .style('font-size', '9px')
            .style('fill', 'var(--text-muted)')
            .text(n.nodeType);
    });
}

