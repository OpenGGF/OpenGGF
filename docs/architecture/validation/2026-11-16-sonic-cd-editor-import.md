# Sonic CD editor/import and menu media

Owner: implementation sub-agent, OpenAI Codex GPT-6.1-sol. No delegates.
Branch `feature/ai-scd-editor-import`, stacked on `feature/ai-scd-mod-api`
`dc6b3c2b9edd621a0feecfe6d411628d51756f92` (PR #350). No merge authority.
The parallel Stageplay/route-tape checkout is read-only context.

## Contracts and current scope

Hosted media supplies detached menu title pixels and an owned menu audio source.
The shared hub consumes these semantic capabilities without selecting CD by name.
CD title art is decoded through `CdProgramData` from the user's verified USA disc;
a bounded 120-frame presentation clip follows 400 native title-program updates.
These counts select a menu clip only, not gameplay or trace behavior. The decoded
sequence survives closing the disc and never installs a live module or world.

Native editor projects and content import are **not implemented at this checkpoint**.
Do not enable the three-game selectors alone. `EditorProjectWorkspace.adoptLoadedLevel`
flattens the source into `EditorProjectLevel`; `EditorProjectRuntimeModule.prepare`
always exports to `ModZoneLevelData`. `CdLevel` owns extra placement bytes, native
loop/layout semantics and the stage payload; native object art/PLC providers depend
on that payload. The project metadata geometry check also assumes all non-S1 games
use 8x8 chunk grids; CD uses 16x16. A correct native project path must save a bounded
stage/media identity and authored edits, then reopen the disc's native stage and
apply those edits while retaining its native owners. A flat portable export cannot
silently replace it. This is remaining work, not an assertion that CD authoring is
inapplicable. No rewind, widescreen, donor, Stageplay or route-tape files are changed.

## Verification journal

Commands unset DISPLAY and WAYLAND_DISPLAY, use Java 25, `-Dmse=off`, the Maven
queue wrapper and surfaceless EGL. Wrapper reports `soniccd.disc.path` found and
`missing=none`. Scratch: `$AGENT_RUNTIME/task-07hk1OLI`.

- Initial `TestCdHostedTitlePreview` ran one test, no skips, and failed because
  the default hosted capability returned no sequence (`NoSuchElementException`).
- The same disc-backed test passes after implementing the preview: one test,
  zero failures/errors/skips. It checks dimensions, coloured pixels, repeated and
  negative tick access, and bootstrap/world ownership.
- Menu audio regression ran one test, no skips, and failed on the absent hosted
  source. After implementation the focused selection passed **108 tests, zero
  failures/errors/skips**. Includes disc title PCM/independent profiles, no-device
  hub playback preserving world/bootstrap owners, rendered CD card, existing
  editor/import suites, Mod API pin/surface/release tests, GLFW setup guard and
  headless audio ownership. Commands are recorded in the task conversation.
- Master-title render: `cd-master-title.png` in the scratch directory above,
  960x672 (3x native 320x224). Visually inspected: selected CD card contains
  native Sonic/emblem/clouds/water/copyright, with Start and SONIC CD labels.
  No desktop or audio device was opened.
- Review found the generalized audio identifier validator would admit unknown
  names before the null-profile check. An attempted `GameId` resolution introduced two forbidden low-level/runtime
  edges, caught by `TestArchUnitRules` (64 tests, one failure, no skips). That
  approach was removed. Native profiles instead declare their presentation
  identity through the existing audio profile capability; legacy stock-id
  validation and null-profile precedence remain unchanged. No architecture
  baseline or guard was relaxed.

Next: finish audio integration and focused verification, inspect the rendered
master-title picture, implement native editor/import persistence and playtest,
then run the requested smoke with two workers against the exact parent SHA.
No smoke VERDICT, native editor playtest render or delivery-complete claim exists
yet. Keep the worktree and task branch while the stacked PR needs them.

The architecture repair selection passes **64 tests, zero failures/errors/skips**:
`TestSoundTestPresentationHost,TestCdHostedTitlePreview,TestCdHostedMenuRendering,TestHubMusicRomThemes,TestArchUnitRules,TestBroadCatchSwallowingGuard,TestGameModuleRegistryUsageGuard`.
The earlier 108-test selection remains the editor/import, API and ownership
regression evidence; no editor/import production code was changed afterward.
The planned change-based smoke selects **4,978 classes plus guards**, two workers,
40-minute execution bound per Maven invocation, queue waiting separately measured.
Stop on infrastructure failure or new/worse failure; compare any failures with the
exact parent, rather than reporting the parent's known failure totals as a pass.

## Interrupted smoke checkpoint

The smoke on `d75938421620f2d77b0fe2876493865359e21ab1` ended with exit 130.
The runner reports `KeyboardInterrupt`; its sender is not established by the log.
The final result is **VERDICT INCOMPLETE**, not a passing smoke:

- Change-based selection: 4,978/4,978 candidate classes; ordinary **8/100 batches**,
  guards not run; **3,889 tests, zero failures/errors, three allowed skips**,
  zero unexplained skips; missing ROM properties: none.
- Allowed skips: CD native-launch comparison lacks optional native fixtures;
  repeated-playback benchmark lacks its explicit opt-in property; isolated
  OpenAL device test lacks its explicit null-driver prerequisite. The USA disc
  was found and disc-backed menu tests executed in the focused runs.
- Preparation: 43.79 seconds. Summed completed batch execution: 113.41 seconds.
  Completed admission waits total 1,049 seconds; the interrupted final admission
  wait is not included. User-response wait: zero; other wall time is unmeasured.
- Runner source fingerprint: `077295bc6750`; run
  `20261116T192202Z-8f65858a`. It confirms Maven stopped and temporary output was
  cleaned. Results/skip classifications were inspected before acknowledging the
  incomplete diagnostics. No owned smoke or baseline job remains live.

Exact-base comparison on `dc6b3c2b9edd621a0feecfe6d411628d51756f92`
reproduced all **29 failures and two errors** from the parent's known-failure
record. The first method selection reproduced 18 ordinary failures; JUnit
parameterized display names did not select invocations. A corrected seven-method
selection exercised those parameterized cases. The combined 31-identity ledger
is retained in task scratch as `baseline-combined.json`. These are baseline
results, not failures observed in this incomplete smoke; final comparison still
requires the remaining smoke batches.

The task branch is a partial menu-media implementation. Native CD editor
project create/clone/edit/save/reopen/playtest, content import round trips and
an editor playtest render remain unimplemented. The master-title PNG remains
in the retained task scratch directory. Next owner action: implement that
stage-preserving editor/import path and disc-backed failing-first tests, then
run a complete smoke and compare failures against the retained baseline ledger.
Do not infer complete 0.8 authoring support from this draft PR. Keep the task
worktree/branch and scratch `.keep`; no merge authority has been granted.

## Native source persistence foundation (implementation continuation)

Preserved the lead's `1ccd708ada` interrupted-smoke record above. No old smoke
was resumed and no new category run was started. This is a partial implementation
checkpoint, not completion of the four-item creator workflow.

`NativeProjectLevel` supplies a semantic `NativeProjectSource`: bounded media
identity, stage, zone, act, period, program and block-grid side, without paths or
provider names. `CdStageLoader` validates every field against the opened disc
and finite payload catalogue before reading/materializing the selected stage.
`EditorProjectDocument.create` captures that reference, and the checksummed
`.ogfproject` payload retains it across save/open/duplicate and structural copies.
Older flat payloads without the optional reference still use their original
contract. The general `EditorProjectLevel.copyOf` collision-remap guard remains
unchanged. Native documents currently reject the flat runtime/export paths.

An explicit native authoring view omits unused trailing block definitions whose
chunk references are outside the decoded library. `CdLevel.loadChunks` decodes
every addressable definition lying inside the stage file, including trailing
non-library bytes; the flat validator rejects these as `Block references missing
chunk`. Only unused invalid definitions are emptied in the detached palette;
the native source data is unchanged. This view must never replace native runtime
data. Reapplying edits onto a fresh native stage is still required.

Failing-first disc evidence (no skips): initial fixture exposed that native R11A
player start lies outside its camera bounds (`Project start must lie inside level
bounds`). The persistence-only fixture now uses an in-bounds authored start to
isolate the source contract; native start/camera semantics remain a UI integration
obligation. The next run failed at `Project block geometry does not match scd`.
The first implementation then failed on trailing block definitions as described
above. The final source test passes, verifies the saved reference and fresh native
reload, rejects foreign media and a mismatched program, and still proves the
normal flat-copy guard rejects the source stage.

Focused command, on `1ccd708ada` plus this changeset:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdEditorProjectSource,TestEditorProjects,TestEditorProjectValidation,TestEditorProjectWorkspace,TestEditorProjectLifecycle,TestContentImportService,TestRawGlfwTestSetupGuard,TestModApiPinPolicy,TestModApiSignatureSurface,TestModApiReleasePolicy,TestArchUnitRules' test
```

Outcome: **161 tests, zero failures/errors/skips**, verified disc found,
`missing=none`. Log: worktree `target/cd-project-source-focused.log`.
Execution was 2:02 Maven time; queue waiting is separate (see the log), user wait
zero. No full-suite claim. `git diff --check` also passes.

Exact next step: connect source-aware create/clone/reopen in
`EditorProjectWorkspace`, resolve the player-start versus camera-bounds contract,
and add failing-first authored delta replay tests. Preserve secondary subtype
and time mask for moved/new placements explicitly. Native replay must run through
`SonicCdGame.loadLevel` so both currentLevel and currentImage are installed; raw
loader reconstruction alone does not establish runtime owners. Then implement
native playtest, content-import round trips and explicit native export handling.
No creator-facing CD selector was enabled by this checkpoint. Do not mark the PR
ready. No operation has been established as inherently impossible on CD; the
current flat-path rejections prevent an unsafe interim runtime substitution.

The requested edited editor-playtest render is still pending; no new render paths
exist. Final change-based smoke must run once on the finished implementation,
against `dc6b3c2b9edd621a0feecfe6d411628d51756f92`, with two workers and surfaceless
EGL, foreground through VERDICT. The prior run's VERDICT remains **INCOMPLETE**;
no new smoke-to-baseline attribution exists. The retained baseline ledger remains
29 failures + 2 errors. No live task test job remains after this focused run.

## Native authored-state replay continuation

Starting source and origin: `3ce259edf004ba15fd0c0080a121aaf668c3425f`, clean.
No category run was live; no new category run was started on this unfinished tree.

`SonicCdGame.loadProject` validates the bounded reference, reloads through
`loadLevel`, then applies a detached authored snapshot onto the resulting actual
`CdLevel`. The game's `currentLevel` and `currentImage` remain the native owners.
Terrain, patterns, palettes, collision libraries, objects and rings are detached
from the document. Source loop flags, layout windows, stage payload and flags stay
on the native level. Existing placement IDs retain second subtype and time mask
after moves; new IDs explicitly use second subtype zero and the active period's
bit. Unexposed placements for other periods remain retained. Replay refuses
changed layout dimensions/grid and IDs colliding with unexposed placements.
The ordinary flat-copy/export/runtime guards remain in place until the native
playtest integration is connected; this commit does not enable the creator UI.

Failing-first evidence: `target/cd-replay-red.log` ran two disc-backed tests with
zero skips and failed with `NoSuchMethodException` for the new materializer. An
intermediate run reached replay but the test incorrectly indexed the filtered
placement list by native ID (expected time mask 64, observed 32). The assertion
now looks up the stable placement ID. The final test also saves/reopens all edits
before replay and checks native owner identity, layout/pixel edits, object add,
move/delete, added rings, retained ring backing, secondary bytes and loop flags.

Final focused command (starting SHA above plus this changeset):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=$AGENT_RUNTIME/task-07hk1OLI python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdEditorProjectSource,TestEditorProjects,TestEditorProjectValidation,TestEditorProjectWorkspace,TestEditorProjectLifecycle,TestContentImportService,TestRawGlfwTestSetupGuard,TestModApiPinPolicy,TestModApiSignatureSurface,TestModApiReleasePolicy,TestArchUnitRules' test
```

Result: **162 tests, zero failures/errors/skips**, disc found, `missing=none`;
`target/cd-replay-focused.log`. Maven execution 60 seconds; admission was queued
separately (exact wait not captured), user wait zero. `git diff --check` passed.
No public Mod API signature changed. Editor/modding guide, CD status and develop
changelog describe this internal milestone and its remaining integration.

Exact next step: add a failing-first disc-backed source-project runtime test,
expose native materialization through a semantic host capability, and connect
`EditorProjectRuntimeModule` while retaining native zone/act/period initialization
and authored spawn semantics. Then implement source-aware workspace/UI creation,
ContentImportService round trips/export policy, edited playtest render and the
single requested foreground change-based smoke. Items 2–6 are pending. Do not
mark PR #352 ready yet. No new render paths exist. Historical smoke VERDICT is
still **INCOMPLETE**; the retained exact-base ledger is 29 failures + 2 errors,
not a result for this tree. No merge or destination push is authorized.

## Step 2: native project playtest

Starting head `76bfdd4dcf`; origin matched after fetch. The disc-backed runtime
regression failed first with the temporary native-materialization rejection
(`target/cd-playtest-red.log`, one error, zero skips). The semantic
`NativeProjectHost` now validates/resolves the source into engine coordinates,
then reloads through the actual game owner and applies detached edits. Native
round/act numbers are distinct from runtime registry indices; an intermediate
failure caught that mismatch. Authored spawn is retained by the native dynamic
start provider, and the Engine launch uses the resolved zone/act/period.
Flat export and hosts without native materialization still reject substitution.

Focused command: `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=$AGENT_RUNTIME/task-07hk1OLI python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCdEditorProjectSource,TestEditorProjectLifecycle,TestArchUnitRules' test`.
Final result: **54 passed, zero failures/errors/skips**, verified media found,
`target/cd-playtest-green2.log`; Maven execution 1:55, queue wait separate.
An interrupted attempt stopped at test compilation; it supplies no test result.
No category run started. Next: creator game/stage selection, import round trip,
render and the requested final smoke; PR remains draft.

## Step 3: creator stage selection

Starting head `0412bf9d2a`. The UI regression failed first because CD was absent
from game choices (`target/cd-creator-red.log`, one error, zero skips). The creator
menu now chooses CD, zone, and labelled act/period, then copies the native stage
with its source reference and original dimensions. Hosted-media selection verifies
the disc before retiring the current editor. Native starts use terrain coordinates;
portable documents still validate their starts against authored camera bounds.
Clone preserves the native capability. An intermediate label assertion exposed a
missing numeric ARB placeholder declaration; the declaration was added.

Final focused selection is the same eleven-class editor/import/API/architecture/
GLFW selection recorded above: **164 passed, zero failures/errors/skips**, verified
disc found, `target/cd-creator-focused2.log`. Maven execution 1:04; admission wait
approximately five minutes, user wait zero. `git diff --check` passes. No category
run started. Next: import/source round-trip and documented flat export refusal,
then edited playtest pictures and the final smoke. PR stays draft.

## Step 4: content import and export policy

Starting head `6f4c7316ca`. Added the round-trip test before making any format
change. Existing `ContentImportService.inspect` delegates `.ogfproject` to the
store and already preserves the complete checksummed source reference. The new
Past-stage regression exercises save/import/save/reopen/native replay, metadata,
terrain edits and source identity. No new serialization implementation was needed.
It also checks that flat export fails before creating a destination. The editor
and modding guides explicitly refuse flat directory/ZIP export of native projects:
those formats do not carry stage program/PLC/placement context. The source-backed
project format remains the supported round trip; no disc payload is shipped.

Focused command used the established environment/queue/EGL options and
`-Dtest=TestCdEditorProjectSource,TestContentImportService,TestEditorProjectValidation`:
**49 passed, zero failures/errors/skips**, verified media found;
`target/cd-import-focused.log`, Maven 44 seconds, admission wait separately queued.
No category job started. Next: render edited native playtest, then final smoke.

## Step 5: edited native editor playtest rendering

Starting head `8b9e21a646`. Added `TestCdEditorPlaytestRendering`: create an authored
R11A document, save/reopen, launch via `EditorProjectRuntimeModule` and
`HeadlessGameBoot`, run 180 production ticks through native startup, then draw
with the real gameplay renderer. No raw GLFW setup, desktop or audio device.
The visual test explicitly disables synthesis and asserts the null backend.
An initial test expected null audio while boot correctly installed no-device
synthesis; its setup was corrected. Another attempt failed before execution
because the checkout filesystem filled. Preserved baseline Surefire evidence in
task scratch and removed only that inactive task baseline's generated build
outputs. The first frames showed startup title/scroll state with zero changed
pixels; advancing the production loop established ordinary gameplay instead of
bypassing startup or weakening the difference assertion.

Final command uses established queue/EGL/environment options plus
`-Dopenggf.test.output=$AGENT_RUNTIME/task-07hk1OLI`
and `-Dtest=TestCdEditorPlaytestRendering,TestRawGlfwTestSetupGuard`:
**6 passed, zero failures/errors/skips**, verified media found;
`target/cd-editor-render4.log`, Maven 44 seconds. Admission separately queued.
Inspected both 320x224 PNGs: Sonic and native PPZ terrain/water are visible;
the authored foreground block appears on the left only in the edited shot.
**8,226 changed pixels**, same camera `(2208,668)` in both. Authored launch
`(2368,512)` versus native start `(64,1899)`; 180 gameplay ticks precede capture.
Files in task scratch: `cd-editor-control.png`, `cd-editor-edited.png`, their
`.state.txt` sidecars, `cd-editor-render.txt`, and `edited-playtest.ogfproject`.
These are local evidence, not bundled disc assets or route certification.
No category job started yet. Next: the single finished-tree smoke against the
exact base, failure attribution, final documentation/PR ready state.
