"""
Thin client for the Chatroom REST API.

Tests use it to *arrange* data (create users, rooms, messages) quickly and reliably, then use the
browser only for the behaviour under test. This keeps UI tests fast and focused, and means a
failure points at the feature being tested rather than at some unrelated setup step.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

import requests

from framework import config


@dataclass(frozen=True)
class TestUser:
    __test__ = False  # tell pytest this is not a test class

    id: int
    username: str
    display_name: str
    password: str
    token: str


def unique(prefix: str) -> str:
    """Unique, valid username/room name so tests never collide, even when re-run against the same database."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}"


class ApiClient:
    def __init__(self, base_url: str = config.BASE_URL):
        self.base_url = base_url
        self.session = requests.Session()

    # ------------------------------------------------------------------ raw requests

    def request(self, method: str, path: str, token: str | None = None, json: dict | None = None) -> requests.Response:
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        return self.session.request(method, self.base_url + path, json=json, headers=headers, timeout=10)

    def _ok(self, method: str, path: str, token: str | None = None, json: dict | None = None):
        response = self.request(method, path, token, json)
        if not response.ok:
            raise AssertionError(f"{method} {path} failed: {response.status_code} {response.text}")
        return response.json() if response.content else None

    # ------------------------------------------------------------------ domain helpers

    def health(self) -> bool:
        try:
            return self.request("GET", "/actuator/health").ok
        except requests.RequestException:
            return False

    def register(self, prefix: str = "user", display_name: str | None = None) -> TestUser:
        username = unique(prefix)
        display = display_name or username
        body = self._ok(
            "POST",
            "/api/auth/register",
            json={
                "username": username,
                "password": config.DEFAULT_PASSWORD,
                "displayName": display,
            },
        )
        return TestUser(body["user"]["id"], username, display, config.DEFAULT_PASSWORD, body["token"])

    def create_room(self, user: TestUser, name: str, description: str = "") -> dict:
        return self._ok("POST", "/api/rooms", user.token, {"name": name, "description": description})

    def join_room(self, user: TestUser, room_id: int) -> dict:
        return self._ok("POST", f"/api/rooms/{room_id}/join", user.token)

    def open_direct(self, user: TestUser, other: TestUser) -> dict:
        return self._ok("POST", f"/api/direct/{other.id}", user.token)

    def send_message(self, user: TestUser, conversation_id: int, content: str) -> dict:
        return self._ok("POST", f"/api/conversations/{conversation_id}/messages", user.token, {"content": content})

    def conversations(self, user: TestUser) -> list[dict]:
        return self._ok("GET", "/api/conversations", user.token)

    def general_room_id(self, user: TestUser) -> int:
        return next(c["id"] for c in self.conversations(user) if c["name"] == "general")
