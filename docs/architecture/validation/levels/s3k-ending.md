# S3K native ending routes

This matrix belongs to the [2026-10-13 playable-completion work](../../plans/2026-10-13-s3k-playable-completion.md), against integration base `5e2f51f07917d5eff8ac120007dac55327f93f32`. Native Sonic/Tails endings remain inside `$0D01`; Knuckles continues inside `$0A01` after SSZ2. Locked-on `S3Credits` is an RTS, so a generic `GameMode.ENDING` would discard the actual scene owners. Resources, rejected approaches and run history are in the [ending lifecycle design](../../designs/2026-10-13-s3k-ending-lifecycle.md).

Focused playable ending qualification is complete. The three source-confirmed presentation omissions were subsequently repaired and qualified below; whole-scene native pixel parity remains unclaimed. All seven actual incoming DEZ/DDZ scenarios execute in composed-source smoke on `aaff73747`, with seven tests and zero failures/errors/skips. The complete ordinary run records 27,200 tests, one separately attributed inherited CLI assertion, zero errors and 64 inspected skips; fresh guards pass 680 tests with zero failures/errors/skips. Qualified source is integrated and pushed on develop as `f38e9b36e`; all eight owned task worktrees and local branches are retired. The live ending index records portable reconstruction separately from historical capture identities. The evidence below distinguishes declared scene entry from an actual final-fight continuation.

## Character routes

Progress is the signed `-2/0/+2` result of `sub_5FE82`: exactly seven Super Emeralds selects `+2`, otherwise exactly seven Chaos Emeralds selects `0`, otherwise `-2`.

| Character / progress | Native incoming route | Qualified outgoing lifecycle |
| --- | --- | --- |
| Sonic / -2 | Ordinary DEZ escape → `$0D01` | Declared native scene entry → Tornado rescue → reunion → both credit parts → banner/Eggman terminal → controller Start/title |
| Sonic / 0 | DEZ → DDZ → `$0D01` | Declared native scene entry → Super rescue/revert → reunion transformation → both credits → banner/EggRobo terminal → Start/title |
| Sonic / +2 | DEZ → DDZ → `$0D01` | Declared native scene entry → Hyper rescue → AIZ Master Emerald restoration → reunion → both credits → S3 eyecatch → Start/title |
| Tails / -2 | Ordinary DEZ escape → `$0D01` | Declared native scene entry → rescue → reunion → both credits → banner/Eggman terminal → Start/title |
| Tails / 0 | Ordinary DEZ escape → `$0D01` | Declared native scene entry → native Chaos-only branch (no reunion transformation) → both credits → EggRobo terminal → Start/title |
| Tails / +2 | Ordinary DEZ escape → `$0D01` | Declared native scene entry → Super Tails rescue → AIZ restoration → reunion → both credits → S3 eyecatch → Start/title |
| Sonic + Tails / all three | Ordinary DEZ or DDZ → `$0D01` | Three declared-entry lifecycles, actual P2 retirement and both restore directions, both credits and Start/title |
| Knuckles / -2 | Actual maintained SSZ2 two-fight movie from input0 | Native final defeat → allocated flight graph → island/cloud camera → both credits → Mecha/Master Emerald terminal → Start/title at 320 and 800 |
| Knuckles / 0 | Same actual SSZ2 fight | Flight with Master Emerald → both credits → Knuckles/S3 banner → native direct Sega/title exit at 320 and 800 |
| Knuckles / +2 | Same actual SSZ2 fight | Powered flight with Master Emerald → both credits → Knuckles/S3 banner → S3 eyecatch → Start/title at 320 and 800 |

The nine `$0D01` cases terminate on actual runtime phases with a finite 60,000-input cap; all pass. The six Knuckles cases assert the maintained fight milestones at inputs3294/4016/8156, native flight allocation8704 and actual title return. Input0 executes the production initial `Process_Sprites` pass with inherited VInt410766 unchanged. External priming plus input-start1 was rejected because it lost this setup row and died2096; gameplay clocks were not shifted to fit the fixture.

## Independent obligations and completed evidence

All passing totals below have zero failures, errors and skips. ROM-backed runs use the verified original locked-on ROM and explicit absolute paths; donors also receive the original S1/S2 ROM paths.

| Obligation | Owning check / native boundary | Completed evidence |
| --- | --- | --- |
| ENTRY / LOAD / INPUT | `TestS3kEndingTransitionPresentation`, `TestSonic3kLevelInitProfile`; native white callback, `$D01` title deletion, one initial object pass and entry Start ownership | Transition3 passed in the 58-case run; init profile6 passed. Parked/routine0 ending Start cannot pause the level |
| OBJECT / EVENT / REWIND | `TestS3kEndingRescue`; Dynamic_object_RAM offsets5/45 are global SST8/48; rescue thresholds, catch, pre-entry authority and configured retirement | 36 passed in the final 48-case run. Includes thirty five-width × Sonic/Tails × duplicate/mixed/large seven-follower entry, full-world replay and genuine fresh-team reset cases |
| EVENT / LOAD / REWIND | `TestS3kEndingNativeObjectClear`; `loc_5ABC0` / `loc_5AD8E` | Two passed. Post-object native clears erase SST100 dispatch/ownership/rewind identity immediately, then retire P1's shield handle; live↔erased worlds restore and replay before another object pass |
| OBJECT / ORACLE / REWIND | `TestS3kEndingClouds`, `TestSwScrlEnding`, `TestS3kEndingSceneResources` | Combined thirteen passed: allocation-before-RNG cadence, ROM deformation, pure pre-init restore, queued AIZ/SSZ geometry/collision/VRAM and delayed redraw |
| WORLD / REWIND | `TestS3kEndingSceneState` | Five passed: two-row retained cells, sparse blank/header restore, per-band source/cell X and native palette component order |
| OBJECT / REWIND / WIDTH | `TestS3kKnucklesEnding`, `TestS3kKnucklesEndingChildren`, `TestSszAct2MechaEntry` | Flight13 and managed graph6 passed in the 58-case run; Mecha entry15 passed. Five actual preset widths preserve literal init/threshold/prior-render gates; allocation pressure, graph retirement and palette flash replay are separate checks |
| PRESENT / ORACLE / REWIND | `TestS3kCreditsState`, five-width Plane A tests, exact mapping guard and `TestPatternSpriteRendererCorruptionGuard` | Credits7 passed; corrected width/guard selector8 passed. Native glyph tile347+, 40-column plane crop, both parts and sparse restore are tested outside SAT collection |
| EVENT / INPUT / REWIND | `TestS3kPostCredits` plus ordinary Mecha sparks neighbor | Seven passed. Banner hold, charge-release dispatch boundary, subtype80 setup, managed Master fade, terminal timers/Start and ordinary subtype table behavior |
| CONTROLLER / AUDIO / REWIND | `TestSonic3kEndingSuperState` and ordinary powered-form consumers | LRZ dependency60221d5c8: eleven literal hook vectors plus43 ordinary checks passed, without ordinary ring/music substitutions |
| DONOR / RESOURCE / REWIND | `TestS3kEndingDonorSceneEntry` | Thirty passed in the 58-case run: five widths × six supported S1/S2 Sonic and S2 Tails progress0/+2 vectors; native body/tail banks, actual tail advancement, control, both registry directions and ordinary fresh-load binding |
| EFFECT / ROSTER / REWIND | Shield16, real Flock manager hydration1, Flock runtime13, auxiliary powerups3; SpriteManager neighbors | Auxiliary33 and shared manager10 passed. Captured absence and semantic membership restore in both directions; Flock's generic probe constructor permits captured PLAYER_REF hydration |
| COMPLETE LIFECYCLE / REWIND | `TestS3kEndingLifecycleCapture`, `TestS3kKnucklesEndingLifecycleCapture` | Final D01 nine passed; actual SSZ final-fight six passed. Full registry equality and forward replay at real phases, both credits and actual title return |
| RENDER / DMA / WIDTH | `TestS3kEndingSpriteDmaCapture`, `TestS3kKnucklesTerminalPixels`, mask geometry | Original Tornado GPU2, actual bad-Knuckles terminal GPU1 and mask4 passed. Final Tails departure GPU3 passed at FG4/8/12 with physicalX0/renderCopy608 preserved and actual native actors/ROM palette pixels |
| FRESH LOAD / REWIND | `TestPlayableGroundTransitions`, `TestAbstractPlayableSpriteRewindCapture` | Generic reset/snapshot19 passed; final D01 fresh AIZ loads also assert no derived ending-origin residue |

D01 registry windows cover rescue flight, AIZ return where applicable, reunion, launch, both credit parts and banner. Team cases also restore before the first native object pass and replay native P2 retirement. Knuckles windows cover final defeat, flight jump, turn, small plane, both credits, banner and the applicable Mecha-charge/S3 terminal. Comparisons retain the entire registered world; no object/sprite omissions or snapshot filters are used.

## Breadth and remaining limits

Supported presets are **320, 352, 400, 528 and 800**. Short donor, flight, credits and roster checks assert the actual camera width. The configured seven-follower scenario is a large roster, not a claimed parser maximum. Complete ending lifecycles cover all three progress values for native Sonic, Tails and Sonic+Tails, and the maintained Knuckles final fight at320/800. These checks do not claim a complete cold campaign for every donor/width/team combination.

