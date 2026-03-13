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
  # Check if the JSON starts with { and ends with }
  local trimmed
  trimmed=$(echo "${json_str}" | tr -d '[:space:]')
  if [[ "${trimmed}" =~ ^'{' ]] && [[ "${trimmed}" =~ '}'$ ]]; then
    return 0
  else
    return 1
  fi
}

# Story 17: Sync Status - Basic Health Output
@test "Given authenticated session, when wire sync status runs, then health information is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status
  assert_status 0
  [[ "${output}" == *"Sync Status"* ]]
  [[ "${output}" == *"ready"* ]]
  [[ "${output}" == *"Lag:"* || "${output}" == *"lag"* ]]
  [[ "${output}" == *"Pending:"* || "${output}" == *"pending"* ]]
}

# Story 17: Sync Status - Healthy Exit Code
@test "Given healthy sync status, when wire sync status runs, then exit code is 0" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status
  assert_status 0
}

# Story 17: Sync Status - Degraded Exit Code
@test "Given degraded sync status, when wire sync status runs, then exit code is 1" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire sync status
  assert_status 1
}

# Story 17: Sync Status - Unauthenticated Access
@test "Given no session, when wire sync status runs, then access is denied with reauth guidance" {
  run_wire sync status
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 18: Sync Status - Verbose Flag
@test "Given authenticated session, when wire sync status --verbose runs, then detailed metrics are shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --verbose
  assert_status 0
  [[ "${output}" == *"Event Queue Lag"* || "${output}" == *"Lag"* ]]
  [[ "${output}" == *"Pending Messages"* || "${output}" == *"pending"* ]]
  [[ "${output}" == *"MLS"* || "${output}" == *"migration"* ]]
  [[ "${output}" == *"Last Sync"* || "${output}" == *"timestamp"* ]]
}

# Story 18: Sync Status - Verbose with Degraded Status
@test "Given degraded sync, when wire sync status --verbose runs, then detailed metrics with warnings are shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire sync status --verbose
  assert_status 1
  [[ "${output}" == *"Lag"* || "${output}" == *"lag"* ]]
  [[ "${output}" == *"Pending"* || "${output}" == *"pending"* ]]
  # Should show some warning or degraded indicator
  [[ "${output}" == *"⚠"* || "${output}" == *"warning"* || "${output}" == *"degraded"* ]]
}

# Story 19: Sync Diagnostics - Basic Run
@test "Given authenticated session, when wire sync status --diagnose runs, then diagnostic checks are shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --diagnose
  assert_status 0
  [[ "${output}" == *"Authentication"* ]]
  [[ "${output}" == *"Sync Engine"* || "${output}" == *"Engine"* ]]
  [[ "${output}" == *"Network"* ]]
}

# Story 19: Sync Diagnostics - Recovery Hints
@test "Given degraded sync with diagnostics, when wire sync status --diagnose runs, then recovery hints are shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire sync status --diagnose
  # Diagnostics returns 0 if no error checks (only degraded)
  assert_status 0
  [[ "${output}" == *"Diagnostics"* || "${output}" == *"diagnostics"* || "${output}" == *"checks"* ]]
  [[ "${output}" == *"Recovery"* || "${output}" == *"Command"* ]]
}

# Story 19: Sync Diagnostics - Error State
@test "Given error sync state, when wire sync status --diagnose runs, then error checks and recovery hints shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_error"
  run_wire sync status --diagnose
  # Diagnostics returns 1 (DEGRADED) because error checks are present
  assert_status 1
  [[ "${output}" == *"error"* || "${output}" == *"Error"* ]]
}

# Story 17: Sync Status - JSON Output
@test "Given authenticated session, when wire sync status --json runs, then valid JSON is output" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --json
  assert_status 0
  [[ "${output}" == *"status"* ]]
  [[ "${output}" == *"ready"* ]]
  [[ "${output}" == *"metrics"* ]]
  validate_json "${output}"
}

