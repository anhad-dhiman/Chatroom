import { memo, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { MAX_MESSAGE_LENGTH, REACTIONS, type Message } from '../api/types';
import { formatTime, linkify } from '../lib/format';
import { Avatar } from './Avatar';
import { EditIcon, SmileIcon, TrashIcon } from './icons';

interface Props {
  message: Message;
  currentUserId: number;
  /** Show avatar + name (first message of a group). */
  showHeader: boolean;
  onReact(messageId: number, emoji: string): void;
  onEdit(messageId: number, content: string): Promise<void>;
  onDelete(messageId: number): void;
}

export const MessageItem = memo(function MessageItem({ message, currentUserId, showHeader, onReact, onEdit, onDelete }: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(message.content ?? '');
  const [picker, setPicker] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const editRef = useRef<HTMLTextAreaElement>(null);
  const own = message.sender.id === currentUserId;

  useEffect(() => {
    if (editing) {
      const el = editRef.current;
      el?.focus();
      el?.setSelectionRange(el.value.length, el.value.length);
    }
  }, [editing]);

  async function saveEdit() {
    const content = draft.trim();
    if (!content || content === message.content) {
      setEditing(false);
      return;
    }
    try {
      await onEdit(message.id, content);
      setEditing(false);
    } catch {
      /* error toast is shown by the parent; stay in edit mode */
    }
  }

  function onEditKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void saveEdit();
    } else if (e.key === 'Escape') {
      setEditing(false);
      setDraft(message.content ?? '');
    }
  }

  return (
    <article
      data-testid="message"
      data-message-id={message.id}
      className={`message ${showHeader ? 'has-header' : 'is-grouped'} ${own ? 'is-own' : ''}`}
      onMouseLeave={() => { setPicker(false); setConfirmDelete(false); }}
      aria-label={`${message.sender.displayName} at ${formatTime(message.createdAt)}`}
    >
      <div className="message-gutter">
        {showHeader ? <Avatar user={message.sender} size={36} /> : <time className="gutter-time">{formatTime(message.createdAt)}</time>}
      </div>

      <div className="message-main">
        {showHeader && (
          <div className="message-header">
            <span className="sender">{message.sender.displayName}</span>
            <time dateTime={message.createdAt} title={new Date(message.createdAt).toLocaleString()}>
              {formatTime(message.createdAt)}
            </time>
          </div>
        )}

        {message.deleted ? (
          <p className="message-body is-deleted" data-testid="message-deleted">This message was deleted</p>
        ) : editing ? (
          <div className="edit-box">
            <textarea
              ref={editRef}
              value={draft}
              maxLength={MAX_MESSAGE_LENGTH}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onEditKey}
              rows={Math.min(8, draft.split('\n').length)}
              aria-label="Edit message"
            />
            <div className="edit-actions">
              <span className="hint">Enter to save · Esc to cancel</span>
              <button className="button ghost small" onClick={() => setEditing(false)}>Cancel</button>
              <button className="button primary small" onClick={() => void saveEdit()}>Save</button>
            </div>
          </div>
        ) : (
          <p className="message-body" data-testid="message-body">
            {linkify(message.content ?? '').map((part, i) =>
              part.kind === 'link' ? (
                <a key={i} href={part.value} target="_blank" rel="noreferrer noopener">{part.value}</a>
              ) : (
                <span key={i}>{part.value}</span>
              ),
            )}
            {message.editedAt && <span className="edited" data-testid="message-edited" title={new Date(message.editedAt).toLocaleString()}> (edited)</span>}
          </p>
        )}

        {message.reactions.length > 0 && (
          <div className="reactions">
            {message.reactions.map((r) => {
              const mine = r.userIds.includes(currentUserId);
              return (
                <button
                  key={r.emoji}
                  data-testid="reaction"
                  className={`reaction ${mine ? 'is-mine' : ''}`}
                  onClick={() => onReact(message.id, r.emoji)}
                  aria-pressed={mine}
                  title={reactionTitle(r.userIds.length, mine)}
                >
                  <span>{r.emoji}</span>
                  <span className="reaction-count">{r.userIds.length}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {!message.deleted && !editing && (
        <div className="message-toolbar" role="toolbar" aria-label="Message actions">
          {confirmDelete ? (
            <>
              <span className="confirm-text">Delete message?</span>
              <button className="button danger small" onClick={() => onDelete(message.id)}>Delete</button>
              <button className="button ghost small" onClick={() => setConfirmDelete(false)}>Cancel</button>
            </>
          ) : (
            <>
              <button className="icon-button small" onClick={() => setPicker((p) => !p)} aria-label="Add reaction" title="Add reaction">
                <SmileIcon width={16} height={16} />
              </button>
              {own && (
                <>
                  <button className="icon-button small" onClick={() => { setDraft(message.content ?? ''); setEditing(true); }} aria-label="Edit message" title="Edit">
                    <EditIcon width={16} height={16} />
                  </button>
                  <button className="icon-button small" onClick={() => setConfirmDelete(true)} aria-label="Delete message" title="Delete">
                    <TrashIcon width={16} height={16} />
                  </button>
                </>
              )}
            </>
          )}
          {picker && (
            <div className="emoji-picker" role="menu">
              {REACTIONS.map((emoji) => (
                <button
                  key={emoji}
                  role="menuitem"
                  onClick={() => { onReact(message.id, emoji); setPicker(false); }}
                  aria-label={`React with ${emoji}`}
                >
                  {emoji}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </article>
  );
});

function reactionTitle(count: number, mine: boolean): string {
  if (!mine) return `${count} ${count === 1 ? 'person' : 'people'}`;
  if (count === 1) return 'You reacted · click to remove';
  return `You and ${count - 1} ${count === 2 ? 'other' : 'others'} · click to remove`;
}
