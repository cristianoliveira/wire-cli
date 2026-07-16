#!/usr/bin/env bats
#
# Integration tests for wire message delete command
#
# Tests the message delete command at the CLI integration level.
# Covers local delete (for me), remote delete (for everyone), JSON output,
# and all failure scenarios.
#
# Exit codes used by wire message delete:
#   0  - Success
#  11  - Unauthorized (no active session)
#  12  - Network error (connectivity failure)
#  13  - Server error (backend failure)
#  14  - Validation error (blank conversation or message id)

load "helpers/common.bash"

setup_file() {
	if [[ -z "${WIRE_BIN:-}" ]]; then
		WIRE_BIN="${BATS_TEST_DIRNAME}/../../build/install/wire/bin/wire"
	fi
	export WIRE_BIN
}

setup() {
	setup_wire_test_env
	setup_active_session
}

teardown() {
	teardown_wire_test_env
}

# Helper: Create active session for testing
setup_active_session() {
	export WIRE_STUB_MODE="login_ok"
	# First need to login
	run_wire login --email "test@example.com" --password "Password1"
	export WIRE_STUB_MODE="" # Reset stub mode after login
}

# ==================== SUCCESS SCENARIOS ====================

@test "message delete: success with default scope (for me)" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" "msg-001"
	assert_status 0
	[[ "${output}" == "Message deleted." ]]
}

@test "message delete: success with --for-everyone" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" "msg-001" --for-everyone
	assert_status 0
	[[ "${output}" == "Message deleted for everyone." ]]
}

@test "message delete: success with --json outputs for_me by default" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" "msg-001" --json
	assert_status 0
	[[ "${output}" == *"scope"* ]]
	[[ "${output}" == *"for_me"* ]]
}

@test "message delete: success with --json and --for-everyone outputs for_everyone" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" "msg-001" --for-everyone --json
	assert_status 0
	[[ "${output}" == *"for_everyone"* ]]
}

# ==================== VALIDATION ERROR SCENARIOS (EXIT 14) ====================

@test "message delete: validation error - blank conversation ID" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "" "msg-001"
	assert_status 14
	[[ "${output}" == *"validation error: conversation-id required"* ]]
}

@test "message delete: validation error - whitespace-only conversation ID" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "   " "msg-001"
	assert_status 14
	[[ "${output}" == *"validation error: conversation-id required"* ]]
}

@test "message delete: validation error - blank message ID" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" ""
	assert_status 14
	[[ "${output}" == *"validation error: message-id required"* ]]
}

@test "message delete: validation error - whitespace-only message ID" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-001" "   "
	assert_status 14
	[[ "${output}" == *"validation error: message-id required"* ]]
}

# ==================== UNAUTHORIZED SCENARIO (EXIT 11) ====================

@test "message delete: error - unauthorized when no active session" {
	rm -f "${WIRE_SESSION_FILE}"
	unset WIRE_STUB_MODE
	run_wire message delete "conv-010" "msg-010"
	assert_status 11
	[[ "${output}" == *"must be logged in"* ]] || [[ "${output}" == *"session"* ]]
}

# ==================== NETWORK ERROR SCENARIO (EXIT 12) ====================

@test "message delete: error - network error" {
	export WIRE_STUB_MODE="network_error"
	run_wire message delete "conv-012" "msg-012"
	assert_status 12
	[[ "${output}" == *"network"* ]]
}

# ==================== SERVER ERROR SCENARIO (EXIT 13) ====================

@test "message delete: error - server error" {
	export WIRE_STUB_MODE="server_error"
	run_wire message delete "conv-014" "msg-014"
	assert_status 13
	[[ "${output}" == *"server"* ]]
}

@test "message delete: error - conversation not found" {
	export WIRE_STUB_MODE="conversation_not_found"
	run_wire message delete "conv-nonexistent" "msg-001"
	assert_status 13
	[[ "${output}" == *"conversation"* ]]
}

# ==================== HELP TEXT ====================

@test "message delete: help text displays examples" {
	run_wire message delete --help
	assert_status 0
	[[ "${output}" == *"EXAMPLES"* ]]
	[[ "${output}" == *"--for-everyone"* ]]
}

@test "message delete: help text documents default scope" {
	run_wire message delete --help
	assert_status 0
	[[ "${output}" == *"for you"* ]] || [[ "${output}" == *"local"* ]]
}

# ==================== EXIT CODE REGRESSIONS ====================

@test "message delete: exit code 0 for success" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-015" "msg-015"
	[ "${status}" -eq 0 ]
}

@test "message delete: exit code 12 for network error" {
	export WIRE_STUB_MODE="network_error"
	run_wire message delete "conv-016" "msg-016"
	[ "${status}" -eq 12 ]
}

@test "message delete: exit code 13 for server error" {
	export WIRE_STUB_MODE="server_error"
	run_wire message delete "conv-017" "msg-017"
	[ "${status}" -eq 13 ]
}

@test "message delete: exit code 14 for validation error" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "" "msg-018"
	[ "${status}" -eq 14 ]
}

# ==================== OUTPUT EXACTNESS ====================

@test "message delete: no extra output on success" {
	export WIRE_STUB_MODE="success"
	run_wire message delete "conv-019" "msg-019"
	assert_status 0
	[ "$(echo "${output}" | wc -l)" -eq 1 ]
}
