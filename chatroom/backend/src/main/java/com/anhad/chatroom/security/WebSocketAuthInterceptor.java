package com.anhad.chatroom.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the JWT from its {@code Authorization} header, and restricts
 * which destinations a client may subscribe to. Browsers can't set headers on the WebSocket handshake,
 * which is why the token travels in the CONNECT frame instead.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            String token = JwtService.stripBearer(accessor.getFirstNativeHeader("Authorization"));
            AuthUser user = jwtService.parse(token)
                    .orElseThrow(() -> new MessageDeliveryException("Invalid or missing token"));
            accessor.setUser(JwtAuthenticationFilter.authentication(user));
            return message;
        }

        if (accessor.getUser() == null && (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND)) {
            throw new MessageDeliveryException("Not authenticated");
        }

        if (command == StompCommand.SUBSCRIBE && !isAllowedSubscription(accessor.getDestination())) {
            throw new MessageDeliveryException("Subscription to " + accessor.getDestination() + " is not allowed");
        }
        return message;
    }

    /**
     * Private data only ever flows through per-user queues, so the only shared topic is presence.
     * This stops a client from subscribing to someone else's queue directly.
     */
    static boolean isAllowedSubscription(String destination) {
        return destination != null
                && (destination.startsWith("/user/queue/") || destination.equals("/topic/presence"));
    }
}
