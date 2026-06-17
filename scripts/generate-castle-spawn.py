#!/usr/bin/env python3
"""Generate the ObsidianGate castle spawn WorldEdit schematic.

The output is a classic MCEdit .schematic for WorldEdit 6.x on Minecraft 1.12.2.
Modded blocks are written as numeric Forge block IDs from the current server
world registry, so regenerate this file if the mod list or world registry is
reset.
"""

from __future__ import annotations

import gzip
import random
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "modpack/server/config/worldedit/schematics/obsidiangate_castle_spawn.schematic"

W, H, L = 96, 38, 96
CENTER_X, CENTER_Z = W // 2, L // 2

AIR = 0
GRASS = 2
COBBLE = 4
PLANKS = 5
WATER = 9
GRAVEL = 13
LOG = 17
LEAVES = 18
LAPIS = 22
WOOL = 35
GOLD = 41
TORCH = 50
FENCE = 85
GLOWSTONE = 89
STONE_BRICK = 98
IRON_BARS = 101
GLASS_PANE = 102
QUARTZ = 155
SEA_LANTERN = 169

# Forge block IDs from /home/minecraft/mc-rpg/world-season-20260617-v2/level.dat.
TC_STONE_ARCANE = 983
TC_STONE_ARCANE_BRICK = 984
TC_STONE_ANCIENT = 985
TC_STONE_ANCIENT_TILE = 986
TC_STONE_ELDRITCH_TILE = 990
TC_LOG_GREATWOOD = 1005
TC_LOG_SILVERWOOD = 1006
TC_PLANK_GREATWOOD = 1012
TC_PLANK_SILVERWOOD = 1013
TC_PAVING_TRAVEL = 1047
TC_PAVING_BARRIER = 1048
TC_PILLAR_ARCANE = 1049
TC_LAMP_ARCANE = 1112

DRPG_BLOODGEM_BLOCK = 1710
DRPG_ARLEMITE_BRICKS = 1748
DRPG_DARKSTONE_BRICKS = 1749
DRPG_EDEN_BRICKS = 1761
DRPG_WILDWOOD_BRICKS = 1762
DRPG_APALACHIA_BRICKS = 1763
DRPG_SKYTHERN_BRICKS = 1764
DRPG_MORTUM_BRICKS = 1765
DRPG_ARLEMITE_LAMP = 1774
DRPG_BLUEFIRE_LAMP = 1776
DRPG_DIVINE_LAMP = 1778
DRPG_DARKSTONE = 1806
DRPG_DIVINE_ROCK = 1822
DRPG_ANCIENT_BRICKS = 1965
DRPG_ANCIENT_STONE = 1967
DRPG_ANCIENT_TILE = 1968

ids = [AIR] * (W * H * L)
data = bytearray(W * H * L)
rng = random.Random(20260618)


def index(x: int, y: int, z: int) -> int:
    return y * W * L + z * W + x


def in_bounds(x: int, y: int, z: int) -> bool:
    return 0 <= x < W and 0 <= y < H and 0 <= z < L


def setb(x: int, y: int, z: int, block_id: int, meta: int = 0) -> None:
    if not in_bounds(x, y, z):
        return
    if not 0 <= block_id <= 4095:
        raise ValueError(f"Block ID out of MCEdit schematic range: {block_id}")
    i = index(x, y, z)
    ids[i] = block_id
    data[i] = meta & 0xF


def getb(x: int, y: int, z: int) -> int:
    return ids[index(x, y, z)] if in_bounds(x, y, z) else AIR


def fill(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block_id: int, meta: int = 0) -> None:
    xa, xb = sorted((x1, x2))
    ya, yb = sorted((y1, y2))
    za, zb = sorted((z1, z2))
    for y in range(max(0, ya), min(H - 1, yb) + 1):
        for z in range(max(0, za), min(L - 1, zb) + 1):
            for x in range(max(0, xa), min(W - 1, xb) + 1):
                setb(x, y, z, block_id, meta)


def aged_wall_block(x: int, y: int, z: int) -> None:
    palette = (
        (TC_STONE_ARCANE_BRICK, 0, 62),
        (TC_STONE_ANCIENT_TILE, 0, 12),
        (DRPG_ANCIENT_BRICKS, 0, 10),
        (DRPG_DARKSTONE_BRICKS, 0, 8),
        (TC_STONE_ARCANE, 0, 5),
        (COBBLE, 0, 3),
    )
    pick = rng.randrange(sum(weight for _, _, weight in palette))
    total = 0
    for block_id, meta, weight in palette:
        total += weight
        if pick < total:
            setb(x, y, z, block_id, meta)
            return


