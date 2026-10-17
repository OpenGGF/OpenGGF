#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R16 (2026-10-17; frozen at R16 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: band lists and fixed-point state; independent comparison oracle only.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Independent references for S3K background deformation (kos46 research R16, 2026-10-17). Comparison oracle only.

ApplyDeformation, stated as a band algorithm rather than the ROM's register flow: the band list holds heights (bit 15:
the band has one scroll word per line, otherwise one word for the whole band). Bands lying wholly above the BG
camera Y are skipped (their scroll words with them). Then lines are emitted top to bottom, d1 + 1 lines in total: the
first band contributes the lines left below the camera, later bands their full height, the last band whatever remains.
Each line is the long (-camera_x << 16) | (-scroll & $FFFF). Heights of 0 are refused (the ROM's dbf would wrap).
AIZ1_Deform, stated as the fixed-point arithmetic it performs (16.16 values, 32-bit wraparound): base =
((camera_x - $1300) << 16) asr 5; bands at HScroll_table+$30.. use 14, 16, 18 and 20 x base; the six cloud bands use
(base asr 1) + k * base + drift with the drift long at +$3C (then advanced by $2000); thirteen bands at +$16.. use
base + k * (base asr 3); Camera_Y_pos_BG_copy = Camera_Y_pos_copy asr 1.
"""


def s16(v):
    v &= 0xFFFF
    return v - 0x10000 if v & 0x8000 else v


def s32(v):
    v &= 0xFFFFFFFF
    return v - 0x100000000 if v & 0x80000000 else v


def apply_deformation(table_word, scroll_word, camera_bg_y, camera_x, d1=0xDF):
    """table_word(i) / scroll_word(i): i-th word of the band list / scroll array. Returns the emitted longs."""
    hi = (-camera_x & 0xFFFF) << 16
    y = s16(camera_bg_y)
    ti, si = 0, 0
    while True:                                       # skip bands above the camera
        h = table_word(ti); ti += 1
        per_line, h = bool(h & 0x8000), h & 0x7FFF
        if h == 0:
            raise ValueError('zero-height band')
        if y - h < 0:
            break
        y -= h
        si += h if per_line else 1
    left_in_band = h - y                              # lines of this band from the camera down (y < 0: more than h)
    if per_line:
        si += y                                       # skip the lines above the camera (backwards when y < 0)
    remaining = d1 + 1
    out = []
    first = True
    while remaining > 0:
        if not first:
            h = table_word(ti); ti += 1
            per_line, h = bool(h & 0x8000), h & 0x7FFF
            if h == 0:
                raise ValueError('zero-height band')
            left_in_band = h
        n = min(left_in_band, remaining)
        if per_line:
            for _ in range(n):
                out.append(hi | (-scroll_word(si) & 0xFFFF)); si += 1
        else:
            v = -scroll_word(si) & 0xFFFF; si += 1
            out.extend([hi | v] * n)
        remaining -= n
        first = False
    return out


def aiz1_deform(camera_x, camera_y, table):
    """table: bytearray HScroll_table[$00..$7F]; returns (camera_bg_y, new table)."""
    t = bytearray(table)
    put = lambda off, v: t.__setitem__(slice(off, off + 2), ((v >> 16) & 0xFFFF).to_bytes(2, 'big'))  # noqa: E731
    base = s32(((camera_x - 0x1300) & 0xFFFF) << 16) >> 5
    for off, mult in ((0x30, 14), (0x04, 16), (0x32, 16), (0x3A, 16), (0x34, 18), (0x38, 18), (0x36, 20)):
        put(off, mult * base)
    put(0x14, base)
    drift = int.from_bytes(t[0x3C:0x40], 'big')
    t[0x3C:0x40] = ((drift + 0x2000) & 0xFFFFFFFF).to_bytes(4, 'big')
    acc = s32(base) >> 1
    for k in range(6):
        acc += drift
        put(0x12 - 2 * k, acc)
        acc += base
    step = s32(base) >> 3
    acc = base
    for k in range(13):
        acc += step
        put(0x16 + 2 * k, acc)
    return s16(camera_y) >> 1 & 0xFFFF, bytes(t)


def water_is_dry(water_level, camera_y):
    return s16(water_level - camera_y) >= 0xE0


def apply_fg_bg(table_word, scroll_word, fg, delta_word, bg_y, d1):
    """Lines below the water: same band walk from bg_y; line i is (fg[i] << 16) | (-scroll + delta(i))."""
    plain = apply_deformation(table_word, scroll_word, bg_y, 0, d1)
    return [((fg[i] & 0xFFFF) << 16) | ((plain[i] + delta_word(i)) & 0xFFFF) for i in range(d1 + 1)]


def aiz1_water(rom_word, table_word, scroll_word, camera_x, camera_y, camera_bg_y, water_level, frame,
               fg_delta, bg_delta):
    """AIZ1_ApplyDeformWater as a whole: dry -> one ApplyDeformation; else top lines, FG array, FG+BG lines."""
    above = s16(water_level - camera_y)
    if above >= 0xE0:
        return apply_deformation(table_word, scroll_word, camera_bg_y, camera_x)
    if above <= 0:
        raise ValueError('water line at/above the screen top')
    top = apply_deformation(table_word, scroll_word, camera_bg_y, camera_x, above - 1)
    below = 0xE0 - above
    fg_base = fg_delta + ((frame + 2 * water_level) & 0x7E)
    fg = [(rom_word(fg_base + 2 * i) - camera_x) & 0xFFFF for i in range(below)]
    bg_y = (water_level - camera_y + camera_bg_y) & 0xFFFF
    bg_base = bg_delta + (((s16(frame) >> 1) + 2 * bg_y) & 0x7E)
    return top + apply_fg_bg(table_word, scroll_word, fg, lambda i: rom_word(bg_base + 2 * i), bg_y, below - 1)
