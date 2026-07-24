# S1 Complete-Run Recorder — Run Mode + Special-Stage Byte-Level Behavior Spec

Authority: `tools/bizhawk/s1_complete_run_recorder.lua` at current HEAD (the
file stamps `lua_script_version "3.17"`; see §10 for the 3.15-fixture
provenance). The Lua is the behavioral authority; where any spec text and the
Lua disagree, the Lua wins. Consumer contract:
`src/main/java/com/openggf/trace/TraceRunManifest.java`.

Canonical fixtures (read-only ground truth):

- Run mode: `src/test/resources/traces/s1/runs/s1-ghz-maze-roundtrip/`
  (`run_manifest.json` + segment dirs `ghz1`, `ss`, `ghz2` + the source movie
  `s1-ghz-maze-roundtrip.bk2`).
- Standalone special stage: `src/test/resources/traces/s1/special_stage/`
  (see §11 — a byte-identical copy of the run's `ss/` segment).

This document specifies the level→special-stage→level detour machinery, the
`s1_special_stage` segment writer, and `run_manifest.json` of the S1
complete-run recorder. The plain multi-segment level behavior (CSV v7, aux
events, arm/finalize per act) is specified in
`docs/s1-trace-recorder-behavior.md` §-by-§ where shared; this doc covers only
the run-mode deltas and the SS writer. The S2 analog is
`docs/s2-run-mode-behavior.md`; §12 lists every S1-vs-S2 delta.

Line references are to `s1_complete_run_recorder.lua` at HEAD (1764 lines).

---

## 1. Activation — the detour machine is ALWAYS on; `run_id` only gates fields

```lua
run_id = os.getenv("OGGF_TRACE_RUN_ID") or nil                       -- L390
source_bk2_name = os.getenv("OGGF_TRACE_SOURCE_BK2")
    or "s1-complete-run.bk2"                                         -- L391
local BASE_OUTPUT_DIR = os.getenv("OGGF_TRACE_OUTPUT_DIR")
    or "trace_output/"                                               -- L150
```

**Critical S1-vs-S2 difference:** unlike S2 (where every run-mode branch is
gated on `run_id ~= nil`), the S1 detour state machine, SS segment writer,
segment/transition bookkeeping, and `finalize_run_end()` funnel are
**unconditional** — they run on every invocation of this recorder. The env
vars only control:

- `OGGF_TRACE_RUN_ID` → (a) forces `run_manifest.json` emission even for a
  detour-free run; (b) adds the `"run_id"` line to `ss/metadata.json` (L598-600)
  and to the manifest (L809). Presence test is `os.getenv(...) ~= nil` — an
  empty string still activates.
- `run_manifest.json` is otherwise emitted **iff at least one transition
  occurred** (`#transitions_done == 0 and run_id == nil` → early return,
  L786-788). A single-stage complete-run regeneration with no SS detour and no
  run id therefore stays output-identical to the legacy layout (no manifest).
  Conversely: **any movie that enters a special stage produces `ss*/` segment
  dirs and a `run_manifest.json` even without `OGGF_TRACE_RUN_ID`** — only the
  `run_id` lines are absent.
- `OGGF_TRACE_SOURCE_BK2` → the `source_bk2` value in level metadata, ss
  metadata, and the manifest (default `"s1-complete-run.bk2"`). The fixture
  was captured with `s1-ghz-maze-roundtrip.bk2`.
- `OGGF_TRACE_OUTPUT_DIR` → run root (trailing `/` or `\` appended if
  missing, L151-153).
- `S1_STOP_AT_FRAME` → optional hard stop (re-read from env every frame,
  L1342); `OGGF_TRACE_VISIBLE=1` → window visible; `OGGF_S1_RNG_CALL_RANGE`
  → FZ-only diagnostic rng_call aux events (§10, not used by any fixture).

Fixture capture env (reconstructed): `OGGF_TRACE_RUN_ID=s1-ghz-maze-roundtrip`,
source BK2 name `s1-ghz-maze-roundtrip.bk2`, output dir = the run dir.

Run-mode state lives in **globals** (L379-391): `segments_done`,
`transitions_done`, `segment_dir_counts`, `detour_active`
(`nil | "special_stage"`), `current_segment_dir_token`, `current_ss_index`,
`ss_min_angle_seen`/`ss_max_angle_seen`/`ss_last_rotate` (self-check only),
`run_id`, `source_bk2_name`.

### Output layout

```
BASE_OUTPUT_DIR/
  run_manifest.json          (only per the gating above)
  ghz1/    physics.csv aux_state.jsonl metadata.json   (kind level)
  ss/      physics.csv aux_state.jsonl metadata.json   (kind special_stage)
  ghz2/    physics.csv aux_state.jsonl metadata.json   (kind level)
  ...
```

### Segment directory naming (exact)

Both level and SS arms go through `next_segment_dir_token(base)` (L421-425):
per-base counter `segment_dir_counts[base]`; first use yields the bare token,
repeats yield `base .. "_" .. n` (`ghz1`, `ghz1_2`, ...; `ss`, `ss_2`, ...).

- **Level segments** (L1448): base token = `start_zone_name ..
  tostring(start_act + 1)` — zone short name from `ZONE_NAMES` (L295-304:
  ghz/lz/mz/slz/syz/sbz/endz/ss), 1-based act. No `segN_` prefix (S2 delta).
  Note zone id 7 is *named* `"ss"` in `ZONE_NAMES`, so hypothetical level acts
  in zone 7 would claim `ss1`..`ss4` — distinct from the detour's bare `ss`
  token, which is why the two namespaces never collide (L626-631 comment).
- **SS segments** (L620): base token literal `"ss"` → `ss`, `ss_2`, `ss_3`...

All known `<zone><act>` dirs plus the bare `ss/` are pre-created in ONE
`os.execute` at load (`precreate_segment_dirs`, L310-352); `ensure_segment_dir`
(L358-371) is a shell-free probe fallback that only shells out for a dir not
in the pre-created set (e.g. `ss_2`, `ghz1_2`, `unknown_XX1`).

---

## 2. The detour state machine (`on_frame_end` structure, exact order)

`on_frame_end()` (L1333) evaluates in this order; placement is load-bearing:

1. **Top-of-function stop guard** (L1342-1352): `stop_reached = stop_at > 0
   and frame_now >= stop_at` (with `stop_at` = env `S1_STOP_AT_FRAME`,
   `frame_now` = `emu.framecount()`); `movie_len = movie.isloaded() and
   movie.length() or 0`; `movie_done = (movie_len > 0 and frame_now >=
   movie_len) or (movie.isloaded() and movie.mode() == "FINISHED")`. Either
   → `finalize_run_end()`, `finished = true`, return. NOT gated on `started`,
   so a movie ending mid-`$10` (or before gameplay ever starts) still
   finalizes correctly. **No `OGGF_BK2_FRAME_COUNT`-style max-override exists
   in S1** — raw `movie.length()` only (S2 delta).
2. **Block 1 — SS entry/continuation** (L1371-1404): gate `started and
   game_mode == 0x10` (`GM_Special`; game_mode = u8 at `$FFF600`). No
   `run_id` gate (S2 delta). The `started` requirement means a movie beginning
   inside `$10` with nothing armed can never create an ss segment or a
   `from_segment = -1` transition. S1's SS entry always occurs with a level
   segment armed: the Got-Through card writes `#id_Special` directly while
   `GM_Level` runs, gated on the flash-set `f_bigring`
   (`docs/s1disasm/_incObj/3A Got Through Card.asm:201`; flag set by the giant
   ring flash, `_incObj/4B, 7C Giant Ring and Flash.asm:123`), so the edge is
   a direct `$0C → $10` with `started == true`.
   - **Entry** (`detour_active ~= "special_stage"`, L1372-1397): finalize the
     armed level segment (flush → `write_metadata()` →
     `append_level_segment_done(trace_frame)` → `close_files()` →
     `started = false`, `trace_frame = 0`), push the `giant_ring` transition
     (§3), `start_ss_segment()` (§4), `detour_active = "special_stage"`,
     print, **return without writing an ss row**.
   - **Continuation** (L1398-1403): `write_ss_row()` then return. Because this
     returns first, the non-level finalize branch below never sees a `$10`
     frame — no double-finalize.
3. **Block 2 — SS exit** (L1405-1414): gate `detour_active ==
   "special_stage"` with game_mode now ≠ `$10` (first non-`$10` frame — the
   results tally trailing off `$10`, or the return load handoff).
   `finalize_ss_segment()`, `detour_active = nil`, then **fall through** into
   the arm gate on the same frame. **There is NO
   `reset_recording_state_keep_files` analog** — `known_objects`,
   `prev_status`, `prev_routine`, `prev_ctrl_lock`, `prev_opl_screen` are
   never reset at any segment boundary (§9).
4. **Level arm gate** (`if not started`, L1416-1485): `game_mode == 0x0C`
   (`GAMEMODE_LEVEL`) and player `obCtrlLock` word (`$FFD000+0x3E`) == 0.
   On arm: sample `bk2_frame_offset = emu.framecount()`, `start_x`/`start_y`
   (player x/y words), `start_rng_seed` (`v_random` u32be `$FFF636`),
   `start_zone_id`/`start_act` (`$FFFE10`/`$FFFE11`), compute the dir token,
   push the `stage_exit` transition **iff the previous finished segment was a
   special stage** (§3), `open_files()`, initial `write_metadata()`, return
   without recording (row 0 lands one frame later).
5. **Level finalize + re-arm** (L1487-1500): armed and game_mode leaves `$0C`
   to anything ≠ `$10` (act transitions go `$0C → $8C → $0C`): flush →
   `write_metadata()` → `append_level_segment_done(trace_frame)` →
   `close_files()` → `started = false`, `trace_frame = 0`, return. The
   recorder does NOT exit — the next `$0C`+unlock arms the following segment.
6. Shadowed in-loop BK2-end checks (L1506-1530, dead since v3.6 — the top
   guard fires strictly earlier; both still funnel through
   `finalize_run_end`), then the plain level row/aux writing.

### Run termination funnel

Every live termination path calls `finalize_run_end()` (L757-769) exactly
once before `finished = true`: the top stop/movie-done guard, the two shadowed
mid-loop sites, and the main-loop `FRAME_CAP` backstop (L1702-1725;
`movie.length() + 64` when known, else 2,000,000). Routing is load-bearing
because `started` is true during BOTH an armed level segment and an armed SS
segment:

```lua
if detour_active == "special_stage" -> finalize_ss_segment(); detour_active = nil
elseif started -> flush; write_metadata(); append_level_segment_done(trace_frame);
                  close_files(); started = false
write_run_manifest()   -- always attempted, last (gating per §1)
```

Unconditionally running the level finalize mid-detour would overwrite
`ss/metadata.json` via the shared `OUTPUT_DIR`, append a bogus `kind="level"`
entry, and leave `finalize_ss_segment()` a silent no-op on its `not started`
guard. After `finished = true` the main loop only prints and breaks — it must
not re-finalize.

---

## 3. Transitions: `giant_ring` and `stage_exit`

S1 emits exactly two entry kinds (both in `TraceRunManifest.ENTRY_KINDS`; the
S2 kinds `starpost_special`/`starpost_bonus` are never produced — S1 has no
starpost stage entry). S1 transitions carry **only** ring/emerald fields —
no `special_bonus_entry_flag` / `saved_x_pos` / `saved_y_pos` /
`last_star_post_hit` (all optional `Integer`s in `TraceRunManifest.Transition`,
so the reduced set validates as-is).

### `giant_ring` (level → ss), pushed at SS entry (L1385-1392)

All fields are read **on the first frame `game_mode` reads `$10`** (after that
frame completed), after the level append and before `start_ss_segment()`:

| Lua field | Source | When read |
|---|---|---|
| `from_segment` | `#segments_done - 1` | after level append → 0-based index of the finished level |
| `to_segment` | `#segments_done` | index the ss segment will occupy |
| `entry_kind` | literal `"giant_ring"` | — |
| `mode_change_bk2_frame` | `emu.framecount()` | entry frame; **equals the ss segment's `bk2_frame_offset`** (4957 in the fixture) |
| `rings_before` | u16be `$FFFE20` (`v_rings`, binary) | entry frame (fixture: 85) |
| `emeralds_before` | u8 `$FFFE57` (`v_emeralds`, count 0-6) | entry frame (fixture: 0) |

### `stage_exit` (ss → level), pushed at the return-level ARM (L1459-1468)

Emitted inside the level-arm branch, only when `#segments_done > 0 and
segments_done[#segments_done].kind == "special_stage"`. At that point
`segments_done == [..., level, ss]`, so indices are exact without adjustment.
Fields are read **on the arm-detection frame** (`$0C` + `obCtrlLock == 0`) —
NOT necessarily the first non-`$10` frame, though in the fixture they coincide
(frame 8049):

| Lua field | Source | Notes |
|---|---|---|
| `from_segment` | `#segments_done - 1` | ss segment's 0-based index |
| `to_segment` | `#segments_done` | return level's index (appended only later, at its finalize) |
| `entry_kind` | literal `"stage_exit"` | — |
| `mode_change_bk2_frame` | `emu.framecount()` | arm frame; **equals the return level's `bk2_frame_offset`** (8049) |
| `rings_after` | u16be `$FFFE20` | fixture: **67** — S1 CARRIES the ring count through the SS round-trip (S2 zeroes it on reload; do not import that expectation) |
| `emeralds_after` | u8 `$FFFE57` | fixture: 1 (emerald collected) |

`giant_ring` records carry no `*_after` fields; `stage_exit` records carry no
`*_before` fields. **Lua truthiness caveat:** manifest emission tests are
`if t.<field> then` (L838-841) — `0` is truthy in Lua, so a sampled value of 0
(fixture `emeralds_before: 0`) IS emitted. A port must key on "was the field
recorded for this transition kind", never on the value.

---

## 4. The special-stage segment writer (`trace_profile "s1_special_stage"`)

No `event.onmemoryexecute` hooks; state is sampled from RAM once per `$10`
continuation frame — i.e. once per emulated frame including lag frames.

### Arming (`start_ss_segment()`, L619-653)

Executed once per detour, on the entry frame:

1. `dir_token = next_segment_dir_token("ss")`; `OUTPUT_DIR = BASE_OUTPUT_DIR
   .. dir_token .. "/"`; `ensure_segment_dir`.
2. `started = true`; `bk2_frame_offset = emu.framecount()` (the entry frame);
   `trace_frame = 0`.
3. `current_ss_index = mainmemory.read_u8(0xFE16)` (`v_lastspecial`, 0-5) —
   sampled **at arm time, BEFORE SS_Load runs** (GM_Special opens with a
   multi-frame fade). SAMPLING-WINDOW CAVEAT (L628-639): SS_Load
   (`docs/s1disasm/_inc/Special Stage Loading & Drawing.asm:536-556`) reads
   `v_lastspecial`, increments it mod 6, and skips already-collected stages —
   after a first emerald, the arm-time read can name a stage the skip loop
   then rejects. `finalize_ss_segment` re-reads and prints a self-check:
   healthy iff re-read == `(current_ss_index + 1) % 6`; anything else means
   `current_ss_index` is suspect (stdout diagnostic only — no output-file
   effect).
4. Reset self-check accumulators; open `physics.csv` + `aux_state.jsonl`;
   write the 14-column header; flush; initial `write_ss_metadata()`; print.

**Frame-0 alignment:** the entry branch returns without writing a row; ss row
0 is recorded on the **next** `$10` frame. Row N's BK2 input index is
`bk2_frame_offset + N` (so row 0's input is the entry frame's input).

### physics.csv schema (ss_csv_version 1)

Header (one line, 14 columns):

```
frame,input,lag,x_pos,y_pos,vel_x,vel_y,inertia,status,ss_angle,ss_rotate,bg_anim,rings,emeralds
```

Row format string (L687): `"%d,%x,%d,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x,%x\n"` —
`frame` decimal, `lag` decimal 0/1, **everything else lowercase unpadded hex**
(do NOT reuse the level writer's `%04X` uppercase padding). Sources
(`write_ss_row`, L671-702), all `mainmemory` reads on the row's frame:

| Column | Source |
|---|---|
| `frame` | `trace_frame` (0-based; incremented after the write) |
| `input` | `bk2_input_mask(raw v_jpadhold1 $FFF604, trace_frame)` — the SAME shared helper as level rows (`lib/oggf_trace_common.lua`): BK2 `movie.getinput(bk2_frame_offset + trace_frame, 1)`, directions bits 0-3, A\|B\|C collapsed to JUMP `0x10`, **no Start bit** (S2 SS delta), RAM fallback when no movie is loaded OR `movie.getinput` returns nil |
| `lag` | `emu.islagged() and 1 or 0` |
| `x_pos` | u32be `$FFD008` (x word ++ x-sub word, e.g. `25ab0300`) |
| `y_pos` | u32be `$FFD00C` |
| `vel_x` / `vel_y` / `inertia` | u16be `$FFD010` / `$FFD012` / `$FFD014` (**unsigned** reads → negative values render as e.g. `ffde`) |
| `status` | u8 `$FFD022` |
| `ss_angle` | u16be `$FFF780` (`v_ssangle`) |
| `ss_rotate` | u16be `$FFF782` (`v_ssrotate`; exit ramp targets `0x1800`) |
| `bg_anim` | u16be `$FFF7A0` (`v_ssbganim`) |
| `rings` | u16be `$FFFE20` (level ring counter, carried into the SS) |
| `emeralds` | u8 `$FFFE57` |

Cadence, all checked BEFORE the increment (i.e. at rows 0, 60, 120... / 0,
300, 600...): flush every `trace_frame % 60 == 0`; `write_ss_metadata()`
rewrite every `trace_frame % 300 == 0`; progress print at row 0 and every 300.
Self-check accumulators (`ss_min_angle_seen`/`ss_max_angle_seen`/
`ss_last_rotate`) update every row — stdout only.

**Tail caveat (L657-663):** S1's SS results tally can still run under
game_mode `$10`, so trailing rows may include results-tally frames. Rows keep
being written until the first non-`$10` frame; the fixture's ss segment has
3091 rows including that tail.

### Aux events

**None.** `aux_state.jsonl` is opened at arm and closed at finalize but never
written — the finished file is exactly **0 bytes** (fixture confirms; its
`.gz` is a 36-byte gzip of empty content). None of the level aux machinery
(scans, snapshots, v_objstate, camera_boundary, v_oscillate, lag_state,
cursor_state) runs during the detour.

### ss metadata.json (`write_ss_metadata()`, L583-605)

Written at arm, every 300 rows, and at finalize. Exact key order and
formatting (2-space indent, one key per line; `source_bk2` via Lua `%q`):

```json
{
  "game": "s1",
  "trace_profile": "s1_special_stage",
  "special_stage_index": <current_ss_index>,
  "ss_csv_version": 1,
  "characters": ["sonic"],
  "main_character": "sonic",
  "sidekicks": [],
  "bk2_frame_offset": <n>,
  "trace_frame_count": <trace_frame at write time>,
  "source_bk2": "<source_bk2_name>",
  "lua_script_version": "3.17",
  "recording_date": "YYYY-MM-DD",
  "run_id": "<run_id>",
  "fresh_load": false,
  "segment_index": <#segments_done>
}
```

- The `"run_id"` line is emitted **only when `run_id ~= nil`**, via plain
  string concatenation (no escaping — unlike the manifest's `%q`).
- `fresh_load` is always `false`; `segment_index` is the last key (no
  trailing comma) and equals `#segments_done` at write time — the number of
  segments finished before this one (fixture: 1, the ghz1 level). Stable
  across all rewrites because the ss entry is appended to `segments_done`
  only after the final metadata write.
- No `zone`/`zone_id`/`act`/`start_x`/`start_y`/`rng_seed`/`trace_schema`/
  `csv_version`/`aux_schema_extras`/`rom_checksum`/`notes` keys (all
  level-metadata-only). `special_stage_index` + `ss_csv_version` are required
  by the manifest consumer for `kind == "special_stage"`.

### Finalize (`finalize_ss_segment()`, L711-745)

Guarded `if not started then return end` (idempotent). Flush → final
`write_ss_metadata()` → self-check prints (row count; angle range;
final `ss_rotate` vs `0x1800`; `v_lastspecial` re-read vs healthy) →
`close_files()` → append to `segments_done`:

```lua
{ dir = <token>, kind = "special_stage", profile = "s1_special_stage",
  special_stage_index = <current_ss_index>, zone_id = 0, act = 0,
  bk2_frame_offset = <offset>, rows = <trace_frame> }
```

Then `started = false`, `trace_frame = 0`, accumulators and
`current_ss_index` cleared.

---

## 5. `bk2_frame_offset` / `trace_frame_count` alignment identities

- **Level segment:** `bk2_frame_offset` = `emu.framecount()` on the
  arm-detection frame, which is **not recorded**; row 0 is written on the next
  `on_frame_end()` (post-movement state), so row N is written when
  `emu.framecount() == bk2_frame_offset + 1 + N`, and row N's BK2 input index
  is `bk2_frame_offset + N`.
- **SS segment:** identically shaped — entry frame supplies the offset and is
  skipped; row 0 lands one frame later.
- Fixture identities: ghz1 `774 + 4182 = 4956` (last level row written at emu
  frame 4956) and the SS entry fired at `4957 = 774 + 4182 + 1`; ss
  `4957 + 3091 = 8048` (last ss row at 8048) and the exit/arm frame is
  `8049`; each transition's `mode_change_bk2_frame` equals the following
  segment's `bk2_frame_offset` (4957, 8049). `TraceRunManifest.validate`
  requires strictly increasing offsets across segments.
- Segment-dir metadata carries the same `bk2_frame_offset` /
  `trace_frame_count` values as the manifest entry (metadata is rewritten
  with the final `trace_frame` during finalize).

---

## 6. run_manifest.json — exact byte layout and write timing

`write_run_manifest()` (L785-849) writes `BASE_OUTPUT_DIR ..
"run_manifest.json"`, called only from `finalize_run_end()` — **exactly once,
at run termination** (never rewritten periodically; a killed process loses the
manifest but keeps finalized segment dirs). Gating per §1. Before writing, a
non-fatal invariant check (L798-804) prints a WARNING for any transition where
`to_segment ~= from_segment + 1` or `to_segment > #segments_done`.

Exact emission (2-space indent; `%q` = Lua quoted-string format, used for
`run_id`, `source_bk2`, `dir`, `kind`, `trace_profile`, `entry_kind` — note
S1 uses `%q` for `source_bk2` where S2 uses `json_escape`):

```
{
  "run_schema": 1,
  "game": "s1",
  "run_id": <%q run_id>,                  <- line present only when run_id ~= nil
  "source_bk2": <%q source_bk2_name>,
  "rom_checksum": "AFE05EEE",
  "lua_script_version": "3.17",
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

- `rom_checksum` is the **inline literal** `"AFE05EEE"` — the CRC32 of Sonic 1
  World REV01 (the only ROM this recorder targets), not computed at runtime.
  `lua_script_version` is the inline script-version literal.
- **Segments array** (L814-823): one object per line, 4-space indented, key
  order exactly `dir, kind, trace_profile, bk2_frame_offset,
  trace_frame_count, zone_id, act`, with `<extra> = ', "special_stage_index":
  <n>'` appended **only** for `kind == "special_stage"`. The Lua-side
  per-segment row-count field is `rows`, emitted under JSON key
  `trace_frame_count`. Level entries: `kind "level"`, `trace_profile
  "complete_run"` (the literal lives ONLY in `append_level_segment_done`,
  L567-577 — S1 level metadata.json itself has no `trace_profile` key),
  `zone_id` = raw `v_zone`, `act` = 1-based. SS entries: `kind
  "special_stage"`, `trace_profile "s1_special_stage"`, `zone_id 0`, `act 0`,
  `special_stage_index` from the arm-time `v_lastspecial` read. Trailing
  comma after every entry except the last; each entry ends with a newline.
- **Transitions array** (L825-844): mandatory fields in order `from_segment`,
  `to_segment`, `entry_kind` (`%q`), `mode_change_bk2_frame`; optional fields
  appended **in this fixed order, each present iff the Lua table field is
  set**: `rings_before`, `rings_after`, `emeralds_before`, `emeralds_after`.
  So a `giant_ring` record renders `..., "rings_before": R,
  "emeralds_before": E` and a `stage_exit` renders `..., "rings_after": R,
  "emeralds_after": E`. (Truthiness caveat §3: value 0 is still emitted.)
- Closing: `  ]\n}\n` (no trailing comma on the transitions `]`; file ends
  with a newline after `}`). Final print of segment/transition counts.
- Must satisfy `TraceRunManifest.validate`: `run_schema == 1`, ≥1 segment,
  known kinds, strictly increasing `bk2_frame_offset`, unique segment dirs
  each containing `metadata.json`, `special_stage_index` on every
  special_stage segment, known `entry_kind`s, per-transition
  `to_segment == from_segment + 1` and `to_segment < segments.size()`.

Fixture manifest (verbatim values): segments `ghz1` (level, complete_run,
774, 4182, zone 0, act 1), `ss` (special_stage, s1_special_stage, 4957,
3091, 0, 0, index 0), `ghz2` (level, complete_run, 8049, 812, zone 0, act 2);
transitions `giant_ring` (0→1, 4957, rings_before 85, emeralds_before 0) and
`stage_exit` (1→2, 8049, rings_after 67, emeralds_after 1).

---

## 7. Level segments inside a run

Run-context level segments are produced by exactly the plain complete-run
level writer (CSV v7 `%04X`-padded uppercase, 42 columns with an all-zero
sidekick block, full aux pipeline, flush 60 / metadata 300 cadences) — see
`docs/s1-trace-recorder-behavior.md` for that contract. Run-context deltas:

1. Output dir token goes through `next_segment_dir_token` (§1) — first
   entries are unchanged (`ghz1`), only re-entries gain `_2` suffixes.
2. Arming may push a `stage_exit` transition (§3).
3. Finalization also appends a `segments_done` entry with
   `profile = "complete_run"`.
4. **S1 level metadata.json is byte-identical in and out of run context** —
   there are NO `run_id` / `segment_index` lines in the level metadata (S2
   delta; fixture `ghz1/metadata.json` confirms — its keys end
   `rom_checksum: ""`, `notes: ""`, `source_bk2`). The only run-dependent
   level-metadata bytes are `source_bk2` (env-driven) and `rng_seed` /
   `start_x` / `start_y` / offsets (sampled at arm; fixture ghz2 shows
   `rng_seed "0x9BF88D9A"` because the SS ran the RNG, vs ghz1's
   `"0x00000000"`).

---

## 8. Cross-segment carried state (NO reset between segments)

The per-segment finalizes reset only `started` and `trace_frame`. These
file-scope trackers carry across ALL segment boundaries, including through an
SS detour (during which none of them update):

- `known_objects` (slot → last id): the return level's first `scan_objects`
  diffs against the PREVIOUS level's final slot map — a slot holding the same
  object id across the boundary emits **no** `object_appeared` event.
- `prev_status` / `prev_routine` / `prev_ctrl_lock`: `check_mode_changes` on
  the new segment's frame 0 diffs against the previous segment's final
  values — the fixture's `ghz2/aux_state.jsonl` opens with a frame-0
  `routine_change {"from":"0x02","to":"0x04"}` precisely because
  `prev_routine` carried 0x02 from ghz1's last frame.
- `prev_opl_screen`: cursor_state events on the new segment fire only when
  `v_opl_screen` differs from the previous segment's last seen value.

A port that resets these at segment boundaries will emit extra/missing aux
events on the return segment and fail the byte gate.

---

## 9. File encodings (run + standalone fixtures)

- **Every line of every file** in `runs/s1-ghz-maze-roundtrip/` and
  `special_stage/` is **CRLF**-terminated, including the final line:
  `run_manifest.json` (last bytes `] \r \n } \r \n`), each `metadata.json`,
  each `physics.csv` (level AND ss, inside the `.gz`), and each non-empty
  `aux_state.jsonl` (inside the `.gz`). Verified: zero LF-only lines in any
  file. The Lua writes `"\n"` through Windows text-mode `io.open`; the
  capture ran on Windows EmuHawk. The native harness reproduces this via
  `Program.cs` `ExpandRunNewlines` on run-published files (see
  `docs/s2-run-mode-behavior.md` §9).
- `ghz1/physics.csv.gz` etc. are gzip members (with stored original
  filenames); the differential gate gunzips to a temp dir and compares the
  CRLF payload bytes. `ss/aux_state.jsonl.gz` and
  `special_stage/aux_state.jsonl.gz` decompress to exactly **0 bytes**.
- Pure ASCII throughout; no BOM.

---

## 10. Version-stamp provenance: fixture "3.15" vs Lua "3.17"

The runs/ and special_stage/ fixtures are stamped `lua_script_version
"3.15"`, but the committed Lua at HEAD stamps `"3.17"` (L513, L596, L812).
Facts established from git history (do not re-derive):

- `203e647b8` introduced the detour machine + manifest and bumped 3.14→3.15.
  That committed 3.15 **hardcoded** `source_bk2 "s1-complete-run.bk2"` and
  used raw `<zone><act>` level tokens (no `_N` dedup), and had no
  `OGGF_TRACE_OUTPUT_DIR`/`OGGF_TRACE_SOURCE_BK2` env support.
- The fixtures contain `source_bk2 "s1-ghz-maze-roundtrip.bk2"` while stamped
  3.15 — the committed 3.15 could not emit that byte sequence, so the capture
  used an interim/patched script between the 3.15 and 3.17 commits. The
  fixture bytes, not any committed Lua revision, are the gate's ground truth.
- `b1a810536` bumped 3.15→3.17 (no 3.16 ever existed in-tree; the header
  comment block documents neither 3.15, 3.16 nor 3.17). The complete
  3.15→3.17 output-affecting delta set: (a) the three version strings; (b)
  `source_bk2` becomes env-driven via `%q` (renders identically to the old
  literal for plain filenames); (c) level dir tokens gain `next_segment_dir_token`
  dedup (first entries unchanged); (d) `BASE_OUTPUT_DIR` env override; (e)
  env-gated FZ `rng_call` aux events + a conditional `"rng_call_per_frame"`
  aux_schema_extras entry (only when `OGGF_S1_RNG_CALL_RANGE` is set AND the
  segment is zone 5 act index 2 — never in these fixtures); (f)
  `OGGF_TRACE_VISIBLE`. With only the run/source/output env vars set and no
  repeated zone+act, current-Lua output matches the fixtures byte-for-byte
  **except the three `"3.15"` → `"3.17"` version strings and
  `recording_date`**.
- Consequence for the differential gate: the handed-down rule "3.15-stamped
  fixtures get NO version normalization" collides with the current Lua's
  "3.17" — that rule presumed the port would stamp "3.15", but the Lua is
  the behavioral authority and stamps "3.17", so a byte-identical version
  line is unattainable against immutable fixtures. Adjudicated resolution
  (adversarial-review round): the native recorder stamps the current Lua's
  "3.17" everywhere; the ROM-backed gates drive the production CLI and
  compare with exactly one pinned-line substitution (fixture line must be
  exactly the `"3.15"` string, produced line must be exactly `"3.17"`,
  exactly once per file, every other byte exact) — which also pins the
  production stamp itself, unlike injecting "3.15" into the runner, and
  matches the precedent already merged for S2 (9.11-s2 -> 9.12-s2 in
  S2TraceDifferentialTests). The byte-neutrality of the stamp delta is the
  verified fact set above, not an assumption. (The separate 3.14-stamped
  `*_completerun` fixtures follow the same resolution via spec
  s1-complete-run-behavior.md §2.)

---

## 11. Provenance of the standalone `special_stage/` fixture

`src/test/resources/traces/s1/special_stage/{physics.csv.gz,
aux_state.jsonl.gz, metadata.json}` are **byte-identical** to
`runs/s1-ghz-maze-roundtrip/ss/` (verified with `cmp` on the decompressed
payloads and the metadata, including `"run_id": "s1-ghz-maze-roundtrip"`,
`"segment_index": 1`, `bk2_frame_offset 4957`, `trace_frame_count 3091`), and
its `s1-ghz-maze-roundtrip.bk2` is byte-identical to the run dir's copy.

There was **no separate standalone invocation or profile**: the standalone
fixture is a published copy of the same run capture's `ss/` segment, movie
included so the trace-replay BK2 resolver finds it. A native port validating
against `special_stage/` therefore validates the identical bytes as the run
gate's `ss/` comparison — same writer, same detour, same run.

---

## 12. S1-vs-S2 run-mode delta table

| Aspect | S1 (`s1_complete_run_recorder.lua`) | S2 (`s2_trace_recorder.lua` v9.12-s2) |
|---|---|---|
| Activation | detour machine always on; `OGGF_TRACE_RUN_ID` only adds run_id fields + forces manifest for detour-free runs; manifest auto-emits when any transition occurred | everything gated on `run_id ~= nil` |
| Level→SS entry kind | `giant_ring` ($0C→$10 via Got-Through card + f_bigring) | `starpost_special` |
| Transition fields | rings/emeralds only | + `special_bonus_entry_flag`, `saved_x/y_pos`, `last_star_post_hit` |
| `rings_after` | carried through SS (fixture 67) | zeroed by ROM reload (fixture 0) |
| Level dir tokens | `<zone><act>` (+`_N` dedup) | `seg<levelcount>_<zone><act>` |
| Level metadata in run | unchanged (no run_id/segment_index lines) | gains `run_id` + `segment_index` |
| SS CSV | 14 cols, single character, `frame`/`lag` decimal + lowercase hex | 48 cols, Sonic+Tails blocks |
| SS input | shared `bk2_input_mask` (no Start bit, P1 only) | dedicated mask incl. Start `0x80`, P2 column |
| SS index source | `v_lastspecial $FFFE16` pre-SS_Load (skip-loop caveat + finalize self-check) | `$FFFE16` equivalent, no skip-loop caveat |
| Movie length | raw `movie.length()` only | `max(movie.length(), OGGF_BK2_FRAME_COUNT)` |
| `source_bk2` | `OGGF_TRACE_SOURCE_BK2` env, `%q` | BK2 basename, `json_escape` |
| Post-SS state reset | none — trackers carry across (§8) | `reset_recording_state_keep_files()` |
| ROM checksum literal | `"AFE05EEE"` | `"7B905383"` |
| SS aux file | opened, never written, 0 bytes | same |
| SS frame-0 alignment | entry frame skipped, row 0 next frame | same |

---

## 13. Porting invariants checklist (native harness)

1. The detour machine and manifest bookkeeping run unconditionally;
   `OGGF_TRACE_RUN_ID` only adds the `run_id` lines and forces manifest
   emission for transition-free runs. Manifest emission rule:
   `#transitions > 0 or run_id present`.
2. SS entry requires an armed level segment (`started && game_mode == 0x10`);
   entry finalizes the level (metadata BEFORE transition push BEFORE ss arm),
   reads all `giant_ring` fields on the entry frame, and writes no ss row on
   that frame.
3. SS row N's input = BK2 index `bk2_frame_offset + N`; level rows identical.
   Entry/arm frames supply offsets and are never recorded.
4. `stage_exit` fields are read on the return-level ARM frame (not the SS-exit
   frame), and only pushed when the previous finished segment is a
   special_stage. `rings_after` is the carried (non-zero) S1 ring count.
5. SS csv: `frame`/`lag` decimal, all else lowercase unpadded hex; unsigned
   u16 reads for vel/inertia; u32 reads for x/y (pixel++sub); ss aux file
   exists and is 0 bytes; rows continue through the `$10` results tally.
6. `current_ss_index` sampled at arm time from `$FFFE16` (pre-SS_Load);
   the finalize re-read is stdout self-check only.
7. ss metadata: `run_id` line present iff run_id set; `segment_index` =
   segments finished before this one; `fresh_load` always false; rewrite at
   arm/every 300 rows/finalize.
8. Manifest optional transition fields: presence by transition kind, not by
   value (Lua 0 is truthy); fixed order rings_before, rings_after,
   emeralds_before, emeralds_after; `rom_checksum "AFE05EEE"` inline.
9. `finalize_run_end` routes ss-vs-level by `detour_active` first; manifest
   written exactly once at run end; the main loop must not re-finalize.
10. No cross-segment reset of `known_objects` / `prev_status` /
    `prev_routine` / `prev_ctrl_lock` / `prev_opl_screen` (§8).
11. All run/standalone fixture files are CRLF (including inside `.gz`);
    reproduce via the run-file newline expansion, per fixture bytes.
12. Version strings: fixtures stamp `3.15`, current Lua stamps `3.17`; the
    only other current-Lua-vs-fixture deltas are `recording_date` (§10) —
    resolve the stamp question explicitly in the gate design before comparing.
