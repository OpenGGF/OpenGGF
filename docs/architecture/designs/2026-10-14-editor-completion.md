# Complete level authoring workflow

The requested outcome is to create and edit playable levels for Sonic 1, Sonic 2,
and Sonic 3&K, with safe projects, playtesting, and usable export. The implementation
starts from develop `4a0e714d5e29b65a5b1ba8e6cbb6a8223f560c75`.

## Scope and acceptance

An author can create a named blank level using a ROM-derived asset library, clone
a supported loaded layout, or import an exported definition. They can change
foreground/background placement, block/chunk composition and descriptor flags,
collision assignments, object/ring placement and properties, dimensions, bounds,
start position, name and music. Projects save independently of gameplay sidecars,
reopen with complete data, and retain undo/selection across playtests. Each game
must demonstrate create → edit → save → reopen → playtest → export → host load.

This is a level authoring tool using imported or ROM-derived asset libraries.
Pixel-art creation, arbitrary native stock-zone event editing, campaign scripting,
and a general mod packaging studio are separate capabilities. Flat authored levels
must not inherit stock bosses, cutscenes, PLC headers or terrain mutations. A stock
layout clone is a layout/assets/spawns starting point; unsupported native features
must be reported before a supposedly playable export is published.

## Owners and data flow

`EditorProjectDocument` owns the authored mutable level, metadata, project path and
saved state. The editor controller and history survive transient gameplay modes.
`EditorProjectStore` writes a bounded, versioned, complete project with a checksum
through a same-directory temporary file and atomic replacement. Failed saves leave
the preceding project intact. Loading validates counts, lengths, identities and
every reference before publishing a document. Missing keyed-object dependencies
remain in the document; playtesting reports their absence without rewriting them.

`EditorProjectLevel` reconstructs complete editor data and reserves bounded free
definition slots for local composition. It supports blank creation and resizing
without changing the public Mod API. Metadata includes game, source zone/act and
fingerprint, start, bounds, music and S3K object-zone family. Source ROMs and stock
gameplay saves remain independent of project files.

The host integration decorates the selected module/zone registry for one authored
level using existing `DelegatingGameModule`, `ModZoneRuntimeContribution` and
`ModZoneAdapter` seams. Each playtest constructs fresh game-owned runtime data from
the authored snapshot and uses flat authored-level initialization. Runtime changes
do not enter the document. Restart uses the authored start and current level;
play from cursor validates its position. Returning to the editor restores the
document and existing history rather than attaching gameplay-mutated data.

`FullLevelExporter` retains staged non-overwriting publication, adds explicit game
metadata, canonicalizes semantic object placement, emits S1/S2 v1 or S3K v2, and
validates both the strict parser and destination host. S1 receives a bounded flat
custom-level adapter with 16×16 blocks and correct native object/ring semantics.
S3K uses explicit S3KL/SKL metadata and sparse palette ownership. Incompatible
objects, palette claims and native features produce actionable errors.

Editor interactions share layout/hit-test geometry. Pointer ownership prevents
painting through chrome and stops stationary-pointer camera feedback. Stroke
transactions finish at save/export/history/focus/layer/mode/playtest boundaries.
Pattern browsing, erase, flags, local collision edits and tolerant spawn picking
complete the existing hierarchy. Status and active brush properties remain visible
at native and wider viewports.

## Delivery and evidence

Independent workers own project data, interactions, and export/game hosts; the
lead owns project menus, document/playtest integration, documentation and combined
validation. Focused checks cover real editing, malformed projects, failed writes,
dependency absence, repeated local edits, mode boundaries, all-game export/load,
and authored/runtime isolation. Offscreen rendering verifies readable chrome and
previews. Relevant S3K loading/bootstrap/AIZ guards remain mandatory.

Inspect the change-based smoke selection against the pinned pre-task SHA, state
its candidate count/cost/stopping rule, and run preflight plus smoke/guards once
after focused fixes. Attribute disputed failures by exact identity with bounded
baseline checks. No release-only full suite is used. Consumed diagnostics are
acknowledged. Integrate into the main workspace's develop branch, push only that
branch, then remove fully accounted worker/task worktrees and branches.

## Rejected approaches and audit evidence

