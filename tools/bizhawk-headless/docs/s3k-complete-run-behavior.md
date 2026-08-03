# S3K Complete-Run Recorder — Byte-Level SEGMENTATION Specification

> **V5 supersession (2026-08-03).** Current native output has one v5 contract
> and one module-plus-direct timing grammar. Versioned recorder/timing text
> below records predecessor history and does not select live behavior.

> **2026-08-02 recorder-order note.** The maintained native complete-run
> writer is now `6.42-s3k-completerun`, with held-counter final-parent
> retirements attributed from canonical FIFO state transitions and
> hardware-timing events serialized
> in canonical same-frame service order: `vint_service`, module
> `post_objects`, then direct `pre_main_loop`. Non-Candidate-B fixtures remain
> immutable at their existing published stamps; the 67-segment super-emerald
> run and both existing run manifests remain `6.40-s3k-completerun`. This
> recorder correction does not authorize their rewrite. A replacement
> super-emerald capture is a separately reviewed and approved publication
> action.
> The separate 15-segment Sonic-and-Tails Candidate B publication was
> explicitly approved and installed: Candidate B supplied exactly 15 metadata
> files and 14 timing files, while its independent repeat remained
> validation-only. Installed metadata is canonical 6.42 and the timing delta
> from 6.40 is exactly 27 in-place `vint_service`-to-`post_objects`
> substitutions across 14 segments; physics, aux, and ending timing bytes did
> not move. The permanent capture gate now requires direct installed-6.42
> equality and retains the exact 6.40 predecessor identities in a cheap
> non-capture ledger.
>
> **2026-07-27 publication note.** The committed fleet is now native
> recorder v6.37, not the v6.33 Lua fixture generation described in the
> historical sections below. The Sonic+Tails pass publishes all 15 captured
> segments through the ending, and the Knuckles B/C identities were freshly
> captured rather than metadata-patched. Exact destinations, source tokens,
> schemas, hashes, lengths, and timing inventories are frozen in
> `src/test/resources/traces/s3k/hardware-timing-publication.tsv`. The raw
> terminal tokens are curated semantically as `hpz22 -> hpz`, `hpz -> ssz`,
> `ssz -> dez`, `dez23 -> ddz`, and `ddz -> ending`.
>
> This document remains the segmentation research record. Native publication
> authority and review requirements are defined by `../AGENTS.md` and
> `../README.md`; Lua is retained as optional corroboration and diagnostic
> substrate, not as fixture-publishing authority.

Authoritative specification for the **segmentation** half of
`tools/bizhawk/s3k_complete_run_recorder.lua`
(`LUA_SCRIPT_VERSION = "6.33-s3k-completerun"`, 5918 lines, loading
`tools/bizhawk/lib/oggf_trace_common.lua`). This document owns: how one
BK2 movie playback pass is carved into per-zone / per-bonus /
per-special-stage segments, every arm / publish / discard / stop
predicate **with its exact evaluation order relative to the frame
advance**, how `bk2_frame_offset` and `trace_frame_count` are derived,
how repeat visits get the `_2` / `_3` … directory suffixes, how each
segment's `zone_id` / `act` / `kind` / `bonus_stage_type` /
`special_stage_index` are resolved, and the `run_manifest.json` /
`metadata.json` fields that are segmentation-derived.

Three sibling documents remain normative for everything else and are
**not** restated here:

- [s3k-trace-recorder-behavior.md](s3k-trace-recorder-behavior.md) —
  RAM map, `physics.csv` row format, input-column derivation,
  `ADVANCE_ONLY` semantics, `metadata.json` trace_schema-6 byte layout.
- [s3k-aux-events.md](s3k-aux-events.md) — every `aux_state.jsonl`
  event template and the per-frame emission order.
- [s3k-profiles-and-hooks.md](s3k-profiles-and-hooks.md) — the STANDARD
  recorder's three profiles, hook architecture and deferral, env-var
  refusal table.

The shared run/manifest model is
[s1-complete-run-behavior.md](s1-complete-run-behavior.md) §4 and
[s2-run-mode-behavior.md](s2-run-mode-behavior.md) §11; S3K differs from
both in material ways, called out in §2.3.

For the historical v6.33 behavior described below, the Lua and this document
must agree. Current fixture publication follows the native authority policy
linked above.

---

## 0. Canonical fixtures (read-only ground truth)

Three distinct capture passes are committed. They are **not**
interchangeable: they were produced by three different recorder states
and two different movies. Gunzip `.gz` fixtures to a temp dir for
comparison; never modify anything under `src/test/resources/traces/`.

Movies live in `src/test/resources/traces/s3k/_movies/`:
`s3k-complete-sonic-tails.bk2`, `s3-knux-multibonus-ss.bk2` (the latter
is also copied into `runs/s3-knux-multibonus-ss/` and into
`special_stage/`).

### 0.1 Set (A) — complete-run pass over `s3k-complete-sonic-tails.bk2`

Published as `src/test/resources/traces/s3k/<zone>_completerun/`. All
seven carry `trace_profile: complete_run`, `lua_script_version:
6.33-s3k-completerun`, `capture_mode:
physics_animation_aux_without_diagnostic_hooks`, `pre_trace_osc_frames:
1`, and **no** `run_id`. First recorded by commit `192d9c976`
("regenerate consistent S3K v7 fixtures"); last regenerated by
`eb87d681b` for the `ADDR_VBLA_WORD` fix, which is why every committed
S3K fixture now stamps `recording_date: 2026-07-25`.

| Dir | `segment_index` | `zone_id` | `act` | `bk2_frame_offset` | `trace_frame_count` | last recorded BK2 frame | successor arm frame |
|---|---|---|---|---|---|---|---|
| `aiz_completerun` | 0 | 0 | 1 | 941 | 26228 | 27169 | 27170 |
| `hcz_completerun` | 1 | 1 | 1 | 27170 | 31482 | 58652 | 58653 |
| `mgz_completerun` | 2 | 2 | 1 | 58653 | 39398 | 98051 | 98052 |
| `cnz_completerun` | 3 | 3 | 1 | 98052 | 40064 | 138116 | 138117 |
| `icz_completerun` | 4 | 5 | 1 | 138117 | 25393 | 163510 | 163511 |
| `lbz_completerun` | 5 | 6 | 1 | 163511 | 46244 | 209755 | 209756 |
| `mhz_completerun` | 6 | 7 | 1 | 209756 | 28156 | 237912 | 237913 → FBZ (unpublished) |

`segment_index` is the fixture's own metadata field and confirms these
seven are segments 0..6 of the run with **nothing between them** — no
bonus, no special stage, no repeat visit. Zone id 4 (FBZ) is absent
because Flying Battery follows MHZ in the S&K route, not CNZ.

Derivation (see §5.6): every row is reproduced by
`offset(i+1) = offset(i) + rows(i) + 1`.

```
  941 + 26228 + 1 = 27170   ✓ hcz
27170 + 31482 + 1 = 58653   ✓ mgz
58653 + 39398 + 1 = 98052   ✓ cnz
98052 + 40064 + 1 = 138117  ✓ icz
138117 + 25393 + 1 = 163511 ✓ lbz
163511 + 46244 + 1 = 209756 ✓ mhz
209756 + 28156 + 1 = 237913 → predicted FBZ arm frame (not published)
```

Six of the seven `trace_frame_count`s are therefore **cross-validated**
by the successor's `bk2_frame_offset`. `mhz_completerun`'s 28156 is the
one count with no published successor: it is validated only by its own
`physics.csv` row count (28156 data rows + 1 header) and *predicts* the
FBZ arm at BK2 frame 237913. A native port that emits a different MHZ
count is wrong even though set (A) alone cannot prove it; run the
capture past MHZ and check that FBZ arms at 237913.

`941` (the AIZ arm) is emulation-determined — the first BK2 frame at
which `Game_mode == 0x0C` and both control locks read zero after the
title→level handoff. It is not derivable from the fixture set; it is the
differential gate's first assertion.

### 0.2 Set (B) — run pass over `s3-knux-multibonus-ss.bk2`, `run_id = s3-knux-multibonus-ss`

Published as `src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/`
— 25 segment dirs plus `run_manifest.json` plus a copy of the `.bk2`.
Character metadata is `knuckles` (solo), so no `sidekick_interact_object`
events appear.

**Set (B) is ONE capture pass with a retroactive metadata patch** (§8.3)
— not two passes. All 25 dirs' `physics.csv.gz` / `aux_state.jsonl.gz`
and the manifest come from commit `76bdfc0f2` (2026-07-19,
`6.31-s3k-completerun`). Commit `9e3ccdb41` (2026-07-20) then hand-edited
**only** the 8 bonus `metadata.json` files — `git show --name-only
9e3ccdb41` lists exactly those 8 plus the 3 standalone `bonus_*`
`metadata.json` and the Lua, and **no** `.gz` payload — bumping their
`lua_script_version` to `6.32-s3k-completerun`, their `recording_date` to
`2026-07-20`, and inserting `v_int_run_count`. Hence the mixed stamps
inside one run dir; the underlying bytes are homogeneous 6.31.

