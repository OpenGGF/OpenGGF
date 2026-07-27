# S3K Standard Trace Recorder — Profiles, Hooks, and Movie Handling

Authoritative migration spec for `tools/bizhawk/s3k_trace_recorder.lua`
(v6.32-s3k at HEAD, using `tools/bizhawk/lib/oggf_trace_common.lua`) covering:

1. the recorder's **profiles** (`aiz_end_to_end`, `level_gated_reset_aware`,
   and the default `gameplay_unlock`): arm/reset/discard/stop semantics and
   how each canonical fixture's `bk2_frame_offset` derives;
2. the recorder's **hook architecture**: every `event.onmemoryexecute` /
   `event.onmemorywrite` registration, hooked addresses with skdisasm labels,
   what each callback captures and when it flushes;
3. the **main-loop structure** and S3K-specific movie/BK2 handling, including
   the fixture movies' `SyncSettings.json` vs the native `Bk2Reader`, and the
   `ADVANCE_ONLY` clarification.

**The Lua is the behavioral authority.** This document was derived by reading
the full 4957-line recorder at worktree HEAD plus the shared lib, the three
gated fixtures, and the Lua's git history for the 6.28 → 6.29 → 6.30 → 6.31
→ 6.32 version bumps. The S1 spec ([s1-trace-recorder-behavior.md](s1-trace-recorder-behavior.md))
§2 frame-alignment model and §8 file-encoding rules carry over; S3K deltas are
called out explicitly. **s3k_complete_run_recorder.lua (6.33-s3k-completerun)
is a separate later migration and is out of scope here.**

---

## 0. Canonical fixtures and byte targets

All three fixtures are stamped `lua_script_version: "6.32-s3k"`,
`trace_schema: 6`, `csv_version: 7`, and — decisively —
`capture_mode: "physics_animation_aux_without_diagnostic_hooks"`. Gunzipped
byte targets (fixtures are read-only; gunzip to temp for comparison):

| Fixture | Profile | Offset | Rows | Movie (input rows) | physics.csv sha256 | aux_state.jsonl sha256 |
|---|---|---|---|---|---|---|
| `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/` | `aiz_end_to_end` | 511 | 20798 | `s3-aiz1-2-sonictails.bk2` (21309) | `3c219725d85d64762b514f973263edced337a37cd16fb8bf50f2b0ac3b5a2a39` | `9d90d669de5b9fc0c00666ad2023a164d1d110d441b9bcc8403280d1a5d74b47` |
| `src/test/resources/traces/s3k/cnz/` | `level_gated_reset_aware` | 3171 | 42253 | `s3k-cnz-sonic-tails.bk2` (45597) | `195de5a64bd879f6d920ffe9a487931beb4f6366516587d23268b1059a7b46e2` | `17ddb988b74e8718d6e3d73a7aaefff56d077e6e5d015c7ab875a4674a94052e` |
| `src/test/resources/traces/s3k/mgz/` | `level_gated_reset_aware` | 2602 | 35912 | `s3k-mgz-sonic-tails.bk2` (38818) | `16bff6712e4228494b8aeac587006edeee9f6befc62aa7b9078a465db4e2d611` | `4ce8ee02e8e6dc1664659a494578427da0c6111e5a4c0fb88b71026b2b2c2035` |

`physics.csv` and `aux_state.jsonl` must be **byte-identical with zero
normalization**. Any difference is a native-port bug (or a mis-derived spec),
never a normalization.

### 0.1 Pinned metadata delta (recording_date only, as of 6.32-s3k)

Established empirically from the Lua git history and the fixture bytes:

- **v6.28 → v6.29** (`2a688288f` "fix(trace): remove S3K replay phase
  recorder metadata"): the single line
  `  "pre_trace_osc_frames": <start_gameplay_frame_counter>,\n` (emitted
  between `trace_frame_count` and `start_x`) was **removed** from
  `write_metadata()`, and the version string bumped. Nothing else changed.
- **v6.29 → v6.30** (`4393d74c3` "fix(trace): align fresh roster and AIZ
  inputs"): `bk2_input_mask` dropped the `aiz_end_to_end`-only `-1`
  frame adjustment (CSV `input` column now `BK2[bk2_frame_offset +
  trace_row]` for **every** profile), and the version string bumped. The same
  commit **regenerated the AIZ fixture's `physics.csv.gz`** to the new input
  convention and **hand-removed** the `pre_trace_osc_frames` line from the
  AIZ and CNZ `metadata.json` — but **not** from MGZ's.
- **v6.30 → v6.31** (`95c36166c` "fix(tools): S3K standard recorder
  frame-counter address 0xFE08 -> 0xFE04"): `ADDR_FRAMECOUNT` moved from
  `0xFE08` (`Debug_placement_mode`, dead-zero in normal gameplay) to `0xFE04`
  (`Level_frame_counter`), and the version string bumped. All three fixtures
  were regenerated on the fixed recorder (`3eebb13bf`): `physics.csv`'s
  `gameplay_frame_counter` column and every aux `vfc` /
  `oscillation_state.level_frame_counter` went from a constant `0`/`0000` to
  a live, ROM-plausible value, and the MGZ fixture's leftover
  `pre_trace_osc_frames` line (missed by the v6.29→v6.30 hand-removal) was
  dropped in the same regeneration.

The three canonical fixtures in tree today are the **regenerated, v6.32**
captures — the byte targets above are already current. The only permitted
`metadata.json` delta for a fresh v6.32 capture vs. the checked-in fixtures
is `recording_date`; `lua_script_version` is `"6.32-s3k"` on both sides and
`pre_trace_osc_frames` is absent from every fixture (retired since v6.29).
Every other byte of `metadata.json` — key order, two-space indent, hex
widths, `aux_schema_extras` element order and `", "` joining, the
`capture_mode` line, the `notes` value — must match exactly. No loose
normalization.

### 0.2 Capture environment of the fixtures

All three fixtures were captured with `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS`
**unset** (`LIGHTWEIGHT_REGEN = true`) and all `OGGF_S3K_*` window env vars,
`OGGF_TRACE_STOP_FRAME`, and `OGGF_BK2_FRAME_COUNT` unset. Verified against
the fixture aux streams: **zero hook-driven events appear in any fixture**
(see §2.4).

---

## 1. Profiles

`TRACE_PROFILE = os.getenv("OGGF_S3K_TRACE_PROFILE") or "gameplay_unlock"`.
Three values exist; the profile changes arm predicates, stop predicates,
checkpoint vocabulary, metadata `notes`, and the `aux_schema_extras` list.

### 1.1 Key RAM used by profile logic

`mainmemory` domain (68K work RAM, `$FF0000` stripped), big-endian:

| Address | Width | skdisasm name | Used for |
|---|---|---|---|
| `0xF600` | u8 | `Game_mode` | All arm/stop/discard predicates. `0x00` Sega, `0x04` Title, `0x28` LevelSelect_S2Options, `0x0C` Level; the engine ORs `$40`/`$80` during level-load handoff → `0x4C`/`0x8C`. `is_level_family_mode(m) = (m & 0x0F) == 0x0C` |
| `0xFE10` | u8 | `Current_zone` | start capture; zone-leave stop; zone-gated events |
| `0xFE11` | u8 | `Current_act` | start capture; act-transition checkpoint |
| `0xEE4F` | u8 | apparent act | checkpoints / `zone_act_state` |
| `0xB000+0x32` | u16be | P1 `move_lock` | arm predicate (level-gated), checkpoints |
| `0xF7CA` | u8 | `Ctrl_1_locked` | arm predicate (level-gated), checkpoints |
| `0xF711` | u8 | level-started flag | `gameplay_start` checkpoints |
| `0xEEC6` | u16be | `Events_fg_5` | `aiz1_intro_refresh_begin` checkpoint |
| `0xF636` | u32be | RNG seed | metadata `rng_seed` (captured at arm) |
| `0xFE04` | u16be | `Level_frame_counter` | CSV `gameplay_frame_counter`; aux `vfc`. Was `0xFE08` (`Debug_placement_mode`, dead-zero) before v6.31-s3k — the label in this row was always `Level_frame_counter`, the address was not |
| `0xFE0E` | u16be | low word of `V_int_run_count` (the `ds.l` at `0xFE0C`) | CSV `vblank_counter`. Was `0xFE12` (`Life_count`, i.e. `lives << 8`) before v6.32-s3k — the label in this row was always “VBlank word”, the address was not |
| `0xF628` | u16be | lag frame count | CSV `lag_counter` (S3K reads a real counter, unlike S2's constant 0) |

Player bases: `PLAYER_BASE = 0xB000`, `SIDEKICK_BASE = 0xB04A`
(S3K OST slots are `$4A` bytes — **not** S2's `$40`; never reuse S2 offsets).

### 1.2 `gameplay_unlock` (default; no gated fixture)

- **Arm:** `Game_mode == 0x0C && u16be[0xB032] == 0 && u8[0xF7CA] == 0`.
  The arm frame is **not** recorded (function returns after arming).
- **Stop:** first post-advance frame with `Game_mode != 0x0C` finalizes
  without recording that row. Plus the global movie stops (§3).
- No profile checkpoints beyond `zone_act_state`.

### 1.3 `aiz_end_to_end` (AIZ1 → AIZ2 → HCZ handoff)

- **Arm:** `movie.isloaded() AND is_level_family_mode(Game_mode)` — i.e. the
  first frame `Game_mode` leaves SEGA/TITLE into `{0x0C, 0x4C, 0x8C}`. This
  captures the AIZ1 vine-drop intro from its very first (still-transitional,
  `0x4C`) frame while discarding title frames whose player RAM holds latched
  demo values.
- **Arm frame IS recorded as trace row 0** — uniquely among the profiles, the
  code falls through after arming instead of returning. Fixture evidence:
  `intro_begin` checkpoint at frame 0 with `game_mode: 76` (`0x4C`).
- **Offset 511 derivation:** `bk2_frame_offset := emu.framecount()` at the
  arm moment = number of completed emulator frames when `Game_mode` first
  reads as level-family, i.e. the Sega-splash + title prefix of
  `s3-aiz1-2-sonictails.bk2` is exactly 511 frames. Consequently row N's
  **state** was produced by BK2 input row `offset + N - 1` (the arm frame
  consumed row 510) — one earlier than in the other profiles. The CSV
  `input` column is still `BK2[offset + N]` (§1.6).
- **No `Game_mode != 0x0C` stop:** the profile is exempt from the
  gameplay-left check, so it survives the AIZ1→AIZ2 in-place reload
  (`0x8C` frames), the fake-fire transition, and the AIZ2→HCZ handoff.
  It ends **only** via the movie stops (§3.2). For the fixture:
  `511 + 20798 = 21309 = movie.length()`, so the run ends on the length
  check / FINISHED (same iteration), with row 20798 never recorded.
- **Coverage / checkpoints** (each `emit_checkpoint_once`, keyed by name):
  - `intro_begin` — frame 0 (fixture: F0, mode `0x4C`)
  - `gameplay_start` — first frame with `u8[0xF711] != 0 && mode == 0x0C &&
    move_lock == 0 && Ctrl_1_locked == 0` (fixture: F1386)
  - `aiz1_intro_refresh_begin` — zone 0, act 0, `Events_fg_5 != 0`
    (fixture: F1537)
  - `aiz2_reload_resume` — zone 0, act 1, apparent act 0 (fixture: F5496)
  - `aiz2_main_gameplay` — zone 0, act 1, locks clear (fixture: F5496)
  - `hcz_handoff_complete` — zone 1, act 0, locks clear (fixture: F20769,
    mode `0x8C`)
- Metadata `notes`: `"AIZ intro through HCZ handoff end-to-end fixture"`.
- Profile-specific `aux_schema_extras` tail (§0.1 fixture shows exact list):
  `aiz_boundary_state_per_frame`, `aiz_transition_floor_solid_per_frame`,
  `aiz_handoff_terrain_state_per_frame`, `terrain_wall_sensor_per_frame`,
  `aiz_ship_loop_per_frame`, `aiz_fire_transition_per_frame`.

### 1.4 `level_gated_reset_aware` (CNZ, MGZ)

- **Arm:** identical to `gameplay_unlock` (`mode == 0x0C`, P1 `move_lock`
  word 0, `Ctrl_1_locked` 0). **Not zone-gated.** Arm frame NOT recorded;
  row 0 is the next completed frame.
- **Discard-and-reset:** while `started`, if `Game_mode` reads `0x00` (Sega),
  `0x04` (Title), or `0x28` (LevelSelect) the recording is **discarded**:
  both output files are closed and **deleted** (`physics.csv`,
  `aux_state.jsonl`, `metadata.json` removed from the output dir), *all*
  recorder state (frame counters, offset, checkpoint/dedup sets, prev-state
  latches, per-frame hook accumulators) resets, and the recorder re-arms.
  The shipped output is therefore always the **last** armed segment.
- **Zone-leave stop:** while `started`, the first post-advance frame with
  `u8[0xFE10] != start_zone_id` finalizes; that frame's row is NOT recorded.
  This is how both fixtures end: CNZ zone 3 → 5 (ICZ handoff) at trace frame
  42253; MGZ zone 2 → 3 (CNZ handoff) at 35912 — both with `game_mode 0x8C`.
  Act transitions within the zone (CNZ1→CNZ2 at F16669) do NOT stop it.
- **No `Game_mode != 0x0C` stop** (exempt like `aiz_end_to_end`); mid-zone
  transitional `0x4C`/`0x8C` frames keep recording.
- **Offset derivations:** `bk2_frame_offset := emu.framecount()` at the
  (final) arm. CNZ 3171: the movie plays AIZ gameplay first (which arms and
  is then discarded by the pause+A soft reset to title — the metadata
  `notes` records this), navigates level select (`0x28` frames keep the
  recorder disarmed and also discard any prior armed state), and re-arms
  when CNZ1 gameplay unlocks after 3171 completed frames. MGZ 2602: same
  level-select route, arming when MGZ1 gameplay unlocks.
- **Checkpoints:**
  - `gameplay_start` — gated on `actual_zone_id == 3` (a CNZ-literal in the
    Lua!), `mode == 0x0C`, level-started, locks clear. Fixture: CNZ F0.
    **MGZ never emits it** (zone 2) — the MGZ fixture's only checkpoint is
    `gameplay_end`. Reproduce this quirk exactly; do not "fix" it.
  - `act_transition_to_cnz2` — edge (zone 3, act 0) → (zone 3, act 1) using
    the previous frame's zone/act latches. Fixture: CNZ F16669.
  - `gameplay_end` — emitted in the **finalisation path** (not
    `on_frame_end`), with `frame = trace_frame` (== row count, one past the
    last recorded row) and the zone/act/apparent-act/game_mode read *at
    finalize time* (fixture: CNZ `{42253, zone 5, mode 140}`, MGZ
    `{35912, zone 3, mode 140}`). Emitted only if `aux_file` is still open.
- Metadata `notes`: `"CNZ1+CNZ2 Sonic+Tails playthrough from level-select
  BK2 (pause+A reset from AIZ)"` **only when `start_zone_name == "cnz"`**;
  otherwise `""` (MGZ fixture).
- Profile `aux_schema_extras` tail (the non-AIZ branch): `cage_state_per_frame`,
  `cage_execution_per_frame`, `cnz_cylinder_state_per_frame`,
  `cnz_cylinder_execution_per_frame`, `solid_object_cont_entry_per_frame`,
  `collision_response_list_per_frame`, `collision_response_list_end_of_frame`;
  plus `cnz_event_ram_per_frame` iff zone is CNZ and
  `OGGF_S3K_CNZ_EVENT_RAM_RANGE` set; plus `rng_call_per_frame` iff zone is
  CNZ and `OGGF_S3K_RNG_CALL_RANGE` set. (Fixtures: neither env set — the
  first seven only.)

### 1.5 `zone_act_state` (all profiles)

Emitted whenever the tuple `(zone, act, apparent_act, game_mode)` differs
from the previously emitted tuple (first recorded frame always emits).

### 1.6 CSV `input` column (v6.30 convention)

`input = bk2_input_mask(raw, trace_row)` reads
`movie.getinput(bk2_frame_offset + trace_row, 1)` — **no profile-dependent
adjustment** — and folds to the engine mask (`U=1 D=2 L=4 R=8`, any of
A/B/C → `0x10`; Start is NOT represented). The RAM fallback
(`rom_joypad_to_mask(u8[0xF604])`) only applies with no movie loaded or when
`movie.getinput` returns nil for the requested row — never in fixture
capture (every recorded row satisfies `offset + row < movie.length()`). Because the `aiz_end_to_end` arm frame is recorded as
row 0, that profile's row N `input` is the input consumed by row N+1's
state (one-ahead); for level-gated profiles it is the input that produced
row N's state. Replay owns compensating for this; the recorder must not.

---

## 2. Hook architecture

### 2.1 The master gate

```lua
DIAGNOSTIC_HOOKS_ENABLED = os.getenv("OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS") == "1"
LIGHTWEIGHT_REGEN = not DIAGNOSTIC_HOOKS_ENABLED
```

**Every** `event.onmemoryexecute` / `event.onmemorywrite` registration sits
inside `if not LIGHTWEIGHT_REGEN then ... end` at script load (after the
banner print, before the main loop). In lightweight mode (the fixture mode)
no callback is ever registered, and `metadata.json` gains the
`capture_mode: "physics_animation_aux_without_diagnostic_hooks"` line.
All hook callbacks additionally self-gate on `aux_file ~= nil` and
`started`, most on a frame window and/or a register-identity check.

Addresses: `onmemoryexecute` hooks use ROM PC addresses (< `0x200000`, all in
the S&K half of the locked-on image). `onmemorywrite` hooks use **full-bus**
RAM addresses (`0xFF0000 | ram_offset`), with both bytes of each word hooked.

### 2.2 Execution hooks (`event.onmemoryexecute`)

| Group | PC(s) | skdisasm label (sonic3k.asm line) | Captures | Flush event |
|---|---|---|---|---|
| `CAGE_DIAG` | `0x338C4`, `0x339A0`, `0x33ADE`, `0x33B1E`, `0x33B62` | `sub_338C4` (69877), `loc_339A0`, `loc_33ADE`, `loc_33B1E`, `loc_33B62` (CNZ wire cage branches) | branch tag, PC, a0/a1/a2 (cage/player/state addrs), d5/d6, state byte `1(a2)`, player status + object_control, cage status. No frame window | `cage_execution` (one event listing all hits, per frame) |
| `V65` | `0x13DD0`, `0x13EB8`, `0x14A0A`, `0x14B7A` | `loc_13DD0` (26696), `loc_13EB8` (26784), `loc_14A0A` (27802), `loc_14B7A` (27957) — Tails CPU normal-follow / input-accel path | gated `a0 == 0xB04A`; delayed Stat/Pos-table reads at `(Pos_table_index-0x44)&0xFF`, branch classification, pre/post path vel/status | `tails_cpu_normal_step` (single merged per-frame record; only if a hook fired this frame) |
| `V66` | `0x14F08`, `0x14F4A`, `0x14F56`, `0x14F5C`; `0x1F912`, `0x1F982` | `Tails_Check_Screen_Boundaries` (28407) entry/return/kill/clamp; `AIZTree_SetPlayerPos` (43781) entry / post-y_vel | zone 0 + windows `{[4660,4679],[7549,7560]}` (`OGGF_S3K_AIZ_BOUNDARY_RANGE`); camera min/max X/Y (`0xEE14/16/18/1A`), Tails pre/post snapshots, boundary action | `aiz_boundary_state` (only if `seen`) |
| `V67_AIZ` | `0x1E2E0`, `0x1E2F4`, `0x1E42E`, `0x1E44C`, `0x1E4A0`, `0x1E4D4` | SolidObjectTop standing-exit/standing/first-check/first-vertical; `RideObject_SetRide` body; return | zone 0 + window `[5408,5438]`; gated a0 == `Obj_AIZTransitionFloor` (object_code `0x0004FE38`, label at 104782), a1 ∈ {P1, P2}; per-player path + d1/d2/d3 | `aiz_transition_floor_solid` (only if `seen`) |
| `V69_AIZ` | `0x0F7F8`; reuses `0x1E44C`, `0x1E4A0` | `Sonic_CheckFloor` return (19839-19891); SolidObjectTop vertical/landing | zone 0 + window `[5430,5438]`; CheckFloor-return hook gated a0 == P1, the two SolidObjectTop hooks gated a0 == `Obj_AIZTransitionFloor` && a1 == P1; floor distance/angle (d1/d3), probe x/y, solid gate pre_y/surface_y/delta | enriches `aiz_handoff_terrain_state` — **which emits per-frame in-window even with no hooks** (§2.4) |
| `V67_CNZ` | `0x324C0`, `0x32538`, `0x32594`, `0x32604`, `0x3260A`; `0x1E1CA`, `0x1E1F2` | `sub_324C0` (67990) + cylinder branches; `MvSonicOnPtfm` (41647) pre/return | window `[4490,4512]` (`OGGF_S3K_CNZ_CYLINDER_RANGE`); gated a1 == Tails AND a0's object_code == `0x00032188` (Obj_CNZCylinder); regs d2/d4/d5/d6, per-player slot bytes, Tails pos/subpix/status | `cnz_cylinder_execution` |
| `V611_SOLID` | `0x1DF90` | `SolidObject_cont` (41399) | windows `{[4788,4792],[7600,7625]}` (`OGGF_S3K_SOLID_CONT_RANGE`); a0/a1/d1/d2, player y_radius + default_y_radius (`+0x16`), player/solid x/y | `solid_object_cont_entry` |
| `V615_CRL` | `0x10440` | `Touch_Process` (20655) | zone 3 + window `[618,624]` (`OGGF_S3K_CRL_RANGE`); walks `Collision_response_list` (`0xE380`: byte-count word capped `0x7E`, then word OST addrs), Clamer spring-child scan (object_codes `0x890AA/0x890C8/0x890D0`) | `collision_response_list_per_frame` (one per Touch_Process hit) |
| `V618_AIZ_SHIP` | `0x502CA`, `0x502FA`, `0x50318`, `0x50324`, `0x5033A`, `0x50348` | `AIZ2_DoShipLoop` (105205), camera-store, `sub_50318` (105236), branches, return | window `[16320,16335]` (`OGGF_S3K_AIZ_SHIP_LOOP_RANGE`); label, PC, a1→character, d0/d1, camera X/min/max, `Events_bg+2`, player x/y/gvel/xvel/anim/status | `aiz_ship_loop` |
| `V621_SONIC_RECORD` | `0x10D80` | `Sonic_RecordPos` (22119) | gated a0 == P1; `Pos_table_index & 0xFF` (`0xEE26`), `Ctrl_1_logical` (`0xF602` u16be), `Ctrl_1_locked`, raw `Ctrl_1` (`0xF604` u16be), P1 object_control/status/x/y. No window | `sonic_record_pos` |
| `V625_RNG_CALLS` | `0x1D24` | `Random_Number` (2992) | **armed only when `OGGF_S3K_RNG_CALL_RANGE` set**; zone 3 + window; reconstructs result/next-seed from seed (`0xF636`) via the ROM's shift/add algorithm, caller PC from `(A7)`, a0/a1 object contexts, source label heuristics | `rng_call` |

WRITE_DIAG velocity/position hooks (`event.onmemorywrite`, full-bus):

| Addresses | Target | Windows (defaults) | Flush event |
|---|---|---|---|
| `0xFFB062/63`, `0xFFB064/65` | Tails x_vel / y_vel | `{[3640,3660],[7549,7560]}` (`OGGF_S3K_VELOCITY_WRITE_RANGE`, single or `;`-multi) | `velocity_write` (character `tails`; PC + post-write value per hit) |
| `0xFFB010/11`, `0xFFB014/15`; `0xFFB05A/5B`, `0xFFB05E/5F` | Sonic x_pos / y_pos; Tails x_pos / y_pos | `{[4788,4792],[7549,7560],[7600,7625],[16320,16335]}` (`OGGF_S3K_POSITION_WRITE_RANGE`) | `position_write` — flushed as **sonic first, then tails**, each only if it has hits; each hit records PC, post-write value, and a1/a0 at write time |

Hook callbacks read registers via `emu.getregister("M68K ...")` (pre-fetch
register file at the hooked instruction) and accumulate into per-frame Lua
tables; **all flushing happens in `on_frame_end` in the fixed order of §3.4**,
so one aux line aggregates a frame's hits.

### 2.3 Poll-driven events that need NO hooks (present in fixtures)

These run every recorded frame in `on_frame_end` regardless of the hook gate
(some window/zone-gated); the fixture aux streams confirm each:

- `cpu_state` (Tails CPU block `0xF700..0xF70F` + `Ctrl_2_logical`
  `0xF66A/0xF66B` + `Pos_table_index`), `oscillation_state`
  (`0xFE6E` × `0x42` bytes + `Level_frame_counter`), `object_state`
  (every OST slot 1..109 within 160 px of P1 **or** P2), `interact_state`
  (P1 always, P2 if present), `sidekick_interact_object` (P2 present only),
  `air_countdown_state` (2/frame: fixed slots 94/95 at `0xCB2C`/`0xCB76` +
  visible `Obj_AirCountdown` children), `control_lock_state` (on change of
  the u8 `0xF7CA`/`0xF7CB` locked bytes or the u16be `0xF602`/`0xF66A`
  logical latches, plus forced baseline every 60 frames), `state_snapshot` (every 60 frames + on air-flip and
  hurt/death routine change), `mode_change`, `routine_change`,
  `player_mode_set` (on `0xFF08` change), `object_appeared`/`object_removed`/
  `object_near`/`slot_dump` (scan_objects, slots 1..109, proximity to P1
  only), `zone_act_state`, `checkpoint`.
- Window-gated polls: `aiz_fire_transition` (aiz profile, zone 0, frames
  5200-5600 → 401 events in the AIZ fixture), `aiz_handoff_terrain_state`
  (zone 0, frames 5430-5438 → 9 events, emitted with
  `sonic_floor_seen:false` etc. when no hooks armed), `terrain_wall_sensor`
  (zone 0, frames 7549-7560 → 12 events), `cnz_cylinder_state` (frames
  4490-4512, per OST slot holding `0x00032188` → 23 events in CNZ),
  `collision_response_list_end_of_frame` (zone 3, frames 618-624 → 7 events
  in CNZ), `cage_state` (per OST slot whose object_code is `0x00033836` or
  `0x0003385E` → 9766 events in CNZ), `cnz_event_ram` (env-armed only;
  absent from fixtures).
- Pre-trace one-shots on the **first recorded frame**, before row 0's CSV
  write: `cpu_state_snapshot` (`"frame":-1`) then `object_state_snapshot`
  (`"frame":-1`, one per dynamic slot 3..109 whose object_code is
  `0x00031754` = CNZ balloon — 4 in the CNZ fixture, 0 in AIZ/MGZ).

### 2.4 Verdict for the native port

Fixture aux event census (zero counts omitted): none of `cage_execution`,
`velocity_write`, `position_write`, `sonic_record_pos`, `rng_call`,
`tails_cpu_normal_step`, `aiz_boundary_state`, `aiz_transition_floor_solid`,
`solid_object_cont_entry`, `collision_response_list_per_frame`,
`cnz_cylinder_execution`, `aiz_ship_loop` appears in any of the three
fixtures. All are env-gated OFF (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` unset;
`rng_call` additionally requires its own env var).

**Therefore the native S3K standard port does not need M68K
execute/memory-write callbacks to reproduce the gated fixtures.** The
`metadata.json` must still advertise the hook event names in
`aux_schema_extras` (the Lua does so unconditionally) and must emit the
`capture_mode` line. Native support for
`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1` is **explicitly deferred**: it would
require extending `GpgxHost` with the BizHawk core's exec/mem callback
surface (LibGPGX `mem_cb`/`ExecCallback` hooks as wired by EmuHawk), and no
gate exercises it. The native CLI should refuse (or loudly no-op) a request
for diagnostic hooks rather than silently produce hook-less output that
claims otherwise.

**Stage C decision (implemented):** native exec/memwrite hook capture is a
documented no-op. The unit gates in `tests/S3KHookAbsenceTests.cs` pin this
decision to the fixture bytes: per gated fixture they assert zero aux lines
whose `event` value is any of the 13 deferred families (§2.2 plus
`cnz_event_ram`), anchor non-vacuously on the per-frame poll counts
(`cpu_state`/`oscillation_state` == row count, one `cpu_state_snapshot`),
verify the 9 AIZ `aiz_handoff_terrain_state` skeletons keep
`sonic_floor_seen:false` / `solid_vertical_seen:false`, and require the
lightweight `capture_mode` line in each `metadata.json`. If a fixture is
ever regenerated with `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`, those gates
fail — the signal that the GpgxHost callback surface must then be built
instead of deferred.

The CLI refusal is **not** limited to the hook switch. `OGGF_TRACE_STOP_FRAME`
and `OGGF_BK2_FRAME_COUNT` (§3.1) truncate the capture, and the frame-window
overrides belonging to the *frame-polled* families the port does implement
change `aux_state.jsonl` with the hook switch off — the exact mode every
fixture was captured in. All of those are refused too; the complete list, the
three classes, and the deliberately-not-refused hook-gated window overrides
are tabulated in `s3k-aux-events.md` §5.1.

---

## 3. Main loop, stop ordering, movie/BK2 handling

### 3.1 Loop shape

```lua
while true do
    on_frame_end()          -- inspects the frame that just COMPLETED
    if finished then <finalize; client.exit(); break> end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
```

Identical model to S1 §2: every RAM read is **post-advance**;
`emu.framecount()` = frames completed; the completed frame consumed BK2 row
`emu.framecount() - 1`. The first `on_frame_end()` runs before any advance.
`HEADLESS` is hardcoded `true`. At load: `mkdir OUTPUT_DIR`
(`OGGF_TRACE_OUTPUT_DIR` or `trace_output/`, slash-normalized), frame limit
off, speed 6400, sound off, invisible emulation (guarded); prints suppressed
when `OGGF_TRACE_QUIET=1`.

### 3.2 `on_frame_end` source order — stops are POST-advance

Exact source order (the S1/S2-port lesson: stop predicates are evaluated
after the advance, in this order, and a stop **never records the row it
fires on**):

1. `if finished then return end`.
2. *(started only)* `OGGF_TRACE_STOP_FRAME`: `trace_frame >= stop` → finish.
3. *(started only)* `OGGF_BK2_FRAME_COUNT` (> 0):
   `bk2_frame_offset + trace_frame >= count` → finish.
4. *(started only)* `not movie.isloaded()` → finish **before any RAM read**
   (memory-domain safety on movie unload).
5. `game_mode := u8[0xF600]`.
6. *(level-gated, started)* discard-and-reset on
   `game_mode ∈ {0x00, 0x04, 0x28}`: close + **delete** the three output
   files, reset all state, `return` (re-arm next iterations).
7. *(level-gated, started)* zone-leave: `u8[0xFE10] != start_zone_id` →
   finish. **Runs BEFORE the movie-end checks and the row write** — this is
   how both level-gated fixtures end.
8. *(not started)* arm check (§1.2-1.4). On arm: `offset :=
   emu.framecount()`; capture `start_x/y` (`0xB010`/`0xB014` u16be),
   `start_zone_id/act`, `start_rng_seed` (u32be `0xF636`),
   `start_gameplay_frame_counter` (`0xFE04`), zone name; `open_files()`
   (CSV header written + flushed); `write_metadata()` (first of many —
   rewritten every 300 frames and at finalize; only the final rewrite's
   bytes ship). Then: `aiz_end_to_end` **falls through** (arm frame = row
   0); every other profile `return`s (arm frame dropped). Still-unarmed →
   `return`.
9. *(neither aiz nor level-gated)* `game_mode != 0x0C` → finish.
10. *(movie loaded)* `end_frame_limit := movie.length()`, raised to
    `OGGF_BK2_FRAME_COUNT` when that is larger (post-movie tail mode).
    `offset + trace_frame >= end_frame_limit` → finish. Else, if no tail
    allowed and `movie.mode() == "FINISHED"` → finish. Per the S1 §2.4
    analysis of the pinned BizHawk 2.11 binaries, FINISHED appears on the
    `on_frame_end` after the advance that consumed the movie's **last**
    input row; for the AIZ fixture both predicates coincide at trace frame
    20798 (`511 + 20798 = 21309`), which is why the fixture has exactly
    20798 rows. **The frame fed by the movie's final input row is never
    recorded** — the same movie-end stop-ordering bug found independently in
    both prior ports; do not reintroduce it.
11. First recorded frame only: pre-trace snapshots (§2.3) and a **recapture**
    of `start_gameplay_frame_counter` from `0xFE04` (unifies the
    arm-frame-recorded vs arm-frame-dropped profiles; since v6.29 this value
    no longer reaches metadata but the recapture still happens).
12. Write CSV row `trace_frame` (§3.3), flush every 60 rows, rewrite
    metadata every 300 rows.
13. Aux cascade in the fixed order of §3.4.
14. `trace_frame += 1`.

Finalisation (main loop, after `on_frame_end` sets `finished`): for
level-gated, emit `gameplay_end` (§1.4); flush CSV; final
`write_metadata()`; close files; `client.exit()`.

### 3.3 physics.csv

Header and 42-column row format are byte-identical to the S1/S2 CSV v7
surface (S1 §3.1). S3K-specific sources: `gameplay_frame_counter` ←
`0xFE04` (`Level_frame_counter`; `0xFE08` before v6.31-s3k),
`vblank_counter` ← `0xFE0E` (low word of `V_int_run_count`; `0xFE12`
`Life_count` before v6.32-s3k), `lag_counter` ← `0xF628` (a real
counter — MUST be read, not pinned 0), `stand_on_obj` ← u16be at
`base+0x42` mapped to an OST slot index (0 unless the address is exactly
`0xB000 + slot*0x4A`, slot < 110), sidekick block from `0xB04A` with
`present := u32be[base] != 0` (all-zero sub-record when absent),
`animation_id` ← `base+0x20`, `mapping_frame` ← `base+0x22`. All hex fields
uppercase, widths per the shared format string; negative s16 values
wrapped `+0x10000`.

### 3.4 Per-frame aux emission order (byte order in the file)

`zone_act_state`/checkpoints → `player_mode_set` → mode_change block (air
[+`state_snapshot`], rolling, on_object, control_locked, routine_change
[+`state_snapshot` on hurt/death]) → `cpu_state` → V65 flush → V66 flush →
V67_AIZ flush → V69_AIZ flush (in-window poll) → `aiz_fire_transition` →
`oscillation_state` → `object_state`* → `interact_state` (sonic, tails) →
`sidekick_interact_object` → `air_countdown_state` (p1, p2) →
`cnz_cylinder_state`* → cylinder-execution flush → `cnz_event_ram` →
`cage_state`* → cage-execution flush → `velocity_write` flush →
`position_write` flush (sonic, tails) → `aiz_ship_loop` flush →
`sonic_record_pos` flush → `rng_call` flush → `solid_object_cont_entry`
flush → `state_snapshot` (every 60) → `control_lock_state` →
`terrain_wall_sensor` → `collision_response_list_*` flush →
`scan_objects` (`object_appeared`*/`object_removed`*/`object_near`*/
`slot_dump`). Every aux line ends `\n` and is flushed immediately.

### 3.5 BK2 movies: SyncSettings, input layout, reader compatibility

All three fixture movies carry **byte-identical** `SyncSettings.json`:

```
UseSixButton:false, ControlTypeLeft:1, ControlTypeRight:1, Region:0,
ForceVDP:0, LoadBIOS:false, Overscan:3, GGExtra:false, SMSFMSoundChip:1,
GenesisFMSoundChip:0, Filter:0, LowPassRange:26214, LowFreq:880,
HighFreq:5000, LowGain/MidGain/HighGain:1.0, BackdropColor:4294902015,
SpritesAlwaysOnTop:false
```

Checked against `src/Bk2/Bk2Reader.cs` at HEAD: every field is within the
reader's pinned values/ranges (`Overscan` 3 ∈ [0,3], `GenesisFMSoundChip`
0 ∈ [0,3], all pinned equalities match). **No sync-setting tolerance changes
are required for the S3K migration.** Two S3K-relevant reader facts:

- The LogKey is the standard 3-group `System|P1|P2` layout the reader
  already validates. All 45597 + 38818 + 21309 input rows across the three
  movies have `..` system and an **all-idle P2 group** (verified). The
  reader's hard throw on any pressed P2 button (`P2Active`) is therefore
  not triggered — but it remains a live constraint: a future S3K movie with
  real controller-2 input (manually-driven Tails) will need P2 forwarding
  through `IGpgxHost` before it can be read. Do not silently drop P2.
- `Header.txt` is `Core Genplus-gx` / `Platform GEN` (accepted); the `SHA1`
  header value is `C5B1C655C19F462ADE0AC4E17A844D10` — a 32-hex (MD5-shaped)
  digest, matching the Lua's `S3K_ROM_CHECKSUM` constant and the metadata
  `rom_checksum` field. The reader copies it opaquely; keep it opaque.
  **ROM identity validation is separate**: `src/Bootstrap/RomIdentity`
  needs a new S3K validator keyed on the locked-on combined image
  SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` behind the new
  `S3K_ROM_PATH` env var (SKIP-when-absent convention), and `metadata.json`
  must still print the literal `rom_checksum` string above.

### 3.6 Dual-half ROM notes

The recorder itself never reads ROM — it is RAM + PC-hook only. The
locked-on image's S&K half (`< 0x200000`) contains every hooked PC in §2.2;
comments referencing `s3.asm` (e.g. `AIZ1_FireRise`, s3.asm:70383) are
research annotations only. The native port needs no half-aware logic beyond
loading the combined ROM the movies were recorded against.

### 3.7 `ADVANCE_ONLY` clarification

`ADVANCE_ONLY` is a **replay-side execution phase**
(`com.openggf.trace.TraceExecutionPhase`), derived by
`TraceReplayBootstrap.phaseForReplay` from the recorded counter columns and
pre-level-prefix predicates — the recorder never writes it. Consequence for
the port: the recorder must faithfully record **every** post-arm frame as a
row (including lag frames, `0x4C`/`0x8C` transition frames, and the
pre-gameplay AIZ intro prefix) with exact `gameplay_frame_counter`,
`vblank_counter`, and `lag_counter` values, because the replay classifies
rows into `FULL_LEVEL_FRAME` / `VBLANK_ONLY` / `ADVANCE_ONLY` (input-latch
rows) purely from those recorded values. Never skip, merge, or synthesize
rows to "help" the replay. (v6.29 removed the last recorder-side phase
knob, `pre_trace_osc_frames`, for exactly this reason.)

### 3.8 Environment variables (complete)

| Var | Effect | Fixture state |
|---|---|---|
| `OGGF_S3K_TRACE_PROFILE` | profile selection | `aiz_end_to_end` / `level_gated_reset_aware` |
| `OGGF_TRACE_OUTPUT_DIR` | output dir | set by launcher |
| `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` | `1` registers all hooks | unset |
| `OGGF_TRACE_STOP_FRAME` | early stop at trace frame | unset |
| `OGGF_BK2_FRAME_COUNT` | input-row cap / post-movie tail | unset |
| `OGGF_TRACE_QUIET` | `1` silences prints | (byte-neutral) |
| `OGGF_BIZHAWK_LIB` | shared-lib dir override | launcher |
| `OGGF_S3K_VELOCITY_WRITE_RANGE`, `OGGF_S3K_POSITION_WRITE_RANGE`, `OGGF_S3K_SOLID_CONT_RANGE`, `OGGF_S3K_AIZ_BOUNDARY_RANGE` (+ legacy `OGGF_S3K_AIZ_BOUNDARY_FRAME_START/END`), `OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/END`, `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START/END`, `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`, `OGGF_S3K_AIZ_SHIP_LOOP_RANGE`, `OGGF_S3K_AIZ_FIRE_RANGE`, `OGGF_S3K_CNZ_CYLINDER_RANGE`, `OGGF_S3K_CNZ_EVENT_RAM_RANGE` (armer), `OGGF_S3K_CRL_RANGE`, `OGGF_S3K_RNG_CALL_RANGE` (armer) | per-event frame windows; `<s>-<e>` or `;`-separated multi-window where noted | all unset (defaults active) |

---

## 4. The ten trickiest invariants

1. **Arm-frame asymmetry:** `aiz_end_to_end` records its arm frame as trace
   row 0 (falls through after arming); `level_gated_reset_aware` and
   `gameplay_unlock` drop the arm frame (row 0 = next completed frame). So
   AIZ row N's state consumed BK2 row `offset+N-1`, level-gated row N's
   consumed `offset+N` — yet since v6.30 the CSV `input` column is
   `BK2[offset+N]` for **all** profiles, making the AIZ input column
   one-ahead of its own row's state. Replay compensates; the recorder must
   not.
2. **Stops are post-advance, pre-row-write, in exact source order** (§3.2):
   env stops → movie-unloaded → discard-reset → zone-leave → arm →
   gameplay-left → movie length/FINISHED. The row a stop fires on is never
   written; the movie's final input row's frame is never recorded (AIZ:
   `511 + 20798 == 21309` exactly).
3. **Zone-leave beats movie-end and is level-gated-only:** both level-gated
   fixtures end on `zone != start_zone` during a `0x8C` transitional frame
   (CNZ→ICZ at 42253, MGZ→CNZ at 35912), with `gameplay_end` carrying the
   *post-leave* zone/mode and `frame == row count`.
4. **Discard-and-reset deletes files:** on `Game_mode ∈ {0x00,0x04,0x28}`
   the level-gated profile deletes `physics.csv`/`aux_state.jsonl`/
   `metadata.json` and re-arms; shipped output is the **last** armed
   segment (CNZ offset 3171 exists only because an earlier AIZ segment was
   discarded via pause+A).
5. **Pinned metadata delta only** (§0.1): fresh-capture `metadata.json` may
   differ from fixtures solely in `recording_date` — both sides stamp the
   literal `lua_script_version: "6.32-s3k"`, and `pre_trace_osc_frames` is
   absent from every fixture (retired since v6.29, and the MGZ fixture's
   leftover line was removed in the v6.31 regeneration). Everything
   else — including `capture_mode`, `aux_schema_extras` order, `notes` —
   byte-exact. physics.csv / aux_state.jsonl: zero normalization.
6. **Hook events are env-gated OFF in all fixtures:** `capture_mode:
   physics_animation_aux_without_diagnostic_hooks`; zero
   exec/write-hook events in any fixture aux stream. The native port defers
   the GpgxHost exec/mem callback surface, but must still advertise the
   hook event names in `aux_schema_extras` unconditionally.
7. **Window-gated polls still fire without hooks** and must be reproduced:
   `aiz_handoff_terrain_state` emits 9 skeleton events (F5430-5438,
   `sonic_floor_seen:false`), `terrain_wall_sensor` 12 (F7549-7560),
   `aiz_fire_transition` 401 (F5200-5600), `cnz_cylinder_state` 23
   (F4490-4512), `collision_response_list_end_of_frame` 7 (F618-624, zone
   3), `cage_state` per active cage — all with hardcoded default windows.
8. **`gameplay_start` is zone-3-literal in the level-gated profile:** MGZ
   legitimately has no `gameplay_start`/act-transition checkpoints — its
   only checkpoint is `gameplay_end`. Reproduce the quirk; do not
   generalize it.
9. **S3K OST geometry everywhere:** slot size `0x4A`, `SIDEKICK_BASE =
   0xB04A`, 110 slots, dynamic slots 3..92, fixed Breathing_bubbles slots
   94/95; `stand_on_obj`/`interact` map to a slot only on exact
   `0xB000 + k*0x4A` alignment, else 0. `lag_counter` is a real S3K counter
   (`0xF628`), unlike S2's constant 0.
10. **Row-0 prelude ordering:** on the first recorded frame, `cpu_state_snapshot`
    (`frame:-1`) then per-balloon `object_state_snapshot` (`frame:-1`) are
    written to aux **before** row 0's CSV row and aux cascade, and
    `start_gameplay_frame_counter` is recaptured at that instant (harmless
    to metadata since v6.29, but the read order is part of the loop).

---

## 5. Native capture-runner memory behavior (post-gate addendum)

Not part of the Lua's own behavior — an implementation fact the native
`S3KTraceCaptureRunner` port had to get right to be usable on the canonical
fixtures without excessive memory, discovered running the three differential
gates in `tests/S3KTraceDifferentialTests.cs` for real: the full
`aux_state.jsonl` streams are large (AIZ 125,528,736 bytes; MGZ 185,001,526;
CNZ 213,296,906). A first cut buffered every profile's whole output in two
`StringBuilder`s and then materialized each again via `ToString()` for a
single `Write` — roughly 4x the aux stream size in peak managed memory.

The fix (`TraceStreamSink`, one per output stream) only buffers the profile
that can actually need to throw output away mid-capture:

- `aiz_end_to_end` and `gameplay_unlock` can never discard a partial
  recording, so they stream straight to the injected writers — the same
  form `S1TraceCaptureRunner` already uses. Buffering bought them nothing:
  `RunTraceCapture` stages writers and only publishes (`link(2)`s) them on
  success, so a failed capture ships nothing regardless of profile.
- `level_gated_reset_aware` is the only profile that can discard an armed
  recording mid-capture (the pause+A soft-reset path, §1.4), so it alone
  still buffers — but the buffered flush now copies the builder in
  fixed-size `char[]` blocks via `CopyTo` instead of calling `ToString()`,
  removing the second full-size contiguous copy.
- `Discard()` throws on a streaming sink instead of silently no-opping,
  which would otherwise ship a segment the Lua would have deleted.

No `physics.csv` / `aux_state.jsonl` byte changed for any of the three
canonical fixtures; this is a memory-footprint fix only, verified by a
runner test that observes the streaming/buffered split directly (mid-capture
the `gameplay_unlock` writer already holds the header and rows while the
`level_gated_reset_aware` writer is still empty) rather than trusting a
comment.
