#!/usr/bin/env python3
"""Generate a pasteable ObsidianGate WorldEdit schematic.

The output is a classic MCEdit .schematic for WorldEdit 6.x on Minecraft 1.12.2.
It uses vanilla numeric IDs so the schematic survives Forge registry changes.
The WorldEdit offset is centered: stand on the target ground block and paste.
"""

from __future__ import annotations

import gzip
import json
import math
import random
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "modpack/server/config/worldedit/schematics/obsidiangate_modded_spawn.schematic"

W, H, L = 97, 44, 97
CENTER_X, CENTER_Z = W // 2, L // 2
SURFACE_Y = 1

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

AIR = 0
STONE = 1
GRASS = 2
DIRT = 3
COBBLE = 4
PLANKS = 5
BEDROCK = 7
WATER = 9
GRAVEL = 13
LOG = 17
LEAVES = 18
LAPIS = 22
DISPENSER = 23
SANDSTONE = 24
WOOL = 35
YELLOW_FLOWER = 37
RED_FLOWER = 38
GOLD_BLOCK = 41
IRON_BLOCK = 42
BRICK = 45
BOOKSHELF = 47
MOSSY_COBBLE = 48
OBSIDIAN = 49
TORCH = 50
FENCE = 85
NETHERRACK = 87
SOUL_SAND = 88
GLOWSTONE = 89
JACK_O_LANTERN = 91
STAINED_GLASS = 95
TRAPDOOR = 96
STONE_BRICK = 98
IRON_BARS = 101
GLASS_PANE = 102
VINE = 106
WATERLILY = 111
NETHER_BRICK = 112
NETHER_BRICK_FENCE = 113
ENCHANTING_TABLE = 116
BREWING_STAND = 117
CAULDRON = 118
END_STONE = 121
REDSTONE_BLOCK = 152
QUARTZ = 155
STAINED_CLAY = 159
SEA_LANTERN = 169
CARPET = 171
CONCRETE = 251
SIGN_POST = 63
WALL_SIGN = 68

ids = [AIR] * (W * H * L)
data = bytearray(W * H * L)
tile_entities: list[list[tuple[int, str, object]]] = []
rng = random.Random(20260619)


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


