# S3K Trace Recorder — Byte-Level CORE Specification (RAM map, physics.csv, metadata)

Authoritative byte-level specification for the maintained native S3K
STANDARD recorder in `tools/bizhawk-headless/`. This document owns the S3K
RAM address map, `physics.csv`, metadata shape, hardware-timing stream,
input-column derivation (including `ADVANCE_ONLY` rows), and file encodings.
Two sibling documents own the remaining STANDARD surface:

- [s3k-aux-events.md](s3k-aux-events.md) — every `aux_state.jsonl` event
  template (verbatim), per-frame emission order, per-fixture event census.
- [s3k-profiles-and-hooks.md](s3k-profiles-and-hooks.md) — the three
  profiles, arm/stop/reset predicates, POST-advance stop ordering, hook
  architecture and its deferral, BK2/movie handling, env vars.

Scope: the STANDARD recorder in lightweight capture mode and the timing
ledger/stream shared by the native complete-run recorder. Complete-run
segmentation and metadata extensions remain owned by
[s3k-complete-run-behavior.md](s3k-complete-run-behavior.md). The frozen Lua
recorders remain the historical authority for the physics/aux surface they
published, but they stop at version 6.37 and hardware-timing schema 1. The
native implementations are the maintained behavior and publication authority
for version 6.38 and schema 2.

The S1 spec's frame-alignment / `IGpgxHost` translation model (S1
§2.3–§2.4) and file-encoding rules (S1 §8) carry over unchanged: LF-only
newlines, no BOM, ASCII output, CSV flushed every 60 rows, aux flushed
per line.

## 6. Current version and container contract

| producer/data | version | trace schema | hardware-timing schema | timing kinds |
|---|---|---:|---:|---|
| Current native STANDARD writer | `6.38-s3k` | 7 | 2 | module and direct |
| Current native complete-run writer | `6.38-s3k-completerun` | 7 | 2 | module and direct |
| Committed S3K fixtures | `6.37-s3k` / `6.37-s3k-completerun` | 7 | 1 | module only |
| Frozen Lua recorders | `6.37-s3k` / `6.37-s3k-completerun` | 7 | 1 | module only |

Current native output contains `physics.csv`, `aux_state.jsonl`,
`metadata.json`, and `hardware_timing.jsonl` before the publication layer
applies fixture compression. `trace_schema` stays 7 because the timing
container and event shape did not change. `hardware_timing_schema` selects
the authority registry:

- schema 1 records and authorizes `KOS_MODULE_QUEUE`; the direct queue remains
  live production timing;
- schema 2 records and authorizes `KOS_MODULE_QUEUE` and
  `KOS_DECOMPRESSION_QUEUE`; and
- either schema rejects unknown event kinds. An event never submits work or
  supplies compressed/decoded bytes.

### 6.1 Current STANDARD metadata bytes

`S3KTraceMetadataWriter` writes two-space indentation, the following fixed key
order, LF line endings, and one trailing LF. Schema 2 is the production
default; schema 1 is accepted only for explicit compatibility tests.

```json
{
  "game": "s3k",
  "zone": "<start_zone_name>",
  "zone_id": <start_zone_id>,
  "act": <start_act + 1>,
  "bk2_frame_offset": <bk2_frame_offset>,
  "trace_frame_count": <trace_frame_count>,
  "start_x": "0x<hex4>",
  "start_y": "0x<hex4>",
  "characters": ["sonic", "tails"],
  "main_character": "sonic",
  "sidekicks": ["tails"],
  "rng_seed": "0x<hex8>",
  "recording_date": "<YYYY-MM-DD>",
  "lua_script_version": "6.38-s3k",
  "trace_schema": 7,
  "hardware_timing_schema": 2,
  "csv_version": 7,
  "capture_mode": "physics_animation_aux_without_diagnostic_hooks",
  "aux_schema_extras": [<profile-owned entries>],
  "trace_profile": "<TRACE_PROFILE>",
  "bizhawk_version": "2.11",
  "genesis_core": "Genplus-gx",
  "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
  "notes": "<profile-owned notes>"
}
```

The recording date is the only nondeterministic value. Standard native
captures use `6.38-s3k`; complete-run, bonus, and special-stage metadata use
`6.38-s3k-completerun` and their complete-run-owned key set. The committed
fixtures intentionally retain their published 6.37/schema-1 metadata and
must not be rewritten merely to match this template.

### 6.2 Current hardware-timing bytes

`hardware_timing.jsonl` is UTF-8 without BOM, with one compact object and one
LF per event:

```json
{"event":"hardware_work_completed","raw_frame":10429,"boundary":"pre_main_loop","kind":"kos_decompression_queue","ordinal":3,"submission_fingerprint":"sha256:<64 lowercase hex digits>"}
```

Fields and field order are exact. Events sort by `raw_frame`, boundary order
`vint_service`, `pre_main_loop`, `post_objects`, kind, then ordinal. On a raw
frame where both physical owners retire, the direct `pre_main_loop` event is
written before the module `post_objects` event.

The fingerprint is independently derived from length-prefixed UTF-8 kind,
big-endian signed 32-bit canonical source, compressed length, canonical
destination bits, decoded destination length, length-prefixed compression
variant, and module count. The SHA-256 prefix is literal `sha256:`. Direct
destinations retain their exact ROM longword bits, including sign-extended
`0xFFFFxxxx` RAM addresses.

### 6.3 Current direct and module ledgers

The recorder owns independent, run-wide ordinals for the two physical queues:

- direct FIFO: `$FF40-$FF5F`, four eight-byte source/destination entries,
  with count/busy word at `$FF0E`;
