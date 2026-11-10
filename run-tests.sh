#!/usr/bin/env bash
#
# run-tests.sh: one entry point for every test layer in Chatroom.
#
# Used the same way on a laptop and in CI, so "works on my machine" and "works in CI"
# mean the same thing.
#
#   ./run-tests.sh                 backend + frontend (the fast suites)
#   ./run-tests.sh --all           lint + backend + frontend + end-to-end
#   ./run-tests.sh --e2e           build the app, start it, run Selenium tests, stop it
#   ./run-tests.sh --e2e --smoke   only end-to-end tests marked @smoke
#   ./run-tests.sh --e2e -- -k login   pass extra arguments to pytest
#   ./run-tests.sh --help
#
# Reports (JUnit XML, HTML, screenshots, server log) are written to ./reports/.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORTS_DIR="${REPORTS_DIR:-$ROOT_DIR/reports}"
E2E_PORT="${E2E_PORT:-8080}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

RUN_BACKEND=false
RUN_FRONTEND=false
RUN_E2E=false
RUN_LINT=false
E2E_MARKER=""
SKIP_BUILD=false
SERVER_PID=""
declare -a PYTEST_EXTRA=()

declare -a SUITE_NAMES=()
declare -a SUITE_RESULTS=()
declare -a SUITE_TIMES=()

# ---------------------------------------------------------------- output helpers

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  BOLD=$'\e[1m' RED=$'\e[31m' GREEN=$'\e[32m' YELLOW=$'\e[33m' BLUE=$'\e[34m' RESET=$'\e[0m'
else
  BOLD="" RED="" GREEN="" YELLOW="" BLUE="" RESET=""
fi

info()    { printf '%s==>%s %s\n' "$BLUE$BOLD" "$RESET" "$*"; }
warn()    { printf '%sWARN%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
die()     { printf '%sERROR%s %s\n' "$RED$BOLD" "$RESET" "$*" >&2; exit 2; }

usage() {
  sed -n '3,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<'EOF'
Options:
  --backend       Java unit + integration tests (Maven)
  --frontend      TypeScript type check + React unit tests (Vitest)
  --e2e           Python + Selenium end-to-end tests against a freshly started app
  --lint          Static checks: shellcheck and ruff (if installed)
  --all           Everything above
  --smoke         With --e2e: only run tests marked @smoke
  --skip-build    With --e2e: reuse an existing build in backend/target
  -h, --help      Show this help

Environment:
  BASE_URL        Run --e2e against an already running server instead of starting one
  BROWSER         chrome (default) or firefox
  HEADLESS        true (default) or false to watch the browser
  E2E_PORT        Port for the app started by --e2e (default 8080)
EOF
}

# ---------------------------------------------------------------- argument parsing

parse_args() {
  if [[ $# -eq 0 ]]; then
    RUN_BACKEND=true RUN_FRONTEND=true
    return
  fi
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --backend)    RUN_BACKEND=true ;;
      --frontend)   RUN_FRONTEND=true ;;
      --e2e)        RUN_E2E=true ;;
      --lint)       RUN_LINT=true ;;
      --all)        RUN_BACKEND=true RUN_FRONTEND=true RUN_E2E=true RUN_LINT=true ;;
      --smoke)      E2E_MARKER="smoke" ;;
      --skip-build) SKIP_BUILD=true ;;
      -h|--help)    usage; exit 0 ;;
      --)           shift; PYTEST_EXTRA=("$@"); break ;;
      *)            usage; die "Unknown option: $1" ;;
    esac
    shift
  done
}

require() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || die "'$cmd' is required but not installed."
  done
}

# Python is "python3" on macOS/Linux but often just "python" on Windows (Git Bash).
find_python() {
  local candidate
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys; sys.exit(sys.version_info < (3, 10))' 2>/dev/null; then
      echo "$candidate"
      return
    fi
  done
  die "Python 3.10+ is required for the end-to-end tests."
}

# ---------------------------------------------------------------- suite runner

