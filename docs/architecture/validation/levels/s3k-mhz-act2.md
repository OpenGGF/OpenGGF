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
