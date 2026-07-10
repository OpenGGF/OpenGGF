# Mod Support Phase 3 (Characters + Standalone Games) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mod-provided playable characters selectable on a base game's launch screen, and standalone games (own `GameModule`, all-jar assets, no ROM) on the master title.

**Architecture:** Workstream A opens the nine character-identity walls the Phase 3 spec enumerates (registry, `characterType()`, archetypes, registry-first art, moveset hook, super gate, surfacing). Workstream B builds `GameDataSource`, `GameId.STANDALONE`, the detection-free boot path, dynamic master-title entries, the game-agnostic `ModLevel` lift, and the standalone streamed music/SFX routes. Two acceptance samples close each workstream.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase3-design.md` (authoritative; its recon anchors were verified twice in review), parent + Phase 0/2 specs, Phase 1/2 plans.

## CONTINGENCY PREAMBLE

Authored 2026-07-09 with **Phases 0, 1, AND 2 unlanded**. Markers **[P0]/[P1]/[P2]** flag tasks consuming those phases' interfaces; each such task's first step is *re-verify the landed interface* and adapt mechanically; structural divergence → STOP and update this plan. Do not begin until Phases 0–2 have merged. This plan is contract-and-anchor throughout (Phase 2 plan's calibration): recon line anchors are cited, complete code only where a piece is pure-new and dependency-free.

## Global Constraints

- JUnit 5 only; never `git add -A`; no new singletons; **no new Maven dependencies** (Phase 2's ASM is the last).
- Commit trailers per repo policy; intermediate `feat` commits touching `src/main/` use `Changelog: n/a: covered by final phase-3 changelog entry in this branch`.
- **Branch:** `feature/ai-mod-support-phase3` off `develop`.
- **Byte-identical guardrail:** every wall-opening task in workstream A ends by running the physics/character suites (`TestPhysicsProfile`, `TestPhysicsProfileRegression`, `TestCollisionModel`, `TestSidekickCpuFollowParity`) plus a trace spot sweep — the seams must be no-ops for stock play.
- **ArchUnit:** expect at minimum the edges the new packages introduce; add exactly what the ratchet's failure message names, citing this plan.
- Regression gates at workstream boundaries: full suite, S3K must-keep-green, and trace spot sweeps (s1_ghz1/s2_ehz1/s3k_aiz1) **in BOTH variants** — mods disabled, AND the phase's sample mod enabled with a stock game launched — bit-identical each (spec §Verification). Log sweeps in `docs/TRACE_FRONTIER_LOG.md`.

---

## Workstream A — mod characters

### Task A1: `PlayableCharacterRegistry` + `CharacterDefinition` **[P2]**

**Files:**
- Create: `src/main/java/com/openggf/sprites/playable/PlayableCharacterRegistry.java`, `CharacterDefinition.java`
- Modify: `src/main/java/com/openggf/game/session/GameplayTeamBootstrap.java` (`createPlayable` ~110-121, respawn chain ~81-85)
- Test: `src/test/java/com/openggf/sprites/playable/TestPlayableCharacterRegistry.java`

**Contract (spec §A1):** `CharacterDefinition(code, displayName, spriteFactory, respawnStrategyFactory = SonicRespawnStrategy, behavesLike archetype, SecondaryAbility, supportsSuperForm = false for mods, artSupplier)`. Registry: builtins registered at module init reproducing today's chain byte-identically (test: for codes sonic/tails/knuckles + unknown, the registry-built sprite class and respawn strategy equal the old chain's output); **archetype validation:** `behavesLike == KNUCKLES` requires `ability == GLIDE` → registration refused with a load-time error (spec §A3 softlock rule). `createPlayable`/the respawn chain become registry lookups (unknown → Sonic fallback, logged). Mods register via `ModContext.registerCharacter(...)` [P2 — extend `ModContext`], scoped to their patch's base game.

- [ ] Steps: re-verify Phase 2 `ModContext` → failing tests (builtin equivalence incl. respawn strategies; unknown fallback; KNUCKLES/GLIDE refusal; mod registration round-trip) → implement → PASS → byte-identical guardrail suites → commit (`feat: playable character registry over the hardcoded-three factory`).

---

### Task A2: `characterType()` virtual + physics resolution

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` (`resolvePhysicsProfile` ~3625-3637, `getInitProfile` use ~3658), `Sonic.java`, `Tails.java`, `Knuckles.java`
- Test: `src/test/java/com/openggf/sprites/playable/TestCharacterTypeResolution.java`

