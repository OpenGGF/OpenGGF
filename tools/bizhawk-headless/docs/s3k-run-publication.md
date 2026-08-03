# S3K Complete-Run Recorder — Publication Specification (directories, metadata.json, run_manifest.json, encodings)

> **V5 supersession (2026-08-03).** Publication now uses the single v5
> envelope and literal candidate workflow in
> `docs/guide/contributing/trace-v5-publication.md`. The Candidate-B and
> versioned identities below remain historical publication evidence.

> **2026-08-02 recorder-order note.** The maintained native complete-run
> writer is `6.42-s3k-completerun`. On a shared raw frame it serializes module
> `post_objects` before direct `pre_main_loop`, after any `vint_service` event.
> Non-Candidate-B fixtures remain read-only at their existing published
> stamps; the 67-segment super-emerald run and both existing run manifests
> remain `6.40-s3k-completerun`. Candidate B is the explicitly approved
> installed exception. A replacement 67-segment super-emerald capture still
> requires separate approval and must be staged under
> `tools/bizhawk-headless/.scratch/s3k-knuckles-complete-superemeralds-v642/`
> for independent review before publication.
> The canonical 466,334-row Sonic-and-Tails validation capture retains exact
> committed physics/aux identity and produces a reviewed timing-only delta:
> 27 in-place `vint_service`-to-`post_objects` substitutions across 14 of 15
> segments, with raw frame, kind, ordinal, fingerprint, position, and ordering
> unchanged. The user explicitly approved the Candidate B bytes frozen by
> manifest commit `f7827cb1f`; exactly 15 metadata files and 14 changed timing
> files were installed from Candidate B, while the independent repeat remained
> validation-only. The 15-segment gate now pins the installed 6.42 bytes and
> requires a fresh capture to match them exactly, with only recording_date
> normalized in metadata. A cheap companion contract retains all 27 literal
> 6.40 predecessor edges, reconstructs and verifies every predecessor hash,
> pins the aggregate at 27, and proves the historical wrong-25 total fails.
>
> **2026-07-27 publication note.** The canonical committed fleet is native
> recorder v6.37. It contains 47 timing-owned fixture destinations plus the
> 25-segment/22-transition Knuckles B manifest. The immutable, machine-checked
> inventory is
> `src/test/resources/traces/s3k/hardware-timing-publication.tsv`; it records
> destination ownership, raw recorder token, schemas, exact file hashes and
> lengths, and hardware-event ranges. Historical v6.33 tables below remain
> useful for byte-level provenance, but no longer enumerate the current
> published fleet.

Authoritative byte-level contract for **how the S3K complete-run recorder
publishes output**: which directories it creates and how it picks their
names, which files each segment kind writes, the exact byte layout of
`run_manifest.json` and of all three `metadata.json` shapes, the per-file
line-ending convention, and the recorder's full environment-variable
surface.

Historical v6.33 behavioral authority order:

1. `tools/bizhawk/s3k_complete_run_recorder.lua` (5918 lines,
   `LUA_SCRIPT_VERSION = "6.33-s3k-completerun"` at L357) plus
   `tools/bizhawk/lib/oggf_trace_common.lua`. **Do not modify either.**
2. The canonical fixtures under `src/test/resources/traces/s3k/`
   (read-only; gunzip to a temp dir to inspect).
3. `docs/skdisasm` for RAM questions.

Current publication authority is the reviewed native harness under
`tools/bizhawk-headless/AGENTS.md`; Lua is optional corroboration.

Companion specs: `s3k-trace-recorder-behavior.md` (CORE physics.csv /
RAM map / standard-recorder metadata), `s3k-aux-events.md` (aux event
vocabulary), `s3k-profiles-and-hooks.md` (profiles, hooks, stop
ordering), `s1-complete-run-behavior.md` / `s1-run-mode-behavior.md` /
`s2-run-mode-behavior.md` §11 (shared run/manifest model).

This document does **not** re-specify `physics.csv` rows or
`aux_state.jsonl` event bodies — see the companions. It specifies
everything *around* them.

---

## 0. Canonical fixtures — three distinct capture identities

The S3K complete-run recorder has produced **three** committed fixture
sets from **two** movies. They are not interchangeable and they publish
different shapes, so establishing which is which remains the single most
important prerequisite for the native port. All three now share one
recorder identity — Lua `6.33-s3k-completerun`, Linux, diagnostic hooks
off — and **all three are byte-reproducible**. That was not true of (B)
until commit `63eccd290` re-captured it; the legacy (B) analysis is kept
below because it is the whole reason the port's normalization rules
(§6, §7.3) are written the way they are.

Movies live in `src/test/resources/traces/s3k/_movies/`:
`s3k-complete-sonic-tails.bk2` (Sonic+Tails AIZ→Doomsday complete run)
and `s3-knux-multibonus-ss.bk2` (Knuckles multi-bonus + special-stage
route).

### 0.1 Identity (A) — complete-run pass, no `run_id`

Seven published dirs directly under `src/test/resources/traces/s3k/`,
all `trace_profile: complete_run`, `lua_script_version:
6.33-s3k-completerun`, `recording_date: 2026-07-25`,
`capture_mode: physics_animation_aux_without_diagnostic_hooks`,
**no `run_id` key**, `pre_trace_osc_frames: 1`.
`source_bk2: s3k-complete-sonic-tails.bk2`. **No `run_manifest.json`**
(no detour occurred and `run_id` was unset — see §4.1).

| Dir | `segment_index` | zone/act | `bk2_frame_offset` | rows | physics.csv sha256 / bytes | aux_state.jsonl sha256 / bytes |
|---|---|---|---|---|---|---|
| `aiz_completerun/` | 0 | aiz(0)/1 | 941 | 26228 | `2f8d3d0c2f5a4b3f30b7784ed28fa37071951f6d8d538f08573b4631fa33f872` / 4249570 | `d55efb44c7fadc022591c56054964e002c8ade868867a8965a0efbe820f2d210` / 172380688 |
| `hcz_completerun/` | 1 | hcz(1)/1 | 27170 | 31482 | `5d829f35729bb9254f272283dd078d3c6b259c771ca3d57eea3fb249d7ed73c7` / 5100718 | `9fa13b138dd4e22749bdf0cfb66c71cd28e0e72568372b683efbc0255208077f` / 210920712 |
| `mgz_completerun/` | 2 | mgz(2)/1 | 58653 | 39398 | `ddfcc9851a6c6b100e9366ebe9fccfecd9a99745639a8192f0f93e241879ae52` / 6383110 | `1b3faa5204d83883a8877c0c3873ba7831aefde4a07abff942e119dc3a8038eb` / 208896738 |
| `cnz_completerun/` | 3 | cnz(3)/1 | 98052 | 40064 | `2d1ba19a27d614c25ceb8962f7506552cc8b038cc3a36a00b08f4337d329d404` / 6491002 | `1134664398b8b911f0ed0024376c71d1aa546598785cd3008e04ed91ffbd3406` / 211836043 |
| `icz_completerun/` | 4 | icz(5)/1 | 138117 | 25393 | `386cf6e8e62b61c8cd03c252668db47d3511fc1fd6c43399830e6655086d0c99` / 4114300 | `0e21af4b895ab47ceca79ea74301208bd8ab1a44899cab921c566d75608efaa9` / 173735703 |
| `lbz_completerun/` | 5 | lbz(6)/1 | 163511 | 46244 | `dba472735a28d1bb3235a4fe79ab6734202456f97bca6ca00cac2f5d64c8a139` / 7492162 | `d89419f674e653686a80482954eb6cb309dfbf17016c6962e9f53f830ed1b8b5` / 266307785 |
| `mhz_completerun/` | 6 | mhz(7)/1 | 209756 | 28156 | `d502ee1305f363c448d5507aae54b732d851433713f809fdd79ce8ccc21c9c03` / 4561906 | `0260219e935d5ec5b0873d8f59422fabe1ad8a6be8b8d2d9f505428e220764d6` / 184254918 |

`segment_index` runs 0..6 with **no gaps**: the Sonic route skips FBZ
(zone 4) entirely and took no bonus/special detour through MHZ, so
`segments_done` has exactly one entry per published dir up to that
point. A capture of the full movie continues past `mhz` (SOZ, LRZ, …);
only these seven segments are committed as fixtures. A native
differential gate must therefore compare **the seven named dirs**, not
"every dir the capture produced".

### 0.2 Identity (B) — run pass, `run_id: s3-knux-multibonus-ss`

