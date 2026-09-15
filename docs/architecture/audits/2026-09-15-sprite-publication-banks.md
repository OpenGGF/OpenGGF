# Sprite-publication bank audit — 2026-09-15

## Initial inspection scope and result

Bounded inspection following the S3K main-player Tails regression introduced
by `7653029ab` and fixed by `1055d9cc3`. Source inventory was inspected at
`6e6740a07ab9b38627026b8cf1ad536ece059551`. This is a bank/caller and
reachability audit, not a complete audit of that commit's loading/timing
changes, a visual certification, or an implementation pass. The authorized follow-up
implementation and its separate evidence are recorded below.

The unused-capacity fix addresses the demonstrated Tails corruption. A host/donor address mismatch and three
additional concrete allocation/reuse risks merit targeted reproduction.
None is established here as a second visible regression caused by that commit.
Overlapping allocations alone do not prove simultaneous incompatible use.

## Publication boundary

[LevelSpritePresentation](../../../src/main/java/com/openggf/level/LevelSpritePresentation.java)
enables retained publication through `SpriteTablePublication` on the level
initialization profile. The only production implementation found is
[Sonic3kLevelInitProfile](../../../src/main/java/com/openggf/game/sonic3k/Sonic3kLevelInitProfile.java).
Ordinary S1/S2 levels therefore do not exercise this retained-publication
path; their character art can exercise it when donated into an S3K host.

[PlayerSpriteRenderer](../../../src/main/java/com/openggf/sprites/render/PlayerSpriteRenderer.java)
is the sole production caller of `bindPatternBank`. Its current call supplies
the mapping frame; the old whole-bank overload has no production callers.
Bounds queries no longer publish bank contents. The frame still stores one
`PatternVersion` per integer tile ID, and successive bindings use `putAll`.
It cannot represent two different simultaneously required images at one ID.
Filtering unused slots does not solve an overlap in slots both sprites use.

## ROM-backed bank inventory

The existing production art loaders were invoked in a temporary Java probe
with the existing `s1.gen`, `s2.gen`, and `s3k.gen` files. Player loader sources
had no intervening changes from the previously built source. Values below are
loader-reported capacity; ranges are half-open. They are not counts of tiles
used by every animation. S3K shield capacities used the same mapping/DPLC
loader and bank-size resolver as `loadSingleShieldArt`.

| Art | Base | Capacity | End exclusive |
| --- | --- | ---: | --- |
| S1 Sonic | `0780` | 23 | `0797` |
| S2 Sonic | `0780` | 31 | `079F` |
| S2 Tails body | `07A0` | 28 | `07BC` |
| S2 Sonic dust | `049C` | 16 | `04AC` |
| S2 Tails dust | `048C` | 16 | `049C` |
| S3K Sonic / Knuckles | `0680` | 29 | `069D` |
| S3K Super Sonic | `0680` | 33 | `06A1` |
| S3K Tails body | `06A0` | 24 | `06B8` |
| S3K tail | `06B0` | 9 | `06B9` |
| S3K dust | `34000` | 16 | `34010` |
| S3K fire shield | `079C` | 33 | `07BD` |
| S3K lightning / bubble shield | `079C` | 36 | `07C0` |
| S3K insta-shield | `079C` | 29 | `07B9` |

In a native S2 host, the separate tail renderer reuses the Tails art set at
`07B0`, overlapping body-bank capacity. Donating this art into S3K exposes
a separate host/donor address-selection problem described below; it would be
incorrect to assume that configuration actually receives `07B0`.
A dedicated S2-donor pixel regression is still missing. The existing
Tails reproduction covers native S3K art, main/sidekick relocation, and direct
and deferred sprite-table collection, not the whole donor matrix.

## Prioritized follow-up work

### 1. S2-style Tails in an S3K host — high priority

