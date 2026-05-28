#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
WORLD_NAME="${WORLD_NAME:-world}"
BACKUP_ROOT="${BACKUP_ROOT:-$SERVER_ROOT/backups/playerdata}"
RETENTION="${RETENTION:-288}"
BACKUP_OWNER="${BACKUP_OWNER:-minecraft:minecraft}"
LOCK_FILE="${LOCK_FILE:-/tmp/obsidiangate-playerdata-backup.lock}"

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
        log "Playerdata backup already running, skipping."
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
STAGING_DIR="$BACKUP_ROOT/.staging/playerdata-$TIMESTAMP"
ARCHIVE_PATH="$BACKUP_ROOT/playerdata-$TIMESTAMP.tar.gz"
TMP_ARCHIVE_PATH="$ARCHIVE_PATH.tmp"

cleanup() {
    status=$?
    rm -rf "$STAGING_DIR" "$TMP_ARCHIVE_PATH"
    exit "$status"
}

trap cleanup EXIT INT TERM

copy_if_exists() {
    source_path="$WORLD_DIR/$1"
    target_path="$STAGING_DIR/$WORLD_NAME/$1"
    if [ ! -e "$source_path" ]; then
        return
    fi

    mkdir -p "$(dirname "$target_path")"
    if [ -d "$source_path" ]; then
        if command -v rsync >/dev/null 2>&1; then
            mkdir -p "$target_path"
            rsync -a --delete "$source_path/" "$target_path/"
        else
            cp -a "$source_path" "$(dirname "$target_path")/"
        fi
    else
        cp -a "$source_path" "$target_path"
    fi
}

write_backup_info() {
    {
        printf 'createdAt=%s\n' "$(date -Iseconds)"
        printf 'serverRoot=%s\n' "$SERVER_ROOT"
        printf 'worldName=%s\n' "$WORLD_NAME"
        printf 'retention=%s\n' "$RETENTION"
        printf 'contents=playerdata,stats,advancements\n'
    } > "$STAGING_DIR/backup-info.txt"
}

prune_old_backups() {
    ls -1t "$BACKUP_ROOT"/playerdata-*.tar.gz 2>/dev/null |
        sed "1,${RETENTION}d" |
        while IFS= read -r old_archive; do
            [ -n "$old_archive" ] || continue
            log "Removing old playerdata backup: $old_archive"
            rm -f "$old_archive" "$old_archive.sha256"
        done
}

log "Starting playerdata backup: $WORLD_DIR"
mkdir -p "$STAGING_DIR/$WORLD_NAME"
copy_if_exists "playerdata"
copy_if_exists "stats"
copy_if_exists "advancements"
write_backup_info

if [ ! -d "$STAGING_DIR/$WORLD_NAME/playerdata" ]; then
    fail "No playerdata directory found in $WORLD_DIR"
fi

log "Compressing playerdata backup: $ARCHIVE_PATH"
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

log "Playerdata backup complete: $ARCHIVE_PATH"
