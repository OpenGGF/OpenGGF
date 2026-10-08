# Content mods

This page is the detailed additive-content reference. New creators should start at
the [handbook index](index.md), which orders the six quickstarts by effort and links
the format, trust, identity, troubleshooting, and sample references.

OpenGGF Mod API 0.7 supports restart-loaded music packs, code-backed objects,
baked art, complete Sonic 2 and Sonic 3&K zones, playable characters, and no-ROM
standalone games. Mods are discovered from the
process `mods/` directory at restart; executable mods must be enabled and granted
trust in the Mod Manager before they run.

The current Mod API is the unpublished, mutable `0.7.0` candidate on `develop`; no
creator contract has been published yet. Maintained samples should
declare `engineApiRange: ">=0.7.0 <0.8.0"`; see the
[compatibility guide](../architecture/mod-api-compatibility.md). Start with the
guide for the contribution you are building:

- [Music packs](music-packs.md) — data-only WAV/Ogg replacements for stock music.
- This guide — Phase 2 objects, art reskins, and complete Sonic 2 zones.
- [Playable characters](characters.md) — owner-tagged character identity, physics,
  playable art, archetypes, abilities, saves, and rewind.
- [Standalone games](standalone-games.md) — a complete `GameModule`, levels,
  characters, music, and SFX that launch without a ROM.
- [The `ggfmod` CLI](ggfmod.md) — launcher syntax, project scaffolding, and all
  converters.
- [Mod scenes](guides/mod-scenes.md) — a patch mod's startup scene
  (`ModContext.registerStartupScene`): a full-screen menu, minigame or whole game drawn by
  the mod with the player's ROM art, starting from `examples/hello-scene`.
- [Native-Tails Flappy](guides/native-tails-flappy.md) — a maintained S3K patch
  combining the 0.7 fresh-game, team, input, and HUD policies with a fixed-camera
  dynamic-object minigame.

A creator build needs matching candidate artifacts from one commit; follow
[setup](getting-started.md). It needs both jars:

- the engine jar, which contains the public API and runtime dependencies; and
- the `openggf-mod-sdk` classifier jar, which contains `ggfmod`, converters, and
  project templates.

Use `docs/modding/ggfmod.ps1` on Windows or `docs/modding/ggfmod` on macOS/Linux.
Pass the engine jar and SDK jar first, followed by the command. The examples below
abbreviate that launcher as `ggfmod`.

## Start a project

```text
ggfmod init my-mod --id my-mod --package example.mymod
```

The generated Maven project is a working reference mod, not pseudocode. It contains
a strict Mod API 0.7 manifest, a namespaced patrol badnik, a character stub, an
8-by-8 Genesis sheet source, and a minimal full-level editor export. The character
stub deliberately has no playable art or terrain sensors; use the
[character guide](characters.md) and checked-in acceptance sample before enabling it
for gameplay. The entrypoint also registers the object, its baked art and preview,
and a zone inserted after `mtz3`.

From the generated project directory, build and validate its distributable:

```text
mvn package -Dopenggf.engine.jar=/absolute/path/engine.jar -Dopenggf.sdk.jar=/absolute/path/sdk.jar
ggfmod validate target/my-mod-mod.jar --format json --warnings allow
```

The Maven lifecycle compiles Java, converts the source art/level, and runs the SDK's
validated package step. For `--id my-mod`, the distributable is
`target/my-mod-mod.jar`. Run the same Maven command after source edits; it removes
only the generated outputs before rebuilding. Review the sorted validation findings,
including warnings permitted by this prototype's explicit policy.

Manual `convert` and `package` commands are an expert alternative. Choose fresh
output paths: converters and the packager intentionally refuse to overwrite an
existing destination. The generated project does that cleanup in its Maven lifecycle.
Copy the validated `*-mod.jar` into `mods/`, start the engine, enable it in the Mod
Manager, confirm the code-trust warning, and restart.

Code-bearing mods require the JVM jar because GraalVM native-image builds cannot
load new classes under closed-world AOT. On native builds they remain disabled for
the current process and appear as `UNSUPPORTED` in the Mod Manager; data-only music
packs and reskins continue to work. Run `OpenGGF-<ver>-jar-with-dependencies.jar`
(or the universal jar) to use code-bearing mods.

For an explicit local development launch, use `ggfmod run target/classes`. This is
the only exploded-directory entry point. The engine snapshots that directory once;
later source-tree changes are not observed by the running session. Deterministic
test, replay, capture, and time-attack launches still force external mods off.

## Add an object

Register object keys through `ModContext.registerObject`. Keys are owner-local at
registration and become namespaced as `<mod-id>:<local-key>` in level exports and
saves. Never assign a stock byte object id to a mod object.

