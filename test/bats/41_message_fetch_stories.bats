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

@test "message fetch: json output is structured and preserves content" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch --json "conv-001"
	assert_status 0
	[[ "$(jq -r '.conversationId' <<<"${output}")" == "conv-001" ]]
	[[ "$(jq -r '.returned' <<<"${output}")" == "2" ]]
	[[ "$(jq -r '.truncated' <<<"${output}")" == "false" ]]
	[[ "$(jq -r '.items[0].messageId' <<<"${output}")" == "msg-001" ]]
	[[ "$(jq -r '.items[0].mentionsSelf' <<<"${output}")" == "true" ]]
}

@test "message fetch: limit returns latest fetched messages" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch --json --limit 1 "conv-001"
	assert_status 0
	[[ "$(jq -r '.returned' <<<"${output}")" == "1" ]]
	[[ "$(jq -r '.truncated' <<<"${output}")" == "true" ]]
	[[ "$(jq -r '.items[0].messageId' <<<"${output}")" == "msg-002" ]]
}

@test "message fetch: validation error for non-positive limit" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch --limit 0 "conv-001"
	assert_status 2
	[[ "${output}" == *"validation error: limit must be between 1 and 100"* ]]
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
	assert_status 2
	[[ "${output}" == *"validation error: conversation required"* ]]
}

@test "message fetch: unauthorized when no active session" {
	rm -f "${WIRE_SESSION_FILE}"
	unset WIRE_STUB_MODE
	run_wire message fetch "conv-001"
	assert_status 1
	[[ "${output}" == *"session"* ]] || [[ "${output}" == *"logged in"* ]]
}

@test "message fetch: network error maps to exit 12" {
	export WIRE_STUB_MODE="network_error"
	run_wire message fetch "conv-001"
	assert_status 1
	[[ "${output}" == *"network"* ]]
}

@test "message fetch: server error maps to exit 13" {
	export WIRE_STUB_MODE="server_error"
	run_wire message fetch "conv-001"
	assert_status 1
	[[ "${output}" == *"server"* ]]
}

@test "message fetch: conversation not found maps to exit 13" {
	export WIRE_STUB_MODE="conversation_not_found"
	run_wire message fetch "conv-missing"
	assert_status 1
	[[ "${output}" == *"conversation not found"* ]]
}

@test "message fetch: requires conversation argument" {
	export WIRE_STUB_MODE="success"
	run_wire message fetch
	[ "${status}" -ne 0 ]
}
