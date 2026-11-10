package com.anhad.chatroom.conversation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anhad.chatroom.common.ApiException;
import com.anhad.chatroom.message.Message;
import com.anhad.chatroom.message.MessageDto;
import com.anhad.chatroom.message.MessageMapper;
import com.anhad.chatroom.message.MessageRepository;
import com.anhad.chatroom.realtime.ChatEvent;
import com.anhad.chatroom.realtime.EventPublisher;
import com.anhad.chatroom.user.UserDto;
import com.anhad.chatroom.user.UserRepository;
import com.anhad.chatroom.user.UserService;

@Service
public class ConversationService {

    public static final String DEFAULT_ROOM = "general";

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageMapper messageMapper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EventPublisher events;

    public ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
                               MessageRepository messages, MessageMapper messageMapper, UserService userService,
                               UserRepository userRepository, EventPublisher events) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.messageMapper = messageMapper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.events = events;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<ConversationDto> listForUser(Long userId) {
        return toDtos(conversations.findAllForUser(userId), userId);
    }

    @Transactional(readOnly = true)
    public ConversationDto getForUser(Long conversationId, Long userId) {
        Conversation conversation = requireMember(conversationId, userId);
        return toDtos(List.of(conversation), userId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<RoomSummaryDto> browseRooms(String query, Long userId) {
        List<Conversation> rooms = conversations.searchRooms(query == null ? "" : query.trim());
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rooms.stream().map(Conversation::getId).toList();
        Map<Long, Long> counts = memberCounts(ids);
        Set<Long> joined = conversations.findAllForUser(userId).stream()
                .map(Conversation::getId).collect(Collectors.toSet());
        return rooms.stream()
                .map(r -> new RoomSummaryDto(r.getId(), r.getName(), r.getDescription(),
                        counts.getOrDefault(r.getId(), 0L), joined.contains(r.getId())))
                .toList();
    }

    /** Throws 404 if the conversation doesn't exist and 403 if the user isn't a member. */
    @Transactional(readOnly = true)
    public Conversation requireMember(Long conversationId, Long userId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw ApiException.forbidden("You are not a member of this conversation");
        }
        return conversation;
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public ConversationDto createRoom(String rawName, String description, Long userId) {
        String name = normalizeRoomName(rawName);
        if (conversations.existsByTypeAndNameIgnoreCase(ConversationType.ROOM, name)) {
            throw ApiException.conflict("A room named #" + name + " already exists");
        }
        Conversation room = conversations.save(Conversation.room(name,
                description == null || description.isBlank() ? null : description.trim(), userId));
        members.save(new ConversationMember(room.getId(), userId));
        return toDtos(List.of(room), userId).getFirst();
    }

    @Transactional
    public ConversationDto joinRoom(Long roomId, Long userId) {
        Conversation room = conversations.findById(roomId).filter(Conversation::isRoom)
                .orElseThrow(() -> ApiException.notFound("Room"));
        if (!members.existsByConversationIdAndUserId(roomId, userId)) {
            members.save(new ConversationMember(roomId, userId));
            // Joining a room starts you at "everything read" rather than burying you in old unread messages.
            messages.findLatestPerConversation(List.of(roomId)).stream().findFirst()
                    .ifPresent(latest -> markRead(roomId, userId, latest.getId()));
            events.toConversation(roomId, ChatEvent.MEMBER_JOINED, userService.byIds(List.of(userId)).get(userId));
        }
        return toDtos(List.of(room), userId).getFirst();
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        Conversation room = requireMember(roomId, userId);
        if (!room.isRoom()) {
            throw ApiException.badRequest("You can't leave a direct message");
        }
        if (DEFAULT_ROOM.equalsIgnoreCase(room.getName())) {
            throw ApiException.badRequest("Everyone stays in #" + DEFAULT_ROOM);
        }
        UserDto user = userService.byIds(List.of(userId)).get(userId);
        events.toConversation(roomId, ChatEvent.MEMBER_LEFT, user);
        members.findByConversationIdAndUserId(roomId, userId).ifPresent(members::delete);
    }

    /** Returns the existing DM between the two users, creating it on first use. Idempotent. */
    @Transactional
    public ConversationDto openDirect(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw ApiException.badRequest("You can't start a conversation with yourself");
        }
        if (!userRepository.existsById(otherUserId)) {
            throw ApiException.notFound("User");
        }
        String key = Conversation.directKey(userId, otherUserId);
        // If both users open the DM at the same instant, the unique direct_key constraint rejects the
        // second insert and the client gets a 409 it can simply retry (it will then find the existing DM).
        Conversation dm = conversations.findByDirectKey(key).orElseGet(() -> {
            Conversation created = conversations.saveAndFlush(Conversation.direct(userId, otherUserId));
            members.save(new ConversationMember(created.getId(), userId));
            members.save(new ConversationMember(created.getId(), otherUserId));
            return created;
        });
        return toDtos(List.of(dm), userId).getFirst();
    }

    @Transactional
    public void markRead(Long conversationId, Long userId, Long messageId) {
        ConversationMember member = members.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this conversation"));
        member.markReadUpTo(messageId);
    }

    @Transactional
    public void joinDefaultRoom(Long userId) {
        conversations.findByTypeAndNameIgnoreCase(ConversationType.ROOM, DEFAULT_ROOM)
                .ifPresent(room -> joinRoom(room.getId(), userId));
    }

    // ---------------------------------------------------------------- mapping

    private List<ConversationDto> toDtos(List<Conversation> list, Long userId) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Conversation::getId).toList();

        Map<Long, Long> counts = memberCounts(ids);
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : messages.countUnreadByConversation(userId)) {
            unread.put((Long) row[0], (Long) row[1]);
        }
        List<ConversationMember> allMembers = members.findByConversationIdIn(ids);
        Map<Long, Long> lastRead = allMembers.stream()
                .filter(m -> m.getUserId().equals(userId))
                .collect(Collectors.toMap(ConversationMember::getConversationId, ConversationMember::getLastReadMessageId));

        // Only DMs list their members; rooms can be large, so they only expose a count.
        Set<Long> directIds = list.stream().filter(c -> !c.isRoom()).map(Conversation::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> directMemberIds = allMembers.stream()
                .filter(m -> directIds.contains(m.getConversationId()))
                .collect(Collectors.groupingBy(ConversationMember::getConversationId,
                        Collectors.mapping(ConversationMember::getUserId, Collectors.toList())));
        Set<Long> userIds = new HashSet<>();
        directMemberIds.values().forEach(userIds::addAll);
        Map<Long, UserDto> users = userService.byIds(userIds);

        List<Message> latest = messages.findLatestPerConversation(ids);
        Map<Long, MessageDto> lastMessages = messageMapper.toDtos(latest).stream()
                .collect(Collectors.toMap(MessageDto::conversationId, Function.identity()));

        return list.stream().map(c -> new ConversationDto(
                c.getId(),
                c.getType(),
                c.getName(),
                c.getDescription(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getLastMessageAt(),
                counts.getOrDefault(c.getId(), 0L),
                directMemberIds.getOrDefault(c.getId(), List.of()).stream().map(users::get).toList(),
                unread.getOrDefault(c.getId(), 0L),
                lastRead.getOrDefault(c.getId(), 0L),
                lastMessages.get(c.getId())
        )).toList();
    }

    private Map<Long, Long> memberCounts(List<Long> conversationIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : members.countByConversation(conversationIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    static String normalizeRoomName(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Room name is required");
        }
        String name = raw.trim().replaceFirst("^#", "").toLowerCase().replaceAll("\\s+", "-");
        if (!name.matches("^[a-z0-9][a-z0-9_-]{1,31}$")) {
            throw ApiException.badRequest(
                    "Room names must be 2-32 characters: lowercase letters, numbers, dashes or underscores");
        }
        return name;
    }
}
