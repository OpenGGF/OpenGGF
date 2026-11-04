# Bonus-return saved apparent act and Slots source camera

The final continuation and handoff below supersede the original stopped state.
The original evidence and its limits are retained for provenance.

## Scope and original stopped state

Implementation lane `aiz3-act-camera-restore`, using the T3-prepared checkout.
Merged wave7 `2c27b9cb7`, then stopped WIP `a9c7e48f4`. The resulting merge
was first `064314b1b`; adding the required documentation trailers changed its
identity to `d045ff8b4` without changing its tree. No push or integration into
develop was performed. TraceChaser remains pinned and checked out at `10f1e09`.

**The first continuation stopped at a title-boundary error**, under the lane's
explicit stop rule. This is not a completed delivery or a three-profile gate.
The original WIP is retained; the proposed water/order extension is not applied.
No camera-fraction, entry-import, MHZ rewind, GameLoopBonusStageSession,
frontier-log, SURVEY, or other fleet owner was edited.

## Existing implementation and ROM ownership

- `BonusStageTransitionCoordinator.restoreReturnState` restores the engine's
  own `BonusStageState.savedApparentZoneAndAct`. ROM `loc_6034`
  (sonic3k.asm:7580-7582) copies Saved2 apparent identity before reloading.
  The WIP's **late** restore removes the later AIZ2 resize clamp, but does
  not establish the correct identity during the load itself.
- `S3kSlotBonusStageRuntime.shutdown` holds camera X/Y across refocusing the
  original player. The source camera is the last `sub_4BBF4` track
  (sonic3k.asm:98988-99004), not the parked player's camera. Camera fractions
  remain another lane's responsibility.

## Executed checks in this continuation

Environment: Java 25, `-Dmse=off`, surfaceless EGL via JAVA_TOOL_OPTIONS,
DISPLAY/WAYLAND_DISPLAY/JDK_JAVA_OPTIONS/MAVEN_ARGS/LD_LIBRARY_PATH unset,
LUA_BIN=/usr/bin/lua5.4 and $HOME/.local/bin prepended to PATH.
All six original absolute ROM properties from the lane brief were supplied;
wrapper output reports no missing ROM inputs. No tests skipped.

1. At `064314b1b`, before any source/test edits:
   `bash /tmp/aiz3-20261104/focused.sh "$PWD" resume-units none TestBonusStageTransitionCoordinator,TestS3kSlotBonusStageRuntime`.
   Both original WIP assertion additions ran: **33 tests, 0 failures, 0 errors,
   0 skips**. Queue 387 s; Maven execution 93 s. Wall interval
   10:59:41–11:07:42 UTC (481 s, including about 1 s wrapper/archive overhead).
2. Added two discriminating checks, with production unchanged, then ran:
   `/tmp/aiz3-resume-20261104/run.sh red-return none -Dtest=TestLevelZoneLoadExecutor,TestBonusStageReturnWaterRestore`.
   **7 tests, 1 failure, 2 errors, 0 skips**. Queue 863 s; Maven execution
   36.845 s. Wall interval 11:08:16–11:23:17 UTC (901 s, about 1.2 s overhead).
   The saved source diff accompanies the complete reports.
   - The new load-profile observation fails as intended: loaded act 1,
     apparent act **1**, expected saved apparent act **0**, before loading.
   - The new ROM-backed Knuckles AIZ1→AIZ2 return reaches
     `GameLoopBonusStageSession.doExitBonusStage:429` and throws
     `IllegalStateException: a fresh title-card transition boundary is already pending`.
   - The **existing** HCZ test
     `bonusStageReturn_restoresSavedWaterLevelImmediately` throws the same
     error at the same owner. No separate baseline run of that method was
     taken, so this is an observed unrelated blocker, not a proven new
     production regression or proof of cross-test contamination.

The discriminating tests were saved as a patch and removed from the branch
before handoff; no failing experimental test is silently committed. The
production tree equals the 33-test passing tree. No guards, rewind set,
keep-greens, or r6/r7/segments profiles ran in this continuation after the
stop condition. No live jobs remain. User wait: zero; repository inspection,
reporting and other non-job work are not represented as queue/execution time.

## Bounded water finding; extension not applied

