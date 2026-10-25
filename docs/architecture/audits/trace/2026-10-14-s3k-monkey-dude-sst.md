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

`BreakObjectToPieces2` (sonic3k.asm:45826–45862) initializes piece0 with a1=a0,
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
(sonic3k.asm:104773–104821) calls Load_Level, which reads the layout header rather
than clearing Object_RAM. Offset_SomeObjectsDuringTransition(:104200–104228)
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

AIZMinibossCutscene_End(:136961–136966) clears Boss_flag, restores level music,
loads the actual PLC_Monitors and jumps Go_Delete_Sprite_2(:179237–179240), which
changes code and sets38bit4. Its child Draw tails(:178178–178200) observe that bit,
mark their own code and return without drawing. Physical clear is next dispatch.
The cutscene, body, arm, barrels and debris capture this marker state; the boss's
original camera-reset branch was not native and is removed. Fightable91's
separate ordinary lifetime is retained.

ALL_LIVE_SST exposed the transition floor's previous omission. Its native Act2
branch(:104823–104836) writes X7FFF/Delete_Current_Sprite, then still runs
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
(sonic3k.asm:138174-138179). The test now hits in Hover and asserts the CheckHit
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

## Returned HCZ landing and death-reload attribution — 2026-10-25

Bounded investigation in `bugfix/ai-hcz-continuity-investigation-20261025`,
owned checkout `t3-43f29579`, starting clean at
`d3fa3d519f6acb7285d538b5f972807036f1019e`, exactly the parent's pinned base.
Hooks were installed. Runtime, fixtures, comparisons and timing authority were
never edited. Two bounded Sol helpers independently read history/native source;
neither ran tests or changed files. Historical `53b4d0363` / delivered
`94c75d8a8` counts were not treated as current verification.

### Current baseline and commands

Physical Surefire XML confirms Java 25.0.5.0.2, CLI surfaceless EGL, original
absolute ROM paths and this checkout's working directory. The original main
checkout's `{s1,s2,s3k}.gen` bytes match all CRC32/SHA-1 identities in `AGENTS.md`;
no ROM copy/link was made. `ROM_ROOT` below resolves that original checkout.
Exact commands:

```bash
ROM_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
q() {
  env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
    LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null \
    python3 tools/testing/maven_queue.py -Dmse=off \
    -Dopenggf.headless.gl=surfaceless-egl \
    "-Dsonic1.rom.path=$ROM_ROOT/s1.gen" \
    "-Dsonic2.rom.path=$ROM_ROOT/s2.gen" \
    "-Ds3k.rom.path=$ROM_ROOT/s3k.gen" "$@"
}
q -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical '-Dtest=TestS3kAizTraceReplay#replayMatchesTrace,TestS3kSonicTailsHczSegmentTraceReplay#replayMatchesTrace,TestS3kSonicTailsCompleteEmeraldRunPrefix,TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds' test -B
# Temporary diagnostic cases described below; removed after investigation.
q -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical '-Dtest=TestHCZHandLauncherObjectInstance#risingHandResolvesTopSolidAfterWritingItsNewY,TestHczContinuityInvestigation#observeCurrentContinuity' test -B
```

The clean baseline completed at **19:24:38 UTC: six physical cases, one chain
assertion failure, zero errors/skips**, exit 1. All three prefixes and standalone
AIZ/HCZ passed. Chain segments 0/2/4/6/8/9 completed with zero physics/animation/
bootstrap errors or warnings. Segments 8/9 represent 7,175/3,574 rows, including
62/55 lag rows. Standalone AIZ/HCZ compared 20,463/3,519 frames; their advertised
missing auxiliary schemas still limit coverage. Segment 11 is incomplete:
**81,981 differences (69,334 physics / 12,647 animation), first non-camera
physics row 1768 `y` `$0870/$0871`, last mismatching row 5725**, 50 lag rows,
zero bootstrap errors/warnings. The assertion remains lost production ownership
at input 68801, HCZ1/load generation 9. Suppressed unmatched hardware completions
remain incomplete-run diagnostics. Counts are not independent defects or a
completed 14,208-row comparison.

Baseline queue waiting was 56 seconds; Maven execution, including compilation,
was 2:59; submission-to-finish about 236 seconds. The first diagnostic attempt
ran no tests: temporary `getOnObject()` and package-private `getYOffset()` access
failed test compilation at 19:27:10 UTC (34 seconds queue, 24.371 seconds Maven).
After correcting only those accessors, the second diagnostic completed at
**19:28:52 UTC: two cases / two expected assertion failures / zero errors/skips**,
exit 1 (7 seconds queue, 52.561 seconds Maven, about 60 seconds submission-to-finish).
Queue waiting is separate from execution; parallel helper time is not added to
elapsed time.

### First causal owner: the rising hand omits SolidObjectTop

