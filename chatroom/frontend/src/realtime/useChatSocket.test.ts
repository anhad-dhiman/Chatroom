import { socketUrl } from './useChatSocket';

describe('socketUrl', () => {
  it('uses ws on http and wss on https', () => {
    expect(socketUrl({ protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/ws');
    expect(socketUrl({ protocol: 'https:', host: 'chat.example.com' })).toBe('wss://chat.example.com/ws');
  });
});
