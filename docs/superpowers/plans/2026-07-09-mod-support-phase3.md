# Mod Support Phase 3 (Characters + Standalone Games) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mod-provided playable characters selectable on a base game's launch screen, and standalone games (own `GameModule`, all-jar assets, no ROM) on the master title.

**Architecture:** Shared Workstream 0 establishes module/session ownership, API versioning, and format fixtures. Workstream A then opens the character-identity walls; Workstream B builds detection-free standalone boot, unified title entries, game-agnostic levels, and namespaced audio. A and B parallelize only after Workstream 0.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase3-design.md` (authoritative; its recon anchors were verified twice in review), parent + Phase 0/2 specs, Phase 1/2 plans.

## CONTINGENCY PREAMBLE

Authored 2026-07-09 with **Phases 0, 1, AND 2 unlanded**. Markers **[P0]/[P1]/[P2]** flag tasks consuming those phases' interfaces; each such task's first step is *re-verify the landed interface* and adapt mechanically; structural divergence → STOP and update this plan. Do not begin until Phases 0–2 have merged. This plan is contract-and-anchor throughout (Phase 2 plan's calibration): recon line anchors are cited, complete code only where a piece is pure-new and dependency-free.

## Global Constraints

- JUnit 5 only; never `git add -A`; no new singletons; **no new Maven dependencies** (Phase 2's ASM is the last).
- Commit trailers per repo policy; intermediate `feat` commits touching `src/main/` use `Changelog: n/a: covered by final phase-3 changelog entry in this branch`.
- **Execution branch (user directive 2026-07-10):** implement and commit directly on
  the existing `next` worktree; do not create a phase branch or merge-back commit.
- **Byte-identical guardrail:** every wall-opening task in workstream A ends by running the physics/character suites (`TestPhysicsProfile`, `TestPhysicsProfileRegression`, `TestCollisionModel`, `TestSidekickCpuFollowParity`) plus a trace spot sweep — the seams must be no-ops for stock play.
- **ArchUnit:** expect at minimum the edges the new packages introduce; add exactly what the ratchet's failure message names, citing this plan.
- Regression gates at workstream boundaries: full suite, S3K must-keep-green, and trace spot sweeps (s1_ghz1/s2_ehz1/s3k_aiz1) **in BOTH variants** — mods disabled, AND the phase's sample mod enabled with a stock game launched — bit-identical each (spec §Verification). Log sweeps in `docs/TRACE_FRONTIER_LOG.md`.

---

## Workstream 0 — shared ownership, API, and formats

### Task 0: Phase 3 shared foundation **[P0][P2]**

**Files:** modify `GameModule`, `DelegatingGameModule`, `WorldSession`,
`SessionManager`, `ModContext`, Phase 2 `ModRegistrationPlan`/
`ModBackedGamePatch`/`ModFaultBoundary`, and the candidate mod-API surface baseline;
create `GameDataSource`, `RomDataSource`, `ModAssetDataSource`,
`AbstractStandaloneGameModule`, `ModGame`, `CharacterKey`, `PlayableCharacterRegistry`,
`CharacterDefinition`, and playable-v2 golden fixtures.

- [ ] Write failing tests first proving: one immutable module-owned character registry
  is transactionally decorated by `ModBackedGamePatch`, forwarded, and survives editor
  session rebuild; two owners may use the same local character name without collision;
  save/rewind round trips retain the owner and disabled-owner fallback is explicit;
  `WorldSession` retains its data
  source; directory/jar assets obey root containment; standalone bases require only
  the documented minimum; Phase 3 public/protected API changes fail the old baseline;
  and playable-v2 golden bytes round-trip.
- [ ] Implement the exact contracts from the Phase 3 design and cross-phase
  format/security spec. Maintain a candidate surface baseline as types land; do not
  change the published API version in Task 0.
- [ ] Run the focused tests, the Phase 0 module-forwarding tests, and
  `mvn "-Dtest=TestArchUnitRules,TestPerGameRuleArchitectureGuard" test`; commit.

## 2026-07-10 readiness amendments (authoritative)

- A1 does not create an ad-hoc/global registry. It consumes the immutable module-owned
  registry from Task 0; bootstrap, active-team resolution, art, physics, super state,
  and labels share it. Add `SONIC_AND_TAILS`; reject `SONIC_ALONE` custom mains with
  sidekicks rather than silently remapping.
- A4 implements playable container v2 exactly from the format/security spec before
  runtime art consumption. A5/A6 add all new types/protected hooks to `@ModApi` and
  refresh the semantic surface baseline.
- Task 0 extends Phase 2 `ModFaultBoundary` with owner-keyed character and standalone
  invocation helpers. A1 wraps sprite/respawn factories, A4 art suppliers, A5 ability
  hooks, and B3 every standalone module/game/provider callback; tests assert finding,
  pending disable, owner/dependent cascade, and return-to-title behavior.
- B1 uses Task 0's real asset-opening `GameDataSource`, stored by `WorldSession` and
  passed through `SessionManager`; nullable ROM is a capability, not the interface.
- B2 adds a deny-by-default architecture test for new `switch(GameId)` sites lacking
  an explicit standalone/game-code route.
- B3 uses the common session join point and tests source survival through editor
  rebuild plus every standalone-reachable ROM touch.
- B4/B6 replace enum arrays with one `List<MasterTitleEntry>` model used by every
  clamp/draw/ROM-gate/persistence/selected-id/launch path. Automated tests drive a
  selection result into standalone boot with an empty ROM directory.
- B5 uses namespaced `TrackKey` and one-shot SFX keys; it never allocates numeric ids.
- Standalone entries reserve namespaced slot 1 and expose New Game/Continue for that
  valid slot. Acceptance saves, returns to title, and resumes through Continue; direct
  `SaveManager` calls alone do not prove UX.
- Workstream gates use exact commands: focused Maven tests; the S3K set with
  `-Ds3k.rom.path=s3k.gen`; then `mvn test`. Record/stage trace-log changes and update
  README release notes in B7.

---

## Workstream A — mod characters

### Task A1: `PlayableCharacterRegistry` + `CharacterDefinition` **[P2]**

**Files:**
- Modify: `src/main/java/com/openggf/game/session/GameplayTeamBootstrap.java` (`createPlayable` ~110-121, respawn chain ~81-85)
- Modify: Phase 2 `ProjectScaffolder` to add the compilable sample character stub now
  that the Phase 3 API exists
- Test: `src/test/java/com/openggf/sprites/playable/TestPlayableCharacterRegistry.java`

**Contract (spec §A1):** `CharacterDefinition(CharacterKey, displayName, spriteFactory, respawnStrategyFactory = SonicRespawnStrategy, behavesLike archetype, SecondaryAbility, supportsSuperForm = false for mods, artSupplier)`. Builtin keys persist unchanged; `ModContext.registerCharacter(localName, ...)` constructs the owner-tagged `modId:localName` key and freezes it through Task 0's registration-plan/backing-patch path. Registry builtins reproduce today's chain byte-identically. **Archetype validation:** `KNUCKLES` requires `GLIDE`. `createPlayable`/respawn become registry lookups (unknown/disabled mod key → explicit Sonic fallback + finding). Test same local name across two owners and owner-safe lookup.

- [ ] Steps: re-verify Task 0 registry ownership/fault helper and Phase 2 `ModContext` → failing tests (builtin equivalence incl. respawn strategies; unknown fallback; KNUCKLES/GLIDE refusal; mod registration round-trip; throwing mod sprite/respawn factory follows the owner fault contract; unedited `ggfmod init` scaffold now includes and compiles the character stub) → implement → PASS → byte-identical guardrail suites → commit (`feat: playable character registry over the hardcoded-three factory`).

---

### Task A2: `characterKey()` virtual + physics resolution

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` (`resolvePhysicsProfile` ~3625-3637, `getInitProfile` use ~3658), `Sonic.java`, `Tails.java`, `Knuckles.java`
- Test: `src/test/java/com/openggf/sprites/playable/TestCharacterTypeResolution.java`

