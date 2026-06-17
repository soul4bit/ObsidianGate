#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
RCON_COMMAND="${RCON_COMMAND:-$SERVER_ROOT/scripts/obsidiangate-rcon-command.sh}"
ROAD_LENGTH="${4:-1000}"
ROAD_WIDTH="${ROAD_WIDTH:-4}"
CHUNK_LENGTH="${CHUNK_LENGTH:-512}"
LIGHT_EVERY="${LIGHT_EVERY:-12}"
MAX_LENGTH="${MAX_LENGTH:-20000}"

usage() {
    echo "Usage: $0 <center-x> <surface-y> <center-z> [length]" >&2
    echo "Example: $0 0 64 0 3000" >&2
}

is_int() {
    case "$1" in
        ""|-) return 1 ;;
        -*) case "${1#-}" in ""|*[!0-9]*) return 1 ;; *) return 0 ;; esac ;;
        *[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

run_rcon() {
    printf '%s\n' "$*"
    "$RCON_COMMAND" "$@" >/dev/null
}

fill_chunked() {
    x1="$1"
    y1="$2"
    z1="$3"
    x2="$4"
    y2="$5"
    z2="$6"
    block="$7"
    data="$8"

    dx=$((x2 - x1))
    dz=$((z2 - z1))
    [ "$dx" -lt 0 ] && dx=$((-dx))
    [ "$dz" -lt 0 ] && dz=$((-dz))

    if [ "$dz" -ge "$dx" ]; then
        start="$z1"
        end="$z2"
        step_axis="z"
    else
        start="$x1"
        end="$x2"
        step_axis="x"
    fi

    if [ "$start" -le "$end" ]; then
        cur="$start"
        while [ "$cur" -le "$end" ]; do
            next=$((cur + CHUNK_LENGTH - 1))
            [ "$next" -gt "$end" ] && next="$end"
            if [ "$step_axis" = "z" ]; then
                run_rcon "fill $x1 $y1 $cur $x2 $y2 $next $block $data replace"
            else
                run_rcon "fill $cur $y1 $z1 $next $y2 $z2 $block $data replace"
            fi
            cur=$((next + 1))
        done
    else
        cur="$start"
        while [ "$cur" -ge "$end" ]; do
            next=$((cur - CHUNK_LENGTH + 1))
            [ "$next" -lt "$end" ] && next="$end"
            if [ "$step_axis" = "z" ]; then
                run_rcon "fill $x1 $y1 $next $x2 $y2 $cur $block $data replace"
            else
                run_rcon "fill $next $y1 $z1 $cur $y2 $z2 $block $data replace"
            fi
            cur=$((next - 1))
        done
    fi
}

place_lights_z() {
    x1="$1"
    x2="$2"
    y="$3"
    start="$4"
    end="$5"
    cur="$start"
    if [ "$start" -le "$end" ]; then
        while [ "$cur" -le "$end" ]; do
            run_rcon "fill $x1 $y $cur $x2 $y $cur minecraft:glowstone 0 replace"
            cur=$((cur + LIGHT_EVERY))
        done
    else
        while [ "$cur" -ge "$end" ]; do
            run_rcon "fill $x1 $y $cur $x2 $y $cur minecraft:glowstone 0 replace"
            cur=$((cur - LIGHT_EVERY))
        done
    fi
}

place_lights_x() {
    z1="$1"
    z2="$2"
    y="$3"
    start="$4"
    end="$5"
    cur="$start"
    if [ "$start" -le "$end" ]; then
        while [ "$cur" -le "$end" ]; do
            run_rcon "fill $cur $y $z1 $cur $y $z2 minecraft:glowstone 0 replace"
            cur=$((cur + LIGHT_EVERY))
        done
    else
        while [ "$cur" -ge "$end" ]; do
            run_rcon "fill $cur $y $z1 $cur $y $z2 minecraft:glowstone 0 replace"
            cur=$((cur - LIGHT_EVERY))
        done
    fi
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

for value in "$CX" "$Y" "$CZ" "$ROAD_LENGTH"; do
    if ! is_int "$value"; then
        usage
        exit 2
    fi
done

if [ "$ROAD_WIDTH" -ne 4 ]; then
    echo "Only ROAD_WIDTH=4 is supported right now." >&2
    exit 2
fi

if [ "$ROAD_LENGTH" -le 0 ] || [ "$ROAD_LENGTH" -gt "$MAX_LENGTH" ]; then
    echo "length must be between 1 and $MAX_LENGTH; override MAX_LENGTH only if you really mean it." >&2
    exit 2
fi

X1=$((CX - 2))
X2=$((CX + 1))
Z1=$((CZ - 2))
Z2=$((CZ + 1))
HEAD_Y1=$((Y + 1))
HEAD_Y2=$((Y + 4))

echo "Building four 4-wide roads from $CX $Y $CZ, length $ROAD_LENGTH..."

fill_chunked "$X1" "$Y" "$CZ" "$X2" "$Y" "$((CZ + ROAD_LENGTH))" minecraft:wool 14
fill_chunked "$X1" "$HEAD_Y1" "$((CZ + 1))" "$X2" "$HEAD_Y2" "$((CZ + ROAD_LENGTH))" minecraft:air 0
place_lights_z "$X1" "$X2" "$Y" "$CZ" "$((CZ + ROAD_LENGTH))"

fill_chunked "$X1" "$Y" "$CZ" "$X2" "$Y" "$((CZ - ROAD_LENGTH))" minecraft:wool 14
fill_chunked "$X1" "$HEAD_Y1" "$((CZ - 1))" "$X2" "$HEAD_Y2" "$((CZ - ROAD_LENGTH))" minecraft:air 0
place_lights_z "$X1" "$X2" "$Y" "$CZ" "$((CZ - ROAD_LENGTH))"

fill_chunked "$CX" "$Y" "$Z1" "$((CX + ROAD_LENGTH))" "$Y" "$Z2" minecraft:wool 14
fill_chunked "$((CX + 1))" "$HEAD_Y1" "$Z1" "$((CX + ROAD_LENGTH))" "$HEAD_Y2" "$Z2" minecraft:air 0
place_lights_x "$Z1" "$Z2" "$Y" "$CX" "$((CX + ROAD_LENGTH))"

fill_chunked "$CX" "$Y" "$Z1" "$((CX - ROAD_LENGTH))" "$Y" "$Z2" minecraft:wool 14
fill_chunked "$((CX - 1))" "$HEAD_Y1" "$Z1" "$((CX - ROAD_LENGTH))" "$HEAD_Y2" "$Z2" minecraft:air 0
place_lights_x "$Z1" "$Z2" "$Y" "$CX" "$((CX - ROAD_LENGTH))"

run_rcon "fill $((CX - 3)) $Y $((CZ - 3)) $((CX + 3)) $Y $((CZ + 3)) minecraft:glowstone 0 replace"
echo "Done."
