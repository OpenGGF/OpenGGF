# WFZ Ending Sky→Space Background Transition — Design Spec

> **Status:** Design spec for review. Implementation plan (TDD tasks) included at the end.
> **For agentic workers:** once approved, use superpowers:subagent-driven-development or
> superpowers:executing-plans to implement the tasks. Steps use checkbox (`- [ ]`) syntax.

**Goal:** As Sonic rides Robotnik's getaway ship up at the end of Wing Fortress Zone,
the background should scroll gradually from the WFZ sky up into space, exactly as the ROM
does — a vertical background scroll driven by `Camera_BG_Y_offset`, not a palette fade.

**Non-goals:** No change to the DEZ starfield credits (`SwScrl_DEZ`); no change to the
WFZ→DEZ hard cut timing; no new render subsystem. This is a wiring + one-method-rewrite fix.

---

## 1. Background: how the ROM drives the transition

Confirmed from `docs/s2disasm/s2.asm` (see the WFZ-ending background investigation). The
sky→space reveal is a **vertical background scroll**, not a palette effect:

| Step | ROM routine | s2.asm | Effect |
|------|-------------|--------|--------|
| Trigger | `ObjB2_Jump_to_ship` sets `Dynamic_Resize_Routine = 6` | 79090 | selects `LevEvents_WFZ_Routine4` |
| Offset ramp | `LevEvents_WFZ_Routine4` | 20657-20678 | ramps `Camera_BG_Y_offset` up toward `#$1B81` and `Camera_BG_X_offset` down toward `-$2C0`, then `bra ScrollBG` |
| Diff calc | `ScrollBG` | 20885-20920 | `Camera_BG_{X,Y}_pos_diff = clamp(cam − BG_pos − BG_offset, ±16)` |
| Advance + reveal | `SetVertiScrollFlagsBG` | 18400-18429 | `Camera_BG_Y_pos += diff`; flags newly-revealed BG rows for tile upload |
| Display | `SwScrl_WFZ` | 15645 | `move.w Camera_BG_Y_pos, Vscroll_Factor_BG` |
| Hard cut | `ObjB2_Start_DEZ` | 79162-79169 | at `objoff_2A ≥ $9C0`: `Current_ZoneAndAct = DEZ`, `Level_Inactive_flag = 1` |

Net effect: as `Camera_BG_Y_offset` climbs, the `ScrollBG` Y-diff pins to `−16`, so
`Camera_BG_Y_pos` decreases ~16 px/frame, scrolling the WFZ background plane **up** to
reveal the space/star region at the top of that plane. It converges toward
`cameraY − Camera_BG_Y_offset`, chased at ≤16 px/frame.

**Not a palette effect:** `PalCycle_WFZ` (s2.asm:2998-3038) only cycles the fire/conveyor
colours on palette line 2; there is no sky→space palette script.

---

## 2. Root cause in the engine (two connected gaps)

The offset ramp is **already implemented correctly**:
`Sonic2WFZEvents.primaryRoutine6_reverse()` (`Sonic2WFZEvents.java:323-349`) ramps
`bgYOffset → $1B81` and `bgXOffset → −$2C0`, mirroring `LevEvents_WFZ_Routine4`. The state
just dead-ends inside `Sonic2WFZEvents`:

**Gap A — `syncBgDiffs()` does not implement `ScrollBG`.**
`Sonic2WFZEvents.syncBgDiffs()` (`Sonic2WFZEvents.java:366-371`) currently snaps the BG
position straight to the camera and ignores `bgXOffset`/`bgYOffset` and the ±16 clamp:
```java
bgXPosDiff = camera().getX() - bgXPos;      // ignores bgXOffset, no clamp
bgYPosDiff = camera().getY() - bgYPos;      // ignores bgYOffset, no clamp
bgXPos = camera().getX();                    // snaps, never scrolls up
bgYPos = camera().getY();
```
So the rising offset has zero effect on `bgYPos`.

