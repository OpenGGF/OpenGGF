# MGZ event publication — 2026-11-15

Base: `e676eb5086b7069c5e8ffdd0dbd33990d5c2bf5f` (`develop`).
Scope: MGZ only; shared shake implementation unchanged.

## Owners and ROM evidence

- `ShakeScreen_Setup` (sonic3k.asm:104234-104255) owns flag/countdown,
  offset and last offset. Negative flags persist until an explicit write;
  positive flags count down, and dying/restarting P1 suppresses samples.
- `MGZ1_ScreenEvent` (sonic3k.asm:106303-106307) and
  `MGZ2_ScreenEvent` (sonic3k.asm:106436-106450) consume the preceding offset.
  MGZ2 also reasserts continuous mode from `Events_fg_0`.
- `MGZ1BGE_Normal` (sonic3k.asm:106331-106355) and
  `MGZ2BGE_Normal` (sonic3k.asm:107100-107122) prepare the next sample.
- `MGZ1_Deform` (Lockon S3/Screen Events.asm:989-1024) reads then adds `$500`
  to its cloud longword. `MGZ2_BGDeform`
  (Lockon S3/Screen Events.asm:1090-1181) adds `$800` before reading.
- `loc_51A04` (sonic3k.asm:107198-107209) latches cloud freeze and clears
  its accumulator. Re-entering state 8 does not clear that latch.
- `loc_51B1C` (sonic3k.asm:107323-107333) writes a timed `$E`, distinct from
  continuous mode. `MGZ2SE_Normal` (sonic3k.asm:106458-106469) arms `$14`
  after its background pass, so that dispatch does not decrement it.
- `loc_3461E` (sonic3k.asm:71011-71020) and
  `loc_346A2` (sonic3k.asm:71053-71059) write the full word `-1` while a
  trigger platform moves and clear it on completion. This explicit clear was
  missing from the survey and engine.
- `Tunnelbot_StartRumbleWait` (sonic3k.asm:184875-184880) and
  `Tunnelbot_SetTriggerAndDelete` (sonic3k.asm:184903-184906) set/clear the flag.
  The miniboss's private sample table was not a separate ROM owner.
- The game-wide shake boolean still has S3K readers in LBZ; MGZ's compatibility
  writes remain. The manager's FBZ-routed flag remains untouched.

## Witness and change

`TestS3kMgzScreenPublication` loads real MGZ and raises an object-style flag.
Drawing the same frame twice changed shake from 0 to 2 on the base.
An initial version stepped an ordinary frame before drawing and passed; it was
not accepted as a red witness. The narrowed witness isolates draw consumption.

Events now own the publication, cloud longword/freeze latch and `S3kScreenShake`.
The runtime adapter has no mutable state. Objects write flags; no private table
is sampled. `SwScrlMgz` owns only buffers and derived picture output. Rebuilding
it cannot advance clocks, clear requests, latch freeze or mutate event state.
Collision consumers read completed event differences independently of rendering.
The former pre-physics background-trigger bridge becomes a read-only callback;
production background dispatch follows objects/camera. The synthetic rise SST
still runs after players, before later objects.

The composed framed same-build payload includes scalar publication, event state,
cloud state and all three shake words. Validation precedes manager mutation;
legacy scalar-only, invalid framing, noncanonical scalars and out-of-range words
are rejected. No migration format is provided.

## Rejected designs

1. Keep captured state in `SwScrlMgz`: would repair some rewind symptoms but
   leave rendering able to advance gameplay and consume requests.
2. Put a second producer/codec in `MgzZoneRuntimeState`: would split the
   existing event schema and flag/cloud/collision publication across restore
   owners. A stateless adapter and composed event payload have one lifetime.
3. Derive clouds solely from a frame index: cannot represent the ROM's persistent
   freeze/clear lifetime and makes first-observation time affect reconstruction.

## Verification and limits

All commands use Java 25. `Q` below means `python3 tools/testing/maven_queue.py -Dmse=off`;
`G` means `-Dopenggf.headless.gl=surfaceless-egl`. Selectors (literal CSV expansions):

- F = `SwScrlMgzTest,TestSonic3kMgz2QuakeEvents,TestSonic3kMgz2BgRiseEvents,TestSonic3kMgz2CollapseEvents,TestSonic3kMgz2EndBossEvents,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kMgzScreenPublication,TestMgzComposedRewindState`
- A = F plus `TestS3kZoneRuntimeStateAdapters`.
- B = A plus `TestS3kMgz2BgRiseHeadless,TestMgzMinibossInstance,TestS3kMgzTriggerPlatformObject`.
- T = `*Mgz*TraceReplay,TestS3kMgzF498AirRollPhysics`.

