# Sonic 2 Special Stage Rewind Design

## Goal

Add held-key live rewind inside Sonic 2 special stages while keeping the existing
mode-boundary rule: entering or leaving a special stage starts a fresh rewind
timeline. This spec covers Sonic 2 only. Sonic 1 support is provided by the
existing special-stage rewind branch, and Sonic 3 and Knuckles special stages
remain non-rewindable until their own design lands.

## Existing Foundation

The shared framework from the Sonic 1 special-stage rewind work is the base:

- `SpecialStageProvider.supportsRewind()` is the semantic capability gate.
- `SpecialStageProvider.rewindAdapter()` supplies one provider-owned adapter
  under `SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY`.
- `SpecialStageStepper` replays recorded `Bk2FrameInput` rows by installing a
  logical input override, mapping it through `SpecialStageInputMapper`, calling
  `handleInput`, `handlePlayer2Input`, and `update` on the active provider.
- `GameLoop` registers the provider-owned adapter before a rewind-capable
  special-stage mode-entry boundary, records frames after live `update`, and
  clears the special-stage session on results or level return boundaries.
- Live-only debug and tuning shortcuts sever the current special-stage rewind
  session and suppress same-frame recording.

The Sonic 2 work should not add a new shared rewind controller or a new
GameLoop mode. It should plug into this provider/adapter path.

## Architecture

Sonic 2 becomes rewind-capable only after the entire
`Sonic2SpecialStageManager` runtime graph can capture and restore deterministic
simulation state. `Sonic2SpecialStageProvider` then returns `true` from
`supportsRewind()` and returns a `Sonic2SpecialStageRewindAdapter` from
`rewindAdapter()`.

The adapter is game-local and delegates to package-local manager methods:

```java
final class Sonic2SpecialStageRewindAdapter
        implements RewindSnapshottable<Sonic2SpecialStageSnapshot> {
    private final Sonic2SpecialStageManager manager;

    @Override
    public String key() {
        return SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY;
    }

    @Override
    public Sonic2SpecialStageSnapshot capture() {
        return manager.captureRewindSnapshot();
    }

    @Override
    public void restore(Sonic2SpecialStageSnapshot snapshot) {
        manager.restoreRewindSnapshot(snapshot);
    }
}
```

`resetForMissingSnapshot()` should keep the default throwing behavior. The
adapter is registered only while an eligible Sonic 2 special stage is active, so
a missing snapshot means the registration lifecycle is wrong.

## Snapshot Surface

Use explicit snapshot records rather than generic reflection capture. The S2
runtime includes structural renderers, ROM data, callbacks, mutable arrays, and
polymorphic active objects. Explicit records make the capture contract reviewable
and avoid accidentally serializing GL or ROM cache state.

### Manager Snapshot

Create `Sonic2SpecialStageSnapshot` in
`com.openggf.game.sonic2.specialstage`. It should capture:

- `initialized`, `currentStage`, `resultState`, `emeraldCollected`.
- `frameCounter`, `heldButtons`, `pressedButtons`, `p2HeldButtons`,
  `p2LogicalButtons`.
- `tailsControlCounter` and a cloned `tailsCtrlRecordBuf`.
- `lastDrawingIndex`, `checkpointRainbowPaletteActive`,
  `rainbowPaletteCycleIndex`, `pendingCheckpoint`,
  `pendingCheckpointNumber`, `pendingRingRequirement`,
  `pendingRingsCollected`, `pendingFinalCheckpoint`,
  `currentRingRequirement`.
- Alignment/debug state: `spriteDebugMode`, `planeDebugMode`,
  `alignmentTestMode`, `alignmentTestSavedRainbowPalette`,
  `alignmentPendingCheckpoint`, `alignmentFrameIndex`,
  `alignmentFrameTimer`, `alignmentTrackFrameIndex`,
  `alignmentLastDecodedFrameIndex`, cloned `alignmentDecodedTrackFrame`,
  `alignmentDrawingIndex`, `alignmentTriggerOffsetFrames`,
  `alignmentRainbowSpeedScale`, `alignmentRainbowSpeedAccumulator`,
  `alignmentStepByTrackFrame`.
