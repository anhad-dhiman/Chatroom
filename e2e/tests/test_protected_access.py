"""
Access control: what a logged-out or unauthorised user can and can't reach.

The UI checks show what a user sees; the API checks are black-box tests of the server itself,
because hiding a button in the UI is not security, and the server must refuse the request too.
"""

import pytest

from framework.api_client import ApiClient
from pages.chat_page import TOKEN_KEY, ChatPage
from pages.login_page import LoginPage


@pytest.mark.smoke
def test_logged_out_visitor_sees_login_not_chat(driver):
    login = LoginPage(driver).open()

    assert login.heading() == "Welcome back"
    assert not login.is_present(ChatPage.CONVERSATION_ROW)
    assert not login.is_present(ChatPage.COMPOSER)


def test_forged_token_is_rejected_and_cleared(driver):
    login = LoginPage(driver).open()
    driver.execute_script("window.localStorage.setItem(arguments[0], arguments[1]);", TOKEN_KEY, "forged.token.value")

    driver.refresh()

    assert login.visible(LoginPage.SCREEN)
    login.wait.until(lambda _: login.local_storage(TOKEN_KEY) is None, "the invalid token should be removed")


@pytest.mark.parametrize(
    "method, path",
    [
        ("GET", "/api/conversations"),
        ("GET", "/api/users/me"),
        ("GET", "/api/rooms"),
        ("POST", "/api/rooms"),
        ("GET", "/api/conversations/1/messages"),
    ],
)
def test_api_requires_authentication(api: ApiClient, method, path):
    response = api.request(method, path)
    assert response.status_code == 401


def test_api_rejects_forged_token(api: ApiClient):
    assert api.request("GET", "/api/conversations", token="forged.token.value").status_code == 401


def test_user_cannot_read_or_post_in_someone_elses_dm(api: ApiClient, make_user):
    alice, bob, eve = make_user("alice"), make_user("bob"), make_user("eve")
    dm = api.open_direct(alice, bob)
    api.send_message(alice, dm["id"], "private note")

    assert api.request("GET", f"/api/conversations/{dm['id']}/messages", eve.token).status_code == 403
    response = api.request("POST", f"/api/conversations/{dm['id']}/messages", eve.token, {"content": "let me in"})
    assert response.status_code == 403


def test_other_users_dm_is_not_listed(api: ApiClient, make_user, driver):
    alice, bob, eve = make_user("alice", "Alice A"), make_user("bob", "Bob B"), make_user("eve", "Eve E")
    dm = api.open_direct(alice, bob)
    api.send_message(alice, dm["id"], "just between us")

    chat = ChatPage(driver).open_as(eve)

    names = chat.conversation_names()
    assert "Alice A" not in names and "Bob B" not in names
