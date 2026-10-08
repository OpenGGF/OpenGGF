# Mod API compatibility surface

OpenGGF Mod API 0.7 (`0.7.0`) is the unpublished, mutable compiled-mod candidate
carried by `develop` and `next` after the 0.6 release rollover. No Mod API baseline
has been published yet. The runtime-visible, type-only `com.openggf.game.ModApi`
annotation marks its roots.
Every engine type reachable through those roots' public or protected
constructors, methods, fields, generic bounds, annotations, nested types,
supertypes, interfaces, record components, and sealed permits clauses belongs to
the same recursive contract and must also be annotated.

The exact current candidate inventory is
`src/test/resources/mods/mod-api-signatures-0.7.txt`. `TestModApiSignatureSurface`
requires that file to be unique, sorted canonical UTF-8 text and exactly equal to
the dependency-complete runtime surface. This major/minor filename denotes a
replaceable candidate pin; it carries no compatibility promise. Earlier 1.x and
2.x labels were provisional development markers and carry no compatibility promise.

The root [`mod-api-release-policy.properties`](../../mod-api-release-policy.properties)
descriptor is the sole authority for branch topology, current version and status,
and the published-baseline set. This guide explains that policy but does not define
version state. The current descriptor has an empty published set.

`targetBranch` identifies the destination branch checked by CI; the three line
fields describe engine versions. Pushes to `master`, `develop` and `next` must
agree with that destination. Feature-branch pushes still validate the descriptor,
but omit the optional destination property because a feature ref is not a release
integration destination. Pull-request jobs check their base branch. An unpublished candidate on `develop` or `next`
may retain an API line at or below its engine line. Thus both the 0.7 and 0.8 engine
lines carry the same 0.7.0 candidate until an explicit API policy change. This does
not publish a baseline or rename the candidate pin. Master and publication checks
continue to require the selected release line.

Creator manifests should declare the maintained candidate range:

```yaml
engineApiRange: ">=0.7.0 <0.8.0"
```

Manifest `formatVersion: 1` is a separate wire-format version. It does not mean
Mod API 1.x and must not be used to infer compiled-code compatibility.

The controlled-entry follow-up changes `CourseControl.advanceEntryPresentation()`
from `void` to `boolean`: it reports whether the row advanced an active entry
fade. `presentationReady()` includes completion of the native released title
overlay. Modes can hold a settled world during that final presentation and capture
a reusable checkpoint afterward. This is an unpublished candidate signature
change; rebuild compiled mods. The `0.7` pin and `ModApiVersion` description
are updated together while the descriptor retains candidate `0.7.0`.

The KiS2 trace-readiness follow-up adds semantic movement and checkpoint-ring
rules, the saved ring bank in checkpoint/load snapshots, separate boss duck-frame
selection, and an explicit title-to-level-select action plus post-reset menu
handoff. Movement policies now group air and ground-pose decisions separately.
Candidate records use canonical constructors; owning factories pass stock defaults
explicitly. These are
changes to the unpublished candidate; version `0.7.0` remains unchanged and its
normalized `0.7` signature pin is replaced in place.

The SOZ completion extends `PersistentRespawnState` with the object-owned lower
respawn-table bits so bonus/special-stage returns preserve activated mechanisms.
Its canonical constructor now accepts those bytes. Placement ownership transfer
is also exposed by `ObjectManager` for children that retain a parent's layout
entry. These update the mutable `0.7` pin; the descriptor's candidate version
remains `0.7.0` and no published baseline is created.

The Hidden Palace completion adds `SpecialStageProvider.resultsExitFadesToWhite()` (default
`true`: the Sonic 1/2 exit SFX and white fade; Sonic 3&K leaves silently through black) and
`GameLoop.debugCompleteSpecialStageWithEmerald()`, the debug completion that capture tools
request without reading a key binding. Both update the mutable `0.7` pin.

The Survivors v2 follow-up adds `LevelInputOverlay`, an optional module service dispatched
before host and native ROM pause handling in LEVEL mode. `handleInput(InputHandler)` returns whether a modal
UI owns Start/Enter for that frame; the UI remains responsible for freezing its gameplay.
Modules without the service retain normal pause behavior. This replaces no input bindings
and changes only the mutable `0.7` pin; the descriptor and `ModApiVersion` remain at `0.7.0`.

