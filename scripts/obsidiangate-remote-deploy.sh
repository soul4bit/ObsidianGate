#!/bin/sh
set -eu

WRAPPER_VERSION="2026.05.29-config-merge.1"

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

install -d "$SERVER_MODS_DIR"

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
    systemctl status "$SERVICE_NAME" --no-pager -l
fi
