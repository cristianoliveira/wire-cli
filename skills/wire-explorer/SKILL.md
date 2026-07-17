---
name: wire-explorer
description: >
  Find account, conversation, message, user, device, presence, backup, and sync information in Wire through wire-cli.
  Use when user asks to investigate Wire data, inspect a conversation, find messages or users, check account health, or explore locally cached Wire information.
---

# Wire Explorer

## Objective

Answer questions about Wire by selecting narrowest read-only `wire` command, preferring structured output, and reporting evidence without exposing secrets.

## Workflow

1. Verify CLI is available with `command -v wire`.
2. Check login state with `wire me`, using bounded timeout and capturing stdout, stderr, and exit status:
   - `wire me` is read-only alias for `wire profile`.
   - Success displaying Name/Email/Handle/Presence confirms user is logged in.
   - Unauthorized or "no active session" means user is not logged in or session expired.
   - Network/server failure or timeout is inconclusive; report it without inspecting internal files.
   - Do not use `wire logout` as a probe because it logs user out.
3. Discover exact command contract with `wire --help`, then `<group> --help` and `<command> --help`. Do not guess flags.
4. Start narrow. Resolve names to IDs before querying dependent resources.
5. Prefer `--json` or `--json-lines` when supported. Otherwise preserve stable text output.
6. If current data may be stale, explain cache/sync tradeoff before choosing:
   - `wire message fetch <conversation-id>` synchronizes before reading.
   - `wire message fetch --local <conversation-id>` reads locally available messages only.
   - `wire doctor status` inspects sync state; do not run `doctor sync` because it mutates state.
7. Run command with a timeout. Capture exit status and stderr separately when practical.
8. Summarize answer, command used, freshness limitations, and unresolved IDs or authentication failures.

## Read-only Routes

- Current account and primary login probe: `wire me` (`wire profile` is equivalent)
- Presence: `wire presence get`
- Conversations: `wire conversation list`, `search`, `get`
- Messages: `wire message fetch`, `fetch --local`, `search`, `watch`
- Users: `wire user search`, `get`
- Devices: `wire device list`, `info`
- Health: `wire doctor status`, `diagnose`
- Backups/local data: inspect via `wire backup --help`; avoid import/create
- Assets: `wire download` writes a file, so ask for explicit intent and output path first
- Diagnostics: use CLI global options `--verbose` or `--log-level`

## Guardrails

- Use only `wire` commands. Do not inspect Wire session files, databases, caches, source code, or other implementation details.
- Never expose cookies, tokens, passwords, or sensitive diagnostic output.
- Stay read-only unless user explicitly requests mutation. Never implicitly send/delete/react, update profile/presence, manage connections/devices, import/create backups, or force sync.
- Treat `watch` as long-running; always use bounded timeout or user-provided stop condition.
- Human-readable message fetch has no JSON mode. Message search and watch support structured output.
- Distinguish no results from command failure using exit status and stderr.

## Validation

Before answering, verify:

- CLI exists and login state was checked through `wire me`;
- command exists and arguments came from current help;
- successful authenticated operation confirmed session validity;
- result belongs to requested account/conversation/user;
- local versus synchronized freshness is stated;
- no secret or unnecessary personal data is included;
- no mutating command ran without explicit request.
