from __future__ import annotations

from selenium.webdriver.common.by import By

from framework.api_client import TestUser
from pages.base_page import BasePage, by_test_id


class LoginPage(BasePage):
    SCREEN = by_test_id("auth-screen")
    HEADING = (By.CSS_SELECTOR, ".auth-card h1")
    USERNAME = (By.NAME, "username")
    DISPLAY_NAME = (By.NAME, "displayName")
    PASSWORD = (By.NAME, "password")
    SUBMIT = (By.CSS_SELECTOR, ".auth-card button[type='submit']")
    SWITCH_MODE = (By.CSS_SELECTOR, ".auth-switch button")
    ERROR = (By.CSS_SELECTOR, ".auth-card [role='alert']")

    def open(self) -> "LoginPage":
        self.visit("/")
        self.visible(self.SCREEN)
        return self

    def is_displayed(self) -> bool:
        return self.is_present(self.SCREEN)

    def heading(self) -> str:
        return self.visible(self.HEADING).text

    def login(self, username: str, password: str) -> None:
        self.type(self.USERNAME, username)
        self.type(self.PASSWORD, password)
        self.click(self.SUBMIT)

    def login_as(self, user: TestUser) -> None:
        self.login(user.username, user.password)

    def switch_to_sign_up(self) -> "LoginPage":
        if "Create" not in self.heading():
            self.click(self.SWITCH_MODE)
            self.visible(self.DISPLAY_NAME)
        return self

    def sign_up(self, username: str, password: str, display_name: str = "") -> None:
        self.switch_to_sign_up()
        self.type(self.USERNAME, username)
        if display_name:
            self.type(self.DISPLAY_NAME, display_name)
        self.type(self.PASSWORD, password)
        self.click(self.SUBMIT)

    def error_message(self) -> str:
        return self.visible(self.ERROR).text
