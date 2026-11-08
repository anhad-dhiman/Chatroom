package com.anhad.chatroom.message;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Keyset pagination: newest first, strictly older than {@code beforeId}. */
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);

    @Query("""
            select m from Message m
            where m.id in (select max(m2.id) from Message m2
                           where m2.conversationId in :conversationIds group by m2.conversationId)
            """)
    List<Message> findLatestPerConversation(@Param("conversationIds") Collection<Long> conversationIds);

    /** Unread counts for every conversation a user belongs to: [conversationId, count]. */
    @Query("""
            select m.conversationId, count(m) from Message m, com.anhad.chatroom.conversation.ConversationMember cm
            where cm.userId = :userId
              and m.conversationId = cm.conversationId
              and m.id > cm.lastReadMessageId
              and m.senderId <> :userId
              and m.deleted = false
            group by m.conversationId
            """)
    List<Object[]> countUnreadByConversation(@Param("userId") Long userId);
}
