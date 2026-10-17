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


## Manager dispatch preserves the native coarse-X retirement

Separate follow-up over `bca64bb0b` (Act3 `e580b1be9`, collision `ffb12d41a`
and stable identity-map dependency): native `S3KBadnikProjectile_Main →
Sprite_CheckDeleteTouchXY → Go_Delete_Sprite` owns both bounds and delayed
clear. Although direct projectile updates retained status7, the ordinary
ObjectManager pass subsequently applied its generic camera cull and released
SST43 in the same pass. The local projectile now declares its custom lifetime;
its own update alone admits or clears it. Shared manager culling, clocks,
allocator and viewport extension remain unchanged.

The corrected normal-manager negative61600 is6/2/0/0 at12:15:54 BST,
25.445s execution (queue interval not retained): both coarse-X sides fail at
the pending physical record, while four literal Y-boundary/list/replay controls
pass. The first draft62426 also found the catalogue bug, but its four projectile
failures stopped at a wrong new identity oracle: stable ObjectRefId does not
encode a dynamic object's current physical slot. Corrected tests resolve the
captured dynamic record's exact object ID; that draft is not coarse-X kill evidence.

The two new X cases move from admitted inclusive `$380/$67F` to pending
`$37F/$680`, and then clear on the next dispatch. Four Y cases cover−129/−128/
384/385 through the complete normal manager. They assert physical slot43,
collision-list association, exact restore and two forward cycles before,
pending and after clear. Pure33919 completes55/0/0/0 at12:21:54 BST,
70s execution: all19 projectile cases and36 catalogue/descriptor cases pass.
The supplied nonexistent shortened capture-arguments selector contributed no
coverage; exact `TraceCaptureToolArgsTest`12 is supplied below.

Affected99063 passes613/0/0/0 at12:37:50 BST,29.176s execution/806s queue:
`TestBloominatorBadnikInstance,TestMonkeyDudeBadnikInstance,TestMonkeyDudeNativeSstGraph,TestS3kMonkeyDudePlacedGraph,TestS3kBadnikTouchResponseProfiles,TestS3kSelfContainedTransientRewind,TestS3kRewindFixBatch1Codecs,TestScalarOnlyCodecDeletion,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TraceCaptureToolArgsTest`.
The two distinct loading classes supply36+7 cases; mandatory S3K startup60
and actual capture arguments12 are covered. Normal commands retain Java25,
surfaceless EGL and the verified absolute ROM; no broad/full suite ran.

Strict86861 uses the exact five-case85471 selector/profile with
`OGGF_SLOT_PROBE=1 OGGF_SLOT_PROBE_FULL=1
OGGF_SLOT_PROBE_OUT=target/coarse-x-slot-occupancy`. It completes5/1/0/0 at
12:42:36 BST,98s execution/143s queue. Original AIZ, initial HCZ and both
prefixes pass. Chain segments6/8/11 retain189/12,719/82,067 raw mismatched
fields, first physics fields3,319 TailsX `$31C1/$31CA`,1,583 TailsX
`$366C/$3674`,1,510 Y `$7D6/$7DF`, and the input68,801 ownership stop.
The sampled physical-presence frontier moves1055→1688: pending projectile
SST43 at1055 now exists, and samples agree through1687. At1688 native cork-floor
fragment code `$2A5F8` occupies35 and41..51; engine keeps inert CorkFloor35 plus
12 new fragments41..52. `BreakObjectToPieces2` reuses a0, then calls checked
`AllocateObjectAfterCurrent` for N−1 pieces; this is the next source owner,
not a trace closure. Second-entry ring25/flash17 versus native18/22 remains.

Alternate74434 uses the exact40737 profile/selector and completes1/1/0/0 at
12:44:33 BST,36.163s execution/58s queue:60 grouped spans (25 physics/35
animation), first25,589 animation `$13/$05`, then25,590 Tails subpixel
`$7800/$6C00`, unchanged. Final90744 affected rewind/source/trace/hardware
111 guards pass at12:47:40 BST,33.760s execution/108s queue. These are bounded
owner/control checks, not strict whole-chain, pixel, waveform or campaign certification.


## Containing-run movie resolution for single-segment capture

Separate tooling prerequisite over coarse retirement `c46334247`: the
maintained TraceCaptureTool accepted ordinary segment paths, but TraceCatalog
could not find HCZ's movie at its containing run root. Optional copying/linking
was rejected. The resolver now reuses `resolveRunBk2` after structural manifest
validation and exact metadata game/run/source/index/member/offset/count/profile
matching; shared movie priority and lexical containment checks are retained.
An invalid containing run fails closed without borrowing another per-segment
movie. No fixture, controller, row bootstrap or run-capture support changed.

62426's catalogue negative establishes four actual failures: two missing
containing-run movie resolutions and two accepted unsafe aliases. Three new
mismatched-header cases already reject. Candidate33919 passes all36 catalogue/
discovery/descriptor cases together with19 projectile cases; exact capture
arguments12 pass99063, and source/rewind/trace/hardware guards111 pass90744.
The exact catalogue selector is
`TraceCatalogTest,TestTraceCatalogRunDiscovery,TestTraceCatalogDescriptorOwnership,TraceCatalogSpecialStageTest`.
Those pure checks use the bounded lean lane. All actual ROM/trace/native
capture consumers retain normal admission. The previously supplied nonexistent
shortened arguments class has no coverage; `TraceCaptureToolArgsTest` is the
actual checked class. Runtime source stayed identical through these gates.

Native/engine screenshot36213 retained original AIZ Monkey/body clips and
native HCZ observations, then stopped before HCZ engine boot with
`No trace matched`. Only that missing clip and a correctly identified shared
projectile spot are retried after committed-source qualification; completed
images are retained. Whole joined-run capture remains unsupported, and a
matching RAM row does not identify the currently published framebuffer scene.


## Actual native and engine image spots

Captured outside the repo under
`$HOME/Videos/OGGF/s3k-parity-trace-2026-10-14/spot-checks/`:
`ffb12d41a-v2` retains the initial four native publications and two original
engine clips; `2f50c37c0-missing-v2` supplies only the missing HCZ engine clip
and genuine shared-projectile native/engine pair. Every image below was actually
inspected. PNGs retain the provider extent and colors; no repainting, fitted
clock shift or color conversion was applied.

Native host is verified TraceChaser video port `bf3c5391c` (immutable executable
SHA256 `71b949a6b3ecb0709d4ba73f8cc0752d945a14c15c58f0326fc3adb7d4d4031b`),
official owned BizHawk2.11 archive SHA256
`cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8`.
Managed Cores.dll `0144e6e2…` and native gpgx.wbx.zst `c4231296…` are distinct
pins, checked by each launcher; the provenance JSON carries their complete
hashes, ROM/movie/compiler/adapter pins, argv/PID/UTC launch/end and callback
clocks. `AdvanceWithVideo` publishes exactly the completed hardware frame,
and its VRAM/CRAM/VSRAM copies have explicit endian metadata. Native capture
runs with the original movie SyncSettings and no RAM writes. Engine uses the
maintained TraceCaptureTool, Java25/surfaceless EGL and isolated display-free
environment. The native adapter source/executable are unchanged between retries.

| Spot | Native publication / engine source | Observed image and context |
| --- | --- | --- |
| AIZ1 Monkey, row1680 |core2191,input2190,LFC `$056F`,VInt `$0869`; engine`ffb12d41a`| Real arm/hand graph and actors are visible; HUD broadly matches. Native provider348×240 has active320×224 at(14,8), engine320×224. Arm/hand poses and terrain/parallax pixels differ. |
| AIZ1 body, row3224 |core3735,input3734,LFC `$0B77`; engine`ffb12d41a`| The retained filename says `aiz-projectile`, but native `$86D70` is Bloominator BODY. This image has no projectile qualification credit. Scene/HUD broadly matches. |
| AIZ2 shared projectile, row6970 |core7481,input7480,LFC `$1A10`,VInt `$1D13`; engine`2f50c37c0`| Full SST30 is `$86D5E`,XY `$0E03/$02FC`,mapping4,flags `$84`,art `$252A`; the small projectile is visible beside rolling Sonic in both scenes. Native348×240/active320×224 at(14,8), engine320×224; colors and poses differ. |
| HCZ entry, row0 |core53609,input53608,LFC1,VInt `$CF76`; native from full emerald movie, engine standalone`2f50c37c0`| Both providers320×224. Native currently publishes the black/red Hydrocity title; engine shows the waterfall. The current RAM gameplay row does not identify the published scene. |
| HCZ entry, row30 |core53639,input53638,LFC `$1F`,VInt `$CF94`; same two source contexts| Waterfall, falling Sonic and Tails broadly correspond. Native still has a title edge; native full-movie HUD score255900/lives1 differs from standalone engine score0/lives3. This is not a joined-run HUD oracle or grounds for gameplay hydration. |

At Monkey row1680 native producer1392 has prepared the next table while the
published producer is1391. At projectile6970 the pair is6673/6672; HCZ0 is
25631/25630 and HCZ30 is25661/25660. Prepared/published full640-byte SAT hashes
differ at all these spots, but include unused table tails and do not establish
that every active sprite differs. Monkey's prepared palette equals published
CRAM, whereas the projectile and HCZ spots differ; snapshots preserve both.
Existing RAW_RGB maps 3-bit channels to0..255; native GPGX uses the observed
0/34/68…238 ramp. This is an existing documented display-profile difference,
not evidence for changing a native palette writer. Tile/pose/parallax, active
priority and publication-phase parity are unqualified; native audio was not
captured, so engine MKV audio supplies no native waveform claim.

35098 rejected a wrong320×224 provider-size assumption before accepting any
image.36213, corrected only to preserve actual dimensions, completed initial
native/AIZ captures then failed the missing containing-run movie resolution.
62487 failed before any engine child because the new6970 selector wrongly
required AIZ1; nearby actual native RAM matches the exact LFC/P1 in AIZ2.
Only that declared scene label was corrected.34708 then completes normally0
at12:59:15 BST,31.052s execution/3s queue, on clean committed`2f50c37c0`:
native projectile15.486s, engine projectile12.483s and HCZ2.418s.
Exact child intervals and hashes are in its provenance JSON. Completed native
HCZ/Monkey/body images were reused, never recaptured. The command is normal
queued `-Dmse=off exec:exec -Dexec.executable=python3` with owned
`aiz-hcz-native-spots/run_missing_spots.py %classpath <exact clean SHA>`;
no unqueued native loop or display fallback was used.

This supplies bounded image corroboration for the actual Monkey/projectile
owners and exposes the HCZ publication/standalone boundary. It does not certify
fresh-return title pixels: that joined observer still needs accurate scene
publication instrumentation. Strict fields remain as86861/74434, and the next
physical graph owner remains cork-floor1688.


## Cork same-record fragment and ICZ table follow-up

Source context: `d8025e094` plus the owned Cork implementation (SHA256
`e221bbe2cd7e3289ccc46b7c91a3b774a03af11f99dddee70f6a1ae04272a448`),
original selection base `5b2ff8e78`. No controller, fixture, shared allocator,
collision algorithm or clock changed.

`BreakObjectToPieces2` (sonic3k.asm:45786–45822) initializes piece0 with a1=a0,
then allocates only N−1 checked AfterCurrent siblings. A failed allocation ends
the admitted mapping prefix. The original Cork record now retains its typed
identity as the first moving fragment: CNZ `loc_65D46` compares the saved child's
code to `loc_2A5F8`, rather than waiting for an unrelated replacement. The parent
moves once after the allocation walk, as `loc_2A5D8`/`loc_2A6BC` fall through.
Motion preserves the fractional long and applies old velocity before +$18;
retirement consumes the preceding Render_Sprites sign bit. The parent's $0080
priority word is bucket1; the shipped byte copy leaves a cleared sibling's word0.
Each static piece separately retains art bit15.

ICZ is a separate correction within the same owner. Init stores the subtype
nibble directly, including the bit4 small-floor branch. `loc_2A816` indexes
`word_2A84C` by the pre-break frame*4: native0/2/4/6/8 select14/12/10/8/6
pieces and the matching high-launch velocity suffix. The small frameA→B branch
uses the four-entry `word_2A884`. Alternate-plane and signed-subtype admission
remain unchanged.

Rejected setup: the new declared five-tick AIZ rolling approaches never reached
the real Cork checkpoint; 97527's five placement failures,13980,39557 and28193
are setup failures, not runtime kill evidence. The latter two end at
P1 10451/1086, unrolled/pushing with GS0. The draft was removed. Actual admission
is supplied by the maintained controller-driven emerald chain: at1688 Tails
breaks the placed Cork and stationary Sonic is dropped. Independent constructed
bottom/top checkpoints are explicitly component oracles, not cold routes.

The first candidate6255 (15/2fail/10errors/0skip) exposed a separate fixture
error: mocked physical camera words400/480 lacked published X/Y-copy words,
so the post-camera renderer correctly retired offscreen fragments. Corrected
negative29909 on exact `d802` Cork is7/7fail/0errors/0skip at13:59:04 BST,
70s execution/0queue. It covers partial higher-pool admission, parent retirement
at five widths and absent static high-priority draw. The prior five role/count
negatives remain separate. Common graph57499 passes15/0/0/0 at14:09:58 BST,
70s execution/495s queue:12 new cases plus the existing CNZ exact-link3.

