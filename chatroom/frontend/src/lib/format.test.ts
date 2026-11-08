import { message, bob } from '../test/fixtures';
import { formatDay, formatRelative, initials, linkify, preview, startsGroup } from './format';

describe('format helpers', () => {
  it('builds initials', () => {
    expect(initials('Bob Builder')).toBe('BB');
    expect(initials('alice')).toBe('AL');
    expect(initials('jane_doe')).toBe('JD');
    expect(initials('  ')).toBe('?');
  });

  it('groups consecutive messages from the same sender within five minutes', () => {
    const first = message({ id: 1, createdAt: '2026-01-01T12:00:00Z' });
    expect(startsGroup(first, undefined)).toBe(true);
    expect(startsGroup(message({ id: 2, createdAt: '2026-01-01T12:04:00Z' }), first)).toBe(false);
    expect(startsGroup(message({ id: 2, createdAt: '2026-01-01T12:06:00Z' }), first)).toBe(true);
    expect(startsGroup(message({ id: 2, sender: bob, createdAt: '2026-01-01T12:01:00Z' }), first)).toBe(true);
  });

  it('extracts links without trailing punctuation', () => {
    expect(linkify('see https://example.com/a?b=1. thanks')).toEqual([
      { kind: 'text', value: 'see ' },
      { kind: 'link', value: 'https://example.com/a?b=1' },
      { kind: 'text', value: '. thanks' },
    ]);
    expect(linkify('no links here')).toEqual([{ kind: 'text', value: 'no links here' }]);
  });

  it('labels days and relative times', () => {
    const now = new Date('2026-03-10T15:00:00');
    expect(formatDay('2026-03-10T09:00:00', now)).toBe('Today');
    expect(formatDay('2026-03-09T09:00:00', now)).toBe('Yesterday');
    expect(formatRelative('2026-03-10T14:59:30', now)).toBe('now');
    expect(formatRelative('2026-03-10T14:45:00', now)).toBe('15m');
    expect(formatRelative('2026-03-10T12:00:00', now)).toBe('3h');
  });

  it('previews the last message for the sidebar', () => {
    expect(preview(null, 1)).toBe('No messages yet');
    expect(preview(message({ content: 'hi' }), 1)).toBe('You: hi');
    expect(preview(message({ sender: bob, content: 'yo' }), 1)).toBe('yo');
    expect(preview(message({ deleted: true, content: null }), 2)).toBe('Message deleted');
  });
});
