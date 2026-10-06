# Putt Putt Paradise — Emerald Hill course coverage

The external Sonic 2 Mini Golf mod uses both full Sonic 2 World REV01 Emerald
Hill acts (registry zone 0, acts 0/1). This matrix covers the intentional golf
rules and the inherited ROM terrain/object binding, not stock Sonic 2 parity.
The signpost and egg-prison placements supply finish gates; their stock
controllers and EHZ boss/progression events are omitted. Art and sound continue
through the local ROM pipeline. See the [design](../../designs/2026-10-05-sonic-mini-golf.md)
and [delivery evidence](../../plans/2026-10-05-putt-putt-paradise.md).

## Configuration and route obligations

| Axis | Contract and evidence | Limits |
| --- | --- | --- |
| Acts and characters | `TestPuttPuttParadise.completeFreshRomRoutes`: separate fresh EHZ1/EHZ2 Sonic/Tails routes at each supported width, completed by real timed charge/chip input | No teleports, protection, comparison-row hydration or exploratory checkpoint restores in completion tests |
| Viewports | Production `WidescreenAspect` presets resolve to 320, 352, 400, 528 and 800 ×224; camera width and presentation are asserted | Generic standard's 512/640 examples are not current engine presets; these are the actual advertised menu choices |
| Native movement | Full routes use native S2 movement with the scoped retained-roll golf rule; flat putts and airborne chips use coherent native radii/contact | Other movement donors are unverified; this does not certify the stock zone standard's donor breadth |
| Team and authority | `bothFullActsCompleteWithAlternatingIndependentGolferRoutes`: Sonic/Tails, Tails/Sonic, Sonic/Sonic, Tails/Tails; independent whole-world lies and ledger, finished-player skipping, Act 2 starter reversal | One physical golfer is active; followers, CPU Tails and flight are excluded by `GolfModule.supportsSidekick()` and controller-neutral WATCH |
| Practice | Either act/character from menu; one-hole result state, free pre-commit cancellation, pause and concession | Native GPU samples use Sonic; Tails ROM art/scene and physical routes are separately tested |
| Direct online | `TestGolfOnlineIntegration` starts separate JVMs through validated creator packages; two real turns, pause ownership, host acceptance, score/scene agreement, guest permanent HOLD, guest concession, worker teardown | Continuous two-act network traversal is not separately sampled; offline complete routes and cross-act scene/resource tests cover the constituent boundaries |

## Behavioral matrix (both acts unless scoped)

