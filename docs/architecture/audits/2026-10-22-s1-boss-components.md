# S1 standard boss component fidelity — 2026-10-22

Scope: S1-1/S1-3/S1-4/S1-6 from the native gap audit. Implementation base
`94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`, isolated branch
`bugfix/ai-gap-s1-boss-fidelity-20261022`. Source references were read from the
optional S1 disassembly at `f6ece657c1cf253404312137dfcb8ec15fa42318`; the
worktree's pre-existing modified submodule was preserved. Supplied S1 REV01
ROM: CRC32 `AFE05EEE`, SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`.

## Native ownership and chosen implementation

Each standard boss's `*_Main` component table writes its ship slot first,
then uses `FindNextFreeObj` for face, flame, and optional pipe/spike. Creation
happens during the first object pass, not the Java constructor. A failed
allocation retains only the completed prefix; initialization is never retried.
Existing `spawnChild` already supplies this ascending allocation, same-pass
execution, and normal real-slot lifetime. No ObjectManager contract changed.

| Boss | ROM table | Native order | Priorities |
| --- | --- | --- | --- |
| GHZ | `0x17EC2` | ship, face, flame | 3 throughout |
| MZ | `0x189F8` | ship, face, flame, tube | 4, 4, 4, 3 |
| SYZ | `0x198B6` | ship, face, flame, spike | 5 throughout |
| LZ | `0x185F8` | ship, face, flame | 4 throughout |
| SLZ | `0x19080` | ship, face, flame, pipe | 4, 4, 4, 3 |

The previous folded-overlay approach was rejected: reservations alone do not
supply independent animation/update/delete positions, and dynamic spawn keys
complicate releasing reservations after the ship moves. Real components use
existing allocation and identity-based rewind. Their local animation cursor,
timer, mapping frame, visibility and exact ship reference are captured.

`Ani_Eggman` is read through injected `ObjectServices.romReader()`. Its actual
table is `0x18468`; audit address `0x18484` is normal-face-1's script. Native
animation changes load immediately; delay N holds each frame for N+1 object
executions. `FE,2` in escape flame loops the final two entries after the prefix.
No compiled frame table supplies fallback asset bytes.

The ship and components retain their own preceding BuildSprites visibility
at the existing post-camera hook. Horizontal half-width is 32 except SYZ
spike's 8; assumed height is 32. The supported viewport supplies the extended
view width. This keeps rendering/camera phase separate from object execution
and prevents early ship deletion orphaning visible components at wide widths.
SYZ face deletes on parent identity loss rather than the offscreen flame/spike
branch. Other component escape deletions use their own previous render flag.

LZ `BossLabyrinth_FaceMain` uses the face-local cleared `$3D` under
`FixBugs=0` (`0x18932`: `4A28 003D`); the ship's early-defeat flag therefore
never directly chooses defeated animation. The fixed branch changes the
owner from a0 to a1. `BossLabyrinth_FlameMain` non-escape `BNE` at `0x18984`
(`661A`) skips normal movement flames entirely. Both shipped branches are
preserved. GHZ face defeat begins at phase 10, panic only at phase 12; MZ's
return-to-centre laugh precedes the hit check, whereas SYZ hold panic still
falls through the hit/player-hurt checks.

Standard S1 defeat bursts now use the native `BossDefeated` helper's semantics:
allocation succeeds before RNG advances, X is `(lowByte>>2)-32`, Y is
`highByte>>3` without subtraction, and Obj3F owns five eight-pass frames. The
inherited shared helper previously selected the seven-frame S2 explosion and
its centred Y distribution. LZ reads the current dispatched V-int count for
this eight-pass gate, not the previous completed pass's stamp.

The approved narrow art registry fix follows `Expl_Main`'s actual mapping
pointer at `0x948C` to `Map_ExplodeBomb` at `0x958A`. It reuses the ROM-loaded
Nem_Explode bank with five signed mapping offsets, including backward shared
frames. Fiery frames 1/2 use tiles `0x40/0x50`; item explosions use `6/0x16`.
An actual EGL pixel readback compares both fiery frames with independently
indexed native column-major tiles and discriminates them from the old alias.
`HeadlessGameBoot` supplies known no-device audio; no desktop display is used.

## GHZ six-slot assembly

`GBall_Main`/`GBall_PosData` at `0x182A8`/`0x1835E` define six total objects:
anchor, four intermediate links, final ball; distances `00 10 20 30 40 60`.
The ship itself allocates the assembly root with `FindNextFreeObj`
(actual call at `0x1809E`), after its face/flame slots. Native scratch-position
words initialize the root before the ship publishes its new sine position.
Failed root allocation still advances the ship to phase 4 with timer 119,
leaves no unmanaged Java child, and is never retried. The initializer returns
without extending or displaying the anchor. Later allocated slots
execute during that pass; the final ball already has collision `$81` from
`GBall_MakeBall` (`0x18356`) while it deploys. Slot-local defeat converts each
part in place to Obj3F rather than freeing all links or allocating replacement
explosions. The conversion pass still displays the old part; Obj3F's own
initialization pass makes its sound request without display/animation fallthrough. The ball's defeat countdown starts from its current radius.

On exhausted `FindNextFreeObj`, a1 points to `0xFFF000`, beyond executable
SST, and the unconditional final conversion writes there. It does not turn
the last real chain link into a ball. Partial real prefixes remain harmless;
no extra real object is synthesized. The resulting out-of-pool SMPS RAM
corruption is outside this object model's scope and is not claimed emulated.

## Bounded verification and inherited gaps

`TestS1EggmanNativeComponents` independently reads ROM component tuples and
branch opcodes, checks first-pass topology/order/priorities, lower holes,
full-RAM failure/no retry, actual scripts/cadence/reset, shipped LZ branches,
escape child-slot release, post-camera flag ownership, native/extended width,
MZ/SYZ precedence, and full recreation plus forward playback for all five
bosses. GHZ assembly tests separately exercise geometry/contact, partial RAM,
per-part defeat lifetime and restore after the anchor has expired. Existing
boss graph tests retain meaningful identity/state/forward assertions.

These short checks apply to the boss mechanics in GHZ3, MZ3, SYZ3, LZ3 and
SLZ3. They do not certify all act routes, donated characters, teams, continuous
movies or pixel/audio parity. Parent integration owns combined smoke and
route comparison. Missing slots are a demonstrated allocation mismatch,
not a measured explanation of any particular continuous-chain divergence.

The final combined focused invocation used:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS1EggmanNativeComponents,TestS1GhzWreckingBallNative,TestS1GhzBossGraphRewind,TestSonic1SyzBossInstance,TestS1SlzBossSpikeballGraphRewind,TestS1SyzBossBlockGraphRewind,TestRewindFixS1Batch2Codecs,TestS1BossExplosionRomArt' \
  '-Dsonic1.rom.path=<existing absolute S1 REV01 ROM>' test -B
```