History was inspected before writing the diagnostic. Foreign commit
`5096be6cc1b7b8bbc9bcc9bf0646803ab7e9f5b5` repairs this exact owner and is not
an ancestor of `d3fa`. Its full runtime diff was reviewed, not applied: an
after-Y solid pass, retained per-player standing bits for the next grab/release
checks, and capture/restore/binding handling for those bits. Its parent-to-current
launcher delta adds native-facing presentation helpers and citation remaps;
preserve these in any adaptation. Broader foreign composition
`b6c4898e2790938ce76ec14ef7e2e8c22c6e9530` spans 36 files and is not a bounded
launcher transfer. No foreign branch was integrated.

The committed native `hcz_2` physics/aux and passive current-engine observer
establish these ordered states:

| Row | Native | Current engine |
| --- | --- | --- |
| 1767 | P1 `$242C,$0871`, status `$44`, no standing; hand slot 36 at `$0898` | Matching P1 position/fractions/velocities; hand `$0898`, offset `$20`, no standing/riding |
| 1768 | Hand rises to `$0890`; P1 `$2435,$0870`, status `$4C`, interact 36, control zero | Hand `$0890`, offset `$18`; P1 `$2435,$0871`, Y fraction `$8300`, no standing/riding or object control |
| 1769 | Continued ride gives P1 Y `$0871` | Fresh landing runs one row late: P1 Y `$0870`, riding the hand |
| 1770 | P1 X `$2447` leaves `[2400,2440)`; OnObj clears | Hand moves to `$0898` but P1 stays Y `$0870`, standing/riding; the stale ride persists through observed row 1774 |

`HCZHandLauncher_UpdateSolid` (sonic3k.asm:65864-65872) writes the new Y before
`SolidObjectTop`, with `d1=$20`, `d3=$11`.
`loc_1E45A` (sonic3k.asm:42044-42063) computes the rolling landing from surface
`$0890-$11=$087F` and feet-plus-four `$0871+$0E+4=$0883`: `d0=-4`, then
`$0871-4+3=$0870`, preserving Y fraction `$8300`. The previous `$0898` surface
gives positive overlap and rejects landing.
`MvSonicOnPtfm` (sonic3k.asm:41687-41722) places the continued rider at `$0871`.
Native input and `object_control` are zero: this is ordinary landing, not a
grab, jump edge or movement lock. Interact `$2A` at 1767 is stale; Status_OnObj
is clear, so it does not establish standing.

Current `HCZHandLauncherObjectInstance` selects `MANUAL_CHECKPOINT`, which
receives no automatic tail checkpoint. Rising/returning branches write `currentY`
without resolving a solid. Grab/release checks instead call `checkpointAll()`
before that write. Reusing historical
`risingHandResolvesTopSolidAfterWritingItsNewY` with an empty contact batch fails
on its first rising dispatch: **expected one checkpoint, observed zero**.
The actual chain independently confirms the motion and absent landing. Matching
hand Y and player fractions reject fan phase, surface-height constants and
fractional position as explanations for this first mismatch.

### Actual stop cause and causal limit

The temporary observer subclasses `AbstractRunChainTest` and only reads existing
production state from `afterProductionStep`. It captures comparator cursor and
segment before stepping, and states that trace row plus
`productionOutputRowObserver - 1`; here the physical index is `63075 + traceRow`.
Rows 1758–1774 identify the launcher by spawn `$2420,$0878`, not an assumed
engine slot. Other output is bounded to generation/death/drowning edges and every
256th returned-HCZ row. Successful test-only source fingerprint:
`c75c1c57ddcb5f6eebb7ff85cd548d047b4151a7db9b96ff30011c095a75c7b8`;
first non-executing attempt:
`67dbd7da43e054977d4eb1b1b31f89abe5f4865667e522b41748f6579b45871a`.
Each is SHA-256 of sorted changed/new `src` paths, each followed by NUL and
complete file bytes, relative to `d3fa`. Production/fixtures equal baseline.

- Returned HCZ is admitted on `INTERIOR_RETURN@gen8/z1a0`.
- At **row 4942 / movie 68017**, engine P1 `$283D,$0783` enters drowning
  pre-death: dead false, drowning/pre-death true. Native P1 is alive/routine 2
  at `$3729,$06EC`.
- At **row 5637 / movie 68712**, engine P1 `$26A6,$0822` becomes dead, drowning
  true, pre-death false. Native P1 is alive/routine 2 at `$3716,$06D7`.
- At **row 5726 / movie 68801**, the production receipt is
  **`DEATH_RESTART@gen9/z1a0`**. P1 respawns at `$1580,$066C`, with cleared
  death/drowning state. Native P1 remains alive/routine 2 at `$3716,$06D5`.
  The coordinator correctly rejects the new generation before source closure.

The observer reproduces the baseline's exact 81,981 counts, first field/values,
incomplete status and ownership assertion. This rejects a changed instrumented
route and a false initial-load/coordinator-admission diagnosis. The immediate
stop is **an actual gameplay death reload**. Whether the landing defect alone
causes the later drowning remains unproven; intervening owners were not repaired
or isolated. Historical hand-only `5096be6cc` reports first error 1891 and a
different KosM abort; adding separate Mega Chopper control commit
`1eaa2cc4ac1302f43273e0f11d40cfaba408180e` reports first error 3475 and later
death-restart 70718. Evidence commit
`b8a6024db31c44e0af6972b25c314f90a1845ad1` retains these observations in this
audit's foreign history. These are historical results, not fresh current-source
passes, single-defect causation or grounds to relax timing authority.

