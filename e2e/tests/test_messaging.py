"""Sending, editing, deleting and reacting to messages in a single browser."""

import pytest
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.keys import Keys

from framework.api_client import ApiClient, unique
from pages.chat_page import ChatPage


@pytest.fixture
def room(api: ApiClient, signed_in):
    """A fresh room containing only the signed-in user, already open in the browser."""
    chat, user = signed_in
    name = unique("msg").lower().replace("_", "-")
    api.create_room(user, name)
    chat.driver.refresh()
    chat.wait_until_loaded().open_conversation(name)
    return chat, user, name


@pytest.mark.smoke
def test_sent_message_appears_with_sender(room):
    chat, user, _ = room

    message = chat.send_message("Hello from Selenium")

    assert user.display_name in message.text
    assert "Hello from Selenium" in chat.message_texts()


def test_multiline_message_keeps_line_breaks(room):
    chat, _, _ = room

    composer = chat.visible(ChatPage.COMPOSER)
    composer.send_keys("line one")
    # Shift+Enter inserts a new line; plain Enter sends.
    ActionChains(chat.driver).key_down(Keys.SHIFT).send_keys(Keys.ENTER).key_up(Keys.SHIFT).perform()
    composer.send_keys("line two", Keys.ENTER)

    assert "line one\nline two" in chat.wait_for_message("line two").text


def test_edit_own_message(room):
    chat, _, _ = room
    chat.send_message("Teh first draft")

    chat.edit_message("Teh first draft", "The first draft")

    assert chat.is_edited("The first draft")
    assert not any("Teh first draft" in text for text in chat.message_texts())


def test_delete_own_message_leaves_placeholder(room):
    chat, _, _ = room
    chat.send_message("This will be deleted")

    chat.delete_message("This will be deleted")

    assert not any("This will be deleted" in text for text in chat.message_texts())


def test_cannot_edit_or_delete_someone_elses_message(api: ApiClient, make_user, room):
    chat, user, name = room
    other = make_user("other")
    room_id = next(c["id"] for c in api.conversations(user) if c["name"] == name)
    api.join_room(other, room_id)
    api.send_message(other, room_id, "Message from someone else")

    actions = chat.message_actions("Message from someone else")

    assert "Add reaction" in actions
    assert "Edit message" not in actions
    assert "Delete message" not in actions


def test_add_and_remove_reaction(room):
    chat, _, _ = room
    chat.send_message("React to this")

    chat.react("React to this", "🎉")
    chat.wait_for_reactions("React to this", {"🎉": 1})

    chat.react("React to this", "🎉")  # same emoji again toggles it off
    chat.wait_for_reactions("React to this", {})


def test_dark_mode_preference_is_remembered(room):
    chat, _, _ = room
    before = chat.theme()

    chat.toggle_theme()
    after = chat.theme()
    chat.driver.refresh()
    ChatPage(chat.driver).wait_until_loaded()

    assert after != before
    assert chat.theme() == after