An owner's transaction is bounded by its asset root's collection budget (10,000
successful registrations in production). The same budget bounds total authored
acts across its zones before any assets decode, and returned service/runtime
graphs before publication. Exceeding it rejects the whole transaction.

The generated badnik demonstrates the minimum runtime contracts:

- extend an appropriate public object base such as `AbstractBadnikInstance`;
- use injected `services()` only after construction, never a singleton or a
  constructor-time service lookup;
- use public shared helpers such as `PatrolMovementHelper` and the standard
  `DestructionEffects.DestructionConfig` path instead of copying engine behavior;
- implement `ModRewindRecreatable` and recreate from `ObjectReconstructionContext.spawn()`
  (the older `RewindRecreatable` expert contract remains available); and
- keep mutable gameplay state in instance fields that the rewind schema can capture.

Do not add mutable static gameplay state, uncaptured object references, or immutable
`final` scalar state whose runtime value changes. `ggfmod validate` checks compiled
classes for these object, service, and rewind rules before the engine creates an
owner class loader.

An `ObjectSpawn` keeps the ROM's field widths: `subtype` and `objectId` are one byte and
`renderFlags` two bits, so anything packed above them is silently dropped. Pass larger
initial values (hitpoints, art indices, amounts) to a constructor that stores them in
fields; the rewind schema restores fields, and `recreateForRewind` only needs a constructor
that accepts the bare spawn. Objects that implement `TouchResponseProvider` are touchable
only after they have rendered once (`requiresRenderFlagForTouch()`), which headless tests
never do; return `false` there for objects that are always near the camera.

Art sheets are registered with `registerObjectArt` and a `BakedSheetRef`; editor
previews use `registerObjectPreview`. The sheet YAML assigns each 8-by-8 tile to a
Genesis palette line and describes bounded pieces. `convert art` rejects images
whose dimensions, palette use, or piece bounds cannot be represented exactly.

## ROM art intake (Sonic 2 patch mods)

`ModContext.registerRomObjectArt(key, request)` materializes object art from the
*player's own ROM* at gameplay launch, instead of shipping baked art in the mod jar.
This is the supported way to remix a stock game's existing art (for example, Tails'
flying frames) into a new mod object — the mod jar itself ships zero ROM bytes; the
sheet is decoded into memory only after the engine opens the player's `s2.gen`.
The maintained [ROM-art remix guide](guides/rom-art-remix.md) follows the complete
source, decoded-pattern probe, rewind, and package-inspection workflow.

The request names a ROM art address, its compression, an S2 mapping table address, an
optional DPLC table address, a palette line, and a bank size:

```java
context.registerRomObjectArt("bird", new RomArtRequest(
        0x64320,                 // artAddress: Tails' flying-frame art
        RomArtCompression.UNCOMPRESSED,
        0xB8C0,                  // uncompressedByteSize (UNCOMPRESSED only)
        0x739E2,                 // mappingAddress
        0x7446C,                 // dplcAddress (0 = no DPLC flattening)
        0,                       // paletteLine (0-3)
        1));                     // bankSize (1 for a static sheet)
```

The literals above are `Sonic2Constants.ART_UNC_TAILS_ADDR` /
`ART_UNC_TAILS_SIZE` / `MAP_UNC_TAILS_ADDR` / `MAP_R_UNC_TAILS_ADDR`, and palette
line `0` matches `ART_TILE_TAILS`'s stock palette assignment. Use `RomOffsetFinder`
(`--game s2`) to locate art/mapping/DPLC addresses for other stock objects; a label's
compression is usually visible in its ROM offset finder result or the surrounding
disassembly.

**Gates.** ROM art intake is available only to additive Sonic 2 patch mods
(`baseGame: s2`, `type: patch`); a standalone module (or any non-S2 `baseGame`) fails
registration outright. Because registration happens before any ROM is open, addresses
are checked only against a static Sonic 2 ROM-length bound at registration time; the
real decompression, mapping, and DPLC parsing happen at gameplay launch once the
player's ROM is available.

**Palette.** `paletteLine` is a palette *line* index (0-3) into the active zone
palette, not a ROM color address. For an additive S2 format-v1 zone, the host
replaces line 0 with the active ROM character palette after decoding creator level
data while preserving creator-owned lines 1-3. Sonic and Tails share
`Pal_SonicTails`; a Knuckles-main lock-on changes line 0 indices 2-5 and can recolour
borrowed Tails art.

**DPLC.** An optional `dplcAddress` (S2 player-format DPLC table) flattens
frame-by-frame VRAM tile swaps into one static sheet, the same technique the engine
itself uses for objects such as the AIZ intro plane and the ICZ snowboard. Pass `0`
when the mapping's pieces already reference art tiles directly.

