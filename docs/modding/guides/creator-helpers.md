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

`CompactFont` is a code-drawn 5×7 font with six-pixel advance and scale 1–8.
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

The default parser normalizes headers and lookup to uppercase, and unknown
characters advance without drawing. To retain distinct upper/lowercase artwork,
use `AtlasFont.parse(bytes, height, AtlasFont.GlyphCase.EXACT_FIRST, '?')`.
Lookup tries the exact character, then uppercase, then the specified fallback.
A nonzero fallback must exist in the face; `'\0'` retains blank unknown-character
advances. Spaces keep their normal advance, and measurement omits the final gap.
Three Islands uses this overload while retaining its authored font pixels,
wrapping, colors and shadows.

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

## Native object recipes and callback composition

`CaterkillerJrFactory.create(spawn, CaterkillerJrRecipe.STOCK)` retains the native
six-body roles and delays. `DOUBLE_LENGTH` uses twelve explicit native roles and
delays. The returned badnik remains native; recipes and exact links restore with
its graph. The preset belongs to `com.openggf.game.sonic3k.objects.badniks`;
`Sonic3kMonitorFactory` belongs to `com.openggf.game.sonic3k.objects`.
`Sonic3kMonitorFactory.eggman(spawn)` retains the placement and native
shell, contents, hurt, respawn and rewind behavior without subclassing native AI.

Use `Sonic3kBadnikConfig.destruction()` with `DestructionEffects.defeatBadnik` for the native score,
points popup, deferred animal and destruction order. A no-popup animal recipe
is a different effect. `ObjectCombatRules` exposes admitted native targeting,
firing and cooldown decisions. `PoweredScreenAttackable` supplies the small
semantic callback; stock collision category/property eligibility still precedes it.

`ShieldProjectileMotion.awayFrom(dx, dy)` returns the native radial velocity.
`NativeObjectFieldTarget` supplies semantic body/projectile centres and displacement;
opt into grounded field motion only when the object supports it. Field queries,
displacement and powered callbacks run under the receiving object's verified
content owner. A callback fault quarantines that owner and aborts the current
frame through the existing host recovery boundary; another mod's caller does not
inherit the fault. Healthy gameplay resumes after host retirement and reassembly,
not by continuing a partially updated frame. Creator budding
reuses the registered factory under its exact content owner, with native slot,
floor, lifetime and replay bounds; implementing a callback does not enable every
mutator or grant native AI identity. Publish `ObjectPresentationMetadata` for body
transforms. Draw attached flames/effects through `services().renderAttachedEffect`
so they retain their own presentation subject, deferred palette replay and enclosing body state.
`TitleScreenProvider.drawOverlay(PixelCanvas)` draws a title badge on the host's
current canvas without finding global renderer services.

## ROM art without repeated decoding

Get the ROM handle from the scene's art service. Named `StockSceneArt` recipes
are qualified by game and exact ROM SHA-1. The supplied catalogue currently covers
fifteen commonly reused S3&K locked-on banks, including rings, Robotnik's ship,
the capsule, explosions, monitors, AIZ rocks, several badniks and the players' dash dust
(`S3K_DASH_DUST`: the spin dash cloud and skid puffs, streamed through the player
DPLC layout on palette line 0; play its `Ani_DashSplashDrown` scripts, read from
the ROM at `$18DC0`, with `RomAnimationPlayer`). Skid child frames `$11–$14`
issue no DPLC loads: `Sonic_MoveLeft/Right` first selects cue `$15` on their parent,
which preloads one shared tile bank. Read that cue from the ROM and use a separate
`RomSpriteRequest.uncompressed` for its bank and the same mappings when drawing
those four children. Decoding their empty cues in isolation produces blank puffs. A different ROM or an unknown
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
ground line under vertical mirroring. These avoid repeated placement arithmetic.

For a multipart pose, build a `SpriteComposition` from an ordered immutable list
of `Part(sprite, x, y, flipX)` values. `place(x, y, style)` prepares the complete
pose, respecting each sprite origin, part flip, whole-pose flips and independent
X/Y scaling. Its `Placement.bounds()` supplies detached geometry before drawing;
`Placement.draw(canvas)` emits only those prepared parts in the original order.
Slay and Three Islands use this shared geometry while retaining their own boss
recipes and animation. Prepare interaction geometry during update so skipping or
repeating a draw cannot change gameplay.

`SceneCanvasTransform` supplies scoped translation and opacity without a parallel
canvas implementation:

```java
try (var shifted = new SceneCanvasTransform(canvas, offsetX, offsetY, opacity)) {
    shifted.clip(0, 0, panelWidth, panelHeight);
    drawPanel(shifted);
}
```

Nested transforms compose translation and opacity and restore the previous clip
when closed. Closing a parent revokes its descendants. Keep them inside one draw
callback and close them in reverse order. `clear` still covers the full viewport;
custom canvas implementations must report their current `clipBounds()` for clip
restoration. Sitar uses this helper for its shifted presentation.

## Provider title-card presentation

`TitleCardPresentation` evaluates positions for the provider's four title-card
pieces: banner, name, ZONE and act. Call `position(element, arrivalElapsed,
exitElapsed)` with indices 0–3, add `(canvas.width() - 320) / 2` to X and draw only
frames the provider supplies. Skip absent/null pieces. Arrival moves 16 pixels
per row; release moves 32, staggered at 1/3/5/7. Keep `exitElapsed` zero for an
unlimited loading hold and start it at 1 on explicit release, or use
`automaticExitElapsed(elapsed)` for the default 28-row arrival/90-row hold.
`durationFrames(width)` supplies Flappy's existing width-dependent wait.

