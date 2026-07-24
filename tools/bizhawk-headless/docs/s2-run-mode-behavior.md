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
