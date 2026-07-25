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
is complete. Section 2 is that audit, current as of `95c36166c`; re-run it
whenever a recorder's RAM-map section changes.

---

## 2. Cross-recorder ROM-constant audit (as of `95c36166c`, 2026-07-25)

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
| `s3k_trace_recorder.lua` | `0xFE04` (`Level_frame_counter`) — **fixed in `95c36166c`, was `0xFE08`** | `0xFE12` (`Life_count`) — **wrong, queued, see §2.2** |
| `s3k_complete_run_recorder.lua` | `0xFE04` (`Level_frame_counter`) — correct since `6564667eb` | `0xFE12` (`Life_count`) — **wrong, queued, see §2.2** |

### 2.2 The two defects found

**(A) `ADDR_FRAMECOUNT` in `s3k_trace_recorder.lua` — FIXED here.**
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

**(B) `ADDR_VBLA_WORD = 0xFE12` in BOTH S3K recorders — wrong, NOT fixed
here, deliberately queued.** `0xFE12` is `Life_count`. S3K's
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
   costing a permanent local slot). Not done here: the two S3K recorders
   disagreeing on `ADDR_VBLA_WORD` (§2.2) is itself evidence that per-game
   constants were never unified even within one game, so this would be a
   larger, deliberate follow-up, not a drive-by extension of the leaf-helper
   module.

**When queuing defect (B):** fix `ADDR_VBLA_WORD` in both S3K recorders in
one commit (they already agree with each other, so no unification step is
needed — only correction), bump both `LUA_SCRIPT_VERSION`s, and regenerate
every fixture whose `vblank_counter` column or VBlank-adjacent aux field
would change: the three STANDARD fixtures (`aiz1_to_hcz_fullrun`, `cnz`,
`mgz`) and every complete-run/bonus/special-stage identity (A/B/C — see
`tools/bizhawk-headless/docs/s3k-run-publication.md` §"capture identities").
Follow the same isolation-before-installing discipline `3eebb13bf` used for
defect (A): categorize every byte delta mechanically (cell-by-cell CSV,
per-key JSON) before installing, and confirm the delta reduces to exactly
the `vblank_counter`/VBlank-derived fields with no other column moving.