def fill_aged(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int) -> None:
    xa, xb = sorted((x1, x2))
    ya, yb = sorted((y1, y2))
    za, zb = sorted((z1, z2))
    for y in range(max(0, ya), min(H - 1, yb) + 1):
        for z in range(max(0, za), min(L - 1, zb) + 1):
            for x in range(max(0, xa), min(W - 1, xb) + 1):
                aged_wall_block(x, y, z)


def tower(x1: int, z1: int, x2: int, z2: int, height: int, accent: int) -> None:
    fill_aged(x1, 1, z1, x2, height, z2)
    fill(x1 + 3, 2, z1 + 3, x2 - 3, height - 2, z2 - 3, AIR)
    fill(x1 + 3, 0, z1 + 3, x2 - 3, 0, z2 - 3, TC_PAVING_BARRIER)
    fill(x1 + 3, 11, z1 + 3, x2 - 3, 11, z2 - 3, TC_PLANK_GREATWOOD)
    fill(x1 + 3, 21, z1 + 3, x2 - 3, 21, z2 - 3, TC_PLANK_SILVERWOOD)
    fill(x1 + 1, 1, z1 + 1, x1 + 2, height, z1 + 2, accent)
    fill(x2 - 2, 1, z2 - 2, x2 - 1, height, z2 - 1, accent)

    mx = (x1 + x2) // 2
    mz = (z1 + z2) // 2
    for yy in (7, 8, 17, 18):
        for dx in (-1, 0, 1):
            setb(mx + dx, yy, z1, AIR)
            setb(mx + dx, yy, z2, AIR)
            setb(x1, yy, mz + dx, AIR)
            setb(x2, yy, mz + dx, AIR)
        setb(mx, yy, z1, GLASS_PANE)
        setb(mx, yy, z2, GLASS_PANE)
        setb(x1, yy, mz, GLASS_PANE)
        setb(x2, yy, mz, GLASS_PANE)

    fill_aged(x1 - 1, height + 1, z1 - 1, x2 + 1, height + 1, z2 + 1)
    fill(x1 + 3, height + 2, z1 + 3, x2 - 3, height + 2, z2 - 3, AIR)
    for x in range(x1 - 1, x2 + 2):
        for z in (z1 - 1, z2 + 1):
            if (x - x1) % 3 != 1:
                fill_aged(x, height + 2, z, x, height + 4, z)
    for z in range(z1 - 1, z2 + 2):
        for x in (x1 - 1, x2 + 1):
            if (z - z1) % 3 != 1:
                fill_aged(x, height + 2, z, x, height + 4, z)
    setb(mx, height + 5, mz, TC_LAMP_ARCANE)


def wall_line_horizontal(z1: int, z2: int) -> None:
    fill_aged(18, 1, z1, 77, 13, z2)
    fill(20, 2, z1 + 1, 75, 11, z2 - 1, AIR)
    fill_aged(18, 13, z1, 77, 13, z2)
    for x in range(18, 78):
        if x % 4 in (0, 1):
            for z in (z1, z2):
                fill_aged(x, 14, z, x, 16, z)


def wall_line_vertical(x1: int, x2: int) -> None:
    fill_aged(x1, 1, 18, x2, 13, 77)
    fill(x1 + 1, 2, 20, x2 - 1, 11, 75, AIR)
    fill_aged(x1, 13, 18, x2, 13, 77)
    for z in range(18, 78):
        if z % 4 in (0, 1):
            for x in (x1, x2):
                fill_aged(x, 14, z, x, 16, z)


def gatehouse() -> None:
    fill_aged(34, 1, 75, 61, 20, 91)
    fill(38, 2, 78, 57, 18, 88, AIR)
    fill_aged(34, 1, 75, 42, 22, 91)
    fill_aged(53, 1, 75, 61, 22, 91)
    fill(37, 2, 79, 39, 18, 87, AIR)
    fill(56, 2, 79, 58, 18, 87, AIR)
    clear_arch(CENTER_X, 75, CENTER_X, 91, "z")
    for x in range(CENTER_X - 5, CENTER_X + 6):
        if x % 2 == 0:
            fill(x, 9, 84, x, 14, 84, IRON_BARS)
    fill_aged(33, 21, 74, 62, 21, 92)
    for x in range(33, 63):
        if x % 3 != 0:
            fill_aged(x, 22, 74, x, 24, 74)
            fill_aged(x, 22, 92, x, 24, 92)
    for z in range(74, 93):
        if z % 3 != 0:
            fill_aged(33, 22, z, 33, 24, z)
            fill_aged(62, 22, z, 62, 24, z)
    for x in (40, 56):
        fill(x, 16, 74, x, 19, 74, WOOL, 14)
        setb(x, 17, 74, GLOWSTONE)