Both legacy and prepared paths in
[LevelPlayableArtInitializer](../../../src/main/java/com/openggf/level/LevelPlayableArtInitializer.java)
select separate-versus-shared tail art using the donor, but in the shared-art
branch obtain `getTailsTailVramBase()` from the host `GameModule`.
Only `Sonic2GameModule` overrides the default `-1` result. `Sonic3kGameModule`
does not. With S2 donor art and an S3K host, the first tail renderer therefore
receives base `-1`; `SpriteArtSet` and `DynamicPatternBank` do not normalize
it, and only subsequent tail instances are relocated by the initializer.
This is a concrete source-level mismatch, not merely overlapping capacities.

Next test: initialize native S3K with S2 donor Tails, as main player and then
as sidekick, assert a valid non-overlapping tail allocation and inspect roll
and spindash pixels. Include S1 host coverage because its default is also
`-1`, even though it does not use retained S3K publication. Source blame shows
the host lookup already present in `7ac365dc1f` (July 12) and the default in
`c772df40eb` (March 25), predating `7653029ab`; this audit does not identify
the original introducing commit. No live visual reproduction was run.

### 2. Simultaneous insta-shields — high priority

[Sonic3kObjectArtProvider](../../../src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java)
creates one renderer per shield type; all four types use base `079C`.
[ShieldAnimationArtLifecycle](../../../src/main/java/com/openggf/game/sonic3k/objects/ShieldAnimationArtLifecycle.java)
keeps an instance-local animation cursor but obtains that shared renderer.
[DefaultPowerUpSpawner](../../../src/main/java/com/openggf/level/objects/DefaultPowerUpSpawner.java)
explicitly supports CPU-owned insta-shields in auxiliary object space.
Two Sonic owners can therefore require inspection for different active
insta-shield frames sharing the same renderer and bank.

Next test: a two-Sonic team, staggered insta-shield activation, compare both
owners' published pixels against isolated rendering; repeat across rewind.
Then inspect mixed elemental/insta shields only where ownership and fixed-slot
rules allow coexistence. Do not infer coexistence from the art registry alone.
Historical attribution requires a matched before/after rendering comparison:
shared renderer/bank use could also cause older atlas batching defects.

### 3. Powered-up Sonic sidekick allocation — high priority

[LevelPlayableArtInitializer](../../../src/main/java/com/openggf/level/LevelPlayableArtInitializer.java)
relocates ordinary sidekick bodies into allocated virtual banks and initializes
a super-state controller for each player. However,
[Sonic3kSuperStateController](../../../src/main/java/com/openggf/game/sonic3k/Sonic3kSuperStateController.java)
loads a new Super Sonic renderer at the art set's fixed `0680` base and replaces
the player's existing renderer on transformation, without preserving that
player's relocated bank. Controllers are per player, but their new banks share
addresses. Ordinary Sonic/Knuckles main-player art also starts at `0680`.

Next test: main Knuckles or Sonic with a Sonic sidekick; transform the sidekick
through a supported activation path, assert bank ownership and compare both
players' pixels while their mapping frames differ. Cover revert and rewind.
Also check duplicate powered-up Sonic owners. The audit establishes the
allocation path, not which normal-play routes trigger it or a visual failure.

### 4. Lightning sparks versus LBZ surface splash — medium priority

[LightningSparkObjectInstance](../../../src/main/java/com/openggf/game/sonic3k/objects/LightningSparkObjectInstance.java)
uses `TRANSIENT_EFFECTS.base()` (`48000`) for five static tiles, caching them
once per instance. `Sonic3kObjectArtProvider` assigns exactly that same base
to its mutable surface-splash renderer. The latter is used by
[Lbz1GroundLaunchIntroInstance](../../../src/main/java/com/openggf/game/sonic3k/objects/Lbz1GroundLaunchIntroInstance.java),
which also shares one splash renderer across the affected players.

Next check: establish whether a supported LBZ entry can display sparks and
splash together, or restore them into overlapping lifetimes. Sparks live only
21 updates, so coexistence cannot be assumed. If reachable, compare both
art sources at IDs `48000`–`48004`, including a skipped publication and rewind.
Shared splash rendering is harmless when all participating cursors stay in
sync; test divergence only if the production lifecycle permits it.

