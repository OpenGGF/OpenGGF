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
transformation animation too early. `Level_MainLoop` (s2.asm:5095-5106) runs
objects then palette work; `PalCycle_Load` (s2.asm:2689-2690) calls
`PalCycle_SuperSonic`. The corrected ownership splits control/ring drain/reversion
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

The subsequently approved restore correction keeps captured normal stars after
Super has cleared their timer. Retail Obj35 tests the secondary invincible bit;
its Super deletion is under `fixBugs=1` (s2.asm:42494-42503), which is not the
shipped branch. An internal marker selects that lifetime only for the S2
controller while Super. The private playable refresh rebinds an exact live Obj35
or restores the matching captured pending entry through a non-ModApi bridge.
Destructive manager restoration deliberately recreates player-bound stars later,
so a live-only search would lose the graph. The bridge requires matching owner
identity and stock concrete class, retains captured identity/scalars/slot/callback
ownership, and never invokes a spawner for an absent entry. Unsupported spawners,
overridden factories and foreign live SSTs leave the pending entry untouched;
construction failure restores it exactly once. No ObjectManager, allocator,
published API or capture shape changes.

The combined nine-class queued command selecting the expanded start-effects
class, native sprite assembly, S2 donor/S3K/generic Super controls, playable
capture, normal Obj35 and Mod API signatures passed78/78, zero skips
(Maven94s,queue0s), on `f85bb8746` plus these changes. The ten start-effects
cases now include destructive136→129 restoration and forward replay, another
owner's pending entry, truly missing owner despite a stale pointer, unsupported
factory/spawner and construction-failure retry. Seven assembly cases include
the native63-owner bucket cap and asymmetric both-axis flips. The assembler
remains unwired pending the qualified shadow backdrop contract.
Fresh affected `-Pguards -Dtest=TestPerGameRuleArchitectureGuard,TestRewindArchitectureGuard`
passed7/7, zero skips (Maven30.59s,queue65s), on the same production source.

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

`Level_TtlCard` (s2.asm:4925-4927) spends another title-card VInt wait after
its successful position/PLC test and before the HUD load. REV01 requests it
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
`Obj10_Init` (s2.asm:70520-70522) and `Obj88_Init`
(s2.asm:70671-70673) explicitly subtract one. Native `priority=$18` also aliases `sub3_y_pos`
(s2.constants.asm:23,99): retail HUD/counter geometry leaves bucket zero, and
rings-to-go `$0038` overwrites its intended bucket one. Preserve `fixBugs=0`;
`DisplaySprite3` is the optional correction, not shipped behavior.

Direct ROM mapping counts: Sonic's 18 frames have
`3,3,3,3,4,5,5,5,4,5,5,6,3,3,3,3,4,4` pieces; Tails has
`3,4,4,5,3,4,5,4,4,5,5,4,2,3,2,3,2,2`; each tail/shadow frame has one.
HUD frames have `4,4,4,2`, banner seven, digits one, wings eight, hand four;
normal rings/bombs one, final ring sparkles two, bomb explosion frames two/three.
The Java mapping types retain whole geometry before current tile loops.

The ROM-backed data loader now translates each player mapping destination through
its DPLC requests independently. A whole mapping piece can cross disjoint source
requests; its render fragments do not become separate native SAT entries. The
lock-on loader retains its six-byte whole geometry alongside its existing source
fragments. An independent logical-ROM example uses one flipped four-tile piece
spanning sources 3–4 and 10–11, requiring display order 11,10,4,3 and one native
footprint. The focused mapping/loader/lock-on tests passed on the production
source included in the 108-case run below. H32 admission and native owner ordering
remain separate consumer work; this translation alone does not certify them.