The caller owns rendering, its clock, hurry/completion policy, replacement name
and optional act display. The helper owns no images or scene lifetime. Flappy,
Sitar and Slay share these positions without sharing their release policy.

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
Use `.object(key, factory)` for owned factories: the builder supplies known-key
lookup, creation and browsing consistently and rejects duplicate keys or a
conflicting custom registry. Use the canonical identity bound by the factory;
do not derive ownership from runtime class names. Supply your own registry,
physics, audio, placement encoding, load profile or save provider where the game
needs them. `AbstractStandaloneGameModule` and
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

For native campaigns that depend on a concrete decoded level (such as AIZ's fire
reload), opt into `nativePlacements()`. This path admits verified factory bindings
and `retainRings((rings, objects) -> ...)` while keeping the same native level.
Retain only an ordered subset of the exact offered ring records: never reconstruct,
move, duplicate or reorder them. `forZone(index)` and `whenInventory(predicate)`
gate admission before edits. Binding and retention are validated before either
table is published, inside the registered owner boundary. Moves/removals/replacements
remain the legacy snapshot path; native ring-object mappings and creator-defined
level implementations cannot use this opt-in path. The
[Second Strike example](../../../examples/second-strike/README.md) shows separate
forest/fire inventory plans and preserves later Act 2 content.

S3K difficulty policies are optional services, not alternate stage controllers.
`AizMinibossAttackPolicy` selects a bounded warned follow-up at the native breath
boundary; `BlueSphereLayoutPolicy` receives an isolated ROM-layout copy and may add
at most 32 red/bumper approach cells while preserving entry, blue/ring cells and
their reachable native grid; `GumballSpringPolicy` selects two to four symmetric
native spring offsets. Creator returns are copied/validated inside the canonical
owner fault boundary. Stage rewards, native movement, results, exit and return
remain the host's responsibility. Layout admission proves bounded integrity,
not that a board has been solved using native player input.

## Captured state and persistent settings

`ModStorage` supplies bounded UTF-8 `read`, `write`, `delete` and `list` operations
for a verified owner, plus `readBytes` and `writeBytes` for binary files such as
recorded inputs or ghosts. Gameplay modules obtain it from their registration context;
scenes use the compatible `SceneStorage` interface. Both reach the same
`saves/mods/<verified-owner>/` namespace, and text and binary files share it. Text
files are limited to `ModStorage.MAX_TEXT_BYTES` (1 MiB) and binary files to
`ModStorage.MAX_BINARY_BYTES` (4 MiB); an oversized write returns false and leaves
the previous file untouched. Names follow the accepted local filename syntax, and
paths cannot cross into another owner or stock save namespace. Every write stages
the whole file beside its target and renames it into place, so readers see either
the old or the new file. A write/delete result reports success; a read can be
absent, and `readBytes` returns a fresh copy. Check the result when persistence
affects the player's decisions.

Legacy scene files retain the eligible old owner-directory read fallback, then
new writes use the canonical directory. Built-in game namespaces are excluded
from that fallback. Updating or uninstalling a jar does not erase owner data.
Survivors retains its existing profile format through the owner-storage migration.
Infinite's leaderboard remains on its legacy
`SavePaths.root()/infinite-sonic/leaderboard.txt` read fallback, then writes to its
verified owner storage. An unreadable or oversized generation remains read-only;
unsaved leaderboard changes retry without reapplying the completed run.

For an owner-local text checkpoint, `ModSaveSlot<T>` shares validation, recovery
and checked publication while the mod owns its format through `ModSaveCodec<T>`.
Construct one slot with `context.storage()`, a primary filename and optional
backup filename, then keep it on the owning scene/module. The codec has no I/O:
`decode` returns `VALID`, `CORRUPT` or `INCOMPATIBLE`, and `encode` must produce
text that the same codec validates. Unexpected codec failures propagate through
the normal owner fault boundary.

`load()` distinguishes `ABSENT`, `VALID`, `RECOVERED`, `CORRUPT`, `INCOMPATIBLE`
and `UNREADABLE`. A newer primary is authoritative and cannot silently fall back
to an older format. Rejected bytes remain protected until the player's explicit
New Game/reset decision calls `allowReplacement()`. A valid backup can recover a
damaged primary; if corruption appears later, automatic recovery requires a backup
to preserve the previously validated generation. A slot without a backup protects
those new corrupt bytes until explicit replacement. `save(value)` returns a
`ModSaveWriteResult`; display "saved"
only when `saved()` is true. An incompatible backup also requires explicit replacement.
Unreadable backups and required backup write failures prevent primary publication,
and oversized text is rejected before writing.
Explicit replacement can overwrite an unreadable generation when storage can still
write it. When both generations are rejected, reset establishes a validated backup
before publishing the new primary; a supported old backup is retained. This keeps
the following ordinary checkpoint writable. Failed reset publication still retains
the exact encoded checkpoint and reports its failed boundary.

On a failed publication, `pending()` reports the retained encoded checkpoint;
call `retry()` on a bounded cadence and during exit. Retrying does not encode
again or repeat rewards, calendar changes or RNG. Each file write is atomic;
the primary/backup sequence is not a transaction across two files. Slay, Three
Islands and Starpost Valley demonstrate local codecs and explicit replacement
decisions. Keep non-rewound settlement ledgers outside captured gameplay state
when rewinding must not grant the same durable reward twice.

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
`captureSaveFields(zoneState)` supplies optional game-owned JSON inputs at the
save boundary. The host freezes nested string-keyed maps and lists before
`capture(reason, context)` reads `capturedFields()`. Describe the custom payload
and its version in the mod, and use `restoreProgress` for supported custom
progress fields. Test snapshot independence from later live changes, save/load,
missing-owner recovery and failures inside the provider's owner boundary.
