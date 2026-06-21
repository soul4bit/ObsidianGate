#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
RCON_COMMAND="${RCON_COMMAND:-$SERVER_ROOT/scripts/obsidiangate-rcon-command.sh}"
ROAD_BUILDER="${ROAD_BUILDER:-$SERVER_ROOT/scripts/obsidiangate-build-spawn-roads.sh}"
ROAD_COMMAND="${ROAD_COMMAND:-spawnroads build}"
ROAD_LENGTH="${4:-240}"
HUB_RADIUS="${HUB_RADIUS:-44}"
FOUNDATION_DEPTH="${FOUNDATION_DEPTH:-10}"
CLEAR_ABOVE_HEIGHT="${CLEAR_ABOVE_HEIGHT:-30}"
BUILD_ROADS="${BUILD_ROADS:-server}"
RCON_DELAY="${RCON_DELAY:-0.08}"

usage() {
    echo "Usage: $0 <center-x> <surface-y> <center-z> [road-length]" >&2
    echo "Example: $0 484 70 -823 240" >&2
}

is_int() {
    case "$1" in
        ""|-) return 1 ;;
        -*) case "${1#-}" in ""|*[!0-9]*) return 1 ;; *) return 0 ;; esac ;;
        *[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

min() {
    if [ "$1" -le "$2" ]; then
        echo "$1"
    else
        echo "$2"
    fi
}

run_rcon() {
    printf '%s\n' "$*"
    attempts=0
    err_file="$(mktemp)"
    while :; do
        attempts=$((attempts + 1))
        if "$RCON_COMMAND" "$@" >/dev/null 2>"$err_file"; then
            if [ "$RCON_DELAY" != "0" ]; then
                sleep "$RCON_DELAY"
            fi
            rm -f "$err_file"
            return 0
        fi
        if [ "$attempts" -ge 3 ]; then
            cat "$err_file" >&2
            echo "RCON command failed after $attempts attempts: $*" >&2
            rm -f "$err_file"
            return 1
        fi
        sleep 1
    done
}

fill_box() {
    run_rcon "fill $1 $2 $3 $4 $5 $6 $7 $8 replace"
}

set_block() {
    run_rcon "setblock $1 $2 $3 $4 $5 replace"
}

fill_tiled() {
    x1="$1"
    y1="$2"
    z1="$3"
    x2="$4"
    y2="$5"
    z2="$6"
    block="$7"
    data="$8"

    tx="$x1"
    while [ "$tx" -le "$x2" ]; do
        tx2="$(min "$((tx + 15))" "$x2")"
        tz="$z1"
        while [ "$tz" -le "$z2" ]; do
            tz2="$(min "$((tz + 15))" "$z2")"
            fill_box "$tx" "$y1" "$tz" "$tx2" "$y2" "$tz2" "$block" "$data"
            tz=$((tz2 + 1))
        done
        tx=$((tx2 + 1))
    done
}

lamp_post() {
    x="$1"
    z="$2"
    fill_box "$x" "$((Y + 1))" "$z" "$x" "$((Y + 4))" "$z" minecraft:nether_brick_fence 0
    set_block "$x" "$((Y + 5))" "$z" minecraft:sea_lantern 0
    for dx in -1 1; do
        set_block "$((x + dx))" "$((Y + 4))" "$z" minecraft:end_rod 0
    done
    for dz in -1 1; do
        set_block "$x" "$((Y + 4))" "$((z + dz))" minecraft:end_rod 0
    done
}

tree() {
    x="$1"
    z="$2"
    log_meta="$3"
    leaf_meta="$4"

    fill_box "$x" "$((Y + 1))" "$z" "$x" "$((Y + 6))" "$z" minecraft:log "$log_meta"
    fill_box "$((x - 3))" "$((Y + 5))" "$((z - 3))" "$((x + 3))" "$((Y + 7))" "$((z + 3))" minecraft:leaves "$leaf_meta"
    fill_box "$((x - 2))" "$((Y + 8))" "$((z - 2))" "$((x + 2))" "$((Y + 9))" "$((z + 2))" minecraft:leaves "$leaf_meta"
    set_block "$x" "$((Y + 10))" "$z" minecraft:leaves "$leaf_meta"
}

flower_patch() {
    x1="$1"
    z1="$2"
    x2="$3"
    z2="$4"
    flower_data="$5"

    x="$x1"
    while [ "$x" -le "$x2" ]; do
        z="$z1"
        while [ "$z" -le "$z2" ]; do
            if [ $(((x + z) % 3)) -eq 0 ]; then
                set_block "$x" "$((Y + 1))" "$z" minecraft:red_flower "$flower_data"
            fi
            z=$((z + 2))
        done
        x=$((x + 2))
    done
}

build_compass_floor() {
    fill_box "$((CX - 34))" "$Y" "$((CZ - 34))" "$((CX + 34))" "$Y" "$((CZ + 34))" minecraft:stonebrick 0
    fill_box "$((CX - 26))" "$Y" "$((CZ - 26))" "$((CX + 26))" "$Y" "$((CZ + 26))" minecraft:quartz_block 0
    fill_box "$((CX - 19))" "$Y" "$((CZ - 19))" "$((CX + 19))" "$Y" "$((CZ + 19))" minecraft:stonebrick 0
    fill_box "$((CX - 12))" "$Y" "$((CZ - 12))" "$((CX + 12))" "$Y" "$((CZ + 12))" minecraft:quartz_block 0
    fill_box "$((CX - 7))" "$Y" "$((CZ - 7))" "$((CX + 7))" "$Y" "$((CZ + 7))" minecraft:obsidian 0
    fill_box "$((CX - 5))" "$Y" "$((CZ - 5))" "$((CX + 5))" "$Y" "$((CZ + 5))" minecraft:lapis_block 0
    fill_box "$((CX - 2))" "$Y" "$((CZ - 2))" "$((CX + 2))" "$Y" "$((CZ + 2))" minecraft:glowstone 0

    fill_box "$((CX - 3))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX + 3))" "$Y" "$((CZ - 8))" minecraft:wool 10
    fill_box "$((CX - 3))" "$Y" "$((CZ + 8))" "$((CX + 3))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:wool 14
    fill_box "$((CX + 8))" "$Y" "$((CZ - 3))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ + 3))" minecraft:wool 4
    fill_box "$((CX - HUB_RADIUS))" "$Y" "$((CZ - 3))" "$((CX - 8))" "$Y" "$((CZ + 3))" minecraft:wool 11

    fill_box "$((CX - 5))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX - 4))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:quartz_block 0
    fill_box "$((CX + 4))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX + 5))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:quartz_block 0
    fill_box "$((CX - HUB_RADIUS))" "$Y" "$((CZ - 5))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ - 4))" minecraft:quartz_block 0
    fill_box "$((CX - HUB_RADIUS))" "$Y" "$((CZ + 4))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ + 5))" minecraft:quartz_block 0

    fill_box "$((CX - 3))" "$((Y + 1))" "$((CZ - 3))" "$((CX + 3))" "$((Y + 5))" "$((CZ + 3))" minecraft:air 0
}

build_obsidian_gate() {
    gz=$((CZ - 14))

    fill_box "$((CX - 12))" "$Y" "$((gz - 5))" "$((CX + 12))" "$Y" "$((gz + 5))" minecraft:obsidian 0
    fill_box "$((CX - 10))" "$Y" "$((gz - 3))" "$((CX + 10))" "$Y" "$((gz + 3))" minecraft:quartz_block 0
    fill_box "$((CX - 8))" "$((Y + 1))" "$((gz - 2))" "$((CX - 6))" "$((Y + 15))" "$((gz + 2))" minecraft:obsidian 0
    fill_box "$((CX + 6))" "$((Y + 1))" "$((gz - 2))" "$((CX + 8))" "$((Y + 15))" "$((gz + 2))" minecraft:obsidian 0
    fill_box "$((CX - 8))" "$((Y + 14))" "$((gz - 2))" "$((CX + 8))" "$((Y + 17))" "$((gz + 2))" minecraft:obsidian 0

    fill_box "$((CX - 5))" "$((Y + 4))" "$gz" "$((CX + 5))" "$((Y + 13))" "$gz" minecraft:stained_glass 10
    fill_box "$((CX - 3))" "$((Y + 5))" "$((gz - 1))" "$((CX + 3))" "$((Y + 12))" "$((gz - 1))" minecraft:stained_glass 2
    fill_box "$((CX - 10))" "$((Y + 2))" "$((gz - 3))" "$((CX - 9))" "$((Y + 14))" "$((gz + 3))" minecraft:quartz_block 0
    fill_box "$((CX + 9))" "$((Y + 2))" "$((gz - 3))" "$((CX + 10))" "$((Y + 14))" "$((gz + 3))" minecraft:quartz_block 0
    fill_box "$((CX - 10))" "$((Y + 16))" "$((gz - 3))" "$((CX + 10))" "$((Y + 17))" "$((gz + 3))" minecraft:quartz_block 0

    set_block "$((CX - 11))" "$((Y + 8))" "$gz" minecraft:sea_lantern 0
    set_block "$((CX + 11))" "$((Y + 8))" "$gz" minecraft:sea_lantern 0
    set_block "$CX" "$((Y + 18))" "$gz" minecraft:sea_lantern 0
    set_block "$CX" "$((Y + 20))" "$gz" minecraft:lapis_block 0
    set_block "$CX" "$((Y + 21))" "$gz" minecraft:obsidian 0
    set_block "$CX" "$((Y + 22))" "$gz" minecraft:sea_lantern 0
}

crystal_pylon() {
    x="$1"
    z="$2"
    glass_data="$3"

    fill_box "$((x - 2))" "$Y" "$((z - 2))" "$((x + 2))" "$Y" "$((z + 2))" minecraft:quartz_block 0
    fill_box "$((x - 1))" "$((Y + 1))" "$((z - 1))" "$((x + 1))" "$((Y + 1))" "$((z + 1))" minecraft:obsidian 0
    fill_box "$x" "$((Y + 2))" "$z" "$x" "$((Y + 11))" "$z" minecraft:obsidian 0
    fill_box "$((x - 1))" "$((Y + 5))" "$z" "$((x + 1))" "$((Y + 9))" "$z" minecraft:stained_glass "$glass_data"
    fill_box "$x" "$((Y + 5))" "$((z - 1))" "$x" "$((Y + 9))" "$((z + 1))" minecraft:stained_glass "$glass_data"
    set_block "$x" "$((Y + 12))" "$z" minecraft:sea_lantern 0
    set_block "$x" "$((Y + 13))" "$z" minecraft:stained_glass "$glass_data"
    set_block "$x" "$((Y + 14))" "$z" minecraft:end_rod 0
}

build_outer_pylons() {
    crystal_pylon "$((CX - 26))" "$((CZ - 26))" 10
    crystal_pylon "$((CX + 26))" "$((CZ - 26))" 3
    crystal_pylon "$((CX - 26))" "$((CZ + 26))" 11
    crystal_pylon "$((CX + 26))" "$((CZ + 26))" 4

    lamp_post "$((CX - 16))" "$((CZ - 16))"
    lamp_post "$((CX + 16))" "$((CZ - 16))"
    lamp_post "$((CX - 16))" "$((CZ + 16))"
    lamp_post "$((CX + 16))" "$((CZ + 16))"
}

build_low_border() {
    edge=$((HUB_RADIUS - 4))
    west=$((CX - edge))
    east=$((CX + edge))
    north=$((CZ - edge))
    south=$((CZ + edge))

    fill_box "$west" "$((Y + 1))" "$north" "$((CX - 8))" "$((Y + 2))" "$north" minecraft:stonebrick 0
    fill_box "$((CX + 8))" "$((Y + 1))" "$north" "$east" "$((Y + 2))" "$north" minecraft:stonebrick 0
    fill_box "$west" "$((Y + 1))" "$south" "$((CX - 8))" "$((Y + 2))" "$south" minecraft:stonebrick 0
    fill_box "$((CX + 8))" "$((Y + 1))" "$south" "$east" "$((Y + 2))" "$south" minecraft:stonebrick 0
    fill_box "$west" "$((Y + 1))" "$north" "$west" "$((Y + 2))" "$((CZ - 8))" minecraft:stonebrick 0
    fill_box "$west" "$((Y + 1))" "$((CZ + 8))" "$west" "$((Y + 2))" "$south" minecraft:stonebrick 0
    fill_box "$east" "$((Y + 1))" "$north" "$east" "$((Y + 2))" "$((CZ - 8))" minecraft:stonebrick 0
    fill_box "$east" "$((Y + 1))" "$((CZ + 8))" "$east" "$((Y + 2))" "$south" minecraft:stonebrick 0

    for x in "$west" "$east"; do
        for z in "$north" "$south"; do
            fill_box "$((x - 1))" "$((Y + 1))" "$((z - 1))" "$((x + 1))" "$((Y + 4))" "$((z + 1))" minecraft:obsidian 0
            set_block "$x" "$((Y + 5))" "$z" minecraft:sea_lantern 0
        done
    done
}

build_gardens() {
    fill_box "$((CX - 38))" "$Y" "$((CZ - 32))" "$((CX - 29))" "$Y" "$((CZ - 21))" minecraft:grass 0
    fill_box "$((CX + 29))" "$Y" "$((CZ - 32))" "$((CX + 38))" "$Y" "$((CZ - 21))" minecraft:grass 0
    fill_box "$((CX - 38))" "$Y" "$((CZ + 21))" "$((CX - 29))" "$Y" "$((CZ + 32))" minecraft:grass 0
    fill_box "$((CX + 29))" "$Y" "$((CZ + 21))" "$((CX + 38))" "$Y" "$((CZ + 32))" minecraft:grass 0

    tree "$((CX - 34))" "$((CZ - 27))" 0 0
    tree "$((CX + 34))" "$((CZ - 27))" 1 1
    tree "$((CX - 34))" "$((CZ + 27))" 2 2
    tree "$((CX + 34))" "$((CZ + 27))" 0 0

    flower_patch "$((CX - 37))" "$((CZ - 31))" "$((CX - 30))" "$((CZ - 22))" 4
    flower_patch "$((CX + 30))" "$((CZ - 31))" "$((CX + 37))" "$((CZ - 22))" 1
    flower_patch "$((CX - 37))" "$((CZ + 22))" "$((CX - 30))" "$((CZ + 31))" 5
    flower_patch "$((CX + 30))" "$((CZ + 22))" "$((CX + 37))" "$((CZ + 31))" 6

    fill_box "$((CX - 31))" "$Y" "$((CZ - 20))" "$((CX - 21))" "$Y" "$((CZ - 15))" minecraft:quartz_block 0
    fill_box "$((CX - 30))" "$Y" "$((CZ - 19))" "$((CX - 22))" "$Y" "$((CZ - 16))" minecraft:water 0
    fill_box "$((CX + 21))" "$Y" "$((CZ + 15))" "$((CX + 31))" "$Y" "$((CZ + 20))" minecraft:quartz_block 0
    fill_box "$((CX + 22))" "$Y" "$((CZ + 16))" "$((CX + 30))" "$Y" "$((CZ + 19))" minecraft:water 0
}

stall() {
    sx="$1"
    sz="$2"
    wool="$3"

    fill_box "$((sx - 2))" "$((Y + 1))" "$((sz - 1))" "$((sx - 2))" "$((Y + 3))" "$((sz - 1))" minecraft:fence 0
    fill_box "$((sx + 2))" "$((Y + 1))" "$((sz - 1))" "$((sx + 2))" "$((Y + 3))" "$((sz - 1))" minecraft:fence 0
    fill_box "$((sx - 2))" "$((Y + 1))" "$((sz + 1))" "$((sx - 2))" "$((Y + 3))" "$((sz + 1))" minecraft:fence 0
    fill_box "$((sx + 2))" "$((Y + 1))" "$((sz + 1))" "$((sx + 2))" "$((Y + 3))" "$((sz + 1))" minecraft:fence 0
    fill_box "$((sx - 3))" "$((Y + 4))" "$((sz - 2))" "$((sx + 3))" "$((Y + 4))" "$((sz + 2))" minecraft:wool "$wool"
    fill_box "$((sx - 2))" "$((Y + 1))" "$sz" "$((sx + 2))" "$((Y + 1))" "$sz" minecraft:planks 5
    set_block "$sx" "$((Y + 2))" "$sz" minecraft:chest 0
}

build_market() {
    fill_box "$((CX + 20))" "$Y" "$((CZ - 13))" "$((CX + 37))" "$Y" "$((CZ + 13))" minecraft:stonebrick 0
    stall "$((CX + 27))" "$((CZ - 7))" 4
    stall "$((CX + 27))" "$((CZ + 7))" 1
    stall "$((CX + 35))" "$CZ" 11
}

build_arrival_balcony() {
    fill_box "$((CX - 20))" "$Y" "$((CZ + 20))" "$((CX + 20))" "$Y" "$((CZ + 36))" minecraft:stonebrick 0
    fill_box "$((CX - 14))" "$Y" "$((CZ + 24))" "$((CX + 14))" "$Y" "$((CZ + 32))" minecraft:quartz_block 0
    fill_box "$((CX - 2))" "$Y" "$((CZ + 25))" "$((CX + 2))" "$Y" "$((CZ + 31))" minecraft:water 0
    fill_box "$((CX - 18))" "$((Y + 1))" "$((CZ + 35))" "$((CX + 18))" "$((Y + 2))" "$((CZ + 35))" minecraft:stonebrick 0
    lamp_post "$((CX - 16))" "$((CZ + 30))"
    lamp_post "$((CX + 16))" "$((CZ + 30))"
}

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
    usage
    exit 2
fi

if [ ! -x "$RCON_COMMAND" ]; then
    echo "RCON command helper not executable: $RCON_COMMAND" >&2
    exit 1
fi

CX="$1"
Y="$2"
CZ="$3"
if [ "$#" -eq 4 ]; then
    ROAD_LENGTH="$4"
fi

for value in "$CX" "$Y" "$CZ" "$ROAD_LENGTH" "$HUB_RADIUS" "$FOUNDATION_DEPTH" "$CLEAR_ABOVE_HEIGHT"; do
    if ! is_int "$value"; then
        usage
        exit 2
    fi
done

if [ "$HUB_RADIUS" -lt 36 ]; then
    echo "HUB_RADIUS must be at least 36." >&2
    exit 2
fi

case "$BUILD_ROADS" in
    0|false|False|FALSE|no|No|NO)
        BUILD_ROADS=0
        ;;
    legacy|Legacy|LEGACY)
        BUILD_ROADS=legacy
        ;;
    *)
        BUILD_ROADS=server
        ;;