The same saved-block ordering directly reaches the water frontier:
`CheckLevelForWater` (sonic3k.asm:9790-9795) compares current and apparent
identity when deciding whether Knuckles gets AIZ2 water. The zone loader
currently overwrites apparent act before loading; water initialization also
unconditionally selects the direct-load path. A late restore cannot repair
that already-completed selection. The discriminating load-profile check
executes and observes that wrong identity; the integration check cannot
validate water assertions because of the title-boundary blocker.

A second concrete restoration difference is independently visible in ROM:
`loc_7932` (sonic3k.asm:9833-9846) initializes mean, target and speed;
`loc_2D2C2` (sonic3k.asm:61833-61856) subsequently restores **mean only**.
The current coordinator overwrites both mean and target with the saved mean.
`DynamicWaterHeight_AIZ2` (sonic3k.asm:8680-8727) then owns target changes.
The saved patch proposes restoring apparent act before the load, retaining it
in the bonus-return zone loader, passing actual apparent/current inequality
to water initialization, and retaining the initialized target after mean
restoration. It is **unexecuted**, not accepted or committed runtime code.
It introduces no trace hydration or new saved gameplay values. Resume only
after the unrelated title-boundary blocker is resolved or attributed.

## Historical witnesses, not current matched baselines

Original raw before/after Mega witnesses remain in `/tmp/aiz3-20261104/`:
`f-x1-reports` and `f-m1-reports`. Their source provenance is the stopped
lane's work in progress; the reports alone do not pin each uncommitted tree.
They must not be relabelled as runs of this continuation's merged SHA.

| Mega segment | Historical before | Historical WIP after |
| --- | --- | --- |
| 0 | 12, first 4568 queue busy | identical |
| 1 | 12, first 1300 queue busy | identical |
| 2 | 12, first 2025 queue busy | identical |
| 3, Slots | 14, first 1052 queue busy | 12, same first queue field |
| 4, aiz_3 | 11194, first 105 x ($03D7/$1050), incomplete | 57027, first 1686 x_speed ($0018/$0030), complete |

The Slots witness removes the held-camera pair. The aiz_3 total is higher
while its first error is later, and its comparison reaches completion rather
than aborting early. This is not an exact added-error attribution: these run
reports retain only the first/recent mismatches, and no full per-row error
dump survived. Do not infer all subsequent fields from grouped spans or
claim the added-error gate passed. The historical WIP full chain says:

- walk-failure: segment 4 (aiz_3) exit boundary (starpost_bonus) never observed;
- segment-physics: segment 2, 12 errors, first 2025 queue.s3k_kos_direct.busy
  ROM=false, engine=true;
- segment-physics: segment 4, 57027 errors, first 1686 x_speed
  ROM=$0018, engine=$0030.

Historical full profiles at baseline `814cde7b7` remain available:
r6 889 cases/10F/0E/7S; r7 119/81F/2E/0S; segments 70/46F/2E/0S.
The segments Maven log completed at 09:55:50 UTC even though its old driver
never archived it; raw reports were recovered read-only from `aiz3-baseline`.
Wave7 changed source and fixtures relative to this baseline. These are
historical context, not matching source/input baseline evidence for this tree.

## Handoff and preservation

Complete logs, commands, test source diff, original/full chain text, raw XML
and JSON, historical comparisons and the two proposed patches are preserved
under `/tmp/aiz3-resume-20261104/`. No raw/regenerable report is committed.

Keep `/mnt/LinuxData/T3/worktrees/OpenGGF/aiz3-baseline` (clean, detached
`814cde7b7`) and the original WIP worktree `t3-9a7dfec6` (clean,
`bugfix/ai-trace-mega-aiz3` at `a9c7e48f4`). Their changes have not been
integrated by this lane. Keep `/tmp/aiz3-20261104`, including unknown
`AbstractSprite.bak`, dump scripts and logs; no unknown work was deleted.
The new task branch and checkout remain for lead review/resumption.

Proposed frontier entry, **blocked; do not publish as certified progress**:
"Bonus-return restoration WIP (`a9c7e48f4`) passes its 33 focused assertions
on wave7. Historical Slots 14→12 and aiz_3 first105→1686 are retained as
witnesses only. A discriminating profile-load check confirms saved apparent
act is restored too late for water selection. ROM also restores saved mean
without replacing the initialized water target. Continuation stopped at a
duplicate pending title-boundary error in the excluded GameLoop owner;
water patch and all required profile gates remain unvalidated."