None of the 25 carries `capture_mode`, i.e. **the pass ran with
`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`** (§10.7). All 25 — bonus dirs
included — carry `pre_trace_osc_frames: 0` and all-zero `vfc` /
`gameplay_frame_counter`, because the pass predates the
`ADDR_FRAMECOUNT 0xFE08 → 0xFE04` fix (commit `6564667eb`), which did
**not** bump `LUA_SCRIPT_VERSION`. That the 6.32-stamped bonus dirs still
carry the 0xFE08 artefacts is the direct proof that the stamp was patched
in rather than re-captured.

| # | `dir` | `kind` | `trace_profile` | `bk2_frame_offset` | `trace_frame_count` | `zone_id` | `act` | extra | `offset(i)+rows(i)+1` |
|---|---|---|---|---|---|---|---|---|---|
| 0 | `aiz` | level | `complete_run` | 915 | 4654 | 0 | 1 | | 5570 ✓ |
| 1 | `gumball` | bonus_stage | `s3k_bonus_stage` | 5570 | 1430 | 19 | 1 | `bonus_stage_type: gumball` | 7001 ✓ |
| 2 | `aiz_2` | level | `complete_run` | 7001 | 2140 | 0 | 1 | | 9142 ✓ |
| 3 | `slots` | bonus_stage | `s3k_bonus_stage` | 9142 | 1200 | 21 | 1 | `slots` | 10343 ✓ |
| 4 | `aiz_3` | level | `complete_run` | 10343 | 7568 | 0 | 2 | | 17912 ✓ |
| 5 | `slots_2` | bonus_stage | `s3k_bonus_stage` | 17912 | 1278 | 21 | 1 | `slots` | 19191 ✓ |
| 6 | `aiz_4` | level | `complete_run` | 19191 | 3210 | 0 | 2 | | 22402 ✓ |
| 7 | `gumball_2` | bonus_stage | `s3k_bonus_stage` | 22402 | 1648 | 19 | 1 | `gumball` | 24051 ✓ |
| 8 | `aiz_5` | level | `complete_run` | 24051 | 3631 | 0 | 2 | | 27683 ✓ |
| 9 | `hcz` | level | `complete_run` | 27683 | 3176 | 1 | 1 | | 30860 ✓ |
| 10 | `slots_3` | bonus_stage | `s3k_bonus_stage` | 30860 | 5379 | 21 | 1 | `slots` | 36240 ✓ |
| 11 | `hcz_2` | level | `complete_run` | 36240 | 11933 | 1 | 1 | | 48174 ✓ |
| 12 | `ss` | special_stage | `s3k_special_stage` | 48174 | 4630 | 0 | 0 | `special_stage_index: 0` | 52805 → gap |
| 13 | `hcz_3` | level | `complete_run` | 54274 | 3949 | 1 | 2 | | 58224 ✓ |
| 14 | `slots_4` | bonus_stage | `s3k_bonus_stage` | 58224 | 1603 | 21 | 1 | `slots` | 59828 ✓ |
| 15 | `hcz_4` | level | `complete_run` | 59828 | 2097 | 1 | 2 | | 61926 ✓ |
| 16 | `ss_2` | special_stage | `s3k_special_stage` | 61926 | 7194 | 0 | 0 | `special_stage_index: 1` | 69121 → gap |
| 17 | `hcz_5` | level | `complete_run` | 70590 | 3435 | 1 | 2 | | 74026 ✓ |
| 18 | `slots_5` | bonus_stage | `s3k_bonus_stage` | 74026 | 1791 | 21 | 1 | `slots` | 75818 ✓ |
| 19 | `hcz_6` | level | `complete_run` | 75818 | 8422 | 1 | 2 | | 84241 ✓ |
| 20 | `mgz` | level | `complete_run` | 84241 | 8721 | 2 | 1 | | 92963 ✓ |
| 21 | `pachinko` | bonus_stage | `s3k_bonus_stage` | 92963 | 3051 | 20 | 1 | `pachinko` | 96015 ✓ |
| 22 | `mgz_2` | level | `complete_run` | 96015 | 2076 | 2 | 1 | | 98092 ✓ |
| 23 | `ss_3` | special_stage | `s3k_special_stage` | 98092 | 6537 | 0 | 0 | `special_stage_index: 2` | 104630 → gap |
| 24 | `mgz_3` | level | `complete_run` | 106104 | 8517 | 2 | 1 | | terminal |

21 of the 24 successions reproduce exactly under
`offset(i+1) = offset(i) + rows(i) + 1` — including every
level→bonus, bonus→level and level→level boundary, and including the
level→special-stage **entry** boundaries at 11→12 (48174), 15→16 (61926)
and 22→23 (98092). SS entry is the *same* structural case as a level arm
(§5.6): `finalize_segment()` and `start_ss_segment()` run on one frame,
so the successor's `bk2_frame_offset` is that frame.

The three special-stage **exits** are the only boundaries that are not a
`+1` succession, because SS-results (`Game_mode = 0x48`) and the level
reload run between them with no recording:

| SS segment | last SS row frame | next level arm | unrecorded gap |
|---|---|---|---|
| `ss` (12) | 52804 | 54274 (`hcz_3`) | 1469 frames |
| `ss_2` (16) | 69120 | 70590 (`hcz_5`) | 1469 frames |
| `ss_3` (23) | 104629 | 106104 (`mgz_3`) | 1474 frames |

The gap is **not** a constant — do not hard-code 1469. It is
`SS-results + level reload + the locked level intro until both control
locks clear`, and it varies with route state.

### 0.3 Set (C) — second run pass over the same movie, `run_id = s3k-multibonus`

Published as four standalone dirs (`bonus_gumball/`, `bonus_pachinko/`,
`bonus_slots/`, `special_stage/`), not under `runs/`. Same recorder
identity as set (A) and regenerated alongside it (`recording_date
2026-07-25`), so: `6.33-s3k-completerun` **and** the `0xFE04`
frame-counter fix.

| Published dir | recorder dir | `segment_index` | `bk2_frame_offset` | `trace_frame_count` | `capture_mode` | `v_int_run_count` | `pre_trace_osc_frames` |
|---|---|---|---|---|---|---|---|
| `bonus_gumball` | `gumball` | 1 | 5570 | 1430 | present | 5529 | 1 |
| `bonus_slots` | `slots` | 3 | 9142 | 1200 | present | 9097 | 1 |
| `bonus_pachinko` | `pachinko` | 21 | 92963 | 3051 | present | 92662 | 1 |
| `special_stage` | `ss` | 12 | 48174 | 4630 | **absent** | **absent** | n/a (field not in SS metadata) |

The offsets, frame counts, `segment_index`es and `v_int_run_count`s are
**identical to the corresponding set-(B) segments**, which is the
strongest available proof that segmentation is deterministic across
recorder versions 6.31→6.32 and across hooks-on vs hooks-off: the same
movie carved the same way twice, 4 days apart, on two different recorder
builds.

The rename `gumball → bonus_gumball`, `ss → special_stage` etc. is a
**publisher** action (`NoReplacePublisher`), not a recorder action. The
recorder always writes `BASE/gumball/`, `BASE/ss/`, … (§5.5). The
publisher also cherry-picked only the *first* occurrence of each detour
kind; `gumball_2`, `slots_2..slots_5`, `ss_2`, `ss_3` from this pass were
not published.

`special_stage/` carrying neither `capture_mode` nor `v_int_run_count`
is **not** an anomaly to explain away: SS segments are written by a
completely separate metadata writer (§8.2) that emits neither field
under any configuration.

---

## 1. Frame model and evaluation order

### 1.1 The loop

```lua
while true do
    on_frame_end()
    if not finished and emu.framecount() >= FRAME_CAP then finished = true end
    if finished then <end-of-run finalize>; write_run_manifest(); break end
    if client.ispaused() then client.unpause() end
    emu.frameadvance()
end
```

`on_frame_end()` runs **before** `emu.frameadvance()`, i.e. it observes
the machine state at the **end of** BizHawk frame `emu.framecount()`.
The native harness's contract is therefore: *advance to frame N, then
evaluate the whole `on_frame_end` predicate chain against frame N's
end-of-frame state, in source order.* This is the same POST-advance
ordering that the S1 and S2 ports each got wrong once — the entire
arm/publish/stop chain must be evaluated after the advance, in the
listed order, with the early `return`s preserved.

Iteration 0 evaluates against the frame at which the script was loaded
(BK2 frame 0 for a fresh movie start), before any advance.

