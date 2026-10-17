# S3K Hydrocity Act 2 coverage matrix

Canonical binding: `S3K_HYDROCITY_2`, engine zone 1/act 1, ROM `$0101`, S3KL object set.
Edges: HCZ1 incoming structural act handoff; MGZ1 outgoing full entry.
Character routes: Knuckles and Sonic/Tails use different native starts and end-route contexts.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kHcz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kHcz2EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| OBJECT / EVENT / CAMERA: raised floor, wall and twisting loop | `TestS3kHcz2RaisedFloorWallCollisionHeadless`, `TestS3kHcz2TwistingLoopRegression`, `TestS3kHcz2SpindashStuckRegression`, `TestSonic3kHCZEvents` | Positioned/event regressions; cold traversal and five-preset/donor/roster interaction products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / REWIND: end boss and water columns | `TestS3kHczEndBossGraphRewind`, `TestHczEndBossInstance`, `TestHczEndBossWaterColumnControlPolicy` | Component/arena graph checks; actual arena admission, completion and outgoing lifecycle breadth remain separate. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kHcz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | `TestS3kSonicTailsHcz2SegmentTraceReplay`, `TestS3kTailsFullChainHcz2SegmentTraceReplay` | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Qualification — 2026-10-14

Base/runtime lineage: `5b2ff8e78`; branch tip before this slice `390ebd765`, plus this commit’s explicit native-scripted art owner and test. Worktree `.worktrees/ai-s3k-parity-hpz`, Java 25.0.4.1, all three original ROMs by absolute path, `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null`.

`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kCanonicalEntryLifecycle,TestLevelPlayableArtInitializerModArt,TestDonorTailsTailBanks,TestSpriteManagerRewindCapture,TestS3kCnzSoloCarryInHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils"` with `-Ds3k.rom.path`, `-Dsonic1.rom.path`, `-Dsonic2.rom.path`, then `test`: request 31958 completed 10:58:41 BST (09:58:41 UTC), 169 tests/0 failures/0 errors/0 skips, Maven 37.197 s. The new class contributes 70 cases/0/0/0 (7.070s); these are bounded focused checks, not broad smoke/full-suite certification. No integration/push is claimed by this local record.

The shared selection’s first measured CNZ NPC art failure, rejected alternatives and coherent correction are recorded in the [CNZ1 matrix](s3k-cnz-act1.md). They do not imply a new native scene-parity qualification for this act.

## Finite normal-entry products — 2026-10-14

The independent per-act class above supplies 140 distinct rows: native 75, S1 Sonic 20, S2 Sonic 25 and S2 Tails 20, each spanning 320/352/400/528/800 production presets. Solo, supported single follower, mixed two, duplicate two and declared seven-follower examples deduplicate only when donor character support makes rows identical. Unsupported follower names are explicitly substituted by the generator's declared Sonic intent; that is not a menu fallback or a participant cap. Every admitted identity and CPU leader is checked at entry and every ordinary/replay step. Two full-world restore/45-frame forward cycles remain in the same loaded world.

The eight-act/animation-consumer request 83519 on `6e9d951aa` plus the initial carry-art candidate completed 1186 tests/1 failure/8 errors/0 skips at 11:44:40 BST (10:44:40 UTC), Maven 1m53s. All seven other acts passed their 140 rows (CNZ1 also passed 3 native mixed/duplicate/seven-follower carry-authority vectors). AIZ2 passed 139/140; its S2-Tails/800/seven-Sonic case reordered player refs 3/5 in AutoSpin's unordered identity map during immediate restoration. Separately, 8 mocked art-manager consumers exposed a bridge-dispatch setup issue, corrected by the carry slice and its 61-case gate. These consumer errors do not erase the completed per-act rows and are not described as a green invocation.

