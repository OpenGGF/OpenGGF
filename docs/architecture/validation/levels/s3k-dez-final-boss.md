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
| Implemented | Independent runtime owner; floor/collapse, laser-upload and scroll components. Screen events and final boss are not connected. The provider already separates the sanctuary; `$1700` still selects the default scroll handler until its new handler is connected |
| Cold-reachable | Not started |
| Rewind-verified | Component state bytes, falling-block reconstruction/replay and laser-upload gate; full arena/boss graph remains open |
| Native behaviour matched | Not started; replay frontiers measured at `035e48a58` below |
| Visually matched | Not started; `raw-00-baseline-before-work/1700-final-boss` is the "before" capture |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1700` resource profile | `levartptrs $4C/$4C/$40`; player placement `loc_7FD9E`; no title card (`Act3_flag`, `loc_62B6`) | — | `TestS3kDezFinalArenaHeadless` | missing | unrun | Slice 9 |
| ENTRY: `Act3_*` carry | `loc_7F310` saves rings, timer and `Saved2_status_secondary`; `DEZ3_ScreenEvent` stage 0 `loc_5A49A` restores them | — | slice 9 test | missing | unrun | Slices 8 and 9; shared owner with LRZ |
| PRESENT: scroll and plane | `sub_5A508`, `sub_5A76C`, `loc_5A734`, FG wrap `Camera_X_copy & $1FF`, `Camera_Y_copy = $20 + shake` | 320 + one wide | `TestS3kDezFinalArenaHeadless` | component present, not registered | unrun | Slice 9; the provider's act-keying for `$16`/`$17` is the LRZ campaign's shared edit |
| PRESENT: laser DMA | `sub_5A79E`, `ArtUnc_DEZFBLaser` → tile `$208`, `$40` words when `Events_bg+$10 != +$12` | 320 | slice 9 test | missing | unrun | Slice 9 |
| EVENT: arena shrink stages | `Events_bg+$00`: `$6C0` → `$2C0` → `$6C0` → 0 | — | slice 9 test | missing | unrun | Slice 9 |
| OBJECT: arena floor and falling blocks | `Obj_5A7C8`, `Obj_5A872`, `Obj_5A8E6`, `Obj_5A922`, `Obj_5A94C` | 320 component fixtures | `TestDezFinalArenaFloor` | components present; event spawn pending | focused checks pass | Connected arena, wide support and lifecycle breadth open |
| BOSS: `Obj_DEZ3_Boss` phases and chase | per-phase | — | `TestS3kDezFinalBossHeadless` | missing | unrun | Slice 10 |
| EXIT: `loc_803D6` branches | `SaveGame`; `Player_mode < 2` and 7 emeralds → `$C00`; else `Player_mode != 3` → `$D01`; else `Game_mode 0` | — | `TestS3kDezExitBranches` | missing | unrun | Slice 10; closes the DDZ seeded-entry caveat |
| ORACLE: Sonic + Tails arena | `runs/s3k-sonic-tails-complete-emeralds/dez23_8` (`zone_id 23`, act index 0, 5,181 rows, offset 509032) — **this is `$1700`, not "Hidden Palace proper"** | — | `TestS3kSonicTailsDez238SegmentTraceReplay` (expected red) | — | blocked: 621 errors, first error frame 0 `x_sub` expected `0x0000` actual `0x0C00` (`035e48a58`) | Slices 9-10 |
| ORACLE: Tails arena | `runs/s3k-tails-full-chain-all-emeralds/dez23_8` (5,550 rows) | — | `TestS3kTailsFullChainDez238SegmentTraceReplay` (expected red) | — | blocked: 339 errors, first error frame 0 `camera_y` expected `0x0010` actual `0x0020` (`035e48a58`) | Slices 9-10 |

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
