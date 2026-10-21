# S3K IceCap Act 2 coverage matrix

Canonical binding: `S3K_ICECAP_2`, engine zone 5/act 1, ROM `$0501`, S3KL object set.
Edges: ICZ1 incoming structural act handoff; LBZ1 outgoing full entry.
Character routes: Native starts and end-route contexts vary by main character; all three short native starts are checked separately.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kIcz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kIcz2EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| OBJECT / EVENT / REWIND: cork/floor and slide terrain | `TestS3kIcz2StaleObjectGrounding`, `TestS3kIcz2CorkFloorRegression`, `TestSonic3kIczRewindRoundTrip`, `TestS3kIczIceObjectRewind`, `TestS3kIczPlatformHazardRewind` | Declared local/constructed ownership and terrain contracts; independent before/active/release placement products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| BOSS / REWIND / LOAD: end boss graph and exit | `TestS3kIczEndBossObject`, `TestS3kIczEndBossGraphRewind` | Component graph/render contracts; real arena admission, complete character paths and released LBZ1 destination products remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kIcz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. |
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

[`TestS3kIcz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kIcz2EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

After each title/fade/fresh-assembly release, the complete registered world is captured, advanced for 45 neutral logical ticks, restored immediately and replayed twice. Each forward tick checks P1 survival, unchanged lives and actual configured owners. Intrinsic intro control remains native-owned; this is not a forced unlock or a raw restore across the load. Physical pit traversal, physical post capture/respawn, completion edges and later mechanic lifetimes remain separate.

One coherent focused invocation qualified all eight acts: command session54147 on runtime `e57236d519a861c9fbc43ba867230793dbd6fb0d` plus this commit's nine test-only deltas, 1120 tests / 0 failures / 0 errors / 0 skips (each act 140/0/0/0), actual terminal exit0 at 13:42:47 BST (12:42:47 UTC), Maven 3m05s. Exact selection: `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix`, `TestS3kIcz2EntryMatrix`, each restricted with `#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice` in the comma-separated `-Dtest` selector. Command prefix: `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`; original absolute S1/S2/S3K ROM properties were supplied, followed by `test`. Entry rows were not rerun merely to repeat their unchanged evidence. This is finite lifecycle qualification, not complete act, native-pixel, strict-trace or arbitrary roster certification; combined smoke/integration/push remain lead-owned.


## Cork frame/table and fragment lifetime — 2026-10-14

This appends to the canonical matrix from `d13585237`, preserving its qualified
140 normal-entry/140 restart product records and their exact source limits.
Cork Init now stores the subtype nibble directly. `loc_2A816` uses pre-break
frame*4 to select `word_2A84C`'s high-launch suffix: native0/2/4/6/8 mapping
indices produce14/12/10/8/6 pieces. The bit4 small-floor frameA→B branch retains
the four-entry `word_2A884`; alternate-plane/signed-subtype admission is unchanged.
The original SST is piece0, sibling allocation stops on checked failure, and
native fractional motion, byte-copy priority and prior-render deletion restore
without ghost allocations.

Independent literal checkpoint tests cover those branches, partial pressure and
two-way recreation/two forward cycles. The retained `TestS3kIcz2CorkFloorRegression`
physical rolling-loop route passes within affected74672's607/0/0/0 on the owned
Cork source over `d8025e094`;111 structural/source/timing guards pass. This does
not rerun or expand the dated140-product qualifications. [Exact evidence](../../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#cork-same-record-fragment-and-icz-table-follow-up).
Cork-specific physical contact/registered-world replay across all presets,
donors/rosters, whole-act completion/load/history and native pixels/audio remain
separate obligations; no ICZ strict sweep or full level certification is claimed.

## Entry replay input correction — 2026-10-17

`CanonicalEntryMatrixSupport.advanceEntry(fixture, row, frames, right)` passed its `right` flag as the second argument of `HeadlessTestFixture.stepFrame(up, down, left, right, jump)` in every committed revision from the helper's introduction in `a54c1a8ae` through `0197c5a5a`. The `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice` runs recorded above on those sources therefore held **Down**, not Right, in each 45-frame capture/replay window, so a grounded P1 ducked in place instead of moving. Request 83519's uncommitted initial candidate cannot be inspected here; treat its replay input as unverified rather than as Right. Those records keep their original commands, sources and counts and remain evidence within that Down-held scope: actual start table, owners, survival, unchanged lives and full-world restore/replay equality. They did not exercise held-Right movement, the camera scroll it causes or the object placement that scroll admits. The restart products' post-release windows were already neutral and are not affected.

The helper now passes the flag as `right`. [`TestS3kCanonicalEntryRightInput`](../../../../src/test/java/com/openggf/tests/TestS3kCanonicalEntryRightInput.java) drives the same helper on native 320 solo Sonic in CNZ2, whose start is grounded on flat floor, and checks the production joypad path on each of 45 frames (Right held; Up, Down and Left absent; not crouching). It then requires P1 still grounded with `ground_vel` 45 × `$C` (`Sonic_Move` adds `Acceleration` for `button_right` in `Ctrl_1_held_logical`) and forward movement.

Corrected qualification, all on `0197c5a5a` plus this commit's test-only deltas (helper SHA-1 `49e31e0026db`), command prefix `env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, original absolute S1/S2/S3K ROM properties, then `test`:

- Eight-act entry selection: `-Dtest` lists `TestS3kAiz2EntryMatrix`, `TestS3kHcz2EntryMatrix`, `TestS3kMgz1EntryMatrix`, `TestS3kMgz2EntryMatrix`, `TestS3kCnz1EntryMatrix`, `TestS3kCnz2EntryMatrix`, `TestS3kIcz1EntryMatrix` and `TestS3kIcz2EntryMatrix`, each restricted with `#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`, plus the first version of the input test. It finished at 02:02:24 UTC after a 193 s queue wait and 1m46s of Maven: 1121 tests / 1 failure / 0 errors / 0 skips. Every act passed 140/0/0/0 with held Right. The one failure was the input test's own premise. It first used MGZ1, whose Sonic start (ROM `$C0,$F00`) is airborne, so `ground_vel` stays zero while air control raises `x_vel` by 24 each frame. The test was moved to CNZ2; the helper was not changed.
- Revised input test with the helper restored to its `0197c5a5a` bytes: fails at Right frame 0 (`isRightPressed` false), 02:04:26–02:05:01 UTC, 1 test / 1 failure / 0 errors / 0 skips.
- Revised input test with the fix: 02:05:15–02:05:44 UTC, 1 test / 0 failures / 0 errors / 0 skips.

Death/reload products, combined smoke and integration were not rerun here. This requalifies the held-Right entry floor only; it is not act certification.


## Incoming synchronous retained16 handoff — 2026-10-17

ICZ R11 preserves559 known outgoing native pattern groups with current ICZ2
collision indices until the existing queued payload publishes all589 groups.
The independent component checks two forced whole-world restores/publication
replays within the new ICZ2 timeline. Initial77 focused cases and the later4
opaque-extent repair cases are separate source epochs, not one combined rerun.
Unknown688..717 remain absent until publication; async/cold/presentation builds
receive no new predecessor. [Exact sources, old-reds, commands and limits](s3k-icz-act1.md#r11-synchronous-known16-handoff-repair--2026-10-17).
No ICZ2 route, native pixel, full aux, physical death/reload or full-act claim follows.


## Ordinary solo Sonic boss-admission authoring frontier — 2026-10-21

A bounded input-only authoring bout on unchanged runtime
`7fbcfbd8a4597163390d5d813facec4c3e69a637` used direct `5/1`, native 320,
solo Sonic, donor off and explicit title-enabled `GameplayCaptureSession.Settings`.
All position, rings, checkpoint, emerald, clock, camera-fraction and reverse-gravity
setup fields were null/false. This admits the existing fresh assembly/title owners;
it does not import an ICZ1 world or a native trace. The ROM Sonic start
`$07E0/$0370` and placed boss record 340 (`$4490/$05B8`, ID `$BD`) remained controls.

Eleven exploratory branch requests produced 30 controller variants. One separate
uninterrupted 1800-input observation retained public loop/path/cork fields.
All 12 original Jupiter harness invocations passed 1/0/0/0 with no skips, but those
harness passes are **not route passes**: branches may stall or die. The best
proposal ends alive at `$22F5/$08EE` with 6 rings after 3821 inputs, before boss
placement/admission. One periodic-jump variant died at zero-based input 450.
No runtime omission was established; no new maintained boss-route consumer or
controller resource was promoted. Two fresh full-route witnesses, admission
rewind windows and the final route-class check were therefore not launched.

The real loop path marker 157 (`$2180/$0600`, subtype `$91`) switched C/D→E/F
at input 1709 in the observed prefix; actual corks 150/151 broke at 1724/1733.
Releasing Right through that drop let the next proposal reach the lower cave.
The remaining source-backed interaction question is actual ice cube 162
(`$2240/$08F4`) → ordinary rolling top landing/shatter → button 163
(`$2240/$08FC`, subtype 2) → trigger-mode collapsing bridge 165
(`$22C0/$0911`, subtype `$82`). `Obj_ICZIceCube`/`sub_8B3AA` requires saved
animation 2 and real standing contact; the button writes momentary trigger 2,
and `loc_20B40` latches that trigger in the bridge. This chain was not completed
or observed, and is not proof of a terrain-wall defect or an obligatory whole-act path.
The last checked restorable branch prefix is 2040 inputs of the lower-cave
proposal: alive `$221D/$08EE`, 6 rings, LEVEL, camera `$217D/$088E`; pending fresh
assembly and callback-bearing fade were rejected by the existing author tool.
Resume by replaying inputs from row 0, never by importing that checkpoint.

Useful input pairs, the exact temporary adapter source and an already-created
normal end PNG remain in the owned external task directory named in the dated
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md).
Raw CSVs/PNGs other than that end still, temporary repository source/class and
owned Maven reports were consumed. This changes the authoring frontier only;
boss admission, fight/exit, incoming ICZ1 history, ordinary full-act completion,
other roster/donor/widths and native timing/pixels remain open.


## Ordinary cube/button2/bridge cold crossing — R2, 2026-10-21

The follow-up to the authoring frontier above reaches the actual lower-cave
interaction on unchanged production `7fbcfbd8a4597163390d5d813facec4c3e69a637`,
starting from docs-only `79103a4aa4cad48662cd32c521a325cc87869364`.
Only three additional controller policies were tried. The selected policy retains
all 2040 prior input rows, holds Jump before steering onto cube 162, releases
movement for the landing/bounce/button, then continues Right. The final 2292-row
proposal is an exact prefix of that policy, ending before the later enemy encounter.

Two fresh JVMs independently cold-replayed the final input, drawing every tick,
without capture/restore, authoring calls or gameplay seeds. Each original
`TestIcz2ColdRouteAuthor#coldWitness` passed 1T/0F/0E/0S. Both measured the same
placed-owner chain: cube 162 saved roll animation 2 top landing/deletion and native
`-$300` bounce at input 2095; actual button 163 riding/standing and trigger 2 at 2131;
the same bridge 165 first fell from Y 2321 to 2322 at 2183; P1 was grounded beyond
its stock `$50` right span plus player radius at 2276. After row 2291 P1 is alive,
grounded and control-released at `9023,2544`, moving right at 713, with 6 rings and
3 lives; camera `8863,2448`, same 5/1 world, native 320 solo Sonic/off.

The witness checks production script-compiler/BK2-loader equality for every row
and both pads, neutral P2/no Start/debug inputs, the unchanged 2040-row prefix,
and the final prefix against its original candidate. Explicit title-enabled
15-field Settings retain null/false gameplay setup fields. The input pair and
exact temporary witness source remain outside the repository; see the dated
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md).

This is repeated ordinary gameplay evidence for this partial interaction/crossing,
not a runtime fix or maintained regression-test qualification. A maintained input
resource/consumer and the affected full-registry rewind spots are still pending.
No snapshot or newly admitted rewind checkpoint is retained. Boss admission,
combat/exit, full act, incoming ICZ1 history, roster/donor/viewport breadth and
native timing/pixel conformance remain open.


## Maintained ordinary partial crossing — 2026-10-21

[`TestIcz2ColdCubeBridgeRoute`](../../../../src/test/java/com/openggf/tools/TestIcz2ColdCubeBridgeRoute.java)
promotes the exact R2 2292-row script/BK2 as an ordinary partial-route consumer.
The independent packaging test checks all rows/both pads through the production
compiler/writer/loader, neutral P2/no Start/debug/state payload, full-file hashes
and the original 2040-row prefix digest. The route uses the same explicit
title-enabled native 320 solo Sonic/off direct 5/1 inlet, with all gameplay setup
fields null/false and normal drawing on every main and branch input. It checks
no death or life loss on every input; it permits the prefix's earlier hurt.

Ten 45-input whole-registry spots cover before/active/after path/corks
1664/1709/1724/1733/1778, cube approach/shatter/button 2040/2095/2131, bridge fall
2183 and the final crossing 2246→2291. Each compares all 60 registered keys on
immediate restore and two forward cycles, restores the external held-input
history, and rejects loads/mode changes/fresh assembly/callback-bearing fades.
Actual normal-update references and captured ObjectRefIds bind the placed owners;
equal class/coordinates are not used as identity across restore.

The original 35T/1F group failed the second restore at row 2040. An authorized
error-context-only method diagnostic (1T/1F) identified
`IczCrushingColumnObjectInstance.BottomDecoration`, raw ID `$AF`, child entry
slot 16, separately ObjectRefId slot -1/dynamicId 253. Its immutable birth spawn
Y/rawYWord changed from 2818 to 2817. Reconstruction now uses the captured spawn;
live drawing still follows `parent.y + $B0` (`loc_8A612`). A short independent
actual-manager component reconstructs twice after ordinary parent movement and
checks both the complete birth record and further live following. It is a
declared component fixture, not a second cold route.

The affected 107T/1F/0E/0S group completed all ten world windows and passed its
other 106 cases, including input packaging, 12 column controls, 8 cube, 11 button,
14 bridge and 60 required S3K core cases (both level-loading classes are included).
Its remaining failure exposed the new consumer's break/retirement confusion:
`isDestroyed` observed cork retirement at 1746/1758; public `isBroken` is the
actual break writer at the unchanged expected 1724/1733. Only that detector was
corrected; no runtime/input/timestamp or other assertion changed.

The corrected-detector route method (73490) still fails 1T/1F/0E/0S on cork151:
actual break1732 versus old expected1733, with all ten windows complete and the
requested cube/button/bridge/crossing observations unchanged. Retained observer
source shows that1733 came from attempt07, while this movie retains attempt09's
prefix; they first differ at1724 (Right versus neutral). That old data point did
not apply to this input. No runtime/input/timestamp fitting was performed.

After Root's bounded source/data review, only the cork151 expected movie datum
changes to1732; window1733 and all inputs/runtime/predicates remain unchanged.
The final exact route method (96349) passes **1T/0F/0E/0S**, with all ten 60-key
windows and 3642 normally drawn main/forward/replay inputs. It asserts actual
cube162 shatter/-$300 bounce2095, actual button163 standing/trigger2 at2131,
same placed bridge165 first fall2183 and grounded crossing2276. After input2291
P1 is alive, grounded and control-released at9023/2544, velocity713/0, rings6,
lives3 and camera8863/2448, with the original native320 solo Sonic/off roster.

Qualification combines that separately executed route pass with the affected
group's 106 unchanged passing cases; it is not a wholly green107-case invocation
or a full-suite result. The maintained partial interaction and these same-world
rewind spots are locally qualified on the pinned private-owner source; incoming
load/history boundaries and other configurations are not added.

The original failures and diagnostic remain recorded in the dated
[completion plan](../../plans/2026-10-13-s3k-playable-completion.md). This consumer
adds only this partial native 320 solo Sonic/off interaction and same-world replay.
Boss admission/combat/exit, full act, incoming ICZ1 history, roster/donor/viewport
breadth and native timing/pixel conformance remain open.
