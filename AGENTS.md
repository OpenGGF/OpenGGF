# OpenGGF agent guidance

Keep this file and `CLAUDE.md` identical. Skills are mirrored between
`.agents/skills/` and `.claude/skills/`.

## Project and scope

OpenGGF is an alpha Java 25 reimplementation of Sonic 1, 2, and 3&K for
research and preservation. Runtime assets come from user-supplied ROMs; this
is an independent fan project with no Sega affiliation. The editor is
experimental. The unpublished Mod API candidate is implemented on the development branches;
it is outside the 0.6 release scope.

Accuracy means reproducing shipped-ROM behavior. Use the disassembly to
explain differences; do not tune gameplay to make a fixture pass. Prioritize
S3K playable routes, keep AIZ → HCZ stable, and choose later-zone work by
current route blockers and trace frontiers. Broader cleanup should serve the
requested work or remove an active risk.

Carry authorized work through verification and the user's delivery flow.
Use skills for relevant domain knowledge; load supporting references only
for the current question. Routine implementation choices do not need a
planning ceremony or another approval. User instructions override skill
workflow defaults.

## Coordination and progress

- Give delegated work a bounded outcome, owned files/worktree, focused checks
  and a stopping condition. Delegate independent work that can advance the task;
  do not create extra workers just to watch the same job or repeat an unchanged review.
- One lead owns the delivery-wide validation plan and schedules combined smoke
  coverage when the policy below requires it. Workers validate their own changes
  with relevant focused checks; do not assign the same broad run to every worker.
- Before submitting costly validation, check the task's existing queued/running
  jobs. For the same unchanged worktree/source fingerprint (including uncommitted
  inputs), selection, fixtures, flags and environment, use the existing job
  instead of submitting another copy. A completed result is
  reusable only when current policy permits it and its tested state and coverage
  match; a branch name or identical command alone is insufficient. New edits,
  failures or unresolved risk still require the affected checks.
- Keep one command session and one observer per job. Prefer completion
  notifications or a bounded wait. For lengthy jobs that need polling, back off
  to 30–60 seconds or longer, read only changed status or a bounded final
  summary, and avoid repeated full-log reads or identical progress commands.
  Report meaningful progress to the user independently of the polling cadence.
- Queue waiting is pending work, not a failed run. Keep the existing request
  alive and advance independent work while it waits. Cancel only task-owned jobs
  that are no longer needed; do not restart a request merely because admission is
  slow. Coordination never bypasses the Maven queue, changes resource limits or
  forces unrelated worktrees to serialize.
- At a work-slice boundary, handoff, compaction or changed blocker, update a
  compact checkpoint in the existing task plan/status or a task message: outcome,
  current/tested SHA and relevant uncommitted changes, ownership,
  validation selection/results/limits, live job
  handles, blocker and next action. Reuse that summary; load only the references
  needed for the next question. Do not create pass caches, validation receipts or
  extra log archives; existing diagnostic cleanup still applies.
- Distinguish queue delay, execution, user-response waits and unmeasured time in
  status reports. Parallel worker/process intervals overlap; their sum is not
  elapsed delivery time. Report unavailable timing as unknown.

## Build and verification

```bash
mvn -v                              # must report Java 25
mvn -Dmse=off compile               # build only; no test queue
mvn -Dmse=off -DskipTests package    # package without running tests; no test queue
tools/testing/install-hooks.sh     # once per worktree
python3 tools/testing/run_categories.py --list
python3 tools/testing/run_categories.py --base <pre-task-commit> --run  # bounded smoke selection unless focused validation applies
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test  # focused iteration
python3 tools/testing/maven_queue.py --lean -Dmse=off "-Dtest=TestCollisionLogic" test  # small focused tests, 1 GiB JVM heaps
python3 tools/testing/maven_queue.py -Dmse=off package -DskipTests  # build only; batched smoke validation is separate
python3 tools/testing/maven_queue.py -Dmse=off -Psmoke test -B         # explicit alias for the default smoke suite
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B        # separate fresh JVM for structural guards
# Public release preparation ONLY:
python3 tools/testing/maven_queue.py -Dmse=off -Pfull-suite test -B
```

