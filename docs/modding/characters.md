# Playable character mods

Mod API 0.7 lets a code-bearing patch contribute playable characters to a stock
game. A character has an owner-tagged identity, a factory, a ROM-routing archetype,
a physics profile, baked playable art, and an optional airborne ability hook. The
engine carries that identity through launch selection, saves, rewind, art loading,
and sidekick creation.

Use the checked-in
[`sample-character-src`](../../src/test/resources/mods/sample-character-src/README.md)
project as the executable reference. It targets Sonic 2, builds real `.ggfp` art,
registers a distinct physics profile, packages the mod, and is exercised by the
headless Phase 3 acceptance test.

## Manifest and identity

A character contribution is executable code, so its manifest needs a trusted
entrypoint and the maintained 0.7 range:

```yaml
formatVersion: 1
id: my-character-pack
name: My Character Pack
version: 1.0.0
authors: [Mod Author]
description: Adds an original runner to Sonic 2.
engineApiRange: ">=0.7.0 <0.8.0"
type: patch
baseGame: s2
entrypoint: example.characters.MyCharacterMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

`CharacterKey.mod(owner, localName)` turns the manifest owner and local name into
the canonical persisted key `<mod-id>:<local-name>`. Pass that same key in the
`CharacterDefinition` and register the definition with the same local name. The
host binds that key during factory construction; the default `characterKey()`
returns it without parsing the sprite's runtime name:

```java
CharacterKey key = CharacterKey.mod(context.ownerModId(), "runner");
context.registerCharacter("runner", new CharacterDefinition(
        key,
        "My Runner",
        MyRunner::new,
        null,
        PlayerCharacter.SONIC_ALONE,
        SecondaryAbility.NONE,
        false,
        ignored -> playableArt,
        ignored -> playablePalette));
```

The local name is owner-scoped, so two mods may both register `runner` without a
collision. Registration is transactional: duplicate names, a mismatched definition
key, or a later registration failure rejects the owner's whole contribution rather
than publishing a partial registry. The resulting `PlayableCharacterRegistry` is an
immutable module-owned snapshot.

Unknown character keys and keys whose owner is disabled fall back explicitly to
Sonic and produce a finding. Do not persist an ad-hoc display label or runtime index;
persist `CharacterKey.persisted()`.

## Character definition

`CharacterDefinition` contains:

- `key` and `displayName` — stable identity and launch-screen label;
- `spriteFactory` — creates the main or sidekick sprite from persisted code and
  centre position;
- `respawnStrategyFactory` — pass `null` for `SonicRespawnStrategy`, or supply a
  strategy appropriate to the character;
- `behavesLike` — the existing ROM routing bucket used by events and level logic;
- `secondaryAbility` — one built-in double-jump ability or `NONE`;
- `supportsSuperForm` — must be `false` for mod characters in playable format v2;
- `artSupplier` — the full `SpriteArtSet` from a baked playable sheet; and
- optional `paletteSupplier` — the character palette, or `null` to retain the host
  game's palette fallback;
- optional `underwaterPaletteSupplier` — an authored 16-colour water line. The full
  constructor takes this after `movementAbilities`; existing constructors default
  it to `null`. Suppliers have the same owner fault boundary as the dry palette.

When absent, water colours are derived at rendering time using the host zone's
normal and underwater palette pair, including zone changes and palette cycles.
The native games load authored ROM tables: S1 replaces line zero with LZ/SBZ3
Sonic water colours; S2 uses CPZ/ARZ water palettes; S3K selects a zone water
palette and applies character-specific patches. The shared renderer estimates
their per-channel colour shift for custom rows; this is a creator fallback,
not a claim that the ROM transforms arbitrary character palettes.

The maintained [MICA skeleton](../../examples/mica-and-talon/README.md) demonstrates
`baseGame: any` registration and acceptance in all three games.

The archetypes are routing contracts, not cosmetic labels:

| Archetype | Meaning |
|---|---|
| `SONIC_ALONE` | Sonic's solo event and level route. It is invalid for a custom main launched with sidekicks. |
| `SONIC_AND_TAILS` | Sonic's team route, including sidekick-aware event branches. |
| `TAILS_ALONE` | Tails' solo routing bucket. |
| `KNUCKLES` | Knuckles-exclusive routes and gates. The definition must declare `SecondaryAbility.GLIDE` to avoid softlocks. |

Choose the route whose geometry and events the character can actually complete.
The engine does not silently remap `SONIC_ALONE` to a team route when sidekicks are
present. A `SONIC_AND_TAILS` definition uses `SONIC_ALONE` for a solo launch.

## Sprite and physics

Extend `AbstractPlayableSprite`. The factory constructor receives the persisted code
and centre coordinates. Keep the default `characterKey()` and declare the
secondary ability consistently. An expert identity override is still supported,
but the host rejects a null, unstable, or mismatched declaration before publishing
the sprite:

```java
public final class MyRunner extends AbstractPlayableSprite {
    public MyRunner(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
    }

