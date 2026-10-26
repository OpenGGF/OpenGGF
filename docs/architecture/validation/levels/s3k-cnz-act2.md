# S3K Carnival Night Act 2 coverage matrix

Canonical binding: `S3K_CARNIVAL_NIGHT_2`, engine zone 3/act 1, ROM `$0301`, S3KL object set.
Edges: CNZ1 incoming structural/results handoff; ICZ1 outgoing full entry.
Character routes: Sonic/Tails reach the end boss; Knuckles uses native cutscene/teleporter progression. Do not infer one route from another character code.

Status: in progress. The bounded normal-entry and graph floor below is qualified; the act is not certified.
Supported production presets are 320/352/400/528/800 via `WidescreenAspect` and `resolveDisplayAspect`. Configured main/donor support is off×{Sonic,Tails,Knuckles}, S1×Sonic and S2×{Sonic,Tails}. `LaunchProfile.sanitizedFor` owns configured fallback; script-created NPCs have their own native registration/art authority. Configured comma rosters are uncapped by `ActiveGameplayTeamResolver.parseConfiguredSidekicks`; pair, mixed, duplicate and declared large-roster examples do not establish a maximum.

The initial native/default binding covers all three solo mains at 320; the finite product follow-up extends that floor. The 140 finite normal-entry products below now qualify all five production presets, supported main/donor pairs and solo/pair/mixed/duplicate/seven-follower examples. Independent actual death/reload now qualifies the same 140 supported products. Other entry mechanisms, physical posts, mechanic-specific authority and route composition remain separate obligations. Raw 512/640 injection, when used by older local tests, is custom geometry evidence rather than production-preset coverage.

## Obligations

