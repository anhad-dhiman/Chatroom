import { alice, bob, conversation, message } from '../test/fixtures';
import { chatReducer, conversationTitle, initialChatState, sortedConversations, type ChatState } from './chatStore';

function withConversation(overrides = {}): ChatState {
  return chatReducer(initialChatState, { type: 'conversations/loaded', conversations: [conversation(overrides)] });
}

describe('chatReducer', () => {
  it('counts unread messages from other people only', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'message/created', message: message({ id: 1, sender: bob }), currentUserId: alice.id });
    state = chatReducer(state, { type: 'message/created', message: message({ id: 2, sender: alice }), currentUserId: alice.id });
    expect(state.conversations[10].unreadCount).toBe(1);
    expect(state.conversations[10].lastMessage?.id).toBe(2);
    // Your own message moves your read marker forward.
    expect(state.conversations[10].lastReadMessageId).toBe(2);
  });

  it('ignores duplicate deliveries of the same message', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'history/loaded', conversationId: 10, messages: [], hasMore: false, mode: 'replace' });
    const m = message({ id: 5, sender: bob });
    state = chatReducer(state, { type: 'message/created', message: m, currentUserId: alice.id });
    state = chatReducer(state, { type: 'message/created', message: m, currentUserId: alice.id });
    expect(state.messages[10]).toHaveLength(1);
    expect(state.conversations[10].unreadCount).toBe(1);
  });

  it('only appends to conversations whose history has been loaded', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'message/created', message: message(), currentUserId: alice.id });
    expect(state.messages[10]).toBeUndefined();
  });

  it('prepends older history and keeps messages sorted and unique', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'history/loaded', conversationId: 10, messages: [message({ id: 3 }), message({ id: 4 })], hasMore: true, mode: 'replace' });
    state = chatReducer(state, { type: 'history/loaded', conversationId: 10, messages: [message({ id: 1 }), message({ id: 2 }), message({ id: 3 })], hasMore: false, mode: 'prepend' });
    expect(state.messages[10].map((m) => m.id)).toEqual([1, 2, 3, 4]);
    expect(state.hasMore[10]).toBe(false);
  });

  it('replaces updated messages in place, including the sidebar preview', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'history/loaded', conversationId: 10, messages: [message({ id: 1 })], hasMore: false, mode: 'replace' });
    state = chatReducer(state, { type: 'message/created', message: message({ id: 2 }), currentUserId: alice.id });
    state = chatReducer(state, { type: 'message/updated', message: message({ id: 2, deleted: true, content: null }) });
    expect(state.messages[10][1].deleted).toBe(true);
    expect(state.conversations[10].lastMessage?.deleted).toBe(true);
  });

  it('marks a conversation read without moving the marker backwards', () => {
    let state = withConversation({ unreadCount: 3, lastReadMessageId: 7 });
    state = chatReducer(state, { type: 'conversation/read', conversationId: 10, messageId: 5 });
    expect(state.conversations[10].unreadCount).toBe(0);
    expect(state.conversations[10].lastReadMessageId).toBe(7);
  });

  it('clears the active conversation when it is removed', () => {
    let state = withConversation();
    state = chatReducer(state, { type: 'conversation/activated', conversationId: 10 });
    state = chatReducer(state, { type: 'conversation/removed', conversationId: 10 });
    expect(state.activeId).toBeNull();
    expect(state.conversations[10]).toBeUndefined();
  });

  it('tracks presence', () => {
    let state = chatReducer(initialChatState, { type: 'presence/snapshot', userIds: [1, 2] });
    state = chatReducer(state, { type: 'presence/changed', userId: 2, online: false });
    state = chatReducer(state, { type: 'presence/changed', userId: 3, online: true });
    expect(Object.keys(state.online).map(Number).sort()).toEqual([1, 3]);
  });
});

describe('selectors', () => {
  it('sorts conversations by latest activity', () => {
    const state = chatReducer(initialChatState, {
      type: 'conversations/loaded',
      conversations: [
        conversation({ id: 1, lastMessageAt: '2026-01-01T10:00:00Z' }),
        conversation({ id: 2, lastMessageAt: '2026-01-02T10:00:00Z' }),
        conversation({ id: 3, lastMessageAt: null, createdAt: '2026-01-01T11:00:00Z' }),
      ],
    });
    expect(sortedConversations(state).map((c) => c.id)).toEqual([2, 3, 1]);
  });

  it('titles a DM after the other person', () => {
    const dm = conversation({ type: 'DIRECT', name: null, members: [alice, bob] });
    expect(conversationTitle(dm, alice.id)).toBe('Bob Builder');
    expect(conversationTitle(dm, bob.id)).toBe('Alice');
  });
});
