# Shared creator helpers

Use these helpers for the repeated mechanics in a mod, then keep its rules,
styling and choreography in the project. They belong to the mutable Mod API
candidate. Build against the SDK and engine from the same creator kit; start with
[setup](../getting-started.md), [scene lifecycle](mod-scenes.md) and
[production-backed tests](../testing.md).

## Scene UI and level overlays

`SceneCanvas` implements `PixelCanvas`: both draw logical-pixel ARGB rectangles.
`LevelOverlayCanvas` supplies the same small drawing surface during a level's
render pass. Its commands use screen coordinates and clip to the declared logical
viewport. It does not move the level camera. Construct it with the engine-provided
`GraphicsManager` from the current services and logical viewport dimensions, then
draw during the mod's render callback. Rectangles and fonts also work during CPU
sprite preparation and deferred frame replay, preserving screen positions,
clipping and alpha as the camera or rendering viewport changes.
Frame and gradient coordinate offsets reject integer overflow before drawing any
part of the shape.

```java
PixelCanvas ui = canvas;
UiPrimitives.gradient(ui, 8, 8, 120, 24, 0xFF244466, 0xFF102030);
UiPrimitives.frame(ui, 8, 8, 120, 24, 0xFFFFFFFF);
CompactFont.shadowed(ui, "READY", 16, 16, 1, 0xFFFFFFFF, 0xFF000000);
UiPrimitives.meter(ui, 8, 36, 120, 6, health, maximumHealth,
        0xFF222222, 0xFF44DD66);
```

`CompactFont` is a code-drawn 5×7 font with six-pixel advance and scale 1–4.
`width`, `fit` and `glyphs` share the same metrics; lowercase uses uppercase glyphs
and unsupported characters use `?`. `draw`, `shadowed` and `outlined` work on
either canvas. Use `glyphs` when an existing mod command renderer needs the
horizontal pixel runs directly. Golf and Sitar keep their color/style decisions
while sharing those glyphs.

For an authored HUD face, `BitmapFont.binary(alphabet, bits, width, height,
advance)` retains row-major `0`/`1` pixels with exact character case. It shares
measurement, cached rectangle emission and `PixelCanvas` drawing without
replacing the mod's design. Unknown characters advance without drawing. Keep the
immutable face on the owning UI/controller; select uppercase or a special
lowercase glyph in the mod. Infinite's 3×5 face and Survivors' distinct letter and
damage-digit faces use this path. Alphabets are bounded to 256 characters, glyphs
to 32×64 pixels, advance to 128 pixels and scale to 1–8.
Geometry is prepared once per face. Identical spans on adjacent rows share a
rectangle, and every lit pixel is covered exactly once so translucent text keeps
its intended color.

`AtlasFont` keeps an original, variable-width font in one white scene atlas.
Supply UTF-8 blocks with a `= A` header and exactly the chosen number of rows;
`#` is a white pixel and `.` is transparent. Separate glyphs with a blank line.

```text
= A
.#.
#.#
###
#.#
#.#
```

Parse once with `AtlasFont.parse(bytes, 5)`, measure with `font.width(text)` and
draw with `font.draw(canvas, text, x, y, argb, scale)`. The parser rejects duplicate
glyphs, inconsistent dimensions and malformed rows. Font input is bounded to
1 MiB, glyph dimensions to 64 pixels and atlas width to 4096 pixels. Scene draw
scale is 1–8. Tower Defence and Slay retain their authored fonts through this
parser; their markup and card/button styling remain project code.

`TextLayout` accepts a `Metrics` callback, so it works with either font:

```java
var lines = TextLayout.wrap(description, 180, text -> CompactFont.width(text, 1));
String label = TextLayout.ellipsis(title, 100, font::width);
int x = TextLayout.alignedX(left, areaWidth, font.width(label),
        TextLayout.Alignment.CENTER);
```

Wrapping preserves explicit blank lines. A single word wider than the area stays
whole; use ellipsis where clipping is preferable. Alignment uses logical pixels
and does not impose a layout system.