- module FIFO: `$FF64-$FF7B`, four six-byte source/destination entries, with
  modules-left/busy byte at `$FF60`.

Direct count is always `$FF0E & $7FFF`; zero-sentinel slot scanning is
forbidden. Canonical identity is assigned when a slot first appears and
survives active decoder progress. While the previous head remains busy, slot
zero must not change. Busy transitions plus longest suffix/prefix overlap
prove a retirement and every append, including unchanged-count and adjacent
identical replacements. One proven head may retire between represented
samples; loss of more than one or unexplained mutation is fatal. Schema 2
emits that retirement at `pre_main_loop`; schema 1 keeps the ledger current
but suppresses the direct event.

The module ledger normalizes an active source to its two-byte archive header,
retains that canonical identity while the active pointer advances, and
recognizes final retirement only from the tracked final-module state plus
head removal/shift. Per-module busy-bit falls are not archive completion.
Module retirement emits at `post_objects`, or `vint_service` for a genuine
held-counter row without an admitted object loop. The exact title-card parent
state is the sole held-counter exception that preserves `post_objects`.

Segment handoffs may pass a null timing writer while keeping both ledgers and
ordinals alive. A standard-recorder discard/reset clears both ledgers and
resets both ordinal bases atomically. A module-created Kosinski child is a
real direct submission with its own direct ordinal and fingerprint.

## 0. Published schema-1 fixtures (read-only; gunzip to temp)

| Fixture | Profile | `bk2_frame_offset` | Rows | physics.csv sha256 | aux_state.jsonl sha256 |
|---|---|---|---|---|---|
| `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/` | `aiz_end_to_end` | 511 | 20798 | `3c219725d85d64762b514f973263edced337a37cd16fb8bf50f2b0ac3b5a2a39` | `9d90d669de5b9fc0c00666ad2023a164d1d110d441b9bcc8403280d1a5d74b47` |
| `src/test/resources/traces/s3k/cnz/` | `level_gated_reset_aware` | 3171 | 42253 | `195de5a64bd879f6d920ffe9a487931beb4f6366516587d23268b1059a7b46e2` | `17ddb988b74e8718d6e3d73a7aaefff56d077e6e5d015c7ab875a4674a94052e` |
| `src/test/resources/traces/s3k/mgz/` | `level_gated_reset_aware` | 2602 | 35912 | `16bff6712e4228494b8aeac587006edeee9f6befc62aa7b9078a465db4e2d611` | `4ce8ee02e8e6dc1664659a494578427da0c6111e5a4c0fb88b71026b2b2c2035` |

Movies (all `Core Genplus-gx`, `Platform GEN`, console + **two** pad
sections `|..|........|........|`): `s3-aiz1-2-sonictails.bk2` 21309
input rows, `s3k-cnz-sonic-tails.bk2` 45597, `s3k-mgz-sonic-tails.bk2`
38818. The BizHawk `SHA1` header field holds
`C5B1C655C19F462ADE0AC4E17A844D10` (a 32-hex digest of the locked-on
ROM; the ROM's actual SHA-1 is
`CFBF98C36C776677290A872547AC47C53D2761D6`). Alignment facts: AIZ ran to
exact movie end (511 + 20798 = 21309); CNZ finalised on zone-leave at row
42253 (final zone 5 = ICZ handoff); MGZ on zone-leave at row 35912
(final zone 3 = CNZ handoff).

These immutable fixtures are stamped `6.37-s3k`, `trace_schema: 7`, and
`hardware_timing_schema: 1`. Their published physics/aux bytes and
schema-1 timing stream are protected by frozen hashes. Current native
6.38/schema-2 output is a publication candidate, not byte-identical metadata
or timing data; no differential normalization may conceal payload changes or
install schema-2 bytes without explicit publication approval.

---

## 1. RAM address map

All reads are from the `mainmemory` domain (68K work RAM, `$FF0000` base
stripped; Lua `0xB000` = M68K `$FFFFB000`). Multi-byte values are
big-endian, assembled from consecutive byte reads in the native port.
Widths: `u8`, `s8`, `u16be`, `s16be`, `u32be`. "skdisasm name" is the
label the address actually resolves to in
`docs/skdisasm/sonic3k.constants.asm` — some of the Lua's own constant
names are historically mislabeled (flagged below); **reproduce the read,
not the label**.

### 1.1 Global variables (core capture path)

