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
  run_wire login --email "jane@example.com" --password "CorrectHorse1"
  assert_status 0
}

@test "Given persisted session, when presence get runs, then normalized presence value is printed" {
  login_stub_session

  run_wire presence get
  assert_status 0
  [ "${output}" = "online" ]
}

@test "Given persisted session, when bare presence runs, then command remains compatible and prints presence" {
  login_stub_session

  run_wire presence
  assert_status 0
  [ "${output}" = "online" ]
}

@test "Given unsupported upstream presence value, when presence get runs, then output is normalized to unknown" {
  login_stub_session

  export WIRE_STUB_MODE="presence_invalid_value"
  run_wire presence get
  assert_status 0
  [ "${output}" = "unknown" ]
}

@test "Given presence unauthorized response, when presence get runs, then command fails with relogin guidance" {
  login_stub_session

  export WIRE_STUB_MODE="presence_unauthorized"
  run_wire presence get
  assert_status 11
  [[ "${output}" == *"Session is invalid or expired"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Given presence network failure, when presence get runs, then retry guidance is returned" {
  login_stub_session

  export WIRE_STUB_MODE="presence_network_error"
  run_wire presence get
  assert_status 12
  [[ "${output}" == *"Check your connection and retry"* ]]
}

@test "Given presence server failure, when presence get runs, then concise server guidance is returned" {
  login_stub_session

  export WIRE_STUB_MODE="presence_server_error"
  run_wire presence get
  assert_status 13
  [[ "${output}" == *"Retry later or check server settings"* ]]
}

@test "Given valid status, when presence set runs, then normalized written value is printed" {
  login_stub_session

  run_wire presence set busy
  assert_status 0
  [ "${output}" = "busy" ]
}

@test "Given invalid status, when presence set runs, then command fails with allowed values guidance" {
  login_stub_session

  run_wire presence set in_a_call
  assert_status 14
  [[ "${output}" == *"Invalid status 'in_a_call'"* ]]
  [[ "${output}" == *"Allowed values: online, busy, away, offline"* ]]
}

@test "Given presence command help, when requested, then get and set subcommands are shown" {
  run_wire presence --help
  assert_status 0
  [[ "${output}" == *"get"* ]]
  [[ "${output}" == *"set"* ]]
}

@test "Given profile success and presence fetch failure, when profile runs, then output falls back to unknown and exits zero" {
  login_stub_session

  export WIRE_STUB_MODE="presence_network_error"
  run_wire profile
  assert_status 0
  [[ "${output}" == *"Name: Jane Doe"* ]]
  [[ "${output}" == *"Presence: unknown"* ]]
}
