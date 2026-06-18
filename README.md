# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega
Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It aims to
faithfully reimplement the physics and rendering behaviour of the original hardware using data
loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** This project is not affiliated with or endorsed by Sega. Sonic the Hedgehog and
> all related characters, names, and trademarks are the property of Sega Corporation. No ROM images
> or other copyrighted game data are included in this repository. Users must supply their own
> legally obtained ROM files to use this software.
>
> The disclaimer is also shown in-engine on startup; it can be disabled by setting
> `startup.legalDisclaimer: false` in `config.yaml`.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.yaml` in the working directory. A legacy
`config.json` is migrated automatically on first run. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `SPACE`, `Q`, or `F9`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

## Controls

> Currently, only keyboard controls are supported.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Jump |
| Enter | Pause / unpause |

The bundled `config.yaml` exposes these under `input.pause` and `input.player1`.
Additional bindable controller inputs, including Player 1 Start, are documented in
[`CONFIGURATION.md`](CONFIGURATION.md); keys omitted from the template still use the
engine defaults until added explicitly.

### Debug Controls

| Key | Action |
|-----|--------|
| F1 | Show/Hide Debug Overlay (text and bounding boxes) |
| F2 | Show/Hide Shortcuts Overlay |
| F3 | Show/Hide Player Panel |
| F4 | Show/Hide Sensor Labels |
| F5 | Show/Hide Object Labels |
| F6 | Show/Hide Camera Bounds |
| F7 | Show/Hide Player Bounds |
| F8 | Show/Hide Object Points |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |
| Page Up | Cycle Acts (`debug.keys.nextAct`) |
| Page Down | Cycle Zones (`debug.keys.nextZone`) |

`F9` is also the default level-select shortcut (`debug.keys.levelSelect`), so it
can both open level select and toggle ring bounds while debug overlays are enabled.

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`debug.flags.editor` must be `true`) |
| F5 | Restart the playtest from editor mode |

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it features software emulation of the YM2612 FM synthesiser and SN76489 PSG
chips (based on [libvgm] and [Genesis Plus GX] reference cores) driven by a reimplemented SMPS
sound driver.

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Broadly playable. All 7 zones, 6 bosses, special stages, title screen, ending/credits. When S3K is the donor, S1 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic the Hedgehog 2 (S2) | Most complete. All zones, 9 bosses (including both DEZ bosses), special stages, Tails AI, credits/ending. When S3K is the donor, S2 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic 3 & Knuckles (S3K) | Near-complete vertical-slice coverage and now the main parity/release focus. AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and parts of LBZ have substantial route object, event, boss/miniboss, scroll, palette/PLC, and trace coverage; FBZ and later zones remain the largest content frontier. S3K also includes title screen, level select, data select with save/load support, Knuckles glide/climb, Blue Ball special stages (WIP), bonus-stage parity work, palette cycling, and broad object/badnik coverage. Data select can also be donated to S1/S2 via cross-game donation. |

Recent engine work has also moved shared zone behavior onto runtime-owned frameworks: `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and `AdvancedRenderModeController`. The current roadmap priority is to use those systems to close playable S3K vertical slices rather than to run broad architecture migrations for their own sake. S1/S2 uplift remains valuable when it removes duplication or active risk in code already being touched, but S3K route completeness now leads work selection.

Current migration status is intentionally partial rather than universal. Sonic 2 already uses the runtime-owned stack for HTZ/CNZ runtime state, palette ownership, animated tile orchestration, CNZ staged overlay rendering, and CNZ layout mutations via `ZoneLayoutMutationPipeline`. Sonic 3&K uses the same stack for AIZ/HCZ/CNZ runtime-state adapters, AIZ staged render effects and advanced render modes, HCZ/SOZ animated tile channels, CNZ runtime-state-backed scroll behavior, and seamless terrain-swap/mutation paths routed through the mutation pipeline. The shared scroll-composition helpers are live in AIZ, HCZ, and MGZ. Other S1/S2/S3K zones still mix these frameworks with older zone-local paths and should be treated as follow-up migration work rather than implied complete adoption.

Near-term S3K work should be planned as playable route slices with explicit gates: required traversal objects and badniks, event/camera behavior, scroll/parallax, animated tiles, palette and PLC state, bosses or transitions, rewind coverage where state is gameplay-relevant, trace replay for known blockers, and visual validation against stable-retro where practical. AIZ through HCZ remains the primary release slice, but CNZ, MGZ, ICZ, MHZ, and LBZ now have enough coverage that work should be prioritized by route blockers and complete-run trace frontiers rather than by first-pass zone bring-up.

Work is ongoing across all three games. Recent branch work spans S3K route
stabilization (AIZ, HCZ, CNZ, MGZ, ICZ, Mushroom Hill, and Launch Base), an S3K complete-run per-zone
trace suite (one Sonic+Tails AIZ->Doomsday movie segmented per zone, each trace
spanning the act1->act2 transition through the zone-exit handoff) with
ROM-accurate in-game pause modelling, explicit trace-entry capability metadata,
and a frontier-only replay mode that bounds failing trace sweeps to the first
divergence plus diagnostic context, animated ROM-derived master-title game previews that replace the
bundled title emblem resource, S2 trace-frontier closures (Sky Chase and Casino
Night
level-select replays), object-physics standardization onto shared contracts,
expanded rewind coverage, and architecture-guard hardening across runtime
ownership, trace/rewind invariants, and object-service boundaries. A recent
test-suite quality pass (driven by a multi-agent audit) replaced assertion-free
diagnostic, tautological, and source-text-grep tests with real behavioral
oracles, and added a guard that fails the build on assertion-free `@Test`
methods, plus order-dependence hardening (an S3K AIZ replay-probe crash fix, a
fork-mate state-leak fix flagged by the singleton-lifecycle guard, and the MZ1
lost-ring regression test rerouted through the production replay bootstrap so it
is deterministic rather than fork-order dependent). See CHANGELOG.md for the
detailed, per-merge history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Revision |
|------|-------------------|----------|
| Sonic 1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 3&K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | World (lock-on combined ROM) |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. ROM filenames are configurable via
`config.yaml` (see `roms.sonic1`, `roms.sonic2`, and `roms.sonic3k`).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`crossGame.enabled` is `true` and `crossGame.source` is `"s3k"`. Enable it in
`config.yaml`:

```yaml
crossGame:
  enabled: true
  source: "s3k"
```

Both the base game ROM and the donor game ROM must be present.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine reimplementation out there is
written in C, C++, or C#. A Java implementation proves it can be done on a managed runtime, and
the JVM's cross-platform nature means it runs on Windows, macOS, and Linux without platform-specific
builds (though a GraalVM native image is also available for those who prefer it).

