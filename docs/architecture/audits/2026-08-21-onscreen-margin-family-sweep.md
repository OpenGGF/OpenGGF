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

## Stage 1 landed; stage 2 is blocked (2026-08-21, `72f4de020`)

**Stage 1 — `ObjectRangeOps`, landed with no callers.** The ROM comparison above, modelled
exactly: `$80`-block quantisation of both operands, camera offset a block first, 16-bit
subtraction, unsigned compare against 640, X only. Guard test pins the bound, the coarse camera
term, and the three properties a margin band cannot express. `MarkObjGone`'s respawn-bit clear
and its two-player early-out are deliberately excluded so no caller inherits them silently.

**Stage 2 — the render-flag predicate — is blocked, and no proxy was landed.**

The engine has **no per-object draw outcome**, and that is a statement about role rather than
about a grep. There is no renderer write-back of any kind: objects emit
`appendRenderCommands(List<GLCommand>)` and nothing records whether an object emitted anything.
The only "on screen" notion available to an object is
`AbstractObjectInstance.isWithinRenderSpriteBounds(...)`, which the object computes **itself,
from the camera, during its own update** — a current-frame bounds test. That is precisely the
proxy a sibling lane declined to land on for `Obj82`'s swing gate, and landing it here would
rebuild the same conflation one level up: a distance check wearing the name of a draw outcome.

**What would unblock it:** recording, per object per frame, whether the object was actually
drawn — a renderer write-back that does not exist today.

**And a trap for whoever builds it.** Headless trace replay calls
`GraphicsManager.getInstance().initHeadless()`, so a graphics manager exists — but whether the
per-object render pass actually executes under trace replay determines whether such a flag is
ever set in the one environment that measures these objects. **If the render pass does not run
headlessly, the flag reads "never drawn" and the predicate deletes everything, on every trace.**
That failure mode would look like a working implementation right up until the suite went red,
so establish it *before* writing the write-back, not after.

Because stage 2 is blocked, the conversion stages behind it are not started: the 2 render-flag
sites cannot be converted, and the 3 X-axis sites and the remaining 30 wait on the merge-and-
measure checkpoint as planned.

## The headless question, answered as a measurement (2026-08-21, `d448ec294`)

**The per-object render pass does not execute under headless trace replay.** Measured, not read.

Method: counters and first-hit prints on every call site in the object render path in
`ObjectManager`, plus a **positive control** in the `ObjectManager` constructor so that silence
could be distinguished from a broken probe channel. Run:
`mvn -Dmse=off -Ptrace-replay -Dsonic2.rom.path=<repo>/s2.gen -Dtest=TestS2WfzLevelSelectTraceReplay test`.

| probe | fired |
|---|---|
| **control** — `ObjectManager` constructor | **1** — channel works, `System.err` is captured |
| `renderBucketSnapshot.capture` | **0** |
| `drawPriorityBucket` entry | **0** |
| `appendRenderCommands` via `drawPriorityBucket` (`ObjectManager:1519`) | **0** |
| `appendRenderCommands` via `drawBucketInstances` (`ObjectManager:1567`) | **0** |

The test itself is confirmed to have executed — `Tests run: 1` — so this is a measured zero and
not an absent run. Both halves matter: **a zero from a probe whose channel is unproven is not a
zero**, and an absent probe line in a run that never ran is not evidence of anything. Each of
those mistakes cost a round today.

**Consequence for the render-flag predicate.** A per-object draw-outcome flag would be written
by code that never runs during trace replay. Every object would read "not drawn last frame", and
a predicate deleting on that would **delete every object on every trace** — while looking like a
correct implementation, because the flag, the predicate and the ROM citation would all be right.
The only thing wrong would be that the producer never runs.

**This is therefore not a "build the write-back more carefully" problem.** It is a question about
what trace replay can observe at all: the ROM's `render_flags.on_screen` is a *rendering* outcome,
and the replay harness does not render. Any faithful implementation needs a source of truth that
exists in a non-rendering run — which is a design decision, not an implementation detail.

**Stage 2 and every conversion behind it stay blocked.** `ObjectRangeOps` (stage 1) is unaffected:
it takes object x and camera x and reads nothing from the render path.

## Stage 2 is UNBLOCKED — the predicate needs no renderer write-back (2026-08-21)

`isPreUpdateWithinRenderSpriteBounds` transfers to `Obj98`, and the pattern is already
established in this tree. All three conditions hold:

**(a) The helper's shape matches `BuildSprites` exactly.** `containsRenderSpriteBounds` is
`x >= left - xMargin && x < right + xMargin` with
`y >= top - yMargin && y < bottom + yMargin` and an 11-bit vertical wrap. `BuildSprites`
(`docs/s2disasm/s2.asm:30567-30576`) tests `(x - cam) + width_pixels >= 0` and
`(x - cam) - width_pixels < screen_width`, then the Y band — and masks `#$7FF`, the wrap the
helper models. This is an asymmetric per-axis edge test, **not** a symmetric margin: unlike
`isOnScreen`, this helper was built for this predicate.

**(b) `Obj98` is queued for `BuildSprites` on every surviving frame**, so its flag can never go
stale. `Obj98_Main`'s tail is `MarkObjGone` (`s2.asm:74681`), which either reaches
`DisplaySprite` — queueing the object — or deletes it. Either way the next frame's flag is
fresh. This is the property the sibling lane established separately for its own object, and it
had to be checked rather than assumed.

**(c) Both margins are ROM-derived and pass the two-halves test.** For the wall turret shot,
`xMargin` is `width_pixels` = 4, from the object's own attribute data; `yMargin` is **32**, and
I can name the routine that computes it — `BuildSprites_ApproxYCheck` (`s2.asm:30609`) — and
the branch that selects it — `btst #render_flags.explicit_height,d4 / beq.s
BuildSprites_ApproxYCheck` (`s2.asm:30580-30581`), reached because `Obj98`'s attribute data
leaves `explicit_height` clear.

**The pattern is not novel.** `CollapsingPlatformObjectInstance:189-203` already does exactly
this, with `APPROX_RENDER_Y_MARGIN = 32 // BuildSprites_ApproxYCheck assumed radius`, as do
`GrounderWallInstance`, `GrounderRockProjectile`, `Sonic1SLZBossSpikeball` and two Knuckles
cutscene objects. The render-flag sites in this family are the ones that **did not** use it.

**One guard to check per site before converting.** `snapshotPreUpdatePosition()` begins
`if (getSpawn() == null) return;`, leaving `preUpdateValid` false, and the helper returns
`false` when it is. On a delete-on-not-drawn site that reads as "not drawn" and deletes
immediately. `WallTurretShotInstance` carries a spawn (observed directly in probe output as
`spawn=2341,357`), so it is safe; any other site must be checked the same way rather than
assumed.

**Consequence.** Stage 2 needs no renderer write-back, so the headless finding above — true and
unchanged — is not a blocker for this path. It would only bind if someone later wanted a real
draw-outcome flag for a predicate this helper cannot express.
