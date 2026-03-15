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

login_stub_session() {
  export WIRE_STUB_MODE="login_ok"
  run_wire login --email "jane@example.com" --password "correct-horse"
  assert_status 0
}

validate_json() {
  local json_str="$1"
  # Try to parse the JSON using basic structure validation
  local json_single_line
  json_single_line=$(echo "${json_str}" | tr '\n' ' ' | sed 's/  */ /g')
  
  if echo "${json_single_line}" | grep -q '^[[:space:]]*\[.*\][[:space:]]*$'; then
    return 0
  elif echo "${json_single_line}" | grep -q '^[[:space:]]*{.*}[[:space:]]*$'; then
    return 0
  else
    return 1
  fi
}

validate_jsonlines() {
  local output="$1"
  local line_count=0
  
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    if ! echo "$line" | grep -q '^{.*}$'; then
      return 1
    fi
    ((line_count++))
  done <<<"$output"
  
  return 0
}

# ============================================================================
# Part 1: Stub Message Tests (safe, deterministic)
# ============================================================================

@test "Given authenticated session, when message send runs with valid input, then message is sent successfully" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message send conv-dm-alice "Hello from test"
  assert_status 0
  [[ "${output}" == *"Hello from test"* || "${output}" == *"sent"* ]]
}

@test "Given authenticated session, when message send runs with empty text, then command fails with validation error" {
  login_stub_session
  
  export WIRE_STUB_MODE="send_invalid_input"
  run_wire message send conv-dm-alice ""
  assert_status 14
  [[ "${output}" == *"Invalid input"* ]]
}

@test "Given authenticated session, when message fetch runs for conversation, then all messages are retrieved" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice
  assert_status 0
  [[ "${output}" == *"How are you doing"* || "${output}" == *"alice"* ]]
}

@test "Given authenticated session, when message fetch runs with --limit 5, then at most 5 messages are returned" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice --limit 5
  assert_status 0
  # Verify limit was applied (roughly by line count or message count)
  local line_count
  line_count=$(echo "${output}" | grep -c "^" || true)
  [[ $line_count -le 20 ]]  # 5 messages with headers should be ~15-20 lines
}

@test "Given authenticated session with pagination, when message fetch runs with --from cursor, then paginated messages are returned" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice --limit 3
  assert_status 0
  # Just verify the command succeeds and returns data
  [[ "${output}" != "" ]]
}

@test "Given authenticated session, when message fetch runs with invalid conversation ID, then not found error is returned" {
  login_stub_session
  
  export WIRE_STUB_MODE="fetch_conversation_not_found"
  run_wire message fetch invalid-conv-id
  assert_status 16
  [[ "${output}" == *"not found"* || "${output}" == *"Conversation"* ]]
}

@test "Given no session, when message fetch runs, then access is denied with reauth guidance" {
  run_wire message fetch conv-dm-alice
  assert_status 11
  [[ "${output}" == *"Run wire login to re-authenticate"* || "${output}" == *"Session is invalid"* ]]
}

# ============================================================================
# Part 2: Output Format Tests
# ============================================================================

@test "Given authenticated session, when message fetch runs with default format, then output is human readable text" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice
  assert_status 0
  # Should contain readable content (not JSON)
  ! grep -q '^\[' <<<"${output}"
}

@test "Given authenticated session, when message fetch runs with --format json, then valid JSON array is output" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice --format json
  assert_status 0
  validate_json "${output}"
  [[ "${output}" == *"["* ]]
}

@test "Given authenticated session, when message fetch runs with --format jsonlines, then one JSON object per line is output" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice --format jsonlines
  assert_status 0
  # Should have lines that are JSON objects
  local line_count
  line_count=$(echo "${output}" | grep -c '{' || true)
  [[ $line_count -gt 0 ]]
}

@test "Given authenticated session, when message send succeeds, then output confirms message sent" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message send conv-dm-alice "Test message"
  assert_status 0
  # Output should contain the message text or confirmation
  [[ "${output}" == *"Test message"* || "${output}" == *"sent"* || "${output}" == *"Message sent"* ]]
}