### Will Sega shut this down?

This project contains no copyrighted material. No ROM data, sprites, music, or other Sega assets
are included in the repository. The engine is an independent reimplementation, developed and
verified against the community-maintained disassemblies, that requires users to supply their own
legally obtained ROM files. We have no affiliation with Sega and make no claim to any of their
intellectual property.

### What platforms does it run on?

Anywhere Java 21 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries.

### Did you use AI to write this? / This is AI slop!

Various agents (Claude, Codex, and Gemini, in various models, versions and forms) have all been used at various points in the project's history, and
the commit history doesn't hide it; you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The pre-AI core — the engine framework and architecture, the rendering pipeline, the physics
engine and its subpixel movement model, and the sensor-based collision system — was designed and
coded by hand over years, long before any agent touched the repo. Other subsystems were built
with heavy AI assistance under direct human oversight; the SMPS audio engine, in particular, was
AI-built and steered against reference implementations rather than hand-written. AI was brought in for bulk analysis and research, to accelerate
object and boss implementation, debugging, validation, and unit tests; all with accuracy verified
against the original ROM disassemblies. Every commit is reviewed, tested, and corrected where
needed.

[You can't prompt your way to ROM accuracy (yet!)](docs/AI_JOURNEY.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/DEVELOPMENT_TIMELINE.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.

## Releases

### v0.6.prerelease (Current development snapshot)

Development since `v0.5.20260411` is the active 0.6 prerelease line. The detailed running notes now
live in `CHANGELOG.md`; this README keeps only the high-level shape of the release.

- **AIZ2 rewind softlock fixed + full rewind-coverage campaign (2026-06-17).** Merged
  `bugfix/ai-aiz2-rewind-loop-boss`. Fixed the AIZ2 boss / ship-loop held-rewind softlock and visual
  corruption, then shipped a Phase-1 rewind-coverage audit (`RewindCoverageAnalyzer` + report-only
  `TestRewindCoverageGuard` vs a committed baseline) and closed the entire at-risk backlog: ~159
  runtime-spawned objects across S1/S2/S3K (boss children, projectiles, traversal/capture objects,
  cutscene objects, results/slot machines) now have rewind recreate codecs instead of being silently
  dropped on a backward seek, with the genuinely-cosmetic remainder documented as known discrepancies.
  Coverage baseline ratcheted from 1705 to 1452 gap keys; the guard fails CI on any new gap.
- **AIZ miniboss body/flames no longer strand in AIZ2 -- actual carry-path fix (2026-06-17).** Merged
  `bugfix/ai-aiz2-boss-child-carry-strand`. The two earlier AIZ miniboss carry fixes targeted the
  *placed* cutscene object (0x90), which is never in the dynamic-object carry snapshot, so they
  did not stop the strand. The objects actually carried across the AIZ1->AIZ2 fire reload were the
  miniboss boss component children (body/arm/flame barrels); the carry snapshot now excludes
  `BossChildComponent`, matching ROM `Load_Level` clearing `Dynamic_object_RAM`. Off-screen
  boss-part persistence during a fight is unaffected.
- **Test-suite cleanup after trace-frontier fixes (2026-06-17).** Merged
  `bugfix/ai-test-suite-cleanup`, aligning stale parity and guard tests with the
  current trace-frontier behavior while fixing the underlying rewind snapshot,
  dynamic object lifecycle, S1 fixed-air countdown access, and headless tilemap
  cache issues exposed by the full suite.
- **AIZ miniboss self-destructs if carried across an act reload (2026-06-17).** Merged
  `bugfix/ai-aiz2-miniboss-defeat-carry-guard`, hardening the AIZ2 fightable miniboss
  (object 0x91) against the same ghost failure mode as the AIZ1 cutscene fix: the persistent
  boss, which holds the arena camera lock even after defeat, now removes itself and its
  tracked children if it is ever carried across a seamless act reload instead of becoming an
  invisible camera-locking ghost in the next act.
- **AIZ1 cutscene miniboss no longer strands flame children in AIZ2 (2026-06-17).** Merged
  `bugfix/ai-aiz2-miniboss-carry`, fixing an AIZ playthrough bug where the AIZ Act 1
  cutscene miniboss (object 0x90) and its persistent flame-barrel children were carried
  across the seamless AIZ1->AIZ2 fire reload without the world offset applied, stranding an
  invisible-bodied "copy" of the miniboss whose flames kept hurting the player partway
  through AIZ2. The cutscene object now removes itself and its tracked children when carried
  across the act transition, matching the ROM.
- **Trace frontier reporting/noise reduction + AIZ2 visual capture fixes (2026-06-17).** Merged
  `bugfix/ai-trace-frontier-develop`, making trace replay reports focus on the
  true release-blocking frontier, compacting noisy diagnostic context, advancing
  several S2/S3K route frontiers, and carrying the AIZ worker's trace-faithful
  `TraceCaptureTool` frame driver. The same branch adds the AIZ2 battleship
  section clip mode and fixes the display-only forest-canopy Plane-A wrap so
  the post-bombing loop reveals and wraps like the ROM while leaving the AIZ
  gameplay frontier at f19089.
- **Timeline-clip tooling for contributors (2026-06-16).** Merged
  `feature/timeline-clip-tooling`, adding `docs/assets/timeline/make_clip.py`
  (a one-shot ffmpeg encoder that produces house-style GIFs/MP3s — 320px,
  8 fps, midpoint-centred, 64-colour palette) and `docs/assets/timeline/README.md`
  documenting the settings and how to add a Development Timeline clip, so
  contributed clips stay visually consistent. Docs/tooling only.
- **Dev-clip date correction + README timeline link (2026-06-16).** Merged
  `feature/ai-journey-clip-date-fix`: the oldest dev clip (white box under
  terrain) is a v0.05 build from 2015-04-09 captured in 2024, not an Oct 2024
  build — corrected in `docs/AI_JOURNEY.md` and `docs/DEVELOPMENT_TIMELINE.md`.
  Also links the Development Timeline from the README's AI section. Docs-only.
- **Development-timeline 2024 prologue (2026-06-16).** Merged
  `feature/ai-journey-timeline-prologue`, adding the two oldest dev clips (the
  white-box-under-terrain physics rewrite and the first Emerald Hill tiles
  decompressed from the ROM, Oct 2024) as a chronological prologue to
  `docs/DEVELOPMENT_TIMELINE.md`, so it matches the hall of shame in
  `docs/AI_JOURNEY.md`. Reuses existing `assets/ai-journey/` media; docs-only.
- **AI journey journal expansion + development-timeline gallery (2026-06-16).** Merged
  `feature/ai-journey-expansion`, substantially expanding `docs/AI_JOURNEY.md` against
  the commit log and the project's two-person chat history: corrects the AI-use timeline
  (first real AI work was ChatGPT + Kosinski in Sept 2024, agentic Codex in June 2025,
  Jules-scaffolded/human-tuned audio in Nov 2025), reframes audio as the oracle-less
  exception, and adds a curated hall of shame. Adds `docs/DEVELOPMENT_TIMELINE.md`, a
  captioned GIF/audio gallery of ~40 dev builds (Dec 2025 → Apr 2026), with media under
  `docs/assets/`. Docs-only; no engine change.
- **S3K AIZ trace frontier to f19089 + AI journey journal (2026-06-16).** Merged
  `bugfix/ai-trace-frontier-develop`, advancing the S3K AIZ trace past the AIZ2
  battleship bombing run and wrap into the end-boss arena approach (frame ~19089)
  via ROM-cited fixes: same-frame-spawned hazard touch latency (1-frame), the
  `Status_Push` frame-end clear keyed on the real ROM anim byte (S2/S3K), and
  CPU-sidekick off-screen respawn-facing / auto-jump / wall-push state (S3K),
  plus S2 MTZ/HTZ/ARZ object-slot frontier advances and a `GroundWallResponseState`
  extraction. Also adds `docs/AI_JOURNEY.md` — a commit-log-verified history of
  the project's AI use — and corrects the README's AI-authorship section (the
  SMPS audio engine was AI-built, not hand-authored).
- **Runtime display shader library branch (2026-06-15).** Merged
  `feature/ai-display-shader-library-spec-no-trace`, adding a user-supplied
  root `shaders/` library, runtime shader cycling, a searchable/folder-based
  shader picker, RetroArch/BizHawk GLSL preset loading, in-app libretro GLSL
  shader-pack download/update support, shader activation toasts, and a
  post-processing pipeline that can apply scene, presentation, or final display
  shader phases without committing third-party shader assets.
- **S3K AIZ collapsing-platform on-object trace frontier (2026-06-15).** Merged
  `bugfix/ai-aiz-frontier-f3317`, advancing the S3K AIZ1 trace from frame 3317
  to 4234 by deferring the airborne-rider unseat by one frame on the collapsing
  platform's collapse-transition frame (ROM `ObjPlatformCollapse_CreateFragments`
  skips `sub_205B6` that frame), via a new
  `SolidObjectProvider.defersAirborneRiderUnseatThisFrame` hook. No S3K trace
  regresses; S1/S2 unaffected.
