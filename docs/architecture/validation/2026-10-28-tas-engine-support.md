# Remaining TAS support: composed receiving checks

## Status and source

Implementation and composed verification are complete. The task base is
`8945c0a789b27be57b2361c70f561f31872a76ca`. Final consumer verification uses
`139541c0d6d68b22b28f0c3742717b28dff392cd`, with `src` tree
`2685aecffa0a45c62e47f5a03aa1be76cb7393f1` and production tree
`ae9d672b932d62a877bfddae53a4059a9404b6aa`. The subsequent exact bootstrap-audit
approval changes tests only. Four matched pre-existing baseline failures remain;
there is no unresolved new regression. Integration, push and cleanup bindings
are recorded in the task ownership registry and current external verdict report.

The initial stock receiving stage below used `3b6bc2fd7836046958f85c674cf9e11a2be2267d`
plus stock reset registration/test changes, tested `src` tree
`fa48fe2cb034f0d1a2604058ca75ec57c4b062cf`. Its observations remain historical
stages rather than the final source binding.

The isolated integration branch contains the pause, reset, ORT, DEZ, original
cartridge audio and current startup owners. The native Competition host and P2 intro are also composed and focused-qualified.
Competition support concerns the historical missing-host expectations; full route
and published-input synchronization remain unexecuted. The complete selected
smoke run, matched baseline attribution, focused regression repairs and final
composed/domain checks are recorded below. The current assessment is 117 supported
and four deliberate exclusions; this is bounded mechanism support.

## Stock composition

The parent supplies explicit engine/root/input/mode-publication capabilities to
native Competition visits. The stock S3K module binds the standalone title exit,
suppresses the locked-on save grid for the actual SK cartridge, admits the
standalone intro input owner before the existing ending overlay, and registers
the native session reset policy.

Audio profiles are cached by the actual logical cartridge's resource enum,
without capturing its reader or a retired world. Original S3 Competition also
supplies its explicit audio profile and native sound map: level audio
configuration must not replace a valid map with an empty one.

Receiving fixtures use those stock startup/reset services. The reset lifecycle
fixture retains only its title-input observation seam; the separate output
fixture uses the real title. The composed reset/pause witness starts a fresh
ordinary FBZ world after actual reset, reaches normal admitted gameplay, then
uses the recorded-input ownership lease for real Start edges. Paused V-int
advances while gameplay/RNG freeze, and full registry replay across resume must
match. This is not the published no-Left/Right MHZ-to-FBZ route.

## Commands and observed results

`TAS_WORKTREE` denotes this isolated integration checkout and `TAS_ROM_ROOT`
the existing original-ROM directory. Exact local bindings and live command
handles are in the task ownership registry. No ROM was copied, renamed or linked.

```bash
cd "${TAS_WORKTREE}"
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=${TAS_ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${TAS_ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  "-Dkis2.rom.path=${TAS_ROM_ROOT}/kis2.gen" \
  '-Dtest=com.openggf.TestGameLoopNativeMenus,com.openggf.TestTasS3kTitleCapabilities,com.openggf.game.sonic3k.audio.TestSonic3kAudioModuleComposition,com.openggf.tools.TestTasS3kSessionReset,com.openggf.tools.TestTasS3kSessionResetOutput,com.openggf.tools.TestSonic3kStandaloneNativeLaunch' test
```

1. The first request acquired admission after 14 seconds, then stopped at
   test compilation: the newly added stock reset assertion lacked a
   `GameServices` import. Maven took 1m34s; outer exit 1; **zero tests ran**.
2. After that import correction, the same selection ran **25 tests: 24 passed,
   one failed, zero errors/skips**, outer exit 1. Queue wait 0s; Maven 34.811s.
   The failure was the new post-reset pause witness: a logical Start sample had
   no recorded-input ownership lease, so the production loop correctly did not
   treat it as an admitted recorded Start edge.
3. The helper now acquires that existing lease around each authored input step.
   Production code and assertions were unchanged. Only the affected method ran:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.tools.TestTasS3kSessionReset#ordinaryMovieKeepsItsLevelCursorAndRestoreForwardReplayAfterResetUsesFreshRegistry' test
