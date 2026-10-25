# S3K HCZ act 1 — Sonic route coverage

Canonical game/zone/act: Sonic 3 & Knuckles, Hydrocity, act 1.
Runtime slots: zone 1, act 0; outgoing production reload: zone 1, act 1.
Representative configuration: native 320px Sonic with CPU Tails, movement donor off.
Historical raw-width full routes cover320/400/512/640/800px crossed with off/S1/S2
donors. They retain their original Sonic+CPU-Tails fixture, including S1 Tails
without donated Tails art. Current short production entry/reset checks resolve
320/352/400/528/800 presets and use an explicitly supported Sonic CPU for S1,
Tails for native/S2, with actual donor ROM renderer/animation and current main/
camera ownership. Legacy512/640 geometry remains separately labelled. This is
bounded configuration/route evidence, not full level certification or trace parity.

## Obligations and current evidence

| Obligation | Test / lane | Evidence and limits |
| --- | --- | --- |
| ENTRY / ROUTE / WATER | `TestS3kHcz1RoutePilot`, ordinary `slow-suite` | Fresh production start, recorded input prefix followed by ordinary live-state steering; water must be seen; P1 death and drowning pre-death forbidden; CPU identity/controller/leader/dead-streak audit; production act-2 reload required. |
| BOSS composition | Same complete route | Six live miniboss hits, defeat and production reload. This does not cover every attack/phase independently. |
| REWIND / local hazards | `TestS3kHcz1RouteRewind`, ten independent cases | Water entry, bubble shield, both early bridges, fan lift, conveyor ride, spring ascent, boss active/hit/defeated. Each reaches its own live spot, captures all registered owners, advances 30 ordinary-input frames and checks immediate restore plus forward replay twice. |
| LOAD / fresh live handoff | `TestGameLoopFreshLevelHandoff`, ROM-backed focused lane | Real fade callback retains PALETTE_FADE and defers four title archives to the next iteration; recording and live drivers restore destination slots before the no-VInt initial sprite pass and ordinary gameplay. Pause and two consecutive loads are covered. Pending fresh transitions exclude rewind; broader viewport/donor/team handoff coverage remains open. |
| LOAD / timeline boundary | `TestS3kHcz1ReloadRewind` | Actual act-2 reload replaces object ownership and clamps live rewind history to the new root; 30 new-act frames restore/replay twice. Checkpoint/death-restart remains open. |
| Configuration entry and reset | `TestS3kHcz1EntryMatrix`,30 production cases plus6 legacy geometry cases | Five production presets × off/S1/S2: actual movement admission, two entire-world30-frame replay cycles and two repeated production resets. Actual donor-supported CPU art/chain, main/camera focus and configured/resolved width are checked after restore/replay/load. Separate512/640 rows preserve the original custom geometry/reset assertions. Exact scoped qualification below; additional supported main/team and death/checkpoint products remain open. |
| Full-route configuration axes | `TestS3kHcz1CompatibilityRoutes`, 15 cases | All five historical raw widths × off/S1/S2 complete with ordinary pad inputs, no P1 death/drowning, CPU lifecycle checks, six-hit defeat and actual reload. Other main characters/teams remain open. |
| REWIND / horizontal arena admission | `TestS3kHcz1RouteRewind`, five independent width cases | Capture before the horizontal lock; 30 saved-input frames must cross it, then immediate restore and forward replay twice must match all registered owners. Native ROM min/max X bounds remain `3680`. This late checkpoint uses donor off; donor entry replay is covered separately. |
| Arena trigger boundaries | `TestHczMinibossRomParity` | Initial camera window and horizontal admission checked on both sides at all five widths, retaining vertical-gate independence and native world-bound writes. |
| PRESENT / ORACLE | Separate trace/native lanes | No pixels/audio or trace comparison is claimed. |

## Stock trace handoff verification — 2026-10-07

