# S3K complete-run recorder — profiles and aux events

Authoritative behavioural spec for the native port of
`tools/bizhawk/s3k_complete_run_recorder.lua`
(`LUA_SCRIPT_VERSION = "6.33-s3k-completerun"`, line 357).

Authority order, per the migration contract:

1. The Lua script itself (line references below are into
   `s3k_complete_run_recorder.lua` at commit `77402cdfa`).
2. The committed fixtures under `src/test/resources/traces/s3k/` (read-only).
3. `docs/skdisasm` for RAM-layout questions.

Companion documents: `s3k-trace-recorder-behavior.md`, `s3k-aux-events.md`,
`s3k-profiles-and-hooks.md` (the already-ported STANDARD recorder), and
`s1-complete-run-behavior.md` / `s2-run-mode-behavior.md` §11 for the shared
run/manifest model.

---

## 1. The three profiles

`TRACE_PROFILE` is hard-pinned to `"complete_run"` at line 341 — the
single-arm `aiz_end_to_end` / `level_gated_reset_aware` paths are structurally
unreachable in this recorder. The profile *written into a segment's
`metadata.json`* is nevertheless one of three values, selected per segment:

| Profile string | Selected when | Segment `kind` | Writer functions |
|---|---|---|---|
| `complete_run` | level segment: `current_segment_is_bonus == false` | `level` | `start_new_segment` (5026), `write_metadata` (~1280–1452), `finalize_segment` (4995) |
| `s3k_bonus_stage` | `BONUS_TOKENS[zone_id] ~= nil`, i.e. `zone_id ∈ {0x13 gumball, 0x14 pachinko, 0x15 slots}` (line 439) | `bonus_stage` | same as above; `write_metadata` branches at 1431 |
| `s3k_special_stage` | `Game_mode == GAMEMODE_SPECIAL_STAGE` ($34) detour | `special_stage` | `start_ss_segment` (5137), `write_ss_metadata` (5103), `write_ss_row` (5174), `finalize_ss_segment` (5240) |

### 1.1 Selection is data-driven, never a zone/route carve-out

* Bonus is a **zone-id table lookup** (`BONUS_TOKENS`), not a zone name test.
* Special stage is a **`Game_mode` register test**, evaluated *before* the
  per-zone arm gate and before the level-family row guard, in
  `on_frame_end` source order (5335–5382).
* The port must reproduce this ordering literally. The S1 and S2 ports each
  independently regressed by evaluating segment arm/publish/stop
  **pre**-advance; every condition here is evaluated **post**-advance
  (`on_frame_end` runs after `emu.frameadvance()` in the `while true` loop at
  5850–5902).

### 1.2 What each profile captures

**`complete_run`** — the full S3K level schema. Arms on the first
`Game_mode == 0x0C` **AND** `zone_id ~= current_segment_zone` **AND**
`ctrl_lock_timer == 0` **AND** `Ctrl_1_locked == 0` frame (5411–5414), then
**arms and returns** — the arm frame is *not* row 0; the next BizHawk frame is
(5463–5476). `bk2_frame_offset` stays at the arm frame `F`, so row `N` is BK2
frame `bk2_frame_offset + N`. Records continuously through later control
locks, the seamless act1→act2 transition, and the trailing `0x8C` zone-exit
handoff frames of the *next* zone until that zone reaches its own unlocked
`0x0C`.

**`s3k_bonus_stage`** — byte-for-byte the *same* row writer and the *same*
aux pipeline as `complete_run`. A bonus segment is an ordinary level segment
that happened to arm on a bonus zone id. The only differences are in
`metadata.json` (`trace_profile`, `bonus_stage_type`, `v_int_run_count`) and
in the manifest (`kind`, `bonus_stage_type`).

**`s3k_special_stage`** — a **completely separate** row writer and metadata
writer. 20-column blue-spheres CSV, **no aux events at all**, and a metadata
document with a disjoint field set. See §2.2 and §4.4.

---

## 2. `physics.csv` — column sets and format strings

### 2.1 `complete_run` and `s3k_bonus_stage` (identical, 42 columns)

Verified identical header bytes across `aiz_completerun`, `hcz_completerun`,
`mgz_completerun`, `cnz_completerun`, `icz_completerun`, `lbz_completerun`,
`mhz_completerun`, `bonus_gumball`, `bonus_slots`, `bonus_pachinko`, and every
level/bonus segment of `runs/s3-knux-multibonus-ss/`.

```
frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,player_present,player_x,player_y,player_x_speed,player_y_speed,player_g_speed,player_angle,player_air,player_rolling,player_ground_mode,player_x_sub,player_y_sub,player_routine,player_status_byte,player_stand_on_obj,player_animation_id,player_mapping_frame,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj,sidekick_animation_id,sidekick_mapping_frame
```

Row format string, verbatim (5587–5588):

```lua
"%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X,"
    .. "%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X\n"
```

Uppercase hex, zero-padded. `player_present` is the literal `1`. Signed
16-bit velocities pass through the local `uhex()` (`v < 0 → v + 0x10000`,
5575–5578). `input` is derived from the **BK2 movie**, not from `$FFF604`
(5567–5573) — the ROM byte lags on lag-frame paths. `sidekick_*` comes from
`read_character_trace_state(SIDEKICK_BASE)` (1687), which returns an all-zero
struct with `present = 0` when `read_u32_be(SIDEKICK_BASE) == 0`.

Flush cadence: `physics_file:flush()` every 60 rows; `write_metadata()`
re-emitted every 300 rows (5626–5631).

### 2.2 `s3k_special_stage` (20 columns)

```
frame,input,input_p2,lag,anim_frame,x_pos,y_pos,angle,velocity,turning,jumping,fade_timer,spheres_left,ring_count,rings_left,rate,rate_timer,clear_timer,clear_routine,started
```

Row format string, verbatim (5200):

```lua
"%d,%x,%x,%d,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x\n"
```

**Different in every respect from §2.1:** `frame` and `lag` are decimal, every
other column is **lowercase, unpadded** hex (the S2 SS recorder convention).
`lag` is `emu.islagged() and 1 or 0`; `started` is `Special_stage_started ~= 0`
as `0`/`1`. Column order is the blue-spheres plan's TABLE order, **not**
address order — `rate_timer` (`0xE43E`) is emitted *after* `rate` (`0xE444`),
matching `S3kSpecialStageTraceFrame`'s parser. Source addresses (475–490):

| Column | Address | Width |
|---|---|---|
| `anim_frame` | `0xE420` | u16 |
| `x_pos` | `0xE422` | u16 |
| `y_pos` | `0xE424` | u16 |
| `angle` | `0xE426` | u8 |
| `velocity` | `0xE428` | s16 → `+0x10000` if negative |
| `turning` | `0xE42A` | u8 |
| `jumping` | `0xE432` | u8 |
| `fade_timer` | `0xE433` | u8 |
| `spheres_left` | `0xE438` | u16 |
| `ring_count` | `0xE43A` | u16 |
| `rings_left` | `0xE442` | u16 |
| `rate` | `0xE444` | u16 |
| `rate_timer` | `0xE43E` | u16 |
| `clear_timer` | `0xE44A` | u16 |
| `clear_routine` | `0xE44C` | u8 |
| `started` | `0xE450` | u8 → 0/1 |

