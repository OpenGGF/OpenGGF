# Shared implementation pitfalls

**A handler capture is not proof of a stored world checkpoint.** Cold headless
loading can leave parallax's active selection uninitialized even when a zone
handler is available directly. Initialize through the ordinary production entry
before retained history, then assert the actual registry snapshot carries that
history. A fresh direct capture after the registry capture proves a different
boundary. R7F observed no physical-plane restore in the former Slot unit; its
true/false authority flags correctly rejected preservation. Separately, a real
exact restore must survive later geometry-cache invalidation and same-level
metadata rebind, while ordinary live/scene invalidation still discards residency.
Keep the strict authoritative-and-restored gate, invalidate derived caches, and
consume the transient restore marker at the existing postrestore reset. See the
[matched R7G evidence](validation/levels/s3k-bonus-stage-transitions.md#r7g-actual-captured-plane-inlet-and-bounded-repair-qualification--2026-10-17).


Screen-relative sprites must cancel the camera origin that the renderer actually
subtracts. S3K endings deliberately write `Camera_X_pos_copy`/`Camera_Y_pos_copy`
independently of physical camera words: Tails departure can have physicalX0 and
copy608 for hundreds of passes. Adding physical camera coordinates to a native
bit2-clear sprite or fixed presentation mask hides the native scene. Project from
copies plus shake, refresh derived playable offsets at the existing post-camera
Render_Sprites boundary, and preserve both native words. See the
[ending lifecycle evidence](designs/2026-10-13-s3k-ending-lifecycle.md).

Capture boot must preserve an existing native camera binding. After ScreenInit
sets `Scroll_lock`, even `Camera.setFocusedSprite` resets physical and render-copy
coordinates; guarding only the following forced snap leaves that overwrite.
Keep both operations inside the unfrozen entry path. Assert each scene at its
actual initialization boundary: D01 during load, SSZ arrival before initial
Process_Sprites. A first-row image can pass while terrain corruption moves into
another column, so retain physical/copy and full-scene checks. See the
[ending capture diagnosis](designs/2026-10-13-s3k-ending-lifecycle.md#capture-boot-preserves-a-native-camera-binding-2026-10-14).

Read the relevant section when working on coordinates, objects, rendering, rewind,
headless tests, or audio. S3K-specific routing is in [AGENTS_S3K.md](../../AGENTS_S3K.md).

The things that cost the most time when missed.

A headless boot must release its audio base-ROM reference before closing the
owned ROM. A later title boot installs its audio profile before the new ROM;
otherwise the loader reads the previous, closed FileChannel. Close/reboot
therefore retire that reference at the ROM owner's boundary, while preserving
existing dependent-owner failure/retry semantics. The saved-slot HPZ
roundtrip reproduced four second-title errors before the repair, then passed
actual disk/session reloads; see the
[task evidence](plans/2026-10-13-s3k-playable-completion.md#saved-slot-hpz-to-ssz-roundtrips-2026-10-17).


**Changing dispatch order must preserve captured physical parent ownership.**
S3K `CreateChild1_Normal` stores the parent's SST address in `parent3`, not a
Java identity. Removing an existing boss and re-adding that instance at another
slot after child birth is an invalid inlet for ordinary family behavior. To
exercise warnings before/after barrel anchors, allocate unrelated occupants
normally before five-child birth and free those holes before warning admission;
keep the root and captured component slots unchanged. A lower warning reads the
barrel pose before its dispatch, while a higher warning reads the updated pose.
Check actual physical owner/ObjectRef and both poses, then force recreation and
repeat the continuation. See the [Second Strike fixture evidence](plans/2026-10-13-s3k-playable-completion.md#second-strike-warning-fixture-physical-ownership--2026-10-17).

**A “pressed” getter may expose held input rather than the native press byte.**
HCZ splash `sub_38534` passes the complete Ctrl_1/2_logical word in d5;
`loc_385D2` masks its low pressed bits with `$0070`, while sustain direction
checks mask the high held byte. `isJumpPressed()` exposes held/forced input;
this owner must use `isLogicalJumpPressActive()`. A raw hardware edge also cannot
stand in for a CPU-written logical P2 edge. Independently activate skim and test
held/no logical edge, logical edge/raw neutral, and held input on a raised curve;
a spindash-held route alone does not identify which input owner was wrong.
See the [HCZ8329 evidence](audits/trace/2026-10-14-s3k-monkey-dude-sst.md#canonical-returned-hcz8329-water-skim-input-owner--2026-10-17).

**Optional presentation does not erase native object ownership.** S3K `Level`
writes the title owner at `Dynamic_object_RAM+5`, absolute SST8, and creates
four higher-slot children before `Load_Sprites`. Omitting its visible loop still
retains the `$16` wait, child retirement and next-pass owner teardown. Intro
controllers at `Dynamic_object_RAM+2` use absolute SST5 instead. Capture the
actual parent links and surviving graph; freeing these slots changes ordinary
later allocation. A production `LevelLoadContext` may leave zone/act unset, so
an entry owner must use the admitted destination rather than those unset values.
See the [fresh-title audit](audits/trace/2026-10-14-s3k-fresh-title-sst.md).

**Concurrent hub music must retain device ownership.** A PCM filter around an
`OpenAlPcmSink` hides its concrete type from `AudioManager.update()`: retain and
service the original device on its owning thread or queued audio never starts.
A second process-global OpenAL context also steals a suspended game's context.
The optional hub owner requires `ALC_EXT_thread_local_context`; unsupported
isolation falls back to silence. The two-context regression uses OpenAL's null
driver and checks that opening, pumping and closing the hub leaves gameplay's
context and AL dispatch intact (October 15 menu polish).

**Immediate text does not follow deferred sprite commands.** Ghost sprite passes
prepare queued world draws; drawing `PixelFontTextRenderer` there writes text
before the world, which later overwrites it. Collect nameplates during preparation
and draw them in the hosted-run overlay after the world, before its HUD. A name
present in the ghost pose or HUD does not prove an overhead label was displayed;
inspect actual gameplay pixels (2026-10-22 nine-player name test).

**Capture long filenames on a fresh menu font.** The immediate full-size
`PixelFont` path's `ensureBatchCapacity` replaces its vertex array without copying
already-added glyphs when it grows within a string. A fresh ROM-picker capture
therefore showed only the end of a long configuration filename. Host menu pages
can use the established `beginMegaBatch` / `endMegaBatch` path, whose growth
preserves vertices; the path picker now does so with `finally` cleanup. A warm
font or short labels can hide the problem. The shared immediate renderer was
outside this menu-only fix (2026-10-15 master-title usability review).

**Authoring art and terrain share one origin.** Retain detached ROM sheets from the
normal asset load; requesting a new module provider during drawing can return an
unloaded provider (S1 does this deliberately). A free editor camera has no live
parallax/deformation update, so use its own Y for foreground placement rather
than the parked Plane A VScroll. Inspect both sprites and floor pixels. Capture
each ROM in a fresh JVM when reusing native palette-publication resources across
boots would contaminate the picture. AIZ's initial palette belongs to its intro;
select the ROM-backed static authoring palette only on the first library copy,
never by advancing gameplay or overwriting a reopened project's edited colors
(October 2026 visual-polish viewer pass).

**Detached editor art must not retain runtime providers.** Keep an editor picture catalogue in a weak level-keyed presentation sidecar,
outside the level/checkpoint graph, with detached CPU art and value metadata only. Do not retain a live object-art provider or a closure
that captures it: level references can appear in opaque checkpoint graphs, and
the provider can lead to ROM file channels or runtime managers. Pass the provider
explicitly while preparing an authoring library, then release it. Even detached
pattern banks made the checkpoint graph exceed its bounded traversal budget when
stored directly on the level.
`TestPuttPuttParadise.opaqueCourseCheckpointGraphDoesNotRetainMutableSessionOwners`
caught this during the October 2026 editor polish pass.

**Editor rendering uses editor-owned managers.** Once an authored document enters
`EDITOR`, `GameServices.camera()`, `level()` and `sprites()` require a gameplay
mode and reject the call. Offscreen editor examples use the active
`EditorModeContext` camera, level manager and sprite manager, matching Engine's
retained managers. Global graphics ownership remains shared. Do not open a fake
gameplay session just to satisfy these accessors; it would change the state being
pictured (October 2026 editor-guide capture).

**Native X words need unsigned comparison at the Java boundary.** Playable
centre X and camera maximum X are exposed as signed Java shorts, while authored
placements use unsigned words. In S1 Sign_Touch and S2 Obj0D_Main, native sub.w
with blo/bcs and bhs compares the unsigned difference; do not subtract a signed
centre from an unsigned placement. Sign_SonicRun and Obj0D_Main_State3 add.w the
walk-off offset, then cmp.w/blo: truncate the sum to 16 bits and compare both
words unsigned. Goals around 0x8000 and camera bounds around 0x7F00 exposed both
errors during editor completion (host fixes 82bf7929c). Test activation, crossing
the signed boundary, and native addition wrap separately.

**Decoded storage capacity can differ from a layer's logical height.** S1's
cleared eight-row background RAM must not enlarge foreground geometry merely
because both layers share a `Map`. Keep the previous declared-layout maximum
for the foreground accessor and expose native BG capacity independently. GHZ1's
5/1 layout became 8 rows in storage; treating that as eight foreground rows
changed Infinite Sonic's terrain candidates (GitHub issue sweep, 2026-10-13).

**Native score words are tens of displayed points.** S1/S2 `AddPoints` and S3K
`HUD_AddToScore` add native bonus words while the ROM HUD supplies a final zero.
The engine score stores points. Keep native counters for tally timing, multiply
awards by ten, and verify the actual glyph layout: S1/S2 results have variable
columns followed by `Hud_Base`'s fixed zero, while S3K's BCD sprite loop rotates a
seventh zero nibble. Clearing the fixed zero makes a bonus ten times too small;
scaling the variable digits as well makes it ten times too large. At zero, clear
all variable columns to avoid `00`. The October 13 issue sweep's ROM pattern and
award regressions exercise both ends.

**A background layout needs its presentation art and event palette.** Starfall
Frontier's background revision (base `68d1e03482`, 2026-10-08) found numbered
filler inside otherwise valid S3K background chunks. Append both block/chunk
banks as `LoadLevelLoadBlock2` does, then prime the appropriate AniPLC frame
and direct `AnimateTiles_*` uploads into a private picture bank. SOZ's dispatch
names an LRZ list that its custom routine never executes; blindly priming that
list loads lava over desert art. ICZ1's outdoor plane also needs
`ICZ1_SetIntroPal` line-4 writes, otherwise its mountains use the indoor cave
colours. Check the composed picture, not just successful decompression or a
non-null image. A static creator picture is not a live zone animation timeline.

**A static background picture is not the stock scroll plane.** Three Islands' viewport-only
fix (`1ede498795`, corrected in the 2026-10-10 follow-up) drew every pixel but still exposed
empty layout rows and flattened S1/S2 parallax. Coverage assertions alone missed both.
Use the engine-owned `SceneBackground` for supported moving views; verify scanline offsets,
populated source periods, lower camera positions and the native fixed-view cases (EHZ Y,
DEZ X/Y). Additional speed multipliers on an already-deformed background distort its layers.

**A ROM mapping bank is not an animation sequence.** In the Starfall Frontier
biome revision (base `e6844866ed`, 2026-10-08), Rhinobot's mapping frames 0/1
face left, frame 2 is its brake pose, and frame 3 faces right. Cycling all four
therefore reverses the visible direction even with correct xflip. Follow the
owning object's pose selection (`Obj_Rhinobot` / `sub_86FF8`), or explicitly
curate the creator's locomotion frames. Check both orientations through every
animation phase with decoded images; a UV-only test or one screenshot misses
an intrinsically reversed pose. Facing should follow AI intent, since damage
recoil reverses velocity without changing the target.


Super Sonic Bros. repeated this in the 2026-10-20 facing audit (base `4ffa7d4f50`):
Mecha Sonic's raw art faces left; Sonic $B4 and Knuckles $B5 hang left. `SceneDraw.flipX`
is a geometric mirror, not semantic facing. Compose source orientation, desired
pose direction and actor facing once. Do not normalize every frame globally:
Sonic $CC is left-facing when used alone but belongs unchanged in a full spin.
Egg Robo's `sub_91930` mirrors child offsets using the parent's final render flags;
mirroring just the image breaks the assembly. A direction change before SKID also
means its art must depict the old travel direction. Inspect decoded ROM pixels,
then test complete poses in both directions (including accessories and offsets).
See [the mod audit](audits/2026-10-20-super-sonic-bros-facing.md).

**A scene kit's zone number is not its decoder level index.** When re-encoding
S1 kit blocks into an additive act, use the decoder's level table, not the public
SceneLevelKit zone ordering. Starpost Real Ruins (base `3da4cd930`, 2026-10-10)
needs MZ `0x86+act`, LZ `0x83+act`, SBZ `0x8F+act`. Also reserve the original
placeholder palette claim explicitly: reusing Green Hill's line3/colour15
claim failed for Scrap Brain pattern556, whose opaque colours fill that line.
A separate original line1/colour2 placeholder permits the existing encoder to
allocate the whole source palette; validate every source act rather than one band sample.

**Deferred native effects retain their creator owner.** A native shield spawned
inside a creator callback is still owned by that callback's fault boundary.
During player-refresh rewind, restore its captured owner as well as the dynamic
slot and ObjectRefId. Real Ruins exposed null versus starpost-valley owner in a
full registry replay; `29c3e1755` fixes the pending power-up path. A native class
name does not imply a null owner. Fixtures also need the production mod class
resolver when restoring creator and creator-owned native objects.

**Coordinates.** ROM `x_pos` / `y_pos` map to `getCentreX()` / `getCentreY()`. `getX()` /
`getY()` are top-left render bounds — mixing them produces a ~19px vertical offset and
wrong collision. When porting disassembly that touches `x_pos` / `y_pos`, default to the
centre APIs unless the code is explicitly about sprite bounds, render extents, or collision
box edges; route playable-sprite native writes through `NativePositionOps`. If camera,
collision, object anchoring, or scripted movement drifts relative to the player, suspect
this first. The debug HUD `Pos:` line prints top-left, **not** ROM centre — don't quote it
against a disassembly trace without converting. Y increases downward (Mega Drive
convention). VDP coordinates in the disassembly are offset by +128; the engine uses direct
screen coordinates.

**Missing terrain is not a grounding signal.** S3K horizontal spring
`sub_2326C` rejects `Status_InAir`. Its engine ordering compensation may accept
actual negative paired-foot terrain distance, but `ObjectTerrainUtils.noCollision`
also describes real empty terrain, not just absent test services. Never substitute
`player centreY >= springY` when both probes miss. Supply actual collision tiles
in isolated landing fixtures and use the object's injected level/collision context;
zero distance remains airborne on this pass. This removes the test-only exception
left by `50a45804f` after the ICZ early-launch correction.

**Replacing a player is not initializing a camera.** `Camera.setFocusedSprite`
also resets live and render-copy coordinates to the sprite's top-left. During
held gameplay, preserve and restore the camera snapshot around roster replacement;
the restore rebinds its target through the new registered main character. Merely
changing the target can hide the next golfer under the HUD until physics resumes.
`TestCourseControl.characterReplacementPreservesTheEntireCameraViewAndRebindsItsTarget`
and the independent-golfer turn tests cover both view and target identity.

**Object-controlled mod menus do not pause player protection clocks.** The playable sprite
still decrements invincibility and hurt recovery while a mod holds its movement. Freezing a
mod's Fever HUD alone can therefore consume the actual protection behind upgrade cards.
Capture the player timers on menu entry, hold positive clocks above expiry during the menu,
and restore the exact saved durations on release. The Survivors regression
`feverProtectionDoesNotExpireBehindLevelUpCards` checks the player clock as well as the HUD;
menu-owned saved fields must participate in rewind too.

**Menu fills do not blend on their own.** `MenuPixelFont.fillRect` and
`TexturedQuadRenderer` submit tinted quads without touching GL blend state. With
blending disabled, the usual state after level rendering, a 0.66-alpha dim writes
its own colour and replaces the held frame; panels drawn at 0.94 look opaque only
by accident. Menus with translucent layers enable `GL_BLEND` around their pass, as
`MasterTitleScreen` and `SceneRenderer` do. `MutatorConfigurationScreen` restores
the caller's blend state and keeps destination alpha (`GL_ZERO, GL_ONE`), so
`GameplayCaptureTool` PNGs match the window; a capture PNG whose alpha is below 1
shows the same mistake (Mutator Lab polish, 2026-10-08).

**A forced SFX stop skips the driver's priority release.** Sonic 2 clears
`SFXPriorityVal` when an SFX track reaches its own stop (`cfStopTrack` /
`zStoppedChannel`, `s2.sounddriver.asm:3514-3535`). `AudioManager.stopAllSfx()`
kills SFX tracks without passing through that request model. The stock title
called it when its flashing star was deleted; the ROM does not
(`s2.asm:26748-26750`, `:27068-27162`). The latch stayed at Sparkle's `$70`, so
every later `$6F` `SndID_Blip` from a mod menu over the stock title was rejected
until some `$70`-or-higher SFX finished naturally. No production caller of
`AudioManager.stopAllSfx()` remains. `TestTitleScreenAudioRegression` checks the
latch and an admitted blip after the intro.

**Silent invincibility still has a native music-expiry request.** `giveInvincibility()`
does not itself start music, but the playable timer reissues the level song on expiry.
A mode keeping its own arena/boss soundtrack continuous must opt out through
`PowerUpRules.restoreLevelMusicAfterInvincibility`; do not suppress timer expiry or
misrepresent the boss flag, which couples music to gameplay state. Stock factories
retain the ROM behavior. Survivors checks Fever and monitor expiry for both leaders,
including restored expiry boundaries.

**Creator object ownership follows the registered placement key.** Returning
creator instances for untagged native placements from a custom registry does
not assign their callback owner: placed registration clears ownership for a
null spawn owner. Register a namespaced factory through `ModContext`, preserve
the ROM placement fields when tagging selected objects, and retain the
framework's backing registry. A wrapper that only forwards factory calls can
also hide its `Supplier` callback-boundary capability. Verify a real placed
object's owner scope through the production loader before and after forced
rewind recreation; dynamic-object containment alone misses this boundary.

**An installed stage provider is not an active stage.** Main-level services can
contain `NoOpBonusStageProvider`, or a native provider whose active type is
`NONE`. Testing provider presence grants main-level rings the bonus-puzzle
exemption. Query the actual active type; restoration back to the main level
must explicitly use the main-level award domain even before provider teardown.
The Mutator Lab expansion's native award and return controls exercise this
distinction (2026-10-08).

**Registry decorators must retain native semantic classification.** An
object-registering co-mod introduces `ModDecoratedObjectRegistry` even when the
Mutator Lab registers no objects. Factory forwarding alone hides the backing
registry's monitor/ring classification and silently disables placement filters.
Forward the engine-internal classifier for native placements; a namespaced,
creator-owned override remains outside that native classification. Preserve
placement indices and allocation order. Do not extend the creator API merely
to carry this internal capability (Mutator Lab expansion, 2026-10-08).
**Native mod registration does not establish API retention.** Experimental
runtime-loaded callbacks can resolve members that normal image reachability
trimmed, even after other mods registered successfully. Exact package retention
does not include subpackages: `com.openggf.game` omitted `GameRules.SONIC_2` and
both standalone modules aborted. Derive exact-class preservation from the
canonical API plus all static mod references, retain original `.class` resources
for validators, and run a metadata audit before creator execution. Prove the audit
rejects a deliberately pruned image; metadata presence still does not qualify
every invocation, gameplay callback or VM fault boundary.
Origin: [October 8 native mod follow-up](research/2026-10-08-graalvm-native-mod-feasibility.md#follow-up-both-standalone-mods-and-automatic-member-coverage).

**An engine-invoked method reference retains its creator's authority.** A direct
`StackWalker.getCallerClass()` check can hide a child-loaded lambda/method-reference
frame and see only the engine callback host. A creator returning
`ModStorageFactory::forOwner` reproduced that admission gap. Authority bridges
must inspect hidden frames and the first external, non-bootstrap defining loader;
matching a reported owner or binary class name is insufficient. Keep regressions
for direct calls, engine-invoked creator method references and legitimate engine
method references such as `List.forEach(registry::register)`. Preserve inherited
owned adapters while validating fresh publication against its verified owner.
Use the internal `EngineCallerAccess` inspection bridge for new admission gates;
its immutable JDK walker comes from the `EngineContext` bootstrap factory, without
locating a live gameplay session. Keep lower-level consumers on the utility bridge
and inspect a module-lineage provider's actual defining loader before calling it.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md).

**Returned concrete values can contain deferred creator callbacks.** An owned
interface proxy does not automatically bind callbacks inside a returned record
or concrete registry. The `InitStep`/`StaticFixup` records returned by
`LevelInitProfile` run actions later in level loading/reset, and
`PlayableCharacterRegistry` definitions contain
factory, art and respawn callbacks. Project these contracts explicitly through
bounded immutable values, preserve metadata and earlier-owned callback identity,
and test the later production consumer. Concrete captured services also need
provenance on their underlying state, not just their published rewind proxy.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md).

**Published creator controllers can be interface proxies.** Keep frame, draw,
rewind and close operations on the published controller. A headless fixture
needing creator-specific configuration or diagnostics obtains the separately
published concrete service and invokes its callbacks through the same retained
fault boundary; reflecting on the proxy's implementation class does not expose
creator methods or fields. The Golf peer fixture's missing `configure` method
was hidden by a second missing-field exception during shutdown. Preserve the
primary exception and suppress cleanup failures before attributing a dead peer
to networking. Assert registered owner identity and rewind participation instead
of assuming the concrete service and interface proxy are the same object.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md#development-full-run-fixture-repairs).

**Mockito defaults differ from production interface defaults.** A bare
`GameModule` mock returns null for `transformDecodedLevel`, although the real
default returns the supplied level unchanged. Fixtures exercising a real load
must invoke that default or provide the intended valid transformation, preserving
loaded-level identity and publication assertions. Conversely, `when` invokes a
real method on a `CALLS_REAL_METHODS` mock before installing the stub: use
`doReturn` to install an expert standalone identifier override when the fixture
deliberately has no declarative specification. Keep the real identity consumers
under test. These gaps stopped HUD publication and standalone routing fixtures
before their existing assertions could run.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md#development-full-run-fixture-repairs).

**Embedded Java snippets are executable API consumers.** Constructor and method
migrations include compiler text blocks, maintained samples and SDK templates.
A failed compiler-exit assertion can look like a runtime warning-count mismatch;
inspect captured compiler diagnostics before changing the later assertion.
`TestModTestKit` still called the retired five-argument zone constructor inside
a text block. Migrating it to `singleAct` retained the same declaration and
allowed the original missing-owner warning/recovery checks to execute.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md#development-full-run-fixture-repairs).

**An isolated packed-mod test needs its own rewind class resolver.**
`GameplaySessionFactory` normally reads the process-wide `ModSubsystem`. A test
that scans, validates and launches through a private `ModRuntime` must attach
that runtime's `ModClassResolver` to the session's `LevelManager` before loading.
Otherwise a correctly owned object can fail capture or recreation because the
session cannot resolve its private loader. The distributed testkit exposes this
as `rewindClassResolver()`. Check identical binary names from two different
owners against the actual loaded classes; never infer ownership from a name.
Origin: [October 7 mod framework readiness](plans/2026-10-07-mod-framework-product-readiness.md).

**Camera limits are not terrain extents.** Sonic 2's decoded `maxY` limits the
camera origin; `Sonic_Boundary_CheckBottom` compares the player centre with
`maxY + $E0`. A custom lost-ball rule based on `maxY` alone rejects valid low
floors. Boss-free courses also need the native outgoing camera extent: EHZ2's
egg-prison placement lies beyond the closed boss arena, and `loc_2F460` opens
the camera toward `$2AB0`. Derive the custom finish from ROM placements and
release its view bound explicitly, rather than simulating a boss to open it.

**Overlay text also passes through CPU sprite preparation.** An opaque
`GLCommandable` is rejected by the preparation guard even when its eventual GPU
operation is a rectangle. Use typed screen primitives whose immutable geometry
retains logical coordinates and fallback height; replay them through the supplied
render host. Keep the guard and existing world-relative primitives unchanged.
Exercise clipping, translucent pixels, nonzero cameras and replay through a
different manager/projection. Admit a deferred host by its actual defining loader
before virtual calls, preserving trusted host subclasses while rejecting creator
overrides. The Survivors font adoption exposed this boundary in focused session
`38496`; see [framework readiness](plans/2026-10-07-mod-framework-product-readiness.md).

**A title can select a different viewport after manager construction.** Refresh
the camera dimensions before level load, and rebuild render geometry from the
resolved presentation configuration. Trace playback intentionally keeps a320px
gameplay camera even with wider presentation; using that camera for render-cache
sizing breaks the native-camera/wide-presentation split. A scene-only width fix
still leaves the native framebuffer or
background period using the manager's old cache. Putt Putt Paradise's normal
title-to-level and 320→800 scene tests cover both boundaries.

**A persisted pixel-width write does not replace the resolved aspect overlay.**
`SCREEN_WIDTH_PIXELS` is derived from `DISPLAY_ASPECT`; in a synthetic test,
use `setSessionOverride` for an explicit width and assert the camera's resolved
dimensions before interpreting missing pixels. Survivors' full-font check requested
974 pixels with `setConfigValue`, but retained the native 320-pixel camera: local
pixel 312 at origin nine was correctly clipped at absolute X321. A session override
restored every pixel at scales one through three with unchanged projection,
translucency and 276 cached primitives. Keep production clipping intact.

**Touch listeners are not the ROM's special-touch branch.** The shared controller
notifies listeners for harmful contacts too. An object whose collision byte changes
category must gate its own property writes: SSZ MTZ orbs use harmful `$87`, armed
`$C6`, and an unlisted `$DA` branch. Ordinary `Touch_Special/loc_103FA` writes only
for listed sizes; `HyperTouch_Special` independently ORs 3 through the powered
attack port. Do not bank harmful touches or move the ROM's shared Hyper sidekick
effects into an individual object. See the regression and correction in `95b478717`.

**Unused ROM pointers need an explicit lifetime in snapshots.** DEZ tilting
bridge `loc_46EAC` switches sections to `loc_46F18/loc_46F28`, which never read
the old parent word at `$3E` again. The low section may retire while higher
sections still fall. Keeping that unused address as a Java object reference
makes whole-world capture fail after parent deletion. Clear it at the routine
boundary; do not hide a still-used reference from the codec. Observe surviving
sections through retirement in tests, rather than finding them only through
the now-retired parent link. SSZ column debris has a subtler boundary:
`loc_44C2C/loc_44C32` reports at delay zero but still reads parent Y on that
same update; detach only after that final read. Later negative-delay gravity
updates never dereference the pointer, even if the ROM word still contains it.

**A route splice needs more than equal visible coordinates.** Native word writes
preserve position fractions. After the SSZ MTZ touch correction, two cold input
sequences reached the same upper transport pixel but inherited different fractions;
one later missed a ground-jump edge and cloud bounce. Reauthor controller inputs
and replay the complete suffix. Do not clear fractions or copy a reference snapshot
to make the old input movie work. Even matching player fractions is not proof of
matching followers, object phases or the complete route. Keep declared local test
stimuli separate from controller-only reachability evidence.

**Destination camera policies must check the destination.** S3K's `InitCamera`
runs before `InitLevelEvents` replaces the previous zone runtime. A provider
that selects a camera policy solely from `GameServices.zoneRuntimeState()` can
therefore apply the source arena's policy to the destination. Final DEZ's
widescreen projection moved DDZ's initial camera to -240; native DDZ autoscroll
then masked it to $7F11. Keep the ROM scroll arithmetic and gate the presentation
policy on current zone/act as well as its runtime owner. The short
`TestDezFinalScreenEntry.outgoingDoomsdayLoadDoesNotInheritFinalArenaCameraProjection`
regression exercises this boundary without replaying the full fight.

**Checkpoint index is not used-post history.** `getLastCheckpointIndex()` models
S3K `Last_star_post_hit`; `getStarPostActivationMark()` tracks the separate
used-post high-water. A real death reload can restore the former while leaving
the latter empty. Read the ROM's field, not whichever flag happens to be set
by a positioned test's `saveCheckpoint`. SSZ's opening bridge incorrectly waited
at X+$C0 after death because it consulted used-post history, while ScreenInit
correctly skipped the arrival using the restored index. The cold bridge/reload
regression (`TestSszBridgeCheckpointCapture`, source fix `485e23cfc`) exercises
this distinction through actual GameLoop reloads. Reacquire the rewind controller
after a load too; inspecting the discarded controller does not measure the new
timeline's isolation.

**Character identity is not player-slot order.** MHZ2's `sub_65E72` indexes
`RawAni_65EB0` by `character_id` plus V-int bit 1. Treating the leader as Sonic
and every follower as Tails gave solo Tails Sonic's floor-grab frames, while the
usual Sonic+Tails pairing looked correct. Use the playable character identity
for character-indexed ROM data and retain slot checks only where the ROM uses
slots. Unit doubles must expose the intended identity: a generic
`TestablePlayableSprite("tails", ...)` still identifies as Sonic; use real Tails
for identity-sensitive checks. Follower instance names likewise are not character
keys. Cover both solo and paired placement of the same character.

**A one-shot animation write is not a forced pose.** Knuckles' slide get-up
(`Knuckles_Sliding.getUp`) and fall-from-glide landing write `anim=$22/$23`
once alongside `move_lock=$F`. The lock gates Move, not subsequent Duck,
Spindash or Jump writes. Forcing the landing animation until the lock expires
hid Spindash9 from Toxomister's `loc_8FE50` even while the real spindash flag
was set. Publish the native byte and let its owners replace it; changing the
consumer to inspect a convenient state flag conceals the upstream mismatch.
The LRZ cold-route investigation and regression are recorded in the
[bring-up audit](audits/2026-09-22-sk-zone-bring-up.md#knuckles-lrz-cold-route-cloud-escape-2026-09-25).

**Offscreen initialization gates can remove themselves.** S3K
`Obj_WaitOffscreen/loc_85B02` restores the caller's continuation, so later
dispatches skip the visibility gate. Rechecking the `$20` window froze Monkey
Dude's body while its arm kept running. Only the ordinary unload range retires
the object.

**A cleared raw-animation timer is not the first frame's delay.**
`Animate_RawMultiDelay` decrements before it tests. A timer cleared on a script
change (`loc_871C2`, `loc_87218`) therefore publishes the next mapping on the
very next dispatch. Seeding the timer with the script's delay shifts every phase.

**Object-control early returns can still write character state.** S3K
`Knuckles_Control/loc_165AE` clears `double_jump_flag` when object-control bit0
suppresses movement, before reaching `Animate_Knuckles`. Skipping movement
alone leaves glide physics active through a hanging-object capture and replaces
the object's release velocity on the next player pass. Release the engine's
glide animation override too, so the object's native `anim` write remains
visible, but distinguish ability-owned raw frames from object-control bit1.
`loc_16614` still honours that bit after the glide clear: FBZ's spinning pole
claims raw mappings at capture and does not reassert the gate every held pass.
Clearing a shared raw-frame flag therefore cancels the new object's ownership.
Capture both engine ownership fields for rewind; test the incoming ability,
capture handoff and subsequent held pass, not only an explicit forced pose.
Preserve `double_jump_property` and radii unless the owning ROM routine
writes them. The SOZ2 cold route exposed this at `sub_40F52`: its correct
`x_vel=-$200` was replaced by glide acceleration on the next frame.

**A held input row still owns a queue tail.** A native LEVEL lag iteration
skips gameplay but still services the Kos module/direct queues (`LevelLoop`,
sonic3k.asm:7940/7887). Use `TraceSuppressedRowClosure` in the live, recording
and standalone drivers alike.

**Reused position words.** A field named `x_sub` is not always a fraction.
KiS2 `Knuckles_BeginClimb` and S3K `Knuckles_Gliding_HitWall` store the grab's
native X word there, then the climbing routine compares it with `x_pos` and
detaches on displacement. Preserve the ROM's alias and instruction width;
keeping a separate top-left anchor or restoring a supposed fraction changes
both gameplay and replay after rewind.

**Player wall probes.** Object terrain helpers are not interchangeable with the
player's native wall scan. KiS2/S3K climbing uses the live LRB solidity bit,
signed-width extension tiles and a left-side pre-mirror offset; gliding uses
fixed ten-pixel probes. Keep the owning routine's geometry and copy reusable
sensor results before another scan. Shape setters can also move native centres,
and writing the ROM animation word changes both current and previous animation.
The [KiS2 wall investigation](research/trace/2026-09-14-kis2-chain-frontier.md#wall-contact-continuation-2026-09-15-base-b8d0ae91b)
records independent geometry, position-word and animation-restart regressions.

**Glide collision order is a native temporary-state contract.** `Knux_DoLevelCollision_CheckRet`
(skdisasm044:32665–32854) selects the movement quadrant once from the original
velocities. Walls may correct the centre and zero X before the subsequent ceiling
or floor probes. Preserve those updated integer anchors and the fractional words;
zero distance is clear. S3K resolves a simultaneous floor before Push, whereas
KiS2 `Knuckles_GlideControl` (c336fede:38177–38188) checks Push before restoring
radii/InAir. Keep that response policy independent of the radius policy. Reverse
ceiling wrappers can replace d5/Collision_addr with TOP, which later reverse
floor/opposite-wall calls inherit; reloading LRB changes the ROM query. Wall-grab
facing comes from signed-byte(property+$40), not X after collision zeroed it.
The2026-10-22 MHZ correction's component tests cover these distinctions; changed
cold input history remains a separate route obligation, not evidence to fit physics.

**Slide collision flags are not glide responses.** Native `Knuckles_Sliding`
(S3K:31032–31049; KiS2:38449–38469) calls collision-only correction and then a
second independent floor scan at corrected coordinates. Keep KiS2's 10/10 radii
through both scans and the writes, and carry the effective scanner bit/path out
of the first call. Do not dispatch glide landing/wall-grab responses from the
slide's temporary flags. Signed BPL makes +32→0 continue while −32→0 gets up.
Get-up uses byte subtraction then sign extension for the radius correction and
leaves double_jump_property intact. A late restore after ordinary setAir(false)
is insufficient: landing callbacks already observed zero. Use the narrow native
floor-reset entry before publication; ordinary landing keeps its original reset.
The climb floor tail must also skip its own earlier explicit clear. Do not restore
the saved byte afterward: that overwrites changes deliberately made by callbacks.
Keep ledge completion on its distinct ordinary landing path.
Declared component and registered restore/forward controls cover these branches;
live route qualification remains separate.

**Native glide prelude can be skipped by a correct local handler.** All five
Knuckles glide states run the speed owner's unsigned WORD camera tail before
LevelBound and no-gravity movement; flags3–5 skip active speed controls, not that
tail. Move once before their handlers, including climb/ledge early returns, and
remove any prior slide-local move. State2 also calls ChgJumpDir's second tail.
Native bias95/97 oscillates, and high-bit words compare unsigned; shared signed,
clamped easing is not equivalent. Preserve the existing actor ownership gate.
When adding this prelude to component controls, declare coherent camera bounds:
a Y512 inlet with default maxY0 dies before any climb probe. Keep original terrain
expectations and death-plane implementation intact instead of disabling the gate.

**A native sound ID needs its actual loader namespace.** S3K GroundSlide7E
resolves pointer-list entry4B after native ID_BASE33; assert the actual ROM target
and PSG3 stream, not merely7E at an observer. Queue it only after successful
second-floor Y/angle writes on the actual Level_frame_counter low3-bit cadence.
KiS2 removes this sound block; an optional installed-profile cue preserves absence.
The component request and ROM-target proof do not establish native waveforms.

**Climb offsets precede the radius.** S3K `Knuckles_Wall_Climb` and KiS2
`Knuckles_Climbing_Wall` pass `Y-8` / `Y+9` to the vertical helpers;
`CheckCeilingDist_WithRadius` / `sub_F828` then subtract/add the sign-extended
**X** radius. Stock 10/10 probes therefore reach `Y-18` / `Y+19`, including
the mirrored reverse branches. Generic object probes and radius-less player
probes both miss this geometry. Floor contact lands only at a negative distance,
copies raw `Primary_Angle`, and writes current animation 5 after floor cleanup;
it retains `double_jump_property`. Preserve that byte across `setAir(false)`'s
extra landing effects, and keep the floor tail separate from ledge completion.
S3K idle uses the same downward geometry and retains the shipped `FixBugs=0`
distance-to-animation clobber; KiS2 omits the idle probe. See the
[source-derived component controls](research/trace/2026-09-14-kis2-chain-frontier.md#wall-climb-vertical-contract--2026-10-21).

**A player sensor is not a terrain oracle.** `Sensor.doScan` returns `null` when the
sensor is inactive, and `AbstractPlayableSprite.updateSensors` deactivates the pair the
current movement quadrant does not use — ceiling sensors whenever the player is grounded
or moving mostly downward, ground sensors while moving mostly upward. A sweep that probes
`getCeilingSensors()` without activating them therefore reports zero hits at every sample
point, in every zone, which reads exactly like missing collision data. Activate the sensor
(or drive the sprite into the quadrant that uses it) before believing a negative result,
and prefer a control in a zone where the same probe is known to hit. Measured 2026-09-17:
a 4161-point Death Egg act 2 sweep reported 0 upward hits and was recorded as an engine
defect; forcing `setActive(true)` at one of those points returned the ceiling at distance 7.

**The two vertical terrain helpers disagree by one pixel.** For the same column,
`ObjectTerrainUtils.checkCeilingDist` reports zero distance one row higher than the ceiling
sensor does — `checkCeilingDist` says the Death Egg act 2 corridor ceiling ends at y=$051F,
the sensor's first clear row is $0520, and an upright head-bonk comes to rest against $0520.
Shipped player collision runs through the sensor (`Sonic_CheckCeiling`'s `eori.w #$F,d2`
form of `FindFloor`, sonic3k.asm:20278-20292), so derive expected player positions from the
sensor or from a measured upright control, never from the convenience helper.

**Clearing the air bit is not a landing.** Direct `bclr #Status_InAir,status`
paths such as DEZ hang-carrier capture (`sub_4703E`, `loc_47104`) do not call
`Sonic_ResetOnFloor`. `setAir(false)` synthesizes landing effects, clearing jump
and double-jump state, changing radii and resetting score chains. Use the existing
`clearAirForNativeControlRestore()` for a bare status clear. Likewise, when the
ROM writes jump radii explicitly and then sets the roll bit, use
`setRollingFlagPreserveRadii` rather than the generic rolling box transition.
The DEZ carrier regression preserves airborne jump fields and custom radii on
capture, and native centre/fractions on release.

**Hurt owns its terrain landing despite object control.** Normal held movement
may skip `resetOnFloor`, but the hurt dispatch runs terrain outside that gate.
Native `Player_TouchFloor`/`Knux_TouchFloor` clears InAir before HurtStop tests it
and releases `object_control` (`sonic3k.asm:24405-24443,32934-32944,33007-33022`).
Test through public movement and a real terrain floor callback: calling
`setAir(false)` directly can conceal an early return that leaves a hurt player
airborne with zero velocities and stale control.

**Custom retirement tails must retain the viewport term.** New objects that override
`isCustomOutOfRange` bypass the manager's widened placement-window policy.
A literal native `$280` then lets an 800-pixel viewport load an object early,
delete it immediately, and leave it dormant when the player later reaches it.
Use `coarseXCullRange()` for `Sprite_OnScreen_Test`/`Test2` and preserve any
additional ROM anchor/range offset separately (DEZ turbine: `$400` plus that
range). Native 320-pixel behavior remains exact. Positioned contact checks can
miss this: the 2026-09-22 DEZ curved-bridge approach passed at 320 and failed at
800 even though both positioned width checks passed. Test an approach from
outside the native spawn window as well as an already-loaded interaction.

**A masked coarse camera can reverse a signed viewport movement.** A negative
visible origin such as -3 becomes `$FF80` under the ROM's 16-bit chunk mask;
returning to zero then looks like backward scrolling. The new first-chunk
borrow guard can strand both placement cursors at the layout end with no
active objects. Treat disagreement between raw and masked movement directions
as a coordinate discontinuity and use the existing window reconstruction.
Do not remove native `Load_Sprites`' `$80` borrow branch or invent a native-camera
projection for the viewport. The 2026-10-17 regression checks ordered entry
ownership, strict 640/928 right edges and two restore/replays at 320/800, then
complete LRZ and MHZ wide routes. Negative-origin window semantics themselves
remain unchanged.

**Object clocks.** `ObjectInstance.update(int vIntRunCount, ...)` receives the
object-visible ROM `V_int_run_count`, stored by `ObjectManager` as `vblaCounter`. It is not
the manager's executed-frame counter or the ROM `Level_frame_counter`; lag frames can
de-phase those clocks. When porting a frame gate, name and read the clock the disassembly
actually uses instead of treating the update parameter as a generic frame number.

**Terminology** differs from standard Sonic 2 naming: **Pattern** = 8x8 tile, **Chunk** =
16x16 (composed of Patterns), **Block** = 128x128 (composed of Chunks).

**Sprite tiles are column-major:** `tileIndex = column * heightTiles + row`. H-flip draws
from the last column first, V-flip from the bottom row first.

**Pattern IDs exceed the VDP's 11 bits.** The engine adds a virtual pattern ID space above
`0x7FF` with a non-overlapping base per category; use
`GraphicsManager.renderPatternWithId()` when IDs exceed the VDP range, and pick a fresh
base for a new category. Range table in
[docs/status/known-discrepancies.md](../status/known-discrepancies.md).

**ENEMY touch responses poll every frame** while the overlap persists (matching the ROM
`Touch_Loop`) — SPECIAL/monitor contacts stay edge-triggered. Don't add consumed-once
"already hit" latches to the enemy touch path.

**S1 silently ignores solid-bit setters.** `setTopSolidBit()` / `setLrbSolidBit()` no-op
under `CollisionModel.UNIFIED`, so springs and plane switchers are automatic no-ops for S1.

**Non-solid ownership must survive inline cleanup.** Setting `Status_OnObj`
without `SolidObject` needs an ownership signal recognized by inline cleanup.
For controllers without an existing live latch, use
`ObjectManager.markObjectSupportThisFrame` on acquisition and held passes.
Do not report support after native release or during a recapture cooldown.
SOZ quicksand (`sub_3FD4E` and its variant owners; initial implementation
`86d800e53`) passed direct object tests while the ordinary route lost the flag at
frame end. Use the existing support contract rather than inventing a solid ride
or setting object-control bits the ROM never wrote. Test the production frame
boundary and restore/forward replay, not only calls to `update`.

**Rewind coverage is guarded.** A new spawnable object without a recreate path, an
uncaptured `final` scalar, or an object reference not captured as a rewind id fails
`TestRewindCoverageGuard`. A global static manager consumed across frames but unregistered
fails `TestStaticStateRewindCoverageGuard` — fix it with a `RewindSnapshottable` adapter,
not a baseline entry, unless the gap is genuinely intentional.
New concrete object classes also change `TestRemainingRewindTailInventory`, an
ordinary-suite check outside `-Pguards`. Run its real round-trip sweep and update
the test/resource totals only after verifying the new class passes or has honest
graph coverage. A passing coverage guard alone does not check those totals.

**A completed gameplay tick may precede completed publication.** The outer PLC
frame publishes dynamic-art diagnostics in `finish()`, after the gameplay body
returns. Live floor/checkpoint capture before that boundary differed from replay
by one diagnostic row even when world coordinates agreed. Retain input/pacing
before host input updates, capture after successful publication, and revalidate
installation/controller/level/provider identity before mutation. Failed ticks and
stale completions must not record. Pre-tick assembly remains a separate boundary.

**Binding an owner does not admit a closed object world.** The Tails DEZ route
exposed eager floor construction while the source level was inactive for
transition, before existing entry gates could reject it. Both matched baselines
passed. Reject that opportunistic object-world capture; preserve explicit entry,
special-provider bootstrap and allowed cosmetic-fade recording. A null controller
can represent the retired-source window until the destination's real assembly
and native fade release complete. Require its fresh history at readiness and
prove old handles cannot replay; do not patch an inadmissible snapshot's boss
references. See the [paydown evidence](plans/2026-10-22-architecture-debt-paydown.md).

**A “this frame” contact flag may cross snapshots.** In split object/solid phases,
callbacks can run after the object's update. A manual codec must capture those
pending flags, even if update clears them each pass. DEZ gravity pads cleared
`pressedThisFrame`/`occupiedThisFrame` on restore: this dropped a queued first
press or let an occupied zero-counter pad rearm, sink back and toggle again.
Capture before consumption and replay both the armed and occupied-rearm cases;
a mid-count snapshot does not cover them. The cold Tails DEZ2 pad at input46581
exposed an 8px change on the first replayed frame where ordinary play stayed put.

**Slot-encoded identity sets must not hold dead objects.** Touch-overlap sets and
instance-keyed solid latches are captured as slot numbers because Java identity
does not survive a restore. A member that has since been deleted keeps its old
slot, so restore either drops it or binds whichever object now occupies that
slot (SOZ Rockn shells, LRZ flames). Dynamic deletion therefore evicts
instance-keyed latches as `removeActiveObject` already did for layout objects
(`Delete_Current_Sprite` zeroes the slot's status bits), deletion also drops the
object from the touch-overlap buffers, touch capture records only live members
as the restore-view guard, and restore resolves a captured slot before its spawn: a
child that shares its parent's spawn otherwise rebinds to the parent. Physical
double-buffer parity is not state; capture the logical contents. These gaps were
latent until `da27f4f3d` made the restore comparator inspect these channels.

**Subsystem SST reservations must follow object restore.** Attracted rings own
slots outside the object manager's captured occupant set. Re-register the ring
adapter after the object adapter when level adapters move, including seamless
act transitions. Restore clears future ring records without releasing their old
numeric slots: those slots may now belong to restored objects. Then reserve the
saved ring slots. Restoring rings first loses their reservations when objects
rebuild occupancy; releasing future ring slots afterwards frees restored objects.
The cold solo DEZ2 route exposed both at input28910. Test allocation after replay,
not only snapshot fields: the object snapshot deliberately excludes ring slots.

**Managed children need identity relinking.** `RewindStateful` represents captured
helper values; applying it to a live managed child can make an owner's captured
collection retain stale objects after recreation. Use object scalar capture and
identity relinking for those children. Exercise remove/recreate/restore with the
real manager, including any optional defeat controller that advances child cleanup.
Test creation and retirement boundaries as well as a steady fight. LRZ's old
restore hook rebuilt destroyed arms (12 survivors became 24); restore the captured
graph instead. Chainspike's DEZ2 route also exposed a retained child linked to a
stale body: transient/final parent references and nearest-live-body recreation
are not identity restoration. Capture the exact parent reference through the
object-id fixup pass; proximity is not evidence of ownership. Full-registry
forward replay caught an extra child where a component snapshot passed. Also
publish the parent's ROM retirement flag before removing its identity:
`Sprite_CheckDeleteTouch -> loc_85094` sets status bit7 for `Child_CheckParent`.
An exact-id sidecar may encode an already-retired parent as null so a child
awaiting its next update remains an orphan; do not silently accept an unregistered
live owner.
DEZ's tunnel controller has the opposite lifetime: its ring-trail channel starts
10 updates before the player channels and can delete its spawner while they still
run. `DEZTunnelControl_Done` never dereferences that completed channel, so release
the Java link when the channel reaches zero, before the later spawner slot deletes
it. Do not suppress identity validation or keep the spawner alive for snapshots.
Exercise capture in that retirement window, not only during the moving trail.
Boss-child spawn metadata is a derived position/ordinal cache:
refresh it before capture, including the first frame after subclass construction.
LRZ's hand overrides `syncPositionWithParent()` with a no-op, so the base constructor
initially builds a zero-position record. Its constructor then copies the native
`CreateChild8_TreeListRepeated` coordinates; publish the cache there too. Waiting
for the first volley made an immediate restore change the record during the hand's
stagger delay, even though its live coordinates and ordinary replay looked right.
`TestS3kLrzBossRewindHeadless` covers arms, hit flashes and defeat debris through
explicit removal/recreation and whole-world forward replay.

The next boss-child ordinal is construction history, not a cache derivable from
survivors. Retiring the highest ordinal, or every child of one exact runtime class,
must not make a future spawn reuse that identity after rewind. Capture the complete
per-class next-ordinal map; reconstruction may consume provisional ordinals before
phase-2 field restore overwrites them. Do not clear it in the settled hook and
rebuild `max(live ordinal)+1`. Keep immutable `Class` keys themselves in the typed
in-memory codec: two class loaders can define different classes with the same name.
Exercise retired-highest and all-retired checkpoints with a future spawn after
repeated restore, alongside the live-highest control. Origin: LRZ fight full-registry
replay, 2026-10-21; this identity bookkeeping is shared across games and is not a ROM
slot number or a native timing observation.

DDZ children instead retain their creation spawn: do not call a normal parent-derived
constructor with a null parent during recreation and silently replace that spawn
with `(0,0)`. Pass `ctx.spawn()` through construction; relink the captured parent
later. Compare all registered snapshot keys, not only live coordinates: the incoming
DEZ→DDZ route exposed this metadata loss despite the earlier summary replay passing.

**Capture again after a creator disappears.** Replaying across deletion from an
older snapshot does not prove that the surviving graph can itself be captured.
DEZ1's orb fragments and finite explosion bursts retained unused Java parent
references after the source SST was deleted. `loc_7E916`/`loc_7E972` fragments and
`CreateBossExp00`/`CreateBossExp06` bursts use copied coordinates, not
`Obj_WaitForParent`; omit their live parent dependency. Preserve actual follower
references. Test both pre-deletion replay and a fresh post-deletion capture/replay.
MHZ's Mushmeanie shell has the same shape: `loc_8DC42` reads the parent once at launch
and `loc_8DC62` never again, so the body's second hit can delete it mid-flight. Drop the
link at launch and recreate from the captured spawn with the parent relinked by id, not
by proximity to a live body (800px MHZ1 route, 2026-10-15).

**Exact-SST act carries keep the fixed power-up slots.** `Shield` and
`Invincibility_stars` are fixed `Level_object_RAM` slots (`sonic3k.constants.asm:325-328`)
that a `Load_Level` act reload never clears. `PERSISTENT_EXACT_SST` carries only
persistent dynamic occupants, yet the manager rebuild skipped power-up registration for
every exact-SST policy as if the carry had imported them (only `ALL_LIVE_SST` does). A
fire shield gained in MHZ1 left the act-2 manager while the player kept the status; a later
registry restore re-spawned it, so the route's restore check failed although restore was
right. Register owners the carry did not retain, and compare the live manager with the
player's power-up state across the transition, not just capture against restore.

**Identity-keyed player collections need an explicit policy.** A final
`Map<AbstractPlayableSprite, ...>` or player `Set` is not captured by the default object
scalar path, and the coverage guard does not flag it. The MHZ mushroom cap's per-player
last airborne `y_vel` (`$30/$32(a0)`) and standing set were silently dropped, so a rider
restored on the cap bounced at -$680 instead of -$780; the curled vine's `$36/$37` segment
map (a restored rider took the landing frame's -1 snap), the catapult's riders (launched
by the next update) and the dropping platform's riders had the same gap. Add `CAPTURED`
entries (as for the swing bars), drop entries for sprites that left the player slots
(the MHZ1 carry-in Tails), and replay through the consuming update from a capture taken
on every frame of the ride; a capture far from that update passes. A dense route sweep
(capture every ten frames, replay thirty, compare rows and whole snapshots) found these
after targeted spot checks had passed.

**ROM sprite priority buckets are SAT order.** Lower priority buckets and earlier object
slots appear in front; painter rendering reverses both orders. Folded boss parts
can have independent buckets even when attached to the same rocket. Compare native
pixels against ROM table-driven composition, rather than testing a numeric
"front" flag against the same assumption used by the implementation.
Do not split a unified bucket into low/high terrain-priority groups: that
reorders sprites within the same SAT bucket. Collect mask entries in native
ascending slot order, apply masks to later entries, then reverse the resulting
pieces for painter replay (CPU, direct and instanced paths alike). Players occupy
the first slots and precede objects during collection; painter order is the
reverse. Merely creating a mask object is insufficient: the zone must enable
the SAT collection/post-pass. The SOZ priority audit on 2026-09-16 exposed both
failures with a door drawn through the sand and a same-bucket mask hiding the
wrong object.
**Follower priority inheritance must be field-specific.** Hyper stars illustrate
this: `Obj_HyperSonic_Stars_Init` fixes the display-list word at `$80`, while
`loc_19458` inherits only Sonic's art-word high bit. Copying both properties
moves the orbiters behind the player. Check each field's writes independently;
a follower's relationship to the player does not imply identical priorities.

**Do not manufacture foreground priority to fit a sprite overlap.** Slot's ROM
loads descriptor words and its row/column/VInt owners preserve priority. Promoting
every nonzero tile in a central square hid opaque walls/peppermints behind native-
low Plane A. The old glass test asserted the same promotion assumption. Compare
complete ROM/live words, then exercise actual TilePriorityFBO and non-SAT sprite
consumers with an opaque nonzero-index pixel. Controlled component palettes can
discriminate opacity without becoming native-picture evidence. R8's independent
red and one fixed original-pixel capture closed the two large components; HUD
carried-life and remaining RGB differences remain separate. See the
[bounded result](validation/levels/s3k-bonus-stage-transitions.md#r8-fixed-five-originals-native-priority-components-close--2026-10-17).

**Hardware tile priority needs its own evidence.** The SSZ2 Mecha body retained
the correct `$280` display-list bucket but inherited `isHighPriority() == false`,
despite `ObjSlot_MechaSonic` setting `art_tile` bit15. The floating island's
correct high-priority Plane B mask therefore hid the airborne body; glow and
projectile children already had the correct bit. A palette-preserving draw call
does not preserve this object attribute. Check the ROM art word independently
of the bucket, assert the object's tile-occlusion mask through phase transitions
and rewind, and inspect an actual sprite/high-plane overlap. Route completion
and screenshots without overlap cannot establish priority correctness.

LRZ3's missing pool (2026-09-25) had the complementary failure: high-priority
Plane B floor tiles existed in the ROM but were hidden by low-priority Plane A
lava-wall tiles. Verify the source window, retained-plane lifetime, and the
high-background replay plus sprite mask independently. Rebuilding correct BG
pixels alone cannot repair a background-first draw order. Preserve column
VScroll in the replay, since the pool slope is part of its sampling contract.

**Keep native GL state with the pass owner.** Background descriptor alpha is
metadata, so its tile pass disables blending and restores the incoming state.
Do that inside the initialized `BackgroundRenderer` begin/end boundary. A raw
`glIsEnabled` in the caller can abort an isolated JVM whose command recorder
deliberately overrides the GPU operations; an earlier native test can conceal
the dependency. Verify the recorder independently and real pixels with both
incoming blend states (GitHub issue sweep, 2026-10-13).

**The bucket encoding differs per game and the engine defaults are silent.** S1/S2
store the bucket as a byte (`move.b #4,priority(a0)` is bucket 4); S3K stores the
display-list byte offset as a word (`move.w #$280,priority(a0)` is bucket 5, `$80` is
bucket 1, not a flag), and ObjDat/ObjDat3 tables carry the same word third. Transcribe
through `RenderPriority.bucket(n)` / `RenderPriority.fromS3kWord(word)`; `clamp` folds
a raw S3K word into bucket 7 without complaint (four CNZ/LRZ objects shipped that way).
Boss-child constructor arguments need the same conversion: LRZ drill debris
passed `$80` into `AbstractBossChild` and silently drew in bucket7 rather than1
until the 2026-09-24 cold-route cleanup. The same bug occurred on the retiring
arm path assigning `$80` into the inherited bucket field. Check phase writes as
well as constructors. Test the normalized result, not merely
the presence of an override.
`getPriorityBucket()` defaults to bucket 0, the front-most, so every class that draws
must override it; `TestObjectPriorityBucketGuard` enforces this and a ROM priority of 0
opts in by returning `bucket(0)` with the citation. `isHighPriority()` is the art
word's bit 15, a different property. See the
[sprite priority bucket audit](audits/2026-09-16-sprite-priority-bucket-audit.md).
**S3K HCZ boss mappings are ROM addresses, never disassembly files.** `Sonic3kObjectArtProvider`
reads `Map_HCZMiniboss`, `Map_HCZEndBoss` and `Map_HCZWaterWall` through `S3kSpriteDataLoader`
at `Sonic3kConstants.MAP_HCZ_MINIBOSS_ADDR` (`0x3629E0`, the offset-table base, not the first
frame body), `MAP_HCZ_END_BOSS_ADDR` (`0x3634D4`) and `MAP_HCZ_WATERWALL_ADDR` (`0x22EE10`);
an earlier build parsed the `.asm` files under `docs/` at runtime and rendered nothing when
the submodule was absent. `TestArchitecturalSourceGuard` pins the three constants and this note.

**Inline-drawn ROM children need their own buckets.** When an owner draws its ROM
child slots from its own render call (boss orbs, panels, box pieces), implement
`MultiBucketRenderable`: report the parts' buckets through `extraRenderBuckets()`, their
art-word bit through `isHighPriority(int)`, and draw only the requested bucket in
`appendRenderCommands(commands, bucket)`. The manager lists the owner in every bucket
and re-reads part buckets each frame, so runtime `priority` rewrites work. Parts sort
by `partSlotIndex(bucket, class)`, the owner's slot unless the owner models where the
ROM allocated the child. Splitting into child instances is still the better model when
the parts need their own slots for allocation or touch order.
Resolve relative mapping attributes with the complete native 16-bit art-word
addition before separating fields. SOZ pillar spikes use `$C49B/$D49B + $4001`,
which clears priority as palette bits wrap; independent palette addition leaves
the spikes falsely in front. A BG-high color replay also needs an equivalent
sprite-priority-mask contribution, or correctly low sprites still cover it.
Assert both submerged pixels and exposed art so a missing sheet cannot make a
mask test pass. Registry builder names use `Sonic3kObjectArtProvider.invokeBuilder`
(the explicit switch, not reflection); wire that case as well as the registry.
**ROM palette-line names are one-based.** `sonic3k.constants.asm:787-790` declares
`Normal_palette ds.b $80` and then `Normal_palette_line_2 = Normal_palette+$20`,
`_line_3 = +$40`, `_line_4 = +$60` — so `line_2` is the **second** line, engine palette
index **1**, and `line_1` is the base label that never appears in a write. Reading the digit
as a zero-based index shifts every write one line, and the symptom is not "nothing happens":
it is the wrong sprites changing colour, which looks like a mapping or art bug. `Target_palette`
is named the same way. Four S3K classes already say this in comments (`AizEndBossInstance`,
`LbzEndBossInstance`, `LbzFinalBoss1Instance`, `TunnelbotBadnikInstance`); the Lava Reef
miniboss's hit flash had it wrong for a round because its author read the name rather than
the constants file.

The separate `art_tile` high bit controls sprite-versus-tile priority. Native
child creation copies that bit; `SetUp_ObjAttributes3` can change the SAT bucket
without clearing it. FBZ2's laser-room children need both properties preserved.
Reversed tile planes also retain the VDP order B-low, A-low, B-high, A-high,
and both planes' opaque high pixels contribute to the sprite-occlusion mask.
See the [FBZ2 graphics audit](audits/2026-09-14-fbz2-laser-room-graphics.md).

**Route rewind probes need the whole owner state.** A recreated object can match
its own snapshot while reading a future static counter or a reset helper timer.
The AIZ1 route continuation found both: `Events_fg_4` needed a registered adapter,
and `AizIntroPaletteCycler` needed `RewindStateful` timer/frame capture. Construct
stateful helpers before schema restore, then bind services when used: object
recreation can run before service injection. Compare resource pixels by content;
Java identity of re-decoded `Pattern` instances is not a rendering difference.
When a helper gains `RewindStateful`, remove obsolete owner-field `DEFERRED`
overrides and test while it is still pending. LRZ's boss camera gate otherwise
restored empty and completed immediately, making an entry-wait replay submit
boss art early; later fight-only rewind checks never exercised that gate.

**Motion holders need stable identity in the default rewind path.** Declare
`SubpixelMotion.State` holders `final`; the default schema captures supported
in-place helpers only when their identity is fixed. LRZ's new boulder initially
used a replaceable holder, so snapshots matched immediately after recreation but
the next update moved riders from the constructor position. Comparing complete
world state after one forward frame caught it. Arrays use their own capture policy;
do not apply this rule to arrays indiscriminately. Origin: 2026-09-22 LRZ bring-up.

**Recreated arrays must accept the captured shape.** A final array is restored by
copying into its existing allocation. An empty placeholder constructor therefore
cannot restore a populated array, even when the no-argument inventory probe
passes. MHZ's weather-machine palette fade exposed this with four target lines
versus an empty recreated array. Keep variable-shaped snapshot arrays replaceable,
or recreate the exact required shape; exercise populated live state and forward
replay through both animation phases. Origin: 2026-09-26 MHZ Tails route.

**Captured collections can lose shared value identity.** If two maps index the
same mutable holder, independently restored map values may match immediately but
then evolve separately. MHZ's horizontal bar stores active and cooldown-history
indexes of one per-player hang state. Restore recreated two holders; release wrote
30/8 to one while the re-grab path read zero from the other. Rejoin the indexes in
`afterGenericRewindStateRestored`, and compare forward state through release, not
just immediate snapshot equality or the player's control flag. The ROM's single
`a2` record in `sub_3ED6E` owns both flags and cooldown. Origin: 2026-09-26 paired
MHZ cold route; this is a local ownership repair, not a generic alias guarantee.

**Released solid contacts need captured provenance.** A CPU follower can retain
its last contact after that owner is destroyed and its slot reused. Rewind must
clear the future Java pointer, preserve the captured released-contact state and
relink a live contact only through its recorded slot. A nearby same-type object
is not the old owner. HCZ1 fan/conveyor/spring replay exposed this through the
CPU's `releasedUnderwaterPushConsumed` flag; immediate restore alone passed,
but the next 30 frames differed. Exercise two restore/forward-replay cycles.

**Pending object initialization needs its construction policy.** A recreated
explosion may not have run its first update yet. Capturing only its scalar timers
loses configured animal/points factories, changing child allocation order and RNG
on forward replay. Preserve the exact factory configuration with the initialization
latch; obtain live services from the restored owner. HCZ1 bridge-trigger windows
exposed this boundary while nearby approach checkpoints passed. Select the live
event, and compare forward replay, not only immediate restoration.

**Claimed hardware work is memoized.** `HardwareTimingJob` shares one immutable rewind
snapshot per claimed job across checkpoints and drops it in every mutator. Unclaimed jobs
are re-snapshotted each capture because `coordinatorPreparation` still hands out their live
preparation, which a caller may step or restore. Do not widen the memo to unclaimed jobs
without first removing that alias.

**Headless tests:** call `GroundSensor.setLevelManager(...)` and
`Camera.updatePosition(true)` *after* the level load, and prefer
`@ExtendWith(SingletonResetExtension.class)` over manual teardown. Set
`startup.legalDisclaimer=false` in tests that boot the full `Engine`.

**Underwater route liveness.** `getDead() == false` does not exclude the drowning
pre-death phase. `applyDrownDeath()` locks controls and zeroes velocity before the
delayed dead flag; no-death route assertions must also reject `isDrowningDeath()`.
The HCZ route continuation initially mistook the resulting latched logical input
on a conveyor for an input-publication defect. Inspect the first capture and air
state before that deadline, not only the later stalled state.

**`FixBugs` / `fixBugs` assembly paths.** All three disassemblies are built with the
bug-fix conditional OFF — `FixBugs = 0` (`s1disasm/sonic.asm:20`,
`skdisasm/sonic3k.asm:38`, `skdisasm/s3.asm:25`) and `fixBugs = 0`
(`s2disasm/s2.asm:27`) — because that is what the shipped ROMs do, and the traces
record shipped-ROM behaviour. **Always model the `FixBugs = 0` path**, including
when it is plainly a bug: the un-fixed path is the accurate one, and taking the
fixed branch will desync a trace that compares the affected field. There are ~327
such blocks in s1disasm, ~262 in s2disasm and ~111 in skdisasm, so you will meet
them often.

When you port code near one of these conditionals, **say so in a comment** — name
the flag, state which branch the engine takes and why, and describe what the fixed
branch would do. That costs a line now and is the only thing that will make a
future "support the bug-fixed revisions" effort tractable, since the sites are
otherwise invisible once ported. `Camera.java:122-124` and
`Sonic1BatbrainBadnikInstance.java:394` are existing examples of the shape.

**Startup scenes bypass title and level audio setup.** Install the active ROM and
module audio profile before calling a startup scene's `enter`; an initialized
speaker backend alone cannot resolve native music/SFX IDs. A scene test that first
loads `SharedLevel` masks this omission. `TestModSceneLauncherAudio` opens through
the production launcher without loading a title or level and checks final PCM for
music and an isolated effect. Origin: Eggman's Sky bug reports, 2026-10-08.

**PCM exhaustion need not release a native caller.** S1 PlaySegaSound starts
Z80 sample88, then blocks the 68000 in a separate nested NOP/DBF loop inside
VBlank. The sample's byte clock and the caller's CPU-work completion are distinct;
a transport-active getter cannot substitute for the latter. Instruction-only
cycles also omit bus/refresh/peripheral costs and cannot be rounded into an
absolute startup delay. Preserve each owner and require a service boundary before
starting the post-chant timer. See the [S1 startup causal evidence](validation/audio/2026-10-22-s1-s2-native-audio-evidence.md#s1-startup-causal-slice-instruction-work-is-not-presentation-time).

**Audio accuracy:** the FM core is the Nuked-OPN2 port (`audio.synth.nuked`); its only
reference is the pinned `ym3438.c`, and `Ym2612Chip` is engine glue over it. For the PSG
reference the libvgm cores, for the sequencer the SMPSPlay source, rather than simplified
versions. Diagnose against a source of truth instead of twiddling knobs.

**Implicit SMPS durations reuse scaled track RAM.** S1 `SetDuration` multiplies
the explicit duration by `TempoDivider` before saving it; `FinishTrackUpdate`
and `DACUpdateTrack` copy `SavedDuration` without applying the current divider.
S2/S3K `zSetDuration`/`zFinishTrackUpdate` follow the same ownership. Rescaling a
remembered raw byte after a divider flag shifts later attacks even when early
audio matches: the S1 Credits survey found a PSG2 attack 768 services early.
Check native saved-duration semantics and the complete medley tail, rather than
shortening catalogue metadata to the engine's premature stop. This was found
during the 2026-10-07 Sitar Hero full-song catalogue work.

**A duration-only PSG command can remain a native rest.** S1 `PSGDoNext` and
S2 `zPSGDoNext` clear the rest flag before decoding a positive duration byte,
but their note-on routines test the signed saved frequency. A preceding rest
stores `$FFFF`, so `PSGDoNoteOn`/`zPSGDoNoteOn` restore rest rather than attack.
Preserve this frequency sentinel under the driver's RESET policy; S3K's KEEP
policy retains a playable frequency and has different continuation behavior.
An independent interpreter must model the same distinction: otherwise S1
Credits gains 96 false attacks and S2 Oil Ocean gains three. Compare the first
divergent tuple and native track RAM, not only attack totals. Origin: Sitar Hero
full-song work, `03e0c2ac0`, 2026-10-07.

**S3K track execution uses the loaded bank, not a header-to-header slice.**
`zGetNextNote` and `cfJumpToGosub` can call phrases before the current song
header. Sonic 3 Ending calls Title-bank FM phrases; a bounded song slice cut
its natural tail from 609 to 514 services. Keep header parsing and local voice
lookup anchored to the raw header while track reads, jumps and returns address
the loaded 32 KiB bank. Test earlier-header calls and local/shared voices
together. Origin: full-song work, `64c7b25b6`, 2026-10-07.

**Freeze the indexed SMPS program separately from its raw header.** A parsed
source can retain a short `getData()` header/voice slice while `dataLength`,
`dataByteAt` and `read16` expose a larger address space. Copying the raw slice
but retaining the indexed address base produces an immutable yet invalid
program. After S3K bank reads were restored, Blue Sphere, 1-up and Ending each
initialized nine tracks directly and zero through the old frozen consumer; AIZ
began at the bank start and concealed the defect. Snapshot indexed reads with
their decoding semantics as well as raw bytes and metadata. Program identity
must distinguish different indexed contents with identical headers and different
headers sharing one bank. Indexed bytes alone do not cover custom `read16`
semantics: equal bytes with little- versus big-endian word reads must not reuse
one frozen entry, nor may final-word value and rejection become interchangeable.
Exercise live, frozen and restored consumers together.
Origin: Sitar Hero full-song integration, 2026-10-07; see the
[dated S3K catalogue evidence](designs/2026-10-07-sitar-hero-s3k-song-catalogue.md).

**Speaker starvation is a device transition, not generated-sample equality.** A
stopped OpenAL queue can leave a sub-packet remainder in the software FIFO, so
the consumed cursor never reaches the producer's count. Detect stopped-after-play
at the sink, reset on flush and expose interruption to the scoped scene player.
`AL_SAMPLE_OFFSET` describes the source mixer; optional
`AL_SOFT_source_latency` and calibration address backend arrival. The
latency-subtracted cursor can remain behind a drained queue's final boundary
until PCM is refilled; wall-clock sleep alone does not establish consumption.
Whole-song AC variance and progression must span authentic rests: a short constant/DC packet
does not establish stalled backing. Origin: Sitar Hero native null-backend hitch
and Chemical Plant masked-mix probes, 2026-10-06.

**Audio gap accounting must stay bounded and allocation-free.** Boxed map updates
on FIFO drops broke the warmed presentation allocation budget, even without a
scene player. Retire primitive gap boundaries during device updates as well as
cursor reads; otherwise ordinary playback retains entries indefinitely. If the
ledger fills or output fails, report the failure and freeze scoped rhythm time.
A silent fallback's generated cursor does not establish audible progress.
Defer sink replacement requested inside `accept()` until the producer finishes
its frame's capture/history boundary. Origin: Sitar Hero matched baseline/current
allocation check and speaker-failure lifecycle, 2026-10-06.

**External audio clocks are not rewind frame numbers.** GameLoop's audio clock
continues through title/fade intervals while live rewind records gameplay only
and resets its origin on level loads. `recordExternalStep` must observe the
completed host clock, not overwrite it with the shorter rewind counter: a fade
completion can issue `InitAudio` commands before the next host tick and violate
timeline ordering. Keep the recorded coordinate mapping for seek/truncation,
prune it with history, and reroot it at load boundaries. Test internal/external
step changes, branching, and consecutive death reloads with live rewind enabled.
Origin: SOZ mixed/duplicate-team checkpoint validation, 2026-09-15.

**Programmatic capture settings are not CLI arguments.** `GameplayCaptureTool`
normalizes `--sidekick none` to an empty string; `GameplayCaptureSession.Settings`
expects that normalized character-code value. Passing the literal `"none"` to
Settings requests an unknown sidekick, which can fall back to a second Sonic.
Verify the actual registered team and rendered players before claiming solo
route evidence. Second Strike MHZ probes 44377/82072/19540 (2026-10-16) exposed
this caller error; their manager/terrain observations were retained with the
unintended-team limit, and their incomplete wind comparison was not certified.

**Terrain probes do not include live object solids.** Native floor data can be
correct while a monitor blocks the proposed approach. MHZ original monitor28
at `$0660,$0530`, with native solid width `$19`, matches the fixed probe's stop
at `$0647` before its `$0649` target. Inventory object solids alongside terrain;
do not remove the blocker or extend a controller search to manufacture a route.
This was a source/trajectory attribution, not a recorded collision callback.

**AniPLC submission is not presentation.** S3K `AnimateTiles_DoAniPLC` changes
counters and queues immutable ROM art; `Process_DMA_Queue` publishes it during a
later eligible VInt. Keep Level patterns, the level atlas and aliased object
atlases on the same publication boundary. Capture both pending work and actual
presented bytes for rewind, and register every destination before the first
submission so an early snapshot can restore original art. A graphics-only delay
or an empty pre-submission destination set loses observable state. The S3K
profile's internal publication port uses the existing exactly-once physical
token and source-proven loop phases. Follow callees: `VInt_12` fades DO drain DMA
through `Do_ControllerPal`; `VInt_0` lag does not. VInt14 is Sega-art loading, not
the level title-card loop, which arms VIntC. These distinctions were established
by FBZ native/GPU paired evidence on 2026-09-14.

**Finite layouts are not VDP rings.** Native camera bounds normally hide the
horizontal ends of a finite foreground; a centered wide camera can expose them.
Wrapping the full-layout GPU texture then borrows unrelated far-end terrain.
Clip the finite wide foreground in both visible and sprite-mask passes; preserve
explicit ring owners and background wrapping. Compare pixel bounds to the latched
`LevelScrollPresentation`, not the later live camera logged after the CPU step.
The LRZ seamless-handoff check caught this distinction at the last3pixels before
camera X=0 (September23S&K completion campaign).

**A screen-coordinate scene can outlive its world bounds.** S3K SSZ2
`loc_58B7C` clears camera X for credits while preserving the final arena's
native min/max. The wide bounds mask then used world459..779 and concealed
terminal sprites in the centered240..560 window. Gray pixels with thousands
of grain colors came from `shader_arena_mask.frag`, not a sixteen-color
resident Pattern0. Use the internal presentation-origin policy for that
screen window without rewriting gameplay bounds or changing unrelated plane
wrapping. The initial flight's masked wide wings remain intentional.
See the [ending ownership record](designs/2026-10-13-s3k-ending-lifecycle.md).

**Retained SAT requires retained scroll.** S3K `VInt` writes `V_scroll_value`
to VSRAM and `VInt_8_Cont` uploads `H_scroll_buffer` alongside the prepared
sprite table. Retaining screen-relative sprites while sampling terrain with the
next CPU camera makes stationary world objects slide by one camera delta.
Retain the matching horizontal/vertical scroll buffers, camera origin and plane
routing; use them for both visible terrain and its high-priority sprite mask.
Do not rebase old sprites onto the live camera to hide the mismatch. Stationary
FBZ boundary captures missed this regression: add a moving-camera check in a
second zone. Pixel oracles must sample the CPU scroll before the VBlank they
describe; reading the next loop after rendering compares different generations.
See the [MHZ follow-up](audits/2026-09-15-s3k-presentation-camera.md).
Plane drawing is the exception: `ScreenEvents` ends in `DrawTilesAsYouMove`
(`sonic3k.asm:105024`, `103171`), which reads live `Camera_X_pos_copy` against
`Camera_X_pos_rounded` and writes VRAM inside the CPU loop, and
`AIZ2_DoShipLoop` retargets that baseline in the same routine as its `$200`
camera subtraction. The engine's AIZ2 foreground ring therefore takes the live
camera and the live `Level_repeat_offset` from the gameplay step
(`LevelForegroundPlane.drawAsYouMove`), never the published scroll
generation: pairing the published camera with the live offset trips the ring's
large-jump reseed when the wrap lands on a lag frame (2026-09-16 AIZ2 forest
regression). The `$200` wrap equals the 64-tile plane width, so a retained
scroll register still aliases onto the same ring cells.

**Rewind DMA history is not current pattern memory.** LRZ Act2's first rewind
restored old miniboss pixels over PLC `$30` even though the seamless load had
installed the correct spike-ball/flame art. KosM's last-written payload was
historical: a later Nemesis PLC or replacement level owner could overwrite the
same addresses. Capture the live image of tracked ranges; retain the requested
logical image while a physical restore is deferred. A snapshot graph comparison
alone missed this because both graphs retained the same stale journal. Check
actual pattern pixels and rendered consumers after restore as well (2026-09-24).

**Private palette residency is not retained SAT state.** Chaos donor objects
keep native palette bits in published `Tile` records. Adding a private GPU bank
base in the producer would capture that row in `LevelSpritePresentation.Tables`;
restore could then sample another owner's colours after banks are released and
allocated in a different order. Retain an exact semantic owner/object/donor
resolver with the subject and acquire current residency at the draw boundary.
Validate an actual prepared/published table restore with changed bank order,
not only the gameplay runtime's snapshot (2026-10-14).
Private rows also belong in the expanded underwater texture. Changing the total
row count while leaving an older texture changes sampling even for ordinary
host rows; rebuilding only when the owned command runs is too late for a preceding
native batch. Rebuild at execution boundaries from the last published native
water/fade basis, preserving pending palette writes. Uploads can bind the active
atlas texture unit: invalidate deferred-command binding caches even when texture
IDs are unchanged. Real priority-water and acquisition-between-draws pixels
caught both defects; dry-only palette tests did not.
Bound the private pool without capping ordinary donor/sidekick allocation.
Ordinary contexts already support atlas growth beyond sixteen contexts; applying
the private pool's 64-row ceiling to them broke the sixteenth allocation that
previously grew to 68 rows. Search complete unoccupied private ranges, preserve
ordinary growth and check coexistence, high-row sampling, exhaustion and reuse.
Keep the renderer's captured inputs at its existing `PaletteView` boundary.
Storing concrete `level.Palette` rows in a new graphics helper introduced 27
frozen architecture violations despite passing pixel checks. Detached RGB bytes
preserve publication/value equality and pending writes without adding runtime
layer dependencies; verify the architecture rule as well as actual water pixels.

**SAT capacity belongs to mapping pieces, not expanded tiles.** HUD letters and
numeric art can have scattered cache addresses while sharing one native SAT
footprint; blank columns still consume its declared width. Transparent,
offscreen and already masked pieces also consume count/width. Preserve the
native footprint through admission, then clip immutable replay fragments.
Art-version tests must place the compared mapping inside the evaluated raster:
a missing offscreen tile version after admission does not prove lost resident
art. See [native sprite budgets](designs/2026-10-22-native-sprite-scanline-budgets.md).

**CPU sprite state is not the presented SAT.** S3K `VInt_8_Cont` uploads
`Sprite_table` to VRAM `$F800`; the resumed `LevelLoop` then runs objects and
later `Render_Sprites` builds the next table. An emulator-frame screenshot can
therefore show retained positions/mappings while the same sample's object RAM
has advanced. Compare native RAM and VRAM table ownership before changing
motion or animation. The engine's transient sprite-mask collector is not a
VBlank presentation buffer, and pooled GL commands cannot stand in for durable
rewindable presentation state (FBZ B2/B4 investigation, 2026-09-14).
Mutable virtual DPLC banks need an art-generation binding as well as a mapping:
retaining their numeric cache addresses alone can pair the previous mapping
with tiles overwritten by the next animation update. Preserve immutable
ROM-derived bank contents with the prepared presentation; stable art addresses
continue through the normal cache. Prepare even when host drawing is skipped,
and restore both prepared and displayed tables across rewind. HUD numeric
art is a separate publication: S3K `VInt8/10 -> Do_Updates -> UpdateHUD`
rewrites digits and advances the timer while the SAT still describes the
previous `Render_HUD` geometry. Buffering the digit choice with that geometry
adds a one-frame second-rollover error (21 pixels in FBZ B1 reverse). Lag,
fade and title-card handlers do not run the same numeric update.

Ending IslandLift evidence (2026-10-13, immutable jar `ed7dc650`, production
Java `70c09903`) reproduces this distinction: the displayed table placed cells
at X 516 while the next CPU RAM implied X 514. A manually created particle had no
published tiles until its natural even-VInt preparation and following publication.
Sample the producer's position, mapping and CRAM before that publication. In wide
views also check the prepared mask: `LevelBoundsMaskTransition` deliberately
crossfades edges in world space, and moving render copies left opacity 0.35973397
at X 514 inside bounds 240..560. Exact RGB tests select a transparent prepared
footprint; they do not fit a position/frame offset or weaken the color comparison.

**Screen-only SAT words wrap per piece.** `Render_Sprites` bit2-clear skips the
world camera clip; loc_1AF76 adds each mapping offset, masks X to 9 bits and replaces
X 0 with 1. Ordinary Mode5 Y is 9 bits too. IslandLift subtype `$41` can hold raw Y `$FF44`
after its camera threshold: native displayed center is 196. Preserve its raw word
and fraction; normalize only the derived one-piece draw origin, after the offset.
Passing 65348-style unsigned words straight to an integer renderer hides
native visible particles. Qualify wrapped-Y and X 0→1 as well as the normal case.

**S3K retained plane strip counts and clipping.** `Setup_TileRowDraw` and
`Setup_TileColumnDraw` subtract one before DBF: callers supplying `$20`/`$10`
write 32/16 blocks, not 33/17. `Draw_PlaneVertSingleTopDown`/`BottomUp`
first reject delayed rows outside the masked camera window through `+$F0`.
FBZ outdoor redraw supplies zero as that window origin; a delayed `$100` row
therefore consumes its redraw step without modifying retained cells. Preserve
the entire retained ring in a regression, including untouched rows and rewind.
A fixture event-flag write does not install its associated foreground layout:
match ordinary layout-copy and camera history before comparing GPU descriptors.
Count observed LFC advances rather than assuming each host frameadvance call
advanced gameplay (2026-09-14 FBZ boundary6 native/engine evidence).

FBZ horizontal redraw has a separate source-Y rule: `loc_5293C`/`loc_52962`
pass zero outdoors, not the bobbed VScroll. Indoors `Setup_TileColumnDraw`
resolves 16px blocks (`d1 >> 4`); passing raw Y77 into an 8px tile copier
incorrectly begins at row9 instead of row8. Keep this FBZ caller rule in its
owner, and account for the ordinary row-scroll pass separately in strip tests.

A background-mode change must also reproduce `Reset_TileOffsetPositionEff`
when its ROM caller uses it. FBZ's vertical and horizontal changes in both
acts reset the rounded Y after deformation and before the normal row tail;
retaining the old mode's origin queues spurious rows even when the staged
redraw counter is correct. `Draw_TileRow` tests the **low byte** of its word
scroll delta and conditionally negates the word before masking `$30`; using
the word sign or an unconditional absolute value differs for large deltas.
These producer rules are separate from `VInt_DrawLevel` publishing the buffered
writes at VBlank (2026-09-14 B1 ordered-redraw investigation).


S3K `ChangeRingFrame` owns independent bytes `$FEB2/$FEB3`, not a division of
`Level_frame_counter`. `loc_60DE` clears them before fresh setup objects because
they lie inside `Oscillating_table..AIZ_vine_angle`; the vine word itself is
excluded. Seamless core-only reloads skip that clear. Keep the bytes with the
existing S3K global animation owner and snapshot them with the combined animator.
The internal stage-ring frame provider lets render/bounds consumers read that
state without changing other games' animation rules. An explicit visual fixture
LFC reset exposes this ownership difference; copying native ring bytes or fitting
a renderer offset would conceal it (FBZ paired boundary investigation, 2026-09-14).

### Late object input and follower history

S3K `Sonic_RecordPos` records input in the player's dispatch before later SST objects run. A later object that writes `Ctrl_1_logical` (for example FBZ `loc_86358`) must not rewrite that tick's follower-history sample. Use the logical-input-only API at that boundary; a history rewrite makes delayed CPU input arrive one frame early even when final per-frame controller fields agree. The FBZ completion task (`d87e42bdd`) reproduces this at the actual end-boss exit owner and checks both the retained current sample and the following tick's record.

### Results queue observation and allocation retries

A full level frame may retire results art, run Obj_LevelResultsCreate, publish the act transition and submit new terrain art. Its post-frame global incomplete-job count cannot identify the original results batch. For a pre-Create allocation/rewind checkpoint, service the real hardware boundaries without dispatching objects, verify the exact result handles and empty physical FIFO, then dispatch the actual registered owner. Keep a separate full-loop transition check. Native Obj_LevelResultsCreate polls global Kos_modules_left on every retry, including after art has been claimed but the first child allocation failed; readiness of the three owned handles alone is insufficient. Origin: 2026-09-14 FBZ combined validation.


### Physical retained planes and source-cache dimensions

An event-owned VDP Plane B is64x32 cells even when its source-world cache is
taller. Native row writes into the first32 cache rows do not update the later
world rows; rendering with that taller height can sample untouched art while
a CPU ring comparison looks correct. Convert to the physical retained image
when the event takes ownership and preserve the same representation through
rewind. Read back the actual GPU texture as well as the CPU ring. Origin:
2026-09-14 FBZ completion; the192-row cache regression and ordered reverse
redraw capture isolate this from the separate VBlank publication issue.

Conversely, an initial `Refresh_PlaneFull` does not imply permanent event ownership.
LRZ2 follows it with `loc_5705C`'s `Draw_TileRow`; the source-window renderer must
release the seeded ring and continue sampling layout rows. Retaining the initial
64x32 image wrapped source Y=$100 to row zero, making a full-width cavern seam.
`TestS3kLrzScrollRegistrationHeadless` checks source rows across that boundary,
all five widths and rewind. LRZ3 already required the same release for its
`DrawBGAsYouMove`/`DrawTilesVDeform2` paths.


### HUD warning phase is not elapsed time

S3K `Render_HUD` / `loc_DB68` selects warning mappings using bit 3 of
`Level_frame_counter`; timer warning eligibility separately tests whether
`Timer_minute` is 9. `LevelTimer.totalFrames` can diverge after loading, paused
timers or explicit visual-fixture clock resets. Use the module's internal HUD
warning policy and the already rewind-owned level clock. Do not add a fitted
phase offset or a second counter. Test label rendering with deliberately
opposed timer and level phases, including a restored earlier level counter.
Origin: 2026-09-14 FBZ paired whole-frame investigation (307 RINGS pixels).


FBZ retained presentation (2026-09-15): a frame owns only the mutable DPLC
patterns referenced by its displayed tiles. Capturing an entire staging bank
also captures unused tails from older animations; an eight-frame S1 rewind
exposed five differing unused slots despite identical displayed art. Keep
referenced versions immutable and leave unrelated bank history out of the frame.
Filtering unused slots does not separate simultaneous owners: a retained frame
can store only one image per tile ID. Allocate independent banks for independent
players, reserve the largest selected form capacity, and keep allocation lifetime
aligned with renderer lifetime. Test staggered frames whose **used** slots overlap;
different animation cursors alone can still select disjoint slots and hide a
collision. See the [bank audit](audits/2026-09-15-sprite-publication-banks.md).
Results-screen renderer cache flags are likewise derived: rebuilding claimed
ROM art clears the cache, so such flags use `RewindTransient` while gameplay
readiness and timing stay captured.

### S3K full-resolution top-sloped surfaces

`SolidObjectTopSloped` uses `SolidObjCheckSloped` / `SolidObjSloped`, sampling one
height byte per horizontal pixel. The similarly named `...Sloped2` helper uses
two pixels per sample. Keep table resolution explicit for both new landings and
continued rides, including odd X positions and horizontal flips. SOZ spring vine
(`Obj_SOZSpringVine`, `$40786`) also reaches `loc_1E45A`, whose unsigned `blo` test
admits a 16-pixel overlap; test 15/16/17 rather than assuming an exclusive 16.
Its controller's `Delete_Sprite_If_Not_In_Range` tail does not draw: the separate
`Sprite_OnScreen_Test` child owns the eight visible pieces and its own X cull.

### Animated-art DMA units and cached camera phases

SOZ1 presentation investigation (2026-09-15): `Add_To_DMA_Queue` takes d3 in
**words**, while engine raw-pattern slices use bytes. `$60` words fills six tiles,
not three. Check the final destination tile as well as the first; a test which
only proves one tile changes misses half-length transfers. The channel graph's
owned destination range must grow with the actual copy. Also distinguish the
current gameplay camera from a previous presentation pass's cached parallax
copy when deriving animation phases. See the [SOZ execution record](plans/2026-09-15-soz-methodology-v2.md#normal-act-1-desert-presentation-implementation).


### Custom animation dispatch can leave an AniPLC pointer unused

SOZ1/2's `Offs_AniPLC` entries name LRZ1, but their custom handlers return
without executing it. Follow calls/branches to `AnimateTiles_DoAniPLC` before
registering a shared list; a data pointer alone does not establish execution.
AniPLC regression checks must include VBlank publication, since submission-only
checks see unchanged CPU tiles while the next presentation corrupts them.
The SOZ desert artifact correction in the methodology-v2 plan records the case.


### Scroll-buffer parity does not prove foreground deformation is rendered

SOZ1 computed correct FG/BG scroll words while only BG used per-line sampling.
The foreground tile pass also needs the owning zone's advanced render mode;
register it at that boundary instead of adding a zone check to the shared shader.
For a pixel-displacement oracle, compare opaque foreground regions: shifting a
composite through a transparent edge also shifts the independently scrolled BG
and gives a false failure. The SOZ methodology-v2 plan records this correction.

SSZ's launch exposed the same hazard for vertical columns: `shader_tilemap`
adds the per-column value to `WorldOffsetY`. Convert native absolute VSRAM
words to camera-relative deltas at the scroll producer. Supplying the native
word directly counts camera Y twice; the logic can still reach the next zone
while the foreground Death Egg artwork is entirely absent. Check the final
shader sum and a rendered frame, not just the handler's array.


DEZ final-arena column remapping exposed two further coordinate boundaries.
The BG FBO starts at aligned scroll Y, so a native `$E0` band boundary becomes
FBO `$C0` when that origin is `$20`. Remapped world X must also subtract the
moving cache origin before wrapping. Testing only scroll arrays or a static
camera misses both errors: the first bends the independently moving floor,
the second shifts the planet as the cache advances. Inspect a moving wide
capture with the native centre and the lower band both visible.

MHZ2's ship (2026-09-23) reproduced the missing-consumer failure: the controller,
propellers and HScroll array moved while Plane A used ordinary camera sampling.
The initial VSRAM word and HInt6's mid-screen reset are separate inputs. Preserve
the same split in low/high tile passes and the sprite-occlusion mask; drawing a
visible ship overlay alone would introduce another priority mismatch.

### Resolve raw object offsets through the constants table

The SOZ rappel wire final swing reads `$46(a0)` in `loc_4AC98`, while its
initializer writes `parent3(a0)`. `sonic3k.constants.asm` defines `parent3 = $46`:
these are the same initialized endpoint reference. An earlier implementation
misread the numeric spelling as an unused pointer and forced immediate
retraction, then encoded that mistake as a unit expectation. Check aliases in
the owning constants table before claiming a shipped bug; follow both the
writer and reader. Test sustained behavior and release, not only arrival at a
routine. The SOZ methodology-v2 plan records the correction.

The DEZ lift arm uses the multisprite layout: `$24/angle($26)` are
`sub4_x_pos/sub4_y_pos`, not unused saved coordinates. `sub_4748E` fills the
links, reads those just-written coordinates into the main sprite, then moves
the end joint into sub4. Treating the numeric fields as previous-frame storage
invented a delayed joint and placed the first main sprite at zero; a test built
from that interpretation also passed. Resolve the active layout from
`render_flags` bit 6 and `sonic3k.constants.asm`, then check the complete draw
order and positions. The September 22 campaign audit records this correction.

`LRZ3_ScreenInit` is another alias trap: after copying the fire palette, `a1`
points at `Target_palette_line_4+$20`; `.offset` is explicitly
`Stack_contents-(Target_palette_line_4+$20)`. The subsequent `$9C0/$36C` writes
therefore target `$FD10/$FD14` in stack RAM, not `Player_1+x_pos/y_pos`.
A port that teleports the player can make an incorrectly positioned checkpoint
fixture pass. Preserve the shipped writes' lack of player effect, assert that
screen setup retains the incoming position, and declare the real checkpoint
coordinates in route probes. The September 22 campaign audit records the correction.

### Seamless target initialization must precede resource handoff

The post-target resource handoff may install persistent palette targets, rotation
gates and native event state. `reinitializeZoneFeaturesForActTransition` clears
zone-scoped palette/render registries, so running it after the handoff erases
those resources. SOZ's connected Act1 victory capture exposed this: controls
released in Act2 while every restored world/player palette stayed black. Keep
target initialization before `transferAfterTargetInit`, and verify both readiness
and destination palette/pixels. ICZ's other production handoff transfers queued
resource ownership to the already initialized event owner.

### CPU recovery status resets must release engine grounding caches

A native `status` reset can clear `Status_OnObj` while preserving the stale
interaction pointer and the old object's standing bit. The engine has additional
riding/standing snapshots: clear those at recovery's native status reset with
`ObjectManager.clearRidingObject`, without clearing the ROM-owned interaction
or object bits. Otherwise object-control frames can preserve a former support,
and the pre-movement desynchronization recovery grounds Tails on the first
normal frame, skipping gravity. The independent SOZ recording exposed this at
recovery handoff; the resulting missed Tails Sandworm kill later changed Sonic's
rebound, so Sonic's first position mismatch was a downstream symptom.

### Native interaction pointers follow recycled SST slots

S3K `sub_13EFC` compares the saved code-pointer high word with the current word
at the player's `interact` slot, then refreshes that word while on an object.
A released Java contact does not imply that the native slot is empty: allocation
may already have installed another object there. Read the live slot occupant;
compare zero only for an actually empty slot. Preserve same-word replacements
and detect changed words. A live occupant without a code-pointer provider is
unknown, not an empty slot. Rewind tests need a recycled slot whose old contact
is absent after reconstruction, not just restoration of an unchanged owner.

### Oscillation table offsets include a native control word

`OscillationManager.getByte/getWord` address data after the native two-byte
control word. Subtract2 from `Oscillating_table+$NN` references before selecting
an engine offset. SOZ `loc_402CC/loc_402EE` read native`+$16`, hence engine`$14`.
Reading engine`$16` selected velocity instead of position: negative velocity's
high byte displaced sand-block spawners by roughly255pixels and changed their
zero-position release gate. Distinguish position and velocity in routine tests;
reset-state tests where both high bytes are zero cannot catch this error. The DEZ floating-platform bring-up found the same error inherited from LRZ `$2D`: native `$0A/$1E` must become engine `$08/$1C`. A 180-frame placed ride exposed a 255-pixel jump that comparing both object implementations missed; test the native-layout bytes through a full turning interval.

### Independently allocated exit helpers must not retain the retiring boss

SOZ `loc_77A6E` is allocated without a parent pointer. It waits on `_unkFAB8`
bit 0, set at `loc_779C0`, then `loc_77A98` follows Player 1 independently
until level clear. Model that captured signal rather than reading the boss's
escape phase through a retained reference. The controller-only end-boss route
exposed an unregistered reference between root deletion and destination load;
continuous snapshots cover this interval even when selected hit/escape rewind
spots all pass.

### Unsigned negative-window comparisons can exclude zero

S3K `loc_1E45A` first uses `BHI` after subtracting the player's feet from the
surface, then `CMP.W #-$10 / BLO`. Taken together these admit native values
`$FFF0..$FFFF`, corresponding to positive overlap1..16; zero is rejected by the
second comparison. Treating this as an inclusive0..16 range made the SOZ spring
vine capture a rolling player one frame early. Test both zero and the negative
window edge rather than deriving a signed interval from either branch alone.


### Debug checkpoint jumps need a load boundary

Moving the player and camera to a distant checkpoint leaves source-room event
state alive. In SOZ2, a jump from the early temple keeps background routine `$10`
and bypasses the sand-exit transition to `$20`, preventing boss wall art and room
brightening. Seed a fresh checkpoint and use the production reload; do not save
source runtime state or bypass native event gates. Verify destination events,
unchanged lives and rewind timeline isolation, not coordinates alone.


### A bare native allocation search does not occupy a slot

S3K `AllocateObject` and `AllocateObjectAfterCurrent` only scan SST code pointers
and return an address/condition codes. Occupancy starts when the caller writes a
nonzero code pointer. Mecha Sonic's `loc_7B39C` ends with a bare `AllocateObject`
and no write; reserving an engine slot for it invents an occupant. Distinguish the
search from the caller's initialization before translating allocation pressure.
Origin: 2026-09-22 S&K completion campaign, SSZ Mecha graph audit.

### `TerrainCheckResult.hasCollision()` means overlapping, not "found"

A terrain sweep written around `hasCollision()` reports **no terrain anywhere**, confidently
and silently. `ObjectTerrainUtils.checkFloorDist(x, centreY, radius)` returns
`hasCollision() == false` with `distance() == 8` for a floor 8 px below the probe — a real
surface it found and measured. It returns `true` only once the probe box is *inside* the
terrain (`distance() <= 0`). Nothing found returns `distance() == 32767`.

So "did this probe find a surface?" is `distance() != 32767` (in practice, a small finite
distance), and the surface is at `centreY + radius + distance`. Measured 2026-09-18 while
sweeping Death Egg act 2 for floors under the `$5B` gravity swaps: the first sweep reported
that all eleven sites had no floor within `$400` px, which is obviously false for a level
people walk through, and the fault was only located by running the same helper against the
already-measured corridor at x=`$1ACC`.

**Calibrate a terrain sweep against a known-good point before believing a negative result.**
A sweep that finds nothing is far more often a wrong predicate than an empty level.


### A working early object update does not prove its lifetime tail

DEZ's `$5F` turbine controller accelerated correctly, then left its player frozen
at `$262D` because default range retirement removed the movement owner. Its ROM
tail shifts the anchor `$400`, compares an unsigned `$680` range, and runs after
both player slots. Test the production loop crossing the unload boundary; direct
`update()` tests cannot see this failure. Also distinguish `SolidObjectFull2`'s
returned d6 side bits (`loc_1E094` sets them in air) from grounded status pushing
bits when implementing bounces. See the 2026-09-22 S&K campaign audit.

When a test changes viewport configuration, rebuild the gameplay session before
loading and assert `GameServices.camera().getWidth()`. `Camera` captures dimensions
at construction; asserting the config value alone can report wide coverage while
the production camera remains 320 px. Rendered captures exposed this in the DEZ
room follow-up; matching the actual width also changes when the puzzle spawns and
therefore its bob phase, so identical pad timings need not solve both views.


MHZ2's ship controller and propellers likewise disappeared after one tick because
the engine applied world-range retirement to their native workspace/screen
coordinates. A ROM tail that returns or calls only Draw_Sprite has no implicit
world deletion. Exercise it through the real object manager, not repeated direct
updates. Its capsule/results also retained boss-owned camera/control state:
generic results cleanup must not restore pre-boss bounds while a scripted exit
still owns the expanded arena.

SSZ swinging carriers (`loc_46142`, `loc_461FE`, `loc_462B6`, 2026-09-24)
make the hub's coarse-X test responsible for retiring the whole group. The arc
and rider bar draw without independent range tests. Generic manager culling of
a swinging tip removed the bar while the arc retained its identity, both dropping
a usable platform and crashing rewind capture. Mark all three as manager-persistent
so the hub's own ROM range check can signal arm then bar. Verify the cascade still
deletes/recreates the graph; do not merely null the reference during capture.
The cold SSZ route first diverged at input7076 because the restored bar now caught
Sonic, requiring an ordinary jump to continue along the upper walkway.

HCZ fan bubbles (`HCZCGZFan_Bubble`, `$30834`, 2026-10-08) likewise call
`MoveSprite2` once and then `Draw_Sprite`; the draw tail supplies neither a
second move nor camera/age retirement. Native observations show bubbles
continuing outside the camera margin until their own water check. Preserve
allocation before RNG: use the existing reserved-slot factory so a saturated
pool does not consume randomness before discovering allocation failure.
The fan's sound and bubble gates read `Level_frame_counter`, whereas object
`update` receives `V_int_run_count`; test the clocks with different residues.
See the [S3K lane audit](audits/2026-10-07-s3k-parity-gap-verification.md).

### Independently allocated contents need their own lifetime contract

S3K `Obj_MonitorSpawnIcon` allocates `Obj_MonitorContents` with
`AllocateObjectAfterCurrent`. The broken shell is later deleted offscreen by
`Sprite_OnScreen_Test`, while the contents finish the rise and pay out
(sonic3k.asm:40504-40516, 40640-40652; returned HCZ row 3832, 2026-10-17).
Three engine defaults each cancelled that award without an error:

- a shell `onUnload` that destroys a parent-linked mirror;
- the generic dynamic-object `out_of_range` unload in `ObjectManager`, which
  runs unless the object reports `isPersistent()`. Use it when the ROM routine
  has no offscreen check;
- the rewind probe in `ObjectRewindDynamicCodecs`, which knows only listed
  constructor shapes. Any other shape is silently not recreated. S2
  `MonitorContentsObjectInstance` shows the working pair: an `(ObjectSpawn)`
  probe and an `(ObjectSpawn, PlayableEntity)` recreate.

A persistent world-space object (`render_flags` bit 2) also needs
`RomWorldPositionedObject`; without it, a seamless transition that carries it
throws.

### A dying child can outlive its parent's SST identity

FBZ's cold Tails results replay (2026-09-27) found the same ownership error in
converted arm chains. `loc_6F3C4` installs `Obj_FlickerMove`, so the captured
converted phase must run before any parent/arm availability guard. A retired
arm cannot freeze its surviving links after reconstruction. Include the native
unsigned `(y - cameraY + $80) > $200` cull: horizontal-only lifetime left links
thousands of pixels below the arena. Also inspect the called movement routine:
S3K `MoveSprite` adds `$38` gravity after using the old Y velocity;
`MoveSprite2` is the no-gravity variant. An existing fragment test incorrectly
asserted no gravity and had to be corrected against the disassembly.

LRZ drill debris (`loc_78A70`, 2026-09-24) becomes independent `Obj_FlickerMove`:
it never reads its parent again. Keeping an `AbstractBossChild` relationship
retained the hidden drill solely for its clock/reconstruction link. Model these
pieces as standalone slots, and transfer the drill's slot to EndSignControl.
Preserve the initializer's Draw_Sprite-only dispatch before movement/flicker and
Go_Delete_Sprite_3's next-dispatch deletion. Register a replacement before calling
an initializer that reads services; construction context alone does not bind its
post-construction methods. Test reconstruction after the source parent is gone.


DEZ final fingers (`loc_80D64`, 2026-09-23) wait 32 updates while their hand
(`loc_80B42`) deletes itself first. `Refresh_ChildPosition` dereferences the
stored slot address, so cleared position words read zero and a reused slot
provides the replacement's position. Keeping a Java parent reference reads
stale coordinates and can crash rewind capture after that parent is removed
from the identity table. For this phase, capture the native slot address and
release the identity link when entering the dying code. Test an empty slot,
reused slot, and capture/restore after parent retirement. Do not delay parent
deletion or freeze its last position to avoid the dangling reference: both
change native allocation/position behavior.


DDZ widescreen capture (2026-09-23): normalized float transport is not the
VDP scroll contract. Decode HScroll back to an integer before modulo; a tiny
wrap-boundary residue can select unrendered column512 of an800px allocation.
The live repeated-render regression catches the cloud seam, while an isolated
core-context shader test did not. Test the production render path as well as
coordinate-coded textures. All declared sampler uniforms need compatible
texture-unit bindings even when their shader branch is disabled.


Exact-SST act transitions (MHZ/HCZ,2026-09-23): checking the rebuilt manager
before the first player update misses delayed double registration. An offset
step marked the already-carried Insta-Shield unregistered, so the next player
update inserted the same identity again. Step the player, then assert one owner
and capture/restore equality; a correct immediate carry snapshot is insufficient.


MHZ2 entrance (2026-09-23): `Check_CameraInRange` is an activation gate, not
an unconditional delete. Its failure branch `loc_85C74` calls
`Delete_Sprite_If_Not_In_Range`, retaining a still-near actor in its existing
SST slot. Deleting on every rectangle miss loses actors admitted by the wider
placement window. The resulting missing leaf-blower cutscene looked like a
traversal dead end; the native recording hits the same Knuckles-only wall and
then lifts Sonic. Check the expected event owner before tuning route inputs or
collision. Test pre-activation waiting followed by admission, not only direct
construction inside the rectangle.


Player-controlled cutscenes also own rendering writes. MHZ2 `loc_6339C` sets
hardware priority while carrying Sonic through foreground rock and clears it
at `loc_633D6`; movement/control-only tests missed an invisible lifted player.
Audit every dynamic `art_tile` and `render_flags` write alongside position and
control, including release/reset. Object display-list priority is independent.
Assert the live player bit through replay and inspect an actual terrain overlap.


A native child is not necessarily owned by the actor that allocated it.
MHZ2 `loc_6338E` stores only a player slot; `loc_632AE` retires Knuckles while
the lift remains active. Requiring a live Knuckles parent during carrier rewind
recreation loses the lift mid-ascent. Follow the actual RAM references and test
restoration after the allocating actor has gone. Likewise, `Child_Draw_Sprite`
contains a lifetime branch despite its name: execute that branch in gameplay
updates so headless simulation does not retain deleted actors' children.

Player rewind must preserve both the live hardware tile-priority bit and the
sprite display bucket, independently of collision layer and follow-history arrays.
The SSZ cold route (final pad ascent, input 14840) exposed a snapshot which restored
history but retained the future live priority: subsequent replay rewrote the
history with `$80` instead of zero. Test both priority directions and distinct
sprite buckets; comparing only fields already present in the snapshot cannot find
an omitted field.


A solid's placed-object ID is not proof that a live contact exists. S3K event
platforms can have ID zero, and clearing contact leaves the ROM `interact` slot
sticky. Player rewind records binding presence separately from ID and released
owner provenance, then relinks only the captured slot. Test zero-ID live contact,
cleared contact with a still-occupied slot, and deleted-owner slot reuse. The
September25 LRZ boss replay exposed this when a platform expired after restore.

### S3K control restoration preserves the last interaction address

`Restore_PlayerControl` / `Restore_PlayerControl2` clear byte `object_control`
($2E), clear `Status_InAir`, and publish Wait animation/frame/timer. They do not
clear word `interact` ($42). The MHZ `loc_76270` post-capsule caller also preserves
it. An extra engine slot reset left the live capsule reference bound to slot5
but the captured interaction slot at0; the MHZ cold route first exposed it at
input22758 when rewind could not relink the contact. Removing the unsupported
write restores ROM semantics and the full incoming route. Do not solve this by
weakening rewind comparisons or relinking to the nearest object. Explicit
interaction clears in other routines (for example FBZ chain release) still apply.
Evidence: 2026-09-25 S&K campaign broad-validation follow-up.


### Explicit floor probes must not inherit collision-dispatch activation

`Sonic_Balance` calls `ChooseChkFloorEdge` directly (S3K `sonic3k.asm:22566-22570`;
S1/S2 equivalents are cited in `checkTerrainEdgeBalance`). The engine's sensor
active flags emulate a different routine's quadrant dispatch. They are a cache,
not a native precondition for Balance. The Knuckles cold LRZ2 route exposed a
forward-replay mismatch at input41205 after restoring from an upward jump:
disabled floor probes returned null, falsely selected an edge, flipped facing
and reset look-up timing. Immediate snapshot comparison alone missed the cache.
The balance routine now enables its explicit probes locally and restores the
flags afterward, as the existing ground-wall probe already does. Do not add a
ROM condition based on the cache or hide the differing player history.
`TestGroundSensor` checks enabled/disabled probes on both edges, all three S3K
characters and both gravity directions; the cold route exercises the actual
restore and45-input replay. Origin: 2026-09-25 S&K campaign direct-HPZ follow-up.

### Gravity orientation must have one owner

S3K `loc_10C62` runs Animate_Sonic then XORs render_flags bit 1 under
Reverse_gravity_flag; Tails/Knuckles do the same. Player draw and Hyper trail
consume this completed orientation. A second draw-time XOR cancels it. The
DEZ2 cold-clear video exposed precisely that composition of `c122066f8`'s
animation flip and `8f5da1c8a`'s drawing flip. Tests of each in isolation passed: the
old drawing test toggled gravity without advancing animation. Exercise animation
and the actual renderer argument together, on consecutive frames and on return
to normal gravity. Objects owning mapping frames also own render flags; generic
drawing must not reinterpret them. Tails carry skips the player animator and
therefore publishes facing and gravity itself (`loc_14492` / `sub_1459E`).

### Proximity is not an established platform ride

DEZ final floor (2026-09-25): `providesPreMovementGroundAttachmentSupport`
feeds the pre-physics grounding recovery, not just terrain attachment. Opting a
persistent top-solid into it accepted Sonic at Y186 above the real Y205 standing
height. Recovery cleared air without establishing a rider; the inline checkpoint
then rejected contact, producing alternating air/ground and mapping7/8 every frame.
`loc_5A860`/`loc_5A912` instead call `SolidObjectTop`; new riders pass `loc_1E45A`
and `RideObject_SetRide` before the standing-bit continuation can carry them.
Use real contact ownership for ordinary platforms. An entry test starting one
pixel above the floor missed this; descend from above the proximity window and
assert stable support and animation on every subsequent frame.

### Tails flight ceiling uses signed words

S3K `loc_14892` adds `$10` to `Camera_min_Y_pos` as a word, then uses
`cmp.w y_pos(a0),d0` / `blt`. Preserve both addition wrap and signed comparison.
Promoting the camera minimum with `& 0xFFFF` turns SSZ's `-$100` into a ceiling
below the entire level and cancels upward flaps. `$FFF0 + $10` must wrap to zero.
The former unsigned unit-test expectation was itself incorrect; positive-bound
clamping, wrapped addition and negative SSZ bounds have independent regressions.

### Native camera equality gates need the preliminary clamp projection (SSZ, 2026-09-26)

SSZ loc_57686/loc_5770C install preliminary limits before testing camera X at
$160/$1660. Projecting only the final arena while always adding the widescreen
inset to the gate creates a dependency cycle: an800px leftward GHZ approach
clamps at raw$160, compares$250 with$160, and never sets the flag which enables
projection. A checkpoint already aligned with the equality can pass while the
ordinary route fails. Keep camera projection ownership through the preliminary
bounds as well as the final lock, preserve native words for player walls, and
clear preliminary ownership at the original open-bounds branch. Verify an actual
approach at every supported width, asserted after session creation, including
pre-lock capture/restore and forward replay. The campaign audit records the cold
red probe and subsequent validation status.


### Controller lookahead can reward avoiding the encounter

The SSZ Tails input-authoring prototype compared boss health before and after a
trial. Before allocation, health was represented as zero: a policy that let the
boss spawn appeared to lose eight hits of progress, so the author preferred
avoiding its trigger. Until the target exists, use the approach policy rather
than comparing fight policies. After allocation, an absent target is not defeat;
require an observed zero-health state, and rank death below surviving trials.
`SszBossInputAuthorTool` preserves this correction from the September2026
campaign. Its scores/horizons are authoring heuristics, never ROM/gameplay rules.
Whole-registry rewind also needs the external input driver's held-button history,
and must not cross a level replacement. Only a fresh uninterrupted input replay
can validate the selected route; speculative trial state is not route evidence.


A projected min/max pair alone does not guarantee a centered widescreen arena.
Shipped ScrollHoriz clamps directionally, and its deadzone branch does not
clamp at all. SSZ's cold Tails Mecha approach retained camera6355 instead of
6320 at800px even after projection was enabled; camera-relative boss geometry
was35 world pixels right of the native arena. All five checkpoint-start tests
passed, while the cold regression failed. When a native final lock must remain
fixed on widescreen, use the existing `lockedNativeHorizontalCamera` policy,
owned by captured event state, and test the ordinary approach plus release/load.
Do not replace the shared ROM camera clamp with an unconditional symmetric one.


### Finite world clipping and autonomous foreground scroll

A foreground plane is not necessarily terrain: SSZ2 ApplyFGDeformation puts
clouds on Plane A and advances their texture coordinates indefinitely. The
widescreen finite-layout guard originally clipped those deformed coordinates,
erasing clouds as their samples left the layout and exposing black behind the
island. Clip camera/world coordinates instead; wrap the sampled texture within
the visible world. Test both scroll signs, both physical boundaries and visible/
priority-mask output, then inspect a long production capture. A static edge test
alone passed the broken implementation. Native320 did not enable the guard, so
a native-only rendering check missed this defect.


Native arena projection also has a lifetime across seamless reloads. MHZ replaces
its Act 1 runtime while results retain the rebased native camera maximum `$98`.
Dropping the projection with the old boss flag slides the wide camera and clips
results behind the automatic bounds mask. Derive the incoming projection from
the retained boundary until its actual expansion, not the act identity or the
earlier results-active clear. At800px its visible left edge is negative; convert
that display origin before unsigned ROM event comparisons. The campaign's
`TestS3kMhzMinibossViewport` and cold wide route cover both halves of this handoff.

### Seamless reload can expose base art beneath direct DMA

MHZ investigation, 2026-09-27: glyphs visible on the reload frame were initially
misidentified as stale sprite art. A tiles-only render was byte-identical to the
full render, while atlas lookup and CPU pixels agreed with the loaded level.
`Load_Level` in the ROM retains VRAM; rebuilding the engine level exposed base
patterns normally replaced by `AnimateTiles_MHZ`'s direct DMA. Preserve those
ranges through the resource handoff and publish mutation effects immediately:
frame-top reloads return before the ordinary mutation flush. An extra animation
pass hides the symptom but also advances unrelated clocks. The independent
sprite-publication reset must be investigated separately.

### Sprite publication survives in-place layout reloads

`LevelSpritePresentation` models the pending CPU SAT, published VDP SAT and HUD
counter publication separately. A seamless `Load_Level` call in LevelLoop is not
a fresh Level initialization: clearing all three produces two missing-sprite
frames. Retain these screen-relative tables through zone registry reset, while
invalidating the source act's full-layout scroll addresses. Before assuming
retained sprite art is invalid, compare the referenced pattern pixels across the
actual resource handoff: the MHZ probe found only the17 player DPLC slots changed,
already covered by immutable frame versions. Previous-frame table restoration is
a useful diagnostic, but production retention belongs at the actual boundary.

MHZ's `Events_fg_0` tracks physical camera X while `Events_fg_1` accumulates
background travel across arena repeats. `MHZ1_BackgroundEvent` rebases the former
and retains the latter. Resetting both on act initialization makes the forest
jump. Restore the accumulator only after the destination scroll handler's lazy
initialization, then recompute deformation without another animation tick.

**Provider registration is not every zone's resource-use boundary.** S3K's
provider constructs all handlers before selecting a zone. Eager ending-band
reads made unrelated FBZ/CNZ no-ROM registration tests fail and caused
`ParallaxManager.load` to discard the entire provider on the same IOException.
SOZ/LRZ3 already defer their native tables until relevant init/render. Store the
ROM reference, validate the ending `$5B144`/`$5B15A` banks into one immutable pair
on actual ending use, and fail before native RAM/output changes if either bank
is unavailable or malformed. A first post-restore rebuild may populate that
derived cache without consuming a scroll tick. Do not replace unrelated fixtures
with ROM-required skips or supply fallback tables to hide this contract regression.

MHZ2 pillar arithmetic (2026-09-27): successive `DIVU.W` operations at
`loc_55586` are a 32-bit division assembled from two words, not independent
word quotients. The first remainder stays in the upper half of D2 while MOVE.W
replaces the lower half. Dropping it makes the native $4180 ramp flat instead of
-$8000 per line. `loc_555D2` then derives solid/spike world positions from those
scroll words, so this rendering arithmetic also changes collision. For a centered
wide view, add the display inset to the published scroll words only; remove it
before recovering the original helper positions. The earlier `loc_555FC` clear
must not publish new helpers. See the September 22 campaign audit for baseline
probe evidence and candidate validation status.


### Twisted-vine standing bits are persistent rider state

`Obj_MHZTwistedVine` (`sub_3DCD0`, `sub_3DE80`) retains per-player standing
bits while moving a rider around the curve. An identity set representing those
bits is not a derived contact cache: a restored player halfway along the arc
cannot re-enter through the narrow admission window. Capture the collection
with stable player references and test object recreation with replacement player
instances, both participants and independent release. The2026-09-27 MHZ800
cold route exposed this at input13000; short tests cover both curve halves.


### Custom restore hooks must not clear generically captured contact ownership

DEZ1's turbine puzzle retains whether P1 already pushed in this contact pass,
modeling `loc_499EC`'s `a1` ownership with shipped `FixBugs=0`. The generic codec
captures this flag, but an old custom restore hook cleared it after decoding.
A snapshot taken on the sixth panel contact exposed an immediate world-state
mismatch; a short P1-contact/restore/P2-contact test also detects the wrong
panel owner. Restore the declared state exactly and let the next normal update
clear the flag. Do not silently turn a captured flag into a derived cache in a
custom hook. The original two-player bug remains preserved (2026-09-28).

### A cache's loaded flag must be as transient as the cache

`AizPlaneIntroInstance` draws intro Sonic through its own ROM-art
`PlayerSpriteRenderer`s, which the rewind policy treats as transient, but its
plain `renderersLoaded` boolean was captured. A restore that recreated the
intro copied "loaded" onto an instance with null renderers, so it never drew
Sonic again; every gameplay key still matched and only the sprite table lost
his 16-19 Player_1 tiles. Make the guard flag Java `transient` with the cache
it describes, or derive it from the cache reference (2026-10-16).

### Re-admit a departed sprite instead of rebuilding it

The `loc_68D8` carry-in Obj_Tails deletes itself after its flyoff. A restore into
its carry window rebuilt it through a factory, but solid-contact headroom and
standing latches and plane-switcher sides capture players by reference, so the
new carrier replayed beside stale entries for the old one (`headroomSnapshots`
9 vs 10 in MHZ1). Re-admit the departed instance with its structural links and
clear only uncaptured deletion markers on restore (2026-10-16, `f07a25bf9`).

### Spawn-based rewind probes must retain placement data

Generic recreation constructs a probe before invoking the object's recreation
hook. A typed object can reject an anonymous spawn before the hook ever sees its
captured properties. Use the captured spawn for SpawnRewindRecreatable probes
when present. Coordinate-only native effects such as S3kSignpostSparkleChild
deliberately have no spawn; retain the neutral probe fallback and restore their
coordinates from captured scalar state. Check the real ObjectManager codec path,
not only a direct recreation-hook call or an in-place restore.
Origin: [editor authoring parity recovery](plans/2026-10-22-editor-authoring-parity.md#complete-smoke-and-recovery).

### A recreated effect must keep the owner its spawner supplied

`SkidDustObjectInstance` received its owner's dust renderer from its spawner;
rewind recreation from the spawn record had none and borrowed P1's. A follower's
puff then drew from P1's dust bank, although `loc_18D1C` copies the owning dust
object's `art_tile` (sonic3k.asm:34166). Capture the owner's identity and
resolve its resource on restore (2026-10-16, `0ea15a057`).


### Fixed native bounds must not inherit engine smoothing targets

A seamless reload's current-bound and target-bound overrides are separate. AIZ's
`AIZ1BGE_Finish` writes a fixed X lock of `$10/$10`, and `Do_ResizeEvents` eases
only max Y. Pin the engine's X targets too, or the loaded defaults move the lock
on the next tick (S3K trace campaign, 2026-10-03).

**Head-only presentation follows native admission and indexed art identity.** A Sonic head
crosses mapping-piece and tile boundaries, and slope, crouch and balance poses put it below
or beside the body. Use reviewed per-frame anatomy metadata, the original mapping-piece
index and source pixel centres; retain explicit stock ball/special-pose classifications.
Scale only admitted ROM head pixels about an unflipped neck anchor after the native SAT
mask pass. Flip pixel indices with `-coordinate-1`, and geometric anchors with
`-coordinate`. Freeze the indexed DPLC generation before later draws reuse its slots.
Each native virtual player bank also needs a separate companion fragment bank: resetting
fragment IDs for every local S1/S2 preparation lets a second Sonic slot overwrite the
first slot's queued pixels. The production `ggfmod sprites ... char=sonic heads=true`
review panels and `TestPlayerHeadPresentation` cover the three native inventories,
source identity, flips, admission and simultaneous duplicate banks. Native-image packaging
explicitly includes the text metadata. Review panels use 112px cells and reject opaque-pixel
cropping through the maximum 200% scale; a smaller plausible-looking sheet clipped pushing
Sonic at the edge (Mutator Lab, 2026-10-08).

The graphics boundary owns immutable mask geometry and a producer-supplied replay
interface. Pose classification and the native head renderer stay above that boundary;
graphics must not name a player profile or call a level renderer directly. Carry the
shared replay adapter with the draw metadata so native SAT admission and ordering run
before the same head composition, without a global runtime-renderer registration.
Engine-only sprite graphics access uses the session bootstrap's immutable caller walker,
including hidden frames, and checks the actual caller's class loader. A lambda or nested
creator class must not inherit engine access from its host invocation (Mutator Lab guard
repair, 2026-10-09).

**ROM palette constants can describe another revision.** Sonic 1 REV01 moves the Sonic
palette relative to REV00. A raw sheet read through the old absolute palette constant
can still look plausible while turning Sonic green/yellow. Character contact sheets use
`loadCharacterPalette`, which resolves the native `PalPointers` entry, and retain the
original ROM identity; reference-tree art and guessed palette addresses are not runtime
fallbacks (Mutator Lab native mask research, 2026-10-08).

Raised hands can share a native mapping piece with Sonic's head. A broad right edge
on S3K balance frame A1 selected part of its raised glove; the actual ROM detail and
production head/body panels exposed it. Trace the explicit eye/muzzle boundary instead
of selecting the whole piece, and verify an opaque glove pixel remains at its stock
body coordinate. Distinct geometry is required even when piece IDs appear convenient
(Mutator Lab mask refinement, 2026-10-08).

### Playable rule binding must follow the selected world

A bootstrap default can remain Sonic 2 while a normal Engine world has selected
Sonic 1 or S3K. Bind construction and level-reset physics/rules through the
current-or-bootstrap accessor; an end-of-frame status refresh is too late for
CPU initialization and fresh-level assembly. The 2026-10-12 runtime qualification
exposed Tails' first S3K CPU dispatch writing S2's $4000 offscreen position instead
of `sub_13ECA`'s $7F00. Fixtures that select the same bootstrap and world hide this
lifetime error. Test differing modules at construction and reset, plus the
pre-session bootstrap fallback, without writing expected trace values into play.

### Native service recreation needs verified publication authority

`LevelManager.initGameModule` calls `createGame` on every full load. Sonic 2 and
S3&K consequently publish a new PLC service, even when their root module object
is unchanged. Strict generic `registerOrRefresh` correctly rejects a different
unowned adapter reporting the old key; relaxing that rule would also admit
creator collisions and host-state theft. The 2026-10-07 composed focus exposed
this distinction in 107 Golf cases and one S3K lifecycle case.

Use `NativeRewindAdapterPublication` for the exact current adapter published by
the session's verified native root. Preserve that raw service's identity and all
queue state, refresh its incarnation under the same root authority, and advance
the course registry generation so retained checkpoints cannot silently cross
loads. Do not retain/reset a PLC queue merely to avoid adapter replacement.
Private fixtures must likewise retain the same root for module resolution,
world-session construction and pinned data: two separate `new Sonic2GameModule`
objects describe different lifetimes even when their names match. The native
publication regression covers direct and hidden creator method references,
foreign roots, bundle retagging, and both real ROM-backed PLC lifecycles.

### Creator quarantine does not replace frame-abort recovery

Fresh service withdrawal prevents a quarantined creator from supplying another
recipe. It does not remove already assembled world actors, cached callback proxies
or controllers. A first dynamic optional-recipe failure must propagate its typed
abort to `Engine.runFrameWithModAbort`, which discards the failed frame and initiates
world/title recovery. Catching that abort and selecting a native STOCK preset can
let retained callbacks run later in the same frame. Missing or already-withdrawn
services may still select STOCK; a newly failing provider is a different boundary.

Second Strike’s 2026-10-15 real ModContext regression reproduced continued execution
for all three tested MGZ character configurations before removing the catch. The
Knuckles fixture originally used an inherited synthetic event admission and needs
migration to the actual placed A2 owner; that limitation does not alter the shared
fault-recovery finding. Keep cached/direct callback semantics and VM-fatal propagation
unchanged, and test the native producer with the real frame-abort boundary.

**Finite audio tails own the complete presentation.** A cue mixed after a song's
end taper can remain audible beyond finite EOF even though the song clock has
stopped. Fade the combined output and retire remaining cue voices at the finite
boundary; also fade a cue that exhausts its selected source window. Sitar Hero's
2026-10-08 fumble regression measured 439 nonzero post-taper frames before this
ordering repair and zero afterward. Keep calibrated judgment coordinates separate
from the audible song clock when testing cue freshness or sending peer timestamps.

**Physical input cannot reconstruct admitted fractional-speed input.** A tap can
be retained across a zero-tick presentation or a native S2 special-stage lag, then
consumed after the physical key is released. Live rewind must retain the actual
admitted P1/P2 sample and post-tick fractional/pending state in an aligned internal
history ring; replaying held-only BK2 rows loses the tap and can reset a nonzero
remainder. Preserve ordinary driver/debug fields and reject metadata from a
different world, level, provider or entry lifetime. Never obtain these values from
trace comparison rows. Mutator Lab reproduced the loss at 75% and 150% and verified
the repaired native acceptance/jump (stage commit `7cca6aef3`, 2026-10-08).

**A bound context can outlive its current world.** The native hub can retain a
retired gameplay context during title/session handoff. Check that the context is
runtime-ready before resolving its world/module/policies; an eager global module
lookup crashes startup even when no gameplay body is due. Bonus mode also does not
imply interactive readiness: let its pending initial ProcessSprites setup finish
at canonical cadence before spending a modified speed budget.

**Resident patterns can change without changing identity.** KosM DMA updates
`Sonic3kLevel` patterns in place. A sprite sheet decoded from resident VRAM
must register its native source tile range with the object-art provider so
`refreshAffectedRenderers` republishes its virtual atlas. A ready renderer and
nonzero CPU patterns do not prove GPU pixels are current. The S3K ending
Tornado at input600 had6845 nonzero pixels and a ready virtual base137948
while remaining invisible: an identity-only cache missed the upload and the
sheet lacked `levelArtTileRanges`. Use the existing range refresh owner, not
a hash scan, extra art job or fitted delay (2026-10-13 ending lifecycle task).

**External pad priming is not a production setup row.** The native initial
`Process_Sprites` callback has no VInt, but GameLoop still admits its input row.
`consumePendingInitialProcessSpritesPass()` executes that callback; do not infer
a token-only skip from its name. S3K Knuckles ending authoring (2026-10-13)
externally primed input0 then started at1, copying an older HeadlessTestRunner
fixture, and died2096 before the first fight. Production input0 published the
same SST4/5/6 and P1 as the fixture callback, with VInt unchanged, then matched
all three boss milestones. Match the actual callback/slot/input boundary before
reauthoring ordinary controls; never suppress setup or shift gameplay clocks.

**A width override does not resize an already-created test camera.** The ROM
Jupiter extension creates its gameplay managers before a test's session override.
A local HeadlessTestFixture can retain that camera. Explicitly refresh its viewport
before loading and assert the dimension, or create a fresh scenario context.
Four wider S3K credit-plane vectors read shifted glyphs with a retained320 camera
while the320 vector passed (2026-10-13). A width parameter is not width evidence.

**Initial touch publication follows the native range tail.** An object routine can
finish before its caller performs the native out-of-range deletion. Publish the
initial collision response only after that caller keeps the actor. LRZ2
`Sprite_CheckDeleteTouch3` / `loc_1B666` checks the anchor range before AddList;
publishing inside the shared update wrapper retained erased spike-ball owners
in both collision buffers after a real checkpoint death/title boundary
(2026-10-14 S3K follow-up). A full SST reset must also retire both buffers before
clearing identity tables and reusing slots. Do not hide missing identities,
replace them with new slot occupants, or suppress every native pending-delete
actor: the response-list eligibility owner retains its own native predicate.

**Identity-map iteration is not captured gameplay order.** Recreated players have
different Java identity hashes. Capturing an `IdentityHashMap` of player keys
verbatim can swap serialized associations after restore even though every key
resolves to the right participant. The AIZ2 S2-Tails/800/seven-Sonic entry
exposed refs3/5 swapping in AutoSpin state (2026-10-14). Encode this unordered
map in stable `PlayerRefId` order; preserve key/value pairs, null keys, the live
identity map and explicitly ordered maps. A deterministic changed-bucket-order
fixture checks replacement identities and duplicate character labels in both
restore directions. Never filter snapshot bytes to hide this discrepancy.

**Native white fades are distinct CRAM loops, not reversed screen overlays.** S3K `Pal_FromWhite`
lowers all components together every third V-int; `Pal_ToWhite` raises only the first nonwhite R/G/B
component each V-int. Retain the blocking loop's 22nd wait even when all colours have arrived.
Step the final normal/water target before profile/range conversion; tinting an already-faded donor
can erase its white floor. Keep raw published water inputs detached from pending writers when the
white generation advances or restores. Saturate public colour-step values before compact cache-key
storage: byte key 256 otherwise aliases step zero. See the
[ROM owner and red/green evidence](designs/2026-10-13-s3k-ending-lifecycle.md#native-white-palette-fade-owner-decision--2026-10-17-root).

**A capture does not need a second fade owner.** The runtime-ready GameLoop's
PLC lifecycle already services the FadeManager once per presentation, exactly
as the live Engine. A direct GameplayCaptureSession formerly also called its
UI pipeline, giving frameCount2 after one step of an actual90-frame fade
(2026-10-14). Use the existing production ownership predicate for the UI
fallback. The same rule applies to death/reload helpers: external manual fade
ticks can trigger fresh assembly outside its logical iteration. Preserve input
cadence and requalify affected cold routes after repairing a driver; do not fit
runtime timing or silently retain the accelerated protocol for old inputs.
Movies authored under the doubled fade spent 11 of the native 22 frozen fade-out
frames before each full load; `FadeWindowRetimeTool` inserts only the held-input
remainder there (gameplay and object VInt do not advance) and verifies the movie
uninterruptedly. A fade that runs alongside live gameplay is not a retiming site.


**Replay the displayed plane generation and the complete multipart order.**
A B-high overlay must use the same immutable horizontal/vertical/column scroll
buffers as the rear tile pass, including after rewind and lazy redraw. Reading
current CPU deformation later in the frame shifts only the replayed high tiles.
S3K ending overlap tests reproduced this at both320 and800 without changing
native scroll values. Also reverse the entire native main/sub2/sub3 group for
painter emission: reversing pieces inside each mapping alone lets a later
propeller mapping overwrite an earlier main-body wheel. Keep SAT collection in
native main-first order (`Render_Sprites` `$1AEE4/$1AF2A`). Independently reconstruct
opaque ROM overlap and priority before excluding a covered player pixel; the
native reunion's earlier wing legitimately covers20 of619 player pixels.
The same applies to manual results painters: after correcting digit geometry,30 native
icon/text pixels still differed because only each mapping was considered. Reverse the
whole tally/reveal group as well as its pieces; the declared Tails results prefix then
matches17 raw RGB checkpoints. An animation-phase explanation was rejected by opaque
ROM pixels. Clearing a multipart child count does not suppress its parent's mapping.
Scripted status-facing does not imply render_flags-facing when object mappings
bypass the ordinary playable mapper. See the
[ending publication evidence](designs/2026-10-13-s3k-ending-lifecycle.md#source-owned-initial-and-published-ending-presentation-2026-10-14).

**The first authored zone is not necessarily a scene mod's entry point.**
Native friends qualification selected Starpost Valley's first owned zone, a
festival, before opening its farm scene and hit the legitimate `Activity needs
its retained scene session` guard (2026-10-14). Bootstrap scene-based diagnostics
from the backing game's initial zone, then let the scene own authored-act
admission. A matched real-JAR control reproduces the old failure and renders the
farm after the corrected bootstrap. Do not remove the mod's lifecycle guard or
invent session state to make an arbitrary zone load.

**KosM completion is a queued DMA, and equal descriptors are not FIFO identity.**
Process_Kos_Module_Queue waits for the complete direct FIFO, then calls
Add_To_DMA_Queue. The paired production pattern facade must wait for the actual
submitted child receipts and publish only at a supported VInt. Keep receipts
after parent claim and capture pending DMA separately from presented memory.
Two identical source/destination entries need their admitted native FIFO
position; Java identity is lost on copied snapshot restore and descriptor-only
matching lets an unpaired predecessor borrow a later job's readiness. Shift
only at actual archive retirement. Preflight both banks before admitting a
global producer so failure cannot leave unpaired physical work. Preserve
initial resident ingress separately: correct new upload timing does not prove
retained pre-upload VRAM bytes. Origin: 2026-10-14 ending transfer69e4f51.


**Decoded frontend sheets are not physical pattern residency.** A frontend can
render a virtual sheet correctly while the following level decoder replaces
its unwritten native pattern gaps with zero. S3K's initial Master span at
VRAM $A5C0 was exactly 2,176 bytes of title-banner ROM $35026C+$5C0, not new
Master art. Retain only admitted frontend CPU uploads at their literal native
destinations, seeded from the current stock Level's bounded physical patterns;
never copy virtual atlases or comparison VRAM. Overlay writes of zero still
overwrite retained bytes, and retained extent must not move the append cursor.

Freeze immutable bytes and resource-owner generation before an async build;
validate that owner is still installed before GPU publication. Equal bytes and
generation in a detached owner are insufficient. A menu's world replacement
may transfer these bytes only through a capture-created token binding the
exact reused data source and supported owner type. Consume the seed at real
resource installation, preserve it over ordinary title reset, and clear it on
a fresh context/hardware lifetime. Newer admitted SaveScreen uploads supersede
title art. This does not model untouched name tables, SAT or every level-to-level
gap (2026-10-14 ending ingress; d0fabc3ef candidate4d401bcc).


**A presentation hold can outlive its upload latch.** Rewind restores the CRAM
generation shown with the captured picture, independently of whether the next
scene uses level palette latching. Releasing immediate scene publication must
flush that restored hold even when the level latch is already false; guarding
release only by that latch leaves subsequent fade requests parked. Preserve the
immediate redraw generation, then test the next publication and two forward
cycles with an independent unlatched control. A capped matrix-failure preview
is not complete attribution: rerun both entire affected classes after the
mechanism repair. Origin: S3K sanctuary palette rewind,2026-10-17.


**Sprite ownership precedes plane priority.** In Blue Sphere, `Draw_SSSprites`
(`loc_99CC` / `loc_9A84`) appends cells directly, and `Draw_SSSprite_Normal`
appends mapping pieces forward. The first nontransparent SAT pixel owns the
sprite result even when a high plane hides its low-priority pixel. Clipping
that pixel before ownership can incorrectly expose a later high sprite;
painting all low sprites before all high sprites also fails the interleaved
priority entries in native turn frames. Keep indexed ownership and winning
priority together, then compare against the plane. `loc_9A04` / `loc_9ABC`
clear bit 15 of the base art word for the ORIGINAL unsigned perspective word
at least `$54`; mapping attributes are added with `ADD.W`, including carries,
rather than ORed. Shadow operators are later SAT entries; HUD number cells
belong to Plane A. Origin: Blue Sphere native sprite-priority task, 2026-10-17.


**Bind white-fade semantics at gameplay composition.** `GameplaySessionFactory`
translates the host's `PaletteWhiteFadeModel` into renderer-owned values through
`FadeManagerInternalAccess`. `FadeManager` must not discover a world/session or
game rules during palette publication, update or render. This policy belongs to
the composed owner, survives logical reset/rewind, and defaults to the overlay
for detached UI fades. Keep renderer attachment separate: only the actively
bound palette owner publishes CRAM fades. Origin: 2026-10-17 white-fade
architecture guard repair; existing native component/22-V-int and immediate-scene
rewind-release semantics are unchanged.


**A restored palette owner can rebind without submitting new work.** Whole-world
restore first reinstalls the displayed fade and its captured pending bit, then
restores the logical animation owner. A matching mode/steps/line-mask request
while that displayed generation is held is idempotent: preserve pending false
AND pending true. Different requests and ordinary latched updates still queue.
Otherwise an owner restoring NONE or FROM_BLACK creates phantom V-int work and
whole-world replay fails even if the displayed RGB is unchanged. Compare every
captured field; do not omit pending from snapshot equality. Origin: bounded
presentation smoke repair R5,2026-10-17, isolated donor old-red and two restored
generations in the existing S3K playable-completion Plan.


**A row-pointer write is not a decoded row copy.** Native LBZ loc544C6 changes
BG row addresses, retaining shared byte owners. After encoded collision aliases
are modelled, copying the common154-column map width into native four-byte BG
rows overwrites neighbouring storage. Repoint the encoded words under COW,
sync the decoded view and rebuild aliases; verify later source mutations and
whole-world restoration, not just the first rendered row. DDZ's separate smoke
errors were an undeclared synthetic fixture floor pass: Sonic_Init has no floor
probe and loc81554 owns flight. Declare its existing fresh-start inlet and keep
an independent unknown-definition refusal; do not invent missing RAM to make a
component bootstrap green (R13, 2026-10-17).

### Native instruction pins and delayed camera history are different authorities

A named recorder pin may lie inside an operand: actual locked-on ROM RecordPos
entry is10D66, X/Y writes10D7E/10D82, increment10D86; the retained named10D80
is the0010 operand of the X write. Verify bytes at real instruction boundaries
before registering a passive callback; do not mutate capture authority/fixtures
from a diagnostic. Recheck original ROM/movie/runtime/fixture/source identities
inside normal admission immediately before launch and after terminal, because
queue waiting can outlast a source freeze. Keep RecordPos, camera-entry, physical
camera return and published RAM-copy phases separate. Native R15 writes239.E0,
then current pose becomes231.E0, while delay0100 still selects239; engine's
latest23A likewise correctly gives19A. Different producer inputs do not prove a
Camera/CPU bug or identify the unobserved intervening object writer (R15,
2026-10-17; see the existing HCZ trace audit).


## A scene-owned layout clear is not decoder truncation

DEZ3 Load_Level copies a full `$1000` layout bytes from ROM; the actual layout
asset is `$F8` bytes and the following MHZ asset contributes nonzero tail bytes.
DEZ3_ScreenInit then multiplies unsigned header words (20*4+8*4 decimal), keeps
the low-word sum plus `$88`, and clears `$FF80F8..$FF8FFF` with inclusive DBF.
Missing that separate owner can select following-asset bytes through valid native
word row pointers. Preserve the full initial copy and put the clear once in the
scene event, synchronizing encoded COW and flattened mutation aliases. Global
loadMap truncation, visual-width wrapping or blanking collision profiles hides
the missing writer. SensorResult.tileId is a collision-profile index, not a raw
layout/128/16 descriptor. Retained16 patterns still use the destination LoadSolids
bank (`$09CFC0/+1` for final DEZ), never predecessor indices.

Qualify a real first production ScreenInit, preserve meaningful prefix/resources,
then force two whole-world recreations/forwards and a later legitimate terrain
edit before discriminating all tail bytes. Do not infer definition0 emptiness or
native route equality from a clear or a successful authored route. The separate
native four alias-word writes remain unimplemented. Origin: DEZ final residency/
first-difference task,2026-10-17; see existing trace audit and final-boss matrix.

### Piece-scoped standing bits need their own rewind key

A folded multi-piece object can represent several native SSTs, each with an
independent standing bit. Preserve the base spawn/instance identity channel and
piece index in both live standing keys and their pre-clear snapshots, resolving
fresh owner/player identities through the existing restore context. The riding
piece index is an independent channel: retaining a rider must not synthesize
an explicitly cleared live bit. An unscoped parent standing query does not
measure a child's bit. The2026-10-20 ICZ solo-Sonic maintained lower-platform
episode exposed the missing codec key; Candidate13 passes its complete-world
contact/ride replays twice per spot. Its separate synthetic two-piece landing
fixture still failed setup, so that result does not certify fresh sibling contact.

### Resync must retire the placed ownership it discards

When an in-place placement resync clears its active map, first retire the
outgoing placed actors' SST slots and unconsumed child reservations using the
existing owner-aware helpers. Otherwise the live allocator retains orphan bits
that capture cannot recover from live actors: immediate restore can compare
equal while the next allocation/dispatch order diverges. Preserve live dynamic
children and consumed reservations. Do not move this retirement into a generic
clear used during restoration. Candidate13's ObjectManager lifecycle controls
and all9 HCZ terminal cases pass after this bounded correction.

### Charging and moving projectiles can have different parent lifetimes

S3K CreateChild10_NormalAdjusted writes the immediate Gun into a Shot's parent3.
loc_9176C ends at Child_DrawTouch_Sprite after charge work, including arming
collision/velocity/SFX. Go_Delete_Sprite sets status7 before physical deletion,
so an engine isDestroyed check alone misses a pending Gun. Suppress the
retiring child's actual touch-list publication and draw without falsifying its
collision byte, then run physical deletion on its next own entry.

After a successful last charging tail, loc_917B4 never reads the Gun again.
Release that captured raw link at this actual last use; neither a permanent
parent dependency nor an early pre-charge guard matches the routine. Keep
missing/unregistered owners distinct from observed retirement. Generic rewind
must encode the required link through exact object identity while it is live;
a field excluded as a structural parent needs an explicit captured policy.
Candidate14's 22 SSZ Shot tests cover actual manager same-pass order, SFX/list/
draw edges and two fresh replay cycles at four boundaries.

The earlier synthetic two-piece standing fixture now passes all eight class
cases in Candidate14 after the normal pre-update lifecycle snapshot. Its
former failure was setup, not authority to change collision geometry or seed
standing bits.

### Physical display queues require current bytes and a shared producer stream

A native display queue stores physical address words, not retained Java owners.
S3K Draw_Sprite preserves duplicate submissions, counts each bucket's 63 entries,
and spills into later buckets. Render_Sprites dereferences current slot bytes,
updates render_flags bit7 before zero-mapping or exhausted-budget exits, and
shares its 80-piece budget with preceding HUD/ring output. A static plant queue
cannot be inserted by slot into legacy Java buckets and acquire those semantics:
the omitted actors also consume input positions, ordering and output capacity.

Fresh Level clear establishes known bytes only until subsequent writes. An
unmigrated actor can leave the bank stale; allocating its slot to a plant does
not repair that history. The native loader and plant INIT write partial fields,
allocation probes without clearing, and retirement clears exactly $4A bytes.
A queued address reused before consumption must see the new current bytes,
including preserved residue. Neither the old Java owner nor zero-filled defaults
is an admissible fallback. Rewind constructors must not replay original loader
or INIT writes.

Before migrating an issuer, define the canonical mixed submission/output stream,
all relevant field writers or invalidation, the exact legacy exclusion rule,
shared admission, and atomic preparation/publication/restore. A DATA bank or
queue round trip alone proves none of this ordinary integration. Native owners
are Draw_Sprite/Render_Sprites, Load_Sprites, Delete_Referenced_Sprite and
Obj_AIZForegroundPlant; the
[2026-10-20 source audit](plans/2026-10-13-s3k-playable-completion.md#physical-display-producer-integration-boundary--2026-10-20)
records the concrete gap without claiming an observed pixel failure.

### Detached player simulation must not publish live world events

A private native player can still escape its service context through callbacks in
`setAir(false)`. Stageplay's first standalone S3K simulation reached the live HCZ
water-tunnel player query through `publishLandingAnimationWrite`, even though its
hosted tests passed. Bound puppets skip live world-event publication; ordinary players
retain it. Verify graphics-free tools in a fresh JVM with no gameplay/editor session,
not only under a headless Engine fixture. Native floor collision also preserves the
subpixel word: compare foot pixels for landing support, and exact fractions only
when both runs consume identical inputs. See the
[Stageplay design](designs/2026-10-21-stageplay.md).


### Offline native worlds need the complete presentation binding (Stageplay, 2026-10-21)

`GameplaySessionFactory.attachManagers` creates owners but does not bind the graphics
camera; GameLoop normally completes that host step. A host that steps LevelFrameStep
directly must call `GraphicsManager.bindRuntimeManagedReferences` with its session's
camera/fade manager. Otherwise tile shaders show the level while queued sprite/HUD
commands subtract a retired bootstrap camera and disappear. A nonblank frame or
populated CPU SAT does not prove visible actors. Compare actual body pixels.

Native published SAT/scroll coordinates belong to the native camera. Cinematic
views reuse LevelRenderer's scoped current-world priority pass, preserving native
publication and activation. Bind the native camera for updates, apply the director
camera only for drawing, then restore it. Reopening a level also replaces its audio
producer: establish/renew an offline capture lease after that boundary, not before.

Stageplay camera limits are ROM camera origins, as consumed by
`Camera.updatePosition`, not terrain dimensions. Subtracting a second viewport
height from `Level.getMaxY()` can hide an entire valid airborne cast while native
state and scenery still look plausible. Clamp the native-height director directly
to those origins; adjust only the additional widescreen width. Exercise actual
on-screen airborne body pixels as well as platform pixels.

Fresh offline live-shot contexts also need the production game-entry palette
boundary: reset `RenderContext` (including per-sidekick banks), then
`GraphicsManager.clearPaletteTextures()`, before native load/graphics binding.
Keep initialized atlas/tile renderers. `GraphicsManager.resetState()` drops the
GL terrain renderers without recreating them and can leave valid bodies over
black scenery. Verify visible terrain as a positive control, plus a return to an
earlier act after changing cast; body-only and equal-blank-image checks can pass.

**Slide collision flags are not glide responses.** Native `Knuckles_Sliding`
(S3K:31032–31049; KiS2:38449–38469) calls collision-only correction and then a
second independent floor scan at corrected coordinates. Keep KiS2's 10/10 radii
through both scans and the writes, and carry the effective scanner bit/path out
of the first call. Do not dispatch glide landing/wall-grab responses from the
slide's temporary flags. Signed BPL makes +32→0 continue while −32→0 gets up.
Get-up uses byte subtraction then sign extension for the radius correction and
leaves double_jump_property intact. A late restore after ordinary setAir(false)
is insufficient: landing callbacks already observed zero. Use the narrow native
floor-reset entry before publication; ordinary landing keeps its original reset.
Declared component and registered restore/forward controls cover these branches;
LevelBound/camera prelude, slide sound and live route qualification remain separate.


**Preserve native landing bytes before callbacks, without repairing afterward.**
Knux_TouchFloor retains double_jump_property on native climb-floor exits. A
private exit that explicitly clears it before public setAir(false) exposes zero
to both hooks; restoring the old byte afterward also overwrites a hook's new
value. Reuse the internal native-floor entry before either clear and omit the
late restore. Keep ordinary and ledge-completion semantics separate. The14-case
public-dispatch control observes both hooks and callback-written66, as well as
ordinary/ledge negatives. Original5aef's47 returned-state checks alone did not
cover callback timing. See the aggregate's bounded5aef/372/372671811 composition
in [the swarm plan](plans/2026-10-22-native-gap-swarm.md).
