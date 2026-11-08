package com.anhad.chatroom.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation_members")
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** High-water mark used to compute unread counts. */
    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId = 0L;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ConversationMember() {
    }

    public ConversationMember(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getUserId() { return userId; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public Instant getJoinedAt() { return joinedAt; }

    /** Moves the read marker forward; never backwards. */
    public void markReadUpTo(Long messageId) {
        if (messageId != null && messageId > lastReadMessageId) {
            lastReadMessageId = messageId;
        }
    }
}
