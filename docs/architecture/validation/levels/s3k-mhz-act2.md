# S3K Mushroom Hill Act 2 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_2`, ROM `$701`, engine zone 7 / act index 1.
Incoming seamless MHZ1 or direct level load; outgoing FBZ1 (`$400`) after the
boss's grab-wait completion. Status: integrated implementation reviewed
historically; current campaign act certification pending.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Historical source review missed composition gaps; the campaign has corrected entry/boss lifetime, shared defeat acknowledgement, results ownership and the ship Plane A consumer. |
| Cold-reachable | Actual fresh MHZ1 incoming native320 Sonic solo route traverses Act2 and reaches FBZ1 without gameplay seeds or deaths (2026-09-23). |
| Completable | The23775-input cold production capture includes all9 hits, actual capsule/results, ship carry and playable FBZ1. Other route products remain open. |
| Native-accurate | Direct ROM review/local corrections; no complete native visual or frame-perfect claim. |
| Rewind/standard | Native320 incoming route now passes all eight complete-registry restore/45-input replay spots through released FBZ1; five-preset entry/admission have independent short checks. Other breadth and lifecycle obligations remain incomplete. |

## Obligation mapping

| ID | Concrete contract and inspected source / historical evidence | Remaining obligation |
| --- | --- | --- |
| ENTRY | `TestSonic3kMHZEvents.act2ScreenInitLoadsGreenSeasonForLowerEarlyStart` and autumn/gold start cases; incoming MHZ1 retains production fixed SST ownership. | Both real entry paths, player/camera/control and configuration cross-product. |
| OBJECT | Shared MHZ traversal/badnik families plus act-specific end-arena supports/spikes. `TestS3kMhzCutsceneGraphRewind.mhz2LiftChildRestoresFreshWithCapturedPlayerReference` covers carried-player identity. | Act-specific placement/subtype coverage and native/team/donor interaction evidence. |
| EVENT | Season-region transition/boundary cases in `TestSonic3kMHZEvents`; custom plane/art stages reviewed against `loc_5528A..loc_55486`. | Current live terrain/palette/event threshold and negative-authority evidence. |
| CAMERA | End-arena repeat/clamp, explicit object repeat participation and pillar scroll table are exercised by event cases. | Native and wide lock/containment/release, both visible edges, replay around changes. |
| BOSS | `TestMhzBossObjects` checks initial camera gate, native palette/hit flash, weather-machine signals, rise/escape and `mhzEndBossGrabWaitTimerRequestsFlyingBatteryAndDeletesBoss`. | Current full encounter from controller input, allocation-pressure and phase breadth. |
| LIFE | Fresh runtime resets and existing object recreation are component evidence only. | Checkpoint/death/restart, world reset/persistence and team restoration. |
| LOAD | `act2BackgroundEventWaitsForEndBossSignalBeforeRestoreRedraw`, final redraw cleanup and FBZ request; ROM-backed layout/chunk/block/art pipeline reviewed historically. | Readiness/restore reconciliation and actual next playable FBZ state across breadth. |
| REWIND | `TestS3kMhzEndBossGraphRewind.mhzEndBossInvisibleControllersRestoreFreshRelinkParentAndPreserveFlags`, visual/weather/head/spike/hit-proxy helpers and arena helper tests. | Full live graph/attack/hit/defeat replay, world mutation, camera and load spots. |
| PRESENT | Normal/boss `SwScrlMhz` paths, season palette selection, ROM animation and custom arena visual helpers. | Current native references and rendered width-sensitive gameplay; tests of fields alone do not establish pixels. |
| ROUTE | Headless final grab-wait completion requests FBZ1 with immediate level deactivation. | Direct and incoming representative production routes through all mandatory interactions. |
| ORACLE | Original audit cites debris/child allocation, directional velocity, flicker and deferred deletion routines; source review identified no further Sonic/Tails feature gap. | Re-run applicable regressions with current evidence and honor trace exclusion. |

## Scope and evidence limits

