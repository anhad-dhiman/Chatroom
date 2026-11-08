package com.anhad.chatroom.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.anhad.chatroom.IntegrationTestBase;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerReturnsTokenAndJoinsGeneralRoom() throws Exception {
        TestUser user = register("alice");

        getAs(user, "/api/users/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.displayName").value(user.username()));

        getAs(user, "/api/conversations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("general")));
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        TestUser user = register("bob");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.username().toUpperCase(), "password", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username is already taken"));
    }

    @Test
    void invalidRegistrationIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "x", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void loginWithCorrectAndWrongPassword() throws Exception {
        TestUser user = register("carol");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.username(), "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.username(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresValidToken() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        TestUser user = register("dave");
        patchAs(user, "/api/users/me", Map.of("displayName", "Dave D.", "avatarColor", "#123abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dave D."))
                .andExpect(jsonPath("$.avatarColor").value("#123abc"));

        patchAs(user, "/api/users/me", Map.of("avatarColor", "red"))
                .andExpect(status().isBadRequest());
    }
}
