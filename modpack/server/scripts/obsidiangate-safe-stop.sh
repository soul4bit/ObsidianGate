#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
RCON_COMMAND="${RCON_COMMAND:-$SERVER_ROOT/scripts/obsidiangate-rcon-command.sh}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

if [ ! -x "$RCON_COMMAND" ]; then
    log "RCON command helper not executable: $RCON_COMMAND"
    exit 1
fi

log "Saving world before shutdown..."
"$RCON_COMMAND" "save-all" >/dev/null || log "WARN: save-all failed before shutdown"
sleep 2

log "Stopping Minecraft server via RCON..."
"$RCON_COMMAND" "stop" >/dev/null

deadline=$(( $(date +%s) + WAIT_SECONDS ))
while [ "$(date +%s)" -lt "$deadline" ]; do
    if ! "$RCON_COMMAND" "list" >/dev/null 2>&1; then
        log "Minecraft RCON is offline; shutdown command was accepted."
        exit 0
    fi
    sleep 2
done

log "ERROR: Minecraft did not stop within ${WAIT_SECONDS}s"
exit 1
