# OpenGGF mod creator handbook

Start with [setup and your first project](getting-started.md). Recipients use
[installing, updating and removing mods](installing-mods.md). The API is the
mutable, unpublished 0.7.0 candidate; use matching artifacts from one commit.

OpenGGF's mod workflow is source-first and reproducible: author files, convert them
with `ggfmod`, validate/package a jar, then enable it in the Mod Manager and restart.
Choose the smallest quickstart that matches your goal; they are ordered by typical
effort. Mod API 0.7 includes exclusive fresh-game insertion plus destination-scoped,
launch-only teams, deterministic input filters, and row-only HUD profiles; the
[content-mod reference](content-mods.md#choose-a-fresh-game-destination-and-presentation)
defines their ordering, replay, persistence, width, stock-default, and owner-fault
contracts.

## Native builds vs. the JVM jar

Code-bearing mods (objects, characters, zones, scenes, and standalone games) require the
**JVM jar**. They are loaded at runtime by the engine's mod classloader, which a
GraalVM native-image binary cannot use under closed-world AOT. Native builds do
not load these mods: the Mod Manager marks them `UNSUPPORTED` and refuses to
enable them, and a boot notice lists any that were enabled. **Data-only music
packs and reskins are unaffected and work on native builds.** To use code-bearing
mods, run `OpenGGF-<ver>-jar-with-dependencies.jar` (or the universal jar).

1. [Music pack](quickstarts/music-pack.md)
2. [Data-only art reskin](quickstarts/reskin.md)
3. [Object or badnik](quickstarts/object.md)
4. [Sonic 2 zone](quickstarts/zone.md)
5. [Playable character](quickstarts/character.md)
6. [Standalone game](quickstarts/standalone.md)
7. [Scene or non-platformer game](guides/mod-scenes.md)

## What you can build

| Scope | Delivered path | Current boundary |
|---|---|---|
| Music | Data-only WAV/Ogg stock overrides | No MP3 or base-game streamed SFX overrides |
| Object reskin | Baked sheet over exact stock provider key | Preserve host palette indices and all consumed mapping frames; no playable-character reskin |
| Object/character | Owned factories, injected services, character specifications, landing/reset hooks, saves and rewind | JVM; no arbitrary static gameplay state or mod super form |
| Stock-level edits | Immutable decoded placement operations, including owned object bindings | JVM; preserves the stock load pipeline; live editor application is separate |
| New zone or campaign | Sonic 2 v1 and bounded S3K v2 adapters, ordered acts and owned runtime providers | S1 adapter planned; S3K does not inherit arbitrary stock zone events |
| Scene/whole-screen game | Startup scene with input, audio, storage and canvas | JVM; ROM-backed scenes require the relevant supplied ROM |
| Original standalone game | No-ROM levels, characters, audio and progression | JVM; no standalone patch stacking, bonus/special-stage or roster UI |
| Shared UI and input | [Fonts, layout/focus and overlays](guides/creator-helpers.md); [named remappable actions](guides/action-bindings.md) | Logical pixels and explicit input edges; game rules and visual styling stay in the mod |
| State and testing | [Storage, captured state and service bundles](guides/creator-helpers.md); [production-backed Jupiter tests](testing.md) | Use complete session tests for gameplay rewind and renderer/capture checks for appearance |

See the detailed guides for narrower contracts; roadmap entries do not imply implementation.

## Follow-along guides

- [ROM-art remix](guides/rom-art-remix.md) — source-first tour of the
  `sample-rom-art-remix` gallery sample: bounded Sonic 2 art, mapping, and DPLC
  intake, launch-memory materialization, decoded-pattern probes, and rewind.
- [Native-Tails Flappy](guides/native-tails-flappy.md) — build-along tour of the
  `sample-flappy` gallery sample: an anchorless S3K fresh-game destination, scoped
  Tails/input/HUD policies, fixed camera, and rewind-stable recycling pipes.
- [Standalone platformer](guides/standalone-platformer.md) — build-along tour of the
  `sample-platformer` gallery sample: a no-ROM standalone game with a Tiled-authored
  level, an original character with a double jump, a patrolling badnik, and a spring
  gimmick.
- [Two-act Tide Circuit](guides/two-act-campaign.md) — an original hosted Sonic 2
  campaign with per-act runtime providers, checkpoints, progression, tagged saves
  and a route/rewind coverage matrix.
- [Mod scenes](guides/mod-scenes.md) — full-screen menus and games drawn by the mod: the
  startup-scene registration, lifecycle and fault boundary, the canvas, ROM sprites and
  characters, audio, storage and headless testing. Start from
  [hello-scene](../../examples/hello-scene/README.md), a two-class starter; Slay the Robotnik
  is the complete example.
- [AI-generated art](guides/ai-art.md) — prompting, quantizing, and laying out
  original sprite/tile PNGs for `ggfmod convert art`, and swapping generated art into
  either build-along sample.

## Experimental projects

- [Robotnik Tower Defence](../../examples/robotnik-tower-defense/README.md) —
  Industrial Action: defend Robotnik's base door from 15 waves of unionised
  Flickies with six ROM-drawn badnik defenses, upgrades, repair and an emergency
  bomb. An S3K mod scene with mouse/pad controls and saved records; not one of the
  nine maintained gallery samples.

- [Sonic Survivors](../../examples/sonic-survivors/README.md) — a separate Sonic 2 survivors roguelike: walled arenas cut from each route act, ROM-art badniks with hitpoints, a bounce combo, level-up cards, zone bosses, a route with act choice and saved meta-progression, all through the existing code-patch API. Not part of the nine maintained gallery samples.

- [Slay the Robotnik](../../examples/slay-the-robotnik/README.md) — a Slay the Spire-style deck-building roguelike on Sonic 3 & Knuckles, built as a mod scene: three heroes, four acts of zone maps, ROM-drawn badniks and bosses, events, shops and relics.
- [Infinite Sonic](../../examples/infinite-sonic/README.md) — a separate endless Sonic 1 project with terrain-aware ground/flying encounters using ROM-derived sections, seeded world recycling and the existing code-patch API. Not part of the nine maintained gallery samples.

- [Putt Putt Paradise](../../examples/putt-putt-paradise/README.md) — Sonic 2 Mini Golf using ROM-backed Emerald Hill courses, timed charges, independent alternating golfers, and host-authoritative direct TCP play. An external candidate-API project, not one of the nine maintained gallery samples.
- [Sitar Hero](../../examples/sitar-hero/README.md) — full-song ROM rhythm with career, four difficulties, practice, local and direct-connect multiplayer, and native instrument-playing performers on real ROM stages. Demonstrates mixed-ROM startup scenes, timestamped player controls, consumed-sample music timing, cancellable preparation, owner-scoped peer transport and running-game menu audio; its README is a reading-order tour of the source with recipes.

## Reference

- [`ggfmod` command reference](ggfmod.md)
- [Production-backed creator tests](testing.md)
- [Shared UI, art, animation, physics and state helpers](guides/creator-helpers.md)
- [Custom action bindings and remappable input](guides/action-bindings.md)
- [Manifest v1](formats/manifest.md)
- [Baked art containers](formats/baked-containers.md)
- [`ModLevelDefinition` formats v1 and v2](formats/level-definition.md)
- [Content mods and Mod API 0.7](content-mods.md)
- [Audio manifest v1](formats/audio-manifest.md)
- [Character archetypes](concepts/character-archetypes.md)
- [Executable-code trust](concepts/trust.md)
- [Namespaced identity semantics](concepts/id-semantics.md)
- [`ggfmod validate` findings](troubleshooting.md)
- [Catalog scalability probe and acceptance](tools/scalability.md)
- [Maintained sample gallery](samples/index.md)
- [Deferred-backlog decisions](BACKLOG.md)
- [GUI tooling evaluation](GUI_TOOLING_EVALUATION.md)

The nine sample sources are built by the default test suite. Treat them as
executable contracts rather than snippets copied out of context.

Advanced module authors can configure [session pacing, scripted rewind and display
controls](content-mods.md#advanced-module-session-controls).