**Limits.** Materialized sheets are bounded by the same `ModInputLimits` sheet caps
(`maxSheetPatterns`, `maxSheetFrames`, `maxSheetPieces`) enforced elsewhere in the mod
pipeline, so a garbage or oversized request cannot allocate unboundedly.

**Faults.** A bad address, a decompression failure, or a sheet that exceeds the
`ModInputLimits` caps aborts launch with an owner-attributed `MOD_ROM_ART_INVALID`
diagnostic naming the offending key and the hex ROM address — the same creator-apply
fault contract as other launch-time mod failures.

Once materialized, the sheet is served through the normal object-art path: call
`getRenderer("<mod-id>:bird")` from object code exactly as you would for
`registerObjectArt`.

## Add a Sonic 2 zone

The format-v1 additive-zone path is intentionally Sonic 2 only; S3K uses format v2
as described in the next section. In the editor, start from a level, make the desired
changes, and use the full-level export into the mod project's source tree. A full
export is different from the editor's sidecar/delta save: the
export directory must contain these required files:

```text
level.json
patterns.bin
chunks.bin
blocks.bin
fg-map.bin
solid-heights.bin
solid-widths.bin
solid-angles.bin
collision-primary.bin
collision-secondary.bin
palettes.bin
```

`bg-map.bin` is the only optional inventory entry and is present when the level has a
background layout.

`level.json` carries boundaries, start position, music, tagged spawns, and references
to the ten binary assets. The binaries contain the complete pattern/chunk/block,
collision, and palette data needed to load without a ROM-address fallback.
`ggfmod convert level` validates this exact inventory and copies a retained snapshot
to the baked output.

Register a single act with `registerZone(ModZoneContribution.singleAct(...))`,
or an ordered campaign with `ModZoneContribution.multiAct(...)`. Mod zones use
owner-local authored metadata (`zoneIndex` from `0x40`, `levelIndex` from `0x400`).
Independent mods can both start with `64` and `1024`, including mods for different
host games. The engine allocates distinct effective zone and level IDs after
composition; never persist those IDs or use them to identify another owner's
content. Creators must not use those reserved bands for stock content. Runtime list
indices are append-only after Sonic 2's 11 stock zones, while `insertAfter` creates a
results-boundary progression redirect without renumbering stock zones. Use a valid
results-driven stock anchor such as `mtz3`.

Object spawns in the export retain namespaced keys such as
`my-mod:sample-badnik`. Music may reference a valid stock Sonic 2 music id or a
namespaced converted track. A minimal zone needs no custom events, animation,
water, palette cycling, or parallax handler; unknown synthetic ids use the engine's
graceful defaults.

Saved mod-zone locations use a tagged zone key rather than the allocated runtime
index. If the mod is later disabled or missing, the slot remains intact, loading
reports the missing zone, and play restarts at zone 0. Re-enabling the mod makes the
tagged destination resolvable again.

Stateful zone events implement `RewindableZoneEvents<Snapshot>` and return an
immutable snapshot from `capture()`. `restore()` hydrates that state without
creating gameplay objects; `reconcileAfterRewindRestore()` runs after object and
world restoration and may re-adopt restored object references. Implement
`resetForMissingSnapshot()` to establish a complete initial state when an older
snapshot lacks this contribution. Supply a factory in `ModZoneContribution` so
loads and respawns create fresh handlers. Ordinary stateless `LevelEventProvider`
handlers remain supported; expert handlers implementing `RewindSnapshottable`
retain their explicit state contract.

The engine registers event and module adapters under its own trusted owner and
stable local identity. A creator key such as `gamerng`, `rings`, `level`, or `mode`
cannot replace host state or another owner's state. Returning the same object as
a service, controller and rewind adapter keeps one proxy identity. Returned
functional factories, event callbacks, and state callbacks all run through the
contributing owner's fault boundary, including dependent disable on failure.

Expert patches may return a fresh `PlayableCharacterRegistry`, including semantic
builtin replacements. The engine binds its definition factories, returned respawn
strategies, art and later sprite callbacks to the verified patch owner; repeated
queries reuse the mapped registry. Definitions forwarded unchanged from the base
retain their original identity and owner. Fresh mod keys or previously owned
callback templates from another owner reject publication. Character definitions
retained from `GamePatch.providedCharacterDefinitions()` for launch metadata use
the same boundaries before any patch module is applied.

Full exports may contain material derived from a user-supplied ROM. Mod authors are
responsible for ensuring they have the right to distribute every exported asset;
shipping a lightly edited stock level may distribute copyrighted level data.

