import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Conversation, RoomSummary } from '../api/types';
import { HashIcon, SearchIcon } from './icons';
import { Modal } from './Modal';

interface Props {
  onClose(): void;
  onOpen(conversation: Conversation): void;
  onSelectExisting(id: number): void;
}

export function BrowseRoomsModal({ onClose, onOpen, onSelectExisting }: Props) {
  const [query, setQuery] = useState('');
  const [rooms, setRooms] = useState<RoomSummary[] | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const t = window.setTimeout(() => {
      api.browseRooms(query).then((r) => !cancelled && setRooms(r), (e: Error) => !cancelled && setError(e.message));
    }, 200);
    return () => {
      cancelled = true;
      window.clearTimeout(t);
    };
  }, [query]);

  async function join(room: RoomSummary) {
    if (room.joined) {
      onSelectExisting(room.id);
      return;
    }
    try {
      onOpen(await api.joinRoom(room.id));
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCreating(true);
    try {
      onOpen(await api.createRoom(name, description));
    } catch (err) {
      setError((err as Error).message);
      setCreating(false);
    }
  }

  return (
    <Modal title="Rooms" onClose={onClose}>
      <div className="search-field">
        <SearchIcon />
        <input placeholder="Search rooms" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search rooms" />
      </div>

      <ul className="picker-list">
        {rooms === null && <li className="muted padded">Loading…</li>}
        {rooms?.length === 0 && <li className="muted padded">No rooms match “{query}”. Create it below!</li>}
        {rooms?.map((room) => (
          <li key={room.id}>
            <button className="picker-row" data-testid="room-result" onClick={() => void join(room)}>
              <span className="room-icon"><HashIcon width={16} height={16} /></span>
              <span className="row-text">
                <span className="row-title">{room.name}</span>
                <span className="row-subtitle">
                  {room.memberCount} {room.memberCount === 1 ? 'member' : 'members'}
                  {room.description ? ` · ${room.description}` : ''}
                </span>
              </span>
              <span className={`pill ${room.joined ? '' : 'accent'}`}>{room.joined ? 'Open' : 'Join'}</span>
            </button>
          </li>
        ))}
      </ul>

      <form className="create-room" onSubmit={create}>
        <h3>Create a room</h3>
        <label>
          Name
          <input name="roomName" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. book-club" maxLength={33} required />
        </label>
        <label>
          <span>Description <span className="muted">(optional)</span></span>
          <input name="roomDescription" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button primary" type="submit" disabled={creating || name.trim().length < 2}>
          {creating ? 'Creating…' : 'Create room'}
        </button>
      </form>
    </Modal>
  );
}