After qualified carry art `a54c1a8ae`, Root's collision dependency `eea476471` and canonical player-key map capture dependency `e77b387a3` were adopted as `6c5ae479d`/`cacd63b77`. The valid-source follow-up request 30705 completed 163/0/0/0 at 12:06:02 BST (11:06:02 UTC), Maven 1m23s: AIZ2's complete 140 rows, player-reference codec 5, AIZ2 object codec 3, object-manager snapshot 11 and carried-art/handoff 4. Its exact selector is `TestS3kAiz2EntryMatrix,TestRewindPlayerReferenceCodecs,TestNativeCarriedPlayerArtBinding,TestAiz2ObjectRewindCodecs,TestObjectManagerRewindSnapshot`. All use the local queued lean wrapper, `-Dmse=off`, surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY, null OpenAL and original absolute S1/S2/S3K ROM properties. An earlier dependent request was accidentally submitted during dependency prose conflicts and was canceled at compilation with exit 130; it executed no tests and earns no behavioral credit.

Thus 1120 distinct normal-entry rows are qualified through retained 980 unaffected rows and the repaired AIZ2 source's 140, with explicit source scope; this is not one pristine 1120-case green run. No current native pixel, strict trace, physical post, death/reload, incoming/outgoing lifecycle or whole-act completion claim follows from this entry product. The listed remaining mechanic, route and presentation obligations stay open.

## Independent actual restart floor — 2026-10-14

[`TestS3kCanonicalDeathLifecycle`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalDeathLifecycle.java) independently boots this actual act binding, native 320 solo Sonic, then invokes the production pit-death owner twice. It writes no position, clock, ring, life, checkpoint or destination state. Each real `GameLoop` reload replaces the object/runtime owner, costs exactly one life, restores the current typed roster, resolved camera width and P1 focus, and retires nonempty outgoing live history. Title/fade/fresh assembly release is required separately from any intrinsic intro control. Each released same-world destination has immediate full-registry restoration and two 45-frame neutral forward cycles; no raw snapshot crosses the load.

Qualified source: `5d334596e` (includes early donor boot, frozen native camera and single production fade ownership) plus this commit's private dead-main mask correction and the two new death test classes. Queued lean request 40522 completed 12:59:51 BST (11:59:51 UTC), Maven 1m16s: 247 tests / 0 failures / 0 errors / 0 skips. The eight-act floor contributes 8 cases; `TestMainDeathVerticalWrap` contributes 18 literal main-motion/real-roster/live-mask cases; existing death/radius/movement/CPU rewind, reverse-gravity and S1 LZ3 wrap neighbors contribute 221. Command: `python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestMainDeathVerticalWrap,TestDeathRestartRoutineParity,TestDeathRadiusTransition,TestS3kCanonicalDeathLifecycle,TestS3kReverseGravityBoundary,TestS1Lz3VerticalWrap,TestPlayableSpriteMovement,TestSidekickCpuControllerRewindCapture"`, original absolute S1/S2/S3K ROM properties, then `test`; DISPLAY/WAYLAND_DISPLAY unset and `ALSOFT_DRIVERS=null`.

