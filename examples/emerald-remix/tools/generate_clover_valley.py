#!/usr/bin/env python3
"""Generate Clover Valley Zone, Emerald Remix's original Sonic 2 level (2026-10-21, feature/ai-emerald-remix).

Inputs are the authored terrain, ledge, decoration and placement tables below; no ROM or
disassembly is read. Every tile is drawn here from a world-space pixel function, then cut
into 8x8 patterns, 16x16 chunks and 128x128 blocks and deduplicated, so the art, the
collision and the layout always agree.

  python3 tools/generate_clover_valley.py            # rewrite src/main/resources/levels/clover-valley
  python3 tools/generate_clover_valley.py --check    # fail if the committed export differs
  python3 tools/generate_clover_valley.py --preview /abs/out.png   # also write a picture of the level

The output is a canonical ModLevelDefinition v1 export, the same inventory the level editor
writes, so it can be opened with the editor's "Import exported level". Once you edit it in
the editor, keep the saved .ogfproject as the source and replace this directory with the
editor's export; this script is only the starting point.
"""
import argparse
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/levels/clover-valley"

# ---- geometry -------------------------------------------------------------------------
CELL = 16                    # collision cell (chunk) size in pixels
BLOCK_CELLS = 8              # chunks per 128px block side
WIDTH_BLOCKS, HEIGHT_BLOCKS = 40, 6
COLS, ROWS = WIDTH_BLOCKS * BLOCK_CELLS, HEIGHT_BLOCKS * BLOCK_CELLS
WIDTH_PX, HEIGHT_PX = COLS * CELL, ROWS * CELL
START_ROW = 36               # ground surface row at the start (y = 576)

# Terrain, left to right: (kind, amount). "flat" runs n columns at the current row; "up"/"down"
# add n 45-degree slope columns, each changing the surface by one row; "step" moves the
# surface by n rows at once (negative = a wall to jump up).
TERRAIN = [
    ("flat", 30), ("up", 4), ("flat", 14), ("down", 4), ("flat", 8),
    ("down", 3), ("flat", 6), ("up", 3), ("flat", 10),
    ("step", -2), ("flat", 12), ("step", -2), ("flat", 10), ("down", 4),
    ("flat", 36), ("up", 6), ("flat", 4), ("up", 4), ("flat", 12), ("down", 10),
    ("flat", 16), ("down", 2), ("flat", 10), ("up", 2),
    ("step", -3), ("flat", 14), ("down", 3), ("flat", 12),
    ("up", 4), ("flat", 8), ("down", 4), ("flat", 65),
]
# Top-solid floating ledges: (first column, length, surface row).
LEDGES = [(60, 9, 34), (112, 30, 25), (196, 8, 33)]
# Non-solid decorations standing on the surface: (column, kind).
DECOR = [(6, "flower"), (9, "clover"), (17, "flower"), (24, "clover"), (38, "flower"), (44, "clover"),
         (55, "flower"), (75, "clover"), (78, "flower"), (98, "clover"), (120, "flower"), (125, "clover"),
         (131, "flower"), (162, "flower"), (166, "clover"), (184, "flower"), (190, "clover"),
         (216, "flower"), (232, "clover"), (246, "flower"), (262, "clover"), (270, "flower"),
         (284, "clover"), (291, "flower"), (300, "clover"), (306, "flower")]

# ---- palette (Genesis 0BGR words) ------------------------------------------------------
SKY = 0x0EA6
LINE1 = [SKY, 0x0000, 0x0E42, 0x0E84, 0x0EA8, 0x0044, 0x0EEE, 0x0AAA,
         0x0888, 0x0444, 0x0666, 0x0E86, 0x00EE, 0x0088, 0x0EC8, 0x0EEC]   # shared: rings, spikes, HUD greys
LINE2 = [SKY, 0x0000, 0x0024, 0x0046, 0x0268, 0x048A, 0x06AC, 0x0040,
         0x0262, 0x0484, 0x06C6, 0x0AE8, 0x0C8E, 0x0EEE, 0x00CE, 0x0A4E]   # terrain
LINE3 = [SKY, 0x0C62, 0x0E84, 0x0EA6, 0x0EC8, 0x0EEC, 0x0EEE, 0x0ECA,
         0x0A86, 0x0864, 0x06A6, 0x0484, 0x0262, 0x08C8, 0x0AEA, 0x0040]   # background
