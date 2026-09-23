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

## Current campaign priorities (2026-09-23, after `6b016ccc5`)

The target remains all seven zones. The initial inventory below is historical;
its original unimplemented counts must not be read as current production status.

| Zone | Current evidence and next obligation |
| --- | --- |
| MHZ | Integrated Sonic/Tails route and miniboss fixes; reconcile the original audit's accepted Knuckles/trace exclusions and create the missing per-act evidence matrices before certification. |
| FBZ | Cold native Act 1 and thirteen Act 2 completion rows already recorded; finish matrix reconciliation, checkpoint geometry and presentation obligations without undoing the accepted S1 elevator challenge. |
| SOZ | Both acts, golem, end boss and playable exits implemented; cold and positioned route evidence exists. Reconcile later production/rewind checks with the remaining roster/lifecycle rows and explicit trace deferrals. |
| LRZ | Turbines, chained platforms, cutscenes, boss act, end boss and HPZ handoff implemented in this campaign. A 12820-frame fresh boss-act route reaches HPZ. Repair the Act 1 miniboss's missing runtime art submission; then retain distinct cold-route, strict-parity and breadth gaps. |
| SSZ | Main-route bosses, collapse, results and DEZ launch implemented; Knuckles arrival/finale and cold-route breadth remain substantive acceptance work. |
| DEZ | Both main acts have concrete placed-object factories and controller-driven boss/exit evidence. `$1700` remains the main implementation blocker: floor/laser/scroll/retained-plane components exist, but connected ScreenInit, final boss, chase and ending/DDZ exit are unfinished. |
| DDZ | Both boss phases and exit request implemented; seeded completion and unseeded/strict-bootstrap frontiers remain separate. Validate the real incoming DEZ transition once available and reconcile star/HUD presentation gaps. |

Combined campaign validation, main-workspace integration, push and cleanup remain
pending. Local component commits are checkpoints, not completion or delivery.

## Initial reconciled inventory (2026-09-22)

| Zone | Actual implementation location | Evidence and remaining work |
| --- | --- | --- |
| MHZ | Integrated in develop; [completion audit](../research/s3k-zones/mhz-completion-audit.md) | Sonic/Tails implementation reviewed, followed by miniboss lifetime/defeat/transition fixes. Original audit explicitly excluded further Knuckles work and stopped traces. No conformant per-act matrix yet; current route, lifecycle, visual and breadth validation still needed. Historical broad failures were not attributed to baseline and must not become a green claim. |
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