- Extending legacy sidecars into projects was rejected: they omit patterns,
  palettes, collision profiles and metadata; S3K reapply is deliberately disabled.
- Removing the S3K reapply gate was rejected: stock event/art paths still require
  concrete native owners, including the AIZ fire handoff.
- Keeping the current playtest sharing was rejected: gameplay mutates the same
  level and each editor entry clears history; F5 currently reloads zone 0/act 0.
- Parser-only export validation was rejected: current v1/S2 placement assumptions
  do not provide playable S1 or S3K exports.

These findings were source audits of the pinned base, not observed UI/test passes.
Implementation and validation evidence will be added here as the work lands.

## Implementation decisions and reviewed boundaries

Project storage landed in ec1a522e3; editing controls in 9022b22d4 and
b9a8762a7; author guidance in fcb3b120e; native export/host work in b6e2f809f.
The savepoint compares all authored channels, including metadata and direct asset
edits. Structural properties/resize prepare a candidate retaining the old path
and savepoint, then publish only after the host can display it. Source-game
selection checks the selected ROM first; cancelling a staged switch restores the
retained document. Closing publishes an empty workspace after host return.

The placement allocator survives same-document playtests along with redo history.
Resetting it from live placements could reuse an identity held by an undone
command, causing placement after redo to fail. The dirty indicator consumes the
document comparison rather than the legacy layout flag. Modal ownership includes
closing frames so a command cannot also paint, toggle modes or restart gameplay.

S3K has no unused numeric stock ID: apparent fillers are native ring entries, and
ID 0 is a ring. Numeric reuse was rejected. The internal openggf:authored-goal key
wraps the existing native signpost/results graph behind a placement-owned trigger.
Engine registry provenance, callback ownership and reserved creator names protect
its authority; creator-reported owner strings do not grant it. Published Mod API
signatures are unchanged. Review requires actual foreign-loader authority tests
and unsigned position boundary checks in every supported native goal flow.

Neutral custom decoding erased block 0 while the parser allowed an occupied
block. Authored loading now preserves it with all-game collision checks; native
Sonic 2 ROM decoding keeps its separate shipped-ROM handling. Review identified
JSON token-limit parity: memory playtests and disk exports must reject the same
oversized metadata before publishing a host snapshot. Final integrated evidence
and hashes will be recorded after verification.

## Native integration findings

Sonic 2 EHZ's decoded shared bank contains 768 chunks and 256 blocks, while its
base 914 patterns become 926 after the native animated-content initialization.
Some shared definitions still reference unavailable patterns 1280–1311. New blank
libraries retain reference-closed available chunks/blocks and report omissions;
strict layout clones and saved-project snapshots remain exact and fail when
their references are unavailable. Inserting placeholder art or silently changing
an existing layout was rejected.

Gameplay camera bounds are camera origins, so a 1024px canvas uses maximum X
704 for the native 320px viewport. Native S1/S2 goal walk-off requires floor
through camera maximum plus $128; placing a goal against full-canvas camera
bounds made actual Engine completion impossible. New projects reserve the native
320×224 final viewport, full-canvas editor navigation stays independent, and
resize preserves custom insets while they fit. After clipping below that viewport,
later growth restores the canonical 320×224 insets; custom bounds need review.

Fresh S3K terrain scheduling originally assumed every native level had stock
KosM sources. Authored native levels already have baked terrain; the stock terrain
producer now checks semantic stock-ROM identity, retaining the separate player,
object and results art paths. A real-ROM host regression plus stock fresh-handoff
positive controls covers that ownership boundary. The actual Engine check then
found a separate event-routing leak: delegated logical zone zero initialized
stock AIZ events, whose plane intro held the player at the authored start
(air false, both speeds zero, control locked and object controlled after 120
frames). The authored module now supplies no stock level-event provider. Native
helpers must also respect loaded stock identity before consulting campaign event
managers. Actual S3K movement and native results return now pass.

Unsaved application quit uses the same atomic save barrier as project replacement.
The temporary prompt retains mode/session/history and all live/render-copy camera
words. It suspends before native PLC/VBlank/fade service so an already-pending
respawn/restart callback cannot replace the hidden runtime. Cancellation resumes
that callback; it does not reconstruct or discard the active playtest.