def clear_arch(cx1: int, cz1: int, cx2: int, cz2: int, axis: str) -> None:
    if axis == "z":
        for z in range(min(cz1, cz2), max(cz1, cz2) + 1):
            for y in range(1, 11):
                half = 5 if y < 7 else max(1, 5 - (y - 6))
                for x in range(cx1 - half, cx1 + half + 1):
                    setb(x, y, z, AIR)
    else:
        for x in range(min(cx1, cx2), max(cx1, cx2) + 1):
            for y in range(1, 11):
                half = 5 if y < 7 else max(1, 5 - (y - 6))
                for z in range(cz1 - half, cz1 + half + 1):
                    setb(x, y, z, AIR)


def tree(cx: int, cz: int, log_id: int, leaf_meta: int = 0) -> None:
    fill(cx, 1, cz, cx, 5, cz, log_id)
    for y in range(5, 9):
        radius = 3 if y < 8 else 1
        for z in range(cz - radius, cz + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                if abs(x - cx) + abs(z - cz) <= radius + 1 and getb(x, y, z) == AIR:
                    setb(x, y, z, LEAVES, leaf_meta)


def lamp_post(x: int, z: int, lamp_id: int) -> None:
    fill(x, 1, z, x, 4, z, FENCE)
    setb(x, 5, z, lamp_id)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        setb(x + dx, 4, z + dz, TORCH)


def carve_roads() -> None:
    road_offsets = (-2, -1, 0, 1)
    clear_offsets = (-3, -2, -1, 0, 1, 2)

    for z in range(L):
        for dx in clear_offsets:
            fill(CENTER_X + dx, 1, z, CENTER_X + dx, 8, z, AIR)
        for dx in road_offsets:
            setb(CENTER_X + dx, 0, z, WOOL, 14)
            if z % 9 == 0 or abs(z - CENTER_Z) <= 1:
                setb(CENTER_X + dx, 0, z, GLOWSTONE)

    for x in range(W):
        for dz in clear_offsets:
            fill(x, 1, CENTER_Z + dz, x, 8, CENTER_Z + dz, AIR)
        for dz in road_offsets:
            setb(x, 0, CENTER_Z + dz, WOOL, 14)
            if x % 9 == 0 or abs(x - CENTER_X) <= 1:
                setb(x, 0, CENTER_Z + dz, GLOWSTONE)

    # Keep the center visibly special after both roads cross.
    fill(CENTER_X - 3, 0, CENTER_Z - 3, CENTER_X + 3, 0, CENTER_Z + 3, GLOWSTONE)
    fill(CENTER_X - 1, 1, CENTER_Z - 1, CENTER_X + 1, 1, CENTER_Z + 1, DRPG_BLOODGEM_BLOCK)
    setb(CENTER_X, 2, CENTER_Z, TC_LAMP_ARCANE)


def build_scene() -> None:
    for z in range(L):
        for x in range(W):
            if 3 <= x <= 92 and 3 <= z <= 92 and (x < 8 or x > 87 or z < 8 or z > 87):
                setb(x, 0, z, WATER)
            else:
                setb(x, 0, z, GRASS)

    fill(7, 0, 7, 88, 0, 88, TC_PAVING_BARRIER)
    fill(22, 0, 22, 73, 0, 73, GRASS)

    for z in range(26, 71):
        for x in range(26, 71):
            dx = x - CENTER_X
            dz = z - CENTER_Z
            d2 = dx * dx + dz * dz
            if d2 <= 16 * 16:
                setb(x, 0, z, TC_PAVING_TRAVEL)
            if 9 * 9 <= d2 <= 10 * 10:
                setb(x, 0, z, DRPG_ANCIENT_TILE)
            if d2 <= 4 * 4:
                setb(x, 0, z, DRPG_DIVINE_ROCK)

    wall_line_horizontal(12, 15)
    wall_line_horizontal(80, 83)
    wall_line_vertical(12, 15)
    wall_line_vertical(80, 83)
    tower(7, 7, 22, 22, 24, DRPG_EDEN_BRICKS)
    tower(73, 7, 88, 22, 24, DRPG_APALACHIA_BRICKS)
    tower(7, 73, 22, 88, 24, DRPG_SKYTHERN_BRICKS)
    tower(73, 73, 88, 88, 24, DRPG_MORTUM_BRICKS)
    gatehouse()

    fill(CENTER_X - 7, 0, 84, CENTER_X + 7, 0, 95, TC_PLANK_GREATWOOD)
    for z in range(84, 96):
        for x in (CENTER_X - 8, CENTER_X + 8):
            setb(x, 1, z, FENCE)
            if z % 4 == 0:
                fill(x, 1, z, x, 3, z, FENCE)
                setb(x, 4, z, DRPG_DIVINE_LAMP)

    # Split the inner keep so the north road can pass cleanly through the spawn.
    fill_aged(25, 1, 25, 37, 12, 37)
    fill(28, 2, 28, 34, 10, 34, AIR)
    fill_aged(59, 1, 25, 71, 12, 37)
    fill(62, 2, 28, 68, 10, 34, AIR)
    for box in ((24, 13, 24, 38, 13, 38), (58, 13, 24, 72, 13, 38)):
        fill_aged(*box)
    for x in (31, 65):
        setb(x, 14, 31, TC_LAMP_ARCANE)

    for x, z, lamp in (
        (31, 31, TC_LAMP_ARCANE),
        (65, 31, DRPG_ARLEMITE_LAMP),
        (31, 65, DRPG_BLUEFIRE_LAMP),
        (65, 65, DRPG_DIVINE_LAMP),
        (48, 24, TC_LAMP_ARCANE),
        (48, 72, DRPG_DIVINE_LAMP),
        (24, 48, DRPG_ARLEMITE_LAMP),
        (72, 48, DRPG_BLUEFIRE_LAMP),
    ):
        lamp_post(x, z, lamp)

    tree(27, 27, TC_LOG_GREATWOOD)
    tree(69, 27, TC_LOG_SILVERWOOD)
    tree(27, 69, LOG)
    tree(69, 69, TC_LOG_GREATWOOD)

    carve_roads()


def write_nbt_string(out: bytearray, value: str) -> None:
    encoded = value.encode("utf-8")
    out.extend(struct.pack(">h", len(encoded)))
    out.extend(encoded)


def write_named(out: bytearray, tag_type: int, name: str) -> None:
    out.append(tag_type)
    write_nbt_string(out, name)


def write_schematic() -> None:
    low_blocks = bytearray(len(ids))
    add_blocks = bytearray((len(ids) + 1) // 2)
    uses_add_blocks = False
    for i, block_id in enumerate(ids):
        low_blocks[i] = block_id & 0xFF
        high = (block_id >> 8) & 0xF
        if high:
            uses_add_blocks = True
        if i & 1:
            add_blocks[i >> 1] |= high << 4
        else:
            add_blocks[i >> 1] |= high

    out = bytearray()

    def tag_short(name: str, value: int) -> None:
        write_named(out, 2, name)
        out.extend(struct.pack(">h", value))

    def tag_int(name: str, value: int) -> None:
        write_named(out, 3, name)
        out.extend(struct.pack(">i", value))

    def tag_string(name: str, value: str) -> None:
        write_named(out, 8, name)
        write_nbt_string(out, value)

    def tag_byte_array(name: str, value: bytes | bytearray) -> None:
        write_named(out, 7, name)
        out.extend(struct.pack(">i", len(value)))
        out.extend(value)

    def tag_empty_compound_list(name: str) -> None:
        write_named(out, 9, name)
        out.append(10)
        out.extend(struct.pack(">i", 0))

    write_named(out, 10, "Schematic")
    tag_short("Width", W)
    tag_short("Height", H)
    tag_short("Length", L)
    tag_string("Materials", "Alpha")
    tag_byte_array("Blocks", low_blocks)
    tag_byte_array("Data", data)
    if uses_add_blocks:
        tag_byte_array("AddBlocks", add_blocks)
    tag_empty_compound_list("Entities")
    tag_empty_compound_list("TileEntities")
    tag_int("WEOriginX", 0)
    tag_int("WEOriginY", 64)
    tag_int("WEOriginZ", 0)
    tag_int("WEOffsetX", -CENTER_X)
    tag_int("WEOffsetY", -1)
    tag_int("WEOffsetZ", -CENTER_Z)
    out.append(0)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(gzip.compress(bytes(out), compresslevel=9))
    print(OUTPUT)
    print(f"dimensions={W}x{H}x{L}")
    print(f"offset={-CENTER_X},-1,{-CENTER_Z}")
    print(f"size={OUTPUT.stat().st_size}")
    print(f"add_blocks={uses_add_blocks}")


def main() -> None:
    build_scene()
    write_schematic()


if __name__ == "__main__":
    main()
