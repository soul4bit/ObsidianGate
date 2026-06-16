#!/bin/sh
set -eu

WRAPPER_VERSION="2026.06.15-health-rollback.1"

if [ "${1:-}" = "--self-test" ]; then
    echo "obsidiangate-remote-deploy: ok $WRAPPER_VERSION"
    exit 0
fi

STAGE_DIR="${1:?stage dir is required}"
SERVER_JAR="${2:?server jar file name is required}"
SERVER_MODS_DIR="${3:?server mods dir is required}"
WEB_ROOT="${4:?web root is required}"
SERVICE_NAME="${5:?service name is required}"
SKIP_RESTART="${6:-0}"
SERVER_ROOT="${7:-$(dirname "$SERVER_MODS_DIR")}"
HEALTH_CHECK_SECONDS="${8:-45}"

case "$HEALTH_CHECK_SECONDS" in
    ''|*[!0-9]*)
        echo "Health check seconds must be a non-negative integer: $HEALTH_CHECK_SECONDS" >&2
        exit 2
        ;;
esac

canonicalize_path() {
    if command -v realpath >/dev/null 2>&1; then
        realpath -m -- "$1"
        return
    fi
    if command -v readlink >/dev/null 2>&1; then
        readlink -m -- "$1"
        return
    fi
    echo "Neither realpath nor readlink is available for path validation." >&2
    exit 2
}