**Gap B — the WFZ BG position never reaches the scroll handler.**
`SwScrlWfz.update()` reads a **separate** `BackgroundCamera` instance
(`SwScrlWfz.java:97` → `composer.setVscrollFactorBG((short) bgCamera.getBgYPos())`), owned
by `Sonic2ScrollHandlerProvider`. That `bgCamera`'s WFZ Y is seeded once at level init and
then frozen — `BackgroundCamera.setBgYPos()` has no production caller, and the WFZ branch of
`updateFromForeground()` is a no-op (only MTZ updates the BG camera per-frame). So even a
correct `Sonic2WFZEvents.bgYPos` is never consumed.

---

## 3. Phase 0 gate — RESOLVED 2026-07-16: PASS

**Question:** does the loaded WFZ background plane have room for the revealed "space" region,
or is it only a short sky band (which would make this a BG-layout task)?

**Findings** (from a throwaway `SharedLevel.load(SONIC_2, 9, 0)` probe, since removed):
- The WFZ background is **layer 1 of a 128×16-block, 2-layer map — 2048px (`$800`) tall**,
  exactly matching the ROM's `Camera_BG_Y_pos & $7FF` segment structure. It is **not** a
  256px sky band, and `computeBuildParams` builds the **full** BG height for WFZ
  (`bgLoopBand` is false; `LevelTilemapManager.java:605`).
- Sampling the BG plane (layer 1) top-to-bottom: **rows 0–3 (the top ~512px, the region the
  ending scroll reveals) are blank (block 0)**; rows 4–15 are the repeating sky/cloud
  background (a 4-block period of indices `$25/$00/$50/$1F`).

**Conclusion:** the "space" is the **VDP backdrop revealed above the sky** as the plane
scrolls up — the blank top rows show the backdrop colour. It is **not** a star-tile region in
the WFZ BG layout (the actual starfield is the separate DEZ intro, `SwScrl_DEZ`, after the
hard cut). So this is the **wiring task** below (Tasks 1–3), **not** a BG-layout/tile-load
task. Gate **passes**.

**Residual visual detail (confirm after implementing, via `dev.cmd`):** whether the revealed
top renders as flat black or a dark "space" gradient depends on the WFZ VDP backdrop / top
palette line. This does not change the wiring approach; it's the final human visual check.

---

## 4. Design decisions

### 4.1 `ScrollBG` math + seeding (Gap A)
Rewrite `syncBgDiffs()` to the ROM `ScrollBG` chase, incorporating the offsets and the ±16
clamp, advancing a persistent `bgXPos`/`bgYPos`:
```java
int dx = clamp16(camera().getX() - bgXPos - bgXOffset);
int dy = clamp16(camera().getY() - bgYPos - bgYOffset);
bgXPosDiff = dx; bgYPosDiff = dy;
bgXPos += dx;    bgYPos += dy;
```
**Seeding — no new flag needed.** `bgXPos`/`bgYPos` are already seeded to the camera by the
existing `primaryRoutine0_initBgSync()` (`Sonic2WFZEvents.java:243-247`: `bgXPos = camera().getX()`,
`bgYPos = camera().getY()`), which runs before routine 2/4/6 ever calls `syncBgDiffs()`. So
the `ScrollBG` chase starts from the seeded position; there is **no `bgSeeded` flag** and
**no rewind-snapshot change** (`SNAPSHOT_BYTES` stays `Integer.BYTES * 8`; `bgXPos`/`bgYPos`/
`bgYOffset`/`bgXOffset`/`bgXPosDiff`/`bgYPosDiff` are already in those 8 ints). *(An earlier
draft added a `bgSeeded` flag — dropped: it was redundant with routine 0 and would have both
discarded camera movement between init and the first routine-2 frame and overflowed the
fixed 8-int snapshot buffer.)*

**Trace safety:** during normal play `bgXOffset = bgYOffset = 0` and the S2 camera moves
≤16 px/frame, so `dy = clamp16(cameraY − bgYPos)` keeps `bgYPos == cameraY` — identical to
the current snap. The clamp/offset only diverge during the ending ramp. This is why the
rewrite must NOT regress the WFZ or DEZ-ending traces (see verification).

