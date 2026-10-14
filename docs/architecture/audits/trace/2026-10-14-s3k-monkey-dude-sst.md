# S3K MonkeyDude physical SST graph — 2026-10-14

Isolated `.worktrees/ai-s3k-parity-trace`, over title repair `92d376187` and
qualified continuation dependency `9fa71e966` (original `ae632fe2a`). Native ROM
SHA1 `CFBF98C36C776677290A872547AC47C53D2761D6`, CRC32 `63522553`; V5 fixtures
and Genesis inputs remain unchanged and comparison-only. The owning native
movie/core pins are in [the preceding title audit](2026-10-14-s3k-fresh-title-sst.md).

## Native ownership and implementation

`Obj_MonkeyDude/MonkeyDude_Init` (`$8715A`, `sonic3k.asm:182697–183325`)
selects normal/flipped `ChildObjDat_MonkeyDudeChildChain` then calls
`CreateChild4_LinkListRepeated`. Five checked actors have subtypes0/2/4/6/8,
priority `$300` (bucket6), copied body coordinates and numeric predecessor
parent3 links. Root init anchors at±`$E`/−2; followers initially retain the copied
body coordinates; the hand initializes mapping6 and its root/second-follower
addresses. Init returns without running subsequent movement. Existing root
angle/RNG/throw math moves unchanged into the real root; independent followers
and hand now dispatch after it, including fractional positions and hand-owned
projectile allocation before that hand's movement.

Normal body culling uses `Sprite_CheckDeleteTouch/loc_85094`: release respawn
state and publish status7, then clear the following pass. Unreleased
`Obj_WaitOffscreen/loc_85AD2/loc_85AFC` clears immediately. `Child_Draw_Sprite`
runs its actor first and then reads predecessor status7, likewise delaying its
own clear until the next pass. The graph captures these pending markers and
restores numeric native links; pure spawn recreation creates no children.

`Touch_EnemyNormal` rewrites the body in its physical slot with explosion code,
routine0 and status7. `MonkeyDudeExplosionObjectInstance` retains the displaced
mapping until its own `loc_1E626` INIT; arms may consume that byte before INIT.
The shared explosion exposes only a protected final read of its actual rendered
mapping index; no animation/public API/pin changes. Narrow source-owned headers
read actual managed body/arm/replacement owners through `NativeSstSlotAccess`;
unknown live headers reject before movement mutation. No fake ten-field
`NativeSstStateSource`, ghost-slot reservation, global clock/allocator change,
trace hydration or fixture-specific behavior is introduced.

The observed fixture return `aiz_2` is AIZ1 segment2. The actual placed body is
`X=$1E58,Y=$0490,id=$8E,subtype=$10,flip=1`, table `$1E44DA`, record `$1E45E2`.
A first positioned test incorrectly chose AIZ2 and failed all five cases with
absent placement; selecting the actual ROM table fixed setup without runtime
tuning. Direct title AIZ2 checks remain separate lifecycle evidence.

## Negative evidence and qualification

Every Maven selector below uses the normal local queue, `-Dmse=off`, Java25,
absolute `-Ds3k.rom.path`, and `JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`.
No broad or release full suite runs.

| Check | Result | Attribution |
| --- | --- | --- |
| `TestMonkeyDudeNativeSstGraph` initial native graph |16053:4/4/0/0| Both orientations lack five actors; retirement/recreation negative. |
| Unreleased wait-owner boundary method |50407:1/1/0/0| Native immediate clear was incorrectly delayed; source-corrected. |
| Normal/power replacement pre-INIT mapping plus real placement |56343:7/2/0/0| Literal retained mapping1 was0 in both replacement paths; five actual preset placement/world checks pass. |
| Final affected selector below |36090:104/0/0/0,10:12:19 BST|79s execution,359s queue; graph8, legacy5, real placement5, destruction/explosion/RNG/API consumers and required startup60. |
| Rewind/source/trace/hardware guards below |90457:111/0/0/0,10:18:17 BST|32.326s execution,98s queue. |
| Strict originalAIZ/initialHCZ/both prefixes + continuous chain |85471:5/1/0/0,10:17:44 BST|98s execution,0s queue; four controls pass, exact inherited chain failure remains. |
| AlternateAIZ `-Ptrace-segments` |40737:1/1/0/0,10:23:19 BST|36.854s execution,0s queue;60 grouped spans unchanged, first25,589 animation `$13/$05`. |

Affected selector:
`TestMonkeyDudeNativeSstGraph,TestMonkeyDudeBadnikInstance,TestS3kMonkeyDudePlacedGraph,TestExplosionObjectInstance,TestDestructionEffects,TestAnimalObjectRngOwnership,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
Guards use `-Pguards` with
`TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard`.
Strict85471 uses `-Ptrace-replay` with
`TestS3kAizTraceReplay#replayMatchesTrace,TestS3kSonicTailsHczSegmentTraceReplay#replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`;
alternate40737 uses `TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`.

The frozen eight Java files have path/content digest
`007d9f765821d4cbc0e0408d5c015eae7483ee665a5dca1590b41c822a4757c6`.
Both physical chains retain independent identities across complete manager
restoration; normal/power replacement tests cover before/after INIT and child
retirement with replay. Actual ROM placement at320/352/400/528/800 verifies
camera width before/after load and twice replays45 inputs from the full registry.
These are local positioned graph checks, not cold campaign or pixel/audio proof.

## Trace effect and next owners

The former missing-arm occupied-slot gap at segment2 row127 is closed. Sampled
occupied-slot sets match through row725; first next difference at726 is extra
engineSST11 `S3kAirCountdownObjectInstance` beside10 while native occupies10 only.
The occupancy observer masks native code pointers to a low byte; labels cannot
certify actor types. At second entry3981, engine ring25/flash17 still differs
from native18/22. The flash still misses same-pass INIT, leaving the previously
observed extra Level pass and persistent vine angle; no local angle/carry fix.

The continuous chain retains189/12,719/82,067 **raw per-row field mismatches**,
first segment6 TailsX3,319 `$31C1/$31CA`, segment8 TailsX1,583 `$366C/$3674`,
returnedHCZ mapping1,507 `$63/$95`, firstY1,510 `$7D6/$7DF`, and LEVEL ownership
loss at input68,801 after5,725/14,208 returnedHCZ rows. Standalone counts are
compressed spans; these chain counts are not. No strict frontier moves.

The body still contains an inherited synthetic player-facing/±32 anchor absent
from its native routine; that separately authorized next slice must use literal
spawn orientation and native body X/collision origin. This graph preserves the
existing shared S3K death recipe: nativeExplosion allocates Animal, whose INIT
then creates EnemyScore after deferred RNG, whereas the shared recipe creates
points in the explosion pass. BREAK request ownership is likewise not qualified
here. Those limits are neither waived by graph replay nor silently changed.
