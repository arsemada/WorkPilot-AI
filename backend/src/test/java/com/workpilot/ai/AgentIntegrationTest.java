package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.repository.ActivityEventRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.service.ai.AiCompletion;
import com.workpilot.ai.service.ai.AiCompletionClient;
import com.workpilot.ai.service.ai.AiToolCall;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "workpilot.ai.daily-message-limit=20")
@AutoConfigureMockMvc
class AgentIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

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
        csrfToken = result.getResponse().getCookie("XSRF-TOKEN").getValue();
        csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void savesATaskOnlyAfterConfirmation() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Agent Org"));
        register(memberEmail, "Mia", "Member", null);
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Agent Org"));
        login(ownerEmail);
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail, "role", "MEMBER")).getResponse().getStatus());

        when(aiCompletionClient.complete(anyList(), anyList())).thenReturn(new AiCompletion(
                "",
                4,
                1,
                List.of(new AiToolCall("call-create", "create_task", "{\"title\":\"Quarterly report\",\"priority\":\"HIGH\"}"))));
        UUID conversationId = conversation(organizationId);
        JsonNode proposed = ask(organizationId, conversationId, "Create a high-priority task for the quarterly report.");
        JsonNode toolCall = proposed.get("messages").get(1).get("toolCalls").get(0);
        assertEquals("PROPOSED", toolCall.get("status").asText());
        assertEquals("Quarterly report", toolCall.get("arguments").get("title").asText());
        assertEquals("HIGH", toolCall.get("arguments").get("priority").asText());
        assertTrue(proposed.get("messages").get(1).get("content").asText().contains("before it is saved"));
        assertEquals(0, taskRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 10)).size());

        UUID toolCallId = UUID.fromString(toolCall.get("id").asText());
        login(memberEmail);
        assertEquals(404, submit("/api/organizations/" + organizationId + "/conversations/" + conversationId
                + "/tool-calls/" + toolCallId + "/confirm", Map.of()).getResponse().getStatus());
        login(outsiderEmail);
        assertEquals(404, submit("/api/organizations/" + outsiderOrgId + "/conversations/" + conversationId
                + "/tool-calls/" + toolCallId + "/confirm", Map.of()).getResponse().getStatus());

        login(ownerEmail);
        JsonNode confirmed = objectMapper.readTree(submit("/api/organizations/" + organizationId + "/conversations/"
                + conversationId + "/tool-calls/" + toolCallId + "/confirm", Map.of()).getResponse().getContentAsString());
        JsonNode savedCall = confirmed.get("messages").get(1).get("toolCalls").get(0);
        assertEquals("COMPLETED", savedCall.get("status").asText());
        assertEquals("Quarterly report", savedCall.get("result").get("task").get("title").asText());
        assertEquals("TODO", savedCall.get("result").get("task").get("status").asText());
        UUID taskId = UUID.fromString(savedCall.get("result").get("task").get("id").asText());
        assertEquals("Quarterly report", taskRepository.findByIdAndOrganizationId(taskId, organizationId).orElseThrow().getTitle());
        assertTrue(activityEventRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 5)).stream()
                .anyMatch(event -> event.getType() == ActivityType.TASK_CREATED && event.getSummary().contains("Quarterly report")));

        JsonNode again = objectMapper.readTree(submit("/api/organizations/" + organizationId + "/conversations/"
                + conversationId + "/tool-calls/" + toolCallId + "/confirm", Map.of()).getResponse().getContentAsString());
        assertEquals(taskId.toString(), again.get("messages").get(1).get("toolCalls").get(0).get("result").get("task").get("id").asText());
        assertEquals(1, taskRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 10)).size());
    }

    @Test
    void missingAndMalformedArgumentsDoNotCreateTasks() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Clarify Org"));
        login(ownerEmail);
        when(aiCompletionClient.complete(anyList(), anyList()))
                .thenReturn(new AiCompletion("", 2, 1, List.of(new AiToolCall(
                        "call-bad", "create_task", "{\"priority\":\"HIGH\",\"organizationId\":\"" + UUID.randomUUID() + "\"}"))))
                .thenReturn(new AiCompletion("What should the task be called?", 2, 2));
        JsonNode reply = ask(organizationId, conversation(organizationId), "Prepare a task for the meeting follow-up.");
        assertEquals("What should the task be called?", reply.get("messages").get(1).get("content").asText());
        assertEquals("FAILED", reply.get("messages").get(1).get("toolCalls").get(0).get("status").asText());
        assertFalse(reply.get("messages").get(1).get("toolCalls").get(0).get("arguments").has("organizationId"));
        assertEquals(0, taskRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 10)).size());
    }

    @Test
    void lookupStaysInsideTheOrganization() throws Exception {
        String ownerEmail = email();
        String outsiderEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Lookup Org"));
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Hidden Lookup Org"));
        login(ownerEmail);
        UUID userId = UUID.fromString(objectMapper.readTree(getWithSession("/api/auth/me").getResponse().getContentAsString()).get("id").asText());
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        UUID overdueId = task(organizationId, "My overdue report", yesterday, "TODO", userId);
        task(organizationId, "Finished report", yesterday, "DONE", userId);
        task(organizationId, "Due today", today, "TODO", userId);
        login(outsiderEmail);
        task(outsiderOrgId, "Hidden overdue report", yesterday, "TODO", null);

        login(ownerEmail);
        when(aiCompletionClient.complete(anyList(), anyList()))
                .thenReturn(new AiCompletion("", 2, 1, List.of(new AiToolCall(
                        "call-list", "list_tasks", "{\"overdue\":true,\"mine\":true}"))))
                .thenReturn(new AiCompletion("You have one overdue task.", 3, 2));
        JsonNode reply = ask(organizationId, conversation(organizationId), "Show me my overdue tasks.");
        JsonNode tasks = reply.get("messages").get(1).get("toolCalls").get(0).get("result").get("tasks");
        assertEquals(1, tasks.size());
        assertEquals(overdueId.toString(), tasks.get(0).get("id").asText());
        assertEquals("My overdue report", tasks.get(0).get("title").asText());
        assertFalse(reply.get("messages").get(1).get("toolCalls").get(0).get("result").toString().contains("Hidden overdue report"));
    }

    @Test
    void rejectLeavesTheTaskUnsaved() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Reject Org"));
        login(ownerEmail);
        when(aiCompletionClient.complete(anyList(), anyList())).thenReturn(new AiCompletion(
                "Please confirm the follow-up task.",
                2,
                1,
                List.of(new AiToolCall("call-reject", "create_task", "{\"title\":\"Meeting follow-up\"}"))));
        UUID conversationId = conversation(organizationId);
        JsonNode proposed = ask(organizationId, conversationId, "Prepare a task for the meeting follow-up.");
        UUID toolCallId = UUID.fromString(proposed.get("messages").get(1).get("toolCalls").get(0).get("id").asText());
        JsonNode rejected = objectMapper.readTree(submit("/api/organizations/" + organizationId + "/conversations/"
                + conversationId + "/tool-calls/" + toolCallId + "/reject", Map.of()).getResponse().getContentAsString());
        assertEquals("REJECTED", rejected.get("messages").get(1).get("toolCalls").get(0).get("status").asText());
        assertEquals(400, submit("/api/organizations/" + organizationId + "/conversations/" + conversationId
                + "/tool-calls/" + toolCallId + "/confirm", Map.of()).getResponse().getStatus());
        assertEquals(0, taskRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 10)).size());
    }

    private UUID task(UUID organizationId, String title, String dueDate, String status, UUID assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("dueDate", dueDate);
        body.put("status", status);
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId.toString());
        }
        MvcResult created = submit("/api/organizations/" + organizationId + "/tasks", body);
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID conversation(UUID organizationId) throws Exception {
        MvcResult created = submit("/api/organizations/" + organizationId + "/conversations", Map.of());
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode ask(UUID organizationId, UUID conversationId, String content) throws Exception {
        MvcResult sent = submit("/api/organizations/" + organizationId + "/conversations/" + conversationId + "/messages",
                Map.of("content", content));
        assertEquals(200, sent.getResponse().getStatus(), sent.getResponse().getContentAsString());
        return objectMapper.readTree(sent.getResponse().getContentAsString());
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
        return "agent-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult submit(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).header("X-XSRF-TOKEN", csrfToken).cookie(csrfCookie);
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
