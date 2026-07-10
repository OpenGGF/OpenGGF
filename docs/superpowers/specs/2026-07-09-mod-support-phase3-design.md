# Mod Support Phase 3 — Characters + Standalone Games Design

**Date:** 2026-07-09
**Status:** Approved (brainstorming session)
**Parent:** `2026-07-09-mod-support-design.md` §8 Phase 3. Siblings: Phase 0/2 specs,
Phase 1/2 plans. **Depends on:** Phases 0, 1, and 2 all merged (characters ride the
patch/`ModContext` machinery; standalone games reuse Phase 2's `ModLevelDefinition`
zone pipeline wholesale). Recon date for all code claims: 2026-07-09.

## Goal

Two capabilities: **(A) mod-provided playable characters** — a mod ships a new
character (≈100-LOC sprite class + baked art + physics profile) selectable on the
launch-config screen of a base game; **(B) standalone games** — a mod that IS a
complete game (own `GameModule`, all assets from the jar, no ROM), appearing as a
new master-title entry.

---

## A. Mod-provided playable characters

Recon found nine identity walls; the persistence surfaces (`SelectedTeam`, config
`MAIN_CHARACTER_CODE`, `TraceMetadata`) are already string-keyed and open. The
design opens each wall with the smallest seam:

### A1. Character registry (wall 1 — the `createPlayable` if-chain)

`GameplayTeamBootstrap.createPlayable` hard-`new`s `Sonic`/`Tails`/`Knuckles` and
its respawn-strategy chain is string-hardcoded. New seam:
`PlayableCharacterRegistry` — `register(String code, CharacterDefinition def)`
where `CharacterDefinition` carries: sprite factory
(`(code, x, y) -> AbstractPlayableSprite`), display name, respawn-strategy
factory (default `SonicRespawnStrategy`), and the behavior archetype (A3).
Built-ins register at module init exactly as today's chain behaves (byte-identical
default). Mods register through `ModContext.registerCharacter(...)`, scoped to
their patch's base game like all Phase 2 content. `createPlayable` becomes a
registry lookup with the current fallback (unknown code → Sonic) retained and
logged.

### A2. `characterType()` virtual (wall 2 — the `instanceof` physics chain)

`AbstractPlayableSprite.resolvePhysicsProfile` derives charType via
`instanceof Tails/Knuckles` — a mod character silently gets Sonic physics. New
`public String characterType()` on `AbstractPlayableSprite`: the base
implementation preserves today's derivation (instanceof chain moved verbatim, so
untouched subclasses are byte-identical); `Sonic`/`Tails`/`Knuckles` override with
constants; a mod character overrides with its code. `resolvePhysicsProfile` and
`getInitProfile` consume `characterType()`. The mod's patch decorates
`PhysicsProvider.getProfile(...)` to serve the mod profile for its code (profiles
are pure records — mod-constructable by design).

### A3. Behavior archetype (wall 3 — the closed `PlayerCharacter` enum)

`PlayerCharacter` mirrors ROM `Player_mode` and is referenced in ~80 `src/main`
files (S3K level routing, cutscenes, sidekick carry logic). **Deliberate
decision: the enum is NOT widened.** A mod character's `CharacterDefinition`
declares `behavesLike: SONIC_ALONE | TAILS_ALONE | KNUCKLES` — which ROM
routing bucket it takes. `ActiveGameplayTeamResolver.resolvePlayerCharacter`
consults the registry for non-builtin codes and returns the declared archetype
(builtin codes: unchanged string chain). Rationale: the enum encodes ROM-faithful
routing; every playthrough must take *some* ROM path, and the archetype makes that
choice explicit and testable instead of the current silent Sonic fallback.
**Archetype validation (round-1 hazard):** `behavesLike: KNUCKLES` routes the
player onto Knuckles-exclusive gates/geometry that assume glide (MHZ start
boundary, LBZ gates, AIZ boss triggers — recon-cited) — the registry REFUSES a
`KNUCKLES` archetype unless the definition's ability is `GLIDE` (softlock
prevention, load-time error). **Sidekick interaction is pinned:** a mod
character's archetype is used exactly as declared and is never remapped by
sidekick presence (the sidekick-derived `SONIC_ALONE`/`SONIC_AND_TAILS` split
applies only to the builtin Sonic path; a mod main character with sidekicks
keeps its declared archetype). The docs tell creators what each archetype means
(level routes, carry eligibility, event branches).