The controlled-level additions expose `GameplayFrameController`, `CourseControl`,
opaque session/hole/layout-tagged `CourseCheckpoint`, `MenuInput`, title act
selection, and typed ROM-backed scene values/presenters. The default controller
is absent, retaining stock admission. Controlled HOLD is a recorded mode row;
SETUP_ONLY is not. Opaque course rollback excludes the engine-bound controller
adapter by identity and preserves past debug history; actual act loads reset it.
Prepared scenes carry value/art identities, never ROM bytes or mutable snapshots.
The additive `PlayerPresentationPose.Kind.IDLE` selects a ROM-backed held idle
view immediately after a roster switch; appendage selection remains internal
and shares native tail scripts with the live renderer.
Compiler-generated immutable enum constants and switch tables are now accepted
by the static initializer validator; arbitrary author static objects remain rejected.
These extend the mutable candidate pin and keep `0.7.0` unpublished.

The Survivors music follow-up adds `PowerUpRules.restoreLevelMusicAfterInvincibility`.
Stock rule factories pass `true`, retaining the native expiry request. Modes whose
invincibility never interrupts music can pass `false`; protection/visual cleanup
still occurs normally. The canonical record constructor gains this boolean, so
rebuild compiled mods using that constructor. Candidate `0.7.0` remains unpublished;
the runtime candidate description and normalized pin are updated together. The
release descriptor retains its existing candidate version and publication state.

## What the 0.7 candidate includes

The candidate exposes the accumulated creator capabilities together:

- restart-loaded music packs and trusted, code-backed patches;
- namespaced object factories, bounded baked object art, Sonic 2 ROM-derived art,
  complete Sonic 2 zones, and host-adapted Sonic 3&K custom zones;
- owner-tagged playable characters, playable-subclass rewind payload hooks, and
  no-ROM standalone modules over durable bounded assets;
- exclusive custom game-start destinations, destination-scoped launch teams,
  deterministic input filters, and row-only HUD profiles;
- tagged saves, game-agnostic baked levels, deterministic TMX conversion, and
  the two-artifact `ggfmod` workflow.

The zone seam uses `LevelDescriptor` in creator-facing signatures; stock
`LevelData` values implement that interface without becoming creator ABI. Dynamic
object rewind entries retain their owning compiled-mod loader through
`DynamicObjectEntry.ownerModId` and `RewindClassResolver`. Creator callbacks stay
transactional, owner-fault-bounded, and engine-authoritative.

## SMPS construction compatibility

`AbstractSmpsData(byte[], int)` retains its legacy constructor-time `parseHeader()`
hook for existing extensions. The new protected overload with a
`deferHeaderParsing` boolean lets extensions initialize their own fields before
parsing. Passing `true` leaves the default header state installed and suppresses
the hook; the subclass then owns explicit initialization.

Built-in S1, S2 and S3K music data use this deferred path and a format decoder,
so their construction never dispatches overridden `parseHeader()` or `read16()`.
Their byte order, signed key/volume offsets, voice-bank resolution and existing
truncated-input policies remain distinct where required. The decoder's temporary
result is private; it adds no creator-facing header or array API. The existing
`FrozenSmpsData` snapshot boundary remains unchanged. This is an additive change
to the unpublished 0.7.0 candidate, with its candidate pin replaced in place.
Distinct SFX header parsers retain their legacy initialization path in this change;
their offset-dependent reparsing is a separate construction migration.

## Reviewing, maintaining, and publishing the recursive surface

Full-song scene preparation adds `SceneMusicPreparation` and its state enum,
reached through the default `SceneMusic.prepareAsync` and `preparePartAsync`
methods. The host keeps ROM loading and playback on the scene owner while one
bounded worker synthesizes independent audio. Jobs expose progress, cancellation,
bounded errors and READY publication. Ten-minute duration, 256 MiB combined PCM
and 200,000 completed note-event limits apply before playback. Legacy defaults
remain synchronous. These are additive changes to the mutable 0.7 candidate;
the descriptor/runtime version remains unpublished 0.7.0 and the normalized
signature pin is regenerated in place.

