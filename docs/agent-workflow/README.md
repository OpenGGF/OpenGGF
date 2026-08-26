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

## Managed agent scratch storage

Bootstrap durable agent scratch storage from a source checkout, then verify the
user-wide installed command:

```bash
tools/agent-scratch install
agent-scratch verify
```

The tracked `tools/agent-scratch` is bootstrap/source only. `install` copies it to the stable
user-wide `$HOME/.local/bin/agent-scratch`, selects the disk-backed
`$AGENT_SCRATCH_ROOT`, configures Claude and Codex to use managed children, and installs a
daily user cleanup timer whose `ExecStart` invokes that installed copy. Ensure
`$HOME/.local/bin` is on `PATH`, and re-run `tools/agent-scratch install` after helper-source
updates. Neither routine commands nor the cleanup service may depend on a checkout or
worktree; installation also retires legacy OpenGGF-named generated units. New configuration
uses `AGENT_SCRATCH_ROOT`; a matching legacy `OGGF_SCRATCH_ROOT` is accepted only as a
compatibility alias. `verify` checks the managed
configuration and systemd units; it reports the Claude runtime check as `unverified` when
Claude is unavailable or cannot run in the current session, which is not a successful Claude
verification.

Create durable task output with the helper, rather than a repository-local scratch folder
or `/tmp`. The final output line from `new` is the unique task directory under
`$AGENT_SCRATCH_ROOT/tasks`:

```bash
agent-scratch status
TASK_DIR="$(agent-scratch new trace-investigation | tail -n 1)"
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
agent-scratch keep "$TASK_DIR" --until YYYY-MM-DD
```

Existing Claude/Codex sessions and old output are audited rather than migrated in place.
Inspect and explicitly archive or quarantine them; a newly installed root affects new
sessions after the relevant client restarts.

### Certifying test-session storage

Re-run `tools/agent-scratch install` after helper-source changes and require
`agent-scratch verify` to succeed before relying on managed sessions. The test-session
coordinator independently invokes that verification and
`agent-scratch reserve-test-session --json`. The versioned response binds the canonical
managed root and allocation, storage tier `MANAGED_CODEX_TEST_SESSIONS`, filesystem device,
usable byte snapshot, inode-count status/value, canonical `lease_root`, retention deadline,
and helper version. Static verification of helper bytes, configuration, lane ownership,
writable-root policy, and unit-file content remains mandatory. When the Codex sandbox cannot
reach the user service bus, runtime timer state is reported as
`UNAVAILABLE_IN_SANDBOX` without invalidating otherwise verified static state. Unknown
service-manager failures, or any missing, stale, malformed, timed-out, unsafe, or failed
configured helper response, remain startup failures and must never fall back into the
project.

Storage tiers are selected in this order: `EXPLICIT_OVERRIDE` for
`OPENGGF_TEST_ROOT`, verified `MANAGED_CODEX_TEST_SESSIONS`, visibly warned
`PROJECT_LOCAL_FALLBACK` only when managed scratch is not configured, and
`SYSTEM_TMP_EXPLICIT` only with `--allow-system-tmp`. Before Maven starts, every tier must
prove usable bytes of at least `max(20 GiB, 5% of filesystem capacity)` and pass a live
contained create/write/flush/read/unlink inode probe. `OPENGGF_TEST_MIN_FREE_BYTES` accepts
only an unsigned decimal that raises the default; blank, whitespace-only, malformed, or
lower values fail startup. Capacity and the live inode probe are measured again at
finalisation. A managed reservation reports `inode_count_status=MEASURED` with numeric
`usable_inodes` (where zero refuses immediately), or
`inode_count_status=UNAVAILABLE_DYNAMIC` with JSON-null `usable_inodes` for a dynamic inode
pool, for which the live probe is authoritative rather than treating dynamic allocation as
measured exhaustion. Other tiers record why a numeric count is unavailable rather than
inventing one.

An explicit `--lock-root` or `OPENGGF_TEST_LOCK_ROOT` has highest lock-root priority.
Otherwise a managed reservation uses its verified `lease_root` under
`$AGENT_SCRATCH_ROOT/codex/test-session-locks`, allowing the exact default wrapper to lease
inside the existing Codex writable boundary without touching protected Git metadata.
Unmanaged tiers continue to default to the per-worktree Git lock root. The helper creates
and verifies the shared managed lane; coordinator namespace ownership, liveness, reclaim,
and deletion semantics are unchanged.

After any terminal state, automatic compaction may remove only `tmp` and
`build/test-classes/traces`. It preserves manifests, command files, terminal
`maven.log.gz`, reports,
diagnostics, ordinary resources, other compiled classes, JAR/native/package outputs,
`artifacts/`, `distribution/`, and every path in the report/artifact inventories. Use
`--retain-ephemeral` (PowerShell `-RetainEphemeral`) only when those reproducible trees are
needed for diagnosis; it records `RETAINED_BY_REQUEST` and does not extend expiry. A storage
failure turns an otherwise successful child into `STORAGE_FINALIZATION_FAILED`; a prior
child or identity failure remains primary.

