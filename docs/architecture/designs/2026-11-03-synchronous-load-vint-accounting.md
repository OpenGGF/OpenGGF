# Synchronous load and transition V-int accounting

Status: proposed, implementation deferred for lead/user decision. Follow-up to
[the Mega Slots audit](../audits/2026-11-03-s3k-mega-slots-clock.md), measured on
`347b695dc` with temporary read-only probes. No runtime change is included.

Follow-up: the [native/engine phase census](../audits/2026-11-03-s3k-load-clock-causal-accounting.md)
now partitions the residual 53 ticks in this witness: 33 zero-routine interrupt
increments outside explicit waits, 18 shorter terrain-wait ticks and two
shorter locked-title ticks. Only 25 of the 33 interrupt samples fall inside
direct block/chunk decode or layout copy; six follow unmasking and two occur
during initial object work. The 53 must not become a synchronous-decompression
budget. The proposed ledger/pricing implementation remains deferred.

## Localization and hypothesis result

The deficit accumulates over **three fresh loads**, not during ordinary play
or the seamless AIZ1→AIZ2 advance. These are matched semantic boundaries;
movie cursors parked or re-anchored during choreography are not elapsed time.
Recorded values come from physics CSV / mode-change aux rows, joined offline.
The entry clocks precede destination row zero; adding its first V-int gives
877 at the initial AIZ row and 9098 at the first Slots row.

| Boundary | Native V-int | Live object V-int | Deficit | Newly lost |
| --- | ---: | ---: | ---: | ---: |
| Initial AIZ bootstrap, before row0 | 876 | 876 | 0 | 0 |
| AIZ→Gumball restart requested, row4545 | 5422 | 5422 | 0 | 0 |
| Gumball admitted, before row0 | 5529 | 5497 | 32 | 32 |
| Gumball→AIZ restart requested, row1277 | 6807 | 6775 | 32 | 0 |
| Returned AIZ admitted, before row0 | 6957 | 6903 | 54 | 22 |
| Immediately before AIZ act advance, row1628 | 8586 | 8532 | 54 | 0 |
| Immediately after act advance, row1629 | 8587 | 8533 | 54 | 0 |
| AIZ2→Slots restart requested, row2002 | 8960 | 8906 | 54 | 0 |
| Slots admitted, before row0 | 9097 | 9002 | 95 | 41 |

Matched restart-to-admission spans are107/75,150/128 and137/96 native/engine
V-ints respectively. Every intervening ordinary gameplay row retains its
incoming deficit. Initial bootstrap work precedes the seeded start and is
not charged again. Its initial equality is by construction, not a measurement
of the native pre-trace load cost; this fixture starts at movie offset915. Ordered fresh loads are Gumball (level index230), returned
AIZ1 (192) and Slots (234); the act advance retains load generation4 and adds
no fresh-load deficit.

**The sync-load-lag hypothesis is only partly established.** Each bonus-entry
fade also has21 held engine steps at an unchanged object clock. The source
`LevelIterationAdmissionController.consumeTransitionFreezeRow` returns before
VBlank service when its observer has no unconsumed rows. The chain has closed
the source comparator at this point. A branch-site probe confirmed exactly21
calls per entry with `observerHasUnconsumedRecordedRows=false`: at cursor5570
the object clock stays5422; at9142 it stays8906. The first missing V-int is
therefore the first held Gumball-entry fade step, before its fresh load.
This is distinct from synchronous work cost: an existing fade loses service.

After those42 held rows,53 V-ints remain distributed11/22/20 over the three
load/title spans. The load-step probe finds no elapsed V-int charge at
`LoadLevelData`, `InitBackgroundRenderer` or other synchronous init steps.
`InitObjectSystem` temporarily resets the local counter to0; outer load carry
restores the incoming value, so this reset is not a loss of the whole clock.
This exposes the missing synchronous-time accounting, but does **not** prove
that all53 residual V-ints belong to synchronous decompression: queue waits,
title release and phase fences must be separated before pricing them.