ICZ negative63818 is9/7fail/0errors/0skip at14:19:22 BST,26.054s execution/
468s queue; its two admission controls already pass. Corrected affected74672
passes607/0/0/0 at14:22:00 BST,74s execution/23s queue:
`TestCorkFloorNativeFragmentGraph,TestS3kCnzWaterLevelCorkFloorGraphRewind,TestS3kIcz2CorkFloorRegression,TestS3kCnzWaterHelpersHeadless,TestS3kSelfContainedTransientRewind,TestScalarOnlyCodecDeletion,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The new graph class contributes21 cases, including two-way recreation/two forward
cycles, checked pressure, native table branches and both roles' render edges at
320/352/400/528/800. These are declared component camera widths; actual ICZ's
retained short roll route is not a whole-act/configuration qualification.

Strict25714 uses the unchanged exact five-case86861 profile/selector above,
with observer output `target/cork-slot-occupancy`;5/1/0/0 at14:27:09 BST,
96s execution/97s queue. Original AIZ, initial HCZ and both prefixes still pass.
Chain segment6/8/11 raw field totals remain189/12,719/82,067: physics189/
12,604/69,393, animation0/115/12,674. First non-camera physics remains
3319 TailsX $31C1/$31CA,1583 TailsX $366C/$3674,1510 Y $7D6/$7DF;
the input68,801/load-generation9 stop remains. The report does not expose a
fresh first-animation field for segment11; no new first-animation claim is made.
At1688, original SST35 and all11 siblings41..51 now match the native aux XY
births exactly. Allocation-presence observations, including existing reserved
slots, match at all preceding retained samples; the first mismatch moves
1688→2133, native-only6. This is not proof that every reserved native child dispatches.
Second-entry ring25/flash17 versus native18/22 remains.

Alternate10147 retains60 grouped spans, first25589 animation $13/$05,
1/1/0/0 at14:25:32 BST,34.046s execution/63s queue. Replacement guard30177
passes111/0/0/0 at14:30:04 BST,33.360s execution/0queue using the same four
90744 classes/profile above. Initial65122 ended130 while waiting, with no tests;
the wrapper's KeyboardInterrupt path also handles SIGTERM, and its sender is
unattributed. No live matching request existed before the sole replacement.
All commands retain Java25/surfaceless EGL and the verified absolute S3K ROM;
unit-only checks use lean, traces/domain checks their normal lanes. No broad or
release full-suite run occurred. Cork native pixels/audio, ICZ route/configuration
breadth and the unchanged strict frontiers remain separate obligations.


## Rhinobot prior-render waiter and native range phases

Source context: `6fc85aab8` plus the local Rhinobot candidate, tested runtime
source SHA256 `ad3f6534b265c8536d020e56b760fe334ad397a38480d6aaf409020112a7e8a8`.
After qualification only three inherited setup-reference comment strings changed
from the regular helper to `SetUp_ObjAttributesSlotted` (:178850–178896); noncomment
source and line counts are identical. Committed source SHA256 is `51d88e7f958a0ed30ba82e023fb28045249923b7edcd13f71c08b74f8c60711a`.
No movie, fixture, shared allocator, culling algorithm or clock changed.

The previous sampled allocation-presence frontier was returned AIZ segment2
row2133, native-only slot6. Its full native code is $85AD2, not a low-byte object
ID: the physical waiter appears at1051 at($2820,$0320), stays in that operation
through2133 and clears at2154. Native cameraY never falls below930 during
1051..2153, while the $20-high placeholder ends at832. The engine's same placed
Rhinobot began X-only patrol by1055 and retired early at2133.

`Obj_WaitOffscreen` / `loc_85AD2` (:180271–180303) installs a $20×$20 zero-piece
mapping and reads the preceding Render_Sprites sign bit. The scan uses copied
camera coordinates, inclusive left/top and exclusive right/bottom bounds
(:36340–36381); it sets the sign before rejecting Map_Offscreen's zero pieces.
The corrected object captures dispatch and prior-sign state, suppresses body
pixels while the placeholder is installed, and leaves its priority word0 until
Init loads $280/bucket5 and dimensions $14/$10. `loc_85B02` restores the saved
operation and returns before both range tails; Init owns the next dispatch.

The independent review identified a release edge which Y-only tests missed:
a prior visible sign followed by a large camera-X move still returns natively,
but inherited post-routine culling deleted the owner. The local owner now keeps
all three range phases distinct. An undrawn waiter takes `loc_85AFC`'s immediate
clear; release bypasses range; Init/active `Sprite_CheckDeleteTouchSlotted` /
`Go_Delete_SpriteSlotted` (:179047–179098) publishes status7/Delete_Current code
and frees the physical slot on the following dispatch. The existing semantic
respawn bridge clears the placement latch; captured pending retirement emits no
touch or body draw. Shared manager culling and ordinary allocation are unchanged.

Negative19716 on exact `6fc` runtime is8/8fail/0errors/0skip at14:43:04 BST,
24.910s execution/0queue: five vertical dormancy cases plus initial/unpublished
phase and premature body drawing. Prior21336 was a snapshot-helper draft
compilation error, not behavioral evidence. First candidate77822 passes596/
0/0/0 at14:49:53 BST,70s execution/198s queue. Additional exact header/release
negative87004 is3/2fail/0errors/0skip at14:52:08 BST,24.439s execution/13s queue;
immediate undrawn-waiter deletion is its passing control.

Final affected8226 is599/0/0/0 at14:55:26 BST,76s execution/0queue. Selection:
`TestRhinobotNativeWaitGraph,TestRhinobotBadnikInstance,TestS3kSelfContainedTransientRewind,TestScalarOnlyCodecDeletion,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The new class contributes20 component cases: the five declared camera widths
320/352/400/528/800, literal render edges and both placement flips, empty-map
body suppression, phase/header admission, immediate versus delayed deletion,
and two-way recreation/two forward cycles including the 45-frame post-delete
window. These are component camera oracles, not full preset/donor/roster or
registered-world route certification. All four mandatory startup consumers pass.
Commands retain Java25, surfaceless EGL and original absolute S3K ROM; the exact
pure negative selectors use lean, the affected/domain run its normal lane.

Canonical82836 is5/1fail/0errors/0skip at15:00:50 BST,98s execution/78s queue,
using the same five-case25714 selector/profile, with output
`target/rhinobot-wait-slot-occupancy`. Original AIZ, initial HCZ and both prefixes
remain green. Chain segment6/8/11 raw totals stay189/12,719/82,067: physics189/
12,604/69,393, animation0/115/12,674; first non-camera physics stays3319 P2X
$31C1/$31CA,1583 P2X $366C/$3674,1510 Y $7D6/$7DF. Segment11 remains incomplete
at input68,801/load-generation9. Alternate69538 is1/1fail/0errors/0skip at
14:59:05 BST,35.067s execution/36s queue:60 grouped spans, first25589 animation
$13/$05. No raw/grouped definition or first-animation measurement changed.
Guards41681 passes111/0/0/0 at14:58:29 BST,35.089s execution/0queue, same four
30177 source/rewind/timing classes/profile. No broad or full-suite run occurred.

The read-only slot observer shows engine6 exactly($2820,$0320) at1051,1055,
1688 and2133, then absent at2154, matching the actual native waiter lifecycle.
Sampled allocation-presence, including reserved keys, advances2133→2897:
native-only20, last preceding retained sample2881. This is neither whole native
child dispatch nor strict trace closure. Ring25/flash17 versus native18/22
remains. Earlier native/engine Monkey/projectile/HCZ images stay separately
source-bounded; this slice adds no native pixel/audio qualification. Rhinobot
charge/brake children, slotted art/DPLC pressure and the inherited dash-timeout
fallback remain separate native-conformance limits, not claims from these tests.


## AIZ barrel flare raw lifetime and physical parent3

Source context: `8f7ec20a1` plus local flare SHA256
`2d292933cffc459cb0a6e3e79ebc9e21fe7985c606c38096c5d57bffda742eb3`,
barrel `5ebe04009423b8001b586a7cc33538c36f01e5c4f90c6c95a83194b6738be343`
and owning policy `fdb281a21a362102e494d69be4d18da5d04c7ce1a1a8258822dc28655374210b`.
New component test hash `df8337b7e26140da076fcd89b1225e087bf88b40687f1e30a52bb39eea40d265`;
updated actual-ROM napalm test `cb13b075166ffef388d2610833f6084cd7e55d62648f52d3945a7905efdf0d87`.

The owning allocation ledger is returned AIZ **segment2**, separately from the
first strict physics reds in segments6/8/11. Original-controller row2881 creates
flare17/shot18 in both engines. Native keeps flare17 until Go_Delete at2898 and
physical clear2899, so barrel1 at2897 allocates19/20. The old Java sequence freed
17 early, placing that next pair in17/19 and leaving20 absent. The existing $10
barrel stagger is therefore not evidence for a fitted fire-delay correction.

`AIZMiniboss_BarrelShotFlare` (:137029–137047) refreshes physical parent3, dispatches
its routine, then Draw_Sprite. Init publishes frame7/priority$200/dimensions8 and
returns without animation. The exact13 ROM bytes at$6913F match
`AniRaw_AIZMiniboss_BarrelShotFlare` (:137954):7/1,7/1,8/1,9/3,A/3,B/3,F4.
The existing ROM-backed raw interpreter applies `Animate_RawMultiDelay`
(:177563–177617): cursor0 skips the first pair on the first animation call, signed
byte underflow advances, and F4 invokes Go_Delete_Sprite. That marker still draws
B in its wrapper on birth+17; Delete_Current clears the following dispatch.
The local wrapper has no coarse-X deletion tail, so shared manager culling is
locally bypassed without changing the manager or checked allocation.

`Refresh_ChildPositionAdjusted` (:177300–177332) reads the current numeric SST,
not the original parent object, copies lower-two render bits and negates the
signed offsets. The flare now captures the physical parent3 slot; a pure
ctx.spawn shell restores the birth metadata, raw cursor and numeric link.
The barrel's existing refresh stores its copied bits/signed Y offset. Empty
slots provide genuinely cleared XY/flags. Live barrel/flare projections and
existing NativeSstStateSource provide only consumed header data; an unknown
nonempty owner fails before any position/animation mutation. No fake full-header
projection or unknown zero default was added. Arbitrary pressure residue remains
unsupported, not certified by the literal recycled-header vector.

Draft99161/52600 terminated at test compilation, with no behavioral credit.
Draft49556 ran19 setup errors because its fixed placement attempted reserved
player SST0..3; dynamic admission correctly rejected it. Corrected negative72464
on8f completes19/13fail/0errors/0skip at15:38:30 BST,26.753s execution/36s queue.
First candidate84527 is619/5fail/0errors/0skip at15:42:09 BST,73s/20s: three live
flip vectors still had an idle FrozenBoss without its native dispatch stamp;
two actual-ROM consumer helpers still reflected removed anchor/cursor fields.
The initial five moving passes were also idle-barrel controls, not proof that
phase-two restoration compensates wrong birth metadata.

The corrected matched nine-vector negative29122 restores only the exact8f
flare/barrel/policy runtime, retaining declared test inputs. It completes9/
8fail/0errors/0skip at15:48:47 BST,65s execution/144s queue: all five genuinely
moving cases lose flare birth metadata (12320/816→nearest-barrel12329/820), while
flip1/2/3 fail and flip0 passes. Exact candidate hashes were restored and the
three temporary source backups removed. Consumer helpers now resolve numeric
parent3 to the actual live owner identity and compare initialized/delete,
mapping, cursor and timer state, without filtering comparisons.

