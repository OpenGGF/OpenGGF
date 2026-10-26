#!/usr/bin/env python3
"""Lossless native PPM (P6, 8-bit RGB) -> PNG conversion with decode-back verification.
player-contact-reference-opus-r1, 2026-10-24. No resizing, aspect correction or editing.
Usage: ppm_png.py <in.ppm> <out.png>; prints JSON hashes. Refuses an existing output.
"""
import hashlib, json, re, struct, sys, zlib
from pathlib import Path

def read_ppm(data):
    m = re.match(rb'P6\s+(\d+)\s+(\d+)\s+255\s', data)
    if not m: raise ValueError('Not an 8-bit P6 PPM')
    w, h = int(m[1]), int(m[2]); pixels = data[m.end():]
    if len(pixels) != w * h * 3: raise ValueError('PPM pixel extent')
    return w, h, pixels

def chunk(kind, body):
    return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)

def encode(w, h, pixels):
    raw = b''.join(b'\0' + pixels[y * w * 3:(y + 1) * w * 3] for y in range(h))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) +
            chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def decode(png):
    if png[:8] != b'\x89PNG\r\n\x1a\n': raise ValueError('PNG signature')
    at, idat, ihdr = 8, b'', None
    while at < len(png):
        n = struct.unpack('>I', png[at:at + 4])[0]; kind = png[at + 4:at + 8]; body = png[at + 8:at + 8 + n]
        if zlib.crc32(kind + body) & 0xffffffff != struct.unpack('>I', png[at + 8 + n:at + 12 + n])[0]: raise ValueError('PNG CRC')
        if kind == b'IHDR': ihdr = struct.unpack('>IIBBBBB', body)
        elif kind == b'IDAT': idat += body
        at += 12 + n
    w, h, depth, colour, *_ = ihdr
    if (depth, colour) != (8, 2): raise ValueError('Unexpected PNG format')
    raw = zlib.decompress(idat); stride = w * 3 + 1
    if any(raw[y * stride] != 0 for y in range(h)): raise ValueError('Unexpected PNG filter')
    return w, h, b''.join(raw[y * stride + 1:(y + 1) * stride] for y in range(h))

def main():
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    if dst.exists(): raise SystemExit('Output exists')
    data = src.read_bytes(); w, h, pixels = read_ppm(data)
    png = encode(w, h, pixels)
    if decode(png) != (w, h, pixels): raise SystemExit('Lossless round trip failed')
    dst.write_bytes(png)
    print(json.dumps(dict(ppm=str(src), png=str(dst), width=w, height=h, ppm_sha256=hashlib.sha256(data).hexdigest(),
                          png_sha256=hashlib.sha256(png).hexdigest(), rgb_sha256=hashlib.sha256(pixels).hexdigest(),
                          decoded_rgb_equals_ppm=True), sort_keys=True))

if __name__ == '__main__':
    main()