`input` / `input_p2` come from `ss_input_mask(player, …)` (5075), which reads
`movie.getinput(bk2_frame_offset + trace_row, player)` and accepts either the
`"P<n> Up"` or bare `"Up"` key form; `A|B|C` from either form all fold into
`INPUT_JUMP`. Falls back to `C.rom_joypad_to_mask(raw)` when no movie is
loaded.

### 2.3 Line endings

Empirically measured (`grep -c $'\r$'` on every gunzipped file):

| Fixture set | `physics.csv` | `aux_state.jsonl` | `metadata.json` | `run_manifest.json` |
|---|---|---|---|---|
| **(A)** `*_completerun` × 7 | LF | LF | LF | — |
| **(C)** `bonus_gumball` / `bonus_slots` / `bonus_pachinko` / `special_stage` | LF | LF | LF | — |
| **(B)** `runs/s3-knux-multibonus-ss/**` (all 25 segments) | **CRLF** | **CRLF** | **CRLF** | **CRLF** |

This is **not** a run-mode-vs-plain-mode distinction ((C) also carries a
`run_id` and is LF), and **not** a `runs/`-layout distinction either: the
repo-wide census in
[s3k-complete-run-behavior.md](s3k-complete-run-behavior.md) §9 finds
`traces/s1/special_stage/` CRLF outside any `runs/` tree. It is the
host-platform text-mode artefact of Lua's `io.open(path, "w")` — (B) was
captured 2026-07-19 on the CRLF host, (A) and (C) on 2026-07-23 on the LF
host. The native port must therefore make the newline convention an explicit
per-fixture property, as `Program.cs`'s `ExpandNewlinesIf` /
`ExpandRunNewlines` already do for S1/S2; it must **not** be inferred from
run-vs-plain mode or from the output layout.

---

## 3. Per-frame aux emission order

Every aux event for a `complete_run` / `s3k_bonus_stage` row is written from
`on_frame_end` **after** the `physics_file:write(...)` for that same row
(5586). Source order (5633–5783), which is also the byte order in
`aux_state.jsonl` and must be ported literally:

| # | Call | Family emitted |
|---|---|---|
| 1 | `emit_s3k_semantic_events(trace_frame)` (1119) | `zone_act_state` (dedup), `checkpoint` (other profiles only) |
| 2 | `emit_player_mode_event()` (1892) | `player_mode_set` |
| 3 | `check_mode_changes(status, routine)` (1903) | `mode_change` ×4 fields, inline `state_snapshot` on the `air` edge, `routine_change`, inline `state_snapshot` on hurt/death |
| 4 | `write_tails_cpu_per_frame()` (1600) | `cpu_state` |
| 5 | `V65.flush_tails_cpu_normal_step()` (3554) | `tails_cpu_normal_step` **(hook)** |
| 6 | `V66.flush_aiz_boundary_state()` (3776) | `aiz_boundary_state` **(hook)** |
| 7 | `V67_AIZ.flush_aiz_transition_floor_solid()` (4033) | `aiz_transition_floor_solid` **(hook)** |
| 8 | `V69_AIZ.flush_aiz_handoff_terrain_state()` (4196) | `aiz_handoff_terrain_state` **(poll, hook-enriched)** |
| 9 | `V628_AIZ_FIRE.write()` (3391) | `aiz_fire_transition` — **profile-gated off**: returns immediately unless `is_aiz_end_to_end_profile()` (3393). Unreachable here. |
| 10 | `write_oscillation_per_frame()` (1579) | `oscillation_state` |
| 11 | `write_game_paused_per_frame()` (1566) | `game_paused_state` |
| 12 | `write_object_states_per_frame(...)` (1983) | `object_state` (0..n) |
| 13 | `write_interact_state_per_frame(sk_present)` (2225) | `interact_state` (1 or 2) |
| 14 | `write_sidekick_interact_object_state(sk_present)` (2018) | `sidekick_interact_object` (0 or 1) |
| 15 | `write_air_countdown_state_per_frame()` (2103) | `air_countdown_state` (always exactly 2) |
| 16 | `V67_CNZ.emit_cnz_cylinder_state_per_frame()` (3209) | `cnz_cylinder_state` **(poll)** |
| 17 | `V67_CNZ.flush_cnz_cylinder_hits()` (3180) | `cnz_cylinder_execution` **(hook)** |
| 18 | `V622_CNZ_EVENT_RAM.write()` (3313) | `cnz_event_ram` **(poll, env-gated off)** |
| 19 | `CAGE_DIAG.emit_cage_state_per_frame()` (4955) | `cage_state` **(poll)** |
| 20 | `CAGE_DIAG.flush_cage_hits()` (4941) | `cage_execution` **(hook)** |
| 21 | `WRITE_DIAG.flush_tails_velocity_writes()` (2483) | `velocity_write` **(memwrite hook)** |
| 22 | `WRITE_DIAG.flush_tails_position_writes()` (2657) | `position_write` **(memwrite hook)**, `"sonic"` then `"tails"` |
| 23 | `V618_AIZ_SHIP.flush()` (2777) | `aiz_ship_loop` **(hook)** |
| 24 | `V621_SONIC_RECORD.flush()` (2866) | `sonic_record_pos` **(hook)** |
| 25 | `V625_RNG_CALLS.flush()` (3045) | `rng_call` **(hook, env-gated off)** |
| 26 | `V611_SOLID.flush_solid_object_cont_entries()` (4536) | `solid_object_cont_entry` **(hook)** |
| 27 | `if trace_frame % 60 == 0 then write_state_snapshot()` (1822) | `state_snapshot` |
| 28 | `write_control_lock_state(trace_frame % 60 == 0)` (1861) | `control_lock_state` |
| 29 | `V613_AIZ_WALL.write_terrain_wall_sensor()` (4375) | `terrain_wall_sensor` **(poll)** |
| 30 | `V615_CRL.flush_collision_response_list_per_frame()` (4820) | `collision_response_list_per_frame` **(hook)**, then `collision_response_list_end_of_frame` **(poll)** |
| 31 | `scan_objects(x, y)` (1760) | per slot: `object_appeared`, `object_removed`, `object_near`; then one `slot_dump` if any appeared |

Confirmed empirically against `aiz_completerun/aux_state.jsonl` frame 0:
`cpu_state_snapshot` → `zone_act_state` → `player_mode_set` → `routine_change`
→ `cpu_state` → `oscillation_state` → `game_paused_state` → `object_state`×3 →
`interact_state`×2 → `sidekick_interact_object` → `air_countdown_state`×2 →
`state_snapshot` → `control_lock_state` → `object_appeared`×7 → `object_near` →
`slot_dump`.

### 3.1 Pre-trace one-shots

On the first frame that produces a physics row, before the row is written
(5527–5544):

1. `write_tails_cpu_snapshot()` → one `cpu_state_snapshot` with `"frame":-1`.
2. `write_object_snapshots()` → zero or more `object_state_snapshot`, also
   `"frame":-1`, emitted only for slots whose `object_code` maps through
   `snapshot_object_id_for_code` (1625) — currently **only** `OBJ_CNZ_BALLOON`.
3. `start_gameplay_frame_counter = read_u16_be(ADDR_FRAMECOUNT)` — this value
   becomes `pre_trace_osc_frames` in `metadata.json`.

---

## 4. Aux family catalogue

`frame` is `trace_frame`; `vfc` is `read_u16_be(ADDR_FRAMECOUNT)` = 
`Level_frame_counter` at `0xFE04` (see §7.2 — this address changed after the
(B) fixtures were captured). All templates below are **verbatim** from the
Lua. `\n` is appended by `C.write_aux`.

