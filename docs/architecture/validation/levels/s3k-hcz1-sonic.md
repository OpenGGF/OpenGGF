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
| LOAD / timeline boundary | `TestS3kHcz1ReloadRewind` | Actual act-2 reload replaces object ownership and clamps live rewind history to the new root; 30 new-act frames restore/replay twice. Checkpoint/death-restart remains open. |
| Configuration entry and reset | `TestS3kHcz1EntryMatrix`, 30 cases | Full cross product of 320/400/512/640/800px and off/S1/S2 donor: movement admission plus two 30-frame replay cycles; two repeated production resets retain width, donor, player and CPU-team ownership. This is entry/reset breadth, not full-route breadth. |
| Full-route configuration axes | `TestS3kHcz1CompatibilityRoutes`, 15 cases | All five supported widths × off/S1/S2 complete with ordinary pad inputs, no P1 death/drowning, CPU lifecycle checks, six-hit defeat and actual reload. Other main characters/teams remain open. |
| REWIND / horizontal arena admission | `TestS3kHcz1RouteRewind`, five independent width cases | Capture before the horizontal lock; 30 saved-input frames must cross it, then immediate restore and forward replay twice must match all registered owners. Native ROM min/max X bounds remain `3680`. This late checkpoint uses donor off; donor entry replay is covered separately. |
| Arena trigger boundaries | `TestHczMinibossRomParity` | Initial camera window and horizontal admission checked on both sides at all five widths, retaining vertical-gate independence and native world-bound writes. |
| PRESENT / ORACLE | Separate trace/native lanes | No pixels/audio or trace comparison is claimed. |

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