This is the independently executed native 320/Sonic restart floor, not the remaining finite lifecycle cross-product, physical pit traversal, post activation, route completion, native pixels or a strict trace sweep. Shared-change smoke, integration and push remain lead-owned and pending. The [MGZ1 record](s3k-mgz-act1.md#native-main-death-wrap-owner-correction-2026-10-14) retains the measured gameplay blocker and rejected partial repair.

## Finite actual restart products — 2026-10-14

[`TestS3kHcz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kHcz2EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

After each title/fade/fresh-assembly release, the complete registered world is captured, advanced for 45 neutral logical ticks, restored immediately and replayed twice. Each forward tick checks P1 survival, unchanged lives and actual configured owners. Intrinsic intro control remains native-owned; this is not a forced unlock or a raw restore across the load. Physical pit traversal, physical post capture/respawn, completion edges and later mechanic lifetimes remain separate.

One coherent focused invocation qualified all eight acts: command session54147 on runtime `e57236d519a861c9fbc43ba867230793dbd6fb0d` plus this commit's nine test-only deltas, 1120 tests / 0 failures / 0 errors / 0 skips (each act 140/0/0/0), actual terminal exit0 at 13:42:47 BST (12:42:47 UTC), Maven 3m05s. Exact selection: `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix`, `TestS3kIcz2EntryMatrix`, each restricted with `#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice` in the comma-separated `-Dtest` selector. Command prefix: `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`; original absolute S1/S2/S3K ROM properties were supplied, followed by `test`. Entry rows were not rerun merely to repeat their unchanged evidence. This is finite lifecycle qualification, not complete act, native-pixel, strict-trace or arbitrary roster certification; combined smoke/integration/push remain lead-owned.

## Entry replay input correction — 2026-10-17

`CanonicalEntryMatrixSupport.advanceEntry(fixture, row, frames, right)` passed its `right` flag as the second argument of `HeadlessTestFixture.stepFrame(up, down, left, right, jump)` in every committed revision from the helper's introduction in `a54c1a8ae` through `0197c5a5a`. The `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice` runs recorded above on those sources therefore held **Down**, not Right, in each 45-frame capture/replay window, so a grounded P1 ducked in place instead of moving. Request 83519's uncommitted initial candidate cannot be inspected here; treat its replay input as unverified rather than as Right. Those records keep their original commands, sources and counts and remain evidence within that Down-held scope: actual start table, owners, survival, unchanged lives and full-world restore/replay equality. They did not exercise held-Right movement, the camera scroll it causes or the object placement that scroll admits. The restart products' post-release windows were already neutral and are not affected.

The helper now passes the flag as `right`. [`TestS3kCanonicalEntryRightInput`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalEntryRightInput.java) drives the same helper on native 320 solo Sonic in CNZ2, whose start is grounded on flat floor, and checks the production joypad path on each of 45 frames (Right held; Up, Down and Left absent; not crouching). It then requires P1 still grounded with `ground_vel` 45 × `$C` (`Sonic_Move` adds `Acceleration` for `button_right` in `Ctrl_1_held_logical`) and forward movement.

Corrected qualification, all on `0197c5a5a` plus this commit's test-only deltas (helper SHA-1 `49e31e0026db`), command prefix `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, original absolute S1/S2/S3K ROM properties, then `test`:

- Eight-act entry selection: `-Dtest` lists `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix` and `TestS3kIcz2EntryMatrix`, each restricted with `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`, plus the first version of the input test. It finished at 02:02:24 UTC after a 193 s queue wait and 1m46s of Maven: 1121 tests / 1 failure / 0 errors / 0 skips. Every act passed 140/0/0/0 with held Right. The one failure was the input test's own premise. It first used MGZ1, whose Sonic start (ROM `$C0,$F00`) is airborne, so `ground_vel` stays zero while air control raises `x_vel` by 24 each frame. The test was moved to CNZ2; the helper was not changed.
- Revised input test with the helper restored to its `0197c5a5a` bytes: fails at Right frame 0 (`isRightPressed` false), 02:04:26–02:05:01 UTC, 1 test / 1 failure / 0 errors / 0 skips.
- Revised input test with the fix: 02:05:15–02:05:44 UTC, 1 test / 0 failures / 0 errors / 0 skips.

Death/reload products, combined smoke and integration were not rerun here. This requalifies the held-Right entry floor only; it is not act certification.


## Retained HCZ1-to-HCZ2 carrier boundary — 2026-10-17

`TestSonic3kHCZEvents` now binds incoming X-lock ownership to both current and
target bounds and checks each carrier's captured `$3A` target after camera motion.
Actual manager/player/camera restoration twice replays both carriers through P1
release and native size-child ownership; manager recreation and the schema guard
also check distinct per-player target words. These local fixtures pass within
worker focused1591/0/0/4; named guards113/0/0/0 pass on `b9c1c8b3d`, including
Root prerequisite `534a69e74`. They add no fresh-entry matrix products.

The [audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#final-single-invocation-strict-qualification)
records three assertion-red qualifiers, returned-HCZ first reported non-camera
physics7134 and absent owned exit, the separate load7126 boundary, and HCZ slice
4353 spans first9482 `air` without matched-baseline attribution. Native-only 320
Sonic+Tails images are inspected; the engine GLFW pixel oracle is blocked at init.
Cold traversal, other products, arena completion and cross-load rewind policy
retain their existing obligations; no general HCZ2 certification.


Followup after866 (2026-10-17): the independent load/title owner is qualified narrowly by old-red/green, focused134, guards131 and a canonical prefix closing HCZ1-entry row7126. The distinct alternate retains its one26179 ring span. This adds no HCZ2 cold/fresh/arena or cross-load matrix product. Pre-fix dc338/866 full-slice comparison has identical windows but mixed span deltas (4699/4353);3738 is not a matched baseline. New-source full slice and returned HCZ11 remain unqualified. [Exact evidence](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#bounded-followup-qualification-and-limits).


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


HCZ7134 test-only decision (2026-10-17, base3896): independent one-tick coupled
CPU/movement/wall/integration and no-nudge control pass in focused86494
(7/0F/0E/0S total). Generic ordering is supported; actual7134 branch/probe/terrain
is still unknown. Equal final X can conceal different velocity/Push results.
No new route, matrix product or runtime qualification. [Exact evidence and
missing live fields](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#hcz7134-coupled-cpu-and-ground-wall-decision--2026-10-17).


R6 live witness (2026-10-17, Root86171 composition): one canonical prefix through
7135 observes correct CPU nudge/acceleration/projection but missing wall response
at7134. Engine final0176.F800/vx-ground0270/noPush vs native0175.F800/0170-0/Push.
BG collision is active; selected distance, translated BG terrain and native layer
comparison are still missing. Prefix217 errors/7136 rows is a partial window,
not improvement versus full R4 or a matrix certification. No runtime change or
new independent slice/GLFW qualification. [Fields/clocks and seam limit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r6-actual-hcz7134-cpu-and-wall-input-witness--2026-10-17).


R7 selected BG attempt (2026-10-17): actual7133 projected wall selects BG+18,
empty tile0, provider0200/0500, translatedFF7D/0338. The single invocation67242
fails an observer shared-probe count assertion after7133 (1/1F/0E/0S), so actual
7134 selected distance/terrain remains unobserved. Partial206 errors over7134
reference rows matches R6's same earlier window; no matrix/route certification.
Native P2 layer/raw BG state fields are absent; inferred native diffs are kept
separate. No retry or runtime edit. [Evidence and excluded stale vertical pool](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r7-native-inventory-and-partial-selected-bg-witness--2026-10-17).


R8 corrected observer (2026-10-17, production86171 unchanged): original prefix
44688 reaches7135 with the same217 comparator errors/7136 reference rows and
first7134sidekick_x0175/0176. Observation assertions pass1/0F/0E/0S. Actual7134
FG/BG/combined+31 selects BG, transformed0180/0838→FF80/0338, provider0200/0500,
empty BG descriptor0/no flips/primary-secondary collision0. Exactly one lexical
wall-owner call per observed tick; floor/clamp calls pass unchanged. Native P2
layer/selected terrain remains missing; no wrapping fault or production fix
established. No new route/matrix/GLFW qualification. R7's partial assertion red
is preserved. [Full selected metrics, passivity and native gaps](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r8-lexical-wall-owner-observer-and-actual7134-bg-result--2026-10-17).


R9 source inventory (2026-10-17,32a): retained native7134 BG-1 now qualifies
layers0E/0F/provider0200/0500. Source resolves FF907F94 to current HCZ shared
16×16 ROM bytes and adjacent703E0179 to retained HCZ1 128×128 table tail.
One cold-HCZ2 regression32483 is1/0F/1E/0S at missing block224 before lookup
assertions; it is not an active-transition old-red. No runtime/API fix, retry,
new engine trace, rewind/matrix/route qualification or native execution here.
Internal address lookup plus resident-table/capture design handed back to Root.
[Exact source and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r9-native-word-layout-byte-provenance-and-resident-table-boundary--2026-10-17).


### R10 encoded collision and resident asset ownership — 2026-10-17

Actual HCZ1→HCZ2 production job/request/build/commit now preserves unwritten
128×128 destination bytes and encoded layout row addressing. Current live16×16
bytes select A216 at FF80/0338; retained prior-act0179 selects the adjacent cell.
Positive MOVEA.W aliases read the current constructor ROM's immutable bounded
prefix, with source-derived zero/nonzero controls; unknown regions still fail.
No public API expansion, observation hydration, zone or row fit.

Independent transition old-red21043→green6557; ROM owner old-red53171→final3897
7/0F/0E/0S. Every snapshot key matches after two forced whole-world recreations,
live definition/map mutations, scene swap and forward replay. Captures start
after the intentional cross-load history reset; no retired timeline is revived.

Original prefix49095: actual7133 FG18/BG2/combined2;7134 FG31/BG-1/combined-1,
P20175.F800/vx0170/g0/pushing matches native. Through7135, HCZ7136/14208 rows
has206 errors (11camera physics/195animation), lag53 and no non-camera physics
mismatch. Observer pass is not full parity. Full returned-HCZ, independent slice,
alternate, viewport/roster/cold/PAL/auxiliary breadth and GLFW remain open.
[Exact command, hashes, inherited failures and all compared windows](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r10-encoded-collision-words-resident-destinations-and-rom-aliases--2026-10-17).


### R11 HCZBlock Full2 visibility and whole-world replay — 2026-10-17

On152d07 composition, only HCZBlock's existing offscreen bypass capability
changes to match loc1F3CA→SolidObjectFull2. Both the Player2 render gate and the
object loc1DF88 gate are absent from that native path. Independent actual HCZ2
object controls use visible/visible, visible/offscreen and offscreen/offscreen
participants; corrected genuine old-red85345 fails only the last two. New four
cases pass within41923's157 focused checks (mandatoryS3K4 included), including
two forced whole-world recreations and complete registered-key forward replay.
Affected source/authority/rewind guards22063 pass118 without skips.

Final original prefix7687 consumes7809/14208 HCZ rows,206 errors=11camera physics+
195animation, lag53, warnings/bootstrap0. Native7807/7808 P2 endpoints now match;
no non-camera physics mismatch through7808. Earlier complete gameplay windows
0/2/4/6/8/9 remain zero errors. First remaining all-group error3475 companion
animation17/1C; later non-camera frontier not observed. The once final observer
contains only post-row reads and is removed after facts are durable. No full
act/returned exit/slice/alternate/GLFW/viewport/roster/native presentation
certification; Root owns remaining route and delivery validation.
[Source, controls, clocks, commands and exact window comparison](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r11-hczblock-full2-visibility-owner-closes7807-through7808--2026-10-17).


### R12 actual composed full-window frontier and slice cause — 2026-10-17

No production/test edit onbef8; R11 code/test bytes retained. Once existing full
canonical17194 completes all14208 returnedHCZ rows with125612 field errors
(107498physics/18114animation),lag59,warn/bootstrap0. First non-camera now8329
y_speed0000/-0680; pre-R11 same-window133258 started7807sidekick_y. Earlier six
gameplay windows remain complete zero-error. The giant_ring exit is still absent,
so no onward segment/playable exit or act certification. Four suppressed unmatched
hardware completions are not attributed to a new timing owner.

Separate original82EA slice prefix68487 reaches10835, strict1/1F/0E/0S and104
spans/10773 compared rows.10833camera copy019A (native0199) makes the real render
edge negative instead of inclusive0; valid cached false correctly yields counter1
on10834, then resets10835 after visibility returns. Fresh-entry delay is disabled.
No CPU/cache/width fix. Selected camera P1/history/latch inputs were not captured;
actual native aux lacks those selected-history/scroll fields. Observer's
PRE_MAIN_LOOP label corrected to loop tail. Independent disconfirming test design
is handed back, not run; all temporary observers removed before canonical.
Full independent-slice delta attribution, later8329 owner/exit, other BK2/GLFW/
viewport/roster/native presentation breadth remain open. Root owns delivery.
[Exact source, clocks, comparison windows and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r12-slice-cached-visibility-consequence-and-full-composed-canonical--2026-10-17).

### R14 slice camera history input limit — 2026-10-17

On unchanged0cd178d8f production, sole original82EA prefix63258 compares10773
rows through10835 with104=92physics+12animation spans, strict1/1F/0E/0S, first
9482air1/0. Four actual camera-entry samples prove delay2→1→0 selects0226 then
023A; at10833currentP10231.CD00 differs from latesthistory023A. Correct native
camera arithmetic for these engine inputs yields019A. No Camera/CPU/counter fix
or independent old-red is supported. Native final poses/index/copies do not
supply matching selected history, H_scroll, forced/latch or camera-entry P1.
No new native/fullslice/canonical/alternate/8329/GLFW/broad or matrix breadth
qualification. Temporary observer removed; R12's returned8329/absent exit and
mixed whole-slice vector limits remain. Root owns missing native-plan review.
[Actual source pins, fixture identity, clocks and passivity](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r14-slice-camera-entry-history-inputs-no-camera-or-cpu-repair--2026-10-17).

### R15 original native selected camera input observed — 2026-10-17

One original82EA native power-on pass46154,0..38005, observes raw10831..35.
All four10832..35 endpoint controls,60 ordered/passive callbacks and immediate
pre/post identity guards pass. Actual10833 RecordPos0239.E0 writesFFE548;
delay0100 selects0239 ->camera0199. R14 engine latest023A ->019A uses the same
correct rule. Native prior selected0225 vs engine0226 shows producer divergence
already before this camera consequence. Exact earlier engine P1 writer remains
unknown; no Camera/CPU or unobserved late-object repair is justified.
No new engine replay/focused pass, matrix product, full route/exit, alternate,
GLFW or native visual acceptance. Existing strict slice/8329/exit limits remain.
[Exact original inputs, byte pins, CPU/publication phases and disconfirming proposal](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r15-original-native-camerahistory-input-plan--2026-10-17).


## R18 spinning-column owner witness (2026-10-17)

On Rootf196, one unchanged original AD40 boot prefix10733 reaches row8720
inclusive. Actual8717 column slot11/P2 binding is intact, cached visibility
valid/false, private rider active/prior-standing true, yet the column selects
hold. Native AUX binds the same code326B6/sub2/interactB32E/SST11 and publishes
Air/control release at8717. The exact engine checkpoint retains player OnObj but
leaves its private rider standing flag false; next8718 existing release clears
OnObj/control without Air. See the [R18 audit](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r18-hcz8717-column-cached-visibility-owner-witness--2026-10-17).
Strict prefix remains red:213=15physics+198animation/8721 rows, first non-camera
8717 sidekick_air1/0, lag59/0warnings/bootstrap,1/1F0E0S. Earlier six gameplay
windows complete zero. No independent old-red, runtime repair, new native,
whole canonical or viewport/roster qualification. Logs are removed after facts;
this records an owner question and preserves all existing coverage limits.


## R19 independent column release old-red (2026-10-17)

TestHczSpinningColumnCachedRelease uses actual ROM factory/normal allocation,
SST FullSolid landing/capture and the real copied-camera/body visibility
publisher. Focused79315 on R18df2bbf/runtimef196 is **2/1F0E0S**: visible hold/jump
passes; the intended offscreen release/retained-standing recapture assertions
fail. First hidden dispatch holds Airfalse/controlTTT and moves0408→0407 while
OnObj, actual ride binding and native standing bit remain. Twice forced whole-
world recreate/forward comparisons at both before-flip and actual post-dispatch
boundaries pass before native expectations. Correct native release/post-release
replay is **not** proven; the old state is held. The existing standing-bit getter
supports a column-local correction, with no shared/API widening. See the
[R19 source/design/results](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r19-independent-column-cached-release-old-red--2026-10-17).
No runtime fix, new native, trace qualification or broader roster/viewport
coverage; the existing8717 frontier and all earlier qualification limits remain.


## R20 local column release and measured limits (2026-10-17)

On f196+local column owner (d8119e4a), unchanged R19oracle now passes real SST
capture, actual cached release/retained-standing recapture and twice forced
whole-world replay around before-flip and actual release boundaries. Visible
hold/jump and unmanaged clear-held/positive-control controls also pass. Sole
focus62518 is73/0F0E0S, including required S3K loading checks. No shared/API change.
Root-reviewed source then runs one originalAD40 boot prefix93493 through8720;
actual8715..20pose/fractions/velocities/status/control match the retained native
sequence, including8717Airtrue/control0/OnObjtrue. Its JUnit1/0F is **not strict
qualification**: unfinished seg11 aggregate was not captured. No current total/
group/firstNonCamera count or213→current comparison is available. Earlier six
completed gameplay reports/gap remain zero. See [R20 evidence and reporting gap](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r20-local-column-release-owner-and-prefix-reporting-limit--2026-10-17).
No later frontier, full returned-HCZ/exit, independent slice/alternate, new native,
GLFW or wider viewport/roster qualification; dedicated hurt/dead cases remain open.
Stop after handback before any additional execution.


## R21 actual partial report closes R20's count gap (2026-10-17)

One separately authorized reporting-only execution95476 on the same frozen
d811/c35d/6c741 source actually records8721 compared/14208 reference rows,
completefalse: **206=11camera physics+195animation**, firstNonCameraNULL through
8720, lag59/warnbootstrap0. Strict1/1F0E0S is solely0-versus206 after original
prefix cleanup. No diagnostic failure or behavioral variant. Same-window10733
is213=15physics+198animation; only aggregate/group deltas and available recent
identities are proved, not an exhaustive error-array difference. First all-group
3475sidekick_animation_id17/1C remains; six earlier complete windows/gap are zero.
[Exact source/identity/phase/report and counts](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r21-actual-final-prefix-report-qualifies-the-local-column-repair--2026-10-17).
With73focused0skip, real twice-world release/recapture and R20's native-matching
8715..20 endpoints, the local owner is qualified through8720. No later frontier,
full returned-HCZ/exit, independent slice/alternate, new native, exact GLFW or
wider roster/viewport/auxiliary certification. Hurt/dead and multi-owner breadth
remain limited to the recorded source argument. No further execution here.


## R22 composed fullcanonical returned-HCZ window (2026-10-17)

On committed7df991e289b93f92b34e10e79dc4f9900ec67eaa, unchanged maintained
original AD40 fullcase76850 (trace-replay/all3ROM/normalqueue/EGL/singlefork)
finishes1/1F0E0S, no skips. ReturnedHCZ completes all14208 rows:
**11118=10054physics+1064animation**, lag59/0warnings/bootstrap, firstNonCamera
**8775 sidekick_status_byte native000B / engine000A**. Repaired8717 is passed;
no new writer is observed. Earlier complete six gameplay windows remain
all-group zero. Actual13gaps/0failures includes giant_ring77284 and SS6→hcz_3
stage_exit85097: returned-HCZ exit is present, SS parity is not certified.
Seg13 remains partial (5393 reference, exact compared count not emitted),63295
errors/first287y06D4/06DA/recent3208, ownership loss88306 atLEVEL/gen10/zone1/
act1; four suppressed unmatched hardware edges remain. No onward route green.
[Exact source/command, group/window table, recent arrays and timing limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r22-composed-full-canonical-reaches8775-status-strict-route-remains-red--2026-10-17).
The available recent5 ring does not permit exhaustive residual-set comparison;
historical49197 total26153 is same-window aggregate evidence only. R21's206
partial and73focused source epochs remain distinct. No independent slice,
alternate, auxiliary/GLFW/native or viewport/roster breadth is newly qualified.
No runtime edit/probe/rerun; next8775 causal scope belongs to Root. Own docs-only
accounting/consumed cleanup follows this single maintained route measurement.


## R25 bounded8775 facing-writer observation (2026-10-17)

On51320/Java7df, sole originalAD40 boot prefix8612 compares8777/14208,
completefalse:207=12physics+195animation, firstNonCamera8775status000B/000A,
lag59/warningsbootstrap0. JUnit1/1F0E0S is the unchanged strict0-versus207
assertion after original cleanup; no observer error/skip/retry. Existing six
complete earlier windows/gap remain zero. Actual setter stack identifies
SidekickCpuController.updatePanic3424, with moveLock0/g0/spinfalse/pinballfalse/
manual0/signedlockfalse. Engine moveLock1→0 is observed across preceding8774,
but its writer and native gate inputs are missing. No CPU/column/facing repair
is justified from the correct current PANIC arithmetic. [Exact source/command,
phases/clocks/stack, report and native limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r24r25-actual8775-facing-writer-native-guard-still-missing--2026-10-17).
This does not change R22's full14208/actual exit or certify seg13ownership88306,
slice/alternate/native/GLFW/roster/viewport breadth. Temporary source/classes and
inspected own diagnostics are removed after facts; no runtime change/new run.

## R28 native8775 PANIC guard observation (2026-10-17)

On513781/Java7df/native4fdf, after separately reviewed source and compiler,
one originalAD40 power-on pass observes raw8773..76/input71848..51 only.
Session66263 exits0; all4original endpoint controls,32callback+4endpoint
passivity checks and conditional callback order match. Actual nativeCPU/PANIC
executes, returning13F44→13FBE with move_lock001D on all four frames, idle/
spin/g/signedCtrl2lock0. No facing hook executes. FullVInt00011644..47,
LF8715..18 and callbackCompletedFrame71848..51 are separate from after-core.
P2pose/velocities and column release/recapture endpoints match original CSV/AUX.
[Exact command, reviewed hashes, counts/phases and retained originals](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r26r28-original-native-panic-returns-on-retained-move_lock--2026-10-17).
Historical R25engine moveLock0 at8775 differs upstream of its correct facing
choice. Earlier native timer producer and engine1→0 writer remain missing;
no shared timer/movement/CPU/column repair or independent old-red is proved.
Native observation is not engine trace green; R22full11118/first8775, actualexit,
R25partial207 and separate seg13ownership88306 remain. Originals held for Root
read; no additional run/fixture/runtime change or broader certification.

## R30 actual8774 move-lock writer (2026-10-17)

On24cd/Java7df plus two reviewed passive sources, sole originalAD40 prefix41203
is1/1F0E0S, no skip/observer failure. Actual8777/14208 partial207=12physics+
195animation/lag59/warnbootstrap0/first8775statusB/A; serialized03052 summary
matches R25's bounded report, not a complete error-vector proof. One real setter
at8774 old1/incoming0 is doSlopeRepel reached from modeNormal. PREAirtrue becomes
false by setter time, OnObjtrue; actual Air writer and prior native001D producer
remain unknown. This proves the countdown caller, not a global countdown defect.
[Exact phases/stack/source/command/results and limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r29r30-actual-move-lock-writer-reaches-ground-dispatch--2026-10-17).
All10records,8reports/8owners/XML/TXT inspected; source/classes restored/removed,
raw18files retained until Rootread. No repair/oldred/newnative/broader coverage.
R22full11118/exit/R28nativeguard/seg13ownership88306 remain prior epochs.


## R38 to R41 stale-air ride capability and current canonical (2026-10-17)

An independent real bubble/column/visible-CPU inlet establishes genuine final
lock35/34 old-red only after two held-world plus two released-world forced
recreation/forward cycles. Grounded K-1/natural0/old controls pass. Local column
capability a170 (all other Java7df runtime unchanged) plus unchanged testdb5a
passes one exact affected R40focus:76/0F0E0S, mandatory S3K4 included. No generic
CPU/timer/movement change. R41 then runs one original maintained fullcanonical:
returned HCZ complete14208 **6553=5489physics+1064animation**, firstNonCamera11920
sidekick_status_byte0002/0003, lag59/warnbootstrap0. Animation3475 remains.
Earlier six windows zero; actual SS6 entry/hcz_3 return observed, but seg13
ownership still fails88306/actualact1 vs expected2. Its incomplete summary is
byte-identical to R22. Recent5 is not a full error vector; lower11118->6553
cannot certify all fields. [Exact commands/windows/source/results/limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r41-original-canonical-advances-to11920-strict-route-remains-red--2026-10-17).
No current native/independent-BK2/caller-producer/general-route certificate;
strict residuals and later ownership remain. Originals retained for Root read;
no additional run, source variant, commit or delivery action in R41.

R42 Root DIRECTread accepts all20current originals. Comparator warn0 remains
separate from actual runtime468SMPSshadow+2FAST+1markRemembered warnings. Local
preservation/cleanup does not change this coverage or prove the11920 writer.


### R47 original canonical timeout-status measurement — 2026-10-17

After independent S2/S3K timeout old-red->R46focused156green, one unchanged
original AD40 fullcanonical35289 on d712+CPU8930 measures **complete returned
HCZ14208, TOTAL6511=5447physics+1064animation**, firstNonCamera13322status09/01
(lag59/warnbootstrap0), advancing historical R41first11920. Earlier animation
3475 and strict residual remain; recent5 is not full-vector/no-regression proof.
Six earlier complete windows stayzero. Actual giant_ring/SS6+return semantic
assertions pass, but segment13 remains incomplete with ownership88306/gen10/
act1-vs2 and4suppressed hardware identities, so no fullroute certificate.
All original reports retained for RootDIRECTread; no runtime probe/native or
actual11920 caller proof. [R47 exact result/limits](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r47-original-canonical-firstnoncamera13322-strict-red--2026-10-17).
