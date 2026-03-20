#!/usr/bin/env bash

set -euo pipefail

WIRE_CLI=${WIRE_CLI:-wire-cli}

# Similar to inotifywait, but for Wire messages.
# Streams new messages and reacts to each one.

CONVERSATION_ID=${1?"Usage: $0 <conversation-id>"}

# Validate if pi is installed
if ! command -v pi &> /dev/null; then
	echo "pi is not installed. Please install it first."
	exit 1
fi

pi @.agent/SOUL.md "ping" --print </dev/null >response.md

echo "response: $(cat response.md)"

"$WIRE_CLI" message watch "$CONVERSATION_ID" | while IFS= read -r MESSAGE; do
	echo "received: $MESSAGE"

	# return if not contain Morty
	if [[ "$MESSAGE" =~ Morty ]]; then
		echo "Morty found in '$MESSAGE'"

		pi --continue "$MESSAGE" --print </dev/null >response.md

		# get the output of the markdown file
		RESPONSE=$(cat response.md)

		echo "response: $RESPONSE"

		## Multiline response
		cat response.md | "$WIRE_CLI" message send "$CONVERSATION_ID" 2>/dev/null
	fi
done
