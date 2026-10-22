package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.repository.OrganizationRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

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
    }

    @Test
    void summarizesOnlyTheCallerOrganization() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();

        MvcResult ownerRegistration = register(ownerEmail, "Ada", "Owner", "Dashboard Org");
        UUID organizationId = organizationId(ownerRegistration);
        UUID ownerId = userId(ownerRegistration);
        register(memberEmail, "Mia", "Member", null);
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Dashboard Org"));

        login(ownerEmail);
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER")).getResponse().getStatus());

        login(outsiderEmail);
        JsonNode emptySummary = read(getWithSession("/api/organizations/" + outsiderOrgId + "/dashboard"));
        assertEquals(0, emptySummary.get("totalTasks").asLong());
        assertEquals(0, emptySummary.get("inProgressTasks").asLong());
        assertEquals(0, emptySummary.get("completedTasks").asLong());
        assertEquals(0, emptySummary.get("overdueTasks").asLong());
        assertEquals(1, emptySummary.get("memberCount").asLong());
        assertEquals(0, emptySummary.get("recentTasks").size());
        assertEquals(0, read(getWithSession("/api/organizations/" + outsiderOrgId + "/dashboard/activity")).size());
        JsonNode emptyTrend = read(getWithSession("/api/organizations/" + outsiderOrgId + "/dashboard/trends"));
        assertEquals(14, emptyTrend.get("points").size());
        assertEquals(today.minusDays(13).toString(), emptyTrend.get("from").asText());
        assertEquals(today.toString(), emptyTrend.get("to").asText());
        assertEquals(0, emptyTrend.get("points").get(0).get("completedCount").asLong());
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/dashboard").getResponse().getStatus());

        login(ownerEmail);
        createTask(organizationId, "Overdue todo", "TODO", today.minusDays(1));
        createTask(organizationId, "Due today", "TODO", today);
        createTask(organizationId, "Due tomorrow", "TODO", today.plusDays(1));
        UUID looseNoteId = createTask(organizationId, "Loose note", "TODO", null);
        UUID overdueActiveId = createTask(organizationId, "Overdue active", "IN_PROGRESS", today.minusDays(1));
        createTask(organizationId, "Future active", "IN_PROGRESS", today.plusDays(1));
        UUID donePastId = createTask(organizationId, "Already done", "DONE", today.minusDays(1));
        createTask(organizationId, "Dropped", "CANCELLED", today.minusDays(1));
        UUID notesId = createTask(organizationId, "Finish the notes", "IN_PROGRESS", null);
        assertEquals(200, patch("/api/organizations/" + organizationId + "/tasks/" + notesId, Map.of("status", "DONE"))
                .getResponse().getStatus());

        JsonNode summary = read(getWithSession("/api/organizations/" + organizationId + "/dashboard"));
        assertEquals(9, summary.get("totalTasks").asLong());
        assertEquals(4, summary.get("todoTasks").asLong());
        assertEquals(2, summary.get("inProgressTasks").asLong());
        assertEquals(2, summary.get("completedTasks").asLong());
        assertEquals(1, summary.get("cancelledTasks").asLong());
        assertEquals(2, summary.get("overdueTasks").asLong());
        assertEquals(2, summary.get("memberCount").asLong());
        assertEquals(5, summary.get("recentTasks").size());
        assertTrue(summary.get("recentTasks").toString().contains(notesId.toString()));

        JsonNode todayTrend = read(getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today + "&to=" + today));
        assertEquals(1, todayTrend.get("points").size());
        assertEquals(2, todayTrend.get("points").get(0).get("completedCount").asLong());
        LocalDate yesterday = today.minusDays(1);
        JsonNode yesterdayTrend = read(getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + yesterday + "&to=" + yesterday));
        assertEquals(0, yesterdayTrend.get("points").get(0).get("completedCount").asLong());
        LocalDate tomorrow = today.plusDays(1);
        JsonNode tomorrowTrend = read(getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + tomorrow + "&to=" + tomorrow));
        assertEquals(0, tomorrowTrend.get("points").get(0).get("completedCount").asLong());

        assertEquals(400, getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + tomorrow + "&to=" + today)
                .getResponse().getStatus());
        assertEquals(400, getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today.minusDays(90))
                .getResponse().getStatus());
        assertEquals(400, getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today.minusDays(90) + "&to=" + today)
                .getResponse().getStatus());
        assertEquals(200, getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today.minusDays(89) + "&to=" + today)
                .getResponse().getStatus());

        assertEquals(200, patch("/api/organizations/" + organizationId + "/tasks/" + donePastId, Map.of("status", "IN_PROGRESS"))
                .getResponse().getStatus());
        JsonNode reopened = read(getWithSession("/api/organizations/" + organizationId + "/dashboard"));
        assertEquals(1, reopened.get("completedTasks").asLong());
        assertEquals(3, reopened.get("inProgressTasks").asLong());
        assertEquals(2, read(getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today + "&to=" + today))
                .get("points").get(0).get("completedCount").asLong());

        assertEquals(200, patch("/api/organizations/" + organizationId + "/tasks/" + overdueActiveId, Map.of("title", "Overdue active work"))
                .getResponse().getStatus());

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        User owner = userRepository.findById(ownerId).orElseThrow();
        Task historical = new Task();
        historical.setOrganization(organization);
        historical.setCreatedBy(owner);
        historical.setTitle("Historical done");
        historical.setStatus(TaskStatus.DONE);
        historical.setPriority(TaskPriority.LOW);
        taskRepository.saveAndFlush(historical);

        JsonNode withHistory = read(getWithSession("/api/organizations/" + organizationId + "/dashboard"));
        assertEquals(10, withHistory.get("totalTasks").asLong());
        assertEquals(2, withHistory.get("completedTasks").asLong());
        assertEquals(2, read(getWithSession(
                "/api/organizations/" + organizationId + "/dashboard/trends?from=" + today + "&to=" + today))
                .get("points").get(0).get("completedCount").asLong());
        String activity = read(getWithSession("/api/organizations/" + organizationId + "/dashboard/activity")).toString();
        assertTrue(activity.contains("completed \\\"Finish the notes\\\""));
        assertTrue(activity.contains("changed \\\"Already done\\\" from DONE to IN_PROGRESS"));
        assertTrue(activity.contains("updated \\\"Overdue active work\\\""));
        assertFalse(activity.contains("Historical done"));

        login(memberEmail);
        assertEquals(10, read(getWithSession("/api/organizations/" + organizationId + "/dashboard")).get("totalTasks").asLong());

        login(ownerEmail);
        assertEquals(204, delete("/api/organizations/" + organizationId + "/tasks/" + looseNoteId)
                .getResponse().getStatus());
        JsonNode afterDelete = read(getWithSession("/api/organizations/" + organizationId + "/dashboard"));
        assertEquals(9, afterDelete.get("totalTasks").asLong());
        assertFalse(afterDelete.get("recentTasks").toString().contains("Loose note"));
        assertTrue(read(getWithSession("/api/organizations/" + organizationId + "/dashboard/activity")).toString()
                .contains("deleted \\\"Loose note\\\""));

        assertEquals(401, mockMvc.perform(get("/api/organizations/" + organizationId + "/dashboard")).andReturn()
                .getResponse().getStatus());
    }

    private UUID createTask(UUID organizationId, String title, String status, LocalDate dueDate) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", title);
        body.put("status", status);
        if (dueDate != null) {
            body.put("dueDate", dueDate.toString());
        }
        MvcResult created = submit("/api/organizations/" + organizationId + "/tasks", body);
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode read(MvcResult result) throws Exception {
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult register(String email, String firstName, String lastName, String organizationName) throws Exception {
        Map<String, String> body = new HashMap<>();
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

    private UUID userId(MvcResult registered) throws Exception {
        return UUID.fromString(objectMapper.readTree(registered.getResponse().getContentAsString()).get("id").asText());
    }

    private String email() {
        return "dash-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult submit(String path, Object body) throws Exception {
        return exchange(post(path), body);
    }

    private MvcResult patch(String path, Object body) throws Exception {
        return exchange(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path), body);
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
