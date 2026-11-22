# Sonic CD playtest fixes — 2026-11-15

Current reconciliation: James authorized the parent to merge PR #332 into
`develop`. The implementation child merged develop into the existing feature
branch; see the reconciliation record below. Earlier no-merge authorization
statements describe historical handoffs and are superseded by this instruction.

## Final delivery summary (original draft handoff)

All eight reported fixes, including held Escape, are implemented for manual
playtesting on `feature/ai-sonic-cd-playtest-fixes`. Delivery is a draft PR to
`develop`; no merge is authorized. Automated attribution and guard corrections
are complete, with no live jobs or remaining scoped delivery blockers. James's
manual acceptance below remains **pending**.

- Initial focused integration: 274 cases, 244 passed, one source-guard failure,
  29 allowed absent-native-capture skips. Functional checks passed.
- Change-based smoke: 4,947 classes, 41,031 cases, 43 failures, 26 errors,
  601 skips; **VERDICT FAILED**. Missing required/unclassified inputs and
  unperformed skip coverage remain gaps; this is not a green broad/full suite.
- Correction checks: 123 cases, 120 passed, one camera-scanner failure, two
  allowed native skips; source and draw-cache guards passed. Final scanner
  correction: four tests passed, zero skips, including true `PpzBoulder`
  virtual dispatch and truthful `ObjDango` art-selection metadata. No guard
  requirement was weakened.
- Matched baseline attribution reproduced 59 of 66 original ordinary failures
  in the initial 30-class comparison. The cog later passed on both sources.
  The final two-class comparison ran 23 cases per side: 13 passed and ten
  matching failures, zero errors/skips. All eight Three Islands failures match
  with the actual catalogue paths; both Flappy rewind failures reproduce with
  workspace `rewind.budgetScope=off`. Earlier apparent baseline passes came
  from different environment/configuration, not established regressions.
  No source-induced regression was identified within the compared scope.
- Results rendering verified `123400/1200/50000` advancing to
  `126600/0/48000`, then exact restored pixels for the same live instances;
  graph recreation and end-to-end rewind remain outside this evidence.
- Latest fetched disassembly `origin/ai-development` is
  `ca8903228f9af567df9fca4520906ea118f60ab9`; review warranted no pin change.

Tests ran working sources based on `25659a9702ccf9a388fc75db9d05c16c9769efcc`
before the implementation commit containing this record existed. Historical
fingerprints and commands below identify their actual source states; no whole
commit is claimed tested. The final delivery changes documentation only.
The PR records the delivered head. Current workspace stays on this feature
branch; user media, crash reports and submodules are preserved.

Paths in this record are normalized for repository policy: `${REPO_ROOT}` is
the current OpenGGF workspace and `${HOME}` is the operator home. Historical
commands retain their original arguments with those path prefixes substituted.

## Scope and delivery state

Owner: playtest coordinator; focused implementation/evidence owners: objects,
PPZ background, suspension, audio and results workers; documentation: this
handoff. Historical working-source base/HEAD on this branch:
`25659a9702ccf9a388fc75db9d05c16c9769efcc` before delivery commit.
Evidence below is worker-reported against their working source, not a tested
integration SHA. The branch is for James's manual test before any merge into
`develop`; no merge is authorized.

Eight reports define the scope: flowers never grow; invisible diagonal spring
`0A:0A`; garbage monitor reward graphics; PPZ vertical background jump near
player top-left X=9499/Y=580 and garbage at the bottom; music too loud relative
to effects; results bonus digits/score; PPZ object `2D` misnamed “Ga”/garbled;
and held Escape crashing with unknown game `scd`. Settings resume/end and the
suspended card's displayed act are part of the suspension fix.

No new stage/route certification, exhaustive parity audit, hardware/analog
audio calibration or new supported mode is claimed. Existing stage breadth
and gaps remain in the [stage matrix](levels/scd-stages.md).

## Changes and bounded evidence

| Area | Implemented behavior | Worker evidence and limits |
| --- | --- | --- |
| Objects | Consume flower animation routine advancement; resolve diagonal spring tile despite palette bit; decode the monitor item's single static mapping piece; resolve names by the current stage factory; use section-owned PPZ generator art | 21 tests, zero skips: `TestSonicCdCommonObjects2`, `TestCdObjectRegistryStageScope`, `TestSonicCdObjectFixPictures`. Local art plate: `target/soniccd-object-fixes/disc-art.png`; not archived or a whole-scene certificate |
| PPZ background | Disc camera-section table selects row ownership through a shared semantic layout hook; static rows repeat at 512 pixels while dynamic rows retain layout coverage | `TestPpzBackgroundRowSampling`: 3 passing tests including pixel comparison; `TestPpzPeriodsScroll`: all 10 variants, 300 frames each. Does not certify a complete route or all widths |
| Suspension | Hosted entries can suspend/resume, restore settings and report the player-facing act | 212 tests, zero skips across `TestSuspendedGame`, `TestSuspendedSettings`, `TestMasterTitleSuspendedGame`, `TestGameLoopSuspensionOwnership`, `TestEngine`, `TestArchitecturalSourceGuard`; manual resume/end still pending |
| Audio | Normalize CD sources by 3/8 to match fast FM's 3/4 scale followed by the shared 1/2 mix | `TestCdPresentationScale`: 2 passing tests via isolated Java/JUnit, **not Maven**; both fail on baseline. First Maven attempt failed during concurrent compilation and is not an audio verdict. Analog unverified; Nuked relative residual approximately 0.27 dB |
| Results | Update score/time/ring digit art in results sheets; invalidate derived upload markers after restore | Follow-up log `target/results-review-fixed.log`: 18 tests, zero failures/errors/skips (17 common-object cases plus one rendered results case). Worker evidence, not integrated validation |

Audio reference: Genesis Plus GX commit
`2499cd3e3d1d8b7d0130d8b2825adb4cc608b2c0`; the presentation correction
preserves native device arithmetic and player music/effects controls.

The former single background-window heuristic cannot express independently
static and moving rows; row ownership replaces that compromise. The earlier
audio path bypassed the shared FM headroom; normalization belongs at the CD
presentation contribution, rather than changing native players.

The fetched `scddisasm` `ai-development` tip is
`ca8903228f9af567df9fca4520906ea118f60ab9`. All implementation workers found
no correction warranted by that update; keep the reference unchanged. Workers
reported inherited unresolved citation findings: this is not a globally clean
source-citation verdict. Existing failure logs remain under `target/`; no raw
screenshots/logs are archived here. No trace sweep/frontier update was performed.

