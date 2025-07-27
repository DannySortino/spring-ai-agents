# Spring AI Agents - Visualization Tool Guide

## Overview

The Spring AI Agents Visualization Tool provides comprehensive capabilities for visualizing, monitoring, and creating graph workflows. This tool addresses all the requirements specified in the original issue:

✅ **Graph Structure Visualization** - View configured graph structures and dependencies  
✅ **Real-time Execution Status** - Monitor node execution status in real-time  
✅ **Interactive Graph Creator** - Create graph .yml files via web interface  
✅ **Configurable Feature Toggles** - Enable/disable features to avoid overhead  

## Features

### 1. Graph Structure Visualization
- **Visual Graph Display**: Interactive graph visualization using vis.js
- **Node Types**: Different colors and shapes for prompt, tool, and conditional nodes
- **Dependency Visualization**: Clear arrows showing node dependencies
- **Interactive Navigation**: Zoom, pan, and click nodes for details
- **Multiple Agents**: Switch between different agents to view their graphs

### 2. Real-time Execution Status Tracking
- **Live Status Updates**: WebSocket-based real-time status updates
- **Node States**: Track PENDING, RUNNING, COMPLETED, FAILED, SKIPPED states
- **Execution History**: View past executions and their results
- **Performance Metrics**: Execution duration and error tracking
- **Visual Indicators**: Color-coded status indicators with animations

### 3. Interactive Graph Creation Web Interface
- **Drag-and-Drop Designer**: Visual graph creation interface
- **Node Palette**: Pre-defined node types (prompt, tool, conditional)
- **Template Library**: Ready-to-use workflow templates
- **Graph Validation**: Real-time validation with error/warning reporting
- **YAML Generation**: Generate downloadable YAML configuration files
- **Copy/Download**: Easy copying and downloading of generated configurations

### 4. Configurable Feature Toggles
- **Granular Control**: Enable/disable individual features
- **Performance Optimization**: Avoid overhead of unused features
- **Security**: Disable creator in production environments
- **Flexible Configuration**: YAML-based feature configuration

## Configuration

### Enable Visualization Features

Add the following to your `application.yml`:

```yaml
spring:
  ai:
    agents:
      # Your agents configuration here
      
      # Visualization Configuration
      visualization:
        # Enable graph structure visualization
        graph-structure: true
        
        # Enable real-time execution status tracking
        real-time-status: true
        
        # Enable interactive graph creation web interface
        interactive-creator: true
        
        # Optional: Customize endpoints and ports
        port: 8081
        base-path: "/visualization"
        websocket-endpoint: "/ws/status"
```

### Feature Toggle Options

| Feature | Property | Description | Default |
|---------|----------|-------------|---------|
| Graph Structure | `graph-structure` | Visualize workflow graphs | `false` |
| Real-time Status | `real-time-status` | Track execution status | `false` |
| Interactive Creator | `interactive-creator` | Web-based graph creation | `false` |

### Example Agents Configuration

See `docs/examples/visualization-example.yml` for a complete configuration with sample agents demonstrating:
- Sequential workflows
- Parallel processing
- Conditional logic
- Orchestrator patterns
- Tool integration

## Usage

### 1. Starting the Application

```bash
# Using the example configuration
java -jar spring-ai-agents.jar --spring.config.location=docs/examples/visualization-example.yml

# Or with your own configuration
java -jar spring-ai-agents.jar --spring.profiles.active=dev
```

### 2. Accessing the Visualization Interface

Open your browser and navigate to:
- **Default**: http://localhost:8080/visualization
- **Custom base path**: http://localhost:8080/your-custom-path

### 3. Using the Interface

#### Graph Structure Tab
1. Select an agent from the left sidebar
2. View the interactive graph visualization
3. Click nodes to see details
4. Use controls to fit, refresh, or navigate the graph

#### Execution Status Tab
1. View active executions in real-time
2. Browse execution history
3. Click executions to see detailed node status
4. Monitor performance metrics and errors

#### Graph Creator Tab
1. Drag nodes from the palette to the designer
2. Connect nodes by defining dependencies
3. Set node properties (prompts, tools, conditions)
4. Validate your graph for errors
5. Generate and download YAML configuration

