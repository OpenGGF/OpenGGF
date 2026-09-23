# S3K Mushroom Hill Act 2 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_2`, ROM `$701`, engine zone 7 / act index 1.
Incoming seamless MHZ1 or direct level load; outgoing FBZ1 (`$400`) after the
boss's grab-wait completion. Status: integrated implementation reviewed
historically; current campaign act certification pending.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Original direct audit found no further missing Sonic/Tails gameplay feature, including seasons, custom arena, boss and FBZ exit. |
| Cold-reachable | Current direct/seamless representative route not yet verified. |
| Completable | Headless terminal boss handoff exists; current cold production completion remains open. |
| Native-accurate | Direct ROM review/local corrections; no complete native visual or frame-perfect claim. |
| Rewind/standard | Boss/controller/helper graph checks exist; breadth and lifecycle obligations remain incomplete. |

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
validation records. **No fresh MHZ execution result is claimed here.** Existing
unit/graph tests are candidates for the listed contracts, not proof of complete
act coverage. The historical 622-case focused pass and 656-case guards in the
original audit belong to their stated September 12 candidates. Historical full
runs were red/interrupted; their failures were not all attributed to a matched
baseline. They cannot be relabelled a green campaign baseline.

## Breadth ledger

| Dimension | Accepted scope / remaining evidence |
| --- | --- |
| Width | Standard requires 320, 400, 512, 640, 800 through the supported configuration. Current per-act entry/lifecycle cross-product, meaningful traversal and rewind coverage are unassessed; historical local camera checks do not fill this row. |
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
