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

validate_json() {
  local json_str="$1"
  # Try to parse the JSON using basic structure validation
  if echo "${json_str}" | grep -q '^{.*}$'; then
    return 0
  else
    return 1
  fi
}

# Story 13: Device List - Table Format
@test "Given authenticated session, when wire client list runs, then devices are displayed in table format" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire client list
  assert_status 0
  [[ "${output}" == *"ID"* ]]
  [[ "${output}" == *"Type"* ]]
  [[ "${output}" == *"Fingerprint"* ]]
  [[ "${output}" == *"Last Active"* ]]
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"desktop"* ]]
}

# Story 13: Device List - JSON Format
@test "Given authenticated session, when wire client list --json runs, then valid JSON is output" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire client list --json
  assert_status 0
  [[ "${output}" == *"devices"* ]]
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"fingerprint"* ]]
  validate_json "${output}"
}

# Story 13: Device List - Empty Devices
@test "Given authenticated user with no devices, when wire client list runs, then empty state message is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_empty"
  run_wire client list
  assert_status 0
  [[ "${output}" == *"No devices found."* ]]
}

# Story 13: Device List - Unauthenticated Access
@test "Given no session, when wire client list runs, then access is denied with reauth guidance" {
  run_wire client list
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 13: Device List - JSON with Empty Devices
@test "Given authenticated user with no devices, when wire client list --json runs, then empty JSON array is output" {
  login_stub_session

  export WIRE_STUB_MODE="list_empty"
  run_wire client list --json
  assert_status 0
  [[ "${output}" == *"devices"* ]]
  [[ "${output}" == *"[]"* ]]
  validate_json "${output}"
}

# Story 13: Device List - Unauthorized Session
@test "Given expired session, when wire client list runs, then unauthorized error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire client list
  assert_status 11
  [[ "${output}" == *"invalid or expired"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 15: Device Delete - Confirmation Prompt
@test "Given authenticated session, when wire client delete runs without --yes, then confirmation prompt is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  # We need to answer 'n' to the confirmation
  run_wire_with_stdin "n" client delete "device-001"
  assert_status 0
  [[ "${output}" == *"Are you sure you want to delete device"* ]]
  [[ "${output}" == *"Device deletion cancelled."* ]]
}

# Story 15: Device Delete - Confirmation With Yes
@test "Given authenticated session, when wire client delete is confirmed with 'y', then device is deleted" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire_with_stdin "y" client delete "device-001"
  assert_status 0
  [[ "${output}" == *"Are you sure you want to delete device"* ]]
  [[ "${output}" == *"Device deleted successfully."* ]]
}

# Story 15: Device Delete - Yes Flag Skips Confirmation
@test "Given authenticated session, when wire client delete --yes runs, then no confirmation prompt is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire client delete "device-001" --yes
  assert_status 0
  [[ "${output}" == *"Device deleted successfully."* ]]
  [[ "${output}" != *"Are you sure"* ]]
}

# Story 15: Device Delete - Non-existent Device
@test "Given authenticated session, when wire client delete runs for non-existent device, then not found error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="not_found"
  run_wire client delete "non-existent-device" --yes
  assert_status 14
  [[ "${output}" == *"Device not found"* ]]
}

# Story 15: Device Delete - Unauthenticated Access
@test "Given no session, when wire client delete runs, then access is denied with reauth guidance" {
  run_wire client delete "device-001" --yes
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 15: Device Delete - Server Error
@test "Given server failure, when wire client delete runs, then server error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire client delete "device-001" --yes
  assert_status 13
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# Story 13: Device List - Multiple Devices
@test "Given authenticated user with multiple devices, when wire client list runs, then all devices are shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire client list
  assert_status 0
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"device-002"* ]]
  [[ "${output}" == *"device-003"* ]]
  [[ "${output}" == *"desktop"* ]]
  [[ "${output}" == *"mobile"* ]]
}

# Story 13: Device List - JSON Multiple Devices
@test "Given authenticated user with multiple devices, when wire client list --json runs, then all devices are in JSON" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire client list --json
  assert_status 0
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"device-002"* ]]
  [[ "${output}" == *"device-003"* ]]
  validate_json "${output}"
}

# Story 15: Device Delete - Confirmation Cancelled
@test "Given authenticated session, when device deletion confirmation is cancelled, then device is not deleted" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  # User inputs lowercase 'n'
  run_wire_with_stdin "n" client delete "device-001"
  assert_status 0
  [[ "${output}" == *"Device deletion cancelled."* ]]
}

# Story 15: Device Delete - Case Insensitive Confirmation
@test "Given authenticated session, when confirming device deletion with capital 'Y', then device is deleted" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire_with_stdin "Y" client delete "device-001"
  assert_status 0
  [[ "${output}" == *"Device deleted successfully."* ]]
}

# Helper function to run wire with stdin input
run_wire_with_stdin() {
  local stdin_payload="$1"
  shift
  run "${WIRE_BIN}" "$@" <<<"${stdin_payload}"
}