# Story 18: Sync Status - Verbose JSON Output
@test "Given authenticated session, when wire sync status --verbose --json runs, then valid JSON with metrics is output" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --verbose --json
  assert_status 0
  [[ "${output}" == *"status"* ]]
  [[ "${output}" == *"metrics"* ]]
  [[ "${output}" == *"lag_ms"* || "${output}" == *"lag"* ]]
  validate_json "${output}"
}

# Story 19: Sync Diagnostics - JSON Output
@test "Given authenticated session, when wire sync status --diagnose --json runs, then valid JSON diagnostics output" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --diagnose --json
  assert_status 0
  [[ "${output}" == *"checks"* ]]
  [[ "${output}" == *"summary"* ]]
  validate_json "${output}"
}

# Story 17: Sync Status - Initializing Mode
@test "Given initializing sync state, when wire sync status runs, then initializing status is shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_initializing"
  run_wire sync status
  assert_status 1
  [[ "${output}" == *"initializing"* ]]
}

# Story 17: Sync Status - Error Exit Code
@test "Given error sync state, when wire sync status runs, then error status and degraded exit code" {
  login_stub_session

  export WIRE_STUB_MODE="status_error"
  run_wire sync status
  assert_status 1
  [[ "${output}" == *"error"* ]]
}

# Story 17: Sync Status via Bare Command
@test "Given authenticated session, when bare wire sync runs, then status is displayed" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync
  assert_status 0
  [[ "${output}" == *"Sync Status"* || "${output}" == *"ready"* ]]
}

# Story 17: Sync Status - Network Error
@test "Given network failure, when wire sync status runs, then network error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="network_error"
  run_wire sync status
  # Exit code should be network error (12)
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

# Story 17: Sync Status - Server Error
@test "Given server failure, when wire sync status runs, then server error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire sync status
  assert_status 13
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# Story 19: Sync Diagnostics - Healthy Checks
@test "Given healthy sync, when wire sync status --diagnose runs, then all checks show healthy status" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status --diagnose
  assert_status 0
  [[ "${output}" == *"healthy"* ]]
}

# Story 19: Sync Diagnostics - Initializing Checks
@test "Given initializing sync, when wire sync status --diagnose runs, then checks show initializing status" {
  login_stub_session

  export WIRE_STUB_MODE="status_initializing"
  run_wire sync status --diagnose
  # Diagnostics returns 0 if no error checks (initializing is not an error)
  assert_status 0
  [[ "${output}" == *"initializing"* ]]
}

# Story 17: Sync Status - Multiple Calls
@test "Given authenticated session, when wire sync status is called multiple times, then consistent output" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire sync status
  local first_output="${output}"
  assert_status 0

  run_wire sync status
  local second_output="${output}"
  assert_status 0

  [[ "${first_output}" == "${second_output}" ]]
}

# Story 18: Sync Status - Verbose with Initializing
@test "Given initializing sync, when wire sync status --verbose runs, then initializing metrics shown" {
  login_stub_session

  export WIRE_STUB_MODE="status_initializing"
  run_wire sync status --verbose
  assert_status 1
  [[ "${output}" == *"Lag"* || "${output}" == *"lag"* ]]
}

# Story 19: Sync Diagnostics - JSON with Recovery Hints
@test "Given degraded sync with diagnostics, when wire sync status --diagnose --json runs, then recovery hints in JSON" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire sync status --diagnose --json
  # Diagnostics returns 0 if no error checks (only degraded)
  assert_status 0
  [[ "${output}" == *"recoveryHints"* || "${output}" == *"recovery"* ]]
  validate_json "${output}"
}

# Story 17: Sync Status - Unauthorized Session
@test "Given expired session, when wire sync status runs, then unauthorized error is returned" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire sync status
  assert_status 11
  [[ "${output}" == *"invalid or expired"* || "${output}" == *"unauthorized"* ]]
}

# Story 19: Sync Diagnostics - Unauthenticated Access
@test "Given no session, when wire sync status --diagnose runs, then access is denied" {
  run_wire sync status --diagnose
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# Story 18: Sync Status - Verbose Unauthenticated
@test "Given no session, when wire sync status --verbose runs, then access is denied" {
  run_wire sync status --verbose
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}