The survey's S1/S2/KiS2 cluster is therefore a related architecture frontier,
not proof of a common exact budget. Its measured early return/ending edges
remain evidence from that survey, not newly measured here. FAST is not a
synchronous-load ledger: S3K has a real `s3k-fast-v1.json` readiness manifest;
`HardwareTimingService` applies it to submitted queue jobs at service
boundaries. Changing a fingerprint's serviceFrames cannot account for
synchronous work that submitted no such job. `serviceLevelLoadVBlank` is a
consumer hook (currently S2's pending music publication), not an elapsed-time
source; calling it repeatedly alone does not repair the clock.

## Owner and ROM evidence

The production level-entry/transition lifecycle should own elapsed physical
rows, independent of whether a comparator is attached. Dispatch each through
the existing physical VBlank lifecycle token; the bound ObjectManager clock,
or its owning EngineContext when detached, advances exactly once. Keep the
`LevelInitProfile.serviceLevelLoadVBlank` hook for game-owned consumers.
Do not let that hook also increment the clock and double-count its caller.
Use injected session ownership: a static EngineTiming lookup in this headless
fixture sampled a different, unseeded clock. That initial probe was rejected;
all numbers above use the live ObjectManager read by Slots.

S3K's `Pal_FadeToBlack (sonic3k.asm:5074-5085)` has22 explicit VSync waits.
`Level (sonic3k.asm:7536-7575)` enters it before fresh assembly.
`loc_62CC (sonic3k.asm:7769-7781)` is the locked title/art loop.
`LoadLevelLoadBlock (sonic3k.asm:9737-9782)` queues terrain art and explicitly
waits for its KosM owner; those waits must remain queue-owned.
`LoadLevelLoadBlock2 (sonic3k.asm:38714-38768)` synchronously decodes
block/chunk streams and calls Load_Level, with interrupts enabled at the
caller; `loc_6310 (sonic3k.asm:7789-7803)` masks LevelSetup separately.
`loc_6468 (sonic3k.asm:7880-7915)` owns setup-only sprite dispatch before the
first LevelLoop VSync. Preserve all three kinds of elapsed work separately.

Cross-game source anchors: S2 `SpecialStage (s2.asm:6537-6644)` restores
interrupts before synchronous stage setup; `Level_TtlCard (s2.asm:4914-4927)` contains the separate HUD VInt fence. S1 `GM_Ending (sonic.asm:3560-3632)` performs its own synchronous scene setup. Audit the
interrupt-enabled and masked spans of each owning routine; do not copy a
single load delay between games, acts, entry and return.

## Proposed derivation and boundaries

1. Repair the existing fade VInt service path independently of comparator
   exhaustion, while preserving movie-cursor ownership. Count explicit ROM
   waits as explicit production states, not as a budget estimated from a
   destination offset. The current21 held steps are evidence, not a new delay.
2. Have actual selected load operations emit a production work ledger: ROM
   source/range, compression format and decoded algorithm work, layout/plane
   construction work, selected native branch, and interrupt-service regime.
   Cached or predecoded Java data must not erase native work; cache hits must
   carry the same work description. Do not charge queued KosM/Nemesis work a
   second time as synchronous work.
3. Derive serviceable V-int spans from the owning ROM algorithms and bounded
   timing analysis of that work, carrying fractional phase across operations
   rather than rounding every loader to a frame. Keep masked work and
   explicit waits separate. Refuse unresolved bounds; do not invent exact
   row counts from the53 residual or the survey's36–109. This is algorithm
   accounting, not CPU instruction execution, virtual VDP/beam timing or
   hardware bus/register emulation. A derivation requiring those would exceed
   the [engine boundary](../engine-map.md#reimplementation-boundary).
4. Consume the ledger through held load phases, running only the ROM-equivalent
   interrupt service. Preserve controller policy, queue service boundary and
   captured timing state across rewind/recreation. No object/gameplay pass
   occurs merely because loading consumed time.

The [hardware-timing contract](2026-07-27-cross-game-hardware-timing-trace-contract.md)
classifies synchronous loading as lag/phase, not a new completion kind. Trace
lag may select an already implemented loop; readiness events may release only
matching prepared production work. No physics/aux values, per-row counter
sync, destination-derived delays, fitted fixture constants, cursor seeking,
queue fabrication, ordinal shifting or comparison suppression is authorized.
The narrow readiness manifest cannot become a general time-injection port.

## Expected witnesses and implementation gate

Mega's three load deficits and Slots' clock-dependent zero payout / early
release / ownership failure should move. Its independent animation and camera
frontiers are not expected to disappear. Check S2 and KiS2 special-stage-return
early dynamic-art edges and S1 ending entry; preserve already aligned later
edges and keep S2's separate HUD fence explicit. Do not promise a green
campaign: independent gameplay and missing-manifest frontiers remain.

A future implementation needs source-derived ledger tests, interrupt/clock
exactly-once and rewind checks, cached/uncached equivalence, another BK2 per
load family, plus the lead's full `trace-replay` and `trace-replay-r7` profiles
before/after; add `trace-segments` before/after if segment-boot timing changes.
Compare exact error lists under the lane validation gate. This note proposes
no runtime implementation or new trace authority.

Validation for this note: the queued Mega probe uses the environment from the
[previous audit](../audits/2026-11-03-s3k-mega-slots-clock.md#reproduction-commands)
and the following command. The diagnostic property only enables temporary
read-only observation sites; none is retained in the delivered tree.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 \
  -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS3kMegaRunChain -Dopenggf.debug.loadClockSurvey=true \
  "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

Three probe invocations each completed one test, one failure, no errors/skips,
with unchanged Mega axes (cursor10103 ownership rejection; segment2 errors112;
segment3 errors7723, first172 y_speed). The first static-global-clock probe
was discarded for clock localization; two live-object-clock runs corroborate
the table, and the last confirms the freeze gate. Maven execution including
compilation was102/103/109 seconds, separate queue waits92/132/555 seconds.
The old cross-chain survey was inspected, not rerun. No runtime change remains,
so the conditional full-profile gate was not invoked. Temporary probes were
removed; staged whitespace, local links and strict disassembly citations were
checked. No green replay or full-profile pass is claimed.


## Bonus-entry follow-up

The [source-tail experiment](../audits/2026-11-03-s3k-load-clock-causal-accounting.md#bonus-entry-ownership-follow-up)
recovered 20 admission-clock ticks by retaining recorded readiness during native
bonus loading, but is rejected as a standalone correction. The totals leave the
measured 33 synchronous-load interrupts, while each bonus title remains one tick
short and its terrain wait becomes one tick long. Matching totals therefore do
not establish correct phase ownership. Mega reaches an earlier ownership failure,
and the independent cold-start control does not reach its bonus. Both adapters,
title-gate ordering and a valid continuous second movie remain qualification
obligations. This proposal remains deferred; no runtime load price is introduced.
