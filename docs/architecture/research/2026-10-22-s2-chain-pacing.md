# Sonic 2 continuous chain and live special-stage timing — 2026-10-22

Base: `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`, isolated branch
`bugfix/ai-gap-s2-chain-pacing-20261022`. Parent owns integration and combined
smoke coverage. No ROM or comparison data becomes runtime gameplay state.

## Native player corrections

`Obj01_Control` (s2.asm:36248-36264) runs movement, `Sonic_Display`,
`Sonic_Super`, history, water, animation and touch in that order. The controller
formerly ran in `tickStatus` after animation/touch. A controller-owned internal
marker now selects its native control-body phase; other controllers retain their
existing phase. Invincibility expiry moves with this display phase, so the
`invincibility_time=1` written by reversion survives until the next display pass.

`Sonic_RevertToNormal` (s2.asm:37529-37546) writes `prev_anim=Run` before
animation. Original REV01 bytes at `$1AC04` are `117C 0001 001D`. This is an exact
sentinel: selected Roll restarts, selected Run preserves phase. The earlier
negative inference that rings=47 excludes reversion was false: `Sonic_Super`
(s2.asm:37506-37515) tests paused `Update_HUD_timer` BEFORE ring drain. Native
ARZ1 signpost state changes on row 4212 and normal Roll mapping `$3D` follows
at 4213 while rings remain 47.

`Sonic_CheckGoSuper` (s2.asm:37481-37483) unconditionally writes the dry
`$A00/$30/$100` speed bank; REV01 `$1AB72` contains
`31FC0A00F76031FC0030F76231FC0100F764`. `Sonic_Water`'s already-underwater
branch (s2.asm:36379-36382, `bset` at `$1A198`) does not overwrite those words.
The existing rewind-captured `waterPhysicsActive` speed latch now records that
native writer, independently of in-water status, gravity and jump force. Native
water entry/exit and reversion select the following bank. This explains the
ARZ2 second transformation's `$30` acceleration deficit without a frame exception.
The old sibling bubble patch `0d8905611` is already subsumed by base
`431a8f7f4`; it was not imported.

## Entry and title ownership

Ordinary `Sonic2SpecialStageProvider.initializeStage` now arms the existing live
104-presentation load hold. `TRACE_ACCURATE` leaves it unarmed because recorded
row admission owns that span. The hold follows the 22 fade-to-white waits and
precedes track/controller/player work and palette publication. Its lifecycle
selects `LAG`, not the special-stage DMA/DPLC handler. Snapshot restoration
preserves its remaining count.

104 is measured NTSC native evidence across the seven shared-art/layout loads,
not a ROM literal or a completed cycle model. The old explanation that the
entire span masks interrupts was wrong: `SpecialStage` restores SR=$2300 at
s2.asm:6613 before synchronous loaders, with `Vint_routine=0` until :6644.
Those interrupts take `Vint_Lag`. PAL cost closure remains unproved.

`Level_TtlCard` spends another `VintID_TitleCard` wait after its successful
position/PLC test and before `Hud_Base` (s2.asm:4925-4927). REV01 requests it
at `$4104`, calls `WaitForVint` at `$410A`, `Hud_Base` at `$410E`, and only later
`LoadZoneTiles` at `$413C`. An isolated captured `HUD_BASE_WAIT` phase was tested
and withheld: it fixes later one-row art edges, but the seventh return then misses
its destination admission while still in TITLE_CARD. The coupled load/release
accounting must be corrected before this native fence can land.

## Return gap: rejected blanket delay and remaining work

The baseline first return has two different errors: initial mapping-1 submissions
are 40 rows early, but later tail frames are only one row early. A blanket
40-frame return delay would corrupt correct later work and was rejected.

Its admission census starts at movie row 10161 with runs `[23,11,53,14,8,39,25]`:
eight native zone-tile waits occupy 10262–10269, synchronous post-DMA load work
occupies 10270–10308, the leading `RunObjects` pass belongs to terminal row 10308,
and 25 leave-loop waits occupy 10309–10333. The missing HUD fence accounts for
one row. The remaining load needs actual codec/VDP work accounting, not the
observed 39 as a constant or a copied bootstrap art hold.

