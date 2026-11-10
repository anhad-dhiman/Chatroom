import type { Conversation, User } from '../api/types';
import type { useTheme } from '../hooks/useTheme';
import { formatRelative, preview } from '../lib/format';
import type { SocketStatus } from '../realtime/useChatSocket';
import { otherMember } from '../state/chatStore';
import { Avatar } from './Avatar';
import { ChatIcon, HashIcon, LogoutIcon, MoonIcon, PlusIcon, SunIcon } from './icons';

interface Props {
  currentUser: User;
  conversations: Conversation[];
  activeId: number | null;
  online: Record<number, true>;
  status: SocketStatus;
  theme: ReturnType<typeof useTheme>;
  onSelect(id: number): void;
  onBrowseRooms(): void;
  onNewDirect(): void;
  onEditProfile(): void;
  onLogout(): void;
}

export function Sidebar(props: Props) {
  const { currentUser, conversations, activeId, online, status, theme } = props;
  const rooms = conversations.filter((c) => c.type === 'ROOM');
  const directs = conversations.filter((c) => c.type === 'DIRECT');

  return (
    <aside className="sidebar">
      <header className="sidebar-header">
        <div className="brand">
          <span className="brand-mark"><ChatIcon width={18} height={18} /></span>
          <span className="brand-name">Chatroom</span>
        </div>
        <button className="icon-button" onClick={theme.toggle} aria-label="Toggle dark mode" title="Toggle dark mode">
          {theme.theme === 'dark' ? <SunIcon /> : <MoonIcon />}
        </button>
      </header>

      {status !== 'connected' && (
        <div className={`connection-banner ${status}`} role="status" data-testid="connection-banner">
          {status === 'connecting' ? 'Connecting…' : 'Connection lost. Reconnecting…'}
        </div>
      )}

      <nav className="sidebar-scroll" aria-label="Conversations">
        <Section title="Rooms" actionLabel="Browse or create rooms" onAction={props.onBrowseRooms}>
          {rooms.map((c) => (
            <ConversationRow
              key={c.id}
              conversation={c}
              active={c.id === activeId}
              onSelect={props.onSelect}
              icon={<span className="room-icon"><HashIcon width={16} height={16} /></span>}
              title={c.name ?? ''}
              subtitle={preview(c.lastMessage, currentUser.id)}
            />
          ))}
        </Section>

        <Section title="Direct messages" actionLabel="New direct message" onAction={props.onNewDirect}>
          {directs.length === 0 && (
            <button className="empty-row" onClick={props.onNewDirect}>Start a conversation</button>
          )}
          {directs.map((c) => {
            const other = otherMember(c, currentUser.id);
            return (
              <ConversationRow
                key={c.id}
                conversation={c}
                active={c.id === activeId}
                onSelect={props.onSelect}
                icon={other ? <Avatar user={other} size={28} online={Boolean(online[other.id])} /> : null}
                title={other?.displayName ?? 'Direct message'}
                subtitle={preview(c.lastMessage, currentUser.id)}
              />
            );
          })}
        </Section>
      </nav>

      <footer className="sidebar-footer">
        <button className="me" onClick={props.onEditProfile} title="Edit profile">
          <Avatar user={currentUser} size={32} online={status === 'connected'} />
          <span className="me-text" data-testid="current-user">
            <span className="me-name">{currentUser.displayName}</span>
            <span className="me-username">@{currentUser.username}</span>
          </span>
        </button>
        <button className="icon-button" onClick={props.onLogout} aria-label="Sign out" title="Sign out">
          <LogoutIcon />
        </button>
      </footer>
    </aside>
  );
}

function Section(props: { title: string; actionLabel: string; onAction(): void; children: React.ReactNode }) {
  return (
    <section className="sidebar-section">
      <div className="section-title">
        <span>{props.title}</span>
        <button className="icon-button small" onClick={props.onAction} aria-label={props.actionLabel} title={props.actionLabel}>
          <PlusIcon width={16} height={16} />
        </button>
      </div>
      {props.children}
    </section>
  );
}

interface RowProps {
  conversation: Conversation;
  active: boolean;
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  onSelect(id: number): void;
}

function ConversationRow({ conversation, active, icon, title, subtitle, onSelect }: RowProps) {
  const unread = conversation.unreadCount > 0 && !active;
  return (
    <button
      data-testid="conversation-row"
      data-conversation-id={conversation.id}
      className={`conversation-row ${active ? 'is-active' : ''} ${unread ? 'is-unread' : ''}`}
      onClick={() => onSelect(conversation.id)}
      aria-current={active ? 'page' : undefined}
    >
      {icon}
      <span className="row-text">
        <span className="row-top">
          <span className="row-title" data-testid="conversation-title">{title}</span>
          {conversation.lastMessageAt && <span className="row-time">{formatRelative(conversation.lastMessageAt)}</span>}
        </span>
        <span className="row-subtitle">{subtitle}</span>
      </span>
      {unread && (
        <span className="badge" data-testid="unread-badge" aria-label={`${conversation.unreadCount} unread`}>
          {conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}
        </span>
      )}
    </button>
  );
}
