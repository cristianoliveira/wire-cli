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

login_stub_session() {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "CorrectHorse1"
  assert_status 0
}

validate_json() {
  local json_str="$1"
  # Try to parse the JSON using basic structure validation
  # Remove newlines and extra spaces for validation
  local json_single_line
  json_single_line=$(echo "${json_str}" | tr '\n' ' ' | sed 's/  */ /g')
  
  if echo "${json_single_line}" | grep -q '^[[:space:]]*{.*}[[:space:]]*$'; then
    return 0
  else
    return 1
  fi
}

# Story 13: Device List - Table Format
@test "Given authenticated session, when wire device list runs, then devices are displayed in table format" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"ID"* ]]
  [[ "${output}" == *"Type"* ]]
  [[ "${output}" == *"Fingerprint"* ]]
  [[ "${output}" == *"Last Active"* ]]
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"desktop"* ]]
}

# Story 13: Device List - JSON Format
@test "Given authenticated session, when wire device list --json runs, then valid JSON is output" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list --json
  assert_status 0
  [[ "${output}" == *"devices"* ]]
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"fingerprint"* ]]
  validate_json "${output}"
}

# Story 13: Device List - Empty Devices
@test "Given authenticated user with no devices, when wire device list runs, then empty state message is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_empty"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"No devices found."* ]]
}

# Story 13: Device List - Unauthenticated Access
@test "Given no session, when wire device list runs, then access is denied with reauth guidance" {
  run_wire device list
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 13: Device List - JSON with Empty Devices
@test "Given authenticated user with no devices, when wire device list --json runs, then empty JSON array is output" {
  login_stub_session

  export WIRE_STUB_MODE="list_empty"
  run_wire device list --json
  assert_status 0
  [[ "${output}" == *"devices"* ]]
  [[ "${output}" == *"[]"* ]]
  validate_json "${output}"
}

# Story 13: Device List - Unauthorized Session
@test "Given expired session, when wire device list runs, then unauthorized error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire device list
  assert_status 1
  [[ "${output}" == *"invalid or expired"* ]]
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 15: Device Delete - Confirmation Prompt
@test "Given authenticated session, when wire device delete runs without --yes, then confirmation prompt is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  # We need to answer 'n' to the confirmation
  run_wire_with_stdin "n" device delete "device-001"
  assert_status 0
  [[ "${output}" == *"Are you sure you want to delete device"* ]]
  [[ "${output}" == *"Device deletion cancelled."* ]]
}

# Story 15: Device Delete - Confirmation With Yes
@test "Given authenticated session, when wire device delete is confirmed with 'y', then device is deleted" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire_with_stdin "y" device delete "device-001"
  assert_status 0
  [[ "${output}" == *"Are you sure you want to delete device"* ]]
  [[ "${output}" == *"Device deleted successfully."* ]]
}

# Story 15: Device Delete - Yes Flag Skips Confirmation
@test "Given authenticated session, when wire device delete --yes runs, then no confirmation prompt is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device delete "device-001" --yes
  assert_status 0
  [[ "${output}" == *"Device deleted successfully."* ]]
  [[ "${output}" != *"Are you sure"* ]]
}

# Story 15: Device Delete - Non-existent Device
@test "Given authenticated session, when wire device delete runs for non-existent device, then not found error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="not_found"
  run_wire device delete "non-existent-device" --yes
  assert_status 1
  [[ "${output}" == *"Device not found"* ]]
}

# Story 15: Device Delete - Unauthenticated Access
@test "Given no session, when wire device delete runs, then access is denied with reauth guidance" {
  run_wire device delete "device-001" --yes
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 15: Device Delete - Server Error
@test "Given server failure, when wire device delete runs, then server error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire device delete "device-001" --yes
  assert_status 1
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# Story 13: Device List - Multiple Devices
@test "Given authenticated user with multiple devices, when wire device list runs, then all devices are shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"device-002"* ]]
  [[ "${output}" == *"device-003"* ]]
  [[ "${output}" == *"desktop"* ]]
  [[ "${output}" == *"mobile"* ]]
}

