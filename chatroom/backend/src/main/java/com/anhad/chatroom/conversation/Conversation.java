package com.anhad.chatroom.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationType type;

    @Column(length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "direct_key", length = 64)
    private String directKey;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
    }

    public static Conversation room(String name, String description, Long createdBy) {
        Conversation c = new Conversation();
        c.type = ConversationType.ROOM;
        c.name = name;
        c.description = description;
        c.createdBy = createdBy;
        c.createdAt = Instant.now();
        return c;
    }

    public static Conversation direct(Long userA, Long userB) {
        Conversation c = new Conversation();
        c.type = ConversationType.DIRECT;
        c.directKey = directKey(userA, userB);
        c.createdBy = userA;
        c.createdAt = Instant.now();
        return c;
    }

    public static String directKey(Long userA, Long userB) {
        return Math.min(userA, userB) + ":" + Math.max(userA, userB);
    }

    public boolean isRoom() {
        return type == ConversationType.ROOM;
    }

    public Long getId() { return id; }
    public ConversationType getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDirectKey() { return directKey; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }

    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