The rewindable shared pool is now integrated as `c78c0287b`, followed by
own-turn counter publication in `8d410a09b`. The pure
`Sonic2SpecialStageSpriteAssembly` consumes those captured owner values in
bucket/slot/mapping-piece order. It enforces the 63-owner limit of each
128-byte DisplaySprite queue before mapping expansion, including owners that
emit no pieces. Each whole piece keeps its own scattered ROM art fragments;
captured counter payloads cannot be recomputed from current ring totals.
Retail 101-remaining BCD `$02F7` indexes the actual digit-$F table word: the
signed count-minus-one is negative, so it emits no pieces rather than clamping
to a valid digit. Seven independent original-ROM assembly tests passed in the
78-case focused run on `0f595ef88` plus this assembly source, with zero skips.
They cover asymmetric flips, ownership/order, the per-bucket owner cap, fixed
H32 limits across outer widths, native HUD footprints and malformed BCD.

This assembly is deliberately not yet called by the renderer. Production
wiring awaits the qualified SHADOW_15 companion contract: normal low-plane
terrain and the operator companion need native intensity before display-profile
conversion. Multiplying final RGB by one half is rejected, and existing
next-cursor track accessors and fixed player projection offsets are separate
geometry gaps. The pure assembly checks do not certify native rendered pixels.

## Native curve projection and palette input

`SSSetGeometryOffsets` (s2.asm:10076-10099) writes its two global words only
when the drawing-index byte equals the player-animation timer. The production
loader now reads all 112 bytes of `SSCurveOffsets` (s2.asm:10107-10124) from
REV01 `$768A`, with no reference fallback. X is negated as a byte before word
sign extension on flipped tracks. Frame `$04` gives `(137,44)` or `(119,44)`,
`$09` gives `(128,12)`, and `$37` gives `(131,54)` or `(125,54)`.
The original-ROM table SHA256 is
`ae2b09025a2d0c64196727d9d72ea70ed71357eca2666159bbeb0563d2172a3d`.

The animation cursor was already advanced when the old frame getter read it.
`SSTrack_Draw` (s2.asm:7041-7053) stores the displayed mapping byte before
advancing that cursor. A separate captured byte and orientation latch now supply
the geometry service; existing legacy frame/orientation consumers remain
unchanged pending their own proof. In particular, displayed Drop `$19` must not
adopt the next cursor's `$1A` orientation trigger. The previous deferred object
turn finishes with its prepared offsets before the next pair is written. Startup
and fade drawing loops retain `(128,54)`. Both player bodies and fixed Obj63
shadows use the shared owner words; dynamic shadows retain their perspective
bases. These native registers, rather than derived render output, enter rewind
snapshots. Fresh entry resets them.

S2 special-stage CRAM decoding also now uses the shared canonical encoding
`round(255*n/7)`. Its former `36*n` lost native channel identity (level 2 became
72 rather than73; white252 rather than255). Native intensity must precede the
display profile/output conversion, so recovering intensity from final RGB is
rejected. An independent literal eight-level ramp covers all512 CRAM colours and
unused bits; the qualified shared native-intensity implementation remains a
separate prerequisite for production shadow presentation, now imported in
`91f3fd4bb` from the approved original leaves.

The ordinary stage and emerald palette writers now read through the injected
DataLoader ROM, bypassing the legacy public utility's process-global ROM cache
and grey substitute. Missing/short reads fail closed. Independent supplied-ROM
views with different stage and emerald words verify owner/cache isolation.
Public utility signatures remain compatible; this closes the ordinary runtime
callers, not every external use of those legacy helpers.

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

## Reusable native CPU component probes

`tools/timing/s2_load_costs.py` executes selected actual REV01 instructions in
private RAM, deriving Kosinski, ring-sort, initial-background, animated-block and
setup CPU costs from ROM tables. Its EHZ1 example is 4,734,984 CPU clocks /
36.990411 fractional NTSC frame equivalents. This is a partial CPU envelope,
not an elapsed load duration: caller/object work, interrupts, per-burst VDP
FIFO/DMA stalls, refresh, Z80 arbitration and starting phase remain unresolved.
ARZ's block decode writes 256 bytes past the nominal block buffer into adjacent
scratch RAM; the probe preserves that native spill instead of clipping it.

