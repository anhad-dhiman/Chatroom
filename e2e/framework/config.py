"""Test configuration, read from environment variables so the same suite runs locally and in CI."""

import os


def _flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


BASE_URL = os.getenv("BASE_URL", "http://localhost:8080").rstrip("/")
BROWSER = os.getenv("BROWSER", "chrome").lower()  # chrome | firefox
HEADLESS = _flag("HEADLESS", True)
# Upper bound for explicit waits. Waits return as soon as the condition is met, so this only
# matters when something is actually broken.
TIMEOUT = float(os.getenv("E2E_TIMEOUT", "10"))
DEFAULT_PASSWORD = "password123"
REPORTS_DIR = os.getenv("REPORTS_DIR", os.path.join(os.path.dirname(__file__), "..", "..", "reports"))