`FocusRegions` owns ordered identities and rectangular hit areas for one menu.
Replace the regions when the layout changes; an enabled focused identity survives
that replacement. Disabled entries cannot receive focus. Directional movement
selects the nearest enabled region in that direction; next/previous wraps in
declared order. The last enabled drawn region wins overlapping mouse hits.
The Tower Defence title menu uses this shared navigation while retaining its
screen transitions and button actions. Map keyboard/pad input through
[named actions](action-bindings.md), then call `move`, `hit` or `focus`.

## ROM art without repeated decoding

Get the ROM handle from the scene's art service. Named `StockSceneArt` recipes
are qualified by game and exact ROM SHA-1. The supplied catalogue currently covers
eleven commonly reused S3&K locked-on banks, including rings, Robotnik's ship,
the capsule, explosions and several badniks. A different ROM or an unknown
identity fails before using its offsets. User-supplied ROM bytes remain the
source of every decoded bank.

`PaletteAssembly` copies authored colors and bounded ROM palette reads into four
16-color lines. `build()` returns a fresh array. Use the recipe's required palette
line and the object's actual palette; a named art recipe does not select a zone
palette for you.

```java
int[] palette = new PaletteAssembly()
        .line(0, myObjectColors)
        .line(1, myRingColors)
        .build();
SceneArtCache cache = new SceneArtCache(rom, 16);
SceneSpriteSet rings = cache.sprites(StockSceneArt.S3K_RING, palette);
```

A `SceneArtCache` belongs to one scene and ROM handle. It keys banks by the full
request and a copied palette, retains 1–128 banks and evicts the oldest bank when
full. This bounds bank count; decoded bank sizes vary. Repeated identical reads
reuse a bank, and later edits to the caller's palette cannot change its key.
`sprites(RomSpriteRequest, palette)` retains the expert path for art outside the
named catalogue. Mapping tables with reordered or shared frame data can use
`request.withMappingFrameCount(count)` (1–512) instead of inferring their size
from the first pointer. The named Flicky recipe uses the three pointers declared
by `Map_Animals1`; its first frame is stored after another frame.
Close the cache in `exit`; it releases its references while the
host continues to own image/texture lifetime. Hello Scene and Slay show the
lifecycle, palette assembly and named/raw recipe mix.

`SpriteAnchors.feet` places a sprite on a ground line using its origin, dimensions
and scale. `centre` centers its image while respecting both mirror axes. `feet` retains the
ground line under vertical mirroring. These
avoid repeated placement arithmetic. Object children, composite bosses and other
multi-part poses still need the mod's own arrangement.

## Choose an animation policy explicitly

For a decoded frame list, `AnimationSampling.frame(set, animation, ticks, timing)`
uses explicit scene timing. `Timing.fixed(4)` displays each entry for four ticks.
`Timing.rom(4)` uses the decoded delay except for speed-driven values above 253,
where the specified scene delay applies. `still` selects the first entry.
Negative ticks use floor-based sampling, so seeking and replay stay deterministic.

```java
SceneSprite frame = AnimationSampling.frame(rings, 0, sceneTicks,
        AnimationSampling.Timing.fixed(4));
SpriteAnchors.feet(canvas, frame, centerX, floorY, SceneDraw.plain());
```

The full `Timing` constructor exposes a fixed delay, long-delay threshold,
replacement delay and native-delay adjustment. Hello Scene preserves its earlier
extra authored tick through that adjustment; Slay preserves its own existing
long-delay policy. Choose one policy for the visual being sampled instead of
adding unexplained `+1` delays at call sites.

For a raw S3&K ordinary-object animation table, `RomAnimationPlayer` executes its
delay-plus-one cadence and `FF` loop, `FE` back, `FD` animation switch and `FC`
routine-advance commands. Capture and restore its immutable state alongside the
owning scene/object. Invalid pointers, jumps or unsupported commands fail
explicitly. Speed-driven playable-character animation and game-specific
choreography remain separate policies; Slay's wrapper shows that distinction.
The player retains the complete nonnegative mapping-frame byte; supply the
object's facing through its draw options.

