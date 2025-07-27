# Spring AI Agent Platform

A powerful, configuration-driven platform for building sophisticated AI agents with complex workflow patterns, MCP (Model Context Protocol) integration, and advanced dependency management.

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- OpenAI API key (or compatible LLM provider)

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd spring-ai-agents
```

2. Set your OpenAI API key:
```bash
export OPENAI_API_KEY=your-api-key-here
```

3. Build and run:
```bash
mvn clean install
mvn spring-boot:run
```

The application will start on `http://localhost:8080` by default.

## 🏗️ Architecture Overview

The Spring AI Agent Platform is built around a **unified graph-based workflow system** that supports:

- **Graph Workflows**: Complex dependency management with arbitrary node relationships (A→B, B→C, A→C)
- **Orchestrator Workflows**: Manager-worker patterns with specialized agents
- **Routing Workflows**: Content-based request routing to different handlers
- **Parallel Execution**: Independent nodes execute concurrently when dependencies are satisfied
- **MCP Integration**: Model Context Protocol for external tool integration

## 📋 Key Features

### Workflow Types

| Type | Description | Use Cases |
|------|-------------|-----------|
| **Graph** | Dependency-based execution with parallel processing | Complex data pipelines, multi-step analysis |
| **Orchestrator** | Manager coordinates specialized workers | Enterprise workflows, role-based processing |
| **Routing** | Content-based request routing | Multi-purpose agents, request classification |

### Advanced Capabilities

- ✅ **Cycle Detection**: Prevents infinite loops in workflow graphs
- ✅ **Topological Sorting**: Automatic execution order determination  
- ✅ **Context Management**: Fine-grained control over data flow between steps
- ✅ **Conditional Logic**: If/then/else branching within workflows
- ✅ **Tool Integration**: MCP-based external tool calling
- ✅ **Retry Mechanisms**: Configurable retry strategies with exponential backoff
- ✅ **Parallel Processing**: Concurrent execution of independent workflow nodes

## 📖 Documentation

### Configuration Guides
- **[Configuration Guide](docs/CONFIGURATION_GUIDE.md)** - Comprehensive configuration reference
- **[Input/Output Node Requirements](docs/INPUT_OUTPUT_NODE_REQUIREMENTS.md)** - **REQUIRED**: New mandatory workflow structure
- **[MCP Client Setup](docs/MCP_CLIENT_SETUP_GUIDE.md)** - Model Context Protocol integration
- **[MCP Troubleshooting](docs/MCP_TROUBLESHOOTING_GUIDE.md)** - Common MCP issues and solutions

### Examples
- **[Basic Configuration](docs/examples/basic-application.yml)** - Simple getting-started examples
- **[Comprehensive Examples](docs/examples/comprehensive-agents-example.yml)** - Advanced agent configurations
- **[Graph Workflow Examples](docs/examples/graph-workflow-example.yml)** - Complex dependency patterns

### Development
- **[Tasks & Roadmap](docs/tasks.md)** - Development tasks and future enhancements

## 🔧 Configuration

### Minimal Configuration

Create your `application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

# IMPORTANT: All agents MUST include both 'input_node' and 'output_node'
# See docs/INPUT_OUTPUT_NODE_REQUIREMENTS.md for details
agents:
  list:
    - name: myAgent
      systemPrompt: "You are a helpful assistant."
      workflow:
        type: graph
        chain:
          # REQUIRED: input_node - entry point for user requests
          - nodeId: "input_node"
            prompt: "Receive user request: {input}"
          - nodeId: "response"
            dependsOn: ["input_node"]
            prompt: "Respond to: {input_node}"
          # REQUIRED: output_node - final result returned to user
          - nodeId: "output_node"
            dependsOn: ["response"]
            prompt: "Present response: {response}"
```

### Advanced Graph Workflow Examples

