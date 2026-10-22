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

## Directing extension checkpoint

Outcome in progress: Tails accessory fidelity, dynamic cameras, constrained authoring
windows, scene transitions, agent screenplay tooling/skill, original short and promo.
Pinned destinations are engine `develop` at `ca58b4691bbc92bfee66b4b18dfed8cad68beed6`,
Editor `main` at `53722132a07ee409d2740349aeebcaf801de4944`, and Media `main` at
`40ed0c0048484c51db40eac562229b3c4af05203`. Isolated local branches own development;
main workspaces retain their branches and unrelated changes.

Core agent owns model/evaluator/persistence/registration/handover; rendering agent
owns accessory ROM providers and scene rendering/resources; frontend agent owns both
editors and window policy. Lead owns authoring tool/skill, API pin, production sources,
combined validation, integrations and cleanup. Workers do not schedule Maven jobs.
Focused regression checks precede one change-based smoke selection (never the public
release-only full suite). Movie capture and UI pictures use no-device audio and Linux
surfaceless EGL with desktop display variables removed. Media sources will be archived
under `projects/stageplay` in OpenGGF-Media; videos remain in the external production root.

Focused extension validation: Java 25 `test-compile` found one missing checked
exception declaration in the new ROM test, corrected without production changes.
The first combined `-Dtest=TestStageplay*,TestTailsTailPose,TestTailsTailsDirectionalAnimation,TestTailsTailsFlightSelection,TestTailsRendering,TestSpriteManagerMainTailsTailsDispatch,TestDonorTailsTailBanks,TestModApiSignatureSurface,TestModApiPinPolicy`
run used all three absolute verified ROM paths, `-Dopenggf.headless.gl=surfaceless-egl`
and `-Dopenggf.stageplay.hudPictures=true`: 154 tests, zero failures, four errors,
zero skips. Three errors were nested Mockito fixture construction; the fourth added
a set after creating the intentionally immutable ModAssetRoot snapshot. Corrected
fixtures preserve the production semantics. A narrow queued `package` with
`-Dtest=TestStageplayTool,TestStageplayRegistration,TestStageplayRenderer` then passed
18 tests, zero failures/errors/skips, including two added bulk-timeline CLI cases.
Both runs used `-Dmse=off`; packaging completed engine, universal and SDK artifacts.
The candidate pin is regenerated from that surface; no version/topology change.

Preflight initially rejected the host's default Lua version. Explicit
`LUA_BIN=/usr/bin/lua5.4` passed Java 25/Lua/PowerShell checks. The change-based
selection against the pinned engine base contains 3,549 smoke candidates plus
guards, in batches of at most 50. Expected ROM-heavy cost is up to about an hour;
use a 90-minute invocation cap (queue waiting excluded) and the standard 10-minute
no-output stop. Fix attributable regressions narrowly; retain and attribute inherited
failures by identity rather than repeating the broad run.


### Dialogue revision checkpoint

The directing smoke tree remained frozen while dialogue drafts and skill corrections
were prepared outside it. User clarification confirms text reveal effects, speaker
styles and dialogue skins. The bounded extension uses v3 portable data, explicit
speakers, named reveal/skin profiles, custom elapsed reveal offsets and bounded PNG
assets. Core owns schema/evaluator; runtime owns resources/presentation/registration;
frontends own shared editing and desktop controls; lead owns CLI, docs, pins and media.
No new executable mod registry or global graphics filtering is introduced.

The second production revision is retained as a superseded preview, including its
successful codec checks: those checks did not establish creative quality. Revision
three uses active choreography and fixed zoom, with original generated dialogue
panel art and distinct typing profiles. Final focused validation and media review
must cover the new surface before integration; earlier smoke evidence alone does not.


