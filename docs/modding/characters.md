# Playable character mods

Mod API 1.2 lets a code-bearing patch contribute playable characters to a stock
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
entrypoint and an API 1.2 range:

```yaml
formatVersion: 1
id: my-character-pack
name: My Character Pack
version: 1.0.0
authors: [Mod Author]
description: Adds an original runner to Sonic 2.
engineApiRange: ">=1.2.0 <2.0.0"
type: patch
baseGame: s2
entrypoint: example.characters.MyCharacterMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

`CharacterKey.mod(owner, localName)` turns the manifest owner and local name into
the canonical persisted key `<mod-id>:<local-name>`. Pass that same key in the
`CharacterDefinition`, return it from the sprite's `characterKey()`, and register
the definition with the same local name:

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
  game's palette fallback.

The archetypes are routing contracts, not cosmetic labels:

| Archetype | Meaning |
|---|---|
| `SONIC_ALONE` | Sonic's solo event and level route. It is invalid for a custom main launched with sidekicks. |
| `SONIC_AND_TAILS` | Sonic's team route, including sidekick-aware event branches. |
| `TAILS_ALONE` | Tails' solo routing bucket. |
| `KNUCKLES` | Knuckles-exclusive routes and gates. The definition must declare `SecondaryAbility.GLIDE` to avoid softlocks. |

Choose the route whose geometry and events the character can actually complete.
The engine does not silently remap `SONIC_ALONE` to a team route when sidekicks are
present.

## Sprite and physics

Extend `AbstractPlayableSprite`. The factory constructor receives the persisted code
and centre coordinates. Override `characterKey()` with the same owner-tagged key used
at registration, and declare the secondary ability consistently:

```java
public final class MyRunner extends AbstractPlayableSprite {
    private static final CharacterKey KEY =
            CharacterKey.mod("my-character-pack", "runner");

    public MyRunner(String code, int x, int y) {
        super(code, (short) x, (short) y);
        setWidth(18);
        setHeight(38);
    }

    @Override public CharacterKey characterKey() { return KEY; }
    @Override public SecondaryAbility getSecondaryAbility() {
        return SecondaryAbility.NONE;
    }

    @Override protected void defineSpeeds() { /* safe construction defaults */ }
    @Override protected void createSensorLines() { /* six terrain sensors */ }
    @Override public void draw() { /* renderer installed from baked art */ }
}
```

Runtime physics are resolved with `PhysicsProvider.getProfile(key.persisted())`.
For a patch character, decorate the base module's provider in a `GamePatch`, return
the custom `PhysicsProfile` only for the canonical key, and delegate all other keys,
modifiers, and rules to the base provider. The acceptance sample's
`SampleCharacterPhysicsPatch` is the minimal complete pattern. Keep `defineSpeeds()`
safe for construction, but do not use an `instanceof` branch or a stock character
name as the runtime profile key.

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

Mod characters cannot opt into stock super forms in API 1.2: set
`supportsSuperForm` to `false`. This prevents a custom sprite from transforming with
Sonic's art merely because the stock ring and emerald requirements are met.

## Failure, rewind, and acceptance checklist

Character factories, respawn factories, art/palette suppliers, identity methods,
and ability hooks run through the owning mod's fault boundary. A nonfatal creator
exception records a finding, schedules that owner and its dependents to be disabled
on the next launch, and returns the current session to the title where required.
Keep mutable gameplay state in rewind-capturable instance fields, use injected
services after construction, and never keep gameplay state in mutable statics.

> **Known limitation (rewind, character subclass fields):** the production
> player rewind snapshot (`AbstractPlayableSprite.captureRewindState()` /
> `PlayerRewindExtra`) is a closed, hand-enumerated record with no extension
> point for subclass-declared fields. A custom character's own instance fields
> (for example a double-jump latch) are therefore **not** restored on a rewind
> seek that lands exactly on a keyframe or replays a cached segment; only
> seeks that re-simulate through live input re-derive them. Design ability
> state to be self-correcting on landing/ground transitions where possible
> (the `sample-platformer` Bolt character's latch clears on landing, which
> bounds any staleness to a single airtime). A subclass capture hook is
> tracked in the deferred backlog.

Before distributing a character mod:

1. build and validate the packed jar;
2. select the character from the base game's launch configuration;
3. verify its canonical key, custom physics, art, palette, and chosen archetype;
4. save and reload a team containing the character;
5. rewind across construction and ability use; and
6. disable or remove the mod and confirm the saved slot reports the missing owner and
   falls back safely rather than corrupting the slot.
