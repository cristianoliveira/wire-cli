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

write_labeled_inventory() {
  local file_path="$1"
  printf '%s\n' \
    "wire-cli-session-store:3" \
    "active: jane@wire.com" \
    "work" "jane@wire.com" "token-jane" "wire.com" \
    "personal" "bob@wire.com" "token-bob" "" \
    >"${file_path}"
}

@test "Given login with --label, when whoami runs, then the label is shown" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "CorrectHorse1" --label work
  assert_status 0

  run_wire whoami
  assert_status 0
  [[ "${output}" == *"work  user-jane"* ]]
}

@test "Given labeled accounts, when account list runs, then labels are shown with the active marker" {
  write_labeled_inventory "${WIRE_SESSION_FILE}"

  run_wire account list
  assert_status 0
  [[ "${output}" == *"* work  jane@wire.com  (wire.com)"* ]]
  [[ "${output}" == *"  personal  bob@wire.com"* ]]
}

@test "Given labeled accounts, when account use runs by label, then the active account switches" {
  write_labeled_inventory "${WIRE_SESSION_FILE}"

  run_wire account use personal
  assert_status 0
  [[ "${output}" == "Switched to personal (bob@wire.com)."* ]]

  run_wire whoami
  assert_status 0
  [[ "${output}" == *"personal  bob@wire.com"* ]]
}

@test "Given labeled accounts, when account use runs by user id, then it still resolves" {
  write_labeled_inventory "${WIRE_SESSION_FILE}"

  run_wire account use bob@wire.com
  assert_status 0
  [[ "${output}" == "Switched to personal (bob@wire.com)."* ]]
}

@test "Given an unknown label, when account use runs, then a validation error is returned" {
  write_labeled_inventory "${WIRE_SESSION_FILE}"

  run_wire account use ghost
  assert_status 2
  [[ "${output}" == *"No stored account for 'ghost'"* ]]
}

@test "Given an existing label on another account, when login reuses it, then the label is rejected" {
  write_labeled_inventory "${WIRE_SESSION_FILE}"
  export WIRE_STUB_MODE="login_ok"

  run_wire login --email "other@example.com" --password "CorrectHorse1" --label work
  assert_status 2
  [[ "${output}" == *"Account label 'work' is already in use"* ]]
}
