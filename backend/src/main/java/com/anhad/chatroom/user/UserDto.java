package com.anhad.chatroom.user;

public record UserDto(Long id, String username, String displayName, String avatarColor) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarColor());
    }
}