The existing inventory classifies these synchronous routines as lag, not a
polled hardware readiness kind. A future source-derived ledger can use
`LevelInitProfile.serviceLevelLoadVBlank` (also reached on suppressed lag VInts),
with module ownership, rewind and cancellation. It must cost actual
`loadZoneBlockMaps`, animated patches, `DrawInitialBG`, collision streams,
water/player setup and device work. `ConvertCollisionArray` immediately returns
in retail S2 and must not be priced as executed work. No shared timing-port or
run-harness change is included in this milestone.

Ordinary recurring special-stage pacing still uses the existing empirical
speed/segment buckets. An accurate replacement must price the current ROM
mapping slice, persistent decode registers, VInt DMA/DPLC, actual object/sprite
work, H32 FIFO and interrupt phase, carrying unfinished clocks through lag.
A trace pass cannot certify that ordinary path. The present work does not claim
that replacement or full chain completion.

## H32 SAT dependency and native topology inventory

The shared sprite worker owns the H32 evaluator and internal access contract;
this lane owns its ordinary S2 special-stage consumer. Capacity is the fixed
native inner H32 raster (256 pixels, 16 active pieces / 256 fetched pixels per
line, 64 entries total), independent of the outer viewport. Submit whole mapping
pieces, preserving native width/height and mapping order before renderer tile
fragmentation. Transparent/offscreen pieces still consume native capacity.

Native ordering is priority bucket, then object execution/enqueue order, then
mapping-piece order. `DisplaySprite` appends within eight queues
(s2.asm:30358-30374); `BuildSprites` drains buckets 0 through 7
(s2.asm:30523-30660), with `RunObjects` scanning slots 0 through 127. Fixed SS
slots are 0 Sonic, 1 Tails (including Tails alone), 2 HUD5E, 3 banner5F,
4 number87, 5/6 player shadows and 7 Obj88. Dynamic slots 8–127 form one pool.
`SSAllocateObject` takes the lowest free slot; `SSAllocateObjectAfterCurrent`
searches after its caller (s2.asm:9450-9483). Reused slots make creation order
insufficient.

| Native owner | Priority bucket |
| --- | --- |
| HUD, Obj87 digits, checkpoint hand | 0 |
| Rings-to-go multi-digit owner | 0, shipped field-overlap bug |
| Banner/flyout children, ordinary text/checkpoint digits/wings | 1 |
| Obj88 tail | Parent bucket minus one |
| Player body | 3 below depth $77, otherwise 2 |
| Normal ring/bomb | 3; collected/exploding switches to 1 below depth 4 |
| Player shadow, emerald | 4 |
| Dynamic shadow, rainbow ring | 5 |
| Spill ring | 5 ascending; 0 once vertical velocity is nonnegative |

The current renderer incorrectly uses body priority plus one for Obj88. Native
`Obj10_Init` and Obj88 explicitly subtract one (s2.asm:70520-70522,
70671-70673). Native `priority=$18` also aliases `sub3_y_pos`
(s2.constants.asm:23,99): retail HUD/counter geometry leaves bucket zero, and
rings-to-go `$0038` overwrites its intended bucket one. Preserve `fixBugs=0`;
`DisplaySprite3` is the optional correction, not shipped behavior.

Direct ROM mapping counts: Sonic's 18 frames have
`3,3,3,3,4,5,5,5,4,5,5,6,3,3,3,3,4,4` pieces; Tails has
`3,4,4,5,3,4,5,4,4,5,5,4,2,3,2,3,2,2`; each tail/shadow frame has one.
HUD frames have `4,4,4,2`, banner seven, digits one, wings eight, hand four;
normal rings/bombs one, final ring sparkles two, bomb explosion frames two/three.
The Java mapping types retain whole geometry before current tile loops.

