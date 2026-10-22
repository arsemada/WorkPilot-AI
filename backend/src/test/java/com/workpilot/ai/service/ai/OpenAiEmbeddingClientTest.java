package com.workpilot.ai.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiEmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsEmbeddingsAndRejectsProviderFailures() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requests.incrementAndGet();
            byte[] request = exchange.getRequestBody().readAllBytes();
            String body = new String(request, StandardCharsets.UTF_8);
            int status = body.contains("\"fail\"") ? 500 : 200;
            byte[] response = status == 200
                    ? "{\"data\":[{\"embedding\":[0.5,0.25]}]}".getBytes(StandardCharsets.UTF_8)
                    : "secret-provider-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        OpenAiEmbeddingClient client = client("test-key", "json", 2, server.getAddress().getPort());
        List<float[]> vectors = client.embed(List.of("alpha"));
        assertEquals(1, vectors.size());
        assertEquals(2, vectors.get(0).length);
        assertEquals(1, requests.get());

        AiUnavailableException failed = assertThrows(AiUnavailableException.class, () -> client.embed(List.of("fail")));
        assertEquals("Embedding failed.", failed.getMessage());
        assertFalse(failed.getMessage().contains("secret"));

        OpenAiEmbeddingClient missingKey = client("", "json", 2, server.getAddress().getPort());
        AiUnavailableException unconfigured = assertThrows(AiUnavailableException.class, () -> missingKey.embed(List.of("alpha")));
        assertEquals("The embedding provider is not configured.", unconfigured.getMessage());
        assertEquals(2, requests.get());

        OpenAiEmbeddingClient mismatched = client("test-key", "pgvector", 8, server.getAddress().getPort());
        AiUnavailableException dimensions = assertThrows(AiUnavailableException.class, () -> mismatched.embed(List.of("alpha")));
        assertEquals("The embedding dimensions do not match the vector index.", dimensions.getMessage());
        assertEquals(2, requests.get());
    }

    private static OpenAiEmbeddingClient client(String apiKey, String vectorStore, int dimensions, int port) {
        AiProperties properties = new AiProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl("http://127.0.0.1:" + port + "/v1");
        properties.setEmbeddingModel("text-embedding-3-small");
        properties.setEmbeddingDimensions(dimensions);
        properties.setVectorStore(vectorStore);
        properties.setTimeout(Duration.ofSeconds(2));
        return new OpenAiEmbeddingClient(properties, new ObjectMapper());
    }
}
