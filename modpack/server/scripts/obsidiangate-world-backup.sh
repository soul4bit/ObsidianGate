#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
WORLD_NAME="${WORLD_NAME:-world}"
SERVICE_NAME="${SERVICE_NAME:-mc-rpg.service}"
BACKUP_ROOT="${BACKUP_ROOT:-$SERVER_ROOT/backups/world}"
RETENTION="${RETENTION:-7}"
STOP_SERVER="${STOP_SERVER:-auto}"
BACKUP_OWNER="${BACKUP_OWNER:-minecraft:minecraft}"
LOCK_FILE="${LOCK_FILE:-/tmp/obsidiangate-world-backup.lock}"

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
    log "ERROR: $*" >&2
    exit 1
}

case "$WORLD_NAME" in
    ""|*/*)
        fail "Invalid WORLD_NAME: $WORLD_NAME"
        ;;
esac

case "$RETENTION" in
    ""|*[!0-9]*)
        fail "RETENTION must be a positive number"
        ;;
esac

if [ "$RETENTION" -lt 1 ]; then
    fail "RETENTION must be at least 1"
fi

WORLD_DIR="$SERVER_ROOT/$WORLD_NAME"
if [ ! -d "$WORLD_DIR" ]; then
    fail "World directory not found: $WORLD_DIR"
fi

if command -v flock >/dev/null 2>&1; then
    exec 9>"$LOCK_FILE"
    if ! flock -n 9; then
        log "Backup already running, skipping."
        exit 0
    fi
fi

mkdir -p "$BACKUP_ROOT"
BACKUP_ROOT_REAL="$(cd "$BACKUP_ROOT" && pwd -P)"
WORLD_DIR_REAL="$(cd "$WORLD_DIR" && pwd -P)"

case "$BACKUP_ROOT_REAL" in
    "$WORLD_DIR_REAL"|"$WORLD_DIR_REAL"/*)
        fail "BACKUP_ROOT must not be inside the world directory"
        ;;
esac

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
STAGING_DIR="$BACKUP_ROOT/.staging/world-$TIMESTAMP"
STAGING_WORLD_DIR="$STAGING_DIR/$WORLD_NAME"
ARCHIVE_PATH="$BACKUP_ROOT/world-$TIMESTAMP.tar.gz"
TMP_ARCHIVE_PATH="$ARCHIVE_PATH.tmp"
SERVER_STOPPED=0

cleanup() {
    status=$?
    if [ "$SERVER_STOPPED" = "1" ]; then
        SERVER_STOPPED=0
        log "Starting $SERVICE_NAME after failed backup..."
        if ! systemctl start "$SERVICE_NAME"; then
            log "ERROR: failed to start $SERVICE_NAME" >&2
            status=1
        fi
    fi
    rm -rf "$STAGING_DIR" "$TMP_ARCHIVE_PATH"
    exit "$status"
}

trap cleanup EXIT INT TERM

should_stop_server() {
    if [ "$STOP_SERVER" = "0" ] || [ "$STOP_SERVER" = "false" ]; then
        return 1
    fi

    if [ "$STOP_SERVER" = "1" ] || [ "$STOP_SERVER" = "true" ]; then
        return 0
    fi

    [ "$(id -u)" = "0" ] && command -v systemctl >/dev/null 2>&1
}

stop_server_if_needed() {
    if ! should_stop_server; then
        log "Online backup mode: $SERVICE_NAME will not be stopped."
        return
    fi

    if systemctl is-active --quiet "$SERVICE_NAME"; then
        log "Stopping $SERVICE_NAME for consistent world copy..."
        systemctl stop "$SERVICE_NAME"
        SERVER_STOPPED=1
    else
        log "$SERVICE_NAME is not active, copying world without restart."
    fi
}

start_server_if_needed() {
    if [ "$SERVER_STOPPED" = "1" ]; then
        log "Starting $SERVICE_NAME..."
        systemctl start "$SERVICE_NAME"
        SERVER_STOPPED=0
    fi
}

copy_world_to_staging() {
    mkdir -p "$STAGING_DIR"
    if command -v rsync >/dev/null 2>&1; then
        mkdir -p "$STAGING_WORLD_DIR"
        rsync -a --delete "$WORLD_DIR/" "$STAGING_WORLD_DIR/"
    else
        cp -a "$WORLD_DIR" "$STAGING_DIR/"
    fi
}

validate_playerdata() {
    playerdata_dir="$STAGING_WORLD_DIR/playerdata"
    invalid_list="$STAGING_DIR/invalid-playerdata.txt"
    rm -f "$invalid_list"

    if [ ! -d "$playerdata_dir" ]; then
        fail "No playerdata directory found in staged world copy"
    fi

    find "$playerdata_dir" -type f -name '*.dat' | while IFS= read -r player_file; do
        if [ ! -s "$player_file" ]; then
            printf '%s: empty file\n' "$player_file" >> "$invalid_list"
        elif ! gzip -t "$player_file" >/dev/null 2>&1; then
            printf '%s: invalid gzip data\n' "$player_file" >> "$invalid_list"
        fi
    done

    if [ -s "$invalid_list" ]; then
        while IFS= read -r invalid_entry; do
            log "Invalid playerdata in world backup: $invalid_entry"
        done < "$invalid_list"
        fail "Refusing to create world backup with invalid playerdata files"
    fi
}

write_backup_info() {
    {
        printf 'createdAt=%s\n' "$(date -Iseconds)"
        printf 'serverRoot=%s\n' "$SERVER_ROOT"
        printf 'worldName=%s\n' "$WORLD_NAME"
        printf 'serviceName=%s\n' "$SERVICE_NAME"
        printf 'retention=%s\n' "$RETENTION"
        printf 'mode=%s\n' "$STOP_SERVER"
    } > "$STAGING_DIR/backup-info.txt"
}

prune_old_backups() {
    ls -1t "$BACKUP_ROOT"/world-*.tar.gz 2>/dev/null |
        sed "1,${RETENTION}d" |
        while IFS= read -r old_archive; do
            [ -n "$old_archive" ] || continue
            log "Removing old backup: $old_archive"
            rm -f "$old_archive" "$old_archive.sha256"
        done
}

log "Starting world backup: $WORLD_DIR"
stop_server_if_needed
copy_world_to_staging
start_server_if_needed
validate_playerdata

write_backup_info
log "Compressing backup: $ARCHIVE_PATH"
tar -C "$STAGING_DIR" -czf "$TMP_ARCHIVE_PATH" "$WORLD_NAME" backup-info.txt
mv "$TMP_ARCHIVE_PATH" "$ARCHIVE_PATH"

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$BACKUP_ROOT" && sha256sum "$(basename "$ARCHIVE_PATH")" > "$(basename "$ARCHIVE_PATH").sha256")
fi

rm -rf "$STAGING_DIR"
prune_old_backups

if [ "$(id -u)" = "0" ] && [ -n "$BACKUP_OWNER" ]; then
    chown -R "$BACKUP_OWNER" "$BACKUP_ROOT" 2>/dev/null || true
fi

log "Backup complete: $ARCHIVE_PATH"
