import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { alice, bob, message } from '../test/fixtures';
import { MessageItem } from './MessageItem';

function setup(overrides = {}, currentUserId = alice.id) {
  const handlers = { onReact: vi.fn(), onEdit: vi.fn().mockResolvedValue(undefined), onDelete: vi.fn() };
  render(<MessageItem message={message(overrides)} currentUserId={currentUserId} showHeader {...handlers} />);
  return handlers;
}

describe('MessageItem', () => {
  it('renders sender, content, edited marker and links', () => {
    setup({ content: 'docs at https://example.com', editedAt: '2026-01-01T12:01:00Z' });
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'https://example.com' })).toHaveAttribute('target', '_blank');
    expect(screen.getByText('(edited)')).toBeInTheDocument();
  });

  it('shows a tombstone for deleted messages and hides actions', () => {
    setup({ deleted: true, content: null });
    expect(screen.getByText('This message was deleted')).toBeInTheDocument();
    expect(screen.queryByRole('toolbar')).not.toBeInTheDocument();
  });

  it('only lets the author edit or delete', () => {
    setup({ sender: bob });
    expect(screen.getByLabelText('Add reaction')).toBeInTheDocument();
    expect(screen.queryByLabelText('Edit message')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Delete message')).not.toBeInTheDocument();
  });

  it('edits inline and saves on Enter', async () => {
    const user = userEvent.setup();
    const { onEdit } = setup({ content: 'helo' });
    await user.click(screen.getByLabelText('Edit message'));
    const box = screen.getByRole('textbox', { name: 'Edit message' });
    await user.clear(box);
    await user.type(box, 'hello{Enter}');
    expect(onEdit).toHaveBeenCalledWith(1, 'hello');
  });

  it('asks for confirmation before deleting', async () => {
    const user = userEvent.setup();
    const { onDelete } = setup();
    await user.click(screen.getByLabelText('Delete message'));
    expect(onDelete).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onDelete).toHaveBeenCalledWith(1);
  });

  it('toggles reactions and highlights your own', async () => {
    // skipHover: jsdom's synthetic pointer moves fire mouseleave without a relatedTarget, which would close the picker.
    const user = userEvent.setup({ skipHover: true });
    const { onReact } = setup({ reactions: [{ emoji: '🎉', userIds: [alice.id, bob.id] }] });
    const chip = screen.getByRole('button', { name: /🎉/ });
    expect(chip).toHaveAttribute('aria-pressed', 'true');
    await user.click(chip);
    expect(onReact).toHaveBeenCalledWith(1, '🎉');

    await user.click(screen.getByLabelText('Add reaction'));
    await user.click(screen.getByLabelText('React with 🔥'));
    expect(onReact).toHaveBeenCalledWith(1, '🔥');
  });
});