esac

if [ "$BUILD_ROADS" = "legacy" ] && [ -x "$ROAD_BUILDER" ]; then
    echo "Building legacy flat season roads before the ObsidianGate hub..."
    DECORATE_SPAWN=0 \
        FOUNDATION_DEPTH="$FOUNDATION_DEPTH" \
        CLEAR_ABOVE_HEIGHT=18 \
        RCON_DELAY="$RCON_DELAY" \
        "$ROAD_BUILDER" "$CX" "$Y" "$CZ" "$ROAD_LENGTH"
elif [ "$BUILD_ROADS" = "legacy" ]; then
    echo "Continuing with hub only; legacy road builder disabled or unavailable: $ROAD_BUILDER" >&2
fi

SUPPORT_Y=$((Y - 1))
FOUNDATION_Y1=$((Y - FOUNDATION_DEPTH))
if [ "$FOUNDATION_Y1" -lt 0 ]; then
    FOUNDATION_Y1=0
fi
HEAD_Y1=$((Y + 1))
HEAD_Y2=$((Y + CLEAR_ABOVE_HEIGHT))

echo "Building ObsidianGate portal hub at $CX $Y $CZ..."
echo "Hub radius: $HUB_RADIUS, foundation y=$FOUNDATION_Y1..$SUPPORT_Y."

