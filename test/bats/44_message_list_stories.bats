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
	export WIRE_STUB_MODE="success"
}

teardown() {
	teardown_wire_test_env
}

@test "message list: success with default human output" {
	run_wire message list
	assert_status 0
	[[ "${lines[0]}" == *"@"* ]]
	[[ "${lines[0]}" == *"("* ]]
}

@test "message list: json output includes conversation fields" {
	run_wire message list --json
	assert_status 0
	[[ "${output}" == *'"conversationId"'* ]]
	[[ "${output}" == *'"conversationName"'* ]]
	[[ "${output}" == *'"messageId"'* ]]
}

@test "message list: json-lines output emits one object per line" {
	run_wire message list --json-lines --limit 2
	assert_status 0
	[ "${#lines[@]}" -eq 2 ]
	[[ "${lines[0]}" == \{*\} ]]
	[[ "${lines[1]}" == \{*\} ]]
}

@test "message list: validation error for non-positive limit" {
	run_wire message list --limit 0
	assert_status 2
	[[ "${output}" == *"validation error: limit must be between 1 and 100"* ]]
}
