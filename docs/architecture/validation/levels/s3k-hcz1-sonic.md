# S3K HCZ act 1 — Sonic route pilot

Canonical game/zone/act: Sonic 3 & Knuckles, Hydrocity, act 1.
Runtime slots: zone 1, act 0; outgoing production reload: zone 1, act 1.
Representative configuration: native 320px Sonic with CPU Tails, movement donor off.
This is a route pilot, not level certification or trace-physics parity.

## Obligations and current evidence

| Obligation | Test / lane | Evidence and limits |
| --- | --- | --- |
| ENTRY / ROUTE / WATER | `TestS3kHcz1RoutePilot`, explicit `openggf.hcz1.pilot=true` | Fresh production start, recorded input prefix followed by ordinary live-state steering; water must be seen; P1 death and drowning pre-death forbidden; CPU identity/controller/leader/dead-streak audit; production act-2 reload required. |
| BOSS composition | Same complete route | Six live miniboss hits, defeat and production reload. This does not cover every attack/phase independently. |
| REWIND / local hazards / load boundaries | Missing independent obligations | The complete route does not certify restore/replay, every water-object boundary, checkpoint/death-restart or act-transition timeline isolation. |
| Configuration breadth | Missing | Other widths, donors, main characters and teams remain unverified. |
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

## Validation scope

This delivery uses proportionate focused validation for private route controllers,
plus the affected CI argument/guard checks. The existing broad runner selection
is disproportionate to these bounded test-only and branch-context changes.
A green route means the assertions above passed; it is not a full ordinary,
full structural-guard, full-level matrix or trace-parity certification.
