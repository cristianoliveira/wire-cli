#!/usr/bin/env bash
set -euo pipefail

# Force non-interactive mode and explicit I/O handling
# This helps prevent hangs when running through Gradle Exec in certain environments

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if [[ -z "${WIRE_BIN:-}" ]]; then
	WIRE_BIN="${PROJECT_ROOT}/build/install/wire-cli/bin/wire-cli"
fi

export WIRE_BIN

# Run bats with explicit I/O redirection to prevent hanging
# Using < /dev/null to ensure stdin is closed
# Using -x trace to help debug if needed (disabled by default)
# exec bats --print-output-on-failure -x "${PROJECT_ROOT}/test/bats" < /dev/null
exec bats --print-output-on-failure "${PROJECT_ROOT}/test/bats" </dev/null
