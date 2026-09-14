# AIZ act 1 route-controller pilot: measured result

Second-zone pilot of the live-state route-controller technique
([design](../research/2026-09-13-live-state-route-controllers.md)), on the
shared `com.openggf.tests.route` primitives. Branch
`feature/ai-aiz1-route-pilot`; test `TestS3kAiz1RoutePilot`
(opt-in). Native Sonic+Tails, 320px, engine at develop `923f14188`.

Run it as (the repository's default Maven silent extension swallows CLI
properties without `-Dmse=off`):

```bash
mvn -Dmse=off -Dopenggf.aiz1.pilot=true "-Dtest=TestS3kAiz1RoutePilot" \
  "-Ds3k.rom.path=/abs/path/s3k.gen" test
```

## Result (pilot commit after the 2026-09-13 review; supersedes `1dceb0fd4`)

**AIZ act 1 completes to the act-2 reload on the recorded program alone, with
no per-hazard gates.** The program is the fixture's own BK2 rows from the
first level frame to the recorded act change, derived at test time by
`InputProgram.fromRecording` (no hand transcription); the only live gates
are the engine's own intro boundary and the reload itself.

| Measure | Value |
|---|---|
| Recorder pre-level prefix (`preLevelFrameCountForTraceReplay`) | 289 rows |
| Engine `Level_started_flag` set (Knuckles cutscene exit handoff) | frame 1097 = row 1386 |
| Recording's first player-driven row | 1428 |
| Recorded act-2 reload row (`zone_act_state` actual_act=1) | 5496 |
| Engine act-2 reload | frame 5174 = row 5463 (33-row lead) |
| Wall time | ~3 s |

The 33-row lead is the difference between the engine's live load-time
simulation and the recorded lag frames over one act; the test reports it
and does not assert on it.

## What the pilot proves

- **The primitives generalise off FBZ.** `InputProgram` (now with a BK2
  encoder and a row cursor), `RecentFrameLog` and `SidekickAudit` drove the
  AIZ1 controller unchanged.
- **A live gate can own an S3K event boundary.** The intro handover is
  asserted against `Camera.isLevelStarted()`, the engine's model of the ROM's
  `Level_started_flag` (cleared at intro bootstrap, set by
  `CutsceneKnucklesAiz1Instance.completeIntroExitHandoff`), rather than a
  proxy assembled from control-lock flags.
- **Row-to-frame alignment is the whole intro problem.** The sanctioned trace
  replay never ticks the engine for the recorder's 289 pre-level rows
  (`TraceReplayBootstrap.phaseForReplay` returns `VBLANK_ONLY` for them), so
  engine frame 0 is recorded row 289. A route program taken from such a
  fixture must skip those rows and then play row `r` on frame `r - 289`;
  nothing else needs to wait.

## Rejected: per-hazard ledge gating (`1dceb0fd4`), killed by measurement

The first pilot commit waited for a live handover (object control seen and
released, pad lock cleared) and then resumed the program at the recording's
first player-driven run (row 1428) immediately, on engine frame 1097. That is
42 frames earlier than the recording's own timing (row 1428 is frame 1139
after the prefix), so every global-oscillator object from the `$1870`
`Obj_FloatingPlatform` onward was met 42 frames out of phase. The commit
answered that with `LEDGE_CLIMB`/`LEDGE_RUNUP`/`LEDGE_JUMP`/`LEDGE_RUN_OFF`
stages and a fitted `$460` drop-speed regulator, and its validation record
concluded that AIZ1 was "a dense, essentially unbroken chain" of
phase-dependent hazards needing a gate each and an engine accessor for the
vine. The cross-review of that commit (ten findings, nine confirmed) also
found the stages themselves defective: a cached `ObjectManager` that the
act-2 reload replaces, a loop exit that misattributed failures, a jump latch
set without confirming take-off, no recovery in the climb stage, and an
opt-in command that the silent extension swallows.

Kill evidence (scratch probes on `923f14188`, not committed):

- The sanctioned trace-replay boot with the recorded drive reaches the
  reload at row 5496, as the passing AIZ trace chain implies.
- The pilot's plain fixture boot with live timing, driving the BK2 masks
  from row 289 with no gates at all, reaches the reload at row 5462 and
  tracks the recording within about 16 px for the whole act (first >4 px
  divergence at row 719, inside the auto-run intro).

The hazard-chain conclusion, the vine-accessor request and the per-hazard
cost model in the previous version of this record were artefacts of the
42-frame early resume and are withdrawn. No production accessor is needed.

## Recommendation

- Promote the pilot from opt-in into the AIZ per-act matrix as the native
  320 Sonic+Tails row, then add width and donor rows; those are where live
  gates are expected to earn their keep, as in FBZ2.
- Record the row-to-frame rule in the design's cost model: a fixture with a
  recorded pre-level prefix costs one derived offset, not a gate.
- Keep the 33-row live-versus-recorded lead as a datum for the benchmark
  application (section 7 of the design); it is the size of the load-time
  difference the mask-log replay would have to absorb.