### 4.1 Always-on frame-polled families

| Family | Template (verbatim) | Scope |
|---|---|---|
| `zone_act_state` | `{"frame":%d,"event":"zone_act_state","actual_zone_id":%s,"actual_act":%s,"apparent_act":%s,"game_mode":%s}` | Deduplicated on `zone\|act\|apparent\|mode` key (1088). **No `vfc` field.** Values via `json_int_or_null`. |
| `player_mode_set` | `{"frame":%d,"vfc":%d,"event":"player_mode_set","mode":%d}` | Emitted on change of `Player_mode` (`0xFF08`, u16) incl. first frame. |
| `mode_change` (air) | `{"frame":%d,"vfc":%d,"event":"mode_change","field":"air","from":%d,"to":%d}` | P1 only. **Followed immediately by an inline `state_snapshot`.** |
| `mode_change` (rolling) | `…"field":"rolling"…` | P1 only. |
| `mode_change` (on_object) | `…"field":"on_object"…` | P1 only. |
| `mode_change` (control_locked) | `…"field":"control_locked"…` | P1 only, from `PLAYER_BASE+OFF_CTRL_LOCK > 0`. |
| `routine_change` | `{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X","sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,"status":"0x%02X","stand_on_obj":%d%s}` | P1 only. `%s` tail is `,"stand_obj_slot":%d,"stand_obj_type":"0x%08X","stand_obj_x":"0x%04X","stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"` when `0 < stand_on_obj < 110`. Velocities are **signed decimal**. Extra inline `state_snapshot` when the new routine is HURT or DEATH. |
| `cpu_state` | `{"frame":%d,"vfc":%d,"event":"cpu_state","character":"tails","interact":"0x%04X","idle_timer":%d,"flight_timer":%d,"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X","auto_fly_timer":%d,"auto_jump_flag":%d,"ctrl2_held":"0x%02X","ctrl2_pressed":"0x%02X","pos_table_index":"0x%04X"}` | Always `"tails"`. Emitted every frame **even when no sidekick exists** (globals are read unconditionally) — confirmed: 1200 `cpu_state` in the Knuckles-alone `bonus_slots`. |
| `oscillation_state` | `{"frame":%d,"vfc":%d,"event":"oscillation_state","level_frame_counter":%d,"osc_table":"%s"}` | `osc_table` is `0x42` bytes at `0xFE6E` as `%02X` concatenated (132 hex chars). `level_frame_counter` and `vfc` are **the same read**. |
| `game_paused_state` | `{"frame":%d,"vfc":%d,"event":"game_paused_state","game_paused":%d}` | Word read at `0xF63A`. |
| `object_state` | `{"frame":%d,"vfc":%d,"event":"object_state","slot":%d,"object_code":"0x%08X","routine":"0x%02X","status":"0x%02X","subtype":"0x%02X","x":"0x%04X","y":"0x%04X","x_radius":%d,"y_radius":%d}` | Slots `1..109`, non-zero `object_code`, within `OBJECT_PROXIMITY = 160` of **either** P1 or P2 (P2 only when `sidekick.present == 1`). |
| `interact_state` | `{"frame":%d,"vfc":%d,"event":"interact_state","character":"sonic","interact":"0x%04X","interact_slot":%d,"status":"0x%02X","status_secondary":"0x%02X","object_control":"0x%02X"}` | `"sonic"` always; a second event with `"tails"` **only if `sidekick.present == 1`**. `object_control` is offset `$2E`. |
| `sidekick_interact_object` | `{"frame":%d,"vfc":%d,"event":"sidekick_interact_object","character":"tails","interact":"0x%04X","interact_slot":%d,"tails_render_flags":"0x%02X","tails_object_control":"0x%02X","tails_invulnerability_timer":"0x%02X","tails_width_pixels":"0x%02X","tails_height_pixels":"0x%02X","camera_x_copy":"0x%04X","camera_y_copy":"0x%04X","tails_status":"0x%02X","tails_on_object":%s,"object_code":"0x%08X","object_routine":"0x%02X","object_status":"0x%02X","object_x":"0x%04X","object_y":"0x%04X","object_subtype":"0x%02X","object_render_flags":"0x%02X","object_object_control":"0x%02X","object_active":%s,"object_destroyed":%s,"object_p1_standing":%s,"object_p2_standing":%s}` | **Early-returns when no sidekick** (2020) — absent from every Knuckles-alone fixture. Booleans via `tostring()`. |
| `air_countdown_state` | `{"frame":%d,"vfc":%d,"event":"air_countdown_state","owner":"%s","fixed_slot":%d,"object_code":"0x%08X","routine":"0x%02X","subtype":"0x%02X","obj30":"0x%04X","obj36":"0x%02X","obj37":"0x%02X","obj38":"0x%02X","obj3a":"0x%04X","obj3c":"0x%04X","obj3e":"0x%04X","owner_ptr":"0x%08X","owner_resolved":"%s","owner_air_left":"0x%02X","owner_status":"0x%02X","owner_status_secondary":"0x%02X","owner_facing_left":%s,"owner_underwater":%s,"rng_seed":"0x%08X","visible_children":[%s]}` | **Exactly 2 per frame**, `owner` `"p1"` (fixed slot 94) then `"p2"` (fixed slot 95), regardless of sidekick presence. Child element template at 2142–2149. |
| `state_snapshot` | `{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,"on_object":%s,"pushing":%s,"underwater":%s,"roll_jumping":%s}` | Every 60 frames, **plus** inline from `mode_change`(air) and hurt/death `routine_change`. Radii are **signed** (`read_s8`). |
| `control_lock_state` | `{"frame":%d,"vfc":%d,"event":"control_lock_state","ctrl1_locked":%d,"ctrl2_locked":%d,"ctrl1_logical":"0x%04X","ctrl2_logical":"0x%04X"}` | On any change of the four values, **plus** forced every 60 frames. `Ctrl_2_logical` is `0xF66A`, not adjacent to `Ctrl_1_logical` (`0xF602`). |
| `object_appeared` | `{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%08X","x":"0x%04X","y":"0x%04X"%s}` | Slots `1..109`, on `code ~= 0 and code ~= prev`. `%s` tail is `,"angle":"0x%02X","base_y":"0x%04X"` for `OBJ_CNZ_BALLOON` only. |
| `object_removed` | `{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%08X"}` | On `code == 0 and prev ~= 0`; reports the **previous** code. |
| `object_near` | `{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%08X","x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"%s}` | Proximity `160` to **P1 only** (`scan_objects` receives only the P1 coords) — this is why `object_near` ≤ `object_state` in two-player fixtures and **equal** in one-player fixtures. Same CNZ-balloon tail. |
| `slot_dump` | `{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}` | Once per frame **iff** any `object_appeared` fired. `slots` is `[[slot,"0x%08X"],…]` over dynamic slots `3..92` with non-zero code (1748). |

### 4.2 Window- or data-gated frame-polled families

These emit with diagnostic hooks **off** and therefore appear in the (A)/(C)
fixtures.