**Contract (spec §A2):** `public CharacterKey characterKey()` — base implementation is today's instanceof chain moved verbatim into builtin keys; the three builtins override with constants; `resolvePhysicsProfile`/`getInitProfile` consume `key.persisted()`. A stub mod subclass returning `CharacterKey.mod("owner-a", "modchar")` resolves only `provider.getProfile("owner-a:modchar")`; a second owner with local `modchar` remains distinct.

- [ ] Steps: failing tests → implement → PASS → guardrail suites + trace sweep → commit (`feat: owner-tagged character key replaces instanceof physics derivation`).

---

### Task A3: Archetype resolution in `ActiveGameplayTeamResolver`

**Files:**
- Modify: `src/main/java/com/openggf/game/session/ActiveGameplayTeamResolver.java` (`resolvePlayerCharacter` ~45-59)
- Test: extend its existing tests

**Contract (spec §A3):** non-builtins use the declared archetype; builtins keep the
current chain. A custom main with sidekicks must declare SONIC_AND_TAILS or a non-Sonic
routing archetype; SONIC_ALONE + sidekicks is a load error, never remapped.

- [ ] Steps: failing tests (three builtin paths unchanged; mod code → declared archetype; sidekick non-remap) → implement → PASS → **byte-identical guardrail suites + trace spot sweep** (this task touches character routing — wall 3) → commit (`feat: behavior archetype resolution for mod characters`).

