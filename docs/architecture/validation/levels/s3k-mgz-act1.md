# S3K Marble Garden Act 1 coverage matrix

Selection update (2026-10-27): routine canonical entry/reload checks cover supported
axis pairs; bonus transitions retain their existing pilots. Full Cartesian
products are explicitly manual `exhaustive-audit` coverage, excluded from routine
and release gates. Historical exhaustive qualifications below are unchanged and
are not renewed by a routine pass. See the [selection record](../../research/2026-10-27-local-validation-cost.md).


Canonical binding: `S3K_MARBLE_GARDEN_1`, engine zone 2/act 0, ROM `$0200`, S3KL object set.
Edges: HCZ2 incoming full entry; MGZ2 outgoing structural act handoff.
Character routes: MGZ1 falling entry applies to all three native mains; later traversal/character composition is not certified by that shared entry.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Physical post 1 is qualified for native320 Sonic, the native pair and the 30 solo rows (2026-10-17 section). Other entry mechanisms, posts 2–3, multi-participant post products, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kMgz1EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kMgz1EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| OBJECT / REWIND: top, pulley and mechanism families | `TestS3kMgzTopPlatformLiveRewind`, `TestS3kMgzPulleyGraphRewind`, `TestS3kMgzMechanismGraphRewind` | Local non-default owner/rider graphs; per-placement before/active/release and complete configuration products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| OBJECT / CAMERA: top-platform movement and multiple riders | `TestS3kMgzTopPlatformParityHeadless`, `TestS3kMgzTopPlatformMultiSidekick`, `TestS3kMgzPulleyAndMantis`, `TestS3kMgzDashTriggerNoSpindashDonation` | Declared local owner/physics contexts, not a cold MGZ1 route. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / EVENT / LOAD: miniboss and MGZ1BGE_Transition | `TestMgzMinibossInstance`, `TestMgzMinibossSpikeArt`, `Sonic3kMGZEvents` | Component boss and source owner bindings; actual uninterrupted defeat/results/structural handoff plus destination replay remains open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kMgz1EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post 1 activation → physical hazard death → real post respawn is qualified for 32 products ([2026-10-17](#physical-post-1-lifecycle-2026-10-17)): native320 Sonic, the native Sonic+Tails pair and all 30 supported solo preset/main/donor rows. The 110 multi-participant rows of that product, posts 2 and 3 and incoming/outgoing route edges remain open. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | Existing MGZ trace bindings and [frontier log](../../../status/trace-frontier-log.md); no new strict trace run in this slice. | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

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

## Native main death-wrap owner correction — 2026-10-14

The valid native eight-act pilot on `24d5be319` completed 8 / 1 failure / 0 errors / 0 skips at 12:42:15 BST; only MGZ1 failed to reload. The separately selectable MGZ1 diagnostic on `738dc9fec` completed 1/1/0/0 at 12:47:32 BST. Actual kill state was P1(192,4046), cameraY3918, min/maxY−256/4096, frozen camera, dead routine6, y velocity−1792 and 3 lives. After 1800 logical iterations it remained dead with countdown0 and 3 lives; Y3097/velocity−32064 showed continued wrapped fall. Neither observation seeded player/camera state or supplied gameplay from trace rows.

ROM `loc_123AA` calls raw `MoveSprite_TestGravity`; `sub_123C2` / `loc_123FA` compare physical CameraY+$100, so this actual restart row is4174, beyond the ordinary `$FFF` control mask. S1 `Sonic_Death` / `ObjectFall` and S2 `Obj01_Dead` / `Obj02_Dead` / `ObjectMoveAndFall` likewise use raw gravity primitives. Main-player dead dispatch must not inherit the normal control mask. Both existing private post-control seams now bypass it for dead mains: `PlayableSpriteMovement` and `SpriteManager`. Their CPU policy and live/hurt mask behavior remain intact; no game/zone condition, public API, physics profile or snapshot shape was added.

The first movement-only candidate was insufficient: request 60954 on `5d334596e` plus that guard completed 241/1/0/0 at 12:55:11 BST. Its 12 direct/live controls, 7 unchanged act pilots and 221 neighbors passed, while actual MGZ1 still failed because the roster seam masked the corpse again. Adding the second native-owner guard and six real-roster motion controls produced the 247-case coherent pass above, including actual MGZ1 repeated reload and incoming world replay. An earlier waiting pilot with an invalid manual fade tick was canceled before execution; its replacement stopped at compilation on the wrong render-drain receiver. Neither earns behavioral evidence. The corrected production driver lets `GameLoop` service the fade once and drains pending work through `GameServices.graphics()`.

## Finite actual restart products — 2026-10-14

[`TestS3kMgz1EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kMgz1EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

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

## Physical post 1 lifecycle (2026-10-17)

[`TestS3kMgz1PhysicalCheckpointLifecycle`](../../../../src/test/java/com/openggf/tests/TestS3kMgz1PhysicalCheckpointLifecycle.java)
covers the first physical star post through the production `GameLoop`. Independent oracles:
`Levels/MGZ/Object Pos/1.bin` (posts 1–3 at (`$1180`,`$AF4`), (`$1E98`,`$3B4`), (`$2AE0`,`$E72`);
upright spikes `$08` at (`$1310`,`$B1D`); `Obj_MGZSwingingSpikeBall` `$58` at (`$1400`,`$B34`)),
`Obj_StarPost` (sonic3k.asm:61599: P1-only window `x-post+8` in `[0,$10)`, `y-post+$40` in
`[0,$68)`; routine-6 twirl child with `$36=$20`; `sub_2D164` saves subtype and the post's own
X/Y; a reloaded post with subtype ≤ `Last_star_post_hit` starts used), `Touch_Sizes`
(:20749) and `HurtCharacter`/`Kill_Character` with no rings.

- Inlet: a declared position 48 px left of the unactivated post at its own Y (not a cold
  route). No checkpoint, life, ring, clock, position or death state is written.
- Activation: ordinary Right input reaches the window edge (native Sonic: x 4473 ≥ 4472);
  the test asserts index 1, Saved X/Y = the ROM placement, the live ROM-placed post object and
  one orbit child. Rewind spots, each restored and replayed twice with full registered-world
  comparison: before activation → contact frame; while the orbit child is live → 40 frames
  later, after `$20` has retired it.
- Death: ordinary input runs right, takes one running jump over the upright spikes (above
  the rings over them), then stands under the swinging ball's anchor. The death frame must
  overlap a placed hurt/enemy touch region by the ROM `Touch_Sizes` entry (or standing on the
  spikes) with 0 rings; all 64 deaths in the qualifying run were on the ball at (5120,2868).
  No semantic kill call.
- Respawn: real reload (replacement object manager and runtime, MGZ1, exactly one life per
  death, outgoing live history retired), P1 at the post's own Saved X/Y on the first reload
  frame, title/fade/assembly/control release, current roster/camera width, the reloaded post
  starts used and passing it again allocates no second orbit child. The released incoming
  world is captured and replayed 45 neutral frames twice in both restore directions
  (`CheckpointIncomingReplay`); no raw snapshot crosses the load. Two death/respawn cycles.
- Negative: the same inlet with 16 Right frames then neutral settles left of the window and
  never saves; resuming Right saves inside the window.

Configurations: native320 Sonic (independent method), the native Sonic+Tails pair, and the
30 supported solo rows of the entry generator (five production presets × off Sonic/Tails/
Knuckles, S1 Sonic, S2 Sonic/Tails). Open: the 110 multi-participant rows. In the bounded
authoring runs (p3/p4 on `0197c5a5a` plus this class), 25 of those 110 rows (mostly
seven-follower rosters, some pairs at 400/800) left P1 standing under the ball's sweep with 0
rings for 2400 frames without a hit, while every solo row died there. The owner is not
established (ball phase/standing spot versus multi-follower touch arbitration; the ROM has only
Player_2), so those rows are neither claimed nor forced. Posts 2 and 3, a cold route to the
post and native pixel/audio acceptance also remain open.

Red check: a temporary class (not committed) restored the pre-approach world and replayed one
frame fewer than captured; the same full-world comparison failed (`dynamic-art-diagnostics`
frame counters first). Observation for the entry matrix owner: `CanonicalEntryMatrixSupport`
`advanceEntry` passes its `right` flag as `stepFrame`'s second (Down) argument, so the entry
rows' 45-frame "Right" replay windows held Down. The separate input correction above now requalifies held Right.

Qualifying run `q1`: `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestS3kMgz1PhysicalCheckpointLifecycle,TestMgz1RedTemp"`
with the original absolute S1/S2/S3K ROM properties and `test`, on `0197c5a5a` plus this class:
35 tests / 0 failures / 0 errors / 0 skips (class 34 in 12.35 s; temporary red 1), Maven
finished 2026-10-17T01:39:47Z. Only class comments changed after the run. Test-only; no
production source changed, so no neighbour or required-four rerun was needed.


## Native attracted-ring intervals (2026-10-22)

The [native SAT evidence](../../designs/2026-10-22-native-sprite-scanline-budgets.md#ring-repair-and-two-movie-qualification)
qualifies two independent committed BK2 input intervals through fresh ROM MGZ1
entry: native320 Sonic plus CPU Tails, natural LIGHTNING acquisition, actual
reserved-slot ring motion, bucket2→bucket1 admission, awards, sparkle deletion
and destructive registered restore/forward. Exact test:
`TestS3kAttractedRingMoviePhase` (2/2, zero failures/errors/skips in focused job36034).
The standalone interval runs rows0–650 with rewind534→550; the complete-movie
MGZ interval runs rows0–500 with rewind447→465. Comparison rows never install
pose/camera/RNG or shield state. Separate short source-backed controls cover
dead-P1 flight/sparkle and converted-bouncer per-SST animation copies.

This is a bounded ring contract. Other mains, donors, widths, participant teams,
complete traversal/physics/art and full-campaign incoming arrival remain inherited
obligations; the act remains uncertified.

## Music ownership follow-up (2026-11-02)

The [cross-game music audit](../../audits/2026-11-02-music-transitions.md)
records corrected native request IDs and/or level-restore selection affecting
this route. Restoration selects the apparent playlist and P1 invincibility at
the native boundary. These local checks do not requalify this matrix’s inherited
route, viewport/donor/team, lifecycle or audible-comparison gaps.

## Seamless horizontal lock — 2026-11-02

`MGZ1BGE_Transition` shifts the live horizontal bound during the Act 2 reload.
The transition request now holds the engine's minimum-X, maximum-X and
minimum-Y easing destinations at the shifted live bounds until native release. `TestS3kMgzTraceReplay`
exercises the production handoff; this local correction does not certify all
viewport/donor/team routes or close the matrix's inherited rewind and route gaps.

At baseline `4311f21c4`, the short replay has 8,469 errors starting at row
13,939 (`camera_x`, 0 vs 2). With the bound targets retained, its physics
comparison passes; four strict animation errors remain at rows 14,384–14,385.
The ROM aux records EndSignControl in slot 7 before results/title in slot 8,
restoring native control on the following pass. A local native-control bridge
probe did not change these errors and was removed. No slot-order/title timing
change or whole-route certification is included here.

The requested Tails full-chain MGZ3 comparison worsens from 3,020 to 3,161
errors with the same row-zero `y_speed` first error. Its first remaining camera error
is row 2,035 (ROM X=1, engine X=0). This blocks integration of the candidate;
the later release disagreement and the four short-trace animation errors
remain unresolved. CNZ's analogous bound-target candidate
was rejected (3,112 → 3,941 replay errors), and CNZ code is unchanged.

A source-predicate bisect over `2f5265e3f..4311f21c4` isolates
`15accb4f426dc61d81e2213faec6f114d09adc97`: transition overrides changed
from setters synchronizing current/target bounds to current-only setters.
This was not a historical full-replay bisect. A temporary executed transition
probe observed max X `0000/6000`, min X `0001/0000` (current/target), followed
by max/camera X `0,2,4,6,8,10`. Reverting the candidate reproduces the original
8,469 errors and row-13,939 camera mismatch exactly. The probe and attempted
control-owner change were removed; trace comparison rules are unchanged.

## Follow-up attribution — 2026-11-02

Matched, observationally instrumented runs of the original transition request
and `9d3fe74c9` reproduce 3,020 and 3,161 MGZ3 error groups. These totals count
contiguous divergence groups, not mismatching frames. No comparison is suppressed,
demoted or re-baselined.

The first difference is a **removed** `camera_x` error at row 1,279: ROM X=0,
original engine X=2, corrected engine X=0. P1 X/Y are `0105/08C0` in ROM and
both engines. Post-step bounds and targets, in minimum-X, maximum-X, minimum-Y,
maximum-Y order, are:

| Build | Bounds | Targets |
| --- | --- | --- |
| Original | `0000,0004,080C,0810` | `0000,6000,0000,0810` |
| Corrected | `0001,0000,0810,0810` | `0001,0000,0810,0810` |

`MGZ1BGE_Transition (sonic3k.asm:106358-106390)` offsets the live lock; it
does not expand it toward the loaded level sizes. The corrected request models
that behavior. The original engine has already started its unrelated generic
2-pixel easing by this post-step sample.

The first newly added row/field error is `camera_x` at row 2,061: ROM X=`0065`,
original engine X=`0065`, corrected engine X=0. This is already a divergent
release state in **both** builds. From row 1,889, ROM has restored P1 control
(`object_control=00`, WAIT animation), while both engines retain object control,
victory animation and active results. At row 2,033, both engines already have
camera Y=`0810` versus ROM `0813`; at row 2,061 both have Y=`0810` versus ROM
`0860`. Both still report `End_of_level_flag=false`, results active and control
locked. Original bounds/targets then are `0000,0620,01F0,0810` /
`0000,6000,0000,0810`; corrected bounds/targets remain
`0001,0000,0810,0810` / `0001,0000,0810,0810`.

`Obj_EndSignControlDoStart (sonic3k.asm:180511-180515)` waits for the completion
flag before `Change_Act2Sizes (sonic3k.asm:180671-180706)` creates the gradual
boundary workers. Holding the corrected bound while that engine flag remains
false is correct. The original stale target accidentally permits camera following
without that owner. Its later agreement in X masks the existing results/title
release desync, rather than establishing a correct release path. Entry-state debt
is also explicit: row-zero y speed is `0038` versus ROM 0, camera Y is `0E16`
versus `0E12`, and rings are 0 versus ROM 161. The exact cause of the delayed
release is not assigned to any one entry-state field here.

The MGZ3 group-count increase is therefore **not an attributable regression in
the transition correction**. The initial integration block based on that count
alone is superseded by this matched row/state attribution. The short MGZ trace's
four strict animation errors remain a separate frontier pending owner diagnosis.

### Remaining short-trace animation owner

A temporary VICTORY-to-WAIT setter stack probe and post-comparison slot snapshots
reproduce all four errors. On rows 14,382–14,383 the engine flow is
`AWAIT_RESULTS` in slot 21, while results occupy slot 7. The last results child
retires at 14,383; results clear the active latch at 14,384. In that same object
pass, the later slot-21 `S3kBossDefeatSignpostFlow.updateAwaitResults()` calls
`restoreNativePlayerControl()` for P1/P2 and writes WAIT. No inference from the
numbers is needed: both setter call stacks reach that owner. Row 14,384 is
expected animation `13`, actual `05`; row 14,385 has the two resulting mapping
mismatches (`B2/BA` for P1 and `A6/AD` for P2).

ROM aux has `Obj_EndSignControlAwaitStart` in slot 7 before results/title in
slot 8. `loc_2DD06 (sonic3k.asm:62753-62764)` clears `_unkFAA8`; the lower
slot observes that on the following pass. Then
`Obj_EndSignControlAwaitStart (sonic3k.asm:180502-180508)` calls
`Restore_PlayerControl2 (sonic3k.asm:180455-180461)`, which writes WAIT to
animation and previous-animation and clears the animation counters. Native
control therefore restores at 14,385, not 14,384.

This cause predates the camera fix: the reverted-target control also reports its
first animation error at 14,384 (`13` versus `05`), and neither the miniboss nor
the defeat-flow implementation changed in `9d3fe74c9`. The engine
`MgzMinibossInstance.queuePostDefeatFlow()` allocates an auxiliary flow instead
of the native in-place `loc_887CA (sonic3k.asm:185107-185110)` handoff. A proper
slot correction must also separate the boss's still-running embedded explosion
controller lifetime; deleting/transferring the boss immediately would truncate
that work. This is not treated as a safe isolated timing adjustment. The four
strict animation errors remain the new frontier; no delay, comparison exception
or slot/lifetime rewrite is included in this camera-bound correction.

### Original current-only setter intent

`15accb4f4` ports FBZ's synchronous reload, native SST survival and independent
camera-copy/target words. It deliberately changes shared transition overrides
from synchronized setters to current-only setters and adds explicit target
fields. FBZ rebases its live X bounds and its independently stored X targets,
while retaining the independent Y targets. Its transition test asserts current
max-X `00A0` versus target `01B0`, and current min-X `0020` versus target
`FF10`; synchronizing the shared setters again would destroy that contract.

The MGZ correction leaves those shared setters and FBZ's request unchanged.
Only MGZ supplies targets matching its shifted held bounds. The original
`TestFbzActTransitionHeadless#screenEventReloadPreservesLiveTeamStateAndUsesShiftedWindows`,
`TestFbzTransitionRewind` and `TestS3kScreenEventCameraCopies` are the focused
intent checks for this follow-up.

### Follow-up validation and merge assessment

The fresh matched MGZ3 runs compare 22,934 rows each with no skips and reproduce
3,020 / 3,161 groups. The short MGZ owner probe compares 35,861 rows and
reproduces exactly four animation errors, no physics errors and no skips.
After removing every probe, the original FBZ intent checks above plus the required
S3K keep-green set and camera snapshot checks pass **69 tests, zero failures,
errors or skips** in the ordinary smoke profile. All added ROM citations resolve;
the one pre-existing unverifiable citation is unchanged, and whitespace checks pass.

This follow-up changes documentation only. Java, test sources, POM and resources
remain identical to `9d3fe74c9`; the other requested r6, segment and r7 results
from that runtime state remain applicable. Their strict error totals and first
errors are unchanged, not re-baselined. No broad smoke or release full suite is
claimed.

The branch is safe to merge as the bounded camera-regression fix that advances
the short MGZ frontier from row 13,939 `camera_x` to row 14,384
`player_animation_id`. It is **not** a green strict MGZ replay or whole-route
certification. The larger MGZ3 group count is attributed to its already-divergent
results/control release, and the remaining four short-trace animation errors
have the independent pre-existing slot-order owner described above.

## Bubbles offscreen continuation — 2026-11-03

`Obj_WaitOffscreen` (sonic3k.asm:180362-180394) consumes the preceding
`Render_Sprites` visibility. Its `loc_85B02` restores the object's continuation
and returns; initialization belongs to the next object pass. Bubbles now retains
that visibility through post-camera publication, returns from the waiting pass,
and exposes zero collision flags until its init routine runs.

`TestBubblesBadnikInstance.offscreenContinuationReturnsBeforeInitializationAndReplaysAfterRestore`
checks the continuation-only pass, init-only pass and first movement, including
capture/restore at the pending-init seam. The focused touch/badnik group passes
88 tests without skips. `TestTouchResponseManager.killedEnemyStillHurtsLaterVulnerableSidekick`
pins the existing later-player hurt path under S1, S2 and S3K rules.

The MGZ zone-slice replay's row-5255 probe showed that the killed badnik remained
in Tails' touch list with flags `$12`; its engine X was `$13F8`, one pixel left
of ROM `$13F9`. Correcting activation restores that position and the same-frame
hurt response. The focused slice moves from 7921 errors at row 5255 to 5367 at
row 16511. The Sonic+Tails segment moves from 4100 to 3447 errors, retaining its
first row-10709 X mismatch; the row-11618 cluster is removed. These remain strict-red
fixtures, not act certification. These figures describe the focused runs;
full-profile comparison is a separate delivery gate. Wider character, donor, viewport, team, native presentation and complete
route/lifecycle obligations remain open. The separately identified raw-animation
callback continuation discrepancy is unchanged by this correction.
