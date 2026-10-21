# Stageplay implementation plan

**Goal:** Deliver shared Stageplay authoring in-engine and in OpenGGF Editor,
with reusable scene playback and film output.

**Design:** [Approved direction and contract](../designs/2026-10-21-stageplay.md).

**Architecture:** Shared immutable scene model, pure evaluator, editing controller
and bounded store; a workspace bridges engine services to both input/rendering
frontends. ROM scene rendering and controlled gameplay provide runtime adapters.

**Stack:** Java 25, existing Jackson/GL/audio/capture infrastructure, existing
Dear ImGui desktop project. No new UI framework in the engine.

## Ownership and bases

- Engine destination: `<engine-root>`, `develop`, pinned
  pre-task commit `ae305bdf6b66603b8e7907161d62eb383d850be2`.
- Engine task tree: `.worktrees/ai-stageplay`, `feature/ai-stageplay`.
- Desktop destination: `<editor-root>`, `main`, base
  `1686a7189f0cbff96c4e06f277964f952e6223f2`; its own `.worktrees/ai-stageplay`.
- An unrelated main-workspace merge initially blocked the ff-only pull. Its owner
  completed it; fetch and ff-only pull subsequently succeeded at
  `02325fd026ea4a5ba33e4abe5149a9209a234e34`. Reconcile it before combined validation.
- Lead owns contracts, engine lifecycle integration, documentation, validation
  selection, commits and delivery. Workers own bounded, disjoint files below.
- Workers share the task checkouts; only the lead schedules build/test jobs.

## Work

- [x] Core: `com.openggf.stageplay.StageplayDocument`, `StageplayTimeline`,
  `StageplayController`, `StageplayStore` and behavioral tests. Immutable nested
  record contract is in the design; public pure runtime data is candidate Mod API.
- [x] Resources/runtime: resolve ROM art, draw sampled scenes through a small scene
  rendering facade; implement audio cue scheduling and reusable playback, recording
  and export using existing owner/fault/capture boundaries.
- [x] Authoring workspace: common new/open/save/Save As, dirty-document handling,
  property forms, actor/key/camera/caption/audio edits, transport, recording/export
  commands, status, and preview. Expose it through `EditorWindowChrome.Host`.
- [x] Built-in frontend: separate Stageplay HUD and input handler; workspace switch;
  complete authoring commands; consumes Stageplay ticks without terrain edits.
- [x] Desktop frontend: Stageplay workspace/panels in OpenGGF Editor; same facade and
  files; queue all mutations into update. Build against isolated engine artifacts.
- [x] Integration: engine host lifecycle/preview/cutscene entry; mod example and
  candidate API inventory/signature pins; guide, release note and design evidence.
- [x] Verification and review: focused tests, actual ROM art/audio/export, isolated
  visual checks, candidate API/structural guards and appropriate change-based smoke.
- Delivery: reconcile current destination changes, integrate without branch
  switches or unrelated staging, push only destinations, remove accounted-for task
  worktrees/branches and consumed diagnostics.

## Interfaces

Core publishes `StageplayTimeline.sample(document, frame)` with immutable sampled
actors/camera/captions. `StageplayController` exposes document edits, undo/redo,
selection and independent transport. `StageplayStore.read/write/encode/decode`
performs strict version-1 persistence. Exact record signatures are the shared
contract; consumers must not create competing state stores.

The engine `StageplayWorkspace` owns a controller, shared forms, status and host
actions. Both frontend workspaces obtain the same instance from
`EditorWindowChrome.Host.stageplay()`. Runtime playback and export receive a
document snapshot, not the mutable editor controller.

## Validation and review focus

Use relevant behavioral tests first: interpolation and animation phase, backwards
seek and cue admission, save failure/round-trip/malformed input, dirty-state undo,
input ownership, ending/cancel/rewind handover, output frame/sample accounting.
Use source-backed ROM properties with verified root ROM paths. Test disabled/missing
resources as explicit errors, never silent skips presented as success.

Inspect `run_categories.py --base ae305bdf6b66603b8e7907161d62eb383d850be2`
and preflight in the task tree before combined validation. State candidate count,
cost and stop rule then; broad coverage is smoke, never release-only full-suite.
Keep one command session/observer per job. Desktop tests use their isolated target
and private Maven repository; coordinate engine admission for alternate-POM tests.

