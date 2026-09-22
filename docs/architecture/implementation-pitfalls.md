# Shared implementation pitfalls

Read the relevant section when working on coordinates, objects, rendering, rewind,
headless tests, or audio. S3K-specific routing is in [AGENTS_S3K.md](../../AGENTS_S3K.md).

The things that cost the most time when missed.

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
form of `FindFloor`, sonic3k.asm:20242-20256), so derive expected player positions from the
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

**Managed children need identity relinking.** `RewindStateful` represents captured
helper values; applying it to a live managed child can make an owner's captured
collection retain stale objects after recreation. Use object scalar capture and
identity relinking for those children. Exercise remove/recreate/restore with the
real manager, including any optional defeat controller that advances child cleanup.
Test creation and retirement boundaries as well as a steady fight. LRZ's old
restore hook rebuilt destroyed arms (12 survivors became 24); restore the captured
graph instead. Boss-child spawn metadata is a derived position/ordinal cache:
refresh it before capture, including the first frame after subclass construction.
`TestS3kLrzBossRewindHeadless` covers arms, hit flashes and defeat debris through
explicit removal/recreation and whole-world forward replay.

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
**The bucket encoding differs per game and the engine defaults are silent.** S1/S2
store the bucket as a byte (`move.b #4,priority(a0)` is bucket 4); S3K stores the
display-list byte offset as a word (`move.w #$280,priority(a0)` is bucket 5, `$80` is
bucket 1, not a flag), and ObjDat/ObjDat3 tables carry the same word third. Transcribe
through `RenderPriority.bucket(n)` / `RenderPriority.fromS3kWord(word)`; `clamp` folds
a raw S3K word into bucket 7 without complaint (four CNZ/LRZ objects shipped that way).
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
**ROM palette-line names are one-based.** `sonic3k.constants.asm:767-770` declares
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

**Motion holders need stable identity in the default rewind path.** Declare
`SubpixelMotion.State` holders `final`; the default schema captures supported
in-place helpers only when their identity is fixed. LRZ's new boulder initially
used a replaceable holder, so snapshots matched immediately after recreation but
the next update moved riders from the constructor position. Comparing complete
world state after one forward frame caught it. Arrays use their own capture policy;
do not apply this rule to arrays indiscriminately. Origin: 2026-09-22 LRZ bring-up.

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

**Audio accuracy:** the FM core is the Nuked-OPN2 port (`audio.synth.nuked`); its only
reference is the pinned `ym3438.c`, and `Ym2612Chip` is engine glue over it. For the PSG
reference the libvgm cores, for the sequencer the SMPSPlay source, rather than simplified
versions. Diagnose against a source of truth instead of twiddling knobs.

**External audio clocks are not rewind frame numbers.** GameLoop's audio clock
continues through title/fade intervals while live rewind records gameplay only
and resets its origin on level loads. `recordExternalStep` must observe the
completed host clock, not overwrite it with the shorter rewind counter: a fade
completion can issue `InitAudio` commands before the next host tick and violate
timeline ordering. Keep the recorded coordinate mapping for seek/truncation,
prune it with history, and reroot it at load boundaries. Test internal/external
step changes, branching, and consecutive death reloads with live rewind enabled.
Origin: SOZ mixed/duplicate-team checkpoint validation, 2026-09-15.

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
(`sonic3k.asm:104978`, `103171`), which reads live `Camera_X_pos_copy` against
`Camera_X_pos_rounded` and writes VRAM inside the CPU loop, and
`AIZ2_DoShipLoop` retargets that baseline in the same routine as its `$200`
camera subtraction. The engine's AIZ2 foreground ring therefore takes the live
camera and the live `Level_repeat_offset` from the gameplay step
(`LevelForegroundPlane.drawAsYouMove`), never the published scroll
generation: pairing the published camera with the live offset trips the ring's
large-jump reseed when the wrap lands on a lag frame (2026-09-16 AIZ2 forest
regression). The `$200` wrap equals the 64-tile plane width, so a retained
scroll register still aliases onto the same ring cells.

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


### Resolve raw object offsets through the constants table

The SOZ rappel wire final swing reads `$46(a0)` in `loc_4AC98`, while its
initializer writes `parent3(a0)`. `sonic3k.constants.asm` defines `parent3 = $46`:
these are the same initialized endpoint reference. An earlier implementation
misread the numeric spelling as an unused pointer and forced immediate
retraction, then encoded that mistake as a unit expectation. Check aliases in
the owning constants table before claiming a shipped bug; follow both the
writer and reader. Test sustained behavior and release, not only arrival at a
routine. The SOZ methodology-v2 plan records the correction.

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