S1 results revealed a different ownership issue after campaign events were removed:
the reserved v_endcard SST object is excluded from dynamic object dispatch and was
updated only by the stock event hook. The existing getGameService seam now supplies
a trusted internal NativeFixedLevelObjectPass, invoked after event-owned fixed
slots and before dynamic SST. The S1 campaign hook no longer updates that card,
so stock and authored paths execute it once with the native V-int clock. The card
keeps its existing ObjectManager recreation/rewind ownership; the pass is stateless.
A foreign-loader implementation or callback projection cannot grant this authority.
This repairs the actual authored Engine path and additive export hosts.

## Focused verification

All runs used Java 25 and the normal Maven queue. ROM-backed selections received
the original absolute root paths via sonic1.rom.path, sonic2.rom.path and
s3k.rom.path; their CRC32/SHA-1 identities match repository guidance.
Validation used existing verified main-workspace ROM files, without creating or
replacing ROM assets. The native worktree bootstrap's asset/config/reference
links are setup artifacts; cleanup unlinks them while preserving their targets.

- session96515: 80 tests, one S3K landing failure, no errors/skips
  (queue189s/Maven79s). All-game create/edit/save/reopen/clone/export host loading
  was already passing. The failure was the native AIZ event leak above.
- session97691: 5 tests, the same S3K landing failure, no errors/skips
  (queue95s/Maven72s). Changed quit regressions passed, including held fade
  callbacks and distinct live/render-copy camera words. The first image harness
  used a private graphics owner and did not certify pane rendering.
- session85572: 132 tests, two failures, no errors/skips (queue89s/Maven76s).
  S3K actual gameplay and native completion passed; all required AIZ/loading/
  bootstrap/decoding checks passed. Failures exposed S1 fixed-card ownership and
  a QA fixture with only one colored thumbnail sample, below its strict threshold.
- session55809: 42 tests passed, zero errors/skips (queue0s/Maven72s), including
  TestEditorProjectLifecycle#playtestOwnsIndependentRuntimeAndNativeGoalReturnsToEditor
  for all three games, TestEditorProjectPresentation, TestNativeFixedLevelObjectPass,
  TestSonic1FixedEndCardSlot, TestSonic1ResultsPlcReadiness,
  TestSonic1PlcProducerCoverage and TestLevelFrameHardwareTimingBoundaries.
- session80469: 6 affected preview/pointer/QA tests passed, zero errors/skips
  (queue353s/Maven70s). The focused chunk uses a 64×64 integer-scaled preview;
  pointer selection covers each enlarged cell at native/wide widths.

Offscreen surfaceless EGL captures at 320×224 and 426×224 were manually inspected:
readable whole-pixel text, library/focused art, selected-cell borders, forms and
visible error/details prompts. Pixel assertions distinguish real cached art from
blue chrome. Captures and Maven reports are temporary target outputs, not runtime
performance evidence. Combined smoke/guards and subsequent attribution follow below.

## Combined validation and baseline attribution

Implementation `9627e29b1` plus upstream `60700c205` was frozen for the complete
selection. Preflight checked Java 25, Lua 5.4 and PowerShell in this exact launch
environment. The 3,270 candidate classes ran as 66 batches of at most 50, followed
by a fresh guards JVM:

Commands normalize the recorded absolute main-workspace path as
`OPENGGF_EDITOR_MAIN`; set it to your existing main checkout. ROM filenames,
selectors, flags and lifecycle arguments are preserved. No ROM setup is performed.

```bash
LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl DISPLAY= WAYLAND_DISPLAY= \
python3 tools/testing/run_categories.py \
  --base 4a0e714d5e29b65a5b1ba8e6cbb6a8223f560c75 \
  --rom-directory "${OPENGGF_EDITOR_MAIN}" --max-minutes 180 --run
```

Run `20261014T125054Z-ade2f786` completed all batches: ordinary 27,373 tests,
3 failures, 2 errors, 85 skips, 4,683.68s execution; guards 680 tests, 4 failures,
0 errors/skips, 273.04s execution. This is bounded smoke coverage, not a green
full-suite or graphics/performance certification. Guards waited 522s for admission;
queue waiting is separate from execution. The retained editor worktree lease was
observed released at 15:53:45 UTC and relayed to the diagnostic owner. All failure
and skip details were inspected (none omitted); diagnostics were then acknowledged.

