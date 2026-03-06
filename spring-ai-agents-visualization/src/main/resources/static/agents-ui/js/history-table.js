/**
 * History table — fetches and renders execution history with filtering.
 */

function populateAgentFilter() {
    fetch('/agents-ui/api/agents')
        .then(res => res.json())
        .then(agents => {
            const select = document.getElementById('filter-agent');
            agents.forEach(a => {
                const opt = document.createElement('option');
                opt.value = a.name;
                opt.textContent = a.name;
                select.appendChild(opt);
            });
        })
        .catch(() => {});
}

function loadHistory() {
    const agent = document.getElementById('filter-agent').value;
    const status = document.getElementById('filter-status').value;
    const query = document.getElementById('filter-query').value;

    const params = new URLSearchParams();
    if (agent) params.set('agent', agent);
    if (status) params.set('status', status);
    if (query) params.set('q', query);
    params.set('size', '100');

    fetch('/agents-ui/api/executions?' + params.toString())
        .then(res => res.json())
        .then(records => renderHistoryTable(records))
        .catch(err => {
            document.getElementById('history-tbody').innerHTML =
                `<tr><td colspan="7" style="color:var(--red);padding:20px;">Error loading: ${err.message}</td></tr>`;
        });
}

function renderHistoryTable(records) {
    const tbody = document.getElementById('history-tbody');

    if (!records || records.length === 0) {
        tbody.innerHTML = `
            <tr><td colspan="7" class="empty-state" style="padding:40px;">
                <h3>No executions found</h3>
                <p>Run agents from the dashboard to start recording history.</p>
            </td></tr>`;
        return;
    }

    tbody.innerHTML = records.map(r => {
        const statusBadge = r.status === 'COMPLETED'
            ? '<span class="badge badge-green">COMPLETED</span>'
            : r.status === 'FAILED'
                ? '<span class="badge badge-red">FAILED</span>'
                : '<span class="badge badge-yellow">RUNNING</span>';

        const inputPreview = truncate(r.input || '', 60);
        const time = r.startedAt ? new Date(r.startedAt).toLocaleString() : '';
        const duration = r.durationMs ? r.durationMs + 'ms' : '—';

        return `<tr onclick="showExecutionDetail('${r.id}')" style="cursor:pointer;">
            <td>${statusBadge}</td>
            <td class="mono">${r.agentName || ''}</td>
            <td class="mono">${r.workflowName || '—'}</td>
            <td class="text-truncate">${escapeHtml(inputPreview)}</td>
            <td class="mono">${duration}</td>
            <td style="font-size:12px;color:var(--text-muted);">${time}</td>
            <td>
                <svg viewBox="0 0 16 16" width="14" height="14" fill="var(--text-muted)"><path d="M6.22 3.22a.75.75 0 0 1 1.06 0l4.25 4.25a.75.75 0 0 1 0 1.06l-4.25 4.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042L9.94 8 6.22 4.28a.75.75 0 0 1 0-1.06Z"/></svg>
            </td>
        </tr>`;
    }).join('');
}

function showExecutionDetail(id) {
    const detail = document.getElementById('history-detail');
    const content = document.getElementById('history-detail-content');

    fetch(`/agents-ui/api/executions/${id}`)
        .then(res => res.json())
        .then(record => {
            let html = `
                <div style="display:grid;grid-template-columns:auto 1fr;gap:6px 16px;font-size:13px;margin-bottom:16px;">
                    <span style="color:var(--text-muted);">ID</span><span class="mono">${record.id}</span>
                    <span style="color:var(--text-muted);">Agent</span><span class="mono">${record.agentName}</span>
                    <span style="color:var(--text-muted);">Workflow</span><span class="mono">${record.workflowName || '—'}</span>
                    <span style="color:var(--text-muted);">Status</span><span>${record.status}</span>
                    <span style="color:var(--text-muted);">Duration</span><span class="mono">${record.durationMs || 0}ms</span>
                </div>
                <div style="margin-bottom:12px;">
                    <strong style="font-size:13px;">Input</strong>
                    <div class="exec-output" style="margin-top:4px;max-height:150px;">${escapeHtml(record.input || '')}</div>
                </div>
                <div style="margin-bottom:12px;">
                    <strong style="font-size:13px;">Output</strong>
                    <div class="exec-output" style="margin-top:4px;">${escapeHtml(record.output || '(none)')}</div>
                </div>`;

            if (record.nodeStatuses && Object.keys(record.nodeStatuses).length > 0) {
                html += '<strong style="font-size:13px;">Node Statuses</strong>';
                html += '<table class="data-table" style="margin-top:8px;"><thead><tr><th>Node</th><th>Type</th><th>Status</th><th>Duration</th></tr></thead><tbody>';
                Object.entries(record.nodeStatuses).forEach(([nodeId, ns]) => {
                    html += `<tr>
                        <td class="mono">${nodeId}</td>
                        <td>${ns.nodeType || ''}</td>
                        <td>${ns.status}</td>
                        <td class="mono">${ns.durationMs || 0}ms</td>
                    </tr>`;
                });
                html += '</tbody></table>';
            }

            content.innerHTML = html;
            detail.style.display = 'block';
            detail.scrollIntoView({ behavior: 'smooth' });
        })
        .catch(err => {
            content.innerHTML = `<p style="color:var(--red);">Error: ${err.message}</p>`;
            detail.style.display = 'block';
        });
}

function clearHistory() {
    if (!confirm('Clear all execution history?')) return;
    fetch('/agents-ui/api/executions', { method: 'DELETE' })
        .then(() => {
            loadHistory();
            document.getElementById('history-detail').style.display = 'none';
        })
        .catch(err => alert('Error: ' + err.message));
}

function truncate(s, max) {
    return s.length <= max ? s : s.substring(0, max) + '…';
}

function escapeHtml(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