On the final production source this executed 44 tests: all 41 tests outside
`TestSonic1SyzBossInstance` passed, including both EGL art tests; its dynamic
recreate test also passed. Two older static SYZ fixtures failed because their
placeholder placement was outside the now-correct arena camera. Their fixture
was corrected to real arena coordinates before a narrow rerun. Queue wait was
356 seconds; Maven reported 1 minute 21 seconds execution. No skips or errors.
An earlier assertion iteration exposed the new visual class's missing generic
rewind probe constructor; adding the existing ObjectSpawn probe contract fixed
both independent forward-restore failures. Initial compile-only failures were
corrected before the assertion run; they did not count as test passes.

The final narrow `TestSonic1SyzBossInstance` rerun used the same command flags
and absolute ROM, selecting only that class. It passed all 3 tests with no
errors/skips (2-second queue wait; 27.365 seconds Maven execution). Besides the
placement coordinates, its packed `rawYWord` must match the arena Y because
`ObjectPlacementController` applies its vertical filter to that native word.
Thus all 44 focused test identities pass across the completed combined and
narrow runs; the combined invocation itself is reported above as red.

The affected guard invocation selected
`TestRewindFieldDispositionGuard,TestRewindCoverageGuard,TestObjectPriorityBucketGuard,TestRewindRecreateLinkToleranceGuard,TestObjectServicesMigrationGuard,TestObjectUpdateClockTerminologyGuard`
with `-Pguards`, the same queued/environment flags, and no ROM requirement.
It ran 19 tests: 18 passed, with one explicit-policy finding for
`S1EggmanVisualComponent#objectRef#shipOwner`; no errors/skips. Queue wait was
37 seconds and Maven execution 2 minutes 22 seconds. The runtime compact codec
already round-trips that identity; the coverage audit additionally requires an
explicit CAPTURED registry entry. Shared-policy ownership was coordinated with
the parent rather than suppressing the field or expanding the gap baseline.
After the parent authorized that exact entry, the queued narrow rerun selected
`TestRewindCoverageGuard,TestRewindFieldDispositionGuard,TestS1EggmanNativeComponents`
with `-Pguards`, the same environment, and the absolute S1 ROM. All 12 tests
passed with zero failures/errors/skips (8-second queue wait; 1 minute 20 seconds
Maven execution). No other policy entries were changed.
No broad smoke/full-suite or continuous-route pass is asserted here.