`FRAME_CAP = absolute_frame_cap()` is computed once at load:
`max(movie.length(), OGGF_BK2_FRAME_COUNT)`, further lowered to
`OGGF_TRACE_STOP_FRAME` when that is smaller and non-zero, then `+ 64`;
`2000000` when no bound exists. It is a runaway backstop only and never
fires in any fixture capture.

### 1.2 The ordered predicate chain (`on_frame_end`, Lua 5274–5786)

Every step below is evaluated in this order. `⇥return` = returns from
`on_frame_end` for this frame; `⇥finish` = sets `finished = true` and
returns, which routes to the end-of-run finalize in the main loop.

| # | Lua | Guard | Condition | Effect |
|---|---|---|---|---|
| 1 | 5275 | — | `finished` | ⇥return |
| 2 | 5289 | — | `movie.isloaded()` and `movie.length() > 0` and `emu.framecount() >= movie.length()` | ⇥finish |
| 3a | 5301 | `HEADLESS and started` | `OGGF_TRACE_STOP_FRAME ~= nil and trace_frame >= it` | ⇥finish |
| 3b | 5308 | `HEADLESS and started` | `OGGF_BK2_FRAME_COUNT > 0 and (bk2_frame_offset + trace_frame) >= it` | ⇥finish |
| 3c | 5316 | `HEADLESS and started` | `not movie.isloaded()` | ⇥finish |
| 4 | 5325–5326 | — | read `game_mode = u8[0xF600]`, `zone_id = u8[0xFE10]` | — |
| 5 | 5335 | `started and game_mode == 0x34` | `detour_active ~= "special_stage"` | SS **entry**: `finalize_segment()`; push `giant_ring` transition; `start_ss_segment()`; `detour_active = "special_stage"`; ⇥return |
| 6 | 5364 | `started and game_mode == 0x34` | else (continuation) | `write_ss_row()`; ⇥return |
| 7 | 5367 | — | `detour_active == "special_stage"` (first non-`0x34` frame) | `finalize_ss_segment()`; `detour_active = nil`; if **not** `is_level_family_mode(game_mode)` then ⇥return, else fall through |
| 8 | 5411 | — | `game_mode == 0x0C` **and** `zone_id ~= current_segment_zone` **and** `u16[0xB032] == 0` **and** `u8[0xF7CA] == 0` | **arm**: `finalize_segment()` if `started`; push `stage_exit` and/or `starpost_bonus` transitions; `start_new_segment(zone_id)`; ⇥return (**arm frame is NOT recorded**) |
| 9 | 5480 | — | `not started` | ⇥return |
| 10 | 5496–5501 | — | `not is_level_family_mode(u8[0xF600])` (re-read) | ⇥return (**no row; segment stays armed and unfinalized**) |
| 11 | 5511 | `HEADLESS and movie.isloaded()` | `(bk2_frame_offset + trace_frame) >= end_frame_limit` | ⇥finish |
| 12 | 5518 | `HEADLESS and movie.isloaded()` | `not allow_post_movie_tail and movie.mode() == "FINISHED"` | ⇥finish |
| 13 | 5527 | — | `not pre_trace_snapshots_written` | emit `cpu_state_snapshot` + `object_state_snapshot`s; set `start_gameplay_frame_counter = u16[0xFE04]` |
| 14 | 5546+ | — | — | write the `physics.csv` row (5586), all aux events (5633–5783), `scan_objects`, then `trace_frame = trace_frame + 1` |

Notes that matter for a byte-identical port:

- **Step 2 is unconditional** (not gated on `started`) and sits *above*
  everything. It is the reason the last segment of a movie stops at
  `movie.length() - 1` rather than running into the post-movie attract
  loop. The STANDARD recorder has no equivalent.
- Step 10 re-reads `0xF600` into a fresh local. Same address, same
  frame, same value — a redundant read with no behavioral effect. Do not
  "fix" it into a reordering.
- Step 10 returning (rather than finishing) is what makes the trailing
  `0x4C` / `0x8C` zone-exit handoff frames land in the **current**
  segment, and what makes a `0x00` / `0x04` / `0x08` excursion silently
  skip rows without ever closing the segment.
- Step 11's `end_frame_limit` is `movie.length()`, raised to
  `OGGF_BK2_FRAME_COUNT` when that is larger (`allow_post_movie_tail`).
  **`allow_post_movie_tail` cannot actually extend recording while a
  movie is loaded:** step 2 is unconditional and fires at
  `emu.framecount() == movie.length()`, one frame before either step 3b
  or step 11 could fire on the raised limit. It is reachable only in the
  no-movie / `movie.length() == 0` configuration, which no fixture uses.
- Steps 3a/3b/3c and 11/12 apply to SS segments too, because `started`
  and `trace_frame` are shared state (§5.3).

---

## 2. Relationship to the STANDARD S3K recorder

### 2.1 Byte-shared (delegate, do not re-implement)

The complete-run recorder is a fork of `s3k_trace_recorder.lua` and its
per-frame capture is character-for-character the same:

- The whole RAM address map, `read_speed`/`hex`/`angle_to_ground_mode`/
  `json_quote`/`bk2_input_mask`/`write_aux` leaf helpers (both load
  `lib/oggf_trace_common.lua`).
- `open_files()` — identical 42-column `physics.csv` header.
- The `physics.csv` row `string.format` — identical specifier string.
- `is_level_family_mode()` — `(game_mode & 0x0F) == 0x0C`.
- The pre-trace snapshot pair, `emit_s3k_semantic_events`,
  `emit_player_mode_event`, `check_mode_changes`, `write_tails_cpu_per_frame`,
  `write_oscillation_per_frame`, `write_game_paused_per_frame`,
  `write_object_states_per_frame`, `write_interact_state_per_frame`,
  `write_sidekick_interact_object_state`,
  `write_air_countdown_state_per_frame`, `write_state_snapshot`,
  `write_control_lock_state`, `scan_objects` — same bodies, same
  frame-end order.
- `write_metadata()`'s field order and byte layout (deltas in §8).
- `character_metadata_json()` — `Player_mode` (`0xFF08`) → team.
- Row/metadata flush cadence: CSV flush every 60 rows; metadata rewrite
  every 300 rows; aux flushed per line.

The native port must **delegate** all of the above to the already-ported
STANDARD classes (`S3KRam`, `S3KTraceCsvWriter`, `S3KAuxEventEngine`,
`S3KTraceMetadataWriter`), not fork them.

### 2.2 Different (the segmentation seam)

| Aspect | STANDARD (`s3k_trace_recorder.lua` 6.30) | COMPLETE-RUN (6.32) |
|---|---|---|
| `TRACE_PROFILE` | `os.getenv("OGGF_S3K_TRACE_PROFILE") or "gameplay_unlock"` | **hardcoded** `"complete_run"` |
| Arm count | exactly one, `if not started then …` | unbounded; one per zone change |
| Arm predicate | `should_start_recording(game_mode)` (profile-dependent) | inline: `game_mode == 0x0C and zone_id ~= current_segment_zone and ctrl_lock_timer == 0 and ctrl_locked == 0` |
| Arm-frame row | `aiz_end_to_end` falls through (arm frame **is** row 0); other profiles arm-and-return | **always** arm-and-return |
| Leaving the level family | `gameplay_unlock`: `game_mode ~= 0x0C` → **finish** | ⇥return only; segment stays armed (step 10) |
| Zone change while armed | `level_gated_reset_aware`: **finish** | finalize + re-arm |
| Soft-reset discard | `should_discard_and_reset` live for `level_gated_reset_aware` | **dead code** (profile can never be `level_gated_reset_aware`) |
| `0x34` / `0x48` | no handling; `gameplay_unlock` finishes, others pollute | full detour state machine (§5.3) |
| Output dir | single `OUTPUT_DIR` | `BASE/<dirToken>/`, repointed per segment |
| Zone name table | `ZONE_NAMES` | `ZONE_TOKEN` / `zone_token_for()` — **different values for ids ≥ 10** (§6.1) |
| Movie-input-end guard | absent | step 2, unconditional, top of chain |
| `run_manifest.json` | never | conditionally (§7) |
| `aiz_fire_transition` aux | emitted for `aiz_end_to_end` | **never** (gated on `is_aiz_end_to_end_profile()`, always false here) |
| `aiz_boundary_state`, `aiz_transition_floor_solid`, `terrain_wall_sensor`, `aiz_ship_loop` in `aux_schema_extras` | declared for `aiz_end_to_end` | **not** declared (the `complete_run` branch declares the cage/cylinder/collision set instead) — see §8.1 |
| Env surface | 24 `OGGF_*` | 25: **drops** `OGGF_S3K_TRACE_PROFILE`, **adds** `OGGF_TRACE_RUN_ID` and `OGGF_BK2_BASENAME` |

### 2.3 Different from the S1 / S2 complete-run + run ports

- **S1** segments on *level* (`Current_Zone_and_Act`) changes and
  finalizes on mode exit. S3K segments on **zone id only** and does
  **not** re-arm on act change: a seamless act1→act2 transition stays
  inside one segment (this is why every set-(A) segment records
  `act: 1`, §6.3).
