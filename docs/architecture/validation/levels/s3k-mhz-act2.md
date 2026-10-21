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
| Completable | Cold native Sonic/Tails/pair and800px Sonic reach actual capsule/results, ship carry and released FBZ1. Historical native320 Knuckles completed the cold chain with terminal health one; the October21 current-source frontier below does not requalify that maintained movie. Other route products remain open. |
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
September implementation scope. The [original completion audit](../../research/s3k-zones/mhz-completion-audit.md)
records the user's exclusions: no further Knuckles-route work after the camera
correction, no standalone S&K entry, and no further trace-driven development.
Those are historical task limits, **not evidence that Knuckles is unsupported**.
The October 13 playable-completion request reopens native locked-on Knuckles
routes; standalone and strict-trace exclusions remain in place.
The console pulley debug-menu unlock is excluded; engine debug controls remain.
Do not restart the excluded trace work.

This matrix was assembled on 2026-09-23 from current test source and historical
validation records. **Historical component execution:498 cases pass, zero skips, on2026-09-23 at19:21 BST**
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
| Main | Sonic and Tails, plus Sonic/Tails native pairing; October13 adds complete cold native320 Knuckles traversal. The September exclusion above is historical; other route products remain unqualified. |
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

## 2026-10-13 — current incoming controller repair

The untouched five-case reproduction fails the Tails/team late-pulley owner
assertions, paired FBZ history isolation and wide frame19500 deformation restore.
The wide route actually enters `SPECIAL_STAGE` at input19466 while retaining the
outgoing level object; restoring only that level registry against the new mode's
camera is an invalid world composition. Replay checkpoints now require LEVEL
and the same current level on both sides of their forward interval. No MHZ
scroll arithmetic or physics constants are changed.

Re-authored ordinary inputs complete fresh incoming Tails320 (35815 inputs),
Sonic/Tails320 (44004) and Sonic800 (29744) through MHZ1/MHZ2 and into released
FBZ1. Independent uninterrupted production playback observes load inputs
35454, 43234 and 28981 respectively, with no deaths. The team catches the actual
shared handle at35047 and reaches its top at35734; the two replay spots require
both players to be controlled and share its native X/Y. Its swing-vine spot
also requires both native participants to be controlled. Scripts/BK2s retain
ordinary controllers only, with CPU-controlled P2. They were authored against
immutable runtime `8438d7040` and the verified locked-on ROM, not seeded state
or trace data.

Earlier qualification on `2e992da7d` plus these route changes passes all three
`TestS3kMhzAct2AuthoredRoute` cases (2026-10-13 17:51 BST, zero skips).
The changed `TestMhzPairColdRouteCapture` passes at 18:04:43 BST (one case,
zero skips). It activates live history at the capsule approach, input 41705,
before the actual FBZ load at 43234. The first attempt activated after that load
because its old end-relative offset did not account for the longer FBZ tail.
Outgoing-history and actual destination isolation assertions are retained.
These bounded passes do not certify the remaining inherited breadth.


The revised wide controller qualification completed on 2026-10-13 at 16:42:43 BST:
`TestMhzWideColdRouteCapture` passes one case with zero failures, errors or skips,
retaining all 25 named whole-world spots, two cycles and forward replay horizons.
It observes the real FBZ load at 28981 and released movement. This is the queued
ROM-backed regression in `ai-s3k-playable-finish`, with the verified absolute
S3K ROM and `python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestMhzWideColdRouteCapture test -B`. Native Sonic/Tails/team and paired
history qualification is recorded above.


### Knuckles switch ownership at actual entry, 2026-10-13

The actual cold MHZ1→2 live-history check exposed a real switch-child ownership
defect: after `loc_6311A` erases the press actor, the independent `loc_632CA` /
`Sprite_OnScreen_Test` child retained a Java parent reference and capture rejected
it. Five short actual-ROM entry widths reproduce that error on `efbef3d38` (five
errors, no assertion failures/skips). Native Object Pos/2.bin row14 is
`04E8 07EC 8220`; the child retains its own `04E0/07EC`, frame0/palette0 and copied
respawn index. Only Sonic/Tails keep `loc_63308` / `Child_Draw_Sprite` and a live
parent. Commit `3596237c3`, integrated as `bed44fae6`, uses pure child recreation
and preserves that exact captured parent policy for the normal branch.

