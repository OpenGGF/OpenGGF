# S2 Trace Recorder — Run Mode (OGGF_TRACE_RUN_ID) Byte-Level Behavior Spec

Authority: `tools/bizhawk/s2_trace_recorder.lua` v`9.12-s2` (the Lua recorder is
the behavioral authority; where any spec text and the Lua disagree, the Lua
wins). Consumer contract: `src/main/java/com/openggf/trace/TraceRunManifest.java`.
Canonical fixture (read-only ground truth):
`src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/`.

This document specifies the third operating mode of the S2 recorder: the
multi-stage "run" mode enabled by the environment variable `OGGF_TRACE_RUN_ID`.
It covers the stage-detour state machine (level -> special stage -> level), the
minimal special-stage writer, per-segment output layout, and
`run_manifest.json`. Plain-mode behavior (profiles `gameplay_unlock` /
`level_gated_reset_aware` without a run id) is specified elsewhere; run mode is
built on top of the plain level recorder and reuses it verbatim for level
segments (see §8).

Line/section references below are to `s2_trace_recorder.lua` at v9.12-s2.

---

## 1. Activation and global effect

```
run_id = os.getenv("OGGF_TRACE_RUN_ID") or nil          -- L146
```

- Run mode is active **iff `run_id ~= nil`** (any non-empty *or empty* string —
  the Lua does not test `#run_id`, only `os.getenv` returning non-nil). Every
  run-mode behavior in the file is gated on `run_id ~= nil`; with the env var
  unset, the recorder is byte-identical to plain mode (the v9.12 header
  declares plain-mode output byte-identical to 9.11-s2 except the version
  string).
- Run-mode state is held in **globals** (not `local`s): `run_id`,
  `segments_done`, `transitions_done`, `detour_active`
  (`nil | "special_stage"`), `current_segment_dir_token`, `current_ss_index`,
  `level_segment_count`, `ss_segment_count`, `effective_output_dir`.
- `effective_output_dir` starts equal to `OUTPUT_DIR` (`"trace_output/"`). All
  file opens in `open_files()` / `write_metadata()` / `reset_recording_state()`
  target `effective_output_dir`. Run mode reassigns it per segment;
  `OUTPUT_DIR` itself stays the run root — `run_manifest.json` is the only file
  written directly at `OUTPUT_DIR`.
- The run capture procedure does **not** set `OGGF_S2_TRACE_PROFILE`, so
  `TRACE_PROFILE` is the default `"gameplay_unlock"` and every level segment
  (and manifest level entry) carries exactly that string.

### Output layout

Plain mode writes one flat directory
(`OUTPUT_DIR/{physics.csv,aux_state.jsonl,metadata.json}`). Run mode instead
produces:

```
OUTPUT_DIR/
  run_manifest.json
  seg1_<zone><act>/   physics.csv aux_state.jsonl metadata.json
  ss/                 physics.csv aux_state.jsonl metadata.json
  seg2_<zone><act>/   ...
  ss_2/               ...
  seg3_<zone><act>/   ...
```

### Segment directory naming (exact)

- **Level segments** (L1652-1657): armed level segments consume a level-only
  counter `level_segment_count` (starts 0, incremented at each level arm) and
  use token

  ```
  string.format("seg%d_%s%d", level_segment_count, start_zone_name,
                apparent_act_for(start_rom_zone_id, start_act) + 1)
  ```

  i.e. `seg1_ehz1`, `seg2_ehz1`, ... — zone short name from `ZONE_NAMES`
  (e.g. `ehz`), act 1-based with the MTZ zone-id-0x05 `+2` apparent-act
  adjustment. **SS segments do not consume a number** — using
  `#segments_done` would wrongly yield `seg3_` for the return level because
  the ss entry sits between the two level entries in `segments_done`.
- **Special-stage segments** (L868-873): `ss_segment_count` increments per
  detour; first detour's token is bare `"ss"`, repeats are
  `"ss_" .. ss_segment_count` → `ss`, `ss_2`, `ss_3`, ...
- Each segment dir is created on arm via
  `os.execute("mkdir \"<dir>\" 2>NUL")` (`ensure_output_dir`, L712-714), and
  `effective_output_dir = OUTPUT_DIR .. token .. "/"`.

---

## 2. The detour state machine (on_frame_end structure)

`on_frame_end()` in this file is structurally **inverted** vs the S1/S3K
complete-run recorders: its `if not started` arm gate comes first with an
unconditional `return`. The run-mode machinery therefore sits **above** that
gate, in this order (placement is load-bearing, L1498-1505):

1. **4b. Top-of-function movie-done guard** (run mode only, L1474-1490):
   if `HEADLESS and movie.isloaded()` and
   (`effective_movie_length > 0 and emu.framecount() >=
   effective_movie_length` (where
   `effective_movie_length = max(movie.length(), OGGF_BK2_FRAME_COUNT)` —
   the env value only ever **raises** the length, never lowers it; the
   `> 0` test keeps a zero-length report from finalizing instantly) **or**
   `movie.mode() == "FINISHED"`) → `finalize_run_end()`, `finished = true`,
   return. Rationale: the plain BK2-end checks sit *below* the detour
   branch's returns, so without this guard a movie ending mid-`$10` would
   keep writing ss rows until FRAME_CAP.
2. **Block 1 — SS entry/continuation** (L1512-1555): gate
   `run_id ~= nil and started and game_mode == 0x10`
   (`GAMEMODE_SPECIAL_STAGE`). The `started` requirement means a movie that
   reaches `$10` while no level segment is armed can never create an ss
   segment or a bogus `from_segment = -1` transition.
   - **Entry** (`detour_active ~= "special_stage"`): finalize the armed level
     segment, push the `starpost_special` transition, arm the ss segment,
     set `detour_active = "special_stage"`, **return without writing an ss
     row** (see §4 alignment).
   - **Continuation** (`detour_active == "special_stage"`): `write_ss_row()`
     then return. Because this returns first, the "left level gameplay"
     branch below can never double-finalize on a `$10` frame.
3. **Block 2 — SS exit** (L1556-1570): gate `detour_active ==
   "special_stage"` with game_mode now ≠ `$10` (first non-`$10` frame after
   the detour — the results tally trailing off `$10`, or the return load
   handoff). `finalize_ss_segment()`, `detour_active = nil`,
   `reset_recording_state_keep_files()` (field-only reset that **never
   deletes files** — without it, stale `emitted_checkpoints` /
   `last_zone_act_state_key` from the first level segment would suppress
   `gameplay_start` / act-transition checkpoints on the return segment,
   since checkpoint dedup is keyed by name only). Then **fall through** into
   the `if not started` arm gate on the *same* frame — non-`$10` frames are
   manifest-only until the level gate re-arms.