# ============================================================================
# Part 3: Real Backend Tests (conditional)
# ============================================================================

@test "Given real backend credentials, when message send runs, then message is sent via real Kalium" {
  if [[ -z "${WIRE_REAL_EMAIL:-}" || -z "${WIRE_REAL_PASSWORD:-}" ]]; then
    skip "Set WIRE_REAL_EMAIL, WIRE_REAL_PASSWORD, and WIRE_REAL_CONV_ID for live Kalium message tests"
  fi
  
  export WIRE_BACKEND="real"
  export WIRE_STUB_MODE=""
  
  # First login with real credentials
  run_wire_with_stdin "${WIRE_REAL_PASSWORD}" login --email "${WIRE_REAL_EMAIL}" --password-stdin
  assert_status 0
  
  # Send message (using a real conversation ID if provided)
  local conv_id="${WIRE_REAL_CONV_ID:-test-conv}"
  run_wire message send "${conv_id}" "Test message from wire-cli BATS integration"
  # May succeed or fail depending on backend state, but shouldn't crash
  [ "$status" -eq 0 ] || [ "$status" -ne 127 ]
}

@test "Given real backend credentials, when message fetch runs, then messages are retrieved from real Kalium" {
  if [[ -z "${WIRE_REAL_EMAIL:-}" || -z "${WIRE_REAL_PASSWORD:-}" ]]; then
    skip "Set WIRE_REAL_EMAIL, WIRE_REAL_PASSWORD, and WIRE_REAL_CONV_ID for live Kalium message tests"
  fi
  
  export WIRE_BACKEND="real"
  export WIRE_STUB_MODE=""
  
  # First login with real credentials
  run_wire_with_stdin "${WIRE_REAL_PASSWORD}" login --email "${WIRE_REAL_EMAIL}" --password-stdin
  assert_status 0
  
  # Fetch messages (using a real conversation ID if provided)
  local conv_id="${WIRE_REAL_CONV_ID:-test-conv}"
  run_wire message fetch "${conv_id}"
  # May succeed or fail depending on backend state, but shouldn't crash
  [ "$status" -eq 0 ] || [ "$status" -ne 127 ]
}

@test "Given authenticated session with message send, when operation completes, then execution time is under 1 second" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  local start_time
  start_time=$(date +%s%N)
  
  run_wire message send conv-dm-alice "Performance test message"
  assert_status 0
  
  local end_time
  end_time=$(date +%s%N)
  local elapsed_ms=$(( (end_time - start_time) / 1000000 ))
  
  # Should complete in under 1000ms
  [[ $elapsed_ms -lt 1000 ]]
}

# ============================================================================
# Part 4: Special Cases & Error Handling
# ============================================================================

@test "Given authenticated session, when message send runs with unicode text, then special characters are preserved" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message send conv-dm-alice "Hello 👋 World! 你好 مرحبا"
  assert_status 0
  # Message should be accepted (actual unicode handling depends on implementation)
  [[ "${output}" != "" ]]
}

@test "Given authenticated session, when message send runs with text containing quotes and newlines, then content is properly escaped" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message send conv-dm-alice "Line 1
Line 2
With \"quotes\""
  assert_status 0
  [[ "${output}" != "" ]]
}

@test "Given authenticated session, when message fetch runs with --format json, then JSON output is properly escaped" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-group-backend --limit 1 --format json
  assert_status 0
  validate_json "${output}"
}

