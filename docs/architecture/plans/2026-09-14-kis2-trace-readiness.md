# KiS2 gap closure before the full-game trace

Base: `ae767f351` on develop. Requested outcome: implement the remaining
ROM-proven KiS2 differences that can be verified before a new full-game BK2,
with production ROM assets and preserved stock-game behaviour. The earlier
[presentation delivery](2026-09-14-kis2-presentation-super.md) remains the baseline.

## Work and ownership

- Movement: glide/climb collision, superspeed, pushing/skidding, balance and
  fresh-button Super activation; semantic rules and focused regressions.
- Zone/contact mechanics: wind tunnels, held-player objects, boss touch box,
  solid contacts, and catalogue checks for already equivalent implementations.
- Presentation: CNZ slot pictures, results lifecycle, title/demo/menu feasibility,
  and review of purported ending gaps against the actual KiS2 branches.
- Integration: saved checkpoint rings/extra-life flags through reload and rewind,
  candidate API pins, combined tests, discrepancy catalogue and delivery.

Separate worktrees own each stream. Shared rule edits are coordinated; runtime
assets never come from the optional `docs/kis2disasm` research tree (`c336fed`,
`gameRevision=3`, `fixBugs=0`). Do not invent gameplay state from traces or
claim complete route coverage before the user records the chain movie.

## Validation and delivery

One combined validation task pinned to the base; focused tests during iteration,
then the actual aggregate category selection plus guards. At task start the shared
runner was occupied by `route-green-20260914`; test execution is pending coordination,
without resetting or consuming that other task's budget. Changes to public candidate
rules/snapshots retain compatible constructors and update normalized signatures.

Integrate into the main workspace's existing develop branch, push only develop,
and clean the task worktrees after accounting for all changes. Preserve unrelated
user files and dirty research submodules.

## Evidence and decisions

- `Obj79_SaveData` / `Obj79_LoadData` explicitly bank and reinstate saved rings
  and extra-life flags. KiS2 omits stock S2's clearing instructions. Restoring
  live pre-death rings was rejected: it is not what the ROM loads.
