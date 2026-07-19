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

@test "Given built CLI, when no arguments are passed, then compact live state is shown" {
  run_wire

  assert_status 0
  [[ "${output}" == *"wire:"* ]]
  [[ "${output}" == *"next:"* ]]
  # output must not be the full help (should be compact)
  [[ "${#lines[@]}" -le 5 ]]
}

@test "Given built CLI, when running --help, then root description covers full CLI scope" {
  run_wire --help

  assert_status 0
  [[ "${output}" == *"conversation"* || "${output}" == *"Conversation"* ]]
  [[ "${output}" == *"message"* || "${output}" == *"Message"* ]]
  [[ "${output}" == *"device"* || "${output}" == *"Device"* ]]
  [[ "${output}" == *"sync"* || "${output}" == *"health"* || "${output}" == *"doctor"* ]]
}

@test "Given command group has no subcommand, when command fails, then error and usage are printed" {
  run_wire user

  assert_status 2
  [[ "${output}" == *"Error: no subcommand specified"* ]]
  [[ "${output}" == *"Usage: wire user"* ]]
  [[ "${output}" == *"Commands:"* ]]
  [[ "${output}" == *"search"* ]]
  [[ "${output}" == *"get"* ]]
}

@test "Given unknown option, when parsing fails, then usage exit code is returned" {
  run_wire --bogus

  assert_status 2
  [[ "${output}" == *"Error: no such option --bogus"* ]]
}

@test "Given command arguments are missing, when command fails, then error and usage are printed" {
  run_wire message send

  assert_status 2
  [[ "${output}" == *"Error: missing argument <conversation>"* ]]
  [[ "${output}" == *"Usage: wire message send"* ]]
  [[ "${output}" == *"<conversation>"* ]]
  [[ "${output}" == *"[<message>]"* ]]
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
