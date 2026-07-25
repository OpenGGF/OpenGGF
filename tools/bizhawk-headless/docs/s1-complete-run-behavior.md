# S1 Complete-Run Recorder — Byte-Level Segmentation Specification

Authoritative specification of the COMPLETE-RUN (multi-level-segment) behavior
of `tools/bizhawk/s1_complete_run_recorder.lua` (1764 lines, currently
stamping `lua_script_version` **"3.17"** — see §2) with
`tools/bizhawk/lib/oggf_trace_common.lua`, for the native C# port in
`tools/bizhawk-headless/`.

**Scope:** the level-segment state machine over one full S1 playthrough of a
single BK2. Run-mode / special-stage detours (`GM_Special 0x10`,
`start_ss_segment`, `write_ss_metadata`, `write_ss_row`,
`finalize_ss_segment`, `write_run_manifest`, `transitions_done`,
`OGGF_TRACE_RUN_ID`) are **excluded — a sibling doc covers those**. Everything
here assumes the stage-free path: no `$10` frame ever occurs,
`OGGF_TRACE_RUN_ID` is unset, so `write_run_manifest` returns without writing
(`#transitions_done == 0 and run_id == nil`) and the output layout is
exactly the per-level directories.

Where this recorder's per-frame byte output is identical to the standard S1
recorder, this doc references
[s1-trace-recorder-behavior.md](s1-trace-recorder-behavior.md) ("STD spec")
instead of repeating it. The Lua source is the behavioral authority; every
claim below was verified against the Lua and against real fixture bytes.

## 0. Canonical fixtures (read-only ground truth)

Source movie: `src/test/resources/traces/s1/_movies/s1-complete-run.bk2` —
**195,493 input rows** (verified from `Input Log.txt`). One playback of this
movie yields **19 level segments**. Committed fixtures (each
`physics.csv.gz` + `aux_state.jsonl.gz` + plain `metadata.json`; all stamped
`lua_script_version "3.14"`, `trace_schema 4`, `csv_version 7`,
`recording_date 2026-07-13`):

| Fixture dir | Recorder dir token | zone | zone_id | act | bk2_frame_offset | trace_frame_count |
|---|---|---|---|---|---|---|
| `ghz1_completerun` | `ghz1` | ghz | 0 | 1 | 788 | 5598 |
| `ghz2_completerun` | `ghz2` | ghz | 0 | 2 | 6622 | 4028 |
| `ghz3_completerun` | `ghz3` | ghz | 0 | 3 | 10885 | 9678 |
| `mz1_completerun` | `mz1` | mz | 2 | 1 | 20791 | 8060 |
| `mz2_completerun` | `mz2` | mz | 2 | 2 | 29079 | 14136 |
| `mz3_completerun` | `mz3` | mz | 2 | 3 | 43443 | 17875 |
| `syz1_completerun` | `syz1` | syz | 4 | 1 | 61548 | 9729 |
| `syz2_completerun` | `syz2` | syz | 4 | 2 | 71507 | 7994 |
| `syz3_completerun` | `syz3` | syz | 4 | 3 | 79731 | 13710 |
| `lz1_completerun` | `lz1` | lz | 1 | 1 | 93657 | 13070 |
| `lz2_completerun` | `lz2` | lz | 1 | 2 | 106944 | 10173 |
| `lz3_completerun` | `lz3` | lz | 1 | 3 | 117333 | 19107 |
| `slz1_completerun` | `slz1` | slz | 3 | 1 | 136660 | 6411 |
| `slz2_completerun` | `slz2` | slz | 3 | 2 | 143290 | 5894 |
| `slz3_completerun` | `slz3` | slz | 3 | 3 | 149403 | 13732 |
| `sbz1_completerun` | `sbz1` | sbz | 5 | 1 | 163354 | 7619 |
| `sbz2_completerun` | `sbz2` | sbz | 5 | 2 | 171193 | 9594 |
| `sbz3_completerun` | **`lz4`** | **lz** | **1** | **4** | 181004 | 8354 |
| `fz_completerun` | **`sbz3`** | **sbz** | **5** | **3** | 189578 | 4457 |

