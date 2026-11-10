"""Sign-up, login and logout through the real UI."""

import pytest

from framework.api_client import unique
from pages.chat_page import TOKEN_KEY, ChatPage
from pages.login_page import LoginPage


@pytest.mark.smoke
def test_login_with_valid_credentials_opens_chat(login_page: LoginPage, make_user):
    user = make_user("login", "Login Tester")

    login_page.login_as(user)

    chat = ChatPage(login_page.driver).wait_until_loaded()
    assert "Login Tester" in chat.current_user_text()
    assert f"@{user.username}" in chat.current_user_text()


def test_login_with_wrong_password_shows_error(login_page: LoginPage, make_user):
    user = make_user("login")

    login_page.login(user.username, "definitely-wrong")

    assert login_page.error_message() == "Invalid username or password"
    assert login_page.is_displayed()
    assert login_page.local_storage(TOKEN_KEY) is None


def test_login_with_unknown_user_shows_same_error(login_page: LoginPage):
    # Same message as a wrong password, so the form doesn't reveal which usernames exist.
    login_page.login(unique("ghost"), "password123")
    assert login_page.error_message() == "Invalid username or password"


@pytest.mark.smoke
def test_sign_up_creates_account_and_joins_general(login_page: LoginPage):
    username = unique("signup")

    login_page.sign_up(username, "password123", display_name="New Person")

    chat = ChatPage(login_page.driver).wait_until_loaded()
    assert "New Person" in chat.current_user_text()
    assert "general" in chat.conversation_names()


def test_sign_up_rejects_taken_username(login_page: LoginPage, make_user):
    existing = make_user("taken")

    login_page.sign_up(existing.username, "password123")

    assert login_page.error_message() == "Username is already taken"


@pytest.mark.parametrize(
    "username, password, expected_error",
    [
        ("ab", "password123", "username"),  # too short
        ("has spaces", "password123", "username"),  # invalid characters
        ("valid_name_x", "short", "password"),  # password under 8 characters
    ],
    ids=["short-username", "invalid-username", "short-password"],
)
def test_sign_up_validation(login_page: LoginPage, username, password, expected_error):
    login_page.sign_up(username, password)

    assert expected_error in login_page.error_message().lower()
    assert login_page.is_displayed()


def test_session_survives_page_reload(signed_in):
    chat, user = signed_in

    chat.driver.refresh()

    assert user.username in ChatPage(chat.driver).wait_until_loaded().current_user_text()


def test_sign_out_clears_session(signed_in):
    chat, _ = signed_in

    chat.sign_out()

    login = LoginPage(chat.driver)
    assert login.visible(LoginPage.SCREEN)
    assert login.local_storage(TOKEN_KEY) is None
    chat.driver.refresh()
    assert LoginPage(chat.driver).visible(LoginPage.SCREEN), "a reload after sign-out must not restore the session"
