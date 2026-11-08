package com.anhad.chatroom.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.anhad.chatroom.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

class ConversationIntegrationTest extends IntegrationTestBase {

    @Test
    void createBrowseJoinAndLeaveRoom() throws Exception {
        TestUser owner = register("owner");
        TestUser guest = register("guest");
        String name = uniqueRoomName();

        long roomId = read(postAs(owner, "/api/rooms", Map.of("name", "#" + name.toUpperCase(), "description", "Test room"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.memberCount").value(1)))
                .get("id").asLong();

        getAs(guest, "/api/rooms?query=" + name)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].joined").value(false));

        postAs(guest, "/api/rooms/" + roomId + "/join", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        // Joining twice is a no-op.
        postAs(guest, "/api/rooms/" + roomId + "/join", null).andExpect(jsonPath("$.memberCount").value(2));

        postAs(guest, "/api/rooms/" + roomId + "/leave", null).andExpect(status().isNoContent());
        getAs(guest, "/api/conversations/" + roomId).andExpect(status().isForbidden());
    }

    @Test
    void duplicateAndInvalidRoomNamesAreRejected() throws Exception {
        TestUser owner = register("owner");
        String name = uniqueRoomName();
        createRoom(owner, name);

        postAs(owner, "/api/rooms", Map.of("name", name.toUpperCase())).andExpect(status().isConflict());
        postAs(owner, "/api/rooms", Map.of("name", "!!")).andExpect(status().isBadRequest());
    }

    @Test
    void cannotLeaveGeneral() throws Exception {
        TestUser user = register("stayer");
        JsonNode conversations = read(getAs(user, "/api/conversations"));
        long generalId = conversations.get(0).get("id").asLong();
        postAs(user, "/api/rooms/" + generalId + "/leave", null).andExpect(status().isBadRequest());
    }

    @Test
    void directMessagesAreIdempotentAndPrivate() throws Exception {
        TestUser alice = register("alice");
        TestUser bob = register("bob");
        TestUser eve = register("eve");

        JsonNode first = read(postAs(alice, "/api/direct/" + bob.id(), null).andExpect(status().isOk()));
        JsonNode second = read(postAs(bob, "/api/direct/" + alice.id(), null).andExpect(status().isOk()));

        assertThat(first.get("id").asLong()).isEqualTo(second.get("id").asLong());
        assertThat(first.get("type").asText()).isEqualTo("DIRECT");
        assertThat(first.get("members")).hasSize(2);

        long dmId = first.get("id").asLong();
        getAs(eve, "/api/conversations/" + dmId + "/messages").andExpect(status().isForbidden());
        postAs(eve, "/api/conversations/" + dmId + "/messages", Map.of("content", "let me in"))
                .andExpect(status().isForbidden());

        postAs(alice, "/api/direct/" + alice.id(), null).andExpect(status().isBadRequest());
        postAs(alice, "/api/direct/999999", null).andExpect(status().isNotFound());
    }

    @Test
    void unreadCountsTrackReadMarker() throws Exception {
        TestUser alice = register("alice");
        TestUser bob = register("bob");
        long dmId = read(postAs(alice, "/api/direct/" + bob.id(), null)).get("id").asLong();

        sendMessage(alice, dmId, "one");
        sendMessage(alice, dmId, "two");
        long last = sendMessage(alice, dmId, "three");

        assertThat(unreadFor(bob, dmId)).isEqualTo(3);
        assertThat(unreadFor(alice, dmId)).isZero(); // your own messages never count

        postAs(bob, "/api/conversations/" + dmId + "/read", Map.of("messageId", last))
                .andExpect(status().isNoContent());
        assertThat(unreadFor(bob, dmId)).isZero();

        // The marker never moves backwards.
        postAs(bob, "/api/conversations/" + dmId + "/read", Map.of("messageId", 1)).andExpect(status().isNoContent());
        assertThat(unreadFor(bob, dmId)).isZero();
    }

    @Test
    void conversationListShowsLastMessageNewestFirst() throws Exception {
        TestUser alice = register("alice");
        long older = createRoom(alice, uniqueRoomName());
        long newer = createRoom(alice, uniqueRoomName());
        sendMessage(alice, newer, "first");
        sendMessage(alice, older, "latest");

        JsonNode list = read(getAs(alice, "/api/conversations"));
        assertThat(list.get(0).get("id").asLong()).isEqualTo(older);
        assertThat(list.get(0).at("/lastMessage/content").asText()).isEqualTo("latest");
    }

    private long unreadFor(TestUser user, long conversationId) throws Exception {
        return read(getAs(user, "/api/conversations/" + conversationId)).get("unreadCount").asLong();
    }
}
