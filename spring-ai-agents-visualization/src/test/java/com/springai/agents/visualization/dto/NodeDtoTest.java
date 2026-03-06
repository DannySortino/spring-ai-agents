package com.springai.agents.visualization.dto;

import com.springai.agents.node.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeDto")
class NodeDtoTest {

    // ── InputNode ───────────────────────────────────────────────────────

    @Test
    @DisplayName("converts InputNode to DTO with type INPUT")
    void inputNode() {
        InputNode node = InputNode.builder().id("input").build();
        NodeDto dto = NodeDto.from(node, 0);

        assertEquals("input", dto.getId());
        assertEquals("INPUT", dto.getType());
        assertEquals(0, dto.getLevel());
        assertFalse(dto.isHasHooks());
        assertNull(dto.getErrorStrategy());
    }

    // ── LlmNode ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("converts LlmNode with prompt and system prompt")
    void llmNode() {
        LlmNode node = LlmNode.builder()
                .id("analyze")
                .promptTemplate("Analyze: {input}")
                .systemPrompt("You are an analyst.")
                .build();

        NodeDto dto = NodeDto.from(node, 1);

        assertEquals("analyze", dto.getId());
        assertEquals("LLM", dto.getType());
        assertEquals(1, dto.getLevel());
        assertEquals("Analyze: {input}", dto.getPromptTemplate());
        assertEquals("You are an analyst.", dto.getSystemPrompt());
    }

    // ── RestNode ────────────────────────────────────────────────────────

    @Test
    @DisplayName("converts RestNode with URL and method")
    void restNode() {
        RestNode node = RestNode.builder()
                .id("fetch")
                .url("https://api.example.com/data")
                .method(HttpMethod.GET)
                .timeout(Duration.ofSeconds(10))
                .build();

        NodeDto dto = NodeDto.from(node, 2);

        assertEquals("fetch", dto.getId());
        assertEquals("REST", dto.getType());
        assertEquals(2, dto.getLevel());
        assertEquals("https://api.example.com/data", dto.getUrl());
        assertEquals("GET", dto.getMethod());
    }

    // ── ContextNode ─────────────────────────────────────────────────────

    @Test
    @DisplayName("converts ContextNode with context text")
    void contextNode() {
        ContextNode node = ContextNode.builder()
                .id("ctx")
                .contextText("Some background info")
                .build();

        NodeDto dto = NodeDto.from(node, 1);

        assertEquals("ctx", dto.getId());
        assertEquals("CONTEXT", dto.getType());
        assertEquals("Some background info", dto.getContextText());
    }

    // ── ToolNode ────────────────────────────────────────────────────────

    @Test
    @DisplayName("converts ToolNode with tool name and guidance")
    void toolNode() {
        ToolNode node = ToolNode.builder()
                .id("search")
                .toolName("web_search")
                .guidance("Use the query field")
                .build();

        NodeDto dto = NodeDto.from(node, 1);

        assertEquals("search", dto.getId());
        assertEquals("TOOL", dto.getType());
        assertEquals("web_search", dto.getToolName());
        assertEquals("Use the query field", dto.getGuidance());
    }

    // ── OutputNode ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("OutputNode strategies")
    class OutputNodeStrategies {

        @Test
        @DisplayName("pass-through output strategy")
        void passThrough() {
            OutputNode node = OutputNode.builder().id("output").build();

            NodeDto dto = NodeDto.from(node, 3);

            assertEquals("output", dto.getId());
            assertEquals("OUTPUT", dto.getType());
            assertEquals("Pass-Through", dto.getOutputStrategy());
            assertFalse(dto.isHasOutputHandler());
            assertNull(dto.getPostProcessPrompt());
        }

        @Test
        @DisplayName("LLM post-process output strategy")
        void llmPostProcess() {
            OutputNode node = OutputNode.builder()
                    .id("output")
                    .postProcessPrompt("Summarize: {analyze}")
                    .build();

            NodeDto dto = NodeDto.from(node, 3);

            assertEquals("LLM Post-Process", dto.getOutputStrategy());
            assertFalse(dto.isHasOutputHandler());
            assertEquals("Summarize: {analyze}", dto.getPostProcessPrompt());
        }

        @Test
        @DisplayName("custom handler output strategy takes priority")
        void customHandler() {
            OutputNode node = OutputNode.builder()
                    .id("output")
                    .postProcessPrompt("Should be ignored")
                    .outputHandler(ctx -> "custom")
                    .build();

            NodeDto dto = NodeDto.from(node, 3);

            assertEquals("Custom Handler", dto.getOutputStrategy());
            assertTrue(dto.isHasOutputHandler());
        }
    }

    // ── Hooks & Config ──────────────────────────────────────────────────

    @Nested
    @DisplayName("hooks and config")
    class HooksAndConfig {

        @Test
        @DisplayName("detects when node has hooks")
        void detectsHooks() {
            NodeHooks hooks = NodeHooks.builder()
                    .beforeExecute(ctx -> {})
                    .build();

            LlmNode node = LlmNode.builder()
                    .id("analyze")
                    .promptTemplate("...")
                    .hooks(hooks)
                    .build();

            NodeDto dto = NodeDto.from(node, 1);
            assertTrue(dto.isHasHooks());
        }

        @Test
        @DisplayName("detects error strategy from config")
        void detectsErrorStrategy() {
            NodeConfig config = NodeConfig.builder()
                    .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                    .defaultValue("fallback")
                    .build();

            LlmNode node = LlmNode.builder()
                    .id("analyze")
                    .promptTemplate("...")
                    .config(config)
                    .build();

            NodeDto dto = NodeDto.from(node, 1);
            assertEquals("CONTINUE_WITH_DEFAULT", dto.getErrorStrategy());
        }

        @Test
        @DisplayName("null hooks and config leave fields at defaults")
        void nullHooksAndConfig() {
            InputNode node = InputNode.builder().id("input").build();
            NodeDto dto = NodeDto.from(node, 0);

            assertFalse(dto.isHasHooks());
            assertNull(dto.getErrorStrategy());
        }
    }
}

