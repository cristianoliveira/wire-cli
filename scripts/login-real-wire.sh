#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
env_file="${repo_root}/.env"

if [[ -f "${env_file}" ]]; then
	set -a
	# shellcheck disable=SC1090
	source "${env_file}"
	set +a
else
	echo "[warn] .env not found at ${env_file}; using current environment variables."
fi

required_vars=(WIRE_EMAIL WIRE_PASSWORD)
missing_vars=()
for var_name in "${required_vars[@]}"; do
	if [[ -z "${!var_name:-}" ]]; then
		missing_vars+=("${var_name}")
	fi
done

if ((${#missing_vars[@]} > 0)); then
	echo "[error] Missing required environment variables: ${missing_vars[*]}" >&2
	echo "        Define them in .env or export them before running this script." >&2
	exit 1
fi

wire_bin="${WIRE_BIN:-${repo_root}/build/install/wire-cli/bin/wire-cli}"
if [[ ! -x "${wire_bin}" ]]; then
	echo "[error] wire-cli executable not found at ${wire_bin}" >&2
	echo "        Build/install first: ./gradlew installDist" >&2
	exit 1
fi

server_args=()
if [[ -n "${WIRE_SERVER:-}" ]]; then
	server_args=(--server "${WIRE_SERVER}")
fi

echo "[info] Attempting login against real Wire backend..."
if printf '%s\n' "${WIRE_PASSWORD}" | WIRE_BACKEND=real "${wire_bin}" login --email "${WIRE_EMAIL}" --password-stdin "${server_args[@]}"; then
	echo "[ok] Login succeeded. You can now run: WIRE_BACKEND=real ${wire_bin} profile"
else
	exit_code=$?
	echo "[error] Login failed (exit ${exit_code}). Check credentials, backend reachability, or WIRE_SERVER." >&2
	exit "${exit_code}"
fi
