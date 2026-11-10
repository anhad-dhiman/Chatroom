package com.anhad.chatroom.message;

import java.time.Instant;
import java.util.List;

import com.anhad.chatroom.user.UserDto;

/** A chat message. {@code content} is null when the message has been deleted. */
public record MessageDto(
        Long id,
        Long conversationId,
        UserDto sender,
        String content,
        Instant createdAt,
        Instant editedAt,
        boolean deleted,
        List<ReactionDto> reactions) {

    /** One emoji and everyone who reacted with it. */
    public record ReactionDto(String emoji, List<Long> userIds) {
    }
}
