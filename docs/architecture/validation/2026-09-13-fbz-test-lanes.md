# FBZ test lanes — 2026-09-13

## Scope and baseline

Base: `ce322996eec8aa9e6fd0e18119ebbab809da07cb` on develop.
Implementation: `.worktrees/ai-fbz-test-lanes`, `feature/ai-fbz-test-lanes`.
Task receipt: `20260913-fbz-test-lanes`, 40-minute aggregate ceiling.

Reuse the completed unchanged-base benchmark: ordinary two-worker lane 278.48 s,
19,765 tests, 12 failures, 12 errors, 13 skips; guards 182.17 s, 664 tests passing.
Combined 460.65 s. FBZ compatibility contributed 85.251 s; Act 1 26.050 s.
All eleven complete compatibility routes failed before their local probes and
checkpoint slices. Other baseline reds: descending-X placement order (expected
[448,384], actual [384,448]), eleven SampleFlappy integration cases and one
S3kModZoneLifecycle case (invalid S3K level resource profile).

## Coverage and plan

Keep all original complete route assertions, under `fbz-route` / `fbz-routes`:
five teams, four additional viewport widths, two active donors. Ordinary retains
one native Sonic/Tails complete route using the same helper. Extract the downstream
checks into fresh configuration scopes: five team authority, five team checkpoint,
five viewport checkpoint, five team local interaction, four viewport interaction,
two donor interaction cases. Preserve all thirteen synchronous reload preflights.
No engine changes or removed assertions. Act 1's 75 seeded lifecycle combinations
remain unchanged; they are already focused scenarios.

Focused checks verify the 26 extracted cases, ordinary selection, exhaustive
selection and build-policy guard. One final change-based broad run validates the
POM/selection change against the pinned base. Expected combined cost about eight
minutes; allow eighteen minutes of execution time, excluding queue waiting. Inspect exact
failures and skips, including the known native representative failure; moving
failed routes into an explicit lane is not a gameplay fix.

## Focused evidence

`mvn -Dmse=off -B -Dtest=TestFbzCompatibilityMatrix#<six extracted methods> test`
with all three SHA-1-verified absolute ROM properties: 26 passed, no skips,
9.862 s class time, 57.73 s invocation including fresh compilation.
The initial helper used Surefire's generic reports property, while this POM owns
`openggf.surefire.reports`; results were verified in the actual default report
XML and the helper corrected before subsequent runs. Zero reports in the helper's
first requested directory were not treated as passing evidence.

Ordinary focused command: `mvn -Dmse=off -B -Dtest=TestFbzCompatibilityMatrix test`
with the same ROM properties: 40 cases, 39 passed, one known failure, no skips;
16.636 s class time, 33.71 s invocation. Native representative fails at frame 31034 with
`obj74-crossing-lost-flat-control`, target $2360, player ($2315,$96c), matching
original team row 2.

Explicit command adds `-Pfbz-routes`: exactly eleven cases, eleven known failures,
no skips, 57.550 s class time, 75.41 s invocation. All original first-error frames match the baseline:
viewport rows 27356/29138/25729/25885; team rows
31003/31034/31034/31031/31026; donors 7343/29706. No route defect was fixed.
The independent review found every original assertion retained and fresh fixture
ownership intact. Missing-ROM skipping remains the shared annotation contract;
the guide explicitly requires eleven executed cases and zero skips for this lane.

Focused build-policy checks: `mvn -Dmse=off -Pguards -B
-Dtest=TestBuildToolingGuard#exhaustiveFbzRoutesHaveAnExplicitLane+ordinarySurefireShouldUseSharedAlphabeticalRunOrder+deeperAudioLanesMustBeExplicit test`:
3 passed, no skips, 18.13 s invocation. Java/Lua/PowerShell preflight passed.
The plan selects 2,511 ordinary source classes and all guards. Reuse the exact-base
completed broad benchmark and reserve the receipt's single broad run for the
integrated develop tree, rather than repeating it in both trees.

## Integrated broad validation

Command on develop `e7cc8de78` (merge of `b322cd1f4`), unchanged throughout testing:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base ce322996eec8aa9e6fd0e18119ebbab809da07cb \
  --workers 2 --max-minutes 18 --run
