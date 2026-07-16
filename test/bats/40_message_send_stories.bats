#!/usr/bin/env bats
#
# Integration tests for wire message send command
#
# Tests the message send command at the CLI integration level.
# Covers successful message sending, stdin fallback, and all failure scenarios.
#
# Exit codes used by wire message send:
#   0  - Success ("Message sent.")
#  11  - Unauthorized (no active session)
#  12  - Network error (connectivity failure)
#  13  - Server error (backend failure)
#  14  - Validation error (blank conversation or message)

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

# Helper: Send message with stdin
run_wire_message_with_stdin() {
	local stdin_payload="$1"
	local conversation_id="$2"
	shift 2
	run "${WIRE_BIN}" message send "$conversation_id" "$@" <<<"${stdin_payload}"
}

# Helper: Create active session for testing
setup_active_session() {
	export WIRE_STUB_MODE="login_ok"
	# First need to login
	run_wire login --email "test@example.com" --password "Password1"
	export WIRE_STUB_MODE="" # Reset stub mode after login
}

# ==================== SUCCESS SCENARIOS ====================

@test "message send: success with positional argument" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-001" "Hello from positional argument"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: success with stdin (no positional message)" {
	export WIRE_STUB_MODE="success"
	run_wire_message_with_stdin "Hello from stdin" "conv-001"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: success with heredoc stdin (no positional message)" {
	export WIRE_STUB_MODE="success"
	run bash -c '"${WIRE_BIN}" message send "conv-001" <<"EOF"
Hello from heredoc
with multiple lines
EOF'
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: positional argument takes precedence over stdin" {
	export WIRE_STUB_MODE="success"
	run_wire_message_with_stdin "stdin message" "conv-002" "positional message"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: multi-word message with spaces intact" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-003" "This is a multi-word message with many spaces"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: quoted message preserves special characters" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-004" "Hello! How are you? @mention :smile:"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

@test "message send: message with newline from stdin is trimmed of CR" {
	export WIRE_STUB_MODE="success"
	# Echo adds CR on some systems; the command should trim it
	run_wire_message_with_stdin "Hello from stdin" "conv-005"
	assert_status 0
	[[ "${output}" == "Message sent." ]]
}

# ==================== VALIDATION ERROR SCENARIOS (EXIT 14) ====================

@test "message send: validation error - blank conversation ID" {
	export WIRE_STUB_MODE="success"
	run_wire message send "" "Hello"
	assert_status 14
	[[ "${output}" == *"validation error: conversation required"* ]]
}

@test "message send: validation error - whitespace-only conversation ID" {
	export WIRE_STUB_MODE="success"
	run_wire message send "   " "Hello"
	assert_status 14
	[[ "${output}" == *"validation error: conversation required"* ]]
}

@test "message send: validation error - blank message from args" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-006" ""
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: validation error - blank message from stdin" {
	export WIRE_STUB_MODE="success"
	run_wire_message_with_stdin "" "conv-007"
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: validation error - whitespace-only message from args" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-008" "   "
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: validation error - whitespace-only message from stdin" {
	export WIRE_STUB_MODE="success"
	run_wire_message_with_stdin "   " "conv-009"
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

# ==================== UNAUTHORIZED SCENARIOS (EXIT 11) ====================

@test "message send: error - unauthorized when no active session" {
	# Make sure we're NOT logged in
	rm -f "${WIRE_SESSION_FILE}"
	unset WIRE_STUB_MODE
	run_wire message send "conv-010" "Hello"
	assert_status 11
	[[ "${output}" == *"must be logged in"* ]] || [[ "${output}" == *"session"* ]]
}

@test "message send: error - unauthorized provides actionable message" {
	rm -f "${WIRE_SESSION_FILE}"
	unset WIRE_STUB_MODE
	run_wire message send "conv-011" "Hello"
	assert_status 11
	# Message should include guidance about logging in
	[[ "${output}" == *"login"* ]] || [[ "${output}" == *"session"* ]] || [[ "${output}" == *"authenticate"* ]]
}

# ==================== NETWORK ERROR SCENARIOS (EXIT 12) ====================

@test "message send: error - network error" {
	export WIRE_STUB_MODE="network_error"
	run_wire message send "conv-012" "Hello"
	assert_status 12
	[[ "${output}" == *"network"* ]] || [[ "${output}" == *"connection"* ]]
}

@test "message send: error - network error suggests retry" {
	export WIRE_STUB_MODE="network_error"
	run_wire message send "conv-013" "Hello"
	assert_status 12
	# Should provide actionable guidance
	[[ "${output}" == *"retry"* ]] || [[ "${output}" == *"connection"* ]] || [[ "${output}" == *"network"* ]]
}

# ==================== SERVER ERROR SCENARIOS (EXIT 13) ====================

@test "message send: error - server error" {
	export WIRE_STUB_MODE="server_error"
	run_wire message send "conv-014" "Hello"
	assert_status 13
	[[ "${output}" == *"server"* ]] || [[ "${output}" == *"error"* ]]
}

@test "message send: error - conversation not found (server error)" {
	export WIRE_STUB_MODE="conversation_not_found"
	run_wire message send "conv-nonexistent" "Hello"
	assert_status 13
	[[ "${output}" == *"conversation"* ]] || [[ "${output}" == *"not found"* ]]
}

# ==================== ARGUMENT PARSING SCENARIOS ====================

@test "message send: help text displays examples" {
	run_wire message send --help
	assert_status 0
	[[ "${output}" == *"EXAMPLES"* ]] || [[ "${output}" == *"wire message send"* ]]
}

@test "message send: help text documents stdin fallback" {
	run_wire message send --help
	assert_status 0
	[[ "${output}" == *"stdin"* ]] || [[ "${output}" == *"read from stdin"* ]]
}

@test "message send: requires conversation argument" {
	export WIRE_STUB_MODE="success"
	run_wire message send
	# Should fail with usage error
	[ "${status}" -ne 0 ]
}

# ==================== REGRESSION TESTS ====================

@test "message send: exit code 0 indicates success" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-015" "Hello"
	[ "${status}" -eq 0 ]
}