Locked-on S3&K ROM, native `FixBugs=0`; Sonic, Tails and Sonic with Tails are the
accepted implementation scope. The [original completion audit](../../research/s3k-zones/mhz-completion-audit.md)
records the user's exclusions: no further Knuckles-route work after the camera
correction, no standalone S&K entry, and no further trace-driven development.
These are accepted scope limits, **not evidence that Knuckles is unsupported**.
The console pulley debug-menu unlock is excluded; engine debug controls remain.
Do not restart excluded trace work or expand the route scope by implication.

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
| Width | Current selectable presets are320/352/400/528/800. Entry and endboss admission are checked at all five presets; complete native320 traversal is observed. The same cold320 input at800 dies in MHZ1 at3523, before Act2, and is not an Act2 failure or wide completion proof. Donor/lifecycle breadth remains open. |
| Donor | Native plus every currently supported S3K movement donor; resolve support from production before selecting cases. No per-act donor certification from the reviewed records. |
| Main | Sonic and Tails, plus Sonic/Tails native pairing; Knuckles implementation expansion is excluded as above. Existing locked-on Knuckles camera test remains a regression, not route coverage. |
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


Incoming route follow-up (2026-09-23): `TestS3kMhzAuthoredRoute` completes
fresh MHZ1, the real results/title handoff and more than300px of released MHZ2
movement. Post-load capture/restore and45-input forward replay now preserve
one Insta-Shield identity, after correcting its delayed duplicate registration.
The26-case focused shared-transition selection passes without skips; see the
Act1 matrix and campaign audit for the exact command and capture provenance.
This is incoming continuity, not an Act2 completion or timeline-isolation test.


Leaf-blower follow-up (2026-09-23): the real incoming route exposed premature
controller deletion before its camera activation rectangle. Native footage
confirmed Sonic must wait at the Knuckles-only wall for the scripted lift.
`TestS3kMhzAct2EntryHeadless` now covers fresh native Sonic solo entry at all
actual presets320/352/400/528/800, press/lift capture-restore-forward replay,
and release/checkpoint7. Wider entry uses the shared native-camera projection;
original thresholds and the adaptation are documented inline. The incoming
MHZ1 route remains covered separately. Dynamic player priority through the
foreground is also a lift contract, not implied by successful movement.
Full Act2, other rosters/donors, load-history isolation and native whole-route
parity remain open. See the campaign audit for red/green and rendered evidence.

Final leaf-blower selection:157 cases, zero skips, on2026-09-23 at20:31 BST.
The five viewport rows also assert camera containment, preserved cutscene
minimumX, zero player y_vel while the carrier moves, and high-priority lift / low
priority release. Final320 incoming and800 direct movies are documented in the
campaign audit. Centering the wide leaf-particle span remains a presentation
follow-up; this does not claim full act or donor/roster certification.


Controller-lifetime follow-up: the five-preset entry test additionally captures
and forward-replays the active lift after Knuckles has retired offscreen.
Carrier reconstruction must work without a live cutscene parent. The centered
native320 leaf span preserves RNG consumption. These are localized entry
contracts; the final retirement selection and revised video are recorded in
the campaign audit when complete. Full Act2 traversal is still open.

Retirement checks complete on the current campaign candidate:86 object cases
pass at20:55 BST after fixing a new test's missing player binding; five graph,
five viewport and one incoming-route case passed on the same production code
at20:50 BST. Zero skips in those selections. The updated800px entrance movie
is `campaign-20260923-act2-leaf-blower-800-retirement` in the external MHZ archive.


Endboss admission follow-up (2026-09-23): the short placed-boss test passes
at320/352, retaining15 encounter members and20-frame restore/replay. The138-case
focused boss selection passed with0 skips at21:14 BST. Added checkpoint reload
breadth remains red at400/528/800: giant-ring retirement exhausts KosM FIFO
(7 rows,3 errors,0 skips). Cold incoming inputs reach the live fight but die
at21086. Completion and wide admission remain open; commands are in the audit.


Admission breadth is now green: the ring-specific capacity retry preserves the
four-entry physical queue and lets its existing enemy-art owner admit first.
All10 positioned/checkpoint rows at320/352/400/528/800 pass, including eventual
ring retirement and boss graph replay (21:30 BST). The accompanying49-case
ring/provider/admission selection also passes, zero skips. Native320 cold-route
boss-entry/chase footage is linked in the campaign audit; full completion,
other rosters/donors and native whole-fight parity remain open.