4. **Level arm gate** (`if not started`, L1572-1717): unchanged plain logic
   (`game_mode == 0x0C` and `move_lock` word at `$FFB02E` == 0), plus the
   run-mode additions in §3/§5. In run mode, once one level segment has been
   armed (`level_segment_count > 0`), the `TARGET_GAMEPLAY_SEGMENT` skip
   check is bypassed (defensive only — `gameplay_segment_index` never
   increments on recorded-segment finalize, so the check could not actually
   swallow the return segment as written).

Block 2's gate can only be true after Block 1 set it, so both blocks are
transitively plain-mode-unreachable.

### SS entry sequence (exact order, L1513-1547)

On the first `$10` frame while a level segment is armed:

1. `physics_file:flush()`
2. `write_metadata()` — final **level** metadata into the still-current level
   segment dir
3. `append_level_segment_done(trace_frame)` — push the level entry onto
   `segments_done` (uses the pre-reset `trace_frame` as row count)
4. `close_files()`; `started = false`; `trace_frame = 0`
5. Push the `starpost_special` transition (§3) — computed **after** the
   append so `#segments_done` already counts the just-finished level
6. `start_ss_segment()` (§4); `detour_active = "special_stage"`; return.

### Run termination paths

Every live termination path in run mode funnels through `finalize_run_end()`
(L976-988) exactly once before `finished = true`:

- the 4b movie-done guard;
- the pre-arm `movie.mode() == "FINISHED"` site (fires if the movie ends
  between segments, e.g. during the post-SS reload before the return level
  re-arms);
- the non-level "Left level gameplay" stop (game_mode leaves `$0C` to
  something other than `$10` — a real stop; `$10` is intercepted by Block 1);
- the in-loop BK2-end (`bk2_frame_offset + trace_frame >= effective length`)
  and FINISHED checks (shadowed in run mode by the 4b guard, funneled anyway);
- the FRAME_CAP backstop in the main loop.

`finalize_run_end()` routes by state — this if/else is load-bearing because
`started` is true during **both** an armed level segment and an armed ss
segment:

```
if detour_active == "special_stage" -> finalize_ss_segment(); detour_active = nil
elseif started -> flush; write_metadata(); append_level_segment_done(trace_frame);
                  close_files(); started = false
write_run_manifest()   -- always, last
```

(Unconditionally running the level finalize mid-detour would overwrite
`ss/metadata.json` via the shared `effective_output_dir`, append a bogus
`kind="level"` entry, and leave `finalize_ss_segment()` a silent no-op.)
After `finished = true`, the main loop's run-mode exit branch only prints and
breaks — it must **not** re-run the plain finalize block (which would rewrite
metadata into the last segment dir).

In the fixture, the run ends via the 4b movie-done guard while seg3 is armed
and still in `$0C`. The guard fired at emu frame **22612**: seg3's last row
(frame 3451) was written at emu frame 22611 (`bk2_frame_offset 19159 + 1 +
3451`), so `bk2_frame_offset + trace_frame_count = 22611` is one **less**
than the capture-time effective movie length of 22612 (the guard's
`frame_now >= length` first became true at 22612, before that call could
write a row — an effective length of 22611 would have fired one call earlier
and left only 3451 rows).

**Capture-time caveat (load-bearing for byte-identical reproduction):** the
committed `s2-ehz-halfpipe-roundtrip.bk2` contains **22819** input rows
(idle from frame 22042 onward). The capture-time effective length of 22612
is therefore *not* derivable from the committed movie file: at capture the
env override was evidently not visible to EmuHawk Lua and `movie.length()` /
`movie.mode()` signaled done at 22612 — 207 frames short of the file-derived
count (the documented chromeless under-report). A port that terminates seg3
from a BK2-derived effective length of 22819 would write 3659 rows, not the
fixture's 3452. Seg3's tail length encodes the capture session's
movie-length signal, not a property of the committed BK2.

---

## 3. Transitions: `starpost_special` and `stage_exit`

Two entry kinds are ever emitted by this recorder (the Java consumer also
accepts `giant_ring`/`starpost_bonus`, which S2 run mode does not produce —
the S2 halfpipe round trip enters via star posts, so the entry kind is
`starpost_special`).

### `starpost_special` (level → ss), pushed at SS entry (L1525-1536)

All RAM fields are read **on the first frame `game_mode` reads `$10`** (the
entry frame, after that frame has completed), immediately after the level
segment finalize and before `start_ss_segment()`:

| JSON key | Lua source | RAM address / width | When read |
|---|---|---|---|
| `from_segment` | `#segments_done - 1` | — | after level append → 0-based index of the finished level |
| `to_segment` | `#segments_done` | — | 0-based index the ss segment will occupy |
| `entry_kind` | `"starpost_special"` | — | — |
| `mode_change_bk2_frame` | `emu.framecount()` | — | entry frame; **equals the ss segment's `bk2_frame_offset`** |
| `special_bonus_entry_flag` | `ADDR_BIGRING_FLAG` | `$FFF7CD` u8 (f_bigring / special-entry flag) | entry frame |
| `saved_x_pos` | `ADDR_SAVED_X_POS` | `$FFFE32` u16be (star-post saved X) | entry frame |
| `saved_y_pos` | `ADDR_SAVED_Y_POS` | `$FFFE34` u16be (star-post saved Y) | entry frame |
| `last_star_post_hit` | `ADDR_LAST_STAR_POST_HIT` | `$FFFE30` u8 | entry frame |
| `rings_before` | `ADDR_RING_COUNT` | `$FFFE20` u16be | entry frame |
| `emeralds_before` | `ADDR_EMERALDS` | `$FFFFB1` u8 | entry frame |

### `stage_exit` (ss → level), pushed at the **return level arm** (L1669-1689)

Emitted inside the run-mode level-arm branch, only when
`#segments_done > 0 and segments_done[#segments_done].kind ==
"special_stage"` (i.e. the previous finished segment was the ss). At that
point `segments_done == [..., level, ss]`, so the indices are exact without
adjustment. Fields are read **on the level arm-detection frame** (`game_mode
== $0C`, `move_lock == 0`):

| JSON key | Lua source | RAM address / width | When read |
|---|---|---|---|
| `from_segment` | `#segments_done - 1` | — | ss segment's 0-based index |
| `to_segment` | `#segments_done` | — | return level's index (pushed to `segments_done` only later, at finalize) |
| `entry_kind` | `"stage_exit"` | — | — |
| `mode_change_bk2_frame` | `emu.framecount()` | — | arm frame; **equals the return level segment's `bk2_frame_offset`** |
| `rings_after` | `ADDR_RING_COUNT` | `$FFFE20` u16be | arm frame — ROM zeroes ring tracking on the post-SS level reload, so this records **0**; the recorder records the truth, no compensation |
| `emeralds_after` | `ADDR_EMERALDS` | `$FFFFB1` u8 | arm frame |

