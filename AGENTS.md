# Agent Instructions

This is a wire CLI to interact with wire cli

## Version management

version: v0.0.1-beta

  - This project is still in development, so before v1.0.0, we don't need
    - Migrations/Deprecation plans from one feature to another
    - Deprecations of commands or features.local
    - We can do breaking changes.

## How to interact with wire

Check:
  DO NOT USE THIS AS DEPENDENCY
  .local/wiretui/ -- A full tui implemantation that connect with wire server

## Debugging and logging

- Default: console logs are OFF (quiet terminal); logs are still written to `~/.cache/wire-cli/logs`.
- Enable console debugging:
  - `--verbose` enables DEBUG console logs
  - `--log-level <LEVEL>` (e.g. `TRACE`, `DEBUG`) sets console log level
  - `WIRECLI_CONSOLE_LOG_LEVEL=<LEVEL>` sets console log level (default: `OFF`)
  - `WIRECLI_LOG_LEVEL=<LEVEL>` sets file log level
- Inspect log files:
  - `ls ~/.cache/wire-cli/logs`
  - `tail -f ~/.cache/wire-cli/logs/*.log`
- Keep stdout clean for scripting:
  - console logs (when enabled) go to stderr; redirect as needed (e.g. `wire-cli ... 2>wire-cli.debug.log`)
  - `--json` / `--json-lines` keep console logs OFF so stdout stays machine-readable

## Runtime env vars

- `WIRECLI_MESSAGE_SEND_TIMEOUT_MS=<milliseconds>` overrides message-send timeout.
  - Default: `60000`
  - Max: `300000` (values above are clamped)
  - Invalid/non-numeric/<=0 values fall back to default.

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