- Lag state: `lagCompensation`, `lagAccumulator`,
  `lagCompensationDisplayEnabled`, `diagnosticWallStartTime`,
  `diagnosticUpdateCount`, `diagnosticTrackAdvances`, `lastFrameTime`,
  `frameSampleCount`, `frameSampleSum`.
- Background/track derived state: `skydomeScrollX`, `alternateScrollBuffer`,
  `lastAlternateScrollBuffer`, `drawingIndex`, `lastAnimFrame`,
  `vScrollBG`, `hScrollDebugTotal`, `hScrollDebugFrames`,
  `lastDebugSegmentIndex`, cloned `decodedTrackFrame`,
  `lastDecodedFrameIndex`, `lastDecodedFlipped`.
- Nested snapshots for the track animator, Sonic/Tails players, intro,
  object manager, checkpoint, and any active alignment checkpoint.

Do not capture ROM/art/cache arrays such as `trackFrames`, `backgroundArt`,
`palettes`, mapping data, or renderer instances. Those remain structural for
the initialized stage. Restore should only re-prime render-facing decoded state
that is a pure cache of captured fields.

### Track Animator Snapshot

Add package-local capture/restore on `Sonic2TrackAnimator` for:

- `stageLayout` clone and `layoutLength`.
- `currentSegmentIndex`, `currentFrameInSegment`, `frameDelayCounter`,
  `currentSegmentType`, `currentSegmentFlipped`.
- `speedFactor`, `stageComplete`, `orientationFlipped`,
  `lastOrientationFrame`.

Capturing the layout clone avoids needing to re-read ROM data during restore and
preserves mock-layout tests.

### Player Snapshot

Add `Sonic2SpecialStagePlayerSnapshot` and package-local
capture/restore methods on `Sonic2SpecialStagePlayer`. Capture:

- Routine state: `routine`, `routineSecondary`.
- Position, velocity, inertia, angle, slide/hurt/DPLC counters.
- Animation fields, mapping frame, radii, priority, status/render flags, and
  `collisionProperty`.
- `globalAnimFrameTimer`.
- Control-record buffer clone, `ctrlRecordIndex`, `swapPositionsFlag`.
- A player-owned invulnerability countdown for this player.

The current S2 player uses `TimerManager` and `SSInvulnerabilityTimer` for
post-bomb invulnerability. That is not safe for special-stage rewind: the
special-stage replay stepper does not tick shared timers, and
`TimerManager.restore()` recreates generic timers without the callback to the
player. The implementation should migrate S2 special-stage invulnerability to a
player-owned countdown that is decremented from the special-stage update path and
captured in the player snapshot. This keeps the behavior inside the same runtime
graph as the rest of the special stage.

If the implementation chooses to keep `SSInvulnerabilityTimer`, restore must
remove stale timer entries and recreate exact callback timers for both players
from the captured countdown. The spec recommendation is the player-owned
countdown because it is smaller and easier to prove.

### Intro Snapshot

Add capture/restore to `Sonic2SpecialStageIntro` for:

- Current phase, phase/frame counters, banner/message positions and visibility.
- Ring requirement and letter flyout state.
- Message letters and banner letters as value snapshots, preserving list order.

Callbacks are not captured; they are structural and should remain installed by
the manager's setup path.

### Object Manager And Object Snapshots

Add `Sonic2SpecialStageObjectManagerSnapshot` and value snapshots for active
objects. Capture:

- `objectLocationData` clone, `stageOffsets` clone, `currentPosition`,
  `currentStage`, `lastProcessedSegment`.
- `ringsCollected`, `perfectRingsTotal`, `currentSpecialAct`,
  `noCheckpointFlag`, `noCheckpointMsgFlag`, `ringsToGoEnabled`,
  `emeraldSpawned`.
- Ordered active object snapshots.