```

The affected method **passed: one test, zero failures/errors/skips**, outer exit
0. Queue wait 10s; Maven 33.325s. The other 24 successful observations remain
applicable: only this new test helper changed between requests. Thus **25
distinct receiving cases have passing observations**, rather than a claim that
one final all-class command passed.

| Receiving class | Cases | Outcome after the affected recheck |
| --- | ---: | --- |
| `TestGameLoopNativeMenus` | 8 | Passed |
| `TestTasS3kTitleCapabilities` | 3 | Passed |
| `TestSonic3kAudioModuleComposition` | 2 | Passed |
| `TestSonic3kStandaloneNativeLaunch` | 5 | Passed |
| `TestTasS3kSessionReset` | 6 | Five unchanged passes; affected method passed |
| `TestTasS3kSessionResetOutput` | 1 | Passed |

## Owner evidence and limits

- [Pause clock and palette rewind](2026-10-28-tas-support-pause.md):
  `ce9005f0b`; includes the separately recorded Maven-success/outer-143
  observation and later clean receiving command.
- [Reset/Power](2026-10-28-tas-support-reset.md): `7274607b1`;
  representative lifecycle support, with the published no-Left/Right route
  unexecuted.
- [ORT placement recovery](2026-10-28-tas-support-ort.md): `d31cc5c44`;
  local cursor/allocation/identity semantics, excluding downstream KosM
  adjacent-memory corruption.
- [Ordered S2 object art](2026-10-28-tas-support-dez.md):
  implementation `b5c3f5594`, record `7e2560314`; real output qualified,
  quick-kill causal route and exact intermediate service timing unexecuted.
- [Cartridge audio](2026-10-28-tas-support-audio.md): `5d57120de`;
  NTSC resource/output support, with original-S3 PAL/SEGA differences retained.
- [Native Competition host](2026-10-28-tas-support-competition.md):
  production through `895b76b67`, final affected tests `179ac7f07`; both cartridges,
  five arenas and bounded Match/Time Attack consumers, not complete race routes.
- [P2 intro](2026-10-28-tas-support-intro.md): `8b7a7d33b`; synchronized
  native input-latch/palette evidence and actual title-to-EHZ1 behavior.
- [Standalone startup](2026-10-28-tas-support-standalone.md):
  source through `b4a926761`; native title/MHZ startup and bounded ordinary
  controls, not full campaign, revision/viewport/team or exact pixel parity.

No full movie, release full suite or exhaustive configuration product was run.
The old verdicts and full test reel retain their original evidence identities.

## Playback ownership review and follow-up

A pinned-source review of `ee9e721ed1ce8c9ef9a997263b0522024f31af66`
found two concrete composition defects. Active movie playback bypassed the
live `LevelInputOverlay`, so recorded P1 Start paused the SK intro and P2 Start
never reached it. Separately, a paused movie lost its post-reset menu owner,
which disconnected inputs and cursor advancement on resume.

Four new actual-session cases reproduced those failures: P1/P2 movie Start and
pause/resume before/after the first held menu action. **Four tests failed,
zero errors/skips**, outer exit 1; queue wait 5s, Maven 1m36s.

The internal `NativeLevelInputOwner` now receives the current published movie
sample before native pause admission. Live configuration overlays retain their
external-input guard. Publication does not advance the cursor. S3K's existing
intro/ending controller handler serves both ordinary and recorded consumers.
Reset-menu ownership now follows the attached timeline identity, preserving a
paused session while distinguishing a replacement timeline of the same movie.
A destination-owner control verifies that an old menu neither acknowledges its
row nor clears its input sample. Public BK2 pad/movie signatures are unchanged.

The first repair selection passed **27 tests, zero failures/errors/skips**,
outer exit 0, queue 2s, Maven 1m35s. Its requested stale bridge-control class
name matched no class; the actual bridge class was verified separately. That
request with `--lean` exhausted the compiler's 1 GiB heap before JUnit, after
39s queue and 56.867s Maven. It is a failed preparation, not a test result.
The required `--normal` retry passed **seven tests, zero failures/errors/skips**,
outer exit 0, Maven 32.871s; no source change or heap override was used.

After strengthening timeline-replacement ownership, the final affected selection
ran all existing/new reset and standalone receiving methods plus ordinary
overlay, pause, external-drive and bridge controls:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.tools.TestSonic3kStandaloneNativeLaunch,com.openggf.tools.TestTasS3kSessionReset,com.openggf.tools.TestTasS3kSessionResetOutput,com.openggf.TestMutatorOverlayRouting,com.openggf.TestRecordedInputHostPause,com.openggf.TestGameLoopExclusiveFrameDrive,com.openggf.debug.playback.TestPlaybackInputBridgeOwnership' test
```