`stage_exit` records carry **no** `special_bonus_entry_flag` /
`saved_*` / `last_star_post_hit` / `*_before` fields; `starpost_special`
records carry no `*_after` fields (the Lua only sets the fields listed above;
the manifest writer emits a field iff it is set — see §7 truthiness note).

---

## 4. The special-stage segment writer (`trace_profile "s2_special_stage"`)

Ported from `s2_ss_trace_recorder.lua` but **without** any
`event.onmemoryexecute` hooks (hard rule for the run port): state is sampled
directly from RAM by `read_ss_state()` **once per `$10` frame** — i.e. once
per `on_frame_end()` call while the detour is active, which is once per
emulated frame including lag frames (each row records `emu.islagged()` in the
`lag` column).

### Arming (`start_ss_segment()`, L868-889)

- Computes the dir token (§1), creates the dir, sets `started = true`,
  `bk2_frame_offset = emu.framecount()` (the entry frame), `trace_frame = 0`.
- Samples `current_ss_index = mainmemory.read_u8($FFFE16)`
  (`SS_ADDR_SPECIAL_STAGE_INDEX`, the v_lastspecial-equivalent index) **at
  arm time**.
- Opens `physics.csv` + `aux_state.jsonl`, writes the 48-column header,
  flushes, writes initial `metadata.json`.

**Frame-0 alignment (run-port convention, NOT the interior
s2_ss_trace_recorder.lua convention):** the entry branch returns without
writing a row, so ss row 0 is recorded on the **next** `$10` frame with
`bk2_frame_offset` sampled at entry — the same alignment the S1/S3K run ports
use. (The interior recorder writes frame 0 in its own arming invocation — a
deliberate one-frame difference to remember for any comparator work against
interior-recorder ss traces.)

### physics.csv schema (ss_csv_version 1)

Header (one line, 48 columns):

```
frame,input,input_p2,lag,speed_factor,track_anim,track_anim_frame,track_drawing_index,track_orientation,track_duration_timer,current_segment,player_anim_frame_timer,rings_togo_bcd,check_rings_flag,tails_control_counter,swap_positions_flag,sonic_present,sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,sonic_ss_y_sub,sonic_ss_z,sonic_angle,sonic_routine,sonic_routine_secondary,sonic_status,sonic_anim,sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,tails_ss_x_sub,tails_ss_y,tails_ss_y_sub,tails_ss_z,tails_angle,tails_routine,tails_routine_secondary,tails_status,tails_anim,tails_anim_frame,tails_rings_bcd,tails_hurt_timer,tails_slide_timer,tails_flip_timer
```

Row format string (L911): `"%d,%x,%x,%d,"` then 44 × `%x` — i.e. `frame`
decimal, `lag` decimal 0/1, and **everything else lowercase unpadded hex**
(the SS convention; do NOT reuse the level writer's zero-padded `%04X`
helpers). `sonic_present`/`tails_present` are `(present and 1 or 0)` through
`%x` (renders 1/0).

Per-row sources:

- `frame` = `trace_frame` (0-based, incremented after the write).
- `input` / `input_p2` = `joypad_mask_from_frame(bk2_frame_offset +
  trace_frame, 1|2)` (L739-759): BK2 movie input via `movie.getinput`, keys
  `"P<n> Up/Down/Left/Right"` (with unprefixed fallbacks), A|B|C collapsed to
  `INPUT_JUMP` (0x10), plus `Start` → `0x80` (`INPUT_START` — note the ss
  writer, unlike the level writer's `bk2_input_mask`, includes Start).
  Returns 0 when no movie is loaded or the frame has no input.
- `lag` = `emu.islagged() and 1 or 0`.
- Shared track state from `read_ss_state()` (L803-820):
  `SS_Cur_Speed_Factor $FFDB16` u16be, `SSTrack_anim $FFDB08` u8,
  `SSTrack_anim_frame $FFDB0B` u8, `SSTrack_drawing_index $FFDB0D` u8,
  `SSTrack_Orientation $FFDB0E` u8, `SSTrack_duration_timer $FFDB1F` u8,
  `SpecialStage_CurrentSegment $FFDB0A` u8,
  `SS_player_anim_frame_timer $FFDB21` u8, `SS_RingsToGoBCD $FFDBA4` u16be,
  `SS_Check_Rings_flag $FFDB86` u8, `Tails_control_counter $FFF702` u16be,
  `SS_Swap_Positions_Flag $FFF742` u8.
- Per-character blocks from `read_ss_character(base)` (L765-799), Sonic at
  `$FFB000`, Tails at `$FFB040`: `present` iff the slot id byte (`+$00`) ≠ 0
  (Sonic=0x09, Tails=0x10); when absent, **all fields are zero** but rows keep
  being written (the fixture's ss tail shows all-zero character blocks during
  the post-clear results while game_mode is still `$10`). Field offsets:
  `ss_x +$2A`, `ss_x_sub +$2C`, `ss_y +$2E`, `ss_y_sub +$30` (u16be each),
  `ss_z +$34` u16be, `angle +$26`, `routine +$24`,
  `routine_secondary +$25`, `status +$22`, `anim +$1C`, `anim_frame +$1B`,
  `rings_bcd = (byte+$3C << 16) | (byte+$3D << 8) | byte+$3E` (three BCD
  digit bytes packed into one value), `hurt_timer +$36`, `slide_timer +$37`,
  `flip_timer +$33`.
- Flush cadence: `physics_file:flush()` when `trace_frame % 60 == 0`;
  metadata rewritten (`write_ss_metadata()`) when `trace_frame % 300 == 0` —
  both checked **before** the increment, i.e. at rows 0, 60/300, ....

### Aux events

**None.** `aux_state.jsonl` is opened at arm and closed at finalize, but the
ss writer never writes to it — the finished file is byte-empty (0 bytes;
fixture confirms). None of the level recorder's aux machinery (checkpoints,
snapshots, scans, cpu_state) runs during the detour.

### ss metadata.json (`write_ss_metadata()`, L827-849)

Distinct shape from level metadata — written at arm, every 300 rows, and at
finalize. Exact key order and formatting (2-space indent, one key per line):

```json
{
  "game": "s2",
  "trace_profile": "s2_special_stage",
  "special_stage_index": <current_ss_index>,
  "ss_csv_version": 1,
  "characters": ["sonic", "tails"],
  "main_character": "sonic",
  "sidekicks": ["tails"],
  "bk2_frame_offset": <n>,
  "trace_frame_count": <rows>,
  "source_bk2": "<json_escape(OGGF_BK2_BASENAME)>",
  "lua_script_version": "9.12-s2",
  "recording_date": "YYYY-MM-DD",
  "run_id": "<run_id>",
  "fresh_load": false,
  "segment_index": <#segments_done>
}
```