### A4. Baked playable art (wall 4 — the art pipeline)

The art *type* exists (`PlayerSpriteArtProvider.loadPlayerSpriteArt(code)` →
`SpriteArtSet`, the full 9-component record: art tiles, mapping frames,
**per-frame DPLC tables**, palette index, base pattern index, frame delay,
bank size, animation profile, animation set) — but there is **no decoratable
module seam**: the interface is implemented by the per-game `Game` classes and
reached via `Level.getGame() instanceof PlayerSpriteArtProvider`.
**Engine change:** `LevelPlayableArtInitializer` consults the
`PlayableCharacterRegistry` FIRST for the resolved code — `CharacterDefinition`
carries an art supplier (`code -> SpriteArtSet`) — falling through to the `Game`
provider for builtin codes (byte-identical for stock play). The mod's supplier
reads a **baked playable-art container** — the Phase 2 baked sheet format
extended as **version 2** with the playable extras: per-frame DPLC tile-run
lists, animation profile/set data, bank size, base pattern index, frame delay.
`ggfmod convert art --playable` emits it; if the creator supplies no DPLC tables
the converter generates trivial full-frame runs and warns about VRAM cost (the
renderer streams frame-by-frame into a `DynamicPatternBank`; whole-sheet is not
how playables render). The registry likewise serves the character's 16-color
line-0 palette (`loadCharacterPalette` path). Sidekick use shares the
`SIDEKICK_BANKS` 0x8000 window — the converter's report states the bank cost.

### A5. Launch/select surfacing (walls 5–6)

- **Launch config:** Phase 0's `GamePatch.providedMainCharacters()` union
  (KiS2-plan Task 7 machinery) is exactly how a mod character enters
  `LaunchProfile.mainCharacterValues`/`sanitizedFor` — no new mechanism.
  `characterLabel` gains a registry-display-name fallback for non-builtin codes
  (today it would render nothing sensible).
- **Data-select:** ride the existing seams — `parseExtraTeams` for team entries
  and the `S3kCustomTeamPortraitComposer` path for portraits (it already handles
  non-canonical teams); mod characters get the custom-composed portrait. No
  presentation rework.

### A6. Moveset seam + explicit non-goals for characters

- **Moveset seam (parent §8 "moveset seams", delivered minimally):** ability
  logic is centralized in `PlayableSpriteMovement`, dispatched on
  `getSecondaryAbility()` at ~9 sites with no subclass hook — a novel ability
  cannot today be written "in the subclass". **Engine change:**
  `AbstractPlayableSprite` gains one overridable pre-dispatch hook
  (`protected boolean onAbilityActivate(...)`, called at the ability-button
  dispatch site in `PlayableSpriteMovement` before the enum switch; returning
  true consumes the press). Mod characters pick an existing
  `SecondaryAbility` (`INSTA_SHIELD`/`FLY`/`GLIDE`/`NONE`) OR implement a novel
  ability in the hook; the enum stays closed. Deeper moveset surgery (new
  ground moves, state-machine states) remains out of scope — declared as a
  narrowing of the parent's "moveset hooks" to this single seam.
- **Super forms are gated OFF for mod characters (engine change, not a free
  consequence):** `Sonic3kSuperStateController` eligibility is
  ring/emerald-based for ANY sprite — the `instanceof Tails` only selects the
  profile, with an else-branch of Super Sonic — so an ungated mod character
  WOULD transform into a Sonic-art super form. `CharacterDefinition` gains
  `supportsSuperForm` (false for mod characters in v1; the controller checks
  the registry gate before transformation, builtins unaffected). Revisit
  mod-supplied super art on demand.
