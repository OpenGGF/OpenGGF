# LBZ frame 23533: not a speed cap -- a Ribot child running two rows ahead

Attribution round for the frontier `TestS3kLbzZoneSliceTraceReplay` moved onto
after the Flybot767 wake-phase fix. Measured at `2e15b6251`.

```
Totals: 4585 errors, 0 warnings.
First error: frame 23533 -- x_speed mismatch (expected=0x016F, actual=0x0200)
```

Attributed, not fixed. The owner is upstream of the reported field and the
remedy is not a limit.

## The round number is not a cap

`0x0200` against `0x016F` reads as a value clamped to a limit. It is not.

At row 23533 (`physics.csv` row `5BED` -- the frame column is hexadecimal) the
engine is in the **hurt** state and the ROM is not:

| | ROM @23533 | engine @23533 | ROM @23534 |
|---|---|---|---|
| `rings` | 21 | 0 | 0 |
| `routine` | `0x02` | `0x04` | `0x04` |
| `status_byte` | `0x06` | `0x02` | `0x02` |
| `animation_id` | `0x02` | `0x1A` | `0x1A` |
| `x_speed` / `y_speed` | `0x016F` / `-0018` | `0x0200` / `-0400` | `0x0200` / `-0400` |

`0x0200` and `-0x400` are the S3K hurt knockback, not a speed limit, and the ROM
writes exactly those values one row later. The recording confirms the ROM's
timing independently: 21 `Obj_LostRings` (`object_code 0x0001A64A`) appear in aux
`object_appeared` at frame **23534**, and `rings` drops `21 -> 0` on the same row.

So this is the same class as LBZ 411 -- an event landing one row early -- not an
acceleration source and not a cap. Nothing here should be tuned.

## What hurts him

`HURT by=RibotChild at=(1270,063C)`, from a probe on the hurt path. The hazard is
ROM slot 20, `object_code 0x0008C370` = `loc_8C370` (`sonic3k.asm:191313`), a
`parent3`-owning child of `Obj_Ribot` (`sonic3k.asm:191259`) that draws through
`Child_DrawTouch_Sprite`. The engine calls it `RibotChild`. It is long-lived, not
a projectile: aux `object_appeared` creates it at frame 23436, ~97 rows earlier.

## The child is two rows ahead of the ROM

Measured with a frame-delimited probe carrying the driver's compared row, per
rule 121. Within one engine frame the order is `SCAN` -> `MOVE` -> `DRIVE idx=N`,
so the `MOVE` in the frame that ends with `DRIVE idx=N` writes the engine's
end-of-row-N position.

| | engine end-of-row | ROM `object_state` slot 20 |
|---|---|---|
| `0x0639` | row 23530 | frame 23532 |
| `0x063C` | row 23531 | frame 23533 |

Engine end-of-row-`N` equals ROM end-of-row-`N+2`.

**The touch phase is not at fault.** `SCAN` precedes `MOVE` in every frame, so the
scan consumes the end-of-previous-row position -- exactly `Touch_Loop`
dereferencing the object pointer at Sonic's slot before `Process_Sprites` reaches
the child (`sonic3k.asm:20655-20681`, `22018-22022`, `35963-35995`). `preU ==
live` at scan time for the same reason as in the Flybot round: the object has not
moved yet.

## It is masked, not fresh

Checked rather than assumed, because a first error moving is not the same as a
row becoming wrong. The **pre-fix** report at `fd7739bdf^` already contains
**9 error spans starting at 23533**, with identical values -- `x_speed 0x016F` vs
`0x0200`, `g_speed`, `y`, `y_speed`, `routine`, `rolling`. So 23533 is a
pre-existing divergence that the frame-411 error was hiding, and the wake-phase
fix neither caused nor moved it.

The first attempt to check this reported "not present" because the walker looked
for a `frame` key while the report stores `start_frame` / `end_frame`. That was a
fact about the walker. Rule 123, one level down: an absence needs the shape
checked before it is believed.

## Named upstream owner, and where to go next

The owner is **the Ribot child's own timing**, not the touch path, not the
player's physics, and not any limit. The open question is why it leads by two
rows: a creation-frame or wake-phase error at its 23436 creation that has simply
persisted, or a per-frame double-step. Those predict different things and are
distinguishable by walking its position series back from 23533 to 23436 against
`object_state` and finding where the lead is acquired -- one row at a time, or
two at once.

Not attempted here, and deliberately not fixed.
