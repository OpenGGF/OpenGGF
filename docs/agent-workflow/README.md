# Agent Workflow

Support material to help agents implement OpenGGF objects/zones/trace-fixes with less context loss.

Zone delivery: [methodology v2](../architecture/designs/2026-09-15-zone-methodology-v2.md)
retains FBZ's route slices and adds early short native comparisons and compatibility
checks. Its first target is the [Sandopolis plan](../architecture/plans/2026-09-15-soz-methodology-v2.md).

## Delivery skills

- [S3K zone bring-up](../../.agents/skills/s3k-zone-bring-up/SKILL.md) is the
  end-to-end entry point: inventory, playable slices, visual/native evidence,
  compatibility and rewind, current acceptance status, media and integration.
- [Gameplay capture](../../.agents/skills/gameplay-capture/SKILL.md) records an
  individual ordinary gameplay scenario.
- [BizHawk native reference capture](../../.agents/skills/bizhawk-native-reference-capture/SKILL.md)
  runs a zone-specific Lua exporter in BizHawk for native screenshots and state
  observations used as ROM corroboration or visual references.
- [Gameplay highlights](../../.agents/skills/gameplay-highlights/SKILL.md) maintains
  the unified source archive and curates a reproducible, act-ordered moving reel.
  It also replaces outdated excerpts after follow-up fixes.

These skills apply the methodology and current repository policy; plans retain
per-task decisions and evidence. Highlights do not certify uninterrupted routes.

## Tools

- `GameplayAllocationTool`: ordinary BK2-driven loop/render bytes per frame, excluding PNG/readback and CSV allocation. Use `--rom <absolute-path> --input <bk2> --zone <numeric-id> --act <one-based> --out-dir target/alloc`; optional `--frames 6000 --warmup 600 --width 400 --main sonic --sidekick tails --rewind true --jfr true`. `--sidekick none` selects solo play; the console reports the live roster count. Loop allocation includes input-row decoding. Compare route state before interpreting allocation deltas; JFR includes other threads, whereas reported counters measure the gameplay thread. Keep raw diagnostics temporary.

- [Maven resource profiler](../../tools/testing/profile_maven.py): sample queued ordinary/guard runs for process-tree memory and CPU admission estimates (2026-09-15 Maven resource task).

- [FBZ boundary comparator](../../tools/bizhawk/compare_fbz_boundary_fixture.py) supports `--sprite-publication` to compare the complete native CPU sprite table with the following consecutive frame’s VDP table; duplicate samples and gaps are explicit. The boundary exporter supplies the read-only CPU snapshots.

- [FBZ cadence pixel comparator](../../tools/bizhawk/compare_fbz_cadence_pixels.py) pairs native VRAM/SAT/CRAM pixels with reconstructable actual-GPU source masks, retaining occlusions and unmatched source pixels; requires Pillow.