| Family | Gate | Template |
|---|---|---|
| `cage_state` | **Data-driven only**: any OST slot `0..109` whose `object_code` is the CNZ wire-cage init or frame pointer. No zone or frame gate. | `{"frame":%d,"vfc":%d,"event":"cage_state","slot":%d,"x":"0x%04X","y":"0x%04X","subtype":"0x%02X","status":"0x%02X","p1_phase":"0x%02X","p1_state":"0x%02X","p2_phase":"0x%02X","p2_state":"0x%02X"}` |
| `cnz_cylinder_state` | Frame window `[4490, 4512]` (863–864, env `OGGF_S3K_CNZ_CYLINDER_RANGE`) **and** slot `object_code == OBJ_CNZ_CYLINDER`. No zone gate. | `{"frame":%d,"vfc":%d,"event":"cnz_cylinder_state","slot":%d,"x":"0x%04X","y":"0x%04X","subtype":"0x%02X","status":"0x%02X","routine":"0x%02X","render_flags":"0x%02X","p1_state":"0x%02X","p1_angle":"0x%02X","p1_distance":"0x%02X","p1_threshold":"0x%02X","p2_state":"0x%02X","p2_angle":"0x%02X","p2_distance":"0x%02X","p2_threshold":"0x%02X"}` |
| `aiz_handoff_terrain_state` | Frame window `[5430, 5438]` (env `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START`/`_END`) **and** `Current_zone == 0` (4113–4119). The flush (4195) gates only on `aux_file` / `started` / `in_window()` — there is **no** hook-state check — so it emits unconditionally with hooks off, with the hook-fed accumulators at their `V69_AIZ.current()` defaults (4121–4137): the two `*_seen` booleans (`sonic_floor_seen`, `solid_vertical_seen`) read `false` and their seven companion numerics read `"0x0000"`/`"0x00"`. | 4206–4215; 24 non-`event` fields. |
| `terrain_wall_sensor` | Frame window `[7549, 7560]` (env `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`) **and** `Current_zone == 0`. | `{"frame":%d,"vfc":%d,"event":"terrain_wall_sensor",%s,%s}` where each `%s` is `V613_AIZ_WALL.snapshot_player(base, label)` (4506) for `"sonic"` then `"tails"`. |
| `collision_response_list_end_of_frame` | Frame window `[618, 624]` (env `OGGF_S3K_CRL_RANGE`) **and** `Current_zone == 0x03`. Emits *"regardless of whether Touch_Process was hooked"* (4849). | `{"frame":%d,"vfc":%d,"event":"collision_response_list_end_of_frame","list_count":%d,"list_entries":[%s],"spring_children":[%s]}` |
| `cnz_event_ram` | `V622_CNZ_EVENT_RAM.enabled`, set **only** when `OGGF_S3K_CNZ_EVENT_RAM_RANGE` is non-empty. **Off by default** — absent from all fixtures. | 3319–3327. |
| `aiz_fire_transition` | `is_aiz_end_to_end_profile()` — **structurally unreachable** in this recorder. | 3399–3407. |

### 4.3 Hook-driven families

All registered together at 5816–5830 behind `if not LIGHTWEIGHT_REGEN`, i.e.
only when `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`. Every flush is additionally
gated on hook-set state (`#hits == 0` or `state.seen == false`), so with hooks
off **none of them can emit a byte**.

| Family | Hook kind | Hook site(s) | Default window |
|---|---|---|---|
| `velocity_write` | `event.onmemorywrite` ×4 | `0xFFB062/3/4/5` (Tails x_vel/y_vel) | `[3640,3660]`, `[7549,7560]` |
| `position_write` | `event.onmemorywrite` ×8 | `0xFFB010/1` + `0xFFB014/5` (Sonic x_pos/y_pos), `0xFFB05A/B` + `0xFFB05E/F` (Tails) | `[4788,4792]`, `[7549,7560]`, `[7600,7625]`, `[16320,16335]` |
| `solid_object_cont_entry` | `event.onmemoryexecute` | `0x1DF90` `SolidObject_cont` | `[4788,4792]`, `[7600,7625]` |
| `cage_execution` | `onmemoryexecute` ×5 | `sub_338C4` + 4 branches | none (frame-unbounded) |
| `cnz_cylinder_execution` | `onmemoryexecute` ×7 | `sub_324C0`, `MvSonicOnPtfm` … | `[4490,4512]` |
| `tails_cpu_normal_step` | `onmemoryexecute` ×4 | `loc_13DD0`, `loc_13EB8`, `loc_14A0A`, `loc_14B7A` | — |
| `aiz_boundary_state` | `onmemoryexecute` ×6 | tree/boundary entry/return/kill/clamp | `[4660,4679]`, `[7549,7560]` |
| `aiz_transition_floor_solid` | `onmemoryexecute` ×6 | `SolidObjectTop` branches | `[5408,5438]` |
| `aiz_ship_loop` | `onmemoryexecute` ×6 | `AIZ2_DoShipLoop` / `sub_50318` | env only |
| `sonic_record_pos` | `onmemoryexecute` | `Sonic_RecordPos` | — |
| `rng_call` | `onmemoryexecute` | `Random_Number` | `V625_RNG_CALLS.enabled` only when `OGGF_S3K_RNG_CALL_RANGE` set |
| `collision_response_list_per_frame` | `onmemoryexecute` | `Touch_Process` `0x10440` | `[618,624]` + zone 3 |
| `aiz_handoff_terrain_state` (enrichment only) | `onmemoryexecute` ×3 | `Sonic_CheckFloor` return, solid vertical/landing | window as §4.2 |

Verbatim templates for the three that **actually appear in a canonical
fixture** (see §5.1):

```
{"frame":%d,"vfc":%d,"event":"velocity_write","character":"tails","x_vel_writes":[%s],"y_vel_writes":[%s]}
{"frame":%d,"vfc":%d,"event":"position_write","character":"%s","x_pos_writes":[%s],"y_pos_writes":[%s]}
{"frame":%d,"vfc":%d,"event":"solid_object_cont_entry","entries":[%s]}
```

Element templates:

```
velocity_write hit : {"pc":"0x%05X","val":"0x%04X"}
position_write hit : {"pc":"0x%05X","val":"0x%04X","a1":"0x%08X","a0":"0x%08X"}
solid_object_cont_entry entry :
  {"pc":"0x%05X","a0":"0x%08X","a1":"0x%08X","d1":"0x%04X","d2":"0x%04X",
   "y_radius":"0x%02X","default_y_radius":"0x%02X","player_x":"0x%04X",
   "player_y":"0x%04X","player_status":"0x%02X","solid_x":"0x%04X","solid_y":"0x%04X"}
```

`position_write` emits `"sonic"` first then `"tails"`, each only when that
character had ≥1 write that frame. **`character` is a fixed base label, not a
character identity** — the Knuckles-alone `mgz_3` fixture contains
`"character":"sonic"` events.

> **Format quirk to preserve verbatim.** `%08X` applied to BizHawk's
> `emu.getregister("M68K A0")` produces a sign-extended 64-bit value in the
> committed fixtures, e.g. `"a0":"0xFFFFFFFFFFFFB000"` — *sixteen* hex digits,
> not eight. Any port that reproduces these bytes must emit the same
> sign-extended form.

### 4.4 `s3k_special_stage` emits **no** aux events at all

`write_ss_row` (5174) writes the CSV row and returns; the `on_frame_end`
special-stage branch `return`s at 5365 before reaching any aux call. The aux
file **is** opened (5155) and **is** committed, but it is a zero-byte file.

Measured: `special_stage/aux_state.jsonl` = 0 lines, and all three of
`runs/s3-knux-multibonus-ss/{ss,ss_2,ss_3}/aux_state.jsonl` = 0 lines. The
`.gz` for `special_stage` is 20 bytes (empty-member gzip).