`src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/`: 25 segment
dirs + `run_manifest.json` + a curation copy of the movie. **Re-captured
2026-07-25 on Linux** by commit `63eccd290` with Lua
`6.33-s3k-completerun` and the diagnostic hooks off, so all 25
`metadata.json` stamp `6.33-s3k-completerun` and `recording_date:
2026-07-25`, the 22 level/bonus segments carry `capture_mode:
physics_animation_aux_without_diagnostic_hooks` and
`pre_trace_osc_frames: 1`, every published file is LF, the
`gameplay_frame_counter` column is live (4547 distinct values across
`aiz`'s 4654 rows), and **no segment carries a hook-driven aux line**.

`run_manifest.json` is sha256
`a36ad5e75daaa0ad8924b4ed624d765f42b14516b0ef985ad2a1f99efb209705`,
8740 bytes, and stamps `6.33-s3k-completerun` in agreement with every
segment. This set is byte-reproducible; §0.2.1 records what it used to
be, because that history is what §6 and §7.3's rules were derived from.

| Dir | kind | profile | `bk2_frame_offset` | rows |
|---|---|---|---|---|
| `aiz` | level | complete_run | 915 | 4654 |
| `gumball` | bonus_stage | s3k_bonus_stage | 5570 | 1430 |
| `aiz_2` | level | complete_run | 7001 | 2140 |
| `slots` | bonus_stage | s3k_bonus_stage | 9142 | 1200 |
| `aiz_3` | level | complete_run | 10343 | 7568 |
| `slots_2` | bonus_stage | s3k_bonus_stage | 17912 | 1278 |
| `aiz_4` | level | complete_run | 19191 | 3210 |
| `gumball_2` | bonus_stage | s3k_bonus_stage | 22402 | 1648 |
| `aiz_5` | level | complete_run | 24051 | 3631 |
| `hcz` | level | complete_run | 27683 | 3176 |
| `slots_3` | bonus_stage | s3k_bonus_stage | 30860 | 5379 |
| `hcz_2` | level | complete_run | 36240 | 11933 |
| `ss` | special_stage | s3k_special_stage | 48174 | 4630 |
| `hcz_3` | level | complete_run | 54274 | 3949 |
| `slots_4` | bonus_stage | s3k_bonus_stage | 58224 | 1603 |
| `hcz_4` | level | complete_run | 59828 | 2097 |
| `ss_2` | special_stage | s3k_special_stage | 61926 | 7194 |
| `hcz_5` | level | complete_run | 70590 | 3435 |
| `slots_5` | bonus_stage | s3k_bonus_stage | 74026 | 1791 |
| `hcz_6` | level | complete_run | 75818 | 8422 |
| `mgz` | level | complete_run | 84241 | 8721 |
| `pachinko` | bonus_stage | s3k_bonus_stage | 92963 | 3051 |
| `mgz_2` | level | complete_run | 96015 | 2076 |
| `ss_3` | special_stage | s3k_special_stage | 98092 | 6537 |
| `mgz_3` | level | complete_run | 106104 | 8517 |

#### 0.2.1 What (B) was before `63eccd290` (history)

The superseded (B) was captured **2026-07-19 on Windows EmuHawk** with
Lua `6.31-s3k-completerun`, and the eight bonus segments'
`metadata.json` were later **hand-edited** (not re-captured) on
2026-07-20 by commit `9e3ccdb41` — so the version stamp read `6.32` in
those eight and `6.31` in the other seventeen and in the manifest. That
in-run drift was a fixture-provenance artifact, never recorder
behavior: `write_run_manifest` and `write_metadata` both read the same
`LUA_SCRIPT_VERSION` global and can never disagree in a single real
capture. Its `run_manifest.json` was sha256
`2a78eb3c40d1f2d2c13d5f604b1a5418b85099423cd6fdd9ced917c6a52dbd60`,
8799 bytes.

Three independent facts made that capture non-byte-reproducible against
the current recorder: **CRLF** from the Windows host's text-mode
`io.open` (§6), the pre-`6564667eb` **`ADDR_FRAMECOUNT` `0xFE08`** dead
read that left `gameplay_frame_counter` constant `0000` and every aux
`vfc` / `level_frame_counter` constant `0` (§7.3 i), and **armed
diagnostic hooks** — being pre-`192d9c976` it ran the hook-registration
block, so `hcz_2` (95 lines), `hcz_6` (48), `mgz` (69) and `mgz_3` (69)
carried hook-driven aux families a hooks-off capture cannot emit
(§8.2).

All three are gone at the source in the re-captured set. This section
exists because the rules those facts produced — LF everywhere, `0xFE04`
in the complete-run recorder, hooks off — are still load-bearing, and
because a future regeneration that reintroduces any of them must be
recognised as a regression rather than a new baseline.

### 0.3 Identity (C) — second run pass, `run_id: s3k-multibonus`

The four published standalone dirs
`src/test/resources/traces/s3k/{bonus_gumball,bonus_pachinko,bonus_slots,special_stage}/`
are the (B) movie re-captured on **2026-07-23** under `run_id:
s3k-multibonus`, then lifted out of the run tree into standalone fixture
dirs. (That is the original capture date; every committed S3K fixture was
last regenerated by `eb87d681b` and now stamps `recording_date:
2026-07-25`.) Same `bk2_frame_offset` and `trace_frame_count` as their (B)
counterparts, and — since (B) was re-captured under the same recorder
identity by `63eccd290` — the same `physics.csv` and `aux_state.jsonl`
bytes too. Only `metadata.json` still separates them, by `run_id`. The
two sets are nonetheless pinned independently in the gate, so a drift in
either is caught rather than cancelling out.

| Dir | (B) counterpart | `segment_index` | offset | rows | physics.csv sha256 / bytes | aux_state.jsonl sha256 / bytes |
|---|---|---|---|---|---|---|
| `bonus_gumball/` | `gumball` | 1 | 5570 | 1430 | `8d6e3e3004e811a124c516ac224fe9e9dd5476cce1d6c3097b3b7c65c2526dd6` / 232294 | `842fbad87a91effb9749bcd7b95f61d558d1cb9929e35cf5ca9ac328743460e7` / 8408680 |
| `bonus_slots/` | `slots` | 3 | 9142 | 1200 | `7fe7de5bb8dd97bf98ef595e46899097bb0b9e01a999aeff0d9953e63504809b` / 195034 | `afe43538f38435bb28ef09defe765ff8fabeae6b4e9d1253485bc4f79c682249` / 2360965 |
| `special_stage/` | `ss` | 12 | 48174 | 4630 | `b6afb3f5f9708f974bf71d5fcfb973aced8e60d81e51f64e01a88012996fa5a1` / 272711 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` / **0** |
| `bonus_pachinko/` | `pachinko` | 21 | 92963 | 3051 | `86c2c655d41153bde45ab762d2f382c51b59d1b7063b2e8b1c7b42a2cc13308b` / 494896 | `129c19c636f1df05783f72d19af3f8aa1936bc44c63501d3825f1e8d35c6acc3` / 10733394 |

`special_stage/` additionally contains a curation copy of
`s3-knux-multibonus-ss.bk2` (not recorder output — see §2.4).

**`physics.csv`, `aux_state.jsonl` and `run_manifest.json` must be
byte-identical with ZERO normalization.** Only `metadata.json` may
differ, and only in the exact lines tabulated in §7.4.

---

## 1. Output layout, directory naming, and directory selection

### 1.1 Base directory

```
BASE_OUTPUT_DIR = os.getenv("OGGF_TRACE_OUTPUT_DIR") or "trace_output/"
```
with a trailing `/` appended if absent (L332-336). `OUTPUT_DIR` is a
separate global repointed per segment; every writer
(`open_files`/`write_metadata`/`write_ss_metadata`/`reset_recording_state`)
consumes `OUTPUT_DIR`, while `write_run_manifest` alone consumes
`BASE_OUTPUT_DIR`.

Layout:

```
<base>/
  <dirToken>/physics.csv
  <dirToken>/aux_state.jsonl
  <dirToken>/metadata.json
  ...
  run_manifest.json            (conditional — §4.1)
```

There is no nesting: every segment dir, of every kind, is a direct child
of the base dir. The `runs/<run_id>/` level visible in the fixture tree
is **fixture curation**, not recorder output — the recorder was pointed
at that directory as its base.

### 1.2 Directory token derivation

`zone_token_for(zone_id)` (L660-666):

1. `BONUS_TOKENS[zone_id]` if present — `{0x13="gumball",
   0x14="pachinko", 0x15="slots"}`.
2. else `ZONE_TOKEN[zone_id]` — `0=aiz 1=hcz 2=mgz 3=cnz 4=fbz 5=icz
   6=lbz 7=mhz 8=soz 9=lrz 10=hpz 11=ssz 13=ddz 22=hpz22 23=dez23`.
3. else `string.format("zone%02x", zone_id)` (lower-case hex, e.g.
   `zone0c`).

Special-stage segments bypass `zone_token_for` entirely and use the
literal base token `"ss"` (`start_ss_segment`, L5137-5141).

### 1.3 Repeat-visit suffixing (exact)

A single `segment_dir_counts` table is keyed by **base token** and shared
by the level/bonus path (`start_new_segment`, L5026-5031) and the SS path
(`start_ss_segment`, L5137-5141):

```lua
local n = (segment_dir_counts[base_token] or 0) + 1
segment_dir_counts[base_token] = n
local dir_token = (n == 1) and base_token or (base_token .. "_" .. n)
```

So the first visit is bare (`aiz`, `gumball`, `ss`), and the *k*-th is
`<token>_<k>` — **1-based, no zero padding, no gap-filling, never
reset**. The counter is monotone for the whole run, so `aiz_5` in (B)
is the fifth AIZ segment even though four other zones' segments were
interleaved. Verified across (B): `aiz`..`aiz_5`, `hcz`..`hcz_6`,
`mgz`..`mgz_3`, `ss`..`ss_3`, `gumball`/`gumball_2`, `slots`..`slots_5`,
`pachinko`.

### 1.4 Directory pre-creation

`precreate_segment_dirs()` runs **once at script load** (L5790) and
creates, in one `os.execute`: the base dir, one dir per `ZONE_TOKEN`
value, one per `BONUS_TOKENS` value, and `ss/`. It first probes each
path by opening `<path>/.oggf_dir_probe` for write and removing it; if
every probe succeeds it returns without shelling out. `ensure_segment_dir`
is the shell-free per-segment fallback for an unknown `zoneXX` token.

Consequences the port must honor:

- Pre-created dirs for zones the movie never visits are left **empty**
  (a real capture's base dir contains empty `fbz/`, `soz/`, … dirs).
  These are not published fixture content; a publisher that stages only
  files, not dirs, matches the fixtures exactly.
- The `_2`/`_3` suffixed dirs are **not** pre-created; they are made by
  `ensure_segment_dir` at arm time.
- `.oggf_dir_probe` is transient and never survives a successful run.

### 1.5 Which segments arm at all

Level/bonus arm gate (L5411-5421): `game_mode == 0x0C` (**raw** — not
the `$4C`/`$8C` level-load family) **and** `zone_id ~=
current_segment_zone` **and** `Ctrl_lock_timer == 0` **and**
`Ctrl_1_locked == 0`. Arming does **not** record the arm frame — it
arms and returns, so trace row 0 is the *next* BizHawk frame
(`bk2_frame_offset = emu.framecount()` at arm; row *N* aligns to BK2
frame `offset + N`).

SS segments arm on the first `Game_mode == 0x34` frame while
`detour_active ~= "special_stage"` (L5335-5359).

**All of these conditions are evaluated POST-advance, in the Lua's
`on_frame_end` source order.** Re-ordering arm/publish/stop evaluation
is the bug class that was independently hit in both the S1 and S2 ports;
port the order literally.

---

## 2. Files written per segment kind

| Segment kind | `physics.csv` | `aux_state.jsonl` | `metadata.json` | Notes |
|---|---|---|---|---|
| `level` (`trace_profile: complete_run`) | 42-column CSV v7 header + one row/frame | full poll-driven event stream | §3.1 shape | |
| `bonus_stage` (`trace_profile: s3k_bonus_stage`) | identical 42-column CSV v7 schema | identical full stream | §3.2 shape | Only the metadata differs from a level segment |
| `special_stage` (`trace_profile: s3k_special_stage`) | dedicated **20-column** SS schema (`ss_csv_version 1`) | no profile aux events; with `--load-queue-state`, exactly one direct and one module physical queue record per stored row | §3.3 shape plus optional `load_queue_state_per_frame` capability | |

### 2.1 Level/bonus file creation

`open_files()` (L1236-1251) opens both files in `"w"` mode and
immediately writes + flushes the CSV v7 header (identical characters to
the standard S3K recorder's — see `s3k-trace-recorder-behavior.md` §3.1).
`aux_state.jsonl` gets no header.

### 2.2 SS file creation

`start_ss_segment()` (L5154-5160) opens both files and writes the SS
header:

```
frame,input,input_p2,lag,anim_frame,x_pos,y_pos,angle,velocity,turning,jumping,fade_timer,spheres_left,ring_count,rings_left,rate,rate_timer,clear_timer,clear_routine,started
```

`aux_file` is opened but **nothing is ever written to it** on the SS
path (`write_ss_row` emits no aux). Every SS fixture's
`aux_state.jsonl` is exactly 0 bytes (sha256
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`) —
verified for (B) `ss`, `ss_2`, `ss_3` and (C) `special_stage`. The port
must publish the empty file, not omit it.

