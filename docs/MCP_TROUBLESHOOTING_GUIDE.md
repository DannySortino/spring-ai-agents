# MCP Troubleshooting Guide

## Overview

This guide helps you troubleshoot common issues when connecting to the MCP (Model Context Protocol) agents in your Spring AI Agent Platform.

## Common Issues and Solutions

### 1. Connection Refused Errors

#### Problem
```
Connection refused to http://localhost:8087/api/agents/simpleAgent/mcp
```

#### Possible Causes and Solutions

**A. Spring AI Agent Platform Not Running**
```bash
# Check if the application is running
curl http://localhost:8086/actuator/health

# If not running, start it
mvn spring-boot:run
```

**B. MCP Server Not Enabled**
Check your `application.yml`:
```yaml
app:
  agents:
    - name: simpleAgent
      mcpServer:
        enabled: true  # Must be true
        port: 8087
```

**C. Port Conflicts**
```bash
# Check what's running on the MCP ports
netstat -an | findstr "8087 8088 8089"

# Or on Linux/Mac
lsof -i :8087
lsof -i :8088
lsof -i :8089
```

### 2. Timeout Errors

#### Problem
```
Request timeout after 30000ms
```

#### Solutions

**A. Increase Timeout in mcp.json**
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

**B. Check Agent Performance**
```bash
# Monitor application logs
tail -f logs/spring-ai-agent.log

# Check system resources
top
htop
```

### 3. Invalid JSON Response

#### Problem
```
SyntaxError: Unexpected token in JSON at position 0
```

#### Solutions

**A. Verify MCP Endpoint**
```bash
# Test the endpoint directly
curl -v http://localhost:8087/api/agents/simpleAgent/mcp

# Check response headers
curl -I http://localhost:8087/api/agents/simpleAgent/mcp
```

**B. Check Content-Type Headers**
Ensure your mcp.json includes proper headers:
```json
{
  "mcpServers": {
    "agentName": {
      "args": [
        "-H", "Content-Type: application/json",
        "-H", "Accept: application/json"
      ]
    }
  }
}
```

### 4. Tool Not Found Errors

#### Problem
```
Tool 'invoiceTool' not found on server 'billingAgent'
```

#### Solutions

**A. Verify Tool Availability**
```bash
# Check agent configuration
curl http://localhost:8088/api/agents/billingAgent/mcp/tools

# List available tools
curl -X POST http://localhost:8088/api/agents/billingAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{"method": "tools/list"}'
```

**B. Check Tool Name Spelling**
Ensure exact tool name match in your mcp.json:
```json
{
  "servers": {
    "billingAgent": {
      "tools": [
        {
          "name": "invoiceTool",
          "description": "Fetch invoice details"
        }
      ]
    }
  }
}
```

### 5. Authentication Errors

#### Problem
```
401 Unauthorized
403 Forbidden
```

#### Solutions

**A. Check API Key Configuration**
```bash
# Verify environment variables
echo $OPENAI_API_KEY
echo $MCP_API_KEY
```

**B. Update mcp.json with Credentials**
```json
{
  "servers": {
    "agentName": {
      "auth": {
        "type": "bearer",
        "token": "your-api-key"
      }
    }
  }
}
```

### 6. SSL/TLS Certificate Errors

#### Problem
```
SSL certificate verification failed
```

#### Solutions

**A. For Development (Not Recommended for Production)**
```json
{
  "global": {
    "security": {
      "validateCertificates": false,
      "allowSelfSigned": true
    }
  }
}
```

**B. For Production**
```bash
# Add certificate to trust store
keytool -import -alias mcp-server -file server.crt -keystore $JAVA_HOME/lib/security/cacerts
```

### 7. Load Balancing Issues

#### Problem
```
All servers unavailable for load balancing
```

#### Solutions

**A. Check Health Status**
```bash
# Test each server individually
curl http://localhost:8087/api/agents/simpleAgent/mcp/health
curl http://localhost:8088/api/agents/billingAgent/mcp/health
curl http://localhost:8089/api/agents/complexAgent/mcp/health
```

**B. Adjust Health Check Settings**
```json
{
  "client": {
    "loadBalancing": {
      "healthCheck": {
        "enabled": true,
        "interval": 10000,
        "timeout": 3000,
        "retries": 2
      }
    }
  }
}
```

## Diagnostic Commands

### Check Application Status
```bash
# Application health
curl http://localhost:8086/actuator/health

# Application info
curl http://localhost:8086/actuator/info

# Available endpoints
curl http://localhost:8086/actuator
```

### Test MCP Endpoints
```bash
# Simple Agent
curl -X POST http://localhost:8087/api/agents/simpleAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{"method": "ping"}'

# Billing Agent
curl -X POST http://localhost:8088/api/agents/billingAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{"method": "tools/list"}'

# Complex Agent
curl -X POST http://localhost:8089/api/agents/complexAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{"method": "capabilities"}'
```