---

## 5. Family × fixture occurrence table

Counts are exact `"event":"<name>"` occurrences in the gunzipped
`aux_state.jsonl` (CR-stripped for the (B) column). `·` = zero.

### 5.1 Required set

| Family | (A) `aiz_completerun` | (A) `lbz_completerun` | (C) `bonus_gumball` | (C) `special_stage` | (B) `aiz` | (B) `hcz_2` | (B) `mgz_3` |
|---|--:|--:|--:|--:|--:|--:|--:|
| rows in `physics.csv` | 26228 | 46244 | 1430 | 4630 | 4654 | 11933 | 8517 |
| `object_state` | 333312 | 464915 | 18229 | · | 29763 | 111321 | 51646 |
| `object_near` | 277783 | 394316 | 18229 | · | 29763 | 111321 | 51646 |
| `air_countdown_state` | 52456 | 92488 | 2860 | · | 9308 | 23866 | 17034 |
| `interact_state` | 52336 | 92356 | 1430 | · | 4654 | 11933 | 8517 |
| `oscillation_state` | 26228 | 46244 | 1430 | · | 4654 | 11933 | 8517 |
| `game_paused_state` | 26228 | 46244 | 1430 | · | 4654 | 11933 | 8517 |
| `cpu_state` | 26228 | 46244 | 1430 | · | 4654 | 11933 | 8517 |
| `sidekick_interact_object` | 26108 | 46112 | · | · | · | · | · |
| `object_appeared` | 4551 | 7323 | 182 | · | 565 | 1880 | 1009 |
| `control_lock_state` | 3273 | 5256 | 100 | · | 517 | 812 | 703 |
| `object_removed` | 3051 | 4745 | 77 | · | 410 | 1301 | 657 |
| `slot_dump` | 2174 | 3240 | 76 | · | 186 | 923 | 568 |
| `state_snapshot` | 797 | 1441 | 26 | · | 154 | 313 | 279 |
| `mode_change` | 747 | 1422 | 2 | · | 180 | 256 | 296 |
| `routine_change` | 14 | 46 | 2 | · | 3 | 7 | 11 |
| `terrain_wall_sensor` | 12 | · | · | · | · | · | · |
| `aiz_handoff_terrain_state` | 9 | · | · | · | · | · | · |
| `zone_act_state` | 4 | 4 | 2 | · | 2 | 3 | 3 |
| `player_mode_set` | 1 | 1 | 1 | · | 1 | 1 | 1 |
| `cpu_state_snapshot` | 1 | 1 | 1 | · | 1 | 1 | 1 |
| **`position_write`** *(hook)* | · | · | · | · | · | **43** | **38** |
| **`velocity_write`** *(hook)* | · | · | · | · | · | **21** | · |
| **`solid_object_cont_entry`** *(hook)* | · | · | · | · | · | **31** | **31** |

### 5.2 Remaining (A) and (C) fixtures

| Family | `hcz_cr` | `mgz_cr` | `cnz_cr` | `icz_cr` | `mhz_cr` | `bonus_slots` | `bonus_pachinko` |
|---|--:|--:|--:|--:|--:|--:|--:|
| rows | 31482 | 39398 | 40064 | 25393 | 28156 | 1200 | 3051 |
| `object_state` | 394205 | 343310 | 349755 | 338502 | 352133 | 875 | 15703 |
| `object_near` | 342381 | 287506 | 269194 | 289285 | 305700 | 875 | 15703 |
| `air_countdown_state` | 62964 | 78796 | 80128 | 50786 | 56312 | 2400 | 6102 |
| `interact_state` | 62840 | 78670 | 80002 | 50670 | 56170 | 1200 | 3051 |
| `oscillation_state` / `game_paused_state` / `cpu_state` | 31482 | 39398 | 40064 | 25393 | 28156 | 1200 | 3051 |
| `sidekick_interact_object` | 31358 | 39272 | 39938 | 25277 | 28014 | · | · |
| `object_appeared` | 4580 | 3982 | 3135 | 7329 | 4879 | 20 | 614 |
| `control_lock_state` | 3690 | 5035 | 4415 | 3196 | 3206 | 66 | 293 |
| `object_removed` | 3319 | 2869 | 2482 | 4506 | 3105 | 15 | 569 |
| `slot_dump` | 2388 | 1898 | 1619 | 3954 | 2644 | 13 | 435 |
| `state_snapshot` | 845 | 1248 | 1241 | 819 | 830 | 43 | 67 |
| `mode_change` | 728 | 1256 | 1158 | 876 | 754 | 26 | 32 |
| `routine_change` | 10 | 25 | 11 | 16 | 6 | 3 | 3 |
| `cage_state` | · | · | **8030** | · | · | · | · |
| `cnz_cylinder_state` | · | · | **23** | · | · | · | · |
| `collision_response_list_end_of_frame` | · | · | **7** | · | · | · | · |
| `object_state_snapshot` | · | · | **4** | · | · | · | · |
| `zone_act_state` | 4 | 4 | 4 | 4 | 4 | 2 | 2 |
| `player_mode_set` / `cpu_state_snapshot` | 1 | 1 | 1 | 1 | 1 | 1 | 1 |

Window arithmetic checks out exactly: `terrain_wall_sensor` = 12 =
`|[7549,7560]|`; `aiz_handoff_terrain_state` = 9 = `|[5430,5438]|`;
`cnz_cylinder_state` = 23 = `|[4490,4512]|`; `collision_response_list_end_of_frame`
= 7 = `|[618,624]|`.

### 5.3 All 25 (B) segments — hook families only

| Segment | `position_write` | `velocity_write` | `solid_object_cont_entry` |
|---|--:|--:|--:|
| `aiz`, `aiz_2`, `aiz_4`, `aiz_5` | · | · | · |
| `aiz_3` | · | · | · (has `aiz_handoff_terrain_state` = 9) |
| `hcz`, `hcz_3`, `hcz_4`, `hcz_5` | · | · | · |
| **`hcz_2`** | **43** | **21** | **31** |
| **`hcz_6`** | **17** | · | **31** |
| **`mgz`** | **43** | · | **26** |
| `mgz_2` | · | · | · |
| **`mgz_3`** | **38** | · | **31** |
| `gumball`, `gumball_2`, `pachinko`, `slots`, `slots_2..5` | · | · | · |
| `ss`, `ss_2`, `ss_3` | (empty aux) | | |

---

## 6. LANDMINE — hook-driven families **do** appear; exec/memwrite callbacks are required

Task 7 (STANDARD recorder) proved hook absence for its three fixtures and
deferred the `GpgxHost` exec/memory-write callback surface behind
`S3KHookAbsenceTests`. **That result does not carry over.**

Four of the twenty-five (B) segments — `hcz_2`, `hcz_6`, `mgz`, `mgz_3` —
contain **hook-driven** aux families:

* `position_write` — `event.onmemorywrite` on 8 fixed RAM byte addresses.
* `velocity_write` — `event.onmemorywrite` on 4 fixed RAM byte addresses.
* `solid_object_cont_entry` — `event.onmemoryexecute` at PC `0x1DF90`.

This is unambiguous: the (B) run was captured with
`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1` (its `metadata.json` files carry **no**
`capture_mode` key, which `write_metadata` writes iff `LIGHTWEIGHT_REGEN`,
line 1375–1377). Consequences for the port:

