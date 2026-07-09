# Sonic 2 special-stage ROM initialization timeline

This is the ROM reference for the engine's special-stage pre-roll. The source
of truth is `docs/s2disasm/s2.asm`; the committed trace is a verification of
observable state, not a source for timing constants.

## Result

`Sonic2SpecialStageIntro.PRE_ROLL_FRAMES` must be **23**.

The causal basis is **one mode-entry observation plus 22
`Pal_FadeToWhite` VInts**. The recorder writes raw f0 immediately when it first
observes `Game_Mode=$10`, before the main loop dispatches `SpecialStage`; the
22 fade waits then occupy raw f1-f22. This is why the replay has 23 absent/zero
samples before its first non-lag observation after synchronous loading.
`PRE_ROLL_FRAMES` models only that observable empty-player/zero-speed window;
it does not mean the full ROM startup is complete, because the track waits,
control/DMA wait, fade from white, and `SpecialStage_Started` gate follow it. It
is an engine/replay frame count, not a claim that all 23 samples are physical
ROM VInts.

Three terms must remain distinct:

- **Raw CSV frame** is the physical BizHawk capture row. The ROM spends many
  physical VInts in synchronous decompression and PLC work; these are correctly
  present as lag rows.
- **Physical VInt** is the interrupt actually serviced by the ROM during a raw
  emulator frame. Raw f0 is only the recorder's mode-entry observation; it is
  not one of the special-stage routine's fade VInts.
- **Replay step** is the trace-paced, non-lag clock used by
  `AbstractS2SpecialStageTraceReplayTest`. It skips CSV rows whose `lag` column
  is 1. This is the clock the engine pre-roll models.

The recorder contract is explicit at
`tools/bizhawk/s2_ss_trace_recorder.lua:371-382`: the frame that first shows
`Game_Mode=$10` is recorded immediately and no initial frame is skipped.

## Raw-frame / ROM-phase timeline

`Pal_FadeToWhite` initializes `d4=$15` and uses `dbf`, so its loop executes 22
times (`s2.asm:3570-3582`). Each iteration selects `VintID_Fade`, waits for one
VInt, updates the palette, and services the PLC.

| Raw CSV frame(s) | ROM phase / VInt handler | Observable RAM effect |
|---:|---|---|
| f0 | **No special-stage init VInt yet.** Recorder observes the `Game_Mode=$10` transition before `SpecialStage` dispatch. | Player slots empty; `SS_Cur_Speed_Factor=0`; `SSTrack_anim_frame=0`. |
| f1-f22 | 22 `VintID_Fade` interrupts for `Pal_FadeToWhite` (`s2.asm:3573-3580`; call at `:6546`). | Players, current speed, and track frame remain zero. |
| f23-f68 | **Disassembly-and-lag-derived inference:** post-fade synchronous initialization. After interrupts are restored at `s2.asm:6613`, the disassembly leaves `Vint_routine=0`, whose dispatch is `Vint_Lag` (`s2.asm:483-484`, `:529-543`), while buffer initialization/decompression/PLC work proceeds (`:6613-6627`). The artifact records lag state and RAM, not PC or `Vint_routine`, so it does not directly prove the handler for each row. | Rows are `lag=1`; player ids are still zero. Raw f23 is the first post-fade lag row and is skipped by replay; inferring `Vint_Lag` is consistent with the disassembly but is not direct capture evidence. |
| f69-f126 | **Same inference, later init phase:** the player-id stores (`s2.asm:6628-6634`) have become visible, followed by background/player-art/palette setup and the `SS_New_Speed_Factor` write (`:6635-6640`). No explicit S2SS wait is armed until `:6644`. | Player ids are visible from f69, but rows remain `lag=1`; recorded `SS_Cur_Speed_Factor` stays zero. The artifact does not capture the exact PC at the f69 transition. |
| f127-f131 | Five `VintID_S2SS` interrupts in the drawing-index wait (`s2.asm:6644-6649`). | Drawing index starts at 0; the five interrupts leave it at 1, 2, 3, 4, then back at 0. At f127 new speed is promoted to current (`s2.asm:960-975`), so `speed_factor` becomes `0xC`; `track_anim_frame` becomes 1 when the index wraps at f131. |
| f132-f136 | Five `VintID_S2SS` interrupts in the duration wait (`s2.asm:6651-6658`). | Duration counts 5,4,3,2,1; `track_anim_frame` becomes 2 at f136. |
| f137 | The one `VintID_CtrlDMA` wait at `s2.asm:6665-6666`. | Raw row is lagged and replay skips it. |
| f138-f159 | 22 `VintID_Fade` interrupts for `Pal_FadeFromWhite` (`s2.asm:3460-3483`, call at `:6672`). | Players/speed remain active; track animation is paused by the fade handler. |
| f160 onward | `VintID_S2SS` intro/gameplay-gate loop (`s2.asm:6674-6690`). | Track animation resumes. The loop cannot advance past `SpecialStage_Started` test `:6689-6690` while the byte is zero. |