## Manual acceptance checklist

- [ ] Let a planted flower complete its growing animation.
- [ ] Find diagonal spring `0A:0A`; check visible art and launch behavior.
- [ ] Break monitors; check reward graphics during emergence and collection.
- [ ] Revisit PPZ near top-left X=9499/Y=580, moving vertically and horizontally;
      check the background transition and bottom edge for garbage.
- [ ] Listen to music, FM effects and PCM at normal settings; change music/effects
      volume independently and judge their relative balance.
- [ ] Finish an act; watch score, time and ring bonuses count down with readable
      changing digits, then rewind across the tally and resume forward.
- [ ] Inspect PPZ object `2D` for the correct stage-local name and intact art.
- [ ] Hold Escape in Sonic CD; check the shelf card and act label, resume the
      same game, change settings while suspended and check them after resume;
      end the suspended game and launch again without an unknown-game crash.

## Coordinator completion fields

Historical preflight handoff (superseded by the final continuation below):
restore PowerShell, run composed integration checks, then user manual acceptance. Validation owner stopped at the material prerequisite failure;
no source/test/skip-policy changes, branches, staging, commits or pushes occurred.

- Integration source: dirty HEAD `25659a9702ccf9a388fc75db9d05c16c9769efcc`;
  pre-run tracked binary diff SHA-256
  `12f080932e695a25ea8606d6c7ce38bdcdaf56c66413a0102e845d327ae7004c`.
  New-file inventory (excluding preserved user `USA/` and `crash-reports/`):
  - `docs/architecture/validation/2026-11-15-sonic-cd-playtest-fixes.md`
  - `src/main/java/com/openggf/game/internal/BackgroundRowLayout.java`
  - `src/main/java/com/openggf/game/soniccd/zones/ppz/PpzBackgroundRows.java`
  - `src/test/java/com/openggf/game/soniccd/TestPpzBackgroundRowSampling.java`
  - `src/test/java/com/openggf/game/soniccd/TestSonicCdObjectFixPictures.java`
  - `src/test/java/com/openggf/game/soniccd/TestSonicCdResultsPictures.java`
  - `src/test/java/com/openggf/game/soniccd/audio/TestCdPresentationScale.java`
- Required selection: 4,947 candidate classes, all bounded smoke categories plus
  guards, two workers. Estimated about 30 minutes; default execution limit
  40 minutes excluding queue wait, with a 10-minute no-output limit.
- Exact attempted command (2026-11-15):

  ```bash
  env -u DISPLAY -u WAYLAND_DISPLAY \
    JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
    PATH="${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
    LUA_BIN=/usr/bin/lua5.4 \
    JAVA_TOOL_OPTIONS=-Dsoniccd.media.root=${REPO_ROOT} \
    python3 tools/testing/run_categories.py \
      --base 25659a9702ccf9a388fc75db9d05c16c9769efcc \
      --workers 2 --headless-gl surfaceless-egl --run
  ```

- Outcome: exit **2**, before compilation or test admission:
  `Category validation failed: Prerequisite check failed before tests: PowerShell on PATH: [Errno 2] No such file or directory: 'pwsh'`.
  Lua 5.4 and Maven/Java 25 probes did not fail. No run ID, category results
  directory or final `VERDICT` was produced. Zero tests executed; skip counts
  and failure identities are unavailable, rather than zero-skip coverage.
  No exact focused coverage was rerun after this material blocker. All requested
  audio, PPZ, object, results and suspension classes still need integrated evidence.
- Inputs: CD property explicitly supplied to fork JVMs; cue
  `USA/Sonic CD (USA).cue` SHA-256
  `2095ccb49d635333ed0dee8bf12b8b43f61a2330dd2fdd24b6a9189e1195fc7e`.
  This hashes the cue only, not the complete disc. Default native recordings at
  `${HOME}/IdeaProjects/native` are absent; native-reference coverage remains
  unavailable. Read-only wrapper discovery found S1/S2 REV01, but **S3K is missing**;
  also missing optional KIS2 and S1/S2 REV00 properties. Required S3K coverage must
  be resolved or explicitly reported before accepting a later smoke verdict.
- Results follow-up: existing `target/results-review-fixed.log` ends in
  `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`,
  finished `2026-11-15T17:41:20Z`, Maven time 2m15s, queue waited 0s.
  Exact original worker command/source fingerprint is not in that log; this is
  supporting worker evidence, not reused integrated coverage. Prior reproduction
  failure remains in `target/results-review-repro.log` (one rendered case failed).
  Current rendered test seeds score/ring/time `123400/1200/50000`, advances
  20 tally frames to `126600/0/48000`, and requires exact equality of all
  **320 × 224 = 71,680 pixels** after restoring score and line snapshots,
  both after a later score upload and after tallying. This restores the same
  three live results-line instances and shared bonus state; it does **not** prove
  destruction/recreation of the results graph or end-to-end rewind-history playback.
- Static checks: `git diff --check` passed. Scoped citation check on changed
  existing files and new source files exited 0: near=34, ok=45,
  unresolved-file=19, unverifiable=84. This is not a globally clean citation verdict.
- Baseline attribution: no test failure was reached; no baseline checkout/build
  created. Parent owns subsequent test-failure attribution with matched inputs.
- Timing for this attempt: total command wall **0.778 seconds** including
  preflight; test execution **0 seconds**; test queue admission never reached;
  tree/activity wait not separately instrumented; user wait **0 seconds**.
  Other investigation/documentation time is unmeasured.
- Live jobs: **none**. Owner: sole integration validation agent. Blocker:
  missing `pwsh`; next action belongs to parent: restore prerequisite and S3K
  input, then run the same required selection and inspect exact focused coverage.
- James's manual acceptance / outstanding observations: **pending**.
- Final branch head unchanged; no delivery link created; keep unmerged.

Documentation policy: fold this unreleased work into the existing Sonic CD
preview entry. Root release index, configuration surface, guides, skills and
intentional discrepancy state are unchanged. For a later source commit that
includes only the versioned changelog, use
`Changelog: n/a: release note recorded in CHANGELOG.0.8.prerelease.md`;
other trailers must match the coordinator's actual staged paths.

## Integrated validation continuation — final handoff

This continuation supersedes the earlier prerequisite-blocked state above; the
original failed preflight and worker evidence remain preserved. Sole owner: test
sub-agent. No source/test/skip-policy changes, branch changes, worktrees, commits,
pushes or delegates. `USA/` and `crash-reports/` were preserved. Only this artifact
was edited after the jobs completed. At this historical handoff manual acceptance and parent triage were pending;
final automated attribution is recorded at the top and below.

