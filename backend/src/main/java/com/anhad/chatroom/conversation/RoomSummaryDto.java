package com.anhad.chatroom.conversation;

/** A public room as shown in the "browse rooms" list. */
public record RoomSummaryDto(Long id, String name, String description, long memberCount, boolean joined) {
}