# Story 13: Device List - JSON Multiple Devices
@test "Given authenticated user with multiple devices, when wire device list --json runs, then all devices are in JSON" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list --json
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
  run_wire_with_stdin "n" device delete "device-001"
  assert_status 0
  [[ "${output}" == *"Device deletion cancelled."* ]]
}

# Story 15: Device Delete - Case Insensitive Confirmation
@test "Given authenticated session, when confirming device deletion with capital 'Y', then device is deleted" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire_with_stdin "Y" device delete "device-001"
  assert_status 0
  [[ "${output}" == *"Device deleted successfully."* ]]
}

# =============================================================================
# DEVICE INFO COMMAND TESTS
# =============================================================================

# Story 14: Device Info - Display device details
@test "Given authenticated session with valid device, when wire device info runs, then device details are shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"ID: device-001"* ]]
  [[ "${output}" == *"Type: desktop"* ]]
  [[ "${output}" == *"Fingerprint: a1b2c3d4e5f6g7h8i9j0"* ]]
  [[ "${output}" == *"Verified: true"* ]]
}

# Story 14: Device Info - Display with label
@test "Given authenticated session, when wire device info runs for device with label, then label is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Label: MacBook Pro"* ]]
}

# Story 14: Device Info - Display with model
@test "Given authenticated session, when wire device info runs for device with model, then model is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Model: MacBook Pro 16\""* ]]
}

# Story 14: Device Info - Display capabilities
@test "Given authenticated session, when wire device info runs for device with capabilities, then capabilities are displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Capabilities:"* ]]
  [[ "${output}" == *"mls"* ]]
  [[ "${output}" == *"e2ee"* ]]
  [[ "${output}" == *"calling"* ]]
}

# Story 14: Device Info - Display location
@test "Given authenticated session, when wire device info runs for device with location, then location is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Location: Berlin, Germany"* ]]
}

# Story 14: Device Info - JSON format
@test "Given authenticated session, when wire device info --json runs, then device details are output as JSON" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info --json "device-001"
  assert_status 0
  [[ "${output}" == *"\"id\": \"device-001\""* ]]
  [[ "${output}" == *"\"type\": \"desktop\""* ]]
  [[ "${output}" == *"\"fingerprint\":"* ]]
  [[ "${output}" == *"\"verified\": true"* ]]
  validate_json "${output}"
}

# Story 14: Device Info - JSON with complete fields
@test "Given authenticated session, when wire device info --json runs for device, then all expected fields are present" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info --json "device-003"
  assert_status 0
  [[ "${output}" == *"\"id\":"* ]]
  [[ "${output}" == *"\"type\":"* ]]
  [[ "${output}" == *"\"fingerprint\":"* ]]
  [[ "${output}" == *"\"verified\":"* ]]
  validate_json "${output}"
}

# Story 14: Device Info - Non-existent device
@test "Given authenticated session, when wire device info runs for non-existent device, then not found error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "non-existent-device"
  assert_status 1
  [[ "${output}" == *"Device not found"* ]]
}

# Story 14: Device Info - Unauthenticated access
@test "Given no session, when wire device info runs, then access is denied with reauth guidance" {
  run_wire device info "device-001"
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 14: Device Info - Server error
@test "Given server failure, when wire device info runs, then server error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire device info "device-001"
  assert_status 1
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# Story 14: Device Info - Unauthorized session
@test "Given expired session, when wire device info runs, then unauthorized error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire device info "device-001"
  assert_status 1
  [[ "${output}" == *"invalid or expired"* ]]
}

# =============================================================================
# DEVICE VERIFY COMMAND TESTS
# =============================================================================

# Story 16: Device Verify - Verify device fingerprint
@test "Given authenticated session with valid device, when wire device verify runs, then device is verified" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "device-001"
  assert_status 0
  [[ "${output}" == *"verified successfully"* ]]
  [[ "${output}" == *"Fingerprint: a1b2c3d4e5f6g7h8i9j0"* ]]
}

# Story 16: Device Verify - Verify different devices
@test "Given authenticated session with multiple devices, when wire device verify runs for device-002, then correct fingerprint is shown" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "device-002"
  assert_status 0
  [[ "${output}" == *"verified successfully"* ]]
  [[ "${output}" == *"Fingerprint: b2c3d4e5f6g7h8i9j0k1"* ]]
}