Active objects are polymorphic. Each snapshot should include a type enum and the
base object state:

- Base object state: `state`, `angle`, `depthFixed`, `screenX`, `screenY`,
  `trackFloorY`, `animIndex`, `animFrame`, `animTimer`, `onScreen`,
  `highPriority`.
- Ring extra: `spinFrame`.
- Bomb extra: no extra fields beyond base unless future code adds one.
- Emerald extra: `phase`, `phaseTimer`, `bobbingOffset`, `bobbingCounter`,
  `ringRequirement`, `musicFaded`, `emeraldAwarded`.

Restore should recreate the correct concrete object type, restore base and extra
fields, and reconnect structural references such as the emerald's manager
reference. It must preserve active-object ordering because collision and render
order depend on list order.

### Checkpoint Snapshot

Add capture/restore to `Sonic2SpecialStageCheckpoint` for:

- Phase, phase timer, last result, current checkpoint, ring requirement, rings
  collected.
- Message letters, hand state, rainbow rings, pending checkpoint data, and
  `rainbowOnly`.

Callbacks are structural. Restore must not null or replace
`onCheckpointResolved` or `onMusicFadeRequested`.

### Palette And Renderer State

S2 checkpoint rainbow palette state is gameplay-visible and should restore.
Capture manager-level `checkpointRainbowPaletteActive` and
`rainbowPaletteCycleIndex`. On restore, re-apply the checkpoint palette state to
`palettes` and graphics if graphics services are available; null-guard graphics
for headless tests.

Renderer, FBO, shader, and debug text renderer instances are structural and not
captured. Restore must not allocate GL resources in headless tests.

## Debug, Tuning, And Boundary Rules

The shared GameLoop live-only boundary logic remains authoritative. These inputs
are not represented in `Bk2FrameInput` and must sever the current special-stage
rewind session before mutating state:

- Global special-stage key.
- X/Z stage or layout debug.
- Complete/fail debug keys.
- Sprite-debug and plane-debug toggles.
- Sprite-debug navigation.
- Alignment-test toggle and adjustment controls.
- F1/F6/F7 lag-compensation controls.

S2 should capture the lag-compensation numeric state because it affects whether
`update()` skips a frame. The keys that mutate it stay boundary events. After the
boundary, the next clean frame installs a fresh special-stage session with the
new lag state in frame zero.

## Audio Rules

The existing rewind controller owns audio replay suppression while held rewind
is active. S2 special-stage snapshot restore should not manually replay SFX or
music when restoring. It should restore flags that determine future live audio
behavior, for example emerald `musicFaded`, `emeraldAwarded`, manager
`resultState`, and lag/music state. If a restore re-enters a state that expects
music tempo to be active, the restore path should set the audio multiplier only
when the live manager state requires it and should be null-guarded for headless
tests.

## Testing Strategy

Headless tests should prove capture/restore correctness at component level.
End-to-end visual rewind remains a manual ROM + GL gate.

Required tests:

- `TestSonic2SpecialStageRewindCapability`
  - Assert `Sonic2SpecialStageProvider.supportsRewind()` is true after the
    adapter exists.
  - Assert the provider returns a present adapter whose key is
    `SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY`.
  - Assert Sonic 3 and Knuckles remains non-rewindable.
- `TestSonic2SpecialStagePlayerSnapshot`
  - Mutate Sonic and Tails player state including control-record buffers,
    routine states, animation fields, hurt state, and invulnerability countdown.
  - Capture, mutate, restore, assert exact value equality.
- `TestSonic2SpecialStageObjectSnapshot`
  - Cover active ring sparkle state, bomb explosion state, emerald phase/timers,
    active-object list ordering, and manager reference reattachment.
- `TestSonic2SpecialStageCheckpointSnapshot`
  - Cover rainbow/message phases, message letters, rainbow rings, hand state,
    pending result data, and callback preservation across restore.
