# S3K IceCap Act 1 coverage matrix

Canonical binding: `S3K_ICECAP_1`, engine zone 5/act 0, ROM `$0500`, S3KL object set.
Edges: CNZ2 incoming full entry; ICZ2 outgoing structural act handoff.
Character routes: Sonic has the native snowboard intro; Tails and Knuckles use separate starts/entry contexts. Checkpoint restoration bypasses snowboard allocation.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kIcz1EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kIcz1EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| ENTRY / OBJECT / CAMERA: snowboard startup, slope and crash | `TestS3kIcz1SnowboardIntroHeadless#sonicAloneRunsSnowboardIntroAndReleasesControlAfterCrash`, `#checkpointRespawnDoesNotRestartSnowboardIntro` | Actual Sonic intro motion plus declared checkpoint state. Existing checkpoint setup is not physical starpost contact or a new all-config death/reload qualification. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| OBJECT / REWIND: ice blocks, freezers and segment columns | `TestS3kIczIceObjectRewind`, `TestS3kIczPlatformHazardRewind`, `TestS3kIczFreezerGraphRewind`, `TestS3kIczSegmentColumnGraphRewind`, `TestS3kIcz1PathFollowPlatformHeadless` | Non-default constructed/local families; not every act placement or all supported configuration products. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / LOAD: miniboss and ICZ1BGE_Normal handoff | `TestS3kIczMinibossObject`, `TestS3kIczAct1TransitionHeadless#act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds` | Boss components and actual resource publication from repositioned late state with a forced normal-background routine; not cold traversal. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kIcz1EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. The [2026-10-17 bounded post1 authoring attempt](#bounded-post1-natural-death-authoring--2026-10-17) observed cold physical contact and ring-loss hits, but all three historical finite candidates ended alive. The [R8 ground-aware continuation](#r8-ground-aware-post1-continuation--2026-10-17) also ended alive after its 1200-input cap; natural death/reload/rewind remains unqualified. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | Existing ICZ trace bindings and [frontier log](../../../status/trace-frontier-log.md); no new strict trace run in this slice. | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

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

[`TestS3kIcz1EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kIcz1EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

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

## Bounded post1 natural-death authoring — 2026-10-17

**Coverage result: blocked by remaining input-route authorship, not a demonstrated runtime defect.** Only native 320, donor off, solo Sonic, ICZ1 physical post1 was attempted. The two initial candidates and the single repeated-return follow-up below all ended alive. No maintained lifecycle test or new product qualification resulted; the existing semantic pit-death floor above is unchanged.

Source: runtime `dc338d3e3a30916559a32d4684ff46e9ad77492c`, local branch `feature/ai-s3k-icz-natural-post-gpt61`, checkout `t3-b04bf688`. The cold prefix was reused by intent from the retained observation probe on `0197c5a5a`; no obsolete production fixes were imported. An isolated temporary Jupiter probe used `CanonicalEntryMatrixSupport.boot` for the real fresh ICZ1 assembly and `GameLoop.step()` with logical controller input. It supplied no gameplay position, rings, checkpoint, hurt/death, manager replacement or comparison state. There was no raw world restore in any authoring run. Only the ordinary cold boot was used.

The original locked-on ROM SHA-1 was `CFBF98C36C776677290A872547AC47C53D2761D6`. The optional disassembly's `Levels/ICZ/Object Pos/1.bin` matched the complete ROM placement block at `$1EE3CA`. Relevant placements are post1 `$3BD0/$076F` (object `$34`, subtype 1), static harmful ice `$3BF3/$06CE` and `$3D10/$0658` (object `$B8`, subtype 0), the swinging platform `$3CE0/$0784` (object `$B4`, subtype 0), horizontal springs `$3B82/$0770`, `$3CB6/$07B3`, `$3E0A/$07B0` (object `$07`, subtype `$10`), and the column `$3F10/$06F2` (object `$B3`, subtype 2). `Obj_ICZHarmfulIce` / `loc_8B4E8` installs `Sprite_CheckDeleteTouch` for subtype zero; the shatter path belongs to nonzero subtypes. `Obj_ICZSwingingPlatform` / `sub_8B0B0` requires the lower solid's standing contact and sufficient speed in its facing direction. These references informed controller authoring; they do not establish a gameplay discrepancy.

The following is the executed cold prefix flattened into input-authoring syntax. In the initial slice, predicate waits ended at the listed frames; this flattened full prefix was not independently captured or certified as a maintained BK2 route. The follow-up below subsequently round-tripped and executed the flattened full stream, without qualifying death/reload or rewind. `A` is held jump, `-` neutral; every instruction is a held controller state.

```text
# Snowboard/crash, real snow-pile jump release, and column approach.
1185 -; 1 A; 109 -; 93 R; 4 -
# Break the segment column and brake.
1 D; 1 D+A; 1 D; 1 D+A; 1 D; 1 D+A; 2 D
4 -; 62 L; 10 -
# Walk through the placed post1, then approach the first harmful ice.
75 R; 150 -
1 D; 1 D+A; 1 D; 1 D+A; 1 D; 1 D+A; 2 D
52 -; 14 L+A; 5 L
```

This reaches frame 1780. The independently completed first candidate appended exactly:

```text
120 R; repeat 12 { 1 R+A; 45 R }
```

The second, explicitly bounded follow-up replayed that prefix and first candidate, then appended exactly one new return sequence from frame 2452:

```text
140 L; 50 L+A; 180 -; 1 R
1 D; 1 D+A; 1 D; 1 D+A; 1 D; 1 D+A; 2 D
52 -; 14 L+A; 626 L
```

Both appended scripts were round-tripped through `InputLogAuthorTool.author` and `Bk2MovieLoader` before execution. The second sequence deliberately returned left, held a full jump toward the earlier obstacles, waited, faced right and reused the platform approach. It was one additional candidate, not a timing sweep. All frame numbers below count executed logical inputs from cold assembly; they are engine observations, not native timing parity.

| Observed spot | Input frame | P1 centre X/Y | Rings / invulnerability / lives | Outcome |
| --- | --- | --- | --- | --- |
| Snowboard crash | 998 | `$38F5/$0335` | 40 / 0 / 3 | Intro object absent; later snow-pile lock at 1185, released/landed at 1295 |
| Stable physical post1 | 1701 | `$3BE5/$076E` | 44 / 0 / 3 | Checkpoint active/index 1; first contact's exact frame was not sampled |
| First hurt sample | 1780 | `$3D23/$0672` | 0 / 120 / 3 | Hurt and alive near the placed static ice |
| Candidate 1 endpoint | 2452 | `$3EE5/$06EE` | 1 / 0 / 3 | Alive, ground speed 24; one ring had been recollected by frame 1991; later rightward inputs stalled here |
| Candidate 2 new hurt onset | 2894 | `$3D24/$0649` | 0 / 120 / 3 | Return/platform approach reached another natural hurt; the recollected ring was lost |
| Candidate 2 endpoint | 3523 | `$3D1D/$07AD` | 0 / 0 / 3 | Alive, ground speed -4094; later samples oscillated in the lower horizontal-spring channel; post1 still active |

Historical focused command, from the intended checkout: `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=<jdk25> ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless python3 <main-checkout>/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path=<original-root-rom> -Dtest=TmpIczCheckpointProbe test -B`. The original ROM was passed by absolute path; no ROM was copied or linked. The temporary selector is intentionally not a maintained test target.

Java was 25.0.4.1 and the test fork reported surfaceless EGL. Command session `75853` completed at 09:05:24 UTC: exit 1, **1 test / 1 failure / 0 errors / 0 skips**, natural-death assertion expected true but was false at the candidate 1 endpoint; class 1.253 s, Maven 1m15s, queue wait 361 s. Session `19237` completed at 09:16:14 UTC: exit 1, **1 / 1 / 0 / 0**, the corresponding assertion failed at candidate 2's endpoint; class 1.482 s, Maven 27.273 s, queue wait 362 s. The second run tested temporary probe source blob `f7b079f3df0fb1fb86f9251a75bfe3e5614b282c`. These failed authoring assertions are not maintained-suite regressions or passing coverage. The temporary probe, authored temporary logs and consumed Maven diagnostics were removed after inspection; no source/tool API was promoted. No broad smoke, full suite, performance run, native capture or strict trace ran in this slice.

Remaining bounded requirement: author an input-driven lethal contact after rings are lost, then assert real object-manager/runtime replacement, exactly one life lost, saved post1 coordinates and no snowboard, title/fade/assembly/control/focus release, retirement of nonempty outgoing history without restoring old-world owners, and `CheckpointIncomingReplay.verify`. Two complete-world capture/restore/forward cycles immediately before physical post contact and before lethal contact remain unqualified. A second natural death/reload with the same incoming-world checks also remains open. Missing controller authorship is not evidence of a runtime bug and does not justify a production change. Widths 352/400/528/800, other mains, donors, pairs/other team shapes, post2 `$5650/$042D`, ICZ2, full route composition and native presentation retain their inherited limits.

### Single repeated-return follow-up — 2026-10-17

A fresh, separately bounded follow-up on `e8d51ddf528f7d2e7a7c6e80b6570ba805f77f00` retained the complete 3523-input stream above, then appended **the exact same 1071-input return block once**. No alternative timings or additional candidates were tried. The intended experiment was another contact after the level ring had already been collected and its dropped ring had aged out. Those facts were not supplied as state: the probe preserved every actual pickup and ordinary hurt timer.

`InputLogAuthorTool.author` and `Bk2MovieLoader` round-tripped the entire 4594-input stream before execution. The temporary `TmpIczThirdContactProbe#thirdContact` used the same cold production boot and logical `GameLoop` driver. Fresh assertions reproduced the stable post1 state at frame 1701 (`$3BE5/$076E`, active index 1) and the second endpoint at 3523 (`$3D1D/$07AD`, zero rings/invulnerability, three lives). This is fresh verification of those states; the first post-contact frame remains unmeasured, and the historical 1631 expectation gains no acceptance from this run.

The third block's required natural-death assertion failed after all 1071 new inputs. Its endpoint at **4594** was **`$3CCE/$07AF`, rings 0, invulnerability 0, hurt false, dead false, ground speed -4092, lives 3, post1 active**. No new hurt onset or ring-count change occurred during this append. Samples remained in the lower spring channel; the previous return block did not recreate its earlier elevated damaging contact from this changed local state. No runtime defect is inferred from that route-authoring result.

The exact focused command was the command above with `-Dtest=TmpIczThirdContactProbe`. It used the current main-checkout absolute queue wrapper from the intended checkout, original absolute locked-on ROM, Java 25.0.4.1, surfaceless EGL, null OpenAL and unset DISPLAY/WAYLAND_DISPLAY. Session `61328` completed at **09:37:55 UTC**, exit 1, **1 test / 1 failure / 0 errors / 0 skips**; class 1.585 s, Maven 26.289 s, queue wait 493 s. Tested temporary source blob: `dac3021890c62ce9329e3ef9deb36d8dd677faed`. The source stayed fixed after submission; waiting did not trigger a replacement request or another candidate. The temporary source, authored temporary input and consumed Maven diagnostics were removed after inspection.

This is the final authorized authoring limit for this follow-up. Natural death, real respawn/life loss, saved post1 coordinate restoration, no-snowboard/control/focus release, history retirement, incoming replay and the two requested pre-contact rewind spots remain unqualified. No maintained test or source/API change was promoted. No broad/full validation ran; the other configuration/post/act limits listed above are unchanged.


### R8 ground-aware post1 continuation — 2026-10-17

**No natural-death certificate:** one controller-only continuation reached its
1200-input cap alive after 10 jump starts (limit 12). No maintained lifecycle
coverage or runtime change resulted. This is an additional bounded authoring
result, not a replacement for the historical three candidates above.

Source was clean Root `a58bdc058ec440d6e2ce70f3be38ee0ebcecc9b5` in owned
`t3-98d036a9`, new local branch
`feature/ai-s3k-icz-natural-post-coverage-gpt61`. The previous AIZ branch/ref and
R1–R7 records were preserved. The temporary probe's tested SHA-256 was
`5648de0e5ac7fd924860e572af36e33bd2b30952cf48b7d16b84456741fc578f`;
production and maintained test sources were unchanged. `CanonicalEntryMatrixSupport.boot`
provided the real cold native320/donor-off/solo-Sonic world, then ordinary
`GameLoop.step()` logical input and pending render tasks drove every input.
`InputLogAuthorTool.author` and production `Bk2MovieLoader` verified every button
of the unchanged 4594-input prefix before boot and the complete 5794-input
stream at termination. The prefix is exactly the earlier 1780-input stream,
672-input first candidate and the 1071-input return block twice; retain a
statement separator after the inline repeat when concatenating these blocks.

Offline button selection used only actual player ground/air and current/prior
centre coordinates, the placed platform's live pose and landing geometry.
It sought a grounded leftward approach from the platform's right side, held
jump through observed ascent, released when ascent ended, steered toward the
platform in air and moved right while standing at its height. Speed, rings,
hurt and lives were observations only. No position/velocity/ring/life/flag/
timer/routine/slot/RNG writes, death calls, trace hydration, seeded hazards,
world restore, gameplay API or production change was used. No alternate
policy or second gameplay candidate was tried.

Append this exact literal RLE to the accepted 4594-input stream to reproduce
the executed continuation; counts sum to **1200**, with 10 distinct A holds:

```text
22 R;23 L+A;25 L;111 R;23 L+A;25 L;109 R;22 L+A;26 L;
112 R;22 L+A;25 L;108 R;22 L+A;25 L;110 R;22 L+A;25 L;
18 L+A;13 L;42 R;27 L;18 L+A;13 L;18 R;27 L;93 R;
22 L+A;25 L;18 L+A;9 L;
```

All counts below are **executed cold logical inputs**, not native frames or
trace rows. `BEFORE` samples precede the next selected input; LF is the actual
level counter at observation, not a substituted input count.

| Observation | Input count / LF | Actual P1 state | Interpretation |
| --- | --- | --- | --- |
| Reproduced prefix | 4594 / `$11F1` | `$3CCE/$07AF`, grounded, rings0/inv0/hurtfalse/deadfalse, ground speed −4092, lives3, post1 active; camera `$3C3E/$074F` | Exact 1701, 3523 and 4594 assertions pass |
| First jump selection | 4616 / `$1207` | `$3DED/$07AD`, grounded, speed −4068; next sample airborne | Ordinary leftward return from spring channel |
| First recorded platform ride | 5482 / `$1569` | `$3D00/$0760`, grounded, `IczSwingingPlatformObjectInstance`, X speed −305, rings0/inv0/lives3 | Ride regained; first riding tick was not sampled |
| Later recorded platform ride | 5582 / `$15CD` | `$3CFE/$0760`, grounded, same platform, X speed −318 | Ride regained again; later samples return to lower channel |
| Cap endpoint | 5794 / `$16A1` | `$3D04/$0765`, airborne, rings0/inv0/hurtfalse/deadfalse, ground speed0, VX−247/VY−195, lives3/post1 active; camera `$3C74/$0720` | 1200 extra inputs / 10 jump starts, no death |

The 77 recorded continuation samples spanned X `$3CCE..$3DFD`,
Y `$0760..$07B2`; these are sampled bounds, not continuous trajectory extrema.
The live platform remained at `$3CE0/$0784`, layout14/object`$B4`/subtype0;
static ice layout12 `$3BF3/$06CE` and layout15 `$3D10/$0658` remained present.
On disassembly pin `044fa46725c71187399e13f5ddb70e11d32dc024`,
`sub_8B0B0` requires the lower solid's standing contact, absolute horizontal
speed at least `$200` and matching facing. The recorded negative riding speeds
above are below that threshold; they do not prove which piece/branch ran
between observations. The platform pose stayed unchanged in recorded samples.
No lethal touch owner or runtime defect was demonstrated. Merely regaining
support did not author the elevated damaging route.

Exact command shape (the actual original three ROMs were absolute paths under
`$OPENGGF_MAIN`, the unchanged Main checkout; no profile was selected):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical \
  '-Dtest=TmpIczR8GroundAwareAuthoring#authorOneGroundAwareContinuation' \
  "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" \
  "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" \
  "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
```

Java 25.0.4.1, normal ROM heap, exact single-token `JAVA_TOOL_OPTIONS` shown above;
inherited `JDK_JAVA_OPTIONS`/`MAVEN_OPTS` were preserved. The S3K ROM retained
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. Own private config was a
regular exact Main byte copy before each JVM; Main SHA-256
`6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da` and
mtime_ns `1792141742849921547` were unchanged before/after. No Main branch or
source/config migration occurred. Queue waiting was zero for these requests.

| Session / terminal UTC | Result | Execution and limit |
| --- | --- | --- |
| 90341 / 14:28:52 | testCompile failure, no tests/gameplay | Observer used nonexistent `GameStateManager.getRings`; corrected to read-only playable getter, Maven1m01 |
| 3516 / 14:30:41 | 1 test / 0 failures / 1 error / 0 skips | Input-script separator rejected **before boot**, zero gameplay; Maven23.411s |
| 53312 / 14:32:25 | 1 / 0 / 1 / 0 | Prefix assertions green, then optional null-spawn dump error; **zero continuation inputs/jumps**, Maven22.963s |
| 53551 / 14:35:23 | 1 / 1 / 0 / 0, intentional cap-alive assertion | Explicitly authorized exact re-execution after null-safe optional dump only; full1200-input candidate, class1.797s/Maven25.214s |

The first two were pre-execution tool prerequisites. The third was an
observation prerequisite failure, separately authorized for one exact rerun;
null owners/spawns now emit class/null metadata without default getX/getY.
Optional `isDestroyed()` was excluded because some owner overrides can mutate
lifetime state. Policy/buttons/bootstrap/caps stayed fixed. The final failure
was solely the explicit natural-death assertion, with no observer error or skip.

No further candidate/JVM, maintained rebuild, affected validation, native/media,
strict trace, sweep or broad run was warranted by this cap result. Inspected
temporary probe/input/observation/reports and its compiled class were consumed;
`target` last compiled the temporary observer, not a newly validated maintained
test tree. Existing normal-entry and independent semantic death/reload floors
remain historical, unchanged qualifications. Physical-post natural lethal
contact, exactly one life lost, saved-post destination/no-snowboard release,
world replacement/history retirement, and before-contact/after-release capture
with two forward replays remain **open**, along with all other axes above.


## R10 retained16 ownership observation — 2026-10-17

**Concrete boundary, no runtime repair or ICZ pass:** the one authorized passive
observation proves a genuine loss of559 known outgoing16x16 pattern entries,
in addition to the detached-oracle absence/allocated-placeholder difference.
Another30 target entries are absent in all observed worlds and remain unknown;
they were never normalized to zero, skipped or removed from comparison.

Green LRZ/DDZ test commit `00272ae474529412e3c02b7af05349499bc6b1c0` was completed
and reported before this ICZ execution. It contains exactly the two reviewed
test files and their four existing docs, reusing their actual `54296` passes.
The ICZ partial128-oracle migration is excluded and remains unstaged at SHA-256
`e06e322770e26e6f19f2c2f211d00d331c9adb65e5face390230cfeade9fe5cc`.
Owned branch is `bugfix/ai-s3k-icz-retained-16-owner-gpt61`, same98d/native01a1496e;
R1–R9 identities/results and earlier branch refs are preserved.

The original maintained
`TestS3kIczAct1TransitionHeadless#act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds`
driver/bootstrap/service sequence and every comparison remained intact. A
marked temporary test-only observer read immutable ROM payloads, actual
Chunk presence/full six-word states, decoded extents and all128-reference
indices. It wrote only `target/icz-r10-retained16.txt`,284621bytes/1777lines
under a10MiB cap. No gameplay setter, private field, hydration, source API,
allocator, collision fallback, service call or comparison was added/changed.
The three sample phases are outgoing before detached construction, detached
deferred construction, and actual in-frame reload before the next POST.
Actual LevelManager LF was0 throughout this component driver, which directly
pumps event/hardware boundaries; it is not native clock or trace-row evidence.

| Actual phase |128 blocks /16 chunks | This build's decoded16 entries | Target indices129..717 (589 total) | All128 maximum16 reference |
| --- | --- | --- | --- | --- |
| Outgoing ICZ1 |230 /688 |688 |559 present, all nonzero native pattern groups;30 absent |687 |
| Detached deferred ICZ2 |20 /129 |129 |589 absent |126 |
| Actual reload before POST |230 /688 |129 |559 present, all four native pattern words zero;30 absent |687 |

The first target entry129 (`$FF9408`) is referenced12 times, first from retained
128-block30. Outgoing state is **`C131 C132 C133 C134 / 00FF 00FF`**; detached
state is **ABSENT**; actual post-reload state is **`0000 0000 0000 0000 / 0000 0000`**.
The first four words are native pattern descriptors; the last two are separate
collision-index metadata. All559 outgoing pattern groups exactly match the
independent ICZ1 secondary ROM decode, and all559 differ from actual zeros.
Collision metadata differs for180 entries; this does not justify carrying old
indices, because native LoadSolids is their separate current-level writer.

| ROM producer | Source | Destination | Decoded bytes | SHA-256 of decoded payload |
| --- | --- | --- | --- | --- |
| Common ICZ primary16 |`$3D3FB6` |`$FF9000` |1032 / `$408` |`256f95e51a18034457c1badb873c1b3dd82c1900a4300b0ef1d4e5957a5c4397` |
| ICZ1 secondary16 |`$3D5958` |`$FF9408` |4472 / `$1178` |`bdf0a5f5e78f069d8700f04cbdb281a5c843a1ea5ede6bfb8436de337b46613f` |
| ICZ2 secondary16 |`$3DA26A` |`$FF9408` |4712 / `$1268` |`9e9268074574b2108b408d7c303f5dbd629d1219b7a3e213117a1c5ab6146f09` |

ICZ1's known16 write ends at `$FFA580` exclusive. The last30 ICZ2 target entries
688..717 correspond to `$FFA580..$FFA66F`; neither observed predecessor nor
current construction supplied those bytes. ICZ2's queued payload, beginning
`C0C5 C0C6 C122 C123`, owns them only when the existing publication owner runs.
Their absence is a coverage/ownership limit, not evidence of native zeros.

Source explanation on pin `044fa46725c71187399e13f5ddb70e11d32dc024`:
`ICZ1BGE_Transition` calls `Load_Level` (layout-only1000-byte copy, no16 clear)
and `LoadSolids`. Current `Sonic3k.captureLevelBuildInputs` carries128 definitions
but no16-pattern bytes. `Sonic3kLevel.loadChunksWithCollision` decodes only the
129 primary entries for the deferred build; `validateResourceReferences` sees
the retained230 blocks/max687 and `ensureChunkCapacity` adds empty Chunks up to688.
The measured counts/words establish that construction boundary. The detached
fresh target lacks those retained128 references, so remains at129 and cannot
serve as the actual handoff's resident16 oracle.

Session **3364**, terminal1 at **16:51:31UTC**, queue14s/Maven25.677s,
**1 test /1 failure /0 errors /0 skips**, class.576s. Temporary observer source
SHA-256 `811f354491302307da88733f3ecd022d87f71e3c1ebaa1ff922d5e2774ec67e1`.
The command is the [R9 focused command](../../plans/2026-10-13-s3k-playable-completion.md#r9-green-lrzddz-split-and-r10-icz-evidence-boundary--2026-10-17)
with ONLY the exact ICZ method above as `-Dtest`; same normal lane/current Main
absolute wrapper/from own cwd/all3 original ROMs/Java25/EGL/nullAL/noambient/
Lua5.4/exact JTO/singlefork/reuse/alphabetical, no profile. No extra Java run.
All128 initial pre-publication/queue/admission/unclaimed assertions pass, then
the same16 index0 assertion fails. Initial pattern and later publication
assertions remain unreached; no ICZ parity or full transition pass follows.

Smallest proposed independent old-red: the short real ICZ handoff must preserve
the known entry129 pattern words from its outgoing ROM owner until existing
publication, while independently checking current-ROM LoadSolids indices and
full claimed ICZ2 payload bytes. Keep the30 unknown pre-publication entries
explicit and every original comparison; do not normalize them or narrow the
span. A possible minimal runtime extension is private immutable native16-pattern
byte carry parallel to128, with target collision indices and ordinary COW/
deferred publication/rewind. **That extension is not authorized or implemented.**
No blanket capacity rewrite, unknown-RAM fallback or public API is proposed.
The observer is removed back to the exact unstaged e06 hash; inspected journal,
reports and its compiled test class are consumed without archive or rebuild.
Root owns the next source/evidence decision and composition. R8 natural-death,
all other act products, full aux/native/cold-route and campaign limits remain open.


## R11 synchronous known16 handoff repair — 2026-10-17

Bounded component qualification on077767/R13 production0cd plus this repair,
branch `bugfix/ai-s3k-icz-retained-16-owner-gpt61`, same98d/native01a1496e.
R10's genuine559 pattern losses are repaired without treating30 absent target
entries688..717 as known zeros. Existing executing synchronous deferred loads
capture immutable sparse native8byte pattern groups, bounded by Block_table's
`$1800` actual bytes. Target non-deferred ROM writes replace the prefix;
retained groups decode with independently selected **target** collision tables.
The first entry129 is C131/C132/C133/C134 with target8/8 from SolidIndexes
`$27A761`, not oldFF/FF from`$279B61`. No predecessor collision metadata is copied.
Cold/detached/presentation and async-prepared builds keep an empty16 predecessor;
async predecessor identity/generation is still an explicit extension limit.

The short independent `TestS3kIczRetainedChunkHandoff` uses the existing real
ICZ component inlet and production queue/service/handoff. Old-red31728 terminal1
17:08:14UTC, queue0/Maven24.467s,1/1F0E0S: retained entry129 word0 expectedC131,
actual0000. Source prerequisites read ICZ1secondary`$3D5958` independently and
require target8/8. Corrected45686 terminal0 17:12:27UTC, queue0/Maven1m06,
**77/0F0E0S** across11 actual classes. Original transition1 (.090s) now reaches
all128/16/pattern/admission/claimed/payload/ordinary-reload assertions. Independent
handoff1 (.177s) checks all559 retained pattern groups and target collision indices
at prePOST/intermediate readiness boundaries, then all589 original target groups,
including formerly unknown30, after the sole queued publication owner. It restores
the new ICZ2 world twice and replays publication identically; no retired Act1
history is restored. Sparse provenance2, deferred resources6, overlay3,
native-word residency4 and mandatoryS3K4 selectors60 also pass without skips.

Source review found one material P2 in the initial carry epoch: an owned opaque
688-entry snapshot followed by a temporary entry717 overlay restored only a prefix,
leaving718 entries and falsely exporting717 as known. New independent9826 was
terminal1 17:14:47UTC, queue17s/Maven24.073s,1/1F0E0S (expected688/actual718).
The minimal correction restores the saved extent ONLY for this owner's opaque
snapshot; external six-word prefix writes retain their existing partial semantics.
Private placeholder provenance follows geometry COW and whole-world references;
collision-only LoadSolids clones and opaque value snapshots preserve unknowns.
Actual zero writes remain known. Final affected68955 terminal0 17:16:43UTC,
queue0/Maven1m11, **4/0F0E0S**: sparse3 + existing opaque-overlay restore1.
The new counterexample covers temporary-tail removal, unknown holes, COW negatives,
external-prefix control and two forced whole-world restores/replays. The original
77 are an earlier construction/publication epoch, not a second final77 run.
Neither ICZ method uses opaque snapshot/restore; its sole production consumer is
AIZ intro terrain precomputation, so no unchanged ICZ handoff repeat was requested.

Final runtime SHA-256: Sonic3k`1ec70e427f97c3fa8da696b4ec8818d66e5d608e14af47aa273e5c5440bfab2f`;
Sonic3kLevel`d392aa6c8f807dc12b1d4787250ead0b33c3dea33efc0134516554a8316c3ab8`.
The original128 oracle remains an immutable outgoing pretransition span, with
independent ROM primary A00/end and detached-negative control.16 expectations
now use outgoing known native4 plus independent target indices, explicitly retain
absence for688..717 before publication and still compare the complete published
payload. No comparison/clock/input/camera/physics/allocator/timing manifest changed.
Native labels/pin and payload hashes remain the R10 evidence above; line numbers
belong only to pin044fa46725c71187399e13f5ddb70e11d32dc024.
[Exact commands and source epochs](../../plans/2026-10-13-s3k-playable-completion.md#icz-r11-synchronous-known16-repair-checkpoint--2026-10-17).

This is a synchronous retained-resource component certificate. R8 natural death/
post reload, cold-route/character/preset breadth, full aux/native/config/BK2,
strict trace/fullchain and combined smoke/integration remain unqualified here.
No extra observer, native/media, prepared-AIZ sweep or broad run was submitted.


Final89633 source/API guards: terminal0 17:19:02UTC,queue0/Maven26.094s,
5/0F0E0S (three exact architectural authority/mutation/timing-mirror methods,
two exact current-pin/helper-nonleak methods), fresh `-Pguards` JVM. Root's bounded
resolution inspection accepted d392aa6c/2680a205 and68955 controls with no remaining
material objection. All5 own jobs terminal; inspected diagnostics consumed under
metadata slot alone, no archives. Main config hash/mtime and private regular bytecopy
preserved. Final target compiles the maintained source before commit; no postcommit
rebuild, composed smoke or integration/push is claimed.

## Knuckles native teleporter arrival candidate — 2026-10-20

**Locally verified candidate; integration and broader routes remain open.** The original
locked-on ROM SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6` owns a fresh
Knuckles arrival that the earlier entry floor omitted. `ICZ1_ScreenInit`
`$53608..$53666` (94 bytes, SHA-256
`cb88d83baeb0193921a41834a9bcb25ba2be34d20d9027b8fec9b79e3a9cf76d`)
tests `Last_star_post_hit == 0`, `Player_mode == 3`, and successful
`AllocateObject`, then overwrites actual P1's word position with `$3640/$0660`
and both camera/copy words with `$35A0/$05FB`. Knuckles' start table itself
contains `$3640/$065C`; the canonical assertion must execute genuine deferred
initial assembly before expecting the ScreenInit override. Checkpoint, other
player modes and allocation failure retain their normal entry words. The fresh
dispatcher hook has no ordinary positioned/decode/seamless fallback authority.

`Obj_ICZTeleporter` `$53AD4..$53C04` (304 bytes, SHA-256
`4e7b812c1dcb1da3b33e8823de84a35722edce7da45f7ce4235e5f011bfb5a39`)
retries `AllocateObjectAfterCurrent` without init effects on failure. Success
installs pad frame `$1A` at `$3640/$670`, bucket1, art `$2347`; saves P1's Y;
starts the shared beam expanded and contracting with progress `$18`, two
segments, base Y `$670-$80`, table `word_467FC`, bucket1 and art `$255E`; writes
only Target palette line2 colours8/10 `$EA2/$E80`; and writes P1 control1,
animation2 and the roll status bit. The existing roll helper also clears skid
state, so a small engine-only direct-status helper preserves native skid,
velocity, radii and box semantics. The exact existing `S3kGradualSwing`
implements `Gradual_SwingOffset($20000,$800)` in the init pass and each Main
pass. Non-negative signed speed releases control/animation and immediately
enters the solid-pad check. Camera X copy `$3780` restores Normal line2
colours8/10 `$002C/$0000` and deletes; earlier copies call full solid with
`d1=$23,d2=$10,d3=1`.

The beam uses ICZ's actual level PLC, not another zone's graphics:
`PLC_1E_1F` entry `$92680 = 00159cd0ae00` loads the Nemesis archive at
`$159CD0` into tile `$570`. Its 45 compressed bytes uniquely match the optional
reference asset, SHA-256
`783ce8124fbcedc1292122ca9fb5950f1be7c087dbbbe6efa2a9a898b31f6e80`.
The registry's `$55E` base plus shared mapping `$12/$16` therefore selects
physical `$570/$574`. Rewind captures the ICZ art selector, `$80` base offset,
phase/progress, swing and reciprocal parent/beam identities.

`TestS3kIczKnucklesArrival` covers the native320/off fresh component inlet,
actual P1 versus P2 focus, no duplicate ScreenInit, Sonic/Tails and saved-entry
negative branches, both real pool allocation-failure boundaries, ROM art tiles,
and two complete registered-world restore/forward cycles through real initial
assembly, contraction and release. Its camera-copy deletion boundary is an
explicit component inlet, not proof of ordinary travel to `$3780`.
`TestTeleporterBeamRendering` adds the ICZ profile/recreation oracle while
retaining HPZ/SSZ/CNZ controls. Root owns the combined executable selection and
the actual CNZ results/beam/fade/load/title-to-released-ICZ composition test.
Root's affected 14-class composed run passed **250 tests, no failures, errors or
skips**, including all ten Arrival cases, nine shared beam cases, 140 ICZ entry
products, the real CNZ-to-released-ICZ handoff and HPZ/SSZ consumers. The tested
all-`src`/POM fingerprint is
`5ba31bd613122cb704f74d4b340d6abf62c5b4ec3081d1422f56fc348e9b7af6`;
the source remained unchanged through the sole run. Four earlier beam fixture
errors required an explicit test-owned parent identity context; contextless
capture remains rejected.

The preinitialization world replay also exposed direct ICZ animation ranges
missing from the animator's initial capture domain. Construction now registers
horizontal `$10E/16` and, only in Act 1, vertical `$122/15` after their existing
art guards. Capture still reads the actual currently presented bytes; no early
animation or upload occurs. The preceding 318-case run passed all 20 publication
cases, including before-first-submission, pending/legal/stale VBlank and Act 2
exclusion controls; its only four errors were the subsequently corrected beam
fixtures. Those unchanged animation controls were not rerun as a ceremony.

Natural post-1 death/reload, other viewport/donor/team route breadth, native
pixels and the full cold route remain unqualified. The local focused results do
not certify the whole act or the excluded native physical-display framework.


## Short post-1 approach observation — 2026-10-20

The preceding one-policy authoring attempt reaches post1 and ordinary zero-ring
hurt but exhausts2400 added inputs alive; it is not a death/reload regression.
A separate temporary selector,
TmpIczPost1RouteAuthoring#observeFirstLeftApproachEdge, preserves that attempt
byte for byte. It replays the real1780-input prefix, waits neutral until actual
hurt recovery (cap180), then holds only Left until the first public contact/motion
edge, the historical approach X window, or120 inputs. No pose/state/timer/life,
callback, world restore or trace hydration is supplied.

Actual recovery takes66 neutral inputs, grounded on the placed collapsing
platform at $3DD5/$0730. The first Left input is submitted/held/pressed mask4
and changes VX0 to-12. After10 Left inputs, the same platform's standing contact
goes true to false and its riding owner to null; P1 becomes airborne at
$3DA4/$06F3, VX-120, with110 immunity frames, zero rings and all3 lives.
No velocity or displacement reversal is observed before this stop. Nearby
spring geometry is context, not proof of a selected spring callback.

Contact loss is consistent with walking off the support's west edge: its ICZ
half-width is $30, placing that edge at $3DA5. This does not establish collapse
timer expiry; the native fragmented parent can retain invisible solid support.

The episode passes as a completed observation inside candidate-4's29T/0F/0E/0S
run, fullsrc+pom71b28bf073867504d903ca0fe0fabba1cb32f33874c5500618a3dd5276c8e947.
No jump, natural death, reload or rewind is attempted. The previous2400-input
result remains NOLETHAL; the maintained physical checkpoint lifecycle is still
unauthored. The next route design must account for the actual collapsing support
before trying to return west; no production gameplay defect follows yet.


## Left charge departure and airborne approach — 2026-10-20

The temporary charge, landing/return, contact and braked-ride selectors explicitly
configure native320 solo Sonic with donor off. Their measured route progress does
not qualify Knuckles; a maintained extraction must assert the actual P1 identity.

A distinct temporary selector,
TmpIczPost1RouteAuthoring#authorLeftChargeDepartureToApproach, preserves both
previous episodes. It uses the same actual 1780-input prefix, neutral until hurt
recovery, one existing eight-input spindash charge, then held Left. Additional
input is capped at309, with three charge presses and no extra jump or Right.

The original candidate-5 passes1T/0F/0E/0S at fullsrc+pom
0dac6ecb9cdc9b1604e4b8dd2d31352f99622e7eed7b364597ba11d8343f50f5.
Actual recovery remains66 neutral inputs on the same placed support. Charge
admission occurs at1848; release1855 clears spindash, enters rolling and
publishes GSpeed-2432 while VX is still0. The next dispatch departs the support
at$3D9D/$06F7, VX-2426. After32 subsequent Left inputs, the approach X gate is
crossed at input1887, $3C77/$075D, VX-2426/VY1736. P1 is still airborne and
rolling, alive with0rings/79immunity frames/3lives, post1 and the same world.
The total added input is107:66neutral+8charge+1release+32Left. All10events and
the exact input script are retained in the original report.

This qualifies an ordinary admitted charge and horizontal approach crossing,
not grounded approach, first subsequent landing/contact, natural death/reload,
rewind or native collision cause. The moving-platform lower piece was not
contacted before this stop; spring and harmful-ice proximity remains context.
The next route question starts with this actual airborne state.


## Charged approach landing and bounded return — 2026-10-20

Temporary authorChargedApproachLandingAndReturn reuses the unchanged admitted
charge episode and submits only Right after the actual airborne approach.
First landing occurs after4 Right inputs, at1891: $3C52/$076D,
VX-2330/VY0, grounded with no object contact. Actual lower-piece entry-side
and standing-height checks admit a Right return. After90 further Right inputs
the declared cap stops at1981, $3CA3/$076E, VX1002/VY0, still grounded.
One ring was naturally recollected; all3 lives, post1 and the original
objects/runtime remain. The lower piece was not contacted.

The episode uses201 added inputs including107 in the inlet, three inlet charge
presses and no extra action presses. All seven events and the exact executed
input script appear in the original report. It passes as one completed
observation in candidate-7's227T/0F/0E/0S selection. This advances airborne-only
progress to actual landing and ground return, but does not qualify platform
callback, natural death/reload, rewind or native collision cause.


## Confirmed lower-platform contact — 2026-10-20

The next temporary selector, authorLandingReturnToLowerPieceContact, preserves
the preceding201-input landing/return episode. Seven additional Right inputs
produce actual managed lower-piece contact at input1988: exact retained platform
owner, piece0, standing=true. P1 is $3CC0/$0770, VX1086/VY0, grounded,
one ring/no immunity/3lives/post1 and the original objects/runtime. The episode
uses208 added inputs, only the original three charge presses, five events and
no suppressed events.

Before that input P1 is $3CBC/$0770, VX1074. That public pre-step velocity is
context, not a captured inner solid callback operand. The lower-piece landing
half-width changes32 to43; this is compatible with activation, not a natural-ride
or callback-cause certificate. The placed subtype0 remains a circular swing/reset
owner; its detach/fall/slide branch is excluded by source.

The episode passes in candidate-8's158T/1F/0E/0S run; the unrelated HCZ graph
slot-probe fixture failure is corrected and its four cases pass separately.
The [composition record](../../plans/2026-10-13-s3k-playable-completion.md#knuckles-head-ship-explosions-and-physical-icz-contact--2026-10-20)
contains the exact commands. Natural ride/hazard travel, ring-loss then death,
checkpoint reload and rewind remain open; one retained ring prevents treating
the next ordinary hit as a lethal certificate.

## Sampled lower-platform rise and return — 2026-10-20

The temporary solo-Sonic selector authorLowerPieceBrakedRideExcursion now
actually retains the same managed piece0/standing contact through8 Left braking
inputs and32 neutral inputs after the1988 contact. At1996 the public GSpeed is62
against RunDecel128, and the controller permanently releases to neutral.
The first sampled lower-piece upward step is at1990; after a minimum piece Y1889,
the first strictly positive Y delta is3 at2028. Quantized flat steps are not
labelled reversal. P1 ends at$3D2C/$0749 with zero speeds, grounded,1ring/3lives/
post1 and the same object/runtime owners. The parent spawn is$3CE0/$0784;
the lower-piece centre at initial contact is$3CE0/$078C.

The completed observation uses248 added inputs (208 inlet plus40 tail), no
additional action presses,8 events and no suppressed events. Candidate10's one
temporary case passes; the overall selection is239T/3F/0E/0S because of separate
HCZ world failures. The full input stream and sampled states remain in its
original report. This is one sampled excursion, not a full oscillation, natural
hazard/death/reload, Knuckles or native-cause certificate. Maintained full-world
contact/ride replay extraction is authorized separately and is not yet validated.

## Maintained physical lower-piece replay — 2026-10-20

TestS3kIcz1PhysicalLowerPlatformReplay now passes its maintained solo-Sonic
episode in Candidate13. Real recorded controller edges reach the authentic
pre-contact1987 state, land on actual managed piece0 at1988, and retain the
same standing/riding owner through8 Left and32 neutral inputs to2028.
Pre-contact, ridden and sampled return-boundary spots each restore into fresh
platform incarnations and replay twice, comparing the complete registry key
set and existing whole-world content comparator. The driver also restores its
previous BK2 input baseline and input count. Forty-one ridden observations
retain current P1/placement identity, piece0 standing bit, ground/latch state,
one naturally recollected ring, three lives, post1 and the same world.

The standing-bit snapshot codec now retains each piece index alongside the
existing spawn/instance owner channel, for both live bits and the independent
pre-clear snapshot. It never reconstructs a live standing bit from riding
membership. The old two-argument parent query remains an unscoped query; the
maintained oracle reads the actual captured lower-child key for current P1.

Candidate13 is240T/1F/0E/0S: this maintained episode and all13 existing ICZ
platform cases pass. The unrelated new synthetic multipart-landing fixture
fails before establishing its first ride; it remains under investigation.
This does not certify a Knuckles physical route, full oscillation, natural
hazard/death/checkpoint reload, native cause or complete act traversal.

## Independent child-contact fixture closure — 2026-10-20

Candidate14 passes all eight TestObjectSolidContactController cases. The new
two-piece landing fixture now performs the ordinary object's pre-update snapshot
once before both contacts; that lifecycle call clears the constructor's initial
solid-contact skip. Geometry, ordinary resolution and standing/riding assertions
are unchanged. Two independently created fixture players establish actual
piece0 and piece1 standing keys; the existing registered-reference, pre-clear,
explicitly cleared live-bit and legacy unscoped cases also pass.

The maintained solo-Sonic physical episode remains qualified by Candidate13;
it was not rerun for this test-only setup correction. The temporary discovery
class was removed only after all six precursor/successful episodes, rejected
approach findings, input stream and method lessons were mapped to the maintained
test, this matrix and the existing Plan. No additional Knuckles, death/reload
or route coverage follows from deleting that owned temporary file.


## Maintained direct cold CNZ2 Knuckles arrival edge R6 — 2026-10-21

The new
[`TestS3kCnz2KnucklesColdRouteCapture`](../../../../src/test/java/com/openggf/tools/TestS3kCnz2KnucklesColdRouteCapture.java)
reaches this actual act from ordinary cold CNZ2, native320/off solo Knuckles,
without gameplay seeds. Two fresh ordinary7,164-input movies agreed on load6870,
fresh assembly6987, arrival release7050 and supported stationary control-free
`$3640/$065C` endpoint7163 (0 rings/3 lives, camera unfrozen and focused on P1).
The maintained cold route separately checks real outgoing-history retirement,
fresh typed arrival, complete60-key45-input restoration/forward replay twice at
active arrival, release and supported incoming states, and full normal drawing.
It does not rewind across fade/load or pending assembly.

The affected whole class passed2T/0F/0E/0S; the existing positioned arrival and
short entry/restart products remain independent. This closes one direct cold
handoff configuration, not ICZ1 traversal/completion, wider/donor/team/respawn
breadth or native timing/pixel proof. Source/data hashes, commands and exact limits
are in the [R6 Plan](../../plans/2026-10-13-s3k-playable-completion.md#cnz2-cold-knuckles-to-released-icz-maintained-r6--2026-10-21).