## Frame-by-frame replay observations

This table uses the comparator's non-lag step number. A replay step samples the
result of a physical frame but is not itself synonymous with a ROM VInt.

| Replay step | Raw frame | Sampled ROM phase | Observable committed-trace effect |
|---:|---:|---|---|
| 0 | f0 | Mode-entry observation; `SpecialStage` has not yet dispatched. | Players absent; current speed and track frame zero. |
| 1 | f1 | `Pal_FadeToWhite` iteration 1 (`s2.asm:3573-3580`). | No tracked init-state change. |
| 2 | f2 | Fade iteration 2 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 3 | f3 | Fade iteration 3 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 4 | f4 | Fade iteration 4 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 5 | f5 | Fade iteration 5 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 6 | f6 | Fade iteration 6 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 7 | f7 | Fade iteration 7 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 8 | f8 | Fade iteration 8 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 9 | f9 | Fade iteration 9 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 10 | f10 | Fade iteration 10 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 11 | f11 | Fade iteration 11 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 12 | f12 | Fade iteration 12 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 13 | f13 | Fade iteration 13 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 14 | f14 | Fade iteration 14 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 15 | f15 | Fade iteration 15 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 16 | f16 | Fade iteration 16 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 17 | f17 | Fade iteration 17 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 18 | f18 | Fade iteration 18 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 19 | f19 | Fade iteration 19 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 20 | f20 | Fade iteration 20 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 21 | f21 | Fade iteration 21 (`s2.asm:3575-3580`). | No tracked init-state change. |
| 22 | f22 | Fade iteration 22; `dbf` falls through and the fade returns (`s2.asm:3575-3582`). | Last absent/zero pre-roll sample. |
| 23 | f127 | First `VintID_S2SS` drawing-index wait (`s2.asm:6644-6647`). | Players first compared as present; `speed_factor` 0→`0xC`; drawing index 0→1, duration 0→5. |
| 24 | f128 | Second drawing-index wait. | Drawing index 1→2; duration 5→4. |
| 25 | f129 | Third drawing-index wait. | Drawing index 2→3; duration 4→3. |
| 26 | f130 | Fourth drawing-index wait. | Drawing index 3→4; duration 3→2. |
| 27 | f131 | Fifth drawing-index wait; index wraps and loop exits (`s2.asm:6644-6649`). | `track_anim_frame` 0→1; duration 2→1. |
| 28 | f132 | First duration-loop wait (`s2.asm:6651-6658`). | Drawing index 0→1; duration reloads 1→5. |
| 29 | f133 | Second duration-loop wait. | Drawing index 1→2; duration 5→4. |
| 30 | f134 | Third duration-loop wait. | Drawing index 2→3; duration 4→3. |
| 31 | f135 | Fourth duration-loop wait. | Drawing index 3→4; duration 3→2. |
| 32 | f136 | Fifth duration-loop wait; loop exits. | `track_anim_frame` 1→2; player routines have initialized to 2. |
| 33-54 | f138-f159 | 22 `Pal_FadeFromWhite` waits (`s2.asm:3460-3483`, call at `:6672`). | Players/speed remain active; track animation stays paused. |
| 55 onward | f160 onward | Intro/gameplay-gate loop (`s2.asm:6674-6690`). | Track animation resumes; `SpecialStage_Started` gates entry to playable loop. |

