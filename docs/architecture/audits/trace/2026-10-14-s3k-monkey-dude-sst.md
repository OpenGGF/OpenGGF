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
