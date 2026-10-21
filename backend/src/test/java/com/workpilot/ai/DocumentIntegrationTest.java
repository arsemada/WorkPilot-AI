package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.repository.DocumentRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

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
    void uploadsAndIsolatesDocuments() throws Exception {
        String ownerEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();

        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Document Org"));
        register(memberEmail, "Mia", "Member", null);
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Org"));

        login(ownerEmail);
        assertEquals(201, submit("/api/organizations/" + organizationId + "/members", Map.of(
                "email", memberEmail,
                "role", "MEMBER")).getResponse().getStatus());

        MvcResult missingTitle = upload(organizationId, "  ", null, "notes.txt", "text/plain", "hello");
        assertEquals(400, missingTitle.getResponse().getStatus());
        assertTrue(missingTitle.getResponse().getContentAsString().contains("Title is required"));

        MvcResult blockedType = upload(organizationId, "Script", null, "run.exe", "application/octet-stream", "nope");
        assertEquals(400, blockedType.getResponse().getStatus());
        assertTrue(blockedType.getResponse().getContentAsString().contains("not supported"));

        byte[] contents = "unique-needle in the file".getBytes(StandardCharsets.UTF_8);
        MvcResult created = upload(organizationId, "  Launch notes  ", "Quarter plan", "notes.txt", "text/plain", new String(contents, StandardCharsets.UTF_8));
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        JsonNode document = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID documentId = UUID.fromString(document.get("id").asText());
        assertEquals("Launch notes", document.get("title").asText());
        assertEquals("notes.txt", document.get("originalFileName").asText());
        assertEquals("text/plain", document.get("contentType").asText());
        assertEquals(contents.length, document.get("sizeBytes").asLong());
        assertTrue(document.get("textIndexed").asBoolean());
        assertTrue(document.get("textPreview").asText().contains("unique-needle"));
        assertEquals("Ada Owner", document.get("uploadedByName").asText());
        assertTrue(documentRepository.findById(documentId).isPresent());

        MvcResult downloaded = getWithSession("/api/organizations/" + organizationId + "/documents/" + documentId + "/content");
        assertEquals(200, downloaded.getResponse().getStatus());
        assertEquals(new String(contents, StandardCharsets.UTF_8), downloaded.getResponse().getContentAsString());
        assertTrue(downloaded.getResponse().getHeader("Content-Disposition").contains("notes.txt"));

        MvcResult second = upload(organizationId, "Diagram", null, "sketch.png", "image/png", "png-bytes");
        assertEquals(201, second.getResponse().getStatus(), second.getResponse().getContentAsString());
        JsonNode image = objectMapper.readTree(second.getResponse().getContentAsString());
        assertFalse(image.get("textIndexed").asBoolean());

        MvcResult found = getWithSession("/api/organizations/" + organizationId + "/documents?q=UNIQUE-NEEDLE&page=0&size=10");
        assertEquals(200, found.getResponse().getStatus());
        JsonNode page = objectMapper.readTree(found.getResponse().getContentAsString());
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals("Launch notes", page.get("items").get(0).get("title").asText());

        MvcResult paged = getWithSession("/api/organizations/" + organizationId + "/documents?page=0&size=1&sort=title&direction=asc");
        JsonNode firstPage = objectMapper.readTree(paged.getResponse().getContentAsString());
        assertEquals(2, firstPage.get("totalElements").asInt());
        assertEquals(2, firstPage.get("totalPages").asInt());
        assertEquals("Diagram", firstPage.get("items").get(0).get("title").asText());

        login(memberEmail);
        MvcResult memberRead = getWithSession("/api/organizations/" + organizationId + "/documents/" + documentId);
        assertEquals(200, memberRead.getResponse().getStatus());
        MvcResult memberDelete = delete("/api/organizations/" + organizationId + "/documents/" + documentId);
        assertEquals(403, memberDelete.getResponse().getStatus());

        login(outsiderEmail);
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/documents").getResponse().getStatus());
        assertEquals(404, getWithSession("/api/organizations/" + outsiderOrgId + "/documents/" + documentId).getResponse().getStatus());

        login(ownerEmail);
        Map<String, Object> rename = new HashMap<>();
        rename.put("title", "Launch notes revised");
        rename.put("description", null);
        MvcResult renamed = patch("/api/organizations/" + organizationId + "/documents/" + documentId, rename);
        assertEquals(200, renamed.getResponse().getStatus(), renamed.getResponse().getContentAsString());
        JsonNode updated = objectMapper.readTree(renamed.getResponse().getContentAsString());
        assertEquals("Launch notes revised", updated.get("title").asText());
        assertTrue(updated.get("description").isNull());

        MvcResult removed = delete("/api/organizations/" + organizationId + "/documents/" + documentId);
        assertEquals(204, removed.getResponse().getStatus());
        assertFalse(documentRepository.findById(documentId).isPresent());
        assertEquals(404, getWithSession("/api/organizations/" + organizationId + "/documents/" + documentId + "/content").getResponse().getStatus());
    }

    private MvcResult upload(UUID organizationId, String title, String description, String fileName, String contentType, String body) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequestBuilder request = multipart("/api/organizations/" + organizationId + "/documents")
                .file(file)
                .param("title", title);
        if (description != null) {
            request.param("description", description);
        }
        request.header("X-XSRF-TOKEN", csrfToken).cookie(csrfCookie);
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
        return "doc-" + UUID.randomUUID() + "@example.com";
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
