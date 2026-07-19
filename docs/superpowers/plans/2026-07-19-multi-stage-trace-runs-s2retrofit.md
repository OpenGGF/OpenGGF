# Multi-Stage Trace Runs — S2 Retrofit (Halfpipe Round-Trip) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retrofit `s2_trace_recorder.lua` with an env-gated run mode: a stage-detour state machine, an embedded 48-column halfpipe row writer (minimal port — no RunObjects PC hooks), and run-manifest emission, so a level→halfpipe→level star-post round-trip records as a manifest-backed run; plus a synthetic S2 run fixture test and the recording procedure docs.

**Architecture:** The S2 engine/Java SS surface already shipped (`SpecialStageTraceData`, `S2SpecialStageReplayHarness`, `AbstractS2SpecialStageTraceReplayTest`, `Sonic2SpecialStageComparisonState`) and `TraceMetadata`/`TraceRunManifest`/`TraceCatalog`/the visual run branch already accept `game:"s2"` runs — **this plan is recorder + fixture + docs only; zero `src/main` changes.** The detour state machine mirrors the S1/S3K ports, with one structural difference: the S2 level recorder is a single-flat-output-dir recorder driven by an existing byte-stable workflow (`record_s2_level_select_traces.ps1`), so run mode (per-segment subdirectories + manifest) activates ONLY when `OGGF_TRACE_RUN_ID` is set; without it the recorder behaves exactly as today (a `$10` edge finalizes and stops).

**Tech Stack:** BizHawk Lua, JUnit 5/Jupiter, existing trace framework.

## Global Constraints

- **Comparison-only invariant** (trace-replay-bug-fixing skill); **no zone/route/frame carve-outs**; **ROM-only runtime assets**; JUnit 5/Jupiter only.
- **Byte-stability of the existing S2 workflow is a hard requirement:** with `OGGF_TRACE_RUN_ID` unset, `s2_trace_recorder.lua` must produce byte-identical artifacts to today for every existing invocation path (`record_s2_trace.bat`, `record_s2_level_select_traces.ps1`, all `TRACE_PROFILE`s incl. `level_gated_reset_aware` segment-skipping). Run mode is opt-in.
- **`s2_ss_trace_recorder.lua` stays untouched** (spec: it remains the interior-only capture tool; its hook-driven aux contract `Assert-SsAuxCoverage` keeps applying to `traces/s2/special_stage` only).
- **Lua budget & placement:** `s2_trace_recorder.lua` has ~151 top-level `local` statements (200-local main-chunk limit). ALL new state/constants/functions added by this plan are **globals**. Placement rule (S1-maze lesson): the state-globals block goes near the top constants; ALL new global FUNCTIONS are defined AFTER the file-scope locals they capture — the last relevant local block is the State block (~L259–290, `started`/`finished`/`trace_frame`/`bk2_frame_offset`/`physics_file`/`aux_file`/forward-declared `close_files`) and the existing helpers `bk2_input_mask` (~L321) / `reset_recording_state` (~L435) / `write_metadata` (~L482) / `close_files` assignment (~L603); define new functions after ALL of these (i.e., after ~L603). A global function defined above a referenced local binds a nil global; the parse gate still passes and it explodes at the first live detour.
- **NO `event.onmemoryexecute` hooks in this port** (BizHawk allows 2 total; the interior recorder uses both; the spec defers the hooks decision to the first round-trip capture). Consequence, stated for reviewers: the run's `ss/` segment carries a REDUCED aux surface (no `run_objects_end`/`stage_finished`/`results_started` events) and therefore must NEVER be copied over `src/test/resources/traces/s2/special_stage` (which the hook-based `Assert-SsAuxCoverage` contract governs). The Task 3 README procedure states this prohibition explicitly.
- **Commit policy:** 7-trailer block on every non-merge commit; the lua commit justifies `Changelog: n/a: recorder tooling only, no engine change`; test/docs commits `Changelog: n/a: test-only change` / `n/a: docs only`. Stage exact paths only; never `git add -A`; never `git stash`. End commits with the session's `Co-Authored-By` / `Claude-Session` lines.
- **VERIFY-ON-FIRST-CAPTURE:** the state-machine RAM reads below reuse addresses already proven by the shipped interior recorder; only the transition-record addresses (`Saved_x_pos` family, `f_bigring`) are new reads — the recorder prints them at detour boundaries so the first real capture validates them.

