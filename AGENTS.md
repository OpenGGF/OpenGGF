# OpenGGF agent guidance

This is the sole project instruction file: Codex reads it, and Claude Code
loads it because the project has no `CLAUDE.md`. Never add one; it would
replace this file for Claude Code. Edit skills only under `.agents/skills/`;
`.claude/skills/` is a generated copy for Claude Code. Refresh it with
`python3 tools/git/sync_skills.py` and stage both trees.

## Project and scope

OpenGGF is an alpha Java 25 reimplementation of Sonic 1, 2 and 3&K for research and preservation. Runtime assets come from user-supplied ROMs; it has no Sega affiliation. The editor is experimental, and the Mod API ships in 0.7 as an unpublished candidate.

Accuracy means reproducing the shipped ROMs, never tuning to make a fixture pass. Explain differences with the disassembly. Prioritise S3K playable routes and keep AIZ → HCZ stable; pick later work by route blockers and trace frontiers. Cleanup must serve the task or remove an active risk.

Reimplement game algorithms, state and output. Do not add CPU instruction or cycle simulation, virtual VDP or beam timing, or hardware bus and register protocols. Translate hardware-facing routines into engine semantics and document what is unsupported. Sound-chip synthesis is the only emulation allowed, and it does not extend to the CPU, video or bus around it. See the [reimplementation boundary](docs/architecture/engine-map.md#reimplementation-boundary).

Carry authorised work through verification and delivery. User instructions override skill defaults.

## Coordination and progress

- Give each delegate a bounded outcome, its own files or worktree, focused checks and a stopping condition. Do not add workers that only watch or repeat a review.
- One lead owns the delivery's validation plan. Workers run focused checks on their own changes.
- Before launching costly validation, reuse an existing job only when its source, inputs, selection and environment match.
- Keep one session and one observer per job. Poll no faster than every 30–60 seconds, and keep queued jobs alive.
- At each handoff, record the outcome, tested SHA, owners, checks and their limits, live jobs, blocker and next action in the existing task status.
- Report queue wait, execution, user wait and unknown time separately. Details: [coordination rules](tools/testing/README.md#coordination-and-progress).

## Build and verification

Focused tests use the ordinary wrapper command; reviewed small classes select the
lean lane automatically. `--lean` is optional for other tests whose memory needs
are known to be bounded. A lean OOM is a failed run: report it and rerun with
`--normal`. Registry qualification is described in the
[testing guide](tools/testing/README.md#queued-maven-execution).

```bash
mvn -Dmse=off compile
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test
python3 tools/testing/run_categories.py --list
python3 tools/testing/run_categories.py --base <pre-task-commit> --run
python3 tools/testing/run_categories.py --base <pre-task-commit> --workers 2 --run
python3 tools/testing/maven_queue.py -Dmse=off package -DskipTests
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
python3 tools/testing/maven_queue.py -Dmse=off -Pfull-suite test -B # public release ONLY
```

- Run every test-running Maven command through `maven_queue.py`; category runs queue themselves. Direct `mvn` bypasses the queue; use the wrapper for local builds/tests.
- Maven output belongs in the current worktree's `target/` directory. Do not share or
  copy build trees.
- Smoke is the routine selection. The full suite is for public releases only, and `exhaustive-audit` runs only on explicit request.
- Choose checks from the change. Bounded impact gets focused tests. Shared or uncertain changes get the change-based smoke run against the pre-task destination SHA. Docs-only changes get syntax, link, mirror and policy checks. ROM, trace and domain checks are separate.
- For broad local runs use `--workers 2`, and state the class count, cost and stopping rule first. Rerun only after edits, failures or repaired prerequisites.
- Attribute a failure by running the same tests on the baseline and comparing identities, assertions and skips. New or worse failures block delivery.
- Never delete queue lock files, and never edit or build the tree during a category run.
- The runner classifies every skip with `tools/testing/release-skip-policy.json`; unexplained or required skips make validation incomplete. Report its final `VERDICT` line. Passing runs delete diagnostics automatically; inspect failed/incomplete results and delete them with `run_categories.py --acknowledge <run-id>`. Add skip-policy rules only for verified explicit opt-ins or optional inputs.
- Use Java 25, JUnit 5 and `-Dmse=off`. Guards need Lua 5.4 and PowerShell. Category `--run` checks prerequisites automatically; `--preflight` checks them without launching tests.
- GL tests need `-Dopenggf.headless.gl=surfaceless-egl`. Render headless or offscreen only; window diagnostics need an isolated display, never the shared desktop.
- The lean lane compiles with normal Maven memory and caps only test execution at 1 GiB heaps.
- Report the command, commit, outcome, skips and limits, and read the [measurement hazards](docs/agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output). A bounded pass is never a full-suite pass. Full procedures: [testing guide](tools/testing/README.md).

## Enforced correction contracts

| Contract | Owner; enforcement |
| --- | --- |
| Headless boot always installs no-device audio, even without synthesis; no fresh-JVM assumption. | `HeadlessGameBoot.installAudioBackend()`; `TestHeadlessAudioOwnership`, ROM `TestHeadlessGameBootAudioIdentity`. |
| Restore caller GL backend, including absent/empty properties, for reused JVMs. | Global `HeadlessStateTeardownExtension`; `TestHeadlessBackendPropertyIsolation`, `TestHeadlessStateTeardownGuard`. |
| New pixel fixtures use shared configured context; raw GLFW debt only shrinks. | `PresentationOffscreenTestContext.openConfigured(...)`; `TestRawGlfwTestSetupGuard` pins bytecode/method-reference callers/counts, rejects both historical fixtures. |
| Puppet/Pose pictures retain native priority/tile occlusion. | `StageplayObjectDraw` batch; ROM/EGL `TestStageplayObjectPriorityRendering`: native/frozen/baked GHZ-loop pixels, non-occluding negative control. |
| Stageplay logical Left/Right; native presentation owns flips/orientation. | Typed `Facing`, strict screenplay, `ObjectVisualFacing`; `TestStageplayFacingGuard`, exhaustive factory-bytecode `TestNativeObjectFacingGuard`, both-direction native pixels. |

## Level test coverage

New zones and acts follow the [zone and act testing standard](docs/guide/contributing/level-test-standard.md) and link their route matrix from the [coverage backlog](docs/status/level-test-coverage.md). Changes to existing levels update the affected obligations and record inherited gaps. Loading a level is not certification.

## ROM and reference setup

Queued test commands and category runs find verified root `.gen` files (then a linked worktree's main checkout) by SHA-1 and pass absolute paths; pass a property only to pin another dump. Check the wrapper's found/missing line: a missing or wrong path silently skips `@RequiresRom` tests. Never rename, copy, delete or link ROMs. Verify identity when it matters:

| ROM | Test property | CRC32 | SHA-1 |
|---|---|---|---|
| S1 World REV01 | `-Dsonic1.rom.path=` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 World REV01 | `-Dsonic2.rom.path=` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3&K locked-on | `-Ds3k.rom.path=` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

The disassemblies in `docs/s1disasm`, `docs/s2disasm` and `docs/skdisasm` are optional development references; builds, tests, and runtime do not require them (`git submodule update --init`).
Cite a label beside its lines, e.g. `loc_390FA (s2.asm:77149-77163)`. `python3 tools/disasm/disasm_citations.py check` verifies citations against the pins, and the pre-commit hook rejects new stale ones. A commit that moves a pin also runs `disasm_citations.py remap <name> <old> <new> --write`.
Trace production lives in the optional pinned `tools/tracechaser/` submodule (`git submodule update --init --recursive tools/tracechaser`); follow its guide and BizHawk 2.11 requirement.

## Runtime invariants

1. Every runtime asset byte comes through the ROM pipeline; disassemblies supply research/labels, never fallback assets.
2. Shared runtime consumes semantic rules, not game/zone names: `GameRules` for game-wide gates, providers/profiles/registries for narrower differences ([placement](docs/architecture/per-game-rule-placement.md)).
3. Trace fixes model ROM state, generalize to another BK2 and cite owning routines for constants/branches; never key on fixtures, routes, frame indices or fitted measurements.
4. **Trace data is comparison-only by default.** Never hydrate/sync gameplay from physics/aux rows. Only the isolated [dedicated hardware-timing input contract](docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md) permits input: It may release only the readiness of a matching, prepared, production-submitted ROM-backed job after kind, ordinal, stable fingerprint and service-boundary match; per-row lag admission may select an existing ROM loop. Neither supplies gameplay values, calls gameplay owners, creates work, uses physics/aux comparison data or keys on frame/zone/route/game names. The user-approved synchronous-initialization extension may also hold/resume an independently prepared ROM-backed production continuation and select observed zero-routine interrupt service after load/phase ordinals, phase kind and operation fingerprint match. It supplies no counters or decoder progress, cannot create/select work or move interrupts across an unproven clock/RNG read, and requires a real resumable load. Authority stays in the timing port/guard; consult the contract for implemented kinds/fixtures, never infer implementation/coverage from scope.
5. V5 (`trace_schema: 5`) alone governs metadata/rows/timing/manifests. Recorder provenance never selects behavior; `lua_script_version` is removed. Commit compressed trace payloads only.
6. Objects use injected `services()`, never `getInstance()`. Gameplay tile edits use `ZoneLayoutMutationPipeline` / `LevelMutationSurface`; editor commands/initial decoders are exempt.
7. Model shipped `FixBugs = 0` / `fixBugs = 0`; comment which conditional branch, why, and fixed-branch differences. The [S2 Sideways exception](docs/status/known-discrepancies.md#s2-sideways-platform-safe-admission-and-owned-child-retirement) omits failed-allocation corruption and cleared/reused-slot cross-writes: preserve bounded Java admission/lifetime, no SST/RAM emulation or relaxed strict rewind/trace checks. It sets no global fixed-bug policy.
8. `mod-api-release-policy.properties` is the sole authority for branch topology, API version/status and published baselines. Surface/version changes update descriptor, `ModApiVersion` and normalized signature pins together: candidate `MAJOR.MINOR`, immutable published `MAJOR.MINOR.PATCH`. Publish only by master promotion.
9. Creator content uses `ModContext` transactions/fault boundaries; never trust creator-reported owners, allocate numeric mod IDs or use standalone content as ROM fallback. Assets: bounded `ModAssetRoot` + typed `LoadOp`.

## Implementation details that change decisions

- ROM `x_pos`/`y_pos` mean `getCentreX()`/`getCentreY()`; `getX()`/`getY()`/HUD `Pos:` are top-left. Playable native writes: `NativePositionOps`.
- Object `update` receives `V_int_run_count`, not executed-frame count/`Level_frame_counter`; name the ROM clock read.
- Rewind: new objects need recreation/captured state; persistent managers need registered `RewindSnapshottable` adapters.
- Keep logic in managers, not `Engine.java`; match nearby idioms.
- Read [pitfalls](docs/architecture/implementation-pitfalls.md) (collision/tiles/headless/rewind/audio) and [S3K rules](AGENTS_S3K.md) (half/tables/zones).
- S3K changes keep `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils` green.

## Delivery and documentation

Follow the user's integration flow. Never switch the main workspace's branch. New branches use isolated `.worktrees/` checkouts named `feature/ai-*` or `bugfix/ai-*`, based on the current main branch. Preserve unrelated work and dirty submodules.

Install the hooks with `tools/testing/install-hooks.sh` (PowerShell: `tools/testing/install-hooks.ps1`) and never use `--no-verify`. Non-master, non-merge commits carry seven trailers, `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs` and `Skills`, each starting `updated` or `n/a` and matching the staged files. A source `feat`, `fix` or `perf` needs a changelog entry or an inline reason.

Development prose goes in the `CHANGELOG.<version>.md` named by the branch's `pom.xml` version (develop: `CHANGELOG.0.8.prerelease.md`); the `Changelog: updated` trailer maps only to root `CHANGELOG.md`, the release index. Fold fixes to unreleased features into their existing entry. Use the [staging checklist](docs/agent-workflow/documentation-obligation-checklist.md) for frontier-log, artifact, version and branch obligations, and [release rollover](docs/project/release-rollover.md) for promotions. The README release section changes only when a merge adds or changes a version theme.

Any non-deletion push to `master` publishes a release; PRs and manual dispatch do not. Never tag first or reuse a version. Use the `release-publishing` skill and [guide](docs/project/release-publishing.md), and confirm the release and downloads.

## Preserving how the work was done

Promote a probe that answered a recurring question or took over an hour to get right, with a header (purpose, inputs, origin) and an index entry. Put lessons in the existing catalogues, and record rejected approaches with their evidence and commits in the dated task artifact. Create no new document types and keep no raw or regenerable output. Details: [preserving methods](docs/agent-workflow/README.md#preserving-how-the-work-was-done).

## Find the owning reference

- [Skill/doc map](docs/agent-workflow/README.md#find-the-owning-reference): architecture, objects/bosses, disassembly, traces, captures/input, promos, PLC, zones, rendering, UI, mods, gaps/configuration.
- ROM implementation: matching `s1-`, `s2-`, `s3k-` object/boss/disassembly/zone/PLC skills.
- Replay: `trace-replay-bug-fixing`, `trace-green-fleet`; footage: `trace-capture`, `gameplay-capture`, `gameplay-highlights`, `promo-direction`.
- Native references: `bizhawk-headless-trace`, `bizhawk-native-reference-capture`, `bk2-input-authoring`; rendering/UI: `headless-rendering`, `ui-design-language`; creation: `stageplay-authoring`, `mod-authoring`.
