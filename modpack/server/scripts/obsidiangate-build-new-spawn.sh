#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
RCON_COMMAND="${RCON_COMMAND:-$SERVER_ROOT/scripts/obsidiangate-rcon-command.sh}"
ROAD_BUILDER="${ROAD_BUILDER:-$SERVER_ROOT/scripts/obsidiangate-build-spawn-roads.sh}"
ROAD_LENGTH="${4:-240}"
HUB_RADIUS="${HUB_RADIUS:-42}"
WALL_RADIUS="${WALL_RADIUS:-36}"
FOUNDATION_DEPTH="${FOUNDATION_DEPTH:-10}"
CLEAR_ABOVE_HEIGHT="${CLEAR_ABOVE_HEIGHT:-28}"

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
    set_block "$x" "$((Y + 5))" "$z" minecraft:glowstone 0
}

tree() {
    x="$1"
    z="$2"
    fill_box "$x" "$((Y + 1))" "$z" "$x" "$((Y + 6))" "$z" minecraft:log 0
    fill_box "$((x - 3))" "$((Y + 5))" "$((z - 3))" "$((x + 3))" "$((Y + 7))" "$((z + 3))" minecraft:leaves 0
    fill_box "$((x - 2))" "$((Y + 8))" "$((z - 2))" "$((x + 2))" "$((Y + 9))" "$((z + 2))" minecraft:leaves 0
    set_block "$x" "$((Y + 10))" "$z" minecraft:leaves 0
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

tower() {
    tx="$1"
    tz="$2"

    fill_box "$((tx - 5))" "$((Y - 2))" "$((tz - 5))" "$((tx + 5))" "$((Y - 1))" "$((tz + 5))" minecraft:dirt 0
    fill_box "$((tx - 5))" "$((Y + 1))" "$((tz - 5))" "$((tx + 5))" "$((Y + 16))" "$((tz + 5))" minecraft:stonebrick 0
    fill_box "$((tx - 3))" "$((Y + 2))" "$((tz - 3))" "$((tx + 3))" "$((Y + 15))" "$((tz + 3))" minecraft:air 0
    fill_box "$((tx - 3))" "$((Y + 8))" "$((tz - 3))" "$((tx + 3))" "$((Y + 8))" "$((tz + 3))" minecraft:planks 5
    fill_box "$((tx - 5))" "$((Y + 16))" "$((tz - 5))" "$((tx + 5))" "$((Y + 16))" "$((tz + 5))" minecraft:stonebrick 0
    fill_box "$((tx - 4))" "$((Y + 17))" "$((tz - 4))" "$((tx + 4))" "$((Y + 17))" "$((tz + 4))" minecraft:nether_brick 0
    fill_box "$((tx - 2))" "$((Y + 18))" "$((tz - 2))" "$((tx + 2))" "$((Y + 18))" "$((tz + 2))" minecraft:nether_brick 0
    set_block "$tx" "$((Y + 19))" "$tz" minecraft:glowstone 0

    fill_box "$((tx - 1))" "$((Y + 5))" "$((tz - 5))" "$((tx + 1))" "$((Y + 7))" "$((tz - 5))" minecraft:stained_glass_pane 14
    fill_box "$((tx - 1))" "$((Y + 5))" "$((tz + 5))" "$((tx + 1))" "$((Y + 7))" "$((tz + 5))" minecraft:stained_glass_pane 14
    fill_box "$((tx - 5))" "$((Y + 5))" "$((tz - 1))" "$((tx - 5))" "$((Y + 7))" "$((tz + 1))" minecraft:stained_glass_pane 14
    fill_box "$((tx + 5))" "$((Y + 5))" "$((tz - 1))" "$((tx + 5))" "$((Y + 7))" "$((tz + 1))" minecraft:stained_glass_pane 14
}

gateway_z() {
    gz="$1"

    fill_box "$((CX - 12))" "$((Y + 1))" "$((gz - 2))" "$((CX - 6))" "$((Y + 13))" "$((gz + 2))" minecraft:stonebrick 0
    fill_box "$((CX + 6))" "$((Y + 1))" "$((gz - 2))" "$((CX + 12))" "$((Y + 13))" "$((gz + 2))" minecraft:stonebrick 0
    fill_box "$((CX - 12))" "$((Y + 10))" "$((gz - 2))" "$((CX + 12))" "$((Y + 13))" "$((gz + 2))" minecraft:stonebrick 0
    fill_box "$((CX - 5))" "$((Y + 1))" "$((gz - 3))" "$((CX + 5))" "$((Y + 9))" "$((gz + 3))" minecraft:air 0
    fill_box "$((CX - 4))" "$Y" "$((gz - 4))" "$((CX + 4))" "$Y" "$((gz + 4))" minecraft:wool 14
    set_block "$((CX - 9))" "$((Y + 14))" "$gz" minecraft:glowstone 0
    set_block "$((CX + 9))" "$((Y + 14))" "$gz" minecraft:glowstone 0
}

gateway_x() {
    gx="$1"

    fill_box "$((gx - 2))" "$((Y + 1))" "$((CZ - 12))" "$((gx + 2))" "$((Y + 13))" "$((CZ - 6))" minecraft:stonebrick 0
    fill_box "$((gx - 2))" "$((Y + 1))" "$((CZ + 6))" "$((gx + 2))" "$((Y + 13))" "$((CZ + 12))" minecraft:stonebrick 0
    fill_box "$((gx - 2))" "$((Y + 10))" "$((CZ - 12))" "$((gx + 2))" "$((Y + 13))" "$((CZ + 12))" minecraft:stonebrick 0
    fill_box "$((gx - 3))" "$((Y + 1))" "$((CZ - 5))" "$((gx + 3))" "$((Y + 9))" "$((CZ + 5))" minecraft:air 0
    fill_box "$((gx - 4))" "$Y" "$((CZ - 4))" "$((gx + 4))" "$Y" "$((CZ + 4))" minecraft:wool 14
    set_block "$gx" "$((Y + 14))" "$((CZ - 9))" minecraft:glowstone 0
    set_block "$gx" "$((Y + 14))" "$((CZ + 9))" minecraft:glowstone 0
}

build_walls() {
    north=$((CZ - WALL_RADIUS))
    south=$((CZ + WALL_RADIUS))
    west=$((CX - WALL_RADIUS))
    east=$((CX + WALL_RADIUS))

    fill_box "$west" "$((Y + 1))" "$north" "$east" "$((Y + 8))" "$((north + 2))" minecraft:stonebrick 0
    fill_box "$west" "$((Y + 1))" "$((south - 2))" "$east" "$((Y + 8))" "$south" minecraft:stonebrick 0
    fill_box "$west" "$((Y + 1))" "$north" "$((west + 2))" "$((Y + 8))" "$south" minecraft:stonebrick 0
    fill_box "$((east - 2))" "$((Y + 1))" "$north" "$east" "$((Y + 8))" "$south" minecraft:stonebrick 0

    fill_box "$((CX - 5))" "$((Y + 1))" "$((north - 1))" "$((CX + 5))" "$((Y + 8))" "$((north + 3))" minecraft:air 0
    fill_box "$((CX - 5))" "$((Y + 1))" "$((south - 3))" "$((CX + 5))" "$((Y + 8))" "$((south + 1))" minecraft:air 0
    fill_box "$((west - 1))" "$((Y + 1))" "$((CZ - 5))" "$((west + 3))" "$((Y + 8))" "$((CZ + 5))" minecraft:air 0
    fill_box "$((east - 3))" "$((Y + 1))" "$((CZ - 5))" "$((east + 1))" "$((Y + 8))" "$((CZ + 5))" minecraft:air 0

    x="$west"
    while [ "$x" -le "$east" ]; do
        if [ "$x" -lt "$((CX - 7))" ] || [ "$x" -gt "$((CX + 7))" ]; then
            fill_box "$x" "$((Y + 9))" "$north" "$x" "$((Y + 11))" "$((north + 2))" minecraft:stonebrick 0
            fill_box "$x" "$((Y + 9))" "$((south - 2))" "$x" "$((Y + 11))" "$south" minecraft:stonebrick 0
        fi
        x=$((x + 4))
    done

    z="$north"
    while [ "$z" -le "$south" ]; do
        if [ "$z" -lt "$((CZ - 7))" ] || [ "$z" -gt "$((CZ + 7))" ]; then
            fill_box "$west" "$((Y + 9))" "$z" "$((west + 2))" "$((Y + 11))" "$z" minecraft:stonebrick 0
            fill_box "$((east - 2))" "$((Y + 9))" "$z" "$east" "$((Y + 11))" "$z" minecraft:stonebrick 0
        fi
        z=$((z + 4))
    done

    gateway_z "$north"
    gateway_z "$south"
    gateway_x "$west"
    gateway_x "$east"
}

build_plaza() {
    fill_box "$((CX - 18))" "$Y" "$((CZ - 18))" "$((CX + 18))" "$Y" "$((CZ + 18))" minecraft:stonebrick 0
    fill_box "$((CX - 2))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX + 1))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:wool 14
    fill_box "$((CX - HUB_RADIUS))" "$Y" "$((CZ - 2))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ + 1))" minecraft:wool 14
    fill_box "$((CX - 4))" "$Y" "$((CZ - 4))" "$((CX + 4))" "$Y" "$((CZ + 4))" minecraft:quartz_block 0
    fill_box "$((CX - 1))" "$Y" "$((CZ - 1))" "$((CX + 1))" "$Y" "$((CZ + 1))" minecraft:glowstone 0
    fill_box "$((CX - 1))" "$((Y + 1))" "$((CZ - 1))" "$((CX + 1))" "$((Y + 4))" "$((CZ + 1))" minecraft:air 0

    for dx in -9 9; do
        for dz in -9 9; do
            ox=$((CX + dx))
            oz=$((CZ + dz))
            fill_box "$((ox - 1))" "$Y" "$((oz - 1))" "$((ox + 1))" "$Y" "$((oz + 1))" minecraft:quartz_block 0
            fill_box "$ox" "$((Y + 1))" "$oz" "$ox" "$((Y + 6))" "$oz" minecraft:nether_brick_fence 0
            set_block "$ox" "$((Y + 7))" "$oz" minecraft:sea_lantern 0
        done
    done

    lamp_post "$((CX - 15))" "$((CZ - 15))"
    lamp_post "$((CX + 15))" "$((CZ - 15))"
    lamp_post "$((CX - 15))" "$((CZ + 15))"
    lamp_post "$((CX + 15))" "$((CZ + 15))"
}

build_gardens() {
    fill_box "$((CX - 32))" "$Y" "$((CZ - 32))" "$((CX - 20))" "$Y" "$((CZ - 20))" minecraft:grass 0
    fill_box "$((CX + 20))" "$Y" "$((CZ - 32))" "$((CX + 32))" "$Y" "$((CZ - 20))" minecraft:grass 0
    fill_box "$((CX - 32))" "$Y" "$((CZ + 20))" "$((CX - 20))" "$Y" "$((CZ + 32))" minecraft:grass 0
    fill_box "$((CX + 20))" "$Y" "$((CZ + 20))" "$((CX + 32))" "$Y" "$((CZ + 32))" minecraft:grass 0

    tree "$((CX - 26))" "$((CZ - 26))"
    tree "$((CX + 26))" "$((CZ - 26))"
    tree "$((CX - 26))" "$((CZ + 26))"
    tree "$((CX + 26))" "$((CZ + 26))"

    flower_patch "$((CX - 31))" "$((CZ - 31))" "$((CX - 21))" "$((CZ - 21))" 4
    flower_patch "$((CX + 21))" "$((CZ - 31))" "$((CX + 31))" "$((CZ - 21))" 1
    flower_patch "$((CX - 31))" "$((CZ + 21))" "$((CX - 21))" "$((CZ + 31))" 5
    flower_patch "$((CX + 21))" "$((CZ + 21))" "$((CX + 31))" "$((CZ + 31))" 6

    fill_box "$((CX - 30))" "$((Y + 1))" "$((CZ - 17))" "$((CX - 23))" "$((Y + 1))" "$((CZ - 14))" minecraft:water 0
    fill_box "$((CX + 23))" "$((Y + 1))" "$((CZ + 14))" "$((CX + 30))" "$((Y + 1))" "$((CZ + 17))" minecraft:water 0
    fill_box "$((CX - 31))" "$Y" "$((CZ - 18))" "$((CX - 22))" "$Y" "$((CZ - 13))" minecraft:quartz_block 0
    fill_box "$((CX + 22))" "$Y" "$((CZ + 13))" "$((CX + 31))" "$Y" "$((CZ + 18))" minecraft:quartz_block 0
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
}

build_market() {
    stall "$((CX - 17))" "$((CZ - 7))" 14
    stall "$((CX - 17))" "$((CZ + 7))" 1
    stall "$((CX + 17))" "$((CZ - 7))" 4
    stall "$((CX + 17))" "$((CZ + 7))" 11
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

for value in "$CX" "$Y" "$CZ" "$ROAD_LENGTH" "$HUB_RADIUS" "$WALL_RADIUS" "$FOUNDATION_DEPTH" "$CLEAR_ABOVE_HEIGHT"; do
    if ! is_int "$value"; then
        usage
        exit 2
    fi
done

if [ "$HUB_RADIUS" -lt 30 ]; then
    echo "HUB_RADIUS must be at least 30." >&2
    exit 2
fi

if [ "$WALL_RADIUS" -lt 28 ] || [ "$WALL_RADIUS" -ge "$HUB_RADIUS" ]; then
    echo "WALL_RADIUS must be at least 28 and smaller than HUB_RADIUS." >&2
    exit 2
fi

if [ -x "$ROAD_BUILDER" ]; then
    echo "Building season roads before the new spawn hub..."
    DECORATE_SPAWN=0 \
        FOUNDATION_DEPTH="$FOUNDATION_DEPTH" \
        CLEAR_ABOVE_HEIGHT=18 \
        "$ROAD_BUILDER" "$CX" "$Y" "$CZ" "$ROAD_LENGTH"
else
    echo "Road builder not executable, continuing with hub only: $ROAD_BUILDER" >&2
fi

SUPPORT_Y=$((Y - 1))
FOUNDATION_Y1=$((Y - FOUNDATION_DEPTH))
if [ "$FOUNDATION_Y1" -lt 0 ]; then
    FOUNDATION_Y1=0
fi
HEAD_Y1=$((Y + 1))
HEAD_Y2=$((Y + CLEAR_ABOVE_HEIGHT))

echo "Building ObsidianGate new spawn hub at $CX $Y $CZ..."
echo "Hub radius: $HUB_RADIUS, wall radius: $WALL_RADIUS, foundation y=$FOUNDATION_Y1..$SUPPORT_Y."

fill_tiled "$((CX - HUB_RADIUS))" "$FOUNDATION_Y1" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$SUPPORT_Y" "$((CZ + HUB_RADIUS))" minecraft:dirt 0
fill_tiled "$((CX - HUB_RADIUS))" "$Y" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$Y" "$((CZ + HUB_RADIUS))" minecraft:stonebrick 0
fill_tiled "$((CX - HUB_RADIUS))" "$HEAD_Y1" "$((CZ - HUB_RADIUS))" "$((CX + HUB_RADIUS))" "$HEAD_Y2" "$((CZ + HUB_RADIUS))" minecraft:air 0

build_plaza
build_walls
tower "$((CX - WALL_RADIUS))" "$((CZ - WALL_RADIUS))"
tower "$((CX + WALL_RADIUS))" "$((CZ - WALL_RADIUS))"
tower "$((CX - WALL_RADIUS))" "$((CZ + WALL_RADIUS))"
tower "$((CX + WALL_RADIUS))" "$((CZ + WALL_RADIUS))"
build_gardens
build_market

run_rcon "fill $((CX - 3)) $((Y + 1)) $((CZ - 3)) $((CX + 3)) $((Y + 4)) $((CZ + 3)) minecraft:air 0 replace"
run_rcon "setworldspawn $CX $((Y + 1)) $CZ"

echo "New spawn hub complete."
