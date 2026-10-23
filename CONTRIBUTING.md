# Contributing to OpenGGF

OpenGGF is a Java game engine for research and preservation of classic Mega Drive /
Genesis platform games. The project values ROM accuracy first: physics, rendering,
object timing, audio, and trace replay behavior should match the original games as
closely as the engine can prove.

This file is the short entry point. Deeper contributor docs live in
[`docs/guide/contributing/`](docs/guide/contributing/).

## What To Work On

Good contributions include:

- ROM-accurate object, badnik, boss, level event, palette, PLC, audio, and rendering fixes.
- Tests that make existing behavior easier to verify.
- Trace replay, rewind, and debugging improvements that preserve the engine's native behavior.
- Documentation that helps future contributors understand a real workflow or parity pitfall.

Current priority is completing and validating the stock campaigns, especially Sonic 3 &
Knuckles character routes and finales. Preserve AIZ → HCZ while choosing later-zone work
by current route blockers and trace frontiers. Start with the [0.7 roadmap](docs/project/v0.7-roadmap.md),
[game status](docs/guide/playing/game-status.md), [coverage backlog](docs/status/level-test-coverage.md),
and [trace frontier log](docs/status/trace-frontier-log.md). The per-game object checklists
record implementation coverage, not proof that a complete route matches the ROM.

## Legal And Asset Rules

Do not commit ROM files, ripped sprites, ripped music, screenshots containing copyrighted game art,
or other copyrighted game data. OpenGGF loads runtime data from user-supplied ROMs; contributors
need their own legally obtained ROMs for local testing.

Runtime assets must come from the ROM through the engine's ROM-loading pipeline. Do not add
fallbacks that read gameplay asset bytes from `docs/` disassembly/reference trees. Those files are
for research, labels, and offset discovery only.

The repository is licensed under GPLv3. By submitting a contribution, you agree that your changes
are offered under the same license unless the maintainers explicitly agree otherwise.

## Development Setup

Start with:

- [Developer Setup](docs/guide/contributing/dev-setup.md)
- [Architecture Deep Dive](docs/guide/contributing/architecture.md)
- [Testing](docs/guide/contributing/testing.md)

Minimum requirements:

- JDK 25 (Maven builds require this major version).
- Maven 3.8 or later.
- OpenGL 4.1+ for running the engine window.
- Optional local ROM files for ROM-dependent tests and gameplay.

Build without running tests, then inspect and run the change-based smoke selection:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
python3 tools/testing/run_categories.py --base <pre-task-commit>
python3 tools/testing/run_categories.py --base <pre-task-commit> --preflight
python3 tools/testing/run_categories.py --base <pre-task-commit> --run
```

Replace `<pre-task-commit>` with the pinned destination commit from before your change.
For a bounded local change, focused checks can replace broad smoke coverage when the
[testing guidance](docs/guide/contributing/testing.md) supports that choice:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestCollisionLogic" test
```

Use the queue for local test-running Maven commands; category runs queue themselves.
Routine validation uses smoke, including CI. The explicit `-Pfull-suite` selection is
reserved for preparing a new public release. Documentation-only changes use relevant
syntax, link, mirror, policy and example checks. Full Maven logs are enabled by `-Dmse=off`.

Run the built jar (on macOS, add `-XstartOnFirstThread` before `-jar`):

```bash
java -jar target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar
```

## Accuracy Rules

When porting behavior from a disassembly, cite and model the ROM behavior rather than matching a
single observed symptom.

- Per-game physics differences must be gated by semantic feature flags, usually on
  `PhysicsFeatureSet`. Do not add game-name branches in shared physics code.
- Trace fixes must not branch on zone id/name, route, frame number, or a known failing trace. Model
  the ROM state that drives the branch: object id/routine, status/control bits, frame counter,
  physics profile, event flag, subtype, or data-driven condition.
- Physics and auxiliary trace rows are comparison-only: never hydrate or sync gameplay
  from them. The isolated [hardware-timing input contract](docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md)
  can release matching, already-submitted ROM-backed work; it cannot supply gameplay values.
- ROM `x_pos` / `y_pos` map to `getCentreX()` / `getCentreY()` in the engine. `getX()` / `getY()`
  are top-left sprite bounds.
- Object art, mappings, DPLCs, animation scripts, PLC data, and runtime bytes must be ROM-backed.

For trace work, read [Trace Replay Testing](docs/guide/contributing/trace-replay.md) before making
changes.

## Architecture Rules

Follow the existing provider and service architecture:

- Non-object code should use `GameServices`, explicit dependencies, or providers.
- Object instances should use injected `ObjectServices`; do not call manager singletons from object
  code.
