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
**Seeding:** `bgXPos`/`bgYPos` init to `0` and are only meaningful once seeded to the
camera. The current code snaps every frame so init is irrelevant; a chase from `0` would
scroll the BG into place over many frames at level start (visible corruption + trace risk).
Add a `bgSeeded` flag; on the first `syncBgDiffs()` call seed `bgXPos = camera().getX()`,
`bgYPos = camera().getY()`, then chase. Reset `bgSeeded = false` in `init(act)` and capture
it in the rewind snapshot (`captureSnapshot`/`restoreSnapshot`, `Sonic2WFZEvents.java:198-214`).

**Trace safety:** during normal play `bgXOffset = bgYOffset = 0` and the S2 camera moves
≤16 px/frame, so `dy = clamp16(cameraY − bgYPos)` keeps `bgYPos == cameraY` — identical to
the current snap. The clamp/offset only diverge during the ending ramp. This is why the
rewrite must NOT regress the WFZ or DEZ-ending traces (see verification).

### 4.2 The event→scroll bridge (Gap B) — RECOMMENDED: inject at level load

`Sonic2WFZEvents` cannot currently reach the `bgCamera` that `SwScrlWfz` reads, and the
game-agnostic `ScrollHandlerProvider` interface must not expose S2's `BackgroundCamera`.
Three options were considered:

| Option | Approach | Verdict |
|--------|----------|---------|
| A (recommended) | Inject the `BackgroundCamera` into `Sonic2LevelEventManager`/`Sonic2WFZEvents` once at level load (where both the event manager and the S2 scroll provider are in scope). The event pushes `bgXPos`/`bgYPos` into it at the end of `syncBgDiffs()`. | Clean; matches ROM ownership (the level-event handler / `ScrollBG` owns `Camera_BG_pos`); no per-frame lookup or cast. |
| B | Per-frame: `Sonic2WFZEvents.update()` reaches `bgCamera` via `GameServices.parallax()` → new `ParallaxManager.getBgCamera()` (downcast to `Sonic2ScrollHandlerProvider`). | Works but adds a per-frame lookup + an S2 downcast on a shared manager. |
| C | `SwScrlWfz` pulls the WFZ event's `bgYPos` at the start of its own update. | Rejected: `SwScrlWfz` has no event access and adding it inverts the intended data flow (event owns scroll state). |

**Ordering (verified):** `LevelFrameStep` runs `levelEvents.update()` (`LevelFrameStep.java:248`)
before the parallax/scroll pass, so a value pushed during the event update is visible to
`SwScrlWfz` the same frame. No reordering needed.

**Injection point for Option A:** the S2 scroll provider owns `bgCamera`
(`Sonic2ScrollHandlerProvider.getBgCamera()`, `:200`); `ParallaxManager` holds the single
provider instance (`ParallaxManager.java:129`) and initialises it per zone
(`initForZone`, `:147`). Wire `Sonic2LevelEventManager.setBackgroundCamera(bgCamera)` from
the S2 level-load path once per level, guarded so non-S2 / test contexts stay null-safe
(the event manager no-ops the push when the reference is null).

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
3. **Bridge test:** after an event update with a non-zero `bgYOffset`, assert the injected
   `BackgroundCamera.getBgYPos()` equals the event's `bgYPos`.
4. **Trace regression gate (release-blocking):** `TestS2WfzLevelSelectTraceReplay` and
   `TestS2DezEndingLevelSelectTraceReplay` must stay green
   (`mvn -Ptrace-replay "-Dtest=…" test`). This is the primary guard that normal-play BG
   scroll is unchanged and the ending path doesn't desync.
5. **Rewind:** a WFZ-ending rewind round-trip must restore `bgSeeded`/`bgXPos`/`bgYPos`
   (extend the existing `Sonic2WFZEvents` snapshot coverage).
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

### Task 1: Implement `ScrollBG` math + seeding in `syncBgDiffs()`

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/events/Sonic2WFZEvents.java` (`syncBgDiffs`, field `bgSeeded`, `init`, snapshot)
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2WfzBgScroll.java` (new)

**Interfaces:**
- Produces: `Sonic2WFZEvents` exposes existing `getBgXPos()`/`getBgYPos()` (add if absent, package/test-visible) returning the post-`ScrollBG` BG position; `bgYPos` chases `cameraY − bgYOffset` at ≤16/frame.