### Check Logs
```bash
# Application logs
tail -f logs/spring-ai-agent.log

# MCP specific logs
grep "MCP" logs/spring-ai-agent.log

# Error logs
grep "ERROR" logs/spring-ai-agent.log
```

### Network Diagnostics
```bash
# Test connectivity
telnet localhost 8087
telnet localhost 8088
telnet localhost 8089

# Check DNS resolution
nslookup localhost

# Test with different tools
wget http://localhost:8087/api/agents/simpleAgent/mcp
curl -v http://localhost:8087/api/agents/simpleAgent/mcp
```

## Configuration Validation

### Validate mcp.json Syntax
```bash
# Using jq (if available)
jq . docs/mcp-configs/all-agents-mcp.json

# Using Python
python -m json.tool docs/mcp-configs/all-agents-mcp.json

# Using Node.js
node -e "console.log(JSON.stringify(require('./docs/mcp-configs/all-agents-mcp.json'), null, 2))"
```

### Validate Application Configuration
```bash
# Check YAML syntax
python -c "import yaml; yaml.safe_load(open('src/main/resources/application.yml'))"

# Check Spring Boot configuration
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"
```

## Performance Optimization

### Connection Pooling
```json
{
  "global": {
    "connectionPool": {
      "maxConnections": 20,
      "connectionTimeout": 30000,
      "keepAlive": true,
      "maxIdleTime": 300000
    }
  }
}
```

### Timeout Tuning
```json
{
  "servers": {
    "simpleAgent": {
      "timeout": 15000,
      "retries": 2
    },
    "billingAgent": {
      "timeout": 30000,
      "retries": 3
    },
    "complexAgent": {
      "timeout": 60000,
      "retries": 2
    }
  }
}
```

### Logging Configuration
```json
{
  "global": {
    "logging": {
      "level": "DEBUG",
      "logRequests": true,
      "logResponses": true,
      "logErrors": true
    }
  }
}
```

## Environment-Specific Issues

### Development Environment
```bash
# Common development issues
export OPENAI_API_KEY=local-api-key
export OPENAI_BASE_URL=http://localhost:1234/v1
export OPENAI_MODEL=local-model

# Start with debug logging
mvn spring-boot:run -Dlogging.level.org.example=DEBUG
```

### Production Environment
```bash
# Production checklist
- SSL certificates properly configured
- Firewall rules allow MCP ports
- Load balancer health checks configured
- Monitoring and alerting set up
- Log rotation configured
```

### Docker Environment
```bash
# Check container networking
docker network ls
docker inspect <container-name>

# Port mapping issues
docker ps -a
docker logs <container-name>

# Container connectivity
docker exec -it <container-name> curl http://localhost:8087/api/agents/simpleAgent/mcp
```

## Getting Help

### Enable Debug Logging
Add to your `application.yml`:
```yaml
logging:
  level:
    org.example: DEBUG
    org.springframework.ai: DEBUG
    org.springframework.ai.mcp: TRACE
    org.springframework.web: DEBUG
```

### Collect Diagnostic Information
```bash
# System information
java -version
mvn -version
curl --version

# Application information
curl http://localhost:8086/actuator/info
curl http://localhost:8086/actuator/health

# Configuration dump
curl http://localhost:8086/actuator/configprops
```

### Create Minimal Reproduction
1. Use the simplest mcp.json configuration
2. Test with a single agent
3. Use basic curl commands
4. Check logs for specific error messages
5. Document exact steps to reproduce

## Frequently Asked Questions

### Q: Why can't I connect to the MCP server?
**A:** Check that:
1. Spring AI Agent Platform is running
2. MCP server is enabled in configuration
3. Correct port numbers are used
4. No firewall blocking connections

### Q: Why do I get timeout errors?
**A:** This usually indicates:
1. Server is overloaded
2. Network latency issues
3. Timeout values too low
4. Agent processing taking too long

### Q: Why are tools not found?
**A:** Verify:
1. Tool names match exactly
2. Agent has the tool configured
3. Tool is properly registered
4. MCP server is responding

### Q: How do I test if MCP is working?
**A:** Use these test commands:
```bash
# Basic connectivity
curl http://localhost:8087/api/agents/simpleAgent/mcp

# Tool listing
curl -X POST http://localhost:8088/api/agents/billingAgent/mcp \
  -H "Content-Type: application/json" \
  -d '{"method": "tools/list"}'
```

### Q: Can I use multiple MCP clients simultaneously?
**A:** Yes, the MCP servers support multiple concurrent connections. Use connection pooling for better performance.

### Q: How do I monitor MCP performance?
**A:** Enable metrics and use monitoring tools:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

For additional support, check the [Spring AI Agent Platform documentation](CONFIGURATION_GUIDE.md) or create an issue with your diagnostic information.