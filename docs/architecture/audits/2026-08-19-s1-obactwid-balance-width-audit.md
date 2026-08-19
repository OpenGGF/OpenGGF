# S1 `obActWid` vs engine balance width — audit of every Sonic 1 `SolidObjectProvider`

**Date:** 2026-08-19. **Base:** `bdbcc274c`. **Scope:** the 42 classes under
`src/main/java/com/openggf/game/sonic1/objects/` that implement
`SolidObjectProvider`, checked against the `obActWid` byte their ROM object
writes.

**Nothing here is measured against a trace.** Every entry is a static
ROM-versus-engine comparison. Reachability is noted where it is obvious and
flagged where it is not. This is a list of candidates for the next round, not a
list of confirmed live defects — the one confirmed instance (Obj30, commit
`d7422d98f`) was found by a trace, and these were found by reading.

## What `obActWid` is, and why one byte matters twice

`_Constants.asm:230` names it *action width*; the per-object comments call it
*sprite display width*. Neither is the whole story, and it is never the rendered
sprite extent — the mappings own that. Three consumers read it:

- **`BuildSprites`' horizontal on-screen cull.** `d1 = obX - cameraX ± obActWid`
  tested against 0 and 320 (`docs/s1disasm/_inc/BuildSprites.asm:49-58`). This is
  the engine's `getOnScreenHalfWidth()`.
- **`Sonic_Balance`.** `d1 = obActWid(a1) + obX(a0) - obX(a1)`, balancing when
  `d1 < 4` or `d1 >= 2*obActWid - 4` (`_incObj/01 Sonic.asm:423-431`). This is
  `getBalanceWidthPixels()`, which defaults to the on-screen width.
- **Many objects' own solidity width**, passed to `PlatformObject`/`SolidObject`
  either raw or after `addi.w #sonic_solid_width,d1` (`$B`).

So a wrong `obActWid` is wrong in up to three places at once, and the engine's
`getBalanceWidthPixels()` default of 16 is only correct for objects whose ROM
byte happens to be `#32/2`.

## Two distinct failure modes

**(A) Full-solid objects fall back to the shared 16.** `getBalanceWidthPixels()`
returns `getOnScreenHalfWidth()` = 16 unless overridden. Every full-solid S1
object whose ROM `obActWid` is not 16 is wrong by the difference.

**(B) Top-solid objects fall back to `params.halfWidth()`, which is not always
`obActWid`.** The default assumes a `PlatformObject` caller passes `obActWid`
straight through as `d1`. Most do. **The two collapsing objects do not**, and
they are the more interesting half of this audit.

## Verified mismatches

`d1` column is the ROM's `SolidObject`/`PlatformObject` argument, shown to
demonstrate that the engine's *collision* width is already correct and only the
balance width is not.

| Object | ROM `obActWid` | ROM solid `d1` | engine balance width | delta |
|---|---|---|---|---|
| Obj1A Collapsing Ledge | **100** (`#200/2`, `FixBugs=0`) | `SlopeObject` 48 | 48 | −52 |
| Obj53 Collapsing Floor | **68** (`#136/2`) | `PlatformObject` 32 | 32 | −36 |
| Obj69 SBZ Trapdoor | **128** (`#256/2`, `FixBugs=0`) | 64+`$B` | 16 | −112 |
| Obj4E MZ Lava Wall | 80 | 32+`$B` | 16 | −64 |
| Obj33 Push Block (4x1) | 64 | `obActWid`+`$B` | 16 | −48 |
| Obj36 Spikes (subtype `$4x`) | 64 | `obActWid`+`$B` | 16 | −48 |
| Obj66 SBZ Rotating Junction | 48 parent / 56 child | 37+`$B` | 16 | −32 / −40 |
| Obj36 Spikes (subtype `$3x`) | 28 | `obActWid`+`$B` | 16 | −12 |
| Obj0C LZ Flapping Door | 40 | 8+`$B` | 16 | −24 |
| Obj36 Spikes (subtype `$0x`) | 20 | `obActWid`+`$B` | 16 | −4 |
| Obj3B GHZ Purple Rock | **19** (`#38/2`, `FixBugs=0`) | 16+`$B` | 16 | −3 |
| Obj26 Monitor | **15** (`#30/2`) | 15+`$B` = `$1A` | 16 | +1 |
| Obj2A SBZ Small Door | 8 | 6+`$B` | 16 | +8 |
| Obj44 GHZ Edge Wall | 8 | 8+`$B` = `$13` | 16 | +8 |
| Obj36 Spikes (subtype `$2x`) | 4 | `obActWid`+`$B` | 16 | +12 |

Four of these are on a `FixBugs` conditional and the value above is the shipped
`FixBugs = 0` branch — the wider, "wrong", accurate one. Obj1A's own comment
argues 200 pixels is too big and "could cause wrapping issues"; that is the
branch the traces record.

## Notable, because it is the easy one to get wrong

**The ROM byte is already in the engine for several of these.** `Sonic1Spike`
holds `actWidth` and builds its solid params as `actWidth + 0x0B`;
`Sonic1PushBlock` does the same with `activeWidth`; `Sonic1Monitor` hardcodes
`0x1A`, which *is* `15 + $B`. In each case the engine has derived the correct
collision width from the correct `obActWid` and then not handed that same value
to the balance test. These are one-line fixes with the value already local.

**Obj26 Monitor is the highest-traffic entry** and the smallest delta. The player
stands on monitors constantly, and `#30/2` = 15 is one pixel off the default, so
its balance window is shifted by one pixel at both edges.

## Checked and correct

Obj18 Platforms (32), Obj5A Circling Platform (24), Obj6C Vanishing Platform
(16), Obj63 LZ Conveyor (16), Obj5E Seesaw (48), Obj15 Swinging Platform,
Obj52 Moving Blocks and Obj33's 1x1 subtype all pass `obActWid` to the platform
helper unchanged, so the top-solid default resolves to the ROM byte. Obj32
Button, Obj3C Smashable Wall, Obj46 MZ Bricks, Obj51 Smashable Green Block and
Obj6F Spin Platform Conveyor are all `#32/2` = 16, which the shared default
happens to match. Obj56, Obj61, Obj59, Obj2F, Obj70, Obj6B, Obj31, Obj30 and the
FZ cylinder already override.

## Not established

- **No trace evidence for any row above.** Balance requires the player grounded,
  still, and `Status_OnObj` on that object; I did not establish that any
  committed fixture reaches that state on any of them.
- **Reachability varies and is not assessed.** Standing still on a spike, on the
  lava wall, or on the rotating junction is not obviously reachable in normal
  play even where it is mechanically possible.
- **The render-cull half of each mismatch is not assessed at all.** Where the
  ROM byte differs, `BuildSprites` culls at a different X than the engine does,
  which is a second divergence per row and would need its own measurement.
- Obj71 Invisible Barrier and Obj3E Prison Capsule derive `obActWid` from
  subtype or a table; I confirmed the engine models the collision width from the
  same source but did not enumerate per-subtype values.

## Recommendation

Do not blanket-fix. Each row needs its ROM byte read at its own site, a decision
on whether the byte is the object's on-screen width too (supply it at
`getOnScreenHalfWidth()`) or only its balance width (override
`getBalanceWidthPixels()`), and a full `-Ptrace-replay` set-diff. The two
collapsing objects are the most valuable to take first: they are the ones that
falsify the current default rather than merely fall through it, and any fix
there should also revisit whether `getBalanceWidthPixels()`'s top-solid fallback
should exist at all.
