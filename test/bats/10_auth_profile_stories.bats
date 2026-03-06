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

@test "Given no session, when profile runs, then access is denied" {
  run_wire profile
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

@test "Given valid credentials, when login runs, then session is persisted" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
  [[ "${output}" == *"Login successful"* ]]
  [ -f "${WIRE_SESSION_FILE}" ]
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
  [[ "${output}" != *"Run wire login"* ]]
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
  [[ "${output}" == *"Logged out"* ]]
  [ ! -f "${WIRE_SESSION_FILE}" ]

  run_wire profile
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}