### 2.3 Write cadence and last-writer-wins metadata

- `physics.csv` is flushed every 60 rows (L5626; L5205 on the SS path) and on finalize.
- `aux_state.jsonl` is flushed **per line** (`write_aux` in
  `oggf_trace_common.lua`).
- `metadata.json` is **rewritten in full** at arm, every 300 rows
  (L5629-5631, and L5206 on the SS path), and again at finalize. Only the finalize write survives.
  Two fields are therefore sampled at finalize time and not at arm time:
  `trace_frame_count` (the final row count) and `recording_date`
  (`os.date("%Y-%m-%d")` at the finalize write — a capture that crosses
  local midnight stamps the later date).
- SS metadata is written at arm (`start_ss_segment` → `write_ss_metadata`)
  and again at finalize (`finalize_ss_segment` → `write_ss_metadata`).

A native port that materializes each `metadata.json` exactly once, at
finalize, is byte-equivalent.

### 2.4 Files that are NOT recorder output

`src/test/resources/traces/s3k/special_stage/s3-knux-multibonus-ss.bk2`
and
`src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/s3-knux-multibonus-ss.bk2`
are curation copies placed for `TraceCatalog`. The recorder never writes
`.bk2` files. Do not emit or gate on them.

---

## 3. `metadata.json` — exact byte layout

All three shapes: `{`, `\n`, then one `  "key": value,\n` line per field
(two-space indent, no tab), then `}` and a final `\n`. No trailing comma
on the last field. Values are emitted by the Lua's own formatters:

- Strings via `string.format("%q", …)` (`source_bk2`,
  `lua_script_version`) or literal concatenation with `"` (everything
  else). `notes` uses `json_quote` (escape + wrap).
- `start_x`/`start_y`: `"0x" .. hex(v)` → **4** upper-case hex digits.
- `rng_seed`: `"0x" .. hex(v, 8)` → **8** upper-case hex digits.
- `v_int_run_count`: bare **decimal** (from `read_u32_be(0xFE0C)`).
- `aux_schema_extras`: a single line, entries joined with `", "` (comma
  **and** space), each `json_quote`d.

### 3.1 Level segment (`write_metadata`, `current_segment_is_bonus == false`)

```
{
  "game": "s3k",
  "zone": "<zone_token_for(start_zone_id)>",
  "zone_id": <start_zone_id decimal>,
  "act": <start_act + 1>,
  "bk2_frame_offset": <decimal>,
  "source_bk2": "<%q>",
  "trace_frame_count": <decimal>,
  "pre_trace_osc_frames": <decimal>,
  "start_x": "0xHHHH",
  "start_y": "0xHHHH",
  "characters": [...],
  "main_character": "...",
  "sidekicks": [...],
  "rng_seed": "0xHHHHHHHH",
  "recording_date": "YYYY-MM-DD",
  "lua_script_version": "6.33-s3k-completerun",
  "trace_schema": 6,
  "csv_version": 7,
  "capture_mode": "physics_animation_aux_without_diagnostic_hooks",   ← iff LIGHTWEIGHT_REGEN
  "aux_schema_extras": [ …19 names, see below… ],
  "trace_profile": "complete_run",
  "run_id": "<id>",                                                   ← iff OGGF_TRACE_RUN_ID set
  "segment_index": <#segments_done at write time>,
  "bizhawk_version": "2.11",
  "genesis_core": "Genplus-gx",
  "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
  "notes": "<fixture_notes>"
}
```

The `characters`/`main_character`/`sidekicks` triple is produced by
`character_metadata_json()` from `Player_mode`
(`sonic3k.constants.asm:892`): `0` → `["sonic", "tails"]` / `"sonic"` /
`["tails"]`; `1` → `["sonic"]` / `"sonic"` / `[]`; `2` → `["tails"]` /
`"tails"` / `[]`; `3` → `["knuckles"]` / `"knuckles"` / `[]`. Array
elements are joined with `", "`.

`aux_schema_extras` for `TRACE_PROFILE == "complete_run"` is a fixed
19-name list (12 base + 7 complete-run additions), byte-identical in
every (A)/(B)/(C) fixture:

```
["cpu_state_per_frame", "oscillation_state_per_frame", "object_state_per_frame", "interact_state_per_frame", "velocity_write_per_frame", "position_write_per_frame", "tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame", "control_lock_state_per_frame", "sonic_record_pos_per_frame", "air_countdown_state_per_frame", "game_paused_state_per_frame", "cage_state_per_frame", "cage_execution_per_frame", "cnz_cylinder_state_per_frame", "cnz_cylinder_execution_per_frame", "solid_object_cont_entry_per_frame", "collision_response_list_per_frame", "collision_response_list_end_of_frame"]
```

This list is advertised **unconditionally** and does not track what the
stream actually contains: hook-driven names appear here even when hooks
are disabled and no such event is emitted (§8.2). Emit it verbatim.

`notes` for the `complete_run` profile is the fixed string:

```
Per-zone segment from the S3K complete-run (AIZ->Doomsday) Sonic+Tails movie. Covers act1 -> seamless act1->act2 -> the act2->next-zone exit handoff (trailing 0x8C frames). Game_paused aux flag is comparison-only.
```

It is written for **every** complete-run segment including bonus
segments and including Knuckles-route captures, where its wording is
inaccurate. Reproduce it verbatim; do not "fix" it.

`pre_trace_osc_frames` is `start_gameplay_frame_counter`, sampled from
`ADDR_FRAMECOUNT` **at the first recorded row**, not at arm
(L5527-5543 overwrites the arm-time sample). Fixture-verified identity
across all ten 6.32/`0xFE04` fixtures:

> `pre_trace_osc_frames` == `physics.csv` row 0's
> `gameplay_frame_counter` == the pre-trace `cpu_state_snapshot`'s
> `vfc` == **1**.

`segment_index` is `#segments_done` — the count of *already finished*
segments, i.e. this segment's own 0-based index. It is stable across the
arm/periodic/finalize writes because `finalize_segment` calls
`write_metadata` **before** appending to `segments_done`.

### 3.2 Bonus segment (`current_segment_is_bonus == true`)

Identical to §3.1 except the single `trace_profile` line is replaced by
this block, in this order:

```
  "trace_profile": "s3k_bonus_stage",
  "bonus_stage_type": "<gumball|pachinko|slots>",
  "v_int_run_count": <decimal>,      ← iff start_v_int_run_count ~= nil (6.32+, bonus only)
```

`bonus_stage_type` is `BONUS_TOKENS[start_zone_id]` (falls back to `""`).
`zone`/`zone_id` remain the bonus zone token/id (`gumball`/19,
`pachinko`/20, `slots`/21); `act` is `start_act + 1`, observed `1` in
every bonus fixture. `v_int_run_count` is `read_u32_be(0xFE0C)`
(`V_int_run_count`, `sonic3k.constants.asm:790`) sampled **once at
segment arm**, gated on `current_segment_is_bonus` — the level path
leaves `start_v_int_run_count = nil` and the key is omitted entirely.

### 3.3 Special-stage segment (`write_ss_metadata`, L5103-5128)

A **different key set and order**, not a superset of §3.1:

```
{
  "game": "s3k",
  "trace_profile": "s3k_special_stage",
  "special_stage_index": <Current_special_stage (0xFE16) at arm>,
  "ss_csv_version": 1,
  "characters": [...],
  "main_character": "...",
  "sidekicks": [...],
  "bk2_frame_offset": <decimal>,
  "trace_frame_count": <decimal>,
  "source_bk2": "<%q>",
  "lua_script_version": "6.33-s3k-completerun",
  "recording_date": "YYYY-MM-DD",
  "bizhawk_version": "2.11",
  "genesis_core": "Genplus-gx",
  "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
  "run_id": "<id>",            ← iff OGGF_TRACE_RUN_ID set
  "fresh_load": false,
  "segment_index": <#segments_done>
}
```

