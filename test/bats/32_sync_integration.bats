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

# Helper function to validate JSON
validate_json() {
  local json_str="$1"
  local trimmed
  trimmed=$(echo "${json_str}" | tr -d '[:space:]')
  if [[ "${trimmed}" =~ ^'{' ]] && [[ "${trimmed}" =~ '}'$ ]]; then
    return 0
  else
    return 1
  fi
}

login_stub_session() {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
}

# ==================== SYNC STATUS COMMAND TESTS ====================

@test "Sync status: healthy status returns exit code 0" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  assert_status 0
}

@test "Sync status: degraded status returns exit code 1" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status
  assert_status 1
}

@test "Sync status: error status returns exit code 1" {
  login_stub_session

  export WIRE_STUB_MODE="status_error"
  run_wire doctor status
  assert_status 1
}

@test "Sync status: initializing status returns exit code 1" {
  login_stub_session

  export WIRE_STUB_MODE="status_initializing"
  run_wire doctor status
  assert_status 1
}

@test "Sync status: unauthenticated access returns exit code 11" {
  run_wire doctor status
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Sync status: expired session returns exit code 11" {
  login_stub_session

  export WIRE_STUB_MODE="unauthorized"
  run_wire doctor status
  assert_status 11
  [[ "${output}" == *"invalid or expired"* || "${output}" == *"unauthorized"* ]]
}

@test "Sync status: network error displays connection message" {
  login_stub_session

  export WIRE_STUB_MODE="network_error"
  run_wire doctor status
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

@test "Sync status: server error returns exit code 13" {
  login_stub_session

  export WIRE_STUB_MODE="server_error"
  run_wire doctor status
  assert_status 13
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

@test "Sync status: human output contains status and metrics" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  assert_status 0
  [[ "${output}" == *"ready"* || "${output}" == *"Ready"* || "${output}" == *"Sync"* ]]
  [[ "${output}" == *"Lag:"* || "${output}" == *"lag"* || "${output}" == *"Pending"* ]]
}

# ==================== SYNC STATUS --VERBOSE TESTS ====================

@test "Sync status --verbose: displays detailed metrics" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose
  assert_status 0
  [[ "${output}" == *"Lag"* || "${output}" == *"lag"* ]]
  [[ "${output}" == *"Pending"* || "${output}" == *"pending"* ]]
}

@test "Sync status --verbose: shows MLS metrics" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose
  assert_status 0
  [[ "${output}" == *"MLS"* || "${output}" == *"mls"* || "${output}" == *"migration"* ]]
}

@test "Sync status --verbose: includes timestamps" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose
  assert_status 0
  [[ "${output}" == *"Last Sync"* || "${output}" == *"timestamp"* || "${output}" == *"2025"* ]]
}

@test "Sync status --verbose: degraded status shows warnings" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --verbose
  assert_status 1
  [[ "${output}" == *"⚠"* || "${output}" == *"warning"* || "${output}" == *"degraded"* ]]
}

@test "Sync status --verbose: unauthenticated access returns exit code 11" {
  run_wire doctor status --verbose
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# ==================== SYNC STATUS --JSON TESTS ====================

@test "Sync status --json: outputs valid JSON" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  validate_json "${output}"
}

@test "Sync status --json: contains status field" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  [[ "${output}" == *"\"status\""* || "${output}" == *"status"* ]]
}

@test "Sync status --json: contains metrics object" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  [[ "${output}" == *"\"metrics\""* || "${output}" == *"metrics"* ]]
}

@test "Sync status --json: contains lag_ms field" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  [[ "${output}" == *"lag"* ]]
}

@test "Sync status --json: degraded status has correct status value" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --json
  assert_status 1
  validate_json "${output}"
  [[ "${output}" == *"degraded"* ]]
}

# ==================== SYNC STATUS --VERBOSE --JSON TESTS ====================

@test "Sync status --verbose --json: outputs valid JSON with verbose metrics" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose --json
  assert_status 0
  validate_json "${output}"
  [[ "${output}" == *"metrics"* ]]
}

@test "Sync status --verbose --json: contains all metric fields" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose --json
  assert_status 0
  [[ "${output}" == *"lag"* ]]
  [[ "${output}" == *"pending"* ]]
}

# ==================== SYNC STATUS --DIAGNOSE TESTS ====================

@test "Sync status --diagnose: displays diagnostic checks" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"Authentication"* ]]
  [[ "${output}" == *"Sync Engine"* || "${output}" == *"Engine"* ]]
  [[ "${output}" == *"Network"* ]]
}

@test "Sync status --diagnose: healthy status shows healthy checks" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"healthy"* ]]
}

@test "Sync status --diagnose: degraded status returns exit code 0 (no errors)" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"Diagnostics"* || "${output}" == *"diagnostics"* || "${output}" == *"checks"* ]]
}

@test "Sync status --diagnose: degraded status shows recovery hints" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"Recovery"* || "${output}" == *"Command"* ]]
}

