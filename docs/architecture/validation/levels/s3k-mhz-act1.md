# S3K Mushroom Hill Act 1 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_1`, ROM `$700`, engine zone 7 / act index 0.
Incoming normal level entry; outgoing seamless MHZ2 (`$701`) with coordinate
rebase, carried objects/resources and in-level title ownership. Status: integrated
implementation reviewed historically; current campaign act certification pending.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Original direct audit found no further missing Sonic/Tails feature; later miniboss lifetime, explosion and transition-identity fixes landed. |
| Cold-reachable | Native 320px Sonic, Tails, Sonic+Tails and Knuckles, plus Sonic at 800px, complete the miniboss/results and released MHZ2. Knuckles uses 30,000 ordinary inputs with 43 two-cycle full-registry windows and actual seamless history isolation; no position/clock/ring/health seed. Other products remain open. |
| Completable | Native Sonic, Tails, Knuckles and paired routes, plus the 800px Sonic candidate, complete all six boss hits, sign/results, seamless MHZ2 load and released player movement. Other configurations remain open. |
| Native-accurate | Disassembly-backed review plus local regressions; frame-perfect/native visual certification not claimed. |
| Rewind/standard | Several live interaction and graph checks exist; breadth and complete before/active/after obligations remain incomplete. |

## Obligation mapping

| ID | Concrete contract and inspected source / historical evidence | Remaining obligation |
| --- | --- | --- |
| ENTRY | `TestSonic3kMHZEvents.act1LevelLoadPinsCameraAndMinXToRomMhzStart`; height-based minimum X matches `sub_54B80`, whose branch is standalone mode rather than character identity. | Current normal/alternate/checkpoint load and donor/team breadth. |
| OBJECT | Mushroom, vine, swing-bar, pulley, wall, pollen and badnik families listed in the original audit. `TestS3kMhzSwingBarLiveRewind.verticalSwingBarHangStateSurvivesRewindRestore` captures a real held player, recreates the bar and resumes through jump release; horizontal case mirrors its own control path. | Per-placement/subtype inventory and act-specific breadth; other families' before/active/after replay. |
| EVENT | `TestSonic3kMHZEvents.act1ScreenEventArmsMinibossArenaAtRomCameraThreshold`; cutscene `_unkFAA9` latch belongs to the captured runtime and resets on full reload. `TestMhz1CutsceneReferenceClosure` and `TestS3kMhzCutsceneGraphRewind` cover references. | Current production threshold/negative authority and reset matrix. |
| CAMERA | `act1MinibossSpecialEventLoopsArenaAtRomThreshold`, explicit repeat-participation regression and the local moving-camera presentation audit. | Every required width through arena lock/repeat/release plus rendering. |
| BOSS | `TestMhzBossObjects.mhzMinibossFatalHitQueuesRomFadeExplosionAndSignpostHandoff`; `TestMhzMinibossLifetime`, `TestMhzMinibossDefeat`, `TestMhzMinibossExplosionAllocation` address fatal update/slot order and explosions. | Current encounter rerun and representative controller fight, phase/rewind breadth. |
| LIFE | Full reload resets the door latch per original audit; death/checkpoint behavior cannot be inferred from object recreation alone. | Dedicated short checkpoint, death/restart and repeated-load checks. |
| LOAD | `act1BackgroundEventRequestsRomSeamlessAct2ReloadWhenResultsSignalArrives`; `$4200` rebase and in-level title handoff. `TestS3kFixedSstTransitionRewind` executes actual MHZ act replacement and retains one pollen owner/identity. | Re-run production handoff, readiness and supported configuration cross-product. |
| REWIND | Swing-bar held-state tests, flame/shard/cutscene graph tests and post-transition capture → restore → capture evidence. | All applicable spots, deterministic forward replay and width/donor/team axes. |
| PRESENT | `SwScrlMhzTest`, `TestS3kMhzPatternAnimation` and ROM-backed boss art tests; no native AnPal cycle is missing. User manually confirmed corrected miniboss explosions in the historical record. | Current captured moving gameplay, viewport edges and audio ownership. |
| ROUTE | Prior component sequence reaches results and MHZ2. | Current representative real entry → mandatory mechanics → boss → next playable act; component transitions are insufficient. |
| ORACLE | `mhz-analysis.md`, completion audit and focused correction records identify owning ROM routines and rejection evidence. | Current bounded regression evidence; respect stopped trace scope. |

