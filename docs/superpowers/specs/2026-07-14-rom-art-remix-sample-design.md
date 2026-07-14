# Sonic 2 ROM-Art Remix Sample Design

## Goal

Preserve a maintained executable contract for Mod API 2.1
`ModContext.registerRomObjectArt(...)` after `sample-flappy` moves to native S3K
Tails. The replacement is a deliberately small Sonic 2 patch project named
`sample-rom-art-remix`, with a build-along guide focused only on borrowing and
rendering art from the player's own ROM.

## Why a separate sample

Keeping an unused ROM-art call inside Flappy would misrepresent the new game's
architecture, while relying only on API unit tests would break the gallery principle
that creator workflows remain executable contracts. A separate sample keeps the
2.1.0 feature visible without coupling it to S3K, custom controls, HUD policy, or the
Flappy minigame.

## Executable project

The checked-in source lives under
`src/test/resources/mods/sample-rom-art-remix-src/` and targets a Sonic 2 `patch`
with `engineApiRange: ">=2.1.0 <3.0.0"`.

Its entrypoint performs three actions:

1. registers one namespaced object factory;
2. registers that object's `bird` sheet through `registerRomObjectArt`, using the
   verified Sonic 2 Tails flying-art, mapping, DPLC, palette-line, and bank-size
   request currently maintained by the old Flappy source; and
3. registers a tiny one-screen Sonic 2 mod zone containing one placement of the
   object.

The object does not seize, hide, move, or damage the player. It resolves the
materialized `sample-rom-art-remix:bird` renderer and alternates the two documented
Tails flying mapping frames at a fixed cadence. Its animation counter is a non-final
scalar and the object implements `RewindRecreatable`, so a rewind restores both
identity and visible frame phase.

ROM-art intake materializes patterns/mappings, not the corresponding palette bytes.
The ordinary Sonic 2 level loader supplies the shared `Pal_SonicTails` character
palette on host-owned line 0 for both the default Sonic team and Sonic+Tails. The
sample and its ROM-backed integration test therefore use the default team; there is
no select-Tails instruction or dependency on extra-team configuration. The sample
does not force a team through the new 2.3 launch policy, which keeps this contract
genuinely usable at its declared 2.1 floor. The real caveat documented by the guide
is Knuckles-main cross-game lock-on: that presentation replaces line-0 indices 2-5,
so it is outside the sample's supported palette setup.

The small zone exists only because a registered object must be instantiated and
rendered for the sample to be executable. It reuses the established bounded Sonic 2
mod-zone format and inserts after a valid results-driven stock anchor. It introduces
no new engine API or runtime architecture.

## Guide migration

The current `docs/modding/guides/flappy-remix.md` is replaced by
`docs/modding/guides/rom-art-remix.md`. The guide retains the valuable material on:

- why ROM bytes never ship in the mod JAR;
- validated Sonic 2 ROM address, compression, mapping, DPLC, palette, and bank-size
  fields;
- registration-time versus gameplay-launch validation;
- namespaced object-art lookup and renderer use;
- correct Mod API floor declaration; and
- build, package, validate, install, and test commands.

It removes Flappy controller physics, forced scrolling, obstacle generation,
scoring, and the old S2 palette workaround. Gallery index, handbook links, sample
counts, changelog text, package tests, and integration-test names move to the new
sample. General `content-mods.md` API reference remains valid and links to the new
guide.

## Compatibility and ownership

`registerRomObjectArt` remains Sonic 2 patch-only at API 2.1. No S3K support is
implied. Materialization still occurs at gameplay launch through the bounded ROM-art
path and owner fault boundary. The sample JAR contains code and original level data,
but no Sega art or palette bytes.

The sample's declared API floor remains 2.1.0 even when built by an engine whose
current API is 2.3.0, because it calls no newer surface. This preserves a meaningful
compatibility example rather than mechanically raising every gallery project.

## Verification

Test-first delivery covers:

1. packaging and zero-finding validation of the eighth maintained source project;
2. rejection when used with a non-S2 or standalone manifest;
3. ROM-gated materialization of the requested Tails sheet;
4. default-team use of the shared Sonic/Tails line-0 palette, plus the documented
   Knuckles-main lock-on caveat;
5. namespaced renderer resolution and the two expected mapping frames;
6. an actual rendered frame/pixel probe rather than registration-only assertions;
7. rewind restoration of the object's animation phase;
8. confirmation that the packed JAR contains no materialized ROM bytes;
9. link/sample-count guards for the renamed guide and gallery index; and
10. unchanged Mod API 2.1/2.2 compatibility snapshots and ROM-art unit tests.

This sample and guide land before, or atomically with, removal of the old Flappy
consumer. The repository therefore never intentionally lacks a maintained executable
owner for the ROM-art intake workflow.
