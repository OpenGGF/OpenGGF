# S3K Mushroom Hill Act 1 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_1`, ROM `$700`, engine zone 7 / act index 0.
Incoming normal level entry; outgoing seamless MHZ2 (`$701`) with coordinate
rebase, carried objects/resources and in-level title ownership. Status: integrated
implementation reviewed historically; current campaign act certification pending.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Original direct audit found no further missing Sonic/Tails feature; later miniboss lifetime, explosion and transition-identity fixes landed. |
| Cold-reachable | Current campaign representative route not yet verified. |
| Completable | Boss/results/transition components have historical focused evidence; current cold production completion remains open. |
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
