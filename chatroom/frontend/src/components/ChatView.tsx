import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Conversation, Message, User } from '../api/types';
import { conversationTitle, otherMember, PAGE_SIZE, type ChatAction } from '../state/chatStore';
import { Avatar } from './Avatar';
import { Composer } from './Composer';
import { BackIcon, HashIcon } from './icons';
import { MessageList } from './MessageList';

interface Props {
  conversation: Conversation;
  messages: Message[] | undefined;
  hasMore: boolean;
  currentUser: User;
  online: Record<number, true>;
  dispatch(action: ChatAction): void;
  onSend(conversationId: number, content: string): void;
  onLeave(conversationId: number): void;
  onBack(): void;
  onError(message: string): void;
}

export function ChatView({ conversation, messages, hasMore, currentUser, online, dispatch, onSend, onLeave, onBack, onError }: Props) {
  const [loadingOlder, setLoadingOlder] = useState(false);
  // Snapshot the read marker when the conversation is opened, so the "New" divider doesn't jump around.
  const [unreadAfterId, setUnreadAfterId] = useState<number | null>(null);
  const id = conversation.id;

  useEffect(() => {
    setUnreadAfterId(conversation.unreadCount > 0 ? conversation.lastReadMessageId : null);
    // Intentionally only re-run when switching conversations.
  }, [id]);

  // Initial history load.
  useEffect(() => {
    if (messages) return;
    let cancelled = false;
    api.messages(id).then(
      (page) => !cancelled && dispatch({ type: 'history/loaded', conversationId: id, messages: page, hasMore: page.length === PAGE_SIZE, mode: 'replace' }),
      (e: Error) => !cancelled && onError(e.message),
    );
    return () => {
      cancelled = true;
    };
  }, [id, messages, dispatch, onError]);

  // Mark everything visible as read while the tab is focused.
  const latestId = messages?.[messages.length - 1]?.id ?? 0;
  useEffect(() => {
    const markRead = () => {
      if (document.visibilityState !== 'visible') return;
      if (latestId > conversation.lastReadMessageId || conversation.unreadCount > 0) {
        dispatch({ type: 'conversation/read', conversationId: id, messageId: latestId });
        if (latestId > 0) api.markRead(id, latestId).catch(() => {});
      }
    };
    markRead();
    document.addEventListener('visibilitychange', markRead);
    return () => document.removeEventListener('visibilitychange', markRead);
  }, [id, latestId, conversation.lastReadMessageId, conversation.unreadCount, dispatch]);

  const loadOlder = useCallback(async () => {
    if (!messages?.length) return;
    setLoadingOlder(true);
    try {
      const page = await api.messages(id, messages[0].id);
      dispatch({ type: 'history/loaded', conversationId: id, messages: page, hasMore: page.length === PAGE_SIZE, mode: 'prepend' });
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setLoadingOlder(false);
    }
  }, [id, messages, dispatch, onError]);

  const onReact = useCallback((messageId: number, emoji: string) => {
    api.toggleReaction(messageId, emoji).then(
      (m) => dispatch({ type: 'message/updated', message: m }),
      (e: Error) => onError(e.message),
    );
  }, [dispatch, onError]);

  const onEdit = useCallback(async (messageId: number, content: string) => {
    try {
      dispatch({ type: 'message/updated', message: await api.editMessage(messageId, content) });
    } catch (e) {
      onError((e as Error).message);
      throw e;
    }
  }, [dispatch, onError]);

  const onDelete = useCallback((messageId: number) => {
    api.deleteMessage(messageId).then(
      (m) => dispatch({ type: 'message/updated', message: m }),
      (e: Error) => onError(e.message),
    );
  }, [dispatch, onError]);

  const isRoom = conversation.type === 'ROOM';
  const other = isRoom ? undefined : otherMember(conversation, currentUser.id);
  const title = conversationTitle(conversation, currentUser.id);

  return (
    <section className="chat-view" aria-label={title}>
      <header className="chat-header">
        <button className="icon-button mobile-only" onClick={onBack} aria-label="Back to conversations">
          <BackIcon />
        </button>
        {isRoom ? (
          <span className="room-icon large"><HashIcon /></span>
        ) : (
          other && <Avatar user={other} size={34} online={Boolean(online[other.id])} />
        )}
        <div className="chat-title">
          <h1>{title}</h1>
          <p className="muted">
            {isRoom
              ? `${conversation.memberCount} ${conversation.memberCount === 1 ? 'member' : 'members'}${conversation.description ? ` · ${conversation.description}` : ''}`
              : other && (online[other.id] ? 'Online' : `@${other.username}`)}
          </p>
        </div>
        {isRoom && conversation.name !== 'general' && (
          <button className="button ghost small" onClick={() => onLeave(id)}>Leave</button>
        )}
      </header>

      {messages === undefined ? (
        <div className="chat-loading"><span className="spinner" aria-label="Loading messages" /></div>
      ) : messages.length === 0 ? (
        <div className="chat-empty">
          <div className="empty-illustration">{isRoom ? <HashIcon width={28} height={28} /> : other && <Avatar user={other} size={56} />}</div>
          <h2>{isRoom ? `Welcome to #${conversation.name}` : `This is the start of your conversation with ${title}`}</h2>
          <p className="muted">{isRoom ? conversation.description ?? 'Say hello 👋' : 'Messages here are only visible to the two of you.'}</p>
        </div>
      ) : (
        <MessageList
          conversationId={id}
          messages={messages}
          currentUserId={currentUser.id}
          hasMore={hasMore}
          loadingOlder={loadingOlder}
          unreadAfterId={unreadAfterId}
          onLoadOlder={loadOlder}
          onReact={onReact}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      )}

      <Composer
        conversationId={id}
        placeholder={isRoom ? `Message #${conversation.name}` : `Message ${title}`}
        onSend={(content) => onSend(id, content)}
      />
    </section>
  );
}
