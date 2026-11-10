# End-to-end tests (Python + Selenium)

These tests drive a **real browser** against a **running Chatroom server**, exactly like a user would:
type into the login form, click buttons, wait for messages to appear. They catch problems that unit
tests can't, such as a broken API contract between the frontend and backend, a WebSocket that never
delivers, or a button that's hidden by CSS.

They are *black-box* tests: they know nothing about the Java or React code, only the URL, what's on the
screen and the public REST API.

## Running them

The easiest way is the repo's test script, which builds the app, starts it, runs the suite and stops it:

```bash
./run-tests.sh --e2e                      # everything
./run-tests.sh --e2e --smoke              # just the critical-path tests
./run-tests.sh --e2e -- -k login          # only tests with "login" in the name
HEADLESS=false ./run-tests.sh --e2e       # watch the browser while the tests run
BROWSER=firefox ./run-tests.sh --e2e      # run in Firefox instead of Chrome
```

To run against a server you already started (for example `docker compose up`):

```bash
cd e2e
python -m venv .venv
source .venv/bin/activate        # Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
BASE_URL=http://localhost:8080 pytest
```

Requirements: Python 3.10+ and Google Chrome (or Firefox). You don't need to download a driver:
Selenium Manager (built into Selenium 4.6+) fetches the right `chromedriver` automatically.

After a run, open **`reports/e2e/report.html`**: every test with its result and duration, plus a
screenshot of the browser for any test that failed.

## How the suite is organised

```
e2e/
├── conftest.py              pytest fixtures: browsers, test users, screenshot-on-failure
├── framework/
│   ├── config.py            settings from environment variables (BASE_URL, BROWSER, HEADLESS…)
│   ├── driver_factory.py    creates Chrome/Firefox WebDriver sessions
│   └── api_client.py        REST client used to set up test data quickly
├── pages/                   Page Objects: one class per screen
│   ├── base_page.py         shared helpers: explicit waits, click, type
│   ├── login_page.py
│   ├── chat_page.py
│   └── modals.py            "Browse rooms" and "New direct message" dialogs
└── tests/
    ├── test_auth.py              sign up, login, logout, validation
    ├── test_protected_access.py  logged-out access, forged tokens, API authorization
    ├── test_search.py            room search, people search, joining, duplicates
    ├── test_messaging.py         send, multi-line, edit, delete, reactions, dark mode
    └── test_realtime.py          two browsers at once: live delivery, live edits, unread badges
```

## Key concepts (the things worth understanding)

**WebDriver.** Selenium's API for remote-controlling a browser. `driver.get(url)` opens a page,
`driver.find_element(...)` finds an element, `.click()` and `.send_keys()` interact with it. Each test gets a
fresh browser, so tests can't affect each other.

**Locators.** How a test finds an element. In order of preference, this suite uses:
1. `data-testid` attributes (`[data-testid="message"]`). These exist only for tests, so restyling or
   rewording the UI doesn't break them.
2. Accessibility attributes (`aria-label="Send message"`, `role="dialog"`). These are stable, and
   relying on them gives a nudge to keep the app's labels meaningful for screen-reader users.
3. `name` attributes on form fields.

CSS classes and XPath based on page structure are avoided where possible because they change often.

**Explicit waits.** The app is asynchronous: after clicking "Send", the message appears a few
milliseconds later, once the server responds. `WebDriverWait(driver, 10).until(condition)` polls until
the condition is true, returning immediately when it is. The suite never uses `time.sleep()`: a fixed
sleep is either too short (the test fails randomly, which is called a *flaky* test) or too long (the
suite is slow).

**Page Object Model (POM).** Every screen has a class (`LoginPage`, `ChatPage`) that knows its locators and
offers user-level actions (`login_as(user)`, `send_message(text)`). Tests read like a user story, and when
the UI changes only the page class needs updating, not every test.

**Fixtures (pytest).** Reusable setup and teardown that tests request by parameter name. For example,
`def test_x(signed_in):` receives a browser already logged in as a brand-new user, and the browser is
closed afterwards even if the test fails.

**Arrange via API, act and assert via UI.** Tests create users, rooms and messages through the REST
API (fast and reliable), then use the browser only for the behaviour under test. A failure therefore
points at the feature being tested, not at an unrelated setup step.

**Test isolation.** Every test creates its own uniquely named users and rooms (`alice_3f9a1c2e`), so
tests can run in any order, and repeatedly against the same database, without interfering.

**Multiple browsers.** Real-time tests open two browser sessions as two different users, then check
that an action in one appears in the other *without a page refresh*. That shows the WebSocket path
works end to end.

**Reporting.** JUnit XML (read by CI to show results per test), a self-contained HTML report, and an
automatic screenshot from every open browser when a test fails.

**Markers.** `@pytest.mark.smoke` tags the handful of critical-path tests (can users log in, send and
receive messages?) so they can run on their own in under a minute, for example right after a deploy.
