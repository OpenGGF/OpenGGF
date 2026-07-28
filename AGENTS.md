# Guidance for AI agents

The same guidance is mirrored in [CLAUDE.md](CLAUDE.md); keep the two in sync (the
`Agent-Docs` trailer requires both to be staged together).

## What this is

OpenGGF is a community-made, fan-made, open-source Java game engine for research and
preservation of classic Mega Drive / Genesis platform games — the mainline Sonic the
Hedgehog series. It is not affiliated with, sponsored by, approved by, or endorsed by Sega.
It reimplements the original hardware's physics and rendering using data loaded from
user-supplied ROM images (Sonic 1, 2, and 3&K). No copyrighted assets live in this repo.
Alongside faithful emulation of behaviour it aims to provide modern tooling: an in-engine
level editor and an open framework for modding.

**Accuracy is the point.** The engine must replicate original physics pixel-for-pixel.
The disassembly is the source of truth — verify against it rather than tuning values until
a test goes green.

The project is in **alpha**. All three games are supported with game-specific modules,
level loading, objects, audio, and scroll handlers.

## Current priority

S3K playable vertical-slice parity and release readiness, not broad architecture migration
for its own sake. Prefer work that closes an actual route through Sonic 3 & Knuckles.

1. Keep AIZ → HCZ stable — the primary release slice.
2. Work CNZ, MGZ, ICZ, MHZ, and LBZ by current route blockers and complete-run trace
   frontiers.
3. Implement S3K objects by route impact: traversal blockers, terrain modifiers, hazards,
   bosses/miniboss support, sidekick/object-lifetime mismatches, then high-usage badniks.
4. Data select, special stages, and broad S1/S2 framework uplift are follow-up polish
   unless they block the active slice or a release gate.

Uplift S1/S2 or older S3K code onto the runtime-owned frameworks opportunistically when it
removes active duplication or risk — but don't let cleanup displace playable S3K progress.

## Build, test, run

```bash
mvn package                          # executable JAR with dependencies
mvn test
mvn "-Dtest=TestCollisionLogic" test # focused run
java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
```

- Entry point is `com.openggf.Engine` (declared in the manifest): a GLFW window with a
  manual timing game loop.
- **Build on JDK 21** — what CI and the release workflow use. Surefire forks inherit
  *Maven's* JVM, not the `java` on `PATH`, so `mvn -v` is the check that matters; if it
  reports anything but 21, `export JAVA_HOME=/path/to/jdk-21` first. A newer JDK makes the
  suite report hundreds of phantom failures (Mockito stubbing errors leaking across
  classes, ROM fixtures failing to load) that look like real regressions but aren't. The
  build fails fast at `validate` if the JVM is wrong.
- Maven Silent Extension is enabled by default (`-Dmse=relaxed` via `.mvn/maven.config`).
  Use `-Dmse=off` when you need full Maven logs.
- In PowerShell, quote `-D...` properties (`mvn "-Dtest=com.openggf.pkg.TestClass" test`).
- Tests are **JUnit 5 / Jupiter only** — no JUnit 4 tests, rules, runners, or `org.junit.*`
  imports.
- Git hooks auto-install during any Maven build's `validate` phase. If you commit without
  building first, run `git config core.hooksPath .githooks` once.

## ROMs

When a task needs a ROM, search the project root for `.gen` files and use the filename
that's actually there. Identify the game from the filename and verify the hash when it
matters. Do not assume a fixed filename, and do not rename, copy, delete, or symlink a ROM
just to satisfy an example command. For ROM-backed tests, pass the discovered path through
that game's property — note S3K's is **not** `sonic3k.rom.path`. A sweep that touches all
three games needs all three; ROM-backed classes error out (they do not skip) when their
ROM is missing.

| ROM | Test property | CRC32 | SHA-1 |
|---|---|---|---|
| Sonic 1 World REV01 | `-Dsonic1.rom.path=` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 World REV01 | `-Dsonic2.rom.path=` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K locked-on | `-Ds3k.rom.path=` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

