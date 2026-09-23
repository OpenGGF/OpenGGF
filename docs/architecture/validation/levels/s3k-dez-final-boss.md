# S3K Death Egg final boss arena coverage matrix

Game / canonical zone / act: S3K `S3K_DEZ_BOSS`, engine zone `$17` act index 0,
ROM `Current_zone_and_act = $1700`. **Not Sonic 2's Death Egg**, and not the
`$1701` Super Emerald sanctuary, which has its own
[matrix](s3k-hpz-sanctuary.md). Owning plan:
[S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: arena components in progress. Nothing below certifies the act.

LevelSizes (sonic3k.asm:38143): x `0`-`$6000`, y `$20`-`$20`. Level art
`levartptrs $4C,$4C,$40` (PLC `$4C`, palette `$40`, `ArtKosM_DEZ3`,
sonic3k.asm:199483). Music `Sonic3kMusic.DEZ2`. Animated tiles: `AnimateTiles_NULL`
(Offs_AniFunc entry 46) — no AniPLC script; the only animated art is the laser DMA
`sub_5A79E`. No AnPal entry (`AnPal_None`).

Incoming: the act 2 boss exit (`loc_7F310` saves `Act3_ring_count`, `Act3_timer` and
`Saved2_status_secondary`, then `StartNewLevel $1700`). The ROM level select lists
`$1700` as "DDZ act 2" (sonic3k.asm:10161); the engine now exposes the distinct final-boss level-select entry.
Outgoing: `loc_803D6` → `$C00`, `$D01` or `Game_mode 0`.

## Baseline behaviour without a resource profile (measured 2026-09-17, `035e48a58`)

A direct `$1700` load through `GameplayCaptureTool` boots the real DEZ3 layout, art and
palette (the Earth backdrop renders) but places Sonic at centre `$60,$70` — the Start
Location file, which the ROM overwrites in `loc_7FD9E` (P1 `$30,$CD`, P2 `$10,$CD`,
`object_control $81`). With no `Obj_5A7C8` arena floor the player falls out of the
level and dies at frame 98. No title card is drawn. Capture:
`~/Videos/OGGF/s3k-dez-bring-up/raw-00-baseline-before-work/1700-final-boss`.

## Five claims

| Claim | State |
| --- | --- |
| Implemented | Production entry, background stages, captured retained planes, floor/laser/art owners, final root/children, chase and escape dispatch are connected. Widescreen support and planet composition are corrected; exposed boss-body margins use ROM layout outside the preserved native view. |
| Cold-reachable | Direct final-arena load reaches hands; a controller-only solo 320px route destroys all six fingers, defeats core and ship, and loads DDZ. Complete incoming DEZ2 continuity and route breadth remain open. |
| Rewind-verified | Component graphs, retained planes and production entry replay at 320/352/400/528/800 pass. Full encounter lifecycle/route breadth remains open. |
| Native behaviour matched | ROM routine-backed components; native movie screenshots/VRAM collected. Strict trace rows below are historical and remain red/unrerun. |
| Visually matched | Native and 800px entry captures inspected; retained boss and moving floor present. Wide planet extension inspected; full phase matching remains open. |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1700` resource profile | `levartptrs $4C/$4C/$40`; player placement `loc_7FD9E`; no title card | 320/352/400/528/800 solo | `TestDezFinalScreenEntry` | connected | focused passes after `ddf517a54` | Incoming transition and roster breadth |
| ENTRY: `Act3_*` carry | `loc_7F310` bank; `loc_5A49A` restores once | Native destination boundary | `TestLevelContinuationCarry` | connected | seven focused cases pass | Full incoming fight-to-arena route |
| PRESENT: scroll and plane | `sub_5A508`, `sub_5A76C`, `loc_5A734`; `$20 + shake` | Five widths, short replay | `TestSwScrlS3kDezFinalBoss`, `TestDezFinalScreenEntry`, `TestLevelTilemapManagerRewindReset` | registered, retained planes consumed | focused passes; captures 108/111 | Full encounter visuals |
| PRESENT: laser DMA | `sub_5A79E`, `ArtUnc_DEZFBLaser` → tile `$208`, `$40` words on changed offset | Native component | `TestDezFinalArenaFloor`, `TestDezFinalMouthSequence` | connected | component passes | Rendered beam-cycle matching |
| EVENT: arena shrink stages | `$6C0` → `$2C0` → `$6C0` → 0 | Component and production hand entry | `TestDezFinalBackgroundEvents`, `TestDezFinalScreenEntry` | connected | focused passes | Complete live chase/exit |
| OBJECT: floor and falling blocks | `Obj_5A7C8`, `Obj_5A872`, `Obj_5A8E6`, `Obj_5A922`, `Obj_5A94C` | Native components; five-width entry | `TestDezFinalArenaFloor`, `TestDezFinalScreenEntry` | connected, native camera words retained | focused passes | Chase traversal and lifecycle breadth |
| BOSS: final phases and chase | `Obj_DEZ3_Boss` and children | Component graphs; solo controller frontier | `TestDezFinalBossController`, `TestDezFinalHand`, `TestDezFinalCore`, `TestDezFinalMouthSequence` | connected | component passes; six fingers and eight core hits | Complete controller fight and breadth |
| EXIT: `loc_803D6` branches | Character/emerald DDZ, ending or title branch | Component character/emerald cases | `TestDezFinalEscapeShip.fadeUsesExactCompletionAndChoosesNativeCharacterEmeraldExit` | implemented | focused passes | Real incoming DDZ/ending handoff |
| ORACLE: Sonic + Tails arena | `dez23_8`, zone 23 act 0, 5181 rows, offset 509032 | Historical trace | `TestS3kSonicTailsDez238SegmentTraceReplay` | — | 621 errors, first frame 0 `x_sub` expected `0x0000`, actual `0x0C00` at `035e48a58` | Unrerun; not current certification |
| ORACLE: Tails arena | Full-chain `dez23_8`, 5550 rows | Historical trace | `TestS3kTailsFullChainDez238SegmentTraceReplay` | — | 339 errors, first frame 0 `camera_y` expected `0x0010`, actual `0x0020` at `035e48a58` | Unrerun; not current certification |

## Execution evidence

See the [act 1 matrix](s3k-dez-act1.md#execution-evidence) for the single frontier command;
all six classes ran in one invocation with 0 skips.


### Arena component checkpoint (2026-09-23, after `7a324bdd4`)

`TestSwScrlS3kDezFinalBoss` and `TestDezFinalArenaFloor`: 13 passing tests, zero
skips. They cover the 192/32 normal scanline split, signed shake and word wrap,
retained horizontal scroll, runtime snapshot, failed floor-block allocation,
column movement and rejection, real entry-support landing at the scripted height,
falling-block reconstruction/replay, both collapse schedules, four ROM mapping
frames, and all pixels of the four laser tiles including unchanged-frame and
restored-gate behavior. `TestSonic3kHpzRuntimeStateRegistration`: two passing
checks establish isolation and stable reuse of the new `$1700` runtime state.

Commands ran through `tools/testing/maven_queue.py -Dmse=off` with explicit
`-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen` for ROM-backed tests.
The named 13 component tests, 137 loading/PLC checks (including the four mandated
S3K classes), two runtime checks and separate `-Pguards` 36-test rewind/physics
selection all passed with zero skips. The object inventory is 1275 total,
1035 isolated and 240 graph-covered, with no missing codec. These are focused
checks, not the full campaign suite. No final-arena movie or native parity claim.
ScreenInit allocations, initial layout edits, retained redraws, boss, chase and
exit remain open; the current live route still reaches an incomplete destination.


Retained-plane follow-up after `6ba7eff23`: four `TestDezFinalPlaneState` checks
pass for the native eight-pass $F0-to-zero replacement, source/destination wrap,
full refresh and rewind/replay. Together with scroll and runtime registration,
the focused run passed 10 checks without skips; the separate rewind/physics
selection passed 36 without skips. The plane is not yet consumed by the live
renderer; ScreenInit, connected stages and wide presentation remain open.


Background event follow-up after `80d67c7fb`: seven event-program cases pass for
publication, opening allocation retry, chase allocation failure/success, all
three redraw phases, laser fallthrough and four mouth layouts. Six plane checks
include native edge redraw and byte-sized direction semantics; four scroll and
two runtime checks also pass. Three structural checks pass separately. All runs
have zero skips. The event program is still unregistered pending production
layout surface, entry graph and renderer integration; no live completion claim.


Entry-object follow-up after `75d411b9b`: Robotnik/cover publications, fractional
movement, pre-init and mid-movement graph reconstruction, quake clock/flag writes
and camera threshold pass in eight focused cases. The final-act runner uses tile
$58C, separate from Act 2's destination. Source mappings pass the art registry
checks. Root allocation, connected ScreenInit and moving rendered evidence remain
open. Final focused loading/floor/entry selection: 76 passed, zero skips.


#### Hand component coverage (2026-09-23)

`TestDezFinalHand` covers partial-prefix allocations 0..3, no retry/healing,
ROM finger offsets and open-only touch/priority, three real attacks per finger,
hand-byte publication/deferred deletion, live graph recreation and dying fingers
after their parent's slot is cleared/reused. Five ROM-backed tests pass without
skips. This is unregistered component coverage; root/route/native/video and
viewport/roster/donor obligations remain open. In particular it does not certify
the complete boss graph or its downstream transition.


#### Core component coverage (2026-09-23)

`TestDezFinalCore`: five ROM-backed cases pass, zero skips, for mouth-only
vulnerability, five-color flash timing, native P1/P2 knockback credit, closing
and reopening, eight-hit defeat and single score/timer publication, distinct
root control/status retirement, and pending-hit graph reconstruction/replay.
The test uses an isolated root and direct attack callbacks. It does not prove
controller reachability, the complete encounter, viewport/donor breadth or
rendered/native parity. Those obligations remain open above.

#### Mouth/beam component coverage (2026-09-23)

`TestDezFinalMouthSequence` passes nine ROM-backed cases without skips:
button publication and forward allocation, button and beam allocation failure,
16-update opening/closing, invisible open hold, ROM laser-frame progression,
96-update release from the final zero frame, allocation-sensitive charge frame,
native P1/P2 half-open damage boundaries, tile-$001 mouth registration,
active graph restoration/replay and particles surviving beam retirement.
The focused selection with hand regression and required AIZ/loading/bootstrap/
decoding checks passed 73 cases, zero skips. Earlier core/art/sequence selection
passed 89, zero skips. These overlap and are not a combined-suite total.

The root is still a test owner, laser layout surfaces remain unconnected, and
attack input is delivered directly in component tests. Live controller entry,
full graph completion, moving render/native matching and viewport/roster/donor
obligations remain open. No new final-arena video is claimed by this checkpoint.

#### Fireball component coverage (2026-09-23)

Five ROM-backed `TestDezFinalFireball` cases pass with zero skips for the shipped
undoubled airborne lookup, two-pass cadence, ground-entry Y-refresh bypass,
three-pass floor alternation and immediate range deletion, all three flame
scripts, collision/fire-shield declarations, exhausted allocation and root-slot
retirement with restore/replay. The separate helper/field/inventory guards pass
three cases without skips; inventory is 1286 total / 1046 isolated / 240 graph /
zero missing codecs. Root-triggered emissions, actual shield contact, full
encounter route and rendered/native acceptance remain open.

#### Emerald and palette component coverage (2026-09-23)

Five ROM-backed `TestDezFinalEmerald` cases pass with zero skips: body visibility
and immediate control-bit retirement, ship release/gravity/landing and parentless
rewind, both ROM palette tables' 94-tick wrap and disabled pause, zone-owned cursor
capture/replay/reset, and all-seven-Super-Emerald gating. `TestDdzRomPalettes`
adds two passing cases for the actual DDZ emerald's shared state and both native
boss flash rows/destinations. The combined focused rerun passed seven, zero skips.
An earlier new test incorrectly assumed one fixture step meant one object update;
it now tests the falling dispatch directly before advancing the fixture.

Art registry (78), DDZ compatibility (34) and DDZ lifecycle (2) passed separately
in the initial selection without skips. No rendered DEZ emerald claim is made:
its native tile-$4D0 art still requires the pending root's module upload and the
production final encounter remains unconnected. Controller route, full graph,
native visuals and supported breadth obligations remain open.

#### Escape scenery/fade dependency coverage (2026-09-23)

`TestDezFinalEscapeScenery` covers native RNG fields, debris init/draw and strict
retirement boundary; crane flip tracking, release, draw-through callback and
retirement replay without its parent; and eight-pass white fade at native DEZ
reload 3 and DDZ reload 7, including mid-fade restoration. Three cases passed,
plus art registry 78 and DDZ lifecycle 2, with zero skips. Neither art uploads
nor the root-to-escape-to-next-level chain is connected by these tests.


#### Escape decoration and module handoff dependencies (2026-09-23)

Three `TestDezFinalShipDecoration` cases exercise ROM raw-animation cadence,
status/flip tracking, Knuckles' single module submission and end-script selection,
head retirement/restore without its retired parent, and flame V-int/motion gating
with immediate retirement. `TestDezFinalArtState` restores the zone, hardware,
physical FIFO and coordinator after crane/debris submission, then compares the
complete decoded tile pixels and drain duration. Combined with
`TestSonic3kPlcArtRegistry`, the queued Maven selection passes 82 cases, zero skips.
The first attempt exposed a private-field accessor typo at compile time; the next
found a test assertion expecting manager sweeping after a direct object update.
Both were corrected; no gameplay behavior was adjusted to accommodate the test.

These are dependency checks. Neither the escape ship nor its event owner is live,
and no new final-arena video, controller completion or full-graph claim follows.

Focused rewind guards: helper-state and field-disposition checks passed; the
first inventory check identified its stale Java count (the text header was already
updated). After updating that count, the isolated inventory rerun passed:
1,289 classes, 1,049 isolated passes, 240 graph-covered, zero unaccounted classes.
All three distinct guard cases pass without skips; this is not a full guard sweep.


#### Escape ship sequence (2026-09-23)

Ten `TestDezFinalEscapeShip` cases cover copy/live camera use, ordered child
creation, fractional chase overshoot and rewind, both credited players and the
shipped flash-row bug, exact defeat waits, all character/emerald exit requests,
independent partial prefixes, forced-slot-64 fade replacement and replay,
non-completion on external fade deletion, restored ship/child references and the
natural chase-to-defeat-to-fade sequence. With the four mandatory S3K load/bootstrap
classes, 69 cases pass without skips. Scenery (3) and DDZ production lifecycle (2)
also pass. Three focused rewind guards pass, including the updated object count.

Initial pressure testing found the production null-only allocation check wrong;
failed spawn helpers return destroyed instances. Fixed it before the passing run.
The test then replaced synthetic allocator reservations with real occupants for
rewind and limited topology comparison to the encounter (session restore adds a
fixed-slot Insta-Shield). No live root or final screen path invokes the ship yet;
this sequence does not certify controller completion or final-arena presentation.

An eleventh ship case then passed alone (zero skips): a real ObjectManager
sweep runs the forward head, crane and emerald on their allocation pass, with
the head still on its initial raw-animation frame. Total distinct ship cases: 11.


#### Main controller and child handoff (2026-09-23)

Four `TestDezFinalBossController` cases pass, zero skips: native script entry,
cover gate and exact 192-move rise; independent core/emerald/two-hand/six-finger
graph and restoration; post-hand plane change, mouth-gated fire and forward
replay; fatal stack-unwind equivalent, pending modules and deferred retirement.
The tests drive phase signals directly where noted; they do not establish a
complete production route. Existing hand/core cases pass 10 and focused rewind
guards pass 3 without skips. Production screen/plane wiring remains absent.

A fifth controller case passed separately, zero skips: 0..4 available slots
preserve independent core/emerald allocation and the two-hand successful prefix,
without healing missing hands after capacity becomes available. Total distinct
controller cases: five. All local Maven work used the queue and the absolute
root locked-on ROM path; waits were for a confirmed live shared Maven run.

### Production entry connection (after `72d4997ca`)

`DezFinalScreenEvents.initializeObjectsAndCamera` runs before the first object
pass through the production level event manager. It preserves the native
AllocateObject/CreateNewSprite4 prefix (moving support, entry support, root),
sets boss position only after root allocation succeeds, and always installs the
scroll lock, camera/copy X `$80`, window `$6C0` and initial laser upload. A
captured marker prevents recreation on subsequent frames or rewind restore.

`TestDezFinalScreenEntry` verifies a real first gameplay frame, forced player
entry and three-frame replay after graph restore, plus available capacities
0..3 and no retry after capacity returns. These two tests and the existing
root/scroll component selection passed 11 total, zero skips, with the absolute
locked-on ROM property. This establishes entry initialization only: layout row
pointers, retained planes, per-frame screen/background updates and complete
route/presentation remain open.

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

### Connected background stages and continuation counters (after `ddf517a54`)

The production ScreenEvents pass now publishes the camera-copy and boss-plane
words, advances `DezFinalBackgroundEvents`, applies immediate chunk mutations
without invalidating retained cells, and services the zone's art jobs after the
root retires. Shake setup uses the level frame clock and leaves its result for
the next screen pass. Initial Plane A refresh resolves native row-0 aliases at
FG rows 4/31 before writing the retained name table. Rendering still does not
consume that table; Plane B's moving floor and hole redraws remain unconnected.

`TestDezFinalScreenEntry.productionRunReachesHandsAndRetainedPlaneStagesThenReplaysAfterRestore`
holds right for 410 production frames, reaches window `$2C0`, background stage
`$10`, two hands and six fingers, then checks state bytes and player position
across a 12-frame restored replay. No forced event-state writes are used.
The shared continuation helper existed, but only LRZ called its destination
restore; DEZ now calls it at foreground stage 0. The production destination
test checks consumption, 37 rings, the restored running timer and no second
restore. The timer expectation includes the subsequent ordinary-frame tick.

A 420-frame native-width capture (right held, no deaths) is stored at
`$HOME/Videos/OGGF/s3k-dez-bring-up/107-final-hands-wip-320/`. Still 380 was
inspected: the missing boss-plane rendering is visible, so this remains an
implementation diagnostic, not visual-match evidence.


### Retained rendering and widescreen entry (2026-09-23)

After `ddf517a54`, the final-object/mandatory S3K selection passed 152 cases,
entry/scroll/camera selection 31, background/counter/cache selection 28, and the
revised planet projection selection 26; all had zero skips. Commands used queued
Maven with the absolute S3K ROM. These are focused selections, not a full-suite pass.
Capture 111 at 800px completes 1200 frames with no hurt/death. Capture 108 at 320px
and the wide route differ only by camera X in recorded state. The original sky
is extended from ROM pixels above the native `$E0` split; floor scrolling remains
independent. Capture 112 subsequently verifies the body margins; its native centre matches capture 108 at four inspected frames. Full native references,
rejected capture 109/110 details and commands are in the owning plan.


The final body-margin/cache selection passed 22 tests, zero skips, including a
one-pixel native-view boundary regression. Capture 112 at 800px completes 1200
frames without hurt/death; full decode passes. Frames 190/600/1000/1199 match
capture 108's native 320px centre pixel-for-pixel, while the added margins reveal
the authored boss instead of blank retained cells. These remain entry/hand-phase
captures, not a complete fight recording. A subsequent controller-only probe
lands seven core hits before falling; no engine tuning was made for that input.


The next controller attempt defeats the core: hit eight is observed at probe
frame 11806, and the ship chase continues to the 15000-frame limit without death.
The preceding seven-hit fall remains a rejected input attempt, not a physics fix.
Ship defeat and live outgoing transition are the remaining route frontier.


Final escape follow-up after `e2120af4d`: 40 focused cases pass, zero skips,
including real explosion-child execution and owner-link rewind plus the finite
$80-count branch. A direct native-width controller-only route (solo Sonic,
200 initial rings, seven Super Emeralds) completes all phases and loads DDZ at
frame 14458. Full incoming DEZ2 continuity, widescreen/team/donor breadth and
whole-route rewind remain open; this does not certify the act.


Support carry correction after `961a04516`: native `loc_5A860`/`loc_5A8C4`
pass post-move X as the carry reference. A real grounded-rider regression
reproduced an erroneous $20 displacement (expected304, actual336), then passed
with horizontal carry disabled through the existing solid contract. The queued
arena-floor, screen-entry, boss-controller and escape-ship selection passes
38 cases, no skips. Capture113 and its route predate this correction; corrected
route replay is pending. The configured proportional widescreen deadzone remains
intentional and is not overridden to manufacture identical controller outcomes.