Absent by construction — do **not** add them: `zone`, `zone_id`, `act`,
`pre_trace_osc_frames`, `start_x`, `start_y`, `rng_seed`,
`trace_schema`, `csv_version`, **`capture_mode`**, `aux_schema_extras`,
**`bonus_stage_type`**, **`v_int_run_count`**, `notes`. `fresh_load` is a
hardcoded `false` (giant-ring entries are always mid-level).

The two absences that look like version/env deltas but are not:

- **`capture_mode`.** `write_metadata` emits it from an
  `if LIGHTWEIGHT_REGEN then` branch (L1375-1377). `write_ss_metadata`
  (L5103-5128) contains no such branch — it never reads
  `LIGHTWEIGHT_REGEN` at all — so no env value and no recorder build can
  make it appear on the SS path.
- **`v_int_run_count`** (and `bonus_stage_type`). Both live inside
  `write_metadata`'s `if current_segment_is_bonus then` block
  (L1431-1436), and `v_int_run_count` is further gated on
  `start_v_int_run_count ~= nil` (L1434). `write_ss_metadata` references
  neither variable, and `start_ss_segment` (L5137) never assigns
  `current_segment_is_bonus` or `start_v_int_run_count`. The
  6.31→6.32 line addition (§7.2) is therefore structurally invisible to
  the SS shape.

The (C) fixture set proves both empirically: captured in **one** pass
under the same `--run-id s3k-multibonus`, the three bonus dirs carry
`capture_mode` **and** a decimal `v_int_run_count` while
`special_stage/` carries neither — so neither absence can be attributed
to a differing recorder build, date, or environment.

---

## 4. `run_manifest.json` — exact byte layout

`write_run_manifest()` (L1458-1535), written to
`BASE_OUTPUT_DIR .. "run_manifest.json"`.

### 4.1 Emission condition and timing

```lua
if #transitions_done == 0 and run_id == nil then return end
```

The manifest is emitted iff **at least one transition was recorded OR
`OGGF_TRACE_RUN_ID` was set**. A plain complete-run pass with no detour
and no run id writes no manifest — which is exactly why identity (A) has
none. Note the disjunction: a detour-free capture *with* `--run-id`
still writes a manifest with an empty `transitions` array, and a
detour-ful capture *without* a run id writes one with no `run_id` line.

Timing: after the end-of-run finalize of the last (possibly truncated)
segment and after the segment summary print, immediately before the loop
`break` (L5893). It is the **last** file the recorder writes — the port's
publisher must link it last so a manifest can never exist without its
segment files.

Before writing, the Lua validates each record's `to_segment ==
from_segment + 1` and `to_segment <= #segments_done`, printing a
`WARNING:` to stdout on violation but **still writing the record**. The
port must reproduce the write, and may reproduce the warning on stderr;
it must not turn the warning into a failure.

### 4.2 Byte layout

```
{
  "run_schema": 1,
  "game": "s3k",
  "run_id": "<%q>",                  ← iff run_id ~= nil
  "source_bk2": "<%q>",
  "rom_checksum": "C5B1C655C19F462ADE0AC4E17A844D10",
  "lua_script_version": "6.33-s3k-completerun",
  "segments": [
    {"dir": …, "kind": …, "trace_profile": …, "bk2_frame_offset": …, "trace_frame_count": …, "zone_id": …, "act": …<, extra>},
    …last entry has no trailing comma…
  ],
  "transitions": [
    {"from_segment": …, "to_segment": …, "entry_kind": …<, optional fields>},
    …last entry has no trailing comma…
  ]
}
```

with a final `\n` after `}`. Two-space indent for top-level keys,
**four-space** indent for array elements. `"segments": [` and
`"transitions": [` are each followed by `\n`; the closing brackets are
`  ],\n` and `  ]\n}\n`. `game` is the literal `"s3k"` — never
`"sonic3k"`, which `TraceExecutionModel.forGame` rejects.

An empty array renders as `  "segments": [\n  ],\n` (open bracket,
newline, closing bracket line) — the Lua's loop simply contributes
nothing.

### 4.3 Segment entry field order (exact)

Always, in this order: `dir`, `kind`, `trace_profile`,
`bk2_frame_offset`, `trace_frame_count`, `zone_id`, `act`. Then exactly
one optional extra, appended last:

| `kind` | `trace_profile` | extra | `zone_id` / `act` |
|---|---|---|---|
| `level` | `complete_run` | — | real zone id / `start_act + 1` |
| `bonus_stage` | `s3k_bonus_stage` | `, "bonus_stage_type": "<token>"` | bonus zone id (19/20/21) / `start_act + 1` |
| `special_stage` | `s3k_special_stage` | `, "special_stage_index": <n>` | hardcoded **0** / hardcoded **0** |

Fields are separated by `", "`. `dir`, `kind`, `trace_profile`,
`bonus_stage_type` are `%q`-quoted; numerics are bare decimal.
`trace_frame_count` is the segment's `rows` (`trace_frame` at finalize).

`kind` and `trace_profile` are derived at finalize:
`current_segment_is_bonus and "bonus_stage" or "level"` /
`current_segment_is_bonus and "s3k_bonus_stage" or TRACE_PROFILE`
(`finalize_segment`, L5005-5017); `"special_stage"` /
`"s3k_special_stage"` are hardcoded in `finalize_ss_segment` (L5254-5263).

> **Native gap:** the shared `RunManifestWriter` currently knows only
> `level` and `special_stage` and has no `bonus_stage_type` slot. The S3K
> port must extend it with a `BonusStageKind` constant and a
> `BonusStageType` property emitted in the same position as
> `special_stage_index` — an additive seam, not a fork.

### 4.4 Transition entry field order (exact)

Always, in this order: `from_segment`, `to_segment`, `entry_kind`,
`mode_change_bk2_frame`. Then the optional fields, **each emitted iff it
was recorded for that transition kind, never keyed on its value** (in
Lua `0` is truthy, so a sampled `0` still renders), in this fixed order:

`special_bonus_entry_flag`, `saved_x_pos`, `saved_y_pos`,
`last_star_post_hit`, `rings_before`, `rings_after`, `emeralds_before`,
`emeralds_after`.

This is byte-identical to the existing shared
`RunManifestWriter.Format` optional ordering — reuse it unchanged.

---

## 5. Transition kinds the S3K recorder can emit

Exactly **three**. There is no S3K `starpost_special`, `death_restart`
or `level_advance`; plain level→level zone changes are boundaries with
**no** transition record at all (hence `#transitions_done` is bounded by,
not equal to, the number of segment boundaries — (B) has 25 segments and
22 transitions, with index gaps at 8→9 and 19→20 where AIZ→HCZ and
HCZ→MGZ crossed with no detour).

Indices are captured **at push time**, between `finalize_segment` and
`start_new_segment`, as `from = #segments_done - 1`, `to =
#segments_done`. Never re-derive them from array position.

### 5.1 `starpost_bonus` (level → bonus stage), L5445-5455

Pushed at the **bonus segment's arm**, after the predecessor level
segment is finalized, when `BONUS_TOKENS[zone_id] ~= nil`.

Fields: the four mandatory ones plus `special_bonus_entry_flag`
(`0xFE48` `Special_bonus_entry_flag`, u8 — `2` for a bonus stage),
`saved_x_pos` (`0xFE2E`, u16), `saved_y_pos` (`0xFE30`, u16),
`last_star_post_hit` (u8), `rings_before` (`0xFE20`, u16),
`emeralds_before` (`0xFFB0`, u8). `mode_change_bk2_frame` is
`emu.framecount()` at the arm frame — equal to the bonus segment's own
`bk2_frame_offset`.

### 5.2 `giant_ring` (level → special stage), L5344-5354

Pushed at the **SS segment's open**, on the first `Game_mode == 0x34`
frame of the detour, after `finalize_segment()`.

Same field set as `starpost_bonus`; `special_bonus_entry_flag` reads `1`
(special stage) instead of `2`. `mode_change_bk2_frame` equals the SS
segment's `bk2_frame_offset`.

### 5.3 `stage_exit` (bonus or special stage → level), L5434-5442

Pushed at the **return level segment's arm**, gated on
`segments_done[#segments_done].kind` being `bonus_stage` **or**
`special_stage`.

Fields: the four mandatory ones plus **only** `rings_after` (u16) and
`emeralds_after` (u8). No `special_bonus_entry_flag`, no `saved_*`, no
`last_star_post_hit`, no `*_before`. `mode_change_bk2_frame` equals the
returning level segment's `bk2_frame_offset`.

### 5.4 Push order at a bonus arm

Within the single arm block the Lua pushes `stage_exit` **before**
`starpost_bonus` (L5434 then L5445). A bonus→bonus arm would therefore
emit both records for the same frame. Port the order literally.

### 5.5 Truncation

If the movie ends mid-`$34`, the end-of-run block routes the open
segment through `finalize_ss_segment` (not the generic
`finalize_segment`), printing `WARNING: movie ended mid special-stage
detour …` and publishing a correctly-labeled truncated `special_stage`
segment (L5874-5884). The already-pushed `giant_ring` entry transition
then still names a real segment and the manifest invariant holds.

---

## 6. Line endings — LF for every S3K file, in both modes

Determined from fixture bytes, not assumed.

| Fixture set | `physics.csv` | `aux_state.jsonl` | `metadata.json` | `run_manifest.json` |
|---|---|---|---|---|
| (A) complete-run pass | **LF** | **LF** | **LF** | n/a |
| (C) run pass `s3k-multibonus` | **LF** | **LF** | **LF** | n/a (dirs lifted out) |
| (B) run pass `s3-knux-multibonus-ss` | **LF** | **LF** | **LF** | **LF** |
| (B) *before* `63eccd290` (superseded) | CRLF | CRLF | CRLF | CRLF |

