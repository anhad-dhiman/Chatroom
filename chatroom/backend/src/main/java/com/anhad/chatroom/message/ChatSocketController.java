package com.anhad.chatroom.message;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.anhad.chatroom.common.ApiException;
import com.anhad.chatroom.security.AuthUser;

/**
 * Lets clients send messages over the open WebSocket instead of a separate HTTP request
 * (lower latency). Destination: {@code /app/conversations/{id}/send}. Errors go to {@code /user/queue/errors}.
 */
@Controller
public class ChatSocketController {

    private final MessageService service;

    public ChatSocketController(MessageService service) {
        this.service = service;
    }

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(@DestinationVariable Long conversationId, @Payload SendPayload payload, Principal principal) {
        service.send(conversationId, user(principal).id(), payload.content());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public String handle(ApiException e) {
        return e.getMessage();
    }

    private static AuthUser user(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw ApiException.forbidden("Not authenticated");
    }

    public record SendPayload(String content) {
    }
}
