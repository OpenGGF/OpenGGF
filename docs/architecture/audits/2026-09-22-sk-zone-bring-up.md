# S&K zone bring-up reconciliation

`$PROJECT_ROOT` denotes the absolute main checkout; `$VIDEO_ROOT` denotes the
external OGGF video archive. Commands used absolute resolved ROM paths.

## Scope and baseline

Resume MHZ, FBZ, SOZ, LRZ, SSZ, DEZ and DDZ against their original plans and the
current level-test standard. Main workspace `develop` was fetched and fast-forward
checked at `c91fd5ac70aad2c6cd73dcfc3525d962a45ef4d5` (already current). Preserve
its dirty disassembly submodules and unrelated untracked files.

This is a campaign audit, not zone certification. Historical test/media results
below are inherited evidence until explicitly re-executed. Implementation,
cold reachability, rewind, native behaviour and visual matching remain separate.
Existing ending/credits and route exclusions remain as documented in each plan.

## Current campaign priorities (updated 2026-09-24)

The target remains all seven zones. The initial inventory below is historical;
its original unimplemented counts must not be read as current production status.

| Zone | Current evidence and next obligation |
| --- | --- |
| MHZ | Integrated Sonic/Tails route and miniboss fixes; accepted Knuckles/trace exclusions reconciled in the new per-act matrices; fresh320Sonic Act1 completion now includes six live replay spots and a corrected one-owner MHZ2 handoff; Act2 cold320 Sonic completion now reaches playable FBZ1 without seeds/deaths; eight full-registry rewind spots and ship-body presentation checks now pass; lifecycle/breadth remain open. |
| FBZ | Cold native Act 1 and thirteen Act 2 completion rows already recorded; finish matrix reconciliation, checkpoint geometry and presentation obligations without undoing the accepted S1 elevator challenge. |
| SOZ | Both acts, golem, end boss and playable exits implemented; cold and positioned route evidence exists. Reconcile later production/rewind checks with the remaining roster/lifecycle rows and explicit trace deferrals. |
| LRZ | Turbines, chained platforms, cutscenes, boss act, end boss and HPZ handoff implemented in this campaign. A 12820-frame fresh boss-act route reaches HPZ. Act 1 miniboss runtime art and lava-arrival priority are corrected; retain distinct cold-route, strict-parity and breadth gaps. |
| SSZ | Act-1 bosses, collapse, results and DEZ launch implemented; arrival Death Egg palette/RNG/cloud/mask/missile owners are now implemented and tested. Cold320 Sonic+Tails now defeats all three bosses and loads DEZ1 in19,492controller frames, zero deaths;37full-registry replay spots and the live SSZ→DEZ timeline reset pass. Knuckles cold320/800 routes now complete crane, both fights and the accepted pre-ending stop, including disk clear state and low-health/final-defeat replay. HPZ-pad incoming continuity and seeded island mask/redraw/scroll/palette tails have focused checks. Native whole-fight comparison, Act-1 route breadth and remaining lifecycle cases remain open. |
| DEZ | DEZ1 cold native Sonic+Tails now clears the turbine, both eight-hit miniboss phases and actual Act2 load in14,231frames, zero deaths. The two shorter routes retain40 replay spots; the complete route adds22 late spots and real load-boundary isolation. DEZ2 cold320 Sonic+Tails now reaches the upper corridor beyond both transporters in19,810frames, zero deaths, with45 Act2 full-registry replay spots across three preserved routes; complete Act2 traversal is still open. Both main acts have concrete placed-object factories and controller-driven boss/exit evidence. Direct `$1700` routes now complete hands/core/ship and load DDZ at 320/800; wide retained scenery, zero-X-carry floor and exit camera projection have focused regressions. Capture115 shows the corrected 800px handoff. Positioned incoming DEZ2-to-full-final continuity now passes at320/800 with eight full-registry replay spots each. Full-phase native parity and roster/lifecycle breadth remain open. |
| DDZ | Both boss phases and exit request implemented; seeded Hyper parity and fresh320/800Super completion now pass; strict-bootstrap remains separate. The real final-DEZ incoming load now reaches initial wide flight with correct camera projection; positioned DEZ2-to-DDZ completion now passes at320/800 with11full-registry replay spots each; cold main-act traversal and remaining Hyper/HUD/native presentation still need validation. |

Combined campaign validation, main-workspace integration, push and cleanup remain
pending. Local component commits are checkpoints, not completion or delivery.

## Initial reconciled inventory (2026-09-22)

| Zone | Actual implementation location | Evidence and remaining work |
| --- | --- | --- |
| MHZ | Integrated in develop; [completion audit](../research/s3k-zones/mhz-completion-audit.md) | Sonic/Tails implementation reviewed, followed by miniboss lifetime/defeat/transition fixes. Original audit explicitly excluded further Knuckles work and stopped traces. Per-act matrices now separate accepted scope and historical evidence from current gaps; route, lifecycle, visual and breadth validation still needed. Historical broad failures were not attributed to baseline and must not become a green claim. |
| FBZ | Integrated; [outstanding actions](../research/s3k-zones/fbz-outstanding-actions.md), [Act 1](../validation/levels/s3k-fbz-act1.md), [Act 2](../validation/levels/s3k-fbz-act2.md) | Cold native Act 1 completion and 13 Act 2 completion rows are recorded; bounded reload/transition/rewind and native afterstates exist. Native checkpoint geometry, strict parity, SAT presentation and remaining matrix obligations stay open. Preserve the explicitly accepted S1 elevator challenge. |
| SOZ | Integrated; [plan and evidence](../plans/2026-09-15-soz-methodology-v2.md) | Both acts, bosses and exits implemented; cold-route, recording and native pixel follow-ups greatly exceed the early plan summaries. Full supported route products, remaining lifecycle/participant obligations and native presentation certification need reconciliation against the two act matrices. Trace deferrals are not silently revoked by this audit. |
| LRZ | Local `feature/ai-lrz-bring-up` at `c708e1a2b` | Act 1 census is zero placeholders; miniboss defeat, seamless act change, post-defeat palette/camera releases and Act 2 background stages exist. Act 2 has 23 placeholder placements; boss act has 8. Latest commit adds spike-ball launchers **and an unregistered turbine draft**, absent from the status summary. Remaining turbine/chained platforms, cutscenes, boss act, exits, palette rotation, breadth, rewind, cold completion and native comparisons are substantive work. |
| SSZ | Local `feature/ai-ssz-bring-up` at `55a999030` | Arrival, bridge, traversal families and Act 1 bosses exist. Latest commit adds Mecha Sonic defeat/handover, superseding the media index's claim that defeat is unimplemented. Collapse/launch completion and Knuckles Act 2 still need work. Cold route only reaches the early arrival area; most mechanisms/fights use declared checkpoint setups. Native and wide/rewind acceptance remain incomplete. |
| DEZ | Local `feature/ai-s3k-dez-bring-up` at `b65b5966a` | Reverse gravity plus gravity mechanisms and selected traversal/badnik families implemented. Summary contradicts itself (93 covered reference rows above a stale 34-row table). Seeded Act 2 free-play prefix is 1256 frames, not a cold completion. Act 1, remaining objects, bosses, final act, transitions, missing gravity consumers and acceptance remain open. Latest capture-startup correction must be reviewed alongside other campaigns' tool edits. |
| DDZ | Integrated; [plan](../plans/2026-09-17-ddz-bring-up.md), [matrix](../validation/levels/s3k-ddz.md) | Controller, both boss phases, wrap, death and exit request implemented. Recorded completion declares inherited V-int and camera-fraction seeds; unseeded entry and strict replay bootstrap remain different claims. Existing visual fixes supersede some initial observations; the known-bugs entry still lists stars/HUD/driver issues and needs checking against code. Ending/credits remain outside the original stop line. |

None of the three local campaigns has been merged into develop. Their clean trees
were inspected without mutation. Continuation uses `feature/ai-sk-zone-completion`
in `.worktrees/ai-sk-zone-completion`, created from LRZ with copy-on-write and
merged with the current develop base (`715b46176`, no conflicts). This preserves
the delegated checkouts and keeps incomplete campaigns off the integrated branch.

## Media and historical scratch

Existing archives are under `$VIDEO_ROOT/`: `lrz-bring-up`,
`ssz-bring-up`, `s3k-dez-bring-up`, `ddz-bring-up`, and the HPZ precedent.
Their `INDEX.md`, `notes/`, numbered clips, preserved raw captures and input logs
record positioned versus cold entry. LRZ has a 129.95-second, 18-chapter reel at
`lrz-bring-up/reel/lrz-bring-up-highlights.mp4`. Continue those archives with new
versioned sources; do not overwrite historical footage or treat a reel as a cold run.

The latest LRZ notes correct two misleading blockers: Act 2 lava at camera Y
`$710` is native, and a spindash mismatch was a probe setup artefact. Preserve the
fight palette through the seamless load until `loc_78B08` replaces it at camera
X `$2C0`. SSZ positioned captures require `--star-post` because cold screen init
otherwise overrides the requested position. These are existing findings, not
new diagnoses.

## Current validation and next work

- Preflight initially rejected default Lua 5.5.1. Explicit
  `LUA_BIN=/usr/bin/lua5.4` passes Java/Lua/PowerShell prerequisites in the new
  worktree. No prerequisite bypass or engine change was needed.
- At `715b46176`, queued Maven `-Dmse=off
  -Dtest=TestLrz*,TestS3kLrz*,SwScrlLrzTest,TestFirewormBadnikInstance,TestIwamodokiBadnikInstance,TestToxomisterBadnikInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
  -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen test` completed
  **383 tests, zero failures/errors/skips** on 2026-09-22. Focused validation only.
- Turbine slice now registers both ROM mappings and corrects `sub_44338` to use
  the logical **press** byte. Six focused cases and 17 real-level width/donor/character
  cases passed (23 tests, zero failures/errors/skips, 2026-09-22). The latter cover
  selectable widths 320/352/400/528/800, native/S1/S2 Sonic, and native Tails/Knuckles,
  including whole-world capture/restore/forward replay. Act 2 census is now five
  placeholders: three chained-platform placements, the cutscene and exit.
- Four new positioned turbine captures declare setup/input/width/donor in their
  external provenance files. Clip 37 includes falling into the band, riding and
  releasing; the other three show native/wide/S1 midride starts. State CSV and
  selected PNGs were inspected. Native trajectory/pixel comparison remains open.
- Continue LRZ playable Act 2 and boss-act obligations, then reconcile SSZ and
  DEZ shared changes before integration. Run combined change-based validation
  against the pinned develop SHA, including required domain gates; attribute
  disputed failures narrowly. Do not certify the seven zones from census or
  unit-test counts.

## Rewind findings from the continuation

At LRZ tip `c708e1a2b` and merged baseline `715b46176`, matched queued runs of
`-Pguards -Dtest=TestSonic3kObjectProfileRegistryGuard,TestRewindArchitectureGuard,TestRemainingRewindTailInventory`
confirmed inherited launcher sidecar triage gaps and eight missing restore
constructors. The stale inventory was 1109 classes; actual LRZ added 47. The
initial sweep passed 913, classified 235 as graph-covered, and could not construct
eight. These were not waived by raising the inventory.

The new `TestS3kLrzBossRewindHeadless` first reproduced a missing dynamic reference
while restoring both unfolded arms. Restoration constructors now supply the real
boss parent for its five child classes; three independent LRZ children also gain
normal spawn constructors. The launcher uses the central managed-reference policy
for its ball instead of a custom transient sidecar. A real placed launcher test
removes its in-flight ball, restores it, checks the managed identity and compares
whole-world replay. The focused launcher/miniboss group passed 32 tests with no
failures/errors/skips before extending the boss test further.

Rejected repair: the miniboss's old restore hook synthesized all missing arm
pieces. A new regression killed one hand, waited for its arm to retire, and called
the hook: **12 remaining pieces became 24**. Removed synthesis; the captured
manager/parent graph is authoritative. The old manual-delete/count-only test was
removed because it asserted that incorrect repair rather than snapshot semantics.
`sub_78B46` is the ROM owner of arm retirement.

A second regression at hit-flash creation exposed stale `AbstractBossChild`
`dynamicSpawn` coordinates. Rebuilding only after restore was insufficient: fresh
children could still capture their pre-subclass-constructor `(0,0)` position.
The LRZ effect constructors now publish their initial position; the shared
restore hook refreshes the derived position/ordinal cache after scalar restore. Whole-world tests explicitly remove/recreate all live boss children
at unfolded-arm, hit-flash and defeat-debris phases, then compare restore and next
frame across every registered subsystem. Both tests passed on 2026-09-22 at 15:10
BST. Touches in this test call the production attack entry point only in eligible
phases; this is graph validation, not a controller-driven fight completion.

The shared cache correction requires combined broad validation before integration.
No full-suite success or seven-zone completion is claimed here.

The alternative of refreshing every `getSpawn()` was tested and rejected: it
changed isolated HTZ child-probe behavior (Flamethrower and LavaBall moved from
passed to parent-dependent) without being necessary for the LRZ correction. Keep
the fix at explicit construction and restore boundaries. The final focused queued
`-Pguards -Dtest=TestRemainingRewindTailInventory,TestRewindArchitectureGuard,TestRewindHarnessCoverageRatchet,TestGraphCoveredIsolatedProbeClassification,TestSonic3kObjectProfileRegistryGuard,TestS3kLrzBossRewindHeadless`
with the absolute S3K ROM completed **38 tests, zero failures/errors/skips** at
15:13 BST. The actual inventory is now 1156 = 916 isolated passes + 240 graph-covered,
with all failure buckets empty. This is focused evidence, not a broad suite pass.

The focused shared-boss regression selection
`-Dtest=TestS1*Boss*GraphRewind,TestS2*Boss*GraphRewind,TestS2DeathEggRobotGraphRewind,TestS2MechaSonicGraphRewind,TestS3k*Boss*GraphRewind,TestS3kLrzBossRewindHeadless,TestS3kLrzLauncherRewindHeadless`
with all three absolute ROM paths passed **77 tests, zero failures/errors/skips**
at 15:14 BST. Combined change-based selection remains required before integration.

## Chained platforms and native traversal corroboration

Continuation after `87f0bf87b` implements `$25` from `Obj_LRZChainedPlatforms`
(`$4A644`): negative subtype expands the ROM's 4/4/8 groups, all ten-waypoint paths
come from ROM, and `sub_4A818` preserves DIVS signed remainder in the minor-axis
fraction. `SolidObjectFull` high d6 bits 18/19 select underside damage; the top
carries its rider safely. The actual groups at `(15C0,A90)`, `(19C0,590)` and
`(1D40,890)` restore independent phases without duplication. Focused tests:

- `TestLrzChainedPlatforms,TestS3kLrzChainedPlatformsHeadless,TestS3kLrzPlacementCensus`:
  11 tests, no failures/errors/skips before expanding contact breadth.
- `TestLrzChainedPlatforms,TestS3kLrzChainedPlatformsHeadless,TestRemainingRewindTailInventory`:
  24 tests, no failures/errors/skips after covering widths 320/352/400/528/800 ×
  native/S1/S2 Sonic and native Tails/Knuckles. All commands use the queued Maven
  wrapper and absolute S3K ROM property. Inventory now 1157/917/240 with empty tails.
- Structural rewind coverage/architecture and profile registration pass; the initial
  inventory-only failure was the new isolated-passing class, then updated explicitly.

Read-only native replay (`native-traversal-20260922/run1`, external LRZ archive)
resumes the original BK2's frame-415400 save and observes frames 418100–422900,
without memory writes. The verified ROM SHA-1 is
`CFBF98C36C776677290A872547AC47C53D2761D6`; BK2 SHA-256
`AD40FB0B0A74FA12B08AB71B2E48A7455B388D14F43F4CDED502AC4A15D1B3C0`.
The common native host completed with no logged failures in 9.527 seconds.
9593 object rows include 1734 chained-platform samples and 134 captured-player
turbine samples. All 134 turbine Y offsets agree with `byte_44572`; releases at
movie frames 419598/419717/419831 yield -1944/-3072/-3012. Three new unit cases
exercise those phases through ordinary capture/rotation, not state hydration;
`TestLrzTurbineSprites` passes nine cases with zero skips/failures/errors.
Native chain roots at frames 422436 and 422859 begin with Y fraction `$73F8`,
matching the independent ROM-table path tests. Native images f419600/f422620 were
inspected. This is behavior corroboration; matched trajectory/pixel certification
still needs matched entry and observation boundaries.

Promoted the reusable native observer to
[`capture_lrz_traversal_reference.lua`](../../../tools/bizhawk/capture_lrz_traversal_reference.lua)
with explicit plan fields. `luac5.4 -p` passed. Use the common host, original movie
and its own save with `--require-output objects.csv --require-output done.txt`;
external `host.json` records the full command and hashes. No observation feeds
engine gameplay.

Clip 38 (`raw-57-chain-80-320-20260922`) is 480 neutral frames from declared
`(15C0,A50)`, 99 rings, native Sonic. State CSV shows a down-and-up ride with zero
hurt/dead frames; frame 120 and the full MP4 decode were checked (8s, 960×672,60fps).
The preceding `(19C0,550)` filming attempt encountered its nearby flame thrower and
was not selected for the showcase. Both are positioned starts, not cold completion.
Act 2 is now down to two placeholder placements: the boulder cutscene and exit.

## LRZ Knuckles exit

After `ab873c917`, `$B3` follows `Obj_StartNewLevel` ($863EC), leaving `$AE` as
Act 2's only placeholder placement. The focused queued command
`-Dtest=TestS3kStartNewLevel,TestS3kLrzPlacementCensus,TestS3kLrzExitHeadless`
with the absolute S3K ROM passed **20 tests, zero failures/errors/skips** at
15:37 BST. The real exit loads HPZ for all three native characters; save gating
is tested separately. Rewind coverage and profile guards passed. Inventory-only
ratchet failure (new passing class) was reconciled to 1158/918/240 and rerun:
one test passed at 15:40 BST, no tails.

External clip 39 and raw-58 record Knuckles entering HPZ from declared `(3FC8,D0)`;
360 frames, zero hurt/dead frames, frame 120 inspected and complete MP4 decoded.
This remains positioned evidence, not cold-route completion.

## LRZ2 boulder and fresh-load continuation

Continuation after `5b86353e7` supplies `$AE`, the subtype `$24` Knuckles actor
and the pushed boulder. The ROM owns the approach/camera/control stages,
`Animate_Raw2MultiDelay`, the 21-update rolling section, `$20` light gravity,
`sub_65EFE` rider capture/rotation and the `$1600` request. Native P1/P2 ordering
is preserved; extra engine participants use the existing extended ordered roster.
`sub_8622C` was extracted unchanged from DDZ into a shared native angle helper.

`LevelContinuationCarry` owns the requested/loading/loaded bank inside the
existing transition coordinator and its rewind adapter. It preserves selected
rings/timer/shield values across a matching fresh load, bypasses the entire title
owner/locked loop, and consumes counters on the destination screen event.
Replacement requests, mismatched/failed loads, second direct loads and reset
clear the bank. No public Mod API descriptor/pin or GameLoop body change.
The recording driver's deferred-player boundary now exists only when a title
was actually requested. LRZ masks the saved shield to native elemental types.

Evidence (all Maven commands queued, absolute S3K ROM):

- `TestLevelContinuationCarry,TestLevelTransitionCoordinator,TestLevelTransitionCoordinatorPeeks`:
  15 passed. First compilation exposed a misspelled zone constant; fixed before execution.
- `TestS3kLevelContinuationHeadless,TestLevelContinuationCarry`: 10 passed, including
  all elemental shields, title-art absence, consumed banks and later direct entry.
- `TestS3kLrzBoulderCutsceneHeadless,TestRemainingRewindTailInventory,TestS3kDdzFlightControllerHeadless,TestS3kLrzPlacementCensus`:
  24 passed, zero skips/failures/errors at 16:10 BST. The 17 boulder cases cover
  widths 320/352/400/528/800, native Sonic+Tails, S1 Sonic, S2 Sonic+Tails and native
  solo Sonic/Tails, including remove/recreate/restore and forward replay.
- Rewind coverage/profile guards passed. Inventory is 1161 = 921 isolated passes
  + 240 graph-covered, empty failure tails (ratchet updated from the observed new classes).
- Expanded boulder tests add Knuckles exclusion and the actual controller-to-load
  bank/shield handoff. Combined LRZ/required S3K/DDZ selection completed 461 tests:
  457 pass, four inherited trace failures, zero errors/skips. Exact command and
  failures are in the [frontier log](../../status/trace-frontier-log.md).
  Their counts and first-error identities match the September 19 record.

The initial scripted approach held jump while airborne and stopped at a stair;
repeated ordinary jump input completed it. No physics change was used to make the
route pass. Recreation then exposed a replaceable `SubpixelMotion.State` holder:
its constructor position survived restore although scalar snapshots matched.
Making the holder final enables the existing in-place codec; the complete world
forward comparison now passes. Recorded in implementation pitfalls.

Native `native-boulder-20260922/run2` (same verified original movie and save as the
traversal observer) completed without host failures in 16.491 seconds. Its promoted
read-only exporter records 901 consecutive rows, frames 428200–429100: boulder
creation 428255, camera flags 1/3 at 428373/428385, push 428426, airborne 428447,
first rider capture 428475, `$1600` request 428800, receiving player `(64,112)` with
233 rings and saved timer at 428823. The relative first-49-move boulder trajectory
is checked directly against ROM arithmetic. Screenshots and state are observations,
never engine input. `luac5.4 -p` passed. Native entry-loop timing and pixel parity
are not certified by these positioned tests.

External clip 40 uses raw-59, declared `(3880,1C0)`, Sonic+Tails, 37 rings and
repeated right/jump input. The first 720 frames have no hurt/death; destination
loads at 693 and its first screen event restores rings. Frames 320/400 inspected,
full clip decoded (12 seconds). Raw footage continues into the unfinished boss act.
Act 2 placement census is now zero placeholders; LRZ3 retains eight.

The final art guard found an earlier turbine-registration defect from `87f0bf87b`:
`Map_445A6` and `Map_445B0` have adjacent pointer tables sharing later frame data.
The first pointer is not the frame count. Explicit counts 5 and 4 prevent reading
the second table/data as more frames. Queued
`-Dtest=TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard,TestLrzTurbineSprites,TestS3kLrzTurbineHeadless`
with the absolute S3K ROM passed 29 tests, zero failures/errors/skips, at 16:17 BST.

## LRZ3 encounter oracle under construction

The next slice starts from `59baeab3c`. Native source ownership resolved before
boss implementation:

- `sub_79F58` consumes boss status bit 6, decrements fourteen-hit health, and
  keeps that bit set for a 32-dispatch flash. The publisher is the floating
  mine's `loc_79EC4` range test against its parent (`[-$30,$30)` in each axis).
  The boss touch byte is `$B8` (hurt), not a normal player-attack health target.
  The mine touch byte is `$9A`. Negative player-attack health/shield tests are owed.
- Initial `ChildObjDat_7A18C` has two children: body overlay and cockpit. Each
  of three launch callbacks per surfaced cycle invokes `7A19A`, one mine plus
  one launch effect. Every airborne mine emits one `7A1A8` particle on
  `V_int_run_count & 3 == 0`. Mine hit/parent death emits one boss explosion.
  Direct counts are resolved; timed peak live counts still need the native
  observer and the complete graph test, not a guessed constant.
- All those tables use `CreateChild1_Normal`: forward search after the owner,
  sequential even subtypes, first failure terminates the suffix without rollback
  or retry. `CreateChild6_Simple` uses the same search/failure policy with one
  repeated code pointer: platform child 1, underside child 1, debris children 10.
  A successful later-slot child can run in the same sweep. Independent tables
  still execute after a prior table failed.
- Platform generator subtype 0 creates one lowered platform with timer `$37F`
  then periodically one at `$17F` intervals with timer `$4FF`; subtypes 1/2
  create one and delete. A selected subtype-0 platform publishes `_unkFA88` bit 0
  when it reaches Y `$612`; that is the boss's entry gate before its 120 wait.
  The boss allocates a separate platform stream at every surfaced entry. Its
  `$44` link to the last platform is a graph edge and must survive recreation.
- Killing damage publishes `Events_fg_5`, clears slope direction/acceleration,
  stops the timer and begins the 128-dispatch sinking wait. `loc_79998` then
  allocates the capsule and palette-restoration controller independently and
  raises `_unkFAA8`. The capsule's `sub_865DE` publishes `_unkFACD`; only the
  results owner's `loc_2DCF8` clears `_unkFAA8`. The boss observes that clear,
  releases both native players, starts gradual max-X `$EC0` and allocates
  `Obj_StartNewLevel $2D` at `($FE8,$5E0)`. A boss-only test cannot certify this chain.
- `loc_59F3C` preserves camera delta `d2` with `movem.w` between P1 and P2.
  Consequently P2's left-edge ground-velocity write uses the sign-extended low
  word, whereas P1 uses the original long. Keep this shipped behavior. Wider
  viewports retain the native 32-pixel right margin; route thresholds and P1-only
  release authority remain fixed. Extended sidekicks follow native P2 semantics.

Remaining oracle work includes exact live peaks, standalone allocator failure
behavior for platform streams, all transient parent checks, and native evidence
for the full completion chain. No boss implementation or certification is claimed.

Native boss observer `native-boss-20260922/run2` records 20201 consecutive frames
428800–449000 from the verified original movie/save. Run 1 had two wrong diagnostic
addresses (foreground words and object routine); corrected from symbols and rerun.
No gameplay RAM was written. `luac5.4 -p` passed. The recording includes the movie's
bonus-stage detour and checkpoint re-entry, not a single uninterrupted cold route.
All 1635 nonzero camera deltas while special-event `$14` remains active match the
ROM fixed-point velocity/stage oracle. No unchanged emulator frame was counted as
a gameplay dispatch.

Native boss gate: platform bit 0 at 435416; root switches to active code at 435538,
initializes 14 health at 435539. Damage at 435948/436046/436133, then groups of three
per cycle; killing damage at 439863. Sinking ends and capsule/palette controller
allocate at 439991. Capsule publishes completion at 440230; results clears its
waiting flag at 441128; boss observes it at 441129; HPZ request at 441628.
The observed boss-mapping peak is 13 at 439747: root, two body/cockpit children,
three mines, one launch effect and six mine particles. This is a movie observation,
not an allocation-failure or universal peak guarantee. Screenshot 437200 inspected.

Camera/screen validation: 39 focused checks at 16:30 and 18 expanded world checks
at 16:34, all passed without skips. Checkpoint tests use the fresh-load lifecycle
to avoid the fixture's optional terrain snap; foreground rewind uses whole world
steps rather than capturing never-dispatched sprites. The initial two fixture
failures were resolved in test setup, not by changing native motion.

Queued palette ownership integration: 3 passed at 16:37. Queued
`-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestS3kZoneEventPaletteOwnership,TestZoneRuntimeRegistryRewindSnapshot`
passed 10 at 16:38, zero skips/errors/failures. These are focused checks; combined
campaign validation and integration remain pending.


### Boss-act presentation and lava surface (September 22, continuing)

Parent `c5c002865`, worktree `.worktrees/ai-sk-zone-completion`.
`SwScrlLrz3` now interprets the distant/linear background coordinates, the ROM
`word_5077E` heat shimmer and the retained 192-byte lava height table. Stage `$C`
produces per-column vertical scroll. Wider views extend the native table by
holding its outer samples. The background event stage machine and its boss
allocation are still pending; isolated rendering checks do not establish reachability.

The boss act's direct animated channel uses tile `$170` and all 48 ROM frames;
its null AniPLC entry must still load that direct channel's raw art. The first
54-test run exposed the constructor's early-return omission (53 passed, one
pixel oracle failure). Loading direct art before that return fixed it; the
focused rerun of `TestS3kLrzPatternAnimation,TestS3kLrzBossPaletteCycling,SwScrlLrz3Test,TestS3kLrzBossCameraHeadless`
passed 34 tests at 16:48 BST, zero failures/errors/skips. Palette clocks reproduce
`AnPal_LRZ3` modes 0, `$80`, and 1, including frozen counters and rewind.

The read-only native observer's optional deformation output records 581
consecutive frames 435600–436180 in `native-boss-20260922/run3` (23.802 seconds
host time). Independent comparisons matched all 581 height tables, all twenty
VScroll columns and all 224 foreground/background horizontal-scroll lines on
every sampled frame. The recording uses the same original movie and verified
state as run 2; no RAM writes or engine hydration. The exporter SHA-256 is
`cf8de2f6f8935ecd43a553642562e679d9fa534588c195e7eae71e5dc7d6391c`.
Both slope directions and all amplitudes 0–128 also have independent fixed-point
arithmetic tests. This is a bounded presentation oracle, not a strict route pass.

`Obj_59FC4` is allocated once before initial placements, as in BackgroundInit.
It owns slope amplitude/table updates and the 16:16 current. Direction may change
in a later boss slot, so current and table retain the lava slot's publication until
its next dispatch. The tilted collision helper uses signed bytes; visual scroll
and platform-height readers use unsigned bytes. Native P1 alone receives the
fire-shield burn exemption. Capsule/end flags suppress burns without suppressing
current. The focused arithmetic/camera/palette/scroll run passed 23 tests at
16:57 BST, zero failures/errors/skips. Actual landing and graph validation follows.

Capture `raw-60-lrz3-camera-presentation` shows the checkpoint camera and updated
presentation, but reaches death because the placed platforms remain unfinished.
It is development evidence only, not a completed route or a highlights chapter.
Media delivery uses file links at the user's request because inline playback
fails in their Codex GUI.

The real solid/contact test passed at 16:59 BST; its strengthened graph-removal,
recreation and whole-world forward replay passed at 17:00 BST (one test each,
zero failures/errors/skips). It establishes a real tilted landing, native P1/P2
fire-shield asymmetry, a negative `$300` fractional current, and current continuing
after capsule completion. Two test accessor typos were corrected before those runs.

Queued `-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestS3kZoneEventPaletteOwnership,TestZoneRuntimeRegistryRewindSnapshot`
passed 10 at 17:01 BST, zero failures/errors/skips. Lua syntax and diff whitespace
checks passed. Combined campaign validation and integration remain pending.


### Boss-act platform approach and user-reported horizon masking

Parent `7542bd6b1`, worktree `.worktrees/ai-sk-zone-completion`.
`Obj_LRZ3Platform` now generates the two initial forward children, reverses after
32 upward movements, and creates the animated underside on the reversal dispatch.
Generator recurrence is 384 dispatches. Partial allocation retains each successful
prefix without retry. Stationary subtype 4 uses the shipped `FixBugs=0` palette.
Entry-platform, stream movement and ten-fragment breakup paths are present;
real boss allocation and encounter validation remain open. The rising branch's
residual-D0 entry selection still needs an explicit solid-return model; normal
checkpoint footage does not exercise it.

Initial capture exposed a registry placement error: platform art was added under
the HPZ cutscene group. Moving it to the boss-act group restored the ROM artwork.
Whole-world removed-graph restore exposed the generic schema excluding the
underside's `parent` field; an explicit captured-link policy fixes reconstruction.
Four allocation tests and twelve headless graph/breakup tests passed at 17:23 BST.
The capacity-0..10 breakup tests use a passive range target, not a completed boss.

The user identified missing horizon occlusion in clip 41. Platforms already changed
SAT bucket 5 to 3, but LRZ never enabled SAT masking. Boss-act `$8B/$44` placements
at `($AA0,$3B2)` and `($BE0,$3B2)` occupy bucket 4, masking the rising phase below
Y `$3A2`. Enabling the post-pass restores that transition. The shared placed-mask
object also hardcoded frame 4; it now decodes its high-nibble-selected mapping
from ROM `Map_SpriteMask` `$18595E`, preserving other LRZ mask heights. SOZ remains
covered. Queued `TestLrzBossPlatforms,TestS3kLrzBossPlatformsHeadless,TestS3kSpriteMaskSupport,TestSozSpriteMaskPresentation`
passed 24 tests at 17:32 BST, zero failures/errors/skips.

