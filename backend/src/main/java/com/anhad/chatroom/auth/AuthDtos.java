package com.anhad.chatroom.auth;

import com.anhad.chatroom.user.UserDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "must be 3-32 characters: letters, numbers or underscores")
            String username,
            @Size(max = 64) String displayName,
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AuthResponse(String token, UserDto user) {
    }
}
