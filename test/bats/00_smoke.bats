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
}

teardown() {
  teardown_wire_test_env
}

@test "Given built CLI, when running --help, then usage is printed" {
  run_wire --help

  assert_status 0
  [[ "${output}" == *"Usage:"* ]]
  [[ "${output}" == *"presence"* ]]
  [[ "${output}" == *"daemon"* ]]
  [[ "${output}" == *"backup"* ]]
}

@test "Given built CLI, when backup import help is requested, then source format is documented" {
  run_wire backup import --help

  assert_status 0
  [[ "${output}" == *"--from"* ]]
  [[ "${output}" == *"wire-backup"* ]]
}

@test "Given built CLI, when message fetch help is requested, then cache bypass is documented" {
  run_wire message fetch --help

  assert_status 0
  [[ "${output}" == *"--no-cache"* ]]
  [[ "${output}" != *"--local"* ]]
}
