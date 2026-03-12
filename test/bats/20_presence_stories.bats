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

login_stub_session() {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
}

@test "Given persisted session, when presence runs, then normalized presence value is printed" {
  login_stub_session

  run_wire presence
  assert_status 0
  [ "${output}" = "online" ]
}

@test "Given unsupported upstream presence value, when presence runs, then output is normalized to unknown" {
  login_stub_session

  export WIRE_STUB_MODE="presence_invalid_value"
  run_wire presence
  assert_status 0
  [ "${output}" = "unknown" ]
}

@test "Given presence unauthorized response, when presence runs, then command fails with relogin guidance" {
  login_stub_session

  export WIRE_STUB_MODE="presence_unauthorized"
  run_wire presence
  assert_status 11
  [[ "${output}" == *"Session is invalid or expired"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given presence network failure, when presence runs, then retry guidance is returned" {
  login_stub_session

  export WIRE_STUB_MODE="presence_network_error"
  run_wire presence
  assert_status 12
  [[ "${output}" == *"Check your connection and retry"* ]]
}

@test "Given presence server failure, when presence runs, then concise server guidance is returned" {
  login_stub_session

  export WIRE_STUB_MODE="presence_server_error"
  run_wire presence
  assert_status 13
  [[ "${output}" == *"Retry later or check server settings"* ]]
}

@test "Given profile success and presence fetch failure, when profile runs, then output falls back to unknown and exits zero" {
  login_stub_session

  export WIRE_STUB_MODE="presence_network_error"
  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name: Jane Doe"* ]]
  [[ "${output}" == *"Presence: unknown"* ]]
}