| Address | Width | skdisasm name | Used for |
|---|---|---|---|
| `0xF600` | u8 | `Game_mode` | Arm/reset/end detection; `zone_act_state.game_mode`; `checkpoint.game_mode`. Level family = `(mode & 0x0F) == 0x0C` (accepts `$0C`/`$4C`/`$8C`) |
| `0xF602` | u16be | `Ctrl_1_logical` (held+pressed bytes) | `control_lock_state.ctrl1_logical` |
| `0xF604` | u8 | `Ctrl_1_held` (raw) | CSV-input **fallback arg only** (never used while a movie is loaded, §4) |
| `0xF7CA` | u8 | `Ctrl_1_locked` | Default/level-gated arm gate; checkpoint gates; `control_lock_state.ctrl1_locked` |
| `0xF7CB` | u8 | `Ctrl_2_locked` | `control_lock_state.ctrl2_locked` |
| `0xF66A` | u16be | `Ctrl_2_logical` (word read) | `control_lock_state.ctrl2_logical` |
| `0xF66A` | u8 | `Ctrl_2_held_logical` | `cpu_state.ctrl2_held` |
| `0xF66B` | u8 | `Ctrl_2_pressed_logical` | `cpu_state.ctrl2_pressed` |
| `0xFE20` | u16be | `Ring_count` | CSV `rings` |
| `0xEE78` | u16be | `Camera_X_pos` (pixel word) | CSV `camera_x`; `aiz_fire_transition.camera_x` |
| `0xEE7C` | u16be | `Camera_Y_pos` (pixel word) | CSV `camera_y` |
| `0xEE14` / `0xEE16` | u16be | `Camera_min_X_pos` / `Camera_max_X_pos` | `aiz_fire_transition` |
| `0xEE80` / `0xEE84` | u16be | `Camera_X_pos_copy` / `Camera_Y_pos_copy` (pixel words) | `sidekick_interact_object.camera_*_copy` |
| `0xEE90` | u32be | `Camera_Y_pos_BG_copy` (16.16) | `aiz_fire_transition.camera_y_bg_copy` |
| `0xEE96` | u16be | `Camera_Y_pos_BG_rounded` | `aiz_fire_transition.camera_y_bg_rounded` |
| `0xEE1A` / `0xEE12` | u16be | `Camera_max_Y_pos` / target | `cnz_event_ram` only (env-gated; absent from fixtures) |
| `0xFE10` | u8 | `Current_zone` | Start capture; zone-leave stop; zone gates; `zone_act_state.actual_zone_id`. Also read once as **u16be** → `aiz_handoff_terrain_state.current_zone_act` (zone<<8 \| act) |
| `0xFE11` | u8 | `Current_act` | Start capture; `zone_act_state.actual_act`; `aiz_fire_transition.act` |
| `0xEE4F` | u8 | apparent act | `zone_act_state.apparent_act` |
| `0xFF08` | u16be | `Player_mode` | `player_mode_set.mode` |
| `0xEEC2` | u16be | `Events_routine_bg` | `aiz_fire_transition`; `aiz_handoff_terrain_state.events_bg` |
| `0xEEC6` | u16be | `Events_fg_5` | `aiz1_intro_refresh_begin` checkpoint gate; `aiz_fire_transition.events_fg_5` |
| `0xEED2` / `0xEED4` | u16be | `Events_bg+$00` / `+$02` | `aiz_fire_transition.events_bg_00_word/_02_word` |
| `0xF664` | u8 | `Background_collision_flag` | `cnz_event_ram` only |
| `0xF711` | u8 | level-started flag | `gameplay_start` checkpoint gates |
| `0xFE04` | u16be | **`Level_frame_counter`** — the Lua calls it `ADDR_FRAMECOUNT` | CSV `gameplay_frame_counter`; every aux `vfc`; `oscillation_state.level_frame_counter`. **Live**: it starts at `0` on the pre-level prefix rows and ticks once per level frame thereafter. Until v6.31-s3k this read was `0xFE08` (`Debug_placement_mode`, dead-zero outside debug mode), so every pre-v6.31 fixture carried the constant `0000` / `"vfc":0` in these fields; the three canonical fixtures were regenerated on `0xFE04` and no dead read remains |
| `0xFE0E` | u16be | **low word of `V_int_run_count`** (the `ds.l` at `0xFE0C`) — the Lua calls it `ADDR_VBLA_WORD` | CSV `vblank_counter`. **Live**: a free-running V-int counter, ticking once per V-blank, so it advances on lag frames and title-screen frames too, not only on level frames. Until v6.32-s3k this read was `0xFE12` (`Life_count`), so every pre-v6.32 fixture carried `lives << 8` (`0300`–`0600` observed) in this column, changing only on a 1UP; every S3K fixture was regenerated on `0xFE0E`. This is the same address S1 (`v_vblank_word`) and S2 (`Vint_runcount+2`) already read |
| `0xF628` | u16be | `Lag_frame_count` (times V-int routine 0 ran) | CSV `lag_counter`. A REAL RAM read (AIZ arms at `0064` = 100 pre-arm lag frames), unlike S2's constant `0` |
| `0xF636` | u32be | `RNG_seed` | metadata `rng_seed` (captured at arm); `air_countdown_state.rng_seed` (per frame) |
| `0xFE6E` | `0x42` bytes | `Oscillating_table` (control word + 16 (value,delta) word pairs) | `oscillation_state.osc_table`: 66 bytes hex-dumped `%02X` each into one 132-char string |
| `0xEE26` | u16be | `Pos_table_index` | `cpu_state.pos_table_index` |
| `0xE380` | u16be + words | `Collision_response_list` (count word = payload BYTE count, then word OST addresses at `0xE382+`) | `collision_response_list_end_of_frame` (count clamped to `0x7E`; entries valid when inside the OST range — no alignment requirement, slot = floor((addr−0xB000)/0x4A)) |
| `0xE400` / `0xE500` | bytes | `Stat_table` / `Pos_table` (Tails CPU delay buffers) | hook-only paths (v6.5/v6.21) — never read in lightweight mode |

Tails CPU global block (`cpu_state` / `cpu_state_snapshot`):

| Address | Width | skdisasm name |
|---|---|---|
| `0xF700` | u16be | `Tails_CPU_interact` (RAM addr of object Tails stood on) |
| `0xF702` | u16be | `Tails_CPU_idle_timer` (legacy snapshot name `control_counter`) |
| `0xF704` | u16be | `Tails_CPU_flight_timer` (legacy `respawn_counter`) |
| `0xF708` | u16be | `Tails_CPU_routine` |
| `0xF70A` / `0xF70C` | u16be | `Tails_CPU_target_X` / `_Y` |
| `0xF70E` | u8 | `Tails_CPU_auto_fly_timer` (legacy snapshot name `interact_id`!) |
| `0xF70F` | u8 | `Tails_CPU_auto_jump_flag` (legacy `jumping`) |

