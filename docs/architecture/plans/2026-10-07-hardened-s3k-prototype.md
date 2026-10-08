# MHZ1 Post Two Ambush implementation

Accepted scope is the [Hardened blueprint](../designs/2026-10-07-hardened-s3k-blueprint.md)
prototype: one local native MHZ1 encounter, solo Sonic, donor off, 320px,
no save. Original implementation base is `6d817a9d74f135714f3da59ab9aab156cc09473e`.
The later full-act, chapter, campaign and Stage Trials gates remain future work.

1. Survey the physical second post's floor, headroom, placements, rings and
   camera using production ROM decoding. Freeze an explicit bounded encounter.
2. Add transaction-owned immutable placement plans without replacing the native
   level, objects, terrain, events or PLC owners. Add explicit fresh-entry,
   no-save and native-input controller opt-ins with stock defaults.
3. Author the resident-art sentry and recreatable projectiles. Capture admitted
   simulation phase, committed aim and finite projectile state through rewind.
4. Integrate the title, lesson, entry, play, checkpoint retry, results and clean
   exit using existing engine presentation/input/audio owners.
5. Build/package the maintained JVM example; document human and agent authoring,
   valid/invalid plans, supported cells and observed evidence. Capture actual
   graphics and PCM outside the repository from committed input programs.
6. Verify focused behavior, two-cycle replay, fault containment, native checkpoint
   reload, the four mandatory S3K regressions and AIZ→HCZ safety. Review the actual
   combined selection, preflight and run required category/guard validation.
7. Independently review high-risk boundaries and the final base-to-head diff;
   fetch/assess develop and provide a clean implementation handoff. The user's
   subsequent root-owned Opus polish and separate promo stages precede feature
   push and the one requested PR. Account for children and retain the feature
   tree, branch and native histories for that integration and review.

Parallel ownership: placement child owns frozen plan registration/application;
encounter child owns geometry, gameplay objects and focused mechanics; session
child owns the three launch/input opt-ins. The lead owns presentation, integration,
authoring documentation and delivery. Each child uses a separate copy-on-write
worktree and queued focused Maven commands. Their commits and exact verification
are reconciled in the outside-repository task ledger before integration.

Final compiled composition is `cd38571ab4c9a5a98d55e2c705266e6390fa46a1`, with
published integration base `33d3976c53304dbbea1c695914ecdd7bfc64cf9d`.
Original `6d817a9d` remains an ancestor. Intent merges preserve upstream
`onNewGameFromTitle`, DelegatingGameModule forwarding, unavailable TWO_PLAYER
title branch, stock invincibility music rules and banked SMPS/scene contracts.
The ModApiVersion comment and compatibility prose retain both API intents.
The latest published successor changes one production capture helper: drawing
and `glFinish` are shared by `render` and the new discard-readback `renderFrame`.
Existing screenshot capture still calls `render`. The other changes are test
fixture ownership/cleanup and documentation; no Engine, GameLoop, gameplay,
audio, Mod API, POM, hook or selection-policy delta is introduced by this merge.

The actual composed package request selected 20 class names and produced 21 fresh
XML reports: **197 passes, zero failures/errors/skips**, exit 0, including audio
saved-duration/rest, frozen bank programs, audio producer rewind, scene music,
actual API/SDK classes, all four mandatory S3K regressions and seven packaged
prototype cases. The compiled candidate export has **20,186 lines**, SHA-256
`c694532e5905a864c830015db49e2648941fb9e27ff69805488630efadff3b20`, byte-equal
to the pin. Candidate 0.7.0 and published baselines are unchanged. The maintained
creator jar was rebuilt and SDK-validated; its 18 entries are classes and
`META-INF/openggf-mod.yaml`, with no ROM payload.

The composed normal trace-profile request ran exactly
`TestS3kAizTraceReplay#replayMatchesTrace`,
`TestS3kAizZoneSliceTraceReplay#replayMatchesTrace` and
`TestS3kHczZoneSliceTraceReplay#replayMatchesTrace`. All three full assertions
literally match the completed clean 2fc baseline: 57 differences / first frame
20302 animation 0/5; 99 / frame 25589 animation 0x13/5; 4699 / frame 9482 air 1/0.
There are no errors or skips. These are inherited failures, not passing traces.
The original baseline request 80733 ended 130 before admission; its cause remains
unknown and its validation incomplete. Historical 151-case 2fc focus and corrected
22-case API/SDK focus remain source-attributed; three nonexistent earlier selector
names provide no coverage.