The [continued stock audit](../../audits/2026-10-07-stock-parity-gap-verification.md#continued-swarm-from-the-delivered-base)
keeps strict trace parity separate from the ordinary routes above. A temporary
probe found an ordinary player pass on input 53,607, before the advertised
HCZ window at input 53,608. Moving comparison attachment earlier was rejected
because it would bind the wrong input. The candidate uses the existing shared-gap
owner for that unrepresented row and retains comparison of every advertised row.

The executed candidate's final focused startup/gap selection passes 66 tests with no
failures, errors or skips. The complete chain compares all 3,574 HCZ rows and
moves the first mismatch from row zero's fractional Y to row 653's Tails Y,
reducing 32,343 errors to 563. A standalone HCZ replay independently reports the
same first mismatch; its one compressed mismatch entry spans 75 rows, rather
than proving that only one row differs. The giant-ring exit remains missed.
The final guard forbids suppressed gap stepping at the advertised input
boundary, with a real-ROM denied-admission regression that leaves the cursor,
physics and level clock unchanged. Local commit `45c6eed2d6e3` retains the same
563-error profile after that guard. The subsequent raw-controller bar correction
(`b3eff6209ed9`) passes 75 focused cases with all mandatory startup controls;
standalone HCZ has zero compared errors. The chain removes exactly the 75 early
Tails-Y observations and retains 488 errors (402 physics, 86 animation), with
the first animation difference at row 3,531 and first physics difference at
row 3,532 primary X `1457/1452`. The native giant ring captures Sonic while the
engine lacks that ring; the collected-ring mask's native save boundary is under
investigation. All 3,574 rows and 55 lag rows remain accounted for. Combined
delivery qualification remains open. No broader
viewport, donor, character/team, native-pixel or audio coverage is inferred.

## Stock fan-child follow-up — 2026-10-08

The [round 3 lane audit](../../audits/2026-10-07-s3k-parity-gap-verification.md)
qualifies four local fan corrections at `fa2ac5e67`: one `MoveSprite2` per
bubble pass (eight pixels upward), water-owned retirement without a camera/age
cap, gameplay-counter sound/spawn cadence, and lowest-slot allocation before
randomness. Twelve fan regressions, graph restore/replay, sixteen conveyor
controls and mandatory startup consumers pass in a ninety-case focused run,
without failures/errors/skips. Native sampling separately matches 630
represented frame ends across seven player/camera/clock fields; 11900 bubble
updates retain the native eight-pixel movement outside the camera margin.
This is state corroboration, not a pixel or complete-lifetime certificate.

The normal five-case trace selection preserves the initial HCZ surface at
zero errors (3574 rows, 55 lag), and the two prefix controls pass. Returned-HCZ
standalone remains an independently matched inherited 3454-error failure,
first row0 Y speed `0000/000E`. The complete chain's returned segment11 remains
incomplete with 82067 differences (69393 physics/12674 animation), first
mapping row1507 `0063/0095`, first primary Y row1510 `07D6/07DF`, then LEVEL
ownership loss at cursor68801. Earlier AIZ6/8 profiles remain unchanged.
The fan corrections do not close that route, establish conveyor causation,
or expand the historical viewport/donor/character/team matrix above.
Root composition `84f0c20f11` passes 275 focused cases. Its combined17-case
normal trace selection retains these exact S3K profiles, with four qualified
assertion failures and no errors/skips; ordinary/guard delivery remains pending.

## Route construction and rejected approaches

Task `route-green-20260914`, pinned integration base `f1843f54a1`; isolated
HCZ development tree `.worktrees/ai-hcz-route-green`. No production state is
hydrated, no trace lag rows resample input, and no physics constants change.
Trace metadata identifies the source movie and input-window boundaries only.

The inherited recorded-only pilot died at frame 3,478. A previous comparison
survey found a hardware-cadence difference earlier, at row 1,163; that was not
proof of a water-physics defect. Live ordinary-input decisions now handle the
fan ledge, monitor alcoves, paired bridges, breakable bars, loops, conveyor
ascent and miniboss instead of assuming the recording remains synchronised.

- The collapsing bridges in the two alcoves use paired Blastoid trigger state.
  Waiting on their centres cannot substitute for defeating the paired enemy.
- A jump must remain held through the underwater rise or bubble rebound; an
  early release clips height. Breakable bars and conveyors need fresh press
  edges when their live owners accept them.
- The dry loop needs the lower branch and a right-facing charge on suitable
  ground. Charging on the rollback slope or auto-jumping before charging
  prevented the intended route.
- Repeated attempts to reach the late spring from beneath its support block
  hit the terrain ceiling. The working path presses the fan button, uses the
  conveyor to approach the spring from above, then takes the upper run-up.
- A later conveyor lock was a consequence of drowning, not an input-publication
  defect. `isDrowningDeath()` is now checked alongside `getDead()`; the reusable
  lesson is in [implementation pitfalls](../../implementation-pitfalls.md).
- A generic jump-held flag updated before the boss override suppressed the
  authored boss jump edges. The fight needs its own correct emitted-input
  history and side/above-core approach; the engine beneath the core is hazardous.
  The successful relative side approach landed all six hits with nine rings
  remaining, without changing boss behavior.

The successful pre-cleanup route reached the production act-2 reload at
**frame 12,583**, with water observed and all assertions passing: **1 case,
zero failures/errors/skips**, 24.26 seconds Maven wall time. Command:
`mvn -Dmse=off -Dopenggf.hcz1.pilot=true -Dtest=TestS3kHcz1RoutePilot
-Ds3k.rom.path=/absolute/path/to/the/existing/rom.gen test`.
The cleaned controller also passed the same single case with no skips in
24.81 seconds. It parks the recording cursor at row 1,997 and uses named
stages and ROM-loaded placement landmarks. Its emitted-pad diagnostic hash
(`-3592398407471656479`) matched the successful pre-cleanup route exactly;
the hash is evidence of cleanup equivalence, not a test assertion or runtime input.

## Original pilot validation scope

The original pilot delivery used proportionate focused validation for private route controllers,
plus the affected CI argument/guard checks. The existing broad runner selection
is disproportionate to these bounded test-only and branch-context changes.
A green route means the assertions above passed; it is not a full ordinary,
full structural-guard, full-level matrix or trace-parity certification.

## HCZ rewind and configuration follow-up

Task tree `.worktrees/ai-hcz-route-coverage`, pinned develop base
`14d902d3900104108b3f550d6f8d4841deddabc3`. The extracted `Hcz1Route`
keeps input decisions in test code and never writes player or trace physics.
The first unchanged-native extraction retained the original 12,583-frame pad hash.

The new rewind checks exposed multiple issues. Persistent shield visuals
can share slot 100, so the snapshot comparator must pair stable object identities
and compare every field. The CPU also retained a destroyed bridge contact that
could not be recreated as a live owner; recording its released-contact provenance
preserves the next-frame decision even after slot reuse. Tests reject dropped or
duplicated identities and accidental relinking to future or nearby contacts.

Strengthening the bridge selectors from route approach stages to live triggered
bridge owners moved their capture points to frames 3,124 and 4,204. Both then
exposed pending explosion factories lost during recreation: missing animal/points
children changed allocation order and RNG during the 30-frame replay. The explosion
snapshot now retains the exact configured factories and all initialization/animation
state, while restored services supply the live renderer and child ownership.

The first completed focused verification passed all ten native rewind spots,
the production reload and native route, plus nine candidate API signature checks:
21 cases, no failures/errors/skips. Separately, all 30 entry/reset matrix cases
passed. These are observed focused results before the subsequent route-controller
breadth refinements; final combined verification is recorded in the
[handover](../../plans/2026-09-14-route-controller-handover.md#integrated-develop-verification).

Wider viewports change activation timing. Temporary controller experiments showed
that preserving momentum between early water hazards and attacking the paired
Blastoid directly can complete 320/off, 400/off, 512/off and 320/S2 routes. These
are survey results, not maintained full-route coverage. Predictive upper steering
and a Down-only roll brought the S1 donor beyond the bridges, but its final curve
still lacked a successful ordinary-input run-up. At 800px, the boss can appear
before the camera lock; walking into the current drowned and repeated jumps
stalled on the upper terrain. A 640px probe reached that boss boundary with the
original upper-bridge steering; alternative bridge approaches were not consistently
safe. No viewport runtime defect or physics change is inferred from these probes.

The delivery retains the established native controller rather than committing an
unfinished multi-configuration controller. Full-route breadth remains explicitly
open; the committed cross-product coverage exercises entry, replay and repeated
load/reset behavior. The handover records rejected navigation approaches so the
next route-authoring task can start from the observed frontier.

Shared rewind state and comparator changes require normal combined change-based
validation. Entry/reset breadth and selected complete routes do not certify HCZ1's
remaining character/team, checkpoint/death, presentation or trace-parity gaps.

## Full-route viewport/donor completion

Follow-up based on `24cdc64e6`, implemented in
`.worktrees/ai-hcz-full-route-matrix`. The maintained matrix now passes all 15
configurations (zero failures/errors/skips), 61.251 test seconds / 1:18 Maven time
excluding queue waiting. This supersedes the earlier temporary survey and open
full-route breadth statement above. Completion frames for the combined controller:

| Width | Donor off | S1 donor | S2 donor |
| --- | ---: | ---: | ---: |
| 320 | 13,007 | 11,500 | 13,007 |
| 400 | 11,298 | 13,151 | 11,298 |
| 512 | 11,831 | 14,773 | 11,831 |
| 640 | 11,386 | 14,294 | 11,386 |
| 800 | 10,909 | 11,375 | 10,909 |

These are observed completion frames, not deadlines or runtime inputs. The
20,000-frame watchdog is unchanged. The controller authors a running early-water
corridor for 400/512px and retains the jumping approach for other widths. S1 uses
its actual no-spindash capability: longer running approach, live obstacle jumps,
and (in wide views) landing left of the upper Blastoid to attack before the ring
monitor. Boss steering distinguishes actual engine retraction from alternating
V-int flicker; off/S2 retain their existing side/above approach.

The only production change converts HCZ miniboss camera observations to native
framing. Wide render origins otherwise cannot reach the ROM arena threshold
before the player meets the terrain wall. The native bound writes remain unchanged.
All five before-lock rewind cases pass, and the original native controller retains
its exact pre-fix completion frame and pad hash with this runtime fix alone.

The matched native trace remains red with 4,699 divergences, first frame 9,482
`air`; candidate and baseline normalized reports are identical. See the
[handover](../../plans/2026-09-14-route-controller-handover.md#full-route-viewportdonor-completion-follow-up)
for commands, rejected approaches and final delivery validation. Other main
characters/teams, checkpoint/death-restart, donor breadth at late rewind spots,
presentation/oracle and trace-parity obligations remain open.

## Full SaveGame consumer boundary — 2026-10-08

Results act-2 completion clears the inherited AIZ mask before HCZ initialization, preserving same-zone seamless loads and special-stage returns. Final combined replay has complete segment9 with zero errors/55 lag and successful giant-ring handoff; standalone matches 3519 executed samples of 3574 rows, zero physics/animation/bootstrap/warnings. Newly reachable segment11 remains incomplete: 82067 errors (69393 physics/12674 animation), first primary Y row1510 `07D6/07DF`, ownership lost at cursor68801 in LEVEL, load generation9, zone1 act0.

`828bc94d8` clears the native32-bit collected-ring mask at exactly seven existing
full-SaveGame gates; existing game-state rewind owns the mask. Focused138 cases
pass without failures/errors/skips, including direct Results tally/helper and
mask-restoration checks. The other six non-tally live routes, successful disk
persistence, live SK-alone, and this matrix's remaining route/rewind/breadth
products are not newly certified. Generic persistence and special-stage/lives/
death/reload/seamless semantics remain distinct. See the [lane audit](../../audits/2026-10-07-s3k-parity-gap-verification.md)
for native ordering and scope, and [combined qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#final-composed-canonical-replay-2026-10-08)
for exact commands/frontiers. [Actual-main qualification](../../audits/2026-10-07-stock-parity-gap-verification.md#actual-main-delivery-qualification)
retains 27 concrete inherited ordinary failures and 62 literal skips with zero
errors; all 672 fresh guards pass. This preserves the route/breadth limits above.

Composed canonical71207 passes standalone HCZ and complete chain segment9:
3,574 source rows,55lag, zero physics/animation/bootstrap/warnings, and the
giant-ring handoff succeeds. The newly reached segment11 is incomplete, first
row1510 Y07D6/07DF; AIZ frontiers remain unchanged. This does not establish
native pixel, dynamic-art or audio parity beyond the fixture's comparisons.


## Production presets, actual CPU art and repeated reset — 2026-10-14

The [paired AIZ1/HCZ1 cohort record](s3k-aiz1-sonic.md#production-preset-and-actual-launch-profile-entry--2026-10-14)
retains exact source, selectors, environment, ROM paths and all execution limits.
There are 30 mandatory production cases: 15 movement/entry/two-cycle30-frame replay
rows and 15 repeated-reset rows, each crossing the five actual presets with off/S1/S2.
Six 512/640 custom-geometry cases retain entry replay and the complete prior two-reset
contract, including ROM start, actual movement after reset, donor/CPU/contact state
and replacement object ownership. New preset overloads never inject raw width and
use the actual launch applier; old integer full-route/custom fixture initialization
and controller behavior are unchanged. An explicitly supported S1 Sonic CPU has
real donor art rather than treating the old invisible S1 Tails fixture as a
production-compatible participant. The old fixture remains explicitly historical.

The first full two-entry-class cohort passes 57/0/0/0 at 10:05:41 BST; HCZ1 is 36 cases,
60.83 seconds. The affected production-owner follow-up passes 45/0/0/0 at 10:10:56 BST;
HCZ1 is 30 cases, 51.98 seconds. Those 30 current rows plus six unchanged custom rows
account for all 36 HCZ1 identities. The later sole-AIZ immediate-focus follow-up
changes no HCZ1 source/behavior. Camera focus and CPU leader references are asserted
immediately after both production restores, after both replays and each reset;
they are not assumed from a snapshot that deliberately omits camera focus.
Entry/reset is qualified; additional main/team, actual checkpoint/death, per-mechanic
and new 352/528 full-route products, native pixels/audio and trace conformance remain
separate obligations. Combined delivery is lead-owned and pending.

## Native fresh-title ownership follow-up — 2026-10-14

`TestS3kFreshTitleCardSstGraph` independently loads actual AIZ2 and HCZ1
fresh boundaries: absolute SST8 and four following children precede placements,
survive omitted presentation, and obey native wait/retirement order. Its graph
restore/replay checks use the five actual production display presets
320/352/400/528/800, with camera width verified before and after loading, two
independent parent graphs and two complete registry replay cycles. D01 excludes
the ordinary title graph. This is a bounded native entry/graph contract; it does
not qualify the older route width inventory above or rendered pixels/audio.
The [audit](../../audits/trace/2026-10-14-s3k-fresh-title-sst.md) records commands,
negative evidence and the unchanged original AIZ/initial HCZ controls.

## Explicit JUnit obligation pilot (2026-10-15, candidate)

The standalone [pilot](../../../../tools/testing/check_route_obligations.py)
binds the existing AIZ1 and HCZ1 representative route, selected two-cycle rewind
and outgoing act-reload checks to an [exact expected inventory](../../../../tools/testing/aiz-hcz-route-obligations.json).
The 26 cases are independent AIZ1/HCZ1 checks, including HCZ horizontal-admission
rewind at the five widths already named by that test; they do not constitute a
continuous AIZ-to-HCZ route or certify the remaining act/configuration products.

```sh
python3 tools/testing/check_route_obligations.py --preflight --rom s3k=/absolute/path/to/existing/s3k-ROM.gen
python3 tools/testing/check_route_obligations.py --run --rom s3k=/absolute/path/to/existing/s3k-ROM.gen
```

The first command checks the canonical original-ROM SHA-1, committed input
fixtures and current source class/method references without executing tests.
The second also checks Java 25, queues exactly the six inventoried classes,
removes only their required `slow-suite` exclusion, verifies fresh XML case
identities and removes the temporary reports even on failure. No opt-in breadth
properties are needed by these six classes. A missing, skipped, failed,
duplicate or unexpected selected identity fails the lane. Existing reports can
be inspected with `--reports DIR`; the caller must supply fresh reports from
the exact tested build, command and fixtures. The tool does not infer their
build identity or freshness. Enum inventory bindings accept plain constants
only; constructor arguments, bodies and other unsupported declarations fail
closed before Maven. Integer sources bind to the selected method's own annotation,
ignore comments/literals and reject unsupported expressions. Ordinary developer skips and unrelated report identities remain
unchanged. The general LTS-04/LTS-05 migration remains open.

Observed on the remediation candidate at base `1f612ff7271d175ea17eb775e786845d5b2ee12a`:
all **26 exact pilot cases passed**, with zero failures, errors or skips
(Maven execution 93 seconds; queue wait is recorded separately). This qualified
the parameterized XML identities. The later annotation-binding hardening retains
those same identities and passes 24 Python safety checks plus canonical
preflight. See the [task record](../../plans/2026-10-15-architecture-debt-remediation.md#runtime-validation-handoff-2026-10-16)
for commands and limits. This is bounded pilot evidence; remaining matrix
obligations and general enforcement stay open.


## Retained HCZ handlers and incoming act-2 carrier — 2026-10-17

Worker source `b9c1c8b3d` composes final hand/chopper/miniboss/drowned/monitor
contracts. `TestSonic3kHCZEvents` checks stored swing targets after camera motion,
current/target X-lock ownership, and real manager/player/camera restore twice
with forward release replay; `TestSonic3kLevelEventRewindSnapshot` and
`TestZoneEventRewindSchemaGuard` cover both distinct captured target words.
Focused1591/0/0/4 includes broader consumers, not 1591 HCZ products; four external
authored-AIZ JAR cases skip. Named guards113/0/0/0 pass. The hard-coded GLFW
pixel oracle fails before scene verification; native-only original 320 Sonic+Tails
images do not replace engine pixel equality or additional viewport/donor/roster
products. The [audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#final-single-invocation-strict-qualification)
records the three assertion-red qualifiers, summary-only returned-HCZ7134 row,
missing exit, load7126, unqualified slice spans and absent auxiliary coverage.
Cold traversal, full product breadth, boss and cross-load/respawn obligations
retain their inherited gaps; Root owns combined delivery validation.


## Fresh load and title submission ownership — 2026-10-17

The same worker's followup after866 adds a short real-load test for Level's ten HCZ1 Nemesis descriptors before title dispatch, first-loop preparation, next-VBlank six-pattern drain and twice-restored queue/job ordinals. Old-red4/3F becomes4/0F/0E/0S. Focused134 plus source/authority/schema guards131 pass without skips. Canonical load-prefix through the first segment9 row passes, including complete segment8 with its prior7126 position/camera errors gone; the alternate BK2 still has exactly26179 `rings`72/0. [Evidence/commands/native observations](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#bounded-followup-qualification-and-limits).

This covers the native320 Sonic+Tails load owner and short semantic replay, not cold-route traversal, an engine pixel product, cross-load timeline policy or the full matrix. Matched pre-fix slice totals4699 ondc338 and4353 on866 include mixed span deltas and the same9482 frontier; historical3738 is not attributed. Full new-source HCZ replay, other viewports/donors/teams and the exact GLFW oracle remain open.


## R3 full strict qualification on Main460 composition — 2026-10-17

Worker merge21327d78a: canonical3815 is6/1F/0E/0S, with all three prefixes,
standalone AIZ and initial HCZ passing. Returned HCZ11 consumes14208 reference
rows,134710 field mismatches(115689physics/19021animation), first reported
non-camera6674 `rings`114/0 and missing giant-ring exit. HCZ90977 is2/1F/0E/0S:
full31482 reference rows,29302 comparison records,4353 spans(3424physics/929animation),
first9482 `air`1/0, first animation9486; independent Poindexter passes. The entire
slice error array matches the prior4353 result. Matched4699/4353 deltas remain
mixed;3738 is unmatched. Alternate760's one26179 ring span is reused, not rerun.
No new entry, viewport/donor/team, pixel, cold traversal or cross-load product is
certified. Active-level ring-reset/title-latch ownership, later route exit and
the exact GLFW oracle remain unqualified. No new runtime fix. [Exact source,
commands, windows and rejected load attribution](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r3-current-composed-strict-qualification--2026-10-17).


R4 title-owner follow-up (2026-10-17): HCZ's carried results SST now receives
its native child-movement gate through the existing request/carry contract.
Short real allocation-hole/latch tests recreate and replay every registered
world key twice; focused182 and guards131 pass without skips. Native slot15's
last child movement69754 precedes clear69755/reset69756. Canonical2015 moves the
first reported non-camera frontier6674 rings→7134 sidekick_x0175/0176, with
133771 field mismatches over the same14208-row returned-HCZ window and missing
exit. Independent full HCZ65594 retains exactly4353 spans/first9482air, Poindexter
passes; earlier divergence prevents a second clean repaired-owner claim. No new
entry, product breadth, engine pixel or cross-load timeline certification.
[Exact evidence and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r4-retained-title-parent-and-child-movement-latch--2026-10-17).


### Returned HCZ8329 skim logical input — 2026-10-17 candidate

The existing HCZ skim owner now consumes the logical A/B/C pressed edge rather
than held/raw input (`loc_385D2`). Independent held/noedge and logical-edge/raw-
neutral controls are genuinely old-red2/2F then pass in focused80/0F0E0S,
including curve exit, native P2 and two registered-world owner-phase replay
continuations; fresh authority25/0F0E0S also passes. These are declared component
inlets at arbitrary water200, not cold traversal or new viewport/donor rows.
One unchanged originalAD40 boot prefix directly observes active skim8328,
ordinary exit8329 with Yspeed0 and no falsejump, then native-matching8330 velocity.
Its live8331 cutoff is still206 errors=11camera-physics+195animation; the prefix
itself fails an unsupported extra animation0 expectation. Existing bounded
R10/R11 evidence already records195 with the same first3475 sidekick17/1C,
without proving full vector identity. No corrected prefix was run. One separately
authorized original maintained canonical49197 is1/1F0E0S: returned HCZ11 completes
14208 rows with26153=23312physics+2841animation,0warnings/bootstrap,lag59, first
non-camera8717 sidekick_air1/0 (previously8329 y_speed0/-680). It crosses the
manifest giant_ring→ss_6→hcz_3 boundaries, then loses ownership at BK2cursor88306.
The newly reached hcz_3 is incomplete,63295=54642physics+8653animation,first287
y06D4/06DA; no route certification follows from reduced counts or later progress.
Earlier six gameplay windows remain complete-zero. No further probe/fix follows. See [exact evidence and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#canonical-returned-hcz8329-water-skim-input-owner--2026-10-17).

## Continuous AIZ-to-HCZ load qualification — 2026-10-25

`TestS3kSonicTailsCompleteEmeraldRunPrefix#aiz1ThroughHczLoadBoundary`
now defends one uninterrupted native 320px Sonic + CPU Tails replay from AIZ1
through the first HCZ row (segment 9, one row consumed). It retains the original
movie, all predecessor comparisons, timing admission and parity assertions.
On `f7620e579` plus the narrowly adapted fresh-load correction, completed segment 8
covers all 7,175 represented rows, including 62 lag closures, with zero physics,
animation, bootstrap errors or warnings. Its former four row-7126 position/camera
errors are gone. The full-chain observation also completes segment 9's 3,574
represented rows, including 55 lag closures, without compared errors.

`Level/loc_6068` and `loc_60DA` submit Nemesis descriptors before the title wait
(sonic3k.asm:7603-7647); `loc_62CC` dispatches the card and prepares the first head
before the next six-pattern VBlank service (sonic3k.asm:7769-7780).
`TestS3kFreshTitlePlcLoadBoundary` checks the real load with live and recording
drivers, ten queued HCZ descriptors before title dispatch, four card-owned KosM
archives, the 56-to-50-pattern service boundary, and two registry restores with
forward replay and unchanged job ordinals. The four cases are independently
old-red (three assertion failures) and corrected-green. The affected focused
run passes 149 cases, including all four mandatory S3K startup anchors, without
failures, errors or skips.

This qualifies the load boundary only. The full canonical chain still loses
production ownership in HCZ1 segment 11 at input 68801, load generation 9:
81,981 errors in the incomplete comparison, first row 1768 `y` expected `$0870`,
actual `$0871`. The strict alternate AIZ recording's trailing ring boundary and
remaining viewport, donor, character/team, outgoing-HCZ, lifecycle and presentation
products remain separate. Exact commands, source fingerprints and continuation
scope are in the [current frontier entry](../../../status/trace-frontier-log.md#2026-10-25--fresh-load-plc-order-qualifies-the-continuous-aiz-to-hcz-load-boundary).
