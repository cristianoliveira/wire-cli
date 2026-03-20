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
	setup_active_session
}

teardown() {
	teardown_wire_test_env
}

setup_active_session() {
	export WIRE_STUB_MODE="login_ok"
	run_wire login --email "test@example.com" --password "password"
	export WIRE_STUB_MODE=""
}

@test "message typing: started without auto-stop succeeds" {
	export WIRE_STUB_MODE="success"
	run_wire message typing "conv-001" --state started --auto-stop-seconds 0
	assert_status 0
	[[ "${output}" == *"Typing started."* ]]
}

@test "message typing: stopped succeeds" {
	export WIRE_STUB_MODE="success"
	run_wire message typing "conv-001" --state stopped
	assert_status 0
	[[ "${output}" == *"Typing stopped."* ]]
}

@test "message typing: while-pid keeps typing until process exits" {
	export WIRE_STUB_MODE="success"
	sleep 5 &
	pid=$!
	run_wire message typing "conv-001" --state started --while-pid "$pid" --auto-stop-seconds 1
	assert_status 0
	[[ "${output}" == *"Typing started."* ]]
	[[ "${output}" == *"Typing stopped."* ]]
}

@test "message typing: network error maps to exit 12" {
	export WIRE_STUB_MODE="network_error"
	run_wire message typing "conv-001" --state stopped
	assert_status 12
	[[ "${output}" == *"typing"* ]]
}

@test "message typing: blank conversation validation error" {
	export WIRE_STUB_MODE="success"
	run_wire message typing "" --state stopped
	assert_status 14
	[[ "${output}" == *"validation error: conversation required"* ]]
}