- `TestSonic2SpecialStageRewindSnapshot`
  - Build a manager with initialized nested components or focused test hooks.
  - Mutate manager, track animator, players, intro, object manager, checkpoint,
    palette flags, lag state, and decoded track cache.
  - Capture, mutate further, restore, assert the captured state returns.
  - Assert structural fields such as renderer and callbacks remain usable and
    are not replaced with snapshot data.
- Extend `TestGameplayModeContextSpecialStageRewindAdapter`
  - Register a Sonic 2 provider and assert the generic special-stage key is
    captured.
  - Deregister and assert the key is removed.
- Keep the existing S1 tests green:
  - `TestSonic1SpecialStageRewindSnapshot`
  - `TestGameLoopSpecialStageRewindBoundary`
  - `TestGameLoopSpecialStageRewindDebugBoundary`
  - `TestLiveRewindManagerSpecialStageMode`

## Manual Acceptance Gate

With `s2.gen` available:

1. Enter a Sonic 2 special stage.
2. Hold live rewind during the intro, normal running, ring collection, bomb hit,
   post-hit invulnerability, checkpoint message, and emerald approach.
3. Verify player position, half-pipe track frame, rings, active objects,
   checkpoint UI, palette rainbow, and invulnerability flash restore cleanly.
4. Verify repeated short backward steps across a keyframe boundary are seamless.
5. Verify no double-triggered SFX or duplicated music fade/jingle occurs during
   replay or release.
6. Verify F1/F6/F7 or alignment/debug controls sever the current rewind session
   and a clean following frame starts a fresh session.
7. Verify the transition to results disables special-stage rewind.
8. Verify Sonic 1 special-stage rewind still works and Sonic 3 and Knuckles
   special stages remain inert.

## Risks

1. **Timer migration risk.** Moving S2 special-stage invulnerability out of
   `TimerManager` must preserve live gameplay behavior. Pin it with focused
   player tests before enabling provider rewind.
2. **Object list reconstruction risk.** Active object ordering and concrete type
   reconstruction are load-bearing for collision/render parity. Snapshot tests
   must assert list order and type-specific fields.
3. **Callback preservation risk.** Checkpoint and object-manager callbacks are
   structural. Restore must not replace them with null or stale snapshot data.
4. **Palette restore risk.** Checkpoint rainbow palette writes are visible. The
   restore path needs a pure, null-guarded palette re-apply helper.
5. **Lag compensation determinism.** The numeric lag factor and accumulator must
   restore together; otherwise replay may skip a different frame than live play.

## File-Touch List

Create:

- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageSnapshot.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageRewindAdapter.java`
- Focused nested snapshot records as needed in
  `com.openggf.game.sonic2.specialstage`, or package-private nested records if
  they stay readable.
- `src/test/java/com/openggf/game/sonic2/specialstage/TestSonic2SpecialStagePlayerSnapshot.java`
- `src/test/java/com/openggf/game/sonic2/specialstage/TestSonic2SpecialStageObjectSnapshot.java`
- `src/test/java/com/openggf/game/sonic2/specialstage/TestSonic2SpecialStageCheckpointSnapshot.java`
- `src/test/java/com/openggf/game/sonic2/specialstage/TestSonic2SpecialStageRewindSnapshot.java`

Modify:

- `src/main/java/com/openggf/game/sonic2/Sonic2SpecialStageProvider.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageManager.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2TrackAnimator.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStagePlayer.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageIntro.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageObject.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageRing.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageBomb.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageEmerald.java`
- `src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageCheckpoint.java`
- `src/test/java/com/openggf/game/TestSpecialStageRewindCapability.java`
- `src/test/java/com/openggf/game/session/TestGameplayModeContextSpecialStageRewindAdapter.java`

Explicitly untouched:

- Sonic 3 and Knuckles special-stage provider and manager, except tests that
  assert it remains non-rewindable.
- Shared GameLoop rewind boundary wiring, unless a test exposes an S2-specific
  bug in the existing provider-agnostic path.
- Level rewind, bonus-stage rewind, and trace replay code.
