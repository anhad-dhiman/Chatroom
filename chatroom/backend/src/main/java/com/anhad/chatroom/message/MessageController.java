package com.anhad.chatroom.message;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageDto> history(@AuthenticationPrincipal AuthUser me,
                                    @PathVariable Long conversationId,
                                    @RequestParam(required = false) Long before,
                                    @RequestParam(defaultValue = "50") int limit) {
        return service.history(conversationId, me.id(), before, limit);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@AuthenticationPrincipal AuthUser me, @PathVariable Long conversationId,
                           @Valid @RequestBody ContentRequest request) {
        return service.send(conversationId, me.id(), request.content());
    }

    @PatchMapping("/messages/{id}")
    public MessageDto edit(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                           @Valid @RequestBody ContentRequest request) {
        return service.edit(id, me.id(), request.content());
    }

    @DeleteMapping("/messages/{id}")
    public MessageDto delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.delete(id, me.id());
    }

    @PostMapping("/messages/{id}/reactions")
    public MessageDto react(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                            @Valid @RequestBody ReactionRequest request) {
        return service.toggleReaction(id, me.id(), request.emoji());
    }

    public record ContentRequest(@NotNull @NotBlank String content) {
    }

    public record ReactionRequest(@NotBlank String emoji) {
    }
}