Cold completion follow-up: native320 Sonic solo now traverses freshMHZ1→MHZ2,
all9 boss hits, actual capsule/results, ship carry and the actualFBZ1 load
(23775inputs,0deaths,no gameplay seeds). Input lives in
`src/test/resources/routes/s3k/mhz2-sonic-incoming-320.{script,bk2}`. This is
not act certification: ship-body rendering is missing in the diagnostic movie,
and the new complete-registry rewind test found uncaptured scroll accumulation
at admission19671. Both fixes and their verification are in progress; the
campaign audit records exact commands and outcomes. Wider completion, other
rosters/donors, native parity and load-history isolation remain inherited gaps.


Final native320 route verification (2026-09-23,22:20 BST): the24-case focused
MHZ/FBZ selection passes with0 skips. `TestS3kMhzAct2AuthoredRoute` now covers
admission, intermediate hit, last hit, defeat, capsule/results, ship, ship carry
and released FBZ1, comparing every registered snapshot key on restore and
45-input replay. The destination waits through the recording driver's native
title owner using a bounded neutral-input tail; snapshots during the explicitly
non-rewindable fresh-load row are excluded by lifecycle semantics, not filtered
state. See the audit for the two fixed MHZ rewind defects and the FBZ retained-
plane palette-ownership correction. The revised875-frame ship-to-FBZ movie
shows the body, propellers and carry and fully decodes. Native ship footage
corroborates the foreground split; no matched-frame pixel-parity claim is made.

## Campaign broad-validation follow-up (2026-09-25)

At `ec8854e40`, `TestS3kMhzAct2AuthoredRoute` regressed at the ship restore
(input22758): `latchedSolidObjectBound` changed true→false. The live capsule was
in slot5 while `lockPostCapsulePlayerUp` had zeroed the ROM interaction slot.
`Restore_PlayerControl` only clears `object_control` ($2E); `interact` ($42) is
untouched. Remove that unsupported write, including its shared signpost copy.
A one-class diagnostic overlay confirmed the cause before production edits.
The focused queued Maven run `campaign-broad-repairs-focused` then passed261
tests, zero failures/errors/skips, including this entire incoming route and all
eight full-registry restore/45-input replay checkpoints. The existing route
video remains presentation evidence; the correction is retained contact state.
All other matrix breadth/lifecycle/native-scene gaps remain open.


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


Tails incoming entry (2026-09-26): the new 24,121-input cold MHZ1 Tails route
reaches this act via its actual seamless handoff, verifies released movement
after rebase and reaches the leaf-blower sequence. This is incoming-entry
evidence only; Tails Act 2 completion remains open. See the Act 1 matrix.


### 2026-09-26 — character-specific floor-grab correction

The user identified solo Tails's wrong pose in the Knuckles press scene. ROM
`sub_65E72/loc_65E9E` selects `RawAni_65EB0 = $B4,$A7,$B5,$A8` using
`character_id` plus V-int bit 1. The old engine selection used participant
index, so solo Tails inherited Sonic's $B4/$B5. It now uses character identity;
vertical flip, control ownership, clock phase and the lift animation are unchanged.

The short unit test checks both V-int phases for Sonic and real Tails. The
production entry test now spans all five presets × Sonic/Tails/Sonic+Tails,
observes the leader/follower raw pose by character key, restores/replays during
the floor grab, and continues through lift, carrier retirement and release.
All 103 cutscene/entry checks pass without skips; the three cold MHZ routes and
mandatory S3K entry/loading/decoding gates also pass on this runtime change.

Fresh native320 Tails video `campaign-20260926-tails-floor-grab-fixed-320`
supersedes the floor-grab segment of the prior Tails Act 1 movie. Of the shared
24,121 input rows, exactly 124 mapping-frame values change ($B4/$B5 to $A7/$A8),
with all other fields identical. The new 1,021-frame/60fps clip continues through
release, has no deaths and fully decodes; both raw-pose phase stills were inspected.
Native table accuracy is established by the disassembly; native video parity is
not claimed. The rest of the Tails Act 2 route remains in progress.


## 2026-09-26 — incoming solo Tails completion

