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

@test "message list: --no-cache still lists messages" {
	run_wire message list --no-cache
	assert_status 0
	[[ "${lines[0]}" == *"@"* ]]
	[[ "${lines[0]}" == *"("* ]]
}

@test "message list: json output uses a list envelope" {
	run_wire message list --json
	assert_status 0
	[[ "$(jq -r '.returned == (.items | length)' <<<"${output}")" == "true" ]]
	[[ "$(jq -r '.total' <<<"${output}")" == "null" ]]
	[[ "$(jq -r '.truncated' <<<"${output}")" == "false" ]]
	[[ -n "$(jq -r '.items[0].conversationId' <<<"${output}")" ]]
	[[ -n "$(jq -r '.items[0].conversationName' <<<"${output}")" ]]
	[[ -n "$(jq -r '.items[0].messageId' <<<"${output}")" ]]
}

@test "message list: since filters older messages" {
	run_wire message list --json --since "2026-03-20T10:01:00Z"
	assert_status 0
	[[ "$(jq -r '[.items[].timestamp >= "2026-03-20T10:01:00Z"] | all' <<<"${output}")" == "true" ]]
}

@test "message list: conversation-id scopes every result" {
	run_wire message list --json --conversation-id "conv-dm-001"
	assert_status 0
	[[ "$(jq -r '.returned' <<<"${output}")" == "2" ]]
	[[ "$(jq -r '[.items[].conversationId == "conv-dm-001"] | all' <<<"${output}")" == "true" ]]
}

@test "message list: mentions-me returns explicit self mentions" {
	run_wire message list --json --mentions-me
	assert_status 0
	[[ "$(jq -r '.returned > 0' <<<"${output}")" == "true" ]]
	[[ "$(jq -r '[.items[].mentionsSelf] | all' <<<"${output}")" == "true" ]]
}

@test "message list: invalid since value is rejected" {
	run_wire message list --since "yesterday-ish"
	assert_status 2
	[[ "${output}" == *"validation error: since must be 'today', an ISO-8601 date, or an ISO-8601 timestamp"* ]]
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

@test "message list: help documents limit bound and defaults" {
	run_wire message list --help
	assert_status 0
	[[ "${output}" == *"limit"* ]]
	[[ "${output}" == *"1"* ]]
	[[ "${output}" == *"100"* ]]
	[[ "${output}" == *"default"* ]]
	[[ "${output}" == *"--no-cache"* ]]
	[[ "${output}" == *"--since"* ]]
	[[ "${output}" == *"--conversation-id"* ]]
	[[ "${output}" == *"--mentions-me"* ]]
	[[ "${output}" != *"--local"* ]]
}
