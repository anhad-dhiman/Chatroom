import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { api } from '../api/client';
import type { ChatEvent, Conversation, User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import type { useTheme } from '../hooks/useTheme';
import { useChatSocket } from '../realtime/useChatSocket';
import { chatReducer, conversationTitle, initialChatState, PAGE_SIZE, sortedConversations } from '../state/chatStore';
import { BrowseRoomsModal } from './BrowseRoomsModal';
import { ChatView } from './ChatView';
import { ChatIcon } from './icons';
import { NewDirectModal } from './NewDirectModal';
import { ProfileModal } from './ProfileModal';
import { Sidebar } from './Sidebar';
import { Toasts, useToasts } from './Toasts';

type ModalKind = 'rooms' | 'direct' | 'profile' | null;

export function ChatApp({ user, theme }: { user: User; theme: ReturnType<typeof useTheme> }) {
  const { token, logout, setUser } = useAuth();
  const [state, dispatch] = useReducer(chatReducer, initialChatState);
  const [modal, setModal] = useState<ModalKind>(null);
  const { toasts, push } = useToasts();
  const stateRef = useRef(state);
  stateRef.current = state;
  const connectedBefore = useRef(false);

  const refreshConversations = useCallback(async (): Promise<Conversation[]> => {
    try {
      const conversations = await api.conversations();
      dispatch({ type: 'conversations/loaded', conversations });
      return conversations;
    } catch (e) {
      push((e as Error).message);
      return [];
    }
  }, [push]);

  // Initial load. On first load, open the most recent conversation (unless on a phone-sized screen).
  useEffect(() => {
    void (async () => {
      const loaded = await refreshConversations();
      const first = sortedConversations({ ...initialChatState, conversations: Object.fromEntries(loaded.map((c) => [c.id, c])) })[0];
      if (first && stateRef.current.activeId === null && window.innerWidth > 768) {
        dispatch({ type: 'conversation/activated', conversationId: first.id });
      }
    })();
  }, [refreshConversations]);

  const onEvent = useCallback((event: ChatEvent) => {
    switch (event.type) {
      case 'message.created':
        if (!stateRef.current.conversations[event.conversationId]) {
          // A brand-new DM someone just started with us.
          void refreshConversations();
        }
        dispatch({ type: 'message/created', message: event.payload, currentUserId: user.id });
        break;
      case 'message.updated':
        dispatch({ type: 'message/updated', message: event.payload });
        break;
      case 'member.joined':
        if (event.payload.id !== user.id) dispatch({ type: 'member/changed', conversationId: event.conversationId, delta: 1 });
        break;
      case 'member.left':
        if (event.payload.id === user.id) dispatch({ type: 'conversation/removed', conversationId: event.conversationId });
        else dispatch({ type: 'member/changed', conversationId: event.conversationId, delta: -1 });
        break;
      case 'presence':
        dispatch({ type: 'presence/changed', userId: event.payload.userId, online: event.payload.online });
        break;
    }
  }, [refreshConversations, user.id]);

  const onConnected = useCallback(() => {
    api.onlineUsers().then((ids) => dispatch({ type: 'presence/snapshot', userIds: ids }), () => {});
    if (!connectedBefore.current) {
      connectedBefore.current = true;
      return;
    }
    // Reconnected after a drop: catch up on anything we missed.
    void refreshConversations();
    const active = stateRef.current.activeId;
    if (active !== null) {
      api.messages(active).then(
        (page) => dispatch({ type: 'history/loaded', conversationId: active, messages: page, hasMore: page.length === PAGE_SIZE, mode: 'merge' }),
        () => {},
      );
    }
  }, [refreshConversations]);

  const { status, send } = useChatSocket(token, { onEvent, onError: push, onConnected });

  const onSend = useCallback((conversationId: number, content: string) => {
    // Prefer the open socket; fall back to HTTP if it's down so the message isn't lost.
    if (send(conversationId, content)) return;
    api.sendMessage(conversationId, content).then(
      (message) => dispatch({ type: 'message/created', message, currentUserId: user.id }),
      (e: Error) => push(e.message),
    );
  }, [send, push, user.id]);

  const openConversation = useCallback((conversation: Conversation) => {
    dispatch({ type: 'conversation/upserted', conversation });
    dispatch({ type: 'conversation/activated', conversationId: conversation.id });
    setModal(null);
  }, []);

  const select = useCallback((id: number) => {
    dispatch({ type: 'conversation/activated', conversationId: id });
    setModal(null);
  }, []);

  const onLeave = useCallback(async (id: number) => {
    try {
      await api.leaveRoom(id);
      dispatch({ type: 'conversation/removed', conversationId: id });
    } catch (e) {
      push((e as Error).message);
    }
  }, [push]);

  const conversations = useMemo(() => sortedConversations(state), [state]);
  const active = state.activeId !== null ? state.conversations[state.activeId] : undefined;

  // Unread count in the tab title.
  useEffect(() => {
    const unread = conversations.reduce((n, c) => n + (c.id === state.activeId ? 0 : c.unreadCount), 0);
    const where = active ? ` · ${active.type === 'ROOM' ? '#' : ''}${conversationTitle(active, user.id)}` : '';
    document.title = `${unread > 0 ? `(${unread}) ` : ''}Chatroom${where}`;
  }, [conversations, state.activeId, active, user.id]);

  return (
    <div className={`app-shell ${active ? 'has-active' : ''}`}>
      <Sidebar
        currentUser={user}
        conversations={conversations}
        activeId={state.activeId}
        online={state.online}
        status={status}
        theme={theme}
        onSelect={select}
        onBrowseRooms={() => setModal('rooms')}
        onNewDirect={() => setModal('direct')}
        onEditProfile={() => setModal('profile')}
        onLogout={logout}
      />

      <main className="main-pane">
        {active ? (
          <ChatView
            key={active.id}
            conversation={active}
            messages={state.messages[active.id]}
            hasMore={state.hasMore[active.id] ?? false}
            currentUser={user}
            online={state.online}
            dispatch={dispatch}
            onSend={onSend}
            onLeave={onLeave}
            onBack={() => dispatch({ type: 'conversation/activated', conversationId: null })}
            onError={push}
          />
        ) : (
          <div className="no-selection">
            <span className="brand-mark large"><ChatIcon width={32} height={32} /></span>
            <h2>Pick a conversation</h2>
            <p className="muted">Choose a room or a direct message from the sidebar, or start something new.</p>
            <div className="row-actions">
              <button className="button primary" onClick={() => setModal('rooms')}>Browse rooms</button>
              <button className="button ghost" onClick={() => setModal('direct')}>Message someone</button>
            </div>
          </div>
        )}
      </main>

      {modal === 'rooms' && <BrowseRoomsModal onClose={() => setModal(null)} onOpen={openConversation} onSelectExisting={select} />}
      {modal === 'direct' && <NewDirectModal online={state.online} onClose={() => setModal(null)} onOpen={openConversation} />}
      {modal === 'profile' && (
        <ProfileModal
          user={user}
          onClose={() => setModal(null)}
          onSaved={(u) => {
            setUser(u);
            setModal(null);
          }}
        />
      )}
      <Toasts toasts={toasts} />
    </div>
  );
}
