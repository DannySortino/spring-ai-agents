/**
 * AgentsWebSocket — STOMP over SockJS client for live execution events.
 */
class AgentsWebSocket {
    constructor(endpoint) {
        this.endpoint = endpoint || '/agents-ui/ws';
        this.stompClient = null;
        this.listeners = [];
        this.agentListeners = {};
        this.connected = false;
        this.reconnectDelay = 2000;
    }

    connect() {
        try {
            const socket = new SockJS(this.endpoint);
            this.stompClient = new StompJs.Client({
                webSocketFactory: () => socket,
                reconnectDelay: this.reconnectDelay,
                onConnect: () => {
                    this.connected = true;
                    console.log('[AgentsWS] Connected');

                    // Subscribe to all events
                    this.stompClient.subscribe('/topic/executions/all', (message) => {
                        const event = JSON.parse(message.body);
                        this.listeners.forEach(fn => fn(event));
                    });

                    // Re-subscribe agent-specific topics
                    Object.keys(this.agentListeners).forEach(agent => {
                        this._subscribeAgent(agent);
                    });
                },
                onDisconnect: () => {
                    this.connected = false;
                    console.log('[AgentsWS] Disconnected');
                },
                onStompError: (frame) => {
                    console.error('[AgentsWS] Error:', frame.headers.message);
                }
            });

            this.stompClient.activate();
        } catch (e) {
            console.warn('[AgentsWS] Connection failed:', e.message);
        }
    }

    disconnect() {
        if (this.stompClient) {
            this.stompClient.deactivate();
            this.connected = false;
        }
    }

    /**
     * Register a callback for all execution events.
     */
    onEvent(callback) {
        this.listeners.push(callback);
    }

    /**
     * Register a callback for events from a specific agent.
     */
    onAgentEvent(agentName, callback) {
        if (!this.agentListeners[agentName]) {
            this.agentListeners[agentName] = [];
            if (this.connected) this._subscribeAgent(agentName);
        }
        this.agentListeners[agentName].push(callback);
    }

    _subscribeAgent(agentName) {
        this.stompClient.subscribe('/topic/executions/' + agentName, (message) => {
            const event = JSON.parse(message.body);
            (this.agentListeners[agentName] || []).forEach(fn => fn(event));
        });
    }
}

window.AgentsWebSocket = AgentsWebSocket;

