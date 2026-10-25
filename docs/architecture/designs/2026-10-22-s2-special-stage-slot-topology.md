# Sonic 2 special-stage native slot topology — 2026-10-22

Implementation starts from `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
This delivery supplies the S2-owned live object identities and lifetimes required
by the separate H32 SAT renderer work. It does not establish exact renderer parity.

## Native ownership

`SpecialStage` (s2.asm:6622-6634) installs Sonic at slot 0, Tails at slot 1
(including Tails alone), HUD at 2, banner at 3 and player counters at 4.
Player initialization installs shadows at 5/6 and Tails' tails at 7.
`SSAllocateObject` / `SSAllocateObjectAfterCurrent` (s2.asm:9450-9483)
share slots 8..127, addresses `$B200..$CFC0`, and search from the bottom or
strictly above the executing owner respectively. Holes, rather than allocation
chronology, determine execution and same-bucket sprite order.

`SSObjectsManager` (s2.asm:6935-7001) checks free capacity before reading even a
marker and commits its stream pointer only at a marker. A full pool during a
segment can leave an already-created prefix while retaining the old stream
pointer. Rings/bombs still execute their initialization fallthrough on the
allocation-associated RunObjects pass; child initialization is not an extra pass.

The initial eleven rings-to-go owners precede the first RunObjects scan.
Optional total/VS counters follow when fixed slot 4 initializes. Hidden counters
and letters retain their slots. Banner pieces, checkpoint marker/rainbows,
wings/hand, message letters/digits, object shadows and spilled rings all occupy
the same bounded pool. Native partial-allocation failures are retained.

`Obj5B_Init` / `Obj5B_Main` (s2.asm:71383-71501) distinguish failure of the
initial spill allocation, which prevents a ring debit, from later child failures,
which do not undo it. The initializing spill does not move or display; later-slot
children can execute routine 2 immediately. Gravity combines ObjectMoveAndFall's
`$38` and the object's additional `$80`; nonnegative Y velocity promotes bucket 0.

`loc_3529C` / `loc_3533A` (s2.asm:71197-71307) allocate shadows after their
parent and retire them at the child's actual turn after the parent signals deletion.
Ring/bomb effects continue their full fixed-point depth lifetime; the animation
loops do not constitute deletion. Emerald shadow allocation follows its sixty-pass
initialization delay and preserves the final pass's approach fallthrough.

## Shipped branches and rewind

The reference is `fixBugs = 0`, corroborated against Sonic 2 World REV01:
CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.
Multi-sprite `sub3_y_pos` overlaps priority at `$18`: HUD/counter geometry and
rings-to-go `$0038` select bucket 0. The corrected DisplaySprite3 branch is not
used. Fixed-player shadow projection also preserves native word truncation.

Snapshots retain value-only slot generations, parent handles, dispatch state,
child flags/timers/positions, spill state and owner identities. Java owner
references never enter the pool. Restoring recreates objects and rebinds them to
the current pool. The immutable render query accounts for every live slot,
including invisible persistent owners, without allocating identities while drawing.

## Rejected approaches and validation boundary

Creation ordinals cannot represent reused holes. Reserving only visible sprites
misses hidden UI and allocation failures. Suppressing shadows in the renderer
cannot reproduce the common RAM topology. Those approaches were rejected from
native allocator/caller evidence before implementation.

Focused tests cover allocation order/holes/full RAM, startup/team topology,
UI children and timing, shadow allocation and geometry, spills, and restoration
followed by forward execution. Existing short streamed-object and rewind checks
remain part of the local validation selection. The parent owns the seven-stage
and complete-chain checks after composition; those are not claimed here.

## Observed focused verification

Java version probe reported Java 25.0.5. The original REV01 ROM remained at its
supplied absolute path; no ROM was copied or linked. Lua was `/usr/bin/lua5.4`.
A first compile passed. The first fifteen-class focused run completed 111 tests,
with four failures, zero errors and zero skips: three unchanged-state assertions
in `Sonic2SpecialStageBootstrapCadenceTest` exposed newly introduced array identity
in `IntroSnapshot.equals`; the fourth exposed the banner flight diagnostic
counter after child movement moved into native slot dispatch. Snapshots now use
immutable value lists and the diagnostic still advances on its existing parent
turn, while child movement remains exclusively in the address-ordered scan.
An affected seven-class run then passed 63 tests without errors or skips.

Independent review also corrected the spill's four-frame loop and immediate
animation reset at nonnegative vertical velocity, FC's twelve-turn lifetime
reset, and global-flag marker dispatch when a deferred FE precedes a newly
streamed FC. Dedicated short regressions cover each case. Number-owner BCD and
child count are captured values; remaining 101 deliberately becomes `$02F7`
with three children under the shipped subtraction branch. Streaming clears
message suppression before earlier owners run; successful hand expiry owns
the next UI requirement and trigger writes.

The final frozen-source check used `maven_queue.py --lean -Dmse=off test`, the
original absolute `sonic2.rom.path`, and this exact focused selector:

```text
TestSonic2SpecialStageNativeSlots,TestSonic2SpecialStageUiSlots,Sonic2SpecialStageBootstrapCadenceTest,TestSonic2SpecialStageCheckpointSnapshot,TestSonic2SpecialStageRewindSnapshot,TestSonic2SpecialStageRewindAdapter,Sonic2SpecialStageSpawnGateTest,Sonic2SpecialStageStreamedObjectCadenceTest,TestSonic2SpecialStageObjectSnapshot,TestSonic2SpecialStageSlotPool
```

It completed **92 tests, zero failures/errors/skips**, after a ten-second queue
wait and 86 seconds reported Maven execution including compilation. The unchanged
fixed-player presentation helper's eleven tests passed in the earlier focused
selection; its lookup tables were also compared byte-for-byte with REV01.
Whitespace and the modified skill mirrors pass their checks. New standalone
source/design citation checks reported no stale anchors; older citations in the
pre-existing manager/pitfall catalogue were not rewritten as unrelated work.
Consumed Surefire diagnostic files were removed after inspection.

Inherited limits remain explicit: production currently has no VS special-stage
mode flag or checkpoint winner/tie flow. Initial optional VS phrase/score-owner
topology is modeled; a full VS round is not certified. Existing player code also
uses fixed geometry offsets rather than implementing `SSSetGeometryOffsets`;
fixed-player render metadata reflects that current production position/offset
state. This local delivery changes neither that physics contract nor the
renderer, and runs no broad smoke or trace sweep.

## Counter-owner publication follow-up to 648e8c958

Review identified an omitted ownership boundary: fixed Obj87 slot 4 reads before
dynamic collection, while a total counter in a later slot can read the new value.
The original aggregate NUMBER state and draw-time `RingHudState` could not retain
that difference. `loc_7480` / `loc_74EA` (s2.asm:9851-9929) emit the player groups
in one owner, Sonic then Tails, each hundreds/tens/units without leading zeroes.
`loc_753E` (s2.asm:9933-10017) adds both cached digit bytes and carries once per
column; it does not clamp or reduce the hundreds mapping frame modulo ten.

Intro now captures immutable `NativeCounterPayload(handle, displayed, digits)`
values. Each `NativeCounterDigit(x, y, mappingFrame)` is already in native child
emission order. The fixed player owner latches on the existing recurring pass
between banner slot 3 and dynamic owners; startup still executes Obj87_Init's
non-displaying return. The total owner latches at its actual dynamic slot.
Manager supplies fresh semantic counter inputs before those turns, and render
queries return saved output without converting current counts. Payloads and the
pending immutable input are included in Intro's rewind snapshot.

This follow-up changes no renderer or physics files. Its independent regression
places a real collecting ring at slot 8, between player counter 4 and total
counter 20: player digits retain 9 while total digits show 10. Other focused
cases cover initialization, composition/order/centers, raw hundreds frame 10
for 999+1, immutability and restore followed by the same collection.

Validation on the unchanged follow-up source used Java 25, Lua 5.4 and the
original absolute REV01 ROM path (represented below by `S2_ROM_PATH`) with this
focused command:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonic2SpecialStageCounterSlots,TestSonic2SpecialStageNativeSlots,TestSonic2SpecialStageUiSlots,TestSonic2SpecialStageRewindSnapshot,TestSonic2SpecialStageRewindAdapter,Sonic2SpecialStageBootstrapCadenceTest' "-Dsonic2.rom.path=${S2_ROM_PATH}" test
```

It completed **63 tests, zero failures/errors/skips**, after a 167-second queue
wait and 76 seconds reported Maven execution including compilation. A bounded
read-only review checked the counter routines and snapshot ownership without
finding a concrete issue. The design's citation check, whitespace check and
skill mirror comparison pass. Intro's citation check still reports the unchanged
648e8c958 comment that names `RunObjects` beside its caller's lines 6660–6663;
this follow-up introduces no stale citation. No broad smoke or trace sweep ran;
the parent retains combined seven-stage/chain and renderer validation.
