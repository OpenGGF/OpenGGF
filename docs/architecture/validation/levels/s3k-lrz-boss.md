# S3K Lava Reef boss act coverage matrix

Game / canonical zone / act: S3K `S3K_LRZ_BOSS`, engine zone `$16` act index 0,
ROM `Current_zone_and_act = $1600`, SKL object set. The act shows the Lava Reef title card
on a level-select load and none on the Act 2 handover (`Act3_flag`, `loc_62B6`).
Character route: Sonic + Tails and Tails alone only — Knuckles never enters `$1600`.
Flash sequence, autoscroll, end boss, capsule and `Obj_StartNewLevel $2D` at `($FE8,$5E0)` to
Hidden Palace `$1601`. Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md).
Status: carry/title suppression, screen stages, flash, autoscroll, platforms/lava,
end boss and HPZ exit implemented. A12820-frame fresh boss-act route completes
with declared initial fire shield/37rings. Native presentation/timing and the full
route product remain incomplete; see the dated evidence below. The ordinary
native320 Sonic+Tails cold Act1 chain now completes both acts and this boss act
in53047 inputs, zero deaths, reaching playable HPZ. The route arrives without
a shield and collects the placed fire shield before the fight; see
[cold completion](#ordinary-cold-lrz-completion-2026-09-25).

Incoming: LRZ2 `loc_63C14` with the Act 3 carry (`Act3_flag`, `Act3_ring_count`, `Act3_timer`,
`Saved2_status_secondary`), level select `$1600`, star-post respawn (`LRZ3_ScreenInit` P1 X >=
`$480`). Outgoing: `$1601`, which closes the Hidden Palace entry dependency recorded in the
[HPZ matrix](s3k-hpz-act.md).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/352/400/528/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 35 placed objects, of which **0 build a
`PlaceholderObjectInstance`** (8 before the September 22 platform work); 52 live rings (53 records minus the
leading `(0,0)` sentinel). The end boss, capsule, `StartNewLevel`, dome platform and Death Egg sprite are
event-spawned and are not in the placement list.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ3_Sprites` `$1FCBA2` (35), `LRZ3_Rings` `$1FCD82` (53 records, 52 live) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| ENTRY: `$1600` resources, title card, Act 3 carry | `Sonic3kLevelResourceProfile`; `Act3_flag` skips the title card and the `loc_62CC` Kos/Nem drain loop; `LRZ3_ScreenEvent` stage 0 (`loc_59B1C`) restores rings and timer | native | `TestS3kLevelContinuationHeadless`, `TestLevelContinuationCarry`, `TestS3kLrzBoulderCutsceneHeadless` | implemented | focused pass, 2026-09-22 | DEZ adoption, native entry-loop timing and remaining screen stages still open |
| ENTRY: star-post respawn branch | `LRZ3_ScreenInit` P1 X >= `$480`: camera `($920,$2F0)`, `Special_events_routine = $14`, `Events_bg+$00 = $10`, `Events_bg+$02 = $2D`, `Events_routine_fg = $C`, `Pal_LRZBossFire` -> `Target_palette_line_2`; preserves saved player position (the `$9C0/$36C` writes address stack RAM) | native + wide/donor | `TestS3kLrzBossCameraHeadless` | implemented | 17 checkpoint cases pass, 2026-09-22 | Full respawn route still needs the platform/boss graph |
| PRESENT: scroll handler registration | `$1600` must not use `SwScrlHpz`; `$1601` must keep it | native | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented (`$1600` selects `SwScrlLrz3`; `$1601` retains HPZ) | focused pass, 2026-09-22 | stage reachability remains open |
| PRESENT: `SwScrlLrz3`, shimmer and per-column VScroll | `LRZ3_BackgroundEvent` five stages `0,4,8,$C,$10`; `word_5A106` = `$310` then 18 x `$10`; `sub_59D82/59DA2/59DBC`, `sub_59DDE` | native + wide | `SwScrlLrz3Test` | scroll/shimmer/column rendering implemented | 581 native frames matched, 2026-09-22 | stage owner and boss allocation implemented; complete native timing/pixel matching remains open |
| PRESENT: animated tiles and palette | `AnimateTiles_LRZ3` channel 0 only at tile `$170` (`loc_2833C` returns for `Current_zone $16`); `$1600` AniPLC entry is `AniPLC_NULL`; `AnPal_LRZ3` gate `Palette_cycle_counters+$00` in {0, `$80`, 1} | native | `TestS3kLrzPatternAnimation`, `TestS3kLrzBossPaletteCycling` | implemented | ROM pixel/color oracles pass, 2026-09-22 | flash graph publishes palette modes; complete native frame alignment remains open |
| EVENT: Death Egg flash sequence | `LRZ3_BackgroundEvent` stages, `Obj_CollapsingBridge` spawn at `($60,$4D0)` | five widths | `TestLrzAutoscrollGraphHeadless` | implemented | flash, release, missile graph and full-world replay passed in the September22 campaign | Native art-admission timing and matched pixels remain open |
| EVENT: autoscroll | `Special_events_routine $14` (`loc_59E46`), seven stages; thresholds X `$410`, Y <= `$330`, X `$650`, Y <= `$2F0`, X `$910`, Y >= `$320`, X `$BBF` with P1 X >= `$C50`; `sub_59F82` push at `Camera_X + $10`, kill on `Status_Push`, right cap `Camera_X + $120` | native + wide/donor | `TestLrzBossAutoscroll`, `TestS3kLrzBossCameraHeadless` | implemented | focused pass, 2026-09-22; 1635 native moving dispatches match arithmetic | Preserve 32px right margin at wide widths; flash graph implemented; ordinary native320 Sonic+Tails cold chain now completes; other cold products remain open |
| OBJECT: `$9E` autoscroll controller, `$AD` platforms (7), `$6E` lava blocks (6), `$8B` sprite masks (2) | `Obj_LRZ3Autoscroll`, `Obj_LRZ3Platform`, `Obj_InvisibleLavaBlock`, `Obj_SpriteMask` | native | `TestS3kLrzPlacementCensus`, `TestSonic3kInvisibleHurtBlockHObjectInstance` | `$6E` and `$AD` implemented; `$8B` reads ROM mapping frame and enables SAT masking | platform graph/rewind and 900-frame checkpoint capture, 2026-09-22 | `$9E` and boss-driven stream implemented; allocation failure, reused-slot reads and graph replay covered by `TestLrzAutoscrollGraphHeadless` and `TestLrzEndBossEncounterHeadless`; native320 Sonic+Tails cold route completes; broader products remain open |
| OBJECT: `$0F` collapsing bridges (8) use `Map_HPZCollapsingBridge` | `Obj_CollapsingBridge` picks the HPZ mappings for `Current_zone $16` by ROM design | native | — | implemented (shared switch already matches) | classification pass | Art under it unverified: open question, slice 9 |
| BOSS: end boss and lava surface | `Obj_LRZEndBoss` `collision_property $E` (14 hits), `off_79812` six routines; `Obj_59FC4` `SolidObjectTopSloped2`, push `Events_bg+$14`; shared `HScroll_table+$110` table | native | `TestLrzBossLavaSurface`, `TestS3kLrzBossCameraHeadless` | lava surface and14-hit boss/mine graph implemented | `TestLrzEndBossEncounterHeadless`:25 positioned width/donor/roster completions with peak/defeat/capsule/results replay passed in September22 campaign | Native320 Sonic+Tails cold carry now completes; other route products and native capsule slot/pose/timing remain open |
| LOAD: defeat -> capsule -> `$1601` handoff | `loc_79998`/`loc_79A30`, `mus_LRZ2` fade, `$EC0` gradual, `StartNewLevel $2D` | five widths and five supported donor/roster cases | `TestLrzEndBossEncounterHeadless#realMineFightCapsuleAndResultsPublishTheHiddenPalaceTransition` | implemented |25 positioned completions and declared-shield fresh route reach HPZ | Native320 Sonic+Tails cold carry now completes; lifecycle/route breadth and native capsule slot/pose remain open |
| ORACLE: strict segment replay | `TestS3kSonicTailsHpz22SegmentTraceReplay` (LRZ3 autoscroll), `TestS3kSonicTailsHpz222SegmentTraceReplay` (boss -> `$1601`), Tails equivalents | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`. Slice 0 evidence is shared with the
[Act 1 matrix](s3k-lrz-act1.md). Baseline media: `~/Videos/OGGF/lrz-bring-up/raw-00-lrz3-before/`.

## September 22 camera/event slice

Worktree `.worktrees/ai-sk-zone-completion`, parent `59baeab3c`. Queued
`TestLrzBossAutoscroll,TestS3kLevelContinuationHeadless,TestS3kLrzBoulderCutsceneHeadless`
passed 39 tests at 16:30 BST, no failures/errors/skips. Expanded real-world
`TestS3kLrzBossCameraHeadless` passed 18 at 16:34 BST, no failures/errors/skips:
17 checkpoint viewport/donor/team cases plus foreground signals, terrain edits,
and full-world restore/forward comparison. Initial fixture failures came from
post-load ground snap (Tails) and capturing an unstepped sprite graph; the tests
now use the fresh-entry lifecycle and advance the complete world before capture.
No runtime adjustment was made for those fixture failures.

The native original movie observer recorded 20201 consecutive frames. It includes
the initial autoscroll, bonus-stage detour/checkpoint re-entry and the full boss
completion. All 1635 moving special-event dispatches agree with the ported fixed
point arithmetic; unchanged emulator frames were excluded from that motion check.
This does not certify frame-clock admission, the unimplemented flash/boss graphs
or visual parity. See the campaign audit for the native encounter observations.

## September 22 end-boss candidate (not certified)

On parent `3fbb61e7c`, the background arena owner and 14-hit boss/mine graph are
implemented in the working tree. Short startup lifetime and removed-parent/child
restore/forward checks pass (`TestS3kLrzBossPlatformsHeadless`, 19 tests, 18:10 BST).
The original checkpoint movie reaches defeat but hurts the player at capture
frame 2777 and dies at 5903 before results. Clip 43 shows only the first cycle.
A native stalled frame precedes a consistent one-frame boss offset; diagnostic
alignment matches 4584 root state rows but is not a strict replay pass.

Startup/launch allocation prefixes, negative player/fire-shield attacks, mine
publication and explosion failure/RNG order now pass. A real authored checkpoint
fight reaches capsule/results and playable `$1601`; full-world restoration and
forward replay pass at peak graph (13), defeat, capsule opening and results.
The 96-test focused selection passed at 18:41 BST without failures/errors/skips.
Clip 44 records the completion with explicit initial fire shield and 37 rings,
native Sonic + Tails, width 320; no hurt/death in 7300 frames.
At 18:46 BST, 25 complete checkpoint fights pass across five widths and native
Sonic solo/team, native Tails solo, S1 Sonic and S2 Sonic + Tails; every case
includes the four restore/forward checks. Stream allocation failure/retry and
reused-slot reads also pass. Palette disable/pause/resume is covered at 18:49 BST.
Capsule child-slot fidelity, native palette row timing, hardware admission and
cold entry remain open. See the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md)
for failures, rejected approaches and provenance. The obligation rows above now reflect this implemented state; their open
acceptance limits remain separate from the historical candidate narrative.


The subsequent cold-entry candidate implements `$9E` and reduces the census to
zero placeholders. Five widths pass the flash/release and whole-world
remove/restore/forward checks; failed flash allocation and partial missile
allocation have independent short tests. Clip 45 covers the first 1500 frames.
The native comparison matches 203 frames before an art-loading admission stall;
this is not full trace parity. The extended route found a real bonus-return
load-order defect and a mistaken stack-write/player-position interpretation in
the earlier checkpoint setup. Those fixes and renewed route evidence are in
progress; see the campaign audit. Earlier clips retain their recorded setup.


The corrected candidate now has a continuous fresh boss-act route through the
bonus detour, all boss phases, capsule/results and playable HPZ: 12820 recorded
frames, no hurt/death, HPZ load at 12700. Clip 46 replaces the earlier positioned
setup as current route evidence. Initial fire shield and 37 rings are declared;
input alignment at bonus return is authored, not a strict native replay claim.
125 camera/encounter/rewind/required S3K checks, 106 cold graph/return/art/burst
checks and 23 structural checks pass (19:17–19:19 BST). The earlier fixed shield
rebind order was replaced with captured dynamic-list ordering. Remaining native
capsule slot/pose, timing-admission and full route-product obligations remain.


## September24 status reconciliation

On7691f0eb3, reconciled the obligation rows with the production flash/controller,
boss/mine/platform-stream and capsule/results owners and their existing tests.
Earlier rows incorrectly retained “not implemented” after the September22
encounter gate and fresh route evidence. This is a documentation correction,
not a new test pass or native certification. The ordinary cold carry
continuation now clears the initial lava gap, staircase and upper corridor,
then lands on the first lava platform at2700/901 with18 rings. Controller
authoring continues without seeded shield/rings.


### Cold autoscroll traversal (2026-09-24)

Fresh replay on7691f0eb3 uses45523 inputs from cold Act1, zero deaths, and
ends on the first lava platform at2700/901 with18 rings and live Tails. Input:
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/cold-boss-checkpoint-wait/variant-1.bk2`.
Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-cold-boss-autoscroll-320/capture.mp4`
films43761–45522 (1762frames). All state rows checked for death, stills44806
and45522 inspected, and the full video decodes. This is cold reachability and
engine presentation evidence, not a new rewind or native-parity pass.

Running continuously against the autoscroll's native right cap gives shorter
world-space jumps than Sonic's nominal X velocity suggests. The successful
route jumps again from the collapsing bridge before its edge, climbs the
solid steps, and delays the final checkpoint-ledge jump to land on the lava
platform. Continuous early hops miss later platforms; the remaining platform
crossing and cold boss fight are still open. No camera, movement or geometry
logic was changed to accommodate these controller attempts.


### Ordinary cold LRZ completion (2026-09-25)

Onfcd861c25 plus the preserved input/test slice, native320 Sonic+Tails completes
Act1, Act2 and the boss act from ordinary cold entry in53047 inputs, zero
deaths. There are no initial position, shield, rings or emerald writes. The
placed fire-shield monitor at2792/1153 is broken during the route, after
arriving in this act without a shield. The encounter retains28 rings without
hurt; the exit loads HPZ at52926 and ends at393/2796, three rings, live Tails
and player control. Input: `lrz-boss-sonic-tails-cold-hpz-320.script`/`.bk2` in
`src/test/resources/routes/s3k/`; both pads through43761 match the preserved
Act2 arrival fixture exactly and the author/loader round trip passes.

`TestLrzBossColdRouteCapture` adds62 full-registry restore/45-input replay
spots over flash, missile release, autoscroll changes, bridges/stairs/platforms,
monitor pickup, arena descent, mine cycles, defeat, capsule, results and HPZ.
No replay crosses a load; destination spots are independent after the handoff.
The test observes real boss/capsule/results publication and asserts earned
fire shield, no encounter hurt, destination state and roster.

Fresh video `$VIDEO_ROOT/lrz-bring-up/campaign-20260925-cold-boss-hpz-320/capture.mp4`
films45523–53046 (7524frames). All53047 CSV rows have zero deaths and there
is no hurt from46515 onward. Stills47340,51840 and53046 inspected; full video
decode passes. This is engine presentation evidence, not matched native pixels.

Rejected controller attempts are useful distinctions: running off each moving
platform misses the next landing; riding the final right platform all the way
down leaves the camera atX3008 and does not spawn the boss. `LRZ3_BackgroundEvent`
requires cameraX$A00 and maximumY. The leftward route must reach that boundary
before the entry platform passes its claim height. A controller targeting2720
with a four-pixel tolerance settled at2721 and delayed entry until knockback;
target2688 reaches the native boundary while the platform is still usable.
The unshielded corrected entry reaches the active boss but the attempted mine
avoidance dies. The successful route obtains its fire shield from the real
monitor; it does not inject immunity. Follow-up validation required the runtime
fixes described below.

The change-based plan fromfcd861c25 selects2913 ordinary classes plus guards
for unclassified route resources. This began as a controller-fixture/test slice,
but the shared contact-state repair below also requires normal combined campaign
validation. Focused results here do not replace that outstanding broad run. Native timing/pixel/capsule
slot matching and remaining width/donor/roster/lifecycle obligations stay open.

### Cold completion follow-up defects (2026-09-25, work in progress)

The first full boss-route rewind run exposed an unused missile parent reference
that outlived its registered object. `loc_791FE`, `loc_7931E` and
`Go_Delete_Sprite` no longer read `parent3` after their respective transition.
The Java implementation now releases those links at that boundary; it does not
change the ROM callback lifetime or motion. `TestLrzAutoscrollGraphHeadless`
passes 14 tests, zero skips. The next run failed at input 47640 on
`latchedSolidObjectReleased`: restoring treated ID zero as no contact, even
though LRZ's event-created solid platforms legitimately use that ID. A two-case
short test reproduced the failure only for ID zero. Snapshots now preserve an
explicit binding bit independently of ID and the sticky ROM interact slot;
a separate cleared-contact case prevents accidentally relinking an old slot.

`TestSpriteManagerRewindCapture,TestAbstractPlayableSpriteRewindCapture,TestLrzBossColdRouteCapture`
then passed 23 tests, zero failures/errors/skips, including all 62 full-registry
restore/replay spots through HPZ. Command: queued Maven `-Dmse=off`, the above
`-Dtest` selection and `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, on `fcd861c25`
plus these working changes. The unpublished 0.7 API pin includes the new binding
field and the earlier campaign's captured tile/sprite priority fields; its
version remains 0.7.0.

The user identified a missing sloping lava floor in the fight video. Native
`native-boss-20260922/run2/f437200.png` confirms the surface. Investigation found
that LRZ3 was not selecting a moving Plane B source window: `sub_59DA2` reads
layout X = camera X - $700, with the pool beyond the initial 512px strip.
There are three interacting omissions: the source window did not follow the
boss camera; `Refresh_PlaneFull` retained the initial 64x32 image despite the
later `DrawBGAsYouMove`/`DrawTilesVDeform2`; and the pool's high-priority Plane B
pixels had no replay above Plane A's opaque low-priority lava wall. The correction
selects the ROM source window, releases the initial image back to the layout
renderer, and registers the existing shared high-background replay plus sprite
mask for `$1600` only. It leaves the ROM tile priority bits unchanged and passes
the existing per-column VScroll into both draws. `SwScrlLrz3Test,TestLrzBossBackgroundStageMachine,TestSonic3kZoneFeatureProvider`
passes 13 tests with zero failures/errors/skips on the combined correction
(`fcd861c25` plus working changes), with the absolute S3K ROM property.
A fresh cold 48000-input replay films inputs 47200–47999 at native320:
`$VIDEO_ROOT/lrz-bring-up/campaign-20260925-lava-pool-fix-320/capture.mp4`.
Inspected frames 47340 and 47900 show the pool and changing slope; the 800-frame
MP4 fully decodes with ffmpeg. These frames have no death/hurt, retain 28 rings,
and keep camera (2560,1376). Native `f437200.png` establishes the plane ordering,
not phase-synchronized pixel parity. Widescreen visual coverage remains open. The earlier cold-completion video is route evidence, not visual
acceptance for the pool.

Reusing the native320 controller movie at width800 died in Act1 at input4827
(4888 inputs including death grace), before the boss footage window. No PNG or
video was produced. This is an unsuccessful width-specific route attempt, not
evidence that the lava correction regressed widescreen. Its state CSV remains
under `$VIDEO_ROOT/lrz-bring-up/campaign-20260925-lava-pool-cold-800/`; a suitable
wide route or bounded arena presentation check is still needed.


The related stability/API selection passed 169 tests across 21 classes with no
failures, errors or skips (`fcd861c25` plus working changes). It includes all four
mandatory S3K loading/bootstrap/AIZ checks; LRZ autoscroll, scroll, encounter and
boss rewind; the shared high-background command pool; API signature, SDK and
Javadoc checks; and the maintained platformer, standalone, character, phase2,
ROM-art and Flappy sample checks. All ROM paths were absolute existing root ROMs.
This is focused validation. The combined plan against actual integration base
`e6c6ac79a8b411f32998ae13c8e5c94099c1818c` still selects all 2913 ordinary classes
plus guards and has not been run for the final campaign candidate.

A second width800 attempt used the historical fresh boss-act movie with its
declared initial fire shield and 37 rings. It died at input2685 before the
recording window; this also supplies no wide pool footage. No route or timing
values were adjusted to disguise either unsuccessful input reuse.

The targeted fresh-JVM `-Pguards` selection
`TestRewindFieldAudit,TestRewindFieldDispositionGuard,TestRewindTransientGuard`
passed 8 tests, no failures/errors/skips, after queue admission. This is not the
full guard suite. The corrected campaign base SHA above supersedes an invalid
transcribed SHA in an earlier attempted plan command; that failed before testing.


## September 25 widescreen platform activation correction

On `6b1667adc`, matched positioned checkpoint sessions (Sonic+Tails, fire shield,
37 rings, position `$9C0/$368`, historical movie starting at input2428) have
identical player/camera state through input174 at widths320 and800. At input175,
native320 lands at Y935 while width800 falls through to Y943. Width800 dies at257.
These are bounded presentation sessions, not cold-route certification.

`sub_7A040` admits the invisible platform generators only within the unsigned
half-open camera rectangle `$140 x $E0`. The implementation incorrectly widened
this gameplay trigger to the display width, advancing the platforms' lifetime
before Sonic reached them. The correction preserves the native320 trigger while
leaving rendering and culling aware of the real viewport. A focused boundary
regression fails on all four wide widths before the correction (4 failures,
0 errors/skips); it covers the excluded right edge and first admitted pixel for
all three generator subtypes at320/352/400/528/800. Existing platform graph and
camera full-world rewind checks accompany it. Queued
`-Dmse=off -Dtest=TestLrzBossPlatforms,TestS3kLrzBossPlatformsHeadless,TestS3kLrzBossCameraHeadless
-Ds3k.rom.path=$REPO_ROOT/s3k.gen test` passes47 tests,
0 failures/errors/skips on `6b1667adc` plus this correction.

The corrected width800 checkpoint replay matches all2634 native320 player rows
(X/Y, velocity, ground speed, air, hurt, death and rings). The camera first differs
at387 during the wider free-follow region; player state remains identical. Both
sessions eventually die at2633 from this inherited controller/setup combination;
this is not a completed route. The ten-second excerpt records1500–2099, before
that death, and shows the restored floor pool:
`$VIDEO_ROOT/lrz-bring-up/campaign-20260925-platform-window-fixed-800/capture.mp4`.
Its full ffmpeg decode passes. Frame1900 was inspected. The arena remains
left-aligned with a large right mask at800; presentation centering remains open.