`aiz_handoff_terrain_state` poll extras: `0xEEC8` u16be draw-delayed
position, `0xEECA` u16be draw-delayed row count, `0xEE33` u8
`Dynamic_resize_routine`, `0xF76C` u8 `Object_load_routine`, `0xF710` u8
`Rings_manager_routine`, `0xFF04` u8 `Kos_modules_left`.

**Deliberately NOT read:** no water RAM (`Water_level` `$F646` etc.) —
underwater state is captured only via player status bit 6; no zone-set
RAM — the S3KL/SKL zone-set is derived from `zone_id` on the replay
side; no S2-style player history buffers in lightweight mode.

### 1.2 Character object blocks (OST slots 0 and 1) — S3K layout

S3K OST: base `0xB000` (`Player_1`), slot size **`0x4A`**, **110 slots**
(`0xB000`–`0xCFCC`). `SIDEKICK_BASE = 0xB04A` (`Player_2`). Dynamic
objects at slots 3..92 (`OBJ_DYNAMIC_START = 3`, `OBJ_DYNAMIC_COUNT =
90`). Fixed `Breathing_bubbles` = slot 94 = `0xCB2C`,
`Breathing_bubbles_P2` = slot 95 = `0xCB76`. Positions are 32-bit: high
word pixel, low word subpixel. **Do NOT assume S2 offsets** (S2 stride is
`$40`; S2 status is `+0x22`):

| Offset | Width | Name | CSV / aux use |
|---|---|---|---|
| `0x00` | u32be | object code (routine pointer) | presence check (`!= 0`); object identity (`object_code`/`object_type`) |
| `0x04` | u8 | `render_flags` | `sidekick_interact_object`; object dumps |
| `0x05` | u8 | routine byte | CSV `*_routine`; aux `routine` |
| `0x06` / `0x07` | u8 | `height_pixels` / `width_pixels` | `sidekick_interact_object`; object dumps |
| `0x10` | u16be | `x_pos` (pixel) | CSV `*_x` |
| `0x12` | u16be | `x_sub` | CSV `*_x_sub` |
| `0x14` | u16be | `y_pos` (pixel) | CSV `*_y` |
| `0x16` | u16be | `y_sub` | CSV `*_y_sub` |
| `0x18` | s16be | `x_vel` | CSV `*_x_speed` via `uhex` |
| `0x1A` | s16be | `y_vel` | CSV `*_y_speed` via `uhex` |
| `0x1C` | s16be | `ground_vel` (inertia) | CSV `*_g_speed` via `uhex`; `routine_change.inertia` (signed decimal) |
| `0x1E` | u8/s8 | `y_radius` | `state_snapshot` (s8 decimal); `object_state`/`terrain_wall_sensor` (u8 decimal) |
| `0x1F` | u8/s8 | `x_radius` | same split as `y_radius` |
| `0x20` | u8 | `anim` | CSV `*_animation_id`; `state_snapshot.anim_id` |
| `0x22` | u8 | `mapping_frame` | CSV `*_mapping_frame` |
| `0x23` / `0x24` | u8 | `anim_frame` / `anim_frame_timer` | object dumps only |
| `0x26` | u8 | `angle` | CSV `*_angle`; ground-mode derivation; balloon `angle` extra |
| `0x28` / `0x29` | u8 | `collision_flags` / `collision_property` | collision-list / object dumps |
| `0x2A` | u8 | `status` | CSV `*_status_byte`; bit 0 facing-left, 1 in-air, 2 rolling, 3 on-object (p1-standing on objects), 4 roll-jump (p2-standing on objects), 5 pushing, 6 underwater |
| `0x2B` | u8 | `status_secondary` | `interact_state`; `air_countdown_state` |
| `0x2C` | u8 | subtype (objects) / `air_left` (players) | `object_state.subtype`; `air_countdown_state.owner_air_left` |
| `0x2E` | u8 | `object_control` | `interact_state.object_control` (the v6.3 fix — NOT `+0x2A`); spring-child `cooldown_byte` |
| `0x32` | u16be | `move_lock` (player ctrl-lock timer) | Arm gate; `state_snapshot.control_locked`; `mode_change control_locked`. On CNZ balloons the u16be here is the `base_y` extra |
| `0x34` | u8 | `invulnerability_timer` | `sidekick_interact_object` |
| `0x40` | u32be | parent/owner pointer | `air_countdown_state.owner_ptr` / child `parent_ptr` |
| `0x42` | u16be | `interact` (RAM addr of stood-on object) | CSV `*_stand_on_obj` after slot resolution; `interact_state.interact` |
| `0x46` / `0x47` | u8 | `top_solid_bit` / `lrb_solid_bit` | `terrain_wall_sensor`; `aiz_handoff_terrain_state.p1_top_solid` |

Player routines: `0x04` hurt, `0x06` death (trigger extra
`state_snapshot`).

**`stand_on_obj` slot resolution** (`interact_addr_to_slot`): read u16be
at `base+0x42`; result is `(addr − 0xB000) / 0x4A` only when `addr ∈
[0xB000, 0xB000 + 110·0x4A)` AND `(addr − 0xB000) % 0x4A == 0`;
otherwise `0` (also for `addr == 0`).

