import type { Message } from '../api/types';

/** Messages from the same sender within this window are visually grouped. */
export const GROUP_WINDOW_MS = 5 * 60 * 1000;

export function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function formatDay(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  if (sameDay(date, now)) return 'Today';
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(date, yesterday)) return 'Yesterday';
  return date.toLocaleDateString([], {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    ...(date.getFullYear() !== now.getFullYear() ? { year: 'numeric' } : {}),
  });
}

/** Short relative label for the sidebar: "now", "5m", "3h", "Mon", "Mar 4". */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  const diff = now.getTime() - date.getTime();
  if (diff < 60_000) return 'now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m`;
  if (sameDay(date, now)) return `${Math.floor(diff / 3_600_000)}h`;
  if (diff < 6 * 86_400_000) return date.toLocaleDateString([], { weekday: 'short' });
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

/** Should this message show the avatar + name header, or be grouped under the previous one? */
export function startsGroup(message: Message, previous: Message | undefined): boolean {
  if (!previous) return true;
  if (previous.sender.id !== message.sender.id) return true;
  const a = new Date(previous.createdAt);
  const b = new Date(message.createdAt);
  return !sameDay(a, b) || b.getTime() - a.getTime() > GROUP_WINDOW_MS;
}

export function initials(name: string): string {
  const parts = name.trim().split(/[\s_]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

export type TextPart = { kind: 'text'; value: string } | { kind: 'link'; value: string };

/** Splits text into plain and URL parts so links can be rendered without dangerouslySetInnerHTML. */
export function linkify(text: string): TextPart[] {
  const parts: TextPart[] = [];
  const re = /https?:\/\/[^\s<]+[^\s<.,;:!?)\]'"]/g;
  let last = 0;
  for (const match of text.matchAll(re)) {
    const index = match.index ?? 0;
    if (index > last) parts.push({ kind: 'text', value: text.slice(last, index) });
    parts.push({ kind: 'link', value: match[0] });
    last = index + match[0].length;
  }
  if (last < text.length) parts.push({ kind: 'text', value: text.slice(last) });
  return parts;
}

export function preview(message: Message | null, currentUserId: number): string {
  if (!message) return 'No messages yet';
  const who = message.sender.id === currentUserId ? 'You: ' : '';
  return message.deleted ? `${who}Message deleted` : `${who}${message.content ?? ''}`;
}
