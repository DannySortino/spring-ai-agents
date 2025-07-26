# MCP Client Setup Guide

## Overview

This guide explains how to create and use `mcp.json` configuration files to connect to the MCP (Model Context Protocol) agents in your Spring AI Agent Platform. The platform exposes three MCP servers that external clients can connect to and use as tools.

## Available MCP Agents

Your Spring AI Agent Platform exposes the following MCP servers:

| Agent | Port | Base URL | Description |
|-------|------|----------|-------------|
| **simpleAgent** | 8087 | `/api/agents/simpleAgent/mcp` | Simple assistant agent for general queries |
| **billingAgent** | 8088 | `/api/agents/billingAgent/mcp` | Billing expert with routing and tool capabilities |
| **complexAgent** | 8089 | `/api/agents/complexAgent/mcp` | Complex analysis agent with parallel processing |

## MCP Configuration Files

We provide four different `mcp.json` configuration files:

### 1. Individual Agent Configurations

- **`simple-agent-mcp.json`** - Connect to simple agent only
- **`billing-agent-mcp.json`** - Connect to billing agent only  
- **`complex-agent-mcp.json`** - Connect to complex agent only

### 2. Comprehensive Configuration

- **`all-agents-mcp.json`** - Connect to all agents simultaneously

## MCP.json Structure

### Basic Structure

```json
{
  "mcpServers": {
    "agentName": "command-line tool configurations"
  },
  "servers": {
    "agentName": "direct HTTP connection configurations"
  },
  "client": {
    "name": "client capabilities and metadata"
  },
  "global": {
    "settings": "global settings (optional)"
  }
}
```

### Key Sections Explained

#### 1. mcpServers Section
Used for command-line tools and external MCP clients:

```json
{
  "mcpServers": {
    "agentName": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "-H", "Content-Type: application/json",
        "-H", "Accept: text/event-stream",
        "http://localhost:PORT/api/agents/AGENT_NAME/mcp"
      ],
      "env": {
        "MCP_SERVER_URL": "http://localhost:PORT",
        "MCP_BASE_PATH": "/api/agents/AGENT_NAME/mcp"
      }
    }
  }
}
```

#### 2. servers Section
Direct HTTP connection configuration:

```json
{
  "servers": {
    "agentName": {
      "url": "http://localhost:PORT/api/agents/AGENT_NAME/mcp",
      "transport": {
        "type": "sse",
        "endpoint": "/sse",
        "messageEndpoint": "/message"
      },
      "description": "Agent description",
      "capabilities": ["tools", "prompts", "resources"],
      "timeout": 30000,
      "retries": 3
    }
  }
}
```

#### 3. client Section
Client capabilities and metadata:

```json
{
  "client": {
    "name": "Your-MCP-Client-Name",
    "version": "1.0.0",
    "capabilities": [
      "sampling",
      "notifications",
      "tool_calling"
    ]
  }
}
```

## Setup Instructions

### Prerequisites

1. **Start the Spring AI Agent Platform**:
   ```bash
   mvn spring-boot:run
   ```
   The main application runs on port 8086, and MCP servers start on ports 8087-8089.

2. **Verify MCP servers are running**:
   ```bash
   curl http://localhost:8087/api/agents/simpleAgent/mcp
   curl http://localhost:8088/api/agents/billingAgent/mcp
   curl http://localhost:8089/api/agents/complexAgent/mcp
   ```

### Using Individual Agent Configurations

#### Connect to Simple Agent
```bash
# Copy the configuration
cp docs/mcp-configs/simple-agent-mcp.json ~/.mcp/simple-agent.json

# Use with your MCP client
your-mcp-client --config ~/.mcp/simple-agent.json
```

#### Connect to Billing Agent
```bash
# Copy the configuration
cp docs/mcp-configs/billing-agent-mcp.json ~/.mcp/billing-agent.json

# Use with your MCP client
your-mcp-client --config ~/.mcp/billing-agent.json
```

#### Connect to Complex Agent
```bash
# Copy the configuration
cp docs/mcp-configs/complex-agent-mcp.json ~/.mcp/complex-agent.json

# Use with your MCP client
your-mcp-client --config ~/.mcp/complex-agent.json
```

### Using All Agents Configuration

```bash
# Copy the comprehensive configuration
cp docs/mcp-configs/all-agents-mcp.json ~/.mcp/all-agents.json

# Use with your MCP client
your-mcp-client --config ~/.mcp/all-agents.json
```

## Agent Capabilities

### Simple Agent
- **Capabilities**: Basic conversation, general assistance
- **Use Cases**: Simple Q&A, general help requests
- **Example Usage**:
  ```json
  {
    "method": "tools/call",
    "params": {
      "name": "simpleAgent",
      "arguments": {
        "input": "Hello, can you help me with a question?"
      }
    }
  }
  ```