### 4.2 The event→scroll bridge (Gap B) — a live `WfzRuntimeStateView` (mirrors HTZ)

`SwScrlWfz` must consume the event-owned BG position without any cross-lifetime reference.
An earlier draft proposed injecting the session-owned `BackgroundCamera` into the
module-owned `Sonic2LevelEventManager` — **rejected** on review: (a) the event manager
survives gameplay-context rebuilds while each `Sonic2ScrollHandlerProvider` creates a *new*
session-owned `BackgroundCamera`, so a retained reference goes stale after an editor/session
rebuild; (b) exposing `BackgroundCamera` through `ParallaxManager` violates that manager's
game-agnostic boundary; (c) the duplicated BG position leaves the rendered VScroll stale for
one frame after a rewind restore (the post-restore parallax recompute runs before the next
event frame re-pushes).

**Use the existing zone-runtime-state pattern instead.** `SwScrlHtz` already consumes a
**live** `HtzRuntimeStateView` (over `Sonic2HTZEvents`) via
`GameServices.zoneRuntimeRegistry().currentAs(HtzRuntimeState.class)`
(`HtzRuntimeStateView.java`, `SwScrlHtz.java:61-63`; registered at
`Sonic2LevelEventManager.java:132`). Mirror it exactly:

- **`WfzRuntimeState`** (interface, `com.openggf.game.sonic2.runtime`, extends
  `ZoneRuntimeState`): `int bgVscrollFactor();` (and `int bgXPos();` if the FBO path needs it).
- **`WfzRuntimeStateView implements WfzRuntimeState`**: a **live** view holding a
  `Sonic2WFZEvents` reference — `bgVscrollFactor() { return events.getBgYPos(); }`. No copied
  state.
- **Register** it in `Sonic2LevelEventManager` alongside HTZ/CNZ
  (`installOwnedRuntimeState(registry, new WfzRuntimeStateView(zone, act, wfzEvents))`,
  next to `:132`), so it is (re)installed per session with the current event manager — no
  durable→session retention.
- **`SwScrlWfz`** reads `GameServices.zoneRuntimeRegistry().currentAs(WfzRuntimeState.class)`
  for the VScroll factor instead of `bgCamera.getBgYPos()` (`SwScrlWfz.java:97`, and the
  segment-select `bgY` at `:141`).