The child writes to live `maven.log`. Terminal finalisation streams it into a
same-session temporary gzip, atomically publishes `maven.log.gz`, and removes the
source only after publication. The terminal manifest/end marker name the gzip;
compression failure retains `maven.log`, is additional storage-finalisation evidence,
and never changes an existing child or identity failure into success.
During forced JVM shutdown, a single shutdown owner stops the process tree and waits
for output drain before finalizing; it deliberately retains `maven.log` and records
gzip as deferred. Normal completion directory-syncs the gzip rename, publishes the
terminal manifest naming the gzip, and only then removes the source and syncs again.
If the bounded shutdown wait cannot prove drain completion, it leaves the `RUNNING`
manifest and log untouched for stale-session recovery.
Gzip publication, terminal-manifest publication, and source deletion each record a
directory-sync outcome: `SYNCED`, `UNSUPPORTED`, or `FAILED`. Native providers that
cannot open directories for sync remain certifying as `UNSUPPORTED`; real I/O errors
on a supporting provider retain recovery evidence and fail storage finalisation.

Destructive compaction requires either descriptor-relative `SecureDirectoryStream` support
or a non-null stable file key with same-store atomic tombstoning and identity revalidation.
There is no unbound pathname fallback. Native Windows on OpenJDK 21 therefore certifies as
`RETAINED_PLATFORM_UNSUPPORTED` without automatic compaction, pending a future
Actworks/Slipmat native file-ID bridge; capacity checks and retention still apply. Managed
terminal sessions expire after seven days unless kept. A live `RUNNING` lease is never
compacted or pruned; an expired stale `RUNNING` session is atomically quarantined for the
normal fourteen-day quarantine period instead of being deleted directly.

For evidence parsing, `OPENGGF_TEST_RUN_START` has exactly `run_id`, `isolation`, `lwjgl`,
`manifest`, `lease`, `log`, `state`, `storage_tier`, `launch_usable_bytes`, and
`capacity_floor_bytes`. `OPENGGF_TEST_RUN_END` has `run_id`, `isolation`, `lwjgl`,
`exit_code`, `state`, `valid`, `manifest`, `log`, `compaction_status`, `reclaimed_bytes`, and
`completion_usable_bytes`, with `process_tree_stopped` only when shutdown handling has that
result. The first group identifies the run, isolation, evidence paths and capacity gate;
the second preserves the run/identity verdict while reporting storage finalisation
separately. Marker strings are encoded so one value cannot create a counterfeit marker.

### `/tmp` output audit

The scoped audit must include POSIX and Windows temporary-root forms, ignored files, and
the ignored scratch-location rules:

```bash
rg --no-ignore -n -i '/tmp|c:\\tmp|%temp%|%tmp%' AGENTS.md CLAUDE.md .agents/skills .claude/skills docs/agent-workflow docs/architecture/designs/2026-08-14-agent-scratch-storage-design.md docs/architecture/plans/2026-08-14-agent-scratch-storage-plan.md tools .gitignore
rg -n -i 'trace_output|tmp' .gitignore
```

Classify each match before accepting it. Agent-workflow and headless-tool documentation are
policy/safety text; the mirrored trace skills and multi-agent runbook retain only
Windows-JVM warnings that `/tmp` is unsafe; the bootstrap/source helper
`tools/agent-scratch` and its tests intentionally reject or exercise `/tmp`/tmpfs roots; and
the BizHawk headless test/build recipes use a private `/tmp` only as an OS-level sandbox
mount. The `.gitignore` `/tmp_*.asm` entry is an ignored assembler-artifact glob, not an
output destination. The ignored `tools/bizhawk/trace_output*` location is a legacy
Lua-recorder fallback, also not a `/tmp` destination. `%TEMP%`/`%TMP%` matches are
acceptable only for launcher-created, short-lived wrapper/config files. New regeneration and
probe recipes use an installed `agent-scratch` task directory (or an explicit managed output
environment variable); no copyable durable-output command may target `/tmp`, `C:\tmp`,
`%TEMP%`, or `%TMP%`.

## Start here

Run `AgentWorkflowTool` for a preflight, read the matching runbook, scaffold with
`ObjectScaffoldTool`, intake art with `RomArtIntakeTool`, and triage traces with
`TraceTriageTool`. For performance work, start from
[`runbooks/runbook-jvm-benchmark.md`](runbooks/runbook-jvm-benchmark.md) rather
than the benchmark CLIs directly — the numbers are easy to misread.
