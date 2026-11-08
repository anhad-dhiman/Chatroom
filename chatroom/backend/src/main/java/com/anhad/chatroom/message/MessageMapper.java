package com.anhad.chatroom.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.anhad.chatroom.message.MessageDto.ReactionDto;
import com.anhad.chatroom.user.UserDto;
import com.anhad.chatroom.user.UserService;

/** Converts messages to DTOs, batch-loading senders and reactions to avoid N+1 queries. */
@Component
public class MessageMapper {

    private final UserService userService;
    private final MessageReactionRepository reactions;

    public MessageMapper(UserService userService, MessageReactionRepository reactions) {
        this.userService = userService;
        this.reactions = reactions;
    }

    public MessageDto toDto(Message message) {
        return toDtos(List.of(message)).getFirst();
    }

    public List<MessageDto> toDtos(Collection<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Set<Long> senderIds = new HashSet<>();
        List<Long> messageIds = new ArrayList<>();
        for (Message m : messages) {
            senderIds.add(m.getSenderId());
            messageIds.add(m.getId());
        }
        Map<Long, UserDto> senders = userService.byIds(senderIds);

        // messageId -> emoji -> userIds, preserving first-reacted order
        Map<Long, Map<String, List<Long>>> grouped = new LinkedHashMap<>();
        for (MessageReaction r : reactions.findByMessageIdInOrderByIdAsc(messageIds)) {
            grouped.computeIfAbsent(r.getMessageId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(r.getEmoji(), k -> new ArrayList<>())
                    .add(r.getUserId());
        }

        return messages.stream().map(m -> {
            List<ReactionDto> reactionDtos = m.isDeleted() ? List.of()
                    : grouped.getOrDefault(m.getId(), Map.of()).entrySet().stream()
                            .map(e -> new ReactionDto(e.getKey(), List.copyOf(e.getValue())))
                            .toList();
            return new MessageDto(
                    m.getId(),
                    m.getConversationId(),
                    senders.get(m.getSenderId()),
                    m.isDeleted() ? null : m.getContent(),
                    m.getCreatedAt(),
                    m.getEditedAt(),
                    m.isDeleted(),
                    reactionDtos);
        }).toList();
    }
}