Historical follow-ups:
[Miniboss lifetime](../2026-09-12-mhz1-boss-lifetime.md),
[miniboss defeat](../2026-09-12-mhz1-boss-defeat.md),
[transition identity](../2026-09-12-mhz1-transition-rewind.md).
The last records 348 focused passes on its stated candidate; it is not a current
branch execution receipt.

## Scope and evidence limits

Locked-on S3&K ROM, native `FixBugs=0`; Sonic, Tails and Sonic with Tails are the
accepted implementation scope. The [original completion audit](../../research/s3k-zones/mhz-completion-audit.md)
records the user's exclusions: no further Knuckles-route work after the camera
correction, no standalone S&K entry, and no further trace-driven development.
Those exclusions describe the earlier MHZ task, **not evidence that Knuckles
is unsupported**. The 2026-10-13 playable-route completion request reopens the
native Knuckles route; standalone cartridge and strict trace work remain outside
this task.
The console pulley debug-menu unlock is excluded; engine debug controls remain.
Do not restart excluded trace work from this playable-route request.

This matrix was assembled on 2026-09-23 from current test source and historical
validation records. **Current component execution:498 cases pass, zero skips, on2026-09-23 at19:21 BST**
(`b6c1147a2` plus campaign edits;37 explicitly selected non-trace MHZ classes).
The campaign audit records the selection and limits; cold route authoring is ongoing. Existing
unit/graph tests are candidates for the listed contracts, not proof of complete
act coverage. The historical 622-case focused pass and 656-case guards in the
original audit belong to their stated September 12 candidates. Historical full
runs were red/interrupted; their failures were not all attributed to a matched
baseline. They cannot be relabelled a green campaign baseline.

## Breadth ledger

