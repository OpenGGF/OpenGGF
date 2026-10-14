# Level editor completion implementation plan

**Goal:** Complete create/edit/save/reopen/playtest/export for all three games.
**Design:** [Editor completion](../designs/2026-10-14-editor-completion.md).
**Base:** `4a0e714d5e29b65a5b1ba8e6cbb6a8223f560c75` on develop.

## Constraints

- Java 25, JUnit Jupiter, ROM pipeline assets, injected runtime services.
- Main branch stays develop; workers use isolated local feature/ai-* worktrees.
- Preserve stock saves, missing dependency records and unrelated repository work.
- Use existing semantic module/zone/profile seams; no game-name gates in shared code.
- Focused queued checks during implementation; one combined change-based smoke run.
- All seven policy trailers precede the final model/version co-author block.

## Workstreams and interfaces

### 1. Complete project data and storage

Owner: project worker, `.worktrees/ai-editor-projects`.
Files: new `editor/project/*`; project tests. No controller, Engine, exporter or
game-module edits without coordination.

- [x] Define metadata, complete validated snapshot, document and store.
- [x] Implement atomic full-project save/open, blank/template creation, resizing,
      saved-state tracking, dependency retention and bounded free definition slots.
- [x] Exercise all-game data round trips, malformed references, oversize inputs,
      overwrite/failure preservation, project independence and history-compatible
      level replacement. Report exact APIs to the lead before consumers are written.

### 2. Complete safe editing interactions

Owner: interaction worker, `.worktrees/ai-editor-interactions`.
Files: controller, editor input/history/palette/render/commands and focused tests.
No Engine/session/project storage or game host edits without coordination.

- [x] Reproduce mouse ownership, stationary cursor and stroke-boundary defects.
- [x] Share UI layout/hit testing; correct pointer navigation and focused clicks.
- [x] Implement pattern library, terrain erase, transforms/palette/priority,
      local collision copy-on-write, useful capacity feedback and spawn inspection.
- [x] Preserve controller state when the same document is reattached; expose
      status text and host/document command callbacks for project integration.
- [x] Verify keyboard/mouse/controller, native/wide layout, flags/previews,
      undo/redo and boundary behavior with focused tests and offscreen output.

### 3. All-game usable export and native host construction

Owner: host worker, `.worktrees/ai-editor-hosts`.
Files: exporter, staging validator, S1 host/runtime helpers, relevant host tests.
No Engine/session/controller/project store edits without coordination.

- [x] Add game/family to export requests, retaining old constructors for callers.
- [x] Canonicalize placement semantics and emit S1/S2 v1 or S3K v2.
- [x] Validate selected host before publishing and preserve earlier exports.
- [x] Add S1 flat custom-level host with 16×16 blocks, ROM-backed character/ring
      art, native placement/ring semantics and initialization excluding stock events.
- [x] Prove per-game parser/adapter load, collision/spawn behavior and meaningful
      unsupported-feature failures. Keep published Mod API pins untouched; update
      candidate policy/pins only if signatures actually change.

### 4. Project menus and document/playtest integration

Owner: lead, `.worktrees/ai-editor-completion`.
Files: project menu/runtime module bridge, Engine/session/title integration,
integration tests, documentation.

- [x] Add title Tools and editor command access to New/Open/Import/Save As,
      properties/resize, start/cursor playtest and repeatable export destinations.
- [x] Make document/controller persistent while transient gameplay uses a new
      snapshot. Restart stays on the authored level/start and retains history.
- [x] Preserve missing dependencies; expose failures visibly and keep the project
      open. Guard trace/movie modes and stock gameplay save isolation.
- [x] Merge worker branches by owned scope, resolve consumer contracts by intent.
- [x] Add all-game headless lifecycle acceptance and offscreen editor QA.
- [x] Update current guide/config/changelog and relevant discrepancy/backlog prose.

### 5. Validation and delivery

- [x] Run focused acceptance with explicit verified absolute ROM paths and inspect
      skips. Review high-risk persistence/runtime integration once.
- [x] Inspect smoke selection/preflight, run complete selected batches and guards,
      inspect summaries, narrowly attribute any disputed baseline failures.
- [x] Acknowledge diagnostics and record commands/results/coverage limits.
- [ ] Verify commit trailers, integrate into updated main develop and push it.
- [ ] Account for all changes, remove merged worktrees/branches, prune metadata.

## Review focus

Corrupt or partially written projects; missing keyed dependencies; pointer input
on chrome and mode boundaries; native-vs-canonical placement semantics; gameplay
mutations contaminating documents; S1 synthetic zones entering ROM header lookups;
S3K palette/object family ownership; asset-capacity exhaustion; restart/respawn
reloading the wrong level; declarations of passes whose ROM tests actually skipped.

## Checkpoint

2026-10-14 16:13 UTC: implementation and bounded verification complete. Root
`958a3cb4a` combines implementation `9627e29b1`, current develop `1a038f53c`,
inventory `aa6e42902`, host boundary `d769f6611` and editor extraction `3c0c93237`.
The final documentation-only commit records these results. Main remains develop;
its dirty disassemblies and untracked user files are preserved.

Complete smoke: 3,270 classes / 66 ordinary batches, 27,373 ordinary cases,
3 failures, 2 errors, 85 skips; fresh guards 680 cases, 4 failures, no errors/skips.
All failure/skip details were inspected and diagnostics acknowledged. The four
inherited ordinary cases match the pinned baseline exactly. Inventory correction
passed 3 focused cases; host/guard correction passed 77; extraction passed 17;
final upstream+correction combination passed 38. Focused runs had no skips. Exact
commands, identities, source contexts and coverage limits are in the design.

Workers are idle, their branches merged, and no owned validation jobs remain.
Category lease release was relayed at 15:53:45 UTC; final focused completion was
observed at 16:13:01 UTC and relayed. Four-tree inventory is stable. No source,
publication or cleanup hold was requested. Normal leases protect final writes and
will exclude any admitted all-tree diagnostic before inventory removal.

Lead's remaining delivery actions at this checkpoint: commit the evidence prose,
verify current upstream, integrate/push develop, preserve the unknown Starpost
Valley save outside the trees, then remove the four accounted trees and fully
merged local branches. Final delivery reports the actual push and cleanup state;
no unchanged smoke rerun or release-only full suite.
