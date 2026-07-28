# Backport candidates for `develop`

Fixes made on `next` during the 2026-07-28 `develop` → `next` merge that are **not
next-specific** and would apply to `develop` on their own. Each entry says what the defect
is on `develop` today, so the backport can be judged without re-deriving it.

Excluded by definition: anything touching the Mod API, `game.patch`, multiplayer `net.*`,
time attack, the v2/v3 editor, or the mod SDK — those exist only on `next`.

## Production defects

### 1. `AutomaticTunnelObjectInstance` writes playable positions directly

**On develop:** nine `player.setCentreXPreserveSubpixel(...)` / `setCentreYPreserveSubpixel(...)`
calls that bypass `NativePositionOps`. `NativePositionOps` exists on `develop`, and
`CLAUDE.md` requires playable-sprite native writes to route through it.

**Fix:** route all nine through `NativePositionOps.writeXPosPreserveSubpixel` /
`writeYPosPreserveSubpixel`.

**Why it matters on develop:** the routing exists so subpixel handling stays in one place;
direct writes are the class of bug the rule was written to prevent. `develop` has no guard
covering this file, so nothing there catches it.

### 2. AWT in `WindowIconLoader` blocks native images

**On develop:** `java.awt.Taskbar` + `java.awt.image.BufferedImage` + `javax.imageio.ImageIO`
in the macOS Dock icon path, reached from ordinary window setup.

**Fix:** delete `applyTaskbarIcon()` and its imports. The `.app` bundle still supplies the
Dock icon; only a plain-JAR launch loses it.

**Why it matters on develop:** AWT anywhere reachable from the entry point defeats a native
image build. `develop`'s own `TestProductionAwtBlacklistGuard` has a shrink-to-zero baseline
that this file must currently be violating or exempted from.

## Diagnostics

### 3. `RewindRegistry` capture failure cannot name the offending adapter

**On develop:** a null snapshot throws
`"Rewind snapshot must not be null for key: " + key`. When the adapter's `key()` is also
null the message is literally `key: null`, which identifies nothing — this cost real time to
track down during the merge (it turned out to be a mocked `CollisionSystem`).

**Fix:** append the adapter's class name.

## Guard correctness

### 4. `TestModifierSupportDocumentation` marks sibling bindings chord-dead

**On develop:** read sites are extracted with `statementAround(...)`, i.e. the whole enclosing
statement. A single `return a(X) || b(Y) || c(Z)` in which *one* binding is read through
`isKeyPressedWithoutModifiers` makes the guard treat **every** binding named in that statement
as chord-dead, demanding they all be documented in the "Chord permanently dead" row.

**Fix:** narrow inline reads to their innermost-enclosing *outer* call
(`callAround(...)`), keeping the whole statement only for hoisted
`int someKey = ...` assignments, which are judged together with the checks applied to the
local.

**Why it matters on develop:** it is latent there — it fires as soon as a modifier-checked
binding is OR'd together with unchecked ones in one statement, and the failure blames the
wrong bindings. `next` hit it because `TimeAttackDebugInput` has exactly that shape, but the
parser bug is `develop`'s.

### 6. S3K `ObjectSlotLayout` dynamic slot count is 89; the ROM says 90

**On develop:** `ObjectSlotLayout.SONIC_3K = new ObjectSlotLayout(4, 89, 110, ...)`.

**The ROM:** `Dynamic_object_RAM ds.b object_size*90` — 90 objects
(`docs/skdisasm/sonic3k.constants.asm:307`). With `firstDynamicSlot = 4` the managed window is
absolute SST slots 4-93, so `lastDynamicSlotExclusive()` must be 94. That is exactly the range
`Offset_ObjectsDuringTransition` walks: it starts at `Dynamic_object_RAM + object_size` and
runs `(Breathing_bubbles - (Dynamic_object_RAM + object_size))/object_size - 1` through `dbf`,
i.e. 90 iterations over absolute slots 4-93 (`sonic3k.asm:104166-104180`).

**Why it matters on develop:** 89 makes the last dynamic slot 92 and drops slot 93 out of the
allocatable window entirely, so any object the ROM would place there is either refused or
displaced, and every downstream slot identity shifts.

**Caution:** this changes object slot identity, so it moves trace output. It wants a
`*TraceReplay` sweep on `develop`, not a blind cherry-pick — `develop`'s 89 may have been
tuned to make some specific trace pass, in which case that trace's real defect is elsewhere
and this change will surface it.

### 7. `TestAizFireCurtainRendererRom` does not pump the hardware timing service

**On develop:** the test calls `events.update(act, frame)` bare. `develop` paces AIZ's fire
phases on `HardwareTimingService`, so art readiness never advances. Servicing
`VINT_SERVICE` / `PRE_MAIN_LOOP` / `POST_OBJECTS` around each update (the pattern
`TestSonic3kAIZEvents#updateWithHardware` already uses) takes
`realAizFakeoutReportsPerPhaseCurtainDescriptorStats` green — 4 failures down to 3. The
remaining 3 are the renderer/executor mismatch in item 5.

## Pre-existing `develop` failures (not caused by the merge)

Confirmed by running the class on a clean `origin/develop` worktree, not inferred.

### 5. `TestAizFireCurtainRendererRom` — all four methods fail on `develop`

**On develop:** 4/4 fail. `AizFireCurtainRenderer` is byte-identical on both branches, so the
cause is `develop`'s `S3kSeamlessMutationExecutor` rework, which changed the AIZ1 fire
transition from writing overlay chunks into the BG layout ("Applied AIZ1 fire transition
overlays (128x128/16x16/8x8)") to "palette, PLC, and transition floor". The renderer still
samples the BG layout and only emits a tile draw when the sampled pattern index falls in the
staged flame-overlay range `[0x500, 0x500+121)`; with overlays no longer written, every
column produces zero draws and the composition plan is empty.

**Either** the renderer needs to follow the executor's new model, **or** the executor is
dropping a write it should still make — this needs someone who knows which of the two
`develop` intended. Not resolved here: it is `develop`'s regression and fixing it blind
risks encoding the wrong half as correct.

**Merge note:** these tests additionally need the hardware timing service pumped per frame
(`VINT_SERVICE` / `PRE_MAIN_LOOP` / `POST_OBJECTS`) because `develop` paces the AIZ fire
phases on it. That fix is applied on `next` and takes 1 of the 4 green; it is a
straightforward backport that reduces `develop`'s failure count from 4 to 3.

## Not backportable (recorded so they are not re-proposed)

- `DelegatingGameModule` missing forwards for `createInitialFixedSstDispatcher` and
  `getTracePlaybackProfile` — `DelegatingGameModule` is `next`-only.
- `PixelImage` / `PngCodec` pure-Java PNG path — replaces the mod SDK's ImageIO use, and the
  SDK is `next`-only. Would only matter on `develop` if it grows a PNG pipeline.
- `playNamespacedSfx` live-command gating — creator audio, `next`-only.
- `ObjectManager.execOrder` sizing / `isManagedDynamicSlot` range / participant-scoped solid
  checkpoints / mod fault-boundary restoration — all reconciliations of `next` features
  against `develop`'s refactor, not defects on `develop`.
- S3K `dynamicSlotCount` 89 — this *is* `develop`'s value; `next` had drifted to 90.
