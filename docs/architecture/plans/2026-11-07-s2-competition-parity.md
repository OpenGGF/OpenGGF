# Sonic 2 Competition Parity Programme

**Dates:** 2026-11-07 to 2026-11-08

**Branch:** `feature/ai-s2-competition-parity`, based on develop `60aa1d658`

**Status:** activated 2026-11-08. `Sonic2NativeModeProvider.QUALIFIED_ZONES` is
`{0, 11, 12}` (EHZ, MCZ, CNZ), so the title offers 2 PLAYER and opens the
Competition visit through the production title route.

## Goal

Run Sonic 2 World REV01's two-player Competition mode as a native product:
title and 2P menu, EHZ/MCZ/CNZ (two acts each) and the competitive special
stage, per-player state and results, split screen and teleport, measured
against native BizHawk references. The product contract is the
[2026-08-09 design](../designs/2026-08-09-s2-native-competition-human-p2-design.md).
Accepted differences are in
[known discrepancies](../../status/known-discrepancies.md#s2-competition-split-screen-native-lag-and-rewind-scope).

## User decisions

| ID | Date | Decision |
| --- | --- | --- |
| D1 | 2026-11-07 | Split screen uses the shared `NativeCompetitionRenderer`. S2's 8x16 cells live in the S2 model, presented through field-projected 8x8 patterns. Every view shows fixed interlace field 0. The interlace VRAM image and H-int state are retired. The field choice and the lag-frame sprite race are documented as unsupported. |
| D2 | 2026-11-07 | Held rewind stays off in Competition, as in S3K; registry capture/restore must work. |
| D3 | 2026-11-07 | Activation evidence is BizHawk native screenshots plus position/state checks. The two-player V5 trace recorder is deferred. |
| D4 | 2026-11-08 | Extend the Stageplay route planner for two players, with support in both editors. Later native input rounds use the planner, not hand-authored scripts. |

## Lane map

Lanes worked in separate worktrees and were merged into the lead branch with
`--no-ff`.

| Lane | Outcome | Final head | Lead merge |
| --- | --- | --- | --- |
| D | Menu and results audio, results flow and exits, production competitive halfpipe | `e2da45027` | `28af4b72e` |
| A1 | `SwScrl_EHZ_2P` / `SwScrl_MCZ_2P` / `SwScrl_CNZ_2P` deformation from ROM tables | `19d0429cf` | `8931c67ca` |
| A2-R | Per-view retained 8x16 planes, 2048-slot art residency, view latches, completed sprite pages; interlace package retired | `2a7b569b7` | `061907c7c`, `95eaa8cf1` |
| B | Native state: teleport swap and freeze, wait input, `Slow_motion_flag`, secondary invincibility, P1 checkpoint environment | `907b98f07` | `429cb1528` |
| C | Object lifetime gates, attacker score banks, cage and slot payouts, fragment slot admission, shared chain word | `ec6c22055` | `4f5218d2a`, `43bddbc86` |
| A2-S | Native display sources: HUD and animated art, object admissions, monitors, invincibility stars, dust | `063d2d491` | `9d9e98f94`, `b889211b3` |
| E1 | Cross-lane wiring: camera banks, load clocks, retained ring phase, cached hurt admission, gated `Sonic2CompetitionVisit` | `f70873957` | `b924adde4` |
| F | Native divergence diagnostic, S2 touch-scan rule, `Obj72` both players, lost-ring visibility, initial camera order, placement phase, menu-Start pause, lag cutoff, special-stage diagnostic | `c6b91cd62` (round 4) | `5de629f6a`, `fd2c0842e`, `d26ea1a07` |
| T | One `Obj34` title-card owner for 1P and both 2P views | `437b6edcf` | `15fb1f335` |
| P | Two-player route planner: goals, frozen-prefix continuation, native anchor splice, seam repair | `73cb548ab` | `897612065`, `dd56ed9d6` |
| PE | Route/Tape editing of two-player programmes | engine `40b878490`; desktop `b323b53` | engine `897612065`; desktop not merged |
| Evidence | BizHawk 2P reference observer and input scripts | `0df9ebf85` | not merged |

The last full lead check, at `43bddbc86`, ran 204 classes and 1,737 tests with
no failures, errors or skips. Later lane merges carried their own focused
checks, listed in each lane's handoff.

## Measured evidence

All native references are official BizHawk 2.11 / GPGX runs of the verified
REV01 dump (SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`) in an isolated
software display, with read-only Lua. No native value enters gameplay.

### Pixels

Engine row *y* is compared with native row *2y* of the 320x448 output (fixed
field 0). Each channel is reduced to the Genesis 3-bit level before counting
(engine `i*255/7`, BizHawk `i*34`); there is no spatial alignment or crop.

| Set | Pairs | Normalised differing pixels | Attribution |
| --- | --- | --- | --- |
| C1b (A2-R) | EHZ first, EHZ separated, MCZ start, CNZ start, EHZ text departure | 2.93%, 4.62%, 3.33%, 4.55%, 3.73% | Display owners then still missing: objects, HUD digits, animated art, title fills |
| C1b locked title | EHZ 1325 | 54.35% | Title art and fills absent at that stage |
| C2b (A2-S, cold) | EHZ flowers 1800/1817, monitor 1875, HUD ring 1909 | 1.08%, 1.40%, 1.38%, 1.41% | Eleven deferred display owners |

A cold cadence check showed field 0 changes only on odd physical frames, so
odd native screenshots pair with input *N* and even ones with *N-1*. That
removed an apparent 4-pixel horizontal offset without any runtime delay.

### Title card (lane T)

- 1P EHZ1: 768 native part rows match; seven pixel cuts have 0 mismatches over
  465,920 compared pixels.
- 2P: 1,212 part rows match on every frame 1184-1385 (allocation, entry, hold,
  both leave handoffs, delayed text, cleared slots). All 9,780 opaque glyph
  comparisons over four cuts match. Full-frame differences are 1,283 / 1,280 /
  13,028 / 1,307 pixels at 1300 / 1310 / 1325 / 1380; 1300 and 1310 are exact
  outside field rows 108-112. The 1325 excess came from P2's initial camera
  (engine 565, native 559), fixed afterwards in lane F round 3 (`5841ee812`).
  The pictures were not re-measured after that fix.

### Native state comparisons

`DebugSonic2CompetitionDivergence` replays each original 2P BK2 through the
production host from the native first-gameplay anchor and compares, per
physical frame, both players' centre X/Y and velocities, both cameras, rings,
lives, score and `Level_frame_counter`. Raw V-int is reported separately.

| Route | Rows compared | Result |
| --- | ---: | --- |
| ehz-finish, ehz-flow, ehz-zone | 1,565 | Exact through 2894; native lag at 2895 (`Vint_Lag` inside `DrawSprite_2P_FlipX` during `BuildSprites_2P`) |
| ehz / ehz-route / ehz-flowers | 1,822 / 1,172 / 491 | Exact through 3151 / 2501 / 1820 |
| ehz-monitor / ehz-monitor-high | 180 / 185 | Exact through 1980 / 1985 (CSVs begin at 1801) |
| mcz-route | 827 | Exact through 2151 |
| cnz-flow / cnz-route | 4,559 / 814 | Exact through 5896 / 2151 |
| CNZ planner route (lane P) | from 1337 | Exact through 1852; at 1853 native P1 lands on `Obj86` slot 106 at (1152,1640), subtype 1; the engine falls |
| special-route | 23 | Exact 1288-1310 from the FadeWhite load boundary; body 1311 is interrupted by the lag IRQ at 1312 in `SSObjectMove` |

Frontier history: ehz and ehz-route moved 1976 -> 2057 (touch-scan rule) ->
end of recording (lost-ring two-view visibility); cnz-route 2035 -> end
(`Obj72` visits both players); cnz-flow 2523 -> end and EHZ 2894 -> 2895
(object placement after display); EHZ title-locked P2 camera fixed (initial
camera order). The CNZ planner route's first blocker at 1152 was the engine
pausing on a held menu Start; `PauseGame` reads the press bytes (s2.asm:1571-1597),
fixed in `9b1e8fc9e`.

### Native lag rates

Ordinary rule: consecutive frames in active 2P gameplay with a repeated
`Level_frame_counter` while the V-int counter advances by one.

| Recording | Frames | Lag frames | Per 1,000 |
| --- | ---: | ---: | ---: |
| ehz-finish (act 1) | 9,752 | 15 | 1.54 |
| ehz-flow | 9,112 | 15 | 1.65 |
| ehz-zone (acts 1 and 2) | 21,996 | 16 | 0.73 |
| Other eight EHZ/MCZ/CNZ recordings | 179-4,559 each | 0 | 0 |

Special stage (actual `Vint_Lag` callbacks, since the stage does not advance
`Level_frame_counter`): 305 of 841 active frames (363 per 1,000), 156 of 404 once
rings and bombs are present (386 per 1,000). Gaps between lag frames are 2-6
frames, median 2, never consecutive. Recordings share inputs, so these are
not independent samples.

### One-player S2 attribution

All 56 `TestS2*TraceReplay` classes ran on lead `15fb1f335` and on base
`60aa1d658` (2026-11-08 01:21-01:54 UTC): 57 tests and 19 failures on both,
with identical identities and assertion messages. Lane F ran the same 56
classes for the touch-scan rule and the conveyor change with identical
results; lane C ran its 7 affected traces (chain and fragment changes) against
a clean baseline with identical identities. Lane F rounds 3 and 4 changed only
Competition host paths and did not repeat the run.

## Pre-act semantic flow lane (2026-11-08)

The flow lane starts from `b7076530a`, on local branch
`feature/ai-s2-competition-flow`; no push or PR is part of its delivery.
Title-to-menu and menu-to-level transitions now execute their native palette
passes. The shared Obj34 owner runs visible arrival, upload and leave waits
for both acts; player control and LFC remain frozen until release. The
campaign title/card paths are unchanged. Renderer overlap is confined to
runtime/video-frame wiring for title-only admissions; the renderer, views and
SAT builder remain owned by the progressive-output lane.

All five frozen routes were re-authored by the Stageplay planner's bounded
observed-lock repair, preserving traversal inputs and cold-qualifying goals.
Their native BK2 input tapes and witnesses remain byte-identical. EHZ's
residual pre-act inlets shrink to 68 and 66 physical frames; CNZ1's to 69.
BizHawk 2.11 confirms CNZ slot use at 2045 and its visible flip at 2513, EHZ
zone results at 18628, and tied act wins entering the special stage at 18909.
These are goal confirmations, not full-route state parity. The final display
counter goals were rebased by 131 to preserve their original hold durations.

The base V-int surplus reported by the object lane is confirmed: 137 at EHZ1
LFC 0 and at native 5823. Semantic waits change it to 168 at the first anchor
and 121 at the second. No fitted clock correction is authorized. The native
20-frame masked menu initialization, EHZ 41/39-frame assembly spans, further
decode interruptions and resulting clock limits are recorded in
[known discrepancies](../../status/known-discrepancies.md#pre-act-physical-timing).
Before/after phase sheets, boundary clocks, native receipts and focused-check
results are external under `~/Videos/OGGF/s2-competition/lanes/flow/REPORT.md`.
The lead owns integration and wider validation.

## Flow integration follow-up (2026-11-08)

The follow-up branch `feature/ai-s2-competition-flow-fix` starts at integrated
`ce26118f8`. The lead's28 failures comprise23 probes whose load/menu assumptions
predated visible startup waits, two tests using the retired field projection,
a timer probe injected before control release, and two CNZ route consumers of
an outdated read-only pixel witness. Positioned probes now drive actual native
continuations before setup; exact timer, PLC, SST, art-publication and rewind
expectations remain. HUD and Kosinski tests compare all16 native cell rows.

The CNZ witness now reads the progressive448-line views and both cell halves.
Resident ROM payload, non-flat pose, complete-cell and framebuffer-colour checks
remain, as do wrong-colour and missing-P2-input negative controls. Both CNZ
routes were requalified through StageplayRoutePlanner with the entire existing
tape as immutable prefix. Scripts, content identities, native witnesses and
exported BK2 bytes are unchanged; the flip movie remains SHA-256
`92070b90f795cdd43a8d6c66634dbc8c6421dbe3f921fddb065457864caf5891`.
The prior native goal receipt remains applicable to identical input bytes.

No gameplay, HUD/V-int ordering or progressive renderer code changed. The
follow-up report appends each failure's disposition and the exact integrated
selection/limits under `~/Videos/OGGF/s2-competition/lanes/flow/REPORT.md`.

## Rejected approaches

| Approach | Evidence and replacement |
| --- | --- |
| Interlace VRAM byte image, CPU word writes and H-int state (`Sonic2InterlaceVram*`, `CpuWordWrite`, `HintState`) | A virtual VDP and beam model, outside the reimplementation boundary. Replaced by semantic planes, art residency and view latches; 10 production files and 9 legacy tests removed in `2a7b569b7`. The 2026-10-22 "physical video admission" prerequisite (field permits, H-int completion events, field parity seeds) was dropped with it. |
| Cell height on the shared `RetainedPlaneDescriptorSource` | Would put interlace pattern pairing and an IM2 branch into the shared tile builder and `@ModApi` `LevelTilemapManager`. The 8x16 cell stays in the S2 model (D1). |
| Field selected from V-int parity | User chose fixed field 0 (D1); a 224-line output cannot show both fields. |
| Closed-form ring phase (`floor`/`ceil` of `Level_frame_counter / 8`) | Counterexample: after eight loops (LFC 8, frame 1), a loop that sets `Level_Inactive_flag` increments LFC to 9 and branches to `Level` before the `ChangeRingFrame` call in `Level_MainLoop` (s2.asm:5088-5118), leaving frame 1 where the formula gives 2. Replaced by a retained `Rings_anim_counter`/`Rings_anim_frame` owner with native reset and rewind (`d627c5bda`). |
| Second title-card owner for the 2P views | One `Obj34` owner drives 1P and both views; the 2P plane and sprite consumers are stateless, with no extra rewind key. |
| Title-card leave rows read the copied camera banks | Wrong: verified operands `47 F8 EE 20` / `47 F8 EE 00` read the live `Camera_X_pos_P2` and `Camera_X_pos` in `loc_15714` (s2.asm:29125-29149) and `loc_15758` (s2.asm:29151-29168). The copied-bank control gave 45,461 differing pixels at 1325; live cameras gave 13,028. |
| Per-object touch render-bit opt-outs | S2 `Touch_Loop` (s2.asm:85209-85219) has no render test. Replaced by the host rule `ObjectInteractionRules.touchScanRequiresObjectRenderBit` (S1 true, S2/KiS2 false, S3K unchanged); an object profile cannot re-enable it. Existing S2 opt-outs are harmless and kept. |
| Independent per-player badnik chains | The ROM shares one `Chain_Bonus_counter`; the salvaged branch's independent-chain expectation was archived as an observation, not committed. |
| A game-module decorator for the animation owners | Two native-world owners (pattern projection and palette-only, distinct rewind keys) replace the ordinary combined manager in Competition; the 1P manager is unchanged. |
| Clearing controller state at load transitions to stop the menu-Start pause | The press latch is recomputed at every synchronous wait instead (`9b1e8fc9e`). The planner route's engine-only unpause Start at row 1200 was then removed by a one-row planner repair. |
| Fitted pre-act delay or V-int reset | Physical initialization lag is outside the semantic reimplementation boundary. Visible ROM loops are represented, and witnessed native input inlets absorb only the measured export residue; no native value enters gameplay. |
| From-scratch planner replacement of the preserved complex CNZ traversal | Search exhausted two million offered rows before the complex slot leg. Use bounded, cold-qualified observed-lock repair, preserving every traversal row and authoring only live control-lock waits. |
| Lag admission, re-alignment or a fitted selector after native lag | Comparisons stop before the first lag frame; no timing value is supplied to gameplay. |
| Image-based object identification at the EHZ 1976 frontier | Picture suggested Coconuts; the engine inventory showed Masher (`$5C`). Identify frontier objects from the object list, not screenshots. |

## Route and act obligations

This is an LTS-08 auxiliary-mode increment under the
[zone and act testing standard](../../guide/contributing/level-test-standard.md),
not act certification. Participants are fixed by the ROM (Sonic P1, Tails P2),
so donor, team and character axes do not apply; viewport width is unassessed.
Held rewind is off (D2).

| Route | Entry and lifecycle | Native state | Pixels | Rewind | Gaps |
| --- | --- | --- | --- | --- | --- |
| EHZ 1 | Production `Visit` through menu, title card, gameplay, signpost and act results (`TestSonic2CompetitionVisit`) | Exact to 2894 (finish/flow/zone) and to the end of five shorter recordings | C2b four cold pairs 1.1-1.4%; title card exact apart from seam rows | Views, teleport, title card and roster restore tests | Native lag from 2895; badnik and monitor in both views, zone results |
| EHZ 2 | Loaded through the act-results flow in the same test | None (comparisons stop at act 1 lag) | None | As EHZ 1 | Native route and pictures |
| MCZ 1 | Menu entry; six-act load and camera checks | Exact 827 rows to 2151 | C1b start pair 3.33% (before display owners) | As EHZ 1 | Full traversal, results, cold pictures |
| MCZ 2 | Load and camera checks only | None | None | As EHZ 1 | Native route and pictures |
| CNZ 1 | Menu entry; slot and cage dual-capture tests | Exact to 5896 and 2151; planner route exact to 1852 | C1b start pair 4.55% | Slot-machine codec, cage and prize restore | `Obj86` landing at 1853 (lane F); mid-flip and spinning-reel pictures |
| CNZ 2 | Load and camera checks only | None | None | As CNZ 1 | Native route and pictures |
| Special stage | Menu and tiebreaker entry through the production halfpipe (`TestSonic2CompetitiveHalfpipeFactory`, `TestCompetitiveHalfpipe*`) | Exact 1288-1310 | `TestCompetitiveHalfpipeEgl` (engine only) | Halfpipe owners registered with the live registry | Ring, checkpoint and result progression; native pictures |
| Menus and results | Title, 2P menu, act/zone/game results and exits (`TestSonic2CompetitionGameResultsExit`, `TestSonic2CompetitionMusic`) | Menu Start pause latch checked on native cold and warm runs | 224-line menu and results references exist | Menu and results owners registered | Zone results and tiebreaker native routes (lane P routes b and c) |

## Open items

1. Lead activation: add qualified zones, show the 2 PLAYER row, apply the
   guide text, and run change-based smoke against `60aa1d658` and `-Pguards`.
2. Lane F round 6: the CNZ `Obj86` horizontal flipper landing at 1853. At
   writing, the cause is identified but not merged:
   `Obj86_Init` (s2.asm:58433-58445) sets `width_pixels` to `$18`, which
   `SolidObject_Landed` re-reads for a new landing, while the engine used a
   default width of 8 for the horizontal flipper. The fix also changes 1P S2
   and needs its own trace attribution and changelog line.
   Fixed in `8dc022263` (plus the human-P2 `Obj44` bumper latch `30baceb1d`); the
   56-class S2 trace attribution was identical before and after, and the CNZ
   planner witness is exact through all 2,507 native frames.

3. Done 2026-11-08 (lane P `8fbac68aa`): cold BizHawk runs confirm the CNZ slot
   in use (2045) and a visible mid-flip tile (2513), the EHZ zone results
   (18628) and tied act wins entering the special stage (18909). Open: the
   divergence tool's next frontier on that route (frame 4274, P2 velocity and
   score), and V-int-seeded slot reel/payout differences, which follow the
   existing V-int clock discrepancy.
4. Desktop editor branch `feature/ai-route-planner-2p` (`b323b53`) in the
   OpenGGF-Editor repository is not merged.
5. Native-reference branch `feature/ai-s2comp-native-refs` (`0df9ebf85`:
   `tools/bizhawk/capture_s2_competition_reference.lua`, `tools/bizhawk/inputs/*.script`)
   is not merged; index the observer when it lands.
6. Two-player V5 recording and competitive-halfpipe replay binding (D3). It
   should reuse the existing hardware-timing lag/phase and atomic-pass
   mechanisms rather than add a completion kind.
7. Seam rows 108-112 stay unsupported (U-3, U-4); palette publication on lag
   frames (U-6) and the lives-rewrite flag equivalence (U-8) are unverified.
8. Display inventory: 41 adopted and 11 deferred of 52 placed object IDs
   (926 placements); the `Obj3A` capsule's Competition reachability proof is
   deferred. `Obj3C` is not placed in any shipped layout and is not implemented.
9. The native lag counting script used for the rates above is external; promote
   it under `tools/bizhawk/` if lag rates are needed again.

## Diagnostics

- `src/test/java/com/openggf/game/sonic2/competition/view/DebugSonic2CompetitionDivergence.java`:
  native 2P state comparator with lag-boundary cutoff; properties `s2comp.refs`,
  `s2comp.route`, `s2comp.instrument`, `s2comp.out`, `s2comp.exclude`.
- `src/test/java/com/openggf/game/sonic2/competition/view/DebugSonic2CompetitionC2Capture.java`:
  C2 engine picture producer from the original BK2s.
- `src/test/java/com/openggf/game/sonic2/competition/specialstage/DebugSonic2CompetitiveHalfpipeDivergence.java`:
  competitive special-stage comparator.
- `tools/bizhawk/capture_execution_observations.lua`: read-only native callback,
  register and RAM observer used for the lag, pause and landing evidence.

All four are indexed in the [agent workflow guide](../../agent-workflow/README.md).
Native movies, CSVs, screenshots and comparison output stay outside the
repository.