Two naming landmines, both ROM-driven (see §4.3):

- **SBZ3 is ROM-encoded as LZ act 4** (`v_zone=1`, `v_act=3`). The recorder
  therefore writes it to directory `lz4/` with metadata
  `"zone": "lz", "zone_id": 1, "act": 4`. The committed fixture dir was
  *renamed by hand* to `sbz3_completerun` — the rename is a fixture-curation
  step, NOT recorder behavior.
- **Final Zone is ROM-encoded as SBZ act 3** (`v_zone=5`, `v_act=2`). The
  recorder writes it to `sbz3/` with `"zone": "sbz", "zone_id": 5, "act": 3`;
  the fixture dir was renamed to `fz_completerun`.

The last segment (FZ) ends at BK2 frame 189578 + 4457 = 194035; the remaining
~1,458 movie rows drive the ending cutscene and are never recorded (§4.5).

**The 8 `credits_*` fixture dirs are NOT emitted by this recorder.** Their
metadata reads `"lua_script_version": "credits-retro-1.4"`, `"csv_version": 4`,
`"trace_type": "credits_demo"`, `"input_source": "rom_ending_demo"`,
`"bk2_frame_offset": 0` — a different (stable-retro credits) pipeline with a
different, 20-column CSV. S1's credits demos run under `GM_Credits ($1C)` and
this recorder only ever arms on game mode `0x0C` (§4.1), so the same pass
cannot produce them. They are out of scope for the native complete-run port.

## 1. Relationship to the standard S1 recorder (byte-shared vs different)

Byte-**shared** with the STD spec (do not re-derive; the templates and
semantics are identical):

- RAM address map (STD §1) — plus four extra global reads listed in §6 here.
- Frame-loop / post-advance inspection model and the "detection frame is not
  recorded" dead-frame rule (STD §2.1–§2.2), applied *per segment arm*.
- CSV v7 header and 42-field row format string, `uhex`, `ground_mode`
  (STD §3) — **identical bytes**, including the constant sidekick block
  `0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00`.
- Input mask derivation from BK2 row `bk2_frame_offset + N` via
  `bk2_input_mask` / `movie.getinput(offset + row, 1)`, Start excluded, A|B|C
  collapsed to `0x10` (STD §4). `bk2_frame_offset` here is the *current
  segment's* offset — `start_ss_segment`-free runs re-base it at every arm.
- Aux templates `routine_change`, `state_snapshot`, `mode_change`,
  `object_appeared`, `object_removed`, `s1_obj64_state`, `slot_dump`,
  `cursor_state` (STD §5.1–§5.6, §5.8–§5.9) — identical format strings.
- File encoding of this fixture set (LF-only; §8).

**Different** from the STD spec:

1. Multi-segment state machine: mode-exit **finalizes and re-arms** instead of
   terminating the run (§4).
2. Per-segment output directories under `BASE_OUTPUT_DIR` with
   `next_segment_dir_token` naming (§4.3).
3. Cross-segment tracker carry-over — `prev_status`, `prev_routine`,
   `prev_ctrl_lock`, `prev_opl_screen`, `known_objects` are **never reset**
   between segments (§5).
4. Extended `object_near` template (7 extra fields; §6.1).
5. Four additional per-frame aux events — `v_objstate`, `camera_boundary`,
   `v_oscillate`, `lag_state` — emitted every recorded frame after
   `scan_objects`, before `cursor_state` (§6.2).
6. Env-gated `rng_call` diagnostic event (§6.3) — absent from all fixtures.
7. `metadata.json` has three extra keys vs STD §7: a 9-entry
   `aux_schema_extras` list, and a trailing `source_bk2` key (§7).
8. End-of-movie handling is a top-of-function guard + `finalize_run_end`
   funnel, with an absolute frame cap (§4.5).

