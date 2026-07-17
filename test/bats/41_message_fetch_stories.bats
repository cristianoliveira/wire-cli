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
	setup_active_session
}

teardown() {
	teardown_wire_test_env
}

setup_active_session() {
	export WIRE_STUB_MODE="login_ok"
	run_wire login --email "test@example.com" --password "Password1"
	export WIRE_STUB_MODE=""
}

@test "message fetch: success returns stable human-readable lines" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch "conv-001"
	assert_status 0
	[[ "${lines[0]}" == "[2026-03-20T10:00:00Z] Alice: Hello from stub" ]]
	[[ "${lines[1]}" == "[2026-03-20T10:01:00Z] Bob: Reply from stub" ]]
}

@test "message fetch: no-cache forces server-backed fetch" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch --no-cache "conv-001"
	assert_status 0
	[[ "${lines[0]}" == "[2026-03-20T10:00:00Z] Alice: Hello from stub" ]]
	[[ "${lines[1]}" == "[2026-03-20T10:01:00Z] Bob: Reply from stub" ]]
}

@test "message fetch: validation error for blank conversation id" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch ""
	assert_status 14
	[[ "${output}" == *"validation error: conversation required"* ]]
}

@test "message fetch: unauthorized when no active session" {
	rm -f "${WIRE_SESSION_FILE}"
	unset WIRE_STUB_MODE
	run_wire message fetch "conv-001"
	assert_status 11
	[[ "${output}" == *"session"* ]] || [[ "${output}" == *"logged in"* ]]
}

@test "message fetch: network error maps to exit 12" {
	export WIRE_STUB_MODE="network_error"
	run_wire message fetch "conv-001"
	assert_status 12
	[[ "${output}" == *"network"* ]]
}

@test "message fetch: server error maps to exit 13" {
	export WIRE_STUB_MODE="server_error"
	run_wire message fetch "conv-001"
	assert_status 13
	[[ "${output}" == *"server"* ]]
}

@test "message fetch: conversation not found maps to exit 13" {
	export WIRE_STUB_MODE="conversation_not_found"
	run_wire message fetch "conv-missing"
	assert_status 13
	[[ "${output}" == *"conversation not found"* ]]
}

@test "message fetch: requires conversation argument" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch
	[ "${status}" -ne 0 ]
}