The ordinary red cases were resolved or attributed by identity:

| Case | Evidence and disposition |
|---|---|
| TestRemainingRewindTailInventory#remainingRoundTripTailMatchesInventory | S3kAuthoredGoalInstance adds one actual passing round trip: 1,321 classes/1,078 passes, 243 graph-covered, 0 no-codec. Commit `aa6e42902` updates the inventory; its focused inventory, real goal restore/replay and rewind coverage checks passed 3/3 without skips. |
| TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels | Matched pinned `4a0e714d5` / current `60700c205` each 1 failure, 0 errors/skips: GLFW must initialize for pixel verification, expected true / actual false at line 53. Native pixels were not verified by these runs. |
| TraceCaptureBootDimensionsTest#realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int)[1] and [2] | Standalone clean pairs pass 2/2 on both commits. The causal ordered pair below gives identical 2 errors on both: Unable to initialize GLFW at HeadlessGameBoot:302, before boot assertions. TestSurfacelessEglContext's existing AfterEach clears the inherited backend property in the reused JVM. |
| TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput | Matched clean pairs each 1 failure, 0 errors/skips at line 182. Both children exit 4 with empty stdout; byte-identical stderr begins with Picked up JAVA_TOOL_OPTIONS, before PRODUCER_UNAVAILABLE. The fixed producer is unavailable before capture; no gameplay/audio producer was exercised. |

All matched pairs used Java 25.0.4.1, the required EGL flag and empty display
variables, normal admission, identical effective classpaths, and verified existing
main ROM files. Owning test/tool/POM sources are unchanged between pinned baseline
and `60700c205`. No unrelated helper, donor, graphics flag or audio path was changed
to force a pass. Both arms of each matched pair used the same exact command
from the projects or interaction worktree, respectively:

```bash
env JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl DISPLAY= WAYLAND_DISPLAY= LUA_BIN=/usr/bin/lua5.4 python3 "${OPENGGF_EDITOR_MAIN}/tools/testing/maven_queue.py" -Dmse=off '-Dtest=com.openggf.game.sonic3k.objects.TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels' "-Dsonic1.rom.path=${OPENGGF_EDITOR_MAIN}/s1.gen" "-Dsonic2.rom.path=${OPENGGF_EDITOR_MAIN}/s2.gen" "-Ds3k.rom.path=${OPENGGF_EDITOR_MAIN}/s3k.gen" test -B

JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl DISPLAY= WAYLAND_DISPLAY= LUA_BIN=/usr/bin/lua5.4 python3 "${OPENGGF_EDITOR_MAIN}/tools/testing/maven_queue.py" -Dmse=off -B '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' "-Dsonic1.rom.path=${OPENGGF_EDITOR_MAIN}/s1.gen" "-Dsonic2.rom.path=${OPENGGF_EDITOR_MAIN}/s2.gen" "-Ds3k.rom.path=${OPENGGF_EDITOR_MAIN}/s3k.gen" clean test
```

The ordered pair used the exact batch profile and all four ROM properties:

```bash
env JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl DISPLAY= WAYLAND_DISPLAY= LUA_BIN=/usr/bin/lua5.4 \
python3 "${OPENGGF_EDITOR_MAIN}/tools/testing/maven_queue.py" -Dmse=off -Psmoke \
'-Dtest=com.openggf.tools.TestSurfacelessEglContext#selectionIsOptInAndRejectsUnknownBackends,com.openggf.tools.TraceCaptureBootDimensionsTest#realBootKeepsNativeGameplayCameraAndWidePresentationCaches' \
"-Dkis2.rom.path=${OPENGGF_EDITOR_MAIN}/kis2.gen" \
"-Ds3k.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
"-Dsonic1.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic The Hedgehog (W) (REV01) [!].gen" \
"-Dsonic2.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
```

Sessions 79790/current and 98355/baseline each ran 3 cases: selection passed,
dimensions 400/528 each errored identically, 0 skips. Non-clean lifecycle and
alphabetical reused-fork ordering were matched. Queue waits 400s/current and 575s/
baseline are separate from Maven 24.137s/current and approximately 64s/baseline.

