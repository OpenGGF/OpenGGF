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

Independent parent review of milestone `44c1d674` found that moving the whole
controller also moved global palette work into the control-only hurt/death gate.
That candidate froze reversion palettes during hurt/death and released the final
transformation animation too early. `Level_MainLoop` runs `RunObjects` then
`PalCycle_Load` (s2.asm:5095-5106); `PalCycle_Load` calls `PalCycle_SuperSonic`
(s2.asm:2689-2690). The corrected ownership splits control/ring drain/reversion
from global fade/cycle/completion. The latter runs once in the world updater,
after objects and before zone palette work, including hurt/death. Derived-output
rewind refresh does not enter that updater. Package-private controller operations
and a non-ModApi bridge preserve the existing published signatures and captured
RAM. The final fade retains its reload of 3 (s2.asm:3129-3139), so the first
cycling entry follows four global calls; subsequent cycling reloads 7.

The next ARZ2 frontier is a separate existing semantic: ordinary balance chooses
four poses, but `SuperSonic_BalanceOnObjRight/Left` (s2.asm:36712-36729) always
faces the edge and selects animation 6, for both objects and terrain. At route
row 4920 all position, velocity and flags match; native animation 6 / mapping
`$C2` differs from the engine's animation `$1E` / mapping `$CF`. The existing
`SONIC_2_SUPER_SONIC.singleFacingBalance` field selects exactly this behavior;
only that constant changes. Other profiles and shared movement definitions stay
unchanged. REV01 animation 6 points through `$1B7C6` to `$1B82A`, beginning
`09 C2 C3 C4 C3 C5 C6 C7 C6 FF`.

## Entry and title ownership

### Transform-start effects

`Sonic_CheckGoSuper` writes Obj7E's ID at fixed `$D040` immediately (REV01
`$1AB6C = 11FC007ED040`), then dry constants, invincibility timer/status and
the transform SFX / Super music requests. Those effects now belong to that
start, rather than the later palette completion. An existing same-player Obj7E
at slot129 retains routine, mapping and timer state, matching the ID-only write.
Ordinary invincibility uses fixed136 / `$D200`, so that common path does not
require rewriting an arbitrary foreign SST. Native constants reserve129 for
Super stars and136–139 for Sonic invincibility; level RAM clearing includes
`$D040`. No shared allocator change or foreign-class SST reinterpretation lands.

The ordinary audio API uses engine playlist keys. Native request `$96` resolves
to engine `GameMusic.SUPER` / `$93`, whose actual loader pointer is `$FCBBC`.
Ordinary engine key `$96` resolves to Title at `$FD193`; passing raw `$96` to
that API was rejected after the audio lane verified the driver's namespace.
The existing semantic music request moves in time without changing its value,
and donor requests keep donor semantic resolution. The new test checks both
actual-ROM loader pointers and observes `SPECIAL_SFX:$DF` then `MUSIC:$93`.

Obj7E's idle cycle trigger also preserves its current animation timer:
`loc_1E188` writes frame0 and its active byte without resetting duration.
Newly zeroed stars therefore advance to frame1 on the following active call.
During the same-pass start, the already-published native control byte prevents
an idle trigger even before the movement dispatcher has applied its freeze.
Existing active animation and captured scalar state remain independent.

Focused eight-class validation on `8d410a09b` plus these effects and the
unwired native sprite assembler completed31 tests,3 new assertion failures,
0 skips. Two new assertions named ordinary SFX instead of the existing
special-channel diagnostic; another counted an offscreen piece after replay
had correctly charged its width and omitted its pixels. Production was
unchanged. The final two-class run
`--lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
-Dtest=TestSonic2SuperTransformationStartEffects,TestSonic2SpecialStageSpriteAssembly`
passed11/11,0 skips (Maven30.89s,queue0s); the other20 checks passed on the same
production source. Start-effects coverage includes failed eligibility,
exhausted general allocation, live ordinary stars at136, retained nonempty129,
world restoration and five forward ticks, with no restore-time audio replay.

One shared restore gap remains identified separately: timer-only
`refreshInvincibilityStarsAfterRewindRestore` can delete captured normal stars
after Super has cleared their timer. Retail Obj35 tests the secondary invincible
bit; its Super deletion is under `fixBugs=1` (s2.asm:42494-42503), which is not
the shipped branch. A bounded internal semantic restore hook is requested from
the shared owner; no unapproved workaround is included here.

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


