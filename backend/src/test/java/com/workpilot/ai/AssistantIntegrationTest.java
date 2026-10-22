package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.exception.AiUnavailableException;
import com.workpilot.ai.service.ai.AiChatMessage;
import com.workpilot.ai.service.ai.AiCompletion;
import com.workpilot.ai.service.ai.AiCompletionClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiCompletionClient aiCompletionClient;

    private String csrfToken;
    private Cookie csrfCookie;
    private MockHttpSession session;

    @BeforeEach
    void fetchCsrfToken() throws Exception {
        session = null;
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        csrfToken = body.get("token").asText();
        csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        when(aiCompletionClient.complete(anyList()))
                .thenThrow(new AiUnavailableException("The AI assistant is unavailable."))
                .thenReturn(new AiCompletion("mocked reply", 9, 2))
                .thenReturn(new AiCompletion("second reply", 14, 3));
    }

    @Test
    void persistsRepliesAndIsolatesConversations() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Assistant Org"));
        register(memberEmail, "Mia", "Member", null);
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Assistant Org"));

        login(ownerEmail);
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER")).getResponse().getStatus());

        assertEquals(401, mockMvc.perform(get("/api/organizations/" + organizationId + "/conversations"))
                .andReturn().getResponse().getStatus());

        MvcResult created = submit("/api/organizations/" + organizationId + "/conversations", Map.of());
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        JsonNode conversation = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID conversationId = UUID.fromString(conversation.get("id").asText());
        assertEquals("New conversation", conversation.get("title").asText());

        MvcResult blank = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "   "));
        assertEquals(400, blank.getResponse().getStatus());
        assertTrue(blank.getResponse().getContentAsString().contains("Message is required"));

        MvcResult tooLong = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "a".repeat(4001)));
        assertEquals(400, tooLong.getResponse().getStatus());
        assertTrue(tooLong.getResponse().getContentAsString().contains("4000"));

        MvcResult failed = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "first question"));
        assertEquals(503, failed.getResponse().getStatus());
        assertTrue(failed.getResponse().getContentAsString().contains("The AI assistant is unavailable."));
        JsonNode afterFailure = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/conversations/" + conversationId).getResponse().getContentAsString());
        assertEquals(0, afterFailure.get("messages").size());

        MvcResult first = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "  first question  "));
        assertEquals(200, first.getResponse().getStatus(), first.getResponse().getContentAsString());
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        assertEquals("first question", firstBody.get("title").asText());
        assertEquals(2, firstBody.get("messages").size());
        assertEquals("USER", firstBody.get("messages").get(0).get("role").asText());
        assertEquals("first question", firstBody.get("messages").get(0).get("content").asText());
        assertEquals("ASSISTANT", firstBody.get("messages").get(1).get("role").asText());
        assertEquals("mocked reply", firstBody.get("messages").get(1).get("content").asText());

        MvcResult second = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "follow up"));
        assertEquals(200, second.getResponse().getStatus(), second.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertEquals(4, secondBody.get("messages").size());
        assertEquals("second reply", secondBody.get("messages").get(3).get("content").asText());

        MvcResult limited = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "over the limit"));
        assertEquals(429, limited.getResponse().getStatus());
        assertTrue(limited.getResponse().getContentAsString().contains("today's assistant message limit"));
        JsonNode afterLimit = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/conversations/" + conversationId).getResponse().getContentAsString());
        assertEquals(4, afterLimit.get("messages").size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiCompletionClient, times(3)).complete(captor.capture());
        List<AiChatMessage> followUp = captor.getAllValues().get(2);
        assertEquals("system", followUp.get(0).role());
        assertTrue(followUp.stream().anyMatch(message -> "first question".equals(message.content())));
        assertTrue(followUp.stream().anyMatch(message -> "mocked reply".equals(message.content())));
        assertEquals("follow up", followUp.get(followUp.size() - 1).content());

        JsonNode listed = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/conversations").getResponse().getContentAsString());
        assertEquals(1, listed.size());
        assertEquals(conversationId.toString(), listed.get(0).get("id").asText());

        login(memberEmail);
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/conversations/" + conversationId)
                .getResponse().getStatus());
        JsonNode memberList = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/conversations").getResponse().getContentAsString());
        assertEquals(0, memberList.size());
        assertEquals(404, submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", "hello")).getResponse().getStatus());

        login(outsiderEmail);
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/conversations").getResponse().getStatus());
        assertEquals(404, getWithSession("/api/organizations/" + outsiderOrgId + "/conversations/" + conversationId)
                .getResponse().getStatus());

        login(ownerEmail);
        assertEquals(204, delete("/api/organizations/" + organizationId + "/conversations/" + conversationId)
                .getResponse().getStatus());
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/conversations/" + conversationId)
                .getResponse().getStatus());
        JsonNode afterDelete = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/conversations").getResponse().getContentAsString());
        assertEquals(0, afterDelete.size());
        assertFalse(afterFailure.get("messages").size() > 0);
    }

    private MvcResult register(String email, String firstName, String lastName, String organizationName) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        if (organizationName != null) {
            body.put("organizationName", organizationName);
        }
        MvcResult result = submit("/api/auth/register", body);
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return result;
    }

    private void login(String email) throws Exception {
        MvcResult result = submit("/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private UUID organizationId(MvcResult registered) throws Exception {
        return UUID.fromString(objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("memberships").get(0).get("organizationId").asText());
    }

    private String email() {
        return "assistant-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult submit(String path, Object body) throws Exception {
        return exchange(post(path), body);
    }

    private MvcResult delete(String path) throws Exception {
        return exchange(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path), null);
    }

    private MvcResult exchange(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("X-XSRF-TOKEN", csrfToken).cookie(csrfCookie);
        if (session != null) {
            request.session(session);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        updateCookies(result);
        return result;
    }

    private MvcResult getWithSession(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).session(session)).andReturn();
        updateCookies(result);
        return result;
    }

    private void updateCookies(MvcResult result) {
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        if (csrf != null) {
            csrfCookie = csrf;
            csrfToken = csrf.getValue();
        }
        if (result.getRequest().getSession(false) instanceof MockHttpSession activeSession) {
            session = activeSession;
        }
    }
}
