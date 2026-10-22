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
}
