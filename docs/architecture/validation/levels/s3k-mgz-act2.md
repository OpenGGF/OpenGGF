# S3K Marble Garden Act 2 coverage matrix

Selection update (2026-10-27): routine canonical entry/reload checks cover supported
axis pairs; bonus transitions retain their existing pilots. Full Cartesian
products are explicitly manual `exhaustive-audit` coverage, excluded from routine
and release gates. Historical exhaustive qualifications below are unchanged and
are not renewed by a routine pass. See the [selection record](../../research/2026-10-27-local-validation-cost.md).


Canonical binding: `S3K_MARBLE_GARDEN_2`, engine zone 2/act 1, ROM `$0201`, S3KL object set.
Edges: MGZ1 incoming structural act handoff; CNZ1 outgoing full entry.
Character routes: Sonic/Tails use collapse/rescue/flying-boss progression; Knuckles uses the separate top-band end boss and capsule route.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kMgz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kMgz2EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| EVENT / CAMERA / REWIND: quakes, chunk edits, collapse and BG rise | `TestSonic3kMgz2QuakeEvents`, `TestSonic3kMgz2ChunkEvents`, `TestSonic3kMgz2CollapseEvents`, `TestSonic3kMgz2BgRiseEvents`, `TestS3kMgz2BgRiseHeadless` | Explicit event/positioned contexts; natural trigger thresholds, full-world named spots and configuration products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / LOAD: rescue, Knuckles boss, capsule/results and CNZ fade | `TestSonic3kMgz2EndBossEvents`, `TestMgzEndBossHandoffHeadless` | Late component fights and actual local handoff checks. The method named `eventSpawnedKnucklesBossCompletesRealFightCapsuleResultsAndAutoWalk` changes the config code after the shared Sonic fixture exists; it is a Knuckles event/boss context, not typed cold-Knuckles traversal. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kMgz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| PRESENT: flying-boss warning packed art | [`TestMgzEndBossArtScaler`](../../../../src/test/java/com/openggf/game/sonic3k/objects/TestMgzEndBossArtScaler.java), production caller in `TestMgzDrillingRobotnikInstance` | The [bounded scaler correction](#packed-byte-warning-art-correction--2026-10-20) checks native copy/merge branches, separate full blocks/partial tail, vertical carry and mapping banks. It does not certify full-scene pixels, DMA/readiness timing, restored drawing or additional viewport/donor/team products. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | Existing MGZ trace bindings and [frontier log](../../../status/trace-frontier-log.md); no new strict trace run in this slice. | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Packed-byte warning art correction — 2026-10-20

Native authority is `sub_246DA` through `loc_24BD6`, including `loc_24984`,
`loc_24906`, `loc_2479C` and `sub_24802`, plus `Map_ScaledArt`. The source is
one ROM-backed 4096-byte bank with 64-byte scanlines. The former pixel-coordinate
sampler chose the wrong nibble from alternate source bytes and missed the
partial tail's after-advance clearing. Its test-only repeated-coordinate
formula and unexplained output CRCs were rejected as independent evidence.
The replacement preserves all modified-scale 4..9 special copy/merge branches,
generic full blocks versus the partial tail, separate vertical fractional
carry, existing mapping extents and column-major tile indices. It allocates
one 64-byte row buffer per call and adds no retained state or new art producer.

Literal tests include `12 34 -> 14` at original scale 4, modified 9's ninth-byte
skip, fractional generic 12 and integer generic 16, full-block retention at
index 64, partial-tail clearing and 32-row banks. Stock MGZ source at ROM
`$36C572..$36D572` is pinned by SHA-256
`a6217a7e6879aec9d7845e4f4fbeeb9dbed0d5bc66c5449b45a53a62dbb751f9`.
At original scale `$7C`, source row 16 bytes `00/44/55/BB` at offsets `0/16/32/48`
produce `04 50`, with the fourth sample cleared. A separately pinned stock
EggRobo bank is only an additional input vector; the production helper remains
MGZ-only. Both banks duplicate high/low nibbles, so interior synthetic nibble
differences alone are not stock visual proof.

Focused validation completed at 2026-10-20 21:09:22 UTC on base `cf3cfd0b7`
plus this change, branch `feature/ai-s3k-packed-art-scaler-20261020`, isolated
T3 checkout `<OWNED_WORKTREE>` (the task-owned isolated checkout).
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` (25.0.4.1), its `bin` first on `PATH`,
`env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null`, and the Main absolute
`tools/testing/maven_queue.py --normal -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl -Dtest=... -Dsurefire.forkCount=1
-Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B` completed
226 tests / 0 failures / 0 errors / 0 skips. The selector comprised the whole
classes `TestMgzEndBossArtScaler` (13), `TestMgzDrillingRobotnikInstance` (64),
`TestMgzEndBossHandoffHeadless` (7), `TestSonic3kPlcArtRegistry` (82),
`TestSonic3kBootstrapResolver` (6), `TestSonic3kDecodingUtils` (3),
`com.openggf.game.sonic3k.TestSonic3kLevelLoading` (36),
`TestS3kAiz1SkipHeadless` (8) and `com.openggf.tests.TestSonic3kLevelLoading` (7).
All three verified root ROMs were supplied by absolute `sonic1.rom.path`,
`sonic2.rom.path` and `s3k.rom.path`; reports and native temporary files used
this checkout's `target/packed-scaler-focused-20261020/candidate-1/`.
Queue waiting was 11 s; Maven execution was 1m22s, including compilation.
The tracked source/build-input fingerprint was SHA-256
`8828862e4652900d20573a057630b94ba0fedfb6723cd149ba7faa5774f2221c`;
the exact argument vector and fingerprint definition remain in the task's
existing implementation registry entry for Root's handoff.

Inherited native rendered-scene, DMA/readiness timing, full-world
restore/re-render and route/preset/donor/team breadth remain open. No strict
trace, SSZ producer adoption, broad smoke or full-suite result is claimed here;
Root owns combined integration and delivery.

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

[`TestS3kMgz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kMgz2EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

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


## Update-owned blink candidate — 2026-10-30

The live independent-render scheduler moves `MgzDrillingRobotnikInstance`'s
existing `renderTick` progression from rendering to the admitted boss update.
`TestMgzDrillingRobotnikInstance#repeatedBossPicturesDoNotSpendTheBlinkClockAndOmittedPicturesDoNotFreezeIt`
checks omitted/repeated pictures and the existing object capture/restore path.
`TestMgzEndBossKnuxAdmission#activeKnucklesBossBlinkAdvancesOnUpdatesRatherThanPictures`
also covers the Knuckles override and destructive graph restore; 141 focused
boss/anchor checks passed without skips on the composed integration candidate.
This is an ownership correction for the existing five-tick blink, not a new
ROM timing oracle or a complete MGZ route qualification. The scheduler's bounded
native route evidence uses AIZ/EHZ; MGZ scene pixels, encounter breadth and the
inherited route obligations above remain separate. Focused results belong in
[the scheduling record](../../designs/2026-09-14-independent-render-frame-rate-design.md#live-region-scheduling-candidate--2026-10-30).