Results are tests/failures/errors/skips; every admitted command found all ROMs (`missing=none`).
An initial shell redirection failed because `target/` did not yet exist; no Maven ran.

| Command (selectors expanded as above) | Outcome | Log under target/ |
| --- | --- | --- |
| `Q -Dtest=TestS3kMgzScreenPublication test` | 1/0/0/0; ineffective initial witness | mgz-red.log |
| Same command, narrowed witness | 1/1/0/0; drawing changed shake 0→2 | mgz-red2.log |
| `Q G -Dtest=F test` | Compile failed: removed read-only adapter getter; no tests ran | mgz-focused.log |
| `Q G -Dtest=A test` | 226/10/2/0; schema inventory and no-P1 collapse fixtures | mgz-focused2.log |
| `Q G -Dtest=B test` | 266/1/0/0; obsolete forced-renderer-state component fixture | mgz-focused3.log |
| `Q G -Dtest=TestS3kMgz2BgRiseHeadless test` | 13/0/0/0 | mgz-headless4.log |
| `Q G -Dtest=T test` | 12/9/0/0; new 45 grouped Tails-counter errors identified | mgz-traces.log |
| Same T command on detached base | 12/8/0/0 | baseline target/mgz-traces-baseline.log |
| `Q G -Dtest=B test` | 267/0/0/0; includes init/transition setup witness | mgz-focused5.log |
| `Q G -Dtest=B test` | 268/2/0/0; two scrolling fixtures omitted negative ROM flag | mgz-focused6.log |
| `Q G -Dtest=TestSonic3kMgz2CollapseEvents test` | 16/0/0/0 after fixture correction | mgz-collapse7.log |
| `Q G -Dtest=T test` | 12/8/0/0; exact baseline comparison below | mgz-traces-final.log |

Final focused classes all pass at their latest executions; no skips. Do not interpret
this as a full-suite or smoke pass. The two latest fixture corrections supply the
negative scrolling flag; no production predicates or expected accumulator values changed.
The red projection and registered restore/forward witnesses pass.

The intermediate 45 grouped errors came from missing `loc_513FA`'s continuous flag
write after the timed collapse startup. Tails' render visibility then changed its
off-screen counter. Restoring that ROM write removes all 45 errors, without a
fixture/frame/route condition. Collapse now branches on the signed global flag;
a witness covers negative entry without opening initialization. The quake spawn
writes its flag immediately. BackgroundInit and MGZ1 transition draws omit shake
setup; a separate witness covers both and normal timed countdown continuation.

Baseline/candidate use the identical T command, ROMs and concrete test order.
All 11 report owners match, with identical compared rows, planned-row/completion
gates, full grouped error/warning lists and bootstrap counts. Frame-498 also passes.
Counts below are reported grouped errors, not raw row/field mismatch totals.

| Replay class suffix (all start TestS3k) | Base → final errors | First error (unchanged) |
| --- | --- | --- |
| MgzTraceReplay | 0 → 0 | none |
| MgzZoneSliceTraceReplay | 0 → 0 | none |
| KnucklesSuperMgz2SegmentTraceReplay | 3103 → 3103 | 223 rings |
| KnucklesSuperMgzSegmentTraceReplay | 1365 → 1365 | 1131 rings |
| MegaMgz2SegmentTraceReplay | 2 → 2 | 1854 y |
| MegaMgz3SegmentTraceReplay | 919 → 919 | 267 rings |
| MegaMgzSegmentTraceReplay | 529 → 529 | 1516 g_speed |
| SonicTailsMgzSegmentTraceReplay | 762 → 762 | 13848 camera_y |
| TailsFullChainMgz2SegmentTraceReplay | 1549 → 1549 | 82 x_speed |
| TailsFullChainMgz3SegmentTraceReplay | 2902 → 2902 | 692 rings |
| TailsFullChainMgzSegmentTraceReplay | 0 → 0 | none |

`git diff --check` passes. `python3 tools/disasm/disasm_citations.py check --strict`
on staged touched files reports three inherited stale citations in unchanged,
non-MGZ manager comments (original lines 667, 2294, 2297); minimal shared-file
scope leaves them untouched. MGZ-owned files/audit pass strict checking. Four
old Tunnelbot citation locations were corrected in the owned file. The checker
also warns about the unrelated Sonic CD reference checkout; its pin is unchanged.

The lead owns smoke/integration checks and independent review. This lane ran no
categories/full-suite. Wider viewport/donor/team, Knuckles BG movement and native
pixel/audio certification remain inherited obligations. Raw logs/reports stay
under target; the clean detached baseline is retained for review.

Measured queue wait: 3238 s; Maven execution: 1084.030 s. User wait: 0 s.
Development/tool/checkout time was not instrumented and remains unknown.