Disassemblies live under `docs/s1disasm/`, `docs/s2disasm/`, `docs/skdisasm/` (untracked,
available locally); SMPS audio reference under `docs/SMPS-rips/SMPSPlay/`.

## Hard rules

These are non-negotiable and enforced by guards, CI, or review. Everything else in this
file is guidance you can weigh against the situation in front of you.

1. **ROM-only runtime assets.** Object art, mappings, DPLCs, animation scripts, PLC data,
   and any other gameplay/runtime asset bytes must come from the user-supplied ROM through
   the ROM-loading pipeline. Never read runtime asset bytes from `docs/` disassembly trees
   as a fallback — that tree is for research, labels, and offset discovery only. If a
   ROM-backed source is missing, find or verify the ROM address instead.
2. **No game-name or zone carve-outs in shared runtime code.** Per-game differences use the
   smallest accurate owner: a typed `GameRules` record for game-wide runtime gates, or an
   existing provider/profile/registry for data, art, zone-local, or object-family behaviour.
   See [docs/architecture/per-game-rule-placement.md](docs/architecture/per-game-rule-placement.md).
3. **Trace fixes model ROM state, never the trace.** If a trace diverges, model what
   actually drives the branch — object id/routine, status/control bits, frame-counter
   visibility, physics profile, event flag, data-driven condition. Do not branch on zone
   id/name, trace route, frame number, or a "known failing trace" exception. *"ROM-default
   behaviour except in AIZ"* is still a zone carve-out. Zone/event/object providers may
   expose ROM state at the owning boundary, but shared physics/sidekick/object code
   consumes semantic predicates.
4. **Trace data is comparison-only by default.** Engine gameplay state must never be
   hydrated or synced from a trace in committed test code. The sole exception is the
   dedicated hardware-timing input contract documented in
   [docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md](docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md):
   it may release only the readiness of a matching, prepared, production-submitted
   ROM-backed hardware job after kind, ordinal, stable submission fingerprint, and service
   boundary all match. It must not use physics/aux comparison data, carry gameplay values,
   call gameplay owners, or create work the engine did not submit. Guard tests must keep
   this exception confined to the timing port. `TestHardwareTimingAuthorityGuard` enforces
   parser/authority isolation and forbids physics/aux/gameplay and reflective mutation paths.
5. **Objects never call `getInstance()`.** Use the injected `services()`.
6. **Gameplay tile edits route through `ZoneLayoutMutationPipeline` / a
   `LevelMutationSurface`** — never a direct `getMap().setValue(...)`. Editor commands and
   initial layout decoders are exempt.
7. **Never bypass the commit policy with `--no-verify`.**

## Commit and branch policy

Tracked hooks live in `.githooks/`, dispatched via `.githooks/run-policy`
(`validate-policy.ps1` on Windows, `validate-policy.sh` elsewhere). CI mirrors the same
rules on PRs into `develop`.

- Every non-`master` non-merge commit carries these trailers, each starting with `updated`
  or `n/a`: `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`,
  `Agent-Docs`, `Configuration-Docs`, `Skills`. `prepare-commit-msg` appends the block —
  fill it in rather than deleting it. If a trailer's mapped files are staged, it must not
  say `n/a`; `.githooks/run-policy` holds the authoritative mapping.
- A `feat`/`fix`/`perf` commit touching `src/main/` must set `Changelog: updated` (staging
  `CHANGELOG.md`) or justify the skip inline: `Changelog: n/a: <reason>`. A bare
  `Changelog: n/a` is rejected.
- Merging a non-`master` branch into `develop` requires a staged `README.md` update
  summarising the change in the release/change log section.
- Branch naming: `feature/ai-*`, `bugfix/ai-*`. Keep a session's PRs on one branch.
- Trace frontier work keeps [docs/status/trace-frontier-log.md](docs/status/trace-frontier-log.md)
  current — when a frontier moves, a fix lands, a passing trace regresses, or a full
  `*TraceReplay` sweep picks the next target. Record command, commit/worktree context,
  pass/fail, error count, and first-error frame/field.