Final affected25518 is619/0/0/0 at15:54:05 BST,71s execution/148s queue:
`TestAizMinibossFlareNativeGraph,TestS3kAizMinibossGraphRewind,TestAizMinibossNapalmRoute,TestAizMinibossNapalmProductionRoute,TestAizMinibossBarrelShotChild,TestSecondStrikeBoss,TestS3kSelfContainedTransientRewind,TestScalarOnlyCodecDeletion,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
The19 new component cases cover literal INIT/raw/deletion/header/two-pair
allocation, five declared camera widths320/352/400/528/800, both-way recreation
and two45-frame forward cycles, live/cleared/recycled parent3, all flip bits and
unknown-header rejection. These are component cameras, not full host preset,
donor/roster/registered-world route certification. Actual AIZ2 napalm production
consumers, SecondStrike and all four mandatory startup consumers pass.

Canonical40282 uses the same25714 trace selector/profile plus documented
`OGGF_SLOT_PROBE=1 OGGF_SLOT_PROBE_FULL=1 OGGF_SLOT_PROBE_OUT=target/flare-native-slot-occupancy`.
A misspelled observer-only request54825 was canceled while waiting, terminal130,
no tests; only the corrected request ran. Canonical40282 completes5/1fail/
0errors/0skip at15:58:37 BST,96s execution/29s queue: original AIZ, initial HCZ
and two prefixes green. Segment6/8/11 raw totals remain189/12,719/82,067;
physics189/12,604/69,393 and animation0/115/12,674. First non-camera physics
remains3319 P2X$31C1/$31CA,1583 P2X$366C/$3674,1510 Y$7D6/$7DF. Segment11 still
loses ownership before closure at input68,801/load-generation9. No additional
first-animation field is exposed by these chain summaries.

Alternate34650 completes1/1fail/0errors/0skip at15:56:25 BST,34.901s/26s:60
grouped spans (25physics/35animation), first25589 animation$13/$05; first physics
25590 tails_x_sub$7800/$6C00. Guards91291 passes111/0/0/0 at15:56:59 BST,
33.758s/62s, the same four source/rewind/timing classes/profile as41681. All
commands retain Java25, exact surfaceless EGL and the absolute verified original
S3K ROM. No broad/full-suite sweep, controller change or comparison hydration.

The segment2 observer now shows actual flare/shot19/20 at2897. Including reserved
keys, sampled allocation-presence advances2897→2910, native-only17/21; preceding
sample2898. That following callback's allocation remains a separate owner.
Shot18 is still born at engineY812 versus native816: native shot Init draws/RTS
before movement (:137065–137085), while current Java moves on its birth dispatch.
These are explicit next targets, not a whole native graph or strict trace pass.
Existing inspected native/engine images remain source-bounded to their recorded
builds; this slice adds no rendered-pixel, SAT/priority or audio certification.


## AIZ shot stationary INIT and cleared raw state

Source6932189b9 plus shot SHA25621077a13d62832b2bc4a6e6cdfea05fe950b35858b17e557464c6cbb3c2116dd;
new TestAizMinibossShotNativeInit SHA2565d184aa7ab0cdc73125abdcc125bb5d74d065d1d28a515debf0fd3221743b30f.
`AIZMiniboss_BarrelShot_Init` (:137065–137085) loads attributes, SFX, script30,
callback34, Y velocity−$400 and word2E=$60, then Draw_Sprite/RTS without movement.
FallingShot_Init (:137474) likewise returns after the common Init. The following
Move_AnimateRaw_Wait (:179640) moves, animates, then calls Obj_Wait; expiry finishes
movement/animation before StartPause. Pause/Launch write routine, callback and
counter but preserve the same raw script/cursor/timer (:137093–137126).

Exact ROM$6914C bytes01,0C,0D,FC use Animate_RawNoSST (:177341), cleared cursor0/
timer0. Init drawsC, then ordinary movement drawsD,D,C,C. The old moving Init
consumed a primed timer and accidentally preserved this published sequence;
merely delaying movement without restoring raw state would introduce a second
error. The shot now captures the existing S3kRawAnimation.State, obtains its
four bytes through injected ROM access, and preserves that bank through Pause/
Launch. Native$60 wait holds zero on movement96, then movement97 underflows and
publishes pause8 on the same pass. No fire delay, allocator, shared clock or
fixture/controller change.

Initial negative64359 is10/9fail/0errors/0skip at16:18:10 BST (24.219s execution/
114s queue). Five forced cutscene-graph recreations additionally reveal the old
shot factory returns null because it searches only fight parents; this is a
separate required next slice. The common-Init fixture was explicitly changed to
a typed inert fight parent, retaining full recreation and all comparisons.
Matched14972 is10/4fail/0errors/0skip at16:24:28 BST (23.961s/94s): three mode
birth-Y failures816→812 and the$60 dispatch failure; five full-recreation/replay
controls and the old published C/D sequence pass. No missing child was waived.

Final46911 passes630/0/0/0 at16:34:25 BST (73s execution/317s queue), the existing
619 flare/actual napalm/scalar/SecondStrike/startup checks plus11 independent
shot cases. Selection adds TestAizMinibossShotNativeInit to25518 above, retaining
normal queue/Java25/EGL/absolute original ROM. The new component cases include
all three modes, literal raw/counter/callback ordering and five declared camera
widths320/352/400/528/800 with forced recreation and two45-entry cycles before/
after Init. These are manager components, not full donor/host/route products.

Canonical25467 uses the same five-case25714 trace profile/selection, observer
OGGF_SLOT_PROBE=1, OGGF_SLOT_PROBE_FULL=1 and output target/shot-init-native-slot-occupancy.
It completes5/1fail/0errors/0skip at16:47:30 BST,93s execution; queue duration is
unavailable in the retained tool output. Original AIZ/initial HCZ/two prefixes
pass. Chain6/8/11 raw189/12,719/82,067; physics189/12,604/69,393 and animation
0/115/12,674 remain unchanged. First non-camera3319 P2X$31C1/$31CA,1583 P2X
$366C/$3674,1510 Y$7D6/$7DF; segment11 ownership stops input68,801/loadgen9.
Alternate27675 is1/1fail/0errors/0skip at16:43:21 BST (33.895s/145s):60 grouped
spans, first25589 animation$13/$05. Guards19599 passes111/0/0/0 at16:45:56 BST
(31.939s execution; queue unavailable). Same guard/alternate commands as25714.
No broad/full run or hydration; unchanged strict reds remain open.

The actual segment2 engine shot18 now draws at birthY816 on row2881, matching
the native stationary Init. Subsequent sampled presence still first misses17/21
at2910, where the barrel's deferred callback is the next allocation owner. Pure
cutscene-shot recreation/exact links remains required next work. Existing image
provenance remains source-bounded; these checks add no rendered/audio parity.


## AIZ shot pure recreation and exact live links

Source `a06056a0b` plus shot SHA256 `bbb2459141faf1353959104c58f213d8d1cf3cb3b5b01a3e323e9bf046a74f6e`,
policy `e5c9f9af5619c4fcb902b3ee9b1025b129e130b1c2825758ce12d0b8b01ba206`,
TestAizMinibossShotNativeGraph `b56ca3eee67a043d23b5558ef7ca016ad9aedb24243f29e3060522f39cef3d38`.
The normal constructor and dispatch are unchanged. Native Launch reads its
firing barrel through parent3 (:137110); FallingShot reads the boss through that
barrel (:137450). Camera-relative shot XY cannot identify either live owner.
The former factory searched only AizMinibossInstance, dropping legitimate
cutscene shots, and selected the nearest barrel when a fight graph was present.

Matched negative99092 completes 9/9fail/0errors/0skip at 16:56:27 BST, 24.450s
execution/12s queue. Three pure ctx.spawn shells return null; all five moving
cutscene manager restorations lose shot slot9. Independent cutscene/fight graphs
change captured shot subtype2 to another barrel's4. No graph difference was
filtered or accepted. The pure shell now retains every ctx.spawn field without
live lookup, child allocation, SFX or animation advancement. Two explicit local
CAPTURED ObjectRef policies hydrate mutable parent/barrel fields in phase two,
along with existing mode/state/needsInitSfx/raw fields, before any dispatch.

Affected37849 passes 639/0/0/0 at 16:59:09 BST, 73s execution/23s queue, the
46911 selection plus TestAizMinibossShotNativeGraph. Its nine cases cover all
three shell modes, five declared camera widths320/352/400/528/800, moving
metadata, forced recreation and two45-entry forward cycles in both directions,
and independent exact two-parent/three-barrel identity. Existing actual AIZ2
napalm/SecondStrike/scalar and four mandatory startup consumers remain green.
Guards9839 passes 111/0/0/0 at 17:01:12 BST, 31.854s/20s with the existing four
-Pguards selectors, Java25/EGL environment. These are full manager components,
not whole registered-world/donor/host/cold-route product certification.

No ordinary gameplay trajectory changes in this factory-only slice; the source-
bounded canonical25467/alternate27675 strict observations remain the preceding
INIT slice's qualification, rather than a new strict pass. Following barrel
callback allocation at2910 remains the next ordinary-dispatch target. Exact
logical ObjectRefs do not certify native reads after numeric parent3 is cleared
or recycled. No new pixel/audio or full-pressure claim follows.


## AIZ barrel same-dispatch wait callback

Source829588298 plus barrel SHA256 `fa6e08895832f64e2ace94af1b8acaeb3c4b76a30f0dc1c47dce8710893d57ca`,
new TestAizMinibossBarrelWaitCallback `0a716f6b6cb2f330cc1d21017ffbb58973f28d15893336bdc1f1bfd996491f3f`.
WaitAfterShot (:137010) calls Obj_Wait (:177949), which decrements word2E and
jumps directly to callback34 on signed underflow. FireShot (:136996) publishes
$1C, decrements byte39 and performs checked AfterCurrent allocation on this same
dispatch. Only then does the WaitAfterShot signed-counter tail select Return
for the fourth shot's 0→$FF. Old BETWEEN_SHOTS only scheduled FIRING_CUTSCENE,
creating the pair one dispatch late and delaying its return tail.

The new ordinary-activation fixture declares only native parent StartSwing bit1,
then executes the real manager until its first burst; it never seeds barrel
phase/timer or assumes a fitted first-birth frame. Literal $1C holds zero for28
passes and underflows on29. Fourth-pair allocation precedes immediate Return.
First OPENING/FireLoop and array animation remain unchanged, so this qualifies
the steady wait/callback contract rather than all initial raw animation.

| Exact normal queue request | Terminal outcome | Timing BST |
| --- | --- | --- |
|23527 negative seven literal/graph cases|7/7fail/0errors/0skip|17:06:12;25.353s execution/100s queue|
|35308 affected639 plus new seven cases|646/0/0/0|17:09:12;71s/11s|
|35562 same canonical five-case trace selection|5/1fail/0errors/0skip|17:14:07;96s/78s|
|31635 same alternate trace-segments selection|1/1fail/0errors/0skip|17:12:02;35.323s/12s|
|70444 same four -Pguards selectors|111/0/0/0|17:17:33;32.483s/345s|

Affected selection adds TestAizMinibossBarrelWaitCallback to37849 above. Five
declared component cameras320/352/400/528/800 force recreation at second/fourth
callback boundaries, complete snapshot equality and two45-pass cycles in both
directions. Actual napalm/SecondStrike/scalar and mandatory startup consumers
pass. This is not full host/donor/roster/registered-world route certification.
All commands retain Java25/EGL/original absolute ROM and existing profiles;
canonical adds OGGF_SLOT_PROBE=1 OGGF_SLOT_PROBE_FULL=1 with output
target/barrel-callback-native-slot-occupancy. No controller, fixture, global
clock, allocator or hydration change; no broad/full sweep.

Original AIZ/initial HCZ/two prefixes remain green. Chain6/8/11 raw189/12,719/
82,067, physics189/12,604/69,393 and animation0/115/12,674 remain unchanged.
First non-camera3319 P2X$31C1/$31CA,1583 P2X$366C/$3674,1510 Y$7D6/$7DF;
input68,801/loadgen9 is still the incomplete stop. Alternate60 grouped spans
(25physics/35animation), first25589 animation$13/$05, first physics25590
P2Xsub$7800/$6C00. No strict frontier was closed or metric changed.

Segment2 pair17/21 now exists on2910. Sampled presence including reserved keys
advances2910→2977, native-only19, preceding retained2972. Low code byte$8A is
the post-Init Obj_Wait pointer, not a unique type identifier. The owning parent
swing-expiry table creates Obj_BossExplosionSpecial (:137926/176817), whereas
current onSwingComplete retains only a Java explosion helper without a physical
SST owner. That source/creation context is the next graph attribution, not a
full runtime-type certification from the low byte alone.

Two additional source-confirmed local contracts remain separate: FireLoop/
FireShot and SetShotPosition (:137688) share byte39, but Java cutsceneCounter/
positionCounter are split; after fourth fire native$FF+4→3 selects a different
first drop column than independent0+4→4. Counter==1 selects the native FallingShot
table (:137890), while current cutscene selects a common-shot mode rather than
the real FallingShot owner. No correction or qualification of these is claimed
by this callback commit. Prior image/audio/projection limits remain.


## AIZ barrel shared native byte39

Source a0b70f969 plus barrel SHA256 `d6f09b25e3b3e92e82547dbca16f49f4eed72a534de6786d33efd640050d4e76`,
new TestAizMinibossBarrelCounter39 `a858fe490f26b7f02a56c68fa7942bd2433f603b0d4f35f2a297c5f45ee1eb1f`,
updated callback test `9281cb2a53304ee74f2c070a7988e86c54770558a03a08e89aac3e348eb1af82`.
FireLoop/FireShot (:136991/136996) and SetShotPosition (:137688) own the same
byte39. The former initializes3 and subtracts1 before each pair; the latter adds4
with byte wrap, stores the result and masks$C for the drop table. Java's split
cutsceneCounter/positionCounter was a real alias error. Fourth fire leaves$FF,
so first Launch writes3/selects bank0, not independent0→4/bank4.

One existing unsigned positionCounter now holds both roles; FireShot subtracts
modulo256 and only the Wait signed test casts to byte. The duplicate captured
scalar is removed. The previous callback test now reads this byte and expects
$FF rather than Java -1, preserving all native allocation/closure assertions.
No public API, shared schema algorithm, clock, allocator or controller changes.

| Normal queue request | Terminal outcome | Timing BST |
| --- | --- | --- |
|76436 exact alias negative|9/9fail/0errors/0skip|17:25:46;23.443s execution/6s queue|
|93869 affected646 plus nine new vectors|655/0/0/0|17:30:09;71s/25s|
|27022 canonical five-case trace/slot observation|5/1fail/0errors/0skip|17:35:39;94s/80s|
|78687 alternate trace-segments|1/1fail/0errors/0skip|17:33:32;36.032s/11s|
|45245 four -Pguards classes|111/0/0/0|17:36:13;32.755s/174s|

Matched negative observes first drop X12148→12068 normal and12276→12316 flipped;
first fire-byte read2→0, fourth$FF→0 and launch3→4. Explicit$FE/$FF writes were
ignored by the separate signed Wait counter. Five replay setups likewise lacked
the pre-launch$FF. Positive tests retain literal normal/flipped columns, byte
wrap, both-role visibility and forced manager recreation with two45-pass cycles
at declared320/352/400/528/800 camera widths. Existing actual napalm/SecondStrike/
scalar/startup consumers pass. These are component contracts, not full host,
donor/team or registered-world route product certification.

Affected command adds TestAizMinibossBarrelCounter39 to35308. The three standard
canonical/alternate/guard commands retain Java25/EGL/original absolute ROM and
profiles; observer output is target/barrel-counter39-native-slot-occupancy.
Original AIZ/initial HCZ/two prefixes pass. Chain6/8/11 raw189/12,719/82,067;
physics189/12,604/69,393 and animation0/115/12,674 are unchanged, first non-camera
3319 P2X$31C1/$31CA,1583 P2X$366C/$3674,1510 Y$7D6/$7DF, with incomplete
input68,801/loadgen9. Alternate60 grouped spans remains first25589 animation
$13/$05. No metric, fixture, hydration or broad/full-suite change.

Sampled segment2 presence remains2977 native-only19, preceding retained2972.
This scalar slice repairs the native drop columns without claiming strict or
whole-object closure. Physical Special explosion ownership at that creation
boundary and the cutscene FallingShot branch remain required next owners;
prior rendered/audio and arbitrary numeric-pressure limits remain separate.


## AIZ physical Special explosion owner

Source6661b9b8b plus new owner SHA256 `28499bf571df30929fabbf8a595003ed78481137ce289e27a9fbf4e5e93ac5f0`,
parent `b57cc1ef6f31ee26499f4e543543ac9096abc67b922ce32015b4ab0bf7cebfa8`,
policy `54e7157a3864495cbdb608aea218e6cdc88fc74a66e98f2532c76aec55ebd7d5`,
new TestAizMinibossSpecialExplosionOwner `8e17c1ab43840327a3fffa9adca12ed025f4fca15444d5fc8e10d271390e7def`.
The existing parent swing-expiry callback creates ChildObjDat_AIZMinibossCutscene_Explosion
(:136870/:137926) through checked AfterCurrent allocation. The former plain Java
helper had no SST and used its own deferred state. The new owner captures its
position, Init/operation state, word wait and byte count in its own physical slot;
pure ctx.spawn recreation creates no children, sound or random work.

Obj_BossExplosionSpecial (:176817) writes physical camera+160/+112, word2E=2 and
subtype2; Obj_CreateBossExplosion (:176661) selects CreateBossExp02 count$28,
ranges$80, routine-set$18, then tail-jumps Obj_Wait. Init consumes2→1; next pass
holds0; following underflow uses Obj_BossExpControl2. The finite controller emits
39 accepted attempts and marks Go_Delete on the40th callback at birth+119; its
slot clears next dispatch+120. Failed child attempts consume the same count/wait
without RNG or child-init sound. Checked AfterCurrent allocation precedes
Random_Number; sub_83E90 uses low16 for X and SWAP/high16 for Y, with word wrap.
The existing ROM-backed S3kBossExplosionChild owns same-pass native-init SFX,
raw animation and next-dispatch retirement. Its exact15-byte ROM bank at$83FCC
was read and verified; no disassembly/runtime fallback art or raw data added.

The owner is independent of parent liveness, has no range/draw/touch gate and
uses the original320/2 centre even at wider declared cameras. The AIZ2 reload
clears this one-shot SST just like its parent; the local carry callback retires
it, with restoration around the clear. Removing the old parent helper also
removes its obsolete central DEFERRED policy. Other helper consumers, shared
allocation/RNG algorithms, Child implementation and art provider are untouched.

| Normal queue request | Terminal outcome | Timing BST |
| --- | --- | --- |
|16515 actual callback/physical owner negative|13/12fail/0errors/0skip; full initial-pool control passes|17:54:37;23.928s execution/276s queue|
|38085 first real-SST candidate|13/0/0/0|18:03:58;70s/72s|
|27598 affected655 plus16 new and5 existing cutscene/seamless consumers|676/0/0/0|18:08:40;31.355s/20s|
|30405 canonical five-case trace/slot observation|5/1fail/0errors/0skip|18:22:12;97s/497s|
|50339 alternate trace-segments|1/1fail/0errors/0skip|18:22:48;35.518s/595s|
|11033 four -Pguards classes|111/0/0/0|18:23:21;31.624s/queue unavailable in retained output|

Sixteen local cases cover actual parent callback admission at declared cameras
320/352/400/528/800, physical-versus-copy origin, first native wait/high-word RNG,
child-init sound, initial/partial pressure, independent lifetime/terminal marker,
pure metadata, act-clear restoration and two45-pass cycles with manager plus
real GameRng adapters. The callback is a declared component boundary, not a cold
boss route or whole production registry. Existing helper-count test is now
explicitly an independent legacy-helper control; it no longer implies that
helper-only counts qualify AIZ's actual physical owner. Existing cutscene music/
fade/seamless, actual napalm/SecondStrike/scalar and startup consumers pass.

Affected command adds TestAizMinibossSpecialExplosionOwner,TestAizMinibossCutsceneInstance,
TestAizMinibossSeamlessTransition to93869. The three standard profiles/selectors
retain Java25/EGL/original absolute ROM; slot output is target/special-explosion-native-slot-occupancy.
Original AIZ/initial HCZ/two prefixes pass. Chain6/8/11 raw189/12,719/82,067,
physics189/12,604/69,393, animation0/115/12,674 remain unchanged. First non-camera
3319 P2X$31C1/$31CA,1583 P2X$366C/$3674,1510 Y$7D6/$7DF; incomplete source stop
input68,801/loadgen9. Alternate60 spans remains first25589 animation$13/$05.
No fixture, metric, global clock, hydration or broad/full-suite change.

Native19 is now present at2977. Sampled segment2 presence advances2977→3067,
native-only21, preceding retained3066. The same row has native low-code$88
BossExplosionHitbox context versus engine ImpactFlame children39..45, and a
retained native21 at low-code$B6 where engine's common-shot mode has cleared.
Low code bytes are not unique type proof; the source FallingShot floor/Go_Delete
and creation-table context supplies this next attribution. Real cutscene
FallingShot admission/collision/lifetime remains required next work. No new
rendered-pixel, SAT/priority, PCM or arbitrary reused-header pressure certification.


## AIZ real cutscene FallingShot and copied explosion lifecycle

Source1db1ebd89 plus four local native owners; final qualified runtime SHA256:
AizMinibossFlameBarrelChild.java:563a9cb8fb7de20b6755e70c7097d3550d0e81ab4502b8e9037cd1f9374a5931, AizMinibossNapalmProjectile.java:014e4a0163436934c4cfd7f038de53fd6f7337227c07e3e8ea5df04e415493b1, AizMinibossNapalmExplosionChild.java:23fe8cb6f3473cdef8505fde3553d75e845e7f6632fcfa61e9ac1bbc5e7141a7, AizMinibossBarrelShotChild.java:3cbbf7d045aa281924757d617a5cd3448998e16b43454c2cb424b7a55a07a8bf.

Native ChildObjDat_690A8 (:137890) selects Flare/FallingShot with second-child
subtype02. The cutscene now uses the existing real FallingShot owner rather
than the advanced common-shot simulation. Common second children also retain
subtype02 independently of their barrel's0/2/4 position-table subtype, matching
CreateChild1_Normal (:176923). FallingShot_Init (:137474) reads the actual barrel
operation and clears its collision byte for the cutscene; the fight keeps98.
BossExplosionHitbox_Init (:137549) copies that byte into seven independently
staggered children. They retain art priority0/bucket1 and have no generic
explosion-child Init sound. No shared explosion factory/art/SFX reuse added.

FallingShot dispatches its own routine before the grandparent-status7 branch
(:137450). Floor impact (:137529) creates the seven actual Hitboxes, marks its
own Go_Delete and still reaches AddList/Draw when the grandparent is live. The
own marker is distinct from that grandparent gate. Hitbox Animate (:137580)
also reaches Draw_And_Touch after its F4 callback; its final mapping5 and97
publication stay live until the following Delete_Current_Sprite dispatch
(:36108/:179136). Existing immediate-destruction/21-harmful-publication oracles
were incorrect: native has21 mapping holds plus one callback touch/draw pass.

Both objects read their real ROM raw banks (6914C/83FCC), capture byte cursor/
timer/terminal state, and recreate from the entire ctx.spawn shell without
live lookup or work. Existing exact live parent/barrel links remain CAPTURED;
no shared schema/API, clock or allocator change. Numeric recycled-parent
pressure remains unqualified. Unknown pressure must not be inferred from these
logical live-reference vectors. Pause/Drop retain the same raw bank.

| Normal queue request | Terminal result | Timing BST |
| --- | --- | --- |
|38406 matched literal/caller/factory negative|14/8fail/0errors/0skip; six graph/world controls pass|20:18:08;25.197s execution/913s queue|
|97580 first candidate|test compilation error: missing assertFalse import; no tests executed|20:23:42;64s/25s|
|49960 affected676 plus16 new and7 existing standalone consumers|699/7fail/0errors/0skip; all failures are old common-only callback family predicates|20:25:18;31.928s/0s|
|84889 corrected callback-only selectors, runtime unchanged|7/0/0/0|20:27:49;24.286s/0s|
|31590 alternate trace-segments|1/1fail/0errors/0skip, retained60 spans|20:29:31;35.301s/0s|
|85181 four -Pguards selectors|111/0/0/0|20:30:05;33.374s/36s|
|49498 canonical five-case trace/slot observation|5/1fail/0errors/0skip; four controls pass|20:31:43;96s/70s|

The unchanged692 passing identities from49960 and corrected7 from84889 qualify
all699 affected identities, not one all-green699 invocation. Source/tests were
frozen for each actual invocation; no source changed after the missing import
except that one callback test's family predicate/independent ROM unit banks.
Sixteen new cases cover caller selection, both barrel roles, clear/retained raw
state, parent-tail ordering, collision copy, seven subtype windows, F4/physical
clear, pure complete metadata, and two45-pass forced recreation cycles at five
component cameras320/352/400/528/800. One separately declared loaded-AIZ world
uses the complete production registry with no key filtering, forcing graph
recreation around birth/rise and impact/retirement for two45-pass cycles. This
is local world evidence, not cold-route or full viewport/donor/team breadth.
The existing actual-ROM Knuckles napalm route4, standalone7, live graph/Second
Strike/scalar and all four startup consumers pass. Callback family tests still
assert exact wait/counter/admission/tail frames; they now count the real native
common/Falling families and assert the second callback's literal Falling child.

Affected selector adds TestAizMinibossFallingShotNativeGraph and
TestAizMinibossNapalmProjectile to27598;84889 selects only
TestAizMinibossBarrelWaitCallback. Standard canonical/alternate/guard profiles
and exact selectors remain those of30405/50339/11033 with Java25/EGL/absolute
original S3K ROM. Slot observer directory is target/falling-shot-native-slot-occupancy.

Chain6/8/11 raw189/12,719/82,067, physics189/12,604/69,393, animation0/115/12,674
remain unchanged. First non-camera3319 P2X31C1/31CA,1583 P2X366C/3674,
1510 Y7D6/7DF; source closure still stops at input68,801/loadgen9. Alternate60
spans is25physics/35animation, first25589 animation13/05, first physics25590
Tails subX7800/6C00. Original AIZ/initial HCZ/two prefixes remain green.

Sampled segment2 presence advances3067→3084 (native-only18; preceding3081).
At3067 engine21 is the real retiring FallingShot and39..45 are the actual
Hitboxes, matching the native96→B6/88 context. At3081 native18 is common-shot
code44 and engine18 is SIMPLE at12164/1080; at3084 native18 is B6 while engine18
is absent, and both are absent at3087. This independently identifies the next
common-shot final Obj_Wait→Go_Delete occupancy owner; low code bytes alone do
not certify complete type identity. No fixture/metric/hydration change, broad
run or new rendered/SAT/PCM parity claim. Native screenshot scope remains the
previously inspected source-bounded AIZ/HCZ spot pairs.


## AIZ common SIMPLE shot final native wait and physical retirement

Sourcecf19f8506 plus one local CommonShot runtime candidate, SHA256
fa21d0070920161b06410ea6d87330177f6cf488030660e44467d607678764d4. AIZMiniboss_BarrelShot_MoveWait (:137079) tail-calls
Move_AnimateRaw_Wait then Draw_Sprite. Launch (:137091) writes callbackGo_Delete
and word2E60. The last Move/Animate runs on signed underflow and its code/status
marker still draws/occupies the SST; the following Delete_Current_Sprite clears
it without another movement/animation/Init sound. Neither MoveWait nor its
caller has a range/delete-touch tail. Removing the Java isOnScreen128 condition
is a local owner repair, with shared manager culling unchanged. Parent/numeric
pressure semantics and legacy advanced-mode geometry remain separate limits.

| Normal queue request | Terminal outcome | Timing BST |
| --- | --- | --- |
|74033 matched native terminal/graph/world negative|8/8fail/0errors/0skip|20:52:25;25.908s execution/0s queue|
|16779 independent actual+copy camera-Y departure negative|1/1fail/0errors/0skip at the first hidden MoveWait|20:56:13;25.459s/125s|
|51679 affected699 plus8 new vectors|707/0/0/0|21:04:49;72s/0s|
|91214 canonical five-case trace/slots|5/1fail/0errors/0skip; four controls pass|21:07:37;96s/0s|
|36977 alternate trace-segments|1/1fail/0errors/0skip; unchanged60 spans|21:08:37;37.111s/119s|
|47500 four -Pguards selectors|111/0/0/0|21:09:53;31.256s/201s|

The first screen setup changed copied Y only and therefore reproduced the final
marker failure rather than isolating the extra range branch. The corrected
single-method negative changes both actual/copy Y after Launch and fails on the
very first hidden update; no runtime/contact/clock fitting. Eight final cases
cover zero/underflow, retained final D/draw/position, next-pass clear/no movement,
that independent X-visible/Y-hidden control, five component cameras and forced
graph recreation, and a separately declared loaded-AIZ full registry. Both
registry scopes replay before/marked/released states for two45-pass cycles with
no snapshot filtering. This is local lifecycle evidence, not full cold-route/
viewport/donor/team or rendering/audio certification. All699 existing consumer
identities, including previous real Falling/ROM napalm/startup controls, pass
on the changed owner in one707green invocation.

Exact focused selector adds TestAizMinibossCommonShotRetirement to49960;
16779 selects only verticalCameraDepartureDoesNotOverrideTheNativeWaitOrCreateAnEarlyDelete.
Canonical/alternate/guard profiles/selectors remain those of49498/31590/85181,
Java25/EGL/absolute original ROM; slot output target/common-shot-native-slot-occupancy.
The original controller and V5 comparison-only contract are unchanged. Chain
6/8/11 raw189/12,719/82,067, physics189/12,604/69,393, animation0/115/12,674 retain
first3319 P2X31C1/31CA,1583 P2X366C/3674,1510 Y7D6/7DF. Incomplete source stop
input68,801/loadgen9 remains. Alternate60 spans25physics/35anim, first25589
animation13/05 and firstphysics25590TailsSub7800/6C00. Four controls stay green.

Actual source18 now agrees at3084's native B6 marker; both clear by3087. Sampled
presence advances3084→3244, preceding3209: native-only24..37 and engine-only4.
Engine records new AIZ2 scenery4..20/pathswap21/Cork22/ring23; the native low
code bytes90/8C/5A do not uniquely identify actor types or prove that old actors
remain. This is a transition-context frontier, not a diagnosed clear/clock bug.
Resolve full native pointers and prepared/published scene boundaries before a
correction. Ending's paired KosM bridge does not bind the AIZ fireOverlay caller:
it currently claims a complete timed archive into seamless prepared art without
a physical journal entry. That workflow needs an actual native witness, not a
guessed timing or state import. No unchanged trace rerun before a relevant
composed dependency; no broad/full run, fixture/hydration/shared clock change.


## Snapshot comparator contract diagnosis — 2026-10-14

The declared 800px AIZ receiving-scene replay on `1255286c` plus the pending
native handoff candidate exposed a comparator inconsistency, not a placed-spawn
or native-slot mismatch. Every placed spawn resource retained Java identity;
both captures had29 placed and13 dynamic entries, with no duplicate dynamic ID
and no child reservation. The only differing equality leaf was a compact
`RewindObjectStateBlob`; its opaque arrays were content-equal while the blob's
ordinary equality retained opaque Java identity. The diagnostic walker already
used deep content there. Separately, its private-record walker omitted explicit
access and swallowed reflection failures, producing empty diffs for actual
cross-package private-record mismatches.

Independent negative59563 completed22:12:57 BST,24.130s Maven/0s queue:
6 tests,2 failures,0 errors/skips, exposing equal top-level-array and opaque-blob
false positives. Its same-package private-record premise was insufficient.
After moving that owned test to a different package, only the three private
mismatch methods ran in59072:3 failures,0 errors/skips,23.413s Maven/0s queue,
completed22:14:19 BST. Those are five distinct negative contracts across two
invocations, not a pristine five-case invocation. The throwing-accessor control
passed before repair.

`RewindSnapshotDiff` now uses the same complete captured-content comparison for
arrays and blobs, explicitly accesses private record components, and throws if
inspection or mismatch diagnosis cannot complete. It preserves schema,type,raw
scalar bytes,all opaque values and every nested array member; no registry key is
filtered. Pure focused77331 completed22:16:10 BST,1:04 Maven/0s queue,lean lane:
14 tests,0 failures/errors/skips (new private-record7,terrain1,pattern2,dynamic
identity4). Command: `python3 tools/testing/maven_queue.py --lean -Dmse=off
'-Dtest=TestRewindSnapshotDiffPrivateRecords,TestRewindSnapshotDiffTerrain,TestRewindSnapshotDiffPatterns,TestRewindSnapshotDiffDynamicIdentity' test`,
JDK25 and the normal surfaceless-EGL environment. The gameplay handoff remains
an uncommitted, separately qualifying candidate; this helper gate gives it no
new native or strict-trace credit. Six omitted captured object-manager fields
are a separate authorized comparator slice.


## Complete captured ObjectManager comparison — 2026-10-14

The existing custom comparator omitted six captured gameplay channels:
solidContactState, planeSwitchers, touchResponseOverlap, collisionResponseState,
dynamicObjectIdCounter and currentExecSlot. ObjectManager restores these values
and uses them for contact admission, native list read/build selection, identity
minting and after-current allocation. Independent mismatch vectors58765 all
failed before repair:6 tests/6 failures/0 errors/skips,22:22:15 BST,25.838s Maven,
0s queue, lean. The comparator now requires every one of those channels in both
equality and diagnostic traversal. bucketsDirty remains excluded because restore
explicitly invalidates the render buckets; peakSlotCount remains excluded because
SlotAllocator only exposes it as telemetry and does not consult it for allocation.
A separate positive changes only those two derived/telemetry values.

First positive28642 passed21/0/0/0 at22:25:22 BST,1:07 Maven/0s queue. An additional
independent diagnostic control27602 then failed1/1/0/0 at22:29:46 BST,24.676s/0s:
equal boxed cursor/counter200 were printed alongside a genuine contact mismatch.
Their diagnostic admission now checks primitive values first. This does not
change gameplay or the snapshot/API shape; native AIZ handoff remains a separate
unstaged candidate.

Final focused69530 passes22/0/0/0 at22:31:31 BST,1:07 Maven/0s queue, lean:
TestRewindSnapshotDiffPrivateRecords7, Terrain1, Patterns2 and DynamicIdentity12.
The selector/environment are identical to77331 above; the two additional pure
controls establish derived-cache/telemetry exclusion and correct equal-counter
diagnostics. No broader gameplay or strict-trace claim follows from this gate.


## Complete Palette snapshot leaf — 2026-10-14

Route's actual placed-stage3/352px HPZ witness3988 completed21:27:31 UTC with
all four original/restored Palette identities distinct and all192 raw RGB bytes
identical. Each PaletteSnapshot capture and restore deep-copies its palettes;
Palette's only instance field is its sixteen Color objects, each containing
exactly byte r,g,b. Palette.dataEquals compares all48 raw bytes. The previous
generic leaf comparison treated those defensive copies as state differences.
This is a verification false positive, not an HPZ palette runtime correction.

Independent pure5896 completed22:33:13 BST,23.882s Maven/0s queue, lean:
50 tests/49 failures/0 errors/skips. The equal-copy positive failed; all48 literal
raw-component changes already failed equality, but the diagnostic reported only
Java identity instead of the changed component. Missing palette, array length
and unrelated phase controls passed. The concrete Palette leaf now uses its
complete dataEquals contract and diagnoses each colors[index].r/g/b raw byte.
It does not normalize to Genesis3-bit channels, filter any key, or treat Palette
subclasses with additional state as this concrete leaf.

Final pure26472 passes72/0/0/0 at22:34:41 BST,1:09 Maven/0s queue, lean: existing
private-record7/terrain1/pattern2/dynamic-identity12 plus Palette50. Exact selector
adds TestRewindSnapshotDiffPalettes to69530 above with the same JDK25/EGL launch.
Actual HPZ/ending consumer qualification is owned by those lanes and remains
separate from this pure helper result. Native AIZ handoff is still unstaged.


## Native AIZ retained scene and transition-floor handoff — 2026-10-14

Source7001b1eef plus this local candidate; the qualified native handoff is kept
separate from its unresolved results/core composition. AIZ1BGE_Finish
(sonic3k.asm:104727–104775) calls Load_Level, which reads the layout header rather
than clearing Object_RAM. Offset_SomeObjectsDuringTransition(:104154–104182)
returns without a scan normally; Super_Tails_flag admits only the four physical
Invincibility_stars at globalSST102..105. The existing ALL_LIVE_SST and
ROM_WORLD_OFFSET_RANGE policies now express this retained graph and empty normal
scan. The builder admits an actual empty range; negative/inverted ranges still
fail. No allocator, shared clock, snapshot/API shape or fixture is changed.
The consolidated SuperTails auxiliary still lacks the native four-slot graph;
this request's source branch does not certify that unimplemented breadth.

Full native pointers in the committed segment2 auxiliary resolve5=68690,
6..11=68E8C,12=686E8,13=68720 and14..16=6872C before and after row3244. They are the
same cutscene/debris/body/arm/barrel owners, not new AIZ2 scenery. At3283 all5..16
hold1ABB6 Go_Delete markers; by3332 they clear/reuse. The old handoff explicitly
removed this cutscene graph and carried only persistent objects, which discarded
its nonpersistent debris. That incorrect clear and offset premise is removed.
Debris now has a pure ctx.spawn shell and captured exact parent3; native Wait
and Move own retirement rather than an engine screen cutoff. The moving callback
only replaces code and returns; Draw is admitted on the next actual Move pass.

AIZMinibossCutscene_End(:136896–136901) clears Boss_flag, restores level music,
loads the actual PLC_Monitors and jumps Go_Delete_Sprite_2(:179146–179149), which
changes code and sets38bit4. Its child Draw tails(:178087–178109) observe that bit,
mark their own code and return without drawing. Physical clear is next dispatch.
The cutscene, body, arm, barrels and debris capture this marker state; the boss's
original camera-reset branch was not native and is removed. Fightable91's
separate ordinary lifetime is retained.

ALL_LIVE_SST exposed the transition floor's previous omission. Its native Act2
branch(:104777–104790) writes X7FFF/Delete_Current_Sprite, then still runs
SolidObjectTop with statusbit7. Java immediately destroyed it, and its missing
existing suppressesObjectEdgeBalance semantic made Sonic balance against the old
world-space support after his coordinate rebase. Sonic_Move's tst.b/bmi bypass
at22458–22460 is the exact owner. The floor now preserves currentX and a captured
pending-clear marker, publishes the changed code highword, suppresses the native
edge branch, and keeps its no-range-tail contract. No shared contact/physics
carve-out is introduced.

| Normal queue request | Observed result | BST execution / queue |
| --- | --- | --- |
|93361 initial literal candidate|3/2fail/1error: empty range and early parent clear; third was void-reflection setup|21:47:17;24.622s/2s|
|28516 declared actual handoff negative|1/1fail/0errors/skips, slot5 lost|21:49:35;24.912s/78s|
|33995 independent Debris draw negative|1/1fail/0errors/skips|21:59:24;21.632s/0s|
|47726 complete helper/new native case|8/0/0/0 across production5widths|22:36:17;26.974s/0s|
|74544 native families/event/carry/recreation/startup|819/2fail/0errors/skips, only stale Napalm marker inventory|22:37:38;36.218s/0s|
|79594 two corrected inventory predicates, runtime unchanged|2/0/0/0|22:39:15;25.110s/37s|
|58655 before floor correction|5/3fail/0errors/skips; originalAIZ/prefix balance regressions|22:41:27;95s/79s|
|7015 before floor correction|66 grouped spans, first6302 animation05/0C|22:39:51;34.606s/43s|
|55671 initial source/rewind/timing guards|111/0/0/0|22:43:16;31.947s/250s|
|14257 independent floor literals/normal-manager graph negative|6/6fail/0errors/skips|22:46:39;26.389s/0s|
|7345 corrected floor and complete affected selection|827/0/0/0|22:49:12;80s/40s|
|3055 five strengthened actual floor+rider world cases|5/0/0/0|22:51:35;24.906s/0s|
|50751 canonical controls/chain with corrected floor|5/2fail/0errors/skips; originalAIZ/initialHCZ/first prefix pass|22:53:24;93s/0s|
|39702 alternate exact trace-segments|1/1fail/0errors/skips; original60 spans restored|22:53:59;34.379s/93s|
|84112 final source/rewind/timing guards|111/0/0/0|22:54:35;35.158s/127s|

Earlier19583 had3 setup errors;75339 was canceled before execution. Initial
10045's byte-array diagnostic and the800px compact-blob equality issue were
verification false positives, closed separately by c3d/d8/7001. Those do not
count as native runtime negatives. The3055 draft52232 failed compilation only
because its test named the two NativePositionOps helpers incorrectly; corrected
helper names did not change runtime.817 passing identities plus corrected2 from
74544/79594 qualified the earlier native candidate;7345 is the one827 green
invocation. Its selection adds TestAizCutsceneNativeTransition,
TestAizTransitionFloorObjectInstance, TestSpawnRewindRecreatableCleanup,
TestSonic3kAIZEvents, TestLevelSeamlessTransitionExecutor and
TestSeamlessCarryExcludesBossChildren to51679's707 selection. The five actual
receiving worlds additionally include floor17 and literal stopped support; all
registry keys replay for two45-pass cycles in both directions, including marker
and physical-clear states. This is a declared ROM-loaded scene, not cold input,
full donor/team or rendering/PCM certification. The old Napalm marker list is
reconciled with cf19's already-qualified pure ctx.spawn recreation, not weakened.

Profiles, selectors and Java25/EGL/original absolute ROM remain those of91214/
36977/47500; observer target/aiz-handoff-floor-native-slot-occupancy. Alternate
returns the same60 grouped25physics/35animation spans: first25589 animation13/05,
firstphysics25590 TailsSub7800/6C00. Sampled slot-presence frontier advances
3244→3887, preceding3858, native-only52/58 and no engine-only key. This counts
physical keys, not code-type parity; low code bytes are not object IDs.

Canonical remaining fields are not green: segment4 raw51, first noncamera7589
rings111/0; segment8 raw1765, first6000 P2X4997/4996. Former segment6 raw189 is now0,
but full chain still stops input68801/loadgeneration9 before source closure.
The second-return prefix inherits that new segment4 results/ring failure. Native
signpost25/stub26 still exist at7589; the engine's results owner5 disappeared by
7566 and reset the new world's rings earlier. OriginalAIZ and initialHCZ remain
green after the local floor correction. This candidate is a LOCAL scoped commit,
not an integration/overall-green claim. Root explicitly requires coherent
qualified da27f4f3d adoption before attributing that seam: df8 pairs the physical
RESULTS producer to real timed direct children/FIFO/VInt publication and may
change actual creation/wait readiness. No counter fit is admitted to hide it.

Runtime source SHA256 at the final focused/strict candidate:
- Sonic3kAIZEvents.java: `a7ead67bd378b9282a363e377e67704d46b4e8f2c2530e62245fb271f85f1d2b`
- AizMinibossCutsceneInstance.java: `5658c543b769b59d8beb7ca70b3e2eaf2ed6de30c5965b70475892824dbb0722`
- AizMinibossBodyChild.java: `575f1b11fc08439f9a3adaa1ca29aff381189c95c8119d651162e53aace51307`
- AizMinibossArmChild.java: `e3f8193436fab0dd1b1afa51bfc4560ea647f31ef54d1608eb77b8339379b8bd`
- AizMinibossFlameBarrelChild.java: `90626f976f0da7485c8cbd450f8072f53ac552e3c3ee617fcf79e36001653495`
- AizMinibossDebrisChild.java: `b7a7ead31f09dda44b2b988c0b376be5c1b0ba60399506e8712ba22ecbeae706`
- AizTransitionFloorObjectInstance.java: `b9087c7ff72fcf49c2c6c0c8d7c2382474190a48edd52a98fa0e35b7cadd66ca`


## AIZ retained results/title SST and real child latches — 2026-10-14

Local scene/floor candidate6894673ab composes with qualified Rootda27f4f3d,
including df8 physical/timed RESULTS FIFO/DMA, as d3ca6c022. Composition31682
completes60 with58passes/2strict failures at23:06:42 BST (140s Maven/0queue).
The second prefix still has51 raw errors at7589 rings111/0; read-only observer
49036 reproduces that exact frontier at23:14:56 (46.095s/0queue). The paired
results dependency alone does not close it.

Native loc_2DD06 replaces the results code with Obj_TitleCard, clears routine,
sets$3E and returns (asm62708–62725); it does not delete the SST. Init queues four
ROM archives and seeds$2E=90 (62108–62166). Create polls global Kos_modules_left
before first checked after-current allocation; first failure retries, whereas an
admitted prefix proceeds to Wait (62169–62210). The literal table starts at
260/E0,2FC/100,344/120,E0/10, targets120/17C/184/C0, with$10 entry,$20 exit and
stagger bytes3/5/7/1 (62303–62378,62450–62478). Higher-slot children write$34 on
every movement, including their final movement. The lower parent clears that
latch and returns; its following stationary poll clears rings/timer/air and
restores music (62214–62245). Wait2 decrements90, then increments$32 while$30 is
nonzero. Children retire from prior Render_Sprites sign; the next parent poll
publishes LoadEnemyArt/End_of_level and deletes the owner.

Native segment4 slot4 remains2D690 at7554/7566; children5–7 are2D95C and banner27
2D8E2 by7566. Rings remain111 through7594 and reset7595. The old engine deletes
its owner after Init and predicts reset from24 plus VBlank-phase compensation.
This AIZ branch now keeps the same SST and four physical children, with captured
parent2/movement/count/stagger/screen state and pure ctx.spawn restoration. The
fresh table seed/visibility contract is reused. The provider claims prepared ROM
art and presents actual child screen words without autonomous movement. No
trace-state input, fitted delay, shared clock/allocator or snapshot/API change
supplies these states. Other retained/slotless title workflows remain unchanged.

The independent existing AIZ results-exit negative fails1/1 at actual owner
deletion after Init. First63261:21 total,1obsolete oracle failure/5lease setup
errors (23:26:01,67s/61squeue); corrected oracle37178:16passes/5same setup errors
(23:27:41,24.130s/23s). Empty fresh-SST query did not prove the direct fixture's
initial omitted lease completed. Pilot88827 passes1 (23:30:01,23.986s/62s) but
shows stacked-FIFO art warnings; strengthened90391 correctly fails5 artLoaded
assertions (23:34:41,66s/0s). Final setup waits for actual initial lease/FIFO,
fresh assembly/real graph retirement, then admitted enemy FIFO before declaring
later results exit. It writes no art readiness or fitted timing. Final15021
passes5 production presets320/352/400/528/800 at23:37:00 (28.259s/0s), including
ROM artLoaded, first creation-scan XY, final movement/latch reset ordering and
two45-frame full-registry cycles at birth/reset and retirement, forced recreation.

Test-only91037 passes those5 and exposes2invalid new fixture premises
(23:42:33,25.284s/0s): no-op fillers were culled, and slot20 was a placed actor,
so removing a dynamic object did not free it for another parent. Persistent/
nonculling declared occupants and genuinely free SST69 correct those premises.
8792 passes the2changed cases at23:45:39 (24.132s/18s): first failure retry,
one-child prefix's20-move latch/two45 world cycles, exact ctx.spawn/parent2 with
another owner/two pure recreation/replay cycles. Qualify7distinct cases; literal
occupancy is not natural full-scene pressure certification. Bounded independent
native review found no concrete new Create/Wait/Wait2 or child-tail defect.

Affected16533 passes137/0/0/0 at23:48:33 (28.488s/0s): results art/children/
rewind, FBZ retained-title sizes, provider/fresh graph, in-level progression and
mandatory S3K startup60. Guards32336 passes100/0/0/0 at23:49:05 (31.552s/8s):
architecture4,factory46,schema25,timing25. Canonical48379 completes5/1failure/
0errors/skips at23:38:43 (96s/0s): originalAIZ,initialHCZ and BOTH return prefixes
pass, segment4 raw51→0, segments2/6 remain0. Chain retains segment8 raw1765
(1686physics/79animation), first6000 P2X4997/4996, and segment11 walk stop
atinput68801/loadgen9. Alternate87590 completes1/1failure/0errors/skips at23:40:31
(33.792s/140s), unchanged60 grouped25physics/35animation, first25589 animation
13/05, firstphysics25590 TailsSub7800/6C00. No full-chain/alternate closure.

Commands use local normal Maven queue, Java25, exact original absolute S3K ROM,
surfaceless EGL and null audio. Graph: -Dtest=TestS3kAizResultsNativeTitleGraph;
changed2: #firstCheckedFailureRetriesThenPartialPrefixUsesItsOwnMovementLatch+
pureContextSpawnRecreationKeepsExactParent2WithAnotherResultsOwner. Affected:
-Dtest=TestS3kResultsScreenObjectInstance,TestS3kResultsKosQueueAndChildren,
TestS3kResultsKosQueueRewind,TestS3kBossDefeatSignpostFlow,TestFbzResultsTitleAndAct2Sizes,
TestSonic3kTitleCardManagerRewind,TestSonic3kTitleCardKosQueue,
TestS3kHeadlessInLevelTitleCardProgression,TestS3kFreshTitleCardSstGraph,
TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils. Guards: -Pguards -Dtest=TestRewindArchitectureGuard,
TestRewindSchemaRegistry,TestSpawnRewindRecreatableCleanup,TestHardwareTimingAuthorityGuard.
Canonical/alternate profiles and selectors are unchanged from50751/39702.
Native class labels in optional SlotOccupancyProbe now read the current physical
owner and fail closed on multiple live claimants. The former first
getActiveObjects match concealed a real hidden-monitor conversion slot leak;
the independent follow-up below owns that diagnosis and repair.
The probe adds callback-contained traceDebugDetails and title/ring phase views;
input, comparison metrics and lag filtering remain unchanged. Temporary outputs
stay under target/aiz-retained-native-title-occupancy, never durable raw logs.

Current numeric recycled-parent pressure, same-act Init art-cache readiness,
SuperTails four-star SST, rendered/audio and broader cold/donor/team obligations
are separate work; no natural path consequence from the existing Init cache has
been established in this slice. HCZ/alternate frontiers remain active. This is a
bounded owner/frontier closure, not overall task completion.

Exact final production SHA-256:
- S3kResultsScreenObjectInstance.java: `4f5b6327d1bff805f141b943cd34d74fca390999f63f6e3701ff064442a13a25`
- S3kInLevelTitleCardElementObjectInstance.java: `1e34deea29bc6365cd47b01d7a5073f3794cce153a368d35836d76e0f41c8b03`
- Sonic3kTitleCardManager.java: `9c58638cdc23305ba0ff88b2c3c706ddeca546a33c87e43d1ab4e049a5c544a8`
- DefaultObjectRewindPolicies.java: `bb5a064ff728a4aff39b1cb3ba4d53b07554eecd200cce9eca4253d93812192f`


## Hidden monitor conversion retains its physical SST — 2026-10-15

The physical label observer21444 fails at23:59:11 BST: one case, zero assertion
failures, one error, `Multiple native SST dispatch owners: 24` (44.328s Maven,
70s queue). This was a real monitor/title alias, not permission to select either
claimant. `S3kHiddenMonitorInstance` installed its replacement without detaching
its old slot. Manager cleanup then freed the old handle's SST while the revealed
monitor remained live; a later title child allocated the same slot.

Native loc_83760 changes the current code pointer to Obj_Monitor, sets routine2,
$3C=4 and Y velocity-$500, clears the signpost bit and calls Sprite_OnScreen_Test
(asm176074–176099). It neither allocates nor deletes that SST. Obj_MonitorBreak
allocates icon/explosion children after current, retains the broken shell, and
Obj_MonitorAnimate eventually changes its code to loc_1D61A. That owner retires
only through Sprite_OnScreen_Test's coarse-X tail (asm40504–40520,40629–40677,
37262–37279). The local conversion now uses the existing detachSlotForTransfer
before addReplacementAtTransferredSlot; allocator, manager and header selection
contracts remain unchanged.

Independent negative52375 fails one assertion at00:07:57 BST (25.641s Maven,
43s queue): old hidden handle expected slot-1, actual42. Fixed51420 passes8/0/0/0
at00:13:21 (66s Maven): three existing components and five production-preset
manager/whole-world cases. Those cases declare the literal loc_83760 local
contact, admit the actual manager replacement at70, prove the first subsequent
free slot is71, and replay two45-pass full-registry cycles before/after cleanup
with forced recreation. This is local conversion coverage, not a natural cold
signpost route or all-pressure certificate. Final24540 passes37/0/0/0 at00:21:45
(28.550s Maven,96s queue): all12 title/transfer graph cases, hidden3, current
monitor20 and monitor-graph2. The prior137 title consumers and100 architecture/
schema/factory/timing guards remain qualified on the identical title source.

Final canonical45615 completes5/1failure/0errors/skips at00:20:13 (93s Maven,
0s queue). Original AIZ, initial HCZ and both AIZ return prefixes pass; the
fail-closed physical observer now completes without duplicate-owner errors.
The chain retains segment8 AIZ5 raw1765 (1686 physics,79 animation), first6000
sidekick_x4997/4996, and returned HCZ segment11 ownership stop at original input
68801/load generation9. Independent alternate31147 completes1/1failure/0errors/
skips at00:20:50 (35.879s Maven,87s queue): unchanged60 spans,25 physics/35
animation, first25589 animation13/05 and firstphysics25590 TailsSub7800/6C00.

Commands retain the canonical/alternate profiles and exact five/one selectors
above. Negative: --lean -Dtest=TestS3kHiddenMonitorInstance#inRangeLandedSignpostTransfersCurrentSlotToRevealedMonitor.
51420: -Dtest=TestS3kHiddenMonitorInstance,TestS3kAizResultsNativeTitleGraph#hiddenRevealRetainsExactlyOnePhysicalOwnerThroughCleanupAndWholeWorldReplay.
24540: -Dtest=TestS3kAizResultsNativeTitleGraph,TestS3kHiddenMonitorInstance,com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance,TestS3kMonitorGraphRewind.
Canonical enables only the optional read-only physical labels under
 target/aiz-physical-title-label-post-transfer. Java25, normal queue, original
absolute S3K ROM, EGL and null audio are unchanged. No trace hydration, arbitrary
header choice, fixture fit, shared clock/allocator change or broad sweep.

Flip/respawn header preservation beyond this replacement transfer, numeric
recycled-parent pressure, same-act title Init art caching, SuperTails four-star
SST, rendered/audio and broader product obligations remain separate. The actual
strict AIZ5/returned-HCZ and alternate frontiers are still open.

Exact conversion production SHA-256: `5dabe3ee8f2445df6c12251cacdcf572d9e5a2021f91b10e4a014374291532d2`.


## Prepared next owner: boss dispatch and returned HCZ — 2026-10-15

Read-only/source preparation during the 23:30–00:30 UTC measurement window;
no production modification, build, test, native probe or capture is launched.
The qualified source remains b8f3da8a1. Two unrun negatives in existing
TestAizEndBossInstance have source SHA256
9fac134eec2d99ad6b3f9c8f4121cf47b63f992ad66544f52c6271e3c08578fd.
They distinguish Hover's retained movement/CheckHit tail from AttackWait's
CheckHit-first/Obj_Wait fallthrough. This is a literal local phase contract,
not a controller-triggered route witness or a red result yet.

Native Touch_Enemy clears collision_flags, decrements HP and publishes status7
on zero; it does not change the boss code/routine or score (asm20912–20928).
Obj_AIZEndBossMain selects its dispatcher once (138047–138066). Revealed runs
raw animation then CheckHit (138124–138127). Hover runs Swing, MoveSprite2 and
Obj_Wait before CheckHit (138144–138148). AttackWait runs CheckHit then Obj_Wait
(138187–138190). The defeat callback installs Wait_FadeToLevelMusic and $34,
then BossDefeated_StopTimer falls through to $2E=$3F and scoring. A uniform
one-pass deferral would therefore break AttackWait's legitimate new-timer tick.
Actual retained aiz_5 aux slot7 is6923E/routine06/status00 through5486 and
85668/routine06/status80 from5487. Current engine capsule birth remains5670
and its button source is P1; native recorded capsule birth is5671 and the
historical independent observation identifies P2 at6006. The old discarded
one-frame probe is historical attribution, never an implementation constant.

Proposed smallest repair after an observed negative: captured local pending
terminal admission, preserving Touch_Enemy HP/status/collision publication and
old movement until the actual CheckHit call. Admit the defeat callback after
Revealed/Hover tails and before AttackWait's existing wait; score after the
native timer-stop owner. Keep generic BossHitHandler, clocks, allocator and
fixtures untouched. Qualify the pending/callback boundary through two-way full
registered-world recreation and forward replay, then existing AIZ end-boss graph
and end-sequence consumers. Initial short command after Root resumes:
--lean -Dmse=off -Dtest=TestAizEndBossInstance#hoveredKillingHitRetainsOldDispatchUntilItsNativeCheckHitTail+attackWaitKillingHitFallsThroughItsExistingObjWaitAfterNativeCheckHit test.
Affected command uses the normal lane with exact ROM and selectors
TestAizEndBossInstance,TestS3kAizEndBossGraphRewind,TestAiz2BossEndSequenceObjects,
TestAizEndBossDebrisWidescreen plus any new actual-world case. Changed owner must
then rerun the canonical five and alternate one selectors already recorded;
no unchanged broad run. Nonfatal hit-flash ordering is a distinct inherited
contract to inspect rather than silently conflate with this terminal slice.

Returned HCZ11's retained report is incomplete82067=69393physics+12674animation,
50 lag rows, bootstrap0; first physics1510 Y07D6/07DF, earlier recorded mapping
1507 63/95. At original input68801 its LEVEL identity is loadGeneration9/zone1/
act0, which the coordinator rejects before source closure. Ownership compares
actual level generations; ordinary/death/interior loads are not licensed by an
in-level advance. The retained record lacks the last load cause and P1 death
boundary, so that stop alone does not establish a bootstrap/coordinator bug.
Native row5725 still has P1 alive/routine02 at3716/06D3; engine occupancy remains
near old9896 conveyors. Earlier rows1506–1510 keep native control03, X advances3,
velocities/ground speed zero, and conveyor mapping/Y move before the later stop.
Native fan10/13 and conveyor91/92 are present in the same geometry as engine
fans7/9 and conveyor91/92. The first animation/Y change is the existing fan
controlled-player ground_vel=1 → conveyor pose-byte progression/table contract.
No current engine phase/oscillation/last-writer record proves its cause yet.
The next bounded observer should record those existing owner values and the
actual load receipt/death state; it must not alter shared bootstrap or hydrate
comparison data. No HCZ runtime repair is inferred from current retained data.

### Boss CheckHit outcome — 2026-10-15 (Opus frontier lane)

Both prepared negatives were red before production changed. The lean exact
selector gave 2 tests and 2 failures: routine 16 at touch, where 6 and 8 were
expected (01:48:14 BST).

`AizEndBossInstance` now keeps Touch_Enemy's publication (HP, invulnerability,
collision and `state.defeated`) and arms a captured `defeatCheckPending` flag.
`admitPendingDefeat()` runs only at the native CheckHit positions:

- after Revealed's raw animation;
- after Hover's Swing, MoveSprite2 and Obj_Wait;
- before AttackWait's existing countdown, so the newly published $3F ticks to
  $3E in the same pass.

It calls the existing defeat-start path, which stops the timer before the
score is added (BossDefeated_StopTimer, then BossDefeated). Nothing else moved:
generic BossHitHandler, allocator, clocks and fixtures are untouched; nonfatal
flash/SFX ordering is unchanged and remains the separate inherited contract.

The generic field capturer holds the flag. A new registered-world test
captures ObjectManager and GameStateManager at the pending boundary and after
admission. It restores each snapshot twice, then replays forward and compares
routine, wait timer, pending flag and score.

The existing capsule-wait test previously hit the boss in its INIT routine.
That is not native-reachable: collision is clear until Revealed
(sonic3k.asm:138109-138114). The test now hits in Hover and asserts the CheckHit
pass publishes $3F. Its 0x40 + 0x78 wait contract is unchanged.

Qualification:

- **Affected classes, same 31-class run as the colour commit:**
  TestS3kAizEndBossGraphRewind 6/0, TestAiz2BossEndSequenceObjects 58/0,
  TestAizEndBossDebrisWidescreen 10/0, and the four mandatory S3K classes.
- **Fixed fixture, lean rerun:** TestAizEndBossInstance 18/0/0/0.
- **Canonical selection:** 5 tests, 1 failure, 0 errors, 0 skips.
  - AIZ5 raw errors fell from 1765 to 1692 (physics 1686 to 1613).
  - Its first error moved from 6000 (P2 X) to 6111 (sidekick_x_speed, ROM
    0024 versus engine 0000).
  - Returned HCZ11 and the alternate slice are unchanged; exact figures are
    in the frontier log.


## Returned HCZ fan load waits for the parked S3K X cursor — 2026-10-15

This is the outcome of the bounded attribution named above.

### Observer

A temporary, comparison-only chain observer subclassed `AbstractRunChainTest`.
It used `productionOutputRowObserver` on the original movie and
`assertChainReplayThroughSegmentRow(seg 11, 5726)`. Per row it recorded engine
fan pulse state, belt pose phase, oscillation byte $14, P1 state, camera, and
the occupancy of slots 3–12. It read no trace rows. It was deleted after use
and can be rebuilt from this description in minutes.

### Native evidence (hcz_2 aux)

- **Fan push.** Natively the $2250 fan pushes the belt rider from trace 1505:
  the belt phase reaches 6 at 1505 and $24 at 1510, which is mapping $64,
  Y $7D6.
- **Fan load.** `object_appeared` dates the $2250 fan (y $874) and the $22B0 fan
  to trace 1246, in slots 10 and 13, with the $227F hurt block and the $22B0
  object $2413E between them.
- **Crossing frame.** At the coarse-X crossing to $2000 (trace 1160),
  `slot_dump` shows the dynamic SST full: slots 4–92 occupied except 7 and 8.
  The two old fans in slots 7 and 8 delete only later in that frame's
  `Process_Sprites`. Slot 3 is never returned by `AllocateObject`, whose loop
  pre-increments past `Dynamic_object_RAM`.
- **ROM behaviour.** `Load_Sprites` allocated 63, 64 and 65 (two $85AD2 and the
  $2250/$78E $2413E). `CreateNewSprite4` then found no slot, so the
  `loc_1B8E8` loop exited with `Object_load_addr_front` parked on the fan.
  `loc_1B864` runs that loop again only on the next `Camera_X_pos_coarse`
  change, which came at 1246.

### Engine before the fix

- **Early load.** The engine created the fan at trace 1161. At that point it
  had 89 slots in use, against native's 87. In the frame the old fans
  retired, its count fell from 89 to 16. `ObjectManager` retried pending
  cursor entries on every loader pass, so the next frame loaded the parked
  fan. Its 121/181 pulse ran 85 frames early.
- **Effect at 1505.** The fan was idle (toggle 1, timer 82) when native was
  active. The belt pose stayed at phase 0 (mapping $95/$94), and the first
  error landed at 1510.
- **Bubble removal (not attributed).** About 70 `FanBubbleChild`s disappeared
  in one frame. `HCZCGZFan_Bubble` ($30834) has no camera deletion and
  survives natively, and this lane did not attribute the mass removal. Engine
  slot numbers therefore still differ: the fan is in slot 6 against native
  slot 10. This is recorded, not changed.

### Repair

`ObjectPlacementController` latches one X-cursor loader pass per coarse change
(and per reset), and clears the latch on rewind restore. The S3K branch of
`ObjectManager` processes pending entries only on such a pass. The pass first
needs a free SST, and stops at the first allocation failure or once the last
free slot has been consumed (`loc_1B8D2`–`loc_1B8F2`, `CreateNewSprite4`).

Pending ownership is unchanged. It is still captured and restored, as
5fc1d61d4 introduced. Its test, which retried on an unchanged camera, now
asserts that the entry stays parked until the next coarse pass. A new case
covers the last-free-slot stop.

### Qualification

- **Focused selection:** placement, slot allocator, CNZ late ring, SS entry
  ring and the four mandatory S3K classes passed 150/0/0/0.
- **Canonical selection:** 5 tests, 1 failure, 0 errors, 0 skips.
  - HCZ11 first error moved from 1510 to 1768 (Y ROM $870, engine $871).
  - HCZ11 raw errors fell from 82067 to 81981 (69334 physics, 12647
    animation).
  - The walk still stops at input 68801/loadGeneration 9.
  - AIZ5 is unchanged at 1692 (first 6111); the other segments stay at 0.
- **Alternate selection:** unchanged at 60, first 25589.
- **Observer confirmation:** the fan is now created at trace 1246, is active
  at 1505, and the belt phase rises from 1505 exactly as native.

## Returned HCZ hand launcher and Mega Chopper control lock — 2026-10-16

Round-2 lane hcz11, branch `bugfix/ai-s3k-r2-hcz11-row1768` over `cc3e77ee9`.

### Row 1768: the rising hand launcher never lands anyone

- **Native (hcz_2 aux).** `HCZHandLauncher_Main` ($30B58) is in slot 36 at
  x $2420, resting at y $8C8 ($32 = $878, $30 = $50). Sonic's x reaches $2400 at
  row 1762, inside the $40-wide range, so `HCZHandLauncher_WaitForGrab` subtracts 8
  per pass. The hand reaches $890 at 1768. Its `SolidObjectTop` (d3 = $11) lands
  the rolling Sonic (y_radius $E) at $890 − $11 − $E − 1 = $870: status $4C,
  interact slot 36, `mode_change on_object 0→1`. At 1769 the continued ride
  (`MvSonicOnPtfm`) gives $871, and at 1770 he rolls off the edge.
- **Engine.** `HCZHandLauncherObjectInstance` uses `MANUAL_CHECKPOINT` and called
  `checkpointAll()` only inside its grab and launch-release checks. A rising or
  returning pass therefore resolved no solid, and Sonic stayed on the floor at $871.
- **ROM order.** `HCZHandLauncher_UpdateSolid` writes y_pos and always calls
  `SolidObjectTop`. `HCZHandLauncher_UpdateAfterLaunch` calls it only while
  $30 <= $18 (sonic3k.asm:65824-65833, 65863-65874).
  `HCZHandLauncher_CheckPlayers`, `HCZHandLauncher_TryGrabPlayer` and
  `HCZHandLauncher_ReleaseHeldPlayers` read the standing bits that the previous
  pass left in status(a0) (65814-65816, 65880-65918, 65966-65968).
- **History.** Milestone 21 (`8d9a82d9f`, 2026-07-11) had already restored this
  order. Merge `e20f4a3ba` (develop into next, 2026-07-17) kept `next`'s
  variant without it, and later merges carried that variant back to this line.

### Row 1891: a Mega Chopper locks Sonic's controls

- **Native.** `Obj_MegaChopper` ($87F5C, slot 9) catches Player_1 alone at row 1863
  (aux `control_lock_state ctrl1_locked 255`). `MegaChopper_CheckCapture` clears
  `Ctrl_1_logical`, sets `Ctrl_1_locked` and sets bit 2 of `$38(a0)` only when
  `collision_property` == 1 (sonic3k.asm:184431-184435). Each carry pass then writes
  `Ctrl_1 & $8C8C` (left/right only) after the release checks (184356-184360).
  Down is pressed from row 1891 but is masked. At 1893 the AutoSpin ($1E9E6) forces
  the roll, anim 2 releases the chopper, and the lock clears
  (`MegaChopper_ReleasePlayer`, 184393-184396; aux `ctrl1_locked 0` at 1893).
- **Engine.** `MegaChopperBadnikInstance` never locked, so Down reached Sonic, who
  rolled at 1891 and went 5 px off (x $24AE/$24B3, rolled y_radius).
- **Repair.** A P1-only capture clears logical input and calls `setControlLocked(true)`.
  Each carry pass publishes this frame's left/right through the existing
  forced-input mask for the next player pass, and every release path unlocks.
  Captures by P2, or by P1+P2 in the same pass, stay unlocked as in the ROM.
  The `$38` bit 2 latch is a captured scalar; restore twice plus forward replay is
  tested.

### The hand-only KosM abort and why it disappears

With only the hand fix, the walk aborted at row 4001 (`S3K KosM module FIFO is
full`, thrown from `Sonic3kStarPostObjectInstance.activate` → `spawnBonusStars` →
`queueStarPostBonusArt`).

- **Native ledger.** The hcz_2 ledger repeats one four-module group (fingerprints
  f7d726/a0cfa4/03987f/0956ed) at rows 37-49 (#130-133), 3652-3661 (#137-140) and
  6789-6796.
- **Port report.** The fail-closed port reported #137 at 3652 as "engine job is not
  prepared", then "engine pending #137 f7d726" at 3656-3661. The engine submitted the
  identical job (same kind and fingerprint) after native had completed it. The port
  therefore never released the group, and the starpost's later submission found
  four entries.
- **Both fixes together.** The run reports no unmatched completion before the walk
  stops. The only unconsumed edges are #154/#155 and #220/#221 at rows 10162+,
  beyond the stop. So every KosM job through row 7642 (#130-#153) matched native
  ordinal and fingerprint.
- **Attribution.** The abort follows the 1891 divergence and ends when that cause
  is fixed. The engine-side producer of the late #137 (the group's owner object)
  was not instrumented.

### Walk-stop observer (question B)

A temporary engine-only observer, deleted after use, recorded receipts, the
generation and P1/P2 drown/death state through the chain on 1eaa2cc4a:

- Segment 11 is admitted at gen 8 with receipt `INTERIOR_RETURN@gen8/z1a0`.
- The act 1 → 2 advance at observer label 6184 (native row 6183) keeps gen 8, the
  seamless path.
- Engine P1 dies at label 7478 (x $06FD, y $07B4, 2 rings) while native P1 is alive
  (row 7643: routine 02, x $09E1).
- At BK2 70718 the death-restart load completes with `DEATH_RESTART@gen9/z1a1`. The
  coordinator correctly rejects it as an ownership loss.

The stop is therefore a gameplay divergence (engine P1 death), not a
coordinator/bootstrap defect.

Labels are the cursor after the step, which is the trace row + 1. This is
consistent with the air decrements: engine 1736/1796/.../3416 against native
1735/1795/.../3415.

### Row 3475: drowned sidekick routine is a comparator derivation gap

- **Native.** Tails' `air_left` runs $1D → 0 by row 3415. `AirCountdown_ReduceAir`
  then sets `routine` $C, anim $17 and a $78 drown timer at row 3475
  (sonic3k.asm:33539-33573). `loc_1857C` later sets routine 6.
- **Engine.** The observer shows the engine sidekick's air ticking on the same rows,
  and its drown pre-death state starting on the same row (label 3476).
- **Gap.** `TraceCharacterState.routineFromSprite` reports $02 for a drowning player.
  S1 and S2 keep routine 2 while drowning (`0A LZ Drowning Countdown.asm`, s2.asm
  near 42167), so S3K needs a per-game $C.
- **Unrelated enum.** `NativePlayableRoutine` maps DROWN to $0A; the ROM writes $C.
  Its only consumer is the MGZ CONTROL gate.
- **Next owner.** The trace comparator, with S1 LZ and S2 drowning fixtures as
  controls. Physics-route rows 1891-3474 now match.

### First production divergence after the fixes: HCZ miniboss descent phase (row 5065)

The same observer dumped engine P1/P2 positions per row. Its labels line up with
native at +1: P1 has zero mismatches over rows 3400-3474 at offset 1 and 75 at
offset 0. P1's first production mismatch after 3475 is row 5066.

- **Native.** At row 5065 Sonic's velocity reverses (x_vel $FA7A → $059E,
  y_vel $07B6 → $F812). At the same row the HCZ miniboss (slot 6, $69F7A,
  `HCZMiniboss_*`, raw routine $0A) gains status $40.
- **Engine.** `HczMinibossInstance` admits the hit one row later (hit count 6 → 5
  at 5066). Across rows 5039-5071 the engine boss y leads native by exactly one
  4-px step: $068C against $0688 at 5064.
- **Why it matters.** Sonic's touch runs before the boss slot moves, so a boss
  4 px lower is touched one frame later.
- **Native descent.** The boss appears at ($36C0,$05F4) in routine $0A at row 5027
  and makes its first +4 step at 5028.
- **Engine descent.** The engine's descent starts one step early. Its entry row
  was outside the observed window.
- **Consequence.** P1 then diverges, and the engine P1 later dies (label 7478),
  which produces the 70718 stop.
- **Next owner.** The HCZ miniboss routine-$0A entry and first-move frame.

The drowned Tails' y is 1 px off from row 3477 ($06A2 against native $06A3).
That is a separate drowned-body motion difference, not attributed here.

### HCZ zone slice matched pair

`-Ptrace-segments -Dtest=TestS3kHczZoneSliceTraceReplay`, 2 tests and 1 failure
in both arms:

- original-source arm (both sources at `cc3e77ee9`): 4699 errors;
- `1eaa2cc4a`: 3746 errors.

Both keep the first error at 9482 `air` 1/0. The 953-error reduction is not
attributed per field: the single-trace report keeps the first error, totals and
recent rows only. The lead ruling therefore keeps both commits unqualified for
merge.

### Rejected / not done

- No capacity change, readiness release, row realignment, hydration or fitted
  timing.
- The exporter routine byte is left as is; an S3K-only $C needs a game-owned
  semantic rule plus S1/S2 controls.
- The miniboss phase is unrepaired.

## Pass 2: drown export, miniboss Init pass, drowned motion — 2026-10-16

### Drown routine export (3475)

`AirCountdown_ReduceAir` writes routine $C to the drowning player, and
`loc_1857C` later writes 6 (sonic3k.asm:33539-33575). S1 and S2 keep routine 2
while drowning (`0A LZ Drowning Countdown.asm`:266; s2.asm:42172).

The exporter now asks the active module's `TracePlaybackProfile` for the byte:
S3K uses `DEDICATED_DROWN_ROUTINE`, everything else `CONTROL_ROUTINE`. Dead (6)
wins over drowning, and drowning wins over hurt. `NativePlayableRoutine.DROWN`
is now $0C; $0A is `loc_12590`'s scroll wait. The value is comparison-only.

### Miniboss Init pass (5065)

`HCZMiniboss_StartFight` only installs `Obj_HCZ_MinibossLoop`.
`HCZMinibossLoop_Init` runs on the next pass without moving
(sonic3k.asm:139286-139294, 139319-139334). Native dating agrees in both traces:

| Trace | StartFight (loop pointer, routine 0) | Init (routine 2) | Routine 4 |
|---|---|---|---|
| `hcz_completerun` | 7960 | 7961 | 8185 |
| `hcz_2` | 4597 | — | 4822 |

The old engine installed the descent on the StartFight pass. A per-row engine
observer of the returned chain showed every phase one pass early: WAIT at 4821,
RISE at 4881, DIVE at 4997. With the fix, an engine observer of the first visit
matches native at 7960, 7961, 8185 and 8245.

### Two compensating latches in the vortex

The early boss had hidden two compensating one-pass latches, and fixing it
exposed them:

- **Capture.** `waterEffectPullReady` (from `04107a037`) spent the water effect's
  first routine-8 pass. `loc_6A5D8` pulls on that pass: native enters routine 8
  at 9045 and Sonic gains object_control at 9046.
- **Release.** `vortexFinalPullPending` pulled and released one pass after
  END_VORTEX. `loc_6A5FA` → `loc_6A986`/`sub_6A996` releases on the same pass
  (sonic3k.asm:139849-139866).

Both latches are removed.

### Drowned motion (3477)

S1 `.sinking`, S2 `Obj0A_PlayerHasDrowned` and S3K routine $C all move first
and then add $10. None checks the player's object_control. The engine added
first, and skipped the motion under a movement-suppressing object lock. In the
returned chain the water wall holds the drowned Tails, so its $FE00 y_sub never
carried a pixel.

No S1/S2 fixture contains a drowning row: 0 of 127 physics files have
anim $17 for the player or sidekick.

### Matched HCZ zone slice

| Arm | Errors | First error |
|---|---|---|
| original `cc3e77ee9` | 4699 | 9482 `air` |
| final | 3748 | 9482 `air` |

- **Common prefix.** 810 spans are identical.
- **Resolved.** 1960 spans from 13549, including the old hand-launch `g_speed`
  −$1000/−$0350.
- **New.** 1009 spans from 13637: `x` $0942/$0943, `tails_x` and later
  CPU/respawn fields.
- **Changed.** 411 spans, the first a `tails_cpu_target_x` span at 12259 that
  lengthens.

The first new span sits one pixel after the first-visit hand launch, and the
next owner is that launch path.

### Remaining

- **Returned HCZ11 first error, 3483** (`sidekick_status_byte` $42/$02). S3K
  routine $C does not run the water check, but the engine's water coordinator
  clears the drowned Tails' underwater bit. Fixing it needs a per-game gameplay
  rule, and those rule records are `@ModApi`, so the change needs lead approval.
- **Walk-stop at BK2 71601.** This is still a death-restart ownership loss
  (gen 9, act 1).
- **Not run here.** `TestHczMinibossNativeRendering` needs a GLFW display.

## Pass 3: drowned routine rule, CPU pause, dead underwater — 2026-10-17

### Water check during the drown (3483)

S3K only calls `Sonic_Water`/`Tails_Water` from the routine-2 control path
(sonic3k.asm:21998, 26242). The drowned routine $C (`loc_125AC`/`loc_15810`)
never calls them. S1 (`f_playerctrl` bit 0 skips only the modes, so
`Sonic_Water` at "01 Sonic.asm":82 still runs) and S2 (s2.asm:36251) keep the
water check for a drowned player.

That per-game fact now lives in `DrowningBubbleRules`, the existing owner of the
drowning countdown, as `drownInstallsDedicatedPlayerRoutine`. Adding it is an
unpublished-candidate API change: the canonical constructor changes, and the
`0.7` pin gains three lines alongside the `ModApiVersion` description. The
descriptor stays at `0.7.0`. The trace exporter's routine $C reads the same
rule; the trace-profile enum from `78eea96fc` was removed.

### CPU step during the drown (3611)

`Tails_CPU_Control` is reached only from Tails' routine-2 path
(sonic3k.asm:26180-26190). The engine CPU kept following Sonic, turning the
drowned Tails left. The CPU update now returns with cleared inputs while the
dedicated-routine rule applies. S2 Tails keeps routine 2 while drowning, so its
CPU still runs; the control test confirms this.

### Dead underwater status (3619)

No game's death routine calls the water check: S3K
`loc_12390`/`loc_1578E`, S2 `Obj01_Dead` and S1 `Sonic_Death`. Across every
committed S1/S2/S3K fixture, the underwater bit never changes while a player
stays in routine 6. The water-check gate now covers dead players in every game.

Validation:

- The S1/S2 underwater-death fixtures (LZ1, CPZ2 level select, CPZ2 seg9 and
  seg10) all pass.
- LBZ slice matched arms: identical 1663 errors, first 30582.

### Zone slice 13637

The errors are `cascading=true`, with x/y 1 px off from 13500 to 13546, before
the 13549 grab. The grab preserves the incoming x_sub (ROM `move.w`), so the
inherited subpixel resurfaces after the 13636 launch. The root is the
pre-existing 9482 boss-defeat edge, unchanged from the original-source arm:
natively both players lose their slot-47 support and go airborne, while the
engine keeps them grounded.

### Next

The 3832 monitor at ($2D9C,$0270), type RINGS, subtype 3:

- Native and engine both break it at 3800 (engine print-only diagnostic).
- Natively the award lands at 3832. The broken shell (`loc_1D61A` via
  `Obj_MonitorAnimate`, sonic3k.asm:40504-40516) is deleted by
  `Sprite_OnScreen_Test`, but `Obj_MonitorContents` is a separate object
  that completes its rise and award.
- The engine keeps the icon state machine and the award in the shell, and
  `onUnload` destroys the `MonitorContentsSlot`. When Sonic leaves the
  monitor offscreen, the award is lost.
- Next owner: move `updateIcon` into `MonitorContentsSlot`, keep it alive past
  the shell's unload, and give it rewind/owner handling.

## Pass 4: independent monitor contents — 2026-10-17

### Owner (3832)

`Obj_MonitorSpawnIcon` allocates `Obj_MonitorContents` with
`AllocateObjectAfterCurrent`, copying x/y, anim, render_flags and the breaking
player (sonic3k.asm:40640-40652). The shell's `loc_1D61A` deletes it offscreen;
the contents run `sub_1D820`, award through `loc_1D850`, then wait out
`loc_1DB2E`'s timer of 29. No contents routine checks the screen.

`5b747c95f` moves the rise, award, timer, render and rewind state into
`MonitorContentsSlot`. It follows S2 `MonitorContentsObjectInstance`. The shell no
longer destroys it. Unit harnesses without an object manager still run the
inherited routine on the shell.

### Engine contracts found on the way

The first fix still lost the payout. Two engine defaults also cancel an
independent object:

- **Offscreen unload.** `ObjectManager` runs the generic dynamic
  `out_of_range` unload on every executed object unless it reports
  `isPersistent()`.
- **Rewind probe.** `ObjectRewindDynamicCodecs` probes only listed
  constructor shapes. `(ObjectSpawn, MonitorType, PlayableEntity)` was
  silently not recreated; the restore diff showed the contents missing.

Persistence also exposes the object to the seamless-transition offset, so it
needs `RomWorldPositionedObject`. All three are now in the implementation
pitfalls.

### Validation

- **Red first** (`0204eb045` source): a new lifecycle class, 4 tests / 3 F.
  The upright, inverted and rewind variants lose the payout when the shell
  unloads; the loaded control pays once.
- **Focused set:** 1511/0/0/4. The 4 skips are
  `TestSecondStrikeCheckpointGameplay`, which needs the external
  `secondstrike.checkpoint.jar`. The set includes every-object rewind round
  trip, the graph/coverage guards, the CNZ monitor-slot trace check and the
  mandatory S3K four.
- **Canonical:** seg11 first error 3832 → 6796; raw 32763 → 29258.
- **Alternate:** 60 identical spans.
- **HCZ slice:** 3738, identical to a matched `0204eb045`-source arm.

### Limits

- The chain report is summary-only, so seg11 spans after 3832 were not
  compared.
- 6796 is unattributed.
- The zone-slice 9482 boss-defeat edge stays inherited, and 13637 is its
  cascade.

## Prepared independent colour-expansion feature — 2026-10-15

The user requested a saved Full/Limited display setting during the measurement
window. This supersedes the earlier optional capture-only native-word provenance
proposal. Its owned source writer was canceled while blocked, before mutation;
no metadata, codec or property draft entered this tree. Source-only feature and
test preparation holds the normal worktree EX lease and runs no heavy work.
The qualified AIZ tip remains b8f3da8a1; pending boss tests remain a separate slice.

The exact minimal transfer is forward rounded RGB * 238/255 after existing fade
and colour-profile conversion. Existing Palette.Color ROM decode levels
0/36/73/109/146/182/219/255 therefore become exactly
0/34/68/102/136/170/204/238. Primary GPGX source
051d430d3d1b54625f9900c8f152d7f232e06daf core/vdp_render.c987–998 builds the normal
3-bit→even-nibble LUT; MAKE_PIXEL498–500 replicates the nibble. Shadow/highlight
is a distinct VDP mode (1135–1151), outside this setting. Direct gain also preserves
intermediate arbitrary creator colours; native word inference is unnecessary.
Default Full, existing profiles, gameplay palette bytes, copies, codecs, rewind
state and source constants stay unchanged. Changing a display preference does not
restore a previous preference when the gameplay timeline rewinds.

The source draft owns display.colorExpansion, the independent annotated enum,
GraphicsManager accessors/upload paths and underwater keys, settings labels,
config defaults, pre-render saved-setting synchronization and documentation.
Normal palette refresh uses the existing latchable publication path; direct and
derived water keys include expansion separately. UI and shaders outside palette
uploads retain their own colour contracts. Candidate API version remains
unpublished0.7.0; authoritative compiled signature generation is pending resumed
builds, not hand-written pin certification.

Prepared meaningful checks cover all512 ROM RGB combinations, all256 arbitrary
channel values under every independent profile, writer/alpha boundaries, saved
UI apply/cancel/default round trips, live setting change, native V-int ownership,
both direct/derived water cache reuse and invalidation, fade order, and actual
surfaceless palette texture plus framebuffer pixels.

Qualification (Opus frontier lane, 2026-10-15). The first focused 14-class run
gave 114 tests, 0 failures, 2 errors, 0 skips. Both errors came from an
explanatory comment that had been added to `mod-api-release-policy.properties`.
That descriptor's parser accepts only strict `key=value` lines, so the comment
was dropped and the descriptor is unchanged. `TestPresentationSpritePixels`
genuinely rendered: its EGL assumptions held and it reported 0 skips.

An audit then found three gaps, all closed in the same commit:

- **Captures ignored the saved setting.** Only `Engine` applied it. The same
  owner, `DisplayColorProfileController.applySavedSettings`, now also runs at
  every `HeadlessGameBoot` boot. It runs per boot because capture sessions
  install their configuration overrides after constructing the boot's GL
  context. `TestCaptureColourExpansion` renders S2 EHZ1 through
  `GameplayCaptureSession` under Full and Limited and reads the framebuffer.
  Full must contain 255, Limited's maximum must be 238 with 238 present, and
  at least 99% of pixels must equal the forward gain. With the wiring removed
  on purpose, the test failed (1 test, 1 failure: "Limited ... was 255").
- **The CRAM colour-0 backdrop and clear colour bypassed profile and
  expansion.** Affected: the parallax uniform, the level clear, S1 special
  stage, S1/S2/S3K level select, S1/S2/S3K/KiS2 title screens, S3K data
  select, the S2 ending and the CRAM-white special-stage results clear. All
  now use `PaletteDisplayColor`. A GL clear-pixel case covers this.
  Constant black clears are unchanged, because every profile maps 0 to 0.
- **Per-frame synchronisation parsed the settings every frame.** It now
  compares the raw saved strings and parses only on change. In a clean JVM a
  probe measured 0 B for both the synchronisation and its two config reads.
  After any `mock(SonicConfigurationService.class)` in the same JVM, Mockito's
  inline mock maker instruments every instance at 96 B/frame; the shared
  qualification fork showed exactly that. The oracle therefore asserts that
  synchronisation allocates no more than its own two reads. There is no
  existing Engine-draw allocation oracle. The 5254 warmed-draw oracle in the
  plan is the Slot panel's.

Bounded SSZ screenshot corroboration uses the already verified original movie
and native publication boundary: separate neutral SETUP_ONLY render, then
unchanged original controller suffix at ordinary poll0. Compare Full/Limited
engine spots680 and1341 with the retained native images/CRAM and actual image
inspection; preserve source/core/movie/geometry/published-boundary provenance.
Do not re-encode or normalize screenshots, repaint RGB, infer shadow/highlight,
or claim full pixel parity from the range transfer. Root owns SSZ source/matrix.


## Retained HCZ lane composed onto Root — 2026-10-17 (GPT-6.1)

Work is confined to T3 tree `t3-3aa3bc3c`, branch
`feature/ai-s3k-hcz-gpt61-repair`. The first writer operation fast-forwarded
`707b3c208` to Root `2056a99cd4011dff1b725f401a905f8baa7ff779`
under the current Main wrapper's `worktree_build_slot`. The retained tree
`t3-178a77b5` at `720406940` and its seven WIP paths remain read-only.

Source comparison, rather than ancestry alone, selected the missing final
`cc3e77ee9..f8897c675` behavior: hand solid dispatch, Mega Chopper P1 control
ownership, miniboss loop Init and final vortex dispatch, drowned motion,
rule-owned water/CPU/exporter gates, and independent monitor contents with
lifetime/recreation/world-offset/award behavior. The obsolete intermediate
trace-profile classifier and `ec1d6b8dc` immediate title-entry change were not
replayed. Root's final trace profile, palette-white-fade rule, player mapping
access, new title helpers and unrelated API pins are retained.

The unfinished carrier WIP now stores each native player's target once at
capture, matching `loc_6A80C`'s word `$3A = Camera_X_pos + $A0`;
`sub_6A916` compares that word even when the camera moves. The request also
carries its locked current X bounds into both X easing targets.
`Change_Act2Sizes` stores HCZ2 sizes but skips applying current HCZ X bounds;
only P1 carrier release `loc_6A8AE` creates `Child1_Act2LevelSize`. The real
stored act-2 sizes remain the later child owner's source. A manager-sidecar,
playable and camera snapshot regression restores twice and replays both
carriers before and after release. Distinct P1/P2 target words are also checked
through manager recreation; the schema guard requires both fields.

The water-check gate now belongs to `LevelWaterCoordinator`, preserving the
sprite size ratchet and its existing public surface. Its moved test uses the
configured stock module's rules, without exposing a protected test helper.
`DrowningBubbleRules` needs the semantic dedicated-routine flag so hybrid rules
carry the behavior without game-name checks. Its prior four-argument candidate
constructor remains and defaults false. The five-argument constructor, accessor
and record component are added to the mutable `0.7` pin; Root's pins are
preserved. The descriptor is reviewed and remains unpublished candidate
`0.7.0`; no publication/version/topology change is justified.

Validation is recorded below when complete. Pass4 and composed-720 figures
above are historical observations, never fresh passes of Root composition.
The title-load boundary remains separate: the later recovery established that
both drivers submit the archive late and that the slice's same-step preparation
accidentally matches native. Native submission belongs on the load frame with
preparation on the first title-loop step. Row 7126 is not certified as walker-only.


Focused invocation `9100` completed on `2056a99c` plus the composed diff at
2026-10-17 09:34:45 BST: **1591 tests, 0 failures, 0 errors, 4 skips**.
The 26-class exact selector covers the new carrier/X-lock/manager-schema
regressions, moved cross-game water checks, drowned motion/CPU/exporter,
hand launcher, miniboss phase and water effects, chopper, monitor lifecycle,
monitor graph/content, every-object rewind, candidate API pins, the four
mandatory S3K startup classes and external Second Strike checkpoint gameplay.
The four skips are that last class's `secondstrike.checkpoint.jar` gate; these
are positioned authored AIZ-content cases, not stock HCZ checks. Queue wait was
285 seconds; Maven execution 33.070 seconds. The prior attempt `34687` stopped
at test compilation (moved test's protected helper and mixed boxed-number list),
not a gameplay failure. The corrected test fixture preserves protected access.
Source guards and strict trace qualification are still pending at this checkpoint.


At the next terminal writer boundary, checkpoint `b6c4898e2` committed the
HCZ work and `b9c1c8b3d` merged Root prerequisite `534a69e74` under this tree's
normal build lease. The prerequisite's six-line non-counter placement recovery
is independently qualified by Root; no worker wide-route rerun is substituted
for Root's delivery validation. The documentation hunks merged without conflicts.

Fresh guards on `b9c1c8b3d`, invocation `81789`, completed 09:38:27 BST:
**113/0 failures/0 errors/0 skips**, queue 50 seconds, Maven 85 seconds.
Exact `-Pguards` selector: `TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard`.
The 72 architectural-source assertions pass unchanged, including the playable
size ratchet; no allowance, budget or behavior assertion was weakened. The other
two previously reported unidentified guard failures were not reproduced by this
fresh named selection. This does not certify unselected guards.

Native presentation corroboration is a finite original-movie diagnostic using
BizHawk/GPGX 2.11 and the published-video API. The helper compiler retained this
tree's build lease and shared activity through its child; execution used normal
7 GiB/8-core queue admission and inherited the lease descriptors. It made no
RAM/setup writes, used no ambient display/Wayland or null OpenAL override, and
captured actual 320x224 published RGB at completed BK2 frames 66876, 66908,
69872 and 69900. The captured native zone/act is 1/0 for the monitor pair and
1/1 for the carrier pair, all four non-lag. Images and `native-inputs.json` are
under the external task root
`$HCZ_TASK_ROOT`.
The original movie SHA-256 is
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`;
ROM SHA-1 is `CFBF98C36C776677290A872547AC47C53D2761D6`.
The read-only headless source is `4fdf73880f93a3172d23bf9e157b15de3c78fc32`;
the setup's existing submodule checkout differs from the parent gitlink and was
not staged or cleaned. All four images were inspected. These native-only images
corroborate the matching original 320 Sonic+Tails route; no engine/native pixel
equality, wide viewport, other team/donor product or HUD/CPU same-frame identity
is inferred from them.