## Second continuation: fixture lifecycle proved, water extension applied

The T3-prepared `aiz3-camera-water-restore` checkout merged `5f53a4a7e`, then
`f7d158c49`, as `bc025bcc7`. The executable TraceChaser checkout and gitlink both
match `10f1e09`. No other checkout was created or modified.

The prior pending-boundary stop was a test-fixture omission. Both direct
`entered`/`exited` fixtures now run the real `GameLoop.step()` entry title until
`BONUS_STAGE`, asserting that the pending fresh boundary has completed before
calling the direct return. Production boundary admission remains strict.
With production still at the saved WIP, `lifecycle-red` executes **7 tests,
2 failures, 0 errors, 0 skips**: the old HCZ test passes; the new apparent-act
load observation fails 0/1, and the new Knuckles seamless AIZ2 return reaches
its water-enabled assertion and fails true/false. These are executed
mechanisms, not inferred explanations of the previous exception.

The saved `water.patch` was then applied. Saved apparent identity is restored
in `prepareReturnLoad` before loading and retained by `LevelZoneLoadExecutor`
for a bonus return. `LevelWaterCoordinator` selects its existing seamless
water predicate from current/apparent inequality. The coordinator restores
saved water mean without replacing the new target: the existing HCZ assertion
also checks the fresh target; the AIZ case checks saved mean `$510` with target
`$528`. ROM ownership remains `loc_6034` (sonic3k.asm:7580-7582),
`CheckLevelForWater` (sonic3k.asm:9790-9795), `loc_7932`
(sonic3k.asm:9833-9846), and `loc_2D2C2` (sonic3k.asm:61833-61856).

`water-green` stopped at test compilation because the widened Mockito target
assertion lacked an `anyInt` import; no tests executed. After adding the import,
`water-green2` passes **428 tests, 0 failures/errors/skips**, including the
saved-act/Slots units, both water return tests, water providers/dynamics,
water/bonus/Slots rewind, the four S3K keep-greens, source/timing/gitlink guards
and fresh-boundary controls. Exact commands, source diffs, logs, timing and XML
are retained under `/tmp/aiz3-resume-20261104/` for each named run. This is a
focused pass, not the required three-profile gate.

The lead separately corrected live title control ownership as `afdf37b29`;
it is to be merged after this focused checkpoint. That fix preserves the
host's current control lock during native snapshot restoration, rather than
capturing a locked snapshot that would re-lock a released return. This lane
has not changed `GameLoopBonusStageSession` or any camera-fraction, CNZ2,
MHZ rewind, frontier-log, SURVEY, or other fleet owner. The lead's frozen
`afdf37b29` profiles will supply the matched integration baseline; the original
historical profiles above are still only historical evidence.


## Final continuation gate and local handoff

The water/fixture correction is `d2b6dddbd`; merging the lead's live-control
correction `afdf37b2967e92eb58d0de096e63a5bf970a0a2e` produced
`e6aeb7f03`. Its only merge conflict was the shared prerelease item; both
control-lock and water/Slots changes were retained. After this merge,
`root-composed-focus` passes **102 tests, 0 failures/errors/skips**, including
both real bonus title routes, the corrected water returns, fresh-boundary
controls, fresh handoff and the source guard. No production
`GameLoopBonusStageSession` edit was made by this lane; it still matches `5f`.

All three complete candidate trace profiles ran as separate queued jobs on
`e6aeb7f03` plus the archived, temporary comparison-only observer patch.
The observer records complete ERROR-row `FrameComparison` fields and never
writes gameplay state, severity, input, timing admission or comparator results.
It is removed from the final tree. The final source edit only corrects the
post-load restoration comment to cite the second native apparent-identity
write at `loc_2D2C2`; executable implementation is unchanged from the tested tree.