- Never commit an uncompressed trace payload (`physics*.csv`, `aux_state*.jsonl`) under
  `src/test/resources/traces/` — they exceed GitHub's per-file limit. Enforced by
  `TestTraceFixtureCompressionGuard`.
- **Architecture artifact placement.** Designs, specifications, implementation plans,
  research notes, audits, validation reports, and similar agent-generated engineering
  artifacts live under the matching `docs/architecture/` subdirectory described in
  [docs/README.md](docs/README.md). Classify documentation by purpose before creating it:
  point-in-time assessments belong in `docs/architecture/audits/`, and audio
  investigations with their supporting assets belong in
  `docs/architecture/research/audio/`. These repository paths override skill defaults.
  Never create loose Markdown in `docs/`, `docs/superpowers`, a top-level `docs/plans`,
  or generic `archive`, `misc`, `notes`, or tool-named dumping grounds. Before finishing,
  stage every relevant artifact created for the task; do not leave documentation or its
  supporting assets untracked.

## Gotchas

The things that cost the most time when missed.

**Coordinates.** ROM `x_pos` / `y_pos` map to `getCentreX()` / `getCentreY()`. `getX()` /
`getY()` are top-left render bounds — mixing them produces a ~19px vertical offset and
wrong collision. When porting disassembly that touches `x_pos` / `y_pos`, default to the
centre APIs unless the code is explicitly about sprite bounds, render extents, or collision
box edges; route playable-sprite native writes through `NativePositionOps`. If camera,
collision, object anchoring, or scripted movement drifts relative to the player, suspect
this first. The debug HUD `Pos:` line prints top-left, **not** ROM centre — don't quote it
against a disassembly trace without converting. Y increases downward (Mega Drive
convention). VDP coordinates in the disassembly are offset by +128; the engine uses direct
screen coordinates.

**Terminology** differs from standard Sonic 2 naming: **Pattern** = 8x8 tile, **Chunk** =
16x16 (composed of Patterns), **Block** = 128x128 (composed of Chunks).

**Sprite tiles are column-major:** `tileIndex = column * heightTiles + row`. H-flip draws
from the last column first, V-flip from the bottom row first.

**Pattern IDs exceed the VDP's 11 bits.** The engine adds a virtual pattern ID space above
`0x7FF` with a non-overlapping base per category; use
`GraphicsManager.renderPatternWithId()` when IDs exceed the VDP range, and pick a fresh
base for a new category. Range table in
[docs/status/known-discrepancies.md](docs/status/known-discrepancies.md).

**ENEMY touch responses poll every frame** while the overlap persists (matching the ROM
`Touch_Loop`) — SPECIAL/monitor contacts stay edge-triggered. Don't add consumed-once
"already hit" latches to the enemy touch path.

**S1 silently ignores solid-bit setters.** `setTopSolidBit()` / `setLrbSolidBit()` no-op
under `CollisionModel.UNIFIED`, so springs and plane switchers are automatic no-ops for S1.

**Rewind coverage is guarded.** A new spawnable object without a recreate path, an
uncaptured `final` scalar, or an object reference not captured as a rewind id fails
`TestRewindCoverageGuard`. A global static manager consumed across frames but unregistered
fails `TestStaticStateRewindCoverageGuard` — fix it with a `RewindSnapshottable` adapter,
not a baseline entry, unless the gap is genuinely intentional.

**Headless tests:** call `GroundSensor.setLevelManager(...)` and
`Camera.updatePosition(true)` *after* the level load, and prefer
`@ExtendWith(SingletonResetExtension.class)` over manual teardown. Set
`startup.legalDisclaimer=false` in tests that boot the full `Engine`.

**Audio accuracy:** reference the libvgm chip cores and the SMPSPlay source rather than
simplified versions. Diagnose against a source of truth instead of twiddling knobs.