Dialogue candidate checkpoint (captured before the subsequent boundary correction):
`-Dtest=TestStageplay* -Dopenggf.stageplay.hudPictures=true` with all three verified
ROM paths and surfaceless EGL completed 145 tests, two failures and no errors/skips.
The failures were stale test expectations: help still expected schema 2, and the
actor-delete test assumed undo restored selection, contrary to the controller's
separate view-state contract. The corrected command
`-Dtest=TestStageplayDialogueWorkspace,TestStageplayTool,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiJavadocTool,TestModApiSdkPackager,TestModSdkArtifactVerifier,TestSampleModsPackage`
passed all 51 tests, no failures/errors/skips (88 seconds Maven execution, 187
seconds admission wait). The normalized candidate gains 160 additive signatures,
no removals; descriptor/version remain the unpublished 0.7.0 candidate.
`-DskipTests package` then built engine, universal and exact SDK/Javadoc artifacts.
All 18 desktop tests passed against that installed engine, including actual dialogue
and skin forms (3.407 seconds Maven execution, 395 seconds admission wait).

The separate `-Pguards -Dtest=TestArchUnitRules` check has 32 cases and three
failures: two HubMusicController provider rules need matched baseline attribution;
the Stageplay boundary rule identifies a workspace reference to runtime image-path
enumeration and two new HUD references to pure dialogue/camera values. The next
change removes the runtime dependency and narrowly declares the pure HUD owners.
The candidate is not ready for integration until that correction and attribution.

The 51-second schema-3 film was compiled and rendered with the packaged candidate.
Its 3,060 authored timeline rows hold zoom one; reviewed half-second sequences show
entrances, landings, turns, retreats and the closing chase, with named speaker panels.
Both the source and MP4 exports decoded completely; each lossless audio channel has
2,448,000 samples. Full-short browser playback reached 51 seconds at rate one with
3,060 presented frames and zero reported dropped frames. This is authored scene
presentation, not native gameplay qualification or physical audio listening.

Boundary correction after `ffe9492aabb7b988876b9f096079a24159f6196a`: the workspace
now checks portable document asset references without importing the runtime asset
loader. HUD permissions name only the new pure dialogue helper and camera value.
Queued `-Dtest=TestStageplayWorkspace,TestStageplayDialogueWorkspace,TestStageplayHud`
passes 24 tests, no failures/errors/skips. The repeated exact
`-Pguards -Dtest=TestArchUnitRules` finishes 32 tests with two failures and no
errors/skips: `shared_code_does_not_construct_concrete_sonic_provider_classes` and
`shared_layers_do_not_depend_on_game_specific_packages`. Both failure identities
and complete assertion messages equal the matched detached `ca58b4691` baseline:
HubMusicController's three concrete audio-profile constructors and nine total
game-specific dependencies. The Stageplay ownership rule passes; the inherited
music-provider issues remain outside this change.


Upstream reconciliation: destination advanced to `79e5ebea27ca75dd90c17666c5e8783056fe099c`.
Merge `83ce60144` retains the new promo-direction skill and disassembly citation
refresh. Lexical comparison of all 1,041 upstream Java files found no executable
token changes. The sole conflict was a citation inside tail tables moved by this
feature; the table remains in TailsTailPose with the updated citation, without
restoring duplicate constants. Promo guidance now routes staged narratives to
Stageplay, permits deliberate silence/reaction holds, distinguishes engine zoom
from compositor filtering and keeps native evidence requirements. Five relevant
skill mirrors, frontmatter and 27 local links pass checks. Independent behavioral
scenarios cover a quiet dialogue film, a native boss-defeat promo and baked source
zoom shimmer; no blocking contradictions were found.

The original smoke invocation ended with signal status 143 after 64/71 completed
batches (35,093 tests, one inherited GLFW failure, no errors, 88 skips; 4,876.66
seconds recorded execution, queue waits excluded). The in-flight 65th batch did
not finish and is not counted. Recovery derives the exact remaining 349 candidates
from that unchanged plan, preserving the original 50-class partitions, smoke tags,
ROM paths and EGL environment through the shared Maven queue. No passing batch is
repeated or selected candidate dropped. Final guards run separately on the merged
dialogue tree. The interruption's initiating cause is unconfirmed.


### Physical puppet follow-up checkpoint

