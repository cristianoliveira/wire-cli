#!/usr/bin/env bats

load "helpers/common.bash"

setup_file() {
  if [[ -z "${WIRE_BIN:-}" ]]; then
    WIRE_BIN="${BATS_TEST_DIRNAME}/../../build/install/wire/bin/wire"
  fi
  export WIRE_BIN
}

setup() {
  setup_wire_test_env
}

teardown() {
  teardown_wire_test_env
}

login_stub() {
  export WIRE_STUB_MODE="login_ok"
  run "${WIRE_BIN}" login --email "jane@example.com" --password "CorrectHorse1"
  assert_status 0
  [ -f "${WIRE_SESSION_FILE}" ]
  unset WIRE_STUB_MODE
}

@test "Given no session, when user search runs, then access is denied" {
  run "${WIRE_BIN}" user search alice
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given no session, when user get runs, then access is denied" {
  run "${WIRE_BIN}" user get alice-uuid@example.wire.com
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given no session, when connection request runs, then access is denied" {
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given no session, when connection block runs, then access is denied" {
  run "${WIRE_BIN}" connection block bob-uuid@example.wire.com --yes
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given persisted session, when user search runs, then matching users are listed" {
  login_stub
  run "${WIRE_BIN}" user search alice
  assert_status 0
  [[ "${output}" == *"alice-uuid@example.wire.com"* ]]
  [[ "${output}" == *"alice"* ]]
}

@test "Given persisted session, when user search matches nothing, then no-results message is shown" {
  login_stub
  run "${WIRE_BIN}" user search zzz-nope
  assert_status 0
  [[ "${output}" == *"No users found"* ]]
}

@test "Given persisted session, when user search runs with --json, then output is machine-readable" {
  login_stub
  run "${WIRE_BIN}" user search alice --json
  assert_status 0
  [[ "${output}" == *'"schemaVersion"'* ]]
  [[ "${output}" == *'"users"'* ]]
  [[ "${output}" == *'"id":"alice-uuid@example.wire.com"'* ]]
}

@test "Given persisted session, when user search runs with --json-lines, then one object per line" {
  login_stub
  run "${WIRE_BIN}" user search e --json-lines
  assert_status 0
  line_count="$(printf '%s\n' "${output}" | wc -l | tr -d ' ')"
  [[ "${output}" == *'"id"'* ]]
}

@test "Given persisted session, when user get runs, then user detail is printed" {
  login_stub
  run "${WIRE_BIN}" user get alice-uuid@example.wire.com
  assert_status 0
  [[ "${output}" == *"alice-uuid@example.wire.com"* ]]
  [[ "${output}" == *"alice"* ]]
}

@test "Given persisted session, when user get runs with --json, then detail is machine-readable" {
  login_stub
  run "${WIRE_BIN}" user get alice-uuid@example.wire.com --json
  assert_status 0
  [[ "${output}" == *'"id":"alice-uuid@example.wire.com"'* ]]
  [[ "${output}" == *'"handle":"alice"'* ]]
}

@test "Given persisted session, when user get runs for unknown id, then not found is reported" {
  login_stub
  run "${WIRE_BIN}" user get ghost@example.wire.com
  [[ "${status}" -ne 0 ]]
  [[ "${output}" == *"not found"* ]]
}

@test "Given invalid user id, when user get runs, then validation error is returned" {
  login_stub
  run "${WIRE_BIN}" user get not-a-qualified-id
  assert_status 2
  [[ "${output}" == *"validation error"* ]]
}

@test "Given persisted session, when connection request runs, then success message is shown" {
  login_stub
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com
  assert_status 0
  [[ "${output}" == *"Connection request sent."* ]]
}

@test "Given persisted session, when connection request runs with --json, then output is machine-readable" {
  login_stub
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com --json
  assert_status 0
  [[ "${output}" == *'"ok":true'* ]]
  [[ "${output}" == *'"message":"Connection request sent."'* ]]
}

@test "Given persisted session, when connection request is repeated, then it is idempotent success" {
  login_stub
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com
  assert_status 0
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com
  assert_status 0
  [[ "${output}" == *"Connection request sent."* ]]
}

@test "Given persisted session, when connection block runs without --yes, then confirmation is required" {
  login_stub
  run "${WIRE_BIN}" connection block bob-uuid@example.wire.com
  assert_status 2
  [[ "${output}" == *"--yes"* ]]
}

@test "Given persisted session, when connection block runs with --yes, then success message is shown" {
  login_stub
  run "${WIRE_BIN}" connection block bob-uuid@example.wire.com --yes
  assert_status 0
  [[ "${output}" == *"User blocked."* ]]
}

@test "Given persisted session, when connection block hits a server error, then failure is reported" {
  login_stub
  export WIRE_STUB_MODE="connection_block_server_error"
  run "${WIRE_BIN}" connection block bob-uuid@example.wire.com --yes
  assert_status 1
  [[ "${output}" == *"could not be completed"* ]]
}

@test "Given persisted session, when connection unblock runs, then success message is shown" {
  login_stub
  run "${WIRE_BIN}" connection unblock bob-uuid@example.wire.com
  assert_status 0
  [[ "${output}" == *"User unblocked."* ]]
}

@test "Given persisted session, when connection unblock hits a server error, then failure is reported" {
  login_stub
  export WIRE_STUB_MODE="connection_unblock_server_error"
  run "${WIRE_BIN}" connection unblock bob-uuid@example.wire.com
  assert_status 1
  [[ "${output}" == *"could not be completed"* ]]
}

@test "Given network error, when user search runs, then actionable error is returned" {
  login_stub
  export WIRE_STUB_MODE="user_search_network_error"
  run "${WIRE_BIN}" user search alice
  assert_status 1
  [[ "${output}" == *"Check your connection and retry"* ]]
}

@test "Given network error, when connection request runs, then actionable error is returned" {
  login_stub
  export WIRE_STUB_MODE="connection_request_network_error"
  run "${WIRE_BIN}" connection request bob-uuid@example.wire.com
  assert_status 1
  [[ "${output}" == *"Check your connection and retry"* ]]
}
