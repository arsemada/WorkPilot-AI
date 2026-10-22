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
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    public static final int INDEX_DIMENSIONS = 1536;
    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final String NOT_CONFIGURED = "The embedding provider is not configured.";
    private static final String UNAVAILABLE = "Embedding failed.";
    private static final String DIMENSIONS = "The embedding dimensions do not match the vector index.";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final URI embeddingsUri;

    public OpenAiEmbeddingClient(AiProperties properties, ObjectMapper objectMapper) {
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
        this.embeddingsUri = URI.create(baseUrl + "/embeddings");
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        String apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        String model = properties.getEmbeddingModel() == null ? "" : properties.getEmbeddingModel().trim();
        int dimensions = properties.getEmbeddingDimensions();
        if (apiKey.isEmpty() || model.isEmpty() || dimensions < 1) {
            throw new AiUnavailableException(NOT_CONFIGURED);
        }
        if ("pgvector".equalsIgnoreCase(properties.getVectorStore()) && dimensions != INDEX_DIMENSIONS) {
            throw new AiUnavailableException(DIMENSIONS);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("dimensions", dimensions);
        ArrayNode input = body.putArray("input");
        for (String value : inputs) {
            input.add(value);
        }
        try {
            String response = restClient.post()
                    .uri(embeddingsUri)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(response, inputs.size(), dimensions);
        } catch (RestClientResponseException exception) {
            log.warn("Embedding provider returned status {}", exception.getStatusCode().value());
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new AiUnavailableException(NOT_CONFIGURED);
            }
            throw new AiUnavailableException(UNAVAILABLE);
        } catch (ResourceAccessException exception) {
            log.warn("Embedding provider request did not complete");
            throw new AiUnavailableException(UNAVAILABLE);
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Embedding response could not be read");
            throw new AiUnavailableException(UNAVAILABLE);
        }
    }

    private List<float[]> parse(String response, int expectedCount, int dimensions) {
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            if (!data.isArray() || data.size() != expectedCount) {
                throw new AiUnavailableException(UNAVAILABLE);
            }
            List<float[]> vectors = new ArrayList<>();
            for (int index = 0; index < expectedCount; index++) {
                JsonNode embedding = data.get(index).path("embedding");
                if (!embedding.isArray() || embedding.size() != dimensions) {
                    throw new AiUnavailableException(DIMENSIONS);
                }
                float[] vector = new float[embedding.size()];
                for (int value = 0; value < embedding.size(); value++) {
                    vector[value] = (float) embedding.get(value).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiUnavailableException(UNAVAILABLE);
        }
    }
}