User steering during final delivery requires default floor support, genuine jump
physics and destination/urgency puppet commands. Engine integration is held for
that implementation. Editor `main` is already delivered at `402a04cc30becaf2f3f78371863f85bf6adf65ef`;
Media `main` is delivered at `7266a51d501e5c74e90ba3e7bca29fed2987d71f`. Their task
worktrees/branches are removed. Revision-three film sources remain preserved;
new feedback requires a new take rather than overwriting that film.

Engine candidate before physics is `074a7479c5eb8959018d50e0b93d928d26e11b12`.
All 718 guard cases completed with five failures, no errors/skips: the two matched
HubMusic rules, two MenuAudioProbeTool singleton-use rules, and the missing native
image resource entry for AuthoredLevelDecoder. A matched detached-base check of
TestProductionSingletonClosureGuard/TestNativeImageResourceGuard is queued; these
three additional failures are not yet attributed. Ordinary recovery has completed
batch 65 (221 tests, no failures/errors/skips); batches 66–71 continue in the frozen
original tree. Root is the sole Maven scheduler.

Ownership: runtime agent implements isolated native playable context/tick/floor
placement plus physics/isolation tests; core owns schema/intent scheduling and
shared resolved performance/resource/render/playback/capture integration; frontend
owns shared workspace, HUD and preview session. Lead owns screenplay/compiler/CLI,
API pins, documentation, revised film/archive, integration and whole-delivery
validation. No worker launches builds or tests. The constructor-bound service
context keeps native defaults unchanged; test direct native-input equivalence,
slope/wall/top-only/stacked floor behavior, short/held jump and landing, donor rules,
seek/repeat/reset, and sentinels proving no live gameplay/audio/camera mutation.


Physics validation checkpoint: all three worker slices are frozen for lead compilation.
The first compile found one malformed shared-menu expression; repaired by frontend.
The second found the remaining DrowningController caller of the newly actor-bound
level-event service; the lead passes its existing player so ordinary fallback remains
unchanged. No physics tests have executed yet. Updated-base preflight passes with
Java 25 and explicit `LUA_BIN=/usr/bin/lua5.4`; the first probe without that setting
failed its Lua prerequisite before running tests.

Matched baseline checks now attribute all five pre-physics guard failures. The two
MenuAudioProbeTool messages match exactly. AuthoredLevelDecoder's missing native-image
resource assertion matches after normalizing only the checkout-root path printed
in the message. The previously matched two HubMusic assertions remain unchanged.
Original smoke recovery batch66 completed:298 tests, no failures/errors, two skips
(opt-in native GL property and SecondStrike air-recovery mod property missing).
Remaining batches67–71 continue; no previously completed batch was repeated.


User naming update: the two authoring modes are **Puppet** (native player movement)
and **Poser** (manual position, animation and mapping frames). Portable v4 still
uses puppet-track presence; old documents remain Poser. Both editor labels/forms,
CLI cast summaries and guides now use those names.

First physics-focused run:40 classes,234 tests,7 failures,2 errors,1 skipped
(opt-in HUD pictures). Existing collision/sensor/Tails tests and API closure passed.
New failures exposed missing pre-tick native jump-edge publication; tick's held
boolean alone does not create the ROM press byte. The facade now follows the native
host's raw/logical input publication. Other repairs: a nested Mockito stub, stable-ID
rather than same-frame command-order assertion, effective S3K profile lookup, and
terrain fixture expectations (a45-degree abrupt angle was correctly cardinal-snapped).
Independent review also found Camera.restore rebinding through the live roster;
an explicit-focus overload now preserves private actor ownership and coordinates.
No native physics constants or trajectories were adjusted to satisfy the tests.

Original ordinary smoke coverage is complete across64 original and7 recovered batches:
37,273 tests,1 failure,2 errors,93 skips. All original/recovery skips were inspected.
The1 failure is the baseline-matched HCZ GLFW pixel prerequisite. The2 recovery errors
are TraceCaptureBootDimensionsTest's two native boot dimensions; a bounded baseline
with explicit surfaceless-EGL passes2/2. Recovery put EGL only in MAVEN_ARGS, which
this Maven invocation did not propagate; the same explicit-flag check on the frozen feature also passes 2/2. These were
launch-prerequisite errors, not engine regressions. The consumed category diagnostics
were acknowledged and removed.
The final change-based plan selects3,558 smoke candidates plus guards; physical-service
changes require renewed affected coverage, unlike the earlier comment-only upstream merge.


