# S3K Standard Recorder — Authoritative AUX Event Spec

Source of truth: `tools/bizhawk/s3k_trace_recorder.lua` (metadata stamp
`6.30-s3k` at HEAD, `trace_schema` 6, `csv_version` 7) plus
`tools/bizhawk/lib/oggf_trace_common.lua`. This document is a byte-level
transcription of the aux surface for the native (C#) port. **The Lua is the
behavioral authority**; where this document and the Lua disagree, the Lua wins
and this document must be fixed.

Scope: the STANDARD recorder only. `s3k_complete_run_recorder.lua`
(`6.32-s3k-completerun`) is a separate later migration and is not covered here.

## 0. Emission mechanics

- Every aux event is one JSON object per line in `aux_state.jsonl`, written via
  `C.write_aux(aux_file, json_str)` which appends `json_str .. "\n"` and
  flushes after **every** line.
- All templates below are the exact Lua `string.format` format strings with the
  `..` concatenations joined. Reproduce them **byte-for-byte** — key order,
  hex-case (`%04X` upper), `0x` prefixes, booleans as bare `true`/`false`,
  decimal vs hex per field.
- `frame` is the recorder's `trace_frame` (0-based recorded row index; `-1` for
  the two pre-trace snapshot families). `vfc` is `mainmemory.read_u16_be(0xFE04)`
  (skdisasm `Level_frame_counter`; see core spec §1.1 — live, and equal to the
  same frame's CSV `gameplay_frame_counter`), read fresh at each emission point.
  Before v6.31-s3k this read was `0xFE08` (`Debug_placement_mode`, dead-zero), so
  every pre-v6.31 fixture shows a constant `"vfc":0`; the three canonical
  fixtures were regenerated on `0xFE04`. Two families
  (`zone_act_state`, `checkpoint`) have **no** `vfc` field.
- `json_int_or_null(v)` renders `null` when nil, else `tostring(v)` (in
  practice always an integer here).
- `json_quote(v)` (S3K form) wraps in double quotes and escapes `\` then `"`.
- Reads happen in `on_frame_end` **after** `emu.frameadvance()`, i.e. state is
  the ROM end-of-frame instant for the recorded row.

### Recorder-level gating knobs

| Knob | Effect |
|---|---|
| `OGGF_S3K_TRACE_PROFILE` | `gameplay_unlock` (default), `aiz_end_to_end`, `level_gated_reset_aware`. Selects start/stop rules, checkpoint set, and the profile-conditional halves of `aux_schema_extras`. |
| `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1` | Enables `DIAGNOSTIC_HOOKS_ENABLED` (i.e. turns OFF `LIGHTWEIGHT_REGEN`). Only then are the `event.onmemoryexecute` / `event.onmemorywrite` hooks registered (single registration block at script load). With it unset (the fixture-regeneration default) **no hook-driven event can ever be emitted** and metadata gains `"capture_mode": "physics_animation_aux_without_diagnostic_hooks"`. |
| `OGGF_S3K_RNG_CALL_RANGE` | Additionally required (non-empty) for `rng_call` — the hook itself is only registered when set AND diagnostic hooks are on. Also appends `rng_call_per_frame` to `aux_schema_extras` (CNZ start-zone only). |
| `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | Enables the (frame-polled) `cnz_event_ram` family; unset ⇒ disabled entirely. Also appends `cnz_event_ram_per_frame` to `aux_schema_extras` (CNZ start-zone only). |
| Other `OGGF_S3K_*_RANGE` / `*_FRAME_START/END` vars | Override the per-family default frame windows listed below; they never enable a family that is otherwise off. |

**Fixture-relevant fact (verified against all three gated fixtures):** every
fixture was captured in lightweight mode (`capture_mode` present in each
`metadata.json`); zero hook-driven events appear in any fixture aux stream.

## 1. Pre-trace one-shots (before the first physics row)

Emitted exactly once, at the top of the first *recorded* frame (guarded by
`pre_trace_snapshots_written`), before that frame's physics row side effects —
they are the first lines of `aux_state.jsonl`.

### 1.1 `cpu_state_snapshot` — frame-polled, always, character `tails`

```
{"frame":-1,"vfc":%d,"event":"cpu_state_snapshot","character":"tails","control_counter":%d,"respawn_counter":%d,"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X","interact_id":"0x%02X","jumping":%d}
```

Fields read from the Tails CPU global block (`$F702/$F704/$F708/$F70A/$F70C/$F70E/$F70F`).

### 1.2 `object_state_snapshot` — frame-polled, always (object-gated), no character

One event per OST slot in `3..109` whose 32-bit object code equals
`OBJ_CNZ_BALLOON = 0x00031754` (the only code mapped by
`snapshot_object_id_for_code`; `object_type` is then `0x41`):

```
{"frame":-1,"vfc":%d,"event":"object_state_snapshot","slot":%d,"object_type":"0x%02X","object_code":"0x%08X","fields":%s}
```

`fields` is `build_object_fields(addr)`: a JSON object of all 0x4A raw slot
bytes `"off_%02X":"0x%02X"` (off_00..off_49, in offset order) followed by:

```
"x_pos":"0x%04X","x_sub":"0x%04X","y_pos":"0x%04X","y_sub":"0x%04X","x_vel":"0x%04X","y_vel":"0x%04X","render_flags":"0x%02X","height_pixels":"0x%02X","width_pixels":"0x%02X","status":"0x%02X","routine":"0x%02X","mapping_frame":"0x%02X","anim":"0x%02X","anim_frame":"0x%02X","anim_frame_timer":"0x%02X","angle":"0x%02X","subtype":"0x%02X","collision_flags":"0x%02X","collision_property":"0x%02X"
```

(`x_vel`/`y_vel` are read signed and wrapped `+0x10000` if negative.)

## 2. Per-frame emission order

For every recorded frame, in exactly this order (a family that has nothing to
say this frame emits nothing but its position in the order is fixed):

1. *(first recorded frame only)* `cpu_state_snapshot`, then `object_state_snapshot` × k (slot order)
2. physics.csv row (not aux)
3. `zone_act_state` (on change of the zone/act/apparent-act/game-mode tuple)
4. `checkpoint` × k (profile-conditional one-shots; see §3.2)
5. `player_mode_set` (on change / first frame)
6. `mode_change field=air` → *(if it fired)* `state_snapshot`
7. `mode_change field=rolling`
8. `mode_change field=on_object`
9. `mode_change field=control_locked`
10. `routine_change` → *(if new routine is hurt `0x04` or death `0x06`)* `state_snapshot`
11. `cpu_state`
12. `tails_cpu_normal_step` (hook flush)
13. `aiz_boundary_state` (hook flush)
14. `aiz_transition_floor_solid` (hook flush)
15. `aiz_handoff_terrain_state` (windowed poll; hook fields default false/0)
16. `aiz_fire_transition` (windowed poll)
17. `oscillation_state`
18. `object_state` × k (slot order 1..109, proximity-gated)
19. `interact_state` character `sonic`, then character `tails` (if sidekick present)
20. `sidekick_interact_object` (if sidekick present)
21. `air_countdown_state` owner `p1`, then owner `p2`
22. `cnz_cylinder_state` × k (windowed poll, slot order 0..109)
23. `cnz_cylinder_execution` (hook flush)
24. `cnz_event_ram` (env-enabled windowed poll)
25. `cage_state` × k (slot order 0..109, object-gated)
26. `cage_execution` (hook flush)
27. `velocity_write` (hook flush, character `tails`)
28. `position_write` character `sonic`, then character `tails` (hook flush)
29. `aiz_ship_loop` (hook flush)
30. `sonic_record_pos` (hook flush)
31. `rng_call` (hook flush)
32. `solid_object_cont_entry` (hook flush)
33. `state_snapshot` (every frame where `trace_frame % 60 == 0`)
34. `control_lock_state` (on change; forced baseline when `trace_frame % 60 == 0`)
35. `terrain_wall_sensor` (windowed poll)
36. `collision_response_list_per_frame` × k (hook walks), then `collision_response_list_end_of_frame` (windowed poll)
37. `scan_objects` slots 1..109 in order — per slot: `object_appeared` (code changed to non-zero) / `object_removed` (code changed to zero) / `object_near` (non-zero + within 160px of P1) — then `slot_dump` once if any slot appeared this frame

Finalisation (`level_gated_reset_aware` only): one `checkpoint` named
`gameplay_end`, with `frame` = final `trace_frame` (one past the last recorded
row), emitted before files close.

Note `state_snapshot` has three trigger sites (after an `air` mode_change, on a
hurt/death routine_change, and the every-60-frames baseline) — all share one
template.

## 3. Event catalog

Legend per family: **Trigger** (frame-polled / hook-driven / hybrid),
**Gating**, **Scope** (character coverage). "Always" means every recorded
frame with no window/zone/env gate.

### 3.1 `zone_act_state` — poll, on change, no `vfc`, no character

Emitted when the tuple `(actual_zone_id, actual_act, apparent_act, game_mode)`
differs from the previously emitted key (first frame emits a baseline).

```
{"frame":%d,"event":"zone_act_state","actual_zone_id":%s,"actual_act":%s,"apparent_act":%s,"game_mode":%s}
```

(args through `json_int_or_null`.)

### 3.2 `checkpoint` — poll, one-shot per name, no `vfc`, no character

```
{"frame":%d,"event":"checkpoint","name":"%s","actual_zone_id":%s,"actual_act":%s,"apparent_act":%s,"game_mode":%s%s}
```

Trailing `%s` is `,"notes":<json_quote(notes)>` when notes non-nil, else empty
(no fixture event carries notes). Names by profile:

- `level_gated_reset_aware`: `gameplay_start` (zone==3 gate — **only fires for
  CNZ**, which is why the MGZ fixture has no `gameplay_start`),
  `act_transition_to_cnz2` (zone 3 act 0→1 edge), `gameplay_end`
  (finalisation).
- `aiz_end_to_end`: `intro_begin` (frame 0), `gameplay_start`,
  `aiz1_intro_refresh_begin`, `aiz2_reload_resume`, `aiz2_main_gameplay`,
  `hcz_handoff_complete` (conditions in `emit_s3k_semantic_events`).

### 3.3 `player_mode_set` — poll, on change (baseline first frame), no character

```
{"frame":%d,"vfc":%d,"event":"player_mode_set","mode":%d}
```

`mode` = u16 at `$FF08` (`Player_mode`).

### 3.4 `mode_change` — poll, on P1 status-bit / control-lock transitions, P1 only

Four templates, emitted in the fixed order air, rolling, on_object,
control_locked (each only on transition):

```
{"frame":%d,"vfc":%d,"event":"mode_change","field":"air","from":%d,"to":%d}
{"frame":%d,"vfc":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}
{"frame":%d,"vfc":%d,"event":"mode_change","field":"on_object","from":%d,"to":%d}
{"frame":%d,"vfc":%d,"event":"mode_change","field":"control_locked","from":%d,"to":%d}
```

`from`/`to` are 0/1. `control_locked` compares the P1 `move_lock` timer word
(`$B000+0x32`) > 0 across frames. An `air` transition additionally emits a
`state_snapshot` immediately after.

### 3.5 `routine_change` — poll, on P1 routine byte change, P1 only

```
{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X","sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,"status":"0x%02X","stand_on_obj":%d%s}
```

`x_vel`/`y_vel`/`inertia` are **signed decimal**. Trailing `%s` = stood-on
object context when `stand_on_obj` in `1..109`:

```
,"stand_obj_slot":%d,"stand_obj_type":"0x%08X","stand_obj_x":"0x%04X","stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"
```

New routine hurt (`0x04`) or death (`0x06`) additionally emits a
`state_snapshot`.

### 3.6 `state_snapshot` — poll (3 trigger sites, see §2), P1 only

```
{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,"on_object":%s,"pushing":%s,"underwater":%s,"roll_jumping":%s}
```

Booleans bare `true`/`false`; `y_radius`/`x_radius` read **signed** (s8).

### 3.7 `cpu_state` — poll, always, character `tails`

```
{"frame":%d,"vfc":%d,"event":"cpu_state","character":"tails","interact":"0x%04X","idle_timer":%d,"flight_timer":%d,"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X","auto_fly_timer":%d,"auto_jump_flag":%d,"ctrl2_held":"0x%02X","ctrl2_pressed":"0x%02X","pos_table_index":"0x%04X"}
```

Reads `$F700..$F70F` block, `$F66A/$F66B`, `$EE26`.

### 3.8 `oscillation_state` — poll, always, no character

```
{"frame":%d,"vfc":%d,"event":"oscillation_state","level_frame_counter":%d,"osc_table":"%s"}
```

`vfc` and `level_frame_counter` are **the same read** (u16 at `$FE04`, twice).
`osc_table` = 0x42 bytes at `$FE6E` as concatenated `%02X` (132 hex chars).

### 3.9 `object_state` — poll, always (proximity-gated), both players

For each OST slot `1..109` with non-zero object code whose `(x,y)` is within
`OBJECT_PROXIMITY = 160` px (Chebyshev, per-axis `abs <= 160`) of Player 1 OR
of Player 2 (P2 arm only when sidekick present):

```
{"frame":%d,"vfc":%d,"event":"object_state","slot":%d,"object_code":"0x%08X","routine":"0x%02X","status":"0x%02X","subtype":"0x%02X","x":"0x%04X","y":"0x%04X","x_radius":%d,"y_radius":%d}
```

`x_radius`/`y_radius` unsigned decimal (u8). Note slot 1 (Tails) itself is
included by the loop.

### 3.10 `interact_state` — poll, always, one per player

Sonic event always; Tails event only when `sidekick.present`:

```
{"frame":%d,"vfc":%d,"event":"interact_state","character":"sonic","interact":"0x%04X","interact_slot":%d,"status":"0x%02X","status_secondary":"0x%02X","object_control":"0x%02X"}
{"frame":%d,"vfc":%d,"event":"interact_state","character":"tails","interact":"0x%04X","interact_slot":%d,"status":"0x%02X","status_secondary":"0x%02X","object_control":"0x%02X"}
```

`interact` = u16 at slot `+0x42`; `interact_slot` resolved via
`interact_addr_to_slot` (0 unless the address is an exact OST slot base).

### 3.11 `sidekick_interact_object` — poll, always (sidekick present), character `tails` (v6.5, extended v6.26)

```
{"frame":%d,"vfc":%d,"event":"sidekick_interact_object","character":"tails","interact":"0x%04X","interact_slot":%d,"tails_render_flags":"0x%02X","tails_object_control":"0x%02X","tails_invulnerability_timer":"0x%02X","tails_width_pixels":"0x%02X","tails_height_pixels":"0x%02X","camera_x_copy":"0x%04X","camera_y_copy":"0x%04X","tails_status":"0x%02X","tails_on_object":%s,"object_code":"0x%08X","object_routine":"0x%02X","object_status":"0x%02X","object_x":"0x%04X","object_y":"0x%04X","object_subtype":"0x%02X","object_render_flags":"0x%02X","object_object_control":"0x%02X","object_active":%s,"object_destroyed":%s,"object_p1_standing":%s,"object_p2_standing":%s}
```

Object fields zero / `object_destroyed:true` when `interact_slot` not in
`1..109`. `camera_x_copy`/`camera_y_copy` = u16 at `$EE80`/`$EE84`.

### 3.12 `air_countdown_state` — poll, always, two events (owners `p1`, `p2`) (v6.23/24)

One event per fixed controller slot (94 = `Breathing_bubbles`, 95 =
`Breathing_bubbles_P2`), always emitted even when the slot code is 0:

```
{"frame":%d,"vfc":%d,"event":"air_countdown_state","owner":"%s","fixed_slot":%d,"object_code":"0x%08X","routine":"0x%02X","subtype":"0x%02X","obj30":"0x%04X","obj36":"0x%02X","obj37":"0x%02X","obj38":"0x%02X","obj3a":"0x%04X","obj3c":"0x%04X","obj3e":"0x%04X","owner_ptr":"0x%08X","owner_resolved":"%s","owner_air_left":"0x%02X","owner_status":"0x%02X","owner_status_secondary":"0x%02X","owner_facing_left":%s,"owner_underwater":%s,"rng_seed":"0x%08X","visible_children":[%s]}
```

`owner_resolved` ∈ `p1`/`p2`/`unknown` from the low word of `owner_ptr`
(slot `+0x40`). `visible_children` (only scanned when `owner_ptr != 0`): every
dynamic slot `3..92` whose code == `OBJ_AIR_COUNTDOWN = 0x00018164` and whose
parent-ptr low word matches, each formatted:

```
{"slot":%d,"object_code":"0x%08X","routine":"0x%02X","subtype":"0x%02X","x":"0x%04X","y":"0x%04X","x_sub":"0x%04X","y_sub":"0x%04X","y_vel":"0x%04X","render_flags":"0x%02X","anim":"0x%02X","mapping_frame":"0x%02X","anim_frame":"0x%02X","anim_frame_timer":"0x%02X","angle":"0x%02X","obj34":"0x%04X","obj3c":"0x%04X","parent_ptr":"0x%08X"}
```

### 3.13 `control_lock_state` — poll, change-triggered + 60-frame forced baseline (v6.12)

Compared tuple: `Ctrl_1_locked ($F7CA)`, `Ctrl_2_locked ($F7CB)`,
`Ctrl_1_logical` u16 (`$F602`), `Ctrl_2_logical` u16 (`$F66A`). First sample
after start always emits.

```
{"frame":%d,"vfc":%d,"event":"control_lock_state","ctrl1_locked":%d,"ctrl2_locked":%d,"ctrl1_logical":"0x%04X","ctrl2_logical":"0x%04X"}
```

### 3.14 `object_appeared` / `object_removed` / `object_near` / `slot_dump` — poll, always

`scan_objects` walks slots `1..109` against a `known_objects` cache
(interleaved per slot, see §2 step 37). Proximity for `object_near` is 160 px
per-axis of **Player 1 only**.

```
{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%08X","x":"0x%04X","y":"0x%04X"%s}
{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%08X"}
{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%08X","x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"%s}
{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}
```

The `%s` extra on appeared/near is present only when the slot's code equals
`OBJ_CNZ_BALLOON = 0x00031754`:

```
,"angle":"0x%02X","base_y":"0x%04X"
```

(`base_y` = u16 at slot `+0x32`.) `slot_dump` fires once at the end of any
frame with ≥1 `object_appeared`; `slots` is
`[[%d,"0x%08X"],...]` over dynamic slots `3..92` with non-zero code
(entry format `[%d,"0x%08X"]`).

### 3.15 `cage_state` — poll, always (object-gated), no window/zone gate (v6.3)

One event per OST slot `0..109` whose 32-bit code is the CNZ wire cage init or
frame pointer (`0x00033836` / `0x0003385E`):

```
{"frame":%d,"vfc":%d,"event":"cage_state","slot":%d,"x":"0x%04X","y":"0x%04X","subtype":"0x%02X","status":"0x%02X","p1_phase":"0x%02X","p1_state":"0x%02X","p2_phase":"0x%02X","p2_state":"0x%02X"}
```

(phases/states at slot offsets `$30/$31` and `$34/$35`.)

### 3.16 `cnz_cylinder_state` — poll, frame-windowed (v6.7)

Window: `4490..4512` default; single-window override
`OGGF_S3K_CNZ_CYLINDER_RANGE`. No zone gate; object-gated on
`OBJ_CNZ_CYLINDER = 0x00032188` per slot `0..109`:

```
{"frame":%d,"vfc":%d,"event":"cnz_cylinder_state","slot":%d,"x":"0x%04X","y":"0x%04X","subtype":"0x%02X","status":"0x%02X","routine":"0x%02X","render_flags":"0x%02X","p1_state":"0x%02X","p1_angle":"0x%02X","p1_distance":"0x%02X","p1_threshold":"0x%02X","p2_state":"0x%02X","p2_angle":"0x%02X","p2_distance":"0x%02X","p2_threshold":"0x%02X"}
```

### 3.17 `cnz_event_ram` — poll, **env-enabled** + windowed + zone-gated (v6.22)

Disabled unless `OGGF_S3K_CNZ_EVENT_RAM_RANGE` is set (default window
`15620..15735` if the value is malformed-but-non-empty… in practice set it to
`<start>-<end>`). Additional gates: zone==3 AND act==0.

```
{"frame":%d,"vfc":%d,"event":"cnz_event_ram","events_bg_00_word":"0x%04X","events_bg_02_word":"0x%04X","events_bg_08_word":"0x%04X","events_bg_08_long":"0x%08X","events_bg_0c_word":"0x%04X","events_bg_0c_long":"0x%08X","events_routine_bg":"0x%04X","background_collision_flag":"0x%02X","events_fg_5":"0x%04X","camera_y":"0x%04X","camera_max_y":"0x%04X","camera_target_max_y":"0x%04X","scroll_slots":%s}
```

`scroll_slots` = `[..]` over dynamic slots with code
`OBJ_CNZ_MINIBOSS_SCROLL_CONTROL = 0x00052004`:

```
{"slot":%d,"addr":"0x%04X","object_code":"0x%08X","routine":"0x%02X","routine_secondary":"0x%02X","x":"0x%04X","y":"0x%04X","status":"0x%02X","subtype":"0x%02X","objoff_2e":"0x%02X","objoff_30":"0x%02X","objoff_32":"0x%02X","objoff_34":"0x%02X","objoff_36":"0x%02X","objoff_38":"0x%02X"}
```

### 3.18 `aiz_fire_transition` — poll, profile+zone+windowed (v6.27)

Gates: `aiz_end_to_end` profile only, zone==0, window `5200..5600` default
(`OGGF_S3K_AIZ_FIRE_RANGE=<start>-<end>`).

```
{"frame":%d,"vfc":%d,"event":"aiz_fire_transition","camera_y_bg_copy":"0x%08X","camera_y_bg_rounded":"0x%04X","events_bg_00_word":"0x%04X","events_bg_02_word":"0x%04X","events_routine_bg":"0x%04X","events_fg_5":"0x%04X","camera_x":"0x%04X","camera_min_x":"0x%04X","camera_max_x":"0x%04X","player_x":"0x%04X","act":"0x%02X"}
```

### 3.19 `terrain_wall_sensor` — poll, zone+windowed, both players nested (v6.13)

Gates: zone==0, windows default `{7549..7560}`
(`OGGF_S3K_AIZ_WALL_SENSOR_RANGE`, multi-window `;`-separated).

```
{"frame":%d,"vfc":%d,"event":"terrain_wall_sensor",%s,%s}
```

with `%s` = `snapshot_player(PLAYER_BASE,"sonic")` then
`snapshot_player(SIDEKICK_BASE,"tails")`, each a nested object:

```
"%s":{"x_pos":"0x%04X","x_sub":"0x%04X","y_pos":"0x%04X","y_sub":"0x%04X","x_vel":"0x%04X","y_vel":"0x%04X","angle":"0x%02X","status":"0x%02X","status2":"0x%02X","object_control":"0x%02X","x_radius":%d,"y_radius":%d,"top_solid_bit":"0x%02X","lrb_solid_bit":"0x%02X","airborne":%s}
```

### 3.20 `aiz_handoff_terrain_state` — **hybrid** poll+hook, zone+windowed (v6.9)

Gates: zone==0, window `5430..5438` default
(`OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START/END`). The flush emits **once per
in-window frame regardless of hook registration** — the poll half (event RAM /
draw-delay / P1 fields) is always live; the hook half
(`sonic_floor_*`, `solid_*`, fed by `event.onmemoryexecute` at
`0x0F7F8`/`0x1E44C`/`0x1E4A0`) stays `false`/`"0x0000"` in lightweight mode.
This is exactly what the AIZ fixture contains (9 events, all
`sonic_floor_seen:false`, `solid_vertical_seen:false`).

```
{"frame":%d,"vfc":%d,"event":"aiz_handoff_terrain_state","events_bg":"0x%04X","draw_pos":"0x%04X","draw_rows":"0x%04X","kos_modules_left":"0x%02X","current_zone_act":"0x%04X","dynamic_resize":"0x%02X","object_load":"0x%02X","rings_manager":"0x%02X","p1_x":"0x%04X","p1_y":"0x%04X","p1_status":"0x%02X","p1_y_radius":"0x%02X","p1_top_solid":"0x%02X","sonic_floor_seen":%s,"sonic_floor_distance":"0x%04X","sonic_floor_angle":"0x%02X","sonic_floor_probe_x":"0x%04X","sonic_floor_probe_y":"0x%04X","solid_vertical_seen":%s,"solid_pre_y":"0x%04X","solid_surface_y":"0x%04X","solid_delta":"0x%04X"}
```

### 3.21 `collision_response_list_end_of_frame` — poll, zone+windowed (v6.15)

Gates: zone==3, windows default `{618..624}` (`OGGF_S3K_CRL_RANGE`,
multi-window). Emitted once per in-window frame even with hooks off (this is
the poll fallback of the CRL pair; the CNZ fixture has exactly its 7 window
frames).

```
{"frame":%d,"vfc":%d,"event":"collision_response_list_end_of_frame","list_count":%d,"list_entries":[%s],"spring_children":[%s]}
```

List entry (`v615_format_list_entry`; `%s` suffix is
`,"routine_label":"%s"` only when the entry's code is a Clamer spring-child
routine `0x000890AA`/`0x000890C8`/`0x000890D0`):

```
{"slot":%d,"ost_lo":"0x%04X","object_code":"0x%08X","collision_flags":"0x%02X","collision_property":"0x%02X","x_pos":"0x%04X","y_pos":"0x%04X"%s}
```

Spring child (`v615_format_spring_child`):

```
{"slot":%d,"ost_lo":"0x%04X","object_code":"0x%08X","routine_label":"%s","x_pos":"0x%04X","y_pos":"0x%04X","collision_property":"0x%02X","collision_flags":"0x%02X","cooldown_byte":"0x%02X"}
```

List parsing: count word at `$E380` (capped `0x7E`), `count/2` word entries
from `$E382`; out-of-OST entries render slot `-1` with zeroed fields.

### 3.22 Hook-driven families (ALL require `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`; ALL absent from all three fixtures)

Each accumulates hits during the emulated frame via `event.onmemoryexecute` /
`event.onmemorywrite` callbacks and flushes at its slot in §2. Emits nothing on
frames with zero hits (except as noted).

#### `tails_cpu_normal_step` (v6.5, extended v6.16) — character `tails`

Hooks `loc_13DD0 (0x13DD0)`, `loc_13EB8 (0x13EB8)`, `loc_14A0A`, `loc_14B7A`;
a0 must be Tails. No frame window. At most one event per frame (state struct
keyed by frame):

```
{"frame":%d,"vfc":%d,"event":"tails_cpu_normal_step","character":"tails","status":"0x%02X","object_control":"0x%02X","ground_vel":"0x%04X","x_vel":"0x%04X","delayed_stat":"0x%02X","delayed_input":"0x%04X","pos_table_index":"0x%02X","delayed_target_x":"0x%04X","delayed_target_y":"0x%04X","follow_dx":"0x%04X","follow_dy":"0x%04X","loc_13dd0_branch":"%s","ctrl2_logical":"0x%04X","ctrl2_held_logical":"0x%02X","path_pre_ground_vel":"0x%04X","path_pre_x_vel":"0x%04X","path_pre_status":"0x%02X","path_post_ground_vel":"0x%04X","path_post_x_vel":"0x%04X","path_post_status":"0x%02X"}
```

`loc_13dd0_branch` ∈ `not_seen`/`leader_on_object`/`leader_fast`/`fallthrough_sub20`.

#### `aiz_boundary_state` (v6.6/6.14) — character `tails`

Hooks Tails boundary + AIZ tree routines (`0x14F08/0x14F4A/0x14F56/0x14F5C`,
`0x1F912/0x1F982`); gates zone==0 + windows default
`{4660..4679, 7549..7560}` (`OGGF_S3K_AIZ_BOUNDARY_RANGE`, plus legacy
`OGGF_S3K_AIZ_BOUNDARY_FRAME_START/END`). Emits only when a hook actually
fired that frame (`seen`):

```
{"frame":%d,"vfc":%d,"event":"aiz_boundary_state","character":"tails","camera_min_x":"0x%04X","camera_max_x":"0x%04X","camera_min_y":"0x%04X","camera_max_y":"0x%04X",%s,%s,%s,%s,"boundary_action":"%s",%s}
```

The five `%s` groups are `V66.format_state` with prefixes `tree_pre`,
`tree_post`, `boundary_pre`, `boundary_post`, `post_move`:

```
"%s_x":"0x%04X","%s_y":"0x%04X","%s_x_vel":"0x%04X","%s_y_vel":"0x%04X"
```

`boundary_action` ∈ `not_seen`/`none`/`kill`/`x_clamp_%04X`.

#### `aiz_transition_floor_solid` (v6.7) — per-player nested

Hooks SolidObjectTop labels (`0x1E2E0/0x1E2F4/0x1E42E/0x1E44C/0x1E4A0/0x1E4D4`)
with a0 = `Obj_AIZTransitionFloor (0x0004FE38)`; gates zone==0 + window
`5408..5438` (`OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/END`). Emits only
when seen:

```
{"frame":%d,"vfc":%d,"event":"aiz_transition_floor_solid","slot":%d,"object_status":"0x%02X","object_x":"0x%04X","object_y":"0x%04X","p1_standing":%s,"p2_standing":%s,%s,%s}
```

`%s` groups via `V67_AIZ.format_player` with prefixes `p1`, `p2`:

```
"%s_path":"%s","%s_d1":"0x%04X","%s_d2":"0x%04X","%s_d3":"0x%04X","%s_status":"0x%02X","%s_object_control":"0x%02X","%s_y_radius":"0x%02X","%s_x":"0x%04X","%s_y":"0x%04X","%s_y_vel":"0x%04X","%s_interact_slot":%d
```

#### `cage_execution` (v6.3) — per-hit player-scoped via `player_addr`

Hooks cage branches (`0x338C4/0x339A0/0x33ADE/0x33B1E/0x33B62`). No window.
Wrapper + hit entry:

```
{"frame":%d,"vfc":%d,"event":"cage_execution","hits":[%s]}
{"branch":"%s","pc":"0x%05X","cage_addr":"0x%04X","player_addr":"0x%04X","state_addr":"0x%04X","d5":"0x%04X","d6":"0x%02X","state_byte":"0x%02X","player_status":"0x%02X","player_obj_ctrl":"0x%02X","cage_status":"0x%02X"}
```

Branch labels: `sub_338C4_entry`, `loc_339A0_mounted`, `loc_33ADE_cooldown`,
`loc_33B1E_continue`, `loc_33B62_release`.

#### `velocity_write` (v6.4/6.13) — character `tails` only

`event.onmemorywrite` on Tails x_vel/y_vel bytes (`0xFFB062-0xFFB065`, full-bus
addresses). Windows default `{3640..3660, 7549..7560}`
(`OGGF_S3K_VELOCITY_WRITE_RANGE`, single or multi-window). Value read
post-write from RAM.

```
{"frame":%d,"vfc":%d,"event":"velocity_write","character":"tails","x_vel_writes":[%s],"y_vel_writes":[%s]}
{"pc":"0x%05X","val":"0x%04X"}
```

#### `position_write` (v6.8/6.11/6.13/6.17) — one event per character with hits (`sonic` first, then `tails`)

`event.onmemorywrite` on Sonic/Tails x_pos/y_pos bytes (`0xFFB010/11`,
`0xFFB014/15`, `0xFFB05A/5B`, `0xFFB05E/5F`). Windows default
`{4788..4792, 7549..7560, 7600..7625, 16320..16335}`
(`OGGF_S3K_POSITION_WRITE_RANGE`).

```
{"frame":%d,"vfc":%d,"event":"position_write","character":"%s","x_pos_writes":[%s],"y_pos_writes":[%s]}
{"pc":"0x%05X","val":"0x%04X","a1":"0x%08X","a0":"0x%08X"}
```

#### `aiz_ship_loop` (v6.18)

Hooks `AIZ2_DoShipLoop` labels (`0x502CA/0x502FA/0x50318/0x50324/0x5033A/0x50348`,
labels `entry`/`camera_store`/`sub_50318`/`loc_50324`/`loc_5033A`/`ret_50348`).
Windows default `{16320..16335}` (`OGGF_S3K_AIZ_SHIP_LOOP_RANGE`).

```
{"frame":%d,"vfc":%d,"event":"aiz_ship_loop","hits":[%s]}
{"label":"%s","pc":"0x%05X","character":"%s","a1":"0x%08X","d0":"0x%04X","d1":"0x%04X","camera_x":"0x%04X","camera_min_x":"0x%04X","camera_max_x":"0x%04X","events_bg_2":"0x%04X","player_x":"0x%04X","player_y":"0x%04X","player_gvel":"0x%04X","player_xvel":"0x%04X","player_anim":"0x%02X","player_status":"0x%02X"}
```

`character` = `sonic`/`tails`/`"0x%04X"` from a1 low word.

#### `sonic_record_pos` (v6.21) — P1-gated (a0 == Player_1)

Hooks `Sonic_RecordPos` entry `0x10D80`. **No frame window** — with hooks on
it fires every recorded frame. Wrapper + hit entry:

```
{"frame":%d,"vfc":%d,"event":"sonic_record_pos","hits":%s}
{"pc":"0x%05X","pos_table_index":"0x%02X","ctrl1_logical":"0x%04X","ctrl1_locked":%d,"ctrl1_raw":"0x%04X","object_control":"0x%02X","status":"0x%02X","status_secondary":"0x%02X","x":"0x%04X","y":"0x%04X"}
```

(`hits` is a `[...]` array built by `format_hits`.)

#### `rng_call` (v6.25) — double-gated

Hooks `Random_Number` entry `0x001D24`. Requires diagnostic hooks AND
`OGGF_S3K_RNG_CALL_RANGE` set (default window `17000..21850` when the value is
malformed); zone==3 gate per hit. Result/next-seed reconstructed in Lua from
the entry seed (`s3k_random_step`); caller PC pulled from the stack (u32 at
A7, masked `0xFFFFFF`).

```
{"frame":%d,"vfc":%d,"event":"rng_call","hits":%s}
{"pc":"0x%05X","caller_pc":"0x%06X","source":%s,"seed_before":"0x%08X","seed_after":"0x%08X","result":"0x%08X","result_byte":"0x%02X",%s,%s}
```

`source` is a `json_quote`d label from `source_label` (`CNZBalloon.init`,
`CNZBalloon.subtype80_bubbler`, `AirCountdown`, `Bubbler`, `unknown`,
`object_%08X`). The two trailing `%s` are a0/a1 object contexts
(`format_object_context`, prefix `a0`/`a1`):

```
"%s_ptr":"0x%04X","%s_slot":%d,"%s_object_code":"0x%08X","%s_routine":"0x%02X","%s_subtype":"0x%02X","%s_x":"0x%04X","%s_y":"0x%04X"
```

#### `cnz_cylinder_execution` (v6.7) — Tails-gated (a1 == Player_2)

Hooks cylinder/platform labels (`0x324C0/0x32538/0x32594/0x32604/0x3260A/0x1E1CA/0x1E1F2`;
branch labels `sub_324C0_entry`, `loc_32538_active`, `loc_32594_after_x`,
`loc_32604_clear`, `loc_3260A_twist`, `MvSonicOnPtfm_pre`,
`MvSonicOnPtfm_post`). Window `4490..4512` (`OGGF_S3K_CNZ_CYLINDER_RANGE`);
a0 must be a live cylinder object.

```
{"frame":%d,"vfc":%d,"event":"cnz_cylinder_execution","hits":[%s]}
{"branch":"%s","pc":"0x%05X","cylinder_addr":"0x%04X","player_addr":"0x%04X","state_addr":"0x%04X","d2":"0x%04X","d4":"0x%04X","d5":"0x%04X","d6":"0x%02X","cylinder_status":"0x%02X","slot_state":"0x%02X","slot_angle":"0x%02X","slot_distance":"0x%02X","slot_threshold":"0x%02X","player_x":"0x%04X","player_x_sub":"0x%04X","player_y":"0x%04X","player_y_sub":"0x%04X","player_status":"0x%02X","player_obj_ctrl":"0x%02X"}
```

#### `solid_object_cont_entry` (v6.11)

Hooks `SolidObject_cont` `0x1DF90`. Windows default `{4788..4792, 7600..7625}`
(`OGGF_S3K_SOLID_CONT_RANGE`).

```
{"frame":%d,"vfc":%d,"event":"solid_object_cont_entry","entries":[%s]}
{"pc":"0x%05X","a0":"0x%08X","a1":"0x%08X","d1":"0x%04X","d2":"0x%04X","y_radius":"0x%02X","default_y_radius":"0x%02X","player_x":"0x%04X","player_y":"0x%04X","player_status":"0x%02X","solid_x":"0x%04X","solid_y":"0x%04X"}
```

#### `collision_response_list_per_frame` (v6.15) — one event per Touch_Process hit

Hooks `Touch_Process` `0x10440`; gates zone==3 + windows `{618..624}`
(shared `OGGF_S3K_CRL_RANGE`). `hit_player` ∈ `sonic`/`tails`/`other` from a0.
Entry/spring-child sub-templates identical to §3.21.

```
{"frame":%d,"vfc":%d,"event":"collision_response_list_per_frame","hit_player":"%s","a0":"0x%04X","list_count":%d,"list_entries":[%s],"spring_children":[%s]}
```

## 4. Per-fixture event-family presence (empirical, gunzipped fixture aux streams)

All three fixtures: `lua_script_version` `6.28-s3k`, `trace_schema` 6,
`csv_version` 7, `capture_mode` `physics_animation_aux_without_diagnostic_hooks`
(lightweight — no diagnostic hooks; no `OGGF_S3K_RNG_CALL_RANGE` /
`OGGF_S3K_CNZ_EVENT_RAM_RANGE`). Counts are exact line counts per
`"event":"…"` value.

| Family | AIZ fullrun (20798f, `aiz_end_to_end`) | CNZ (42253f, `level_gated_reset_aware`) | MGZ (35912f, `level_gated_reset_aware`) | Trigger |
|---|---:|---:|---:|---|
| `cpu_state_snapshot` | 1 | 1 | 1 | poll, pre-trace |
| `object_state_snapshot` | 0 | 4 | 0 | poll, pre-trace (balloons only) |
| `zone_act_state` | 6 | 3 | 3 | poll, on change |
| `checkpoint` | 6 | 3 | 1 | poll, profile one-shots |
| `player_mode_set` | 1 | 1 | 1 | poll, on change |
| `mode_change` | 502 | 1292 | 1100 | poll, on change |
| `routine_change` | 6 | 11 | 35 | poll, on change |
| `state_snapshot` | 591 | 1333 | 1105 | poll, interval+triggers |
| `cpu_state` | 20798 | 42253 | 35912 | poll, every frame |
| `oscillation_state` | 20798 | 42253 | 35912 | poll, every frame |
| `object_state` | 242402 | 356364 | 315843 | poll, proximity |
| `interact_state` | 41496 | 84506 | 71824 | poll, per player |
| `sidekick_interact_object` | 20698 | 42253 | 35912 | poll, sidekick present |
| `air_countdown_state` | 41596 | 84506 | 71824 | poll, 2/frame |
| `control_lock_state` | 2160 | 5184 | 5014 | poll, change+baseline |
| `object_appeared` | 3638 | 3335 | 3525 | poll |
| `object_removed` | 2390 | 2657 | 2513 | poll |
| `object_near` | 199830 | 270151 | 260433 | poll, P1 proximity |
| `slot_dump` | 1593 | 1772 | 1639 | poll |
| `cage_state` | 0 | 9766 | 0 | poll, object-gated |
| `cnz_cylinder_state` | 0 | 23 | 0 | poll, window 4490-4512 |
| `collision_response_list_end_of_frame` | 0 | 7 | 0 | poll, zone 3 + window 618-624 |
| `aiz_fire_transition` | 401 | 0 | 0 | poll, profile+zone 0+window 5200-5600 |
| `terrain_wall_sensor` | 12 | 0 | 0 | poll, zone 0 + window 7549-7560 |
| `aiz_handoff_terrain_state` | 9 | 0 | 0 | hybrid poll (hook fields all false/0), zone 0 + window 5430-5438 |
| `cnz_event_ram` | 0 | 0 | 0 | poll, env-gated OFF |
| `tails_cpu_normal_step` | 0 | 0 | 0 | hook, hooks OFF |
| `aiz_boundary_state` | 0 | 0 | 0 | hook, hooks OFF |
| `aiz_transition_floor_solid` | 0 | 0 | 0 | hook, hooks OFF |
| `cage_execution` | 0 | 0 | 0 | hook, hooks OFF |
| `velocity_write` | 0 | 0 | 0 | hook (memwrite), hooks OFF |
| `position_write` | 0 | 0 | 0 | hook (memwrite), hooks OFF |
| `aiz_ship_loop` | 0 | 0 | 0 | hook, hooks OFF |
| `sonic_record_pos` | 0 | 0 | 0 | hook, hooks OFF |
| `rng_call` | 0 | 0 | 0 | hook + env, both OFF |
| `cnz_cylinder_execution` | 0 | 0 | 0 | hook, hooks OFF |
| `solid_object_cont_entry` | 0 | 0 | 0 | hook, hooks OFF |
| `collision_response_list_per_frame` | 0 | 0 | 0 | hook, hooks OFF |

Cross-checks that pin the gate semantics:

- AIZ `interact_state` 41496 = 20798×2 − 100 and `sidekick_interact_object`
  20698 = 20798 − 100: the sidekick slot is empty for exactly 100 frames
  (act/zone reload windows), and both families gate on `sidekick.present`.
- AIZ `air_countdown_state` 41596 = 20798×2 exactly (unconditional 2/frame).
- MGZ `checkpoint` = 1 (`gameplay_end` only): `gameplay_start` /
  `act_transition_to_cnz2` are zone-3-gated inside the
  `level_gated_reset_aware` branch and never fire in MGZ (zone 2).
- CNZ checkpoints: `gameplay_start`@0, `act_transition_to_cnz2`@16669,
  `gameplay_end`@42253 (= trace_frame_count, one past last row). AIZ
  checkpoints: `intro_begin`@0, `gameplay_start`@1386,
  `aiz1_intro_refresh_begin`@1537, `aiz2_reload_resume`@5496,
  `aiz2_main_gameplay`@5496, `hcz_handoff_complete`@20769.
- `terrain_wall_sensor` frames are exactly 7549..7560; `aiz_fire_transition`
  spans exactly 5200..5600; `collision_response_list_end_of_frame` exactly
  618..624; `cnz_cylinder_state` exactly 4490..4512.
- The 9 AIZ `aiz_handoff_terrain_state` events all carry
  `"sonic_floor_seen":false … "solid_vertical_seen":false` — direct proof the
  fixture was captured with the execution hooks unregistered.

## 5. Native-port consequence

For byte-identical regeneration of the three gated fixtures the native
recorder must implement **all frame-polled families** above (including the
windowed ones and the hybrid `aiz_handoff_terrain_state` with its hook fields
pinned to their lightweight defaults) but does **not** need M68K
execute/memory-write callback support: every hook-driven family is provably
absent from all three fixtures because they were captured with
`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` unset. Native support for the hook-driven
families (`tails_cpu_normal_step`, `aiz_boundary_state`,
`aiz_transition_floor_solid`, `cage_execution`, `velocity_write`,
`position_write`, `aiz_ship_loop`, `sonic_record_pos`, `rng_call`,
`cnz_cylinder_execution`, `solid_object_cont_entry`,
`collision_response_list_per_frame`) and the env-gated `cnz_event_ram` is
therefore **explicitly deferred**, not silently dropped: if a future capture
needs them, extend `GpgxHost` with the LibGPGX exec/mem callback surface and
implement the templates above.

### 5.1 Environment variables the native port must refuse

The Lua reads its entire diagnostic surface from the **environment**, never
from CLI arguments, so "the native CLI exposes no such flag" is no protection:
a variable still exported by an earlier Lua investigation changes what the Lua
would have produced from the same movie, and a native capture that ignores it
gets committed as canonical with no diagnostic. The native port models none of
them and therefore refuses each loudly (`Program.RejectUnmodeledS3kEnvironment`,
pinned by the `TraceCli S3K trace refuses every unmodeled output affecting
environment variable` test). Three classes:

| Class | Variables | Why it changes output |
| --- | --- | --- |
| Hook arming | `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`, `OGGF_S3K_RNG_CALL_RANGE`, `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | Arms a deferred hook-driven family and appends it to `aux_schema_extras`. |
| Polled-family windows | `OGGF_S3K_AIZ_FIRE_RANGE`, `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`, `OGGF_S3K_CRL_RANGE`, `OGGF_S3K_CNZ_CYLINDER_RANGE`, `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START/END` | Retunes a family §3 lists as frame-polled, which the port implements with the Lua default window pinned as a constant. These are applied at Lua script load **independently of the hook switch**, so they change `aux_state.jsonl` in exactly the lightweight mode every fixture was captured in. |
| Early stop | `OGGF_TRACE_STOP_FRAME`, `OGGF_BK2_FRAME_COUNT` | Finalizes before the movie/zone stop, truncating both `physics.csv` and `aux_state.jsonl`. |

Refusal keys on **non-emptiness**, not on parseability: the Lua warns and
ignores a malformed range, but an operator who exported one meant to change the
capture and must not be handed a silently canonical file.

The remaining window overrides — `OGGF_S3K_POSITION_WRITE_RANGE`,
`OGGF_S3K_VELOCITY_WRITE_RANGE`, `OGGF_S3K_SOLID_CONT_RANGE`,
`OGGF_S3K_AIZ_SHIP_LOOP_RANGE`, `OGGF_S3K_AIZ_BOUNDARY_RANGE` (and its legacy
`_FRAME_START/END` pair), `OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/END` —
are deliberately **not** refused: every flush they touch is additionally gated
on a hook-populated `state.seen` / hit list, so with the hook switch off (itself
a refusal) they change no byte of the Lua's own output either. Refusing them
would be a false refusal; a test pins that the CLI does not name them.

Metadata note (out of scope here but easy to trip over): the fixtures are
stamped `6.28-s3k` while HEAD stamps `6.30-s3k`, and the MGZ fixture carries a
hand-normalized extra key `"pre_trace_osc_frames": 0` that HEAD's
`write_metadata` does not emit. The exact permitted metadata delta must be
pinned separately; `physics.csv` and `aux_state.jsonl` allow **zero**
normalization.
