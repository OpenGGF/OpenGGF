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
  `S2SpriteDataLoader` / decompression pipeline — no new parsers. Tails'
  flying frames are proven loadable this way (`Sonic2PlayerArt.loadTails()`
  already parses the art/mapping/DPLC triple at
  `ART_UNC_TAILS_ADDR`/`MAP_UNC_TAILS_ADDR`/`MAP_R_UNC_TAILS_ADDR`); DPLC →
  static-object-sheet flattening has engine precedents (`AizIntroArtLoader`,
  `IczSnowboardArtLoader`). The separate tails-appendage art stays excluded
  (parked in backlog) — the bird uses body frames only.
- **Staging vs materialization**: `ModContext` registrations are staged and
  frozen with no ROM access, so the intake registers a *request* (addresses,
  formats, target art key) at registration time; the engine materializes it at
  gameplay launch when the ROM is available, then exposes the result under the
  namespaced art key.
- **Validation & faults**:
  - Requests are structurally validated at registration (addresses within ROM
    bounds, known compression/mapping formats); violations are owner-attributed
    findings.
  - Materialization applies immutable limits in the spirit of
    `ModInputLimits` — a decompressed-size cap and a tile/frame-count cap — so
    a garbage address cannot allocate unboundedly.
  - Materialization failures (decompression error, malformed mapping/DPLC
    tables, cap exceeded) are owner-attributed and follow the phase-0 fault
    contract for creator-apply failures at launch: abort launch with a
    diagnostic naming the owner, the offending address, and the cause.
- **Boundaries**:
  - Available only to mods whose `baseGame` has a ROM (today: additive S2
    patches, where the ROM is guaranteed present at runtime).
  - A standalone module calling it fails validation with a clear finding.
  - ROM-derived bytes live only in memory — never baked to disk. The mod jar
    ships zero copyrighted bytes; art materializes from the user's ROM at load.
- **No new input accessor needed**: `AbstractPlayableSprite` is itself
  `@ModApi` and exposes `isJumpPressed()`/`isJumpJustPressed()` plus the full
  object-control surface (`setObjectControlled`, `applyObjectControlState`,
  `releaseFromObjectControl`, `setHidden`); `ObjectPlayerQuery.mainPlayerOrNull()`
  reaches it within the published surface.

## Part 2 — Mod A: flappy remix (gallery sample #6)

Additive Sonic 2 patch with one custom zone inserted after a stock anchor.

- **Scroll**: the controller object drives forced auto-scroll through the
  already-published `@ModApi` camera surface —
  `services().camera().requestForcedScroll(...)` / `setScrollLocked` / bounds
  setters. No `ZoneEventFactory` involvement (it stays `null` like the
  existing samples); no new event-provider plumbing is in scope.
- **Control**: a controller object puts the main player under object control
  (`setObjectControlled`) and runs flappy physics — constant gravity, flap
  impulse on `isJumpJustPressed()`, `applyHurtOrDeath` on obstacle contact.
- **Visuals**: the player sprite is hidden via `setHidden(true)`; a companion
  mod object anchored to the player's centre renders Tails' flying body frames
  loaded via `ModRomArtIntake`. (Deliberately avoids the parked
  playable-art-donation territory.)
- **Obstacles**: pipes are original generated art, placed as objects in the
  level definition (rewind-safe, deterministic — no runtime RNG spawning).
- **Score**: rings incremented per pipe cleared via
  `services().levelGamestate().addRings(int)`. Best score is
  **session-lifetime only**, held by the controller object as rewind-covered
  state. Durable high-score persistence is out of scope: no creator-facing
  key/value save surface exists for additive patches (`SaveSnapshotProvider`
  is capture-only), and adding one is a backlog candidate, not part of this
  work.
- **Rewind**: controller, bird companion, and pipe objects implement
  `RewindRecreatable` with capturable instance fields, per the content-mods
  contract; player-side object-control/hidden bits are already captured by
  `PlayerRewindExtra`.
- **Guide**: `docs/modding/guides/flappy-remix.md` — narrated build-along
  including the ROM-art API chapter and the rewind-coverage checklist step.

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
- **Rewind**: character (across construction and ability use, per
  `characters.md`), badnik, and gimmick object all demonstrate rewind coverage
  (`RewindRecreatable` + capturable fields).
- **Guide**: `docs/modding/guides/standalone-platformer.md` — includes the
  rewind-coverage checklist step.

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
  `TestSampleModsPackage` currently asserts *exactly five* source mods and
  `docs/modding/samples/index.md` says "exactly these five" — both move to
  seven. (Note: the music-pack sample lives under `docs/modding/samples/`, not
  `src/test/resources/mods/`; the two new samples go under
  `src/test/resources/mods/` like the other code-bearing four.)
- Flappy's ROM-art runtime tests gate on a **new** `-Ds2.rom.path` property
  (modeled on the existing `s3k.rom.path` precedent; skip when absent, like
  `TestRomLogic`).
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
- Creator-facing durable key/value persistence for additive patches (needed
  for a durable flappy high score; backlog candidate with its own
  bounds/fault story).
- New `ZoneEventFactory`/`LevelEventProvider` service plumbing (auto-scroll is
  achieved through the existing `@ModApi` camera surface from object code).
