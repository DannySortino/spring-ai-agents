package com.springai.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main Spring Boot application class for the Spring AI Agent.
 * 
 * This application provides an AI-powered agent system that supports various workflow types
 * including chain, parallel, orchestrator, and routing workflows. It integrates with
 * Model Context Protocol (MCP) servers and provides configurable AI agents through
 * Spring Boot's configuration properties.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringAiAgentApplication.class, args);
    }
}