---

### Task A4: Registry-first playable art + v2 playable container **[P2]**

**Files:**
- Modify: `src/main/java/com/openggf/level/LevelPlayableArtInitializer.java` (~63-128: consult `PlayableCharacterRegistry` art supplier before the `Game` provider)
- Create: playable-container v2 reader/writer and `convert art --playable` implementing
  the pinned `GGFP` section format and Task 0 golden fixtures
- Test: container round-trip; initializer registry-first/game-fallback; multi-character
  playable-art bank allocation

**Contract (spec §A4):** registry-first lookup and byte-identical builtin fallback;
materialize the full `SpriteArtSet` from the already-pinned `GGFP` sections. The
converter may generate trivial DPLC runs with a cost warning. HUD/portrait research
  is documented for a future v3 only; v2 META flags remain zero and schema recutting
  here is forbidden. Allocate multiple mod mains/sidekicks through the shared virtual
  bank allocator and reject capacity overflow before art installation; duplicate
  character kinds never collide.

- [ ] Steps: re-verify Phase 2 container format + read `SpriteArtSet`/initializer + the HUD-icon verification above → failing tests including two mod characters, duplicate sidekick banks/overflow, and a throwing art supplier through the owner fault contract → implement → PASS → **byte-identical guardrail suites + trace spot sweep** (this task touches `LevelPlayableArtInitializer` — wall 4) → commit (`feat: registry-first playable art with v2 baked container`).

---