## 2. Version history: 3.14 fixtures vs the 3.17 Lua — verified byte deltas

**The in-file version-history comment block has NO entries for 3.13, 3.15,
3.16, or 3.17.** The header changelog covers v2.0–v3.12 plus one v3.14 entry
(inserted out of order at line 54):

> `-- v3.14 changes: CSV v7 records the player's animation ID and displayed`
> `-- mapping frame every frame using the shared Player/Sidekick layout.`

There is therefore **no in-file comment claiming that 3.15 output is
byte-identical to 3.14 apart from the version string**. Per the migration's
version rule that claim must not be assumed; it was instead **verified by
diffing the actual version-bump commits**:

- `e00abcd8d` stamped **"3.14"** (CSV v7 animation columns — the fixture
  state).
- `203e647b8` (`feat(trace): s1 recorder stage-detour state machine + maze
  writer + run manifest`) stamped **"3.15"**. Diff vs 3.14, restricted to the
  stage-free level path: the ONLY output-byte change is the
  `"lua_script_version"` line in `metadata.json`. All detour/manifest code is
  unreachable without a `$10` frame, and the manifest writer itself is gated —
  its own comment states: *"Only emitted when a detour occurred or
  OGGF_TRACE_RUN_ID is set, so plain single-stage complete-run regenerations
  remain output-identical."* (That gate comment is the closest thing to a
  byte-compat claim that exists in the file.)
- **"3.16" was never stamped in this file** (`git log -S'"3.16"'` finds
  nothing).
- `08424b744` (between the 3.15 and 3.17 stamps, no version bump) added only
  the `pcall(client.SetSoundOn, false)` fast-headless launcher-guard snippet —
  stdout/toggle only, zero output bytes.
- `b1a810536` (`feat: replay Sonic 1 100% movie through credits`) jumped
  **3.15 → "3.17"**. Level-path deltas, all output-neutral under default env:
  (a) `BASE_OUTPUT_DIR` now honors `OGGF_TRACE_OUTPUT_DIR` (layout only);
  (b) `source_bk2` value now comes from `OGGF_TRACE_SOURCE_BK2` (default
  `"s1-complete-run.bk2"`) and is written via Lua `%q` — for the default name
  `%q` produces the identical bytes `"s1-complete-run.bk2"`;
  (c) `S1_RNG_CALLS` FZ diagnostics behind `OGGF_S1_RNG_CALL_RANGE`, which
  when enabled append `, "rng_call_per_frame"` to `aux_schema_extras` (FZ
  segments only) and emit `rng_call` aux events — disabled by default;
  (d) `next_segment_dir_token` extracted as a function (same tokens).
- `fd3a74291` extracted leaf helpers into `oggf_trace_common.lua` — its module
  header asserts *"byte-for-byte identical to the inline copies they
  replace"* (verified: identical format strings). `2f8926778` only guards
  `client.invisibleemulation` (no output bytes).

**Net result:** with default environment, the current Lua's level-segment
output vs the 3.14-stamped `*_completerun` fixtures differs in EXACTLY one
place: `  "lua_script_version": "3.17",` vs `"3.14"` in each
`metadata.json`. `physics.csv` and `aux_state.jsonl` are byte-identical. The
native differential gate must allow that single-line normalization (plus
`recording_date`) and nothing else; if any other byte differs, it is a port
bug, not a version delta. Note also the stale startup banner
`print("S1 Trace Recorder v3.7 loaded. ...")` — stdout only, never in files.

## 3. Configuration inputs (level path)