## API Endpoints

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/visualization/config` | GET | Get feature configuration |
| `/visualization/agents` | GET | List available agents |
| `/visualization/graphs` | GET | Get all agent graphs |
| `/visualization/graphs/{agent}` | GET | Get specific agent graph |
| `/visualization/executions/active` | GET | Get active executions |
| `/visualization/executions/history` | GET | Get execution history |
| `/visualization/executions/{id}` | GET | Get execution details |
| `/visualization/creator/validate` | POST | Validate graph definition |
| `/visualization/creator/generate-yaml` | POST | Generate YAML from graph |
| `/visualization/creator/node-types` | GET | Get available node types |
| `/visualization/creator/templates` | GET | Get workflow templates |

### WebSocket Endpoints

| Endpoint | Description |
|----------|-------------|
| `/ws/status` | Real-time execution status updates |
| `/topic/executions` | Subscribe to all execution updates |
| `/topic/execution/{id}` | Subscribe to specific execution |

## Architecture

### Backend Components

1. **VisualizationConfiguration**: Conditional bean registration based on feature flags
2. **GraphVisualizationService**: Extracts and prepares graph data for visualization
3. **ExecutionStatusService**: Tracks real-time execution status with WebSocket broadcasting
4. **VisualizationController**: REST API endpoints for graph data and status
5. **GraphCreatorController**: Interactive graph creation and YAML generation
6. **VisualizationWebController**: Serves HTML templates

### Frontend Components

1. **Modern Web Interface**: Bootstrap 5 + Font Awesome for professional UI
2. **Graph Visualization**: vis.js library for interactive graph rendering
3. **Real-time Updates**: WebSocket integration with SockJS and STOMP
4. **Responsive Design**: Mobile-friendly interface with adaptive layouts
5. **Interactive Creator**: Drag-and-drop graph designer with validation

### Integration Points

1. **GraphWorkflow Enhancement**: Integrated ExecutionStatusService for real-time tracking
2. **AgentConfiguration**: Automatic injection of status service when available
3. **Conditional Beans**: Feature-based component registration to avoid overhead
4. **WebSocket Configuration**: Automatic setup when real-time features are enabled

## Performance Considerations

### Production Recommendations

```yaml
spring:
  ai:
    agents:
      visualization:
        graph-structure: true      # Keep for monitoring
        real-time-status: false    # Disable for performance
        interactive-creator: false # Disable for security
```

### Memory Usage
- Graph visualization: Minimal overhead, only loads when requested
- Real-time status: ~1MB per 1000 executions in memory
- Interactive creator: No overhead when disabled

### Network Traffic
- WebSocket connections: ~1KB per status update
- Graph data: ~5-50KB per agent depending on complexity
- YAML generation: Processed server-side, minimal client load

## Troubleshooting

### Common Issues

1. **Visualization not loading**: Check feature flags are enabled
2. **WebSocket connection failed**: Verify real-time-status is enabled
3. **Graphs not displaying**: Ensure agents have graph workflows configured
4. **YAML generation errors**: Validate graph structure before generation

### Debug Configuration

```yaml
logging:
  level:
    com.springai.agent.service.ExecutionStatusService: DEBUG
    com.springai.agent.service.GraphVisualizationService: DEBUG
    com.springai.agent.controller: DEBUG
```

## Security Considerations

1. **Production Deployment**: Disable interactive-creator in production
2. **Access Control**: Add authentication/authorization as needed
3. **CORS Configuration**: Restrict origins in production environments
4. **WebSocket Security**: Consider authentication for WebSocket connections

## Future Enhancements

Potential areas for future development:
- Graph editing capabilities in the structure view
- Export graphs as images (PNG, SVG)
- Advanced filtering and search in execution history
- Custom node types and templates
- Integration with monitoring systems (Prometheus, Grafana)
- Multi-tenant support with user-specific graphs

## Support

For issues, questions, or contributions:
1. Check the troubleshooting section above
2. Review the example configuration files
3. Enable debug logging for detailed information
4. Refer to the API documentation for integration details