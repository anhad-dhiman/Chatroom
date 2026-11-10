"""Searching for rooms and people."""

import pytest

from framework.api_client import ApiClient, unique


@pytest.fixture
def rooms(api: ApiClient, make_user):
    """Two rooms sharing a unique prefix, created through the API by another user."""
    owner = make_user("owner")
    prefix = unique("srch").lower().replace("_", "-")
    names = [f"{prefix}-design", f"{prefix}-devops"]
    for name in names:
        api.create_room(owner, name, f"Room for {name}")
    return prefix, names


@pytest.mark.smoke
def test_room_search_filters_by_name(signed_in, rooms):
    chat, _ = signed_in
    prefix, names = rooms

    modal = chat.browse_rooms().search(prefix)
    modal.wait_for_results(names)

    modal.search(names[1])
    modal.wait_for_results([names[1]])


def test_room_search_with_no_match_shows_empty_state(signed_in):
    chat, _ = signed_in

    modal = chat.browse_rooms().search(unique("nothing"))

    modal.wait_for_no_results()
    assert modal.result_names() == []


def test_joining_a_room_from_search_opens_it(signed_in, rooms):
    chat, _ = signed_in
    _, names = rooms

    modal = chat.browse_rooms().search(names[0])
    modal.wait_for_results([names[0]])
    modal.open_room(names[0])

    assert chat.active_conversation() == names[0]
    assert names[0] in chat.conversation_names()


def test_creating_a_duplicate_room_shows_error(signed_in, rooms):
    chat, _ = signed_in
    _, names = rooms

    modal = chat.browse_rooms()
    modal.create_room(names[0].upper())

    assert "already exists" in modal.error_message()


def test_user_search_finds_people_and_starts_a_dm(signed_in, make_user):
    chat, _ = signed_in
    target = make_user("target", f"Target {unique('x')}")

    modal = chat.new_direct_message().search(target.display_name.split()[1])
    modal.open_with(target.display_name)

    assert chat.active_conversation() == target.display_name
    assert target.display_name in chat.conversation_names()


def test_user_search_does_not_list_yourself(signed_in):
    chat, me = signed_in

    modal = chat.new_direct_message().search(me.username)

    modal.wait.until(lambda _: "Nobody found" in modal.driver.find_element(*modal.DIALOG).text)
    assert me.display_name not in modal.result_names()