Actual audiovisual evidence is outside Git at
`${OPENGGF_TASK_SCRATCH}/hardened-s3k/captures`.
The maintained controller programs drive production gameplay; no trace values
supply state or readiness. Inspected PNGs include the first animated title,
first released PLAY, committed aim, volleys, results and checkpoint reentry.

| Capture | Observed state and decoded evidence |
| --- | --- |
| `final-33d-safe-01` | Actual compiled `cd38571ab`: 1316 GPU frames / 1,052,800 stereo PCM frames; native post313, tell332, lock368, volleys380/404, clear675, post respawn840, title951, separate fresh1035. Full 60fps 640×448 movie decodes. |
| `final-33d-failure-01` | Actual compiled `cd38571ab`: 715 GPU frames / 572,000 stereo PCM frames; post279, lock337, volley349, actual zero-ring death352, post respawn531, title642. Full movie decodes. |
| `extraction-native-safe-01` | Actual compiled `c73270ded`: genuine native X11 press/release input; 1376 state rows / 1425 decoded desktop frames, post372, volleys438/462, clear735, native respawn900, title1011, separate fresh1106. Stereo48k device PCM AC RMS1194/1199 per channel. |
| `extraction-missing-rom-01` | Actual compiled `c73270ded`: 337 native rows / 361 decoded desktop frames, actual ROM-not-found0, preserved S3K selection90, repeated refusal180, responsive menu300. Engine and both recorder exits0; device PCM AC RMS1805 per channel. |

The complete final safe/fatal CSV and WAV bytes equal their `c73270ded` captures,
not merely selected rows or audio event counts. The `33d3976c` helper delta is
exercised by new real S3K rendering tests and final GPU walkthroughs. It does not
change the native `Engine.display` caller used for the source-attributed c732
window captures; those are retained with their exact source and limits.

The native captures identify an AMD Radeon RX9070XT / Mesa26.2.4 OpenGL4.6
backend, exact owned Engine PID/title/viewable geometry and focused window.
This host's explicit GLFW show call stalled; only that owned window was mapped
frameless. Child-only `ALSOFT_DRIVERS=pulse`, private `PULSE_SINK`,
`LD_PRELOAD=/usr/lib/libkeyutils.so.1`, `vblank_mode=0` and
`__GL_SYNC_TO_VBLANK=0` were recorded. Engine/recorders stopped and private sinks
unloaded. This is unmodified Engine.display at diagnostic 60Hz, not certification
of normal WM initialization, Engine.loop cadence, hardware controllers or speakers.
The GPU tool separately observes actual ROM PCM: title AC1247/1251, volley-one
1769/1806 and volley-two1743/1816 per channel, with no flat10ms volley windows.

The first native recipe began movement before native ENTRY released (PLAY285
versus logical274), so its later encounter did not clear. It is rejected as a
successful walkthrough. `native-safe.script/.bk2` adds a neutral entry margin
and completes the native path; this changes authored input, not engine readiness
or physics. Earlier D5/pre2fc media and recorder255 diagnostics remain historical.

Independent placement, session, mechanics, integration, readiness and native-card
consumption reviews found no blockers. The 67 own source/example/API deltas after
be3 are identical to the reviewed pre-be3 implementation. Child commits were
accounted before their clean merged trees/branches were removed; histories and
light results are retained outside Git. No peer branch is imported. Mutators owns
overlay/configuration/lifecycle, Multigame exclusive worker step/input/media, and
this branch bounded native placements, launch and input opt-ins.

Final combined request 66538 completed on `7bb15c49a`, against published base
`02796b4c`: 3032 selected classes / 3030 ordinary reports / 26,428 tests,
28 assertion failures, zero errors and 62 skips in 4994.61 seconds. Every full
assertion and every skip identity/first causal line matches the qualified base;
only exception prefixes and the known SSZ Tails object-blob hash are normalized.
There are no new, changed, absent or omitted failures/skips, and no ROM skips.
This is inherited-red ordinary validation, not a green whole-suite result.

