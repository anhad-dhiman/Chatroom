from __future__ import annotations

from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as EC

from framework.api_client import TestUser
from pages.base_page import BasePage, by_test_id
from pages.modals import DirectMessageModal, RoomsModal

TOKEN_KEY = "chatroom.token"


def _xpath_literal(text: str) -> str:
    """Quote text for XPath, including text that contains quotes."""
    if "'" not in text:
        return f"'{text}'"
    if '"' not in text:
        return f'"{text}"'
    return "concat('" + text.replace("'", "', \"'\", '") + "')"


class ChatPage(BasePage):
    CURRENT_USER = by_test_id("current-user")
    CONVERSATION_ROW = by_test_id("conversation-row")
    CONVERSATION_TITLE = by_test_id("conversation-title")
    CHAT_TITLE = by_test_id("chat-title")
    MESSAGE = by_test_id("message")
    COMPOSER = (By.CSS_SELECTOR, "textarea[aria-label='Message']")
    BROWSE_ROOMS = (By.CSS_SELECTOR, "button[aria-label='Browse or create rooms']")
    NEW_DIRECT = (By.CSS_SELECTOR, "button[aria-label='New direct message']")
    SIGN_OUT = (By.CSS_SELECTOR, "button[aria-label='Sign out']")
    THEME_TOGGLE = (By.CSS_SELECTOR, ".sidebar button[aria-label='Toggle dark mode']")
    TOAST = by_test_id("toast")

    # ------------------------------------------------------------------ navigation

    def open_as(self, user: TestUser) -> "ChatPage":
        """Signs in by putting the user's token in localStorage, exactly where the app keeps it after a real login.
        Much faster than typing into the form, so it's used by every test that isn't about logging in."""
        self.visit("/")
        self.driver.execute_script("window.localStorage.setItem(arguments[0], arguments[1]);", TOKEN_KEY, user.token)
        self.driver.refresh()
        return self.wait_until_loaded()

    def wait_until_loaded(self) -> "ChatPage":
        self.visible(self.CURRENT_USER)
        self.visible(self.CONVERSATION_ROW)
        return self

    def current_user_text(self) -> str:
        return self.visible(self.CURRENT_USER).text

    def sign_out(self) -> None:
        self.click(self.SIGN_OUT)

    def toggle_theme(self) -> None:
        self.click(self.THEME_TOGGLE)

    def theme(self) -> str | None:
        return self.driver.execute_script("return document.documentElement.dataset.theme;")

    # ------------------------------------------------------------------ sidebar

    def conversation_names(self) -> list[str]:
        self.visible(self.CONVERSATION_ROW)
        return [el.text for el in self.driver.find_elements(*self.CONVERSATION_TITLE)]

    def conversation_row(self, name: str) -> WebElement:
        xpath = (
            f"//*[@data-testid='conversation-row'][.//*[@data-testid='conversation-title']"
            f"[normalize-space()={_xpath_literal(name)}]]"
        )
        return self.visible((By.XPATH, xpath))

    def open_conversation(self, name: str) -> "ChatPage":
        self.conversation_row(name).click()
        self.wait.until(EC.text_to_be_present_in_element(self.CHAT_TITLE, name))
        return self

    def active_conversation(self) -> str:
        return self.visible(self.CHAT_TITLE).text

    def unread_count(self, name: str) -> int:
        badges = self.conversation_row(name).find_elements(*by_test_id("unread-badge"))
        return int(badges[0].text.rstrip("+")) if badges else 0

    def wait_for_unread(self, name: str, expected: int) -> None:
        self.wait.until(lambda _: self.unread_count(name) == expected, f"expected {expected} unread in {name!r}")

    def browse_rooms(self) -> RoomsModal:
        self.click(self.BROWSE_ROOMS)
        return RoomsModal(self.driver).wait_open()

    def new_direct_message(self) -> DirectMessageModal:
        self.click(self.NEW_DIRECT)
        return DirectMessageModal(self.driver).wait_open()

    # ------------------------------------------------------------------ messages

    def send_message(self, text: str) -> WebElement:
        composer = self.visible(self.COMPOSER)
        composer.send_keys(text)
        composer.send_keys(Keys.ENTER)
        return self.wait_for_message(text)

    def message(self, text: str) -> tuple[str, str]:
        """Locator for the message whose body contains `text`."""
        return (
            By.XPATH,
            f"//*[@data-testid='message'][.//*[@data-testid='message-body']"
            f"[contains(normalize-space(), {_xpath_literal(text)})]]",
        )

    def wait_for_message(self, text: str) -> WebElement:
        return self.visible(self.message(text))

    def message_texts(self) -> list[str]:
        return [el.text for el in self.driver.find_elements(*by_test_id("message-body"))]

    def _hover(self, element: WebElement) -> None:
        # The action toolbar is hidden until the mouse is over a message, like a real user would do.
        ActionChains(self.driver).move_to_element(element).perform()

    def message_actions(self, text: str) -> list[str]:
        element = self.wait_for_message(text)
        self._hover(element)
        buttons = element.find_elements(By.CSS_SELECTOR, "[role='toolbar'] button")
        return [b.get_attribute("aria-label") for b in buttons if b.is_displayed()]

    def edit_message(self, text: str, new_text: str) -> WebElement:
        element = self.wait_for_message(text)
        self._hover(element)
        element.find_element(By.CSS_SELECTOR, "button[aria-label='Edit message']").click()
        box = self.visible((By.CSS_SELECTOR, "textarea[aria-label='Edit message']"))
        box.send_keys(Keys.CONTROL, "a")
        box.send_keys(Keys.DELETE)
        box.send_keys(new_text, Keys.ENTER)
        return self.wait_for_message(new_text)

    def delete_message(self, text: str) -> None:
        element = self.wait_for_message(text)
        message_id = element.get_attribute("data-message-id")
        self._hover(element)
        element.find_element(By.CSS_SELECTOR, "button[aria-label='Delete message']").click()
        # Second click confirms: the app asks before deleting.
        confirm = (By.XPATH, f"//*[@data-message-id='{message_id}']//button[normalize-space()='Delete']")
        self.click(confirm)
        self.wait_for_deleted(message_id)

    def wait_for_deleted(self, message_id: str) -> None:
        self.visible((By.CSS_SELECTOR, f"[data-message-id='{message_id}'] [data-testid='message-deleted']"))

    def react(self, text: str, emoji: str) -> None:
        element = self.wait_for_message(text)
        self._hover(element)
        element.find_element(By.CSS_SELECTOR, "button[aria-label='Add reaction']").click()
        self.click((By.CSS_SELECTOR, f"button[aria-label='React with {emoji}']"))

    def reactions(self, text: str) -> dict[str, int]:
        """{emoji: count} for one message, e.g. {'🎉': 2}."""
        element = self.wait_for_message(text)
        result = {}
        for chip in element.find_elements(*by_test_id("reaction")):
            emoji, count = chip.find_elements(By.TAG_NAME, "span")
            result[emoji.text] = int(count.text)
        return result

    def wait_for_reactions(self, text: str, expected: dict[str, int]) -> None:
        self.wait.until(lambda _: self.reactions(text) == expected, f"expected reactions {expected}")

    def is_edited(self, text: str) -> bool:
        return bool(self.wait_for_message(text).find_elements(*by_test_id("message-edited")))

    def wait_for_edited(self, text: str) -> None:
        self.wait.until(lambda _: self.is_edited(text), "expected the message to show '(edited)'")
