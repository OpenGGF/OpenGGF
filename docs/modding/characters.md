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
- `supportsSuperForm` — enables the authored `SuperFormProfile` supplied by `superFormSupplier`;
- `artSupplier` — the full `SpriteArtSet` from a baked playable sheet; and
- optional `paletteSupplier` — the character palette, or `null` to retain the host
  game's palette fallback;
- optional `underwaterPaletteSupplier` — an authored 16-colour water line. The full
  constructor takes this after `movementAbilities`; existing constructors default
  it to `null`. Suppliers have the same owner fault boundary as the dry palette;
- `movementAbilities` — immutable native-move declarations, described below;
- optional `presentationSupplier` — names and non-gameplay image slots; and
- optional `superFormSupplier` — the authored per-host powered-form profile.

When absent, water colours are derived at rendering time using the host zone's
normal and underwater palette pair, including zone changes and palette cycles.
The native games load authored ROM tables: S1 replaces line zero with LZ/SBZ3
Sonic water colours; S2 uses CPZ/ARZ water palettes; S3K selects a zone water
palette and applies character-specific patches. The shared renderer estimates
their per-channel colour shift for custom rows; this is a creator fallback,
not a claim that the ROM transforms arbitrary character palettes.

The maintained [MICA example](../../examples/mica-and-talon/README.md) demonstrates
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

`drawStandardArt()` submits host ROM dust effects and the installed current pose
when visible. Dust uses its own ROM art, independent of the canonical sheet. The host
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

### Movement ability declarations

`CharacterDefinition.movementAbilities` is an immutable set of declared native
moves. Existing convenience constructors declare an empty set; use the full
constructor with `Set.of(CharacterDefinition.MovementAbility.SPINDASH)` when the
actor implements native spindash. The registry intersects each actor's declaration
with its donor's enabled native rules. Stageplay uses that result to offer and
validate route starting actions, without branching on actor or game names. The
contract is extensible to future moves and mod actors; Stageplay's current stock
cast/asset limits still apply.


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

Playable GGFP v2 carries the gameplay sheet; presentation is supplied separately
through `CharacterPresentation`. GGFP presentation sections remain pending.
Mod characters do not receive Tails' separate tail appendage renderer automatically.
Package indexed presentation PNGs beside the GGFP, read them with
`PresentationImages.read(context.modAssets(), relativePath)`, and pass the resulting
presentation from the definition's supplier. `ModAssetRoot.readBounded` loads the
GGFP under the asset limits; `PlayableSheetMaterializer.read` validates it.
See [MicaMod.java](../../examples/mica-and-talon/src/main/java/mica/MicaMod.java),
[MicaPresentation.java](../../examples/mica-and-talon/src/main/java/mica/MicaPresentation.java)
and [build.py](../../examples/mica-and-talon/build.py) for loading and jar layout.

### Canonical vocabulary

The exact enum is [CanonicalAnimation.java](../../src/main/java/com/openggf/game/CanonicalAnimation.java).
Names describe poses, not permission to perform the corresponding move; the
host profile and character ability still own dispatch.

| Group | Names |
|---|---|
| Core movement (shared across all games) | `WALK`, `RUN`, `ROLL`, `ROLL2`, `PUSH`, `WAIT`, `DUCK`, `LOOK_UP`, `SPRING`, `BALANCE`, `BALANCE2`, `BALANCE3`, `BALANCE4` |
| Combat / state | `HURT`, `DEATH`, `DROWN` |
| S1-specific animations | `STOP`, `WARP1`, `WARP2`, `WARP3`, `WARP4`, `FLOAT3`, `FLOAT4`, `LEAP1`, `LEAP2`, `SURF`, `GET_AIR`, `BURNT`, `SHRINK`, `WATER_SLIDE`, `NULL_ANIM` |
| Shared (present in multiple games) | `FLOAT`, `FLOAT2`, `HANG` |
| S2+ animations (introduced in Sonic 2) | `SPINDASH`, `SKID`, `SLIDE`, `HANG2`, `BUBBLE`, `HURT2`, `FLY`, `TAILS_FLY`, `TAILS_FLY_ASCEND`, `TAILS_FLY_CARRY`, `TAILS_FLY_CARRY_ASCEND`, `TAILS_FLY_TIRED`, `TAILS_SWIM`, `TAILS_SWIM_ASCEND`, `TAILS_SWIM_CARRY`, `TAILS_SWIM_TIRED` |
| S3K animations (introduced in Sonic 3&K) | `BLINK`, `GET_UP`, `VICTORY`, `BLANK`, `HURT_FALL`, `GLIDE_DROP`, `GLIDE_LAND`, `GLIDE_SLIDE`, `TAILS_CARRIED` |
| Powered transformation | `SUPER_TRANSFORM` |
| Character-owned strike | `HOMING_DIVE`, `HOMING_DIVE_FORWARD`, `HOMING_DIVE_FORWARD_UP`, `HOMING_DIVE_UP`, `HOMING_DIVE_FORWARD_DOWN`, `HOMING_DIVE_DOWN` |

