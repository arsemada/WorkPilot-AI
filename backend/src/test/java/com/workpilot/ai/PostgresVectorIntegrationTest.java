package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.service.ai.AiChatMessage;
import com.workpilot.ai.service.ai.AiCompletion;
import com.workpilot.ai.service.ai.AiCompletionClient;
import com.workpilot.ai.service.ai.EmbeddingClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "workpilot.ai.embedding-dimensions=1536",
        "workpilot.ai.vector-store=pgvector",
        "workpilot.ai.max-cosine-distance=0.2"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresVectorIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";
    private static final String SECRET = "alpha-shuttle-secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/vendor/postgresql");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmbeddingClient embeddingClient;

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
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<String> inputs = invocation.getArgument(0);
            return inputs.stream().map(PostgresVectorIntegrationTest::vectorFor).toList();
        });
        when(aiCompletionClient.complete(anyList(), anyList())).thenReturn(new AiCompletion("mocked grounded reply", 4, 2));
    }

    @Test
    void pgVectorRetrievalStaysInsideTheOrganization() throws Exception {
        String ownerEmail = email();
        String outsiderEmail = email();
        UUID organizationId = organizationId(register(ownerEmail, "Ada", "Owner", "Vector Org"));
        UUID outsiderOrgId = organizationId(register(outsiderEmail, "Otto", "Outsider", "Other Vector Org"));
        login(ownerEmail);

        MvcResult uploaded = upload(organizationId, "Travel notes", "The " + SECRET + " leaves at dawn.");
        assertEquals(201, uploaded.getResponse().getStatus(), uploaded.getResponse().getContentAsString());
        String documentId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();
        JsonNode ready = objectMapper.readTree(getWithSession("/api/organizations/" + organizationId + "/documents/" + documentId)
                .getResponse().getContentAsString());
        assertEquals("COMPLETED", ready.get("processingStatus").asText());
        assertEquals("COMPLETED", ready.get("indexingStatus").asText());

        JsonNode grounded = ask(organizationId, conversation(organizationId), "When does the alpha shuttle leave?");
        assertEquals(true, grounded.get("messages").get(1).get("documentSupport").asBoolean());
        assertEquals(documentId, grounded.get("messages").get(1).get("sources").get(0).get("documentId").asText());
        assertTrue(lastPrompt().get(lastPrompt().size() - 2).content().contains(SECRET));

        login(outsiderEmail);
        JsonNode isolated = ask(outsiderOrgId, conversation(outsiderOrgId), "When does the alpha shuttle leave?");
        assertEquals(false, isolated.get("messages").get(1).get("documentSupport").asBoolean());
        assertEquals(0, isolated.get("messages").get(1).get("sources").size());
        assertFalse(lastPrompt().get(lastPrompt().size() - 2).content().contains(SECRET));
    }

    private static float[] vectorFor(String text) {
        float[] vector = new float[1536];
        vector[text.contains("alpha") ? 0 : 1] = 1f;
        return vector;
    }

    private List<AiChatMessage> lastPrompt() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiCompletionClient, org.mockito.Mockito.atLeastOnce()).complete(captor.capture(), anyList());
        List<List<AiChatMessage>> prompts = captor.getAllValues();
        return prompts.get(prompts.size() - 1);
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

    private MvcResult upload(UUID organizationId, String title, String body) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequestBuilder request = multipart("/api/organizations/" + organizationId + "/documents")
                .file(file)
                .param("title", title)
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(csrfCookie)
                .session(session);
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
        body.put("organizationName", organizationName);
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
        return "vector-" + UUID.randomUUID() + "@example.com";
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