1. Byte-reproducing the (B) run requires wiring LibGPGX's exec **and**
   memory-write callback surface into `GpgxHost` (study how EmuHawk wires
   `SetMemCallbacks`). Under Mono, the classic failure is a collected delegate
   → interop crash; the delegates must be GC-pinned for the process lifetime.
2. Each hit needs `emu.getregister("M68K PC"/"A0"/"A1"/"D1"/"D2"/"A7")`
   equivalents at callback time, and the fixtures pin the sign-extended
   64-bit register rendering (§4.3).
3. §7.2 shows the (B) run is **not byte-reproducible from the current Lua
   anyway**. A pragmatic split is therefore available and recommended:
   * treat (A) + (C) — hooks **off**, `capture_mode` present — as the
     byte-exact differential gate, extending the existing `S3KHookAbsenceTests`
     pinning to all eleven of those fixtures; and
   * treat (B) as a **shape/manifest** fixture whose hook families are
     asserted present-and-parseable, with byte-exactness explicitly out of
     scope and the reason recorded (`ADDR_FRAMECOUNT` drift, §7.2).

   Whichever split is chosen, it must be stated in the port's test docs, not
   left implicit — the absence-pinning test must not silently widen to cover
   fixtures that actually contain hook events.

---

## 7. Fixture provenance — version and address drift (must be pinned, not normalized)

### 7.1 The 6.31 → 6.32 metadata delta is exactly two lines

Pinned from `git show 9e3ccdb41` (`fix(s3k): recorder captures V_int_run_count
for bonus segments, re-capture s3-knux-multibonus-ss`, 2026-07-20). The whole
Lua diff:

```
-LUA_SCRIPT_VERSION = "6.31-s3k-completerun"
+LUA_SCRIPT_VERSION = "6.32-s3k-completerun"
+ADDR_V_INT_RUN_COUNT = 0xFE0C
+start_v_int_run_count = nil
+        if start_v_int_run_count ~= nil then
+            meta_file:write('  "v_int_run_count": ' .. start_v_int_run_count .. ',\n')
+        end
+    start_v_int_run_count = current_segment_is_bonus
+        and mainmemory.read_u32_be(ADDR_V_INT_RUN_COUNT) or nil
```

So the *only* observable 6.31→6.32 differences are:

* the `lua_script_version` string, and
* a `"v_int_run_count": <decimal>` line inserted **immediately after**
  `"bonus_stage_type"` in the bonus branch of `write_metadata`.

`physics.csv` and `aux_state.jsonl` are untouched by the bump. Assert both as
**exact literals per fixture** — never a loose regex, never a blanket
normalization.

The same commit hand-updated only the **bonus** segments' `metadata.json`
inside `runs/s3-knux-multibonus-ss/` — `git show --name-only 9e3ccdb41`
lists exactly those 8 files plus the 3 standalone `bonus_*`
`metadata.json` and this Lua, and **no** `.gz` payload. Despite the
commit subject saying "re-capture", nothing was re-captured: the per-file
diff is the three lines above. That is precisely why the one run
directory carries mixed stamps — level and `ss` segments **and**
`run_manifest.json` say `6.31-s3k-completerun`; `gumball`, `gumball_2`,
`pachinko`, `slots`, `slots_2..5` say `6.32-s3k-completerun` — while all
25 dirs' physics/aux bytes remain homogeneous 6.31/`0xFE08` output
(`pre_trace_osc_frames: 0` and all-zero `vfc` in the bonus dirs too).
No recorder configuration emits this combination, so the mixed stamping
was never a port target; see `s3k-complete-run-behavior.md` §0.2 / §8.3.

**Superseded, kept as history:** commit `63eccd290` re-captured the whole
`runs/s3-knux-multibonus-ss/` tree on Linux with the hooks off, and
`eb87d681b` regenerated it again for the `ADDR_VBLA_WORD` fix, so all 25
dirs and the manifest now stamp `6.33-s3k-completerun` uniformly, are LF,
carry `capture_mode` and `pre_trace_osc_frames: 1`, and carry the live
`0xFE04` / `0xFE0E` counters. The paragraph above explains why the mixed
stamp existed and must not be reintroduced — it no longer describes the
tree in git.

### 7.2 Why `special_stage/` carries neither `capture_mode` nor `v_int_run_count`

Not a version effect — a **different writer**. `write_ss_metadata` (5103) is a
separate function that emits a disjoint field list and never calls either
code path:

* `capture_mode` is written only by `write_metadata` at 1375–1377.
* `v_int_run_count` is written only by `write_metadata` at 1434–1436, and
  only when `start_v_int_run_count ~= nil`, which `start_new_segment` sets
  only when `current_segment_is_bonus` (5051–5052).

`start_ss_segment` never touches `start_v_int_run_count`. Hence:

| Field | level seg | bonus seg | SS seg |
|---|---|---|---|
| `capture_mode` (hooks off only) | yes | yes | **never** |
| `v_int_run_count` | **never** | yes | **never** |
| `pre_trace_osc_frames` / `rng_seed` / `aux_schema_extras` / `notes` | yes | yes | **never** |
| `special_stage_index` / `ss_csv_version` / `fresh_load` | never | never | yes |

Measured `v_int_run_count` values, identical in (B) and (C) for the same
segment: `gumball` 5529 (`bk2_frame_offset` 5570), `slots` 9097 (9142),
`pachinko` 92662 (92963). Emitted as **decimal**, from a `read_u32_be` of
`0xFE0C` sampled once at segment-arm time. It tracks but does not equal the
BizHawk frame count — `V_int_run_count` counts V-ints, which do not advance on
every emulated frame — so assert the literal per fixture, never a derived
relation.

### 7.3 `ADDR_FRAMECOUNT` moved after the (B) capture — (B) is NOT reproducible

`git show 6564667eb` (`fix(tools): S3K recorder frame-counter address
0xFE08 -> 0xFE04`, 2026-07-21):

```
-local ADDR_FRAMECOUNT       = 0xFE08
+local ADDR_FRAMECOUNT       = 0xFE04  -- Level_frame_counter (was 0xFE08 = Debug_placement_mode, dead-zero since inception; matches S1/S2 recorders)
```

`0xFE08` is `Debug_placement_mode`, always zero in normal gameplay. Measured
consequences:

| | (B) `runs/s3-knux-multibonus-ss/*` | (A) + (C) |
|---|---|---|
| `physics.csv` `gameplay_frame_counter` | **all `0000`** (1 distinct value in `aiz`) | live (1026 distinct in `bonus_slots`) |
| aux `vfc` | **all `0`** (1 distinct value) | live |
| aux `oscillation_state.level_frame_counter` | all `0` | 26040 distinct in `aiz_completerun` |
| `metadata.json` `pre_trace_osc_frames` | `0` | `1` |

The (A) and (C) sets were regenerated together by `192d9c976`
(`fix(trace): regenerate consistent S3K v7 fixtures`, 2026-07-23) on Linux
with hooks off. So the three fixture sets are:

| Set | Captured | Host / newline | Hooks | `run_id` | `ADDR_FRAMECOUNT` |
|---|---|---|---|---|---|
| **(A)** 7 × `*_completerun` | 2026-07-23 (`192d9c976`) | LF | off (`capture_mode` present) | none | `0xFE04` |
| **(B)** `runs/s3-knux-multibonus-ss/` (25 segs + manifest) | 2026-07-19 (`76bdfc0f2`); 8 bonus `metadata.json` restamped 2026-07-20 (`9e3ccdb41`) | CRLF | **on** | `s3-knux-multibonus-ss` | `0xFE08` |
| **(C)** `bonus_gumball`, `bonus_slots`, `bonus_pachinko`, `special_stage` | 2026-07-23 (`192d9c976`) | LF | off (bonus dirs); SS writer emits neither key | `s3k-multibonus` | `0xFE04` |

