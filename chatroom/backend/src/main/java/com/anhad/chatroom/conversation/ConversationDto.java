package com.anhad.chatroom.conversation;

import java.time.Instant;
import java.util.List;

import com.anhad.chatroom.message.MessageDto;
import com.anhad.chatroom.user.UserDto;

/**
 * A conversation from the point of view of one user.
 *
 * @param members     for DIRECT conversations, both participants; empty for rooms (use memberCount)
 * @param unreadCount messages from other people the current user has not read yet
 */
public record ConversationDto(
        Long id,
        ConversationType type,
        String name,
        String description,
        Long createdBy,
        Instant createdAt,
        Instant lastMessageAt,
        long memberCount,
        List<UserDto> members,
        long unreadCount,
        Long lastReadMessageId,
        MessageDto lastMessage) {
}
