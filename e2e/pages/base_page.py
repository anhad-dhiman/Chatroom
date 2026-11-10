"""
Base class for all page objects.

The Page Object Model keeps *how* to find things on a page (locators) in one place, so tests read
like user stories ("log in", "send a message") and a UI change means editing one page class rather
than every test.
"""

from __future__ import annotations

from selenium.common.exceptions import StaleElementReferenceException
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webdriver import WebDriver
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from framework import config

Locator = tuple[str, str]


def by_test_id(value: str) -> Locator:
    """Locator for a `data-testid` attribute: stable hooks that don't break when CSS classes or copy change."""
    return By.CSS_SELECTOR, f'[data-testid="{value}"]'


class BasePage:
    def __init__(self, driver: WebDriver):
        self.driver = driver
        # Explicit wait: poll until a condition is true (or time out). Never use time.sleep(),
        # which is either too short (flaky) or too long (slow).
        #
        # StaleElementReferenceException is retried too: React replaces or removes DOM nodes when state
        # changes (e.g. the last reaction chip disappears), so an element found a moment ago can vanish
        # before we read it. That just means "not settled yet, poll again", not a failure.
        self.wait = WebDriverWait(driver, config.TIMEOUT, ignored_exceptions=(StaleElementReferenceException,))

    def visit(self, path: str = "/") -> None:
        self.driver.get(config.BASE_URL + path)

    def find(self, locator: Locator) -> WebElement:
        return self.wait.until(EC.presence_of_element_located(locator))

    def visible(self, locator: Locator) -> WebElement:
        return self.wait.until(EC.visibility_of_element_located(locator))

    def click(self, locator: Locator) -> None:
        self.wait.until(EC.element_to_be_clickable(locator)).click()

    def type(self, locator: Locator, text: str) -> None:
        field = self.visible(locator)
        field.clear()
        field.send_keys(text)

    def is_present(self, locator: Locator) -> bool:
        return len(self.driver.find_elements(*locator)) > 0

    def wait_until_gone(self, locator: Locator) -> None:
        self.wait.until(EC.invisibility_of_element_located(locator))

    def local_storage(self, key: str) -> str | None:
        return self.driver.execute_script("return window.localStorage.getItem(arguments[0]);", key)