| Env var | Default | Effect |
|---|---|---|
| `OGGF_TRACE_OUTPUT_DIR` | `trace_output/` | `BASE_OUTPUT_DIR`; a trailing `/` is appended if the value ends with neither `/` nor `\` |
| `OGGF_TRACE_SOURCE_BK2` | `s1-complete-run.bk2` | `source_bk2` metadata value (Lua `%q`-quoted) |
| `S1_STOP_AT_FRAME` | `0` (off) | Hard stop when `emu.framecount() >= value` (§4.5); re-read via `os.getenv` **every frame** |
| `OGGF_S1_RNG_CALL_RANGE` | unset | `<first>-<last>` segment-local frames; enables §6.3 |
| `OGGF_TRACE_VISIBLE` | unset | `1` keeps the EmuHawk window visible (no output bytes) |
| `OGGF_BIZHAWK_LIB` | unset | Shared-lib directory override (loader only) |

`MOVIE_FRAME_SAFETY_MARGIN = 30` is declared and **never used** — do not
port. Directory pre-creation (`precreate_segment_dirs`: `BASE_OUTPUT_DIR`,
`<zone><act>/` for every `ZONE_NAMES` entry × acts 1–4, plus bare `ss/`) and
`ensure_segment_dir` are I/O hygiene with no output-byte effect; the native
port may create directories on demand.

## 4. The level-segment state machine

State: `started` (armed), `finished` (terminal, never re-arms), `trace_frame`
(segment-local row index), `bk2_frame_offset` (segment-local), the
`start_*` captures, and the carry-over trackers (§5). `segments_done` /
`segment_dir_counts` persist for the whole pass.

Each `on_frame_end()` (post-advance, pre-record) evaluates in this order:

1. **Global stop guard** (§4.5) — not gated on `started`.
2. Special-stage branches — unreachable in a stage-free run (sibling doc).
3. **If not `started`:** arm gate (§4.1); returns without recording either way.
4. **If `started` and `game_mode != 0x0C`:** finalize + re-arm posture (§4.2);
   returns without recording.
5. Shadowed in-loop movie guards (§4.5) — dead code since v3.6.
6. Record row `trace_frame` (§6), `trace_frame += 1`.

### 4.1 Segment arm (start detection)

Identical predicate to STD §2.2, evaluated whenever `started == false` and
`finished == false`:

```
game_mode (u8 @0xF600) == 0x0C  AND  ctrl_lock (u16be @0xD03E) == 0
```

On first fire: `bk2_frame_offset := emu.framecount()`; capture `start_x`
(u16be `0xD008`), `start_y` (u16be `0xD00C`), `start_rng_seed` (u32be
`0xF636`), `start_zone_id` (u8 `0xFE10`), `start_act` (u8 `0xFE11`),
`start_zone_name` (§4.3); `trace_frame := 0`; compute the dir token and set
`OUTPUT_DIR = BASE_OUTPUT_DIR .. token .. "/"`; open `physics.csv` (write the
v7 header) + `aux_state.jsonl`; **write `metadata.json` immediately**
(crash insurance — rewritten every 300 rows and at finalize; only final bytes
matter). The detection frame itself is NOT recorded (dead-frame rule); row 0
is written by the next `on_frame_end()`. Trace row N of segment k = state
after applying BK2 row `offset_k + N`.

There is no zone/act carve-out anywhere: arming is purely
`game_mode`/`ctrl_lock`-driven, and zone/act are only *read* for naming and
metadata.

### 4.2 Segment finalize on mode exit (level transitions, FZ, ending)

When `started` and the post-advance `game_mode != 0x0C` (the first such
frame): flush CSV, `write_metadata()` (final `trace_frame_count`), append the
in-memory `segments_done` entry, close both files, `started := false`,
`trace_frame := 0`, return. **The mode-exit frame is never recorded.** The
recorder then idles (manifest-free, byte-free) until the arm gate fires again.

Boundary taxonomy over the complete run (all the SAME code path — the
recorder models only the `game_mode` byte):

- **Act/zone transitions:** game mode goes `0x0C -> 0x8C -> 0x0C` (bit 7 set
  during the got-through/title-card sequence). The first `0x8C` frame ends
  the segment; the next `0x0C` frame with `ctrl_lock == 0` arms the next.
  Fixture gaps are the title-card windows (e.g. GHZ1 ends at 788+5598=6386;
  GHZ2 arms at 6622).
- **Deaths / in-mode restarts do NOT split segments.** An S1 death keeps
  `v_gamemode == 0x0C` (the restart loops inside GM_Level), so recording
  continues uninterrupted through death, reload, and title-card control lock
  — visible only as `routine_change` to `0x06` etc. in aux. Only a mode-byte
  change ends a segment. (The canonical movie is deathless; this is the
  coded behavior, not an exercised fixture path.)
- **FZ:** ROM `sbz` act 3 (§0); ends when the ending sequence flips the mode
  away from `0x0C`.
- **Ending & credits:** GM_Ending/GM_Credits are not `0x0C`, so nothing arms
  after FZ — no `endz*` directory is ever created by playback (the
  `endz1..endz4` pre-created dirs stay empty), and no credits segments exist
  (§0).

### 4.3 Directory naming — exactly as the Lua builds it

```
start_zone_name = ZONE_NAMES[start_zone_id] or string.format("unknown_%02x", start_zone_id)
base_token      = start_zone_name .. tostring(start_act + 1)
dir_token       = next_segment_dir_token(base_token)
OUTPUT_DIR      = BASE_OUTPUT_DIR .. dir_token .. "/"
```

`ZONE_NAMES` (matches s1disasm `Constants.asm`): `0="ghz"`, `1="lz"`,
`2="mz"`, `3="slz"`, `4="syz"`, `5="sbz"`, `6="endz"`, `7="ss"`; unknown ids
use `unknown_%02x` (**lowercase** hex). `next_segment_dir_token` counts per
base token: the first arm of a token yields the bare token; the n-th (n>1)
yields `<token>_<n>` (e.g. `ghz1_2`). In the canonical run every base token
is unique, so **no `_2` suffix ever appears**; suffixes only arise when a run
re-enters the same `<zone><act>` (special-stage roundtrips, mode round-trips
— run-mode territory). The 19 tokens in order are: `ghz1 ghz2 ghz3 mz1 mz2
mz3 syz1 syz2 syz3 lz1 lz2 lz3 slz1 slz2 slz3 sbz1 sbz2 lz4 sbz3` (§0 table;
note `lz4` = SBZ3, final `sbz3` = FZ).

### 4.4 Per-segment offset/count derivation

- `bk2_frame_offset` = `emu.framecount()` at that segment's arm frame — the
  count of frames completed before arming; equivalently the index of the
  first BK2 input row consumed after arming. Native translation: with rows
  `0..r-1` applied so far, arming after advance r-1 sets `offset := r`
  (identical to STD §2.3's convention, re-run per segment).
- `trace_frame_count` = number of CSV data rows written for the segment
  (`trace_frame` at finalize). Rows are indexed `0..count-1`; fixture line
  count = count + 1 header line (ghz1: 5599 lines). The last recorded frame
  is the last `0x0C` frame before the mode exit.

### 4.5 End-of-movie / stop handling

Top-of-function guard, evaluated **before anything else, every frame, not
gated on `started`** (v3.6 semantics):

```
stop_at      = tonumber(os.getenv("S1_STOP_AT_FRAME") or "0")
movie_done   = (movie_len > 0 and emu.framecount() >= movie_len)
               or (movie.isloaded() and movie.mode() == "FINISHED")