- `FbzVisualCaptureTool` also records actual tilemap GPU uniforms and hash-bound descriptor, lookup and atlas readbacks, so camera-state acceptance can be separated from shader sampling defects. See [FBZ GPU sampling evidence](../architecture/research/s3k-zones/fbz-validation.md#gpu-sampling-and-retained-background-history-2026-09-14).

- [Movie checkpoint exporter](../../tools/bizhawk/capture_movie_checkpoints.lua) and [trace checkpoint pixel comparator](../../tools/bizhawk/compare_trace_checkpoint_pixels.py) capture native PNG/VDP checkpoints (with a reusable zone-entry state) and compare them with a full-run trace capture in 3-bit colour, skipping lag rows (2026-09-17 SOZ pixel checkpoints).

- [SOZ pushable-rock probe](../../tools/bizhawk/capture_soz_pushable_rock.lua) finishes the native entry jump, then records the positioned push/fall/track/stop sequence with the shared host (2026-09-15 SOZ methodology v2).

- [Native reference capture](../../tools/bizhawk/README.md) runs explicit zone-owned Lua exporters/plans with an isolated BizHawk 2.11 configuration, source hashes and failed-export detection. The FBZ command remains a compatibility profile (2026-09-15 SOZ methodology v2).

Nine `com.openggf.tools` CLIs. All invocations are PowerShell-quoted (quote each `-D...` property).

| Tool | Purpose | Invocation |
|------|---------|------------|
| `AgentWorkflowTool` | Preflight checklist for an object task: zone-set resolution, registry status, RomOffsetFinder commands, required guards, docs. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.AgentWorkflowTool" "-Dexec.args=object s3k MHZ 0x8A"` |
| `RomArtIntakeTool` | S3K ROM-backed art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`. Flags (caution, not a hard reject) `s3.asm`-sourced labels (Sonic 3 standalone / S3L half) — it classifies by source file, since a label search carries no ROM offset. Prefer an S&K equivalent; if an object has none, the S3-half reference is legitimate (rare; verify). Recommends StandaloneArtEntry vs LevelArtEntry and `Sonic3kConstants` / `Sonic3kPlcArtRegistry` hints. Processes multiple labels. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"` |
| `ObjectScaffoldTool` | Guard-friendly object/badnik skeleton + JUnit5 test shell (no `getInstance()`, no ctor `services()`, no `addDynamicObject`/`setDestroyed`; center-coord note). `--game s3k --badnik` emits the `...sonic3k.objects.badniks` package extending `AbstractS3kBadnikInstance`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ObjectScaffoldTool" "-Dexec.args=--game s3k --class MhzFooObjectInstance --id 0x8A --badnik"` |
| `TraceTriageTool` | Reads `target/trace-reports/<game>_<zone>_report.json` and prints a first-divergence brief (frame/field, ROM vs engine, likely owning subsystem, disasm search terms). Comparison-only; never hydrates engine state. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=s2 mtz1"` |
| `ZoneSpecNormalizerTool` | Normalizes an `s3k-zone-analysis` spec into the stable 13-section layout (palette cycling vs mutation kept separate; `(not analyzed)` placeholders for gaps). | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ZoneSpecNormalizerTool" "-Dexec.args=<path-to-zone-analysis-spec.md>"` |
| `TraceBenchmarkTool` | Replays a trace headlessly with no pacing and reports per-subsystem frame-time percentiles, for comparing JVMs or catching a performance regression. Writes a JSON report. Never quote its numbers without checking the trajectory digest matched. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceBenchmarkTool" "-Dexec.args=--trace aiz1 --json target/bench/temurin21-g1.json"` |
| `BenchmarkCompareTool` | Renders a Markdown comparison from two or more benchmark reports; the first is the baseline. Pure post-processing, so it can run under any JVM. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.BenchmarkCompareTool" "-Dexec.args=--out target/bench/comparison.md target/bench/a.json target/bench/b.json"` |
| `InputLogAuthorTool` | Compiles a short controller script (`60 R; 1 D+R; repeat 3 { 1 A ; 1 - }`) into a BizHawk `Input Log.txt` or minimal `.bk2`, then re-parses it with `Bk2MovieLoader` so the file is proven loadable. Skill: `bk2-input-authoring`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" "-Dexec.args=--inline '60 R; 1 A' --out target/capture/run.txt"` |
| `DezFinalRouteAuthorTool` | Observes the final DEZ encounter and authors controller-only input through hands, core and escape. Declared solo Sonic boot: 200 rings, seven Super Emeralds. Optional DEZ2 input starts at ($34B0,$300), traverses its boss/real final-arena load and continues without reseeding. Writes input script/state CSV; no post-boot gameplay-state injection or parity claim. | `DezFinalRouteAuthorTool <ROM> <width> <output-prefix> [DEZ2-boss-input]` (same Java classpath as gameplay capture) |
| `GameplayCaptureTool` | Pictures or films any gameplay section: boots a zone/act on the production path (`HeadlessGameBoot` + `GameLoop.step()`), teleports the leader, drives it from an input log or `.bk2`, and writes PNG frames, `state.csv`, and an MP4. Widths, donors and teams are flags. Skill: `gameplay-capture`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool" "-Dexec.args=--game s3k --zone fbz --act 2 --x 0x1CF0 --y 0x76C --input target/capture/run.txt --out-dir target/capture/fbz2"` |
| `LevelTileUsageLocatorTool` | Lists the 128px layout cells (foreground and background) that place given 8x8 tiles or palette-line colours, so a capture can be aimed at AniPLC destination tiles or an AnPal-cycled colour that is off screen from the level start. Origin: HPZ bring-up demo captures. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.LevelTileUsageLocatorTool" "-Dexec.args=--game s3k --zone hpz --act 2 --tiles 0x2D0-0x2DB --colors 3:1,2"` |
| `tools/bizhawk/capture_hpz_route_reference.lua` | Native BizHawk exporter for Hidden Palace questions: records RAM (camera, players, event words, palette line 4, emerald state) and framebuffer images for plan-declared movie windows, and saves native states at planned frames so later probes load a state instead of replaying a complete-run movie. Origin: HPZ bring-up. | `python3 tools/bizhawk/capture_native_references.py ... --exporter tools/bizhawk/capture_hpz_route_reference.lua --plan plan.lua --fixture-state <state> --require-output observations.csv --require-output done.txt` |
| `tools/bizhawk/capture_ddz_route_reference.lua` | Native BizHawk exporter for Doomsday questions: records the DDZ controller/boss/body SST fields, autoscroll words (`_unkFA82..FAB8`), foreground-plane scroll and palette per frame; `plan.slots` logs every SST slot-occupancy change (load and allocation order) and `plan.boss_code` retargets the boss columns. Origin: DDZ bring-up (slot histories found the `Camera_X_pos_coarse_back` latch). | `python3 tools/bizhawk/capture_native_references.py ... --exporter tools/bizhawk/capture_ddz_route_reference.lua --plan plan.lua --fixture-state <state> --require-output observations.csv --require-output done.txt` |

## Test harness helpers

- `src/test/java/com/openggf/tests/route/` — shared route primitives for headless
  route controllers: `InputProgram` (parse, derive from a BK2, and step pad runs),
  `RouteSteering` (steer, walk with a speed cap, brake distance, ordinary crossing
  budget), `ObjectLifetimeFrames` (spawn/despawn identity sets per frame),
  `RecentFrameLog` (failure diagnostics) and `SidekickAudit` (CPU team contract:
  identity, ownership, leader chain, death/respawn). Extracted from the FBZ2 native
  route in commit 610464952; see
  [live-state route controllers](../architecture/research/2026-09-13-live-state-route-controllers.md).
  They overlap only the failure-diagnostics part of backlog item LTS-04, which stays
  pending.
- `LevelSolidityMapProbe` (test scope, opt-in `-Dopenggf.solidity.map=<out file>` plus
  `openggf.solidity.game/zone/act/x0/x1/y0/y1/step`) writes an ASCII map of a level's
  foreground solidity from the engine's terrain sensors, so a BK2 route (glide targets,
  climbable faces, monitor perches) is authored against real terrain before any capture.
  Pair it with `tools/traces/assemble_bk2_from_input_log.py`, which packages an
  `InputLogAuthorTool` log as the BizHawk-keyed `.bk2` the TraceChaser headless harness
  accepts. Both come from the first Knuckles in Sonic 2 fixture (2026-09-14).