Pay particular attention to silent loss of frontend-hidden fields, sound firing
while scrubbing, movement-only keys resetting animation, pending input surviving
workspace transitions, stale ROM/GL ownership after load, and export errors
leaving a misleading partial output. A detached/staged cutscene pass alone is not
evidence of native bound-actor direction.

## Validation evidence

All commands used Java 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`) in the
engine task tree, unless noted. ROM arguments named the verified original root
files; all stated focused passes had zero failures, errors and skips.

- Core: `python3 tools/testing/maven_queue.py --lean -Dmse=off
  -Dtest=TestStageplayDocument,TestStageplayTimeline,TestStageplayController,TestStageplayStore test`:
  17 tests passed.
- Combined focused selection: `python3 tools/testing/maven_queue.py -Dmse=off
  -Dtest=TestStageplayController,TestStageplayWorkspace,TestStageplayHud,TestStageplayPlayback,TestStageplayRenderer,TestStageplayCueDispatcher,TestStageplayAudioSamples,TestStageplayCaptureTool,TestStageplayRegistration,TestStageplayNativeBinding,TestCourseControl,TestControlledFrameRuntime,TestGameplayModeContextRewindRegistry,TestEngineModRuntimeLifecycle,TestRewindRegistry,TestEditorWindowChromes,TestEditorProjectMenu,TestEditorProjectWorkspace,AudioManagerCaptureModeTest,TestStageplayHudPictures
  -Dopenggf.stageplay.hudPictures=true -Dopenggf.headless.gl=surfaceless-egl
  -Dsonic2.rom.path=<verified-S2> -Ds3k.rom.path=<verified-S3K> test -B`:
  140 tests passed; 81 seconds execution, no admission wait.
- Follow-up playback/audio cleanup: `-Dtest=TestStageplayPlayback,TestStageplayAudioLifecycle`:
  8 tests passed before the final 60Hz rejection was added.
- Subsequent affected run verified the 60Hz negative, three actual subprocess
  cleanup tests, corrected ROM-visible HUD pictures and all 11 API signature
  checks. Three descriptor-policy tests rejected a new comment in the strict
  descriptor; removing that unsupported comment preserves the unchanged candidate
  topology. Ordinary candidate surface changes update the signature pin only.
- Final focused command: `python3 tools/testing/maven_queue.py -Dmse=off
  -Dtest=TestStageplayWorkspace,TestStageplayLivePlayback,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy
  -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2> test -B`:
  30 tests passed, including the recording budget/failed-stop regressions and real
  LiveHost/native/render/audio handover. 81 seconds execution, no admission wait.
- Engine/SDK package with tests skipped succeeded. API reflection snapshot had no
  unaudited or unannotated types. The demo mod packaged with zero validator findings;
  actual film and imported-WAV sample accounting are in the design evidence.
- Desktop: engine queue wrapper with `-f <desktop-task>/pom.xml
  -Dmaven.repo.local=<desktop-task>/.m2/repository -Dmse=off
  -Dopenggf.stageplay.desktopPictures=true -Dopenggf.headless.gl=surfaceless-egl
  -Dsonic2.rom.path=<verified-S2> test -B`: all 15 tests passed after repairing font
  lifetime. The changed new-scene label's picture test passed separately. Both
  frontend pictures were inspected. Native window focus/HiDPI remain untested.
- Change-based preflight passed with `LUA_BIN=/usr/bin/lua5.4` after the default
  `lua` reported a different version. Broad smoke and guards completed as recorded below.

## Broad run and failure attribution

The change-based plan selected 3,539 ordinary candidate classes in 71 batches,
plus a separate guard JVM. Command at engine candidate
`9ba9a034ec6c3168dad0d12faa1dafee97961d20` (base
`ae305bdf6b66603b8e7907161d62eb383d850be2`):

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
python3 tools/testing/run_categories.py \
  --base ae305bdf6b66603b8e7907161d62eb383d850be2 \
  --rom-directory <engine-root> --max-minutes 120 --run
```

The command process terminated with exit 143 at the clean boundary after batch
42; its cause is unknown. A temporary reviewed continuation retained the complete
plan, original source/compiled fingerprint, locks, aggregate results and remaining
execution budget. It ran batches 43–71 and guards without repeating completed
batches. All 71 ordinary batches completed: **37,217 tests, four failures, two
errors, 87 skips**. Guards: **717 tests, seven failures, no errors or skips**.
Ordinary execution was 6,311.30 seconds, guards 272.41 seconds, preparation 83.38
seconds; these exclude queue waiting and are not elapsed delivery time.

