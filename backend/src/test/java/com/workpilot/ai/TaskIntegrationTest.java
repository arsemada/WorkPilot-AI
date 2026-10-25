package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.repository.TaskRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class TaskIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

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
    void managesTasksInsideOneOrganization() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();

        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Task Org"));
        UUID memberId = userId(register(memberEmail, "Mia", "Member", null));
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Org"));

        login(ownerEmail);
        MvcResult added = submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER"));
        assertEquals(201, added.getResponse().getStatus());

        MvcResult invalid = submit("/api/organizations/" + organizationId + "/tasks", Map.of("title", "  "));
        assertEquals(400, invalid.getResponse().getStatus());

        MvcResult created = submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "  Write the brief  ",
                "description", "Cover the launch plan",
                "priority", "HIGH",
                "dueDate", "2026-06-01",
                "assigneeId", memberId.toString()));
        assertEquals(201, created.getResponse().getStatus());
        JsonNode task = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID taskId = UUID.fromString(task.get("id").asText());
        assertEquals("Write the brief", task.get("title").asText());
        assertEquals("TODO", task.get("status").asText());
        assertEquals("HIGH", task.get("priority").asText());
        assertEquals(memberId.toString(), task.get("assigneeId").asText());
        assertEquals("Mia Member", task.get("assigneeName").asText());
        assertTrue(taskRepository.findById(taskId).isPresent());

        MvcResult outsiderAssign = submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Bad assignment",
                "assigneeId", userId(objectMapper.readTree(register(email(), "No", "One", null).getResponse().getContentAsString())).toString()));
        assertEquals(400, outsiderAssign.getResponse().getStatus());
        assertTrue(outsiderAssign.getResponse().getContentAsString().contains("Assignee must be a member"));

        MvcResult fetched = getWithSession("/api/organizations/" + organizationId + "/tasks/" + taskId);
        assertEquals(200, fetched.getResponse().getStatus());

        login(memberEmail);
        MvcResult memberRead = getWithSession("/api/organizations/" + organizationId + "/tasks/" + taskId);
        assertEquals(200, memberRead.getResponse().getStatus());

        MvcResult illegal = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "DONE"));
        assertEquals(400, illegal.getResponse().getStatus());
        assertTrue(illegal.getResponse().getContentAsString().contains("Cannot change status from TODO to DONE"));

        MvcResult started = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "IN_PROGRESS"));
        assertEquals(200, started.getResponse().getStatus());
        assertEquals("IN_PROGRESS", objectMapper.readTree(started.getResponse().getContentAsString()).get("status").asText());

        MvcResult forbiddenDelete = delete("/api/organizations/" + organizationId + "/tasks/" + taskId);
        assertEquals(403, forbiddenDelete.getResponse().getStatus());

        login(outsiderEmail);
        MvcResult hiddenList = getWithSession("/api/organizations/" + organizationId + "/tasks");
        assertEquals(404, hiddenList.getResponse().getStatus());
        MvcResult hiddenTask = getWithSession("/api/organizations/" + outsiderOrgId + "/tasks/" + taskId);
        assertEquals(404, hiddenTask.getResponse().getStatus());

        login(ownerEmail);
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Alpha follow-up",
                "description", "unique-needle in the notes",
                "status", "DONE",
                "priority", "LOW",
                "dueDate", "2026-01-15"));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Beta later",
                "priority", "URGENT",
                "dueDate", "2026-12-20"));

        JsonNode byStatus = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?status=IN_PROGRESS").getResponse().getContentAsString());
        assertEquals(1, byStatus.get("totalElements").asInt());
        assertEquals(taskId.toString(), byStatus.get("items").get(0).get("id").asText());

        JsonNode byPriority = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?priority=LOW").getResponse().getContentAsString());
        assertEquals(1, byPriority.get("totalElements").asInt());
        assertEquals("Alpha follow-up", byPriority.get("items").get(0).get("title").asText());

        JsonNode byAssignee = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?assigneeId=" + memberId).getResponse().getContentAsString());
        assertEquals(1, byAssignee.get("totalElements").asInt());

        JsonNode unassigned = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?unassigned=true").getResponse().getContentAsString());
        assertEquals(2, unassigned.get("totalElements").asInt());

        JsonNode due = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?dueAfter=2026-06-01&dueBefore=2026-06-30").getResponse().getContentAsString());
        assertEquals(1, due.get("totalElements").asInt());
        assertEquals("Write the brief", due.get("items").get(0).get("title").asText());

        JsonNode search = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?q=UNIQUE-NEEDLE").getResponse().getContentAsString());
        assertEquals(1, search.get("totalElements").asInt());

        JsonNode page = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?sort=title&direction=asc&size=2&page=0").getResponse().getContentAsString());
        assertEquals(3, page.get("totalElements").asInt());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(2, page.get("items").size());
        assertEquals("Alpha follow-up", page.get("items").get(0).get("title").asText());

        MvcResult unauthenticated = mockMvc.perform(get("/api/organizations/" + organizationId + "/tasks")).andReturn();
        assertEquals(401, unauthenticated.getResponse().getStatus());

        MvcResult removed = delete("/api/organizations/" + organizationId + "/tasks/" + taskId);
        assertEquals(204, removed.getResponse().getStatus());
        assertFalse(taskRepository.findById(taskId).isPresent());
        MvcResult missing = getWithSession("/api/organizations/" + organizationId + "/tasks/" + taskId);
        assertEquals(404, missing.getResponse().getStatus());
    }

    @Test
    void completingARecurringTaskCreatesTheNextCopy() throws Exception {
        String ownerEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Recurrence Org"));
        login(ownerEmail);

        MvcResult created = submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Weekly status",
                "priority", "MEDIUM",
                "dueDate", "2026-10-20",
                "recurrence", "WEEKLY"));
        assertEquals(201, created.getResponse().getStatus());
        JsonNode task = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID taskId = UUID.fromString(task.get("id").asText());
        assertEquals("WEEKLY", task.get("recurrence").asText());

        MvcResult started = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "IN_PROGRESS"));
        assertEquals(200, started.getResponse().getStatus());
        MvcResult done = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "DONE"));
        assertEquals(200, done.getResponse().getStatus());

        JsonNode open = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?status=TODO&q=Weekly")
                .getResponse().getContentAsString());
        assertEquals(1, open.get("totalElements").asInt());
        JsonNode next = open.get("items").get(0);
        assertEquals("Weekly status", next.get("title").asText());
        assertEquals("2026-10-27", next.get("dueDate").asText());
        assertEquals("WEEKLY", next.get("recurrence").asText());
        assertEquals(taskId.toString(), next.get("spawnedFromId").asText());

        MvcResult reopen = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "IN_PROGRESS"));
        assertEquals(200, reopen.getResponse().getStatus());
        MvcResult doneAgain = patch("/api/organizations/" + organizationId + "/tasks/" + taskId, Map.of("status", "DONE"));
        assertEquals(200, doneAgain.getResponse().getStatus());
        JsonNode stillOne = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?status=TODO&q=Weekly")
                .getResponse().getContentAsString());
        assertEquals(1, stillOne.get("totalElements").asInt());

        MvcResult monthly = submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Monthly close",
                "dueDate", "2026-01-31",
                "recurrence", "MONTHLY"));
        assertEquals(201, monthly.getResponse().getStatus());
        UUID monthlyId = UUID.fromString(objectMapper.readTree(monthly.getResponse().getContentAsString()).get("id").asText());
        patch("/api/organizations/" + organizationId + "/tasks/" + monthlyId, Map.of("status", "IN_PROGRESS"));
        patch("/api/organizations/" + organizationId + "/tasks/" + monthlyId, Map.of("status", "DONE"));
        JsonNode nextMonth = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks?status=TODO&q=Monthly")
                .getResponse().getContentAsString());
        assertEquals("2026-02-28", nextMonth.get("items").get(0).get("dueDate").asText());
    }

    @Test
    void myWorkAndBoardGroupAssignedTasks() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Work Board Org"));
        UUID memberId = userId(register(memberEmail, "Mia", "Member", null));
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Work Org"));

        login(ownerEmail);
        MvcResult added = submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER"));
        assertEquals(201, added.getResponse().getStatus());

        UUID ownerId = userId(objectMapper.readTree(getWithSession("/api/auth/me").getResponse().getContentAsString()));

        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Overdue for Mia",
                "dueDate", today.minusDays(2).toString(),
                "assigneeId", memberId.toString()));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Due today for Mia",
                "dueDate", today.toString(),
                "assigneeId", memberId.toString()));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Started for Mia",
                "status", "IN_PROGRESS",
                "dueDate", today.plusDays(7).toString(),
                "assigneeId", memberId.toString()));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Later for Mia",
                "dueDate", today.plusDays(14).toString(),
                "assigneeId", memberId.toString()));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Owner only overdue",
                "dueDate", today.minusDays(1).toString(),
                "assigneeId", ownerId.toString()));
        submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Finished for Mia",
                "status", "DONE",
                "assigneeId", memberId.toString()));

        login(memberEmail);
        JsonNode mine = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/my-work").getResponse().getContentAsString());
        assertEquals(today.toString(), mine.get("today").asText());
        assertEquals("Overdue for Mia", mine.get("overdue").get(0).get("title").asText());
        assertEquals(1, mine.get("overdue").size());
        assertEquals("Due today for Mia", mine.get("dueToday").get(0).get("title").asText());
        assertEquals(1, mine.get("dueToday").size());
        assertEquals("Started for Mia", mine.get("inProgress").get(0).get("title").asText());
        assertEquals(1, mine.get("inProgress").size());
        assertEquals("Later for Mia", mine.get("upNext").get(0).get("title").asText());
        assertEquals(1, mine.get("upNext").size());

        login(ownerEmail);
        JsonNode ownerWork = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/my-work").getResponse().getContentAsString());
        assertEquals(1, ownerWork.get("overdue").size());
        assertEquals("Owner only overdue", ownerWork.get("overdue").get(0).get("title").asText());
        assertEquals(0, ownerWork.get("dueToday").size());

        JsonNode board = objectMapper.readTree(getWithSession(
                "/api/organizations/" + organizationId + "/tasks/board").getResponse().getContentAsString());
        assertTrue(titles(board.get("todo")).contains("Overdue for Mia"));
        assertTrue(titles(board.get("todo")).contains("Owner only overdue"));
        assertTrue(titles(board.get("inProgress")).contains("Started for Mia"));
        assertTrue(titles(board.get("done")).contains("Finished for Mia"));
        assertEquals(0, board.get("cancelled").size());

        login(outsiderEmail);
        MvcResult hiddenMine = getWithSession("/api/organizations/" + organizationId + "/my-work");
        assertEquals(404, hiddenMine.getResponse().getStatus());
        MvcResult hiddenBoard = getWithSession("/api/organizations/" + organizationId + "/tasks/board");
        assertEquals(404, hiddenBoard.getResponse().getStatus());
        assertEquals(200, getWithSession("/api/organizations/" + outsiderOrgId + "/my-work").getResponse().getStatus());

        MvcResult unauthenticated = mockMvc.perform(get("/api/organizations/" + organizationId + "/my-work")).andReturn();
        assertEquals(401, unauthenticated.getResponse().getStatus());
    }

    private static List<String> titles(JsonNode items) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            values.add(item.get("title").asText());
        }
        return values;
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

    private UUID userId(JsonNode profile) {
        return UUID.fromString(profile.get("id").asText());
    }

    private String email() {
        return "task-" + UUID.randomUUID() + "@example.com";
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