- `PlaneOpacityProbe` (`com.openggf.tools`, library not CLI) answers "can the plane behind
  this one show through here?" from the decoded layout / blocks / chunks / patterns alone --
  no renderer, no camera, no frame. `coverage(level, map, layer, x, y, w, h)` counts the
  see-through pixels of a rectangle and `pixelAt` resolves one, applying both flip levels.
  Use it whenever a background change produces byte-identical captures: a capture cannot
  separate "wrong pixels drawn" from "no pixels reachable", and this can. Always pair it with
  a control viewport that must report see-through pixels. Origin: LRZ slice 5 follow-up,
  2026-09-18, where it showed Lava Reef act 1's foreground is opaque across the whole dome
  (`TestS3kLrzForegroundOpacity`).
- `FbzRouteEvidenceProbe` (test scope, opt-in `-Dmse=off -Dopenggf.fbz.evidence=true`)
  prints the `RouteCompletionEvidence` line of each of the eleven FBZ2 complete-route
  matrix rows without asserting; diff the output before and after a route-controller
  or primitives refactor to prove byte-identical behaviour.

## Docs

- [Release publishing](../project/release-publishing.md) — automatic publication on
  `master` push, skipped-job diagnosis, and publication verification;
  use the mirrored `release-publishing` skill for this task.
- [runbooks/README.md](runbooks/README.md) — step-by-step runbooks per task type
- [ci-guard-failure-explainer.md](ci-guard-failure-explainer.md) — guard test → correct fix
- [pitfall-catalogue-index.md](pitfall-catalogue-index.md) — known ROM pitfalls grouped by bug class
- [documentation-obligation-checklist.md](documentation-obligation-checklist.md) — trailers / TRACE_FRONTIER_LOG / changelog
- [delegation-prompt-templates.md](delegation-prompt-templates.md) — research/impl/triage/art/review prompt templates
- [briefing-trace-rounds.md](briefing-trace-rounds.md) — how to hand a trace divergence to an agent: supply the symptom, not your hypothesis.
  Also the accumulated round rules, with a scannable index: the evidence rules, the operational ones (the branch is the artifact; create worktrees copy-on-write; concurrent rounds are a budget), and a
  **measurement-hazard table** listing each hazard's signature and what it looks like. Read that table before reporting any suite number — every entry in it produces output indistinguishable from a real result.

## Worktree resource-link policy

The `post-checkout` hook may create convenience links in a linked worktree for
local ROMs, `config.yaml`, and reference-disassembly directories. Those links
are **filesystem-only scaffolding**: their targets are relative to the
worktree, so they remain portable on the same machine, but they must never
enter a Git tree.

Do not stage generated-resource entries. The policy rejects symlinks at
`config.yaml`, any path ending in `.gen`, and `docs/s1disasm`,
`docs/s2disasm`, `docs/kis2disasm`, `docs/scddisasm`, or `docs/skdisasm`.
Separately, the repository-wide ROM-like asset rule rejects added or modified
paths ending in `.gen`, `.smd`, `.bin`, `.sms`, `.gg`, or `.32x`. It also
rejects absolute symlink targets anywhere in the repository. If a broad
`git add` includes one, unstage it, keep or recreate the local link only in the
filesystem, and inspect `git diff --cached` before committing. The ignore rules
cover both a real reference directory and a hook-created link; use `git add -f`
only for an intentional policy test fixture, never to commit local resources.

