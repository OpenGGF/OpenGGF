# S3K HCZ act 1 — Sonic route coverage

Canonical game/zone/act: Sonic 3 & Knuckles, Hydrocity, act 1.
Runtime slots: zone 1, act 0; outgoing production reload: zone 1, act 1.
Representative configuration: native 320px Sonic with CPU Tails, movement donor off.
Maintained full routes cover 320/400/512/640/800px crossed with off/S1/S2 donors.
This is Sonic-with-CPU-Tails route coverage, not full level certification or trace-physics parity.

## Obligations and current evidence

| Obligation | Test / lane | Evidence and limits |
| --- | --- | --- |
| ENTRY / ROUTE / WATER | `TestS3kHcz1RoutePilot`, ordinary `slow-suite` | Fresh production start, recorded input prefix followed by ordinary live-state steering; water must be seen; P1 death and drowning pre-death forbidden; CPU identity/controller/leader/dead-streak audit; production act-2 reload required. |
| BOSS composition | Same complete route | Six live miniboss hits, defeat and production reload. This does not cover every attack/phase independently. |
| REWIND / local hazards | `TestS3kHcz1RouteRewind`, ten independent cases | Water entry, bubble shield, both early bridges, fan lift, conveyor ride, spring ascent, boss active/hit/defeated. Each reaches its own live spot, captures all registered owners, advances 30 ordinary-input frames and checks immediate restore plus forward replay twice. |
| LOAD / fresh live handoff | `TestGameLoopFreshLevelHandoff`, ROM-backed focused lane | Real fade callback retains PALETTE_FADE and defers four title archives to the next iteration; recording and live drivers restore destination slots before the no-VInt initial sprite pass and ordinary gameplay. Pause and two consecutive loads are covered. Pending fresh transitions exclude rewind; broader viewport/donor/team handoff coverage remains open. |
| LOAD / timeline boundary | `TestS3kHcz1ReloadRewind` | Actual act-2 reload replaces object ownership and clamps live rewind history to the new root; 30 new-act frames restore/replay twice. Checkpoint/death-restart remains open. |
| Configuration entry and reset | `TestS3kHcz1EntryMatrix`, 30 cases | Full cross product of 320/400/512/640/800px and off/S1/S2 donor: movement admission plus two 30-frame replay cycles; two repeated production resets retain width, donor, player and CPU-team ownership. This is entry/reset breadth, not full-route breadth. |
| Full-route configuration axes | `TestS3kHcz1CompatibilityRoutes`, 15 cases | All five supported widths × off/S1/S2 complete with ordinary pad inputs, no P1 death/drowning, CPU lifecycle checks, six-hit defeat and actual reload. Other main characters/teams remain open. |
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
