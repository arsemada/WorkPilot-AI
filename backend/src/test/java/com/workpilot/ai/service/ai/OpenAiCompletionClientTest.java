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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompletionClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsAssistantTextAndRejectsProviderFailures() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Integer> status = new AtomicReference<>(200);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = status.get() == 200
                    ? """
                    {"choices":[{"message":{"content":[{"type":"output_text","text":"server-reply"}]}}],"usage":{"prompt_tokens":5,"completion_tokens":2}}
                    """.getBytes(StandardCharsets.UTF_8)
                    : "provider exploded with secret details".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OpenAiCompletionClient client = client("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", Duration.ofSeconds(2));
        AiCompletion completion = client.complete(List.of(new AiChatMessage("user", "hello")));
        assertEquals("server-reply", completion.content());
        assertEquals(5, completion.promptTokens());
        assertEquals(2, completion.completionTokens());
        assertEquals("Bearer test-key", authorization.get());

        status.set(500);
        AiUnavailableException failure = assertThrows(AiUnavailableException.class,
                () -> client.complete(List.of(new AiChatMessage("user", "hello"))));
        assertEquals("The AI assistant is unavailable.", failure.getMessage());
        assertFalse(failure.getMessage().contains("secret"));
        assertEquals(2, requests.get());

        OpenAiCompletionClient missingKey = client("  ", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", Duration.ofSeconds(2));
        AiUnavailableException unconfigured = assertThrows(AiUnavailableException.class,
                () -> missingKey.complete(List.of(new AiChatMessage("user", "hello"))));
        assertEquals("The AI assistant is not configured.", unconfigured.getMessage());
        assertEquals(2, requests.get());
    }

    @Test
    void reportsATimeoutWithoutInventingAReply() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"too late\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OpenAiCompletionClient client = client(
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                Duration.ofMillis(200));
        AiUnavailableException failure = assertThrows(AiUnavailableException.class,
                () -> client.complete(List.of(new AiChatMessage("user", "hello"))));
        assertEquals("The AI assistant did not respond in time.", failure.getMessage());
    }

    private static OpenAiCompletionClient client(String apiKey, String baseUrl, Duration timeout) {
        AiProperties properties = new AiProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setModel("gpt-4o-mini");
        properties.setTimeout(timeout);
        properties.setMaxOutputTokens(50);
        return new OpenAiCompletionClient(properties, new ObjectMapper());
    }
}