**Why this fixes the three P1 issues:** no session-owned object is retained in the durable
event manager and no S2 type leaks through `ParallaxManager` (P1#1); the view reads
`Sonic2WFZEvents` **live**, so after a rewind restore of the event's `bgYPos` (already in the
snapshot) the very next parallax recompute reads the restored value — no duplicate state, no
stale frame (P1#2); and there is no new snapshot field (P1#3, see §4.1).

**Ordering (verified):** `LevelFrameStep` runs `levelEvents.update()` (`LevelFrameStep.java:248`)
before the parallax/scroll pass, so the event's `bgYPos` for the frame is set before
`SwScrlWfz` reads the view. (Live read means even out-of-order restore is correct.)

### 4.3 What does NOT change
- The offset ramp (`primaryRoutine6_reverse`) — already correct.
- The DEZ starfield (`SwScrl_DEZ`) and the ending DEZ hard cut.
- Chunks/blocks/collision and the (now-fixed) pattern supplement.

---

## 5. Verification strategy

1. **Phase 0 spike** decides go/no-go on the star tiles (Section 3).
2. **Unit test** for the `ScrollBG` math: given camera/offset/BG-pos inputs, assert the
   clamped diff and advanced `bgXPos`/`bgYPos` (drives `bgYPos` toward `cameraY − bgYOffset`
   at ≤16/frame; equals `cameraY` when offset 0).
3. **Bridge integration test (through the real context, not a direct setter):** boot a WFZ
   gameplay context, drive the WFZ event to a non-zero `bgYOffset`, run one
   `LevelFrameStep`/`ParallaxManager` pass, and assert the **rendered VScroll factor** (what
   `SwScrlWfz` wrote) equals `Sonic2WFZEvents.getBgYPos()`. This exercises the actual
   `ZoneRuntimeRegistry` registration + `SwScrlWfz` read — a direct `WfzRuntimeStateView`
   unit test alone would pass even if the level-load registration were missing.
4. **Trace regression gate (release-blocking):** `TestS2WfzLevelSelectTraceReplay` and
   `TestS2DezEndingLevelSelectTraceReplay` must stay green
   (`mvn -Ptrace-replay "-Dtest=…" test`). This is the primary guard that normal-play BG
   scroll is unchanged and the ending path doesn't desync.
5. **Rewind:** the view is live over `Sonic2WFZEvents`, and `bgXPos`/`bgYPos`/`bgYOffset` are
   already in the `Sonic2WFZEvents` snapshot (`SNAPSHOT_BYTES`, unchanged). A WFZ-ending
   rewind round-trip must restore those and then render the restored VScroll on the very
   next parallax pass — assert the rendered VScroll after restore (guards P1#2). No new
   snapshot field, and no separate `BackgroundCamera` state to reconcile.
6. **Visual (manual):** ride the ship at the WFZ ending via `dev.cmd`; the sky should
   scroll up into space. Headless can't confirm this — the trace gate + unit tests cover
   correctness; the visual is the final human check.

---

## 6. Global constraints (from repo policy)
- ROM-faithful: model real ROM state (`Camera_BG_Y_offset`/`ScrollBG`); no zone/frame carve-outs.
- Must not regress `TestS2WfzLevelSelectTraceReplay` or `TestS2DezEndingLevelSelectTraceReplay`.
- Non-`master` commits carry the trailer block; `fix`/`feat` touching `src/main` set
  `Changelog: updated` (+ stage `CHANGELOG.md`).
- No `git stash` (repo-wide across worktrees); base the branch on `develop`.

---

## Implementation Plan (TDD, bite-sized)

### Task 0: Phase 0 spike — confirm the WFZ BG plane supports the reveal — DONE (PASS)

Resolved 2026-07-16 (see Section 3). The WFZ background is a 2048px-tall plane whose blank
top rows are revealed as the sky scrolls up (backdrop = "space"); the engine builds the full
height. This is a wiring task, not a BG-layout task. Proceed to Task 1.

- [x] Probe WFZ BG plane dimensions + top-row content via `SharedLevel.load(SONIC_2, 9, 0)`.
- [x] Record finding + gate decision (PASS) in Section 3.

---

### Task 1: Implement the ROM `ScrollBG` chase in `syncBgDiffs()`

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/events/Sonic2WFZEvents.java` (`syncBgDiffs`; add public `getBgXPos()`/`getBgYPos()` accessors for the view in Task 2). No `bgSeeded`, no `init`/snapshot change (routine 0 seeds; §4.1).
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2WfzBgScroll.java` (new)

**Interfaces:**
- Produces: `Sonic2WFZEvents#getBgXPos()` / `#getBgYPos()` (public) returning the post-`ScrollBG` BG position; after routine 0 seeds them to the camera, `bgYPos` chases `cameraY − bgYOffset` at ≤16 px/frame.

- [ ] **Step 1: Write the failing test.** Two cases: (a) with `bgYOffset` set, `bgYPos` moves toward `cameraY − bgYOffset` at ≤16/frame; (b) **regression for P2#1** — camera moves between routine 0 (seed) and the first routine-2 `syncBgDiffs`, and `bgYPos` tracks it (no reseed discards it). Drive through the real routine dispatch (`update()` with `eventRoutine` 0 then 2), using the existing `setBgYOffsetForTest` (`:189`) and a stubbed/advanced `camera()`.
```java
@Test
void scrollBgChasesCameraMinusOffsetClampedTo16() throws Exception {
    Sonic2WFZEvents ev = newWfzEventsWithCamera(cam);   // existing test seam
    cam.set(0x200, 0x180); ev.runRoutine(0);            // routine 0 seeds bgYPos=0x180
    ev.setBgYOffsetForTest(0x100);
    ev.runRoutine(2);                                    // one ScrollBG step
    assertEquals(0x180 - 16, ev.getBgYPos());            // chases toward 0x080 at 16/frame
}

@Test
void seedFromRoutine0ThenCameraMovesBeforeFirstScrollBg() throws Exception {
    Sonic2WFZEvents ev = newWfzEventsWithCamera(cam);
    cam.set(0x200, 0x180); ev.runRoutine(0);            // seed at 0x180
    cam.set(0x200, 0x188);                               // camera moved +8 before routine 2
    ev.runRoutine(2);
    assertEquals(0x188, ev.getBgYPos());                 // tracks (offset 0, ≤16 move) — NOT reseeded/discarded
}
```
- [ ] **Step 2: Run test to verify it fails** — `mvn -o "-Dtest=com.openggf.game.sonic2.TestSonic2WfzBgScroll" test` → FAIL (current snap ignores offset).
- [ ] **Step 3: Implement** — rewrite `syncBgDiffs()` (no flag):
```java
private void syncBgDiffs() {
    int dx = clamp16(camera().getX() - bgXPos - bgXOffset);
    int dy = clamp16(camera().getY() - bgYPos - bgYOffset);
    bgXPosDiff = dx; bgYPosDiff = dy;
    bgXPos += dx;    bgYPos += dy;
}
private static int clamp16(int v) { return v > 16 ? 16 : (v < -16 ? -16 : v); }
```
Add public `getBgXPos()`/`getBgYPos()`.
- [ ] **Step 4: Run tests to verify they pass** (both cases).
- [ ] **Step 5: Run the trace gate** (`TestS2WfzLevelSelectTraceReplay` + `TestS2DezEndingLevelSelectTraceReplay`) → still green (normal play: offset 0, camera ≤16/frame ⇒ behaviourally identical to the old snap).
- [ ] **Step 6: Commit** (`fix(s2): WFZ ScrollBG chase for BG scroll ...`, `Changelog: updated`).

---

### Task 2: `WfzRuntimeStateView` bridge — `SwScrlWfz` consumes the live event BG position

**Files:**
- Create: `src/main/java/com/openggf/game/sonic2/runtime/WfzRuntimeState.java` (interface extends `ZoneRuntimeState`)
- Create: `src/main/java/com/openggf/game/sonic2/runtime/WfzRuntimeStateView.java` (live view over `Sonic2WFZEvents`)
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2LevelEventManager.java:~132` (install the WFZ view next to HTZ/CNZ)
- Modify: `src/main/java/com/openggf/game/sonic2/scroll/SwScrlWfz.java:97,141` (read the registry view instead of `bgCamera.getBgYPos()`)
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2WfzBgBridgeIntegration.java` (new — real context)

**Interfaces:**
- Consumes: `Sonic2WFZEvents#getBgYPos()` (Task 1), `GameServices.zoneRuntimeRegistry()`, the HTZ/CNZ registration idiom (`installOwnedRuntimeState`, `Sonic2LevelEventManager.java:132-145`), `HtzRuntimeStateView` as the template.
- Produces: `WfzRuntimeState#bgVscrollFactor()` returning `Sonic2WFZEvents.getBgYPos()` live; installed in `ZoneRuntimeRegistry` when WFZ loads; read by `SwScrlWfz`.

- [ ] **Step 1: Write the failing integration test** (through the real context, per §5.3 — NOT a direct setter):
```java
// Boot a WFZ gameplay context (mirror an existing SharedLevel / context-boot test),
// drive the WFZ event to routine 6 with a non-zero bgYOffset for a few frames, run one
// LevelFrameStep + ParallaxManager pass, and assert SwScrlWfz's written VScroll factor
// tracks Sonic2WFZEvents.getBgYPos() (scrolls up), not the frozen bgCamera value.
@Test
void wfzEndingScrollReachesRenderedVscrollThroughRuntimeRegistry() { /* ... */ }
```
- [ ] **Step 2: Run to verify it fails** (SwScrlWfz still reads the frozen `bgCamera`).
- [ ] **Step 3: Implement.** Create `WfzRuntimeState` (`int bgVscrollFactor();`) and
  `WfzRuntimeStateView` (live over `Sonic2WFZEvents`, mirroring `HtzRuntimeStateView`);
  install it in `Sonic2LevelEventManager` beside HTZ/CNZ; change `SwScrlWfz` to read
  `GameServices.zoneRuntimeRegistry().currentAs(WfzRuntimeState.class)` (fall back to the
  existing `bgCamera` value if the view is absent, for non-WFZ/test safety).
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Rewind check** — a WFZ-ending rewind restores the event `bgYPos`; assert the
  rendered VScroll on the next parallax pass equals the restored value (guards P1#2).
- [ ] **Step 6: Trace gate** (`TestS2WfzLevelSelectTraceReplay` + `TestS2DezEndingLevelSelectTraceReplay`) green.
- [ ] **Step 7: Commit** (`Changelog: updated`).

---

### Task 3: Regression + rewind verification

**Files:** none (verification), or a small WFZ-ending rewind assertion if a gap surfaces.

- [ ] **Step 1:** Run the trace gate:
      `mvn -Ptrace-replay -o "-Dtest=com.openggf.tests.trace.s2.TestS2WfzLevelSelectTraceReplay,com.openggf.tests.trace.s2.TestS2DezEndingLevelSelectTraceReplay" test` → both green.
      If either regresses, bisect the diff (Task 1 vs Task 2) and fix; do not proceed.
- [ ] **Step 2:** Run `TestSonic2WfzBgScroll` + `Sonic2WFZEvents` rewind snapshot tests → green.
- [ ] **Step 3:** Manual visual check via `dev.cmd` at the WFZ ending (record result).
- [ ] **Step 4:** Update `CHANGELOG.md` (single feature entry) and merge to `develop` per policy (README release-log update on merge).

---

## Self-review notes
- Spec coverage: Gap A → Task 1; Gap B → Task 2; Phase 0 gate → resolved (§3, PASS);
  trace/rewind safety → Task 3 + each task's gates.
- Phase 0 gate resolved: the BG plane is 2048px tall and the "space" is the backdrop revealed
  above the sky (§3) — wiring task, not BG-layout task.

## Review resolution (Codex, 2026-07-16)
All five items verified against the codebase and folded in:
- **P1 (cross-lifetime injection):** dropped the `BackgroundCamera` injection; switched to a
  live `WfzRuntimeStateView` in `ZoneRuntimeRegistry`, mirroring `HtzRuntimeStateView`/`SwScrlHtz`
  (verified `Sonic2LevelEventManager.java:132`, `HtzRuntimeStateView.java`, `SwScrlHtz.java:61`). §4.2.
- **P1 (rewind stale):** resolved by the same change — the view is live, so the snapshot-restored
  event `bgYPos` is read on the next parallax pass; no duplicate state. §4.2/§5.5.
- **P1 (snapshot bytes):** moot — the `bgSeeded` field is gone (§4.1), so `SNAPSHOT_BYTES`
  (`Integer.BYTES * 8`) is unchanged (verified `Sonic2WFZEvents.java:30`,
  `Sonic2LevelEventManager.java:49-54,311`).
- **P2 (routine 0 already seeds):** verified `primaryRoutine0_initBgSync()`
  (`Sonic2WFZEvents.java:243-247`); dropped `bgSeeded`; added the movement-between-init
  regression test (Task 1 case b).
- **P2 (bridge test bypassed production):** Task 2's test is now an integration test through the
  real gameplay context/registry asserting rendered VScroll (§5.3).
