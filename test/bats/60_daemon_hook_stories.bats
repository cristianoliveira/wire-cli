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
  if [[ -n "${daemon_pid:-}" ]]; then
    kill "${daemon_pid}" 2>/dev/null || true
    wait "${daemon_pid}" 2>/dev/null || true
  fi
  teardown_wire_test_env
}

@test "Given ongoing-call hook, when daemon receives call, then hook receives call environment" {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "CorrectHorse1"
  assert_status 0

  export HOOK_OUTPUT="${WIRE_TEST_ROOT}/ongoing-call.env"
  mkdir -p "${XDG_CONFIG_HOME}/wire/hooks"
  cat >"${XDG_CONFIG_HOME}/wire/hooks/ongoing-call.sh" <<'HOOK'
#!/usr/bin/env bash
printf '%s\n%s\n%s\n%s\n' \
  "${WIRE_HOOK_EVENT}" \
  "${WIRE_CALL_STATUS}" \
  "${WIRE_CALL_CONVERSATION_ID}" \
  "${WIRE_CALLER_ID}" >"${HOOK_OUTPUT}"
HOOK
  chmod +x "${XDG_CONFIG_HOME}/wire/hooks/ongoing-call.sh"

  export WIRE_STUB_MODE="ongoing_call"
  "${WIRE_BIN}" daemon >"${WIRE_TEST_ROOT}/daemon.stdout" 2>"${WIRE_TEST_ROOT}/daemon.stderr" &
  daemon_pid=$!

  for _ in $(seq 1 50); do
    [[ -s "${HOOK_OUTPUT}" ]] && break
    sleep 0.1
  done

  [[ -s "${HOOK_OUTPUT}" ]]
  expected=$'ongoing-call\nincoming\nstub-conversation@example.com\nstub-caller@example.com'
  [ "$(cat "${HOOK_OUTPUT}")" = "${expected}" ]
}