Before changing the candidate surface:

1. Run `TestModApiSignatureSurface` and inspect every added or changed line.
2. Annotate every newly reachable engine type.
3. Narrow third-party signatures to JDK or engine-owned contracts instead of
   allowlisting dependencies.
4. Add a JDK type only to the explicit platform allowlist after compatibility
   review. Package-prefix exemptions are forbidden.
5. Regenerate the sorted LF snapshot and rerun the Javadoc, SDK packaging, and
   maintained sample tests.

On PowerShell, the snapshot tool needs both compiled engine classes and ASM. Use:

```powershell
mvn "-DskipTests" compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
$cp = "target/classes;$((Get-Content target/mod-api-snapshot-classpath.txt -Raw).Trim())"
java -cp $cp com.openggf.mods.code.ModApiSignatureSurface --snapshot |
    Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-0.7.txt
```

The pre-commit and CI policy hooks require the candidate pin to be staged whenever a
`@ModApi` source changes its declaration text: comments are ignored; interfaces,
records, enums and annotation types compare every remaining line; classes compare
annotations, type declarations and `public`/`protected` declarations. A body-only
edit to an annotated class therefore commits without a pin change, and
`TestModApiSignatureSurface` remains the exact check.

Release packaging generates exact-inventory Javadoc and attaches
`openggf-mod-sdk` and `openggf-mod-sdk-javadoc` classifier jars beside the engine
artifact. Architecture guards ignore only the `@ModApi` marker edge and the
release tool's exact inventory lookup; the annotation does not establish runtime
ownership.

Until publication, ordinary compatible or incompatible 0.7 development keeps
`0.7.0` and replaces the major/minor candidate pin in place. At promotion from
`master`, change the descriptor status and published-baseline set, rename the pin
to the immutable full SemVer form
`mod-api-signatures-MAJOR.MINOR.PATCH.txt`, and update `ModApiVersion`'s supported
contracts. A later engine accepts a creator manifest when its `engineApiRange`
contains either the current contract or any retained published contract.

Published pins are immutable evidence: on a later configured release line,
additions are allowed but removals and signature changes are not. A maintenance
patch on the same release line must have exactly the same recursive surface as the
previous published baseline. Any incompatible successor requires an explicit
migration and compatibility decision.

New creator APIs should prefer narrow engine-owned facades and immutable value
types. A public or protected signature may not leak an unannotated engine type or
an unreviewed third-party type. Before publication, removals, narrowing changes,
record-shape changes, and unannotation require intentional candidate review and a
pin rewrite; after publication they require an explicit compatibility decision.

## 0.7 reset inventory

The reset removed provisional compatibility shims before establishing the first
baseline. `TestNoProvisionalModApiShims` is the executable evidence that these
members remain absent.

| Removed member or family | Evidence marker |
| --- | --- |
| Legacy `CheckpointState.RewindState` constructor | canonical-record constructor assertion |
| Legacy `CameraSnapshot` constructor | canonical-record constructor assertion |
| Legacy `GameStateSnapshot` constructor | canonical-record constructor assertion |
| Legacy `WaterSystemSnapshot.DynamicWaterEntry` constructor | canonical-record constructor assertion |
| Legacy `CollisionRules` constructor families | exact canonical-plus-`AirCollisionRules` constructor set |
| Legacy `ObjectInteractionRules` constructor | canonical-record constructor assertion |
| Legacy `PlayerAnimationRules` constructor | canonical-record constructor assertion |
| Legacy `PlayerCapabilityRules` constructor | canonical-record constructor assertion |
| Legacy `RingRules` constructor families | canonical-record constructor assertion |
| Legacy `SidekickCpuRules` constructor | canonical-record constructor assertion |
| Legacy `PerObjectRewindSnapshot.SidekickCpuRewindExtra` constructor | canonical-record constructor assertion |
| Legacy `PerObjectRewindSnapshot.PlayerRewindExtra` constructor | canonical-record constructor assertion |
| Legacy `ModZoneContribution` constructor | canonical-record constructor assertion |
| Legacy `PlayableSpriteMovement.RewindState` constructor | canonical-record constructor assertion |
| Legacy `PlayableSpriteController.RewindState` constructor | canonical-record constructor assertion |
| Legacy `TraceMetadata` constructor | canonical-record constructor assertion |
| `SpriteManager.drawUnifiedBucketWithPriority(int, GraphicsManager, Runnable, Runnable)` | reflected method-absence assertion |
| `ObjectManager.snapshotPersistentDynamicObjectsForTransition()` | reflected method-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformCarrySolidContactObject` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffPending` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffXVel` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffYVel` | reflected field-absence assertion |
| Provisional compatibility comments and marker phrases in production Java | exact empty marker allowlist |