### Authoring the animation table

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
walk/run, roll or push timing. Other poses require fixed durations. End actions are `loop` (default), `loop-back` with `loopBackSteps: N`, `hold`, or `next`
with an authored canonical `next` target. WALK and RUN share the host's speed
threshold and native slope orientation rules.

`loop-back` repeats the final N authored steps (1 through the script length),
including their full durations; it never counts unique drawings or elapsed ticks.
`hold` keeps the final frame. `next` writes the target animation ID when the
source duration expires; its first frame publishes on the following update.
The binding translates only `next` targets into host IDs and preserves rewind counts.
GGFP v2 ANIM end tags are 0=loop, 1=hold, 2=next, 3=loop-back;
only tag 3 adds an unsigned big-endian 16-bit step count before the next-key string.
This unreleased format has no migration path.

Native ownership: SAnim_End_FE (s2.asm:38430-38437) rewinds from the
terminator and immediately publishes the target frame; SAnim_End_FD
(s2.asm:38440-38446) writes only the animation byte. MICA's 179-step S2
intro repeats the last six steps, matching SonAni_Wait (s2.asm:38702-38714).
TALON repeats 28, matching TailsAni_Wait (s2.asm:41566-41569).
Other native hosts use rewind 2 in SonAni_Wait (_anim/Sonic.asm:181-188),
53 in AniSonic05 (General/Sprites/Sonic/Anim - Sonic.asm:43-49), and
28 in AniTails05 (General/Sprites/Tails/Anim - Tails.asm:49-52). These are
host references, not substitutions for an authored sheet's rewind count.
Finite S2 BLINK/GET_UP hand off to WALK (native ID 0), as SonAni_Blink
(s2.asm:38724) and SonAni_GetUp (s2.asm:38726) specify; creators can use
`next: WALK` for that behavior. `hold` is equivalent to a final-frame-only
native rewind, used by SonAni_LookUp (s2.asm:38718) and SonAni_Duck
(s2.asm:38720). Authored scripts retain their chosen timing on every host.


Optional WALK/RUN `slopes` contains four equal, even-length cycles in native
slope-selector order (0, 2, 4, 6), including the flat cycle first. Their length
must equal `frames`; they require speed timing. Without slopes, the flat cycle
is repeated in all four orientations. Mapping and DPLC references are packed
into strides at materialization; source tiles are shared. Mod sheets have no
implicit native tumble or Super alternate frames.

ROM-free standalone modules have no native ID vocabulary; their default presentation
retains the authored `WAIT` cycle. Native profile binding applies to stock hosts.

Optional fixed-duration strike keys are `HOMING_DIVE_FORWARD`,
`HOMING_DIVE_FORWARD_UP`, `HOMING_DIVE_UP`, `HOMING_DIVE_FORWARD_DOWN` and
`HOMING_DIVE_DOWN`. Both world-owned `HomingAttack` modes select by current
velocity, with normal facing mirroring leftward flight. Measured clockwise from
forward (positive y is down), the sectors are [-22.5°, 22.5°) forward,
[-67.5°, -22.5°) forward-up, [-90°, -67.5°) up,
[22.5°, 67.5°) forward-down and [67.5°, 90°] down.
A nonzero x velocity sets facing in the homing runtime; zero keeps facing.
Tiny x values naturally select vertical poses; a stopped strike selects forward.

Selection falls back from the exact direction to `HOMING_DIVE`, then the native
animation. A sheet supplying only `HOMING_DIVE` retains its existing behavior.
Direction changes preserve the script cursor and timer, immediately publishing
the corresponding pose even during held ticks; differing cycle lengths wrap the
cursor. Keep matching frame counts and durations for aligned directional phases.
Only script execution sees reserved creator IDs: collision retains native Roll.
Landing, hurt, control takeover and completion restore native selection. Direction
is derived each frame; existing world strike and script rewind state suffice.