## Verify an adoption

Keep a regression at the behavior boundary: measured text/glyph runs, focus after
a layout replacement, pixel palettes and sprite origins, the native animation
sequence or input transition edges. The maintained examples use these helpers
without moving their game rules into the engine. Use the distributed
[testkit](../testing.md) for packaging, owner faults, deterministic input and
recorded draws; use the level testing standard for full gameplay rewind.

## Gameplay assembly and immutable edits

`StandaloneGameSpec` assembles a no-ROM game's ordinary providers. Declare zones
with ordered `Act` records containing a level index, authored `Level`, start
position and `MusicReference`; the builder supplies core load/reset behavior,
empty object/touch tables, native-silent audio and a default progression snapshot.
Supply your own object registry, physics, audio, placement encoding, load profile
or save provider where the game needs them. `AbstractStandaloneGameModule` and
provider overrides remain available for expert assembly. The standalone and
platformer gallery projects demonstrate the declarative path.

`DelegatingLevel` and `DelegatingZoneRegistry` forward their contracts. Override
only the operations your mod changes. Survivors uses these for its arena views;
it no longer needs a parallel copy of every forwarding method.

`PhysicsProfile` remains an immutable value. `toBuilder()` copies every field,
`withMax(speed)` edits only maximum speed, and the builder groups movement,
slopes, rolling, shape and balance settings. Profile speeds use the engine's
fixed-point units. `PhysicsProvider.transform(predicate, edit)` scopes an edit
to selected characters and preserves the source's modifiers, semantic rules and
optional initialization profile:

```java
PhysicsProvider physics = base.getPhysicsProvider().transform(
        "sonic"::equals, profile -> profile.withMax(0x0C00));
```

Infinite uses `withMax` inside its forwarding `CoursePhysics` wrapper instead of
reconstructing the full record; it does not use `PhysicsProvider.transform`.
To define a new playable character, pass `CharacterPhysicsSpec` into its
`AbstractPlayableSprite` constructor. It combines one profile and a
`PlayableSensorSpec`; the profile's standing/rolling shape drives the same
simulation and sensors. Override `onLanded()` for simulation-owned airborne-to-
ground transitions and `onLevelReset()` for a fresh load/respawn reset. Snapshot
hydration does not replay these gameplay events. Bolt uses the landing callback
to replenish its authored double jump.
Create a fresh sprite in each character-factory call. The
[character guide](../characters.md) explains factory, art and respawn ownership
for both registered characters and expert module registries.

`LevelPatch` describes immutable operations over decoded object placements.
Select placements by predicate, then `remove`, `move`, `replace` or explicitly
`bind` native placements to a registered owned factory. Replacement preserves
the placement slot and owner/object-key pair; duplicate placement identities and
ownership-changing replacements fail. Register the operation with
`ModContext.decodedLevelPatch` so application uses the production owner boundary
and decoded-load contract. Golf uses this path for course placement changes
while preserving stock geometry and loading.

## Captured state and persistent settings

`ModStorage` supplies bounded UTF-8 `read`, `write`, `delete` and `list` operations
for a verified owner. Gameplay modules obtain it from their registration context;
scenes use the compatible `SceneStorage` interface. Both reach the same
`saves/mods/<verified-owner>/` namespace. Single files are limited to 1 MiB,
names to the accepted local filename syntax, and paths cannot cross into another
owner or stock save namespace. A write/delete result reports success; a read can
be absent. Check the result when persistence affects the player's decisions.

Legacy scene files retain the eligible old owner-directory read fallback, then
new writes use the canonical directory. Built-in game namespaces are excluded
from that fallback. Updating or uninstalling a jar does not erase owner data.
Survivors retains its existing profile format through the owner-storage migration.
Infinite's leaderboard remains on its legacy
`SavePaths.root()/infinite-sonic/leaderboard.txt` path; it has not migrated to owner storage.

