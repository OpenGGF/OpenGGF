# Testing utilities

OpenGGF uses Maven directly. Build and test output stays below the current
worktree's `target/` directory.

Install the repository hooks once per worktree:

```bash
tools/testing/install-hooks.sh
```

PowerShell uses `tools/testing/install-hooks.ps1`.

## Test categories

Use Python 3.11+ and Java 21. The runner uses the existing Maven defaults and exclusions;
it does not move tests, alter CI/release coverage, or remove slow tests from a selected category.

```bash
python3 tools/testing/run_categories.py --list
# Compact plan: categories, candidate counts and changed-path reasons.
python3 tools/testing/run_categories.py --base develop
# Validate a finished change against its actual integration base, including local edits.
python3 tools/testing/run_categories.py --base develop --run
# Add coverage for a semantic dependency beyond the path rules.
python3 tools/testing/run_categories.py --base develop --category audio --run
# Focused development; not a substitute for the change-based delivery command.
python3 tools/testing/run_categories.py --category physics --run
# Full ordinary suite and guards, including for release preparation.
python3 tools/testing/run_categories.py --category all --run
```

Use the actual integration branch or pinned base SHA instead of `develop` where appropriate.
The diff includes committed, staged, unstaged, deleted, renamed and untracked files. A rename
checks both old and new paths. Explicit categories are additive: they cannot override an
unknown change's full-suite fallback. Without `--run`, no Maven process starts. Add `--json` only when the complete class
inventory is needed; default plans cap changed-path detail at 50 entries to keep output bounded.

| Category | Coverage |
|---|---|
| `common` | Shared and unclassified ordinary tests; included in every category run |
| `physics` | Movement, collision, sensors, solid objects and playable sprites |
| `gameplay` | Objects, bosses, zone events, stages and game flow |
| `audio` | Audio drivers, synthesis, presentation and audio tooling, including exhaustive oracles |
| `rendering` | Graphics, palettes, scrolling, sprite art and viewports |
| `rewind` | Rewind, recording, playback and save state |
| `content` | ROM decoding, compression and resource loading |
| `mods` | Mod API, creator content and SDK tools |
| `network` | Networking and presence integration |
| `tooling` | Editor, configuration, diagnostics, launchers and tool tests |

Ownership lives in `test-categories.json`. Package rules select a primary category; class-name
rules add cross-cutting categories for legacy locations. Recognized game/zone names
assign otherwise unowned legacy tests to gameplay without pulling audio-owned S2
oracles into gameplay. Categories overlap. New ordinary
classes without a narrower owner fall into `common`, so naming changes cannot silently drop
them. Candidate counts include Maven-discovered helper/abstract classes, not just executed tests.
The union covers the ordinary source inventory; Maven's existing tags and exclusions still apply.

Change rules are deliberately narrower than test ownership. Physics changes also select
related gameplay, rendering and rewind tests. Audio changes also select rewind and tooling.
Game-specific object changes select gameplay, physics, rendering, rewind and content. Ordinary
documentation changes select tooling, so updating a changelog does not force audio sweeps.
Shared clocks, game-loop code, ROM pipelines, configuration, resources, build files, test
infrastructure and unclassified changes select **all** ordinary categories. A new narrow rule
must document its dependent categories and include a selector regression test. The selector is
an explicit workload policy, not a proof of semantic independence: agents must add categories
or choose `all` when a change crosses these boundaries (for example, changing an object's audio
request contract). Do not add a rule just to avoid an inconvenient failing test.

Change-based runs (`--base`) and `--category all` include the full `guards` profile in its
own Maven invocation. Focused category runs omit guards to avoid repeating them during
iteration; add `--guards` to request them explicitly. Trace
replay, native graphics, diagnostics, performance profiles and TraceChaser integration retain
their existing commands and prerequisites. Gameplay category coverage does not replace affected
replay fixtures or the domain skill's required checks. A category run is never a full trace sweep.

