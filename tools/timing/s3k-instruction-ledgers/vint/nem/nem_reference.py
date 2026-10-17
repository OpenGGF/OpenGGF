#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R12 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: Nemesis-compressed bytes; independent format decoder and encoder used only as a test oracle.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Independent Nemesis reference decoder and test encoder (kos46 research, 2026-10-17).

Written from the data format, not from the ROM routine's register flow: header word (bit 15 XOR mode, low 15 bits
pattern count); code-table records (byte >= $80 selects palette index (low nibble), $FF ends; otherwise byte
= 0rrrllll gives repeat count r+1 and code length l, followed by the code byte); bitstream read MSB first, where the
6-bit prefix 111111 escapes to 7 inline bits (3-bit repeat, 4-bit palette index) and any other prefix must match a
table code. Pixels fill 8-nybble rows; XOR mode outputs the running XOR of rows. Runs continue across rows and
patterns. Used only as a comparison oracle for the micro-executed ROM decoder.
"""


class FormatError(ValueError):
    pass


def parse(data, offset):
    hdr = (data[offset] << 8) | data[offset + 1]
    pos = offset + 2
    codes, pal = {}, None
    while True:
        b = data[pos]; pos += 1
        if b == 0xFF:
            break
        if b & 0x80:
            pal = b & 0x0F
            continue
        length = b & 0x0F
        if not 1 <= length <= 8 or pal is None:
            raise FormatError('code record %02X at %X' % (b, pos - 1))
        codes[(length, data[pos])] = (pal, ((b >> 4) & 7) + 1)
        pos += 1
    return bool(hdr & 0x8000), hdr & 0x7FFF, codes, pos


def decode(data, offset, patterns=None):
    """Returns the list of 32-bit rows written for `patterns` patterns (default: the header count)."""
    xor, count, codes, pos = parse(data, offset)
    count = count if patterns is None else patterns
    bitpos = pos * 8

    def peek(n):
        v = 0
        for k in range(n):
            i = bitpos + k
            v = (v << 1) | ((data[i >> 3] >> (7 - (i & 7))) & 1)
        return v

    pixels = []
    need = 64 * count
    while len(pixels) < need:
        if peek(6) == 0x3F:
            bitpos += 6
            v = peek(7); bitpos += 7
            pixels += [v & 0xF] * ((v >> 4) + 1)
            continue
        for n in range(1, 9):
            hit = codes.get((n, peek(n)))
            if hit:
                bitpos += n
                pixels += [hit[0]] * hit[1]
                break
        else:
            raise FormatError('no code matches at bit %d' % bitpos)
    rows, prev = [], 0
    for r in range(8 * count):
        v = 0
        for p in pixels[8 * r: 8 * r + 8]:
            v = (v << 4) | p
        prev = (prev ^ v) if xor else v
        rows.append(prev)
    return rows


def encode(symbols, table, xor=False, pad=8):
    """Test encoder: symbols are (palette, run) pairs; table maps (palette, run) -> bit string, missing pairs are
    emitted inline. Returns the art bytes (header, table, bitstream, `pad` trailing zero bytes for the ROM's
    look-ahead refills)."""
    pixels = sum(r for _, r in symbols)
    if pixels % 64:
        raise ValueError('symbols must fill whole patterns')
    hdr = (pixels // 64) | (0x8000 if xor else 0)
    out = bytearray([hdr >> 8, hdr & 0xFF])
    by_pal = {}
    for (pal, run), bits in table.items():
        by_pal.setdefault(pal, []).append((run, bits))
    for pal, recs in sorted(by_pal.items()):
        out.append(0x80 | pal)
        for run, bits in recs:
            out += bytes([((run - 1) << 4) | len(bits), int(bits, 2)])
    out.append(0xFF)
    stream = ''
    for pal, run in symbols:
        bits = table.get((pal, run))
        stream += bits if bits else '111111' + format(((run - 1) << 4) | pal, '07b')
    stream += '0' * (-len(stream) % 8)
    out += bytes(int(stream[i:i + 8], 2) for i in range(0, len(stream), 8))
    return bytes(out) + bytes(pad)


def pixels_to_rows(symbols, xor=False):
    px = [p for pal, run in symbols for p in [pal] * run]
    rows, prev = [], 0
    for r in range(len(px) // 8):
        v = 0
        for p in px[8 * r: 8 * r + 8]:
            v = (v << 4) | p
        prev = (prev ^ v) if xor else v
        rows.append(prev)
    return rows
