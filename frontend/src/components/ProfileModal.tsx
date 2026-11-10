import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { User } from '../api/types';
import { Avatar } from './Avatar';
import { Modal } from './Modal';

const COLORS = ['#4f46e5', '#2563eb', '#0891b2', '#059669', '#65a30d', '#d97706', '#dc2626', '#db2777', '#7c3aed', '#475569'];

interface Props {
  user: User;
  onClose(): void;
  onSaved(user: User): void;
}

export function ProfileModal({ user, onClose, onSaved }: Props) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [avatarColor, setAvatarColor] = useState(user.avatarColor);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      onSaved(await api.updateMe({ displayName: displayName.trim(), avatarColor }));
    } catch (err) {
      setError((err as Error).message);
      setSaving(false);
    }
  }

  return (
    <Modal title="Your profile" onClose={onClose}>
      <form onSubmit={save} className="profile-form">
        <div className="profile-preview">
          <Avatar user={{ displayName: displayName || user.username, avatarColor }} size={64} />
          <span className="muted">@{user.username}</span>
        </div>
        <label>
          Display name
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={64} required />
        </label>
        <fieldset className="swatches">
          <legend>Avatar color</legend>
          {COLORS.map((c) => (
            <button
              type="button"
              key={c}
              className={`swatch ${c === avatarColor ? 'is-selected' : ''}`}
              style={{ background: c }}
              onClick={() => setAvatarColor(c)}
              aria-label={`Color ${c}`}
              aria-pressed={c === avatarColor}
            />
          ))}
        </fieldset>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button primary" type="submit" disabled={saving || !displayName.trim()}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </form>
    </Modal>
  );
}
