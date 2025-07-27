package com.springai.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class MockChatModelConfiguration {
    
    @Bean
    @Primary
    public ChatModel mockChatModel() {
        ChatModel mockChatModel = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        
        // Configure mock to return a simple response
        when(mockChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(null);
        
        return mockChatModel;
    }
}