- [ ] **Step 1: Write the failing test** (offset ramp scrolls BG up; zero offset = camera-locked):
```java
@Test
void scrollBgChasesCameraMinusOffsetClampedTo16() throws Exception {
    Sonic2WFZEvents ev = new Sonic2WFZEvents(/* test ctor / seams as existing tests use */);
    ev.init(0);
    ev.setBgYOffsetForTest(0x100);            // ramp active
    // seed frame: bgYPos <- cameraY; then one step
    ev.stepBgForTest(/* cameraX */ 0x200, /* cameraY */ 0x180);
    ev.stepBgForTest(0x200, 0x180);
    // With offset 0x100, target = cameraY - 0x100; bgYPos moves down toward it at <=16/frame
    assertEquals(0x180 - 16, ev.getBgYPos());  // seeded to 0x180, then -16 toward 0x080
}
```
*(Adapt to the existing `Sonic2WFZEvents` test seams — `setBgYOffsetForTest` already exists at `:189`; add a minimal `stepBgForTest` that calls the private `syncBgDiffs` with a stubbed `camera()` or drive via `update()`.)*

- [ ] **Step 2: Run test to verify it fails** — `mvn -o "-Dtest=com.openggf.game.sonic2.TestSonic2WfzBgScroll" test` → FAIL (current snap ignores offset).
- [ ] **Step 3: Implement** — add `private boolean bgSeeded;`, reset in `init(act)`, and rewrite `syncBgDiffs()`:
```java
private void syncBgDiffs() {
    if (!bgSeeded) { bgXPos = camera().getX(); bgYPos = camera().getY(); bgSeeded = true; }
    int dx = clamp16(camera().getX() - bgXPos - bgXOffset);
    int dy = clamp16(camera().getY() - bgYPos - bgYOffset);
    bgXPosDiff = dx; bgYPosDiff = dy;
    bgXPos += dx;    bgYPos += dy;
}
private static int clamp16(int v) { return v > 16 ? 16 : (v < -16 ? -16 : v); }
```
- [ ] **Step 4:** Add `bgSeeded` to `captureSnapshot`/`restoreSnapshot` (`:198-214`).
- [ ] **Step 5: Run test to verify it passes.**
- [ ] **Step 6: Commit** (`fix(s2): WFZ ScrollBG chase for BG scroll ...`, `Changelog: updated`).

---

### Task 2: Bridge the WFZ event BG position into the scroll handler's `BackgroundCamera`

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2LevelEventManager.java` (add `setBackgroundCamera` + pass-through), `Sonic2WFZEvents.java` (push in `syncBgDiffs` tail; hold a nullable `BackgroundCamera`)
- Modify: the S2 level-load wiring that has both the event manager and `Sonic2ScrollHandlerProvider.getBgCamera()` in scope (candidate: `ParallaxManager.initForZone` S2 path, or `Sonic2`/`LevelManager` load). Confirm exact site during Task 2.
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2WfzBgScroll.java` (extend)

**Interfaces:**
- Consumes: `Sonic2ScrollHandlerProvider.getBgCamera()` (`:200`), `Sonic2WFZEvents.getBgXPos()/getBgYPos()`.
- Produces: `Sonic2WFZEvents#setBackgroundCamera(BackgroundCamera)` — null-safe; when set, `syncBgDiffs()` writes `bgCamera.setBgXPos(bgXPos)` / `setBgYPos(bgYPos)` after the chase.

- [ ] **Step 1: Write the failing test:**
```java
@Test
void eventPushesBgPositionIntoBackgroundCamera() throws Exception {
    BackgroundCamera bg = new BackgroundCamera();
    Sonic2WFZEvents ev = /* construct */;
    ev.init(0);
    ev.setBackgroundCamera(bg);
    ev.setBgYOffsetForTest(0x100);
    ev.stepBgForTest(0x200, 0x180);
    ev.stepBgForTest(0x200, 0x180);
    assertEquals(ev.getBgYPos(), bg.getBgYPos());
}
```
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** the nullable `BackgroundCamera` field + push in `syncBgDiffs()` tail; add `Sonic2LevelEventManager.setBackgroundCamera(...)` delegating to the WFZ events; wire it from the confirmed level-load site (guarded for null / non-S2).
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** (`Changelog: updated`).

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
- Spec coverage: Gap A → Task 1; Gap B → Task 2; star-tile risk → Task 0 (gate); trace safety → Task 3.
- Open item to confirm during Task 2: the exact S2 level-load site with both the event
  manager and the scroll provider's `bgCamera` in scope (candidates listed).
- Biggest risk remains Task 0 (star tiles). If it fails, this spec's scope does not deliver
  the visual and a follow-up BG-layout spec is required.