Additional boss/transition consumers, invocation `81847`, completed on
`b9c1c8b3d` at 09:38:53 BST: **37 tests, 1 failure, 0 errors, 0 skips**,
30 seconds queue and 25.746 seconds Maven. Selector:
`TestHczMinibossRomParity,TestHczMinibossNativeRendering,TestHczMinibossVisualParity,TestHczMinibossMultiSidekick,TestHczMinibossRocketTouchRewind,TestLevelSeamlessTransitionExecutor,TestTraceHydrateSwitchDefault`.
All 36 non-pixel consumers pass. The exact failure is
`TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels`,
line 53: `GLFW must initialize for pixel verification`, expected true / actual
false. This unchanged test calls GLFW directly before ROM/art/scene verification;
the supplied surfaceless-EGL property cannot select another backend there.
`Xvfb`, `xvfb-run`, `weston` and `cage` are unavailable. No ambient display was
used, no graphics helper changed and no pixel result is inferred from other
native-only captures. This is an explicit remaining engine pixel-verification
limit, not a claimed matched-baseline pass.

All Java invocations use the current Main absolute
`$MAIN_WORKSPACE/tools/testing/maven_queue.py` from this
worker's cwd, `--normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`,
`env -u DISPLAY -u WAYLAND_DISPLAY -u ALSOFT_DRIVERS EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4`,
and original ROM paths under `$MAIN_WORKSPACE/`:
`Sonic The Hedgehog (W) (REV01) [!].gen`,
`Sonic The Hedgehog 2 (W) (REV01) [!].gen`,
`Sonic and Knuckles & Sonic 3 (W) [!].gen`, with the corresponding
`sonic1.rom.path`, `sonic2.rom.path` and `s3k.rom.path` properties.
Only exact class/method selectors are used; no broad smoke/full-suite or push.


