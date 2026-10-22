package com.workpilot.ai.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class OpenAiCompletionClient implements AiCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompletionClient.class);
    private static final String NOT_CONFIGURED = "The AI assistant is not configured.";
    private static final String UNAVAILABLE = "The AI assistant is unavailable.";
    private static final String BUSY = "The AI assistant is busy. Try again in a moment.";
    private static final String TIMED_OUT = "The AI assistant did not respond in time.";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final URI completionsUri;

    public OpenAiCompletionClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(30) : properties.getTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com/v1";
        }
        this.completionsUri = URI.create(baseUrl + "/chat/completions");
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiCompletion complete(List<AiChatMessage> messages) {
        String apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        String model = properties.getModel() == null ? "" : properties.getModel().trim();
        if (apiKey.isEmpty() || model.isEmpty() || !supportedBaseUrl(properties.getBaseUrl())) {
            throw new AiUnavailableException(NOT_CONFIGURED);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", Math.max(1, properties.getMaxOutputTokens()));
        ArrayNode messageNodes = body.putArray("messages");
        for (AiChatMessage message : messages) {
            ObjectNode node = messageNodes.addObject();
            node.put("role", message.role());
            node.put("content", message.content());
        }

        try {
            String response = restClient.post()
                    .uri(completionsUri)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("AI provider returned status {}", status);
            if (status == 401 || status == 403) {
                throw new AiUnavailableException(NOT_CONFIGURED);
            }
            if (status == 429) {
                throw new AiUnavailableException(BUSY);
            }
            throw new AiUnavailableException(UNAVAILABLE);
        } catch (ResourceAccessException exception) {
            log.warn("AI provider request did not complete: {}", exception.getClass().getSimpleName());
            if (timedOut(exception)) {
                throw new AiUnavailableException(TIMED_OUT);
            }
            throw new AiUnavailableException(UNAVAILABLE);
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI provider response could not be read: {}", exception.getClass().getSimpleName());
            if (timedOut(exception)) {
                throw new AiUnavailableException(TIMED_OUT);
            }
            throw new AiUnavailableException(UNAVAILABLE);
        }
    }

    private AiCompletion parse(String response) {
        if (response == null || response.isBlank()) {
            throw new AiUnavailableException(UNAVAILABLE);
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiUnavailableException(UNAVAILABLE);
            }
            String content = textOf(choices.get(0).path("message").path("content"));
            if (content.isBlank()) {
                throw new AiUnavailableException(UNAVAILABLE);
            }
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            return new AiCompletion(content, promptTokens, completionTokens);
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("AI provider response could not be parsed");
            throw new AiUnavailableException(UNAVAILABLE);
        }
    }

    private static String textOf(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if (part.isTextual()) {
                text.append(part.asText());
            } else if (part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        return text.toString();
    }

    private static boolean supportedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            String scheme = URI.create(baseUrl.trim()).getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean timedOut(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.http.HttpTimeoutException || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