## Add a Sonic 3&K zone

Mod API 0.7 includes an S3K host adapter for additive zones. Use level format v2 and
declare `baseGame: s3k`; the v1 Sonic 2 and standalone paths above are unchanged.
Format v2 keeps the bounded pattern, chunk, block, map, solid, and collision files,
but removes `palettes.bin`. Its `hostMetadata.s3k.objectZoneSet` value is `S3KL` or
`SKL`, while `paletteClaims` lists only the line 1-3 color cells used by reachable
level art. See the [exact level-format reference](formats/level-definition.md).

For a namespaced-object-only level, write `S3KL` as the default object set. If any
entry uses `stockObjectId`, select the intended set explicitly; registration rejects
stock objects whose factories depend on a real ROM zone. Namespaced mod objects are
the reliable path for custom gameplay.

S3K supplies the selected character palette on line 0 and reserves only the cells
actually used by the lives HUD. Line 1 reserves colors 1, 5, 12, 14 and 15;
this includes the life-count digits within the name piece. The creator owns every
other declared sparse cell.
Claims that overlap host-owned line 0 or a live HUD cell fail registration instead
of creating a frame-order-dependent palette conflict. The custom-zone runtime is
empty by default: flat scroll and no inherited stock animated tiles, PLC loads,
zone features/events, special passes, or advanced render modes. Explicit per-act
runtime contributions below supply supported water, scroll, animation, palette,
feature, state and render consumers; they do not inherit arbitrary stock zone events.

Registration remains additive and tagged. Runtime zone indices may change with the
enabled mod set, so saves store `savedZone.mod.owner/local` identity rather than that
synthetic index. If the owner is later disabled, S3K data select preserves the slot
but safely falls back to AIZ1; re-enabling the owner makes the tagged destination
resolvable again.

## Choose a fresh-game destination and presentation

The maintained [Native-Tails Flappy guide](guides/native-tails-flappy.md) exercises
this complete policy set against a real S3K launch and shows how the policies stay
destination-scoped while the gameplay controller remains ordinary mod object code.

Mod API 0.7 lets a complete-zone patch mark one owned zone as a fresh-game start and
attach launch-only policies to that tagged destination. Set the `gameStart`
argument of `ModZoneContribution.singleAct(...)` or `multiAct(...)` to `true`;
pass `false` for an ordinary contributed zone. A mod using these contracts should declare `>=0.7.0 <0.8.0`:

```java
var destination = ZoneKey.mod("my-mod", "flappy");
context.registerZone(ModZoneContribution.singleAct(
        "flappy", new BakedLevelRef("levels/flappy/level.json"), null, null, true));
context.registerLaunchTeam(new ModLaunchTeamContribution(
        destination, CharacterKey.TAILS, List.of()));
```

Game-start selection is exclusive. Enabled patch order is authoritative: the last
effective declaration wins, each shadowed owner receives `MOD_GAME_START_SHADOWED`,
and disabling the winner reveals the previous declaration or the host's stock fresh
destination. This does not insert the zone into results progression. Both New Slot
and No Save fresh starts use the resolved destination.

The launch-team policy replaces only the copied gameplay-session team after the
resolved character registry verifies every required identity. It does not mutate
`config.yaml`, the player's data-select choice, or the durable team saved in an active
slot. A missing required character aborts launch; the engine never silently substitutes
a partial team.

An input filter transforms P1's logical snapshot without adding a movement framework:

```java
context.registerInputFilter(new ModInputFilterContribution(destination, raw ->
        PlayerInputState.of(
                raw.heldMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.pressedMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.actionHeldMask(), raw.actionPressedMask(),
                raw.startHeld(), raw.startPressed())));
```

The engine records the raw `InputHandler.logical()` snapshot first, then applies the
filter downstream. Trace playback and rewind re-simulation therefore replay the same
raw snapshot and reapply the filter deterministically. `PlayerInputState` reconstructs
the legacy jump bits from `actionHeldMask` and `actionPressedMask`; preserve those
action masks when suppressing directions or jump will be lost.

`registerHudProfile(new ModHudProfileContribution(destination, profile))` installs a
row-only presentation over the existing SCORE, TIME, RINGS, and LIVES labels and
counters. Each immutable `HudRow` chooses visibility, label, metric, label/value
coordinates, width, and a `NONE`, `TIMER_FLASH`, or `ZERO_FLASH` warning. Numeric
metrics accept widths 1 through 9 and saturate non-negative values into that width;
TIME requires the existing four-character width. A profile can label the RINGS metric
as SCORE and omit the original score row, but it does not create or replace gameplay
counters.