Differences vs the level metadata: no `zone`/`zone_id`/`rom_zone_id`/`act`/
`gameplay_segment`/`start_x`/`start_y`/`rng_seed`/`trace_schema`/
`csv_version`/`aux_schema_extras`/`bizhawk_version`/`genesis_core`/`route`/
`rom_checksum`/`notes`; adds `special_stage_index`, `ss_csv_version`,
`fresh_load` (always `false`); `trace_profile` is unconditionally
`"s2_special_stage"`; `characters`/`sidekicks` are **hardcoded** to
sonic+tails (not derived from slot presence); `run_id` line is emitted only
when `run_id ~= nil` (always true here); `segment_index` is the **last** key
(no trailing comma) and equals `#segments_done` at write time — the finished
segments before this one (ss → 1, ss_2 → 3 in the fixture), stable across
rewrites because the ss entry is appended only after the final metadata write.

### Finalize (`finalize_ss_segment()`, L938-962)

Guarded by `if not started then return end` (idempotent). Flush → final
`write_ss_metadata()` → `close_files()` → append to `segments_done`:

```lua
{ dir = <token>, kind = "special_stage", profile = "s2_special_stage",
  special_stage_index = <current_ss_index>, zone_id = 0, act = 0,
  bk2_frame_offset = <offset>, rows = <trace_frame> }
```

(`special_stage_index` is required by `TraceRunManifest.Segment.validate` for
`kind == "special_stage"`; `zone_id`/`act` are hardcoded 0.) Then
`started = false`, `trace_frame = 0`, `current_ss_index = nil`.

---

## 5. Per-segment `bk2_frame_offset` / `trace_frame_count`

- **Level segment:** `bk2_frame_offset = emu.framecount()` sampled on the
  arm-detection frame (`game_mode == $0C` and `move_lock == 0` first
  observed while unarmed). That detection frame is **not** recorded; row 0
  is written on the next `on_frame_end()` after one more `emu.frameadvance()`
  (post-movement state), and the BK2 input for row N is at absolute index
  `bk2_frame_offset + N`. `trace_frame_count` = rows actually written (the
  final `trace_frame`), captured into `segments_done[i].rows` at finalize.
- **SS segment:** identically shaped — `bk2_frame_offset = emu.framecount()`
  at the `$10` entry frame (which is skipped; row 0 lands one frame later),
  `trace_frame_count` = ss rows written; the row count includes every `$10`
  frame after entry up to (not including) the first non-`$10` frame,
  including lag frames and the results tally.
- Consequences visible in the fixture: each `starpost_special`
  `mode_change_bk2_frame` equals the following ss segment's
  `bk2_frame_offset` (3795, 12605) and each `stage_exit`
  `mode_change_bk2_frame` equals the following level segment's
  `bk2_frame_offset` (9701, 19159). `TraceRunManifest.validate` requires
  strictly increasing `bk2_frame_offset` across segments.
- Metadata `bk2_frame_offset`/`trace_frame_count` in each segment dir carry
  the same values (level metadata is rewritten with the final `trace_frame`
  during finalize; ss metadata likewise).

---

## 6. run_manifest.json — exact byte layout and write timing

Written by `write_run_manifest()` (L998-1061) to
`OUTPUT_DIR .. "run_manifest.json"` — run mode only (`run_id == nil` returns
immediately), and only from `finalize_run_end()`, i.e. **exactly once, at run
termination** (it is not rewritten periodically; a killed process loses the
manifest but keeps finalized segment dirs). Before writing, a non-fatal
invariant check prints a WARNING for any transition where
`to_segment ~= from_segment + 1` or `to_segment > #segments_done`.

Exact emission (2-space indent; `%q` is Lua's quoted-string format —
double-quoted with backslash escapes — used for `run_id`, `dir`, `kind`,
`trace_profile`, `entry_kind`, while `source_bk2` goes through the shared
`json_escape` helper instead):

```
{
  "run_schema": 1,
  "game": "s2",
  "run_id": <%q run_id>,
  "source_bk2": "<json_escape(OGGF_BK2_BASENAME)>",
  "rom_checksum": "7B905383",
  "lua_script_version": "9.12-s2",
  "segments": [
    {"dir": <%q>, "kind": <%q>, "trace_profile": <%q>, "bk2_frame_offset": <%d>, "trace_frame_count": <%d>, "zone_id": <%d>, "act": <%d><extra>}<,>
    ...
  ],
  "transitions": [
    {<fields joined by ", ">}<,>
    ...
  ]
}
```

- `rom_checksum` is the **inline literal** `"7B905383"` — the CRC32 of Sonic 2
  World REV01 (the only ROM this recorder targets), not computed at runtime.
  `lua_script_version` is likewise emitted from the version constant
  (`"9.12-s2"`).
- **Segments array**: one object per line, 4-space indented, key order exactly
  `dir, kind, trace_profile, bk2_frame_offset, trace_frame_count, zone_id,
  act` with `<extra> = ', "special_stage_index": <n>'` appended **only** for
  `kind == "special_stage"`. The Lua-side per-segment row-count field is
  `rows`, emitted under the JSON key `trace_frame_count`. Level entries:
  `kind "level"`, `trace_profile` = `TRACE_PROFILE` (`"gameplay_unlock"` for
  runs), `zone_id` = engine zone id, `act` = 1-based apparent act. SS
  entries: `kind "special_stage"`, `trace_profile "s2_special_stage"`,
  `zone_id 0`, `act 0`, `special_stage_index` from `$FFFE16` at ss arm.
  A trailing comma follows every entry except the last; each entry ends with
  a newline.
- **Transitions array**: same one-object-per-line layout. Mandatory fields in
  order: `from_segment`, `to_segment`, `entry_kind` (`%q`),
  `mode_change_bk2_frame`. Optional fields appended **in this fixed order,
  each present iff the Lua table field is set**:
  `special_bonus_entry_flag`, `saved_x_pos`, `saved_y_pos`,
  `last_star_post_hit`, `rings_before`, `rings_after`, `emeralds_before`,
  `emeralds_after`. Note the interleaving: a `starpost_special` record renders
  `..., last_star_post_hit, rings_before, emeralds_before` (no `*_after`),
  a `stage_exit` record renders `..., mode_change_bk2_frame, rings_after,
  emeralds_after` (nothing else). **Lua truthiness caveat for porters:** the
  emission tests are `if t.<field> then` — in Lua, `0` is truthy, so a field
  whose sampled RAM value is 0 (e.g. `emeralds_before = 0`,
  `rings_after = 0` in the fixture) **is still emitted**. A port must key on
  "was the field recorded for this transition kind", never on the value.
- Closing: `  ]\n}\n` (the transitions `]` has no trailing comma; file ends
  with a newline after `}`).
