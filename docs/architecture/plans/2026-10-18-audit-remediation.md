# October 18 audit remediation

Base: `8030832d2ab6080e5324cdfe35e971677fd97b41` on `develop`.
Worktree: `.worktrees/ai-audit-remediation`, branch `bugfix/ai-audit-remediation`.
Scope: repair the twenty findings from the read-only audit without changing native
physics, trace authority, public API signatures or the network wire format.

## Decisions

- Block held rewind during append-only input recording. Truncating movie buffers
  would require recording state to participate in world restoration and is unnecessary.
- Retained round results reserve their wire slots until the next round. New members
  use another protocol slot while the configured cap still limits active players.
  Adding participant identity to the wire was rejected to preserve protocol compatibility.
  This bounds distinct retained-result sessions to 256 wire slots; exhausting them
  blocks replacement admissions until the next round, including a returning owner.
  Removing that bound requires a separate participant-identity protocol change.
- Keep verified bests separately from pending improvements; rejection and timeout
  restore the last verified result. Near ghost streams expire after 120 local advances
  without an aggregate and can resume from a new jitter buffer.
- Read legacy saves as before, enumerate both locations, and remove the legacy
  fallback before deleting its canonical replacement. A failed deletion reports false.
- Share catalog metadata compatibility with playback; strict manifest decoding still
  rejects missing identity, while the existing compatibility reader tolerates only that gap.
- Allocate required empty editor terrain before optional spare slots. Object-backed
  rings retain native 12-bit Y; separate authored ring entries retain unsigned 16-bit Y.

## Regression map

| Audit finding | Regression owner |
|---|---|
| 1 Legacy save resurrection | `TestModStorage` |
| 2 Rewind during input recording | `TestGameLoopSpecialStageRewindGate` |
| 3 Negative camera Y painting | `TestEditorMouseTransform` |
| 4 Verified best fallback | `TestHostRoundEngineVerified` |
| 5 Late podium verdicts | `TestClientRaceSession` |
| 6 Reused result slots | `TestRoomHost` |
| 7 Tunnel close cleanup | `TestHostMasterLink` |
| 8 Near-to-far ghost transition | `TestRemoteGhostPlayback` |
| 9 Joining during votes | `TestRoomHostVote` |
| 10 Stereo PCM registration | `TestDecodedPcmCache` |
| 11 Null movie manifest isolation | `TestUserRecordingManifestJson`, `TestUserRecordingCatalog` |
| 12 Missing movie engine identity | `TestUserRecordingSessionLauncher` |
| 13 Duplicate recording control tick | `TestGameLoopTitleCardPause` |
| 14 Null YAML startup | `TestLegacyConfigMigration` |
| 15 Legacy Player 1 Start | `TestConfigMigrationService` |
| 16 Blank-project capacity | `TestEditorProjects` |
| 17 Separate ring height | `TestEditorSpawnFactory` |
| 18 Pending Mod Manager budget | `TestModManagerScreen` |
| 19 Codec placeholder argv | `TestFfmpegCommandTemplate` |
| 20 PNG transparency keys | `TestPngTransparency` |

## Validation checkpoint

The new regressions failed before their corresponding fixes. The first batch
reported five assertion failures and one stereo-registration exception. The second
reported eleven assertion failures and four errors (null manifests have two checks).
After repairs, the combined 21-class focused selection passed 201 tests, zero
failures/errors/skips, using Java 25 and queued Maven `--lean -Dmse=off -Dtest=… test`.

High-reasoning GPT-6.1-Sol reviewed implementation `eb5e3f959` and found one
introduced expiry callback identity error: collected map entries remained live
while fallback restoration replaced their values. A new regression reproduced
attempt 1 being reported instead of expired attempt 2; the repair snapshots the
expired Best before restoration and uses it for both callbacks. The review also
noted the documented wire-slot exhaustion tradeoff and a pre-existing empty
podium when joining ROUND_END before any later delta; that separate join behavior
is outside the twenty audited fixes.

### Completed delivery validation

Production source tested at `0517bd9e0e2d3df438fbaf7304fb85b9fd3a12d9`.
The final follow-up changes only the older spot-check test and this evidence.
Java 25, Lua 5.4 and PowerShell preflight passed. Root ROMs were discovered and
identity-verified by the category runner; no missing-ROM skip was reported.

The shared GameLoop/capture changes selected 3,503 ordinary smoke candidate
classes, partitioned into 71 batches, followed by guards. The stopping rule was
completion or the configured 120-minute execution/no-output limits. Expected cost
was a potentially hour-long ROM-heavy run; completed ordinary execution was
5,589.82 seconds and guards 234.23 seconds (queue waiting is separate).
Run `20261018T012238Z-20d4a453` completed every batch:

