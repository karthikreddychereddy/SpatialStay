package com.spatialstay.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {

    @Bean("primaryAiChatClient")
    public ChatClient primaryAiChatClient(
            @Value("${app.ai.groq.api-key}")
            String apiKey,

            @Value(
                    "${app.ai.groq.base-url:"
                            + "https://api.groq.com/openai/v1}"
            )
            String baseUrl,

            @Value(
                    "${app.ai.groq.model:"
                            + "qwen/qwen3.8-27b}"
            )
            String model
    ) {

        OpenAiChatOptions options =
                OpenAiChatOptions.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .model(model)
                        .temperature(0.2)
                        .maxTokens(500)
                        .build();

        OpenAiChatModel groqModel =
                OpenAiChatModel
                        .builder()
                        .options(options)
                        .build();

        return ChatClient
                .builder(groqModel)
                .build();
    }
}