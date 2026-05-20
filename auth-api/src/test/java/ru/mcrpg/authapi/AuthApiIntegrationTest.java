package ru.mcrpg.authapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerRefreshAndProfileFlowWorks() throws Exception {
        JsonNode registration = parse(mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "Arcanist42",
                      "email": "arcanist42@example.com",
                      "password": "Supersafe123",
                      "deviceName": "Desktop"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account.username").value("Arcanist42"))
            .andExpect(jsonPath("$.account.avatar").isNotEmpty())
            .andExpect(jsonPath("$.account.avatarUrl").isNotEmpty())
            .andReturn());

        String accessToken = registration.path("accessToken").asText();
        String refreshToken = registration.path("refreshToken").asText();
        String avatar = registration.path("account").path("avatar").asText();

        mockMvc.perform(get("/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("arcanist42@example.com"))
            .andExpect(jsonPath("$.avatar").value(avatar));

        mockMvc.perform(get("/avatars/" + avatar))
            .andExpect(status().isOk())
            .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));

        JsonNode refreshed = parse(mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andReturn());

        String refreshedAccessToken = refreshed.path("accessToken").asText();
        String refreshedRefreshToken = refreshed.path("refreshToken").asText();
        assertFalse(refreshedAccessToken.isEmpty());
        assertFalse(refreshedRefreshToken.isEmpty());
        assertFalse(refreshToken.equals(refreshedRefreshToken));

        mockMvc.perform(patch("/me")
                .header("Authorization", "Bearer " + refreshedAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "updated@example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("updated@example.com"));
    }

    @Test
    void gameTicketCanBeVerifiedOnlyOnce() throws Exception {
        JsonNode registration = parse(mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "TicketUser",
                      "email": "ticket@example.com",
                      "password": "Supersafe123"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn());

        String accessToken = registration.path("accessToken").asText();
        String accountId = registration.path("account").path("id").asText();
        String expectedPlayerUuid = offlinePlayerUuid("TicketUser");

        JsonNode ticket = parse(mockMvc.perform(post("/game/tickets")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serverId": "obsidiangate-main"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.serverId").value("obsidiangate-main"))
            .andExpect(jsonPath("$.uuid").value(expectedPlayerUuid))
            .andReturn());

        String rawTicket = ticket.path("ticket").asText();
        assertFalse(accountId.equals(expectedPlayerUuid));

        mockMvc.perform(post("/game/tickets/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"" + rawTicket + "\",\"serverId\":\"obsidiangate-main\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.uuid").value(expectedPlayerUuid));

        MvcResult secondVerification = mockMvc.perform(post("/game/tickets/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"" + rawTicket + "\",\"serverId\":\"obsidiangate-main\"}"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode second = parse(secondVerification);
        assertFalse(second.path("valid").asBoolean());
        assertEquals("used", second.path("reason").asText());
    }

    @Test
    void loginByEmailWorks() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "MageLogin",
                      "email": "magelogin@example.com",
                      "password": "Supersafe123"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "login": "magelogin@example.com",
                      "password": "Supersafe123",
                      "deviceName": "Laptop"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.username").value("MageLogin"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void usernameUniquenessIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "CaseKnight",
                      "email": "caseknight@example.com",
                      "password": "Supersafe123"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "caseknight",
                      "email": "caseknight2@example.com",
                      "password": "Supersafe123"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("username_taken"));
    }

    @Test
    void missingAuthorizationHeaderReturnsUnauthorizedApiError() throws Exception {
        mockMvc.perform(get("/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("missing_token"));
    }

    @Test
    void malformedJsonReturnsBadRequestApiError() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_json"));
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String offlinePlayerUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
