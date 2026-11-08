package com.anhad.chatroom.realtime;

/**
 * Envelope for everything pushed to clients over the WebSocket.
 *
 * @param type one of the constants below
 */
public record ChatEvent(String type, Long conversationId, Object payload) {

    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_UPDATED = "message.updated";
    public static final String MEMBER_JOINED = "member.joined";
    public static final String MEMBER_LEFT = "member.left";
    public static final String PRESENCE = "presence";
}