require_under() {
    path="$(canonicalize_path "$1")"
    base="$(canonicalize_path "$2")"
    label="$3"

    case "$path" in
        "$base"/*)
            printf '%s\n' "$path"
            ;;
        *)
            echo "$label must be under $base: $path" >&2
            exit 2
            ;;
    esac
}

STAGE_DIR="$(require_under "$STAGE_DIR" /home "Stage directory")"
SERVER_MODS_DIR="$(require_under "$SERVER_MODS_DIR" /home "Server mods dir")"
SERVER_ROOT="$(require_under "$SERVER_ROOT" /home "Server root")"
WEB_ROOT="$(require_under "$WEB_ROOT" /var/www "Web root")"

case "$SERVICE_NAME" in
    mc-rpg.service|mc-rpg-*.service|obsidiangate-*.service) ;;
    *)
        echo "Refusing to restart unexpected service: $SERVICE_NAME" >&2
        exit 4
        ;;
esac

if [ ! -d "$STAGE_DIR/client" ]; then
    echo "Missing staged client directory: $STAGE_DIR/client" >&2
    exit 3
fi

if [ ! -f "$STAGE_DIR/manifest.json" ]; then
    echo "Missing staged manifest: $STAGE_DIR/manifest.json" >&2
    exit 3
fi
if [ ! -f "$STAGE_DIR/manifest.json.sig" ]; then
    echo "Missing staged manifest signature: $STAGE_DIR/manifest.json.sig" >&2
    exit 3
fi

if [ ! -f "$STAGE_DIR/$SERVER_JAR" ]; then
    echo "Missing staged server jar: $STAGE_DIR/$SERVER_JAR" >&2
    exit 3
fi

BACKUP_BASE="$SERVER_ROOT/obsidiangate-deploy-backups"
BACKUP_DIR="$BACKUP_BASE/$(date -u +%Y%m%dT%H%M%SZ)"
SYSTEMD_UNITS=""

backup_item() {
    source_path="$1"
    backup_name="$2"
    if [ -e "$source_path" ]; then
        install -d "$(dirname "$BACKUP_DIR/$backup_name")"
        cp -a "$source_path" "$BACKUP_DIR/$backup_name"
    fi
}

restore_item() {
    backup_name="$1"
    target_path="$2"
    if [ -e "$BACKUP_DIR/$backup_name" ]; then
        rm -rf "$target_path"
        install -d "$(dirname "$target_path")"
        cp -a "$BACKUP_DIR/$backup_name" "$target_path"
    else
        rm -rf "$target_path"
    fi
}

remember_systemd_unit() {
    unit_name="$1"
    case " $SYSTEMD_UNITS " in
        *" $unit_name "*) ;;
        *) SYSTEMD_UNITS="$SYSTEMD_UNITS $unit_name" ;;
    esac
}

prepare_rollback_backup() {
    rm -rf "$BACKUP_DIR"
    install -d "$BACKUP_DIR"
    backup_item "$SERVER_MODS_DIR" "server-mods"
    backup_item "$SERVER_ROOT/config" "server-config"
    backup_item "$SERVER_ROOT/scripts" "server-scripts"
    backup_item "$SERVER_ROOT/server-icon.png" "server-icon.png"
    backup_item "$WEB_ROOT/client" "web-client"
    backup_item "$WEB_ROOT/launcher" "web-launcher"
    backup_item "$WEB_ROOT/manifest.json" "manifest.json"
    backup_item "$WEB_ROOT/manifest.json.sig" "manifest.json.sig"
}

restore_previous_release() {
    echo "Health check failed; restoring previous ObsidianGate release from $BACKUP_DIR" >&2
    restore_item "server-mods" "$SERVER_MODS_DIR"
    restore_item "server-config" "$SERVER_ROOT/config"
    restore_item "server-scripts" "$SERVER_ROOT/scripts"
    restore_item "server-icon.png" "$SERVER_ROOT/server-icon.png"
    restore_item "web-client" "$WEB_ROOT/client"
    restore_item "web-launcher" "$WEB_ROOT/launcher"
    restore_item "manifest.json" "$WEB_ROOT/manifest.json"
    restore_item "manifest.json.sig" "$WEB_ROOT/manifest.json.sig"
    for unit_name in $SYSTEMD_UNITS; do
        restore_item "systemd/$unit_name" "/etc/systemd/system/$unit_name"
    done
    if [ -n "$SYSTEMD_UNITS" ]; then
        systemctl daemon-reload
    fi
    systemctl restart "$SERVICE_NAME" || true
    systemctl status "$SERVICE_NAME" --no-pager -l || true
    exit 7
}

cleanup_old_backups() {
    if [ -d "$BACKUP_BASE" ]; then
        ls -1dt "$BACKUP_BASE"/* 2>/dev/null | tail -n +6 | xargs -r rm -rf
    fi
}

prepare_rollback_backup

install -d "$SERVER_MODS_DIR"

find "$SERVER_MODS_DIR" -mindepth 1 -maxdepth 1 -name 'obsidiangate-forge-auth-server-*.jar' -exec rm -rf {} +
if [ -d "$STAGE_DIR/server/mods" ]; then
    find "$SERVER_MODS_DIR" -mindepth 1 -maxdepth 1 ! -name 'obsidiangate-forge-auth-server-*.jar' -exec rm -rf {} +
    cp -a "$STAGE_DIR/server/mods/." "$SERVER_MODS_DIR/"
fi

install -m 644 "$STAGE_DIR/$SERVER_JAR" "$SERVER_MODS_DIR/$SERVER_JAR"

if [ -d "$STAGE_DIR/server/config" ]; then
    install -d "$SERVER_ROOT/config"
    cp -a "$STAGE_DIR/server/config/." "$SERVER_ROOT/config/"
fi

if [ -f "$STAGE_DIR/server/server-icon.png" ]; then
    install -m 644 "$STAGE_DIR/server/server-icon.png" "$SERVER_ROOT/server-icon.png"
fi

if [ -d "$STAGE_DIR/server/scripts" ]; then
    rm -rf "$SERVER_ROOT/scripts"
    install -d "$SERVER_ROOT/scripts"
    cp -a "$STAGE_DIR/server/scripts/." "$SERVER_ROOT/scripts/"
    find "$SERVER_ROOT/scripts" -type f -name "*.sh" -exec chmod 755 {} +
fi

if [ -d "$STAGE_DIR/server/systemd" ]; then
    for unit in "$STAGE_DIR"/server/systemd/*.service "$STAGE_DIR"/server/systemd/*.timer; do
        [ -e "$unit" ] || continue
        unit_name="$(basename "$unit")"
        case "$unit_name" in
            mc-rpg.service|mc-rpg.timer|mc-rpg-*.service|mc-rpg-*.timer|obsidiangate-*.service|obsidiangate-*.timer) ;;
            *)
                echo "Refusing to install unexpected systemd unit: $unit_name" >&2
                exit 4
                ;;
        esac
        remember_systemd_unit "$unit_name"
        backup_item "/etc/systemd/system/$unit_name" "systemd/$unit_name"
        install -m 644 "$unit" "/etc/systemd/system/$unit_name"
    done

    systemctl daemon-reload
    if [ -f /etc/systemd/system/mc-rpg-world-backup.timer ]; then
        systemctl enable --now mc-rpg-world-backup.timer
        systemctl list-timers --all mc-rpg-world-backup.timer --no-pager
    fi
    if [ -f /etc/systemd/system/mc-rpg-playerdata-backup.timer ]; then
        systemctl enable --now mc-rpg-playerdata-backup.timer
        systemctl list-timers --all mc-rpg-playerdata-backup.timer --no-pager
    fi
fi

install -d "$WEB_ROOT"
if command -v rsync >/dev/null 2>&1; then
    install -d "$WEB_ROOT/client"
    rsync -a --delete "$STAGE_DIR/client/" "$WEB_ROOT/client/"
else
    rm -rf "$WEB_ROOT/client"
    install -d "$WEB_ROOT/client"
    cp -a "$STAGE_DIR/client/." "$WEB_ROOT/client/"
fi

if [ -d "$STAGE_DIR/launcher" ]; then
    install -d "$WEB_ROOT/launcher"
    cp -a "$STAGE_DIR/launcher/." "$WEB_ROOT/launcher/"
fi

install -m 644 "$STAGE_DIR/manifest.json" "$WEB_ROOT/manifest.json"
install -m 644 "$STAGE_DIR/manifest.json.sig" "$WEB_ROOT/manifest.json.sig"
sha256sum "$SERVER_MODS_DIR/$SERVER_JAR"
sha256sum "$WEB_ROOT/manifest.json"
sha256sum "$WEB_ROOT/manifest.json.sig"

if [ "$SKIP_RESTART" != "1" ]; then
    systemctl restart "$SERVICE_NAME"
    if [ "$HEALTH_CHECK_SECONDS" -gt 0 ]; then
        echo "Waiting ${HEALTH_CHECK_SECONDS}s for $SERVICE_NAME health check..."
        sleep "$HEALTH_CHECK_SECONDS"
    fi
    if ! systemctl is-active --quiet "$SERVICE_NAME"; then
        restore_previous_release
    fi
    systemctl status "$SERVICE_NAME" --no-pager -l
    cleanup_old_backups
fi