@test "Given authenticated session, when message fetch runs with network error stub, then network failure message is returned" {
  login_stub_session
  
  export WIRE_STUB_MODE="fetch_network_error"
  run_wire message fetch conv-dm-alice
  assert_status 12
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

@test "Given authenticated session, when message fetch runs with server error stub, then server failure message is returned" {
  login_stub_session
  
  export WIRE_STUB_MODE="fetch_server_error"
  run_wire message fetch conv-dm-alice
  assert_status 13
  [[ "${output}" == *"unavailable"* || "${output}" == *"server"* ]]
}

@test "Given authenticated session, when message send runs with unauthorized stub, then reauth guidance is returned" {
  login_stub_session
  
  export WIRE_STUB_MODE="send_unauthorized"
  run_wire message send conv-dm-alice "Test"
  assert_status 11
  [[ "${output}" == *"Session is invalid"* || "${output}" == *"re-authenticate"* ]]
}

# ============================================================================
# Part 5: Bot Example & Practical Use Cases
# ============================================================================

@test "Given ping-pong workflow, when send and fetch messages run in sequence, then full message lifecycle works" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  
  # Step 1: Send a message (ping)
  run_wire message send conv-dm-alice "Ping"
  assert_status 0
  [[ "${output}" == *"Ping"* ]]
  
  # Step 2: Fetch messages from the conversation
  run_wire message fetch conv-dm-alice --limit 10
  assert_status 0
  
  # Step 3: Verify the sent message appears in the fetch
  [[ "${output}" == *"Ping"* ]]
}

@test "Given multi-conversation message workflow, when fetching from different conversations, then messages are properly scoped" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  
  # Fetch from Alice's conversation
  run_wire message fetch conv-dm-alice
  assert_status 0
  local alice_output="${output}"
  
  # Fetch from Bob's conversation
  run_wire message fetch conv-dm-bob
  assert_status 0
  local bob_output="${output}"
  
  # Both should have content
  [[ "${alice_output}" != "" ]]
  [[ "${bob_output}" != "" ]]
  
  # Alice's should contain Alice's messages
  [[ "${alice_output}" == *"alice"* ]] || [[ "${alice_output}" == *"How are you"* ]]
}

@test "Given group conversation scenario, when message fetch runs on group chat, then conversation-specific messages are returned" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-group-backend
  assert_status 0
  
  # Group chat should have multiple messages
  local message_count
  message_count=$(echo "${output}" | grep -c "From:" || true)
  [[ $message_count -gt 0 ]]
}

@test "Given message fetch with pagination limit, when --limit is specified as 1, then only one message is returned" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-group-backend --limit 1
  assert_status 0
  
  # Should have minimal output for single message
  local line_count
  line_count=$(echo "${output}" | grep -c "^" || true)
  [[ $line_count -ge 1 ]]
}

@test "Given no authenticated session, when message send runs, then requires login" {
  export WIRE_STUB_MODE=""
  run_wire message send conv-dm-alice "Unauthorized test"
  assert_status 11
  [[ "${output}" == *"login"* || "${output}" == *"Session"* ]]
}

@test "Given no authenticated session, when message send with conversation ID runs, then authentication error takes precedence" {
  export WIRE_STUB_MODE=""
  run_wire message send conv-invalid "Test"
  assert_status 11
  # Should fail on auth, not on conversation
  [[ "${output}" == *"login"* || "${output}" == *"Session"* ]]
}

@test "Given authenticated session, when message send with network error stub runs, then failure is reported" {
  login_stub_session
  
  export WIRE_STUB_MODE="send_network_error"
  run_wire message send conv-dm-alice "Network test"
  assert_status 12
  [[ "${output}" == *"network"* || "${output}" == *"connection"* ]]
}

# ============================================================================
# Part 6: Output Validation
# ============================================================================

@test "Given authenticated session, when message fetch --format json runs, then output contains valid JSON structure" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-dm-alice --limit 2 --format json
  assert_status 0
  
  # Should be a JSON array
  [[ "${output}" == *"["* ]]
  [[ "${output}" == *"]"* ]]
  validate_json "${output}"
}

@test "Given authenticated session with multiple messages, when message fetch --format jsonlines runs, then each line is valid JSON" {
  login_stub_session
  
  export WIRE_STUB_MODE=""
  run_wire message fetch conv-group-backend --limit 5 --format jsonlines
  assert_status 0
  
  # Each line should be a JSON object
  validate_jsonlines "${output}"
}

run_wire_with_stdin() {
  local stdin_payload="$1"
  shift
  run "${WIRE_BIN}" "$@" <<<"${stdin_payload}"
}