## SLZ escape follow-up

After milestone `d8aca9ad72607af7381cf7771cbbb629481c3d5e`, the chain worker
reported a one-update difference in SLZ's expanding right bound. Native aux
records independently establish the dispatch boundaries: row 8283 first sets
status bit 7 while the ship still has routine-secondary 2; row 8284 selects 6
with timer 120; row 8405 selects Recover with timer -24; row 8471 selects Escape
with timer 42 without moving; row 8472 first runs Escape. These are comparison
observations, never gameplay inputs. The earlier post-events bound probe could
not identify which owner was late. The coordinated unchanged-super probe in the
chain worker's completed job 19061 showed the killing hit arriving after the
engine boss's row 8284 update; its next update consumed the defeat settle pass
at 8285. The one-update offset therefore already existed before Recover.

Source inspection separately exposed a definite omitted movement call. Shipped
REV01's Escape `.flee` at `0x19398` calls `BossMove` at `0x18050`, then branches
from `0x1939C` to ShipUpdate at `0x1917A`, which calls `BossMove` again. Thus
each active Escape pass advances X by eight pixels and fixed Y by -128/256 pixel
before the sine copy. Recover's exit at `0x19372` instead branches to MoveUpdate
at `0x1919E`: it does not fall through to Escape. The short regression pins the
complete branch/call bytes and verifies independent motion/clock and rewind.

This movement omission does not explain a missing early +2 bound update:
below `0x2160`, the native and engine Escape branches expand unconditionally;
the previous rendered flag is consulted only at or beyond that bound. No
capsule width or Recover-to-Escape fallthrough adjustment is justified.

The killing-hit cause is the boss spikeball's strict `<32` distance test.
`BossSpikeball_CheckCollision` instead performs four unsigned word endpoint
comparisons, rejecting only `BLO`, so touching endpoints count. The shipped
comparisons/branches are at `0x19694`, `0x196A4`, `0x196B4`, `0x196C4`; cumulative
signed hitbox deltas at `0x197D2` are `-24,+48,-24,+48` for the boss and
`+8,-16,+8,-16` for the ball. Each endpoint wraps to 16 bits before comparison.
Native row 8282 leaves the ball at `(0x211A,0x0244)`. In row 8283 the ship moves
to `(0x211E,0x0224)` before that later ball slot runs: the Y separation is
exactly 32. Native accepts it and clears the killing ball's velocity before
ObjectFall; the old engine test rejects it and hits one pass later. The fix uses
the native endpoint arithmetic. Independent tests use arbitrary centres and
cover all four edges, corners, wrap, pre-physics contact and recreated forward
execution; no route values select runtime behavior.

## FZ independent components and animation follow-up

`BossFinal_ObjData` at `0x1A524` contains six rows, not a controller plus six
visuals. Row zero is the visible/controller Eggman at `(0x0100,0x0100)`; rows
one through five are panel, legs, cockpit, empty ship and flame. ObjData2 at
`0x1A560` assigns routines 2/4/6/8/10/12 and priorities 4/1/3/3/3/3. Shipped
FixBugs=0 leaves the legs/cockpit/flame active widths zero; these are preserved.
The controller allocates its five later visual rows with FindNextFreeObj, then
the launcher with FindFreeObj, then four cylinders with FindNextFreeObj anchored
to the controller. A native route's occupied slot 34 consequently produces
controller 33, visuals 35–39, launcher 40, cylinders 41–44. Native aux slot 35
is the panel (routine 4/frame 11), not the visible Eggman controller.

Every component now owns its dispatch, previous post-camera render flag,
animation cursor and lifetime. The parent renders only its current mapping.
The launcher converts to Obj3F in its own SST even at full RAM; initialization
then skips display and five eight-pass frames release that same slot. Its
earlier lower-slot FindFreeObj result waits until the next execution pass to
initialize coordinates. Native per-owner deletion gates are retained rather
than substituting parent destruction or generic object culling.

