package com.anhad.chatroom.message;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_reactions")
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageReaction() {
    }

    public MessageReaction(Long messageId, Long userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMessageId() { return messageId; }
    public Long getUserId() { return userId; }
    public String getEmoji() { return emoji; }
    public Instant getCreatedAt() { return createdAt; }
}