### Finite corrective boundary

Restore one launcher solid pass after the final Y write when native permits
it. Grab, escape and release must read retained incoming standing bits rather
than perform an early solid pass. Preserve current native-facing helpers and
per-player binding/rewind semantics. Reuse all four focused controls from
`5096be6cc`, then inspect actual landing/continued ride/edge exit at 1767–1770
and two-way registered restore plus forward replay. No shared solid heights,
tolerances, queues, bootstrap, death admission or inputs need changing for this
owner.

Parent review should qualify the narrow candidate against all three prefixes,
standalone AIZ/HCZ and the affected HCZ slice by exact fields/spans. If the next
current frontier becomes 1891, review Mega Chopper separately; do not bundle
the broader foreign composition. The drowning prerequisites and outgoing HCZ
closure remain open campaign work. No native recapture, alternate replay,
broad smoke, full suite, pixel/audio, other viewport/donor/team or whole-campaign
claim follows. Temporary diagnostics are consumed and test sources removed at
handoff; this investigation retains evidence prose only. Parent owns review,
integration, delivery and cleanup.


## HCZ object phase repairs and remaining continuity gate — 2026-10-25

Implementation checkout `t3-191b48d4`, branch
`bugfix/ai-hcz-continuity-repair-20261025`, starts exactly at clean
`61be1910ac29d5606410be315ec881a5745a75d3`. Only its task branch was renamed;
hooks were installed. The parent owns review, combined smoke, integration, push
and checkout retirement. No shared load, timing, physics or comparison owner,
fixture, tolerance or input movie was changed. Sideways and the parallel AIZ
ring-reset owner remain outside this patch.

The narrow foreign `5096be6cc` launcher and `1eaa2cc4` Chopper diffs were read
against current owners and native source, never cherry-picked wholesale. Current
native-facing helpers remain intact. One read-only Sol helper checked the
Chopper input publication and native controller source; it ran no tests or builds.
Its live-logical-word finding was reproduced and repaired; its separate inherited
raw-controller shake issue remains a qualification limit below.

### Corrected native owners

The hand's manual checkpoint runs once after its final Y write on every Main
pass. LaunchReturn retains the native `$30 <= $18` gate. Grab, jump escape and
launch release consume the standing bits left by the preceding solid pass;
those per-player bits travel with player bindings and rewind state. Native
`HCZHandLauncher_UpdateSolid` (sonic3k.asm:65864-65872),
`HCZHandLauncher_UpdateAfterLaunch` (sonic3k.asm:65903-65914) and
`HCZHandLauncher_TryGrabPlayer` (sonic3k.asm:66006-66008) own these boundaries.
A current continuous replay verifies all of rows 1767–1770: rising handY`0898`
then`0890`; SonicY`0871` → `0870` → `0871`, preserving fraction`8300`; OnObj
sets at1768 and clears at1770 on leaving the right edge. The old late landing
and retained rider are removed without changing solid dimensions.

The next current defect was independent: a Chopper owns P1 in CARRY at row1863,
but engine control lock was false while native `ctrl1_locked` became`FF`.
Down at1891 rolled/released Sonic early, shifting X`24B3` → `24AE`; native
remains unrolled through1892 and releases on the forced roll at1893.
`MegaChopper_CheckCapture` (sonic3k.asm:184524-184530) clears logical input
and acquires the lock only for selector1, not P2 or simultaneous P1+P2.
`MegaChopper_CarryFollowPlayer` (sonic3k.asm:184439-184453) republishes the
masked pad after release checks; `MegaChopper_ReleasePlayer`
(sonic3k.asm:184486-184489) releases only its acquired lock. The engine now
models that ownership, publishes both the forced movement mask and live logical
word, and captures the latch/player state for rewind. It leaves the current
follower-history entry untouched: Sonic_RecordPos preceded this object's write.
A new negative control caught a first adaptation that updated only the forced
mask and left live logical Left at0 instead of4. The completed replay before that
last publication correction already matched capture1863, unrolled1891–1892,
and released1893 at the native positions/fractions/velocities; final evidence is
recorded below rather than inferred from the historical foreign counts.

### Focused commands and physical results