- S1/S2 run-mode published files are CRLF, reproduced port-side by
  `ExpandRunNewlines`; S3K's convention is **per capture host, not per
  game and not per publication layout** (§9).
- S3K's detour model has *two* stage families (bonus stages, which are
  level-family `Game_mode` with zone ids 0x13–0x15, and special stages,
  which are `Game_mode == 0x34` with their own CSV schema). S1/S2 have
  no equivalent of the bonus family riding the ordinary arm gate.

---

## 3. Configuration inputs affecting segmentation

| Env var | Effect on segmentation | Fixture value |
|---|---|---|
| `OGGF_TRACE_OUTPUT_DIR` | `BASE_OUTPUT_DIR`; trailing `/` or `\` appended if missing. Every segment writes `BASE/<dirToken>/`. | scratch dir |
| `OGGF_TRACE_RUN_ID` | `run_id`. Adds `"run_id"` to level/bonus **and** SS metadata, and forces `run_manifest.json` even with zero transitions. | (A) unset; (B) `s3-knux-multibonus-ss`; (C) `s3k-multibonus` |
| `OGGF_BK2_BASENAME` | `SOURCE_BK2_NAME` (`source_bk2` in every metadata and in the manifest). Default `"s3k-complete-sonic-tails.bk2"`. | (A) unset (default); (B)/(C) `s3-knux-multibonus-ss.bk2` |
| `OGGF_TRACE_STOP_FRAME` | step 3a; also lowers `FRAME_CAP`. Truncates the **current** segment (SS included). | unset |
| `OGGF_BK2_FRAME_COUNT` | steps 3b + 11; `> movie.length()` enables `allow_post_movie_tail`, which suppresses the `movie.mode()=="FINISHED"` stop. | unset |
| `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` | `"1"` ⇒ `LIGHTWEIGHT_REGEN = false` ⇒ hooks registered **and** `capture_mode` omitted from metadata. No effect on segment boundaries (proved by (B) vs (C), §0.3). | (A)/(C) unset; (B) `1` |
| `OGGF_TRACE_QUIET` | `"1"` replaces `print`; byte-neutral. | — |
| `OGGF_BIZHAWK_LIB` | shared-lib lookup only. | launcher-set |
| 17 × `OGGF_S3K_*` window vars | per-aux-event frame windows. **All windows are `trace_frame`-relative, i.e. per segment** (§10.6). No effect on boundaries. | all unset |

`OGGF_S3K_TRACE_PROFILE` is **not read** by this recorder. A native CLI
must not accept it here.

---

## 4. Segmentation state

Scope is **mixed**, and the split is arbitrary — a native port should
treat all of it as one recorder-instance state bag. The `Lua` column
below is the declaration line; `global` entries were deliberately left
unscoped because the main chunk sits at Lua 5.4's 200-local cap, but the
older frame/segment counters predate that pressure and are still
`local`:

| Name | Lua | Scope | Meaning | Reset by |
|---|---|---|---|---|
| `started` | 873 | local | a segment (level, bonus **or** SS) is armed and open | `reset_recording_state` |
| `finished` | 874 | local | main-loop termination latch | never |
| `trace_frame` | 875 | local | rows written in the current segment; next row's `frame` column | `reset_recording_state`; explicitly `= 0` in `start_ss_segment` |
| `bk2_frame_offset` | 876 | local | BK2 frame at which the current segment armed | set at arm |
| `current_segment_zone` | 884 | **global** | ROM zone id of the armed **level/bonus** segment; `nil` when none | `nil` at every finalize; never set by `start_ss_segment` |
| `segments_done` | 885 | **global** | ordered list of finalized segment records | never reset |
| `transitions_done` | 895 | **global** | ordered list of boundary records | never reset |
| `segment_dir_counts` | 896 | **global** | base token → visit count; drives the `_2`/`_3` suffix | never reset |
| `detour_active` | 897 | **global** | `nil` \| `"special_stage"` | SS entry / SS exit |
| `current_segment_dir_token` | 898 | **global** | dir name of the current segment (`aiz`, `aiz_3`, `ss_2`, …) | set at arm |
| `current_segment_is_bonus` | 899 | **global** | `BONUS_TOKENS[zone_id] ~= nil` at arm | set at arm |
| `current_ss_index` | 902 | **global** | `u8[0xFE16]` read at SS arm | SS arm; `nil` at SS finalize |
| `ss_prev_spheres_left`, `ss_spheres_left_increased`, `ss_prev_started`, `ss_started_transitions` | 903–906 | **global** | SS self-check accumulators — **print-only**, never written to any file | SS arm / SS finalize |
| `run_id` | 907 | **global** | `OGGF_TRACE_RUN_ID` | — |
| `start_x`, `start_y`, `start_zone_id`, `start_zone_name`, `start_act`, `start_rng_seed` | 909–914 | local | per-segment metadata captured at arm | `reset_recording_state` |
| `start_v_int_run_count` | 919 | **global** | bonus-only `u32be[0xFE0C]` at arm; `nil` otherwise | `reset_recording_state` |
| `start_gameplay_frame_counter` | 920 | local | arm-time lfc, **overwritten** at step 13 | `reset_recording_state` |
| `pre_trace_snapshots_written` | 941 | local | one-shot latch for the step-13 snapshots | `reset_recording_state` |

`reset_recording_state(keep_files)` closes both files, clears `started`,
zeroes `trace_frame` / `bk2_frame_offset` / all `start_*`, clears the
per-frame diagnostic accumulators, and — **only when `keep_files` is
false** — `os.remove`s `physics.csv`, `aux_state.jsonl`, `metadata.json`
from `OUTPUT_DIR`. Both finalize paths call it with `true`. In this
recorder there is no call site with `false`, so the delete branch is dead
code; a native port must not delete published segment files.

---

## 5. The segmentation state machine

### 5.1 Level / bonus arm gate (step 8)

```
game_mode == 0x0C                       -- RAW 0x0C, NOT is_level_family_mode
  AND zone_id ~= current_segment_zone   -- zone_id = u8[0xFE10]
  AND u16be[0xB032] == 0                -- Player_1 + $32, ctrl-lock timer
  AND u8[0xF7CA] == 0                   -- Ctrl_1_locked
```

Every clause is load-bearing:

- **RAW `0x0C`, not the `0x0F` mask.** During the `0x4C` / `0x8C`
  level-load handoff the player object is not yet placed (`x_pos`/`y_pos`
  read 0) and `Ctrl_1_locked` can briefly read 0. Arming there produces a
  wrong frame-0 camera/position and a wrong `start_x`/`start_y`.
- **Both control gates.** Arming at the raw zone-entry frame (during the
  locked `0x0C` intro, camera still settling under load lag) produced a
  systematic frame-0 camera/position/lock mismatch in every segment. The
  gate skips exactly that locked intro. This is byte-identical to the
  STANDARD recorder's `gameplay_unlock` `should_start_recording`.
- **`zone_id ~= current_segment_zone` is a ONE-TIME gate per segment.**
  Once armed for zone Z, the gate cannot re-fire until the ROM zone
  register moves off Z. Everything after the arm — later control locks,
  the seamless act1→act2 transition, the act2→next-zone exit handoff —
  is recorded into the same segment.

On success, in this exact order:

1. `if started then finalize_segment() end`
2. `stage_exit` transition push, iff `#segments_done > 0` and the
   just-finalized segment's `kind` is `bonus_stage` **or**
   `special_stage`.
3. `starpost_bonus` transition push, iff `BONUS_TOKENS[zone_id] ~= nil`
   (i.e. the zone being armed is 0x13/0x14/0x15).
4. `start_new_segment(zone_id)`
5. ⇥**return — the arm frame is not recorded in any segment.**

Steps 2 and 3 are independent `if`s. A bonus→bonus boundary would push
both with identical `from_segment`/`to_segment`; no fixture exercises it.
Port the two independent pushes verbatim rather than an if/elseif.

### 5.2 `start_new_segment(zone_id)` / `finalize_segment()`

`start_new_segment`:

```
base   = zone_token_for(zone_id)
n      = (segment_dir_counts[base] or 0) + 1 ; segment_dir_counts[base] = n
dir    = (n == 1) and base or (base .. "_" .. n)
OUTPUT_DIR = BASE .. dir .. "/" ; ensure_segment_dir(OUTPUT_DIR)
started = true ; current_segment_zone = zone_id
bk2_frame_offset = emu.framecount()
start_x  = u16be[0xB010] ; start_y = u16be[0xB014]
start_zone_id = zone_id  ; start_act = u8[0xFE11]
start_rng_seed = u32be[0xF636]
start_gameplay_frame_counter = u16be[0xFE04]      -- overwritten at step 13
start_zone_name = zone_token_for(zone_id)
current_segment_is_bonus = (BONUS_TOKENS[zone_id] ~= nil)
start_v_int_run_count = current_segment_is_bonus and u32be[0xFE0C] or nil
open_files() ; write_metadata()
```