**47 tests passed, zero failures/errors/skips**, outer exit 0; queue 6s,
Maven 1m39s. Tested `src` tree:
`e170932d7bdb723871def61853cb0461338f0cae`. This includes the replacement-timeline
control; no separate red execution is claimed for that additional control.
The previous 13 unchanged menu/capability/audio-composition successes remain
applicable, yielding **60 distinct parent receiving cases with successful
observations**, not one 60-case final command. Combined smoke/domain gates
remain separate.

## Native Competition reference boundary

The controller-only published input is a useful research artifact, but its
candidate Azure/GO boundary was never observed. A bounded original-S3 GPGX
prefix stayed outside Competition; its failed engine-alignment candidates are
not native physics or missing-host verdicts.

The native loop has four *opportunities*, not four unconditional actor passes.
`loc_4C1C` (s3.asm:6077-6086) normally sets `_unkFF7E` to zero; its debug/sound-test
branch can select a nonzero value. After the first body, `loc_4C78`
(s3.asm:6105-6130) admits later bodies only on accumulator carry. No four-pass
change was made to engine physics. This corrects the overbroad wording in the
external native-prefix README; its raw observations and provenance are unchanged.


## Current named-scenario assessment

The historical report remains unchanged at 104 supported and 17 unsupported
named scenarios. The current assessment covers the same 121 IDs and qualifies
13 additional engine mechanisms: five pause consequences, two typed-reset
prerequisites, two Competition host expectations, standalone SK startup, ordered
DEZ art reuse, ORT recovery and the demonstrated P2 intro transition. The focused
assessment is therefore **117 supported / four deliberately unsupported**, with
completed combined verification and attributed baseline failures below. Support follows each original native
expectation; it does not broaden a mechanism check into a full movie certificate.

Three KosM assessments require adjacent-memory corruption outside the engine
reimplementation boundary. The fourth exclusion is exact jump-dependent native
halfpipe hardware lag. Neither an arbitrary-memory compatibility layer nor CPU,
VDP or bus timing emulation was added. Logical art-slot last-writer behavior and
semantic V-int ownership are game algorithms and remain supported.

The 81 defined scenario families aggregate to 77 supported, three unsupported and
one mixed; the additional historical source-note family remains a source note.
The 55 publication associations and old full-reel source identities are retained.
In particular, the no-Left/Right MHZ-to-FBZ route remains unexecuted: its repaired
expectation is that the movie input path can represent and dispatch Reset.
Competition's two repaired expectations are native host dispatch, with real
participant, output, result, save and restart consumers; complete event routes
and original-TAS synchronization remain outside that bounded qualification.

## Combined validation plan

The change-based selection is pinned to the original pre-task destination
`8945c0a789b27be57b2361c70f561f31872a76ca`. Upstream `develop` subsequently
advanced to `dc73d64858eef4087e70b572d780eb3f7cf50201`; its bounded source-audit
concurrency changes were merged without gameplay conflict. Shared lifecycle,
timing and decoder changes require the complete selected smoke coverage, not a
release full suite. The dry-run plan selects 4,279 candidate classes and separate
fresh-JVM guards. The qualified two-worker local recipe targets about 30 minutes
of execution including preparation/guards; shared admission waiting is additional
and total elapsed delivery time is unmeasured.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 8945c0a789b27be57b2361c70f561f31872a76ca --workers 2 \
  --headless-gl surfaceless-egl --rom-directory "${TAS_ROM_ROOT}" --preflight

