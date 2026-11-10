package com.anhad.chatroom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** A registered user with a valid token. */
    protected record TestUser(long id, String username, String token) {
    }

    protected TestUser register(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode body = read(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "password123")))));
        return new TestUser(body.at("/user/id").asLong(), username, body.get("token").asText());
    }

    protected ResultActions getAs(TestUser user, String url) throws Exception {
        return mvc.perform(auth(get(url), user));
    }

    protected ResultActions postAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(withBody(auth(post(url), user), body));
    }

    protected ResultActions patchAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(withBody(auth(patch(url), user), body));
    }

    protected ResultActions deleteAs(TestUser user, String url) throws Exception {
        return mvc.perform(auth(delete(url), user));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long createRoom(TestUser owner, String name) throws Exception {
        return read(postAs(owner, "/api/rooms", Map.of("name", name))).get("id").asLong();
    }

    protected long sendMessage(TestUser sender, long conversationId, String content) throws Exception {
        return read(postAs(sender, "/api/conversations/" + conversationId + "/messages", Map.of("content", content)))
                .get("id").asLong();
    }

    protected static String uniqueRoomName() {
        return "room-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, TestUser user) {
        return user == null ? builder : builder.header("Authorization", "Bearer " + user.token());
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        if (body == null) {
            return builder;
        }
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
}