### Task A5: Moveset hook + super-form gate

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` (hook), `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java` (ability dispatch site), `src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java` (gate; read ~126-147 first)
- Test: hook consumption + gate tests

**Contract (spec §A6):** one `protected boolean onAbilityActivate(...)` pre-dispatch hook (default false → today's enum dispatch runs; returning true consumes the press) — stock behavior byte-identical; `supportsSuperForm` checked at the transformation-eligibility site (builtins true, registry-resolved for mods; a false gate refuses transformation, rings/emeralds untouched).

- [ ] Steps: read the ~9 dispatch sites, pick the activation site → failing tests including a throwing owner-tagged ability hook through the fault contract → implement → PASS → guardrail suites (super-state tests included) → commit (`feat: ability pre-dispatch hook and super-form registry gate`).

---

### Task A6: Surfacing (launch label fallback, data-select docs) + character acceptance sample **[P0][P2]**

**Files:**
- Modify: `src/main/java/com/openggf/game/launch/LaunchProfile.java` (`characterLabel` ~312-320)
- Create: the sample mod character source under `src/test/resources/mods/sample-character-src/` + its headless acceptance test

- [ ] **Step 1:** `LaunchProfile.characterLabel` (~312-320) parses persisted
  `CharacterKey` values and gains a registry-display-name fallback for mod keys; mod
  characters reach launch lists via Phase 0's `providedMainCharacters()` union using
  canonical `owner-a:modchar`. Test two owners sharing local `modchar`.
- [ ] **Step 2 (acceptance):** sample S2 mod character with owner-tagged key and sidekick explicitly
  `none`, `behavesLike: SONIC_ALONE`, NONE ability, distinct profile, and baked v2 art.
  Assert launch/profile/save/rewind and deterministic disable behavior.
- [ ] **Step 3:** guardrail suites + sweeps → commit (`feat: mod character surfacing and acceptance sample`).

---

## Workstream B — standalone games

### Task B1: `GameDataSource` + the five shared fetches **[P0]**

**Files:**
- Modify: Task 0's `GameDataSource` consumers in `LevelManager`,
  `LevelWaterCoordinator`, `ParallaxManager`, and game initialization
- Test: null-path tests per site + a ROM-session equivalence test

**Contract (spec §B1):** consume Task 0's session-owned `GameDataSource` (`Optional<Rom>`, bounded `openAsset`, stable identity). Source-based defaults require the ROM capability; standalone bases override from assets. The five fetches route through the session source and editor rebuild retains it. ROM sessions remain byte-identical.

- [ ] Steps: re-verify Phase 0's loader null-Rom behavior [P0] → read the five sites → failing tests → implement → PASS → full zone-loading + S3K gates → commit (`feat: GameDataSource abstraction over shared ROM fetches`).

---

### Task B2: `GameId.STANDALONE` + `getGameCode()`

**Files:**
- Modify: `game/GameId.java`, `game/GameModule.java`, `Engine.java` (the two gameCode switches ~1069/~1098), `integration/presence/RuntimePresenceSnapshotProvider.java` (~87), `data/RomManager.java` (`resolveRomForGame` ~177-184)
- Test: gameCode derivation + resolveRomForGame explicit-cases tests + deny-by-default
  `switch(GameId)` architecture guard

**Contract (spec §B2):** one new constant; `getGameCode()` default = `GameId.code()`; the exactly-3 compiler-forced switches collapse to `module.getGameCode()`; `resolveRomForGame` becomes explicit s1/s2/s3k with unknown → throw. `SaveManager` needs nothing (string-namespaced).

- [ ] Steps: add the constant, follow the compiler → failing tests including the
  architecture scan that rejects every new `switch(GameId)` without an explicit
  standalone/game-code route → implement → PASS → commit (`feat: GameId.STANDALONE and module game codes`).

---

### Task B3: Standalone boot path + ROM-touch audit **[P1][P2]**

**Files:**
- Modify: `Engine.java` (new `initializeStandaloneGame`), `mods/code/ModContext`,
  `ModRegistrationPlan`, `ModRuntime`, and `ModFaultBoundary`
- Create: `OwnerAwareStandaloneModule.java` (or equivalent supported proxy)
- Test: headless standalone boot test; audit checklist

**Contract (spec §B3):** `ModContext.registerGameModule(module)` is legal only for a
`standalone` owner, exactly once, and freezes atomically into `ModRegistrationPlan`;
failed registration publishes nothing. `ModRuntime` publishes the owner-tagged module
through an `OwnerAwareStandaloneModule` whose module/game/provider callbacks all use
Task 0's fault boundary. Boot skips `romManager`/detection and `resolveModule` (patch
stacking deferred), then joins `openGameplaySession` with `ModAssetDataSource`;
`GameServices.rom()` semantics remain IOException. The headless test exercises the
session join point; B6 drives the UI route. Audit every standalone-reachable ROM touch.

- [ ] Steps: re-verify Phase 1 catalog + Phase 2 registration/fault contracts → failing
  transaction, duplicate-module, module/provider/Game throw, and boot tests → implement
  + ROM-touch audit → PASS → commit (`feat: detection-free standalone game boot path`).

---

### Task B4: Game-agnostic `ModLevel` lift **[P2]**

**Files:**
- Create: `src/main/java/com/openggf/level/ModLevel.java`
- Modify: Phase 2's `ModZoneLoader` (build `ModLevel` when no host module)
- Test: rom-free construction round-trip from fixture assets

**Contract (spec §B5.1):** lift Phase 2's in-memory construction into `ModLevel` (no Rom, no host module; `Pattern`/`Chunk`/`Block`/`Map` are already game-agnostic); retain the Phase 2 S2 overload as a thin adapter over shared decode helpers. Standalone `Game.loadLevel` routes every index to the loader. Linear standalone progression uses the module registry topology and the final declared act returns the module's terminal result (credits/title), never an out-of-range next zone.

- [ ] Steps: re-verify the landed Phase 2 overload → failing tests including final-act terminal progression → implement by lifting, not duplicating (extract shared decode into package-visible helpers if needed) → PASS → HTZ + S3K gates (the S2 overload untouched) → commit (`feat: game-agnostic ModLevel for standalone zones`).

---

### Task B5: Standalone streamed music + one-shot SFX **[P1]**

**Files:**
- Modify: `audio/AudioManager.java` (FALLBACK_WAV branch ~846-859), `audio/AudioBackend.java` (default `tryStartStreamedMusic` exposure), `audio/AbstractSmpsAudioBackend.java` (one-shot SFX pool), `mods/ModAudioIntegration.java` (`buildResolverForStandalone`)
- Test: null-loader music route; SFX pool polyphony/suppression

**Contract (spec §B5.2-3):** standalone definitions route namespaced `TrackKey` and
`SfxKey` through prepared registries. `AudioBackend` exposes keyed start/one-shot
methods; no numeric allocation or stock override id is used. The bounded one-shot pool
is presentation-only and rewind-suppressed. Consume the `sfx` records that Phase 1
already parses/refuses under audio-manifest v1; do not change that format version.
EMPTY preserves stock fallback.

- [ ] Steps: re-verify Phase 1's landed backend surface → failing tests → implement → PASS → audio suites → commit (`feat: standalone streamed music route and one-shot SFX pool`).

---

### Task B6: Master-title standalone entries + standalone acceptance sample **[P1][P2]**

**Files:**
- Create: `src/main/java/com/openggf/game/MasterTitleEntry.java`
- Create: `src/test/resources/mods/sample-standalone-src/` with its source assets and
  build script
- Modify: `MasterTitleScreen`, `Engine`, and master-title result/construction wiring
- Test: screen-logic tests per the editor/manager screen convention; the acceptance test below

- [ ] **Step 1:** Replace every selection/clamp/draw/ROM-gate/persistence/selected-id/
  launch path with one immutable `List<MasterTitleEntry>` of stock/standalone variants;
  no parallel enum arrays remain. Standalone UI offers New Game and Continue when
  reserved slot **1** is valid. New Game targets slot 1; Continue loads slot 1;
  corrupt/missing slot hides Continue. A typed result routes directly to
  `initializeStandaloneGame`, never ROM detection.
- [ ] **Step 2 (acceptance):** package the checked-in standalone sample, then automate
  screen selection→boot with empty ROM root; load/play, start its namespaced music,
  assert the sample's literal physics profile drives its player, spawn/exercise its
  badnik through normal object services, fire one declared SFX through the real
  one-shot pool, complete the final act to its
  terminal result, save slot 1, return to title, observe/select Continue, and verify
  restored zone/team. Assert corrupt-slot behavior and stock trace parity.
- [ ] **Step 3:** full gates → commit (`feat: master-title standalone entries and acceptance sample`).

---

### Task B7: Docs + changelog

- [ ] Write creator guides; finalize all Phase 3 `@ModApi` additions, review the
  public/protected baseline diff, and set additive API version `1.2.0`. Assert both
  Phase 1's canonical `>=1.0.0 <2.0.0` range and Phase 2 ranges beginning at `1.1.0`
  remain eligible. Regenerate/review the annotated API Javadoc and prove every new
  character/standalone signature type is in the closed annotated inventory; update CHANGELOG,
  README, AGENTS/CLAUDE; run exact gates and commit.

---

## Execution notes

- Workstream 0 is required first; A and B then parallelize with disjoint ownership
  except B7's final API/docs integration.
- Re-verify markers are mandatory; this plan predates all three dependency phases.
- Completion flow: commit verified task slices directly on `next`; B7 carries the
  README release-log note. No merge-back step exists.