The ordinary failures were attributed as follows:

- `TestAudioPresentationProducer.warmedProducerAllocatesNoFramePacketOrConsumerArray`:
  introduced 320,000 allocated bytes from a per-frame `ArrayList` iterator in the
  new PCM overlay loop. Use indexed traversal; the existing regression verifies
  the warmed zero-allocation contract.
- `TestTitleInputOwnership.captureChordIsConsumedWhileBlockedAndCannotFireAfterReturningWithKeyHeld`,
  `TestZoneLayoutMutationPipeline.levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary`,
  `TestLevelManagerModPatternWindows.everyLoadAndEditorRebuildRegistersModWindowsBeforeStockArtCaching`,
  and `TestOrdinaryTitleScreenCommandEvidence.masterTitleDrawExpandsBackdropAndFitsActionPaneAtEveryWidth`
  all reproduced at baseline `02325fd026ea4a5ba33e4abe5149a9209a234e34` with
  matching assertions/errors. Upstream `ad12b52dc` repaired those fixture contracts;
  it was merged automatically at `597a5b78f`. No runtime conflict was involved.
- `TestCompleteRunAudioCli.freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`
  also reproduced on that baseline with the same `JAVA_TOOL_OPTIONS`: the Java
  launcher prepended its notice to the asserted child stderr. Follow-up commands
  unset that environment variable and pass EGL on the Maven command line.

Baseline attribution used the same queued Java 25/EGL environment and exact test
selectors, not a second broad suite. The title/input plus zone mutation classes
ran 17 tests (one failure, one error); the pattern-window class ran one test (one
error); the exact title evidence and CLI methods each ran one test (one failure).
All four runs had no skips. Execution: 81, 29.282, 26.366 and 28.295 seconds,
respectively; known admission waits were 74, 48 and five seconds for the latter
three. The first wait was not retained.

The new guard failures were the editor/Stageplay package cycle, its new dependency
edges, and an audio source-text fan-out check matching the cue callback's generic
`sink.accept` name. Correct the dependency direction and call the cue callback
`cueConsumer`; do not weaken cycle or audio ownership guards. The other four
failures reproduced exactly on baseline `02325fd02`:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestProductionSingletonClosureGuard,TestArchUnitRules' \
  -Dopenggf.headless.gl=surfaceless-egl test -B
```

That baseline ran 73 tests, four failures, no errors/skips; 37.036 seconds
execution and 79 seconds queue waiting. Both singleton guard failures identify
`MenuAudioProbeTool:41` calling `AudioManager.getInstance`. The two ArchUnit
failures identify `HubMusicController:118` constructing concrete Sonic audio
profiles and `:120` accessing concrete title-music enums. These unrelated
violations remain inherited; they are not waived or fixed by Stageplay.

All 87 ordinary skips were inspected with no omitted cases: 32 inapplicable
InfiniteSonic combinations, 16 unavailable graphics checks, one isolated-OpenAL
prerequisite, 37 opt-in diagnostics/captures/benchmarks or optional fixtures, and
one CPZ spin-tube capture/release assumption abort. That CPZ route remains a
coverage gap. The Stageplay HUD picture check, opt-in in broad smoke, was run
explicitly and inspected in the focused acceptance above. This is bounded smoke
validation, not a full-suite or route certification.

## Corrective validation

At runtime source commit `d740156563c61dfeb896efdaf44a9c4ca87c4224`, a clean
compilation followed by the affected 33-class selection passed **236 tests with
no failures, errors or skips**. Execution was 91 seconds; admission wait was zero.
This includes the warmed zero-allocation audio regression, authored-provider
discovery and bounded/path/game checks, both workspace consumers, native playback,
export cleanup, API signatures and all seven upstream repaired fixture classes.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestStageplayDocument,TestStageplayTimeline,TestStageplayController,TestStageplayStore,TestStageplayWorkspace,TestStageplayHud,TestStageplayHudPictures,TestStageplayPlayback,TestStageplayRenderer,TestStageplayCueDispatcher,TestStageplayAudioSamples,TestStageplayAudioLifecycle,TestStageplayCaptureTool,TestStageplayRegistration,TestStageplayNativeBinding,TestStageplayLivePlayback,TestStageplayExportJob,TestAuthoredLevelResources,TestOgfProjectLevelDecoder,TestEditorProjects,TestEditorProjectValidation,TestEditorWindowChromes,TestEditorProjectWorkspace,TestAudioPresentationProducer,TestCompleteRunAudioCli,TestModApiSignatureSurface,TestTitleInputOwnership,TestOrdinaryTitleScreenCommandEvidence,TestZoneLayoutMutationPipeline,TestLevelManagerModPatternWindows,TestAizMinibossBarrelShotChild,TestOwnedNativeAttackLifetime,TestPresentationSpritePixels' \
  -Dopenggf.stageplay.hudPictures=true -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=<verified-S1> -Dsonic2.rom.path=<verified-S2> \
  -Ds3k.rom.path=<verified-S3K> clean test -B
```

