package com.workpilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "workpilot.ai")
public class AiProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxOutputTokens = 800;
    private int maxMessageChars = 4000;
    private int maxHistoryMessages = 20;
    private int dailyMessageLimit = 40;
    private String embeddingModel = "text-embedding-3-small";
    private String embeddingModelVersion = "3";
    private int embeddingDimensions = 1536;
    private double maxCosineDistance = 0.45;
    private int retrievalLimit = 4;
    private String vectorStore = "pgvector";
    private int maxToolCalls = 3;
    private Duration toolBudget = Duration.ofSeconds(15);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxMessageChars() {
        return maxMessageChars;
    }

    public void setMaxMessageChars(int maxMessageChars) {
        this.maxMessageChars = maxMessageChars;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getDailyMessageLimit() {
        return dailyMessageLimit;
    }

    public void setDailyMessageLimit(int dailyMessageLimit) {
        this.dailyMessageLimit = dailyMessageLimit;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingModelVersion() {
        return embeddingModelVersion;
    }

    public void setEmbeddingModelVersion(String embeddingModelVersion) {
        this.embeddingModelVersion = embeddingModelVersion;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public double getMaxCosineDistance() {
        return maxCosineDistance;
    }

    public void setMaxCosineDistance(double maxCosineDistance) {
        this.maxCosineDistance = maxCosineDistance;
    }

    public int getRetrievalLimit() {
        return retrievalLimit;
    }

    public void setRetrievalLimit(int retrievalLimit) {
        this.retrievalLimit = retrievalLimit;
    }

    public String getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(String vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public Duration getToolBudget() {
        return toolBudget;
    }

    public void setToolBudget(Duration toolBudget) {
        this.toolBudget = toolBudget;
    }
}