## Presentation contract

| Surface | Slot or bank | Host |
|---|---|---|
| Names | `displayName`, `hudName`, `supplementaryGlyphs` by `Font(Host, Style)` | S1/S2/S3K HUD and results; S2 half-pipe HUD |
| Life/1-up | `Art.hudLifeIcon`, `Art.oneUpFace` | S1/S2/S3K; life icon also in data select |
| Signs | `Art.signpostFace`, `endSignFace`, semantic `sign` poses/backings | S1/S2/S3K |
| Stage players | `stages`: `S2_HALF_PIPE`, `S3K_BLUE_SPHERE`, `S3K_SLOT_MACHINE` | S1 instead reuses the playable roll sheet |
| Continue | `Art.screenFrames`: `continue/player`, `continue/icon`, `continue/floor`, `continue/team-idle` | Host-specific native frame IDs |
| Portrait | `Art.screenFrames`: `portrait`, frame 5 | S3K data select, including donated menus |
| Snowboard | `Art.screenFrames`: `snowboard`, frames 0–12 | S3K ICZ rider |
| Endings | `endings`: `EndingSpriteSet.Surface` | S1/S2/S3K; selectors listed below |

The optional `CharacterDefinition.presentationSupplier` constructor argument follows
`underwaterPaletteSupplier` and precedes `superFormSupplier`. It receives the canonical persisted character key and
runs inside the character owner's fault boundary, like the art and palette
suppliers. Existing constructors default it to `null`.

`CharacterPresentation` carries an optional display name, an optional short HUD
name (up to five characters), indexed images per host, and supplementary glyphs
keyed by host and letter style. `Art` has independent HUD life icon (16×16),
1-up face, signpost face and end-sign face slots. Null names and images mean native
fallback. `Image` uses palette indices 0–15, with zero transparent; it copies pixel
arrays on construction and access. Presentation maps are immutable snapshots.
`Art.sign` optionally supplies semantic face, alternate, spin1, edge and spin3
poses. Each pose has a character-palette face and optional native-palette backing;
backing RGB colours are matched to its declared host palette line (0–3). Spin
images are already oriented. Null poses retain native frames and poles remain
native. The four-argument `Art` constructor remains supported.
`com.openggf.mods.assets.PresentationImages` reads indexed PNG assets through `ModAssetRoot`, bounded to
256×256 and at most the smaller of 256 KiB and the root asset-byte limit;
MICA packages its HUD, monitor, sign and supplementary S1 HUD glyph assets this way.

S1, S2 and S3K consume the HUD life icon/name, act-results name, special-stage
results name (S2 including the Super announcement; S3K Blue Sphere) and 1-up
monitor face slots. HUD names use each host's small font inside its native 32×16 cell; the short
name is preferred when supplied. Results use the host title/results alphabet.
Absent slots, names with unsupported glyphs and HUD names that do not fit
retain the native presentation. 1-up faces must be 16×16; other sizes retain the
native face. Stock characters without a supplier keep their native tiles and
mappings. The internal name composer reports native, supplementary and missing
glyph coverage and extracts pixel crops across tile boundaries. Title-font readers
for all three hosts and small HUD-font readers use the ROM pipeline. The S3K reader
inventories each zone separately, including shared ZONE letters and pixel crops
within multi-letter pieces, plus the results-name banks. Native glyphs take
precedence over supplements: MICA needs supplementary M and A only in the S1 HUD
font. Stock S2 results select Miles/Tails for Tails alone using the native
graphics-region setting. Sign objects in all three hosts consume the optional
face, spin and backing poses, retaining native poles, timing and fallback frames.
`CharacterPresentation.stages` optionally supplies `StageSpriteSet` entries for
`S2_HALF_PIPE`, `S3K_BLUE_SPHERE` and `S3K_SLOT_MACHINE`. Each entry maps the host's
native mapping-frame ID to a bounded indexed image and its hotspot. Animation,
movement, jump projection and flips stay with the native stage owner. Empty maps
retain stock art; supplied sets must cover every frame used by their archetype.
S1 instead binds the ordinary canonical playable sheet to its native roll scripts.
The stage player uses the character palette supplier in an isolated render-context
row; native palette lines remain available to stage art and stock sidekicks.
MICA packages the wave3 PNG sets through `PresentationImages` and `ModAssetRoot`.

