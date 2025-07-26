# Agent MCP Server Usage Guide

This document explains how to connect to and use the individual MCP servers exposed by each configured agent.

## Overview

Each agent configured with `mcpServer.enabled: true` is exposed as a separate, connectable MCP server. External MCP clients can connect to these servers and interact with the agents using the standard Model Context Protocol (MCP).

## Available Agent MCP Servers

Based on the current configuration, the following agents are exposed as MCP servers:

### 1. Simple Agent MCP Server
- **Agent Name**: `simpleAgent`
- **Base URL**: `/api/agents/simpleAgent/mcp`
- **Port**: 8087 (configured) or 8086 (default application port)
- **Description**: "Simple assistant agent MCP server"

### 2. Billing Agent MCP Server
- **Agent Name**: `billingAgent`
- **Base URL**: `/api/agents/billingAgent/mcp`
- **Port**: 8088 (configured) or 8086 (default application port)
- **Description**: "Billing expert agent MCP server with routing capabilities"

### 3. Complex Agent MCP Server
- **Agent Name**: `complexAgent`
- **Base URL**: `/api/agents/complexAgent/mcp`
- **Port**: 8089 (configured) or 8086 (default application port)
- **Description**: "Complex analysis agent MCP server with parallel processing"

## MCP Protocol Endpoints

Each agent MCP server exposes the following standard MCP protocol endpoints:

### Server Information
```
GET /api/agents/{agentName}/mcp/info
```
Returns server information including capabilities, version, and description.

**Example Response:**
```json
{
  "name": "simpleAgent-mcp-server",
  "version": "1.0.0",
  "description": "Simple assistant agent MCP server",
  "agent": "simpleAgent",
  "capabilities": {
    "tools": {"listChanged": true},
    "resources": {"subscribe": true, "listChanged": true},
    "prompts": {"listChanged": true}
  }
}
```

### Tools List
```
GET /api/agents/{agentName}/mcp/tools/list
```
Returns the list of available tools (the agent itself).

**Example Response:**
```json
{
  "tools": [
    {
      "name": "simpleAgent",
      "description": "Simple assistant agent MCP server",
      "inputSchema": {
        "type": "object",
        "properties": {
          "input": {
            "type": "string",
            "description": "Input text for the agent"
          },
          "context": {
            "type": "object",
            "description": "Additional context parameters",
            "additionalProperties": true
          }
        },
        "required": ["input"]
      }
    }
  ]
}
```

### Tool Call
```
POST /api/agents/{agentName}/mcp/tools/call
```
Executes the agent with the provided input.

**Request Body:**
```json
{
  "name": "simpleAgent",
  "arguments": {
    "input": "Hello, how can you help me?",
    "context": {
      "userId": "user123",
      "sessionId": "session456"
    }
  }
}
```

**Example Response:**
```json
{
  "isError": false,
  "content": [
    {
      "type": "text",
      "text": "Hello! I'm a helpful assistant. I can answer questions, provide information, and help with various tasks. How can I assist you today?"
    }
  ]
}
```

### Health Check
```
GET /api/agents/{agentName}/mcp/health
```
Returns the health status of the agent MCP server.

**Example Response:**
```json
{
  "status": "healthy",
  "agent": "simpleAgent",
  "timestamp": 1703123456789
}
```

### Server-Sent Events (SSE)
```
GET /api/agents/{agentName}/mcp/sse?clientId=optional
```
Establishes an SSE connection for real-time communication.

```
POST /api/agents/{agentName}/mcp/message?clientId=optional
```
Sends messages through the SSE connection.

## Discovery Endpoint

To discover all available agent MCP servers:

```
GET /api/agents/mcp/list
```

**Example Response:**
```json
{
  "agentMcpServers": [
    {
      "agent": "simpleAgent",
      "baseUrl": "/api/agents/simpleAgent/mcp",
      "sseEndpoint": "/api/agents/simpleAgent/mcp/sse",
      "description": "Simple assistant agent MCP server",
      "port": 8087
    },
    {
      "agent": "billingAgent",
      "baseUrl": "/api/agents/billingAgent/mcp",
      "sseEndpoint": "/api/agents/billingAgent/mcp/sse",
      "description": "Billing expert agent MCP server with routing capabilities",
      "port": 8088
    },
    {
      "agent": "complexAgent",
      "baseUrl": "/api/agents/complexAgent/mcp",
      "sseEndpoint": "/api/agents/complexAgent/mcp/sse",
      "description": "Complex analysis agent MCP server with parallel processing",
      "port": 8089
    }
  ]
}
```

## Connecting with MCP Clients

### Using curl

1. **Get server info:**
```bash
curl -X GET http://localhost:8086/api/agents/simpleAgent/mcp/info
```

2. **List available tools:**
```bash
curl -X GET http://localhost:8086/api/agents/simpleAgent/mcp/tools/list
```

3. **Call the agent:**
```bash
curl -X POST http://localhost:8086/api/agents/simpleAgent/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "simpleAgent",
    "arguments": {
      "input": "What is the weather like today?",
      "context": {}
    }
  }'
```

### Using MCP Client Libraries

You can use any MCP-compatible client library to connect to these servers. The servers follow the standard MCP protocol specification.

**Example with Python MCP client:**
```python
import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def main():
    # Connect to the agent MCP server
    server_params = StdioServerParameters(
        command="curl",
        args=["-X", "GET", "http://localhost:8086/api/agents/simpleAgent/mcp/info"]
    )
    
    async with stdio_client(server_params) as (read, write):
        async with ClientSession(read, write) as session:
            # Initialize the connection
            await session.initialize()
            
            # List available tools
            tools = await session.list_tools()
            print(f"Available tools: {tools}")
            
            # Call a tool
            result = await session.call_tool("simpleAgent", {
                "input": "Hello, how are you?",
                "context": {}
            })
            print(f"Result: {result}")

if __name__ == "__main__":
    asyncio.run(main())
```

## Configuration

To add a new agent as an MCP server, add the `mcpServer` configuration to your agent definition in `application.yml`:

```yaml
app:
  agents:
    - name: myNewAgent
      systemPrompt: "You are a specialized assistant"
      workflow:
        type: chain
        chain:
          - prompt: "Process: {input}"
      mcpServer:
        enabled: true
        port: 8090  # Optional: specific port
        description: "My new agent MCP server"
        baseUrl: "/api/agents/myNewAgent/mcp"  # Optional: custom base URL
```

## Error Handling

The MCP servers return appropriate HTTP status codes:

- **200 OK**: Successful operation
- **400 Bad Request**: Invalid request format or missing required parameters
- **404 Not Found**: Agent not found or MCP server not enabled for the agent
- **500 Internal Server Error**: Server-side error during agent execution

Error responses follow the MCP protocol format:
```json
{
  "isError": true,
  "error": "Error description here"
}
```

## Troubleshooting

1. **Agent not found (404)**: Ensure the agent is configured with `mcpServer.enabled: true`
2. **Connection refused**: Verify the application is running on the correct port
3. **Tool execution errors**: Check the agent configuration and system prompts
4. **SSE connection issues**: Ensure WebSocket/SSE support is enabled in your client

## Integration with AI Applications

These agent MCP servers can be integrated with various AI applications and frameworks that support the Model Context Protocol, including:

- Claude Desktop
- Custom MCP clients
- AI agent frameworks
- Workflow automation tools

Each agent becomes a reusable, connectable component that can be accessed by multiple clients simultaneously.