Exact tie ordering still needs one rewindable S2-local pool shared by streamed
objects and folded UI children/shadows, including their allocation/deletion
lifetimes. Existing ring/bomb lists, intro/checkpoint letters, HUD synthesis and
shadow calls have no shared slot identity. A ring-only slot field cannot certify
native topology. No common ObjectManager change is required.

## Verification

All Maven tests use `tools/testing/maven_queue.py`, Java 25, Lua 5.4, absolute
original ROM paths, unset DISPLAY/WAYLAND_DISPLAY and surfaceless EGL where
rendering is required. No broad suite belongs to this worker.

- Preflight: `run_categories.py --base 94c40570a --preflight` passed; no tests run.
- Baseline `-Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
  -Dtest=TestS2CompleteEmeraldRunChain test`: 1 failure, no skips; 14 axes:
  12 gap disagreements, segment 18 has 119 errors first row 4213 mapping 61/65,
  segment 19 first row 2175 `x_sub`, then ownership lost at row 3863 / BK2 110617.
  Maven elapsed 97 seconds including fresh compilation, test 14.30 seconds;
  measured queue wait 5 seconds. These are overlapping components, not summed
  task elapsed time.
- Initial 12-class focused run: 67 tests passed, no skips, before the subsequent
  invincibility-phase fix and strengthened assertion. Startup policy regression
  was observed red first (4 failures / 11 cases), then green with production wiring.
- Final affected Super/control/rewind selectors
  `TestSonic2SuperReversionAnimation,TestSonic2SuperSpeedConstantOwnership,TestSonic2DonorSuperStateRewind,TestSonic3kSuperStateRewind,TestSuperStateController,TestCrossGameSuperSonic`:
  39 passed, no failures/errors/skips, after display-phase invincibility expiry
  and strengthened real-frame assertions. Includes the S2 controller on an S3K
  host and native S3K controller controls. Maven 87 seconds including compilation,
  measured queue wait 309 seconds; no wall-clock gameplay timing is used.
- Chain/standalone-stage/other-movie and affected guard results follow when complete.

The matched baseline halfpipe chain at exact base Java source reproduced two
structural-gap failure axes: all eight return edges were 65 rows early on each
return. The isolated HUD fence moved those to 64 early without changing physics.
The temporary source backup was consumed and deleted after candidate restoration;
no second checkout, branch switch or copied build tree was used.

Affected source guards ran separately with `-Pguards
-Dtest=TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault`:
36 passed, no failures/errors/skips. The unchanged timing port remains the only
recorded readiness authority; no physics/aux hydration was introduced.

The 10-class trace run with the isolated HUD fence passed all seven standalone
stages and the independent `TestS2Arz2LevelSelectTraceReplay`, with no skips.
The complete chain failed on seven axes, stopping earlier at the seventh return
(destination movie row 101691, still TITLE_CARD); this is why that isolated
change was withheld. Other ordinary level-gap one-row art errors cleared, while
initial return pairs remained 39/37/35 rows early. The other-movie halfpipe
retained its two attributed structural-gap axes (64 early with the isolated
fence, baseline65). This incomplete candidate was not committed.

The qualified milestone excludes the isolated HUD fence. With native Super and
entry fixes applied, `-Ptrace-replay -Dsurefire.forkCount=1
-Dsurefire.reuseForks=true
-Dtest=TestS2CompleteEmeraldRunChain,TestS2EhzHalfpipeRoundTripChain` completed
2 tests, both failing, no skips. Full-chain segment18 is now complete with ZERO
errors (baseline119, first4213 mapping61/65). Segment19 now completes; its first
error moved from2175 x_sub to4920 dynamic_art mapping194/207, with14546 errors
versus baseline47450 incomplete. The walk reaches the CNZ1 handoff, then rejects
cursor113342 versus destination113340. The12 structural-gap failure axes remain.
The independent halfpipe chain exactly retains its matched baseline's two
65-row-early gap axes; no new failure identity was introduced there. This is
bounded frontier progress, not a green chain. Maven100s, measured queue149s.