- The file must satisfy `TraceRunManifest.validate`: `run_schema == 1`, ≥1
  segment, known kinds, strictly increasing `bk2_frame_offset`, unique
  segment dirs each containing `metadata.json`, `special_stage_index` present
  on every special_stage segment, known `entry_kind`s, per-transition
  `to_segment == from_segment + 1` and `to_segment < segments.size()`.

---

## 7. Level metadata additions in run mode

Level segments use the plain `write_metadata()` with one run-mode-only block
(L577-580) inserted between `source_bk2` and `rom_checksum`:

```
  "run_id": "<run_id>",
  "segment_index": <#segments_done>,
```

`segment_index` equals `#segments_done` **at write time**. Because the finalize
order is always flush → `write_metadata()` → `append_level_segment_done()`,
the value is stable for the whole segment lifetime (periodic 300-frame
rewrites included) and equals the number of segments finished before this one:
fixture `seg1 → 0`, `seg2 → 2`, `seg3 → 4` (ss metadata: `ss → 1`,
`ss_2 → 3`). `gameplay_segment` stays `0` for every run segment
(`gameplay_segment_index` increments only in the plain skip branch, which run
mode does not take). All other level-metadata keys and values (including
`rom_checksum: ""` and `trace_profile: "gameplay_unlock"`) are byte-identical
to plain mode.

---

## 8. Relationship of run-mode level segments to the plain recorder

Run-mode level segments **are** the plain `gameplay_unlock` recorder — the
same arm gate (`$0C` + `move_lock == 0`, detection frame skipped), the same
CSV v7 writer (42-column symmetric Sonic+Tails blocks, `%04X`-padded
uppercase hex, BK2-derived `input` column via `bk2_input_mask`), the same aux
event pipeline (pre-trace `player_history_snapshot` / `cpu_state_snapshot` /
`object_state_snapshot`s, `zone_act_state`/`checkpoint`, `mode_change` /
`routine_change` / `state_snapshot`, per-frame `cpu_state`, object scans /
`slot_dump` / `cursor_state`, CNZ slot-machine state), the same flush (60) and
metadata-rewrite (300) cadences, and the same `write_metadata()` (trace_schema
9, csv_version 7). The complete list of run-mode deltas for a level segment:

1. files open under `OUTPUT_DIR/segN_<zone><act>/` instead of flat
   `OUTPUT_DIR` (`effective_output_dir` redirection);
2. metadata gains the `run_id` + `segment_index` lines (§7);
3. arming may push a `stage_exit` transition (§3) when the previous finished
   segment was a special stage;
4. finalization routes through the detour/`finalize_run_end` funnels (§2)
   and appends a `segments_done` entry;
5. the `TARGET_GAMEPLAY_SEGMENT` skip check is bypassed after the first
   level arm (defensive);
6. after an SS detour, level-tracking state is reset with
   `reset_recording_state_keep_files()` (no `os.remove`).

`physics.csv` and `aux_state.jsonl` bytes for a level segment are produced by
exactly the code the plain mode uses; the fixture's level segments are
therefore directly comparable to plain-mode captures modulo the metadata
deltas above **and the fixture line endings (§9)**.

---

## 9. File encodings (run fixture)

The run-mode spec is byte-level, so this differs from the plain-mode S2
fixtures and must not be inherited from the plain spec's §9 ("LF-only"):

- Every non-empty file in the canonical run fixture — `run_manifest.json`,
  each segment `metadata.json`, each `physics.csv` (level and ss), and each
  level `aux_state.jsonl` — uses **CRLF** (`\r\n`) line endings, including
  the final line (last bytes `7d 0d 0a` for `run_manifest.json`). The Lua
  writes `"\n"` through text-mode `io.open`, and the capture ran on Windows
  EmuHawk, which expanded every `\n` shown in this spec's templates to
  `\r\n`. (The committed plain-mode S2 fixtures are LF-only; the run fixture
  was not normalized.)
- UTF-8 without BOM (pure ASCII in practice; first bytes of
  `run_manifest.json` are `7b 0d 0a`).
- The ss segments' `aux_state.jsonl` files are exactly **0 bytes** (§4).

---

## 10. Porting invariants checklist (native harness)

1. Run mode iff `OGGF_TRACE_RUN_ID` is present; without it, output must stay
   byte-identical to plain mode.
2. `ss` dir token is bare for the first detour, `ss_2`+ afterwards; level
   tokens number by **level arms only**.
3. SS row 0 is one frame after the entry frame; entry frame supplies
   `bk2_frame_offset` and all `starpost_special` RAM fields.
4. `stage_exit` fields are read at the return-level arm frame;
   `rings_after` is genuinely 0 (ROM reload behavior) — record the truth.
5. SS csv: `frame`/`lag` decimal, all else lowercase unpadded hex; ss aux
   file exists and is empty; ss rows continue through the `$10` results
   tally with zeroed character blocks.
6. Manifest optional transition fields: presence by kind, not by value
   (Lua `0` is truthy).
7. `finalize_run_end` must route ss-vs-level by `detour_active` first, and
   the manifest is written exactly once at run end.
8. `movie.length()` may under-report; the effective length is
   `max(movie.length(), derived BK2 frame count)` — the override only raises.
   Fixture caveat: the canonical run's seg3 was terminated by a capture-time
   effective length of 22612, which is 207 frames short of the committed
   BK2's 22819 input rows (§2) — the fixture's seg3 row count is not
   reproducible from a file-derived length alone.
9. Post-SS reset must clear checkpoint/known-object dedup state without
   deleting the finalized ss files.
10. Segment/metadata `segment_index` = count of previously finished segments
    (level metadata written before its own append).
11. Fixture comparison: every non-empty run-fixture file is CRLF-terminated
    (§9) — do not assume the plain-mode fixtures' LF-only convention.

---

## 11. v9.13-s2 design: complete-run extension (title-card reloads + SS aux)

Status: IMPLEMENTED — in `s2_trace_recorder.lua` v9.13-s2 and mirrored by the
native harness's S2 run runner; §11.5 records the gate-derived addenda and
corrections discovered during implementation (the shipped Lua is the
authority where they disagree with the design text). §§1-10 above remain the
v9.12 byte authority except where §11.4/§11.5 note deltas; this section
specifies the only behavioral changes.
Motivating capture (this session, `sonic-2-sonic-tails-complete-emeralds.bk2`,
259,590 rows): the run stopped at emu frame ~32,760 after 7 segments because a
death restart reloads the level with the title-card bit set — `Game_Mode`
(`$FFF600`) reads `$8C`, not `$0C` — and the armed non-level branch (§2 item
"Left level gameplay") finalized the whole run. The BK2's input gap at rows
32777-32922 (~146 idle frames = `restart_countdown` + title-card reload)
corroborates the death at that frame.