The underlying current behaviors remain available through their canonical 0.7
owners; this inventory records deleted shims, not removed product capabilities.
Zone declarations use named `ModZoneContribution.singleAct(...)` and
`multiAct(...)` factories; the record retains only its canonical constructor.

## Historical development corpus

The following dated documents preserve development history and scope provenance.
They are not current version authority:

- `docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md`
- `docs/superpowers/specs/2026-07-13-example-mods-design.md`
- `docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md`
- `docs/superpowers/specs/2026-07-14-mod-gap-fixes-design.md`
- `docs/superpowers/specs/2026-07-14-rom-art-remix-sample-design.md`
- `docs/superpowers/specs/2026-07-14-s3k-mod-zone-adapter-design.md`
- `docs/superpowers/specs/2026-07-22-mod-api-0-7-reset-design.md`
- `docs/superpowers/plans/2026-07-13-mod-rom-art-intake.md`
- `docs/superpowers/plans/2026-07-13-sample-flappy-mod.md`
- `docs/superpowers/plans/2026-07-13-sample-platformer-mod.md`
- `docs/superpowers/plans/2026-07-14-mod-gap-fixes.md`
- `docs/superpowers/plans/2026-07-14-mod-gameplay-policies.md`
- `docs/superpowers/plans/2026-07-14-native-tails-flappy.md`
- `docs/superpowers/plans/2026-07-14-rom-art-remix-sample.md`
- `docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md`

The root release-policy descriptor is the sole current Mod API version authority.
The maintained
creator workflow and format documentation begins at
[`docs/modding/index.md`](../modding/index.md).

The KiS2 touch continuation adds explicit `glideAttacksEnabled` player capability
and `bossHitEndsActiveGlide` interaction components to the unpublished 0.7
candidate. Rule producers pass both values explicitly. No older constructor
overload is retained; the current candidate signature pin is regenerated in
place and the descriptor/runtime version remains 0.7.0.

Player rewind snapshots now distinguish an actual solid-contact binding from a
zero placed-object ID. Event-created S3K solids can use ID zero; clearing contact
also leaves the ROM interact slot intact. `PlayerRewindExtra` carries the explicit
binding bit (alongside the campaign’s captured tile priority and display bucket),
and `AbstractPlayableSprite.hasLatchedSolidObjectBinding()` exposes
it to restore reconciliation. This updates the unpublished 0.7 candidate pin in
place; the descriptor and runtime version remain 0.7.0, with no published baseline.

Player raw-frame ownership now separates object scripts from player abilities.
`AbstractPlayableSprite.setAbilityMappingFrameControl(boolean)` and
`PlayerRewindExtra.abilityMappingFrameControl` preserve that distinction through
rewind, while the existing raw-frame query continues to cover both owners.
The unpublished 0.7 candidate pin is regenerated in place; the release descriptor
and runtime API version remain 0.7.0, with no published baseline changed.

The unpublished 0.7 candidate also exposes `GameModule.gameplayStepsPerFrame()`
(default one, delegated by `DelegatingGameModule`). Interactive forward level play
accepts 1–32 complete simulation steps per presentation frame. Fractional-rate
accumulators belong to a registered module rewind adapter. Canonical `GameLoop.step()`
remains one tick for deterministic tools and traces; pause, rewind, externally driven
movies, transitions and non-level scenes retain their existing pacing. The candidate
version remains 0.7.0 and its normalized pin is regenerated in place.