**Do not "fix" this by loosening a comparison.** Model it: the differential
gate targets (A)+(C) byte-exactly; (B) is a legacy-address, hooks-on capture
and must be gated on shape/manifest invariants with the divergence recorded
here, or re-captured (which would rewrite committed fixtures and is out of
scope for the port).

---

## 8. Delegate vs. new relative to the ported `S3KAuxEventEngine`

`src/Recording/S3KAuxEventEngine.cs` (1579 lines) already implements **25**
families. Measured by scanning its emitted `"event":"…"` literals:

**Already implemented — DELEGATE, do not reimplement:**
`air_countdown_state`, `aiz_fire_transition`, `aiz_handoff_terrain_state`,
`cage_state`, `checkpoint`, `cnz_cylinder_state`,
`collision_response_list_end_of_frame`, `control_lock_state`, `cpu_state`,
`cpu_state_snapshot`, `interact_state`, `mode_change`, `object_appeared`,
`object_near`, `object_removed`, `object_state`, `object_state_snapshot`,
`oscillation_state`, `player_mode_set`, `routine_change`,
`sidekick_interact_object`, `slot_dump`, `state_snapshot`,
`terrain_wall_sensor`, `zone_act_state`.

**Corrections to the task's candidate list:** `object_appeared`,
`object_removed`, and `player_mode_set` are **already implemented** — three of
the four candidates are false positives. Reuse them unchanged.

**Genuinely new to the complete-run recorder — exactly one family:**

| Family | Why new | Evidence |
|---|--:|---|
| `game_paused_state` | Introduced by the complete-run recorder to make the accidental HCZ pause window visible (header comment lines 26–28). `grep -c game_paused s3k_trace_recorder.lua` = **0**. Absent from `S3KAuxEventEngine`. | Present in **every** level/bonus fixture at exactly 1/frame. |

It is a two-value, always-on, frame-polled emitter:

```
{"frame":%d,"vfc":%d,"event":"game_paused_state","game_paused":%d}
```

reading `Game_paused` as a **word** at `0xF63A` (the ROM writes
`move.w #1,(Game_paused).w`; a byte read of the high half would always be 0).
Emission slot is #11 in §3, between `oscillation_state` and `object_state`.
Recording is **never** altered while paused — frozen frames are recorded
verbatim; the flag is comparison-only.

**Also new but not an aux family:** the entire `s3k_special_stage` writer
pair (`write_ss_row` / `write_ss_metadata`) and the SS detour state machine.
The standard recorder has no `ss_csv_version`, no 20-column schema, and no
`$34` detour handling.

**Deferred hook families with no `S3KAuxEventEngine` counterpart:**
`velocity_write`, `position_write`, `solid_object_cont_entry`,
`cage_execution`, `cnz_cylinder_execution`, `tails_cpu_normal_step`,
`aiz_boundary_state`, `aiz_transition_floor_solid`, `aiz_ship_loop`,
`sonic_record_pos`, `rng_call`, `collision_response_list_per_frame`,
`cnz_event_ram`. Of these, the first three **do occur** in canonical
fixtures — see §6.

---

## 9. Environment surface

The existing S3K CLI (`Program.cs` `RejectUnmodeledS3kEnvironment`, plus
`UnmodeledS3kEnvironmentVariables`) refuses 11 variables. The complete-run
recorder's own surface adds the following; the refusal table must be extended
for every output-affecting variable the port does not model, and a test must
pin which variables are deliberately **not** refused so the guard cannot
degrade into a blanket `OGGF_*` ban.