`mhz2-tails-incoming-320.script/.bk2` preserves 35,814 ordinary controller
inputs from cold MHZ1 through both acts, all nine endboss hits, capsule/results,
ship carry and released FBZ1. No state seeds, donor or follower; native 320.
`TestS3kMhzAct2AuthoredRoute` now covers both Sonic and Tails. Each checks nine
live restore/forward-replay boundaries, including a 90-input weather-fade window
that crosses controller deletion and reconstructs its four captured palette lines.
Tails additionally checks the controlled late-pulley climb and airborne upper
passage. The palette-controller reconstruction regression separately exercises
both white and return phases; generic empty-constructor probes had missed its
final-array shape mismatch. The fade algorithm and ROM timing are unchanged.

The fresh `campaign-20260926-tails-act2-clear-320` video matches all 35,454
shared exploratory rows, apart from input-text representation, has no death or
follower rows, and reaches released FBZ movement. The 4014-frame 60fps 640×448
movie fully decodes; defeat and ship stills were inspected. This is engine route
and presentation evidence, not native-emulator parity. Other widths, native
pair/donor route breadth and inherited presentation obligations remain open.


Paired incoming follow-up (2026-09-26): the native320 Sonic + Tails cold MHZ1
route now rebases into Act2 and continues through the floor-grab/lift to released
traversal. This is not paired Act2 completion. The shared horizontal-bar restore
fix also applies to this act's ten placed `$0B` bars; short jump/upward/downward
release reconstruction checks supplement the existing solo Act2 routes. See the
Act1 matrix and campaign audit for the ownership defect and verification scope.


## Native-pair cold completion (2026-09-27)

`mhz2-team-incoming-320.script/.bk2` now continues the existing native Sonic+Tails
Act1 route through Act2, all nine endboss hits, capsule, ship pickup and playable
FBZ in40,630 ordinary inputs. No position, health, ring, clock or debug seeds;
P2 remains CPU controlled. This supersedes the paired-Act2 completion gap above.
The separate Act1 fixture and short entry checks remain independent.

`TestS3kMhzAct2AuthoredRoute` adds16 full-registry restore/replay windows for the
pair, covering both catapults, lower loop, swing vine, first and shared late
pulley, boss phases, capsule/results, weather restoration, ship carry and FBZ.
Both players must be controlled at the shared-pulley pull and climb spots.
`TestMhzPairColdRouteCapture` separately proves populated production live history
is isolated at the actual FBZ load, preserves roster identity and checks ordinary
movement after entry. Recording-driver snapshots cannot stand in for that check.
The candidate four-route/history follow-up passes without failures/errors/skips;
the integrated b3428e8d7 combined selection passes79 cases, zero skips, as recorded
in the campaign audit.

The fresh40,630-row rendering has zero deaths and matches all37,972 boss-author
and40,270 exit-author rows on every shared state field except input-text format.
The7130-frame60fps640x448 video and2710-frame defeat-to-FBZ excerpt fully decode.
This is engine presentation with retained registered Tails, not continuously
on-screen follower presence or native pixel/timing parity. Other widths, donor,
team/lifecycle products and inherited presentation obligations remain open.


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

## 2026-09-27 endboss viewport and pillar follow-up (candidate)

The new cold 800px input reaches the final catapult and chase, exposing delayed
native camera admission and an incorrect player clamp. `TestS3kMhzEndBossViewport`
adds five-preset native gate/wrap, whole-registry replay, and pillar scroll/helper
contracts. Base fails eight wide gate/wrap cases; the two native cases pass.
A separate native probe proves `loc_55586`'s division remainder was dropped,
shifting pillar collision helpers even at 320px. Both fixes are unintegrated and
candidate validation is pending; see the campaign audit. Native completion must
be reverified after the arithmetic correction, and 800px completion remains open.


Candidate native Sonic, Tails and pair Act2 routes all pass after the arithmetic
correction. Fifteen five-preset viewport/gate/wrap/replay cases pass. Two old
event oracles copied the port's discarded DIVU remainder; after replacing the
oracle with independent unsigned division, the86-case event/scroll/viewport
selection passes with zero skips. Original-game observations independently
match3,035 scroll words and3,710 helper coordinates. Integration and800px cold
completion remain pending; no native full-frame parity claim is made.


