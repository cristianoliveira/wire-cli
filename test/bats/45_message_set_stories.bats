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
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "test@example.com" --password "Password1"
  export WIRE_STUB_MODE=""
}

teardown() {
  teardown_wire_test_env
}

@test "message set: read marks requested message" {
  export WIRE_STUB_MODE="success"

  run_wire message set "conv-001" --read "msg-001"

  assert_status 0
  [[ "${output}" == "Message marked as read." ]]
}

@test "message set: read emits requested coordinates and outcome as JSON" {
  export WIRE_STUB_MODE="success"

  run_wire message set "conv-001" --read "msg-001" --json

  assert_status 0
  [[ "${output}" == '{"conversationId":"conv-001","messageId":"msg-001","state":"read","outcome":"applied"}' ]]
}

@test "message set: repeated read reports an idempotent no-op" {
  export WIRE_STUB_MODE="already_read"

  run_wire message set "conv-001" --read "msg-001" --json

  assert_status 0
  [[ "${output}" == '{"conversationId":"conv-001","messageId":"msg-001","state":"read","outcome":"already_read"}' ]]
}

@test "message set: read requires a message id" {
  run_wire message set "conv-001" --read

  assert_status 2
  [[ "${output}" == *"--read"* ]]
}

@test "message set: read fails when session is unavailable" {
  rm -f "${WIRE_SESSION_FILE}"

  run_wire message set "conv-001" --read "msg-001"

  assert_status 1
  [[ "${output}" == *"session"* ]] || [[ "${output}" == *"logged in"* ]]
}

@test "message set: read maps network errors" {
  export WIRE_STUB_MODE="network_error"

  run_wire message set "conv-001" --read "msg-001"

  assert_status 1
  [[ "${output}" == *"network"* ]]
}

@test "message set: help documents read option" {
  run_wire message set --help

  assert_status 0
  [[ "${output}" == *"--read"* ]]
  [[ "${output}" == *"message ID"* ]]
}