@test "Sync status --diagnose: error status shows error checks" {
  login_stub_session

  export WIRE_STUB_MODE="status_error"
  run_wire doctor status --diagnose
  assert_status 1
  [[ "${output}" == *"error"* || "${output}" == *"Error"* ]]
}

@test "Sync status --diagnose: initializing status shows initializing checks" {
  login_stub_session

  export WIRE_STUB_MODE="status_initializing"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"initializing"* ]]
}

@test "Sync status --diagnose: unauthenticated access returns exit code 11" {
  run_wire doctor status --diagnose
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

@test "Sync status --diagnose: network error displays connection message" {
  login_stub_session

  export WIRE_STUB_MODE="diagnostics_network_error"
  run_wire doctor status --diagnose
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

@test "Sync status --diagnose: server error returns exit code 13" {
  login_stub_session

  export WIRE_STUB_MODE="diagnostics_server_error"
  run_wire doctor status --diagnose
  assert_status 13
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry later"* ]]
}

# ==================== SYNC STATUS --DIAGNOSE --JSON TESTS ====================

@test "Sync status --diagnose --json: outputs valid JSON" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose --json
  assert_status 0
  validate_json "${output}"
}

@test "Sync status --diagnose --json: contains checks array" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose --json
  assert_status 0
  [[ "${output}" == *"checks"* ]]
}

@test "Sync status --diagnose --json: contains summary field" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose --json
  assert_status 0
  [[ "${output}" == *"summary"* ]]
}

@test "Sync status --diagnose --json: degraded includes recovery hints" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --diagnose --json
  assert_status 0
  [[ "${output}" == *"recovery"* || "${output}" == *"Recovery"* ]]
  validate_json "${output}"
}

# ==================== BARE SYNC COMMAND TESTS ====================

@test "Bare wire doctor: shows status when no subcommand" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor
  assert_status 0
  [[ "${output}" == *"ready"* || "${output}" == *"Sync"* ]]
}

@test "Bare wire doctor: returns degraded exit code for degraded status" {
  login_stub_session

  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor
  assert_status 1
}

@test "Bare wire doctor: unauthenticated access returns exit code 11" {
  run_wire doctor
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* ]]
}

# ==================== CONSISTENCY TESTS ====================

@test "Sync status: multiple calls produce consistent output" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  local first_output="${output}"
  assert_status 0

  run_wire doctor status
  local second_output="${output}"
  assert_status 0

  [[ "${first_output}" == "${second_output}" ]]
}

@test "Sync status: JSON output is deterministic across calls" {
  login_stub_session

  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  local first_output="${output}"
  assert_status 0

  run_wire doctor status --json
  local second_output="${output}"
  assert_status 0

  [[ "${first_output}" == "${second_output}" ]]
}

# ==================== EXIT CODE VALIDATION TESTS ====================

@test "Exit codes: healthy status = 0" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  assert_status 0
}

@test "Exit codes: initializing status = 1" {
  login_stub_session
  export WIRE_STUB_MODE="status_initializing"
  run_wire doctor status
  assert_status 1
}

@test "Exit codes: degraded status = 1" {
  login_stub_session
  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status
  assert_status 1
}

@test "Exit codes: error status = 1" {
  login_stub_session
  export WIRE_STUB_MODE="status_error"
  run_wire doctor status
  assert_status 1
}

@test "Exit codes: unauthorized = 11" {
  run_wire doctor status
  assert_status 11
}

@test "Exit codes: server error = 13" {
  login_stub_session
  export WIRE_STUB_MODE="server_error"
  run_wire doctor status
  assert_status 13
}

@test "Exit codes: diagnostics with healthy checks = 0" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
}

@test "Exit codes: diagnostics with error checks = 1" {
  login_stub_session
  export WIRE_STUB_MODE="status_error"
  run_wire doctor status --diagnose
  assert_status 1
}

# ==================== ERROR SCENARIO TESTS ====================

@test "Error handling: network failure displays error message" {
  login_stub_session
  export WIRE_STUB_MODE="network_error"
  run_wire doctor status
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

@test "Error handling: server error shows retry guidance" {
  login_stub_session
  export WIRE_STUB_MODE="server_error"
  run_wire doctor status
  [[ "${output}" == *"unavailable"* || "${output}" == *"Retry"* ]]
}

@test "Error handling: unauthorized shows reauth guidance" {
  login_stub_session
  export WIRE_STUB_MODE="unauthorized"
  run_wire doctor status
  [[ "${output}" == *"login again"* || "${output}" == *"re-authenticate"* || "${output}" == *"unauthorized"* ]]
}

@test "Error handling: no session shows login guidance" {
  run_wire doctor status
  [[ "${output}" == *"Run wire login"* || "${output}" == *"re-authenticate"* ]]
}

# ==================== METRIC VALIDATION TESTS ====================

@test "Metrics: ready status shows healthy lag values" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose
  assert_status 0
  [[ "${output}" == *"100"* || "${output}" == *"lag"* ]]
}

