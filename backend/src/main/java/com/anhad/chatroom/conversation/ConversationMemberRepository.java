package com.anhad.chatroom.conversation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationMember> findByConversationId(Long conversationId);

    List<ConversationMember> findByConversationIdIn(Collection<Long> conversationIds);

    @Query("select m.userId from ConversationMember m where m.conversationId = :conversationId")
    List<Long> findUserIds(@Param("conversationId") Long conversationId);

    @Query("""
            select m.conversationId, count(m) from ConversationMember m
            where m.conversationId in :conversationIds
            group by m.conversationId
            """)
    List<Object[]> countByConversation(@Param("conversationIds") Collection<Long> conversationIds);
}