def disc(cx: int, zc: int, radius: int, y: int, block_id: int, meta: int = 0) -> None:
    r2 = radius * radius
    for z in range(zc - radius, zc + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            if (x - cx) * (x - cx) + (z - zc) * (z - zc) <= r2:
                setb(x, y, z, block_id, meta)


def ring(cx: int, zc: int, inner: int, outer: int, y: int, block_id: int, meta: int = 0) -> None:
    inner2 = inner * inner
    outer2 = outer * outer
    for z in range(zc - outer, zc + outer + 1):
        for x in range(cx - outer, cx + outer + 1):
            d2 = (x - cx) * (x - cx) + (z - zc) * (z - zc)
            if inner2 <= d2 <= outer2:
                setb(x, y, z, block_id, meta)


def line_road_x(z1: int, z2: int, y: int, x: int) -> None:
    for z in range(z1, z2 + 1):
        for dx in range(-3, 4):
            block = STONE_BRICK if abs(dx) == 3 else QUARTZ
            setb(x + dx, y, z, block)
        if z % 7 == 0:
            setb(x - 1, y, z, SEA_LANTERN)
            setb(x + 1, y, z, SEA_LANTERN)


def line_road_z(x1: int, x2: int, y: int, z: int) -> None:
    for x in range(x1, x2 + 1):
        for dz in range(-3, 4):
            block = STONE_BRICK if abs(dz) == 3 else QUARTZ
            setb(x, y, z + dz, block)
        if x % 7 == 0:
            setb(x, y, z - 1, SEA_LANTERN)
            setb(x, y, z + 1, SEA_LANTERN)


def pillar(x: int, z: int, height: int, base: int, shaft: int, cap: int, meta: int = 0) -> None:
    fill(x - 1, SURFACE_Y, z - 1, x + 1, SURFACE_Y, z + 1, base)
    fill(x, SURFACE_Y + 1, z, x, SURFACE_Y + height, z, shaft, meta)
    setb(x, SURFACE_Y + height + 1, z, cap)


def lamp(x: int, z: int) -> None:
    fill(x, SURFACE_Y + 1, z, x, SURFACE_Y + 4, z, FENCE)
    setb(x, SURFACE_Y + 5, z, SEA_LANTERN)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        setb(x + dx, SURFACE_Y + 4, z + dz, TORCH)


def add_sign(x: int, y: int, z: int, lines: tuple[str, str, str, str], rotation: int = 0) -> None:
    setb(x, y, z, SIGN_POST, rotation)
    tile_entities.append([
        (TAG_STRING, "id", "Sign"),
        (TAG_INT, "x", x),
        (TAG_INT, "y", y),
        (TAG_INT, "z", z),
        (TAG_STRING, "Text1", json.dumps({"text": lines[0]}, separators=(",", ":"))),
        (TAG_STRING, "Text2", json.dumps({"text": lines[1]}, separators=(",", ":"))),
        (TAG_STRING, "Text3", json.dumps({"text": lines[2]}, separators=(",", ":"))),
        (TAG_STRING, "Text4", json.dumps({"text": lines[3]}, separators=(",", ":"))),
    ])


def tree(cx: int, zc: int, leaf_meta: int = 0) -> None:
    fill(cx, SURFACE_Y + 1, zc, cx, SURFACE_Y + 5, zc, LOG)
    for y in range(SURFACE_Y + 5, SURFACE_Y + 9):
        radius = 3 if y < SURFACE_Y + 8 else 1
        for z in range(zc - radius, zc + radius + 1):
            for x in range(cx - radius, cx + radius + 1):
                if abs(x - cx) + abs(z - zc) <= radius + 1 and getb(x, y, z) == AIR:
                    setb(x, y, z, LEAVES, leaf_meta)


def crystal_cluster(cx: int, zc: int, glass_meta: int) -> None:
    heights = (4, 7, 5, 6, 3)
    offsets = ((0, 0), (-2, 1), (2, 1), (1, -2), (-1, -2))
    for (dx, dz), height in zip(offsets, heights):
        x = cx + dx
        z = zc + dz
        fill(x, SURFACE_Y + 1, z, x, SURFACE_Y + height, z, STAINED_GLASS, glass_meta)
        setb(x, SURFACE_Y + height + 1, z, SEA_LANTERN)


def obsidian_gate() -> None:
    # A sculpted portal frame, intentionally without actual portal blocks.
    for side in (-1, 1):
        x = CENTER_X + side * 6
        fill(x - 1, SURFACE_Y + 1, CENTER_Z - 1, x + 1, SURFACE_Y + 16, CENTER_Z + 1, OBSIDIAN)
        fill(x, SURFACE_Y + 2, CENTER_Z, x, SURFACE_Y + 14, CENTER_Z, STAINED_GLASS, 10)
    for dx in range(-6, 7):
        arch_y = SURFACE_Y + 15 + int(round(math.sin((dx + 6) / 12 * math.pi) * 4))
        fill(CENTER_X + dx, arch_y, CENTER_Z - 1, CENTER_X + dx, arch_y + 1, CENTER_Z + 1, OBSIDIAN)
        if abs(dx) <= 4:
            setb(CENTER_X + dx, arch_y - 1, CENTER_Z, STAINED_GLASS, 10)
    fill(CENTER_X - 4, SURFACE_Y + 3, CENTER_Z, CENTER_X + 4, SURFACE_Y + 12, CENTER_Z, STAINED_GLASS, 10)
    fill(CENTER_X - 2, SURFACE_Y + 5, CENTER_Z, CENTER_X + 2, SURFACE_Y + 10, CENTER_Z, STAINED_GLASS, 2)
    pillar(CENTER_X - 9, CENTER_Z - 4, 8, NETHER_BRICK, QUARTZ, SEA_LANTERN)
    pillar(CENTER_X + 9, CENTER_Z - 4, 8, NETHER_BRICK, QUARTZ, SEA_LANTERN)
    pillar(CENTER_X - 9, CENTER_Z + 4, 8, NETHER_BRICK, QUARTZ, SEA_LANTERN)
    pillar(CENTER_X + 9, CENTER_Z + 4, 8, NETHER_BRICK, QUARTZ, SEA_LANTERN)


def build_thaumcraft_court() -> None:
    cx, zc = CENTER_X, CENTER_Z - 29
    disc(cx, zc, 13, SURFACE_Y, STONE_BRICK)
    ring(cx, zc, 8, 10, SURFACE_Y, LAPIS)
    ring(cx, zc, 11, 12, SURFACE_Y, QUARTZ)
    fill(cx - 3, SURFACE_Y + 1, zc - 3, cx + 3, SURFACE_Y + 1, zc + 3, BOOKSHELF)
    setb(cx, SURFACE_Y + 2, zc, ENCHANTING_TABLE)
    for x, z in ((cx - 10, zc - 10), (cx + 10, zc - 10), (cx - 10, zc + 10), (cx + 10, zc + 10)):
        pillar(x, z, 6, STONE_BRICK, QUARTZ, SEA_LANTERN)
    crystal_cluster(cx, zc - 7, 10)
    add_sign(cx - 3, SURFACE_Y + 1, zc + 12, ("THAUMCRAFT", "Arcane Court", "Research", "Awakens"), 0)


def build_tech_lab() -> None:
    cx, zc = CENTER_X + 29, CENTER_Z
    disc(cx, zc, 13, SURFACE_Y, IRON_BLOCK)
    ring(cx, zc, 7, 9, SURFACE_Y, REDSTONE_BLOCK)
    ring(cx, zc, 11, 12, SURFACE_Y, LAPIS)
    for x in range(cx - 7, cx + 8, 7):
        for z in range(zc - 7, zc + 8, 7):
            fill(x - 1, SURFACE_Y + 1, z - 1, x + 1, SURFACE_Y + 3, z + 1, DISPENSER, 2)
            setb(x, SURFACE_Y + 4, z, SEA_LANTERN)
    fill(cx - 2, SURFACE_Y + 1, zc - 2, cx + 2, SURFACE_Y + 1, zc + 2, REDSTONE_BLOCK)
    for step in range(0, 13, 2):
        setb(cx - 10 + step, SURFACE_Y + 2 + step // 2, zc - 12, IRON_BARS)
        setb(cx - 10 + step, SURFACE_Y + 2 + step // 2, zc + 12, IRON_BARS)
    add_sign(cx - 4, SURFACE_Y + 1, zc + 13, ("TECH", "Forge Lab", "Power", "Online"), 0)


def build_divinerpg_altar() -> None:
    cx, zc = CENTER_X, CENTER_Z + 29
    disc(cx, zc, 13, SURFACE_Y, NETHER_BRICK)
    ring(cx, zc, 7, 9, SURFACE_Y, END_STONE)
    ring(cx, zc, 11, 12, SURFACE_Y, GLOWSTONE)
    fill(cx - 4, SURFACE_Y + 1, zc - 4, cx + 4, SURFACE_Y + 1, zc + 4, SOUL_SAND)
    fill(cx - 2, SURFACE_Y + 2, zc - 2, cx + 2, SURFACE_Y + 2, zc + 2, GOLD_BLOCK)
    pillar(cx - 10, zc, 9, NETHER_BRICK, OBSIDIAN, GLOWSTONE)
    pillar(cx + 10, zc, 9, NETHER_BRICK, OBSIDIAN, GLOWSTONE)
    pillar(cx, zc - 10, 9, NETHER_BRICK, OBSIDIAN, GLOWSTONE)
    pillar(cx, zc + 10, 9, NETHER_BRICK, OBSIDIAN, GLOWSTONE)
    crystal_cluster(cx, zc, 2)
    add_sign(cx - 4, SURFACE_Y + 1, zc - 13, ("DIVINERPG", "Rift Altar", "Bosses", "Beyond"), 8)


def build_botania_grove() -> None:
    cx, zc = CENTER_X - 29, CENTER_Z
    disc(cx, zc, 13, SURFACE_Y, GRASS)
    ring(cx, zc, 8, 10, SURFACE_Y, MOSSY_COBBLE)
    fill(cx - 4, SURFACE_Y, zc - 4, cx + 4, SURFACE_Y, zc + 4, WATER)
    setb(cx, SURFACE_Y, zc, WATERLILY)
    for x, z in ((cx - 9, zc - 9), (cx + 9, zc - 9), (cx - 9, zc + 9), (cx + 9, zc + 9)):
        tree(x, z)
    for _ in range(80):
        x = rng.randint(cx - 12, cx + 12)
        z = rng.randint(zc - 12, zc + 12)
        if (x - cx) * (x - cx) + (z - zc) * (z - zc) <= 13 * 13 and getb(x, SURFACE_Y + 1, z) == AIR:
            setb(x, SURFACE_Y + 1, z, RED_FLOWER if rng.random() < 0.55 else YELLOW_FLOWER, rng.randint(0, 8))
    add_sign(cx - 4, SURFACE_Y + 1, zc + 13, ("BOTANIA", "Grove", "Mana", "Blooms"), 0)


def build_border() -> None:
    fill(3, SURFACE_Y, 3, W - 4, SURFACE_Y, 3, STONE_BRICK)
    fill(3, SURFACE_Y, L - 4, W - 4, SURFACE_Y, L - 4, STONE_BRICK)
    fill(3, SURFACE_Y, 3, 3, SURFACE_Y, L - 4, STONE_BRICK)
    fill(W - 4, SURFACE_Y, 3, W - 4, SURFACE_Y, L - 4, STONE_BRICK)
    for x in range(5, W - 5, 8):
        lamp(x, 5)
        lamp(x, L - 6)
    for z in range(13, L - 13, 8):
        lamp(5, z)
        lamp(W - 6, z)


def build_scene() -> None:
    fill(0, 0, 0, W - 1, 0, L - 1, DIRT)
    fill(0, SURFACE_Y, 0, W - 1, SURFACE_Y, L - 1, GRASS)
    fill(CENTER_X - 45, SURFACE_Y, CENTER_Z - 45, CENTER_X + 45, SURFACE_Y, CENTER_Z + 45, STONE_BRICK)
    fill(CENTER_X - 42, SURFACE_Y, CENTER_Z - 42, CENTER_X + 42, SURFACE_Y, CENTER_Z + 42, GRASS)

    line_road_x(4, L - 5, SURFACE_Y, CENTER_X)
    line_road_z(4, W - 5, SURFACE_Y, CENTER_Z)
    disc(CENTER_X, CENTER_Z, 18, SURFACE_Y, QUARTZ)
    ring(CENTER_X, CENTER_Z, 12, 14, SURFACE_Y, OBSIDIAN)
    ring(CENTER_X, CENTER_Z, 16, 17, SURFACE_Y, SEA_LANTERN)
    disc(CENTER_X, CENTER_Z, 6, SURFACE_Y, STAINED_CLAY, 10)
    fill(CENTER_X - 2, SURFACE_Y, CENTER_Z - 2, CENTER_X + 2, SURFACE_Y, CENTER_Z + 2, SEA_LANTERN)

    build_border()
    build_thaumcraft_court()
    build_tech_lab()
    build_divinerpg_altar()
    build_botania_grove()
    obsidian_gate()

    for x, z in ((CENTER_X - 17, CENTER_Z - 17), (CENTER_X + 17, CENTER_Z - 17), (CENTER_X - 17, CENTER_Z + 17), (CENTER_X + 17, CENTER_Z + 17)):
        lamp(x, z)

    add_sign(CENTER_X - 5, SURFACE_Y + 1, CENTER_Z - 8, ("OBSIDIAN", "GATE", "Season Spawn", "Paste Center"), 8)
    add_sign(CENTER_X - 6, SURFACE_Y + 1, CENTER_Z + 9, ("No blast", "block damage", "WorldEdit", "schematic"), 0)


def write_nbt_string(out: bytearray, value: str) -> None:
    encoded = value.encode("utf-8")
    out.extend(struct.pack(">h", len(encoded)))
    out.extend(encoded)


def write_named(out: bytearray, tag_type: int, name: str) -> None:
    out.append(tag_type)
    write_nbt_string(out, name)


def write_tag_payload(out: bytearray, tag_type: int, value: object) -> None:
    if tag_type == TAG_BYTE:
        out.extend(struct.pack(">b", int(value)))
    elif tag_type == TAG_SHORT:
        out.extend(struct.pack(">h", int(value)))
    elif tag_type == TAG_INT:
        out.extend(struct.pack(">i", int(value)))
    elif tag_type == TAG_STRING:
        write_nbt_string(out, str(value))
    elif tag_type == TAG_BYTE_ARRAY:
        raw = bytes(value)
        out.extend(struct.pack(">i", len(raw)))
        out.extend(raw)
    else:
        raise ValueError(f"Unsupported tag payload type: {tag_type}")


def write_tag(out: bytearray, tag_type: int, name: str, value: object) -> None:
    write_named(out, tag_type, name)
    write_tag_payload(out, tag_type, value)


def write_compound_payload(out: bytearray, entries: list[tuple[int, str, object]]) -> None:
    for tag_type, name, value in entries:
        write_tag(out, tag_type, name, value)
    out.append(TAG_END)


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
    write_named(out, TAG_COMPOUND, "Schematic")
    write_tag(out, TAG_SHORT, "Width", W)
    write_tag(out, TAG_SHORT, "Height", H)
    write_tag(out, TAG_SHORT, "Length", L)
    write_tag(out, TAG_STRING, "Materials", "Alpha")
    write_tag(out, TAG_BYTE_ARRAY, "Blocks", low_blocks)
    write_tag(out, TAG_BYTE_ARRAY, "Data", data)
    if uses_add_blocks:
        write_tag(out, TAG_BYTE_ARRAY, "AddBlocks", add_blocks)

    write_named(out, TAG_LIST, "Entities")
    out.append(TAG_COMPOUND)
    out.extend(struct.pack(">i", 0))

    write_named(out, TAG_LIST, "TileEntities")
    out.append(TAG_COMPOUND)
    out.extend(struct.pack(">i", len(tile_entities)))
    for tile_entity in tile_entities:
        write_compound_payload(out, tile_entity)

    write_tag(out, TAG_INT, "WEOriginX", 0)
    write_tag(out, TAG_INT, "WEOriginY", 64)
    write_tag(out, TAG_INT, "WEOriginZ", 0)
    write_tag(out, TAG_INT, "WEOffsetX", -CENTER_X)
    write_tag(out, TAG_INT, "WEOffsetY", -SURFACE_Y)
    write_tag(out, TAG_INT, "WEOffsetZ", -CENTER_Z)
    out.append(TAG_END)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(gzip.compress(bytes(out), compresslevel=9))
    print(OUTPUT)
    print(f"dimensions={W}x{H}x{L}")
    print(f"offset={-CENTER_X},{-SURFACE_Y},{-CENTER_Z}")
    print(f"tile_entities={len(tile_entities)}")
    print(f"size={OUTPUT.stat().st_size}")
    print(f"add_blocks={uses_add_blocks}")


def main() -> None:
    build_scene()
    write_schematic()


if __name__ == "__main__":
    main()
