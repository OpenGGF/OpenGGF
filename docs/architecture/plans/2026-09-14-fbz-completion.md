# FBZ completion

Integration base: `435ec2e68c398bcc17af78e69f3e92b637bec90a` (`develop`).
Development tree: `.worktrees/ai-fbz-completion`, `feature/ai-fbz-completion`.

## Outcome and order

Complete both acts with ROM-backed mechanics, supported route breadth, rewind
coverage and measured presentation. Existing class coverage is not completion.

1. Reproduce the remaining S1 donor elevator squeeze. Test the user's proposed
   ordinary run-up and timed roll using live geometry and ordinary inputs,
   explicitly observing whether the existing assist fires. First use a short
   production-loaded scenario, then integrate the maneuver into the authentic
   complete route. Preserve acquisition, clearance, release and damage checks.
2. Refresh the canonical complete-run trace. Diagnose the earliest causal
   mismatch through production submissions and ROM ordering; do not relax the
   comparison contract. Validate timing changes on independent recordings.
3. Map existing entry, object, event, boss, lifecycle, rewind and complete-route
   coverage into per-act/character matrices. Add missing independent scenarios
   and required breadth; distinguish seeded local checks from authentic routes.
4. Complete reproducible visual evidence tooling, fresh native checkpoint and
   cadence capture, independent state/region review, semantic comparisons, then
   compatibility captures. Repair measured presentation mismatches.
5. Run combined change-based validation and relevant deeper lanes, integrate
   into the current main `develop`, push only `develop`, then account for and
   remove this task's worktree and local branch.

## Current evidence and uncertainties

The September 14 controller handover reports every width/team route completing
Act 2, with only S1 donation failing at the `$1DC0` squeeze. This is inherited
evidence. The existing S1 controller accelerates only to `$0110` before rolling
and seeks a geometry window for the `$0800` assist; it does not establish the
limits of a longer ordinary run-up. The current predictor assumes rolling
friction, while actual car landing invokes standing posture. Real execution is
the deciding evidence.

The starting strict replay was reproduced on `435ec2e68`: 5,666
errors, first row 34, `queue.s3k_kos_direct.busy` expected true/actual false.
That row submits the FBZ Blaster archive body at `$0DC6C4` to `$FFFFD000`.
Submission, first-child coordination and completion need measuring before a
cause can be assigned. Historical July trace outcomes are obsolete.

Visual acceptance is incomplete: native Java executors exist, compatibility
capture rejects, the amendment has no reviewed exact start state or visible
regions, and no reproducible aggregate capture/comparison host exists. The Lua
exporter covers start and five AniPLC series, not all late checkpoints/series.
BizHawk 2.11 preflight passes locally; native display access works when launched in the verified host environment.
Expect substantial capture/tooling work, not a short final verification.

## Validation and ownership

The user explicitly authorized a separate manually tracked 40-minute FBZ test
budget while KiS2 retains the shared receipt (2026-09-14). Preserve KiS2 accounting;
record every FBZ invocation and permit only one combined broad attempt. Reserve
the broad attempt until implementation and focused validation stabilize.
Focused route and trace baselines precede changes. Java 21, Lua 5.4 and
PowerShell preflight passed; all three existing runtime ROM SHA-1s match.
The independent `kis2-tier-two` delivery owns the active shared receipt/lock.
Do not replace or finish its receipt. All invocations are measured under the separately authorized accounting.
Report any mandatory-check/budget conflict before starting those checks.

Root owns route/controller/runtime changes and integration. Independent trace, coverage and native-capture workstreams have explicit bounded
ownership; their measured execution is charged to the same manual budget. Preserve unrelated submodule changes and
`raiscan-0.6-thoughts.md` in the main workspace.

## Measured changes and rejected approaches

The user's ordinary run-up/roll suggestion is feasible. The controller now
waits for an ascending car whose live slope surface will intercept the lower
half of the native catch band after ordinary floor acceleration. It runs from
west of the block, then presses DOWN alone. The engine still performs all
movement, posture changes and solid contacts. No trace state, velocity writes,
spindash, or squeeze assist supplies the crossing.

A brief airborne fall from the raised button is ordinary egress; rejecting all
airborne run-up frames aborted this valid approach. The first shorter roll
probes sometimes cleared the block but later stalled/crushed, so geometric
clearance alone was rejected as success: the test requires the exact car's
acquisition, safe crossing, release and continued movement.

Later S1 controller obstacles were independently resolved with ordinary inputs:
a full run-up through raised bottom magnetic columns, a jump over the nearby
Blaster, and repeated end-boss jumps. Reversing immediately on a boss hit turned
Sonic into an arm; preserving actual rebound direction reached seven hits.
A speculative early running-jump strategy stalled at the arena corner and was
rejected. Descending jumps now choose ordinary air input using live arm/flame
positions and projected landing clearance. This is test input authoring, not a
change to production physics or collision widths.

`mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical -Pfbz-routes
-Dtest=TestFbzCompatibilityMatrix#donatedMovementProfileCanReachTheMandatoryBossEntryWithoutSpindash
test` with all three absolute verified ROM properties passed **2 tests, zero
failures/errors/skips**, 56.20 seconds, on `9fa8fc0a0` plus the local controller
changes. Both S1 and S2 complete the mandatory route through the final SOZ
request. This is focused route evidence, not complete zone certification.

