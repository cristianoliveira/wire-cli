#!/usr/bin/env python3
"""Wrapper to run bats tests with timeout to prevent indefinite hangs."""

import subprocess
import sys
import os

# Set WIRE_BIN environment variable
wire_bin = os.environ.get('WIRE_BIN')
if not wire_bin:
    # If not provided, calculate from script location
    # This script is at: /path/to/project/test/bats/run_bats_timeout.py
    # Project root is two levels up
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(script_dir))
    wire_bin = os.path.join(project_root, 'build/install/wire-cli/bin/wire-cli')

os.environ['WIRE_BIN'] = wire_bin

# Run bats with timeout
# The script is in test/bats/, so run.sh is in the same directory
script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'run.sh')

try:
    result = subprocess.run(
        ['bash', script_path],
        timeout=300,  # 5 minutes timeout
        stdin=subprocess.DEVNULL
    )
    sys.exit(result.returncode)
except subprocess.TimeoutExpired:
    sys.stderr.write("ERROR: Bats tests timed out after 5 minutes\n")
    sys.exit(124)  # Standard timeout exit code
