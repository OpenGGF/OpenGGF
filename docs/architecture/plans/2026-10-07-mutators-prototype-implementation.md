# Mutators prototype implementation

Accepted design: [mutators blueprint](../designs/2026-10-07-mutators-blueprint.md).
Original base: `6d817a9d74f135714f3da59ab9aab156cc09473e` (`develop`). Delivery is a
feature-branch PR; the main checkout is outside this task's edit authority.

## Bounded product

JVM, trusted boot-prepared `example-mutators`, Sonic solo in Sonic 2 Emerald Hill
Act 1. Dry ordinary air gravity and selective player presentation. Normal jump,
hurt, death, scripted movement, targeting, objects and collision retain native
owners. No placement filters, new levels, broad character/donor promises,
hot installation, native creator bytecode or modified-game recording support.

## Work and evidence

1. Typed definitions, independent action/option scopes, admitted/effective state,
   atomic groups, safe capabilities, owner faults, separate saved preferences and
   rewind snapshots. Contract child owns isolated branch and focused tests.
2. Injected semantic movement/presentation policies, stock identity and native SAT
   admission. Gameplay child owns isolated branch and focused tests.
3. Lead integrates one world-owned state, explicit load causes and modal host UI
   using the existing native level loop; maintains title/configuration/restart/hub
   flow and buildable human/agent example.
4. Lead observes actual GPU frames and PCM in outside-repository task captures,
   exercises repeated launch/exit and recovery, and runs combined change-based
   validation plus affected domain tests. Independent review checks high-risk
   boundaries and final original-base-to-head diff.
5. Fetch destination, assess overlap, commit/push only feature branch, raise and
   register PR. Account for child edits/worktrees/processes; retain PR tree.

No prototype gate is marked complete until executed evidence is recorded in the
existing design and example guide. Later MVP/product stages remain future work.

## Recovery and rejected approaches

The first implementation lead stopped on provider capacity, with the contract
commit `bff89b4fa1632a2612b7e2e973a940c56984dadd` integrated and gameplay/host
sources preserved. Recovery retains that commit and the original base. The
gameplay child's structural `@RewindTransient` binding remains excluded from
sprite history; the standalone hook's declaration heuristic was not bypassed
or satisfied with a fake pin. Genuine host API additions belong in the combined
candidate signature update.

The final read-only review exposed two delivery blockers. A preference save
failure at Start/Resume could be overwritten by loading or acknowledgment, so
transition requests must retain the visible retryable draft on failed save.
Persistence now gates Start and Resume before any policy publication, and gates
Restart/Return before a command can leave the menu. A still-unavailable settings
directory holds these actions; closing the Engine window remains available.
The capture's offline PCM lease did not survive the production title-to-level
audio rebuild: a stock GPU reproduction with 600 neutral inputs and Start
completed 622 title rows before `beginCaptureMode() not called`. The existing
manager-owned live capture handle supplies the needed carry/rebind lifetime;
there is no new audio producer or speaker path.
The producer's presentation rate must still match capture FPS before attaching:
checking only the requested lease rate would miss PAL/configuration mismatch and
silently truncate or pad packets. Independent repair review caught this guard
regression before the focused invocation was admitted.

A 300-frame initial wait never left the stock title, and the example's original
240-frame prefix therefore did not prove its menu path. Capture inputs must
wait for the native intro/title readiness before selecting controls. Custom
controls should appear only when they can accept input. The synchronized CSV
and actual engine window, rather than an input script alone, establish the
visited phases. Completed repair, integration and audiovisual results are
recorded below before delivery.