### 11.1 Disasm-verified `Game_Mode` sequences (docs/s2disasm/s2.asm)

Mode constants (`s2.constants.asm:465-477`): SegaScreen `$00`, TitleScreen
`$04`, Demo `$08`, Level `$0C`, SpecialStage `$10`, ContinueScreen `$14`,
2PResults `$18`, 2PLevelSelect `$1C`, EndingSequence `$20`, OptionsMenu `$24`,
LevelSelect `$28`; `GameModeFlag_TitleCard` = bit 7 (`GameModeID_TitleCard`
mask `$80`).

**The reload family.** Every in-`$0C` reload funnels through
`Level_Inactive_flag`: `Level_MainLoop` tests it and branches back to `Level`
(`tst.w (Level_Inactive_flag).w / bne.w Level`, s2.asm:5096-5097); `Level:`
(loc_3EC4) immediately does `bset #GameModeFlag_TitleCard,(Game_Mode).w`
(s2.asm:4758, "add $80 to screen mode") → **`$8C`** for the whole
title-card/reload sequence; `Level_StartGame` (loc_435A) does the `bclr`
(s2.asm:5082) → back to `$0C`. The base mode value never changes across a
reload; only bit 7 toggles. Members of the family:

| Trigger | Disasm site | `Current_ZoneAndAct` (`$FFFE10` word) |
|---|---|---|
| Death / star-post restart | `Obj01_Gone` (loc_1B31C, s2.asm:~38346-38352): `restart_countdown` expiry → `move.w #1,(Level_Inactive_flag).w` (Tails: `Obj02_Gone`, loc_1CD90) | unchanged |
| Time over (lives remain) | `Obj39_TimeOver` (loc_14034, s2.asm:27748-27751): `clr.l (Saved_Timer).w`, `Level_Inactive_flag = 1` | unchanged |
| Act 1 → act 2 | Results `Obj3A` loc_14270→loc_1429C (s2.asm:27979-28005): `LevelOrder` (word_142F8) lookup → `move.w d0,(Current_ZoneAndAct).w`, `clr.b (Last_star_pole_hit).w`, `Level_Inactive_flag = 1`. **Act transitions are NOT seamless at the `Game_Mode` level** — same `$0C → $8C → $0C` shape | next act |
| Zone → next zone | Same `Obj3A` code path (`LevelOrder` maps act 2 → next zone act 1); negative entry → SegaScreen (s2.asm:27994-27995) | next zone act 1 |
| SCZ → WFZ | ObjB2 route: `move.w #wing_fortress_zone_act_1,(Current_ZoneAndAct).w` (s2.asm:78875) then `Level_Inactive` | WFZ1 |
| WFZ → DEZ | `ObjB2_Start_DEZ` (loc_3AC40, s2.asm:79196-79201): `Current_ZoneAndAct = DEZ1`, `ObjB2_Deactivate_level` sets `Level_Inactive`, clears star posts | DEZ1 |
| Continue accepted | `ContinueScreen` (s2.asm:10319) exit: `move.b #GameModeID_Level` (s2.asm:10410), `Life_count = 3`, rings/timer cleared → then `Level:` reload | act 1 of current zone |
| Post-SS return | GameMode_SpecialStage results epilogue (s2.asm:~6804-6813): `Level_Inactive` then `move.b #GameModeID_Level` → `$0C`, then GameMode_Level re-entry → `$8C` → `$0C` (recorder is unarmed here; §2 already handles it) | unchanged |

**Non-reload terminal modes (direct writes from `$0C`, no `$8C`):**

- Game over: `Obj39_Dismiss` (loc_14014, s2.asm:27735-27746) writes
  ContinueScreen `$14`, then overwrites with SegaScreen `$00` when
  `Continue_count` is 0. Continue-screen timeout → SegaScreen `$00`
  (s2.asm:10406).
- SS entry: `Obj79_Star` (loc_1F536, s2.asm:44873-44878): `f_bigring = 1`,
  `Game_Mode = $10` — direct `$0C → $10` (already Block 1).
- Ending: ObjC7 (DEZ final boss) defeat path writes EndingSequence `$20`
  (s2.asm:83098) — direct `$0C → $20`. Credits end → SegaScreen
  (s2.asm:13266-13267), long after the recorder finalized.

### 11.2 Segmentation semantics: surviving `$8C` while armed

New **Block 1.5** in `on_frame_end`, placed after Block 2's fall-through and
before the `if not started` arm gate, gated
`run_id ~= nil and started and game_mode == GAMEMODE_LEVEL_TITLECARD` (new
constant `0x8C`, exact-match — `$88` Demo|TitleCard is out of scope):

1. Finalize the armed level segment exactly like the SS-entry sequence (§2):
   flush → `write_metadata()` → `append_level_segment_done(trace_frame)` →
   `close_files()` → `started = false`, `trace_frame = 0`.
2. Capture a **pending reload transition** (global
   `pending_reload_transition`, NOT yet pushed to `transitions_done`), with
   all boundary fields read on this first-`$8C` frame (see field table).
3. `reset_recording_state_keep_files()` and fall through (mirrors Block 2):
   `$8C` frames are manifest-only; the next `$0C` + `move_lock == 0` frame
   re-arms via the unchanged arm gate, producing the next numbered
   `seg<N>_<zone><act>` level segment (`level_segment_count` keeps counting
   level arms across zones/deaths, so directory names stay unique:
   `seg1_ehz1 … seg5_ehz1` after an EHZ1 death).
4. At that re-arm, the arm branch completes the pending record with the
   `*_after` fields (read on the arm frame, same convention as `stage_exit`)
   and pushes it — `from_segment = #segments_done - 1`,
   `to_segment = #segments_done`, exact for the same reason `stage_exit`'s
   indices are (§3). The `stage_exit` push and the pending-reload push are
   mutually exclusive at one arm (previous finished segment is either the ss
   or a level).

**Kind decision** (at the `$8C` boundary frame): compare the
`Current_ZoneAndAct` word (`$FFFE10`) against the finished segment's
`(start_rom_zone_id << 8) | start_act`:

- differs → `entry_kind = "level_advance"` (act→act, zone→zone, ObjB2 routes,
  continue-accepted restarts to act 1 of the zone the player died in act 2 of);
- equal → `entry_kind = "death_restart"` (death, star-post respawn, time
  over — time over deliberately classifies as `death_restart`).

Robustness note: `Obj3A` writes the destination into `Current_ZoneAndAct`
one-or-more `$0C` frames *before* `Level_Inactive` lands (the existing
`act_transition_to_*` checkpoint fires on those tail frames). Classification
compares the boundary value against the segment-**start** values, so those
pre-boundary tail frames do not affect it.

**Transition record fields** (manifest optional-field emission order in
`write_run_manifest` is unchanged; presence keyed by kind, never value —
Lua `0` is truthy):