## Verified S2 ROM facts (from `docs/s2disasm`, mainmemory = low 16 bits)

| Symbol | Address | Fact |
|---|---|---|
| `Game_Mode` | `0xF600` | `GameModeID_Level = $0C`, `GameModeID_SpecialStage = $10` (`s2.constants.asm:468-469`) |
| SS entry edge | — | **Direct `$0C -> $10`**: `Obj79_Star` writes `f_bigring = 1` then `Game_Mode = $10` on star-ring touch (`s2.asm:44877-44878`); spawn gated on 1P mode + `Emerald_count != 7` + `Ring_count >= 50` (`s2.asm:44661-44667`) |
| SS exit edge | — | `$10 -> $0C` at `s2.asm:6813`; the SS results tally (Obj6F, ring bonus loop `s2.asm:6797-6807`) runs **under `$10`** — recorded into the ss segment tail, uncompared (same model as the S1 maze results tail) |
| `f_bigring` | `0xF7CD` (b) | set to 1 at entry (`s2.constants.asm:1610`) — S2's `special_bonus_entry_flag` analog |
| `Last_star_pole_hit` | `0xFE30` (b) | star-post index (`s2.constants.asm:1704`) |
| `Saved_x_pos` / `Saved_y_pos` | `0xFE32` / `0xFE34` (w) | return position saved by `Obj79_SaveData` (`s2.asm:44738-44739`), restored on reload (`s2.asm:44778-44779`) |
| `Saved_Ring_count` | `0xFE36` (w) | saved at star post (`s2.asm:44742`); NOTE the reload restores then immediately **zeroes** `Ring_count` (`s2.asm:44780-44782`) — `rings_after` at re-arm will read 0; record the truth, do not "fix" it |
| `Ring_count` | `0xFE20` (w) | |
| `Emerald_count` | `0xFFB1` (b) | |
| `Current_Special_Stage` | `0xFE16` (b) | ss segment's `special_stage_index` (read at ss arm) |
| `Current_Zone`/`Current_Act` | `0xFE10`/`0xFE11` | |

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `tools/bizhawk/s2_trace_recorder.lua` | Modify | Env-gated run mode: detour machine, embedded 48-col SS writer (no hooks), manifest emitter |
| `src/test/resources/traces/synthetic/run_ehz_ss_3seg/` | Create | Synthetic 3-segment S2 run fixture (level/ss/level, `starpost_special` + `stage_exit` transitions) |
| `src/test/java/com/openggf/tests/trace/TestS2SyntheticRunFixture.java` | Create | Validates the fixture through `TraceRunManifest` + `SpecialStageTraceData` |
| `tools/bizhawk/README.md`, `docs/TRACE_FRONTIER_LOG.md` | Modify | Round-trip recording procedure + frontier entry |

**Scope notes (explicit, for reviewers):**
- Zero `src/main` changes: `TraceMetadata` already parses `run_id`/`segment_index` (plan-a), `TraceRunManifest.validate` has no per-game profile whitelist and `ENTRY_KINDS` already contains `starpost_special`/`stage_exit`, `TraceCatalog.romZoneToProgressionIndex` is identity for s2, and `applyPerGameSpecialStageConfig` already handles `"s2"`. The chain test (`TestS3kBonusRoundTripChain` analog) and in-chain/visual SS-interior comparison remain the shared deferred follow-up recorded in the frontier log since the blue-spheres/S1 plans.
- The spec's open question "are the RunObjects PC hooks ported too?" is answered NO for this MVP (two-hook ceiling + interior recorder unaffected); the first round-trip capture's divergence report decides whether to revisit (spec Component 2 pacing note).

