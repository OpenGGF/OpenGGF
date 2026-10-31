# OpenGGF agent guidance

Keep `AGENTS.md`/`CLAUDE.md` byte-identical and `.agents/skills/`/`.claude/skills/` mirrored.

## Project and scope

Alpha Java 25 Sonic 1/2/3&K reimplementation for research/preservation; user-supplied ROMs, no Sega affiliation. Editor experimental; unpublished development-branch Mod API outside 0.6.
Reproduce shipped ROMs, not fixtures; explain differences with disassembly. Prioritize S3K routes/AIZ → HCZ stability, then blockers/frontiers. Cleanup serves the task or active risk.
Implement game algorithms/state/output, not CPU instruction/cycle simulation, virtual VDP/beam timing or hardware bus/register protocols. Translate hardware-facing routines to engine semantics; document unsupported behavior. Only sound-chip synthesis permits emulation, excluding surrounding CPU/video/system bus. External emulators remain references; trace/timing restrictions remain. See [boundary](docs/architecture/engine-map.md#reimplementation-boundary).
Finish authorized verification/delivery; use relevant skills/references without routine approval ceremonies. User instructions override skills.

## Coordination and progress

- Delegate independent bounded outcomes/files/worktrees/checks/stopping conditions; no duplicate observers/reviews.
- One lead plans delivery validation; workers run focused checks.
- Reuse only matching source/inputs/selection/environment and policy-permitted coverage.
- One session/observer per job; poll ≥30–60s, keep queued jobs alive, cancel only unneeded owned jobs.
- Checkpoint outcome, current/tested SHA, edits/owners, checks/limits/jobs, blocker/next action in existing status; no caches/receipts/archives.
- Separate queue/execution/user-wait/unknown time; overlapping durations are not elapsed time. Follow [coordination rules](tools/testing/README.md#coordination-and-progress).

## Build and verification

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

- Queue test-running/uncertain Maven; categories queue automatically. Direct `mvn` bypasses the queue; use the wrapper for local builds/tests.
- Maven output belongs in the current worktree's `target/` directory. Do not share or
  copy build trees.
- Smoke is routine; full-suite is public-release-only. Manual `exhaustive-audit` requires explicit request.
- Bounded impact: focused checks. Shared/uncertain: change-based smoke against pre-task destination SHA plus semantic dependencies. Docs: syntax/links/mirrors/policy/scenarios. ROM/trace/domain checks stay separate.
- Broad local: `--workers 2`; state count/cost/stopping rule. Repeat only for edits, failures, repaired prerequisites or unresolved risk.
- Match baseline/current failure identities/assertions/skips; new/worsened failures block. Report unattributed failures/incomplete coverage.
- Never delete queue lock files or edit/build the tree during category runs.
- Inspect results/skips, then `python3 tools/testing/run_categories.py --acknowledge <run-id>`; do not archive diagnostics.
- Java 25, JUnit 5/Jupiter, `-Dmse=off`; actual-environment preflight; guards need Lua 5.4/PowerShell.
- GL: `-Dopenggf.headless.gl=surfaceless-egl` (category `--headless-gl surfaceless-egl`); preserve flags. Headless/offscreen only; window diagnostics need isolated displays, never shared-desktop/ambient fallback.
- `--lean`: prepared small classes, bounded memory, exact selectors, no profiles/custom overrides; report OOM and rerun `--normal`.
- Read [measurement hazards](docs/agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output); report command/commit/outcomes/skips/limits. Follow [testing procedures](tools/testing/README.md); bounded passes are not full-suite.

## Enforced correction contracts

| Contract | Owner; enforcement |
| --- | --- |
| Headless boot always installs no-device audio, even without synthesis; no fresh-JVM assumption. | `HeadlessGameBoot.installAudioBackend()`; `TestHeadlessAudioOwnership`, ROM `TestHeadlessGameBootAudioIdentity`. |
| Restore caller GL backend, including absent/empty properties, for reused JVMs. | Global `HeadlessStateTeardownExtension`; `TestHeadlessBackendPropertyIsolation`, `TestHeadlessStateTeardownGuard`. |
| New pixel fixtures use shared configured context; raw GLFW debt only shrinks. | `PresentationOffscreenTestContext.openConfigured(...)`; `TestRawGlfwTestSetupGuard` pins bytecode/method-reference callers/counts, rejects both historical fixtures. |
| Puppet/Pose pictures retain native priority/tile occlusion. | `StageplayObjectDraw` batch; ROM/EGL `TestStageplayObjectPriorityRendering`: native/frozen/baked GHZ-loop pixels, non-occluding negative control. |
| Stageplay logical Left/Right; native presentation owns flips/orientation. | Typed `Facing`, strict screenplay, `ObjectVisualFacing`; `TestStageplayFacingGuard`, exhaustive factory-bytecode `TestNativeObjectFacingGuard`, both-direction native pixels. |

## Level test coverage

New zones/acts follow the [route/rewind standard](docs/guide/contributing/level-test-standard.md) and link matrices from the [backlog](docs/status/level-test-coverage.md); update affected obligations/inherited gaps. Loading is not certification; enforcement remains planned.

## ROM and reference setup

Discover root `.gen` files; pass absolute ROM paths. Missing/wrong paths silently skip `@RequiresRom`; never rename/copy/delete/link ROMs for examples. Verify identity when relevant:

| ROM | Test property | CRC32 | SHA-1 |
|---|---|---|---|
| S1 World REV01 | `-Dsonic1.rom.path=` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 World REV01 | `-Dsonic2.rom.path=` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3&K locked-on | `-Ds3k.rom.path=` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

`docs/s1disasm`, `docs/s2disasm`, `docs/skdisasm`: optional development references; builds, tests, and runtime do not require them. Opt in: `git submodule update --init`.
Cite labels/lines (`loc_390FA (s2.asm:77149-77163)`); run `python3 tools/disasm/disasm_citations.py check`; pin moves require `disasm_citations.py remap <name> <old> <new> --write`.
Traces: optional pinned `tools/tracechaser/` submodule; `git submodule update --init --recursive tools/tracechaser`. Follow its guide/BizHawk 2.11 requirement; use `tools/tracechaser/...` paths.

## Runtime invariants

1. Every runtime asset byte comes through the ROM pipeline; disassemblies supply research/labels, never fallback assets.
2. Shared runtime consumes semantic rules, not game/zone names: `GameRules` for game-wide gates, providers/profiles/registries for narrower differences ([placement](docs/architecture/per-game-rule-placement.md)).
3. Trace fixes model ROM state, generalize to another BK2 and cite owning routines for constants/branches; never key on fixtures, routes, frame indices or fitted measurements.
4. **Trace data is comparison-only by default.** Never hydrate/sync gameplay from physics/aux rows. Only the isolated [dedicated hardware-timing input contract](docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md) permits input: It may release only the readiness of a matching, prepared, production-submitted ROM-backed job after kind, ordinal, stable fingerprint and service-boundary match; per-row lag admission may select an existing ROM loop. Neither supplies gameplay values, calls gameplay owners, creates work, uses physics/aux comparison data or keys on frame/zone/route/game names. Authority stays in the timing port/guard; consult the contract for implemented kinds/fixtures, never infer implementation/coverage from scope.
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

Follow user integration flow. Never switch main-workspace branches; base isolated `.worktrees/` branches `feature/ai-*`/`bugfix/ai-*` on its current branch. Preserve unrelated work/dirty submodules.
Install/obey hooks: `tools/testing/install-hooks.sh` (PowerShell `tools/testing/install-hooks.ps1`); never `--no-verify`.
Non-master/non-merge trailers: `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`; values start `updated`/`n/a` and match staged files. Source `feat`/`fix`/`perf`: changelog or inline skip reason.
Use the [staging checklist](docs/agent-workflow/documentation-obligation-checklist.md) for frontier/artifact/version/ref/alias obligations. Develop/next prose: `CHANGELOG.0.7.prerelease.md`; earlier themes: `CHANGELOG.0.7.md`; root `CHANGELOG.md`: index/development pointer and exact-path trailer. Fold unreleased fixes into existing entries. [Rollover](docs/project/release-rollover.md) owns promotion/splits.
Merges into `develop` update README release themes only if themes change; no hook requires it.
Non-deletion `master` pushes publish, PRs/manual dispatch do not; never pre-tag/reuse versions. Use `release-publishing`/[guide](docs/project/release-publishing.md) for atlas/pilot qualification; confirm release/downloads.

## Preserving how the work was done

[Preserve methods](docs/agent-workflow/README.md#preserving-how-the-work-was-done): promote recurring/>1-hour probes with purpose/inputs/origin and index entry; catalogue lessons; record rejected approaches/kill evidence/commits in dated task artifacts. Narrative: milestones only; no new document types or raw/regenerable outputs.

## Find the owning reference

- [Skill/doc map](docs/agent-workflow/README.md#find-the-owning-reference): architecture, objects/bosses, disassembly, traces, captures/input, promos, PLC, zones, rendering, UI, mods, gaps/configuration.
- ROM implementation: matching `s1-`, `s2-`, `s3k-` object/boss/disassembly/zone/PLC skills.
- Replay: `trace-replay-bug-fixing`, `trace-green-fleet`; footage: `trace-capture`, `gameplay-capture`, `gameplay-highlights`, `promo-direction`.
- Native references: `bizhawk-headless-trace`, `bizhawk-native-reference-capture`, `bk2-input-authoring`; rendering/UI: `headless-rendering`, `ui-design-language`; creation: `stageplay-authoring`, `mod-authoring`.