`Art.screenFrames` optionally supplies immutable indexed poses with hotspots and
sixteen RGB colours. Continue consumers use `continue/player`, `continue/icon`
and the S1 `continue/floor` / S3K `continue/team-idle` banks, keyed by native
mapping frame. They retain the native animation and movement owners. Separate
screen palette lines preserve native text and Tails; omitted banks/frames fall
back to ROM art. Existing four/five-argument `Art` constructors remain supported.
The S3K `portrait` bank (frame 5) supplies the data-select bust, composed with
stock team portraits using its authored hotspot. Its palette is isolated in a
renderer-owned graphics bank; no native menu line is replaced.
Data-select saved-slot life counters also use `hudLifeIcon` with the character's
palette; miniature continue counters reuse S3K `continue/icon` frame 0 when its
visible pixels fit the 16×24 menu figure cell. Native crosses/digits and every
stock fallback remain unchanged. NO SAVE uses the same solo/team portrait
composer and refreshes immediately when the selected team changes.
The S3K `snowboard` bank supplies the 13 native rider mapping IDs (0–12).
The host retains intro timing, movement, separate board/spray and snow occlusion;
private renderer palettes keep rider and native effects independent.

`CharacterPresentation.endings` adds optional `EndingSpriteSet` entries. S3K
supports `S3K_NORMAL_POSE` / `S3K_POWERED_POSE` reunion poses, `S3K_PASSENGER`
tiny passengers, the `S3K_EYECATCH` Sonic trio-eyecatch child, and
`S3K_PLAYER` / `S3K_POWERED_PLAYER` ending frames. Frames retain native mapping IDs
and hotspots; missing entries fall back to the native host renderer. Indexed
images are bounded to 256×256 pixels to include the enlarged reunion art. Each
set carries sixteen RGB colours; the renderer keeps it on a private palette
row. Powered player/reunion frames use the live authored form palette. Vehicle,
companion, banner, choreography and script timing stay native. Existing four- and
five-argument presentation constructors retain empty ending maps.

S1's ending actor consumes `S1_ENDING`; absent slots retain its ROM art. Images
are bounded to 256×256 to cover the native leap close-up. Shared native palettes
remain intact for the emeralds, flowers, logo and companions.

S2 consumes `S2_ENDING` (normal ObjCF selectors), `S2_SUPER` (powered
ObjCF selectors), `S2_FLOAT` (native Float2 frame IDs), and `S2_PHOTOS` (0–3).
Photo panels are complete solo illustrations. Normal rider overlays contain no
vehicle pixels: the host retains the native Tornado pieces and separate Tails
pilot. Normal and Super paths retain their native choreography. Powered poses
use the character's S2 Super profile palette cycle; native fade steps also apply
to the private rows. The existing KiS2 presentation keeps its own route behavior.

Palette ownership follows `Pal_AC7E / Pal_AC9E / Pal_ACDE / Pal_AD1E / Pal_AD3E`
(docs/s2disasm/s2.asm:14332-14336) and the line-1 rider versus line-0 vehicle
pieces in `Map_objCF_0132` (docs/s2disasm/mappings/sprite/objCF.asm:80-89).
Private rows preserve all of these ROM-owned colours, including the native Tails
pilot drawn separately from MICA's solo photo panels.

S2's half-pipe ring HUD composes the short/display name from its own native
SONIC/MILES/TAILS letters in a 40×8 cell, with HUD-style supplements only for
missing letters. Stock and unsupported names retain their native art.
GGFP presentation sections are pending.

## Native ground abilities

Declare `CharacterDefinition.MovementAbility.SPINDASH` in the definition's
movement-ability set to use the host's native charge/release path. The registry
intersects declarations with native host capabilities: MICA can spindash in S2
and S3K; S1 retains its native no-spindash rule. A canonical sheet should supply
SPINDASH poses. Canonical sheets enable native deep-wait Blink/GetUp interrupts
only when those scripts are authored; a looping WAIT fallback cannot finish an
input-locking native interrupt.

## Ability and super-form rules

Characters can own the world-session homing capability independently of Mutator Lab.
Import `com.openggf.sprites.playable.HomingAttack` and return
`HomingAttack.start(this, HomingAttack.Mode.HOMING)` from `onAbilityActivate`.
Choose `Mode.INSTAGIB` per call for a Super/Hyper strike; no menu setting or native
character identity is required. A target uses native contact/defeat rules, refreshes
the air action for chaining, and retains the existing effects and camera lag.
Without a target, `start` dashes forward. Use `startIfTarget(this, mode)` to
return false without dashing when a native air move should get the press instead.