| Field | `death_restart` | `level_advance` | RAM / when read |
|---|---|---|---|
| `mode_change_bk2_frame` | yes | yes | `emu.framecount()` on the first `$8C` frame |
| `saved_x_pos` / `saved_y_pos` | yes | — | `$FFFE32` / `$FFFE34` u16be, boundary frame (values the reload will consume) |
| `last_star_post_hit` | yes | — | `$FFFE30` u8, boundary frame (`LevelOrder` path clears it, hence omitted for `level_advance`) |
| `rings_before` | yes | yes | `$FFFE20` u16be, boundary frame (pre-zeroing truth) |
| `emeralds_before` | yes | yes | `$FFFFB1` u8, boundary frame |
| `rings_after` | yes | yes | `$FFFE20` u16be, re-arm frame (0 after death; truth recorded) |
| `emeralds_after` | yes | yes | `$FFFFB1` u8, re-arm frame |

No `special_bonus_entry_flag` on either kind. Rendered order per §6's fixed
optional-field order: `saved_x_pos, saved_y_pos, last_star_post_hit,
rings_before, rings_after, emeralds_before, emeralds_after` (death_restart)
and `rings_before, rings_after, emeralds_before, emeralds_after`
(level_advance).

**Pending-transition lifecycle:** pushed only at the completing arm; if the
run terminates first (movie exhausted mid-reload via the 4b guard or the
pre-arm FINISHED site), the pending record is **discarded** — never emitted —
so `run_manifest.json` always satisfies `TraceRunManifest.validate`
(`to_segment < segments.size()`). `finalize_run_end` needs no change for
this: only `transitions_done` is written.

**Run termination:** unchanged funnels. With Block 1.5 intercepting `$8C`,
the armed non-level branch now fires only for genuinely terminal modes:
`$20` ending (the graceful complete-run end for this movie), `$14` continue
screen, `$00` game over/sega, `$18` 2P results, etc. No manifest record marks
the run end; the last segment entry + this spec carry that semantics.
(A future movie that continues past game over would need a
`continue_restart` kind for the `$14 → $0C → $8C` chain; out of scope here —
this movie never game-overs.)

**Engine extension required:** `TraceRunManifest.ENTRY_KINDS` is strict
(`validate` throws on unknown `entry_kind`), so the implementation commit
must add `"death_restart"` and `"level_advance"` to the set in
`src/main/java/com/openggf/trace/TraceRunManifest.java` — an engine change:
stage `CHANGELOG.md`, `Changelog: updated`. `SEGMENT_KINDS` needs no change
(all new segments are `"level"`).

**Segment naming across all S2 zones:** the existing
`seg%d_%s%d` + `ZONE_NAMES` + `apparent_act_for` machinery already covers the
full route — ehz `0x00`, cpz `0x0D`, arz `0x0F`, cnz `0x0C`, htz `0x07`,
mcz `0x0B`, ooz `0x0A`, mtz `0x04` (acts 1-2) / `0x05` (+2 apparent-act →
`mtz3`), scz `0x10` (`scz1`), wfz `0x06` (`wfz1`), dez `0x0E` (`dez1`) — and
`level_segment_count` guarantees unique directory tokens for repeated
zone/act visits. No naming change.

### 11.3 SS-aux merge: run-mode SS segments gain the standalone event stream

Run-mode ss segments' `aux_state.jsonl` (currently 0 bytes, §4) gains the
**hook-free** aux event surface of `s2_ss_trace_recorder.lua` v1.4-s2ss (the
byte authority for templates; canonical fixture
`src/test/resources/traces/s2/special_stage/` — 4,580 events: 2,991
`run_objects_end` + 1,589 hook-free). All events are `"type"`-keyed (never
`"event"`) and use the standalone's lowercase-hex formats verbatim.

Ported events (templates are the standalone's exact `string.format` strings):

1. **`state_snapshot` (frame -1)** — `write_pretrace_snapshot`
   (standalone L431-441):
   `'{"frame":-1,"type":"state_snapshot","ring_requirement":"0x%04x","current_level_layout":"0x%08x","initial_speed_factor":"0x%04x","perfect_rings_left":"0x%04x"}'`
   — RAM `$DB8C` u16be, `$DB8E` u32be, `$DB16` u16be, `$DB9A` u16be.
   Emitted once per ss segment in `start_ss_segment()`, after
   `write_ss_metadata()` (standalone order: metadata → pretrace snapshot),
   i.e. sampled on the `$10` entry frame. (The fixture's all-zero values are
   correct: SS init has not populated these at entry.)
2. **`control_state`** — `check_control_state` (L496-505):
   `'{"frame":%d,"type":"control_state","started":%d}'` — `$DB23`
   `SpecialStage_Started` (`~= 0 and 1 or 0`); emitted on change **or** on
   the first row (`prev == nil` seed).
3. **`checkpoint`** — `check_checkpoint` (L459-477):
   `'{"frame":%d,"type":"checkpoint","check_rings_flag":"0x%02x"}'` on the
   0→nonzero edge of `SS_Check_Rings_flag`.
4. **`stage_finished`** — same function:
   `'{"frame":%d,"observed_frame":%d,"type":"stage_finished","check_rings_flag":"0x%02x"}'`
   — `frame` = `last_nonlag_trace_frame`, `observed_frame` = current
   `trace_frame`. Ported **without** `publish_pending_finish_pass` and
   without its `error()` assertions (both are `run_objects_end`-machinery).
   `last_nonlag_trace_frame` is maintained hook-free: in `write_ss_row`,
   when `lag == 0`, set it to `trace_frame` immediately after computing
   `lag` and before the row write (matches `record_frame` L617-621).
5. **`message_state`** — `check_message_state` (L479-494):
   `'{"frame":%d,"type":"message_state","hide_rings_to_go":"0x%02x","trigger_rings_to_go":"0x%02x","no_rings_togo_lifetime":"0x%04x"}'`
   on any change of `$DBA6`/`$DBA7`(u8)/`$DBA2`(u16be).
6. **`results_started`** — `check_results_started` (L447-457):
   `'{"frame":%d,"type":"results_started","slot":%d}'` on first sighting of
   `ObjID_SSResults` (`$6F`) in the 128-slot SST scan; emitted at most once
   per ss segment.

**Not ported:** `run_objects_end` — it requires the standalone's two
`event.onmemoryexecute` hooks, and the run port's hard rule (§4) is no
execute hooks. The run-mode ss aux stream is therefore a documented
**subset** of the standalone's surface (all state-sampled events; no
per-pass records). At the finish frame the standalone's order is checkpoint
→ terminal `run_objects_end` → `stage_finished`; the port emits checkpoint →
`stage_finished` directly.