`tools/timing/s2_track_costs.py` executes the native `$5604` track entry across
its four persistent slices. It preserves the three ROM cursors and six saved
decoder words, records ordered word-width VDP/PNT writes and checks every raw
128-word row against an independent MSB-first format reader. Flipped slices
write private PNT RAM; the following VInt's DMA is explicitly outside the probe.
The reference reader deliberately does not share the Java display-strip
permutation. Mapping 0's ordinary CPU slice costs are 51450,82262,82678,63158;
the flipped/alternate case costs 52438,83902,84354,64374. Write timestamps are
instruction-start CPU prefixes, not bus-cycle timestamps. Other main-loop work,
interrupts and device stalls still prevent complete frame closure. Neither tool
is consumed by runtime or supplies readiness/gameplay authority.

Original-ROM Python checks ran with `SONIC2_ROM=<absolute original REV01 path>`:
`python3 -B -m unittest discover -s tools/timing -p test_s2_load_costs.py -v`
passed 16 tests, zero skips (6.98 seconds); the corresponding
`-p test_s2_track_costs.py -v -f` command passed 14 tests, zero skips
(89.09 seconds). The latter covers 224 mapping/orientation/PNT combinations,
896 persistent native calls and suffix replay from 672 saved boundaries.
Synthetic instruction/flag/cost, malformed-stream, unsupported-path and
nontermination controls fail closed. These component results do not replace
the still-open ordinary stage pacing and return-admission obligations.

The approved temporary GameRng caller inventory on `0f595ef88` plus the assembly
source ran one actual chain, with one failure/zero skips, unchanged14 axes and
the same ARZ2 row5084 frontier (queue200s, Maven97s). The probe was then removed;
the consumed XML and chain diagnostics were deleted. Pre-arrow native/engine
producer totals were: countdown69/69, mouth bubbles110/108, numbers8/6,
generator burst23/22, generator timers65/70, offsets65/70, child init65/70,
large-bubble selection8/8, burst-end14/14, leaves44/48 and animals5/5.
The first21 calls match in producer order. Calls22/23 swap a child init and mouth
bubble, but both raw low-byte/nibble outputs coincide, so this is not causal
proof. Producer order diverges materially at137: native generator timer/offset
then child initialization precede a fixed countdown, while the engine observes
the countdown and a new burst first. The inventory lacks engine frame/slot/owner
identity, so assigning those later generator calls to specific native owners
would be unsupported. The fixed countdown already runs after dynamic execution;
the first swap does not establish an early sidecar phase.

An independent concrete defect was found in `Obj2C_CreateLeaves`
(s2.asm:52127-52138): a low-speed touch clears `objoff_2E`, but its caller still
reseeds the phase from `Level_frame_counter`. REV01 `$26188` calls `$261C8`;
`$26190` writes the level-clock word after return, including the slow branch
through `$261C2` (`4268002E4E75`). The engine's early return omitted that reseed,
allowing a later fast touch to spawn immediately. The correction preserves the
native word/zero sentinel and injected level clock, with independent primary,
secondary, pause, wrap and destructive world-restore/forward controls. The four
extra leaf calls are consistent with one extra batch; neither that count nor the
source fix proves it is the earliest cause of the arrow divergence.

The native geometry/palette/leaf focused batch selected15 classes on
`91f3fd4bb` plus this source:120 tests,119 passed,one new rewind-oracle failure,
zero skips (queue30s, Maven97s). The first oracle compared the custom snapshot
by identity; its stronger replacement exposed a test fixture that had never
bound Intro to the native slot pool, although production setup always does.
Only the test was corrected to perform native binding/child initialization.
The final20-case geometry run passed with zero failures/errors/skips
(queue16s, Maven31.03s); it compares every captured manager field, immediate
restore,24-forward replay and native owner render outputs. All100 other cases
had passed on unchanged production source, including the five leaf regressions,
three canonical/injected-ROM palette checks and11 API checks. The seven native
stages, chain/second-movie, renderer pixels and recurring timing remain separate
obligations; these focused checks do not close them.