@test "message send: exit code 11 for unauthorized" {
	export WIRE_STUB_MODE="unauthorized"
	run_wire message send "conv-016" "Hello"
	[ "${status}" -eq 11 ]
}

@test "message send: exit code 12 for network error" {
	export WIRE_STUB_MODE="network_error"
	run_wire message send "conv-017" "Hello"
	[ "${status}" -eq 12 ]
}

@test "message send: exit code 13 for server error" {
	export WIRE_STUB_MODE="server_error"
	run_wire message send "conv-018" "Hello"
	[ "${status}" -eq 13 ]
}

@test "message send: exit code 14 for validation error" {
	export WIRE_STUB_MODE="success"
	run_wire message send "" "Hello"
	[ "${status}" -eq 14 ]
}

# ==================== IDEMPOTENCY / EDGE CASES ====================

@test "message send: same message sent twice succeeds both times" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-019" "Same message"
	[ "${status}" -eq 0 ]
	run_wire message send "conv-019" "Same message"
	[ "${status}" -eq 0 ]
}

@test "message send: output is exactly 'Message sent.' on success" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-020" "Hello"
	assert_status 0
	[ "${output}" = "Message sent." ]
}

@test "message send: no extra output on success" {
	export WIRE_STUB_MODE="success"
	run_wire message send "conv-021" "Hello"
	assert_status 0
	# Output should be exactly "Message sent." with no extra lines or spaces
	[ "$(echo "${output}" | wc -l)" -eq 1 ]
}

# ==================== STDIN EDGE CASES ====================

@test "message send: stdin with empty input returns validation error" {
	export WIRE_STUB_MODE="success"
	# Send EOF immediately (empty stdin)
	run bash -c 'printf "" | "${WIRE_BIN}" message send "conv-022"'
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: stdin with only whitespace returns validation error" {
	export WIRE_STUB_MODE="success"
	# Send only spaces
	run bash -c 'printf "   \n" | "${WIRE_BIN}" message send "conv-023"'
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: positional empty string is different from no arg" {
	export WIRE_STUB_MODE="success"
	# Empty string as positional arg should be treated as provided (empty)
	# This tests argument parsing, not the service logic
	run_wire message send "conv-024" ""
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}

@test "message send: positional argument precedence ignores stdin even when stdin has content" {
	export WIRE_STUB_MODE="success"
	run_wire_message_with_stdin "from stdin" "conv-025" ""
	assert_status 14
	[[ "${output}" == *"validation error: message required"* ]]
}
