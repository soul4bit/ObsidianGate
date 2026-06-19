#!/bin/sh
set -eu

SCREEN_SESSION="${SCREEN_SESSION:-mc-rpg-manual}"
CONSOLE_DELAY="${CONSOLE_DELAY:-0}"

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <minecraft-command>" >&2
    exit 2
fi

command="$*"
printf '%s\n' "$command"
screen -S "$SCREEN_SESSION" -p 0 -X stuff "$command$(printf '\r')"

if [ "$CONSOLE_DELAY" != "0" ]; then
    sleep "$CONSOLE_DELAY"
fi