The Lua writes only `"\n"`; the encoding comes from the host. `io.open(…,
"w")` is **text mode**, so a Windows EmuHawk expands every `\n` to
`\r\n`. The superseded (B) was captured on Windows; (A) and (C) were
captured on Linux/Mono on 2026-07-23 (commit `192d9c976`), and the
current (B) on Linux on 2026-07-25 (§0.2.1).

**Therefore the S3K complete-run port publishes LF for every file in
both plain mode and run mode.** Do **not** copy the S1/S2 run-mode rule
("run mode ⇒ `ExpandRunNewlines`") — for S3K that would corrupt the (B)
and (C) gates, which are run-mode captures with LF output.
`ExpandRunNewlines` must not be applied on any S3K path.

No committed S3K fixture is CRLF any more, so nothing in this tree can
be cited to justify a newline normalization step — and the gates carry
none.

---

## 7. The three capture identities, pinned

### 7.1 Recorder-build timeline (from `git log -p` on the Lua)

| Commit | Date | `LUA_SCRIPT_VERSION` | Change relevant to publication |
|---|---|---|---|
| `64e10fbf6` | 2026-07-19 | `6.31-s3k-completerun` | Player_mode-derived team metadata |
| `9e3ccdb41` | 2026-07-20 | `6.32-s3k-completerun` | **+`v_int_run_count`** metadata line (bonus segments only) |
| `6564667eb` | 2026-07-21 | `6.32-s3k-completerun` (**no bump**) | `ADDR_FRAMECOUNT` `0xFE08` → `0xFE04` |
| `192d9c976` | 2026-07-23 | `6.32-s3k-completerun` (**no bump**) | `LIGHTWEIGHT_REGEN` inverted to default-on; `capture_mode` string changed; pre-trace snapshots always emitted |
| `f71b5ea44` | 2026-07-25 | `6.33-s3k-completerun` | `ADDR_VBLA_WORD` `0xFE12` (`Life_count`) → `0xFE0E` (low word of `V_int_run_count`) — physics.csv `vblank_counter` only; all 39 S3K fixture dirs regenerated in `eb87d681b` |

### 7.2 The 6.31 → 6.32 metadata delta (exact, and it is *only* metadata)

Commit `9e3ccdb41` changed the published bytes in exactly two ways:

1. `lua_script_version` string bump `"6.31-s3k-completerun"` →
   `"6.32-s3k-completerun"` — in every `metadata.json` (level, bonus,
   ss) **and** in `run_manifest.json`.
2. One new line, `  "v_int_run_count": <decimal>,`, inserted between
   `"bonus_stage_type"` and `"run_id"`, emitted **only** for
   `s3k_bonus_stage` segments.

`physics.csv`, `aux_state.jsonl`, the CSV schema, the aux vocabulary,
`aux_schema_extras`, and every other metadata key are untouched. The
commit's own message records that it re-captured the movie
deterministically, confirmed the physics rows byte-identical, and then
**hand-edited only the `metadata.json` files** of the eight bonus
segments plus the three interior `bonus_*` copies.

### 7.3 The two version-invisible deltas that broke the legacy (B)

Neither bumped `LUA_SCRIPT_VERSION`, so a version string alone cannot
distinguish a legacy capture from a current one. That is why both are
still specified here even though (B) has been re-captured (§0.2.1): a
regenerated fixture set announces neither change in its version stamp,
so the port must be pinned to the current behaviour by observation, not
by trusting `lua_script_version`.

**(i) `ADDR_FRAMECOUNT` `0xFE08` → `0xFE04` (commit `6564667eb`).**
`0xFE08` is `Debug_placement_mode`, dead-zero in normal gameplay;
`0xFE04` is the live `Level_frame_counter`. This changes:

- `physics.csv` column 6 `gameplay_frame_counter` — the whole column was
  `0000` in the legacy (B) (verified: its `runs/…/aiz/physics.csv` had
  exactly one distinct value across all 4654 rows) versus 26040 distinct
  values across 26228 rows in `aiz_completerun`. The re-captured (B)
  reads live: 4547 distinct values across the same 4654 rows;
- every `aux_state.jsonl` line's `vfc` field, and
  `oscillation_state.level_frame_counter`;
- `metadata.json`'s `pre_trace_osc_frames` (0 in the legacy (B), 1 in
  (A)/(C) and in the re-captured (B)).

A structural diff of the legacy (B) `gumball` against (C)
`bonus_gumball` (after stripping CR) showed **identical line counts,
identical key sets, and differences confined to `vfc` /
`level_frame_counter`** — which is why the re-captured `gumball` is now
byte-identical to `bonus_gumball`:

```
object_state.vfc 18149   object_near.vfc 18149   air_countdown_state.vfc 2600
cpu_state.vfc 1300       oscillation_state.vfc 1300
oscillation_state.level_frame_counter 1300        game_paused_state.vfc 1300
interact_state.vfc 1300  object_appeared.vfc 177  control_lock_state.vfc 98
slot_dump.vfc 74         object_removed.vfc 52    state_snapshot.vfc 23
cpu_state_snapshot.vfc 1 player_mode_set.vfc 1    mode_change.vfc 1
routine_change.vfc 1
```

**Both S3K recorders now read `0xFE04`.** The standard recorder
(`s3k_trace_recorder.lua`) read `0xFE08` until v6.31-s3k, which is why
this section existed; that fix landed and the standard recorder's three
canonical fixtures were regenerated on the live counter. The native port
therefore carries NO recorder-identity fork for this address — the
`FrameCounterAddressFor` seam and the 4-argument `FormatRow` overload
were deleted (s3k-completerun-profiles.md §11.1), and
`s3k-trace-recorder-behavior.md` no longer instructs anyone to reproduce
a dead read.

**(ii) `LIGHTWEIGHT_REGEN` inverted (commit `192d9c976`).** Before:
`LIGHTWEIGHT_REGEN = os.getenv("OGGF_TRACE_LIGHTWEIGHT") == "1"` —
opt-IN, so a default capture wrote **no** `capture_mode` line. After:
`DIAGNOSTIC_HOOKS_ENABLED = os.getenv("OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS")
== "1"; LIGHTWEIGHT_REGEN = not DIAGNOSTIC_HOOKS_ENABLED` — default-ON,
so a default capture always writes it. The same commit changed the
string from `"physics_animation_only"` to
`"physics_animation_aux_without_diagnostic_hooks"` and removed the
lightweight early-return that used to suppress the pre-trace
`cpu_state_snapshot` / `object_state_snapshot` emissions.

Consequence: **`capture_mode`'s presence is an env/build fact, not a
version fact.** The legacy (B) omitted it because it predated the
inversion — and, because it predated it, that capture really did run
with the hooks armed: four of its 25 segments contained hook-driven aux
families that no (A) or (C) fixture contains (§8.2). That was the
**third** independent reason the legacy (B) was not byte-reproducible
against HEAD, on top of the `ADDR_FRAMECOUNT` change and CRLF. The
re-captured (B) carries `capture_mode` in all 22 of its level/bonus
segments and no hook-driven line anywhere, so all three reasons are
gone.

### 7.4 Per-fixture permitted delta versus a fresh 6.32 (HEAD) capture

`physics.csv`, `aux_state.jsonl` and `run_manifest.json`: **zero
permitted difference, zero normalization.** `metadata.json`: only the
lines below.

| Fixture | Permitted `metadata.json` delta | `physics.csv` / `aux_state.jsonl` / `run_manifest.json` |
|---|---|---|
| (A) `aiz_completerun`, `hcz_completerun`, `mgz_completerun`, `cnz_completerun`, `icz_completerun`, `lbz_completerun`, `mhz_completerun` | `recording_date` value **only** | byte-identical; no manifest emitted |
| (C) `bonus_gumball`, `bonus_pachinko`, `bonus_slots` | `recording_date` value **only** (capture must set `--run-id s3k-multibonus`) | byte-identical |
| (C) `special_stage` | legacy fixture comparison also accounts for the 6.39 version/capability migration; this shape still has no `capture_mode` or `v_int_run_count` | `physics.csv` byte-identical; audited captures replace the legacy empty aux file with per-row direct/module queue state |
| (B) `runs/s3-knux-multibonus-ss/` — 14 level segments (`aiz`..`aiz_5`, `hcz`..`hcz_6`, `mgz`..`mgz_3`) | `recording_date` value **only** (capture must set `--run-id s3-knux-multibonus-ss`) | byte-identical |
| (B) 8 bonus segments (`gumball`, `gumball_2`, `slots`..`slots_5`, `pachinko`) | `recording_date` value **only** (same `--run-id`) | byte-identical |
| (B) 3 ss segments (`ss`, `ss_2`, `ss_3`) | legacy fixture comparison also accounts for the 6.39 version/capability migration; this shape has no `capture_mode`, `pre_trace_osc_frames`, or `v_int_run_count` | `physics.csv` byte-identical; audited captures replace each legacy empty aux file with per-row direct/module queue state |
| (B) `run_manifest.json` | n/a — the manifest carries no `recording_date` | byte-identical, **zero** free fields |

**Gate policy.** All three identities are byte-differential targets:
thirty-six dirs in total, full-file sha256 on `physics.csv` and
`aux_state.jsonl`, `run_manifest.json` raw, and a `metadata.json`
comparison that permits **only** the `recording_date` value line with
`lua_script_version` pinned as the exact `6.33-s3k-completerun` literal
on both sides.

This is a tightening. Until `63eccd290` re-captured it, (B) could only
be gated structurally — segment inventory, dir tokens, offsets/row
counts, `kind` / `trace_profile` / `bonus_stage_type` /
`special_stage_index`, the 22-record transition list — with three
non-byte-exact deltas pinned as literals (CRLF folding, the dead
`gameplay_frame_counter` column and aux counter fields, and per-segment
hook-line counts). All three normalizations have been **deleted**, not
left dormant: a normalization nobody needs is one that silently absorbs
the next regression.