Native checkpoint comparison found landing one frame early: `sub_7A064` passes
`d2=$10,d3=$0D` to `SolidObjectTop`; `loc_1E44C` uses d3, not d2. Correcting the
contract restored the input movie's traversal. Raw 63, input offset 434068,
contains 900 frames with no hurt/death. Against native run 2 frames 434069–434968,
all X, X/Y speed, ground speed and camera values match; Y matches after the first
positioned-load frame (engine 876 versus native 872 on that first frame). These
are observations, not inputs to the engine. Clip 42 contains all 900 frames and
supersedes clip 41. Complete decoding of the MP4 succeeded. This is bounded native
checkpoint evidence, not cold-route or full encounter certification.


The same `SolidObjectTop` d3 correction applies to flat `Obj_59FC4` lava.
Both callers now use the existing direct-top contract for overlap 1..16,
rejecting zero and 17 and preserving relative landing snap. The boundary fixture
initially skipped all new contacts because newly allocated objects had not taken
their first pre-update snapshot; the diagnostic exposed `skipSolid=true`.
Advancing that lifecycle boundary fixed the test setup. The direct-window method
belongs to the existing optional-slope contract: the flat platform supplies no
slope table. Queued `TestS3kLrzBossPlatformsHeadless,TestS3kLrzBossCameraHeadless,TestLrzBossLavaSurface`
passed 37 at 17:42 BST with no failures/errors/skips. Queued `-Pguards`
`TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestObjectPriorityBucketGuard,TestSonic3kObjectProfileRegistryGuard,TestObjectServicesMigrationGuard`
passed 18 at 17:42 BST, no failures/errors/skips. Campaign-wide validation,
integration and push are still pending.

### End-boss implementation in progress

Parent `3fbb61e7c`, same campaign tree. `LRZ3_BackgroundEvent` now owns the
single arena-allocation attempt; the candidate boss models the 14-hit scripted
mine publisher, persistent crest/pilot, launch plume/trail, first-free platform
stream, sinking/explosion phase, capsule, palette restoration and results wait.
This is not yet a delivered or certified encounter. Capsule child-slot fidelity,
all allocation-failure boundaries, completion replay and inherited cold entry
remain open.

Raw 64 revealed the new unpositioned root was being removed by default placement
range unloading after its first update. The native waiting routine has no such
tail; explicit routine-owned lifetimes fix it. Raw 65 then reached all 14 mine
hits and defeat. A second implementation defect was truncating a 24-bit child
routine address through the spawn subtype byte. Stable byte identities now
recreate the five roles explicitly. The removed-parent/child restore and forward
replay test initially found the inert zero-spawn schema probe needed a legal
constructor; the corrected focused platform/graph class passed 19 tests at
18:10 BST, no failures/errors/skips. Earlier background/camera/lifetime selection
passed 40 at 18:06 BST.

Native run 2 has an unchanged player/platform frame at emulator frame 435183;
the positioned engine run advances there (capture frame 1114). No frame-index
special case was added. From body initialization through the last pre-death boss
row, 4584 engine rows match native root X, Y, routine, health, flash and wait timer
with that **explicit one-frame diagnostic offset**. This is state-machine evidence,
not strict replay parity or permission to drive gameplay from the comparison.
The corresponding native input can diverge spatially after the admission difference.
Raw 65 first hurts P1 at frame 2777 and dies at 5903, before results. Adding a
fire shield at initial setup does not resolve it: the mine hit consumes it.

Clip 43 contains raw 65 frames 1450–2649, 20 seconds at original speed, silent,
showing emergence and the first three scripted hits. All 1200 frames decode;
no damage/death occurs in that excerpt. Full raw capture and provenance retain
the later failed route instead of presenting the excerpt as completion evidence.


The authored controller route now reaches the real capsule/results release and
playable `$1601`. Raw 67 has 7300 frames, zero hurt/death frames, and loads Hidden
Palace at frame 7119. Setup is explicitly a fresh positioned checkpoint, native
Sonic + Tails, 320px, 37 initial rings and a fire shield. The first 1450 inputs
come from the original BK2; subsequent inputs were authored against the live
mine/capsule geometry. No gameplay values are supplied from native observations.
Clip 44 edits frames `[1450,2100)` and `[5700,7300)` into 2250 frames / 37.5 s;
complete MP4 decoding passed. The destination's unusual entrance artwork prompted
an independent native capture: frame 441900 is `$1601`, Y `$AEC`, with the same
ROM art. This ruled out a suspected wrong destination. This remains checkpoint
completion evidence, not cold entry or strict parity.

Rejected capture raw 66 used an input-log header without `#P1`/`#P2` group
markers, silently shifting decoded controls. All 5670 authored rows of corrected
raw 67 round-trip to their intended button masks. Its source input and provenance
are retained with the external capture; raw 66 is marked failed, not completion.

`TestLrzEndBossEncounterHeadless` exercises startup/launch allocation prefixes,
negative rolling/fire-shield attacks, real mine publication and delayed root
consumption, V-int-gated trails, grounded capsule eligibility, and allocation
failure/RNG ordering for three-burst explosions. Its real controller-driven fight
removes and recreates the encounter at peak graph size (13), defeat, capsule
opening and results, comparing all registered snapshots before and after one
forward frame. This exposed two rewind defects: the floating capsule omitted its
optional explosion controller, and power-up rebinding made the idle insta-shield
the last occupant of the equipped shield's shared fixed slot. The floating
capsule now captures its emitter like the upright capsule; rebinding registers
the idle ability before the equipped shield. The 37-test encounter/shield/player
rewind selection passed at 18:35 BST, no failures/errors/skips. The expanded
96-test encounter/platform/background and required AIZ/load/bootstrap/decoding
selection passed at 18:41 BST, no failures/errors/skips. All commands use the
queued Maven wrapper and the explicit verified S3K ROM path.

Still open: `$9E` cold-entry flash graph, capsule's collapsed child-slot graph and
separate P2 ending-pose boundary, platform stream slot-reuse pressure, native
palette-restore timing, wider/donor/team encounter products and the native
art-queue admission difference. Campaign integration and combined validation
remain pending.

The focused structural selection (`-Pguards`, field disposition, helper coverage,
zone-event schema and recreate-link tolerance) passed 21 checks at 18:43 BST.
The art/provider/capsule selection completed 121 tests without skips and found
one stale assertion: the Act 1 plan expected nine standalone entries, omitting
the already-registered Fireworm head alongside its segment sheet (six common
entries plus four badnik entries). The boss art is correctly confined to `$1600`;
its registration does not change Act 1. The corrected inventory asserts the head
explicitly, and a new test pins the boss ROM art/mappings/palette and excludes it
from Acts 1/2, playable HPZ and the sanctuary. The other 120 tests passed.

The corrected art registry selection passed 77 at 18:44 BST. Platform-stream
pressure now verifies failed-first-allocation retry, cleared-slot X=0, reused-slot
occupant X, and low-byte direction shutdown. The encounter class then passed
41 tests at 18:46 BST (no failures/errors/skips): 25 complete fights across five
widths (320/352/400/528/800) and five configurations (native Sonic + Tails,
native Sonic solo, native Tails solo, S1 Sonic solo, S2 Sonic + Tails), each with
all four full-world remove/restore/forward checks, plus 16 short boundaries.
These use declared arena/fire-shield setup and do not certify the cold approach.

`Run_PalRotationScript`'s global disable gate is now honored by the capsule
palette helper while its caller still writes `$7FFF`. The focused pause/resume
regression passed at 18:49 BST; two existing LRZ3 palette-cycle checks also
passed. An initial assertion incorrectly treated `palscriptdata 4` as stored
word 4: the disassembly macro emits `frames-1`, so the correct encoded delay is
3 and the next write is four enabled dispatches later. Runtime timing already
used the ROM word; only the test expectation needed correction. Native palette
row parity and capsule child-slot fidelity remain open.


## Cold boss-act flash/controller and bonus return (in progress)

On `68255ce2e`, `$9E Obj_LRZ3Autoscroll` now owns the cold-entry flash,
white/target-palette fades, eight released rockets, target reticles, descending
missiles, impact hitboxes, terrain edits and debris. Art/attribute/animation and
child tables are ROM-backed; allocation keeps the forward prefix and does not
heal failed children. The act census is now zero placeholders. This is
implementation inventory, not certification.

Clip 45 (`45-lrz3-flash-and-missiles.mp4`, external campaign directory) records
1500 fresh-entry frames with native Sonic + Tails, width 320, initial fire shield
and 37 rings. The first 203 frames agree with the native observer on P1 X/Y,
vertical speed, camera Y and controller/flash positions/timers/subtypes. The
native unchanged frame at 429070 then separates the ordinary capture from the
reference; hardware art-work admission and inherited V-int phase remain open.
Ten fresh-entry/width/removed-graph/allocation checks passed at 19:02 BST with no
failures/errors/skips; 23 structural checks passed at 18:58 BST.

Extending the route naturally entered Gumball, returned at engine frame 2302,
and died at 2467 because return initialization saw fresh P1 X=$40 before the
saved checkpoint was restored. `LRZ3_ScreenInit` consequently locked camera X=0.
The return coordinator now prepares checkpoint position before loading, and the
scoped bonus-return flag preserves it across the normal manual-load clear.
The first correction passed 18 focused checks at 19:09 BST. Its longer route
corrected camera X but exposed the later restore overwriting screen-owned Y
bounds; checkpoint camera limits are now applied by camera initialization before
ScreenInit, not after it. The post-load bonus handoff retains the resulting
position/camera and still restores interior rings, timer, shield and activation.
Reverification is pending for this expanded correction.

Source review also rejected a prior interpretation in `LRZ3_ScreenInit`:
`.offset = Stack_contents-(Target_palette_line_4+$20)` makes the two subsequent
writes land at `$FD10/$FD14`, not player coordinates. The earlier port and camera
fixture incorrectly encoded a teleport to `$9C0,$36C`. That teleport is removed;
checkpoint route fixtures now explicitly start at the real saved `$9C0,$368`.
Earlier clips 42–44 and positioned encounter results remain historical evidence
with that setup defect; the corrected candidate needs renewed route evidence.


The corrected cold route now reaches playable Hidden Palace at frame 12700;
all 12820 rendered frames have zero hurt/death. Clip 46 is a 3190-frame
(53.17-second) highlight; `raw-69-lrz3-cold-completion` keeps the uninterrupted
capture, exact input log and setup. It starts from the default boss-act entry
with native Sonic + Tails, width 320, declared fire shield and 37 rings. The
original movie's Gumball visit lasts longer than the engine run, so the authored
log re-aligns its post-bonus segment at the live return boundary, then uses
controller-driven mine avoidance/capsule/exit actions. This does not seed runtime
state or certify strict parity. The return now has P1 `$9C0,$368`, camera
`$920,$2F0`, matching the observed native entry state.

The expanded checkpoint/return selection initially completed 111 tests with one
failure: a short lava rewind spot had the opposite fixed shield/insta-shield
insertion order from the long encounter. Always rebinding either visual first
cannot preserve both histories. That approach is superseded: ObjectManager now
reorders delayed player-bound recreations by the captured dynamic-list order,
using existing object identities. The earlier fixed rebinding-order change was
removed. The regression also asserts original ordering when stars recreate in
reverse player order. At 19:17 BST, 125 camera/encounter/shield/two-phase restore
and required AIZ/load/bootstrap/decoding checks passed, no failures/errors/skips.
At 19:19 BST, 106 cold-graph/bonus coordinator/water return/census/art/explosion
checks passed, including subtype-0's 31 attempted bursts and allocation/RNG
failure ordering. The focused structural selection passed 23 checks without
failures/errors/skips. These are focused checks; combined delivery validation
and integration remain pending.


## SSZ branch reconciliation

Merge candidate combines `feature/ai-ssz-bring-up` at `55a999030` with campaign
milestone `9e12d6b3f`. Nine content conflicts were resolved by retaining both zone
owners/registrations, preserving LRZ's distinct Act 2 animation and boss scroll,
and retaining both capture `--star-post` and rings options (including the
rings-only Settings constructor used by existing probes). Both campaigns' status
entries remain; stale historical claims are reconciled in subsequent slices.
The 24-class SSZ/capture-arguments/LRZ-entry and required S3K selection passed
240 tests at 19:23 BST, no failures/errors/skips. This is focused merge validation,
not the campaign delivery suite.

Review found implementation gaps despite those passing tests: the Mecha act-end
object only sets `resultsRequested`, with no production results allocation or
ending pose; all three bosses incorrectly convert ROM score 100 directly to
100 displayed points (`HUD_AddToScore` stores tens, so the engine award is 1000).
The art loader also rejects `mecha_sonic_extra`: mappings reference tile $96 but
the registered compressed blob decodes to 139 tiles. These are next-slice fixes,
alongside the missing collapse/launch owner and the early cold-route frontier.


### SSZ results correction

The score review resolved against `HUD_AddToScore`'s 999999-unit cap, explicitly
9999990 displayed points. Removed the unnecessary shared boss score hook and
SSZ's 100-point overrides; all three use the existing 1000-point award.
`sub_868F8` now creates the production results owner after a living, grounded
leader passes the signed-word countdown gate. The owner retains control/camera
for the pending launch; `Check_TailsEndPose` poses the native second player once.
The allocation is first-free and attempted once, including exhaustion.

The first extended test run completed 44 checks with 43 passing and one error:
capturing after defeat found a dead trail reference retained by Mecha Sonic.
Trail expiry now removes that bookkeeping reference at the same lifecycle edge.
The subsequent 16-test Mecha selection passed with no failures/errors/skips,
including real results completion and capture/restore/forward replay. At 19:38 BST, the expanded 18-test Mecha selection plus
`TestRewindFieldDispositionGuard` and `TestRewindRecreateLinkToleranceGuard`
passed (20 checks, zero failures/errors/skips). The added allocation test initially
omitted manager registration and failed for missing services; registering the
object through the manager corrected the test setup. The command was queued
Maven with `-Dmse=off -Dtest=TestS3kSszMechaSpawnHeadless,TestRewindFieldDispositionGuard,TestRewindRecreateLinkToleranceGuard`
and the absolute S3K ROM path. The earlier GHZ/MTZ selection passed 28 checks. The art-loader warning and
missing launch remain separate open work; these results do not certify SSZ.


### SSZ dash trail art

`loc_7C902` / `byte_7D65F` consume only mapping frames 0..3: blank, then
three 24x8 trails. Register that exact prefix on `ObjDat3_7D402`'s palette 0
and high-priority bit. The complete native table still has 27 frames; frame 26
has twelve pieces at tile $93 (through $96), outside the 139-tile KosM blob.
It is not part of the dash consumer. Other effects remain owed and must resolve
their own VRAM binding; no tile data was fabricated or validator weakened.

At 19:41 BST, queued Maven `-Dtest=TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestS3kSszMechaSpawnHeadless`
with the absolute S3K ROM completed 98 checks, 97 passing, one failure, no skips.
The full ROM art crawler, new exact table/prefix regression, renderer guard,
and Mecha tests passed. The one failure was the pre-bring-up inventory assertion
that SSZ has seven standalone sheets; the merged registry has fifteen. Updated
that expected inventory and reran `TestSonic3kPlcArtRegistry#sszPlanHasEggRobo`:
one pass, no skips at 19:41 BST. This reconciles a stale merge test, not an asset
loading fallback. The corrected trail is visible in `$HOME/Videos/OGGF/ssz-bring-up/raw-39-mecha-trail-restored/capture.mp4`
(900 frames, 15 seconds; full ffmpeg decode checked). It starts at the declared
final-pad checkpoint with 355 rings; this is attack/art evidence, not a completed fight.


### SSZ launch implementation under validation

The campaign now connects production results to `SSZ1_ScreenEvent` stages 4/8,
ten independently delayed collapse columns, the four native Kos/KosM jobs,
Death Egg foreground layout/palette, 38 spiral pieces, crumble particles,
`Obj_57E96`'s forced jump/nine spiral turns/final arc and `StartNewLevel $B00`.
All runtime art still comes from the ROM. The launch owns captured timers,
fixed-point motion, job ordinals, screen shake and retained Plane-B tile words.
Mecha receives `Delete_Current_Sprite` for its next object dispatch, rather than
being removed during the screen event.

The component handover test uses declared checkpoint setup and test-positioned
boss hits, then exercises production results, neutral-input launch, actual DEZ1
load and graph removal/recreation with forward replay at four launch boundaries.
It is not a cold route or controller-only boss-clear claim. Width expansion and
required S3K regression selection are in progress; native team/donor breadth,
allocation exhaustion, video review and load-history isolation remain owed.

Two plausible but insufficient implementations were rejected during development:

- Reaching DEZ was possible even without the first scripted jump. The initial
  code wrote logical/history input only, which did not reach movement under the
  hardware-input lock. A negative-Y-velocity assertion exposed that missing
  jump; the existing forced-input ownership path now delivers it, with held
  input suppressing repeat jump presses. The focused regression passed.
- Reading `loc_57916` alone suggests 32 adjacent cleared tiles. Its consumer,
  `VInt_DrawLevel_Draw`, calls `VInt_VRAMWrite` twice, at addresses $80 bytes
  apart. D1=7 therefore means 16 tiles on each of two rows. The implementation
  retains native Plane B and writes the literal $6061 descriptors after normal
  entering-row updates; it does not replace the effect with a solid rectangle.

`Render_Sprites` reads zero `width_pixels` for these fresh native slots; their
height fields are $18 (bridge), $1C (ramp), $10 (detached bridge) and 8 (debris).
Those bounds replace the initial generic 32/28 and 16/8 culling margins.
The spiral DBF decrements before entering its loop, so its high-byte count means
exactly that many $70 rises; there is no additional or missing turn.

At 20:19 BST the seven-class selection completed 82 checks: 80 passed, two
failed, zero skips. The 400px handover lost its boss and the 800px case could
not land a hit. Mecha's native camera-relative fight box was being constructed
from the wider viewport's displayed left edge. Applying the same native focus
conversion already used by the GHZ recreation keeps the fight over its arena
floor. At 20:22 BST all 20 `TestS3kSszMechaSpawnHeadless` checks passed, including
the three widths through DEZ1. The other 62 checks in the earlier selection
passed: launch state/background row selection and the required AIZ/load/bootstrap/
decoding checks. Both selections used queued Maven, `-Dmse=off` and the absolute
S3K ROM path. A missing `ObjectLifetimeOps` import stopped the preceding compile
and was corrected before either completed selection. These remain focused
checks; combined campaign validation is still owed.

The first rendered Hyper route (`raw-40-mecha-launch-hyper`, declared final-pad
checkpoint, 355 rings, Super Emerald array `3333333`) cleared Mecha via controller
input and reached DEZ1. It exposed an absent foreground Death Egg: the launch
supplied absolute VSRAM words to a shader that adds its column input to camera Y.
The producer now converts to deltas; a regression asserts the final shader sum.
That initial recording is superseded during visual validation, not certified.
The input-authoring probe omitted rendering and its timing differed from the
recorded playback, so the rendered CSV, not the probe's completion frame, owns
capture claims. Investigate that difference before using the probe as a parity
oracle; neither run hydrates from trace comparison rows.

At 20:32 BST the expanded focused selection (`TestS3kSszMechaSpawnHeadless`,
`TestSszLaunchState`, `TestSszLaunchBackground`, `TestSonic3kPlcArtRegistry`,
`TestPatternSpriteRendererCorruptionGuard`) completed 107 checks: 106 passed,
one failed, zero skips. Native Sonic at 320/400/800, Sonic + Tails and S1-donor
handover passed, including the shader-sum regression. Tails alone exposed the
test's Sonic-sized floor-contact assumption. The test now waits for physics to
establish contact and asserts the handover on the exact first grounded dispatch.
At 20:34 BST both native-team cases passed; the two focused rewind guards passed
in a separate `-Pguards` invocation. All Maven runs used the shared queue and
absolute S3K/S1 ROM properties where applicable. The full ROM art crawler passed
with the launch ramp/debris registrations; no synthetic padding was added.

The corrected 4800-frame rendering now shows the foreground Death Egg and
reaches DEZ1 at camera-row frame 4223, with zero deaths. The complete 80-second
capture and 1940-frame (32.33-second) excerpt both passed ffmpeg decoding.
Clip 24, its source InputLog/state CSV/provenance and inspected stills are under
`$HOME/Videos/OGGF/ssz-bring-up/`. Source-frame 4000 shows the Death Egg;
4700 shows the loaded destination. Exhausted launch allocations, explicit
transition-history isolation and native timing/pixel comparison remain open.


### SSZ recreated-boss defeat explosions

Following launch commit `703158d80`, both `loc_7A5EC` and `loc_7AD3A`
now allocate an independent subtype-4 `Obj_CreateBossExplosion` controller.
`CreateChild1_Normal` uses **AllocateObjectAfterCurrent**, not first-free
allocation; the earlier working assumption was rejected by the routine itself.
The controller follows the occupant of its captured parent slot, predecrements
its zeroed wait word, reloads two, allocates each explosion forward, and consumes
RNG only after success. The child owns its initial explosion sound. An empty
parent slot installs next-pass deletion. MTZ exposes its native `$38` stop bit;
arbitrary replacement occupants' unmodelled `$38` bytes remain a fidelity gap.

The first 28-case encounter run completed with 26 passes and two rewind errors,
no skips. New defeat snapshots exposed stale deleted shield/orb references,
previously missed by the mid-fight spots. Children now release those references
when deleted. GHZ links become independent on `Obj_FlickerMove`, which no longer
reads their former parents, so earlier culled links cannot poison later snapshots.
The same tests preserve the source-backed 184-frame defeat-to-release interval.
Focused allocation tests also cover full forward slots, no fallback/RNG on
failure, three-frame cadence, slot replacement positions and deferred deletion.
The next focused run passed all 30 cases without skips. The initial refreshed
recording showed no explosions despite their live object graph: SSZ did not load
the shared boss-explosion sheet. The controller allocation path now ensures the
existing ROM-backed sheet is cached, as other S3K boss callers do. That recording
is superseded, not visual evidence. A test-only attempt to force object recreation
used a nonexistent `GameServices.levelManager()` accessor and failed compilation;
it was corrected to `GameServices.level()` before rerunning.
The four-case allocation/defeat selection then passed; both defeat cases passed
again with explicit ready-renderer assertions. The two rewind structural guards
passed in a separate fresh `-Pguards` JVM, with zero skips. Commands used queued
Maven, Java 21, `-Dmse=off`, and the absolute S3K ROM property. The final defeat
checks remove controllers/explosions before restoring, so they exercise recreation
as well as state rollback and forward replay.

The corrected `raw-41-mtz-defeat-explosions/capture.mp4` is 720 frames / 12 seconds,
source frames 1680–2399, native Sonic alone at 320 px with the declared
`$1700,$420` checkpoint and 355 rings. It shows the bursts and the exit pad;
frame 1770 was inspected and the full file passed ffmpeg decoding. All 2400
stepped CSV rows are alive. The capture's provenance records source hashes.
This is local rendered evidence, not native parity or a cold route.
The combined change-based plan against `c91fd5ac7` now selects 2785 ordinary
classes plus guards; that campaign-wide run remains owed.


### SSZ runtime gate rewind follow-up

After `eaf341739`, inspection found `_unkFA82` (EggRobo pairing bits) and
`Boss_flag` absent from `SszZoneRuntimeState.captureBytes/restoreBytes`. A focused
regression reproduced the stale boss flag across retirement. Both the unsigned
word and boolean now round-trip, including into a newly created runtime state.
The initial test used a nonexistent enum constant and failed compilation; after
correcting it to `SONIC_ALONE`, the expected behavioral failure was observed.

At 20:58 BST, queued Java-21 Maven `-Dmse=off` with absolute S3K/S1 ROM paths
passed 62 checks, zero skips, across `TestSszZoneRuntimeState`,
`TestSszLaunchState`, `TestS3kSszEggRobo`, `TestS3kSszGhzArenaHeadless`,
`TestS3kSszMtzArenaHeadless`, and `TestS3kSszMechaSpawnHeadless`. This covers the
expanded byte layout's consumers and existing launch graph recreation. It is a
focused pass, not campaign-wide certification.


### Mecha defeat palette and spark follow-up

After `3c024e515`, act-1 `loc_7B888` installs the ROM-backed `word_7D842`
palette script and allocates `Obj_MechaSonic_Sparks` forward from the boss.
`loc_7B984` advances the signed byte delay before animation, and the colour write
resolves immediately so the later spark slot sees the same-pass `$E88` at line 1
colour 9. All twelve durations and the repeat edge come from the shipped script;
rotation-disable freezes the delay. The spark alternates ROM offset/frame rows,
plays its own sound on each gated dispatch, and keeps native no-liveness-test
behavior when its parent slot becomes empty. Arbitrary replacement occupants'
`$38`/render-flag bytes remain an unmodelled semantic boundary.

The spark has its own eight-frame mapping prefix on palette line 1. The existing
four-frame dash sheet remains on line 0; the full 27-frame extra-effects table
still must not be bound wholesale to the 139-tile art blob.

ROM inspection rejected the plan's claim that `loc_7B39C` consumes an unwritten
slot. `AllocateObject` only scans code pointers and returns an address; no SST
write or reservation occurs. The bare tail call is therefore observationally
inert for the later objects. A synthetic engine reservation would be wrong.

The first focused selection completed 81 checks: 80 passed, zero skips, and the
standalone inventory expected 15 rather than the new 16 sheets. The full ROM
mapping crawler and renderer corruption guard passed, as did the complete
palette-row/spark-gate regression. After updating the inventory, all 23 Mecha
cases plus the inventory check passed, including rotation-disable and launch
recreation with spark objects removed before restore. The two rewind guards passed in a fresh `-Pguards` JVM at 21:06 BST,
zero skips. The corrected `raw-42-mecha-defeat-sparks` recording is 1000 frames
(16.67 seconds), source 1600–2599 of 2600 stepped frames, zero deaths. It uses
raw-40's declared Hyper checkpoint input. Frame 1805 shows the flash and spark;
full ffmpeg decoding passed. Source hashes and setup are in the media provenance.
These remain focused checks; combined campaign validation is still owed.


### Mecha secondary collision follow-up

After `d094005bb`, `ChildObjDat_7D474` now allocates the invisible `loc_7C9BA`
owner forward from Mecha's slot. `sub_7D260` reads `(dx,dy,collision,frame)` from
ROM `byte_7D280`, indexed by the parent's mapping frame. Position mirrors the
parent's X orientation. This owner adds collision without drawing, so it does
not require a render flag. The parent's ordinary hit window does not suppress
it; status bit 7 on the killing hit stops collision and installs next-pass
deletion. The bare allocation after it remains a no-op search.

At 21:11 BST all 24 `TestS3kSszMechaSpawnHeadless` checks passed with no skips,
including the 1600-frame secondary-table walk, real player hurt while the
parent's collision byte is zero, killing-hit retirement, and all existing
width/team/donor handovers. Queued Maven used Java 21, `-Dmse=off`, and absolute
S3K/S1 paths. The explicit removed-owner recreation check also passed. The refreshed
`raw-43-mecha-complete-graph` controller recording includes this combat change:
4800 frames, zero deaths, DEZ1 camera reset at 4223, destination inspected at
4700, and full 80-second decoding passed. It uses the same declared Hyper
checkpoint and input as raw-40, not a cold-route/native-parity setup.
Both rewind guards passed in a fresh `-Pguards` invocation at 21:14 BST,
zero skips. Clip 25 is the decoded 2940-frame / 49-second excerpt (source
1500–4439). Combined campaign validation/integration remains outstanding.


### DEZ branch integration into the campaign

Merged existing DEZ head `b65b5966a` into campaign head `7dc253667` locally.
The ten textual conflicts were reconciled by retaining LRZ/SSZ registrations,
act-specific animation and special-zone routing while adding DEZ owners. Capture
settings preserve the existing checkpoint/rings constructors and expose reverse
gravity independently; a combined-option regression checks all three. The DEZ
reference worktree remains untouched.

The merged Java-21 queued Maven selection `TestS3kDez*,TestS3kReverseGravity*,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry` completed 347 tests with zero
failures, errors or skips, using absolute S3K/S1/S2 ROM paths. The seeded act-2
frontier remains 1256 frames; row 21029 differs by one pixel in X and camera X.
Cold act-2 entry remains zero matching frames and is not certified by this pass.

The capture arguments, imported badnik checks and rewind checks initially ran
29 tests: 28 passed, no skips; the only failure was the stale source-class count.
The actual probe reports 1232 classes, 992 isolated passes, 240 graph-covered,
zero missing codecs and unchanged remaining buckets. Updating the count was
followed by both rewind checks passing in a fresh `-Pguards` JVM. This is focused
integration evidence; shared reverse-gravity changes still require the combined
campaign validation before delivery.


### Turbine controller lifetime and airborne solid contacts

After `bf7e5175c`, the old `$262D`/`$2636` freeze was reproduced in the production
loop: at frame 51 the placed `$5F` controller was removed while its player-control
byte still suppressed movement. The old diagnosis ruled out the owner because
its early acceleration worked; that inference was false. `Obj_DEZGravityRoom`
checks `((x+$400)&$FF80)-Camera_X_pos_coarse_back` as an unsigned word against
`$680`, after both player slots. The default engine range retired it early.
The fix supplies that existing per-object range/timing contract. No shared
collision algorithm changed and no native probe was needed to resolve this
explicit ROM tail.

A lifetime-only capture (`042`) then reached `$29AB`, but exposed a second
failure: carried players passed the closed exit gate. A production-loop assertion
failed at frame 83. Both `$60` and `$61` must admit positive `object_control=$01`:
`SolidObjectFull2` reaches `loc_1DFFE`, which rejects only negative values.
Their callbacks had also confused `d6` returned side bits with grounded status
pushing bits. `loc_1E094` sets the returned bit even in air. The providers now
admit positive control, reject bit 7 on new contact, and consume `touchSide` for
their ROM launches/panel writes. The initial closed-gate test was masked once
the earlier puzzle correctly bounced the player; its independent setup now
starts after the puzzle and explicitly supplies the skipped room owner. That
synthetic owner occupies a later slot, so its `$38` acceleration can follow the
`-$C00` bounce in the same pass. The test checks reversal; the real placed-puzzle
route checks the exact launch.

At 21:35 BST, queued Java-21 Maven `-Dmse=off` with the absolute S3K ROM passed
the then-14 `TestS3kDezGravityRoomHeadless` cases, zero skips; the wide
configuration claim in that first pass was subsequently invalidated below. The unchanged neighboring
`TestS3kDezGravityPuzzleHeadless` (12) and `TestS3kDezBumperWallHeadless` (4) passed
in the preceding focused selections. Coverage includes unsigned retirement edges,
the actual placed owner surviving the old stall, airborne panel writes/bounce,
closed gate rejection, and 100 frames of identical production-loop movement,
camera and panel state after rewind. A positioned ordinary-input route (`60`
neutral, alternating `30` up / `30` down) hits all six panels before crossing
the gate and leaves the room at 320 px. The initial 800 px parameter only changed configuration
while the existing camera stayed 320 px; it is not wide-route evidence. The test
now rebuilds the session and asserts the actual camera width. Wide capture `046`
hits five panels but does not yet exit with the same inputs. No state is hydrated during
these routes. Native pixel/timing, cold act entry and broader team/donor acceptance
remain open; this is not full campaign validation.

Media live under `$HOME/Videos/OGGF/s3k-dez-bring-up/`: `043` (600 neutral frames)
and `044` (360 neutral wide frames) show real bounces; `045` (1200 frames) shows
the six-panel controller route and exit at native width. All have zero deaths;
full video decoding and selected phase images were inspected. `042` is explicitly
an intermediate lifetime-only recording with the later-discovered gate bypass,
not completion evidence. Inputs and provenance are retained outside the repo.

The corrected-width route attempt remained red at 800 px (panel set `$38` after
1500 frames). Identical pad timing is not a cross-width acceptance requirement:
earlier spawn visibility changes the shaft's bob phase. Final tests separate the
complete native-width route from the independent closed-gate bounce at actual
320/800 px; all 14 room cases passed at 21:41 BST, zero skips. `046` and `047`
wide recordings remain behind the gate and are marked as attempts. A temporary
authoring probe reported wide completion that did not reproduce in recording,
even when it rendered every step; that result is rejected as completion evidence.
Wide full-room controller acceptance remains open rather than being inferred
from config values or the probe. The source change is confined to three DEZ
object contracts; combined shared-physics campaign validation remains owed.


### Invisible shock-block family

