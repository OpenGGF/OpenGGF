# The `isOnScreen(<literal>)` family: a classified population

**Measured at** `3f9bc92cb`, 2026-08-21. **Survey only — nothing fixed.**

## Read this first: on a moving object, `spawn` is not an identity

The round that produced this sweep nearly returned the opposite verdict. Measuring how long
engine projectiles live, I keyed each object on `(slot, spawn)` and got **713 of 714 objects
living exactly one sample** — apparently instant death, i.e. *over-creation*. That is wrong. The
`spawn` record is rebuilt as the object moves, so the key changes every frame and every sample
looks like a new object. Re-keyed on **slot alone**, the answer inverted: 36 distinct objects
living a mean of 19.86 samples against the ROM's 64 living 2.48 — *under-deletion*.

Same trap as the moving LZ blocks, which rebuild their spawn record every frame they travel.
**Identity and position are different things, and a positional key silently manufactures a new
object every time the thing moves.** Both failure modes produce a clean-looking number with a
confident wrong owner behind it.

## Population

The commissioned scope was "54 literal-margin call sites". The real population is larger and the
literal-margin sites are a minority of it:

| shape | sites |
|---|---|
| `isOnScreen()` / `isOnScreenX()` — no argument | 90 |
| `isOnScreen(<expression>)` — computed margin | ~58 |
| **`isOnScreen(<literal>)` — the commissioned scope** | **50** |
| total call sites | **183** |

## The literal-margin 50, by ROLE

Role matters more than fidelity, because only one role can produce an occupancy divergence.

| role | sites | can it cause the occupancy signature? |
|---|---|---|
| **deletion gate** (guards `setDestroyed`) | **35** | yes |
| `isPersistent()` return | 6 | no — respawn contract, different mechanism |
| behaviour gate (sound, animation, activation) | ~10 | no — e.g. four `ChainedStomper` sites gate a *sound* |

## The three "confessed" sites are already fixed

`Sonic1PushBlockObjectInstance`, `Sonic1MotobugBadnikInstance` and `Sonic1BombShrapnelInstance`
read as confessions, but their comments are **past tense**: "previously `isOnScreenX(320)`",
"freed the slot ~160px too early", "not the raw `isOnScreenX(160)`". They record debt already
**paid**, not debt outstanding. **The "confessed approximation" class is empty of live sites.**

They are still valuable — as the template. `PushBlock` now uses `isOnScreen(activeWidth + 128)`:
the margin derives from the object's own ROM width rather than a literal, which is why my
literal-only search did not see it.

## Why "cited-but-divergent versus faithful" does not partition these

Of the 35 deletion gates: **28 cite no ROM routine at all**, 4 cite some routine, 2 cite the
render flag, 1 cites `MarkObjGone`/`out_of_range`. And in **33 of 34** measurable cases the
margin literal appears nowhere in the site's own commentary. But the deeper problem is that
**"faithful" is unreachable with this helper**, for every site, at any margin.

**The ROM's off-screen deletion is coarse, asymmetric, unsigned, and X-only.**

S1, `out_of_range` (`docs/s1disasm/Macros.asm:278-293`):

```
        andi.w  #$FF80,d0          ; object x rounded down to a $80 block
        move.w  (v_screenposx).w,d1
        subi.w  #128,d1
        andi.w  #$FF80,d1          ; (camera-128) rounded down to a $80 block
        sub.w   d1,d0
        cmpi.w  #128+320+192,d0    ; = 640
        bhi     exit               ; UNSIGNED
```

S2, `MarkObjGone` (`docs/s2disasm/s2.asm:30215-30226`) is the same shape:
`andi.w #$FF80,d0`, `sub.w (Camera_X_pos_coarse).w,d0`,
`cmpi.w #$80+roundToNextMultiple(screen_width,$80)+$80,d0` — also **640** — `bhi`.

The engine's helper is `isOnScreen(margin)` → `cameraBounds.contains(x, y, margin)`:
**symmetric, fine-grained, two-dimensional.** It diverges on four independent axes at once:

| | ROM | `isOnScreen(m)` |
|---|---|---|
| bound | 640 | `2m + screen` |
| granularity | 128-px blocks, both operands | exact pixels |
| symmetry | unsigned — anything left of `camera-128` wraps and is deleted | symmetric band |
| axis | **X only** | **X and Y** |

**32 of the 35 deletion gates use the 2-D form** where the ROM tests X alone.

**No choice of margin fixes any of this.** A symmetric fine-grained 2-D band cannot express an
asymmetric coarse unsigned 1-D comparison. Correcting the constants would leave every site still
wrong, and would look like progress.

## One site's comment is wrong three ways

`ArrowProjectileInstance:104-107` cites
`cmpi.w #$80+320+$40+$80,d0 ; 480 pixels` and then calls `isOnScreen(480)`. That expression
evaluates to **640**, not 480; the real S2 constant is
`$80+roundToNextMultiple(320,$80)+$80`, also **640**; and the code uses a third number. A
citation, an arithmetic slip and an implementation that agree with each other about nothing —
and it passes review because the ROM line is right there.

## And a second ROM predicate is in play

`Obj98_Main` does not use `MarkObjGone` for its removal at all — it deletes on the **render
flag**, "was this drawn last frame" (`docs/s2disasm/s2.asm:74678-74679`), with `MarkObjGone`
only as its tail. Two of the 35 sites cite that predicate. It is not a tighter distance band; it
is a different question, and it needs a different primitive again.

## Recommendation — do not commission 35 constant fixes

The batch landings this sweep was meant to feed cannot be "correct the margin", because the
margin is not the defect. What is missing is the **primitive**: an `outOfRange` helper that
models the coarse/asymmetric/unsigned/X-only comparison, and a separate render-flag predicate.
Add those two, then convert sites to them — one designed change plus mechanical conversions,
against 35 bespoke constants that cannot be made right.

Sequencing that keeps it revertable: land the primitives with no callers; convert the 2 render-flag
sites; convert the 3 `isOnScreenX` sites, which are already on the correct axis; then the
remaining 30 in per-game batches. The 6 `isPersistent` and ~10 behaviour sites are **out of scope**
and should not be touched by this programme — they are a different contract and a different
question.
