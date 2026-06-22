#!/usr/bin/env python3
import gzip
import struct
import sys

MAX_DEPTH = 64
MAX_COLLECTION_LENGTH = 16_777_216
MAX_PLAYERDATA_BYTES = 16 * 1024 * 1024


class NbtError(Exception):
    pass


class Reader:
    def __init__(self, data):
        self.data = data
        self.offset = 0

    def take(self, size):
        if size < 0:
            raise NbtError("negative payload size")
        end = self.offset + size
        if end > len(self.data):
            raise NbtError("unexpected end of NBT data")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u8(self):
        return self.take(1)[0]

    def i32(self):
        return struct.unpack(">i", self.take(4))[0]

    def string(self):
        length = struct.unpack(">H", self.take(2))[0]
        self.take(length)


def require_depth(depth):
    if depth > MAX_DEPTH:
        raise NbtError("NBT nesting is too deep")


def collection_length(reader):
    length = reader.i32()
    if length < 0 or length > MAX_COLLECTION_LENGTH:
        raise NbtError("invalid NBT collection length")
    return length


def skip_payload(reader, tag_type, depth):
    require_depth(depth)
    if tag_type == 0:
        return
    if tag_type == 1:
        reader.take(1)
        return
    if tag_type == 2:
        reader.take(2)
        return
    if tag_type == 3:
        reader.take(4)
        return
    if tag_type == 4:
        reader.take(8)
        return
    if tag_type == 5:
        reader.take(4)
        return
    if tag_type == 6:
        reader.take(8)
        return
    if tag_type == 7:
        reader.take(collection_length(reader))
        return
    if tag_type == 8:
        reader.string()
        return
    if tag_type == 9:
        element_type = reader.u8()
        length = collection_length(reader)
        for _ in range(length):
            skip_payload(reader, element_type, depth + 1)
        return
    if tag_type == 10:
        while True:
            child_type = reader.u8()
            if child_type == 0:
                return
            reader.string()
            skip_payload(reader, child_type, depth + 1)
    if tag_type == 11:
        reader.take(collection_length(reader) * 4)
        return
    if tag_type == 12:
        reader.take(collection_length(reader) * 8)
        return
    raise NbtError("unknown NBT tag type: %s" % tag_type)


def validate(path):
    with gzip.open(path, "rb") as handle:
        data = handle.read(MAX_PLAYERDATA_BYTES + 1)
    if len(data) > MAX_PLAYERDATA_BYTES:
        raise NbtError("playerdata is too large")

    reader = Reader(data)
    root_type = reader.u8()
    if root_type != 10:
        raise NbtError("playerdata root is not a compound tag")
    reader.string()
    skip_payload(reader, root_type, 0)
    if reader.offset != len(reader.data):
        raise NbtError("playerdata contains trailing data")


def main(argv):
    if len(argv) < 2:
        print("Usage: obsidiangate-validate-playerdata.py <file.dat> [...]", file=sys.stderr)
        return 2

    status = 0
    for path in argv[1:]:
        try:
            validate(path)
        except Exception as exception:
            print("%s: %s" % (path, exception), file=sys.stderr)
            status = 1
    return status


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