`trace_frame` is **not** explicitly reset here — it is already 0, either
from load or from the `reset_recording_state` inside the preceding
`finalize_segment`.

`finalize_segment()`:

```
if not started then return end
physics_file:flush() ; write_metadata()      -- final trace_frame_count
rows = trace_frame ; token = zone_token_for(start_zone_id)
close_files()
segments_done[+1] = {
  token, dir = current_segment_dir_token,
  kind    = current_segment_is_bonus and "bonus_stage" or "level",
  profile = current_segment_is_bonus and "s3k_bonus_stage" or "complete_run",
  bonus_stage_type = current_segment_is_bonus and BONUS_TOKENS[start_zone_id] or nil,
  zone_id = start_zone_id, act = start_act + 1,
  bk2_frame_offset, rows }
reset_recording_state(true) ; current_segment_zone = nil
```

`write_metadata()` runs **before** the `segments_done` append, so the
metadata's `segment_index` (`#segments_done`) is the segment's own
0-based index. Same for `write_ss_metadata()`.

### 5.3 Special-stage detour (steps 5–7)

`GAMEMODE_SPECIAL_STAGE = 0x34`.

**Entry** — `started and game_mode == 0x34 and detour_active ~= "special_stage"`:

1. `finalize_segment()` — closes the level segment whose last row was
   frame `F - 1`.
2. push `giant_ring` transition (§7.2) with `from_segment =
   #segments_done - 1`, `to_segment = #segments_done`, `mode_change_bk2_frame
   = emu.framecount()`, plus `special_bonus_entry_flag` (`u8[0xFE48]`),
   `saved_x_pos` (`u16be[0xFE2E]`), `saved_y_pos` (`u16be[0xFE30]`),
   `last_star_post_hit` (`u8[0xFE2A]`), `rings_before` (`u16be[0xFE20]`),
   `emeralds_before` (`u8[0xFFB0]`).
3. `start_ss_segment()`.
4. `detour_active = "special_stage"`; ⇥return — **the entry frame is not
   recorded in either segment.**

The entry branch is gated on `detour_active`, **never on `started`
alone**: `start_ss_segment` sets `started = true`, so a `started`-only
test would re-finalize/re-open on every `0x34` frame.

`started` is required for entry. **A `0x34` detour that occurs before
the first level segment has ever armed produces no SS segment at all** —
the whole `if` is skipped and the frame falls through to the arm gate,
which cannot pass because `game_mode ~= 0x0C`.

**Continuation** — `started and game_mode == 0x34 and detour_active == "special_stage"`:
`write_ss_row()`; ⇥return. The level-schema row path below is
unreachable for `0x34` frames.

**`start_ss_segment()`**:

```
n   = (segment_dir_counts["ss"] or 0) + 1 ; segment_dir_counts["ss"] = n
dir = (n == 1) and "ss" or ("ss_" .. n)
OUTPUT_DIR = BASE .. dir .. "/" ; ensure_segment_dir(OUTPUT_DIR)
started = true ; bk2_frame_offset = emu.framecount() ; trace_frame = 0
current_ss_index = u8[0xFE16]                  -- Current_special_stage
ss_prev_spheres_left = nil ; ss_spheres_left_increased = false
ss_prev_started = nil ; ss_started_transitions = 0
open physics.csv (20-column SS header) + aux_state.jsonl ; write_ss_metadata()
```

It deliberately does **not** set `current_segment_zone`; that stays `nil`
from the preceding `finalize_segment`, so the level arm gate's
`zone_id ~= current_segment_zone` is trivially true when the detour ends.

**Exit** — first non-`0x34` frame with `detour_active == "special_stage"`
(step 7), evaluated **before** the arm gate so it closes exactly once
regardless of what mode follows:

1. `finalize_ss_segment()` — flush, `write_ss_metadata()`, close, append
   `{kind="special_stage", profile="s3k_special_stage",
   special_stage_index=current_ss_index, zone_id=0, act=0, …}`,
   `reset_recording_state(true)`, `current_segment_zone = nil`.
2. `detour_active = nil`.
3. If `is_level_family_mode(game_mode)` → fall through to the arm gate
   (which will not pass yet on a `0x4C`/`0x8C` frame, and will pass on
   the first settled `0x0C`). Otherwise (`0x48` SS-results, fades)
   ⇥return.

`write_ss_row` carries its **own** flush cadence, mirroring the level
path but calling the SS writer: `physics_file:flush()` every 60 rows and
`write_ss_metadata()` every 300 rows (Lua 5205–5206). Neither changes
final bytes — `finalize_ss_segment` rewrites the metadata with the final
`trace_frame_count` — but a port that streams must match the flush points
if it asserts on partial output.

**SS segments emit no aux events at all.** `write_ss_row` writes only
CSV. No profile aux engine runs for this segment. Legacy captures opened and
closed `aux_state.jsonl` empty; 6.39 audited captures with
`--load-queue-state` write direct then module physical queue state for every
stored row. The committed
`ss`, `ss_2`, `ss_3` and `special_stage` aux fixtures are all 0 bytes
uncompressed.

### 5.4 Bonus stages are NOT a detour

Gumball / pachinko / slots run under the ordinary level `Game_mode`
family with `Current_zone` = 0x13 / 0x14 / 0x15. They therefore arm,
record and finalize through the **exact same** level path — same
42-column `physics.csv`, same aux stream, same `write_metadata`. The
only bonus-specific behavior is `current_segment_is_bonus`, which
switches `kind`/`trace_profile`, adds `bonus_stage_type`, and captures
`v_int_run_count`.

`BONUS_TOKENS = {[0x13]="gumball", [0x14]="pachinko", [0x15]="slots"}`.
`BONUS_ZONE_MIN/MAX` (0x13/0x15) exist but are documentation only — the
code always uses the table lookup, so an unmapped id in the range would
be treated as an ordinary zone. Port the lookup, not a range check.

### 5.5 Directory naming and the `_2`/`_3` suffix

`zone_token_for(zone_id)`:

```
BONUS_TOKENS[zone_id]                          -- 0x13/0x14/0x15
  or ZONE_TOKEN[zone_id]
  or string.format("zone%02x", zone_id)
```

`ZONE_TOKEN` (**not** `ZONE_NAMES`):

| id | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 22 | 23 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| token | `aiz` | `hcz` | `mgz` | `cnz` | `fbz` | `icz` | `lbz` | `mhz` | `soz` | `lrz` | `hpz` | `ssz` | *(absent)* | `ddz` | `hpz22` | `dez23` |

Suffixing:

```
n   = (segment_dir_counts[baseToken] or 0) + 1
dir = (n == 1) and baseToken or (baseToken .. "_" .. n)
```

`segment_dir_counts` is keyed on the **base token**, is never reset, and
counts *arms*, not zone visits. SS segments share the machinery with key
`"ss"`. Set (B) exercises `aiz`→`aiz_5`, `hcz`→`hcz_6`, `mgz`→`mgz_3`,
`gumball`→`gumball_2`, `slots`→`slots_5`, `ss`→`ss_3`.

`precreate_segment_dirs()` runs once at load and pre-creates `BASE/`
plus one dir per `ZONE_TOKEN` value, per `BONUS_TOKENS` value, and
`BASE/ss/` — in a single `os.execute`, and only after a probe-file test
shows at least one is missing. `ensure_segment_dir(dir)` is a shell-free
probe used at each arm; it shells out only for a token outside the
pre-created set (i.e. `zone%02x` and every `_2`+ suffix dir). Neither
affects output bytes; a native port just needs `Directory.CreateDirectory`.

### 5.6 `bk2_frame_offset` and `trace_frame_count` derivation

Let a segment arm at BK2 frame `F`:

- `bk2_frame_offset = F`.
- Frame `F` writes **no** row (arm-and-return).
- Row `N` is written while observing the end of BK2 frame `F + 1 + N`
  **only while the segment records every frame** — see the skip caveat
  below.
- `trace_frame_count` = number of rows = `trace_frame` at finalize.
- The last recorded BK2 frame is `F + trace_frame_count` (same proviso).
- `physics.csv` has `trace_frame_count + 1` lines (header + rows).

The replay convention is nevertheless **row `N` ⇔ BK2 input frame
`bk2_frame_offset + N`**, and `bk2_input_mask` reads
`movie.getinput(bk2_frame_offset + trace_row, 1)`. This is correct and
deliberate: BizHawk applies the input recorded at frame `k` *during* the
advance from `k` to `k+1`, so the input at `F + N` is exactly the input
that produced the state observed at `F + N + 1`, which is row `N`.