| Obligation | Named production checks and independent contract | Scope / inherited gaps |
| --- | --- | --- |
| ENTRY / LOAD | `launchesFullRomEmeraldHillWithoutSidekick` compares full decoded ROM geometry and excludes only end controllers; `normalDevelopmentBootUsesStockProfileAndTitleSelectionBeforeEntryFade` uses normal scanner/trust/resolver and stock S2 launch profile, selects Tails/EHZ2/400, asserts single roster and finished fade | Four act/character direct entries; normal UI entry sampled on EHZ2, other combinations share the same provider |
| TITLE / ROM art | `titleRemixesRomArtAcrossWidthsAndRestoresPalettesOnReturn` invokes the real module title provider at 320/352/400/528/800, checks decoded art/palette banks, both portraits, emblem, full-width backdrop, bounds, steady-frame cache reuse and palette restoration on re-entry; existing `TestGolfMenu` covers every setup/input flow | Native title/setup frames are visual evidence, not stock intro-animation parity; the remixed layout uses the existing S2 title decoder |
| HOLD / timing | `aimingHoldsEveryCourseSubsystem`, `pauseFreezesFeedbackAndCancelIsFreeBeforeCommit`, `runningRowPreservesLogicalInputAndDistinctChargeAudioStamps` | Clocks, V-int/PLC, objects, animation, ledger and logical input observed; native PCM output/pitch waveform not recorded |
| OBJECT / contact | `chargesCommitOnceAndReleaseIntoNativeRolling`, `zeroSpeedSupportedCurlRemainsRolledUntilSettlement`, `realChipApexCannotSpendGroundSettlementDwell`, `actualEmeraldHillSpringKeepsGolfCurledAcrossTwoNativeBounces` | Short independent putt/chip/support tests; EHZ1 Obj41 spring executes twice; full routes traverse loop/slopes/encounters but do not certify every object subtype or moving-support combination |
| LIFE / penalty | `damageRestoresWholeNeutralCourseButRetainsStrokeAndPenalty`; real live-rewind damage boundary; model compound damage/death/finish priority, lost/watchdog and exactly-once ledger | Native damage observed; death/lost/watchdog priority contract tested in model rather than separate full-ROM terminal routes |
| EVENT / completion | Full-act fresh routes assert swept finish, COMPLETE and zero penalties; `exactSweptGateRejectsDiagonalBoundingBoxFalsePositive`; local two-act completion tests assert old history is invalid and the new timeline is capturable | EHZ2 end-camera release derives the native post-boss escape bound, without spawning the boss; miniboss/boss obligations intentionally inapplicable |
| CAMERA / presentation | `TestGolfScenePresentation` checks source/presenter ownership, ROM recipe provenance, codec bounds, priorities, scrolling, survey and start/end envelopes; live viewport change exercises 320→800 without reload; `creatorFinishFlagSurvivesWireWithoutChangingNativeViewOrCourse` | CPU parity checks both acts at 320/800; native OpenGL framebuffer samples 320 EHZ1 and 800 EHZ2; terrain survey does not widen object admission |
| PRESENT / aim guide | `aimingGuideMatchesLaunchOriginAtReferencePowerAndSelectedAngle` checks literal dot positions at 320/400/800 for Sonic/Tails, both facings, and declared flat/sloped angle inputs; full course snapshots unchanged by rendering. `guideDoesNotQueuePartiallyClippedDots` covers viewport edges. Native before/after EHZ1 views at 320 Sonic and 800 Tails include 0/15/45/75-degree right and 0/45-degree left shots | AIM-only half-power reference; guide does not predict terrain/object collisions or the final lie, and does not preview earned charge power |
| REWIND / roster | `alternateIndependentGolferWorldsAndRewindRoster`, `liveRewindReplaysNonKeyframeAimChargeFlightAndNativePenalty` compare whole course, meter, score and exact tick across held/running rows and descending seeks with resumed replay | All four local pairings; detailed non-keyframe scenario is native-width Sonic/EHZ1, not a width × donor × act rewind sweep |
| Checkpoint isolation | Old session/old act/same-key adapter replacement reject before any mutation; `opaqueCourseCheckpointGraphDoesNotRetainMutableSessionOwners`; registry identity partition excludes the engine-bound controller | Checkpoints are opaque engine-issued values; ledger remains outside per-shot rollback but inside full debug rewind |
| ONLINE lifecycle | `TestGolfProtocol` / `TestGolfTransport`: codec/value bounds, duplicate receipts, independent pause owners, reconnect replay, write deadline/backpressure, finite lazy classes, immediate creator-loader close, no remaining workers and port reuse | Direct TCP, no matchmaking or host migration; existing 30-second reconnect window |
| PRESENT / scorecard | Actual native menu/setup/aim/survey/duck/spindash/WATCH/pause/finish flag and DNF scorecard captures | Finish-preview image is a view-only preview, not traversal evidence; scorecard capture uses real concession |

## Execution

Local branch `feature/ai-putt-putt-paradise`, pre-implementation base
`d5eaa3efc24b45e7f0d25b0a3392ef61dc5352a1`. The delivery plan records commands,
completed counts, skips and broad-suite results. This matrix deliberately records
remaining axis and parity gaps; a passing mod route is not full certification of
stock Emerald Hill or the complete zone/act testing standard.