After `674a99f72`, SKL `$6D` now registers the shock variant of the shared
horizontal hurt block; S3KL still creates HCZWaterSplash. The first focused
reaction test failed specifically for LIGHTNING on the top face. The shared
reaction-byte mapping now recognizes bit 5 while retaining the existing fire
bit. The test covers all four flip combinations, five shield states and three
contact faces, with no immunity from ordinary, fire or bubble shields.

The shock owner also preserves the native flipped init return: side/underside
variants skip both contact and range retirement for that first dispatch. Top
init falls through. Its fixed unsigned coarse range check runs after contact,
through the existing object-manager contract. Scalar init state is rewind-owned.

Queued Java-21 `-Dmse=off` with absolute ROM paths completed 79 checks for the
initial shared-reaction/registration change, including the four mandatory S3K
loading regressions, zero skips. The later init/production selection completed
22 checks: only a too-strict lightning ring-count assertion failed (10 rather
than 7, from normal attraction of nearby rings). The corrected five shield
cases on the actual DEZ1 record-27 floor then passed at 21:50 BST, including
80-frame restore/forward replay of damage, position, rings and shield state,
zero skips. The shared hurt-block cases and three cold-route checks passed;
the seeded act-2 frontier stays 1256 frames / row 21029 X and camera X, cold
entry remains 0 matching frames.

At 21:52 BST, `-Pguards -Dtest=TestRemainingRewindTailInventory,
TestRewindArchitectureGuard,TestSonic3kObjectProfileRegistryGuard` passed all
7 checks, no skips, in a fresh JVM. The source-class probe is 1233 total, 993
isolated passes and 240 graph-covered, no missing codec. Census is 224/365
concrete in act 1 and 318/494 in act 2. These are focused checks, not combined
campaign delivery or a full route/native visual claim.


### Visible lightning family

After `de73bead8`, SKL `$52` is implemented from `Obj_DEZLightning` at `$478BE`
through `$4791A`. Animation `$47926` and mapping `$4792E` are ROM-backed;
DEZMisc tile `$379`, palette 0 and sprite bucket 5 follow the art word and
priority word independently. `$FC` ends the flash into a subtype countdown.
Only pose 3 publishes a collision-list pointer; flags stay `$9F`, so the next
player pass consumes exactly the native list. Sound reads the last renderer
visibility bit, including the empty waiting pose.

The initial census caught the wrong pillar constant (`ALT` is `$20`); the final
registration uses `$52`. The contact test's original absolute pass count also
counted initial-load dispatch as ordinary gameplay. It now observes the actual
preceding published pose and checks damage through the production player loop,
including restoring a pending damaging pointer and replaying the hit.

At 22:08 BST, queued Java 21 `-Dmse=off` selection
`TestS3kDezLightningHeadless,TestS3kDezPlacementCensus,TestSonic3kPlcArtRegistry,
TestPatternSpriteRendererCorruptionGuard,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
passed 150 tests, zero failures/errors/skips, with absolute S3K and S1 ROM paths.
This includes both packages' `TestSonic3kLevelLoading` classes.
Clip `050` under `$HOME/Videos/OGGF/s3k-dez-bring-up/` shows 300 frames of the
placed emitters, zero deaths, and passes full ffmpeg decode. Frame 90 was
inspected. `048` immediately died and `049` framed the emitters below the screen;
neither supports visual acceptance. Census is 272/365 and 412/494 concrete.
Native matching and donor/team/inverted-contact breadth remain open.

Fresh `-Pguards` selection `TestRemainingRewindTailInventory,TestRewindArchitectureGuard,TestSonic3kObjectProfileRegistryGuard` passed 7 checks at 22:09 BST, zero skips. Inventory is 1234 total / 994 isolated / 240 graph-covered / 0 missing codec. Combined campaign validation remains owed.


### Conveyor-belt family

After `bdfd129ff`, `$50` implements the ROM grounded-player displacement in
`sub_47854`, including signed direction from the centreline/X-flip, unsigned
half-open word bounds, native P1/P2 participation, no reverse-gravity gate,
unchanged fractional position and velocity, and the post-contact `$280` range
tail. No sprite/art owner is added: the belt artwork is level art.

At 22:17 BST, queued Java 21 with absolute S3K ROM path and
`-Dtest=TestS3kDezConveyorBeltObjectInstance,TestS3kDezConveyorBeltHeadless,
TestS3kDezPlacementCensus,TestS3kMgzTwistingLoopObject,TestS3kDezColdRoutes`
passed 47 checks, zero failures/errors/skips. The placed belt carries the
positioned player 40 pixels in 20 dispatches at verified 320/800 camera widths,
including restore/forward replay. The S3KL `$50` twisting-loop behavior remains
green. Seeded DEZ2 still matches 1256 frames, first difference row 21029 player
X `$697/$696` and camera X `$5F7/$5F6`; the cold entrance remains unmatched.

Census is 280/365 and 417/494 concrete. Visual attempts `051`–`054` are recorded
as attempts, not acceptance: the positioned player is obscured by foreground
art while the belt carries, or a higher entry lands outside the belt window.
The previous lightning clip `050` remains the last accepted DEZ video. The
conveyor needs a route-based visual entry; no player priority was forced.
The Act 1 matrix's width list was corrected from stale 512/640 values to the
actual `WidescreenAspect` presets. These focused checks do not certify DEZ.

At 22:18 BST the fresh `-Pguards` inventory/architecture/profile selection passed all 7 checks, zero skips. Measured inventory is 1235 total / 995 isolated / 240 graph-covered / 0 missing codec. Combined campaign validation is still pending.


### Torpedo-launcher family

After `222473083`, `$4D` implements the visible-only signed word countdown,
allocation-failure recoil path and independent later-slot torpedo of
`Obj_DEZTorpedoLauncher` / `loc_471D6`–`loc_472A2`. Mapping `$472A8` has ten
frames with separate parent/projectile palette bases, tile `$373`; recoil
uses a byte timer and decrements on the firing pass. The projectile carries
the copied visible flag into its first dispatch, publishes a touch pointer
after moving and retires on a later off-screen flag, not coarse spawn range.
`ObjectSpawn` intentionally keeps only placement flip bits, so the runtime
visibility is a captured scalar seeded by the visible-only creation contract.

At 22:28 BST, queued Java-21 selection
`TestS3kDezTorpedoHeadless,TestS3kDezPlacementCensus,TestSonic3kPlcArtRegistry,
TestPatternSpriteRendererCorruptionGuard,TestCnzBarberPoleObjectInstance`
passed 105 tests, zero failures/errors/skips, with absolute S3K/S1 ROM paths.
This includes actual exhausted SST allocation, inherited visibility, recoil
boundaries, ROM piece geometry/palette, same-pass projectile movement and
production damage/restore/replay. At 22:28–22:29, fresh `-Pguards` inventory,
architecture and profile checks passed 7/7 without skips: 1237 total classes,
997 isolated passes, 240 graph-covered, no missing codec.

Clip `057` under `$HOME/Videos/OGGF/s3k-dez-bring-up/` passes full ffmpeg decode:
420 frames, zero deaths, with the torpedo moving right in inspected frames
125/135 while Sonic jumps below it. `055` and `056` did not keep the launcher
visible and are not firing evidence. This is positioned engine footage, not
native parity or a cold route. Census is 316/365 and 455/494; shared campaign
validation/integration and remaining level work are still pending.


### Four-section staircase family

After `35390ba3c`, `$4F` follows `Obj_DEZStaircase` through `loc_47814`:
parent plus three forward-allocated solid sections, partial-allocation fallthrough,
original-anchor range tails, separate render flips and original status word order,
standing/underside return-bit latches, 30/60-tick waits, alternating shake and
signed fixed-point rounding of the four step offsets. The render map is `$46F7A`
at the staircase-specific tile `$480`, palette 1, bucket 3.

The first art/census selection at 22:36 BST passed 92 checks without skips.
The next selection passed the eight then-current staircase cases, including
320/800 actual landing/carry and forced graph recreation; its only failure was
the older `TestSinkingMudRegistry` assertion that SKL `$4F` was still a
placeholder. That assertion now expects the staircase, retaining S3KL mud.
At 22:40 BST, queued Java 21 with absolute S3K ROM path and
`-Dtest=TestS3kDezStaircaseHeadless,TestS3kDezPlacementCensus,
TestSinkingMudObjectInstance,TestSinkingMudRegistry,TestS3kDezColdRoutes`
passed 27 tests, zero failures/errors/skips. The new ninth staircase case
exhausts the actual SST pool, leaves two slots, and verifies the resulting
three-section partial staircase and shared retirement anchor.

The graph replay deletes/removes all four sections before restoring; the
restored children point to the recreated managed parent, then reproduce the
shake-to-extension positions for 180 dispatches. Actual placed landings also
restore player movement and nearby hazards at 320/800. Seeded DEZ2 remains at
1256 matching frames / row 21029 first X/camera-X difference, cold entry 0.

Clips `058` (320) and `059` (800) under `$HOME/Videos/OGGF/s3k-dez-bring-up/`
show the landing, delay and upward ride, 360 frames each, no damage/deaths.
Both pass full ffmpeg decode and frame 160 was inspected. Census is 334/365
and 470/494. Native parity and donor/team breadth
remain open; the clips are positioned engine evidence only.

At 22:49 BST, the ten-case `TestS3kDezStaircaseHeadless` selection passed with
zero skips on the worktree after `35390ba3c`. The added case uses the actual
Act-2 `$950,$790` flipped placement with explicitly declared reverse gravity:
inverted landing starts its timer, the parent rises its assigned quarter word
to `$770`, and player movement/gravity replay for 180 frames after restore.
This is a component-entry check, not evidence of reaching it through the route.

The first structural run rejected custom capture/restore overrides for the
new staircase. Those overrides were removed rather than expanding the guard
baseline. An exact `parent` field policy uses the existing identity codec;
the ten-case rerun includes removing/recreating all four sections and asserting
that each child binds to the newly managed parent. Clip gameplay is unchanged
by this rewind-only revision; the capture provenance retains its original source hash.

At 22:49 BST the fresh `-Pguards` inventory/architecture/profile selection
passed all 7 checks with zero skips after the schema change. Inventory is
1238 total / 998 isolated / 240 graph-covered / 0 missing codec.
Combined campaign validation remains pending.

### Hover machine family

After `46be7745e`, SKL `$5E` follows `loc_494DA`, `loc_494EA` and `sub_4952A`.
The stationary housing and parentless later-slot rotor capture scalar state
through the existing schema. Old orbit phase owns movement and bucket 4/6;
the player's horizontal offset independently owns the semicircular lift.
Native P1/P2 admission, fractions, jump/flip fields and Level_frame_counter
sound cadence are covered. Exhausted allocation still animates the housing
and is not retried. Forced removal/recreation reproduces orbit and priority.

At 22:55 BST the focused object/headless/census/PLC selection passed 93 checks,
zero skips, including actual lift and replay at verified 320/800 widths. An
incorrectly named renderer-corruption selector matched no class; the actual
`TestPatternSpriteRendererCorruptionGuard` is being run separately. Fresh
inventory/architecture/profile guards passed 7 at 22:56, zero skips, with
1240 total / 1000 isolated / 240 graph-covered / 0 missing codec.

`060-hover-machine-320` and `061-hover-machine-800` under the external DEZ
capture directory each show six seconds of positioned hovering, zero damage
or deaths. Both videos passed full decode and frame 90 visual inspection.
Counts are now 345/365 and 470/494 concrete placements, leaving 20/24
placeholders. Native matching, complete routes and donor/character breadth
remain open; campaign validation and integration remain pending.

At 22:57 BST the separately selected `TestPatternSpriteRendererCorruptionGuard`
passed both cases with zero skips, completing 95 focused checks for this slice.


### Hanging carrier family

After `b1c767647`, SKL `$4C` follows `Obj_DEZHangCarrier` / `sub_4703E`.
The previous P1 grab latch installs the rise routine without executing it that
pass; P2 can hang independently but cannot start movement. MoveSprite2 runs
before -8 acceleration. Native upward FindFloor uses radius `$14`; ceiling
penetration correction preserves the fractional word, then installs horizontal
±$200 travel for unsigned subtype×4 dispatches. The current X owns retirement.

Grab uses the half-open rectangle and admits positive object_control while
rejecting bit 7, hurt/dead and debug state. It clears only Status_InAir via the
existing native-control helper: an initial `setAir(false)` incorrectly invoked
terrain landing resets and was removed. A regression preserves jump fields and
custom radii. Jump release consumes the logical press edge, chooses 18/60 ticks
from all held directions, lets right override left, and writes native radii
without shifting the centre or fractions. Cooldown expiry returns before any
recapture. Lost render bit or hurt/death releases without launch. Pin sound
reads Level_frame_counter independently for both native players.

At 23:06 BST, queued Java 21 and absolute S3K ROM path with
`-Dtest=TestS3kDezHangCarrierObjectInstance,TestS3kDezHangCarrierHeadless,
TestS3kDezPlacementCensus,TestCnzSpiralTubeMultiSidekick,
TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard`
passed 99 checks, zero skips. The prior unit run exposed an incorrect expected
switch sound and the synthetic landing path; both were corrected from the
owning ROM routine. At 23:08, the three headless cases passed again after adding
forced carrier deletion/recreation before restore. Actual 320/800 rides rise
against real terrain, complete the `$25` leftward travel, release on a jump,
and reproduce 400 frames plus that release. The fresh structural selection
passes 7 checks, zero skips (1241 total / 1001 isolated / 240 graph / 0 missing).

Clips `062-hang-carrier-320` (480 frames) and `063-hang-carrier-800`
(430 frames) show the positioned ride and release. Both pass full ffmpeg decode
and inspected frames 100/300 (320) and 300 (800). No deaths; the longer 320 clip
includes a post-release hazard hit with 36 hurt-state rows. Counts are 348/365
and 471/494 concrete. Native parity, cold routes, Act-2 interaction and broader
participants remain open. Combined validation/integration are still pending.


### Curved energy bridge and approach-window correction

After `33e6b66d5`, SKL `$56` follows `Obj_DEZEnergyBridgeCurved`, shared
`sub_47DDE` phase decoding, and `sub_47F9C`. Its rectangle admits native P1/P2
without status gates and switches top/LRB solidity to E/F; leaving restores
C/D without setting air, while timer expiry sets air for admitted players.
Expiry still draws; inactive passes do not. Sound consumes the previous
rendered bit and Level_frame_counter. The ROM map `$48038` has 3/3/3/2 pieces
at DEZMisc+$B2. The existing straight `$55` bridge incorrectly hid its expiry
draw and used freshly computed visibility; both edges now match the same ROM
tail, with regression coverage and no seeded route-frontier change.

Initial focused selection at 23:15 BST passed 29 cases (curved unit, straight
bridge, census, MGZ moving platform and cold-route checks). At 23:16, the three
new ROM/headless cases plus 78 PLC and 2 renderer-corruption checks passed,
zero skips. Initial fresh inventory/architecture/profile guards passed 7 at
23:17 (1242 total / 1002 isolated / 240 graph / 0 missing codec).

A stronger controller approach passed at 320 but failed at 800: the curve was
loaded into the wider placement window and immediately retired against literal
`$280`, then remained dormant when the player arrived. Clip 068 reproduces
the absent curve and missed carrier. Positioned width checks had missed this.
The fix uses existing `coarseXCullRange()` in the new DEZ families, retaining
the exact native limit and extending only the viewport term under the documented
engine policy. The turbine retains its additional `$400` in anchor and range.
A direct boundary regression covers nine affected object classes. The controller
approach now crosses secondary terrain, loops around and grabs the hanging
carrier at both widths, with restore/forward replay from before the curve.
No gameplay state or priority was forced to make the route work.

At 23:24 BST, queued Java 21, absolute S3K ROM path and
`-Dtest=TestS3kDezCurvedEnergyBridgeObjectInstance,TestS3kDezCurvedEnergyBridgeHeadless,
TestS3kDezEnergyBridgeHeadless,TestS3kDezHangCarrierObjectInstance,
TestS3kDezHangCarrierHeadless,TestS3kDezHoverMachineObjectInstance,
TestS3kDezHoverMachineHeadless,TestS3kDezStaircaseHeadless,TestS3kDezTorpedoHeadless,
TestS3kDezConveyorBeltObjectInstance,TestS3kDezConveyorBeltHeadless,
TestS3kDezLightningHeadless,TestS3kDezGravityRoomHeadless,TestS3kDezColdRoutes`
passed 87 checks, zero failures/errors/skips. This includes the two newly added
controller approaches, component recreation/expiry checks, prior object graph
replays and native/wide retirement boundaries. Native seeded DEZ2 still matches
1256 frames; first differing row remains 21029 (X 0696/0697, camera 05F6/05F7).
The cold act-entry mismatch remains. Counts are 349/365 and 471/494 concrete,
leaving 16/23 placeholder placements; native parity and complete routes remain
open. Campaign-wide verification and integration are pending.

Capture attempts are kept distinct: 064 shows stationary expiry, 065 climbs
partway and rolls back with insufficient momentum, 066 reaches the curve while
it is off, and 067 times the approach into its lit phase using only controller
input. The initial 068 launch raced a compile and failed before boot; its retry
is the pre-fix wide failure above. Compile and capture must be sequential even
for a source-comment rebuild. Corrected widescreen footage is 069. These are
positioned engine routes, not cold-start or native comparison evidence.

Clips 067 (320) and corrected 069 (800) each complete 504 frames with zero
hurt/death rows. Full decode and traversal-frame inspection pass, and every
player X/Y/X-velocity/Y-velocity/ground-speed row agrees across widths.

### Floating platforms and inherited LRZ oscillator correction

After `8a58aafbc`, SKL `$4A` implements `Obj_DEZFloatingPlatform`, the nine
`word_25AB8` movers, full-solid carry, original-anchor retirement and alternate
mapping frames. ROM map `$25ACA` has 2/3 pieces; DEZ2Extra+$08 is tile `$33A`,
palette 1. The high subtype nibble does not select a skin. Status X-flip reflects
motion; `MOVE.B #4,render_flags` clears artwork flips. All ten Act-2 placements
now resolve, making the census 349/365 and 481/494 (16/13 placeholders).

The initial comparison against LRZ's existing implementation was insufficient:
both read native oscillator offsets as engine data offsets. A real 180-frame
ride caught a 255-pixel platform jump. `OscillationManager` excludes the native
control word, so `+$0A/+$1E` must use `$08/$1C`. Both classes are corrected,
and a 400-update test reads independently from the full ROM-format table to
catch position/velocity confusion. LRZ's artwork also now obeys its init's
cleared render flags. The first positioned entry at `$1570,$790` missed the
leftward platform; the accepted landing starts at `$1540,$790`, without forcing
collision or changing movement. A separate recreation-test error attempted to
remove a placed object with the dynamic-object API; retiring it through a real
manager step before restore now proves new-instance recreation.

Queued Java 21 commands, absolute S3K ROM, current worktree after `8a58aafbc`:

- At 23:41, floating unit (4), LRZ unit (7), DEZ census (6) and CNZ bumper (11)
  passed. The two placed tests reached the recreation check and exposed the
  test's removal mistake above; the invocation was not green.
- At 23:42, `-Dtest=TestS3kDezFloatingPlatformHeadless,TestS3kDezColdRoutes,
  TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
  TestSonic3kDecodingUtils` passed 65 tests, zero failures/errors/skips. Both
  classes named `TestSonic3kLevelLoading` ran (35 and 7).
- At 23:42, `TestS3kLrzSolidMovingPlatformHeadless` passed two actual Act-2
  record-49 rides at 320/800, each with 180-frame replay after forced recreation.
- At 23:43, fresh `-Pguards` selection of inventory, rewind architecture and
  profile/registry passed 7 checks, zero skips: 1243 total object types,
  1003 isolated / 240 graph-covered / 0 missing codec.
- At 23:44, the final render-facing correction passed floating unit (now 5)
  and LRZ unit (7). The earlier art selection passed all 78 PLC and both
  renderer-corruption checks; that invocation's placed tests had still been red.

The seeded DEZ2 frontier remains 1256 frames, first mismatch at native row
21029 (player X 0696/0697, camera 05F6/05F7); cold entry remains 0. No trace
frontier is claimed to have moved. Reverse-gravity placement entries, other
characters/donors/teams, cold routes and native visual comparison remain open.
Combined campaign verification and integration remain pending.

Accepted recordings under `$HOME/Videos/OGGF/s3k-dez-bring-up/` are 071/072
(DEZ 320/800) and 073 (LRZ 320), each 420 frames with zero hurt/death rows,
full ffmpeg decode and frame-120 inspection. DEZ player position/velocity rows
match across widths for all 420 frames. Rejected 070 preserves the missed-entry
evidence. Each capture has source hashes and explicit positioned-entry scope.


### Tilting bridge

After `6b7055cea`, SKL `$4B` is implemented as eight independent forward SST
allocations sharing the exact parent's prior-standing aggregate. Each part
reads signed acceleration directly from ROM `$46ED8`, adds it to a long
16:16 velocity, and moves long Y. Parent tilt beyond ±$70 installs a fall
routine for the following dispatch; that routine multiplies velocity by four
once before adding `$1000` per pass. The manual solid checkpoint precedes
the shared terrain-release check. Going below Camera_max_Y+$110 replaces
both X and retirement anchor with `$7F00`. The one-frame map `$46F7A` uses
DEZMisc tile `$34D`, palette 1 and bucket 5. Schema identity capture relinks
all children after complete graph recreation, without adding bespoke codecs.

At 23:55 BST, queued Java 21 with the absolute S3K ROM and
`-Dtest=TestS3kDezTiltingBridgeHeadless,TestS3kDezPlacementCensus,
TestCnzTriangleBumperObjectInstance` passed 18 tests, zero failures/errors/skips.
At 23:56, the expanded eight tilting checks plus the 78-case PLC crawler and
two renderer-corruption checks passed 88. At 23:57 the final nine tilting
cases passed, including an inverted DEZ2 record-133 landing with declared
reverse-gravity entry, and explicit release onto real terrain in the two
normal-gravity routes. Unique focused coverage is 99 checks across these
selections; this is not a broad-suite result. At 23:58, the fresh inventory,
rewind-architecture and profile/registry guard selection passed 7, zero skips:
1244 total / 1004 isolated / 240 graph-covered / 0 missing codec.

Counts are 350/365 and 484/494 concrete, leaving 15/10 placeholders. Remaining
placed families are lift pads `$4E`, conveyor pads `$53`, tunnel launchers `$57`,
and the two act bosses. Counts do not cover the unplaced final-boss sequence.
Cold routes, native comparison, donor/roster breadth, final-boss handover and
campaign integration remain open.

Clips 074/075 under the external DEZ task directory show the actual Act-1
bridge landing, tilt, collapse and floor release at 320/800. Both record
480 frames, zero hurt/death rows; every player position/velocity row matches
across widths. Full decode and frame-280 inspection pass; the 320 frame 420
shows Sonic on the floor after the sections fall away. Positioned footage is
not cold-route or native comparison evidence.

### DEZ widescreen composition (after `30ab1b14f`, 2026-09-23)

User identified repeated fixed scenery in both wide acts and warned that simply
centring the native crop exposes unfinished edges. Decoded ROM backgrounds
confirm that Act 1 has reusable side walls, while Act 2 has no hidden complete
planet sides. Rejected a bare centred crop and whole-image horizontal stretching;
the user selected a curve extension that preserves the native centre.

Act 1 now centres its 320px source and reflects 32px outer-wall strips. Its
render-only descriptor owner and period cover the viewport; terrain is unchanged.
Act 2 keeps that original centre pixel-for-pixel. Outside it, reflected 64px
ROM surface strips follow a circle through the indexed silhouette's left edge,
apex and right edge. Derive the outline from indexed art rather than live RGB:
otherwise a fade-to-black can cache a false planet shape. A generic internal
background column remap carries source X and a Y offset through the prepared
render frame; shared renderer/shader code contains no game or zone checks.
The extension is intentional widescreen presentation, not ROM pixel parity.

`TestS3kDezWidescreenBackground` checks all five supported widths, original
centre preservation, per-pixel wall reflection, continuous planet limbs, cache
identity, black-palette independence, restore and act-load isolation. The first
shared regression run passed 95 executed checks, with two explicitly opt-in
background capture/performance diagnostics skipped. Commands used the queued
Maven wrapper, Java 21, DISPLAY=:0 and the absolute S3K ROM. This selection
included the required AIZ/loading/bootstrap/decoding tests and actual capture
smoke. A follow-up checks the final prepared-frame wiring. Campaign-wide
verification/integration remains outstanding.

External clips 076/077 show the initial 800px implementation, 360 frames each,
zero hurt/death rows. Both fully decode; frame 280 of Act 1 and frame 120 of
Act 2 were inspected. They are positioned presentation checks, not cold-route
or native comparison evidence. ROM-only layout and curve studies accompany
them in `$HOME/Videos/OGGF/s3k-dez-bring-up/`.

Final prepared-frame check: queued `maven_queue.py -q -Dmse=off
-Ds3k.rom.path=<absolute-s3k.gen>
-Dtest=TestS3kDezWidescreenBackground,TestS3kDezScrollHeadless,TestLevelRendererBackgroundViewport,TestParallaxRewindSnapshot,TestGameplayCaptureSmoke
test` passed **34 tests, zero failures/errors/skips**. Final clips 078/079
fully decode and have identical CSV and checked PNGs to 076/077. Width study
080 captures frame 120 after 121 steps at 320/352/400/528 for both acts; all
eight images were inspected, all runs have zero hurt/death rows. Native
320px PNGs are byte-identical to the earlier frame-120 images from clips
074 (Act 1) and 071 (Act 2). This is matched engine regression evidence,
not native emulator pixel certification. The extension adds no object types
or census changes. Conveyor-pad WIP remains stashed until this fix is committed.

### DEZ conveyor pads (after `f759da908`, 2026-09-23)

Restored the conveyor-pad WIP after the widescreen fix and completed SKL `$53`
from `loc_479F0`–`sub_47B58`. Activation installs the selected routine but does
not run it immediately. Vertical motion has finite word distance and a separate
native P1/P2 belt carry, reversed by gravity; horizontal motion follows floor
probes, falls after a delayed routine change and reverses at walls. Both toggle
belt animation once on new standing bits. Horizontal sound follows the manual
solid checkpoint and uses the preceding render visibility; vertical sound does
not require visibility. Animation and both mapping tables load from ROM.
The narrow table has five pointers, including a wide-frame alias, not four.

The eleven `TestS3kDezConveyorPadHeadless` checks pass: all direction flags and
both gravity polarities, both native slots/extra-follower exclusion, exact
finite travel and subtype `$80`, ROM animation, terrain boundary and fall order,
map shape, real horizontal/vertical rides at 320/800, forced recreation and
forward replay, and a declared inverted Act-2 underside ride/replay. The final
focused command selected that class, `TestS3kDezColdRoutes`,
`TestSonic3kPlcArtRegistry` and `TestPatternSpriteRendererCorruptionGuard`:
**94 passed, zero skips**, Java 21 and the absolute S3K ROM, queued Maven.
The earlier run also passed census (6) and the existing S3KL MGZ platform (5).
It had five test-fixture errors (the mocked level hid the injected manager),
which the final run resolved without changing gameplay behavior.

The Act-2 seeded frontier remains 1256, first mismatch at native row 21029:
player X `$697/$696`, camera X `$5F7/$5F6`; Y/rings agree. Cold entry remains
frontier zero because its scripted arrival is missing. This is unchanged
ratchet evidence, not a full route pass.

Fresh schema/inventory/architecture checks pass with 1245 total / 1005
isolated / 240 graph-covered / 0 missing codec. The profile guard found `$53`
listed separately in each half although it is now concrete everywhere; move it
to `SHARED_IMPLEMENTED_IDS` and rerun that guard. Counts are now 354/365 and
489/494, leaving lift pads `$4E`, tunnel launchers `$57` and the act bosses.
The reverse-gravity reference inventory is 94 covered / 4 partial / 14 missing /
4 n/a: group J is complete, while the eleven A-I rows and three boss rows remain.
Cold routes, native comparisons, donor/roster breadth, final-boss flow and
campaign integration remain outstanding.

The repaired profile guard passes both checks; combined fresh inventory/schema/
architecture/profile coverage is eight checks, zero skips. No further engine
change was needed. External videos 081–085 document the capture attempts:
neutral Act-1 entries encounter torpedoes/spikes (081 dies at frame 327; 082/083
survive their budgets but take damage). Controlled vertical input 084 holds
Sonic against the belt; its shared excerpt is frames 0–179, three seconds with
zero hurt/death. The full 420-frame source encounters a torpedo at 209 and dies
at 384, and remains explicitly outside a completed-route claim. The script is
`17 -; 43 R; repeat 180 { 1 R; 1 - }`, round-tripped by InputLogAuthorTool.
Act-2 horizontal clip 085 records 300 frames, 43 hurt rows and zero death rows;
its torpedo hazard remains visible. Both shared videos fully decode and their
mechanism frames were inspected. These are component demonstrations, not clean
full routes. The original conveyor-pad stash is accounted for by this commit.

### DEZ lift pads (after `ffe39535a`, 2026-09-23)

Implemented SKL `$4E` from `Obj_DEZLiftPad` `$47378`, `sub_4748E` and
`sub_4757A`; the S3KL CNZ wire cage remains unchanged. P1 standing/debug gates,
word acceleration and angle, orientation bits, 30-frame held return pause,
independent forward draw slot, original-anchor cull and graph recreation follow
the disassembly. Art uses three ROM frames at `$47614`, DEZMisc2+$6, palette 1.

Two rejected interpretations matter. `$24/$26` in the arm are multisprite sub4
coordinates, not delayed joint storage. The completed joint loop already wrote
link two there; the following moves reorder the sprites. The constants table
and loop order killed the apparent one-frame lag model. Allocation failure also
cannot simply leave the pad at its anchor: zero `$3E` points at ROM, whose `$16`
vector word is `$200`. Ignored ROM child writes still leave the parent displaced
by 513 links. The regression checks `$600` becomes `$E5D0`, no child and no retry.
Both lessons are recorded in the mirrored ROM pitfall catalogue.

Validation in `.worktrees/ai-sk-zone-completion`, Java 21, absolute S3K ROM:
`maven_queue.py -q -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen
-Dtest=TestS3kDezLiftPadHeadless,TestS3kDezPlacementCensus test` completed after
the allocation correction: 13 tests, zero failures/errors/skips. Earlier focused
runs passed the seven lift checks plus 78 PLC and two corruption checks (87),
and 22 CNZ cage alias checks. The separate schema/profile guard run passed eight
checks with inventory 1247 total / 1007 trivial / 240 stateful / zero remaining.
No fields or registration changed after that guard run. This is focused local
validation; combined campaign validation and integration are still pending.

Actual DEZ1 lift entry `$498,$708`, Sonic solo, seven rings: 40-frame landing,
140-frame ride, forced parent/arm recreation, forward replay and paused jump
release are covered at 320/800. External clips
`$HOME/Videos/OGGF/s3k-dez-bring-up/086-lift-pad-320/capture.mp4` and
`087-lift-pad-800/capture.mp4` each contain 420 frames / seven seconds, no hurt
or death rows, and identical player position/velocity rows across widths.
Both fully decoded; ride/pause/release stills inspected. Authored input is
`180 -; 1 R+A; 59 R; 180 -`. Normal-path footage predates only the allocation
failure correction. No cold route, native parity or breadth certification is
claimed. Census is now 361/365 and 489/494: `$57` and the act bosses remain.

### DEZ light tunnels (after `408b95256`, 2026-09-23)

Implemented SKL `$57` and its independent controller, trail spawner and ring
sprites from `$481F2`–`$488BC`. The S3KL MGZ trigger-platform factory is retained.
Countdown only starts numerically for P1; P2 alone waits. A failed controller
allocation still consumes the countdown while captured players remain locked.
ROM paths, scaling/wait tables, eleven launcher mappings, fourteen ring mappings
and sixteen animation pointers are loaded through the ROM reader. Setup waits
11 player passes but starts the trail immediately; circle/sine centres occupy
`x_pos/y_pos` fractional words and persist into following straight segments.
This is why a separate idealized geometric interpolation was not used.

