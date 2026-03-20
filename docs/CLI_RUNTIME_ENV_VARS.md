# CLI Runtime Environment Variables

Use these environment variables to tune runtime behavior without changing command flags.

## Message Send Timeout

`WIRECLI_MESSAGE_SEND_TIMEOUT_MS` controls how long `wire message send` waits for a send operation to finish.

- Default: `60000` ms
- Max: `300000` ms (higher values are clamped)
- Invalid values (`non-numeric` or `<=0`) fall back to the default (`60000`)

Example:

```bash
WIRECLI_MESSAGE_SEND_TIMEOUT_MS=120000 nix run .# -- message send --conversation-id <conversation-id> --text "hello"
```
