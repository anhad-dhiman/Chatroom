"""Creates WebDriver instances.

Selenium Manager (built into Selenium 4.6+) downloads the matching driver binary automatically.
"""

from selenium import webdriver
from selenium.webdriver.remote.webdriver import WebDriver

from framework import config


def create_driver() -> WebDriver:
    if config.BROWSER == "firefox":
        options = webdriver.FirefoxOptions()
        if config.HEADLESS:
            options.add_argument("-headless")
        driver = webdriver.Firefox(options=options)
        driver.set_window_size(1400, 900)
        return driver

    if config.BROWSER != "chrome":
        raise ValueError(f"Unsupported BROWSER={config.BROWSER!r} (use chrome or firefox)")

    options = webdriver.ChromeOptions()
    if config.HEADLESS:
        options.add_argument("--headless=new")
    options.add_argument("--window-size=1400,900")
    # Needed in containers / CI runners:
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    # Collect browser console errors so tests can assert the page logged none.
    options.set_capability("goog:loggingPrefs", {"browser": "SEVERE"})
    return webdriver.Chrome(options=options)