These destination policies are required as a set. Creator/provider/filter failures
run through the owner fault boundary, record `MOD_CALLBACK_FAILED`, pending-disable
the owner and dependents, persist that decision, and abort rather than continuing with
a partial launch. With no matching contribution—or after session teardown—the stock
defaults are the selected team, `GameplayInputFilter.IDENTITY`, and
`HudProfile.stock()`.

Mod API 0.7 intentionally publishes no fixed-forward-movement controller, forced-camera or
scroll policy, world wrapping/rebasing runtime, or flight-fatigue rule. Fixed-camera
minigames should keep the player stationary, move and recycle their own obstacles,
filter unwanted directions, and use already-published character ability state.

## Make a data-only art reskin

A reskin needs no Java entrypoint and no trust grant. Set `type: patch` and the
appropriate `baseGame`, omit `entrypoint`, convert the sheet, and map an exact stock
art key in `artOverrides`:

```yaml
artOverrides:
  signpost: art/reskin.ggfs
```

Package the directory and validate the resulting jar with the same commands. With the
mod disabled, the engine retains the original provider instance and behavior; with it
enabled, only the named art lookup is decorated. Run `ggfmod art-keys` to list
known exact stock provider keys; unknown keys produce `UNKNOWN_ART_OVERRIDE_KEY`.
Availability still depends on the game/zone. Preserve every mapping frame consumed
by the stock animation. Sonic 2 signpost uses frames 0–5: Sonic, Tails, Eggman and
three spin transitions. The maintained reskin supplies all six.

Baked palette metadata quantizes source PNG colors to indices; it does not install
an arbitrary new palette over stock gameplay. Author against the host's active
palette line. This sample uses line 0/index 6, white in normal S2 Sonic/Tails art.
Donated/super character palette changes can affect that line; verify those routes
if your mod claims to support them.

For the complete CLI invocation and launcher details, see [the `ggfmod` guide](ggfmod.md).
For streamed stock-music replacement metadata, see [Music packs](music-packs.md).

## Controlled level modes and course views

Return a stable `GameplayFrameController` from a patch's module and register that
same controller through `rewindAdapters()` if its mode state is rewindable.
The host invokes `beforeTick(CourseControl, LogicalInputSnapshot)` once per mode
row: `false` holds course physics, objects, clocks, animation and PLC work; `true`
admits one native step with neutral player input. `afterTick` receives whether
that step ran. Keep aim, menus and feedback in the controller; keep physics in
native gameplay. Stock modules return no controller and retain their normal path.
Initial setup rows are excluded from movie/history input; subsequent HOLD rows
are included. Released native title-card text continues its presentation exit,
including its normal control-lock ownership, on controlled rows. Pending entry
music also retains its native presentation countdown. `presentationReady()`
waits for the entry fade and title overlay to finish; capture the first reusable
course checkpoint after that boundary so rollback cannot bring an entry card back.
`advanceEntryPresentation()` returns whether it serviced an active fade on this
row. A mode can settle its initial ball through those fade rows, then hold the
settled world while the remaining title text exits.
Window focus and configured keyboard pause freeze both native and
creator rows; the configured frame-step key admits one row while paused. Start
remains available for the creator's own menu. Holding Escape services the host's
return fade independently of HOLD or pause. `allowsDebugRewind()` controls admission of live developer rewind.
Modes with their own rewind allowance should return false in every mode so the
developer shortcut cannot bypass it. `CourseControl.rewindHeld()` supplies the
configured rewind key/primary-pad bumper for the current live row, independent
of the developer rewind enabled setting; the controller owns its press latch.
It is false while a logical movie override is active. Supply an A-operated menu
command for Genesis movie replay, whose controller rows cannot encode that key
or bumper. This accessor is part of the unpublished 0.7 candidate surface; its
normalized pin is regenerated without changing the policy/version descriptor.

`GameplayFrameController.rewindPresentation()` optionally returns a finite,
bounded `RewindPresentation` value for creator-owned reverse playback. The host
uses its existing VHS pass and settings; the value cannot seek history, restore
world state or grant rewind permission. Stock controllers and null requests have
no effect. Host pause suppresses the creator request. Keep playback and allowances
in the mod, as Putt Putt Paradise does, rather than calling developer rewind.

### Replay a view and its audio

`SceneReplay` records immutable ROM-backed scenes into an 8 MiB / 128-sample
history. It preserves both endpoints and reduces the interior sample density
as a recording grows. The mod chooses the tick and playback speed; the helper
knows nothing about shots, scores or rewind allowances. `frame(revision)` returns
a fresh revision for the normal scene presenter. Its snapshot contains view
values, never gameplay objects or renderer allocations.