## 2026-09-27 cold 800px completion and twisted-vine rider restoration (candidate)

From integrated base `760a22892`, `mhz2-sonic-incoming-800.script/.bk2`
contains 25,548 ordinary controller inputs from fresh MHZ1, Sonic solo, 800px,
no donor or gameplay seeds. It includes the production setup input: do not add
an extra settling frame. Actual FBZ load occurs at input 25,397; the final
ordinary rightward movement reaches centre (117,1900), with control released
and no deaths. This closes the earlier *candidate* 800px traversal frontier;
it does not certify wider character/donor products.

`TestMhzWideColdRouteCapture` uses the production game loop and frozen inputs.
It checks 25 named whole-registry restore/forward-replay spots, with two replay
cycles each: traversal, chase admission and damage, defeat, capsule/results,
ship carry, weather fade and playable FBZ. Intervals stop before the fresh
load; this is not a claim of live-history isolation across that load.

The first replay failed at input 13,000 while Sonic rode the twisted vine.
The recreated object lost `activePlayers`, leaving Sonic attached but no longer
following the curve. The ROM owner is `Obj_MHZTwistedVine`, `sub_3DCD0` /
`sub_3DE80`: status standing bits persist between updates; entry-window contact
cannot reconstruct a rider already halfway around the curve. The exact field
now uses the existing captured player-reference collection codec. No shared
codec or forward physics changes. Short independent tests cover both curve
halves, both riders, replacement player identities and independent release at
320/800px. Matched source-only diagnostics fail all four cases on base and
pass all four with the candidate; the complete route also passes. Normal Maven
verification is queued and must be recorded before delivery.

Fresh engine video on `760a22892`:
`$HOME/Videos/OGGF/mhz-bring-up/campaign-20260927-wide-act2-clear-800/capture.mp4`.
The 3,548-frame, 60fps, 1600x448 clip lasts 59.133 seconds and fully decodes.
All 25,548 captured rows are alive; all 25,398 shared prefix rows match the
winning input author on twelve gameplay fields. Fight/exit/arrival stills were
inspected. The black load-boundary image at 25,397 is the real fade; 25,547 shows
playable FBZ. Forward presentation is unaffected by the rider snapshot fix.
This is engine evidence, not native pixel parity.

Measurement limit: adding this input to the existing RecordingFrameDriver
fixture diverged after the MHZ handoff (first one-pixel difference at 9,770,
reconvergence at 9,974, persistent movement difference at 10,300). Its cause is
unresolved. The existing native route fixtures remain unchanged. The new test
uses the same production GameLoop as the independently reproduced capture;
no physics was fitted to the headless driver and no driver parity is claimed.


Normal candidate Maven verification (Java21, DISPLAY=:0, absolute root S3K ROM,
serial queue) now passes the frozen800px route:1 test,0 failures/errors/skips,
25 named whole-world replay spots,33.96s test body(1m46 Maven including the
fresh build). The short/object/controller/policy and four mandatory S3K
loading/bootstrap selection passes94 tests,0 failures/errors/skips,28.334s
Maven. Separate `-Pguards` compact-reachability and field-disposition checks
pass5 tests,0 skips. Native Sonic/Tails/pair cold consumer routes pass3 tests,
0 skips (42.921s Maven). The complete FBZ route class and defeat-child checks
pass24 tests,0 skips (49.714s Maven), including the corrected boundary oracle.
Integration follows these completed candidate checks.


### Route drawing policy follow-up (2026-10-09)

The two MHZ cold-route JUnit classes keep every input and every scene
draw while omitting discarded screenshot readback. The restore-based prefix
control has the same palette mismatch with full drawing and drawing gaps;
independent cold snapshots also differ in session-local spawn/owner references.
Drawing gaps remain unqualified for MHZ, so scene drawing is retained.
Complete forward/replay branches remain fully drawn. The bounded
state/pixel controls and their limits are described in the
[headless testing guide](../../../guide/contributing/headless-testing.md).
This performance change supplies no new native reference, full-route visual
certification or missing viewport/donor/character coverage; inherited gaps remain.