**Skip caveat.** `trace_frame` counts ROWS, not frames. Step 10's hard
mode guard suppresses the row on a non-level-family `Game_mode`
(`$00`/`$04`/`$08`) *without closing the segment*, and the step-8 arm
gate is one-time per zone (`zone_id ~= current_segment_zone`), so a
game-over/continue, a pause+A soft reset back to the title, or an
ending/credits excursion that returns to the SAME zone resumes into the
already-open segment. Across such an excursion of length `k`, the row
written after it is observed at frame `F + 1 + N + k`, so the
`F + 1 + N` identity breaks — but the input index does **not**: it stays
`bk2_frame_offset + trace_row`, i.e. `F + N`, because
`bk2_input_mask` is indexed by the ROW counter. A port must therefore
index the BK2 stream by row (`S3KCompleteRunCaptureRunner.InputRow`) and
must **not** reuse "the row consumed by the immediately preceding
advance", which is the same value only in the contiguous case. Every
published (A)/(B)/(C) segment happens to be frame-contiguous, so no
fixture exercises this; the synthetic gate
`S3KCompleteRunCaptureRunner indexes the input column by BK2 row across
a mid-segment excursion` does.

**The succession identity.** For any boundary where the terminating
frame is *the same frame* that opens the next segment — i.e. every level
arm (step 8) and every SS entry (step 5) — the predecessor's last row is
frame `F' - 1` and therefore:

```
bk2_frame_offset(i+1) = bk2_frame_offset(i) + trace_frame_count(i) + 1
```

This holds for all 6 published (A) successions and the 21 non-SS-exit
(B) successions (§0.1, §0.2). It does **not** hold across an SS **exit**,
because `finalize_ss_segment` and the next level arm are different frames
separated by SS-results and the level reload.

A segment terminated by a stop condition (steps 2/3/11/12) instead of a
boundary has `trace_frame_count` fixed by that stop. Two different
indices are in play and must not be conflated: the **observed BK2
framecount** `c` at which a row is written (row `N` ⇔ `c = F + 1 + N`)
and the row's **input index** `F + N = c - 1` (the replay convention
above). The table gives both:

| Terminator | rows (`trace_frame_count`) | last row's observed framecount `c` | last row's input index |
|---|---|---|---|
| step 2 (`emu.framecount() >= movie.length()`, `= M`) | `M - F - 1` | `M - 1` | `M - 2` |
| step 3a (`OGGF_TRACE_STOP_FRAME = S`) | exactly `S` | `F + S` | `F + S - 1` |
| step 3b / 11 (`limit = L`) | `L - F` | `L` | `L - 1` |
| step 12 (`movie.mode() == "FINISHED"`) | emulator-determined | — | — |
| `FRAME_CAP` backstop | `FRAME_CAP - F - 1` | `FRAME_CAP - 1` | `FRAME_CAP - 2` |

Step 2's row is the one that matters in practice: it is the reason a
movie-terminated final segment ends at observed framecount
`movie.length() - 1`, and (per §1.2) it preempts steps 3b/11/12 whenever
a movie is loaded.

### 5.7 End-of-run finalize

When `finished` becomes true the main loop runs, in order:

1. If `detour_active == "special_stage"`: print a WARNING and
   `finalize_ss_segment()` — **never** `finalize_segment()`, which would
   stamp `kind: "level"` on a directory full of 20-column SS rows and
   still validate against the already-pushed `giant_ring` transition.
   A truncated SS segment is legitimate, correctly-labeled data.
2. Else `finalize_segment()` (a no-op if nothing is armed).
3. Print the segment table.
4. `write_run_manifest()`.
5. `break`, then up to 8 `client.exit()` attempts and `client.pause()`.

There is **no discard path**. Every armed segment is published, however
short. `reset_recording_state(false)`'s file deletion is unreachable.

---

## 6. Resolving a segment's attributes

### 6.1 `zone` (metadata) and `dir`

Both come from `zone_token_for(start_zone_id)` — the complete-run
recorder never uses `ZONE_NAMES`. This matters: `ZONE_NAMES` and
`ZONE_TOKEN` **disagree** for ids ≥ 10 (`ZONE_NAMES[10] = "ssz"` vs
`ZONE_TOKEN[10] = "hpz"`; `[11]` `"dez"` vs `"ssz"`; `[13]` `"hpz"` vs
`"ddz"`; `ZONE_TOKEN[12]` is absent, so id 12 falls through to
`"zone0c"`). A native port that reuses the STANDARD recorder's zone
table will silently mislabel every late-game segment.

For bonus zones `zone_token_for` returns the bonus token, so
`"zone": "gumball"` with `"zone_id": 19` — as in the committed fixtures.

### 6.2 `zone_id`

`start_zone_id` = the `zone_id` **argument** passed to
`start_new_segment`, which is the `u8[0xFE10]` read once at step 4 of the
arming frame. It is *not* re-read at finalize.

SS segments hardcode `zone_id = 0` in the `segments_done` record and omit
zone entirely from `metadata.json`.

### 6.3 `act`

`start_act = u8[0xFE11]` at the arm frame; every emission is
`start_act + 1` (1-based). Consequences:

- Set (A) segments all report `act: 1` **even though each spans act 1
  and act 2** — the act register was 0 at the arm frame and the recorder
  never re-arms on act change.
- Set (B)'s `aiz_3`/`aiz_4`/`aiz_5` report `act: 2` because those arms
  happen after a bonus detour that returned into AIZ act 2.
- Bonus segments report `act: 1` (bonus zones hold act 0).

SS segments hardcode `act = 0` in the manifest record (and have no `act`
in `metadata.json`).

### 6.4 `kind` and `trace_profile`

| Condition at arm | `kind` | `trace_profile` |
|---|---|---|
| `BONUS_TOKENS[zone_id] == nil` | `level` | `complete_run` |
| `BONUS_TOKENS[zone_id] ~= nil` | `bonus_stage` | `s3k_bonus_stage` |
| armed via `start_ss_segment` | `special_stage` | `s3k_special_stage` |

`current_segment_is_bonus` is latched at arm and read again at finalize
and at every periodic `write_metadata`; the ROM zone register is never
re-consulted.

### 6.5 `bonus_stage_type` / `special_stage_index`

- `bonus_stage_type = BONUS_TOKENS[start_zone_id]` — `gumball` (0x13),
  `pachinko` (0x14), `slots` (0x15). Emitted in `metadata.json` only in
  the `current_segment_is_bonus` branch, and in the manifest only for
  `kind == "bonus_stage"`.
- `special_stage_index = u8[0xFE16]` (`Current_special_stage`), read once
  at SS arm. Set (B) shows 0, 1, 2 for `ss`, `ss_2`, `ss_3` — it tracks
  the ROM's stage counter, **not** the `_N` dir suffix. Do not derive one
  from the other.

### 6.6 `segment_index`

`#segments_done` at metadata-write time = the segment's own 0-based
index, because `write_metadata` / `write_ss_metadata` always run before
the `segments_done` append. Verified: (B) `ss` → 12, (C) `bonus_pachinko`
→ 21.

---

## 7. `run_manifest.json`

### 7.1 Emission gate and shape

```lua
if #transitions_done == 0 and run_id == nil then return end
```

So a plain multi-zone complete-run with no detour and no `OGGF_TRACE_RUN_ID`
writes **no manifest** — which is why set (A) has none. Written to
`BASE_OUTPUT_DIR/run_manifest.json` after the last finalize.

Top level, in order: `run_schema: 1`, `game: "s3k"` (never `"sonic3k"` —
`TraceExecutionModel.forGame` rejects that), `run_id` (only if set),
`source_bk2`, `rom_checksum` (`C5B1C655C19F462ADE0AC4E17A844D10`),
`lua_script_version`, `segments`, `transitions`.

Each segment line, one per line, comma-separated except the last:

```
    {"dir": %q, "kind": %q, "trace_profile": %q, "bk2_frame_offset": %d, "trace_frame_count": %d, "zone_id": %d, "act": %d[, "bonus_stage_type": %q | , "special_stage_index": %d]}
```

The `extra` field is `bonus_stage_type` for `kind == "bonus_stage"`,
`special_stage_index` for `kind == "special_stage"`, empty otherwise.

Before writing, each transition is checked for `to_segment ==
from_segment + 1` and `to_segment <= #segments_done`; a violation prints
a WARNING but does **not** suppress the record.

### 7.2 Transition records — when they are pushed

Transitions are pushed at **three** sites, all between a finalize and the
next arm, so `from_segment = #segments_done - 1` and `to_segment =
#segments_done` are exact at push time. **Never derive them from list
position**: plain level→level zone changes are boundaries with *no*
record, so record order does not map to boundary order.