`CourseControl.recordAudioReplay(maxSeconds)` records the **actual final PCM**
heard during forward presentation. Its opaque `AudioReplay` is independent of
developer history, works with developer rewind disabled, and owns no logical
sound-driver restore. Pause produces silence without advancing the clip.
Choose enough time for your longest recording, including PAL presentation.
The host bounds recordings to 1–120 seconds and four live leases per audio producer; an exhausted
bounded tail yields silence instead of audio from before the recording began.
Close a lease on settlement, cancellation or replay completion; closing releases
its ownership even if a failed sink flush reports an exception. Session teardown
also closes forgotten leases if a creator's cleanup fails.

The controller pattern is small:

```java
import com.openggf.audio.AudioReplay;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.presentation.SceneReplay;

// Capture your reusable world before the action; keep budgets/scores separately.
CourseCheckpoint before = course.capture();
SceneReplay views = new SceneReplay();
views.record(0, initialScene);
AudioReplay sound = course.recordAudioReplay(80); // immediately before release

// afterTick, for admitted forward rows only:
if (views.wantsSample(++elapsed)) views.record(elapsed, currentScene);

// When your rules accept a rewind, hold world simulation:
views.begin(elapsed, speed, latestScene);
sound.beginReverse(speed);

// Each unpaused reverse beforeTick:
if (!views.atOrigin()) {
    views.step();                    // draw views.frame(nextRevision)
} else {
    sound.close();                   // previous row presented the final PCM
    course.restore(before);          // one rollback, after reverse playback
    views.clear();
}
// Return false during this reverse sequence. Close sound on all other exits.
```

`GameplayFrameController.presentationPaused()` supplies the audio hold;
`rewindPresentation()` independently requests the user's configured VHS effect.
Release crossfades back to the live voices without stopping music, truncating
developer history or making scores rewindable. A second reverse owner is rejected
before changing the existing selection. Use `AudioReplay.setRate` if your view
speed changes; finite positive rates up to 64 are supported.

Network mods publish their authoritative replay phase/rate and close local clips
at the accepted result. A clip contains what that machine actually heard, so it
is a local presentation resource, not a snapshot or wire payload. If a peer joins
mid-action it has no earlier local audio to replay; do not invent that missing
history. Putt Putt Paradise's thin `ShotReplay` shows how a mod layers its duration
policy over these generic tools.

`CourseControl.capture()` returns an opaque whole-course checkpoint. Restore
validates session, loaded hole and registry generation before changing character,
world or audio. The controller adapter is excluded by its engine-bound identity,
so a shot's ledger can survive course rollback; creator key prefixes do not grant
exemption. Full debug rewind restores the controller too. Course replacement
invalidates speculative future history and preserves past; actual act loads reset
history. Checkpoints are in-process handles, not a network format.

`CourseControl.playerState()` returns centre coordinates, native 8.8 speeds,
registered character identity and support/camera observations. `launchRolling`
accepts a creator-calculated facing, X/Y/ground impulse and airborne flag;
it validates signed native speeds, corrects rolling radii and releases support.
The creator chooses loft, spin, power and sounds. `loadLevel(zone, act)` loads an
explicit module-owned destination; `selectCharacter(code)` requires an exact
registered character and constructs it before replacing the single main roster.
This capability has no golf/zone/character-name restrictions. Controllers
can retain rolling without enabling pinball's minimum-speed boost, draw a
presentation-only scene/overlay, and request title return. Use session overrides
for selected characters and viewport rather than writing user preferences.

`LevelManager.captureScene()` captures prepared terrain, object/player tiles,
palette and procedural primitives. `SceneFrameCodec` provides a bounded value
encoding; `SceneViewPresenter` resolves art identities from the local ROM and
composes or draws a read-only view. It never steps the recipient's world, loads
objects from the sender, or accepts ROM/art bytes as a fallback.
`PlayerPresentationPose` selects a native displayed frame or a ROM-backed idle,
duck or spindash view. These held views include native appendages such as Tails'
tails and use the caller's presentation clock, without changing animation,
physics or dynamic-art clocks in the playable world.
[Putt Putt Paradise](../../examples/putt-putt-paradise/README.md) demonstrates
independent golfer checkpoints and a host-authoritative direct TCP room. Its
transport is separate from time-attack ghost races.


