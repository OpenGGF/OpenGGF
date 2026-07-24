# S2 Trace Recorder — Byte-Level Behavioral Specification (Level Gameplay)

Authoritative specification for porting the LEVEL-GAMEPLAY behavior of
`tools/bizhawk/s2_trace_recorder.lua` (v9.12-s2, using
`tools/bizhawk/lib/oggf_trace_common.lua`) to the C# headless harness
(`tools/bizhawk-headless/`). Covers the plain `gameplay_unlock` profile and
the `level_gated_reset_aware` segment-selection profile. **Run mode
(`OGGF_TRACE_RUN_ID`), the special-stage detour, per-segment subdirs, and
`run_manifest.json` are OUT OF SCOPE here** — a sibling document covers them.
In plain (non-run) mode every run-mode branch in the Lua is gated on
`run_id ~= nil` and is unreachable; `effective_output_dir == OUTPUT_DIR` for
the whole run, and the v9.12 header declares plain-mode output byte-identical
to v9.11-s2 except the version string.

The port must produce **byte-identical** `physics.csv` and `aux_state.jsonl`,
and `metadata.json` identical except (a) the `recording_date` value and
(b) `lua_script_version` `9.11-s2` → `9.12-s2` for fixtures stamped
`9.11-s2`. Canonical fixtures (`.gz` fixtures gunzip to these bytes):

| Fixture | Profile | Segment | Offset | Rows | physics.csv sha256 | aux_state.jsonl sha256 |
|---|---|---|---|---|---|---|
| `src/test/resources/traces/s2/ehz1_fullrun/` | `gameplay_unlock` | 0 | 899 | 5852 | `efeb90112d36f897317f688881140c042792a2b640cf8313470216db91f57a83` | `5522e70caa8134570eb5acdcfc3c188655d929b2e777101ae70785168e122dc2` |
| `src/test/resources/traces/s2/arz/` | `level_gated_reset_aware` | 0 | 2752 | 5073 | `72c0a49ca19e26248889aee82e68b3cd7a2f503965c1ae80eb1be16ea01578ec` | `390dc8862377ffb8c77c72d75938acbe1a06bf72cf94392b2ffdd2dd6929d772` |
| `src/test/resources/traces/s2/arz2/` | `level_gated_reset_aware` | 1 | 7998 | 7809 | `83056cfcb9b059165fdd8710d7d510c9db249700a57d287610ce02d52ac35451` | `bae3b1654a7356dbbc6729e56767c0e0718e842163ecc236f1c60c5121b9c1e8` |

`s2-ehz1.bk2` has 6778 input rows; `s2-lvl-select-ARZ.bk2` (shared by
`arz`/`arz2`) has 15853.

This spec assumes the S1 spec
([s1-trace-recorder-behavior.md](s1-trace-recorder-behavior.md)) is read
first. The **frame-alignment / start-detection / movie-end model (S1 §2) and
the file-encoding rules (S1 §8) carry over unchanged** and are not restated;
sections below give S2 deltas plus everything S2-specific, exhaustively.
Templates marked VERBATIM must be reproduced exactly — hex widths, key
order, quoting, and no whitespace after JSON separators.

---

## 1. RAM address map

All reads are from the `mainmemory` domain (68K work RAM, `$FF0000` base
stripped). Multi-byte values are big-endian, assembled from consecutive
`IGpgxHost.ReadMainRamByte` reads. Widths as in S1 §1 (`u8`/`s8`/`u16be`/
`s16be`/`u32be`).

### 1.1 Global variables

