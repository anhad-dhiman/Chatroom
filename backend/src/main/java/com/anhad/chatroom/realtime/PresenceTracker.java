package com.anhad.chatroom.realtime;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.anhad.chatroom.security.AuthUser;

/**
 * Tracks who is online by counting open WebSocket sessions per user (a user can have several tabs open).
 * In-memory, so it is per-instance; a multi-instance deployment would back this with Redis.
 */
@Component
public class PresenceTracker {

    private final ConcurrentHashMap<Long, Integer> sessionsPerUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> userBySession = new ConcurrentHashMap<>();
    private final EventPublisher events;

    public PresenceTracker(EventPublisher events) {
        this.events = events;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        AuthUser user = authUser(event.getUser());
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        if (user == null || sessionId == null) {
            return;
        }
        userBySession.put(sessionId, user.id());
        int count = sessionsPerUser.merge(user.id(), 1, Integer::sum);
        if (count == 1) {
            events.broadcastPresence(user.id(), true);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Long userId = userBySession.remove(event.getSessionId());
        if (userId == null) {
            return;
        }
        Integer remaining = sessionsPerUser.computeIfPresent(userId, (id, n) -> n <= 1 ? null : n - 1);
        if (remaining == null) {
            events.broadcastPresence(userId, false);
        }
    }

    public Set<Long> onlineUserIds() {
        return Set.copyOf(sessionsPerUser.keySet());
    }

    public boolean isOnline(Long userId) {
        return sessionsPerUser.containsKey(userId);
    }

    private static AuthUser authUser(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return principal instanceof AuthUser user ? user : null;
    }
}
