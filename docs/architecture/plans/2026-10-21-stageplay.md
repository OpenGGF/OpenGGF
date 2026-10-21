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
- [ ] Verification and review: focused tests, actual ROM art/audio/export, isolated
  visual checks, candidate API/structural guards and appropriate change-based smoke.
- [ ] Delivery: reconcile current destination changes, integrate without branch
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
  `lua` reported a different version. Broad smoke and guards are still pending.

## Checkpoint

2026-10-21: both implementations and focused acceptance are complete in isolated
task trees. Desktop commit `98a9659` is local. Engine implementation commit `56d7d140c` has been reconciled with destination
`02325fd026ea4a5ba33e4abe5149a9209a234e34`. The merge was automatic; shared changelog,
configuration and subsystem prose retain both Stageplay and upstream racing work. All workers are frozen.
No live build jobs. Lead owns combined smoke/guards, delivery and cleanup. No push,
integration or worktree cleanup has completed yet.
