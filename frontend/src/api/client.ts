import type { AuthResponse, Conversation, Message, RoomSummary, User } from './types';

const TOKEN_KEY = 'chatroom.token';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable (private mode) – the session just won't persist */
    }
  },
};

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Network error – is the server running?');
  }

  if (response.status === 401 && token && !path.startsWith('/api/auth/')) {
    onUnauthorized();
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const data = await response.json();
      if (data?.error) message = data.error;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(response.status, message);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

export const api = {
  register: (username: string, password: string, displayName?: string) =>
    request<AuthResponse>('POST', '/api/auth/register', { username, password, displayName }),
  login: (username: string, password: string) =>
    request<AuthResponse>('POST', '/api/auth/login', { username, password }),

  me: () => request<User>('GET', '/api/users/me'),
  updateMe: (changes: { displayName?: string; avatarColor?: string }) =>
    request<User>('PATCH', '/api/users/me', changes),
  searchUsers: (query: string) => request<User[]>('GET', `/api/users?query=${encodeURIComponent(query)}`),
  onlineUsers: () => request<number[]>('GET', '/api/users/online'),

  conversations: () => request<Conversation[]>('GET', '/api/conversations'),
  conversation: (id: number) => request<Conversation>('GET', `/api/conversations/${id}`),
  markRead: (id: number, messageId: number) =>
    request<void>('POST', `/api/conversations/${id}/read`, { messageId }),

  browseRooms: (query: string) => request<RoomSummary[]>('GET', `/api/rooms?query=${encodeURIComponent(query)}`),
  createRoom: (name: string, description: string) =>
    request<Conversation>('POST', '/api/rooms', { name, description }),
  joinRoom: (id: number) => request<Conversation>('POST', `/api/rooms/${id}/join`),
  leaveRoom: (id: number) => request<void>('POST', `/api/rooms/${id}/leave`),
  openDirect: (userId: number) => request<Conversation>('POST', `/api/direct/${userId}`),

  messages: (conversationId: number, before?: number, limit = 50) =>
    request<Message[]>(
      'GET',
      `/api/conversations/${conversationId}/messages?limit=${limit}${before ? `&before=${before}` : ''}`,
    ),
  sendMessage: (conversationId: number, content: string) =>
    request<Message>('POST', `/api/conversations/${conversationId}/messages`, { content }),
  editMessage: (id: number, content: string) => request<Message>('PATCH', `/api/messages/${id}`, { content }),
  deleteMessage: (id: number) => request<Message>('DELETE', `/api/messages/${id}`),
  toggleReaction: (id: number, emoji: string) => request<Message>('POST', `/api/messages/${id}/reactions`, { emoji }),
};