**Contract (spec §A2):** `public String characterType()` — base implementation is today's instanceof chain moved verbatim (untouched subclasses byte-identical; test asserts sonic/tails/knuckles instances return the same charType the old chain produced); the three builtins override with constants; `resolvePhysicsProfile`/`getInitProfile` consume the method. A stub mod subclass overriding `characterType() -> "modchar"` resolves `provider.getProfile("modchar")` (test with a stub provider).

- [ ] Steps: failing tests → implement → PASS → guardrail suites + trace sweep → commit (`feat: characterType virtual replaces instanceof physics derivation`).

---

### Task A3: Archetype resolution in `ActiveGameplayTeamResolver`

**Files:**
- Modify: `src/main/java/com/openggf/game/session/ActiveGameplayTeamResolver.java` (`resolvePlayerCharacter` ~45-59)
- Test: extend its existing tests

**Contract (spec §A3):** non-builtin codes consult the registry's declared archetype; builtin codes keep the exact string chain (incl. the sidekick-derived SONIC_ALONE/SONIC_AND_TAILS split, which applies ONLY to builtin sonic); a mod character's archetype is never remapped by sidekick presence. Unknown code with no registration → today's Sonic bucket (logged).

- [ ] Steps: failing tests (three builtin paths unchanged; mod code → declared archetype; sidekick non-remap) → implement → PASS → **byte-identical guardrail suites + trace spot sweep** (this task touches character routing — wall 3) → commit (`feat: behavior archetype resolution for mod characters`).

---

### Task A4: Registry-first playable art + v2 playable container **[P2]**

