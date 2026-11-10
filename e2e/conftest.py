"""
Shared pytest fixtures.

Fixtures are pytest's way of doing setup/teardown: a test lists what it needs as parameters
(e.g. `driver`, `make_user`) and pytest builds those objects before the test and cleans them up after,
even if the test fails.
"""

from __future__ import annotations

import os
import re
from collections.abc import Callable, Iterator

import pytest
from selenium.webdriver.remote.webdriver import WebDriver

from framework import config
from framework.api_client import ApiClient, TestUser
from framework.driver_factory import create_driver
from pages.chat_page import ChatPage
from pages.login_page import LoginPage

SCREENSHOT_DIR = os.path.join(config.REPORTS_DIR, "screenshots")


# ---------------------------------------------------------------------- session-wide


@pytest.fixture(scope="session")
def api() -> ApiClient:
    client = ApiClient()
    if not client.health():
        pytest.exit(
            f"Chatroom is not reachable at {config.BASE_URL}. Start it first (see e2e/README.md).",
            returncode=2,
        )
    return client


@pytest.fixture
def make_user(api: ApiClient) -> Callable[..., TestUser]:
    """Factory fixture: `alice = make_user("alice", "Alice Smith")` registers a fresh user through the API."""
    return lambda prefix="user", display_name=None: api.register(prefix, display_name)


# ---------------------------------------------------------------------- browsers


@pytest.fixture
def browsers(request: pytest.FixtureRequest) -> Iterator[Callable[[], WebDriver]]:
    """Factory for browser sessions. Every browser it opens is screenshotted on failure and closed afterwards."""
    opened: list[WebDriver] = []

    def open_browser() -> WebDriver:
        driver = create_driver()
        opened.append(driver)
        return driver

    yield open_browser

    report = getattr(request.node, "rep_call", None)
    for index, driver in enumerate(opened):
        try:
            if report is not None and report.failed:
                _save_screenshot(request, driver, index)
        finally:
            driver.quit()


@pytest.fixture
def driver(browsers: Callable[[], WebDriver]) -> WebDriver:
    """A single browser, for tests that only need one."""
    return browsers()


@pytest.fixture
def login_page(driver: WebDriver) -> LoginPage:
    return LoginPage(driver).open()


@pytest.fixture
def signed_in(driver: WebDriver, make_user) -> tuple[ChatPage, TestUser]:
    """A browser already signed in as a fresh user (token injected, skipping the login form for speed)."""
    user = make_user("member")
    return ChatPage(driver).open_as(user), user


# ---------------------------------------------------------------------- reporting hooks


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item: pytest.Item, call):
    """Stores each phase's result on the test item so fixtures can tell whether the test failed."""
    outcome = yield
    report = outcome.get_result()
    setattr(item, f"rep_{report.when}", report)
    # Screenshots are taken during fixture teardown; attach them to the teardown report for pytest-html.
    if report.when == "teardown" and getattr(item, "screenshot_extras", None):
        report.extras = getattr(report, "extras", []) + item.screenshot_extras


def _save_screenshot(request: pytest.FixtureRequest, driver: WebDriver, index: int) -> None:
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)
    name = re.sub(r"[^A-Za-z0-9_.-]+", "_", request.node.nodeid)[-150:]
    path = os.path.join(SCREENSHOT_DIR, f"{name}_browser{index + 1}.png")
    driver.save_screenshot(path)

    # Embed the screenshot in the pytest-html report, if that plugin is installed.
    try:
        import pytest_html
    except ImportError:
        return
    extras = getattr(request.node, "screenshot_extras", [])
    extras.append(pytest_html.extras.png(driver.get_screenshot_as_base64(), f"browser {index + 1}"))
    request.node.screenshot_extras = extras


def pytest_html_report_title(report):
    report.title = "Chatroom end-to-end test report"