The 85 skips include opt-in benchmarks/soaks/captures, unavailable native graphics
checks and optional local audio references. 32 InfiniteSonic cases lacked applicable
platform/flyer stretches; CPZ spin-tube capture/release did not satisfy its scenario
prerequisite. These are coverage limits, not passes or missing-ROM excuses. All-game
editor lifecycle and owned offscreen editor pixels passed in the focused runs above.

## Guard corrections and updated upstream

Commit `d769f6611` routes export host creation through the existing composition-root
registry's pure factory, with fresh-host and unchanged bootstrap/world ownership
checks. The two intentional editor-to-io/mods data edges reuse bounded asset and
strict baked-level parsing contracts. A new guard restricts exact owners and types;
general creator execution/registration stays rejected. Edge pairs increase 125→127;
constructor baseline 0, cycle baseline 1 and the 16-slice cluster remain unchanged.
Duplicating parsing/sandbox logic or widening the cycle waiver was rejected.
Session 24969 passed 77 checks, 0 failures/errors/skips, including all three ROM-backed
export hosts, 31 ArchUnit cases and V-int terminology (queue 32s/Maven 133s).

Commit `3c0c93237` extracts `GameLoopEditorController` without changing public APIs,
editor gates or the early native-frame suspension barrier. GameLoop shrinks from
3,345 to 3,303 effective lines against the unchanged 3,313 budget. Session 14868 passed 17
checks, 0 failures/errors/skips: the real quit/cancel/pending-fade callback, all-game
modal shortcuts, focus loss, routing and offscreen presentation (queue 35s/Maven 77s).
Increasing the large-class budget was rejected.

Upstream develop `1a038f53c` adds native/scoped-palette graphics behavior. Its merge
`3773c5bd7` resolved implementation-pitfalls prose cleanly and preserves both sets of
findings. Correction merges `fb1899219` and `958a3cb4a` are clean. Because that
combination was not covered by the earlier smoke source, a final normal focused
request will cover the affected guards, owned editor pixels and actual native goal
completion for all three games. Unchanged broad smoke is not repeated.

Final request session35058 tested production/test source `958a3cb4a` with the four
owned evidence/architecture prose files uncommitted and frozen. It passed 38 cases,
0 failures/errors/skips: 31 ArchUnit, 1 size, 1 clock, 1 actual pixel, and 4 native
lifecycle cases (all three goals plus S2 quit/cancel). Queue 22s/Maven 133s. Actual
XML identities and zero skips were inspected; native 320px pattern and 426px form
captures were inspected again after the graphics merge. No extra broad baseline,
post-integration or release full-suite run is justified for this unchanged source.

```bash
LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl DISPLAY= WAYLAND_DISPLAY= python3 tools/testing/maven_queue.py -Dmse=off -B -Pguards '-Dtest=TestArchUnitRules,TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction,TestObjectUpdateClockTerminologyGuard,TestEditorProjectPresentation,TestEditorProjectLifecycle#playtestOwnsIndependentRuntimeAndNativeGoalReturnsToEditor+cancellingNativeExitModalRestoresTheSameHeadlessPlaytestAndEditorView' "-Dsonic1.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${OPENGGF_EDITOR_MAIN}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

All editor-introduced failures are corrected and the four remaining ordinary red
cases match the pinned baseline. The delivery therefore retains those explicit
headless/test-environment limits; it is not described as an entirely green suite.
Final documentation checks cover shell syntax, local links, mirrored guidance,
diff whitespace and commit policy. Integration/push/cleanup follow the repository
workflow and are reported separately in the delivery result.

Delivery completed on develop `605d0c431`: its tree exactly matches the validated
feature tree `48d29f95d`, and the remote SHA was verified after push. All four owned
trees/merged local branches were removed at the 16:21 UTC checkpoint under normal
leases. Main dirty disassemblies and unrelated files remain intact. The unknown
3,065-byte Starpost Valley save was preserved outside the repository before removal;
its SHA-256 remains `fb947efe9990ae65a4cbbacb6efadfa8faa6caa57c9353ce8752eb30a38ce156`.