Module title screens can read `MenuInput.pointer(input, width, height)`, an
immutable logical-coordinate `Pointer` with `inside`, left/right press edges and
`over(left, top, width, height)`. The engine shares its letterbox/framebuffer
mapper with mod scenes; menus own their hit regions and hover policy. A missing
window, unseen pointer, headless graphics or logical movie override produces
`Pointer.none()`. Mouse state is physical presentation input and never replaces
recorded controller rows. [Putt Putt Paradise's source guide](../../examples/putt-putt-paradise/README.md#learn-from-this-example)
walks through registration, one controlled shot and safe extension points.

Gameplay prompts should name the player's actual control rather than a fixed key.
`ButtonPrompts.label(input, player, button)` (or `CourseControl.buttonLabel` inside a
controlled level) returns the incoming player's own binding: P1's A defaults to
"Space" and P2's to "Right Shift", remaps are followed, and a player whose last
intentional input came from their pad sees that pad family's physical button. An
empty result means neither device can produce the button. `MenuInput.confirmLabel`
remains the fixed menu-confirm prompt and is not a substitute for a player binding.

Session rewind registration and removal belong to the engine. Contribute adapters
through your module or owner transaction; a creator may still construct and use
a private `RewindRegistry` for isolated calculations.

## Compose a campaign from authored acts

`ModZoneContribution.multiAct(localKey, orderedActs, insertAfter, events, gameStart)`
registers one tagged zone with contiguous zero-based acts. Each `BakedLevelRef`
points at an independently validated export. Act 1 and Act 2 may both contain
`zoneIndex: 64` and `levelIndex: 1024`: those numbers are owner-local metadata.
The engine allocates one host zone slot and separate runtime level slots, and the
results progression plan advances through the ordered acts before the stock
successor. Only Act 1 supplies a fresh-game destination. Saves persist the verified
owner/local zone tag and act, so reordering other mods does not change the saved
identity; a missing or disabled owner falls back through the host save profile.

Attach `withRuntime(context -> ModZoneRuntimeServices.builder()...build())` to the
zone contribution for act-local behavior. The engine creates the factory result
on each load/respawn and supplies the tagged destination, receiving game, logical
zone/act and decoded level. `Level.getZoneIndex()` is the separately allocated
host ROM slot; do not use it as the persisted identity. The builder accepts
features, an explicit `WaterDataProvider`, scroll handler, `ZoneRuntimeState`,
animated tile channels, palette animation, staged render effects, render modes
and local rewind adapters. An absent contribution leaves that facility empty.
An absent water provider means a dry custom act; it cannot retain a previous
act's water or invoke a stock ROM zone lookup.

Every factory and returned callback remains inside the verified owner's fault
boundary, including dynamic-water handlers and animation functions. The engine
qualifies channel and adapter identities, captures the live runtime state, and
removes old act adapters on replacement. Runtime state must report the supplied
receiving game and logical zone/act. Restore hydrates state; event reconciliation
runs after all restored world owners. Full act loads create a new rewind timeline.
The supported host profile is currently `flatEmpty()` plus these explicit runtime
contributions. Other expert profile kinds are rejected instead of silently ignored.

Keep scroll recomputation idempotent for the restored frame: hydration recomputes
camera-derived output without advancing the simulation. Capture any mutable scroll
accumulators separately. The animated-channel graph captures its phase cache;
creator-mutated pixel bytes remain derived art. Restore or regenerate those bytes
from captured state, as Tide Circuit does, and upload through the injected render
context. A cached phase alone cannot undo future pixel writes.

[Tide Circuit](guides/two-act-campaign.md) supplies two original acts and exercises
water, animated tiles/palettes, scrolling, staged rendering, state capture and the
normal results completion path. Its [route matrix](../architecture/validation/levels/tide-circuit.md)
records observed checks and open breadth separately.

## Publish a shared service and rewind graph

Use `registerServiceBundle("run", () -> GameServiceBundle.builder()...build())`
for state whose lifetime is one module application. Factories are evaluated
before publication, duplicate contracts within one owner are rejected, and the
complete graph shares one bounded callback cache. `frameController("mode", mode)`
registers the same instance as `GameplayFrameController` and its rewind adapter.
The session registry recognizes that shared identity: course checkpoints exclude
the controller ledger, while full debug rewind captures it. Concrete creator
classes retain their exact class identity for `getGameService(MyState.class)`;
public interface callbacks receive owner wrappers. Deferred `LevelInitProfile`
`InitStep` and `StaticFixup` actions also retain the provider's verified owner when
the loader or teardown runs them later; forwarding an earlier owned action retains
its original attribution. Captured concrete state keeps
its verified owner/local provenance even when queried through its original class;
a new bundle cannot recapture another owner's state. Two mods defining identically
named private classes remain independent. A shared engine/interface service
contract uses the later applied owner, and inherited owned proxies retain their
original owner. `dynamicService` supports nullable, temporary services; the
provider and returned interface callbacks are fault-bound. Frame controllers use
a stable captured instance rather than a dynamic provider.

`registerService(localKey, contract, instance)` and
`registerRewindAdapter(localKey, adapter)` are fixed-instance conveniences.
`storage()` returns the handle rooted by the host at `mods/<verified-owner>`;
retain it in a closure instead of supplying paths or another owner's id.
Owner registration and returned graph entries share the asset root's collection
budget (10,000 by default, lowerable by the host). Exceeding a budget or duplicating
a key aborts publication.

For native placement changes, use
`decodedLevelPatch("placements", LevelPatch.empty().select(...).bind("spring"))`
after registering the local `spring` object. The engine binds the owner and checks
the registered factory. Each immutable template runs exactly once after the final
native, prepared or expert override level has been decoded, in composition order.
Use `GameModule.transformDecodedLevel(source)` to observe native placements before
forwarding to `super`; Golf uses this to retain its finish gate before the registered
template removes the native marker. The helper cannot rebind another owner's
placement. Existing expert `loadLevelOverride` remains available.

## Declare composition intent

Manifest v1 accepts an optional `composition` mapping:

```yaml
composition:
  after: [shared-art]
  before: [presentation-pack]
  conflictsWith: [alternative-rules]
  exclusiveContributions: [startup-scene, game-start, "art:signpost"]
```

`before`/`after` order both data and code application. They create no required
dependency, ignore absent/disabled neighbours, and do not disable optional
neighbours when a callback fails. Actual dependencies still determine eligibility
and dependent fault closure. Self references, duplicates, unknown fields and
unbounded declarations are rejected. Ordering cycles block their participants;
explicit conflicts block both enabled participants. Diagnostics sort participants
so scan order does not change the reason.

The default singular policies use the later application for a startup scene,
required display width, stock art key, stock music id or fresh-game destination,
scoped to the receiving game. Tagged zones, objects, characters and destination
launch/input/HUD policies compose under their verified identity. Service contracts
use exact `Class` identity; owner-defined types do not collide by binary name.
Zone runtime facilities replace only that owner's selected act lifetime.

An exclusive claim is accepted only when that owner's successful transaction
actually publishes the named target. Unused claims fail the transaction. When a
successfully contributed singular target has multiple owners and any claims
exclusivity, all conflicting participants and their required dependents are
rejected, instead of choosing a winner. Exclusive keys currently cover
`startup-scene`, `display-width`, `game-start`, `art:<stock-key>` and
`audio:<nonnegative-stock-id>`; unknown kinds are rejected. The engine's immutable
registration report lists scoped winners, shadowed owners, additive targets and
failure policy from the latest successful pass; it never re-runs registration to
inspect metadata. Bundle targets are reported as additive owner-scoped registrations
until their lifetime factories run, rather than inventing service contracts.
Audio is reported separately by `PreparedModMusic.overrideReport()` after actual
decoding and publication, with winner and shadowed track identities. Failed audio
preparation excludes that owner's tracks and retains the previous healthy override.
Exclusive stock data collisions are rejected in catalog eligibility before audio
preparation; compiled claims are checked before registration publication.

## Advanced module session controls

Custom game modules can opt into faster interactive gameplay with
`GameModule.gameplayStepsPerFrame()` (default 1, host range 1–32). Return alternating
counts for fractional rates and capture the accumulator through `rewindAdapters()`.
Return the continuous matching rate from `gameplayAudioPlaybackRate()` (default
1.0, bounded to 1–32) to accelerate music and effects without alternating their
pitch with the integer step budget. The host releases its audio rate at pause,
rewind, death, scene changes and teardown; external trace/movie owners retain
control of their own playback.

A module can also drive the engine's live rewind itself by returning a
`ScriptedRewind` from `GameModule.scriptedRewind()` (default `null`). While one is
returned, level play records rewind history even with live rewind switched off. When
its `requested()` is true the host rewinds with the ordinary presentation, taking
`stepsThisFrame()` steps per frame and checking `reachedTarget()` on the restored
state after each, then calls `ended(boolean)` once. Keep the implementation's own
state out of `rewindAdapters()`: it decides where the restore stops. Infinite Sonic's
CONTINUE is the worked example.
The host advances complete simulation ticks, preserving per-tick collision and
movement. Pause, rewind, external movie/trace ownership, transitions and non-level
scenes retain their normal pacing. `GameLoop.step()` remains one deterministic tick;
interactive hosts use `stepPresentationFrame()`. The
[Infinite Sonic example](../../examples/infinite-sonic/README.md) demonstrates a
rewindable clock that compounds speed every 30 seconds of active play.
A module can also pin a display aspect for its session with
`GameModule.requiredDisplayAspect()` (a `display.aspect` preset name; the master title
restores the player's setting) and hide the level select with
`GameModule.suppressesLevelSelect()`. Infinite Sonic uses both.