### Billing Agent
- **Capabilities**: Invoice processing, dispute handling, payment processing
- **Available Tools**:
  - `invoiceTool` - Fetch invoice details
  - `disputeTool` - Initiate dispute processes
  - `paymentTool` - Process payments
- **Routes**: invoice, dispute, payment
- **Example Usage**:
  ```json
  {
    "method": "tools/call",
    "params": {
      "name": "invoiceTool",
      "arguments": {
        "invoiceId": "INV-12345"
      }
    }
  }
  ```

### Complex Agent
- **Capabilities**: Parallel processing, complex analysis, reporting
- **Features**: Multi-task execution, result aggregation
- **Concurrency**: Up to 10 parallel tasks
- **Example Usage**:
  ```json
  {
    "method": "tools/call",
    "params": {
      "name": "complexAgent",
      "arguments": {
        "input": "Analyze this complex dataset and provide insights"
      }
    }
  }
  ```

## Environment Variables

You can customize the MCP connections using environment variables:

```bash
# Override default URLs
export MCP_SIMPLE_AGENT_URL=http://localhost:8087
export MCP_BILLING_AGENT_URL=http://localhost:8088
export MCP_COMPLEX_AGENT_URL=http://localhost:8089

# Override base paths
export MCP_SIMPLE_AGENT_PATH=/api/agents/simpleAgent/mcp
export MCP_BILLING_AGENT_PATH=/api/agents/billingAgent/mcp
export MCP_COMPLEX_AGENT_PATH=/api/agents/complexAgent/mcp

# Connection settings
export MCP_CONNECTION_TIMEOUT=30000
export MCP_RETRY_COUNT=3
```

## Integration Examples

### Python MCP Client Example

```python
import json
import requests
from mcp_client import MCPClient

# Load configuration
with open('docs/mcp-configs/all-agents-mcp.json', 'r') as f:
    config = json.load(f)

# Initialize MCP client
client = MCPClient(config)

# Connect to billing agent and call invoice tool
response = client.call_tool(
    server="billingAgent",
    tool="invoiceTool",
    arguments={"invoiceId": "INV-12345"}
)

print(response)
```

### Node.js MCP Client Example

```javascript
const fs = require('fs');
const { MCPClient } = require('mcp-client');

// Load configuration
const config = JSON.parse(
  fs.readFileSync('docs/mcp-configs/all-agents-mcp.json', 'utf8')
);

// Initialize MCP client
const client = new MCPClient(config);

// Connect to simple agent
client.callTool({
  server: 'simpleAgent',
  arguments: {
    input: 'What is the weather like today?'
  }
}).then(response => {
  console.log(response);
});
```

### cURL Examples

#### Call Simple Agent
```bash
curl -X POST http://localhost:8087/api/agents/simpleAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "arguments": {
        "input": "Hello, how can you help me?"
      }
    }
  }'
```

#### Call Billing Agent Invoice Tool
```bash
curl -X POST http://localhost:8088/api/agents/billingAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "invoiceTool",
      "arguments": {
        "invoiceId": "INV-12345"
      }
    }
  }'
```

#### Call Complex Agent
```bash
curl -X POST http://localhost:8089/api/agents/complexAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "arguments": {
        "input": "Perform complex analysis on this data"
      }
    }
  }'
```

## Configuration Customization

### Custom Timeouts
Modify timeout values in your mcp.json:

```json
{
  "servers": {
    "agentName": {
      "timeout": 60000,
      "retries": 5
    }
  }
}
```

### Custom Client Capabilities
Add specific capabilities your client supports:

```json
{
  "client": {
    "name": "My-Custom-Client",
    "version": "2.0.0",
    "capabilities": [
      "sampling",
      "notifications",
      "tool_calling",
      "streaming",
      "batch_processing"
    ]
  }
}
```

### Load Balancing (All Agents Config)
The all-agents configuration includes load balancing:

```json
{
  "client": {
    "loadBalancing": {
      "strategy": "round_robin",
      "healthCheck": {
        "enabled": true,
        "interval": 30000,
        "timeout": 5000
      }
    }
  }
}
```

## Next Steps

1. **Choose your configuration**: Select individual agent configs or the comprehensive all-agents config
2. **Copy to your MCP client directory**: Place the chosen config file where your MCP client can access it
3. **Start the Spring AI Agent Platform**: Ensure all MCP servers are running
4. **Test the connection**: Use the provided examples to verify connectivity
5. **Integrate with your application**: Use the MCP agents as tools in your AI workflows

For troubleshooting and advanced configuration options, see the [MCP Troubleshooting Guide](MCP_TROUBLESHOOTING_GUIDE.md).