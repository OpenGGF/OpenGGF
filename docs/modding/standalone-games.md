# Standalone games

Mod API 1.2 lets a trusted mod jar provide a complete no-ROM game. A standalone mod
registers one `GameModule`, opens all authored assets from its immutable mod asset
root, appears beside the three stock games on the master title, and uses a save
namespace equal to its manifest id.

The checked-in
[`sample-standalone-src`](../../src/test/resources/mods/sample-standalone-src/README.md)
is the executable reference. It packages one baked level, an owner-tagged main and
sidekick, literal physics, one badnik, streamed music, and a one-shot SFX. Its
acceptance test launches through the real master-title route with no ROMs, completes
the terminal act, saves slot 1, returns to title, and continues the saved team.

## Manifest and registration

A standalone manifest has no `baseGame` and requires API 1.2:

```yaml
formatVersion: 1
id: my-standalone
name: My Standalone Game
version: 1.0.0
authors: [Mod Author]
description: A one-zone original game built on OpenGGF.
engineApiRange: ">=1.2.0 <2.0.0"
type: standalone
entrypoint: example.standalone.MyStandaloneMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

The entrypoint may register owner-tagged characters and load bounded assets, then
must register exactly one module. The module itself exposes its object registry:

```java
@Override
public void register(ModContext context) {
    String owner = context.ownerModId();
    // Read bounded assets and register characters first.
    context.registerGameModule(new MyStandaloneModule(owner, loadedLevel));
}
```

`registerGameModule` is valid only for a `standalone` owner. The module must report
`GameId.STANDALONE`, and both `getIdentifier()` and `getGameCode()` must equal the
manifest id. A patch manifest cannot register a module; a standalone manifest cannot
register a `GamePatch`. The registration transaction publishes nothing if any
character, object, asset, or module declaration fails.

## Module and game bases

Extend `AbstractStandaloneGameModule`. It fixes `GameId.STANDALONE`, derives the save
game code from `getIdentifier()`, rejects ROM-shaped creation calls, and supplies
no-ROM-safe defaults for optional providers. Implement at least:

- `getIdentifier()`;
- `createGame(GameDataSource)`;
- `createTouchResponseTable(GameDataSource)`;
- `createObjectRegistry()` and `getObjectPlacementEncoding()`;
- `getAudioProfile()`;
- `getZoneRegistry()`; and
- `getPhysicsProvider()`.

Also provide the level-init profile and other capabilities your game actually uses.
Use literal `GameRules` and `PhysicsProfile` values appropriate to your game; do not
pretend to be a stock `GameId` to inherit ROM behavior.

`GameDataSource` is the durable session capability. For standalone sessions,
`rom()` is empty, `openAsset(path)` reads a bounded normalized path from the owning
jar snapshot, and `identity()` is stable for diagnostics and caches. The same source
survives gameplay/editor context rebuilds. Do not call `GameServices.rom()` or assume
a working-directory asset fallback.

Extend `ModGame` for the module's `Game`. Its protected `dataSource()` returns the
source passed by `createGame`, while `getRom()` is intentionally `null`. Implement
`loadLevel(int)` and `getMusicId(int)`; override other neutral defaults only when the
game needs them.

## Baked levels and progression

Use the same exact full-level export inventory and `ggfmod convert level` command as
an additive Sonic 2 zone. During registration, load a primary standalone level
through the supported facade:

```java
Level level = StandaloneLevelLoader.load(
        context.modAssets(),
        new BakedLevelRef("levels/first/level.json"),
        context.ownerModId(),
        ringSpriteSheet);