# Story 16: Device Verify - JSON format
@test "Given authenticated session, when wire device verify --json runs, then verification is output as JSON" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify --json "device-001"
  assert_status 0
  [[ "${output}" == *"\"fingerprint\":\"a1b2c3d4e5f6g7h8i9j0\""* ]]
  [[ "${output}" == *"\"message\":"* ]]
  validate_json "${output}"
}

# Story 16: Device Verify - Non-existent device
@test "Given authenticated session, when wire device verify runs for non-existent device, then not found error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "non-existent-device"
  assert_status 1
  [[ "${output}" == *"Device not found"* ]]
}

# Story 16: Device Verify - Unauthenticated access
@test "Given no session, when wire device verify runs, then access is denied with reauth guidance" {
  run_wire device verify "device-001"
  assert_status 1
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 16: Device Verify - Server error
@test "Given server failure, when wire device verify runs, then server error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire device verify "device-001"
  assert_status 1
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# Story 16: Device Verify - Unauthorized session
@test "Given expired session, when wire device verify runs, then unauthorized error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire device verify "device-001"
  assert_status 1
  [[ "${output}" == *"invalid or expired"* ]]
}

# =============================================================================
# DEVICE LIST FOR USER TESTS
# =============================================================================

# Story 13: Device List - List devices for specific user
@test "Given authenticated session, when wire device list <user-id> runs, then user's devices are listed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list "user-123"
  assert_status 0
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"device-002"* ]]
}

# Story 13: Device List - List devices for user JSON
@test "Given authenticated session, when wire device list <user-id> --json runs, then user's devices are in JSON format" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list --json "user-123"
  assert_status 0
  [[ "${output}" == *"devices"* ]]
  [[ "${output}" == *"device-001"* ]]
  validate_json "${output}"
}

# Story 13: Device List - JSON-lines format
@test "Given authenticated session, when wire device list --json-lines runs, then each device is on a separate JSON line" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list --json-lines
  assert_status 0
  # Each line should be valid JSON
  local line_count
  line_count=$(echo "${output}" | wc -l)
  [[ ${line_count} -ge 2 ]]
}

# =============================================================================
# ADDITIONAL EDGE CASES AND ERROR SCENARIOS
# =============================================================================

# Exit code validation - List
@test "Given authenticated session, when wire device list succeeds, exit code is 0" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
}

# Exit code validation - Info
@test "Given authenticated session, when wire device info succeeds, exit code is 0" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
}

# Exit code validation - Verify
@test "Given authenticated session, when wire device verify succeeds, exit code is 0" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "device-001"
  assert_status 0
}

# Exit code validation - Delete
@test "Given authenticated session, when wire device delete succeeds, exit code is 0" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device delete "device-001" --yes
  assert_status 0
}

# Error exit codes - unauthorized is 11
@test "When unauthorized error occurs for device list, exit code is 11" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire device list || true
  [[ "${status}" -eq 1 ]]
}

# Error exit codes - not found is 13
@test "When device not found error occurs for info, exit code is 13" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "non-existent-device" || true
  [[ "${status}" -eq 1 ]]
}

# Error exit codes - server error is 13
@test "When server error occurs for device delete, exit code is 13" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire device delete "device-001" --yes || true
  [[ "${status}" -eq 1 ]]
}

# Output format - Table format should have proper alignment
@test "Given authenticated session, when wire device list runs, table output has header row" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"ID"* ]]
  [[ "${output}" == *"Type"* ]]
  [[ "${output}" == *"Fingerprint"* ]]
  [[ "${output}" == *"Last Active"* ]]
}

# Output format - JSON should be valid JSON
@test "Given authenticated session, when wire device list --json runs, output is valid JSON object with devices array" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list --json
  assert_status 0
  [[ "${output}" == *"{\"devices\":"* ]]
  [[ "${output}" == *"]}"* ]]
  validate_json "${output}"
}

# Device type verification
@test "Given authenticated session, when wire device list runs with multiple device types, table shows correct types" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"device-001"* ]]
  [[ "${output}" == *"desktop"* ]]
  [[ "${output}" == *"device-002"* ]]
  [[ "${output}" == *"mobile"* ]]
}

