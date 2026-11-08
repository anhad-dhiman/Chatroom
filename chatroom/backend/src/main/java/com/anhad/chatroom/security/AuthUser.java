package com.anhad.chatroom.security;

import java.security.Principal;

/**
 * The authenticated caller. Implements {@link Principal} so that the username is used
 * as the session name for STOMP user destinations ({@code /user/queue/...}).
 */
public record AuthUser(Long id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
