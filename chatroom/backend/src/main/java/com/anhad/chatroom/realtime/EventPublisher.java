package com.anhad.chatroom.realtime;

import java.util.Collection;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.anhad.chatroom.conversation.ConversationMemberRepository;
import com.anhad.chatroom.user.UserDto;
import com.anhad.chatroom.user.UserService;

/**
 * Fans events out to the private queue ({@code /user/queue/events}) of every member of a conversation.
 * Delivery is deferred until the surrounding transaction commits, so clients never see data that
 * was rolled back.
 */
@Component
public class EventPublisher {

    public static final String USER_QUEUE = "/queue/events";
    public static final String PRESENCE_TOPIC = "/topic/presence";

    private final SimpMessagingTemplate messaging;
    private final ConversationMemberRepository members;
    private final UserService users;

    public EventPublisher(SimpMessagingTemplate messaging, ConversationMemberRepository members, UserService users) {
        this.messaging = messaging;
        this.members = members;
        this.users = users;
    }

    public void toConversation(Long conversationId, String type, Object payload) {
        List<Long> memberIds = members.findUserIds(conversationId);
        toUsers(memberIds, new ChatEvent(type, conversationId, payload));
    }

    public void toUsers(Collection<Long> userIds, ChatEvent event) {
        List<String> usernames = users.byIds(userIds).values().stream().map(UserDto::username).toList();
        afterCommit(() -> usernames.forEach(u -> messaging.convertAndSendToUser(u, USER_QUEUE, event)));
    }

    public void broadcastPresence(Long userId, boolean online) {
        messaging.convertAndSend(PRESENCE_TOPIC, new ChatEvent(ChatEvent.PRESENCE, null, new Presence(userId, online)));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public record Presence(Long userId, boolean online) {
    }
}