The separate fresh guard JVM produced 87 reports / 678 cases, one assertion
failure, zero errors/skips in 219.58 seconds. The owned blocker is
`TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`:
LevelManager had 3183 effective source lines against its unchanged 3145 budget.
Commit `c73270ded` extracted module placement, fresh-position and reload hooks
into the package-private `ModuleLevelLoadController`, together with the adjacent
native dynamic-start resolver. The collaborator is load-owned; suppression
unwinds in finally and camera handoff is consumed before the next frame/rewind
boundary. Native checkpoint/return authority and signed fallback coordinates
retain their existing meaning. The public API and ratchet budget are unchanged.
Independent source review found no blockers. Normal queued package request 86055
then passed **158 cases in 18 fresh reports**, zero failures/errors/skips, covering
native post/death/fresh/stage-return precedence, placement fault boundaries,
packaged retry/two-cycle replay, prepared/deferred loading, stock manager state
and all four mandatory S3K regressions. Fresh guard request 85829 ended 130 during
execution: 48 partial reports/239 cases are incomplete, cause unknown. After
exact owned-process reconciliation established no surviving execution, one
identical normal replacement 34304 passed **87 fresh reports/678 cases**, zero
failures/errors/skips. The actual ratchet blocker is resolved at its original
budget. Original incomplete evidence remains in the light outside ledger.

The completed combined command was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 02796b4c497c3aa887f77a8d8e9697b7009d2b4c --max-minutes 150 --run
```

Java21/Lua5.4/PowerShell preflight passes against actual base `33d3976c`.
Its unchanged combined-selection rules select 3034 classes: the completed 3032
ordinary inventory plus two upstream capture/ownership test classes. Latest
published-base ordinary cost is about 109 minutes plus 3.6 minutes fresh guards;
the 150-minute execution and 10-minute no-output caps exclude queue waiting.
Timeout is incomplete. The combined diagnostics were inspected, acknowledged
and deleted; full concrete failure/skip comparisons remain as light task facts.

The unchanged-algorithm facade extraction was verified by the 158 focused cases
and all 678 fresh guards; it does not warrant repeating the completed ordinary
lane. The localized 33d capture-helper/test-fixture successor has a bounded
production path and no unresolved gameplay/public-contract/timing change.
Normal request 69268 rebuilt 3705 production and 3600 test sources, then passed
**74 cases in 13 fresh reports**, zero failures/errors/skips, and packaged the
engine, SDK and Javadoc. Its exact selectors cover the five new real S3K
pixel/state/title/results parity cases, owned-mock cleanup, capture smoke,
skipped titles/arguments, actual API/SDK classes and packaged prototype launch.
The final compiled 20,186-line export remains byte-equal to the candidate pin;
creator packaging/SDK validation and actual final GPU/PCM walkthroughs passed.
This is composed focused evidence after the qualified inherited-red ordinary
run, not a full 33d ordinary-suite pass or a new clean-base baseline.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -B -Dmse=off \
  "-Dsonic1.rom.path=$OPENGGF_S1_ROM" "-Dsonic2.rom.path=$OPENGGF_S2_ROM" \
  "-Ds3k.rom.path=$OPENGGF_S3K_ROM" \
  -Dtest=TestGameplayCaptureFrameRendering,TestOwnedMocks,TestGameplayCaptureSmoke,TestGameplayCaptureSkippedTitles,TestGameplayCaptureToolArgs,TestModApiPinPolicy,TestModApiSignatureSurface,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModApiJavadocTool,TestGgfModCliCommands,TestModApiSdkPackager,TestHardenedPrototype \
  package
```

The variables denote existing user ROMs discovered by identity, not newly
created links or renamed copies. The exact absolute paths and all commands,
source IDs, results/skips and incomplete invocation history are in the outside
ledger. No own build, capture, recorder or private sink remains running.

The user subsequently requested root-owned Claude Opus 5.5 hands-on polish, then a
separate promo-video task. After these implementation gates, the lead supplies a
clean exact-source handoff outside Git and holds feature push/PR for polish
integration. Native semantics and prototype scope remain fixed; full-act,
chapter/campaign and Stage Trials gates remain future work.
