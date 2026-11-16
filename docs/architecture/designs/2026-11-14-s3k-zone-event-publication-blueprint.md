# S3K zone event publication cleanup — blueprint

Research baseline: `develop` at `5796486345`, inspected read-only on 2026-11-14.
The reference model is HCZ's completed publication in
[PR #271](https://github.com/OpenGGF/OpenGGF/pull/271), which is not yet on
`develop`. **Observed** means inspected source at the baseline, not a test run.
Severity ratings are survey hypotheses: each zone wave re-reads its owners and
the disassembly before changing anything.

Scope is Sonic 3 & Knuckles zone events, scroll handlers and zone runtime state.
S1, S2, shared object/rewind code and Sonic CD (on `next`) are later programmes
(see [future scope](#future-scope)).

## Why

Gameplay owners in several S3K zones advance or store event state in more than
one place: the events class, zone runtime state, objects and the scroll handler
(renderer). Shake values pass through "pending" fields consumed later in the
frame, and some of that state is missing from rewind. This makes behaviour hard
to follow, puts frame order in distant code and lets rewind restore an
inconsistent world.

HCZ was the pilot. Its cleanup worked, but the process around it was heavy: a
6,600-line audit, many review rounds and a PR that had to be split. This
blueprint keeps the HCZ target and caps the process.

## Target model

The ROM keeps one set of global shake words and advances them in one routine.
`ShakeScreen_Setup` (sonic3k.asm:104234-104255) copies `Screen_shake_offset` to
`Screen_shake_last_offset`. A positive (timed) `Screen_shake_flag` counts
down through `ScreenShakeArray` (sonic3k.asm:104277-104279); a negative
(continuous) flag indexes `ScreenShakeArray2` (sonic3k.asm:104280-104285) with
`Level_frame_counter & $3F`. It writes zero while `Player_1+routine >= 6`.

Each zone wave moves its zone to this shape:

1. **One producer.** The zone events class owns its completed publication: the
   foreground/background scroll words, collision differences, accumulators,
   redraw continuation and the three shake words. It runs at the ROM's position
   in the frame, after objects and camera for ordinary screen events.
2. **Readers use the preceding publication.** Players and objects read the
   values published last frame. Objects that start a shake write the ROM flag
   through one zone-owned request method; they never write offsets or phases.
3. **The scroll handler only projects.** `SwScrl*` reads the publication and
   fills scroll output. It does not advance accumulators, consume pending
   values or keep state that gameplay or rewind needs.
4. **One rewind payload.** The publication is captured and restored as one
   validated unit. It rejects malformed or out-of-range values before mutating
   the manager. A scroll-handler rebuild after restore consumes no setup, redraw
   row or one-shot shake.
5. **No pending fallbacks.** A value is either part of the published state or a
   ROM-named variable with its ROM lifetime. "Pending", "applied" and "next"
   copies exist only if the ROM has them.

`S3kScreenShake` (`game/sonic3k/runtime/S3kScreenShake.java`) already models
the shake words, their setup and their rewind capture for HPZ, LRZ, SSZ and
the DEZ final arena. It also keeps the ROM detail that a timed countdown above
$14 reads on into `ScreenShakeArray2`, which each zone-local table copy must be
checked for. The first wave
decides whether it becomes the shared owner of the three words for every zone,
including HCZ after #271 merges. The recommendation is yes: one ROM routine and
one global set of words should have one owner, with zones supplying only their
flag writes.

## Observed starting point

Frame order in `LevelFrameStep` is pre-physics events, players and objects,
after-object/before-camera events, camera, then `Sonic3kLevelEventManager`'s
post-camera `onUpdate` and parallax. Rewind captures most events classes
through `ZoneEventSchemaSidecar`, some zones through `ZoneRuntimeState
.captureBytes`, and only HCZ, MHZ, LBZ and Slots scroll handlers through
`captureRewindState`.

| Zone | Survey finding | Severity |
| --- | --- | --- |
| MGZ | Shake is produced by the events class, `MgzZoneRuntimeState` and three objects (`MGZTriggerPlatformObjectInstance`, `TunnelbotBadnikInstance`, `MgzMinibossInstance`, which samples its own continuous table). Both the runtime state and `SwScrlMgz` keep pending/next/published shake words; `SwScrlMgz` also latches background-rise state. Neither the runtime state nor `SwScrlMgz` is captured by rewind. | High |
| LBZ | `LbzZoneRuntimeState` keeps pending, countdown, prepared and applied shake words. `SwScrlLbz` consumes the pending value and computes continuous shake itself; the shake word is outside its rewind state, with a comment working around that. The events class and LBZ2 objects also read and write the game-wide shake flag. | High |
| FBZ | One validated runtime payload already covers shake, accumulator and collision differences. Objects write shake offset and phase directly, and `SwScrlFbz` advances the HScroll accumulator by sampling it. `update` re-runs act initialisation every frame as an idempotent fallback. | Medium |
| ICZ | Shake lives in the events class with its own tables. ICZ and `IczSegmentColumnObjectInstance` also set the shared shake-active flag. `SwScrlIcz` advances the background-loop accumulator and indoor state without rewind capture. | Medium |
| MHZ | The events class owns flag, offset and last offset, but the only writer of its flag found is a test setter; `MhzEndBossInstance` writes the shared flag instead. Check this against the ROM first: it may be a missing shake, not just tidiness. `SwScrlMhz` advances a loop accumulator that is captured. | Medium |
| AIZ | Shake is owned by the events class with duplicated tables. Continuous mode depends on a `WaterSystem` timer that WaterSystem itself decrements. `SwScrlAiz` anchors its wave frame on first call and has no rewind capture. | Medium |
| CNZ | Shake is owned and ticked by the events class, with a separate applied copy. `SwScrlCnz` reads shake but also publishes deformation words to runtime state. A boss writes scroll offset/velocity through the manager, and act-2 size accumulators live in the manager. | Low–Medium |
| LRZ, SOZ, SSZ, DEZ | Shake is in runtime state (`S3kScreenShake` or `SozEventState`), but scroll publication is not uniformly read-only: LRZ writes deformation words and SSZ advances background events. SSZ has load-time screen init and an update fallback, with no pre-physics init fallback. | Low |

**Low-zone follow-up candidates (verified 2026-11-16):** the
[low-zone audit](../audits/2026-11-15-s3k-low-zone-event-publication.md)
records these retained contracts; no code change for them belongs in PR #334.

- `SwScrlSsz.update` runs SSZ1 background setup/events and
  `SszLaunchBackground.update`; its once-per-frame guard is only the captured
  `backgroundScrollFrame`, not a separate event-publication owner.
- `SwScrlCnz` (through `CnzZoneRuntimeState.publishDeformOutputs`) and
  `SwScrlLrz` write runtime state during the normal scroll pass; CNZ also
  retains an applied shake copy.
- `SozZoneRuntimeState.restoreBytes` silently zero-fills missing trailing
  sections of a short payload instead of rejecting the whole payload.
- SSZ has no pre-physics init fallback; initial-object preparation owns screen
  init, and the update path retains a fallback.

Shared signals: eight zones carry their own copies of the ROM shake tables
instead of using `S3kScreenShake`. Writers of the game-wide shake-active
boolean (`GameStateManager.setScreenShakeActive`, also used by S2's HTZ, MCZ and
ARZ) and of the manager's FBZ-routed `setScreenShakeActive` are mixed across
ICZ, MGZ, MHZ, LBZ and FBZ owners. Do not remove the game-wide flag in this
programme; each wave only stops S3K writes it proves have no S3K reader.

## Waves and order

Order follows the playable route (AIZ → HCZ stays stable) and severity. One
zone per PR, each targeting `develop` and stacked only when a wave depends on a
previous one.

0. **Shared owner (after #271 merges).** Decide the `S3kScreenShake` question
   above, make it the owner of the three shake words and move HCZ onto it.
   Stop when HCZ's existing witnesses pass unchanged.
1. **MGZ** — next on the route and the worst split. Includes the miniboss's
   private table.
2. **ICZ then LBZ** — ICZ's shake-active writes and LBZ's reads interact, so
   settle the flag's S3K readers in ICZ first.
3. **MHZ, then FBZ** — MHZ starts with the ROM check on its flag writer.
4. **AIZ** — highest stability risk; deliberately late so the recipe is proven.
   Its checks must include `TestS3kAiz1SkipHeadless`, `TestS3kAiz1RouteRewind`,
   `TestAiz2ForestRingCameraGeneration` and a capture of the AIZ2 forest loop
   (`aiz_completerun`), where screen-event ordering has regressed before.
5. **CNZ, LRZ, SOZ, SSZ, DEZ** — survey only. Adopt the shared owner where it
   deletes code; open a PR only for a demonstrated defect.

## Per-zone recipe

1. **Read first.** Map every writer and reader of the zone's shake words, scroll
   accumulators and collision differences, with ROM labels and pinned lines.
   Record which values the ROM really has.
2. **Red witness.** Write a test that fails against the current code for a
   real consequence: a reader seeing the wrong frame's value, a rewind restore
   that diverges, or a renderer rebuild that consumes state. Use the zone's real
   load and real players, as HCZ's
   `TestS3kHczScreenEventConsumerPublication` does. A tautological witness does
   not count.
3. **Move to one producer.** Apply the target model. Keep the ROM's
   `FixBugs = 0` behaviour and comment any branch that matters.
4. **One payload.** Extend the zone's rewind capture to the whole publication,
   with malformed-input rejection tests. Same-build only; no migration for
   unreleased formats.
5. **Verify.** The zone's focused tests (below), its trace replays, the four S3K
   guard tests and a change-based smoke run at delivery only. Trace data stays
   comparison-only.

**Stop** when the red witness passes, the zone's trace replays show no new or
worse mismatches against the baseline, and the scroll handler holds no gameplay
state. Do not extend a wave into adjacent cleanup it did not plan.

## Process limits

- One PR per zone, one independent review of the frozen diff, one change-based
  smoke run before delivery. Rerun only after edits or failures.
- One dated audit section per zone, capped at about 150 lines: owners found,
  the red witness, the change, the checks and their limits. Raw logs stay under
  `target/`.
- A wave that finds the ROM disagrees with the survey records that and replans;
  it does not force the target model onto a different ROM structure.
- Record time as queue, execution, user wait and unknown separately.

## Focused tests per zone

| Zone | Existing focused classes |
| --- | --- |
| MGZ | `SwScrlMgzTest`, `TestSonic3kMgz2QuakeEvents`, `TestSonic3kMgz2BgRiseEvents`, `TestSonic3kMgz2CollapseEvents`, `TestSonic3kMgz2EndBossEvents` |
| ICZ | `SwScrlIczTest`, `TestSonic3kIczRewindRoundTrip` |
| LBZ | `SwScrlLbzTest`, `TestSonic3kLbzRewindRoundTrip`, `TestLbzLaunchRuntimeState` |
| MHZ | `TestSonic3kMHZEvents`, `SwScrlMhzTest`, `TestMhzRewindFidelitySweep` |
| FBZ | `TestFbzEventsAct1`, `TestFbzEventsAct2`, `TestFbzZoneRuntimeState`, `TestFbzEventRewindRoundTrip`, `TestFbzFramePhaseOrdering` |
| AIZ | `TestSonic3kAIZEvents`, `SwScrlAizTest`, `TestS3kAizEventWriteSupport`, `TestS3kAiz1RouteRewind`, `TestS3kAiz1SkipHeadless` |
| CNZ | `SwScrlCnzTest`, `TestSwScrlCnz2P`, `TestCnzZoneRuntimeState`, `TestS3kCnzParallaxRegisteredRewind` |
| Shared | `TestS3kScreenEventCameraCopies`, `TestScreenShakeTimerSlotObjectInstance`, `TestS3kHczMovingWallShakeRewind` |

Every surveyed zone has at least one trace replay under
`src/test/java/com/openggf/tests/trace/s3k/`. Select them by class name and
compare reports against a baseline run on the same commit; see the
[trace replay guide](../../guide/contributing/trace-replay.md).

## Future scope

- **S1 and S2 zones.** S2's HTZ, MCZ and ARZ read the game-wide shake-active
  flag; that flag's cleanup belongs there.
- **Shared object and rewind code.** Solid-contact expiry ownership and the
  dense rewind recreation path in `ObjectManager`, the two lowest-scoring paths
  in the code-climb audit (PR #271).
- **Sonic CD on `next`.** Apply the same model once its zone events stabilise.

Each needs its own survey and blueprint section; this document grants no work
outside S3K.