Never widen normalization to make a fixture pass. If a divergence
appears against any identity, fix production code — or, if the fixture
itself is stale, re-capture it under the current recorder and re-pin the
bytes, which is exactly what `63eccd290` did.

---

## 8. Environment-variable surface

### 8.1 Complete list read by `s3k_complete_run_recorder.lua`

| Variable | Line | Effect | Output-affecting? | Native handling |
|---|---|---|---|---|
| `OGGF_BIZHAWK_LIB` | 307 | locates `lib/oggf_trace_common.lua` | no (load-time only) | N/A — no Lua in the port |
| `OGGF_TRACE_OUTPUT_DIR` | 332 | `BASE_OUTPUT_DIR` | yes (location, not bytes) | modeled as `--output-dir` |
| `OGGF_TRACE_STOP_FRAME` | 342 | early finalize at `trace_frame >= N` | **yes** (truncates both streams) | **refuse** |
| `OGGF_BK2_FRAME_COUNT` | 343 | early finalize at `offset + trace_frame >= N` | **yes** (truncates both streams) | **refuse** the env var; the equivalent capability is the existing `--effective-movie-length` CLI option, exactly as in the S1/S2 run ports |
| `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` | 348 | `=1` arms all 13 hook families **and** removes the `capture_mode` line | **yes** | **refuse** |
| `OGGF_TRACE_QUIET` | 350 | replaces `print` with a no-op | no (stdout only; no published file changes) | **deliberately NOT refused** — pin this in a test |
| `OGGF_BK2_BASENAME` | 360 | `source_bk2` in every `metadata.json` and in `run_manifest.json` | **yes** | **modeled** — derived from the movie filename (`Path.GetFileName(options.MoviePath)`), same as S1/S2 |
| `OGGF_TRACE_RUN_ID` | 907 | `run_id` line in level/bonus/ss metadata and in the manifest; forces manifest emission | **yes** | **modeled** — `--run-id` |
| `OGGF_S3K_RNG_CALL_RANGE` | 786 | sets `V625_RNG_CALLS.enabled`; both consumers are dead here (see below) | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | 3253 | sets `V622_CNZ_EVENT_RAM.enabled`, the only gate on the **frame-polled** `cnz_event_ram` emit (`V622_CNZ_EVENT_RAM.write` L3310, called per frame at L5699) | **yes** | **refuse** |
| `OGGF_S3K_AIZ_FIRE_RANGE` | 3365 | retunes the `aiz_fire_transition` window, but the emitter is unreachable here (see below) | no | **deliberately NOT refused** |
| `OGGF_S3K_AIZ_WALL_SENSOR_RANGE` | 4411 | retunes the frame-polled `terrain_wall_sensor` window (**present in `aiz_completerun`: 12 events**) | **yes** | **refuse** |
| `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START` / `_END` | 4103-4105 | retunes the frame-polled `aiz_handoff_terrain_state` window (**present in `aiz_completerun`: 9 events**) | **yes** | **refuse** |
| `OGGF_S3K_CRL_RANGE` | 4665 | retunes the end-of-frame `collision_response_list_end_of_frame` poll (**present in `cnz_completerun`: 7 events**) | **yes** | **refuse** |
| `OGGF_S3K_CNZ_CYLINDER_RANGE` | 3070 | retunes the frame-polled `cnz_cylinder_state` window (**present in `cnz_completerun`: 23 events**) | **yes** | **refuse** |
| `OGGF_S3K_POSITION_WRITE_RANGE` | 2546/2550 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_VELOCITY_WRITE_RANGE` | 2420/2426 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_SOLID_CONT_RANGE` | 4457 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_AIZ_SHIP_LOOP_RANGE` | 2688 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_AIZ_BOUNDARY_RANGE` | 3618 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_AIZ_BOUNDARY_FRAME_START` / `_END` | 815-816 | legacy single-window form of the above | no with hooks off | **deliberately NOT refused** |
| `OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START` / `_END` | 845-847 | window for a hook-populated family | no with hooks off | **deliberately NOT refused** |

The complete-run recorder has **no** `OGGF_S3K_TRACE_PROFILE` (the
standard recorder's L273): `TRACE_PROFILE` is hardcoded to
`"complete_run"` (L341) so the single-arm `aiz_end_to_end` /
`level_gated_reset_aware` paths can never engage. It also has **no**
`OGGF_TRACE_LIGHTWEIGHT` any more (removed by `192d9c976`) — a stale
export of that name is silently ignored by HEAD and must **not** be
added to the refusal table, because refusing it would be a false
refusal.

That hardcoded `TRACE_PROFILE` is what makes three otherwise
plausible-looking refusals false. The refusal table above must be
justified per-variable against HEAD, not inherited from the standard
recorder's table:

- **`OGGF_S3K_AIZ_FIRE_RANGE` — no output effect.**
  `V628_AIZ_FIRE.write()` (L3388) returns at **L3393**
  (`if not is_aiz_end_to_end_profile() then return end`), and
  `is_aiz_end_to_end_profile()` (L1032-1034) is
  `TRACE_PROFILE == "aiz_end_to_end"` — impossible under L341.
  `aiz_fire_transition` can never be emitted by this recorder, and no
  fixture contains one. Refusing this variable would be a false refusal.
- **`OGGF_S3K_RNG_CALL_RANGE` — no output effect with hooks off.** Its
  two consumers are both dead: (i) the `rng_call_per_frame` append to
  `aux_schema_extras` is at **L1422**, inside the `else` arm of
  `if TRACE_PROFILE == "complete_run"` (L1392) — unreachable under L341;
  (ii) `rng_call` lines come only from `V625_RNG_CALLS.flush()` (L3042),
  which returns at **L3044** when `#hits == 0`, and `hits` is populated
  only by callbacks registered in `V625_RNG_CALLS.register_hooks()`
  (L3053), itself called only inside `if not LIGHTWEIGHT_REGEN then`
  (**L5816**) — i.e. only when the already-refused
  `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`. It therefore belongs in the
  same "no with hooks off" class as the seven window variables below it.
- **`OGGF_S3K_CNZ_EVENT_RAM_RANGE` — refused, but for one reason only.**
  Its `aux_schema_extras` append is at **L1418**, in the same
  unreachable `else` arm, so that half of the older description was
  wrong. What survives is real and sufficient: `V622_CNZ_EVENT_RAM.write()`
  (L3310) is called unconditionally per frame at **L5699** and gates
  solely on `.enabled` (L3313) + window + CNZ act 1 — and `.enabled`
  defaults `false` (L3250) and is set **only** by this env var
  (L3255). Setting it therefore injects `cnz_event_ram` aux lines
  into a stream that has none in any fixture. Refusal stands.

The seven remaining `*_RANGE` / `*_FRAME_START|END` variables are
correctly classified as not-refused: each only widens a window consulted
by a flush that early-returns on an empty hit list
(`WRITE_DIAG.flush_tails_velocity_writes` L2485,
`WRITE_DIAG.flush_position_writes_for` L2642,
`V611_SOLID.flush_solid_object_cont_entries` L4529,
`V618_AIZ_SHIP.flush` L2779, `V66.flush_aiz_boundary_state` L3778,
`V67_AIZ.flush_aiz_transition_floor_solid` L4035), and those lists stay
empty while L5816 leaves the hooks unregistered. By contrast
`V613_AIZ_WALL.write_terrain_wall_sensor` (L4375),
`V69_AIZ.flush_aiz_handoff_terrain_state` (L4195),
`V67_CNZ.emit_cnz_cylinder_state_per_frame` (L3209) and the
`collision_response_list_end_of_frame` branch of
`V615_CRL.flush_collision_response_list_per_frame` (L4816, emit at
L4871) have **no** hit-list guard — they poll and emit purely on window
+ zone, which is why their four variables are refused and why their
events are present in the fixtures.

### 8.2 Hook-family absence in (A)/(C) — and its former *presence* in (B)

Full `event`-value census over every (A) and (C) `aux_state.jsonl`
(counts are per fixture; only the non-uniform families are itemized):

```
COMMON to all seven (A) dirs and all three (C) bonus dirs:
  air_countdown_state  control_lock_state  cpu_state  cpu_state_snapshot
  game_paused_state    interact_state      mode_change  object_appeared
  object_near          object_removed      object_state  oscillation_state
  player_mode_set      routine_change      slot_dump    state_snapshot
  zone_act_state
  + (A) only:              sidekick_interact_object  (present in all seven
                           (A) dirs; absent from all three (C) bonus dirs,
                           which are Knuckles-solo and never take the
                           sidekick branch)
  + aiz_completerun only:  aiz_handoff_terrain_state (9), terrain_wall_sensor (12)
  + cnz_completerun only:  cage_state (8030), cnz_cylinder_state (23),
                           collision_response_list_end_of_frame (7),
                           object_state_snapshot (4)
  + (C) special_stage:     load_queue_state (direct then module per stored row)
```

An absence gate must therefore treat `sidekick_interact_object` as
route-dependent, not universal: asserting it on `bonus_gumball` /
`bonus_slots` / `bonus_pachinko` fails.

**Every family above is frame-polled or a pre-trace snapshot. Not a
single hook-driven family (`event.onmemoryexecute` / memory-write
callbacks) appears in any (A) or (C) fixture** — no `cage_execution`,
`cnz_cylinder_execution`, `velocity_write`, `position_write`,
`sonic_record_pos`, `rng_call`, `tails_cpu_normal_step`,
`aiz_boundary_state`, `aiz_transition_floor_solid`,
`solid_object_cont_entry`, `collision_response_list_per_frame`,
`aiz_ship_loop`, `cnz_event_ram`.