| Variable | Line | Output-affecting? | Disposition for the port |
|---|--:|---|---|
| `OGGF_TRACE_OUTPUT_DIR` | 332 | yes (base dir) | **Model** — maps to the output-directory argument. |
| `OGGF_TRACE_RUN_ID` | 907 | yes (`run_id` in every `metadata.json` + `run_manifest.json`; also forces manifest emission even with zero transitions, 1459) | **Model** — maps to `--run-id`. |
| `OGGF_BK2_BASENAME` | 360 | **yes** — sets `SOURCE_BK2_NAME`, written to `source_bk2` in every `metadata.json` **and** in `run_manifest.json` | **Model.** Default is `"s3k-complete-sonic-tails.bk2"`; the (B)/(C) captures used `s3-knux-multibonus-ss.bk2`. Deriving it from the movie filename (the S2 approach) reproduces both canonical values. |
| `OGGF_TRACE_STOP_FRAME` | 342 | yes (truncates) | Already refused. |
| `OGGF_BK2_FRAME_COUNT` | 343 | yes (truncates; also enables the post-movie tail at 5507–5509) | Already refused. |
| `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` | 347 | yes (arms §4.3; removes `capture_mode`) | Already refused when `=1`. **Re-evaluate** if exec callbacks land (§6). |
| `OGGF_TRACE_QUIET` | 350 | no (stdout only) | Do **not** refuse; pin as deliberately allowed. |
| `OGGF_BIZHAWK_LIB` | 307 | no (Lua module path) | N/A to the native port; pin as deliberately allowed. |
| `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | 1418, 3253 | yes (arms `cnz_event_ram` **and** appends it to `aux_schema_extras`) | Already refused. |
| `OGGF_S3K_RNG_CALL_RANGE` | 786 | yes (arms `rng_call` + `aux_schema_extras`) | Already refused. |
| `OGGF_S3K_AIZ_FIRE_RANGE` | 3365 | **no** in this recorder — `V628_AIZ_FIRE.write` returns unless `is_aiz_end_to_end_profile()`, unreachable when `TRACE_PROFILE == "complete_run"` | Currently refused (correct for the standard recorder). Keep refusing for symmetry, but record here that it is inert on the complete-run path. |
| `OGGF_S3K_AIZ_WALL_SENSOR_RANGE` | 4391 | yes (retunes polled `terrain_wall_sensor`) | Already refused. |
| `OGGF_S3K_CRL_RANGE` | 4665 | yes (retunes polled `collision_response_list_end_of_frame`) | Already refused. |
| `OGGF_S3K_CNZ_CYLINDER_RANGE` | 3070 | yes (retunes polled `cnz_cylinder_state`) | Already refused. |
| `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START` / `_END` | 4103/4105 | yes (retunes polled `aiz_handoff_terrain_state`) | Already refused. |
| `OGGF_S3K_VELOCITY_WRITE_RANGE` | 2426 | only with hooks on | Not refused — flush is hook-state gated. Pin as deliberately allowed. |
| `OGGF_S3K_POSITION_WRITE_RANGE` | 2550 | only with hooks on | Not refused; pin. |
| `OGGF_S3K_SOLID_CONT_RANGE` | 4457 | only with hooks on | Not refused; pin. |
| `OGGF_S3K_AIZ_BOUNDARY_RANGE` | 3618 | only with hooks on | Not refused; pin. |
| `OGGF_S3K_AIZ_BOUNDARY_FRAME_START` / `_END` | 815/816 | only with hooks on | Not refused; pin. |
| `OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START` / `_END` | 845/847 | only with hooks on | Not refused; pin. |
| `OGGF_S3K_AIZ_SHIP_LOOP_RANGE` | 2688 | only with hooks on | Not refused; pin. |

**No `OGGF_S3K_TRACE_PROFILE` equivalent exists here** — `TRACE_PROFILE` is a
hard-coded local (341). A port that exposes a `--trace-profile` switch on the
complete-run path would diverge from the Lua.

---

## 10. Porting checklist

1. Evaluate the SS detour → per-zone arm → level-family guard chain in
   `on_frame_end` **source order, post-advance**. Do not reorder.
2. Arm-and-return: the arm frame is not row 0.
3. Three metadata writers, not one: level/bonus (`write_metadata`) and SS
   (`write_ss_metadata`) share **no** fields beyond `game`, `characters`,
   `bk2_frame_offset`, `trace_frame_count`, `source_bk2`,
   `lua_script_version`, `recording_date`, `bizhawk_version`,
   `genesis_core`, `rom_checksum`, `run_id`, `segment_index`.
4. `aux_schema_extras` for `TRACE_PROFILE == "complete_run"` is a fixed
   19-element list (12 base + 7 complete-run additions, 1378–1402); the
   `cnz_event_ram_per_frame` / `rng_call_per_frame` conditionals live in the
   `else` branch and are unreachable here.
5. SS segments write an **empty** `aux_state.jsonl`; keep the file.
6. Newline convention is a per-fixture property (§2.3).
7. Buffer only discard-capable profiles — reuse task 7's `TraceStreamSink`.
   The complete movie is ~238k input rows across seven segments; peak RSS is
   the S2 complete-emeralds failure mode.
8. Extend, don't widen, the hook-absence pinning test (§6).

---

## 11. Port status — Stage B (profile + aux surface)

Stage B lands the row/aux surface only. It writes no files, opens no
segments and adds no CLI; segmentation is Stage A
(`S3KCompleteRunSegmenter`) and the writer/publication layer is Stage C.

### 11.1 Seams introduced, and why each is a seam rather than a fork

| Seam | Where | Why |
|---|---|---|
| `S3KTraceProfile.CompleteRun` | `S3KAuxEventEngine` | The complete-run recorder is a *different recorder*, not a different `OGGF_S3K_TRACE_PROFILE` value. The enum already encodes recorder identity for the standard recorder's three profiles, and every existing profile gate (`checkpoint` vocabulary, `aiz_fire_transition`) is already written as "only for profile X", so the new value falls through them correctly with no edits — matching the Lua, where both `is_*_profile()` predicates are false. |
| ~~`S3KAuxEventEngine.FrameCounterAddressFor(profile)` + the explicit-address constructor~~ **DELETED** | `S3KAuxEventEngine` | `ADDR_FRAMECOUNT` used to be `0xFE08` in the standard recorder and `0xFE04` in the complete-run recorder (§7.3), so the address was selected from the profile (= recorder identity), with an explicit-address constructor so the legacy `0xFE08`-era (B) captures could be pinned without a second class. Both halves are gone. `s3k_trace_recorder.lua` v6.31-s3k moved the standard recorder to `0xFE04` and its three canonical fixtures were regenerated, and the legacy (B) captures were themselves regenerated on `0xFE04` (commit `63eccd290`). The engine now reads `S3KRam.LevelFrameCounter` unconditionally and exposes ONE constructor. |
| ~~`S3KTraceCsvWriter.FormatRow(frame, input, host, frameCounterAddress)`~~ **DELETED** | `S3KTraceCsvWriter` | The same fork, in the one CSV column that differed. With both recorders on `0xFE04` the 3-argument overload reads `S3KRam.LevelFrameCounter` and the 4-argument overload is removed. |
| `emitsGamePausedState` | `S3KAuxEventEngine` | `game_paused_state` is the ONE aux family the complete-run recorder adds (§8) — verified by diffing the two Lua scripts' `"event":"…"` literals, which differ by exactly that one line, and by diffing all 26 shared writer bodies, which are character-identical apart from an inert `bk2_input_mask` default argument. Emitted in cascade slot #11, between `oscillation_state` and the first `object_state`. |

`S3KSpecialStageCsvWriter` is genuinely new (nothing to delegate to): a
20-column writer with the opposite numeric convention and no aux
counterpart.

Everything else is delegated unchanged. In particular `object_appeared`,
`object_removed` and `player_mode_set` were already implemented for the
standard recorder and are reused verbatim.

### 11.2 Hook decision — option §6.3, made explicit

The pragmatic split recommended in §6 is the one taken:

* **(A) + (C) — the eleven hooks-off fixtures — are the byte-exact
  target.** Measured: zero occurrences of any of the 14 hook/env-armed
  families across all eleven aux streams. `S3KHookAbsenceTests` is
  extended to all eleven with per-fixture non-vacuous anchors
  (`cpu_state`, `oscillation_state` and the new `game_paused_state` each
  exactly once per row; exactly one `cpu_state_snapshot`; the AIZ
  `aiz_handoff_terrain_state` skeleton count; the profile's own
  `physics.csv` header).
* **(B) `runs/s3-knux-multibonus-ss/` is shape-only**, and is pinned in
  the OPPOSITE direction by
  `HookBearingRunSegmentsStillCarryHookEvents`: `hcz_2`, `hcz_6`, `mgz`
  and `mgz_3` must keep their exact `position_write` /
  `velocity_write` / `solid_object_cont_entry` counts, and must keep
  having no `capture_mode` key. That gate exists so the absence gate can
  never be quietly widened over fixtures whose reproduction really would
  require the exec/memwrite callback surface.

**No `GpgxHost` exec/memwrite callback surface is implemented.** It stays
deferred exactly as task 7 left it. The reason is stronger here than
there: (B) is not byte-reproducible from the current Lua at all (§7.3
`ADDR_FRAMECOUNT` drift), so implementing callbacks would not make any
committed fixture reproducible. If a future capture of (A)/(C) turns up
hook events, the extended absence gate fails and that decision must be
revisited.

### 11.3 Still out of scope after Stage B

`write_metadata` / `write_ss_metadata` for complete-run,
`run_manifest.json` emission (including the `bonus_stage_type` field the
shared `RunManifestWriter` does not yet emit), the CLI subcommand and its
env-var refusal-table extension, publication via `NoReplacePublisher`,
the per-fixture newline convention (§2.3), and the ROM-backed
differential gate.

## 12. Port status — Stage C landed (migration complete)

Every gap listed in §11.3 has since been closed by
`S3KCompleteRunSegmenter` (segmentation), `S3KRunManifestWriter` +
`RunManifestWriter` (`run_manifest.json`, incl. `bonus_stage_type`), the
`--trace-profile complete_run` / `--run-id` CLI branch in `Program.cs`
and its dedicated env-var refusal table
(`RejectUnmodeledS3kCompleteRunEnvironment`), `NoReplacePublisher`
publication via `S3KStagedSegmentSink`, and three ROM-backed differential
gates — see `s3k-run-publication.md` §10 for the full "as built" class
map and gate coverage, and `tools/bizhawk/README.md`'s "Sonic 3 & Knuckles
complete-run and run mode" section for the verified capture commands and
final byte-parity results. §11.2's hook decision stands unchanged: no
`GpgxHost` exec/memwrite callback surface was added, since neither the
byte-exact identity-(A)/(C) targets nor the structurally-gated legacy
identity-(B) fixture would become reproducible by adding it.
