# Conversation Guidance

Own conversation list/get/search/create/delete, membership data, display models, and formatting.

- Keep conversation contracts free of Kalium SDK types.
- `ConversationFormatter` owns reusable conversation presentation; commands choose output mode.
- Real runtime/adapter handles Kalium flow collection and translation.
- Session-backed service resolves active auth; auth guard maps unavailable auth.
- Message send/fetch/watch behavior belongs in `message/`, not here.

Tests belong in `src/test/kotlin/wirecli/conversation/`. Cover contract mapping, formatter output, service success/failure, missing session, and deterministic stub behavior.
