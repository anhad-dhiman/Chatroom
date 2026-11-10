import type { User } from '../api/types';
import { initials } from '../lib/format';

interface Props {
  user: Pick<User, 'displayName' | 'avatarColor'>;
  size?: number;
  online?: boolean;
}

export function Avatar({ user, size = 36, online }: Props) {
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.4, background: user.avatarColor }} aria-hidden>
      {initials(user.displayName)}
      {online !== undefined && <span className={`presence-dot ${online ? 'is-online' : ''}`} />}
    </span>
  );
}
