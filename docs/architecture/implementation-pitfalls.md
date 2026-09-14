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

**Rewind coverage is guarded.** A new spawnable object without a recreate path, an
uncaptured `final` scalar, or an object reference not captured as a rewind id fails
`TestRewindCoverageGuard`. A global static manager consumed across frames but unregistered
fails `TestStaticStateRewindCoverageGuard` — fix it with a `RewindSnapshottable` adapter,
not a baseline entry, unless the gap is genuinely intentional.

**Managed children need identity relinking.** `RewindStateful` represents captured
helper values; applying it to a live managed child can make an owner's captured
collection retain stale objects after recreation. Use object scalar capture and
identity relinking for those children. Exercise remove/recreate/restore with the
real manager, including any optional defeat controller that advances child cleanup.

**ROM sprite priority buckets are SAT order.** Lower priority buckets and earlier object
slots appear in front; painter rendering reverses both orders. Folded boss parts
can have independent buckets even when attached to the same rocket. Compare native
pixels against ROM table-driven composition, rather than testing a numeric
"front" flag against the same assumption used by the implementation.
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