The generic automatic tunnel was inspected for its straight movement contract;
its hardcoded paths/reverse handling do not cover the DEZ curve descriptor
format. The trail was first given a hand-written rewind override; the architecture
guard rejected new overrides. Replacing that with a plain `BodyState` holder
uses the existing schema without changing the guard baseline. Final inventory
is 1251 / 1011 / 240 / zero no-codec, with exact controller-to-trail identity.

Commands in `.worktrees/ai-sk-zone-completion`, Java 21, absolute S3K ROM:
- `maven_queue.py -q -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen
  -Dtest=TestS3kDezTunnelLauncherHeadless,TestS3kDezPlacementCensus test`:
  initial 11 checks passed, no skips.
- Expanded tunnel checks, `TestSonic3kPlcArtRegistry` and `TestS3kDezColdRoutes`:
  89 checks passed, no skips. The command also named a nonexistent
  `TestSonic3kPlcArtCorruption`; that name executed no tests. The correct
  `TestPatternSpriteRendererCorruptionGuard` was run explicitly next.
- `-Dtest=TestS3kDezTunnelLauncherHeadless,TestPatternSpriteRendererCorruptionGuard,TestS3kMgzTriggerPlatformObject`:
  22 checks passed, no skips, including the first circle's exact fraction and
  signed velocity writes. The final plain-state rewind adjustment reran all nine
  tunnel checks, including release and exit replay, with no failures/skips.
- Fresh `-Pguards -Dtest=TestRemainingRewindTailInventory,TestRewindFieldDispositionGuard,TestRewindArchitectureGuard,TestSonic3kObjectProfileRegistryGuard`:
  eight passed after removing the custom rewind overrides. No guard baseline
  was weakened; only the four new object classes changed the inventory totals.

The seeded Act-2 frontier remains 1256: native row 21029 expects X `$697` and
camera `$5F7`, versus `$696/$5F6`; Y `$3CE`, camera Y `$36E` and five rings agree.
Cold Act-2 entry remains zero because the miniboss transport is not implemented.
These frontier checks passing means the recorded limits were retained, not
that the cold route is complete. Placement census is 364/365 and 493/494.
Both act bosses and final-boss flow remain unimplemented.

External footage under `$HOME/Videos/OGGF/s3k-dez-bring-up`:
088/089 are matching 900-frame circular-route segments at 320/800, no hurt/death,
but stop just short of release. Final 090 runs 1080 frames / 18 seconds through
the exit at 800, zero hurt/death; full decode and circle/exit stills inspected.
091 shows the upward sine path from `$2C00,$690`, 600 frames / ten seconds;
a post-release hazard hits at frame 539 (23 hurt rows, no death). Its fully
decoded eight-second `traversal-excerpt.mp4` contains frames 0–479 and no damage.
Neutral input, Sonic solo and seven initial rings throughout. All eight ROM
paths reach their endpoints in focused checks, but all seven cold entries,
native per-mode cadence and character/donor/team breadth remain open.

### DEZ miniboss preparation and campaign guard corrections (after `a427d8a03`)

The Act-1 encounter remains unregistered. Its two-phase root, orbiters, platform
cross-links, laser and surviving transport are still required; the placement
census and cold DEZ2 frontier have not advanced. The expanded encounter oracle
is in the DEZ bring-up plan. Supporting code now supplies the 39-frame
level-backed sheet, real KosM submission/claim and physical DMA, the ROM attack
palette script, and the independently collidable eye. Eye collision flags and
publication are separate, as in `Add_SpriteToCollisionResponseList`; phase-change
immunity must not erase the underlying `$17` collision byte.

Focused checks in the task worktree, Java 21, queued Maven with `-Dmse=off` and
absolute ROM paths:

- `TestDezMinibossResources,TestDezMinibossPaletteState,TestSonic3kPlcArtRegistry`:
  84 passed, no skips. All 177 tiles upload byte-for-byte; palette callback is
  pass 321 after forty eight-pass rows, with disable/restart and restore checks.
- `TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestPatternSpriteRendererCorruptionGuard`:
  61 passed, no skips (the loading selector matches two classes).
- The eye's object-manager recreation test initially exposed an omitted parent
  link. An exact `CAPTURED` policy fixes it; all five eye checks pass, including
  forced parent/eye recreation and 32-pass replay. The test parent is a minimal
  shell, not the unfinished boss root: this is not the whole-encounter graph gate.
- Combined affected eye/lift/lightning/torpedo/tunnel, LRZ boulder/flame and SSZ
  GHZ/MTZ/Mecha checks: 112 passed, no skips. The lift check also asserts that
  cleanup retires its arm while preserving the placement's respawn decision.
- Broader structural selection exposed campaign omissions that earlier focused
  profile-registry checks did not cover: explicit touch-profile declarations
  missing on three existing collision objects, nine raw destruction calls, and
  one raw forward allocation in SSZ launch crumble. These now use the existing
  profile mapper and lifecycle operations, without relaxing guard budgets.
  Crumble retains successive forward slot searches and same-sweep publication.
- Final `TestObjectPhysicsStandardizationGuard`: all 33 checks pass. Rewind
  inventory passes at 1252 total / 1012 isolated / 240 graph-covered / zero
  no-codec; architecture (4), field disposition (1) and coverage (1) also passed.
  After the crumble edit, all 24 `TestS3kSszMechaSpawnHeadless` checks passed again,
  including complete launch to DEZ and recreated launch graphs. No skips.

These are focused checks, not the outstanding combined campaign delivery run.
No new boss video is claimed: its production encounter is not connected yet.
The lifecycle/profile corrections preserve the recorded presentation; existing
video links remain the relevant visual evidence for those completed slices.

### DEZ miniboss child graph preparation (after `595ec0cd9`)

A6 is still unregistered: these are components, not a completed encounter or
cold-route advance. ROM-backed orbiters, eight-way fragments, explosion
controllers, chained platform arms/spikes, beam/feet and three debris roles now
exist. The arms rewire after 32 own entries; both initially point `$44` at the
root, and a missing second arm deliberately leaves that alias in place. Extended
orbits accelerate the root angular word in both arm slots. The beam damages
native P1/P2 directly, with a half-open centre rectangle and standing/immunity
exemptions; it is not a touch-list enemy. Eye and arm flicker culling now publish
both native death status and the child-cleanup bit before deferred deletion.

Queued Java-21 Maven with `-Dmse=off`, absolute S3&K ROM path and selectors
`TestDezMinibossBeam,TestDezMinibossHazards,TestDezMinibossArm,TestDezMinibossEye`
completed **24 tests, zero failures/errors/skips**. This covers every orb-burst
capacity prefix, arm/spike and beam-foot prefixes, actual platform riding and
airborne defeat release, both angular alignment directions, beam charge/growth
and direct damage boundaries, native explosion count/RNG failure semantics,
stationary cover delay and first-pass spark/body behavior. Real ObjectManager
recreation/replay covers arm chain and settled cross-links, orbit/launch/burst,
beam charge/growth/live feet. Parents are test shells, not the absent root.

Initial test invocations had two compilation-fixture errors (slot-interface
cast and checked ROM accessor), a nonexistent test-service builder, an incorrect
expectation that the second orb would be visible behind the centre, and an
arm clock check that counted a load setup pass. Fixed the test assumptions from
the ROM and completed setup before counting encounter entries; no ROM timers
were tuned to fit those tests. Production support classes compiled successfully.

Fresh `-Pguards` selection of `TestObjectPhysicsStandardizationGuard`,
`TestRemainingRewindTailInventory`, `TestRewindFieldDispositionGuard` and
`TestHelperStateRewindCoverageGuard`: **36 passed, zero failures/errors/skips**.
The verified inventory is 1260 total / 1020 isolated / 240 graph-covered / zero
no-codec. This is focused validation, not the campaign's combined delivery run.

The first-defeat oracle also needed a correction: ROM bytes at `$7EE70` are
`61 00 00 02`, a `bsr.w` whose target is its return address `$7EE74`. The helper
therefore executes twice, making two independent explosion-controller attempts.
The root must reproduce both; component tests do not yet prove that chain.
Root arena/health dispatch, mask, sign/results, surviving transport, seamless
act change and complete native/wide moving captures remain open. No new gameplay
video or whole-boss certification is claimed.

### September 23: DEZ A6 root and continuous Act-2 transport

Development follows `5c0483e74` in `ai-sk-zone-completion`. SKL A6 is now
registered, completing the 365/365 Act-1 placement inventory while A7 remains
Act 2's one placeholder. The root connects the existing eye/orb/platform/beam
components, both eight-hit phases, first-defeat double explosion allocation,
second-defeat sign/results slot, surviving transport, physical resource queues,
synchronous reload and retained Plane-B redraw. Native player targeting remains
P1-only where the ROM calls Find_OtherObject; the beam checks native P1/P2.

Focused verification (all Maven calls through `tools/testing/maven_queue.py`,
Java 21, `-Dmse=off`, absolute `$HOME/code/projects/OpenGGF/s3k.gen` for
ROM checks):

- `-Dtest=TestDezMinibossEncounter,TestDezMinibossTransport,TestDezMinibossArm,TestDezMinibossBeam,TestDezMinibossHazards,TestDezMinibossEye test`:
  39 cases, 38 passed and one allocation-prefix fixture error (reused full KosM
  FIFO across cases). Giving each prefix its own gameplay session and rerunning
  `-Dtest=TestDezMinibossEncounter#initialEyeAndOrbAllocationsKeepEveryAvailablePrefixWithoutRetry test`
  passed the remaining case; no failure or skip remains in this focused set.
  The connected regression covers left/centre/right finishes through released
  Act-2 control. Child checks retire the newly registered placement before their
  intentionally isolated graph probes.
- `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestSozHyudoroTitleLifecycle,TestS3kDezSeamlessActChange,TestS3kDezPlacementCensus,TestS3kSharedBossCameraGate test`:
  153 tests, zero failures/errors/skips (both classes named TestSonic3kLevelLoading
  selected). Includes the mandatory AIZ/loading/bootstrap/decoder checks and
  unchanged SOZ title lifecycle.
- `-Pguards -Dtest=TestObjectPhysicsStandardizationGuard,TestRemainingRewindTailInventory,TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard test`:
  36 tests, zero failures/errors/skips. Inventory is 1265 total / 1025 isolated /
  240 graph / 0 without codec; no guard allowance increased.
- The combined selection from campaign base `c91fd5ac70aad2c6cd73dcfc3525d962a45ef4d5`
  now selects 2846 ordinary classes and all guards, full=true. This was a plan
  inspection only; final combined validation and main-workspace integration/push
  remain owed for the campaign.

Controller evidence: 098 (800px) and 099 (320px), outside the repository under
`$HOME/Videos/OGGF/s3k-dez-bring-up/`, replay the same authored solo log for 4000
frames. Declared positioned start `($3740,$2E0)`, 200 rings and all Super Emeralds.
Both phases are hit through normal touch handling; both runs reach results,
reload, floor collapse, spin/launch, landing and released control with zero hurt
or death frames. CSV confirms no follower. Full movies decoded; stills at entry,
phase two, results, floor collapse, launch and landing inspected. The wide
42-second excerpt is source seconds 12–54. This is positioned Hyper evidence,
not cold traversal, ordinary-Sonic fight completion or native-clock matching.

The first width-dependent player-state difference is the resource-gated reload
(1653 wide / 1652 native). Results children remain visible longer in the wider
viewport (`S3kResultsElementObjectInstance.isWithinRenderWindow`), adding eight
passes before transport (landing 2964 wide / 2956 native). The one-pass queue
readiness difference remains unmeasured against a native duration oracle; no
artificial wait was added. The two fight phases have identical player state
before that reload boundary.

Rejected evidence and corrections are in the owning plan: low positioned entry
that never crossed the camera gate; root gate missing native widescreen framing;
invisible transport incorrectly inheriting world-offset participation; a locked
walk missing its forced-input owner; and a temporary direct-session probe using
literal "none" instead of empty sidekick code, silently creating a follower.
The latter explains the initially non-reproducing input replay and is added to
the measurement-hazard catalogue. Cold route, native comparison, donor/roster
breadth and connected defeat allocation-exhaustion tails remain open.

### DEZ Act 2 boss foundations after `81768e6ee` (2026-09-23)

Reviewed the entire `$7F06C–$7FD28` encounter and its allocation, angle, range and
wait helpers before registering `$A7`. The plan now records damage authority,
child topology, independent allocation prefixes and defeat publication. Native
health is published by a released enemy, never by ordinary player attacks.
`Check_InMyRange` is centred on that enemy (signed, asymmetric endpoints), and
`Wait_NewDelay` inherits the root's current timer on the killing hit. These are
explicit implementation constraints for the remaining encounter.

Prepared the forty-frame runtime ROM sheet and real PLC/module queues, the
tracking bumpers, and the eight-hit/flash state. Commands use Java 21 and
`python3 tools/testing/maven_queue.py -Dmse=off`, with the absolute existing ROM
passed as `-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen` for ordinary checks:

- `-Dtest=TestDezEndBossResources test`: 2 passed, zero skips, including every
  uploaded pixel and own prepared-job claim.
- `-Dtest=TestDezEndBossBumper test`: 6 passed, zero skips, including real manager
  capture/remove/recreate and forty-pass forward replay of the shared parent.
- `-Dtest=TestDezEndBossDamageState test`: 4 passed, zero skips; helper state and
  palette publication, not an integrated fight.
- Focused structural checks covered physics ownership, helper state, field
  disposition and inventory. Initial inventory expectation incorrectly counted
  the bumper as graph-only: the probe also passes in isolation. Correct inventory
  is 1266 total, 1026 isolated, 240 graph-covered, zero missing codecs; no failure
  allowance was raised.

The placement remains a placeholder until enemy/shield/debris/exit integration
is ready. No new movie or native parity claim accompanies this preparation.
Full campaign validation, destination integration and push remain pending.

The resource/loading regression selection
`-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kPlcArtRegistry test`
passed 137 tests with zero skips. The four-class guard selection ran 36 tests:
35 passed before the inventory correction, and the corrected inventory passed
its focused rerun. No executable behavior changed after those checks.

### DEZ Act 2 shield and enemy components after `a88f2f756`

Implemented the shield with its independent visor and the released enemy with
its three projectiles. The shots rewrite their parent from enemy to root on the
initial nondrawing pass. Allocation keeps every available forward prefix and
never retries a missing suffix. The floor/ceiling probes use the injected level;
the new upward-probe overload delegates to the existing native algorithm.

Java 21 queued focused commands, ordinary checks with the existing absolute
S3K ROM property:

- `-Dtest=TestDezEndBossShield test`: 5 passed, zero skips.
- Initial `-Dtest=TestDezEndBossEnemy test`: 7 passed, zero skips.
- Source review then corrected projectile offscreen removal to the ROM's delayed
  `Go_Delete_Sprite` (root death still deletes immediately), with a regression.
- Final `-Dtest=TestDezEndBossEnemy,TestObjectTerrainUtils test`: 18 passed,
  zero skips (9 enemy + 9 terrain). Includes all four burst allocation prefixes.
- `-Pguards -Dtest=TestObjectPhysicsStandardizationGuard,TestRemainingRewindTailInventory,TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard test`:
  36 passed, zero skips. Inventory is 1270 total / 1030 isolated / 240 graph /
  zero missing codecs. No guard allowance changed.

No integrated boss, cold route, native cadence or media claim yet. The next
work is the root's launch/camera and Robotnik/door/defeat publication chain.


### LRZ runtime miniboss art queue (after `6b016ccc5`)

The actual miniboss previously entered ART_DELAY without submitting art. Its
new captured art helper queues $16FCDA -> tile $3FB and the raw boss explosion
PLC, preserving the native 48-update delay. 108 focused resource/boss/rewind/art
checks and three separate structural checks passed with no skips. Exact commands
and the synchronous-Nemesis limitation are recorded in the LRZ plan.

Two new external `miniboss-art-queue-20260923-{320,800}` captures replay the same
arrival input for 600 frames, starting at ($2C00,$600), 355 rings, solo Sonic.
Both reach the camera-locked, unrolled-arm fight. The 320px CSV has zero hurt or
death frames; the 800px CSV has 88 hurt frames, zero death frames. This is a
positioned visual check, not equivalent combat trajectory or cold completion.
Both videos decode successfully; arm/hand scenes at frames 380/500 were inspected.
The larger viewport's combat difference remains uncharacterized.


### LRZ arrival priority correction (after `5b1f04e39`)

User review correctly identified Sonic behind the lava in the preceding arrival
videos. Those starts skipped the `$2BA0,$750` path switch, leaving fresh-load
low priority. The ROM and existing engine set high priority only on crossing;
no renderer/priority behavior was changed. `GameplayCaptureSession.state.csv`
now exposes `high_priority`. Nine switcher tests pass, zero skips, via
`python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestPlaneSwitcherStateIsolation,TestSlotOwnedPlaneSwitcherDispatch test`.
New regression covers the omitted interaction, right/left crossings and
switcher-latch restore/forward replay; no native parity claim.

Replacement `$HOME/Videos/OGGF/lrz-bring-up/miniboss-priority-crossing-20260923-320-v2/capture.mp4`
starts at `$2B70,$750`, 355 rings, same 95R/30L/neutral input. Its CSV changes
priority 0 to 1 at frame 32 (`$2BA1,$7AF`) and retains it. At 320 px it reaches
the locked arena and visibly renders Sonic above the lava; 43 hurt frames,
zero death frames across 600 steps. An 800px companion at
`miniboss-priority-crossing-20260923-800` confirms the same priority transition,
but this shorter approach does **not** reach the existing raw-X camera gate:
last camera X 11073, no fight arrival claim. Both are positioned captures.
This further motivates the requested shared centering treatment for LRZ1 and
DEZ2. It remains pending; moving raw camera bounds would incorrectly move
player boundary walls too. See the LRZ plan's centering follow-up.


### Shared native-window arena framing (after `c4cf93390`)

User requested centering LRZ1 and DEZ2 while preserving the original behavior
in source comments. The internal zone policy now projects camera view limits
through `NativeViewportFraming`; native boundary words remain authoritative for
player walls. Captured zone state owns the opt-in and LRZ carries it across the
act rebase. ROM lock sources: `word_784E8`, `word_7F0C6`. DEZ's native-framed exit
writes now retain native min-X rather than shifting its gameplay wall.

The first wide LRZ movie revealed that arm anchors also used raw camera X;
reject that `miniboss-centered-20260923-800` movie. The `-v2` replacement restores
native arm world positions and keeps projectile/debris native lifetime windows.
At 320px all 600 recorded CSV rows equal the pre-change capture. At 800px all
non-camera fields equal the 320px rows, and every camera X is exactly 240 less.
Both the final wide LRZ and DEZ opening videos decode and inspected stills show
the intended centering. DEZ's `dez-end-boss-solo.txt` replay produces eight real
hits, defeat at 6344, exit code $7F2FE at 6787, and zone 23 load after 6837 steps;
zero hurt/death rows. Capture setup remains positioned, not cold/native parity.

Focused queued Maven commands, all `-Dmse=off`, ROM path root `s3k.gen`:
- `-Dtest=TestNativeArenaCameraFraming,TestCamera,TestLrzMinibossInstance,TestLrzPostDefeatCameraRelease,TestDezEndBossEncounter test`:
  82/83 passed; the new arm test omitted the parent's same-frame dispatch.
  Correcting its setup and rerunning `TestLrzMinibossInstance` passes all 25.
  The preceding DEZ assertion correction reflects the intended native wall,
  not an engine workaround. Final production code passes all 83 distinct cases.
- `-Dtest=TestLrzPostDefeatCameraRelease,TestS3kLrzSeamlessActChangeHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
  65 passed, zero skips, including the added wide release threshold case.

The shared camera edit requires normal combined delivery validation; these are
focused checks. DEZ final-hand work remains separate and uncommitted. Full
campaign integration/push and remaining level obligations are still open.

Structural follow-up: `-Pguards
-Dtest=TestObjectPhysicsStandardizationGuard,TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard test`
passes 35 checks, zero skips. The object inventory is intentionally left for
the separate, still-uncommitted DEZ hand graph; no guard allowance was changed.


### Final DEZ hand/finger component (after `e76abdb1b`)

Completed the saved hand controller and finger graph against `loc_80B22..80D64`.
The new retirement scenario reproduced a rewind capture error from an already
unregistered parent. Dying fingers now retain the native SST slot address,
release their obsolete identity link, read cleared/reused slot positions and
complete their timer after restore. No parent lifetime was extended to hide it.

Queued commands in the task tree, `-Dmse=off`, absolute root `s3k.gen` supplied:
- `-Dtest=TestDezFinalHand test`: five passed, zero skipped, after the reproduced
  reference error was corrected. Covers allocation prefixes, damage, open phase,
  recreation, parent retirement and slot reuse.
- `-Pguards -Dtest=TestRemainingRewindTailInventory,TestObjectPhysicsStandardizationGuard,TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard test`:
  36 passed, zero skipped. Measured inventory is 1279 total / 1039 isolated /
  240 graph / zero missing codecs. Two new concrete classes pass their probes;
  no exception bucket changed.

This graph remains unregistered until the rest of the final boss and its event
surface are connected. No live route, visual or native parity claim is made.
Next: actual root/core/button/mouth/beam/fireball/escape graph and connected
screen/plane pipeline; campaign-wide remaining zones and delivery stay open.

### 2026-09-23 — Final DEZ core checkpoint after `6e0316523`

Added the eight-hit core and captured mouth-status byte. ROM `loc_804F0`,
`sub_8119A` and `sub_8125C` own the phase/damage ordering; `loc_80584` waits
for root control bit 4 rather than defeat status bit 7. Five focused ROM-backed
`TestDezFinalCore` tests passed, zero skips, through queued Maven with the
absolute root S3K ROM property. Coverage includes exact flash words/timing,
P1/P2 credit, closed/open collision publication, single score/timer defeat and
pending-hit graph reconstruction/replay. Tests call attack callbacks directly;
this is not a controller-driven encounter or rendered evidence.

Separate queued `-Pguards` selection of `TestRemainingRewindTailInventory`,
`TestHelperStateRewindCoverageGuard`, and `TestRewindFieldDispositionGuard`
initially passed the latter two and reported only the new class count. Measured
inventory: 1280 total / 1040 isolated / 240 graph / zero missing codecs, no
exception growth. After updating the count and header, all three passed with
zero skips. Commands ran in the task tree based on `6e0316523`; combined campaign
validation/integration/push remain pending. The core is unregistered, and the
final encounter remains incomplete; continue with button/mouth/beam, fireball,
root, escape and connected arena/rendering before route validation.

### 2026-09-23 — Final DEZ mouth/beam checkpoint after `e6fa6d95a`

Implemented `$80590` button, `$8060C` mouth, `$807BC` charge/laser/release and
`$808AE` particles. Explicit parent/root/mouth rewind links and scalar particle
SST address preserve both active and retiring graphs. The mouth uses native
level tile `$001`; laser publication uses the existing retained-plane consumer
contract, which is still not connected to the live arena. No new movie/native
pixel claim, and no final-boss completion claim.

Queued commands in the task tree based on `e6fa6d95a`, with explicit absolute
root S3K ROM property and `-Dmse=off`:

- `-Dtest=TestDezFinalMouthSequence,TestDezFinalCore,TestSonic3kPlcArtRegistry test`:
  89 passed, zero skips (initial six mouth checks, five core, 78 art).
- `-Dtest=TestDezFinalMouthSequence,TestDezFinalHand,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
  73 passed, zero skips, including nine extended mouth/beam tests. An intervening
  test-only compile failure used int rather than short mocked coordinates and
  an incorrect record accessor; both were corrected before this completed run.
- Separate `-Pguards` helper-state and field-disposition checks passed. The
  inventory check initially reported the expected four new isolated passes:
  1284 total / 1044 isolated / 240 graph / zero missing codec and no exception
  growth. After updating the measured count/header, its focused rerun passed
  one case, zero skips. No repeated helper/field run was needed.

Coverage includes native P1/P2 laser centre boundaries, charge allocation-failure
register behavior, all laser publications, delayed release, mouth closure,
active graph restoration and particle retirement/replay. These are overlapping
focused selections, not a broad campaign pass. Next: root/emerald/fireball/escape
and production screen/plane integration, then actual entry-to-exit media and
remaining seven-zone acceptance. Integration/push/cleanup remain pending.

### 2026-09-23 — Final DEZ fireball checkpoint after `79b516486`

Added the invisible airborne/floor emitter and its three ROM-script flame
variants. Preserved the shipped undoubled table index at `$809A6`, the ground
transition's removed return address, allocation cadence, subtype-specific XY/X
tracking and native root-slot lifetime. No live registration or completion claim.

Task-tree queued `-Dmse=off -Ds3k.rom.path=<absolute root S3K ROM> -Dtest=TestDezFinalFireball test`:
five passed, zero skips. Separate queued `-Pguards` selection of
`TestRemainingRewindTailInventory,TestHelperStateRewindCoverageGuard,TestRewindFieldDispositionGuard`:
three passed, zero skips. The guard confirms the two added isolated classes:
1286 total / 1046 isolated / 240 graph / zero missing codecs; no tail exception
was added. These are focused checks, not campaign-wide validation.

Next dependency research found `DdzBossMasterEmeraldObjectInstance` currently
embeds its `$8141E` color scripts in Java. The equivalent DEZ `$813AA` scripts
must load palette bytes from ROM; reconcile the existing DDZ implementation
against the same runtime-asset invariant during that work. Native scripts use
shared `Palette_rotation_data`, so check overlap/reset ownership across root
emerald retirement and escape-ship emerald initialization before choosing the
state owner. This is a newly identified validation/implementation obligation,
not a claim that DDZ was fully verified before it.

### 2026-09-23 — DEZ emerald / DDZ ROM palette checkpoint after `ef83c4ff2`

Implemented body/escape emerald motion, visibility, control-bit retirement and
ROM-backed art registration. Two infinite palette script entries are captured
in each owning zone runtime, preserving the shared-cursor reset on a new emerald.
DDZ now reads its emerald script and boss-flash destination/color tables from
ROM rather than Java asset arrays. The DEZ root and art upload remain pending;
DDZ's already identified first-following-dispatch discrepancy remains an explicit
matrix obligation. No new route, renderer or native parity claim.

Queued Maven in the task tree based on `ef83c4ff2`, `-Dmse=off` and explicit
absolute root S3K ROM property for ROM-backed selections:

- Initial `TestDezFinalEmerald,TestSonic3kPlcArtRegistry,TestS3kDdzLifecycleProduction,TestS3kDdzCompatibilityMatrix`:
  art 78, DDZ lifecycle 2 and DDZ compatibility 34 passed with zero skips. Four
  of five new emerald cases passed; the fifth incorrectly assumed the fixture's
  first step dispatched the object once. Corrected the test to assert a direct
  falling update before fixture advancement; no timing change was made to pass it.
- Final `TestDezFinalEmerald,TestDdzRomPalettes`: seven passed, zero skips,
  including both script periods, pause/reset/rewind, actual DDZ owner and both
  flash rows/destinations.
- Required `TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
  59 passed, zero skips (both matching loading classes included).
- Separate `-Pguards` inventory/helper/field selection: three passed, zero skips;
  confirmed 1287 total / 1047 isolated / 240 graph / zero missing codec, no new
  exception bucket. All are focused checks, not the campaign combined suite.

Continue with root/escape and connected screen/plane entry, then moving evidence,
remaining zone obligations and combined integration/push/cleanup.

### 2026-09-23 — DDZ emerald exit edge after `ae36c875a`

Closed the motion obligation found in the previous palette review. Source
`loc_81D44` falls into `loc_81D4A` immediately; a new focused test first failed
against the prior implementation (expected X 9527, actual 9500). The production
update now applies wrap, player clamp and camera delta in that dispatch and
releases parent3's Java identity because following never reads it again.
The regression exercises zero and `$2000` wrap and captures/restores/replays
after removing the root and ship. Queued `-Dmse=off` with explicit root S3K ROM,
`-Dtest=TestDdzRomPalettes,TestS3kDdzLifecycleProduction test`: five passed, zero
skips. No shared algorithm or new state fields changed; broad campaign validation
and fresh route/media remain outstanding. Task tree based on `ae36c875a`.

### 2026-09-23 — Escape scenery/fade after `d1b2b3e2b`

Added native crane/debris lifetimes and art registrations, and parameterized the
existing shared `loc_85E64` fade by its caller-supplied `$3A` reload. DDZ defaults
remain unchanged; DEZ can supply 3. Explicit crane identity capture ends when
its wait begins; midpoint fade capture retains the selected cadence.

Task-tree queued `-Dmse=off` with absolute root S3K ROM,
`-Dtest=TestDezFinalEscapeScenery,TestSonic3kPlcArtRegistry,TestS3kDdzLifecycleProduction test`:
83 passed, zero skips. Separate queued `-Pguards` inventory/helper/field checks:
three passed, zero skips; confirmed 1288 total / 1048 isolated / 240 graph /
zero missing codec and no exception growth. `git diff --check` clean. These are
focused component checks; no complete escape, new capture or campaign delivery
claim. Continue with root/ship and pending production screen/plane integration.


### DEZ final escape decorations and persistent art jobs (after `389c43c76`)

Added the ROM head/flame dispatch and final-zone module-job ownership needed by
the retiring root. The source's head-end routine explicitly selects Robotnik's
five-tick raw script even for EggRobo mappings; preserve it and document it in
code. Flame init does not draw; V-int parity and parent X velocity gate later
drawing. Head control-bit retirement is deferred, while flame deletion is immediate.
Pending crane/debris module jobs now survive the root through captured zone state,
matching the native global FIFO's lifetime without introducing a readiness wait.
The production event/root/ship still must call that owner's service method.

Queued command, Java 21, root `s3k.gen`:
`-Dmse=off -Ds3k.rom.path=<root>/s3k.gen -Dtest=TestDezFinalShipDecoration,TestDezFinalArtState,TestSonic3kPlcArtRegistry test`:
82 passed, zero skipped. Coverage includes flipped head tracking, both raw scripts,
Knuckles upload, retirement rewind, flame motion/parity, restored FIFO drain and
all decoded crane/debris pixels. Initial compile accessor typo and direct-update
manager-sweep test assumption were corrected before this run. Final integration,
route/media, shared-camera broad validation, other-zone obligations and push remain
open; these components do not certify DEZ completion.

Focused rewind guards: helper-state and field-disposition checks passed; the
first inventory check identified its stale Java count (the text header was already
updated). After updating that count, the isolated inventory rerun passed:
1,289 classes, 1,049 isolated passes, 240 graph-covered, zero unaccounted classes.
All three distinct guard cases pass without skips; this is not a full guard sweep.


### DEZ2 fixed horizontal widescreen arena (2026-09-23)

User refinement: keep the wide boss camera centred horizontally regardless of
Sonic's X, retaining vertical tracking. Added an optional internal zone-owned
native X anchor and a shared midpoint calculation; DEZ captures `$3470` and
releases it at `$7F2DC`. Camera applies it above 320px only. Original locks and
custom rationale are documented in code. Focused queued command
`-Dmse=off -Ds3k.rom.path=<root>/s3k.gen -Dtest=TestNativeArenaCameraFraming,TestCamera,TestDezEndBossEncounter test`
passes 60, zero skips. The new 900-frame capture 105 decodes and was inspected;
X is `$3380` after entry, Y remains active. The first 700 rows differ from 104
only in `cam_x`. The unchanged positioned solo controller log completes eight
hits and enters zone 23 in 6835 steps, no hurt/death. The exit is two steps earlier
than with moving X; hit timing remains unchanged. Shared-camera combined validation,
campaign integration/push and full final-arena implementation remain pending.


### Final DEZ escape ship (after `294aff6ec`)

Ported the complete ship's dispatch through native exit selection, including
ROM-backed flash palettes, credited-player brake, native camera fraction/overshoot,
ordered children, release signals, explosions and exact fade completion. Added
captured zone words for the chase camera. The native forced fade overwrite targets
absolute SST 64; tests caught and fixed the assumption that failed spawn helpers
return null. Actual failed objects are destroyed and slotless. Native ROM behavior
and necessary engine adaptations are commented, including the Sega/title request.

Queued `-Dmse=off -Ds3k.rom.path=<root>/s3k.gen -Dtest=TestDezFinalEscapeShip,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passes 69, zero skips (10 ship, 59 required loading/bootstrap). Scenery 3 and DDZ
lifecycle 2 pass separately. `-Pguards` selection of remaining-tail inventory,
helper-state coverage and field-disposition passes all 3, zero skips; inventory
1,290 / 1,050 isolated / 240 graph-covered / 0 unaccounted. Initial graph tests
were corrected to use real SST occupants through restore and compare encounter
objects rather than also counting the fixed player effect installed at restore.
The root, screen/retained planes and live final route remain pending. Neither this
nor the separate DEZ2 camera fix completes the campaign or its integration gates.

