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

## Reconciled inventory

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