| Address | Width | Name (s2disasm) | Used for |
|---------|-------|-----------------|----------|
| `0xF600` | u8 | `Game_Mode` | Start/skip/end detection (`0x0C` = level; `0x10` = special stage, run-mode only) |
| `0xF604` | u8 | `Ctrl_1_Held` (raw) | CSV-input fallback arg ONLY (never used with a movie loaded, §5); `state_snapshot.raw_input` |
| `0xF602` | u8 / u16be | `Ctrl_1_Held_Logical` | u8 read → `state_snapshot.logical_input`; u16be read → `cpu_state.ctrl1_logical`. **NOT dead code in S2** (unlike S1 where `0xF602` is never read) |
| `0xF606` | u8 | `Ctrl_2_Held` (raw) | `cpu_state.ctrl2_raw_held` |
| `0xF66A` | u8 | `Ctrl_2_Held_Logical` (held byte) | `cpu_state.ctrl2_held` |
| `0xF66B` | u8 | `Ctrl_2_Held_Logical+1` (pressed byte) | `cpu_state.ctrl2_pressed` |
| `0xFE20` | u16be | `Ring_count` | CSV `rings` |
| `0xEE00` | u16be | `Camera_X_pos` (pixel word of the 32-bit value) | CSV `camera_x` |
| `0xEE04` | u16be | `Camera_Y_pos` (pixel word) | CSV `camera_y` |
| `0xFE10` | u8 | `Current_Zone` | Start capture; skip-segment zone naming |
| `0xFE11` | u8 | `Current_Act` | Start capture; per-frame act-transition checkpoint |
| `0xF636` | u32be | `RNG_seed` | metadata `rng_seed` (captured at arm) |
| `0xFE04` | u16be | `Level_frame_counter` (`v_framecount`) | CSV `gameplay_frame_counter`; aux `vfc` |
| `0xFE0E` | u16be | VBlank word (`ADDR_VBLA_WORD`; `0xFE0C` is `Vint_runcount` longword — read +2 for the changing low word) | CSV `vblank_counter` |
| — | — | lag counter | **CSV `lag_counter` is constant `0`** (`%04X` → `0000`). S2 exposes no dedicated lag counter; the column is a schema-v3 diagnostic placeholder. Do NOT wire `emu.islagged()` here (that is the run-mode SS writer's field, out of scope) |

### 1.2 Sonic history buffers (Tails CPU input)

| Address | Width | Name | Used for |
|---------|-------|------|----------|
| `0xE400` | bytes | `Sonic_Stat_Record_Buf` (64 × 4-byte entries: input u16be, status u8, pad) | `player_history_snapshot.input_history/status_history`; `cpu_state.delayed_input/delayed_status` |
| `0xE500` | bytes | `Sonic_Pos_Record_Buf` (64 × 4-byte entries: x u16be, y u16be) | `player_history_snapshot.x_history/y_history`; `cpu_state.delayed_x/delayed_y` |
| `0xEED2` | u16be | `Sonic_Pos_Record_Index` (low byte used: `& 0xFF`) | `player_history_snapshot.history_pos`; `cpu_state.pos_table_index` |

### 1.3 Tails CPU state

| Address | Width | Name | Used for |
|---------|-------|------|----------|
| `0xF702` | u16be | `Tails_control_counter` | `cpu_state_snapshot.control_counter`; `cpu_state.idle_timer` |
| `0xF704` | u16be | `Tails_respawn_counter` | `cpu_state_snapshot.respawn_counter`; `cpu_state.flight_timer` |
| `0xF708` | u16be | `Tails_CPU_routine` | `cpu_routine` in both events |
| `0xF70A` | u16be | Tails CPU target X | `target_x` |
| `0xF70C` | u16be | Tails CPU target Y | `target_y` |
| `0xF70E` | u8 | Tails interact id | `cpu_state_snapshot.interact_id`; `cpu_state.interact` |
| `0xF70F` | u8 | Tails CPU jumping flag | `cpu_state_snapshot.jumping`; `cpu_state.auto_jump_flag` |

### 1.4 ObjPosLoad (OPL) cursor state

| Address | Width | Name | Used for |
|---------|-------|------|----------|
| `0xF76C` | u8 | `v_opl_routine` | Declared (`ADDR_OPL_ROUTINE`) but **never read** — do not port |
| `0xF76E` | u16be | `v_opl_screen` | `cursor_state` trigger + `opl_screen` |
| `0xF770` | u32be | `v_opl_data` forward cursor | `cursor_state.fwd_ptr` |
| `0xF774` | u32be | `v_opl_data+4` backward cursor | `cursor_state.bwd_ptr` |
| `0xFC00` | u8 | `v_objstate[0]` forward counter | `cursor_state.fwd_ctr` |
| `0xFC01` | u8 | `v_objstate[1]` backward counter | `cursor_state.bwd_ctr` |

### 1.5 CNZ slot machine state

Emitted every frame **only when `start_rom_zone_id == 0x0C`** (CNZ), §7.10.

| Address | Width | JSON field |
|---------|-------|------------|
| `0xFF4C` | u16be | `in_use` |
| `0xFF4E` | u8 | `routine` |
| `0xFF4F` | u8 | `timer` |
| `0xFF51` | u8 | `index` |
| `0xFF52` | u16be | `reward` |
| `0xFF54` | u16be | `slot1_pos` |
| `0xFF56` | u8 | `slot1_speed` |
| `0xFF57` | u8 | `slot1_routine` |
| `0xFF58` | u16be | `slot2_pos` |
| `0xFF5A` | u8 | `slot2_speed` |
| `0xFF5B` | u8 | `slot2_routine` |
| `0xFF5C` | u16be | `slot3_pos` |
| `0xFF5E` | u8 | `slot3_speed` |
| `0xFF5F` | u8 | `slot3_routine` |

(Note the gap: there is no read at `0xFF50` or `0xFF53`.)

### 1.6 Character object blocks (SST slots 0 and 1)

Sonic = `PLAYER_BASE = 0xB000` (slot 0). Tails/sidekick =
`SIDEKICK_BASE = 0xB040` (slot 1). **Both characters use the same offsets**
(symmetric blocks; the S1 port's player-only reads generalize to a
per-character reader, §4.2).

| Offset | Width | Name | Used for |
|--------|-------|------|----------|
| `+0x00` | u8 | object id | Presence check (`0` = absent); slot-scan id |
| `+0x01` | u8 | `render_flags` | `object_state_snapshot` alias only |
| `+0x08` | u16be | `x_pos` (centre X) | CSV x; aux positions |
| `+0x0A` | u16be | X subpixel | CSV x_sub |
| `+0x0C` | u16be | `y_pos` (centre Y) | CSV y |
| `+0x0E` | u16be | Y subpixel | CSV y_sub; `s2_tornado_state.y_sub` |
| `+0x10` | s16be | X velocity | CSV x_speed (uhex) |
| `+0x12` | s16be | Y velocity | CSV y_speed (uhex); `s2_tornado_state.y_vel` uses an **unsigned** u16be read |
| `+0x14` | s16be | inertia (ground speed) | CSV g_speed (uhex); `cpu_state.tails_inertia` uses an **unsigned** u16be read |
| `+0x16` | s8 | Y radius | `state_snapshot.y_radius` |
| `+0x17` | s8 | X radius | `state_snapshot.x_radius` |
| `+0x1A` | u8 | displayed mapping frame | CSV mapping_frame; `object_state_snapshot.mapping_frame` |
| `+0x1B` | u8 | anim frame | `object_state_snapshot.anim_frame` alias only (never read for characters/CSV) |
| `+0x1C` | u8 | animation id | CSV animation_id; `state_snapshot.anim_id`; object aliases |
| `+0x1E` | u8 | anim frame timer | `object_state_snapshot.anim_frame_timer` alias only |
| `+0x22` | u8 | status flags | CSV status_byte; air/rolling bits; aux events |
| `+0x24` | u8 | routine | CSV routine; routine_change |
| `+0x25` | u8 | routine_secondary | object aliases; `s2_tornado_state.routine_secondary` |
| `+0x26` | u8 | terrain angle | CSV angle; ground_mode |
| `+0x28` | u8 | subtype | `object_state_snapshot.subtype` alias only |
| `+0x2E` | u16be | `move_lock` (control lock timer) | **Start detection (S2 offset differs from S1's `+0x3E`)**; `control_locked`/`move_lock` in aux. For objects, `+0x2E..0x31` bytes are also the `s2_tornado_state.objoff_2e..objoff_31` fields |
| `+0x38` | u8 | stick-convex | Declared (`OFF_STICK_CONVEX`) but **never read** — do not port |
| `+0x3D` | u8 | `standonobject` (SST index, 0 = none) | CSV stand_on_obj; routine_change context; `cpu_state.tails_interact` |
| `+0x46` | u8 | `top_solid_bit` (active top plane `$0C`/`$0E`) | `state_snapshot.top_solid_bit` |
| `+0x47` | u8 | `lrb_solid_bit` (active LRB plane `$0D`/`$0F`) | `state_snapshot.lrb_solid_bit` |

Status flag bits (same as S1): `0x01` facing-left, `0x02` in-air, `0x04`
rolling, `0x08` on-object, `0x10` roll-jump, `0x20` pushing, `0x40`
underwater.

S2 player routine values: `0x00` init, `0x02` control, `0x04` hurt, `0x06`
death.

### 1.7 Object table (SST)

- Base `0xB000` (**not** S1's `0xD000`), 128 slots × `0x40` bytes.
- Slot 0 = Sonic (never scanned), slot 1 = Tails (IS scanned — it fires
  `object_appeared` with `object_type":"0x02"` on frame 0).
- Slot scan covers slots **1..127** ascending.
- "Dynamic" slots are **16..127** (`OBJ_DYNAMIC_START = 16`, vs S1's 32) —
  used by both `slot_dump` and the frame −1 snapshot's slot range comment;
  the frame −1 `object_state_snapshot` scan itself covers 1..127.

---

## 2. Environment inputs and native derivation

| Lua env var | Lua default | Meaning | Native derivation |
|---|---|---|---|
| `OGGF_S2_TRACE_PROFILE` | `"gameplay_unlock"` | `TRACE_PROFILE`; enables reset-aware/skip EHZ semantics when `"level_gated_reset_aware"` | CLI/env passthrough (a real input) |
| `OGGF_TRACE_GAMEPLAY_SEGMENT` | `0` (via `tonumber(... or "0") or 0`) | `TARGET_GAMEPLAY_SEGMENT`: which controllable segment to record | CLI/env passthrough (a real input) |
| `OGGF_BK2_FRAME_COUNT` | `nil` | Overrides `movie.length()` **upward only** in the BK2-end guard and frame cap (EmuHawk under-reports in chromeless runs) | **Derive from the movie file**: the BK2 input-log row count. The output must be as if the env var was passed correctly |
| `OGGF_BK2_BASENAME` | `""` | `SOURCE_BK2`, written verbatim (json-escaped) into `metadata.source_bk2` | **Derive from the movie file**: the BK2 file's basename (e.g. `s2-ehz1.bk2`) |
| `OGGF_TRACE_RUN_ID` | `nil` | Run mode toggle | Out of scope; must be ABSENT for the behavior in this spec |

---

## 3. Start detection, profiles, and segment selection

The frame-loop model — post-advance inspection, `bk2_frame_offset :=
emu.framecount()` at detection, detection frame not recorded, trace row N =
state after applying BK2 row `offset + N` — is exactly S1 §2.1–§2.3. The
native pre-advance movie-end folding (S1 §2.3(a)/§2.4: finish before
applying row `offset+N` when `offset + N + 1 >= <BK2 row count>`; the final
input row is never consumed or recorded) applies unchanged.

### 3.1 Arm predicate (both profiles)

While `not started` (and `not finished`, `not skipping_segment`):

```
game_mode (u8 @ 0xF600) == 0x0C  AND  move_lock (u16be @ 0xB02E) == 0
```

Note the S2 control-lock offset: `PLAYER_BASE + 0x2E`, not S1's `+0x3E`.

### 3.2 Segment counting and skipping (`gameplay_segment_index`)

State: `gameplay_segment_index` (init 0), `skipping_segment` (init false),
`skipped_segment_zone_name` (init nil), `finished` (init false — once true,
never re-arm).

When the arm predicate fires and `gameplay_segment_index <
TARGET_GAMEPLAY_SEGMENT`:

1. Read `skip_zone_id` = u8 `0xFE10`; `skipped_segment_zone_name` :=
   `ZONE_NAMES[skip_zone_id]` or `"unknown_%02x"` (lowercase hex).
2. `skipping_segment := true`; return (nothing recorded, no files opened).

While `skipping_segment`, each completed frame checks only
`game_mode != 0x0C`. When the skipped segment ends (mode leaves level):

- If profile is `level_gated_reset_aware` AND `skipped_segment_zone_name ==
  "ehz"`: the segment is **not counted** (`gameplay_segment_index`
  unchanged) — this is the level-select movies' EHZ debug/menu bootstrap
  segment.
- Otherwise: `gameplay_segment_index := gameplay_segment_index + 1`.
- Either way `skipped_segment_zone_name := nil`, `skipping_segment := false`.

**`gameplay_segment_index` increments ONLY in this skip path.** It never
increments when a recorded segment finalizes, and never on the reset-aware
discard (§3.4). The `bypass_target_check` at Lua line ~1617 is
`run_id ~= nil and level_segment_count > 0` — **always false in plain mode**
(run-mode defensive guard against future skip-bookkeeping changes; the
comment itself notes it is not a live hazard). Do not port any bypass for
level-gameplay scope; the plain-mode arm check is exactly
`gameplay_segment_index < TARGET_GAMEPLAY_SEGMENT`.

Worked example (the ARZ fixtures, one invocation each):

- `arz` (TARGET=0): the movie's EHZ bootstrap ARMS immediately (0 < 0 is
  false) and starts recording EHZ; when the menu exit leaves level mode the
  reset-aware discard (§3.4) throws that recording away and re-arms;
  ARZ act 1 then records as `gameplay_segment: 0`.
- `arz2` (TARGET=1): EHZ bootstrap is skipped WITHOUT counting (reset-aware
  + ehz); ARZ act 1 is skipped and counted (index 0 → 1); ARZ act 2 records
  as `gameplay_segment: 1`.

### 3.3 Arm actions (recording start)

When the predicate fires and the target check passes:

1. `started := true`; `bk2_frame_offset := emu.framecount()`.
2. Capture from the detection frame's RAM: `start_x` u16be `0xB008`,
   `start_y` u16be `0xB00C`, `start_rng_seed` u32be `0xF636`,
   `start_rom_zone_id` u8 `0xFE10`,
   `start_zone_id` = `ROM_ZONE_TO_ENGINE_ZONE[start_rom_zone_id]` (falls back
   to the raw id if unmapped), `start_act` u8 `0xFE11`,
   `start_zone_name` = `ZONE_NAMES[start_rom_zone_id]` or `"unknown_%02x"`.
3. Open `physics.csv` (header §4.1) + `aux_state.jsonl`; write
   `metadata.json` (crash insurance; only final bytes matter).
4. Emit pre-trace aux events in this exact order (§6):
   `player_history_snapshot`, `cpu_state_snapshot`, then one
   `object_state_snapshot` per occupied slot 1..127 ascending.
5. Emit `zone_act_state` with `frame=0` and the arm-time game_mode (§7.1) —
   this primes the dedup key so the first recorded frame does NOT re-emit it.
6. Emit `checkpoint` `"gameplay_start"` with `frame=0` (§7.2).
7. Return without recording (detection-frame skip, as S1).

Zone name map (`ZONE_NAMES`, u8 `0xFE10` → name; else `unknown_%02x`
lowercase): `0x00="ehz"`, `0x01="unknown_01"`, `0x02="wz"`,
`0x03="unknown_03"`, `0x04="mtz"`, `0x05="mtz"`, `0x06="wfz"`, `0x07="htz"`,
`0x08="hpz"`, `0x09="unknown_09"`, `0x0A="ooz"`, `0x0B="mcz"`, `0x0C="cnz"`,
`0x0D="cpz"`, `0x0E="dez"`, `0x0F="arz"`, `0x10="scz"`.

Engine zone map (`ROM_ZONE_TO_ENGINE_ZONE`; unmapped → raw id):
`0x00→0, 0x0D→1, 0x0F→2, 0x0C→3, 0x07→4, 0x0B→5, 0x0A→6, 0x04→7, 0x05→7,
0x10→8, 0x06→9, 0x0E→10`.

Apparent act (`apparent_act_for`): `rom_zone_id == 0x05` (MTZ alternate id) →
`actual_act + 2`; otherwise `actual_act` unchanged.

### 3.4 Reset-awareness (`level_gated_reset_aware` only)

While `started`, if `game_mode != 0x0C` AND profile is
`level_gated_reset_aware` AND `start_zone_name == "ehz"`: the in-progress
recording is a debug/menu bootstrap — **discard and re-arm**:

- Close both files; delete `metadata.json`, `physics.csv`,
  `aux_state.jsonl` from the output dir.
- Reset ALL per-recording state to arm-fresh values: `started=false`,
  `trace_frame=0`, `bk2_frame_offset=0`, `start_*` zeroed/`"unknown"`,
  `prev_character_state` (both characters) zeroed, `prev_opl_screen=-1`,
  `known_objects={}`, `emitted_checkpoints={}`,
  `last_zone_act_state_key=nil`.
- **NOT reset:** `gameplay_segment_index`, `skipping_segment` bookkeeping,
  `finished`, and `recorded_sidekick_present` (sticky; harmless since Tails
  exists in all fixtures, but do not reset it).
- Return; the arm gate re-evaluates on subsequent frames.

Under `gameplay_unlock`, or when `start_zone_name != "ehz"`, leaving level
mode instead finalizes (§3.5). The reset check keys on the recorder's
`start_zone_name` string — reproduce it verbatim.

### 3.5 End conditions (plain mode, in Lua source order while `started`)

1. `game_mode != 0x0C` → reset-aware EHZ discard (§3.4) or finalize.
   Nothing is recorded for the frame that left level mode.
2. BK2-end guard (`HEADLESS` + movie loaded): with `movie_length =
   max(movie.length(), OGGF_BK2_FRAME_COUNT)`, finalize without recording
   when `movie_length > 0 and (bk2_frame_offset + trace_frame) >=
   movie_length`.
3. `movie.mode() == "FINISHED"` → finalize without recording.
4. Otherwise record row `trace_frame` (§4, §6, §7) and increment.

Pre-arm: if the movie reports FINISHED before the target segment became
recordable, finalize with no trace rows (no files were opened; no output).
The `FRAME_CAP` backstop (`effective length + 64`, else 2,000,000) and
`MOVIE_FRAME_SAFETY_MARGIN` (declared, unused) are Lua-lifecycle only.

The native port folds 2+3 into the single pre-advance predicate
`offset + N + 1 >= <BK2 row count>` exactly as S1 §2.3(a)/§2.4 — the
FINISHED signal fires on the `on_frame_end` after the advance that consumed
the movie's last input row, so that row is never recorded; the effective
`movie_length` (with the `OGGF_BK2_FRAME_COUNT` override) equals the BK2 row
count the native harness reads directly. All three level fixtures ended via
condition 1 (`game_mode` left `0x0C` before movie end: 899+5852+1=6752 <
6778; 2752+5073+1=7826 and 7998+7809+1=15808 < 15853), so the differential
gate cannot exercise the movie-end path — cover it with unit tests.

Finalization (plain mode): flush `physics.csv`, rewrite `metadata.json`
(final `trace_frame_count`), close both files. Only the final metadata bytes
matter (it is also rewritten at arm and every 300 recorded frames).

---

## 4. physics.csv (CSV v7, dual-character)

### 4.1 Header (exact, single line, then `\n`)

```
frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,player_present,player_x,player_y,player_x_speed,player_y_speed,player_g_speed,player_angle,player_air,player_rolling,player_ground_mode,player_x_sub,player_y_sub,player_routine,player_status_byte,player_stand_on_obj,player_animation_id,player_mapping_frame,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj,sidekick_animation_id,sidekick_mapping_frame
```

(Identical to the S1 v7 header.)

### 4.2 Row format (VERBATIM Lua format string, 42 fields)

```
"%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X\n"
```

Same specifier string as S1. Arguments in order:

| # | Column | Value |
|---|--------|-------|
| 1 | `frame` | `trace_frame` (row index N), `%04X` — **hex**, e.g. row 5850 renders `16DA` |
| 2 | `input` | BK2-derived mask (§5), `%04X` |
| 3 | `camera_x` | u16be `0xEE00` |
| 4 | `camera_y` | u16be `0xEE04` |
| 5 | `rings` | u16be `0xFE20` |
| 6 | `gameplay_frame_counter` | u16be `0xFE04` |
| 7 | `vblank_counter` | u16be `0xFE0E` |
| 8 | `lag_counter` | constant `0` → `0000` |
| 9 | `player_present` | constant `1` (`%d`) — Sonic's block is read unconditionally, no presence check |
| 10–14 | `player_x/y/x_speed/y_speed/g_speed` | u16be `0xB008`/`0xB00C`; s16be `0xB010`/`0xB012`/`0xB014` through uhex |
| 15 | `player_angle` | u8 `0xB026`, `%02X` |
| 16–18 | `player_air/rolling/ground_mode` | status bits `0x02`/`0x04`; ground_mode per §4.4 |
| 19–20 | `player_x_sub/y_sub` | u16be `0xB00A`/`0xB00E` |
| 21–25 | `player_routine/status_byte/stand_on_obj/animation_id/mapping_frame` | u8 `0xB024`/`0xB022`/`0xB03D`/`0xB01C`/`0xB01A`, `%02X` each |
| 26 | `sidekick_present` | `1` if u8 `0xB040` ≠ 0, else `0` (`%d`) |
| 27–42 | sidekick block | same 16 fields as 10–25 read from base `0xB040` via the shared character reader; **all zero when absent** |

The sidekick block comes from `read_character_trace_state(0xB040)`: when the
slot-0 id byte at `0xB040` is `0`, present=0 and every field is 0 (rendering
exactly `0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00` —
same bytes as S1's constant block). When present, the three speeds are read
**signed** (s16be) and passed through uhex at format time; `air`/`rolling`
come from the sidekick's own status byte; `ground_mode` uses the sidekick's
angle. This is the fundamental S2 delta from S1: **the sidekick columns are
live, symmetric reads, not constants.**

### 4.3 `uhex`

`uhex(v) = v < 0 ? v + 0x10000 : v` — identical to S1 §3.3. Applied to all
six speed fields (player and sidekick).

### 4.4 `ground_mode`

Identical to S1 §3.4: `air ? 0 : angle_to_ground_mode(angle)` with
thresholds `<=0x1F || >=0xE0 → 0`, `0x20..0x5F → 1`, `0x60..0x9F → 2`,
else `3`.

---

## 5. Input mask (CSV `input` column)

Byte-identical semantics to S1 §4, via the same shared
`C.bk2_input_mask(fallback_raw, trace_row, bk2_frame_offset)` (no
`frame_adjustment`): the mask comes from **`movie.getinput(bk2_frame_offset
+ N, 1)`**, i.e. BK2 input row `offset + N` — the same row applied for the
advance that produced row N.

```
Up→0x01  Down→0x02  Left→0x04  Right→0x08  (A or B or C)→0x10
START IS EXCLUDED — never contributes.
```

From the harness's `Bk2Frame` bits: `mask = (bk2 & 0x0F) | (((bk2 & 0x70)
!= 0) ? 0x10 : 0)`. The RAM fallback (`rom_joypad_to_mask` over u8 `0xF604`)
never triggers with a movie loaded — do not let it trigger natively. The
S2 movies carry no P2 activity; only controller 1 is read.

(The raw `0xF604` byte is still read each frame — it feeds the
`state_snapshot.raw_input`/`raw_input_mask` diagnostics, §7.5.)

---

## 6. Pre-trace (frame −1) events

Emitted once at arm time (§3.3 step 4), before trace frame 0, in this exact
order. All use `"frame":-1` and `"vfc"` = u16be `0xFE04` read at emission
(the arm-time counter; `0` in all fixtures). Values are decimal unless a
template shows a hex specifier.

### 6.1 `player_history_snapshot` (VERBATIM)

```
'{"frame":-1,"vfc":%d,"event":"player_history_snapshot","history_pos":%d,"x_history":[%s],"y_history":[%s],"input_history":[%s],"status_history":[%s]}'
```

Args: vfc; `history_pos` = u16be `0xEED2` `& 0xFF`; then four
comma-joined 64-entry decimal lists built for `i = 0..63` with
`offset = i*4`:

- `x_history[i]` = u16be `0xE500 + offset`
- `y_history[i]` = u16be `0xE500 + offset + 2`
- `input_history[i]` = u16be `0xE400 + offset`
- `status_history[i]` = u8 `0xE400 + offset + 2`

### 6.2 `cpu_state_snapshot` (VERBATIM)

```
'{"frame":-1,"vfc":%d,"event":"cpu_state_snapshot","character":"tails","control_counter":%d,"respawn_counter":%d,"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X","interact_id":"0x%02X","jumping":%d}'
```

Args: vfc, u16be `0xF702`, u16be `0xF704`, u16be `0xF708`, u16be `0xF70A`,
u16be `0xF70C`, u8 `0xF70E`, u8 `0xF70F`. Emitted unconditionally (even if
Tails is absent from slot 1).

### 6.3 `object_state_snapshot` (per occupied slot, VERBATIM)

`vfc` is read ONCE before the loop. For each slot 1..127 ascending whose id
byte (slot base `+0x00`) is non-zero:

```
'{"frame":-1,"vfc":%d,"event":"object_state_snapshot","slot":%d,"object_type":"0x%02X","fields":%s}'
```

`fields` is a JSON object built as: 64 raw-byte entries
`"off_%02X":"0x%02X"` for offsets `0x00..0x3F` (keys use UPPERCASE hex, e.g.
`off_0A`), followed by these semantic aliases in order — `x_pos` u16be
`+0x08` (`"0x%04X"`), `x_sub` u16be `+0x0A`, `y_pos` u16be `+0x0C`, `y_sub`
u16be `+0x0E`, `x_vel` s16be `+0x10` (+`0x10000` if negative, `"0x%04X"`),
`y_vel` s16be `+0x12` likewise, `id` u8 `+0x00` (`"0x%02X"`),
`render_flags` u8 `+0x01`, `status` u8 `+0x22`, `routine` u8 `+0x24`,
`routine_secondary` u8 `+0x25`, `mapping_frame` u8 `+0x1A`, `anim` u8
`+0x1C`, `anim_frame` u8 `+0x1B`, `anim_frame_timer` u8 `+0x1E`, `subtype`
u8 `+0x28`. All entries joined with `,`, wrapped in `{`…`}`.

Slot 0 (Sonic) is skipped (hydrated from `metadata.start_x/start_y`); slot 1
(Tails) is included.

---

## 7. aux_state.jsonl per-frame events (VERBATIM templates)

One event per line + `\n`. No spaces after `:` or `,`. Booleans are bare
`true`/`false`. `"frame"` = current `trace_frame`; `"vfc"` = u16be `0xFE04`
(re-read at the top of each helper in the Lua; constant within a frame, so
one read per frame is byte-identical). **`zone_act_state` and `checkpoint`
have NO `vfc` field.**

Per-frame emission order for recorded row N (after the §3.5 checks):

1. `zone_act_state` (§7.1) + possible act-transition `checkpoint` (§7.2).
2. CSV row (§4) — separate file; listed for completeness.
3. `check_mode_changes("sonic", 0xB000, ...)` (§7.3–7.6) using the
   status/routine values already read for the CSV row.
4. `check_mode_changes("tails", 0xB040, ...)` using `sidekick.status` /
   `sidekick.routine` from the CSV read. If the Tails id byte is 0: no
   events; the tails prev-state (status/routine/ctrl_lock) is zeroed.
5. `cpu_state` (§7.6) — every frame, unconditionally.
6. `cnz_slot_machine_state` (§7.7) — only when `start_rom_zone_id == 0x0C`.
7. Snapshot gate (§7.5): if `trace_frame % 60 == 0` **or `5104 <=
   trace_frame <= 5106` or `5995 <= trace_frame <= 6005`** →
   `state_snapshot` for sonic, then tails (each skipped if that character's
   id byte is 0). The two hardcoded frame windows are debugging leftovers
   baked into v9.6+ and present in all three fixtures' byte streams —
   **reproduce them verbatim** (they are part of the recorder's byte
   contract, not a replay carve-out).
8. `scan_objects` (§7.8) with subjects
   `[{sonic, slot 0, present=1, CSV x/y}, {tails, slot 1,
   present=sidekick.present, sidekick CSV x/y}]`.
9. `cursor_state` (§7.9).
10. `trace_frame := trace_frame + 1`.

Tracker initial values (arm time / §3.4 reset): per-character
`prev_character_state = {status=0, routine=0, ctrl_lock=0}`,
`prev_opl_screen = -1`, `known_objects` empty, `emitted_checkpoints` empty,
`last_zone_act_state_key = nil`.

### 7.1 `zone_act_state`

```
'{"frame":%d,"event":"zone_act_state","actual_zone_id":%d,"engine_zone_id":%d,"actual_act":%d,"apparent_act":%d,"game_mode":%d}'
```

Args: frame, u8 `0xFE10`, engine map of it, u8 `0xFE11`, apparent act
(§3.3), game_mode. Dedup: the event is skipped iff the formatted key
`"%d:%d:%d:%d:%d:%d"` of (frame, raw_zone, engine_zone, actual_act,
apparent_act, game_mode) equals the previous key. **Because the frame number
is part of the key, this emits every recorded frame** (fixture: 5852 lines
for 5852 rows); the dedup only suppresses the frame-0 duplicate of the
arm-time emission (§3.3 step 5), which primes the key with `frame=0`.

### 7.2 `checkpoint`

```
'{"frame":%d,"event":"checkpoint","name":"%s","actual_zone_id":%d,"engine_zone_id":%d,"actual_act":%d,"apparent_act":%d,"game_mode":%d%s}'
```

`%s` name is json-escaped; the trailing `%s` is `',"notes":"%s"'` when notes
are non-empty (never used in level scope — always empty). Each checkpoint
name fires at most once per recording (`emitted_checkpoints` set). Two
emitters:

- `"gameplay_start"` at arm, `frame=0` (§3.3 step 6).
- Per-frame in step 1: if u8 `0xFE11` (`actual_act`) differs from
  `start_act`, emit name
  `string.format("act_transition_to_%s%d", start_zone_name,
  apparent_act + 1)` — note it uses the **start** zone name and the
  **current** apparent act.

### 7.3 `mode_change` (VERBATIM; per character)

```
'{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"<FIELD>","from":%d,"to":%d}'
```

Character is `"sonic"` or `"tails"` — the S2 delta from S1 (which has no
`character` key). Checked in this order against the character's prev state:

1. `"air"` (status bit `0x02`) — on change, emit, then **immediately** emit
   that character's `state_snapshot` (§7.5).
2. `"rolling"` (bit `0x04`).
3. `"on_object"` (bit `0x08`).
4. `"control_locked"` — `move_lock (u16be base+0x2E) > 0` vs prev; after the
   check (fired or not) `prev.ctrl_lock := move_lock` unconditionally.

Then the routine check (§7.4); finally `prev.routine := routine` and
`prev.status := status` (both inside the helper in S2 — unlike S1 where the
caller updates prev_status).

### 7.4 `routine_change` (VERBATIM; per character)

Fired when the character's routine differs from prev (init 0 — both
characters typically fire `0x00 -> 0x02` on frame 0):

```
'{"frame":%d,"vfc":%d,"event":"routine_change","character":"%s","from":"0x%02X","to":"0x%02X","x":"0x%04X","y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,"status":"0x%02X","stand_on_obj":%d%s}'
```

Keys are `x`/`y` (S1 uses `sonic_x`/`sonic_y`). Args: frame, vfc, prev
routine, new routine, u16be `base+0x08`, u16be `base+0x0C`, s16be
`base+0x10` (**signed decimal**), s16be `base+0x12`, s16be `base+0x14`,
status (the passed-in value), u8 `base+0x3D`, optional suffix. Suffix is
empty when `stand_on_obj == 0` or `>= 128`; otherwise with `obj_addr =
0xB000 + stand_on_obj*0x40`:

```
',"stand_obj_slot":%d,"stand_obj_type":"0x%02X","stand_obj_x":"0x%04X","stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"'
```

(u8 `+0x00`, u16be `+0x08`, u16be `+0x0C`, u8 `+0x24`.) If the **new**
routine is `0x04` (hurt) or `0x06` (death), immediately emit that
character's `state_snapshot`.

### 7.5 `state_snapshot` (VERBATIM; per character)

Returns without emitting if the character's id byte (`base+0x00`) is 0.

```
'{"frame":%d,"vfc":%d,"event":"state_snapshot","character":"%s","control_locked":%s,"move_lock":"0x%04X","anim_id":%d,"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,"top_solid_bit":"0x%02X","lrb_solid_bit":"0x%02X","raw_input":"0x%02X","raw_input_mask":"0x%02X","logical_input":"0x%02X","logical_input_mask":"0x%02X","on_object":%s,"pushing":%s,"underwater":%s,"roll_jumping":%s}'
```

Args (fresh reads at emit time): frame, vfc, character,
`move_lock (u16be base+0x2E) > 0` → `true`/`false`, move_lock (`"0x%04X"`),
anim_id u8 `base+0x1C` (**decimal**), status u8 `base+0x22`, routine u8
`base+0x24`, y_radius **s8** `base+0x16` (signed decimal), x_radius **s8**
`base+0x17`, top_solid u8 `base+0x46`, lrb_solid u8 `base+0x47`, raw_input
u8 `0xF604`, `rom_joypad_to_mask(raw_input)` (`raw & 0x0F`, plus `0x10` if
`raw & 0x70`), logical_input u8 `0xF602`, `rom_joypad_to_mask` of it, then
`true`/`false` for status bits `0x08`, `0x20`, `0x40`, `0x10`.

Note: **both characters' snapshots embed CONTROLLER 1's** raw/logical input
bytes (the Lua reads `0xF604`/`0xF602` regardless of character). Triggers:
air mode_change, hurt/death routine change, and the step-7 gate
(`% 60 == 0` or frames 5104–5106 / 5995–6005).

### 7.6 `cpu_state` (VERBATIM, every frame)

With `delay = (0x10 << 2) + 4 = 68`, `record_index` = u16be `0xEED2`
`& 0xFF`, `delayed_index = (record_index - 68) & 0xFF`:

```
'{"frame":%d,"vfc":%d,"event":"cpu_state","character":"tails","interact":"0x%04X","idle_timer":%d,"flight_timer":%d,"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X","auto_fly_timer":0,"auto_jump_flag":%d,"ctrl2_held":"0x%02X","ctrl2_pressed":"0x%02X","ctrl2_raw_held":"0x%02X","ctrl1_logical":"0x%04X","pos_table_index":"0x%02X","delayed_index":"0x%02X","delayed_x":"0x%04X","delayed_y":"0x%04X","delayed_input":"0x%04X","delayed_status":"0x%02X","tails_status":"0x%02X","tails_interact":"0x%02X","tails_inertia":"0x%04X"}'
```

Args in order: frame; vfc; **`interact` = u8 `0xF70E`** (an 8-bit value
through `"0x%04X"` — renders e.g. `0x0001`); `idle_timer` = u16be `0xF702`
(the control counter — field names do NOT match the RAM variables);
`flight_timer` = u16be `0xF704` (respawn counter); `cpu_routine` = u16be
`0xF708`; `target_x` u16be `0xF70A`; `target_y` u16be `0xF70C`;
`auto_fly_timer` is the **literal `0`** baked into the template;
`auto_jump_flag` = u8 `0xF70F`; `ctrl2_held` u8 `0xF66A`; `ctrl2_pressed`
u8 `0xF66B`; `ctrl2_raw_held` u8 `0xF606`; `ctrl1_logical` **u16be**
`0xF602` (held+pressed word); `pos_table_index` = record_index;
`delayed_index`; `delayed_x` u16be `0xE500 + delayed_index`; `delayed_y`
u16be `0xE500 + delayed_index + 2`; `delayed_input` u16be
`0xE400 + delayed_index`; `delayed_status` u8 `0xE400 + delayed_index + 2`
(the delayed index is used as a **raw byte offset** into the buffers — ROM
convention, entries stride 4); `tails_status` u8 `0xB040+0x22`;
`tails_interact` u8 `0xB040+0x3D`; `tails_inertia` **u16be (unsigned)**
`0xB040+0x14`. Emitted even when Tails is absent (reads then return the
empty slot bytes).

### 7.7 `cnz_slot_machine_state` (VERBATIM; CNZ recordings only)

Gate: `start_rom_zone_id == 0x0C` (captured at arm — NOT the live zone
byte). Every frame, after `cpu_state`. Note this event carries **both**
`vfc` (u16be `0xFE04`) and `vbc` (u16be `0xFE0E`, hex):

```
'{"frame":%d,"vfc":%d,"vbc":"0x%04X","event":"cnz_slot_machine_state","in_use":"0x%04X","routine":"0x%02X","timer":"0x%02X","index":"0x%02X","reward":"0x%04X","slot1_pos":"0x%04X","slot1_speed":"0x%02X","slot1_routine":"0x%02X","slot2_pos":"0x%04X","slot2_speed":"0x%02X","slot2_routine":"0x%02X","slot3_pos":"0x%04X","slot3_speed":"0x%02X","slot3_routine":"0x%02X"}'
```

Args: frame, vfc, vbc, then the §1.5 reads in table order.

### 7.8 Object scanning (`scan_objects`)

`vfc` read once at scan start. For each slot 1..127 ascending (`addr =
0xB000 + slot*0x40`, `obj_id` = u8 `addr`, `prev_id` =
`known_objects[slot]` or 0):

**a. `object_appeared`** — iff `obj_id != 0 && obj_id != prev_id` (an id
CHANGE between two non-zero values fires appeared only, no removed):

```
'{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}'
```

(x/y = u16be `+0x08`/`+0x0C`.)

**b. `object_removed`** — iff `obj_id == 0 && prev_id != 0`:

```
'{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%02X"}'
```

(`object_type` is the PREVIOUS id.)

**c. `s2_tornado_state`** — iff `obj_id == 0xB2` (ObjB2 Tornado; emitted
before the proximity loop, unconditionally, not gated on proximity):

```
'{"frame":%d,"vfc":%d,"event":"s2_tornado_state","slot":%d,"x":"0x%04X","y":"0x%04X","y_sub":"0x%04X","y_vel":"0x%04X","routine":"0x%02X","routine_secondary":"0x%02X","status_byte":"0x%02X","objoff_2e":"0x%02X","objoff_2f":"0x%02X","objoff_30":"0x%02X","objoff_31":"0x%02X"}'
```

Args: frame, vfc, slot, u16be `+0x08`, u16be `+0x0C`, u16be `+0x0E`,
**u16be (unsigned)** `+0x12`, u8 `+0x24`, u8 `+0x25`, u8 `+0x22`, u8
`+0x2E`, u8 `+0x2F`, u8 `+0x30`, u8 `+0x31`. Diagnostic only (SCZ/WFZ
route); not fed back into replay.

**d. `object_near`** — for each subject in order **sonic then tails**: iff
`subject.present != 0 && slot != subject.slot && |obj_x - subject.x| <= 160
&& |obj_y - subject.y| <= 160` (subject x/y are the CSV-row reads):

```
'{"frame":%d,"vfc":%d,"event":"object_near","character":"%s","slot":%d,"type":"0x%02X","x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}'
```

(Key is `"type"` here vs `"object_type"` above. Sonic's slot is 0, never
scanned, so his self-exclusion is vacuous; Tails' `slot != 1` check is
live.) Then `known_objects[slot] := obj_id` (always, including to 0).

**e. `slot_dump`** — after the loop, iff any `object_appeared` fired this
frame:

```
'{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}'
```

`%s` scans **dynamic slots 16..127 only** (fresh id reads; S1 scans
32..127), collecting `[%d,"0x%02X"]` per non-empty slot, joined with `,`,
wrapped in `[`…`]` (empty → `[]`), e.g. `[[16,"0x9D"]]`.

### 7.9 `cursor_state`

Identical template and semantics to S1 §5.9 (including `prev_opl_screen`
init −1, the first recorded frame always firing, and the `"L"`/`"R"` rule),
with S2 addresses: `opl_screen` u16be `0xF76E`, `fwd_ptr` u32be `0xF770`,
`bwd_ptr` u32be `0xF774`, `fwd_ctr` u8 `0xFC00`, `bwd_ctr` u8 `0xFC01`:

```
'{"frame":%d,"vfc":%d,"event":"cursor_state","opl_screen":"0x%04X","fwd_ptr":"0x%08X","bwd_ptr":"0x%08X","fwd_ctr":%d,"bwd_ctr":%d,"dir":"%s"}'
```

---

## 8. metadata.json — exact byte layout (trace_schema 9)

Written with 2-space indent, this exact key order, LF endings, trailing `\n`
after `}`. Plain-mode template (run-mode `run_id`/`segment_index` lines are
absent):

```
{
  "game": "s2",
  "zone": "<start_zone_name>",
  "zone_id": <start_zone_id>,
  "rom_zone_id": <start_rom_zone_id>,
  "act": <apparent_act_for(start_rom_zone_id, start_act) + 1>,
  "gameplay_segment": <gameplay_segment_index>,
  "bk2_frame_offset": <offset>,
  "trace_frame_count": <trace_frame>,
  "start_x": "0x<%04X of start_x>",
  "start_y": "0x<%04X of start_y>",
  "characters": <["sonic", "tails"] | ["sonic"]>,
  "main_character": "sonic",
  "sidekicks": <["tails"] | []>,
  "rng_seed": "0x<%08X of start_rng_seed>",
  "recording_date": "<%Y-%m-%d>",
  "lua_script_version": "9.12-s2",
  "trace_schema": 9,
  "csv_version": 7,
  "aux_schema_extras": ["cnz_slot_machine_state_per_frame", "cpu_state_per_frame"],
  "trace_profile": "<json_escape(TRACE_PROFILE)>",
  "bizhawk_version": "2.11",
  "genesis_core": "Genplus-gx",
  "route": "<start_zone_name>",
  "source_bk2": "<json_escape(SOURCE_BK2)>",
  "rom_checksum": "",
  "notes": ""
}
```

Field derivations:

- `zone` / `route`: both are `start_zone_name` (§3.3), written raw (not
  escaped).
- `zone_id`: engine progression id (§3.3 map); `rom_zone_id`: raw u8
  `0xFE10` at arm.
- `act`: apparent act (MTZ `0x05` → +2) **plus 1** (1-based).
- `gameplay_segment`: `gameplay_segment_index` at write time — the number
  of COUNTED skipped segments (§3.2), NOT the target env value (they match
  whenever recording succeeds).
- `bk2_frame_offset` / `trace_frame_count`: as recorded; final rewrite wins.
- `start_x`/`start_y`: `"0x%04X"` (shared `hex()` helper, width 4; the
  negative +0x10000 adjustment cannot trigger on unsigned reads).
  `rng_seed`: `"0x%08X"` of u32be `0xF636` at arm (`hex(v, 8)`).
- `characters`/`sidekicks`: sidekick considered present iff
  `recorded_sidekick_present` (sticky true once any recorded frame saw a
  non-zero id at `0xB040`) OR the id at `0xB040` is non-zero at write time.
  Present → `["sonic", "tails"]` / `["tails"]` (note the space after the
  comma in `characters`); absent → `["sonic"]` / `[]`.
- `recording_date`: local date `%Y-%m-%d` — nondeterministic, normalized in
  comparisons.
- `lua_script_version`: the native port writes `"9.12-s2"`. Fixtures
  stamped `"9.11-s2"` differ ONLY in this string (the v9.12 header's
  declared plain-mode byte-identity) — the only permitted normalization
  besides `recording_date`.
- `trace_schema` 9 / `csv_version` 7 / `aux_schema_extras` /
  `bizhawk_version` "2.11" / `genesis_core` "Genplus-gx": literals.
- `trace_profile`: the profile string (env), json-escaped.
- `source_bk2`: json-escaped `SOURCE_BK2` — natively the movie file's
  basename (§2).
- `rom_checksum` / `notes`: empty-string literals.

The Lua writes metadata at arm, every 300 recorded frames, and at finalize;
each write truncates. Only final bytes matter — the native port may write
once at finish. The bootstrap-comparator eligibility
(`TraceMetadata.nativePreludeMode()`) is derived from the version string; no
separate flag is emitted.

---

## 9. File encodings

Identical to S1 §8: UTF-8 without BOM (pure ASCII in practice), LF-only
line endings, every line including the last terminated with `\n`. Verified
against all three fixtures.

---

## 10. Shared with S1 vs genuinely different

Byte-for-byte shared with the S1 port (reuse, do not fork):

- Frame-alignment model, detection-frame skip, `bk2_frame_offset`
  convention, native pre-advance movie-end folding (S1 §2).
- CSV v7 header text, 42-field format string, uhex, ground_mode thresholds.
- Input-mask derivation incl. Start exclusion and the never-taken RAM
  fallback.
- `mode_change`/`state_snapshot` trigger structure, `object_appeared`/
  `object_removed`/`object_near`/`slot_dump`/`cursor_state` template
  skeletons and ordering, tracker initializations, snapshot-on-air/hurt/
  death rules.
- metadata write cadence, encodings, stdout being non-contractual.

Genuinely different in S2 (the S2-only surface):

- RAM layout: player base `0xB000` (not `0xD000`); `move_lock` at `+0x2E`
  (not `+0x3E`); camera at `0xEE00`/`0xEE04`; dynamic slots start at 16
  (not 32); solidity-plane bytes `+0x46`/`+0x47` exist and are recorded.
- Live symmetric sidekick block in the CSV (S1 writes constants).
- Character-scoped aux events: `character` key in `mode_change`,
  `routine_change` (with `x`/`y` key names), `state_snapshot`, and
  `object_near`; both characters checked per frame (sonic first).
- `state_snapshot` extras: `move_lock`, `top_solid_bit`, `lrb_solid_bit`,
  `raw_input`/`raw_input_mask`/`logical_input`/`logical_input_mask`.
- Per-frame `cpu_state` + pre-trace `cpu_state_snapshot` /
  `player_history_snapshot` (Tails CPU + delayed Sonic history).
- Frame −1 `object_state_snapshot` full-slot dumps.
- `zone_act_state` every frame + `checkpoint` events (incl. act-transition
  naming), with the frame-in-dedup-key quirk.
- `s2_tornado_state` (ObjB2) and `cnz_slot_machine_state` diagnostics.
- The hardcoded snapshot windows 5104–5106 and 5995–6005.
- Segment selection: profiles, `gameplay_segment_index` counting, EHZ
  bootstrap skip/discard semantics, `OGGF_BK2_FRAME_COUNT` effective movie
  length.
- metadata: schema 9 with `rom_zone_id`, `gameplay_segment`,
  `rng_seed`, `trace_profile`, `bizhawk_version`, `genesis_core`, `route`,
  `source_bk2`, `aux_schema_extras`; MTZ apparent-act adjustment; sidekick
  presence rule.

Dead Lua code not to port (level scope): `ADDR_OPL_ROUTINE` (`0xF76C`),
`OFF_STICK_CONVEX` (`+0x38`), `MOVIE_FRAME_SAFETY_MARGIN`, the RAM-input
CSV fallback, `rom_zone_id 0x01/0x03/0x09` placeholder names (unreachable in
practice but keep the `unknown_%02x` fallback), and every `run_id`-gated
branch (§0). `OFF_ANIM_FRAME` (`+0x1B`) and `OFF_ANIM_TIMER` (`+0x1E`) ARE
used — by `object_state_snapshot` aliases (unlike S1 where they are dead).
