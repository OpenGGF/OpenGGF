# Synchronous load and transition V-int accounting

Implementation source: `aba50b4f7dab48ba13833a4449810db36a85b636`. See [final qualification and remaining limits](#final-delivery-qualification-2026-11-04); earlier checkpoints below are historical.

Status: original ledger proposal rejected; bounded timing extension approved and under implementation.
The earlier ROM-stated V-int trial is retained as history in
[the load-time V-int trial](../audits/2026-11-03-load-vint-trial.md); its remaining
cycle-cost boundary motivates the bounded recording input below. The release
V-int shares ownership with the resumable title scan rather than being added twice.
Follow-up to
[the Mega Slots audit](../audits/2026-11-03-s3k-mega-slots-clock.md), measured on
`347b695dc` with temporary read-only probes. The original census is followed below
by the authorized implementation boundary.

Follow-up: the [native/engine phase census](../audits/2026-11-03-s3k-load-clock-causal-accounting.md)
now partitions the residual 53 ticks in this witness: 33 zero-routine interrupt
increments outside explicit waits, 18 shorter terrain-wait ticks and two
shorter locked-title ticks. Only 25 of the 33 interrupt samples fall inside
direct block/chunk decode or layout copy; six follow unmasking and two occur
during initial object work. The 53 must not become a synchronous-decompression
budget. The original ledger/pricing proposal is retained below as rejected history.

## Reviewed implementation boundary after the title correction

At `32d4e9aae`, the bonus-entry fade correction restores all 22 fade waits and
the locked-title spans are 70/80/83, matching the native census. Terrain waits
remain 5/26/13 against native 10/26/26. The cumulative entry-clock deficits are
10/32/51: 18 terrain-wait ticks and 33 synchronous zero-routine interrupts.
These counts describe this witness only and are forbidden as runtime budgets.

The independent phase and work-ledger designs and their cross-family review
agree that terrain ownership can be corrected under the existing contract.
Source gameplay comparison ends independently of physical source coverage.
The existing run coordinator should retain the source timing schedule through
actual fade/title production, then release at native destination readiness or
coverage exhaustion. A native release with source rows left must report that
suffix explicitly, never run destination gameplay against source input or hold
production until the recording ends. `HardwareTimingCoordinator` remains the
sole movie-to-raw-row mapper. Nested title admission must preserve a row the
outer physical driver already prepared without re-beginning it and resetting
service-boundary ordering.

The original ledger-to-VInt pricing proposal below is **rejected**, rather than
an implementation plan. No source-derived semantic work-to-duration law or
incoming fractional interrupt phase has been established. Pricing decoder
tokens or Java initialization steps would introduce an arbitrary timing model;
instruction/cycle pricing would exceed the engine reimplementation boundary.
Work receipts without a real consumer would not repair this gap.

Current V5 per-row lag evidence cannot uniquely select the native outcome.
An interrupt-enabled synchronous operation can service `VInt_0`, whereas a
masked row services nothing; both can be emulator lag rows. Mega's four
affected segment payloads contain no `lag_state` events at all. The native
census additionally has 2/3/1 masked rows across the three loads, so physical
row counts and clock increments are not interchangeable. Reading recorded
counter deltas would make clock comparison tautological and remains forbidden.

The smallest proposed new authority is a dedicated, reviewed initialization
stream that may select serviced versus masked physical load rows and resume an
already-started production load continuation at its semantic phase boundaries.
It must supply no counters, gameplay values, CPU/register/cycle state or decoder
progress; create no work; and consume no physics/aux comparison values. Holding
or resuming direct production work is additional authority beyond current
readiness release and lag-loop selection. The user has now authorized this
bounded extension. It requires a revised contract, recorder coverage and a genuinely resumable
production load. Wrapping the current atomic `LevelLoadAttempt` in a facade is
insufficient. The approved scope and ordered V5 companion stream are specified
in the [timing contract](2026-07-27-cross-game-hardware-timing-trace-contract.md#2026-11-03-approved-extension-synchronous-initialization-phases).

Even an approved replay extension would leave exact live synchronous timing
unresolved. Native zero-routine service must also remain distinct from ordinary
held VBlank queue service, and object initialization needs proof that interrupted
object prefixes commute with any proposed semantic continuation boundary.

One specific phase witness must be checked before accepting the terrain slice:
the native deferred interrupt after display-clear unmask occupies its own row
before the title loop. Retaining source readiness without that row can expose
an 11/27 terrain span rather than 10/26. Do not compensate by changing terrain
readiness or queue costs; measure movie rows and clocks at both boundaries.

## Initial-object semantic cuts

The Gumball sample interrupts allocation after `Obj_GumballMachine` has seeded
its RNG from the live V-int clock, before its child family is complete. The
seed read precedes `CreateChild1_Normal`; the allocator and child copying do not
read the clock or RNG. Production prepares the machine state once, then admits
zero-routine service before its real child-creation pass. The saved initialized
flag prevents a later first update or rewind restore from reseeding it. This
proof identifies an equivalent semantic cut, not the exact interrupted allocator
caller: the native sample contains no stack/caller evidence.

The Slots sample falls in `sub_4B6AA`'s bootstrap clear called by `SpawnPlayers`,
before `Process_Sprites`. Clear, placement, mapping and allocation do not read
the clock or RNG; the later Slots entropy reads remain after the cut. Production
therefore holds its real bootstrap before that clear and player-cage assembly.
Moving this service past the Slots entropy reads would be invalid.

The source-tail probe initially consumed all four expected readiness edges while
silently losing 21 fade ticks. Its passing edge count was insufficient evidence.
The corrected prefix check pins the native fade endpoint as well as represented
job membership and consumed edges. With terrain readiness corrected and before
synchronous-phase input, first Gumball admission retained the remaining five
native synchronous ticks as a deficit. Wider route validation then exposed an
earlier Slots title-ownership failure, so the terrain-only candidate was not
accepted for delivery. These are diagnostic checkpoints, not passing claims for
the larger continuation implementation.

The longer native recording exposed another initial-object service on the second
AIZ2 return: absolute BK2 row 19190, interrupted PC `$1AF86` in `sub_1AF6C`,
a sprite-rendering helper after the initial object pass. Moving that service
before the object pass is unsupported. The continuation therefore separates
`INITIAL_PRESENTATION` after the actual initial object execution: prepare the
CPU sprite table, admit service, then run the retained initial animation tail.
The native observer begins this phase at `$6484` (`Render_Sprites`) and closes
its permitted interrupt window at `$648A` (`Animate_Tiles`), rejecting later
services until separately proved. Rendering and its HUD/ring/LRZ helpers read
neither the V-int clock nor RNG; HUD flash reads `Level_frame_counter`, which
zero-routine service leaves unchanged. The service may therefore commute to
completed sprite-table preparation before animation. Ordinary immediate callers
retain the previous combined object/animation executor.

A later AIZ2 return exposed service in the other direction: absolute row 24050,
interrupted PC `$E8E8` in `Load_Rings` / `loc_E8E6`, before initial object
execution. `INITIAL_PLACEMENT` now owns real player restoration and parent/ring
placement through the `$647E` first `Process_Sprites` call. Gumball's seed read
is prepared in the following `INITIAL_OBJECTS` phase, so placement services
cannot be moved past that read. The recorder distinguishes both operations
rather than applying a destination-specific delay. This larger shape supersedes
the earlier four- and five-phase diagnostic captures below.

The complete independent census then covered 114,622 physical input rows and
22 fresh loads (including the pre-trace bootstrap), with 2,407 diagnostic
markers; SHA-256 of that diagnostic CSV was
`8614f317177e6b9527838bdc3404670e4302cd020520e4ead48ff1598a2d003e`.
Two additional object-loop interruption points need equivalent completion
classification: HCZ2 input row 54274 at `$1AB08`, and Pachinko input row 92963
at `$1AAFC`. The HCZ2 saved `A0=$FFFFB206`, `D7=$67` identifies the next SST
entry; it does not by itself prove that remaining entries are empty.

The observer may finish object work at `loc_1AB04`, after the current callback
and before the next `LEA`/`DBF`, only when its remaining traversal count agrees
with the verified 110-entry, `$4A`-stride SST and every remaining entry has a
zero callback pointer. This is actual callback completion, not a fitted slot
cut. Remaining empty iterations and rendering read neither clock nor RNG;
`VInt_0` creates no objects. Native register/slot values remain diagnostic-only
and are never exported in timing input. An earlier candidate hooked `$1AB08`,
which could run too late for an interrupt returning to that address; independent
review required the earlier boundary and a `D7` consistency check. Both were
added with adversarial controls. The full native timing capture remains under
qualification, separate from this source/diagnostic proof.

### HCZ callback-tail proof and rejected empty-only classification

The empty-only classifier still rejected the HCZ2 capture. A second native
probe found live callbacks after the interrupted loop boundary, beginning with
`Obj_StillSprite` at SST `$B206`. The remaining families were title elements,
a first-invocation special-stage ring, snake blocks, spinning columns, dash
dust and the wave splash. Treating this as an empty scan would be incorrect.

Their source closures permit a different equivalent cut: finish the real object
pass and CPU sprite-table preparation before this service, while retaining
animation after it. The recorder checks the saved interrupt PC against the
actual between-callback loop instructions and validates the SST pointer/count
shape. It then checks **every actual later JSR target**, including children
created by another callback, until native `$6484`. Unknown targets invalidate
the staged capture even when earlier rows were already written. The JSR hook
also requires its sampled target to equal the current SST callback pointer;
register sampling is reference observation, never serialized timing input.

| Reviewed entry | Owning source | Why VInt_0 commutes with the remaining call |
| --- | --- | --- |
| `$2B928` | `Obj_StillSprite (sonic3k.asm:60244-60266)` | Attributes, draw and deletion |
| `$2D690` | `Obj_TitleCard (sonic3k.asm:62135-62343)` | Local counters, children and queued art |
| `$2D95C` | `Obj_TitleCardElement (sonic3k.asm:62394-62421)` | Parent state, movement and draw |
| `$2D8E2` | `Obj_TitleCardRedBanner (sonic3k.asm:62345-62373)` | Parent state, movement and draw |
| `$6166A` | `Obj_SSEntryRing (sonic3k.asm:128270-128285)` | First call deletes or defers via Obj_WaitOffscreen; no fallthrough to collision |
| `$256BE` | `Obj_HCZSnakeBlocks (sonic3k.asm:50914-51051)` | Local angle/sine, solidity and draw |
| `$32656` | `Obj_HCZSpinningColumn (sonic3k.asm:68153-68362)` | Local/oscillation state, solidity and draw |
| `$18B3E` | `Obj_DashDust (sonic3k.asm:33994-34218)` | Local animation, art submission and same-type children |
| `$1F202` | `Obj_HCZWaveSplash (sonic3k.asm:43206-43266)` | Local animation and Level_frame_counter, not V_int_run_count |

The closures neither read the interrupt/lag clocks nor modify palettes read by
VInt_0. It does not service their queued PLC/DMA work or sample new joypad input.
Queued work keeps its physical-row membership and ordinary readiness boundary.
CPU rendering, HUD and ring preparation also use local or level counters;
competition remains excluded and VInt_0 still publishes no sprite table.
Other title children, Hyudoro and unknown dust child entrypoints are unproved
and reject capture. This is a bounded source proof, not a whitelist inferred
from a passing movie. Focused recorder tests cover malformed traversal, an IRQ
inside a callback despite a stale boundary observation, unknown late children,
and target-sampling mismatch. Native capture qualification remains pending.

### Full native candidate after callback validation

The candidate on TraceChaser base `703bd92d` plus the task changes completed
all 114,622 movie inputs: 25 segments, 22 transitions and 21 represented fresh
loads (the initial bootstrap is excluded). The native publisher produced
`initialization_timing.jsonl.gz` with these frozen properties:

- Stored length: 6,912 bytes; SHA-256 `22081bbc0c403c4324d5c1bfda678f1c99871d352e335a503f9178684307b1a9`.
- Decoded length: 193,924 bytes; SHA-256 `16a9bb99255f92d81084e4e989f8ca3d1152b940dc99ec76335e3f1ad147e7fb`.
- 800 events: 359 holds, 315 zero-routine services and 126 phase resumes; at most five events per row.
- All 315 service rows and all 21 load-release rows match the earlier independent full-movie census exactly, including load identity and absolute row.

The decoded bytes match the prior full candidate before rebasing onto the
consumer-pinned recorder revision. Its first 9,200 inputs also remain identical
to the earlier six-phase prefix (`c1d32d85831e5a8478c53cbaf7799425ef72f2beefaa6fc56933090a6302702d`).
Independent code review found that a recording cap could otherwise stop before
remaining callback validation finished. A success-path completion check now
rejects active loads, unflushed phases and provisional tails before publication;
cleanup does not throw a second completion error. Real runner/sink tests prove
that capped phases and provisional tails publish no files and a completed tail
can publish. The full recapture with that correction preserved the frozen bytes.

This candidate remains outside canonical fixtures pending consumer qualification
and explicit approval of these exact native bytes. The engine prefix uses an
external overlay with unchanged canonical comparison files. No metadata seed
replacement, comparison suppression or exact live-play timing is established.

## Native recorder qualification checkpoint before presentation separation

The reviewed native observer uses the verified locked-on ROM, semantic Level
execute boundaries and actual `VInt_0_Main` service. It rejects ordinary handlers
inside synchronous phases and initial-object service outside proved cuts. The
Gumball cut opens at `$60C80`, after the `$60C7A` V-int-to-RNG seed instruction;
the Slots cut opens at `loc_6B76` and closes at `$647E`, before the initial
`Process_Sprites` call. No interrupted PC or register state enters the stream.

A 9,200-input-row prefix of `s3-knux-multibonus-ss.bk2` produced 84 events,
20,194 decoded bytes, SHA-256
`77d04d7535b831d9b6bc95f9a77ba90bafd7cc49d8846269fdbcabf9ab36b0f7`.
The capture with the initial-object safeguards is byte-identical to the earlier
candidate and retains the independent census's 33 serviced interrupts:

| Represented load | Absolute BK2 row range | `vint_0` | `hold` | `resume` |
| --- | --- | ---: | ---: | ---: |
| 1 | 5482–5569 | 5 | 7 | 4 |
| 2 | 6869–7000 | 22 | 25 | 4 |
| 3 | 9025–9141 | 6 | 7 | 4 |

This is recorder-prefix evidence, not an engine replay pass or canonical fixture
publication. The initial-object review objection was fixed before this second
capture; nine bounded recorder tests passed. Full-route, second-movie and
production/headless admission qualification remain outstanding.

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

Matched restart-to-admission spans are107/75,150/128 and 137/96 native/engine
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

The subsequent [blocking-fade correction](../audits/2026-11-03-s3k-load-clock-causal-accounting.md#bonus-entry-blocking-fade-correction)
uses the existing production owner at bonus entry. It restores the measured
locked-title spans to native 70/80/83 without the rejected readiness handoff.
The current residual is51 ticks:33 measured synchronous interrupts and18
shorter terrain waits. This closes the bounded title-loop discrepancy, not
the synchronous-load design or another campaign's qualification.


## Integration and qualification checkpoint, 2026-11-04

The uncommitted candidate is in `.worktrees/load-phase-readiness`, branch
`bugfix/ai-load-phase-readiness`, based on
`aa26247c07b8b928c080946b651cffc91bbc51a9`. The pre-task integration base is
`32d4e9aae457b8aae00abd67d172a530a75d6c71`. Reconciliation retained upstream
segment-carried state, seamless frame ownership, return-tail withholding,
`loc_7870` release service, title/bonus extractions and S1/S2 solid-contact fixes.
The native continuation excludes duplicate legacy release service. No dispatcher
budget was raised; merged title update/bonus entry/bonus exit budgets are
86/22/93. Main-workspace unrelated changes remain preserved.

### Enclosing ownership corrections

- Service the final held gameplay fade interrupt before its callback can change
  modes, claiming it once. The real regression observes all 22 interrupts;
  Gumball's initial seed/release clocks become 5528/5529.
- Clear inherited rolling geometry before writing the destination centre. Prepare
  the object-art lease before caller title initialization; assemble playable art
  at its later native phase. Initial Process_Sprites runs inside loading once,
  including before title release; pause adds no ordinary pass.
- Install and size the initial placement layout before restoring persistent bits,
  preserving fixed SST owners. Defer live rewind installation until completion;
  explicit capture/restore remains rejected during a pending load.
- Retain the zone wrapper's respawn/ring bank and LOADING carry through HOLD.
  Finalize once at actual success/failure. Cleanup preserves the primary failure,
  suppresses secondary failures, and restores prior admission. Two new wrapper
  witnesses failed before this fix and pass afterward; actual continuation tests
  cover once-only terminal callbacks and exception identity. Two exact terminal
  catch fingerprints are reviewed in the existing inventory, without a fallback.
- Restore the saved runtime apparent act before return setup and preserve it in
  the zone wrapper. Actual act1/apparent act0 is a seamless return, including water
  initialization. Keep the live-manager post-load write for rebinding. The first
  correction removes the AIZ3 row105 clamp; the water consumer correction awaits
  focused qualification.
- Map physical special-stage gap rows through HardwareTimingCoordinator, the sole
  mapper. A pending native continuation owns its masked/service decision even in
  a gap; generic lag service must not bypass it. The independent movie has no
  gap-admission census and its BK2 has no LagLog. Its results/fade handoff remains
  under investigation; no timing hold or production request is fabricated.
  The fallback suppressed-gap clock service excludes rows that start, retain or
  complete a native load, preserving masked interrupts.

The upstream S3K emerald ledger moved from shared `trace.replay` into its owning
`game.sonic3k.specialstage` package, preserving its fold rules. Its eight carried
state regression cases pass and ArchUnit accepts the placement.

### Rejected explanations and attribution

The missing third bonus load followed a gameplay divergence, not insufficient
scheduling authority. Cancellation/palette-reveal/restore probes emitted no fade
callback-loss evidence; transition-freeze and realtime rewind probes also failed
to explain it. All temporary production probes were removed. At returned AIZ
row231 a revived monitor stopped the candidate at x11039/speed0. A bounded
baseline witness on `c24e9fa6e57248ba7ae29c1836f04c6088720a2b` instead observed
x11040, y711, velocity1416/1904, radii9/19, path12/13, clock7159. This established
a new regression independently of aggregate reports. The enclosing wrapper had
cleared the saved respawn bank before the held load consumed it. Correcting that
lifetime restores the real next star-post/bonus request and all first three load
release clocks: `[5529,6957,9097]`.

The canonical baseline Mega run reaches Slots but fails its stage-exit boundary.
Its returned-AIZ report has zero errors and `complete=false`; that does not imply
zero compared rows, because the headless walker can omit the physical tail.
The matched fresh-handoff baseline also fails
`realFadeCallbackKeepsItsOwnerAndDefersTheFourTitleArchives` with expected NONE,
actual HOLD_BLACK. These are inherited failures, not passing coverage.

### Native capture evidence

Recorder commit `f45bbb233f50db6ce9d7d2b8c141059548462ae0` activates optional
initialization capture after the first represented exported row, excluding cold
bootstrap. It rejects competition, nested represented loads, unfinished loads at
EOF and caps with no represented row. All 14 focused recorder tests pass. The
broader native suite has 849 passes, 52 failures and three optional skips; the
52 identities match the earlier baseline (51 assertion messages identical, one
audio seal hash changed). It is not a whole-native-suite pass.

The full Mega capture produces 21 represented loads: 800 events comprising
359 HOLD, 315 VINT_0 and 126 RESUME events, at most five per row. All 75 existing
canonical physics/auxiliary/hardware payloads match decoded bytes. Independent
native observation matches all 315 serviced interrupt and 21 release edges.
The compressed companion has 6,912 bytes, SHA-256
`22081bbc0c403c4324d5c1bfda678f1c99871d352e335a503f9178684307b1a9`;
decoded 193,924 bytes, SHA-256
`16a9bb99255f92d81084e4e989f8ca3d1152b940dc99ec76335e3f1ad147e7fb`.

The independent Sonic-and-Tails 10,000-input prefix captures one completed
special-stage return, 53 events at raw 8685–8816. Its compressed 700-byte companion
has SHA-256 `9030f8a42f6d3601339b859331c535bbec706970ea922b13ae2a40e51945d068`;
decoded 12,782 bytes, SHA-256
`5769fc4b8bfad84a8ab5d015ef948ad4544e26d5fad4370cbe0e95079cd9b686`.
This prefix does not establish the original movie's endpoint or engine parity.
Another movie ending mid-HCZ-load was correctly rejected with no publication.
No new canonical companion has been installed. External overlays contain the
literal captured bytes; comparison values never drive engine gameplay.

### Engine verification and remaining work

Commands use JDK25, Lua5.4 and
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`, through the Maven queue.
Focused replay checks use `-Ptrace-replay-r7 -Dsurefire.forkCount=1`.

| Selection / completed run | Observed outcome | Limit |
| --- | --- | --- |
| `-Pguards test -B`, v5, 05:08:19Z | 834 tests, zero failures/errors/skips; 4m19s execution | Predates apparent-act/water/gap corrections |
| Focused v15, 04:42:45Z | 92 tests, two failures, no errors/skips; first-three-load prefix and required S3K loading/bootstrap/decoding selections pass | Inherited fade assertion and then-unregistered catch sites; sites subsequently pass guards |
| Whole-movie v16, 05:10:57Z | Gets past Slots, reaches AIZ3; later boundary error | First causal mismatch row105 x, corrected apparent-act clamp |
| Bonus/source guards + whole movie v17, 05:28:22Z | 113 tests, zero failures, one whole-movie error, no skips; 25 bonus and 87 source guards pass | AIZ3 first movement mismatch row1686 x_speed0x18/0x30 at water entry; later raw17796 unconsumed initialization |
| Independent movie v1, 05:29:02Z | Matching load identity, wrong parked row7336 versus physical8685 | Missing headless gap mapping identified |
| Focused v18, 05:56:35Z | 55 tests, two failures, three errors, no skips | Two test-call replacements corrected; water test must await real continuation; independent load still unconsumed before8686 |

Focused v19 completed at 06:35:24Z: 17 tests, two failures, two errors and no
skips; Maven execution 1m47s, separate from approximately 32 minutes of queue
waiting. Both water-return tests and all timing-coordinator tests pass. The two
water-selection unit failures are a missing level identity in that fixture
(feature zone −1); the selected boolean already matches. That setup is corrected.
The whole-movie first AIZ3 movement mismatch advances from row1686 x_speed to
row6443 y (0x0782/0x078D), with a later unconsumed observation before raw17906.
The independent return is still late at raw 8685: the pre-step fade is HOLD_BLACK
with its callback pending, and at8686 it is complete with resultsExitReady true.
No prepared native load exists on the recorded first HOLD. A small independent
native results/fade marker capture and focused engine edge observation v20 are
queued to establish the owning extra iteration, without relaxing the match. Temporary test-only
observations are still present and must be removed or promoted before staging.
The change-based plan against the pre-task SHA selects approximately4452 smoke
classes plus guards; inspect again after final source cleanup. Broad smoke,
final guards, canonical publication approval, commits, integration, push and
worktree cleanup remain open. Exact live-play synchronous timing is unresolved.


### Special-stage results final-wait ownership

The independent native observation completed all 10,000 original movie inputs,
with five markers: results installation raw7411/clock7263, initial object pass
raw7412/clock7264, fade begin raw8663/clock8515, fade end raw 8685/clock8537,
and the deferred display-clear interrupt raw8686/clock8538. The optional
`loc_2E5EC` hook did not execute on this result branch; no completion marker is
inferred from it. The fade spans 22 real interrupts. The literal dedicated stream
already begins its display-clear HOLD on raw 8685, between fade completion and
the following interrupt.

Focused v20 completed at 07:19:32Z: three tests, no failures, one independent
return error, no skips; Maven execution36.143s. Both corrected water-selection
cases pass. Its engine observations match native initial-object row7412 and
fade initiation row8663 (first pre-step FADING_TO_BLACK observation at8664).
The engine completes the final fade wait on8685 but merely arms
resultsExitReady, deferring Level until8686. That extra results iteration is the
cause, not a short fade or an unmatched cartridge recipe.

The native load profile now explicitly opts into continuing Level after its
final results fade wait. The shared lifecycle takes that continuation without
another results hardware scan; other profiles retain their existing ordering.
The final source interrupt runs before fade completion can change mode or start
a pending load. loc_5FF6 returns directly into loc_6000 and display clearing
(sonic3k.asm:7555-7568); there is no intervening results Wait_VSync.

The results return caller also retains its presentation flag through the actual
attempt, restores players/checkpoint state after assembly, binds title art only
after its prefix, and reveals at that boundary. Save publication runs once only
after successful completion and terminal cleanup, avoiding a snapshot of the
held load. Focused v21 includes real independent replay, success/failure
publication controls, results lifecycle tests, and source/timing-authority guards.
It is queued; final guards, smoke and delivery remain open.


### Gap readiness is a separate captured input

Focused v21 completed at 08:05:47Z: 152 tests, zero failures, one independent-return
error, zero skips; Maven execution 1m53s. Source guards87, timing authority26,
results lifecycle and success/failure publication controls pass. Display clear
now starts at raw 8685 and resumes on 8686, matching the independent recording.
The next rejection is before 8793: no phase2 HOLD was consumed on 8792.

The new cause is queue classification. A phase token had marked gap-created
parents18–21 as recorded, without installing readiness edges for them. They
therefore stayed pending even though their completions were captured in the old
interstitial file. That file explicitly treats frame fields as provenance; it
must not be reinterpreted as readiness input. The corrected implementation adds
a dedicated seven-field initialization readiness companion, using the existing
kind/ordinal/fingerprint/boundary protocol, and activates it only after matching
production's prepared load. Old gap receipts prove claimed prefix/suffix work;
they never provide a readiness row.

The native completion owner reports old interstitial `bk2_frame` as
host.CompletedFrame. The new readiness uses host.CompletedFrame−1, exactly the
phase stream's namespace. Old parent23 at completed-frame8793 is therefore
physical raw8792, matching the phase2 HOLD. There is no evidence here for an
extra terrain wait or a fitted timing correction. Recorder scope/final-row/
compression witnesses and engine strict-loader/ordinal-receipt/real-port checks
qualify this extension before new literal captures are replayed.

Full guards v6 passed 834 tests with no failures/errors/skips at 08:11:28Z;
Maven execution 3m28s. This covers the final-wait and deferred-results changes,
but precedes the new readiness companion. Focused engine v22 is queued; final
smoke and a fresh guard pass after readiness integration remain required.


Native producer 80218a069f11a6b5ae1a34d3d7a9932a53a9d3bb is committed locally,
not pushed. Its 17 initialization and 13 compression checks pass with no skips;
both policy scanners pass. The independent regenerated prefix matches all 11
existing decoded payloads (nine segment payloads, old interstitial and phase
stream). Its new readiness companion has 17 events for load 1, raw8688–8792:
stored 955 bytes, SHA-256
`6c03dc7fbd326f5e4a5eac5761b18646b57ddc9aca05f55207952dc6099393b8`;
decoded 4066 bytes, SHA-256
`bd4fa92c6037c0ecfd9c26cfad46231b063058e0cfc38ddf67658459115de313`.

The full regenerated Mega capture matches all 77 previous decoded payloads,
including all 75 existing canonical segment payloads. Its new readiness companion
contains 53 events for loads 12/15/21, raw54155–106081: stored 1791 bytes,
SHA-256 `f331b54a3025395aeb4b0890b5b71885db0194d084b01f890d24c5e583c54b82`;
decoded 12858 bytes, SHA-256
`dab55660025932a6e0f36ce0a61dddd2f83c26dcdd3e00fd19e898e43d39e66a`.
The phase companion is unchanged, including its stored and decoded hashes.
Literal overlays are external (`initialization-second-replay-overlay-v2` and
`initialization-replay-overlay-v4`); canonical fixtures remain untouched.

Engine readiness activates only when a captured load entry and a current
production-matched display-phase proof both exist. It can activate during that
first production row, without another pump. Sparse normal queue-wait rows retain
the matched load interval. A completed-final-phase receipt ends gap authority
on the following physical row, preserving subsequent ordinary gap work and its
remaining ordinal receipts. In-flight gap capture/restore is rejected; unobserved
readiness loads fail completion. A phase token or an empty companion alone
supplies no queue readiness authority.

Focused engine v22 (strict loader, ordinal receipts, real timing coordinator,
authority guard), v23 (independent native prefix and required S3K load checks),
and v24 (first-three-load and whole-Mega observations) are queued. Build-only
v1 found a missing Map import; corrected v2 is compiling. Final guards/smoke,
canonical approval, integration, pushes and owned-worktree cleanup remain open.


Focused v23 completed at 09:23:46Z: 61 tests, zero failures, one independent-return
error, zero skips. All 60 required S3K loading/bootstrap/decoding checks pass.
The independent replay now passes its queue/phase admissions and reaches the
real title release. Mode-boundary reporting then asks LiveRewindManager to install
a RewindController, whose constructor captures the active readiness gap and is
correctly rejected. This is automatic controller installation, not an explicit
user rewind and not a queue-data mismatch. Run timelines remain non-rewindable:
LiveRewindManager now declines installation while the physical run driver is
attached. The guard is retained. Focused v26 covers the independent row-zero
clock and no-controller assertion plus ordinary level-owner/special/bonus rewind
regressions. V24 and the corrected coordinator v25 remain queued.


Focused v24 completed at 09:34:49Z: two tests, zero assertion failures, one
whole-run error, zero skips. The first-three-load witness passes on the literal
v4 overlay, retaining release clocks 5529/6957/9097. The whole-run observation
stops before physical raw 17906 with the load-5 data-decode HOLD at 17905
unconsumed. Its preceding AIZ3 comparison first disagrees at row 6443, y
expected 0x0782 versus actual 0x078D, with 2589 error groups before interruption.
That incomplete report does not qualify the campaign or establish the later
load failure's cause. A bounded queue/title-state observation is queued as v27.
Coordinator v25, independent return/rewind v26 and fresh full guards v7 remain
pending shared Maven admission; queue time is not execution failure. The
candidate remains uncommitted on aa26247c0; canonical files are unchanged.


The user explicitly approved the exact Mega phase companion (6912 stored bytes,
SHA-256 `22081bbc0c403c4324d5c1bfda678f1c99871d352e335a503f9178684307b1a9`)
and readiness companion (1791 stored bytes, SHA-256
`f331b54a3025395aeb4b0890b5b71885db0194d084b01f890d24c5e583c54b82`)
after reviewing the unchanged gameplay-payload delta and incomplete later
campaign coverage. Installation is authorized after validation; the files remain
external while the pending focused checks and later-load attribution complete.


Upstream reconciliation: main and candidate are now based on
`91151314217a8b2e51c471e93700b766da1e1b68`. The candidate's uncommitted work
was preserved in owned stash `f5a6dcdf52083f3b9e9185db207c054265e0c945` before
fast-forwarding, then reapplied. Both frontier-log sections are retained. The
LevelManager conflict is resolved by capturing upstream's deleted-title-owner
wait as an immutable presentation choice before the physical load clears state;
ordinary suppression still omits its title. This keeps the ending's title and
terrain waits and its white presentation without late policy reselection.
Upstream's locked title event/scroll gates and pre-gap S2 clock anchor remain.

Native upstream `a9f8b94` is merged locally as `98579e1`; its one conflict retains
both physical lag census and the initialization observer. The merged native
build passes 17 initialization and 13 compression checks, no failures or skips;
policy and history scanners pass. One initial filter selected no tests, and a
second run omitted S3K_ROM_PATH (seven skips); neither is counted as qualification.
The corrected ROM-backed initialization run has no skips. These are focused
checks, not a fresh full native suite. No native push has occurred.

Coordinator v25 completed at 09:46:28Z on the pre-reconciliation aa26247c0
candidate: 12 tests pass, no failures/errors/skips, Maven execution 35.223s.
Pending v26, guards v7 and v27 requests remain alive and will compile/test the
updated candidate when admitted. The approved literal companion hashes now
have a permanent publication test; installation still waits for validation.


Focused v26 completed at 09:56:29Z on the reconciled candidate: 34 tests,
one independent-return assertion failure, zero errors/skips; all 33 live-rewind
regressions pass. The replay clears the earlier snapshot rejection but cannot
admit the returned level because no completed-load receipt was observed. Root
cause: the deferred runtime completion activated scheduled playback before
NativeLevelLoadController incremented its successful generation. The synchronous
path increments before its outer activation. Activation now uses the existing
success callback after terminal cleanup and generation publication, preceding
caller save publication. A real controller/tracker regression checks this order
and exactly-once activation. Focused v28 combines it with the independent replay,
immutable title selection and upstream ending terrain-wait witness. Guards v7
and bounded later-load observation v27 are still queued and will test this fix.


Permanent Mega coverage now retains the first-three-load seed/release/placement
assertions. Its initial promotion used an AtomicInteger for the RNG's long seed;
guards v7 and observation v27 both failed test compilation before any tests ran.
The holder and literal are corrected to long. Build-only v4 passes at 10:04:45Z
(Maven 35.736s), including the new completed-load publication regression.
Fresh guards v8 and later-load observation v29 replace those failed invocations;
v28's independent return/publication/ending checks remain queued. The current
change-based plan selects 4456 smoke classes plus guards; no broad smoke has
been launched. The task remains uncommitted and unpushed.


Focused v28 completed at 10:07:40Z: eight tests, three assertion failures,
zero errors/skips. All four completion/publication regressions pass. The
independent replay now reaches its first returned gameplay row, but the temporary
probe's blanket no-controller assertion is too strong after the physical driver
has detached. The phase/gap snapshot rejection remains unchanged. A permanent
LiveRewindOwnerLifetime witness now checks rejection while that driver is
attached and ordinary installation after detachment; the replay retains its
native row-zero clock assertion.

The two upstream ending witnesses also use freshLevelTransitionMode as a proxy
for unfinished terrain. A real resumable continuation can already drain
loc_7870, execute LevelSetup and initial Process_Sprites before its title
provider retires that presentation flag. Their director assertion now uses
pending physical module work, the ROM's actual fence; both still require the
real terrain submission and queue-drained release in GameLoop and recording.
No production timing or gameplay check is weakened. V30 rechecks these boundary
witnesses and the independent clock; guards v8 and later-load observation v29
remain queued. The approved canonical companions are still external.


Full guards v8 completed at 10:13:21Z: 834 tests, one failure, no errors/skips;
Maven execution 3m19s. All 87 architectural source guards, all 26 timing-authority
guards and all 11 trace-invariant guards pass. The sole failure is the exact
legacy fingerprint of TraceSessionLauncher.finishRunReplayLaunch. Review of its
diff shows only the added readiness loader argument in the guarded try block;
the Throwable handler still delegates to abortLaunchFailure with the original
failure and the existing resource/session cleanup. The inventory replaces
`901da1a170cc5bd6e62567e6327cb3eaa3cca34a160dc3070b85f959406da842` with
`d077162e1f84de67fd8778f3efe8aacc19c1387c269d370b641fcfa437b2ba17` at count one.
No new fallback allowance is added. V31 checks the exact inventory and active
payload cleanup; final combined smoke/guards remain required.

Native upstream's later S1-only census publication (10f1e09) is retained in
local merge c733354. Its four changed paths contain no S3K recorder or shared
initialization change, so the preceding focused S3K native evidence is retained.
Both policy scanners pass. Candidate's private producer checkout now points to
c733354; the user-owned physical checkout and shared native main worktree are
untouched. The latter has advanced independently and must be coordinated at
final native integration. No task branch or destination push has occurred.


V29 completed at 10:17:57Z: one test, zero assertion failures, one timing error,
zero skips. It again reaches AIZ3 row 6443 y as the first movement disagreement
and stops before raw 17906. The bounded observation shows both queues empty and
title SLIDE_IN unchanged at every row 17895–17905, with no prepared phase.
This rules out pending resource work as that hold's immediate gate. Code review
finds initializeBonus retains inLevelGameplayOwnedExternally from the preceding
in-level card, so updateWithPlc omits all of the new card's children. Normal and
deleted-owner initialization already reset it. The bonus path now resets it too,
matching loc_60DE's old-SST clear and loc_62B6's new title installation. V32
combines a ROM-free prior-external-owner-to-bonus animation regression with the
whole external native-capture observation. No frame counts or captured values
are used to make production advance.


V30 completed at 10:24:28Z: 34 tests, three assertion failures, no errors/skips.
All 26 live-rewind owner checks (including attached-driver rejection and normal
installation after detachment), immutable title selection, and all four
completed-load publication tests pass. The independent first returned gameplay
row now exposes expected clock 8666 versus actual 8778, a 112-tick excess.
That assertion is retained while V33 samples results and load-phase boundaries;
no fitted subtraction or counter import is permitted. Bootstrap seeds its initial
V5 counter, so a prior special-stage masked interval is a hypothesis, not an
attributed cause yet.

Both ending witnesses still fail with pending physical modules. The earlier
presentation-flag explanation did not identify the pending producer. Their
failure now reports actual module descriptors and the original ending-terrain
job's ready/claimed/retired state. No additional test relaxation or production
change is made pending that evidence. V33 runs those observations; V31 checks the
inventory correction and launcher cleanup; V32 checks bonus child ownership and
whole-Mega progress. All remain under the shared queue. Canonical files are still
external, and broad smoke/integration/push/cleanup remain open.


### Final bounded qualification and remaining frontiers

V32 completed at 10:36:29Z: nine tests, one whole-run assertion failure, no
errors/skips. All eight bonus-title state-machine checks pass. The real second
Slots title now advances and prepares data decode at raw 17905, precisely the
captured boundary. The longer observation reaches segment 8 and stops at raw
25811 on an unexpected production reload (generation 11, progression/ROM zone 0,
act 1). Its two failure axes are that ownership loss and the previously observed
AIZ3 row 6443 y disagreement (2589 groups). This advances the frontier; it does
not qualify all 21 loads or attribute the later reload's cause.

Matched updated baseline v2 on 911513142 completed at 10:56:45Z: three tests,
one Mega failure, no errors/skips. The same external inputs/payloads fail the
earlier Slots stage_exit boundary. Both original ending tests pass there.
V33 on the candidate completed at 10:50:20Z with three assertion failures and no
errors/skips. Its ending diagnostics identify terrain parent 0 as claimed and
physically retired; pending parents 1–3 are later scene art. This is consistent
with loc_5D8BC setting routine 2 (sonic3k.asm:123677-123682) and then queuing
EndingMasterEmerald/SonicPlane/SonicPlaneEnding at loc_5D9B8's tail
(sonic3k.asm:123744-123755). A presentation-mode or whole-FIFO predicate therefore
forbids work the initial Process_Sprites itself performs. The permanent check
now requires a new, physically retired terrain batch before any director
progress, even before the presentation flag changes. V34 passes both GameLoop
and recording cases at 11:06:08Z with no failures/errors/skips.

The independent clock excess predates the represented Level load: the engine
starts it at 8649 against native 8537 and returns at 8778 against native 8666.
Both intervals are 129 ticks; the incoming and outgoing errors are both 112.
A separate read-only GpgxHost frame-end sample validates 10,000 contiguous original
inputs. Between AIZ row 577 and load row 8685 it observes 7994 one-tick rows,
113 zero-tick rows (2890–3002), and one two-tick row (3003), all exceptional rows
in native mode 0x34. Their net loss against one tick per row is 112. The
SpecialStage routine masks interrupts at sonic3k.asm:10621-10628 before its own
initialization, outside the current Level-phase producer/consumer scope. Neither
that diagnostic counter stream nor a fitted 112 correction enters production.

The native sample CSV is 196649 bytes, SHA-256
`cb4bb3423aa23a878777d1fb920b7776d7c3e7e383032bfd1d9d295f8d6ce9dc`;
its small read-only probe source is SHA-256
`23336b2f8a773496f2d3c009f41e9a4bb958aa6f1e899588af721cb91f6cb3af`.
The original BK2 is 130155 bytes, SHA-256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`;
the locked-on ROM identity is the required CFBF98C36C776677290A872547AC47C53D2761D6.
Sampling uses official BizHawk 2.11, explicit inputs, no display, no memory
writes or new execution callbacks, and an external task output. The marker and
per-frame samples agree at the results/fade/load boundaries.

V35 qualifies the new load's interval without claiming the unrelated startup
is fixed: one test passes at 11:11:23Z, no errors/skips, explicitly reporting
before=112, after=112, nativeLoadTicks=129. The earlier absolute-clock assertion
failure remains recorded above. All six prepared load phases and all dedicated
readiness edges reach their real return boundary; no global clock reseed is used.

The exact two user-approved Mega companions are now installed byte-for-byte.
Permanent tests pin their stored lengths/hashes and retain first-three-load
seed/release/placement observations. All three temporary replay probe classes
and their debug-only gap/mode hooks are removed; the pre-production row sample
and permanent observation seam remain. V36 checks the canonical Mega cases and
literal publication bytes; V37 checks short S1/S2 return boundaries through the
shared lifecycle. Broad smoke/guards, integration, pushes and cleanup remain
open; source is still uncommitted on 911513142 with native pin c733354.


V36 completed on the canonical installed fixture at 11:24:54Z (54.456 seconds
execution, shared-queue waiting separate): four tests, three passes, one failure,
zero errors/skips. Both focused Mega witnesses and literal approved publication
bytes pass. The whole replay reproduces V32's exact segment-8 raw-25811 closure
failure and segment-4 first movement mismatch row 6443 y (0x0782 versus 0x078D),
2589 reported error groups. Canonical installation therefore changes neither the
observed later frontier nor the stated limits of the external qualification.


### Broad smoke attribution and synchronous lifecycle compatibility

The first complete change-based smoke selection used the original integration
base `32d4e9aae457b8aae00abd67d172a530a75d6c71`, two ordinary workers and
surfaceless EGL. It completed all 90 ordinary batches and the guard batch on
uncommitted `911513142`, source fingerprint `d827d5e9f28a`: 37733 tests,
135 failures, 37 errors, 49 skips (25 allowed, 24 unclassified). All 834 guard
tests pass, without errors or skips. Ordinary execution was 1590.46 seconds,
guards 166.35 seconds and preparation 38.33 seconds; shared queue waits are
separate and not included in those execution figures. This is a failed bounded
smoke run, not a full-suite pass.

Matched baseline `911513142` reproduces the four public bonus-entry oracle
failures, the existing HOLD_BLACK handoff failure, the fixed audio-reference
manifest digest failure, and one fresh-RNG assertion. The same baseline passes
fresh-boundary rewind, all 26 sanctuary-pedestal cases, ending transition
presentation and the source-overload sentinel. The candidate's new failures in
those areas therefore block integration. A read-only live probe also reproduces
the baseline bonus-request freeze; it supplies no reason to change bonus
admission merely to clear those inherited failures. Its temporary class is removed.

The continuation had been selected even without an explicit phase client. That
replaced existing ordinary rewindable title boundaries with a retained load
whose closure state is deliberately not snapshottable. Preserve the default
synchronous lifecycle and select the production continuation only when its
phase-admission interface is explicitly installed before loading. This selection
consults no capture row, event, comparison field or gameplay value; ROM operations
remain selected by the profile before the admission client can hold them. The
existing synchronous pipeline and its title-boundary snapshots remain the live
path, whose exact loading time is still outside this qualification. This avoids
inventing the rejected second snapshot framework.

Two local corrections accompany that boundary: the title-scan unit fixture now
stubs the extracted `beginTitleScan` seam, and ordinary ObjectManager setup no
longer reads a native-prefix clock it never uses. Focused v40 qualifies those
changes and the newly failing real boundary/sanctuary cases. Broader failing
class attribution is queued on the same baseline; neither an unattributed
failure nor the unclassified skips are reported as green.


V40 on `911513142` runs 73 cases: 72 pass, no errors/skips, with only the exact
baseline D01 title assertion remaining. It clears the new title-scan mock,
source-overload sentinel, fresh-boundary camera/rewind, fresh title PLC,
AIZ result-title graph and all 26 sanctuary-pedestal cases. The intended event
rewind class was misspelled in that selector; it was not run and is included by
its concrete name in V41 rather than counted as qualified.

Matched baseline V5 completes the other 49 originally failing classes: 434 cases,
81 failures, one error, zero skips, at 15:32:25Z. Across that selection and V3,
87 of the initial candidate's 172 failed cases have exactly matching baseline
type/message after worktree-path normalization; three messages differ and
82 cases pass on baseline. Raw totals alone are not the integration gate.

The candidate is reconciled onto develop `5627fc64affb01002c31f60c6e4485c85a62baee`.
Preserve upstream's pending-bonus request exemption from host freeze, destination
player/title boundary and control-lock ownership, and observed lag-service census.
Caller source presentation is retained through the phase-prefix callback; the
synchronous destination boundary is installed only after an immediate load,
while an active production continuation retains its real assembly. The upstream
emerald ledger move replaces the duplicate task-local move/helper. The immutable
title policy is extracted from LevelManager to its own package-private record so
the combined ownership stays within the frozen manager budget. No budget is
raised for the composition.

The reconciled source builds/packages with tests skipped at 15:35:24Z (1m49s).
V41 selects all original failing classes plus phase/readiness and required S3K
regressions (82 concrete classes, existence checked); it is queued. Fresh guards
and focused canonical/R6 checks still follow. The exact approved fixture bytes
remain unchanged. Native producer c733354 is already pushed to TraceChaser main;
the engine remains uncommitted/unpushed. Owned stash
`35bcd3287b0deb8112b729c88a5666da8f01d35f` preserves the pre-reconciliation input
until accounted cleanup, alongside the earlier task stashes.


## Final delivery qualification, 2026-11-04

Final executable source is based on `5627fc64affb01002c31f60c6e4485c85a62baee`.
The default synchronous path retains its existing rewindable title boundary;
an explicit production phase-admission client selects the resumable operations
before any event is consulted. Both adapters share the same load/title scan.

The upstream full-tail comparison exposed two actual ordering omissions. The
native prefix now clears structured player-slot state at loc_60DE
(sonic3k.asm:7649-7651), reusing the ordinary boundary's existing operation.
Title initialization and its art submissions run on the first actual object
scan of loc_62CC (sonic3k.asm:7769-7780), after that loop's synchronization wait,
rather than on the display-prefix resume. A continuation test pins that separation without
any movie rows or counter values. No artificial delay or fitted counter price
was added. The source-tail witness observes either already-compared full tails
or the retained-tail phase; it does not remove comparison coverage.

V43 first exposed stale player fields on the newly compared tail. V44's native
slot clear removed those field errors, leaving four queue fields at each
first-title boundary. V45's scan-owned initialization removes those errors too.
V45 runs 25 cases: 24 pass, only the whole campaign remains red on its later
frontier. The timing-coordinator cases also prove registry capture/restore
rejects an active readiness gap before another owner is touched. The final
bonus-title source presentation helper is extracted into an immutable boundary
record; the root dispatcher budget is lowered to 90 and its class budget is
not raised.

Final bounded checks, with JDK 25, Lua 5.4, original verified ROMs and
surfaceless EGL:

| Check | Observed result |
| --- | --- |
| Change-based smoke from `32d4e9aae`, source `d827d5e9f28a` | 37733 cases, 135 failures, 37 errors, 49 skips; failed. 24 skips are unclassified explicit opt-in diagnostics/inputs. This is the pre-repair run, not a final green suite. |
| V41 original failing classes plus phase/readiness/required S3K regressions | 686 cases, 36 failures, one error, no skips. Five failures are the stale fixture assumptions subsequently corrected; the remaining 32 match baseline. |
| V42 corrected fade/handoff fixtures | 19 pass, no skips, 15:47:04Z. |
| V6 matched remaining methods on updated baseline `5627fc64a` | 53 cases, 31 failures, one error, no skips. All 32 remaining candidate failures match identity/type/assertion after removing object identity hashes and set display order only. |
| V10 fresh `-Pguards test -B` after extraction | 834 pass, no errors/skips, 16:16:58Z. |
| V46 `-Ptrace-replay-r7`, both permanent Mega prefix methods | Two pass, no skips, 16:20:15Z. Source terrain receipts, all compared prefix fields, 22 fade ticks, Gumball seed 5528, release clocks 5529/6957/9097 and native placement are checked. |
| V47 `-Ptrace-replay`, S1/S2 return prefixes plus ordinary boundary controls | 12 pass, no skips, 16:21:40Z. |
| V48 existing independent Sonic-and-Tails prefix | One pass, no skips, 16:26:56Z; native Level interval 129, incoming/outgoing clock errors both +112. Temporary class removed afterward. |

The remaining ordinary failures are inherited in these classes (case counts):
`TestEditorProjectLifecycle` (1), `TestCnz2CutsceneButtonInstance` (1),
`TestSozEndBoss` (1), `TestThreeIslandsScene` (8),
`TestPrivateLobbyScreenRendering` (1), `TestLrzSszCheckpointRoutes` (1),
`TestS3kCnz1MinibossCompletionEdge` (5), `TestS3kFreshLevelRngState` (1),
`TestS3kIcz1PhysicalLowerPlatformReplay` (1), `TestS3kMhzAct2AuthoredRoute` (3),
`TestS3kMhzAct2EntryHeadless` (1), `TestS3kMhzAuthoredRoute` (1),
`TestS3kMhzTailsAuthoredRoute` (1), `TestS3kMhzTeamAuthoredRoute` (1),
`TestS3kMhzWideAuthoredRoute` (1), `TestDezIncomingFinalRouteCapture` (2),
`TestIcz2ColdCubeBridgeRoute` (1), and `TestS3kCompleteRunReferenceProducer` (1).
No unrelated route, mod, graphics, editor or audio assertions are relaxed.

The updated baseline's whole Mega walk fails at a duplicate/reordered service
boundary at raw_frame 1124 after entering AIZ3, whose first live mismatch is
row 105 x; its returned-AIZ tail also reports 12 queue error groups from row
2025. The candidate's compared first-three-load prefix is clean. Its whole walk
reaches physical 25811 before segment-8 ownership fails, and AIZ3's first
movement mismatch is row 6443 y, with 2589 error groups. This is later newly
reached coverage, not campaign certification. The independent SpecialStage
startup still has the separate net 112-service gap described above; neither a
counter reseed nor an emulated hardware model is introduced.

Both exact user-approved compressed companions remain unchanged. Producer
`c73335417979e2cc4303d25d9fc948bba370badc` is pushed to TraceChaser main, with
repository policy/history checks passing and its owned task checkout/branch
cleaned. Engine source is committed as `aba50b4f7dab48ba13833a4449810db36a85b636`; destination
integration and accounted worktree/stash cleanup follow this qualification. Raw category diagnostics are acknowledged after recording
these outcomes; they are not archived as pass receipts.
