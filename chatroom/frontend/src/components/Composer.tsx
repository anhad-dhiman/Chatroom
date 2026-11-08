import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { MAX_MESSAGE_LENGTH } from '../api/types';
import { SendIcon } from './icons';

interface Props {
  placeholder: string;
  conversationId: number;
  onSend(content: string): void;
}

export function Composer({ placeholder, conversationId, onSend }: Props) {
  const [text, setText] = useState('');
  const ref = useRef<HTMLTextAreaElement>(null);

  // Fresh, focused composer for each conversation.
  useEffect(() => {
    setText('');
    ref.current?.focus();
  }, [conversationId]);

  // Auto-grow up to a max height.
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
  }, [text]);

  function submit(e?: FormEvent) {
    e?.preventDefault();
    const content = text.trim();
    if (!content || content.length > MAX_MESSAGE_LENGTH) return;
    onSend(content);
    setText('');
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  }

  const remaining = MAX_MESSAGE_LENGTH - text.length;

  return (
    <form className="composer" onSubmit={submit}>
      <textarea
        ref={ref}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder={placeholder}
        rows={1}
        maxLength={MAX_MESSAGE_LENGTH}
        aria-label="Message"
      />
      {remaining < 200 && <span className={`char-count ${remaining < 0 ? 'over' : ''}`}>{remaining}</span>}
      <button className="send-button" type="submit" disabled={!text.trim()} aria-label="Send message">
        <SendIcon />
      </button>
    </form>
  );
}