- **HUD life icon:** recon left the art source unconfirmed (likely fixed HUD PLC
  tiles, not per-class). Implementation verifies; fallback is the base game's
  default icon. If it turns out per-character, a baked icon slot joins the v2
  playable container (same treatment as the data-select portrait below).
- **Data-select portrait fallback is explicit:** the custom-team composer maps
  unknown codes to the SONIC base frame, so a mod character's composed portrait
  is Sonic's — acceptable v1 behavior, stated so nobody reads A5 as a real
  portrait; a portrait slot joins the v2 container alongside the HUD icon if
  either proves per-character.
- **Tails-tail-style secondary sprites** (`initTailsTails` is
  `instanceof Tails`-gated): mod characters get none.

### Character acceptance

A sample mod character on S2 (distinct physics profile + baked art + `NONE`
ability, `behavesLike: SONIC_ALONE`): selectable on the S2 launch screen,
plays with its own physics (test asserts the profile resolved is the mod's, not
Sonic's), saves/loads a slot with its team string, survives a rewind round-trip,
and is force-disabled in trace mode with zero behavior deltas.

---

## B. Standalone games

Recon classified the whole `GameModule` surface: only four genuine ROM couplings
(`createGame(Rom)` + the `Game.getRom()` contract, `getObjectArtProvider`,
`GameAudioProfile.createSmpsLoader(Rom)` — nullable, and
`createTouchResponseTable(RomByteReader)` — satisfiable from jar bytes since
`RomByteReader` wraps a plain `byte[]`). The real work is five shared
unconditional `GameServices.rom()`/`getRom()` fetches (plus one gated consumer)
and the boot/identity plumbing.

### B1. `GameDataSource`

`interface GameDataSource { Rom romOrNull(); }` with two implementations:
`RomDataSource` (wraps the opened ROM — stock games, byte-identical) and
`ModAssetDataSource` (jar-backed; `romOrNull() == null`). Changes:

- `GameModule` gains `default Game createGame(GameDataSource source)
  { return createGame(source.romOrNull()); }` and
  `default TouchResponseTable createTouchResponseTable(GameDataSource source)
  { return createTouchResponseTable(RomByteReader.fromRom(source.romOrNull())); }`.
  Stock modules untouched; a standalone module overrides the source-based pair
  (touch table from jar bytes via `new RomByteReader(byte[])`).
- The **five** shared unconditional `getRom()` fetches route through the
  session's `GameDataSource`:
  `LevelManager.initGameModule` (~366: `parallaxManager.load`, `createGame`),
  `initAudio` (~378: `audioManager.setRom(source.romOrNull())` — the audio
  manager already null-tolerates via `createSmpsLoader` returning null),
  `initObjectManager` (~522), `initializeZoneFeatureProvider` (~682: fetch the
  rom lazily inside the provider-null guard), and
  `LevelWaterCoordinator.initialize` (~37: same lazy fix).
  `ParallaxManager.load(Rom)` is a provider-gated *consumer* of the first
  site's rom (nullable param), not a sixth fetch. For ROM sessions every value
  is identical. **One change is NOT mechanical:** the source-based
  `createTouchResponseTable` default delegates through
  `RomByteReader.fromRom(romOrNull())`, which NPEs on null — the default is
  stock-only by design and standalone modules MUST override it (jar bytes via
  `new RomByteReader(byte[])`); the Javadoc and a guard test say so.
- `Game.getRom()` is documented nullable for standalone `Game` implementations
  (their own code never calls it; shared callers go through the source).

### B2. Identity: `GameId.STANDALONE` + game codes

**Census correction (review round 1):** `GameId` has exactly **3**
compiler-forced switch expressions in `src/main` —
`Engine.createDataSelectSaveContext`, `Engine.loadDataSelectPayload`, and
`RuntimePresenceSnapshotProvider` (`AgentWorkflowTool`'s switches are over its
own local enum and are unaffected). Design: add **one** constant
`GameId.STANDALONE`, plus `GameModule.getGameCode()` whose default is the
existing `GameId.code()` (which those Engine switches currently duplicate —
they collapse to `module.getGameCode()` calls); standalone modules return their
mod id. `SaveManager` is already string-namespaced (`saves/<gameCode>/`), so
standalone saves isolate per mod id with no further work.
`RomManager.resolveRomForGame`'s silent `default -> SONIC_2_ROM` is fixed to
explicit `s1/s2/s3k` cases; unknown ids throw (standalone launches never call it).

### B3. Boot path: standalone launch without detection

`Engine.initializeGame()` is ROM-first (open → detect → module) with no
module-injection path. New sibling `Engine.initializeStandaloneGame(ModDescriptor
mod)`: skips `romManager`/`RomDetectionService` entirely, obtains the module the
mod registered via `ModContext.registerGameModule(...)` (the parent-spec §2
entry, first consumed here — **declared amendment of Phase 2 §A's "registration
outside a patch is invalid":** that rule scopes additive content; a
`type: standalone` mod registers a module, characters, and content directly,
with no `baseGame`), then joins the common
`openGameplaySession`/`initializeGameplayRuntime` path with a
`ModAssetDataSource`. `showStartupRomError` never engages (nothing to open).
**Patch stacking on standalone games is OUT OF SCOPE in Phase 3** —
`resolveModule` is skipped for standalone sessions. (Round-1 review showed it is
not free: with a single shared `GameId.STANDALONE`, patch base-game matching by
request gameId would apply one standalone's patches to all standalone games;
opening it needs code-string matching + manifest vocabulary — deferred to
demand, and noted against B2's open question.)
**`GameServices.rom()` semantics for standalone sessions are pinned:** the
`RomManager` stays installed and `getRom()` throws `IOException` exactly as it
does today for a missing file; Phase 3 includes an **audit task** confirming
every standalone-reachable ROM touch is IOException-guarded or provider-gated
(known guarded set from recon: `LevelPlayableArtInitializer.initSuperState`
(logs, harmless — silenced for null super controller), `ParallaxManager`'s lazy
camera-scroll load, `LazyMappingHolder`, `DefaultObjectServices.rom()`,
`GameLoop`'s title/level-select sites).

### B4. Master title: dynamic standalone entries

`MasterTitleScreen.GameEntry` stays a closed enum for the three stock games; the
screen additionally renders **catalog-provided standalone entries** (enabled
standalone-type mods) after the stock three. The coupling census this touches
(round-1 review): the positional `MENU_LABELS` array and every
`GameEntry.values().length`-sized state array must become
`stock + standalone`-sized; standalone entries have **no ROM preview art** —
they render a text tile (mod display name; mod-supplied title art is future
polish); selection routes through a **separate standalone launch path** direct
to `initializeStandaloneGame` (never `exitMasterTitleScreen(gameId)` →
`DEFAULT_ROM` → detection); the TAB `LaunchConfigPanel` is `GameEntry`-typed
and is a **no-op on standalone entries** (Phase 3 has no per-standalone launch
config). `LaunchProfileStore` keys stay per-stock-game. **Consequence stated
plainly:** a standalone game's `registerCharacter` roster is registered but
NOT user-selectable in Phase 3 (no launch panel for standalone entries) — the
game starts with its module's default character; roster selection UI is future
work.

### B5. Content: reuse Phase 2 wholesale

A standalone game's levels are `ModLevelDefinition`s loaded by the Phase 2
`ModZoneLoader` — as the module's PRIMARY zone path, not a patch overlay. This
reuse is real but **not free**; two engine changes are declared:

1. **Game-agnostic `ModLevel` (lifting Phase 2's S2-shaped work):** Phase 2's
   in-memory construction lives on `Sonic2Level` (whose constructors still
   carry ROM-address ints and an S2 module context). Phase 3 lifts the
   in-memory path into a game-agnostic `ModLevel` in `com.openggf.level`
   (`Pattern`/`Chunk`/`Block`/`Map` are already game-agnostic classes) that
   `ModZoneLoader` builds with **no Rom and no host module**; Phase 2's S2
   overload remains for patch zones (or becomes a thin adapter — plan-time
   choice).
2. **Standalone routing simplifications:** no synthetic-index gate (the
   standalone `Game.loadLevel` routes every index to the loader), no
   `ZoneProgressionPlan` redirects (linear order over its own registry is
   correct as-is), and none of Phase 2's S2 data-select/title-card extensions
   apply (standalone profiles are the module's own or absent).

Its object registry serves mod objects with the full 0x00–0xFF id space (no
stock census to avoid), art is baked sheets, and physics is a literal
`PhysicsProvider`/`GameRules` record set (already pure).

**Streamed audio needs a real route (round-1 BLOCKER fix — engine change):**
with `createSmpsLoader -> null`, `AudioManager.playMusic` falls to the
`FALLBACK_WAV` branch, which never consults the Phase 1 streamed layer — that
hook lives in `AbstractSmpsAudioBackend.playSmps`, unreachable without SMPS
data; and `ModMusicResolver` is built from a base game's `audioOverrides`,
which a standalone game doesn't have. Two additions: (1) the `FALLBACK_WAV`
branch first attempts the streamed route via the backend's (Phase 1, public)
`tryStartStreamedMusic(musicId)` — exposed through an `AudioBackend` default so
`AudioManager` can call it; (2) `ModAudioIntegration` gains
`buildResolverForStandalone(...)` mapping the game's OWN track ids from its
audio manifest (not overrides of base ids). **(3) Standalone SFX get a real
route too:** the existing `FALLBACK_NAME` path is a hardcoded four-entry
name→working-directory-wav map — effectively no SFX for a single-jar game — so
the standalone resolver also maps SFX ids to short decoded PCM buffers (same
Phase 1 decoders), mixed through a small bounded-polyphony one-shot pool in the
streamed backend. This deliberately lifts a slice of Phase 1's deferred
streamed-SFX work, scoped to standalone games (base-game SFX *overrides* remain
deferred as before).

Providers beyond the minimum default to no-op per the recon classification table
(title cards, special stages, data select, ending, debug — all `NoOp`/null
defaults exist today).

### B6. Standalone non-goals

Special/bonus stages, cross-game donation from/to standalone games, standalone
trace recording, per-standalone launch-config persistence, and data-select
presentation (a standalone game may return the null host profile and simply
start at its first zone; opting into data-select uses the minimal
`DataSelectHostProfile` surface recon identified).

### Standalone acceptance

A sample standalone game (one zone, one badnik, streamed music, literal physics):
appears on the master title with the three stock entries, launches with **no ROM
files present at all** (the acceptance test runs with an empty working directory),
plays its zone, saves/loads a slot under `saves/<modid>/`, and its enablement has
zero effect on stock-game behavior (trace spot sweep with the mod enabled but a
stock game launched — bit-identical).

---

## Verification strategy

Unit tests per seam (registry lookup + fallback, `characterType()` byte-identical
derivation for the builtins, archetype resolution, `GameDataSource` null paths,
gameCode switches); the two acceptance samples above as headless integration
tests; full suite + S3K must-keep-green + trace spot sweeps (mods disabled AND
mods enabled/stock-launched) at each workstream boundary.

## Open questions

- **`GameId.STANDALONE` singleton vs per-mod:** one shared constant with
  `getGameCode()` disambiguation is the plan. One consumer IS known to need
  finer distinction — the patch registry's base-game matching — which is why
  patch stacking on standalone games is deferred (B3); if it is ever opened,
  matching moves to code strings.
- **Playable-container animation format:** the `SpriteArtSet` animation
  profile/set structure needs its serialized form pinned at plan time (it is
  engine-internal today; recon captured the 9-component list, not the byte
  shape).
