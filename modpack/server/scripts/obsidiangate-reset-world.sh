#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
PROPERTIES_FILE="${PROPERTIES_FILE:-$SERVER_ROOT/server.properties}"
BACKUP_ROOT="${BACKUP_ROOT:-$SERVER_ROOT/backups/world-resets}"
RCON_COMMAND="${RCON_COMMAND:-$SERVER_ROOT/scripts/obsidiangate-rcon-command.sh}"
ROAD_BUILDER="${ROAD_BUILDER:-$SERVER_ROOT/scripts/obsidiangate-build-spawn-roads.sh}"
SPAWN_BUILDER="${SPAWN_BUILDER:-$SERVER_ROOT/scripts/obsidiangate-build-new-spawn.sh}"
BUILD_SPAWN="${BUILD_SPAWN:-0}"

SPAWN_X="${SPAWN_X:-484}"
SPAWN_SURFACE_Y="${SPAWN_SURFACE_Y:-70}"
SPAWN_Z="${SPAWN_Z:--823}"
ROAD_LENGTH="${ROAD_LENGTH:-240}"

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
    log "ERROR: $*" >&2
    exit 1
}

usage() {
    cat >&2 <<EOF
Usage:
  $0 prepare --confirm-wipe [new-world-name]
  $0 decorate [spawn-x surface-y spawn-z protection-radius]

prepare must run while Minecraft is stopped. It archives the active world and
switches server.properties level-name to a new world.

decorate must run after the new world starts. It sets /setworldspawn, disables
mob block griefing, protects a radius around spawn and reloads spawn protection.
Set BUILD_SPAWN=1 only when you explicitly want the legacy RCON-built hub.
EOF
}