```

`StandaloneLevelLoader` returns a game-agnostic `ModLevel`; it does not require a ROM
or host module. The module's `ZoneRegistry` owns the zone/act topology and maps each
descriptor's `levelIndex()` to `ModGame.loadLevel`. Keep the topology internally
consistent: saved zone and act values are validated against it both when the title
entry is built and again at launch.

Standalone progression is linear across the declared registry. The final declared
act must return the game's terminal result (normally credits/title), not request an
out-of-range next zone. A normal next-act result remains ordinary stock-style
progression and does not save or return to title early.

## Characters, objects, and callback ownership

Register each character with `ModContext.registerCharacter` as described in
[Playable character mods](characters.md). Phase 3 starts a standalone with the
module's default configured team; there is no standalone roster-selection panel.
If the game supports sidekicks, return `true` from `supportsSidekick()` and ensure
the default/saved roster keys resolve in the module's character registry.

Object placements use namespaced keys such as `my-standalone:walker`; never allocate
a stock byte id as persisted mod identity. Creator object callbacks, registry calls,
solid/touch behavior, dynamic children, act-transition transfers, and rewind
recreation retain the engine-authoritative owning mod. Use injected `ObjectServices`
only after construction and implement the normal rewind recreation contract.

The owner-aware standalone wrapper also guards module, game, and provider callbacks.
A nonfatal creator exception records a finding, pending-disables the owner and its
dependents, and returns the current session to title rather than continuing in a
partially failed game.

## Streamed music and one-shot SFX

Declare standalone audio in `audio/audio-manifest.yaml`:

```yaml
formatVersion: 1
tracks:
  - id: zone-theme
    assetPath: audio/zone-theme.ogg
    loop: true
    loopStartFrame: 44100
    loopEndFrame: 220500
    gain: 0.8
    tempoEffects: true
sfx:
  - id: hit
    assetPath: audio/hit.wav
    gain: 0.7
```

Return `MusicReference.namespaced(owner, "zone-theme")` from the module or zone
registry. Standalone music enters the streamed route even when the native SMPS
loader is `null`; it never allocates or steals a numeric stock music id.

Standalone SFX use namespaced keys from the same manifest and play as decoded PCM
through a bounded 16-voice one-shot pool. They are presentation-only, do not enter
the SMPS command timeline, and are suppressed during rewind. Base-game streamed SFX
overrides are still unsupported; the SFX route described here is for standalone
games. Object code uses the injected service path, for example
`services().playSfx(new SfxKey("my-standalone", "hit"))`.

See [Music packs](music-packs.md) for manifest field rules, loop coordinates,
codec support, gains, and production limits.

## Master title and saves

Each valid enabled standalone mod becomes one master-title entry after the three
stock games. It has no ROM preview or stock launch-configuration panel. Selecting it
offers:

- **New Game**, which starts the module's initial/default state in namespaced slot 1;
  and
- **Continue**, shown only when slot 1 contains a structurally valid payload whose
  team and zone/act topology still resolve.

Saves live under `saves/<manifest-id>/`. Corrupt, fractional, overflowing, negative,
or now-invalid locations hide Continue. Launch validates the payload again so a
catalog or topology change cannot slip through between title rendering and boot.
Completing the terminal credits flow saves and returns to the title; selecting
Continue restores the namespaced main and sidekick identities.

## Phase 3 boundaries

The following are deliberately not part of standalone API 1.2:

- patch stacking onto standalone games;
- a roster or launch-options UI for standalone entries;
- special stages or bonus stages;
- cross-game feature donation to or from a standalone game;
- standalone trace recording;
- a full data-select presentation; and
- Phase 4 work such as TMX import, a docs site/gallery, or a GUI studio.

A standalone module may use the minimal slot-1 flow without implementing a stock
data-select presentation.

## Acceptance checklist

Before distributing a standalone game:

1. build, package, and validate the jar;
2. test with an empty ROM directory and no accidental working-directory assets;
3. launch through the actual master-title New Game action;
4. exercise every level, character, object, streamed track, and SFX through normal
   engine services;
5. complete the final act and verify credits return to title;
6. verify `saves/<manifest-id>/` slot 1 and Continue restore the team and location;
7. corrupt or invalidate a payload and confirm Continue disappears; and
8. launch stock games with the standalone enabled and confirm their behavior remains
   unchanged.