# run_suite "Name" command args...: runs one suite, records pass/fail and duration, never aborts the script.
run_suite() {
  local name="$1"; shift
  local start end status
  info "Running: $name"
  start=$(date +%s)
  # Each suite runs in its own subshell with "set -e", so the first failing command fails the suite
  # (and only that suite). run_suite must NOT be called from an "&&"/"||" list or an "if" condition:
  # bash silently disables "set -e" inside those contexts.
  set +e
  ( set -e; "$@" )
  status=$?
  set -e
  end=$(date +%s)
  SUITE_NAMES+=("$name")
  SUITE_RESULTS+=("$status")
  SUITE_TIMES+=("$((end - start))")
  if [[ $status -eq 0 ]]; then
    info "${GREEN}PASSED${RESET}: $name ($((end - start))s)"
  else
    printf '%sFAILED%s: %s (exit %s)\n' "$RED$BOLD" "$RESET" "$name" "$status" >&2
  fi
}

# ---------------------------------------------------------------- suites

backend_tests() {
  require java
  cd "$ROOT_DIR/backend"
  # "bash mvnw" works even if the executable bit was lost (e.g. committed from Windows).
  bash mvnw -B -ntp test
  mkdir -p "$REPORTS_DIR/backend"
  cp target/surefire-reports/*.xml "$REPORTS_DIR/backend/" 2>/dev/null || true
}

frontend_tests() {
  require node npm
  cd "$ROOT_DIR/frontend"
  [[ -d node_modules ]] || npm ci --no-audit --no-fund
  npm run typecheck
  mkdir -p "$REPORTS_DIR/frontend"
  npx vitest run --reporter=default --reporter=junit --outputFile.junit="$REPORTS_DIR/frontend/junit.xml"
}

lint_checks() {
  local failed=0
  if command -v shellcheck >/dev/null 2>&1; then
    shellcheck "$ROOT_DIR/run-tests.sh" || failed=1
  else
    warn "shellcheck not installed; skipping shell lint"
  fi
  local ruff=""
  if command -v ruff >/dev/null 2>&1; then
    ruff=ruff
  elif [[ -x "$ROOT_DIR/e2e/.venv/bin/ruff" ]]; then
    ruff="$ROOT_DIR/e2e/.venv/bin/ruff"
  fi
  if [[ -n "$ruff" ]]; then
    (cd "$ROOT_DIR/e2e" && "$ruff" check . && "$ruff" format --check .) || failed=1
  else
    warn "ruff not installed; skipping Python lint (pip install ruff)"
  fi
  return "$failed"
}

build_app() {
  info "Building frontend (Vite) and backend (Maven)"
  cd "$ROOT_DIR/frontend"
  [[ -d node_modules ]] || npm ci --no-audit --no-fund
  npm run build
  cd "$ROOT_DIR/backend"
  bash mvnw -B -ntp -q package -DskipTests
}

start_app() {
  local jar
  jar=$(find "$ROOT_DIR/backend/target" -maxdepth 1 -name 'chatroom-*.jar' ! -name '*.original' | head -n 1)
  [[ -n "$jar" ]] || die "No jar found in backend/target. Run without --skip-build."
  mkdir -p "$REPORTS_DIR"
  info "Starting app on port $E2E_PORT (in-memory H2 database)"
  # Serve the freshly built React app straight from frontend/dist (production bundles it inside the jar).
  java -jar "$jar" \
    --spring.profiles.active=h2 \
    --server.port="$E2E_PORT" \
    --spring.web.resources.static-locations="file:$ROOT_DIR/frontend/dist/" \
    >"$REPORTS_DIR/server.log" 2>&1 &
  SERVER_PID=$!
  # This function runs inside run_suite's subshell, so stop the server when that subshell exits.
  trap stop_app EXIT
}

wait_for_health() {
  local url="$1/actuator/health" waited=0
  info "Waiting for $url"
  until curl -fsS "$url" >/dev/null 2>&1; do
    if [[ -n "$SERVER_PID" ]] && ! kill -0 "$SERVER_PID" 2>/dev/null; then
      tail -n 40 "$REPORTS_DIR/server.log" >&2 || true
      die "The app exited during startup (log above, full log in reports/server.log)."
    fi
    if (( waited >= HEALTH_TIMEOUT )); then
      die "The app did not become healthy within ${HEALTH_TIMEOUT}s."
    fi
    sleep 2
    waited=$((waited + 2))
  done
  info "App is healthy after ${waited}s"
}

stop_app() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    info "Stopping app (pid $SERVER_PID)"
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  SERVER_PID=""
}

e2e_tests() {
  require java curl
  local python venv venv_python base_url
  python=$(find_python)

  if [[ -n "${BASE_URL:-}" ]]; then
    base_url="$BASE_URL"
    info "Using already running server at $base_url"
  else
    base_url="http://localhost:$E2E_PORT"
    [[ "$SKIP_BUILD" == true ]] || build_app
    start_app
  fi
  wait_for_health "$base_url"

  venv="$ROOT_DIR/e2e/.venv"
  if [[ ! -d "$venv" ]]; then
    info "Creating Python virtual environment in e2e/.venv"
    "$python" -m venv "$venv"
  fi
  # The venv layout differs between Windows (Scripts/) and macOS/Linux (bin/).
  if [[ -x "$venv/bin/python" ]]; then venv_python="$venv/bin/python"; else venv_python="$venv/Scripts/python"; fi
  "$venv_python" -m pip install -q --disable-pip-version-check -r "$ROOT_DIR/e2e/requirements.txt"

  local -a pytest_args=(
    --junitxml="$REPORTS_DIR/e2e/junit.xml"
    --html="$REPORTS_DIR/e2e/report.html" --self-contained-html
  )
  if [[ -n "$E2E_MARKER" ]]; then pytest_args+=(-m "$E2E_MARKER"); fi
  if [[ ${#PYTEST_EXTRA[@]} -gt 0 ]]; then pytest_args+=("${PYTEST_EXTRA[@]}"); fi

  mkdir -p "$REPORTS_DIR/e2e"
  (cd "$ROOT_DIR/e2e" && BASE_URL="$base_url" REPORTS_DIR="$REPORTS_DIR/e2e" "$venv_python" -m pytest "${pytest_args[@]}")
}

# ---------------------------------------------------------------- summary

print_summary() {
  local i overall=0 line color
  echo
  printf '%s%-28s %-8s %s%s\n' "$BOLD" "Suite" "Result" "Time" "$RESET"
  for i in "${!SUITE_NAMES[@]}"; do
    if [[ "${SUITE_RESULTS[$i]}" -eq 0 ]]; then
      line="PASS" color="$GREEN"
    else
      line="FAIL" color="$RED"
      overall=1
    fi
    printf '%-28s %s%-8s%s %ss\n' "${SUITE_NAMES[$i]}" "$color" "$line" "$RESET" "${SUITE_TIMES[$i]}"
  done
  echo "Reports: $REPORTS_DIR"

  # In GitHub Actions, also write the table to the job summary page.
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "| Suite | Result | Time |"
      echo "|---|---|---|"
      for i in "${!SUITE_NAMES[@]}"; do
        if [[ "${SUITE_RESULTS[$i]}" -eq 0 ]]; then line="✅ pass"; else line="❌ fail"; fi
        echo "| ${SUITE_NAMES[$i]} | $line | ${SUITE_TIMES[$i]}s |"
      done
    } >>"$GITHUB_STEP_SUMMARY"
  fi
  return "$overall"
}

main() {
  parse_args "$@"
  trap 'exit 130' INT TERM
  mkdir -p "$REPORTS_DIR"

  if [[ "$RUN_LINT" == true ]]; then run_suite "Lint (shellcheck, ruff)" lint_checks; fi
  if [[ "$RUN_BACKEND" == true ]]; then run_suite "Backend (JUnit)" backend_tests; fi
  if [[ "$RUN_FRONTEND" == true ]]; then run_suite "Frontend (Vitest)" frontend_tests; fi
  if [[ "$RUN_E2E" == true ]]; then run_suite "End-to-end (Selenium)" e2e_tests; fi

  print_summary
}

main "$@"