---

### Task 1: Recorder retrofit — env-gated run mode

**Files:**
- Modify: `tools/bizhawk/s2_trace_recorder.lua`

**Interfaces:**
- Consumes (read, do NOT modify): `tools/bizhawk/s2_ss_trace_recorder.lua` — SS RAM address block (~L66–113), `read_ss_character` (~L189), `read_ss_state` (~L228), 48-col header (~L369–370) and row writer (~L596–620), metadata writer (~L386–405); `tools/bizhawk/s1_complete_run_recorder.lua` — the landed S1 port as the state-machine/manifest model (`append_level_segment_done`, `start_ss_segment`/`finalize_ss_segment`, detour branch, `finalize_run_end` funnel, `write_run_manifest`); `src/main/java/com/openggf/trace/TraceRunManifest.java` + `src/test/resources/traces/synthetic/run_aiz_gumball_3seg/run_manifest.json` (schema ground truth).
- Produces: with `OGGF_TRACE_RUN_ID` set — per-segment subdirs under `OUTPUT_DIR` (`seg1_<zone><act>/`, `ss/`, `seg2_<zone><act>/`, numbered `segN_` in arm order so repeat zones never collide) each holding its own `physics.csv`/`aux_state.jsonl`/`metadata.json`, plus `run_manifest.json` at `OUTPUT_DIR` root. Without the env var — byte-identical behavior to today.