- Surefire inherits Maven's JVM. Set `JAVA_HOME` to JDK 25 if needed.
- Use Lua 5.4 for the TraceChaser forwarder guard; set `LUA_BIN` if needed.
- Maven output belongs in the current worktree's `target/` directory. Do not share or
  copy build trees. The per-Surefire-fork LWJGL extraction uses
  `target/test-tmp`. Only test-running or uncertain Maven commands use the shared queue below. Keep
  diagnostic output bounded with targeted searches and reads.
- Use headless engine tests for behavior and offscreen captures for routine visual
  validation and promo footage. Never automate focus or keyboard input on the user's
  shared desktop. Window/focus/close diagnostics must use an owned isolated display
  and fail before Engine launch if isolation is unavailable; never fall back to the
  ambient X11/Wayland session. Keep such diagnostics separate from routine tests.
- Use JUnit 5/Jupiter. `-Dmse=off` exposes full Maven logs. PowerShell quotes
  `-D...` arguments and uses `tools/testing/install-hooks.ps1`.
- **Routine validation defaults to smoke.** Plain Maven `test`/`package`, category
  runs, pull requests and manual CI use the `smoke` selection. It excludes
  `slow-suite` exhaustive oracles and performance measurements. **Run the full
  suite ONLY when preparing a new version for public release**, explicitly with
  `-Pfull-suite`; the Release workflow selects it. Do not request or launch the
  full suite for routine development, integration, shared changes, uncertainty,
  baseline checks or post-integration checks. This supersedes older delivery
  instructions requiring full suites in those situations.
- During implementation, run focused regressions or
  `run_categories.py --category NAME --run`. For combined delivery validation,
  inspect `run_categories.py --base <integration-base>` against the pinned
  pre-task destination SHA, then run it when appropriate. Add semantic dependencies
  with `--category NAME`. Shared or unknown changes and `--category all` select
  all **smoke** categories, never the release-only full suite. Broad routine local
  coverage uses the category runner, which partitions the complete selection into
  at most 50 candidate classes per ordinary batch and releases shared admission
  between batches and before guards. Keep the combined worktree lock so another
  command cannot modify its `target/`. Raw unfiltered Maven runs remain monolithic;
  do not use them for routine broad checks or repeat testing during packaging.
  Do not manually
  narrow the runner's selection. See [test categories](tools/testing/README.md#test-categories).
- **Proportionate validation:** choose checks from changed behavior, consumers
  and plausible failure modes. Focused regression/integration checks can replace
  a broad smoke run when impact is bounded and understood and no cross-cutting
  risk remains. Inspect the change-based plan and explain a disproportionate
  selection; no further approval is needed. Shared algorithm, public contract,
  build/selection-policy, timing/physics or uncertain changes normally use the
  change-based **smoke** selection. Documentation-only changes use syntax, links,
  mirrors, policy and scenario checks. Required affected ROM/trace/domain checks
  remain separate; routine validation is not an exhaustive trace sweep.
- Plan validation for the whole delivery, using the pre-task integration SHA.
  Finish focused fixes first. Before a broad smoke run, state its candidate class
  count, expected cost and stopping rule. ROM-heavy smoke coverage can still take
  an hour overall; smoke is a selection, not a short-runtime guarantee. Batches
  provide admission opportunities for focused jobs; a single expensive class,
  memory pressure or an existing serial holder can still cause a wait. Run
  `run_categories.py --base <integration-base> --preflight` in the actual launch
  environment to check Java 25, Lua 5.4 and PowerShell. On macOS retain known
  working display/service permissions; tool preflight does not prove GLFW access.
  Complete required checks once; repeat only for new edits, failures, repaired
  prerequisites or unresolved risk. Do not rerun unchanged smoke coverage on
  baseline, development and integrated trees merely as a ceremony. Attribute
  disputed failures with bounded matched tests on baseline/current and compare
  exact identities, assertions and skips. New/worsened failures block delivery.
- A smoke/category/focused pass is bounded validation, never a full-suite pass.
  Record exact commands, outcomes, skips and coverage limits. Public releases
  retain full ordinary (`-Pfull-suite`), guards and required ROM/trace validation.