fill_tiled "$((CX - HUB_RADIUS))" "$FOUNDATION_Y1" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$SUPPORT_Y" "$((CZ + HUB_RADIUS))" minecraft:dirt 0
fill_tiled "$((CX - HUB_RADIUS))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:grass 0
fill_tiled "$((CX - HUB_RADIUS))" "$HEAD_Y1" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$HEAD_Y2" "$((CZ + HUB_RADIUS))" minecraft:air 0

build_compass_floor
build_obsidian_gate
build_outer_pylons
build_low_border
build_gardens
build_market
build_arrival_balcony

run_rcon "fill $((CX - 3)) $((Y + 1)) $((CZ - 3)) $((CX + 3)) $((Y + 5)) $((CZ + 3)) minecraft:air 0 replace"
run_rcon "setworldspawn $CX $((Y + 1)) $CZ"

if [ "$BUILD_ROADS" = "server" ]; then
    echo "Starting adaptive spawn roads with: /$ROAD_COMMAND $CX $Y $CZ $ROAD_LENGTH"
    run_rcon "$ROAD_COMMAND $CX $Y $CZ $ROAD_LENGTH"
    echo "Road build runs on the server tick. Watch /spawnroads status and run /save-all after it completes."
elif [ "$BUILD_ROADS" = "0" ]; then
    echo "Spawn roads skipped. To build them later, run: /$ROAD_COMMAND $CX $Y $CZ $ROAD_LENGTH"
fi

echo "ObsidianGate portal hub complete."
