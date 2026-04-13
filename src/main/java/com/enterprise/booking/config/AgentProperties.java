package com.enterprise.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private final Llm llm = new Llm();
    private final Rag rag = new Rag();

    public Llm getLlm() {
        return llm;
    }

    public Rag getRag() {
        return rag;
    }

    public static class Llm {
        private String provider = "openai";
        private String apiKey;
        private String chatModel = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private double temperature = 0.1;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Rag {
        private int topK = 4;
        private double minScore = 0.35;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }
    }
}