The policy also rejects new textual machine-local user-home paths. Use a
repository-relative path, `$HOME`, an environment variable, or a neutral
placeholder such as `<user>` in documentation, commands, and reports. Put
intentional architecture audits and handovers in their matching
`docs/architecture/` category instead of committing root-level
`MERGE-STATUS*.md` or `HANDOVER*.md` scratch files.

`pre-commit` gives immediate staged-content feedback, and `commit-msg` repeats
the check for merge resolutions before the normal merge-specific policy path
can return. `pre-push` checks every non-deletion update: an existing branch is
checked over its outgoing range, while a new branch is checked for commits
unique to that remote as well as its tip. A clean tip does not make an earlier,
newly published violation acceptable; remove the bad commit from unpublished
history, then run the hook again. CI applies the same commit-range and
delivered-tree checks on every branch push.

## Direct Maven output

OpenGGF build and test commands invoke Maven directly. Each worktree owns its
`target/` tree, including Surefire reports, trace reports, diagnostics,
temporary files, and per-fork LWJGL extraction. Do not redirect these outputs
into shared session storage. Put durable captures in an explicit external task
or archive directory and retain only the evidence the task requires.

## Start here

Run `AgentWorkflowTool` for a preflight, read the matching runbook, scaffold with
`ObjectScaffoldTool`, intake art with `RomArtIntakeTool`, and triage traces with
`TraceTriageTool`. For performance work, start from
[`runbooks/runbook-jvm-benchmark.md`](runbooks/runbook-jvm-benchmark.md) rather
than the benchmark CLIs directly — the numbers are easy to misread.

Local Maven commands: [`tools/testing/maven_queue.py`](../../tools/testing/maven_queue.py) waits automatically for a shared execution slot across linked worktrees; category runs use it too.

## Test harness helpers

- `com.openggf.tests.route`: reviewed input-program, steering, object-lifetime,
  recent-frame and CPU-team audit helpers imported from `435ec2e68` for the
  route-controller continuation. `TestS3kAiz1RoutePilot` is the native AIZ1
  representative route; `TestS3kAiz1RouteRewind` checks independently reached
  live spots. The [AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md)
  records their evidence and remaining coverage.

- `tools/bizhawk/capture_fbz_boundary_fixture.lua`: declared-write native FBZ boundary6 pilot; records every redraw frame with RAM/VDP bytes and bounded failure, launched by the visual host with `--fixture-state` (2026-09-14 FBZ completion).

- `FbzBoundaryFixtureCaptureTool` and [boundary comparator](../../tools/bizhawk/compare_fbz_boundary_fixture.py) reproduce declared native fixture setup through production frames and compare actual retained Plane-B descriptors, uploaded palettes and framebuffers; acceptance remains independently reviewed.

- [Fresh native FBZ entry](../../tools/bizhawk/capture_fbz_fresh_entry.lua), launched by the visual host with `--fresh-entry-act 1|2`, uses the complete BK2 reset opening, AIZ vine cheat and ordinary title/level-select inputs; it never writes RAM. This removes retained cloud-history residue from complete-run FBZ states.

- [`capture_lrz_boulder_reference.lua`](../../tools/bizhawk/capture_lrz_boulder_reference.lua)
  records read-only native LRZ2 cutscene, rider and fresh-load carry observations from an original movie save.
- [`capture_lrz_traversal_reference.lua`](../../tools/bizhawk/capture_lrz_traversal_reference.lua)
  observes LRZ turbine and chained-platform slots during unchanged native BK2 playback;
  use the common native capture host with explicit frame windows and its own movie save.

- `tools/bizhawk/capture_lrz_boss_reference.lua`: read-only original-movie LRZ3
  camera/event and object-graph observer, with optional lava/VScroll/HScroll export;
  `palette=true` also observes the shared LRZ post-boss script cursor, timer and colors;
  `background=true` observes the LRZ2 Death Egg slot, screen coordinates, priority and art-load flag;
  native save and frame-window plan inputs
  (September 22 S&K completion campaign).

- `tools/bizhawk/capture_ssz_arrival_reference.lua`: read-only original-movie SSZ1 cutscene, Death Egg child timers/animation, palette and Knuckles landing observer; explicit native save and frame-window plan (September 24 S&K completion campaign).
- [SSZ arena-static presentation demo](../../tools/visuals/ssz_arena_static_demo.py): composites deterministic side-only noise over verified widescreen boss footage and emits a local comparison page; originating [2026-09-24 design study](../architecture/designs/2026-09-24-widescreen-boss-arena-static.md), not an engine feature.