## Lower-risk or rejected candidates

- **Ordinary sidekicks and ghosts:** bodies and duplicate dust/tail banks use
  the level-owned monotonically advancing allocator within
  `SIDEKICK_BANKS` (`38000`–`3FFFF`). Both ghost renderer families use it too.
  Bounds and overflow checks are present. No ordinary allocation collision
  was found; the powered-renderer replacement above bypasses this protection.
- **Dust:** S2 dust ranges are adjacent, not overlapping. S3K dust uses a
  separate virtual range; further instances are relocated by the initializer.
- **Continue screens and S2 endings:** explicitly separated screen/ending
  ranges; these are not evidence of an active S3K level-publication collision.
- **AIZ intro Sonic:** normal and Super renderers intentionally share an
  address, but the intro selects one renderer and hides the controlled live
  player. No second ordinary simultaneous owner was established here.
- **Hyper trails:** rejected as a presumed delayed-art collision.
  `HyperFormTrailSample` delays position and historical art attributes while
  keeping the mapping frame live. ROM `Obj_HyperSonicKnux_Trail_Main`
  (`sonic3k.asm:35378`) explicitly copies `Player_1+mapping_frame`, and the
  object uses `ArtTile_Player_1`. Giving it independently delayed animation
  artwork would change the shipped behavior.
- **Rewind:** presentation records retain immutable pattern versions. This
  protects an already-correct snapshot from later bank changes; it also
  faithfully retains a bad snapshot if two owners collided during preparation.
  Other mutable object-art paths are not comprehensively certified by this
  player-bank inspection.

## Recommended next scope and verification limits

First reproduce the S2-Tails-in-S3K host/donor address mismatch, then add one
ROM-backed rendering test for staggered two-Sonic insta-shields and one for a
powered Sonic sidekick.
Use isolated-owner pixel output as the comparison, exercise both draw orders
and deferred collection, and verify capture/restore. Establish LBZ coexistence
before investing in a splash/spark visual fixture. Avoid changing all shared
banks indiscriminately: native mutual exclusion and intentional sharing matter.

No engine code was changed, no new gameplay/visual regression was reproduced,
and no broad engine suite or gameplay capture was run for this inspection.
Validation comprised source/caller inspection, stock-ROM loader measurements,
the Hyper-trail disassembly check, and documentation syntax/link checks.
The temporary inventory probe is regenerable in minutes and is not retained.

## Authorized follow-up implementation

The follow-up starts at `d92fea6f15ba0f90f9df15aab2d8c19763ded87f`.
Three independent Astra investigations reproduced the high-priority risks.
These are additional allocation defects; the tests do not attribute their
introduction to `7653029ab`.

### Donated shared Tails art

`971d46f49` (investigation commit `7d58869c7`) relocates the first shared-art
donor tail as well as duplicates, through the existing level allocator. Both
legacy and prepared initialization reserve the complete 28-tile donor bank.
Native S2 keeps its `$7B0` tail address. No donor API change is needed.

On unchanged baseline production code, all eight combinations of S1/S3K host
contracts, main/sidekick and legacy/prepared initialization reproduced base
`-1`. The fixed focused run passed 24 tests with no skips:
`TestDonorTailsTailBanks,TestLevelPlayableArtInitializerModArt,TestTailsRendering`,
using `maven_queue.py -Dmse=off` and the absolute `sonic2.rom.path`.
The donor regression uses real S2 ROM art and mocked host contracts; it compares
139 body frames against directional-roll/spindash tail art, in both draw orders
and direct/deferred collection. It is not a live donor-zone gameplay capture.

### Powered Sonic owners

`56fdcb50c` (investigation commit `2c0204756`) reserves a private powered bank
when the owner's normal renderer already occupies the virtual player range.
It reserves the full 33-tile Super capacity rather than reusing the 29-tile
normal allocation. Native main-player `$680` and the ROM's current-art Hyper
trail remain unchanged. Allocation is lazy and reused across transformations;
revert and rewind retain the appropriate renderer and donor render context.

