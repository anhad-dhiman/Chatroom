package com.anhad.chatroom.user;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anhad.chatroom.realtime.PresenceTracker;
import com.anhad.chatroom.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PresenceTracker presence;

    public UserController(UserService userService, PresenceTracker presence) {
        this.userService = userService;
        this.presence = presence;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return UserDto.from(userService.get(me.id()));
    }

    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(me.id(), request.displayName(), request.avatarColor());
    }

    @GetMapping
    public List<UserDto> search(@AuthenticationPrincipal AuthUser me,
                                @RequestParam(defaultValue = "") String query) {
        return userService.search(query, me.id());
    }

    @GetMapping("/online")
    public Set<Long> online() {
        return presence.onlineUserIds();
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 64) String displayName,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #4f46e5") String avatarColor) {
    }
}