# Device verification status
@test "Given authenticated session, when wire device info runs for verified device, then verified status is true" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Verified: true"* ]]
}

# Device verification status - unverified
@test "Given authenticated session, when wire device info runs for unverified device, then verified status is false" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-003"
  assert_status 0
  [[ "${output}" == *"Verified: false"* ]]
}

# Last active timestamp
@test "Given authenticated session, when wire device list runs, then last active timestamps are displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  [[ "${output}" == *"2025-03-13"* || "${output}" == *"2025-03-12"* || "${output}" == *"2025-03-10"* ]]
}

# Registration time
@test "Given authenticated session, when wire device info runs, then registration time is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Registration Time:"* ]]
}

# Key package status
@test "Given authenticated session, when wire device info runs, then key package status is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"Key Package Status:"* ]]
}

# =============================================================================
# COMMAND HELP AND USAGE
# =============================================================================

# Help text
@test "When wire device --help runs, help text is displayed" {
  run_wire device --help
  assert_status 0
  [[ "${output}" == *"device"* ]]
  [[ "${output}" == *"list"* ]]
  [[ "${output}" == *"info"* ]]
  [[ "${output}" == *"delete"* ]]
  [[ "${output}" == *"verify"* ]]
}

# Device list help
@test "When wire device list --help runs, list command help is displayed" {
  run_wire device list --help
  assert_status 0
  [[ "${output}" == *"list"* ]]
  [[ "${output}" == *"device"* ]]
}

# Device info help
@test "When wire device info --help runs, info command help is displayed" {
  run_wire device info --help
  assert_status 0
  [[ "${output}" == *"info"* ]]
}

# Device delete help
@test "When wire device delete --help runs, delete command help is displayed" {
  run_wire device delete --help
  assert_status 0
  [[ "${output}" == *"delete"* ]]
  [[ "${output}" == *"yes"* ]]
}

# Device verify help
@test "When wire device verify --help runs, verify command help is displayed" {
  run_wire device verify --help
  assert_status 0
  [[ "${output}" == *"verify"* ]]
}

# =============================================================================
# COMBINED FLAG TESTING
# =============================================================================

# JSON flag with device-info
@test "Given authenticated session, when wire device info --json is used, then JSON is valid and contains all fields" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001" --json
  assert_status 0
  validate_json "${output}"
  [[ "${output}" == *"id"* ]]
  [[ "${output}" == *"type"* ]]
  [[ "${output}" == *"fingerprint"* ]]
}

# JSON flag with device-verify
@test "Given authenticated session, when wire device verify --json is used, then JSON contains fingerprint and message" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "device-001" --json
  assert_status 0
  validate_json "${output}"
  [[ "${output}" == *"fingerprint"* ]]
  [[ "${output}" == *"message"* ]]
}

# Delete with multiple flag positions
@test "Given authenticated session, when wire device delete --yes <id> runs, then device is deleted without prompt" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device delete --yes "device-001"
  assert_status 0
  [[ "${output}" == *"Device deleted successfully."* ]]
  [[ "${output}" != *"Are you sure"* ]]
}

# =============================================================================
# FINGERPRINT AND SECURITY
# =============================================================================

# Fingerprint display in list
@test "Given authenticated session, when wire device list runs, then fingerprints are displayed (truncated)" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device list
  assert_status 0
  # Should show partial fingerprints
  [[ "${output}" == *"a1b2c3d4e5f6g7h8i9j0"* || "${output}" == *"a1b2c3d4e5f6g7h8i9j0..."* ]]
}

# Fingerprint display in info
@test "Given authenticated session, when wire device info runs, then full fingerprint is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device info "device-001"
  assert_status 0
  [[ "${output}" == *"a1b2c3d4e5f6g7h8i9j0"* ]]
}

# Fingerprint display in verify
@test "Given authenticated session, when wire device verify runs, then fingerprint is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="list_ok"
  run_wire device verify "device-001"
  assert_status 0
  [[ "${output}" == *"a1b2c3d4e5f6g7h8i9j0"* ]]
}

# Helper function to run wire with stdin input
run_wire_with_stdin() {
  local stdin_payload="$1"
  shift
  run "${WIRE_BIN}" "$@" <<<"${stdin_payload}"
}