Repaired physics check: `python3 tools/testing/maven_queue.py --normal -Dmse=off
-Dtest=TestPlayablePuppet,TestPlayablePuppetRom,TestStageplayPuppetsRuntime,TestStageplayRenderer,TestStageplayPuppetWorkspace,TestStageplayWorkspace,TestStageplayDirectingWorkspace,TestStageplayDialogueWorkspace,TestStageplayHud,TestStageplayTool,TestStageplayPuppets,TestModApiSignatureSurface
-Dopenggf.headless.gl=surfaceless-egl <three verified absolute ROM properties> test -B`
completed 90 tests, one failure, no errors or skips (34.094 seconds Maven execution).
Jump initiation, variable-height ROM jumps, arrival/blocked behavior, seek and both
authoring modes pass. The remaining synthetic landing test compares fractional
centres even though native floor correction preserves subpixel phase; investigate
that expectation without rewriting native physics. A standalone packaged `simulate`
then exposed an active-gameplay camera dependency absent in hosted tests; add fresh
process coverage before accepting the agent-only workflow.

The landing comparison now asserts native foot pixels while preserving fractional
phase. Standalone simulation's actual stack exposed the S3K landing-animation
callback reaching HCZ live player queries. Bound puppets now skip that world-event
publication; ordinary players keep it. New `TestStageplayOfflineSimulationRom`
executes production simulation in a fresh JVM without an Engine session, using S2
Tails and S3K Knuckles ROM terrain/art. It checks movement, actual jump/landing,
every resolved row and identical replay after closing/reopening detached resources.

Queued exact checks `-Dtest=TestPlayablePuppet,TestStageplayOfflineSimulationRom,TestHCZWaterTunnelHandler,TestModApiSignatureSurface`
with explicit EGL and the verified S2/S3K paths pass **34 tests, no failures, errors
or skips** (80 seconds Maven execution, including full source compilation).
The external Editor task tree passes **19 tests, no failures/errors/skips** with
`-Dopenggf.stageplay.desktopPictures=true -Dopenggf.headless.gl=surfaceless-egl`
and the verified S2 path (11.396 seconds Maven execution). It uses the matching
physics artifact before the landing-callback fix; its S2 UI path is unaffected.
Actual Puppet inspector, action menu and destination form images were inspected.
Final engine packaging and broad smoke/guards remain pending.


## Puppet foundation completed smoke and bounded repairs

Engine package at `508915e67d6646b03f27a5e41ff5124b2d926799` built successfully,
including universal SDK and Javadocs. The final revision-four screenplay and
compiled example now reproduce the reviewed 51-second take: 26 native commands
(22 destinations, four jumps), seven shots and speaker-specific dialogue. This is
still detached terrain; the next live-world plan owns placed objects and rings.
The external film/provenance is under `stageplay-delivery/revision-4` outside Git;
Editor and Media followups are local commits `10f0de9` and `89bd3f8` respectively.

The unchanged foundation at `508915e67` completed category run
`20261021T201324Z-6b2c93b4` from pinned destination `ca58b4691bbc92bfee66b4b18dfed8cad68beed6`:
3,559 candidate classes, all 72 ordinary batches plus guards. Command:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base ca58b4691bbc92bfee66b4b18dfed8cad68beed6 \
  --rom-directory <engine-root> --max-minutes 180 --run
