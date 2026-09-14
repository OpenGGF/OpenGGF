# Agent Workflow

Support material to help agents implement OpenGGF objects/zones/trace-fixes with less context loss.

## Tools

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
| `GameplayCaptureTool` | Pictures or films any gameplay section: boots a zone/act on the production path (`HeadlessGameBoot` + `GameLoop.step()`), teleports the leader, drives it from an input log or `.bk2`, and writes PNG frames, `state.csv`, and an MP4. Widths, donors and teams are flags. Skill: `gameplay-capture`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool" "-Dexec.args=--game s3k --zone fbz --act 2 --x 0x1CF0 --y 0x76C --input target/capture/run.txt --out-dir target/capture/fbz2"` |

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

## Test harness helpers

- `com.openggf.tests.route`: reviewed input-program, steering, object-lifetime,
  recent-frame and CPU-team audit helpers imported from `435ec2e68` for the
  route-controller continuation. `TestS3kAiz1RoutePilot` is the native AIZ1
  representative route; `TestS3kAiz1RouteRewind` checks independently reached
  live spots. The [AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md)
  records their evidence and remaining coverage.