The parent review correction and native Super balance change were validated with
`-Dtest=TestSonic2SuperGlobalPalettePhase,TestSonic2SuperBalance,TestSonic2SuperReversionAnimation,TestSonic2SuperSpeedConstantOwnership,TestSonic2DonorSuperStateRewind,TestSonic3kSuperStateRewind,TestSuperStateController,TestCrossGameSuperSonic,TestSpecialStageMappingArtTranslation,Sonic2SpecialStageDataLoaderTest,TestKis2SpecialStageDataLoader,TestModApiSignatureSurface`.
The first run completed 108 tests: 107 passed, one new last-fade test failed
because its grounded debug arrangement cleared forced animation before the
ordinary airborne transformation path. The final production code was unchanged;
the test now uses the native emerald/apex gate. Its next run passed the final
fade/control ordering and active-Super death cases, but two damage cases
correctly re-transformed on their still-eligible apex after debug reversion.
Those cases now use actual paused-HUD-timer reversion and assert the native
one-frame grace before applying real damage/death. The final narrow
`TestSonic2SuperGlobalPalettePhase` run passed all four cases, zero skips.
The other 104 cases passed on the same production source, including 33 independent
object/terrain balance, normal controls and world restore/forward cases, and all
11 Mod API signature checks. Final narrow Maven time 29.5 seconds, queue wait
4 seconds. A fresh `-Pguards` batch selecting
`TestPerGameRuleArchitectureGuard,TestRewindArchitectureGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault`
passed 43 checks, zero skips; Maven 35.5 seconds, queue wait 147 seconds.
These are focused results, not a green chain or broad smoke result.


The post-correction three-class trace command selected
`TestS2CompleteEmeraldRunChain,TestS2EhzHalfpipeRoundTripChain,TestS2Arz2LevelSelectTraceReplay`
with the same trace profile/single-fork/absolute-ROM/EGL flags. It completed
3 tests, 2 failures, zero skips: independent ARZ2 level-select passed; the
halfpipe retained its exact matched-baseline two gap axes, all edges65 early.
Full-chain segment18 remains complete/0 errors. Segment19 now has13495 errors,
first5084 sidekick_y native `$48A` versus engine `$489`; the row4920 balance
mismatch is closed. CNZ1 admission remains cursor113342 versus113340 and all12
structural gap axes remain. Maven45.47 seconds, queue wait35 seconds. Tested
source was `364088d93` plus staged phase/balance and unstaged mapping-loader
translation; no timing/fixture/hydration changes. The local policy hook rejected
that record initializer as a surface delta despite11 normalized-signature checks
passing; parent coordinates the existing policy correction rather than a pin
change or hook bypass.

## ARZ2 RNG producer evidence

After the phase/balance correction, the first chain error at ARZ2 row5084 is
Tails entering hurt while the native player keeps rolling. A temporary
observation-only chain hook on `e157bbadb` plus the mapping translation showed
the engine arrow at Y `$481`; the committed native observation places it at
`$461`. The engine's second arrow consumed seed `$F6EE848A` and produced
`$C64D3A1A`. The 55-row hook was removed after the failed probe (one test,
zero skips, Maven42.37s, queue281s); comparisons and gameplay were unchanged.

A separate read-only native capture played original BK2 SHA256
`e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5`
naturally from row0 through111839, with no save/setup/RAM writes. TraceChaser
pin `322851cbfa2e1c1803332142e11ab74b4eb34eea` was built in this isolated tree;
the stock GPGX core ran video/audio disabled and callbacks armed at row106753.
All478 RandomNumber entries at `$3390` paired with returns at `$33B4`.
The first arrow's native call at row111535 (returnPC `$30B0E`) is draw477:
seed `$F9BA0D1A` → `$17F6192A`, D0 `$F9BA17F6`, choice2 / Y `$4A1`.
The second at row111791 is draw478: seed `$17F6192A` → `$DE2407BA`,
D0 `$17F6DE24`, choice0 / Y `$461`. There are no intervening RNG calls.
The observed engine seeds are draws491/492 of that recurrence: fourteen extra
engine calls already precede the first arrow. This proves a producer-count
disagreement, not which producer is wrong; no RNG adjustment is justified.
Native/current Super stars contain no RandomNumber caller.

The external task capture is `native-gap-s2-rng-20261022/capture`, with paired
call TSV SHA256 `3ca8b861fe5503cc323ee16415235191b67278a945a20381304f0332b4ce8d92`.
The sparse `frames.tsv` slot19/20 columns accidentally used the S1 object base
and are excluded; the callback seed/PC/register/row columns are independent and
valid. No repeat capture was needed for the RNG objective. Committed trace aux
rows contain no RNG samples at these arrow boundaries, so the native capture
was necessary; nearest recurrence-pair fitting was rejected.