- Do not call `services()` from object constructors unless the class is created through an explicit
  construction-context path.
- Spawn child runtime objects through `spawnChild(...)`, `spawnFreeChild(...)`, or an existing
  lifecycle helper. Direct `ObjectManager.addDynamicObject(...)` is for documented bridge code.
- New object behavior should prefer shared contracts such as `ObjectControlState`,
  `ObjectPlayerQuery`, `ObjectPlayerParticipationPolicy`, `ObjectLifetimeOps`, and canonical
  profiles under `com.openggf.game.profiles.*`.
- Gameplay-path tile edits should route through `ZoneLayoutMutationPipeline` or a
  `LevelMutationSurface`, not direct map writes.
- IDs above the VDP's 11-bit pattern range must render through the virtual pattern ID path.

See:

- [Architecture Deep Dive](docs/guide/contributing/architecture.md)
- [Tutorial: Implement an Object](docs/guide/contributing/tutorial-implement-object.md)
- [Adding Bosses](docs/guide/contributing/adding-bosses.md)
- [Adding Zones](docs/guide/contributing/adding-zones.md)
- [Audio System](docs/guide/contributing/audio-system.md)
- [Rewind System](docs/guide/contributing/rewind-system.md)

## Testing Expectations

All new or updated tests must use JUnit 5 / Jupiter. Do not add JUnit 4 tests, rules, runners, or
`org.junit.*` imports.

Use the narrowest useful verification:

- Unit tests for local logic.
- `HeadlessTestFixture` / headless integration tests for physics, collision, object, and level
  behavior.
- ROM-gated tests with `@RequiresRom` when real ROM data is necessary.
- Trace replay tests for parity-sensitive movement, object timing, spawn timing, collision, and
  route behavior.

When a trace frontier moves, regresses, or a trace sweep is used to choose the next target, update
[`docs/status/trace-frontier-log.md`](docs/status/trace-frontier-log.md) in the same change.

## Branches, Commits, And Documentation

Release maintainers: pushing `master` automatically publishes a GitHub release
after validation and builds succeed. Manual dispatch is validation-only; see [Publishing a GitHub release](docs/project/release-publishing.md)
for the command, tag ownership, and final checks.

For normal contributions, use focused branches from `develop`. In an existing delegated
workspace, use its current branch as the base unless another is specified; create the
task branch in a separate worktree and leave the main checkout on its existing branch:

- `feature/ai-...` for new features.
- `bugfix/ai-...` for fixes.

Tracked git hooks and CI enforce commit-message trailers on non-`master` branch commits. Maven
never installs or touches the hooks; install them explicitly once per worktree:

```bash
tools/testing/install-hooks.sh      # PowerShell: tools/testing/install-hooks.ps1
```

The script points `core.hooksPath` at `.githooks` for that worktree only.

Do not bypass hooks with `--no-verify`.

The trailer block records whether related documentation was updated or intentionally skipped:

```text
Changelog: updated|n/a
Guide: updated|n/a
Known-Discrepancies: updated|n/a
S3K-Known-Discrepancies: updated|n/a
Agent-Docs: updated|n/a
Configuration-Docs: updated|n/a
Skills: updated|n/a
```

A `feat`, `fix`, or `perf` commit that touches `src/main/` must either update
[`CHANGELOG.md`](CHANGELOG.md) or justify the skip, for example:

```text
Changelog: n/a: release note recorded in CHANGELOG.0.7.prerelease.md
```

New development release notes belong in `CHANGELOG.0.7.prerelease.md`; the root
`CHANGELOG.md` is the release index and alone owns the `Changelog` trailer mapping.
End AI-assisted commit messages with a `Co-authored-by` line for each contributing
model, after the documentation trailers.

See [Documentation And Branch Policy](docs/guide/contributing/documentation-policy.md) for the
human-facing policy details.

## Pull Request Checklist

Before opening a PR:

- Rebase or merge from current `develop` if your branch is stale.
- Confirm no ROM files or copyrighted game assets are staged.
- Run the focused tests for the changed area and include the commands in the PR description.
- Inspect and run the change-based smoke selection for shared physics, lifecycle, rendering,
  audio, rewind, trace or runtime changes, plus affected domain checks. Keep public-release
  full-suite validation separate. Inspect skipped tests and attribute disputed failures by
  matched baseline/current test identity and assertion.
- Update the current development changelog, guide, configuration, discrepancy and trace-frontier
  documentation where required; change the root changelog only when its index/pointer changes.
- Explain ROM citations or parity reasoning for gameplay behavior changes.
- Call out known limitations or intentional discrepancies instead of hiding them in implementation
  details.

Keep PRs focused. Avoid unrelated refactors unless they are necessary to make the requested change
safe and understandable.
