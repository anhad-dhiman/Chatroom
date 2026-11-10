"""
Real-time behaviour, verified with two independent browser sessions (two different users),
just like two people chatting on different computers.
"""

import pytest

from framework.api_client import ApiClient, unique
from pages.chat_page import ChatPage

pytestmark = pytest.mark.realtime


@pytest.fixture
def two_users_in_a_room(api: ApiClient, make_user, browsers):
    alice = make_user("alice", f"Alice {unique('a')}")
    bob = make_user("bob", f"Bob {unique('b')}")
    name = unique("live").lower().replace("_", "-")
    room = api.create_room(alice, name)
    api.join_room(bob, room["id"])

    alice_chat = ChatPage(browsers()).open_as(alice).open_conversation(name)
    bob_chat = ChatPage(browsers()).open_as(bob).open_conversation(name)
    return alice_chat, bob_chat, alice, bob, room


@pytest.mark.smoke
def test_message_is_delivered_live_to_other_user(two_users_in_a_room):
    alice_chat, bob_chat, alice, _, _ = two_users_in_a_room

    alice_chat.send_message("Are you seeing this live?")

    # No refresh: Bob's page must update on its own via the WebSocket.
    delivered = bob_chat.wait_for_message("Are you seeing this live?")
    assert alice.display_name in delivered.text


def test_edits_deletes_and_reactions_sync_live(two_users_in_a_room):
    alice_chat, bob_chat, _, _, _ = two_users_in_a_room
    alice_chat.send_message("Original text")
    alice_chat.send_message("Short-lived message")
    bob_chat.wait_for_message("Short-lived message")

    alice_chat.edit_message("Original text", "Edited text")
    bob_chat.wait_for_edited("Edited text")

    bob_chat.react("Edited text", "👍")
    alice_chat.wait_for_reactions("Edited text", {"👍": 1})

    message_id = alice_chat.wait_for_message("Short-lived message").get_attribute("data-message-id")
    alice_chat.delete_message("Short-lived message")
    bob_chat.wait_for_deleted(message_id)


def test_unread_badge_counts_messages_in_other_conversations(api: ApiClient, two_users_in_a_room):
    _, bob_chat, alice, _, room = two_users_in_a_room
    bob_chat.open_conversation("general")

    api.send_message(alice, room["id"], "one")
    api.send_message(alice, room["id"], "two")

    bob_chat.wait_for_unread(room["name"], 2)

    bob_chat.open_conversation(room["name"])
    bob_chat.wait_for_unread(room["name"], 0)


def test_new_direct_message_appears_for_recipient(two_users_in_a_room):
    alice_chat, bob_chat, alice, bob, _ = two_users_in_a_room

    alice_chat.new_direct_message().search(bob.username).open_with(bob.display_name)
    alice_chat.send_message("Hi Bob, quick question")

    bob_chat.conversation_row(alice.display_name)
    bob_chat.wait_for_unread(alice.display_name, 1)
