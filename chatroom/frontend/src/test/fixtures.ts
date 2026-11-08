import type { Conversation, Message, User } from '../api/types';

export const alice: User = { id: 1, username: 'alice', displayName: 'Alice', avatarColor: '#4f46e5' };
export const bob: User = { id: 2, username: 'bob', displayName: 'Bob Builder', avatarColor: '#059669' };

export function message(overrides: Partial<Message> = {}): Message {
  return {
    id: 1,
    conversationId: 10,
    sender: alice,
    content: 'hello',
    createdAt: '2026-01-01T12:00:00Z',
    editedAt: null,
    deleted: false,
    reactions: [],
    ...overrides,
  };
}

export function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 10,
    type: 'ROOM',
    name: 'general',
    description: null,
    createdBy: null,
    createdAt: '2026-01-01T00:00:00Z',
    lastMessageAt: null,
    memberCount: 2,
    members: [],
    unreadCount: 0,
    lastReadMessageId: 0,
    lastMessage: null,
    ...overrides,
  };
}