- **S3K AIZ sidekick wall/on-object trace frontier (2026-06-15).** Merged
  `bugfix/ai-aiz-trace-green`, advancing the S3K AIZ1 trace from frame 2590 to
  3317 (and HCZ complete-run 407→1402) by two ROM-cited CPU-sidekick fixes:
  keeping `Status_OnObj` on a same-frame land-and-jump-off-object frame, and
  restoring the per-frame terrain-wall follow nudge so the sidekick re-pushes
  flat walls like the ROM — which let a `distance==0→−1` wall-distance band-aid
  and its `PhysicsFeatureSet` flag be deleted. Includes a BizHawk ROM-execution
  diagnostic (`tools/bizhawk/diag_tails_wallprobe.lua`) used to capture the
  ground truth. No S3K trace regresses; S1/S2 unaffected.
- **AIZ2 battleship wrap contract documentation (2026-06-15).** Merged
  `bugfix/ai-aiz2-battleship-wrap-docs`, correcting stale documentation and test
  wording so the AIZ2 post-bombing ship loop is described by its ROM
  `Level_repeat_offset=$200` gameplay wrap instead of the obsolete `$80` visual
  approximation, with the remaining seam work documented as display-only. No
  engine behavior change.
- **Sonic 2 Death Egg ending trace closure (2026-06-14).** Merged
  `bugfix/ai-death-egg-ending-cutscene`, restoring the DEZ escape ending path
  by matching ObjC6 barrier solidity, ObjAF boss-id handoff, and ObjC7 Death
  Egg Robot animation/sensor/defeat timing to the ROM. The
  `S2DezEndingLevelSelect` replay now reaches the ending pictures and credits
  path with no divergences.
- **Launch configuration screen branch (2026-06-12).** Merged
  `feature/ai-launch-config-screen`, adding per-title launch profiles from the
  master title screen, transient session-only configuration overlays, donor-gated
  main/sidekick character choices, widescreen launch presets, red experimental
  marking for ultrawide modes, and trace-safe overlay clearing across user,
  programmatic, failed, and teardown launches.
- **Rewind palette capture and determinism audit branch (2026-06-12).** Merged
  `bugfix/ai-rewind-palette-and-audit`, adding live palette-color rewind
  snapshots, schema-driven S3K zone-event sidecars for AIZ/HCZ/CNZ/MGZ/MHZ/ICZ,
  malformed-payload guards, S3K shield post-rewind art refresh recovery, and
  the opt-in `debug.rewind.determinismAudit` segment re-simulation diagnostic.
- **Engine performance optimization branch (2026-06-12).** Merged
  `bugfix/ai-performance-optimization`, a 13-task measured pass over the
  audio, rendering, and rewind hot paths: keyframe-spike max 71.7→27.9 ms,
  dirty-rect pattern-atlas uploads (≈512× less data per DPLC change),
  incremental background-window scrolling, SAT replay batching, in-place
  object restore and deferred audio-driver restores during held rewind
  (≈22× faster / ≈257× less allocation per backward step on the audio
  share), evidence-gated SMPS/resampler math (fade fallback removal proven
  PCM-identical), a disasm-cited Tails CalcAngle parity fix, and committed
  ROM-gated measurement harnesses. Trace-replay results are byte-identical
  to the pre-branch baseline throughout; full numbers in
  `docs/performance/2026-06-11-performance-results-tally.md`.
- **Develop sync and release-hardening integration (2026-06-11).** Merged the
  latest `origin/develop` release-prep work, carrying configuration/save
  resilience updates, data-select presentation fixes, sidekick and trace
  diagnostic refinements, touch-response and mutable-level hardening, and the
  release task/review documentation used to track the 0.6 prerelease line.
