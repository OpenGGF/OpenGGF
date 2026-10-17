#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R15 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: the same scene description as render_cost.py; independent comparison oracle only.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Independent S3K sprite-renderer reference (kos46 research R15, 2026-10-17). Comparison oracle only.

Written from the mapping format and the renderer's documented rules, not by executing ROM code: display lists in
priority order 0..7, the camera cull (signed X window, unsigned wrapped Y window), static mappings (bit 5: one piece
at the mapping pointer), frame offset tables (signed word offsets, a piece count word, 6-byte pieces: y byte, size
byte, tile word, x word), the four flip writers (x-flip: tile ^ $800, x = -x - width(size); y-flip: tile ^ $1000,
y = -y - height(size); both: ^ $1800), width = ((size >> 2) + 1) * 8 and height = ((size & 3) + 1) * 8, the
x & $1FF == 0 -> 1 fix-up of the unclipped writers, the 80-entry SAT budget (a writer stops when the budget turns
negative), multi-draw (bit 6) objects with per-piece clipping (y outside 97..351 skips, x outside 97..447 rewinds
after y/size/tile were stored), the final y = 0 stores, Sprites_drawn = $50 - entries left, and the Spritemask scan.
SAT link bytes are never written. Returns the SAT bytes, Sprites_drawn, on-screen flags and the drained lists.
"""


def s8(v):
    v &= 0xFF
    return v - 0x100 if v & 0x80 else v


def s16(v):
    v &= 0xFFFF
    return v - 0x10000 if v & 0x8000 else v


def w16(rom, a):
    return (rom[a] << 8) | rom[a + 1]


WIDTH = lambda size: ((size >> 2) + 1) * 8   # noqa: E731
HEIGHT = lambda size: ((size & 3) + 1) * 8   # noqa: E731


class Ref:
    def __init__(self, rom, sc, sat_size=0x280):
        self.rom, self.sc = rom, sc
        self.sat = bytearray([sc.sat_fill]) * sat_size
        self.slot = 0                  # next SAT byte offset (a6)
        self.left = 0x4F               # d7
        self.on = {}

    def put_w(self, off, v):
        self.sat[off] = (v >> 8) & 0xFF
        self.sat[off + 1] = v & 0xFF

    def pieces(self, mappings, frame, static):
        if static:
            return mappings, 1
        a1 = (mappings + s16(w16(self.rom, mappings + 2 * frame))) & 0xFFFFFF
        return a1 + 2, w16(self.rom, a1)

    def piece(self, a):
        r = self.rom
        size = r[a + 1]
        if size > 0x0F:
            raise ValueError('size byte %02X outside the flip tables' % size)
        return s8(r[a]), size, w16(r, a + 2), w16(r, a + 4)

    def unclipped(self, a1, n, d0, d1, art, flags):
        xf, yf = flags & 1, flags & 2
        for k in range(n):
            yb, size, tile, xw = self.piece(a1 + 6 * k)
            y = (-yb - HEIGHT(size) if yf else yb) + d1
            x = (-xw - WIDTH(size) if xf else s16(xw)) + d0
            t = tile + art
            t ^= (0x800 if xf else 0) | (0x1000 if yf else 0)
            x &= 0x1FF
            x = x or 1
            self.put_w(self.slot, y & 0xFFFF)
            self.sat[self.slot + 2] = size
            self.put_w(self.slot + 4, t & 0xFFFF)
            self.put_w(self.slot + 6, x)
            self.slot += 8
            self.left -= 1
            if self.left < 0:
                return

    def clipped(self, a1, n, d0, d1, art, flags):
        xf, yf = flags & 1, flags & 2
        for k in range(n):
            yb, size, tile, xw = self.piece(a1 + 6 * k)
            if yf and not xf:
                self.sat[self.slot + 2] = size        # y-flip writer stores the size before its y test
            y = ((-yb - HEIGHT(size) if yf else yb) + d1) & 0xFFFF
            if y <= 96 or y >= 352:
                continue
            self.put_w(self.slot, y)
            self.sat[self.slot + 2] = size
            t = (tile + art) ^ ((0x800 if xf else 0) | (0x1000 if yf else 0))
            self.put_w(self.slot + 4, t & 0xFFFF)
            x = ((-xw - WIDTH(size) if xf else xw) + d0) & 0xFFFF
            if x <= 96 or x >= 448:
                continue                                # entry rewound; y/size/tile stay in memory
            self.put_w(self.slot + 6, x)
            self.slot += 8
            self.left -= 1
            if self.left < 0:
                return

    def cull_camera(self, sp):
        sc = self.sc
        d0 = s16(sp.x - sc.camera_x)
        if s16(d0 + sp.width) < 0 or s16(d0 - sp.width) >= 320:
            return None
        d1 = (sp.y - sc.camera_y + sp.height) & sc.y_wrap & 0xFFFF
        if d1 >= (2 * sp.height + 224) & 0xFFFF:
            return None
        return (d0 + 128) & 0xFFFF, (d1 + 128 - sp.height) & 0xFFFF

    def run(self):
        sc = self.sc
        if sc.competition or sc.level_started:
            raise ValueError('not modelled by the reference')
        for lvl, sprites in enumerate(sc.levels):
            for sp in sprites:
                flags = sp.render_flags & 0x7F
                self.on[sp.addr] = False
                if flags & 0x40:
                    self.multi(sp, flags)
                    continue
                if flags & 4:
                    pos = self.cull_camera(sp)
                    if pos is None:
                        continue
                else:
                    pos = (sp.x & 0xFFFF, sp.y & 0xFFFF)
                self.on[sp.addr] = True
                if self.left < 0:
                    continue
                a1, n = self.pieces(sp.mappings, sp.frame, flags & 0x20)
                if n:
                    self.unclipped(a1, n, pos[0], pos[1], sp.art_tile, flags)
            if lvl == 0 and sc.current_zone == 9:
                raise ValueError('LRZ hook not modelled')
        if self.left >= 0:
            for _ in range(self.left + 1):
                self.put_w(self.slot, 0)
                self.slot += 8
        drawn = (0x4F - self.left) & 0xFF
        self.spritemask()
        return bytes(self.sat), drawn, dict(self.on), (0,) * 8

    def multi(self, sp, flags):
        sc = self.sc
        if flags & 4:
            pos = self.cull_camera(sp)
            if pos is None:
                return
        else:
            d0 = s16(sp.x - 128)
            if s16(d0 + sp.width) < 0 or s16(d0 - sp.width) >= 320:
                return
            d1 = s16(sp.y - 128)
            if s16(d1 + sp.height) < 0 or s16(d1 - sp.height) >= 224:
                return
            pos = ((d0 + 128) & 0xFFFF, (d1 + 128) & 0xFFFF)
        self.on[sp.addr] = True
        if self.left < 0:
            return
        if sp.frame:
            a1, n = self.pieces(sp.mappings, sp.frame, False)
            if n:
                self.clipped(a1, n, pos[0], pos[1], sp.art_tile, flags)
            if self.left < 0:
                return
        for cx, cy, cf in sp.children:
            if flags & 4:
                cx = (cx - sc.camera_x + 128) & 0xFFFF
                cy = (cy - sc.camera_y + 128) & sc.y_wrap & 0xFFFF
            a1, n = self.pieces(sp.mappings, cf, False)
            if n:
                self.clipped(a1, n, cx & 0xFFFF, cy & 0xFFFF, sp.art_tile, flags)
            if self.left < 0:
                return

    def spritemask(self):
        if not self.sc.spritemask:
            return
        off, d1 = -4, 0x4E
        while True:
            off += 8
            if w16(self.sat, off) == 0x7C0:
                self.put_w(off + 2, 1)
                self.put_w(off + 10, 0)
                d1 -= 1
                if d1 < 0:
                    return
                continue
            d1 -= 1
            if d1 < 0:
                return
