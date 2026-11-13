# Mod example framework friction

Scope: confirm the example review, compare alternatives and remove confirmed
framework burdens in one PR against develop. Base: `c4ca46472e75a0df6f23dfd410e347ab8ed52a70`.

## Decisions

- Palette ownership: confirmed. The S3K bridge selected registration-placeholder
  claims after a load override supplied different terrain. Retain immutable claims
  in the loaded native level and read them through `LevelOrigin.original`.
  Keeping reserved placeholder cells leaks obsolete metadata; mutating the zone
  registration to track a load adds unnecessary shared state. Native HUD/character
  palette reservations remain. Remove only the example's placeholder reservation.
- Auxiliary presentation: confirmed lifecycle bookkeeping. Introduce a session-owned
  `AuxiliaryObjectCollection` that reconciles desired model identities with live
  manager membership and retires only objects it registered. Missing membership,
  destruction and manager changes rebuild derived objects without checkpoint
  generation counters. It does not restore the visual model or simulate gameplay.
  Rewinding the whole typing course would change its deliberately separate combat
  progression. Keep compound sprites for grouping, but reject the original claim
  of unstable equal-slot draw order: the comparator and stable sort preserve order.
- Menu appearance: confirmed duplication. Share immutable `MenuTheme` roles with
  host `MenuChrome`; expose canvas-based `SceneMenuChrome` and migrate Act Practice.
  Exposing the private host renderer would couple creators to graphics internals;
  copying another private skin perpetuates drift. Theme RGB is now byte-derived;
  blended float values can differ slightly from former rounded decimal constants.
- Card types: obsolete workaround. Current validator accepts verified immutable
  enums. Migrate Slay the Robotnik's CardType with its display names retained;
  save identity uses card IDs. Correct stale rationale for remaining string kinds.
- Additive placements: confirmed API cliff. Add a local-factory placement builder
  to LevelPatch, applying zone/inventory gates and engine ownership. Assign identities
  deterministically after the original inventory, before ordinary placement traversal
  sorts. Multiple patches compose. Native level identity and concrete services survive.
  Reject generic caller-owned ObjectSpawn input; arbitrary stock edits stay on the
  existing expert assembly path. No numeric mod object IDs or new authority source.

## Ownership and verification

Lead: integration, additive placements, candidate surface/pins, docs, final checks and PR.
Delegates: palette/Starpost terrain; presentation/Typing; menu/CardType.
Focused checks use queued Maven; broader smoke and guards run only after edits settle.
Queue waiting is separate from execution. No trace-parity or full-suite certification
is claimed. Final results and limitations will be recorded here before delivery.

## Review correction

Independent review found that the initial addition implementation used `y` as the
raw native Y envelope, losing flip/respawn bits required by editor reconstruction.
Admission now receives the host game's `ObjectPlacementEncoding` and uses
`createKeyed`; direct addition application without a host codec fails before
publication. Native-parser round-trip regressions cover both S1 and S2/S3K formats.
An early host test also expected the underlying rejection rather than the normal
`ModFaultBoundary.CallbackAborted` wrapper; it now verifies the wrapped cause.
The first focused build caught a wrong package in the new palette regression test;
that test compilation error was corrected before successful palette validation.

## Final validation and delivery evidence

Validation used the staged task tree on base `c4ca46472e75`, Java 25,
`-Dmse=off`, verified supplied ROMs and surfaceless EGL. No trace frontier,
full-suite or exhaustive certification is claimed. Independent review preceded
final validation; no additional review loop was needed for test corrections.

- Palette selection: 21 engine tests and 232 nested creator tests, zero skips;
  packed validator reported zero findings. Creator UI: 20 tests, zero skips.
  Creator examples: six outer tests and 145 nested Slay tests, zero skips,
  packed validator zero findings.
- Broad command: `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py
  --base c4ca46472e75a0df6f23dfd410e347ab8ed52a70 --workers 2
  --headless-gl surfaceless-egl --run`. Selection: 4,627 candidate classes,
  94 ordinary batches plus guards. Final line: **VERDICT FAILED**;
  38,505 tests, 39 failures, one error, 50 skips (47 allowed, three unexplained).
  Guards contributed 843 tests with zero failures/errors. The broad run inherited
  `DISPLAY=:0`; later focused checks unset both display variables.