stop_reached = stop_at > 0 and emu.framecount() >= stop_at
if stop_reached or movie_done then finalize_run_end(); finished = true; return end
```

`finalize_run_end()` on the level path: if `started`, flush + final
`write_metadata()` + append `segments_done` + close files; then
`write_run_manifest()` which is a **no-op** for stage-free runs. Effects:

- **Normal complete run:** FZ was already finalized by the mode-exit branch
  (§4.2), so `started == false` when `emu.framecount()` reaches 195,493 —
  the guard fires, writes nothing, and the pass exits. Movie-end handling
  contributes zero output bytes.
- **Movie ending mid-segment** (short/truncated BK2): per the pinned BizHawk
  2.11 ordering (STD §2.4), `movie.mode() == "FINISHED"` is observed on the
  `on_frame_end` after the advance that consumed the movie's LAST input row,
  so that frame is **never recorded** — the segment's final row is the one
  fed by the second-to-last input row. The native pre-advance predicate
  `offset + N + 1 >= rows` (STD §2.3(a)) reproduces this exactly.
- `S1_STOP_AT_FRAME` mid-segment finalizes a truncated but well-formed
  segment (metadata reflects rows written so far).
- The two in-loop guards (BK2-end row check and `FINISHED` check inside the
  recording path) are **shadowed dead code since v3.6** — the top guard fires
  strictly earlier on the same predicates; both also funnel through
  `finalize_run_end` as belt-and-braces.
- Backstop: `FRAME_CAP = movie.length() + 64` (load-time; `2,000,000` if no
  movie length) forces `finalize_run_end` + exit if every stop signal fails.

## 5. Cross-segment tracker carry-over (fixture-verified)

`prev_status`, `prev_routine`, `prev_ctrl_lock`, `prev_opl_screen`, and
`known_objects` are file-scope and are **NOT reset at finalize or re-arm**.
A native port that naively re-initializes them per segment will diverge on
frame 0 of every segment after the first. Verified consequences in fixtures:

- **`routine_change` `0x00 -> 0x02` fires on frame 0 of the FIRST segment
  only** (`prev_routine` init 0). GHZ2 frame 0 has NO routine_change —
  `prev_routine` was already `0x02` from GHZ1's last row.
- **`object_appeared`/`object_removed` on a segment's frame 0 are diffs
  against the PREVIOUS segment's last recorded frame.** GHZ2 frame 0 emits
  `object_removed` for slots 23–29 (GHZ1's leftover `0x3A` got-through-card
  pieces) and emits no `object_appeared` for slots whose id is unchanged
  (e.g. slot 1 HUD `0x21` persists silently). Only the very first segment
  sees the "every occupied slot appears" pattern of STD §5.4.
- **`cursor_state` on frame 0 of later segments compares against the previous
  segment's last `opl_screen`** and can emit `"dir":"L"` (GHZ2 frame 0:
  `opl_screen 0x0000` < GHZ1's final `0x2480` → `"L"`; GHZ1's last
  `cursor_state` is frame 4862, `opl_screen "0x2480"`); a fresh tracker
  (`prev = -1`) would have said `"R"`. It also does NOT fire at all if the
  new segment's first `opl_screen` equals the previous segment's last.
- `prev_status` / `prev_ctrl_lock` carry-over suppresses spurious
  `mode_change` events at segment starts when the bits match across the gap.
- `lag_state.lagcount` is `emu.lagcount()` — **cumulative since emulator
  boot, monotone across the whole pass**, including pre-arm boot frames and
  inter-segment gaps (fixtures: 347 at GHZ1 row 0 → 386 at GHZ2 row 0 →
  2937 at FZ row 0). It is NOT per-segment. `lagged` is `emu.islagged()` for
  the current frame. The native port must reproduce the emulator's exact lag
  accounting from power-on.

`SNAPSHOT_INTERVAL`-driven `state_snapshot`s and the 60-frame flush / 300-
frame metadata cadences key off the segment-local `trace_frame`, which DOES
reset to 0 per segment.

## 6. Per-frame recording — deltas vs STD §5/§6

Emission order per recorded row N (fixture-verified on GHZ1 frames 0 and 60):

1. CSV v7 row (byte-identical format to STD §3).
2. `check_mode_changes`: air (+snapshot), rolling, on_object, control_locked,
   routine_change (+snapshot on `0x04`/`0x06`) — STD §5.1–§5.3 templates.
3. `prev_status := status`.
4. Periodic `state_snapshot` if `trace_frame % 60 == 0`.
5. `scan_objects` (slots 1..127 ascending): `object_appeared` /
   `object_removed` / `s1_obj64_state` (id `0x64`) / **extended**
   `object_near` (§6.1) / `known_objects` update; then `slot_dump` iff any
   appearance.
6. **`v_objstate`** (§6.2), **`camera_boundary`**, **`v_oscillate`**,
   **`lag_state`** — every frame, in that order.
7. `S1_RNG_CALLS.flush()` — `rng_call` event only when armed (§6.3).
8. `cursor_state` check/emit (STD §5.9).
9. `trace_frame += 1`.

### 6.1 Extended `object_near` (VERBATIM template)

```
'{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%02X","x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X","obj_frame":"0x%02X","routine2":"0x%02X","objoff_3c":"0x%08X","objoff_32":"0x%04X","objoff_34":"0x%04X","objoff_36":"0x%04X","objoff_38":"0x%04X"}'
```

Same proximity gate as STD §5.7 (`dx <= 160 AND dy <= 160` vs the CSV row's
player x/y). Extra args after `status`: u8 `+0x1A` (obj_frame /
`OFF_ANIM_FRAME_DISP`), u8 `+0x25` (`ob2ndRout`), u32be `+0x3C`, u16be
`+0x32`, u16be `+0x34`, u16be `+0x36`, u16be `+0x38`. Note the key order:
`objoff_3c` precedes `objoff_32`.

### 6.2 Per-frame diagnostic events (no `vfc` field in any of these)

```
'{"frame":%d,"event":"v_objstate","bytes":"%s"}'
```
`bytes` = 192 bytes at `0xFC00`..`0xFCBF` as 384 uppercase hex chars
(`%02X` each, concatenated).

```
'{"frame":%d,"event":"camera_boundary","limitbtm1":"0x%04X","limitbtm2":"0x%04X","lookshift":"0x%04X","bgscrollvert":"0x%02X"}'
```
u16be `0xF726`, u16be `0xF72E`, u16be `0xF73E`, u8 `0xF75C`.

```
'{"frame":%d,"event":"v_oscillate","bytes":"%s"}'
```
`bytes` = 0x42 (66) bytes at `0xFE5E`..`0xFE9F` as 132 uppercase hex chars.

```
'{"frame":%d,"event":"lag_state","lagged":%s,"lagcount":%d}'
```
`lagged` = bare `true`/`false` from `emu.islagged()`; `lagcount` =
`emu.lagcount()` (cumulative; §5). The Lua defensively falls back to
`false`/`-1` if the `emu` API is missing — never happens on the pinned build.

Each of these four appears exactly `trace_frame_count` times per segment
(GHZ1: 5598 each).

### 6.3 `rng_call` (env-gated; NOT in any fixture)

Only when `OGGF_S1_RNG_CALL_RANGE=<first>-<last>` parses: `event.
onmemoryexecute` hooks at ROM PCs `0x0029AC` (RandomNumber), `0x01A6DE`,
`0x01A6F8`, `0x01A700` (FZ boss contact probes) accumulate hits; every hook
additionally gates on `started`, segment-local `trace_frame` within range,
and RAM `zone==5 && act==2` (Final Zone — a ROM-state gate, not a route
carve-out). `S1_RNG_CALLS.flush()` emits one
`{"frame":..,"vfc":..,"event":"rng_call","hits":[...]}` line per frame with
hits. When enabled AND the segment is FZ, `write_metadata` appends
`, "rng_call_per_frame"` to `aux_schema_extras`. Default-off: the native port
needs it only if it reproduces the diagnostic mode; the differential gates
never see it.

## 7. metadata.json — exact byte layout (level segments)

Exact template (2-space indent, LF lines, trailing `\n` after `}`; written at
arm, every 300 rows, and at finalize — only final bytes matter):

```
{
  "game": "s1",
  "zone": "<start_zone_name>",
  "zone_id": <start_zone_id>,
  "act": <start_act + 1>,
  "bk2_frame_offset": <offset>,
  "trace_frame_count": <trace_frame>,
  "start_x": "0x<%04X of start_x>",
  "start_y": "0x<%04X of start_y>",
  "characters": ["sonic"],
  "main_character": "sonic",
  "sidekicks": [],
  "rng_seed": "0x<%08X of start_rng_seed>",
  "recording_date": "<%Y-%m-%d>",
  "lua_script_version": "3.17",
  "trace_schema": 4,
  "csv_version": 7,
  "aux_schema_extras": ["s1_obj64_state_per_frame", "object_near_obj_frame", "v_objstate_per_frame", "camera_boundary_per_frame", "object_near_routine2_objoff3c", "object_near_objoff_34_36_38", "v_oscillate_per_frame", "lag_state_per_frame", "object_near_objoff_32"],
  "rom_checksum": "",
  "notes": "",
  "source_bk2": "s1-complete-run.bk2"
}
```

- All `start_*`-derived values come from the segment's **arm-frame** RAM
  (§4.1), never end-of-segment state. `act` = raw `v_act` + 1. `zone` /
  `zone_id` are the raw ROM values — hence `lz`/1/4 for SBZ3 and `sbz`/5/3
  for FZ (§0). There is **no `route` key** and no `trace_profile` key in
  level metadata (those live in the run manifest / SS metadata — sibling
  doc).
- `rng_seed` = `"0x%08X"` of u32be `0xF636` at arm — `0x00000000` for every
  segment of the canonical run.
- `source_bk2` is the **last** key (no trailing comma), value =
  `string.format('%q', source_bk2_name)`; for the default and any
  metacharacter-free name this equals plain `"<name>"`. The 3.14 fixtures
  carry the identical bytes from the then-hardcoded literal.
- The conditional `, "rng_call_per_frame"` extras entry (§6.3) is the only
  data-driven variation; never present in fixtures.
- `rom_checksum` is the empty string in LEVEL metadata (the `AFE05EEE`
  literal exists only in the run-manifest writer — sibling doc).
- Nondeterministic field: `recording_date` only. Version rule: vs the
  3.14-stamped fixtures the `lua_script_version` line is additionally
  normalized per the §2 verification; **no other byte may differ**.

## 8. File encodings — as observed in this fixture set

All 19 `*_completerun` fixtures (gunzipped `physics.csv` / `aux_state.jsonl`
and the plain `metadata.json`): pure ASCII, **LF-only** (zero `0x0D` bytes),
final byte `0x0A`, no BOM. This **differs from the
`runs/s1-ghz-maze-roundtrip/` fixture set, which is CRLF** throughout
(Windows text-mode artifact; see `docs/s2-run-mode-behavior.md` §9 and
`Program.cs` `ExpandRunNewlines`) — line-ending policy is therefore **per
fixture set** and the complete-run gate must produce/compare LF bytes
exactly, with no CRLF expansion.

## 9. Intentional non-differences (native vs Lua)

Identical to STD §9 (stdout, flush/metadata cadence, headless speed toggles,
mkdir strategy), plus complete-run specifics with no output-byte impact: the
`precreate_segment_dirs` probe/mkdir dance, `ensure_segment_dir`,
`client.exit()` retry/pause tail, the stale "v3.7 loaded" banner, the
`segments_done` in-memory accumulation (unobservable without a manifest), and
the dead declarations `MOVIE_FRAME_SAFETY_MARGIN`, `ADDR_CTRL1`
(the `0xF604` reads use the literal, not the constant), `ADDR_CTRL1_DUP`,
`ADDR_OPL_ROUTINE`, `OFF_ANIM_FRAME`, `OFF_ANIM_TIMER`, `OFF_STICK_CONVEX`
(byte at `+0x38`; the `object_near` WORD `objoff_38` at the same offset IS
read), and the RAM-input fallback `rom_joypad_to_mask` (never triggers with a
movie loaded).