Ani_SEgg (`0x1A1B2`), Ani_Eggman (`0x18468`) and Ani_FZEgg (`0x1AB8A`) are read
from the injected ROM reader. WAIT and PLASMA do not advance the controller's
animation; Crush advances it only on its active-child path; Fall writes mapping
frame 6 directly. The damaged cockpit runs its own ROM script and burst clock.
Its bursts use the same allocation-before-RNG/downward-offset Obj3F behavior
as the standard bosses. Cylinder frame selection also advances at exactly -8
and +39 pixels: native `BCS` rejects negative displacement after subtraction,
not equality (`0x1ACFA`, `0x1AD06`, `0x1AD0A`).

FZ's controller must retain its solid pushing bit in its live SST while its
engine placement record changes. It now uses the existing instance-latch
policy, as its cylinder already did. A short production contact test establishes
push, moves the controller, then jumps away: shipped Solid_NoCollision writes
the word `0x0001` across animation/previous-animation while preserving the
already selected mapping. The ROM call at `0x1A6EE` targets SolidObject at
`0x10062`; its push-bit test and word-write sequence is pinned at `0x1021E`.
This proves ownership independently of the full route.

The chain's row 354 `00/02` discrepancy is Sonic's animation byte, with the
mapping/physics still exact. The topology mismatch alone does not establish its
cause. Existing map-frame-based roll suppression remains an explicit inherited
contact approximation pending the next coordinated per-owner solid probe;
this change does not manufacture another animation correction or claim the
continuous FZ frontier is closed. Launcher attack saturation/RNG and broader
campaign/audio parity are outside this bounded component follow-up.

### Follow-up bounded validation

The first queued attempt stopped at test compilation: an anonymous
StubObjectServices override's unqualified `rom()` selected its inherited Rom
method instead of the enclosing harness's RomByteReader method. Qualifying the
call corrected the test without changing production bytes. It executed no tests.

The completed focused command was (`SONIC1_ROM_PATH` denotes the existing,
verified absolute S1 REV01 ROM path):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS1SlzEscapeNative,TestS1SlzSpikeballNativeContact,TestS1FzNativeComponents,TestS1FzNativePushLatch,TestS1FzBossGraphRewind,TestS1EggmanNativeComponents,TestS1SlzBossSpikeballGraphRewind,TestSonic1FzBossCombatContact,TestSonic1FZBossDamageFlash,TestSonic1FzBossEscapeHitCue,TestFZCylinderCadence,TestFZPlasmaBallAnimation,TestSonic1FZBalanceWidth,TestSonic1FzWidescreenAndTeamSafety,TestSonic1FzRomMappings,TestSonic1FzSeggMappingRemap' \
  "-Dsonic1.rom.path=$SONIC1_ROM_PATH" test -B
```

Session 47099 waited 93 seconds and executed in 28.565 seconds: 78 tests,
74 passed, one failure, three errors, zero skips. The three errors exposed the
new FZ visual's constructor reading ROM services during generic reconstruction,
which runs outside its normal construction context. Its recreate hook now
constructs without asset reads, binds the unique FZ controller, and restores
captured ROM-derived metadata and animation state. It allocates no SSTs.

The SLZ failure exposed an unintended second test ball: timer 40 ran the real
drop branch while initializing visuals, so the test's first-ball lookup updated
the falling ball rather than its intended flying ball. The fixture now uses
timer 39 and requires one boss, one seesaw, one ball and exactly six managed
slots. Production collision bytes were unchanged by that fixture correction.

The affected rerun selected
`TestS1SlzSpikeballNativeContact,TestS1FzNativeComponents,TestS1FzBossGraphRewind,TestRewindFieldDispositionGuard,TestRewindCoverageGuard,TestObjectPriorityBucketGuard,TestRewindRecreateLinkToleranceGuard,TestObjectServicesMigrationGuard,TestObjectUpdateClockTerminologyGuard`
with `-Pguards` and the same environment/ROM flags. Session 79076 waited 386
seconds and executed in 2 minutes 32 seconds. All 41 tests passed: 22 affected
behavior/restore tests plus 19 structural guard tests, zero failures/errors/skips.
Thus all 78 focused behavior test identities pass across the completed original
and affected rerun; the original invocation remains recorded above as red.
Already passing unrelated selections were not repeated. No smoke or continuous
route result is claimed by this lane; the chain owner qualifies these commits
in its next necessary production replay and owns shared frontier prose.