Override `protected void onMovementTick()` for per-tick character behavior. It runs
inside the character owner's fault boundary before movement input and homing preview
selection (live movement ticks, excluding death). Use the subclass rewind hooks for
mutable state. For a normal homing preview, call
`HomingAttack.requestReticle(this, getAir())`; the runtime gates availability and
keeps the target locked during the strike.

For a flight move, call `HomingAttack.requestReticle(this, downHeld)` from `onMovementTick()`, and `HomingAttack.divebomb(this, mode)` on the down+jump activation. Release
the request with `false` to hide the reticle, including during a strike. The dive
uses the previewed live target; without one it dives steeply forward-down. The
facade accepts a free airborne controlled leader, including flight without native
`jumping` state, and rejects a spent air action, grounded, hurt, dead, controlled or
locked player. It does not grant flight or intercept native flapping input: the
character owns that dispatch. Returning false leaves native air moves available.

The runtime, air-action latch and plain-value rewind snapshot belong to the world;
characters must not cache engine targets or serialize the runtime. Other character
state still follows the subclass rewind contract below.


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

Opt in with `supportsSuperForm = true` and the final `SuperFormSupplier` constructor
argument (after the optional presentation supplier). The supplier runs through the
registered owner fault boundary. It returns an immutable `SuperFormProfile` mapping
host `GameId` to supported `SUPER` and optional S3K `HYPER` tiers. Existing constructors
remain available and default to no profile. Enabling a mod form without a profile
is rejected.

Each tier supplies 1–256 fade rows and dry-cycle rows, an optional underwater
cycle of the same length as the dry cycle, ticks per frame in 1–256, a physics profile, a canonical transformation
animation and optional canonical animation overrides. Rows are sixteen packed
Mega Drive colour words. Missing overrides preserve the normal sheet; Gilded is a
palette transformation. Missing water rows use the live host-zone derivation.
Hyper trails use the character's own renderer and rewind-captured position history;
stars are opt-in. Neither Sonic's palette nor his alternate mappings are installed.

S2 defaults to seven emeralds and 50 rings, its jump-apex trigger and native drain.
S3K uses the explicit air-ability trigger, seven Chaos Emeralds before conversion
for Super, and seven Super Emeralds for Hyper, retaining the native shield and
invincibility gates. S1's six-emerald, 50-ring form is **mod-defined**, borrowing S2's
trigger and drain with S1 invincibility music. S1 has no native Super form; this
path needs no donor ROM. Stock character controllers are unchanged.

The MICA example registers `GildedSettings`: enable its settings override in the
host settings overlay to edit per-game form switches, S1 emerald requirement,
ring requirement and S1 music. These existing `MutatorOption` widgets admit at
LOAD. The small `CharacterSuperFormPolicy` extension is necessary because existing
policies cannot address a character's authored form. The host accepts a policy
only for a matching owner and local character; inactive overrides retain profile
defaults. No creator callback receives world mutation authority.

An ability can read `isSuperSonic()` (including the transformation) and
`getSuperStateController().isHyperFormActive()`. MICA selects
`INSTAGIB` when `isSuperSonic()` is true. The controller captures palette cursor,
phase, display frame, tier and ring drain for whole-player rewind.

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
  `PerObjectRewindSnapshot.PlayableSubclassRewindExtra`
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


## Current limits

S2 competitive 2P presentation, title/branding art, slot-reel prize symbols and
custom Tornado pilots are outside the delivered character presentation surfaces.
The slot-machine *player* set is delivered; its reel symbols are separate art.
GGFP presentation sections are pending: keep using bounded sidecar PNG assets.
MICA is a framework demonstration, with TALON planned for phase 2. Focused
acceptance and surface captures do not establish complete title-to-ending routes.
For a build-along, see [an original character across three games](guides/original-character.md).

### Object-owned native mapping poses

Playable YAML optionally supplies `nativeFrames`, a map from semantic pose keys
(for example `hang/0`) to authored mapping indices. The GGFP v2 `NFRM` section
retains these aliases. Each host declares its native bytes through
`DonorCapabilities.getNativeFramePoses()`; shared binding consumes semantic keys
and canonical fallbacks rather than game-name branches. The current stock host
vocabularies describe the normal Sonic archetype; character-specific Tails and
Knuckles raw mapping vocabularies are not yet supplied. Missing poses use the
host descriptor's canonical fallback; stock art bypasses binding.