The donor30 pass predates the final type-independent unclaimed-origin restore correction. Native Rescue36/D01 nine qualify that correction; the completed change-based smoke includes the donor class on composed `aaff73747`. The inherited ordinary S2 powered-controller RAM gap is closed by the [matching-owner follow-up](#ordinary-s2-controller-and-native-binding-rewind-follow-up--2026-10-14), with local controller/world and binding replay evidence. Continuous incoming DEZ/DDZ coverage is tracked in the [final DEZ matrix](s3k-dez-final-boss.md) and [DDZ matrix](s3k-ddz.md), with SSZ continuity in [SSZ2](s3k-ssz-act2.md). The [completed composed-source qualification](../../plans/2026-10-13-s3k-playable-completion.md#completed-composed-source-qualification) records the bounded smoke/guard result and inherited CLI assertion; develop source delivery and owned-worktree cleanup are complete.

Native art/maps/palettes and moving output are verified at specific producer boundaries, not by whole-scene BizHawk pixel parity. Literal controller tests cover audio request absence; the retained films are silent and do not qualify rendered audio. No trace parity sweep or release-only full suite was run for this feature.

## Repaired native presentation owners — 2026-10-13

The follow-up starts from integrated `772092298`, merged into the ending branch
as `79cb117ff` with no tree difference before the repair. Production Java is
`70c0990389436562191a211c342493331496bfac6c5a9a4957216fabe1a4f35a` (3892 paths,
path-NUL-bytes-NUL SHA256); final production/test Java is
`46805c543ea2d319932b854f58dc3e7c3e38025e900a80a760ac9fdc5f5a95d4` (7653 paths).

| Changed owner | Native contract and qualified check | Observed result |
| --- | --- | --- |
| IslandLift family and resident art | `loc_5DB76` allocates 2 subtype `$42` pieces and queues ROM `$160AAA` at tile `$2FF`; `loc_5DD6A` conditionally adds 18 in native table/allocation order. Map `$6068E` has 27 frames; the module has 62 patterns. All seven subtype dispatches retain their raw words/fractions, delays, priority and native even-VInt admission | `TestS3kEndingIslandLift`: 22 vectors, including five widths, fragmented lowest-free allocation, complete recreation/replay and per-piece 9-bit SAT/X 0→1 projection. The mapping and PatternSpriteRenderer corruption guards pass |
| Independent Master Emerald palette helper | One white-ready admission per rescue/AIZ director; signed FAC1 selects engine line 2/line 1 cells 14–15, with positive-progress/rotation-disable/zero-mode gates and captured byte timer/cursor | `TestS3kEndingPaletteOwners`: 3 literal channel/timer/wrap/helper registry vectors; the existing Knuckles palette consumer also passes unchanged |
| AIZ restoration hold AnPal | The event-owned fresh bank retains word F632/F634 and secondary F650/F652 state. FG 24→28→32 and 32→36 keep native same-dispatch fallthroughs; hold resets only the owning counters and leaves the secondary offset intact | Two actual Super Tails hold at 320/800 cases assert both channels, independent helper, inactive ordinary LEVEL_STARTED gate and full-world replay |
| Actual producer pixels and lifecycle | New `TestS3kEndingPresentationCapture` in the level package selects actual 2+18 graph phases and a transparent prepared mask; the next ordinary VInt publishes its sampled SAT/CRAM. Separate wrapped-Y vectors use natural even-VInt preparation and publication | Eight GPU cases cover 320/800, Chaos/Super admission, ROM module pixels, graph replay and wrapped-Y visibility. The affected nine complete D01 lifecycles pass through both credit parts and actual Start/title |
| Structural helper ownership | Default compact `StatefulValueCodec` captures the final mutable `S3kRawAnimation.State`; the independent palette helper supplies an explicit immutable object extra | Helper-state coverage 1, object coverage 1 and transient-reference 2 guards pass |

There are 50 distinct focused checks across the completed runs, with no unresolved
failures/errors/skips. Initial short 29 had two fixture failures (subtype `$42` X `$180`
projected to native X `$200` and was correctly hidden). Initial scene 17 had four pixel
oracle failures; its complete lifecycle 9 and AIZ hold 2 passed. Corrected 20 passed
all 12 clock vectors and wrapped-Y 2, leaving only two wide moving-edge pixel
premises. Final actual-scene 4 passed with producer-correct SAT/CRAM sampling and
transparent prepared-mask selection; structural 4 also passed. The unchanged
passing slices were preserved rather than repeated. Commands and negative
attribution are in the [dated repair record](../../designs/2026-10-13-s3k-ending-lifecycle.md#native-presentation-repair--2026-10-13).

Full-capacity `Create_Continue_Sprite` has no allocation-failure branch and can
overwrite occupied native RAM. Typed safe rejection retains only the admitted
prefix, without retries/healing. This inherited missing emulation is unsupported
and unqualified here, not an accepted deviation. Ordinary and fragmented stock
graphs are qualified. No cold campaign breadth or whole-scene BizHawk parity is
added by this local presentation repair; combined validation remains the lead's
obligation.

## Deferred deformation resource follow-up — 2026-10-13

The first FBZ/CNZ errors in the coordinator's frozen `7d0a9acbb` smoke were a
shared resource-boundary regression, not new scroll arithmetic differences.
The unchanged FBZ method passes on baseline `2e992da7d`, whose test/provider/
interface bytes equal original `5e2f51f07`. Sixteen explicit no-ROM provider
methods share this registration contract. Ending-only immutable banks now load
on relevant use, validate together before publication, and fail before native
RAM/output mutation; pure first rebuild remains tick-free.

Focused contract 32 passes with zero failures/errors/skips: sixteen provider
clients, six first-use/failure cases, four ending math/RAM checks and six generic
parallax rebuild checks. Affected consumers also pass 23/0/0/0: D01 lifecycle 9,
scene handoff 1, clouds 2, scene state 5 and actual IslandLift/AIZ GPU 6. All 55
follow-up checks have zero failures/errors/skips; combined smoke remains lead-owned.
See the [dated follow-up](../../designs/2026-10-13-s3k-ending-lifecycle.md#deferred-ending-deformation-resources--2026-10-13).
Historical media hashes remain their actual earlier sources; no new film or
latest-integrated pixel-parity claim is added by this immutable-data change.

## Unimplemented native presentation breadth — 2026-10-13

**Historical audit, superseded by the qualified repair above.** The following
records the c210 source, not the current repaired implementation.

A bounded independent source review after feature `c2103d5f7` (coordinator integration `d63bfe397`) confirmed three omissions. They were unfinished parity work, not accepted deviations or newly observed playable blockers. No tests were repeated for this documentation audit. Existing scene progression, registry replay, both credits and terminal/title qualification remains valid within its recorded scope.

| Missing native owner at c210 | Native behavior and affected scope | Source evidence at the audit |
| --- | --- | --- |
| IslandLift ending decoration family | `loc_5DB76` allocates two continuation subtype `$42` pieces at `word_5DBB6` and queues `ArtKosM_IslandLiftGfx` at tile `$2FF`. `loc_5DD6A`, unless Player_mode3 or progress < 0, later allocates 18 pieces: 6×`$41`, 2×`$43`, 4×`$44`, 2×`$45`, 2×`$46`, 2×`$47`. `loc_5EDAC` owns their mappings, raw animation, priority and screen/world projection | Rescue hold/departure routines advance the native signals and timers but allocate no corresponding IslandLift graph or art upload. Existing terrain redraw/cloud owners do not replace this animated sprite family |
| Rescue and AIZ-return Master Emerald palette helper | `loc_5D9EE` admits one `loc_5DF58` owner when the normal white color is ready. For progress>0, nonzero `_unkFAC1` selects `off_7DD5A` and cells 14–15 of native `Normal_palette_line_3` when positive, or `Normal_palette_line_2` when negative. `loc_5E08A` sets the negative mode for AIZ return; clear writers stop it. These native line symbols are zero-based engine lines 2/1 | `S3kEndingMasterPaletteState` is used only by `EndingKnucklesFlightObjectInstance` and writes fixed engine line2. The rescue/AIZ director has neither this allocated helper nor its `_unkFAC1` selection/cursor lifecycle. The existing Knuckles flight cycle is implemented and is not the reported omission |
| AIZ water palette animation during restoration hold | `loc_5AD56` calls `AnPal_AIZ1` every ordinary screen-event pass until Events_fg_4 signals the fade. The preceding fade completion clears the relevant native palette-cycle gates/counters | Ending FG32 advances background scroll and checks the signal but never invokes an AIZ palette owner. Ordinary `Sonic3kPaletteCycler` selects AIZ only for zone0; resource handoff retains internal `$0D01` and does not rebind that cycle |

The subsequent repair supplies ROM-backed allocation/art/animation and palette state with the native admission, dispatch, stop and rewind ownership, plus independent producer/phase checks and moving/native-window evidence. The older c210 Tornado, mask, terminal and departure pixel cases did not assert these effects; the new producer cases above do. See the [resolved work record](../../../status/s3k-known-bugs.md#native-ending-presentation-gaps-resolved) and [dated decision/evidence record](../../designs/2026-10-13-s3k-ending-lifecycle.md#read-only-presentation-audit--2026-10-13).

## Moving evidence

Durable sources are outside the repo at `~/Videos/OGGF/s3k-finish-endings-2026-10-13/`, with one `ending-capture-index.json` recording input/ROM/jar/CSV/video identities and exact commands. Sonic incomplete320 is a declared `$D01` entry with20000 neutral inputs/every4; Tails Super800 is a declared entry with30000 neutral inputs/every8. Knuckles incomplete/Super800 execute the maintained BK2 from input0, then neutral to30000, captured from8100/every8. Native60-input playback is encoded at15fps or rational15/2fps. Observed title inputs are15101,15546,24082 and23357 respectively; the one-input difference from an older Knuckles incomplete movie remains unattributed.

The original missing-plane movie is rejected: identity-only resident-art caching missed in-place DMA. The first Tails b2af film is also retained as negative evidence because physical camera0 versus renderCopy608 filled the center with mask grain. The corrected final Tails film uses source d1a298ec / immutable jar ed152e71, preserves the identical state CSV and visibly clears the native center through departure.

The historical61-second silent highlights reel under `highlights/ending-reel-render-copy-d1a298ec789d` includes the corrected departure, AIZ restoration, reunion, credits, Knuckles flight/Mecha terminal and title. Its EDL retains exact native-input↔source-time mapping and honest scene-entry/final-fight labels; it does not present editorial cuts as a cold campaign. FFprobe, full xerror decode and representative temporal frames qualify the media artifact. Bounded smoke/guards complete on composed `aaff73747`, with the separately attributed inherited CLI assertion recorded above. Develop source delivery and owned-worktree cleanup are complete; portable reconstruction follows the live ending index, and these historical films retain their original capture-source identities.

Two 85-second presentation excerpts now supplement the historical films under
`presentation-repair-70c099038943/`: Sonic Chaos 320 and Tails Super 800, each 6000
neutral inputs, captured from 900/every 4, native 60-input speed encoded 15fps at
nearest-neighbor 2x. They show the changed IslandLift departure and Super AIZ
restoration/hold, not a cold campaign or terminal continuation. The external
index preserves the older jar/source/video identities and adds the immutable
repair jar ed7dc650 plus exact commands, CSV and media hashes. Both full xerror
decodes and temporal still inspection pass.

The latest61-second silent reel is
`highlights/ending-reel-presentation-f664a1e1a/s3k-ending-highlights-presentation-repair-provisional.mp4`,
under the same durable task directory. It has12 chapters at1600×520 and15fps,
915 frames; output SHA-256 is
`5f5cef9fabe730b58091c0f3b2b94a233b640edc902a27a6f783d3749ec2ecde`.
Chapters1–6 use the qualified70c099/ed7dc650 Sonic Chaos320 and Tails Super800
Island/AIZ presentation excerpts; chapters7–12 retain the b2af/a302
credits, Knuckles and title sources. Its filename remains historical. The
immutable source-index snapshot, original capture identities and mixed-source
EDL remain unchanged. It shows the three repaired presentation owners without
claiming an uninterrupted cold campaign, whole-scene native pixel parity or
latest-integrated pixel parity. Portable reconstruction uses the final pushed
develop head recorded in the live `ending-capture-index.json`; historical
source commands and hashes remain available beside that recipe.


## Remaining-parity follow-up — 2026-10-14

Knuckles' routine2 quiet-wind omission is repaired: `loc_5E80A` requests `$CE`
before animation and timer expiry when `Play_SFX_Continuous` admits the native
VInt low nibble. Three independent new request-clock, expiry/adjacent-routine
and owner restore/replay cases fail on unchanged production and pass after the
repair. The complete affected class passes16/0/0/0 at08:15:18 BST; exact
commands and source context are in the [follow-up record](../../plans/2026-10-13-s3k-playable-completion.md#quiet-wind-repair-qualification).
This does not qualify rendered PCM.

The rescue/return flame, propeller, passenger, Master Emerald and fixed-plane
pilot are now managed native SST owners. Birth dispatches, native screen/world
coordinates, physical parent-slot reuse, small-plane flags and delayed delete
owners participate in rewind. The composed ending graph qualifies77 distinct
focused cases across the source-frozen75-case invocation and bounded corrections;
there is no claimed single pristine77-case invocation. Both native sprite-clear
boundaries retain the global catcher pointer, including two complete registry
restore/replay cycles. Exact commands and corrected premises are in the follow-up
record. The independent continuation core qualifies41 distinct cases, preserving
SST93 residue/identity and execution cursor without unload. Managed ending graph continuation pressure qualifies17 distinct real-child
cases: corrected15-case19591 (08:35:55 UTC,31.519s Maven) plus two unchanged
pilots from43234. Core7093 separately passes11/0/0/0 (08:38:14 UTC,26.058s).
The fixed plane8/Master48 and Knuckles-only groups are excluded as impossible
SST93 continuation victims by native placement/Player_mode3 gates. Actual
full-table loc5DB76/loc5DD6A trigger dispatch and downstream scene composition
remained separately unqualified here; the [2026-10-15 native products](#native-declared-products-and-remaining-obligations--2026-10-15)
qualify them for the declared Tails/+2 and Sonic/0 scenes. See the [real-child pressure evidence](../../designs/2026-10-13-s3k-ending-lifecycle.md#actual-managed-ending-graph-under-continuation-pressure-2026-10-14).
Whole-scene native composition, NTSC/PAL branch breadth, donor incomplete
progress and rendered audio remain pending in the broader follow-up.

## Production presets and PAL lifecycle follow-up — 2026-10-14

On `1c8cfbe33` plus test-only follow-up, donor entry now has45 passing cases:
S1 Sonic, S2 Sonic and S2 Tails at progress -2/0/+2 across all actual presets
320/352/400/528/800. The production aspect resolver, camera, usable native scene
body/tail banks, progress-specific transformation, both binding restore
directions and fresh-load donor restoration are asserted. Hyper Sonic/Tails
AIZ return has ten passing actual-preset handoff/replay cases. This replaces
raw-width setup as the preset evidence, while retaining its historical scope.

Nine literal PAL table/delay/two-part/two-cycle cases pass. Twelve separate
REGION=PAL lifecycle cases pass through production credit selection and actual
title return: nine declared D01 Sonic/Tails/team entries and three Knuckles
final-fight continuations. The latter retains its historical410766 declared
clock. All76 focused cases have zero failures/errors/skips; the
[exact commands and limits](../../plans/2026-10-13-s3k-playable-completion.md#production-presets-incomplete-donors-and-pal-credits-qualification)
record completion times, source and queue waits. No native framebuffer, PCM,
50Hz wall-clock or additional cold-campaign certification follows from these
local cases. The first native 50 Hz product is in the
[2026-10-16 PAL section](#native-pal-50-hz-sonic2-product-ending-pal-lane-2026-10-16). The ordinary S2 controller RAM obligation is qualified separately below.

## Ordinary S2 controller and native binding rewind follow-up — 2026-10-14

`Sonic2SuperStateController` now captures its core state, drain countdown and
palette state/frame/timer, preserving shipped signed cadence and the `$F8`
residual palette byte. Revert resolves current world-owned stars rather than a
stale Java reference. Immutable ordinary animation scripts survive revert and
are reconciled silently on restore. The existing public snapshot shape is
unchanged.

The native binding reapplies each matching controller's RAM after attachment or
detachment. Its scene controller preserves captured player physics before any
powered writer runs, rejects the ordinary owner's unset tier, and returns to
its own fresh-construction RAM when re-entering from an inactive snapshot.
Active snapshots retain their actual progressed RAM. Constructor RAM is a
derived resource baseline, never comparison data or a copied future scene.

A coherent64-case focused run passes with zero failures/errors/skips: ordinary
S2 world/replay3; donor/native binding48 (the45 preset/progress rows plus
TRANSFORMING, actual SUPER and actual progressed-scene→fresh-entry vectors);
native ending-controller11; ordinary jump trigger1 and debug grant1. All new
world/binding cases use two complete registry cycles and real forward frames.
The SUPER entry vector declares an incoming running HUD timer before native
dispatch; it is an owner-boundary test, not a cold donor campaign.

Completed09:26:06 UTC (10:26:06 BST), Maven32.626s, no queue wait, on `99bfb689d`
plus this repair. Existing KiS2/default/spindash neighbors also passed in a
separate bounded24-case run. Exact commands, failed candidate causes and
coverage limits are in the [dated record](../../plans/2026-10-13-s3k-playable-completion.md#ordinary-s2-ram-and-matching-controller-binding-qualification).
This is rewind/binding qualification, not stock S2 route, full native
framebuffer/PCM or cross-product cold-campaign certification.

## Paired KosM child and VInt publication qualification — 2026-10-14

Qualified transfer `df8a26e936e54ae5503078e12d0ff9589846c92d` waits for
the actual submitted direct-child FIFO receipts, then queues physical pattern
DMA for the next supported VInt. Equal source/destination descriptors retain
their admitted native FIFO position through copied restore; a preceding
unpaired entry cannot borrow a later receipt. Claimed parent payloads, pending
DMA and presented memory have distinct captured owners. Public queue snapshot
and DMA interfaces retain their shapes. All paired production consumers use
that owner; raw and synchronous unpaired operations retain their contracts.

Worker56679 passes18 checks, including actual320/800 world/GPU boundaries.
The final affected consumer selection qualifies289 distinct checks across
248 unchanged passes in4922 and41 corrected cases in69069, not a single
289-case green invocation. The earlier38 missing S1/S2 ROM prerequisites and
three physical-camera fixture assumptions were corrected without runtime
changes; final69069 passes60/0/0/0 with all three verified original ROMs.
Initial archive-readiness and duplicate-FIFO negatives are preserved in the
[owning design](../../designs/2026-10-13-s3k-ending-lifecycle.md).

The native/engine v7 spots independently confirm actual child completion and
VInt publication: old Master art persists through outer3 and the new payload
publishes at4. Initial retained native VRAM differs from direct engine scene
entry, and late Tails scroll/pixel differences remain under investigation.
No initial-ingress, whole-scene RGB, PAL50, PCM or SAT-overflow acceptance is
claimed. A separately discovered private-record comparison defect requires
helper-corrected composed replay before full-registry equality credit; receipt,
DMA-byte and published-pixel observations are independent evidence.

## Native declared products and remaining obligations — 2026-10-15

Seven of the nine D01 finite NTSC 320 products now match independently captured
native references at all eleven declared checkpoints (native poll p ↔ engine
outer p+1, `declared-native-spots-*-320-v1`): zero 3-bit-level pixel mismatches
and identical SST slot/x/y/mapping inventories. The Tails/Super and Sonic/Chaos
pair drove the repairs; the two Hyper Sonic products keep one attributed residual. The
closing repairs are exact FIFO costs for the reunion jobs, the `loc_5AE90` →
`loc_5AEA8` → `loc_5AED4` fall-throughs and `loc_5B204` writing only
`Camera_Y_pos`; native RAM independently dates both credits starts. Details,
rejected intermediates and baselines are in the
[ending lifecycle design](../../designs/2026-10-13-s3k-ending-lifecycle.md#late-reunion-gate-credits-fall-throughs-and-the-finite-products-2026-10-15).
Raw RGB differs by construction (native GPGX 34×level, max 238; engine
`(level×255+3)/7`, max 255); no image is normalized and no original-RGB
acceptance is claimed.

| Obligation | Disposition | Proven | Not proven |
| --- | --- | --- | --- |
| `loc_5DB76`/`loc_5DD6A` full-table dispatch and downstream composition | Qualified for the two declared products | Native poll 3071 holds all 20 IslandLift SSTs (2 `$42` from `word_5DBB6`, 18 from the six `word_5DDD2`…`word_5DE12` tables) with engine-identical slot, x, y and mapping in both products; whole-SST and 3-bit pixel agreement at every checkpoint. `TestS3kEndingIslandLift` (22) and the continuation-pressure cases pass | No native Knuckles or progress -2 product. The director-owned dispatch is covered by headless tests over declared IslandLift pressure (below), not by a cold-campaign occupancy |
| Tails/+2 and Sonic/0 declared products | Qualified as declared scenes | 22/22 checkpoints, zero level mismatches, all whole frames viewed | Cold campaign identity, original-RGB bytes |
| Sonic -2, Tails -2, Tails 0, Sonic+Tails -2/0 declared products | Qualified as declared scenes | Newly captured native references (unchanged v1 adapter, immutable host); engine `6ace9000f`: zero 3-bit-level mismatches and identical SST inventories at all 11 checkpoints each | Cold campaign identity, original-RGB bytes |
| Sonic +2 and Sonic+Tails +2 (Hyper Sonic route) | Open, attributed to native CPU time | Checkpoints 1–4095 match exactly; SST identical at all 11. Native v5 ledgers (`B/native-cloud-transition-{tails,sonic,team}-super-320-v5`, Tails/+2 reproducing the v1 control) queue the same three Kos entries at LF 4402 and the module chain at 4403 on all three routes; only the FIFO head `$1D17A6` (fingerprint `5b4280f6…`) finishes in 3 polls on the Sonic and team routes (`Kos_decomp_queue_count` 3→2 at LF 4405) against 4 on Tails/+2 (LF 4406). Every later service step is identical, one frame earlier, so the `loc_5AE0A` gate opens at LF 4443 instead of 4444 | 171/194 level pixels at 5119/6143 (`HScroll_table+0` `$14F800`, `Camera_Y_pos` 517/1345 against engine `$14F000`, 516/1344). `Process_Kos_Queue` runs in main-loop idle time and resumes from a VInt bookmark (sonic3k.asm:2840), so the same job's duration depends on how much of those frames the route's objects consume. The FAST manifest holds one cost per fingerprint (4, measured on Tails/+2, which also matches Sonic/0 and the other five products); a route- or character-keyed cost would be a fitted constant (runtime invariant 3) and was not added. Closing it needs a CPU-cycle budget model for the Kos queue. The 2026-10-15 native measurement ([lifecycle design](../../designs/2026-10-13-s3k-ending-lifecycle.md#kos-idle-budget-measurement-of-the-hyper-sonic-residual-2026-10-15-opus-kos-budget-lane)) confirms the per-object idle-budget cause and rules out a route-free model; tracked in [S3K known bugs](../../../status/s3k-known-bugs.md#ending-reunion-kos-service-count--per-object-cpu-budget-open) |
| Knuckles products (SSZ2 `$0A01`) | Open | Lifecycle 9 and terminal pixels pass. Credits against the native complete run (below): with the native inlet declared, `Camera_Y_pos`, all 32 Plane A rows and every cloud SST equal native at all 52 state-anchored spots (32 during the text, 20 after it); `8f602642c`, `9b0c85dd0`. Background routine 4's starfield and sea Plane B (`516ca64f1`). The terminal banner's `word_5F260` row and TM, `loc_5FBA0`'s pink flash, `loc_5FF62`'s logo fade and SSZ2's Plane A V-scroll from `Camera_Y_pos_copy` (kterminal lane): all 20 credits-done spots are pixel-identical with the native `HScroll_table` longword declared, and the credit text sits where native shows it at all 32 text spots | With the native `V_int_run_count` `$66141` declared at the flight edge, all 12 credits-one spans are pixel-identical to native; the earlier "one V-int late" clouds came from a `$66142` declaration ([presentation section](#knuckles-credits-v-int-inlet-teleporter-pad-and-zero-copy-pass-presentation-lane-2026-10-16)). The S3 eyecatch fades are fixed from `loc_5FF62` ([2026-10-16 terminal section](#native-pal-50-hz-sonic2-product-ending-pal-lane-2026-10-16); [known bugs](../../../status/s3k-known-bugs.md#knuckles-credits-and-terminal-residuals-resolved)); no declared native product; `loc_5B1A2` there clears only `Camera_Y_pos` and is not separately compared |
| Team retirement / full-frame replay | Passing on Root `e0a136b83` | `TestS3kEndingRescue` 36/36 and `TestS3kEndingLifecycleCapture` 18/18 (0 skipped), one queued focused run on 2026-10-15 05:58–06:01 UTC, Root plus the test-only wrapped-particle correction | The 30 roster and 6 team replay failures recorded on `be2a3f6d3`/`2b9eb431d` do not reproduce. Which composed commit cleared them was not bisected |
| Wrapped particle presentation | Resolved: test premise corrected | Bisect (first-parent `5b2ff8e78..da27f4f3d`) puts the 0/398 failure at `90897f41d`, the merge of `9a7070930`, which added native Plane B high-priority sea composition. Subtype `$41` is low priority (`loc_5EDFE` clears art_tile bit 15). Native Sonic/0 spots at polls 1279, 1398–1401 and 1535 (`B/declared-native-spots-sonic-chaos-320-islandlift-v1`; engine input 1400 is poll 1399) hold 512/512 B-high opaque sea pixels under the old particle box (screen 224–255 × 188–203) and B-low sky at 40–71 × 40–55, so the VDP hides the old vector. The visible wrapped-Y cases now use the same threshold vector in the sky (Y `$FEB0`), and new `wrappedLowPriorityParticleIsCoveredByTheHighPrioritySea` (320/800) asserts that the old vector shows no particle pixels. No production change | The engine's B-high composition between native spot checkpoints is covered by the declared-product comparisons, not by this vector |
| PAL | Open; native tier for Sonic/+2 | Literal PAL tables and REGION=PAL lifecycles (earlier sections). [Native PAL Sonic/+2](#native-pal-50-hz-sonic2-product-ending-pal-lane-2026-10-16): raw RGB byte-identical at polls 1–3071; LF, V-int and RNG equal for 13,888 frames; native credits last 7,415 frames, as in the engine. First divergence at LF 3778: the AIZ1 Kos modules finish four frames earlier at PAL (`loc_5AC70`). Owned by the Kos CPU-budget model | Eight other Sonic/Tails PAL products, Knuckles at PAL, PCM and PAL pacing. Pixels from poll 4095 inherit the Kos phase. The S3 eyecatch palette and tail are fixed: Tails/+2 NTSC is raw-RGB identical at every terminal spot |
| PCM / audio | Open (rendered PCM) | Request tier on all nine declared NTSC products: fade and credits requests on native-anchored frames; the credits request now loads and advances song `$32` ([ending audio request ledger](#ending-audio-request-ledger-and-credits-admission-ending-audio-lane-2026-10-16)). Output-stage component tier only: the opt-in native-compatible PCM stage matches GPGX source vectors byte for byte on an identical synthetic FM/DAC strobe schedule ([component tier](../audio/2026-10-16-native-compatible-pcm-output.md)) | No ending window compared. The first real window (AIZ1, Sonic/Tails emeralds movie) measured a structural cadence defect and an unmatched warm-up, not parity. There is no native per-strobe schedule. The stage excludes PSG, has no MAME core (Knuckles) and models NTSC only. Knuckles and PAL request dates are missing |
| SAT overflow / original RGB | Open | — | Native 20-sprite/320-dot overflow still needs a real witness; byte-RGB acceptance awaits the LIMITED colour profile |

### Knuckles credits against native frames (campaign-visual lane, 2026-10-15)

The cold Knuckles campaign's credits (`lrz-knuckles-cold-hpz-ssz2-ending-320.bk2`) were
compared with native frames of `runs/s3k-knuckles-complete-superemeralds` selected by
`$FA86=4`, `$FA84>2` and `$FAAE` 0/4 (inputs 422795 and 428089) plus 150-frame spans;
engine frames use `LIMITED`/`RAW_RGB`. Pictures, RAM/VRAM/CRAM/SST dumps and provenance are in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/campaign-visual-v1/`.

- **Black line across both parts (engine).** Natively the Plane B island box (transparent,
  256×128) and `loc_591D6`'s `Map_KnuxEndingIslandMask` squares both start at line 48
  (`Camera_Y_pos_BG_copy $D0.4000`, mask `y_pos $F0`). The engine froze the BG copy at `$D2`
  because the flight init omitted `loc_5E70E`'s `clr.w (Screen_shake_flag).w` (native `$EECC`
  drops from `$FF00` at input 419848), and projected the screen-space mask with the physical
  camera instead of the render copy. Fixed in `13e2e9025`.
- **Blank part two (engine).** Native shows every part-two name; the engine showed none.
  `sub_5B18E` scrolls `Camera_Y_pos` to `$1CA0` over a 32-row Plane A ring that the VDP wraps
  every 256 lines, but the engine tile pass discarded rows past the SSZ2 layout height.
  The credits descriptor owner now makes the foreground pass wrap
  (`d10afbcdc`).
- Matching after `13e2e9025`: part-one text, island rows 63–199 in both, cloud sprites.
  Part-two text after `d10afbcdc` is measured in the credits-lane follow-up below. The native run's Super-emerald progress ends in the S3 eyecatch; the cold movie's
  banner and Mecha terminal have no native counterpart in this comparison.

### Knuckles credits text, plane ring and clouds (credits lane, 2026-10-15)

Same native spots as above, re-measured by credits state from the RAM, VRAM and SST dumps
rather than by eye. The ROM owner is `sub_5B18E`: `loc_5B204` (sonic3k.asm:121520–121580)
adds `_unkFAC8` to `Camera_Y_pos` every frame. On each 16px crossing it clears the row pair
at `(Camera_Y_pos+$E0)` mod 256 in the 32-row Plane A and writes at most one line whose
descriptor high byte matches that position. `sub_5B514` takes the speed from
`CreditsText_ScrollSpeed` and the start delay from `CreditsText_ScrollDelay`, keyed by
`_unkFA88`. `sub_5FE82` sets that byte to 2 when all seven Super Emeralds are held.

- **Scroll speed (capture inlet).** Native `_unkFAC8` is `$BC00` (`_unkFA88`=2, all
  `Collected_emeralds_array` bytes 3). The cold movie collects no emeralds, so the engine
  correctly took the `$C8` column. That is the whole part-one drift: 23px at span 3, 77px
  at span 11. The capture driver now declares the native emerald state after Mecha's defeat,
  before Knuckles' flight runs `sub_5FE82`.
- **Plane ring (engine, `8f602642c`).** The foreground pass wrapped the retained credits
  plane at the SSZ2 layout height: 19 chunks, 304 rows, and 304 mod 32 = 16. So lines at
  world Y `$980`–`$12FF` and from `$1C80` showed rows 128px from native. That is the
  part-two "same block wraps back in" report. The override now reports a 32-row ring and
  the pass builds exactly that.
- **Credits start (engine, `9b0c85dd0`).** `loc_58B7C` (sonic3k.asm:117827–117839) falls
  into `loc_58B9E` and runs `sub_5B18E` on the pass that finishes the redraw. Native shows
  `Events_routine_fg` `$10` with `_unkFA86` 2 at input 422608. The engine started a pass
  later, which moved every credit line and the credits-relative cloud picture by a frame.
- **Clouds and the part-two island (engine, `9b0c85dd0`).** `loc_58BBC` (sonic3k.asm:117846–117875)
  tests `_unkFAAE`, not completion. When `Camera_Y_pos` reaches `$1660` it clears `_unkFAB9`,
  lifts `Camera_Y_pos_BG_copy` by `$100` and loads `Pal_Ending2`. The engine waited for
  completion at `$1CA0`, so this never fired. At credits-done native holds BG routine 4,
  `_unkFAB9` 0 and BG Y `$80.C000`; after the fix the engine holds routine 4, no clouds and
  BG Y `$80`.
- **Flight-to-credits timing (capture inlet).** From flight init (native input 419847,
  `V_int_run_count` `$66141`) to FG routine C the engine took 2672 frames and native 2753.
  Every flight leg matched to within the 25-frame native snapshots. The gap is the
  `loc_5908E` hand-off: the second swing zero crossing after the negative `Events_fg_4`
  comes at native +1513 and engine +1426. Replaying `Gradual_SwingOffset($2000,$4A)` and
  the controller gate (the engine's own model) from the native SST at +1150/+1175/+1200
  predicts +1513 exactly. So the swing phase and `_unkEE9C` are inherited from the route's
  SSZ2 fight, which the cold movie does not share. For verification only, the driver
  declares that controller state and the counter at flight init. The engine then matches
  native `Camera_Y_pos` on all 1420 per-frame native log rows (+1352…+2771), and the
  hand-off, `$2A0`, FG `$C`/`$10` and the credits anchors (`V_int_run_count` `$66CC5`).

Before is the lead's render on `4657a7266` with the cold inlet. After is `9b0c85dd0` with
the declared native inlet (super emeralds, swing/`_unkEE9C`/counter at flight init), in
`engine-k-credits-fix2-aligned/`. Plane A rows compare columns 0–39, the 40 that
`sub_5B2D6` clears, and treat pattern 0 (the blank VRAM tile) as empty. Cloud SSTs compare
x, y and mapping frame of every `loc_5FA8C` slot. Pixels compare the 320×224 playfield.

| Spot | Native input | Native `Camera_Y_pos` | Before: Δ px | Before: yellow IoU | Before: frame px equal | After: Δ px | After: Plane A rows | After: cloud SSTs | After: frame px equal | Native VSRAM = copy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| one 0 | 422795 | $30 | +0 | 1.00 | 96.3% | 0 | 32/32 | 7/7 | 100.0% | yes |
| one-span 1 | 422945 | $9E | +7 | 0.18 | 91.0% | 0 | 32/32 | 6/6 | 100.0% | yes |
| one-span 2 | 423095 | $10C | +14 | n/a | 94.6% | 0 | 32/32 | 5/5 | 99.4% | no |
| one-span 3 | 423245 | $17A | +22 | 0.00 | 86.1% | 0 | 32/32 | 6/6 | 97.5% | no |
| one-span 4 | 423395 | $1E9 | +28 | 0.00 | 83.4% | 0 | 32/32 | 6/6 | 97.2% | no |
| one-span 5 | 423545 | $257 | +35 | 0.00 | 93.4% | 0 | 32/32 | 6/6 | 99.5% | no |
| one-span 6 | 423695 | $2C5 | +42 | 0.00 | 94.3% | 0 | 32/32 | 6/6 | 99.2% | no |
| one-span 7 | 423845 | $333 | +49 | 0.00 | 93.0% | 0 | 32/32 | 6/6 | 100.0% | yes |
| one-span 8 | 423995 | $3A1 | +56 | 0.00 | 92.9% | 0 | 32/32 | 5/5 | 100.0% | yes |
| one-span 9 | 424145 | $40F | +64 | 0.00 | 90.9% | 0 | 32/32 | 6/6 | 98.1% | no |
| one-span 10 | 424295 | $47E | +70 | 0.00 | 90.6% | 0 | 32/32 | 6/6 | 98.1% | no |
| one-span 11 | 424445 | $4EC | +77 | 0.00 | 88.9% | 0 | 32/32 | 6/6 | 98.2% | no |
| two 0 | 428089 | $F60 | +0 | 0.00 | 92.9% | 0 | 32/32 | 5/5 | 99.2% | no |
| two-span 1 | 428239 | $FCE | +7 | 0.00 | 78.2% | 0 | 32/32 | 6/6 | 95.0% | no |
| two-span 2 | 428389 | $103C | +15 | n/a | 78.0% | 0 | 32/32 | 6/6 | 100.0% | yes |
| two-span 3 | 428539 | $10AA | +22 | 0.00 | 91.5% | 0 | 32/32 | 5/5 | 100.0% | yes |
| two-span 4 | 428689 | $1118 | +29 | 0.00 | 83.4% | 0 | 32/32 | 7/7 | 98.0% | no |
| two-span 5 | 428839 | $1187 | +35 | 0.00 | 76.7% | 0 | 32/32 | 6/6 | 94.9% | no |
| two-span 6 | 428989 | $11F5 | +42 | n/a | 80.9% | 0 | 32/32 | 6/6 | 96.7% | no |
| two-span 7 | 429139 | $1263 | +50 | 0.00 | 87.8% | 0 | 32/32 | 6/6 | 99.2% | no |
| two-span 8 | 429289 | $12D1 | +57 | 0.00 | 88.8% | 0 | 32/32 | 5/5 | 100.0% | yes |
| two-span 9 | 429439 | $133F | +64 | 0.00 | 93.9% | 0 | 32/32 | 5/5 | 100.0% | yes |
| two-span 10 | 429589 | $13AD | +71 | 0.00 | 84.5% | 0 | 32/32 | 6/6 | 97.8% | no |
| two-span 11 | 429739 | $141B | +78 | 0.00 | 83.2% | 0 | 32/32 | 5/5 | 97.2% | no |

All 20 credits-done spots also hold no clouds (`_unkFAB9` 0) in both. Limits:

- **One pixel of credit text (fixed by the kterminal lane).** At the 11 of 32 text spots
  where native VSRAM equals `Camera_Y_pos_copy` the frames were pixel-identical. At the
  other 21 the engine's text sat 1px above native. The native picture is one pass behind
  RAM, and the engine models that by publishing the sprite table and scroll at VInt; the
  text led because SSZ2's Plane A V-scroll followed the camera the credits had just moved.
  See the kterminal section below.
- **Post-credits Plane B.** Fixed in `516ca64f1`; see the post-credits section below.
- **Part two after `$1660`.** The native spans stop at `$141B`. The lift itself is
  RAM-checked at credits-done but has no mid-transition native picture.
- `Special_V_int_routine`: native runs `$04`→`$08` (`SpecialVInt_VScrollOn`/`VScrollCopy`)
  and `$0C` clears itself (`SpecialVInt_VScrollOff`). The engine holds `$04`, then `$0C`.
  No visual effect was measured (island window 96–100% at dy 0); recorded, not changed.

### Knuckles post-credits Plane B and the credit-text phase (postcredits lane, 2026-10-15)

**Post-credits Plane B (engine, `516ca64f1`).** `SSZ2_BackgroundEvent` routine 4
(sonic3k.asm:117996) is a single `lea (Level_layout_main).w,a3` before `loc_58D1E`.
`ScreenEvents` hands background events `Level_layout_main+2`, the background row pointers
(sonic3k.asm:102239-102246), so routine 4 makes `Draw_TileRow` (d1=0, d6=$20: layout X
0-$1FF) refill Plane B from foreground rows. `loc_59208` lowers `Camera_Y_pos_BG_copy` by
`$6000` per pass from the lifted `$1D0` and stops when the word reads `$80`, at `$80.C000`.
Each 16-line crossing replaces one ring row. The lift re-rounds `Camera_Y_pos_BG_rounded`
with the lifted copy, so the ring keeps its island rows until each is overwritten.
`sub_58D3E` takes `loc_58D44`: the `HScroll_table` longword gains `$8000`, and its old
integer seeds eight words, each an eighth lower, under a fixed `$5E`. `sub_58FBC` then
runs `ApplyDeformation` with `SSZ2_BGDeformArray` (`$58CDE`). The engine sampled the
background layout (the island) and kept the plain deformation.

Native credits-done (input 432708) holds routine 4, BG Y `$80.C000`, foreground rows 1-2
(chunks `$0C` starfield, `$0A`/`$0B` sea) and `HScroll_table+4..+$18` =
`0,$3DE,v/8..v,$5E`. Its lines 0-159 scroll 0 and its sea bands start at line 160.
`SszEndingBackgroundPlaneState` records, for each 16-line slot, the layout row
`Draw_TileRow` last wrote and its source. The SSZ runtime capture carries it for rewind.
The new `TestSszAct2CameraController` case covers the lift, the descent, the plane cells
at 320/800, the band words and a restore/replay. It was red 12/2 before the change.

Re-rendered from the cold Knuckles movie with the credits lane's declared inlet
(`engine-k-postcredits-v1/`), credits-done went from about 16% to 87-95% of pixels equal.
Seq000 has an identical starfield and differs only in the sea rows. The engine reached
credits-done after the same 2180 routine-4 passes as native. The sea difference is
inherited: `loc_58D6E` adds `$1000` per pass from the SSZ2 arrival, so the `HScroll_table`
longword depends on the route (engine `$06E7D000` against native `$08192160`). Declaring
native `$032B2160` at flight init as well (`engine-k-postcredits-v2-hsaligned/`) gives the
native longword at every credits-done spot. Seq000 is then 100.0% pixel-identical. Seq001
to seq019 are 92.9-93.6% equal, all in rows 28-159: the terminal banner. There the
engine's logo sits at y 94-133 against native 29-69, and native Knuckles flashes pink.
That offset is identical before this change, so it belongs to the terminal objects; the
kterminal lane below fixed both. `HScroll_table+6` stays `$3E0` against native `$3DE`, because the
engine's flight camera X is `$240` against native `$1CF`. Both are inherited from the
fight. It shifts the eight horizon lines of the second band (160-167) by 2px; they are
uniform, so no pixel differs.
`bg-descent-*` frames show the island scrolling down out of Plane B, then the chunk `$03`
sky, then the sea and the stars. No native picture exists between `$141B` and
credits-done.

**The native picture is one pass behind RAM.** `VInt` writes VSRAM from
`V_scroll_value` (sonic3k.asm:528) and `VInt_8` DMAs `Sprite_table` (sonic3k.asm:761) in
the same interrupt. `ScreenEvents` sets `V_scroll_value` from `Camera_Y_pos_copy`
(sonic3k.asm:102253), before `sub_5B18E` advances `Camera_Y_pos`. At that interrupt both
hold the pass that just finished, latched together. In the 8 consecutive native frames (credits-one
422795-422798, credits-two 428089-428092), the dumped VSRAM and the dumped SAT always
equal the previous dump's RAM `V_scroll_value` and `Sprite_table`. The picture is drawn
from that VRAM:

- the cloud in SAT entry 4 is 8 lines tall;
- RAM moves it from y 46 to 48 between 422797 and 422798, but the dumped SAT moves it from
  45 to 46;
- the native picture shows rows 45-51, then 46-52, which no RAM position can produce.

So the native picture is one pass behind the RAM that selects the spot, for planes and
sprites alike. The engine models that latch, so its clouds were already right and its text
was a pass early. The kterminal lane below attributed and fixed it.

Native flight-window capture: `native-knux-credits-flight/` (CampVisualProbe exe
`516847eb…`, resumed from the campaign lane's `native-knux-2` savestate at input 412501;
112 snapshots every 25 inputs plus 1420 per-frame log rows; provenance alongside).

### Knuckles terminal banner, pose flash and the credits Plane A phase (kterminal lane, 2026-10-15)

Same native spots (`native-knux`, credits-done inputs 432708-433088 every 20, with RAM,
CRAM, VRAM and SST). Engine renders `engine-k-kterminal-v1/` and `-v2/` use the cold
Knuckles movie with the postcredits lane's declared inlets: Super Emeralds after Mecha's
defeat; `V_int_run_count` `$66142`, the swing/`_unkEE9C` state and `HScroll_table`
`$032B2160` at flight init.

- **Banner position (engine).** `Obj_5EF68` (sonic3k.asm:125476) indexes `off_5EFB6` by
  `Player_mode`, `SK_alone_flag` and `_unkFA88`. Locked-on Knuckles at progress 0 or +2
  takes `loc_5F0A6`: the end pose (`loc_5F480`) and `loc_5F20A` with the subtype that
  `AllocateObject` cleared. `sub_5FF1C` (sonic3k.asm:126931) then reads `word_5F260[0]`,
  (`$120`,`$B0`). Progress -2 takes `loc_5F0C4` (subtype 8, (`$120`,`$F0`)), and every
  Sonic and Tails row takes `loc_5F05C` (subtype 4, (`$D0`,`$B8`)). The engine hard-coded
  (`$120`,`$F0`) for Knuckles and (`$120`,`$B0`) for Sonic. Native holds the logo SST at
  (`$120`,`$B0`) from input 432728. Sonic's and Tails' rows now follow the same table; no
  native picture of those terminals exists.
- **Trademark (engine).** `loc_5F20A` also allocates `loc_5F4FA` (sonic3k.asm:125982)
  with subtype 2: `ObjDat3_60130`'s TM, mapping frame 2, at `byte_5F520[2]` (+`$34`,
  -`$10`). Native slot 11 holds it at (`$154`,`$A0`). `loc_5F26C` passes subtype 4 for the
  S3 eyecatch. The child deletes itself when `Graphics_flags` bit 7 is clear (Japanese
  hardware); like the engine's title screen, the ending models overseas hardware. No
  initializer draws in its own pass, so the banner first draws one pass after
  `Obj_5EF68`'s init.
- **Pink flash (engine).** `loc_5F480` allocates `loc_5FBA0` (sonic3k.asm:126562) only
  when `_unkFA88` is positive. Until `Palette_cycle_counters+0` is set, it steps `$3A` by 6
  through `Pal_KnuxEndPoseSuper` every third pass, waiting `$E` passes after the wrap at
  `$3C`. `move.l (a1,d0.w),(a2)+` writes colours 2-3 of line 0 and
  `move.w 4(a1,d0.w),2(a2)` writes colour 5, so colour 4 keeps `Pal_KnuxEndPose`'s
  `$0080`; native CRAM agrees. Native RAM dates `Obj_5EF68`'s init to input 432709 (`$2E`
  `$1EC` at 432728), with the first flash write in that pass. The engine reproduces the
  native `$3A` colours at all 19 later spots.
- **Logo fade (engine).** `sub_5FF1C` allocates `loc_5FF62` (sonic3k.asm:126958) after
  the logo. From that pass, every fourth pass `sub_85EB4` raises the 16 line-1 colours a
  level toward `$EEE`, eight times. `loc_5FF94` then falls into `loc_5FFA6`'s countdown,
  and `sub_85F2A` lowers them toward `Target_palette_line_2` eight times (passes 31-59).
  Native RAM holds `$0AAA` throughout at pass 19, and the target raised to at least `$888`
  at pass 39. The engine stepped from black straight to the target.
- **Credits Plane A phase (engine).** The engine prepares the sprite table and scroll
  registers at the loop tail and publishes them at the next VInt (`LevelSpritePresentation`).
  That is the native picture's one-pass latch. In SSZ2, `SwScrlSsz.getVscrollFactorFG()`
  returned the physical camera plus shake. `ScreenEvents` (sonic3k.asm:102253) writes
  `V_scroll_value` from `Camera_Y_pos_copy`, after `SSZ2_ScreenEvent` has added
  `Screen_shake_offset` to the copy (sonic3k.asm:117750-117751). `loc_5B204` moves only
  `Camera_Y_pos`, so Plane A led the published sprites by one pass. A temporary instrumented
  render showed each cloud drawn inside the step (`LevelFrameStep` →
  `LevelSpritePresentation.prepare`), never in the render pass. Plane A now takes the copy.

Rejected: a render-camera offset (projection against subtraction camera). Slot 8's cloud
stepped 1, 1, 2 lines while `Camera_Y_pos` stepped 1, 0, 1, so only a whole-pass SST lag
fits. Also rejected: the earlier "capture limit, nothing modelled" reading of the text,
which assumed the engine draws everything at the current pass.

| Spots (3-bit levels, native cropped at 14,8) | Postcredits v2 | kterminal v1 | kterminal v2 |
| --- | --- | --- | --- |
| credits-done seq000 | 100.0% | 100.0% | 100.0% |
| credits-done seq001-002 (logo fading in) | 92.9% | 96.3% | 100.0% |
| credits-done seq003-019 | 92.9-93.6% | 100.0% | 100.0% |
| credit text, 32 spots | 1px above native at 21 | dy 0 | dy 0 |

At the 26 text spots with a yellow title, every title pixel matches native at dy 0. The
text frames are 94.7-97.5% equal as wholes. At 30 of 32 spots every remaining pixel lies
inside a cloud box: this tree reaches credits part one one V-int late, so the clouds
differ. That and a glyph fragment at credits-one spans 9-10, both older than this lane,
are recorded in [S3K known bugs](../../../status/s3k-known-bugs.md#knuckles-credits-and-terminal-residuals-resolved),
with the zero-copy pass and the S3 eyecatch fades.

Red first, all queued and focused with the ROM paths, 0 skipped: `TestS3kPostCredits` and
`TestSszAct2CameraController` 23 run, 4 failed (banner y 240 at progress 0 and +2, flash
colour, and the older 800 px case running at 320); `TestSszAct2CameraController` 13 run, 1
failed (credits pass 123: Plane A 2 against copy 1); `TestS3kPostCredits` 13 run, 1 failed
(logo colour `$0000` against native `$0AAA` at pass 19). The final run of the eleven
keep-green classes, the four S3K guards, `TestS3kSszMechaSpawnHeadless` and the render ran
215 tests with none failed or skipped.

### Knuckles credits V-int inlet, teleporter pad and zero-copy pass (presentation lane, 2026-10-16)

Resolves three of the four residuals the kterminal section left in
[S3K known bugs](../../../status/s3k-known-bugs.md#knuckles-credits-and-terminal-residuals-resolved).
The source is the round-2 presentation branch over Root `d3fb0625e`.

**"One V-int late" was the declared inlet.** Native `observations.csv`
(`native-knux-credits-flight`) holds `V_int_run_count` `$66141` on the flight edge row,
input 419847. RAM at credits part one (422795) holds `$66CC5`, 2948 frames later. In every
engine render the flight object first exists after frame 58253 and part one starts at
61201, also 2948 frames later. The engine counts one V-int per frame, with identical flight
logs. The pre-`9b0c85dd0` render (`engine-k-credits-fix-aligned`, credits at 61202 with
`$66CC6`) and the `9b0c85dd0` render (61201, `$66CC5`) both fit a `$66141` declaration. The
postcredits and kterminal renders (61201, `$66CC6`) declared `$66142` in their provenance,
following the driver comment's "input 419848". No commit between `9b0c85dd0` and
`516ca64f1` touches a V-int counter. Declaring `$66141` reproduces native `$66CC5`, and
every credits-one cloud SST equals native.

**The glyph fragment was the HPZ teleporter pad.** The engine object dump at span 9 put
`SSZHPZTeleporterObjectInstance` in slot 11 at `($A0,$4B0)`, where native slot 11 holds a
cloud. Native SSTs hold the pad (routine `loc_455CC`) through the phase-one spots (input
414150) and not from the super-gold spots (415643, camera X `$240`). Its routine ends in
`Sprite_OnScreen_Test` (`loc_456EE`), so the pad is deleted at that camera with its respawn
bit clear. Native RAM then shows camera X `$1CF` at the flight and 0 at the credits, with
`Object_load_routine` 4.

`Load_Sprites`'s backward branch (`loc_1B864`, sonic3k.asm:37601-37605) subtracts `$80`
from the new coarse X and branches past the back cursor on the borrow. A backward pass
landing in the first chunk therefore loads nothing, however far it jumped. S2's
`ObjectsManager_GoingBackward` has the same `subi.w #$80,d6 / bcs.s` (s2.asm:33051-33052).
The engine's backward pass loaded every spawn above the clamped edge 0. The pad was live
from span 7 (camera Y `$333`) and drew tiles the credits font had replaced.
`ObjectPlacementController.spawnBackwardNonCounter` now returns when the unclamped edge is
negative. The trim of the front cursor still runs, as `loc_1B8A8` does.
`TestObjectPlacementManager#backwardMoveToTheFirstChunkLoadsNothingBehindTheCursor` was red
26/1 before the change.

**Zero-copy pass.** `ParallaxManager` reads a handler's FG V-scroll of 0 as "follow the
camera", and `ZoneScrollHandler` is Mod API. `SwScrlSsz` now implements the internal
`ExplicitForegroundVerticalScroll` while act 2's screen init holds, so a `Camera_Y_pos_copy`
of 0 is used as `V_scroll_value` (sonic3k.asm:102253). `TestSszAct2CameraController`'s
credits case now asserts every pass, including the one zero-copy pass; it was red 13/1 at
pass 122 (Plane A 1 against copy 0). No Mod API declaration changed.

Renders use the temporary `PresCreditsDiagDriver`, derived from the postcredits driver,
with the cold Knuckles movie, LIMITED/RAW_RGB at 320, and these declared inlets: Super
Emeralds after Mecha, V-int `$66141`, the swing/`_unkEE9C` state and `HScroll_table`
`$032B2160` at flight init. They compare against `native-knux` credits-one spots by raw
RGB over the native active area at (14,8). Both sides use the same 8-level values, and
nothing is shifted or normalised. Pictures and the driver source are in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/round2-presentation/`.

| Credits-one spans 0-11 | V-int at span 0 | Cloud SSTs equal native | Pixel-identical spans | Differing pixels |
| --- | --- | --- | --- | --- |
| Part A tree + `$66141` (`engine-k-diag-vint66141`) | `$66CC5` | 12/12 | 10/12 | spans 9-10: 440 and 647 px, all in x 136-183 (the pad) |
| Parts A+B (`engine-k-diag-fixed`) | `$66CC5` | 12/12 | 12/12 | none; no teleporter object in any span |

Checks (queued, focused, exact selectors, ROM paths, 0 skipped): red
`TestSszAct2CameraController` 13/1 (15:01:51Z); red
`TestObjectPlacementManager,TestSszAct2CameraController` 26/1 (15:09:17Z, the borrow case
only). Then 26 classes: the placement tests, `TestS2ObjectWindowing`, the kterminal
keep-green set, `TestS3kSszMechaSpawnHeadless`, `TestS3kZoneAnimateTilesPublication` and the
four S3K guards. They ran 296 with none failed (15:19:26Z). Not covered: credits part two
and credits-done (the driver captured part one only); S2 trace replays with a backward
camera jump into the first chunk were not run.

### Ending audio request ledger and credits admission (ending-audio lane, 2026-10-16)

This is request-tier interim evidence for the nine declared NTSC 320 D01 products.
Rendered PCM remains open, and nothing here compares PCM.

**Request ledger.** A scratch probe adds observation-only hooks to the v16-limited
declared protocol: `AudioRequestObserver`, `musicFadeOutCount()` and
`commandTimeline()` deltas, and the rescue director's routine. The ledgers, scripts and
`provenance.json` are in
`B/engine-audio-ledger-v1/` (`B` = `ending-native-reference-prerequisites`).

On base `6405313ce` all nine products pass every assertion:

- **A1** (`loc_5DE36`/`loc_5DE46`, skdisasm 044fa46 sonic3k.asm:124105-124116): one fade, on the first
  gate-true frame. That is Level_frame_counter 3281 for −2 and 3692 for 0/+2. The
  gate's `foregroundY` equals the published camera Y copy on every gate frame.
- **A2** (`Obj_5DFEE`, :124273-124287): `$DC` only for +2, at 3714.
- **A3** (`Obj_Ending` `loc_5E1C2`, :124416-124426): `$DC` at 3303 for −2 and 3714 for
  0. It is absent for +2, although `Obj_Ending` still first runs there at 4403.
- No other credits or fade request occurs in the 6144 inputs.

The expected frames are native-anchored routine dates, not native audio observations:

- They come from `Events_routine_fg` 8→12 and from the first `Queue_Kos_Module` calls of
  `Obj_5DFEE` and `Obj_Ending`.
- The Sonic −2/0/+2, Tails +2 and Sonic+Tails +2 frames come directly from native
  `branches.csv` ledgers. The cloud-transition v5 ledgers agree.
- The other four products are derived from the declared ledgers' camera writes. That
  derivation was checked against the five direct products.
- The Hyper Kos residual (#46) does not reach any of these dates.

Red-first checks:

- Expecting `$DC` on a +2 product fails.
- Moving the fade one frame fails.
- A ledger with its observations removed fails in all nine products.
- Audio-disabled runs are not empty: the request seam sits upstream of
  `AUDIO_ENABLED`. Their ledgers are identical, and `ledger.csv` is byte-identical
  across audio enabled, audio disabled and the v16-limited run.

The `$0D01` load issues `mus_SSZ` (`$15`) from `LevelMusic_Playlist`
(:7712-7733). The ROM's non-demo `Level` entry also sends `cmd_FadeOut` through
`Play_SFX` (:7540-7541). The declared engine ingress records no request for it, which
is noted here and not asserted. Whether the ordinary mode transition issues it was not
checked.

**Credits admission (fixed in `f640e7929`).** On `6405313ce` every `$DC` request
reached the seam on its native frame, but none was admitted:

- No command or sequencer followed in any of the nine products, while the load
  `PlayMusic` and the A1 fade, used as positive controls, were recorded.
- S3K had no request service, so `playMusicMailboxNativeRequest` fell back to
  `playSfx($DC)`, which found no SFX.

The fix follows the driver:

- `zPlaySoundByIndex` tests `mus_CreditsK` first. `zPlayMusicCredits` then loads music
  table entry `$32` as ordinary music (skdisasm 044fa46 Sound/Z80 Sound Driver.asm:1641-1644,
  1709-1712).
- The S3K profile declares that route through the non-API `NativeMailboxMusicRoutes`
  capability.
- The observer still records the raw `$DC` once, on the same frame.

Checks:

- `TestS3kEndingAudioRequestLedger` covers Sonic −2/0/+2. The audio presentation is
  pulled as `GameplayCaptureTool --audio` does.
- It asserts the request frames above, the `mus_SSZ` positive control, and that song
  `$32` is serviced with advancing track positions after `$DC`.
- It was red 3/3 on the unchanged source (19:44:56+01:00) and 3/0/0/0 after the fix.
- After the fix, the nine-product rerun produced byte-identical request ledgers, and
  every `$DC` was followed by `PlayMusic` id `$33` on its frame.
- Focused neighbours passed 348/0/0/0 across 32 classes. They cover audio admission
  and rewind, the S2 request service and monitors, the S1 monitor, the ending rescue,
  lifecycle and ledger, the three audio boundary guards and the required S3K four.

Not covered:

- Knuckles' `Obj_Song_Fade_Transition` already plays engine music `$33`, so its request
  seam shows `$33` rather than `$DC`.
- Knuckles, wind and post-credits requests (A4-A6) and PAL (A8) have no request dates.
- Rendered PCM is not compared.

### Native PAL 50 Hz Sonic/+2 product (ending-pal lane, 2026-10-16)

This is the first native-tier PAL evidence. It covers one declared product: Sonic/+2 (Hyper
Sonic, `Player_option` 1, all Super Emeralds), D01 at 320, from scene entry through credits
and the post-credits terminal to `Title_Screen`. The other eight declared Sonic/Tails PAL
products, Knuckles at PAL and rendered PCM remain open.

**Setup.** Native uses the unchanged declared setup and fence (`declared-scene-spec-v2`).
The only change is GPGX `SyncSettings.Region=Europe`, set in memory. The movie file is
unchanged, and the fence requires `Graphics_flags` bit 6. The engine runs the v16-limited
title-owner ingress with `REGION=PAL` before boot, `FrameRateResolver` at 50, and LIMITED +
RAW_RGB output. Inputs are neutral after the startup Start, so the terminal times out
(`loc_5F2F8`, 044fa46 sonic3k.asm:125848, `$4AF`). Pairing is native poll p ↔ engine outer p+1, with no offset. The
plan, probes, hashes and sampled times are in
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/ending-pal-v1/provenance.md`.
Native ran 20:22:56–20:24:07 UTC and the engine (`e94f6c4e7`) 20:30:49–20:30:58 UTC, both
under queued `exec:exec`.

**Matched.**
- Raw RGB is byte-identical (0 differing pixels, no normalization) at all eight spots
  from poll 1 to 3071.
- `Level_frame_counter`, `V_int_run_count`, `RNG_seed` and progress are equal on all
  13,888 paired level frames.
- The native scene has no lag frames before the exit.
- Credits take 7,415 frames on both sides: native `_unkFA86` 4→6 at LF 4479→11894,
  engine routine 4→6 at 4481→11896. This is the first native confirmation of the
  literal PAL table (speed `$FD00`) and two-part replay.

**First divergence: ordinal 3777, LF 3778.** Native `Events_routine_fg` goes 16→20 and the
camera jumps to `($1EB8,$1E0)` four frames before the engine (LF 3782). `loc_5AC70`
(044fa46 sonic3k.asm:121051-121053) waits on `Kos_modules_left` for the AIZ1 jobs that `loc_5AC12`
queued: three `Queue_Kos` and two `Queue_Kos_Module` (121031-121048). Native finishes
them at LF 3777. The NTSC native reference (`declared-native-spots-sonic-super-320-v1`)
and the engine finish at 3781.

The cause is idle CPU, not object state. Native NTSC and PAL pixels, VRAM and CRAM are
identical at every spot through poll 3071, and every ledger field matches until ordinal
3777. RAM differs only in stack bytes, plus, at poll 1, a 274-byte in-progress block of
`Kos_decomp_buffer` from `$FFD64E`. Only the service rate differs. `Process_Kos_Queue` runs in
main-loop idle time (044fa46 sonic3k.asm:2872). A PAL frame has 313 lines instead of 262, and VInt
spends 17,942 cycles in its PAL `move.w #$700,d0 / dbf d0,*` (044fa46 sonic3k.asm:545-548).

The engine's `s3k-kos-v1` service counts are NTSC measurements keyed by fingerprint, with
no region term. A PAL table or region shift fitted to this route would break runtime
invariant 3. The owner is the Kos CPU-budget model in
[S3K known bugs](../../../status/s3k-known-bugs.md#ending-reunion-kos-service-count--per-object-cpu-budget-open).
That model's research child already measures a further ≈3.4k cycles per PAL frame that it
cannot yet explain.

**Downstream.** These differences follow from that divergence:
- Routines 20–32 stay four frames late. Routine 36 re-aligns at LF 4381, which matches
  the timed wait.
- The reunion gate (routine 44) and the credits start are two frames late (native
  4442/4478, engine 4444/4480). Native PAL services the reunion jobs as the NTSC Sonic
  route does (v7p ledger), which keeps the known one-frame Hyper residual, plus one frame
  more.
- Spots 4095–12287 show phase differences: cloud and Tornado positions, `Camera_Y_pos`
  during credits, and palette line 3 cycle phase.
- The engine's `GameMode` used to change 23 outer steps after its terminal ended. This was
  region-independent and is fixed in the
  [2026-10-17 handoff section](#terminal-to-title_screen-handoff-ending-pal-lane-2026-10-17).

**Terminal palette and tail (fixed; line numbers are 044fa46 `sonic3k.asm`).** The S3
eyecatch has no PAL branch. The first picture (poll 13311) showed 478 differing pixels.
A second native run per product (`EndingScenePalProbeV2`: per-frame `Normal_palette`
and `Palette_cycle_counters+0`, every frame from `loc_5FD5A`) traced them to five owners:
- `Obj_5EF68` clears `Super_palette_status` on its first pass (125517-125520). The engine
  kept the Hyper cycle writing line 0 colours 2–4.
- `loc_5F15A` sets `Palette_cycle_counters+0` (125704-125706) and nothing clears it.
  `sub_5928C`, the water cycle that `Ending_BackgroundEvent` routine 4 calls every frame
  (118597-118615), tests that byte first. The engine re-wrote line 3 colours 13–15
  after the fade to black.
- `Obj_FadeSelectedToBlack` (179589-179612) takes its first `DecColor_Obj` step on its
  allocation pass, which is native poll 14256 on Tails/+2 NTSC. The engine stepped one
  pass later; `loc_5F18A` still hands over on the pass after the eighth step.
- `sub_5FF1C` (126972), `loc_5F31C` (125863) and `loc_5F410` (125949) each allocate a
  `loc_5FF62` (126999). These fade lines 1, 0 and 2 up to white, then down to their
  targets, with the first step on the eyecatch initialization pass (native slots 5, 11
  and 13). The engine used a raise-only reveal.
- `loc_5F3AA`'s `CreateChild1_Normal` (125913-125921) adds the tail `loc_5F3EA`/`loc_5F400`
  (`AniRaw_6026F`) at (-$10,+8). It is the only eyecatch object drawn on its
  initialization pass, and it sits behind Tails: the native SAT links Sonic, Tails,
  Knuckles, then the tail. The engine had no tail.

Red first: `TestS3kEndingS3Eyecatch` (5 cases) and `TestS3kEndingS3EyecatchTail` each
failed on the earlier source for the behaviour they pin. The 17 focused classes then pass
200/0/0/0 (22:45:25 UTC, including the required S3K four, the PAL/NTSC lifecycle captures
and the Knuckles terminal pixels).

Paired pictures, native poll p against engine outer p+1 (LIMITED + RAW_RGB, no
normalization, engine timeline unchanged):
- **Tails/+2 NTSC:** raw RGB identical at all 165 spots. That covers the scene, credits,
  banner, every eyecatch frame from 14299 to 14426, and every 64th poll to the exit.
  Palette lines 0, 1 and 3 equal native on every frame.
- **Sonic/+2 PAL:** poll 13311 is now 0 px, and so are 17 of the 19 steady spots. Polls
  12912 and 13104 show 315 px in Sonic's eyecatch sprite. Here the eyecatch starts one
  frame after native, inherited from the PAL Kos phase above; this is not separately
  attributed.

Pictures and provenance:
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/ending-terminal-palette-v1/`.

**Not claimed.** Not claimed: other PAL products, Knuckles at PAL, PAL wall-clock pacing,
PCM, request dates (A8), or whole-run original-RGB acceptance beyond poll 3071. The
title-side `Demo_timer` PAL values (044fa46 sonic3k.asm:5553, 6526) were not compared. The native
run went on through the attract demo before its post-exit `$3FB2` display enable.

### Full-table continuation from the director's own routines (ending-pal lane, 2026-10-17)

`TestS3kEndingDirectorContinuationDispatch` drives the real rescue director rather than
calling `EndingIslandLiftObjectInstance.createGroup`. The aiz5-slots lane drafted it,
pinned to `e94f6c4e7`; the ending-pal lane added the negative gates and ran it on
`038468817`. Line numbers are 044fa46 `sonic3k.asm`.

- **`loc_5DB76`** (123878-123895): the routine `$C` expiry writes `word_5DBB6`'s two `$42`
  entries. The second overwrites the live catcher at SST93 in the same pass. The test
  checks the oracle-matched five fields, retained identity, no deletion, `loc_5EE18`'s
  same-pass init over the catcher residue, and two-cycle restore/replay.
- **`loc_5DD6A`** (124031-124066): for Sonic and for Tails, the routine `$1C` expiry
  fills every native hole in table order and overflows into SST93 over the live small
  propeller. The final `$47` has `$2D`=4; the test checks `loc_5EE86`'s inherited
  residue, the displaced owner no longer being dispatched, and restore/replay.
- **Negative gates:** Sonic at progress -2, and Knuckles (`Player_mode` 3) booted at D01
  with all Super Emeralds. Each still advances to routine `$1E` but writes no table entry,
  and passes restore/replay.

Command: `TestS3kEndingDirectorContinuationDispatch,TestS3kEndingContinuationPressure,`
`TestS3kEndingNativeGraphPressure` at 33/0/0/0, finished 22:59:50 UTC (34.1 s Maven), with
no production change.

**Mutation check:** removing the director's
`!knucklesMode()&&progress()>=0` gate made both negative cases fail (2 → 20 IslandLift
owners), and the source was then restored.

**Limits:**
- IslandLift fillers are declared allocation pressure.
- The Knuckles D01 boot exercises the `Player_mode` 3 branch, but shipped Knuckles never
  reaches D01.
- The `$47`-over-Tornado animation window (frames ≥4 outside the engine script window)
  has no reachable case.
- No native full-table occupancy is compared.

### Terminal to Title_Screen handoff (ending-pal lane, 2026-10-17)

Line numbers are 044fa46 `sonic3k.asm`.

**Native sequence.** The post-credits terminal hold `loc_5F2F8` (125848-125861) ends with
`loc_5F314`. That writes `Game_mode` 0 and returns without `Draw_Sprite`, so the holder's own
sprite leaves that pass's table. Its separately allocated children, TM and caption stay. The
level loop ends. `Sega_Screen` (5419) writes 4, and `Title_Screen` (5424) runs
`Pal_FadeToBlack` (5074-5084): 22 passes of `Wait_VSync` + `Pal_ToBlack`. `VInt_12` (871)
publishes the previous pass's palette, the unchanged sprite table and H-scroll through
`Do_ControllerPal` (948-964). Title initialization follows.

**Native measurement** (declared Tails/+2 NTSC, `EndingScenePalProbeV3`, frozen plan v3
`e10f1a82…`; exit window every frame from the `loc_5F2F8` pass whose countdown is ≤8; state
equal to v2):
- `Game_mode` 4 at poll 15531.
- The logo is gone at 15532.
- Fade steps run 15532-15552 (red, then green, then blue).
- Black at 15553.

**Engine before** (`eec53b4b7`; pairing poll p ↔ outer p+1):
- LEVEL until outer 15554 and TITLE_SCREEN from 15555, 23 frames late.
- 8618 → 231 differing pixels over 15532-15552. It deleted the terminal object and used the
  FadeManager curve.

**Fix.**
- **The ending object keeps its SST.** `S3kPostCreditsObjectInstance.finish` keeps the object
  and draws its last picture without the holder's own sprite.
- **Pending passes are ending state.** `S3kEndingState` holds the entry-fade passes, captured
  for rewind.
- **Same-pass handoff.** `GameLoopTitleEntryFade` takes a `TITLE_SCREEN` exit only when the
  level runtime is a `TitleEntryFadeSource` with a pending fade. It sets TITLE_SCREEN in the
  same level pass and keeps the existing stop-music call. Each TITLE_SCREEN frame then runs one
  pass with no level, object, HUD or title input update: publish the prepared table and
  palette, then `Pal_ToBlack`. The last pass calls the unchanged title return.
- **Render seam.** The engine and capture-session render seams show the level while that fade
  is pending; every other TITLE_SCREEN path draws the title provider.

**Engine after:**
- TITLE_SCREEN from outer 15532 (poll 15531).
- Raw RGB identical to native at every frame from 15528 to 15554, including all 22 fade frames.
- The terminal timeline before the finish is unchanged.

From 15555 the title's own content differs, because native spends 8 frames initializing
the title. That is the existing title baseline and is not changed here.

**Tests.**
- **Red:** `TestS3kEndingTitleHandoff` (production D01 session) failed 2/2 on the old source,
  with LEVEL on the finishing pass.
- **New coverage:**
  - Mode date, the per-pass `Pal_ToBlack` palette, frozen level/object counters and a held
    Start.
  - Mid-fade restore and forward replay, and the startup title as a negative case.
  - `TestEngineRenderDispatcher`'s title-entry routing. `TestS3kPostCredits` now expects the
    retained SST.
- **Focused selection:** 24 classes, with the title-card lifecycle and hold routes, S1/S2/S3K
  game over, the DEZ escape ship, `TestGameLoop` and the required S3K four.
- **Guards:** `TestArchitecturalSourceGuard` passes (GameLoop/Engine size ratchets). The two
  `TestProductionSingletonClosureGuard` failures are both in
  `tools/MenuAudioProbeTool.java:41` (1e6d3d93a), outside this change.

**Not covered:** PAL products, the Knuckles terminal (shares the code; no native picture) and
the title's own content.

Pictures:
`$HOME/Videos/OGGF/s3k-parity-coverage-2026-10-14/spot-checks/ending-title-handoff-v1/`.



### Knuckles eyecatch native SAT priority — 2026-10-17

The declared native 320 NTSC Super-emerald SSZ2 component now has one original-RGB
eyecatch checkpoint: native input 433872 / eyecatch init+576, 71680/71680 pixels
identical in Limited/RAW_RGB, donor off. The old code differs 70 pixels; native
SAT collection was receiving a group already reversed for direct drawing.
`TestS3kEndingS3EyecatchTail` passes both the new independent four-child opaque
overlap oracle and the existing Tails direct-draw/init case (2/0/0/0).

The displayed native tail is frame 8; CPU SST frame 9 describes the next producer.
No animation offset is fitted. This closes that checkpoint's overlap defect,
not every Knuckles terminal picture or PAL/PCM/cold/wide/donor products. Earlier
terminal original-RGB residuals remain open. See the
[implementation and rejected diagnoses](../../designs/2026-10-13-s3k-ending-lifecycle.md#knuckles-eyecatch-child-priority-2026-10-17-root).

## 2026-10-17 maintained Tails NTSC sampler

The [maintained sampler and current-source evidence](../../plans/2026-10-13-s3k-playable-completion.md#2026-10-17-maintained-positioned-ending-sampler) replace the temporary engine-facing probe for the bounded Tails solo/+2/NTSC/native320 product. One capture from frozen `dc338d3e3` plus exact sampler source hash `b47871f82488802ea2c9a356218825eb372b29c518804f65f57d24fdbe45161c` reaches TITLE_SCREEN at outer15532 and captures every165 requested pre-exit picture. The fail-closed raw RGB comparator checks original320×224 dimensions on both sides with no crop and reports165/165 pairs,0 differing pixels, including native5119/6143 paired to engine5120/6144. CLI2, required S3K60 and comparator9 controls pass without skips. Source/runtime/plan/spot hashes and exact commands are linked above.

This positioned title-owner ingress is not a cold campaign. Native ordinals select observation moments only; no native gameplay data hydrate engine state. Unsampled frames, prepared palettes, other native products, PAL/PCM, strict hardware timing and interactive hub recovery remain inherited gaps; no ending runtime behavior changed. Root owns combined guards and integration.

## 2026-10-17 nine-product NTSC sampler follow-up

The [explicit nine-product sampler and exact current-source evidence](../../plans/2026-10-13-s3k-playable-completion.md#2026-10-17-nine-declared-ntsc-ending-products) adopt diagnostic VInt prerequisitea547 through merge957c78a54 while preserving R1. All three declared rosters (Sonic, Tails, Sonic+Tails) and progress−2/0/+2 have one fresh capture each, with the frozen title-owner VInt0/RNG0 positioned ingress, native320/LIMITED238/RAW_RGB and private configuration. Original native declarations/ledgers verify eight11-pair early plans and dense Tails165. No reference was recaptured or renamed; four-/five-digit filenames are selected explicitly.

| Product/progress | Required pairs | Fresh raw RGB result | Actual capture bound |
| --- | --- | --- | --- |
| sonic-solo / 0 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| tails-solo / +2 | 165/165 | 0 differing pixels | 15532 / TITLE_SCREEN |
| sonic-solo / -2 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| sonic-solo / +2 | 11/11 | First9 zero;171 at5119 and194 at6143 | 6144 / LEVEL |
| tails-solo / -2 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| tails-solo / 0 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| sonic-tails / -2 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| sonic-tails / 0 | 11/11 | 0 differing pixels | 6144 / LEVEL |
| sonic-tails / +2 | 11/11 | First9 zero;171 at5119 and194 at6143 | 6144 / LEVEL |

All253 required pairs have original320×224 dimensions with no crop or normalization:249 equal and4 differing. The two+2 Sonic-led products are not pixel-qualified. Their first mismatch at5119→5120 is `(7,160)`, native238/238/238 versus engine136/204/238, with exact scene/timing/source hashes recorded in the linked Plan; no causal or new-regression attribution is made. Their qualification stops at that result. Team pre-dispatch provenance confirms actual Sonic+Tails registration, followed by ordinary native ending retirement. Source/runtime remain frozen across all nine captures; there is no runtime fix, fitted clock, retry or broader acceptance claim. CLI4+mandatoryS3K60+trace-authority11 controls pass75 with0 failures/errors/skips; Python10 passes. Actual private config outputs and unchanged Main config hash/mtime are verified. Root owns separate runtime investigation and combined guards/integration. Eight11-spot results do not certify later exit, unsampled frames, PAL/PCM/cold/Knuckles/wide or strict timing.


## 2026-10-17 passive Sonic/Tails reunion queue causality

The [two-product production-handle observation](../../plans/2026-10-13-s3k-playable-completion.md#2026-10-17-passive-reunion-queue-causality) runs once each for Sonic solo/+2 and Tails solo/+2, NTSC320/FAST/LIVE with the same frozen title-owner ingress, on3896035af plus the recorded passive tool source. Each emits187 metadata rows over all47 LF4400..4446, no images; per-product4447-row state prefixes match R2. Observer/user.dir restoration and actual private config publication are verified; Main config is unchanged. Focused controls pass18/0F/0E/0S.

Direct handle19, source `$1D17A6`, actual submission fingerprint `5b4280f6…`, is MEASURED `s3k-kos-v1`, assigned4 PRE_MAIN_LOOP services for both products. Their observed production queue chains are identical: head retirement4405, final parent retirement4433/4440/4443 and gate4444. Retained native Sonic retires that head4404 and gates4443; Tails retires4405 and gates4444. This establishes the production profile's collapse of native three-/four-window behavior; the existing instruction-budget research supplies the native cost explanation. Missing engine-owned CPU/hardware budgets prevent a supported timing repair. No manifest/readiness/runtime mutation or new RGB qualification follows; R2's Sonic/team late171/194 residuals remain open. Team, PAL/wide/cold/terminal breadth and6+46 acceptance are outside this metadata slice.