### Final single-invocation strict qualification

All three requested qualifications completed on executable source `b9c1c8b3d`,
with no executable edits between submission and completion. Java sources, POM and
candidate signature-pin path/content SHA-256 remained
`49d4f9091ed26cab4d777000f04a421469473eb617f36a2f65f97dda36ce6097`.
Each ran once. They queued concurrently and executed serially under this tree's
normal lease. Waiting intervals overlap; their sum is not delivery elapsed time.
The long wait was existing isolated-measurement admission, not replay execution.
No queue/lock/resource override or foreign process signal was used.

| Invocation | Selection | JUnit run/F/E/S | Finished BST | Queue wait / Maven / slot hold |
| --- | --- | --- | --- | --- |
| `25747` | canonical five-case controls/prefixes/chain | 5/1/0/0 | 10:03:18 | 1336.2s / about 98s / 99.6s |
| `67547` | HCZ slice plus independent Poindexter control | 2/1/0/0 | 10:03:57 | 789.0s / 38.175s / 38.8s |
| `45622` | alternate AIZ slice | 1/1/0/0 | 10:04:35 | 828.2s / 36.935s / 37.6s |

Common options are as above, plus `-Dsurefire.forkCount=1
-Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B`.
Exact profile and `-Dtest` values:

- Canonical: `-Ptrace-replay`,
  `TestS3kAizTraceReplay#replayMatchesTrace,TestS3kSonicTailsHczSegmentTraceReplay#replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`.
