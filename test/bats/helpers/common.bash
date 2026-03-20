#!/usr/bin/env bash

setup_wire_test_env() {
	local base_dir
	base_dir="${BATS_TEST_TMPDIR:-/tmp}"
	export WIRE_TEST_ROOT
	WIRE_TEST_ROOT="${base_dir}/wire-cli-${BATS_SUITE_TEST_NUMBER:-0}-${BATS_TEST_NUMBER:-0}-$$"

	export HOME="${WIRE_TEST_ROOT}/home"
	export XDG_CONFIG_HOME="${HOME}/.config"
	export WIRE_SESSION_FILE="${XDG_CONFIG_HOME}/wire/session.json"

	# Keep test processes isolated from inherited shell state.
	export WIRE_BACKEND="stub"
	export WIRECLI_CONSOLE_LOG_LEVEL="OFF"
	unset WIRE_STUB_MODE
	unset WIRE_REAL_EMAIL
	unset WIRE_REAL_PASSWORD
	unset WIRE_REAL_SERVER

	mkdir -p "${XDG_CONFIG_HOME}/wire"
}

teardown_wire_test_env() {
	if [[ -n "${WIRE_TEST_ROOT:-}" && -d "${WIRE_TEST_ROOT}" ]]; then
		rm -rf "${WIRE_TEST_ROOT}"
	fi
}

run_wire() {
	run "${WIRE_BIN}" "$@"
}

file_mode_octal() {
	local target="$1"
	if stat -f "%Lp" "${target}" >/dev/null 2>&1; then
		stat -f "%Lp" "${target}"
	else
		stat -c "%a" "${target}"
	fi
}

assert_status() {
	local expected="$1"
	[ "${status}" -eq "${expected}" ]
}
