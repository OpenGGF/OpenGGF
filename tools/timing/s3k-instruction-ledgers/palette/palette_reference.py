#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R17 (2026-10-17; frozen at R17 qualification).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: the same palette state as palette_cost.py; independent comparison oracle only.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Independent S3K palette fade reference (kos46 research R17, 2026-10-17). Comparison oracle only.

Stated per colour word (0000 BBB0 GGG0 RRR0) rather than as the ROM's register flow:
  dec_color:  0 stays; else lower red by 2 if any red bit, else green by $20, else blue by $200 (whole-word subtract).
  add_color:  equal stays; else +$200 if the whole word stays <= target (unsigned), else +$20 if so, else +2 (so a
              finished component can carry into the next one: the shipped behaviour).
  add_color2: $EEE stays; else raise red by 2 if red < $E, else green by $20, else blue by $200.
  dec_color2: blue (high byte, masked to $E), green and red each step down toward the target component when above it;
              the high byte keeps only the blue bits afterwards.
Range: Palette_fade_info offset byte / count-1 byte. ToBlack and ToWhite also step the water copy unconditionally;
FromBlack, FromWhite and FillBlack only when Water_flag. FromWhite runs every third call (Pal_fade_delay2). FillBlack
fills white for zone/act $0D01 and $1701. Animate_Palette: timer < 0 nothing, 0 -> AnPal_Load (not modelled), else
timer - 1 then FromBlack (FromWhite for $0D01/$1701).
"""
WHITE_ZONES = (0x0D01, 0x1701)


def dec_color(c):
    if c == 0:
        return c
    for mask, step in ((0x00E, 2), (0x0E0, 0x20), (0xE00, 0x200)):
        if c & mask:
            return (c - step) & 0xFFFF
    return c


def add_color(c, t):
    if c == t:
        return c
    for step in (0x200, 0x20):
        if (c + step) & 0xFFFF <= t:
            return (c + step) & 0xFFFF
    return (c + 2) & 0xFFFF


def add_color2(c):
    if c == 0xEEE:
        return c
    for mask, step in ((0x00E, 2), (0x0E0, 0x20), (0xE00, 0x200)):
        if c & mask != mask:
            return (c + step) & 0xFFFF
    return c


def dec_color2(c, t):
    hi, lo, thi, tlo = (c >> 8) & 0xFF, c & 0xFF, (t >> 8) & 0xFF, t & 0xFF
    blue = hi & 0xE
    blue = blue - 2 if blue > (thi & 0xE) else blue
    green, red = lo & 0xE0, lo & 0xE
    green = green - 0x20 if green > (tlo & 0xE0) else green
    red = red - 2 if red > (tlo & 0xE) else red
    return (blue << 8) | green | red


def step(name, s):
    """Returns the next (normal, water, fade_timer, delay2) for routine `name` on state `s` (PalState-like)."""
    n, w = list(s.normal), list(s.water)
    lo, hi = s.fade_offset // 2, s.fade_offset // 2 + s.fade_count + 1
    timer, delay2 = s.fade_timer, s.delay2

    def over(pal, f, other=None):
        for k in range(lo, hi):
            pal[k] = f(pal[k], other[k]) if other is not None else f(pal[k])

    def from_black():
        over(n, add_color, s.target)
        if s.water_flag:
            over(w, add_color, s.target_water)

    def from_white():
        nonlocal delay2
        delay2 -= 1
        if delay2 >= 0:
            return
        delay2 = 2
        over(n, dec_color2, s.target)
        if s.water_flag:
            over(w, dec_color2, s.target_water)

    def fill_white():
        nonlocal delay2
        for k in range(lo, hi):
            n[k] = 0xEEE
        delay2 = 0

    if name == 'Pal_ToBlack':
        over(n, dec_color); over(w, dec_color)
    elif name == 'Pal_ToWhite':
        over(n, add_color2); over(w, add_color2)
    elif name == 'Pal_FromBlack':
        from_black()
    elif name == 'Pal_FromWhite':
        from_white()
    elif name == 'Pal_FillWhite':
        fill_white()
    elif name == 'Pal_FillBlack':
        if s.zone_act in WHITE_ZONES:
            fill_white()
        else:
            for k in range(lo, hi):
                n[k] = 0
                if s.water_flag:
                    w[k] = 0
    elif name == 'Animate_Palette':
        if timer < 0:
            pass
        elif timer == 0:
            raise ValueError('AnPal_Load not modelled')
        else:
            timer -= 1
            from_white() if s.zone_act in WHITE_ZONES else from_black()
    return tuple(n), tuple(w), timer, delay2