- HCZ slice: `-Ptrace-segments`, `TestS3kHczZoneSliceTraceReplay`.
- Alternate: `-Ptrace-segments`, `TestS3kAizZoneSliceTraceReplay#replayMatchesTrace`.

**Canonical observations.** All four control/prefix cases pass. Standalone
original AIZ reports zero errors/warnings/bootstrap errors, `total_frames=20463`;
initial HCZ reports zero errors/warnings/bootstrap errors, `total_frames=3519`.
The full chain has zero comparator errors in segments 0, 2, 4, 6 and 9.
Segment 8 retains four physics errors at row **7126**, first reported non-camera
`x`, ROM `$0280` / engine `$0000`; 62 lag frames and comparator `complete=true`.
This remains the separate unqualified load boundary, not a certified walker-only
problem and not an effect of the carrier repair.

Returned segment 11 reports **133771 total comparator errors**: **114800 physics
+ 18971 animation**, zero bootstrap errors/warnings, 59 lag frames.
Its first reported non-camera physics mismatch is row **7134 `sidekick_x`**,
ROM `$0175` / engine `$0176` (one pixel). The report is `complete=true`, and its
recent mismatch tail reaches row **14207**. The walk nevertheless fails because
the `giant_ring` exit boundary is not observed within its window. No downstream
campaign or exit success is claimed. Its close also reports unmatched direct Kos
#220/#221 and module #154/#155 completions at rows 10162/14172 and 10163/14173.
Those expected completions have no engine pending job; no readiness was fabricated.