### Sonic 3&K bring-up notes

Full detail in [AGENTS_S3K.md](AGENTS_S3K.md) and the `s3k-*` skills. The expensive ones:

- **Prefer S&K-half addresses.** The locked-on ROM has an S&K half (`< 0x200000`) and an S3
  half (`>= 0x200000`) with identical shared bytes; the S3KL/SKL runtime references the S&K
  half for the overwhelming majority of assets. Put `sonic3k.asm` offsets in
  `Sonic3kConstants.java` by default and run `RomOffsetFinder --game s3k`; when both halves
  hit, pick `sonic3k.asm`. Some objects genuinely reference S3-half (`s3.asm`) assets —
  verify the object's code points there, then use it. Don't loop hunting for an S&K
  equivalent that doesn't exist.
- **Dual object pointer tables.** S3K remaps many object IDs by zone set: `S3kZoneSet.S3KL`
  (zones 0-6, AIZ-LBZ, 256 entries) and `SKL` (zones 7-13, MHZ-DDZ, 185 entries). Resolve
  names via `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)`.
- **Compression type is encoded in the label suffix** (e.g. `AIZ1_8x8_Primary_KosM`), since
  S3K files use a `.bin` extension. `RomOffsetFinder` auto-infers it.
- **Known limitation:** some S3K acts log `maxChunkPatternIndex > patternCount` (dynamic
  art / PLC parity is incomplete).
- **Keep green:** `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`.

## Where to look next

Skills carry the step-by-step procedures — reach for them rather than reconstructing a
workflow. Sources live in `.agents/skills/` (mirrored in `.claude/skills/`).

| Task | Skill |
|---|---|
| Any `*TraceReplay` failure | `trace-replay-bug-fixing` (plus `trace-green-fleet`, `trace-capture`) |
| Implementing an object / badnik | `s1-implement-object`, `s2-implement-object`, `s3k-implement-object` |
| Implementing a boss | `s1-implement-boss`, `s2-implement-boss`, `s3k-implement-boss` |
| Navigating a disassembly | `s1disasm-guide`, `s2disasm-guide`, `s3k-disasm-guide` |
| Bringing up an S3K zone | `s3k-zone-bring-up` (+ `-analysis`, `-events`, `-parallax`, `-animated-tiles`, `-palette-cycling`, `-validate`) |
| Pattern Load Cues | `plc-system`, `s3k-plc-system` |

Deeper reference, loaded when the work needs it:

| Document | Covers |
|---|---|
| [docs/architecture/engine-map.md](docs/architecture/engine-map.md) | Service tiers, session ownership, runtime framework stack, `LevelManager` decomposition, multi-game and physics providers, level events, sidekicks, rewind, audio, config, tooling |
| [docs/architecture/object-implementation-reference.md](docs/architecture/object-implementation-reference.md) | Object registration, behaviour contracts, base classes, shared utilities, per-game art loading, constants files |
| [docs/architecture/per-game-rule-placement.md](docs/architecture/per-game-rule-placement.md) | Where a per-game behavioural difference belongs |
| [docs/guide/contributing/headless-testing.md](docs/guide/contributing/headless-testing.md) | `HeadlessTestRunner`, singleton reset, test infrastructure |
| [docs/agent-workflow/README.md](docs/agent-workflow/README.md) | Workflow CLIs, per-task runbooks, CI guard-failure explainer, pitfall index, documentation-obligation checklist |
| [docs/status/known-discrepancies.md](docs/status/known-discrepancies.md) | Intentional divergences from the ROM, virtual pattern ID ranges, trace bootstrap contracts |
| [AGENTS_S3K.md](AGENTS_S3K.md) | Sonic 3&K specifics |
| [CONFIGURATION.md](CONFIGURATION.md) | `config.yaml` keys, bindings, debug flags |

## Code style

Keep logic in manager classes, not `Engine.java`. Java 21. Source files end with a newline.
Write code that reads like the code around it — match its comment density, naming, and
idiom.