An eleventh ship case then passed alone (zero skips): a real ObjectManager
sweep runs the forward head, crane and emerald on their allocation pass, with
the head still on its initial raw-animation frame. Total distinct ship cases: 11.


### MHZ matrix reconciliation (2026-09-23)

Added both act matrices and linked the coverage backlog. Inspected the original
completion audit, September 12 miniboss/transition follow-ups and current event,
boss and graph-test source. Preserve the explicit no-further-Knuckles and stopped
trace-work scope; these are not unsupported-configuration claims. Historical
focused passes remain attributed to their original candidates, and red/interrupted
full runs remain red/incomplete. No fresh MHZ run or full-act certification is
claimed. The matrices enumerate every standard obligation, breadth and rewind
gaps, including current controller routes and checkpoint/death lifecycle work.
Local links were resolved successfully; this is documentation-only progress.


### DEZ final main controller (after `3e63d37a7`)

Added the ROM's plane-owning root and its entry, walking, hands, exposed-core chase,
fatal callback and escape-ship handoff. It has no ordinary touch attack interface;
existing fingers/core publish the damage state. Native player word writes use
NativePositionOps and scripted control. Controller captures only its owned scalar
state; child references and pending art continue through established graph/zone
owners. Four new ROM-backed tests pass, zero skips; the existing hand/core selection
passes 10, and focused inventory/helper/field guards pass all 3. Inventory is now
1,291 total, 1,051 isolated, 240 graph-covered, zero unaccounted. The controller
has not yet been connected to ScreenInit or retained-plane rendering, so there is
no new final-arena movie or production-completion claim. The next integration
must preserve partial allocation and initialize boss position only on success.

A fifth controller case passed separately, zero skips: 0..4 available slots
preserve independent core/emerald allocation and the two-hand successful prefix,
without healing missing hands after capacity becomes available. Total distinct
controller cases: five. All local Maven work used the queue and the absolute
root locked-on ROM path; waits were for a confirmed live shared Maven run.

### DEZ final arena entry connection (after `72d4997ca`)

Connected the first pre-physics pass to native-ordered moving support, entry
support and boss allocation. Camera initialization survives allocation failure;
boss position is published only after root allocation. Captured initialization
state prevents duplicate allocation after rewind. The real first frame and
three-frame replay, plus all four allocation capacities, pass alongside existing
root/scroll checks (11 tests, zero skips). Remaining work includes retained-plane
rendering, layout setup, background updates, art servicing and the full route.

Validation follow-up: the combined `TestDezFinal*` plus mandatory S3K loading,
bootstrap, decoding and AIZ selection ran 143 tests, zero skips, initially with
two failures. The floor and retired-hand-slot fixtures manually construct their
encounters and were receiving a second production root on their first loop
step. Marking their component setup as already initialized preserves the
intended isolation; the independent production-entry test retains the real
initialization path. The repaired floor/hand/entry selection passed all 16,
zero skips. Commands used `tools/testing/maven_queue.py -Dmse=off` and
`-Ds3k.rom.path=$ROOT/s3k.gen`. This is focused validation; the change-based
plan selects the full ordinary suite and guards, deferred to combined campaign
validation after remaining implementation.

A production capture at native width ran 180 frames with neutral input and no
deaths, ending at Sonic `$360,$CD`, camera `$2C0,$20`. Video decode and still 90
were inspected. Durable work-in-progress files are under
`$HOME/Videos/OGGF/s3k-dez-bring-up/106-final-entry-wip-320/`. The planet/body
presentation is not certified: retained-plane rendering and subsequent event
updates still require connection.

### DEZ final live events (after `ddf517a54`)

Connected initial retained Plane A refresh and per-frame background stages,
collapse allocation, immediate no-redraw chunk writes, plane/camera words,
level-clock shake and global art-job claims. Added the previously missing DEZ
call to consume continuation rings/time at the first foreground event.
The ROM-backed controller route reaches window `$2C0`, stage `$10`, two hands
and six fingers in 410 frames, then reproduces state bytes and player position
after a 12-frame rewind replay. No test writes force the route stages.

The initial entry/background/carry selection passed 16 tests, zero skips. The
expanded `TestDezFinal*,TestLevelContinuationCarry,TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
selection ran 151 tests, zero skips, with one new-test timer expectation failure.
The restored bank was consumed correctly; LevelManager advances the running
timer later in the ordinary frame, so the completed-frame assertion is 12346
for a bank of 12345. No engine timer adjustment was made. All local Maven calls
use `tools/testing/maven_queue.py -Dmse=off` with the absolute root S3K ROM.
The change-based plan still selects the full suite and guards; combined campaign
validation remains pending after implementation.

A 420-frame right-input diagnostic capture completed without hurt/death and
its video decoded successfully. Still 380 shows the renderer still reading the
ordinary layout instead of the retained boss plane. Capture 107 is explicitly
WIP, not matched presentation evidence. Raw-map inspection corroborates the
separate FG body windows and BG sky/floor layout; details are in the DEZ plan.

Concurrent upstream inspection (2026-09-23): main `develop` is now `40d55783c`,
including `c122066f8` (reverse-gravity player render flip) and its Hyper-trail
follow-up note. The campaign tree is still based on `c91fd5ac7`; do not overwrite
these upstream changes during integration. Main's current full ordinary suite
is live and holds the Maven queue while the campaign's focused checks wait.
No main-workspace files, branch, or active validation were changed by this
inspection. Refresh the destination and reconcile the gravity fix before final
combined validation; existing campaign captures predate that upstream fix.

Native-reference progress while validation waits: two read-only BizHawk movie
passes completed without host failures. The first saved final-DEZ entry frame
509032; the second captured hand/core/escape/fall screenshots with full VDP
dumps. Native pictures were inspected and the reference procedure, hashes and
limits recorded in the DEZ plan. This does not certify the engine renderer; its
new source remains queued for compilation and focused tests.


### Final-arena wide-entry regression (in progress)

The retained-render selection passed 37 cases with zero skips, and the isolated
counter restore selection passed seven. Capture 108 at 320px completed 1200 frames
without hurt/death and passed full video decoding; inspected frames show the
hand-phase body. Capture 109 at 800px instead died at frame 201 after scripted
control released. Its floor anchor consumed the visible camera left rather than
the native 320px camera word, moving support 240px left. The pending correction
projects presentation only and preserves native object/event offsets. Five-width
entry/rewind tests have been added and remain queued. The rejected wide capture
also repeats the planet; final-arena sky extension must avoid deforming its moving
floor. Neither capture establishes full fight, native trajectory or zone completion.


### Final DEZ rendering checkpoint and SSZ arrival verification

The body-margin/cache regression passed 22 cases without skips. Capture 112 at
800px completes 1200 frames without hurt/death, decodes fully, and preserves the
native 320px centre exactly at four inspected frames; added margins now show the
ROM body. The final direct-load controller frontier advances through all fingers
to seven core hits, then a fall. The fight, incoming/outgoing transitions and
full-phase native visual comparison remain open. No engine behavior was tuned
to make the controller succeed.

SSZ source reconciliation found existing act-2 arrival setup/release code despite
the slice-0 matrix's stale wording. New Knuckles native/wide arrival tests and the
existing act-1 checks pass eight cases without skips. They cover the rise and
mid-rise replay, not `loc_59078`, the crane or Super Mecha; those remain the next
substantive SSZ implementation work. Accepted trace/ending exclusions are preserved.


The subsequent normal-controller attempt lands core hit eight at probe frame
11806 and survives the ship chase through frame 14999. This advances the direct
final-arena route beyond the prior seven-hit fall; ship defeat/outgoing load and
incoming DEZ2 continuity remain open. No production gameplay edit was required.


Integration-base refresh (2026-09-23): fetched origin from the unchanged main
`develop` checkout. `HEAD...origin/develop` reports0/0 at `40d55783c`; the two
commits after campaign base `c91fd5ac` are `c122066f8` (reverse-gravity player
render flip) and `40d55783c` (Hyper-trail gravity follow-up documentation).
Main's dirty disassembly submodules, BizHawk archives and unrelated notes are
preserved. Campaign integration, updated-base validation and push remain open.


2026-09-23 SSZ follow-up: corrected the Mecha body's missing hardware priority
(`ObjSlot_MechaSonic` art bit15, independent of queue$280), and documented why
the high-island mask exposed it. Wide crane/transformation pans project native
camera progress without widening player bounds. An early Emerald visual uses
ROM art without changing native allocation/palette timing. Captured native
camera recovery also covers defeat bounds and explosion workers. The two cold
fights still reach the accepted pre-ending stop at320/800 and now assert the
real saved clear payload; the S3K save provider derives the clear flag from
captured SSZ state so rewind cannot leave a live one-way clear latch.

Focused commands in the campaign worktree (`b6c1147a2` plus the uncommitted SSZ
batch), Java21 and absolute S3K ROM: `TestS3kSszAct2FinalFight`2pass;
`TestSszAct2MechaEntry,TestSszAct2BackgroundPriority`16cases and
`TestSszCraneCameraPan,TestS3kSszCraneRouteHeadless,TestSszMechaDefeatRunner,TestSszZoneRuntimeState,TestS3kSaveSnapshotProvider`24cases.
Eight wide component cases initially retained a320px fixture; corrected
aspect/session setup reran both five-preset methods,10pass. This establishes42
distinct focused passes, no skips, not a full suite. Production sources compiled
successfully. Capture14 shows the pan/early emerald; capture15 the late fight.
Both videos decode fully and their CSVs contain no deaths. An explicitly
reframed still at7661 confirms the powered-down body draws over the island;
republishing the sprite table is necessary after a probe-only camera change.

The actual HPZ teleporter/load through SSZ rise/crane release and mid-rise
rewind/replay now passes five width cases. Checkpoint index-1 is the empty host
sentinel. Incoming whole-HPZ cold coverage and load-history isolation are still
distinct obligations; no trace or ending exclusion was revoked.


Final focused SSZ follow-up gates: `-Pguards` priority-bucket and service guards
pass14 cases; the corrected profile/registry guard passes2. Ordinary profile
checks pass7 cases after reconciling the explicit CNZ/SKL owner table with the
campaign's DEZ mechanisms/bosses and StartNewLevel. The failed intermediate
assertions were stale inventory expectations, not runtime changes. Updated
S3K loading/bootstrap/decoding/AIZ/object-recreation consumers pass1371 cases
with no skips. Combined campaign validation/integration/push remain outstanding.


Seeded SSZ2 stage$10/$14/$18 presentation now includes shared Emerald/water
palette counters, Pal_Ending1 and second redraw, native island ramp/band
composition, and retained-plane row streaming. Four isolated native arithmetic
samples match all1300 parameter/scroll bytes; the first wide capture exposed
missing `Draw_TileRow` consumption even though those arithmetic checks passed.
Added its captured drawing cursor and one/two incoming rows; focused
ending-plane/arrival/deformation selection passes39 cases, zero skips at18:34
BST. Capture17 shows water/cloud entry. Actual ending ownership is still
excluded; these declared presentation seeds do not extend the cold stop.


DDZ Super-form stars: implemented the omitted fixed-slot `loc_8242A/82452`
owner, distinct from existing Hyper stars. Native declared Super entry confirms
initialization/frame cadence and art$879C/queue$80; the star region matches2401
pixels after Genesis3-bit quantization in the800px capture. Corrected a DMA
word/byte intake mistake exposed by the mapping guard. Eight focused cases cover
all five actual viewport presets, release-boundary replay, cadence and wrap;
1313every-object recreation cases pass. Native320Super completes the recorded
route; the same inputs at800die at6340 and remain an open controller frontier.

The focused architecture guard found the campaign's new SSZ crane parent
sidecar/annotation. Replaced that redundant custom capture with the existing
central CAPTURED managed-reference policy; crane/full-fight replays and guards
are being rerun. No guard baseline was raised to exempt the new code.

Final combined focused replay selection (Java21/absolute S3K ROM,18:50 BST) passes15 cases without failures/errors/skips: DDZ native Hyper routes and320Super completion, eight star checks, SSZ crane route and both320/800full final fights. This includes the crane managed-reference correction. It is not the campaign broad suite.

The corrected crane ownership passes all6focused structural guard cases (priority, rewind architecture, coverage ratchet), zero skips. DDZ800failure is ring exhaustion after one body hit; the same inputs reach fight routine4 at3957/39rings versus native-width3806/77rings. Wider completion remains open.


### 2026-09-23 — fresh DDZ controller completion

At `b6c1147a2` plus campaign edits, independently authored Super Sonic inputs
now complete fresh level-select entry at both actual320/800 widths. The only
declared gameplay setup is seven Chaos Emeralds; there is no inherited V-int,
camera fraction, position, health or ring seed. The scripts and reproducible BK2s
are `routes/s3k/ddz-super-fresh-{320,800}`. Native320 reaches the ending request
after10396 capture passes with12rings; wide800 after9923 with20rings. Three
rings during the wide exit explain the earlier17ring observation at fade entry.
No production gameplay was changed to make these inputs complete.

`TestS3kDdzAuthoredRoutes` checks no death, the `$D01` request, and restore plus
45-input forward replay at first body damage, first chase wrap and exit. It
compares player/camera/object summaries, palette words and all DDZ runtime bytes.
Queued Java21 Maven `-Dmse=off -Dtest=TestS3kDdzAuthoredRoutes test`, with the
absolute S3K ROM, passes2cases with zero failures/errors/skips at19:08 BST. The
first diagnostic failure was a null-spawn fixed-object summary; the next was
the stale17ring endpoint expectation. Neither required a gameplay change.

This closes fresh native/wide controller completion, independently of the
seeded Hyper movie parity result. It does not close strict trace bootstrap,
full incoming DEZ2 continuity, Hyper/HUD presentation, or load-history isolation.
The wide `campaign-20260923-fresh-completion-800` recording has9923 state rows,
zero deaths/followers and1673 images (8250..9922); full video decode passes.
Visual inspection exposed intermittent background wrap seams; diagnosis and
corrected presentation evidence are recorded separately.


### DDZ widescreen background wrap seam (2026-09-23)

The fresh800 movie exposed intermittent one-column black lines in cloud bands.
A no-gameplay-step render isolation removed the foreground plane through the
mutation surface: the line remained at x654, y144 onward. The source FBO had
valid cloud pixels in columns0/511 and transparent, unrendered column512; its
allocation was800 while the rendered period was512. The live HScroll word for
that band was1166. Rounding the decoded normalized R32F word before modulo
removes the seam. The shader now documents that the VDP supplies integer pixels;
widescreen allocation does not authorize fractional wrapping. No DDZ scroll
speeds, camera, gameplay bounds or ROM art were changed.

An isolated GL4.1 all-word texture test did **not** reproduce the original seam
(with224 or272 source rows and opaque or transparent unused columns). It is
retained as complementary wrap coverage, not claimed as the reproducer. The
actual DDZ session regression does reproduce it: at fresh route8400, a second
render compares cloud x142 with x654 and fails at y160 (cloud vs black). The
rounded shader passes the same check. This distinction avoids claiming that a
synthetic passing test explained the live render state.

The existing pixel-centre test failed on both rounded and original shaders:
background=true,320x224,pixel0,0 expected128/actual0. Its newly available column
remap samplers were left on the 2D background unit even while disabled. Binding
all 1D samplers to the test's 1D unit matches the production renderer. This is a
test-setup correction, not a second rendering workaround.


Corrected presentation verification: queued Java21 Maven with the absolute S3K
ROM, `DISPLAY=:0`, `-Dopenggf.test.gl.native=true` and
`-Dtest=TestDdzBackgroundWrapCapture,TestBackgroundScrollWrapPixels,TestShaderPixelCentreSampling`
passes3cases with zero failures/errors/skips at19:18 BST. The background tests
include all normalized scroll words -32767..32767 and the actual route render;
pixel-centre coverage includes native, integer and fractional scaling. These
are focused checks; the campaign's combined category/guard run is still owed.

`campaign-20260923-fresh-completion-800-wrap-fixed` supersedes the earlier
wide fresh-route movie. All9923 CSV rows are byte-identical to the original,
including zero deaths/followers and20 final rings. The1673-frame movie fully
decodes; stills8400/9000/9681/9922 were inspected. Controller source and provenance
are alongside the external video. No native whole-scene parity claim is made.


### 2026-09-23 — current MHZ component validation and cold input authoring

At `b6c1147a2` plus campaign edits, the37 non-trace MHZ-named classes pass498
cases, zero failures/errors/skips (19:21 BST, Java21, absolute S3K ROM). The
selection was generated from `src/test/java/**/*.java`: exclude paths containing
`/trace/`, retain stems containing `Mhz` or `MHZ`, sort stems, join with commas
and pass as `-Dtest` to queued Maven `-Dmse=off test`. This selects object, event,
scroll, animated-art, runtime registration and graph/held-state rewind checks.
It does not select shared badnik names or claim full route/breadth coverage.
An initial wildcard request was cancelled during compilation and replaced with
this explicit non-trace list before replay execution; trace scope stays excluded.

Fresh Sonic-solo input authoring reaches the Act1 pulley and subsequent traversal.
A repeated run/jump candidate cycled in a twisted vine; running through its entry
and using actual pulley grab/down-pull/release input advances beyond it. A second
right-only candidate bounced in the adjacent spring shaft because it skipped the
lift. Positioned screenshots were used only to inspect that geometry; none of
their state was inserted into the cold route. No runtime tuning is justified by
those controller stalls. Completion and a preserved final route remain pending.


### 2026-09-23 — fresh MHZ1 completion and delayed duplicate owner

`routes/s3k/mhz1-sonic-fresh-320.script/.bk2` preserves9301 controller passes
from normal locked-on level select, Sonic solo, donor off,320px. No position,
clock, emerald, ring or health seed is supplied. The route uses the actual
pulley grab/down-pull/release, sticky-vine spindash and terrain loops, reaches
the miniboss with47rings, lands all six hits, completes sign/results and moves
more than300px after the seamless Act2 handoff. There are no deaths. The
BK2 was compiled and round-tripped through the production authoring tool.

Input-authoring stalls were not runtime evidence: periodic jumps shed loop
momentum; running into the spring shaft skipped the adjacent pulley; the sticky
vine needs a spindash. A broad “near twisted vine” authoring heuristic also
suppressed a needed jump on a different vertical path and was removed. No
engine movement/collision or boss health was tuned to complete the route.

`TestS3kMhzAuthoredRoute` reached and replayed the pulley, vine charge, first
boss hit, last-hit approach and defeat. Its first post-load restore at frame9094
failed: the live object list contained the same Insta-Shield owner twice, while
restore retained one. The short `TestS3kFixedSstTransitionRewind` reproduction
now steps the player after manager replacement and fails in both MHZ and HCZ
(expected one owner, actual two). Earlier tests captured before this delayed
registration and missed it.

Root cause: `rebuildManagersForActTransition` already restores and reconciles
exact-SST owners. The subsequent `applySeamlessOffsets` marked Insta-Shield
unregistered for every policy except ALL_LIVE_SST, including PERSISTENT_EXACT_SST.
The next player update registered the identical object again. The flag reset
now applies only to legacy PERSISTENT_ONLY; both exact policies retain the
existing identity and their already-invalidated DPLC. The code explains that
ROM Load_Level retains the fixed shield slot outside Dynamic_object_RAM.
This is a shared policy correction, not an MHZ-specific exception.


Final focused verification at19:43 BST, Java21 and the absolute S3K ROM:
`maven_queue.py -Dmse=off -Dtest=TestS3kFixedSstTransitionRewind,TestS3kMhzAuthoredRoute,TestLevelSeamlessTransitionExecutor,TestFbzActTransitionHeadless,TestS3kLrzSeamlessActChangeHeadless,TestS3kDezSeamlessActChange test`
passes26cases, zero failures/errors/skips. This includes the full controller
route and45-input restore/replay at all six spots, the two delayed-owner
regressions, and neighbouring transition consumers. The earlier498-case MHZ
component selection preceded this one-condition shared-policy fix. Neither
selection is the campaign broad run.

The corrected external `mhz-bring-up/campaign-20260923-act1-cold-320-fixed`
movie covers frames7240..9300 (2061images) from9301ordinary input passes; all
rows have no death/follower. Its CSV is byte-identical to the pre-fix capture.
Full ffmpeg decode passes; fight, defeat and released-Act2 stills were inspected.
The input source/BK2 and provenance accompany it. Native visual parity, other
characters/widths/donors, full Act2 traversal and load-history isolation remain
separate obligations. No excluded Knuckles or trace work was reinstated.


MHZ2 entrance follow-up: controller continuation from the committed MHZ1
handoff reaches the wall at world `(1184,2016)` and stalls at player X1157.
`Obj_BreakableWall` selects `loc_21818` in MHZ; its native P1/P2 checks require
character ID2. Removing that restriction would be an unsupported route fix.
Backtracking reaches the caps at `(976,2000)`, `(832,1888)` and `(688,1856)`;
short jump pulses, late steering and cap overshoot explain several failed input
attempts. Neither these attempts nor positioned layout images establish a
collision defect. Full Act2 completion remains open. The coverage backlog now
links both MHZ matrices and distinguishes the completed Act1 route from incoming
Act2 reach. A native movie observation is being used to resolve the entrance
geometry before further implementation conclusions.


Required S3K consumers, 2026-09-23 at20:10 BST (`b6c1147a2` plus campaign
edits): queued Java21 Maven with absolute S3K ROM and
`-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
completed **59 tests, zero failures/errors/skips**. The selector includes both
classes named `TestSonic3kLevelLoading`. This is focused regression validation,
not the outstanding combined ordinary/guard run. Consumed Maven log removed.

Native entrance selection correction: the first observation selected movie
frame235951 from the `mhz_2` segment name. Segment suffixes are not act numbers;
the manifest's explicit one-based act field is1, and native RAM correctly
reported `$700`. This was investigator selection error, not replay drift or an
engine defect. The first explicitly Act2 segment, `mhz_7` at frame275484, starts after
a bonus detour at X4160; the actual seamless entrance is earlier inside the
continuous Act1 segment. Observe the native zone transition, not just labels.
No native observation is used to hydrate engine gameplay.


### 2026-09-23 — MHZ2 waiting cutscene and lift priority

Native movie observation supersedes the entrance-input hypothesis above.
`mhz-bring-up/native-act2-20260923/seamless-entry` contains2501 observations
at movie frames265500..268000 from the verified complete Sonic/Tails BK2,
BizHawk2.11/GPGX and the common native host. All three exploratory host runs
completed with no host failures; only this final window observes the lower
entrance and leaf blower. No RAM setup writes or native-to-engine hydration
were used. Frame265590 shows Sonic against the same X1157 wall, with Knuckles
on the far side; frame265950 shows the scripted lift through the foreground.
This is native event corroboration, not strict trajectory parity or act certification.

The engine deleted `CutsceneKnucklesMhz2Instance` whenever the activation
rectangle rejected its camera. `Check_CameraInRange` actually branches through
`loc_85C74` to `Delete_Sprite_If_Not_In_Range`: a nearby actor waits in its SST
slot. The placement window admits the actor before the rectangle opens, so the
unconditional destroy latched away the entire leaf-blower sequence. The port
now uses the shared coarse-X offscreen check while waiting, retains its slot,
and removes the unnecessary activation-time reallocation. An existing unit
test had encoded unconditional deletion; its corrected wait-then-admit assertion
failed before the fix (one failure, zero skips).

The first corrected route selection passed native320/352 but failed the400,
528 and800 entry rows: the wider camera follows farther left and never reaches
`loc_63170`'s raw `$3D0` before Sonic hits the wall. The controller now recovers
the original320px camera origin with `NativeViewportFraming` for both gate and
lock, and projects the display minimum back into the selected viewport. Inline
comments preserve the original thresholds, slot behavior and reason for the
widescreen adaptation. All95 focused cases then passed with no skips, including
five cold width rows, press/lift capture-restore-forward replay, and the existing
full incoming MHZ1 route. Its final assertion now allows the legitimate Act2
cutscene to own control; the earlier released-title replay spot still proves
that the seamless title handoff relinquished control.

Rendered follow-up caught a separate omitted source write: `loc_6339C` clears
vertical render flip and sets the high art_tile byte's bit7 (hardware priority),
then `loc_633D6` clears that priority on release. Without those writes, the player
vanished inside foreground rock during the lift despite passing state/route tests.
The lift now applies both writes; priority is included in the live replay
observation and asserted at lift/release. Pre-priority movies are diagnostic,
not the final presentation evidence. Full Act2 completion remains open; the
unmodified controller continuation now reaches X2082 beyond the lift.


The final lift review also corrected `loc_54E00`'s vertical-limit threshold to
use the recovered native camera origin. At800px the old visible origin stayed
below `$380`, retaining minimumY`$620` and letting Sonic rise offscreen. The
five entry rows now assert camera containment on every controlled lift pass.
`loc_54E3C`'s Events_bg+$16 gate preserves the cutscene-owned X minimum, and
`loc_633D6` keeps velocity on the carrier rather than writing it to Player_1/2.
The native observation has player y_vel=0 during the lift; the prior port wrote
the carrier's negative velocity into it. Release retires the carrier on its
first nonnegative velocity dispatch as the source does.

Final focused verification at20:31 BST on2026-09-23 (`b6c1147a2` plus campaign
edits): queued Java21 Maven with absolute S3K ROM and
`-Dtest=TestMhz1CutsceneObjects,TestS3kMhzCutsceneGraphRewind,TestS3kMhzAuthoredRoute,TestS3kMhzAct2EntryHeadless,TestSonic3kMHZEvents`
passes **157 cases, zero failures/errors/skips**. Earlier95-case passes were
superseded by the vertical-camera/ownership follow-up. The earlier59 required
S3K consumer cases preceded this localized MHZ correction; combined campaign
validation remains pending. Consumed red/intermediate/final Maven logs removed.

Final movies live at external `mhz-bring-up/campaign-20260923-act2-leaf-blower-320-final`
and `...-800-final`. Native320 is the actual fresh MHZ1 incoming route (9901input
passes, frames9100..9900 filmed); wide800 is a fresh MHZ2 level-select entry
(850passes, frames100..849 filmed). Neither has gameplay seeds, follower or
death. Both fully decode through ffmpeg. Reviewed frames show Sonic in front
of the rock during lift, camera tracking through the upper passage, and normal
priority after release. Inputs and class/input hashes accompany each movie.
Earlier unsuffixed and `-priority-fixed` captures are diagnostic/superseded.
Widescreen leaf particles still use the visible origin with the native320px
random span; centering that existing span without extra RNG is a presentation
follow-up. Complete Act2 traversal, broader rosters/donors and campaign delivery
remain open.


### MHZ2 upper route and press-controller retirement (2026-09-23)

Continued ordinary controller inputs clear the first large mushroom catapult
(base1952,1420, child2016) and the twisted-vine passage. The first apparent
spring stall at4469,1870 was an input approach: Sonic was on top of a left-facing
spring. Dropping to its side produces the expected leftward launch. Upper Act2
route authoring remains ongoing; this is not full traversal evidence.

That route exposed leaves following the camera indefinitely. The missing
`loc_632AE -> loc_62422` retirement checks previous BuildSprites visibility,
restores Target_palette_line_2, reloads monitor PLC, allocates the level-music
fade and deletes Knuckles. The engine now retains previous-pass visibility in
rewind state, restores its saved pre-cutscene level palette line, and retires
instead of spawning leaves forever. Independent ROM carriers (`loc_6338E`,
`loc_633D6`) retain only player references and can recreate after Knuckles is
gone; release/checkpoint writes belong to each carrier. Switch child lifetime
now runs during update, not render, preserving the same headless behavior.
The ROM monitor reload replaces shared VRAM; engine monitor sheets remain
separately resident, so there is no destructive tile restoration to emulate.
Original behavior and that representation difference are documented inline.

The native320 random leaf span is now centered in wider views with the same
folded random word and number of particles. The first focused centering check
(`TestMhz1CutsceneObjects,TestS3kMhzAct2EntryHeadless`, queued Maven, absolute
S3K ROM, b6c1147a2 plus edits) passed90 cases with zero skips at20:37 BST.
Retirement's first95-case selection passed all five live viewport rows and
five graph rows, but failed three obsolete unit assumptions: parent-owned lift
sound and render-time switch deletion. Those tests now dispatch the actual
carrier/update owner; final verification is pending. No full-suite claim.


Retirement verification: the subsequent97-case selection ran86 object cases,
five graph cases, five viewport entry cases and one full incoming MHZ1 route.
Only the new isolated palette case errored: its harness omitted the focused
player, so no carrier was allocated. Binding that player (no production change)
and rerunning `-Dtest=TestMhz1CutsceneObjects` passed all86 cases with zero
failures/errors/skips at20:55 BST. The other11 cases passed on the same production
code at20:50 BST. Both used queued Maven, Java21 and the absolute S3K ROM.
These are focused checks, not a combined campaign or full-suite pass.

Updated wide footage: external
`mhz-bring-up/campaign-20260923-act2-leaf-blower-800-retirement/capture.mp4`,
fresh MHZ2 native Sonic solo, donoroff,850 inputs, film350..849 (500 frames),
no gameplay seeds or deaths. Reviewed550/600/700 show visible high-priority
carry through rock, camera containment and normal priority after release.
The MP4 fully decodes; input and compiled-class hashes accompany it. This
supersedes800-final for leaf centering/lifetime, not a full-act completion claim.
Route authoring now clears the lower sticky vine with a deliberate spindash
and reaches the later loop/upper spring passage aroundX8200. Repeated jumping
and early spindash attempts were rejected as poor approach inputs; no gameplay
constants were tuned to those attempts.


### MHZ2 placed boss admission (2026-09-23)

Cold MHZ1 incoming inputs now traverse the MHZ2 late pulley and final mushroom.
They exposed an endboss softlock: placement15568,804 was culled on forward
admission because its public position already included the active$C0 offset.
Obj_MHZEndBoss checks the camera before that offset. Public coordinates now
retain the placement until admission; collision/drawing wait too. The admitted
core and six child families use explicit ROM retirement, not ordinary culling.
Inline comments identify the routines and engine representation difference.

First5-width admission check:2 absent-boss failures and3 KosM FIFO errors.
A delayed private-context offset attempt passed2 admission rows but broke21
seeded/post-init unit cases of138; rejected in favour of keeping private context
initialization and gating its public position. Final queued Java21 Maven,
absolute s3k.gen, b6c1147a2 plus edits:
`-Dtest=TestS3kMhzEndBossAdmission,TestMhzBossObjects,TestS3kMhzEndBossGraphRewind,TestMhzEndBoss*`
passed138 with0 failures/errors/skips at21:14:44 BST. Only2 admission rows were
compiled then. They check15 encounter members and20-frame restore/replay.
The current cold route reaches the live fight, then dies at21086; no completion.

Subsequent `-Dtest=TestS3kMhzEndBossAdmission` added5 actual checkpoint reload
rows to the2 positioned rows:7 cases,0 failures,3 errors,0 skips at21:18:54 BST.
Widths400/528/800 still exhaust KosM FIFO through giant-ring retirement during
approach. Investigation remains open; this is not dismissed as repositioning.
Combined campaign checks, full MHZ2 completion and integration remain pending.


Queue diagnosis: the retirement is a single MAIN ring at14784,1600 as the
positioned camera passesY1208. Both fixtures initialize cameraY1568 while the
player is704; their vertical approach exposes that ring only at400+ widths.
The physical FIFO contains precisely the4 MHZ2 enemy jobs (Cluckoid arrow,
Madmole, Mushmeanie, Dragonfly), with no duplicates. The ring's restoration
producer previously forced the still-pending enemy batch into the queue before
adding BadnikExplosion ($DB406), causing the fifth submission. The ROM enqueue
scans past the4-slot allocation on overflow; expanding capacity or suppressing
jobs would not reproduce its ordinary ordering. A ring-specific capacity-aware
producer now leaves pending enemy admission with its existing owner and retries
the single restoration when a physical slot is available. The marked-delete
ring remains invisible/noncollidable and persistent until then; its existing
captured state retains the obligation over rewind. Native available-capacity
retirement is unchanged. This explicit wider-presentation adaptation is
commented at both owners. Regression execution is pending.