    private static CharacterPhysicsSpec physicsSpec() {
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(0x10, 0x80, 0x10, 0x500, 0x640)
                .rolling(0x20, 0x80, 0x80, 0xE00).build());
    }

    @Override public SecondaryAbility getSecondaryAbility() {
        return SecondaryAbility.NONE;
    }
    @Override public void draw() {
        drawStandardArt();
    }
}
```

`drawStandardArt()` submits the installed current pose when visible. The host
continues to own the renderer and its pattern bank; drawing changes no simulation
state. Runtime main/sidekick suffixes have no effect on the factory-bound key, and
the same sprite class can serve several distinct registered definitions. Outside
a registered factory, the base retains its safe native identity fallback.

`CharacterPhysicsSpec` owns this instance's active speeds, standing/rolling shape
and sensors. It creates paired terrain probes with `PlayableSensorSpec.standard()`
by default; pass an explicit sensor spec for different side-probe reach. Grouped
`PhysicsProfile` builders validate ranges before narrowing values. Keep custom
specs on each instance or construct them through a factory; creator classes cannot
keep engine objects in static fields.

The module still supplies game-wide modifiers and `GameRules`. A patch can also
expose the authored tuning through its provider for other module consumers. The
maintained `SampleCharacterPhysicsPatch` uses a scoped transformation of the
inherited provider, reusing `SampleCharacter.physicsSpec().profile()` and preserving
other characters, rules and modifiers. The instance constructor uses that same
factory. Legacy expert subclasses can retain `defineSpeeds()` and
`createSensorLines()`; new characters need no duplicated construction defaults.

The character's generated content patch automatically advertises the canonical key
to the stock launch configuration. Its display name comes from the registry. A saved
team stores that same key, and rewind recreates the owner class through the original
mod class loader.

## Baked playable art

Ordinary object sheets and playable sheets are different containers:

- `.ggfs` is produced by `convert art` for objects and static sheets.
- `.ggfp` is playable container v2, produced by `convert art --playable`; it includes
  mappings, animation data, palette data, bank metadata, and per-frame DPLC runs.

Place `--playable` immediately after `convert art`:

```text
ggfmod convert art --playable --image src/main/mod/runner.png --sheet src/main/mod/runner-sheet.yaml --out target/classes/art/runner.ggfp
```

The current converter generates trivial full-frame DPLC runs and reports their bank
cost. Treat that warning as a VRAM budget: the engine reserves separate virtual
pattern banks for mains, sidekicks, and duplicate character kinds, and rejects a
capacity overflow before installing art. Materialize the validated file with
`PlayableSheetMaterializer.read(...)`, then supply its `art()` and `palette()` from
the definition as shown by the sample.

Playable v2 has no custom HUD life icon or data-select portrait. Those presentations
use the host fallback. Mod characters also do not receive Tails' separate tail
appendage renderer automatically.

## Ability and super-form rules

Use `INSTA_SHIELD`, `FLY`, `GLIDE`, or `NONE` when an existing
`SecondaryAbility` is sufficient. For one novel airborne activation, override:

```java
@Override
protected boolean onAbilityActivate(boolean up, boolean down,
                                    boolean left, boolean right) {
    // Start the character-specific action.
    return true;
}
```

The hook runs only for a valid airborne ability-button activation. Returning `true`
consumes the press before built-in ability dispatch; `false` leaves stock dispatch
unchanged. It is not a general replacement for ground movement or the player state
machine.

Reset grounded abilities in `onLanded()` and load/respawn state in `onLevelReset()`.
`onGroundStateChanged(boolean airborne)` reports both transition directions. These
hooks run through the character's owner fault boundary during simulation. Drawing
must not change gameplay state. Rewind restores captured state without emitting
landing/reset events, so an ability latch must still be captured explicitly.

Mod characters cannot opt into stock super forms in the current 0.7 contract: set
`supportsSuperForm` to `false`. This prevents a custom sprite from transforming with
Sonic's art merely because the stock ring and emerald requirements are met.

## Failure, rewind, and acceptance checklist

Character factories, respawn factories, art/palette suppliers, identity methods,
and ability hooks run through the owning mod's fault boundary. A nonfatal creator
exception records a finding, schedules that owner and its dependents to be disabled
on the next launch, and returns the current session to the title where required.
Keep mutable gameplay state in rewind-capturable instance fields, use injected
services after construction, and never keep gameplay state in mutable statics.

**Rewind capture for character subclass fields:** the closed,
hand-enumerated `PlayerRewindExtra` base surface is no longer the whole story.
`AbstractPlayableSprite` exposes an overridable
`captureSubclassRewindState()` / `restoreSubclassRewindState(PlayableSubclassRewindExtra)`
pair a character subclass can override to carry its own rewind-relevant fields
alongside the base snapshot:

- **`captureSubclassRewindState()`** runs on every keyframe capture — every
  `captureRewindState(boolean)` call, which includes keyframe-exact seeks and
  cached-segment scrubs, not just the "live" forward-play capture path. Return
  an immutable payload implementing the empty marker interface
  `PerObjectRewindSnapshot.PlayerRewindExtra.PlayableSubclassRewindExtra`
  (typically a `record`), or `null` if there is no subclass state to capture.
  The default implementation returns `null`, which is correct for the base
  Sonic/Tails/Knuckles sprites and any subclass that has not overridden the
  hook.
- **`restoreSubclassRewindState(extra)`** runs on every rewind restore — every
  `restoreRewindState(PerObjectRewindSnapshot)` call, again including
  keyframe-exact seeks and cached-segment scrubs — and is **always** invoked,
  never skipped. It runs *after* all base `PlayerRewindExtra` fields,
  controller-owned state (movement, spindash dust, animation, drowning,
  Tails-carry, sidekick CPU), and sensor offsets have already been restored,
  so an override may safely read already-restored base sprite/controller
  state when reconstructing subclass-local state.

**Immutability contract:** the captured payload is stored as-is in the
snapshot's in-memory object graph — there is no serialization step and no
defensive copy. Implementations must be immutable (a `record` of primitives or
other immutable types) and must never alias mutable live sprite/controller
state.

**Null contract:** `extra` is `null` when the owning snapshot carries no
subclass payload — the common case for base playable sprites and for subclasses
that do not override `captureSubclassRewindState()`. Overrides
must tolerate a `null` argument, typically by resetting subclass state to its
default, rather than assuming a payload is always present.

The `sample-platformer` Bolt character's double-jump latch is the executable
reference, copied verbatim from
[`BoltCharacter.java`](../../src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/BoltCharacter.java):

```java
/** Immutable payload carrying the double-jump latch through a rewind keyframe. */
private record BoltRewindExtra(boolean doubleJumpUsed)
        implements PerObjectRewindSnapshot.PlayableSubclassRewindExtra {
}

