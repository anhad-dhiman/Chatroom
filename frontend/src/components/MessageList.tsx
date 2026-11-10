import { Fragment, useLayoutEffect, useRef } from 'react';
import type { Message } from '../api/types';
import { formatDay, sameDay, startsGroup } from '../lib/format';
import { MessageItem } from './MessageItem';

interface Props {
  conversationId: number;
  messages: Message[];
  currentUserId: number;
  hasMore: boolean;
  loadingOlder: boolean;
  /** Messages with an id above this (from others) are shown under a "New" divider. */
  unreadAfterId: number | null;
  onLoadOlder(): void;
  onReact(messageId: number, emoji: string): void;
  onEdit(messageId: number, content: string): Promise<void>;
  onDelete(messageId: number): void;
}

export function MessageList(props: Props) {
  const { conversationId, messages, currentUserId, hasMore, loadingOlder, unreadAfterId } = props;
  const scrollRef = useRef<HTMLDivElement>(null);
  const nearBottom = useRef(true);
  const heightBeforePrepend = useRef<number | null>(null);
  const lastIdRef = useRef<number | null>(null);

  useLayoutEffect(() => {
    nearBottom.current = true;
    lastIdRef.current = null;
  }, [conversationId]);

  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const last = messages[messages.length - 1];

    if (heightBeforePrepend.current !== null) {
      // Older history was inserted above: keep the same message under the user's eyes.
      el.scrollTop += el.scrollHeight - heightBeforePrepend.current;
      heightBeforePrepend.current = null;
    } else if (nearBottom.current || (last && last.id !== lastIdRef.current && last.sender.id === currentUserId)) {
      el.scrollTop = el.scrollHeight;
    }
    lastIdRef.current = last?.id ?? null;
  }, [messages, currentUserId]);

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    if (el.scrollTop < 150 && hasMore && !loadingOlder && heightBeforePrepend.current === null) {
      heightBeforePrepend.current = el.scrollHeight;
      props.onLoadOlder();
    }
  }

  const firstUnread = unreadAfterId === null
    ? undefined
    : messages.find((m) => m.id > unreadAfterId && m.sender.id !== currentUserId)?.id;

  return (
    <div className="message-list" ref={scrollRef} onScroll={onScroll} role="log" aria-live="polite">
      {hasMore ? (
        <div className="history-edge">
          {loadingOlder ? <span className="spinner small" aria-label="Loading older messages" /> : (
            <button
              className="link-button"
              onClick={() => {
                heightBeforePrepend.current = scrollRef.current?.scrollHeight ?? null;
                props.onLoadOlder();
              }}
            >
              Load older messages
            </button>
          )}
        </div>
      ) : (
        messages.length > 0 && <div className="history-edge muted">This is the beginning of the conversation.</div>
      )}

      {messages.map((m, i) => {
        const prev = messages[i - 1];
        const newDay = !prev || !sameDay(new Date(prev.createdAt), new Date(m.createdAt));
        const isFirstUnread = m.id === firstUnread;
        return (
          <Fragment key={m.id}>
            {newDay && <div className="divider"><span>{formatDay(m.createdAt)}</span></div>}
            {isFirstUnread && <div className="divider unread"><span>New</span></div>}
            <MessageItem
              message={m}
              currentUserId={currentUserId}
              showHeader={newDay || isFirstUnread || startsGroup(m, prev)}
              onReact={props.onReact}
              onEdit={props.onEdit}
              onDelete={props.onDelete}
            />
          </Fragment>
        );
      })}
    </div>
  );
}