| Obligation | Contract / existing binding | Current evidence and shortest remaining action |
| --- | --- | --- |
| ENTRY / CAMERA: normal character start and current focus | `Sonic_Start_Locations` / `Knux_Start_Locations`; [`TestS3kCnz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`](../../../../src/test/java/com/openggf/tests/TestS3kCnz2EntryMatrix.java) | 140 supported products: actual host-ROM start table, all resolved production widths, typed admitted roster/ordered CPU leaders/current native P2 and camera focus, decoded current mappings and unchanged P1 lives. This is a fresh entry floor, not every intro/alternate-entry mechanism. |
| REWIND: normal entry world | Complete registered-world capture/restore and 45 ordinary Right-input frames twice; all title-card pattern bytes compared by content | Qualified for all 140 finite products. Held Right is requalified by the 2026-10-17 input correction below; the 2026-10-14 runs held Down. No P1 death or hidden life loss is permitted. This does not certify later event/interaction/child lifetimes or cross-load history policy. |
| OBJECT / EVENT / REWIND: water/cork and Knuckles switch graph | `TestS3kCnzWaterLevelCorkFloorGraphRewind`, `TestS3kCnz2CutsceneButtonGraphRewind`, `TestS3kCnzMechanismRewind`, `TestS3kCnzLocalMechanicsRewind` | Declared component graphs; actual placement and team/viewport/donor interaction breadth remain open. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| EVENT / CAMERA / LOAD: Knuckles teleporter route | `TestS3kCnz2KnucklesEventAdmission`, `TestS3kCnz2KnucklesAdmissionControls`, `TestS3kCnz2CapsulePlcAdmission`, `TestS3kCnz2KnucklesCapsuleHandoff`, `TestS3kCnz2KnucklesTeleporterCharge`; existing event/teleporter consumers | Actual primary/role/X/Y admission, separate allocations, physical clamp and full FIFO append are locally qualified below. The [capsule handoff](#knuckles-capsule-results-and-camera-opening--2026-10-20) covers real results/FG4 completion, allocation retry, P1 control and gradual camera opening. The [native charge prefix](#knuckles-native-beam-capture-and-lift-prefix--2026-10-20) and [real ICZ handoff](#knuckles-hide-camera-rise-and-icz-handoff--2026-10-20) extend that positioned pre-results inlet through ordinary controller travel, beam/capture/lift, hide `$18`, camera rise and actual ICZ release with complete-world replay. The combined gate retains two ICZ failures; physical publication, product breadth and cold traversal remain open. |
| BOSS / REWIND: end boss, children and scatter | `TestS3kCnzEndBossHeadless`, `TestS3kCnzEndBossGraphRewind` | Component/positioned boss contracts; natural arena and uninterrupted playable exit remain separate. Listed bindings were setup-inspected, not independently rerun as a whole in this slice. |
| LIFE / LOAD: physical post, actual death/restart and incoming/outgoing worlds | Native checkpoint/death/reload owners and the edges listed above | All 140 supported preset/donor/main/roster products are qualified by `TestS3kCnz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`: two actual deaths/reloads, exactly one life lost per restart, new world owners, outgoing live-history retirement, restored current roster/camera and two complete-world 45-frame restore/replay cycles after each released destination. Physical post activation and incoming/outgoing route edges remain separate obligations. |
| PRESENT: actual viewport, palette/scroll/plane and audio | Act-owned ROM art/AniPLC/AnPal/event and music/SFX owners | Decoded playable source is checked at entry. Scene pixels, native audiovisual timing and viewport-edge parity require their own bounded native/GPU evidence. |
| ROUTE: representative completion and compatibility axes | Authentic entry→mandatory mechanics→boss/results→released next state | Complete character-route composition, every preset/donor axis and applicable team-shape traversals remain open; short entry is not route certification. |
| ORACLE: timing-sensitive native behavior | Existing CNZ trace bindings and [frontier log](../../../status/trace-frontier-log.md); no new strict trace run in this slice. | Preserve each owning strict-ledger result/source. Comparison rows do not supply gameplay; no strict trace or conformance sweep was run here. |

## Native P1 vertical-bound operand — 2026-10-21

Native `CNZ2_ScreenEvent` (`sonic3k.asm:107927..107943`, reference commit
`044fa46725c71187399e13f5ddb70e11d32dc024`, blob
`0f10a373ff0e3d5c0033ed7d13c678bcb927c7b0`) reads unsigned
`Player_1+x_pos`. Below `$0940` it writes minY `$0580`; `$0940..$45FF`
writes zero; `$4600..$FFFF` leaves the existing bound untouched. The runtime
Y-start helper resolves the registered main playable, as adjacent CNZ2 P1
admission/control owners do. Its null guard, word mask, thresholds and
conditional bound write are preserved. Camera targeting remains independent.

`TestS3kCnz2KnucklesAdmissionControls#act2YStartUsesNativePrimaryWithContradictoryRegisteredSidekickFocus`
declares a native 320×224 Knuckles/Tails component inlet after real initial
Player2 setup. It selects registered P2 through `ObjectServices.camera()` and
calls the ordinary public `events.update(1, 0)`, without a private helper or
event-phase seed. It asserts the bound, unchanged main/P2 identities and
coordinates, retained P2 focus, and no late capsule/teleporter admission.

| P1 X | Focused P2 X | Initial minY | Native minY |
| --- | --- | --- | --- |
| `$093F` | `$0940` | `$0000` | `$0580` |
| `$0940` | `$093F` | `$0580` | `$0000` |
| `$4600` | `$093F` | `$0123` | `$0123` |
| `$45FF` | `$4600` | `$0580` | `$0000` |
| `$8000` | `$093F` | `$0123` | `$0123` |
| `$FFFF` | `$0940` | `$0123` | `$0123` |

The existing late-admission P2-focus control has both actors beyond `$4600`
and cannot distinguish this earlier operand. The existing scroll-band control
now registers its main sprite instead of treating an unregistered focus as P1.
RED on the original runtime produced6 expected minY failures/0 errors/0 skips.
GREEN on the final source passed81 cases across8 selected classes, including all
six rows, the existing CNZ controls and five mandatory S3K classes;0 failures,
errors or skips. Exact commands, source hashes, environment and inspection limits
are recorded in the [dated plan](../../plans/2026-10-13-s3k-playable-completion.md#cnz2-native-p1-vertical-bound-operand--2026-10-21).


Root's normal merge `ff7e1b4d08b8b326159ba584ffcc2790b0ec9a40` then passed
the three composed CNZ classes:13 admission controls,1 event-admission control
and7 scroll controls, **21T/0F/0E/0S**. All actual identities,69 properties,
classpath/ROM/EGL values and complete streams were read; no skips. Suites
0.987/0.046/0.370s respectively; Maven displayed01:15min, queue/compiler/parent
intervals unknown. The source/config stayed unchanged, observed processes were
reaped and exact owned reports/tmp were consumed. This reuses the author's
60 mandatory S3K checks on the same runtime/POM/dependency footprint, without
rerunning unchanged coverage. See the [Root checkpoint](../../plans/2026-10-13-s3k-playable-completion.md#root-composed-soniccnz-and-visible-dez-checkpoint--2026-10-21)
for selection reasoning; all component limits below remain.

This is declared public component behavior. The existing 0.7 candidate API
already exports `Camera.setFocusedSprite`, `ObjectServices.camera` and the
native-P2 query; no API surface or version changes. No stock configuration
changing focus away from P1 was established. Cold-route impact, creator callback
transactions/fault handling, arbitrary-focus rewind, native pixels/timing and
whole-act/product qualification remain unproven. Inherited route, presentation
and rewind obligations above are unchanged.

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

[`TestS3kCnz2EntryMatrix#actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice`](../../../../src/test/java/com/openggf/tests/TestS3kCnz2EntryMatrix.java) reuses the separately qualified production death driver, independently of the normal-entry method. The same 140 supported rows cover all five resolved production presets, native/S1/S2 main support and solo/single/mixed/duplicate/seven-follower shapes. Each row boots this actual act, advances ordinary neutral inputs, invokes the semantic pit-death owner twice, requires exactly one life lost and replacement object/runtime owners on each real reload, and checks the current typed roster, CPU leader order, native P2 and camera width/focus after assembly. Nonempty outgoing live history must retire. No position, gameplay counter, checkpoint, destination or callback state is supplied to rescue the scenario.

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


## Knuckles event admission and raw PLC producer — 2026-10-18

Native044 `CNZ2_ScreenEvent/loc_5216C` at107969–107994 checks Player_mode3 and
actual Player_1 centre X >= `$4880`, Y >= `$B00`, independently of the later
`Obj_CNZTeleporter` arm. `AllocateObject` admits the teleporter; `CreateNewSprite4`
searches after that actual slot and admits capsule `$4980,$A20`. Only its success
reaches raw PLC `$083D74` (one `$0DD990` source, `$9280` byte destination,70patterns).
Either allocation failure still reaches FG4/clamp `$4750..$48E0` and clears End_flag.
The teleporter keeps its separate `$4A38/$4A40` airborne gate; admission cannot
force arming or a position snap.

These are explicitly positioned native320 component inlets, not prior traversal.
The original independent event oracle reached six final old-red leaves before
repair. Role/X/Y controls, real0/1free-slot failures, an actual first-P2 camera
focus control and two complete registered-world forced recreation/one-pass forward
cycles now pass. The P2 control first failed because its ordinary INIT/CPU setup
was unqualified; it now consumes the pending initial pass and one neutral CPU
settling frame before its declared positioned input, without a private phase seed.
The separate parallax rebuild fix is preserved at `2b1c5954`.

The raw producer oracle retains every existing FIFO entry and progress field.
Its first version failed an invalid empty-queue prerequisite (actual7queued entries),
so it was corrected before assigning semantic credit. The unchanged corrected
oracle then reached its final append assertion and failed: prior7 remained but
the eighth original descriptor was absent. The raw submit implementation now
passes that assertion and both allocation-failure controls. Aligned/span/count/
capacity/decode checks complete before queue mutation; signed-negative definitions
are empty. Reject unsupported occupancy before allocating/decoding entries.
The logical15-entry limit does not model shipped retained-sixteenth-slot behavior.

Root tree `ai-s3k-current-parity-composition`, HEAD`2b1c5954` plus qualified
service63a8/CNZcb119/teleporter7bde/tests4b55,8851,22ec: sole normal12-class
request72315 passed97/0F0E0S at15:45:02+01, Maven01:17, queue0s/7GiB8cores.
It includes both loading classes, bootstrap/decoding/AIZ skip, title-load/queue
rewind consumers, both admission classes, capsule producer and existing event flow.
Exact command/environment and full original case/property/stream/artifact manifests
are in the existing dated Plan/canonical root slice; Main absolute wrapper,3absolute
ROM paths, Java25, JTO-only EGL, fork1/reuse/alphabetical, normal/no-profile.
Earlier13-class377/2F had only the P2 fixture and derived-parallax restore failures;
those were separately repaired/qualified, and unaffected entry/teleporter/capsule
consumers retain their completed identities. No unchanged entry matrix was repeated.

All24 current original XML/TXT were fully inspected, including97identities/times,
72equalproperties, complete streams and skips. This is bounded local producer
validation. Ordinary Nemesis service must follow queued DMA, and preparation must
follow animated tiles; gameplay pattern publication/renderer refresh/world ownership,
full occupancy, real results publication/FG4 allocation retry/native music/control,
scene pixels and the full route still require their own implementation and evidence.
No native caller/readiness/service or route certificate follows.

## Knuckles capsule results and camera opening — 2026-10-20

The native `CNZ2_ScreenEvent/loc_521D2..loc_521FC` waits for the real
End_of_level_flag, allocates the ordinary gradual camera worker, targets `$49A0`,
restores level music and P1 control, then advances FG4 to FG8. Allocation refusal
returns with the handoff still pending. `Restore_PlayerControl` restores air,
object control and WAIT animation state; it does not address the camera's focused
sidekick. The capsule continuation now leaves the results flag for that event.
The teleporter parent no longer rewrites the old `$48E0` clamp each pass.

[`TestS3kCnz2KnucklesCapsuleHandoff`](../../../../src/test/java/com/openggf/tests/TestS3kCnz2KnucklesCapsuleHandoff.java)
uses a declared native320/off Knuckles positioned inlet, the actual admitted
capsule's solid button, ordinary grounded results and the real results exit.
The unchanged first oracle failed seven handoff assertions before the repair;
it passes afterward. The gradual worker's original fractional motion opens the
camera through41 entries, and two complete registered-world restore/replay cycles
match both the results handoff and the opening. A separate declared component
inlet splits the real object pass from ScreenEvent, fills the actual public pool,
checks refusal without side effects, frees one real slot and verifies retry.
With P2 focused, only P1 is restored; FG8 does not repeat allocation/music requests.
These are positioned/component checks, not a cold route recording.

On Root's `ai-s3k-current-parity-composition` at HEAD`691de5183`, the five-class
handoff/admission/capsule/teleporter request passed18 tests with0 failures, errors
or skips (Maven1m21s, reported queue0s). The separate four required S3K selectors
matched five actual classes, including both `TestSonic3kLevelLoading` packages,
and passed60/0F0E0S (Maven28.900s, queue0s). No unchanged matrix was repeated.
Exact selections, environment and source identity are in the [dated Plan](../../plans/2026-10-13-s3k-playable-completion.md#cnz2-knuckles-capsule-handoff--2026-10-20).

Coverage stops before the teleporter's independent `$4A38/$4A40` airborne arm.
Beam/lift, capture/hide, camera rise, ICZ entry, physical Nemesis/renderer
publication, scene/music identity, explicit Super-state controls and remaining
viewport/donor/team/cold-route products stay open. The act is not certified.

## Knuckles native beam, capture and lift prefix — 2026-10-20

`Obj_CNZTeleporterMain` now allocates the existing shared `Obj_TeleporterBeam`
after its own native slot. Refusal retains the Main state without publishing
beam/event, parent-X or sound effects; success sets parent/child X `$4A40` and
child Y `$A38`, leaving P1 X alone. The child performs the real first `$10` wait,
six signed `0/$FF` build pairs, second `$10` wait and odd-Level-frame expansion,
incrementing only when both low clock bits are set. CNZ uses `word_467FC`'s
flat-Y columns, art word `$24EE` (palette1, low plane priority, bucket1), and the
ROM KosM module at tile `$500`. Dedicated mapping rebasing resolves `$12` to
local0 and `$16` to local4 within its16 patterns. The parent draws no pad.

The lower parent slot reads prior child progress: signed values below8 return;
every entry at8 writes native object_control1 and roll status/animation2 without
changing position, fractions, velocities, radii or skid state. Progress9..`$17`
lifts one Y word on odd actual Level_frame_counter and moves X one word toward
the parent only when bit1 is also set. Explicit captured object references
restore both parent/beam links; the captured art selector retains the CNZ variant
through recreation. HPZ/SSZ variants retain their own table, palette and priority.

[`TestS3kCnz2KnucklesTeleporterCharge`](../../../../src/test/java/com/openggf/tests/TestS3kCnz2KnucklesTeleporterCharge.java)
keeps the public positioned button inlet **before** the real capsule/results
sequence. After actual results release, its primary method supplies only ordinary
Right and state-selected grounded Jump buttons. The qualified sequence uses97
travel entries, with jumps at actual X `$4980` and `$4A15`; then one art/landing
entry admits the real beam at LF1146. Build, capture and lift-end boundaries are
LF1163/1232/1267. All59 registered keys restore and replay twice through beam
admission, signed build, capture and lift `$11`. These measured positions select
no production condition; exact controller buttons repeat on replay. The prior
Right-only and single initial airborne-Jump attempts failed before arming at
`$4A15/$A2C`; they provide no semantic beam old-red credit.

Two independent short component checks cover real pool exhaustion/retry without
P1/sound/event effects and a declared positioned native parent-arm dispatch.
The first positioned version failed before arming at `$4A08`: it omitted results
opening, so the ordinary player boundary still read `$48E0+$128`. Its corrected
inlet explicitly invokes the original parent arm on the public posed P1 before
the first component snapshot. It does not establish capsule travel or weaken
the primary method. Both forward paths retain two full registered-world cycles.

On Root's composition HEAD`691de5183`, candidate1 used frozen Java/POM
fingerprint`11c6e9546ad81f1762f55d6749bd162e5ac62bf4d292029a10feb0d21180a163`
and completed34T/1F/0E/0S (Maven1m22s). The sole failure was that positioned inlet;
ordinary travel, allocation/art, existing CNZ handoff/route, beam rendering/graph,
HPZ transport and SSZ arrival/pads passed. After the test-only correction,
candidate2 fingerprint`5c3a5cfaa91757e14aacdfdff523dec8cbdfc66144404c07b16584c869864f98`
passed the affected three-case class, 3T/0F/0E/0S (Maven28.712s), without repeating
the31 unchanged control identities outside that class. Both fingerprints hash sorted `src/**/*.java` and
`pom.xml` relative-path-NUL/content-NUL inputs; production was identical between
the requests. The [dated Plan](../../plans/2026-10-13-s3k-playable-completion.md#cnz2-knuckles-native-charge-prefix--2026-10-20)
records exact selections/environment and the one failed setup. An independent
source review found no material issue; no integration, commit or push is claimed.

This qualifies native320/off solo Knuckles from the declared pre-results button
inlet through lift progress `$11`. It does not qualify cold traversal to that
button, viewport/donor/team breadth, explicit Super-state vectors, native pixels,
physical KosM/VRAM and renderer publication, hide `$18`, camera rise or ICZ entry.
The act and full Knuckles completion route remain uncertified.

## Knuckles hide, camera rise and ICZ handoff — 2026-10-20

Root's combined `cnz-icz-native-handoff-20261020/candidate-1` completed203 tests:
201 passed, two failed, none errored or skipped. Within that invocation, all four
`TestS3kCnz2KnucklesTeleporterCharge` cases passed. The new tail retains the
original positioned **pre-results** button inlet and controller-only travel after
real results release. It proves prior-child `$18` admission, native control3,
Y-word zero with retained pose fractions/velocities/radii, animation/map0 and
Scroll_lock. Subsequent parent dispatches subtract camera Y by `$10`; only an
unsigned new word below `$780` sends the native fade and `$0500` request.

The real `GameLoop` runs fade, title and fresh assembly into ICZ's typed Knuckles
arrival owner, releases P1 with current focus, and excludes Sonic's snowboard.
Native hide/rise restore and replay twice. Nonempty outgoing history retires at
the actual load; the incoming released world then restores and replays all59 keys
through45 ordinary frames twice. Its exact stdout records `riseEntries=31`,
`exitCameraY=1913` (`$779`), `sourceLF=1330`, `outgoingHistory=77` and
`incomingKeys=59`. The31 count starts after the test's three-entry rise checkpoint;
it is not a fitted production timer or the total rise duration.

That first invocation was not green: ICZ arrival pool admission and the
pattern-animator preinitialization snapshot domain failed. Both production
corrections subsequently passed in the 318-case composition run; four new beam
component cases then exposed a missing test-owned parent identity context. After
correcting that fixture, the affected 14-class composed run passed
**250 tests, no failures, errors or skips**, at all-`src`/POM fingerprint
`5ba31bd613122cb704f74d4b340d6abf62c5b4ec3081d1422f56fc348e9b7af6`.
This includes all four charge/tail cases, ten ICZ arrival cases, 140 ICZ entry
products, beam rendering, shared rewind identities/contacts, and HPZ/SSZ controls.

CNZ's sheet retains the generic terminal frame9 (eight ROM `$12` pieces, local0)
alongside frames1..8; pads0/10 stay empty. Its complete ROM-art assertion passes,
without claiming the CNZ route contracts its beam. The real outgoing fade/load
and released ICZ world replay qualify the positioned route tail. Cold traversal
to the button, product breadth, native pixels and physical KosM/VRAM/renderer
publication remain open. These local results are not integration or a complete
act certificate.


## Maintained cold Knuckles completion edge R6 — 2026-10-21

[`TestS3kCnz2KnucklesColdRouteCapture`](../../../../src/test/java/com/openggf/tools/TestS3kCnz2KnucklesColdRouteCapture.java)
now independently checks an input-only script/BK2 contract and the direct ordinary
cold native320/off solo Knuckles route from ROM0301/engine3/1 into **released
ICZ1 engine5/0**. Its explicit15-field inlet preserves titletrue and all-null/false
gameplay seeds. All6,989 R5 rows remain exact;175 neutral inputs pass native-owned
arrival release, making7,164 total. Two fresh ordinary JVM replays agreed before
adding the maintained checks; no author helper/restore ran in those replays.

The route reaches actual late admission4732, natural capsule button5444,
results5510/completion6047, beam6663, Y-word0/camera rise6813, load6870,
fresh assembly6987, release7050 and supported control-free endpoint7163.
It preserves solo Knuckles/main/movement/focus/lives, actual level replacement,
normal rendering and the final `$3640/$065C` pad floor, 0 rings/3 lives.
There is no no-hurt oracle: the earlier prefix has162 hurt-state samples, not162 hits.

Twenty-one actual role windows compare all60 registered keys, restore and replay
45 ordinary inputs twice. They cover traversal/grab/wall-jump, admission,
capsule/button/results, teleporter control/beam/hide-rise, active incoming arrival,
release and supported incoming states. The real full load retires33 source history
frames; pending fade/load/title assembly is observed without a raw restore across
it. Every main/forward/replay input draws normally (9,999 draws, zero skipped).

One affected whole-class request48106 passed **2T/0F/0E/0S**; its pure input
method does not depend on the traversal or ROM availability. Existing short
positioned/local cases and their oracles remain separate. Exact source/input hashes,
literal inverse, commands, two fresh JVMs, queue/case timings and limits are in the
[dated Plan](../../plans/2026-10-13-s3k-playable-completion.md#cnz2-cold-knuckles-to-released-icz-maintained-r6--2026-10-21).
Only this direct320/off/solo edge is added: full-act, other route/roster/donor/width,
checkpoint/respawn breadth, native clocks/PC/pixels and physical PLC/VRAM proof
remain inherited gaps. Root source/data review and composed qualification are separate.


### Root composed qualification 2026-10-21

After source/data review and the canonical-comparator/source-epoch wording
correction, Root head `7fbcfbd8a4597163390d5d813facec4c3e69a637` passed one
six-class ICZ/CNZ request **31T/0F/0E/0S**, including both maintained CNZ methods.
The21 complete60-key45-input/two-cycle windows, ordinary released arrival and
input inverse remain intact. This supersedes the pending composed-check note
above only for that bounded edge; full-act/breadth/native limits remain open.
Actual identities, environment, source fingerprint, timings, cleanup and broader
red smoke disposition are in the
[Root composition record](../../plans/2026-10-13-s3k-playable-completion.md#root-icz-cnz-composition-and-smoke-disposition-2026-10-21).

## Object-pipeline performance follow-up (2026-10-22)

The shared CNZ fan execution lookup uses adjacent owned SST queries with stable duplicate-occupant filtering. It retains layout/overlap and pending-deletion semantics; the shared fan checks apply independently of act loading.

The [query/allocation audit](../../audits/performance/2026-10-22-native-object-query-performance.md)
and its companion implementation records own exact tested states, commands and
remaining delivery checks. These local checks do not close inherited viewport,
donor/team, full-route, load/respawn, audiovisual or whole-world rewind products.


## Admitted live-history fixture receiving check — 2026-10-23

The current pending title-card entry intentionally has no live rewind controller.
The maintained test now observes the new floor when actual ICZ level play is
admitted, retaining the nonempty outgoing-history retirement/zero-earliest-floor
assertions before switching the optional recording request off. Production,
recording, event rows and all21 replay spots are unchanged.

Receivingd7849bf4d plus this test-only patch passes the whole maintained class2
and22 owning live-rewind controls (24T/0F/0E/0S). All63 current registry keys,
21 two-cycle45-input windows,7164 main inputs,9999 draws/zero skips and the exact
6870/6987/7050 load/assembly/release rows are preserved. Earlier60-key qualification
above remains its historical source epoch. See the
[exact command, source fingerprint and limits](../../plans/2026-10-22-native-gap-swarm.md#cnz2-cold-handoff-observes-the-admitted-live-history-floor--2026-10-23).
This does not qualify the other CNZ/ICZ routes, native timing, breadth or full acts.