The diagnostic7-row reruns reproduced the same3 errors, zero failures/skips;
temporary queue/actor print statements were removed after inspection. A
snapshot-based controller search was rejected as completion evidence: its
external input history was not restored, and a candidate did not reproduce
from cold boot. Only complete cold input reruns count as route evidence.


Capacity adaptation verification: queued Java21 Maven, absolute S3K ROM,
b6c1147a2 plus edits, `-Dtest=TestS3kMhzEndBossAdmission,TestSonic3kSSEntryRing*,TestSonic3kObjectArtProvider`
passed49 cases (10 admission,24 ring,15 art-provider), zero failures/errors/skips,
21:29:27 BST. An added eventual-retirement assertion was compiled subsequently:
`-Dtest=TestS3kMhzEndBossAdmission` passed10 with zero failures/errors/skips at
21:30:15 BST. Both positioned and checkpoint-reload approaches now admit the
boss at320/352/400/528/800 and release the old ring after its queued restoration.
These are focused results; full campaign validation remains pending.

Progress footage: external `mhz-bring-up/campaign-20260923-act2-boss-admission-320/capture.mp4`,
fresh MHZ1 native Sonic solo donoroff,21000 controller inputs, no gameplay seeds,
film19500..20999 (1500 frames),0 deaths. It shows actual incoming continuity,
endboss admission and chase, not completed MHZ2. Reviewed20100 and20900 show
weather/intro sprites and the active chasing boss; MP4 fully decoded. Input,
ROM and compiled boss-class hashes accompany it. Later cold input attempts
still die before the last two hits; no runtime attack constants were tuned.


### MHZ2 fatal hit through capsule/ship handoff (2026-09-23)

An input search with external previous-button state explicitly restored found
R+A period60/phase32 from21000. A complete cold MHZ1 rerun reproduced all9 hits
and the MHZ2 killing hit at21336, with no gameplay writes. It exposed a real
post-fight softlock: the camera kept wrapping indefinitely at boss phase3.
The boss-local finalHitHandoffFlag was disconnected from the event-owned
_unkFAA9 byte, which loc_55686 clears at the next wrap. The fatal hit and
loc_768B6 now share that event-owned flag. The grounded loc_768D2 writes55 to
Events_fg_5; loc_55620 acknowledges its high byte (FF55), stops repetition and
releases loc_55424's restoration. The helper also waits for landing, allocates
the native P2 controller once and starts forced RIGHT on its next dispatch.
No threshold or attack constant was tuned to the authored inputs.

Next cold execution reached the capsule but reused Act1's completed-results
flag, skipping the unopened capsule. The boss now clears the engine completion
semantic when loc_761E8 establishes its new wait. With that corrected, actual
capsule/results execution exposed generic results cleanup restoring pre-boss
level bounds (cameraY1568), pulling the player out of the expanded arena.
A retained MhzResults owner preserves boss camera/control authority, and
loc_76270 now performs the missing Restore_PlayerControl/2 before its separate
UP controller lock. The missing loc_54DB0 foreground wait also now returns to
routine4 after results so the ship request is consumed.

Initial handoff selection187 cases had4 failures: seeded unit tests asserted a
ship signal before the now-correct native post-capsule clear. They now publish
that later ship signal at its actual phase. One subsequent test compile failed
because an automated test edit touched an unrelated method without mhzEvents;
that edit was removed. Final queued Java21 Maven, absolute S3K ROM,
b6c1147a2 plus edits, `-Dtest=TestMhzBossObjects,TestSonic3kMHZEvents,TestS3kMhzEndBossGraphRewind`
passed187 cases, zero failures/errors/skips,21:44:15 BST. This includes shared
fatal-hit acknowledgement/grounded walkoff, both native players' restored object
control, and existing boss graph checks. Focused evidence, not a full suite.

The cold route then reached ship wait phase8 atX18299, but its ship workspace
stopped after one controller tick: all3 ship actors had been culled at world
X0/screen coordinates. loc_5583E has no world-range deletion and loc_5582E ends
Draw_Sprite. Both actors now persist through the scene; propellers' native
hardware screen coordinates are converted at rendering, with the$80 bias.
The ship sound gate now reads Level_frame_counter as loc_558AC does. The live
object-manager lifetime regression and another cold completion run are pending.


Ship lifetime verification: the same187-case focused selection passed with
zero failures/errors/skips at21:47:51 BST. Cold native Sonic solo inputs now
reach the actual FBZ1 transition request at23654 and playable FBZ1 at23774,
without deaths or gameplay seeds. The23775-frame input is retained as
`routes/s3k/mhz2-sonic-incoming-320.{script,bk2}`. The external diagnostic movie
`mhz-bring-up/campaign-20260923-act2-completion-320/capture.mp4` films20900..23774
(2875frames), fully decodes and has0 deaths/no follower in its state rows.
Visual review at23550/23650 exposes an unfinished ship: propellers are present,
but the Plane A body is absent. This is route continuity, not visual certification.

The new `TestS3kMhzAct2AuthoredRoute` checks every registered snapshot key at
admission, intermediate hits, defeat, results, ship, carry and the actual FBZ
load, including45-input forward replay. Its first run failed at admission19671:
zone-runtime's three published scroll words changed on immediate restoration.
`SwScrlMhz` had not captured its loop-adjusted camera accumulator, so rebuilding
an older frame applied the$200 repeat adjustment to a rewind camera jump.
The handler now captures its logical accumulator; a direct repeat/restore test
and the complete route test are queued. No snapshot differences are filtered.

Ship presentation research: loc_55486 writes _unkEE9C to Plane A VSRAM;
HInt6 restores Camera_Y_pos_copy at the$80 split, and sub_5550C writes ship
HScroll to the upper128 lines. Those render inputs were missing even though
the event fields and propellers moved. The candidate adds an internal semantic
foreground VScroll split, shared by visible low/high tiles and sprite-priority
mask passes, while leaving camera/player state unchanged. MHZ's mode enables
its existing per-line HScroll. The full ROM-backed layout supplies authored
rows directly; no disassembly asset fallback or invented ship art is used.
GPU boundary/mask/reset checks and a revised cold capture are pending. Native
staged row streaming and wide margins still require visual corroboration.


The scroll fix's queued selection (`TestS3kMhzAct2AuthoredRoute,SwScrlMhzTest`,
Java21, absolute S3K ROM, b6c1147a2 plus edits) completed22:09:10 BST:
7 scroll tests pass, the single route test fails,0 errors/skips. The route now
passes scroll restoration and exposes `object-manager.dynamic[13].spawn.subtype`
1→0 at admission19671. `MhzEndBossSpikeChild.recreateForRewind` constructed every
spike with subtype0 even though schema restoration recovered its live subtype
field. The factory now preserves ctx.spawn().subtype(); a standalone forced
recreation test and the same complete route are queued. No comparison weakened.

Native visual reference used the existing complete-run movie and saved native
frame265500, then ordinary playback to302650 through the common BizHawk2.11
host and `capture_ddz_route_reference.lua`. Initial plan expected zone0700 and
was rejected by the exporter (actual0701); that failed emulator was stopped.
Corrected output `mhz-bring-up/native-act2-20260923/ship-corrected-zone` completed
in42.633s,0 host failures,3651 observations299000..302650,122 sampled images.
The ship foreground stage16 is present301593..302488;302650 is FBZ1. Reviewed
302300 shows the upper Plane A body with separate propellers and ground below.
Host/plan/movie/ROM hashes and exact command are in host.json. This is native
reference corroboration, not renewed trace-driven development or matched inputs.

Candidate movie `mhz-bring-up/campaign-20260923-act2-ship-plane-320/capture.mp4`
replays the23775-input cold route, filming22900..23774 (875frames),0 deaths,
no follower/seeds. Full MP4 decode passes. Reviewed23550/23650 show the body
attached to propellers and player carry,23774 actual FBZ1. This supersedes the
missing-body diagnostic for presentation structure; different native inputs
and state preclude a matched-frame pixel-parity claim. Provenance records the
compiled render class and input hashes. Shared GPU split/mask/reset tests and
full-route rewind remain pending; combined2895-class ordinary+guards selection
was inspected but not launched while focused changes remain in flight.


Focused render selection `TestForegroundWindowRendering,TestSonic3kZoneFeatureProvider`
completed22:12:39 BST:8 pass,0 failures/errors/skips, native GL display enabled.
The GPU check covers upper/lower scroll split pixels, high-priority mask and
one-shot reset. Queued `TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
passed59,0 failures/errors/skips at22:17:44 BST on the same MHZ/render candidate.
These are focused results, not the combined campaign suite.

The spike correction selection (`TestS3kMhzAct2AuthoredRoute,TestS3kMhzEndBossGraphRewind,TestSonic3kMHZEvents`)
completed22:13:08 BST:66 pass, route1 fails,0 errors/skips. All seven MHZ
capture/45-input replay spots now pass. The FBZ check had sampled the cleared
fresh-load row23677, before the recording driver's title owner/first gameplay
boundary; restoring that unsupported transition snapshot mixed the old MHZ
handler with the new zone. The test now waits for released destination gameplay,
without filtering any state differences. A23775-input-only rerun had no valid
FBZ spot yet (1 failure,0 errors/skips,22:16:16); the headless recording driver
retains title presentation that the movie capture omits. A bounded600-neutral
input destination tail reaches that valid spot23801.

That next run failed on a genuine FBZ restore mismatch (1 failure,0 errors/skips,
22:17:21): palette owner IDs50..57 changed fromnone tofbz.eventPalette.
`reconcileRetainedPlaneState` reissued an old background-change palette patch
although the level palette and ownership adapters had already restored the
captured frame. Reconciliation now rebuilds only the retained tile plane;
normal init/background-change events keep their palette writes. A short FBZ
palette-restore regression, retained-plane/lifecycle/event tests and the complete
MHZ route are queued. This does not alter original palette event timing.

An800px cold input reuse probe died in MHZ1 at3523 (3584 rows including death
grace), before the requested capture range19000. It produced noPNG/movie and
is not Act2 presentation evidence. Do not tune runtime physics to reuse a native
input sequence across different object-visibility timing. Wide complete-route
authoring remains open; five-width local MHZ2 entry/admission checks still stand.


Final focused MHZ→FBZ rewind selection on b6c1147a2 plus campaign changes,
queued Java21 with `-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen`:
`-Dtest=TestS3kMhzAct2AuthoredRoute,TestFbzAct1RomRuntimeLifecycle,TestFbzRetainedPlaneNativeRows,TestFbzEventsAct1,TestFbzEventRewindRoundTrip test`
passed24 cases,0 failures/errors/skips,22:20:37 BST (60s Maven execution).
The route compares every registered snapshot key on restore and45-input replay
at all eight required spots, including released FBZ1 after its native title
boundary. Seven MHZ spots use the authored input; the FBZ tail uses explicitly
neutral input after the23775-frame capture script. A standalone forced-recreate
spike test passed earlier with the63 event cases. The palette correction also
passes FBZ's retained-plane/native-row/lifecycle regressions. `git diff --check`
is clean. Full2895-class ordinary+guards campaign verification, upstream
reconciliation, integration, push and cleanup remain pending. No level-wide
certification is inferred from this focused pass.


### Incoming DEZ2 → final encounter continuation (2026-09-23)

Next frontier is the actual DEZ2 gravity-boss exit followed by the full final
encounter, without a direct-zone23 reboot. `DezFinalRouteAuthorTool` accepts an
optional existing DEZ2 boss input, boots the declared Sonic solo ($34B0,$300),
200 rings/seven Super Emeralds setup, then observes the real load and continues
its controller-only authoring. No state is reseeded at zone23. A bounded neutral
input tail allows the incoming fade to finish if the prefix stops at its request.
The captured script includes both phases, enabling independent full replay.
This is positioned encounter continuity; it does not close cold DEZ2 traversal,
roster/donor breadth or native parity. Native-width execution and independent replay now pass; details below.


Native320 incoming route completes DEZ2 → six final fingers → eight core hits →
eight escape-ship hits → real DDZ load at20861, then240 controller-only entry
frames. The21102-input BK2/script is retained with the route tests; only the
initial positioned200rings/sevenSuperEmeralds setup is declared. No state is
reseeded at either load. All20862 author/replay CSV rows match across every
non-input field; full replay has zero deaths/followers. Capture116 films19200..21101
and capture117 films6300..7349. Both fully decode; viewed116:20575/21050 and
117:6820/6850/7200. Their provenance and inputs are in the external DEZ archive.

Queued Java21 absolute-ROM `-Dtest=TestDezIncomingFinalRouteCapture test` on
b6c1147a2 plus campaign edits passes1test,0failures/errors/skips at22:29:25 BST
(34.484s Maven). All registered snapshot keys compare on restore and45-input
replay at hands18HP, hands≤9HP, core4/1HP, escape4/1/0HP and live DDZ flight.
Only external capture-driver previous-button history is separately restored.
This is positioned incoming continuity, not cold traversal/native parity, and
does not close donor/team/checkpoint/death/history-isolation breadth. The800px
author also reachesDDZ at20868 without death; independent replay and two-width
rewind validation are in progress.


Two-width incoming follow-up: queued `-Dtest=TestDezIncomingFinalRouteCapture`
passes2cases, zero failures/errors/skips,22:34:53 BST (52.002s Maven). Both320
and800 verify every registry key at all eight restore/45-input replay spots.
The wide21109-input movie independently matches all20869 author rows, no deaths
or follower; capture118 fully decodes and stills20582/21050 were inspected.
The remaining240inputs show actual DDZ flight. No gameplay change was needed.
Complete incoming DDZ combat and cold DEZ2 traversal remain separate open rows.


Incoming DDZ continuation authors reach the actual $D01 request without deaths:
320 adds10056inputs after20862incoming passes (30918total);800 adds10662after
20869 (31531total). The initial positioned200rings/sevenSuperEmeralds are the
only seeds. Repository route inputs retain both transitions and all three fights;
the ending itself remains excluded. The full-registry test was extended with
DDZ body6HP, first chase wrap and defeat checkpoints. It reproduced2failures,
0errors/skips (native24867/wide24897): recreated child spawn coordinates became
(0,0), although live fields and parent links restored. The old DDZ route summary
compared live object coordinates but omitted this immutable metadata. Eleven
parent-derived DDZ child factories now preserve ctx.spawn through their existing
initialization; parent relinking and normal creation are unchanged. A short
eleven-family regression supplements the full route. Verification is pending.

Further inventory reconciliation found two still-open LRZ2 presentation owners:
loc_78AA8 does not yet run word_78EAA (the final boss has a separate implementation
of the same script), and loc_5711E's background Death Egg sprite is absent. These
are implementation gaps, not merely unverified matrix rows; retain them in the
next work queue. No LRZ palette/source behavior has been changed in this slice.


After the child-spawn correction, Surefire XML reports all4cases passing with
zero skips (2freshSuper routes +2complete incomingHyper routes), including11
whole-registry checkpoints per incoming width. The Maven wrapper nevertheless
exited143 without a final build summary; treat that invocation as incomplete
rather than a successful command. A route-only confirmation is queued, plus
the11-family short regression and59 mandated S3K checks.
Main develop was fetched/fast-forward checked again and remains40d55783c; its
reverse-gravity changes are still to be reconciled into the campaign tree.


Short DDZ child-spawn regression plus the four mandatory S3K consumers passes70
cases, zero failures/errors/skips,22:42:16 BST (21.770s Maven), queued Java21 and
absolute S3K ROM. No new object family or schema was introduced by the fix.


Complete incoming DDZ verification now passes at320/800: queued Java21 with
absolute S3K ROM, `-Dtest=TestDezIncomingFinalRouteCapture test`,2cases, zero
failures/errors/skips, BUILD SUCCESS22:43:32 BST (62s Maven). The30918/31531
controller inputs run from the positioned DEZ2 boss through the final arena,
both DDZ phases and actual $D01 request, without deaths or reseeds. All11
required spots per width compare every registered key on restore and45-input
replay, including DDZ body damage, chase wrap and defeat. The11-family child
spawn regression plus59 mandatory S3K checks separately pass70cases, no skips
(22:42:16 BST). Earlier freshSuper route cases also pass; these are focused
checks, not the combined campaign suite. Wide incoming completion video is
`$VIDEO_ROOT/ddz-bring-up/campaign-20260923-incoming-dez2-completion-800/capture.mp4`:
31531state rows, no deaths/followers,7finalrings,1531filmed frames, full decode
passed;30369/31291/31530 inspected (last is white exit fade). It predates the
recreation-only fix, which does not run during normal forward playback.
Cold DEZ2 traversal, roster/donor breadth, history isolation and native whole-scene
matching remain open. Ending/credits remain excluded.


### LRZ2 post-miniboss palette ramp (2026-09-23)

The prior turn made concrete progress: positioned DEZ2→final→DDZ completion and
whole-registry replay now pass at320/800. The next implementation frontier is
LRZ2's omitted loc_78AA8 rotation, followed by loc_5711E's Death Egg sprite.

`LrzPostDefeatCameraReleaseInstance` now reads word_78EAA from ROM, executes the
13row finite script with its signed byte delay/callback, honors rotation-disable,
and publishes shared timer writes32767atstart/0atcamera$940. A captured pending
word in LrzZoneRuntimeState connects the object phase to AnPal_LRZ2; independent
channelD remains active. The callback stops only the palette script, leaving the
camera waiter alive. No gameplay/camera geometry was changed.

Initial compilation failed because the new test was outside the package-private
cycler's package; moved the test, without widening production API. The first
executed selection passed13/failed2: a standalone test cycler had its own palette
registry, and the rewind setup captured before initial power-up registration.
Using the production registry and advancing two setup frames fixed those test
errors. The next13+1pass/1fail exposed inconsistent test setup: manually moving
the camera after scroll publication. Publish that setup through production
frames before capture; no differences are filtered. The macro palscriptdata stores
frames-1, so row starts are0,4,8,12,16,32,36,40,44,48,52,56,60; callback68.

Queued Java21/absoluteROM results on b6c1147a2 plus campaign edits:
- `-Dtest=TestLrzPostBossPalette,TestLrzPostDefeatCameraRelease,TestS3kLrzPaletteCycling,TestS3kLrzBossPaletteCycling`:15pass,0skips,22:52:18 BST,20.345s.
- `-Dtest=TestLrz*,TestS3kLrz*,SwScrlLrzTest,TestSonic3kLrz*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` with all3absoluteROMproperties:558pass,0skips,22:54:10 BST,70s.
- `-Dtest=TestLrzPostBossPaletteRouteCapture`:1native route pass,0skips,22:54:52 BST,23.683s; the test now also checks800 (pending).

Corrected positioned approach($2B70,$750),355rings/sevenChaosEmeralds, solo Sonic
crosses the real priority marker, defeats the miniboss, changes act at2200 and
starts the palette script at3276. All13live cursor changes match source-relative
timing, with no gameplay writes after declared boot. Both320/800captures replay
4200inputs, film3000..4199, no hurt/death/followers, full decode passes. Native
stills3276/3336show bright→dark background;3400/3500/3600andwide3280/3320/3500
were inspected. Wide gameplay first differs at3125during results; no pixel parity
claim. The camera becomes negative at the centered Act2 start and exposes left
scenery: retain this separate presentation gap. Existing native f416433.cram
colors33..37 exactly match scriptrow5, but full-ramp native sampling remains open.


The expanded route test passes both320/800,2cases/no skips at22:57:48 BST
(26.160s Maven). Rewind architecture guard passes4cases at22:58:36; the typed
ObjectServices migration guard passes13at22:59:38, no skips. The first guard
command also named nonexistent TestObjectServiceAccessGuard; only the4actual
architecture cases are claimed from it. The actual migration class ran separately.

Native palette capture now observes111consecutive frames416380..416490 from
the original movie's415400state, no RAM writes. Common host completed PNG/VDP
capture in5.123s and optional LRZ palette-state exporter in2.167s, no failures.
The first script write is416397; all13rows and their0,4,8,12,16,32,36,40,44,48,52,56,60
offsets match ROM; callbackat68.94script-active native rows match all5words,
iteration and sharedtimer32767-elapsed exactly. CRAM publishes those colors one
frame later. Native416398/416430/416458screenshots reviewed. This closes native
color/timer/script corroboration, not full-scene parity across different routes/teams.

That observation exposed a real first-draft timing error: our post-object cycler
consumed the timer write before its decrement. A new independent assertion failed
expected32767/actual32766 (1pass/1fail). ROM AnimatePalettes precedes ExecuteObjects;
the engine resolves palette ownership later, so it now applies deferred object
writes after its existing cycle tick, including fade-only passes without advancing
clocks. Release0 is likewise visible to the next normal tick. A fade/resume check
was added; corrected focused/component/route/mandatory-S3K verification is pending.


Final timer-order selection (`TestLrzPostBossPalette,TestLrzPostDefeatCameraRelease,
TestS3kLrzPaletteCycling,TestS3kLrzBossPaletteCycling,TestLrzPostBossPaletteRouteCapture,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`)
passes77cases,0failures/errors/skips,23:04:54 BST (68s Maven). Separate24-class
`-Dtest=*S3k*Palette*,*Sonic3k*Palette*` selection passes128cases,0skips at
23:05:26 BST (22.741s). Both queued Java21 with absolute S3K ROM. The wider
558-case LRZ pass preceded this clock-order correction; it is not reattributed
to the corrected source. Combined campaign suite/guards remain due.

Final800v2capture has identical4200CSV rows and1200PNGs to the preceding video;
full ffmpeg decode passes. The correction changes the captured timer and its
release ordering, beyond the recorded input's camera endpoint$895 (before$940),
without changing this footage. Provenance/source hashes and input copies are
alongside it. Component tests cover exact timer release and fade-write semantics;
the two-width route tests cover the mid-ramp checkpoint.

Reconciled LRZ matrix/backlog summaries against live production: Act1 has zero
placeholder placements and an implemented miniboss/seamless exit; old slice3
counts and open-miniboss claims were stale. The boss-act12820-frame fresh route
was also already recorded below an outdated status paragraph. Conversely, the
backlog's claim that the LRZ2 Death Egg background sprite was implemented was
incorrect: searches by native labels, verified art/mapping addresses and class
registrations confirm it is missing; Lrz2BackgroundStageMachine explicitly says
it is owed. This and negative-camera left scenery are the next concrete LRZ
implementation/presentation tasks. Native whole-scene and other route/lifecycle
products across all seven zones remain separate, and integration/push are pending.


LRZ2 Death Egg native observation (September 23 continuation): the common host
completed the original movie frames425000..428300 from its ordinary415400save,
3301rows and4screenshots,13.732s,zero host failures. ROM SHA-1 is the required
CFBF98C36C776677290A872547AC47C53D2761D6. Evidence lives outside the repository at
`$HOME/Videos/OGGF/lrz-bring-up/native-death-egg-20260923/`;
`comparison.json` checks every row against sub_57082's signed-word X gate and Y
subtraction, zero mismatches. First nonzero X is frame427916, camera13058,
scroll-table word3672, X=-2016,Y=121. The following observation has the queue flag
$FF00 rather than0. This corroborates the native object-before-background-update
ordering; the observer reads the completed frame, not the exact queue-call instant.
Priority remains$380 and art word$639F throughout. loc_5711E/loc_57156 are
screen-positioned (render bit2 clear); Render_Sprites masks each SAT X to9bits.
A direct unmasked world-coordinate implementation would therefore be wrong.
Art is queued at$15A112 to tile$39F, mapping$5719E. Reviewed native428100PNG;
no engine or full-scene parity claim. Implementation and widescreen wrap/entry
presentation remain open; no runtime behavior was changed by this observation.

Rechecked the user's Mecha-follow bounds request against SszMechaArenaPan:
loc_7C9F6's +6 native step clamps to Camera_max_X_pos, and only the published
camera coordinate receives NativeViewportFraming's centered inset. Existing
transformationPanProjectsNativeProgressAtEachWidthAndReplays covers all five
aspect widths, final-step overshoot, unchanged native bounds, foreground extent
and restore/replay; transformationPanStopsAtNewLimitWithoutReleasingTheCamera
covers the original lock lifetime. No additional camera change or redundant test
run was made in this continuation. Full campaign integration remains pending.


LRZ2 Death Egg implementation follow-up: added the event-allocated screen sprite,
level-art registration, captured deformation word and late runtime art request.
Direct init and seamless stage0 each attempt allocation once; Knuckles deletes it.
Native priority$380/art$639F remain separate. Native320 gate is unchanged; wide
views extend the offscreen gate by width-320, including earlier art submission,
and unwrap one SAT coordinate turn instead of repeating the planet every512pixels.
A temporary production-boot placement probe found no $2F rail placements in Act2;
its four $6E placements are subtype113 (not the shared$3A1 rail art). No runtime
assets were read from the disassembly. First probe incorrectly used a bare fixture
without the ROM extension and loaded the wrong game; discarded that failed probe.

Queued Java21/absolute-S3K-ROM evidence on b6c1147a2+edits:
- `-Dtest=TestLrz2BackgroundStageMachine,SwScrlLrzTest`:16pass/no skips,
  23:20:04 BST,55.376s.
- `-Dtest=TestLrzDeathEggBackground,TestLrzPostBossPaletteRouteCapture,TestRemainingRewindTailInventory`:
  4pass/1fail. Sprite and both real handoff routes pass. Inventory expects
  total1291/passed1051/graph240 but observes1313/1070/240; no-codec0.
  Unclassified parent-dependent constructors: SszCraneClaw and SszCraneClawPart;
  isolated other-failure: SszCraneShipDecoration. These campaign classes need
  graph-evidence reconciliation, not silently reclassified as passing. Still open.
- Expanded `TestLrzDeathEggBackground,TestLrzPostBossPaletteRouteCapture,
  TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
  TestSonic3kDecodingUtils`:68pass/no skips,23:23:40 BST,66s. Includes five-width
  recreated sprite and45-step every-key registry replay, Knuckles deletion,
  and exactly-one-owner checks after both real seamless handoffs.
- `-Pguards -Dtest=TestObjectPriorityBucketGuard,TestObjectServicesMigrationGuard,
  TestRewindArchitectureGuard`:18pass/no skips,23:24:34 BST,24.030s.

External captures `campaign-20260923-death-egg-moving-{320,800}` contain240rows,
zero hurt/death, input copies and class-hash provenance; both videos decode fully.
Viewed native239 and wide120. Separate native `death-egg-visible-320` starts at
(13875,640), settles on the slope, and shows the body clearly at frame30 with
camera(13715,588);90rows/no death. Native reference428100 has camera(13715,576),
so this is a position/appearance corroboration, not matched full-scene pixels.
Earlier neutral probe at(13550,850) started below the platform and died at146;
keep it labelled as a rejected presentation setup, not a route regression.
Wide420frame walk-only probe stalls against a step; final moving clip adds normal
jump presses. Full cold routes, negative Act2-left scenery, campaign inventory,
combined validation, upstream reconciliation, integration, push and cleanup remain.


### SSZ crane inventory and finite foreground follow-up (2026-09-23)

Previous turn produced a real inventory failure, not an engine recreation failure.
Strengthened TestS3kSszCraneRouteHeadless at both320/800: asserts claw, two parts
and decoration are live, captures all registry keys, advances45frames, destroys
ship/children, restores, compares every key, replays45, then compares the full
registry again at the complete release endpoint. Bothcases pass23:29:34 BST,
20.707s queued Java21/absoluteS3KROM. Added the three parent-dependent families to
RewindRoundTripHarness graph classification with this test as evidence. First
inventory rerun:28classification checks pass, inventory still fails because its
expected totals were hardcoded in Java as well as the comment. Corrected both;
final inventory passes23:31:44 BST,18.797s. Actual1313total,1070isolated-pass,
243graph-covered,zero unclassified/no-codec. No production crane change required.

The LRZ negative-left scenery came from shader_tilemap's unconditional horizontal
modulo over the entire finite foreground texture. Added one-shot ClipHorizontal
sampling for wider finite foreground draws, including their priority/mask passes.
Native320, Plane B, explicit foreground rings and alternate plane sources retain
wrapping. Shared renderer consumes semantic layout policy, no zone-name case.
Original VDP behavior (plane wrap hidden by native camera bounds) and wider-view
difference documented in LevelRenderer and shader. Camera/player bounds untouched.
The controls use an unmarked internal-access bridge; also moved this campaign's
new MHZ vertical-split setter behind it, avoiding new published ModApi signatures.

Verification on b6c1147a2+campaign edits, queued Java21:
- `-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen
  -Dtest=TestTilemapGpuRendererPerLineSampling,TestLrzPostBossPaletteRouteCapture,
  TestLrzDeathEggBackground,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
  TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:70pass/no skips,
  23:33:16 BST,66s.
- `-Dopenggf.test.gl.native=true
  -Dtest=TestForegroundWindowRendering,TestShaderPixelCentreSampling,
  TestBackgroundScrollWrapPixels,TestTilemapGpuRendererPerLineSampling`:
  5pass/no skips23:33:38 BST,20.764s. RealGL covers both finite X edges,
  visible/high-mask passes, retained wrap on the following draw, existing MHZ
  split/window/scissor and background wrapping. Following the access-only bridge
  change, the same5checks pass23:37:44 BST,58.905s.

Replayed both4200frame real LRZ miniboss/results/seamless inputs and captured
frames3000..4199 to external `campaign-20260923-finite-left-edge-{320,800}`.
Both1200frame MP4s decode fully; no hurt/death. Native1200PNGs identical to the
preceding320probe and all gameplay rows identical; only neutral input text differs
at4100..4199 (implicit empty versus explicit neutral authored records). Wide all4200
CSV rows identical to800v2;359PNGs changed only outside the latched layout's left
extent. The first comparator mistakenly used the post-step live camera and reported
changes in up to3authored pixels; LevelRenderer uses retained LevelScrollPresentation,
and comparing the matching prior camera resolves every one (zero inside changes).
Reviewed wide3280. Raw comparison/provenance retained beside the videos.
The cleared margin reveals Plane B, so a sharp room-edge transition remains visible;
this closes unrelated terrain wrapping, not scene-extension polish. No repeat of
already-passing engine checks for subsequent prose edits. Shared-renderer broad
validation, remaining level work, upstream integration, push and cleanup remain due.


### Act1 reconciliation and next SSZ implementation target (2026-09-23)

The original SSZ matrix still labelled113placements as placeholders and the crane
as unimplemented. Registry/profile code already implements both; updated the
stale Act2 census assertion from {$B2:$00} to the empty set and corrected matrix
status without claiming cold completion. Also separated the fixed cloud source
window from its still-open plain-mode pixel demonstration; retained arrival
Death Egg omissions as actual work.

Extended existing arrival test with15native roster/width cases (all5widths ×
Sonic solo/Sonic+Tails/Tails solo), three every-registry-key restore/45-input
replays at rise40, rise-end108 and swing/release173, then control-release/no-death
assertions at240. Corrected the test's non320aspect enum selection to match its
actual width. No production behavior changed in this follow-up.
Queued Java21/absoluteS3KROM `-Dtest=TestS3kSszArrivalHeadless,
TestS3kSszPlacementCensus,TestS3kSszKnucklesBridgeHeadless,TestS3kSszLifecycleProduction,
TestS3kSszBackgroundLayout,TestS3kSszBackgroundClouds,TestS3kSszScrollBands`:
60pass,0failures/errors/skips,23:41:08 BST,23.241s on b6c1147a2+edits.

