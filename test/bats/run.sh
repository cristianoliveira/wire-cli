#!/usr/bin/env bash
set -euo pipefail

if ! command -v bats >/dev/null 2>&1; then
	printf 'bats is required. Install it first (e.g. nix develop, brew install bats-core).\n' >&2
	exit 127
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if [[ -z "${WIRE_BIN:-}" ]]; then
	WIRE_BIN="${PROJECT_ROOT}/build/install/wire/bin/wire"
fi

export WIRE_BIN

bats --print-output-on-failure "${PROJECT_ROOT}/test/bats"