```

Ordinary: **37,338 tests, two failures, three errors, 93 skips**, 6,267.05 seconds
recorded lane execution. Guards: **718 tests, ten failures, no errors/skips**,
271.09 seconds. Preparation took 27.04 seconds. These execution figures exclude
queue waiting and are not elapsed delivery time. All skips were inspected: optional
capture/measurement/mod-fixture prerequisites, graphics prerequisites, and routes
without the required placed objects; no missing-ROM skip was accepted as coverage.

The new collision-test error invokes GroundSensor's now instance-owned resolver
with a null reflection receiver. Preserve the recreation assertion using the same
real sensor before and after session replacement. Five new guard failures expose
stale bootstrap-access approvals, direct WorldSession construction, the two class
size ratchets, and a temporary-file pattern. Repairs keep session construction in
GameplaySessionFactory, extract existing geometry caching and boss-state lookup
into their existing collaborators, update exact audited bootstrap call sites, and
use JUnit-owned temporary storage. No guard budgets or scope were relaxed.

The five inherited guard failures remain the two HubMusic architecture assertions,
two MenuAudioProbeTool singleton assertions and missing AuthoredLevelDecoder native
resource declaration, all matched on the pinned baseline earlier. The ordinary HCZ
pixel test still has its matched GLFW prerequisite failure. Trace boot's two errors
and the audio CLI stderr assertion require bounded clean-environment confirmation:
TestSurfacelessEglContext clears the JVM backend property between tests, and the
JAVA_TOOL_OPTIONS launcher notice appears in child stderr. Exact focused commands
use an explicit EGL property and unset JAVA_TOOL_OPTIONS. This is a failed broad
run followed by focused repairs, not an all-green or full-suite claim.


Focused followup (same tree, `508915e67` plus the nine-file bounded repair) used
Java25, explicit `-Dopenggf.headless.gl=surfaceless-egl`, all three verified absolute
ROM properties, and unset DISPLAY/WAYLAND_DISPLAY/JAVA_TOOL_OPTIONS/MAVEN_ARGS:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl <three verified absolute ROM properties> \
  -Dtest=CollisionSystemTest,TestPlayablePuppet,TestPlayablePuppetRom,TestSceneRomLibrary,TestStageplayOfflineSimulationRom,TraceCaptureBootDimensionsTest,TestCompleteRunAudioCli,TestModApiSignatureSurface test -B
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  -Dtest=TestBootstrapModuleProviderCachingGuard,TestArchitecturalSourceGuard,TestNoLeakedTemporaryFiles,TestArchUnitRules test -B
```

The first attempt stopped during test compilation because the new fixture named a
nonexistent helper; replacing it with this class's existing concrete sprite fixed
compilation. No tests executed in that attempt. The retry passed **99 tests, no
failures/errors/skips**, 45.622 seconds Maven execution and one second queue wait.
It covers the same sensor across session replacement, native Puppet motion and
ROM floor/jump/seek, fresh-process offline simulation, ROM library ownership,
API signatures, both trace boot dimensions and all 13 audio CLI tests.

Affected guards ran **112 tests, two failures, no errors/skips**, 40.422 seconds
Maven execution. All five new guard failures are repaired. The remaining two are
exact baseline-matched HubMusic assertions (three concrete constructors and nine
package dependencies); the twelve reported lines, nine unique violation lines,
match the pinned baseline without normalizing source locations. No guard rule,
size budget, singleton allowance or API pin was weakened. The completed broad
run's diagnostics have been acknowledged and deleted; no broad rerun is needed
for these bounded extractions and fixture/audit repairs.


Final environment attribution on pinned baseline `ca58b4691bbc92bfee66b4b18dfed8cad68beed6`:
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`, Java25, no desktop display,
the verified S1/S3K paths, and queued selector
`-Dtest=TestSurfacelessEglContext,TraceCaptureBootDimensionsTest,TestCompleteRunAudioCli`
ran **18 tests, one failure, two errors, no skips** (29.210 seconds Maven execution,
15 seconds queue wait). The two TraceCaptureBootDimensionsTest invocations fail
with `Unable to initialize GLFW` at their line41 and the audio CLI assertion fails
at line182, exactly matching the completed foundation run. The backend-selection
test clears its property for later tests in that JVM. This confirms pre-existing
order/environment sensitivity; the separate clean-environment followup above passes
both dimension cases and all audio CLI cases. No unrelated graphics/audio code was
changed to hide those failures. All observed new failures are resolved; broad
coverage retains the stated inherited failures and 93 skips.
