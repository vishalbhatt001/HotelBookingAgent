package com.enterprise.booking.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class LangChainConfig {

    @Bean
    public ChatModel chatLanguageModel(AgentProperties properties) {
        AgentProperties.Llm llm = properties.getLlm();
        Assert.hasText(llm.getApiKey(), "OPENAI_API_KEY must be set.");
        return OpenAiChatModel.builder()
                .apiKey(llm.getApiKey())
                .modelName(llm.getChatModel())
                .temperature(llm.getTemperature())
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(AgentProperties properties) {
        AgentProperties.Llm llm = properties.getLlm();
        Assert.hasText(llm.getApiKey(), "OPENAI_API_KEY must be set.");
        return OpenAiEmbeddingModel.builder()
                .apiKey(llm.getApiKey())
                .modelName(llm.getEmbeddingModel())
                .build();
    }
}
