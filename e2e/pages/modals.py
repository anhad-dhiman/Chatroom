from __future__ import annotations

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC

from pages.base_page import BasePage, by_test_id


class _Modal(BasePage):
    DIALOG = (By.CSS_SELECTOR, "[role='dialog']")
    CLOSE = (By.CSS_SELECTOR, "[role='dialog'] button[aria-label='Close']")

    def wait_open(self):
        self.visible(self.DIALOG)
        return self

    def close(self) -> None:
        self.click(self.CLOSE)
        self.wait_until_gone(self.DIALOG)


class RoomsModal(_Modal):
    SEARCH = (By.CSS_SELECTOR, "input[aria-label='Search rooms']")
    RESULT = by_test_id("room-result")
    EMPTY = (By.XPATH, "//*[@role='dialog']//li[contains(., 'No rooms match')]")
    NAME = (By.NAME, "roomName")
    DESCRIPTION = (By.NAME, "roomDescription")
    CREATE = (By.CSS_SELECTOR, ".create-room button[type='submit']")
    ERROR = (By.CSS_SELECTOR, "[role='dialog'] [role='alert']")

    def search(self, query: str) -> "RoomsModal":
        self.type(self.SEARCH, query)
        return self

    def result_names(self) -> list[str]:
        return [el.find_element(By.CSS_SELECTOR, ".row-title").text for el in self.driver.find_elements(*self.RESULT)]

    def wait_for_results(self, expected: list[str]) -> list[str]:
        """Search is debounced, so wait for the list to settle on the expected names."""
        self.wait.until(lambda _: sorted(self.result_names()) == sorted(expected), f"expected room results {expected}")
        return self.result_names()

    def wait_for_no_results(self) -> None:
        self.visible(self.EMPTY)

    def open_room(self, name: str) -> None:
        self.click((By.XPATH, f"//*[@data-testid='room-result'][.//*[normalize-space()='{name}']]"))
        self.wait_until_gone(self.DIALOG)

    def create_room(self, name: str, description: str = "") -> None:
        self.type(self.NAME, name)
        if description:
            self.type(self.DESCRIPTION, description)
        self.click(self.CREATE)

    def error_message(self) -> str:
        return self.visible(self.ERROR).text


class DirectMessageModal(_Modal):
    SEARCH = (By.CSS_SELECTOR, "input[aria-label='Search people']")
    RESULT = by_test_id("user-result")

    def search(self, query: str) -> "DirectMessageModal":
        self.type(self.SEARCH, query)
        return self

    def wait_for_user(self, display_name: str) -> None:
        self.wait.until(EC.text_to_be_present_in_element(self.RESULT, display_name))

    def result_names(self) -> list[str]:
        return [el.find_element(By.CSS_SELECTOR, ".row-title").text for el in self.driver.find_elements(*self.RESULT)]

    def open_with(self, display_name: str) -> None:
        self.wait_for_user(display_name)
        self.click((By.XPATH, f"//*[@data-testid='user-result'][.//*[normalize-space()='{display_name}']]"))
        self.wait_until_gone(self.DIALOG)