```

Run `20260913T110426Z-1c51d328`: ordinary 2,513 reports, 19,781 tests,
19,754 passed, 2 failures, 12 errors, 13 skips, 247.04 s. Compared with the
same-base completed benchmark, ordinary wall time improved 31.44 s (11.3%).
This is a single before/after observation, not a repeated statistical estimate.

| Ordinary class | Before seconds | After seconds |
| --- | ---: | ---: |
| FBZ compatibility | 85.251 | 23.594 |
| FBZ Act 1 | 26.050 | 27.486 |
| Rewind torture | 65.592 | 60.838 |
| Synthetic OPN2 scripts | 27.022 | 26.896 |
| S2 request-aware stream | 12.133 | 12.696 |
| Complete-run capture store | 11.808 | 11.921 |
| Launcher run branch | 10.495 | 10.368 |

FBZ compatibility class time fell 61.657 s (72.3%). Two-worker wall time
saves less because the workers overlap. The eleven exhaustive routes remain
available and still fail; fewer ordinary route failures are a selection change,
not a fix. Ordinary case count increases by 16: replace eleven route cases with
one representative and add 26 independently runnable checks.

### Failure and skip comparison

The native representative matches old `configuredTeamSurvivesSharedPlaneAndBossState(TeamCase)[2]`
at frame 31034, `obj74-crossing-lost-flat-control`, target $2360/player ($2315,$96c).
`TestObjectPlacementEncoding#commonParserPreservesDescendingFullXOrderInsideOnePlacementColumn`
still expects [448,384] but receives [384,448]. All twelve error identities below
match the baseline, including message SHA-256
`d9fd7e303e2685b9fe63d9e459146487691676d1d0e60884f027bccdda52bd46`
and deepest cause `IllegalArgumentException: invalid S3K level resource profile`:


- `TestS3kModZoneLifecycle#taggedIdentitySurvivesSaveReopenEditorAndRealBackwardSeek`
- `TestSampleFlappyIntegration#firstForwardUpdateCreatesSixIndependentEntriesAndCoversSuper32By9`
- `TestSampleFlappyIntegration#engineRestartResetsRunAndRecreatesTheSameInitialSequence`
- `TestSampleFlappyIntegration#scoreCrossingOneHundredDoesNotRunCollectibleRingBonusLogic`
- `TestSampleFlappyIntegration#pipeAndVisibleBoundsAlwaysUseCrushDeath`
- `TestSampleFlappyIntegration#initialTitleCardSeesTheResolvedS3kCustomZonePalette`
- `TestSampleFlappyIntegration#bootsVisibleNativeTailsAtFixedAnchorAndMaintainsFlight`
- `TestSampleFlappyIntegration#rewindRestoresAndResimulatesScoreAndLiveRecycleExactly`
- `TestSampleFlappyIntegration#rewindAcrossFirstControllerUpdateKeepsOneControllerAndSixStablePipes`
- `TestSampleFlappyIntegration#recycleMovesTheSameEntryAndAdvancesCounterPermutation`
- `TestSampleFlappyIntegration#onePipeScoresOncePerCycleAndResetEnablesTheNextCycle`
- `TestSampleFlappyIntegration#customZoneComposesNativeTailsHudCreatorClaimsAndDecodedPipeArt`

The thirteen skips remain optional audio/rewind/rendering/timeline diagnostics,
unavailable surfaceless EGL, the existing CPZ spin-tube capture assumption, and
an absent local BizHawk audio reference. No missing-ROM skip occurred.

Guards: 82 reports, 665 passed, zero failures/errors/skips, 177.18 s.
The added profile-policy check accounts for the extra guard case. Combined
ordinary and guards: 424.22 s (7m04s), versus 460.65 s (7m41s): 36.43 s saved,
7.9%. No new or worsened failure was observed; the run remains red because of
baseline failures. This does not certify route completion or skipped native and
optional diagnostic coverage.

The final follow-up changes only this evidence document; executable files remain
those validated at `e7cc8de78`. Consumed runner diagnostics and focused reports are
removed before delivery. No production source changed.
