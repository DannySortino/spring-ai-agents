package com.springai.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Manual OpenAI configuration for connecting to local models like LMStudio
 * Replaces Spring Boot auto-configuration with custom setup
 */
@Configuration
@Slf4j
public class OpenAiConfiguration {

    @Value("${spring.ai.openai.base-url:http://localhost:1234/v1}")
    private String localAIUrl;

    @Value("${spring.ai.openai.chat.options.model:local-model}")
    private String selectedModel;

    @Value("${spring.ai.openai.api-key:local-api-key}")
    private String apiKey;

    @Bean
    public OpenAiApi openAiApi() {
        // Handle null or empty baseUrl with proper default
        String baseUrl = localAIUrl;
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "http://localhost:1234/v1";
            log.warn("Base URL was null or empty, using default: {}", baseUrl);
        }
        
        // Handle null or empty API key with proper default
        String apiKeyValue = apiKey;
        if (apiKeyValue == null || apiKeyValue.trim().isEmpty()) {
            apiKeyValue = "local-api-key";
            log.warn("API key was null or empty, using default: {}", apiKeyValue);
        }
        
        // Clean up the URL if it has the chat completions endpoint
        if (baseUrl.endsWith("/v1/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/v1/chat/completions"));
        }
        
        // Ensure the URL ends with /v1
        if (!baseUrl.endsWith("/v1")) {
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/v1";
            } else {
                baseUrl += "v1";
            }
        }

        log.info("Configuring OpenAI API with base URL: {}", baseUrl);

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKeyValue) // Use null-safe API key value
                .webClientBuilder(WebClient.builder()
                        // Force HTTP/1.1 for streaming
                        .clientConnector(new org.springframework.http.client.reactive.JdkClientHttpConnector(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .restClientBuilder(RestClient.builder()
                        // Force HTTP/1.1 for non-streaming
                        .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .build();
    }

    @Bean
    public OpenAiChatModel nonStreamingChatModel(OpenAiApi openAiApi) {
        // Handle null or empty model name with proper default
        String modelName = selectedModel;
        if (modelName == null || modelName.trim().isEmpty()) {
            modelName = "local-model";
            log.warn("Model name was null or empty, using default: {}", modelName);
        }
        
        // Handle null or empty API key with proper default
        String apiKeyValue = apiKey;
        if (apiKeyValue == null || apiKeyValue.trim().isEmpty()) {
            apiKeyValue = "local-api-key";
            log.warn("API key was null or empty, using default: {}", apiKeyValue);
        }
        
        log.info("Configuring non-streaming OpenAI Chat Model with model: {}", modelName);

        // Configure model-specific options with streaming disabled
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .streamUsage(false)
                .build();

        // Create an instance of OpenAiChatModel using the API and options.
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