### Repaired prerequisites and inputs

Bounded read-only searches covered user-local tooling, `/opt`, and likely
project/download/media locations. No existing `pwsh` was found. Installed the
current official non-prerelease GitHub release, PowerShell **7.7.0**, at
`${HOME}/.local/opt/powershell/v7.7.0/bin/pwsh`; it successfully reports 7.7.0.
No sudo or system package changes were needed. Official source:
[PowerShell v7.7.0](https://github.com/PowerShell/PowerShell/releases/tag/v7.7.0),
using Microsoft's [portable archive method](https://learn.microsoft.com/en-us/powershell/scripting/install/alternate-install-methods).
Archive `powershell-7.7.0-linux-x64.tar.gz` SHA-256:
`c89d9c682d2c403dfe726f3df948e359675235a5bd6fab535776de69729ca0cb`.
Verified against the same release's publisher `hashes.sha256` (UTF-16 file) and
GitHub release-asset digest before extraction. Downloads/checksums remain in the
user-local installation directory. PATH was supplied per invocation, without
changing shell startup files or installing a system command.

Verified S3K SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` at original path
`${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods/s3k.gen`.
A second existing package in `Downloads/oggf test/` also matched; it was not used.
Root `Sonic and Knuckles & Sonic 3 (W) [!].gen` instead hashes to
`b711a909cce238ca4af3e517a2edca306228efa5` and was not selected.
No ROM was renamed, copied, linked, deleted or downloaded. Smoke discovery from
the supplied original package directory found S1 REV01, S2 REV01 and S3K.
KiS2 and S1/S2 REV00 remain unavailable. CD media root stayed exactly
`${REPO_ROOT}`; only USA media is present. Native CD recordings,
Europe and Japan media are absent; no search/download of additional media was attempted.

### Source and execution identity

Dirty HEAD/base: `25659a9702ccf9a388fc75db9d05c16c9769efcc`.
Pre-run tracked binary diff SHA-256 remains
`12f080932e695a25ea8606d6c7ce38bdcdaf56c66413a0102e845d327ae7004c`.
Source-only SHA-256 before focused testing and after smoke:
`8c6d4e4f6e559b03213155e601c759bcd962c6198acda1a702e996b98d810537`.
This hashes sorted unique tracked/untracked `src` files as path, NUL, contents,
NUL; it excludes user media, crash reports, this artifact and generated target files.
Runner working-tree fingerprint: `4cf6168c069a1ad58448fb955fdaa0941f2201422cfe3258b33a5b8b26703e92`.
The runner completed without a tree-change rejection; source-only hash also matched.
These results describe the integrated dirty source, not an immutable tested commit.

Both jobs used Java 25 at `${HOME}/.jdks/corretto-25.0.4.1`, Maven from
`/usr/share/idea/plugins/maven-plugin/lib/maven3/bin`, explicit surfaceless EGL,
and unset `DISPLAY`/`WAYLAND_DISPLAY`. No baseline build was made: parent owns
matched-baseline attribution, per this continuation's scope. Observed failures
must not be labeled inherited or regressions without that comparison. Small
pixel differences are reported as observations, not automatically gameplay defects.

### Composed focused run

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
  PATH="${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dsoniccd.media.root=${REPO_ROOT} \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods/s3k.gen' \
  -Dtest=TestCdPresentationScale,TestCdAudioMixGains,TestCdFmSoundEffects,TestCdPcmDriverNative,TestCdStageMusicNative,TestPpzBackgroundRowSampling,TestPpzPeriodsScroll,TestSonicCdCommonObjects2,TestCdObjectRegistryStageScope,TestSonicCdObjectFixPictures,TestSonicCdResultsPictures,TestSuspendedGame,TestSuspendedSettings,TestMasterTitleSuspendedGame,TestGameLoopSuspensionOwnership,TestEngine,TestArchitecturalSourceGuard test
```

Exit **1**, `BUILD FAILURE`: **274 tests, 1 failure, 0 errors, 29 skips**;
244 passed. All 17 requested classes produced fresh XML. Maven time **1m18s**,
finished `2026-11-15T17:47:09Z`; queue waited **0s**. Wrapper has no category
run ID/verdict. Non-Maven launcher overhead was not separately instrumented.
S1/S2 were not discovered for this focused invocation; none of the selected
cases skipped for those inputs. Fresh XML was preserved before smoke in
`target/soniccd-integrated-validation/focused-reports/`; log:
`target/soniccd-integrated-validation/focused.log`.

| Class | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| `TestEngine` | 36 | 0 | 0 | 0 |
| `TestGameLoopSuspensionOwnership` | 56 | 0 | 0 | 0 |
| `TestSuspendedGame` | 3 | 0 | 0 | 0 |
| `TestMasterTitleSuspendedGame` | 15 | 0 | 0 | 0 |
| `TestSuspendedSettings` | 13 | 0 | 0 | 0 |
| `TestPpzBackgroundRowSampling` | 3 | 0 | 0 | 0 |
| `TestPpzPeriodsScroll` | 1 | 0 | 0 | 0 |
| `TestSonicCdCommonObjects2` | 17 | 0 | 0 | 0 |
| `TestSonicCdObjectFixPictures` | 1 | 0 | 0 | 0 |
| `TestSonicCdResultsPictures` | 1 | 0 | 0 | 0 |
| `TestCdAudioMixGains` | 4 | 0 | 0 | 0 |
| `TestCdFmSoundEffects` | 3 | 0 | 0 | 2 |
| `TestCdPcmDriverNative` | 16 | 0 | 0 | 16 |
| `TestCdPresentationScale` | 2 | 0 | 0 | 0 |
| `TestCdStageMusicNative` | 11 | 0 | 0 | 11 |
| `TestCdObjectRegistryStageScope` | 3 | 0 | 0 | 0 |
| `TestArchitecturalSourceGuard` | 89 | 1 | 0 | 0 |

The single failure is
`com.openggf.tests.TestArchitecturalSourceGuard#sonicCdProductionSourcesDoNotCiteGenesisPlusGxSource`:
`CdFmSoundRequestService.java:37,39,40` cites Genesis Plus GX and source filenames.
The 29 skips classify as **allowed optional-fixture / soniccd_native absent**:
2 FM-effect, 16 PCM-driver and 11 stage-music cases; zero unclassified,
required-skipped or undeclared-input skips in this focused set. Exact expanded
skip identities/messages and classifications are preserved in
`target/soniccd-integrated-validation/focused-skips.json` and the fresh XML.
Entirely skipped native classes supply no parity evidence. The picture tests
and the PPZ scroll test executed; all scoped functional checks passed except
the source guard. This is not an overall green verdict.

### Required change-based smoke

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
  PATH="${HOME}/.local/opt/powershell/v7.7.0/bin:${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
  LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dsoniccd.media.root=${REPO_ROOT} \
  python3 tools/testing/run_categories.py \
  --base 25659a9702ccf9a388fc75db9d05c16c9769efcc \
  --workers 2 --headless-gl surfaceless-egl \
  --rom-directory '${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods' \
  --run
```

Run ID **`20261115T174728Z-1cfa602f`**, exit **1**. Preflight and preparation
passed; 4,947 candidate classes, **99/99 ordinary batches and 1/1 guards**
completed. No timeout. Preparation **42.49s**, ordinary **2039.66s**, guards
**211.83s**: recorded execution total **2293.98s (38m13.98s)**. Printed queue
wait **0s** throughout; preparation build-activity wait **0.000013s**.
User wait **0s**; command wall overhead/tool probes and investigation/documentation
time are not separately instrumented and are not counted as test execution.
Original estimate was about 30 minutes, default budget 40 minutes.

| Lane | Tests | Failures | Errors | Skips | Allowed | Unclassified | Required skipped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ordinary | 40173 | 40 | 26 | 601 | 491 | 100 | 10 |
| guards | 858 | 3 | 0 | 0 | 0 | 0 | 0 |

```text
VERDICT FAILED: head=25659a9702cc source=4cf6168c069a change-based 4947/4947 candidate classes; ordinary 99/99 batches; guards 1/1 batches; tests=41031 failures=43 errors=26 skipped=601 (allowed=491 unexplained=110); missing ROM properties=kis2.rom.path,sonic1.rev00.rom.path,sonic2.rev00.rom.path; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false,soniccd_media=true,soniccd_native=false,soniccd_stamp_ledger=false; bounded smoke selection, not a full-suite pass
```

At the failed-run handoff, diagnostics were at
`target/category-tests/20261115T174728Z-1cfa602f/`:
`plan.json`, `preparation.json`, every command array, `results.json`,
`status.json`, rolling logs and failure reports. Overall log:
`target/soniccd-integrated-validation/smoke.log`. These diagnostics were retained for attribution; their failed identities are
preserved below before category-run acknowledgement. `results.json` retains all
69 failed/error case identities and all 601 skip identities; omitted counts
are zero. The 110 unexplained skips are 100 unclassified and 10 required-skipped.
The required skips concern absent QQZ native capture (1), Europe disc refusal
cases (5), and Europe/Japan R3/R4 PCM cases (4), despite the coarse CD-media
capability being true. Many unclassified skips name missing KiS2/REV00 inputs.
No skip-policy change was made; these remain incomplete coverage.

Material handoff: three guards flag current CD changes (camera-relative draw
metadata for `CdRobotGenerator`/stale `PpzBoulder`, results draw-cache fields
`drawnRings/drawnScore/drawnTime`, and the Genesis Plus GX citation). The broad
failures also include unavailable Europe/Japan media, missing KiS2, gameplay
session/service ownership, renderer pixels, mod rewind, and S3K route assertions.
Environment-related observations and actual behavior failures require parent
triage; no blanket baseline/inherited attribution is claimed. In particular,
`TestEngineSceneFocusPresentation#privacyPresentationRestoresFramebufferMasksAndRendererState`
is a suspension-adjacent pixel failure needing review.

Exact failed/error identities (full messages/stacks in retained `results.json`):

- ordinary:
  - `com.openggf.TestEngineSceneFocusPresentation#privacyPresentationRestoresFramebufferMasksAndRendererState` (failure).
  - `com.openggf.data.cd.TestSonicCdDiscMedia#readsRegionalDirectoriesDataAndAudioWithoutLoadingVideos(String)[2]` (error).
  - `com.openggf.data.cd.TestSonicCdDiscMedia#readsRegionalDirectoriesDataAndAudioWithoutLoadingVideos(String)[3]` (error).
  - `com.openggf.game.sonic2.objects.TestSonic2ObjectBugFixes#mtzCogHighSpeedLeftwardReleaseStillSkipsFoldedSiblingSideStop` (error).
  - `com.openggf.game.sonic3k.objects.TestAizCollectorPublicRewind#publicIntroBeforeInitRoundTripsWithoutACollector(boolean)[1]` (error).
  - `com.openggf.game.sonic3k.objects.TestAizCollectorPublicRewind#publicIntroBeforeInitRoundTripsWithoutACollector(boolean)[2]` (error).
  - `com.openggf.game.sonic3k.specialstage.TestS3kSpecialStageSpritePixels#planeAHudPlayersAndRearShadowShareOwnershipAndPreserveTheCaptureFramebuffer` (failure).
  - `com.openggf.game.soniccd.audio.TestCdFmSoundAssets#actualMediaAdmitsOnlyTheMeasuredUsaMdinitAndDetachesItsEntireBank(CdReleaseVariant)[1]` (error).
  - `com.openggf.game.soniccd.audio.TestCdFmSoundAssets#actualMediaAdmitsOnlyTheMeasuredUsaMdinitAndDetachesItsEntireBank(CdReleaseVariant)[3]` (error).
  - `com.openggf.game.soniccd.media.TestCdSystemProgramSource#configuredEuropeIsNotAnOriginalUsaSystemSource` (failure).
  - `com.openggf.game.soniccd.media.TestCdSystemProgramSource#configuredJapanIsNotAnOriginalUsaSystemSource` (failure).
  - `com.openggf.game.soniccd.media.TestCdTimeWarpPrograms#originalMediaDetachesOnlyTheAuthenticatedUsaProgramsAndKeepsFileBackedDomains(CdReleaseVariant)[1]` (error).
  - `com.openggf.game.soniccd.media.TestCdTimeWarpPrograms#originalMediaDetachesOnlyTheAuthenticatedUsaProgramsAndKeepsFileBackedDomains(CdReleaseVariant)[3]` (error).
  - `com.openggf.game.soniccd.media.TestSonicCdProgramMedia#allStageHeadersNativeFilesAndCatalogueComponentsMatchDirectUserBytes(String)[2]` (error).
  - `com.openggf.game.soniccd.media.TestSonicCdProgramMedia#allStageHeadersNativeFilesAndCatalogueComponentsMatchDirectUserBytes(String)[3]` (error).
  - `com.openggf.game.soniccd.release.TestSonicCdRetailMedia#capturedWholeDiscMatchesTheIndependentlyDerivedProfile(String)[2]` (error).
  - `com.openggf.game.soniccd.release.TestSonicCdRetailMedia#capturedWholeDiscMatchesTheIndependentlyDerivedProfile(String)[3]` (error).
  - `com.openggf.graphics.TestBufferStagedTextureUploads#streamOrphansWhenFullAndGrowsForLargeUploads` (failure).
  - `com.openggf.graphics.TestNativeSpriteOwnershipPixels#defaultStencilBackupAcceptsNormalized32BitDepthAndPreservesBothImages` (failure).
  - `com.openggf.graphics.TestNativeSpriteOwnershipPixels#existingStencilContentsBindingsAndBothFaceStatesSurviveRepeatedScopesAndFailures` (failure).
  - `com.openggf.graphics.TestNativeSpriteOwnershipPixels#layeredScopesRestoreForeignStencilAndFaceStatesAfterDrawAndFailure` (failure).
  - `com.openggf.graphics.TestSpriteSatReplayPixels#bothGpuBackendsRenderWholeAndStripCachesAndShadowGeometry` (failure).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#proxyMarkersNeverInvokeForeignHandlersAndValidationContinuesAfterFindingAnOwner` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#genuineRecreationRefreshesTheSelectedRawPublication(boolean, boolean)[1]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#genuineRecreationRefreshesTheSelectedRawPublication(boolean, boolean)[2]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#genuineRecreationRefreshesTheSelectedRawPublication(boolean, boolean)[3]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#genuineRecreationRefreshesTheSelectedRawPublication(boolean, boolean)[4]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#actualRootIdentityAnchorsTheResolvedPublisherIncludingOwnedProjections` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#genuineOwnedProjectionRefreshesChipWithoutClaimingCreatorState` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean, String)[1]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean, String)[2]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean, String)[3]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean, String)[4]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean, String)[5]` (error).
  - `com.openggf.mods.code.TestKis2NativeRewindPublicationOwnership#inventoriesValidateBeforeReservationAndIdenticalDuplicatesKeepDeduplication` (error).
  - `com.openggf.mods.code.TestSampleFlappyIntegration#rewindRestoresAndResimulatesScoreAndLiveRecycleExactly` (failure).
  - `com.openggf.mods.code.TestSampleFlappyIntegration#rewindAcrossFirstControllerUpdateKeepsOneControllerAndSixStablePipes` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#tailsJoinsInTheFieldAndTheTravellingShopReturnsToTheSameSpot` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#startupIsPlayableAndArrivalDialogueAndRestStayInTheField` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#greenHillMysteriesUseRealInteractionsAndPersistThroughContinue` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#physicalTrailsConnectFieldsAndBattleUsesTheEncounterField` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#allStoryLandmarksHavePlayableInteriorsWithIndependentGatesSavesAndRewards` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#travellerDialogueWaitsForTheDiscoveryOrRescueItDescribes` (failure).
  - `com.openggf.mods.code.TestThreeIslandsScene#explorationUsesTwoAxesAndStoryDiscoveriesPersistAcrossContinue` (failure).
  - `com.openggf.mods.scene.host.TestPrivateLobbyScreenRendering#actualLobbyAndFullTextPixelsSupportMultipleReadablePages` (failure).
  - `com.openggf.tests.TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost` (failure).
  - `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[2]` (failure).
  - `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#placedMinibossOrdinaryHitsDriveDefeatResultsAndReleasedCnz2(Row)[4]` (failure).
  - `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[2]` (failure).
  - `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice(Row)[4]` (failure).
  - `com.openggf.tests.TestS3kCnz1MinibossCompletionEdge#truncatedAfterApproachNeverCompletesTheEdge` (failure).
  - `com.openggf.tests.TestS3kFreshLevelRngState#actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int, int)[2]` (failure).
  - `com.openggf.tests.TestS3kIcz1PhysicalLowerPlatformReplay#nativeSonicContactsAndRidesTheLowerPieceWithTwoFreshWorldReplays` (failure).
  - `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[1]` (failure).
  - `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` (failure).
  - `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` (failure).
  - `com.openggf.tests.TestS3kMhzAct2EntryHeadless#coldEntryPressesLiftsAndReleasesNativeTeamWithReplay(WidescreenAspect, String, String)[12]` (failure).
  - `com.openggf.tests.TestS3kMhzAuthoredRoute#freshSonicCompletesActOneAndReplaysItsLiveInteractions` (failure).
  - `com.openggf.tests.TestS3kMhzTailsAuthoredRoute#freshTailsCompletesActOneAndReplaysItsLiveInteractions` (failure).
  - `com.openggf.tests.TestS3kMhzTeamAuthoredRoute#nativePairCompletesActOneAndReplaysItsLiveInteractions` (failure).
  - `com.openggf.tests.TestS3kMhzWideAuthoredRoute#wideSonicCompletesActOneAndReplaysItsLiveInteractions` (failure).
  - `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` (failure).
  - `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` (failure).
  - `com.openggf.tools.TestIcz2ColdCubeBridgeRoute#coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay` (failure).
  - `com.openggf.tools.audio.completerun.s3k.TestS3kCompleteRunReferenceProducer#fixedProducerRejectsEveryMismatchedTypedRequestBeforePublication` (failure).
- guards:
  - `com.openggf.level.objects.TestCameraRelativeDrawGuard#productionCameraReadersDeclareTheirProjectionPolicy` (failure).
  - `com.openggf.level.objects.TestDrawPathFieldWriteGuard#productionDrawPathsMatchReviewedCachesAndFrozenLogicalDebt` (failure).
  - `com.openggf.tests.TestArchitecturalSourceGuard#sonicCdProductionSourcesDoNotCiteGenesisPlusGxSource` (failure).

Final state: **no live jobs**. Test sub-agent's authorized environment repair,
focused execution and required smoke execution are complete. Parent owns failure
triage, matched-baseline attribution and any implementation/guard-metadata changes,
then feature-branch delivery for James's still-pending manual acceptance. No merge
is authorized, and neither failed run certifies acceptance.

## Scoped guard correction pass — 2026-11-15

Owner: implementation sub-agent. One correction pass in the existing shared
branch/workspace; no branch/worktree, delegates, commits or pushes. This section
was added only after the focused job finished. Ordinary failure attribution stays
with the separate baseline owner and its isolated archive/output tree. No broad
category rerun or acceptance verdict is claimed. Original lane totals, failed
identities and diagnostics above remain unchanged.

### Contract review and edits

- Camera metadata: `CdRobotGenerator.appendRenderCommands` reads camera X to
  select section art. Added `CameraRelativeDraw.Use.ART_SELECTION` and declared
  it on the generator and `PpzBoulder`. This accurately distinguishes section
  art selection from culling or screen positioning; both retain world-space
  projection. `ObjectMotionPresentation` treats only `SCREEN_POSITION` as screen
  anchored, so the new metadata preserves the existing follow policy.
- **Camera guard blocker:** the original `PpzBoulder has stale
  @CameraRelativeDraw` finding is a scanner false positive, not evidence that
  its camera read disappeared. `CdBoulder.appendRenderCommands` calls protected
  `boulderArtKey()` virtually; the `PpzBoulder` override reads
  `services().camera().getX()`. `TestCameraRelativeDrawGuard.readsCamera` follows
  the bytecode call's declared owner, never resolving that virtual call to the
  concrete subclass. Removing the annotation or calling it `CULL_ONLY` would
  misdescribe real behavior. Kept the valid declaration and the failing guard.
  Resolving virtual dispatch in the scanner is outside the authorized
  production-metadata/comments and approved-inventory scope. No production
  wrapper/extra draw override was introduced just to satisfy the scanner.
- Draw purity: explicitly approved exactly one write site each for
  `CdResults#drawnScore`, `#drawnTime`, `#drawnRings` in `reviewed-caches.txt`.
  These are memo keys for copied digit tiles/GPU uploads, read only to avoid
  redundant uploads. `bonus()` in update owns score/bonus advancement. Existing
  `RewindTransient` and `afterGenericRewindStateRestored` invalidate all three
  markers; no cache or gameplay implementation was changed. This is the
  user-authorized reviewed addition to the normally shrink-only inventory,
  not an allowance for logical draw writes. Rendered restore verification ran.
- Audio source architecture: replaced the production comment's external-source
  derivation with the engine's digital output scaling:
  `FastYm2612Chip.renderInternalFrame` uses `(channelOut[ch] * 3) >> 2`;
  `VirtualSynthesizer` uses `MASTER_GAIN_SHIFT = 1`. CD contributions join after
  that mix, hence the same `3/4 * 1/2 = 3/8` presentation gain. Native/disassembly
  retains ownership of player/device arithmetic. Constants, gain arithmetic,
  native behavior and the source prohibition guard are unchanged. The 3/8 gain
  matches the engine's FM output scale and headroom. The console mixes FM,
  CD-DA and PCM in hardware. Their analogue mix has not been calibrated against
  a physical console.

### Exact external audio evidence retained

The removed production comment contained the following external-source account.
It is preserved verbatim here as historical calibration evidence, not relabeled
as native/disassembly evidence:

```text
Genesis Plus GX 2499cd3e3d1d8b7d0130d8b2825adb4cc608b2c0:
core/sound/ym2612.c YM2612Update emits the 14-bit channel sum unchanged;
sound.c sound_update applies fm_preamp/100 (default 100). pcm.c applies
sample*ENV*pan >> 5 at pcm_volume=100; cdd.c applies the native fader/1024
at cdda_volume=100. system.c audio_update/blip_mix_samples adds these
streams without another relative gain. Our fast FM facade scales the
same 14-bit range by 3/4, then VirtualSynthesizer halves the whole sum.
Hence CD sources need 3/8 too, rather than bypassing that headroom.
This is parity with the reference's MAME/fast FM scale, not an analog
hardware calibration. Its Nuked path uses pin-sum*11, whereas ours uses
pin-sum*8/2 (4/11); that existing core-scale difference remains.
```

This pass verified the OpenGGF scaling above, not the external source account
anew. The previously reported approximately 0.27 dB Nuked residual and missing
analog calibration remain evidence limits. The guard's non-commercial-source
rationale and prohibition on emulator-source-owned production behavior remain
intact; no matching pattern or citation sample was relaxed.

### Focused execution and stopping decision

Tested dirty HEAD `25659a9702ccf9a388fc75db9d05c16c9769efcc`, not a new
immutable commit. Post-run source-only fingerprint (same path/NUL/content/NUL
method described above):
`41821e7d1f000f525b649ad28e6a41c422d1261868bd789b35a3bf0fad832d7b`.
No source edits were made while the job ran.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
  PATH="${HOME}/.local/opt/powershell/v7.7.0/bin:${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
  LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dsoniccd.media.root=${REPO_ROOT} \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods/s3k.gen' \
  -Dopenggf.surefire.reports=target/soniccd-guard-correction/reports \
  -Dtest=TestCameraRelativeDrawGuard,TestDrawPathFieldWriteGuard,TestArchitecturalSourceGuard,TestObjectMotionPresentation,TestSonicCdCommonObjects2,TestSonicCdObjectFixPictures,TestSonicCdResultsPictures,TestCdPresentationScale,TestCdAudioMixGains,TestCdFmSoundEffects \
  test -B > target/soniccd-guard-correction/focused.log 2>&1
```

Exit **1**, `BUILD FAILURE`, finished `2026-11-15T18:33:36Z`:
**123 tests, 1 failure, 0 errors, 2 skips; 120 passed**. All ten selected
classes produced fresh XML in the dedicated reports directory. Queue wait
**0s**; Maven compilation/test execution **2m48s**; user wait **0s**;
launcher overhead and investigation time were not separately measured. No
category `VERDICT` is produced by this focused wrapper invocation.

| Class | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| `TestCameraRelativeDrawGuard` | 2 | 1 | 0 | 0 |
| `TestDrawPathFieldWriteGuard` | 3 | 0 | 0 | 0 |
| `TestArchitecturalSourceGuard` | 89 | 0 | 0 | 0 |
| `TestObjectMotionPresentation` | 1 | 0 | 0 | 0 |
| `TestSonicCdCommonObjects2` | 17 | 0 | 0 | 0 |
| `TestSonicCdObjectFixPictures` | 1 | 0 | 0 | 0 |
| `TestSonicCdResultsPictures` | 1 | 0 | 0 | 0 |
| `TestCdPresentationScale` | 2 | 0 | 0 | 0 |
| `TestCdAudioMixGains` | 4 | 0 | 0 | 0 |
| `TestCdFmSoundEffects` | 3 | 0 | 0 | 2 |

Remaining failure identity:
`com.openggf.level.objects.TestCameraRelativeDrawGuard#productionCameraReadersDeclareTheirProjectionPolicy`:
`[com.openggf.game.soniccd.objects.ppz.PpzBoulder has stale @CameraRelativeDraw]`.
The generator finding is resolved; the boulder scanner limitation is preserved.

Both skips are in `TestCdFmSoundEffects`:
`nativeDriverStateMatchesTheSequencerForEverySoundTestEffect` and
`nativeComparisonRejectsTheSonic3kFrequencyBase`, each reporting
`Native capture absent: ${HOME}/IdeaProjects/native/aud1-fixtures/soundtest-fm`.
These match the existing optional `soniccd_native` input classification; no skip
policy was changed and native parity is not proven. Wrapper discovery reported
no S1/S2 properties, the explicit caller S3K path, and missing KiS2/REV00;
none caused a skip in this selection. USA disc pictures executed under EGL.
`git diff --check` passed after the implementation edits.

Reassessment: stop after this single authorized correction pass at the concrete
camera-scanner blocker. Parent owns any authorization for a bounded virtual-call
scanner correction; do not remove truthful metadata to obtain green. Two scoped
guards now pass, focused functional checks pass, overall scoped validation still
fails. Ordinary baseline attribution and manual acceptance remain with their
existing owners. This worker has **no live jobs** and made no delivery actions.

## Bounded camera guard dispatch correction — 2026-11-15

Owner: implementation sub-agent; second narrow guard correction, now complete.
Changed only `TestCameraRelativeDrawGuard.java`, `ObjDango.java` metadata and
this artifact. No production behavior changes, inventory suppression, branches,
worktrees, commits, pushes, subagents or category runs.

The guard now retains invocation opcodes (including method-handle invocation
kinds), resolving virtual helper calls within the producer hierarchy against
the concrete draw producer. Special/super and static calls retain symbolic
resolution; private declarations are not treated as overridable. Method
identity/visited tracking stays separate from invocation kind. Existing direct,
hidden-helper, stale and inherited metadata checks remain intact. New fixtures
prove an inherited draw reaches an overridden camera-reading helper: truthful
metadata passes, absent metadata fails with the exact class discrepancy.
Super-call, static-hiding and private-helper controls reject false attribution
to unused camera-reading methods.

The first corrected scan recognized the truthful `PpzBoulder` declaration and
uncovered exactly one missing declaration: `ObjDango`. Inspected
`SharedBadnik.appendRenderCommands -> sheetKey -> ObjDango.sheetKey`: camera X
selects SSZ/MMZ section art while drawing remains in world space. Added only
`@CameraRelativeDraw(ART_SELECTION)` and its import to that class. No other
production annotation changes were necessary.

Both focused runs used the artifact's Java 25/Maven/PowerShell/Lua environment,
unset display variables, and the ordinary Maven queue. Exact final command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
  PATH="${HOME}/.local/opt/powershell/v7.7.0/bin:${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
  LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dsoniccd.media.root=${REPO_ROOT} \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods/s3k.gen' \
  -Dopenggf.surefire.reports=target/soniccd-camera-dispatch-correction/final-reports \
  -Dtest=TestCameraRelativeDrawGuard test -B \
  > target/soniccd-camera-dispatch-correction/final.log 2>&1
```

- Initial run (same command, `reports`/`focused.log`): exit 1, 4 tests,
  1 failure, 0 errors/skips, 37.215s Maven time, finished 18:37:31Z.
  Only failure: `ObjDango requires @CameraRelativeDraw`. Three fixture tests
  passed. Evidence retained there alongside the original
  `target/soniccd-guard-correction/` failure and broad failed-run diagnostics.
- Final run: exit 0, **4 tests passed, 0 failures/errors/skips**, Maven time
  1m54s, finished 18:39:50Z. Fresh XML confirms production inventory and all
  direct/helper/inherited/dispatch controls executed. Queue wait 0s each;
  user wait 0s; launcher/investigation overhead unmeasured. No category
  `VERDICT` applies. Missing optional ROM discovery caused no skips here.
- Source: dirty HEAD `25659a9702ccf9a388fc75db9d05c16c9769efcc` on
  `feature/ai-sonic-cd-playtest-fixes`. Source-only SHA-256 (sorted unique
  tracked/untracked `src` paths, path/NUL/content/NUL) before and after final run:
  `4d750258f8e032105fcf4f07f0241c8af630134c322a119be0069a501cf99c65`.
  First run source hash:
  `34e41ed0582a6c1e524fe668a5e410995544605f55358f0bf251d3ed7e60a240`.
  These identify working sources, not an immutable tested commit.
- `git diff --check` passed. No live jobs or material scoped blockers remain.
  Stop at focused success; ordinary matched-baseline triage and manual
  acceptance stay with their existing owners. This does not supersede the
  earlier broad failed verdict or certify gameplay/route acceptance.

## Completed baseline attribution and delivery

Initial 30-class baseline evidence is retained in
`target/soniccd-integrated-validation/baseline/comparison.json` (59 of 66
original ordinary failures reproduced). The subsequent matched-feature attempt
is retained in `matched-feature/` under the same directory, including its
failed classload rerun. Missing runtime bytecode at the time caused
`NoClassDefFoundError` / `ClassNotFoundException` for `Sonic3kLevelInitProfile`,
not an established initializer/source defect; bytecode was present at later
inspection. That collapsed run is not equal-coverage evidence.

Final authority for the remaining observations is
`target/soniccd-integrated-validation/bounded-two-class/comparison.json` and
its `summary.json`, with exact commands, source identities and fresh XML retained
locally. Both sides ran 23 cases, 13 passes, ten matching failures, no errors or
skips, and no changed assertions. The eight `TestThreeIslandsScene` identities
listed in the original smoke failures above all reproduce when runtime
`user.dir` selects the original catalogue/configuration paths. JUnit ROM
properties alone do not configure that catalogue. The two listed
`TestSampleFlappyIntegration` failures reproduce at `installLiveRewind` before
resimulation because the original workspace config uses `rewind.budgetScope=off`;
the archive's bundled default differed. Matching sample sources and test harnesses
were verified. The cog's later pass matches baseline. No source-induced
regression was identified in the compared scope. Original smoke remains FAILED;
missing input/skip coverage was not performed or reclassified.

Validation and implementation owners have finished; no live jobs remain.
Delivery owner finalized the documentation, staged only task files and retained
this feature branch for James's manual checklist. No new test execution is
required for the documentation-only finalization. Static delivery checks and
commit hooks supply documentation/policy evidence only. All original failed
identities and the exact smoke verdict above remain part of this durable record;
regenerable logs and screenshots are not committed.

Final static checks: `git diff --cached --check` and
`python3 tools/disasm/disasm_citations.py check-staged` passed; Markdown fence
balance and relative file-link existence passed for all three changed documents.
No skill files changed; mirror enforcement remains enabled in the commit hook.
After inspection and preservation of the exact failed identities/verdict above
and the existing `target/soniccd-integrated-validation/` summaries, ran
`python3 tools/testing/run_categories.py --acknowledge 20261115T174728Z-1cfa602f`:
`Diagnostics deleted.` This removes that category-run directory only, not the
separately retained focused/baseline/comparison evidence. Acknowledgement does
not change the original FAILED verdict.

The first delivery commit attempt was rejected by the resource-policy hook for
machine-local home paths in this artifact. Normalized only those prefixes to the
placeholders above; no source or validation results changed. Hooks stay enabled.


## Develop reconciliation for parent merge — 2026-11-15

Owner: implementation child. Parent alone performs the authorized final merge.
Feature input `12ac3c6c1595424921e98a2aa395f94663b2bdb4`; fetched develop
`e07b66cc650babe4f22b5b97a05f975910ca6166`. Merged develop into the current
feature branch, without switching branches, worktrees or delegation.

Two conflicts resolved:
- `ObjDango.java`: retain `ART_SELECTION`; `sheetKey()` reads camera X to select
  section art, rather than only culling. No gameplay logic changed.
- `TestCameraRelativeDrawGuard.java`: retain invocation opcode/access-aware
  dispatch from the feature, including method handles, private/static/super
  controls, and retain develop's `HookTemplate`/`OverriddenHook` fixture.
  Both branches' intended override detection remains covered.

All other incoming HCZ publication, shared presentation, source-provenance and
reference changes were preserved as merged. No submodule pin or media changes.
Only the combined guard and this record differ from the feature input beyond
incoming develop changes; Dango retains its feature annotation. Local `USA/`
and `crash-reports/` remain untracked and unstaged.

Focused verification used Java 25, queue ownership and surfaceless EGL. Selection
was bounded to conflict behavior, CD pictures, suspension/presentation overlap
and the four required S3K checks. No broad smoke rerun: the historical **VERDICT
FAILED** and baseline/environment attribution above remain authoritative.

Exact successful command (same normalized paths as the earlier record):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=${HOME}/.jdks/corretto-25.0.4.1 \
  PATH="${HOME}/.local/opt/powershell/v7.7.0/bin:${HOME}/.jdks/corretto-25.0.4.1/bin:/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH" \
  MAVEN_OPTS=-XX:TieredStopAtLevel=1 LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dsoniccd.media.root=${REPO_ROOT} \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Ds3k.rom.path=${HOME}/Downloads/Telegram Desktop/OpenGGF-experimental-windows-x64-with-mods/s3k.gen' \
  -Dopenggf.surefire.reports=target/soniccd-develop-reconciliation/reports \
  -Dtest=TestCameraRelativeDrawGuard,TestDrawPathFieldWriteGuard,TestArchitecturalSourceGuard,TestSonicCdCommonObjects2,TestSonicCdObjectFixPictures,TestSonicCdResultsPictures,TestEngineSuspensionRendering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  test -B > target/soniccd-develop-reconciliation/retry.log 2>&1
```

Initial attempt: identical command without `MAVEN_OPTS`, output `focused.log`;
exit 250 before test execution. Corretto 25.0.4.1 C2 compiler SIGSEGV in
`IfNode::same_condition` while compiling javac `Types$DescriptorCache`.
Original failure evidence remains in that log and local ignored
`hs_err_pid182518.log` / `replay_pid182518.log`. No source correction was
warranted. Retry disables C2 for the Maven compiler JVM only, leaving test JVM
settings unchanged.

Retry exit 0, **176 tests passed, zero failures/errors/skips**, finished
20:55:39Z, Maven execution 4m59s, queue wait 0s. Initial crash elapsed 7.59s,
queue wait 0s; user wait 0s; investigation/launcher overhead unmeasured.
No category `VERDICT` applies to this focused invocation. Fresh XML:

| Class | Tests | Failures/errors/skips |
| --- | ---: | --- |
| `com.openggf.TestEngineSuspensionRendering` | 1 | 0/0/0 |
| `com.openggf.game.sonic3k.TestSonic3kBootstrapResolver` | 6 | 0/0/0 |
| `com.openggf.game.sonic3k.TestSonic3kDecodingUtils` | 3 | 0/0/0 |
| `com.openggf.game.sonic3k.TestSonic3kLevelLoading` | 36 | 0/0/0 |
| `com.openggf.game.soniccd.TestSonicCdCommonObjects2` | 17 | 0/0/0 |
| `com.openggf.game.soniccd.TestSonicCdObjectFixPictures` | 1 | 0/0/0 |
| `com.openggf.game.soniccd.TestSonicCdResultsPictures` | 1 | 0/0/0 |
| `com.openggf.level.objects.TestCameraRelativeDrawGuard` | 4 | 0/0/0 |
| `com.openggf.level.objects.TestDrawPathFieldWriteGuard` | 3 | 0/0/0 |
| `com.openggf.tests.TestArchitecturalSourceGuard` | 89 | 0/0/0 |
| `com.openggf.tests.TestS3kAiz1SkipHeadless` | 8 | 0/0/0 |
| `com.openggf.tests.TestSonic3kLevelLoading` | 7 | 0/0/0 |

The 11 selected simple names execute 12 classes because `TestSonic3kLevelLoading`
exists in two packages. Explicit S3K input was found; discovery reported missing
S1/S2, REV00 and KiS2 properties, but no selected test skipped. CD disc-backed
pictures and suspension rendering executed under EGL; native reference fixtures
and manual acceptance were not rerun.

Tested source: merged working tree with the two parent SHAs above, before the
merge commit existed. Sorted tracked `src` path/NUL/content/NUL SHA-256:
`5b3fa6f7867294349d6dbf0a9caa56029bc896e823281b4fa53d893370f10fed`. Final subsequent edits affect this record only.
The merge commit containing this section records the exact reconciled source.
Installed hooks remain enabled. No live validation jobs or material reconciliation
blockers remain. Next action: push the feature branch, mark PR #332 ready and
hand back to the parent for current CI/review assessment and authorized merge.