Production corrections integrated so far: `ef13df370` queues the ROM FBZ enemy
KosM batch; `e8307ddc2` preserves cage animation word writes and horizontal-chain
hand-step writes; `9fa8fc0a0` reads the platform clock's low byte without an
arithmetic increment. Complete V5 replay moved from 5,666 errors / first row 34
queue busy to 4,721 / first row 3888 mapping frame. An independent recording
improved from 5,227 to 5,090 errors; its earlier Tails subpixel frontier remains.
These results remain red and are not ROM parity.

Native execution consumed 276.557 seconds and produced 1,025 RAM observations
and nine diagnostic PNGs in the external task directory. No accepted exact
start or AniPLC series was produced. The exporter incorrectly read `$F60C`,
treated the `$F60E` VDP command template as live display state, supplied an
algorithm name where `memory.hash_region` expects a domain, and mistook a
reused title-card slot for a live title card. Correcting only the address did
not repair the invalid display-state assumption. Keep these diagnostics
separate from accepted visual evidence; do not amend frozen references from
an unpaired frame or infer display readiness from the command template.

Further proven corrections: `8eb04d603` adds an internal zone-owned tumble
presentation policy (public Mod API unchanged) and preserves snake standing
ownership across dynamic position updates. `c31bdbd7a` resets tumble fields when
an airborne rider lands on a moving cage, matching `Player_TouchFloor` for all
native characters. Final strict counts are 4,667 / first 13,585 Y for the complete
recording and 5,065 / first 116 Tails subpixel for the independent recording.
All early cage mapping discrepancies are gone; the next physics owner is a
rolling landing on a disappearing platform. Trace parity remains incomplete.

The expanded Act 1 lifecycle method passes all 105 width × donor × team
combinations plus its final native reset. All 96 local arrival delays and 15
width/donor squeeze cases pass. Initial test failures came from assuming a
released cached latch must be null and from applying viewport configuration
before fixture initialization overwrote it. The production authority checks
now inspect actual riding state and the test asserts the effective width.

The active-car rewind spot uncovered a production defect after comparison
excluded only the shared documented nonsemantic fields (`epochAtCapture`,
`bucketsDirty`, `peakSlotCount`). Generic dynamic reconstruction registered
`execOrder` while the new instance still had slot -1; phase-2 field restoration
later supplied its real slot. The car's moving spawn record also has a new
identity after restore, so both riding lookup routes failed. Restoration must
assign every captured dynamic slot before callbacks and execution-table
registration, as it already did for adopted construction children. No saved
contact or gameplay fields are removed from the regression comparison.

The restoration fix now passes all three independent squeeze rewind spots
(before entry, active car, after exit), each with two capture/restore/forward
replay cycles. The no-ROM dynamic-solid regression proves the captured slot,
restored riding authority and exactly one execution tick across two restores.
Focused command: `mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical
-Dtest=TestFbzSqueezeOrdinaryRoll#productionRegistryRestoresAndReplaysTheLocalCrossing,TestObjectManagerRewindSnapshot,TestObjectManagerRewindDynamicClassification,TestFbzVisualExporterGuard
test` with all absolute verified ROM properties and `LUA_BIN=/usr/bin/lua5.4`:
**30 tests, zero failures/errors/skips**, 46.49 seconds. The Lua exporter guard
exercises actual reads, domain restoration on success/failure and refusal to
publish unverified display evidence; it runs in the guard lane, which provisions
Lua, instead of adding a new ordinary-lane dependency.

Independent review found no blocking runtime/controller issues. The slot fix
preserves adopted and slotless behavior, allocator reservations, phase-2
reference restoration and public snapshot/API contracts. The test comparator
retains all gameplay fields, including newer fields omitted by the older
shared comparator's explicit field list; only three documented restore
bookkeeping fields are excluded. Temporary probes were removed.

The change-based plan selects the full ordinary inventory plus structural
guards, with two ordinary workers. Recent repository measurements put this at
about 7–8 minutes; it is not a short check, and the older normalization measured
34 minutes. Preflight passes with Java 21, Lua 5.4 selected explicitly and
PowerShell. An invocation without LUA_BIN correctly failed preflight before
launching any tests; that missing environment variable is repaired.
No broad attempt has been consumed. The unmodified runner selection/execution
can use the user-authorized manual ledger without touching KiS2's shared Git
receipt; neither selection rules nor retry protection are changed.

## Candidate checkpoint and remaining budget

The final exhaustive command `mvn -Dmse=off -B -Dsurefire.forkCount=1
-Dsurefire.runOrder=alphabetical -Pfbz-routes -Dtest=TestFbzCompatibilityMatrix
test`, with the same three verified absolute ROM paths, passes **11 tests,
zero failures/errors/skips**, 62.31 seconds. It covers four additional widths,
five Sonic-main team configurations and both active donors; every route reaches
the mandatory boss/capsule/SOZ handoff. Tails-main and Knuckles-main cold-start
completion are still missing; these Sonic-main results do not certify them.

Manual delivery accounting at this checkpoint:

| Execution | Seconds |
| --- | ---: |
| Root focused runs, including failed iterations and baseline | 694.604 |
| Independent trace lane, including baselines and probes | 743.860 |
| Native capture diagnostics, conservatively charged | 276.557 |
| **Total / 2,400-second cap** | **1,715.021** |
| Remaining | 684.979 |

No combined broad run, integration, push or final worktree cleanup has happened.
The main workspace remains develop with unrelated changes preserved. The single
broad attempt is reserved for the completed aggregate scope. Approximately eight
of the remaining eleven minutes are needed for that run under recent measured
conditions, leaving insufficient execution time for outstanding strict-trace,
native-reference/presentation and missing main-character route work. This is an
explicit validation-budget/completion conflict under the user's forty-minute
cap, not a claim that the local candidate is fully validated or FBZ complete.
