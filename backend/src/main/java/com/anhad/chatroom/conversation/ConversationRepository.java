package com.anhad.chatroom.conversation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    Optional<Conversation> findByTypeAndNameIgnoreCase(ConversationType type, String name);

    boolean existsByTypeAndNameIgnoreCase(ConversationType type, String name);

    @Query("""
            select c from Conversation c
            where c.type = com.anhad.chatroom.conversation.ConversationType.ROOM
              and lower(c.name) like lower(concat('%', :query, '%'))
            order by c.name
            """)
    List<Conversation> searchRooms(@Param("query") String query);

    @Query("""
            select c from Conversation c
            where c.id in (select m.conversationId from ConversationMember m where m.userId = :userId)
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}
