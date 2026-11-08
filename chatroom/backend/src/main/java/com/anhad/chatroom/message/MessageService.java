package com.anhad.chatroom.message;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anhad.chatroom.common.ApiException;
import com.anhad.chatroom.conversation.Conversation;
import com.anhad.chatroom.conversation.ConversationService;
import com.anhad.chatroom.realtime.ChatEvent;
import com.anhad.chatroom.realtime.EventPublisher;

@Service
public class MessageService {

    public static final int MAX_LENGTH = 2000;
    public static final int MAX_PAGE_SIZE = 100;
    /** Kept small and fixed so the UI can render a consistent reaction picker. */
    public static final Set<String> ALLOWED_REACTIONS = Set.of("👍", "❤️", "😂", "🎉", "😮", "😢", "🔥", "👀");

    private final MessageRepository messages;
    private final MessageReactionRepository reactions;
    private final MessageMapper mapper;
    private final ConversationService conversations;
    private final EventPublisher events;

    public MessageService(MessageRepository messages, MessageReactionRepository reactions, MessageMapper mapper,
                          ConversationService conversations, EventPublisher events) {
        this.messages = messages;
        this.reactions = reactions;
        this.mapper = mapper;
        this.conversations = conversations;
        this.events = events;
    }

    /** Returns up to {@code limit} messages older than {@code beforeId}, oldest first. */
    @Transactional(readOnly = true)
    public List<MessageDto> history(Long conversationId, Long userId, Long beforeId, int limit) {
        conversations.requireMember(conversationId, userId);
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        long before = beforeId == null ? Long.MAX_VALUE : beforeId;
        List<Message> page = messages.findByConversationIdAndIdLessThanOrderByIdDesc(
                conversationId, before, PageRequest.of(0, size));
        return mapper.toDtos(page.reversed());
    }

    @Transactional
    public MessageDto send(Long conversationId, Long userId, String rawContent) {
        Conversation conversation = conversations.requireMember(conversationId, userId);
        Message message = messages.save(new Message(conversationId, userId, clean(rawContent)));
        conversation.setLastMessageAt(message.getCreatedAt());
        // Sending implies you've read everything up to your own message.
        conversations.markRead(conversationId, userId, message.getId());

        MessageDto dto = mapper.toDto(message);
        events.toConversation(conversationId, ChatEvent.MESSAGE_CREATED, dto);
        return dto;
    }

    @Transactional
    public MessageDto edit(Long messageId, Long userId, String rawContent) {
        Message message = requireOwnMessage(messageId, userId);
        message.edit(clean(rawContent));
        return publishUpdate(message);
    }

    @Transactional
    public MessageDto delete(Long messageId, Long userId) {
        Message message = requireOwnMessage(messageId, userId);
        message.softDelete();
        return publishUpdate(message);
    }

    /** Adds the reaction if the user hasn't used that emoji on the message yet, otherwise removes it. */
    @Transactional
    public MessageDto toggleReaction(Long messageId, Long userId, String emoji) {
        if (emoji == null || !ALLOWED_REACTIONS.contains(emoji)) {
            throw ApiException.badRequest("Unsupported reaction");
        }
        Message message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message"));
        conversations.requireMember(message.getConversationId(), userId);
        if (message.isDeleted()) {
            throw ApiException.badRequest("You can't react to a deleted message");
        }
        reactions.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji).ifPresentOrElse(
                reactions::delete,
                () -> reactions.save(new MessageReaction(messageId, userId, emoji)));
        reactions.flush();
        return publishUpdate(message);
    }

    private MessageDto publishUpdate(Message message) {
        MessageDto dto = mapper.toDto(message);
        events.toConversation(message.getConversationId(), ChatEvent.MESSAGE_UPDATED, dto);
        return dto;
    }

    private Message requireOwnMessage(Long messageId, Long userId) {
        Message message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message"));
        if (!message.getSenderId().equals(userId)) {
            throw ApiException.forbidden("You can only change your own messages");
        }
        if (message.isDeleted()) {
            throw ApiException.badRequest("This message was deleted");
        }
        return message;
    }

    private static String clean(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            throw ApiException.badRequest("Message can't be empty");
        }
        if (content.length() > MAX_LENGTH) {
            throw ApiException.badRequest("Message is too long (max " + MAX_LENGTH + " characters)");
        }
        return content;
    }
}