- **Release-readiness review remediation follow-up (2026-06-10).** Merged
  `bugfix/ai-release-review-fixes-20260609`, closing the verified
  release-review blocker/high/medium findings that were later consolidated into
  `docs/superpowers/plans/2026-06-11-release-remediation.md`.
  The branch restores the S3K AIZ trace gate, fixes rewind map copy-on-write
  isolation, hardens develop trace CI, quarantines malformed config/editor
  saves, tightens architecture guards, and clarifies release config/docs.
- **Release review remediation completion (2026-06-09).** Merged the final
  `bugfix/ai-release-review-fixes` changes after rebasing develop, carrying the
  lifecycle-test alignment that verifies destroyed gameplay contexts detach
  cleared runtime managers while stale references remain inert.
- **Release review remediation branch (2026-06-09).** Merged
  `bugfix/ai-release-review-fixes`, closing the temporary release-review issue
  tracker. The branch tightens S3K trace replay invariants, rendering/cache
  ordering, release package validation, ROM-like asset policy, editor teardown,
  AIZ boss child lifecycle, and S3K ROM-backed object mappings, with focused
  verification over the affected trace, render, tooling, editor, object, and
  art guard suites.
- **Release-readiness review fixes branch (2026-06-09).** Merged the deep
  architecture/code review remediation branch for the 0.6 prerelease line:
  trace release gates no longer allow missing S3K AIZ replay coverage,
  save/config/editor recovery paths are more resilient, native-library and
  shader loading are release-hardened, and architecture ratchets remain part of
  the release guard set. The follow-up release review kept S3K AIZ replay as a
  hard publish gate and closed the known AIZ mismatch through ROM-visible object
  and sidekick state rather than trace-state seeding.
- **Release-prep guardrails and S1 ROM-data migration branch (2026-06-08).**
  Merged the release-prep policy/CI hardening that validates direct release
  pushes, counts generated trace replay reports, blocks warning-only trace
  debt by default, and keeps S3K AIZ full-run entry behavior driven by explicit
  fixture capability metadata instead of gameplay-state seeding. The same branch
  moves another large Sonic 1 runtime-data
  slice to ROM-backed sources: palette cycles, GHZ bridge bend tables,
  LZ/SBZ conveyor and child-platform placement data, support-object mappings,
  badnik mappings, SBZ machinery mappings, and the Final Zone boss mapping
  slice.
- **Release-readiness review and AIZ intro fix branch (2026-06-07).** Merged
  the release hardening pass for branch-policy hooks, release CI, trace/rewind
  invariants, ROM-backed S3K object art coverage, static-state reset coverage,
  and build/tooling guards. The branch also fixes the AIZ Sonic+Tails intro
  bootstrap so Player 2 Tails is re-parked in the ROM dormant marker after the
  production `spawnSidekick` step, preventing him from appearing flying at the
  start of the intro pan.
- **S1 trace-fleet integration branch (2026-06-06).** Merged
  `bugfix/ai-s1-trace-fleet-integration`, preserving the release-readiness
  roadmap and the accepted trace/debug harness work from the main workspace.
  The branch carries the S1 Chopper subpixel-origin behavior, Burrobot touch
  response coverage, inline-order object touch diagnostics, Labyrinth object
  tests, and the checked-in S3K complete-run BK2 needed by the trace catalog.
- **S1 trace-fleet frontier pass (2026-06-06).** Merged
  `bugfix/ai-s1-trace-round4` after validating the accepted Codex fleet fixes
  against the S1 green guard set. The pass advances GHZ2 Obj18 platform timing
  (f615->f2370 through bridge, rock, swinging-platform, Chopper, and Obj18
  fixes), LZ water and slide behavior (LZ1 f112->f302, LZ2 f463->f1089), SBZ2
  Electrocuter and SLZ2 fan object frontiers, SBZ3 button and Obj64 bubble
  timing (f45->f1421), and the real Final Zone trace (f277->f713) without
  regressing the nine S1 guard traces. Remaining red frontiers are documented in
  `docs/TRACE_FRONTIER_LOG.md`.
- **Release hardening branch (2026-06-06).** Started the no-known-hidden-failures
  release pass: documented the two-phase roadmap, enabled branch-policy checks
  for release PRs, made ROM-gated tests reject invalid configured ROM files,
  moved Turtloid/Sol constructor child spawns onto managed lifecycle helpers, and
  isolated lightning-shield spark virtual patterns from shared object art.
- **Release hardening validation (2026-06-06).** The same branch now keeps the
  S3K AIZ/CNZ known-failing tests enabled, runs trace-replay policy tests in
  release CI, bounds the one legacy S3K AIZ trace bootstrap, documents the S2
  Tornado trace contract, resets the AIZ intro terrain-swap cache across game
  bootstrap, and routes ICZ palette/animated-tile consumers through typed
  runtime state.
- **S1 complete-run trace suite — 18 acts from one TAS (2026-06-05).** A single complete-run BizHawk
  movie (Raiscan, every level start-to-finish) is auto-segmented by a new recorder
  (`tools/bizhawk/s1_complete_run_recorder.lua`) into a per-act trace for **all 18 S1 gameplay acts**
  (GHZ1..FZ), each sliced at its BK2 offset. No mid-run-offset bootstrap change was needed — the
  recorder's frame-0 arming makes each complete-run frame-0 row identical to a dedicated-BK2 trace.
  17 acts now pin a genuine object-physics first-divergence (the new S1 frontier targets; FZ needs
  Final-Zone-specific handling). Tests use the engine gameplay-progression `zone()` index, not the ROM
  `zone_id` (the documented zone-numbering pitfall). See `docs/TRACE_FRONTIER_LOG.md`.
- **Clean architecture boundary ratchets (2026-06-05).** Merged
  `feature/ai-clean-architecture-integration`: tightened the architecture review guardrails around
  package-cycle edges, module ownership, graphics runtime bindings, cross-game donor construction, and
  raw object-lifecycle calls. The branch keeps runtime state ownership explicit and records the current
  architectural debt as shrink-only ratchets so future cleanup can reduce the baseline without widening
  shared-layer coupling.
- **Architecture consolidation feature (2026-06-05).** Merged
  `feature/ai-clean-architecture-consolidated`: consolidated the architectural review workers into one
  branch, extracting boot/menu controllers, engine render dispatch, donated data-select warmup, level
  rewind snapshots, and dynamic object rewind codecs into focused collaborators. The merge also tightens
  source-guard ratchets and keeps follow-up architecture guidance in the contributor docs.
