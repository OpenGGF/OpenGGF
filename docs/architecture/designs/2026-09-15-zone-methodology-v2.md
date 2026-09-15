# Zone implementation methodology v2

Date: 2026-09-15. Agreed direction; first application: Sandopolis Zone (SOZ).
This document defines the method, not a certification of an implemented zone.
The [SOZ plan](../plans/2026-09-15-soz-methodology-v2.md) applies it to both acts.

## Origin and changes from FBZ

The [FBZ design](2026-07-12-fbz-complete-zone-design.md) (`2d07c9c72`),
[execution plan](../plans/2026-07-12-fbz-complete-zone.md) (`89d6a29f3`) and
[RED/GREEN log](../research/2026-07-12-fbz-red-green-log.md) preserve the first
version: exhaustive inventory, disassembly-backed tests, route slices, independent
reviews, late complete-run traces and a final compatibility audit. Inventory and
comparison manifests were committed without replay in `f0be2d1c2`.

Keep that foundation. Change when independent evidence and compatibility enter:

| FBZ approach | v2 decision | Reason |
| --- | --- | --- |
| Complete-run trace deferred until broad implementation | Keep full-route replay late; compare short native sequences after each slice | Detect phase and presentation errors before they spread |
| Focused RED/GREEN plus two fresh review passes per task | Retain meaningful failing regressions; review coupled boundaries and batch routine families | Spend review effort on ROM interpretation, test independence and shared contracts |
| Compatibility audit after native implementation | Add representative compatibility to every mandatory mechanic; retain final breadth audit | Discover ability and participant constraints while design can still change |
| Completion accumulated through task gates | Track implementation, reachability, rewind, native behavior and pixels separately | A passing unit test cannot establish an ordinary route or rendered parity |

This is a refinement, not evidence that FBZ lacked useful tests. Its execution log
records corrections to expected values and setup assumptions. Later
[laser-room](../audits/2026-09-14-fbz2-laser-room-graphics.md) (`cd2483bf6`) and
[miniboss](../audits/2026-09-15-fbz1-miniboss-visual.md) (`9aa24c795`) fixes show
why art priority, plane assignment and child presentation need independent checks.

## Delivery loop

**Inventory → focused failing test → playable route slice → short native
comparison and representative compatibility → boundary review → next slice.**
Finish with complete routes and the full applicable validation obligations.

1. **Establish evidence and ownership.** Inspect current production registrations,
   decoded placements/subtypes, reachable dynamic children, events, art/PLC and
   audio. Recheck catalogue claims against the locked-on disassembly. Record the
   owning routine, clock, update phase, state owner, lifecycle and test binding.
   Inventory the whole route, but implement only missing or incorrect behavior.
2. **Write a discriminating focused test.** Observe the missing-behavior failure
   before implementation. Derive expected values from a cited ROM branch/table
   or independent native capture, not from the proposed Java algorithm. Include
   the adjacent timer phase, opposite approach direction, failed allocation or
   participant distinction when it can change that branch. Record concise
   RED/GREEN evidence in the task plan; do not preserve raw logs.
3. **Deliver a route slice.** Join the necessary objects, events, collision, art
   and camera behavior into a traversable boundary. Resolve shared ownership
   before consumers. Keep local tests independent of long route prerequisites.
4. **Compare a short native sequence.** Check entry, active behavior and release
   with the same declared inputs and equivalent starting conditions. Compare
   relevant state and actual rendered output where presentation changed. A
   one-frame screenshot or final-state equality cannot prove update cadence.
5. **Exercise representative compatibility.** Use native standard width, one
   wider supported viewport, S1 movement donation and an extra-follower team on
   each new mandatory mechanic. Verify actual resolved configuration. Expand
   immediately when sensitivity appears; representative cases are an early
   warning, not a substitute for the required final matrix.
6. **Review the boundary.** Check ROM interpretation and oracle independence,
   then implementation ownership, lifecycle and maintainability. Shared contracts,
   timing changes and coupled boss/event sequences warrant independent review.
   Routine object families can share one review. Re-review changed findings;
   do not repeat clean reviews or add routine human approval ceremonies.

## Independent evidence contract

Choose checkpoints and acceptance fields before evaluating candidate output.
Record ROM identity, disassembly revision, native core/version, input source,
entry recipe, coordinate convention, clock/phase and comparison boundary. Use
the current TraceChaser guide for native production and the repository's live V5
contract; historical FBZ recorder metadata is not a current specification.

Prefer ordinary native entry and controller inputs. A local setup fixture must
declare every setup write and demonstrate relevant initialization/history; mark
it as seeded evidence. Engine setup uses production owners. Never import native
physics/aux rows into engine gameplay. Any existing dedicated hardware-timing
port remains governed solely by its existing contract, with no new exception.

Keep local fixtures and cold traversal as separate obligations. Match camera
copies, palette/VRAM readiness and retained plane history where relevant; equal
camera coordinates alone do not establish equal renderer inputs. Read the
[measurement hazards](../../agent-workflow/briefing-trace-rounds.md) before
interpreting results. Amend an invalid checkpoint only with source/setup evidence
and a recorded reason; never move it to fit the engine.

If native capture is blocked, finish independent implementation and focused
checks, record the missing comparison, and resolve the prerequisite before
claiming native or visual acceptance. Do not build later slices on an unresolved
shared phase/ownership assumption.

## Completion and cost

Use existing per-act/character-route matrices under `validation/levels/`, linked
from the [coverage backlog](../../status/level-test-coverage.md). Follow the
[level test standard](../../guide/contributing/level-test-standard.md), including
width/donor lifecycle products, supported characters/team shapes, and before,
active and after rewind spots with forward replay or intentional timeline reset.

For each obligation record separately:

- **Implemented:** concrete production binding exists and focused behavior passes.
- **Reachable:** ordinary inputs reach and leave the mechanic; distinguish cold
  traversal from a seeded local boundary.
- **Rewind verified:** capture/restore and forward replay cover the state owners.
- **Native behavior matched:** an independent comparison covers the named fields
  and interval, including relevant clock and allocation ordering.
- **Visually matched:** actual rendered pixels satisfy the declared comparison;
  numeric scroll/art assertions alone are insufficient.

Each claim carries command, commit, configuration, setup, outcome, skips and
coverage limits. Unknown, failed and blocked obligations remain visible. No single
aggregate green label substitutes for these claims.

Run focused checks during iteration; schedule full-route replay when its route is
implemented. Final compatibility breadth and native regression remain required.
Repository change-based validation, baseline attribution, mandatory S3K checks,
CI and release gates retain their authority. State broad-run class count, expected
cost and stopping rule before starting; do not invent a second testing policy here.

Keep decisions and rejected approaches with evidence in the dated task plan.
Promote reusable probes and lessons into existing tools/catalogues. Durable
captures live in an explicit external task directory; temporary diagnostics follow
the repository cleanup rules. This revision rejects both blanket trace deferral
and constant full-route replay during foundational implementation.