**The re-captured (B) matches that: all 25 of its segments are
hook-free** (verified by an `event`-value census over all 25
`aux_state.jsonl`). The *legacy* (B) was different, and an earlier
revision of this spec was wrong to claim otherwise: it predated the
`LIGHTWEIGHT_REGEN` inversion (§7.3 ii), so its capture ran the L5816
hook-registration block, and **four of its 25 segments carried
hook-driven families**:

| legacy (B) segment | rows | hook-driven events |
|---|---|---|
| `hcz_2` | 11933 | `position_write` 43, `solid_object_cont_entry` 31, `velocity_write` 21 |
| `hcz_6` | 8422 | `position_write` 17, `solid_object_cont_entry` 31 |
| `mgz` | 8721 | `position_write` 43, `solid_object_cont_entry` 26 |
| `mgz_3` | 8517 | `position_write` 38, `solid_object_cont_entry` 31 |

Their `frame` values landed exactly inside the recorder's **default
trace-frame** windows — `position_write` at 4788-4792 and 7549-7625
(`POSITION_WRITE_RANGES`), `solid_object_cont_entry` at 4788 and
7600-7625 (`V611_SOLID`), `velocity_write` at 3640-3660
(`VELOCITY_WRITE_RANGES`) — so the windows *were* entered; only segments
long enough to reach them and whose route actually executed the hooked
PCs produced hits. The other 21 legacy (B) segments were hook-free — the
longest of them, `aiz_3` at 7568 rows, stopped short of the 7600-7625
window and never executed the hooked PCs inside the 3640-3660 /
4788-4792 ones. Those same four dirs, re-captured with the hooks off,
now carry none.

**Hook absence across all three identities is therefore exactly "the
switch was off" (L5816), the same result task 7 established — no
stronger.** It is still sufficient, because the port refuses
`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS` and every committed fixture is now
a byte-differential target.

**The LibGPGX exec/memory-write callback surface therefore stays
deferred for the complete-run port too.** `tests/S3KHookAbsenceTests.cs`
covers the eleven **(A)/(C)** fixtures plus the four (B) segments that
used to be hook-bearing — `hcz_2`, `hcz_6`, `mgz`, `mgz_3` — which now
sit on the *absence* side. Its old (B) counter-gate, which pinned those
same four in the opposite direction so the absence gate could never
silently widen to a fixture that genuinely needed exec callbacks, lost
its subject when the run was re-captured and is deleted; gating the four
directly is what that guarantee becomes. The other 21 (B) segments are
not enumerated there because their bytes are gated in full by
`S3KRunModeDifferentialTests` case 2. Per fixture
assert zero aux lines whose `event` is any deferred family, anchor
non-vacuously on `cpu_state` == `oscillation_state` == row count and
exactly one `cpu_state_snapshot`, and assert the `capture_mode` line's
presence in each level/bonus `metadata.json` and its **absence** in
`special_stage/metadata.json`. If a future fixture regeneration turns a
hook family on, those gates fail — that is the signal to build the
callback surface (and to watch delegate GC-pinning under Mono: a
collected delegate is the classic interop crash).

---

## 9. Porting invariants checklist

1. Publish LF for every file, in every mode. Never call
   `ExpandRunNewlines` on an S3K path (§6).
2. `ADDR_FRAMECOUNT = 0xFE04` for BOTH recorders (§7.3 i). The standard
   recorder read `0xFE08` until Lua v6.31-s3k; there is no fork left.
3. Emit `capture_mode:
   "physics_animation_aux_without_diagnostic_hooks"` in level and bonus
   metadata; never in special-stage metadata (§3.3).
4. `v_int_run_count` only for bonus segments, decimal, sampled once at
   arm from `0xFE0C`, positioned between `bonus_stage_type` and
   `run_id` (§3.2).
5. `run_id` line emitted iff `--run-id` was given, in all three metadata
   shapes and in the manifest.
6. Manifest emitted iff `transitions.Count > 0 || runId != null`;
   published **last** (§4.1).
7. `segment_dir_counts` keyed by base token, shared across level, bonus
   and ss paths, monotone for the whole run, 1-based `_k` suffixes
   (§1.3).
8. `special_stage` manifest entries hardcode `zone_id: 0` and `act: 0`
   (§4.3).
9. Transition indices captured at push time, `stage_exit` pushed before
   `starpost_bonus` within one arm block (§5.4).
10. All arm / publish / stop conditions evaluated POST-advance in the
    Lua's `on_frame_end` source order (§1.5).
11. Refuse every output-affecting env var in §8.1 — exactly eight
    entries: `OGGF_TRACE_STOP_FRAME`, `OGGF_BK2_FRAME_COUNT`,
    `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS`, `OGGF_S3K_CNZ_EVENT_RAM_RANGE`,
    `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`,
    `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START`/`_END`,
    `OGGF_S3K_CRL_RANGE` and `OGGF_S3K_CNZ_CYLINDER_RANGE` — and pin, in
    a test, the variables that are deliberately **not** refused
    (including `OGGF_TRACE_QUIET`, `OGGF_S3K_RNG_CALL_RANGE` and
    `OGGF_S3K_AIZ_FIRE_RANGE`, whose emitters are dead under the
    hardcoded `complete_run` profile), so the guard cannot degrade into
    a blanket `OGGF_*` ban.
12. Stream, do not buffer, any profile that cannot discard a
    mid-capture recording. The (A) pass alone produces ~1.4 GB of
    `aux_state.jsonl` across seven segments (largest single segment:
    `lbz_completerun` at 266 MB); a segment closes and can be released
    at finalize, so peak footprint should track the largest single
    segment, not the run.
13. Fix production code on divergence. Never fixtures, never looser
    normalization, never a zone/route/frame carve-out.

---

## 10. Native port — as built (Stage C)

The publication layer landed as five new classes plus three seams in
existing shared code. Everything the complete-run recorder shares with
the already-migrated pieces is delegated; this section records only what
is genuinely new and why.

### 10.1 Class map

| Class | Owns |
|---|---|
| `S3KCompleteRunMetadataWriter` | All three `metadata.json` shapes (§3) and `character_metadata_json` |
| `S3KRunManifestWriter` | The S3K front-end literals over the shared writer, plus the §4.1 emission gate as `ShouldEmit` |
| `S3KCompleteRunCaptureRunner` | The Lua main loop (§1.5 ordering), row/aux dispatch, and the manifest decision |
| `S3KStagedSegmentSink` | Production sink: streams each segment into the staging session |
| `NoReplacePublisher.StagedStream` | Incremental staged-file writing (invariant 12) |

The runner delegates segmentation to `S3KCompleteRunSegmenter`, the
42-column row to `S3KTraceCsvWriter.FormatRow(..., S3KRam.LevelFrameCounter)`,
the aux cascade to `S3KAuxEventEngine(S3KTraceProfile.CompleteRun)` and
the 20-column SS row to `S3KSpecialStageCsvWriter`. It never calls
`EmitFinalization`: that emits the `gameplay_end` checkpoint for
`level_gated_reset_aware` only, and this recorder's `TRACE_PROFILE` is
hard-pinned to `complete_run`.

### 10.2 Three seams in shared code

1. **`RunManifestSegment.BonusStageType` + the `bonus_stage` branch in
   `RunManifestWriter.Format`.** Additive, not a fork: the extras stay
   mutually exclusive by kind, and S1/S2 never produce
   `BonusStageKind`, so their manifests are byte-unaffected. The 8-arg
   constructor still exists and delegates.
2. **`NoReplacePublisher.IncrementalStagingSession.OpenFile` →
   `StagedStream`.** `StageFile(name, string)` would hold a 266 MB
   segment as a .NET string (2 bytes per ASCII char) and then copy it;
   the streamed form writes into the staged temporary as the capture
   produces it. Same no-replace guarantees: the temporary lives next to
   its final, nothing lands under a final name before the completed
   set's `Publish()`, and an abandoned stream removes its temporary. A
   session that still has an open stream at `Complete()` throws rather
   than half-publishing.
3. **`--effective-movie-length` reaches the S3K path.** It is the
   modeled equivalent of the refused `OGGF_BK2_FRAME_COUNT` and feeds
   the segmenter's unconditional movie-input-end guard. The existing
   CLI rule (it requires `--run-id`) is unchanged.

### 10.3 CLI surface

`--run-id <id>` OR `--trace-profile complete_run` with the S3K
locked-on ROM selects the complete-run recorder; both were previously
rejected with a "not migrated yet" error. `--trace-profile` with any
other string still reaches the STANDARD recorder verbatim, and
`--gameplay-segment` is still S2-only. S1, S2 and S3K-standard CLI
behavior and stdout bytes are unchanged.

stdout follows the S1 complete-run shape: `BizHawk` / `ROM SHA-1` /
`Movie frames` / (`Effective movie length` when set) / `Run ID` or
`Trace profile` / `Segments` / `Transitions` / one `Segment <dir>:` line
per segment / `Run manifest` when one was emitted.

### 10.4 Environment refusal is a SEPARATE table

`RejectUnmodeledS3kCompleteRunEnvironment` is not the standard
recorder's `RejectUnmodeledS3kEnvironment`. The standard table refuses
`OGGF_S3K_AIZ_FIRE_RANGE` and `OGGF_S3K_RNG_CALL_RANGE`; under the
complete-run script's hard-pinned `TRACE_PROFILE` both emitters are
unreachable, so refusing them would be a false refusal (§8.1). The
complete-run table refuses the eight entries of invariant 11 and
nothing else, and `TraceCli S3K complete-run does not refuse the
variables that cannot change its output` pins thirteen non-refusals —
including `OGGF_TRACE_QUIET` and the removed `OGGF_TRACE_LIGHTWEIGHT` —
so the guard cannot degrade into a blanket `OGGF_*` ban.

### 10.5 Gate coverage as landed, and what is left

Landed:

- `metadata.json` reproduced **byte for byte** against six committed
  fixtures — `aiz_completerun`, `hcz_completerun`, `bonus_gumball`,
  `bonus_slots`, `bonus_pachinko`, `special_stage` — each fed its own
  `recording_date`, so these are full-file equality assertions covering
  both the (A) shape and the (C) shape.
