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

At graph commit `8653f9232`, the body still contains an inherited synthetic
player-facing/±32 anchor absent
from its native routine; that separately authorized next slice must use literal
spawn orientation and native body X/collision origin. This graph preserves the
existing shared S3K death recipe: nativeExplosion allocates Animal, whose INIT
then creates EnemyScore after deferred RNG, whereas the shared recipe creates
points in the explosion pass. BREAK request ownership is likewise not qualified
here. Those limits are neither waived by graph replay nor silently changed.


## Separate body orientation correction

`Obj_MonkeyDude` never calls Find_SonicTails. Its WAIT jumps to `loc_85652`
(Animate_Raw then Obj_Wait); ACTIVE changes Y only. The routine tail
`Sprite_CheckDeleteTouch` and `Touch_Width` read literal body `x_pos`. Placement
render_flags bit0 stays fixed; the arm root's player-side attack check does not
turn or displace the body. The previous two legacy tests asserted a synthetic
32-pixel shift and are replaced with literal flip0/1 oracles across both P1
sides, body animation and two capture/replay cycles. The existing actual
placement test additionally checks ROM flip1 and body/touch X`$1E58` at all
five production presets.

40269 is the negative:2/2/0/0 at10:27:37 BST,23.724s execution/0s queue; old
touch X is132/68 instead of100. The source removes only the body player-facing
writer, synthetic offsets and now-unused fields, preserving arm targeting,
animation, placement and shared clocks.54064 passes18/0/0/0 at10:30:59 BST,
71s execution/113s queue, with
`TestMonkeyDudeBadnikInstance,TestMonkeyDudeNativeSstGraph,TestS3kMonkeyDudePlacedGraph`
in the normal ROM-backed lane. The negative uses the two new body methods with
`--lean`; production checks retain the normal lane.

Strict39401 repeats the exact85471 selector/profile on the changed body:
5/1/0/0 at10:33:01 BST,96s execution/0s queue. Four controls remain green;
continuous chain remains189/12,719/82,067 raw mismatched fields, the same first
fields and input68,801 stop. Alternate80705 repeats the exact40737 selector:
1/1/0/0 at10:33:36 BST,34.562s execution/97s queue;60 grouped spans, first25,589
animation`$13/$05`. Sampled occupancy still first differs at726 with extra
engineAirCountdown11. This closes a source-confirmed body conformance omission
and moves no strict frontier. No rendered pixel or audio parity is asserted.


## Shared S3K projectile native INIT and retirement

Separate slice over RNG85ca2a3c6: `CreateChild2_Complex` copies mappings/art,
positions and word velocities into a cleared higher SST; collision remains0.
`S3KBadnikProjectile_Init` `$86D4A` reads ObjDat and sets shield-reaction bit3,
then falls through `$86D5E` Main, its movement callback, and
`Sprite_CheckDeleteTouchXY`. The tail checks unsigned coarse X against `$280`
at native width and unsigned `(Y-CameraY+$80)` against `$200`, inclusive: Y
relative−128..384 independently of screen height. The existing declared wider
coarse-X viewport extension is preserved. The admitted path independently
calls AddList and Draw even after shield deflection clears hurt flags.
`Go_Delete_Sprite` publishes status7/code`Delete_Current_Sprite` now; the next
slot dispatch clears without another movement. Two captured booleans retain
INIT/pending deletion through pure recreation, without a fake full native header
projection, generic allocator/timing changes or fixture gates.

Actual native segment2 row969 has projectile SST43 `$272B/$0444`, cameraY`$047C`:
56px above the viewport, inside the native−128 boundary. The previous 48px
rectangle deleted it immediately. The short test additionally drives the
ordinary real Monkey body/five-arm/hand graph to its once-only throw with the
coconut56px above the viewport; the higher projectile moves in that same pass.
Other cases verify literal Y/coarse-X equality and adjacent failures, unsigned
Y wrap, full manager snapshots before/pending/after clear with two replay
cycles, and rendering independent of shield-deflected touch.

New `TestS3kBadnikProjectileNativeLifetime`: baseline64281 fails13/13/0/0
at11:08:27 BST,26.822s execution/0s queue. An initial test-only stream typing
error caused compile failure90570, which is not native negative evidence.
Candidate63437 completes614/1/0/0 at11:10:26 BST,71s execution/0s queue:
all13 new checks and the remaining runtime consumers pass; the sole red scalar inventory
`TestScalarOnlyCodecDeletion#batch170S3kBadnikCutsceneScalarsAreMutableForCompactRestore`
that still named the synthetic `treeAnchorX` removed by ddbc. Removing only that
absent field, with its native X rationale, passes exact73701:1/0/0/0 at11:11:26
BST,25.626s execution/0s queue. No allowance or unrelated inventory changes.

Normal affected selector:
`TestS3kBadnikProjectileNativeLifetime,TestBloominatorBadnikInstance,TestMonkeyDudeBadnikInstance,TestMonkeyDudeNativeSstGraph,TestS3kMonkeyDudePlacedGraph,TestS3kBadnikTouchResponseProfiles,TestS3kSelfContainedTransientRewind,TestS3kRewindFixBatch1Codecs,TestScalarOnlyCodecDeletion,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The exact scalar-only method rerun uses the bounded `--lean` lane; all ROM/trace
checks retain normal admission. Commands use Java25, surfaceless EGL,
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=<selector>'
'-Ds3k.rom.path=<absolute verified ROM>' test -B`.

Strict76116 repeats exact85471 selector/profile with read-only occupancy enabled:
5/1/0/0 at11:13:37 BST,101s execution/16s queue. Original AIZ, initial HCZ and
both prefixes pass. Chain189/12,719/82,067 raw field mismatches, first fields
and input68,801 stop are unchanged. Alternate90350 repeats exact40737 selector:
1/1/0/0 at11:15:54 BST,37.454s execution/63s queue,60 compressed spans and first
25,589 animation`$13/$05`. The observer uses `OGGF_SLOT_PROBE=1`,
`OGGF_SLOT_PROBE_FULL=1`, output only under this tree's target. Presence now
matches through sampled segment2 row1054, closing the earlier969 absence;
at1055 native SST43 is still `Delete_Current_Sprite` `$1ABB6` at `$267F/$04BF`,
while the engine has cleared it. ROM bytes`22487011720022C151C8FFFC32C14E75`
corroborate that clear routine; this remaining dispatch/clock boundary is not
a fitted extra wait. At second entry3981 engine ring25/flash17 versus18/22
remains. No strict trace closure, native pixel/audio parity or full projectile
header conformity is asserted.

The two positioned ending lease errors are inherited only relative to pre-RNG
ddbc. They are NEW/unresolved delivery blockers until matched against pinned5b2
or fixed at the fresh title owner/art lifecycle; prior delivered seven-case
incoming qualification is not superseded by a five-cold/two-error green claim.

Final affected guards28232 pass111/0/0/0 at11:18:31 BST,32.393s
execution/29s queue. This is bounded owner/consumer validation, not broad smoke
or release full-suite coverage.