#### Complex Dependencies with Parallel Processing
```yaml
app:
  agents:
    - name: analysisAgent
      model: openai
      system-prompt: "You are a data analysis expert."
      workflow:
        type: graph
        chain:
          # Root node
          - nodeId: "extract_data"
            prompt: "Extract key data from: {input}"
          
          # Parallel analysis
          - nodeId: "statistical_analysis"
            dependsOn: ["extract_data"]
            prompt: "Analyze statistics: {extract_data}"
          
          - nodeId: "trend_analysis"
            dependsOn: ["extract_data"]
            prompt: "Identify trends: {extract_data}"
          
          # Synthesis with multiple dependencies
          - nodeId: "final_report"
            dependsOn: ["statistical_analysis", "trend_analysis"]
            prompt: "Generate report: {statistical_analysis} + {trend_analysis}"
```

#### Conditional Logic (If/Then/Else Branching)
```yaml
app:
  agents:
    - name: smartAgent
      model: openai
      system-prompt: "You are an intelligent assistant with conditional logic."
      workflow:
        type: graph
        chain:
          - nodeId: "priority_router"
            conditional:
              condition:
                type: CONTAINS
                field: "input"
                value: "urgent"
                ignoreCase: true
              thenStep:
                prompt: "URGENT: Immediate attention required for: {input}"
                tool: "emergencyTool"
              elseStep:
                prompt: "Standard processing for: {input}"
```

#### Orchestrator Pattern (Manager-Worker-Synthesizer)
```yaml
app:
  agents:
    - name: orchestratorAgent
      model: openai
      system-prompt: "You are a project manager coordinating specialists."
      workflow:
        type: orchestrator
        manager-prompt: "Analyze request and assign to specialists: {input}"
        workers:
          - name: "technical-specialist"
            workflow:
              type: graph
              chain:
                - nodeId: "tech_analysis"
                  prompt: "Technical analysis: {input}"
          - name: "business-analyst"
            workflow:
              type: graph
              chain:
                - nodeId: "business_analysis"
                  prompt: "Business analysis: {input}"
        synthesizer-prompt: "Combine technical and business insights: {workerResults}"
```

#### Routing Pattern (Content-Based Routing)
```yaml
app:
  agents:
    - name: routingAgent
      model: openai
      system-prompt: "You are a smart routing assistant."
      workflow:
        type: routing
        routes:
          technical:
            prompt: "Handle technical issue: {input}"
            tool: "technicalSupportTool"
          billing:
            prompt: "Handle billing inquiry: {input}"
            tool: "billingTool"
          general:
            prompt: "Provide general assistance: {input}"
```

## 🛠️ API Usage

### REST Endpoints

- `POST /agents/{agentName}/invoke` - Execute an agent workflow
- `GET /agents` - List all configured agents
- `GET /agents/{agentName}` - Get agent details

### Example Request

```bash
curl -X POST http://localhost:8080/agents/myAgent/invoke \
  -H "Content-Type: application/json" \
  -d '{"input": "Analyze the quarterly sales data"}'
```

## 🔌 MCP Integration

The platform supports Model Context Protocol for external tool integration:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        connections:
          dataserver:
            url: http://localhost:8080
            sse-endpoint: /mcp/sse
```

See the [MCP Setup Guide](docs/MCP_CLIENT_SETUP_GUIDE.md) for detailed configuration.

## 🧪 Testing

Run the test suite:

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=GraphWorkflowTest

# Integration tests
mvn test -Dtest=*IntegrationTest
```

## 📊 Monitoring

The platform includes Spring Boot Actuator endpoints:

- `/actuator/health` - Application health status
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Application information

## 🚨 Troubleshooting

### Common Issues

1. **Agent not found**: Check agent name in configuration
2. **Workflow cycles**: Review dependencies for circular references
3. **MCP connection issues**: See [MCP Troubleshooting Guide](docs/MCP_TROUBLESHOOTING_GUIDE.md)

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.springai.agent: DEBUG
    org.springframework.ai: DEBUG
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Resources

- **[Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)** - Core Spring AI framework
- **[Model Context Protocol](https://modelcontextprotocol.io/)** - MCP specification
- **[OpenAI API Documentation](https://platform.openai.com/docs)** - OpenAI API reference

## 📞 Support

- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)
- **Documentation**: [docs/](docs/) directory

---

**Built with ❤️ using Spring AI and the Model Context Protocol**