- **Architecture roadmap completion (2026-06-05).** Integrated the follow-on roadmap phase (recovered
  from a sibling architecture agent that worked in the shared checkout instead of a worktree, then
  reconciled cleanly onto develop — zero conflicts): playable terrain collision paths name
  `FrameCollisionPlan.terrainOnly()` at call sites with plan-aware `CollisionSystem` overloads,
  `ObjectArtData` drops dead legacy `obj26`/`obj41`/`obj79` fields, the architectural source guard blocks
  shared object-art metadata growth, and `ObjectSolidContactController` is extended. Its own suite is
  green. Landed under the land-genuine/allow-regression policy. The completion had *accidentally
  reverted* a genuine MTZ2 fix — an `MTZPlatformObjectInstance` Obj6B `moveType` eager-arm the prior
  comment warned shifts every rider 1px (f453); since that eager-arm gave mtz3 no benefit either, it was
  **restored** (MTZ2 stays f873). **One accepted regression remains: MTZ3 7596→5664**, from the
  completion's terrain-collision-plan changes (`CollisionSystem`/`ObjectSolidContactController`/
  `PlayableSpriteMovement`), logged in `docs/TRACE_FRONTIER_LOG.md` as a follow-up. mcz1/scz/cnz1/mcz2
  held; EHZ1/WFZ green; spilled-ring behavior preserved.
- **Spilled rings as ROM Obj37 objects in the unified touch loop (2026-06-05).** Merged the
  spilled-ring object model (`LostRingObjectInstance` + shared `SpillAnimationState` + type-keyed
  every-frame lost-ring touch branch in `ObjectTouchResponseController` + `LostRingRewindCodec` +
  cross-game gating), reconciled onto the architecture-roadmap refactor. Spilled rings now execute in
  the object loop and participate in the slot-ordered touch loop (ROM `Touch_ChkValue`), so a lower-slot
  spilled ring suppresses a later hazard. **Advances MTZ2 f641→f873.** Landed under the
  land-genuine/allow-regression policy with **one accepted, documented regression: SCZ green→f6180**
  (spilled-ring SST-occupancy parity in the object-dense zone — a concrete orchestration follow-up,
  logged in `docs/TRACE_FRONTIER_LOG.md`). mcz1 held at f2757; EHZ1/WFZ stay green. Spec/plan under
  `docs/superpowers/`.
- **Architecture roadmap cleanup (2026-06-05).** Merged `feature/ai-architecture-roadmap` (15 commits):
  object-manager collaborator extraction, owned editor/frame runtime contexts, shared object-art data
  split, explicit frame-collision phases (`FrameCollisionPlan`), pattern-atlas overlap guard, scoped
  object construction context, and tightened architecture-governance guards. Compiles with the core
  architecture guards green; landed under the land-genuine/allow-regression policy — any trace
  frontier moved by the refactor is captured in `docs/TRACE_FRONTIER_LOG.md` for an orchestration
  follow-up rather than blocking the merge. (Pre-existing `TestRewindArchitectureGuard` red on the
  `LbzCupElevatorInstance` baseline is unrelated to this branch.)
- **S2 trace-fleet pass 8 — 4 genuine advances, 0 regressions (2026-06-05).** Third 16-trace S2 pass;
  diminishing returns confirmed (10→9→4 advances across passes 6-8 as the generic per-trace fleet
  saturates). Landed: ChopChop vertical-band gate (arz2 669→857), a `PlayableSpriteMovement` sidekick
  correction (cnz2 2172→2880), the first direct `SidekickCpuController` fix (mcz1 2522→2757), and a
  Slicer badnik fix (mtz3 7304→**7596**). Combined sweep: greens intact, no regression. The 12
  no-changes cluster on CPU-sidekick (Tails) behavior — the next high-leverage target is a focused
  `SidekickCpuController`/ROM `TailsCPU_*` deep-dive rather than more generic passes. See `CHANGELOG.md`.
- **S2 trace-fleet pass 7 — 9 genuine advances, 1 accepted+queued regression (2026-06-05).** Second
  16-trace S2 pass under the land-genuine/investigate-regressions-after policy. 9 ROM-cited advances:
  two universal player-physics corrections cited across all three games — variable jump-height cap
  gates on `jumping(a0)` not the held button (cnz2 1784→2172), and standing-still duck/look-up reads
  pre-friction inertia (mcz1 2362→2522) — plus OOZ popping platform (ooz1 1133→1756), CPZ spin-tube
  waypoint preserve (cpz2 2542→2888), SpinyOnWall detection (cpz1 3303→3329), Monitor inclusive right
  edge (mcz2 4252→4485), per-test water reload (arz2 566→669), MTZ3 Spring-Wall flush-side bounce
  (mtz3 6913→**7304**), and the boss-defeat routine-read-once one-frame deferral (dez1 1366→2194). The
  dez1 deferral lives in the shared `AbstractBossInstance`, so it also shifts WFZ's boss (ObjC5,
  `routine_secondary=$1E` defeat) by one frame: WFZ green→f12886 — accepted and queued. **The
  follow-up restored it:** a per-class `defeatDeferralAppliesToThisBoss()` hook (default false; only
  ObjAF/Mecha Sonic overrides true) scopes the deferral to the primary-`routine` defeat path, so
  **WFZ is green again with DEZ1 held at 2194**. Combined sweep: no other regression; EHZ1/SCZ stay
  green; the two shared physics changes regressed nothing. See `CHANGELOG.md`.
- **S2 trace-fleet pass 6 — 10 genuine object/boss/sidekick advances (2026-06-04).** A 16-trace S2
  fleet pass (per-trace worktree + triage/fix/verify agents, land-genuine/investigate-regressions-after
  policy) landed 10 ROM-cited per-object fixes, each in its own object/boss class: ChopChop subpixel
  X-move (arz2 549→566), OOZ Fan sub-pixel-preserving push (ooz1 756→1133), Button off-screen
  render-flag gate (mtz1 863→1000), Mecha Sonic wind-up + boss touch lag (dez1 1023→1366), vertical
  Flipper per-player standing (cnz2 1775→1784), MCZ vine off-screen sidekick release (mcz1 2181→2362),
  CPZ spin-tube per-character capture (cpz2 2518→2542), Speed Booster dual-character boost (cpz1
  2822→3303), MCZ rotating-platform parent solidity (mcz2 4009→4252), and the dead-CPU-Tails fall
  `Screen_Y_wrap` bypass (mtz3 3719→**6913**, the standout). Combined full-suite S2 sweep: zero
  regressions, all three greens (EHZ1/SCZ/WFZ) stay green, and mcz2 synergized past its isolated
  frontier. Six traces (arz1/cnz1/htz1/htz2/mtz2/ooz2) were left unmoved — their first-divergences
  are real bugs the generic agents correctly declined to paper over. See `CHANGELOG.md`.
