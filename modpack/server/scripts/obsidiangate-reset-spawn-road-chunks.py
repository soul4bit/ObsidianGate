#!/usr/bin/env python3
import argparse
import datetime as dt
import os
import sys
import tarfile
from collections import defaultdict
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser(
        description=(
            "Remove overworld chunk entries inside ObsidianGate road regions so "
            "Minecraft can regenerate the original terrain from the world seed."
        )
    )
    parser.add_argument("--server-root", default=os.environ.get("SERVER_ROOT", "/home/minecraft/mc-rpg"))
    parser.add_argument("--world-name", default=os.environ.get("WORLD_NAME"))
    parser.add_argument("--properties-file", default=os.environ.get("PROPERTIES_FILE"))
    parser.add_argument("--regions-file", default=os.environ.get("REGIONS_FILE"))
    parser.add_argument("--backup-root", default=os.environ.get("BACKUP_ROOT"))
    parser.add_argument("--region-prefix", default="road_")
    parser.add_argument(
        "--confirm-delete-chunks",
        action="store_true",
        help="Actually modify .mca files. Without this flag the command only prints a dry run.",
    )
    return parser.parse_args()


def fail(message):
    print("ERROR: " + message, file=sys.stderr)
    raise SystemExit(1)


def read_properties(path):
    values = {}
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def safe_world_name(name):
    return bool(name) and "/" not in name and "\\" not in name and name not in {".", ".."}


def active_world_name(server_root, explicit_world_name, explicit_properties_file):
    if explicit_world_name:
        if not safe_world_name(explicit_world_name):
            fail("Unsafe WORLD_NAME: " + explicit_world_name)
        return explicit_world_name

    properties_file = Path(explicit_properties_file) if explicit_properties_file else server_root / "server.properties"
    if not properties_file.is_file():
        return "world"

    props = read_properties(properties_file)
    world_name = props.get("level-name", "world")
    if not safe_world_name(world_name):
        fail("Unsafe level-name in " + str(properties_file) + ": " + world_name)
    return world_name


def road_regions(properties, prefix):
    names = set()
    marker = ".ownerId"
    for key in properties:
        if key.startswith("region.") and key.endswith(marker):
            names.add(key[len("region.") : -len(marker)])

    regions = []
    for name in sorted(names):
        if not name.startswith(prefix):
            continue
        base = "region." + name + "."
        if properties.get(base + "dimension", "0") != "0":
            continue
        try:
            min_x = int(properties[base + "minX"])
            max_x = int(properties[base + "maxX"])
            min_z = int(properties[base + "minZ"])
            max_z = int(properties[base + "maxZ"])
        except (KeyError, ValueError) as exc:
            fail("Invalid road region " + name + ": " + str(exc))
        regions.append((name, min(min_x, max_x), max(min_x, max_x), min(min_z, max_z), max(min_z, max_z)))
    return regions


def chunks_for_regions(regions):
    chunks = set()
    for name, min_x, max_x, min_z, max_z in regions:
        min_cx = min_x // 16
        max_cx = max_x // 16
        min_cz = min_z // 16
        max_cz = max_z // 16
        for chunk_x in range(min_cx, max_cx + 1):
            for chunk_z in range(min_cz, max_cz + 1):
                chunks.add((chunk_x, chunk_z, name))
    return chunks


def group_by_region_file(chunks, region_dir):
    grouped = defaultdict(list)
    for chunk_x, chunk_z, region_name in chunks:
        region_x = chunk_x // 32
        region_z = chunk_z // 32
        local_x = chunk_x % 32
        local_z = chunk_z % 32
        region_file = region_dir / ("r.%d.%d.mca" % (region_x, region_z))
        grouped[region_file].append((local_x, local_z, chunk_x, chunk_z, region_name))
    return grouped


def backup_region_files(region_files, world_dir, backup_root):
    timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_root.mkdir(parents=True, exist_ok=True)
    archive = backup_root / ("road-chunks-before-reset-" + timestamp + ".tar.gz")
    with tarfile.open(archive, "w:gz") as tar:
        for region_file in sorted(region_files):
            tar.add(region_file, arcname=str(region_file.relative_to(world_dir)))
    return archive


def delete_chunk_entries(grouped):
    touched_files = []
    touched_chunks = 0
    missing_files = 0
    for region_file, entries in sorted(grouped.items()):
        if not region_file.is_file():
            missing_files += 1
            continue
        changed = False
        with region_file.open("r+b") as handle:
            for local_x, local_z, _chunk_x, _chunk_z, _region_name in entries:
                entry_offset = 4 * (local_x + local_z * 32)
                handle.seek(entry_offset)
                location = handle.read(4)
                if len(location) != 4 or location == b"\x00\x00\x00\x00":
                    continue
                handle.seek(entry_offset)
                handle.write(b"\x00\x00\x00\x00")
                handle.seek(4096 + entry_offset)
                handle.write(b"\x00\x00\x00\x00")
                touched_chunks += 1
                changed = True
        if changed:
            touched_files.append(region_file)
    return touched_files, touched_chunks, missing_files


def main():
    args = parse_args()
    server_root = Path(args.server_root).resolve()
    world_name = active_world_name(server_root, args.world_name, args.properties_file)
    world_dir = server_root / world_name
    region_dir = world_dir / "region"
    regions_file = Path(args.regions_file).resolve() if args.regions_file else server_root / "obsidiangate" / "regions.properties"
    backup_root = Path(args.backup_root).resolve() if args.backup_root else server_root / "backups" / "road-chunk-resets"

    if not world_dir.is_dir():
        fail("World directory not found: " + str(world_dir))
    if not region_dir.is_dir():
        fail("Overworld region directory not found: " + str(region_dir))
    if not regions_file.is_file():
        fail("Regions file not found: " + str(regions_file))

    properties = read_properties(regions_file)
    regions = road_regions(properties, args.region_prefix)
    if not regions:
        fail("No regions with prefix '" + args.region_prefix + "' in " + str(regions_file))

    chunks = chunks_for_regions(regions)
    grouped = group_by_region_file(chunks, region_dir)
    existing_files = [path for path in grouped if path.is_file()]

    print("World: " + str(world_dir))
    print("Regions file: " + str(regions_file))
    print("Road regions: " + ", ".join(name for name, *_rest in regions))
    print("Selected chunks: " + str(len({(chunk_x, chunk_z) for chunk_x, chunk_z, _name in chunks})))
    print("Region files: " + str(len(existing_files)) + " existing, " + str(len(grouped) - len(existing_files)) + " missing")

    if not args.confirm_delete_chunks:
        print("Dry run only. Re-run with --confirm-delete-chunks while Minecraft is stopped to modify the world.")
        return

    if not existing_files:
        fail("No existing .mca files match selected road chunks.")

    archive = backup_region_files(existing_files, world_dir, backup_root)
    touched_files, touched_chunks, missing_files = delete_chunk_entries(grouped)
    print("Backed up region files to: " + str(archive))
    print("Deleted chunk entries: " + str(touched_chunks))
    print("Touched region files: " + str(len(touched_files)))
    if missing_files:
        print("Skipped missing region files: " + str(missing_files))
    print("Start Minecraft and build roads after terrain regenerates.")


if __name__ == "__main__":
    main()