| `entry_kind` | Pushed at | Extra fields |
|---|---|---|
| `giant_ring` | SS entry (step 5), after `finalize_segment()` | `special_bonus_entry_flag`, `saved_x_pos`, `saved_y_pos`, `last_star_post_hit`, `rings_before`, `emeralds_before` |
| `starpost_bonus` | arm gate (step 8), when the zone being armed is 0x13/0x14/0x15 | same six |
| `stage_exit` | arm gate (step 8), when the just-finalized segment's kind is `bonus_stage` or `special_stage` | `rings_after`, `emeralds_after` |

Every record opens with `from_segment`, `to_segment`, `entry_kind`,
`mode_change_bk2_frame` (= `emu.framecount()` at the push frame), always
in that order. The optional numeric fields follow, emitted only when
non-nil, in this **fixed writer order** (Lua 5521–5528) — note that
`rings_after` sits between `rings_before` and `emeralds_before`, so the
order is *not* "entry fields then exit fields":

```
special_bonus_entry_flag, saved_x_pos, saved_y_pos, last_star_post_hit,
rings_before, rings_after, emeralds_before, emeralds_after
```

All eight are `%d` decimal. Fields are joined with `", "` and wrapped as
`    {…}` with a trailing `,` on every record but the last.

Set (B) has 22 transitions for 24 boundaries: the two missing are
`8→9` (`aiz_5`→`hcz`) and `19→20` (`hcz_6`→`mgz`), both plain
level→level. Every `mode_change_bk2_frame` equals the `to` segment's
`bk2_frame_offset` for `starpost_bonus`/`giant_ring` (same frame), and
equals the *next level segment's* `bk2_frame_offset` for `stage_exit`.

---

## 8. Segmentation-derived `metadata.json` fields

Full byte layout is owned by
[s3k-trace-recorder-behavior.md](s3k-trace-recorder-behavior.md), under
"Current v5 container contract". Only the complete-run deltas are specified
here.

### 8.1 Level / bonus segments (`write_metadata`)

Deltas vs the STANDARD writer:

- `"zone"` uses `zone_token_for` (§6.1).
- `notes` is the fixed complete-run string:
  `"Per-zone segment from the S3K complete-run (AIZ->Doomsday) Sonic+Tails movie. Covers act1 -> seamless act1->act2 -> the act2->next-zone exit handoff (trailing 0x8C frames). Game_paused aux flag is comparison-only."`
  — emitted verbatim for **every** segment including bonus segments and
  non-Sonic+Tails runs. It is a constant, not a description; do not
  template it.
