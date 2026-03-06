package com.springai.agents.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample application demonstrating the Spring AI Agents framework.
 * <p>
 * This application shows how to import the framework as a dependency and
 * implement custom {@link com.springai.agents.agent.Agent} beans that are
 * auto-discovered, built, and optionally exposed as MCP tools.
 * <p>
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}

