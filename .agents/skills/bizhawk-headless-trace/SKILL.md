---
name: bizhawk-headless-trace
description: Use when recording or publishing a Sonic 1, Sonic 2, or Sonic 3&K trace from a BizHawk BK2 movie with the native headless harness, including complete-game runs, special-stage detours, and compressed test fixtures.
---

# BizHawk Headless Trace

Use the native GPGX harness as the canonical recording path for BK2-backed
traces. The harness runs BizHawk 2.11 through Mono without a display and
publishes an all-or-nothing output directory.

## Before capture

- Preserve the current branch and unrelated working-tree changes.
- Discover the ROM at the repository root; do not assume a filename. Verify
  the documented game/hash when the capture identity matters:
  S1 `AFE05EEE`, S2 REV01 `7B905383`, S3&K `63522553`.
- Use BizHawk 2.11 Linux x64 at `docs/BizHawk-2.11-linux-x64` or set
  `BIZHAWK_HOME` to an absolute existing directory. Do not substitute 2.11.1.
- Keep the BK2's truthful filename. The published `source_bk2` metadata must
  match it exactly.
- Choose an output path that does not exist; the CLI refuses to overwrite it.

## Capture

The game is selected from the ROM. Always use `--mode trace`:

```bash
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S1_ROM_PATH" \
  --movie "$MOVIE" \
  --output "$OUTPUT"
```

For one level, use the default S1 profile or the game's documented standard
profile. For a multi-stage complete movie, select one of these mutually
exclusive modes:

- `--trace-profile complete_run` records level segments without forcing a run
  manifest unless the movie contains a stage detour.
- `--run-id <truthful-run-id>` records a named run, emits `run_manifest.json`,
  and preserves special-stage/bonus segments and transition metadata.

For S1, a complete-emeralds movie is a distinct run from the ordinary complete
run. Give it its own run ID and destination; never replace the existing run.
For S2/S3&K, follow the game-specific run-mode options in
`tools/bizhawk/README.md` and the corresponding behavior document under
`tools/bizhawk-headless/docs/`.

Do not set diagnostic recorder environment variables unless the task
specifically requires them. Do not inject movie length or gameplay state to
make a capture finish. The capture must model the ROM's actual movie.

## Publish under test resources

For a named run, place the BK2, manifest, and per-segment files together:

```text
src/test/resources/traces/<game>/runs/<run-id>/
  <truthful-source-name>.bk2
  run_manifest.json
  <segment>/metadata.json
  <segment>/physics.csv.gz or physics.csv
  <segment>/aux_state.jsonl.gz or aux_state.jsonl
```

Copy `metadata.json`, `run_manifest.json`, the BK2, and every segment directory
from the harness output. Native publication compresses payloads above its
threshold by default; if a payload remains plain, gzip it with deterministic
metadata before committing:

```bash
gzip -9 -n -c physics.csv > physics.csv.gz
gzip -9 -n -c aux_state.jsonl > aux_state.jsonl.gz
```

Never commit an uncompressed `physics*.csv` or `aux_state*.jsonl` under
`src/test/resources/traces/` unless the path is explicitly grandfathered by
`src/test/resources/trace-guard/uncompressed-payload-baseline.txt`.

## Verify

Check the output before publication:

```bash
find "$OUTPUT" -type f | sort
cat "$OUTPUT/run_manifest.json"
```

Confirm that every manifest segment exists, every metadata `source_bk2` names
the committed BK2, the ROM checksum is correct, segment counts and offsets are
plausible, and no partial output or unexpected segment was published. Compare
the new run ID and manifest with the existing run inventory; distinct movies
must remain distinct fixtures.

Run the native harness tests relevant to the capture, then the applicable Java
trace replay tests. Record any new first-error frame/field and update the trace
frontier log when a frontier moves. Use the behavior documents and
`tools/bizhawk/README.md` as the detailed protocol references; this skill is
the task-discovery and publication checklist, not a second recorder spec.