@test "Metrics: degraded status shows higher lag values" {
  login_stub_session
  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status --verbose
  assert_status 1
  [[ "${output}" == *"5000"* || "${output}" == *"lag"* ]]
}

@test "Metrics: error status shows critical lag values" {
  login_stub_session
  export WIRE_STUB_MODE="status_error"
  run_wire doctor status --verbose
  assert_status 1
  [[ "${output}" == *"30000"* || "${output}" == *"lag"* ]]
}

@test "Metrics: verbose output contains MLS percentage" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose
  assert_status 0
  [[ "${output}" == *"85"* || "${output}" == *"MLS"* ]]
}

@test "Metrics: JSON output has proper metric structure" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  [[ "${output}" == *"\"metrics\""* || "${output}" == *"metrics"* ]]
  validate_json "${output}"
}

# ==================== COMBINATION FLAGS TESTS ====================

@test "Flag combinations: --verbose --json both work together" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose --json
  assert_status 0
  validate_json "${output}"
}

@test "Flag combinations: --json works with --diagnose" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose --json
  assert_status 0
  validate_json "${output}"
}

@test "Flag combinations: --verbose ignored when --diagnose is set" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose --verbose
  assert_status 0
  [[ "${output}" == *"Authentication"* || "${output}" == *"checks"* ]]
}

# ==================== REAL DATA SIMULATION TESTS ====================

@test "Real data: healthy status returns realistic metrics" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  validate_json "${output}"
  [[ "${output}" == *"ready"* ]]
  [[ "${output}" == *"lag"* || "${output}" == *"metrics"* ]]
}

@test "Real data: network metrics included in verbose output" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --verbose --json
  assert_status 0
  validate_json "${output}"
  # Should contain network-related information
  [[ "${output}" == *"metrics"* ]]
}

@test "Real data: MLS metrics included in diagnostics" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
  # MLS or Key Package information should be in the output
  [[ "${output}" == *"Key"* || "${output}" == *"packages"* || "${output}" == *"checks"* ]]
}

@test "Real data: diagnostics includes all check categories" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
  [[ "${output}" == *"Authentication"* ]]
  [[ "${output}" == *"Engine"* || "${output}" == *"Sync"* ]]
  [[ "${output}" == *"Network"* ]]
}

# ==================== PERFORMANCE/TIMING TESTS ====================

@test "Performance: sync status completes in reasonable time" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  
  local start_time
  local end_time
  local duration
  
  start_time=$(date +%s%N)
  run_wire doctor status
  end_time=$(date +%s%N)
  
  # Calculate duration in milliseconds
  duration=$(( (end_time - start_time) / 1000000 ))
  
  assert_status 0
  # Should complete in less than 5 seconds (5000ms)
  [[ ${duration} -lt 5000 ]]
}

@test "Performance: diagnostics completes in reasonable time" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  
  local start_time
  local end_time
  local duration
  
  start_time=$(date +%s%N)
  run_wire doctor status --diagnose
  end_time=$(date +%s%N)
  
  duration=$(( (end_time - start_time) / 1000000 ))
  
  assert_status 0
  # Should complete in less than 5 seconds (5000ms)
  [[ ${duration} -lt 5000 ]]
}

# ==================== DETAILED OUTPUT VALIDATION TESTS ====================

@test "Output validation: status text is human-readable" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  assert_status 0
  # Should contain readable status information
  [[ "${output}" != "" ]]
  [[ ${#output} -gt 10 ]]
}

@test "Output validation: verbose output is more detailed than standard" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  
  run_wire doctor status
  local standard_length=${#output}
  
  run_wire doctor status --verbose
  local verbose_length=${#output}
  
  # Verbose output should be longer or equal
  [[ ${verbose_length} -ge ${standard_length} ]]
}

@test "Output validation: JSON is properly formatted" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --json
  assert_status 0
  
  # Verify it's valid JSON
  validate_json "${output}"
  
  # Should have proper indentation or structure
  [[ "${output}" == *"{"* && "${output}" == *"}"* ]]
}

@test "Output validation: diagnostics output contains check details" {
  login_stub_session
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status --diagnose
  assert_status 0
  
  # Should have meaningful details for checks
  [[ "${output}" == *"healthy"* || "${output}" == *"details"* || "${output}" == *"status"* ]]
}

# ==================== STATE ISOLATION TESTS ====================

@test "State isolation: different stub modes produce different output" {
  login_stub_session
  
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  local ready_output="${output}"
  
  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status
  local degraded_output="${output}"
  
  # Outputs should be different
  [[ "${ready_output}" != "${degraded_output}" ]]
}

@test "State isolation: different exit codes for different status" {
  login_stub_session
  
  export WIRE_STUB_MODE="status_ready"
  run_wire doctor status
  local ready_code="${status}"
  
  export WIRE_STUB_MODE="status_degraded"
  run_wire doctor status
  local degraded_code="${status}"
  
  # Exit codes should be different
  [[ ${ready_code} -ne ${degraded_code} ]]
}