@Override protected PerObjectRewindSnapshot.PlayableSubclassRewindExtra captureSubclassRewindState() {
    return new BoltRewindExtra(doubleJumpUsed);
}

/**
 * Tolerates {@code null} (no subclass payload in the snapshot) by resetting the
 * latch to its fresh default of {@code false} rather
 * than assuming a payload is always present, per the hook's null contract.
 */
@Override
protected void restoreSubclassRewindState(PerObjectRewindSnapshot.PlayableSubclassRewindExtra extra) {
    doubleJumpUsed = extra instanceof BoltRewindExtra bolt && bolt.doubleJumpUsed();
}
```

A rewind seek that lands exactly on a keyframe or replays a cached segment
restores `doubleJumpUsed` byte-for-byte. Bolt clears it in `onLanded()` and
`onLevelReset()` during new simulation transitions; its draw method only renders.
Restoring a grounded keyframe and drawing repeatedly preserve the captured latch.

Before distributing a character mod:

1. build and validate the packed jar;
2. select the character from the base game's launch configuration;
3. verify its canonical key, custom physics, art, palette, and chosen archetype;
4. save and reload a team containing the character;
5. rewind across construction and ability use; and
6. disable or remove the mod and confirm the saved slot reports the missing owner and
   falls back safely rather than corrupting the slot.

Expert module registries also bind fresh character factories, art/palette suppliers
and custom respawn strategies to the module's verified owner. New custom keys must
use that registered owner. Inherited definition
instances keep their earlier ownership. A replacement for a builtin retains its
semantic `sonic`, `tails` or `knuckles` key while its callbacks belong to the mod
that publishes it. Factories create a fresh sprite for each player/session;
reusing an instance from an earlier construction is rejected because it retains
its previous runtime callback boundary.


### Movement ability declarations

`CharacterDefinition.movementAbilities` is an immutable set of declared native
moves. Existing convenience constructors declare an empty set; use the full
constructor with `Set.of(CharacterDefinition.MovementAbility.SPINDASH)` when the
actor implements native spindash. The registry intersects each actor's declaration
with its donor's enabled native rules. Stageplay uses that result to offer and
validate route starting actions, without branching on actor or game names. The
contract is extensible to future moves and mod actors; Stageplay's current stock
cast/asset limits still apply.

### Canonical animation table

Playable sheet manifests require an `animations` table with uppercase
`CanonicalAnimation` names. `WAIT` is mandatory; there is no `idle` alias.
One baked sheet works in all three hosts. The host binds native animation IDs
at installation, using its native velocity profile and canonical fallback map;
an authored equivalent sharing a host fallback destination (for example STOP/SKID)
can serve that destination. An absent pose whose fallback is unavailable uses `WAIT`.

```yaml
animations:
  WAIT: { frames: [0], durations: [8], end: loop }
  WALK:
    frames: [1, 2, 3, 4]
    speedDriven: true
    end: loop
    slopes:
      - [1, 2, 3, 4]
      - [5, 6, 7, 8]
      - [9, 10, 11, 12]
      - [13, 14, 15, 16]
  RUN: { frames: [17, 18], speedDriven: true, end: loop }
  ROLL: { frames: [19, 20], speedDriven: true, end: loop }
  SPRING: { frames: [21], durations: [8], end: hold }
  HURT: { frames: [22, 23], durations: [4, 8], end: next, next: WAIT }
```

Frame indices refer to the manifest's top-level `frames`. Fixed `durations` are
positive ticks per frame and must match the frame list length. `speedDriven`
is required for `WALK`, `RUN`, `ROLL`, `ROLL2`, and `PUSH`, selecting native
walk/run, roll or push timing. Other poses require fixed durations. End actions are `loop` (default), `hold`, or `next`
with an authored canonical `next` target. WALK and RUN share the host's speed
threshold and native slope orientation rules.

Optional WALK/RUN `slopes` contains four equal, even-length cycles in native
slope-selector order (0, 2, 4, 6), including the flat cycle first. Their length
must equal `frames`; they require speed timing. Without slopes, the flat cycle
is repeated in all four orientations. Mapping and DPLC references are packed
into strides at materialization; source tiles are shared. Mod sheets have no
implicit native tumble or Super alternate frames.

ROM-free standalone modules have no native ID vocabulary; their default presentation
retains the authored `WAIT` cycle. Native profile binding applies to stock hosts.