- Ordinary: 36,984 tests, three failures, two errors, 81 skips.
- Guards: 715 tests, four failures, zero errors/skips.
- One ordinary failure was an older spot-check assertion requiring slot reuse.
  That assertion now requires reserved-slot isolation, while preserving checks of
  original identity and recording-request isolation. The affected four-class
  network selection then passed 32 tests with zero failures/errors/skips.
- The other four ordinary failures/errors and all four guard failures reproduce
  on unchanged base `8030832d2ab6080e5324cdfe35e971677fd97b41`, by exact test and
  assertion/stack. They were not repaired as part of this task.
- Skips cover opt-in native graphics/audio, capture, measurement and soak checks;
  unavailable local mod/BizHawk fixtures; 32 inapplicable platform/flyer route
  cases; and a spin-tube capture/release assumption. These remain coverage limits.
  This is bounded smoke validation, not a fully green or full-suite result.

Exact remaining baseline failures:

| Test | Baseline evidence |
|---|---|
| `TestTitleInputOwnership.captureChordIsConsumedWhileBlockedAndCannotFireAfterReturningWithKeyHeld` | line 105: expected true, got false |
| `TestZoneLayoutMutationPipeline.levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary` | null `GameModule.getRules()` at `GameplaySessionFactory:60` |
| `TestLevelManagerModPatternWindows.everyLoadAndEditorRebuildRegistersModWindowsBeforeStockArtCaching` | same null-rules stack at test line 65 |
| `TestOrdinaryTitleScreenCommandEvidence.masterTitleDrawExpandsBackdropAndFitsActionPaneAtEveryWidth` | line 111: card-row footer START hint missing at width 320 |
| `TestProductionSingletonClosureGuard.productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices` | `MenuAudioProbeTool:41` uses `AudioManager.getInstance()` |
| `TestProductionSingletonClosureGuard.productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge` | same raw singleton call |
| `TestArchUnitRules.shared_code_does_not_construct_concrete_sonic_provider_classes` | `HubMusicController:118` constructs three concrete audio profiles |
| `TestArchUnitRules.shared_layers_do_not_depend_on_game_specific_packages` | same controller accesses the three profiles and game music enums |

Commands below ran in the task worktree except the explicitly marked matched
base checks, which ran in the unchanged main checkout. Environment used JDK 25
and Lua 5.4; `--rom-directory` was the main checkout. The category diagnostics
were inspected, including failures and skips, then acknowledged and deleted.

```bash
python3 tools/testing/run_categories.py --base 8030832d2ab6080e5324cdfe35e971677fd97b41 --preflight
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHostMasterLink,TestClientRaceSession,TestHostRoundEngineVerified,TestRemoteGhostPlayback,TestRoomHost,TestRoomHostVote,TestUserRecordingManifestJson,TestUserRecordingCatalog,TestUserRecordingSessionLauncher,TestModManagerScreen,TestEditorProjects,TestEditorSpawnFactory,TestPngTransparency,TestGameLoopTitleCardPause,TestGameLoopSpecialStageRewindGate,TestDecodedPcmCache,TestLegacyConfigMigration,TestConfigMigrationService,TestEditorMouseTransform,TestFfmpegCommandTemplate,TestModStorage' test
# 201 tests passed before the reviewer follow-up.
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHostRoundEngineVerified,TestHostRoundEngine,TestRoomHost,TestRoomHostVote' test
# 39 passed after the reviewer follow-up.
python3 tools/testing/run_categories.py --base 8030832d2ab6080e5324cdfe35e971677fd97b41 --rom-directory <main-checkout> --max-minutes 120 --run
# Matched base checks: 17 tests (one failure/one error), one test (one error),
# nine tests (one failure), 73 tests (four failures), respectively; no skips.
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestTitleInputOwnership,TestZoneLayoutMutationPipeline' test
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestLevelManagerModPatternWindows' test
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestOrdinaryTitleScreenCommandEvidence,TestSpotCheck' test
python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestProductionSingletonClosureGuard,TestArchUnitRules' test
# Final task-tree regression after aligning the spot-check expectation: 32 passed.
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSpotCheck,TestRoomHost,TestRoomHostVote,TestHostRoundEngineVerified' test
python3 tools/testing/run_categories.py --acknowledge 20261018T012238Z-20d4a453
```

No release-only full suite was launched. No production source changed after the
combined smoke/guards run; the final test-only correction was verified narrowly.
