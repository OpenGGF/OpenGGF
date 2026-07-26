---
name: s1-trace-replay
description: Use when running or creating Sonic 1 trace replay tests that compare engine physics against recorded reference data.
---

# S1 Trace Replay

Record a Sonic 1 BizHawk trace, copy it to the test resources, run the trace replay tests, and interpret the divergence results.

## Inputs

$ARGUMENTS: Optional zone name or action. Examples:
- `mz1` — record and test MZ1 only
- `ghz1` — record and test GHZ1 only
- `all` or empty — record and test both GHZ1 and MZ1
- `test-only` — skip recording, just run the trace tests on existing data
- `interpret` — skip recording/testing, just interpret the latest report

## Prerequisites

- Sonic 1 REV01 ROM discovered at the project root; export its path as `S1_ROM_PATH`
  (SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`).
- BK2 movies live beside their fixtures under
  `src/test/resources/traces/s1/<zone>/`.
- For the native recorder: Mono 6.12 + xbuild, and a BizHawk distribution at
  `docs/BizHawk-2.11-linux-x64` (or `BIZHAWK_HOME`).

## Step 1: Record the trace

**S1 recording is migrated to the native harness** (`tools/bizhawk-headless/`). It runs
under Mono with no display, so prefer it over the Lua recorder:

```bash
tools/bizhawk-headless/run.sh \
    --rom "$S1_ROM_PATH" \
    --movie src/test/resources/traces/s1/<zone>/<movie>.bk2 \
    --output /tmp/regen-<zone> \
    --mode trace \
    --trace-profile <profile>
```

`--output` must not already exist. Use `--run-id <id>` for complete-run / run-mode
captures instead of `--trace-profile`. Validate changes with
`tools/bizhawk-headless/test.sh --filter S1` — the differential gates compare against the
committed fixtures byte-for-byte.

The Lua recorder (`tools/bizhawk/s1_trace_recorder.lua`) remains the behavioural authority
the native port is validated against, and still works on Linux via
`tools/bizhawk/run_bizhawk_lua.sh <lua> <bk2> <rom>` with `DISPLAY=:0`. Reach for it when
cross-checking the native output, not as the default path.

### Verify recording succeeded

Check the written `metadata.json`:

- `recording_date` matches today
- `zone` matches the expected zone (`ghz`, `mz`, …)
- `trace_frame_count` is plausible (GHZ1 ~3905, MZ1 ~7936)

### Important notes

- BizHawk does not print Lua output to stdout in chromeless mode. Judge Lua runs by output
  files and timestamps, never console output. The native harness does report errors on
  stderr and exits non-zero.
- Lua output lands in `tools/bizhawk/trace_output/` (relative to the *script's* folder, not
  BizHawk's), and each run OVERWRITES the previous one — record and copy one zone at a
  time. The native harness writes to whatever `--output` you pass and refuses to clobber.

## Step 2: Copy Trace to Test Resources

After each recording, copy the three output files into the fixture directory (paths are
repo-relative; `$SRC` is your `--output` dir for a native capture, or
`tools/bizhawk/trace_output` for a Lua one). The `.bk2` is unchanged:

```bash
SRC=/tmp/regen-<zone>
DST=src/test/resources/traces/s1/<zone>_fullrun
cp "$SRC"/metadata.json "$DST/"
# Payloads are committed GZIPPED. A native capture already produced .gz for
# anything over 1 MiB; gzip whatever is still plain before copying.
for f in physics.csv aux_state.jsonl; do
    [ -f "$SRC/$f.gz" ] && cp "$SRC/$f.gz" "$DST/"
    [ -f "$SRC/$f" ] && gzip -9 -n -c "$SRC/$f" > "$DST/$f.gz"
done
```

`TestTraceFixtureCompressionGuard` fails the build if an uncompressed `physics*.csv` or
`aux_state*.jsonl` lands under `src/test/resources/traces/`: uncompressed these run to
hundreds of MB and exceed GitHub's per-file limit. The pre-existing uncompressed fixtures
are grandfathered in `src/test/resources/trace-guard/uncompressed-payload-baseline.txt`.

Fixtures are read-only ground truth: only overwrite one deliberately, to gain diagnostic
data, and commit the regen separately from any recorder change.

## Step 3: Run Trace Replay Tests

```bash
mvn "-Dtest=*TraceReplay" test
```

Expected output pattern:
- `MSE:TESTS total=15 passed=N failed=M errors=0 skipped=0`
- GHZ1 (`TestS1Ghz1TraceReplay`) should PASS
- MZ1 (`TestS1Mz1TraceReplay`): current per-trace error/warning counts live in `docs/status/trace-frontier-log.md`, not here — baselines drift as fixes land, so check the log or regenerate rather than trusting a number quoted in this skill.

### Test class mapping

| Test class | Zone | Trace directory |
|---|---|---|
| `TestS1Ghz1TraceReplay` | GHZ Act 1 | `src/test/resources/traces/s1/ghz1_fullrun/` |
| `TestS1Mz1TraceReplay` | MZ Act 1 | `src/test/resources/traces/s1/mz1_fullrun/` |

## Step 4: Interpret Results

### Divergence report location

Reports are written to `target/trace-reports/`:
- `s1_ghz1_report.json` — GHZ1 divergence report
- `s1_mz1_report.json` — MZ1 divergence report
- `s1_mz1_context.txt` — context window around first MZ1 error (only if errors exist)

### Reading the report JSON

```bash
cat target/trace-reports/s1_mz1_report.json | python -m json.tool | head -30
```

Key fields in each divergence group:
- `field` — which physics field diverged (x, y, x_speed, y_speed, g_speed, angle, air, rolling, ground_mode)
- `severity` — `ERROR` or `WARNING` (warnings are within tolerance)
- `start_frame` / `end_frame` — frame range of the divergence
- `cascading` — true if this divergence follows an earlier error (cascade effect)

### Interpreting with auxiliary trace data

The `aux_state.jsonl` file contains rich event data for debugging divergences. Key event types:

**`slot_dump`** — Full snapshot of all occupied SST slots when any object appears:
```bash
grep "slot_dump" src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | head -3
```
Use this to compare ROM slot allocation vs engine allocation at specific frames.

**`routine_change`** — Player routine transitions with full Sonic state:
```bash
grep "routine_change" src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl
```
S1 routines: 0=init, 2=control, **4=hurt**, 6=death, 8=reset. NOT the same as S2.

**`object_appeared` / `object_removed`** — Object lifecycle with slot number:
```bash
grep '"slot":75' src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | grep "appeared\|removed"
```

Object positions in trace rows and aux events are ROM centre coordinates. Do not compare them directly to debug HUD `Pos:` values or `getX()` / `getY()` top-left bounds; use `getCentreX()` / `getCentreY()` when tracing engine state.

**Object/player participation mismatch** — If the first error involves object contact, standing state, sidekick state, or object removal, classify it with the standard object contracts when present: `ObjectControlState` for controlled-player gates, `ObjectPlayerQuery` / `ObjectPlayerParticipationPolicy` for which player(s) the object should inspect, and `ObjectLifetimeOps` for delete/despawn/remembered-object behavior. Prefer canonical profile compatibility wrappers over object-local fixes when the issue is generic, but prove wrapper equivalence before changing behavior.

**`object_near`** — Per-frame proximity log of objects within 160px of Sonic:
```bash
grep '"frame":3193' src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | grep "object_near"
```

### Common divergence patterns

1. **y_speed sign flip** (e.g., expected=-04C0, actual=+04C0): Enemy bounce missed. The ROM bounced off a badnik but the engine didn't. Check `slot_dump` and `object_near` for the badnik's slot — slot differences cause timing gate differences in objects like Batbrain.

2. **Position drift after hurt**: Ring loss events (object type 0x37 = `RingLoss` scattered rings) confirm damage. Check `routine_change` events for the 2→4 transition and what object Sonic was standing on.

3. **Cascading errors**: Once one divergence occurs, subsequent errors often cascade. Focus on the FIRST error — fixing it may resolve many downstream issues.

4. **Warning-only 1px Y differences**: Often terrain collision rounding. Usually not actionable unless they precede errors.

### CSV column reference (v2.2, csv_version=4)

Recorder schemas have advanced since this table was written (the S1 complete-run recorder is at v3.x). Treat the table below as a starting reference only — the recorder script's CSV header function and the trace's own `metadata.json` are the authoritative source for current column layout.

| Column | Index | Description |
|---|---|---|
| frame | 0 | Trace frame number (hex) |
| input | 1 | Joypad bitmask |
| x | 2 | Sonic centre X |
| y | 3 | Sonic centre Y |
| x_speed | 4 | X velocity (signed) |
| y_speed | 5 | Y velocity (signed) |
| g_speed | 6 | Ground speed (signed) |
| angle | 7 | Terrain angle |
| air | 8 | Airborne flag (0/1) |
| rolling | 9 | Rolling flag (0/1) |
| ground_mode | 10 | Ground mode (0-3) |
| x_sub | 11 | X subpixel |
| y_sub | 12 | Y subpixel |
| routine | 13 | obRoutine raw byte |
| camera_x | 14 | Camera X pixel |
| camera_y | 15 | Camera Y pixel |
| rings | 16 | Ring count (binary word) |
| status_byte | 17 | Status flags byte |
| v_framecount | 18 | ROM frame counter (currently reads 0 — address needs verification) |
| stand_on_obj | 19 | SST slot Sonic is standing on (0=none) |

### S1 object ID quick reference (common in MZ)

| ID | Name |
|---|---|
| 0x25 | Ring |
| 0x2F | MZ Large Grassy Platform |
| 0x30 | MZ Glass Block |
| 0x33 | Push Block |
| 0x36 | Spikes |
| 0x37 | RingLoss (scattered rings after damage) |
| 0x46 | MZ Brick |
| 0x54 | Lava Tag |
| 0x55 | Batbrain |
| 0x78 | Caterkiller |
| 0x79 | Lamppost |

## Workflow Summary

For a full re-record and test cycle:

1. Record zone (BizHawk headless, ~10-30 seconds)
2. Verify metadata.json has correct date/zone/csv_version
3. Copy 3 files to test resources
4. Repeat for second zone if doing both
5. Run `mvn test -Dtest="*TraceReplay"`
6. Compare error count against the current baseline in `docs/status/trace-frontier-log.md` (GHZ1 should stay 0)
7. If errors changed: read report JSON and cross-reference aux events at first error frame
