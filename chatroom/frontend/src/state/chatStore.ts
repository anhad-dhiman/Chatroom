import type { Conversation, Message, User } from '../api/types';

export interface ChatState {
  conversations: Record<number, Conversation>;
  /** Loaded messages per conversation, oldest first. */
  messages: Record<number, Message[]>;
  /** Whether older history exists beyond what is loaded. */
  hasMore: Record<number, boolean>;
  activeId: number | null;
  online: Record<number, true>;
}

export const initialChatState: ChatState = {
  conversations: {},
  messages: {},
  hasMore: {},
  activeId: null,
  online: {},
};

export type ChatAction =
  | { type: 'conversations/loaded'; conversations: Conversation[] }
  | { type: 'conversation/upserted'; conversation: Conversation }
  | { type: 'conversation/removed'; conversationId: number }
  | { type: 'conversation/activated'; conversationId: number | null }
  | { type: 'conversation/read'; conversationId: number; messageId: number }
  | { type: 'history/loaded'; conversationId: number; messages: Message[]; hasMore: boolean; mode: 'replace' | 'prepend' | 'merge' }
  | { type: 'message/created'; message: Message; currentUserId: number }
  | { type: 'message/updated'; message: Message }
  | { type: 'member/changed'; conversationId: number; delta: 1 | -1 }
  | { type: 'presence/snapshot'; userIds: number[] }
  | { type: 'presence/changed'; userId: number; online: boolean };

export const PAGE_SIZE = 50;

function mergeById(a: Message[], b: Message[]): Message[] {
  const byId = new Map<number, Message>();
  for (const m of a) byId.set(m.id, m);
  for (const m of b) byId.set(m.id, m);
  return [...byId.values()].sort((x, y) => x.id - y.id);
}

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'conversations/loaded': {
      const conversations: Record<number, Conversation> = {};
      for (const c of action.conversations) conversations[c.id] = c;
      const activeId = state.activeId !== null && conversations[state.activeId] ? state.activeId : null;
      return { ...state, conversations, activeId };
    }

    case 'conversation/upserted':
      return {
        ...state,
        conversations: { ...state.conversations, [action.conversation.id]: action.conversation },
      };

    case 'conversation/removed': {
      const { [action.conversationId]: _removed, ...conversations } = state.conversations;
      const { [action.conversationId]: _messages, ...messages } = state.messages;
      return {
        ...state,
        conversations,
        messages,
        activeId: state.activeId === action.conversationId ? null : state.activeId,
      };
    }

    case 'conversation/activated':
      return { ...state, activeId: action.conversationId };

    case 'conversation/read': {
      const c = state.conversations[action.conversationId];
      if (!c) return state;
      return {
        ...state,
        conversations: {
          ...state.conversations,
          [c.id]: { ...c, unreadCount: 0, lastReadMessageId: Math.max(c.lastReadMessageId, action.messageId) },
        },
      };
    }

    case 'history/loaded': {
      const existing = state.messages[action.conversationId] ?? [];
      const messages =
        action.mode === 'replace' ? action.messages : mergeById(existing, action.messages);
      const hasMore =
        action.mode === 'merge' ? (state.hasMore[action.conversationId] ?? action.hasMore) : action.hasMore;
      return {
        ...state,
        messages: { ...state.messages, [action.conversationId]: messages },
        hasMore: { ...state.hasMore, [action.conversationId]: hasMore },
      };
    }

    case 'message/created': {
      const { message, currentUserId } = action;
      const c = state.conversations[message.conversationId];
      const loaded = state.messages[message.conversationId];
      const alreadyHave = loaded?.some((m) => m.id === message.id) ?? false;
      const messages = loaded && !alreadyHave
        ? { ...state.messages, [message.conversationId]: [...loaded, message] }
        : state.messages;
      if (!c) return { ...state, messages };

      const fromSomeoneElse = message.sender.id !== currentUserId;
      const counts = fromSomeoneElse && !alreadyHave && message.id > c.lastReadMessageId;
      return {
        ...state,
        messages,
        conversations: {
          ...state.conversations,
          [c.id]: {
            ...c,
            lastMessage: message,
            lastMessageAt: message.createdAt,
            unreadCount: counts ? c.unreadCount + 1 : c.unreadCount,
            lastReadMessageId: fromSomeoneElse ? c.lastReadMessageId : Math.max(c.lastReadMessageId, message.id),
          },
        },
      };
    }

    case 'message/updated': {
      const { message } = action;
      const loaded = state.messages[message.conversationId];
      const c = state.conversations[message.conversationId];
      const nextConversations =
        c && c.lastMessage?.id === message.id
          ? { ...state.conversations, [c.id]: { ...c, lastMessage: message } }
          : state.conversations;
      return {
        ...state,
        conversations: nextConversations,
        messages: loaded
          ? { ...state.messages, [message.conversationId]: loaded.map((m) => (m.id === message.id ? message : m)) }
          : state.messages,
      };
    }

    case 'member/changed': {
      const c = state.conversations[action.conversationId];
      if (!c) return state;
      return {
        ...state,
        conversations: {
          ...state.conversations,
          [c.id]: { ...c, memberCount: Math.max(0, c.memberCount + action.delta) },
        },
      };
    }

    case 'presence/snapshot': {
      const online: Record<number, true> = {};
      for (const id of action.userIds) online[id] = true;
      return { ...state, online };
    }

    case 'presence/changed': {
      const online = { ...state.online };
      if (action.online) online[action.userId] = true;
      else delete online[action.userId];
      return { ...state, online };
    }
  }
}

// ------------------------------------------------------------------ selectors

export function sortedConversations(state: ChatState): Conversation[] {
  const activity = (c: Conversation) => Date.parse(c.lastMessageAt ?? c.createdAt);
  return Object.values(state.conversations).sort((a, b) => activity(b) - activity(a));
}

/** For a DM, the person you're talking to. */
export function otherMember(conversation: Conversation, currentUserId: number): User | undefined {
  return conversation.members.find((m) => m.id !== currentUserId) ?? conversation.members[0];
}

export function conversationTitle(conversation: Conversation, currentUserId: number): string {
  if (conversation.type === 'ROOM') return conversation.name ?? 'room';
  return otherMember(conversation, currentUserId)?.displayName ?? 'Direct message';
}
