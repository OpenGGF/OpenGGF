# Test failure triage — 2026-11-02

The initial source base was `92dd2135fb9ac376644e8001c268a21d1c6c2cea`.
Two completed CI runs reported the same seven failed cases. Local Surefire XML
also contained 69 failed cases across 33 class reports, dated September–October;
those files are historical observations, not a current suite result.

## Bounded repairs

- The playback pause wiring assertion searched for code that moved to
  `GameLoopPauseInput.nativeStartEdge`. Follow that owner and retain the existing
  live-versus-recorded pause behavior checks.
- The overlay clipboard guard assumed P2 Start was still Right Ctrl. Exercise
  both Backspace and legacy Right Ctrl as real configured P2 Start bindings;
  preserve the non-copying P toggle in both cases.
- Enter became both the editor's explicit Descend key and logical P1 Start.
  Mode cycling ran first and prevented descent. Give the explicit Enter command
  priority while preserving O and logical-only Start mode cycling.
- Declare `AizDrawBridgeObjectInstance$BridgeExplosion` in the isolated rewind
  inventory. Declare `MhzEndBossExplosionController` against its existing parent
  recreation/retirement graph test. The fresh base contained both undeclared
  classes; the earlier CI run only contained the bridge puff.
- The CPZ hurt/landing fixture must open its own Sonic 2 gameplay session.
  It passed after another class but errored in a fresh JVM because landing calls
  `GameServices.gameState()`.
- Isolate the title animation fixtures from remembered game selection with their
  temporary configuration directory. Changing to a genuinely different selected
  game still resets preview animation.

The CPZ repair passed all eight cases in a fresh JVM. The isolated title
fixtures passed all 14 cases; the final selection including incoming FBZ
overlay checks passed 70 cases, with no skips.

## Historical entries removed from the active failure list

The fresh historical selection ran 118 cases with no skips. Only the title
selection assertion failed. The checked title-input ownership, layout mutation,
pattern-window mock rules, architecture permission count, HCZ fan rewind policy,
competition presentation/host, audio CLI and ordinary title command cases passed.
The CPZ case additionally required isolated reproduction, as described above.

The old `TestScratchNumericEditorFreeze` was a temporary probe with no remaining
source class. Current `TestTextInputSession` and `TestMenuTextEditor` checks pass:
empty native preedit callbacks must leave ordinary editing keys visible; actual
composition and cancellation still suppress their command edges. Do not restore
the obsolete probe's expectation that an empty callback freezes ordinary typing.

The Sonic/Tails slots bonus replay also passes: 5,259 gameplay comparisons,
5,401 planned rows, comparison gate reached, zero divergences and no skipped test.

## Consolidated remaining inventory

Severity describes consequence if the failure is current; complexity describes
an estimated repair campaign, not a measured implementation duration. Historical
entries below require reproduction before being called current defects.

| Remaining group | Evidence/status | Severity | Fix complexity |
| --- | --- | --- | --- |
| KiS2 EHZ1 (`TestKis2Ehz1TraceReplay`) | Delivered replay advances93→38 errors; animation/cursor/X history now match. Remaining Y slots26–63 expect$0293 versus$0294. Explicit entry provenance/reanchor repair is assigned to the inlet lane. | High for parity qualification | Large: native bootstrap/history evidence |
| Knuckles LBZ2/Big Arm (`TestS3kKnucklesLbz2BigArmTraceReplay`) | Delivered replay advances249→232 after camera admission and EggRobo-head submission. Rings55/0 and movement945 remain. Queue frontier1454 expects$376876; the proved Big Arm INIT omission is repaired locally but leaves every replay record unchanged. | High for parity qualification | Large: progression, remaining producers and movement |
| S3K CNZ (`TestS3kCnzTraceReplay`) | The independent pinned-a8 survey reproduces 3,112 errors, first row17322 `camera_x`; active solid-order effort owns this frontier. No fleet rerun. | High | Large: current camera/slot causes require attribution |
| Whole-run chains and S2 halfpipe return | Full halfpipe return is freshly red on two timestamp axes, uniformly65 rows early; represented gameplay/art comparisons pass. KiS2 chain freshly reproduces its terminal boundary at65856; the glide repair advances CPZ1 from35,443 to23,755 errors, frontier2051→3490. Three other listed classes are unmeasured by this fleet. | High | Large: production handoffs, progression and timing |
| S3K cold routes/campaigns | MHZ Knuckles and DEZ solo Sonic deaths freshly reproduced. Nine other listed classes are unmeasured. FBZ’s current short witness passes and its removed historical method is not a current failure. | High | Large: route-specific reproduction and ROM-backed diagnosis |