`VersionedSettings` is an immutable ordered `key=value` document. Unversioned
legacy text remains version zero; `empty(1)` declares `formatVersion=1`.
Use `requireVersion` before editing a known format, then `with` and `serialize`.
Decide the migration in the mod instead of overwriting an unknown future format.
This helper does not add file I/O or silently migrate values.

`SnapshotRandom` supplies the xorshift64* stream used by Survivors and captures
its complete state as one `long`. Restoring that value reproduces the following
sequence. Zero remains an absorbing seed; choose the game's seed fallback
explicitly. Bounds zero and one consume no randomness, preserving the existing
selection policy. This is for authored mod state; use the game RNG service where
the behavior belongs to native gameplay.

`CapturedPool<T>` keeps a bounded ordered collection with explicit snapshot-copy
semantics. Adding beyond capacity expires the oldest entry. Supply a copy
function for mutable values, or `value -> value` for immutable values; snapshot
and restore copy every entry through it. Restore validates/copies the complete
replacement before changing the pool. Golf uses it for its captured feedback
trail without changing the old snapshot shape. Keep gameplay lifecycle and
recreation in the owning object/controller; a pool alone does not make a manager
rewind-safe.

## One service and rewind graph per application

Register a `GameServiceBundle` factory when several consumers share mutable
state. The factory runs for each module application, so restarting or resolving
a second module creates an independent graph. Construct the state once inside
that factory, then publish the same instance to its consumers:

```java
context.registerServiceBundle("run", () -> {
    RunState run = new RunState(context.storage());
    return GameServiceBundle.builder()
            .capturedService("state", RunState.class, run)
            .build();
});
```

Here `RunState` is the mod's own `RewindSnapshottable` implementation. Its local
adapter key is namespaced by the verified owner and bundle identity. For an
alternative gameplay loop, `frameController("state", controller)` publishes one
`GameplayFrameController` and its captured state together. `service` adds an
ordinary service, `rewindAdapter` adds captured state, and `dynamicService` looks
up a service that changes during play, such as the active arena. Service lookup
uses the actual `Class` identity, preserving isolation between different owners'
equally named classes. Golf and Survivors demonstrate shared controllers/run
state and owner storage.

Keep controller bookkeeping outside a course checkpoint when restoring it would
erase the mode's current ledger. The production course capture honors the shared
underlying identity of the owned controller and adapter. Capture game-world
state through the normal session registry, including native random state, then
verify forward replay. A bundle wires ownership and lifecycle; the mod still
defines its state and restoration semantics.

## Object lookup, reconstruction and save inputs

`services().objectQuery()` supplies read-only membership snapshots for the current
live object set. `activeObjectsOfType(type)` has stable captured ordering;
`identityOf(object)` returns its `ObjectRefId`, and `resolve(identity)` finds the
current instance after rewind recreation. Discard saved lookup handles at a
level or timeline reset, which can reuse native ordinals. The objects themselves
retain their supported gameplay operations.

Implement `ModRewindRecreatable` for the ordinary creator reconstruction path.
Its `ObjectReconstructionContext` exposes the captured spawn, state, typed
payload, injected services and object/player queries. Recreate the instance from
those inputs; captured references reconnect in the normal second restore phase.
Native player-bound objects can retain deferred reconstruction through
`enqueuePendingPlayerBoundEntry`. Flappy and the gameplay examples show this
path. The existing expert reconstruction contract remains available.

Save providers receive one immutable `RuntimeSaveContext` with the current zone
key, act, lives, continues, emerald state and selected save/team information.
`captureRuntimeFields(zoneState)` supplies optional game-owned JSON inputs at the
save boundary. The host freezes nested string-keyed maps and lists before
`capture(reason, context)` reads `capturedFields()`. Describe the custom payload
and its version in the mod, and use `restoreProgress` for supported custom
progress fields. Test snapshot independence from later live changes, save/load,
missing-owner recovery and failures inside the provider's owner boundary.
