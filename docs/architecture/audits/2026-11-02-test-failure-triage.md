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
| KiS2 EHZ1 (`TestKis2Ehz1TraceReplay`) | Fresh completed replay: 93 errors, including 91 bootstrap errors and animation differences. First bootstrap mismatch is `player_history.pos`, expected `$0068`/slot `$19`, actual `$003F`. | High for parity qualification | Large: native bootstrap/history and animation evidence |
| Knuckles LBZ2/Big Arm (`TestS3kKnucklesLbz2BigArmTraceReplay`) | Fresh completed replay: 249 errors. Rings start at 55 versus 0; subsequent queue fingerprints also disagree. | High for parity qualification | Large: carried-in progression and queue lifecycle |
| S3K CNZ (`TestS3kCnzTraceReplay`) | Historical replay plus three slot/air-countdown/path-swap assertions. Not rerun in this task. | High | Large: object allocation/lifetime and strict trace parity |
| Whole-run chains and S2 halfpipe return | Five historical classes listed below. Not rerun in this task. | High | Large: production handoffs, progression and timing |
| S3K cold routes/campaigns | Twelve historical classes listed below. Deaths, boss completion, replay or handoff assertions; not rerun in this task. | High | Large: route-specific reproduction and ROM-backed diagnosis |

The two fresh red replays reached their prescribed comparison gates and skipped
no tests. Their first assertions match the old local reports. Neither repair may
hydrate gameplay from expected trace rows or make an assertion weaker. See the
[bootstrap boundary](../../status/known-discrepancies.md#s3k-complete-run-segment-start-position-bootstrap-debt).

Whole-run/return classes:

- `TestS1CompleteEmeraldRunChain`
- `TestS2CompleteEmeraldRunChain`
- `TestS2EhzHalfpipeRoundTripChain`
- `TestKis2CompleteEmeraldRunChain`
- `TestS3kKnucklesSuperEmeraldRunChain`

Cold-route/campaign classes:

- `TestFbzAct2RouteHeadless`
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
