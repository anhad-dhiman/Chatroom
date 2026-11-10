import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import type { ChatEvent } from '../api/types';

export type SocketStatus = 'connecting' | 'connected' | 'disconnected';

interface Handlers {
  onEvent(event: ChatEvent): void;
  onError(message: string): void;
  /** Called on every (re)connect so the app can re-sync anything missed while offline. */
  onConnected(): void;
}

export function socketUrl(location: Pick<Location, 'protocol' | 'host'> = window.location): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

/**
 * Maintains one STOMP connection for the signed-in user, reconnecting automatically.
 * Returns a `send` function that publishes over the socket, or returns false if it's not connected.
 */
export function useChatSocket(token: string | null, handlers: Handlers) {
  const [status, setStatus] = useState<SocketStatus>('connecting');
  const clientRef = useRef<Client | null>(null);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    if (!token) return;
    const parse = (frame: IMessage) => JSON.parse(frame.body);

    const client = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('connected');
        client.subscribe('/user/queue/events', (f) => handlersRef.current.onEvent(parse(f)));
        client.subscribe('/topic/presence', (f) => handlersRef.current.onEvent(parse(f)));
        client.subscribe('/user/queue/errors', (f) => handlersRef.current.onError(f.body));
        handlersRef.current.onConnected();
      },
      onWebSocketClose: () => setStatus((s) => (s === 'connected' ? 'disconnected' : s)),
      onStompError: (frame) => {
        setStatus('disconnected');
        handlersRef.current.onError(frame.headers.message ?? 'Connection error');
      },
    });
    clientRef.current = client;
    setStatus('connecting');
    client.activate();
    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [token]);

  const send = (conversationId: number, content: string): boolean => {
    const client = clientRef.current;
    if (!client?.connected) return false;
    client.publish({
      destination: `/app/conversations/${conversationId}/send`,
      body: JSON.stringify({ content }),
    });
    return true;
  };

  return { status, send };
}