env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 8945c0a789b27be57b2361c70f561f31872a76ca --workers 2 \
  --headless-gl surfaceless-egl --rom-directory "${TAS_ROM_ROOT}" --run
```

The run must finish the planned batches and guards. New or worsened failures
block integration; disputed failures require bounded matched baseline/current
checks by identity and assertion. Required original-ROM S3K checks remain
`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
`TestSonic3kBootstrapResolver` and `TestSonic3kDecodingUtils`, with skips inspected.
The completed run and affected rechecks follow. The broad run was red; its new
regressions were repaired and its baseline failures remain explicit.


## Completed combined run and failure attribution

The complete smoke plan ran at `88b2e5d483e7aa24c23dbf8c1da103071a254c18`
(`src` tree `78e969328faef45eec646d60c7848082747128dd`; prepared working-tree
fingerprint `0e435832d04796c20951131927110f64563ac5e0ccaa159af98b991bb4b9c1da`).
Java 25/Lua 5.4, explicit surfaceless EGL and the verified original ROM directory
were supplied. Preflight passed; preparation took 34.85 seconds.

| Lane | Completion | Tests | Failures | Errors | Skips | Maven execution wall |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Ordinary smoke, two workers | All 86 planned batches | 35,380 | 9 | 16 | 52 | 1,569.36 s |
| Fresh guards, one worker | One planned batch | 808 | 5 | 0 | 0 | 155.86 s |

Both lanes returned nonzero; this is **not a green broad run**. Queue waits are
additional and separate from the lane execution values. Fifty-two explicit skips
were inspected: opt-in measurements/captures/soaks/local packaged-mod artifacts,
platform/audio prerequisites, two REV00-only S2 cases and one declared spin-tube
fixture prerequisite. No missing original REV01 S1/S2 or locked-on S3K ROM was
hidden as a skip. No failed/skipped case was omitted from the runner summaries.

The four required S3K classes also ran in their separate original-ROM selection:
**24 tests passed, zero failures/errors/skips** in Maven, which reported
BUILD SUCCESS after 34.346 seconds. The command session subsequently returned
outer exit 143; its cause remains unknown and is not reported as exit 0. Queue
wait was 6,640 seconds, overlapping the combined run and its retained worktree
lease. The final composed run below includes all four classes and obtains a clean
command result.
Their per-class counts were bootstrap resolver 6, decoding utilities 3, AIZ1 skip
8 and level loading 7. This is separate domain verification, not an added claim
about complete campaign or publication synchronization.

| Broad failure group | Cases | Attribution / required follow-up |
| --- | ---: | --- |
| Native Competition pause consumers | 2 failures | Shared pause service and S2 host both spent the same clock tick; explicit first-claim clock ownership correction qualifies the native consumers. |
| S2 common-only PLC parser | 1 error | The later invalid-selector fallback control (`-2`), not the initial `-1` common sheets, reached overly strict static art provenance. Common-scope normalization preserves parser fallback and strict native/foreign identity checks. |
| Stock-art override inventory | 1 failure | Three new SK intro keys were absent from creator discovery; add the actual provider keys, retaining the existing exact inventory assertion. |
| Stageplay S3K donor audio | 16 cases: 15 errors, 1 failure | Ambient S1/S2 world ROM incorrectly selected the detached S3K donor's cartridge profile. Correct the source/root ownership rather than relaxing strict cartridge decoding. |
| SOZ cork transfer/reload | 4 failures | Native respawn address was not copied at ownership transfer; per-instance rewind then lost the column's placement association. The four matching baseline cases pass. |
| CNZ numeric-slot inventory | 1 failure | Matching baseline assertion fails too; no task change to its test or profile inventory. |
| Loop dispatcher/class budgets | 2 guard failures | Extract coherent admission/visit collaborators; retain all retirement behavior and lower only stale exact-method ratchets. |
| Rewind coverage, static S3K state and assertion-free fixture guards | 3 guard failures | All three exact baseline assertions reproduce; unrelated to this task's new source. |

