package com.anhad.chatroom.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.anhad.chatroom.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class MessageIntegrationTest extends IntegrationTestBase {

    @Test
    void sendAndPaginateHistory() throws Exception {
        TestUser alice = register("alice");
        long room = createRoom(alice, uniqueRoomName());
        for (int i = 1; i <= 5; i++) {
            sendMessage(alice, room, "message " + i);
        }

        JsonNode latest = read(getAs(alice, "/api/conversations/" + room + "/messages?limit=3")
                .andExpect(jsonPath("$", hasSize(3))));
        // Oldest first within a page.
        assertThat(latest.get(0).get("content").asText()).isEqualTo("message 3");
        assertThat(latest.get(2).get("content").asText()).isEqualTo("message 5");
        assertThat(latest.get(0).at("/sender/username").asText()).isEqualTo(alice.username());

        long oldestLoaded = latest.get(0).get("id").asLong();
        getAs(alice, "/api/conversations/" + room + "/messages?limit=3&before=" + oldestLoaded)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("message 1"));
    }

    @Test
    void rejectsEmptyAndOversizedMessages() throws Exception {
        TestUser alice = register("alice");
        long room = createRoom(alice, uniqueRoomName());
        postAs(alice, "/api/conversations/" + room + "/messages", Map.of("content", "   "))
                .andExpect(status().isBadRequest());
        postAs(alice, "/api/conversations/" + room + "/messages", Map.of("content", "x".repeat(2001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAuthorCanEditOrDelete() throws Exception {
        TestUser alice = register("alice");
        TestUser bob = register("bob");
        long room = createRoom(alice, uniqueRoomName());
        postAs(bob, "/api/rooms/" + room + "/join", null);
        long messageId = sendMessage(alice, room, "hello");

        patchAs(bob, "/api/messages/" + messageId, Map.of("content", "hacked")).andExpect(status().isForbidden());
        deleteAs(bob, "/api/messages/" + messageId).andExpect(status().isForbidden());

        patchAs(alice, "/api/messages/" + messageId, Map.of("content", "hello, edited"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello, edited"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());

        deleteAs(alice, "/api/messages/" + messageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.content").doesNotExist());

        patchAs(alice, "/api/messages/" + messageId, Map.of("content", "zombie")).andExpect(status().isBadRequest());

        // Deleted messages stay in history as tombstones.
        getAs(bob, "/api/conversations/" + room + "/messages")
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void reactionsToggle() throws Exception {
        TestUser alice = register("alice");
        TestUser bob = register("bob");
        long room = createRoom(alice, uniqueRoomName());
        postAs(bob, "/api/rooms/" + room + "/join", null);
        long messageId = sendMessage(alice, room, "react to me");
        String url = "/api/messages/" + messageId + "/reactions";

        postAs(alice, url, Map.of("emoji", "👍")).andExpect(jsonPath("$.reactions[0].userIds", hasSize(1)));
        postAs(bob, url, Map.of("emoji", "👍"))
                .andExpect(jsonPath("$.reactions", hasSize(1)))
                .andExpect(jsonPath("$.reactions[0].emoji").value("👍"))
                .andExpect(jsonPath("$.reactions[0].userIds", hasSize(2)));
        postAs(bob, url, Map.of("emoji", "🎉")).andExpect(jsonPath("$.reactions", hasSize(2)));

        // Toggling again removes it.
        postAs(alice, url, Map.of("emoji", "👍")).andExpect(jsonPath("$.reactions[0].userIds", hasSize(1)));

        postAs(alice, url, Map.of("emoji", "not-an-emoji")).andExpect(status().isBadRequest());
    }

    @Test
    void nonMembersCannotReact() throws Exception {
        TestUser alice = register("alice");
        TestUser outsider = register("outsider");
        long room = createRoom(alice, uniqueRoomName());
        long messageId = sendMessage(alice, room, "members only");
        postAs(outsider, "/api/messages/" + messageId + "/reactions", Map.of("emoji", "👍"))
                .andExpect(status().isForbidden());
    }
}
