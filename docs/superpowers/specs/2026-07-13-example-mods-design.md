# Example mod pair + ROM-art mod API — design

Date: 2026-07-13
Status: approved (brainstorm), pending implementation plans

## Goal

Ship two fun, follow-along example mods that people can review, rebuild from a
guide, and play — plus the one engine capability they need. Both mods join the
maintained gallery so CI keeps them honest.

1. **Mod A — "sample-flappy"**: a one-button flappy minigame built as an
   additive Sonic 2 patch, demonstrating the *remix* pipeline: importing art
   from the user's own ROM (Tails' flying frames) to make something entirely
   new.
2. **Mod B — "sample-platformer"**: a no-ROM standalone platformer act built
   entirely from original/AI-generated assets, demonstrating the
   *from-scratch* pipeline.

## Decisions (from brainstorm)

- **Purpose**: tutorial and showcase equally — each mod must be genuinely fun
  *and* fully narratable in a build-along guide.
- **Two mods, two pipelines**: flappy = ROM-asset remix (ROM owners), platformer
  = fresh assets (anyone, no ROM).
- **ROM art path**: add a supported engine API first (imperative `@ModApi`
  helper), rather than teaching raw `ObjectServices.rom()` parsing or dropping
  the ROM-import story.
- **Packaging**: gallery samples #6/#7 under `src/test/resources/mods/`,
  CI-built and validated like the existing five; guides in `docs/modding/`.
  No prebuilt jar distribution (trust is per-jar-hash; flappy art needs the
  user's ROM anyway).
- **Art authorship**: Claude authors original pixel art via deterministic
  generation scripts (PNGs + generator checked in); the guide gains an AI-art
  chapter so readers can swap in their own generated art.

## Feasibility findings that shaped the design

- There is **no existing mod-facing ROM-art mechanism**: `LoadSource` is sealed
  to `RomAddress` (base-game only) and `ModAsset` (jar, forced uncompressed);
  manifest `artOverrides` replaces stock art with mod assets, never the
  reverse; `ModLevelDefinition` has no ROM-address field. This wall was
  deliberate (mod content must load without ROM fallback), so the new API is an
  explicit, bounded exception for *remix* mods targeting a ROM game.
- `@ModApi` `PlayableEntity` already exposes everything flappy control needs:
  `setXSpeed`/`setYSpeed`, `move`, position setters, `applyHurtOrDeath`,
  object-control flags; `ObjectServices.playerQuery()` reaches the main player.
- Mod zones are **S2-only** (`ModContext.registerZone` rejects other games), so
  the flappy art source is `s2.gen`.
- Parked backlog items respected by this design: no playable-art donation, no
  Tails appendage sprites, no mod objects in stock zones, no deeper moveset
  surgery (one ability hook only), TMX collision limited to
  `NO_COLLISION`/`ALL_SOLID`.
- Current published Mod API is **2.0.0**; both samples declare
  `engineApiRange: ">=2.0.0 <3.0.0"`.

## Part 1 — Engine: ROM-art intake API (prerequisite for Mod A)

A registration-time `@ModApi` capability reached through `ModContext` —
working name `ModRomArtIntake`.

- **Input**: ROM art address + compression (Nemesis/Kosinski/uncompressed),
  S2 mapping address, optional DPLC address, palette source (ROM palette
  address or explicit line), optional frame selection.
- **Output**: art registered under the calling mod's **namespaced art key**,
  consumable by mod objects via the normal `getRenderer(artKey)` path.
- **Implementation**: thin adapter over the engine's existing
  `S2SpriteDataLoader` / decompression pipeline — no new parsers.
- **Boundaries**:
  - Available only to mods whose `baseGame` has a ROM (today: additive S2
    patches, where the ROM is guaranteed present at runtime).
  - A standalone module calling it fails validation with a clear finding.
  - ROM-derived bytes live only in memory — never baked to disk. The mod jar
    ships zero copyrighted bytes; art materializes from the user's ROM at load.
- **Plan-level open item**: confirm whether `@ModApi` exposes the player's
  jump-press input to object code; if not, add that one accessor here.

## Part 2 — Mod A: flappy remix (gallery sample #6)

Additive Sonic 2 patch with one custom zone inserted after a stock anchor.

- **Scroll**: `ZoneEventFactory` drives forced auto-scroll.
- **Control**: a controller object puts the main player under object control
  and runs flappy physics — constant gravity, flap impulse on jump press,
  `applyHurtOrDeath` on obstacle contact.
- **Visuals**: the player sprite is visually suppressed; a companion mod object
  anchored to the player renders Tails' flying frames loaded via
  `ModRomArtIntake`. (Deliberately avoids the parked playable-art-donation
  territory.)
- **Obstacles**: pipes are original generated art, placed as objects in the
  level definition (rewind-safe, deterministic — no runtime RNG spawning).
- **Score**: rings incremented per pipe cleared; best score persists through
  the mod save surface.
- **Guide**: `docs/modding/guides/flappy-remix.md` — narrated build-along
  including the ROM-art API chapter.

## Part 3 — Mod B: standalone platformer (gallery sample #7)

No-ROM standalone module on the proven Phase-3 path
(`sample-standalone-src` precedent), designed as a real playable slice:

- **One act**, TMX-authored level (`ALL_SOLID` collision).
- One original character with distinct `PhysicsProfile` plus one
  `onAbilityActivate` hook: a double-jump.
- One badnik, one gimmick object.
- Streamed OGG music + WAV SFX, master-title entry, save/Continue.
- All art original, produced by deterministic generation scripts checked in
  alongside the PNGs.
- **Guide**: `docs/modding/guides/standalone-platformer.md`.

## Part 4 — Guides

Three additions to the creator handbook, linked from `docs/modding/index.md`
and `docs/modding/samples/index.md`:

1. `guides/flappy-remix.md` — build-along: init → ROM-art intake → zone/level →
   control code → package → trust → play.
2. `guides/standalone-platformer.md` — build-along: init → art generation →
   TMX level → character/badnik/gimmick → audio → package → play.
3. `guides/ai-art.md` (own page): generating character/tile PNGs
   with an image model, 16-color palette quantization, sheet layout + YAML
   descriptor, VRAM/bank cost awareness.

Existing quickstarts stay untouched — they remain the terse reference; these
are the tutorials. Mod ids/titles stay IP-neutral (`sample-flappy`); guides may
name "Tails' flying art" descriptively.

## Testing & CI

- Gallery CI builds/validates both samples through real `ggfmod package`.
- Flappy's ROM-art runtime tests gate on `-Ds2.rom.path` (skip when absent,
  like `TestRomLogic`).
- `ModRomArtIntake` unit tests: art/mapping/DPLC load against `s2.gen`;
  standalone-rejection validation finding.
- Platformer integration test modeled on
  `TestPhase3StandaloneSampleIntegration`.
- Samples index updated to list #6/#7 as executable contracts.

## Sequencing

Three sub-projects, each with its own implementation plan:

1. Engine ROM-art intake API (blocks Mod A).
2. Mod A + guide (after 1).
3. Mod B + guide + AI-art chapter (independent; may run in parallel with 1–2).

## Out of scope

- Prebuilt/downloadable jar releases and any release workflow.
- Playable-art donation, appendage sprites, moveset surgery, mod objects in
  stock zones, S1/S3K additive zones, richer TMX collision (all parked in
  `docs/modding/BACKLOG.md`).
- Declarative manifest `romArt:` entries (considered, rejected in favor of the
  imperative helper; revisit only if data-only ROM-art reskins become a real
  demand).
