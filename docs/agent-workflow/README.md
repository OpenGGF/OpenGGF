# Agent Workflow

Support material to help agents implement OpenGGF objects/zones/trace-fixes with less context loss.

## Tools

Seven `com.openggf.tools` CLIs. All invocations are PowerShell-quoted (quote each `-D...` property).

| Tool | Purpose | Invocation |
|------|---------|------------|
| `AgentWorkflowTool` | Preflight checklist for an object task: zone-set resolution, registry status, RomOffsetFinder commands, required guards, docs. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.AgentWorkflowTool" "-Dexec.args=object s3k MHZ 0x8A"` |
| `RomArtIntakeTool` | S3K ROM-backed art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`. Flags (caution, not a hard reject) `s3.asm`-sourced labels (Sonic 3 standalone / S3L half) — it classifies by source file, since a label search carries no ROM offset. Prefer an S&K equivalent; if an object has none, the S3-half reference is legitimate (rare; verify). Recommends StandaloneArtEntry vs LevelArtEntry and `Sonic3kConstants` / `Sonic3kPlcArtRegistry` hints. Processes multiple labels. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"` |
| `ObjectScaffoldTool` | Guard-friendly object/badnik skeleton + JUnit5 test shell (no `getInstance()`, no ctor `services()`, no `addDynamicObject`/`setDestroyed`; center-coord note). `--game s3k --badnik` emits the `...sonic3k.objects.badniks` package extending `AbstractS3kBadnikInstance`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ObjectScaffoldTool" "-Dexec.args=--game s3k --class MhzFooObjectInstance --id 0x8A --badnik"` |
| `TraceTriageTool` | Reads `target/trace-reports/<game>_<zone>_report.json` and prints a first-divergence brief (frame/field, ROM vs engine, likely owning subsystem, disasm search terms). Comparison-only; never hydrates engine state. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=s2 mtz1"` |
| `ZoneSpecNormalizerTool` | Normalizes an `s3k-zone-analysis` spec into the stable 13-section layout (palette cycling vs mutation kept separate; `(not analyzed)` placeholders for gaps). | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ZoneSpecNormalizerTool" "-Dexec.args=<path-to-zone-analysis-spec.md>"` |
| `TraceBenchmarkTool` | Replays a trace headlessly with no pacing and reports per-subsystem frame-time percentiles, for comparing JVMs or catching a performance regression. Writes a JSON report. Never quote its numbers without checking the trajectory digest matched. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceBenchmarkTool" "-Dexec.args=--trace aiz1 --json target/bench/temurin21-g1.json"` |
| `BenchmarkCompareTool` | Renders a Markdown comparison from two or more benchmark reports; the first is the baseline. Pure post-processing, so it can run under any JVM. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.BenchmarkCompareTool" "-Dexec.args=--out target/bench/comparison.md target/bench/a.json target/bench/b.json"` |

## Docs

- [runbooks/README.md](runbooks/README.md) — step-by-step runbooks per task type
- [ci-guard-failure-explainer.md](ci-guard-failure-explainer.md) — guard test → correct fix
- [pitfall-catalogue-index.md](pitfall-catalogue-index.md) — known ROM pitfalls grouped by bug class
- [documentation-obligation-checklist.md](documentation-obligation-checklist.md) — trailers / TRACE_FRONTIER_LOG / changelog
- [delegation-prompt-templates.md](delegation-prompt-templates.md) — research/impl/triage/art/review prompt templates

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

## Managed agent scratch storage

Set up durable agent scratch storage once from the canonical checkout, then verify the
host configuration:

```bash
tools/agent-scratch install
tools/agent-scratch verify
```

Installation selects the disk-backed `$OGGF_SCRATCH_ROOT`, configures Claude and Codex
to use its managed children, and installs a daily user cleanup timer. Re-run `install` if
the canonical checkout moves. `verify` checks the managed configuration and systemd units;
it reports the Claude runtime check as `unverified` when Claude is unavailable or cannot
run in the current session, which is not a successful Claude verification.

Create durable task output with the helper, rather than a repository-local scratch folder
or `/tmp`. The final output line from `new` is the unique task directory under
`$OGGF_SCRATCH_ROOT/openggf/tasks`:

```bash
tools/agent-scratch status
TASK_DIR="$(tools/agent-scratch new trace-investigation | tail -n 1)"
```

Run `status` before large captures or downloads: it reports free bytes, inodes, current
area usage, process protection, and pending keep-marker expiry. `/tmp` is reserved for
short-lived operating-system files, never agent-owned captures, diagnostics, reports, or
downloads.

The daily cleanup timer prunes task directories after seven days, quarantine after fourteen
days, and Claude/Codex support trees after thirty days. It skips a live Claude or Codex
tree and any task with an unexpired keep marker. Keep a task only for a bounded period
(at most 30 days from the command date), then archive evidence that must outlive that
window outside the managed root:

```bash
tools/agent-scratch keep "$TASK_DIR" --until YYYY-MM-DD
```

Existing Claude/Codex sessions and old output are audited rather than migrated in place.
Inspect and explicitly archive or quarantine them; a newly installed root affects new
sessions after the relevant client restarts.

### `/tmp` output audit

The scoped audit command is:

```bash
rg -n '/tmp' .agents/skills .claude/skills docs/agent-workflow tools
```

Classify each match before accepting it. Agent-workflow and headless-tool documentation are
policy/safety text; the mirrored trace skills and multi-agent runbook retain only
Windows-JVM warnings that `/tmp` is unsafe; `tools/agent-scratch` and its tests
intentionally reject or exercise `/tmp`/tmpfs roots; and the BizHawk headless test/build
recipes use a private `/tmp` only as an OS-level sandbox mount. The `.gitignore`
`/tmp_*.asm` entry is an ignored assembler-artifact glob, not an output destination. The
ignored `tools/bizhawk/trace_output*` location is a legacy Lua-recorder fallback, also not
a `/tmp` destination; new regeneration and probe recipes use a helper-created task
directory (or an explicit managed output environment variable). No copyable durable-output
command may target `/tmp`.

## Start here

Run `AgentWorkflowTool` for a preflight, read the matching runbook, scaffold with
`ObjectScaffoldTool`, intake art with `RomArtIntakeTool`, and triage traces with
`TraceTriageTool`. For performance work, start from
[`runbooks/runbook-jvm-benchmark.md`](runbooks/runbook-jvm-benchmark.md) rather
than the benchmark CLIs directly — the numbers are easy to misread.
