#!/usr/bin/env bats

load "helpers/common.bash"

setup_file() {
  if [[ -z "${WIRE_BIN:-}" ]]; then
    WIRE_BIN="${BATS_TEST_DIRNAME}/../../build/install/wire-cli/bin/wire-cli"
  fi
  export WIRE_BIN
}

setup() {
  setup_wire_test_env
}

teardown() {
  teardown_wire_test_env
}

write_session_inventory() {
  local file_path="$1"
  shift
  printf '%s\n' "$@" >"${file_path}"
}

run_wire_with_stdin() {
  local stdin_payload="$1"
  shift
  run "${WIRE_BIN}" "$@" <<<"${stdin_payload}"
}

@test "Given no session, when profile runs, then access is denied" {
  run_wire profile
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given no session, when presence runs, then access is denied" {
  run_wire presence
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given invalid credentials, when login runs, then auth fails and no session is created" {
  export WIRE_STUB_MODE="login_invalid"
  run_wire login --email "jane@example.com" --password "wrong"
  assert_status 10
  [[ "${output}" == *"Invalid email or password"* ]]
  [ ! -f "${WIRE_SESSION_FILE}" ]
}

@test "Given auth network failure, when login runs, then actionable error and non-zero exit are returned" {
  export WIRE_STUB_MODE="login_network_error"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 12
  [[ "${output}" == *"Check your connection and retry"* ]]
  [ ! -f "${WIRE_SESSION_FILE}" ]
}

@test "Given password from stdin, when login runs, then session is persisted without password arg" {
  export WIRE_STUB_MODE="login_ok"
  run_wire_with_stdin "correct-horse" login --email "jane@example.com" --password-stdin
  assert_status 0
  [ "${output}" = "Login successful." ]
  [[ "${output}" != *"deprecated"* ]]
  [ -f "${WIRE_SESSION_FILE}" ]
}

@test "Given incompatible password flags, when login runs, then validation error is returned" {
  export WIRE_STUB_MODE="login_ok"
  run_wire_with_stdin "correct-horse" login --email "jane@example.com" --password "correct-horse" --password-stdin
  assert_status 14
  [[ "${output}" == *"Use either --password or --password-stdin"* ]]
  [ ! -f "${WIRE_SESSION_FILE}" ]
}

@test "Given sensitive auth failure details, when login fails, then secrets are redacted" {
  export WIRE_STUB_MODE="login_secret_failure"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 10
  [[ "${output}" == *"token=<redacted>"* ]]
  [[ "${output}" == *"password=<redacted>"* ]]
  [[ "${output}" != *"abc123"* ]]
  [[ "${output}" != *"super-secret"* ]]
}

@test "Given valid credentials, when login runs, then session is persisted" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [[ "${output}" == *"Login successful."* ]]
  [ -f "${WIRE_SESSION_FILE}" ]
}

@test "Given successful login, when session is saved, then file and directory permissions are restricted" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [ -f "${WIRE_SESSION_FILE}" ]

  session_dir="$(dirname "${WIRE_SESSION_FILE}")"
  [ "$(file_mode_octal "${WIRE_SESSION_FILE}")" = "600" ]
  [ "$(file_mode_octal "${session_dir}")" = "700" ]
}

@test "Given persisted session, when profile runs in new process, then no relogin is needed" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [ -f "${WIRE_SESSION_FILE}" ]

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name: Jane Doe"* ]]
  [[ "${output}" == *"Email: jane@example.com"* ]]
  [[ "${output}" == *"Presence: online"* ]]
  [[ "${output}" != *"Run wire login"* ]]
}

@test "Given multiple persisted valid sessions, when profile runs, then active account selection is deterministic" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "user-zed" "token-zed" "" \
    "user-amy" "token-amy" ""

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Handle: user-amy"* ]]
}

@test "Given mixed valid and invalid persisted sessions, when profile runs, then a valid session is used" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "" "token-invalid" "" \
    "user-bruno" "token-bruno" ""

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Handle: user-bruno"* ]]
}

@test "Given legacy persisted sessions, when profile runs, then inventory is migrated to the versioned schema" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "user-zed" "token-zed" "" \
    "user-amy" "token-amy" ""

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Handle: user-amy"* ]]
  read -r first_line <"${WIRE_SESSION_FILE}"
  [ "${first_line}" = "wire-cli-session-store:1" ]
}