The focused five-width admission/recreation, existing entry/cutscene/graph and
mandatory loading gate passes 173 cases without failures/errors/skips at
19:08:47 BST (77 seconds Maven, isolated `ai-s3k-finish-lbz-knux-dev`). Its
`maven_queue.py --lean -Dmse=off` selector is
`TestS3kMhzAct2EntryHeadless,TestS3kMhzCutsceneGraphRewind,TestMhz1CutsceneObjects,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
with `"-Ds3k.rom.path=$S3K_TASK_ROM" test -B`.
The actual cold native320 Knuckles Act1→released Act2 rerun subsequently passes
43 two-cycle whole-world windows and real seamless history isolation at
19:16:41 BST. Later cold Act2→FBZ completion and broader products remain pending.

The command records normalize only machine-local paths: `S3K_TASK_QUEUE` is
the updated main-workspace focused wrapper, and `S3K_TASK_ROM` is the absolute
existing locked-on ROM path with the documented supported SHA-1.


### Native terminal flow and full cold Knuckles completion, 2026-10-13

Current native ownership is `f9ac22318` (`e3dc92ea5` / `2688b2cf1` integrated).
`sub_76782` runs after movement/arena admission; its pre-arena floor preserves
health two, and `loc_767F6` starts escape at health one after32 flash dispatches.
Earlier descriptions of nine hits to zero reflected the old Java terminal
assumption. Clock-stop/score ordering, character escape speed, `$3F` underflow
wait and fractional arena-repeat position now follow their native owners.
The proxy and both spikes retire on parent status-seven semantics at `$44D0`,
while the boss body and head remain live. Fresh recreation preserves captured
placement and exact parent identity, including independent two-parent negatives.
The174-case native/component/graph/schema gate passes without failures,
errors/skips at21:11:56 BST; the [dated record](../../plans/2026-10-13-s3k-playable-completion.md#mhz-native-terminal-ownership-and-current-route-replay)
contains commands and negative-fixture attribution.

`TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline`
passes on the integrated owner plus its new controller contract at21:15:27 BST,
with the existing wide test (two cases, no failures/errors/skips). The66271-input
`mhz2-knuckles-cold-complete-320` script/BK2 preserves the qualified30000-input
Act1 prefix exactly. True cold native320 solo Knuckles covers thirteen fixed
Act2 vine/loop/wall/ring/pulley/jump/glide spots plus nine dynamic boss/terminal/
capsule/ship/weather/FBZ spots. All22 windows restore the entire world and
replay twice; weather uses90 inputs and the other windows45, clamped before the
actual fresh load. Every native health value9..1 is observed, escape remains at
one, no death/transformation/hidden respawn occurs, and live history enabled at
63600 resets at actual FBZ66053. Released destination movement ends grounded at
`(779,1388)` with seven earned rings. The independent Act1 contract retains43
windows and its seamless history barrier. This is cold traversal, rather than a
positioned encounter or a movie whose raw parked load substitutes for control.

The800px Sonic test retains25 two-cycle world windows and released FBZ control,
actual load28981 and health9..1. Native Sonic/Tails/team and paired history pass
four cases on preceding`b143f272e` at21:05:03 BST; consolidated smoke must cover
them after the retirement follow-up. Other selectable widths, supported donors,
team products and native whole-scene conformance remain inherited obligations.
One inspected conformance follow-up is spike`loc_76674` status-six hit-flash
suppression and separate draw/touch admission: current Java still combines its
draw gate with touch flags. This bounded terminal/parent repair does not certify
that branch or full native parity, and adds no accepted discrepancy.


## Released checkpoint-world replay follow-up (2026-10-14)

The existing physical-post death driver now uses only the production GameLoop
fade owner and services pending render work after each tick. It checks exactly
one life lost at fresh assembly and after actual title/fade/control release,
then captures and replays the complete registered incoming world twice in both
restore directions after EACH of two real deaths. Every45-input branch requires
an alive leader, unchanged lives/post, retained roster/donor/viewport and the
same current runtime/object manager; a raw restore never crosses a load.
Short independent native320/S2-Tails800 witnesses precede the product methods.

This act contributes140 products over all its authored posts and all five
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
4 native Knuckles physical contacts in this act with two full-registry
contact replay cycles. `knucklesPreservesItsRouteAcrossBothDeathsAndIncomingWorlds`
adds20 products over every authored post and all five selectable widths.
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


### Knuckles complete movie retimed (2026-10-15)

Surfaced after the rewind-snapshot rows cleared on Root 2adfa37a7: the movie was
authored under the pre-8179ccd80 double fade advance, so its full load landed 11
frames early. `FadeWindowRetimeTool` measured the owned frozen fade-out window in
the production harness, inserted the native remainder and verified one
uninterrupted replay with no death. Test frame constants move by 11 at and after
the old load. No runtime behaviour changed.

| Movie | Old SHA-256 | New SHA-256 | Windows `start:native→+inserted` | Frames | New full loads | Verified end |
|---|---|---|---|---|---|---|
| `mhz2-knuckles-cold-complete-320.bk2` | `d9b4728bd380b3c87abc4070975a791987a3d5e2ba36f23074c4aa817d7a0b84` | `6e4cd1409bcb7e3d1889cf014dbb363e0a687bcea395073003008e063156676a` | 66042:22→+11 (Act1 prefix unchanged) | 66282 | 29044, 66064 | 779,1388, 7 rings, zone 4/0 |

Validation: run `probe-v1` (focused queued selectors, S3K ROM, branch
bugfix/ai-s3k-route-behaviour-regressions on 2adfa37a7) passed the owning test, 0 skips.

### Sonic incoming movies re-authored for the native MHZ1 carry-in (2026-10-15)

See the MHZ act 1 record for the cause (aedc137a1, `loc_68C2`→`loc_68D8`), the opening and
the tracking method. Act 2 continues the same time-aligned tracking from the act-2 release.

| Movie | Old SHA-256 | New SHA-256 | Frames | Milestones (new; old) | Status |
|---|---|---|---|---|---|
| `mhz2-sonic-incoming-320.bk2` | `df0f52d4d34620022ff027059a2cc90911a3e1b039feb884d1bf8597d63a351f` | `314c73520a71585c41cd96fbc618f95da367e38043d902ca66cd850d9c945bd4` | 23944 | MHZ2 8617, release 9148, end boss 19835…20992 (19671…21156), ship 22264/23548, FBZ load 23666 (23497) | green |
| `mhz2-sonic-incoming-800.bk2` | `15e386e9fec403ad411702f1f95c1c2f33e3202fa31d15d20af7e3038b324067` | `0cf35c45b0d7c899140b2066856fcdefa98b2919903b50253d63b31a110295cf` | 29433 | act 1 identical to old; MHZ2 9260, release 9832; object ride 11410…12040 (11313…11940); end boss 23040…24387 (23450…24797); FBZ load 28681 (old test constant 28981); ends 201,1219 in FBZ (same) | green (re-authored 2026-10-16, below) |

`TestS3kMhzAct2AuthoredRoute` takes the Sonic case's new input count (23944); the Tails and
team movies are unchanged. Incoming 800 stopped under the three-failed-searches rule: six
bounded tracking searches from the act-2 release (offsets auto/−54, mushroom-cap state
carry, no-restore windows) all failed in the MHZ2 traversal before the end boss — cold
replays drift from restored trials at 11320 (P1 3911,1645, VInt 11320, level timer 2029,
RNG `e1f02cdb`) and 14132 (P1 8954,1102, VInt 14132, timer 4841, RNG `9bb599f3`), and the
last search lost the old path at 15100 (P1 13012,1905, VInt 15100, timer 5809, RNG
`87c386cb`). The old movie stays in place; its test remains red (carry-in opening).

Validation on 923399354 (run `v-mhz`, Maven Finished 21:07:07Z): `TestS3kMhzAct2AuthoredRoute`
3/0/0/0 (Sonic re-authored; Tails and team unchanged); `TestMhzWideColdRouteCapture` 1 failure
(`expected: <4> but was: <7>` at 29700, unchanged incoming 800 movie).

### Incoming 800 re-authored under the fixed rewind (2026-10-16)

The 2026-10-15 stop above rested on restore drift; `902ba8a10` fixed it (MHZ act 1 record,
D1-D4) and the earlier drift sites replay identically. Four bounded searches from the act-2
release, each accepted only by an uninterrupted cold replay (temporary authoring helper, deleted):

| Search | Method | Result |
|---|---|---|
| r1 | time-aligned follow of the old route (offset −54) | cold-verified, lost at 12023: missed the old 11313-11940 object ride |
| r2 | from 11200: time-aligned follow with auto offset and with 8px tolerance; progress-based follow (`ReferencePathInputAuthor`) | both time-aligned runs lost at 12020; the progress-based run rode the object (11410-12040) and stuck at old progress 13842 (P1 8501,1315) |
| r3 | from 13300: time-aligned auto offset; progress-based with wider search | lost at 13900; stuck at 13844 — the old route waits at 7889,1455, 8165,1452 and 8270,1457 before a timed spindash climb, and the new route arrived ~450 inputs earlier |
| r4-r6 | stand at the old 7889,1455 rest (new 13036), search 0-400 neutral inputs, then the old inputs: 54 inputs re-align object phases (offset +410, 3.7px mean error over 400 inputs); time-aligned follow with +410 to the end boss; neutral through the ship to the FBZ load; then the old post-load inputs | complete: all nine end-boss hits exactly 410 inputs ahead of the old route; the ship sequence runs 99 later; FBZ load 28681 |

`TestMhzWideColdRouteCapture` takes the measured counts (29433 inputs, FBZ check at 29400, load
28681, replay clamp 28680); its spots, health and named-spot obligations are unchanged. No
runtime behaviour changed in this step.

Validation: run `p13` on the tree of this commit (focused selectors via `maven_queue.py`, normal
lane, S3K/S1/S2 ROMs, Maven Finished 23:37:15Z, 11 tests, 0 failures/errors/skips):
`TestMhzWideColdRouteCapture` 1/0 (25 named whole-world replay spots, two restore cycles each),
`TestS3kMhzAuthoredRoute` 1/0, `TestS3kMhzWideAuthoredRoute` 1/0, `TestS3kMhzAct2AuthoredRoute` 3/0,
`TestMhzKnucklesColdRouteCapture` 2/0, `TestMhzRewindFidelitySweep` 3/0.


### Coarse camera discontinuity recovery — 2026-10-17

The composed source2056 retained the correct native first-chunk backward borrow
but regressed this matrix's existing800px cold route. The loader now rebuilds
its current window when signed visible-camera movement and its masked coarse
movement disagree; native backward loads keep the ROM borrow branch. The full
route is requalified without changing the movie or supplying gameplay state.
See the [repair and validation record](../../plans/2026-10-13-s3k-playable-completion.md#wide-route-coarse-camera-discontinuity--2026-10-17)
for the matched baseline, causal controls, exact commands and remaining limits.


### Knuckles current-source frontier R1–R5 — 2026-10-21

The historical complete-route results above do not qualify the unchanged Knuckles
movie on the current climb implementation. All observations below use native320,
solo Knuckles, donor off, cold engine7/0, the original settings/title policy,
null gameplay seeds, neutral P2 and normal drawing after every actual step.
The entire30000-input Act1 prefix remains exact. The maintained test, script,
BK2, health predicates, fixed replay windows and load/timeline assertions are
unchanged; no maintained test ran in these rounds.

| Round | Actual evidence | Limit |
| --- | --- | --- |
| R1 | First supplied checkout was efd605142 rather than expected fee3; stopped with0 workloads. After explicit source reconciliation, two uninterrupted row0 projections on fee3c7a56 and immediate pre-climb876037b4 reached39940: current died there; baseline survived. All64 recorded public fields agreed through35018. First departure35019: current centreY1427/property3/angle−34/animation5 versus baseline1437/0/0/32. | Source association, not later death cause or stale-controller proof. |
| R2 | Source-only review, including existing5aef proof, found no new textual omission in the changed climb exit. Native centre/top-left and radius conventions were checked. | Prospective1464/1454 probe coordinates and−18/−8 displacement were not measured returns in R2. |
| R3 | Two original-once call witnesses through35020: current query8387,1464 returned distance−18/raw angle−34; baseline8387,1454 returned−8/−34. Both took idle negative-floor exit under the same effective Right+A operands. Retained35016..35020 public rows matched each earlier same-source arm. Temporary source preimages/inverses were checked and whole files restored. | Pinned shipped FixBugs0 geometry/landing tail supports current arithmetic and retained property3/animation5. No native route operand witness or later death attribution; no physics revert justified. |
| R4 | On e3d23e2fa, eight finite policies were authored/round-tripped;0–6 attempted,1–6 reached changed inputs. Selected policy3 releases only A at35020..35035, represses at35036, and otherwise preserves original same-index rows through41017. It stayed alive/unhurt with9 rings/3 lives, recorded actual mushroom support40858, and reached maxX10037. | Restored-checkpoint exploration only. Declared X10240 threshold **not met**; no independent cold or complete66282-input qualification. |
| R5 | On frozen b078214e7, one uninterrupted cold row0 trial matched all64 R4 mushroom40858 fields and R4's41017 endpoint. Actual flag4 at41924 admitted Up41925..42049; flag5 ledge animation completed into released ground42067 at10048/1836. At42511, released ground10165/1836 has10 rings/3 lives and no hurt. | First airborne X≥10240 is42549. Hurt42621..42720 loses all10 rings; final42721 is on actual Spikes support at10381/2316,0 rings. Generic stop label fired but is **not a safe endpoint qualification**. Policies1–3 remain untried. |

R4's first private comparison failed at35018 solely because the stale Spring
instance descriptor became absent after rewind; all other63 columns agreed,
and actual riding was absent in both. The original exception remains retained.
The private comparison subsequently excluded only that descriptor, with raw64
columns preserved; maintained assertions were not changed, and policy0 was never
retried. Captured released-owner normalization is source-supported, while the
actual captured release bit was not separately observed. This is not a demonstrated
runtime omission. Later real death guards refused restoration after policies2
(death39934) and6 (39796). Policy7 was cancelled before admission, never executed,
after review accepted policy3 only as useful exploration.

`MHZ_DIAGNOSTIC_DIR` denotes the existing external task directory
`mhz-knuckles-first-motion-20261021`; its exact location is in the owned registry.
The compact source/commands/ordered rows, original errors and selected proposal
remain outside the repository in
`${MHZ_DIAGNOSTIC_DIR}/observation.json`
and `r4-products/policy-3.{script,bk2}`. Raw logs/series/classes were consumed and
removed. Each round reports0 JUnit tests; skips are inapplicable to these executable
observations. See the [dated Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-current-motion-and-controller-frontier--2026-10-21)
for source pins, original-call interpretation and continuation boundaries.

R5 retained all selected R4 rows through40858. Its only later differences from the
original same-index stream are Up41925..42040 and Right42050..42511; Up41925..42049
was selected only from actual pre-step flag4, including the input that produced
flag5. The full42722-row product was production-authored/reloaded and compared
against every emitted controller row. It includes the later hazardous tail;
the useful next checkpoint is the observed42511 ground row, not a rewritten movie
count or a restored/qualified safe endpoint. No R5 restore occurred. Capturing
one registry checkpoint did not seed gameplay. The first policy alone consumed
1863 of its6000 additional-input bound, and no second request was submitted.

R5 command54947 exited0 with0 JUnit tests (skips N/A),389 seconds reported queue
wait and23.143 seconds Maven execution. This proves a bounded ordinary wall/ledge
passage on b078, not independent post-author replay, full66282 completion, native
route equivalence or FBZ admission. The prior R4 restored-only and no-cold-qualifier
limits remain part of that original record; R5 adds its own bounded fresh-cold
observations. Useful `r5-products/policy-0.{script,bk2}` and source/ordered facts
remain in the same external task directory. Root must review the ledge checkpoint
and later hurt/Spikes tail before further workload or maintained adaptation.


### Knuckles reached-ledge continuation R6 — 2026-10-21

The existing42511 ledge was cold-reached exactly in every completed R6 prefix:
all64 fields match, including10165/1836, sub9525/23340, velocity24/0/ground24,
10 rings/3 lives, ground/unhurt/no onObject/no actual riding, Right/noA and
released controls. World/Level/OM/P1/LEVEL and pending/fade guards qualify the
ordinary inlet; the stale mushroom latch is not contact proof. No restore,
snapshot or seed was used. The preserved42512 R5 rows include all R4/Act1 inputs.

Three finite policies and their6000-input bounds were declared before execution.
Three unique policies exhausted; no safe onward frontier qualified.

| Policy | Actual appended inputs | Terminal input | MaxX | Outcome |
| --- | --- | --- | --- | --- |
| 0 | 108 | 42619 | 10581 | hurt_ringloss_or_lifeloss |
| 1 | 109 | 42620 | 10581 | hurt_ringloss_or_lifeloss |
| 2 | 132 | 42643 | 10581 | hurt_ringloss_or_lifeloss |

Hazard support, first hurt and eventual ring publication remain separate facts.
Policies0/1 stop on first hurt while the counter still reports21 rings/3 lives;
no later ring loss or hurt receiver is inferred. Actual nearby/selected owners
are bounded public observations, not original damage-call or native witnesses.
The original R5 hazardous stop label stays rejected. A safe onward qualification
must not be inferred from unhurt Spikes support or a Maven exit0.

The [R6 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-reached-ledge-continuation-r6--2026-10-21)
records finite recipes, original ROM/source pins, exact row/owner facts, proposal
hashes, queue/execution times and any expiry cancellation. All requests are
0 JUnit executable observations, skips N/A; no maintained/broad/native/full66282,
FBZ, health/load/rewind or campaign qualification was rerun. Consumed raw outputs
and classes were removed before the docs commit. Useful `r6-products` proposals,
private adapters and compact facts remain in the existing external task directory.
Root reviews before any further maintained adaptation or workload.

### Knuckles first-Spikes-support fresh jump R7–R8 — 2026-10-21

R7 source-only inspection found no demonstrated physics omission and did not
attribute the original R5/R6 hurt receiver. Native FixBugs0 dimensions/contact
selection and shared hurt writes cannot identify a receiver from endpoint X or
static placement proximity. R6's21/21/13 remain first-hurt ring values.

R8 ran three fresh cold ordinary continuations at1ce48789e94cf509f6559b57bc7620ee828d11cb,
runtime b078-equivalent. Each preserved all42512 accepted input rows, neutralP2,
R4 first40859 and Act1 first30000; production author/loader roundtrips compared
every proposal row. All64 checkpoint42511 and actual left Spikes support42562
fields match. Entire cold-prefix public64 series also match each other.
World/Level/OM/P1/LEVEL/input/pending/fade/control guards qualify those reached
observations, with no seeding, snapshot or restore.

Actual support at42562 is left Spikes slot18/spawn10256/1888/sub112/layout392.
All policies release A at42563 while still on that support, then execute a fresh
ground jump42564 (airborne, vy=-1536, actual riding none). Policies1/2 later
repress into actual glide at42589/42601. These are ordinary input observations.

| Policy | Input after fresh ground jump | Appended inputs | First hurt / centre |
| --- | --- | --- | --- |
| 0 | Hold Right+A | 111 | 42622 /10581,1916 |
| 1 | Hold24; release1; repress | 123 | 42634 /10581,1821 |
| 2 | Hold36; release1; repress | 120 | 42631 /10581,1927 |

Each bound was1200 appended inputs and stopped on first hurt. All appended rows
retain10 rings/3 lives and released controls, with no death or flag4/Up.
Terminal actual riding is none. MaxX10581 never reaches the declared grounded/free
onward inlet beyond10784. The three-policy cap is exhausted: no safe onward
frontier, original hurt receiver UNKNOWN, and no later ring-loss/native/full-act
claim. R4's private assertion/cancellation and rejected R5 hazardous label stay
unchanged. No maintained inputs/tests/oracles/counts/windows were adapted.

The [R7–R8 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-first-spikes-support-fresh-jump-r7r8--2026-10-21)
pins source ranges, exact finite recipes, proposal hashes, queue versus execution
times, scope/expiry and the proposed original receiver/contact question.
All jobs are0 JUnit executable observations, skips N/A. Consumed raw outputs and
classes were removed before the local docs commit; useful proposals and compact
facts remain external. No fourth policy/retry/instrumentation or further workload
was performed. Root reviews the actual frontier before any later action.

### Knuckles accepted-hit ordinary recovery R9 — 2026-10-21

R9 is one separate damage-recovery lifecycle on exactce443d11a4f07d25d5b21e748337146a558c8e14;
R8 stays immutable. The maintained Act2 test rejects death/decreasing lives,
not every hurt or ring loss (`TestMhzKnucklesColdRouteCapture.java:158..162`).
No maintained oracle, original boss/FBZ/history/rewind predicate or input data
was weakened or adapted.

The entire42635-input R8 policy1 prefix and a finite480-input Right-only/P2neutral
tail were production-authored/reloaded before boot in this same request. Every
mask/boolean/payload was checked, including JUMP0x10. All64 original42511 and
42634 fields matched on current source; the original world/Level/OM/P1/LEVEL,
input, pending/fade and released-control guards qualified the already-hurt inlet.
No seeds, snapshot/restore or terrain/touch/provider re-probe occurred.

| Input | Ordinary reached event |
| --- | --- |
| 42635 | A released/Right-only; ring count10→0, lives3. |
| 42732 | Grounded but hurt, actual Spikes slot25 riding/onObject. |
| 42733 | Hurt clears; movement input remains inactive. |
| 42734 | First grounded/unhurt/free movement recovery at10383/2316, velocity12/0/ground12,0 rings/3 lives. |

Recovery is on **actual Spikes**, spawn10360/2352/sub48/layout400, not a safe
onward inlet or hazard clearance. One request executed100 new/42735 total
step/draw pairs, stopped at that first recovery and did not run a second public
new-hurt edge, death or life loss. Invulnerability remains UNOBSERVED; source120
is not a measured timer. The initial hurt receiver and native/cause equivalence
remain unknown. No full-act, boss/FBZ, history or rewind qualification follows.

The [R9 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-accepted-hit-ordinary-recovery-r9--2026-10-21)
records static versus actually executed proposal hashes, pre-boot proof, complete
selected rows and1467s queue wait/01:26 Maven time. Session91709's original
Maven/Java exited0; its unchanged launcher then exited1 on a stale old-deadline
registry-write guard. That original post-result metadata error is retained and
the record completed under Root's explicit15:34 amendment, without restart.
All observations are0 JUnit/skips N/A. Raw outputs/classes were consumed before
the local docs commit; useful proposals and compact facts remain external.
Root reviews before any later ordinary policy, adaptation or qualification.


### Knuckles recovered lower-support ordinary coast R10 — 2026-10-21

One fresh ordinary cold lifecycle at exact2d1a7e4432bff3ffc1168aebbc80a54a3a86babd
reused R9's unchanged43115-row fixed proposal. All42735 previously executed R9
inputs, R8/R4/Act1 prefixes and neutralP2 were preserved. Before boot, the
production author/loader checked all rows/booleans/masks/payloads/JUMP0x10;
after stopping it rechecked every actually executed row. All64 checkpoints42511,
42634 and42734 matched, including the actual recovered lower Spikes support.
World/Level/OM/P1/LEVEL7/1, input, pending/fade and released-control guards held.

Exactly77 new constant Right-only inputs42735..42811 ran, with one normal draw
per step. Actual Spikes slot25 riding ended at42780; all post-step rows through
42810 were airborne/no onObject/no actual rider. Ring count0→1 at42787 is an
observed counter change, not pickup-receiver attribution. First ordinary grounded/
unhurt/free non-object landing at42811 is centre10550/2417, sub59445/6956,
velocity1308/0/ground1308, angle4,1 ring/3 lives, onObject=false/actual rider none.
The stale Spikes latch remains recorded separately from actual support.

All77 new rows retain3 lives/no hurt/no death/free controls. This answers the
bounded coast/drop question and stops immediately; it does not qualify wall
admission/clearance, a safe onward route, mushroom support/bounce, native cause,
invulnerability, full-act or FBZ behavior. No named terrain receiver or original
callback was queried. R5–R9 hurt receiver remains UNKNOWN. There were no seeds,
snapshots/restores, new observers or maintained data/oracle/count/window edits.

Session33434 exited0,0 JUnit/skips N/A:543s reported queue wait,01:23 Maven time,
21.823s boot/prefix/tail. Exactly42812 total inputs ran;303 fixed rows remain
unexecuted. The [R10 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-recovered-lower-support-ordinary-coast-r10--2026-10-21)
pins commands, source/inputs/environment, proof and actual executed BK2/script
hashes, complete selected rows and explicit16:04 R10 scope. R9's old-deadline
launcher error stays historical; R10 has no command-metadata error. Raw diagnostics/
classes/roundtrip validation copy were consumed before the local docs commit;
useful actual inputs/private preimages/compact facts remain external. No second
attempt, native/broad/maintained-route run, push or integration followed. Root
owns review and any later controller or qualification scope.


### Knuckles original remaining Right-tail question R11 — 2026-10-21

One cold lifecycle at exactd62bd0796430a7f93026898199e13fa34712b157 replayed the
SAME fixed43115-row proposal, preserving all42812 actually executed R10 rows and
earlier R9/R8/R4/Act1 prefixes byte-for-byte. Production author/loader proof of
all43115 records/masks/booleans/payloads/finite pad alphabet/JUMP0x10 preceded
boot in this same JVM. All64 checkpoints42511/42634/42734/42811 matched;
world/Level/OM/P1/focus/solo-roster/LEVEL7/1/pending/fade/control guards qualified
the original ordinary landing. One public64 projection followed each normal
step/draw and its retained values supplied scalar decisions.

Of the only303 authorized remaining fixed Right-only/P2neutral inputs,44 ran:
42812..42855. Every new row is grounded/unhurt/alive/free,1 ring/3 lives,
onObject=false and actual rider none. At42854, centre10781/2414 is below the
threshold;42855 is the first X>10784 boundary at10787/2415, sub12597/8748,
velocity1535/151/ground1548, angle4, movement active. The stale Spikes latch is
retained separately. Small X backsteps42829/42836 are actual public observations;
the selected contact/receiver is UNKNOWN.

The bounded post-wall-coordinate question is reached. Individual wall contact/
admission/destruction, terrain/mushroom receiver, invulnerability/native correctness
and future safe passage remain unproved. No new owner/predicate/callback query,
controller timing, runtime threshold or maintained oracle/data/window change was
made. No whole-act/boss/FBZ/history/rewind qualification follows. R10 and every
older result, unknown and deadline stay immutable.

Session52144 exited0 without command-metadata errors:64s reported queue wait,
01:25 Maven,22.506s boot/prefix/tail;0 JUnit/skips N/A. Exactly42856 total
step/draw pairs ran, leaving259 fixed rows unexecuted. The same JVM checked the
actual executed script/BK2 prefix against every original row after stopping.
The [R11 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-original-remaining-right-tail-question-r11--2026-10-21)
pins inputs/proofs/source/environment/identity/command, complete selected rows,
explicit16:44 R11 deadline and one durably consumed budget. Useful actual inputs/
private preimages/compact facts remain external; raw diagnostics/classes/tmp and
the roundtrip validation copy were consumed under owned leases before commit.
No retry, extra policy/native/broad/maintained-route run, push, integration or
foreign cleanup followed. Root owns any later continuation or adaptation.

### Knuckles complete-fixed-tail question R12 — 2026-10-21

At exacta6f2116d, the unchanged [R11 setup](#knuckles-original-remaining-right-tail-question-r11--2026-10-21) replayed all42856 preserved inputs; all five64-field checkpoints matched.
Same-JVM production author/loader proved every fixed43115 row/finite pad/mask/boolean/payload/JUMP0x10 before boot and every actually executed row after stopping.
The sole259-row fixed Right/P2neutral tail stopped on first new hurt43006 after151 new/43007 total step/draw pairs;108 fixed rows remain unexecuted.
43005 was grounded/unhurt10869/2412,1 ring/3 lives;43006 is airborne/hurt10869/2412, velocity512/-1024/ground0, rings0/lives3/no death.
No rider/control handoff preceded the stop; actual rider stayed none, distinct from the stale Spikes latch. Hurt receiver/invulnerability/native cause remain UNKNOWN.
Session99681 exited0,0 JUnit/skips N/A:queue1s/Maven01:24/boot-prefix-tail22.093s. No retry, changed policy, maintained oracle/data/window edit or full-route qualification.
The [R12 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-complete-fixed-tail-question-r12--2026-10-21) pins source/proofs/products/command, first facts and16:54 scope; raw diagnostics/classes/tmp were consumed.
Useful actual inputs/private preimages/compact facts remain external; earlier histories stay immutable. Root owns any later workload; no push/integration occurred.

### Knuckles one jump/glide admission question R13 — 2026-10-21

At exact1f0b7c2f, accepted R11's42856-row prefix and all five64-field checkpoints matched; R12's future was not replayed. Setup is unchanged from [R11](#knuckles-original-remaining-right-tail-question-r11--2026-10-21).
One captured-signedVY>=0 policy jumped42856, released42861 after retainedVY1149, and repressed42862 after retainedair/jumping/flag0/VY1205; glide flag1 actually activated.
At42865 flag1→3 whileair=true,10823/2426,VX1036/VY0/G1036,property0/mapping204; no flag4 climb admission. All10 new rows retain1 ring/3 lives/no hurt/death/actual rider.
Raw x_sub14389/y_sub14124/wall_climb_x10027 imply no fractional/cursor semantics; stale Spikes is not actual support. Fluid/reverse/effective providers/terrain writer remain UNKNOWN.
The one attempt stopped after10 new/42866 total normal step/draw pairs, with no Up, retiming, second jump or continuation. Preboot finite-pad/prefix and post-stop executed-row production roundtrips/independent payload proofs passed.
Session79763 exited0,0 JUnit/skips N/A:queue15s/Maven01:23/boot-prefix-tail25.331s, no metadata exception. No maintained predicates/data/window changes or route/native qualification.
The [R13 Plan](../../plans/2026-10-13-s3k-playable-completion.md#mhz-knuckles-one-jumpglide-admission-question-r13--2026-10-21) pins source/input/proofs/commands/limits and17:24 workload/17:34 cleanup deadlines; raw diagnostics/classes/tmp/validation copies were consumed.
Useful exact inputs/private preimage/compact facts remain external. Earlier history is immutable; Root owns source/data review and any later workload. No push/integration occurred.