- **S2 object-load vertical-eligibility parity (2026-06-04).** The post-camera placement creation
  path (`inlineCreateObject`) applied the S2 Camera_Y filter that ROM `ObjectsManager` does not (it
  loads on the X-window scan, no Y filter), so some objects loaded one frame late. Sharing the
  vertical-eligibility policy across both S2 load paths fixed a CNZ Big Block (ObjD4) 1px position
  drift: restored CNZ1 f3831→f3906 (its pre-windowing frontier) and advanced HTZ2 f1078→f2306, no
  regression. Closes the windowing cascade — no remaining regression.
- **S2 sidekick interact-slot despawn parity (2026-06-04).** Modelled ROM's `interact(a0)` default
  (0 = MainCharacter = ObjID_Sonic 0x01) and zeroed-on-delete semantics in the Tails-CPU despawn
  comparator, distinguishing never-ridden (→0x01) from ridden-then-deleted (→0) interact slots
  (S2-gated). Advanced MTZ1 f375→f863 and restored MTZ3 f2638→f3719 (its pre-cascade peak), no
  green/S1/S3K regression. (CNZ1 remains at 3831 pending a separate ObjD4 update-cadence fix.)
- **ROM object-windowing port + transient/cog lifetime fixes (2026-06-04).** Ported ROM's S2 object
  load/unload timing onto the SlotAllocator substrate behind a per-game `ObjectWindowingStrategy`
  (shared `ObjectManager` no longer depends on game code): ROM single-load-per-frame ordering, final
  cursor boundaries, and object-side `MarkObjGone`. Also fixed the per-game explosion (Obj27) self-delete
  frame (S1/S2/S3K) and the MTZ giant-cog ride-release carry/push timing (mtz3 2047→2638). Added a
  comparison-only object-occupancy oracle. No green/S1/S3K regression; one accepted, documented cascade
  (cnz1 3906→3831) pending its separate ground-collision fix. See `docs/TRACE_FRONTIER_LOG.md`.
- **Object-slot allocator unification (2026-06-04).** Extracted a single `ObjectManager`-owned
  `SlotAllocator` authority (per-game empty predicate, recycle, rewind snapshot/restore) and routed
  all dynamic-slot allocation through it; `objectIdInSlot`/`execOrder` stay in `ObjectManager` as the
  identity authority. Verified pure refactor — zero trace-frontier movement. Foundation for the ROM
  object-windowing port (`docs/superpowers/specs/2026-06-04-rom-object-windowing-port-design.md`).
- **S2 sidekick interact-slot foundation (2026-06-04).** Replaced the instance-based Tails-CPU
  ride latch with ROM's persistent slot-based `interact(a0)` model (slot index, never cleared;
  id re-snapshot each on-screen frame), so the off-screen despawn matches ROM without the old
  masking guards. Greens (S1, EHZ1/SCZ/WFZ) and S3K frontiers unchanged; HTZ2 advanced
  (795→1078). One **known, documented regression** — MTZ3 3719→2638 — whose prior advance leaned
  on the old broken heuristic and is being re-derived on the correct foundation (follow-target +
  interact-slot recycle parity). Kept deliberately to stop further work building on the wrong model.
- **S2 trace advances pass 4 (2026-06-04).** Six more S2 `*TraceReplay` frontiers moved
  forward (ARZ2, CNZ2, CPZ1/CPZ2, MTZ3, MCZ2), including a per-object balance-width hook and
  ROM-accurate MTZ-cog multi-piece ordering — verified with no green/S1/S3K regressions.
- **S2 trace advances pass 3 + construction-context fix (2026-06-04).** Seven more S2
  `*TraceReplay` frontiers moved forward (ARZ1/ARZ2, CPZ1/CPZ2, MCZ1/MCZ2, DEZ), and a
  latent engine bug fixed — `ObjectConstructionContext` now keeps the construction context
  across nested child spawns, so bosses spawning multiple children inject services into all
  of them (the Death Egg Robot `ForearmChild` no longer crashes). Verified with no
  green/S1/S3K regressions.
- **S2 trace-replay frontier advances, continued (2026-06-04).** Eight more Sonic 2
  `*TraceReplay` frontiers moved forward (trace-green-fleet pass 2: ARZ2, MTZ2/MTZ3,
  CPZ1/CPZ2, MCZ1/MCZ2, OOZ1), including ROM-accurate `Camera` vertical-wrap visibility
  and a universal `SolidObject` zero-distance landing predicate — verified together with
  no green/S1/S3K regressions.
- **S2 trace-replay frontier advances (2026-06-03).** Seven Sonic 2 `*TraceReplay`
  frontiers moved forward via disassembly-cited ROM fixes with no zone/route/frame
  carve-outs (ARZ1/ARZ2, CPZ2, HTZ1, MCZ1/MCZ2, OOZ1), produced by the
  `trace-green-fleet` agent workflow and verified together with no green regressions
  and S1/S3K byte-identical. See `docs/agent-workflow/trace-green-fleet-decisions.md`.
- **Widescreen foundation + Discord Rich Presence (2026-05-30).** New `DISPLAY_ASPECT` presets
  (NATIVE_4_3 / 16:10 / 16:9 / 21:9 / 32:9, height fixed at 224) make camera, boundaries, spawn
  windows, and the object despawn/visibility surface width-driven, with `NATIVE_4_3` kept
  byte-for-byte identical and pinned by a regression test; trace/headless runs are forced to
  320×224. Deliberate right-boundary/despawn divergences are logged in `KNOWN_DISCREPANCIES.md`;
  widescreen UI centering and title/special-stage backgrounds remain experimental. Also adds
  opt-in Discord Rich Presence behind `DISCORD_RICH_PRESENCE_*` config flags.
- **S2 trace-replay parity (2026-05-15 to 05-18).** Native title-card prelude ticking, frame-0
  bootstrap comparison, and re-recorded S2 traces surfaced and fixed six engine bugs; the Casino
  Night and Sky Chase level-select replays now reach green.
- **Editor groundwork.** A config-gated editor/playtest loop with block/chunk previews, world-grid
  navigation, and teardown-safe mode switching that preserves `MutableLevel` edits across
  gameplay-mode rebuilds.
- **Runtime/session modernization.** Retired the legacy `GameRuntime` / `RuntimeManager` façade;
  process-wide services sit behind `EngineServices`, with `SessionManager`, `WorldSession`, and
  `GameplayModeContext` owning lifecycle, durable world state, and gameplay-scoped managers.
