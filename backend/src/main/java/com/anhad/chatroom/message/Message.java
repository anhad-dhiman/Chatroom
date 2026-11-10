package com.anhad.chatroom.message;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(nullable = false)
    private boolean deleted;

    protected Message() {
    }

    public Message(Long conversationId, Long senderId, String content) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public void edit(String newContent) {
        this.content = newContent;
        this.editedAt = Instant.now();
    }

    /** Soft delete: the row stays so that threads and unread markers remain consistent. */
    public void softDelete() {
        this.deleted = true;
        this.content = "";
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getSenderId() { return senderId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public boolean isDeleted() { return deleted; }
}