`GameModule.scriptedRewind()` (default `null`, delegated by `DelegatingGameModule`)
returns a `com.openggf.game.rewind.ScriptedRewind`, which lets a module run the live
rewind itself in level play: the host records history whenever one is returned, rewinds
while `requested()` holds (`stepsThisFrame()` steps per presentation frame, stopping at the
first step whose restored state passes `reachedTarget()` or at the history floor), then
calls `ended(boolean)`. The player's live rewind setting still alone arms the rewind key,
and stock modules return `null`, so their rewind is unchanged. Additive to the unpublished
0.7 candidate; the descriptor and `ModApiVersion` remain 0.7.0 and the pin is updated in place.

`GameModule.gameplayAudioPlaybackRate()` supplies the matching continuous audio
rate for interactive custom pacing, delegated through `DelegatingGameModule`.
The host bounds it to 1–32 and releases ownership outside paced play. This is an
additive change to the unpublished 0.7 candidate; the descriptor and
`ModApiVersion` remain 0.7.0 and the normalized candidate pin is updated in place.

`TitleScreenProvider.startZoneIndex()` (default 0) lets a title choose the zone a
one-player exit starts on (act 0; out-of-range values start zone 0), and
`GameModule.showsTitleCardActNumber(int, int)` (default true, delegated by
`DelegatingGameModule`) lets a module hide the act number on Sonic 1 and Sonic 2
title cards. Both are additive to the unpublished 0.7 candidate; the pin is updated in place.

`GameModule.onNewGameFromTitle(GameStateManager)` supplies game-owned initialization
after the shared title/level-select new-game reset. Its default is inert and
`DelegatingGameModule` forwards it to the wrapped module. S1 uses it to clear its
emerald inventory and special-stage cursor, matching `PlayLevel`; Continue and
ordinary level resets do not invoke it. This is additive to the unpublished 0.7
candidate: the normalized pin is updated in place, while the descriptor,
`ModApiVersion` and published pins remain unchanged.

`GameModule.requiredDisplayAspect()` (default `null`) names a `display.aspect` preset
the session requires; interactive launches apply it as a session override after patch
resolution, so the master title restores the player's aspect and trace test mode still
resolves native 4:3. `GameModule.suppressesLevelSelect()` (default `false`) makes the
host ignore `LEVEL_SELECT_ON_STARTUP`, route a title level-select exit to one-player
play, and ignore the in-level level-select key. Both are delegated by
`DelegatingGameModule` and additive to the unpublished 0.7 candidate; the pin is
updated in place.

Putt Putt Paradise's course controller and bounded scene values extend this same
unpublished candidate. Atlas residency lookup and packed `PatternVersion` data
remain engine-internal presentation details; creators use the ROM-backed scene
contract instead. The candidate pin is regenerated in place when removing the
initial internal-sampler exposure; version/status and published pins are unchanged.


Final Putt Putt Paradise polish keeps the mutable candidate controller facade
semantic: `CourseControl.PlayerState` / `playerState`, `launchRolling` with native
signed 8.8 velocities, and explicit `loadLevel(zone, act)` replace provisional
ball/loft/EHZ-specific operations. Exact registered character construction is
completed before the single live roster is replaced. Sound, shot limits and
scoring remain creator-owned. `MenuInput.Pointer` adds shared logical menu mouse
coordinates without adding a window handle to the creator signature. Both changes
are represented by the regenerated `0.7` candidate pin; published pins and the
release policy descriptor remain unchanged.

The final presentation follow-up adds the bounded `RewindPresentation` record
and optional controller request to the mutable 0.7 candidate. Default/null
requests retain stock rendering. This is a view-only value: native admission,
world checkpoints and undo permission remain unchanged. Candidate pins and
`ModApiVersion` are refreshed while the release descriptor is retained; API version/status
and immutable published baselines do not change.