- `run_manifest.json` reproduced against the (B) manifest **byte for
  byte with zero normalization** — gating all 25 segment entries, all 22
  transition records and every optional-field presence rule. The two
  deltas this once pinned as literals (CRLF, the 6.31 stamp) were legacy
  capture artifacts and are gone from the fixture (§0.2.1).
- The driver, sink and staging over synthetic movies: dir tokens, row
  counts, per-kind file sets and metadata shapes, the empty SS aux file,
  input-column alignment, finalize-time sampling, the manifest gate in
  all three states, LF everywhere, and no final path before `Publish()`.
- One ROM-backed byte gate over identity (C),
  `S3KCompleteRunDifferentialTests`: a `--effective-movie-length 7001`
  pass over `s3-knux-multibonus-ss.bk2` reproduces the whole
  `bonus_gumball` segment — `physics.csv` and `aux_state.jsonl` by
  sha256 with zero normalization, `metadata.json` modulo the
  `recording_date` value — in about five seconds.
- **The full identity-(A) byte gate,
  `S3KCompleteRunSegmentsDifferentialTests`** (§10.6).
- **The full run-mode gate, `S3KRunModeDifferentialTests`** (§10.7),
  which closes the three formerly hand-verified identity-(C) dirs, gates
  the 25-segment (B) run tree byte for byte, and raises the (B) manifest
  check from formatter level to recorder level.

Nothing in the publication contract is now left to manual verification.

### 10.6 The identity-(A) gate

`S3KCompleteRunSegmentsDifferentialTests` runs **one untruncated**
`--trace-profile complete_run` pass (no `--run-id`) over the full
466,334-row `s3k-complete-sonic-tails.bk2` and validates all fifteen
committed `*_completerun` dirs against the installed canonical 6.42
publication. Physics, aux, and timing are byte-identical. Metadata may differ
only by recording date. The former 6.40 timing identities remain independently
attested by a cheap non-capture migration test: it reverses exactly the 27
approved VINT-to-POST substitutions across fourteen segments and requires all
15 frozen predecessor hashes, including the byte-identical ending stream.

Measured, not estimated: **5m57s wall, 235 MB peak RSS, 2.84 GB of
output.** The earlier "hours of wall clock" note in this section was
wrong by two orders of magnitude — the streaming `S3KStagedSegmentSink`
holds no segment in memory, so a 266 MB `lbz` aux stream costs one OS
write buffer. Because 2.84 GB is well beyond a RAM-backed `/tmp`, this
gate stages under `tools/bizhawk-headless/.scratch/` (beside the
existing `bin/` and `obj/`, covered by the repo's `tools/*` ignore
rule) rather than `Path.GetTempPath()`, and deletes it in a `finally`.

Four deliberate strength choices:

1. **No truncation.** Stopping at MHZ would satisfy the seven fixtures
   by construction. Running to DDZ and asserting the whole fifteen-line
   segment summary proves `mhz` ends at 28,156 rows *because* `fbz`
   arms at BK2 frame 237,913 — the post-advance arm ordering of §1.5 —
   and not because the capture ran out of movie. It also exercises the
   eight post-MHZ segment boundaries (`fbz`, `soz`, `lrz`, `hpz22`,
   `hpz`, `ssz`, `dez23`, `ddz`) before final comparison against their
   committed fixture directories.
2. **Canonical metadata identity.** Both installed and captured sides must
   contain the exact `6.42-s3k-completerun` literal. Only the validated
   `recording_date` line may differ; schema, key order, line count, and every
   other byte remain pinned.
3. **Canonical timing plus retained predecessor evidence.** Each installed
   and captured `hardware_timing.jsonl` has the same pinned byte length, line
   count, and 6.42 SHA-256. The cheap non-capture contract separately names
   all 27 migrated rows, reverses each exact `"boundary":"post_objects"` to
   `"boundary":"vint_service"`, and requires the frozen 6.40 predecessor
   hash per segment. It sums all fifteen reviewed counts, pins 27, and rejects
   the deliberately wrong expected aggregate 25. The ending segment stays
   byte-identical; raw frame, kind, ordinal, fingerprint, event position, and
   ordering cannot move.
4. **`run_id` absence asserted directly.** Line-count equality alone
   would let a stray `run_id` line pass if it displaced another key, so
   both files are probed for a `"run_id":` line explicitly. That absence
   plus the absent `run_manifest.json` is what makes this identity (A)
   and not (B)/(C).

Byte lengths are asserted before SHA-256 on every payload stream: a length
mismatch localises a truncated or over-long file, where a hash mismatch
only says "different". Fixture `.gz` bytes are streamed through SHA256
rather than materialised, which keeps the gate's footprint at the
capture's own 2.84 GB instead of 4.3 GB.

---

### 10.7 The run-mode gate (identities (C) and (B))

`S3KRunModeDifferentialTests` runs **two untruncated `--run-id` passes**
over the 114,622-row `s3-knux-multibonus-ss.bk2`, one per capture
identity, and **both are byte-exact**. It passed on the first attempt:
**no production change was needed**, and every observed delta is one
§7.4 already predicted. Tightening case 2 to byte-exact after
`63eccd290` likewise needed no production change — only the deletion of
the normalizations the legacy fixtures had required.

**Case 1 — identity (C), byte-exact.** `--run-id s3k-multibonus`
reproduces all four committed (C) dirs. `physics.csv` and
`aux_state.jsonl` match by length **and** sha256 with zero
normalization; `metadata.json` matches line for line with only the
`recording_date` value free. This closes `bonus_slots`,
`bonus_pachinko` and `special_stage`, which §10.5 previously listed as
hand-verified only. `special_stage`'s `aux_state.jsonl` is gated as the
0-byte file it is.

**Case 2 — identity (B), byte-exact.** `--run-id
s3-knux-multibonus-ss` reproduces the 25-segment run tree on the same
terms as case 1: `physics.csv` and `aux_state.jsonl` by raw length
**and** sha256 with zero normalization, `run_manifest.json`
byte-identical, and `metadata.json` line for line with only the
`recording_date` value free and `lua_script_version` pinned as the exact
`6.33-s3k-completerun` literal on both sides. The three SS segments'
`aux_state.jsonl` are gated as the 0-byte files they are. The pass must
publish exactly the 25 segment dirs, three files each, plus
`run_manifest.json` and nothing else.

`run_manifest.json` is what this case gates that (A) and (C) cannot:
8,740 bytes carrying all 25 segment records and all 22 transition
records with their sampled RAM. `S3KCompleteRunPublicationTests` already
gates the *formatter* given the right data; this gates the **recorder
rediscovering that data from the movie** — every `bk2_frame_offset`,
`trace_frame_count`, dir token, `saved_x_pos`, `rings_before`/`_after`,
`emeralds_*` and `last_star_post_hit`. It carries no `recording_date`,
so it is compared with **no free field at all**.

**This case used to be structural, and why it no longer is.** Until
commit `63eccd290` re-captured the fixtures, (B) was a 2026-07-19
**Windows** EmuHawk capture by Lua **6.31**, three builds behind HEAD,
and the case could only assert structure — segment inventory, dir
tokens, offset/row pairs, kinds and profiles, the 22-record transition
list — with three independently pinned, non-byte-exact deltas:

| Legacy delta | What it was | Scope of its effect, as measured then |
|---|---|---|
| CRLF (host text mode) | all 25 dirs + manifest contained `\r\n` | every file |
| `ADDR_FRAMECOUNT` `0xFE08`→`0xFE04` | Lua L547 reads `0xFE04` at HEAD; every legacy (B) `physics.csv` column 5 cell was the constant `0000` and every aux `vfc` / `level_frame_counter` the constant `0` | `physics.csv` **column 5 only** — all 41 other columns of all rows already matched; aux counter fields only |
| diagnostic hooks armed (pre-`192d9c976`) | hook-driven families present in exactly 4 of 25 segments | `hcz_2` +95 aux lines, `hcz_6` +48, `mgz` +69, `mgz_3` +69 |

There was **no unexplained residue** even then: after accounting for
those three, every remaining byte already matched, which is why
re-capturing under the current recorder identity — Linux, Lua 6.32,
hooks off — closed all three at the source rather than exposing new
work. The regenerated tree is LF, stamps 6.32 in the manifest and in all
25 `metadata.json`, carries the live `0xFE04` counter, carries
`capture_mode`, reports `pre_trace_osc_frames: 1`, and contains zero
hook-driven aux lines (§0.2).

The CRLF folding, the counter-column masking, the per-segment
hook-line-count literals and the differing-key allowances have all been
**deleted from the gate**, not left dormant behind now-unreachable
branches. Do not reintroduce any of them: emitting CRLF, reverting
`ADDR_FRAMECOUNT`, or arming the hooks would now break case 2 as well as
the identity-(A) gate and case 1.

Measured: **2m20s wall, 235 MB peak RSS**, 370 MB of output per pass.
The passes run sequentially and each output tree is deleted before the
next, so peak scratch is one pass, under `tools/bizhawk-headless/.scratch/`
for the same tmpfs reason as §10.6. Both fixture sides are hashed by
streaming the `.gz`, so neither side is materialised.

### 10.8 Migration status

With §10.6 and §10.7 landed alongside `S3KCompleteRunDifferentialTests`
(the fast, truncated smoke gate over identity (C)), all three named
capture identities have a ROM-backed differential gate, and the S3K complete-run
migration is complete: every Lua recorder in the fleet — S1, S2, S3K
standard, and now S3K complete-run — has a byte-parity-gated native
port. `tools/bizhawk/README.md`'s "Sonic 3 & Knuckles complete-run and
run mode" section carries the verified capture commands, the final gate
list, and the pinned metadata-delta policy for operator-facing use; this
document remains the byte-level authority those commands and gates
implement.
