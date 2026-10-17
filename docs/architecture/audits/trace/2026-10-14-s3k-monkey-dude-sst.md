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


## 2026-10-17 — bounded GPT-6.1 load/title ownership followup

### Matched HCZ slice attribution before new production edits

The same owned tree temporarily detached to Root `dc338d3e3a30916559a32d4684ff46e9ad77492c` under its normal writer lease, then returned to `feature/ai-s3k-hcz-gpt61-repair` at `866e68501e33294c0024a903aa7d82c0aca79d82`. Only the pre-existing TraceChaser gitlink/checkout difference was present and preserved: actual checkout `4fdf73880f93a3172d23bf9e157b15de3c78fc32`, parent pin `4ae64e075d2e233539f5637fd16bd5ae7aa7d76e`. No Main or retained-lane source changed.

Exact command, from the owned cwd, with the original three absolute ROM properties from the preceding qualification:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u ALSOFT_DRIVERS EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl <original-three-absolute-ROM-properties> \
  -Ptrace-segments '-Dtest=TestS3kHczZoneSliceTraceReplay' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Baseline session 90300 finished 10:28:46 BST: **2 tests / 1 failure / 0 errors / 0 skips**, 406s queue, Maven 89s; **4699 spans = 3973 physics + 726 animation**. Current session 41102 finished 10:39:07 BST: **2/1F/0E/0S**, 498s queue, Maven 90s; **4353 = 3424 + 929**, reproducing the consumed prior result. The current executable digest remains `49d4f9091ed26cab4d777000f04a421469473eb617f36a2f65f97dda36ce6097`. The current replay was regenerated solely because the consumed report did not retain full span identities. Fixture/test/POM are byte-identical between the compared commits.

Both report `total_frames=29302`, maximum mismatch end row **31461**, no bootstrap errors or warnings. Both first report row **9482**: `air` 1/0 over 9482–9484, `tails_air` 1/0 over 9482–9485, `status_byte` 0043/0041 over 9482–9484 and `tails_status_byte` 0042/0040 over 9482–9485. First animation row **9486**, `player_animation_id` 0000/0005 over 9486–9488. The independent Poindexter test passes on both.

Exact equality over every reported span attribute gives **1132 common**, **3567 baseline-only** (2921 physics / 646 animation), **3221 current-only** (2372 / 849). Earliest baseline-only span: `camera_x` 0080/0082, rows **9902–10558**. Earliest current-only exact spans start **10677**: `tails_x_sub` 0C00/2C00 to 11019; `x_sub` FD00/EA00 and `y_sub` 1900/EB00 to 11243. Differing interval ends alone can change exact span identity. Comparing field/row mismatch coverage separately removes **80633** baseline field/rows and adds **51473** current field/rows. First newly mismatching field/row is **10834 `tails_cpu_respawn_counter` 0000/0001**; next is row **11028** `air`, `rolling`, `status_byte`, and `player_animation_id`. This is mixed, already divergent downstream behavior, not a claim that every repair improves the trace or that any native frontier is zero. Causality of those downstream deltas is not newly isolated here. Historical **3738** has no matched current-Root source/identity provenance and is not the regression baseline. Raw reports are consumed after this comparison, not archived.

### Independent old-red ownership proof

Before production changes, `TestS3kFreshTitlePlcLoadBoundary` drove a real fresh HCZ1 load and only two driver steps, without a BK2, trace/comparison rows or clock fitting. Corrected old-red session **34218**, 10:41:11 BST, 0s queue / Maven 25.590s: **4/3F/0E/0S**. Both drivers returned from load with **0 descriptors instead of HCZ1's 10**; live GameLoop's first title-loop step also left the head unprepared. The recording driver's first-step preparation, next-VBlank six-pattern drain and twice-restored job/queue replay passed. Initial session19291 had those three real failures plus a test-only raw-record/byte-array equality failure; it was corrected to the existing complete content comparator before this production change. The test retains full hardware job progress and ordinal checks.

Owning native source is pinned `sonic3k.asm`: Level `loc_6068`/`loc_60DA` submit `Load_PLC` (7614/7647), `loc_62B6` installs the owner (7766), then `loc_62CC` calls Process_Kos_Queue, Wait_VSync, Process_Sprites, Process_Nem_Queue_Init and Process_Kos_Module_Queue (7769–7781). Obj_TitleCardInit owns the four KosM submissions (62120–62166). Process_Nem_Queue_ShiftUp returns at the completed entry; no leftover pattern budget crosses entries (2251–2277). The bb8/ec1 narrative's implied cross-entry remainder is not the implementation contract. The actual correction separates Level's Nemesis submission from the title owner's KosM submission, and closes the live first title-loop tail without dispatching the owner's next routine twice. No readiness input, timer, physics, archive recipe, public API, snapshot shape or source budget is changed.

First implementation trial76745: 127/0F/76E/0S, 10:43:08 BST. The inserted step ran before InitGameModule and dereferenced its not-yet-created PLC service. This is an own implementation error, corrected by inserting immediately after InitGameModule before the profile adds its leading fixed-title scope. No test assertion or load gate was removed.

### Bounded followup qualification and limits

Frozen executable source after `866e68501`: **`8b1e770e6904e76fec9822267e2fc75ad994a8c1068c86d3bdeb7901c5920232`**, using the preceding sorted Java/POM/candidate-pin digest recipe. All following jobs use this same source. CLI EGL remains set, DISPLAY/WAYLAND_DISPLAY unset, Java25 and Lua5.4; `ALSOFT_DRIVERS=null` is used for these new runs, as requested. The earlier matched baseline/current pair deliberately retains its original unset-AL environment. Original absolute S1/S2/S3K ROM properties and the current absolute Main queue wrapper are passed from the owned cwd, with `--normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`.

- **Focused62978**, finished10:50:26 BST, queue322s / Maven84s: **134/0F/0E/0S**. Exact selector: `TestS3kFreshTitlePlcLoadBoundary,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestSonic3kLevelTitlePlcService,TestSonic3kLevelInitProfile,TestSonic3kTitleCardKosQueue,TestSonic3kTitleCardManagerRewind,TestS3kFreshTitleCardSstGraph,TestPlcLifecycleDriverParity,TestS3kFreshTitleCardLevelFadeIn,TestS3kFreshTitleCardCaptureLifecycle,TestInitialProcessSpritesCoordinator,TestInitialProcessSpritesPartialRewind,TestInitialProcessSpritesArmingEpoch`, then `test -B`. The loading selector executes both the game-level36 and tests-level7 cases; mandatory S3K four and the independent4-case old-red/green contract all execute. The archive wait unit fixture now explicitly starts Level's FIFO before its isolated title owner; all existing behavior assertions remain.
- **Guards49004**, finished10:52:10 BST, queue28s / Maven35.645s: **131/0F/0E/0S**. `-Pguards '-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestZoneEventRewindSchemaGuard' test -B`. Source72, rewind architecture4, helper1, rewind coverage1, zone schemas18, invariant10, timing authority25. No budget or allowance changed.
- **Canonical load-prefix10857**, finished10:53:22 BST, queue0s / Maven48.882s: **1/0F/0E/0S**. `-Ptrace-replay '-Dtest=TestS3kSonicTailsCompleteEmeraldRunPrefix#aiz1ThroughHczLoadBoundary' -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B`. It drives the first row of segment9; completed comparison reports for segments0/2/4/6/8 each have **0 physics / 0 animation / 0 bootstrap / 0 warnings**. Segment8's full declared7175-row reference window is complete, laggedFrames62: the prior four row**7126** position/camera mismatches are gone. This is a new bounded prefix pin, not a full canonical run or an all-auxiliary claim.
- **Alternate69802**, finished10:53:58 BST, queue48s / Maven35.609s: **1/1F/0E/0S**. `-Ptrace-segments '-Dtest=TestS3kAizZoneSliceTraceReplay#replayMatchesTrace'` with the same three fork/order flags and `test -B`. The distinct original BK2 `_movies/s3k-complete-sonic-tails.bk2` SHA256 is `82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`. Its only span remains **26179–26179 `rings` 72/0**, physics1, animation/bootstrap/warnings0; `total_frames=26041`, declared reference26228 rows. The inherited early ring publication at this separate boundary is unqualified; the test is not weakened.

Actual native corroboration, session**43873**, used official BizHawk2.11/GPGX and the original canonical BK2 hash recorded above, no setup writes. External `$HCZ_TASK_ROOT/native-title-load/native_frames.py` compiles the owned C# observer under the intended-tree writer plus build-activity lease, releases both, then executes once under normal admission with inherited descriptors. No outer activity shim or foreign signal. Execution admission wait0s; compile/execution duration was not separately measured. It completed at53562 with **79 continuous CPU observations**,53484–53562, and seven actual320x224 published PNGs, all inspected. Retained source hash `58241846513cdb137c036a3973b9c09047d8ad2c49e2cbb2b42da624890a2d4a`; actual TraceChaser checkout remains4fdf7388. Temporary executable removed; source, CSV, input hashes and useful PNGs retained in that external task directory.

| Completed hardware frame | Native CPU observation after the frame |
|---|---|
| 53487 | GameMode8C;10 Nemesis entries,0 prepared patterns; P1=0,0; no title KosM parents. This captured frame is lagged. |
| 53488 | Same10 entries;56 patterns prepared; all four title parents present. |
| 53489 | 50 patterns remain after the next real title VBlank. |
| 53559 | FIFO empty; P1=0280,0020 and camera=01E0,0000; LoadLevelLoadBlock has submitted the terrain parents. |

These are completed-frame counts, distinct from the fixture's zero-based BK2 input indices. Committed segment8 has offset46432; row7126 is input index53558, completed frame53559. This is input-stream identity, not a fitted clock. Published pixels and CPU end state are separate products; native PNGs do not certify engine pixel equality, same-frame GPU/CPU state, or other configurations. The short production regression plus real BK2 prefix establishes the causal runtime ownership fix.

**Stopping boundary:** one evidenced load/submission/first-loop fix, focused and schema/authority proof, a clean affected canonical prefix and unchanged strict alternate frontier. Returned HCZ segment11/full canonical/HCZ full slice were not requalified on the new source; prior b9/866 counts are not transferred. The matched pre-fix slice delta is mixed, not an all-fields improvement. The exact `TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels` GLFW failure remains unqualified; Root's different batch66 test identities do not attribute it. No broad smoke/full suite, new tree/children, Main/Root writes, integration or push. Root's later f8f08a5bd composition is separate. All existing requests are terminal and reports are consumed after recording these summaries.


## R3 current composed strict qualification — 2026-10-17

Owned branch `feature/ai-s3k-hcz-gpt61-repair`, worktree `t3-3aa3bc3c`.
Merge **21327d78ad7f5455df3e83c9d0c0f55201e0cf96** combines760b2005d with
published Main **460715cbca03d81b4f289051ccb9779cdc8ff5be**, under the normal
intended-tree writer lease. Four prose conflicts were reconciled by preserving
both contributions: the release fold, S3K discrepancy appendix, append-only Plan
and coverage backlog. No Java conflicts. Root's immutable white-fade session
binding, player mapping access, fresh-title/DPLC APIs and diagnostic instruction
ledger remain intact. No later passive Root commit was adopted.

The tested executable digest is
**8b2bb7e6e6bc297554c97501fa1051555bbdd20e018e6da4b6f95a04a9d7048d**,
using the preceding sorted Java/POM/signature-pin recipe. The actual clean
TraceChaser checkout remains4fdf73880f93a3172d23bf9e157b15de3c78fc32 against
parent gitlink4ae64e075d2e233539f5637fd16bd5ae7aa7d76e; it was neither updated
nor staged. All executable inputs stayed frozen through both qualifications.
Original S1/S2/S3K SHA1 identities were verified; no fixture or native source changed.

Exact common command prefix, from the owned cwd, with the three variables bound
to the original absolute ROM files and `$MAIN_WORKSPACE` to the Main checkout:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM"
```

Append exactly one of the following selections, followed by
`-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B`:

- Canonical3815: `-Ptrace-replay '-Dtest=TestS3kAizTraceReplay#replayMatchesTrace,TestS3kSonicTailsHczSegmentTraceReplay#replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds'`.
- Independent HCZ90977: `-Ptrace-segments '-Dtest=TestS3kHczZoneSliceTraceReplay'`.

Both requests were submitted once after checking existing own jobs; one observer
per session, normal resource admission, no retry, hold, reservation or override.
Queue intervals overlap and are not additive elapsed time.

### R3 completed windows and all reported groups

**Canonical3815**, finished11:15:49 BST: **6 tests /1F/0E/0S**, queue287s,
Maven1m55s. All three prefix methods, standalone AIZ and initial HCZ pass. The
additional prefix method is760's HCZ load-boundary pin; this is six tests, not the
old five-test selection. Standalone AIZ consumes declared rows0–20797 with20463
comparison records, zero physics/animation/bootstrap/warnings. Initial HCZ consumes
rows0–3573 with3519 comparison records and the same zero groups.

The full chain completes the comparator for every reached ordinary segment:

| Segment | Native reference rows consumed | Physics errors | Animation errors | Lagged frames |
| --- | --- | --- | --- | --- |
| 0 aiz | 0–2289 (2290) | 0 | 0 | 0 |
| 2 aiz_2 | 0–4022 (4023) | 0 | 0 | 11 |
| 4 aiz_3 | 0–8234 (8235) | 0 | 0 | 14 |
| 6 aiz_4 | 0–4102 (4103) | 0 | 0 | 16 |
| 8 aiz_5 | 0–7174 (7175) | 0 | 0 | 62 |
| 9 hcz | 0–3573 (3574) | 0 | 0 | 55 |
| 11 hcz_2 | 0–14207 (14208) | **115689** | **19021** | 59 |

Bootstrap and warnings are zero in all these reports. Segment11's total is
**134710 field mismatches**, not4353-style deduplicated spans. First reported
**non-camera physics** is **6674 `rings`, native114 / engine0**. The five-entry
recent ring ends at14207 and is not a full span history. This summary does not
publish the first camera or animation error, or the exact number of ordinary
physics-comparison rows after phase filtering; do not infer those from the
reference-window length or lag count. `complete=true` proves consumption of the
represented window, not observation of its requested exit. The giant-ring exit
is absent, so the walk fails independently. The11-gap dynamic-art report has
zero gap failures; that does not certify all special-stage physics or auxiliary
schemas. The close-time exception retains four unmatched recorded completions:
direct#220 at10162 PRE_MAIN_LOOP, module#154 at10163 POST_OBJECTS, direct#221
at14172 PRE_MAIN_LOOP and module#155 at14173 POST_OBJECTS, engine pending none.
Their first causes and the later campaign remain unqualified. No new stop BK2
cursor is published; the historical71601 cursor is not transferred.

**HCZ90977**, finished11:13:52 BST: **2/1F/0E/0S**, queue193s,
Maven about91s. The full31482-row reference window0–31481 is driven without a
frontier/prefix override. Report `total_frames=29302` counts comparison records,
not the last row. **4353 spans =3424 physics +929 animation**, bootstrap/warnings0;
maximum mismatching row31461. First physics:9482–9484 `air`1/0, with
9482–9485 `tails_air`1/0 and both status bytes diverging on the same edge.
First animation:9486–9488 `player_animation_id`0/5. Independent
`hczPoindexterOccupiesRomSlotBeforeTailsBounce` passes. The complete sorted error
array SHA256 is
**71f91e9494d9a6926cd2f032dcc174a0d289355d9f9a6658b3752ae61d96d251**
(`sha256(json.dumps(errors, sort_keys=True))`), identical to the earlier4353
result. This is exact span reuse evidence, not just equal totals.

The prior matched dc338/866 pair remains **4699 versus4353 with mixed deltas**:
1132 common exact spans,3567 baseline-only,3221 current-only; the first added
field/row is10834 `tails_cpu_respawn_counter`0/1. Same29302 comparison records
and31461 maximum mismatch row. Historical3738 is unmatched. R3's nullAL runs do
not rewrite the pair's original unset-AL environment or turn its mixed result
into a regression-free claim.

Alternate69802 on760 is reused within its stated scope: one26179 `rings`72/0
span, physics1/animation0/bootstrap0/warnings0,26041 comparison records over
declared26228 rows. No post-merge alternate pass is claimed. Static reconciliation
found no changed AIZ/HCZ gameplay owner in Main460: the level-profile additions
are diagnostic instruction inputs, the special-stage renderer is private, and
the immutable white-fade binding does not change this RecordingFrameDriver's
black-fade path. The full HCZ span array also remains identical. No source change
was made in response to these results; an affected runtime fix would invalidate
this reuse decision.

### R3 rejected fresh-load attribution and stopping boundary

The first candidate was `FreshLevelTransitionBoundaryController.publishInitial`:
it publishes destination rings with Get_LevelSizeStart's players, while
`complete` publishes them only if initial publication did not occur. Native
Level/loc_6310 calls Get_LevelSizeStart before LoadLevelLoadBlock's actual
Wait_VSync loop; **loc_63B4 clears Ring_count after that loop and j_LevelSetup**,
with the Last_star_post_hit branch preserving the checkpoint owner's choice.

Before any production edit, a temporary two-act independent semantic test loaded
HCZ with37 source rings, called the real partial publication and expected37 until
completion, then captured both boundaries for twice-restored completion/job-ordinal
replay. Session**70863**, exact selector
`TestS3kFreshTitlePlcLoadBoundary#playerAssemblyRetainsSourceRingsUntilTerrainCompletesWithRewind`,
normal common prefix above without a trace profile, `test -B`, finished11:19:32
BST after13s queue and25.843s Maven: **2/2F/0E/0S**, both expected37 / actual0
at partial publication. It did not reach its downstream rewind assertions. This
is an unqualified separate fresh-load phase defect, not proof of the current
canonical cause. The uncommitted trial was removed; the four previously committed
load/archive checks are byte-identical. No production fix or new green is claimed.

Kill evidence for that attribution: committed hcz_2 rows6672–6679 retain114 rings,
P1 at0116,06EC and P2 at00F6,06F0; gameplay_frame_counter advances19DE–19E5.
Native rings become0 on6680 while the same players remain. This is an active
level/in-place transition, not the held zero-player fresh-load boundary. Thus
loc_63B4's otherwise real ordering defect cannot be substituted for this cause.
The candidate in-level owner is **Obj_TitleCardWait/loc_2D810**, whose ring/timer
reset requires the preceding higher-slot children's $34 movement latch to stay
clear. Current results source only gives the literal SST child graph to the AIZ
handoff (`nativeTitleChildrenOwned = aizAct1MinibossTitleHandoff`); other retained
handoffs still use the manager/external presentation and dispatch-reset policies.
That static gap is not yet a matched live engine/native parent-and-latch proof.
No six-dispatch adjustment, forced readiness, zone/row condition or title-counter
fit is justified. The next bounded investigation must identify the actual HCZ
retained title parent's create/Wait dispatch, child birth slots and $34 latch on
both products before changing its owner. First non-camera rings6674 remains
explicitly unqualified; the current report cannot independently pin first animation.

The slice9482 edge is also not certified as “boss deletion clears support”. Native
interact pointers are P1 slot47 and P2 slot28, both with standing-on-object bit3
clear before the edge; those pointers persist across it. BossDefeated_StopTimer
stops the HUD timer, awards score and clears the boss render flag, without a
player-air write. No forced airborne patch was made from these stale pointers.

**Bounded outcome:** the requested full canonical and independent full HCZ
qualifications are complete and strict-red on the Main460 composition. They do
not certify returned HCZ exit, campaign completion, all auxiliary fields, PAL,
PCM, other presets/donors/teams or six-ending/46-fingerprint timing. Exact
`TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels`
GLFW initialization remains unqualified; Root's different EGL test identities
are not its baseline. No changed visual behavior or new native pixel product;
previous useful native images remain retained. No broad smoke/full suite,
children, new tree, Main/Root write or push. All own jobs are terminal; only
append-only evidence changes follow merge21327. Root owns delivery and integration.


## R4 retained title parent and child movement latch — 2026-10-17

One bounded ownership correction after98e2c6029, same owned tree/branch and
Main460 composition. Main467's passive follow-up was not adopted. No Main/Root,
retained-lane or TraceChaser writes; no new worktree/children. Frozen executable
Java/POM/signature digest for all green checks and both affected qualifiers:
**e49c68da90e35b1bb8173ba57053464cefa9e5dbf273715f754cd0e453e01773**.

### Native parent and ordering evidence

R3's near-player aux rows did not expose the title parent or its $34 movement
word. One passive diagnostic on the original canonical BK2 observes the actual
instructions instead: results replacement loc_2DD06 ($2DD06), Obj_TitleCard
wrapper ($2D690), Wait's $34 test ($2D804), loc_2D810 reset gate ($2D810), and
horizontal/banner `st $34(a1)` ($2D996/$2D91C). Original-ROM bytes at every hook
are verified before execution, including the wrapper, test/clear/return sequence,
reset test and both latch stores. No native setup writes or engine hydration.
S3K SHA1 remainscfbf98c36c776677290a872547ac47c53d2761d6; original movie SHA256
ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0.
Actual clean native checkout remains4fdf73880f93a3172d23bf9e157b15de3c78fc32.

External `$HCZ_TASK_ROOT/r4-title-parent/native_title_parent.py` compiles its
C# observer with current Main's intended-tree writer and shared build-activity
lease, inheriting the descriptors. It releases those leases before one normal
resource-aware admission and inherits that execution lease. No unleased JVM or
native helper, outer activity shim, resource override, foreign signal or retry.
Native PID2170922 is terminal. Execution admission wait0s; UTC execution bounds
**2026-10-17T10:28:19.987470Z–10:29:16.324485Z**; compilation duration unknown.
Exact expanded argv is retained in external `native-inputs.json`; with the
existing original-ROM, owned-worktree and external-task-root bindings it is:

```bash
mono "$HCZ_TASK_ROOT/r4-title-parent/NativeTitleParent.exe" "$S3K_ROM" "$HCZ_WORKTREE/src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2" "$HCZ_WORKTREE/src/test/resources/traces" "$HCZ_TASK_ROOT/r4-title-parent"
```

The temporary executable is removed. Source SHA256
**8b46ca152e63805db5c79a4397d87e56e857a31aaf0a6f40a97daa4148d24aa2**,
163 instruction events,206 parent/child state records, inputs and five actual
published320x224 PNGs remain in that task directory. Callbacks are armed only
for the bounded completed-frame window69660–69765; the original input prefix is
fast-forwarded once, without producing a new chain trace or capture sweep.
All five PNGs69746/69750/69755/69756/69765 were inspected. Published HUD digits
and CPU writes are separate products:69756's CPU reset is not a claim that the
published HUD had already consumed it;69765 visibly has zero rings/time.

| Native completed frame | Actual retained parent/child event |
| --- | --- |
| 69716 | loc_2DD06 changes results slot15 ($B456) into Obj_TitleCard and returns. |
| 69717 | Same slot15 runs Init, then polls Create until the real global KosM FIFO permits it. |
| 69727 | Four children in slots16/18/19/20 move on their Create scan: X0250/02EC/0334, banner Y0020. Parent $34 isFF00. |
| 69750 | Native rings114; act child still moves at X01C4 toward0184. This is canonical row6674. |
| 69754 | Act child's28th $10 move reaches0184 and still writes $34. |
| 69755 | Lower-slot Wait seesFF00, clears $34 and returns; rings remain114. |
| 69756 | Following stationary parent poll sees0, executes loc_2D810, resets rings/time/air and advances routine4→6. |

The72 horizontal movement writes are the literal table's20+24+28 moves; banner
writes11 times. This is native geometry and dispatch order, not a six-frame
correction. The fixture's offset63075 maps completed69750 to zero-based row6674
and69756 to6680. No fitted clock is introduced.

### Independent old-red and correction

The production HCZ reload preserves the ring/time globals and the results SST,
but formerly requested a zero-additional-dispatch manager policy. Results exit
initialized an overlay on its next pass, then deleted its physical parent;
HCZ never entered the existing literal title-child graph. The manager's24/phase
count reset rings while the native act child was still moving. Changing fresh
Level/loc_63B4 cannot repair this active-level owner.

Initial independent old-red86962,11:33:17 BST, queue119s/Maven27.358s:
**1/1F/0E/0S**, expected retained TITLE_CARD_INIT, actual RESULTS. To avoid
expanding other explicitly counted retained transitions, the final test requests
the already-existing `TitleCardResetGates.NATIVE_WAIT_GATE` semantic. Final
pre-production selection48402,11:33:58 BST, queue0/Maven24.660s:
**3/3F/0E/0S**. Missing retained INIT plus two independent -1/0 failures:
the real HCZ request and request normalization/carry policy. Exact selector:
`TestS3kRetainedTitleMovementLatch,TestLevelSeamlessTransitionExecutor#nativeWaitGateSurvivesNormalizationAndCarriedOwnerPolicy,TestSonic3kHCZEvents#act1TransitionKeepsTheArenaXLockUntilTheCarrierReleases`.

One correction:

- HCZ requests the native movement gate. Request normalization and the immutable
  carried policy preserve that existing sentinel instead of clamping it to zero.
- A carried publication owner receiving that gate retains its SST and uses the
  already-implemented literal child Create/Wait graph. Other explicit dispatch
  policies keep their existing behavior; no game/zone/row/fixture test is added
  to shared runtime logic.
- The retained owner keeps the request's control-lock ownership. Existing Wait2's
  90, stagger and retirement semantics are untouched; no new fields, budget,
  public signature, API pin, readiness input or archive recipe is introduced.

`TestS3kRetainedTitleMovementLatch` is independent of BK2 rows. A declared
post-results component boundary leaves one real occupied higher-slot hole;
Create allocates41/43/44/45 and every child moves on that same actual manager
scan. The final $10 move leaves rings17, the next parent clear leaves17, and
only the following parent poll clears them. Complete registry snapshots at
birth and latch-clear restore twice with in-place object reuse disabled and
forward replay to the same reset state. Every registered world key is compared,
including object identity/cursors/exact parents, title state and hardware
jobs/ordinals. This proves recreation and replay of this local boundary, not
cross-load timeline isolation or a cold-route product.

### R4 finite validation and resulting frontiers

All Maven commands use the R3 exact normal common prefix: current absolute Main
wrapper from the owned cwd, original three absolute ROM properties, Java25,
CLI surfaceless-EGL, nullAL, Lua5.4, DISPLAY/WAYLAND_DISPLAY unset. No sources
changed while any request was queued/running. No broad smoke/full suite.

- **Focused65721**,11:35:41 BST, queue0/Maven1m24s: **182/0F/0E/0S**.
  Exact `-Dtest`: `TestS3kRetainedTitleMovementLatch,TestLevelSeamlessTransitionExecutor,TestSonic3kHCZEvents,TestS3kAizResultsNativeTitleGraph,TestS3kResultsScreenObjectInstance,TestFbzResultsTitleAndAct2Sizes,TestS3kResultsKosQueueAndChildren,TestS3kResultsKosQueueRewind,TestSonic3kTitleCardManagerRewind,TestSonic3kTitleCardKosQueue,TestS3kMgzLbzCarriedResultsTitleOwnership,TestS3kHeadlessInLevelTitleCardProgression,TestS3kTransitionWriteSupport,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kFreshTitlePlcLoadBoundary,TestS3kFixedSstTransitionRewind,TestLevelTransitionCoordinatorPeeks,TestGameLoopFreshLevelHandoff`, then `test -B`.
  Mandatory S3K four execute, including both36- and7-case loading classes.
- **Guards22781**,11:36:17 BST, queue68s/Maven34.859s: **131/0F/0E/0S**.
  `-Pguards '-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestZoneEventRewindSchemaGuard' test -B`.
  Source72, rewind4, helper1, coverage1, zone18, invariant10, authority25; no allowance or budget change.
- **Canonical2015**,11:37:30 BST, queue0/Maven50.562s: **1/1F/0E/0S**.
  `-Ptrace-replay '-Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds' -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B`.
  Reached ordinary segments0/2/4/6/8/9 have complete zero physics/animation/bootstrap/warning reports,
  with the same2290/4023/8235/4103/7175/3574-row reference windows and0/11/14/16/62/55 lag counts as R3.
  Returned HCZ11 remains complete over **0–14207 (14208 rows)**, lag59,
  **133771 field errors =114800 physics +18971 animation**, bootstrap/warnings0.
  First reported non-camera physics is now **7134 `sidekick_x`0175/0176**:
  R3's6674 ring frontier is closed. The summary does not expose first camera or
  animation error, full span history, or an exact post-filter physics-row count.
  Requested giant-ring exit remains absent, and the four late unmatched direct#220/#221,
  module#154/#155 completions remain. Eleven dynamic-art gaps report0 failures;
  this is not all-auxiliary/special-stage physics certification. R3's unchanged
  standalone/prefix cases were not rerun; this one affected chain is a new measured result,
  not transferred historical133771 credit.
- **Independent HCZ65594**,11:38:09 BST, queue50s/Maven38.102s: **2/1F/0E/0S**.
  `-Ptrace-segments '-Dtest=TestS3kHczZoneSliceTraceReplay'` with the same three
  fork/reuse/order flags and `test -B`. All31482 reference rows,29302 comparison
  records; **4353 spans =3424 physics +929 animation**, bootstrap/warnings0,
  first9482–9484 `air`1/0, first animation9486–9488 player_animation_id0/5,
  maximum mismatch row31461. Independent Poindexter passes. Entire sorted error-array
  SHA256 remains **71f91e9494d9a6926cd2f032dcc174a0d289355d9f9a6658b3752ae61d96d251**,
  so there is no new compared-field span on this distinct original BK2. Its earlier9482
  divergence remains; this does not prove a second clean execution through the repaired owner.

No unchanged request was duplicated. Alternate760's one26179 ring span is reused:
AIZ already selects the same literal graph, and no AIZ caller adopts the newly
preserved carried native-gate request in this fix. No fresh alternate pass is
claimed. Matched dc3384699/8664353 remains mixed downstream evidence; historical3738
is unmatched. The initial exact GLFW oracle still lacks a matching identity baseline;
these actual native title images do not certify engine pixel equality or other products.

**Stopping boundary:** one ROM-owned retained-parent/latch correction with short
old-red/green, complete-world replay, focused182/guards131 and concrete new canonical
frontier7134; independent strict slice remains4353/9482. No second fix or sweep.
Fresh-load ring publication remains a separate unlanded finding. Missing returned
HCZ exit, sidekick7134, support9482, full auxiliary coverage, configuration breadth,
PAL/PCM and broader timing acceptance remain unqualified. All own jobs are terminal;
consumed diagnostics are removed under current Main's worktree_metadata_slot alone.
Root owns review/integration/broad validation and push. Local only.


## HCZ7134 coupled CPU and ground-wall decision — 2026-10-17

Test-only follow-up on3896deae811e5b12732a1ff4017e599263339105, same owned
feature tree. Production, fixtures, clocks, source budgets and API pins are
unchanged; actual clean TraceChaser4fdf checkout difference is preserved. No
prior own job was live. No trace/native/capture/broad qualification was launched.

Parent-relayed read-only native analysis identifies7133 P2 X0173.8800,
Y0830.1F00, vx/ground026A, status40;7134 X0175.F800, vx0170, ground0,
status60. The actual native path is loc_13E34's immediate +1, underwater +6,
sub_F61C's projected probe0180/0838 returning-1, then loc_14BCA subtracting0100
and setting Push before integration. This slice corroborates the owning source
in locked-on `sonic3k.asm`; it does not recapture that native observation.

`TestS3kCpuFollowGroundWallPipeline` adds one two-case, one-tick independent
regression. A declared tile-aligned wall W0400 and full floor F0300 use actual
SolidTile/GroundSensor geometry. Tails' real radii9/15 put the initial centre at
(W-13).C000,F-16, with underwater ground velocity01F0. Ordinary leader-history
writes through `recordFollowerHistoryForTick` supply held Right and delayed
target delta1 or0 after loc_13DA6's slow-leader $20 offset. Real routine-0
initialization and `SpriteManager.update` dispatch the CPU and movement; no
forced CPU state, nudge, collision distance or replay values. A passive wrapper
around the real wall response observes the actual first terrain query and
pre/post velocity. A floor distance0 sanity check validates the setup.

| Delayed target delta | CPU nudge | Accelerated vx/g | Actual projected probe | Wall response vx/g/Push | Integrated X |
| --- | --- | --- | --- | --- | --- |
| 1 | +1 | 01F6/01F6 | W,F-8 | 00F6/0/true | (W-11).B600 |
| 0 | 0 | 01F6/01F6 | W-1,F-8 | 01F6/01F6/false | (W-11).B600 |

The full-tile wall/previous-empty calculation returns-1 at W; the empty-cell
extension is flush0 at W-1. Both final X values are identical, so position alone
cannot diagnose this ordering. The regression checks the CPU result, actual
probe, response, final velocity/ground/Push and grounded integration.

Exact focused invocation, using the existing original absolute ROM and Main
workspace bindings from R3 above, from this owned cwd:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kCpuFollowGroundWallPipeline,TestS3kHcz1GroundWallProbe,TestSidekickCpuFollowParity#s3kFreshSpawnInitFrameResetsKinematicsWithoutRunningNormalCpu+followRightStillNudgesPositionWhenDxIsBelowThreshold+followNudgeIsSuppressedWhileObjectControlBitZeroIsSet' test -B
```

Initial request56102 finished13:08:43 BST, queue0s/Maven25.744s,7/2F/0E/0S.
Both new cases saw empty terrain because the test stubbed the three-argument
solid-tile accessor while GroundSensor uses the two-argument overload. CPU and
probe assertions already passed; this is a test setup error, not a runtime
old-red. Correcting only that stub and adding the floor sanity assertion gives
request86494,13:09:42 BST, queue0s/Maven25.296s: **7/0F/0E/0S**. New coupled
cases2, existing ROM terrain checks2, existing CPU initialization/nudge/control
checks3; no skips. New cases take0.648s together. Both requests are terminal.

This disproves the generic shared-order hypothesis for these conditions and
establishes no production defect. Canonical7134 remains unqualified: the
retained report supplies only integer actual X0176. The next causal decision
requires the **live engine** CPU branch/delayed target/nudge, full16.16 pre-wall
X/Y and vx/yv/ground/angle/control path, and its actual probe/distance plus
terrain descriptor, solidity bits, collision-table tile/flip/width at0180/0838.
Compare these to the native branch before assigning CPU, acceleration, wall,
terrain, or integration ownership. The synthetic result does not establish any
of those live values. No new canonical/slice/alternate result or frontier move,
no HCZ certification or exact GLFW oracle claim. Existing7134sidekick_x and
independent9482air remain; no runtime fix is justified in this bounded slice.


## R6 actual HCZ7134 CPU and wall input witness — 2026-10-17

Own feature tree fast-forwards under its normal intended-tree writer lease from
b8ff76c5c to Root86171c6b87f1bb9d3392ebd5036c4d0eb4071d71. All prior own
jobs were terminal. Actual clean TraceChaser4fdf7388 remains untouched against
the new322851cb gitlink. R1–R5 identities/results are preserved separately;
R6 is `s3k-gpt61-hcz-7134-live-wall-input-witness-20261017-r6`, ordinal6.

One temporary `TestS3kHcz7134LiveWallWitness#canonicalThroughHcz7135` subclasses
the existing chain harness and calls its ordinary
`assertChainReplayThroughSegmentRow(...,11,7136)`: original BK2, bootstrap,
input and lag admission, starting at segment0, stopping after row7135. The
existing slot-probe callback arms at row7131. CPU and collision proxies delegate
every call to the actual owners and wrap only CPU.update and the P2 ground-wall
call. They sample before/after, then restore the original bindings. No gameplay
values, fixtures, physics, readiness, clocks, ROM bytes or auxiliary reference
are modified; no fake geometry, positioned state, reseed, runtime/API widening,
new native/media capture, variant, alternate, guard or broad run.

Observer source SHA256
47f7cfbdaa5ac6ffe869821d7fe91cfe4c8ecb59e099661979658c21ae31028f.
It reads the actual NormalStepDiagnostics, leader/history buffers, sprite state,
production probe descriptor and call-boundary clocks. Projected coordinates below
are derived from those actual full16.16 inputs using the production descriptor;
they are not a direct scanWorld callback. The foreground terrain queries are
additional read-only lookups, not a substitute response. The observer deliberately
does not label the pooled foreground result as the selected result when BG is
active. Temporary source/classes and consumed diagnostics are removed after this
summary; this small observation can be reconstructed from the existing seams.

### Single invocation and bounded comparison

Current absolute Main wrapper from the owned cwd; the original three absolute
ROM bindings are the same as R3/R5. Exact command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  -Ptrace-replay '-Dtest=TestS3kHcz7134LiveWallWitness#canonicalThroughHcz7135' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Request56073 is terminal: queue0s, Maven1m39s, JUnit23.886s, finished
**2026-10-17 13:22:30 BST /12:22:30 UTC**,1/0F/0E/0S. This is successful
bounded observation, **not parity green**: an intentional prefix stop does not
close/assert the current comparator. Capturing it before detachment reveals
**217 field mismatches =20physics+197animation**, warning/bootstrap0, lag53,
`complete=false`, consumed7136 of14208 reference rows (0–7135). The first
non-camera physics error remains **7134 sidekick_x0175/0176**. At7132 there
were already206 errors (11physics+195animation), with no non-camera physics
mismatch; the retained camera entries are6790/6791/6792/6947/6948. This recent
buffer does not establish the first animation or first camera mismatch globally.
At7134 the increment is4physics+1animation;7135 adds5physics+1animation.

Six earlier ordinary windows complete with zero physics/animation/bootstrap/
warning errors: seg0/2/4/6/8/9 consume2290/4023/8235/4103/7175/3574 reference
rows; lag0/11/14/16/62/55 respectively. Prefix gap record has11 recorded gaps,
0 reported failures; incomplete later structural gaps are deliberately not
verified. These bounded counts do not replace R4's full14208-row/133771 total,
certify a route exit, or qualify the independent HCZ slice/alternate/GLFW oracle.

### Actual clocks and P2 pipeline

Each row association is the harness's pre-production comparator cursor passed to
its post-step slot callback. No fitted clock offset or replay state injection.
The metadata declares BK2 offset63075. `announcedMovieRow` is the existing
driver's cursor+1 convention; the after-step cursor has consumed that row.

| Trace/CSV row | BK2 cursor before → after /announced | Engine VInt_run_count | LevelManager LF /SpriteManager /CPU argument | ObjectManager frame preCPU → postrow | Chain stepEngineFrame ordinal |
| --- | --- | --- | --- | --- | --- |
| 7133 (CSV1BDD) | 70208→70209 /70209 | 69065 (10DC9) | 7081 (1BA9) | 946→947 | 69065 |
| 7134 (CSV1BDE) | 70209→70210 /70210 | 69066 (10DCA) | 7082 (1BAA) | 947→948 | 69066 |

The native CSV LF is likewise1BA9/1BAA; its separate `vblank_counter` column is
0FDC/0FDD, not an observed native power-on VInt longword. The chain step count
counts this diagnostic's completed `stepEngineFrame` calls, not every possible
engine dispatch. Its preCPU/prewall value is completed+1 for the current call;
the postrow helper ran after the increment and printed the next ordinal instead
(69066/69067). The table uses the actual in-call event count, with no timing fit.

P2 Y is0830.1F00 and y velocity0 throughout both observed ticks. Angle0,
radii9/15, grounded GROUND path, underwater, facingRight, not rolling/sliding,
OnObj false (also false at frame start), move_lock0/object_control0, no tunnel,
stick-to-convex or wall suppression. Actual top/lrb bits are **0E/0F**,
loop-low-plane false, acceleration6 and logical Right08. Native layer bytes
were not provided by the relayed native witness, so their equivalence is unproven.

| Row | Actual phase | Centre X | vx /ground | Push |
| --- | --- | --- | --- | --- |
| 7133 | preCPU | 0170.1E00 | 0264/0264 | false |
| 7133 | postCPU | 0171.1E00 | 0264/0264 | false |
| 7133 | prewall → postwall | 0171.1E00 | 026A/026A unchanged | false |
| 7133 | postrow | 0173.8800 | 026A/026A | false |
| 7134 | preCPU | 0173.8800 | 026A/026A | false |
| 7134 | postCPU | 0174.8800 | 026A/026A | false |
| 7134 | prewall → postwall | 0174.8800 | 0270/0270 unchanged | false |
| 7134 | postrow | **0176.F800** | **0270/0270** | **false** |
| 7134 | relayed native postrow | **0175.F800** | **0170/0** | **true** |

The latest CPU record is NORMAL, branch `leader_fast`, delay16, held/pressed08,
recorded/push-bypass status44, dy1, generatedRight08, immediate nudge+1,
object_control0 and pre/post status40, no jump press or skipped steering. At7133,
history slot19 contains0559/0831, dx03E9;7134 slot1A contains0560/0831,
dx03ED. Raw position lookback16 and recorded-slot target agree. The current
leader g0750/074D exceeds0400, so no slow-leader $20 target bias is applied.
Postphysics diagnostic capture is true and agrees with the observed final state.

The production probe descriptor is modeC0/rotationC0, Right, offset+10/+8.
At7133 its live inputs project to **017D/0838**;7134 projects to **0180/0838**,
matching the native probe location. Ground-wall call eligibility is true.
Foreground descriptors at7134 are005D at0180,005C at0170,0058 at0190, no flips;
all have the live lrb bit0F clear and return no solid tile for that path. **BG
collision is active** at each observed call. No selected BG terrain/metric,
Camera_X/Y_diff, translated BG probe, row mask, or exact returned distance was
recorded. Public CollisionTrace has no CalcRoomInFront-result event. This
observer's FG-only pooled-result qualification therefore did not expose the
selected result; this is an observation limit, not proof that test-local BG
sampling would require a production API change.

### Causal handback

CPU nudge, acceleration, full-position projection and integration are now live
witnessed. The earliest demonstrated missing effect is inside the wall-call
boundary: native loc_14BCA consumes-1 and changes0270→0170/ground0/Push, while
the engine leaves0270/ground0270/Push clear before integration. Source eligibility
and the lack of any change indicate a nonnegative selected result on this path;
the exact distance was not observed and is not asserted as0. The BG-vs-FG
selection uses BG on equal/nearer signed distance (`GroundSensor.scanWorld` /
`combineDualPlaneResults`). BG transforms/selected terrain and native-vs-engine
layer bytes are the remaining inputs needed to distinguish provider timing,
plane translation/row-mask, terrain lookup and distance/selection ownership.
No CPU/order, collision-response constant or runtime fix is justified here.
No second invocation or new qualification was launched. Main/Root and foreign
native/performance artifacts remain untouched; only this evidence is retained.


## R7 native inventory and partial selected BG witness — 2026-10-17

Same feature tree at7863745b6, runtime source unchanged from86171c6b8. R6
and all earlier evidence are preserved; actual clean native4fdf remains against
gitlink322851cb. No Main/Root/oldlane writes, native/media, variants, alternate,
guards, broad qualification or production/API change. Exactly one engine request
was submitted. **The requested actual7134 selected-result witness remains
incomplete: an observer assertion ended the attempt after7133. No retry.**

### What native evidence already contains

The complete committed `hcz_2/aux_state.jsonl.gz` stream was read before authoring:
18 event kinds, including376 state snapshots,14208 CPU/sidekick-interact records,
28416 character interact records and1178 slot dumps. Its only collision/layer
keys are `top_solid_bit`, `lrb_solid_bit`, `primary_collision_addr` and
`secondary_collision_addr`. No raw BG collision flag, camera diffs, layout mask,
BG probe or plane-result event exists in this fixture.

The layer snapshots bracket7134 at7080 and7140 with0E/0F and collision-array
addresses00276B61/00277161. These are **P1**, not P2: the snapshot has no
character key, and `S3KAuxEventEngine.BuildStateSnapshot` reads `PlayerBase`.
Those global array addresses do not establish which array P2 selected. P2's
per-frame interact record contains status/object_control but no layer bytes;
slot dumps are only `[slot,code]`. Native P2 layer bytes are missing data, not
an unread equivalent of P1's snapshot. The newer exporter has other focused
character payloads, but those events are absent from this fixture.

There is source-backed indirect BG information. At7133/7134, native sidekick
interact payload reports occupied slot9 code000510DA, X05BE/Y0700 and camera
copies052F/07CC then0537/07CC. `Obj_HCZ2Wall` /loc_510E8 writes
`x_pos = $5BE - (Events_bg+$00).w` while BG routine4 is active, so that sampled
wall X indicates offset word0. `HCZ2_WallMove` /loc_51052 derives BG copies as
FGcopy-$200+wallOffset and FGcopy-$500; `Get_BGActualEffectiveDiff` therefore
implies camera diffs0200/0500. Native P1 X05CF/05D7,Y0831 also lies inside
`HCZ2BGE_WallMove`'s BG-enabled bounds [03F0,0C10) × [0600,0840).
These are **owner/source inferences**, not sampled BG flag/diff registers.
`Find_Tile_BG` uses `(Y >> 5) & Layout_row_index_mask`; the normal level-init
source seeds007C and HCZ has no override. The actual mask word at7134 was not
recorded. Native P2 layer, selected tile and exact plane-result witness remain
unqualified; the relayed ROM/math expectation at7134 remains-1.

### Single frozen invocation and why it stopped

Temporary physics helper `HczSelectedWallWitness` spies the existing private
CalcRoomInFront GroundSensor. Shared pooled buffers are identity-checked; its
real scan runs once per invocation and the exact returned object is forwarded.
Primitive FG/BG/combined copies precede terrain queries. Assertions verify no
playable mutation across scan/lookups, provider state stability and no pooled
return overwrite. Original probe binding is restored on exit. No CPU journal,
positioned setup, fake geometry, gameplay values or clock adjustment is added.

Observer file SHA256s: helper504712881af75a6a56a1d024719217de6cd83ff54bcd12ab49530ace9a103591;
runnerd7ec2b33e7b314379dc35b306822ce52856277fdf845b7a3cd4bbdb682477b8e.
Combined SHA256 over the listed helper then runner paths, each path+NUL+bytes:
**b9b8c4163e0ee8887fdcad2638e6ded958dff813f80c232d52a87086dbff2141**.
Exact original-ROM normal/EGL command from the owned cwd, using R3 bindings:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  -Ptrace-replay '-Dtest=TestS3kHcz7134SelectedBgWitness#canonicalThroughHcz7135' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Request67242 finished13:38:11 BST /12:38:11 UTC, queue0s/Maven48.960s,
JUnit24.202s: **1/1F/0E/0S**. The failure is the observer's assertion
`one P2 wall-owner scan per observed tick`, expected1/actual4. The shared probe
also serves the fatal BG floor tail and left/right post-movement clamps
(`CollisionSystem` lines494–540). It does **not** mean four CalcRoomInFront
owner calls. The assertion runs after row7133, so7134/7135 were not executed.
The original7135 stop target was not reached. Cleanup additionally reports four
future unmatched completion edges10162/10163/14172/14173; this early abort
cannot attribute or qualify the prior full-route unmatched-completion condition.

Partial HCZ comparison consumes7134/14208 reference rows (0–7133), with
**206=11physics+195animation**, bootstrap/warnings0, lag53, no non-camera
physics mismatch yet. Counts and actual postrow state match R6 at7132/7133.
Six earlier complete ordinary segments0/2/4/6/8/9 again have zero group/bootstrap/
warning errors and lag0/11/14/16/62/55 over2290/4023/8235/4103/7175/3574 rows.
No new earlier comparator decision is observed; no whole-prefix or7134
passivity/qualification claim follows. Prior R6's first7134 field is unchanged
as historical evidence, not reproduced anew by this attempt.

### Qualified actual7133 wall sample

Only sample1 is the requested projected RIGHT CalcRoomInFront call. Its live
inputs match R6: centre0171.1E00,0830.1F00, vx/ground026A, vy0, angle0,
underwater/grounded/notPush, radii9/15, top/lrb0E/0F. Actual scan arguments
are offset(+10,0), projected delta(+2,+8), layer0 FG /layer1 BG, lrb15.

| Actual7133 item | Observed value |
| --- | --- |
| Provider | DefaultBackgroundPlaneCollisionProvider; active=true, diffX0200/diffY0500, identical before/after scan |
| FG probe → BG probe | 017D/0838 → FF7D/0338 (signedX-131), using the actual provider transform and word cast |
| Provider Y mask /BG row gate | FFFF; Sonic3kLevel background row present=true (source row `(Y & 0FFF) >> 7`, row6) |
| FG signed result | +18 (0012), tile0, flagged angle03, RIGHT, no angle write |
| BG signed result | +18 (0012), tile0, flagged angle03, RIGHT, no angle write |
| Combined result /selected plane | +18/tile0/angle03, exact returned object is BG; equality selects BG |
| Passivity checks | Shared buffers, unchanged playable state, stable provider, copied result unchanged after lookups all pass |

FG query017D/016D/018D yields descriptors005C/0059/005D, no flips, no lrb
solidity and both collision indices0. BG queryFF7D/FF6D/FF8D (signed-131/-147/
-115) yields descriptor/chunk0, no flips/solidity, primary/secondary indices0.
There is no solid shape metric: the +18 is the empty-cell extension result,
not a fitted width or a negative contact. Engine provider0200/0500 agrees with
the native source inference at7133, but native P2 layers remain unknown.

Samples2–4 are the post-movement floor/left/right tails, outside the requested
wall owner. In particular, the DOWN sample uses `bgScanResult`; this helper's
fixed `bgWallScanResult` read there is stale and must **not** be called its BG
result or selected plane. Those extra samples are excluded from qualified wall
evidence. A future observer must tag the real caller and choose the pool by
scan direction, rather than asserting that a shared probe has one call/tick.

The actual7134 FG/BG/combined distance and selected terrain are still missing.
No value is projected from7133 and called an actual7134 observation. No runtime
fault/fix is certified from this partial attempt. The one authorized run is
spent; stop with this concrete observer failure plus native P2-layer/raw-state
gaps. Temporary source/classes and consumed diagnostics are removed normally;
useful native images and foreign/performance artifacts remain untouched.


## R8 lexical wall-owner observer and actual7134 BG result — 2026-10-17

Routine observer repair only, actual child run ordinal9 after the metadata-only
ordinal8 turn. Own tree5daf6dbb6 retains R6/R7 docs; production remains exactly
Root86171c6b8. Native actual4fdf/gitlink322851cb and useful images are preserved.
R7 request67242 remains a partial assertion failure after7133, not a parity pass.
Root's registered R8 ID is recorded separately in both own ownership entries,
with refreshed24h expiry; all earlier round identities remain intact.

### Corrected seam and sole invocation

Test-only `GroundWallSensorWitness` filters the actual `CollisionSystem` stack
for `resolveGroundWallCollision`, rather than counting every shared probe call.
The eligible stack is `scanCalcRoomInFront` → `resolveGroundWallCollision`.
Exactly one real scan per eligible call executes; all other callers also execute
their real method unchanged. Pooled buffers are identity-checked, the identical
actual result is returned, and FG/BG/combined primitives are copied immediately
before pure terrain queries. Vertical/horizontal BG pools are selected by actual
direction. Assertions check unchanged playable fields, provider stability and
unchanged returned pool after lookup. No gameplay writes, new CPU journal,
reseed, fixture/clock adjustment, production API or owner change is made.

The observer is armed after actual7132 and closed after7134;7135 executes after
restoring the original probe. Actual7133/7134 each have exactly one eligible
wall-owner call and three other legitimate shared-probe calls (fatal BG floor
and left/right clamps). There is no assertion that all probe users total one.
The comparator at7132/7133/7134/7135 and actual postrow fields match R6, with no
new earlier comparator decision. This is the passivity boundary tested here,
not a claim that every unobserved internal field has been compared.

Frozen helper SHA256:
`f2982126b5f8e9e37182673ca8d27055333daea43439ec379b4f1f1c91a8842d`;
temporary runner SHA256:
`5db6cd8d22278e3d1aaf3d3b26a6817e4c684df013cad388070a100c366cb262`.
Combined helper-then-runner path+NUL+bytes SHA256:
`a75063e0d3014d07e418dc189d2efa2e4f3e68397b4efe01afccc05a6351c5b4`.
The sole invocation from this owned cwd used the unchanged R3 absolute-path
bindings (`MAIN_WORKSPACE`, `S1_ROM`, `S2_ROM`, `S3K_ROM`) and these exact options:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" \
  "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" \
  -Ptrace-replay '-Dtest=TestS3kHcz7134LexicalBgWitness#canonicalThroughHcz7135' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Request44688 finished13:48:34 BST /12:48:34 UTC. Queue0s, Maven47.168s,
JUnit23.578s, **1/0F/0E/0S**. The original chain driver/bootstrap/BK2 inputs/lag
admission run from segment0, stopping after segment11 row7135 inclusive. No
positioned state, native/media run, variant, alternate, broad or guard run.
This execution success proves the bounded observer completed, **not parity**.

| Segment | Compared reference rows | Physics /animation errors | Lag | Complete |
| --- | ---: | --- | ---: | --- |
| 0 | 2290 | 0 /0 | 0 | true |
| 2 | 4023 | 0 /0 | 11 | true |
| 4 | 8235 | 0 /0 | 14 | true |
| 6 | 4103 | 0 /0 | 16 | true |
| 8 | 7175 | 0 /0 | 62 | true |
| 9 | 3574 | 0 /0 | 55 | true |
| 11 prefix | 7136 of14208 (0–7135) | 20 /197 | 53 | false |

Every listed window has zero warnings/bootstrap errors. The prefix dynamic-art
gap report has11 records and zero reported failures; later gaps/exit/completion
edges are outside this observation. HCZ is **217 errors**, with206 already by
7132/7133 (11physics/195animation),211 at7134 (15/196),217 at7135 (20/197).
First reported non-camera physics remains7134 `sidekick_x`,0175/0176. The
7134 fields also retain native/engine x_speed0170/0270, ground0000/0270,
status0060/0040 and mapping_frame0005/0006. No longer-window total is replaced.

### Actual selected7134 result

Reuse R6's row/clock association: row7134 is BK2cursor70209→70210,
V_int_run_count69066, engineLF/CPU7082; native LF1BAA. The probe is observed
inside the real movement wall call for that row, not reconstructed from a
postrow position. Pre-wall centre0174.8800,0830.1F00, vx/ground0270, vy0,
angle0, underwater/grounded/notPush, radii9/15, actual engine top/lrb0E/0F.
RIGHT scan arguments offset(+10,0), delta(+2,+8), lrb15 are unchanged from R6.

| Actual7134 item | Observed value |
| --- | --- |
| Provider | DefaultBackgroundPlaneCollisionProvider; active=true; diffX0200/diffY0500 stable before/after |
| FG probe → BG probe | 0180/0838 → FF80/0338 (signedX-128), actual provider transform and word cast |
| Provider Y mask /BG row gate | FFFF /present=true; engine source row6; layer1 dimensions20×8 blocks |
| FG /BG signed distances | +31 /+31 (001F), both tile0, angle03, RIGHT, no angle write |
| Combined /selected plane | +31/tile0/angle03; identical returned object is BG on equality |
| Foreground layer0 descriptors at0180/0170/0190 | 005D/005C/0058; no flips/lrb solidity; primary and secondary collision indices0 |
| Background layer1 descriptors atFF80/FF70/FF90 | 0000/0000/0000, chunk0, no flips/lrb solidity; primary and secondary collision indices0 |
| Passivity | Shared pools, playable unchanged, provider stable and identical return unchanged after pure queries: all pass |

There is no solid-tile width metric in these empty queried cells. +31 is the
actual sensor's empty-cell extension result, not an inferred solid contact.
7133 independently repeats R7's qualified RIGHT sample:017D/0838 → FF7D/0338,
FG/BG/combined+18, selectedBG, empty background terrain, provider0200/0500.
The actual7134 positive result explains why the wall response leaves0270/
ground0270/noPush; integration remains0176.F800. It does not establish which
terrain/layer/transform owner differs from native.

### Concrete remaining native gap and handback

R7's full native aux inventory is reused: no P2 layer bytes, raw BG flag,
diffXY, row mask, translated descriptor or exact FG/BG/combined result exists
in this fixture. P1-only0E/0F snapshots cannot qualify P2. Native wall-slot
source inference still supports0200/0500, and the relayed native/ROM math
requires-1 at7134, but neither is an actual native selected-plane/tile witness.
The native layout007C mask and engine providerFFFF mask belong to different
stages; their numeric difference alone proves no defect. Negative wordX and
this chunk boundary are observations, **not proof of a wrapping fault**.

Stop with the engine selectedBG+31 now established and native P2 layer/selected
terrain unqualified. No production correction, second invocation, frontier
advance or whole-chain/slice/alternate/GLFW certification is claimed. Root owns
next native evidence and any runtime decision. The validated generic observer
is retained in test scope because pooled-result/caller attribution recurred;
it has no test entrypoint or production API. The fixture-specific runner,
compiled runner classes and consumed raw diagnostics are removed normally;
no raw archive, foreign/native/performance cleanup or Main write.


### Native observation brief for Root; execution not authorized here

Read-only static preparation after R8, no engine/native invocation or build.
Original BK2 (relative to this owned tree):
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2`,
SHA256 `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
Manifest segment11 `hcz_2` starts at zero-based BK2 index63075. Direct ZIP
`Input Log.txt` reads at indices70208/70209 are both
`|..|...R....|........|`: P1 Right08, physical P2 neutral00. CPU-generated P2
logical Right08 is separate from that physical controller input.

| HCZ row | Actual input index (zero-based) → consumed count | Native CSV LF /VInt low word | Engine VInt /executed chain step /LF |
| --- | --- | --- | --- |
| 7133 (CSV frame1BDD) | 70208 →70209 | 1BA9 /0FDC | 69065 /69065 /7081 |
| 7134 (CSV frame1BDE) | 70209 →70210 | 1BAA /0FDD | 69066 /69066 /7082 |

This is manifest+actual input/driver boundary mapping, not a fitted native
power-on clock. Native observer must log its own input index, core.Frame before/
after, LF word at RAMFE04 and full VInt long atFE0C; no full native VInt or
core.Frame value is reconstructed from engine69066. At7133 CSV P2 ends
0173.8800/0830.1F00,vx-ground026A,status40;7134 ends0175.F800/0830.1F00,
vx0170,vy0,ground0,status60. Expect the observed upstream+1 CPU nudge and
acceleration6 to make pre-wall0174.8800/vx0270 at7134, but record actual native
pre/post values and signed wall return rather than supplying them to the ROM.

All RAM addresses below are low16 mainmemory offsets (24-bit hardware address
FFxxxx), reads big-endian. Require A0 lowwordB04A (P2), and the call originating
at14BB0 with return14BB4, so later P2 clamps cannot be conflated with this owner.

| Required RAM | Address /width |
| --- | --- |
| P2 base; routine | B04A; B04F byte |
| P2 centre X/sub, Y/sub | B05A/B05C and B05E/B060 words (or longs atB05A/B05E) |
| P2 vx,vy,ground | B062/B064/B066 signed words |
| P2 y/x radii; angle/status | B068/B069 bytes; B070/B074 bytes |
| P2 object_control /move_lock | B078 byte /B07C word |
| **Raw P2 top/lrb layers** | **B090/B091 bytes; actual values unknown, do not substitute P1's0E/0F** |
| CPU P2 logical held/pressed | F66A/F66B bytes; physical P2 heldF606 is separate |
| BG collision flag; X/Y diff | F664 byte; EE3E/EE40 words |
| FG copy X/Y; BG copy X/Y | EE80/EE84 words; EE8C/EE90 words |
| HCZ BG routine/offset | EEC2/EED2 words |
| Layout header, row mask, X/Y wrap | 8000 (four dimension words); EEAE word; EEA8/EEAA words |
| Current/primary/secondary collision pointers | F796/F7B4/F7B8 longs |
| Primary angle/save, secondary angle/save | F768/F769/F76A/F76B bytes |

Owning locked-on ROM PCs, verified by static reads of the original ROM
(SHA1cfbf98c36c776677290a872547ac47c53d2761d6, CRC63522553):

- `sub_F61C` = CalcRoomInFront at00F61C; it selects Collision_addr by raw
  top_solid_bit==0C versus secondary, then loads actual lrb intoD5 at00F638.
  Tails caller BSR at014BB0 returns at014BB4 (ROM bytes
  `6100 AA6A 301F 4A41`);014BB6 tests signedD1. RecordD0–D6/A0–A5 at entry,
  projection branch00FA88 and return014BB4; negative response reaches014BCA.
- `FindWall` wrapper00F47E. FG signedD1 is available after its first scan at
  00F48C; BG signedD1 at00F4B4; combined signedD1 at00F4DA /caller014BB4.
  The wrapper subtracts diffX at00F4A4 and diffY at00F4A8 for right scans,
  then selects BG on `D1 <= saved FG distance`. RecordA3 step, D2/D3 word
  coordinates, D5 solidity bit, D6 flip control and A4 angle destination.
- First wall-cell evaluator `sub_F4DC` at00F4DC; adjacent evaluator
  `sub_F584` at00F584 (often called FindWall2). Their post-lookup boundaries
  00F4DE/00F586 have A1 pointing to the actual raw descriptor word; capture
  that address+word on **every** call inside this one lexical wall owner.
- `Find_Tile_BG`00EFB6 and `Find_Tile_FG`00EFF0 (actual LEA literals in
  FindWall). Capture inputD2/D3, row maskEEAE, row-pointer address/value,
  logical X word shifts, selected layout byte address/value, and returnedA1.
  BG computes `(D2.w >> 5) & mask`, reads header+0A+rowIndex, then adds
  unsigned `(D3.w >> 7)`. For inferred0338/FF80 this is rowIndex0018,
  row-pointer address8022 and X column01FF; **these calculations are not an
  observed native RAM value or evidence of a wrapping defect**.7133's FF7D
  would yield column01FE. Do not replace either with a width-modulo address.

The layout byte selects a128-byte chunk via `ChunkAddrArray` at00F02A;
within it the raw descriptor address uses `(Y & 70) + ((X >> 3) & 0E)`.
Record descriptor low10 tile ID, flip bits0A/0B, actual D5 solidity bit,
Collision_addr-selected byte at`pointer+2*tileID`, and the actual AngleArray /
HeightMapsRot lookup address, signed metric and D1 returns, including adjacent
lookups. This distinguishes native negative-word lookup from a different
provider/plane/layer; no expected tile is injected. Qualify callback PC
before/after-instruction semantics explicitly. Existing compiled one-pass
harness may be reused by Root with transparent RAM/PC observation; this worker
starts no native process, build, new recording or source modification.


## R9 native word-layout byte provenance and resident-table boundary — 2026-10-17

Own clean4fa branch fast-forwarded under its intended-tree writer to Root
32a3133d585d8519f6bf47fd821f91dc2d55f3b8 (two append-only evidence files;
no runtime change). Actual native4fdf/gitlink322851cb preserved. Exact returned
R9 taskId is recorded in both own registry entries, actual child ordinal10,
24h expiry refreshed. R8 engine selectedBG+31 and all earlier results remain
historical. This bounded slice makes **no production/API edit** and launches
no native/capture, engine prefix, alternate, broad or guard request.

### Qualified native input and source inventory

Root's one native request16359 is terminal0. Existing task-directory
`hcz-native7134-selected-wall-v1` retains provenance/callbacks/summary and four
64KiB RAM snapshots for inputs70207–70210. They were read, not regenerated or
modified. The original AD40 movie/input and clocks are those in the preceding
brief. Native actual full VInt is00010FDC/00010FDD at7133/7134; LF1BA9/1BAA.
A0 isFFFFB04A, raw P2 layers0E/0F, BGflagFF, diffs0200/0500. These now qualify
the fields missing in R8; no P1-layer substitution or inferred native clock.

| Owner row | FG signed distance | BG signed distance | Combined | Actual BG coordinate |
| --- | ---: | ---: | ---: | --- |
| 7133 | +18 | +2 | +2 | FF7D/0338 |
| 7134 | +31 | -1 | -1 | FF80/0338 |

Native7134 row mask007C, row index0018, row pointer address8022/value8E80,
unsigned word X column01FF selectFF907F byte94. Find_Tile_BG returnsA1FF4A30,
descriptorA216: tile0216, hFlip=false,vFlip=false, lrb15 set. Collision byte
27758D=FF, angle0960FF=FF, rotated height0980F8=10. The adjacent FF70 query
selectsFF907E byteE0, descriptor atFF703E=0179 with lrb15 clear. Native final
0175.F800/vx0170/ground0/status60 is qualified by the existing endpoint.
R8 engine FG/BG+31 with the same provider/layers is therefore a real lookup
input disagreement; CPU nudge/layer/provider/metric-constant tuning is rejected.

### Exact907F writer: current shared HCZ tile definitions

`Load_Level` copies exactly1000 bytes from `LevelPtrs[zone*2+act]` to
Level_layout_header8000–8FFF. ROM entry3 at09D5CC points to2834FA (HCZ2).
**All1000 bytes match all four native RAM snapshots** at8000–8FFF. Thus907F
is outside the loaded layout payload: it is **Block_table9000 +007F**.

`LoadLevelLoadBlock2` loads the 16×16 primary stream to Block_table before
appending the secondary stream at its running destination. Both HCZ LLB
entries2/3 use primary ROM3B91F8 (`HCZ_16x16_Primary_Kos`). Static decoding
with the existing pinned `tools/timing/s3k-instruction-ledgers/kos/kos_service.py`
(read-only ROM input, fresh analysis state, unit instruction cost; no engine or
native process/readiness input) emits0558 bytes in11544 modeled instructions.
The entire decoded output equals RAM9000–9557 in all four existing snapshots.
SHA256 `ec6b181dfeb7adb1684954a5fce2866a25493eccb94bdf3905e5611e25533234`.
Offsets0078–007F are E091/E092/E093/**E094**;907F's94 is the low byte of the
fourth pattern word of native tile15 (engine `Chunk(15).getPatternDesc(1,1)`).
It is current ROM data, **not untouched layout or unknown previous-load RAM**.

`HCZ1BGE_Normal` queues the HCZ2 16×16 secondary stream3BF17C to9558,
so it leaves this common0558-byte prefix intact. The existing production
loader independently confirms E094 and current block94/cell(0,3)=A216 in the
short attempted regression below. No native RAM value is supplied to it.

### Adjacent128×128 descriptor has a different, retained writer

The second lookup also needs a resident table that a cold HCZ2 load truncates.
Static original-ROM decoding establishes:

| Native128×128 table writer | ROM source | Destination | Output length /exclusive end |
| --- | --- | --- | --- |
| HCZ common | 3BA2AA | 0000 | 0A00 /0A00 |
| HCZ1 secondary | 3BD0CC | 0A00 | 7000 /7A00 |
| HCZ2 secondary | 3C18EE | 0A00 | 6580 /6F80 |

Common output, the entire HCZ2 overwrite0A00–6F7F, and retained HCZ1 tail
6F80–79FF each exactly match all four native snapshots. Their decoded stream
SHA256s respectively are
`bfbb41fb6eb1e6e679c05f2b5ac7eabc1fee5d4734cf49f75d8c9cd3be33beb0`,
`1b2e639deeac8b80c3d92daebf67f05d6c5779344e5fa080bcf3ad33da9a43cc`,
`ef924e8ad40b21fcc062d4bf3525380e0107f8a3f8408b813b4fcceb069e68e7`.
The first descriptor4A30=A216 is inside the current HCZ2 overwrite. The
adjacent703E=0179 is outside it, at HCZ1 secondary output offset663E.
Native Queue_Kos into RAM_start+A00 does not clear the remaining tail.
This identifies the data's load provenance; no new RAM-write-PC capture was run.

### Current owners and the one attempted short regression

`Sonic3kLevel.loadMap` reads1000 ROM bytes, then `parseLayoutLayer` flattens
only header-declared columns into Map and discards the encoded row-pointer/
contiguous-byte workspace. `LevelLayoutLookup.getChunkDescAt` always wraps X
by layer width and wraps BG Y by height before walking Map→Block→ChunkDesc.
That cannot reproduce an unsigned word column spilling from the layout into
resident Block_table bytes. `Sonic3kLevel.loadBlocksWithPlan/decodeBlocks`
rebuild arrays to the target output length. HCZ's production transition queues
and claims its native destination workloads, then requests RELOAD_TARGET_LEVEL;
its captured build inputs retain frontend patterns, not this previous table tail.
Cold HCZ2's valid table ends6F80 (223 complete blocks); native blockE0=224
belongs to retained HCZ1 residency. The cold missing block is **not a measured
new active-level failure** and cannot stand in for the real transition lifecycle.

Exactly one independent ROM loader/provider regression was authored at32a,
SHA256 `b2de44b215a45599dae758f88d5661a6554e8e922b19e98c3208484ea4532414`,
method `TestS3kWordLayoutCollision#encodedBackgroundRowCanReadTheCurrentRomTileDefinitionBytes`.
It uses SharedLevel's production HCZ2 load, DefaultBackgroundPlaneCollisionProvider
with declared0200/0500 geometry, encoded ROM row6, negative/adjacent/positive
coordinates; no fixture, native RAM, physics/aux rows or gameplay writes.
It passes provider transform, ROM row pointer, current E094 and current
block94/A216 sanity assertions, then errors while checking direct blockE0.
Actual lookup/control assertions are **not reached**. This is **not qualified
old-red, a parity result or rewind proof**. The cold setup omitted the native
resident predecessor table; no second attempt is launched.

Exact command from the own cwd, unchanged R3 absolute-path ROM/wrapper bindings:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kWordLayoutCollision#encodedBackgroundRowCanReadTheCurrentRomTileDefinitionBytes' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Request32483: **1/0F/1E/0S**, `IllegalArgumentException: Invalid block index:224`
at the adjacent-table sanity check (line63), finished14:14:50 BST /13:14:50 UTC.
Queue0s/Maven24.482s/JUnit0.581s. No retry, runtime change, descendant engine
prefix, variant or wider suite. One-off test/classes and consumed XML/text are
removed after inspection; useful native artifacts and prior results preserved.

### Design handback before production/API or cross-load changes

The demonstrated boundary needs two semantic owners, rather than a replacement
wrap constant:

1. An internal collision-layout addressing capability must preserve encoded row
   pointers and resolve the native word address into the **current production
   asset owners**.8000–8FFF is the loaded layout;9000 onward is the actual live
   16×16 definition bytes (derivable from Chunk pattern words). It must use
   unsigned word shifts/addition and the real row mask, without width modulo,
   clamp, zone/row conditions or copying observation RAM. Keep the generic shared
   lookup free of S3K name branches; existing render-only descriptor overrides
   are not a collision hook. No public Mod API expansion is proposed here.
2. Resident128×128 definitions must survive the owner's shorter destination
   write across the actual level-build/commit boundary. Capture prior arrays as
   value inputs and compose explicit ROM destination writes without clearing
   unwritten bytes. A generic residency/carry contract is needed; neither a
   cold HCZ2 default nor a forced blockE0 supplies the native predecessor.

This is the requested stop boundary: it entails new internal collision and
cross-load retention/capture contracts, so **Root must authorize the minimal
implementation**. Do not silently model unknown RAM regions as width-wrap or
empty data. Keep layout mutations/scene swaps coherent with encoded backing;
current LevelSnapshot captures Block/Chunk refs and flattened Map bytes only,
and SceneLayoutSnapshot likewise omits encoded pointers. Any new retained
state must use existing per-game rewind ownership without casually expanding
public snapshot signatures/pins. Required future proof is a short production
HCZ1→HCZ2 owner transition, negative/adjacent/positive lookup controls and two
forced full-world restores with forward replay. None is claimed by R9.

Raw907F94 and adjacent703E0179 now have source-backed current/prior-load
provenance. Production remains32a's runtime86171; canonical7134sidekick_x and
independent9482air remain open, with no new trace counts or general HCZ claim.


## R10 encoded collision words, resident destinations and ROM aliases — 2026-10-17

Owned tree `t3-3aa3bc3c`, branch `feature/ai-s3k-hcz-gpt61-repair`, R10 child
ordinal11. Base `64b77a2ab39e64c17624cc32197b4e7a8606df18` merges Root067 while
preserving9f0 R9 evidence; before edits, `e757cfaa5e6cb585a757700069f6a4dd1da5df8a`
merges Root28. Append conflicts retain both complete evidence suffixes. No Main
or Root writes, new tree, children, native execution, media, alternate or broad
run. The inherited clean native4fdf checkout against gitlink322 is preserved.

### Production owners and independent old-red

Native Find_Tile_BG/FG ($EFB6/$EFF0; disassembly19151–19201) masks the word Y row,
adds unsigned word X>>7 to the encoded row pointer, then MOVEA.W selects the
byte's address space. That byte selects a separate ChunkAddrArray entry whose
D1 upper word remainsFFFF. These are two different address-formation owners.
R9's current ROM907F=94/A216 and prior HCZ1 tail703E=0179 provenance remains
unchanged; no observation RAM enters gameplay.

`TestS3kNativeWordLayoutResidency#productionActTransitionRetainsTheNativeWordLookupOwners`
loads actual HCZ1, signals its native results completion, services the real
jobs, and executes the existing request/build/commit into HCZ2. It verifies
E094 from current16×16 ROM data, current block94/A216, retained blockE0/0179,
negative FF80/0338, adjacent FF70, unsigned/word-wrap and ordinary positive
controls. No forced block, cold-target substitution, trace rows or hydrated state.
Initial95005 failed a test assumption that the transition remained pending;
HCZ's existing BG owner executes it inline when a level exists. That is not an
owner old-red. Corrected21043 is genuine **1/1F/0E/0S**: all five retention/
negative-word assertions fail (missing resident tail and zero descriptors),
while the positive control passes.6557 is **1/0F/0E/0S** after implementation.

Shared lookup delegates to an internal semantic level capability; S1/S2 use the
existing path. S3K retains the1000 encoded layout bytes, uses the existing live
camera wrap register's paired row mask, and reads live16×16 definition words.
Map writes update encoded bytes and aliases through existing mutation surfaces.
The128×128 build seed is an immutable value of the predecessor's actual live
ROM-produced definitions; ResourceLoader destination writes start at zero and
preserve only unwritten tail bytes. Prepared builds capture the seed before
worker execution. No public Mod API/snapshot shape or signature pins change.

The per-game rewind adapter captures encoded/scene metadata; existing level COW
arrays own live definitions. Unchanged captures reuse their value, with no
large unchanged per-frame byte copies. Tests mutate live16×16/retained128×128/
map data and swap a real ROM scene, then force whole-world object recreation,
restore twice, replay forward, and compare **every registered snapshot key**.
Captures begin after the intentional transition history-reset boundary.

### Independent review: MOVEA.W ROM space and the necessary extension

The reviewed runtime7 hash `eed1f832c06869d305b39d0ba2e88da08ad8e7b2dfd808010594e5afafd820bf`
contained one material defect: initial selected words0000–7FFF were incorrectly
read as FF0000 resident RAM. Stock zero row pointers select ROM instead.
A production HCZ2 FG row28/column1 control is genuine38709 old-red, **1/1F**:
no exception under that incorrect substitution. The authorized safe correction
explicitly refused ROM aliases with full24-bit addresses;97298 then passed7.
The sole74271 original prefix reached segment8 (`aiz_5`)2195/7175 rows with
zero comparison errors, then refused **ROM00007F** in an airborne ceiling probe.
Last observed row2194; the next advance did not commit. No HCZ7134 observation
occurred. This was an honest unsupported source boundary, not bad native-RAM
provenance or HCZ qualification. Its suppressed undrained future timing edges
on early close do not establish a second timing defect.

Root then authorized a bounded same-owner ROM extension. Revised short control
53171 is genuine **1/0F/1E/0S** against the refusal, at ROM000000. Final code
loads a private final32KiB prefix from the constructor's current production
`Rom.readBytes(0,0x8000)`. Positive MOVEA.W byte selection reads that immutable
bounded asset; no ROM handle, public API, arbitrary RAM bus, hardcoded zeros,
zone/row fit or reference hydration is added. Recreated layout bindings use the
current level's immutable asset without copying it per snapshot. Unknown other
regions/unwritten definitions still fail explicitly with full24-bit context.
The control compares real ROM-selected descriptors across reachable zero-row
columns, including nonzero bytes whose result differs from block0. ROM binding
is checked again after both forced whole-world restorations and forward replay.
Final3897 passes **7/0F/0E/0S**.

### Commands, measured results and inherited failures

All invocations use the unchanged R3 absolute `MAIN_WORKSPACE`, `S1_ROM`,
`S2_ROM`, `S3K_ROM` bindings, own cwd/target, current Main wrapper, normal ROM
admission, Java25/Lua5.4, no ambient display, EGL, nullAL and one reusable fork:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4   python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off   -Dopenggf.headless.gl=surfaceless-egl   "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM"   <selection below> -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true   -Dsurefire.runOrder=alphabetical test -B
```

| Session | Exact selection (in place of selection above) | Result tests/F/E/S | Queue / Maven; finished UTC |
| --- | --- | --- | --- |
|95005|`-Dtest=TestS3kNativeWordLayoutResidency#productionActTransitionRetainsTheNativeWordLookupOwners`|1/1/0/0, setup assertion only|Maven1m13s;13:29:27|
|21043|same owner selector, corrected test|1/1/0/0, genuine five owner assertions|8s/25.648s;13:30:14|
|6557|same owner selector|1/0/0/0|0s/1m12s;13:35:02|
|31686|`-Dtest=TestS3kNativeWordLayoutResidency,TestSonic3kLevelOverlayRewindIsolation`|6/0/0/0|0s/1m13s;13:39:27|
|4439|focused selector below|122/0/1/0|0s/31.884s;13:45:06|
|45410|guard selector below|179/2/0/0|6s/40.199s;13:46:23|
|38709|`-Dtest=TestS3kNativeWordLayoutResidency#zeroRowPointerUsesTheRomAddressSpaceRatherThanResidentDefinitions`|1/1/0/0, wrong-space old-red|0s/25.588s;13:50:33|
|97298|same two-class selector as31686|7/0/0/0, explicit-refusal source|11s/1m15s;13:53:00|
|74271|`-Ptrace-replay -Dtest=TestS3kHcz7135ResidentLayoutWitness#canonicalThroughHcz7135`|1/0/1/0, ROM00007F refusal|0s/48.756s;13:54:34|
|53171|same zero-row selector, actual-ROM expectation|1/0/1/0, refusal old-red|14s/26.077s;13:58:12|
|3897|same two-class selector as31686|7/0/0/0, final ROM-owner source|0s/1m13s;14:00:17|
|49095|same original-prefix selector as74271|1/0/0/0 observer, **red comparator below**|0s/48.198s;14:01:25|

4439 exact selection:
`-Dtest=TestChunkDescPow2Indexing,TestCollisionLogic,TestObjectTerrainUtils,TestTerrainSurface,TestZoneLayoutMutationPipeline,TestZoneLayoutMutationPipelineNoRedraw,TestSeamlessTransitionResourceHandoffAcceptance,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAiz1SkipHeadless,TestModApiSignatureSurface,TestResourceLoaderModSources`.
Mandatory S3K4 contributes60 passing cases; both level-loading packages run.
Mod API signature checks11 and the S1/S2/default layout arithmetic controls6 pass.
Sole error is `TestZoneLayoutMutationPipeline.levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary`:
mock `GameModule.getRules()` null at `GameplaySessionFactory.attachManagers:60/39`
→`TestEnvironment.activeGameplayMode:167`→test220 (`paletteWhiteFade`). Root's
fresh compiled exact baseline61070 on Root0684/runtime28/noR10 is **1/0F/1E/0S**,
finished13:47:37 UTC, Maven1m12s/wall74.543s. Root directly compared both XMLs:
name/type/message/top four stack lines identical. **Inherited, not a pass.**

45410 exact selection:
`-Pguards -Dtest=TestArchitecturalSourceGuard,TestPerGameRuleArchitectureGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceRunVblankClockAuthorityGuard,TestStaticStateRewindCoverageGuard,TestRewindArchitectureGuard,TestZoneEventRewindSchemaGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestProductionSingletonClosureGuard`.
Only `TestProductionSingletonClosureGuard.productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices`
and `.productionCodeOnlyUsesRawGetInstanceAtEngineServicesBootstrapBridge` fail,
both `MenuAudioProbeTool.java:41` singleton access. Root attributes exact identities
through Main7076602 (73/4F), Root94450 (145/4F,09:56:49 UTC), and Root55438
(159/2F,10:13:42 UTC); guard/tool diff707→Root0684 is empty. Other177 pass,
including architecture72, hardware timing25, trace invariants10, per-game3,
Vblank2, zone schema18 and rewind coverage. No budgets, guards or mock changed.
These checks precede the reviewed address-space correction/ROM extension and
are reused for unchanged contracts; final affected owner/rewind tests are fresh.
No broader guard/full/smoke rerun is claimed.21043 JUnit time was not retained.

### Final original prefix: actual native7134 closure, bounded residuals

Final base is e757. Runtime7 path+NUL+bytes SHA256 is
`0b1f59eb135fd284a5e058e0f08e8f438d2869193935d06c3c9da1531318b676`;
runtime plus permanent test8 is
`e8df8b23588dfb249e37c271c2e02bcca9c132972cefce98a7ec38bf551b25aa`;
with temporary observer it is
`28afdb9ddec7b7c8e2cb313ce45d1271c337f4393698f8fbd8c3d715405609fc`.
49095 uses the original AD40 BK2, bootstrap, inputs/lag and original driver from
boot; it stops after segment11 row7135 inclusive (7136/14208 rows). The R8
lexical observer forwards the actual wall owner once per7133/7134 tick, copies
pooled results before pure lookups, and asserts playable/provider/returned-result
passivity. Other legitimate floor/clamp calls remain unchanged. No new CPU journal.

| Actual tick | FG / BG / combined distance | Selected BG / post-P2 |
| --- | --- | --- |
|7133|+18 /+2 /+2|FF7D/0338;0173.8800,0830.1F00,vx/g026A,noPush|
|7134|+31 /-1 /-1|FF80/0338;0175.F800,0830.1F00,vx0170,g0,pushing|
|7135|not additionally probed|0175.FE00,0830.1F00,vx/g0006,pushing|

Provider active/diff0200/0500, layers0E/0F remain correct.7134 selects current
A216: chunk0216, no flips, LRB solid, collisionFF, angleFF, metric16; adjacent
FF70 selects retained0179 nonsolid. Native7133/7134 distances and7134 endpoint
now match Root's already-qualified ONEpass16359. No new native run is implied.

| Gameplay segment | Consumed/reference rows | Physics /animation errors | Lag |
| --- | --- | --- | --- |
|0|2290/2290|0/0|0|
|2|4023/4023|0/0|11|
|4|8235/8235|0/0|14|
|6|4103/4103|0/0|16|
|8|7175/7175|0/0|62|
|9|3574/3574|0/0|55|
|11|7136/14208, incomplete|11/195|53|

All listed windows have zero warning/bootstrap errors. HCZ's **206** residual
errors are11 camera physics and195 animation; `firstNonCameraPhysicsMismatch`
is absent through7135. R8's identical7136-row window had217 (20physics/197animation),
so nine non-camera physics fields and two animation fields close here. Recent
camera entries include6790–6792 and6947–6948 `camera_y`; the report does not
establish the earliest remaining animation field. No next non-camera frontier
beyond7135 is measured. The JUnit observer pass is **not strict parity green**.

Stopping condition met: production ROM ownership and actual7134 wall closure
with independent old-red/green and full-world replay evidence. Full returned HCZ,
independent4353-span slice, alternate and exact GLFW oracle remain unqualified
on this source. Historical matched4699/4353 is mixed,3738 unmatched; no old full
window totals or alternate760 result are transferred. Root owns combined smoke,
further strict qualification, Main integration/push and native publication.

Final housekeeping:72 consumed own report/XML/text/owner metadata files removed
under current Main `worktree_metadata_slot` alone; temporary observer source and
two compiled classes removed under own writer lease. Exact results/limits above
and existing ownership entry retain the useful evidence; no raw log archive.
Native4fdf checkout, original native RAM/PC artifacts, useful images and foreign
performance work remain untouched. No own live jobs or further test submission.


## R11 HCZBlock Full2 visibility owner closes7807 through7808 — 2026-10-17

Owned `t3-3aa3bc3c` safely fast-forwarded R10 `fa289e22e` to Root runtime
`152d07ce90d512d2b75dfddfd10c777b0788acea` under the intended-tree writer
lease, retaining the R10 feature branch and private configuration. R11 branch is
`bugfix/ai-s3k-hcz-7807-causal-owner`. Root's later a58bdc docs-only update was
not a reason to restart. Native checkout4fdf remains clean internally and differs
from gitlink322851 exactly as inherited; it was neither changed nor staged.
No Main/Root write, new worker/tree, native execution, media or broad run.

Root93212 on152 supplied the first actual non-camera frontier7807
`sidekick_y` native066F/engine0669. Its complete14208-row HCZ window had133258
errors =113873physics+19385animation, lag59, zero warnings/bootstrap; that full
pre-fix total is context, not this slice's qualification. Root's report facts
were extracted read-only and relayed as consumed.

### Native owner and one passive engine prefix

Original run directory is
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds`, BK2 SHA256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
Segment11 offset63075 and the original driver/bootstrap/input/lag remain
unchanged. Observing46246 walks from boot through7808 inclusive. Test-local
ceiling wrappers forward each actual scan once and return its identical pooled
result; FG/BG/combined copies precede pure lookups. Existing CollisionTrace
callbacks observe the actual block checkpoint without replacing its owner.
Playable/provider/result passivity is asserted. Journal166423bytes,10 ceiling
samples and90 block checkpoint samples; no full CPU journal or state hydration.
19262 was a separate successful build-only test-compile prerequisite, not a test.

Native existing aux identifies active slot21 `loc_1F3CA` HCZBlock at09B0/0650.
P2's retained interaction slot25 at09D0/06C0 is stale, not current support.
At7807 the engine's actual upward terrain probes at09AA/065B and09B8/065B
return FG/BG/combined+20, empty. Active BG provider diff0426/0500 yields
0584/015B and0592/015B, also empty; this is not a terrain solver correction.
Actual post-movement block21 checkpoint receives P2
09B1.FD00/0669.F700, vxFEA2/vyFBB0/gFF1A, radii07/0E, stand radius0F,
air/rolling, no OnObj/object control, params1B/10/11. It returns NONE and
leaves those fields unchanged. Native instead ends066F.F700/vy0/g0.
Native raw P2 render_flags05 is offscreen. The engine raw render flag was not
sampled by this observer; the independent visibility controls below establish
the causal gate rather than inventing that missing measurement.

ROM `Obj_HCZBlock` / `loc_1F3CA` calls `SolidObjectFull2`
(`docs/skdisasm/sonic3k.asm:43287–43297`). **Both gates are bypassed**:
`SolidObjectFull2` dispatches Player2 without the player render_flags test
(:41091–41102), and new-contact `SolidObjectFull2_1P` branches directly to
`SolidObject_cont` (:41105–41107), skipping the object's `loc_1DF88` test
(:41430–41434). HCZBlock had inherited the shared regular-Full visibility
contract. Its existing `bypassesOffscreenSolidGate()` capability now returns true;
no shared geometry/movement/profile/API, timer, CPU, camera or plane changed.

Underside arithmetic is source-derived: current radius0E, default0F and block
half-height10 give maxTop1E/total3D. Integrated relativeY19 gives overlap
19+4+1E=3B, so signed bottom distance3B-4-3D=-6. `loc_1E0F6` clears ground
speed and `loc_1E104` subtracts -6 from y_pos and clears y_vel, preserving y_sub.

### Independent old-red, visibility controls and twice-world replay

`TestHczBlockFull2Visibility` uses a real production HCZ2 fixture and allocated
subtype0 HCZBlock at declared0400/0400, with airborne rolling CPU Tails at
0400/0419, fractions3500/A500 and rising velocity. These are independent
geometry inputs, not positioned replay rows. The block first receives its
ordinary admission dispatch, then the actual object-manager checkpoint runs.

| Camera Y | P2 visible / object contact bounds | Before fix85345 | After fix41923 |
| --- | --- | --- | --- |
|0380|true /true|pass|pass|
|0420|true /false|fail Y041F/0419|pass|
|0500|false /false|fail Y041F/0419|pass|

Initial89593 failed all three, including the on-screen control: the freshly
allocated object's first-dispatch contact guard was still armed. This was a
setup error, not owner proof. Only the test's normal admission tick was corrected
before85345, which genuinely fails just the two offscreen cases (3/2F/0E/0S).
The permanent fourth case disables in-place rewind, captures all registered
world keys, resolves the underside and advances two ordinary ticks. It twice
restores/recreates the whole world and repeats that forward sequence, comparing
every registered key at restore and replay endpoints. No new runtime state or
rewind schema is introduced.

All invocations below used Java25, Lua5.4, current Main absolute
`$MAIN_WORKSPACE/tools/testing/maven_queue.py --normal`
from this cwd, `-Dmse=off`, EGL `-Dopenggf.headless.gl=surfaceless-egl`,
`EGL_PLATFORM=surfaceless`, `ALSOFT_DRIVERS=null`, unset DISPLAY/WAYLAND_DISPLAY,
`-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true
-Dsurefire.runOrder=alphabetical test -B`, and the three original absolute ROM
properties shown in R10's command above. No wrapper/activity shim or direct JVM.

| Handle / exact selection | Observed completed result | Queue / Maven / finish UTC |
| --- | --- | --- |
|19262, `-DskipTests test-compile`|build success, no tests|build-only /1m14s /14:20:47|
|46246, `-Ptrace-replay -Dtest=TestS3kHcz7808OwnerWitness#originalCanonicalThrough7808`|1/0F/0E/0S observer, HCZ7809-row214errors|0s /49.731s /14:22:49|
|89593, `-Dtest=TestHczBlockFull2Visibility#full2UndersideDoesNotDependOnVisibility`|3/3F/0E/0S setup error|0s /24.042s /14:27:36|
|85345, same selector after ordinary admission setup|3/2F/0E/0S genuine old-red, on-screen passes|0s /24.230s /14:29:49|
|41923, `-Dtest=TestHczBlockFull2Visibility,TestHCZBlockObjectInstance,TestS3kHczMechanismRewind,TestSolidObjectManager,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`|157/0F/0E/0S|0s /1m14s /14:34:11|
|22063, `-Pguards -Dtest=TestArchitecturalSourceGuard,TestPerGameRuleArchitectureGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceRunVblankClockAuthorityGuard,TestRewindArchitectureGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard`|118/0F/0E/0S|1s /33.171s /14:35:55|
|7687, `-Ptrace-replay -Dtest=TestS3kHcz7808QualifiedPrefix#originalCanonicalThrough7808`|1/0F/0E/0S bounded final observer, not parity green|0s /46.965s /14:37:04|

41923 includes block5, mechanism rewind2, shared solid86, new visibility/world4,
and mandatory S3K4: bootstrap6/decoding3/AIZ skip8/loading36+7 (both loading
classes selected). Guards include architecture72, per-game3, timing25, trace10,
Vblank2 and rewind4+1+1. No new disputed failure or skip; no unrelated mock,
singleton, guard budget or allowance changed. R10's inherited failures retain
that earlier scope; this finite selection does not certify their repair.

### Final frozen runtime and bounded frontier

Runtime file HCZBlock SHA256:
`98ea5c3633c6cba49ecfccb2b990631820708be9dff6d6aa5e513bbb0597127f`.
Production eight-line diff from152 SHA256:
`fea6adf0854b9048d4043318b336cede1848c3d3f6425a8771be1c11d5fa1758`.
Runtime+permanent regression path+NUL+bytes (sorted) SHA256:
`bc784ce429608d4e3d06f1ed23f68e156adbc2440c4a0d4b5f19632c79753cc2`.
Initial two-file temporary observer hash was
`ba370d9b4c7c4f1f7290a3a6d2e33fd5f75cac9c53dd63e97e921b46f60a019c`;
its sensor/checkpoint wrappers were removed before final qualification. Final
post-row-only observer SHA256 was
`72fd2739a694201d51c81a27864335aaa8a3ae6152f0f3099fe3f20d53ed6529`.
It only reads post-row poses and existing comparator summaries. No sensor,
CollisionTrace or driver substitution remains; no gameplay field is supplied.

| Gameplay segment | Consumed / reference rows | Physics / animation errors | Lag |
| --- | --- | --- | --- |
|0|2290/2290 complete|0/0|0|
|2|4023/4023 complete|0/0|11|
|4|8235/8235 complete|0/0|14|
|6|4103/4103 complete|0/0|16|
|8|7175/7175 complete|0/0|62|
|9|3574/3574 complete|0/0|55|
|11|7809/14208 incomplete|11/195|53|

All listed windows have zero warning/bootstrap errors. Matched7809-row HCZ
window46246 had214=19physics+195animation; final7687 has206=11camera physics+
195animation. Eight non-camera fields close; no non-camera physics mismatch
remains through7808. The first remaining all-group mismatch is3475
`sidekick_animation_id`, native17/engine1C, observed in the comparator's first
error output. Recent camera_y mismatches include6790–6792 and6947–6948; this
summary does not establish the earliest camera mismatch or later non-camera
frontier. It is not zero-frontier/whole-HCZ/whole-chain certification.

| Row | Actual engine P2 X.Y fractions / velocities | Clock at completed row |
| --- | --- | --- |
|7806|09B4.5B00 /066E.7F00, vxFEAE/vyFB78/gFF1A|objectVInt70836, objectLF1620, levelLF7754, movieLogicalFrame70881|
|7807|09B1.FD00 /066F.F700, vxFEA2/vy0000/g0000|objectVInt70837, objectLF1621, levelLF7755, movieLogicalFrame70882|
|7808|09B0.9300 /066F.F700, vxFE96/vy0038/g0000|objectVInt70838, objectLF1622, levelLF7756, movieLogicalFrame70883|

These endpoints match existing native rows; air/rolling remain true and no OnObj
is invented. BK2 cursor7804..7808 maps70879..70883 by actual manifest/driver
observations. Native7807 frame1E7F, LF1E4B=7755, recorded Vblank word127E;
engine terrain phase objectVInt70836/objectLF1620 precedes its block phase
70837/1621. These are named clocks, not fitted offsets or an inferred full native
VInt. P1 physical7804 input0014 and7805..8 input0004 are distinct from generated
P2 CPU held0014/pressed0. Physical P2 is neutral.

Stopping condition: one source-backed object-local capability correction with
independent old-red/green, twice-world replay and actual7807/7808 closure. Later
non-camera frontier, full returned HCZ/exit, independent slice, alternate, exact
GLFW oracle and viewport/roster/native presentation breadth remain unqualified
on this source. Historical4699/4353 mixed windows and3738 unmatched totals are
not improvement proof; Root owns current baseline vector attribution and further
strict/broad validation. No extra prefix/native/capture/alternate/guard run.
Both temporary runners, the sensor helper and exact compiled classes were
removed after facts became durable.70 consumed own report/XML/text/owner files
were removed under `worktree_metadata_slot` alone, preserving foreign native/
performance artifacts and useful images. No log archive or new job remains.
Root independently reviewed this exact eight-line override and98ea5c runtime
file: no material finding. Source review confirms the Full2 Player2 dispatch,
Full2_1P direct continuation and block call before Sprite_OnScreen_Test; no
additional review or runtime check was requested.


## R12 slice cached visibility consequence and full composed canonical — 2026-10-17

Owned3aa3 safely fast-forwarded R11cd94 to Root
`bef8f27ca5069ec296e6912b1e1bc7f735375c2f` on
`bugfix/ai-s3k-hcz-slice-cpu-visibility-gpt61`. R10fa and R11cd94 branch refs
remain retained. Native4fdf is internally clean and its inherited gitlink322
checkout difference is untouched. The inherited private config symlink was
isolated under the intended-tree writer lease as an identical regular0600 copy;
no Main/Root configuration write. **No R12 production or maintained-test edit.**

Root's actual matched whole-slice comparison is context: Main80317155 has4699
spans=3973physics+726animation, current58673 onbef8 has4353=3424+929, both29302
compared rows, no warning/bootstrap errors, eight advertised aux schemas missing
and first9482 air1/0. Root reports sorted whole-errors JSON SHA256
`71f91e9494d9a6926cd2f032dcc174a0d289355d9f9a6658b3752ae61d96d251`, identical
to historicalR3. First newly mismatching field/row is10834
`tails_cpu_respawn_counter`0000/0001. Lower totals are not a regression waiver.
The earliest demonstrated compound b6 attribution does not identify its causal
sub-owner. No ancestor sweep or another full slice is run here; historical3738
remains unmatched. Root artifacts were not needed for the new observation.

### Original slice witness: cached flag is consumed correctly

Fixture is `src/test/resources/traces/s3k/hcz_completerun`; its original movie
`src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2` SHA256 is
`82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`.
This is distinct from canonicalAD40. One temporary subclass retains the original
slice bootstrap/input/lag/row driver and strict divergence assertion, stopping
through raw10835. It forwards the existing session hardware observer and uses
existing Camera.UpdateObserver callbacks plus the end-row hook. No controller
replacement, new shared debug API, flag/counter write, trace hydration, CPU
journal, new native execution or media. Read-only sampling is checked for
passivity; journal55779bytes/13 bounded samples, not a full-row dump.

**Observer phase correction:** its temporary label
`pre_main_loop_before_CPU` was wrong. Actual call order and CPU LF show that
PRE_MAIN_LOOP occurs after CPU/camera scrolling. LevelFrameStep:518 services
that boundary after ScreenEvents, before boundary easing and render-flag refresh.
Those samples are retained only as loop-tail observations. No direct private
checkDespawn entry/exit hook is claimed. The exact relevant dataflow is the prior
end-row cached publication and the following post-CPU camera-entry callback:
SpriteManager:1506 is the runtime cached-flag setter, called by the LevelFrameStep
post-camera tail; there is no intervening new publication before that CPU tick.
Compact latest-normal diagnostics identify its NORMAL branch/preStatus; no
second observer run is submitted to repair a label.

| Raw row | Native P2 / camera copy X / flag / timer | Engine post-row P2 / camera copy X / cached flag / timer |
| --- | --- | --- |
|10832|0176.0500 /0185 /84 /0|0176.2500 /0186 /true /0|
|10833|0181.6700 /0199 /84 /0|0181.8700 /019A /false /0|
|10834|018C.C600 /019C /84 /0|018C.E600 /019B /true /1|
|10835|0198.2200 /01A6 /84 /0|0198.4200 /01A6 /true /0|

All these cached states are valid in engine. P2 Y0831 is identical as a word;
engine fraction4900 versus nativeB900 is an already divergent field, not repaired.
Render centre equals body centre; render width18 (24pixels), body dimensions20×28pixels (decimal),
radii07/0E, 320×224 camera, zero XY shake, copied/live camera X equal in samples,
and Y copy07CC. Both native and engine CPU routine6, preStatus44 (grounded rolling
underwater), object control0, no OnObj; stale native interact slot28 is empty and
not active support. Engine flightTimerRaw0. Fresh-entry rule is **false**, and
fresh-entry-delay-consumed remains false throughout.

At10833 native horizontal inclusion is0181-0199+18=0, so render_flags84 is
correct. Engine copiedX019A gives0181-019A+18=-1, so the actual production
visibility lookup publishes false. The next CPU dispatch10834 therefore sees
the real valid cached false and increments its counter0→1. Current geometry is
already visible at that post-CPU callback, but CPU deliberately consumes the
preceding publication. End10834 publishes true; CPU10835 resets the counter.
This is precisely native `sub_13EFC` / loc13F18/13F28
(`docs/skdisasm/sonic3k.asm:26856–26885`) for those inputs, and the native
Render_Sprites inclusive left-edge test (:36376–36395). Counter/cache/width tuning
would hide the wrong camera/P1 consequence. No CPU runtime defect is proven.
Current minX/target80 and maxX/target9AA→9CC→9EF are far from this camera X;
the observation does not prove which earlier carrier/bounds/history write
created its one-pixel difference.

| Raw row | Recorded native Vblank word / LF | Engine real object VInt / object LF / level LF | Actual recording cursor during row → after row |
| --- | --- | --- | --- |
|10833 (2A51)|944C /2A13=10771|37964 /926 /10771|38003→38004|
|10834 (2A52)|944D /2A14=10772|37965 /927 /10772|38004→38005|
|10835 (2A53)|944E /2A15=10773|37966 /928 /10773|38005→38006|

These are recorded/actual owner clocks, not fitted offsets or EngineTiming0.
Recording start index27170 is an actual driver field; P1 input0008 on these
native rows. Dynamic-art movieLogicalFrame is the local presentation10833→10834
at raw10833, not the absolute BK2 cursor38003. No full native VInt is fabricated.

### Exact source gap and independent disconfirming design

Camera.computeNextHorizontalCameraX (:491–570) selects focused P1, delayed
position history or forced coordinates; native MoveCameraX
(:38400–38473) likewise reads H_scroll_frame_offset and a selected Pos_table
word, or x_pos. R12 did **not** capture actual focused P1/camera history input,
horizScrollDelayFrames, forcedScrollRequested/X, fastScrollCap or the one-shot
clamp latch at camera entry. Camera-entry callback precedes the owner; its next
sample follows camera/fixed objects/ScreenEvents. It cannot distinguish those
upstream writes just from the eventual copied X. Existing native aux through10835
was actually inventoried: camera/history keys are `pos_table_index` and camera
XY copies only; no H_scroll_frame_offset, selected Pos_table contents or forced
XY fields. `sonic_record_pos_per_frame` is advertised but missing. Native final
P1 physics poses exist; they do not establish the selected camera input phase.
No shared Camera/P1/allocator/API correction is therefore justified.

Proposed independent disconfirming control, **not executed**: declare body X=W,
width24, publish via the real refresh owner at copied cameraW+24 (visible/reset0)
and W+25 (offscreen/increment1), then run the following NORMAL CPU dispatch.
Vary physical camera independently of the copy to test publication ownership;
do not force flags/counters. This should be green on current code and disconfirm
the CPU/visibility owner. A genuine camera-producer old-red needs the missing
native input/history/latch contract first, with production motion driving it;
assigning expected camera0199 or widening the edge would not be a valid test.
This is a precise causal handback, not an implemented source repair.

### One newly required existing full canonical on unchanged production

Root separately authorized the maintained full canonical because R11 only
qualified through7808. Temporary R12 source and two exact compiled classes were
removed under writer lease after facts were durable. The existing maintained
class ran once, no observer or source/API changes, on exactbef8 production.
Original movie SHA256 is
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0` at
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2`.

Both runtime invocations use current Main's absolute wrapper via the existing
`$MAIN_WORKSPACE` binding, own cwd/target, `--normal -Dmse=off`, Java25/Lua5.4,
EGL surfaceless flag, nullAL, unset DISPLAY/WAYLAND_DISPLAY, original absolute
three ROM properties and single reusable fork/alphabetical flags recorded in
R10/R11 above. Original bootstrap/fixtures/input/lag are unchanged.

| Handle / exact selection | Completed result | Queue / Maven / JUnit / finish UTC |
| --- | --- | --- |
|50567, `-DskipTests test-compile`|build success, no tests|build-only /1m14s /n/a /14:57:11|
|68487, `-Ptrace-segments -Dtest=TestS3kHczSliceCpuVisibilityWitness#replayMatchesTrace`|1/1F/0E/0S, strict104 spans=92physics+12animation,10773 compared rows through raw10835, warning/bootstrap0|6s /32.803s /7.446s /14:58:17|
|17194, `-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`|1/1F/0E/0S, two actual failure axes below|32s /50.504s /25.835s /15:05:18|

68487 retains first9482 air1/0, first animation9486 player_animation_id0000/0005,
eight missing advertised aux schemas, and the one-frame10834 counter0/1 span.
Its short window is not improvement proof against whole-slice4353/4699 totals.

17194 actual gameplay reports have cursor-derived complete=true against these
unchanged fixture lengths (LiveTraceComparator:712–713 closes at frameCount):

| Segment | Reference rows fully compared | Physics / animation errors | Lag |
| --- | --- | --- | --- |
|0|2290|0/0|0|
|2|4023|0/0|11|
|4|8235|0/0|14|
|6|4103|0/0|16|
|8|7175|0/0|62|
|9|3574|0/0|55|
|11 returned HCZ|14208|107498/18114|59|

All listed windows have zero warnings/bootstrap errors. Returned HCZ's **125612
is total across both groups**, despite the assertion's misleading “physics
comparator errors” label. Matched pre-R11 Root93212 had133258=113873physics+
19385animation over the same14208 rows, first non-camera7807 sidekick_y.
Current first non-camera is **8329 y_speed**, native0000/engine-0680 (delta1664).
The total reduction7646 is6375physics+1271animation; it is not a parity or
regression waiver, and earlier camera/animation errors remain. No source
investigation or repair is performed at8329.

The other actual failure axis remains **segment11 giant_ring exit never observed
within its boundary window**. No onward segment or playable exit is qualified.
Dynamic-art gap journal has11gaps/0failures; this does not waive suppressed
unmatched hardware completions in cleanup: raw10162 PRE_MAIN_LOOP KOS_DECOMPRESSION_QUEUE#220,
10163 POST_OBJECTS KOS_MODULE_QUEUE#154,14172 PRE_MAIN_LOOP
KOS_DECOMPRESSION_QUEUE#221 and14173 POST_OBJECTS KOS_MODULE_QUEUE#155, each with no matching pending engine job. Their baseline
identity/causality is not newly attributed; no readiness fabrication or retry.

Stopping boundary: read-only slice cause and exact missing upstream owner inputs,
plus the one required full canonical's concrete next non-camera8329 frontier and
still missing exit. No R12 runtime/test fix, second witness/full slice/alternate/
native/media/guard/broad run, source integration or push. Root owns further
owner authorization, broad smoke and delivery. Prior focused/guard successes
retain their original R11 scope, not new R12 passes. Consumed own diagnostics are
removed under metadata lease alone after facts are durable:24 exact own report/
XML/text/owner files, no archive. Native4fdf, private config, useful images and
foreign performance artifacts remain preserved. Temporary source/two classes
are absent, all own jobs terminal, and no further selection is planned.


## R13 residency smoke regressions: native row pointers and DDZ fixture entry — 2026-10-17

Owned3aa, `bugfix/ai-s3k-residency-smoke-regressions-gpt61`, starts at R12bd2.
Pre-edit merge25d3500cd retains both complete Root02b and R12 evidence suffixes;
production/maintained tests at that merge match bef8/02b. R10fa/R11cd94/R12bd2
refs, native4fdf versus gitlink322 and private regular config are preserved.
Root's broad15826 stays foreign/frozen; this lane launches no broad, trace,
native, media, ancestor sweep or extra worker/tree and makes no Root/Main write.

### Two distinct causes, with actual old-reds

Root's observed Main803 matched baseline89086 is18/1F/0E/0S; DDZ/LBZ/S2/Results
pass and its sole GLFW failure is inherited. Own pre-edit43333 reproduces exactly
**15/1F/14E/0S**: LBZ `launchStartReframesDeathEggSmallBackgroundRows`34/17,
and DDZ palette3 + stars11 all throw `$FF82AF: 128x128 definition 20` from the
fixture's synthetic `resolveGroundAttachment` (Builder577). This is not evidence
that unknown definition RAM should become zero or a decoded fallback.

DDZ is a fixture-inlet correction, with no DDZ runtime edit. Native Sonic_Init
(disassembly21938–21978) returns after initialization without a floor probe;
DDZ_ScreenInit allocates loc81492 and loc81554 installs object_control81 before
ordinary movement. The two component-test boot helpers now declare the existing
`withFreshLevelStartLifecycle`, as the production DDZ lifecycle tests already do.
A separate pure-lookup control reads the actual ROM schema20×6, FG row2 pointer
80B0 plus unsigned-word column1FF ->82AF, byte14hex/20. The cold load has no
producer for definition20. It still throws the full24-bit FF82AF refusal under
that explicitly undeclared terrain probe; no region, empty asset or zone carve-out
is invented. The existing native flight/star/palette behavior checks remain intact.

LBZ is a production pointer-owner repair. Native `LBZ2BGE_Normal/loc_544C6`
(disassembly111493–111532), a3=Level_layout_main+2, copies WORD pointers
8(a3)->(a3), C(a3)->4(a3); it does not copy row bytes. Actual current ROM LBZ2
layout28A6FE has FGwidth154, BGwidth4 and BG row0..3 pointers8EF8/8EFC/8F00/8F04.
The old byte-row implementation used mapWidth154, overwriting overlapping native
row storage after R10 made encoded/decoded aliases coherent. The old test also
seeded154 BG columns; it now declares the real four-column geometry.

The independent short control loads the production ROM, verifies actual BG source
bytes against ROM and calls the exact private reframe owner. Genuine old-red5006
is **1/1F/0E/0S**, first encoded index10 expected8F (pointer8F00) / actual8E
(pointer8EF8), before production changes. Its oracle requires the complete1000
encoded bytes to differ only in the two native destination pointer words.
Final16322 passes that same oracle. It uses no trace rows, forced blocks or RAM.

### Internal owner, mutation and rewind

S3kCollisionLayout changes the two encoded pointer words under COW, syncs the
decoded destination view and rebuilds its inverse alias index. Sonic3kLevel's
unmarked internal helper carries the BG producer flag; LBZ invokes it inside the
existing zone mutation pipeline and requests the existing redraw. A later source
row mutation reaches both logical aliases and the actual collision descriptor.
Two forced whole-world recreations each restore every registry key, repeat the
reframe/live ROM-block mutation and run two ordinary forward ticks with exact
whole-world equality. Existing scene swap, retained-tail, positive-ROM, negative/
adjacent/word-wrap, live-asset mutation and replay controls remain unchanged/green.
No new persistent field, public Mod API shape/pin, general RAM bus, shared movement,
CPU, counter, camera, metric, timing-authority or readiness change is introduced.

Frozen runtime path+NUL+bytes sorted SHA256:
`363e7328c6f0834b788ccdd08de170951a3bfe85f1307445e57838b2009c332a`.
Runtime plus three changed test files:
`581a266573ad547be61dd4a9b94ab405df62c7b14fb8ec8c18112eb29ff79579`.
Root's exact-hash read-only review found no material issue in pointer COW, decoded
sync, alias reindex/write/restore, producer flag or pipeline redraw; it ran no
tests. That inspection is reused without another unchanged review round.

### Exact bounded verification

Same original absolute Main/ROM bindings as R3/R10, own cwd/target, current Main
wrapper, normal lane, Java25/Lua5.4, EGL/nullAL/no ambient display, one fork:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" <selection> -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

| Owned session / selection | Tests/F/E/S | Queue seconds / Maven / finished UTC |
| --- | --- | --- |
|43333, `-Dtest=TestDdzRomPalettes,TestDdzSuperStars,TestSonic3kLbzLaunchSignals#launchStartReframesDeathEggSmallBackgroundRows` on pre-edit25d|15/1/14/0|522 /30.677s /15:30:21|
|5006, `-Dtest=TestSonic3kLbzLaunchSignals#launchRepointsTheRomRowsWithoutOverwritingTheirByteOwners`, test-only diff on25d|1/1/0/0|27 /26.853s /15:33:24|
|16322, focused selector below, final frozen runtime/tests|86/0/0/0|101 /1m20s /15:38:47|
|48957, affected guards below, same frozen runtime/tests|118/0/0/0|39 /35.007s /15:40:22|

16322 exact selector:
`-Dtest=TestDdzRomPalettes,TestDdzSuperStars,TestSonic3kLbzLaunchSignals#launchStartReframesDeathEggSmallBackgroundRows+launchRepointsTheRomRowsWithoutOverwritingTheirByteOwners+repointedRowsKeepMutationAliasesAfterTwoWholeWorldRestores,TestS3kNativeWordLayoutResidency,TestSonic3kLevelOverlayRewindIsolation,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface`.
Breakdown: DDZ4+11, LBZ3, native-word4 + overlay3, mandatory S3K4=60
(including both loading packages), exact API-pin1. No skips.

48957 exact selection:
`-Pguards -Dtest=TestArchitecturalSourceGuard,TestPerGameRuleArchitectureGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceRunVblankClockAuthorityGuard,TestRewindArchitectureGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard`.
Breakdown72+3+25+10+2+4+1+1. No skips, guard budgets/keys unchanged.
All four requests are terminal. Useful work was not separately metered; this
slice's elapsed time stayed below90 minutes even before excluding queue waits.
Consumed own diagnostics are removed under metadata_slot alone, without archives.

### Limits and handback

This qualifies these two smoke-regression causes and their bounded consumers,
not a complete smoke/full-suite or campaign. Root owns composed validation and
integration/push. No new HCZ run occurs: R12's full returned-HCZ14208 rows /
125612 all-group errors / first non-camera8329y_speed0000/-0680 / absent giant_ring
exit remains historical bef8 evidence, not a new pass on this source. Current
slice/alternate/GLFW/native-pixel/whole-route qualifications are not inferred.
No8329, CPU visibility, camera-input or unknown RAM owner is repaired here.

## R14 slice camera-entry history inputs; no Camera or CPU repair — 2026-10-17

Owned3aa, `bugfix/ai-s3k-hcz-slice-camera-input-gpt61`, base0cd178d8f (R13).
Production and maintained tests are unchanged. R10fa/R11cd94/R12bd2/R13refs,
native4fdf versus gitlink322 and private regular0600 config remain preserved.
No Root/Main write, child, new tree, native/media execution or broad selection.
Root's15826 is foreign; its source/output is untouched.

### Original input identity, exact invocation and strict result

This is the original **82EA slice**, not AD40 complete-emerald chain:
`src/test/resources/traces/s3k/hcz_completerun`, using
`src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2`, SHA256
`82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`.
Unchanged compressed fixture hashes, SHA256:

| File | Hash |
| --- | --- |
|physics.csv.gz|f5b2f7b97951a3b8897d2e3f96c1a8155cd2bcf5d82410a441f8b376c2020337|
|aux_state.jsonl.gz|c87c92393e09e050012001296009145d86018f55e4cc7a16a12862e7f61cef77|
|metadata.json|7e115e9488de8be8954390c7ef01cc75fc939e61f005b1b30563c6fe01ef1f33|
|hardware_timing.jsonl|f055e4863d0048dd5143d353ad5946544a09da9d14325fe0fdf113a3d002a811|

Temporary `TestS3kHczSliceCameraInputWitness` extends the original maintained
driver with only its passive observation and lastRaw10835 bound. Source SHA256
`02cae7c30522c0e70f73c3731385366e7b038c0e5aa52dceb909083a63d0b1ec`.
Original bootstrap/input/lag and strict comparison remain active. From own cwd,
current Main absolute wrapper and the original absolute ROM bindings used above:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  -Ptrace-segments '-Dtest=TestS3kHczSliceCameraInputWitness#replayMatchesTrace' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Build-only82622, `-DskipTests test-compile -B`, succeeds at15:51:12UTC,
Maven30.933s; no tests. Sole63258 finishes16:09:07UTC, exit1,
**1/1F/0E/0S**, queue1026s, Maven30.850s, JUnit7.121s. It compares10773 rows
through raw10835 inclusive, **104 spans=92physics+12animation**, no warning or
bootstrap error/warning. First9482air1/0; first animation9486player_animation_id
0000/0005; the one-row10834tails_cpu_respawn_counter0000/0001 remains. Eight
advertised aux schemas are absent, including sonic_record_pos_per_frame and
position/velocity/CPU-normal-step writes, as in R12. Prefix sorted-error SHA256
`5cfd8f6d23cd616b20ed183cd13c8981e333cde522059f79946c87e025694dd1`
is not the complete-slice4353-vector hash. No longer-window total or full route
qualification is inferred from this bounded run.

### Passive seam and actual camera inputs

Existing Camera.UpdateObserver fires before the owner consumes delay/forced
latches. A transparent existing timing observer delegate forwards every original
notification exactly once; actual replay-port raw latch is read after forwarding.
Original comparedSprite identity is returned unchanged. No CPU journal, gameplay
write, clock assignment, controller replacement, API expansion or hydration.
Before/after read assertions preserve Camera.capture, forced fields, P1 pose,
history cursor and both full history arrays. Four camera-entry callbacks and one
forwarded VINT_SERVICE/POST_OBJECTS/PRE_MAIN_LOOP per row are observed. All20
samples/58494bytes complete without observer failure. PRE_MAIN_LOOP is the
post-CPU/camera/events tail, not a pre-CPU hook. Other phases are camera entry,
after POST_OBJECTS, before boundary easing after events, and end-row comparison.

Hexadecimal positions/fractions, decimal delay/history slots:

| Raw | Focused actual P1 at camera entry | vx/g | Delay entry→tail | Latest X/slot | Selected behind/X/slot | Physical/copy X entry→tail/end | maxX/target |
| --- | --- | --- | --- | --- | --- | --- | --- |
|10832|0230.9B00|0A35|2→1|0230/18|1/0226/17|0177→0186|0988|
|10833|0231.CD00|0A32|1→0|023A/19|0/023A/19|0186→019A|09AA|
|10834|023B.FC00|0A2F|0→0|023B/20|0/023B/20|019A→019B|09CC|
|10835|0246.2800|0A2C|0→0|0246/21|0/0246/21|019B→01A6|09EF|

Focused sprite is the real registered main. P1Y0831.EB00, vy0/angle0,
grounded/rolling, no active object-control/OnObj/movement suppression. Camera
320×224, proportional deadzone90..A0, zero shake, Y/copy07CC; frozen/force argument/
forced-request/fast-vertical/deferred-clamp/custom-max-easing/uncapped-left all
false. ForcedXY0, cap18hex, minX/target80, viewportMax7FFF; bounds do not clamp.
Previous end10831 is0177/07CC and each next entry matches the previous endpoint.

The observed owner computes selected226−177−A0=F ->186; then
**23A−186−A0=14 ->19A**; then23B−19A−A0=1 ->19B and246−19B−A0=B ->1A6.
Every step is below cap18. Camera arithmetic is correct for its observed inputs.
At10833 currentP1word231 is nine pixels behind its latest delayed-history23A;
this witness does not identify an intervening object/solid writer. In particular,
it does not prove a carrier, HCZBlock, bounds, CPU or camera-owner correction.
An independent control with this history/current-pose difference would disconfirm
a camera bug; it is not an old-red and is not authored/run. No output0199 fit or
counter/cache change is justified.

### Separate raw, movie, native and engine clocks

Metadata and actual driver start index are27170. The original frame-contiguous
recorder contract writes rowN after core27171+N, using movie input27170+N
(S3KCompleteRunCaptureRunner's documented InputRow/on-frame-end contract).
This is a source-derived mapping corroborated by actual driver cursors, not a
fitted offset or a newly executed native observation. Core endpoints below must
not be substituted for camera-entry PCs inside that frame.

| Raw/index | Movie input→post-advance core endpoint | Recorded native VInt word / LF | Engine object VInt / object LF / level LF | Actual cursor entry→end |
| --- | --- | --- | --- | --- |
|10832 (2A50)|38002→38003|944B /2A12=10770|37963 /925 /10770|38002→38003|
|10833 (2A51)|38003→38004|944C /2A13=10771|37964 /926 /10771|38003→38004|
|10834 (2A52)|38004→38005|944D /2A14=10772|37965 /927 /10772|38004→38005|
|10835 (2A53)|38005→38006|944E /2A15=10773|37966 /928 /10773|38005→38006|

Sprite-manager LF and driver executed counter equal levelLF here. At camera entry,
last-polled BK2 is one less than the current cursor: the driver marks/increments
it after the step. Actual last-polled and recorded P1 masks are8 throughout.
No unbound EngineTiming0 or fabricated high word of native VInt is used.

Existing native final P1 endpoints10832..35 are022F.AE00,0231.E000,023C.0F00,
0246.3B00, Y0831.1900, vx0A35/0A32/0A2F/0A2C. Native copiedcamera0185/0199/
019C/01A6 and Pos_table_index0048/004C/0050/0054 are recorded. These final poses
are not camera-entry P1 or the earlier RecordPos write. Native index is a next-write
byte offset; engine history slot is a current slot. Their numerical values alone
do not establish selected native history or an indexing defect. The actual aux
camera/history key union is only camera_x_copy/camera_y_copy/pos_table_index;
no H_scroll word, table contents, forced state or camera-entry P1 is available.

### Byte-verified native source pins and missing observation contract

Read-only original ROM SHA1`cfbf98c36c776677290a872547ac47c53d2761d6` and
locked-on sonic3k.asm agree on MoveCameraX entry**01C0A8**, bytes
`3811 4A38 F623 6636 3215 671E`; DeformBgLayer entry**01BFB0**, bytes
`4A38 EE30 6702 4E75`. Source loc1BFF0 sets a1=EE78(cameraX),a2=EE14(bounds),
a4=EE00(scroll amount),a5=EE24(H_scroll),a6=E500(Pos_table). Nonzero H_scroll
subtracts0100, derives byte displacement4×(remaining high byte)+4, subtracts that
from next-write indexEE26, reads(a6,d0.w), masks7FFF, then uses the ordinary
90..A0 deadzone/cap18. DelayN thus selects N−1 chronological frames behind the
last recorded write, consistent with the engine rule for equivalent histories.

**Pin caveat, no capture-authority edit:** retained recorder/docs name10D80 as
Sonic_RecordPos entry. Actual original ROM has entry**010D66**
`4A78 FFE8 6634 B0FC B000 662C`; index read010D72, X write**010D7E**
`32E8 0010`, Y write**010D82**`32E8 0014`, increment**010D86**`5838 EE27`,
RTS010D9E. **010D80 is the0010 operand word, not an instruction entry.** Existing
pins, native capture code and canonical fixtures are untouched. Any later native
plan must check expected ROM bytes before registering callbacks, count/order
real instructions and separate RecordPos pre/post from camera-entry phases.

Native full RAM addresses (FFxxxx hardware, xxxx mainmemory): P1B000 with x10 /
x-sub12 / y14 / y-sub16; Pos_tableE500..E5FF, indexEE26, H_scrollEE24,
physical cameraEE78/EE7C, published copiesEE80/EE84, Scroll_lockEE0A,
Deform_lockEE30, Teleport_activeF623, force flagF676/forcedXF678/YF67C.
Normal LevelLoop calls Process_Sprites before DeformBgLayer then ScreenEvents;
P1 calls RecordPos after movement/display/super processing, before water/animation/
touch and later object slots. Deform checks locks, clears forced flag/delay and
selects forced coordinates when requested, then calls MoveCameraX. Exact missing
native question is P1/history/index/delay/forced/latches at those distinct PCs,
not a final-pose inference.

Bounded retained-domain inventory identifies full64KiB RAM in
`$D/hcz-native7134-selected-wall-v1/input-70207..70210.ram`; provenance is AD40,
not82EA input38003/core38004. Those domains contain history bytes at their own
endpoints only and cannot close this question. No matching82EA full RAM was
identified in that bounded inventory; no domains/probe are widened or executed.
Root owns review of a concrete new native plan and any execution authorization.

R14 stops with precise missing native selected inputs. No Camera/CPU/runtime fix,
new native capture, second prefix, fullslice/canonical/alternate/8329/GLFW/guard/
broad run or act certificate. R12's returned-HCZ8329/absent-exit limit and the
mixed full-slice4353/4699 vectors remain explicit. After durable facts, temporary
observer source/two compiled classes are removed under intended writer lease;
six exact own diagnostics are consumed under metadata_slot alone, no archives.
All own jobs terminal, source/index clean apart from inherited native checkout.

## R15 original native camera/history input plan — 2026-10-17

Owned3aa, `feature/ai-s3k-hcz-native-camera-history-gpt61`, starts clean at
R14docs72d1be929. R10..R14 refs, native4fdf versus gitlink322 and regular0600
private config remain preserved. No Main/Root writes or engine replay/runtime
edit. The native-reference-capture skill/host guide and actual IGpgxHost contract
are read; Root owns the concrete native-plan review boundary before execution.

Retained domains do not close the question: the four full64KiB HCZ7134 dumps
are AD40 inputs70207..10, not82EA input38003/core38004. Earlier retained title
captures are also AD40. No matching82EA full RAM is identified in the bounded
external inventory. BK2's BizState1.0 member is only format text `3\r\n`, not a
saved gameplay state. P2 is inactive throughout original inputs0..38005.

### Frozen helper and compiler-only prerequisite

Owned external task directory `$D/hcz-native-camera-history-r15-v1` contains:

| Input/output | SHA256 |
| --- | --- |
|NativeCameraHistory.cs|a237ef891ab8daa71517a384122d0facd80665a7316cf1bc51a4a0cad04df127|
|plan.json|44c0be5300b0c8592bf52de557bc41445fdf29fb78313e5120d352f6709a9510|
|run_native_camera_history.py|a0312eddf33550510bcccb85bebbd39b75667a046ad5c296aeb49608034e259d|
|NativeCameraHistory.exe|b668c1ebdfa7fdd758c5a710e8c67f6efe7815fb4dcfdf65ad7b17ca601f0c46|

Compiler-only command `python3 "$D/hcz-native-camera-history-r15-v1/run_native_camera_history.py" compile`
finishes0 at16:27:53.327119..914875UTC. It holds own intended-tree writer and
shared build_activity_slot, propagates FDs3/5 to Mono/Roslyn, then releases both.
No host/core/native source is rebuilt or edited, and no GPGX host is instantiated.
An initial pure byte-plan check caught a D1/D0 opcode transcription before source
creation; the checked selected-ready prefix is905104400090. No native retry.

Reuse existing compiled host-bf3c539 SHA256
71b949a6b3ecb0709d4ba73f8cc0752d945a14c15c58f0326fc3adb7d4d4031b;
relevant Bk2/GpgxHost/IGpgxHost paths have empty bf3c→4fdf source diff. Actual
BizHawk2.11 core/common/Newtonsoft DLLs, gpgx.wbx.zst and libwaterboxhost hashes
match retained R9 provenance and are rechecked by the launcher. Critical native
payloads: gpgx.wbx.zst c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12;
libwaterboxhost.so d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78.
Original ROM/BK2/fixture hashes are the unchanged R14 identities above.

### Concrete execution proposal, pending parent technical review

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$D/hcz-native-camera-history-r15-v1/run_native_camera_history.py" run
```

The launcher imports current Main's absolute maven_queue, uses normal default
maven_slot(ownroot,estimate90,kind=native-r15-camera-history), and passes all
execution leases to its one Mono child. No reservation/exclusive override or
outer activity shim. Compiler leases have ended before test admission. Native
execution deadline240s/outer child timeout300s excludes normal queue wait.

Actual GpgxHost.Open receives original ROM and original movie.SyncSettings,
power-on inputs0..38005, every hardware frame, no load-state/position/RAM writes,
no video/audio/media or canonical publication. Callbacks are armed only for
inputs38001..38005 (neighboring raw10831 through10835), after every pinned ROM
opcode has been checked before host creation/registration. Selected native D0
is compared to the actual RAM read, never to an inferred0199 target. RecordPos
preX/preY/postXY/preincrement/postincrement/return and Deform/actual camera call/
entry/history-or-current read/selected-ready/return have separate PC phases and
count/order assertions. Source pins, registers and exact ring arithmetic are
those verified in R14; existing named10D80 is unchanged and never registered.

The bounded external journal contains actual P1/P2 raw fields, Pos_table256,
EE24/26, physical/published camera/bounds/forced/lock fields and real registers,
with before/after read passivity checks and a10MiB limit. Equality controls use
existing fixture values only after advancement: original P1 fullXY/vx/g/status,
P2XY/status/render84, copiedcamera, LF/VIntword/index and CPU0/routine6 for
10832..35. These comparison values never select native inputs or supply gameplay.
Each callback logs actual CompletedFrame separately from established movie/raw
mapping. A postboot trajectory, observer or phase-count mismatch must stop with
partial evidence before any retry. No engine probe or runtime repair is authorized.

At this checkpoint **native execution count is zero**. Exact source/command/
hashes/bootstrap/controls were handed to Root for review; no execution permission
is inferred from the compiler result or from unavailable retained RAM.

### Reviewed provenance correction and the sole actual native pass

Root independently reread all three sources, all14 original ROM pins and the
original10830..35 CSV controls, then authorized one execution with a bounded
Python-only provenance correction. Original ordinal16 plan task/result remains
separate from execution followup ordinal17. No fresh reviewer or C# rebuild.
The reviewed launcher a0312edd is superseded only for identity guarding by
**09acfdbd1768893eb1c8df95738c110452650853af1bf0822d2db85e10ee69f8**.
One verifier checks planfiles_sha256, original ROM SHA1/movie hash, all four
fixture member hashes,14 ROM opcode pins, compiled source/plan/EXE and launcher
freeze initially, immediately inside normal maven_slot before MonoPopen and
after terminal, including the failure path. C#/plan/EXE hashes remain unchanged.
Syntax and readback checks pass; the exact proposed command above runs once.

Sole owned handle**46154** finishes**0**. Normal resource-aware admission is
7GiB/8cores, estimate90, queue**248s**, no override or shim. Submitted
16:34:54.059330UTC, admitted16:39:02.371206; immediate pre-native identity guard
16:39:02.407631 and post-terminal guard16:39:13.106020 both pass. Final timestamp
16:39:13.106040; native stopwatch**10.3544623s**, distinct from queue and parent
wall time. FDs5/6/7 propagate to the only Mono child. All38006 original inputs
0..38005 execute once; callbacks observe only38001..38005, no media/state loads/
manual RAM writes, canonical publication or engine execution.

All four native10832..35 equality controls match: full P1/P2 positions/status,
P1 velocities/routine, P2render84, CPUroutine6/timers0, copiedcamera, LF/VIntword
and index. **60 callbacks**, contiguous sequence0..59, twelve ordered phases per
five observed inputs, satisfy all count/order/passivity checks. Actual CPU PC
matches each registered real instruction boundary. There are three history
reads, two current reads, five moved returns, no forced or early-return branch.
The five actual RecordPos X/Y writes target the observed next-write slot,
advance its byte index by4 and preserve all other Pos_table bytes. No named10D80
hook or capture-authority change is used.

### Actual producer, selected history and current-pose distinction

Positions/indices below are hex; raw/movie/core columns are decimal. Native
callbacks occur at actual CompletedFrame=input, and post-advance core=input+1;
the original raw/movie mapping has not been fitted or shifted.

| Raw / movie input / core after | Actual native RecordPos P1X / index before→after | Native camera-entry P1X / H_scroll | Actual selected source / offset / word | Native physical camera entry→return / end copy |
| --- | --- | --- | --- | --- |
|10831 /38001 /38002|0225.7900 /0040→0044|0225.7900 /0300|history /0038 /0218|0163→0178 /0178|
|10832 /38002 /38003|022F.AE00 /0044→0048|022F.AE00 /0200|history /0040 /0225|0178→0185 /0185|
|10833 /38003 /38004|0239.E000 /0048→004C|0231.E000 /0100|history /0048 /0239|0185→0199 /0199|
|10834 /38004 /38005|023C.0F00 /004C→0050|023C.0F00 /0000|current /— /023C|0199→019C /019C|
|10835 /38005 /38006|0246.3B00 /0050→0054|0246.3B00 /0000|current /— /0246|019C→01A6 /01A6|

P1Y is0831.1900. Actual full native VInt0000944A..0000944E and LF2A11..2A15
are constant across the distinct callback phases of their corresponding frame.
At camera entry A0FFFFB000, force/scroll-lock/deform-lock/teleport all0. For the
history branches, actual D1 byte displacement is0C/08/04 and post-decrement
H_scroll0200/0100/0000; offsets selectFFE538/FFE540/FFE548. No native-selected
history word is reverse-inferred from final pose or camera output.

Physical camera and publication remain distinct: at moved-return PC1C11C,
physicalX is the new endpoint while copiedX still holds0163/0178/0185/0199/019C;
only end-of-frame copiedX is0178/0185/0199/019C/01A6. This is RAM publication,
not a rendered-frame/VDP claim. Current native minX80 and maxX966/988/9AA/9CC/
9EF do not clamp these moves. Sampled native target wordsEE0C/EE0E are0000/6000,
whereas R14 engine target fields follow its current easing bounds. Their exact
owner-equivalence is not qualified by this observation and is not tuned; current
bounds are remote from cameraX, so neither is a demonstrated cause here.

**At10833 the native input gap is closed:** RecordPos writes0239 atFFE548,
H_scroll0100 subtracts0100 and selects that same latest word with displacement4;
239−185−A0=14 and185+14=199. R14 engine delay1 likewise selects its actual latest
023A, with pre186, and23A−186−A0=14 ->19A. Native/engine implement the same
selection arithmetic for their respective inputs. Native10832 selected0225 vs
engine0226 likewise precedes the10834 CPUcounter consequence. At10834 both use
current pose; native023C ->19C versus engine023B ->19B. At10835 the same current
word0246 gives1A6 on both, without removing existing fractional differences.

Native10833 P1 remains0239.E0 through RecordPos return, then is0231.E0 by
Deform. This observed bracket includes later P1 water/touch and object execution;
**the intervening writer is not identified**. Engine R14 latest023A versus current
0231.CD shows the same history/current-pose distinction. No HCZBlock, carrier,
movement/collision or allocator defect follows merely from this bracket.

The causal input owner is P1's position arriving at the history-record point,
already different before this camera consequence. Native10832 P1 is022F.AE00,
engine R14 camera-entry is0230.9B00; native selected0225 came from the neighboring
10831 record, whereas engine selected0226. The earliest defective engine writer
and earlier divergence onset remain unproven. No engine pre-RecordPos full-pose/
terrain witness is supplied by this native-only pass. Missing native inputs are
now observed; missing upstream engine causality is not replaced by a camera or
CPU fit, a source-history compound boundary or a fitted fractional adjustment.

### Independent disconfirming design and stopping limit

Smallest proposed control, not authored/run here: use a real playable's production
history-record point after movement, then a legitimate solid-object side correction
that changes current pose. Delay1 must read the recorded word; delay0 must read
the corrected current word, alongside the existing forced-scroll control. Use
declared geometry/native rules rather than replay coordinates/history injection;
if made stateful, capture/recreate/forward twice with the full world. This is
expected to be green and disconfirm a Camera/order defect. It is **not** a genuine
old-red and cannot authorize any shared runtime repair. Further work must identify
the earlier P1 writer; no additional engine/native probe is launched by this task.

Inspected native outputs and SHA256 before consumption:

| Artifact | Bytes | SHA256 |
| --- | --- | --- |
|callbacks.jsonl|139705|158991c7fcf745f80a95d8620e3dc8bb6c70c1761c9183b4a75759367395126e|
|boundaries.jsonl|11973|a523f0a16c7716f637a181ac5e6914d69fcd77b3765e2ec0607501c69b4a7024|
|completion.json|263|b76af91085b6a720813620c560be726422bb77460465d5110444bf03430e2f02|
|host-settings.json|738|ed9d54360bcd6639229bacff00bce004230d76a96d521d884fe214ad51c54792|

All own jobs terminal; no postboot mismatch or retry. No engine/runtime/API/
fixture/pin edit, new native build/profile, media, broader route or extra test.
R14's strict104spans/10773row prefix and mixed full4353/4699 vectors remain;
R12's8329y_speed/absent returned exit are not requalified. This is one qualified
native input observation, not engine parity or native presentation certification.
The helper is a one-off exact-input diagnostic completed below the hour threshold;
no new general tool/API is promoted. Root owns any next owner authorization,
combined validation, integration and push. Consumed owned native diagnostics and
compiler/helper temporaries are cleaned after durable facts; no archives, foreign
captures, performance artifacts, refs, private config or native4fdf are removed.

R15 cleanup complete: four exact owned helper/source/class/plan files removed
under own writer lease and six consumed native/compile/execution diagnostics
under metadata_slot alone; external task directory empty/removed, no archives.
All prior native captures and foreign artifacts remain untouched.


## Canonical returned HCZ8329 water-skim input owner — 2026-10-17

Owned `t3-fa8da232` starts at warning-fixture53dbe8fd905104a0d21b1ebc8aa0b7edc172342d:
HCZ runtime is Root02b/bef8 plus an unused ending kernel. Kernel16b and warning53d
refs, private config and unknown TraceChaser4fdf checkout remain retained.
R12's original canonical17194 context is immutable
`bd2fb639ebb1fd1db561e954a273349acd68d7fb`, heading
“R12 slice cached visibility consequence and full composed canonical”. Its complete
returned HCZ11 window14208 has125612 errors, first non-camera8329 y_speed0/-680;
this is distinct from the82EA slice9482/10834 work. That camera investigation is
separately owned; no camera/counter/visibility source or probe is changed here.

The original AD40 movie SHA256 is
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
Build-only99674 passed at16:00:39UTC, Maven23.656s. One passive canonical boot
prefix21228 used the maintained AbstractRunChainTest driver/bootstrap/lag policy,
segment11 offset63075, stopping after8331 committed rows (8330 inclusive).
Temporary witness SHA256
`ade3e6f661b9c57af2468f1551524197b39810148351197ed98e38e0a344729c`
used whole-production pre/post brackets and existing CollisionTrace callbacks,
forwarding each existing callback once and restoring its observer in finally.
There was no sensor/controller replacement, input/gameplay write, private
movement entry hook, new shared API, trace hydration or new native execution.

21228 finished16:16:53UTC, queue914s, Maven48.519s/JUnit24.316s,
1/0F0E0S **observer result only**. All five target rows and345 actual callbacks
were sampled; read-only movement-latch equality assertions held. The live
seg11 comparator at cursor8331 remained red:218 errors,0 warnings/bootstrap,
54 lag rows, first non-camera still8329 y_speed0/-680. No closed seg11 report is
claimed: the prefix exception intentionally avoids full-segment closure/assertion.
Earlier segments0/2/4/6/8/9 completed0 errors/warnings/bootstrap (lag0/11/14/16/62/55),
and the11-gap ledger had0 failures. Do not compare this8331-row partial count
against the prior14208-row total as an improvement.

| Row | Native logical word | Engine action held/pressed | Engine post-row velocity X/Y | Engine post-row air/jumping |
| --- | --- | --- | --- | --- |
|8326|5240 (Down+A+B, new A)|3/1|0000/0000|false/false|
|8327|5000 (A+B, no press)|3/0|0000/0000|false/false|
|8328|5000 retained delta state|3/0|08FA/0000|false/false|
|8329|4000 (A, no press)|1/0|08F4/F980|true/true|
|8330|0000|0/0|08AD/FC38|true/true|

Action bits above are engine A=1/B=2/C=4; native A=$40/B=$10/C=$20.
At8329 raw pad pressedMask/actionPressedMask are0; the playable logical press,
movement inputJumpPress and forcedJumpPress are all false. Movement's held and
previous-held inputs remain true. Published raw/effective/admitted scratch fields
are already null at these callbacks; this is **not** an observation inside their
publication/consumption. Prepared movie row71404 holds A with predecessor71403
holding A+B, correctly generating no edge. The outer movie cursor71404→71405,
actual ObjectManager Vbla71359/objectLF2142 and LevelManager LF8276 are distinct
observations, not a fitted clock offset. Across8326–8330 the respective post-row
ranges are movie71402–71406, Vbla71356–71360, objectLF2139–2143, levelLF8273–8277.

The last ground-probe callback at8329 still sees0BBE.EB00/06F1.DA00,
velocity08F4/0000, grounded/not jumping. The first dynamic checkpoint,
HCZ2Wall@4, already sees0BBE.EB00/06F0.DA00,08F4/F980,air/jumping true.
All21 actual dynamic checkpoints retain that velocity. In particular the
physical Sonic3kSpike@17 at0B91/06F3 returns main NONE and changes no velocity;
the later invisible hurt blocks are not the first writer either. No private
`doJump` entry/exit is claimed. The native splash object at384B2 is a gameplay
owner too: `sub_38534` loads Ctrl_1/2_logical into d5 and calls `sub_3857E`.

Source call chain SpriteManager929 → LevelManager1549 →
Sonic3kZoneFeatureProvider861 executes HCZWaterSkimHandler immediately after the
playable slot, before dynamic objects. Its sustain path used **held**
`isJumpPressed()` and `exitWithJump` writes precisely-$680,air and jumping.
The activation inputs at8328 (Y6F1, rolling radius14,water700,X08FA,Yspeed0)
fit ordinary skim activation; private skim participation was not directly
sampled, so that branch membership was initially an inference. The following
independent reproducer resolves the semantic defect without another long probe.

Shipped-ROM SHA1CFBF98C36C776677290A872547AC47C53D2761D6 bytes at385D2 are
`3005024000706678`: move.w d5,d0; andi.w #$70,d0; bne.s38652. This masks the
LOW **pressed** byte, while the direction sustain test masks high held bits.
Disassembly `sonic3k.asm:75403–75422,75465–75468,75522–75534` owns this decision.
There is no FixBugs alternate at this branch. The smallest owner correction is
`isLogicalJumpPressActive()`, preserving raw/logical and native P2 distinctions.

Independent old-red5337 at16:36:07UTC selected only the held-without-logical-edge
and logical-edge/raw-neutral methods in TestHCZWaterSkimHandler. Both naturally
activate via feet/speed checks and public production pad-latch setters at arbitrary
water200, with no private routine/state injection. Result2/2F0E0S,
queue882s/Maven24.283s/JUnit0.233s: held sustain wrongly clears active skim;
logical edge/raw neutral leaves Yspeed0 instead of-$680. No setup/NPE/skip error
is counted as red evidence. Companion curve/P2/registered-world two-forward
controls remain independent of the long prefix.

Focused9066 finished16:39:02UTC, queue55s/Maven1m18s:80/0F0E0S.
The seven selectors matched eight classes: skim13, water-gate3, static adapters4,
bootstrap6, decoding3, AIZ skip8, plus both level-loading classes36+7.
The genuine old-red branches now pass. Curve-held/noedge, separate native P2
logical input and two full registered-world capture/restore/forward comparisons
pass too. The replay drives isolated declared owner phases at arbitrary water200,
not full terrain frames or a cold route; it uses the installed hcz-water-skim
adapter together with all other registered world keys, with no comparator
relaxation. Root source review found no material issue in runtime SHA256
`99957690824c7cb147da4284345aaff9004703e253d4f40fbe6e404a56c488a1`
and test SHA256
`2dd28e9ba775b173c2851effa3be3461bffb4e943ad3ea260328a2791e193cae`.
At that checkpoint, separate authority validation and the authorized simple
post-row prefix were pending; their results and maintained canonical follow below.


Fresh-JVM authority7703 passed25/0F0E0S at16:41:21UTC,
queue20s/Maven29.089s. One newly authorized same-AD40 post-row prefix9812
(no replacement observer) finished16:42:13UTC, queue29s,
Maven50.427s/JUnit25.760s: **1/1F0E0S, not a parity pass**.
Temporary source SHA256
`9f6471fe7973039fb70db1c1353fe4edc979dccfd39d7172bedd4c5666bde4d2`.
Actual8329 velocity0/grounded/not-jumping assertions passed; active skim is now
directly observed true at8328 and false at8329. This confirms the earlier
participation inference and ordinary raised-curve exit. Post8330 is
0BC7.1F00/06EE.7A00,velocity0834/FCA0,inertia08E6,angleF0,grounded/not jumping.
At cursor8331 the live/aborted comparator has206=11physics+195animation,
0 warnings/bootstrap,54 lag, firstNonCameraPhysicsMismatch null. The remaining
recent physics fields are camera_y at6948/6947/6792/6791/6790, not new gameplay
state in the inspected window. Earlier six gameplay segments and11 gaps retain
their prior complete-zero results.

The prefix failed an **extra unsupported diagnostic assertion** expecting zero
animation errors. The agent added that after authoring the initial prefix;
21228's218 total did not record its group split, so195 cannot be labeled new or
inherited from that prior result alone. No baseline zero-animation claim was
justified. This is a diagnostic-assumption failure, not observed proof of a
production regression. The resulting ordinary assertion abort also reports four
unconsumed future hardware completions at10162/10163/14172/14173; this is distinct
from the normal handled prefix-abort lifecycle and does not price a new runtime
input. No assertion is relaxed, no second corrected prefix is silently run, and
no runtime/source widening or canonical request follows automatically. Root
owns classification/next authorization. Runtime and maintained test bytes remain
the independently reviewed,80+25-focused-qualified hashes above. Full-window
animation/camera parity and the giant-ring route exit remain open.


Root's disposition uses the existing immutable R10fa/R11cd94 bounded evidence:
195 animation errors already exist through7135/7808 with first3475
sidekick_animation_id17/1C. Current9812 XML stderr independently logs that same
first3475 sidekick_animation_id17/1C. Thus zero animation was an invalid diagnostic
expectation, not evidence of a new195-error regression. Equal aggregates and the
same first field do **not** prove full vector identity. Current compact reports
retain only group totals and five recent camera entries; first-error logging
retains3475, but the195 entries' exact maximum frame and complete field inventory
are unavailable without new instrumentation. No values are reconstructed or
invented. The existing known first/aggregate limit is retained explicitly.
Temporary prefix source/classes and consumed owned diagnostics are removed
normally, preserving9812=1F in the durable record. Maintained short controls
are unchanged. Root now explicitly authorizes one original maintained full
canonical on the frozen reviewed runtime, without prefix/cutoff/assertion or
observer replacement; its actual next frontier is pending and stops this unit.


The single required maintained canonical49197 is now terminal, unchanged original
AD40/bootstrap/from-boot/default-lag driver, with no cutoff, observer replacement
or extra assertion. It used the plan's environment/all3ROMs and exact selection
`-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical`,
JTO's single EGL token and no additional CLI EGL token. Source is53dbe8fd plus
exact reviewed runtime99957690/test2dd28e9b above. At16:51:28UTC:
**1/1F0E0S**, queue151s, Maven52.840s/JUnit27.372s.

| Gameplay segment | Completed | Errors (physics + animation) | Lag | First non-camera physics |
| --- | --- | --- | --- | --- |
|0/2/4/6/8/9|all complete|0 each|0/11/14/16/62/55|none|
|11 hcz_2|all14208 rows|26153 =23312 +2841|59|8717 sidekick_air native1 / engine0|
|13 hcz_3|incomplete; recent comparison row3208|63295 =54642 +8653|6|287 y native06D4 / engine06DA|

All eight gameplay reports have0 warnings/bootstrap errors. The13-gap ledger
has0 failures, including actual hcz_2→ss_6 at movie-logical77283 and
ss_6→hcz_3 at85096. The manifest calls these transitions giant_ring and stage_exit
(BK2 mode changes77284 and85097); the maintained walker reaches both. Thus the
previous missing returned-HCZ giant-ring exit is no longer the walk blocker,
without certifying this red route or a new character/donor/viewport row.

The chain fails two strict axes: the completed seg11 comparator and seg13 losing
production ownership before source closure at BK2cursor88306, modeLEVEL,
loadGeneration10/progressionZone1/romZone1/act1 (manifest hcz_3 expects act2).
Four suppressed unconsumed hardware completions at raw3256/3257/5356/5357 remain:
Kos#244/KosM#171 and KosM#172 have different descriptors; Kos#245 has no unclaimed
matching submission. They are reported, not hydrated, repriced or repaired.
No special-stage parity total is inferred from gap traversal.

Compared with prior17194's same completed14208-row seg11, first non-camera moves
8329 y_speed0/-680→8717 sidekick_air1/0, and aggregate125612
(107498physics+18114animation)→26153 (23312+2841). Fewer errors are not acceptance;
old full vectors are unavailable, so no exact per-field regression attribution
is claimed beyond the retained first-frontier evidence. Seg13 is a newly reached
incomplete window, not an equal-window baseline gain. Stop here: no further
probe, runtime fix, slice, native or selection is run. Root owns next causality
and composition; ending caller/bus/6+46/Hyper/PAL/PCM acceptance stays open.


## R16 cold Sonic campaign first-death bracket — 2026-10-17

Outcome: the single original-driver observation42765 reproduces **cold death49613**,
**1/1F0E0S**, on production `00c54ec82fa7fcf39822698a0de11df612b0148d`.
It identifies a death interval, **not the rising-death writer or a proven repair**.
No further execution or runtime edit follows. Root's earlier smoke66 on02b and
matched Main803 case24967 (pass) are separate source qualifications:00c54 also
contains later Slot/painter, LRZ/DDZ split, skim and unused-timeline composition.
The maintained campaign test, movie and GameplayCaptureSession have no803→00c54
diff; equal input/test source does not isolate the changed runtime owner.

Owned branch `bugfix/ai-s3k-cold-sonic-campaign-causal-owner-gpt61` safely starts
from R15docs9bdf and fast-forwards to00c54. Prior R10–R15 refs remain. Foreign
TraceChaser checkout4fdf73880f93a3172d23bf9e157b15de3c78fc32 (parent gitlink322851c)
is untouched/unstaged. Private regular0600 config retains exact Main bytes
SHA256 `6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`;
own original mtime is restored and Main bytes/mtime remain unchanged. No Root
uncommitted EGL change was copied. R16 exact task ID/ordinal18 and refreshed24h
expiry are recorded only in the owned registry entry; R15 ordinals16/17 remain.

### Original selection and execution accounting

Original movie: `src/test/resources/routes/s3k/lrz-sonic-cold-hpz-ssz1-dez1-320.bk2`,
SHA256 `41cc9e73afc5a62a901a7ad3afaedc509b19ac68127673be09ca018054b5d9b5`.
The unchanged maintained inlet is Settings(320,sonic,empty-team,donor-off,nulls),
ordinary `boot(ROM,9,0,settings)`, every original movie input followed by normal
`renderFrame()`. Original assertions, semantic rewind spots and45-step replay
windows stay in their original order. The temporary observer stops first death
or input49614; the original death assertion ends this run at49613.

From the owned cwd, with Main tooling and the original absolute ROM paths bound
to the variables below (no ROM copies/links), the sole gameplay command is:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

- Initial build-only wrapper3033316/Maven3033322: process continuity was observed;
  both became absent before7156. Lost command-handle metadata means terminal exit
  **unknown**, not a pass. No live build was duplicated or restarted.
- 7156: testCompile failure before Surefire/boot, exit1, queue0/Maven21.374s,
 17:30:30UTC. The observer's nonexistent getOnObject getter was corrected only
 to existing pure isOnObject. No gameplay executed.
- 19431: repaired build-only `-DskipTests test-compile`, exit0/Maven23.124s,
 17:31:28UTC. No gameplay executed.
- 42765: sole actual gameplay execution, exit1, queue0/Maven52.688s,
 JUnit method30.105s/suite30.410s, finished17:32:52UTC. XML inspected:
 **1/1F0E0S**, exact original `cold death 49613`, no observer error/suppressed
 exception. Original maintained assertion506 is508 with the two diagnostic calls.
 No postboot retry, alternate selection, native/media or broad run.

Temporary helper SHA256
`4c8b355f38f60a7829ab993573db8f92751a9055714492a494253acbd7b75721`;
instrumented maintained source
`1d48fb0977c7029a604cc53480d8d92561b043abae90e36b77c5d1f95c007ad9`;
original restored maintained source
`9616921c0665bd5ac0372beb60eb9fe40d7bab9d469ba68360e9ec36d5b519c0`.
No production source changed. The proxy invokes the original CollisionTrace
method once and returns its original value, copying pooled SensorResult distance/
angle immediately. Pure playable/camera sample-equality checks and trace identity
restoration pass. It calls no isDestroyed accessor and avoids null-spawn default
position getters. No timing authority, input/controller, scene or gameplay writes.

### Directly observed primitives

The bounded journal is5,688,626bytes, SHA256
`b20d6306995be6137a0e41e492a27c961dce634dd60b6227633af237fb23b9a9`:
63 original loop/input entries49551..49613,1860 forwarded callbacks:474 terrain
results,693 solid starts and693 solid results. Only **SszSolidCloudObjectInstance**
and **SSZHPZTeleporterObjectInstance** are named by solid callbacks (ten clouds
plus one teleporter per input). No touch/damage callback exists in CollisionTrace.
All recorded samples have rings0, no shield/hurt/invulnerability; the earlier
ring-loss/trajectory producer is outside this window. Death first appears49613.

| Actual boundary | P1 centre16.16 X/Y | vx / vy / g | object VInt / object LF / level LF / sprite LF | State |
| --- | --- | --- | --- | --- |
| after input49612 / before49613 |19C0.7D00 /0620.4E00|-374 /-712 /-786|49543 /10365 /10364 /10364|alive, air/jump/roll; logical24|
|49613 last terrain result, ceiling_5 null|19BF.2A00 /061D.8600|-339 /-656 /-786|49543 /10365 /10365 /10365|still alive, air/jump/roll|
|49613 first cloud checkpoint START, before resolution|19BF.2A00 /0618.8600|0 /-1792 /0|49544 /10366 /10365 /10365|dead, air/jump, roll cleared|
|49613 after original step/render|19BF.2A00 /0618.8600|0 /-1792 /0|49544 /10366 /10365 /10365|same dead pose; lives4|

Movie loop index and actual Bk2FrameInput.frameIndex both equal49613; raw input
`|...R.A..|........|`, P1mask24/action1, P2zero, previous session index49612
before step and49613 afterward. No row/VInt/LF offset is fitted. The routine6
label in this observer is explicitly **derived from dead/hurt**, not native RAM
or an observed engine routine byte; nullable routine override stays null.
Camera physical/published19A0/05C0 and current/target bounds are locked throughout
this window; the frozen bit changes false→true at the first observed death.

Before death, nearby ordered snapshots show parent **Mecha slot6** at19C0/0604,
routine10/timer22/mapping5, collision86/property2/hits2, invulnerability/flash0,
not defeated; child10 is at19C0/0604 but its collision byte is **not sampled**.
Afterward parent timer21/mapping3, same position/86/hits2. Other nearby entries:
path swap4, lost ring5, roaming clouds14–16. These before/after owner views are
not a selected touch-provider record or a full SST execution journal.

### Source route and exact missing evidence

`LevelFrameStep` prepares frame-start touch snapshots, runs player physics, then
fixed objects and dynamic SST execution. `SpriteManager.tickPlayablePhysics`
runs movement/terrain, history/water/display/animation and then
`LevelManager.applyTouchResponses` before the dynamic object loop.
`ObjectTouchResponseController` decodes80-category as HURT; its shared route is
handleTouchResponse→applyHurt→applyHurtOrDeath→private applyDeath when not blocked,
without rings/shield. Separate movement/custom callers can reach applyDeath or setDead directly. The Mecha parent/secondary child and cloud update bodies inspected
contain no direct damage/death call. **None of these source facts proves which
call executed during the unobserved interval.**

Native source `Touch_ChkValue`→`Touch_ChkHurt`→`Touch_Hurt`→`HurtCharacter`→
`loc_10350/loc_1036E` supplies the corresponding shared damage route; rolling
alone does not turn a harmful80-category object into an attackable enemy.
`sub_7D2D8`/byte_7D2FC and `sub_7D260`/byte_7D280 separately produce parent and
child collision bytes. Read directly from the canonical user ROM
(SHA1 CFBF98C36C776677290A872547AC47C53D2761D6): parent mapping5 at07D301=86,
child mapping5 at07D294..97=00000000, Touch_Sizes entry6 at00FF6E..6F=1010.
These are source/ROM controls, **not captured native state or proof that either
provider was selected**. No native execution occurred.

The first-dead stack starts in the observer proxy at onSolidCheckpointStart and
ObjectSolidContactController.resolveCheckpointBatchInternal. That callback runs
**before** resolveCheckpointForPlayer; the cloud is already looking at a dead
player. This stack locates the observation only and cannot attribute the kill
to the cloud. Shared TouchResponse and custom damage/update execution are not
reported by this proxy. The precise remaining gap is the first rising-dead
writer/caller/cause, including the selected provider if it is a touch call.
Only after that fact could a short independent ROM-semantic old-red be designed
for the actual owner; no camera/CPU/timing/terrain/touch correction is proposed.

After durable extraction the temporary adapter, modified maintained source,
exact compiled adapter/instrumented outer class and owned raw JSON/XML/text are
removed under the normal intended-tree/metadata leases. No archive is created.
The final change is documentation only; full campaign completion/native parity,
regression ancestry and Root smoke/delivery remain open. No live R16 jobs remain.


## R17 cold Sonic campaign rising-death writer — 2026-10-17

The next single original maintained-case prefix48401 closes R16's missing engine
writer fact. At original **outer movieLoop49613 / Bk2FrameInput.frameIndex49613**,
shared touch selects **SszMechaSonicObjectInstance**, category **HURT**,
**cause NORMAL**, **hadRings false**, sourceX6592/19C0. Its actual call reaches
applyHurtOrDeath→applyDeath→the first false→true setDead. This is a directly
observed engine damage path, not the cloud observation stack from R16, a custom
Mecha update kill, or proof of native-correct death. The00c54-vs-Main803 upstream
trajectory/ring producer remains unqualified; no gameplay repair is proposed.

R16 completed first at eeb79fa158b554899802586eebb200eab9faf8a2; R17 uses that
same branch/tree and production00c54, with only authorized temporary diagnostics.
Exact returned task ID ordinal19 is separate in the owned registry, with24h
expiry; R15/R16 histories and all prior refs remain. No Root/Main/native writes.

### Frozen diagnostic and exact run

Three temporary source hashes, inspected before execution and unchanged through
terminal48401:

| Source | SHA256 |
| --- | --- |
|AbstractPlayableSprite.java|4fe85619e72be3fcfac44dabe1775d8bffafe6fa8ade9387b071c9ebd259dc8a|
|ObjectTouchResponseController.java|c7dd04e2412652538acbcc773aa5afe99ae46098fab77d16679b534c381bcb96|
|TestS3kLrzHpzSszColdCampaign.java|68263f857f01adfe184b8008b9f0db7ba8d1837ee18c7f705fb8d1cd202a28aa|

Combined ordered path-NUL-byte SHA256
`5b415a708de21bc76e0af402946c69b32239cc98ee2e5ecc8d8acb4013f6dd2c`;
exact three-file diff SHA256
`70402a2d43e5c0e02605fe1be9cbbc90bfbd9892846b2c70cb315447bbff77a3`.
The first diagnostic runs only on !this.dead && incomingdead, before the original
assignment: actual StackWalker caller frames first, then existing scalar pose/
status/rings. applyDeath logs its existing DamageCause immediately before the
original setDead. Shared applyHurt logs only its already-computed cause/hadRings/
sourceX/category and Java class/identity immediately before applyHurtOrDeath;
it calls no additional provider getter. No trace/controller/provider replacement.

A test-local AutoCloseable cursor saves/restores the exact prior
`openggf.diagnostic.r17.cursor` property for each outer and existing replay step;
nested replay restores its outer stamp. Only actual input indices49551..49614
publish a stamp. The property controls diagnostic printing only. Original input,
step/render order, assertions/history, replay capture/restore and camera calls
are unchanged; the prefix cap follows the original loop body. No position,
velocity, timer, rings, RNG, slot, camera, fixture or runtime state is supplied.

66285 build-only `-DskipTests test-compile` passes, Maven displayed01:18min,
finished17:44:46UTC; no tests/gameplay. Sole actual48401 then uses **exactly the
R16 command/environment/all3original-ROM paths and original maintained selector**,
including normal admission, Java25, EGL JTO/CLI flag, nullAL, no ambient display,
Lua5.4 and single-fork/reuse/alphabetical. Same original movie SHA256
41cc9e73afc5a62a901a7ad3afaedc509b19ac68127673be09ca018054b5d9b5.
No trace profile or new input/bootstrap is added.

48401 finishes17:45:46UTC, queue0/Maven51.878s,
JUnit method29.932s/suite30.186s: **1/1F0E0S**, original `cold death49613`.
XML inspected: no diagnostic error/suppressed exception. The original assertion
line506 is507 with the temporary outer resource. No postboot retry/new selection.
The targeted stderr subset has **68 lines/7,745bytes**, SHA256
`08df5b3778c933162337e632b22a4a3643af2a664a39afe051684e4fd484a52c`:
one SHARED_TOUCH_HURT, one APPLY_DEATH, one RISING_DEAD_STACK marker,
64 real stack frames and one RISING_DEAD_POSE. The actual XML is180,261bytes;
no separate journal/archive is made.

### Direct writer/provider witness

SHARED_TOUCH_HURT records provider identity440159038 and player identity1406114969;
APPLY_DEATH and RISING_DEAD_POSE record that same player identity. These identities
are invocation-local Java identities, not native SST addresses or rewind keys.
The real caller stack's relevant prefix is:

```text
AbstractPlayableSprite.setDead:2704
AbstractPlayableSprite.applyDeath:3184
AbstractPlayableSprite.applyHurtOrDeath:3087,3064
ObjectTouchResponseController.applyHurt:1398
ObjectTouchResponseController.handleTouchResponse:1131
ObjectTouchResponseController.processCollisionLoop:700
ObjectTouchResponseController.update:414
ObjectManager.runTouchResponsesForPlayer:625
LevelManager.applyTouchResponses:1335
SpriteManager.tickPlayablePhysics:2113
SpriteManager.processPlayableSlots:925
SpriteManager.update:708
GameLoop.lambda$updateLevelMode$1:1873
GameLoop.lambda$updateLevelMode$2:1876
LevelFrameStep.execute:343
GameLoop.updateLevelMode:1870
GameLoop.stepInternalBody:1451
GameLoop.lambda$stepInternal$11:1174
PlcFrameLifecycleCoordinator.runLogicalIteration:155
GameLoop.lambda$stepInternal$9:1164
TraceSessionLauncher.runProductionIterationIfActive:2981
GameLoop.stepInternal:1180
LevelIterationAdmissionController.runTraceObservedStep:285
GameLoop.step:823
GameplayCaptureSession.step:316
TestS3kLrzHpzSszColdCampaign.verifyDeathEggCampaign:505
```

Line numbers above belong to the temporary diagnostic source, not restored HEAD.
At the first rising-dead assignment, P1 is **19BF.2A00/061D.8600**,
vx=-339/vy=-656/g=-786, angle0, air/jumping/rolling true;
hurt/water/pushing/onObject/objectControl/controlLock false; rings0,
invulnerable false, both invulnerability/invincibility timers0;
oldDead false/incomingDead true. Thus the directly observed kill caller is the
shared touch damage route with the Mecha parent class, **not the secondary
collision child**. No selected native provider or native death state is observed.
R16's separate VInt/LF/camera/parent-slot snapshots remain separate evidence;
this logging stamp is an input cursor, not a fabricated ROM clock.

This result identifies the engine writer/cause/provider only. It does not
isolate which earlier runtime change produced the vulnerable encounter, prove
that shared damage handling is wrong, or justify altering player collision,
Mecha collision bytes, rings, camera, CPU or timing. Root owns the next causal
question and any independent native-backed old-red; no repair/test/run follows.
Temporary three-file edits are restored exactly to eeb79, exact instrumented
outer classes/test cursor are removed, and inspected own XML/text are consumed
under normal leases after durable extraction. Final change: docs only; no live
R17 jobs, no archives, foreignnative4fdf/privateconfig/all refs preserved.


## R18 HCZ8717 column cached-visibility owner witness — 2026-10-17

**Owner witness only; no independent old-red or runtime fix.** The single original
AD40 boot prefix10733 observes the missing local branch condition: at8717 the
actual column/P2 binding is intact, cached visibility is valid/false, rider active
and prior-standing are true, yet updateRider selects **active-hold**. This supports
the column-local cached-render release question, not a shared collision/CPU/
camera/cache correction. Handback precedes any behavioral test or edit.

### Ownership, source and original reference

Owned tree stays t3-3aa3bc3c. Under its intended writer lease, clean R17a1a safely
fast-forwards to Root **f196e95da4583206a1201b3b4d02b1f626f7026e** on new local
`bugfix/ai-s3k-hcz-column-held-release-gpt61`; the prior cold-campaign branch and
R10–R17 results/refs remain. This adopts Root's qualified Slot/ICZ composition,
not uncommitted DEZ work. No Root/Main changes or cold investigation are copied.
Foreign TraceChaser4fdf checkout and private regular0600 config are preserved;
Main config hash/mtime stay unchanged. Exact returned R18 task ID, ordinal20,
workspace/native identity and24h expiry are in **gpt61_hcz_column_visibility_r18**;
R17ordinal19's entry is unchanged.

Original BK2 is the maintained complete-emerald run's
`s3k-sonic-tails-complete-emeralds.bk2`, SHA256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
The manifest/trace/bootstrap/timing/lag/input remain original. hcz_2 metadata has
trace_schema5 and bk2_frame_offset63075. Native AUX was read by its **frame field**,
not JSONL line ordinal (many event records share each frame).

AUX8714..8720 positively binds interactB32E/SST11/code000326B6/subtype02,
column X0E10, P2 standing true throughout, with this published state:

| Trace row | Native P2 status / control / render_flags | Column Y |
| --- | --- | --- |
|8714|09 /03 /84|05A2|
|8715|09 /03 /84|05A2|
|8716|09 /03 /04|05A2|
|8717|0B /00 /04|05A3|
|8718|09 /03 /04|05A3|
|8719|0B /00 /04|05A3|
|8720|09 /03 /04|05A4|

Native physics keeps P2 **0E10.BB00/0572.AE00**, all velocities0. Native CSV frame
220D is row8717; gameplay_frame_counter21D3 and vblank_counter160C are recorded
hex values, not assigned engine clocks. Disassembly pin
044fa46725c71187399e13f5ddb70e11d32dc024: sub_32784/loc_327FC first tests the
held player's cached render_flags sign. loc_328AC sets Air, priority100/control0,
then loc_328BE clears held; it preserves OnObj/velocities. Inactive capture reads
the object's standing bit and clears Air/writes control3. SolidObjectFull's P2
render gate returns without changing its standing bit. These native source facts
explain the question; no new native observation/execution was made.

### Temporary source and single execution

Temporary column logging is the only production-file diff. Removing exactly its
private helper/call lines recovers f196's original bytes, proving all original
conditions, setters, ordering and owner calls remain. One temporary subclass
uses the maintained AbstractRunChainTest from boot through segment11 committed
row8721 (8720 inclusive). Existing before/after-production callbacks forward once;
the original hardware observer/base are untouched. The CollisionTrace proxy
forwards each method once/identical return, reads no pooled sensor, and samples
an actual column/P2 checkpoint after its real callback. Exact owner identity comes
from existing solidExecutionRegistry.currentObject().object(), not XY inference.
It calls no isDestroyed/default null-spawn position getter and replaces no
controller/provider. Primitive sample passivity and original P2 identity checks
pass; no gameplay/private-state/visibility writes or fresh visibility calculation.

Frozen tested source hashes:

| Temporary source | SHA256 |
| --- | --- |
|HCZSpinningColumnObjectInstance.java|c9ddec6836095ab6a1f2d34ec5fb8b5987ddf5ea6f0b3f84e977760ca1bea2bf|
|TestS3kCanonicalHczColumnVisibilityWitness.java|59abba20031c8675b83d637a737b98c412583ddbaf71a36a2d5eaaf82300ef43|

Combined ordered path-NUL-byte SHA256
`cc7f2cf8536a5e701e29f21a2a6411ec51e3f4cfa91558b5e296a78e8cd6ad84`,
verified before/after runtime. Earlier build83124 failed testCompile **before
boot**, exit1/displayedMaven01:13min at18:17:01UTC: the test used package-private
ObjectManager.services. Only that accessor was changed to existing public
GameServices.solidExecutionRegistry; original temporary combined5b5c4b3f was
never executed. Repaired build-only63229 passes at18:17:48UTC, Maven24.681s.

Sole actual10733 uses the original AD40 environment from the owned cwd, with
original absolute ROM paths bound below; JTO has exactly the EGL token, with no
additional CLI GL/timing/lag override:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off -Ptrace-replay \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kCanonicalHczColumnVisibilityWitness#originalAd40ColumnThrough8720' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

10733 finishes18:18:46UTC, queue0/Maven48.264s/JUnit24.602s (suite24.873s),
**1/1F0E0S**: the strict prefix zero-error assertion reports213. No diagnostic
error/suppressed failure or postboot retry. Earlier completed segments0/2/4/6/8/9
compare2290/4023/8235/4103/7175/3574 rows, all physics/animation/warning/bootstrap0,
lag0/11/14/16/62/55. The11-gap diagnostic has0 failures.
The current8721-row hcz_2 prefix has **213=15physics+198animation**, lag59,
0warnings/bootstrap; first non-camera remains **8717 sidekick_air native1/engine0**.
This partial window is not compared as a gain against49197's full14208-row total,
and is not a full canonical, route-exit, independent-BK2 or parity-green result.

### Actual owner sequence and clock separation

Bound column identity1966314043 = checkpoint identity, SST11/sub2; actual P2
identity1751753651/interact11 points to that same column throughout. Six observed
column dispatches8715..8720 have update/manager VInt71745..71750,
objectLF2523..2528, levelLF8657..8662. Entry playback cursor71790..71795,
driver next-logical-row71791..71796 and post-production cursor71791..71796 are
separately recorded. In particular8717 entry cursor71792/next71793 accompanies
VInt71747/objectLF2525/levelLF8659. No EngineTiming0 or fitted row offset is used;
native CSV vblank_counter is not claimed to be this engine object-clock origin.

Engine stores visibility bool/validity and render flips, not a full raw native
render_flags byte. The witness reports those exact fields and bit7 mask, existing
canonical trace_status, and the exact control tuple
(objectControlled,allowsCpu,suppressesMovement), **not invented raw bytes**.
Tuple T/T/T is the column's existing native3 semantic state; F/F/F is released.

| Row | Actual cache at column entry | Rider active / prior standing | Selected existing branch | After production: status / control tuple / Air / OnObj |
| --- | --- | --- | --- | --- |
|8715|valid,true|true/true|hold|09 /TTT /false /true|
|8716|valid,true|true/true|hold|09 /TTT /false /true; publication sets cachefalse|
|8717|valid,false|true/true|hold|09 /TTT /false /true|
|8718|valid,false|true/false|existing !standing release|01 /FFF /false /false|
|8719|valid,false|false/false|inactive return|03 /FFF /true /false|
|8720|valid,false|false/false|inactive return|09 /FFF /false /true|

Preceding8714 publication has valid/true, active/prior-standing true. At8717
column motion05A2→05A3 precedes the real hold branch; P2 remains exactly
0E10.BB00/0572.AE00 with vx/vy/g0, alive/unhurt, control TTT. After update its
private standingLastFrame is cleared as usual. The exact own solid callback is
NONE/standingNowfalse/standingLastFrametrue while player OnObj stays true and
interact binding is preserved. The private rider flag remains false. The next
8718 existing release clears OnObj/control without setting Air. This is a
recorded local follow-on latch/cleanup question, not authority to change shared
solid gating. No geometry, player/camera/CPU or cached flag was changed to obtain
this result.

There are19 JSON samples (prior publication + six before/checkpoint/after sets),
27,647bytes SHA256
`139eac6e3476e057960e7d3436ab12f46d1438c65e8f14302e3d79f79cc98078`.
Owner log30lines/17,236bytes SHA256
`b45f98c1c965a0808e00c12b93fbc2560d66ab52d39fa5cd1e042e62e29c0856`.
Proxy forwarded60 solid starts/120 results/42 terrain-result callbacks without
inspecting sensor pools. Raw facts remain distinct from the native comparison.

Stop at the evidenced local-owner handback before independent old-red/runtime
edit. A later control must account for native retained OnObj/standing and zero
velocities across release/recapture; no such test or repair is authored here.
After durable extraction, restore exact column source/remove temporary test and
instrumented classes, then consume the owned witness/XML/text and seven fresh
trace reports with their owner sidecars under normal metadata admission. No
archive; native4fdf/private config/all refs preserved, no live owned jobs.


### Root native held-file identity gate and corrected HCZ report — 2026-10-17

The later HCZ reporting-only run95476 used the unchanged R20 local column
candidate d811 and reviewed temporary seam a625, on the original AD40 driver
through row8720. It wrote the actual active comparator before the original
prefix abort, after the final playable/dynamic-art/coordinator comparisons,
then retained the strict-zero assertion after cleanup. Actual compared8721 /
loaded reference14208, complete=false, comparator identity871662667. It ended
1 test /1 failure /0 errors /0 skips at18:52:31UTC (queue0, Maven51.190s,
method23.924s): ONLY the final strict0-versus206 assertion failed. The current
partial report contains206 errors,11 physics and195 animation, bootstrap0,
warnings0, lag59, firstNonCameraPhysicsMismatch=null. The matched earlier
10733 window had213 errors,15 physics and198 animation. This qualifies that
bounded group-count comparison and the absence of a current non-camera
physics frontier through8720; it is not strict green, full HCZ parity or
exhaustive identity equality. The JSON retains only five recent camera_y
errors, not an exhaustive error array. Six preceding complete gameplay
windows remain all-group0. The local fix/worker documentation commit and
Root composition remain separate from this report fact.

Native97028's map_files refusal remains the result for the OLD algorithm.
After independent source review, Root compiled the NEW held-file algorithm
once at18:52:05.043398..18:52:05.160192UTC under its worktree build lease plus
shared build-activity lease, inherited descriptors3/5. Compiler terminal0,
no warnings; no native host/core rebuild. Reader source SHA256
`f4f2fbc81b2c7e0680b1596fab14f2795308ade5ca005a2926710f0da2c6ee5b`,
DLL `0bf13e126f7a68da2609809c975c18432edfff261b3d586d5051e081c6383732`.
The guard keeps its original native-library FileStream alive and serializes
FD identity/hash/RX checks against disposal. It joins that held Unix FD's
kernel mount/inode metadata to the two actual loaded native anchor maps and
the complete executable mapping; it compares all pinned RX bytes without
exclusions. Missing/ambiguous/changed identities still refuse. No permission
change or runtime fallback was introduced. This proves kernel-exposed
file-device/inode binding and loaded immutable-program identity, not kernel
struct-file pointer equality, native RW/GOT state or whole-process integrity.

Root reviewed the two updated launcher pins and frozen plan, then ran ONE
new identity-only request88245 with normal7GiB/8-core admission, queue0,
no outer activity shim. The same unchanged probe56768b70 EXE opened the
existing stock host with original82EA SyncSettings only, bound/finalized
identity and disposed it. PID3141538 ran18:53:31.1677954..18:53:33.4358537UTC;
terminal0, `identity_only=true advances=0 callbacks=0 observations=0`.
All initial/immediate-inside-slot/post-terminal frozen artifact checks agreed
on fingerprint `7bf404a519f3a3ede4ad456c4fb2dc7c8fc0d2a146901daa65afe626c2cd0bd8`.
Launcher SHA256 `56a5e34888d17a62181129d8503c5a5de12ec3f8ed932ba506b906bb7250e5ce`,
pre-run plan `a0127bba5f91322a0c362441af01ae4b57a37709fb9a05816b5b66465fdff8fa`.
There was no raw-clock observation, phase-origin measurement, decoded IRQ/SR,
kernel initializer, prepared producer/service-window comparison or readiness
acceptance. The source basis for a later passive clock comparison is the next
bounded question. Six useful owned prototype files remain externally with
24-hour ownership for eventual promotion/accounting, not as a raw-log archive.
No new run log, receipt, pass cache or canonical artifact was written.

Root composition at this checkpoint is8f72fc9, Main remains8030832 develop.
The matched three-step cold contact baseline is separately authorized on803;
no current contact prefix, runtime repair or native correctness follows yet.
Cold/DEZ regressions, later HCZ/ending contexts, complete current smoke/guards
and Main integration/push remain open.

## R19 independent column cached-release old-red — 2026-10-17

**Independent old-red only; production remains unchanged.** After the accepted
R18df2bbf accounting, the same owned branch/tree runs exactly one focused
selection79315. Real factory/allocation/SST capture, visible-held/jump controls
and whole-world recreation/replay pass. The native offscreen release and retained
standing recapture assertions fail at their intended final assertion group,
not during setup or an observer. No runtime edit or another trace/native run is
made; the8717 strict frontier remains unqualified.

### Source, inlet and independent oracle

HEAD is **df2bbf13f93b341f8c576414f68c200512487dcb**, runtime
**f196e95da4583206a1201b3b4d02b1f626f7026e**. The only executable addition is
[TestHczSpinningColumnCachedRelease](../../../../src/test/java/com/openggf/game/sonic3k/objects/TestHczSpinningColumnCachedRelease.java).
Test SHA256 `c35d447ad95e556324022e1b6f77414bb4afc056e3a8506f4add69b3e23a313b`;
unchanged production column SHA256
`9d93fcd89b25135a60f430fb16b48c0aed877227e652d91dc7af2ecf06e5857c`,
verified after execution. R18's c9ddec hash described temporary logging, not this
restored production file. Exact R19 task/ordinal21/24h expiry are recorded in
`gpt61_hcz_column_release_oldred_r19`; R18ordinal20 and all earlier refs/results
remain. Native4fdf/private regular0600 config and Main hash/mtime are preserved.

The test loads the actual ROM-backed HCZ2 world, creates a stationary column
through the current game's registry factory inside the normal dynamic allocator,
and admits its first dispatch normally. It assigns no SST slot or timer. Initial
component geometry is declared independently: column0400/0400, Tails X=column+8,
positive vy0100, and top overlap8 derived from the actual FullSolid air half-height
and loaded Tails radius (`relY=y-objectY+4+d2+radius`, native valid window[0,10hex)).
Fractions3A00/7B00 are independent inlet values. Real SST execution plus the real
inline FullSolid callback first establishes OnObj/riding/native object standing;
the next real owning dispatch captures the player. No manual onSolidContact,
private Rider seeding, trace hydration or direct owner invocation is used.

Visibility is published by SpriteManager.refreshPlayableRenderFlags. Physical
camera/column admission stay fixed; only the existing copied-camera inlet changes
using the player's real loaded render-body width. The hidden control places the
copy one pixel beyond the body's left visibility edge; the visible control places
the body inside the viewport. Cached validity and the resulting visibility are
asserted. Neither the cached flag nor any private rider field is written.
The test intentionally ticks the real object/SST component, not the CPU/movement/
camera loop; it claims the local owner contract, not a complete route.

Source pin044fa46725c71187399e13f5ddb70e11d32dc024 remains the authority:
sub_32784/loc_327FC first tests held player's cached render_flags, before routine
or standing. loc_328AC sets Air, priority100hex and control0, then loc_328BE clears
held. It preserves OnObj/pose/fractions/velocities. Priority100 is bucket2,
not bucket1. Inactive sub_32784 tests the **column's** native per-player standing
bit, and active !standing goes straight to loc_328BE without generic release.
SolidObjectFull's offscreen P2 return precedes Full_1P and leaves that bit intact.

### One focused execution, results and rewind limits

Build-only prerequisite1696 passes at18:31:26UTC, displayed Maven01:17min;
no runtime tests execute there. Before the sole actual selection, an additional
existing getter is added to its oracle: ObjectManager.hasObjectStandingBit is
the public read of the actual shared native standing latch. This is recorded
separately from getRidingObject. The final test hash above is what79315 compiles
and executes; no production source changes.

From the owned cwd, with MAIN/S1/S2/S3K bound to the same original absolute
Main-wrapper/ROM paths recorded in R18, the exact focused invocation is:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN/tools/testing/maven_queue.py" --normal -Dmse=off \
  "-Dsonic1.rom.path=$S1" "-Dsonic2.rom.path=$S2" "-Ds3k.rom.path=$S3K" \
  '-Dtest=TestHczSpinningColumnCachedRelease' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true \
  -Dsurefire.runOrder=alphabetical test -B
```

Session79315: terminal1 at **18:32:11UTC**, queue0s, Maven25.475s;
**2tests/1F0E0S**, JUnit suite0.782s. The visible-held/newly-pressed-jump control
passes (method0.323s), including native jump vy=-680hex and preservation of
native centreY/fractions. The cached-release method0.180s reaches its final
`native cached release and retained object-standing lifecycle` assertAll:
9 top-level failures (one nests4 control/OnObj failures), not9 failing tests.

At the first actual cached-false dispatch, expected native release is Airtrue/
control0 with X0408 preserved. Actual old owner keeps Airfalse/controlTTT and
performs its shrinking-radius hold: X0408→0407. Y03D0, fractions3A00/7B00 and
all zero velocities remain. OnObj, actual riding binding and native object
standing bit are all true; priority remains1 instead of2. On the next dispatch,
asserted control/OnObj state is false with Air still false, while the shared
standing bit/binding remain. The inactive native recapture and following release
assertions also fail. The source explains this second state through its stale
callback-standing generic release; it is not a newly instrumented branch trace.

Before checking native semantic expectations, the test captures the entire
registered world before visibility flips and after the first false-cache dispatch.
With in-place restore explicitly disabled, it forces recreation and forward
continuation **twice from each boundary**, comparing every registry key through
the existing RewindSnapshotDiff and checking the recreated column/ride relink.
All these checks pass before the final old-red assertions. The second boundary
is honestly the old implementation's **post-visibility held state**, not a proven
native release. Thus deterministic reconstruction is observed; correct release
and correct post-release recreation remain obligations for a later authorized fix.

### Minimal local design handed back, not implemented

Production already exposes services().objectManager().hasObjectStandingBit:
ObjectManager2939 delegates to the shared standing latch. The offscreen inline
branch preserves it and the riding state without issuing a standing callback.
Therefore callback freshness is the wrong production predicate. No new generic
solid/API contract is required. Proposed bounded owner correction is:

1. Read this existing native standing bit for managed column riders; retain only
   clearly isolated callback compatibility for unmanaged direct component tests.
2. For active held riders, test their existing valid cached offscreen flag before
   routine/standing/hold. Apply the native Air/control0/priority100/heldfalse
   release while preserving OnObj/pose/fractions/velocities. The existing generic
   releaseRider must not implement this branch because it clears OnObj.
3. On active !standing, clear only held as loc_328BE requires. Inactive capture
   reads the retained native column standing bit, allowing offscreen recapture.

No shared solid/CPU/camera/render publisher changes, new API, clock/route fit or
fresh visibility calculation is justified. No runtime fix, green release claim,
canonical/independent BK2 requalification or new native observation is included.
The deliberately red component test is retained for the next authorized fix;
consumed two owned JUnit reports are removed under metadata_slot after durable
facts, without archives. All previous qualification limits remain.


## R20 local column release owner and prefix reporting limit — 2026-10-17

**One local owner correction, focused green and native endpoints matched; strict
matched-window totals are not captured.** R19's genuine old-red commit57dd6a035
is preserved. Root authorizes the same owned tree/branch to correct only
HCZSpinningColumnObjectInstance, reviews its exact frozen source with no material
finding, and conditionally permits one originalAD40 prefix after all focused
checks pass. No shared solid, CPU/camera/cache publisher, jump velocity, renderer,
allocator or public API changes are made. No Root/Main writes or integration.

### Exact owner mapping and frozen source

Runtime base remains f196, with HEAD57dd6a035 plus the local owner/test diff;
this is not execution on Root's later composed250f source. Managed standing
reads the existing ObjectManager.hasObjectStandingBit(player,this), not callback
freshness, the player's OnObj flag or another object's ride. Only unmanaged
objects/no ObjectManager retain the old callback compatibility through
tryServices. Active valid cached-false, or existing dead/hurt routine state,
uses the native loc_328AC release before standing/hold: Airtrue, priority100hex
(bucket2), control0, heldfalse, retaining OnObj/pose/fractions/velocities.
Clearing the engine object-mapping projection accompanies native control bit1
release without changing published mapping/animation values. Active !standing
maps only to loc_328BE/heldfalse. Inactive sub_32784 has only its actual native
standing gate, so positive control3 does not block recapture.

The old generic jump release path and all its velocity writes are unchanged.
Dedicated dead/hurt lifecycle cases are not added; that branch is source-backed
mapping to the same helper, not independent qualification of every routine state.
The existing unmanaged test's ordinary-release expectation was wrong for
loc_328BE. It now verifies retained control/mapping/OnObj/pose/fractions/nonzero
velocities/animation, followed by contact recapture with control3 still present.
This strengthens the local source contract, rather than removing a check to
hide a failed managed inlet.

| Frozen file | SHA256 |
| --- | --- |
|HCZSpinningColumnObjectInstance.java|d8119e4afe924b7e76adcc6e8a71bf38c46e1c85067f5f7e5e42cd956cd3b303|
|TestHczSpinningColumnCachedRelease.java (unchanged R19 oracle)|c35d447ad95e556324022e1b6f77414bb4afc056e3a8506f4add69b3e23a313b|
|TestS3kHczSpinningColumn.java|6c7416b9637acce642ee7d0c4f109736ae9f88621cf29829ce577ee73496ebfb|

Ordered path-NUL-byte combined SHA256 is
`8f0266a1fc9e6fe5b211d709555ee746792c021b0728617842ee6c80322ac98f`.
Root independently verifies source pin044fa467's render/routine-before-standing,
ReleaseHeldPlayer/ClearHeldFlag and inactive capture branches, the retained
standing getter and mapping projection. Source/test hashes remain exact through
both actual runs below. Native4fdf checkout, all prior refs/private regular0600
config and Main config bytes/mtime remain untouched. Exact returned R20task ID,
ordinal22 and24h expiry live in gpt61_hcz_column_release_fix_r20; R19ordinal21
and R18ordinal20 accounting remain separate.

### Single affected focused selection

Use the same original absolute S1/S2/S3K paths, current Main absolute wrapper,
owned cwd, Java25/JTO EGL/nullAL/Lua5.4/no ambient display environment as R19,
normal admission, forkCount1/reuseForkstrue/runOrderalphabetical, -Dmse=off and
`test -B`. No trace profile for this component/load selection. Exact selector:

```text
com.openggf.game.sonic3k.objects.TestHczSpinningColumnCachedRelease,com.openggf.tests.TestS3kHczSpinningColumn,com.openggf.game.sonic3k.objects.TestS3kHczMechanismRewind,com.openggf.game.rewind.TestS3kCnzHczRiderRewindMaps#hczSpinningColumnRestoresRiderArrayAndPlayerReference,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils
```

Session62518 terminal0 at **18:38:32UTC**, queue0s, displayedMaven01:19min:
**73/0F0E0S**, all nine actual class XMLs inspected. Counts: new unchanged R19
oracle2, existing column8, mechanism2, exact rider recreation1; mandatory AIZ1
skip8, loading36+7, bootstrap6, decoding3. The managed offscreen release/retained
standing recapture and twice forced whole-world forward cycles at both
before-flip and now-actual release snapshots pass. Visible-held/newly-pressed
jump controls remain green. The separate legacy control proves clear-held
preservation and positive-control recapture. No broad/unchanged134/118 rerun.

### Sole original prefix93493: direct endpoints and reporting gap

Root source approval plus the all-green/zero-skip focus opens the separate gate.
Temporary TestS3kCanonicalHczColumnReleasePrefix SHA256
`c972858eb5a98f58c73e5049a47645c8f508fcf530ccf4767df54dec3a1829fd`
calls the original assertChainReplayThroughSegmentRow(run,11,8721), from boot.
Its existing afterProductionStep hook forwards the original once, then reads
only actual comparator cursor and P2 primitives at committed rows8715..8720;
no owner logs, callback/checkpoint replacement, hardware observer alteration,
new input/bootstrap/lag authority or gameplay writes. Target rows/cursors are
observation boundaries, not runtime conditions. Exact invocation uses R18's
original -Ptrace-replay environment/ROM/flags, replacing only its selector with
`TestS3kCanonicalHczColumnReleasePrefix#originalAd40ColumnThrough8720`.

Session93493 terminal0 at **18:40:29UTC**, queue0s, Maven47.927s,
JUnit **1/0F0E0S**, method23.962s/suite24.191s. Six actual samples are:

| Row | BK2 cursor | Level LF | Status | Control tuple | Air / OnObj |
| --- | --- | --- | --- | --- | --- |
|8715|71791|8657|09|T/T/T|false /true|
|8716|71792|8658|09|T/T/T|false /true|
|8717|71793|8659|0B|F/F/F|true /true|
|8718|71794|8660|09|T/T/T|false /true|
|8719|71795|8661|0B|F/F/F|true /true|
|8720|71796|8662|09|T/T/T|false /true|

Every sample retains X0E10.BB00/Y0572.AE00 and all velocities0, matching the
original native R18 rows. Status is the existing canonical sprite status;
control is its exact semantic tuple, not fabricated raw bytes. The actual8717
release and8718/19/20 recapture/release alternation therefore match the native
endpoint witness. No object VInt clock is inferred from postrow Level LF.

**Measurement limitation found after terminal:** AbstractRunChainTest's deliberate
ReplayPrefixReached catch aborts/cleans the unfinished comparator without writing
or asserting that segment. This temporary postrow observer omitted its aggregate
summary. Thus there is **no seg11 JSON, current error total, per-group totals,
lag/warning/bootstrap summary or firstNonCameraPhysicsMismatch** from this
execution. It cannot be compared numerically as213→206, and JUnit0F cannot be
called strict green. Its first logged all-group mismatch remains row3475
sidekick_animation_id17/1C; totals cannot be reconstructed from a bounded first-
error log. No count is borrowed from an older runtime or inferred from endpoints.

Six completed earlier gameplay reports0/2/4/6/8/9 are all-group zero, complete,
warning/bootstrap0 and lag0/11/14/16/62/55 as in10733; their original windows
are2290/4023/8235/4103/7175/3574 rows. The11-gap report has0 failures. These
completed windows and the direct six endpoints are observed; the unfinished
8721-row current aggregate and any later frontier are **unqualified**.

Stop and hand back the exact reporting gap before another execution/source
variant. Root received it immediately; no retry, full canonical/slice/alternate,
GLFW/native/media/broad run or runtime retuning is implied. Local documentation
may record source/focused/endpoint facts, but a complete matched-window count
requires a separately authorized observer/reporting step.


## R21 actual final-prefix report qualifies the local column repair — 2026-10-17

**The R20 reporting gap is now resolved by an independently authorized reporting-
only execution, not an inferred count or behavioral variant.** The same frozen
one-file runtime correction d8119e4a and unchanged R19 oracle c35d447a remain;
legacy native-clear-held test6c7416b9 is also unchanged. Root reviews the final
report-only temporary source a625ad6c with no material finding before authorizing
one normal wrapper request. No further behavior change or selection is made.
R19 genuine old-red57dd6a035 remains in history; R20 source/focused/native-
endpoint evidence and its original missing-summary limitation remain above.

### Reviewed report phase, identity and actual execution

R21 source prep has its own ordinal23/task. The actual returned execution task
is separately registered as ordinal24 in
`gpt61_hcz_final8720_reporting_execution_r21`, preserving23/22/21/20 and refreshing
24h expiry. Prepared c174f2e4 is an **unexecuted** draft, superseded before any
compiler/JVM by final temporary source SHA256
`a625ad6c56fb2744d42034777c4f5a4e202e5f91b9b447cb4da421f1fe72678b`.
AbstractRunChainTest itself remains SHA256
`967c9896bf566218e9198626ec7d256f181be9fb78ca5abc787d6b82932148bb`.

The temporary subclass forwards existing stepEngineFrame exactly once. It
intercepts only that base's exact private ReplayPrefixReached control exception,
after final playable animation/dynamic-art comparisons and coordinator.afterStep,
before the outer catch calls hardwareTiming.abort and clears the comparator.
It reads the actual current comparator and its immutable loaded TraceData via
read-only reflection; frameCount is frames.size, not a substituted metadata
value. Assertions distinguish **8721 actually compared** from **14208 loaded
reference rows**, and require completefalse/current comparator/metadata identity.
Existing writeChainSegmentReport writes its normal session-owned JSON/owner
sidecar, using the original group+bootstrap accounting invariant. Only diagnostic
maps/output are written. The identical stop exception is rethrown; only after the
original abort/cleanup returns does the test assert the original strict zero.
No endpoint/owner logs, new timing callback, input/lag/bootstrap changes, gameplay
writes or new API are introduced.

Exact sole invocation from the owned cwd, with the original absolute wrapper/
ROM paths bound as in R18 (including original AD40 BK2/manifest/bootstrap):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal -Dmse=off -Ptrace-replay \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kCanonicalHczColumnReleasePrefix#originalAd40ColumnThrough8720' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Session **95476** finishes **18:52:31UTC**, queue0s/Maven51.190s,
JUnit **1/1F0E0S**, method23.924s/suite24.211s. Compiler prerequisite stays
inside this single request. The sole failure is strict0 versus actual206 **after
original cleanup**; there is no observer/identity/reference/accounting error,
postboot retry or additional JVM. All runtime/test/temporary hashes remain exact.

Actual scalar identity/window record:

```text
comparator=871662667 compared=8721 reference=14208 total=206 physics=11 animation=195 bootstrap=0 warnings=0 lag=59 complete=false firstNonCamera=null
```

The current partial seg11 JSON SHA256 is
`f8edb2e08345316394dc995d903c091cfb9974863b863a3dcefb7c5bb7540972`.
Its full available fields and arrays are inspected, not merely a preview:

| Same original returned-HCZ prefix | R18/10733 | R21/95476 |
| --- | --- | --- |
|Compared rows / loaded reference|8721 /14208|8721 /14208|
|Total field errors|213|206|
|Physics / animation errors|15 /198|11 /195|
|First non-camera physics|8717 sidekick_air1/0|none through8720|
|Lag / warning / bootstrap|59 /0 /0|59 /0 /0|
|Complete reference|false|false|

This is an **observed** matched-window aggregate change, not the formerly
forbidden213→206 prediction. The counts reduce by4 physics and3 animation fields;
there is no exhaustive seven-identity set-difference claim. The summary retains
only a five-entry recent mismatch ring, not the full error array. All five
available entries are remaining camera_y errors (repeatCount1 each):

| Row | Native / engine camera_y | Delta |
| --- | --- | --- |
|6948|07C7 /07C9|2|
|6947|07C2 /07C4|2|
|6792|0678 /067E|6|
|6791|0670 /0678|8|
|6790|0668 /0670|8|

The original first logged all-group mismatch is still3475
sidekick_animation_id17/1C. firstNonCameraPhysicsMismatch is actually null,
so the11 physics errors are camera-only through the measured boundary. Strict
camera/animation errors remain red; neither JUnit nor the partial source is
reported as strict green. Exact full residual-array equivalence is not proved
by the bounded recent ring or durable10733 summary.

Six completed earlier gameplay windows0/2/4/6/8/9 remain complete/all-group zero,
warning/bootstrap0, original lag0/11/14/16/62/55 over the same
2290/4023/8235/4103/7175/3574-row references. The actual11-gap report has0 failures.
R20's six postrow endpoints already match original native8715..20, including
8717/8719Air/control release and8718/8720recapture, retaining OnObj/kinematics.
Together with unchanged independent old-red→green and twice-world replay, this
qualifies the **local column owner through8720**. A later non-camera frontier is
not measured. There is no full returned-HCZ/exit, independent slice/alternate,
GLFW, new native/media, wider roster/viewport or auxiliary-channel certificate.
Dedicated hurt/dead lifecycle breadth and other owner handoffs remain outside
this focused proof. Source epoch is f196+this local d811 owner, not Root's later
composition. Root owns combined guards/smoke/integration/push.

After these facts, remove only the temporary prefix source/compiled class and
consume inspected owned JUnit/segment/gap reports and owner sidecars under
metadata_slot alone. Keep the production owner, strengthened legacy test and R19
independent oracle; no raw archive or validation receipt. All earlier refs,
native4fdf/private config/Main identity remain preserved. Normal local runtime/
test+existing-doc commit is authorized after this bounded qualification; no
further execution follows.


#### Source basis after the native identity gate: clocks and observation limits

Bounded source-only research after88245 identifies GPGX pin051d430d3d1b54625f9900c8f152d7f232e06daf,
not another execution. In the cartridge path, system_frame_gen resets only
mcycles_vdp on entry (system.c351–357), then subtracts the completed frame
period from both signed CPU cycles and refresh_cycles at exit (685–689).
The CD path's later rebasing lines are not the MD authority. Overshoot and
relative refresh position survive. Hard reset initializes a nonzero,
seven-MCLK-aligned CPU coordinate and schedules refresh; neither Open nor
frame entry supplies a zero CPU initializer.

Normal m68k_run execution calls the execute hook before opcode fetch,
checks refresh afterward, then executes/charges the opcode (m68kcpu.c299–317).
If C>=R at that check, R becomes C+896 and C gains14 MCLK. The delayed-IRQ
path m68k_set_irq_delay (233–247) directly fetches/executes one instruction
without the normal callback or refresh loop. Execute callbacks therefore
cannot serve as a complete generic handler ledger. Read hooks follow their
mapped handler while write hooks precede it (m68kcpu.h861–866,881–886,923–929);
neither is a uniform bus phase. Interrupt acceptance can precede the first
callback or occur inside an SR write. int_level alone does not supply the
separate SR mask, assertion/acknowledgment history, live vector or bus waits.

The stock ELF b4cc6dabc069a6f1b87790212d80f665d216e603aa4990955cc816d5bf98d218
corroborates the signed fields and ordering: m68k36F025767E0,
m68k_run36F000EF7D0, interrupt check36F00033F10. A source-only disconfirming
local comparison could use the actual service instruction1C1A tst.w(FF0E)
to1C1E, with a qualified same-run pair: base12 cycles=84MCLK, or98 with
one refresh. This is a proposal, not an observed pair or a production budget.
Actual FIFO-head/producer qualification, scheduler-boundary exclusion and
final provenance would still be needed. The unused S3kKosBusTimeline has no
production consumer; engine-owned Kos state, explicit rebase/phase origin,
SR/IRQ/vector/bus/handler inputs and ending service context remain unresolved.
No additional native/engine command, callback or fixture mutation occurred.

## R22 composed full canonical reaches8775 status; strict route remains red — 2026-10-17

**One unmodified maintained fullcanonical, not a prefix or full suite, measures
this frontier.** Owned3aa branch
`bugfix/ai-s3k-hcz-composed-canonical-frontier-gpt61` is created from preserved
a46 and fast-forwarded under its intended-tree writer lease to committed Root
`7df991e289b93f92b34e10e79dc4f9900ec67eaa`. This composition includes other
qualified Root changes; it is broader than historical49197. Prior held-release
branch stays at a46; all earlier refs, native4fdf checkout and regular private
config/Main bytes+mtime remain. No runtime/test/fixture/observer changes are
made for R22. A source/registry/process check finds no duplicate queued/running
same fullcase; unrelated jobs are preserved. R22ordinal25/task is registered in
`gpt61_hcz_composed_fullcanonical_r22`, separately from24..20, with24h expiry.

### Original selection, source and actual terminal

The actual historical17194/49197 fullcase profile is `trace-replay`. The only
request uses the existing maintained method, original AD40 fromboot/bootstrap/
lag, and the normal Main absolute wrapper from this owned cwd. MAIN_WORKSPACE and
the three ROM variables retain the original absolute bindings documented in R18:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Ptrace-replay \
  "-Dsonic1.rom.path=$S1_ROM" \
  "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true \
  -Dsurefire.runOrder=alphabetical test -B
```

The compiler prerequisite stays inside this one request. There is no prefix
cutoff, checkpoint replacement, reflection, callback, diagnostic gameplay log,
input/timing change or reference hydration. Before/after SHA256 pins match:

| Input | SHA256 |
| --- | --- |
| Maintained fullcase | `eb743765a952e947a54991ecd8fe67c2114bc1b2e5addfc4d3b8b1e4b19a5127` |
| AbstractRunChainTest | `967c9896bf566218e9198626ec7d256f181be9fb78ca5abc787d6b82932148bb` |
| Column runtime | `d8119e4afe924b7e76adcc6e8a71bf38c46e1c85067f5f7e5e42cd956cd3b303` |
| Original run BK2 | `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0` |

BK2 remains at
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2`.
Sole session **76850** finishes **19:02:24UTC**, exit1, queue0s,
displayed Maven01:38min, JUnit **1/1F0E0S**, method26.092s/suite26.359s.
The combined failure has both seg11 comparison and seg13 walk-ownership axes;
four suppressed unmatched hardware completions are retained below. No skipped
ROM test, observer error, retry or additional selection is hidden by this total.

### Completed windows and actual next field

All available JSON fields/arrays and the JUnit XML/failure text are inspected.
Complete reports compare every row of their loaded reference; row counts are
also checked against the original compressed CSVs using TraceData's nonempty/
noncomment/header rules. Every listed report has0warnings and0bootstrap errors.

| Segment | Compared / reference rows | Complete | Total | Physics | Animation | Lag |
| --- | --- | --- | --- | --- | --- | --- |
|0 aiz|2290 /2290|true|0|0|0|0|
|2 aiz_2|4023 /4023|true|0|0|0|11|
|4 aiz_3|8235 /8235|true|0|0|0|14|
|6 aiz_4|4103 /4103|true|0|0|0|16|
|8 aiz_5|7175 /7175|true|0|0|0|62|
|9 hcz|3574 /3574|true|0|0|0|55|
|11 hcz_2|14208 /14208|true|11118|10054|1064|59|
|13 hcz_3|partial count not emitted /5393|false|63295|54642|8653|6|

ReturnedHCZ's actual **firstNonCameraPhysicsMismatch is row8775
`sidekick_status_byte`, native000B / engine000A, delta1**. This proves advance
past repaired8717 in the complete window; no writer/cause is observed here.
First logged all-group mismatch remains3475 `sidekick_animation_id`17/1C.
Seg11 JSON
`s3k-sonic-tails-complete-emeralds_seg11-segment-11-6d40780980dc3a7d.json`
has SHA256
`eb91529a17cd750726ad42e6d369fc218c9f84e49028e770caf5980da3a169b1`.
The assertion's phrase “physics comparator errors” labels TOTAL11118;
the actual group split is10054physics+1064animation, not11118physics.

The entire *available* seg11 recent array has five ERROR/repeatCount1 entries,
all at14207: mapping_frame00A0/0096, animation_id0020/0002,
status_byte0002/0007, rolling0/1, g_speed0000/-0174. This bounded ring is
**not an exhaustive error vector**. Historical49197's complete14208 total26153
(23312physics+2841animation), first8717Air1/0, lag59/warnbootstrap0 is a
same-window aggregate comparison only. Broader source composition and absent
full arrays prevent single-owner attribution or a regression-free/full-vector
claim. R21's8721-row206 remains its distinct partial result, never extrapolated.

### Exit reached; onward ownership and timing limits

The actual dynamic-art gap report has13 gaps/0failures, including
hcz_2→ss_6 gapStart/nextArm77283 and ss_6→hcz_3 gapStart83626/nextArm85096.
Original manifest transitions identify giant_ring at BK2frame77284 and
stage_exit85097. Thus the returnedHCZ giant-ring exit is **present** and the
walker actually enters SS6 and hcz_3. Traversal/gap0 does not certify SS parity.
The next giant-ring transition90491 is not reached/qualified.

Seg13 remains incomplete: first non-camera287 `y` native06D4/engine06DA,
last recent comparison3208. Its5393-row loaded reference is checked, but the
maintained summary does not emit the exact partial compared count; neither3209
nor a fitted movie-offset count is asserted. The failure is
`segment 13 lost production ownership before source closure`, modeLEVEL,
`LevelIdentity[loadGeneration=10, progressionZone=1, romZone=1, act=1]`,
BK2cursor88306 (hcz_3 expects act2). The five available recent entries at3208
are mapping_frame0003/00BF, animation_id0000/0025, status_byte0002/0042,
g_speed02A6/0000 and x_speed02A6/0000, all ERROR/repeatCount1. Its JSON SHA256
is `8d3f40103186273799f2d85b6d6bf20aa0dd271442e83330f2c52506d85f96f5`.
The same historical aggregate/first/recent row/ownership cursor does not prove
exhaustive error-set equality.

Four actual suppressed **unconsumed completion edges at segment end** remain:

| Raw frame / boundary | Kind / ordinal | Expected fingerprint | Actual production |
| --- | --- | --- | --- |
|3256 PRE_MAIN_LOOP|KOS_DECOMPRESSION_QUEUE#244|`c2db2fda975f758607b601f686bc782c7ebe55e2413f540f23b193ba2b6f1741`|different descriptor `fbfc78d499717cfec6df27fdd04fa4b5293a7147ec7ff7a7a18004e9db801e78`|
|3257 POST_OBJECTS|KOS_MODULE_QUEUE#171|`1523a70a6b4a9eea42f03f72e73e83ab222fb6e09187cbc49b028451f14e3033`|different descriptor `10eb568a70724c579f022914f56227c2c7fa421aafa8578aebaa874f0cffb0ca`|
|5356 PRE_MAIN_LOOP|KOS_DECOMPRESSION_QUEUE#245|`3c96d8b9573e86f26814cb8a605459c8fef23cc1ca5425db2fd1cc250d408d91`|no unclaimed submission; never submitted versus already admitted/claimed is not distinguished|
|5357 POST_OBJECTS|KOS_MODULE_QUEUE#172|`70da89e553f70fe647a00489dec5f2612854986b444b87a2e8d81ab0f821e431`|different descriptor `05324378670c6afa8c6d99f6e5313d625d2d926e6bc16f25cd9d8d1a5a195bf8`|

No timing retuning, missing-job fabrication, exact historical descriptor equality
or downstream causality is inferred. Stop at this terminal/new8775 field: no
repair/probe/rerun/guards/fullslice/alternate/native/media or broad suite.
No whole-route certificate, strict-green claim or refreshed matrix breadth.
Root owns further causal scope, combined smoke/guards, integration and push.
After durable facts, consume only inspected own XML/text/segment/gap JSON and
owner sidecars under metadata_slot alone; no raw archive or pass receipt.
R23's queued identity is retained separately without authorizing early owner work.

## Matched cold contact order R14 and R15 — 2026-10-17

Root directly inspected both ORIGINAL bounded journals: hashes, headers,3explicit
main keys48274/75/76,152events and actual bridge204/spring206 START/RESULT
slots/XY/status/clocks. This follows R13's matched original803 pass versus
f196 full-campaign death49613; its first end-of-step P1 difference48275 remains
separate from the earlier engine kill writer in R17 above. These prefixes are
not native truth, allocator-fault proof or repaired campaign acceptance.

Same owned worktree `t3-98d036a9`, native session
`01a1496e-feed-7130-9b66-9f402f964177`; exact803 and
`f196e95da4583206a1201b3b4d02b1f626f7026e` runtime, no DEZ/Root WIP.
Original maintained Sonic cold-campaign class/movie/step+render/45-step forward
and two replay cycles are unchanged except9temporary marked observer blocks.
The pure diagnostic return follows ALL original48276 assertions/route/window
handling. Full-route final assertions are intentionally UNREACHED.

| Exact source / sole request | Terminal UTC / queue / Maven | Actual result |
| --- | --- | --- |
|8030832d2,39089|18:54:32 /0s /1m31|1/0F0E0S; method29.275s; PREFIX ONLY|
|f196e95d,1886|19:00:47 /0s /1m38|1/0F0E0S; method30.259s; PREFIX ONLY|

Both used the same original
`TestS3kLrzHpzSszColdCampaign#sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`
selector and no profile; normal absolute-Main wrapper, all3original ROMs,
Java25.0.4.1/EGL/nullAL/no ambient display, single-fork/reuse/alphabetical,
inherited JDK_JAVA_OPTIONS/MAVEN_OPTS unchanged. Only the owned output filename
changed. Exact command, source hashes and source-only contract are in the
[existing Plan](../../plans/2026-10-13-s3k-playable-completion.md#r14-first48275-solid-contact-source-preparation--2026-10-17).
No compiler repair/retry or extra selection. Parent elapsed unmeasured.

Strictly validated header/schema/UTF8/length/newline/caps, original movie/input
hashes and literal L/L/A input bits,3exact main keys,52/54/46events,67matched
checkpoint pairs per run and12existing terrain results. All3step writes and
152event passivity checks verify in each run. ROOT original inspection confirms
this. Per-process Java identity values do not select comparisons.

At original48275, all selected actual checkpoints read VInt48206/LF9027/OM9028:

| Actual run order | Actual P1 at bridge checkpoint | Actual result |
| --- | --- | --- |
|803 spring206/slot6 → bridge204/slot32|X187D.F000,Y0A6C.6800,radius13|TOP;Y0A64.6800,status21→29|
|f196 bridge204/slot8 → spring206/slot9|X1882.F000,Y0A6C.6800,radius13|NONE;spring later pushes X187D.F000|

The same ROM placement204/offset04C8/id7C/sub00 at1860/0A88 is present in
both. Bridge uncollapsed, width20hex, preupdate valid, skipfalse, render-bound
eligibletrue; P1 cached render validity/onScreen bothtrue. Source-derived
params20/10/9 are not actual captured Java locals/native registers. Spring206
is id74/sub00 at18A0/0A70. Unchanged ascending SST scan/width gate explains the
immediate branch: bridge8 sees1882 outside right1880; bridge32 sees187D inside
after spring6's SIDE push. Source/controller constants are not a justified fix.
All12emitted terrain argument payloads agree. The callbacks bracket resolution;
they are not direct position-write hooks or native correctness evidence.

At FIRST recorded BEFORE48274, the only differing recorded passive field
(excluding invocation-local identities) is bridge204's slot32 versus8. First
checkpoint-order mismatch is48274/event9: spring206/slot6 versus diagonal198/
slot6. Positional comparison stops there; named placements are extracted in
actual independent event order, without fitted alignment. The earlier producer
of these allocations/lifetimes is outside the window. No actual birth is dated
from a first observation. Root owns next scope; no runtime edit/extra run.

Original baseline807618bytes SHA256
`b14d412887c8e603599443810d1c71b2820152a65c5c3310b3c0520ec2754313`;
current780114bytes SHA256
`296f5fa12fdfc670f0546940f656e8c968d4f84facf08bc7b09628b7080d3a67`.
After Root's original inspection and durable extraction, BOTH originals are
consumed under metadata lease ALONE (1587732bytes, no copies/archives/receipts).
The4normal inspected reports were already consumed. Original source9616921c is
restored, owned observer source removed, exactly2instrumented outer/helper
classes removed under intended writer. Last target was f196+observer; no rebuild
or maintained uninstrumented-build claim. All refs/native/private config and
Main hash/mtime preserved. Final accounting is DOCS ONLY; full cold campaign
remains blocked, no native/new probe/guards/broad execution.


### Root preserves the reviewed native diagnostic source — 2026-10-17

The recurring stock-clock identity/access helper is now maintained in
TraceChaser local commit4c3d6ba8c93ae0e607cb15e16e367d47f40f36a0, based
exactly on the existing parent gitlink322851cbfa2e1c1803332142e11ab74b4eb34eea.
It preserves the f4 prototype body byte-for-byte after its purpose/origin
header, at bizhawk-headless/src/Core/StockKosLiveClockReader.cs. Only that
reader, its single production project inclusion, README and byte-identical
native AGENTS/CLAUDE guidance changed. Guidance documents a dormant diagnostic
exception with explicitly authenticated instruction PCs and corrects the stale
instruction to compile production classes again into the referencing test
assembly. No recorder, CLI, host-interface, canonical fixture, gameplay,
readiness or kernel integration was added.

The maintained build.sh compiled both managed projects under the native
worktree build lease and shared OpenGGF build-activity lease, inherited
through the compiler. Terminal0 in2.33s; three existing xbuild warnings concern
unchanged .NET4.8/toolset declarations. Index/history/documentation scanners,
mirrors, XML/source inclusion, links/fences and diff checks passed. Reader body
equality was independently checked by Root. Maintained reader source SHA256
60d014fc3eefe76ef104625676c0122e0eafeb48d6d216f3af05f4e6c847186e;
managed executable c26f7cd661a679db3d59f32d08cc4231a17962497cc63991d95d322dac8935aa.
No tests, core opening, frame advance, callback, native-core rebuild or fresh
maintained-binary bind check occurred. Historical88245 qualifies only its
previously pinned external prototype; it does not transfer raw-scalar/timing
acceptance to this maintained executable.

Root reviewed the exact five-file native diff, then relocated only that clean,
owned native worktree into this composition tree's previously empty optional
submodule directory. Main's existing native e0a2443e checkout and foreign native
worktrees/refs remain unchanged; no unrelated native commits were adopted.
The local parent gitlink and agent tool catalogue now reference the preserved
source. Native and parent integration/push and final owned-resource cleanup
remain pending with the broader delivery. External one-off runner/compiled
artifacts are consumed only after their durable facts and source preservation
are verified; they are not archived as a second maintained tool.

## R24/R25 actual8775 facing writer; native guard still missing — 2026-10-17

**The actual engine LEFT→RIGHT caller is observed, with no facing repair.**
Source-only R24ordinal27 and execution R25ordinal28 are separately registered,
preserving R23/22/all earlier entries. HEAD is51320c6c0 (R22/R23 docs on Java7df),
not Root's later documentation composition. Root reads both actual temporary
files/getter contracts and the original seg11 awaitBoundary path before approving
one original AD40/fromboot prefix through8776 inclusive. No callback/controller/
provider, input, timing/hardware observer, solid, sensor, fixture or gameplay
state is replaced. No native execution or runtime-owner correction occurs.

### Reviewed two-file source and sole invocation

| Temporary source | SHA256 |
| --- | --- |
| AbstractSprite.java | `14e058db692aebb3b4a484f908b9112c6115894cf7c3cf7c349bc869835f05ba` |
| TestS3kCanonicalHczFacingWriterPrefix.java | `10315b23623e079f937e7b551d4c769f2b2651e82dc8a4371070a4263221068d` |

Combined SHA256 over ordered pathUTF8+NUL+bytes+NUL is
`764455f36083874d16f0ab6685c8cebd9da996b3736b1094c823805d40b08389`.
The logger adds60 marked lines before the existing single setDirection assignment
and private diagnostic helpers; the temporary subclass is224lines. Removing both
R24_DIAGNOSTIC_BEGIN..END blocks recovers exact7df AbstractSprite SHA256
`7194d82461a2bd1174d7104290e916de682fb14ba7ec1c56370c03f803e4c0b2`.
Maintained driver/base/BK2 remain the R22 pins. Inventory finds no alternate live
setter override: constructors initialize direction directly, and playable rewind
restore writes it directly. Those paths are not instrumented; absence of a
setter record would not prove absence of a direct write.

The logging context binds the actual registered P2 object, not a name or assumed
slot. Only LEFT→RIGHT calls for that exact identity during8774..76 emit a bounded
16-frame caller stack. Getter projections read raw fractions, canonical status,
engine object-control booleans, speeds, current PANIC scalars/logical mirrors,
leader centre and separately named counters. No fabricated native object_control
byte, NormalStepDiagnostics/PANIC alias, fresh visibility calculation or object
owner action is used. Output and pure reads are checked against the same known
primitive projection; this is not a full-world passivity certificate. A shared
200-record/1MiB cap includes the final partial summary. Diagnostic context/property
and previous row observer are restored; the original observer and super step each
forward exactly once. Pre-step is explicitly **not CPU entry**.

The source-approved R21 reporting seam catches only the identical private prefix
stop after the base's final comparisons/coordinator.afterStep, before original
abort/cleanup. It reads actual current comparator and loaded immutable TraceData,
writes via the existing diagnostic-only report owner, and rethrows the same stop.
Strict all-group zero remains after the original abort/fixture cleanup.

One request, using R18's original absolute variable bindings, from owned3aa:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kCanonicalHczFacingWriterPrefix#originalAd40FacingWriterThrough8776' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

The execution authorization explicitly retains the single EGL JTO token plus
same-valued CLI property. Original boot/input/bootstrap/lag/profile are unchanged;
the maintained fullcase selector itself is not edited. No duplicate live job
exists at submission. **8612** is the only request, terminal1 at
**19:32:38UTC**, queue0s/displayedMaven01:41min, JUnit **1/1F0E0S**,
method24.655s/suite24.923s. Compiler prerequisites succeed inside that request;
no source correction, extra build, observer failure, postboot retry or second
execution. Both reviewed hashes remain exact before/after terminal.

### Actual caller and separately sampled phases

The actual registered Tails identity is665641137; one rising assignment is logged.
All targeted P2 centres/fractions remain0E10.BB00/0572.AE00, vx/vy/g0.
Control below is the logged engine tuple
(owned,cpuAllowed,movementSuppressed,mapping), not a native raw byte.

| Stamped row / phase | Direction / status | Control tuple | moveLock | cpuCounter / levelLF / objectFrame / objectVblaCounter | Playback cursor / stated next row |
| --- | --- | --- | --- | --- | --- |
|8774 PRE_STEP_NOT_CPU_ENTRY|LEFT /0B|false×4|1|8715 /8715 /2581 /71803|71849 /71850|
|8774 POSTROW_AFTER_FINAL_COMPARISON|LEFT /09|true×4|0|8716 /8716 /2582 /71804|71849 /71850|
|8775 PRE_STEP_NOT_CPU_ENTRY|LEFT /09|true×4|0|8716 /8716 /2582 /71804|71850 /71851|
|8775 SETTER_PRE_ASSIGN|LEFT→RIGHT /09 before assignment|true×4|0|8717 /8717 /2582 /71804|71850 /71851|
|8775 POSTROW_AFTER_FINAL_COMPARISON|RIGHT /0A|false×4|0|8717 /8717 /2583 /71805|71850 /71851|
|8776 PRE_STEP_NOT_CPU_ENTRY|RIGHT /0A|false×4|0|8717 /8717 /2583 /71805|71851 /71852|
|8776 POSTROW_AFTER_FINAL_COMPARISON|RIGHT /08|true×4|0|8718 /8718 /2584 /71806|71851 /71852|

The movie values in this table are the diagnostic stamp captured by the original
row announcement before production, including on later setter/postrow lines;
no extra postrow cursor read or fitted row offset is inferred. Counter values
are read at each named phase and are not renamed native VInt or CPU entry.

The bounded stack's direct owner is
**SidekickCpuController.updatePanic:3424 → update:480 →
SpriteManager.processPlayableSlots:831 → update:708 →
GameLoop updateLevelMode callbacks1873/1876 → LevelFrameStep.execute:343**.
The remaining captured callers are GameLoop.updateLevelMode1870,
stepInternalBody1451, stepInternal callbacks1174/1164,
PlcFrameLifecycleCoordinator.runLogicalIteration155,
TraceSessionLauncher.runProductionIterationIfActive2981,
GameLoop.stepInternal1180, LevelIterationAdmissionController.runTraceObservedStep285,
and GameLoop.step823. The stack observes a call, not a substituted controller.

At the actual setter: PANIC/routine8, manual counter0, Ctrl2 signed lockfalse,
moveLock0, spindashfalse, pinballfalse, dead/hurtfalse, gSpeed0. The current
held/pressed **diagnostic logical mirrors** are2/2; they are not raw hardware
input or a newly sampled CPU-entry word. Leader centre is0E75/0411 at this call.
Cached visibility validtrue/onScreenfalse is read, not recalculated. The existing
PANIC facing choice RIGHT for its actual P2X<P1X inputs is source-consistent.
The engine's previous moveLock1→0 is observed across8774, but **its writer/caller/
movement dispatch is not recorded**. R23's native AUX still lacks the actual
native move_lock/spin/lock guards at CPU entry. No positive native move_lock
value, earlier timer producer, shared grounding fault, column fault, CPU formula
repair or native execution path is proved by this witness.

### Actual partial report and stopping limit

All8 targeted records (5367UTF8bytes), JUnit XML/text and every available JSON
array/group are inspected. Only the final original strict-zero assertion fails:
**compared8777/reference14208, completefalse,207=12physics+195animation**,
bootstrap0/warnings0/lag59, firstNonCamera8775 sidekick_status_byte000B/000A.
No skip or suppressed diagnostic failure. Seg11 JSON
`s3k-sonic-tails-complete-emeralds_seg11-segment-11-6603d91c777bb604.json`
has SHA256 `03052a152bac3b0478f4cefa09ded728bf5e9c9e01fe53d5413ab3eb8011cdba`.
Its entire available recent5 array is ERROR/repeatCount1 each:8775status000B/000A,
6948camera_y07C7/07C9,6947camera_y07C2/07C4,6792camera_y0678/067E,
6791camera_y0670/0678. This is not an exhaustive error vector. Do not manufacture
additional comparator errors from postrow samples or fit their phase to a count.
R21's206/8721 and R22's11118/14208 remain different measured windows.

Earlier six0/2/4/6/8/9 gameplay reports complete2290/4023/8235/4103/7175/3574
rows, all groups/warnings/bootstrap0, lag0/11/14/16/62/55. The11-gap report has
0failures; no returned-HCZ exit or onward ownership is reached by this prefix.
R22's actual exit and independent seg13ownership88306 stay historical/separate.
No general green, native-gate qualification or owner repair follows.

Stop after these facts. No extra query/run/selector/native/media/fix. Production
source is restored byte-exact, the temporary test source and both instrumented
class files are removed (no unrequested recompile; the next normal build must
recreate those classes), and18 inspected owned diagnostic files are consumed under
metadata_slot alone. Actual XML confirms Java25.0.4.1/surfaceless-EGL/all3absolute
ROM properties and0skips. All refs/native4fdf/private config remain. Existing docs
only are committed locally under normal hooks; Root owns any next causal scope.


## Matched cold pool producer R16 to R18 (2026-10-17)

Root directly inspected BOTH ORIGINAL raw-pool journals: exact BASE803 handle70778
(19:34:52Z) and CURRENTf196 handle81089 (19:42:31Z), each **1/0F0E0S PREFIX ONLY**
through original48276 after assertions and unchanged45-forward/two-replay windows.
Full-route finals intentionally unreached; historical current cold-death49613
remains a completion blocker. Same98d/native01a1496e, original Sonic case/movie/
session and class2aa3/helperc35c/portablea3d00169, other runtime/test exact each pin.
[Plan: exact commands, source hashes and task IDs](../../plans/2026-10-13-s3k-playable-completion.md).
Normal Main-absolute queue/no profile/all3original absoluteROMs/Java25/EGL/nullAL/
noambient/Lua/fork1/reuse/alphabetical; ONLY output path differs. Queue0s/Maven1m42
both; BASE case34.454s/class34.732s, CURRENT34.149s/34.423s. Parent elapsed unmeasured.
Lifecycle BASE4133main/4163test, CURRENT4143/4192; Java25.0.4.1/release25. No repair,
retry, observer/cap/key/passivity/original assertion failure.

Each arm:18,603positive activation/verified passivity observations,48,547original
step-render pairs,6restore markers; explicit original main/forward/replay/restore
keys and independent BK2 buttons/session cursor/real VInt-LF-OM/loadgen4 validated.
BASE1315records/1313emitted, CURRENT1312/1310. Previous/current full110ordered pools
and deltas validated; Java reference numbers excluded across arms. Sparse absence
carries declared structural state ONLY, not clock metadata. No native/all110-byte/
within-pass birth certificate. Initial declared state agrees.

| First different AFTER input/sample | Owner | Equal again AFTER input/sample |
| --- | --- | --- |
|43945/9668|Animal264 slot8,03A0/0550 id28|43956/9690|
|44245/10268|Animal322 slot5,09F8/05B0 id28|44254/10286|
|47250/16551|Animal556 slot30,0EA8/0F08 id28|47262/16575|
|48195/18441|Animal629 slot6,18B0/0CA0 id00|not equal again through48276|

First three spans close before the surviving fourth. At48195 CURRENT actually
removes629: before→after VInt48125→48126/LF8946→8947/rawOM8947→8948. BASE has no
emitted clock metadata there; none is invented. Same-source animals629/630/631 enter
BOTH arms slots6/7/8 after48149/48165/48181; CURRENT removes48195/48198/48199 versus
BASE48202/48202/48204. Before48201 BASE first allocator-free slots9,30,32… versus
CURRENT6,7,8,9,30,32…. Same48201 source order/IDs632/633/634 (diagonal198/diagonal201/
flat204) receiveBASE9/30/32 vsCURRENT6/7/8; same48213spring206/id635 BASE6/CURRENT9.
Actual free-empty→USED/registered/exec pools establish surviving lifetime/hole/
admission lineage, not allocator fault/native correctness. First direct common
recorded camera difference remains48275 AFTER, BASE0A04/CURRENT0A0C.

Source candidate: registered releaser layout207/idA0/sub04,18B0/0CA0,slot5/id624;
unchanged EggRoboBadnikInstance.releaseAnimal uses shared generic Animal initializer,
a gap already documented in its header. Pin044fa46725c71187399e13f5ddb70e11d32dc024
loc_915F6→ChildObjDat_919E6→loc_917C0/917E2/91800/9181C
(sonic3k.asm198774..198819) installs table/facing X, bounded random high-word negative
Y, frame1/routine4or6 before initial Draw_Sprite; generic MAIN/frame2/X0/Y-400 differs.
Actual cold parent callback/unique parent/mode/velocity/prior render-bit/retirement
branch were NOT observed. Shared correct R5 render semantics stay; no revert/fit.
Independent special-launch old-red source is a separately gated unit, not a repair
or native truth claim from this engine comparison.

Original BASE4924861B SHA256bd1e48195a97a303a2565b1396f5c43020768fde79ab068f4b02d95d14b8447f
and CURRENT4917574B SHA25626b224709a4c4883efeeef952aad462b85213d574cdef88321d983a765bb4a7a
were directly inspected read-only0444; their exact9842435B are consumed metadata_slot
ALONE after durable facts, no copy/archive/receipt. Reports already consumed separately
(BASE171736B,CURRENT171741B). Maintained class restored9616921c; instrumented outer/
helper/Picture classes removed. Original helper sourcec35c remains owned/untracked at
its original path pending Root preservation choice: proposed opt-in test-side protocol
plus existing workflow README entry, no runtime API/refactor/copied driver; no
promotion/deletion in this docs commit. Target has no maintained rebuilt outer
ColdCampaign class. No further campaign/native/media/guards/test/runtime edit.

## R26–R28 original native PANIC returns on retained move_lock — 2026-10-17

**Native CPU entry is observed; its PANIC guard consumes move_lock=$001D. No
engine repair or new trace qualification occurs.** Source-plan ordinal29,
launcher-correction30, compiler31 and execution32 have separate exact task IDs
in the existing ownership registry, preserving all earlier results. Tested own
HEAD is5137810973f4aa009a151a0ce55b15586d509009, Java7df, native checkout
4fdf73880f93a3172d23bf9e157b15de3c78fc32 internally clean. No Root/Main edits,
new worker/tree, engine probe, runtime/test/fixture changes or native/core build.

### Reviewed source, compiler and sole native command

External task directory is `hcz-native-panic-gates-r26-v1` under the existing
parity task D. It contains a task-local C# passive reader, plan and Python
launcher, using the existing stock GpgxHost/official BizHawk2.11 runtime.
Root independently reads actual sources and verifies all20 ROM byte pins,
15callback sites, original movie/fixture/host/tool identities before launch.

| Artifact | Final SHA256 |
| --- | --- |
| NativePanicGates.cs | `8d6dd5e4db69acb5f4c3412e7d38203529d4dab0e70e5dab436d496bd8b3b62d` |
| plan.json | `00222528e5493b300392ca59340f5180fae12f1fa177bf0fffc690be9907438d` |
| run_native_panic_gates.py | `9c01499b6146aeb0f5b7430fb631ed790c7ed7428eb29e2422bce8755ff2ec26` |
| NativePanicGates.exe | `fee52fa3f0245d9af04a004242590304a9754eb3edf01d420ca28b6b9884cf87` |
| compile.json | `18107e02dcf41ef4ca5dc0942ed4d372feb59b361145466dd861447964ef9d5d` |

The original launcher163dd081 lacks Main's existing `handle_termination()` at
CLI dispatch: SIGTERM of Python alone could bypass cleanup while Mono retained
inherited leases. Root's material review finding is closed by importing that
handler and wrapping only compile/run dispatch. Reversing this minimal block
recovers163dd081; C#/plan remain exact. Ordered filenameUTF8+NUL+bytes+NUL over
C#,plan,launcher has SHA256
`d42593fb984de8cfea3d2673d410c26e23f296b7d15431b2a7e4306e8370f41a`.
No signal-execution experiment, observer changes or additional review round.

Only compiler request (exec20b9c9) exits0, empty compiler log, EXE23040bytes.
Metadata UTC20:00:17.829950→20:00:18.452447 gives0.622497s; this metadata span
and tool-reported0.589932387s are separate measurements. Intended-tree writer
and shared build-activity leases/all inherited FDs cover existing MonoRoslyn;
both scopes end before native admission. No GpgxHost is opened by compilation.

After separate Root acceptance of actual compiler output, the only native
command from owned3aa is:

```sh
python3 "$TASK_D/hcz-native-panic-gates-r26-v1/run_native_panic_gates.py" run \
  --expected-launcher-sha256 9c01499b6146aeb0f5b7430fb631ed790c7ed7428eb29e2422bce8755ff2ec26
```

The launcher uses current Main's default normal `maven_slot`, estimate90,
kind `native-r26-hcz-panic-gates`; no Mavenexec goal, reservation, override or
outer activity shim. Session66263, actual MonoPID3257017, terminal0; reported
queue0s. Admission20:02:06.687443UTC, native Popen20:02:06.724924, terminal
metadata20:03:03.354513. C# reports56.2263242s; outer started→finished span is
56.629589s. Initial, inside-admission immediately-before-Mono and post-terminal
identity guards pass. Exact six accepted artifact hashes are independently
rechecked before submission and after terminal, including EXE/compile metadata;
execution records inherited FDs5/6/7. No retries/variants/other selection.

### Original movie mapping and complete controls

Original AD40 BK2 is the maintained
`src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/`
`s3k-sonic-tails-complete-emeralds.bk2`, SHA256
`ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0`.
Original40-character ROM SHA1 isCFBF98C36C776677290A872547AC47C53D2761D6.
Power-on uses original BK2 SyncSettings and inputs0..71851 once. Only inputs
71848..71851 are observed: original manifest offset63075+raw8773..8776.
Expected after-core=input+1 is separately checked, without row shifts/state
loads/RAM writes/trace hydration/reseeding/media/canonical publication. Original
BizState1.0 member is format text3LF; opaque legacy32-character header SHA1 is
not substituted for ROM identity. P2 physical input is inactive in this prefix.
Bounded existing-D inventory had no exact target fullRAM; oldAD40 inputs70207..10
and original82EA artifacts cannot answer this endpoint.

All41 journal records are inspected, including every one of32 actual callbacks,
four after-frame records, four classifications and completion. Same-projection
RAM/register passivity checks succeed32callbacks+4endpoints. All four complete
original CSV/AUX endpoint controls match, neutral input/no lag. AUX is selected
by actual frame key, never multievent line ordinal. Journal62134bytes plus
normal native boot log2165bytes=64299bytes, below200records/1MiB caps. No
failure.json, count/order/control/passivity/observer failure or output overflow.
No JUnit result is implied by this native execution.

| Raw row | Original input / callback CompletedFrame | After-core CompletedFrame | LF | Actual full VInt / recorded LOW | End status/control | Column held at entry→end |
| --- | --- | --- | --- | --- | --- | --- |
|8773|71848 /71848|71849|8715|00011644 /1644|0B /00|1→0|
|8774|71849 /71849|71850|8716|00011645 /1645|09 /03|0→1|
|8775|71850 /71850|71851|8717|00011646 /1646|0B /00|1→0|
|8776|71851 /71851|71852|8718|00011647 /1647|09 /03|0→1|

Full VInt is actual RAM longFE0C, LF isFE04, LOW isFE0E. Native callback
CompletedFrame is actual host input-phase state, distinct from after-Advance.
All P2 poses are0E10.BB00/0572.AE00, vx/vy/g0, render04, interactB32E.
Column actual code326B6/subtype2/status10 qualifies private P2 held+36;
its status retains native P2standing. P1 callback poses are0E75.A400,
Y0413/0412/0411/0410.8F00,VY0038; endpointY0412/0411/0410/040F.8F00,VY0,
g1/status02. Callback copied-camera is0DD5/Y03D3/03D2/03D1/03D0; endpoint
0DD5/Y03D2/03D1/03D0/03CF. These distinct phases are not aligned by final pose.

### Actual dispatch and missing earlier timer owner

All four frames execute exactly once in this source order:
Obj_Tails1365C → Tails_Control137C4 → CPUentry139CC → dispatch139E0 →
PANIC13F40 → post-sub_13EFC13F44 → commonRTS13FBE → CPUjoin1384A.
The real Tails_Control entry is137C4;137C2 is precedingRTS, and the verified
136BC table word0108 selects137C4. No canonical capture pin is changed.
Actual A0 isFFFFB04A throughout. A1 at CPUentry is0001365C, **not leader**;
after native LEA139DC, actual dispatch/PANIC A1 isFFFFB000. Execute callbacks
are before the pinned instruction and do not invoke gameplay owners.

At every callback and end row, raw P2 move_lockB07C is**001D (29)**. All
recorded raw74 projections independently decode to the same source offsets.
Post-despawn idle0, spin_dash_flag0, ground_vel0, signedCtrl2lock0, competition0,
physical/logical heldpressed0, CPUroutine8. Thus native runs CPU/PANIC, tests
idle0, then takes nonzero move_lock return to13FBE. No CPUskip; all four
classifications are `PANIC_return_move_lock`. Facing clear13F5E/afterclear13F64/
set13F6E/done13F74, spin13F94 and alternate return13F92/13FB0 counts are0.
The optional callbacks are absent because of the actual guard, not a hook failure.
CPU flight increments56→57,57→58,58→59,59→60 across sub_13EFC.

Only after complete native inspection, compare historical R25 engine evidence:
its8775 setter correctly reaches PANIC facing with moveLock0, whereas native
returns on001D before facing. This supports an upstream timer-state/lifetime
question, **not a facing formula, CPU/cache/camera or fitted29 repair**. Native
001D is already present at first observed object entry and unchanged over this
window. Its earlier producer and the engine's observed8774 moveLock1→0 writer/
movement dispatch remain unobserved. Source Player_SlopeRepel11E86 decrements
only when the ground/roll path reaches it; object_control and Air/OnObj have
separate dispatch ownership. Current movement suppression/air dispatch and
PANIC guard already contain those branches. Their existence does not prove
which actual engine branch depleted the timer or whether the initial producer
already differed. No generic movement defect is established.

Smallest next independent proposal, **not authored/executed**: use production
movement and actual column capture/release/retained-standing recapture with a
legitimately production-armed movement lock. Check held skip and released
Air+OnObj dispatch preserve the lock, against a reached-ground countdown
control and zero-lock PANIC-facing control; no native29/route/frame injection.
A green check would disconfirm that coupled lifetime hypothesis; a red must
identify its real owner before any shared edit. Earlier native producer remains
a separate missing witness if this independent sequence is correct.

Original observation journal SHA256
`5cadb395d4639d36f2313b43683ac3baf9935b3d12fd91fc34395c14edb82f42`,
execution metadata SHA256
`d274eda12cc16b3fb6ffe5c1d869a6b5eb5f2edd4a35ef1bc558dfe5c03941bf`.
Originals are explicitly retained for Root read, cleanup_pending; no duplicate
summary/archive/pass receipt. No cleanup before Root inspection. R25 strict
207/8777, R22 full11118/14208/actual exit, and independent seg13ownership88306
remain their prior epochs; this native pass moves no engine frontier. Stop here:
no engine witness/old-red/runtime edit or extra native/route/guard/broad run.

## R29/R30 actual move-lock writer reaches ground dispatch — 2026-10-17

**The actual8774 timer1→0 caller is doSlopeRepel reached from modeNormal.**
This is a writer witness, not a global countdown correction. Native R28's001D
producer and the engine's Air-clearing caller remain unobserved. R29source
ordinal33/R30execution34 have separate exact registry IDs; earlier32..29 stay
intact. Root directly inspects all41 R28native records before its exact nine
owned external files are consumed: six source/build files under intended writer,
then three inspected diagnostics under metadata_slot alone. No archive. Compact
native hashes/pins/guard/phases remain in the preceding section and registry.

On HEAD24cd76909bfc6a3802cc00c7dbc61550c9284aa6/Java7df/native4fdf, R29 inventories
26 production setter callers plus five owner assignments. Four assignments
bypass the setter: field initialization0, resetState0, rewind restore and
replenishAir0x23. No alternate setter override is found; absence of a setter
record would not prove no write. No timer seed, native29 injection or old-red.

Root reads the entire205-line temporary subclass and both marked playable
blocks, independently verifies exact stripping/base pins, and approves one
original AD40/fromboot prefix through row8776/cursor8777. Frozen temporary
AbstractPlayableSprite SHA256
`5ecf9aa2e16abf362e8b9250d0602c0de58ea2ca300253cf020e81db2cd8f025`;
TestS3kCanonicalHczMoveLockWriterPrefix SHA256
`3e53a56412d8b286f5fd414df4cddfe2f06ca432793bb9265f45a8462b52ac31`.
Ordered repositorypathUTF8+NUL+bytes+NUL combined SHA256
`e5ed9a35edb55e05a97b75f6f4bfce70ce42c4befef96287ff47759912de4a05`.
Removing only both R29_DIAGNOSTIC_BEGIN..END blocks restores original owner
`838c92521d635811df0b1d2c4ec325f87b82e61097116109d29dea8321eeb7a8`.
No public/API/shared owner change; maintained base967c9896/driver eb743765/BK2AD40
remain exact. The setter's original Math.max assignment runs once and unchanged.

One current Main absolute normal-queue command, from owned3aa with the R25
original ROM/environment bindings:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN_WORKSPACE/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ptrace-replay \
  "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM" \
  '-Dtest=TestS3kCanonicalHczMoveLockWriterPrefix#originalAd40MoveLockWriterThrough8776' \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

Sole session41203 finishes **20:21:04UTC**, queue0s/displayed Maven01:41min;
JUnit **1/1F0E0S**, method24.602s/suite24.881s. Compiler succeeds inside that
request, no prerequisite edit/extra build/observer failure/retry. XML confirms
Java25.0.4.1, surfaceless-EGL, original three absolute ROMs and fork1/reuse/alpha.
Only the strict0-versus207 assertion after original abort/cleanup fails.

### Actual primitive states and caller

All10 bounded records/4998UTF8bytes are inspected: eight row samples, one setter
and one current-comparator report. Actual registered P2 identity is1751753651.
Known-projection rereads succeed and the shared200record/strict<1MiB cap holds.
**The projection has23 named fields**; earlier handback's24 was a counting error,
not an omitted field/new probe. Field order is timer,X,Xsub,Y,Ysub,vx,vy,g,Air,
OnObj,groundMode,direction,controlLocked,objectOwned,cpuAllowed,movementSuppressed,
mappingOwned,cachedValid,cachedOnScreen,levelLF,objectFrame,objectVblaCounter,
spriteManagerFrame. No NORMAL-only diagnostics or unbound EngineTiming read.
Control below is the four engine ownership booleans, not a native raw byte.

All samples retain P2 0E10.BB00/0572.AE00,vx/vy/g0, OnObjtrue,
GroundModeGROUND, controlLockedfalse, cachedvalidtrue/onScreenfalse. GROUND is
surface orientation and is not proof of Airfalse. PRE_STEP is not CPU entry;
POST_PRODUCTION precedes final comparisons. Setter observation is before the
unchanged assignment. Movie cursor/next-row stamps8773..76 are71848/49,
71849/50,71850/51,71851/52 from the original row hook, not fitted clocks.

| Row / phase | Timer | Air | Direction | objectOwned/cpuAllowed/movementSuppressed/mappingOwned | levelLF / objectFrame / objectVblaCounter / spriteManagerFrame |
| --- | --- | --- | --- | --- | --- |
|8773 PRE_STEP|1|false|LEFT|true×4|8714 /2580 /71802 /8714|
|8773 POST_PRODUCTION|1|true|LEFT|false×4|8715 /2581 /71803 /8715|
|8774 PRE_STEP|1|true|LEFT|false×4|8715 /2581 /71803 /8715|
|8774 SETTER_PRE_ASSIGN|old1 /incomingRaw0|false|LEFT|false×4|8716 /2581 /71803 /8716|
|8774 POST_PRODUCTION|0|false|LEFT|true×4|8716 /2582 /71804 /8716|
|8775 PRE_STEP|0|false|LEFT|true×4|8716 /2582 /71804 /8716|
|8775 POST_PRODUCTION|0|true|RIGHT|false×4|8717 /2583 /71805 /8717|
|8776 PRE_STEP|0|true|RIGHT|false×4|8717 /2583 /71805 /8717|
|8776 POST_PRODUCTION|0|false|RIGHT|true×4|8718 /2584 /71806 /8718|

The real stack is AbstractPlayableSprite.setMoveLockTimer3361 ←
PlayableSpriteMovement.doSlopeRepel3871 ← modeNormal901 ←
handleMovementDispatch765 ← handleMovement485 ←
SpriteManager.tickPlayablePhysics2061 ← processPlayableSlots925 ← update708 ←
GameLoop.updateLevelMode callbacks1873/1876 ← LevelFrameStep.execute343 ←
GameLoop.updateLevelMode1870 ← stepInternalBody1451 ← callback1174 ←
PlcFrameLifecycleCoordinator.runLogicalIteration155. The extra leading frame is
the private logger3376; all16 captured frames are inspected. The actual native
VInt is not inferred from these separately named manager counters.

This directly observes a normal-ground dispatcher reaching its existing
SlopeRepel decrement. Air is true at8774pre-step and false before that call;
**the intervening Air writer is not observed**. Existing source has separate
object-control suppression, grounding support and Air/roll/normal decisions;
source presence alone does not identify which grounding owner executed. The
decrement is legitimate for the ground path it reached. No blanket timer freeze,
column/facing/CPU formula correction or earlier native001D producer attribution
follows. Direct-write bypass limits remain; no extra writer probe is launched.

### Exact partial report and remaining boundary

Current comparator identity2076619031 gives **8777compared/14208reference,
completefalse,207=12physics+195animation, bootstrap0/warnings0/lag59**.
FirstNonCamera remains8775sidekick_status_byte000B/000A. Seg11 file
`s3k-sonic-tails-complete-emeralds_seg11-segment-11-3db3054cecc2203e.json`
has SHA256 `03052a152bac3b0478f4cefa09ded728bf5e9c9e01fe53d5413ab3eb8011cdba`,
byte-identical to the R25 serialized summary, including its entire available
recent5 array. That ring contains8775status,6948/6947cameraY,6792/6791cameraY;
it is **not** an exhaustive error vector. No parity forecast or full error-set
identity follows. Six earlier reports0/2/4/6/8/9 complete with all groups/
warnings/bootstrap0, lag0/11/14/16/62/55; actual gap11/failures0. All eight JSON,
eight owner sidecars and original XML/TXT, including every available array,
are inspected. XML SHA256
`a2817dacfae28e30b8fa2e7c0ce849dd012e9fa6b453cd3afdeaff16b2bd4493`.
Original18 diagnostic files remain explicitly retained until Root read, no copy/
archive. Temporary two-source changes are restored exactly; only the two known
instrumented class files are removed, with no extra compilation. A later normal
build must recreate them. Prior refs/native4fdf/private config remain. Docs-only
local accounting does not repair runtime or change R22full11118/actual exit,
R25partial207/R28native29guard/independent seg13ownership88306 epochs. Stop after
handback: no old-red, runtime fix, new native/probe/variant/full/slice/guard/broad.

## R31 to R33 next-physics component stops at CPU prerequisite — 2026-10-17

R31 source-only ordinal35 inventories the sole APS Air storage/setter plus its
reset/rewind/native-control/hurt-landing variants. The feasible pre-movement
branch is PlayableSpriteMovement:727: DUAL_PATH + Air + nonnegative y velocity +
current-session grounding support clears Air before movement-mode dispatch.
ObjectSolidContactController:2604 can accept the retained column riding record;
the column has not opted into the existing stale-air provider capabilities.
This is a source-backed candidate, **not an observed8774 Air caller**. Native
loc_13872 selects status&6; Tails_Stand_Freespace has no SlopeRepel. Later column
recapture may legitimately clear Air again. Native001D's earlier producer remains
unknown; the real R30 reached-ground decrement itself is not proved wrong.
After Root reads R30, exact18 inspected diagnostics are rehashed and consumed
metadata-slot-alone at20:31:42UTC; compact845bd9 facts/hashes remain, no archive.

R32 ordinal36 authors only existing TestHczSpinningColumnCachedRelease, on
HEAD845bd9/Java7df. Full test SHA256
`0d28551d9b32bd873b083ed3be336b6cde2875def92bd5568812c9336b555334`;
exact diff SHA256
`75469c72e4f19b9e5e5621622167e6e0d93104ec3d6d69e357d74a7766e9764f`.
Original two method bodies remain byte-identical. Three additional cases use a
real ROM-registry subtype2 bubble, normal SST/animation, declared pickup overlap,
loaded water owner and actual replenishAir35 before real column capture. No
move-lock/private bubble/rider/cache writes or CPU replacement. The intended
oracles are positive-lock Air retention versus genuine grounded decrement and
natural zero; forced whole-world recreation/forward precedes final expectations.

Purity qualification from source review: independent copied floor sensors do
not publish player angles or CollisionSystem phase. However,
DefaultBackgroundPlaneCollisionProvider.state **memoizes shared derived
cachedState/cachedActive**. This is not a zero-shared-cache-write claim; no
production source change follows. All runtime bytes remain Java7df, including
column d8119e4a/APS838c9252/movement3f324b74/solid06245eea.

Root accepts exact source and authorizes ONE R33 ordinal37 normal lifecycle.
Below, MAIN and the three ROM variables denote the original absolute Main/ROM
paths preserved verbatim in the own execution registry; cwd is the owned tree.
These path-only substitutions satisfy repository documentation resource policy.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless LUA_BIN=/usr/bin/lua5.4 \
  python3 "$MAIN/tools/testing/maven_queue.py" \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$S1_ROM" \
  "-Dsonic2.rom.path=$S2_ROM" \
  "-Ds3k.rom.path=$S3K_ROM" \
  -Dtest=TestHczSpinningColumnCachedRelease -Dsurefire.forkCount=1 \
  -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test -B
```

No profile or extra build. Duplicate-own-job check is empty; sole session7866
finishes terminal1 at20:54:23UTC, reportedqueue0/displayedMaven01:15min,
JUnit **5/2F0E0S**, suite0.882s, Java25.0.4.1. Compiler succeeds in this same
lifecycle without repair. All5 cases/full both failure stacks and complete
available system output are inspected. Six component FAST-load warnings select
NONE when no manifest exists; no skipped cases or observer error.

| Actual case | Result |
| --- | --- |
| Existing cached release/standing/recreation | PASS |
| Existing visible hold/jump control | PASS |
| New genuine-grounded positive lock | PASS, including twice-forced recreation/forward and final K-1 oracle |
| New released positive lock | prerequisite FAIL after first normal frame: actual CPU CATCH_UP_FLIGHT |
| New released natural zero lock | same prerequisite FAIL after first normal frame |

Both release cases pass initial NORMAL/PANIC state, actual release prerequisites,
terrain clearance, one ordinary fixture.stepFrame, levelLF+1 and same registered
P2 identity. They fail at helper253 -> assertNormalPlayerPrerequisites266, before
held recapture checking/NormalFrame return. The positive case reaches/passes the
real35 producer assertion. **Neither release case reaches the forced forward
cycles or final timer oracle.** Their resulting timer/Air/ride values were not
serialized and are not reconstructed. This is an unexpected fixture CPU-state
prerequisite stop, neither genuine timer old-red nor candidate disconfirmation.
No provider/runtime design, source tuning, retry, variant, native or AD40 query
is authorized or performed. Actual8774 Air caller/native001D earlier producer,
R22full/exit/seg13ownership and trace residuals remain separate/unqualified.

Original XML27623B SHA256
`43f908749fc0cd1d9988c38f0da5f207dc247813fdf61bb8fe5dfd65bafa06f0`
and TXT3471B SHA256
`480f188085b27b868eebc87bb9985319d630f61d2a4e21cb9556a954160be414`
remain at this worktree's target/surefire-reports under the complete class name
for explicit Root direct read; cleanup_pending, no copy/archive. Test0d285 stays
uncommitted/unmodified; no failing-test/runtime commit. Ordinals35/36/37 exact
IDs/results and24h expiry are separate in both own registries. No live handle;
native4fdf/private regular config/all prior refs/Root/Main remain untouched.


## R29 independent EggRobo launch closure, cold route still open (2026-10-17)

Preserve the qualified local two-owner unit separately from the unmatched cold
producer. Own98d/native01a1496e, parentb264/runtimef196; frozen Animal944a2933,
EggRobob9e3474e, RNG test81f7db54, pending test570281df, unchanged real-parent oracle
0d2d216c and promoted helper8492f006. Native044 pin loc_915F6/loc_917C0..9181C and
word_2C7EA prove fixed Rabbit/Chicken, full RNG lowbit/highword, parent facing,
frame1/direct WALK-FLY, stationary first draw and persistent flags5 flip. Native
labels/independent ROM words are recorded in the [SSZ1 matrix](../../validation/levels/s3k-ssz-act1.md#r29-native-eggrobo-animal-launch-unit--2026-10-17).

The source path is not a fitted slot/clock/campaign repair. Existing constructors,
generic POP/R5/S1S2/points/physics/allocator behavior stay; pending scalar/enum
provenance and actual renderer arguments are covered across forced recreation.

Evidence accounting:70304 was compiler-only missing pure getter;24069 stopped
4cases at allocator expected5/actual7 before native/world controls.50817 then
passively established ABC4/4/7: capture A→B unchanged; original scan B→C adds
placements11/12/13 and one animal, removes placement0. This did not prove actor
liveness or exact within-pass callback. Moving ONLY four existing public camera
assignments before the unchanged warm-loop produced0d2d; original+1 assertion,
seed/gate/timing/native oracle unchanged.96038 then reached all twice-world
controls and failed EXACT4 native launch fields in all4cases: X±512/0,
Y-256 or-421/-1024, WALK-FLY/MAIN, frame1/2. This is genuine independent old-red.
No earlier prerequisite failure is relabeled as such.

R27 sole9selector lifecycle36866 is78/0F0E0S/10classes; all78 XML identities and
skips inspected, compiler4143main/4194test release25. R28 exact6guard lifecycle
51551 is6/0F0E0S. Only after6green and all six Java plus originalCold9616921c
hashes stayed exact, sole original uninstrumented full campaign96020 ran and
failed1/1F0E0S at SAME colddeath49613, method454→helper506, expectedfalse/actualtrue.
Queue0; Maven1m09/26.652/52.053s. All3 absolute ROMs/Java25.0.4.1/EGL/fork/reuse/
alpha checked in XML; no diagnostic property or early prefix stop. Full original
route tail remains unreached. No observed post-fix first split or Animal629
parent→launch→retirement lineage; same endpoint does not prove same predecessor.
R5 lifetime stays correct; no rollback/phase/allocator/init retuning follows.

All inspected XML/TXT normally consumed after durable Plan facts, metadata-slot
ALONE/no copies/archive/receipt. No R22Read compiled artifact exists in the inspected
owned EggRobo test class set; no target sweep/delete/rebuild is needed. Qualified
current target contains frozen candidate plus maintained tests. R29 local commit
preserves independently justified ROM behavior only; scaling/native-route/coverage
limits and original completion blocker stay open. No further execution or probe
authoring follows from this accounting; next scope is source-read provenance plan.


### DEZ final fresh-continuation resident definitions — 2026-10-17 source handback

Owned fa8 source0c7d9f20dea96eb91765ca1a1e6709cac35c4fe3 retains unknown
TraceChaser4fdf/private exact Main config/kernel16b/warning53d. Relative Root02b,
production differences are HCZ skim and the unused Kos kernel; DEZ/layout/resource
sources are identical, not an identical whole-source fingerprint. Root's actual
Main803 baseline8712 selected the same two ordinary solo/team DEZ incoming-final
methods and passed2/0F0E0S at16:58:34UTC,queue0/Maven57.117s (XML inspected and
consumed by Root). The composed broad smoke reports seven DEZ errors, not seven
new independent diagnostic runs.

One temporary passive witness59905 selects only
`TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding+coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding`.
It keeps the original boot/movie/input/rewind/draw recipe, adds read-only existing
exception operands/resource-load bounds and catches/rethrows each first step
exception. No new API, gameplay write, lookup fallback, observer replacement,
native/media, new tree/worker or second witness. Current Main absolute wrapper
from owned cwd, all3 original absolute ROMs, normal lane, single fork/reuse/
alphabetical, Java25/JTO single surfaceless-EGL token/nullAL/noDISPLAY/WAYLAND,
unchanged other JVM environment. Build-only61387 test-compile passed0 at17:05:53UTC,
Maven1m15s. Witness59905 at17:06:37UTC: **2/0F2E0S**,queue0/Maven37.433s/
JUnit14.030s; XML36205 bytes, no extra journal/archive. These are real unsupported
memory errors during playable movement, not an independent assertion old-red.

| Original case / movie row | Actual first probe owner | Raw word X/Y | FG row index / pointer / unsigned column | Layout byte / selected resident word |
| --- | --- | --- | --- | --- |
|solo60320|CalcRoomInFront→ground wall|2E82/00D5|0004/809C/005D|FF80F9=90 → FF4850 (definition144,offset50)|
|team48730|airborne floor scan|299C/0109|0008/80B0/0053|FF8103=B8 → FF5C02 (definition184,offset02)|

Both use wrapFFFF/rowMask007C, live zone23/0,blockCount29/chunkCount414,
pendingFreshBoundary=false; LF9316/8404 respectively. Catch-time P1 is2E62/CD
with velocity600/0, and29C5/CE with3FC/0, both grounded. The team's actual
playable identity inside the airborne probe is **not sampled**; do not substitute
that catch-time P1 state or infer P2. Selected resident word contents are not
inferred from exceptions.

For each actual route, the resource producer logs DEZ ROM1D5068 write8000 bytes
into an empty initial definition extent; DEZ2 writes the same8000 with retained8000.
The final scene ROM1DC24E writes0E80, but gets retained0000, leaving29 definitions.
Native Find_Tile_FG19175–19200 performs unsigned word arithmetic and preserves
D1FFFF through ChunkAddrArray. Width wrapping/clamping/zero/known-count fallback
would hide the missing owner. Native loc_7F310 (169866–169874) saves Act3 counters/
shield then StartNewLevel1700. StartNewLevel180728–180735 changes IDs/restart/
checkpoint flags; it does not clear the definition RAM. LoadLevelLoadBlock2
loc_1C30E writes atFF0000 and leaves the unwritten tail. Clear_DisplayData1391–1420
clears VDP/sprite/hscroll ranges, not FF0E80–7FFF; Act3 skips the title loop.

The exact engine seam is DezEndBossInstance97→LevelContinuationCarry.request,
whose matching request becomes LOADING at LevelManager3854 before synchronous
load; loadCurrentLevel3604 discards prepared builds. Sonic3k.captureLevelBuildInputs
currently seeds128 definitions only for an executing seamless request. Proposed
bounded next unit: permit the existing immutable stock-ROM definition seed for
that validated matching native fresh-continuation phase/target, no all-livelevel
fallback or cold/manual/async/prepared widening. ICZR11 independently owns16pattern
carry for synchronous transitions; coordinate the shared Sonic3k.java seam before
editing. No generic RAM/public snapshot/API change is proposed.

Independent short red-first design: load real DEZ2, independently decode1D5068
with the existing ResourceLoader and prove8000 extent/selected source words;
decode1DC24E and prove0E80 extent. Use the production continuation request and
actual fresh-load boundary, then assert replaced prefix plus retained ROM tails
and both unsigned FG queries. A normal cold/manual load must not borrow arbitrary
live definitions. Only after the intentional history reset, force recreation and
twice-replay the captured world. This is a proposal, **not executed evidence**.
No runtime extension or additional selection follows this handback. Root owns
coordination and later acceptance; existing ending6+46/Hyper/PAL/PCM remains open.


### DEZ128 guarded continuation candidate and later death limit — 2026-10-17

Root authorized the single128 producer repair after the preceding source handback.
New local branch `bugfix/ai-s3k-dez-final-definition-residency-gpt61` retains0c7d9f20d
and prior refs/config/native dirt. Short independent64188 on unchanged runtime
at17:14:22UTC is genuine **1/1F0E0S**,queue0/Maven1m14s/JUnit0.610s: actual ROM
streams independently decode8000/0E80, every source definition matches, and the
public continuation request plus actual fresh boundary reaches23/0; the intended
retained-tail assertion fails because only29 definitions remain. No private phase,
health/timer/routine/slot writes or long-route prerequisite.

Frozen candidate changes only Sonic3k.java's128 construction seed. A private
synchronous-capture boolean is true only at the public synchronous load; prepared
and generic construction calls pass false. Existing executing seamless behavior
is unchanged. The additional admission requires existing validated LOADING native
continuation, exact current-manager destination matching requested levelIndex,
and existing stock-ROM source identity; the existing immutable seed clone and
ResourceLoader prefix/tail semantics do the work. No16pattern carry, global bus,
new API/snapshot/ContinuationCarry shape, cold/manual/async/prepared widening or
route-keyed fallback. Runtime SHA256
`2e1a580f6d0d70fd2e4397599418a5807c4ce74ea7e118fc124d67d4e088ecab`,
new TestS3kNativeContinuationDefinitionResidency SHA256
`6cd8fb960e1913b64950e1c77ba98791e4697d0b345dfea0d55d041c394a7d49`.
Root independently reviewed these exact bytes: no material finding; reviewer
executed no tests. The REQUESTED prepared negative is not an independent LOADING
prepared test; exclusion in that phase has source-review support, not observed
phase-specific test coverage.

Focused32851 at17:16:44UTC passes99/0F0E0S,queue0/Maven1m23s:12 selectors match
13 classes (both level-loading classes). New3 methods check all8000 ROM destination
bytes, prefix replacement/tail144+184/actual unsigned word probes, cold/manual/
nonmatching/REQUESTED-prepared exclusion, and two forced-recreation full-registry
forward continuations after the intentional fresh-load history reset. These are
short declared continuation inlets, not a cold whole route or replay across a
retired history. Fresh authority/rewind architecture5109 passes29/0F0E0S at
17:17:32UTC,queue0/Maven28.671s. All XML for these epochs is inspected/consumed.

One required original entire TestDezIncomingFinalRouteCapture1616 at17:19:26UTC
is **7/5F0E0S**,queue0/Maven1m36s/JUnit73.454s. Original movies, bootstrap, strict
assertions and replay windows are unchanged; no instrumentation or second witness.
No unsupported-definition exceptions remain, but five actual later deaths block
qualification:

| Case | Actual result |
| --- | --- |
|coldOrdinarySoloSonic|death60811|
|coldOrdinarySoloTails|death67976|
|coldWideOrdinarySoloSonic|death60189|
|incomingFinalFight320|death16647|
|incomingFinalFight800|death16652|
|coldOrdinaryTeam|pass|
|coldEmeraldTeam|pass|

These five deaths are a new observed behavior boundary, not parity acceptance
or a proven native outcome. Main8712's matched solo Sonic passes; no equal-window
native death/terrain attribution is available. Stop all additional probes, tests,
runtime extension and input tuning. Keep frozen source and report the precise
limit to Root before normal runtime commit/disposition; no donor/viewport/native
presentation/whole-route coverage is newly certified for the failed rows. Root's
other baseline/broad/ICZ jobs remain separate; ending6+46/PAL/PCM stays open.


### DEZ solo death witness stopped on observer error — 2026-10-17

Root authorized one passive original solo-Sonic method to observe the five-route
candidate's later death; it did not authorize an automatic retry after a post-boot
observer failure. Production2e1a580f and maintained residency test6cd8fb96 stayed
frozen. Temporary test-only code forwarded the original production step and its
existing rewind-tail helper once, and delegated every CollisionTrace callback.
It used pure getter projections, guarded null-spawn positions and never polled
isDestroyed or wrote gameplay values. A rolling64 actual-step window was limited
to input60300–60812 and at most10MiB; there was no native/media/variant/new tree.

Build-only39825 passed at17:33:25UTC,Maven22.876s. One original selector
`TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`
with the preceding exact environment/flags returned **1/0F1E0S** at17:34:17UTC,
queue0/Maven32.343s/JUnit9.566s. The agent observer incorrectly dereferenced a
legitimate null SensorResult in onTerrainProbeResult, during a ceiling callback
in the original checkpoint60413 forward helper at submitted input60422.
This is a diagnostic NPE, not a gameplay or ROM discrepancy. The preceding
callback was delegated before the NPE; resource-finally restores the observer.
No correction or second witness was run.

The inspected1355463-byte journal retains64 actual steps60359–60422, with zero
omitted callbacks and no dead=true sample. The final step is **incomplete**:
P1 before3003/D2,velocity0000/F980,air/rolling,status06,routine2,rings12; after
exception unwind3002/CB,velocityFFE8/F9B8,status07,routine2,still alive/unhurt.
Camera2EE3/20,copy2EE3/22,boundsY20,zone23/0,generation2; LF9417→9418,
objectLF9418,VInt60398,timer34159. Do not treat that unwind state as a completed
row or claim a private jump/kill-entry observation. Original60811 death was not
reached, so its actual native writer and route-authorship attribution remain
**unresolved**. The smallest remaining question is still which real owner first
changes Sonic's safe terrain/support path before that death; no new fix follows
from this aborted observer. Prior independent99+29 passes and original7/5F
remain separate valid epochs, with five routes blocking qualification.

Temporary test source/helper/classes and inspected journal/XML are removed after
these facts; the four accounted candidate/source/docs WIP paths are retained.
No normal runtime commit, input retiming, wrapping/fallback or further execution.


### DEZ explicit replacement witness identifies pit writer — 2026-10-17

After the accounted41244 STOP, Root separately authorized one replacement of the
same original solo-Sonic selector, not an automatic retry. Temporary logging was
limited to rising setDead(true), applyDeath's existing cause, one computed shared
touch-hurt site, and a logging-only submitted-input stamp around the original
outer/replay steps. There was no sensor callback, object dump, gameplay mutation,
new API, changed input, native capture or new route. Temporary source fingerprints
were recorded separately from unchanged candidate2e1a580f/test6cd8fb96.

Build-only96384 passed at17:42:57UTC,Maven1m15s. Original selector
`TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`
using the preceding exact normal wrapper/environment returned **1/1F0E0S** at
17:43:42UTC,queue0/Maven32.543s/JUnit10.572s (case10.254s). The original strict
`death at60811` assertion remains the failure. No diagnostic error occurred.

The actual call chain at submitted input60811 (direction/action masks0/0) is
applyDeath(PIT) -> rising setDead(true), called by applyPitDeath from
PlayableSpriteMovement.doLevelBoundary:3730, modeAirborne:1087, then the normal
movement dispatch/SpriteManager/GameLoop/session step. Setter/applyDeath line
numbers include temporary logging and are not durable source anchors. At the
writer P1 centre13600/262,sub0/47104,x/y/ground velocities0/2016/0,
air/rolling/onObject/hurt=true/false/false/false,rings12,iframes0,
routineOverride=null,controls=false/false. Physical camera13584/32,copy13584/34,
Y bounds32/32/32/32. The gated shared touch-hurt log count is zero.

Native Player_Boundary_CheckBottom through Player_Boundary_Bottom
(sonic3k.asm:23223–23243) tests the death-plane/reverse-gravity branches, then
compares player Y against Camera_max_Y_pos+$E0 and jumps to Kill_Character.
Observed262 exceeds32+224=256. This establishes the engine pit writer and the
matching native boundary condition; it does **not** establish why Sonic was
already airborne without safe support, nor prove native outcome/route authorship.

The inspected53547-byte scalar journal has64 complete actual steps, including
original checkpoint forward/replay steps: submitted indices are nonmonotonic,
first retained60793 and last60811, not a contiguous64-input interval. Last normal
steps60809/60810 end at13591/255,vy1960 and13595/262,vy2016,alive/unhurt/airborne,
no support,rings12. Step60811 ends13600/255,vy-1736,dead,after the normal kill.
Level23/0,generation2,timer34159; its LF9806->9807,objectLF9807->9808,
VInt60787->60788 are distinct clocks. No earlier support-loss owner is observed.

Temporary sources/helper/classes and inspected journal/XML are removed after
these durable facts. Four accounted WIP paths remain; source2e1a580f/test6cd8fb96
are exact. No qualified runtime commit, fix, extra selection or parity claim:
99+29 controls remain separate, and the original7-case epoch still has five deaths.
The remaining causal evidence gap is the earlier loss of safe terrain/support,
not the identity of the now-observed pit death writer. Stop and hand back to Root.


### DEZ coherent known16/128 composition and independent old-red — 2026-10-17

The separately authorized source-only dependency check confirms missing consumers,
not the60811 PIT's earlier safe-support cause. On immutable original ROM, DEZ2
LLB23 reads16 sources1D1DD6(15E0)+1D7E3A(0210):17F0 bytes/766 known groups.
Final LLB46 source1D899C writes0CF0/414 groups. Retaining128 source1D5068's8000
bytes beyond final1DC24E's0E80 prefix leaves2484 references to352 distinct groups
19E..2FD outside the final16-written prefix,587 with solidity bits. All352 groups
are nonzero known predecessor ROM words within766, never inferred buffer capacity.
Final's own29 definitions have no references beyond414. Target LoadSolids owns
indices independently: SolidIndexes DEZ2=09B1C0/DEZ,final=09CFC0/HPZ,interleaved.
Retained128 byte3DE8,F1B0 references1B0 with sourcewordsD08E/D08F/C882/C881,
targetcollision68/68 versus predecessorFF/FF; oldbase capacity was four zero
pattern words/zero indices.156 solidity-bearing missing references have nonzero
target indices. This is a semantic dependency, not evidence it closes any route.

Under the owned writer, preserved33530-byte helper held only the three owned
tracked diffs and exact newtest. FF0c7d9f20d->Root271457b316145fbc8549ad7fd09cd71007d333cb,
which includes qualified ICZ70e8d10168d92cc30781e4072dc456f9d48093b8. Sonic3k128
patch applied cleanly by three-way; exact owned append-only doc suffixes were
appended to complete newRoot streams, and original6cd8 test restored exactly.
No whole old Sonic3k overwrite, Root WIP adoption, rebase, new tree or Main write.
Hooks installed; private regular config exact Main bytes and native4fdf preserved.
Source hashes distinguish old2e1a base results from this composed source.

One new independent public DEZ2->final request/fresh-load method ran on composed
70e+128-only runtime3791dedef6379cc30bb87c7e08a85c6f2f4a5dd10c4f772dfd34c85fe1996c0c,
testcc98d6ab40199e1d798e4ed4becef8c53b108d66a2053f63527a16dc3910ba11:
`TestS3kNativeContinuationDefinitionResidency#actualFreshContinuationRetainsKnownPatternTailWithTargetCollision`.
Exact preceding normal wrapper/environment/three original absolute ROMs, test -B,
no profile, no altered movie or capture.20783 returned **1/1F0E0S** at17:52:33UTC,
queue0/Maven1m10s/JUnit0.556s(case0.302s). All766 predecessor words and source-known
bound pass; actual fresh load and all414 target-written groups/current collision
indices pass. Intended retained1B0 firstword then fails expectedD08E/actual0000.
No prerequisite failure/error/skip. Old-red proves actual missing known16 tail,
not route outcome. Later alltail assertions and target unknown766/767 are beyond
that failure; source unknown766 was checked before the load.

Smallest private correction memoizes existing seamless OR validated matching
synchronous LOADING eligibility once in LevelBuildInputs, uses it for both128
seed and the synchronous known16 capture. Existing prepared/default build path
still passes ResidentChunkPatterns.EMPTY; no async/cold/all-live fallback,
predecessor collision copy, public API or placeholder change. Target ROM prefix
wins and qualified ICZ70e sparse/provenance implementation supplies known tail.
Frozen sourceffc6eba8fef3bd482f6390b7cba88677726ecb50e3db859c5dc78b418d357792,
test397cf71cbc728a42ec3464b08fffb410d02ce923410e64fa5623c1b447c71555 sent to Root
before any corrected execution. Existing two post-reset world recreation/forward
cycles now explicitly check known1B0 patterns/target indices and unknown766 too.
Root owns final predicate review; corrected checks and seven-route qualification
are not yet run/accepted. Prior99+29 and7-with-five-deaths are separate oldbase
epochs. No normal runtime commit or PIT/route acceptance follows from this red.


Root reviewed frozenffc6eba8/397cf71c, no material finding, then authorized one
focused selection.44773 stopped before any test at17:55:28UTC,queue0/Maven52.778s:
the new private record eligibility argument was missing in documented presentation-
only buildDetachedLevel. This was an agent compiler prerequisite error, not a
runtime/test result. Root authorized appending false only to that ROM-only caller;
readchecked source36381b201a3659f7b8c16b09fec64a489c03862644235491e8f0cefa1caee519,
test397cf71c and eligibility unchanged. One same-selection resubmission15490
returned **109/0F0E0S**,17selectors/18actualclasses at17:57:37UTC,
queue0/Maven1m21s. All18 XML inspected, no skips; frozen source/test hashes match.

Selection under the preceding exact normal wrapper/environment, test -B:

```text
-Dtest=TestS3kNativeContinuationDefinitionResidency,TestSonic3kResidentChunkPatterns,TestS3kIczRetainedChunkHandoff,TestS3kIczAct1TransitionHeadless,TestSonic3kLevelOverlayRewindIsolation,TestSonic3kDeferredLevelResourceLoading,TestLevelManagerDeferredResourceLoading,TestS3kDezSeamlessActChange,TestLevelContinuationCarry,TestFreshLevelBoundaryRewindHeadless,TestGameLoopFreshLevelHandoff,TestS3kFreshLevelRngState,TestS3kNativeWordLayoutResidency,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

Actual counts: newDEZ4,resident3,ICZretained1,ICZAct1transition1,overlay3,
deferred6/9,DEZtransition1,carry7,freshrewind2,handoff4,RNG4,nativeword4,
S3K4 AIZ8/LevelLoading36+7/bootstrap6/decoding3. Existing all8000/prefix/nativeword
probes and cold/manual/nonmatching/REQUESTEDprepared negatives pass. New method
checks target414 and retained352 groups against ROM/current collision, preserving
unknown766/767. Two postfresh-reset strict recreated-world/forward cycles also
check retained1B0/currentcollision and unknown766. The earlier REQUESTEDprepared
case is still not a distinct LOADINGprepared negative; source synchronous gate
and existing private defaultbuild EMPTY review bound that exclusion. No claim of
cold-route/reverse history continuity or full donor/viewport breadth from these
component tests. Results handed to Root before any seven-route requalification;
no original route, source/APIguard, newnative/media or broad selection run here.
Inspected20783 diagnostics and33530-byte composition helper were consumed after
durable facts under metadata lease alone; no patch/raw archive or pass cache.


### DEZ composed known16/128 seven-route qualification remains red — 2026-10-17

After actual109focused0skip, Root separately authorized ONE original entire
`-Dtest=TestDezIncomingFinalRouteCapture` selection with preceding exact normal
wrapper/environment/three absolute original ROMs, test -B, singlefork/reuse/alpha.
No temporary observer, diagnostics, movie/controller/input/assertion changes,
newnative/media or broad run. Frozen Root271457+runtime36381b20/test397cf71c.
73020 returns **7/5F0E0S** at18:00:36UTC,queue0/Maven1m35s/JUnit72.540s.
All seven XML case results are inspected:

| Original case | Composed actual result | Case seconds |
| --- | --- | --- |
|coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding|death60811|7.564|
|coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding|death67976|10.955|
|coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding|death60189|29.329|
|incomingFinalFightRestoresAndReplaysEveryPhase320|death16647|1.052|
|incomingFinalFightRestoresAndReplaysEveryPhase800|death16652|1.171|
|coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding|pass|11.202|
|coldEmeraldTeamClearsBothActsFinalFightAndDoomsday|pass|10.992|

These are the same five exact death assertions/indices and two passes as128-only
1616, with no errors/skips/newunsupportedmemory exception. Independent ROM-backed
known16/targetcollision/provenance correction therefore does **not** move the
observed route death endpoints. No claim of whole state/vector equality between
epochs follows from identical assertions. Main803 matched8712 soloSonic+ordinaryteam
actually passed2/0F0E0S at16:58:34UTC; that material baseline remains separate,
not an identical whole source fingerprint. No native route/death acceptance is
inferred. Root reviewed source and independent controls; five route failures
still block normal runtime qualification/commit/integration.

STOP at Root boundary: do not run the conditional five source/API guards (all7
green prerequisite unsatisfied), repeat109, probe/fix another owner, retime input,
rerun route/coldcampaign/native/media, or claim60811 support closure. Prior94784
identifies PIT only; why safe support was lost earlier remains unresolved.
Inspected73020 XML/text consumed after durable facts under metadata lease alone,
no archives. Four accounted WIP paths, private exact config, native4fdf and old
refs retained; no live handle or runtime commit. Root owns next disposition and
any later current-composition cold campaign requirement.


### DEZ matched original-route journals isolate first horizontal difference — 2026-10-17

Root separately authorized ONE baseline and ONE candidate original maintained
soloSonic selector, sequentially in owned fa8. Baseline8030832d2ab6080e5324cdfe35e971677fd97b41
overlaid only reviewed helperadcb0ef1/test13174fe2; all other tracked main/test
sources exactly803. Candidate271457b316145fbc8549ad7fd09cd71007d333cb retained
runtime36381b20/residencytest397cf71c and pre-run audit a270ffd1/Plan de1a47a6,
with identical helper/test bytes. Ordered portable pair b052d537. Stripping marked
blocks restores the entire original class SHA25663ce204f11b62724a6a40c6088fc62a79541362f99d831c959792e76c3ac188b
on both pins. No controller, boot, step/draw, checkpoint, 45-forward/replay, two
restores or original assertions/end-helper order changed. Native4fdf/private
regular config/Main bytes+mtime and old refs preserved. No native recording.

Both actual commands from fa8 used the following exact environment/selection;
only the two diagnostic property values below differed. No profile or separate
build; compiler ran inside each sole normal queued lifecycle.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
 EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
 JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 \
 python3 ${MAIN}/tools/testing/maven_queue.py \
 --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
 -Dsonic1.rom.path=${MAIN}/s1.gen \
 -Dsonic2.rom.path=${MAIN}/s2.gen \
 -Ds3k.rom.path=${MAIN}/s3k.gen \
 -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical \
 '-Dtest=TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding' \
 '-Dopenggf.tools.matchedStateJournal.path=target/dez-main803-matched-state.bin' \
 '-Dopenggf.tools.matchedStateJournal.source=8030832d2ab6080e5324cdfe35e971677fd97b41+helper-adcb0ef1+test-13174fe2' test -B
```

Candidate replaces only path with `target/dez-candidate-matched-state.bin` and
source with `271457b316145fbc8549ad7fd09cd71007d333cb+Sonic3k-36381b20+ResidencyTest-397cf71c+Audit-a270ffd1+Plan-de1a47a6+helper-adcb0ef1+test-13174fe2`.
Baseline18673 returned1/0F0E0S at19:52:35UTC, queue0/Maven1m29s, JUnit15.964s
(case15.666s). Candidate48975 returned1/1F0E0S at19:59:27UTC, queue0/Maven1m24s,
JUnit10.371s(case9.875s): original death60811, no diagnostic/key/cap/passivity error.
Queue submission is not execution start; exact process launch was not sampled.
Candidate execution is conservatively inside registry creation19:57:55.795204UTC
and console finish19:59:27UTC. No second candidate execution or CLI comparator.

Entire candidate was independently parsed/validated before paired comparison:
36-field schema/header/source/movie binding, 18-class dictionary, exact original
68-spot key schedule/order, every raw BK2 input, footer/passivity/counts/bytes/cap
and exact EOF. Baseline67187 observations/passivity, contexts60931/3060/3060/68/68,
complete=true,9945853B,SHA256cdc2ee0824db0202d9c3c3fe71d65e3de24d0e5c6d040f3e7c564950d2dd41ef.
Candidate66976/passivity,60812/3015/3015/67/67,complete=false,9914694B,
SHA2561f9f1c7365da0767f9a2d0443d6d8193c53f954a5b5ebcceb879af5c88ed18c8.
Root DIRECT independently inspected BOTH original readonly0444 binaries' entire
header/schema/dictionary/key/order/footer streams and confirmed the common keys
and differences. These are comparison-only post-step/restore observations;
complete=true means the unchanged ending helper/assertions returned, NOT that
every neutral ending-helper frame was sampled or that native parity is accepted.

All66976 common explicit keys match without alignment fitting. The first ANY
state difference is sequence66458, MAIN outer/submitted60570, raw Right8:
baseline X31F8/candidate31F5, vx0600/0300, ground0600/0000. Previous60569 all36
projections equal. At60570 both Y00CD.F800, Xfraction2400, declared flags2
(OnObj), actual riding class DezFinalArenaFloor/ownslot4. Zone23act0/gen2,
managerLF9566/OMframe9567/OMVbla60547/power-onVInt60547 are equal. No metadata,
clock, mode, load, fresh-boundary, logical-input, rings or shield difference
anywhere in the common prefix. First riding/support projection change is later
MAIN60577/sequence66465: same floor class, ownslot4/16, both OnObj2; Y205/204
with same F800 fraction and zero vy. First camera difference is sequence66532,
FORWARD outer60624+n20/submitted60644, X12905/12909. A later camera difference
is not the first cause. Both OnObj/same class/slot at60570 does not identify an
inside writer; no collision descriptor or terrain/solid callback was observed.
The earlier PIT writer60811 remains downstream, not closed.

After Root acceptance, these durable facts permit consuming both owned original
binaries under metadata lease alone, with no copy/archive. Inspected candidate
XML/text already consumed. Restore the maintained test by exact marked stripping
under source writer, remove only its instrumented outer class, and retain the
useful engine-facing journal helper source pending bounded promotion. Runtime
36381b20/residencytest397cf71c/helperadcb0ef1 remain unchanged. These appended
docs are AFTER the tested frozen epoch; no claim of rerunning new docs/restored
test, runtime qualification, five-route closure, guard pass or normal commit.
Next scope is SOURCE/PREP ONLY for actual60569..71 horizontal writer boundary;
no physics fix, terrain fitting, new execution, seven-case retry or native work.


### DEZ actual CalcRoomInFront writer witness, matched prefix only — 2026-10-17

Root reviewed the entire three-file reversible diagnostic source, then corrected
the captured boot-player identity risk before any execution. Each selected MAIN
arms the current session.player() field identity; output rechecks that same P1
and context, pose and copied probe/result projections. Zero owner callbacks is
explicitly inconclusive/STOP. Final diagnostic APS9f2859e9/Collision3118302f/
routeTest794918fa, ordered portable0adcd9a40d2155c5ff45ec34dad915baa4f4d53c687078898fab89d8d3f82fa2.
Marked stripping independently recovers entire original APS838c9252/Collision
aa075429/routeTest63ce204f on BOTH Main803 and candidate271457. No new runtime
API/class, CollisionTrace/provider replacement, reprobe or gameplay-value write.

Root separately authorized exactly one BASE and one candidate writer PREFIX.
The exact prior matched-original command/environment above was retained: normal
Main absolute maven_queue.py from fa8, three original absolute ROMs, Java25,
single EGL JVM token plus CLI, surfaceless/nullAL/Lua, DISPLAY/WAYLAND/LD unset,
fork1/reuse/alpha/no-profile, originalsolo selector. The only diagnostic props:
`-Dopenggf.tools.dezFirstWriter.path=target/dez-writer-base803.bin`
`-Dopenggf.tools.dezFirstWriter.source=8030832d2ab6080e5324cdfe35e971677fd97b41+writer-0adcd9a4`
`-Dopenggf.tools.dezFirstWriter.prefix=true`; candidate changes only path to
`target/dez-writer-candidate.bin` and source to
`271457b316145fbc8549ad7fd09cd71007d333cb+Sonic3k-36381b20+ResidencyTest-397cf71c+writer-0adcd9a4`.
One compiler-inclusive queued lifecycle per arm, no separate build, target copy,
outer lease or CLI comparator. During BASE all tracked source outside those
three inserts was exact803; helperadcb retained unchanged. Eight owned candidate
paths were temporarily preserved, then restored byte-exact under writer after
BASE, temporary1272400B source snapshot consumed metadata-only. Unknown native
4fdf/config/Mainmtime/refs preserved. No compiler repair or retry.

BASE16038 returns1/0F0E0S at20:20:24UTC,queue0/Maven1m26s,JUnit9.459s
(case9.208s). Candidate18206 returns1/0F0E0S at20:26:12UTC,queue0/Maven1m22s,
JUnit9.187s(case8.934s). These are PREFIX ONLY: original MAIN60569..60571
steps/draws/per-step assertions run, prior checkpoint windows/replays/restores
remain unchanged, and static disjointness proves no checkpoint is cut on the
three keys. Diagnostic return follows original60571 per-step assertions. Full
route, later60811 death assertion and final ending/helper assertions are
UNREACHED. These files contain NO clock projection; no previous clock-equality
claim is transferred as fresh evidence. Queue0 is not a sampled launch time.

Both entire length-prefixed UTF8 streams were validated before interpretation:
header/source/movie SHA, keys/raw Right8/action0, order, arrays19 inputs/11 result
(or null marker), counters/passivity/stacks, footer/EOF/1MiB cap. Root DIRECT
read both single original readonly0444 files in full, every record and stack:
BASE29747B,SHA256de8bc99e1660f8650ada48f4e4f631bde6a61f8f9c2ee40e47f3234ab8b20998,
11records/9events,3begins+3actualprobes+3asserts-complete,0changedsetters,9passivity;
candidate67156B,SHA256811e3dbe70fa513bd5450549e2a59048cf9ebb557bcfe35b84a40531978961eb,
15records/13events,3begins+3probes+3asserts-complete+4changedsetters,13passivity.
Footers originalFailure=none,prefixComplete=true,fullFinalAssertions=false.

The first RESULT split is60569, despite equal actual probe inputs and P1 pose:
BASE distance+19/angle03/tileId0 vs candidate+3/angleFF/tileIdFF. At60570 inputs
and prepose are still identical; BASE+29/03/0 vs candidate-3/FF/FF. Actual
candidate setter caller stack is CollisionSystem.applyGroundWallVelocityResponse
558/559 <- resolveGroundWallCollision426 <- PlayableSpriteMovement.doGroundMove
3058 <- modeNormal893: vx0600->0300 and g0600->0000, before originalmovement
produces X31F5 rather than BASE31F8. Both are still OnObj at that boundary.
60571 ordinary ground acceleration then writes g0->000C and calculateXY writes
vx0300->000C; its probe0 vs BASE+23 has already-divergent inputs.

The actual one-call inputs at60569/70 are angle0,g/vx0600,vy0,probeModeC0,
rotatedAngleC0,Direction.RIGHT ordinal3,offsetX10/offsetY0,dynamicY8,predictedDx6/
Dy0/effectiveDy8,solidity13/top12/lrb13,tunnel/convex/suppressWall0. Result FF is
not a raw layout/128/16/collision descriptor or proof of native correctness. The
writer is now observed; which resident terrain definition should feed that scan
remains unresolved. Do not blank terrain, restore visual-width wrapping, fit
velocity, infer moving-floor carry or waive the five failed original routes.

After durable facts and Root direct acceptance, restore only the three marked
diagnostic sources exactly under writer (including original assignments/return),
preserve four WIP runtime36381/residency397cf/docs plus useful helperadcb, then
consume BOTH originals metadata-only after hashes, no archive. Normal reports
already inspected/consumed. Target's last build is candidate+diagnostics, NOT a
maintained no-observer rebuild. No runtime commit/fix/repeated route follows.
Next scope is SOURCE READ/PLAN ONLY for actual coordinate->layout128->16 pattern/
collision-index authority, with a new bounded passive descriptor proposal only
if existing source/ROM cannot establish the missing actual selection.


### DEZ ScreenInit unused encoded-layout owner, source and independent red design — 2026-10-17

SOURCE ONLY after matched writer cleanup; no new compiler/JVM/test/native run.
Root and an independent read-only worker byte-checked the shipped owner.
LevelPtrs[46decimal] at ROM0x09D678 is0x000A7F4C(Layout_DEZBoss); exact header
bytes0014 0008 0004 0004 mean FGwidth0x14(20decimal),BGwidth0x08(8decimal),
FG/BGheight0x04(4decimal). Native DEZ3_ScreenInit0x05A3DA..0x05A400(exclusive),
sonic3k.asm120297–120310, uses unsigned mulu.w products0x0050/0x0020, low-word
addition plus0x0088 =>offset0x00F8. Counter0x0F07 and inclusiveDBF clear0x0F08
bytes RAM0xFF80F8..0xFF8FFF. This is an explicit scene write, not a global
loadMap truncation or a claim that definition0 is empty. Load_Level first copies
0x1000 bytes; final layout asset length248decimal=0xF8 matches the immutable ROM.
Following MHZ_Custom_Layout starts0x0A8044, contributing bytes0x06/0x80 at final
copy offsets0x00FF/0x0100. Current and Main803 ScreenInit omit that native clear.

Source-derived scan origins from the OBSERVED equal inputs are(0x31FC,0x00D5)
and(0x3202,0x00D5), not captured raw layout descriptors. Current encoded lookup
with the source row-1 pointer0x809C would select0xFF80FF/0xFF8100; Main803 wraps
X by the visual width0x0A00(2560decimal), selecting columns0x13/0x00 and bytes
0xFF80AF/0xFF809C. Both are source-deduced paths until actual pointer/plane/
loop-low-plane/primary-versus-adjacent metadata is observed. Existing DEZ source
updates touch only FGrow0x02col0x0D/0x0E androw0x03col0x0E; neither rewrites
row1. Native ScreenInit aliases row0 to FG/BGrows0x04/0x1F, notrow1; engine
render aliases are separate from encoded collision ownership. SensorResult FF
is SolidTile collision-PROFILE index0xFF, not raw128/16 descriptor/index.

The target bank is independently fixed: SolidIndexes[46decimal]@0x0981B8
->0x09CFC0, interleaved secondary0x09CFC1, same bytes as HPZ. Ordinary DEZ
entries22/23decimal use0x09B1C0 and must NOT be carried/repointed into final.
Engine layout128->ChunkDesc(index/flip/solidity)->known16->targetcollision index
->global angle0x096000/horizontal profile0x097100 mirrors Find_Tile_FG/FindWall;
actual selected hierarchy remains the missing runtime descriptor fact. No clear
fix or route acceptance is inferred merely from the observed profile FF.

Authorized TEST SOURCE ONLY adds two methods to existing TestDezFinalScreenEntry:
`coldFirstProductionScreenInitClearsOnlyRomUnusedLayoutTail` and
`publicNativeContinuationScreenInitClearsOnlyRomUnusedLayoutTail`. Real final
boot23,0/first production step and existing public DEZ2->final fresh-load inlet;
no private phase/timer/health/player/slot writes. They authenticate the COMPLETE
0x26-byte native clear body including operands, exact header and read-only
registered s3k-collision-layout encoded value. All two forced object/world
recreation+forward cycles precede the final0x00FF/0x0100/all-tail discriminants.
Strict registered-world equality includes real adapters/objects/events/art/
players; captures are after intentional continuation history reset, not cross-load
replay. Separate controls retain exact ROM128/known16 words and target0x09CFC0/+1
indices, compare opaque capacity only for preservation, and preserve meaningful
row1/prefix bytes excluding the eight native alias-write bytes.

An explicitly DECLARED legitimate later mutation control applies the actual
DEZ3_BackgroundEvent FG(col0x0D,row0x02)=block0x1A command through the existing
zone mutation pipeline. It is NOT a naturally reached boss-signal claim.
Repeated ScreenInit must preserve that edit, exact world and current encoded-owner
identity; identity is compared within the current capture, never across recreated
worlds. This isolates once-only scene ownership before the intended old-red.
No blank velocity, definition0-emptiness assumption, ROM RAM hydration, new API,
probe or campaign variation is authored. Source awaits Root handback/review and
a separate one-short-execution gate; no result or runtime qualification claimed.


Actual independent old-red76475 on frozen candidate271457/runtime36381/residency
397cf/helperadcb/newScreenEntrytestff0f82a3: exactly the two proposed methods,
normal Main absolute wrapper/all3originalROMs/Java25/EGLsingleJTO+CLI/nullAL/
noambient/Lua/fork1reusealpha/no-profile,compiler inside the sole lifecycle.
Returns **2/2F0E0S** at20:45:43UTC,queue0/Maven1m11s,JUnit0.800s
(cold0.410s,publiccontinuation0.158s). BOTH fail only final helperline130:
DEZ3_ScreenInit must clear native layout RAM$FF80FF, expected0/actual6.
All preceding full-ROM-body/header, actualinit/inlet, two forced registered-world
recreation/forward cycles, actualsupportrecreation, target0x09CFC0/+1 and exact
128/known16/opaque-preservation, declared background edit/idempotence/prefix/row1
controls executed and passed. Later$FF8100/all0x0F08tail assertions are UNREACHED.
No compiler repair, prerequisite/world/control failure or retry. Root DIRECT
inspected original XML25657B/SHA2565dbbc963abef9b6101a1bf8fce4b678d8e676e62d1acd0b3869587da6317a1d9,
all2identities/fullfailures; TXT2691B/SHA256b7bb219f49d91981b041d9d155ce9476860f0923204cc4f0c264b0a49a97098e
also inspected. After these durable facts, consume both original reports28348B
metadata-only, no archive. Testoracleff0f remains unchanged; no runtime edit yet.

Minimal design handed to Root BEFORE any implementation: package-owned
S3kCollisionLayout header-derived/range-validated tailclear with existing COW and
first/next Map alias synchronization; narrow internal delegation through the
unannotated Sonic3kLevel implementation; actual once-only ScreenInit calls it
inside existing zone mutation pipeline before allocations/initial plane refresh.
No shared encoded-layout/publicModAPI contract, loadMap truncation, collision-bank
change, four-row-alias extension, definition0-emptiness or route closure. Await
separate runtime authorization; existing five route failures still block commit.


DEZ ScreenInit SOURCE FREEZE (after old-red76475; no corrected execution):
Root authorized the minimal three-file encoded-owner change. S3kCollisionLayout
now derives the unsigned header products/low-word sum+$88, validates $88..$1000
before writes, and clears only actual nonzero tail bytes through existing encoded
COW plus first/next flattened Map aliases. An already-zero tail returns without
COW cloning; row pointers/prefix/definition resources remain unchanged. The
unannotated Sonic3kLevel implementation delegates internally; actual ScreenInit
calls it once inside the existing mutation pipeline before marker/allocations,
returns NONE effects, and retains the later explicit plane refresh. No shared
interface/ModAPI/pin/global loadMap change or native four-row-alias extension.
Final collision authority remains $09CFC0/+1; definition0 emptiness and route
closure are NOT asserted. Prior five-route failures still block qualification.

Frozen runtime SHA256:
- `src/main/java/com/openggf/game/sonic3k/S3kCollisionLayout.java`: `6b47c7a5e56c72af13c3dcfcea9959c4026e801dc503c2726b34cab46e5f7bcf`
- `src/main/java/com/openggf/game/sonic3k/Sonic3kLevel.java`: `887e4570255d493526dd433b69bbd355f19863e48cfbb9a6deda458dc6d9dfa9`
- `src/main/java/com/openggf/game/sonic3k/events/DezFinalScreenEvents.java`: `c2519db25a954a9e837d3167b307293814f48719f3c61b28887dc8322d3a3838`
Three-runtime-only binary diff: 5329 bytes/SHA256 `c46dac2e34f34057960680de6e1a59a79b8b06a678135b1bc31e01b577233fc8`.
Oracle ff0f82a3, residency runtime36381/test397cf and helperadcb are unchanged;
original route class63ce204f restored. Source base remains271457b316145fbc8549ad7fd09cd71007d333cb.
`git diff --check` passed; no compiler/JVM/corrected tests/native/route/commit.
These documentation additions are after the old-red tested epoch. Await the
separate bounded affected-check execution gate; do not reuse old red as green.


DEZ ScreenInit affected execution74516 (wrapperPID3310805), frozen reviewed
6b47c7a5/887e4570/c2519db2 plus36381/397cf/helperadcb/oracleff0f, base271457.
ONE normal Main absolute maven_queue.py lifecycle, same accepted all3original
absoluteROMs/Java25/EGLsingleJTO+CLI/nullAL/noDISPLAY/WAYLAND/LD_LIBRARY_PATH/
Lua5.4/fork1reusealpha/no-profile environment. Maven arguments: --normal
-Dmse=off -Dopenggf.headless.gl=surfaceless-egl
-Dsonic1.rom.path=${MAIN}/s1.gen
-Dsonic2.rom.path=${MAIN}/s2.gen
-Ds3k.rom.path=${MAIN}/s3k.gen
-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical
-Dtest=TestDezFinalScreenEntry,TestDezFinalArenaFloor,TestDezFinalEntryObjects,TestDezFinalBackgroundEvents,TestDezFinalPlaneState,TestS3kNativeContinuationDefinitionResidency,TestS3kNativeWordLayoutResidency,TestSonic3kLevelOverlayRewindIsolation,TestSonic3kResidentChunkPatterns,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test -B.
ACTUAL terminal0 at2026-10-17T20:56:00UTC,queue0/Maven1m19s,
122/0F0E0S in14classes (13simpleselectors; LevelLoading matches TWOclasses).
All14originalXMLs/all122testcase identities and TXT summaries read; allcases
positive/no failure/error/skips. Each XML records correct three absoluteROM
paths and surfaceless-EGL property. Per-class actualcounts:
- `com.openggf.game.sonic3k.TestSonic3kBootstrapResolver`: 6
- `com.openggf.game.sonic3k.TestSonic3kDecodingUtils`: 3
- `com.openggf.game.sonic3k.TestSonic3kLevelLoading`: 36
- `com.openggf.game.sonic3k.TestSonic3kLevelOverlayRewindIsolation`: 3
- `com.openggf.game.sonic3k.TestSonic3kResidentChunkPatterns`: 3
- `com.openggf.game.sonic3k.events.TestDezFinalBackgroundEvents`: 7
- `com.openggf.game.sonic3k.objects.TestDezFinalArenaFloor`: 11
- `com.openggf.game.sonic3k.objects.TestDezFinalEntryObjects`: 8
- `com.openggf.game.sonic3k.objects.TestDezFinalScreenEntry`: 15
- `com.openggf.game.sonic3k.runtime.TestDezFinalPlaneState`: 7
- `com.openggf.tests.TestS3kAiz1SkipHeadless`: 8
- `com.openggf.tests.TestS3kNativeContinuationDefinitionResidency`: 4
- `com.openggf.tests.TestS3kNativeWordLayoutResidency`: 4
- `com.openggf.tests.TestSonic3kLevelLoading`: 7
Both unchanged oracleff0f methods passed AFTER all forced two-world/replay,
ROM-body/header/resource/target9CFC0/+1, opaque-preservation, meaningful-prefix
and later legitimate BGedit/idempotence controls. Thus FF80FF, FF8100 and ALL
0x0F08 tail discriminants were reached and passed, unlike old-red76475.
ScreenEntry originalXML28419B/SHA2561440990a6623c6fcfef6fe46490e7d77f4dcdfa03cbbb57f782318e544cfb625.
No compile repair, retry, unexpected field, source mutation during queue/run or
new native evidence. Prior109 is a DIFFERENT source epoch, not this122 claim.
No original seven-route requalification, source/APIguards or commit launched;
prior five route failures remain unqualified. Inspect then consume only these
owned28 XML/TXT reports under metadata_slot ALONE, no archive.


Conditional DEZ source/API guard5039 (wrapperPID3317054) ACTUAL5/0F0E0S,
terminal0 at2026-10-17T21:02:33UTC,queue0/Maven25.334s. ONE fresh normal
-Pguards lifecycle with accepted all3ROM/EGL/JDK25/nullAL/noambient/fork1reusealpha
shape; exact -Dtest=TestArchitecturalSourceGuard#runtimeProductionCodeDoesNotReadDisassemblyAssets+productionGameplayCodeDoesNotBypassLevelMutationSurfaceWithRawLevelMutators+hardwareTimingAuthorityExceptionStaysDocumentedAndAgentGuidanceStaysMirrored,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface+engineOnlyRuntimeHelpersDoNotLeakIntoTheZeroSevenSurface test -B.
Both originalXMLs/all five exact case identities and TXT inspected, zero skips.
Literal reviewed3runtime/residency/test/helper/originalroute hashes reverified
unchanged after completion. Conditional original7route class gate is now earned;
NO route result inferred. Consume only these four reports metadataalone after
durablefacts; no archive. Source unchanged; this is NOT entire29/118guards.


Conditional ORIGINAL full DEZ class54531 (wrapperPID3318408), after actual
5guard pass and literal source recheck: ACTUAL7/0F0E0S, terminal0
2026-10-17T21:05:15UTC,queue0/Maven2m11s,JUnit108.6s. Exact command from owned
fa8cwd: env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH
EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null JAVA_HOME=/usr/lib/jvm/java-25-openjdk
JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4
python3 ${MAIN}/tools/testing/maven_queue.py --normal
-Dmse=off -Dopenggf.headless.gl=surfaceless-egl
-Dsonic1.rom.path=${MAIN}/s1.gen
-Dsonic2.rom.path=${MAIN}/s2.gen
-Ds3k.rom.path=${MAIN}/s3k.gen
-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical
-Dtest=TestDezIncomingFinalRouteCapture test -B. No profile, diagnostic/path/source/
input properties, observer, new movie, copied driver, gameplay writes or native.
ALL original XML testcase identities and TXT summary inspected; no skips,
failure/error elements or unexpected prerequisite. Actual identities/seconds:
- `coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding`: 16.617s
- `coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`: 13.075s
- `incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]`: 8.104s
- `incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]`: 12.404s
- `coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`: 38.143s
- `coldEmeraldTeamClearsBothActsFinalFightAndDoomsday`: 9.725s
- `coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding`: 10.254s
Original route class63ce204f remained byte-exact to803; per-step death/ordinary
character checks, established phase/checkpoint restore/forward assertions and
original ending helper/end assertions remained active. Original old deaths
soloSonic60811/Tails67976/wide60189/incoming32016647/incoming80016652 no longer
reproduce in this class, and both original team passes remain passes. This is
a bounded five-case regression closure on composed known128+known16+ScreenInit
clear source, NOT native pose/descriptor equivalence, another BK2, a broad/full
suite or cold-campaign acceptance. No separate isolation claim that known16
alone fixed these routes: composed70e+128 previously still failed all same five.

Frozen source remains reviewed3runtime6b47c7a5/887e4570/c2519db2, residency
36381/397cf, unchanged oracleff0f/helperadcb and originalroute63ce; base271457.
OriginalXML413141B/SHA2562fc7f7a4e98d2d210a3298fe3dee687b7164dbbe98c5470e5c056c185fcf850f,
TXT350B/SHA25699b50746d5f894891985d19f9c5bb06280fbf63c5d4d7d6dfc0448804fe82002.
Reports remain only at original target paths for immediate Root handback, no
archive; consume under metadata_slot ALONE after durable inspection acceptance.
No route retry, extra execution, source edit, qualified commit/integration/push.
Four native row-alias writes and actual native collision descriptor/clock
qualification remain separate limits; definition0 is not asserted empty.


Qualified LOCAL commit preparation: Root DIRECT read/hash-verified original
routeXML2fc7f7a4/all7identities/no skips and accepted122focus/5freshguards/7routes
as separate epochs. Both original routeXML/TXT413491B consumed after hash check
under metadata_slot ALONE, no archive. Runtime/oracles/originalroute/movies remain
frozen. Journal helper promotion changes HEADER ONLY from testedadcb; stripping
that comment insertion recovers its exact observed source. Existing workflow
README names purpose/inputs/no installed caller. No maintained/production caller
or property installation, functional refactor, compiler/JVM/test/native follows.
KnownS3K/backlog/matrix/frontier/releasefold/pitfall mirrors record actual owners,
rejected128-only/known16-alone route hypotheses and corrected7case result.
This commit is local only; Root owns composition/integration/push/cold validation.


Local resource-policy normalization before commit: `${MAIN}` in the exact command
records denotes the absolute Main-workspace root used at execution (original
paths remain in the task ownership registry, not portable repository prose).
This replaces machine-local path spelling only; source/ROM identities, cwd,
flags, selectors, environments and observed results are unchanged. The first
normal pre-commit attempt rejected that prose; no code/test execution occurred.


### R7 geometry-rebind size ratchet extraction — 2026-10-17

The FDF Title guard68289's sole failure is NOT a new Title source regression,
but IS an overall owned composition regression. The actual XML identifies
user.dir/project.basedir as the FDF tree; TestArchitecturalSourceGuard uses
SRC_MAIN=Path.of(src,main,java), SourceFile.read, stripComments(text,false),
CR/LF lines, nonblank counting and exclusion only of standalone @ModApi. The
unchanged budget is3144. A source-only exact state-machine reproduction matches
its existing8-line sample and the actual failure3153. No baseline JVM was run.

Input identities: Main8030832d2ab6080e5324cdfe35e971677fd97b41 LevelManager
SHA2566807b61ff1302c82ba523c5469292de3bacde803bad81bd825417370630d1731
counts3141. FDFpre74daf0939bec4ecd5f8ff7c8bc7414d9ff5058e6, its current
Title-WIP file and Root86d1794dc6fa0bb4875ddceef0af15af04bcf67d all use
LevelManager76193c0671bb235a3b7b494f37d170b25c3b58ebfc0861644990ca7b48eee3bd
and count3153. All use guardeffa50492eb609231ce9c4570ffca3b4bfd3e5b859f2271b217c440a0111642f.
Owned2077fd1d1834a3d369864bdb28027415a3eacbcc adds exactly12effective lines
for rebindCurrentLevelGeometryForRewindRestore; its parent5c59e6da1ab66064d1aea5047d507185580bf23d
has byte-identical Main803 geometry manager. Three lines of headroom leave nine
over budget. Thus unchanged within the Title slice does not make it inherited Main.

On owned fa8 branch bugfix/ai-s3k-rewind-geometry-budget-extraction-gpt61, FF-only
49ae196a69745289472f524ceda420c9d8e8b610 -> exactRoot86d179, recursive checkout
disabled and privateconfig/nativephysicalhead/oldrefs preserved. The complete
12-effective-line restore body moves to existing engine-only
LevelTilemapRewindAccess.rebindCurrentLevelGeometryForRestore(manager). Removing
only manager receiver qualification recovers its old body EXACTLY, including
null throw/text, assignments/cachecall, updateGeometry, retained-plane invalidation,
early/no-tilemap fallback and comments. Remove the now-redundant package-private
manager method; make only chunksPerBlockSide/cacheLevelDimensions/buildGeometry
package-private. All public/protected declarations remain unchanged; no descriptor,
pin, schema, capture state, mapping, animation or lifecycle changes.

Source-only count is now3141; guard/budget unchanged. Frozen reviewed hashes:
LevelManager31a7201fb1f83d2565a49b4d762d4ee0036508439b501e8ec1853d04c7093a4a;
LevelTilemapRewindAccess9693c50d75dbb14a20031eda8057d71e3a03a674b35fdd5f0d5549baab3af2d9.
Two-file binarydiff4330B/SHA256ed151232a34c07b974ed36a526dd5f89f77d2791b4576d930d8f0f1aed93a463;
ordered(path NUL bytes) combined946d6159a0004604412b726d4ef5a5cc6944499cbd9ecb2485d6341ece8575c9.
Root directly read/accepted that exact diff before compilation.

Two authorized normal Main-absolute-wrapper lifecycles from the owned cwd, compiler
inside each, source frozen/no duplicates. Portable command prefix (`MAIN` means
the absolute Main root used at execution):
```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
 EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
 JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 \
 python3 "${MAIN}/tools/testing/maven_queue.py" --normal -Dmse=off \
 -Dopenggf.headless.gl=surfaceless-egl \
 -Dsonic1.rom.path="${MAIN}/s1.gen" -Dsonic2.rom.path="${MAIN}/s2.gen" \
 -Ds3k.rom.path="${MAIN}/s3k.gen" -Dsurefire.forkCount=1 \
 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical ... test -B
```
Affected38036 replaces `...` with `-Dtest=TestLevelTilemapManagerRewindReset,TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
ACTUAL77/0F0E0S,7classes, terminal22:16:26UTC,queue0/Maven1m21s:
- `com.openggf.game.sonic3k.TestSonic3kBootstrapResolver`: 6
- `com.openggf.game.sonic3k.TestSonic3kDecodingUtils`: 3
- `com.openggf.game.sonic3k.TestSonic3kLevelLoading`: 36
- `com.openggf.level.TestLevelTilemapManagerRewindReset`: 16
- `com.openggf.tests.TestS3kAiz1SkipHeadless`: 8
- `com.openggf.tests.TestS3kSlotPlanePublication`: 1
- `com.openggf.tests.TestSonic3kLevelLoading`: 7
Both LevelLoading packages were deliberately selected by basename. All16 tilemap
rewind cases include retained-plane/ordinary/stale-forward/live/scene controls;
the exact encoded Slot registration-order case passes. Mandatory S3K4 passes.
All7XMLs/all77caseidentities/properties/streams and TXT read; no skips/SEVERE.
Their14inspected reports185812B were consumed metadata_slot ALONE, no archive.

Freshguards74389 replaces `...` with `-Pguards -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface+engineOnlyRuntimeHelpersDoNotLeakIntoTheZeroSevenSurface`.
ACTUAL99/0F0E0S, terminal22:17:42UTC,queue0/Maven32.737s: Architectural72,
HardwareTimingAuthority25 and exactAPI2. All3XMLs/all99identities/properties/
streams and TXT inspected/no skips/SEVERE; the original size predicate ACTUALLY
passes. No separate size-only repetition, repair, retry, new API or budget waiver.
Consume these6original reports metadata_slot ALONE after durablefacts; no archive.

These focused results qualify the extraction only. FDF's separate original-route
gate remains its owner's responsibility; Root29527's frozen combined smoke has
not tested this extraction. No new Title/DEZ route, native/capture, broad/full
suite, integration or push. Qualified DEZ49ae functionality is unchanged. Normal
local preservation commit is authorized after these two green gates; all seven
policy trailers precede the parsed final GPT6.1 coauthor.


## R34 to R36 component CPU mismatch is observed before the timer oracle — 2026-10-17

R34 source-only ordinal38 rejects a speculative leader-start correction. The
builder positions the leader before bootstrap/history initialization, whereas
this component repositions after build, but no R33 selected history/guard was
observed. Source also exposes an independent offscreen OnObj interact-word
comparison before the counter/history-follow path; moving the leader cannot
establish which branch caused the actual prerequisite failure. No inlet changes
or additional execution follow that source inventory.

R35 source-only ordinal39 inserts reversible passive blocks in only CPU and this
existing five-method test. CPU SHA256
`fc9ee63fed5ff2ff99ed501ca44bad3e996eef84f809f5bb0d5ae0ff4c166ca0`,
test `b9ec0857c17d6e94fd67e4c17492f188c37293769b339577f4439d4e36967c38`;
ordered path-NUL-bytes-NUL combined
`4daab9e6b53a85327f9ed8748a991125a38032eeb63bff77b42ef019e90220d8`.
Strip all four CPU/three test marked blocks to recover exact runtime
`4bb9f42fc7e8c0551aeb94e9d4d612819e7981ff1db15e512c5e6aefdc3d6c5a`
and accepted test0d285. The actual mismatch branch passes already-computed locals;
trigger/marker hooks log their real argument and stack before original writes.
Exact P2/controller identities bind the test-owned context, restored in finally.
No owner getter, fresh visibility, CPU/history/timer/latch write or replacement.

Root reviews the exact files and authorizes R36 ordinal40, sole session84126 on
HEADdcbb/Java7df. It uses the exact R33 normal Main-absolute wrapper command
above: `-Dtest=TestHczSpinningColumnCachedRelease`, all five methods, no profile,
original three ROMs/Java25/single EGL JTO plus CLI/nullAL/no ambient display or
LD path/Lua5.4/fork1/reuse/alphabetical. Compiler is inside this one lifecycle,
with no repair or extra build. Terminal1 at21:18:38UTC, reported queue0/Maven01:21,
suite0.939s: **5/2F0E0S**. All five identities/full failures and all16 continuous
records are inspected. Records1..16 occupy19778 UTF-8 bytes including newlines
(conservative charge79064, below128KiB); ordered line SHA256
`239bcf101c4746ecf434c732e9d3adbe13220418e059a77ff2163b9f5872cad7`.
All completed output sites pass CPU27/test25 known-projection equality and
context identity checks. This is bounded projection evidence, not proof that
all shared state is untouched; the prior background derived-cache memoization
qualification still applies. No cap, observer, compiler or new prerequisite error.

Both released cases directly select the same production branch at CPUframe2:
valid cached on-screen=false, Air=true, OnObj=true, managed slot readable=true,
interact SST10, raw live object ID104, already-computed live code word3 versus
raw stored diagnostic interact word0. Respawn/flight/manual counters are0.
Actual stacks show updateNormal -> checkDespawn ->
triggerDespawn(OBJECT_ID_MISMATCH) -> applyDespawnMarker(false). Pre-step CPU is
NORMAL/frame1, P2=$0408.3A00,$03D0.7B00, velocities0, lock35 or natural0,
control/CPU-allow/suppression/mapping flags false. Post-step is CATCH_UP_FLIGHT,
frame2, P2=$7F00.3A00,$0000.7B00, Air=false/OnObj=false, all four ownership
projection flags true; locks remain35/0. The post-step Air caller is unobserved.
The despawn marker preempts the intended released ordinary-physics oracle;
these are the same two original CATCH_UP post-step prerequisite failures,
**not genuine release timer old-reds**, and neither final release replay/oracle
is reached.

The grounded-positive control directly supplies three before/after pairs
(original plus twice-recreated forward): visible cache=true, NORMAL/frame1,
stored interact projection0/lock35 -> PANIC/frame2, stored projection3/lock34.
Air=false/OnObj=true and pose/fractions/velocities stay unchanged. Its original
K-1/recreation assertions and both existing release/visible-jump cases pass.
The live selected word is unobserved on this non-mismatch branch; the reported3
is the actual stored diagnostic projection, not a repeated owner query.
Native001D's earlier producer and campaign8774's actual Air writer remain
separate unknowns. No CPU/cache/timer/grounding/provider/runtime fix, route
qualification, native query or source variant follows this diagnostic.

Root directly accepts the original XML47407B SHA256
`7f5a7ffd2be740616f8f25951cc603661300ee0a6e3410c9f6d901d295bac5cf`
and TXT3471B SHA256
`587e24abd0a3ee73aef2f648f6c1ad675251003c66da0b8a23052bbd237b3393`,
all five cases/full16 records/stacks. R37 authorizes this durable account, exact
marked-block source restoration, deletion of only the four hash-inventoried
R35-containing compiled outer/projection classes, then metadata-slot-alone
consumption of those original two reports. Other compiled inner classes and
unknown/native work are preserved; no archive or failing-test/runtime commit.
R34-36 exact IDs/results remain separate in both existing registries.

After the durable account and Root acceptance, exact source strip proofs pass;
R37 deletes only the four inventoried R35-containing classes under the intended
writer. Accepted XML+TXT50878B are rehashed and consumed at21:23:49UTC under
metadata_slot alone, with no copy/archive/receipt. The unchanged existing inner
classes, private regular config/native4fdf/all refs and foreign work remain.


## R37 to R39 visible CPU inlet reaches the independent timer old-red — 2026-10-17

R37 source-only ordinal41 preserves test0d285 except two new release-case
fixture calls and a semantic helper. It first obtains the same real captured
fixture (including the production subtype2 bubble when requested), publishes
real visible render state, then takes exactly one ordinary fixture frame before
the held/offscreen-release snapshot boundary. Same P2/controller, LF+1,
NORMAL/PANIC, actual column ride/standing/held, cached-valid+visible and stored
CPU interact projection3 are prerequisites. Native loc_326B6 installs pointer
$000326B6; loc_13F2E owns the ordinary CPU word refresh. No CPU/latch/timer
setter, history manipulation, leader-start fit, loop or private write is added.

Reviewed test SHA256
`db5a94d9db416653b0479e85e9f9b21e4b7e9a5f4dbd77bbc0a6bab67669e9c5`;
inlet-only diff against0d285 SHA256
`5d89c226b0c08438f84d2e2c479de1ef4ac60b8a910b3a75b56858ac316728c1`.
Removing that exact helper/separator and reversing the two calls recovers0d285
byte-exactly. Old two cases, grounded-positive case, bubble producer, release/
ordinary-frame/prerequisite helpers, timer expectations and forced-world loops
remain unchanged. Runtime remains Java7df/column d8119e4a.

R38 execution ordinal42 uses ONE unchanged five-method normal Main-absolute
wrapper lifecycle on HEAD17480/testdb5a, the exact R33 command above: no profile,
all original absolute ROM paths, Java25/EGL JTO plus CLI/nullAL/no ambient
DISPLAY/WAYLAND or LD path/Lua5.4/fork1/reuse/alphabetical. Duplicate/source/
ROM-SHA1/config/native/ref/output preflight passes. Sole session42497 is admitted
with reportedqueue0, compiler inside the same request, terminal1 at21:30:01UTC,
Maven01:20, suite1.067s: **5/1F0E0S**. All five identities, full nested failure,
streams, skips and72 properties are inspected; there is no R35 instrumentation
or diagnostic property, setup/warm-slot/terrain/CPU/recreation prerequisite error.

| Actual case | Result |
| --- | --- |
| Existing cached release/standing/recreation | PASS |
| Existing visible hold/jump | PASS |
| Genuine-grounded positive lock | PASS, original two forced-world replay cycles and K-1 |
| Released natural zero | PASS, both two-cycle held/released world-recreation paths and final0 |
| Released positive lock | genuine final timer FAIL: nextFrame.moveLock expected35, actual34 |

The released-positive helper RETURNS after BOTH twice-forced pre-release-world
recreation/forward and twice-forced released-world recreation/forward loops.
Every real object recreation/relink, whole-world snapshot equality, forward
state comparison and original post-frame CPU/held prerequisite precedes the
final native timer oracle. The original assertAll139 has only one failure:
MultipleFailuresError wrapping the suppressed AssertionFailedError at141,
nextFrame.moveLock expected35/actual34. The other final assertion140 verifies
releasedLock35 successfully. The actual bubble and visible CPU inlet are valid
in this component run. This is a **genuine independent component old-red**;
no writer instrumentation identifies the precise Air/decrement caller here.
Campaign8774's actual Air caller and native001D's earlier producer remain
unobserved/separate. No campaign/native/route equivalence or parity claim.

Root directly accepts original XML27074B SHA256
`140bf82cf2448da97e09ddb2fc0898802d442ae83a9f12c6933a40e3cfd35880`
and TXT2881B SHA256
`4144409a05f3839e9bbb88e085f8cbf0bc7100f9bcbbe3c7487028db4187ab28`,
all five cases/full nested failure and all four released forced-world cycles.
R39 may consume only these29955B after this durable account, metadata_slot
alone, no copy/archive/receipt. The exact R38 taskId/ordinal42 are retained;
its full native runId was not relayed and is not inferred.

R39 is source implementation only: native loc_326B6 calls SolidObjectFull;
the latter's cached-offscreen P2 return precedes SolidObjectFull_1P's airborne
standing-bit cleanup at loc_1DC98. Column release loc_328AC sets Air and leaves
OnObj; skipped standing cleanup cannot make that stale ride a pre-movement
floor. Existing SolidObjectProvider capability defaults false, so the column
currently cannot use ObjectSolidContactController's existing offscreen stale-air
grounding exclusion. Only this column opts into the existing
`airborneStaleStandingBitReturnsNoContact(PlayableEntity)` capability with true.
No new method contract, state, shared algorithm, timer/CPU/movement/visibility
gate or API is added; the existing release/recapture/pose implementation and
testdb5a remain unchanged. This source candidate is frozen for Root read before
any compiler/JVM; no green result, runtime/failing-test commit or new execution.

After the durable facts above, accepted original XML+TXT29955B are rehashed
and consumed at21:34:55UTC under metadata_slot alone; no copy/archive/receipt.
Frozen source-only column SHA256
`a1704354640d93659bbda7ce4ff01ab5985d0cbff3810dd903b652ac58f91a49`,
exact one-file git diff SHA256
`523f13d6ed66cea82459d0d6494dc61099d3dc8d3678c05f6f835a8d9da065de`.
Removing only the override recovers d811 byte-exactly. Testdb5a and all other
Java7df runtime files, including APS/movement/shared solid, are unchanged.
No compiler/JVM or test/owner/source variant has run on this candidate.


## R40 sole affected nine-class focus closes the component old-red — 2026-10-17

Root approves the exact R39 nine-line local capability diff a1704354/diff523f13d6
and unchanged testdb5a, including all existing shared consumer sites. R40 uses
ONE normal Main-absolute wrapper lifecycle with the exact historical62518
nine-class selector above, retaining the single HCZ rider recreation method
`TestS3kCnzHczRiderRewindMaps#hczSpinningColumnRestoresRiderArrayAndPlayerReference`.
The registry62518 actual nine-class list and its existing exact audit selector
are cross-checked; the mixed rider class is not expanded. No profile,
original three ROM paths/identities, Java25/EGL JTO plus CLI/nullAL/no ambient
DISPLAY/WAYLAND or LD path/Lua5.4/fork1/reuse/alphabetical, compiler in this one
request. Duplicate/output/source/config/native/ref preflight passes.

Exact execution ordinal44/session48240 is admitted with reportedqueue0 and
finishes terminal0 at21:42:12UTC, Maven01:17: **76/0F0E0S**. All nine actual XML
class identities, all76 method/parameterized identities, skips, assertions and
complete captured streams are inspected. Source/environment properties match;
no diagnostic, observer, prerequisite or novel failure. Output consists of the
FAST missing-manifest/NONE warnings and the decoding bounds-control warning.

| Actual XML class | Tests | F/E/S |
| --- | ---: | --- |
| game.sonic3k.objects.TestHczSpinningColumnCachedRelease | 5 | 0/0/0 |
| tests.TestS3kHczSpinningColumn | 8 | 0/0/0 |
| game.sonic3k.objects.TestS3kHczMechanismRewind | 2 | 0/0/0 |
| game.rewind.TestS3kCnzHczRiderRewindMaps, exact HCZ method only | 1 | 0/0/0 |
| tests.TestS3kAiz1SkipHeadless | 8 | 0/0/0 |
| game.sonic3k.TestSonic3kLevelLoading | 36 | 0/0/0 |
| tests.TestSonic3kLevelLoading | 7 | 0/0/0 |
| game.sonic3k.TestSonic3kBootstrapResolver | 6 | 0/0/0 |
| game.sonic3k.TestSonic3kDecodingUtils | 3 | 0/0/0 |

All class names above carry the com.openggf prefix. The unchanged five-method
component now passes the previously genuine final released-positive timer
oracle, both twice-forced held-world and twice-forced released-world recreation/
forward paths, grounded K-1, natural zero and original release/hold/jump controls.
The existing mechanism/rider recreation and mandatory S3K four obligations pass
within this exact selection, including both loading-class identities. This is
bounded affected validation, not a general green/campaign/trace certificate.
No original prefix/full canonical/native/extra guard or additional JVM is run;
actual campaign8774 Air caller/native001D earlier producer remain separate.

Post-terminal literal source checks confirm column a1704354/diff523f13d6,
unchanged testdb5a, APS838c9252, movement3f324b74, CPU4bb9f42f, shared solid06245eea
and provider31bedb35. Exactly the column differs from Java7df; all nine selected
test sources are unchanged from their frozen fingerprints. Own/Main private
regular0600 configs retain exact bytes/mtime, native4fdf/all refs/index/HEAD17480
and foreign work are preserved. No source repair, retry, variant or commit.
Compact exact class/method results and original report ownership hashes live in
the existing own R40 registry entry; standard18 XML/TXT reports231910B are the
normal cleanup scope after this durable account. No novel failure needs retention.

After inspection and the durable compact results above, exact18 standard
XML/TXT reports231910B are rehashed/consumed at21:44:17UTC under metadata_slot
alone; no copy/archive/receipt or novel failure retained. Ordinal44 and all
prior IDs/results remain separate; no live job or authorized further JVM.


## R41 original canonical advances to11920; strict route remains red — 2026-10-17

After Root accepts R40's actual76/0F0E0S, R41 ordinal45 runs exactly ONE original
maintained `TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`
with `-Ptrace-replay`, normal Main-absolute queue, original AD40/fromboot/default
bootstrap/lag/all three absolute ROMs, Java25/single EGL JTO plus CLI/nullAL/no
ambient DISPLAY/WAYLAND or LD path/Lua5.4/fork1/reuse/alphabetical. The selection
is the exact R22/76850 maintained method/profile above; R41's explicit CLI EGL
flag accompanies the same required JTO backend. Compiler stays in this one
lifecycle. No temporary prefix, observer, logger, reflection, base/fixture/input
edit, gameplay hydration or driver variant. Tested HEAD17480 plus local column
**a1704354**, unchanged bounded testdb5a, maintainedeb743765/base967c9896;
only the column differs from Java7df. Original movie AD40 and tracked route
fixtures, private configs/native4fdf/all refs and source pins remain exact.

Sole session62139 is admitted with reportedqueue0 and finishes terminal1 at
**21:47:19UTC**, Maven51.362s, method26.227s/suite26.472s, **1/1F0E0S**. All
available JSON arrays, original JUnit method/full failure/suppressed exceptions,
output and skips are inspected. There are two failure axes, not a green route:

| Current segment | Full reference / compared | Complete | Total = physics + animation | Lag | Warn/bootstrap |
| --- | --- | --- | --- | ---: | --- |
| 0 aiz | 2290 /2290 | true | 0=0+0 | 0 | 0/0 |
| 2 aiz_2 | 4023 /4023 | true | 0=0+0 | 11 | 0/0 |
| 4 aiz_3 | 8235 /8235 | true | 0=0+0 | 14 | 0/0 |
| 6 aiz_4 | 4103 /4103 | true | 0=0+0 | 16 | 0/0 |
| 8 aiz_5 | 7175 /7175 | true | 0=0+0 | 62 | 0/0 |
| 9 hcz | 3574 /3574 | true | 0=0+0 | 55 | 0/0 |
| 11 hcz_2 | 14208 /14208 | true | **6553=5489+1064** | 59 | 0/0 |
| 13 hcz_3 | 5393 /not emitted | false | 63295=54642+8653 | 6 | 0/0 |

The completed14208-row returned-HCZ firstNonCameraPhysicsMismatch is now
**11920 sidekick_status_byte native0002/engine0003**. The earlier all-group first
logged error remains3475 sidekick_animation_id native17/engine1C. R22's same
full window had11118=10054physics+1064animation and first8775statusB/A. The JUnit
label calls all6553 errors physics; JSON groups above are authoritative. Each
summary exposes only its recent-five mismatch buffer: lower totals and the
unchanged animation count **do not prove full-vector regression absence**.
No exhaustive error array is available to claim otherwise.

The six earlier complete zero-summary payloads are byte-identical to R22.
Segment13's complete available summary payload is also byte-identical to R22
(SHA8d3f4010...), including first287 y native06D4/engine06DA and final retained
row3208. It remains incomplete; row3208 is not substituted for an unreported
compared-row cursor. Actual ownership fails at BK2 cursor88306, modeLEVEL,
loadGeneration10/progressionZone1/romZone1/act1 while segment13 expects act2.
The same four suppressed hardware identities remain: raw3256 PRE_MAIN_LOOP
KosDecompression244;3257 POST_OBJECTS KosModule171;5356 PRE_MAIN_LOOP
KosDecompression245;5357 POST_OBJECTS KosModule172. Their full original expected/
actual descriptors stay in the retained JUnit, with no fabricated readiness.

The maintained semantic boundary assertions do observe entry into ss_6 and
return into hcz_3. Current dynamic-art gap report has13 gaps/0 failures; its full
gap/failure arrays are inspected. Producer-labelled movieLogical journal stamps
are hcz_2->ss_6 sourceclose/arm77283/77283, and ss_6->hcz_3 sourceclose83626/
arm85096. Manifest expected boundary cursors77284/85097 remain separately named;
actual observedBk2Frame is not persisted in the retained report, so those
journal rows are not renamed actual callback cursor values or offset-fitted.
No segment beyond13 is qualified. This measurement establishes a new reported
non-camera frontier, not the actual campaign8774 Air caller/native001D producer.

Original changed seg11 JSON SHA256
`2a766df88cbcb46af1194ce06bf5891d202209b5743505c4b4d083265055892b`,
JUnit XML117150B
`78cec1bc9fe00557ab22c9c4dd248769b5cadef67c53ce1bc82c8fdea169daaf`,
TXT4810B `cb6cc93de9676b00d16485c4525f6544f089f2af16537e674a06025070c99cfd`.
All20 current originals133157B (eight segment JSONs/owners, gap JSON/owner,
JUnit XML/TXT) remain at original target paths for explicit Root DIRECT read.
No archive/copy/cleanup, retry, new probe/owner fix, extra JVM or commit. Exact
R41 IDs/results/24h expiry stay separate; queued R42 identity46 is not started
or folded into this measurement. Runtime/test/docs WIP and foreign work remain.


## R42 preservation boundary before11920 source plan — 2026-10-17

Root directly accepts all9 current JSON reports/all9 owner sidecars and original
JUnit XML/TXT, including the entire available arrays/full nested ownership and
hardware failures. Comparator warningCount0 is **not zero runtime warnings**:
R41's actual output separately contains468 SMPS presentation-shadow warnings,
2 FAST-load missing-manifest/NONE warnings and1 markRemembered warning for
(4584,896), id1. None is omitted or relabelled comparator parity output.

The qualified local change is the one nine-line column capability a170 and the
green unchanged db5a independent component: genuine35/34 old-red after all4
released forced-world cycles -> exact affected76/0F0E0S. The one maintained
fullcase measures non-camera8775->11920 on complete14208 with6553=5489+1064;
recent5/lower11118->6553 are not a full-vector/no-regression waiver. Actual
campaign8774 Aircaller/native001D producer and independent seg13 ownership88306
remain outside that proof. Release prose folds into the existing spinning-column
entry; S3K discrepancy/matrix/frontier and the existing mirrored standing-latch
pitfall retain these limits and the real visible-CPU-inlet setup lesson.

R42 ordinal46 first preserves exactly the column/test and owned existing docs
with normal local policy commit; no failing test/runtime variant or additional
validation is introduced. After this durable account and Root DIRECTread, only
the20 hash-inventoried originals133157B may be consumed metadata_slot alone,
without touching compiled maintained-candidate classes, copying/archive/receipt
or unknown files. Source read/plan for11920 starts only after that commit and
clean-accounting boundary; no new JVM, test/logger/runtime/native probe follows.


## R43/R44 independent flight-timeout full-status old-red — 2026-10-17

On d71264ed8 plus only reviewed flight-test77924700, R43ordinal47 appends
two independent S2/S3K cases; all18 old methods/helpers/inlets are byte-exact
after removing the additions/imports (original8883f72d). Native pinned044
`Tails_FlySwim_Unknown` (sonic3k.asm:26574–26588) and pinned34fc
`TailsCPU_Flying` (s2.asm:39142–39155) each reset the entire status byte to02
at the offscreen300-word threshold. S3K alone additionally reloads flight fuel;
the S2 test does not assert a nonexistent fuel write. This source-backed oracle
is independent of the route's final11920 facing bit.

R44ordinal48 runs exactly one normal Main-absolute-wrapper lifecycle:
`-Dmse=off -Dtest=TestSidekickCpuControllerFlightAutoRecovery` (no profile),
with the original three absolute ROM bindings, Java25, one EGL JAVA_TOOL_OPTIONS
plus CLI flag, nullAL/no ambient display or LD_LIBRARY_PATH, Lua5.4, fork1/reuse/
alphabetical. Session75570 ends **22:08:39UTC**, queue0/Maven24.488s, suite0.336s:
**20 tests/2F0E0S**. Compiler is inside that request; runtime classes were
up-to-date, test compilation completes, no separate build/retry/variant.

Both new cases reach the final assertAll after natural299-update LEFT steering,
FLIGHT/timer299/valid-cached-offscreen prerequisites and two in-place local
leader/sprite/controller restore-forward cycles each. All captured post-timeout
local-partition equality checks pass. The ONLY failures in each case are final
modeled status expected02/actual03 (line655) and RIGHT/LEFT (line656). The other
final footprint assertions pass: CATCH_UP/ROMroutine02/flightTimer0/integerXY0/
unchanged1234,5678 fractions/control81/animation20; S3K fuelF0 passes before
assertAll. All18 unchanged controls pass. Full20 XML identities/properties and
both complete MultipleFailuresError/suppressed lists were inspected, 0 skips.
Actual runtime output has24 FAST-load/no-manifest warnings, separate from the
test failures; no setup/restore/CPU/prerequisite error occurred.

Declared component inlet is existing TestableSprite/static synthetic leader
history and public setInitialState(FLIGHT_AUTO_RECOVERY), starting actual timer0.
No private299 seed, native pose/data input, CPU replacement, runtime hook or
gameplay repair. The two restore cycles cover only the captured local partition,
not world recreation or managed production entry. Actual11920 engine caller/
consumed cache/timer remains unobserved; seg13 ownership88306 and native29/8774
questions stay separate. This is a genuine independent old-red, not route parity
or authorization to change the shared controller.

Original XML39496B SHA0e07f1c15927f725f9761e991808aca7ac6a5e873e6d7653198a5b1da0452126
and TXT8888B SHA994982ab296a0d18d9752d3d7c953165a0658e1a5dc9562114dc9c7156009773
remain singly at the original target/surefire-reports paths for Root DIRECTread.
No copy/archive/cleanup/commit. Post-run test779247/CPU4bb9/columna170/db5a/
APS838c/movement3f324/shared sources/native4fdf/config SHA+mtime stay exact.
Stop: no runtime fix or further JVM/probe is authorized in this phase.


## R45 timeout literal-status candidate, source only — 2026-10-17

Root DIRECT accepts R44's entire20case XML/full failures/properties and original
XML0e07f1c1/TXT994982ab. At22:16:38UTC, only those2originals48384B are rehashed
and consumed under metadata_slot alone after durable R44 facts; CPU/test class
SHA+mtime are unchanged. No archive, copy, receipt or unknown deletion. Prior
R44 IDs/results remain separate from R45ordinal49.

Candidate CPU8930b6b4 adds exactly6 status operations plus3 native-citation lines
inside updateFlightAutoRecovery's existing offscreen300 timeout, immediately
before the unchanged setAir(true). Existing marker-owner operations clear roll
without radii adjustment, underwater status without restoring water physics,
rollingJump/OnObj/push, and facing. setPushing(false) also clears derived push
bookkeeping; this is not a zero-side-effect claim. setOnObject(false) writes
only the status projection, with no ride/latchedID/support-cache reset. The
other timeout pose/fraction/control/timer/fuel/animation/state ordering and the
on-screen OR-air/ordinary steering/handoff paths remain byte-exact. Removing
those9 added lines recovers entire CPU4bb9f42f exactly. Native citations remain
pinned044 sonic3k.asm26574–26588 and pinned34fc s2.asm39142–39155.

Current TestArchitecturalSourceGuard has no explicit CPU class or
updateFlightAutoRecovery method size budget; existing class/method ratchets and
source guards are unchanged. No budget/allowance edit, counting rearrangement
or extraction. APS838c/movement3f324/solid0624/provider31be/columna170/db5a and
privateconfig SHA+mtime/native4fdf remain fixed.

The genuine20case test779247 remains **byte-exact**. A separate source-only
TestSidekickCpuControllerFlightTimeoutStatusInputs (4fcd746b) adds S2/S3K declared
public-status input cases: actual299 updates from public routine4/timer0 with
static synthetic component history, predeadline status7F/LEFT/valid-offscreen,
then timeout status02 with7/14 radii, dimensions and underwater acceleration
preserved. OnObj here is explicitly a declared status bit, not a managed ride.
These two cases are **uncompiled/unexecuted**; do not transfer R44's observed
bit0 old-red or passing local replay controls to the new status/radii oracles.
No new runtime API/state/schema/provider or trace/native data input exists.

Proposed focused gate: unchanged20case FlightAutoRecovery, new2case
FlightTimeoutStatusInputs, existing CatchUpFlight, DespawnParity and
RewindCapture classes; required S3K4 including both existing loading-class
identities; affected runtime-access/trace-authority/rewind guards remain a
separate parent-selected obligation. S2/S3K source status branches agree, with
S3K fuel proof confined to its own oracle. No compiler/JVM/queue/native/route
probe/logger/commit occurs in R45. Actual11920 engine caller/consumed branch
remains unobserved; Root owns source review and the separate focused gate.


## R46 native timeout status focused qualification — 2026-10-17

After Root's actual-file approval, one normal no-profile Main-absolute-wrapper
request96888 on d712+CPU8930/unchanged20case779247/new2case4fcd ends
**22:22:52UTC**, queue0, Maven reports01:20min (rounded). Runtime4143/test4193
sources compile within the same request; no standalone build/extra JVM. Original
three absolute ROMs, Java25.0.4.1, one EGL JTO+CLI flag, nullAL/no ambient display
or LD_LIBRARY_PATH, Lua5.4/fork1/reuse/alphabetical are unchanged.

Actual **156/0F0E0S in10classes**, all XML testcase identities, full streams,
properties, failure/error/skipped children and original TXT inspected:

| Actual class | Cases | F/E/S |
| --- | ---: | --- |
| TestSidekickCpuControllerFlightAutoRecovery |20|0/0/0|
| TestSidekickCpuControllerFlightTimeoutStatusInputs |2|0/0/0|
| TestSidekickCpuControllerCatchUpFlight |7|0/0/0|
| TestSidekickCpuDespawnParity |65|0/0/0|
| TestSidekickCpuControllerRewindCapture |2|0/0/0|
| TestS3kAiz1SkipHeadless |8|0/0/0|
| com.openggf.game.sonic3k.TestSonic3kLevelLoading |36|0/0/0|
| com.openggf.tests.TestSonic3kLevelLoading |7|0/0/0|
| TestSonic3kBootstrapResolver |6|0/0/0|
| TestSonic3kDecodingUtils |3|0/0/0|

The **unchanged** natural-left S2/S3K R44 old-red now passes, including both
local restore-forward cycles per case and all literal-status/pose/fraction/
control/timer/fuel/animation oracles. Separately, the two previously unexecuted
declared7F-input cases now pass literal02 and retained radii/dimensions/water
acceleration. These are component status-input controls, not managed ride or
actual11920 engine branch observations. Old ordinary steering/on-screen/handoff
controls, shared S2/S3K catch-up/despawn/rewind consumers and mandatory S3K4
(including both loading class identities) pass in the same exact selection.

Actual XML runtime streams contain154 FAST/no-manifest warnings,1 rejected
HURT/-1 fallback WAV warning and1 collision-index out-of-bounds diagnostic
(chunkIndex3/offset6/tableLength6). These do not become a zero-warning claim.
No unexpected failure/prerequisite/skip occurred. Post-run all source pins,
privateconfig SHA+mtime, native4fdf and prior refs remain fixed. The20 inspected
standard XML/TXT287086B are eligible for normal hashchecked metadata-slot-alone
consumption after this durable account; preserve compiled candidate classes
and unknown files. No commit/guards/original fullcase/native/extra JVM is
authorized in R46. Canonical11920/source-caller and separate seg13 ownership
88306 remain unqualified until a separately authorized original route run.


## R47 original canonical firstNonCamera13322, strict red — 2026-10-17

R47ordinal51 runs exactly one original maintained AD40/fromboot fullcase after
R46focused156green. Session35289 uses the unchanged eb743 fullcase/base967c,
trace-replay profile, normal Main-absolute wrapper, original3ROM/Java25.0.4.1/
one EGL JTO+CLI/nullAL/no ambient display or LD_LIBRARY_PATH/Lua5.4/fork1/reuse/
alphabetical. d712+CPU8930/20case779247/new2case4fcd/columna170/db5a stays frozen.
No observer/output property/fixture/testbase/input change or standalone build.
Terminal **22:27:01UTC**, queue0/Maven51.355s, method26.391/suite26.660s,
**1/1F0E0S**. Runtime is up-to-date; test compilation is within this one request.

All9JSON+9owner sidecars/fullJUnit XML+TXT and all available arrays/properties/
skips/suppressed failures were inspected. Actual complete comparison windows:

| Segment | Reference / complete compared rows | Total | Physics | Animation | Lag |
| --- | ---: | ---: | ---: | ---: | ---: |
|0 aiz|2290|0|0|0|0|
|2 aiz_2|4023|0|0|0|11|
|4 aiz_3|8235|0|0|0|14|
|6 aiz_4|4103|0|0|0|16|
|8 aiz_5|7175|0|0|0|62|
|9 hcz|3574|0|0|0|55|
|11 hcz_2|14208|6511|5447|1064|59|

All these summaries have warningCount0/bootstrapErrorCount0. Counts are verified
against actual original CSV row counts; complete means reference exhaustion in
LiveTraceComparator.checkComplete, not an inferred movie offset. JSON does not
independently emit a numeric compared cursor. Segment11 firstNonCamera is
**13322 sidekick_status_byte native0009/engine0001**, delta8. First logged
all-group mismatch is still3475 sidekick_animation_id native0017/engine001C.
Seg11 summary SHA bac108c43163f1dd841fc51b355492de5187eaec4c05342e417d0dc2cb29ba77.
R41's first11920/6553=5489+1064 becomes13322/6511=5447+1064 in this measurement;
only a bounded recent5 buffer exists, so lower totals/different first fields
are NOT exhaustive-vector equality/no-regression proof or a green prefix.
Component R44oldred/R46green remain separate from actual11920 caller/consumed
cache/timer observation, which is still absent. No new cause is assigned to13322.

Segment13 remains **incomplete**, reference5393, actual compared count not
emitted; its retained latest error row3208 is not substituted for that count.
Summary63295=54642physics+8653animation, first287 y06D4/06DA, lag6/warnbootstrap0,
SHA8d3f40103186273799f2d85b6d6bf20aa0dd271442e83330f2c52506d85f96f5. This entire
available summary payload, six earlier zero payloads and gap payload are byte
identical to R41; that does not extend to unretained full mismatch vectors.

The maintained giant_ring entry assertion into SS6 and return-boundary
admission into hcz_3 pass before segment13 loses production ownership at
**BK2cursor88306, modeLEVEL/loadGeneration10/progressionZone1/romZone1/act1**,
while segment13 expects act2. No segment beyond13 is qualified. Actual gap
journal13gaps/0failures has hcz_2->ss_6 sourceclose/arm movieLogical77283/77283
and ss_6->hcz_3 sourceclose83626/arm85096. Declared manifest event frames77284/
85097 are separately named; exact observedBk2Frame is not serialized here,
and no fitted offset is introduced. All gap/transition arrays are inspected.

Full suppressed failures retain all4 identities and fingerprints: raw3256/
PRE_MAIN_LOOP/KOS_DECOMPRESSION244 (different descriptor),3257/POST_OBJECTS/
KOS_MODULE171 (different descriptor),5356/PRE_MAIN_LOOP/KOS_DECOMPRESSION245
(no unclaimed submission),5357/POST_OBJECTS/KOS_MODULE172 (different descriptor).
No readiness/descriptor/physics repair is attempted. Actual runtime streams
contain468 SMPS presentation-shadow warnings,2 FAST/no-manifest warnings and
1 markRemembered(4584,896),id1 warning, separate from comparator warningCount0.

All original20files remain singly at target/trace-reports/run-chain and
target/surefire-reports for Root DIRECTread; no copy/archive/cleanup/commit.
Post-run all source/movie/config SHA+mtime/native4fdf/refs remain exact. No
additional selector/JVM/native/logger/guard/repair is authorized. Root's known
LevelManager3153/3144 extraction/fresh composed guards remain parent-owned and
are not implied green by this run. Stop at13322/independent ownership88306.


## R48 preservation before13322 source inventory — 2026-10-17

Root DIRECT accepts all R47 20originals133156B/full available arrays/full2axis
XML and four full hardware fingerprints. Preserve exact tested CPU8930,
genuine20case779247 and declared2case4fcd with existing engineering evidence,
current-POM CHANGELOG.0.7.prerelease.md fold and mirrored literal-status lesson.
Historical CHANGELOG.0.7.md is untouched; Root separately owns its earlier
Air-lock release sentence. R44old-red/R46focused156green/local restore controls
and R47strict1F/first13322/ownership88306/warnings/limited vectors remain explicit.

R48ordinal52 uses a normal local policy commit with seven trailers and parsed
final GPT6.1 coauthor, then hash-consumes only accepted20 originals under
metadata_slot alone, preserving all candidate classes. Tested source epoch is
d712 plus exact CPU8930/779247/4fcd; a later documentation commit SHA is not a
new execution epoch. No child fullguard is claimed: known LevelManager3153/
3144 remains pending parent composition with separately tested48650119.
Only after commit/clean accounting may existing native13319–13325 rows/source
be read. No new test/logger/runtime/observer/native/JVM/probe/route/repair.


## R32–R34 post-launch matched pool account (2026-10-17)

Exact803 versus committed781c, same98d/original Sonic fixed driver and identical
reviewed a690/f8ae observer.56945 and70631 each1/0F0E0S PREFIX through48276 only,
18603verifiedsamples/6restoremarkers; ROOT DIRECT accepted all2642 strict JSON lines,
159typed objects and four XML/TXT reports. BASE1323records/78typed versus CURRENT
1319/81; common explicit keys2613 with equal clocks/load. Four absent803 launch
fields stay separate from common phase comparison, and Java refs stay run-local.

First common phase43944:264priorrendertrue/false. Structural splits43945→43956,
44245→44254,47250→47262 close. First nonclosing48195 now removes current630slot7;
629+631 retire48196. Both epochs show same48149/65/81 releaser624slot5 counterdrops
and child IDs629/630/631. Current native launch mode is actually active: FLY/FLY/WALK,
frame1,X-512,Y-279/-256/-281 versus BASE MAIN/frame2/0/-1024. No observed callback or
unique parent link follows. At48201 BASEanimals occupy6/7/8 while current slots are
free; layouts198/201/204 IDs632/633/634 take9/30/32 versus6/7/8. Spring20648213 takes
6versus9. Known camera Y still first differs48275; no first arbitrary motion claim.
This accepted launch unit has not closed the persistent pool/contact-order question
or original full campaign death49613. Keep correct R5 and native initializer.

R34 preserves tested helper f8ae functional body in place with header-only provenance
and existing workflow entry; restores original Cold9616 and removes the four exact
owned compiled outer/DeathEggCampaign/helper/Picture artifacts after hash inventory.
No maintained uninstrumented rebuild follows. Accepted6originals10850465B are disposed
only after the local commit under metadata_slot ALONE/fullhashchecks, no copies/
archive/receipt. [Exact commands, counts, identities, original hashes and disposition](../../plans/2026-10-13-s3k-playable-completion.md#r30r34-matched-typed-pool-evidence-and-preservation--2026-10-17).
Native scene/P1 truth, callback/retirement instruction and original route tail remain
unqualified. Next step is source-read native-evidence inventory/plan only, no run.
