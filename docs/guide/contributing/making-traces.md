# Making your own traces

Start with a short movie of one level. You need a verified game ROM, a BizHawk
movie and a Linux x64 machine for capture. You do not need to write Java tests
or publish a fixture to make your first trace.

```mermaid
flowchart LR
    A[Set up once] --> B[Record a movie]
    B --> C[Capture and validate]
    C --> D[Trace files ready to inspect]
```

## 1. Set up once

You need Python 3, Mono 6.12 with `xbuild`, `monodis` and the pinned Roslyn
compiler. Follow the [harness requirements](../../../tools/tracechaser/bizhawk-headless/README.md#requirements)
if these are not installed. Java and Maven are only needed later for engine replay.

From your OpenGGF checkout, initialize TraceChaser and install the supported
BizHawk 2.11 release:

```bash
git submodule update --init --recursive tools/tracechaser &&
tools/tracechaser-bootstrap.sh --check &&
tools/tracechaser/bizhawk/fetch_bizhawk_2_11_linux.sh
```

If you already have BizHawk installed, use the existing-installation instructions
below instead of the fetch command. Keep the same terminal open for the commands
on this page.

<details>
<summary>Existing BizHawk installation and setup details</summary>

Native capture runs on Linux x64 under Mono, with no display.

| Prerequisite | Source |
|---|---|
| Python 3 and Mono's `monodis` on `PATH` | [Install BizHawk 2.11](../../../tools/tracechaser/docs/install-bizhawk-2.11.md) |
| Mono 6.12 with `xbuild` and the pinned Roslyn `csc.exe` at `/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe` | [Harness requirements](../../../tools/tracechaser/bizhawk-headless/README.md#requirements) |
| Official BizHawk 2.11 Linux x64, preflighted | `fetch_bizhawk_2_11_linux.sh` or `preflight_bizhawk_2_11.sh` |
| Linux `renameat2(RENAME_NOREPLACE)` | Output is published all-or-nothing with it |
| Java 25 and Maven | Only for the replay step ([Dev Setup](dev-setup.md)) |

From the OpenGGF checkout:

```bash
git submodule update --init --recursive tools/tracechaser
tools/tracechaser-bootstrap.sh --check
tools/tracechaser/bizhawk/fetch_bizhawk_2_11_linux.sh
```

The fetch script downloads the locked official archive into the ignored
`tools/tracechaser/.dependencies/BizHawk-2.11-linux-x64`. To use an existing
installation instead, preflight it and export its path:

```bash
tools/tracechaser/bizhawk/preflight_bizhawk_2_11.sh \
  --bizhawk-home /absolute/path/BizHawk-2.11-linux-x64
export BIZHAWK_HOME=/absolute/path/BizHawk-2.11-linux-x64
```

Keep inputs and outputs in a directory outside both repositories. Avoid `/tmp`
for long runs because it may be a small memory filesystem. See
[Scratch and security](../../../tools/tracechaser/docs/scratch-and-security.md).

</details>

## 2. Record a short movie

Open your [verified ROM](dev-setup.md#rom-files) in BizHawk 2.11's GUI,
EmuHawk. Use the Genesis Plus GX core and record a BK2 movie starting from
power-on. Play into the level you want, stop recording after the section you
care about, and save the `.bk2` outside the repository.

Use the default 3-button controllers and auto region. Movies starting from a
savestate or save RAM are not accepted. Keep the original movie filename.

<details>
<summary>Exact movie settings and how to check them</summary>

Record the movie yourself in EmuHawk (the BizHawk GUI). The native harness
reads the BK2's `Header.txt`, `SyncSettings.json` and `Input Log.txt`, and
rejects a movie that breaks the middle four rows below:

| Requirement | Detail |
|---|---|
| BizHawk 2.11 | The capture runtime must be exactly 2.11; preflight rejects 2.11.1 and later. Record the movie with 2.11 too. |
| Genesis Plus GX core | `Header.txt` has `Core Genplus-gx` and `Platform GEN`. |
| Power-on start | `StartsFromSavestate` and `StartsFromSaveRam` are absent or `False`. Use "Record Movie" from power-on, not from a savestate. |
| Pinned controller and region settings | `SyncSettings.json` has `UseSixButton: false`, `ControlTypeLeft: 1`, `ControlTypeRight: 1`, `Region: 0` and `LoadBIOS: false`, among other pinned fields. |
| Both pads in the log | `Input Log.txt` has the `System`, `P1` and `P2` groups, each with its full button set. |
| The verified ROM | Record and capture with the exact dump listed in the root [AGENTS.md](../../../AGENTS.md#rom-and-reference-setup) table. The capture identifies the game from the ROM SHA-1. |

Play from the title screen into the level you want and stop the movie after
the section you care about. Keep the BK2 file name. Complete-run metadata and
run manifests record it as `source_bk2`, and replay looks up a shared movie by
that name.

Check a recorded movie before capturing:

```bash
unzip -p /absolute/path/trace-research/inputs/ghz1-attempt.bk2 Header.txt
unzip -p /absolute/path/trace-research/inputs/ghz1-attempt.bk2 SyncSettings.json
```

The minimal `.bk2` written by `InputLogAuthorTool` has no `SyncSettings.json`,
so the native harness rejects it. Use EmuHawk recordings for traces.

</details>

## 3. Capture and validate

Run this from your OpenGGF checkout. Change the three paths at the top.
The example captures the first gameplay segment for Sonic 1, Sonic 2 or Sonic
3 & Knuckles, selected from the ROM. For another segment or a whole run, see
"Other routes and complete runs" below.

```bash
ROM=/absolute/path/to/your-game.gen
MOVIE=/absolute/path/to/your-recording.bk2
OUT=/absolute/path/to/trace-research/first-capture

OPENGGF="$PWD"
TRACECHASER="$OPENGGF/tools/tracechaser"
mkdir -p "$(dirname "$OUT")"

"$TRACECHASER/bizhawk-headless/run.sh" \
  --tracechaser-root "$TRACECHASER" \
  --input-repository-root "$OPENGGF" \
  --fixture-root "$OPENGGF/src/test/resources/traces" \
  --mode trace --rom "$ROM" --movie "$MOVIE" --output "$OUT" \
  --compress-threshold 0 &&
python3 "$TRACECHASER/traces/validate_trace_v5.py" \
  --require-frame-keyed-auxiliary "$OUT"
```

Choose an output directory that does not exist yet, outside both repositories.
The recorder builds itself on the first run. It compresses the payloads for you,
and the validator runs only if capture succeeds.

When both commands succeed, your trace is in `$OUT`. Check its starting level
and frame count:

```bash
python3 -m json.tool "$OUT/metadata.json"
```

Look for the expected `game`, `zone`, `act` and `trace_frame_count`.
You now have a trace candidate. A successful capture records what the ROM did;
it does not mean OpenGGF can replay that route accurately.

## If something goes wrong

| Symptom | Next step |
| --- | --- |
| Missing compiler, Mono or BizHawk | Open the setup details above and check the harness requirements. |
| Movie header or sync settings rejected | Open the movie settings above. Record from power-on with the supported core and settings. |
| Output directory already exists | Pick a fresh name, such as `second-capture`. |
| Wrong level or too few frames | Check the movie and choose the appropriate profile below. |
| Capture or validation fails | Keep the command output. Fix the reported cause before treating the trace as usable. |

## When you need more

The remaining steps are for choosing another route, investigating the output,
or adding a fixture to OpenGGF. You can stop here after a first capture.

<details>
<summary>Other routes and complete runs</summary>

The ROM selects the game. There is no game flag.

| Game | One level | Whole movie |
|---|---|---|
| S1 | Omit `--trace-profile` | `--trace-profile complete_run`, or `--run-id NAME` for a named run with a manifest |
| S2 | `--trace-profile gameplay_unlock` (default). For a later gameplay segment of a multi-segment movie, `--trace-profile level_gated_reset_aware --gameplay-segment N` | `--run-id NAME` |
| S3K | `gameplay_unlock` (default), `aiz_end_to_end` for the AIZ intro through the HCZ handoff, or `level_gated_reset_aware` | `--trace-profile complete_run`, or `--run-id NAME` |

`--run-id` cannot be combined with `--trace-profile` or `--gameplay-segment`.
S1 rejects any profile other than `complete_run`. S3K writes an unrecognised
profile string into metadata but records it with `gameplay_unlock` rules, so
spell the profile exactly. Per-game details are in
[capture-s1](../../../tools/tracechaser/docs/capture-s1.md),
[capture-s2](../../../tools/tracechaser/docs/capture-s2.md) and
[capture-s3k](../../../tools/tracechaser/docs/capture-s3k.md).
Add `--load-queue-state` only when you need per-frame load-queue diagnostics.

</details>

<details>
<summary>Inspect the files and save an inventory</summary>

Set `WORK` to an absolute research directory outside the repositories, then
create its evidence directory before using the inventory command:

```bash
WORK=/absolute/path/to/trace-research
mkdir -p "$WORK/evidence"
```

Validate the whole candidate root. The validator only reads.

```bash
python3 "$TRACECHASER/traces/validate_trace_v5.py" \
  --require-frame-keyed-auxiliary "$OUT"
```

Then look at what you captured:

```bash
find "$OUT" -type f | sort
python3 -m json.tool "$OUT/metadata.json"
zcat -f "$OUT"/physics.csv* | head -3
python3 "$TRACECHASER/traces/trace_fixture_inventory.py" generate "$OUT" \
  --output "$WORK/evidence/s1-ghz1-attempt-1-inventory.json"
```

| Output | Check |
|---|---|
| `metadata.json` | `trace_schema` is `5`, `recorder` is `native-bizhawk-headless`, `recorder_version` is `3.0`. `game`, `zone` and `act` match what you played. `trace_frame_count` fits the route length. `start_x` and `start_y` look like the level start. |
| `physics.csv` or `physics.csv.gz` | Ordinary level rows have 42 columns, or 43 with trailing `life_count`. Special stages have 14 (S1), 48 (S2) or 20 (S3K). |
| `aux_state.jsonl` or `.gz` | Per-frame events. Comparison data only; replay never loads gameplay state from it. |
| `hardware_timing.jsonl` or `.gz` | Present when the recorder emits supported queue timing events. Scheduling outcomes only; see the [timing contract](../../architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md). |
| `run_manifest.json` and segment directories | Named runs, and complete runs with a detour. Segment order matches the movie. |
| Inventory JSON | Stored and logical hashes for every file. Keep it outside the candidate. |

The [trace v5 reference](../../../tools/tracechaser/docs/trace-v5.md) defines
every field.

The quickstart uses `--compress-threshold 0`, so no manual gzip step is needed.
For captures made without it, follow the
[compression rules](trace-replay.md#compressing-trace-payloads).

</details>

<details>
<summary>Compare your trace against the engine</summary>

Set `WORK` as described in the inventory section above.

Replay needs a test class that names the trace directory. Follow
[Adding a new trace test](trace-replay.md#adding-a-new-trace-test) to write
one, for example a class returning
`Path.of("src/test/resources/traces/s1/my_route")`.

You can replay the candidate without installing it.
`-Dopenggf.trace.fixtureRoot` maps a path under `src/test/resources/traces` to
the same relative path under another root. Copy the compressed candidate and its
BK2 into that layout, then run your class:

```bash
mkdir -p "$WORK/replay-root/s1"
cp -a "$OUT" "$WORK/replay-root/s1/my_route"
cp "$WORK/inputs/ghz1-attempt.bk2" "$WORK/replay-root/s1/my_route/"

cd "$OPENGGF"
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.trace.fixtureRoot="$WORK/replay-root" \
  -Dtest=TestS1MyRouteTraceReplay test
```

`TestS1MyRouteTraceReplay` stands for your own class. The test skips, rather
than fails, when the directory, `metadata.json`, the physics payload or the BK2
is missing, and `@RequiresRom` tests skip without the ROM. Check the surefire
output for a skip before you read the result as a pass. Reports land in
`target/trace-reports/<profile>/<logical-key>-<lane>-<owner-hash>.json`.

Keep these limits in mind:

- A successful capture means the movie ran under the recorder. It is not a
  passing engine test.
- A replay failure is normal for new routes. It reports the first frame and
  field where OpenGGF differs from the ROM. Fix the engine from the
  disassembly, never by editing the trace.
- A green replay covers only that movie's route, character and start. It does
  not certify the level; see the
  [zone and act test standard](level-test-standard.md).
- Comparing the candidate against itself proves nothing. Determinism needs a
  second capture and `compare_trace_v5_candidates.py --mode v5-literal
  --fail-on-difference`.

[Reading divergences](trace-replay.md#reading-divergences) explains the report.

</details>

<details>
<summary>Propose a trace for the repository</summary>

Installing a trace into `src/test/resources/traces` needs maintainer approval
of the exact candidate bytes. Follow
[Trace v5 capture and publication](trace-v5-publication.md) and
[Validate, compare, and publish](../../../tools/tracechaser/docs/validate-compare-publish.md).

Before you ask, have this ready:

- [ ] The BK2 with its original file name, and the ROM name and SHA-1 you used
- [ ] The exact `run.sh` command line and the TraceChaser commit
      (`git -C tools/tracechaser rev-parse HEAD`)
- [ ] A clean `validate_trace_v5.py` result
- [ ] The inventory JSON, written outside the candidate
- [ ] Payloads stored only as deterministic gzip
- [ ] The replay result: first divergence frame and field, or a pass, with any
      skips

Never commit the ROM, savestates or the BizHawk distribution.

</details>
