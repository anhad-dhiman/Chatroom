package com.anhad.chatroom.conversation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anhad.chatroom.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.listForUser(me.id());
    }

    @GetMapping("/conversations/{id}")
    public ConversationDto get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.getForUser(id, me.id());
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                         @Valid @RequestBody MarkReadRequest request) {
        service.markRead(id, me.id(), request.messageId());
    }

    @GetMapping("/rooms")
    public List<RoomSummaryDto> browse(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam(defaultValue = "") String query) {
        return service.browseRooms(query, me.id());
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto createRoom(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRoomRequest request) {
        return service.createRoom(request.name(), request.description(), me.id());
    }

    @PostMapping("/rooms/{id}/join")
    public ConversationDto join(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.joinRoom(id, me.id());
    }

    @PostMapping("/rooms/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.leaveRoom(id, me.id());
    }

    @PostMapping("/direct/{userId}")
    public ConversationDto openDirect(@AuthenticationPrincipal AuthUser me, @PathVariable Long userId) {
        return service.openDirect(me.id(), userId);
    }

    public record CreateRoomRequest(@NotBlank @Size(max = 33) String name, @Size(max = 255) String description) {
    }

    public record MarkReadRequest(@NotNull Long messageId) {
    }
}
