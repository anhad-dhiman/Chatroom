import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Conversation, User } from '../api/types';
import { Avatar } from './Avatar';
import { SearchIcon } from './icons';
import { Modal } from './Modal';

interface Props {
  online: Record<number, true>;
  onClose(): void;
  onOpen(conversation: Conversation): void;
}

export function NewDirectModal({ online, onClose, onOpen }: Props) {
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState<User[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const t = window.setTimeout(() => {
      api.searchUsers(query).then((u) => !cancelled && setUsers(u), (e: Error) => !cancelled && setError(e.message));
    }, 200);
    return () => {
      cancelled = true;
      window.clearTimeout(t);
    };
  }, [query]);

  async function open(user: User) {
    try {
      onOpen(await api.openDirect(user.id));
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <Modal title="New direct message" onClose={onClose}>
      <div className="search-field">
        <SearchIcon />
        <input placeholder="Search people by name or username" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search people" />
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <ul className="picker-list">
        {users === null && <li className="muted padded">Loading…</li>}
        {users?.length === 0 && <li className="muted padded">Nobody found.</li>}
        {users?.map((user) => (
          <li key={user.id}>
            <button className="picker-row" data-testid="user-result" onClick={() => void open(user)}>
              <Avatar user={user} size={32} online={Boolean(online[user.id])} />
              <span className="row-text">
                <span className="row-title">{user.displayName}</span>
                <span className="row-subtitle">@{user.username}{online[user.id] ? ' · online' : ''}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
