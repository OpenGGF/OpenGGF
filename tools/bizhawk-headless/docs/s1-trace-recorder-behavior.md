# S1 Trace Recorder — Byte-Level Behavioral Specification for the Native Port

Authoritative specification for porting `tools/bizhawk/s1_trace_recorder.lua`
(v3.5, using `tools/bizhawk/lib/oggf_trace_common.lua`) to the C# headless
harness (`tools/bizhawk-headless/`). The port must produce **byte-identical**
`physics.csv` and `aux_state.jsonl`, and `metadata.json` identical except for
the `recording_date` value, against the canonical fixture
`src/test/resources/traces/s1/ghz1_fullrun/`:

- `physics.csv` sha256 `dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d311cb66`
- `aux_state.jsonl` sha256 `026794b175c7fea65491f57cbf5a83684f183b802c7fabaa15eb699e82184a86`
- `metadata.json`: `bk2_frame_offset` 840, `trace_frame_count` 3905
- The GHZ1 BK2 has 4806 input rows. Ignore the `*_retro.*` and `*.gz` variants.

Everything below is derived line-by-line from the Lua source. Where a template
is marked VERBATIM, reproduce it exactly — including hex widths, key order,
quoting style, and the absence of whitespace after JSON separators.

---

## 1. RAM address map

All reads are from the `mainmemory` domain: 68K work RAM with the `$FF0000`
base stripped, i.e. address `0xF600` here = `$FFF600` on hardware. In the
native harness these are `IGpgxHost.ReadMainRamByte(addr)` reads; multi-byte
values are **big-endian** and must be assembled from consecutive byte reads.