Use `--workers 2` to opt into the `test-concurrent` Maven profile for the ordinary lane.
The default is one reused JVM; the opt-in uses two reused JVMs, each with a 3 GiB maximum
heap and serial JUnit execution. Allow memory for both heaps, Maven and native allocations.
Guards keep their separate single-worker invocation. Selection is unchanged; compare matched runs before choosing two workers on a new machine.
This distributes test classes between JVMs, so one long class cannot use both workers.

The ordinary FBZ matrix keeps one full native route plus independent compatibility
checks. `mvn -Dmse=off -Pfbz-routes test -B` selects the exhaustive eleven-route
matrix instead; supply all three verified ROM paths as described in the
[headless guide](../../docs/guide/contributing/headless-testing.md#fbz-compatibility-and-exhaustive-routes).
Run that additional lane for FBZ traversal changes and exhaustive ROM validation.
The category runner's ordinary/guard result does not include this lane.

The runner discovers existing root `.gen` files by the documented SHA-1 identities and passes
absolute ROM paths. It never creates ROM links or copies. Missing ROMs still require inspecting
skips; a successful exit alone does not establish ROM-backed coverage.

Each run starts in `target/category-tests/<run-id>/`. The plan retains the tested head,
working-tree fingerprint, ordinary worker count and selected source classes; command arrays record the exact Maven
arguments. `results.json` records each lane's worker count, totals, skipped cases and failure details, and `status.json`
distinguishes passed, failed and incomplete runs. Diagnostic lists are capped at 1,000 records
per kind with explicit omitted counts; messages/stacks are bounded excerpts. Inspect skips,
omissions and domain coverage before delivery. The runner checks exit status and nonempty
execution and rejects a changed working tree at completion. It does not cache or authenticate
passes for Git hooks, or claim that every candidate source produced a report.

### Cost, prerequisites and stopping

Run `python3 tools/testing/run_categories.py --base <pinned-base> --preflight` in the
same environment as the intended test command. Every `--run` also performs these checks:
Maven must use Java 21; selections with guards require executable Lua 5.4 (`LUA_BIN`,
default `lua`) and `pwsh` on PATH. Failures are collected before any test lane starts.
This checks tool launches, not ROM completeness, native display access or every fixture
prerequisite. On macOS use the known working native display/service permissions from the
first graphics launch, especially after a documented sandbox failure.

Full selections and selections of at least 500 candidate classes print a cost warning.
The September normalization run measured about 24 minutes ordinary and 10 minutes guards;
this is historical context, not a prediction. Category `--max-minutes` defaults to
**40 minutes per invocation across Maven lanes**, including compilation, and can be
adjusted for the intended checks. There is also a **10-minute no-output timeout**.
Both terminate the Maven process tree and report incomplete validation. Queue waiting
is excluded. Tool probes have separate 20-second limits.

Plan the combined checks against the pre-task integration SHA, finish focused fixes first,
and inspect failures before deciding what needs another run. No task registration,
receipts, cumulative budget, retry authorization or manual timing records are required.
Repeated checks should have a reason, such as changed code or repaired prerequisites;
there is no hard attempt limit. Partial or interrupted coverage never certifies a pass.

### Queued Maven execution

Category `--run` commands automatically wait for a shared Maven slot across all linked
worktrees. For focused tests or any other Maven invocation, run this from the intended
worktree (PowerShell accepts the same Python command):

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test
python3 tools/testing/maven_queue.py -Dmse=off package
```

Submit checks when ready, even while another agent is testing. Leave the command session
running: it prints a waiting notice every 30 seconds and starts automatically when it
acquires a slot. On Linux, the queue admits up to two invocations in different
worktrees when memory and CPU budgets fit; category ordinary/guard lanes stay together.
The same worktree always remains exclusive. Unsupported resource probes/platforms
and CPU allocations smaller than two per-run reservations retain serial execution.
There is no background service, task owner or approval step. The OS chooses
among waiting processes; strict FIFO ordering is not promised. Different clones have
separate queues. Each command still uses its own worktree and `target/` directory.

The wrapper forwards Maven arguments without shell interpolation, streams normal output,
and returns Maven's exit status. It adds no timeout to a focused/raw Maven command.
Ctrl-C or SIGTERM cancels a waiting request or stops the running Maven process tree before
releasing the slot. Category runs retain their timeouts and diagnostic summaries. Category
selection is recomputed after waiting so it describes the tree actually being tested.
Avoid editing that worktree during execution; the category runner rejects changed trees.

Admission uses available memory, CPU affinity, visible cgroup v2 limits and one-minute
system load. Each prospective run reserves **7 GiB RAM and 8 CPU cores**, with **2 GiB
memory headroom**. Existing runs also count as full reservations, in addition to their
usage already reflected in OS counters. This deliberate overestimate covers startup
bursts; it may queue work even when a less conservative estimate would fit. These are
admission estimates, not hard limits or a guarantee against unrelated host load.
No running command is preempted when resources later fall. The estimate covers the
single-worker default suite and `smoke`/`guards`. Category `--workers 2`, other explicit
Maven profiles, Maven thread/fork/heap overrides, and nonempty `MAVEN_OPTS`,
`JAVA_TOOL_OPTIONS` or `JDK_JAVA_OPTIONS` retain exclusive execution. Implicit profiles
or heap settings in custom project/user Maven configuration are not detected; use the
serial override for those unmeasured shapes.

Use `OPENGGF_MAVEN_QUEUE=serial` for an exclusive run (in PowerShell, set
`$env:OPENGGF_MAVEN_QUEUE = 'serial'`). The default is `auto`. Serial callers and the
previous single-lock wrapper exclude resource-aware callers in both directions.
Shared Git settings tune the policy across linked worktrees:

```bash
git config openggf.mavenMaxRuns 2
git config openggf.mavenMemoryGiB 7
git config openggf.mavenCpuCores 8
git config openggf.mavenHeadroomGiB 2
```

Values must be positive and finite; `mavenMaxRuns` is an integer from 1 to 64. Change
settings between runs: callers read policy when they enter the queue. A request whose
budget cannot fit waits until resources or the request's configuration change; cancel
and resubmit after changing settings. Separate clones have separate reservations.

The shared Git directory contains `maven-queue.lock`, an OS-managed compatibility lock,
`maven-admission.lock` for atomic admission, and numbered `maven-slot-*.lock` leases.
Each worktree's Git directory also contains `maven-worktree.lock`. Their
presence is **not** evidence of an active run. Never delete these files to force access:
that could create two independent locks. Normal exits and handled cancellation release
them automatically; dead waiting processes leave no queue entries. On POSIX the Maven child
inherits all execution lock descriptors as protection against a killed Python parent. After a forced
kill, especially on Windows, check for surviving Maven/JVM processes before further work.
This is local coordination, not a sandbox against arbitrary commands.

Direct `mvn` and runners predating the single-lock queue do not participate. Use the
wrapper for local Maven builds/tests. The immediately preceding single-lock queue is
compatible and remains exclusive. Existing `openggf-validation/task.json`, `task.lock` and
`target/category-tests-last-broad.json` files are ignored, not migrated or deleted;
they do not authorize or block new runs. CI and release commands/gates are unchanged.

Changes confined to the Python runner/tests and this prose guidance use the Python safety
suite below plus actual tool preflight. Changes to selection policy, POM, Java, workflows
or hooks still require their normal change-based validation. This avoids launching tens
of thousands of engine tests to verify timeout, subprocess and retention behavior.

### Exploratory resource profiling

The optional profiler requires `psutil` in the Python launch environment. It invokes the
category runner, so queueing, timeouts, ROM discovery and diagnostics remain unchanged:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/profile_maven.py --output target/maven-profile.json -- --category all --run
```

It samples descendant process RSS and cumulative user/system CPU once per second and
writes an aggregate JSON every 30 samples and at exit. Wall time and mean CPU include
queue wait; profile an uncontended queue for an isolated estimate. RSS sums shared pages
and can overcount physical memory; sampling misses short-lived processes and peaks,
so CPU time is a lower bound. It measures ordinary and guard lanes, not separate
trace/native profiles. Inspect category results and skips before describing coverage.
`complete` means the runner exited, not that tests passed. Record useful measurements
and conditions in the task's existing architecture artifact; remove temporary JSON
and acknowledge category diagnostics after consuming them.

### Automatic storage cleanup

Acknowledgment retains exclusive queue locking but does not require CPU or memory
headroom: it deletes metadata and never launches Maven.

Diagnostics are temporary, not a run history. After inspecting `results.json` (including
skip reasons and failures) and any relevant log tail, acknowledge consumption:

```bash
python3 tools/testing/run_categories.py --acknowledge <run-id>
```

This deletes the **entire run directory**, including summaries, plans and commands, without
running Maven. It waits for the same execution slot as validation before cleanup. Only exact runner IDs are accepted, never paths or symlinked directories.
A repeated acknowledgment is harmless. Agents must acknowledge consumed results before
delivery; there is no background process that can detect when a human has read a file.

- Success logs and raw XML are deleted immediately after extracting results. The compact
  results remain temporarily so skips can be inspected before acknowledgment.
- Failed/interrupted results include rolling log tails until acknowledged. Each Maven
  invocation keeps two chunks of at most **2 MiB each** while running. Read `.log.1` before
  `.log`. Raw XML is deleted after summary extraction.
- Before launching another run, the runner deletes all unacknowledged, recognized prior
  run directories, including legacy retained runs. This also cleans abandoned diagnostics;
  it never follows symlinks or prunes unrelated target contents.
- Only an explicit `--keep-diagnostics` on `--run` retains a run across later launches.
  Use it only at the user's request. Opt-in retention remains bounded to **two runs /
  100 MiB**, and acknowledgment deletes these folders too. Raw XML and temporary fixture
  data are still removed; this flag retains bounded logs and JSON, not complete build output.
- Each invocation uses its own `openggf.test.tmpdir` and `TMPDIR`/`TMP`/`TEMP`, deleted after
  Maven's process tree exits, including on handled interruption. After a force-killed runner,
  confirm its processes exited. The next launch removes leftovers.

No validation receipt or pass cache is written. The temporary storage needed by an active
test is not limited by the retention limit. Build caches, ROMs, unrelated target folders
and other worktrees are not pruned. Do not copy local logs into another archive to evade
cleanup. Dedicated release/partition evidence uses its existing explicit workflow below.

The selector, queue and retention tests run in the CI smoke job. Run them locally after changes:

```bash
python3 -m unittest discover -s tools/testing -p 'test_run_categor*.py'
```

## Complete Surefire outcome inventories

The PowerShell utilities in this directory export, validate, partition, and
compare complete Surefire outcome inventories:

- `Export-SurefireOutcomeInventory.ps1` converts one or more report roots into
  an ordinal-sorted TSV.
- `Compare-SurefireOutcomeInventory.ps1` compares candidate outcomes with one
  or more parent inventories.
- `New-SurefirePartitionMap.ps1` creates deterministic class partitions for a
  suite that cannot complete as one monolithic run.
- `Test-SurefireOutcomeInventory.ps1` exercises the inventory contract.

An inventory source file contains one fully qualified selected top-level class
per line. A testcase belongs to that root when its XML `classname` is exactly
the root or begins with the exact `root + '$'` boundary. Duplicate testcase
identities are fatal unless a reviewed cardinality file declares the exact
identity, count, and reason.

An authenticated explicit-source export is atomic. Retain all of these
artifacts from the run being exported:

- the ordinal-sorted top-level source-class inventory;
- its exact ordinal-bijective slash-path selector file, supplied to Maven by
  one canonical absolute `surefire.includesFile` argument;
- the exact Maven argument vector, one argument per line;
- the effective POM generated with the same profiles and property overrides;
  and
- the exact `OPENGGF_RUNTIME_INPUTS` value used for the run. It contains the
  canonical selector, Maven-argument inventory, and effective-POM paths exactly
  once for a direct capacity override, plus the reviewed repeated-identity
  cardinality file exactly once when that file is used.

Direct Maven may use an explicit `surefire.argLine` only as a capacity
override. A certifying invocation must contain exactly one explicit value for
each of `surefire.includesFile`, `surefire.argLine`, `surefire.forkCount`,
`surefire.reuseForks`, and `surefire.runOrder`; fork count and reuse must be `1`
and `true`, while run order must be `alphabetical`. The resolved arg line
must preserve exact CDS and Mockito-agent semantics, may retain macOS
`-XstartOnFirstThread`, and must end in the proven-sufficient `-Xmx3g` heap.
The raw Maven vector may instead carry exactly
`${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g`, with the leading
`-XstartOnFirstThread` only when the effective project profile has that same
prefix. The effective execution must expand this canonical template before the
exporter authenticates its final tokens.
Maven may leave the exact `${settings.localRepository}` prefix unresolved in
the effective Mockito property, project argLine, or execution argLine. In that
case, pass the canonical physical repository directory separately as
`-MavenLocalRepositoryPath`. The exporter accepts that evidence only for the
exact `org/mockito/mockito-core/<version>/mockito-core-<version>.jar` suffix,
requires that exact jar to exist below the non-reparse repository path, and
substitutes only within each field's single expected Mockito `-javaagent:`
token before authenticating the resolved JVM arguments. The placeholder is
rejected in every other project or execution token, including temp and LWJGL
paths, and multiple occurrences in either field are rejected. The resolved
project and execution agents must match the same effective Mockito artifact
path and version. Omit this parameter when all effective Mockito paths are
already absolute. This is Maven-environment resolution evidence, not runtime
input selection, so its canonical path must occur zero times in
`OPENGGF_RUNTIME_INPUTS`, including normalized-equivalent forms.
The selected Surefire execution must prove the same resolved JVM arguments and
then exactly the target-local `java.io.tmpdir` and fork-local LWJGL extraction
properties. Any other raw `${...}` placeholder, every raw Surefire `@{...}`
placeholder, including the empty `${}` and `@{}` forms, or an unresolved
placeholder in the final authenticated tokens or paths fails closed, as do
duplicate or mismatched evidence, external temp paths, and shared LWJGL paths.
The 3-GiB value is proven sufficient by the recorded capacity run; it is not a
claim that 3 GiB is the minimum usable heap.
Unlike historical managed-session evidence, direct mode neither supplies nor
accepts invented adapter-owned `user.home`, LWJGL, session-root, or run-id
suffixes.

For example, this records a truthful one-fork/3-GiB direct invocation. The
class and selector inventories must already contain the complete selected
suite described above:

```powershell
$worktree = (Resolve-Path .).Path
$evidence = Join-Path $worktree 'target/surefire-inventory-evidence'
$classes = Join-Path $evidence 'ordinary-classes.txt'
$selector = (Resolve-Path (Join-Path $evidence 'ordinary.includes')).Path
$effectivePom = Join-Path $evidence 'ordinary-effective-pom.xml'
$argumentInventory = Join-Path $evidence 'ordinary-maven-arguments.txt'
$effectiveProjectArgLine = (& mvn -Dmse=off help:evaluate `
    -Dexpression=surefire.argLine -q -DforceStdout).Trim()