The turn-handoff follow-up adds `ButtonPrompts` (with its `Button` enum) and
`CourseControl.buttonLabel(player, button)`. A prompt names one local player's own
binding on that player's last intentional device: the bound key ("Space", "Right
Shift"), or the physical pad button that produces the action in the assigned pad's
family ("X", "Square", "West"); `REWIND` names the shared rewind key or the primary
pad's bumper. Labels are presentation values derived from live bindings and never
alter input, replay rows or gameplay. The regenerated 0.7 candidate pin adds 17
signature lines; `ModApiVersion` documents the capability; the release descriptor,
API version/status and published baselines are unchanged.


Shot-audio follow-up promotes bounded reverse presentation into the mutable 0.7
candidate. `SceneReplay` records immutable value scenes with adaptive interior
sampling; `CourseControl.recordAudioReplay` returns a session-owned `AudioReplay`
that records the final heard PCM and reverses it at the caller's view rate.
The producer retains ownership of PCM buffers, cursors and sink transitions.
Neither resource supplies gameplay state or changes developer rewind ownership.
Creator rules still select permission, duration, checkpoint restore and score
refund. Session teardown closes forgotten audio resources even after a creator
cleanup failure. The candidate signature pin and `ModApiVersion` commentary are
updated together; the release descriptor, candidate version and published pins
remain unchanged. See the [creator replay recipe](../modding/content-mods.md#replay-a-view-and-its-audio).

Mod scenes add the `com.openggf.mods.scene` package to the candidate surface:
`ModScene`, `ModSceneFactory`, `SceneContext`, `SceneCanvas`, `SceneDraw`, `SceneImage`,
`SceneSprite`, `SceneArt`, `SceneRomArt`, `SceneSpriteSet`, `RomSpriteRequest` (with its
`Compression` and `DplcLayout` enums), `SceneBackdrop` (with its `Band` record),
`SceneLevelStage`, `SceneMouse`, `SceneButtons`, `SceneKeys`, `DebuggableScene`,
`SceneAudio` and `SceneStorage`, plus
`ModContext.registerStartupScene`, `ModContext.requireDisplayWidth` and
`GameMode.MOD_SCENE`. The mouse wheel stays off the pinned `InputHandler`: the engine-internal
`control.MouseWheel` (one per input handler, `MouseWheel.of(input)`) collects scroll movement
for whichever screen reads the mouse, the master title or a scene. The engine finds the registered scene as the effective module's
`getGameService(OwnedSceneFactory.class)`, so the `game` package never depends on mod types.
`mods.code.OwnedSceneFactory` is engine-internal and only that package can construct it, so a
patch cannot forge a scene's owner or run one outside the owner's fault boundary; a
`ModSceneFactory` served under its own type is ignored. The creator package holds only the API;
the host (`ModSceneHost`, `SceneServices`, `SceneRomArtFactory`, the renderer, recording canvas,
texture cache, storage, PNG and sprite rasterisers) lives in the engine-internal
`com.openggf.mods.scene.host` package, which no signature reaches. It reads image pixels
through `SceneImage.pixels()` (one copy per upload) and builds `SceneMouse` values through a
private-constructor lookup, so the API types need no package-private hooks. `GameLoop`'s scene
entry points are package-private for the same reason. The engine closes an open scene at shutdown (so `ModScene.exit` runs) and
deletes a scene image's GPU texture once the image goes 120 frames undrawn, uploading it again
if it is drawn later.

Value types are built to evolve without breaking callers: `SceneDraw`, `SceneMouse` and
`SceneLevelStage` are final classes rather than records, so no canonical constructor is pinned.
`SceneDraw` is built only from `plain()` and its `with...` methods; `SceneMouse` only by the
engine (`none()` for tests), with left and right `Down`/`Pressed`/`Released` edges and
`lastInputWasMouse()` (the latest input was the mouse rather than a key or pad press) in place
of the old latching `active`; `SceneLevelStage` keeps its `(x, floorY, width, floor)`
constructor and gains value equality. `RomSpriteRequest` stays a record but its constructor
rejects a null DPLC layout, a DPLC address below `-1` and a size on compressed art, and gains
the `uncompressed` and `compressedWithDplc` factories. Images built from pixels have one
constructor, `new SceneImage(width, height, argb)`; `SceneArt` keeps `png` and `rom`.

Input and drawing helpers replace the raw numbers and loops scenes used to copy.
`SceneButtons` (the pad's own `SACBRLDU` bits) and `SceneKeys` (the engine's GLFW key codes
under stable names) are constant holders; no signature names them, so they are curated roots
in `ModApiSurfaceInventory`. `SceneContext.buttonDown`, `buttonPressed` and `buttonRepeated`
read player 1's pad by those bits; `buttonRepeated` uses the engine menus' own repeat
(`control.MenuRepeat`, now a public engine-internal class: 24 ticks, then every 4). Scenes
settle the back button on the Genesis convention: in a scene `input().menuAccept()` is A, C or
Start and `menuBack()` is B (the engine's own menus keep C as back). `SceneCanvas.drawBackdrop`
(a window and a full-screen overload) draws a `SceneBackdrop` exactly inside its rectangle as
default methods built on `drawRegion`, so test canvases need not implement them.
`SceneRomArt.tiles(address, compression, firstTile, widthTiles, heightTiles, columnMajor,
palette)` decodes raw 8x8 tiles in row or column order. A palette-lines helper was considered
and left out: it would save one `System.arraycopy` per line.

`ModContext.requireDisplayWidth(width)` lets a patch mod fix its session's logical width to a
`display.aspect` preset width without a `GamePatch`: the frozen `ModRegistrationPlan` carries
the preset name (a new trailing component with a compatibility constructor for the previous
shape) and `ModBackedGamePatch`'s module returns it from `requiredDisplayAspect()`, so the
existing session-override and window-refit path applies it. Registration rejects other widths,
a second call and standalone manifests. `DebuggableScene` is an optional `@ModApi` interface
(`boolean debugJump(String)`) a scene implements so tools reach its screens without
reflection; the engine-internal host's `debugJump(String)` runs it inside the owner's fault
boundary through `OwnedSceneFactory`.

`SceneContext`, `SceneCanvas`, `SceneArt`, `SceneRomArt`, `SceneSpriteSet`, `SceneStorage` and
`SceneAudio` are implemented by the engine; creators use them (and may fake them in tests) but
the candidate may add methods to them. `SceneRomArt`'s zone pictures are abstract methods with
an explicit support query instead of defaults that returned `null`: `hasZonePictures(zone, act)`
is true exactly where `zoneBackdrop`, `levelOverview(zone, act, maxHeight)`,
`levelStages(zone, act, width, headroom, maxRise)` and `levelForeground(zone, act, x, y, width,
height)` produce pictures. Today that is five Sonic 3 & Knuckles acts, each in one
representative presentation state its Javadoc names (AIZ1 after the intro, AIZ2 as a fresh act
2 load, HCZ1 below the waterline, LBZ1 as loaded, SSZ1's backdrop as the cloud sea); they are
the acts the Slay the Robotnik example visits, and further acts or states are extension points.
`hasTitleCard(zone, act)` and `titleCard(zone, act)` return an act's stock title card as four
sprites (banner, zone name, "ZONE", act) from the card's own KosM art and mappings
(`game.sonic3k.titlecard.Sonic3kTitleCardArt`, reusing `Sonic3kTitleCardMappings` and the VRAM
layout `Sonic3kTitleCardManager` loads), with the ROM's slide-in, hold and exit timings
documented so a scene can animate it; Sonic 1 and Sonic 2 have neither yet. Stages come from the
engine-internal `level.render.LevelFloorScanner`, which reads the act's primary-path collision
the way a floor sensor does (layout cell, block, chunk descriptor flips and solidity bits, solid
tile height) and never touches a live level. The stock implementation converts values from the
engine-internal `level.render.ZonePictureSource`, which a game module offers as
`getGameService(ZonePictureSource.Factory.class)` (shared engine code has no per-game switch);
for S3K, `Sonic3kZoneArt` builds a detached
level from explicit inputs (no session, settings or live graphics), primes animated tiles into a
private tile array and rasterises on the CPU with `PlaneRasterizer`. Keeping the source in
`level.render` leaves `game` free of `mods` dependencies (the ArchUnit cycle ratchet). These are
in-place changes to the unpublished 0.7 candidate; the descriptor and `ModApiVersion` remain
0.7.0 and the normalized pin is updated in place.

### Mixed-ROM rhythm scenes

Sitar Hero extends the unpublished 0.7 candidate with `SceneArt.availableGames`
and `rom(gameId)`, `SceneContext.physicalInput` and `music`, immutable
`PhysicalInput`/`PhysicalInputEvent`/`PhysicalGamepad`, and the bounded
`SceneMusic`/`ScenePreparedMusic`/`SceneNoteEvent`/`SceneMusicPart`/`SceneMusicPlayer`
contracts. Existing scene implementations retain their default legacy behavior;
the production host supplies the new capabilities. The normalized candidate pin gains 183 additive signature lines and
is regenerated in place, with descriptor/runtime version still 0.7.0 and no
published baseline change.

The physical input values live in the canonical `control` package. Input capture
does not depend on `mods`; the scene host consumes those values through the
existing `mods -> control` boundary, without introducing a parallel device model
or expanding the frozen package-cycle baseline.

`baseGame: any` is restricted to startup-scene/display registration and expands
into separately indexed stock-game decorators. It cannot introduce game-specific
patches, objects, zones, characters or override declarations. Extra ROMs are
scene-owned catalog views; active session ROMs are borrowed, and disassembly
assets are never runtime fallbacks. S1/S2 detached stock pictures use explicit
ROM inputs, including first-frame animated tiles, without graphics/session
mutation.

The host's final-PCM override `audio.presentation.ScenePcmSource` is an explicitly
audited engine-internal terminal reached through the legacy `AudioManager` root.
Its members remain outside the creator signature surface and SDK, although
the legacy host method names the type; creators receive bounded song preparation and player operations instead of arbitrary mixer injection. The
engine-internal pin adds that one type; platform allowlists stay unchanged.
Music judgments follow consumed samples, input follows monotonic observed events,
and the example alone owns curated charts, GH III reference rules and calibration.
No diagnostic/trace rows become gameplay authority.

### Scene-owned direct peer messaging

`SceneContext.network()` reaches `SceneNetwork`, `ScenePeer`, `ScenePeer.State`
and `ScenePeer.Message` through the recursive creator surface. The default
context method remains unsupported for existing fixtures. These four annotated
types enter the normalized 0.7 candidate pin and exact-inventory SDK Javadoc;
`mods.scene.host.network.ManagedSceneNetwork` and its sockets/selector worker
remain outside that surface. No platform allowlist expansion is required.
`ModApiVersion` remains 0.7.0, matching the unchanged candidate descriptor;
there is no publication or new version baseline.

The transport is explicitly admitted by user host/connect actions, with one
active endpoint, bounded strict UTF-8 messages/queues and one lazy selector
worker per scene. Numeric addresses and `localhost` avoid a resolver whose
native blocking could outlive cancellation. Selector I/O was chosen over
blocking read/write threads because write deadlines and cancellation must
remain enforceable even when a peer stops reading. Scene callbacks only
exchange bounded queues and state. The scene host closes networking on exit
requests and every callback fault, rather than depending on a later caller
cleanup. The [scene handbook](../modding/guides/mod-scenes.md#direct-peer-messaging)
owns framing, deadlines, clock meaning and direct-connect limitations.

## Bounded ROM-part cue candidate

Sitar Hero's fumble follow-up adds `SceneMusicPlayer.PLAYHEAD` and the default
`cuePart(long,int,double,double,double,double)` method. It exposes no PCM, synth,
thread or mod-specific instrument type. The production host mixes at most six
enveloped, pitch-gliding residual fragments, validates duration/rate/gain/balance,
smooths audibility over 4 ms, freezes cues on pause and releases them on stop.
Unsupported legacy hosts decline a cue instead of losing basic playback. The
normalized 0.7 candidate pin gains exactly two additive entries, with no removals.
`ModApiVersion` documents the capability; the policy-generated release descriptor
retains unpublished candidate `0.7.0` and its existing schema/publication state.
See the [creator recipe](../modding/guides/mod-scenes.md) and
[fumble implementation plan](plans/2026-10-08-sitar-hero-fumble-feedback.md).
