#!/usr/bin/env python3
"""Sonda corta de wss://host/db: handshake, auth, listen y child_changed. Siempre corta."""
from __future__ import annotations

import json
import os
import socket
import ssl
import struct
import sys
import time
import urllib.error
import urllib.request

TOKEN = os.environ.get("OGT_TOKEN", "dev.dev-user-ana.USER")
HOST = os.environ.get("OGT_HOST", "onlygoodthings.lat")
PATH = "/db"
DEADLINE_S = 12


def recvall(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("socket cerrado")
        buf += chunk
    return buf


def send_text(sock: socket.socket, text: str) -> None:
    payload = text.encode()
    mask = os.urandom(4)
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    header = bytearray([0x81])
    n = len(payload)
    if n < 126:
        header.append(0x80 | n)
    elif n < 65536:
        header.append(0x80 | 126)
        header.extend(struct.pack(">H", n))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack(">Q", n))
    sock.sendall(header + mask + masked)


def recv_frame(sock: socket.socket) -> str | None:
    hdr = recvall(sock, 2)
    opcode = hdr[0] & 0x0F
    masked = bool(hdr[1] & 0x80)
    n = hdr[1] & 0x7F
    if n == 126:
        n = struct.unpack(">H", recvall(sock, 2))[0]
    elif n == 127:
        n = struct.unpack(">Q", recvall(sock, 8))[0]
    mask = recvall(sock, 4) if masked else b""
    data = recvall(sock, n)
    if mask:
        data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    if opcode == 0x8:
        return None
    if opcode == 0x9:
        sock.sendall(b"\x8A" + bytes([0x80 | len(data)]) + os.urandom(4))
        return recv_frame(sock)
    if opcode == 0xA:
        return recv_frame(sock)
    return data.decode("utf-8", "replace")


def handshake() -> socket.socket:
    key = __import__("base64").b64encode(os.urandom(16)).decode()
    raw = socket.create_connection((HOST, 443), timeout=8)
    ctx = ssl.create_default_context()
    sock = ctx.wrap_socket(raw, server_hostname=HOST)
    sock.settimeout(8)
    req = (
        f"GET {PATH} HTTP/1.1\r\n"
        f"Host: {HOST}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    ).encode()
    sock.sendall(req)
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("sin handshake")
        buf += chunk
    status = buf.split(b"\r\n", 1)[0].decode("latin1")
    if "101" not in status:
        raise ConnectionError(f"handshake falló: {status}")
    return sock


def rest_post(path: str, body: dict) -> dict:
    req = urllib.request.Request(
        f"https://{HOST}{path}",
        data=json.dumps(body).encode(),
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=8) as res:
        return json.loads(res.read().decode())


def main() -> int:
    started = time.monotonic()
    sock = handshake()
    print("ok handshake 101")
    send_text(sock, json.dumps({"t": "auth", "token": TOKEN}))
    authed = False
    listen_ok = False
    live = False
    while time.monotonic() - started < DEADLINE_S:
        try:
            raw = recv_frame(sock)
        except (TimeoutError, socket.timeout):
            break
        if raw is None:
            break
        msg = json.loads(raw)
        kind = msg.get("t")
        print("in", kind, msg.get("id"), msg.get("key") or "")
        if kind == "ack":
            authed = True
            send_text(sock, json.dumps({"t": "listen", "id": 1, "path": "ogt/social/posts"}))
        elif kind == "listen_ack":
            listen_ok = True
            feed = rest_post("/api/v1/social/feed", {"pageSize": 1, "mode": "HOME"})
            posts = feed.get("data") or []
            if not posts:
                print("fail feed vacío")
                return 1
            post_id = posts[0]["id"]
            impact = rest_post("/api/v1/social/impact", {"postId": post_id})
            print("impact", impact.get("success"), (impact.get("data") or {}).get("impactCount"))
        elif kind == "child_changed" and msg.get("key"):
            live = True
            break
        elif kind == "value" and listen_ok:
            # snapshot inicial; seguimos esperando child_changed
            continue
        elif kind == "error":
            print("fail", msg)
            return 1
    try:
        sock.sendall(b"\x88\x80" + os.urandom(4))
        sock.close()
    except OSError:
        pass
    if authed and listen_ok and live:
        print("ok auth listen child_changed")
        return 0
    print(f"fail authed={authed} listen={listen_ok} live={live}")
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as err:
        print("fail", err)
        sys.exit(1)