The required10-class trace check on committed `9854f02b1` completed8 passes and
2 failures, zero errors/skips (queue331s, Maven53.088s). All seven native SS
regressions and the independent ARZ2 movie pass. Main chain14 axes and second
movie two65-row-early gap axes are unchanged; ARZ1 remains0 errors, ARZ2 remains
13495 errors first5084 sidekick_y `$048A/$0489`, CNZ1 admission remains+2.
This is kill evidence against treating the correct leaf reseed as closure of
the observed arrow discrepancy. No further seed/producer compensation is
justified. The temporary inventories and consumed reports were removed.

The approved second observation on `5395c6fde` added only read-only producer,
current execution-slot and live-clock labels to `GameRng.nextRaw` and the fixed
countdown controller's existing execution boundary. Missing object contexts
were explicitly `UNKNOWN`, including the fixed Sonic/Tails controllers; inert
solid contexts were not filtered. One existing chain completed with the same14
failing axes, zero skips (session24942, queue18s, Maven97s). Both temporary
hunks were reversed exactly and the two-file diff verified empty before any
commit. Consumed reports were deleted; no second native capture was taken.

The first slot discrepancy is now attributable: native dynamic child34 survives
through generator33's next allocation of child35. The engine instead unloads
child34 through the manager's generic coarse range check and reuses34. At draw22
the engine's fixed Sonic controller runs at level456/VInt106840, followed by
child29 at457/106841; native child35 precedes the fixed controller. Native
`loc_1F988` (s2.asm:45263-45267) uses ObjectMove, the previous rendered on-screen
bit and DisplaySprite, without a coarse range check. The child's native
surface/pop routines also retain their own deletion turn. Transition persistence
is a separate contract, so overriding the existing custom range predicate is
preferable to making these children persistent across transitions.

Native draws137/138 belong to generator `$BB00`/slot44 at movie107986; the
committed row-end clocks are level1230 and VInt low word `$A47C`. These are
row-end samples, not reconstructed call-time clocks. The engine instead reaches
the fixed Sonic countdown/mouth at level1276/VInt107664. Native `loc_1FACE`
(s2.asm:45375-45386) tests coarse range after the generator's timer/allocation
work; the Java generator tests range before that work. The native inactive
water/on-screen gates jump to the same tail, while an active burst bypasses
those gates. Native `loc_1FA2A` (s2.asm:45329-45339) also draws the timer before
allocation and the X offset only after successful allocation. These are
source-backed lifecycle hypotheses with independent regression obligations;
the observation alone does not certify the final arrow or whole chain.


The bounded Obj24 correction now uses the existing custom range contract for
child and generator lifetimes. It loads all seven animation scripts, wobble and
burst-type bytes through the injected ROM reader. Inflation's `$FC` enters
routine4 without further animation; pop's `$FC` displays its last frame on the
routine8 transition and frees the slot on the following own pass. Inhaled large
bubbles retain their pop presentation through frame7. Generator animation uses
its own byte timer, active bursts bypass idle water/render gates, and every
branch reaches its native range tail. The existing reserved-slot constructor
executes X/chance writes only after allocation succeeds, while timer and
last-bubble delay remain on their native sides of allocation. No shared
allocator, RNG recurrence, caller timing or comparison authority changes.

New independent actual-ROM controls first required two fixture corrections:
the slot-control helper needed its no-op render method, and the water lookup
needed ROM zone `$0F` while the load API correctly retained logical index2. The
queued request with an invalid proposed load index was cancelled before Maven
execution. Corrected old-production session42981 executed20 cases:3 passed,
12 assertion failures and5 missing-native-state errors, zero skips
(queue56s, Maven30.804s). It independently detects premature slot release,
missing active-water/final out-of-range spawns, extra full-pool RNG draws,
immediate surface deletion, and VInt-driven generator animation. The ROM
opcode/script anchors pass. These are test results against old production,
not a passing correction or new chain measurement.

