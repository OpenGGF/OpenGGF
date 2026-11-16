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