Widths: `u8` = unsigned byte; `s8` = signed byte; `u16be` = unsigned 16-bit
big-endian; `s16be` = signed (two's-complement) 16-bit big-endian; `u32be` =
unsigned 32-bit big-endian.

### 1.1 Global variables

| Address | Width | Name (s1disasm) | Used for |
|---------|-------|-----------------|----------|
| `0xF600` | u8 | `v_gamemode` | Start detection / end-of-level detection (`0x0C` = level) |
| `0xF604` | u8 | `v_jpadhold1` | Fallback input mask ONLY (never used when a movie is loaded — see §4) |
| `0xF602` | u8 | `v_jpadhold2` | Declared in the Lua (`ADDR_CTRL1_DUP`) but **never read** — do not port |
| `0xFE20` | u16be | ring count | CSV `rings` column |
| `0xF700` | u16be | `v_screenposx` (pixel word of the 32-bit value) | CSV `camera_x` |
| `0xF704` | u16be | `v_screenposy` (pixel word) | CSV `camera_y` |
| `0xFE10` | u8 | `v_zone` | Captured at start for metadata |
| `0xFE11` | u8 | `v_act` | Captured at start for metadata |
| `0xF636` | u32be | `v_random` | Captured at start for metadata `rng_seed` |
| `0xFE04` | u16be | `v_framecount` | CSV `gameplay_frame_counter`; aux `vfc` field |
| `0xFE0E` | u16be | VBlank word (`ADDR_VBLA_WORD`) | CSV `vblank_counter`. NOTE: this is `0xFE0E`, not `0xFE0C` (`v_vbla_count` longword — different variable) |

### 1.2 Player object block (base `0xD000`, = SST slot 0)

| Offset | Abs addr | Width | Name | Used for |
|--------|----------|-------|------|----------|
| `+0x01` | `0xD001` | u8 | `obRender` | `s1_obj64_state.render_flags` (object slots only) |
| `+0x08` | `0xD008` | u16be | `x_pos` (centre X pixel) | CSV `player_x`; aux positions |
| `+0x0A` | `0xD00A` | u16be | X subpixel (16-bit fraction) | CSV `player_x_sub` |
| `+0x0C` | `0xD00C` | u16be | `y_pos` (centre Y pixel) | CSV `player_y`; aux positions |
| `+0x0E` | `0xD00E` | u16be | Y subpixel | CSV `player_y_sub` |
| `+0x10` | `0xD010` | s16be | X velocity | CSV `player_x_speed` (uhex, §3.3) |
| `+0x12` | `0xD012` | s16be | Y velocity | CSV `player_y_speed` (uhex) |
| `+0x14` | `0xD014` | s16be | inertia (ground speed) | CSV `player_g_speed` (uhex) |
| `+0x16` | `0xD016` | s8 | Y radius | `state_snapshot.y_radius` |
| `+0x17` | `0xD017` | s8 | X radius | `state_snapshot.x_radius` |
| `+0x1A` | `0xD01A` | u8 | displayed mapping frame | CSV `player_mapping_frame` |
| `+0x1B` | `0xD01B` | u8 | anim frame | Declared (`OFF_ANIM_FRAME`) but never read — do not port |
| `+0x1C` | `0xD01C` | u8 | animation ID | CSV `player_animation_id`; `state_snapshot.anim_id` |
| `+0x1E` | `0xD01E` | u8 | anim timer | Declared (`OFF_ANIM_TIMER`) but never read — do not port |
| `+0x22` | `0xD022` | u8 | status flags | CSV `player_status_byte`; air/rolling bits; aux events |
| `+0x24` | `0xD024` | u8 | routine (`obRoutine`) | CSV `player_routine`; routine_change |
| `+0x26` | `0xD026` | u8 | terrain angle | CSV `player_angle`; ground_mode derivation |
| `+0x28` | `0xD028` | u8 | subtype | `s1_obj64_state.subtype` (object slots only) |
| `+0x38` | `0xD038` | u8 | stick-convex | Declared (`OFF_STICK_CONVEX`) but never read — do not port |
| `+0x3D` | `0xD03D` | u8 | `standonobject` (SST index, 0 = none) | CSV `player_stand_on_obj`; routine_change context |
| `+0x3E` | `0xD03E` | u16be | control lock timer (`obCtrlLock`) | Start detection; `control_locked` mode/state events |

Status flag bits: `0x01` facing-left, `0x02` in-air, `0x04` rolling, `0x08`
on-object, `0x10` roll-jump, `0x20` pushing, `0x40` underwater.

S1 player routine values: `0x00` init, `0x02` Sonic_Control (normal — S1 has
no separate air/roll routines), `0x04` hurt, `0x06` death, `0x08` reset.

### 1.3 Object table (SST)

- Base `0xD000`, **128 slots** of `0x40` bytes each (slot N at
  `0xD000 + N*0x40`). Slot 0 is the player.
- Slot scan covers slots **1..127** (§6 step scan_objects).
- "Dynamic" slots are **32..127** (used only by `slot_dump`).
- Byte `+0x00` of a slot is the object type ID (`0` = empty).
- Per-slot fields read: `+0x00` id u8, `+0x08` x u16be, `+0x0C` y u16be,
  `+0x22` status u8, `+0x24` routine u8; and for object id `0x64` also
  `+0x01` render_flags u8, `+0x28` subtype u8, `+0x1C` anim u8, `+0x32` u8,
  `+0x33` u8, `+0x34` u16be, `+0x36` u16be, `+0x38` u16be, `+0x3C` u32be.

### 1.4 ObjPosLoad (OPL) cursor state

| Address | Width | Name | Used for |
|---------|-------|------|----------|
| `0xF76C` | u8 | `v_opl_routine` | Declared (`ADDR_OPL_ROUTINE`) but never read — do not port |
| `0xF76E` | u16be | `v_opl_screen` (last processed camera chunk) | `cursor_state` trigger + `opl_screen` field |
| `0xF770` | u32be | `v_opl_data` forward cursor ROM pointer | `cursor_state.fwd_ptr` |
| `0xF774` | u32be | `v_opl_data+4` backward cursor ROM pointer | `cursor_state.bwd_ptr` |
| `0xFC00` | u8 | `v_objstate[0]` forward counter | `cursor_state.fwd_ctr` |
| `0xFC01` | u8 | `v_objstate[1]` backward counter | `cursor_state.bwd_ctr` |

---

## 2. Frame-loop semantics (native harness model)

### 2.1 How the Lua runs

The Lua main loop is:

```
while true do
    on_frame_end()        -- inspects RAM of the frame that just COMPLETED
    ...
    emu.frameadvance()    -- runs ONE frame, consuming the BK2 input row
end
```

So every RAM inspection happens **post-advance** (state of the just-completed
frame) and **pre-record-decision**. `emu.framecount()` inside `on_frame_end()`
is the number of frames completed so far; the frame that produced the current
RAM state consumed BK2 input row `emu.framecount() - 1`.

### 2.2 Start detection

While `started == false` (and `finished == false`), after each completed frame
the recorder checks:

```
game_mode (u8 @ 0xF600) == 0x0C  AND  ctrl_lock (u16be @ 0xD03E) == 0
```

When the predicate first fires:

1. `bk2_frame_offset := emu.framecount()` (number of completed frames).
2. Capture start state from the detection frame's RAM: `start_x` = u16be
   `0xD008`, `start_y` = u16be `0xD00C`, `start_rng_seed` = u32be `0xF636`,
   `start_zone_id` = u8 `0xFE10`, `start_act` = u8 `0xFE11`,
   `start_zone_name` = zone-name map (§7).
3. Open `physics.csv` (write header, §3.1) and `aux_state.jsonl`.
4. **The detection frame itself is NOT recorded** — the function returns.

The next `emu.frameadvance()` consumes BK2 input row `bk2_frame_offset`
(because rows `0..bk2_frame_offset-1` were consumed producing the completed
frames counted by `emu.framecount()`), and the following `on_frame_end()`
records **trace row 0** from that frame's RAM. Therefore:

> **Trace row N = state after applying BK2 input row `bk2_frame_offset + N`
> and advancing one frame.**

Skipping the detection frame is deliberate: on the frame where controls first
unlock, input is present but the ROM has not yet processed movement, so speeds
would read 0 (a "dead frame").

### 2.3 Native translation with `IGpgxHost`

- Detection phase: for r = 0, 1, 2, ... apply BK2 row r's inputs
  (`ClearButtons` + `SetButton`), `Advance()`; after each advance evaluate the
  predicate (`game_mode == 0x0C && ctrl_lock == 0`). When it fires:
  `offset := CompletedFrame`, capture the start state (§2.2 step 2), and do
  **not** emit a row for this frame. Because exactly `CompletedFrame` rows
  (`0..CompletedFrame-1`) have been consumed, the next unconsumed row is row
  `offset` — identical to the Lua's `emu.framecount()` convention.
- Recording phase, per trace row N (starting at N = 0):
  - **(a)** If `offset + N + 1 >= <movie input-row count>`: finish. Row N is
    NOT recorded. This single pre-advance predicate folds BOTH Lua movie
    checks — the row-count guard AND `movie.mode() == "FINISHED"` — and the
    FINISHED one fires first, on the frame fed by the movie's LAST input row
    (see §2.4). The final input row of a movie is therefore never consumed or
    recorded.
  - **(b)** Apply BK2 row `offset + N`, `Advance()`.
  - **(c)** Read `game_mode` (u8 `0xF600`). If `!= 0x0C`: finish WITHOUT
    recording row N.
  - **(d)** Record row N (CSV row + aux events, exact order in §6), then
    N := N + 1.
- On finish: write final `metadata.json` (§7) with `trace_frame_count = N`.

### 2.4 Why this ordering is byte-equivalent to the Lua

The Lua's `on_frame_end()` performs, in source order: (1) read `game_mode`;
(2) start-detection branch; (3) `game_mode != 0x0C` → finish, no record;
(4) headless movie checks — `(bk2_frame_offset + trace_frame) >= movie.length()`
→ finish, no record; and `movie.mode() == "FINISHED"` → finish, no record;
(5) read RAM and record row `trace_frame`, then `trace_frame++`.

All of (3)–(5) run **after** the frame advance and **before** any bytes for
row N are written — exactly steps (b)→(c)→(d) above. The two apparent
reorderings are byte-neutral:

- **`movie.mode() == "FINISHED"` is the effective Lua movie stop, and it
  fires one iteration BEFORE the row-count guard.** In the pinned BizHawk
  2.11 binaries, `MovieSession.HandleFrameAfter` calls `HandlePlaybackEnd` →
  `Movie.FinishedMode()` exactly when `Emulator.Frame == Movie.FrameCount`,
  and `MainForm.StepRunLoop_Core` orders FrameAdvance → HandleFrameAfter →
  Lua resume. So on the `on_frame_end()` after the advance that consumed the
  movie's LAST input row (`offset + trace_frame == movie_length - 1`), the
  Lua sees FINISHED and finalizes WITHOUT recording that row; its row-count
  guard `(offset + trace_frame) >= movie_length` would only fire one
  iteration later (it exists as a safety net for chromeless runs where movie
  mode lags). The native predicate (a), `offset + N + 1 >= rows`, reproduces
  the earlier of the two: the frame fed by the final input row is never
  recorded.
- **Movie-end check moved pre-advance:** predicate (a) depends only on
  constants and the row counter, not on the advanced frame's RAM, so it
  evaluates identically before or after the advance. The Lua does advance
  the emulator through the final input row before noticing FINISHED — but
  nothing is recorded from that frame, so the output files are unaffected;
  the native harness simply never applies that row. The Lua also checks
  `game_mode` (3) before the movie checks (4); when both conditions hold on
  the same frame either order finishes without recording row N — identical
  bytes either way.
- The Lua constant `MOVIE_FRAME_SAFETY_MARGIN` is declared but **unused**; do
  not port it.

For the canonical GHZ1 fixture, the run ends via (c): `offset + N + 1` =
840 + 3905 + 1 = 4746 < 4806 rows, i.e. `game_mode` left `0x0C` well before
either movie stop could fire — the differential gate therefore cannot
exercise the movie-end path, which is why it is covered by unit tests
instead.

---

## 3. physics.csv (CSV v7)

### 3.1 Header (exact, single line, then `\n`)

```
frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,player_present,player_x,player_y,player_x_speed,player_y_speed,player_g_speed,player_angle,player_air,player_rolling,player_ground_mode,player_x_sub,player_y_sub,player_routine,player_status_byte,player_stand_on_obj,player_animation_id,player_mapping_frame,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj,sidekick_animation_id,sidekick_mapping_frame
```

### 3.2 Row format (VERBATIM Lua format string, 42 fields)

```
"%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,%02X,%02X\n"
```

Arguments in order (all reads are of the just-completed frame's RAM):

| # | Column | Value |
|---|--------|-------|
| 1 | `frame` | `trace_frame` (row index N), `%04X` |
| 2 | `input` | input mask from BK2 (§4), `%04X` |
| 3 | `camera_x` | u16be `0xF700`, `%04X` |
| 4 | `camera_y` | u16be `0xF704`, `%04X` |
| 5 | `rings` | u16be `0xFE20`, `%04X` |
| 6 | `gameplay_frame_counter` | u16be `0xFE04`, `%04X` |
| 7 | `vblank_counter` | u16be `0xFE0E`, `%04X` |
| 8 | `lag_counter` | **constant `0`** (`%04X` → `0000`; S1 has no lag counter — diagnostic placeholder) |
| 9 | `player_present` | **constant `1`** (`%d`) |
| 10 | `player_x` | u16be `0xD008`, `%04X` |
| 11 | `player_y` | u16be `0xD00C`, `%04X` |
| 12 | `player_x_speed` | s16be `0xD010` through **uhex** (§3.3), `%04X` |
| 13 | `player_y_speed` | s16be `0xD012` through uhex, `%04X` |
| 14 | `player_g_speed` | s16be `0xD014` through uhex, `%04X` |
| 15 | `player_angle` | u8 `0xD026`, `%02X` |
| 16 | `player_air` | `(status & 0x02) != 0 ? 1 : 0`, `%d` |
| 17 | `player_rolling` | `(status & 0x04) != 0 ? 1 : 0`, `%d` |
| 18 | `player_ground_mode` | §3.4, `%d` |
| 19 | `player_x_sub` | u16be `0xD00A`, `%04X` |
| 20 | `player_y_sub` | u16be `0xD00E`, `%04X` |
| 21 | `player_routine` | u8 `0xD024`, `%02X` |
| 22 | `player_status_byte` | u8 `0xD022`, `%02X` |
| 23 | `player_stand_on_obj` | u8 `0xD03D`, `%02X` |
| 24 | `player_animation_id` | u8 `0xD01C`, `%02X` |
| 25 | `player_mapping_frame` | u8 `0xD01A`, `%02X` |
| 26–42 | sidekick block | **all constant `0`** (17 zeros through the same specifiers) |

The constant sidekick block therefore renders, on every row, as exactly:

```
0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00
```

Notes:
- `status` for fields 16/17 is the same u8 read used for field 22.
- Lua's `%04X` does **not** truncate values above `0xFFFF` — it widens. All
  emitted values here are ≤ 16-bit except `trace_frame`, which stays ≤ 0xFFFF
  for all realistic traces (GHZ1: 3905). C# `val.ToString("X4")` matches this
  widening behavior for larger values.
- Hex digits are uppercase throughout.

### 3.3 `uhex` — two's-complement rendering of signed speeds

The three speed fields are read **signed** 16-bit, then converted for
formatting:

```
uhex(v) = v < 0 ? v + 0x10000 : v
```

so e.g. −2 renders as `FFFE`. (Equivalently in C#: format the raw unsigned
16-bit word with `X4`.)

### 3.4 `ground_mode`

```
ground_mode = air ? 0 : angle_to_ground_mode(angle)
```

with `air = (status & 0x02) != 0` and (VERBATIM thresholds; angle is the
u8 at `0xD026`):

```
angle <= 0x1F or angle >= 0xE0  -> 0   (floor; wraps across 0x00)
0x20 <= angle <= 0x5F           -> 1   (right wall)
0x60 <= angle <= 0x9F           -> 2   (ceiling)
otherwise (0xA0..0xDF)          -> 3   (left wall)
```

---

## 4. Input mask (CSV `input` column)

The mask is derived from the **BK2 movie row `bk2_frame_offset + N`**, NOT
from RAM. (The Lua reads `v_jpadhold1` u8 `0xF604` every frame as
`fallback_raw`, but `bk2_input_mask` only uses it when no movie is loaded or
`movie.getinput` returns nil — neither ever happens in this pipeline. The
fallback path `rom_joypad_to_mask` — `raw & 0x0F` plus `0x10` if
`raw & 0x70` — must never trigger in the native port; ROM-side `v_jpadhold1`
can lag the BK2 by a frame on lag-frame/long-V-int paths, which is precisely
why the movie is the source of truth.)

Mask bits (engine convention):

```
Up    -> 0x01
Down  -> 0x02
Left  -> 0x04
Right -> 0x08
A or B or C (any) -> 0x10   ("JUMP")
START IS EXCLUDED — it never contributes to the mask.
```

The existing `Bk2Frame` exposes button bits `Up=0x01, Down=0x02, Left=0x04,
Right=0x08, A=0x10, B=0x20, C=0x40, Start=0x80`. Collapse:

```
mask = (bk2 & 0x0F) | (((bk2 & 0x70) != 0) ? 0x10 : 0)     // bit 0x80 dropped
```

The same BK2 row's buttons are what get applied to `IGpgxHost` for the
advance that produces the row (§2.3(b)) — mask and applied input come from
the same row by construction, matching the Lua's
`movie.getinput(bk2_frame_offset + trace_frame, 1)`.

---

## 5. aux_state.jsonl event formats (VERBATIM templates)

Every event is one line: the formatted string followed by `\n`. `%d` is
decimal; `%02X`/`%04X`/`%08X` are uppercase zero-padded hex. Booleans render
as the bare words `true` / `false`. There are no spaces after `:` or `,`.
`"frame"` is always the current `trace_frame` (row N being recorded);
`"vfc"` is u16be `0xFE04`. The Lua re-reads `vfc` at the top of each helper
(`check_mode_changes`, `write_state_snapshot`, `scan_objects`, cursor block),
but RAM does not change between reads within a frame, so the native port may
read it once per frame — byte-identical output.

### 5.1 `routine_change`

Fired when player routine (u8 `0xD024`) differs from the previous frame's
value (`prev_routine`, initialized to `0` — so the first recorded frame
typically fires `0x00 -> 0x02`). Template:

```
'{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X","sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,"status":"0x%02X","stand_on_obj":%d%s}'
```

Args: trace_frame, vfc, prev_routine, routine, u16be `0xD008`, u16be `0xD00C`,
s16be `0xD010` (**signed decimal**, e.g. `-512`), s16be `0xD012`, s16be
`0xD014`, status u8 (the value passed into `check_mode_changes`, read for the
CSV row), u8 `0xD03D`, and `%s` = the optional stand-obj context suffix.

Suffix: empty string when `stand_on_obj == 0` or `>= 128`; otherwise, with
`obj_addr = 0xD000 + stand_on_obj*0x40`:

```
',"stand_obj_slot":%d,"stand_obj_type":"0x%02X","stand_obj_x":"0x%04X","stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"'
```

Args: stand_on_obj, u8 `obj_addr+0x00`, u16be `obj_addr+0x08`, u16be
`obj_addr+0x0C`, u8 `obj_addr+0x24`.

After emitting, if the **new** routine is `0x04` (hurt) or `0x06` (death),
immediately emit a `state_snapshot` (§5.2). `prev_routine := routine`
unconditionally afterwards.

### 5.2 `state_snapshot`

```
'{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,"on_object":%s,"pushing":%s,"underwater":%s,"roll_jumping":%s}'
```

Args (all fresh reads of live RAM at emit time): trace_frame, vfc,
`ctrl_lock (u16be 0xD03E) > 0` → `true`/`false`, anim_id u8 `0xD01C`
(**decimal**), status u8 `0xD022`, routine u8 `0xD024`, y_radius **s8**
`0xD016` (signed decimal), x_radius **s8** `0xD017`, then `true`/`false` for
`(status & 0x08)`, `(status & 0x20)`, `(status & 0x40)`, `(status & 0x10)`.

Emitted from three triggers: air-state mode_change (§5.3), hurt/death routine
change (§5.1), and periodically when `trace_frame % 60 == 0` (§6).

### 5.3 `mode_change` (four fields, all same template)

```
'{"frame":%d,"vfc":%d,"event":"mode_change","field":"<FIELD>","from":%d,"to":%d}'
```

`from`/`to` are `0`/`1`. Checked in this order, each against the previous
frame's value:

1. `"air"` — status bit `0x02` vs `prev_status`. If changed, emit, then
   **immediately** emit a `state_snapshot`.
2. `"rolling"` — status bit `0x04` vs `prev_status`.
3. `"on_object"` — status bit `0x08` vs `prev_status`.
4. `"control_locked"` — `ctrl_lock (u16be 0xD03E) > 0` vs
   `prev_ctrl_lock > 0`. After the check (fired or not),
   `prev_ctrl_lock := ctrl_lock` **unconditionally**.

`prev_status` and `prev_ctrl_lock` initialize to `0`. `prev_status` is
updated by the caller after `check_mode_changes` returns (§6), NOT inside it.

### 5.4 `object_appeared`

Fired per slot when slot id (u8 at slot base) is non-zero and differs from
the slot's previous id (`known_objects[slot]`, default 0 — so every occupied
slot fires on the first recorded frame; see fixture frame 0).

```
'{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}'
```

Args: trace_frame, vfc, slot, id, u16be slot+0x08, u16be slot+0x0C.

### 5.5 `object_removed`

Fired per slot when id is 0 and previous id was non-zero:

```
'{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%02X"}'
```

Args: trace_frame, vfc, slot, previous id.

### 5.6 `s1_obj64_state`

For every occupied slot whose id is exactly `0x64`, emitted **before** that
slot's proximity check (unconditionally — not gated on proximity):

```
'{"frame":%d,"vfc":%d,"event":"s1_obj64_state","slot":%d,"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X","render_flags":"0x%02X","subtype":"0x%02X","anim":"0x%02X","objoff_32":"0x%02X","objoff_33":"0x%02X","objoff_34":"0x%04X","objoff_36":"0x%04X","objoff_38":"0x%04X","objoff_3c":"0x%08X"}'
```

Args: trace_frame, vfc, slot, u16be +0x08, u16be +0x0C, u8 +0x24, u8 +0x22,
u8 +0x01, u8 +0x28, u8 +0x1C, u8 +0x32, u8 +0x33, u16be +0x34, u16be +0x36,
u16be +0x38, u32be +0x3C.

### 5.7 `object_near`

For every occupied slot (after the possible `s1_obj64_state`): with
`dx = |obj_x - player_x|`, `dy = |obj_y - player_y|` (player_x/y are the CSV
row's u16be reads of `0xD008`/`0xD00C`), if `dx <= 160 AND dy <= 160`:

```
'{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%02X","x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}'
```

Args: trace_frame, vfc, slot, id, obj_x, obj_y, u8 +0x24, u8 +0x22.
(Note the key is `"type"` here vs `"object_type"` in appeared/removed.)

### 5.8 `slot_dump`

After the full slot loop, if ANY `object_appeared` fired this frame:

```
'{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}'
```

where `%s` is built by scanning **dynamic slots 32..127 only** (fresh id
reads), collecting for each non-empty slot the entry `[%d,"0x%02X"]`
(slot, id), joined with `,` and wrapped in `[`…`]` — e.g.
`[[32,"0x25"],[33,"0x26"]]`; empty scan yields `[]`.

### 5.9 `cursor_state`

After object scanning: read `opl_screen` u16be `0xF76E`. If it differs from
`prev_opl_screen` (initialized to **−1**, so the first recorded frame always
fires):

```
'{"frame":%d,"vfc":%d,"event":"cursor_state","opl_screen":"0x%04X","fwd_ptr":"0x%08X","bwd_ptr":"0x%08X","fwd_ctr":%d,"bwd_ctr":%d,"dir":"%s"}'
```

Args: trace_frame, vfc, opl_screen, u32be `0xF770`, u32be `0xF774`,
u8 `0xFC00` (decimal), u8 `0xFC01` (decimal), dir. Direction rule:

```
dir = (prev_opl_screen >= 0 AND opl_screen < prev_opl_screen) ? "L" : "R"
```

(so the initial transition from −1 is always `"R"`). Then
`prev_opl_screen := opl_screen`. No update when unchanged.

---

## 6. Exact per-frame emission order (recording phase, per trace row N)

Byte order within the frame is fixed. After the advance and the
`game_mode == 0x0C` check:

1. **CSV row** for frame N (§3), using `input` from BK2 row `offset + N`.
   (The Lua's `physics_file:flush()` every 60 frames and metadata rewrite
   every 300 frames are I/O-cadence only — no bytes differ; see §9.)
2. **`check_mode_changes(status, routine)`** — using the status/routine
   values already read for the CSV row:
   a. air change → `mode_change` `"air"` + immediate `state_snapshot`;
   b. rolling change → `mode_change` `"rolling"`;
   c. on_object change → `mode_change` `"on_object"`;
   d. control_locked change → `mode_change` `"control_locked"`; then
      `prev_ctrl_lock` update (always);
   e. routine change → `routine_change` (with optional stand-obj suffix),
      plus `state_snapshot` iff new routine is `0x04` or `0x06`; then
      `prev_routine` update (always).
3. **`prev_status := status`**.
4. **Periodic snapshot:** if `trace_frame % 60 == 0` → `state_snapshot`.
   (Fires on frame 0 — after the `0x00 -> 0x02` routine_change, matching the
   fixture's first two aux lines.)
5. **`scan_objects(player_x, player_y)`** over slots **1..127 in ascending
   order**; for each slot: `object_appeared` (if newly non-zero/changed id) →
   `object_removed` (if newly zero) → if occupied: `s1_obj64_state` (iff id
   == 0x64) → `object_near` (iff within proximity) → update
   `known_objects[slot] := id` (always, including to 0). After the loop:
   `slot_dump` iff any appearance fired.
   - Note: a slot whose id CHANGES from one non-zero value to another fires
     only `object_appeared` (no `object_removed`) — the removed branch
     requires the new id to be 0.
6. **`cursor_state`** check/emit (§5.9).
7. `trace_frame := trace_frame + 1`.

Tracker initial values (set once, before row 0): `prev_status = 0`,
`prev_routine = 0`, `prev_ctrl_lock = 0`, `prev_opl_screen = -1`,
`known_objects` all-zero.

---

## 7. metadata.json — exact byte layout

Written with 2-space indent, this exact key order, `\n` line endings, and a
trailing `\n` after the closing brace. Template (placeholders in `<>`):

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
  "lua_script_version": "3.5",
  "trace_schema": 4,
  "csv_version": 7,
  "aux_schema_extras": ["s1_obj64_state_per_frame"],
  "rom_checksum": "",
  "notes": ""
}
```

- All start-captured values (`zone`, `zone_id`, `act`, `start_x`, `start_y`,
  `rng_seed`) come from the **detection frame's** RAM (§2.2), not
  end-of-run state. `act` is the raw byte **plus 1**.
- `start_x`/`start_y`: `"0x%04X"` of the u16be reads (the Lua `hex()` helper
  adds `0x10000` for negatives, which cannot occur for unsigned reads —
  ignore). `rng_seed`: `"0x%08X"` of u32be `0xF636`.
- Zone name map (u8 `0xFE10` → name; else `unknown_%02x` **lowercase** hex):
  `0="ghz"`, `1="lz"`, `2="mz"`, `3="slz"`, `4="syz"`, `5="sbz"`,
  `6="endz"`, `7="ss"`.
- `recording_date` = current local date `%Y-%m-%d` — **the ONLY
  nondeterministic field**, and the only value comparison against the
  canonical fixture may normalize.
- The Lua writes metadata at start, every 300 recorded frames, and at end,
  as crash-resume insurance — each write truncates and rewrites the file, so
  **only the final bytes matter**. The native port may write it exactly once
  at finish. This is an intentional non-difference (§9).

---

## 8. File encoding

All three output files (`physics.csv`, `aux_state.jsonl`, `metadata.json`):

- UTF-8, **no BOM** (content is pure ASCII in practice).
- **LF** (`\n`) line endings only — never CRLF, even on Windows.
- Trailing newline at end of file (every line including the last is
  terminated with `\n`; there is no extra blank line).

Verified against the canonical fixture files (last byte `0x0A`, zero `\r`
occurrences, no BOM).

---

## 9. Intentional differences (native vs Lua — no output-byte impact)

- **stdout/progress output:** all Lua `print()` lines ("Trace recording
  started at ...", "Movie length: ...", "Left level gameplay ...",
  "Recording complete ...", etc.) are console diagnostics only. The native
  harness may log differently or not at all.
- **Crash-resume metadata cadence:** Lua rewrites `metadata.json` at start
  and every 300 recorded frames so a killed process leaves a usable file;
  the native port writes once at finish. Final bytes identical.
- **Flush cadence:** Lua flushes the CSV every 60 frames and aux after every
  line; irrelevant to content.
- **Headless speed toggles:** `emu.limitframerate(false)`,
  `client.speedmode(6400)`, `client.SetSoundOn(false)`,
  `client.invisibleemulation(true)`, pause/unpause handling, and
  `client.exit()` are EmuHawk lifecycle controls with no native equivalent
  or need.
- **Output directory creation:** Lua `os.execute("mkdir ... 2>NUL")`; native
  uses ordinary directory creation.
- **Dead Lua code not to port:** `ADDR_CTRL1_DUP` (0xF602),
  `ADDR_OPL_ROUTINE` (0xF76C), `OFF_ANIM_FRAME` (+0x1B), `OFF_ANIM_TIMER`
  (+0x1E), `OFF_STICK_CONVEX` (+0x38), `MOVIE_FRAME_SAFETY_MARGIN`, and the
  RAM-input fallback path (`rom_joypad_to_mask`) which never triggers when a
  movie is loaded.
