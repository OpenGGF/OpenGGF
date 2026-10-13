# S3K native ending routes

This matrix belongs to the [2026-10-13 playable-completion work](../../plans/2026-10-13-s3k-playable-completion.md), against integration base `5e2f51f07917d5eff8ac120007dac55327f93f32`. Native Sonic/Tails endings remain inside `$0D01`; Knuckles continues inside `$0A01` after SSZ2. Locked-on `S3Credits` is an RTS, so a generic `GameMode.ENDING` would discard the actual scene owners. Resources, rejected approaches and run history are in the [ending lifecycle design](../../designs/2026-10-13-s3k-ending-lifecycle.md).

Focused playable ending qualification is complete on the ending worktree candidate. Three source-confirmed native presentation omissions remain open below; the passing lifecycle checks do not certify those effects. Combined integration smoke/guards and the separately owned continuous incoming DEZ/DDZ routes remain coordinator obligations. The evidence below distinguishes declared scene entry from an actual final-fight continuation.

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

The donor30 pass predates the final type-independent unclaimed-origin restore correction. Native Rescue36/D01 nine qualify that correction; the final change-based smoke selects the donor class again on integrated source. Existing ordinary S2 powered-controller RAM snapshot support is an inherited gap outside the native scene binding. Continuous incoming DEZ/DDZ coverage is tracked in the [final DEZ matrix](s3k-dez-final-boss.md) and [DDZ matrix](s3k-ddz.md), with SSZ continuity in [SSZ2](s3k-ssz-act2.md). Combined integration qualification remains open until the coordinator records it.

Native art/maps/palettes and moving output are verified at specific producer boundaries, not by whole-scene BizHawk pixel parity. Literal controller tests cover audio request absence; the retained films are silent and do not qualify rendered audio. No trace parity sweep or release-only full suite was run for this feature.

## Unimplemented native presentation breadth — 2026-10-13

A bounded independent source review after feature `c2103d5f7` (coordinator integration `d63bfe397`) confirms three omissions. These are unfinished parity work, not accepted deviations or newly observed playable blockers. No tests were repeated for this documentation audit. Existing scene progression, registry replay, both credits and terminal/title qualification remains valid within its recorded scope.

| Missing native owner | Native behavior and affected scope | Current source evidence |
| --- | --- | --- |
| IslandLift ending decoration family | `loc_5DB76` allocates two continuation subtype `$42` pieces at `word_5DBB6` and queues `ArtKosM_IslandLiftGfx` at tile `$2FF`. `loc_5DD6A`, unless Player_mode3 or progress < 0, later allocates 18 pieces: 6×`$41`, 2×`$43`, 4×`$44`, 2×`$45`, 2×`$46`, 2×`$47`. `loc_5EDAC` owns their mappings, raw animation, priority and screen/world projection | Rescue hold/departure routines advance the native signals and timers but allocate no corresponding IslandLift graph or art upload. Existing terrain redraw/cloud owners do not replace this animated sprite family |
| Rescue and AIZ-return Master Emerald palette helper | `loc_5D9EE` admits one `loc_5DF58` owner when the normal white color is ready. For progress>0, nonzero `_unkFAC1` selects `off_7DD5A` and cells 14–15 of native `Normal_palette_line_3` when positive, or `Normal_palette_line_2` when negative. `loc_5E08A` sets the negative mode for AIZ return; clear writers stop it. These native line symbols are zero-based engine lines 2/1 | `S3kEndingMasterPaletteState` is used only by `EndingKnucklesFlightObjectInstance` and writes fixed engine line2. The rescue/AIZ director has neither this allocated helper nor its `_unkFAC1` selection/cursor lifecycle. The existing Knuckles flight cycle is implemented and is not the reported omission |
| AIZ water palette animation during restoration hold | `loc_5AD56` calls `AnPal_AIZ1` every ordinary screen-event pass until Events_fg_4 signals the fade. The preceding fade completion clears the relevant native palette-cycle gates/counters | Ending FG32 advances background scroll and checks the signal but never invokes an AIZ palette owner. Ordinary `Sonic3kPaletteCycler` selects AIZ only for zone0; resource handoff retains internal `$0D01` and does not rebind that cycle |

Removal requires ROM-backed allocation/art/animation and palette state with the native admission, dispatch, stop and rewind ownership, plus independent producer/phase checks and moving/native-window evidence. The current rendered Tornado, mask, terminal and departure pixel cases do not assert these three missing effects. See the [current unfinished-work record](../../../status/s3k-known-bugs.md#native-ending-presentation-gaps-open) and [dated decision/evidence record](../../designs/2026-10-13-s3k-ending-lifecycle.md#read-only-presentation-audit--2026-10-13).

## Moving evidence

Durable sources are outside the repo at `~/Videos/OGGF/s3k-finish-endings-2026-10-13/`, with one `ending-capture-index.json` recording input/ROM/jar/CSV/video identities and exact commands. Sonic incomplete320 is a declared `$D01` entry with20000 neutral inputs/every4; Tails Super800 is a declared entry with30000 neutral inputs/every8. Knuckles incomplete/Super800 execute the maintained BK2 from input0, then neutral to30000, captured from8100/every8. Native60-input playback is encoded at15fps or rational15/2fps. Observed title inputs are15101,15546,24082 and23357 respectively; the one-input difference from an older Knuckles incomplete movie remains unattributed.

The original missing-plane movie is rejected: identity-only resident-art caching missed in-place DMA. The first Tails b2af film is also retained as negative evidence because physical camera0 versus renderCopy608 filled the center with mask grain. The corrected final Tails film uses source d1a298ec / immutable jar ed152e71, preserves the identical state CSV and visibly clears the native center through departure.

A reproducible61-second silent highlights reel under `highlights/ending-reel-render-copy-d1a298ec789d` includes the corrected departure, AIZ restoration, reunion, credits, Knuckles flight/Mecha terminal and title. Its EDL retains exact native-input↔source-time mapping and honest scene-entry/final-fight labels; it does not present editorial cuts as a cold campaign. FFprobe, full xerror decode and representative temporal frames qualify the media artifact. Integration smoke/guards remain pending.
