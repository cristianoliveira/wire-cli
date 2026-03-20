#!/usr/bin/env bash

set -euo pipefail

WIRE=${WIRE_CLI:-wire-cli}

# Similar to inotifywait, but for Wire messages.
# Streams new messages and reacts to each one.

CONVERSATION_ID=${1?"Usage: $0 <conversation-id>"}

# Validate if pi is installed
if ! command -v pi &>/dev/null; then
	echo "pi is not installed. Please install it first."
	exit 1
fi

pi @.agent/SOUL.md "ping" --print </dev/null >response.md

echo "response: $(cat response.md)"

"$WIRE" message watch "$CONVERSATION_ID" | while IFS= read -r MESSAGE; do
	echo "received: $MESSAGE"

	# return if contain 'Morty' (case insensitive)
	if [[ "$MESSAGE" =~ "morty" || "$MESSAGE" =~ "Morty" ]]; then
		echo "Morty found in '$MESSAGE'"

		## Multiline response with typing indicator
		pi --continue "$MESSAGE" --print </dev/null >response.md &
		response_pid=$!

		"$WIRE" message typing "$CONVERSATION_ID" --while-pid "$response_pid" 2>/dev/null
		wait "$response_pid"

		# get the output of the markdown file
		echo "response: $(cat response.md)"

		"$WIRE" message send "$CONVERSATION_ID" <response.md 2>/dev/null
	fi
done