- **Architectural hardening.** Object code stays on injected `ObjectServices`, shared
  checkpoint/level-loading paths avoid concrete S3K dependencies, animation managers join rewind
  snapshots, trace parity tests sit behind an explicit Maven profile, and ArchUnit guards plus
  review sweeps moved remaining game-id branches behind providers/feature flags.
- **S3K bring-up and parity.** Angel Island, Carnival Night, Hydrocity, Marble Garden, Ice Cap, and
  Mushroom Hill continue to gain ROM-cited objects, badniks, minibosses/bosses, events, parallax,
  animated tiles, and palette/PLC coverage, prioritizing complete playable route slices.
- **Display and audio polish.** Runtime-cycleable display color profiles (`RAW_RGB` / `MD_ANALOG` /
  `NTSC_SOFT`, persisted), S3K SMPS pitch/modulation/1-up restore timing fixes, and foreground-mask
  water alignment against the rendered viewport.
- **Rewind framework.** Gameplay-scoped keyframe capture, deterministic seek/replay, held-rewind
  trace debugging, opt-in live-play rewind (`rewind.liveEnabled`) with reverse audio and a HUD,
  and broad coverage across player/sidekick/object/level/registry state via audit-first generic
  capture, stable identity ids, and compact schema codecs.
- **Trace replay and diagnostics.** Stronger recorder schemas, comparison-only aux streams,
  compressed fixtures, expanding per-frame diagnostic events (register, solid-object, control-lock,
  and wall-sensor capture), and a visual trace visualizer with ghost characters, first-desync pause,
  and a camera-focus cycler. Dozens of ROM-cited S3K physics and object fixes advanced the AIZ, CNZ,
  and MGZ replay frontiers; remaining blockers are documented with ROM constraints.
- **S3K known blockers.** Remaining replay-frontier blockers (e.g. AIZ F6920 collapsing-platform
  ordering, CNZ F6304 Tails-on-door re-land) are documented with ROM constraints and ruled-out
  hypotheses so future work avoids regression-prone hacks.
- **Object physics standardization.** Typed solid/touch/participation/control/native-position/
  lifecycle contracts with the highest-risk object paths migrated, updated agent guidance, and
  static guard tests enforcing explicit contracts.
- **Cross-game cleanup.** Collision, solid-object ordering, sidekick handling, feature-flagged
  physics differences, configuration UX, debug rendering, and performance hot paths continue to be
  tightened across all three games.
- **Architectural cleanup.** Remaining game-id branches moved behind providers/feature flags;
  render overlays (HTZ earthquake, HCZ wall-chase) migrated into `SpecialRenderEffectRegistry`;
  `ScrollEffectComposer` adoption brought to 100% across all 26 scroll handlers; the rendering
  pipeline extracted from `LevelManager` into a new `LevelRenderer`; S1/S2 child spawns routed
  through `spawnChild`/`spawnFreeChild`; plus assorted hot-path allocation and lookup optimizations.
- **Object render-lifecycle fix.** Closed a "spawns but renders invisibly" class of bug introduced
  by the DI migration: objects that cache a sprite renderer in their constructor went invisible when
  spawned via raw `addDynamicObject` (which sets services only after construction). Fixed the S2 and
  HCZ egg-prison capsule animals, added a source guard (`TestNoRendererCaptureInUnsafeSpawn`) and a
  one-time runtime warning so the otherwise-silent failure cannot recur.
- **Trace video capture (2026-06-03).** A headless, deterministic `TraceCaptureTool` renders a
  recorded trace offscreen and writes a lossless, audio+video-synced MKV (FFV1 + 48 kHz FLAC) for
  demo reels — optionally including the desync ghosts. The scope-agnostic `com.openggf.capture`
  pipeline (recorder/sink/encoder + video/audio taps) sits above the game; a `HeadlessGameBoot`
  stands up an offscreen `GameLoop`, a `TraceReplayDriver` (extracted from `TraceSessionLauncher`)
  drives the deterministic replay, and an offline audio backend mode synthesizes for the recording
  without any sound-device output. See the `trace-capture` skill and `CONFIGURATION.md`. This
  develop sync also brings the S3K LBZ tube elevator, ride grapple, Ribot badnik, and rolling-drum
  parity work from concurrent branches.

See `CHANGELOG.md` for the detailed 0.6 prerelease change history.

### v0.5.20260411 (Released 2026-04-11)

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe gameplay-mode teardown, and multi-instance play-testing, while Sonic 3 & Knuckles
gameplay coverage has expanded across Angel Island and Hydrocity. AIZ2 now has the Flying Battery
bombing sequence, end boss, post-boss capsule/cutscene flow, and AIZ-to-HCZ transition represented,
while HCZ now has a larger object/event pass and HCZ1-to-HCZ2 progression.

- **Two-tier service architecture:** all 180+ game object classes migrated from direct singleton
  access to a two-tier dependency injection pattern (`GameServices` global facade + `ObjectServices`
  context-scoped injection). NoOp sentinels replace null checks throughout.
- **Gameplay session ownership:** this release introduced the first explicit gameplay-state
  ownership layer, later superseded by `SessionManager`, `WorldSession`, and
  `GameplayModeContext`. Enables safe editor mode enter/exit and level rebuilds.
- **LevelManager decomposition:** the engine's largest class broken into `LevelTilemapManager`,
  `LevelTransitionCoordinator`, and `LevelDebugRenderer` with ~73 methods extracted.
- **MutableLevel:** snapshot, mutation, and dirty-region tracking for level tile data — the
  foundation for the upcoming level editor's undo/redo and real-time tile editing.
- **Common code extraction (5 phases):** 15+ abstract base classes, 10+ shared utilities, and
  systematic deduplication across all three games, including `SubpixelMotion`, `AnimationTimer`,
  `FboHelper`, `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`,
  `AbstractZoneScrollHandler`, and more.
- **Knuckles** is now a playable character with full glide/climb state machine, ROM-accurate
  jump height, wall grab, ledge climb, and sliding physics. Works in S3K natively and via
  cross-game donation into S1/S2 with correct palette and HUD from the lock-on ROM.
- **Sonic 3&K** expands with title screen (SEGA logo, Sonic morph animation, interactive menu),
  level select screen (SONICMILES background, zone icons, sound test), AIZ miniboss completion
  (defeat flow, napalm attack, staggered explosions), AIZ2 Flying Battery bombing/end-boss work,
  signpost and results screen, Blue Ball special stages (WIP) with per-character art/palette,
  S3K bonus-stage work across Gumball, Glowing Sphere/Pachinko, and Slots, per-character physics
  profiles, palette cycling for all zones, HCZ water rush / conveyor / fan / block / door /
  miniboss coverage, and many new badniks/objects including CollapsingBridge, MegaChopper,
  Poindexter, Blastoid, Buggernaut, Bubbler, TurboSpiker, and InvisibleHurtBlockH.
