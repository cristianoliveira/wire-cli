#!/usr/bin/env bash

set -e

# Similar to inotifywait, but for Wire messages

CONVERSATIONID=${1?"Usage: $0 <conversation-id>"}
MESSAGE=""

while MESSAGE=$(wire-cli message watch $CONVERSATIONID); do
  echo "$MESSAGE"
done