Next concrete owner, not optional polish: SszDeathEggSmallObjectInstance explicitly
omits loc_659CC's longword V_int_run_count→RNG_seed, Normal_palette_line4 backup
and nonzero-word Pal_KnuxSSZEnd patch ($669B2). Native ChildObjDat_665C4 allocates
seven children: loc_65B0E sprite mask (0,0), loc_65A8C slotted DPLC cloud (0,-$33),
five loc_65B42 animated trails at(-$20,$1D),(-$10,$1D),(8,$1D),($10,$1D),($28,$1D).
Cloud timer$190 then$180, second phase$40=$10; DPLCPtr_SSZDeathEggCloud supplies
ArtUnc andDPLC. Mask sets Spritemask_flag and retires on _unkFAB8bit2. Trails
Refresh_ChildPosition and byte_66760 end in Go_Delete_Sprite. After the parent's
$100 timer expires, sub_66054 gates on V_int_run_count low5bits==0, plays MissileShoot
and uses CreateChild6_Simple/ChildObjDat_665F0→loc_65B70. Missile takes Random_Number,
Xoffset(low6bits)-$20, xvel ±$100, Yoffset(highword&$1F), yvel$100 then -$10 before
MoveSprite2 each pass, frames4/5 by VInt parity, Sprite_CheckDeleteXY.
On rise exit, parent sets its own$38bit5 and sharedFAB8bit1, restores the backed-up
palette line and deletes. Existing code implements only movement/timer/sharedflag.
Original native320 and wider presentation, graph recreation, palette restoration,
allocation/slot constraints and native cutscene Knuckles finalX all remain to verify.


### SSZ rising Death Egg palette and missile follow-up (2026-09-23)

Implemented loc_659CC's full V-int RNG reseed, exact palette-line backup,
nonzero Pal_KnuxSSZEnd writes, and departure restoration. The saved palette is
part of the recreatable owner and survives full-registry restore. Queued Java21
with the absolute S3K ROM, `-Dtest=TestSszDeathEggCutscene,TestS3kSszArrivalHeadless,
TestS3kSszKnucklesBridgeHeadless,TestS3kSszColdRoutes`:26pass, zero failures/errors/
skips at23:46:59 BST,58.904s on b6c1147a2+campaign edits.

Missile implementation now follows sub_66054 before parent movement, including
V-int cadence, allocation-local RNG consumption, signed X velocity, high-word Y
scatter, pre-movement acceleration and deferred culling. Corrected the parent's
departure comparison to the ROM's unsigned CMP.W/BLS. Native low hardware
priority remains separate from SAT bucket5; palette0 reuses the small Death Egg
ROM mapping sheet. Widescreen extends only the shared X culling window.
ChildObjDat_665C4's seven initial cloud/mask/trail children remain open; this
increment does not certify the complete cutscene or its native appearance.

Focused follow-up, queued Java21 with absolute S3KROM on the same dirty task HEAD:
- `TestSszAct2MechaEntry`:15pass/zero skips at23:54:58 BST, including every
  supported width, final-step clamping and rewind. No further Mecha pan edit
  was needed for the reiterated bounds request; existing code documents native
  six-pixel movement and display-only widescreen projection.
- First missile/inventory selection:19tests,2failures. Inventory measured the
  new independent missile as isolated-pass:1314total/1071passed/243graph/0no-codec;
  updated both expected inventories. Recheck passes at2026-09-23T23:55:59+01:00. The missile
  replay failure was `sprites[0].playerExtra.instaShieldRegistered=false/true`: the
  test captured before production initialized that auxiliary. Two production
  warmup frames establish the intended rewind boundary; no engine workaround.
- `-Dtest=TestSszDeathEggCutscene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
  TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:63pass,zero failures/errors/
  skips,23:56:50 BST,20.886s. Includes positive/negative missile scatter, one RNG
  draw, inclusive Y cull and delayed deletion, V-int allocation cadence, unsigned
  parent departure, palette restoration, and every-registry-key forward replay
  after owner recreation. Focused evidence only; combined campaign run pending.

Updated input-driven SSZ1 arrival captures: external
`$HOME/Videos/OGGF/ssz-bring-up/campaign-20260923-arrival-missiles-{320,800}`.
Both run1500frames of the Sonic+Tails complete-movie controller input starting
396720, no position/clock/emerald seed; film300..1499 (1200PNGs). Both CSVs show
zero hurt/death and both MP4s fully decode. Reviewed wide700, native500/700/900;
wide700 visibly shows the corrected Death Egg palette and two missiles. Native
700 places it outside the narrow viewport, while900 catches its departing edge.
Input/class hashes and exact commands are retained in each provenance.json.
These clips explicitly precede the cloud/mask/trail implementation and are
progress evidence, not native parity or complete-cutscene acceptance.


### SSZ initial Death Egg children and native landing (2026-09-24)

Implemented all seven ChildObjDat_665C4 allocations in native order, stopping
on failed allocation before applying the palette patch. Mask uses Map_SpriteMask
frame12 and the existing SAT-control path; cloud independently follows background
motion, admits one tracking owner, uses ROM ArtUnc/DPLC frames and its$190/$180
timers; five trails read their parent SST position and retire through the ROM
animation callback. Mask waits for FAB8bit2, independently of parent deletion.
SAT buckets4/5/6 remain distinct from low hardware priority. Cloud tracking is
represented by the live cloud owner rather than a second uncaptured Java bit;
broader cross-owner slotted-art conflicts are not certified by this local check.

Queued Java21/absoluteS3KROM, b6c1147a2+campaign edits:
- `TestSszDeathEggCutscene,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,
  TestRemainingRewindTailInventory,TestS3kSszColdRoutes`:9tests at00:04:17 BST;
  only failure was inventory's expected count after the new child class. Actual
  sweep1315total/1072isolated-pass/243graph-covered/0no-codec; updated both pins.
- `TestSszDeathEggCutscene,TestRemainingRewindTailInventory,TestS3kSszArrivalHeadless,
  TestS3kSszKnucklesBridgeHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
  TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:91pass/no skips,
  00:05:33 BST,25.040s. Includes the cloud's single-owner/release ordering and
  full-registry recreate/45-input replay across initial animation and drift change.
- Native landing assertions added to TestS3kSszKnucklesBridgeHeadless:
  3pass/no skips,00:08:59 BST,19.683s. Native loc_658F2 lands at($3A0,$C64),
  exactly the existing engine outcome. $2A8 is the leap trigger, not the resting
  position. No gameplay tuning was required; the earlier discrepancy hypothesis
  is rejected by observation.

New engine videos external campaign-20260924-arrival-children-{320,800}:
1500frames from the existing82EA movie's396720 input offset, film300..1499;
zero hurt/death and both full MP4 decodes pass. Reviewed wide500/700. These
replace the preceding incomplete-child clips, with exact commands/class hashes
in provenance.json. Native AD40 movie uses a different timeline, so these are
not frame-aligned trajectory/pixel comparisons.

Read-only native exporter promoted to tools/bizhawk: initial run from the prior
447381 native save records448900..451100,9559object rows, host5.572s/0failures.
Observed palette nonzero-word patch matches, cloud enters routine4 at449512
(timer384,velocity$10), Knuckles sets button at450214 at(928,3172). All five
trails have410 observed active rows; the mask remains active after the parent.
Initial exporter mistakenly sampled the Y accumulator from x_vel+$00 (offset$18)
instead of y_vel (offset$1A); corrected and recaptured from its448920 save.
That initial accumulator column is invalid; positions/timers remain useful.

Corrected native run2 completes in4.170s with no host failures; all3654 sampled
Death Egg/cloud/mask Y values equal highword(y_vel accumulator) minus signed
oscillator>>2. Native palette restoration at449992 exactly matches the backed-up
line at449111. comparison.json names run2 as authority; run1's erroneous accumulator
column is not used. Native and engine landing agreement rejects the old resting-X
claim without modifying gameplay. Broader native pixel acceptance and campaign
validation/integration/push/cleanup are still outstanding.


### SSZ cold staircase blocker (2026-09-24)

The old82EA movie inputs reachX1771 then turn back; the newerAD40 cold prefix
reaches1154 and dies at5245. These are route attempts, not proof of missing
walkway behavior. A fresh controller-only continuation from the old prefix's
frame2501 crosses the small platform and reaches the permanent diagonal stairs.
It dies at2724 (continuation223), despite four real$7B:$80 placements ascending
from(2496,3192) to(2880,3096). The new cold regression reproduces that death.

Root cause: SszCollapsingBridgeDiagonalObjectInstance.sampleSlopeByte reads ROM,
but getSlopeData returnednull. Every production contact/carry entry used that
null as a flat-solid fallback; the previous test only queried the sample method.
Supplying a derived cached ROM window at the live slopeOffset restores the
actual shared slope path. Cache is transient and regenerated after recreation
or pointer changes. Also corrected the missing hardware-priority bit from the
object's make_art_tile(...,2,1), independent of SAT queue$180. No shared physics
or route-specific runtime workaround. Native width comparisons exclude the
right edge: corrected the previous comment/test claim that ordinary contact
necessarily reads sample64; the extra diagnostic read is not that evidence.

Initial red test: TestS3kSszColdRoutes#authoredContinuationTraversesThePermanentDiagonalStaircase
fails on death at223. After slope hookup, TestS3kSszColdRoutes and
TestS3kSszTraversalPlatforms pass10tests/no skips,00:19:19 BST,61s (queuedJava21,
absoluteS3KROM,b6c1147a2+edits). The route now reachesX2960 andY<3080 while
asserting sampled surface contact. Further checks add every-registry-key
recreation/15-input replay on the staircase and ROM high-priority assertion.

Expanded queued verification `TestS3kSszColdRoutes,TestS3kSszTraversalPlatforms,
TestS3kSszCompatibilityMatrix,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,
TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:89pass/0skips,00:21:26 BST,69s.
Includes full-registry slope recreation and15-input replay. Authored2779-frame
route is saved as ssz1-sonic-tails-cold-staircase-320.script/bk2; cold320capture
ends(3043,3052)with5rings,0deaths/34hurt rows;800ends(3072,3051)with12rings,
0deaths/0hurt. Both299-frame films2480..2778 fully decode; still2750 reviewed.
Exact inputs/class hash/command/provenance remain outside the repo with the
videos. The old recording-only probe and rejected gap-jump attempts are labelled
route exploration, not parity. Remaining cold SSZ route begins beyond this
staircase; no full-act or combined campaign pass claimed.


### Cold first-replica arena route (2026-09-24)

The authored native320 Sonic+Tails input now reaches the GHZ replica arena from
fresh SSZ1 arrival in4473frames, no ring/emerald/position seeds and no deaths.
It traverses the large spring, upper return walkways, rotating carrier,
collapsing columns and bouncy cloud. At4472 P1=(488,2156),37rings,
camera=(352,1984); the production object manager contains the first replica boss.
Inputs: routes/s3k/ssz1-sonic-tails-cold-ghz-320.{script,bk2}. This is arena
entry, not boss defeat or full-act completion.

TestSszColdRouteCapture drives GameplayCaptureSession's production loop and
render path, comparing all registry keys after45-input replay at carrier3440,
column3818, cloud4168 and arena4400. Queued command:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk DISPLAY=:0 python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen -Dtest=TestSszColdRouteCapture,TestS3kSszColdRoutes test`.
On b6c1147a2+campaign edits,3tests pass,0failures/errors/skips,26.334s,
2026-09-24 00:36:46 BST. Earlier attempt with HeadlessTestFixture did not
reach the arena lock (maxX remained$19A0 instead of$160); its bootstrap/runner
is not the production capture loop, and that mismatch remains uninvestigated.
The final test deliberately exercises the captured production path; this does
not establish equivalence of the two harnesses or native-ROM parity.

Both320/800 captures run4473frames without death; film2779..4472=1694PNGs.
The same input at800 stops earlier at(2047,2540),44rings; wide continuation
is still owed. Both MP4s fully decode; native final frame reviewed. External
archive: campaign-20260924-cold-ghz-{320,800}, with commands/input hash.
Rejected left-wall jumps atX842 were a route-choice error: landing on the
nearby cloud atX880 supplies the ascent. No runtime change was made for them.
Combined campaign validation/integration/push remain pending.


### Cold first replica defeat and transport (2026-09-24)

The native320 Sonic+Tails cold route now defeats the GHZ replica and takes its
released teleporter to the upper receiving platform, without position/ring/
emerald seeds or deaths. Task-tree input:
`routes/s3k/ssz1-sonic-tails-cold-first-replica-320.{script,bk2}`,5827frames.
Endpoint(512,1420),0rings,control released. This advances the preceding entry
frontier; it is not a full-act completion. Wide continuation remains separate.

A temporary read-only feedback probe authored ordinary direction/jump input,
then the frozen BK2 was replayed from cold through the production loop. The
initial static jump sequence died after two hits; centre/follow approaches died
after five/six hits. Following slightly to the right of the ship succeeded.
These rejected control choices supplied no evidence for changing boss physics.
One ordinary jump lands on the risen pad after escape and triggers transport.
A further600R attempt dies at5937,(904,1388),with0rings; do not use that suffix as
successful traversal. The next route task starts from the receiving pad.

`TestSszColdRouteCapture` now verifies boss creation, killing hit, removal,
native defeat flag, receiving position and released control; all registry keys
match after45-input replay at3440/3818/4168/4400/5100/5600 (carrier,column,cloud,
arena,killing-hit window,transport). Queued focused command:
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk DISPLAY=:0 python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen -Dtest=TestSszColdRouteCapture,TestS3kSszGhzArenaHeadless test`.
15tests pass,0failures/errors/skips,28.146s,00:43:03 BST on b6c1147a2+campaign
edits. This is focused production-route/component validation, not a full suite.
No runtime code was changed for this extension.

External `campaign-20260924-cold-first-replica-320/capture.mp4` films4473..5826
(1354PNGs). All5827state rows show0deaths;120hurt rows; endpoint matches the
test. Full MP4 decode passes, frames5100/5600 reviewed; command and input hash
in provenance.json. Not native pixel parity. The previous headless-fixture
mismatch remains uninvestigated; full campaign integration/push still pending.


### Carrier lifetime and missing replica Eggmobiles (2026-09-24)

Cold-route extension exposed an invalid rewind reference from swinging arc to
rider bar at the second transport window. The generic object-manager range
check had removed the swinging tip while its hub/arc remained live. ROM
loc_46142 owns the hub coarse-X cull; loc_461FE and loc_462B6 delete arm and bar
only through the parent's signal. All three now bypass generic pre-culling and
retain the existing hub-owned cascade. The short carrier test moves the camera
out of range, verifies all three retire, then recreates the complete graph from
rewind. This is a lifetime correction, not a nullable-reference workaround.

The correction changes traversal at input7076: Sonic now lands on the formerly
missing bar. The old input ended at(3152,...) instead of its lower-path endpoint.
An ordinary jump off the bar, followed by a jump from the monitor at(3840,1392),
now reaches the upper walkway. Updated input
`routes/s3k/ssz1-sonic-tails-cold-middle-320.{script,bk2}` has7912frames and ends
at(5045,1196),25rings,no deaths. The old7681-frame lower-path capture
`campaign-20260924-cold-middle-320` is superseded diagnostic evidence, not the
current route. Remaining act traversal and wide input still need completion.

User review identified invisible Eggmobile bodies in both Act1 replica fights.
Both boss owners already request ROBOTNIK_SHIP frame$A; addSszEntries registered
that sheet only inside the Act2 crane branch. Consequently the renderer was null
and appendRenderCommands silently skipped the body while separate heads/attacks
drew. ROM PLC_78_79_7A_7B loads ArtNem_RobotnikShip; ObjDat_SSZGHZBoss and
loc_7A72C use Map_RobotnikShip frame$A,palette0,low hardware priority. The shared
sheet is now registered for both SSZ acts. This was missing art registration,
not another priority-bit mistake; no priority override was introduced.

The new SSZ1 art test failed first with missing standalone entry, then checks
ROM address,palette,nonempty frame$A and bounded tile references. The cold
production route also asserts that its live ship renderer exists and is ready.
Existing encounter logic tests had not asserted this dependency, and earlier
visual review failed to catch the head-only presentation; those old captures
must not be treated as complete visual acceptance.

Verification on b6c1147a2+campaign edits:
- The first lifetime selection passed76cases but failed the old route endpoint;
  the dangling-reference error was gone. No claim of a green run was made.
- `-Dtest=TestS3kSszCarriersAndSprings` passes18/0skip at08:02:43 BST,62s,
  including hub retirement and graph recreation.
- Queued Java21/DISPLAY=:0/absoluteS3K-ROM selection:
  `-Dtest=TestSszColdRouteCapture,TestS3kSszCarriersAndSprings,TestSonic3kPlcArtRegistry#sszAct1ReplicaBossesHaveTheRomEggmobileFrame+sszAct2CraneGraphHasRomBackedSheetsIncludingKnucklesHead+s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,TestPatternSpriteRendererCorruptionGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
 83pass,0fail/errors/skips,72s,08:09:08 BST. Cold route compares all registry keys
 across45-input replays at3440,3818,4168,4400,5100,5600,6300,6459,7076,7460.

`campaign-20260924-{ghz,mtz}-eggmobile-{320,800}` shows both restored ships.
Each is a declared positioned checkpoint,40rings,neutral input,450steps,
film150..449=300PNGs,0deaths/44hurt rows. Full MP4 decoding passes and frame250
was inspected at all four configurations. These establish engine presentation,
not native whole-fight pixel parity or cold wide completion.
The combined campaign selection currently has2902ordinary classes plus guards;
that run, integration,push and cleanup remain pending.

Current cold-route film `campaign-20260924-carrier-fix-320/capture.mp4` is7912
steps,film5827..7911=2085PNGs,0deaths,endpoint(5045,1196),25rings. Full MP4
decode passes and frame7076 confirms Sonic riding the formerly culled bar.

### SSZ replica widescreen locks — 2026-09-24 follow-up

User review of the static-mask prototype exposed an underlying camera bug. The
800px GHZ camera followed knockback from X112 to200; MTZ snapped from5488 to5728,
hiding the fight behind the fixed mask. Entry gates already added the native
framing inset, but the provider enabled projected bounds only for SSZ2. SSZ1
now derives projection ownership from the existing captured lock/fight flags,
including allocation and defeat until the launch releases bounds. Native camera
bounds remain $160/$1660. No extra rewind state or gameplay bound changes.

Queued Maven with absolute s3k.gen and Java21: TestNativeArenaCameraFraming,
TestS3kSszGhzArenaHeadless, TestS3kSszMtzArenaHeadless, TestS3kAiz1SkipHeadless,
TestSonic3kLevelLoading, TestSonic3kBootstrapResolver, TestSonic3kDecodingUtils:
112 tests, zero failures/errors/skips, 2026-09-24 09:07:09 BST, dirty campaign
HEAD b6c1147a2. Ten new cases cover both replica locks at320/352/400/528/800,
player displacement, preview, defeat/release and restored ownership. This is
focused validation; combined campaign delivery remains pending.

Recaptured campaign-20260924-{ghz,mtz}-camera-fixed-{320,800}: 450 steps each,
film150..449, positioned checkpoint/40rings/neutral input. All300 filmed rows
retain X352/112 forGHZ and5728/5488 forMTZ respectively, with44hurt rows each.
Sonic stays inside the native central rectangle throughout; full MP4 decode
passes and wide frame400 was inspected for both fights. Earlier eggmobile
captures remain art evidence but are superseded for camera presentation.

The reported roaming-cloud/wrecking-ball overlap matches shipped sprite order:
loc_57BB2 writes cloud priority0; ObjDat3_7A678 writes ball priority$280 (bucket5),
and chain rows write$300 (bucket6). Lower buckets precede later sprites in SAT.
Ball mapping pieces and base art word are low hardware priority; raising that
bit would not be a faithful sprite-order repair. No priority override applied.
This conclusion is disassembly-backed; no new native-video comparison claimed.

### Native cloud ordering corroboration — 2026-09-24

Replayed unmodified s3k-sonic-tails-complete-emeralds.bk2 from native save448920
through454000 using BizHawk2.11/GPGX and the native-reference host. ROM SHA1
CFBF98C36C776677290A872547AC47C53D2761D6; movie SHA256
AD40FB0B0A74FA12B08AB71B2E48A7455B388D14F43F4CDED502AC4A15D1B3C0.
No gameplay RAM writes. External archive:
`$HOME/Videos/OGGF/ssz-bring-up/native-ghz-cloud-order-20260924`.
Host completed with no failures;3001 continuous observed frames,151 screenshots.
Frame453000 visibly shows a roaming cloud covering the wrecking ball's upper-left
region and the Eggmobile. Frame453080 shows Sonic covered during the same fight;
frame451860 also shows player/cloud overlap on the approach. Live cloud code
$57BF6 retains priority0; the ball uses$280. Thus the reported occlusion is
original behavior, not justification for a priority override. This is native
visual corroboration of ordering, not a matched engine/native pixel comparison.

Full Render_Sprites traversal preserves bucket0 before5. No boss suppression was
found in sub_5758A or loc_57BF6. Corrected a misleading engine comment: flag$40
is multi-draw, while CLEAR bit2 selects screen coordinates in loc_1AE58.

### Shared arena-mask trial — 2026-09-24

User approved explicit activation for the GHZ/MTZ replica fights only; integration
and push require confirmation of the live trial. Common ArenaMaskState supplies
activate(width)/release()/advance(), captured inside SSZ runtime. The common
renderer reads the semantic ArenaMaskSource contract, never zone or boss IDs,
and draws into the current framebuffer before HUD.45-frame reversible envelope,
per-gameplay-frame noise, no gameplay RNG; native width is a render no-op.
The SSZ event coordinator derives activation/release from its existing lock owner.

Focused queued Java21/Maven run (absolute S3K ROM, DISPLAY=:0, native GL enabled):
TestArenaMaskState, TestArenaMaskRenderer, TestNativeArenaCameraFraming,
TestS3kSszGhzArenaHeadless, TestS3kSszMtzArenaHeadless, TestSszColdRouteCapture,
TestS3kAiz1SkipHeadless, TestSonic3kLevelLoading, TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils:115tests, zero failures/errors/skips. After making the
GraphicsManager entry package-private through an internal bridge and adding
event activation assertions, reran mask state/realGPU/both replica events/cold
route:31tests, zero failures/errors/skips, finished09:24:53BST. GPU checks cover
all five widths at1x/2x, offset viewports, capture FBO, centre identity, per-frame
noise and deterministic replay, and GL state restoration. Cold route retains
its ten full-registry rewind/replay spots with the additional presentation state.

Live shader recordings: campaign-20260924-{ghz,mtz}-live-static-{320,800},450
frames each from explicit checkpoint/40rings/neutral input. Full MP4 decodes pass.
All450 gameplay CSV rows per recording match pre-mask camera-fixed recordings
exactly. All300 common filmed frames preserve the central320 pixels exactly
before encoding; native320 entire frames are identical. Wide frame400 of both
fights inspected: mask remains active through knockback, HUD readable.
Shared implementation is also present as uncommitted trial code in separate
codex/ssz-arena-static-demo. Reconcile the common patch once at integration.
Full combined suite/guards, broader lifecycle/display-shader coverage and user
confirmation remain pending. No runtime feature commit or push claimed.

### Defeat/release review captures — 2026-09-24

GHZ and MTZ defeat/exit recordings now cover400/528/800. GHZ uses a declared
checkpoint(512,1992), Sonic+Tails,355rings; the authored input defeats the boss,
waits, then jumps onto the pad.1707frames, film350..1706, zero deaths; at1500
player(512,1382) has exited and the mask is gone. MTZ uses checkpoint(5888,1056),
soloSonic,355rings and the existing ssz-mtz-fight-chase input:2600frames,
film1600..2599, zero deaths. At2369 player(5888,81) is above the arena with no
mask. All widths share these positions; viewport-specific camera X retains
centred framing. GHZ wide1230/1400 retain the mask after boss defeat until the
pad releases bounds;1500 shows full width restored. Archives:
`campaign-20260924-ghz-checkpoint-exit-{400,528,800}` and
`campaign-20260924-mtz-unlock-{400,528,800}` under external SSZ task root.

Earlier campaign-20260924-ghz-unlock-{400,528,800} attempts replayed the320cold
route, diverged in earlier traversal and never reached the boss. They are failed
route attempts, NOT release evidence; wide cold-route traversal remains open.
Read-only independent review of shared mask/SSZ adapter found no actionable issues.

### Restart recovery and rewind coverage closure — 2026-09-24

User approved the defeat/release clips and authorized delivery after checks.
Campaign `454184d52` and shared mask `55e8ad12d` were reconciled at `679f7cb87`
with develop `40d55783c`. The PC restart stopped combined run
`20260924T083621Z-e65ae7e2`: 6155 reported tests, one failure, no errors, two
opt-in benchmark skips (SMPS repeated playback and checkpoint cost); guards
had not run. This is incomplete validation. The failure is
`TestRemainingRewindCoverageClosure#coverageBaselineHasNoRemainingRecreateGaps`:
the campaign had introduced 32 exceptions into develop's empty baseline.
Inspection finds constructor-derived spawn constants and typed ObjectRefId
sidecar links. Declare those contracts explicitly and remove the exceptions;
do not weaken the empty-baseline requirement. Focused and combined reruns are
required before delivery.

Focused closure verification: queued Java21/Maven with absolute S3K ROM,
`-Dtest=TestRemainingRewindCoverageClosure,TestRewindCoverageGuard,TestRewindFieldDispositionGuard,TestS3kSszCarriersAndSprings,TestS3kSszEggRobo,TestSszColdRouteCapture,TestS3kSszAct2FinalFight`:
33 tests, zero failures/errors/skips. The first focused run exposed two more
undeclared final-fade fields; these now declare spawn reconstruction and managed
reference capture. The final-fight test adds a live-fade checkpoint restored after
retirement, at both 320 and 800 pixels. This covers the link that a pre-fade
checkpoint cannot exercise. Java21/Lua5.4/PowerShell preflight passed.

### Automatic bounds-derived mask conversion — 2026-09-24

The user superseded the approved two-arena opt-in with an automatic default.
Interrupted broad run `20260924T090401Z-a941c19f` intentionally before changing
runtime code; it is incomplete and is not delivery evidence. Shared code now
derives the clear horizontal world interval from native camera origins and the
native view width, retaining independent left/right edges in the displayed scroll
generation. SSZ activation calls and runtime mask bytes are removed. Ordinary
finite level edges also participate; native width, wrapping foregrounds and
transient inverted bounds do not fabricate a mask.

The first conversion's 38 focused geometry/GPU/retained-presentation/replica/cold
route tests passed with no skips. Its no-fade videos are intermediate evidence
only: the user requested that already-visible scenery fade when newly covered.
A common captured transition now tracks opacity by world column. Newly revealed
offscreen pixels arrive masked, old opaque wings stay opaque, newly covered
visible pixels fade in and released pixels fade out. No event activation switch
is reintroduced. The first fade run exposed a floating-point release residue;
clamping the final epsilon to its target fixes that endpoint. Final verification
and replacement videos remain required. The entire level campaign remains open.

Final focused conversion check (queued Java21, DISPLAY=:0, native GL, absolute
S3K ROM): mask geometry/GPU/transition, retained sprite presentation, both SSZ
replicas, SSZ cold-route replay and the required AIZ/load/bootstrap/decode classes:
99 tests, zero failures/errors/skips, completed 10:17:13 BST. This is focused
validation, not the combined delivery suite. Six replacement videos decode fully:
SSZ GHZ800/400 and MTZ800/352 defeat/release; SSZ and LRZ800 approach/activation.
All have zero deaths. GHZ800's1707 and MTZ800's2600 gameplay CSV rows match the
preceding approved explicit-mask runs exactly. Inspected both800 release stills
and LRZ before/during activation: the newly covered visible region fades, the
previously opaque wing stays opaque, and both replica exits restore full width.
Archives: `campaign-20260924-{ghz,mtz}-bounds-derived-fade-{width}` under SSZ,
and `campaign-20260924-bounds-activation-800` under each SSZ/LRZ external task root.
LRZ demonstrates automatic activation without a new level-specific call.


### 2026-09-24 — bounds-mask feedback and ordinary delivery run

At compiled candidate `89e7f0791`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk
LUA_BIN=/usr/bin/lua5.4 DISPLAY=:0 python3 tools/testing/run_categories.py --base
1adf27cb5c6324adafab134821a33d944a60bbe1 --run` completed 2,905 ordinary
classes / 23,646 tests in 1,308.64 seconds: 14 failures, 2 errors, 29 skips.
The working tree changed during that lane as user-requested mask corrections
were implemented; the runner refused the guard lane. This is incomplete delivery
validation, not a suite pass. The compiled ordinary candidate predates the latest
world-space fade/temporal feather and null-renderer registration corrections.

Exact failing test identities (unattributed unless stated):

- `com.openggf.game.rewind.TestSonic1StomperDoorRewindOwnership#productionLevelRegistrationRunsSbz3ReconcileAfterObjectManagerRestore` — new mask registration dereferenced an unattached renderer; correction pending focused verification
- `com.openggf.game.session.TestGameplayModeContextRewindRegistry#decoratedStockEventsRegisterRewindStateAndCustomZonesRemoveIt` — new mask registration dereferenced an unattached renderer; correction pending focused verification
- `com.openggf.game.sonic3k.TestS3kSidekickIntroPresentationGate#sszDormantCpuBranchDoesNotArmAizOrIczPresentationLatch` — SSZ's separate ROM $0A00 branch must not acquire the AIZ/ICZ presentation gate ==> expected: <false> but was: <true>
- `com.openggf.game.sonic3k.TestSonic3kPlcArtRegistry#sszPlanHasEggRobo` — expected: <16> but was: <22>
- `com.openggf.game.sonic3k.objects.TestCnzMinibossRegistered#cnzMinibossIdIsNotInSharedSet` — CNZMiniboss (0xA6) must NOT be in SHARED_IMPLEMENTED_IDS — it is zone-set-specific ==> expected: <false> but was: <true>
- `com.openggf.game.sonic3k.objects.TestLbzGateLaserObjectInstance#registryRoutesS3klSlot21ToLbzGateLaserOnly` — SKL slot $21 is Obj_LRZSmashingSpikePlatform, not the LBZ gate laser ==> Unexpected type, expected: <com.openggf.level.objects.PlaceholderObjectInstance> but was: <com.openggf.game.sonic3k.objects.LrzSmashingSpikePlatformObjectInstance>
- `com.openggf.game.sonic3k.objects.TestLbzLoweringGrappleObjectInstance#registryRoutesS3klSlot1fToLoweringGrappleOnlyForLbz` — Unexpected type, expected: <com.openggf.level.objects.PlaceholderObjectInstance> but was: <com.openggf.game.sonic3k.objects.LrzLavaFallObjectInstance>
- `com.openggf.game.sonic3k.objects.TestLbzPipePlugObjectInstance#registryRoutesS3klSlot1bToLbzPipePlugOnlyForLbz` — SKL slot $1B is Obj_LRZFireballLauncher, not the LBZ pipe plug ==> Unexpected type, expected: <com.openggf.level.objects.PlaceholderObjectInstance> but was: <com.openggf.game.sonic3k.objects.LrzFireballLauncherObjectInstance>
- `com.openggf.game.sonic3k.objects.TestLbzSpinLauncherObjectInstance#registryCreatesSpinLauncherOnlyForS3klLbz` — Unexpected type, expected: <com.openggf.level.objects.PlaceholderObjectInstance> but was: <com.openggf.game.sonic3k.objects.LrzDashElevatorObjectInstance>
- `com.openggf.game.sonic3k.objects.TestPachinkoRegistry#registryCreatesPachinkoBumper` — expected: <true> but was: <false>
- `com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet#customCompatibleFactoriesCannotReadStockZoneIdentity` — expected: <[]> but was: <[41]>
- `com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet#everyStockZoneDependentFactoryIsExplicitlyInventoried` — expected: <[236, 237, 145, 146, 147, 255, 74, 75, 176, 180, 181, 182, 183, 184, 3, 196, 197, 6, 195, 200, 9, 10, 203, 12, 202, 204, 205, 201, 16, 198, 115, 116, 19, 18, 17, 11, 20, 27, 30, 31, 33, 131, 35, 230, 231, 232, 234, 139]> but was:
- `com.openggf.game.sonic3k.objects.TestSonic3kModZoneObjectSet#stockZoneDependencyInventoryRemainsExplicitFactoryMetadata` — S3KL custom-compatible collision at object $75 ==> expected: <false> but was: <true>
- `com.openggf.game.sonic3k.objects.badniks.TestStarPointerBadnikInstance#profileMarksStarPointerImplementedForS3klLevelsOnly` — expected: <false> but was: <true>
- `com.openggf.sprites.playable.TestS3kReverseGravityRenderMirror#theFlagMirrorsTheDrawWithoutTouchingTheAnimatorsOwnFlip` — loc_10C62 sets render_flags bit 1 ==> expected: <true> but was: <false>
- `com.openggf.tests.TestS3kDezShockBlockHeadless#placedShockFloorUsesTheShieldReactionAndReplaysAfterRewind(String)[5]` — only bit 5 answers the shock reaction ==> expected: <false> but was: <true>

