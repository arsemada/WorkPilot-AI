package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.repository.ActivityEventRepository;
import com.workpilot.ai.service.WorkflowEngine;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private ActivityEventRepository activityEventRepository;

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
    void assignmentNotifiesOnceAndADisabledRuleDoesNothing() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Workflow Org"));
        login(ownerEmail);
        UUID userId = UUID.fromString(objectMapper.readTree(getWithSession("/api/auth/me").getResponse().getContentAsString()).get("id").asText());
        JsonNode rules = rules(organizationId);
        UUID assignmentRule = ruleId(rules, "TASK_ASSIGNED");
        MvcResult renamed = submit(patch("/api/organizations/" + organizationId + "/workflows/" + assignmentRule),
                Map.of("name", "Assignment notice"));
        assertEquals(200, renamed.getResponse().getStatus(), renamed.getResponse().getContentAsString());

        String taskId = createTask(organizationId, "Quarterly report", userId, null);
        JsonNode notifications = notifications();
        assertEquals(1, countTitle(notifications, "Task assigned"));
        assertTrue(notifications.get(0).get("body").asText().contains("Quarterly report"));
        assertFalse(notifications.get(0).get("read").asBoolean());
        JsonNode executions = executions(organizationId);
        assertEquals("SUCCEEDED", execution(executions, "TASK_ASSIGNED").get("status").asText());
        assertTrue(activityEventRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 20)).stream()
                .anyMatch(event -> event.getType() == ActivityType.WORKFLOW_SUCCEEDED
                        && event.getSummary().contains("Assignment notice")));

        MvcResult read = submit("/api/notifications/" + notifications.get(0).get("id").asText() + "/read", Map.of());
        assertEquals(200, read.getResponse().getStatus(), read.getResponse().getContentAsString());
        assertEquals(0, objectMapper.readTree(getWithSession("/api/notifications/unread-count").getResponse().getContentAsString()).get("count").asInt());

        MvcResult revised = submit(patch("/api/organizations/" + organizationId + "/tasks/" + taskId),
                Map.of("title", "Quarterly report revised"));
        assertEquals(200, revised.getResponse().getStatus(), revised.getResponse().getContentAsString());
        assertEquals(1, countTitle(notifications(), "Task assigned"));

        assertEquals(200, submit("/api/organizations/" + organizationId + "/workflows/" + assignmentRule + "/disable", Map.of())
                .getResponse().getStatus());
        createTask(organizationId, "Silent task", userId, null);
        assertEquals(1, countTitle(notifications(), "Task assigned"));
        assertEquals(1, countEvent(executions(organizationId), "TASK_ASSIGNED"));
    }

    @Test
    void overdueScanIsIdempotentAndAccessIsChecked() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Overdue Org"));
        register(memberEmail, "Mia", "Member", null);
        register(outsiderEmail, "Otto", "Outsider", "Other Workflow Org");
        login(ownerEmail);
        UUID userId = UUID.fromString(objectMapper.readTree(getWithSession("/api/auth/me").getResponse().getContentAsString()).get("id").asText());
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of("email", memberEmail, "role", "MEMBER"))
                .getResponse().getStatus());
        createTask(organizationId, "Launch checklist", userId, Map.of("dueDate", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()));

        workflowEngine.scanOverdue(LocalDate.now(ZoneOffset.UTC));
        workflowEngine.scanOverdue(LocalDate.now(ZoneOffset.UTC));
        JsonNode ownerNotifications = notifications();
        assertEquals(1, countTitle(ownerNotifications, "Task overdue"));
        assertEquals("SUCCEEDED", execution(executions(organizationId), "TASK_OVERDUE").get("status").asText());
        String notificationId = ownerNotifications.get(0).get("id").asText();

        login(outsiderEmail);
        MvcResult hidden = getWithSession("/api/organizations/" + organizationId + "/workflows");
        assertEquals(404, hidden.getResponse().getStatus());
        assertEquals("Organization not found", objectMapper.readTree(hidden.getResponse().getContentAsString()).get("message").asText());

        login(memberEmail);
        MvcResult foreignNotification = submit("/api/notifications/" + notificationId + "/read", Map.of());
        assertEquals(404, foreignNotification.getResponse().getStatus());
        assertEquals("Notification not found",
                objectMapper.readTree(foreignNotification.getResponse().getContentAsString()).get("message").asText());
        assertEquals(0, notifications().size());
        UUID ruleId = ruleId(rules(organizationId), "TASK_OVERDUE");
        MvcResult denied = submit("/api/organizations/" + organizationId + "/workflows/" + ruleId + "/disable", Map.of());
        assertEquals(403, denied.getResponse().getStatus());
        assertEquals("You do not have permission to perform this action",
                objectMapper.readTree(denied.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void documentProcessingFollowsTheRuleAndSummaryFailureStaysVisible() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Document Workflow Org"));
        login(ownerEmail);
        UUID processRule = ruleId(rules(organizationId), "DOCUMENT_UPLOADED");
        assertEquals(200, submit("/api/organizations/" + organizationId + "/workflows/" + processRule + "/disable", Map.of())
                .getResponse().getStatus());
        JsonNode pendingUpload = objectMapper.readTree(upload(organizationId, "Pending notes").getResponse().getContentAsString());
        JsonNode pending = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/documents/" + pendingUpload.get("id").asText()).getResponse().getContentAsString());
        assertEquals("PENDING", pending.get("processingStatus").asText());
        assertEquals(0, countEvent(executions(organizationId), "DOCUMENT_UPLOADED"));

        assertEquals(200, submit("/api/organizations/" + organizationId + "/workflows/" + processRule + "/enable", Map.of())
                .getResponse().getStatus());
        UUID summaryRule = ruleId(rules(organizationId), "DOCUMENT_PROCESSED");
        assertEquals(200, submit("/api/organizations/" + organizationId + "/workflows/" + summaryRule + "/enable", Map.of())
                .getResponse().getStatus());
        MvcResult uploaded = upload(organizationId, "Launch notes");
        assertEquals(201, uploaded.getResponse().getStatus(), uploaded.getResponse().getContentAsString());
        String documentId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();
        JsonNode document = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/documents/" + documentId).getResponse().getContentAsString());
        assertEquals("COMPLETED", document.get("processingStatus").asText());

        JsonNode executions = executions(organizationId);
        assertEquals("SUCCEEDED", execution(executions, "DOCUMENT_UPLOADED").get("status").asText());
        JsonNode summary = execution(executions, "DOCUMENT_PROCESSED");
        assertEquals("FAILED", summary.get("status").asText());
        assertNotNull(summary.get("errorMessage").asText());
        assertFalse(summary.get("errorMessage").asText().isBlank());
        assertTrue(summary.get("resultMessage").isNull());
        assertEquals(0, countTitle(notifications(), "Document summary"));

        workflowEngine.retryDue();
        JsonNode retried = execution(executions(organizationId), "DOCUMENT_PROCESSED");
        assertEquals("FAILED", retried.get("status").asText());
        assertEquals(2, retried.get("attemptCount").asInt());
        assertFalse(retried.get("errorMessage").asText().isBlank());
    }

    @Test
    void activitySummaryIsSentOnceAndUnsupportedRulesAreRejected() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Activity Org"));
        login(ownerEmail);
        UUID userId = UUID.fromString(objectMapper.readTree(getWithSession("/api/auth/me").getResponse().getContentAsString()).get("id").asText());
        createTask(organizationId, "Board notes", userId, null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        workflowEngine.summarizeActivity(today);
        workflowEngine.summarizeActivity(today);
        JsonNode notifications = notifications();
        assertEquals(1, countTitle(notifications, "Task activity"));
        assertTrue(notifications.get(0).get("body").asText().contains("Board notes"));
        assertEquals("SUCCEEDED", execution(executions(organizationId), "SCHEDULED_ACTIVITY").get("status").asText());

        MvcResult rejected = submit("/api/organizations/" + organizationId + "/workflows", Map.of(
                "name", "Run SQL",
                "eventType", "TASK_ASSIGNED",
                "actionType", "PROCESS_DOCUMENT"));
        assertEquals(400, rejected.getResponse().getStatus());
        assertEquals("That trigger and action are not supported together.",
                objectMapper.readTree(rejected.getResponse().getContentAsString()).get("message").asText());
        MvcResult duplicate = submit("/api/organizations/" + organizationId + "/workflows", Map.of(
                "name", "Second assignment",
                "eventType", "TASK_ASSIGNED",
                "actionType", "NOTIFY_ASSIGNEE"));
        assertEquals(400, duplicate.getResponse().getStatus());
    }

    private JsonNode rules(UUID organizationId) throws Exception {
        MvcResult result = getWithSession("/api/organizations/" + organizationId + "/workflows");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode executions(UUID organizationId) throws Exception {
        MvcResult result = getWithSession("/api/organizations/" + organizationId + "/workflows/executions");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode notifications() throws Exception {
        MvcResult result = getWithSession("/api/notifications");
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static UUID ruleId(JsonNode rules, String eventType) {
        for (JsonNode rule : rules) {
            if (eventType.equals(rule.get("eventType").asText())) {
                return UUID.fromString(rule.get("id").asText());
            }
        }
        throw new AssertionError("Missing rule " + eventType);
    }

    private static JsonNode execution(JsonNode executions, String eventType) {
        for (JsonNode execution : executions) {
            if (eventType.equals(execution.get("eventType").asText())) {
                return execution;
            }
        }
        throw new AssertionError("Missing execution " + eventType);
    }

    private static int countEvent(JsonNode executions, String eventType) {
        int count = 0;
        for (JsonNode execution : executions) {
            if (eventType.equals(execution.get("eventType").asText())) {
                count++;
            }
        }
        return count;
    }

    private static int countTitle(JsonNode notifications, String title) {
        int count = 0;
        for (JsonNode notification : notifications) {
            if (title.equals(notification.get("title").asText())) {
                count++;
            }
        }
        return count;
    }

    private String createTask(UUID organizationId, String title, UUID assigneeId, Map<String, String> extra) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("assigneeId", assigneeId.toString());
        if (extra != null) {
            body.putAll(extra);
        }
        MvcResult created = submit("/api/organizations/" + organizationId + "/tasks", body);
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult upload(UUID organizationId, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "workflow notes".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequestBuilder request = multipart("/api/organizations/" + organizationId + "/documents")
                .file(file)
                .param("title", title)
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(csrfCookie);
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        updateCookies(result);
        return result;
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
        return "workflow-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult submit(String path, Object body) throws Exception {
        return submit(post(path), body);
    }

    private MvcResult submit(MockHttpServletRequestBuilder request, Object body) throws Exception {
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