Object codes referenced by the core (lightweight) path: `0x00031754`
CNZ balloon (pre-trace snapshot id `0x41`; `angle`/`base_y` extras in
`object_appeared`/`object_near`), `0x00018164` Obj_AirCountdown child,
`0x00033836`/`0x0003385E` CNZ wire cage init/per-frame entry (either
matches for `cage_state`), `0x00032188` CNZ cylinder,
`0x000890AA`/`0x000890C8`/`0x000890D0` Clamer spring-child routines
(labels `loc_890AA_fire`/`loc_890C8_cooldown`/`loc_890D0_reset`).

### 1.3 Mode and zone tables

`GAMEMODE_SEGA = 0x00`, `GAMEMODE_TITLE = 0x04`, `GAMEMODE_LEVEL_SEL =
0x28`, `GAMEMODE_LEVEL = 0x0C`, `GAMEMODE_MASK = 0x0F`. Zone names:
0 aiz, 1 hcz, 2 mgz, 3 cnz, 4 fbz, 5 icz, 6 lbz, 7 mhz, 8 soz, 9 lrz,
0x0A ssz, 0x0B dez, 0x0C ddz, 0x0D hpz; fallback `unknown_%02x`
(lowercase hex).

---

## 2. Lifecycle summary (normative detail in s3k-profiles-and-hooks.md)

Loop: `while true do on_frame_end(); if finished → finalise+exit;
frameadvance end` — all RAM reads are end-of-frame state. **Stop
conditions are evaluated at the TOP of `on_frame_end`, POST-advance and
BEFORE writing the current row, in the Lua's exact source order** — the
stop-ordering bug found independently in both prior ports; do not
reintroduce it. Facts the core files depend on:

- Arm captures `bk2_frame_offset = emu.framecount()`, `start_x`/`start_y`
  (Player_1 u16be `+0x10`/`+0x14`), `start_zone_id`, `start_act`,
  `start_rng_seed` (u32be `0xF636`), zone name; then `open_files()`
  (writes + flushes the CSV header) and the first `write_metadata()`.
- `aiz_end_to_end` arms on the first level-family frame (`$4C`) and
  **records the arm frame itself as trace row 0**; the other two
  profiles (`Game_mode == 0x0C` AND Player_1 `move_lock == 0` AND
  `Ctrl_1_locked == 0`) return after arming so row 0 is the NEXT frame.
- `level_gated_reset_aware` additionally: discards and deletes all three
  output files on soft-reset (`Game_mode ∈ {0x00, 0x04, 0x28}`) and
  re-arms; finalises on zone-leave (`Current_zone != start_zone_id`)
  without writing the changed-zone row.
- At finish time `trace_frame == rows written == final
  trace_frame_count`. `level_gated_reset_aware` emits the `gameplay_end`
  checkpoint at `frame = trace_frame` reading current zone/act/apparent/
  mode; then flush, final `write_metadata()`, close, `client.exit()`.
- Output dir: `OGGF_TRACE_OUTPUT_DIR` (default `trace_output/`,
  separator appended); the Lua's `os.execute('mkdir "<dir>" 2>NUL')` is
  the source of the stray `tools/bizhawk/NUL` file on Linux — the native
  port just creates the directory.

---

## 3. physics.csv (CSV v7, dual-character)

### 3.1 Header (exact, single line, then `\n`; written at arm)

```
frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,player_present,player_x,player_y,player_x_speed,player_y_speed,player_g_speed,player_angle,player_air,player_rolling,player_ground_mode,player_x_sub,player_y_sub,player_routine,player_status_byte,player_stand_on_obj,player_animation_id,player_mapping_frame,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj,sidekick_animation_id,sidekick_mapping_frame
```

### 3.2 Row format (VERBATIM Lua format string, 42 fields — identical characters to the S2 recorder's)

```
"%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X\n"
```

Arguments in order (all end-of-frame reads):

| # | Column | Value |
|---|---|---|
| 1 | `frame` | `trace_frame` (row index N), hex — AIZ's last row renders `513D` |
| 2 | `input` | BK2-derived mask (§4) |
| 3 | `camera_x` | u16be `0xEE78` |
| 4 | `camera_y` | u16be `0xEE7C` |
| 5 | `rings` | u16be `0xFE20` |
| 6 | `gameplay_frame_counter` | u16be `0xFE04` (`Level_frame_counter` — live, §1.1) |
| 7 | `vblank_counter` | u16be `0xFE0E` (low word of `V_int_run_count` — live, §1.1) |
| 8 | `lag_counter` | u16be `0xF628` (`Lag_frame_count`) |
| 9 | `player_present` | **literal `1`**, never derived (the AIZ tail rows show all-zero player fields with `present=1`) |
| 10–11 | `player_x` / `player_y` | u16be `+0x10` / `+0x14` of `0xB000` |
| 12–14 | `player_x_speed` / `_y_speed` / `_g_speed` | s16be `+0x18`/`+0x1A`/`+0x1C` through `uhex` |
| 15 | `player_angle` | u8 `+0x26` |
| 16 | `player_air` | `(status & 0x02) != 0` → `1`/`0` |
| 17 | `player_rolling` | `(status & 0x04) != 0` → `1`/`0` |
| 18 | `player_ground_mode` | `0` when airborne, else `angle_to_ground_mode(angle)` (shared lib: `<=0x1F || >=0xE0` → 0; `0x20..0x5F` → 1; `0x60..0x9F` → 2; else 3) |
| 19–20 | `player_x_sub` / `_y_sub` | u16be `+0x12` / `+0x16` |
| 21 | `player_routine` | u8 `+0x05` |
| 22 | `player_status_byte` | u8 `+0x2A` |
| 23 | `player_stand_on_obj` | resolved slot (§1.2) |
| 24 | `player_animation_id` | u8 `+0x20` |
| 25 | `player_mapping_frame` | u8 `+0x22` |
| 26 | `sidekick_present` | `u32be(0xB04A) != 0` → `1`/`0` |
| 27–42 | sidekick block | same 16 fields as 10–25 from base `0xB04A`; when `sidekick_present == 0` **every field is `0`** through the same specifiers (`0000`/`00`/`0`) |