| Dimension | Accepted scope / remaining evidence |
| --- | --- |
| Width | Supported presets are 320, 352, 400, 528, 800 through the supported configuration. Current per-act entry/lifecycle cross-product, meaningful traversal and rewind coverage are unassessed; historical local camera checks do not fill this row. |
| Donor | Native plus every currently supported S3K movement donor; resolve support from production before selecting cases. No per-act donor certification from the reviewed records. |
| Main | Sonic, Tails, Sonic/Tails native pairing and the current native Knuckles route. Cold native 320px Knuckles Act 1 is qualified below (43 two-cycle windows), and the [Act 2 matrix](s3k-mhz-act2.md#native-terminal-flow-and-full-cold-knuckles-completion-2026-10-13) records the complete cold chain into released FBZ. Other configuration/lifecycle products remain open. |
| Team | Solo/native pair are reviewed implementation paths. Mixed, duplicate and maximum participant shapes need current contract-derived authority/lifecycle checks; absence of evidence is not an unsupported classification. |
| Lifecycle | Checkpoint respawn, death/restart, alternate entry where applicable, repeated loads, and exit behavior need their own short cases and width × supported-donor coverage. |

## Rewind ledger

Keep before/active/after spots independent of complete routes. Existing graph
reconstruction is useful but does not replace live forward replay. Required
remaining coverage includes camera lock/release, world/terrain/plane mutation,
checkpoint/death reset, resource work and team-sensitive ownership. Do not infer
full coverage from the generic object inventory or a class whose name contains
`Rewind`. Use the [level standard](../../../guide/contributing/level-test-standard.md)
for completion criteria and report skips/configuration explicitly.

## Next validation

Inspect the owning tests' actual fixtures/configuration, run a focused ROM-backed
selection through `tools/testing/maven_queue.py`, and record command, commit,
results and skips. Obtain representative controller-driven production completion
and presentation evidence without reinstating the excluded trace campaign.
Fill the breadth and lifecycle gaps above before claiming the act meets the
standard. The [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md) owns
combined integration/verification; this documentation does not supersede it.


### 2026-09-23 — fresh MHZ1 completion and delayed duplicate owner

`routes/s3k/mhz1-sonic-fresh-320.script/.bk2` preserves9301 controller passes
from normal locked-on level select, Sonic solo, donor off,320px. No position,
clock, emerald, ring or health seed is supplied. The route uses the actual
pulley grab/down-pull/release, sticky-vine spindash and terrain loops, reaches
the miniboss with47rings, lands all six hits, completes sign/results and moves
more than300px after the seamless Act2 handoff. There are no deaths. The
BK2 was compiled and round-tripped through the production authoring tool.

Input-authoring stalls were not runtime evidence: periodic jumps shed loop
momentum; running into the spring shaft skipped the adjacent pulley; the sticky
vine needs a spindash. A broad “near twisted vine” authoring heuristic also
suppressed a needed jump on a different vertical path and was removed. No
engine movement/collision or boss health was tuned to complete the route.

`TestS3kMhzAuthoredRoute` reached and replayed the pulley, vine charge, first
boss hit, last-hit approach and defeat. Its first post-load restore at frame9094
failed: the live object list contained the same Insta-Shield owner twice, while
restore retained one. The short `TestS3kFixedSstTransitionRewind` reproduction
now steps the player after manager replacement and fails in both MHZ and HCZ
(expected one owner, actual two). Earlier tests captured before this delayed
registration and missed it.

Root cause: `rebuildManagersForActTransition` already restores and reconciles
exact-SST owners. The subsequent `applySeamlessOffsets` marked Insta-Shield
unregistered for every policy except ALL_LIVE_SST, including PERSISTENT_EXACT_SST.
The next player update registered the identical object again. The flag reset
now applies only to legacy PERSISTENT_ONLY; both exact policies retain the
existing identity and their already-invalidated DPLC. The code explains that
ROM Load_Level retains the fixed shield slot outside Dynamic_object_RAM.
This is a shared policy correction, not an MHZ-specific exception.


Final focused verification at19:43 BST, Java21 and the absolute S3K ROM:
`maven_queue.py -Dmse=off -Dtest=TestS3kFixedSstTransitionRewind,TestS3kMhzAuthoredRoute,TestLevelSeamlessTransitionExecutor,TestFbzActTransitionHeadless,TestS3kLrzSeamlessActChangeHeadless,TestS3kDezSeamlessActChange test`
passes26cases, zero failures/errors/skips. This includes the full controller
route and45-input restore/replay at all six spots, the two delayed-owner
regressions, and neighbouring transition consumers. The earlier498-case MHZ
component selection preceded this one-condition shared-policy fix. Neither
selection is the campaign broad run.

The corrected external `mhz-bring-up/campaign-20260923-act1-cold-320-fixed`
movie covers frames7240..9300 (2061images) from9301ordinary input passes; all
rows have no death/follower. Its CSV is byte-identical to the pre-fix capture.
Full ffmpeg decode passes; fight, defeat and released-Act2 stills were inspected.
The input source/BK2 and provenance accompany it. Native visual parity, other
characters/widths/donors, full Act2 traversal and load-history isolation remain
separate obligations. No excluded Knuckles or trace work was reinstated.


Subsequent Act2 entrance fix (2026-09-23): the same9301 inputs now legitimately
finish inside Knuckles's press sequence. The earlier released-title spot still
proves incoming control release; the final pass is no longer required to remain
uncontrolled. The campaign's new leaf-blower capture extends to9901 passes,
showing the lift and upper-route movement. The Act2 matrix owns that coverage.


### 2026-09-26 — physical checkpoint lifecycle coverage

`TestMhzCheckpointRoutes` inventories all nine ROM-authored physical posts
(five in Act 1, four in Act 2) against the independent disassembly placement
tables. Each post has a declared local controller approach for Sonic, Tails and
Sonic with Tails, with whole-registry capture/restore and two deterministic
forward replays. These approaches do not establish cold reachability.

The lifecycle matrix uses physical contact, then two production pit-death
stimuli through `GameLoop` and its normal fade/title/reload flow. It covers
320/352/400/528/800 for the three accepted native rosters and the current
launch-profile-supported S1/S2 donor rosters. Assertions cover saved position
and index, object/runtime replacement and event binding, roster identity,
viewport, donor rules and decoded participant art, control release and outgoing
history isolation. Scripted checkpoints remain separate; the MHZ2 entrance
index 7 retains its existing cutscene/admission tests.

The final Act 2 post needs the existing upper-passage jumping approach from
(15008,704); a flat post-height setup misses its loading band, and moving that
flat setup farther left falls into the lower passage. No runtime workaround was
added. This closes the tested local physical-post lifecycle cross-product, not
all alternate-entry, mixed/duplicate/maximum-team, event-latch or presentation
obligations. The campaign audit records execution and integration evidence.


### 2026-09-26 — Tails cold Act 1 completion

`mhz1-tails-cold-complete-320.script/.bk2` preserves 24,121 ordinary controller
inputs from locked-on MHZ1 entry, Tails solo, donor off, native 320. Tails takes
a different path from Sonic early in the act; the successful prefix approaches
the late pulley corridor, then uses native flight to clear the upper obstruction.
The route defeats the six-hit miniboss and rebases into MHZ2 at input 23051,
then traverses to the leaf-blower sequence (1150,1228 at the end). No death,
follower, position, health, ring or clock seed is present.

`TestS3kMhzTailsAuthoredRoute` checks early traversal, lower approach, upper
flight, first hit, last-hit approach, defeat and released MHZ2, with whole-registry
restore and 45-input forward replay at all seven spots. An independent rendered
replay agrees with all 22,621 authored state rows through defeat (every field
except the input text representation). The 2,221-frame/60fps movie includes
fight, sign/results and the Act 2 handoff; decoded video and named stills were
inspected. This is engine presentation evidence, not native-emulator parity.
Tails Act 2 completion and wider/donor/other-roster route breadth remain open.
Execution and integration evidence belongs to the campaign audit.


## 2026-09-26 — native-pair cold completion

`mhz1-team-cold-complete-320.script/.bk2` preserves 27,985 ordinary P1 inputs
(P2 neutral) from fresh native 320 Sonic + Tails through all six miniboss hits,
the seamless Act2 rebase, the floor-grab/lift scene and released Act2 traversal.
No gameplay seeds or debug completion. The full fresh capture has no Sonic death
or missing-follower rows; its 25,585 shared boss-author rows agree on every state
field except input-text representation. The 3,085-frame 60fps 640×448 movie fully
decodes; defeat and paired floor-grab frames were inspected. This is engine
presentation evidence, not native-emulator pixel parity.

`TestS3kMhzTeamAuthoredRoute` asserts the actual Sonic/Tails roster on every frame
and whole-registry capture, restore and 45-input replay at nine spots: early
traversal, swing bars, pulley, sticky-vine spindash, upper launch, first hit,
last-hit approach, defeat and released Act2. It exposed a horizontal-bar restore
bug missed by the older control-flag-only test: active/history maps lost their
shared `HangState` identity, so release cooldown diverged. The local restore hook
rejoins those indexes, preserving the ROM single-record model; no forward motion,
release timing or collision rules change. The short reconstruction regression
now covers jump, upward-auto and downward-auto release, independently of the route.

The combined 100-case candidate selection covers both bar test classes, all five
MHZ cold-route cases, 15 entry configurations and the mandatory AIZ/loading/
bootstrap/decoding checks, with zero failures/errors/skips. The campaign audit
owns integration and guard evidence. Full paired Act2 completion, wider/donor
routes and remaining native presentation/interaction obligations remain open.


## 2026-09-27 widescreen admission and inherited results lock

Integrated `a2fad9a79`, from base `5ff400010`: the cold 800px Sonic
Act 1 route reaches all six miniboss hits, actual MHZ2 reload and released
movement without seeds or death (`mhz1-sonic-cold-complete-800.bk2`, 10,330 inputs).
`TestS3kMhzWideAuthoredRoute` covers 17 whole-world restore/forward-replay spots.
`TestS3kMhzMinibossViewport` independently exercises the native gate, object spawn,
repeat offset, inherited results lock and release at 320/352/400/528/800px.
The incoming results boundary remains centered until its native maximum expands;
Act 2 initialization and the earlier results-active clear do not retire it.

Focused checks: 194 passed with zero failures/errors/skips, followed by the
extended ten-case viewport class (10 passed, zero skips). Native Sonic, Tails and
team Act 2 routes plus the four required S3K loading/bootstrap regressions also
passed (62 tests, zero failures/errors/skips). These are focused checks, not a
full-suite result. The full structural guard suite also passes (672 tests, zero
skips), as do 38 camera/mask/results-policy cases. The audit records the justified
proportionate scope. The integrated checkout passed the combined 299-case
focused selection with zero failures/errors/skips.

Video: `$HOME/Videos/OGGF/mhz-bring-up/campaign-20260927-wide-act1-centered-handoff-800/capture.mp4`.
The inherited glyph corruption was isolated to discarded background DMA and
corrected by `MhzActTransitionHandoff`; native/800 cold captures now retain those
40 patterns through the reload. The separate empty sprite-publication frames
remain open. `TestSonic3kMHZEvents.seamlessReloadRetainsTheDirectBackgroundDmaUntilTheNextAnimationPass`
checks the art immediately after the actual event-requested reload, before another
frame or mutation flush.
800px Act 2 completion and wider character/donor breadth remain open.


### Seamless sprite-publication follow-up (2026-09-27, candidate)

`TestLevelSpritePresentationLifecycle` independently exercises FBZ/MHZ/SOZ/LRZ/
DEZ reloads: pending/published SAT and HUD counters survive, while fresh loads
still clear them. Shared validation and exact route limitations are recorded in
[the campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md#seamless-sprite-publication-and-mhz-scroll-carry--2026-09-27).
This adds transition coverage, not another whole-act or native-pixel certification.

## 2026-10-13 — locked-on Knuckles admission

`CutsceneKnux_SKIntro` tests character 2, a clear checkpoint and then a nonzero
`SK_alone_flag` before claiming the camera and P1. The supported locked-on image
has that flag clear even though subtype `$30` remains in the ROM placement table.
The old character-only gate wrongly froze the cold native route at input 1000.
The production constructor now retains the locked-on prerequisite; declared
standalone object fixtures supply it explicitly and capture it through recreation.

`TestMhzKnucklesLockedOnIntroGate` admits the verified placement at `$5C3/$9F5`
through the production factory and manager at widths 320, 352, 400, 528 and 800.
Each case checks deletion before ownership and repeats full-world restore/replay.
The focused command selects that class and `TestMhz1CutsceneObjects` with the
absolute verified locked-on ROM: 93 cases pass, zero failures/errors/skips,
2026-10-13 18:02:16 BST, 29.684 seconds, on `2e992da7d` plus the intro changes.
This short component admission does not certify the complete Knuckles cold route.
Ordinary controller authoring has passed the old lock and real wall climbs;
miniboss, seamless MHZ2 and subsequent FBZ completion still require qualification.


### Cold Knuckles Act 1 qualification, 2026-10-13

The new `mhz1-knuckles-cold-complete-320` script/BK2 has 30,000 ordinary inputs,
byte-identical to the selected uninterrupted authoring prefix. No position,
clock, rings, health, shield, boss state or emerald inventory is seeded. Native
Knuckles passes the locked-on intro gate, real ground loops, blower/wall climbs,
bars/pulleys, sticky release and twisted-vine ascent, all six miniboss hits,
results and the actual seamless MHZ2 load at input 29044. Released ordinary
movement continues in Act2. The rapid fresh jump presses retain actual rebounds;
the independent flame children remain harmful during body hit-flash.

`TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActOneAndRestoresNativeInteractions`
passes one case without failures/errors/skips on `bed44fae6` plus these new
fixtures/test (2026-10-13 19:16:41 BST; 21.40 seconds class, 1:32 minutes Maven).
Command: `env -u DISPLAY -u WAYLAND_DISPLAY
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4
ALSOFT_DRIVERS=null python3 "$S3K_TASK_QUEUE"
-Dmse=off '-Dtest=TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActOneAndRestoresNativeInteractions'
"-Ds3k.rom.path=$S3K_TASK_ROM" test -B` in
`ai-s3k-playable-finish`. Forty-three independent whole-registry windows each
restore and replay twice with full drawing; all seven health values are mandatory.
Actual outgoing live history is recorded and the seamless handoff must retire it
while retaining frame numbering. The first attempt exposed the Act2 switch's
retired-parent reference; the unchanged cold test now passes after its native
ownership correction. This qualifies native320 solo Knuckles Act1. Complete
Knuckles Act2/FBZ, other widths/donors/teams and remaining lifecycle/native
presentation products are separate obligations.

The command records normalize only machine-local paths: `S3K_TASK_QUEUE` is
the updated main-workspace focused wrapper, and `S3K_TASK_ROM` is the absolute
existing locked-on ROM path with the documented supported SHA-1.


## Released checkpoint-world replay follow-up (2026-10-14)

The existing physical-post death driver now uses only the production GameLoop
fade owner and services pending render work after each tick. It checks exactly
one life lost at fresh assembly and after actual title/fade/control release,
then captures and replays the complete registered incoming world twice in both
restore directions after EACH of two real deaths. Every45-input branch requires
an alive leader, unchanged lives/post, retained roster/donor/viewport and the
same current runtime/object manager; a raw restore never crosses a load.
Short independent native320/S2-Tails800 witnesses precede the product methods.

This act contributes175 products over all its authored posts and all five
production presets, using native Sonic, Tails and Sonic+Tails; S1 Sonic and S2 Sonic/Tails/pair. The composed
`262e9bbb10bb317d183dbf289dcfa8568fa0258c` plus final test changes passes the
12 short witnesses in request93678 at12:58:56 UTC, then all1077 affected
products in63733 at13:13:31 UTC, zero failures/errors/skips (3:32 Maven,
queue wait separate). DEZ2 post5 still reaches its placed gravity writer through
ordinary movement and verifies the real inverted death and native cleared
gravity after reload. This adds incoming-world replay to the previously
qualified physical contact/death scope; other unchanged checks were not repeated.
[Exact selectors and source hashes](../../plans/2026-10-13-s3k-playable-completion.md#existing-checkpoint-driver-qualification-2026-10-14)
are in the completion plan. Further roster and mechanic/cold-route products remain open. No native pixel/audio, overall act
certification, full-suite or combined delivery claim follows.


### Knuckles physical checkpoint follow-up (2026-10-14)

`TestMhzCheckpointRoutes.knucklesPhysicallyTouchesAndReplaysEveryPost` adds
5 native Knuckles physical contacts in this act with two full-registry
contact replay cycles. `knucklesPreservesItsRouteAcrossBothDeathsAndIncomingWorlds`
adds25 products over every authored post and all five selectable widths.
Each uses the qualified actual two-death driver, exact life loss, native character
and retained route, fresh object manager, timeline isolation and two45-input
forward/reverse full-world cycles after EACH released death. They are declared
local approaches, not new cold traversals. Donor sanitization still excludes
Knuckles from S1/S2 products.

On source`64f419a3b3a0a9a81b9cfeeb26b55090114f2930` plus the final test draft,
request50826 finishes at13:19:59 UTC: all54 added MHZ cases pass, zero errors
or skips (10.55s class). Its separate five-case Bubbler identity regression
contains two genuine failures; this mixed invocation is not a green delivery
gate. Together with the distinct315 preceding MHZ death products, the current
qualified lifecycle floor is360 (Act1=200,Act2=160), not a single pristine360
invocation. Further rosters, mechanics, cold-route and native visual/audio
breadth remain explicit obligations.

## Solo Sonic movies re-authored for the native carry-in Tails (2026-10-15)

aedc137a1 installs the ROM's temporary carry-in Tails for locked-on solo Sonic
(`SpawnLevelMainSprites` `loc_68C2`→`loc_68D8`, sonic3k.asm:8180-8197). The old movies
started on the ground; under the carrier they miss both opening rings and reach the
Knuckles clamp (`Obj_MHZ1CutsceneKnuckles` `loc_62CF8`, x=$389) 39 frames early. The
carry-in also changes the RNG from frame 0, so no opening can restore the old run exactly:
every opening that lands the cutscene on the old frame 232 has the same new RNG, and none of
1596 (320px) / 1126 (800px) distinct such openings (300 replayed each) keeps the old
remainder working past the pulley and sticky vine. No runtime behaviour changed.

Re-author, controller-only from a cold boot: jump off the carrier (`20 R; 1 R+A; 37 R;
1 R+A; 28-40 R`), collect both rings, stop in the tunnel and walk into the clamp on frame
231 so the cutscene lands on 232 and releases on 863 exactly as before. The remainder
tracks the old route's trajectory frame by frame (reference: the current engine with the
carry-in sprite removed before the first frame, which reproduces the behaviour lane's old
CSV exactly to 23485): the old input for the same frame while P1 stays within 4px of the
reference, otherwise a 60-frame trajectory-cost search from rewind marks; every committed
movie is accepted only from an uninterrupted cold replay. Restore gaps found on the way
(below) were bridged in the temporary authoring helper only (carrying the mushroom cap's
player maps across marks, replaying old inputs straight through repeated divergences).

| Movie | Old SHA-256 | New SHA-256 | Frames | Milestones (new; old) | Status |
|---|---|---|---|---|---|
| `mhz1-sonic-cold-complete-800.bk2` | `04a103e0e06bb48bdbf9757bdd0892ee3bcbbc8fbd234b642b5b150d1e0f4723` | `1fd85032cbfb152ab158419f9a0dc3b78c4615d0df5cb6943ed19c37ef323757` | 10330 | pulley 3671, sticky vine 4870, miniboss 8182…defeat 8829, MHZ2 9260 — all identical to old; old MHZ2 tail unchanged | green (frame-keyed spots unchanged) |
| `mhz1-sonic-fresh-320.bk2` | `b56e810e5759a89964efd501cf77e34f16a48700caab6d0b1e856ee513114a89` | `e7e16005f91fc38f8c9dd4afcd9a9788196a9b47a56aeb7c229f2732a4bac2a5` | 9471 | pulley 3518 (3518), sticky vine 5410 (5410), miniboss 7241…defeat 8114 (7948); hold right to the act end, MHZ2 8617 at x=448 (8469, 448); release 9148 (8979); leaf-blower grab 9340 (9176) | green after the rewind fixes below (movie unchanged) |

The fresh movie is the first 9471 inputs of the re-authored incoming 320 movie (MHZ act 2
record). `TestS3kMhzAuthoredRoute` reaches pulley, sticky vine and all six miniboss hits,
then fails its own `restore act-two-released at 9257` check: after `registry.restore` the
active objects gain a `FireShieldObjectInstance:fixed` that did not exist at capture (P1
701,1906, VInt 9257, level timer 603, RNG `6a65b7fb`). The old route reached the same
position (704,1905) without a shield; the new act-1 history differs. This is a rewind
fidelity defect, not an authoring or gameplay one; the row stays red until it is fixed.

Rewind fidelity findings from this authoring (cold replay vs. restored replay of the same
inputs; fixed afterwards, see the next section):
1. `MhzMushroomCapObjectInstance` keeps `previousYVelocities`, `lastLaunchFrames` and
   `standingPlayers` in identity maps keyed by player sprites; restore does not recover
   them, so a restored player standing on a cap bounces at −$680 instead of the live −$780
   (`launchStandingPlayerOnSpringFrame`; 320px route frame 3903).
2. Registry capture refuses some frames with `Invalid rewind reference at
   MushmeanieBadnikInstance$ShellChild#parent` (800px, frames ~6790-6861).
3. The resurrected `FireShieldObjectInstance` above.
4. Unidentified 1-7px y restore drift in MHZ2 at 800px among mushroom caps and pollen
   (first at 11320, P1 3911,1645, VInt 11320, timer 2029, RNG `e1f02cdb`; again 14132,
   P1 8954,1102, VInt 14132, timer 4841, RNG `9bb599f3`).

Validation on 923399354 (focused selectors through `tools/testing/maven_queue.py`, normal lane,
S3K/S1/S2 ROMs, run `v-mhz`, Maven Finished 21:07:07Z, 6 tests, no errors or skips):
`TestS3kMhzWideAuthoredRoute` 1/0, `TestS3kMhzAct2AuthoredRoute` 3/0;
`TestS3kMhzAuthoredRoute` 1 failure (`restore act-two-released at 9257`, the FireShield
restore defect above); `TestMhzWideColdRouteCapture` 1 failure (zone 7 at 29700 with the
unchanged incoming 800 movie; stopped, see the MHZ act 2 record).

### Rewind fidelity fixes (2026-10-16, `902ba8a10`)

A dense sweep (capture every 10 inputs, play 30, restore, replay and compare P1 rows plus every
registry key; never across act, fresh-load, mode or fade boundaries; now the `RewindFidelitySweep`
tool, with `TestMhzRewindFidelitySweep` pinning the fixed MHZ1 sites) over
`mhz1-sonic-fresh-320`, `mhz1-sonic-cold-complete-800`, the incoming-800 candidate and
`mhz2-sonic-incoming-320` located each defect; red-first focused tests cover them.

| # | Defect | Cause | Fix | Test |
|---|---|---|---|---|
| D1 | restored bounce −$680 vs live −$780 | `Obj_MHZMushroomCap` `$30/$32` y_vel and standing set held in uncaptured identity collections | `CAPTURED` policies; drop records of sprites that left the player slots (carry-in Tails) | `TestS3kMhzMushroomCapRewind` |
| D2 | capture refused at `ShellChild#parent` (800px 6827-6860) | launched shell kept a Java link to the body its second hit deleted | drop the link at launch (`loc_8DC42` is the last parent read); spawn-based recreation, parent relinked by id | `TestS3kMushmeanieShellRewind` |
| D3 | `FireShieldObjectInstance` "resurrected" at 9257 | live bug: `PERSISTENT_EXACT_SST` transitions dropped the player's shield/stars owners (the fire shield from 6721 left the manager at MHZ2 8617 while the player kept it); restore re-spawned it correctly | register owners the carry did not retain (`Shield`/`Invincibility_stars` are fixed `Level_object_RAM` slots) | `TestS3kFixedSstTransitionRewind#playerBoundPowerUpsSurviveThePersistentExactSstCarry` |
| D4 | 1-5px y drift after restore (800px MHZ1 6860-6880; 320px 5550, 5790) | `Obj_MHZCurledVine` `$36/$37` segment map, catapult riders and dropping-platform riders uncaptured | `CAPTURED` policies | `TestS3kMhzRiderCollectionRewind` (the platform case also passes without its policy; the 320px sweep at 5550 is its evidence) |

The reported MHZ2 800px drift sites (11320, 14132) replay identically after the fixes; they were
the same rider/cap family (the authoring helper's cap carry keyed on a moving spawn). Sweeps on
the fixed tree: fresh-320 0-9470, complete-800 0-10329 and the incoming-800 candidate 0-15100
show no P1 drift; incoming-320 MHZ2 9471-23906 none before the FBZ load. The restore refusal
during the FBZ fade is policy, not a gap: live rewind is rejected and its history cleared while
a fade with a completion callback is pending (`LiveRewindManager` `rewindBlocked`), and
`PlcFrameLifecycleCoordinator.beginNativeBlockingFade` refuses a second fade when a raw
registry restore crosses one.

Remaining snapshot-only inequalities (no P1/gameplay effect, outside the route tests' spots):
the MHZ1 carry-in Tails is recreated by restores in its first ~100 frames and solid-contact
headroom/plane-switcher snapshots keep direct references to the replaced instance; MHZ2's
Knuckles-cutscene leaf particles recreate with a zero spawn record. Outside MHZ, unverified
per-player identity collections without a policy: `CorkFloorObjectInstance.standingRiders`,
`LbzMovingPlatformObjectInstance.standingPlayers`, `SinkingMudObjectInstance` maps.

Validation (focused selectors via `maven_queue.py`, normal lane, S3K/S1/S2 ROMs): run `p6`
(Maven EXIT 22:33:17Z) 139 tests, 0 failures/errors/skips, including `TestS3kMhzAuthoredRoute`,
`TestS3kMhzWideAuthoredRoute`, `TestS3kMhzAct2AuthoredRoute`, `TestMhzKnucklesColdRouteCapture`
and the MHZ object/rewind classes; run `p7` (22:47:20Z) 624 tests, 0 failures/errors/skips,
including policy/transition tests and the four S3K guards.
