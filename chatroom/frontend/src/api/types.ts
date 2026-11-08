export interface User {
  id: number;
  username: string;
  displayName: string;
  avatarColor: string;
}

export interface Reaction {
  emoji: string;
  userIds: number[];
}

export interface Message {
  id: number;
  conversationId: number;
  sender: User;
  /** null when deleted */
  content: string | null;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
  reactions: Reaction[];
}

export type ConversationType = 'ROOM' | 'DIRECT';

export interface Conversation {
  id: number;
  type: ConversationType;
  name: string | null;
  description: string | null;
  createdBy: number | null;
  createdAt: string;
  lastMessageAt: string | null;
  memberCount: number;
  /** Populated for DIRECT conversations only. */
  members: User[];
  unreadCount: number;
  lastReadMessageId: number;
  lastMessage: Message | null;
}

export interface RoomSummary {
  id: number;
  name: string;
  description: string | null;
  memberCount: number;
  joined: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export type ChatEvent =
  | { type: 'message.created' | 'message.updated'; conversationId: number; payload: Message }
  | { type: 'member.joined' | 'member.left'; conversationId: number; payload: User }
  | { type: 'presence'; conversationId: null; payload: { userId: number; online: boolean } };

export const REACTIONS = ['👍', '❤️', '😂', '🎉', '😮', '😢', '🔥', '👀'] as const;
export const MAX_MESSAGE_LENGTH = 2000;