`uhex(v)`: if `v < 0` then `v + 0x10000`; print `%04X`
(two's-complement). Physics flush on rows where `N % 60 == 0`; metadata
rewritten on rows where `N % 300 == 0` (evaluated after writing row N).

### 3.3 Row/aux invariants

- Rows are contiguous `0..N−1`; final metadata `trace_frame_count == N`.
- One row per emulated frame from arming, INCLUDING the AIZ `$4C`/`$8C`
  prefix where Player RAM still holds title objects — the prefix rows are
  what the replay's phase classifier consumes (§4.1).
- Aux `"frame"` equals the CSV row being written (exceptions: pre-trace
  `-1` snapshots; the level_gated `gameplay_end` checkpoint at
  `frame == trace_frame_count`).

---

## 4. Input mask (CSV `input` column)

Shared lib `bk2_input_mask(fallback_raw, trace_row, bk2_frame_offset,
0)`. **v6.30 rule: BK2 index = `bk2_frame_offset + trace_row` for EVERY
profile, no profile-dependent adjustment** (v6.29 and earlier applied
`-1` for `aiz_end_to_end`; the AIZ fixture's physics.csv was regenerated
when this changed — never resurrect the adjustment).

- Movie loaded (always in practice): `movie.getinput(index, 1)` (P1
  pad). Mask: `0x01` Up, `0x02` Down, `0x04` Left, `0x08` Right, `0x10`
  JUMP if any of A/B/C. Keys checked as both `"P1 Up"`-style and plain
  `"Up"`-style names.
- `getinput` nil or no movie: fallback `rom_joypad_to_mask(u8(0xF604))`
  = `(raw & 0x0F) | ((raw & 0x70) != 0 ? 0x10 : 0)`. Never hit in
  fixture recording.

Printed `%04X`.

### 4.1 `ADVANCE_ONLY` row semantics (replay-side; recorder obligations)

`ADVANCE_ONLY` is a REPLAY classification
(`TraceReplayBootstrap.phaseForReplay` →
`TraceExecutionPhase.ADVANCE_ONLY`), not a recorder marker: a pre-level
prefix row (aiz_end_to_end intro, before `gameplay_start`) whose `input`
differs from the previous row while the sampled player/sidekick state
AND all three counters (`gameplay_frame_counter`, `vblank_counter`,
`lag_counter`) are byte-identical to the previous row — the replay
latches the BK2 controller snapshot and action edge without advancing
gameplay, animation, VBlank, lag, object, or oscillator state. The
recorder's obligations are exactly: (a) write a row for every frame from
arming including the prefix, (b) derive `input` from the BK2 at
`offset + row` (not ROM RAM, which lags on lag frames), and (c) record
counters/state verbatim. Any deviation breaks the replay's phase
derivation.

---

## 5. aux_state.jsonl

Full event templates, gating windows, and the per-frame emission order
are normative in [s3k-aux-events.md](s3k-aux-events.md). Core facts that
bind this document's files together:

- One JSON object per line; every line flushed on write. `vfc` on
  (almost) every event is a fresh u16be `0xFE04` read — live, and equal
  to that frame's `gameplay_frame_counter` column. `zone_act_state` and
  `checkpoint` have no `vfc`.
- Pre-trace one-shots (`cpu_state_snapshot`, `object_state_snapshot`
  with the full `0x4A`-byte `"off_%02X"` dump) are written at the start
  of the row-0 iteration, so they are the first aux lines.
- Lightweight-mode census (empirical, all three fixtures): ONLY
  poll-driven families appear — `object_state`, `object_near`,
  `air_countdown_state` (2/frame), `interact_state`, `oscillation_state`
  (1/frame), `cpu_state` (1/frame), `sidekick_interact_object`,
  `control_lock_state`, `object_appeared`/`object_removed`/`slot_dump`,
  `state_snapshot`, `mode_change`, `routine_change`, `zone_act_state`,
  `checkpoint`, `player_mode_set`, `cpu_state_snapshot`, plus windowed
  polls `aiz_fire_transition` (401, AIZ), `terrain_wall_sensor` (12,
  AIZ), `aiz_handoff_terrain_state` (9, AIZ — emitted with hook fields
  at defaults), `cage_state` (9766, CNZ), `cnz_cylinder_state` (23,
  CNZ), `collision_response_list_end_of_frame` (7, CNZ),
  `object_state_snapshot` (4, CNZ). **Zero hook-driven events** in any
  fixture; hook support is explicitly deferred (see
  s3k-profiles-and-hooks.md §2.4).
- JSON strings are built with the S3K `json_quote` helper
  (quote-wrapping + `\`/`"` escaping), not S1/S2's `json_escape`.

---

## Appendix A. Historical metadata layout (trace schema 6, superseded)

This section preserves the exact pre-hardware-timing porting history. It is
not the current metadata contract; current native metadata is defined above
and uses recorder 6.38, trace schema 7, and hardware-timing schema 2.

### A.1 v6.30 output (VERBATIM; `\n` line ends, 2-space indent)

```
{
  "game": "s3k",
  "zone": "<start_zone_name>",
  "zone_id": <start_zone_id>,
  "act": <start_act + 1>,
  "bk2_frame_offset": <bk2_frame_offset>,
  "trace_frame_count": <trace_frame>,
  "start_x": "0x<hex4>",
  "start_y": "0x<hex4>",
  "characters": ["sonic", "tails"],
  "main_character": "sonic",
  "sidekicks": ["tails"],
  "rng_seed": "0x<hex8>",
  "recording_date": "<YYYY-MM-DD>",
  "lua_script_version": "6.32-s3k",
  "trace_schema": 6,
  "csv_version": 7,
  "capture_mode": "physics_animation_aux_without_diagnostic_hooks",
  "aux_schema_extras": [<entries, each json_quote-d, joined by ", ">],
  "trace_profile": "<TRACE_PROFILE>",
  "bizhawk_version": "2.11",
  "genesis_core": "Genplus-gx",
  "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
  "notes": <json_quote(notes)>
}
```

- `capture_mode` line present ONLY in lightweight mode (all fixtures).
- `hex4`/`hex8` = uppercase `%04X`/`%08X` via the shared `hex()` helper.
- `zone_id` and `act` are decimal; `act` is **1-based**
  (`start_act + 1`).
- `characters`/`main_character`/`sidekicks` are HARDCODED literals.
- `rom_checksum` is the hardcoded Lua constant `S3K_ROM_CHECKSUM`
  (BizHawk header hash of the locked-on ROM) — never computed.
- `notes`: `aiz_end_to_end` → `AIZ intro through HCZ handoff end-to-end
  fixture`; `level_gated_reset_aware` AND `start_zone_name == "cnz"` →
  `CNZ1+CNZ2 Sonic+Tails playthrough from level-select BK2 (pause+A
  reset from AIZ)` (single line in the file); else empty string `""`.
- `aux_schema_extras` base list (order fixed): `cpu_state_per_frame`,
  `oscillation_state_per_frame`, `object_state_per_frame`,
  `interact_state_per_frame`, `velocity_write_per_frame`,
  `position_write_per_frame`, `tails_cpu_normal_step_per_frame`,
  `sidekick_interact_object_per_frame`, `control_lock_state_per_frame`,
  `sonic_record_pos_per_frame`, `air_countdown_state_per_frame`. Then:
  - `aiz_end_to_end` appends `aiz_boundary_state_per_frame`,
    `aiz_transition_floor_solid_per_frame`,
    `aiz_handoff_terrain_state_per_frame`,
    `terrain_wall_sensor_per_frame`, `aiz_ship_loop_per_frame`,
    `aiz_fire_transition_per_frame` (17 total);
  - every OTHER profile appends `cage_state_per_frame`,
    `cage_execution_per_frame`, `cnz_cylinder_state_per_frame`,
    `cnz_cylinder_execution_per_frame`,
    `solid_object_cont_entry_per_frame`,
    `collision_response_list_per_frame`,
    `collision_response_list_end_of_frame` (18 total), plus
    `cnz_event_ram_per_frame` when start zone is cnz AND
    `OGGF_S3K_CNZ_EVENT_RAM_RANGE` is set, plus `rng_call_per_frame`
    when start zone is cnz AND `OGGF_S3K_RNG_CALL_RANGE` is set
    (neither set for fixtures). MGZ advertises the CNZ families too —
    the advertisement is profile-based, not zone-based, and does NOT
    imply the events occur.

The file is (re)written at arm, on every row where `N % 300 == 0`, and
at finalisation — only the final write survives; a native port may write
it once at the end.

### A.2 Pinned fixture delta (6.32-stamped fixtures vs 6.32 output)

The three canonical fixtures were regenerated by v6.31-s3k (the
`ADDR_FRAMECOUNT` `0xFE08` → `0xFE04` fix) and again by v6.32-s3k (the
`ADDR_VBLA_WORD` `0xFE12` → `0xFE0E` fix), so the historical allowances
are gone at the source rather than tolerated in the gate:

| Fixture | Allowed differences vs fresh capture |
|---|---|
| `aiz1_to_hcz_fullrun` | `recording_date` value |
| `cnz` | `recording_date` value |
| `mgz` | `recording_date` value |

Nothing else — key order, indentation, `", "` joining inside
`aux_schema_extras`, and every other value must match byte-for-byte,
`lua_script_version` `"6.32-s3k"` included (pinned as an exact literal on
BOTH sides). No loose normalization.

Superseded allowances, for history only — do NOT reintroduce them:

- **v6.28 → v6.29** (commit `2a688288f`) removed the line
  `  "pre_trace_osc_frames": <start_gameplay_frame_counter>,` (formerly
  between `trace_frame_count` and `start_x`). Commit `4393d74c3`
  hand-removed it from the AIZ and CNZ fixtures but missed MGZ's, so the
  gate carried an MGZ-only fixture-extra-line allowance until the v6.31
  regeneration dropped the key.
- **v6.29 → v6.30** (commit `4393d74c3`) removed the `aiz_end_to_end`
  `-1` input-index adjustment (§4) and regenerated the AIZ fixture's
  `physics.csv.gz` to the new input convention. The version-string
  allowance `"6.28-s3k"` ↔ `"6.30-s3k"` that followed is likewise gone:
  fixture and port both stamp `6.32-s3k`.

---

## 7. Byte-shared vs different behavior relative to the S1/S2 ports

Shared (reuse existing native infrastructure verbatim):

- Loop/POST-advance stop-ordering model, `IGpgxHost` translation,
  `movie.getinput` alignment convention (`offset + row`), file
  encodings — S1 §2/§8.
- `oggf_trace_common.lua` helpers: `bk2_input_mask` (S3K passes
  adjustment 0 like S1/S2 since v6.30), `rom_joypad_to_mask`, `hex`,
  `angle_to_ground_mode`, `uhex` semantics, per-line aux flush.
- CSV v7 42-column dual-character header and row format string —
  identical characters to the S2 recorder's (S2 §4); only RAM sources
  differ.
- `state_snapshot` / `mode_change` / checkpoint-dedupe patterns.

Different (S3K-specific — never copy S1/S2 values):

- RAM map: players at `0xB000`/`0xB04A` with `0x4A` stride (S1 `0xD000`
  stride `0x40`; S2 `0xB000` stride `0x40`); status at `+0x2A` (S1/S2
  `+0x22`); interact at `+0x42`; move-lock at `+0x32`; 110 slots.
- `lag_counter` is a real RAM read (`0xF628` `Lag_frame_count`), unlike
  S2's constant `0`.
- `gameplay_frame_counter` reads `0xFE04` = `Level_frame_counter`,
  which is the same *semantic* read S1/S2 make (S2's own
  `Level_frame_counter` is also at `0xFE04`). This used to be an S3K
  divergence — the recorder read `0xFE08` = `Debug_placement_mode`,
  constant 0 — and v6.31-s3k removed it.
- `vblank_counter` reads `0xFE0E` = the low word of `V_int_run_count`,
  which is the same *semantic* read S1/S2 make (S2 reads its own
  `Vint_runcount` low word, at the same `0xFE0E`). This used to be an
  S3K divergence — the recorder read `0xFE12` = `Life_count`, i.e.
  `lives << 8` — and v6.32-s3k removed it.
- JSON strings via `json_quote` (S1/S2 use `json_escape` + manual
  quotes) — outputs coincide for the values used; keep the S3K call
  shape.
- Three profiles incl. `aiz_end_to_end`, which records its ARM frame as
  row 0 (all S1/S2 profiles skip the arm frame).
- The reset-aware profile finalises on ZONE-LEAVE (S2's ends on
  Game_Mode exit); level-family masking `(mode & 0x0F) == 0x0C`
  tolerates `$4C`/`$8C` transitional frames mid-trace.
- Much larger poll-driven aux vocabulary, including per-frame full-OST
  proximity scans against BOTH players (`object_state`) alongside the
  Player-1-only legacy scan (`object_near`).
- Metadata: the current native contract is `6.38-s3k`, `trace_schema` 7,
  `hardware_timing_schema` 2, plus hardcoded characters/sidekicks,
  `aux_schema_extras`, `capture_mode`, and constant `rom_checksum`.
  Committed `6.37-s3k` / trace-schema-7 / hardware-schema-1 metadata is
  historical load-only compatibility; Appendix A preserves the still older
  pre-hardware trace-schema-6 layout.

---

## 8. Historical initial native differential results

All three ROM-backed gates in `tests/S3KTraceDifferentialTests.cs` — AIZ
`aiz_end_to_end`, CNZ `level_gated_reset_aware`, MGZ
`level_gated_reset_aware` — passed on the first native-capture attempt
against this document's §0/Appendix A.2 predictions with **zero production code
changes**: the derived `bk2_frame_offset`/`trace_frame_count` pairs, the
`physics.csv`/`aux_state.jsonl` sha256 hashes, and the pinned metadata
deltas all matched exactly as specified. The spec-first approach (write the
byte-level contract from the Lua before porting, then gate against the
canonical fixtures) held for the CORE capture path with no divergence to
record here. Full native suite at that point: 275 PASS / 0 FAIL / 0 SKIP
(commit `1cf5df7f7`), rising to 277 PASS / 0 FAIL / 0 SKIP once the
adversarial-review env-variable and memory fixes below landed.

Two adversarial-review fixes followed the three gates (both output-neutral —
no fixture bytes changed):

- **Environment-variable refusal was incomplete at first cut.** The initial
  `Program.RejectUnmodeledS3kEnvironment()` covered only the hook-arming
  trio (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS`, `OGGF_S3K_RNG_CALL_RANGE`,
  `OGGF_S3K_CNZ_EVENT_RAM_RANGE`). A direct re-audit against
  `tools/bizhawk/s3k_trace_recorder.lua` found eight more variables that
  silently change output the port does not model — five polled-family
  window overrides and the two early-stop variables — now refused too. See
  `s3k-aux-events.md` §5.1 and `s3k-profiles-and-hooks.md` §2.4/§3.8 for the
  complete, corrected list.
- **Capture-runner memory footprint was not part of this spec's scope but
  turned out to matter operationally.** The canonical fixtures' full
  `aux_state.jsonl` streams are large (AIZ 125,528,736 bytes; MGZ
  185,001,526; CNZ 213,296,906), and the first capture-runner
  implementation buffered every profile's entire output in two
  `StringBuilder`s and then materialized each again via `ToString()` before
  a single `Write` — roughly 4x the aux stream size in peak managed memory.
  Fixed by streaming `aiz_end_to_end`/`gameplay_unlock` straight to the
  injected writers (only `level_gated_reset_aware` can discard a
  mid-capture recording via the pause+A soft-reset path, so only it still
  buffers) and flushing the remaining buffered case in fixed-size `CopyTo`
  blocks instead of `ToString()`. No physics.csv/aux_state.jsonl byte
  changed; see `s3k-profiles-and-hooks.md` §5 for the implementation split.