Player contact retains inherited limitations: only the existing primary path is
visited, and the native object-control guard, both-player dispatch, movement
lock/velocity/rolling adjustments and exact resume-music behavior remain
separate work. The component rewind regression uses real ObjectManager and
GameRng adapters over an empty S2 graph attached to an actual-ROM headless world;
its destructive reconstruction/24-turn comparison does not certify the whole
session or ordinary special-stage timing.


Focused corrected source at `d53e035d3` plus the two Obj24 files and tests passed
46/46, zero failures/errors/skips (session69339, queue38s, Maven93s), Java25.0.5
and the original absolute S2 ROM. Exact selector:
`TestSonic2BubbleNativeLifecycle,TestBubbleObjectInstance,TestSonic2Rng,TestS2SelfContainedTransientRewind,TestRewindFixS2Batch4Codecs,TestModApiSignatureSurface,TestArchitecturalSourceGuard#migratedObjectChildSpawnsStayOnManagedHelpers`,
with `--lean -Dmse=off -Dsonic2.rom.path=<original absolute REV01 path> test`.
The20 new cases include native opcode/asset anchors, slot reuse, pop/frame7,
active/idle/range/allocation branches, wrong/missing-reader controls and
destructive component restoration plus24 forward turns. Existing whole-session
transient and player-bound restoration cases also pass. New pitfall citations
add no stale references;37 historical stale references in the existing mirrored
catalogue are identical to its baseline. Chain and second-movie bounds remain
the last measured9854 result until a new completed trace run is recorded.


Fresh affected `-Pguards
-Dtest=TestRewindArchitectureGuard,TestRewindFieldDispositionGuard` passed5/5,
zero failures/errors/skips (session74227, queue9s, Maven29.805s), on the same
functional source. A stale inherited loc_1F90A citation in the touched child was
corrected afterwards; no executable code changed. The touched production and
research citation check then has no stale finding. Consumed reports were removed.