@test "Given unsupported session schema version, when profile runs, then actionable guidance is shown" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "wire-cli-session-store:99" \
    "user-zed" "token-zed" ""

  run_wire profile
  assert_status 11
  [[ "${output}" == *"Stored session format version '99' is unsupported"* ]]
  [[ "${output}" == *"Run wire login to recreate local credentials"* ]]
}

@test "Given missing optional profile fields, when profile runs, then output remains readable" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0

  export WIRE_STUB_MODE="profile_missing_optional"
  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name: Jane Doe"* ]]
  [[ "${output}" == *"Email: jane@example.com"* ]]
  [[ "${output}" == *"Handle: -"* ]]
}

@test "Given upstream network failure, when profile runs, then actionable error and non-zero exit are returned" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0

  export WIRE_STUB_MODE="profile_network_error"
  run_wire profile
  assert_status 12
  [[ "${output}" == *"Check your connection and retry"* ]]
}

@test "Given upstream server failure, when profile runs, then clear error and non-zero exit are returned" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0

  export WIRE_STUB_MODE="profile_server_error"
  run_wire profile
  assert_status 13
  [[ "${output}" == *"Retry later or check server settings"* ]]
}

@test "Given expired or invalid session, when profile runs, then unauthorized guidance and non-zero exit are returned" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0

  export WIRE_STUB_MODE="profile_unauthorized"
  run_wire profile
  assert_status 11
  [[ "${output}" == *"Session is invalid or expired"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
  [[ "${output}" != *"Name:"* ]]
  [[ "${output}" != *"Email:"* ]]
  [[ "${output}" != *"Handle:"* ]]
  [[ "${output}" != *"Presence:"* ]]
}

@test "Given only invalid persisted sessions, when profile runs, then unauthorized diagnostics include recovery guidance" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "" "token-without-user" "" \
    "dangling-record-only-user"

  run_wire profile
  assert_status 11
  [[ "${output}" == *"No valid active session"* ]]
  [[ "${output}" == *"Found 2 invalid or expired stored sessions"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given only invalid persisted sessions, when logout runs, then unauthorized diagnostics include recovery guidance" {
  write_session_inventory "${WIRE_SESSION_FILE}" \
    "" "token-without-user" "" \
    "dangling-record-only-user"

  run_wire logout
  assert_status 11
  [[ "${output}" == *"No valid active session"* ]]
  [[ "${output}" == *"Found 2 invalid or expired stored sessions"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given authenticated user, when login profile logout run in sequence, then flow succeeds end-to-end" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [ -f "${WIRE_SESSION_FILE}" ]

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name: Jane Doe"* ]]

  export WIRE_STUB_MODE="logout_ok"
  run_wire logout
  assert_status 0
  [ "${output}" = "Logged out." ]
  [ ! -f "${WIRE_SESSION_FILE}" ]

  run_wire profile
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given explicit stub backend selector, when login runs, then stub lane remains deterministic" {
  export WIRE_BACKEND="stub"
  export WIRE_STUB_MODE="login_ok"

  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [[ "${output}" == *"Login successful."* ]]
  [ -f "${WIRE_SESSION_FILE}" ]
}

@test "Given real backend credentials, when login profile logout run, then live Kalium path succeeds" {
  if [[ -z "${WIRE_REAL_EMAIL:-}" || -z "${WIRE_REAL_PASSWORD:-}" ]]; then
    skip "Set WIRE_REAL_EMAIL and WIRE_REAL_PASSWORD to run live Kalium integration smoke"
  fi

  export WIRE_BACKEND="real"
  if [[ -n "${WIRE_REAL_SERVER:-}" ]]; then
    run_wire_with_stdin "${WIRE_REAL_PASSWORD}" login --email "${WIRE_REAL_EMAIL}" --password-stdin --server "${WIRE_REAL_SERVER}"
  else
    run_wire_with_stdin "${WIRE_REAL_PASSWORD}" login --email "${WIRE_REAL_EMAIL}" --password-stdin
  fi
  assert_status 0
  [[ "${output}" == *"Login successful."* ]]
  [ -f "${WIRE_SESSION_FILE}" ]

  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name:"* ]]
  [[ "${output}" == *"Email:"* ]]
  [[ "${output}" == *"Handle:"* ]]
  [[ "${output}" == *"Presence:"* ]]

  run_wire presence
  assert_status 0
  [[ "${output}" == "online" || "${output}" == "away" || "${output}" == "busy" || "${output}" == "offline" || "${output}" == "unknown" ]]

  run_wire logout
  assert_status 0
  [ "${output}" = "Logged out." ]
  [ ! -f "${WIRE_SESSION_FILE}" ]
}
