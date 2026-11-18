# Sonic CD editor/import and menu media

Owner: implementation sub-agent, OpenAI Codex GPT-6.1-sol. No delegates.
Branch `feature/ai-scd-editor-import`, stacked on `feature/ai-scd-mod-api`
`dc6b3c2b9edd621a0feecfe6d411628d51756f92` (PR #350). No merge authority.
The parallel Stageplay/route-tape checkout is read-only context.

## Completion status

Steps 2–5 are implemented and verified below. Finished-tree smoke on
`04ba02e06cb59990b4e0b95a6bea3001f8ec702a` completed every selected batch and guards:
**VERDICT FAILED**, solely the exact base's 29 failures and two errors; no new or
worsened failure. All 561 skips are allowed; guards pass 860 tests without skips.
The source-backed creator, playtest and import paths are complete within the
explicit native-dimension/source contract. Flat directory/ZIP substitution remains
refused. The historical incomplete checkpoints below are retained as history.

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

## Step 6: finished-tree smoke and delivery

Tested head `04ba02e06cb59990b4e0b95a6bea3001f8ec702a`; exact comparison base
`dc6b3c2b9edd621a0feecfe6d411628d51756f92`. One finished-tree category run:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=$AGENT_RUNTIME/task-07hk1OLI python3 tools/testing/run_categories.py --base dc6b3c2b9edd621a0feecfe6d411628d51756f92 --workers 2 --headless-gl surfaceless-egl --run
```

Run `20261116T210016Z-56edce30` completed in the foreground. No edits or builds
were made in its worktree while active; no other lane's job was cancelled.

```text
VERDICT FAILED: head=04ba02e06cb5 source=0ce869ca14d8 change-based 4980/4980 candidate classes; ordinary 100/100 batches; guards 1/1 batches; tests=41321 failures=29 errors=2 skipped=561 (allowed=561 unexplained=0); missing ROM properties=none
```

Ordinary: 40,461 tests, 29 failures, two errors, 561 allowed skips. Guards:
860 tests passed, zero failures/errors/skips. This is complete bounded smoke,
not a full-suite pass. Disc-backed tests executed: `soniccd_disc=true`.
Optional audio/native reference files, regional media, BK2/observation fixtures,
and explicit opt-in prerequisites account for allowed skips; no required,
undeclared-input, unidentified or unexplained skip exists.

Attribution compared all 31 test identities, failure/error kind, exception type
and assertion against the retained exact-base ledger (also previously reproduced
on develop `3a868d33d9`). Identity sets are equal, with no new or missing case.
JVM object identity suffixes are normalized in one Three Islands assertion.
Three MHZ route assertions compare `Set.of` checkpoint sets: expected-set display
order varies by JVM, but expected/actual members and missing checkpoints match;
verified the owning assertions before treating those as matches. No route,
physics, rewind or fixture behavior was changed to improve these results.

Failure groups: Three Islands (8), private lobby pixels (1), SSZ opening (1),
CNZ1 miniboss completion edge (5), fresh-level RNG/title owner (1), ICZ1 lower
platform (1), MHZ act-two routes (3), MHZ cold-entry matrix (1), MHZ act-one
Sonic/Tails/team/wide routes (4), DEZ final-fight capture (2), ICZ2 cube bridge (1),
complete-run reference producer (1). Errors: AIZ collector public rewind (2).
No unmatched failure required a further baseline run or branch fix.

Timing: preparation 44.98 seconds; ordinary execution 1,980.29 seconds; guards
176.08 seconds; completed admission waits total 5,177 rounded seconds. User wait
zero; other orchestration wall time is unmeasured. Wait and execution are not
combined as test cost. Final results, failure details and skip classifications
were inspected; compact attribution is retained locally as
`final-smoke-attribution.json`, then runner diagnostics were acknowledged.

Delivery preserves the task branch/worktree for the stacked PR. Final commit
changes this validation record only; tested production/test sources remain at
`04ba02e06c`. No merge, destination push or unrelated file cleanup. Edited PNGs,
state sidecars and the source-backed project remain in the requested task scratch.
PR #352 is to be marked ready for review after this record is committed/pushed.

## SCORE gap investigation: transparent cells expose the world

Starting head/origin `6be140e61f741d72725cb767b191ac77d3b8bfe2`.
The reported garbage between SCORE and its zero in the relocated control is
foreground/background terrain visible through transparent digit cells, not a
pattern ownership collision. No runtime fix was justified by the evidence.

`TestCdEditorPlaytestRendering.uneditedHudMatchesProductionAtTheSameFrame`
boots an unmodified R11A source project at its native start, then an undecorated
production CD session, stepping each exactly 180 times. Its initial execution
passed before any production change: one test, no skips. Consequently this
investigation has **no failing-first defect reproduction**; manufacturing one
would misrepresent the result. The original control is also not fully unedited:
it relocates the start and clears six foreground layout cells before capture.
The edited case paints one of those cells, changing the terrain behind SCORE.

The owning writes are `CdHud.updateDigits` / `writeCells` / `writeDigit`:
`SCORE_DIGITS = $B060 / 32 - $568 = $1B`. Initial score zero clears six two-tile
cells, native tiles `$583..$58E`, before the final zero. Each changed HUD update
uploads its owned sheet through `PatternSpriteRenderer.updatePatternRange`.
The control's blank cells map to virtual IDs `$210AE..$210B9`. Reading each
physical atlas slot with `glGetTexImage` found **zero nonzero CPU pixels and
zero CPU/GPU mismatches in all twelve tiles**, in control, edited, native editor
and production sessions. This excludes both a missing blank upload and a later
corrupting upload at those slots. It does not generalize to other art owners.

Rendering the same frame again with `TRACE_SHOW_GAME_HUD=false`, without a
simulation step, leaves **all 768 pixels** at `x=56..103, y=8..23` unchanged.
The apparent stray marks remain in the world-only control. Native-start editor
and production main HUD regions match **all 3,840 pixels** at
`x=16..111, y=8..47`; the final test also compares the lives region. These checks
run for real surfaceless EGL rendering, not a logic-only headless fixture.

The regression now checks native editor/production HUD pixels, CPU and GPU blank
score cells, and world-only score-gap equality in both relocated captures. It
re-renders control/edited PNGs and writes native editor/production comparisons,
world-only witnesses and `.patterns.txt` readbacks to the existing requested
scratch directory. All are local evidence; no disc-derived pixels are committed.
Only the CD rendering test and this validation record changed. Shared runtime
was untouched, so the conditional new smoke run is not needed; the historical
finished-tree smoke verdict and attribution above remain unchanged.

Final focused verification on the starting SHA plus this test/documentation delta:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=$AGENT_RUNTIME/task-07hk1OLI python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.test.output=$AGENT_RUNTIME/task-07hk1OLI '-Dtest=TestCdEditorPlaytestRendering,TestCdEditorProjectSource,TestEditorProjects,TestEditorProjectValidation,TestEditorProjectWorkspace,TestEditorProjectLifecycle,TestContentImportService,TestRawGlfwTestSetupGuard,TestModApiPinPolicy,TestModApiSignatureSurface,TestModApiReleasePolicy,TestArchUnitRules,TestCdHostedMenuRendering,TestSonicCdModRuntime,TestCdModZones,TestCdModMusic' test
```

**181 passed, zero failures/errors/skips**, verified media found, `missing=none`.
Foreground run, queue wait zero; Maven execution 1:11; user wait zero; other
orchestration time unmeasured. Log `target/cd-hud-focused.log`. The two rendering
tests execute, with native editor/production main HUD and lives comparison.
Manual independent PNG comparison also finds zero score-gap differences against
HUD-hidden witnesses in all four captures. Control and edited images were
visually inspected again; their existing 8,226-pixel terrain difference remains.
`git diff --check` passed. The global citation audit reports historical stale/
unavailable references; this change adds no disassembly citation or pin change.
No category job was started and no runtime or shared renderer was edited.
The branch/worktree and requested scratch evidence remain for PR review.

## Portable native project export

A folder and a ZIP both contain one `native-project.json`. It records a verified
stage reference, a hash of the decoded editor baseline, and authored changes.
Stable placement IDs retain unchanged objects and rings by reference when a
placement moves or is deleted. The importer confirms the stage before applying
the edits, then opens the result as an unsaved editor project. The exporter
reads back each staged package and compares the reconstructed project with
the open document before publishing it.

The format preserves the original native layout dimensions. It depends on the
matching decoded stage revision. The older complete `.ogfproject` save format
and its flat-level export guard still have the same behavior.

Verification on `e8c9ce60b6` with the export changes:

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdEditorProjectSource,TestContentImportService,TestContentImportScreen,TestEditorProjectWorkspace,TestEditorProjects,TestEditorHostExports' test -B
python3 tools/i18n/catalogs.py check
```

All 81 selected tests passed with zero skips. The verified USA CHD supplied
the Past stage in both package round trips. The tests restore a map cell, art
pixel, moved and removed objects, and an added ring. The native replay checks
the existing object's second subtype after the move. The workspace test checks
that export leaves the current stage and document active and prompts before
replacing unsaved work. The run waited 407 seconds for the queue and Maven
reported 1 minute 45 seconds of execution. User wait was zero. Other elapsed
time was unmeasured. English catalog checks passed.

## Source-backed layout resizing

The editor validates resize requests against the selected stage's collision
window. A crop keeps the top-left overlap and removes placements and ring groups
outside it. Expansion uses the editor's empty terrain block. Native replay
recognizes painted foreground columns, clips the background collision alias to
the new width, and keeps loop flags on retained source cells with unchanged
block IDs. The stage retains its native collision row mask and its PPZ
background row selection.

Version 1 native project packages still carry same-size edits. Version 2
declares the new dimensions of a resized project. The reader hashes the
original decoded stage before reshaping its layout and applying the edits.
Comparing the reshaped baseline keeps unchanged disc cells out of the package
when a resize moves their positions in the underlying map array.

The real-disc test opens a native editor project, checks the invalid size error,
and crops the project through the existing form. A Past stage replay compares
the retained foreground, native loops, and background row mapping. An expanded
project goes through save, ZIP export, import, native playtest, and a runtime
collision query in the painted new column. The existing R11A unedited HUD
comparison is part of the same selection.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestCdEditorProjectSource,TestCdEditorPlaytestRendering,TestEditorProjectWorkspace,TestEditorProjects,TestContentImportService' test -B
```

On export base `1de86c5457` with the resize changes, all 53 tests passed with no
skips. The wrapper found the verified USA CHD. Queue wait was 373 seconds.
Maven's elapsed time was 2:01, with no user wait. The catalog quick check and
skip classifier also passed, with the classifier's existing preserved-report
skip.