The chain file is summary-only. **7134 is the first reported non-camera physics
row, not proof of the first error across every verification group**; its first
animation-error row and full span history are unavailable. Historical pass4's
29258 errors stopped at row 8525; comparing that total directly to 133771 over the
longer current window would misstate the result. The old BK2-71601 death-restart
stop is not reused as the current outcome; the current evidence is the complete
comparator window and missing owned exit. The exact final BK2 cursor/load generation
is not retained in this failure summary.

**Alternate observations.** One physics span, no animation/bootstrap errors or
warnings: row **26179 `rings`**, expected **72**, actual **0**;
`total_frames=26041`. This is the current Root composition's measured result,
not the historical 60-span alternate from the retained lane.

**HCZ slice observations.** **4353 error spans** = **3424 physics + 929 animation**,
zero bootstrap errors/warnings, `total_frames=29302`. The first error is row
**9482 `air`**, expected **1**, actual **0**, followed on that row by Tails air
and the two underwater/air status bytes. The first animation span is row 9486.
The independent `hczPoindexterOccupiesRomSlotBeforeTailsBounce` case passes.
The first edge coincides with the previously recorded boss-support gap, but
**4353 is not claimed identical to historical 3738**. No matched baseline/current
span attribution was run in this once-only qualification; the changed later span
count remains unqualified. No unrelated donor/graphics/physics fix was added to
change these totals.