When aliases are supplied, bound canonical scripts move above the 256 native
byte slots, including slope sets and DPLCs. Index zero stays invisible for native
cutscenes. Creator code should use the installed scripts rather than cache the
unbound sheet's mapping indices. Authored native aliases remain in byte slots.

The AIZ intro actor also binds the custom normal sheet. Its riding and lift-off
native mappings use aliases; its separate powered bank falls back to canonical
ROLL with the authored S3K Super palette cycle. The native intro gate follows
the registered Sonic archetype, including custom Sonic stand-ins.


### Tails archetype and flight extensions

A definition with `behavesLike = TAILS_ALONE` receives native Tails gameplay
branches (including touch attacks, object geometry and rolling rules), without
receiving the native appendage renderer or Super Flickies. Use
`SidekickRespawnStrategy::tails` for native fly-in/CPU recovery when following.
Declare `SecondaryAbility.FLY` for the shared flight model. S3K enables it
natively; `MovementAbility.CROSS_GAME_FLIGHT` explicitly enables the same model
in S1/S2 as a mod-defined extension. `SUPER_FLIGHT_LIFT` doubles the native
flap acceleration only while the authored super form is active. Neither
extension changes stock Tails.

The full `CharacterDefinition` constructor accepts an optional `defaultSidekick`
key. It must share the leader's owner and cannot accompany `SONIC_ALONE`.
Selecting that leader in the launch panel selects its available default
sidekick; users may still choose none for solo play. Old constructors retain
solo defaults. Registered mod characters are selectable as sidekicks as well
as leaders. A host that lacks sidekicks still needs explicit module support.
The MICA patch supplies that support in S1.

The TALON example uses its own sheet, palette and Solar tables. Holding down
while airborne requests the homing reticle; down+jump starts a divebomb
(Instagib while Solar), and jump alone flaps normally.

### Shared animation families

Normal Sonic scripts in each stock module publish `DonorCapabilities.getAnimationFamilies()`.
Each binding names a canonical animation, its drawing family and an ordered list
of phase numbers. `family-float2/0`, for example, is a normal `nativeFrames` alias
in the playable sheet. Supply each phase once; several host scripts may reuse it.
Keep the canonical script table for movement, custom timing and missing-family
fallbacks. No container version or canonical enum ordinal changes are required.

Host-aware binding reads delay, repeated step order, LOOP_BACK count and SWITCH
from the host's ROM-loaded script. A family binds only when all its phases are
supplied. Native stock art bypasses this path. Directional ledge balance families
remain distinct: BALANCE/BALANCE2 face the edge; BALANCE3/BALANCE4 face away,
with separate near-edge entry and wobble phases. FLOAT–FLOAT4 share FLOAT2;
SLIDE/WATER_SLIDE share HURT_FALL; GET_AIR shares BUBBLE. Numeric phase indices
are explicit module correspondence, never interpolation by cycle length.

Cross-game Sonic donation uses these same correspondences. Missing donor script
names borrow family phases into host-timed scripts in a private ID namespace;
typed `AnimationId` and canonical requests select them. Raw integer animation
writes retain their existing meaning. Unused native dash slots are N/A, not
proof of a leap pose. Warp, ending leap/surf and shrink retain compatibility
fallbacks where a donor lacks their family. Tails-archetype creator art selects a separate native vocabulary through
`DonorCapabilities.forPlayableCharacter(TAILS_ALONE)`. S2/S3K providers publish
Tails' own phase counts, repeated steps and LOOP_BACK tails; the binder retains
the loaded Tails script clocks and terminators. Native stock characters and
cross-game Sonic donation continue using the original unscoped vocabulary.
Hosts without a native Tails route retain their default pose vocabulary.
Creator flight can ask `TailsFlightController.selectAnimation(carrying)` for the
same native flight/swim branch without duplicating fluid, fatigue or carry rules.
When installing private scripts, offset source mapping indices by the native-frame
alias reservation; never overwrite every variant with a normal-flight script.

A binding may omit a canonical animation name while still defining its family:
Sonic's native carried-exit script shares TAILS_CARRIED phases and retains its
own finite terminator. A null family means explicit N/A. Normal Sonic's legacy
FLY slot belongs to WAIT's lying phases; Tails' actual flight remains separate.

Bindings can also declare a small mapping offset when native scripts reuse the
same drawing at a different anchor. The binder creates mapping views sharing the
same tiles and DPLC, so these variants require no duplicate sheet drawings.