- `capture_mode` line present **iff** `LIGHTWEIGHT_REGEN`
  (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS ~= "1"`).
- `aux_schema_extras` takes the `TRACE_PROFILE == "complete_run"` branch:
  the 12 base entries followed by exactly
  `cage_state_per_frame`, `cage_execution_per_frame`,
  `cnz_cylinder_state_per_frame`, `cnz_cylinder_execution_per_frame`,
  `solid_object_cont_entry_per_frame`,
  `collision_response_list_per_frame`,
  `collision_response_list_end_of_frame` — 19 entries, identical in every
  committed fixture regardless of zone.
- `trace_profile` / `bonus_stage_type` / `v_int_run_count` per §6.4/§6.5.
- `run_id` line present iff `OGGF_TRACE_RUN_ID` is set.
- `segment_index` per §6.6.

`v_int_run_count` is `mainmemory.read_u32_be(0xFE0C)` captured at arm,
emitted **as a decimal integer**, and only when
`current_segment_is_bonus`. `start_v_int_run_count` stays `nil` for level
segments, so the key is absent — not `null`, absent.

### 8.2 Special-stage segments (`write_ss_metadata`)

An entirely separate writer with its own field order:

```
game, trace_profile ("s3k_special_stage"), special_stage_index,
ss_csv_version (1), <character block>, bk2_frame_offset,
trace_frame_count, source_bk2, lua_script_version, recording_date,
bizhawk_version, genesis_core, rom_checksum, [run_id], fresh_load (false),
segment_index
```

It emits **no** `zone`, `zone_id`, `act`, `start_x`, `start_y`,
`rng_seed`, `pre_trace_osc_frames`, `trace_schema`, `csv_version`,
`aux_schema_extras`, `notes`, **`capture_mode`** or **`v_int_run_count`**
— under any configuration. `fresh_load` is a hardcoded `false` (giant-ring
entries are always mid-level).

This is the complete and only explanation for set (C)'s
`special_stage/` lacking both fields while its three bonus siblings carry
both. There is no conditional to model.

### 8.3 Empirically pinned version / address deltas

Two independent changes affect committed metadata bytes. **Only one of
them moved `LUA_SCRIPT_VERSION`.**

| Change | Commit | `LUA_SCRIPT_VERSION` | Observable |
|---|---|---|---|
| `v_int_run_count` added (bonus segments only) | `9e3ccdb41` (2026-07-20) | `6.31` → `6.32` | key absent in every 6.31-stamped fixture; present in every 6.32-stamped **bonus** fixture |
| `ADDR_FRAMECOUNT 0xFE08 → 0xFE04` | `6564667eb` (after the above) | **unchanged, stays `6.32`** | `pre_trace_osc_frames` `0` → `1` |

The full 6.31→6.32 diff on this file is exactly: the version string,
`ADDR_V_INT_RUN_COUNT = 0xFE0C`, the `start_v_int_run_count` global +
its reset, the conditional metadata line, and the arm-time capture.
**No segmentation logic changed.** That is why sets (B) and (C) carve the
same movie identically.

Per-fixture assertions for the native differential gate (exact literals,
never a regex):

| Fixture set | `lua_script_version` | `capture_mode` | `pre_trace_osc_frames` | `v_int_run_count` |
|---|---|---|---|---|
| (A) all 7 | `6.33-s3k-completerun` | present | `1` | n/a (level) |
| (B) 14 level + manifest | `6.33-s3k-completerun` | present | `1` | n/a |
| (B) 3 ss | `6.33-s3k-completerun` | n/a | n/a | n/a |
| (B) 8 bonus | `6.33-s3k-completerun` | present | `1` | present |
| (C) 3 bonus | `6.33-s3k-completerun` | present | `1` | present |
| (C) 1 ss | `6.33-s3k-completerun` | n/a | n/a | n/a |

A current-HEAD native port reproduces **all three** columns byte for
byte. (B) used to be the exception — it was a Windows, hooks-on,
`0xFE08`-era capture with a hand-edited mixed 6.31/6.32 stamp — but
commit `63eccd290` re-captured it on the same recorder identity as (A)
and (C), so the three sets now differ only in publication layout and
`run_id`. §0.2.1 keeps the superseded facts because the rules they
produced (LF everywhere, `0xFE04`, hooks off) are still load-bearing;
a regeneration that reintroduces any of them is a regression, not a
new baseline.

Every set was regenerated once more by `eb87d681b` for the
`ADDR_VBLA_WORD` `0xFE12` → `0xFE0E` fix, which moved every
`physics.csv` hash carrying a `vblank_counter` column (i.e. all but the
`s3k_special_stage` segments, whose writer has no such column) and left
every `aux_state.jsonl` byte-identical.

---

## 9. File encodings — as observed in this fixture set

Newlines are **per capture, not per game, per recorder, per `run_id`, or
per publication layout**:

| Location | `metadata.json` | `physics.csv` | `aux_state.jsonl` | `run_manifest.json` |
|---|---|---|---|---|
| `traces/s3k/<zone>_completerun/` (A) | LF | LF | LF | — |
| `traces/s3k/bonus_*`, `traces/s3k/special_stage/` (C) | LF | LF | LF | — |
| `traces/s3k/runs/<run_id>/**` (B) | LF | LF | LF | LF |

(B) was **CRLF** in every column until commit `63eccd290` re-captured it
on Linux; the S3K tree is now LF throughout. The rule the CRLF row
established still stands and is why the row is kept: the Lua only ever
writes `\n`, so CRLF is the host text-mode artefact of
`io.open(path, "w")` on the machine that ran the capture. There is no
Lua-side publisher, so nothing rewrites newlines between capture and
commit — which means a CRLF fixture is a capture-host fact, and the
native port must never emit one.

**The tempting rule "CRLF keys on publication into `runs/<run_id>/`" is
falsified by the fixture tree.** Census over all 142 committed
`metadata.json` files (recounted after the S3K regenerations): 44 are
CRLF — three `runs/` trees (`s1/runs/s1-ghz-maze-roundtrip`,
`s2/runs/s2-ehz-halfpipe-roundtrip`,
`s2/runs/s2-sonic-tails-complete-emeralds`) **plus
`traces/s1/special_stage/`**, which is not under any `runs/` tree and was
committed by `70233ae6d` on the same 2026-07-19 date as the S1 run.
Conversely set (C) carries a `run_id` and is LF, and
`s3k/runs/s3-knux-multibonus-ss` is a `runs/` tree that is now LF because
`63eccd290` re-captured it on Linux. The single predictor that fits every
case is the capture host/date, not the layout and not `run_id`.

For the port this changes nothing operationally — the newline convention
must be an explicit **per-fixture-set property**, which is exactly what
`Program.cs`'s `ExpandNewlinesIf(bool, string)` / `ExpandRunNewlines`
already express for S1/S2. Do not derive it from run-vs-plain mode.

The empty SS `aux_state.jsonl` has no newline in either layout (0 bytes;
the `special_stage/` gzip member is 20 bytes).

No BOM anywhere; all output is ASCII.

---

## 10. Invariants and landmines for the native port

### 10.1 Post-advance, source-order evaluation

The whole §1.2 chain runs after the advance, in order, with early
returns. This exact bug class was independently introduced in both the
S1 and S2 ports. Do not hoist the arm gate above the SS state machine,
do not merge steps 2/3/11/12, and do not evaluate the arm gate before the
row-mode guard.

### 10.2 The arm frame belongs to no segment

Level, bonus and SS arms all `return` without writing. A one-frame slip
here changes `bk2_frame_offset` for every downstream segment and breaks
the `+1` succession identity across the entire run.

### 10.3 `started` is shared between level and SS segments

`start_ss_segment` sets the same `started` / `trace_frame` /
`bk2_frame_offset` state (all three are file-scope `local`s, Lua 873–876
— see §4). Consequences that must be preserved:
`OGGF_TRACE_STOP_FRAME` and `OGGF_BK2_FRAME_COUNT` truncate SS segments
too; and the SS entry branch must be gated on `detour_active`, never on
`started`.

### 10.4 `current_segment_zone` is cleared by *both* finalize paths

That is the only reason the level arm gate re-fires after a detour into
the *same* zone (AIZ → gumball → AIZ). If a port keeps the zone latched
across a detour, every `_2`/`_3` segment vanishes and set (B) collapses
from 25 segments to 5.

### 10.5 Zone gating in a handoff tail reads the NEXT zone

Aux window gates read the **live** `u8[0xFE10]`, not `start_zone_id`.
Fixture proof: (B) `aiz_3` is 7568 rows long and so covers the
`terrain_wall_sensor` window `[7549, 7560]`, yet emits **zero** such
events — because by those trace frames the ROM zone register had already
flipped to 0x15 (slots) during the bonus handoff tail. The same segment
*does* emit 9 `aiz_handoff_terrain_state` events at trace frames
5430–5438, where the live zone was still 0. Any port that substitutes the
segment's own zone for the live read will emit 12 spurious events.

### 10.6 Aux frame windows are per-segment `trace_frame`, not BK2 frames

All 17 window vars and their defaults are compared against `trace_frame`.
In a 25-segment run each segment restarts the windows from 0. Set (A)'s
`aiz_completerun` emits 12 `terrain_wall_sensor` + 9
`aiz_handoff_terrain_state`; the other six (A) segments emit neither,
because their live zone is not 0.

### 10.7 Hook-driven aux families are absent from (A)/(C) but PRESENT in (B)

**Do not carry the STANDARD recorder's hook-absence result over to this
recorder.** The census splits by capture, not by recorder:

- **(A) 7 dirs + (C) 4 dirs — hooks OFF** (`capture_mode` present ⇒
  `LIGHTWEIGHT_REGEN` ⇒ the `if not LIGHTWEIGHT_REGEN` registration block
  at Lua 5816–5830 never ran). Zero of every hook-driven family:
  `cage_execution`, `cnz_cylinder_execution`, `velocity_write`,
  `position_write`, `solid_object_cont_entry`,
  `collision_response_list_per_frame`, `rng_call`,
  `tails_cpu_normal_step`, `aiz_boundary_state`,
  `aiz_transition_floor_solid`, `aiz_ship_loop`, `sonic_record_pos`,
  `cnz_event_ram`, `aiz_fire_transition`.
- **(B) 25 dirs — hooks ON** (no `capture_mode` key; `write_metadata`
  emits that line iff `LIGHTWEIGHT_REGEN`, Lua 1375–1377). Four segments
  carry real hook events, measured on the gunzipped, CR-stripped streams:

  | Segment | `position_write` | `velocity_write` | `solid_object_cont_entry` |
  |---|--:|--:|--:|
  | `hcz_2` | 43 | 21 | 31 |
  | `hcz_6` | 17 | · | 31 |
  | `mgz` | 43 | · | 26 |
  | `mgz_3` | 38 | · | 31 |

  The other 21 (B) segments carry none — their hooks fired outside every
  window, not "hooks were off".

Byte-reproducing (B) therefore requires LibGPGX exec **and** memory-write
callbacks in `GpgxHost`; see
[s3k-completerun-profiles.md](s3k-completerun-profiles.md) §6 for the
recommended (A)+(C)-byte-exact / (B)-shape-only split and the
`ADDR_FRAMECOUNT` reason (§8.3, and profiles §7.3) that (B) is not
byte-reproducible from current HEAD anyway.

The non-hook, gate-restricted families that *do* appear in the hooks-off
sets are all state-polled: `cage_state` (8030 in `cnz_completerun`),
`cnz_cylinder_state` (23, same dir),
`collision_response_list_end_of_frame` (7, same dir),
`object_state_snapshot` (4, same dir), and `terrain_wall_sensor` (12) /
`aiz_handoff_terrain_state` (9) in `aiz_completerun`.
`aiz_fire_transition` can never appear under any configuration because
its writer is gated on `is_aiz_end_to_end_profile()`, which is
permanently false here.

The existing `S3KHookAbsenceTests` pin may be extended to the eleven
(A)+(C) dirs. It must **not** be widened to cover (B).

### 10.8 `zone_token_for` ≠ `ZONE_NAMES`

§6.1. Reusing the STANDARD table mislabels ids ≥ 10 and produces the
wrong directory names for SOZ-and-later segments.

### 10.9 The `+1` succession identity is the primary geometric assertion

`offset(i+1) = offset(i) + rows(i) + 1` for every boundary except an SS
**exit**. It reproduces 6/7 of set (A) and 21/24 of set (B) from a single
starting offset. Assert it in the differential gate; a violation localizes
the bug to a single boundary.

### 10.10 Two independent metadata deltas, one version bump

§8.3. Do not key `pre_trace_osc_frames` off `lua_script_version` — the
`0xFE08 → 0xFE04` fix shipped without a bump, so `6.32` fixtures exist on
both sides of it.

### 10.11 Truncated-SS finalize must not use the level path

§5.7 step 1. `finalize_segment()` on an open SS segment stamps
`kind: "level"` on SS-schema rows, and the already-pushed `giant_ring`
transition lets the corrupt manifest still validate.

### 10.12 Env-var surface

The complete-run recorder reads 25 `OGGF_*` names: the STANDARD 24 minus
`OGGF_S3K_TRACE_PROFILE`, plus `OGGF_TRACE_RUN_ID` and
`OGGF_BK2_BASENAME`. The native CLI's refusal table for unmodeled
output-affecting variables must be extended to cover the two new names if
they are not modeled, and must **stop refusing** `OGGF_S3K_TRACE_PROFILE`
for this subcommand since the recorder does not read it. Pin the
deliberately-not-refused set in a test so the guard cannot degrade into a
blanket `OGGF_*` ban.

## 11. Gate coverage as landed

All twelve invariants above are covered by the landed port and its three
ROM-backed differential gates rather than left as open risks:

- `S3KCompleteRunSegmentsDifferentialTests` reproduces identity (A) (all
  seven `*_completerun` fixtures) byte-exact from one untruncated pass
  and additionally asserts the full 15-segment `segments_done` summary
  through DDZ, which is the concrete proof for §10.1 (post-advance
  ordering), §10.2 (arm frame owned by no segment), §10.9 (the `+1`
  succession identity), and §10.8 (`zone_token_for` directory naming).
- `S3KCompleteRunDifferentialTests` and `S3KRunModeDifferentialTests`
  reproduce identities (C) and (B) byte-exact — (B) since `63eccd290`
  re-captured that legacy set at 6.32; before then it could only be gated
  structurally. Together they cover §10.3–§10.7 and §10.11 (shared/cleared
  segment state across level/SS/bonus kinds, live-zone-gated aux windows,
  hook-family absence across all three identities, and the truncated-SS
  finalize path).
- §10.10 (`pre_trace_osc_frames` / `ADDR_FRAMECOUNT` are two independent,
  unbumped deltas) and §10.12 (the 25-name env-var surface and its
  refusal-table extension) are pinned by dedicated assertions in
  `S3KRunModeDifferentialTests` and `TraceCliTests` respectively, not
  inferred from the byte gates alone.

See `s3k-run-publication.md` §10 for the class map and exact gate
mechanics, and `tools/bizhawk/README.md`'s "Sonic 3 & Knuckles
complete-run and run mode" section for the verified capture commands and
measured cost. This closes the S3K complete-run migration: every Lua
recorder in the fleet (S1, S2, S3K standard, S3K complete-run) now has a
byte-parity-gated native port.
