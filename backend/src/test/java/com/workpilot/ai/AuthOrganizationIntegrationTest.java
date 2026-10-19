package com.workpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.UserRepository;
import com.workpilot.ai.security.CustomUserDetails;
import com.workpilot.ai.security.OrganizationAccessService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthOrganizationIntegrationTest {

    private static final String PASSWORD = "s3cure-Phrase-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationAccessService organizationAccessService;

    private String csrfToken;
    private Cookie csrfCookie;
    private MockHttpSession session;

    @BeforeEach
    void fetchCsrfToken() throws Exception {
        session = null;
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        csrfToken = body.get("token").asText();
        csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertEquals("X-XSRF-TOKEN", body.get("headerName").asText());
        assertEquals(csrfToken, csrfCookie.getValue());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void registerLoginLogoutAndProfile() throws Exception {
        String email = email();

        MvcResult registered = submit("/api/auth/register", Map.of(
                "email", email,
                "password", PASSWORD,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "organizationName", "Analytical Engines"));
        assertEquals(201, registered.getResponse().getStatus());
        JsonNode created = objectMapper.readTree(registered.getResponse().getContentAsString());
        assertEquals(email, created.get("email").asText());
        assertEquals("Analytical Engines", created.get("memberships").get(0).get("organizationName").asText());
        assertEquals("OWNER", created.get("memberships").get(0).get("role").asText());
        assertSecretFree(registered.getResponse().getContentAsString());

        User stored = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, stored.getPasswordHash()));
        assertTrue(stored.getPasswordHash().startsWith("$2"));

        MvcResult unauthenticated = mockMvc.perform(get("/api/auth/me")).andReturn();
        assertEquals(401, unauthenticated.getResponse().getStatus());

        MvcResult loggedIn = submit("/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(200, loggedIn.getResponse().getStatus());
        assertEquals(email, objectMapper.readTree(loggedIn.getResponse().getContentAsString()).get("email").asText());
        assertSecretFree(loggedIn.getResponse().getContentAsString());
        MockHttpSession authenticatedSession = session;

        MvcResult profile = getWithSession("/api/auth/me");
        assertEquals(200, profile.getResponse().getStatus());
        assertEquals(email, objectMapper.readTree(profile.getResponse().getContentAsString()).get("email").asText());

        MvcResult loggedOut = submit("/api/auth/logout", Map.of());
        assertEquals(200, loggedOut.getResponse().getStatus());

        MvcResult afterLogout = mockMvc.perform(get("/api/auth/me").session(authenticatedSession)).andReturn();
        assertEquals(401, afterLogout.getResponse().getStatus());
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        String email = email();
        MvcResult first = submit("/api/auth/register", account(email, "Ada", "Lovelace", null));
        assertEquals(201, first.getResponse().getStatus());

        MvcResult duplicate = submit("/api/auth/register", account(email.toUpperCase(), "Ada", "Lovelace", null));
        assertEquals(409, duplicate.getResponse().getStatus());
        assertEquals("An account with this email already exists",
                objectMapper.readTree(duplicate.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void invalidCredentialsUseTheSameResponse() throws Exception {
        String email = email();
        assertEquals(201, submit("/api/auth/register", account(email, "Ada", "Lovelace", null)).getResponse().getStatus());

        MvcResult wrongPassword = submit("/api/auth/login", Map.of("email", email, "password", "wrong-password-1"));
        MvcResult unknownEmail = submit("/api/auth/login", Map.of("email", email(), "password", PASSWORD));

        assertEquals(401, wrongPassword.getResponse().getStatus());
        assertEquals(401, unknownEmail.getResponse().getStatus());
        assertEquals("Invalid email or password",
                objectMapper.readTree(wrongPassword.getResponse().getContentAsString()).get("message").asText());
        assertEquals("Invalid email or password",
                objectMapper.readTree(unknownEmail.getResponse().getContentAsString()).get("message").asText());
        assertEquals(null, wrongPassword.getRequest().getSession(false));
    }

    @Test
    void missingCsrfTokenIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email(), "password", PASSWORD))))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        MvcResult result = submit("/api/auth/register", Map.of(
                "email", email(),
                "password", "short",
                "firstName", "Ada",
                "lastName", "Lovelace"));
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("Password must be between 8 and 100 characters"));
    }

    @Test
    void organizationAccessFollowsRoleAndTenantBoundaries() throws Exception {
        String ownerEmail = email();
        String adminEmail = email();
        String memberEmail = email();
        String outsiderEmail = email();

        assertEquals(201, submit("/api/auth/register", account(ownerEmail, "Olivia", "Owner", "Northwind")).getResponse().getStatus());
        assertEquals(201, submit("/api/auth/register", account(adminEmail, "Adam", "Admin", null)).getResponse().getStatus());
        assertEquals(201, submit("/api/auth/register", account(memberEmail, "Mia", "Member", null)).getResponse().getStatus());
        assertEquals(201, submit("/api/auth/register", account(outsiderEmail, "Owen", "Outsider", "Other Org")).getResponse().getStatus());

        login(ownerEmail);
        MvcResult created = submit("/api/organizations", Map.of("name", "Northwind Trading"));
        assertEquals(201, created.getResponse().getStatus());
        UUID organizationId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        MvcResult addedAdmin = submit("/api/organizations/" + organizationId + "/members",
                Map.of("email", adminEmail, "role", "ADMIN"));
        assertEquals(201, addedAdmin.getResponse().getStatus());
        MvcResult addedMember = submit("/api/organizations/" + organizationId + "/members",
                Map.of("email", memberEmail, "role", "MEMBER"));
        assertEquals(201, addedMember.getResponse().getStatus());
        UUID memberId = UUID.fromString(objectMapper.readTree(addedMember.getResponse().getContentAsString()).get("userId").asText());

        login(memberEmail);
        assertEquals(200, getWithSession("/api/organizations/" + organizationId).getResponse().getStatus());
        MvcResult memberInvite = submit("/api/organizations/" + organizationId + "/members",
                Map.of("email", outsiderEmail, "role", "MEMBER"));
        assertEquals(403, memberInvite.getResponse().getStatus());

        login(outsiderEmail);
        assertEquals(404, getWithSession("/api/organizations/" + organizationId).getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/api/organizations")).andReturn().getResponse().getStatus());

        login(adminEmail);
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow();
        MvcResult promoteOwner = patch("/api/organizations/" + organizationId + "/members/" + memberId,
                Map.of("role", "OWNER"));
        assertEquals(403, promoteOwner.getResponse().getStatus());
        MvcResult removeOwnerAttempt = delete("/api/organizations/" + organizationId + "/members/" + ownerId(ownerEmail));
        assertEquals(403, removeOwnerAttempt.getResponse().getStatus());

        login(ownerEmail);
        UUID ownerId = ownerId(ownerEmail);
        MvcResult removeLastOwner = delete("/api/organizations/" + organizationId + "/members/" + ownerId);
        assertEquals(400, removeLastOwner.getResponse().getStatus());
        assertEquals("The organization must keep at least one owner",
                objectMapper.readTree(removeLastOwner.getResponse().getContentAsString()).get("message").asText());

        MvcResult promoted = patch("/api/organizations/" + organizationId + "/members/" + memberId,
                Map.of("role", "ADMIN"));
        assertEquals(200, promoted.getResponse().getStatus());
        assertEquals("ADMIN", objectMapper.readTree(promoted.getResponse().getContentAsString()).get("role").asText());

        authenticate(admin);
        assertThrows(ResourceNotFoundException.class, () -> organizationAccessService.requireResourceInOrganization(
                organizationId,
                () -> UUID.randomUUID(),
                Permission.RESOURCE_READ));
        authenticate(userRepository.findByEmailIgnoreCase(memberEmail).orElseThrow());
        assertThrows(ForbiddenException.class, () -> organizationAccessService.requireResourceInOrganization(
                organizationId,
                () -> organizationId,
                Permission.ORG_DELETE));
        authenticate(userRepository.findByEmailIgnoreCase(outsiderEmail).orElseThrow());
        assertThrows(ResourceNotFoundException.class, () -> organizationAccessService.requireResourceInOrganization(
                organizationId,
                () -> organizationId,
                Permission.RESOURCE_READ));
    }

    private UUID ownerId(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private void login(String email) throws Exception {
        MvcResult result = submit("/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(200, result.getResponse().getStatus());
    }

    private void authenticate(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Map<String, String> account(String email, String firstName, String lastName, String organizationName) {
        java.util.HashMap<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        if (organizationName != null) {
            body.put("organizationName", organizationName);
        }
        return body;
    }

    private String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
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
        if (session == null) {
            throw new AssertionError("Expected a session after login");
        }
        MockHttpServletRequestBuilder request = get(path).session(session);
        MvcResult result = mockMvc.perform(request).andReturn();
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

    private void assertSecretFree(String body) {
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("WORKPILOT_SESSION"));
        assertFalse(body.contains("$2a$"));
        assertFalse(body.contains("$2b$"));
    }
}