The next completed route measurement is session21181 on clean committed
`d489be60add071e398da2eb8261c9ca58832b8f4`, in `t3-dcf07b84`:
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/maven_queue.py -Dmse=off -Ptrace-replay
-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
-Dopenggf.headless.gl=surfaceless-egl
-Dtest=TestS2CompleteEmeraldRunChain,TestS2Arz2LevelSelectTraceReplay
-Dsonic2.rom.path=<original absolute REV01 path> test`, using Java25.0.5.
Two tests completed: independent ARZ2 passed, the emerald chain failed,
zero errors/skips (queue18s, Maven approximately97s). The chain retains all14
failing axes: ARZ1 is exact, ARZ2 completes with13495 errors starting at row5084
`sidekick_y` expected `$048A`, actual `$0489`; the CNZ1 handoff is two frames
late, and12 return/art-gap axes retain the first40-frame and later36–40-frame
early edges plus one-frame gaps. This is unchanged from the preceding9854
measurement. The Obj24 correction passes its independent native regressions,
but it does not close this route frontier. No post-correction RNG inventory
was taken, so the earlier14 extra engine draws are not a newly measured count.
Consumed Surefire and trace reports were inspected and removed; no duplicate
route job, native capture or timing authority was added.


## Native H32 presentation integration

The ordinary stage now submits the captured live-owner values through the native
whole-piece assembly, ordered by DisplaySprite bucket, ascending slot and native
mapping-piece order. The internal H32 raster remains256 pixels wide with
16 vertically active pieces,256 fetched pixels and64 table entries regardless
of the outer viewport. Captured counter publications remain inputs; drawing
never creates slots or recalculates saved counter digits from current rings.
The alignment preview retains its separate diagnostic painter.

`SpecialStage` (s2.asm:6564-6566) writes `$8C08` (H32, shadow/highlight enabled)
and `$8700` (backdrop CRAM0/0). Normal terrain selects native shadow intensity
for low-priority tiles and normal intensity for high-priority tiles. The
companion repeats the same background scroll, two-line track projection and
H32 placement using all-shadow derived palette lines. The indexed SHADOW_15
operation samples that companion inside native SAT ownership; multiplying or
inverting final display RGB is rejected. Source-line fade precedes native
intensity and output/profile conversion. The opaque native backdrop uses the
same resident derived CRAM0 recipe even where mapped art is transparent.

Background construction uses a256-high projection scope to bake pattern/batch
height. Retained BEGIN/END commands reopen the matching execution scope while
queued commands read their projection matrix, then close it after the batch.
Those retained commands do not flush unrelated pending CPU batches. The
projected companion uses its own224-high logical target and the main physical
viewport descriptor. Cache keys include ROM mappings, contributing source
palette content, displayed fade, profile/expansion and stage/context generation.
All banks, commands and FBOs are derived owners, never GPU identities in rewind.
Failed restoration/deletion remains owned for a legal creating-context retry.

The stage publishes normal/no-water rendering before construction and again
before execution, including alignment preview. This follows H-INT disable
`SpecialStage` (s2.asm:6560) and normal CRAM upload `Vint_S2SS` (s2.asm:843),
without changing retained level water physics, palettes, players or controller
state. A previously queued level water publication cannot select the stage's
shader or underwater palette.

First integrated selection23062 compiled and executed64 tests:50 passes,
2 failures,12 errors,0 skips (queue356s, Maven90s, Java25.0.5). Background
projection10, bootstrap23 and snapshot5 cases passed, as did the320-wide live
production entry/draw/input/restore/water case. All11 terrain-fixture errors and
the528-wide live error expose an actual empty-owner cleanup bug: first viewport
configuration attempted to capture/restore GL state before a creating context
existed. Empty cleanup now returns before GL access. The separate reviewed
headless/replacement-context cleanup hole now fails closed with resources and
pending restoration retained. The two deterministic-cache assertion failures
came from a recorder emitting a mapping command for every empty flush; it now
models production's nonempty-only emission while retaining exact event-order
assertions. These fixes require their affected rerun; the initial50 passes do
not certify the repaired source.

The three shadow families' actual0/15 proof does not establish absence of
operators in other effective palette3 fragments. A bounded injected-ROM,
actually mapped ordinary-fragment inventory remains pending. No generic
shadow/highlight, native screenshot parity, new chain frontier or timing closure
is claimed from the component/live checks.


Affected rerun92349 completed48 tests:46 passed,2 failed,0 errors/skips
(queue91s, Maven99s), original absolute S2 ROM and Java25.0.5 verified in XML.
Both320/528 actual production cases now pass, including natural ordinary entry,
independent held/jump input, repeated/direct drawing, captured provider/control
state, restoration and73-row forward replay without intermediate drawing, plus
contrasting resident underwater texture/shader controls. Thirteen terrain cases,
seven native assembly cases, three injected-ROM palette cases and11 API checks
also pass. The two failures are test-only: the all-empty mapping case retained
an unconditional fake batch expectation, and static Mockito setup was counted
by the new no-GL cleanup verification. Empty mappings now require the target
clear BEGIN/END with no tile batch, and no-capability verification begins after
stubbing. Production is unchanged after this run; those two assertions and the
new effective-palette inventory still require a completed affected check.


Combined affected check86471 completed33 tests:32 passed, one failed,
zero errors/skips (queue59s, Maven33.248s). Terrain14 and deterministic-cache11
are now exact passes on unchanged production. The new inventory failed closed
on an unresolved MESSAGE frame12, map ROM `$35EC2`, raw tile `$823D`:
SpecialMessages `$1A2` plus tile `$23D` selects `$3DF`, the separately loaded
VS-player text art bank. This is not an observed14/15 pixel. The inventory had
mistakenly treated every mapping-table frame as ordinary reachable output.
Native `SSMessage_TextFrames` (s2.asm:72201-72254) and
`SSMessage_TextPhrases` (s2.asm:72374-72396), with ordinary ring/checkpoint
callers, select GET plus phrase offsets2/4/6. The corrected test walks those
actual ROM word/phrase pointers and asserts the resulting ordinary message
frames0–11/17/18; wings/hand20/21 remain separate cases. VS-only12–16/19,
raw MILES HUD1, donor art/results and unsupported raw mappings remain explicit
limits. Missing referenced ordinary art still fails closed. VS ownership was
notified of the `$3DF` requirement; no shared graphics workaround or production
selection change was made.


Corrected ordinary inventory and affected guards46091 completed85 tests:
83 passed,2 failed,0 errors/skips (queue460s, Maven32.925s), Java25.0.5 and
the original absolute S2 ROM. Eight assembly cases pass. The inventory resolved
2243 mapping cases and20900 fragments across13 art roles, three player/team
contexts and four flip combinations. Its1800 ordinary effective-palette3
fragments reference150 unique source tiles and contain zero indices14/15.
The same decoded pipeline observes33840 deliberately shadow-owned index15
pixels as its positive control. This is a pre-admission source superset for the
supported World/English ordinary producers, not donor/VS/results/general-VDP
certification. The actual ordinary palette selection, native mapping address,
priority and DPLC/source-art path participate in the test; unresolved ordinary
references still fail closed.

Rewind architecture4 and field-disposition1 pass. Architectural source guard
passes70 of72 cases. Its exact two failures are
`objectManagerFacadeStaysWithinExtractedCollaboratorBudget` (ObjectManager3107
versus3086 effective lines) and
`releaseCriticalLargeClassesDoNotGrowWithoutExtraction` (AbstractPlayableSprite3296
versus3258, Sonic1ObjectArtProvider2054 versus2047). All three files are byte
identical to this slice's HEAD `ccfb02562116b9d5321c26de810d6d967e2a2780`.
ObjectManager/S1Art growth entered through the approved native HUD/ownership
leaves composed by91f3; their owner and parent were notified. The playable's
prior44c/0f595 additions require a separately coordinated private-helper
extraction after this H32 checkpoint. No ratchet or API pin was changed, and
this result is not described as fully green guards. Consumed diagnostics were
removed after reading test identities, skips and inventory totals.


Final required trace selection77123 on the H32 candidate after `ccfb02562`
completed9 tests:8 passed, one failed,0 errors/skips (queue315s, Maven95s).
Exact selector is
`TestS2SpecialStage1TraceReplay,TestS2SpecialStage2TraceReplay,TestS2SpecialStage3TraceReplay,TestS2SpecialStage4TraceReplay,TestS2SpecialStage5TraceReplay,TestS2SpecialStage6TraceReplay,TestS2SpecialStage7TraceReplay,TestS2EhzHalfpipeRoundTripChain,TestS2Arz2LevelSelectTraceReplay`,
with `env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3
 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay
-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
-Dopenggf.headless.gl=surfaceless-egl
-Dtest=<exact selector above> -Dsonic2.rom.path=<original absolute REV01 path> test`,
Java25.0.5 and ROM property verified in XML. All seven native stages and the
independent ARZ2 movie pass. The halfpipe round trip retains exactly its two
previously matched-baseline dynamic-art-gap axes, all eight edges65 frames early
on each return; there is no new axis or gameplay divergence. The main emerald
chain was not repeated for this draw-only correction; its last completed bound
is21181 on d489,14 axes. No timing comparison was weakened. The earlier EGL,
bootstrap, palette, API, owner-output and restore controls remain as individually
reported above, with no generic S/H/native screenshot or full-suite claim.
Consumed trace/Surefire reports were inspected and removed. A new test comment
was corrected to the exact loc_35978 label afterwards; no executable code changed.


Private rewind-star helper extraction after H32 f57e01f2b16d1f10444fd42bbfb68cf3bf6a75b4

Only AbstractPlayableSprite's private refreshInvincibilityStarsAfterRewindRestore
and single-use resolver move into the existing engine-only
PlayerBoundPowerUpRestoreAccess. The private wrapper preserves timer<=0
short-circuiting of the S2 preservation marker and explicitly passes the manager,
player, timer and marker. Its Consumer publishes each field assignment before the
same callback/follow-on work. Suppliers reread spawner, cached stars and shield
at the original field-read points; there is no new captured state, allocator,
public/protected playable method or Mod API change. Existing pending-entry
restoration, stock-class/owner matching, foreign-slot refusal and failed-attempt
rollback are unchanged. The shipped FixBugs=0 Obj35 lifetime evidence remains
Obj35_Layer0Update (s2.asm:42494-42503).

Rejected draft: passing the three mutable references by value ran58 focused
controls successfully in session45199 (queue77s, Maven89s), but read-only review
found it retained the entry spawner/shield after destroy callbacks rebind those
private fields. Those passes did not prove equivalence. The accepted supplier
refinement preserves the original repeated reads. The new concrete-player
three-game controls observe the actual private cache during destroy/spawn/refresh
and an exceptional refresh; six further cases mutate the spawner to B or null
and create a shield from the destroy callback. They require B or suppressed spawn,
with the newly published shield receiving the later visibility update.

Final source review compared both original private methods with the moved code
and found no remaining callback-read/publication mismatch within the approved
injected-services/shipped-controller contract. It ran no additional test job.

Final source-frozen selection65276 completed77 tests:75 passed,2 failed,
zero errors/skips (queue303s, Maven96s). All64 affected restore/music/Super/callback
controls and11 API checks passed. All11 XML reports identify Java25.0.5 and the
three original absolute ROM properties. The nine callback-publication cases cover
S1/S2/S3K, including the six B/null spawner/shield mutation cases.

The two exact size checks retain only the unchanged foreign overruns:
`TestArchitecturalSourceGuard#objectManagerFacadeStaysWithinExtractedCollaboratorBudget`
reports ObjectManager3107>3086, and
`TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`
reports Sonic1ObjectArtProvider2054>2047. AbstractPlayableSprite is no longer a
violation of its3258-line budget. Both foreign files are byte identical to f57e01f2;
no ratchet, formatting padding or owner code was changed. Parent/Sprite own their
separate extraction. This is not a fully green source-guard claim.