Matched baseline: the lead's frozen `afdf37b29` archives
`/tmp/trace-survey-20261102/wave7-sol-final-{r6,r7,seg}-reports`, each with full
Maven footer and raw XML/JSON. Baseline provenance was supplied by the lead;
these are not runs performed by this lane. No duplicate baseline sweep was
submitted. Current ROM SHA-1s were separately recorded and S1/S2/S3&K match the
canonical identities. Trace fixture blobs, TraceChaser gitlink and replay-test
sources match `afdf` exactly. Both sides use Java25, one alphabetical Surefire
fork, surfaceless EGL and all original absolute ROM properties.

| Profile | Candidate cases / failures / errors / skips | Matched comparison |
| --- | --- | --- |
| r6 | 889 / 9 / 0 / 7 | All testcase identities, statuses, untruncated messages and error JSONs identical. |
| r7 | 119 / 81 / 2 / 0 | Only Mega changes; the other 118 cases and error reports are identical. |
| segments | 70 / 46 / 2 / 0 | All testcase identities, statuses, untruncated messages and error JSONs identical. |

All three Maven invocations exit1 for those explicit existing red selections;
their forks complete normally and the reports are archived. This is not a
full-suite pass, nor a green trace claim. The seven r6 skips are unchanged:
five explicit opt-in audit/performance cases and the absent optional Gumball
and Pachinko round-trip recordings. No required ROM input is missing; neither
r7 nor segments skips any case. No category-runner `VERDICT` is emitted because
these are focused/profile wrapper runs, not category runs.

| Mega segment | Frozen afdf baseline | Candidate e6aeb7f03 |
| --- | --- | --- |
| 0, AIZ | 12 errors, first4568 queue busy | Identical JSON |
| 1, Gumball | 12 errors, first1300 queue busy | Identical JSON |
| 2, returned AIZ | 12 errors, first2025 queue busy | Identical JSON |
| 3, Slots | 14 errors, first1052 queue busy | 12 errors, same first field/row |
| 4, AIZ3 | 11194 errors, first105 x, incomplete | 2604 errors, first6443 y, complete |

The candidate AIZ3 dump contains exactly **2604** error tuples on990 error
rows, reconciling with the report:2555 physics and49 animation errors,
bootstrap0. Its first error of any group is6443, so there is no candidate error
within the baseline's compared prefix through1124. At6443, ROM/engine X is
`$1D40`, both velocities and ground speed are zero, angle/ground mode and
camera X `$1CA0` agree, and no sidekick exists. Y is `$0782/$078D`; camera Y is
`$0751/$074D`. ROM context reports standing-object slot`$0B`. These are observed
comparison fields, not a diagnosis of the later object frontier. No such owner
was changed or pursued.

The complete chain messages preserve all axes. Baseline has the duplicate or
reordered hardware-service boundary at raw1124 plus segment2's12 queue errors.
Candidate has the unchanged segment2 axis, AIZ3's2604-error axis, and:
`transition step cap 12534 exceeded for starpost_bonus from segment 4 at BK2
cursor 17912`. The dynamic-art gap report remains failureCount0. The later
starpost entry and row6443 are the stopping frontier, outside this bounded
saved-block/camera/water continuation.

**Gate and limits:** no class newly turns red and no observed first error moves
earlier. Counts decrease in both changed segments. No errors are added within
AIZ3's common compared prefix; the first known differing row105 is removed.
The candidate's complete error list is preserved, but the baseline retained
only first/recent live mismatches and aborts at1124. An exact baseline/candidate
added-tuple diff for Slots or the newly reached AIZ3 suffix therefore cannot be
certified from the supplied archive. Candidate rows6443–7536 are new comparison
coverage after the baseline had already diverged at105 and aborted, not proof
of baseline agreement at those rows. No grouped span is expanded into invented
observations. The lead must retain this limit in any integration/frontier claim;
this lane does not claim an unrestricted exact-added-error certificate.

### Exact commands and timing

All commands use the unchanged saved `run.sh` driver in
`/tmp/aiz3-resume-20261104/`; each archive includes `command.txt` (fully expanded
wrapper and six ROM arguments plus generic S1 property), tested HEAD,
`source.diff`, submodule identities, raw Maven log, timing, XML and JSON.
The focused selectors are recorded there without abbreviation. The executions
were:

```bash
/tmp/aiz3-resume-20261104/run.sh lifecycle-red none -Dtest=TestLevelZoneLoadExecutor,TestBonusStageReturnWaterRestore
/tmp/aiz3-resume-20261104/run.sh water-green2 none -Dtest=TestBonusStageTransitionCoordinator,TestS3kSlotBonusStageRuntime,TestLevelZoneLoadExecutor,TestBonusStageReturnWaterRestore,TestSonic3kWaterDataProvider,TestSonic3kDynamicWaterHandlers,TestWaterSystemRewindSnapshot,TestBonusStageCoordinatorRewindAdapter,TestS3kSlotBonusGraphRewind,TestBonusStageRewindCapability,TestLiveRewindManagerBonusStageMode,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestArchitecturalSourceGuard,TestCommittedHardwareTimingFixtures,TestBuildToolingGuard,TestTraceChaserBoundaryGuard,TestHardwareTimingAuthorityGuard,TestFreshLevelBoundaryAfterSetup
/tmp/aiz3-resume-20261104/run.sh root-composed-focus none -Dtest=TestBonusStageReturnWaterRestore,TestFreshLevelBoundaryAfterSetup,TestS3kTitleCardHoldRoutes,TestGameLoopFreshLevelHandoff,TestArchitecturalSourceGuard
/tmp/aiz3-resume-20261104/run.sh candidate-r7 trace-replay-r7 -Dopenggf.aiz3.dumpErrors=true
/tmp/aiz3-resume-20261104/run.sh candidate-r6 trace-replay -Dopenggf.aiz3.dumpErrors=true
/tmp/aiz3-resume-20261104/run.sh candidate-seg trace-segments -Dopenggf.aiz3.dumpErrors=true
```

`water-green` used the same selector as `water-green2` and failed at compilation
before tests, as recorded above. It is preserved, not relabelled as a pass.

| Run | Queue wait, seconds | Maven-reported execution |
| --- | --- | --- |
| lifecycle-red | 311 | 1m30s |
| water-green, missing import | 596 | 1m25s |
| water-green2 | 190 | 1m20s |
| root-composed-focus | 1375 | 1m34s |
| candidate-r7 | 688 | 7m42s |
| candidate-r6 | 1155 | 11m42s |
| candidate-seg | 1290 | 4m55s |

Queue total:5605s (93m25s); Maven-reported execution total:1808s (30m08s).
Maven minute values are reported precision, not independently measured phase
timing. Exact UTC job intervals and counts are in `resume-job-timings.json`.
User wait is zero; non-job investigation, editing, inspection and reporting
were not independently timed and are not disguised as queue or execution.

The full candidate error rows, derived complete tuple list and first-error
fields, full baseline Mega log context, matched profile diffs, ROM hashes,
fixture blob inventory, temporary observer patch and comparison scripts remain
under the same `/tmp` evidence directory. No raw/regenerable report is committed.
The complete-error observation method already exists in the prior Mega
segment2 audit; this continuation reuses it, without adding a runtime API.

Final checks include diff whitespace, byte-identical AGENTS/CLAUDE, coherent
TraceChaser checkout/gitlink and citation checking. The citation checker finds
one inherited stale `loc_1578E` reference in an untouched water-coordinator
branch (old24518–24533, label now29303); it is already present at `afdf`, not a
new restoration citation or an unrelated cleanup target. The final checker
summary is near1 / ok14 / stale1 / unverifiable7; unverifiable citations are
not claimed as verified.

This remains local on `aiz3-camera-water-restore`. No develop integration,
branch push, frontier-log/SURVEY edit, other-owner change, or artifact/worktree
cleanup was performed. The task checkout and all prior lane/worktree/tmp
artifacts are preserved. No live jobs remain. The lead owns integration and
any follow-up decision on the limited added-tuple gate.

Proposed frontier text, for lead reconciliation rather than direct publication:
"Saved apparent act is restored before bonus-return water selection; ROM
mean-only restoration preserves the fresh target, and Slots holds its source
camera. Against frozen afdf, Mega Slots14→12 at unchanged first1052;
AIZ3 11194/incomplete at105x→2604/complete at6443y. All other r7 cases, r6 and
segments match baseline. 428 focused controls and102 composed title/water
controls pass without skips. Remaining frontier is standing-object Y at6443
and a later starpost-entry step cap; exact added-tuple qualification is limited
by the baseline's first/recent-only retention and abort at1124."
