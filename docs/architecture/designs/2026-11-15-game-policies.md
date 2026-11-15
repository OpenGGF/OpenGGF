# Game policies: one typed policy subtree under `GameModule`

**Status:** slice 1 implemented on `feature/ai-game-policies` (base `11250be8e`);
slice 2 (audio rules) designed below and not yet implemented.
**Roadmap:** [0.8 Milestone 1](../../project/v0.8-roadmap.md#milestone-1--lock-the-game-policy-architecture-before-mod-api-publication).
**Rule placement:** [per-game rule placement](../per-game-rule-placement.md) keeps its decision
tree and admission checklist; this record only changes who owns the aggregate.

## Problem

`GameRules` (camera, rings, objects, sidekick CPU, power-ups, drowning, palette fade,
dynamic-art DMA and the player groups) was owned by `PhysicsProvider.getRules()`.
`GameModule.getRules()` was a default that reached through the physics provider, and
`DelegatingGameModule` forwarded it. Physics owns tuning profiles and water/speed-shoes
modifiers; it is not the owner of camera, ring or object behaviour. A patch that only
wanted to change ring rules had to wrap the physics provider, and a creator writing
`PhysicsProvider.fixed(profile, modifiers, rules)` had to choose game-wide rules in a
physics factory.

`GameAudioProfile` mixes two kinds of member: immutable behavioural modes (1-up
override interaction, speed-shoes tempo model, fade parameters) and runtime
construction (`createSmpsLoader(Rom)`, `loadSegaPcm`, request services, coordination
flag handlers, physical/stateful driver policies).

## Decision

`GameModule` stays the sole composition and lifecycle root. It gains one accessor,
`GamePolicies getPolicies()`, returning an immutable record:

```java
@ModApi
public record GamePolicies(GameRules rules) { /* non-null, no withers */ }
```

| Component | Type | Owner of values | Slice |
| --- | --- | --- | --- |
| `rules` | `GameRules` (the existing narrow-record groups, unchanged) | stock constants `GameRules.SONIC_1/2/3K`, `Kis2Rules.RULES`, `SonicCdRules.RULES` | 1 |
| `audio` | `AudioRules` (new immutable record, see slice 2) | each game's audio package | 2 |

Rules for the aggregate:

- `GamePolicies` is a typed subtree, not a superclass and not a second root. It has no
  factories, callbacks, lifecycle or session state, and no `with*` methods.
- `getPolicies()` is abstract: every module states its policies. Stock modules return
  one static instance, so the value is stable for the module's lifetime.
- Consumers depend on the narrow record they read (`module.getPolicies().rules().ring()`),
  never on the aggregate. Only composition code names the `GamePolicies` type
  (modules, `DelegatingGameModule`, standalone specs, the donation composer and
  `CrossGameFeatureProvider`); a guard enforces the allow-list.
- Adding a component is an architecture change: the guard pins the component list.

`PhysicsProvider` loses `getRules()`. `PhysicsProvider.fixed(profile, modifiers)` and
`transform(...)` keep profiles and modifiers only. `GameModule.getRules()` and
`DelegatingGameModule.getRules()` are removed.

## Module kinds

| Module | `getPolicies()` |
| --- | --- |
| `Sonic1GameModule` | `new GamePolicies(GameRules.SONIC_1)` (static) |
| `Sonic2GameModule` | `GameRules.SONIC_2` |
| `Kis2GameModule` (extends S2) | `Kis2Rules.RULES` |
| `Sonic3kGameModule` | `GameRules.SONIC_3K` |
| `SonicCdGameModule` | `SonicCdRules.RULES` |
| `DelegatingGameModule` and every patch/runtime decorator (competition, editor, Stageplay, mod patches) | forwards `base.getPolicies()`; a patch that ships a different rule overrides `getPolicies()` and builds a new record from the base value |
| `AbstractStandaloneGameModule` | `StandaloneGameSpec.policies()`; the builder default is `new GamePolicies(GameRules.SONIC_2)`, matching the previous `PhysicsProvider.fixed(..., GameRules.SONIC_2)` default |

Mod-supplied standalone modules use `StandaloneGameSpec.Builder.policies(...)`;
`physics(PhysicsProvider.fixed(profile, modifiers))` no longer carries rules.

## Cross-game donation

`CrossGamePolicyComposer` is the only public donation composer:

```java
public static GamePolicies donatePlayerCapabilities(
        GamePolicies host, GamePolicies donor, DonorCapabilities capabilities)
```

It copies `host` and replaces only the `DonorCapabilities`-listed
`PlayerCapabilityRules` fields (spindash and its table, elemental/lightning shields,
insta-shield, Tails flight, Knuckles glide attacks), using the same field rules as the
previous `CrossGameRuleComposer.compose`. Every other rule group (and, in slice 2,
audio) stays the host's object. Null capabilities return `host`.

Invalid mixtures are unrepresentable through the public composer: it accepts only
whole policies plus the donor's capability declaration, never a category record, and
`GamePolicies` has no category-replacement methods. A reflection test pins that shape.
There is no generic "replace category X" mechanism.

`CrossGameRuleComposer.compose` becomes package-private (the policy composer's
implementation). Its two derivation helpers, `withPlayerRules` and
`withPlayerAndCameraRules`, are not donation: they define a stock game's rules from a
base game's constants (KiS2 over S2, Sonic CD over S1). A guard limits their callers to
`Kis2Rules` and `SonicCdRules`.

`CrossGameFeatureProvider.getHybridRules()` becomes `getHybridPolicies()`; the player
sprite reads `.rules()` from it, as before.

## Caller migration (`getRules()` → narrow read through `getPolicies()`)

Every production caller was a `GameModule.getRules()` or `PhysicsProvider.getRules()`
read; each becomes `getPolicies().rules()` followed by the same narrow group:

| Caller | Group read |
| --- | --- |
| `LevelManager` (8 sites), `LevelFrameContext`, `LevelRenderer`, `LevelScrollPresentation` | collision, objectInteraction, ring, camera, playerMovement, whole groups for the frame context |
| `LevelPlayableArtInitializer`, `NativePlayerArtBindings`, `PlcFrameLifecycleCoordinator` | dynamicArtDmaService |
| `ObjectManager`, `ObjectTouchResponseController`, `AbstractObjectInstance`, `AnimalObjectInstance`, `DefaultPowerUpSpawner` | objectInteraction, powerUp |
| `RingManager`, `LostRingObjectInstance`, `CheckpointState`, `TraceReplaySessionBootstrap` | ring |
| `AbstractPlayableSprite` (profile resolution, invincibility music), `PlayablePlacementProfile`, `PlayableCharacterRegistry` | the sprite keeps its `GameRules` group; donor reads use the donor module's policies |
| `GameplayModeContext`, `GameplaySessionFactory` | paletteWhiteFade and session rule groups |
| `CrossGameFeatureProvider` | composer input |
| `StageplayNativeCast`, `StageplayResources`, `StageplayTool` | playerCapability |
| stock physics providers, `Kis2PhysicsProvider`, `Kis2GameModule` | removed; values move to the module's `getPolicies()` |
| examples `infinite-sonic` (physics wrapper), `sonic-survivors` (arena power-up rule) | wrapper drops `getRules()`; Survivors overrides `getPolicies()` |

The player sprite's own `GameRules` field and `PlayableEntity.getGameRules()` remain: the
sprite reads many player groups and also receives donated hybrids. Narrowing that
field is not part of this milestone.

## Guards

`TestGamePolicyArchitectureGuard`:

1. `GamePolicies` record components are pinned (names and types).
2. No `PhysicsProvider` or `GameAudioProfile` method returns `GameRules`/`GamePolicies`;
   `GameModule` declares `getPolicies()` and no `getRules()`.
3. The `GamePolicies` type is named only by the composition allow-list.
4. No production type is declared as a broad feature bag
   (`*FeatureSet`, `*FeatureFlags`, `*FeatureBag`, `*Features`, `*Toggles`).
5. Shared runtime packages (`level`, `sprites`, `physics`, `camera`, `timer`,
   `graphics`, `game.rules`, `audio` outside `audio.debug`) contain no game-code string
   branches (`"s1"`, `"s2"`, `"s3k"`, `"scd"`, `"kis2"`) and no concrete stock-module
   `instanceof`; the existing `GameId` branch guard keeps covering enum branches.
6. `CrossGameRuleComposer` derivation helpers are called only from stock rule factories.

The existing positional-`new GameRules(` guard and component-count ceilings stay.

## Slice 2 — audio rules (designed, not implemented)

`AudioRules` (in `com.openggf.audio`, so the audio layer gains no `game` edge) holds the
immutable behavioural modes now spread over `GameAudioProfile` defaults:

| Component | Current member |
| --- | --- |
| `musicDuringOverride` | `getMusicDuringOverridePolicy()` |
| `musicOverrideRetrigger` | `getMusicOverrideRetriggerPolicy()` |
| `systemCommandDuringOverride` | `getSystemCommandDuringOverridePolicy()` |
| `ordinaryMusicSfx` | `getOrdinaryMusicSfxPolicy()` |
| `segaPcmPlayback` | `getSegaPcmPlaybackPolicy()` |
| `sfxBlockedDuringRestoreFadeIn` | `blocksSfxDuringMusicRestoreFadeIn()` |
| `speedShoesMode`, `speedShoesMultiplier` | `getSpeedMode()`, `getSpeedMultiplierValue()` |
| `fadeOutSteps`, `fadeOutDelay` | `fadeOutMusic(AudioManager)` (S1/S2 `0x28,3`; S3K `0x28,6`) |

`GameAudioProfile` keeps the game's sound catalogue (sound/music maps and IDs, SFX
priority/continuous/special classification — ROM data facts) and the runtime
construction members (`createSmpsLoader`, `loadSegaPcm`, `getSegaPcmSpec`,
`createAudioRequestService`, `configurePresentationCoordFlagHandlers`,
`smpsPhysicalPolicy`, `smpsStatefulCommandPolicy`, `getSequencerConfig`,
`handleSystemCommand`).

Wiring: the audio manager's base and donor sources currently carry only a profile, and
every behavioural read goes through `source.profile()`. Slice 2 changes installation to
`setAudio(GameAudioProfile, AudioRules)` (module call sites pass
`module.getPolicies().audio()`), stores the rules in `BaseAudioSource` and
`DonorAudioSource`, and registers donor sources with the donor module's rules.
`AbstractSmpsAudioBackend` receives the same pair. This touches about 250 test
installations and the audio presentation parity/zero-allocation contracts, so it is a
separate reviewable PR rather than part of the rule-ownership move. Donated audio
keeps today's per-source ownership: the donor source's modes come from the donor
module's `AudioRules`, exactly as they come from the donor profile now.

## Mod API

The candidate signature pin, `ModApiVersion` note, creator docs (standalone games,
standalone platformer guide), the two sample standalone mods and the affected example
mods change together. `mod-api-release-policy.properties` is not touched: the candidate
stays `0.7.0`, unpublished.