The matched baseline is `develop` at
`e2ffdc430c2d330fa6e0069fd68e86c303625e43`. The destination advanced from
`dc73d64858eef4087e70b572d780eb3f7cf50201` while the combined run was pending;
that advance predates the matched compilation. Its relevant ObjectManager,
placement, cork implementation and cork test files have no delta from `dc73`,
and no task repair was present there. The ordinary matched selection ran five
cases: CNZ's `numericSlotIsImplementedInBothPointerTables` failed with
`expected true / actual false`; all four
`everyCorkSubtypeBreaksThroughProductionTouchAndRewindsChildGraph` cases passed,
zero errors/skips. Maven took 1m36s, finishing at 23:24:00Z.

The matched fresh-guard selection ran these three exact methods and reproduced
all three failures, zero errors/skips:

- `TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline`: the existing
  `MhzEndBossInstance#objectRef#hitProxy` coverage key.
- `TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow`: existing
  `HCZWaterSkimHandler.slotRetired` and `Aiz2BossEndSequenceState.cutsceneKnucklesSlot`.
- `TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree`:
  the existing nested `TestTestReelExtension#bodyPasses` fixture.

Those four baseline failures remain explicit. The guards/baselines are not
weakened, relabeled as passes or repaired as unrelated cleanup. The new
regressions received the focused successful rechecks below before integration.

### Current upstream composition

Upstream native-code support and fallible world/scene retirement were merged at
`a3c022376`, preserving obsolete-notice removal and the TAS reset/playback owners.
The common-art correction `d0850f940` is composed through `d904596bb`.
Follow-up corrections are all included in the final consumer source:

| Owner | Final correction | Discriminating checks |
| --- | --- | --- |
| DEZ common provenance | `d0850f940` | Exact parser fallback plus common/foreign/load rendering and rewind: 9 passed. |
| Pause consumers | `bf931d696` via `9cb020485` | Explicit first-claim clock owner; remove only S2's duplicate increment: 35 passed. |
| ORT lifetime handoff | `4b4aadc68` via `59e0eacca` | Copy captured native respawn address to the child, retain original; cork/duplicate/rewind consumers: 79 passed. |
| Reset/visit structure | `70a25dd59` via `5eeb73756` | Coherent collaborators and downward-only method ratchets: 185 distinct passing observations, including affected fresh guards. |
| Root/donor audio | `8271d94e7` via `139541c0d` | Owning root reads its pinned world ROM; detached donors retain declared resources: 40 distinct passing observations. |

Their stream records preserve initial failures, fixture corrections, exact
source identities and reuse boundaries. Counts are separate selections and must
not be added to infer unique delivery-wide coverage.


The category runner reported 87 separate admissions with 4,863 seconds of
rounded queue waiting in total. Preparation plus lane execution was 1,760.07
seconds (29m20.07s), meeting the qualified execution target; waiting is additional.
The separate domain request's wait overlaps that run and must not be added as
elapsed delivery time. Total task wall time remains unmeasured.

### Stock-art catalogue correction

`StockArtOverrideCatalog` now publishes the three actual SK intro provider keys:
`sk_intro_bomb`, `sk_intro_knuckles` and `sk_intro_knuckles_grab`. The existing
exact provider-inventory test is unchanged. On `d904596bb` plus those three
entries, `TestStockArtOverrideCatalog` passed one case, zero failures/errors/skips,
outer exit 0; queue 13 seconds, Maven 1m33s. No art availability, frame contract
or native override policy was widened.

Tested stock-catalogue `src` tree: `a0298f0793e8efc561d85ea63d0bb03a689a4f1a`.


## Final composed consumer and domain verification