- **Queue local Maven tests.** Category `--run` commands queue automatically across
  linked worktrees. For focused tests and lifecycle commands that execute tests (including
  `package` without a skip), use
  `python3 tools/testing/maven_queue.py <maven arguments>` from the intended worktree.
  Build-only commands such as `compile`, `test-compile` and `package -DskipTests` bypass
  shared admission automatically; they hold only the current worktree's build/test lock.
  Direct `mvn` builds are also allowed, provided nothing else is writing that worktree's
  `target/`. Prefer the wrapper when builds and tests may share a worktree. Unknown plugin
  goals, alternate POMs/profiles and custom Maven launch arguments remain queued conservatively;
  inspect their behavior before running them directly as builds. The serial override applies
  to queued tests, not build-only commands. Native builds also bypass admission when tests
  are skipped, but their resource needs are separate from ordinary Java compilation.
  The exact JVM-environment flag `-Dopenggf.headless.gl=surfaceless-egl` preserves
  normal admission and lean eligibility; unknown options and resource overrides
  remain conservative. Do not clear required graphics flags to get a slot.
  Submit the command even if another agent is testing: it waits, reports status and
  starts automatically. Waiting requires no permission or manual lock cleanup.
  For small focused tests with known bounded memory needs, prefer `--lean`: it caps
  Maven and the test JVM at 1 GiB each and reserves 4 GiB / 4 cores, so these checks
  can overlap where normal 7 GiB / 8-core budgets cannot. It requires exact `-Dtest`
  selectors without profiles or custom JVM/build overrides. A lean OOM is a failed
  run; report it and rerun in the normal lane. Keep release-only full suites and memory-heavy,
  trace/native test or required domain checks in their existing lanes. Older worktrees
  can invoke the updated main-workspace wrapper by absolute path while keeping
  their own cwd and `target/`; update their tooling for the new scheduling rules.
  Linux admission allows different worktrees to overlap when conservative memory/CPU
  reservations fit (three runs by default, crediting each running job's measured usage);
  one worktree remains exclusive. Single-fork trace/audio profiles share the queue; see
  the guide for exclusive shapes. Other platforms retain serialization. Waiting requests favour short estimates; after five minutes,
  aged requests take priority in arrival order. An aged request needing shared capacity
  pauses new admissions so existing jobs can drain; an aged resource-aware request
  waiting only on its own busy worktree permits unrelated trees to proceed while
  retaining priority in its tree. Running jobs are never preempted. Temporary
  OS-leased waiting records are automatic and pruned after cancellation/death;
  older wrappers retain lock safety but cannot honour priority. See the testing guide.
  Set `OPENGGF_MAVEN_QUEUE=serial` for exclusive execution; shared
  Git policy settings and profiling are documented in `tools/testing/README.md`;
  `maven_queue.py --stats` summarises recorded queue waits, holds and peak memory.
  For per-test memory investigation, use `tools/testing/profile_test_memory.py`
  with exact expensive class selectors; it queues an isolated repeated JVM and
  records allocation, sampled peaks and post-GC retention candidates. See the
  testing guide's measurement limits; a diagnostic report is not suite validation.
  The queue holds a slot only during execution; cancellation releases a waiting
  request or stops its running Maven process tree. Keep the command session alive
  while waiting. Direct `mvn` bypasses the queue; use the wrapper for local builds/tests.
  OS locks release automatically; never delete Maven queue/slot/worktree lock files to force access.
- Category runs have a configurable **per-invocation** timeout (`--max-minutes`,
  default 40) and a 10-minute no-output timeout. Queue waiting does not count.
  A timeout means incomplete validation; inspect the cause and rerun the necessary
  checks when ready. The focused Maven wrapper preserves normal Maven output and
  exit status and adds no test timeout. Local timeouts do not alter CI/release timeouts.
- After a red broad smoke run, fix regressions caused by the change and verify those fixes
  narrowly. Attribute disputed failures with a bounded, matched baseline/current check
  of the failing tests, not broad suites. Unattributed failures remain explicitly
  unattributed. Do not fix unrelated donor paths, graphics helpers or platform tooling
  merely to turn the run green. Report remaining failures and incomplete coverage;
  never describe a partially validated candidate as fully green. Applicable CI/release gates remain
  mandatory; do not claim a green delivery from partial validation.
- For changes confined to the Python category-runner implementation/tests and its prose
  guidance (no POM, selection-policy, Java, workflow or hook changes), verify the Python
  safety suite and actual tool preflight. Do not run the engine suite to test its wrapper.
  Selection-policy/build/Java changes still follow change-based validation above.
- Do not repeat completed checks on unchanged code without a concrete reason. Diagnostics
  are temporary: inspect `results.json` (including skips and failures), then run
  `python3 tools/testing/run_categories.py --acknowledge <run-id>` before delivery to
  delete the entire run directory. Do not leave consumed results behind. Success logs,
  raw XML and invocation temporary directories are removed automatically after Maven exits.
  The next run deletes unacknowledged leftovers under the runner lock. Use
  `--keep-diagnostics` only when the user explicitly requests longer retention; opt-in
  retention is still bounded to two runs / 100 MiB and acknowledgment deletes it too.
  No validation receipts are written. Do not archive logs elsewhere to evade
  cleanup. Inspect summaries instead of streaming logs into context. The runner does not
  cache passes or enforce Git integration; queued Maven commands share its execution slot.
- CI runs smoke on develop/next after a 30-minute quiet period. Non-draft pull
  requests run branch policy, the default smoke suite and separate guards; manual
  CI uses smoke and guards too. Feature/bugfix pushes run nothing. Direct integration
  pushes are not guard-checked by CI. Master pushes run the Release workflow's
  explicit `-Pfull-suite`, guards, packaging and publication. Manual Release
  workflow dispatch is for public release preparation, validates/builds without
  publishing, and also uses full-suite. Routine CI dispatch never selects it.
- Before reporting suite results, read the measurement-hazard table in
  [briefing-trace-rounds.md](docs/agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output).
  Attribute results to the command, commit, and completed run; inspect skips.

## Level test coverage

New zone/act implementations must follow the
[zone and act testing standard](docs/guide/contributing/level-test-standard.md)
and deliver a per-act/character-route matrix linked from the
[coverage backlog](docs/status/level-test-coverage.md). Cover supported viewport,
donor, character and team breadth through real behavior, with consistent rewind
spots for events, interactions, bosses, world changes, camera locks and load/respawn
boundaries. Verify capture/restore and forward replay; test timeline isolation for
loads that intentionally reset history. Short independent checks must not sit behind
long routes. Existing level changes update affected matrix obligations and record
inherited gaps; a partial level is not certified by loading or test-file presence.
The standard and [migration plan](docs/architecture/plans/2026-09-13-level-test-standardisation.md)
define backlog work; generated coverage/prerequisite enforcement is planned, not
already installed. Existing validation scope and delivery policy still apply.

## ROM and reference setup

Discover existing root `.gen` files and pass absolute paths to ROM-backed
tests. Missing/wrong paths silently skip `@RequiresRom` tests. Do not rename,
copy, delete, or create ROM links to satisfy an example. Verify identity when
it matters:

| ROM | Test property | CRC32 | SHA-1 |
|---|---|---|---|
| S1 World REV01 | `-Dsonic1.rom.path=` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 World REV01 | `-Dsonic2.rom.path=` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3&K locked-on | `-Ds3k.rom.path=` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

Disassemblies in `docs/s1disasm`, `docs/s2disasm`, and `docs/skdisasm` are
optional development references. Builds, tests, and runtime do not require them.
Use `git submodule update --init` when needed.
Trace production/probes live in the optional pinned `tools/tracechaser/` submodule; initialize it with
`git submodule update --init --recursive tools/tracechaser` for trace work.
Follow its current guide and verified BizHawk 2.11 dependency. Use
`tools/tracechaser/...` paths; old paths are compatibility forwarders.

## Runtime invariants

1. Load every runtime asset byte through the ROM pipeline. Disassembly
   trees provide research and labels, never runtime fallback assets.
2. Shared runtime code consumes semantic rules, not game/zone-name
   carve-outs. Use `GameRules` for game-wide gates and existing
   providers/profiles/registries for narrower differences. See
   [rule placement](docs/architecture/per-game-rule-placement.md).
3. Trace fixes model ROM state and generalize to another BK2. Cite the
   owning routine for constants and branch conditions. Do not key behavior
   on a fixture, route, frame index, or fitted measurement.
4. **Trace data is comparison-only by default.** Never hydrate or sync engine
   gameplay from physics/aux rows. The only input exception is the isolated
   [dedicated hardware-timing input contract](docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md):
   It may release only the readiness of a matching, prepared, production-submitted
   ROM-backed job after kind, ordinal, stable fingerprint, and service boundary match;
   per-row lag admission may select an already-existing ROM loop. Neither
   shape supplies gameplay values, calls gameplay owners, creates work,
   uses physics/aux comparison data, or keys on frame/zone/route/game name.
   Keep authority inside the timing port and its guard. Consult the
   contract for implemented kinds and fixture coverage; scope is not proof
   of implementation or coverage.
5. V5 (`trace_schema: 5`) is the sole live trace contract across metadata,
   rows, timing, and manifests. Recorder provenance never selects behavior;
   `lua_script_version` is removed. Commit compressed trace payloads only.
6. Objects use injected `services()`, never `getInstance()`. Gameplay tile
   edits use `ZoneLayoutMutationPipeline` / `LevelMutationSurface`; editor
   commands and initial decoders are exempt.
7. Model `FixBugs = 0` / `fixBugs = 0`, matching shipped ROMs. Near conditional
   code, comment which branch is used, why, and what the fixed branch changes.

8. `mod-api-release-policy.properties` is the sole authority for branch topology,
   API version/status, and published baselines. Mod API surface/version changes
   update the descriptor, `ModApiVersion`, and normalized signature pins together.
   Candidate pins use `MAJOR.MINOR`, published pins immutable `MAJOR.MINOR.PATCH`.
   Publish only by master promotion.
9. Creator content uses `ModContext` transactions and fault boundaries; never
   trust creator-reported owners, allocate numeric mod IDs, or use standalone
   content as a ROM fallback. Assets use bounded `ModAssetRoot` + typed `LoadOp`.

## Implementation details that change decisions

- ROM `x_pos`/`y_pos` are `getCentreX()`/`getCentreY()`. `getX()`/`getY()` and
  HUD `Pos:` are top-left. Playable native writes use `NativePositionOps`.
- Object `update` receives `V_int_run_count`, not executed-frame count or
  `Level_frame_counter`. Name the ROM clock a gate actually reads.
- Preserve rewind: new objects need recreation and captured state; persistent
  global managers need a registered `RewindSnapshottable` adapter.
- Keep logic in managers rather than `Engine.java`; match nearby Java idioms.
- Read [implementation pitfalls](docs/architecture/implementation-pitfalls.md)
  for collision, tiles, headless setup, rewind, and audio source references.
  Read [AGENTS_S3K.md](AGENTS_S3K.md) for S3K half/table selection and zone work.
- S3K changes keep `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, and `TestSonic3kDecodingUtils` green.

## Delivery and documentation

A non-deletion push to `master` automatically publishes a GitHub release
after validation and builds succeed. Manual dispatch and pull requests do
not publish. The workflow creates the POM-derived version tag at the pushed
commit and rejects an existing tag, so do not tag first or reuse a released
version for a new release. Read
[release publishing](docs/project/release-publishing.md) and use the
`release-publishing` skill for publication or a skipped release job. Confirm
the release and downloads before reporting that it shipped.

Follow the user's global branch/integration workflow. Never switch the main
workspace branch. New branches use isolated `.worktrees/` checkouts and
`feature/ai-*` or `bugfix/ai-*` names, based on the current main branch.
Preserve unrelated changes, including dirty submodules.

Install and obey `.githooks/`; never use `--no-verify`. Non-master,
non-merge commits need all seven trailers (`Changelog`, `Guide`,
`Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`,
`Configuration-Docs`, `Skills`), each beginning `updated` or `n/a`.
Mapped files and trailers must agree. A source `feat`/`fix`/`perf` needs a
changelog update or an inline reason for skipping it. A merge into `develop`
touches the README release section only when it adds or changes a version
theme; most merges leave it alone, and no hook requires it.

Use the [documentation obligation checklist](docs/agent-workflow/documentation-obligation-checklist.md)
when staging. Each branch's `pom.xml` names its own release line. On
`develop`, release prose goes in `CHANGELOG.<develop version>.md`; `next`
prose stays in the root changelog's Unreleased section until promotion
(`master` is the last released version and `next` the one after `develop`; today that is master
0.6.20260911, develop 0.7.prerelease, next 0.8.prerelease; promoting them at
release time follows [release rollover](docs/project/release-rollover.md)).
Root `CHANGELOG.md` holds the release index and next's Unreleased prose
(its exact path owns the hook trailer). The README release section is only a very
high-level summary of the version's themes, not a change log; a fix to a
feature introduced in that same unreleased version folds into the existing
entry rather than earning one of its own.
Update guides/config/discrepancies when their behavior changes. Update
`docs/status/trace-frontier-log.md` when a frontier moves, a trace fix lands,
a passing trace regresses, or a sweep selects the next target; include
command, commit/worktree, errors, and first-error frame/field.

Keep engineering artifacts under the matching `docs/architecture/`
subdirectory from [docs/README.md](docs/README.md), release material under
`docs/changelog/`, and stage relevant artifacts. Durable captures belong in
an explicit task directory outside the repo; temporary Maven output stays
under `target/`.

## Preserving how the work was done

OpenGGF is an early large-scale AI-agent reimplementation. The method is
part of what the project preserves, for future contributors and for anyone
studying how it was done. Keep the record light and put things where they
already belong; do not create new document types.

- **Scripts and probes.** A one-off diagnostic stays temporary. A probe,
  oracle, comparator, or sampler that answered a question likely to recur,
  or that took more than an hour to get right, is promoted: engine-facing
  tools to `com.openggf.tools`, analysis scripts to the matching `tools/`
  subdirectory, trace production to TraceChaser. Give it a header stating
  purpose, inputs, and the originating commit or task, and a one-line entry
  in `docs/agent-workflow/README.md`.
- **Methods and lessons.** A measurement protocol, hazard, or ROM pitfall
  learned during a task goes into the existing catalogue it belongs to
  (pitfall catalogue, measurement-hazard table, implementation pitfalls),
  not a new file.
- **Decisions and evidence.** Record what was tried and rejected as well as
  what landed, with commit hashes, in the dated `docs/architecture/`
  artifact for the task. A rejected approach with its kill evidence saves
  the next agent from repeating it.
- **Narrative.** `docs/project/ai-journey.md` grows only at milestones, not
  per task.

Test: keep it if a future agent would otherwise re-derive it; delete it if
it can be regenerated from committed inputs in minutes. Raw logs, run
directories, fitted constants, and rejected code on merged branches are
never preserved.

## Find the owning reference

- Release publication/skipped jobs: `release-publishing` and
  [publishing guide](docs/project/release-publishing.md).
- Architecture/services: [engine map](docs/architecture/engine-map.md).
- Objects/bosses: matching `s1-`, `s2-`, or `s3k-implement-*` skill and
  [implementation reference](docs/architecture/object-implementation-reference.md).
- Disassembly lookup: matching `s1disasm-guide`, `s2disasm-guide`, or
  `s3k-disasm-guide` skill.
- Trace failures: `trace-replay-bug-fixing`; multiple independent traces:
  `trace-green-fleet`; video: `trace-capture`; recording: `bizhawk-headless-trace`.
- Native BizHawk screenshots or state observations for ROM corroboration:
  `bizhawk-native-reference-capture`.
- Picture or movie of any gameplay section: `gameplay-capture`; scripted
  controller input for it or for a probe: `bk2-input-authoring`.
- PLC/art queues: `plc-system`, plus `s3k-plc-system` for S3K.
- Zone work: relevant S3K zone/events/parallax/animated-tiles/palette skill;
  whole-zone delivery: `s3k-zone-bring-up`.
- Headless tests: [headless testing](docs/guide/contributing/headless-testing.md).
- Current gaps: [general](docs/status/known-discrepancies.md),
  [S3K](docs/S3K_KNOWN_DISCREPANCIES.md). Configuration: [CONFIGURATION.md](CONFIGURATION.md).

- Next-line Mod API and multiplayer contracts: [subsystem reference](docs/architecture/next-line-subsystems.md),
  [creator handbook](docs/modding/index.md), and
  [API compatibility](docs/architecture/mod-api-compatibility.md).
