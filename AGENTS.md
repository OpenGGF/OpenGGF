# OpenGGF agent guidance

Keep this file and `CLAUDE.md` identical. Skills are mirrored between
`.agents/skills/` and `.claude/skills/`.

## Project and scope

OpenGGF is an alpha Java 21 reimplementation of Sonic 1, 2, and 3&K for
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

## Build and verification

```bash
mvn -v                              # must report Java 21
tools/testing/install-hooks.sh     # once per worktree
python3 tools/testing/run_categories.py --list
python3 tools/testing/run_categories.py --base <pre-task-commit> --run  # combined delivery selection unless proportionate validation applies
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test  # focused iteration
python3 tools/testing/maven_queue.py -Dmse=off package              # full ordinary suite plus packaging
python3 tools/testing/maven_queue.py -Dmse=off -Psmoke test -B         # what every branch push runs in CI
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B        # separate fresh JVM for structural guards
```

- Surefire inherits Maven's JVM. Set `JAVA_HOME` to JDK 21 if needed.
- Use Lua 5.4 for the TraceChaser forwarder guard; set `LUA_BIN` if needed.
- Maven output belongs in the current worktree's `target/` directory. Do not share or
  copy build trees. The per-Surefire-fork LWJGL extraction uses
  `target/test-tmp`. Local Maven commands use the shared queue described below. Keep
  diagnostic output bounded with targeted searches and reads.
- Use JUnit 5/Jupiter. `-Dmse=off` exposes full Maven logs. PowerShell quotes
  `-D...` arguments and uses `tools/testing/install-hooks.ps1`.
- During implementation, run focused tests or `run_categories.py --category NAME --run`.
  Before delivery, unless the proportionate-validation exception below applies, use
  `run_categories.py --base <integration-base> --run` against the
  actual destination/base commit (not HEAD to hide committed work). Review the plan;
  `--category NAME` adds semantic dependencies the path rules cannot infer. Never
  narrow a runner invocation’s selection manually. See [test categories](tools/testing/README.md#test-categories).
- The change-based runner selects related ordinary categories plus common tests and
  runs all structural guards in a separate JVM. This replaces the unconditional
  local full-suite requirement for changes covered by the policy. Shared or unknown
  changes automatically select the full ordinary suite; use `--category all` when
  impact is uncertain. Run affected trace fixtures and domain-mandated checks as well:
  ordinary categories do not cover the separate trace/native/diagnostic profiles.
- **Proportionate validation:** choose local test scope from the actual behavior changed,
  its consumers, and plausible failure modes, not file location or the runner's fallback
  classification alone. Focused validation may replace the local broad run when impact
  is bounded and understood, relevant production paths and edge cases can be exercised
  directly, and there is no unresolved cross-cutting risk. This applies to small fixes,
  configuration/registration changes, and other localized work; line count alone is not
  evidence of low risk. Inspect the change-based plan before deciding. If its selection
  is disproportionate, explain why and run the relevant regression, integration, and
  domain-mandated checks instead. Prefer a regression test that reproduces the reported
  failure. No extra user approval is needed when these conditions hold. Shared algorithm,
  public contract, build/selection-policy, timing/physics changes, or uncertain impact
  still require normal change-based validation. Record commands, results/skips, and
  coverage limits; call focused validation what it is, never a full-suite pass. Do not
  edit the runner's selection to manufacture a narrower broad run. CI and release gates
  remain unchanged. Documentation-only follow-ups do not require repeating engine tests.
- Before a broad run, state the selected class count, expected cost and stopping rule.
  Broad normalization measured about 24 minutes ordinary plus 10 minutes guards; do not
  present this as a short check. Finish focused fixes and documentation first. Run
  `run_categories.py --base <integration-base> --preflight` to check Java 21, Lua 5.4 and
  PowerShell in the actual launch environment. On macOS, use the known working native
  display/service permissions from the first graphics run; tool preflight does not prove
  GLFW access. Do not rediscover a documented sandbox failure with another full run.
- Plan validation for the whole requested delivery, not separately for each commit.
  Pin the pre-task integration SHA and use it for the combined change-based selection.
  Finish focused fixes first; repeat completed checks only for changed code, repaired
  prerequisites, failures or an unresolved risk. There is no task registration,
  cumulative time budget, receipt, or one-attempt gate.
- **Queue local Maven work.** Category `--run` commands queue automatically across
  linked worktrees. For focused tests, packaging and other Maven commands, use
  `python3 tools/testing/maven_queue.py <maven arguments>` from the intended worktree.
  Submit the command even if another agent is testing: it waits, reports status and
  starts automatically. Waiting requires no permission or manual lock cleanup.
  Linux admission allows different worktrees to overlap when conservative memory/CPU
  reservations fit (two runs by default); one worktree remains exclusive. Other platforms
  retain serialization. Set `OPENGGF_MAVEN_QUEUE=serial` for exclusive execution; shared
  Git policy settings and profiling are documented in `tools/testing/README.md`.
  The queue holds a slot only during execution; cancellation releases a waiting
  request or stops its running Maven process tree. Keep the command session alive
  while waiting. Direct `mvn` bypasses the queue; use the wrapper for local builds/tests.
  OS locks release automatically; never delete Maven queue/slot/worktree lock files to force access.
- Category runs have a configurable **per-invocation** timeout (`--max-minutes`,
  default 40) and a 10-minute no-output timeout. Queue waiting does not count.
  A timeout means incomplete validation; inspect the cause and rerun the necessary
  checks when ready. The focused Maven wrapper preserves normal Maven output and
  exit status and adds no test timeout. CI/release commands and gates are unchanged.
- After a red broad run, fix regressions caused by the change and verify those fixes
  narrowly. Attribute disputed failures with a bounded, matched baseline/current check
  of the failing tests, not two full suites. Unattributed failures remain explicitly
  unattributed. Do not fix unrelated donor paths, graphics helpers or platform tooling
  merely to turn the run green. Report remaining failures and incomplete coverage;
  never describe a partially validated candidate as fully green. CI/release gates remain
  mandatory and unchanged; do not claim a green delivery from partial validation.
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
- CI still runs `-Psmoke` on pushes and full tests plus `-Pguards` on pull requests and
  manual dispatch. Releases retain full ordinary, guard and required ROM/trace validation.
  Category runs are partial validation, never evidence that the full suite passed.
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