The standalone reports also list unobserved advertised auxiliary schemas. The
HCZ/alternate list is cage state/execution, velocity writes, position writes,
Sonic position history, Tails CPU normal-step and CNZ cylinder state/execution.
Original AIZ additionally lists its ship-loop, boundary and transition-floor
observations. Zero report warnings or passing physics/animation controls do not
certify those missing auxiliary channels, native SST identity, pixels or audio.

### Hand-back boundary

The retained source composition, carrier/X-target lock, water-owner extraction,
actual restore/replay and fresh named source guards are complete within this
bounded slice. The three strict qualifications remain assertion-red as recorded;
the extra GLFW pixel oracle also remains unqualified. Neither a zero frontier nor
a generally green integration is claimed. Root owns follow-up on row 7134, the
missing HCZ exit, slice-span attribution, actual title archive submission and its
row-7126 load boundary, pixel verification and combined delivery validation.
Only local commits are produced; no broad smoke/full-suite, Main write, push or
retained-tree cleanup is performed. Consumed worker reports are removed after
inspection; the committed evidence and explicitly retained native images are the
handoff record.


Exact focused selector for invocation `9100` (common environment/ROM options
above, no trace/guards profile, `test -B`):

```text
-Dtest=TestSonic3kHCZEvents,TestSonic3kLevelEventRewindSnapshot,TestZoneEventRewindSchemaGuard,TestHCZHandLauncherObjectInstance,TestHczMinibossFightStartPhase,TestHczMinibossWaterEffectCleanup,TestHczMinibossVortexBubbleMotion,TestMegaChopperBadnikInstance,TestS3kMonitorContentsLifecycle,TestS3kMonitorGraphRewind,TestSonic3kMonitorContentOverride,com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance,TestRewindSchemaRegistry,TestDrownedPlayerMotionOrder,TestDrownedSidekickCpuPause,TestDrownedUnderwaterStatus,TestDrowningControllerMusicSelection,TestTraceCharacterState,TestModApiSignatureSurface,TestEveryObjectRewindRoundTrip,TestS3kHczHandLauncherGraphRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSecondStrikeCheckpointGameplay
```

The fresh XML explicitly includes HCZ events9, level-event snapshots38 and zone
schema guard18, all without failures/errors/skips. This verifies the newly named
schema checks actually executed; they were not silently profile-excluded.


`MAIN_WORKSPACE` denotes Root's main integration checkout;
`HCZ_TASK_ROOT` denotes this run's external `r2-recovery/hcz-gpt61-repair`
capture directory. The worker hand-back provides its concrete location.
