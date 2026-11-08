package com.anhad.chatroom.auth;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anhad.chatroom.auth.AuthDtos.AuthResponse;
import com.anhad.chatroom.auth.AuthDtos.LoginRequest;
import com.anhad.chatroom.auth.AuthDtos.RegisterRequest;
import com.anhad.chatroom.common.ApiException;
import com.anhad.chatroom.conversation.ConversationService;
import com.anhad.chatroom.security.JwtService;
import com.anhad.chatroom.user.User;
import com.anhad.chatroom.user.UserDto;
import com.anhad.chatroom.user.UserRepository;

@Service
public class AuthService {

    static final List<String> AVATAR_COLORS = List.of(
            "#4f46e5", "#0891b2", "#059669", "#d97706", "#dc2626", "#db2777", "#7c3aed", "#2563eb");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ConversationService conversations;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       ConversationService conversations) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.conversations = conversations;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim();
        String color = AVATAR_COLORS.get(Math.floorMod(username.toLowerCase().hashCode(), AVATAR_COLORS.size()));

        User user = users.save(new User(username, displayName, passwordEncoder.encode(request.password()), color));
        conversations.joinDefaultRoom(user.getId());
        return respond(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsernameIgnoreCase(request.username().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return respond(user);
    }

    private AuthResponse respond(User user) {
        return new AuthResponse(jwtService.issue(user.getId(), user.getUsername()), UserDto.from(user));
    }
}