- Two preparation attempts failed before tests due to package-private test API
  access. Moving the ROM regression into `mods.code` fixed compilation and its
  documentation link. Final lifecycle setup supplies a real owner fault boundary
  and enables native exec/load placement just as LevelManager does. Intermediate
  repairs caught a wrong boundary argument/import; no production workaround was
  introduced to make these tests pass.
- The placeholder palette cell is intentionally available after this change.
  Two old assertions still forbade it; they now enforce native HUD reservations
  only. Exact ROM art colour comparisons remain intact and passed.
- With `env -u DISPLAY -u WAYLAND_DISPLAY`, queued Maven reran the 18 failed
  classes other than CNZ, plus documentation links, placement host/ROM, reel,
  auxiliary collection, Typing ownership/package, signature surface and the four
  required S3K invariant classes. Result: 219 tests, 24 baseline failures, one
  lifecycle test setup error, zero skips. All API/ownership/S3K contracts passed,
  as did all six reel tests and all seven display-sensitive assertions. Thus the
  three formerly unclassified skips were executed successfully, not waived.
- After the native placement setup correction, queued
  `-Dtest=TestNativeDecodedPlacementPatchHost,TestNativeDecodedPlacementPatchRom`
  passed all eight tests, zero failures/errors/skips. These cover actual editor
  save/reload encoding, factory creation, unload/re-entry, destruction latch,
  persistent respawn and checkpoint restoration. S3K editor reload remains an
  explicitly unsupported existing path; save encoding is covered.

### Baseline attribution

A detached, clean cowtree at `c4ca46472e75` ran only failed classes, with its own
build tree and the same ROM discovery. CNZ's exact class reproduced five failures
in 14 tests, zero skips: parameter rows 2/4 of both completion/replay methods and
`truncatedAfterApproachNeverCompletesTheEdge`. Assertions match, including hits
1150/1593/2046 and the frame-1564 death.

The other 18-class baseline selection (display variables unset, EGL) ran 122 tests,
24 failures, zero errors/skips. The repaired task run reproduced those same 24
identities and assertions; comparison normalized only identity hashes and set
iteration order. They belong to Three Islands (eight), private lobby rendering
(one), LRZ/SSZ checkpoint route (one), fresh-level RNG (one), ICZ1 lower platform
(one), MHZ Act 2 route (three), MHZ Act 2 entry (one), MHZ Sonic/Tails/team/wide
routes (four), DEZ incoming final route (two), ICZ2 cube bridge (one), and complete
run reference producer (one). No new/worsened production failure was observed.
The broad failed verdict remains part of the evidence; focused repairs and
baseline attribution do not turn it into a passing full sweep.

### Time, policy and remaining state

Broad admission waiting totalled 8,001 seconds separately from 1,711.85 seconds
ordinary execution and guard/preparation execution. The final combined focused
run waited 338 seconds and Maven took 122 seconds; final lifecycle waited 53
seconds and Maven took 37.123 seconds. CNZ baseline waited 398 seconds and Maven
took 105 seconds; other baseline and earlier focused waits are separate and not
included in those totals. User clarification wait was brief and overlapped queue
waiting; unmeasured orchestration/checkout overhead is unknown, not execution.
The abandoned original contract session ended with SIGTERM while queued; it is
not counted as a pass. Category diagnostics were inspected and acknowledged after
recording this evidence. No queue locks or ROMs were manipulated.

Skill mirrors, changed local Markdown links, whitespace and commit-message policy
passed. Global citation inventory has inherited stale/missing references; this
task adds no citations or pins. Candidate API remains unpublished 0.7.0: ordinary
signature regeneration leaves the release descriptor unchanged. The final commit
records the required seven policy trailers and model co-author. Delivery is one
open PR against develop; retain the task branch/worktree for review, with no merge
or destination push. The owned clean baseline is removed after evidence accounting.