is_safe_name() {
    case "$1" in
        ""|.|..|*/*|*\\*|*:*|*[!A-Za-z0-9._-]*)
            return 1
            ;;
        *)
            return 0
            ;;
    esac
}

is_int() {
    case "$1" in
        ""|-) return 1 ;;
        -*) case "${1#-}" in ""|*[!0-9]*) return 1 ;; *) return 0 ;; esac ;;
        *[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

read_level_name() {
    [ -f "$PROPERTIES_FILE" ] || fail "Missing server.properties: $PROPERTIES_FILE"
    level_name="$(sed -n 's/^level-name=//p' "$PROPERTIES_FILE" | tail -n 1 | tr -d '\r')"
    if [ -z "$level_name" ]; then
        level_name="world"
    fi
    is_safe_name "$level_name" || fail "Unsafe current level-name: $level_name"
    printf '%s\n' "$level_name"
}

next_world_name() {
    base="world-season-$(date '+%Y%m%d')"
    candidate="$base"
    suffix=2
    while [ -e "$SERVER_ROOT/$candidate" ]; do
        candidate="$base-v$suffix"
        suffix=$((suffix + 1))
    done
    printf '%s\n' "$candidate"
}

set_level_name() {
    new_world="$1"
    tmp_file="$(mktemp "$PROPERTIES_FILE.tmp.XXXXXX")"
    awk -v new_world="$new_world" '
        BEGIN { written = 0 }
        /^level-name=/ {
            print "level-name=" new_world
            written = 1
            next
        }
        { print }
        END {
            if (!written) {
                print "level-name=" new_world
            }
        }
    ' "$PROPERTIES_FILE" > "$tmp_file"
    mv "$tmp_file" "$PROPERTIES_FILE"
}

ensure_server_stopped() {
    if [ -x "$RCON_COMMAND" ] && "$RCON_COMMAND" "list" >/dev/null 2>&1; then
        fail "Minecraft RCON is online. Stop the server before prepare."
    fi
    if pgrep -f "$SERVER_ROOT/server.jar" >/dev/null 2>&1; then
        fail "Minecraft process is still running. Stop the server before prepare."
    fi
}

archive_world() {
    world_name="$1"
    world_dir="$SERVER_ROOT/$world_name"
    if [ ! -d "$world_dir" ]; then
        log "Current world directory does not exist yet, skipping archive: $world_dir"
        return
    fi

    timestamp="$(date '+%Y%m%d-%H%M%S')"
    archive_path="$BACKUP_ROOT/$world_name-before-reset-$timestamp.tar.gz"
    install -d "$BACKUP_ROOT"
    log "Archiving current world to $archive_path"
    tar -C "$SERVER_ROOT" -czf "$archive_path" "$world_name"
    log "World archive complete: $archive_path"
}

prepare_world() {
    if [ "${1:-}" != "--confirm-wipe" ]; then
        usage
        fail "prepare requires --confirm-wipe"
    fi
    shift

    ensure_server_stopped

    current_world="$(read_level_name)"
    new_world="${1:-${NEW_WORLD_NAME:-}}"
    if [ -z "$new_world" ]; then
        new_world="$(next_world_name)"
    fi
    is_safe_name "$new_world" || fail "Unsafe new world name: $new_world"
    if [ "$new_world" = "$current_world" ]; then
        fail "New world name must differ from current level-name: $current_world"
    fi
    if [ -e "$SERVER_ROOT/$new_world" ]; then
        fail "New world path already exists: $SERVER_ROOT/$new_world"
    fi

    archive_world "$current_world"
    set_level_name "$new_world"

    cat > "$SERVER_ROOT/.obsidiangate-last-world-reset" <<EOF
previous_world=$current_world
new_world=$new_world
prepared_at=$(date '+%Y-%m-%d %H:%M:%S')
spawn=$SPAWN_X,$SPAWN_SURFACE_Y,$SPAWN_Z
protection_radius=$ROAD_LENGTH
EOF

    log "Prepared new world: $new_world"
    log "Start Minecraft, then run: $0 decorate $SPAWN_X $SPAWN_SURFACE_Y $SPAWN_Z $ROAD_LENGTH"
}

run_rcon() {
    [ -x "$RCON_COMMAND" ] || fail "RCON command helper not executable: $RCON_COMMAND"
    "$RCON_COMMAND" "$@"
}

decorate_world() {
    x="${1:-$SPAWN_X}"
    surface_y="${2:-$SPAWN_SURFACE_Y}"
    z="${3:-$SPAWN_Z}"
    length="${4:-$ROAD_LENGTH}"

    for value in "$x" "$surface_y" "$z" "$length"; do
        is_int "$value" || fail "Expected integer coordinate/length, got: $value"
    done

    player_y=$((surface_y + 1))

    log "Setting world spawn to $x $player_y $z"
    run_rcon "setworldspawn $x $player_y $z" >/dev/null

    log "Disabling mob block griefing"
    run_rcon "gamerule mobGriefing false" >/dev/null || log "WARN: could not set mobGriefing"

    case "$BUILD_SPAWN" in
        1|true|True|TRUE|yes|Yes|YES)
            [ -x "$SPAWN_BUILDER" ] || fail "Spawn builder not executable: $SPAWN_BUILDER"
            log "Building legacy ObsidianGate spawn hub"
            "$SPAWN_BUILDER" "$x" "$surface_y" "$z" "$length"
            ;;
        *)
            log "Skipping automatic spawn build. Paste the WorldEdit schematic where you want it."
            ;;
    esac

    protection_radius="$length"
    if [ "$protection_radius" -lt 64 ]; then
        protection_radius=64
    fi
    min_x=$((x - protection_radius))
    max_x=$((x + protection_radius))
    min_z=$((z - protection_radius))
    max_z=$((z + protection_radius))

    log "Protecting spawn region dim 0 [$min_x,0,$min_z]..[$max_x,255,$max_z]"
    run_rcon "spawnprotect region $min_x 0 $min_z $max_x 255 $max_z 0" >/dev/null

    log "Reloading spawn protection"
    run_rcon "spawnprotect reload" >/dev/null

    log "Saving world after decoration"
    run_rcon "save-all" >/dev/null || log "WARN: save-all failed after decoration"
    run_rcon "spawnprotect info" || true
}

command="${1:-}"
case "$command" in
    prepare)
        shift
        prepare_world "$@"
        ;;
    decorate)
        shift
        decorate_world "$@"
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        usage
        exit 2
        ;;
esac