FG_LINE, BG_LINE = 2, 3

EMPTY, FULL, UP, DOWN, LEDGE = range(5)
SOLID_TILES = [  # (heights, widths, angle) per collision profile index
    ([0] * 16, [0] * 16, 0x00),
    ([16] * 16, [16] * 16, 0x00),
    ([x + 1 for x in range(16)], [y + 1 for y in range(16)], 0xE0),
    ([16 - x for x in range(16)], [-(y + 1) for y in range(15)] + [16], 0x20),
]
PROFILE = {EMPTY: 0, FULL: 1, UP: 2, DOWN: 3, LEDGE: 1}
MODE = {EMPTY: 0, FULL: 3, UP: 3, DOWN: 3, LEDGE: 1}   # 3 = solid on all sides, 1 = top only


def build_cells():
    cells = [[EMPTY] * COLS for _ in range(ROWS)]
    surface = []                         # ground surface row per column
    col, row = 0, START_ROW

    def fill(c, top, kind):
        cells[top][c] = kind
        for r in range(top + 1, ROWS):
            cells[r][c] = FULL

    for kind, amount in TERRAIN:
        if kind == "step":
            row += amount
            continue
        for _ in range(amount):
            if kind == "flat":
                fill(col, row, FULL); surface.append(row)
            elif kind == "up":
                fill(col, row - 1, UP); surface.append(row - 1); row -= 1
            elif kind == "down":
                fill(col, row, DOWN); surface.append(row); row += 1
            col += 1
    if col != COLS:
        raise SystemExit(f"terrain covers {col} columns, expected {COLS}")
    for first, length, r in LEDGES:
        for c in range(first, first + length):
            cells[r][c] = LEDGE
    return cells, surface


