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

@test "Given built CLI, when running --help, then usage is printed" {
  run_wire --help

  assert_status 0
  [[ "${output}" == *"Usage:"* ]]
  [[ "${output}" == *"presence"* ]]
}
