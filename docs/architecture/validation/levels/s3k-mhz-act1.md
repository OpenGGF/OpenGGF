# S3K Mushroom Hill Act 1 coverage matrix

Canonical act `S3K_MUSHROOM_HILL_1`, ROM `$700`, engine zone 7 / act index 0.
Incoming normal level entry; outgoing seamless MHZ2 (`$701`) with coordinate
rebase, carried objects/resources and in-level title ownership. Status: integrated
implementation reviewed historically; current campaign act certification pending.

## Post Two Ambush local prototype — 2026-10-07

This separate [maintained mod](../../../../examples/hardened-s3k/README.md) uses
the native second post, not a cold whole-act route. Its supported cell is
locked-on World S3K, solo native Sonic, donor off, 320px and no-save. Other
characters, teams, widths, donors, bosses and stage detours remain outside this
prototype. It adds no certification to the stock campaign cells below.

The production sensor survey found floor at `0x1BF..0x1C6` across
`0x1D00..0x1DB0`, with clear headroom to `0x80`; the shelf drops beyond
`0x1DC0`. The room is bounded by `(0x1D10,0x100)..(0x1DB8,0x1D0)`. Native post
`(0x1D60,0x1A8)`, subtype 2, and the spring/mushroom platform beyond the room
are untouched. No original ring lies on this shelf; the plan adds one native
ring at `(0x1D70,0x1A8)` with engine-assigned identity. The fresh entrance is
`(0x1D30,0x1A8)`, the sentry `(0x1DA0,0x1AD)`, and exit `(0x1DB0,0x1AC)`.
This survey establishes authoring geometry; it does not itself prove combat.

| Obligation | Maintained check / current evidence |
| --- | --- |
| Native decode, geometry and preserved protected inventory | Both ROM-backed checks passed with zero skips. Concrete native objects, ring inventory, terrain and PLC are preserved. |
| Physical post contact and two real death reloads | Passed: real native collision activates index 2, followed by two production death reloads. No checkpoint fixture is counted as combat evidence. |
| Safe/failure paths, ordinary ring/shield/hurt dispatch | Passed: native safe-ring/danger volleys, ring loss, all three elemental shields, projectile recreation and two whole-registry 60-tick restore/forward cycles. |
| Projectile recreation and two restore/forward replay cycles | Passed: native safe-ring/danger volleys, ring loss, all three elemental shields, projectile recreation and two whole-registry 60-tick restore/forward cycles. |
| Packaged title, entry holds, pause, real checkpoint menu retry and owner abort | `TestHardenedPrototype`: seven packaged/native cases pass with zero skips after neutral entry and mandatory native-card consumption. Includes two real checkpoint menu retries, pre-contact fresh attempts, entry restore/forward cycles and owner-fault return. |
| Fresh entry/checkpoint/death/stage-return precedence and no-save attachment | 55 focused cases passed with zero skips; the corrected native fresh-camera/checkpoint method also passes. Stock death camera remains `0x1CC0`. |
| Actual GPU title/tells/transitions and final ROM PCM | Camera and entry follow-ups have actual pre-2fc GPU/PCM diagnostics: first released PLAY contains foreground and Sonic, safe/fatal paths reach native post retries, initial title controls do not overlap. Final merged-source video/device evidence remains pending. |

The [implementation plan](../../plans/2026-10-07-hardened-s3k-prototype.md) and
[blueprint](../../designs/2026-10-07-hardened-s3k-blueprint.md) preserve the
original base, contract decisions and later-stage limits. The outside-repository ledger records exact commands and corrected fixture failures.
Final captures and broad verification remain pending; these focused results are
not a full-suite or full-act certification.

## Claims

| Claim | Evidence / state |
| --- | --- |
| Implemented | Original direct audit found no further missing Sonic/Tails feature; later miniboss lifetime, explosion and transition-identity fixes landed. |
| Cold-reachable | Fresh 320px Sonic, Tails and paired routes, plus the 2026-09-27 800px Sonic candidate, reach the miniboss and MHZ2; no position/clock/ring/health seed. |
| Completable | Native Sonic, Tails and paired routes, plus the 800px Sonic candidate, complete all six boss hits, sign/results, seamless MHZ2 load and released player movement. Other configurations remain open. |
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