$mavenLocalRepository = (& mvn -Dmse=off help:evaluate `
    -Dexpression=settings.localRepository -q -DforceStdout).Trim()
$mavenLocalRepository = (Resolve-Path -LiteralPath $mavenLocalRepository).Path
$macLauncher = if ($effectiveProjectArgLine.StartsWith(
        '-XstartOnFirstThread ', [StringComparison]::Ordinal)) {
    '-XstartOnFirstThread '
} else {
    ''
}
$capacityArgLine = $macLauncher +
    '${test.cds.argLine} ${mockito.agent.argLine} -Xmx3g'
$capacityProperties = @(
    '-Dmse=off'
    "-Dsurefire.argLine=$capacityArgLine"
    '-Dsurefire.forkCount=1'
    '-Dsurefire.reuseForks=true'
    '-Dsurefire.runOrder=alphabetical'
    "-Dsurefire.includesFile=$selector"
)
$mavenArguments = @($capacityProperties) + 'test'

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
& mvn @capacityProperties help:effective-pom "-Doutput=$effectivePom"
[IO.File]::WriteAllLines($argumentInventory, $mavenArguments,
    [Text.UTF8Encoding]::new($false))
$argumentInventory = (Resolve-Path $argumentInventory).Path
$env:OPENGGF_RUNTIME_INPUTS = @(
    $selector, $argumentInventory, $effectivePom
) -join `
    [IO.Path]::PathSeparator
& mvn @mavenArguments

& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory $classes `
    -SelectorPatternInventory $selector `
    -MavenArgumentInventory $argumentInventory `
    -RuntimeInputs $env:OPENGGF_RUNTIME_INPUTS `
    -EffectivePomPath $effectivePom `
    -MavenLocalRepositoryPath $mavenLocalRepository `
    -ReportRoot (Join-Path $worktree 'target/surefire-reports') `
    -DirectMaven `
    -CanonicalWorktree $worktree `
    -OutputPath (Join-Path $evidence 'ordinary-outcomes.tsv')
```

Run the exporter from PowerShell so multiple report roots remain an array:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -ReportRoot @(
        ./target/surefire-reports
    ) `
    -DirectMaven `
    -CanonicalWorktree (Resolve-Path .).Path `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

`-DirectMaven` makes expected-red reports usable without inventing coordinator
session values. It requires the explicit canonical worktree and accepts report
roots only at or below that worktree's `target/surefire-reports`; volatile
worktree paths are normalized to `<WORKTREE>`. Direct mode rejects symbolic
links and reparse points in the worktree/report ancestry and anywhere below a
report root before reading XML, so lexical containment cannot hide an external
report tree. Historical managed-session
evidence remains supported without `-DirectMaven`, where any red outcome still
requires the complete `CanonicalWorktree`, `SessionRoot`, and `RunId` provenance
set.

The export schema is:

```text
identity class method outcome red_kind exception_type normalized_message red_body_bytes red_body_sha256 report
```

Outcomes are `PASS`, `FAILURE`, `ERROR`, or `SKIPPED`. Red bodies have LF line
endings before their complete UTF-8 byte length and streaming SHA-256 are
recorded. Textual TSV values use the reversible escape layer implemented by
the exporter and validator.

Compare candidate and parent inventories with:

```powershell
& ./tools/testing/Compare-SurefireOutcomeInventory.ps1 `
    -ParentInventoryPath ./evidence/parent-outcomes.tsv `
    -CandidateInventoryPath ./evidence/candidate-outcomes.tsv `
    -OutputPath ./evidence/parent-candidate-comparison.tsv
```

If a monolithic suite cannot produce a complete inventory, create a stable
union map and run every non-empty per-tree selector:

```powershell
& ./tools/testing/New-SurefirePartitionMap.ps1 `
    -NextClassInventory ./evidence/next-classes.txt `
    -DevelopClassInventory ./evidence/develop-classes.txt `
    -CandidateClassInventory ./evidence/candidate-classes.txt `
    -SlotSize 75 `
    -OutputPath ./evidence/surefire-partitions.tsv
```

A partial monolithic run is not a suite result. Retain it as failed-run
evidence; only a complete monolithic inventory or complete deterministic
partition aggregate may be reported.

Run the literal fixture suite with:

```powershell
pwsh -NoProfile -File tools/testing/Test-SurefireOutcomeInventory.ps1
```

## Trace fixture validation

The remaining trace validation scripts in this directory validate committed
metadata, run manifests, timing sidecars, and fixture compression. They are
independent of the Maven launcher and continue to operate on caller-supplied
paths.
