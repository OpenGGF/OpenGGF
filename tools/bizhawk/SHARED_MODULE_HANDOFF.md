# BizHawk Trace Recorder — Shared Module Handoff

This is the durable home for two related pieces of context referenced from
`tools/bizhawk/lib/oggf_trace_common.lua`'s header comment and from the
commit history of the six per-game BizHawk trace recorders:

1. **What `oggf_trace_common.lua` is and, more importantly, is *not*** —
   the shared-module extraction (`fd3a74291` "refactor(trace): extract
   shared BizHawk recorder leaf helpers into oggf_trace_common") moved only
   pure, game-agnostic leaf helpers into one module. It deliberately left
   every ROM address constant (`ADDR_*` / `SS_ADDR_*`) duplicated per
   recorder. That decision is why a ROM-address bug fixed in one recorder
   does not automatically fix its siblings — see §2.
2. **The cross-recorder ROM-address audit** performed in `95c36166c`
   ("fix(tools): S3K standard recorder frame-counter address 0xFE08 ->
   0xFE04"), extracted from that commit message into a durable table so the
   next recorder touch can consult it instead of re-deriving it.

---

## 1. Why constants stayed per-recorder (root cause)

`tools/bizhawk/` has **six** trace recorders:

| # | Recorder | Scope |
|---|---|---|
| 1 | `s1_trace_recorder.lua` | S1 standard capture |
| 2 | `s1_complete_run_recorder.lua` | S1 per-level complete-run / run mode |
| 3 | `s2_trace_recorder.lua` | S2 standard capture + run mode |
| 4 | `s2_ss_trace_recorder.lua` | S2 special-stage-only capture |
| 5 | `s3k_trace_recorder.lua` | S3K STANDARD capture (three profiles) |
| 6 | `s3k_complete_run_recorder.lua` | S3K per-zone complete-run / bonus / special-stage |

Each recorder began life as an independent script and grew its own copy of
every ROM address it reads — including addresses that are identical across
recorders for the *same game* (e.g. both S3K recorders' `Level_frame_counter`,
`Current_zone`, `RNG_seed`). `fd3a74291` extracted the game-agnostic **leaf
helpers** (`bk2_input_mask`, `hex`, `angle_to_ground_mode`, `read_speed`,
`rom_joypad_to_mask`, `write_aux`, `json_escape`, `json_quote`, the `INPUT_*`
bitmask constants) into `lib/oggf_trace_common.lua`, loaded via a three-tier
`oggf_lib_dir()` loader — but its scope note is explicit:

> SCOPE: leaf helpers only. Do NOT move schema writers (`open_files`,
> `write_metadata`, `write_run_manifest`, `build_slot_dump`, ...),
> `*_csv_version` / schema constants, or the fast-headless toggle block into
> this module. Those embed per-game schema bytes and/or are statically
> grepped by `prepare_bizhawk_fast_lua.ps1` and MUST stay inline per recorder.

ROM address constants (`ADDR_FRAMECOUNT`, `ADDR_VBLA_WORD`, etc.) were never
in scope for that extraction either — they are declared `local` inside each
recorder file, in whatever order that recorder's author introduced them, with
no shared source of truth. **This is the root cause of the defect below**:
a fix to one recorder's copy of a constant does not propagate to the other
five, and nothing short of an explicit audit catches the gap.

**Practical consequence — the checklist for any future ROM-constant fix:**
when a `mainmemory.read_*` address is found wrong in one recorder, grep all
six for the same logical constant (by symbolic name across recorders, since
names are not even consistent — e.g. S1/S2 call it `v_framecount` in
comments while S3K calls it `Level_frame_counter`) before assuming the fix
is complete. Section 2 is that audit, current as of `e234a9d6b` (both defects
it found are now closed); re-run it whenever a recorder's RAM-map section
changes.

---

## 2. Cross-recorder ROM-constant audit (originally `95c36166c`, 2026-07-25; re-run at `e234a9d6b`, 2026-07-27)

**STATUS: BOTH defects this audit found are now CLOSED, and the re-run found
no third one.** (A) was fixed in `95c36166c`. (B) was fixed in `f71b5ea44`
(both S3K Lua recorders, `LUA_SCRIPT_VERSION` -> `6.32-s3k` /
`6.33-s3k-completerun`), all 39 S3K fixture directories were regenerated on it
in `eb87d681b`, and the native C# port was re-pinned in `e234a9d6b`
(`tools/bizhawk-headless/src/Recording/S3KRam.cs:69`). All six recorders now
agree on `ADDR_FRAMECOUNT = 0xFE04` and `ADDR_VBLA_WORD = 0xFE0E` (the S2
special-stage recorder reads neither). §2.3's "other pairs, no defect found"
conclusions stand unchanged at `e234a9d6b`; nothing from this audit remains
outstanding.

Performed by extracting every `ADDR_*`/`SS_ADDR_*` constant and every inline
literal-address `mainmemory.read` from all six recorders, then diffing each
same-game pair.

### 2.1 Frame-counter / VBlank-counter constants, all six recorders

| Recorder | `ADDR_FRAMECOUNT` (frame counter) | `ADDR_VBLA_WORD` (VBlank/`V_int_run_count` low word) |
|---|---|---|
| `s1_trace_recorder.lua` | `0xFE04` (`v_framecount`) — correct | `0xFE0E` — correct |
| `s1_complete_run_recorder.lua` | `0xFE04` (`v_framecount`) — correct | `0xFE0E` — correct |
| `s2_trace_recorder.lua` | `0xFE04` (`Level_frame_counter`) — correct | `0xFE0E` (`Vint_runcount+2`) — correct |
| `s2_ss_trace_recorder.lua` | n/a (does not read this address) | n/a (does not read this address) |
| `s3k_trace_recorder.lua` | `0xFE04` (`Level_frame_counter`) — **fixed in `95c36166c`, was `0xFE08`** | `0xFE0E` (`V_int_run_count` low word) — **fixed in `f71b5ea44`, was `0xFE12` = `Life_count`** |
| `s3k_complete_run_recorder.lua` | `0xFE04` (`Level_frame_counter`) — correct since `6564667eb` | `0xFE0E` (`V_int_run_count` low word) — **fixed in `f71b5ea44`, was `0xFE12` = `Life_count`** |

### 2.2 The two defects found — BOTH NOW CLOSED

**(A) `ADDR_FRAMECOUNT` in `s3k_trace_recorder.lua` — FIXED in `95c36166c`.**
Pointed at `0xFE08` = `Debug_placement_mode`, which is dead-zero during
normal gameplay (it is itself a ROM debug guard, e.g.
`AIZRideVineHandle_CheckGrab` at `docs/skdisasm/sonic3k.asm:46714+`). The
correct address is `Level_frame_counter = 0xFE04`, derived by a sequential
`ds.b` walk of `docs/skdisasm/sonic3k.constants.asm` `CrossResetRAM`
(`$FFFFFE00`): unused word `$FE00`, `Restart_level_flag` `$FE02`,
`Level_frame_counter` `$FE04`, `Debug_object` `$FE06`, `Debug_placement_mode`
`$FE08`, `V_int_run_count`(l) `$FE0C`, `Current_zone` `$FE10`. The
`Current_zone`/`Current_act` anchor (`$FE10`/`$FE11`, already used by the
recorder) confirms the base. Confirmed dead empirically before the fix:
`physics.csv` `gameplay_frame_counter` was a constant `0x0000` across all
three canonical STANDARD fixtures (`aiz1_to_hcz_fullrun`, `cnz`, `mgz`); the
sibling complete-run fixture (already fixed by `6564667eb`) showed 4547
distinct values over its AIZ segment. **Why this one recorder was missed
when `6564667eb` fixed the complete-run recorder:** each of the six
recorders carries its own copy of these ROM constants (§1) — there was no
shared constant to fix once. Bumped `s3k_trace_recorder.lua`'s
`LUA_SCRIPT_VERSION` `6.30-s3k` -> `6.31-s3k`; the three canonical STANDARD
fixtures (`aiz1_to_hcz_fullrun`, `cnz`, `mgz`) were regenerated on the fixed
recorder in `3eebb13bf`, and the native C# port's forked
recorder-identity read (`FrameCounterAddressFor`, the 4-argument
`FormatRow` overload) was deleted in `ba882f967` once both S3K recorders
agreed on `0xFE04`, unifying on `S3KRam.LevelFrameCounter`.

**(B) `ADDR_VBLA_WORD = 0xFE12` in BOTH S3K recorders — FIXED in `f71b5ea44`;
this paragraph describes it as it stood when queued.** `0xFE12` is `Life_count`. S3K's
`V_int_run_count` is a `ds.l` (long) at `$FE0C`, so the low word — the S1/S2
equivalent of `v_vblank_word` / `Vint_runcount+2` — is `0xFE0E`. S1/S2
recorders already read `0xFE0E` correctly. Confirmed empirically: the S3K
`physics.csv` `vblank_counter` column holds only `0x0300`/`0400`/`0500`/`0600`
(lives in the high byte, i.e. it is reading `Life_count`'s low byte behavior,
not a real per-VBlank counter) across the `aiz`/`cnz`/`mgz` STANDARD fixtures
and the complete-run fixtures, whereas S1 fixtures show ~1 distinct value per
frame as expected of a real VBlank counter. **Because both S3K recorders
agree with each other, pair-diffing the two S3K recorders alone would not
have caught this** — it took a cross-*game* comparison (S3K's column
behavior vs. S1/S2's) to surface it. Left unchanged deliberately: fixing it
invalidates the `vblank_counter` column and `oscillation_state`-adjacent aux
fields in every S3K fixture (both STANDARD and complete-run), which is a
second fixture-invalidating change that should be sequenced and reviewed on
its own rather than folded into the frame-counter fix. **Do not fix this as
a side effect of an unrelated recorder change; land it as its own commit
with its own fixture regeneration, following the same pattern as (A).**

**How (B) was actually closed, and what it cost.** It landed exactly as
prescribed above: `f71b5ea44` corrected both Lua recorders in one commit and
bumped both `LUA_SCRIPT_VERSION`s (`6.32-s3k`, `6.33-s3k-completerun`);
`eb87d681b` regenerated all 39 S3K fixture directories with the delta
categorized cell-by-cell before installing (only 35 carry a `vblank_counter`
column — the four special-stage-profile directories do not; `aux_state.jsonl`
came out byte-identical everywhere because no aux field reads that address, so
aux blobs were left untouched; offsets, row counts, segment inventories and the
manifest's 25 segments / 22 transitions all reproduced exactly); `e234a9d6b`
re-pinned the native C# port and its differential gates, taking the native
suite to 359 PASS / 0 FAIL / 0 SKIP. Verification that the column was dead:
across the 35 vblank-carrying fixtures,
`frames[0].vblankCounter() == frames[1].vblankCounter()` held **35/35 before
and 0/35 after**, and the frame-0 seed moved from lives-in-the-high-byte values
(`0300`, `0A00`, `0E00`, `1100`, …) to true counter values (`01EC`, `7ED8`,
`7E86`, `3329`, …).

Engine-side impact was measured as a controlled A/B — `git diff 94258e08c..HEAD -- src/main/`
is empty, so only fixture bytes moved. **Every S3K trace-replay frontier held**
(all 15 classes report a byte-identical first non-camera divergence);
`TestS3kMgzTraceReplay` shed 2,584 errors; `TestS3kSpecialStageTraceReplay`
stayed green; one previously red assertion
(`TestTraceExecutionModel.sonic3kMissingCpuExecutionHookMarksMovingDuplicateAsLag`)
went green because its subject row became representable, and one new red
appeared in `TestTraceReplayStartPositionPolicy` because a test premise that
depended on the frozen counter lost its subject. Full numbers, the
prediction-vs-actual phase-flip table, and the remaining open items are in the
2026-07-27 entry at the top of `docs/TRACE_FRONTIER_LOG.md`.

### 2.3 Other pairs audited, no defect found

- **S1 pair** (`s1_trace_recorder.lua` vs `s1_complete_run_recorder.lua`):
  no disagreement. The complete-run recorder is a strict superset
  (`ADDR_LIMITBTM1/2`, `ADDR_LOOKSHIFT`, `ADDR_BGSCROLLVERT`,
  `ADDR_OSCILLATE`, plus FZ-boss / RandomNumber ROM-code probe PCs).
- **S2 pair** (`s2_trace_recorder.lua` vs `s2_ss_trace_recorder.lua`): no
  value disagreement. `s2_ss_trace_recorder.lua` re-declares the
  special-stage block unprefixed; every overlapping address matches
  `s2_trace_recorder.lua`'s `SS_ADDR_*` copy. Only `SPECIAL_STAGE_INDEX`
  (`0xFE16`) is main-recorder-only, and `GAME_MODE`/`CTRL_1_HELD`/
  `CTRL_2_HELD` are SS-recorder-only.
- **Cosmetic, no data impact:** `s2_trace_recorder.lua`'s `ADDR_OPL_*` and
  `ADDR_OBJSTATE` comments carry S1 label names (`v_opl_routine`,
  `v_opl_screen`, `v_opl_data`, `v_objstate`). The addresses themselves are
  correct for S2 (verified against `docs/s2disasm/s2.constants.asm`:
  `Obj_placement_routine` `$F76C`, `Camera_X_pos_last` `$F76E`,
  `Obj_load_addr_right` `$F770`, `Obj_load_addr_left` `$F774`,
  `Object_Respawn_Table` `$FC00`, back-computed from `System_Stack $FE00`
  less the `$140` stack, `$BE` respawn data, and 2 index bytes). Comments
  only — no fix needed.
- **Verified-correct spot checks, no action needed:** S1 `v_vblank_word`
  `$FE0E` and `v_framecount` `$FE04`; S2 `Level_frame_counter` `$FE04`,
  `Vint_runcount+2` `$FE0E`, `Current_Special_Stage` `$FE16`,
  `Last_star_pole_hit` `$FE30`, `Saved_x/y_pos` `$FE32`/`$FE34`,
  `Emerald_count` `$FFB1`; S3K `Current_special_stage` `$FE16`,
  `Last_star_post_hit` `$FE2A`, `Saved_X/Y_pos` `$FE2E`/`$FE30`,
  `V_int_run_count` `$FE0C`, `Game_paused` `$F63A` (`RNG_seed $F636` + 4).

---

## 3. The lesson

**A fix to one recorder's copy of a ROM constant does not propagate to its
siblings.** This bit twice on the same underlying value: `6564667eb` fixed
`ADDR_FRAMECOUNT` in `s3k_complete_run_recorder.lua` only, and
`s3k_trace_recorder.lua` kept reading the dead `0xFE08` for an unknown
number of prior commits until `95c36166c` caught it via an explicit
cross-recorder audit — not because anyone was looking at
`s3k_trace_recorder.lua` specifically. The shared-module extraction
(`fd3a74291`) intentionally did not create a shared constants module, so
this class of bug is structurally still possible for every constant listed
in §2.1 and §2.3.

Two ways to reduce recurrence, neither acted on yet (recorded here so the
next person doesn't have to rediscover the tradeoff):

1. **Audit, not extraction, as the standing discipline.** Whenever a
   `mainmemory.read_*` address is found wrong or added in one recorder, redo
   the full six-recorder diff in §2 before considering the fix complete.
   This is cheap (all six files are Lua, `grep -n "^local ADDR_"` per file)
   and was sufficient to catch defect (B) here even though it wasn't the
   commit's stated goal.
2. **A shared ROM-constants module** (extending `oggf_trace_common.lua` or a
   sibling `oggf_rom_constants.lua`) would make a same-game pair
   structurally incapable of disagreeing, at the cost of the same
   `loadfile` plumbing and locals-budget care `fd3a74291` already took for
   leaf helpers (S3K recorders sit near Lua's 200-locals cap — see that
   commit's message for the do-block trick used to keep the loader from
   costing a permanent local slot). Not done here, and still not done: every
   constant in §2.1 and §2.3 remains a per-recorder copy, so the failure mode
   is structurally intact even though both known instances of it are now
   closed. Defect (B) is the sharper argument for this than the wording that
   stood here before — the two S3K recorders **agreed with each other** and
   were **both wrong**, so no amount of same-game pair-diffing would have
   caught it. Only the cross-*game* comparison did.

**Defect (B) is CLOSED — how it was landed.** It followed this section's own
prescription exactly: `f71b5ea44` corrected `ADDR_VBLA_WORD` in both S3K
recorders in one commit and bumped both `LUA_SCRIPT_VERSION`s (`6.32-s3k`,
`6.33-s3k-completerun`); `eb87d681b` regenerated every affected fixture — all
39 S3K directories, of which 35 carry the `vblank_counter` column — after
categorizing the delta mechanically cell-by-cell, confirming it reduced to the
`vblank_counter` column alone with `aux_state.jsonl` byte-identical everywhere
(no aux field reads that address) and offsets, row counts, segment inventories
and the manifest's 25 segments / 22 transitions all reproducing exactly; and
`e234a9d6b` re-pinned the native C# port (`S3KRam.VblankWord`) and its four S3K
differential gates. Engine-side consequences (all frontiers held; MGZ -2,584
errors; one red assertion recovered, one test premise died) are recorded in the
2026-07-27 entry of `docs/TRACE_FRONTIER_LOG.md`.

**Nothing from this audit is outstanding.** Both defects are closed and the
§2.3 pairs re-checked clean at `e234a9d6b`. The next recorder change should
re-run the §2 diff from scratch rather than trusting this snapshot.

---

## 4. The original extraction plan (still standing, partially executed)

Everything below is the pre-existing feasibility study that proposed
`lib/oggf_trace_common.lua`. `fd3a74291` executed its phases 0-2 for the **leaf
helpers** only; the ROM address constants it lists under "Do NOT extract" were
deliberately left per-recorder, which section 1 above identifies as the root cause
of both defects in section 2. Read the two halves together: the audit is the
evidence that this plan's scope should now be widened to cover the constants, or
that some other mechanism must stop a fix to one recorder from silently failing to
reach its five siblings.

**Status:** proposed, not started. Read-only feasibility exploration complete.
**Goal:** stop copy-pasting the same leaf helpers across the six per-game recorders
by having them `dofile` one shared module. **Not** a rewrite of the schema writers.

## Why (the duplication)

Six real recorders live in `tools/bizhawk/`:

| File | Lines |
|------|------:|
| `s1_trace_recorder.lua` | 782 |
| `s1_complete_run_recorder.lua` | 1797 |
| `s2_trace_recorder.lua` | 2093 |
| `s2_ss_trace_recorder.lua` | 785 |
| `s3k_trace_recorder.lua` | 4983 |
| `s3k_complete_run_recorder.lua` | 5947 |

~370 duplicated lines of game-agnostic leaf helpers. The worst offender is
`bk2_input_mask()` — **26 lines, byte-for-byte identical in 5 files**.

### Extract these (byte-identical or trivially wrappable) — the ONLY scope

| Helper | Approx lines | Sites (file:line at time of writing) |
|--------|-----:|--------|
| `bk2_input_mask(fallback_raw, trace_row, bk2_frame_offset)` | 26 | `s1:202`, `s1_complete:463`, `s2:409`, `s3k:646`, `s3k_complete:963` |
| `hex(val, width)` | 7 | `s1:230`, `s1_complete:491`, `s2:437`, `s3k:673`, `s3k_complete:990` |
| `angle_to_ground_mode(angle)` | 6–8 | `s1:240`, `s1_complete:501`, `s2:469`, `s3k:681`, `s3k_complete:998` |
| `write_aux(aux_file, json_str)` | 6 | `s1:248`, `s1_complete:509`, `s2:477`, `s2_ss:179`, `s3k:688`, `s3k_complete:1005` |
| `read_speed(base, offset)` | 3 | `s1:182`, `s1_complete:443`, `s2:385`, `s3k:628`, `s3k_complete:945` |
| `rom_joypad_to_mask(raw)` | 5–9 | `s1:189`, `s3k:632`, … (logic identical, formatting differs) |
| `json_escape(v)` / `json_quote(v)` | ~4 | `s2:445 json_escape`, `s2_ss:168`, `s3k:695 json_quote`, `s3k_complete:1012` |
| `INPUT_UP/DOWN/LEFT/RIGHT/JUMP` = 0x01/0x02/0x04/0x08/0x10 | 5 | `s1:123`, `s2:291`, `s3k:387`, `s3k_complete:571` |

> Two helpers rely on file-scope upvalues and must take them as parameters to
> stay pure: `bk2_input_mask` reads the recorder's `bk2_frame_offset`; `write_aux`
> closes over `aux_file`. Pass both in. Both also call BizHawk globals
> (`mainmemory`/`movie`/`emu`) which are present in the module's environment — fine.

### Do NOT extract (out of scope — high risk, low reward)

- `open_files`, `write_metadata`, `write_ss_metadata`, `write_run_manifest`,
  `close_files`, `build_slot_dump` — they *look* similar but embed per-game CSV
  column lists, ROM addresses, and schema fields. The comparator is **byte-sensitive**;
  parameterizing these risks changing emitted bytes. Leave inline.
- All `*_csv_version` / `lua_script_version` / column-list / schema constants —
  keep inline per recorder so the three games' schemas evolve independently.
  (Note: `s1_complete_run_recorder.lua` now uses file-level globals
  `S1_COMPLETE_SCRIPT_VERSION` / `S1_COMPLETE_ROM_CHECKSUM` — that is per-recorder
  single-sourcing, NOT the shared module.)
- **The fast-headless toggle block** (`emu.limitframerate(false)`,
  `client.speedmode(6400)`, `client.invisibleemulation(true)`, `client.SetSoundOn(...false)`
  before the main loop, e.g. `s1:737-743`, `s2:1985-1991`, `s3k:4914-4920`).
  `prepare_bizhawk_fast_lua.ps1:31-42` statically greps each recorder's TEXT for
  those exact calls before the main loop; moving them into the module makes the
  guard abort every launch. **Must stay inline.**

## Mechanism (verified available)

BizHawk 2.11 runs **native Lua 5.4** (KeraLua), not the old 5.1 KopiLua — proven
by the recorders' use of `|` / bit-ops (`s1:218,222`, `s3k:662-668`). So the full
stdlib is available: `require`, `package.path`, `dofile`, `loadfile`, `debug`, `os`.

- `dofile(<absolute path>)` is already the production launch mechanism: the
  fast-headless wrapper generator `prepare_bizhawk_fast_lua.ps1:56-62` emits a
  `%TEMP%` wrapper whose body is `local t = "<abs>"; dofile(t)`. Absolute-path
  `dofile` of another Lua file works in this exact runtime.
- **Relative paths are unreliable.** EmuHawk's CWD is the loaded script's dir on
  the `--lua=` route (`README.md:61-64`), but the `.bat` route runs the `%TEMP%`
  wrapper with `pushd tools/bizhawk` (`run_bizhawk_lua.bat:117`). Do not rely on
  CWD or default `package.path`.
- `os.getenv` works today (heavy use: `s2:110-122`, `s3k:240+`, `diag_template_fast.lua:25-27`).

## Proposed design

Create `tools/bizhawk/lib/oggf_trace_common.lua` returning a table `M` with the
byte-copied helper bodies (see list above). Consume via a robust loader at the top
of each recorder:

```lua
local function oggf_lib_dir()
  local env = os.getenv("OGGF_BIZHAWK_LIB")            -- launcher-provided, most robust
  if env and #env > 0 then return env end
  local src = debug.getinfo(1, "S").source             -- "@<abs path to this recorder>"
  local dir = src:match("^@(.*[/\\])")                 -- strip filename
  if dir then return dir .. "lib/" end
  return "lib/"                                          -- CWD fallback
end
local C = assert(loadfile(oggf_lib_dir() .. "oggf_trace_common.lua"))()
```

Call sites become `C.hex(x)`, `C.bk2_input_mask(raw, row, bk2_frame_offset)`,
`C.write_aux(aux_file, s)`, etc. Re-bind only the hot-loop **constants** locally
(`local INPUT_UP = C.INPUT_UP`) — cheap, and avoids re-introducing local slots
against the 200-locals limit. Consuming helpers through the single `C` table
*reduces* main-chunk locals (helps the trap).

**Launcher change (recommended, one line):** in `run_bizhawk_lua.bat` (~line 116,
before launch) add `set "OGGF_BIZHAWK_LIB=%~dp0lib\"` so the absolute path is
authoritative on the `.bat`/wrapper route; the `debug.getinfo` fallback covers the
direct `--lua=` route.

Why path-robust across worktrees/headless/diag: `debug.getinfo(1,"S").source`
returns the recorder's own absolute path even when reached through the wrapper's
`dofile`; `lib/` travels next to the recorder in every checkout (no cross-worktree
bleed); the env var wins for headless `.bat`.

## Hard constraints / risks

- **Byte-identical output is mandatory.** The trace comparator diffs bytes.
  Extraction must be a *pure copy* with identical `string.format` specifiers and
  formatting. After each step, regenerate one trace per affected game and `diff`
  `physics.csv` / `aux_state.jsonl` / `metadata.json` against pre-change output —
  require **zero diff** before committing.
- **Silent failure is the dominant risk.** In `--chromeless`, Lua errors are
  invisible (`README.md:69-72`): a bad lib path → `dofile` throws → recorder never
  runs → no `trace_output/` → looks like a core-init crash. Mitigation: wrap the
  load in `assert(loadfile(path))`, keep the three-tier fallback, and first-run
  validate WITHOUT `--chromeless` to surface a load-error dialog.
- **Do not touch the fast-headless block or schema writers** (see above).

## Validation tooling

- `luac -p <file>` is available locally (`/usr/bin/luac`, Lua 5.4) — syntax-check
  the module and every edited recorder in pre-commit/CI. (Only parse is meaningful
  offline; the module references BizHawk globals absent under stock `lua`.)
- `lupa` (python) is NOT installed here. Runtime behavior still needs a real
  BizHawk regen to validate (that's the byte-diff gate above).

## Phased plan

0. Create `lib/oggf_trace_common.lua` with **`bk2_input_mask` + `json_escape`/`json_quote` only**.
   Add the loader to the smallest recorder (`s1_trace_recorder.lua`). Add
   `OGGF_BIZHAWK_LIB` to `run_bizhawk_lua.bat`. Regenerate one S1 trace; `diff` — must
   be identical. Test BOTH the `.bat` route and a direct `--lua=` launch.
1. Expand the module with `hex`, `angle_to_ground_mode`, `read_speed`,
   `rom_joypad_to_mask`, `write_aux`, `INPUT_*`. Migrate `s1_trace_recorder.lua`
   fully; regen + diff.
2. Roll to the other five recorders one at a time, each gated by a per-game
   byte-identical regen diff. Update `README.md` and the recorder header comments.
3. **Stop.** Do not extend into schema writers or the fast-headless block.

## Commit policy reminder

Per `CLAUDE.md`: recorder-schema changes commit separately from any regenerated
trace payloads; use the trailer block (no `--no-verify`); keep
`docs/TRACE_FRONTIER_LOG.md` updated only if a frontier moves (this refactor should
not move any).
