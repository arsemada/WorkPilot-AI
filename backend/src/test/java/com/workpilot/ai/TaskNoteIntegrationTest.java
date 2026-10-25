package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class TaskNoteIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void commentsAndDocumentLinksStayInsideTheOrganization() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();

        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Notes Org"));
        register(memberEmail, "Mia", "Member", null);
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Notes Org"));

        login(ownerEmail);
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER")).getResponse().getStatus());

        JsonNode task = objectMapper.readTree(submit("/api/organizations/" + organizationId + "/tasks", Map.of(
                "title", "Write the brief")).getResponse().getContentAsString());
        UUID taskId = UUID.fromString(task.get("id").asText());

        JsonNode document = objectMapper.readTree(upload(organizationId, "Launch notes", "notes.txt", "cover the launch")
                .getResponse().getContentAsString());
        UUID documentId = UUID.fromString(document.get("id").asText());

        login(outsiderEmail);
        JsonNode outsiderDoc = objectMapper.readTree(upload(outsiderOrgId, "Secret notes", "secret.txt", "keep out")
                .getResponse().getContentAsString());
        UUID outsiderDocumentId = UUID.fromString(outsiderDoc.get("id").asText());

        login(memberEmail);
        String commentsPath = "/api/organizations/" + organizationId + "/tasks/" + taskId + "/comments";
        String documentsPath = "/api/organizations/" + organizationId + "/tasks/" + taskId + "/documents";

        JsonNode emptyComments = objectMapper.readTree(getWithSession(commentsPath).getResponse().getContentAsString());
        assertEquals(0, emptyComments.size());

        MvcResult blank = submit(commentsPath, Map.of("body", "   "));
        assertEquals(400, blank.getResponse().getStatus());

        MvcResult created = submit(commentsPath, Map.of("body", "  Check the launch date  "));
        assertEquals(201, created.getResponse().getStatus());
        JsonNode comment = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID commentId = UUID.fromString(comment.get("id").asText());
        assertEquals("Check the launch date", comment.get("body").asText());
        assertEquals("Mia Member", comment.get("authorName").asText());

        login(ownerEmail);
        MvcResult ownerComment = submit(commentsPath, Map.of("body", "Looks good"));
        assertEquals(201, ownerComment.getResponse().getStatus());
        UUID ownerCommentId = UUID.fromString(objectMapper.readTree(ownerComment.getResponse().getContentAsString())
                .get("id").asText());

        JsonNode listed = objectMapper.readTree(getWithSession(commentsPath).getResponse().getContentAsString());
        assertEquals(2, listed.size());
        assertEquals("Check the launch date", listed.get(0).get("body").asText());

        MvcResult linked = submit(documentsPath, Map.of("documentId", documentId.toString()));
        assertEquals(201, linked.getResponse().getStatus());
        assertEquals("Launch notes", objectMapper.readTree(linked.getResponse().getContentAsString()).get("title").asText());

        MvcResult duplicate = submit(documentsPath, Map.of("documentId", documentId.toString()));
        assertEquals(400, duplicate.getResponse().getStatus());
        assertTrue(duplicate.getResponse().getContentAsString().contains("already linked"));

        MvcResult foreign = submit(documentsPath, Map.of("documentId", outsiderDocumentId.toString()));
        assertEquals(404, foreign.getResponse().getStatus());

        login(memberEmail);
        MvcResult hiddenOwnerComment = delete(commentsPath + "/" + ownerCommentId);
        assertEquals(403, hiddenOwnerComment.getResponse().getStatus());

        MvcResult removedOwn = delete(commentsPath + "/" + commentId);
        assertEquals(204, removedOwn.getResponse().getStatus());
        assertEquals(1, objectMapper.readTree(getWithSession(commentsPath).getResponse().getContentAsString()).size());

        MvcResult unlinked = delete(documentsPath + "/" + documentId);
        assertEquals(204, unlinked.getResponse().getStatus());
        assertEquals(0, objectMapper.readTree(getWithSession(documentsPath).getResponse().getContentAsString()).size());

        login(outsiderEmail);
        assertEquals(404, getWithSession(commentsPath).getResponse().getStatus());
        assertEquals(404, getWithSession(documentsPath).getResponse().getStatus());

        MvcResult unauthenticated = mockMvc.perform(get(commentsPath)).andReturn();
        assertEquals(401, unauthenticated.getResponse().getStatus());
    }

    private MvcResult upload(UUID organizationId, String title, String fileName, String body) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "text/plain", body.getBytes(StandardCharsets.UTF_8));
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

    private String email() {
        return "note-" + UUID.randomUUID() + "@example.com";
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
