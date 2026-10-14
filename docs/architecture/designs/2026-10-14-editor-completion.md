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
resize preserves the inset even after shrinking below that viewport.

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
No ROM was copied, renamed or linked by this task.

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
performance evidence. Combined smoke/guards and final integration remain pending.
