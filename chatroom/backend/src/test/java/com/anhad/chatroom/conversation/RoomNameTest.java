package com.anhad.chatroom.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.anhad.chatroom.common.ApiException;

class RoomNameTest {

    @ParameterizedTest
    @CsvSource({
            "general, general",
            "#Random, random",
            "  Game Night  , game-night",
            "team_42, team_42"
    })
    void normalizes(String input, String expected) {
        assertThat(ConversationService.normalizeRoomName(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "-starts-with-dash", "has.dot", "this-name-is-way-too-long-for-a-room-name"})
    void rejects(String input) {
        assertThatThrownBy(() -> ConversationService.normalizeRoomName(input)).isInstanceOf(ApiException.class);
    }
}