**Behavior contract:**
1. `run_id = os.getenv("OGGF_TRACE_RUN_ID") or nil` (global). **Run mode iff `run_id ~= nil`.** All new behavior — segment subdirs, the detour branch, transitions, manifest — is gated on it. Without it, the existing `$10`-edge path (finalize + `finished = true`) is untouched, and no new file I/O occurs.
2. In run mode, the existing arm path redirects output into a per-segment subdir: at arm time set `current_segment_dir_token = string.format("seg%d_%s%d", #segments_done + 1, start_zone_name, apparent_act_for(...) + 1)` and point the per-segment file opens at `OUTPUT_DIR .. current_segment_dir_token .. "/"` (create via the recorder's existing mkdir mechanism, once per segment). The metadata writer keeps writing into the segment dir. Plain mode keeps writing flat into `OUTPUT_DIR` exactly as today — implement by making the "effective output dir" a variable that equals `OUTPUT_DIR` in plain mode.
3. Detour state machine (port the S1 shape, S2 constants): a branch `if run_id ~= nil and started and game_mode == GAMEMODE_SPECIAL_STAGE then` placed BEFORE the existing `game_mode ~= GAMEMODE_LEVEL` finalize/stop branch (~L1095). Entry (gated `detour_active ~= "special_stage"`): finalize the armed level segment (flush + `write_metadata()` + `append_level_segment_done(trace_frame)` + `close_files()` + `started=false; trace_frame=0`), push the `starpost_special` transition (indices `from = #segments_done - 1`, `to = #segments_done`, computed AFTER the finalize), `start_ss_segment()`, `detour_active = "special_stage"`, print the boundary record, `return`. Continuation: `write_ss_row(); return`. First non-`$10` frame: `finalize_ss_segment(); detour_active = nil`, fall through. Entry transition fields (VERIFY-ON-FIRST-CAPTURE prints): `mode_change_bk2_frame = emu.framecount()`, `special_bonus_entry_flag = mainmemory.read_u8(0xF7CD)` (`f_bigring`), `saved_x_pos = read_u16_be(0xFE32)`, `saved_y_pos = read_u16_be(0xFE34)`, `last_star_post_hit = read_u8(0xFE30)`, `rings_before = read_u16_be(0xFE20)`, `emeralds_before = read_u8(0xFFB1)`.
4. Post-SS level re-arm (run mode only): today the recorder STOPS at any non-level mode; in run mode, after `finalize_ss_segment()` the arm gate must be allowed to fire again for the return level segment (reset the arm state the way `reset_recording_state()` does WITHOUT deleting prior segment output — the S1 port's `reset_recording_state(true)` keep-files semantics; check what S2's `reset_recording_state` deletes (~L435–458) and split accordingly). At that re-arm, push the `stage_exit` transition (gated on last `segments_done` kind == `"special_stage"`): `mode_change_bk2_frame`, `rings_after = read_u16_be(0xFE20)` (will be 0 — ROM zeroes on reload, record the truth), `emeralds_after = read_u8(0xFFB1)`. NOTE: the arm path's segment-skip logic (`TARGET_GAMEPLAY_SEGMENT`) must not swallow the return segment — in run mode, bypass the segment-skip comparison after the first armed segment (the round-trip movie is not a level-select multi-segment BK2; document with a comment).
5. `start_ss_segment` / `write_ss_row` / `finalize_ss_segment` / `write_ss_metadata` (all globals): minimal port from `s2_ss_trace_recorder.lua` — the SS RAM address constants (as globals, prefixed `SS_` where needed to avoid collisions), `read_ss_character`/`read_ss_state` transcribed, the exact 48-column header and `%x` lowercase row format (the SS convention — do NOT reuse the level writer's `%04X` helpers), `lag` via `emu.islagged()`, P1/P2 input via the existing `bk2_input_mask`-style BK2 read for both pads (the interior recorder's `joypad_mask_from_frame(frame, pad)`; port it as a global). SS `bk2_frame_offset` = `emu.framecount()` at the first `$10` frame with frame 0 recorded IMMEDIATELY (the SS convention — no dead-frame skip; keep the level segments' existing dead-frame-skip semantics unchanged; add a comment stating the asymmetry is intentional and matches both shipped recorders). `write_ss_metadata` fields: `game "s2"`, `trace_profile "s2_special_stage"`, `special_stage_index = read_u8(0xFE16)` at arm, `ss_csv_version 1`, `characters ["sonic","tails"]`, `main_character "sonic"`, `sidekicks ["tails"]`, `bk2_frame_offset`, `trace_frame_count`, `source_bk2` (the existing `OGGF_BK2_BASENAME` env value the level metadata uses), `lua_script_version` (bumped, see 7), `recording_date`, `run_id`, `fresh_load false`, `segment_index = #segments_done`.
6. `append_level_segment_done(rows)` (global): appends `{dir = current_segment_dir_token, kind = "level", profile = TRACE_PROFILE, zone_id = <engine zone id the metadata writes>, act = <1-based act>, bk2_frame_offset = bk2_frame_offset, rows = rows}`. `finalize_ss_segment` appends `{dir = "ss", kind = "special_stage", profile = "s2_special_stage", special_stage_index = <armed value>, zone_id = 0, act = 0, bk2_frame_offset = <ss offset>, rows = <ss trace_frame>}`. Level metadata gains `run_id`/`segment_index` lines when in run mode (conditional emission, mirroring the S1/S3K writers).
7. `write_run_manifest()` + `finalize_run_end()` funnel (globals, port from the S1 recorder): manifest gated on run mode (`run_id == nil` -> skip entirely); emitted keys per `TraceRunManifest` (`run_schema`, `game "s2"`, `source_bk2`, segments with `trace_frame_count` mapped from `rows`, transitions). Inline literals — do NOT reference S1/S3K globals: script version = bump `LUA_SCRIPT_VERSION` `"9.11-s2"` -> `"9.12-s2"` (minor bump keeps `TraceMetadata.nativePreludeMode()`'s major-9 parse happy — verify that parse accepts `"9.12-s2"` by reading `nativePreludeMode()` first), `rom_checksum "7B905383"` (S2 World REV01 CRC32 per CLAUDE.md). EVERY termination path funnels through `finalize_run_end()` (detour if/else -> ss vs level finalize -> `write_run_manifest()`): the `$10`-stop branch (run mode replaces stop-with-finalize; plain mode keeps today's behavior), movie-end/`FINISHED` checks, and the FRAME_CAP backstop — enumerate every `finished = true` site in the file and route each (or document why a site is dead/shadowed, per the S1 review lesson).
8. All prints at boundaries include the transition field values (VERIFY-ON-FIRST-CAPTURE).

- [ ] **Step 1:** Read the four consumed files (interfaces above). Enumerate every `finished = true` site in `s2_trace_recorder.lua` and note its path (stop branch ~L1103, movie-end ~L1112–1131, FRAME_CAP backstop ~L1341–1345, plus any others found).
- [ ] **Step 2:** Add the state-globals block (near top constants): `segments_done = {}`, `transitions_done = {}`, `detour_active = nil`, `current_segment_dir_token = nil`, `current_ss_index = nil`, `run_id = os.getenv("OGGF_TRACE_RUN_ID") or nil`, plus the SS address/offset constants as globals.
- [ ] **Step 3:** Implement contract items 2–6 (functions defined after ~L603 per the placement rule).
- [ ] **Step 4:** Implement contract item 7 (funnel + manifest).
- [ ] **Step 5:** Parse gate: `"C:\Users\farre\IdeaProjects\sonic-engine\docs\skdisasm\build_tools\lua\lua.exe" -e "assert(loadfile('tools/bizhawk/s2_trace_recorder.lua'))"` — exit 0.
- [ ] **Step 6:** Byte-stability check for plain mode: with `OGGF_TRACE_RUN_ID` unset, verify by CODE INSPECTION (write the trace in the report) that every plain-mode path is unchanged: arm, row write, metadata fields (no `run_id`/`segment_index` lines emitted when unset), `$10`-edge stop, `reset_recording_state`, movie-end, FRAME_CAP. The self-review must name each touched branch and state its plain-mode behavior.
- [ ] **Step 7:** `mvn "-Dtest=com.openggf.tests.trace.TestTraceRunManifest" test` stays green.
- [ ] **Step 8:** Commit — `feat(trace): s2 recorder env-gated run mode + halfpipe writer + run manifest` (lua only; `Changelog: n/a: recorder tooling only, no engine change`).

---

### Task 2: Synthetic S2 run fixture + validation test

**Files:**
- Create: `src/test/resources/traces/synthetic/run_ehz_ss_3seg/run_manifest.json` + `seg1_ehz1/metadata.json` + `ss/metadata.json` + `seg2_ehz1/metadata.json` (+ minimal `physics.csv` per segment: header + 2 rows each; the ss one uses the 48-col header and two `%x` rows; level ones use the v7 42-col header + two rows — copy a header from an existing committed S2 trace and hand-write two plausible rows)
- Test: `src/test/java/com/openggf/tests/trace/TestS2SyntheticRunFixture.java`

**Interfaces:**
- Consumes: `TraceRunManifest.load/validate`, `SpecialStageTraceData.load`, `TraceMetadata.load`. Model test: `TestTraceRunSyntheticFixture` (the plan-a S3K synthetic fixture test — read it first and mirror its structure).

- [ ] **Step 1:** Write the failing test: (a) `TraceRunManifest.load(fixtureDir.resolve("run_manifest.json"))` + `validate(fixtureDir)` passes for game `"s2"` with segments level/special_stage/level and transitions `starpost_special` (0->1, carrying `special_bonus_entry_flag`/`saved_x_pos`/`saved_y_pos`/`last_star_post_hit`/`rings_before`/`emeralds_before`) and `stage_exit` (1->2, `rings_after` 0, `emeralds_after`); (b) `SpecialStageTraceData.load(fixtureDir.resolve("ss"))` succeeds (profile `s2_special_stage` accepted, 2 frames parsed, `metadata().runId()` and `segmentIndex()` populated); (c) the two level segment metadatas parse with `runId`/`segmentIndex` set.
- [ ] **Step 2:** Run: `mvn "-Dtest=com.openggf.tests.trace.TestS2SyntheticRunFixture" test` — FAIL (fixture absent).
- [ ] **Step 3:** Author the fixture files. The manifest mirrors `run_aiz_gumball_3seg`'s shape with s2 values; metadata files carry the exact fields Task 1's writers emit (keep them consistent — the fixture doubles as the emitter's executable specification). The ss `physics.csv` 48-col header must be copied verbatim from `s2_ss_trace_recorder.lua`'s header string; its two rows must parse under `SpecialStageTraceFrame` (run the test to prove it).
- [ ] **Step 4:** Run to green.
- [ ] **Step 5:** Also re-run `mvn "-Dtest=com.openggf.tests.trace.TestTraceRunManifest,com.openggf.tests.trace.TestTraceRunSyntheticFixture" test` — both stay green (the new fixture must not break the S3K synthetic scan assumptions; if `TraceCatalog` scans synthetic dirs in any test, check `TestTraceRunSyntheticFixture` for exclusion conventions and follow them).
- [ ] **Step 6:** Commit — `test(trace): s2 synthetic run fixture validates manifest + ss segment` (`Changelog: n/a: test-only change`).

---

### Task 3: Docs — recording procedure + frontier entry

**Files:**
- Modify: `tools/bizhawk/README.md`, `docs/TRACE_FRONTIER_LOG.md`

- [ ] **Step 1:** README section "S2 halfpipe round-trip (s2-ehz-halfpipe-roundtrip)": record a 1-player Sonic+Tails movie on the S2 World REV01 ROM — play EHZ1, collect ≥50 rings (emeralds must be < 7), touch a star post, enter the circling-stars ring, play the halfpipe to completion (or failure), return to the level, continue until control is settled, stop. Run `s2_trace_recorder.lua` with `OGGF_TRACE_RUN_ID=s2-ehz-halfpipe-roundtrip` (plus the existing `OGGF_BK2_BASENAME`/frame-count env) — run mode emits `seg1_ehz1/` + `ss/` + `seg2_ehz1/` + `run_manifest.json`. Commit under `src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/` with the bk2 committed alongside under the basename `source_bk2` records. **PROHIBITION (stated verbatim):** do NOT copy the run's `ss/` segment over `src/test/resources/traces/s2/special_stage` — the committed interior trace there is produced by `s2_ss_trace_recorder.lua` with the RunObjects PC hooks and is governed by the `Assert-SsAuxCoverage` contract; the run's `ss/` segment has a reduced aux surface (no `run_objects_end` stream) and is consumed by the run/chain path only. VERIFY-ON-FIRST-CAPTURE checklist: boundary prints must show `f_bigring=1` at entry, plausible `saved_x/y` near the star post, `rings_before ≥ 50`, `rings_after = 0` (ROM zeroes on reload — expected), `special_stage_index` plausible; any surprise = re-verify the RAM table before committing.
- [ ] **Step 2:** Frontier log entry (existing format): S2 retrofit landed — recorder run mode + synthetic fixture; recording pending; deferred follow-ups: (a) RunObjects-hook aux for run ss/ segments (revisit after first capture per spec), (b) chain test for the S2 round-trip (shared with the S3K/S1 deferral), (c) in-chain/visual SS-interior comparison (existing shared item).
- [ ] **Step 3:** Commit — `docs(trace): s2 halfpipe round-trip recording procedure + frontier entry` (`Changelog: n/a: docs only`).

---

## Verification gate (after all tasks)

1. Parse gate on the final lua; `TestS2SyntheticRunFixture`, `TestTraceRunManifest`, `TestTraceRunSyntheticFixture`, `TestS2SpecialStageTraceReplay` (must stay green on the committed interior trace — proves no accidental coupling), `S2SpecialStageRecorderContractTest` (untouched contract stays green).
2. Guard classes explicitly: `TestTraceReplayInvariantGuard`, `TestArchUnitRules`, `TestArchUnitTestRules`, `TestSingletonLifecycleGuard`, `TestBuildToolingGuard`.
3. Full `mvn test` (detached + monitor; surefire excludes `**/tests/trace/**` — the trace classes are covered by step 1). Compare against the develop baseline (44F/6E pre-existing); NEW failures block.