def solid_at(cells, x, y):
    kind = cells[y // CELL][x // CELL]
    lx, ly = x % CELL, y % CELL
    if kind in (FULL, LEDGE):
        return True
    if kind == UP:
        return ly >= 15 - lx
    if kind == DOWN:
        return ly >= lx
    return False


def fg_pixel(cells, depth, x, y):
    """Line-2 color index for one foreground pixel; 0 is transparent."""
    kind = cells[y // CELL][x // CELL]
    d = depth[y][x]
    if d < 0:
        return 0
    if kind == LEDGE:
        ly = y % CELL
        c = x // CELL
        left_end = cells[y // CELL][c - 1] != LEDGE if c > 0 else True
        right_end = cells[y // CELL][c + 1] != LEDGE if c + 1 < COLS else True
        lx = x % CELL
        if (left_end and lx < 2 and ly > 9) or (right_end and lx > 13 and ly > 9):
            return 0
        if ly <= 1:
            return 11
        if ly <= 4:
            return 10 if (x + ly) % 5 else 9
        if ly == 5:
            return 8 if x % 3 else 7
        if ly >= 13:
            return 2
        return 5 if ((x // 4 + ly // 4) % 2) else 4
    # Grass: a light top edge, darker toward the soil, then a ragged shadow edge.
    edge = 10 + (1 if (x // 3) % 3 == 0 else 0) - (1 if (x // 5) % 4 == 1 else 0)
    if d == 0:
        return 11
    if d <= 3:
        return 10 if (x + d) % 6 else 11
    if d <= 6:
        return 9
    if d < edge - 1:
        return 8
    if d < edge:
        return 7
    if d == edge:
        return 3
    # Soil: a diamond lattice, period 16 so every underground chunk is identical.
    u, v = (x + y) % 16, (x - y) % 16
    if u == 0 or v == 0:
        return 3
    if u in (1, 2) and v in (1, 2):
        return 6
    return 5 if ((x + y) // 16 + (x - y) // 16) % 2 == 0 else 4


def decor_pixel(kind, lx, ly):
    """A 16x16 decoration whose bottom row stands on the surface."""
    def disc(cx, cy, r):
        return (lx - cx) ** 2 + (ly - cy) ** 2 <= r * r
    if kind == "flower":
        if disc(7.5, 5, 1.6):
            return 14                             # yellow centre
        if disc(7.5, 5, 3.2):
            return 12                             # pink petals
        if disc(7.5, 5, 4.1):
            return 15                             # petal outline
        if lx in (7, 8) and 9 <= ly <= 15:
            return 8                              # stem
        if disc(4.5, 12, 2) or disc(10.5, 11, 2):
            return 9                              # leaves
        return 0
    # clover: three round leaves on a stem
    leaves = ((4.5, 7.5), (10.5, 7.5), (7.5, 3.5))
    if any(disc(cx - 0.6, cy - 0.6, 1.1) for cx, cy in leaves):
        return 11
    if any(disc(cx, cy, 2.6) for cx, cy in leaves):
        return 10
    if any(disc(cx, cy, 3.4) for cx, cy in leaves):
        return 8
    if lx in (7, 8) and 10 <= ly <= 15:
        return 8
    return 0


def bg_pixel(x, y):
    """Line-3 background: banded sky, clouds, far mountains and near hills; period 512."""
    x %= 512
    if y >= 248:
        return 12 if (x + y) % 6 else 15
    hill = 206 + int(14 * _wave(x, 128, 0) + 6 * _wave(x, 64, 21))
    if y >= hill:
        return 13 if y < hill + 2 else (11 if (x // 3 + y // 3) % 7 else 12)
    mountain = 150 + int(30 * abs(_wave(x, 256, 40))) + int(5 * _wave(x, 32, 3))
    if y >= mountain:
        return 8 if y < mountain + 4 or (x + y) % 5 else 9
    for cx, cy, r in ((96, 60, 18), (130, 54, 22), (160, 64, 16), (352, 92, 14), (380, 86, 19), (408, 94, 12)):
        dx, dy = x - cx, (y - cy) * 2
        if dx * dx + dy * dy <= r * r:
            return 7 if dy > r // 2 else 6
    bands = (1, 2, 3, 4, 5)
    band = min(len(bands) - 1, y // 32)
    if y % 32 >= 28 and (x + y) % 2 == 0 and band + 1 < len(bands):
        return bands[band + 1]
    return bands[band]


def _wave(x, period, phase):
    t = ((x + phase) % period) / period
    return 1 - 4 * abs(t - 0.5)                   # triangle wave in [-1, 1]


def render(cells, surface):
    # depth = distance below the top of the solid run in the pixel column; -1 for air
    depth = [[-1] * WIDTH_PX for _ in range(HEIGHT_PX)]
    for x in range(WIDTH_PX):
        run = -1
        for y in range(HEIGHT_PX):
            if solid_at(cells, x, y):
                run += 1
                depth[y][x] = run
            else:
                run = -1
    fg = [[fg_pixel(cells, depth, x, y) for x in range(WIDTH_PX)] for y in range(HEIGHT_PX)]
    for x in range(WIDTH_PX):
        blade = (1, 3, 2, 0, 2, 0, 1, 0)[(x * 3 + x // 8) % 8]      # tuft height in pixels
        for y in range(1, HEIGHT_PX):
            if depth[y][x] == 0 and cells[y // CELL][x // CELL] != LEDGE:
                for up in range(1, blade + 1):
                    if y - up >= 0 and depth[y - up][x] < 0 and (y - up) // CELL == (y - 1) // CELL:
                        fg[y - up][x] = 11 if up == blade else 10
    decor_cells = {}
    for col, kind in DECOR:
        r = surface[col] - 1
        if cells[r][col] != EMPTY:
            raise SystemExit(f"decoration at column {col} is not above open ground")
        decor_cells[(col, r)] = kind
        for ly in range(CELL):
            for lx in range(CELL):
                color = decor_pixel(kind, lx, ly)
                if color:
                    fg[r * CELL + ly][col * CELL + lx] = color
    bg = [[bg_pixel(x, y) for x in range(WIDTH_PX)] for y in range(HEIGHT_PX)]
    return fg, bg


def tile_bytes(image, tx, ty):
    out = bytearray()
    for y in range(8):
        row = image[ty * 8 + y]
        for x in range(0, 8, 2):
            out.append((row[tx * 8 + x] << 4) | row[tx * 8 + x + 1])
    return bytes(out)


def build_assets():
    cells, surface = build_cells()
    fg, bg = render(cells, surface)
    patterns, pattern_ids = [bytes(32)], {bytes(32): 0}
    chunks, chunk_ids = [(0, 0, 0, 0, 0)], {(0, 0, 0, 0, 0): 0}   # (4 pattern words, collision profile)
    blocks, block_ids = [], {}

    def pattern_word(image, line, tx, ty):
        data = tile_bytes(image, tx, ty)
        if data == bytes(32):
            return 0
        if data not in pattern_ids:
            pattern_ids[data] = len(patterns)
            patterns.append(data)
        return (line << 13) | pattern_ids[data]

    def chunk_word(image, line, col, row, kind):
        words = tuple(pattern_word(image, line, col * 2 + dx, row * 2 + dy) for dy in (0, 1) for dx in (0, 1))
        key = words + (PROFILE[kind],)
        if key not in chunk_ids:
            chunk_ids[key] = len(chunks)
            chunks.append(key)
        return chunk_ids[key] | (MODE[kind] << 12) | (MODE[kind] << 14)

    def layer(image, line, collision):
        cells_out = []
        for by in range(HEIGHT_BLOCKS):
            for bx in range(WIDTH_BLOCKS):
                words = tuple(chunk_word(image, line, bx * 8 + cx, by * 8 + cy,
                                         cells[by * 8 + cy][bx * 8 + cx] if collision else EMPTY)
                              for cy in range(8) for cx in range(8))
                if words not in block_ids:
                    block_ids[words] = len(blocks)
                    blocks.append(words)
                cells_out.append(block_ids[words])
        return cells_out

    blocks.append((0,) * 64); block_ids[(0,) * 64] = 0       # block 0 stays empty for the editor
    fg_map = layer(fg, FG_LINE, True)
    bg_map = layer(bg, BG_LINE, False)
    if len(blocks) > 256 or len(chunks) > 1024 or len(patterns) > 2048:
        raise SystemExit(f"over budget: {len(blocks)} blocks, {len(chunks)} chunks, {len(patterns)} patterns")

    def words(values):
        return struct.pack(">" + "H" * len(values), *values)

    def records(magic, stride, values):
        return magic + struct.pack(">HHI", 1, stride, len(values)) + b"".join(values)

    def layout(cells_out):
        return b"GMAP" + struct.pack(">HHHHI", 1, WIDTH_BLOCKS, HEIGHT_BLOCKS, 1, len(cells_out)) + bytes(cells_out)

    signed = lambda values: bytes(v & 0xFF for v in values)
    assets = {
        "patterns.bin": records(b"GPTN", 32, patterns),
        "chunks.bin": records(b"GCHK", 8, [words(list(c[:4])) for c in chunks]),
        "blocks.bin": b"GBLK" + struct.pack(">HBBI", 1, 8, 0, len(blocks)) + b"".join(words(list(b)) for b in blocks),
        "fg-map.bin": layout(fg_map),
        "bg-map.bin": layout(bg_map),
        "solid-heights.bin": records(b"GSHG", 16, [signed(h) for h, _, _ in SOLID_TILES]),
        "solid-widths.bin": records(b"GSWD", 16, [signed(w) for _, w, _ in SOLID_TILES]),
        "solid-angles.bin": records(b"GSAN", 1, [bytes([a]) for _, _, a in SOLID_TILES]),
        "collision-primary.bin": b"GCOL" + struct.pack(">HBBI", 1, 0, 2, len(chunks)) + words([c[4] for c in chunks]),
        "collision-secondary.bin": b"GCOL" + struct.pack(">HBBI", 1, 1, 2, len(chunks)) + words([c[4] for c in chunks]),
        "palettes.bin": b"GPAL" + struct.pack(">HHHH", 1, 4, 16, 0) + words([0] * 16 + LINE1 + LINE2 + LINE3),
        "level.json": level_json(surface),
    }
    return assets, fg, bg


# ---- placements ------------------------------------------------------------------------
SIGNPOST, CHECKPOINT, MONITOR, SPRING = 0x0D, 0x79, 0x26, 0x41
MONITOR_RINGS, MONITOR_SHOES, MONITOR_SHIELD = 4, 5, 6
SPRING_UP_YELLOW = 0x02
CAMERA_MAX_X = WIDTH_PX - 320


def level_json(surface):
    ground = lambda col: surface[col] * CELL
    objects = []

    def place(x, y, object_id, subtype, tracked):
        objects.append({"x": x, "y": y, "stockObjectId": object_id, "subtype": subtype,
                        "renderFlags": 0, "respawnTracked": tracked,
                        "rawYWord": (0x8000 if tracked else 0) | y})

    centre = lambda col: col * CELL + 8
    place(centre(64), 34 * CELL - 16, MONITOR, MONITOR_SHOES, True)           # on the valley ledge
    place(centre(86), ground(86) - 16, MONITOR, MONITOR_RINGS, True)
    place(centre(90), ground(90) - 16, MONITOR, MONITOR_SHIELD, True)
    place(centre(114), ground(114) - 8, SPRING, SPRING_UP_YELLOW, False)       # up to the high ledge
    place(centre(150), ground(150) - 24, CHECKPOINT, 1, True)
    place(centre(200), 33 * CELL - 16, MONITOR, MONITOR_RINGS, True)
    signpost_x = CAMERA_MAX_X + 216
    place(signpost_x, ground(signpost_x // CELL) - 32, SIGNPOST, 0, True)
    objects.sort(key=lambda o: o["x"])
    for i, o in enumerate(objects):
        o["placementId"] = i + 1

    rings = []
    def ring_row(first, last, y, step=2):
        for col in range(first, last + 1, step):
            rings.append((centre(col), y))
    ring_row(12, 20, ground(12) - 16)
    ring_row(36, 46, ground(36) - 16)
    for i, col in enumerate(range(61, 68, 1)):
        rings.append((centre(col), 34 * CELL - 40 - (12 if 2 <= i <= 4 else 0)))
    ring_row(116, 140, 25 * CELL - 16)
    ring_row(160, 168, ground(160) - 16)
    for i in range(8):                                                      # down the long slope
        col = 171 + i
        rings.append((centre(col), ground(col) - 8))
    ring_row(212, 222, ground(212) - 16)
    ring_row(260, 280, ground(260) - 16, 4)
    ring_entries = [{"placementId": i, "x": x, "y": y} for i, (x, y) in enumerate(rings)]

    level = {
        "formatVersion": 1,
        "zoneName": "CLOVER VALLEY",
        "zoneIndex": 64,
        "levelIndex": 1024,
        "blockGridSide": 8,
        "width": WIDTH_BLOCKS,
        "height": HEIGHT_BLOCKS,
        "bounds": {"minX": 0, "maxX": CAMERA_MAX_X, "minY": 0, "maxY": HEIGHT_PX - 224},
        "start": {"x": 96, "y": ground(6) - 32},
        "music": {"stockId": 0x91},
        "assets": {
            "patterns": "patterns.bin", "chunks": "chunks.bin", "blocks": "blocks.bin",
            "foregroundMap": "fg-map.bin", "backgroundMap": "bg-map.bin",
            "solidHeights": "solid-heights.bin", "solidWidths": "solid-widths.bin",
            "solidAngles": "solid-angles.bin", "collisionPrimary": "collision-primary.bin",
            "collisionSecondary": "collision-secondary.bin", "palettes": "palettes.bin",
        },
        "objects": objects,
        "rings": ring_entries,
    }
    return (json.dumps(level, indent=2) + "\n").encode()


# ---- preview ---------------------------------------------------------------------------
def rgb(word):
    return tuple(((word >> shift) & 0xE) * 255 // 14 for shift in (0, 4, 8))


def write_png(path, fg, bg, scale_down=2):
    w, h = WIDTH_PX // scale_down, HEIGHT_PX // scale_down
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            c = fg[y * scale_down][x * scale_down]
            raw.extend(rgb(LINE2[c]) if c else rgb(LINE3[bg[y * scale_down][x * scale_down]]))
    chunk = lambda tag, data: struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                          + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="compare with the committed files without writing")
    parser.add_argument("--preview", help="also write a half-size PNG of the level to this path")
    args = parser.parse_args()
    assets, fg, bg = build_assets()
    if args.preview:
        write_png(args.preview, fg, bg)
    for name, payload in assets.items():
        target = ROOT / name
        if args.check:
            if not target.exists() or target.read_bytes() != payload:
                raise SystemExit(f"Generated asset differs from the committed file: {target}")
        else:
            ROOT.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
    print(("Verified" if args.check else "Generated") + f" {len(assets)} Clover Valley files in {ROOT}")


if __name__ == "__main__":
    main()
