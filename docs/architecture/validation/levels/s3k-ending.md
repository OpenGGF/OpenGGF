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
local cases. The ordinary S2 controller RAM obligation is qualified separately below.

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
| `loc_5DB76`/`loc_5DD6A` full-table dispatch and downstream composition | Qualified for the two declared products | Native poll 3071 holds all 20 IslandLift SSTs (2 `$42` from `word_5DBB6`, 18 from the six `word_5DDD2`…`word_5DE12` tables) with engine-identical slot, x, y and mapping in both products; whole-SST and 3-bit pixel agreement at every checkpoint. `TestS3kEndingIslandLift` (22) and the continuation-pressure cases pass | `Player_mode` 3 and negative-progress skips are unit-covered only; no native Knuckles or progress -2 product |
| Tails/+2 and Sonic/0 declared products | Qualified as declared scenes | 22/22 checkpoints, zero level mismatches, all whole frames viewed | Cold campaign identity, original-RGB bytes |
| Sonic -2, Tails -2, Tails 0, Sonic+Tails -2/0 declared products | Qualified as declared scenes | Newly captured native references (unchanged v1 adapter, immutable host); engine `6ace9000f`: zero 3-bit-level mismatches and identical SST inventories at all 11 checkpoints each | Cold campaign identity, original-RGB bytes |
| Sonic +2 and Sonic+Tails +2 (Hyper Sonic route) | Open, attributed | Checkpoints 1–4095 match exactly; SST identical at all 11 | 171/194 level pixels at 5119/6143: native `HScroll_table+0` is `$14F800` and `Camera_Y_pos` 517/1345 against engine `$14F000` and 516/1344, so native opens the reunion `Kos_modules_left` gate one frame earlier on this route than on Tails/+2 while the engine schedules both identically. The owner lies upstream of fg routine 40 on the Hyper Sonic AIZ-return route and needs a native per-frame ledger for that route |
| Knuckles products (SSZ2 `$0A01`) | Open | Lifecycle 9 and terminal pixels pass; shares the corrected `updateCredits` | No native product; `loc_5B1A2` there clears only `Camera_Y_pos` and is not separately compared |
| Team retirement / full-frame replay | Inherited red | — | `TestS3kEndingLifecycleCapture` team cases (6) and `TestS3kEndingRescue` roster replay (30) fail identically on `2b9eb431d`: `level-sprite-presentation` prepared/published scroll differs after restore and replay. The COMPLETE LIFECYCLE row above therefore no longer holds for team entries |
| Wrapped particle presentation | Inherited red | — | `TestS3kEndingPresentationCapture` wrapped-particle cases (2) fail identically on `2b9eb431d` (`native IslandLift missing/displaced: 0/398`) |
| PAL | Open | Literal PAL tables and REGION=PAL lifecycles (earlier sections) | No PAL native product or 50 Hz capture |
| PCM / audio | Open | Mailbox/request-level audio tests only | No rendered PCM comparison |
| SAT overflow / original RGB | Open | — | Native 20-sprite/320-dot overflow still needs a real witness; byte-RGB acceptance awaits the LIMITED colour profile |
