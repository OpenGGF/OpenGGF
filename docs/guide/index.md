# OpenGGF User Guide

OpenGGF is an open-source, Java-based engine that reimplements classic Mega Drive / Genesis
Sonic the Hedgehog games. It is not an emulator: it reads data from original ROM images and
runs its own implementation of the game logic, physics, rendering, and audio. No copyrighted
assets are included. You must supply your own legally obtained ROM files.

The engine currently supports Sonic the Hedgehog (S1), Sonic the Hedgehog 2 (S2), and
Sonic 3 & Knuckles (S3K) at varying levels of completeness. This guide follows the current `0.7.prerelease`
development line; the [published 0.6 summary](../changelog/v0.6-release-summary.md)
records the older release scope.

## Choose Your Path

### I want to play

You have ROM files and want to get the engine running.

- [Getting Started](playing/getting-started.md) -- From zero to playing in five minutes
- [Configuration](playing/configuration.md) -- Common setup questions answered
- [Controls](playing/controls.md) -- Keyboard and controller reference
- [Game Status](playing/game-status.md) -- What works, what's incomplete, what to expect
- [Troubleshooting](playing/troubleshooting.md) -- Fixing common problems

### I want to cross-reference the engine against the disassembly

You know s1disasm, s2disasm, or skdisasm and want to understand how the engine represents
the same concepts -- or you want to check the engine's accuracy.

- [How the Engine Reads ROMs](cross-referencing/how-the-engine-reads-roms.md) -- The data pipeline from ROM bytes to rendered screen
- [68000 Primer](cross-referencing/68000-primer.md) -- Just enough assembly to read object routines
- [Mapping Exercises](cross-referencing/mapping-exercises.md) -- Learn to trace any feature between disassembly and engine
- [Architecture Overview](cross-referencing/architecture-overview.md) -- Where things live in the codebase
- [Tooling](cross-referencing/tooling.md) -- RomOffsetFinder and other built-in tools
- [Per-Game Notes](cross-referencing/per-game-notes.md) -- S1, S2, and S3K specific quirks

### I want to contribute

You want to add objects, bosses, zones, or engine improvements.

- [Dev Setup](contributing/dev-setup.md) -- Environment, build, and test setup
- [Release Publishing](../project/release-publishing.md) -- Automatic publication on pushes to master
- [Architecture Deep Dive](contributing/architecture.md) -- Providers, services, and runtime
- [Documentation and Branch Policy](contributing/documentation-policy.md) -- Commit trailers, changelog/discrepancy updates, and PR documentation checks
- [Tutorial: Implement an Object](contributing/tutorial-implement-object.md) -- Worked example from disassembly to running code
- [Adding Bosses](contributing/adding-bosses.md) -- Boss-specific patterns
- [Adding Zones](contributing/adding-zones.md) -- Bringing up a new zone
- [Audio System](contributing/audio-system.md) -- SMPS driver, FM synthesis, PSG
- [Rewind System](contributing/rewind-system.md) -- Using, validating, and extending frame rewind
- [Zone and Act Test Standard](contributing/level-test-standard.md) -- Route, character, rewind and load-boundary obligations
- [Testing](contributing/testing.md) -- Writing and running tests
- [Trace Replay Testing](contributing/trace-replay.md) -- BizHawk recordings, replay tests, and divergence analysis

### I want to create levels

- [Level Editor](creating/level-editor.md) -- Create, edit, save, reopen, playtest, and export levels for all three games

### I want to make films and cutscenes

- [Stageplay](creating/stageplay.md) -- What Stageplay is and where to start
- [Your First Film](creating/stageplay-first-film.md) -- A ten-second film, step by step, in the editor and as a screenplay
- [Directing Guide](creating/stageplay-directing.md) -- Cast, routes, objects, camera, dialogue, voices, music, scene changes, export and mods
- [How Day Off Was Made](creating/stageplay-day-off.md) -- A finished film's techniques, shot by shot
- [Troubleshooting and Glossary](creating/stageplay-troubleshooting.md) -- Route failures, floors, "needs Run" and other surprises
- [Reference](creating/stageplay-reference.md) -- Screenplay fields, editor commands and keys, limits
- [Stageplay from the Terminal](creating/stageplay-authoring.md) -- Command-line tools for scripted films and agents

### I want to create mods

Start with the [creator handbook](../modding/index.md) for the maintained workflow,
formats, and samples. Mod API 0.7.0 is an unpublished, mutable candidate; use
matching engine and SDK artifacts from one commit. The
[release-policy descriptor](../../mod-api-release-policy.properties) owns the
current API version and publication status; the
[compatibility guide](../architecture/mod-api-compatibility.md) explains the contract.

## Related Documents

These documents live at the repository root and complement this guide:

- [README](../../README.md) -- Project overview and FAQ
- [CHANGELOG](../../CHANGELOG.md) -- Detailed release history
- [ROADMAP](../../ROADMAP.md) -- Development priorities
- [CONFIGURATION](../../CONFIGURATION.md) -- Full configuration reference (all keys)
- [CREDITS](../../CREDITS.md) -- Community resources this project builds on
- [OBJECT_CHECKLIST](../../OBJECT_CHECKLIST.md) -- Sonic 2 object implementation status
- [S1_OBJECT_CHECKLIST](../../S1_OBJECT_CHECKLIST.md) -- Sonic 1 object implementation status
- [S3K_OBJECT_CHECKLIST](../../S3K_OBJECT_CHECKLIST.md) -- Sonic 3&K object implementation status