Use the `q` environment and the exact six-case canonical command in
[the preceding investigation](#current-baseline-and-commands). `ROM_ROOT` is
the original main checkout resolved by that command; all three original files independently
match the root guidance's size/CRC32/SHA-1. Physical XML reports Java25.0.5.0.2,
explicit surfaceless EGL, this checkout and those original absolute ROM paths.
Display/Wayland and inherited JVM-option variables are unset; Lua is 5.4.9 and
no-device audio is selected. No ROM copies or links were made.

```bash
q '-Dtest=TestHCZHandLauncherObjectInstance' test -B
q '-Dtest=TestMegaChopperBadnikInstance,TestS3kHczHandLauncherGraphRewind' test -B
q '-Dtest=TestMegaChopperBadnikInstance#mainOnlyCaptureLocksControlsAndRepublishesOnlyLeftRight' test -B
q '-Dtest=TestMegaChopperBadnikInstance,TestHCZHandLauncherObjectInstance,TestS3kHczHandLauncherGraphRewind,TestS3kHcz2TwistingLoopRegression,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test -B
```

| Completed invocation (UTC) | Physical result and qualification | Queue / Maven execution |
| --- | --- | --- |
|21:28:25, unchanged runtime plus four launcher controls|15/4F/0E/0S; all four new phase controls fail, including expected checkpoint1/actual0|0s /79s|
|21:30:46, launcher candidate and anchors|79/0F/1E/0S; launcher15 and anchors pass; new rewind fixture lacked active player timer services|0s /83s|
|21:33:22, hand-only exact canonical six|6/0F/1E/0S; five companions pass; chain first1891 X`24B3/24AE`, incomplete22571 differences (21083physics/1488animation),49lag; KosM FIFO abort at4001|0s /120s|
|21:34:31, Chopper red controls plus rewind|11/5F/0E/0S; four Chopper lock controls fail; new launcher restore test also exposes missing P1 identity in its camera fixture|0s /27.454s|
|21:37:20, two-object candidate plus anchors|87/0F/1E/0S; Chopper8, launcher15, new retained-state rewind and anchors pass; existing no-player graph case hit a null-focus fixture error|0s /87s|
|21:38:05, corrected graph fixture|3/0F/0E/0S|0s /27.317s|
|21:40:20, two-object candidate exact canonical six|6/1F/0E/0S; five companions pass; chain remains incomplete36366 (32863physics/3503animation),53lag, first3475 sidekick_routine`0C/02`, ownership loss70718 in HCZ2|0s /123s|
|21:41:41, live logical-word negative control|1/1F/0E/0S; expected logical Left4, actual0|0s /30.325s|
|21:43:48, final two-object runtime focused selection|87/0F/0E/0S|0s /90s|

The87 physical cases include launcher15, Chopper8, registered launcher graph3,
HCZ2 twisting-loop1 and60 anchor cases. The short selector
`TestSonic3kLevelLoading` physically selects two distinct classes:36 cases in
`com.openggf.game.sonic3k` and7 in `com.openggf.tests`; the other anchors are
BootstrapResolver6, DecodingUtils3 and AIZ1Skip8. Counts are physical identities,
not the number of selector tokens. Registered launcher restore recreates the
parent/arm and restores clear/landed/held states, then replays escape and automatic
release without duplicate arms. Chopper's registered object/player snapshots
restore twice and replay masked input/release; fresh-instance Chopper graph
recreation is not newly certified.

The first fixture errors above were test setup failures, corrected locally;
they did not motivate shared runtime changes. These are bounded focused/replay
checks. No broad smoke, full suite, native recapture, pixel/audio certification,
other donor, viewport or full-route breadth was run by this worker.


### Final replay and structural handoff

The final runtime fingerprint is
`7d268324fd09c46830af318adeed963c95ec8abe43942b1f596e8c7141d1372a`:
SHA-256 of the two sorted changed `src/main` paths, each followed by NUL and its
complete bytes. Focused source fingerprint at 21:43:48 is
`b4256b4a2691fe562256e7f87d119a16d1b581a61e12aabe5bc38012adcfab1a`.
The successful final passive chain diagnostic has source fingerprint
`f27260f5f504480f4112e7c68b7dc0092831db5cdf69f6c6452b1e836c0f80d1`;
source fingerprints use all changed `src` paths against `61be1910`, sorted with
the same NUL/full-byte convention. The retained five-file `src` fingerprint after removing the passive observer and
adjusting only caller-label comments is `b7e13000ad35e585332ee70951aee972c88958d9e397b3a9f4b8cba1804d3b21`.
The registered runtime/test edits are retained; the passive `afterProductionStep`
chain edits are removed byte-for-byte to HEAD.

The final six-case command completed21:46:38: five companions pass without
skips, but the optional first-primary nearby-object formatter threw on a spawnless
helper at5065 (six cases, zero assertion failures, one diagnostic error;122s Maven,
0s queue). Its correction was restricted to the chain selector:

```bash
q -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical '-Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds' test -B
```

The first retry compiled no tests: the temporary observer named a nonexistent
boss-debug accessor (24.932s Maven,0s queue). After using the existing public
`traceDebugDetails`, the chain completed21:49:54: **one case / one ownership
assertion failure / zero errors/skips**,53.585s Maven,0s queue. The five companion
results on the same final runtime/fixtures/environment are reused, not rerun.
Their standalone AIZ/HCZ comparisons remain20,463/3,519 samples with zero
physics/animation/bootstrap/warnings; missing advertised auxiliary schemas remain
explicit coverage limits. Every reported result belongs to a completed invocation;
the failed diagnostic attempts are not gameplay regression evidence.

Final segment11 is incomplete at **36,366 differences:32,863physics and
3,503animation;53lag rows; zero bootstrap errors/warnings**. Its first report
remains3475 `sidekick_routine` native`0C`/engine`02`. Chain segments0/2/4/6/8/9
still complete cleanly; segments8/9 retain7,175/3,574 rows and62/55lag. These
counts cannot be compared as equal-depth totals with the old81,981 ending5725
or the hand-only22,571 ending4001. The final comparison reaches7642; source
ownership is replaced at7643/movie70718 by load generation9, zone1 act1 (HCZ2).

The next causal disagreement is **shared player primary-routine dispatch**, not
another proved HCZ object correction:

| Row | Native Tails | Final engine |
| --- | --- | --- |
|3474|`2F49,06BA`, air0, controlled by the column|same position; air0; no drowning pre-death|
|3475|AirCountdown writes routine`0C`, control`81`, zero velocity, resets air30|same position`2F49,06B2`; drowning/pre-death true and air30; trace projection reports routine2|
|3476|Y velocity`0010`|Y velocity`0000`; pre-death remains held|
|3477|Y`06A3`, fraction`0E00`|Y`06A2`, fraction`FE00`|
|3620|X`7F00` after native disappearance|X`2F49`; retained controlled player|

`AirCountdown_ReduceAir` (sonic3k.asm:33579-33606) installs routine`0C`.
`Tails_Index` (sonic3k.asm:26131-26138) dispatches it directly to
`loc_15810`/`loc_15828` (sonic3k.asm:29354-29367): move using the incoming
velocity, then add`$10`, record follower history and process the routine's tail.
Sonic's `loc_125C6` (sonic3k.asm:24742-24747) has the same move-before-add shape.
These are primary routines, so the normal control routine's object-control gate
does not intercept them.

Current `PlayableSpriteMovement.handleMovementDispatch` returns for
`isObjectControlSuppressesMovement && !isHurt && !getDead` at558–560, before its
pre-death branch at678–687. A drowning-but-not-yet-dead player with control`81`
therefore never runs that movement/timer branch. Even moving the condition alone
would leave its add-before-move ordering opposite native. Fixed countdown/death
ownership and CPU dispatch must be checked together. Separately,
`TraceCharacterState.routineFromSprite` has no pre-death arm and returns2, while
`NativePlayableRoutine` labels DROWN as`0A`; native writes`0C` here. Merely
changing the comparator's byte would hide the reported field while leaving the
measured gravity, fractional movement and lifetime wrong.

This is the finite next representation/verification boundary for the parent:
resolve native primary routine before ordinary object control and CPU steering,
retain move-before-gravity/history order, reconcile the fixed countdown/death
owner, and restore/replay retained controlled players before/after the routine
change. Required negatives include normal controlled waiting (which must still
hold), native P1/P2 drowning with control`81`, and S1/S2 consumers of the shared
dispatch. No shared dispatcher, routine projection, fixed-air manager or physics
source was changed here. A local column release or injected Y velocity would
compensate for the wrong owner and is rejected.

The first actual **primary** motion difference is later,5065 during the miniboss:
expected velocities`059E,F812,F7F6`, actual`FA62,07EE,080A`; engine has no hit
and HP6 there, then hits on5066 (HP5), one row after native. Position/fractions
first separate5066. This is a measured next primary frontier, not proof of a
separate boss defect or of causation from the earlier Tails error. Do not fit a
boss offset/delay before qualifying the shared player frontier. Engine P1 later
becomes dead at7477/movie70552 at`06FD,07B4`, with drowning/pre-death false;
native P1 remains alive at`07F1,0668`. The subsequent ownership replacement is
therefore not the old drowning stop. This observer did not collect the final
production receipt kind; it observed dead state and generation replacement.

The helper's additional shake finding is retained as source evidence only:
`Check_LRControllerShake` (sonic3k.asm:179972-179989) masks the low byte of the
selected raw controller word (`Ctrl_1` held/pressed layout at
sonic3k.constants.asm:549-554); the old Java path reads held sprite getters, which
for CPU P2 are AI steering. Direction-release edges can therefore count as changes
without native new presses. This patch does not claim to qualify that inherited
raw-controller ingress or attribute the present route stop to it.

All temporary source probes and inspected task-owned XML/trace diagnostics are
consumed. There is no live Maven job or helper. The task checkout and local
commit remain for parent review; combined smoke, integration, push, whole-route
completion, remaining product matrices and checkout cleanup are parent-owned.


## HCZ review follow-up: authoritative standing and logical-word consumption — 2026-10-25

Parent review of frozen `e27a600` identified two bounded defects before delivery.
Neither finding establishes the later campaign death. This continuation retains
that commit and its exact `61be1910` base in `t3-191b48d4`; no rebase or upstream
runtime import was performed.

`MegaChopper_CarryFollowPlayer` publishes both horizontal bits when both are
held (`sonic3k.asm:184449-184453`). `Sonic_ChgJumpDir` executes its Left and
Right branches independently (`sonic3k.asm:23129-23158`). The legacy forced-mask
API also enabled forced-right priority, so production player dispatch suppressed
Left. An independent airborne, zero-X-speed capture followed by the real
`SpriteManager.update` failed on frozen runtime with X velocity `$0018` rather
than zero (1 test, 1 assertion failure, no errors or skips; 26.529 seconds Maven,
zero queue wait). Immediate field tests had missed consumption. The correction
adds an engine-internal scripted logical-word writer using existing captured
mask/priority/logical fields. Existing deliberate forced-right writes retain
priority. The object write updates the live logical word, leaves the already
recorded follower slot untouched, and feeds the following player pass. The
production control also restores the player and repeats consumption, verifies
raw input independence, and checks the legacy override.

`HCZHandLauncher_TryGrabPlayer` reads the object's authoritative standing bit
before its next solid call (`sonic3k.asm:66006-66008`). `RideObject_SetRide`
clears the previous owner's bit when another support acquires the player
(`sonic3k.asm:42067-42079`). The private retained boolean introduced in
`e27a600` survived that external clear. A manager-level landing, support transfer,
and next hand pass failed independently (1 assertion failure, no skips). The
hand now queries the existing solid manager latch. That owner already captures
and restores object/player identities and standing state; the duplicate boolean
has been removed. The final-Y checkpoint and prior-pass order are unchanged.
The new manager test restores the landing and repeats the transfer/forward pass.

The initial two-control run had 2 cases, 1 expected launcher failure and 1 fixture
error (a sensorless test player entered real movement; 28.650 seconds Maven).
A second Chopper control failed because its real Sonic centre was outside the
capture range (27.616 seconds); fixing the fixture produced the independent
velocity failure above. The first corrected-runtime run executed 28 cases:
all 15 launcher and all 4 registered graph cases passed; Chopper movement passed,
but its new history assertion exposed a missing preceding `endOfTick` in the
fixture. That fixture boundary was corrected without changing runtime. Maven
execution was 79 seconds and queue wait zero. These are focused checks, not a
campaign or broad-suite result.

The final review control passes (1 test, zero failures/errors/skips), including
restore/forward, raw held-field independence, history phase, and the legacy
forced-right override. It shared a focused invocation with the independent
old-runtime drowning controls: 12 physical cases, 10 expected drowning failures,
zero errors/skips, 28.893 seconds Maven, zero queue wait. The unchanged 27 passing
review regressions above are reused; no broad suite or replay was repeated for
this review-only correction. Exact selection was
`TestMegaChopperBadnikInstance,TestHCZHandLauncherObjectInstance,TestS3kHczHandLauncherGraphRewind`
followed by
`TestMegaChopperBadnikInstance#bothDirectionsSurviveObjectThenProductionPlayerPassAndRewind,TestNativeDrowningDispatch`.
Both used queued Maven `test -B -Dmse=off`, explicit surfaceless EGL, Java 25,
Lua 5.4, and the three original verified absolute ROM paths. Display/Wayland and
inherited JVM-option variables were unset. The remaining canonical replay is
reserved for the completed drowning correction, so the last measured campaign
frontier remains input 70718 / segment 11 row 3475 sidekick routine.


## HCZ continuation: primary drowning dispatch and fixed lifetime — 2026-10-25

The parent authorized the shared drowning primary dispatcher, its necessary
fixed countdown, CPU, history and faithful routine projections after reviewing
`e27a600`. `05bf23289` separately resolves the reviewed logical-word consumption
and authoritative hand-standing defects above. This continuation changes no
ring, fresh-load, setup timing, hardware readiness, fixture, input movie or
comparison authority. The exact base remains `61be1910`; the parent's later
upstream documentation changes are left for parent reconciliation.

The native distinction is structural, not a game-name exception in shared
movement. S1 `Drown_Countdown .sonicIsDrowning/.sinking`
(`0A LZ Drowning Countdown.asm:278-295`) and S2 `Obj0A_PlayerHasDrowned`
(`s2.asm:42179-42192`) decrement their own `$78` lifetime, write primary routine
6 at zero, and otherwise move with the old velocity before adding `$10`.
Their shipped `FixBugs=0` branch does not force the player's normal routine;
the fixed branch would also force routine 2 and stop the HUD timer. S3K
`AirCountdown_ReduceAir` writes primary routine `$0C` and `$81` object control
(`sonic3k.asm:33579-33606`); `loc_1857C` retains the same fixed lifetime owner
(`sonic3k.asm:33611-33620`), but `loc_125C6` and `loc_15828` own old-velocity
motion, `$10` acceleration, history, animation/PLC and draw in the player slot
(`sonic3k.asm:24742-24747,29362-29367`). Tails `loc_15810` clears the carried
player's control and sets it airborne before that move (`sonic3k.asm:29354-29360`).

The engine had returned at the object-control movement gate before its generic
pre-death branch. Where that branch ran, it added gravity before moving and
spent a generic player timer. Fixed countdown timers were captured but never
armed; the fixed controller returned throughout pre-death. S3K normal input,
CPU, display timers, water and touch owners could still run around that misplaced
branch. `NativePlayableRoutine` called `$0A` drowning and `$0C` exit, and trace
projection omitted drowning. Native `$0A` is the camera-scroll wait
(`loc_12590`, `sonic3k.asm:24719-24730`), not drowning. The sole runtime enum
consumer is MGZ's CONTROL gate; its CONTROL value remains `$02`.

The correction selects S3K's primary drowning routine before input publication
or CPU steering, and before movement's normal-control resets or fluid-mutator
entry. That branch performs only native drowning motion, the existing history
write and animation/PLC tail. S1/S2 retain their controlled player pass and fixed
motion owner. A semantic `PlayerMovementRules.drowningUsesPrimaryRoutine` field
expresses the difference; the old candidate constructor retains the S1/S2
fixed-sidecar default. The unpublished 0.7 descriptor remains at the candidate version. Version
documentation and the normalized candidate signature pin are updated; ordinary
candidate signature regeneration must leave the release descriptor unchanged,
as the repository hook enforces.
No gameplay value is read from trace data. Only after those owners are corrected
do the enum and trace state project the actual S3K `$0C` state.

The fixed `$78` field is the sole native-slot lifetime counter. The existing
player pre-death field remains a captured phase marker until the fixed owner
writes death; the player timer is only a fallback for engine roster members
without a native fixed slot. Fixed continuation precedes water/shield gates,
resumes the native pending ten-bubble burst, and preserves the successful
allocation boundary before the angle RNG and shortened interval. The S3K burst
uses the native mouth offset, random angle and `Level_frame_counter & 3` subtype
selection (`AirCountdown_MakeItem/loc_185D8`, `sonic3k.asm:33631-33670`). The S3K player-slot branch preserves the native secondary routine by bypassing
CPU steering; S1/S2 retain their normal primary-routine CPU call. Carrier release and Tails respawn retire the corresponding captured
engine carry/drowning states; no second timer is introduced.

Independent old-runtime controls used production `SpriteManager.update` and
real Sonic/Tails movement. The final P2 lifetime fixture pins its leader in an
ordinary controlled wait and uses the native manual B-button respawn bypass. The final initial selection executed 12 physical cases, 10 expected
drowning assertion failures, zero errors/skips; ordinary controlled wait and
the corrected review input control passed. This proved missing `$81` control,
CPU movement before primary dispatch, generic timer ownership, normal-control
side effects, and new-velocity rather than old-velocity integration. A separate
P2 lifetime/carry selection had 3 cases, 3 expected assertion failures, zero
errors/skips: S2 and S3K each spent lifetime pass 120 in the player slot, and
S3K's carried P1 remained controlled. Maven times were 28.893 and 83 seconds,
with zero queue wait. Earlier fixture-only failures are retained in the review
record and task registry, rather than counted as engine evidence.

The first corrected dispatch check had 25 cases, 2 failures, zero errors/skips
(86 seconds Maven, zero queue wait). Thirteen of fourteen drowning cases passed,
including the S3K full death-marker/respawn path and registered restore/forward
before and after fixed-lifetime transition. The remaining S2 fixture expected
S3K's `$7F00` marker instead of S2's `$4000`; the other failure was a candidate
pin line for a bubble type outside the exported API. Both were corrected without
weakening native comparisons. The P2 check now also verifies the completed
engine drowning state is cleared at respawn, and independent successful/failed
allocation controls verify burst RNG ownership. Finished-source affected
regressions and the four mandatory S3K anchors are recorded below when complete.


The first complete affected selection ran 178 cases with 3 failures and no
errors/skips (92 seconds Maven; 93.073 seconds including wrapper overhead;
zero queue wait). The new universal CPU pre-death guard blocked an existing
Sonic sidekick stale-state respawn test. It was rejected and removed: S3K
bypasses CPU in its primary dispatcher, while S2 `Obj02_Control_Joypad2` still
calls `TailsCPU_Control` (`s2.asm:38963-38969`). The two new P2 lifecycle tests
incorrectly expected automatic respawn while their leader remained controlled.
Native manual A/B/C/Start bypasses that gate (`Tails_Catch_Up_Flying`,
`sonic3k.asm:26514-26526`; `TailsCPU_Spawning`, `s2.asm:39108-39124`), so the
physical case now requests B after observing the marker. The P2 fixture also
starts the CPU in native NORMAL, rather than its constructor's INIT state.
The earlier INIT warp is a fixture artefact, not evidence of native CPU steering
during drowning. A matched initialized-P2 old-runtime run on `05bf23289` then
proved both actual motion owners independently: 2 cases, 2 assertion failures,
zero errors/skips, 26.942 seconds Maven, zero queue wait. S2's fixed pass and
S3K's player pass each left Y velocity zero where native requires `$0010`.
Its first attempted compile failed because the test initializer is package
private (76 seconds, no tests); the fixture now accesses it reflectively.

The finished affected selection is **178 cases, zero failures/errors/skips**
(92 seconds Maven; 93.602 seconds wrapper elapsed; zero queue wait). It includes
16 drowning controls, 28 HCZ review regressions, all four S3K anchors (60 physical
cases), 11 candidate-API checks, and 63 selected movement, projection, music,
carry, death and respawn consumers. The exact queued selector is retained in
the task registry; its class/method groups are `TestNativeDrowningDispatch`,
`TestModApiSignatureSurface`, the launcher/Chopper/graph classes above,
`TestTraceCharacterState`, `TestDrowningControllerMusicSelection`,
`TestTailsCarryController`, and the affected methods of
`TestSidekickCpuDespawnParity`, `TestRespawnStrategies`,
`TestPlayableSpriteMovement` and `TestSonic3kMgz2EndBossEvents`. The four anchor
classes are unchanged from the earlier exact selector. Tested source fingerprint
is `df66817524b4d350adea01d57406e7bc42dc99c252c035d3e7d3481eb6c9f0c5`
over `05bf23289` plus the owned source diff and new drowning test. Java 25,
Lua 5.4, surfaceless EGL, the original verified ROM paths and unset display/JVM
variables match the previous launch environment. The three ROM CRC/SHA-1 pairs
were reverified against `AGENTS.md`; no ROM links or copies were made.

Source-segment clarification: manifest segment 11 is `hcz_2`, metadata HCZ act 1,
BK2 offset 63075. It is the second **HCZ1** stint, after the intervening segment,
not a native HCZ2 recording. Historical handoff prose that called its stop HCZ2
must distinguish the source from any replacement destination. The canonical
continuation below records the actual production boundary rather than inferring
it from the label.


The committed canonical six selector was then executed once on the finished
drowning source, with a temporary comparison-only `afterProductionStep`
observer (no gameplay writes). Result: **6 tests, 1 assertion failure, no errors
or skips**, 122 seconds Maven / 123.578 seconds wrapper elapsed / zero queue wait.
All three prefixes and standalone AIZ/HCZ passed. Chain segments 0/2/4/6/8/9
remain clean; segments 8/9 retain 62/55 lag rows. Standalone AIZ/HCZ compare
20,463/3,519 physics rows without errors. Segment 11 remains incomplete at
31,207 differences (28,396 physics / 2,811 animation), 53 lag rows and zero
bootstrap errors. Its first comparison is now row 3832 `rings`, native 104 versus
engine 94. Rings are an explicitly excluded edit owner in this continuation;
this result is reported, not masked or attributed to drowning.

The native P2 state now matches at entry 3475, the old-velocity sinking path,
fixed death transition 3619 and native `$7F00` marker 3620. At rows 3617–3622,
position, fractions, velocities, primary routine, status and animation match;
the inactive `stand_on_obj` reference is still unprojected (`9/-1`) under the
existing comparator policy. No comparison rule was changed. The fixed lifetime
is 120 executed fixed-slot passes, not 120 stored trace rows: animation-only
rows explain why entry and death are farther apart in the recorded row domain.
P1 position/fractions and all three velocities remain equal through row 5064.
Its first motion difference remains 5065: expected `059E/F812/F7F6`, actual
`FA62/07EE/080A`. The boss is one movement pass ahead (`$0690` versus native
`$068C`) and has six hits left; it receives its first hit on 5066. P1 later dies
at 7477 without drowning. At BK2 cursor 70718 production owns generation 9,
progression/ROM zone 1, **act index 1 (HCZ2)**, while the represented source is
still HCZ1. This is the destination/source distinction missing from the earlier
wording. The receipt kind was not sampled; the coordinator is unchanged.

Canonical command is the existing six-case `-Ptrace-replay` selector with
`-Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical`, queued `test -B`, the
same explicit graphics/JDK/Lua/ROM environment and `-Dmse=off` described above.
Its diagnostic source fingerprint was
`ef595de7a06cb2dcaff6b59a072497dc51cc9d4fce76f5f69301b0dc9bdd0633`.
The temporary chain source was restored byte-for-byte and diagnostics inspected
and consumed. This qualifies the drowning correction; it does not claim HCZ
continuity completion or attribute the remaining ring difference.

The next bounded HCZ object question is no longer an offset guess:
`HCZMiniboss_StartFight` only installs `Obj_HCZ_MinibossLoop`
(`sonic3k.asm:139350-139358`); the following pass's `HCZMinibossLoop_Init`
initializes velocity/timer and returns through child creation without moving
(`sonic3k.asm:139384-139397`). Java `startFight` inlines that initialization on
the callback pass. This source mismatch explains the observed one-pass lead.
The parent was notified before a boss runtime edit. Independent old-red owner
entry and registered recreation/forward controls are being established; any
boss correction remains a separate commit from the shared drowning repair.