Both new Sonic/Knuckles-main cases failed bank ownership on unchanged baseline
production code. The fixed focused run passed 22 tests with no skips:
`TestSonic3kPoweredPatternBanks,TestSonic3kSuperStateRewind`, using
`maven_queue.py -Dmse=off` and the absolute `s3k.rom.path`. It exercises supported
activation, two powered owners, isolated ROM pattern versions, both draw orders,
direct/deferred collection, revert and same-controller rewind. Controller
recreation on a level reload was inspected, not reproduced in this test.
Independent review found no blocking issue.

### Independent shield owners

`0a4184643` (investigation commit `c054e66d8`) makes shield renderers and
mutable banks owned per player, while shield types
for the same player retain their native mutual exclusion. The first non-CPU
owner keeps `$79C`; additional owners reserve 36 tiles each in the bounded
`TRANSIENT_EFFECTS + $1000..$2000` subrange. This avoids sparks/splash at the
range start and snowboard art at `+$2000`/`+$4000`. Capacity is checked against
loaded art, and exhaustion fails explicitly.

A provider-owned effects subrange was chosen instead of the existing player
allocator: shield constructors can load before playable-art initialization,
which resets that allocator, and playable-art refresh can reset it without
reloading the object provider. Provider reload clears shield ownership;
persistent objects rebind their renderers while keeping animation cursors.
Donor teardown and initialization also clear ownership. Ordinary respawn keeps
the same player identity. Native and donor shield selection is exclusive in
the current production paths: native S3K art wins when available; donor
insta-shields are the fallback. No creator API signature changes are needed.

The unchanged baseline failed all four direct/deferred and forward/reverse
render-order cases when two Sonic insta-shields were staggered by three updates:
frames 3 and 0 require different ROM pixels in shared tiles `$7A0`/`$7A1`.
An initial four-update stagger passed because its **used** slots were disjoint;
it was rejected as an inadequate reproduction. Tests also cover main fire plus
CPU insta-shield (supported fixed/auxiliary slot ownership), same-owner visual
suppression, donor fallback, rewind recreation, provider source reload and
bounded allocation. These are headless ROM-pixel tests, not a gameplay video.

The focused run passed 35 tests with zero skips, including existing shield
lifecycle, priority, lightning and rewind cases, the renderer corruption guard,
and the mandated PLC art-registry mapping check. The final narrow run added the
bounds/reset regression and confirmed the API pins: 30 tests passed, zero skips
(`TestShieldPublicationBanks`, `TestShieldAnimationArtLifecycle`,
`TestModApiPinPolicy`, `TestModApiSignatureSurface`), using queued Maven with
`-Dmse=off` and the absolute `s3k.rom.path`.

### LBZ spark/splash overlap: no runtime change

The fixed-address overlap remains a source-level risk, but a bounded entry
check did not establish incompatible simultaneous use. Ground launch clears
`jumping` and uses the spring animation. `PlayableSpriteMovement.doJumpHeight`
only admits the lightning double jump inside the `isJumping()` branch.
A temporary ROM-backed LBZ1 headless test gave Sonic a lightning shield before
the normal title-card handoff, let the intro release him, then alternated two
held/two released jump updates throughout the splash. The splash completed
without a `LightningSpark` object: one test passed, zero skips, on unchanged
production code at `d92fea6f1`. Command: queued Maven with `-Dmse=off`,
`-Dtest=TestS3kLbz1GroundLaunchIntroHeadless#lightningAbilityCannotStartDuringGroundLaunchSplash`
and the absolute `s3k.rom.path`.

This does not certify every entry/team/restore configuration. No artificial
spark spawn was used to label the overlap a gameplay bug, and neither splash
addresses nor object timing were changed. The regenerable temporary test and
log were removed. Reopen this finding if a supported overlapping lifetime is
observed; shared splash cursor divergence also remains unestablished.