Exact focused selection:
`TestInvincibilityRewindPublicationOrder,TestShieldRewindRestore,TestAbstractPlayableSpriteRewindCapture,TestShieldRewindPendingRestore,TestSonic2SuperTransformationStartEffects,TestS2SelfContainedTransientRewind#playerBoundInvincibilityStarsRestoreThroughSessionSnapshot,TestS3kSelfContainedTransientRewind#playerBoundInvincibilityStarsRestoreThroughSessionSnapshot,TestSonic1PowerUpMusicRestore,TestPowerUpMusicRestore,TestModApiSignatureSurface,TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction+objectManagerFacadeStaysWithinExtractedCollaboratorBudget`.
Command: `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk
PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH LUA_BIN=/usr/bin/lua5.4 python3
 tools/testing/maven_queue.py --lean -Dmse=off -Dtest=<exact selection>
-Dsonic1.rom.path=<original absolute REV01 S1 path>
-Dsonic2.rom.path=<original absolute REV01 S2 path>
-Ds3k.rom.path=<original absolute locked-on S3K path> test -B`.
No trace/native capture, broad smoke or input/movement work was run for this
private extraction. Earlier aliases, source-qualified cases and route/timing
limits above remain unchanged. Consumed Surefire/trace diagnostics were inspected
and removed after the bounded result was recorded.