## Initialization work between fade and player creation

After `Pal_FadeToWhite` returns, the ROM performs the following work in order:

1. It configures the special-stage VDP registers and masks interrupts
   (`s2.asm:6547-6571`), then DMA-fills the name tables and horizontal-scroll
   table (`:6578-6581`).
2. It clears `SpecialStage_Started` (`s2.asm:6585`) and clears the sprite,
   special-stage shared, display-list, and object RAM regions (`:6587-6603`).
   Thus no stale object id can make a player present during the pre-roll.
3. It restores interrupts, builds the scroll/name-table buffers, decompresses
   the special-stage data, resets the segment, and runs the special-stage PLC
   directly from ROM (`s2.asm:6613-6620`). `ssInitTableBuffers` and
   `RunPLC_ROM` contain no `WaitForVint`; the expensive decompression/PLC work
   nevertheless spans physical VInts. Because the preceding wait leaves
   `Vint_routine=0`, the disassembly implies `Vint_Lag` dispatch during this
   interval. The trace's lag rows support that reading, but do not prove it
   directly because the artifact records neither PC nor `Vint_routine`.
4. It writes the special-stage object ids: Sonic at `s2.asm:6628`, Tails at
   `:6631` when selected, then HUD/banner/ring-count objects at `:6632-6634`.
5. It builds the background, decompresses player art, initializes palettes and
   stage data, and writes `SS_New_Speed_Factor=$000C0000`
   (`s2.asm:6635-6640`). This is the *new* factor; the recorded current factor
   remains zero until the next `Vint_S2SS` copies it.

The PLC/track startup waits are therefore downstream of player creation, not
the cause of the empty-slot window. The first loop waits five `VintID_S2SS`
interrupts for `SSTrack_drawing_index` to return to zero
(`s2.asm:6644-6649`). The second waits another five interrupts for
`SSTrack_duration_timer-1` to reach zero (`:6651-6658`). After object setup and
`RunPLC_RAM`, a single `VintID_CtrlDMA` wait occurs at `:6665-6666`, followed
by the 22-frame fade from white.

`SpecialStage_Started` remains clear through all of this. The start-banner
object sets it only after its countdown/message hand-off (`s2.asm:9734-9746`),
which releases the gate at `s2.asm:6689`. The current committed trace does not
record that byte directly; the campaign's recorder-extension task adds that
auxiliary transition evidence.

## Committed trace cross-check

The check below is against
`src/test/resources/traces/s2/special_stage/physics.csv.gz`, pinned by the
compressed artifact's SHA-256:
`de6174b8de48f4aa1541e654d9e334c0442183778cf1fbd42c4e749f499a43df`.

| Event | Replay/non-lag clock | Raw CSV evidence | ROM interpretation |
|---|---:|---:|---|
| Last absent/zero pre-roll sample | step 22 | f22 | End of the 23-slot pre-roll window f0-f22. |
| Sonic/Tails first present | step 23 | ids first appear during lag at f69; first compared observation is f127 | Object-id writes at `s2.asm:6628-6631`. |
| `speed_factor` 0→`c` | step 23 | f127 | New factor written at `s2.asm:6640`, then promoted to current by the first `Vint_S2SS` (`:960-975`). |
| `track_anim_frame` first moves 0→1 | step 27 | f131 | Fifth VInt of the first drawing-index wait, when index 4 wraps to 0. |
| `track_anim_frame` moves 1→2 | step 32 | f136 | Fifth VInt of the duration wait. |
| Track resumes after fade from white | step 55 | f160 | First `VintID_S2SS` in the gated intro loop (`s2.asm:6679-6690`). |

The raw rows explain why a direct `frame`-column lookup appears different from
the required f23 boundary: raw f23-f126 are `lag=1` init rows and the replay
does not step the engine for them. Specifically, players are still absent
through raw f68, their ids are visible from raw f69, and current speed remains
zero through raw f126. The first non-lag row after that interval is raw f127,
which is replay step 23 and simultaneously exposes both players and current
speed `0xC`. There is no conflict between the disassembly and the committed
trace once the recorder's physical-frame clock and the replay's non-lag clock
are kept distinct.