- **Insta-shield** fully implemented with ROM parity: activation, hitbox expansion, persistent
  lifecycle, cross-game donation, and DPLC cache management.
- **Multi-sidekick system** with configurable sidekick chains, per-character respawn strategies,
  virtual VRAM bank allocation, and VDP-accurate sprite priority ordering.
- **Tails AI rework:** ROM-accurate respawn gating, PANIC mode rewrite, flying/despawn
  improvements, P2 manual override, and per-zone boss/event wiring.
- **Cross-game donation** now bidirectional: S1 can donate into S2/S3K, with `DonorCapabilities`
  interface, `CanonicalAnimation` vocabulary, and `AnimationTranslator` for any game pair.
- **Rendering pipeline:** PatternAtlas slot reclamation, batched DPLC updates, virtual pattern ID
  validation, SAT sprite-mask replay ordering for mixed-priority S3K bonus-stage art, and
  fail-fast shader error handling.
- **Trace replay testing:** automated accuracy verification that records per-frame physics state
  from the real ROM, then replays the same inputs through the engine and compares every field.
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; the latest GHZ bridge pass fixes
  the F2967 rider Y divergence by keeping Bri_Solid's final `Plat_NoXCheck` width and updating
  the rider bend log before sag calculation (`docs/s1disasm/_incObj/11 Bridge.asm:98-114`,
  `135-152`, `_incObj/sub PlatformObject.asm:19-42`, `58-76`, `_incObj/sub ExitPlatform.asm:8-23`).
  A second baseline (S1 MZ1, 7,936 frames) now passes after the Obj52 Moving Block
  jump-carry fix: S1 `MBlock_StandOn` clears Sonic's on-object status via
  `ExitPlatform`, then still moves the block and applies one final `MvSonicOnPtfm2`
  carry on the jump-off frame (`docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83`,
  `_incObj/sub ExitPlatform.asm:5-24`, `_incObj/15 Swinging Platforms.asm:177-194`).
  Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
  Python) as recording backends — both produce identical output consumed by the same Java test
  infrastructure.
- Comprehensive user guide, 15+ design specs and implementation plans, and broad test coverage
  improvements including automated singleton lifecycle testing.

See CHANGELOG.md for full details.

### v0.4.20260304 (Released 2026-03-04)

A release-sized update focused on expanding playable coverage, ending sequences, and engine maturity.

- **Package rename** from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
- **Master title screen** implemented: engine-wide PNG-based title screen with animated clouds, game
  selection, and pixel font renderer. Displayed on startup before entering game-specific title flow.
- **Sonic 1** has moved from initial support to feature complete: title screen flow, special
  stages, major per-zone event scripting, extensive object and badnik additions, multiple boss
  implementations (GHZ, MZ, SYZ, LZ, SLZ, FZ), Labyrinth water/drowning/splash behaviour,
  ending/credits work, SBZ post-level-end sequence, demo playback, edge balance and push block
  collision corrections, and slope crest sensor guard. Expect minor bugs, but the game should be playable
  from beginning to end.
- **Sonic 2** adds title screen support, major object passes for MTZ/SCZ/WFZ/OOZ, 9 boss fights
  (MCZ, MTZ, WFZ, and both DEZ bosses — Mecha Sonic and Death Egg Robot, plus Robotnik escape),
  a complete credits and ending cutscene system with ROM-accurate visuals, expanded per-zone event
  architecture, demo playback, signpost/badnik palette/stair block art fixes, and a systematic
  TODO resolution pass with disassembly validation.
- **Sonic 3&K** sees major AIZ progress including intro cutscene systems, hollow tree and vine
  traversal parity work, miniboss object set bring-up, initial badnik implementations, shield/PLC
  integration fixes, a full water system with provider architecture and underwater palettes,
  seamless AIZ fire transition flow, and related regressions/tests.
- **Cross-game feature donation** implemented: a donor game (S2 or S3K) can provide player sprites,
  spindash dust, physics, palettes, and SFX while the base game handles levels, collision, objects,
  and music. Now includes cross-game Super Sonic delegation.
- **Per-game physics** and Super Sonic state/control flow (implemented for S2, with cross-game
  delegation to S1 and S2 game modules).
- **Profile-driven level loading:** declarative `LevelInitProfile` system with 13 ROM-aligned
  steps per game, replacing the monolithic `loadLevel()` path.
- **Testability refactor:** `GameContext`, `SharedLevel`, `HeadlessTestFixture` builder, and
  profile-driven test teardown. Test grouping by level and 8-JVM parallel execution.
- **Engine fixes:** solid object edge jitter fix, S1 slope crest sensor guard, jump-while-airborne
  guard, fade transition flash fix, results screen rendering fix, HTZ earthquake fixes, SFX
  channel replacement fix.
- PLC/art-loader refactors, RomOffsetFinder/ObjectDiscoveryTool enhancements, configuration
  documentation, and broad audio/stability/performance hardening.

See CHANGELOG.md for full details.

### v0.3.20260206

A massive release covering 366 commits across every major subsystem.

- **Tails** (Miles Prower) is now a playable character with ROM-accurate CPU AI follower behaviour,
  input replay, flight, and configurable sidekick toggle.
- **Multi-game architecture:** The engine has been refactored to support multiple games via a
  provider-based abstraction layer, with initial Sonic 1 ROM support (level select, title cards, HUD,
  audio with S1-specific SMPS driver configuration) alongside the existing Sonic 2 support.
- **Physics:** The physics engine has been completely rewritten to match ROM behaviour.
- **Bosses and objects:** Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ, CNZ, ARZ), along
  with 15+ new badniks and 50+ new game objects spanning all implemented zones.
- **Water:** A full water system with drowning mechanics is in place for CPZ and ARZ.
- **Graphics:** The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated
  rendering pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs).
- **Audio:** Major accuracy improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference)
  and the SMPS driver.
- **Infrastructure:** Per-game ROM configuration, a HeadlessTestRunner for physics integration
  testing, visual and audio regression test suites, a multi-game test annotation framework, GraalVM
  native build support, and significant performance optimisations throughout.

See CHANGELOG.md for full details.

### v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with a
few known issues. Physics have been improved, parallax backgrounds implemented and complete for EHZ,
CPZ, ARZ and MCZ. Some sound improvements, title cards, level outros, etc.

### v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SFX and music are implemented. Everything has room for improvement, but this now
resembles a playable game.

### v0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

### v0.01 (Pre-Alpha, first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
