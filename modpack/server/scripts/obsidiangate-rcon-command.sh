#!/bin/sh
set -eu

SERVER_ROOT="${SERVER_ROOT:-/home/minecraft/mc-rpg}"
SERVER_PROPERTIES="${SERVER_PROPERTIES:-$SERVER_ROOT/server.properties}"
RCON_HOST="${RCON_HOST:-127.0.0.1}"
RCON_PORT="${RCON_PORT:-}"

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <command>" >&2
    exit 2
fi

if [ ! -f "$SERVER_PROPERTIES" ]; then
    echo "server.properties not found: $SERVER_PROPERTIES" >&2
    exit 2
fi

python3 - "$SERVER_PROPERTIES" "$RCON_HOST" "$RCON_PORT" "$*" <<'PY'
import socket
import struct
import sys

properties_path, host, port_arg, command = sys.argv[1:5]

props = {}
with open(properties_path, "r", encoding="utf-8", errors="replace") as handle:
    for line in handle:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key] = value.replace("\\:", ":").replace("\\=", "=").replace("\\\\", "\\")

if props.get("enable-rcon", "false").lower() != "true":
    raise SystemExit("RCON is disabled in server.properties")

password = props.get("rcon.password", "")
if not password:
    raise SystemExit("rcon.password is empty in server.properties")

port = int(port_arg or props.get("rcon.port", "25575"))
timeout = float(props.get("obsidiangate.rcon.timeout", "5"))

def packet(request_id, packet_type, body):
    encoded = body.encode("utf-8")
    payload = struct.pack("<ii", request_id, packet_type) + encoded + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload

def receive(sock):
    header = sock.recv(4)
    if not header:
        raise RuntimeError("RCON connection closed")
    length = struct.unpack("<i", header)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise RuntimeError("RCON connection closed mid-packet")
        data += chunk
    request_id, packet_type = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", "replace")
    return request_id, packet_type, body

with socket.create_connection((host, port), timeout=timeout) as sock:
    sock.settimeout(timeout)
    sock.sendall(packet(1, 3, password))
    auth_id, _, _ = receive(sock)
    if auth_id == -1:
        raise SystemExit("RCON authentication failed")
    sock.sendall(packet(2, 2, command))
    _, _, response = receive(sock)
    if response:
        print(response)
PY