**Emission points:** in `write_ss_row`, after the physics row write and the
existing flush(60)/metadata(300) cadence checks, before the `trace_frame`
increment, in the standalone's order: `check_control_state()` →
`check_checkpoint(state.check_rings_flag)` → `check_message_state()` →
`check_results_started()`.

**Frame indexing:** aux `frame` is the run-mode ss `trace_frame` — the same
base as the segment's `physics.csv` rows, i.e. one emu frame later than the
interior recorder's convention because the run port skips the `$10` entry
frame (§4 frame-0 alignment). Frame `-1` = pre-row-0, sampled at the
entry/arm frame.

**Per-detour state:** `prev_check_rings_flag` / `prev_hide_rings_to_go` /
`prev_trigger_rings_to_go` / `prev_no_rings_togo_lifetime` are seeded from
RAM in `start_ss_segment()` (standalone seeds at arm, L676-679);
`prev_special_stage_started = nil`, `stage_finished_emitted = false`,
`results_started_emitted = false`, `last_nonlag_trace_frame = -1` — all
reset per detour so `ss_2`+ segments re-emit their own frame -1 snapshot and
first-row `control_state`.

**File lifecycle & metadata:** unchanged — aux opened at arm, closed at
finalize; the run-mode ss `metadata.json` keeps its §4 shape
(`run_id`/`fresh_load`/`segment_index`), NOT the standalone's
(`bizhawk_version`/`genesis_core`) shape. `s2_ss_trace_recorder.lua` and
`lib/oggf_trace_common.lua` are not modified.

### 11.4 Byte-compatibility claim for 9.13-s2

- `LUA_SCRIPT_VERSION` bumps to `"9.13-s2"`; the header version-history block
  documents that 9.13 output is byte-identical to 9.12 for all
  previously-capturable shapes.
- **Plain mode** (no `OGGF_TRACE_RUN_ID`): byte-identical to 9.12 modulo
  `recording_date` and the version string.
- **Run mode on movies confined to modes `{$0C, $10}`** (the canonical
  halfpipe round trip): every output file byte-identical to 9.12 (modulo
  `recording_date` + version strings) with exactly ONE exception — ss-family
  segments' `aux_state.jsonl` changes from 0 bytes to the §11.3 event stream
  (a pure superset). SS `physics.csv` and `metadata.json`, all level-segment
  files, and `run_manifest.json` structure stay byte-identical. Block 1.5
  and the new transition kinds cannot fire on such movies (`$8C` never
  occurs while armed: SS entry is a direct `$0C → $10` write and the post-SS
  `$8C` reload happens unarmed).
- The new code paths activate only on previously-**fatal** shapes (`$8C`
  observed while a level segment is armed), which v9.12 answered by
  truncating the run at the first reload.

### 11.5 Implementation addenda (gate-derived; Lua v9.13-s2 + native mirror)

Facts established while implementing and gating §§11.1-11.4. Where these
correct the design text above, the shipped Lua is the authority.

1. **Continue-accepted restarts are unreachable while armed.** §11.1's
   reload-family table lists "Continue accepted" and §11.2's kind decision
   mentions continue-accepted restarts, but Block 1.5 can never observe that
   path: the run already finalized at the terminal `$14` continue screen, so
   the continue path's `$8C` only ever occurs after `finished`. The
   `GAMEMODE_LEVEL_TITLECARD` constant's comment in `s2_trace_recorder.lua`
   states this; treat the §11.1 row as documentation of the ROM's mode
   sequence only, not of Block 1.5 coverage. (A future movie that continues
   past game over still needs the out-of-scope `continue_restart` kind noted
   under "Run termination".)
2. **One `stage_finished` guard IS ported.** §11.3 item 4 says the
   standalone's `error()` assertions are dropped as `run_objects_end`
   machinery, but the `last_nonlag_trace_frame < 0` guard is hook-free and
   validates the `stage_finished` frame source (a `-1` would silently emit a
   bogus record). Both the Lua (`ss_check_checkpoint`) and the native
   `S2SpecialStageAuxEventEngine` keep it, verbatim from
   `s2_ss_trace_recorder.lua`'s `check_checkpoint` error path. Only the
   `run_objects_end`-machinery assertions are dropped.
3. **The canonical halfpipe movie is NOT mode-confined at file length —
   capture-session movie length now matters.** §11.4's "movies confined to
   `{$0C, $10}`" byte-compat claim holds for the canonical halfpipe
   *capture session* (effective movie length 22612, §10 item 8), not the
   committed 22,819-row `.bk2`: the movie's tail reaches the EHZ1→EHZ2 act
   transition's `$8C` at the very frame the 22612 guard ends the run. v9.12
   truncated there identically under a file-derived length; a v9.13 capture
   fed the file-derived 22819 instead survives the reload and records a
   sixth segment (`seg4_ehz2`) plus a `level_advance` transition. The native
   harness therefore grew a run-mode-only `--effective-movie-length`
   argument to inject the session's movie-length signal into the movie-done
   guard; the halfpipe differential gate passes 22612. The
   complete-emeralds movie needs no injection (its file-derived length
   matches the session signal).
4. **Halfpipe fixture regeneration (9.13 stamps).** The committed
   `s2-ehz-halfpipe-roundtrip` fixture set was regenerated from a verified
   native 9.13-s2 capture at effective length 22612, after proving a Lua
   9.13 capture and a native capture of the same BK2 content-identical
   across all segments plus `run_manifest.json` modulo LF/CRLF and
   `recording_date`. The delta vs the 9.12 set is exactly §11.4's claim:
   `ss`/`ss_2` `aux_state.jsonl` go from 0 bytes to the §11.3 event stream,
   `lua_script_version` stamps become `9.13-s2`, everything else (including
   the `.bk2`) is unchanged. Consequently §9's "ss aux is exactly 0 bytes"
   and §10 item 5's "ss aux file exists and is empty" no longer describe
   9.13-era fixtures — ss aux files are non-empty and, like every other
   non-empty run-fixture file, CRLF-terminated. The native writers stamp
   `lua_script_version "9.13-s2"` in level/ss metadata and the manifest.
5. **Complete-run validation outcome.** The motivating movie
   (`sonic-2-sonic-tails-complete-emeralds.bk2`, 259,590 rows) captures
   end-to-end under 9.13: 35 segments (`seg1_ehz1` … `seg28_dez1` + 7 ss
   dirs) and 34 transitions (7 `starpost_special`, 7 `stage_exit`,
   19 `level_advance`, 1 `death_restart` — the SCZ death), emeralds 0→7,
   finalizing at the `$20` ending. Lua and native captures are
   content-identical modulo CRLF and `recording_date`; the native capture is
   installed as the canonical fixture set at
   `src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/`
   with a permanent differential gate (per-segment sha256 over all 35
   physics/aux pairs, normalized metadata/manifest comparison, and an
   exact-output-layout assertion applied to both run gates).
