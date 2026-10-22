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