The three refreshed HUD PNGs were byte-identical to the already reviewed images.

Affected guards used the same display-free Java 25 environment:
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards
-Dtest=TestAudioPresentationArchitectureGuard,TestArchUnitRules,TestProductionSingletonClosureGuard
-Dopenggf.headless.gl=surfaceless-egl test -B`.
This ran 112 tests, five failures, no errors/skips (44.424 seconds execution,
61 seconds queue wait). Audio ownership's 38 tests, package cycles and the
dependency-edge ratchet passed. Four failures matched the inherited violations;
the new owner check also rejected compiler-generated enum-array descriptors.

A test-only correction normalizes array targets to their element type before
ownership checks. Rerunning the same guard command with only
`-Dtest=TestArchUnitRules` ran 32 tests, two inherited failures, no errors/skips
(33.994 seconds execution, 29 seconds queue wait). The Stageplay owner, cycle and
edge checks passed. XML failure messages for both ArchUnit violations and the
two singleton violations were compared against baseline by test identity and
matched exactly. There are no remaining introduced validation failures; the
four inherited guard failures remain visible. No cycle exemption or new legacy
cluster member was added.

The engine/SDK package completed with tests skipped in 38.115 seconds:
`python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`.
Thin and universal jars contain the moved workspace and authored-level service
descriptor/provider, with the old workspace class absent. Public Stageplay API
classes remain in the engine jar; the SDK classifier intentionally contains
creator tools, and its Javadoc classifier includes Stageplay's public contract
without internal editor/provider types. The demo also built against the final
universal engine and SDK artifacts through
`python3 examples/build_example.py stageplay-demo --engine <universal-jar>
--sdk <sdk-jar>`, under the shared build activity/worktree lease:
**zero validator findings**.

The final engine jar and POM were installed into the desktop task's private
`.m2/repository` with `install:install-file`. At desktop commit `80b8886`,
the engine queue wrapper then ran:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -f <desktop-task>/pom.xml \
  -Dmaven.repo.local=<desktop-task>/.m2/repository -Dmse=off \
  -Dopenggf.stageplay.desktopPictures=true -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic2.rom.path=<verified-S2> clean test -B
```

All **15 desktop tests passed, no failures/errors/skips**, in 3.668 seconds
execution with zero queue wait. The two refreshed desktop PNGs were byte-identical
to the reviewed images. Its generated default config reference was inspected and
removed. Linux EGL does not certify native window focus, HiDPI behavior or macOS
first-thread launch; those remain explicit coverage limits.

Six local links in the added/updated Stageplay design, plan, guide and demo prose
resolve, and whitespace checks pass. The broad run was acknowledged after its
evidence was consumed; its diagnostics and temporary continuation were deleted.
No raw logs or validation receipts are retained in version control.

## Delivery handoff

Both destinations were fetched and fast-forward checked after verification work:
engine `develop` remains based on `ad12b52dc`; desktop `main` remains based on
`1686a7189f0cbff96c4e06f277964f952e6223f2`. The current engine tree adds only the
test-only enum-array guard correction and this evidence to runtime commit
`d74015656`. Desktop `80b8886` has no pending edits. No build/test jobs remain.

The destination integration must preserve unrelated main-workspace files and
dirty reference submodules. Identical integrated source does not require another
unchanged smoke run. Validate the actual destination push policy, push only
engine `develop` and desktop `main`, then remove accounted-for task trees and
the detached baseline. Final destination commits, push results and cleanup are
reported at task completion; this handoff pins the verified source and limits.
