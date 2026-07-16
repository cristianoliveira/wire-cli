# Message Guidance

Own send/fetch/watch/search/delete/reaction/typing behavior, message views, timeout handling, and failure mapping.

- Keep message contracts and operation results independent of Kalium types.
- Centralize repeated authenticated operation handling in message service helpers without hiding result translation.
- Real runtime/adapter owns flows, SDK calls, and cancellation boundaries.
- Format fetched messages in `MessageFetchFormatter`; commands select plain/JSON/JSON-lines mode.
- Bound waits and watches. Invalid or non-positive configured timeouts must fall back safely.
- Conversation lifecycle belongs in `conversation/`.

Tests belong in `src/test/kotlin/wirecli/message/`. Cover success/failure for every operation, timeout/cancellation, flow termination, output formatting, missing auth, and deterministic stubs.