The following exact selection ran at `139541c0d6d68b22b28f0c3742717b28dff392cd`.
It includes stock services, real movie input, upstream retirement consumers,
every repaired broad failure group and all four mandatory S3K gates.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=${TAS_ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${TAS_ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  "-Dkis2.rom.path=${TAS_ROM_ROOT}/kis2.gen" \
  '-Dtest=com.openggf.TestGameLoopNativeMenus,com.openggf.TestNativeCompetitionIteration,com.openggf.game.sonic2.competition.TestSonic2NativeCompetitionHost,com.openggf.game.sonic3k.competition.TestS3kCompetitionNativeHost,com.openggf.game.sonic3k.competition.TestS3CompetitionProduct,com.openggf.tools.TestTasS3kSessionReset,com.openggf.tools.TestTasS3kSessionResetOutput,com.openggf.tools.TestSonic3kStandaloneNativeLaunch,com.openggf.TestRecordedInputHostPause,com.openggf.TestMutatorOverlayRouting,com.openggf.debug.playback.TestPlaybackInputBridgeOwnership,com.openggf.game.sonic3k.audio.TestSonic3kAudioModuleComposition,com.openggf.audio.TestSonic3kAudioSourceComposition,com.openggf.stageplay.runtime.TestStageplayAudioLifecycle#authoredSongsSurviveNativeEntriesInOfflinePcm+nativeS3kCreditsRequestUsesTheCreditsAssetInBaseAndDonorProducers,com.openggf.stageplay.runtime.TestStageplayDonorEffectsRom,com.openggf.stageplay.runtime.TestStageplayLiveAbilityReportingRom,com.openggf.stageplay.runtime.TestStageplayUnifiedWorldRom,com.openggf.mods.TestStockArtOverrideCatalog,com.openggf.game.sonic2.TestSonic2PlcParser#commonOnlyArtLoadsRealRomSheetsWithoutLookingUpAZoneHeader,com.openggf.tests.TestSozSandMechanismsProduction#everyCorkSubtypeBreaksThroughProductionTouchAndRewindsChildGraph,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.mods.code.TestSessionRetirementOwnership,com.openggf.mods.scene.host.TestSceneVisitEffectAdmission' test
```

**178 tests passed, zero failures/errors/skips**, 26 reports, observed outer
exit **0**. Queue wait 7 seconds; Maven 1m46s; finished
2026-10-28T23:54:59Z. The four domain classes contribute 24 cases and now have
a clean command exit. No original-ROM domain check was skipped.

## Final fresh guards and exact access audit

At the same consumer source, a fresh guard JVM ran:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

It completed **800 tests, four failures, zero errors/skips**, outer exit 1;
queue 138 seconds, Maven 3m09s, finished 2026-10-28T23:58:34Z. Three failures
are the exact matched baseline guard failures listed above; exception types and
messages match, not merely totals. The fourth was the new production bootstrap
access in `Sonic3kGameModule#getAudioProfile`, absent from the explicit access
inventory. The approved path runs only with no current world and exact root
module identity; it caches immutable enum-selected profiles, never a ROM reader
or module. Add that exact method/line-shape approval and retain all guard rules.

Only the affected audit class then ran in another fresh guard JVM:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=com.openggf.game.patch.TestBootstrapModuleProviderCachingGuard' test
```

**Seven tests passed, zero failures/errors/skips**, outer exit 0; queue 0s,
Maven 36.272s, finished 2026-10-29T00:01:19Z. No production source or consumer
fixture changed after the 178-case run. This is an affected audit recheck, not
a fabricated final 800-case green invocation.

The complete selected smoke coverage was executed once. Every new failure was
repaired and checked through its affected consumers; four unrelated, matched
baseline failures remain: the CNZ inventory assertion and three guards. This
qualifies the bounded delivery under the baseline-comparison policy, while
remaining explicitly short of a green broad/CI or release-full-suite claim.
Coverage obligations and remaining breadth are linked from the
[level-test backlog](../../status/level-test-coverage.md). Historical full-reel
footage and full-route/TAS synchronization claims are unchanged.