Skips were explicit diagnostics/capture/soak opt-ins, four unavailable EGL/OpenGL
checks, one CPZ spin-tube assumption, and local audio reference/output prerequisites.
No missing-ROM skip was reported. The arena GPU check was separately exercised
with `-Dopenggf.test.gl.native=true`.

The user's second clarification rejects screen-space fade history: coverage and
its fade must move with level coordinates. Restore world-space mapping but let
new viewport columns inherit adjacent fade progress rather than full opacity.
The 12px feather now controls duration (45–90 ticks), not final opacity; release
retains the old world-column rate. Isolated JUnit Console validation of
TestArenaMaskState, TestArenaMaskRenderer, TestLevelBoundsMaskTransition and
TestLevelSpritePresentation passed 16 tests with no skips, using the actual native
GPU. These classes were compiled under target/bounds-visual-classes without
changing the delivery run's target/classes. Maven verification is pending.

Refreshed positioned previews live in external SSZ/LRZ capture directories
`campaign-20260924-bounds-world-feather-800`. All 450/600 CSV rows exactly match
the prior recordings, no deaths; both movies fully decode. They demonstrate the
corrected presentation, not cold-route completion. See the arena design for the
rejected screen-space trial and startup camera-coordinate evidence.


Destination refinement: the user identified the native camera-lock ramp as the
remaining spatial delay. Read `loc_85D06`, `loc_85D28`, `loc_85D36` and
`sub_85D6A` in the ROM disassembly: they distinguish the live boundary from the
stored destination. `CameraBoundaryPresentation` exposes that destination through
an internal rewind sidecar, and S3kSharedBossCameraGate supplies it without any
mask-specific boss/level switch. Native movement remains unchanged. Interrupting
boundary assignments supersede the pending rectangle; per-column fades reverse
from their current value immediately.

Queued Maven focused validation (Java21, native GL, absolute s3k.gen):
- `-Dtest=TestS3kSharedBossCameraGate,TestLevelBoundsMaskTransition,TestLevelSpritePresentation,TestGameplayModeContextRewindRegistry,TestArenaMaskState,TestArenaMaskRenderer -Dopenggf.test.gl.native=true test`: 46 tests, no failures/errors/skips.
- `-Dtest=TestLrzMinibossInstance,TestS3kLrzBossRewindHeadless,TestS3kLrzBossCameraHeadless,TestCameraRewindSnapshot,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`: 109 tests, no failures/errors/skips.
- Previous world-space correction plus the two null-renderer registration failures:
  48 focused tests passed, no skips, before destination refinement.

SSZ450/LRZ600 positioned destination previews under external
`campaign-20260924-bounds-destination-feather-800` directories fully decode and
have identical gameplay CSVs to their preceding world-space captures, zero deaths.
Earlier screen-space and live-bound videos are superseded visual experiments.

Focused fresh-JVM guards also pass: queued Maven `-Pguards
-Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestRewindFieldDispositionGuard
test` ran three tests, zero failures/errors/skips. This is the selected rewind
guard scope, not the withheld full guard lane. The consumed broad-run diagnostics
were acknowledged and deleted after recording their findings above.


### 2026-09-24 — fade accepted; campaign failure follow-up

User accepted the mask transition work through `9f20ead7c` and requested resuming
the full seven-zone goal. Current-bound source/target crossfade is the accepted
presentation; the intermediate destination/feather experiments remain history.
No main integration or push has occurred yet.

The ordinary-run failures above were narrowed by a queued Java21/absolute-ROM
170-test run of their12 classes. The bonus-stage bumper was a real registration
regression: the new SKL DEZ fallback ran before the glowing-sphere identity check.
The explicit bonus identity now takes precedence. Four LBZ tests expected LRZ
placeholders; they now assert the actual LRZ classes while preserving LBZ checks.
CNZ/StarPointer audit assertions confused numeric-slot coverage with class identity
across the two pointer tables. SSZ's art test no longer fixes the count of unrelated
entries; it still verifies EggRobo's key/palette, with separate Eggmobile art checks.

The remaining failures were evidence-backed test corrections. SSZ's actual
loc_13AB4 branch includes $0A00 and parks Tails; its test now checks act1 versus
act2 with a module-scoped runtime. The registry source audit omitted literal
registrations, wrongly attributing stock slot$25's read to preceding slot$29;
a new positive/negative literal regression guards that boundary. Its explicit
inventory and compatibility expectations now include LRZ/SSZ overrides and the
independent base factories they retain.

DEZ floor probe: the old start at($780,$780) hits Obj_DEZLightning at frame4,
well before the floor at($780,$7D2). That object writes collision_flags=$9F but
no shield_reaction bit5 (loc_478BE..loc_4791A); immunity belongs to the separate
shock floor. Starting between the bolts at x$76C reaches and stays on the actual
placed floor with lightning shield intact. A separate production-loop test now
asserts that the bolt does remove that shield. The reverse-gravity draw test had
advanced physics over terrain before requiring the animator's own flip to remain
unchanged; the probe shows flag=true and storedFlip=true, whose draw XOR isfalse.
It now tests the draw composition immediately, without an unrelated physics step.

The first focused run had170tests,5failures,1error,0skips; remaining three audit
assertions, the two DEZ setups, and the SSZ test's missing runtime explain those
results. The corrected five-class rerun (`TestS3kSidekickIntroPresentationGate,
TestSonic3kModZoneObjectSet,TestS3kReverseGravityRenderMirror,
TestS3kDezShockBlockHeadless,TestS3kDezLightningHeadless`) passed31tests with no
failures/errors/skips. The other original failure classes passed in the first
run. These are focused attribution/correction results, not a new broad pass.
Combined campaign ordinary/guard validation remains required.


### 2026-09-24 — second SSZ replica cold-route closure

After merging develop `e6c6ac79a` without conflicts (`093938e94`), the fixed-input
SSZ1 native320 Sonic+Tails route now reaches both replica defeats and the second
pad's upper platform in11051frames,0deaths,2rings. Its16rewind spots include the
MTZ approach, live fight, defeat, ascent and receiving platform. Queued
`-Dtest=TestSszColdRouteCapture -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen test` passes
1test with0skips. The first launch preceded the authored movie and failed on that
missing file; the completed input's author round-trip and rerun pass.

The external `ssz-bring-up/campaign-20260924-cold-second-replica-exit-320/capture.mp4`
films the new portion (frames8000–11050) after a real cold boot. All11051 state
rows match the input probe, and the video fully decodes. Upper traversal remains
under input authoring; walking right off the pad is not the route. Native trace
positions show a leftward platform traversal; they inform navigation only, never
hydrate or correct gameplay state. No route completion beyond the upper-platform
frontier is claimed yet.

Combined selection against updated develop `e6c6ac79a` is full ordinary2906classes
plus guards. Java21/Lua5.4/PowerShell preflight passes when launched with explicit
JAVA_HOME and LUA_BIN. A first preflight without the known Lua override rejected
system Lua before running tests. The campaign validation remains pending.


### 2026-09-24 — combined validation and SSZ spring finding

At `fed07f595`, `run_categories.py --base e6c6ac79a --run` completed all
2,906 ordinary classes: 23,656 tests, zero failures/errors, 29 skips, 1,257 seconds.
The skips include opt-in captures/performance/soak checks, native graphics checks,
and an inherited CPZ spin-tube assumption; they do not establish those obligations.
The interrupted invocation lost its parent before the guard lane completed.
Its surviving JVM subsequently exited: guard completion is **not established**.
One observed guard failure was a false positive in `TestTraceV5PositiveInputGuard`:
its unquoted numeric-sequence regex treated animation arrays, a ValueSource and
ROM table prose as retired CSV rows. The correction restricts that check to quoted
CSV literals and standalone text-block rows, with array/prose and text-block
regressions. Queued `-Pguards -Dtest=TestTraceV5PositiveInputGuard test` passes
5 tests with no skips; the full guard lane still needs completion. Diagnostics
from the interrupted run were inspected and acknowledged.

Further cold SSZ traversal exposed a real spring defect. `Obj_SSZRetractingSpring`
calls `sub_1DD0E`, whose `loc_1DECE` path enters the full solid classifier at
`loc_1DFFE`; side contact returns bit 16 in d6. After `swap d6`, `sub_46536` tests
bit 0, not the standing result in bit 20. The engine had selected top-only collision
and accepted standing as a launch trigger. A temporary isolated correction carries
the player through the three upper spring ramps using neutral input, where the
unchanged implementation passes the spring without launching. This diagnostic is
not a completed production cold route. The correction also changes the earlier
springs, so the existing fixed controller route needs reauthoring rather than any
fixture-specific exception in gameplay.


The subsequent complete guard run (`-Pguards test -B`) reached672tests in3:09,
with6failures,0errors,0skips. Besides the already corrected CSV guard, the real
findings were28 new object-local transient annotations,57 V-int naming cases,
one missing DEZ flame touch profile,15 excess ObjectManager facade lines,
five graphics-to-gameplay scroll-split edges, and a camera-to-level edge.
The repairs retain the same spawn-derived exclusions in the central rewind
policy, name update clocks `vIntRunCount`, expose the flame's touch profile,
move restored dynamic ordering into its ownership collaborator, and place
scroll-split data in graphics while resolving camera policy in the game layer.
No structural baseline was increased. The first combined repair selection
(`TestSszColdRouteCapture,TestS3kSszCarriersAndSprings,TestEveryObjectRewindRoundTrip,
TestNativeArenaCameraFraming,TestDezFinal*,TestS3kSsz*Rewind*,TestS3kSszCompatibilityMatrix`)
passes1484tests,0failures/errors/skips, including the refreshed22-spot cold route.
The remaining guard rerun and presentation/collaborator checks are pending.


Follow-up validation: `TestObjectManager*Rewind*,TestS3kSszCraneRouteHeadless,
TestForegroundWindowRendering` with `-Dopenggf.test.gl.native=true` and native
display passes38tests,0skips. The corrected full guard invocation completes
672tests with1failure,0errors/skips: ObjectManager remains3lines over its
existing3086-line facade budget. Moving restored-identity validation into
DynamicObjectOwnership alongside ordering resolves that final finding without
raising the budget. The final ordinary `TestObjectManager*Rewind*` run passes
35tests,0skips, and `-Pguards -Dtest=TestArchitecturalSourceGuard,TestArchUnitRules`
passes101tests,0skips. This is a complete guard run followed by targeted repair
verification, not a claim that the red invocation itself passed. Integration,
combined final validation and push are still outstanding.


### 2026-09-24 — complete cold SSZ route and player priority rewind

The native320 Sonic + Tails route now defeats all three SSZ bosses and actually
loads DEZ1 in19,492 ordinary controller frames, zero deaths. The complete movie
and its RLE input source live under `src/test/resources/routes/s3k/`; the short
replica-only test remains independent. `TestSszColdRouteCapture` adds15 late
full-registry replay spots (37 total) and verifies outgoing/incoming timeline
isolation across the real DEZ load. The SSZ act1 matrix records exact scope.

The first complete-route replay failed at input14840: player follower history
contained `$80` instead of zero after restore. `PlayerRewindExtra` omitted both
live hardware priority and the independent sprite display bucket. Saving and
restoring them repairs the state leak without changing normal forward gameplay.
A small bidirectional regression reproduced the omitted tile bit before the fix.
The final focused player/sprite/full-route selection passed24tests,0failures,
0errors,0skips with native GL and the actual S3K ROM. Broad delivery checks remain
owed for the accumulated campaign; this focused result does not replace them.

The cold completion video under
`$VIDEO_ROOT/ssz-bring-up/campaign-20260924-cold-complete-320/capture.mp4`
shows frames16900–19731, including240 neutral incoming DEZ frames. State rows,
three milestone stills and full MP4 decoding were checked. The accepted mask
logic is unchanged. DEZ cold traversal is the next route frontier.

The focused rewind-field/coverage/architecture guard selection also passed
78 tests, zero failures/errors/skips (`-Pguards`,
`TestRewindFieldDispositionGuard,TestRewindCoverageGuard,TestArchitecturalSourceGuard,TestRewindArchitectureGuard`).


### SSZ exit presentation correction (2026-09-24)

The user identified a mirrored Sonic spiral and incorrect-looking exit overlaps in
`campaign-20260924-cold-complete-320`. Native BizHawk 2.11 observations from
`s3k-sonic-tails-complete-emeralds.bk2` frames467450–468950, plus frame467920
VRAM, distinguish two omissions rather than justify changing ramp priorities:

- `loc_58016` clears render flag bits0/1 and sets object control3. The engine
  retained Sonic's facing flip while Tails happened to be unflipped. The launch
  controller now clears both flips and gives its mappings animation ownership.
- S2 `Obj01_Control` and S3K `loc_10C26` apply the player Y mask only when the
  current camera minimum is `-$100`. The shared engine path checked only the
  level's configured wrap range. It changed SSZ's parked `$7FFF` to `$FFF`,
  defeating the crumble controller's `y >= $4000` fallback and delaying its first
  clear by about64frames. Those late writes erased already-visible column rows.
  The current-bound guard preserves the configured range for later reactivation.

The ramp sprite buckets and art priority bits agree with `loc_581F2`,
`loc_58234`, `loc_582AC`, `loc_58360`; changing them was rejected. At the same
player pose(6723,1080), old engine frame18581 had24 cleared plane rows versus
native frame467920's10. Corrected footage restores the column and unmirrored
Sonic. This is a pose-based visual comparison, not strict trace parity: camera Y
is943 versus945, and the routes/timing/HUD differ.

Both regression tests failed before their corresponding corrections. Queued
Java21 verification on93ae8c010 plus this patch used `-Dmse=off`, native GL,
absolute S3K ROM and `-Dtest=TestPlayableSpriteMovement,TestSszColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
239passed,0failures/errors/skips. This includes the complete cold route,37
rewind/replay spots and actual DEZ load. Combined campaign delivery remains owed.

Replacement footage is
`$VIDEO_ROOT/ssz-bring-up/campaign-20260924-exit-corrected-320/capture.mp4`:
19,732 state rows, zero deaths, frames18000–19731 filmed, complete MP4 decode.
Native evidence is under `$VIDEO_ROOT/ssz-bring-up/native-exit-20260924`.


### DEZ1 cold upper-route frontier (2026-09-24)

The preserved controller route now reaches(9589,640) in5301frames without deaths,
from the normal DEZ1 intro with Sonic+Tails at320. It passes both early lift pads,
the conveyor/elevator stack, stair platforms and the timed energy bridges. The
bridge crossing initially fell into a monitor alcove: its three `$55:$66` bridges
were off, and repeated jumps either hit the ceiling or ran out of floor. Waiting
on the approach for the next bridge activation and jumping before expiry crosses
normally. Neither those rejected inputs nor the native recording's own repeated
attempts justify an engine change. The committed BK2 contains only inputs.

`TestDezColdRouteCapture` passed its21 full-registry replay spots and final carried
endpoint; focused result1pass,0skip. The first endpoint check used an input still
being truncated; final5301-frame authoring and a fresh test invocation resolved it.
The act matrix records command, scope and replay frames. Video:
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-cold-upper-320/capture.mp4`,
frames2910–5300, verified state rows and full decode. Continue from the upper moving
pad atX9589; turbine/miniboss/cold Act2 remain open. Broad campaign delivery is pending.


### DEZ1 turbine exit reached; signed control gate repaired (2026-09-24)

The cold route now carries Sonic+Tails through the middle lift, descending
conveyor, lower electric corridor and all six turbine panels, then through the
actual door to(10763,2096). The main route frontier is8593frames;40 full-registry
restore/45-frame forward comparisons pass. The earlier upper route remains an
independent shorter test. Fixed controller inputs preserve the useful authoring
result; exploratory failed paths remain temporary.

The native reference at complete-run DEZ row8160 names lift pad `$47440` in slot5
at(8720,1557), corroborating the required ride before leaving the middle ledge.
Running off early and jumping before the carrier descended were rejected input
attempts, not engine defects. A temporary authoring controller also re-armed its
jump on the next carrier; replacing that with fixed neutral input restored the
intended conveyor descent. None of that probe logic is runtime code.

A real route blocker remained after panel byte`$3F`: the exit door refused the
positive turbine control state. `sub_30F58` rejects only negative control (`BMI`),
not every nonzero value. The existing engine bit7 predicate repairs both door
orientations without a zone exception. Unit regression failed before correction;
88focused tests then passed, followed by both final cold routes with no skips.
The act matrix gives exact selection and replay frames. Mirrored pitfall guidance
was corrected because it explicitly recommended the overly broad predicate.

Video: `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-cold-turbine-exit-320/capture.mp4`.
Cold input/state8593frames, zero deaths, filmed7840–8592, selected stills and full
MP4 decode inspected. Continue beyond the door; later DEZ1 traversal/miniboss and
cold Act2 remain open. Combined campaign verification/integration are still owed.

### DEZ1 ordinary cold completion and detached-child rewind (2026-09-24)

On `23bf09e25` plus this change, `dez1-sonic-tails-cold-complete-320.bk2`
extends the turbine route through the launcher/conveyor ascent and the ordinary
miniboss. Both phases receive eight real hits; the second phase is completed by
retreating between arm sweeps, not by altering the boss. The actual Act 2 load
occurs at input14230 (14,231 total frames), with Sonic+Tails and no deaths,
position/health/emerald overrides or native-state hydration. The authored input
script is compressed by held-button runs; the BK2 contains the same frames.

Fresh snapshots after projectile retirement exposed dangling creator references:
`loc_7E916`/`loc_7E972` fragments never read a parent, and
`CreateBossExp00`/`CreateBossExp06` use stationary copied positions rather than
`Obj_WaitForParent`. The engine now omits those unused live references. Actual
follower bursts still retain their parent. The cold test first failed on an orb
fragment reference and then on the final finite burst after boss deletion;
neither failure was a sprite-priority defect. The shorter hazard regression now
captures and replays after the creator has disappeared as well as before it.

The complete route adds22 full-registry45-frame replay spots at8800,8900,9350,
9490,9650,9930,10070,10500,11080,11260,11420,11570,12190,12320,12470,12640,
12740,13100,13270,13860,13950,14100. The earlier upper/turbine tests retain40
independent spots. Live history is armed after those probes to test the actual
load boundary. Seamless transitions retain the logical frame counter and re-root
the oldest seekable snapshot; a first test incorrectly expected a zero counter.

Media: `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-cold-complete-320/capture.mp4`
films11250–14349 after the full cold prefix. All14,350 state rows contain no death;
selected combat/defeat/arrival stills and the complete MP4 decode were inspected.
This establishes ordinary native-width reachability, not native pixel parity or
other width/character/donor coverage. DEZ2 cold traversal and the campaign's
remaining matrix obligations continue separately.

Focused verification used queued Java21, native GL and the absolute S3K ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestDezMiniboss*,TestDezColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
108 tests,107 passed, one test-oracle failure at the seamless counter assertion,
zero skips. After correcting only that assertion, the focused
`-Dtest=TestDezColdRouteCapture#coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo`
passed1 test, zero skips. No remaining failure in that selection; this is focused
validation, not a combined campaign suite pass.

The post-fix `campaign-20260924-cold-act2-arrival-320/capture.mp4` continues
through the floor opening, launch and free control at(320,940), filming13840–15239.
All15,240 state rows are death-free; stills14840/15030/15230 and full video decode
were inspected. This additional clip uses the same cold input followed by neutral.

### Cold incoming route: vertical tube and tail mirroring (2026-09-24)

On `75e536735` plus this patch, ordinary Sonic+Tails input continues from the
verified DEZ1 clear through the Act2 entrance and the first conveyors. At the
placed vertical tube(3136,1248), the old implementation froze Sonic atY1057
with Y velocity1384. ROM `loc_49120` sets object_control bits6 and1, leaving bit0
clear; `Sonic_Control` at `loc_10BFC` therefore still runs movement. The tube now
uses the existing movement-active control state. It swings X while ordinary
player physics carries Y through the span. No shared physics algorithm changed.

`TestS3kDezGravityTubeRouteHeadless#placedVerticalTubeKeepsPlayerPhysicsMovingThroughItsSpan`
reproduced the stall before the fix using the actual placement. It now checks
progress, release and full-registry capture/restore/forward replay during the ride.
The cold input reaches the gravity hub at(3136,1472) with11rings and no death;
that hub intentionally needs a fresh direction press rather than a continuously
held direction, per its native input contract. Further traversal is still open.

The reverse-gravity inventory also exposed missing `Obj_Tails_Tail` rendering.
`loc_1613C` mirrors non-directional tail animations, except animation3, whose
angle already supplies its flips. `TailsTailsController.draw` now composes the
flag without mutating stored animation state. The regression covers standing,
spindash, flying, directional rolling, flag release and unchanged rewind state.
The group F row is covered; dust, Knuckles and other listed gaps remain open.

Queued Java21 with the absolute S3K ROM, `-Dmse=off`:
`-Dtest=TestS3kDezGravityTubeHeadless,TestS3kDezGravityTubeRouteHeadless,TestS3kReverseGravityRenderMirror,TestTailsTailsFlightSelection,TestTailsTailsDirectionalAnimation,TestSpriteManagerMainTailsTailsDispatch,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed87 tests, zero skips. The subsequent explicit in-tube replay addition was
verified with `-Dtest=TestS3kDezGravityTubeRouteHeadless`:3 passed, zero skips.
The change-based plan selects the full suite through shared tail code; these are
focused iteration checks, with combined campaign delivery validation still owed.

Preserved `dez2-sonic-tails-incoming-first-hub-320.{script,bk2}` contains the
verified16,201-frame cold prefix through this hub. Its authored BK2 input rows
were compared exactly with the explored movie. The inspected native320 clip
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-vertical-tube-320/capture.mp4`
films15780–16599; all16,600 state rows are death-free and full video decode passes.
This is engine visual evidence; native pixel parity and other widths remain open.

### Reverse-gravity dust consumers (2026-09-24)

On `b89b2c41f` plus this patch, `loc_18C20` now composes the spindash-dust Y flip
and reverses Tails's four-pixel centre adjustment. `loc_18D14` now negates the
complete per-character skid-dust foot offset at spawn. Detached skid puffs keep
their own world coordinates; gravity changes do not relocate an existing puff.
No new state, ROM assets, game/zone carve-out or public API was added.

The new `TestS3kReverseGravityRenderMirror` case failed before the change, then
passed for Sonic and Tails, upright→inverted→upright, using the renderer call and
actual spawned skid coordinates. Queued Java21, absolute S3K ROM, `-Dmse=off`:
`-Dtest=TestS3kReverseGravityRenderMirror,TestSpindashDustControllerSplash,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed71 tests, zero skips. The change-based plan selects the full ordinary suite
through shared code; this is focused iteration, with campaign-wide validation
still pending. Group F's two remaining rows are now covered; the table records97
covered ROM references and eight remaining group A-I gaps rather than certifying
the whole reverse-gravity implementation.

Positioned visual evidence:
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-inverted-tails-dust-320/capture.mp4`
uses Tails at(4700,1267), declared reverse gravity,181 controller frames,
`30 -;20 D;1 D+A;90 D;40 R`. Frames70/120 are charging; the visible dust sits at
the ceiling with Tails's mirrored centre offset. This is engine inspection,
not cold-route or native-pixel evidence.

Independent ordinary route authoring continued from the preserved first hub:
release Right before the hub's fresh Right press; jump the ceiling steps, and
wait120frames before the Spikebonker crossing. The explored cold route reaches
(5899,1171) without changing the enemy. Later traversal and route breadth remain
open; exploratory inputs stay in the external campaign route-author directory
until the next stable route is preserved.

### Knuckles ceiling slide get-up and lower-route research (2026-09-24)

On `8ca9b0ea2` plus this patch, the remaining `Knuckles_Sliding .getUp` gravity
row is implemented. ROM `loc_16B2A` computes liveYRadius−defaultYRadius,
negates that word under Reverse_gravity_flag, then adds it to y_pos before
Knux_TouchFloor restores standing radii. The engine previously always applied
the upright adjustment: the new regression measured823 where841 was required
for an inverted centre at832 with radii10→19. The correction uses a native
centre-word addition and retains the fractional Y word. No physics constants,
terrain probes or wall-climb behavior were changed.

`TestPlayableSpriteMovement#knucklesSlideGetUpPreservesFeetAndFractionUnderReverseGravity`
failed before the correction and passes afterward, covering both gravity signs,
radius restoration, grounding and a nonzero Y fraction. Queued Java21,
`-Dmse=off`, absolute S3K ROM:
`-Dtest=TestPlayableSpriteMovement,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed238 tests, zero skips. The change-based plan selects the full ordinary
suite through shared movement; this is focused iteration pending combined
campaign validation. The inventory now records98 covered references and seven
open group A-I rows; this does not certify Knuckles's remaining gravity paths.

The earlier rightward route from the first DEZ2 hub reached a monitor alcove at
Y1171. The native complete-emeralds recording reaches this region from below:
trace rows25260–25680 travel from(5640,2604) through the rising teleporter to
(6078,1363). It is not evidence that the alcove's wall collision is wrong.
Rows21545–21610 show a Down launch/bounce before the Left exit from the first
hub. Controller authoring also reaches the lower route directly with a fresh
Left press. The descending conveyor between spiked walls requires staying near
its middle until the lower exit; immediate Right drift or an early jump hits
the wall. Native rows are comparison/route research only, never gameplay writes.

The corrected controller input keeps the rider nearX1560 against the conveyor's
belt untilY1960, then exits right. The full cold route reaches(1937,2003),19rings,
zero deaths in17,180frames. This is a lower-route frontier, distinct from the
previous monitor-alcove X maximum. The inspected
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-lower-conveyor-320/capture.mp4`
films16200–17179; state rows, selected stills16750/16900/17150 and full MP4 decode
checked. `campaign-20260924-route-author/act2-lower-centred-belt.{script,bk2,csv}`
preserves the external exploration, with no gameplay state writes. The next
obstacle is the corridor's spiked overhang; no collision change was made for it.


### DEZ2 lower staircase and second tube cold route (2026-09-24)

On `1ccf38505` plus this validation change, the ordinary cold DEZ1 Sonic+Tails
route now reaches DEZ2(3196,2476), seven rings, in17921frames with zero deaths.
The preserved `dez2-sonic-tails-incoming-lower-320.{script,bk2}` continues through
the incoming sequence, first gravity hub, descending conveyor, spiked overhang,
Spikebonker, moving staircase and second tube's polarity release. Jumping before
the overhang removes the stationary approach problem; waiting120frames at the
next step avoids the Spikebonker. No runtime collision or enemy adjustment was
needed. A later hit in the lower corridor loses rings but does not kill the player.

`TestDezColdRouteCapture#coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind`
passed1test, zero failures/errors/skips, with17 full-registry capture/restore and
45-frame forward-replay comparisons. Command: queued Maven, Java21,
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`
plus that method's `-Dtest` selection and `test`. Earlier independent DEZ1 tests
own the outgoing act's rewind spots; this test adds Act2 arrival, hub, belt,
staircase and tube spots and asserts the carried endpoint and follower roster.
This is focused route validation; combined campaign delivery remains pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-lower-route-320/capture.mp4`
films16940–17920. All17921state rows, stills17080/17440/17660, and full MP4 decode
were checked. This is engine presentation evidence, not native pixel parity.
The next frontier is the timed energy-bridge ascent nearX3264–3328. Running past
it drops into the curved lower floor. Braking over the floating platform reaches
(3253,2556); the first jump's timing misses the bridge. These rejected inputs do
not establish a collision defect. Full Act2 completion and breadth remain open.


### DEZ2 energy-bridge ascent and gravity-switch cold route (2026-09-24)

On `7d39f684a` plus this validation change, the new preserved
`dez2-sonic-tails-incoming-middle-320.{script,bk2}` reaches(4853,2371),14rings,
in18931frames from ordinary cold DEZ1 Sonic+Tails, zero deaths. It retains the
previous lower-route input and adds braking onto the floating platform, three
jumps through sequential energy bridges, the launcher crossing, gravity-switch
landing and the next corridor. The two lower bridges activate before the higher
bridge: jumping into their off phase was the route issue, not a demonstrated
collision defect. Horizontal steering onto the gravity switch was authored with
a read-only feedback probe and preserved as fixed ordinary controller inputs;
no gameplay state is hydrated or overridden.

`TestDezColdRouteCapture#coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind`
passed1test, zero failures/errors/skips, with14 full-registry capture/restore and
45-frame replay spots. Queued Java21 command: `-Dmse=off`
`-Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, the named
method's `-Dtest` selection, and `test`. This adds coverage beyond the shorter
lower route's17 spots; combined campaign validation and full Act2 remain open.

`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-middle-route-320/capture.mp4`
films17920–18930. Its18931state rows, stills18180/18340/18820 and complete MP4
decode were checked. This is moving engine presentation evidence, not native
pixel matching. Later external exploration in
`campaign-20260924-route-author/act2-spring-pair-late-jump.{script,bk2,csv}` jumps
at18960 before the spring trap, passes both transporters atX5456 and5968, and
reaches the upper corridor nearX6400. Earlier jumps hit the nearby geometry or
arrived between the opposed springs; the later jump crosses normally. That
extension still needs preserved route/rewind and video evidence; no runtime
change was made for these route-authoring failures.


### DEZ2 transporters and Chainspike parent retirement (2026-09-24)

On `812b78342` plus this patch, the preserved ordinary cold Sonic+Tails route
`dez2-sonic-tails-incoming-transporters-320.{script,bk2}` reaches(6709,1395),
15rings, zero deaths, in19810frames. It jumps out of the first spring pair,
rides both transporters atX5456/5968 and the intervening lift, then clears the
upper spring/Spikebonker approach. The route test asserts both transporter
holds and final free movement, adds14 full-registry 45-frame replay spots, and
keeps the earlier31 Act2 spots in shorter independent routes.

The new replay spot19730 failed on the unmodified parent implementation: replay
left an extra Chainspike child and used slot where forward simulation had none.
The child excluded its final parent from capture and recreated against whichever
live Chainspike was nearest. ROM `loc_91D8C` reads exact `parent3`; proximity is
not its ownership rule. The child now captures/restores an exact ObjectRefId
sidecar, and recreation preserves its saved spawn before relinking.

A generic strict reference was tried first and rejected by the capture itself:
a child can be waiting for its next update after the parent has left the manager.
An explicit sidecar represents that retired parent as null; a missing identity
for a still-live parent remains an error. This exposed the second omission:
`Sprite_CheckDeleteTouch -> loc_85094` sets status bit7 before scheduling deletion,
but manager-owned offscreen removal had left the Java body unmarked. Chainspike
now publishes retirement in `onUnload`, so `Child_CheckParent`'s child deletion
has its corresponding signal. The short regression covers exact replacement
identity, manager removal and the one-update orphan tail. No nearest-body
fallback or additional coverage gap was accepted. The architecture guard records
why this tombstone-bearing reference requires an explicit sidecar.

Queued Java21, native GL, absolute S3K ROM and `-Dmse=off`:
`-Dtest=TestDezColdRouteCapture#coldIncomingActTwoTraversesBothTransportersWithRewind,TestChainspikeBadnikInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed70tests, zero failures/errors/skips. Separate fresh-JVM `-Pguards` selection
`TestRewindArchitectureGuard,TestRewindFieldDispositionGuard,TestRewindCoverageGuard`
passed6tests, zero failures/errors/skips. This is focused iteration; campaign-wide
validation, integration, push and cleanup remain pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-transporters-320/capture.mp4`
films18930–19809 from the cold start. The corrected build's19810 state rows
are identical to the pre-fix forward capture (zero deaths); complete MP4 decode
and the upper crossing still19770 pass inspection. This is engine evidence,
not native pixel parity. Later external input exploration
reaches the gravity switch at(7232,1720): staying over it during the jump toggles
gravity and rises to(7216,659). Overshooting the switch hits the monitor corridor.
`campaign-20260924-route-author/act2-east-switch-catch.{script,bk2,csv}` preserves
that exploration; the upper-left continuation and full Act2 completion remain open.