**Files:**
- Modify: `src/main/java/com/openggf/level/LevelPlayableArtInitializer.java` (~63-128: consult `PlayableCharacterRegistry` art supplier before the `Game` provider)
- Create: playable-container v2 reader (extend Phase 2's `BakedSheetReader` format — version 2 adds per-frame DPLC tile-run lists, animation profile/set, bankSize, basePatternIndex, frameDelay), `src/main/java/com/openggf/tools/modsdk/` writer + `convert art --playable`
- Test: container round-trip; initializer registry-first/game-fallback

**Contract (spec §A4):** registry-first lookup for the resolved code, `Game`-provider fallback for builtins (byte-identical stock); the v2 container materializes a full 9-component `SpriteArtSet`; converter generates trivial full-frame DPLC runs with a VRAM-cost warning when tables are absent; palette via the registry (line-0, 16 colors); sidekick bank cost reported. **Two verifications land at Step 1, BEFORE the container schema is pinned (spec §A6 obligations):** (a) the serialized animation profile/set form (read `SpriteArtSet`'s component types); (b) **the HUD life-icon art source** — trace where the life-counter icon comes from (likely fixed HUD PLC tiles); if it proves per-character, the v2 container gains an optional icon slot AND the data-select portrait slot in the same schema cut (the spec pairs them). Both decisions are recorded in the container format doc; re-cutting the schema after this task is not acceptable.

- [ ] Steps: re-verify Phase 2 container format + read `SpriteArtSet`/initializer + the HUD-icon verification above → failing tests → implement → PASS → **byte-identical guardrail suites + trace spot sweep** (this task touches `LevelPlayableArtInitializer` — wall 4) → commit (`feat: registry-first playable art with v2 baked container`).

---

### Task A5: Moveset hook + super-form gate

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` (hook), `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java` (ability dispatch site), `src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java` (gate; read ~126-147 first)
- Test: hook consumption + gate tests

**Contract (spec §A6):** one `protected boolean onAbilityActivate(...)` pre-dispatch hook (default false → today's enum dispatch runs; returning true consumes the press) — stock behavior byte-identical; `supportsSuperForm` checked at the transformation-eligibility site (builtins true, registry-resolved for mods; a false gate refuses transformation, rings/emeralds untouched).

- [ ] Steps: read the ~9 dispatch sites, pick the activation site → failing tests → implement → PASS → guardrail suites (super-state tests included) → commit (`feat: ability pre-dispatch hook and super-form registry gate`).

---

### Task A6: Surfacing (launch label fallback, data-select docs) + character acceptance sample **[P0][P2]**

**Files:**
- Modify: `src/main/java/com/openggf/game/launch/LaunchProfile.java` (`characterLabel` ~312-320)
- Create: the sample mod character source under `src/test/resources/mods/sample-character-src/` + its headless acceptance test

- [ ] **Step 1:** `LaunchProfile.characterLabel` (~312-320) gains a registry-display-name fallback for non-builtin codes; mod characters reach the launch lists via Phase 0's `providedMainCharacters()` union [P0 — re-verify] — test with a stub patch providing "modchar".
- [ ] **Step 2 (acceptance, spec §Character acceptance):** sample mod character on S2 (`behavesLike: SONIC_ALONE`, ability NONE, distinct profile, baked v2 art): headless test asserts launch-list presence, resolved profile is the mod's (not Sonic's), save-slot team round-trip, rewind snapshot/restore of the sprite, and trace-mode force-disable with zero deltas. Portrait renders via the documented Sonic-base composed fallback (assert no crash, not appearance).
- [ ] **Step 3:** guardrail suites + sweeps → commit (`feat: mod character surfacing and acceptance sample`).

---

## Workstream B — standalone games

### Task B1: `GameDataSource` + the five shared fetches **[P0]**

**Files:**
- Create: `src/main/java/com/openggf/data/GameDataSource.java` (+ `RomDataSource`, `ModAssetDataSource`)
- Modify: `GameModule.java` (two source-based defaults), `data/Game.java` (**`getRom()` Javadoc: documented nullable for standalone implementations** — spec §B1 last bullet), `level/LevelManager.java` (~366/~378/~522/~682), `level/LevelWaterCoordinator.java` (~37), `level/ParallaxManager.java` (nullable param)
- Test: null-path tests per site + a ROM-session equivalence test

**Contract (spec §B1):** `romOrNull()`; source-based `createGame`/`createTouchResponseTable` defaults delegate to the Rom forms (the touch-table default is **stock-only** — Javadoc + a guard test assert standalone modules override it); the five fetches route through the session source with lazy/guarded access; ROM sessions byte-identical (equivalence test: an S2 headless load through `RomDataSource` produces the same level/table state as before the change — assert via existing zone-loading tests passing unmodified).

- [ ] Steps: re-verify Phase 0's loader null-Rom behavior [P0] → read the five sites → failing tests → implement → PASS → full zone-loading + S3K gates → commit (`feat: GameDataSource abstraction over shared ROM fetches`).

---

### Task B2: `GameId.STANDALONE` + `getGameCode()`

**Files:**
- Modify: `game/GameId.java`, `game/GameModule.java`, `Engine.java` (the two gameCode switches ~1069/~1098), `integration/presence/RuntimePresenceSnapshotProvider.java` (~87), `data/RomManager.java` (`resolveRomForGame` ~177-184)
- Test: gameCode derivation + resolveRomForGame explicit-cases tests

**Contract (spec §B2):** one new constant; `getGameCode()` default = `GameId.code()`; the exactly-3 compiler-forced switches collapse to `module.getGameCode()`; `resolveRomForGame` becomes explicit s1/s2/s3k with unknown → throw. `SaveManager` needs nothing (string-namespaced).

- [ ] Steps: add the constant, follow the compiler → failing tests → implement → PASS → commit (`feat: GameId.STANDALONE and module game codes`).

---

### Task B3: Standalone boot path + ROM-touch audit **[P1][P2]**

**Files:**
- Modify: `Engine.java` (new `initializeStandaloneGame`), `mods/code/ModContext` (+`registerGameModule` — the parent-spec §2 entry, declared Phase 2 §A amendment for `type: standalone`)
- Test: headless standalone boot test; audit checklist

**Contract (spec §B3):** skips `romManager`/detection; module from the mod's registration; **`resolveModule` skipped** (patch stacking on standalone deferred); joins `openGameplaySession` with `ModAssetDataSource`; `GameServices.rom()` semantics unchanged (IOException as today). **Test seam pinned:** the headless test exercises the `SessionManager.openGameplaySession` + `ModAssetDataSource` join point directly (the repo's headless idiom); `Engine.initializeStandaloneGame` stays a thin GLFW-side wrapper over that seam and is NOT driven headless (windowless `Engine` boot is not a repo idiom) — its wiring is exercised by B6's manual/visual path. **Audit step:** walk the spec's guarded-site list (initSuperState — silence the log for null super controller, ParallaxManager lazy load, LazyMappingHolder, DefaultObjectServices.rom(), GameLoop title/level-select) confirming IOException-guard or gating; record findings in the task's commit body; fix any unguarded site found.

- [ ] Steps: re-verify Phase 1 catalog + Phase 2 ModContext → failing boot test → implement + audit → PASS → commit (`feat: detection-free standalone game boot path`).

---

### Task B4: Game-agnostic `ModLevel` lift **[P2]**

**Files:**
- Create: `src/main/java/com/openggf/level/ModLevel.java`
- Modify: Phase 2's `ModZoneLoader` (build `ModLevel` when no host module)
- Test: rom-free construction round-trip from fixture assets

**Contract (spec §B5.1):** lift Phase 2's in-memory construction into `ModLevel` (no Rom, no host module; `Pattern`/`Chunk`/`Block`/`Map` are already game-agnostic); Phase 2's S2 overload stays for patch zones (plan-time choice honored: keep both, adapter later if duplication hurts). Standalone `Game.loadLevel` routes every index to the loader; no synthetic gate, no progression plan, no data-select extensions.

- [ ] Steps: re-verify the landed Phase 2 overload → failing tests → implement by lifting, not duplicating (extract shared decode into package-visible helpers if needed) → PASS → HTZ + S3K gates (the S2 overload untouched) → commit (`feat: game-agnostic ModLevel for standalone zones`).

---

### Task B5: Standalone streamed music + one-shot SFX **[P1]**

**Files:**
- Modify: `audio/AudioManager.java` (FALLBACK_WAV branch ~846-859), `audio/AudioBackend.java` (default `tryStartStreamedMusic` exposure), `audio/AbstractSmpsAudioBackend.java` (one-shot SFX pool), `mods/ModAudioIntegration.java` (`buildResolverForStandalone`)
- Test: null-loader music route; SFX pool polyphony/suppression

**Contract (spec §B5.2-3):** the FALLBACK_WAV branch first attempts `backend.tryStartStreamedMusic(musicId)` (Phase 1's public backend method, exposed through an `AudioBackend` default returning false); `buildResolverForStandalone` maps the game's own track ids from its audio manifest; SFX ids map to short decoded PCM buffers played through a bounded-polyphony one-shot pool mixed beside the streamed music (suppressed during rewind like Phase 1 streams; base-game SFX overrides remain deferred). Stock behavior untouched when no resolver present (EMPTY → default false path = today's WAV fallback).

- [ ] Steps: re-verify Phase 1's landed backend surface → failing tests → implement → PASS → audio suites → commit (`feat: standalone streamed music route and one-shot SFX pool`).

---

### Task B6: Master-title standalone entries + standalone acceptance sample **[P1][P2]**

**Files:**
- Modify: `src/main/java/com/openggf/game/MasterTitleScreen.java` (array sites ~67/~143/~164-169/~553-570/~773-781), `src/main/java/com/openggf/Engine.java` (standalone exit wiring), plus the master-title construction sites that pass the catalog (Phase 1 already threads `ModCatalog` into `MasterTitleScreen` — re-verify and reuse that constructor path [P1])
- Test: screen-logic tests per the editor/manager screen convention; the acceptance test below

- [ ] **Step 1:** `MasterTitleScreen` renders catalog-provided standalone entries after the stock three per the spec's coupling census: widen `MENU_LABELS`/`values().length` state arrays to `stock + standalone`, text-tile preview (mod display name), TAB no-op on standalone entries. **Exit wiring named:** a standalone selection sets a pending-standalone result the boot controller consumes → `Engine.initializeStandaloneGame(descriptor)` — a new exit callback beside `exitMasterTitleScreen(gameId)`, never the `DEFAULT_ROM`/detection route. The catalog reaches the screen through Phase 1's existing constructor wiring.
- [ ] **Step 2 (acceptance, spec §Standalone acceptance):** sample standalone game (one zone via `ModLevelDefinition`, one badnik, streamed music, literal physics, touch table from jar bytes): headless test boots it **with zero ROM files in the working directory**, plays its zone (level loads, badnik spawns), saves/loads under `saves/<modid>/`, and a stock-game trace sweep with the mod enabled is bit-identical.
- [ ] **Step 3:** full gates → commit (`feat: master-title standalone entries and acceptance sample`).

---

### Task B7: Docs + changelog

- [ ] `docs/modding/characters.md` + `docs/modding/standalone-games.md` (creator guides incl. archetype semantics/validation, v2 container, roster-not-selectable-in-standalone note); CHANGELOG one phase-3 entry; CLAUDE.md/AGENTS.md pointers; final commit `Changelog: updated`, `Agent-Docs: updated`.

---

## Execution notes

- A and B are independent; within each, order as written (A1→A6, B1→B7). The acceptance samples are the workstream gates.
- Re-verify markers are mandatory; this plan predates all three dependency phases.
- Merge flow: `superpowers:finishing-a-development-branch`; README release-log note on merge.