The two fresh red replays reached their prescribed comparison gates and skipped
no tests. LBZ retains its carried-rings first assertion; KiS2 now reaches only the remaining Y-slot initialization mismatch. Neither repair may
hydrate gameplay from expected trace rows or make an assertion weaker. See the
[bootstrap boundary](../../status/known-discrepancies.md#s3k-complete-run-segment-start-position-bootstrap-debt).

Whole-run/return classes:

- `TestS1CompleteEmeraldRunChain`
- `TestS2CompleteEmeraldRunChain`
- `TestS2EhzHalfpipeRoundTripChain`
- `TestKis2CompleteEmeraldRunChain`
- `TestS3kKnucklesSuperEmeraldRunChain`

Cold-route/campaign classes (11 remaining identities; only MHZ and DEZ solo
representatives reproduced in this fleet):

- `TestSozColdAct2Capture`
- `TestDezColdRouteCapture`
- `TestDezIncomingFinalRouteCapture`
- `TestDezSoloActTwoColdRouteCapture`
- `TestDezSoloColdRouteCapture`
- `TestLrzKnucklesColdRouteCapture`
- `TestMhzKnucklesColdRouteCapture`
- `TestS3kHpzJoinedColdRouteCapture`
- `TestS3kLrzHpzSszColdCampaign`
- `TestSozColdRouteCapture`
- `TestSszWidePairedColdRouteCapture`

## Validation scope

Use the queued Maven wrapper with Java 25, `-Dmse=off`, explicit
`-Dopenggf.headless.gl=surfaceless-egl`, and unset `DISPLAY`/`WAYLAND_DISPLAY`.
ROM checks used absolute original paths whose CRC32/SHA-1 identities match the
repository's S1 REV01, S2 REV01 and S3&K requirements. Tool preflight passed.

Completed repair checks: pause wiring/behavior 10 cases, overlay 15,
editor 96, rewind inventory/round-trip/graph evidence 1,355; all passed without
skips. The rewind ratchet executed 1,090 isolated identities and declared 250
graph identities, with zero unavailable ROM prerequisites. Graph dispositions
are named evidence, not extra isolated executions.

The repair commands used `python3 tools/testing/maven_queue.py -Dmse=off`
with the environment/ROM flags above, `-Dtest=` set to the following exact
selectors, and `test -B`:

- Pause: `TestGameLoop#playbackStartInputFeedsNativePauseEdge,TestRecordedInputHostPause`.
- Overlay: `TestDebugOverlayManagerReset` (in the same invocation as the still-red
  editor and inventory baseline selectors; its own 15 cases passed).
- Editor: `TestLevelEditorController,TestEditorCommands,TestEditorCanvasInput,TestEditorInputHandlerFrontEnd,TestEditorExportInput,TestGameLoopEditorPlaytestShortcuts`.
- Rewind: `TestEveryObjectRewindRoundTrip,TestRemainingRewindTailInventory,TestRewindHarnessCoverageRatchet,TestRewindRoundTripHarnessCoverage,TestParentDependentGraphCoverageGuard,TestAiz2SelfContainedTransientRewind,TestMhzEndBossExplosionController`.
- Isolated CPZ: `TestCPZSpinTubeObjectInstance`.
- Final title/FBZ: `TestMasterTitleScreenLayout,TestFbzMinibossArtShape,TestFbzMinibossChildren`.

The short trace command used `-Ptrace-replay`, one reused fork, and exact selectors
`TestKis2Ehz1TraceReplay,TestS3kKnucklesLbz2BigArmTraceReplay,TestS3kSlotsBonusTraceReplay$SonicAndTails$Segment1`.
It completed with three cases, two failures and zero skips; it is not a green
trace-suite result.

The change-based plan selects all 4,395 smoke classes because the assertion is in
`TestGameLoop`. Focused verification replaces that disproportionate selection:
the only runtime edit is one editor input arbitration condition, exercised by
six editor classes; other repairs concern fixtures and coverage declarations.
No full-suite or exhaustive trace/route campaign was run or claimed.

During delivery, the worktree incorporated upstream FBZ warning-eye rendering,
read-only Maven queue history charts, and the sanctuary foreground correction
through `a4e7a200d`. These did not change the repaired Java/rewind-inventory
files. The final title/FBZ selection ran on `177455610` plus the repairs; the
subsequent sanctuary-only event branch is outside those exercised paths.
The queue history Python selection passed 32 cases. The combined updated Java
source is also compiled before integration; this build is not additional test
coverage. Changelog entries from upstream and this repair are retained together.


## Agent fleet integration follow-up

The fleet is scoped to the original inventory, now19 remaining class identities;
it is not the broader survey's complete inventory. The user has reserved priorities
and policy decisions for additional survey hypotheses. No new work is started from
that list, and carried-run-state/trace-input authority is unchanged.

Worker commits `2832c01cf` (glide animation) and `c839a6bde` (boss art admission)
are preserved in lead merge `91ea642e5`, based on updated `develop` at
`a8f3f8647`. Lead review added a ready-construction-owner camera-exit regression:
it failed before the additional service gate and passes after recognizing the
existing ordinal as ownership. New submissions still require native camera range.

Combined validation completed324 cases:322 functional passes, two known replay
failures, zero errors/skips. Both replay gates complete; KiS2 is91 bootstrap/zero
animation errors over1,817 rows, LBZ239 errors (232 physics/seven animation) over
6,309 gameplay comparisons /6,444 planned rows. The LBZ nonqueue/timing errors
are identical to the pinned survey baseline. Its active-source mismatch coverage
shrinks6,248→13 frames, despite splitting into more error spans. This is bounded
advancement, not a green trace suite. Nine advertised auxiliary comparators remain
unimplemented. The exact combined selector, pinned ROM citations and coverage
limits are in the [frontier entry](../../status/trace-frontier-log.md#2026-11-02--kis2-glide-landing-and-lbz2-camera-owned-art).

The historical worker ran four exact methods at unchanged `2557cf5b0` with no
skips: current FBZ starpost5 passes; MHZ dies at input26853; DEZ solo Sonic dies
at19924 (historical14721); full S2 halfpipe returns fail only timestamp axes.
The DEZ difference is not a fleet repair: that worker made no changes. Death
observations do not identify the damaging contact or prove a ROM discrepancy.
The halfpipe's represented dynamic-art reports pass5,733 and6,381 comparisons,
while eight timestamps per return are uniformly65 rows early. No offset is fitted.

KiS2 history/prelude ownership and the LBZ EggRobo-head producer remain in their
already-assigned implementation slices. CNZ stays with the existing solid-order
effort. Historical row-stamping and newly surveyed hypotheses receive no new
assignment while user priorities are pending. The removed FBZ full-route method
is a coverage obligation, not a currently failing test; its short replacement
pass does not certify the full route.


## Solo history follow-up

Worker `6d8cab5e9`, preserved in merge `6ec3de1a4`, restores leader-history
recording on existing solo S2 title-card passes. KiS2 EHZ1 advances91→38 errors,
all in untouched Y slots26–63 ($0293/$0294), with1,817 rows compared and zero
gameplay/animation errors. Cross-entry integration completes116 cases:114 passes,
known KiS2/LBZ replay failures, zero errors/skips. S1 GHZ1 and ordinary S2 EHZ1
remain green; LBZ stays239 errors. The exact selection and bounded-validation
rationale are in the [frontier entry](../../status/trace-frontier-log.md#2026-11-02--solo-s2-title-card-leader-history-passes).

The remaining history defect involves initialization provenance at both fixture
construction and sidekick reanchoring. Its edits are handed to the survey lead's
shared inlet lane to avoid overlap. The KiS2 worker instead owns native glide
top-boundary speed halving; LBZ owns the missing EggRobo-head art producer.
These newer assignments supersede the earlier hold on those survey hypotheses.
Carried-run-state policy and unrelated survey work remain outside this repair.


The composed history/EggRobo producer candidate `578f985cc`, including upstream
music changes through `193e1ff58`, completes179 cases:177 passes, known KiS2/LBZ
replay failures, zero errors/skips. LBZ advances239→232 by removing exactly seven
row858–859 records; its complete error profile matches the worker candidate.
Next producer frontier is1454 (Big Arm archive$376874). KiS2 remains38 untouched
Y slots, handed to the inlet lane under the accepted merge-order boundary.
These two test classes remain red; this delivery does not claim trace-suite parity.


## Glide boundary and Big Arm INIT follow-up

Glide worker `491be466c` is preserved in integration merge `1f328a17d`. Native
signed-word boundary and arithmetic shifts pass both-rule adversarial controls;
composed validation completes275 cases:273 passes, known chain/EHZ1 failures,
zero errors/skips. CPZ1 compares5,574 rows and retains the worker's23,755 errors,
with first non-camera mismatch3490 Y speed. The chain still stops at65856; six
dynamic-art gaps remain. This repair does not qualify the complete run.

Big Arm worker `f0862ebaf` repairs native INIT module submission with a red/green
descriptor and rewind witness. Its55 controls pass, but every LBZ replay record
remains identical at232 errors. Native object creation/INIT dispatch at row1454
is still unattributed. The component correction must not be credited with trace
advancement. Composed boss validation at `526f8ba24` completes81 cases:80 passes, the
known LBZ failure, zero errors/skips. Its complete replay profile matches the
worker exactly. Exact commands and limits are in the frontier log. Remaining high-severity groups above are large campaigns,
with separately bounded implementation slices assigned where evidence supports
them. Carried-state policy remains a user decision.
