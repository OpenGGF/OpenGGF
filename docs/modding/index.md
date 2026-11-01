# OpenGGF mod creator handbook

Start with [setup and your first project](getting-started.md). Recipients use
[installing, updating and removing mods](installing-mods.md). The API is the
mutable, unpublished 0.7.0 candidate; use matching artifacts from one commit.

OpenGGF's mod workflow is source-first and reproducible: author files, convert them
with `ggfmod`, validate/package a jar, then enable it in the Mod Manager and choose Apply.
Choose the smallest quickstart that matches your goal; they are ordered by typical
effort. Mod API 0.7 includes exclusive fresh-game insertion plus destination-scoped,
launch-only teams, deterministic input filters, and row-only HUD profiles; the
[content-mod reference](content-mods.md#choose-a-fresh-game-destination-and-presentation)
defines their ordering, replay, persistence, width, stock-default, and owner-fault
contracts.

For a specific behavior change, use the [task decision guide](task-recipes.md):
stock-act badniks, scoped movement, gameplay menus, persistent upgrades, native
enemy recipes, and original campaigns. Agents can use the repository
[mod-authoring skill](../../.agents/skills/mod-authoring/SKILL.md) to read only the
relevant recipe and owning source.

The example mods are MIT-licensed, unlike the GPL-3 engine: copy one as a starting
point and keep its `LICENSE` notice with the parts you reuse. The engine, Mod API
and SDK your mod builds against stay under GPL-3.

## Native builds vs. the JVM jar

Native packages and the JVM jar load code mods (objects, characters, zones,
scenes and standalone games) through the same mod classloader, validation,
trust prompts and owner fault boundaries. Data-only music packs and reskins
also work in both. Native packages include the same first-party bundled mods,
including Time Attack; no separate Java installation is required.

Default native builds use pinned GraalVM Community 25.4.4.1.1+1.1 runtime class
loading with generated API and service retention. The VM feature is still
experimental: arbitrary reflection, generated code or third-party/JDK facilities
outside the retained contract need execution coverage. The
[native mod feasibility study](../architecture/research/2026-10-08-graalvm-native-mod-feasibility.md)
records the licence assessment, qualification limits and promotion decision.

1. [Music pack](quickstarts/music-pack.md)
2. [Data-only art reskin](quickstarts/reskin.md)
3. [Object or badnik](quickstarts/object.md)
4. [Sonic 2 zone](quickstarts/zone.md)
5. [Playable character](quickstarts/character.md)
6. [Standalone game](quickstarts/standalone.md)
7. [Scene or non-platformer game](guides/mod-scenes.md)
8. [Stageplay directing and cutscenes](../guide/creating/stageplay.md); to play a scene
   in your mod, see [use a scene in a mod](../guide/creating/stageplay-directing.md#use-a-scene-in-a-mod)

## What you can build

| Scope | Delivered path | Current boundary |
|---|---|---|
| Music | Data-only WAV/Ogg stock overrides | No MP3 or base-game streamed SFX overrides |
| Object reskin | Baked sheet over exact stock provider key | Preserve host palette indices and all consumed mapping frames; no playable-character reskin |
| Object/character | Owned factories, injected services, character specifications, landing/reset hooks, saves and rewind | JVM; no arbitrary static gameplay state or mod super form |
| Stock-level edits | Immutable decoded placement operations, including owned object bindings | JVM; preserves the stock load pipeline; live editor application is separate |
| New zone or campaign | Flat S1/S2 v1 and bounded S3K v2 adapters, ordered acts and owned runtime providers | S1/S3K validate stock-object compatibility; native stock zone scripts are not inherited |
| Scene/whole-screen game | Startup scene with input, audio, storage and canvas | JVM; ROM-backed scenes require the relevant supplied ROM |
| Original standalone game | No-ROM levels, characters, audio and progression | JVM; no standalone patch stacking, bonus/special-stage or roster UI |
| Shared UI and input | [Fonts, layout/focus and overlays](guides/creator-helpers.md); [named remappable actions](guides/action-bindings.md) | Logical pixels and explicit input edges; game rules and visual styling stay in the mod |
| State and testing | [Storage, captured state and service bundles](guides/creator-helpers.md); [production-backed Jupiter tests](testing.md) | Use complete session tests for gameplay rewind and renderer/capture checks for appearance |

See the detailed guides for narrower contracts; roadmap entries do not imply implementation.

## Follow-along guides

- [Second Strike](../../examples/second-strike/README.md) — a normal-campaign S3K
  difficulty prototype using native placement bindings, original-ring retention,
  cooperating enemy roles, finite native presets and earned bonus invitations.
  The linked matrix separates positioned checks from still-incomplete cold routes.

- [Flappy Tails, a tutorial in nine parts](guides/flappy-tails/README.md) — from nothing
  to a polished S3K game. Part 1 chooses between changing the native game (the
  `sample-flappy` gallery sample) and replacing the screen with a mod scene; the scene path
  builds [Flappy Tails](../../examples/flappy-tails/README.md) step by step, with buildable
  checkpoints: ROM art and backdrops, a port of Tails' flight routine, pillars cut from real
  levels, rules, a five-zone tour with title cards, screens and polish, tests, an autopilot
  and filming a promo.
- [ROM-art remix](guides/rom-art-remix.md) — source-first tour of the
  `sample-rom-art-remix` gallery sample: bounded Sonic 2 art, mapping, and DPLC
  intake, launch-memory materialization, decoded-pattern probes, and rewind.
- [Native-Tails Flappy](guides/native-tails-flappy.md) — build-along tour of the
  `sample-flappy` gallery sample: an anchorless S3K fresh-game destination, scoped
  Tails/input/HUD policies, fixed camera, and rewind-stable recycling pipes. Path A of the
  Flappy Tails tutorial.
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
- [Title entries and gameplay runs](guides/gameplay-runs.md) — add a master-title entry whose
  scene launches stock acts of Sonic 1, 2 or 3&K, observes and times them through a run host,
  draws ghosts and an overlay, and is resumed when each run ends. The bundled Time Attack mod
  (`racing/time-attack`) is built on it.
- [AI-generated art](guides/ai-art.md) — prompting, quantizing, and laying out
  original sprite/tile PNGs for `ggfmod convert art`, and swapping generated art into
  either build-along sample.

## Experimental projects

- [Mutator Lab](../../examples/example-mutators/README.md) — an eleven-effect
  catalogue for Sonic 1, Sonic 2 and Sonic 3 & Knuckles, with common widgets,
  explicit live/load scopes and rewind-separated preferences. See the
  [guide](guides/mutators.md) and current verification in the expansion plan.

- [Sonic3K Chaos Mode](../../examples/sonic3k-chaos-mode/README.md) — 105 selectable
  effects over native S3K, with a captured random scheduler, available/selected
  lists, interval and duration settings, and five optional ROM-art sidegames.
  Uses owner-scoped temporary mutators alongside the native game loop.

- [Flappy Tails](../../examples/flappy-tails/README.md) — Tails flaps through five S3K
  zones on the ROM's own flight routine, with pillars cut from each act's floor, real title
  cards, medals, records and Super Tails. An S3K mod scene and the finished game of the
  [tutorial](guides/flappy-tails/README.md); not one of the nine maintained gallery samples.
- [Starfall Frontier](../../examples/starfall-frontier/README.md) — a Terraria inspired
  solo mining and building adventure with a seeded tile world, three biomes,
  crafting stations, equipment, seven quests, three wardens and persistent saves.
  An original mod scene using the player's S3K character art and audio.

- [Three Islands](../../examples/three-islands/README.md) — a story-driven JRPG with fully
  turn-based battles across South Island, West Side Island and Angel Island: ten stock acts
  walked through `SceneRomArt.levelKit`, heroes in each game's own art, Dual and Triple
  Techs, multi-part ROM bosses and Sonic 1/2 zone themes through `ctx.music()`.

- [Robotnik Tower Defence](../../examples/robotnik-tower-defense/README.md) —
  Industrial Action: defend Robotnik's base door from 15 waves of unionised
  Flickies with six ROM-drawn badnik defenses, upgrades, repair and an emergency
  bomb. An S3K mod scene with mouse/pad controls and saved records; not one of the
  nine maintained gallery samples.

- [Sonic Survivors](../../examples/sonic-survivors/README.md) — a separate Sonic 2 survivors roguelike: walled arenas cut from each route act, ROM-art badniks with hitpoints, a bounce combo, level-up cards, zone bosses, a route with act choice and saved meta-progression, all through the existing code-patch API. Not part of the nine maintained gallery samples.

- [Eggman's Sky](../../examples/eggmans-sky/README.md) — a No Man's Sky-style S3K mod scene: Dr. Eggman explores planets remixed from every supplied ROM's zones with `SceneRomArt.levelKit`, a software-rendered space cockpit (streaming images), survival, scanning, crafting, Sonic as sentinels, Egg Stations and a galaxy map.
- [Slay the Robotnik](../../examples/slay-the-robotnik/README.md) — a Slay the Spire-style deck-building roguelike on Sonic 3 & Knuckles, built as a mod scene: three heroes, four acts of zone maps, ROM-drawn badniks and bosses, events, shops and relics.
- [Typing of the Hedgehog](../../examples/typing-of-the-hedgehog/README.md) — a Typing of the Dead-style mod over any supplied Sonic 1/2/3&K ROMs: four courses retain continuous stock-world rails, while 27 use short selected terrain sections or flight encounters with the stock world held. ROM badniks and bosses carry words, with a Story chapter map, Arcade, Boss Rush, Endless, Tails' Typing School and a generated voice cast. A `baseGame: any` jar using frame controllers, course checkpoints, typed text and stock-act hand-offs.
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
- [Level editor projects](../guide/creating/level-editor.md) — create, save, reopen, playtest, and export for all three hosts
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
- [Mod author and framework audit](../architecture/audits/2026-10-12-mod-author-framework-audit.md)
  — source-wide findings and proposed rewrite/API focal points, with verification limits.
- [GUI tooling evaluation](GUI_TOOLING_EVALUATION.md)

The nine sample sources are built by the default test suite. Treat them as
executable contracts rather than snippets copied out of context.

Advanced module authors can configure [session pacing, scripted rewind and display
controls](content-mods.md#advanced-module-session-controls).
