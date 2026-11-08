package com.anhad.chatroom.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anhad.chatroom.common.ApiException;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public List<UserDto> search(String query, Long excludeId) {
        String q = query == null ? "" : query.trim();
        return users.search(q, excludeId, PageRequest.of(0, 20)).stream().map(UserDto::from).toList();
    }

    /** Loads users by id in a single query, for building DTOs without N+1 lookups. */
    @Transactional(readOnly = true)
    public Map<Long, UserDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .map(UserDto::from)
                .collect(Collectors.toMap(UserDto::id, Function.identity()));
    }

    @Transactional
    public UserDto updateProfile(Long userId, String displayName, String avatarColor) {
        User user = get(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (avatarColor != null && !avatarColor.isBlank()) {
            user.setAvatarColor(avatarColor);
        }
        return UserDto.from(user);
    }
}
