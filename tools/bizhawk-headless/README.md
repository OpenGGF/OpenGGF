# Native headless GPGX trace harness

Records canonical Sonic 1 / 2 / 3&K physics traces by driving BizHawk 2.11's
Genesis Plus GX core directly under Mono — no EmuHawk, no X11, no Lua.

It exists because the Lua recorders in [`../bizhawk/`](../bizhawk/) need a real
EmuHawk window and a display, fail silently under `--chromeless`, and cannot be
run headlessly on this Linux box. This harness produces **byte-identical** output
to those recorders, proven by permanent ROM-backed differential gates, and is the
supported capture path on Linux for every game.

| Recorder | Native? |
|---|---|
| S1 standard, complete-run, run mode | yes — gated |
| S2 all three modes, complete-run | yes — gated |
| S3K standard (both profiles) | yes — gated |
| S3K complete-run (level / bonus / special-stage) | yes — gated |

**This harness is the preferred capture path**, and the intended direction is that
the Lua recorders are retired rather than kept at feature parity. It is roughly
1,300-2,800 fps against Lua's ~840, genuinely headless, fails loudly where Lua
under `--chromeless` swallows errors into a silent no-output run, and is the only
one of the two with a test suite.

**This harness is also the canonical fixture-publication authority.** Establish
recorder correctness before publication from named ROM/disassembly semantics,
behavioral and unit tests, and independent review. Existing fixture vectors and
Lua byte parity are optional corroboration, not publication prerequisites.

Never make a capture certify itself. Freeze the candidate's literal hashes,
lengths, versions, inventories, counts, ordering, and ranges; tests must not
derive their expected values dynamically from the same invocation. Obtain
explicit user approval for the exact bytes and deltas before installing them.

Keep the Lua recorders runnable for optional differential evidence and ad-hoc
debugging. The `event.onmemoryexecute` / `onmemorywrite` families live only there,
and a twenty-line throwaway script beats adding a `.cs` to two non-SDK csproj
files for something you intend to delete within the hour. Frozen and
unmaintained Lua is fine; it is not the fixture publisher.

## Requirements

- **Mono 6.12** with `xbuild` on `PATH`. The projects are non-SDK `.csproj` and
  the source is **C# 7.x** — newer language features will not compile.
- A **BizHawk 2.11 distribution**. `common-env.sh` defaults `BIZHAWK_HOME` to the
  repo-local `docs/BizHawk-2.11-linux-x64`, validates that it is an existing
  absolute path, and checks the required DLLs are present under `dll/`.
- **User-supplied ROMs**, passed by environment variable and SHA-1 verified:
  `S1_ROM_PATH`, `S2_ROM_PATH`, `S3K_ROM_PATH`. No ROM is committed to this repo.

## Build, run, test

```bash
./build.sh                       # xbuild both projects into bin/Release
./run.sh <args>                  # exec mono against the harness executable
./test.sh [options]              # the differential + unit suite
./test.sh --no-gates             # ~4s: everything except the ROM-backed gates
./test.sh --game s3k             # just the S3K gates
./test.sh --jobs 1               # sequential, the debugging path
```

`test.sh` skips cleanly when a ROM variable or the BizHawk distribution is
absent, and fails loudly when one is present but wrong (it re-verifies the ROM
SHA-1 rather than trusting the path).

A full run is minutes, not seconds, because the ROM-backed gates replay entire
movies — about 1.4 million frames of real Genesis emulation per run. The runner
therefore executes them **in parallel by default**, and takes selectors so you
can run a slice instead of all of it.

| Option | Meaning |
|---|---|
| `--filter <substr>` | Case-insensitive substring of the test name |
| `--game s1\|s2\|s3k` | Tests tagged with that game. **Untagged tests are excluded** |
| `--movie <substr>` | Tests replaying a matching BK2 movie, e.g. `--movie s3k-complete-sonic-tails`. **Untagged tests are excluded** |
| `--gates-only` / `--no-gates` | The ROM-backed differential gates, or everything else |
| `--jobs <n>` | Worker threads, default 8. `--jobs 1` is sequential and reproduces the pre-parallel output exactly |
| `--slowest <n>` | Slowest-test report size. Default 10 in parallel, 0 (off) at `--jobs 1` |
| `--timings <path>`, `--update-timings` | Read / rewrite the recorded timings used to order the queue |
| `--help` | The above, from the runner itself |

Selectors combine and intersect. `--game` and `--movie` select on tags rather
than on names, so a test that declares neither is excluded by them — use
`--filter` for name-based selection. Exit codes: **0** all passed, **1** a test
failed, **2** the selection matched nothing, **3** a malformed command line.

### What bounds a parallel run

Measured on a 32-core box: the full suite is **957 s sequential and 383 s at the
default `--jobs 8`**, 372 passed / 0 failed / 0 skipped either way, with an
identical per-test outcome set. `--jobs 4` measures 388 s — both are within
noise of the floor below, which is the point.

**One gate sets the floor.** The 466,334-row S3K complete-run capture is **379 s**
on its own, and no amount of concurrency makes a full run shorter than it — the
383 s above is that gate plus ~4 s of serial-tagged tests. That is why the queue is ordered **longest first**, from the recorded
`tests/test-timings.tsv` (refreshed by `--update-timings`, and falling back to
each test's static estimate when the file is missing): start that gate late and
the parallelism evaporates behind it.

**Nothing else throttles.** Each capture is a flat ~231 MB resident whatever the
movie length — that floor is the emulator core, ROM and framebuffers, not the
recording — so on a box with GBs free, memory does not bind and the scheduler
does not model it. The default of 8 sits well inside the 32 cores here and well
above the point where extra workers stop helping; raise or lower it freely with
`--jobs`.

Gate scratch lives under `.scratch/` in this directory, **not** `/tmp`, and each
gate deletes its own root in a finally block, so peak usage is what is
concurrently running (2.7 GB observed at `--jobs 8`, and 0 once the run ends)
rather than what the run has ever produced. `/tmp` is
frequently a RAM-backed tmpfs: running four gates against one filled it and
three captures failed with ENOSPC — correctly, because a full disk and a
recorder that stopped early are indistinguishable to a byte gate.

## Capturing a trace

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
./run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie /abs/path/to/movie.bk2 \
  --output /abs/path/to/output-dir \
  --trace-profile aiz_end_to_end
```

The game is auto-detected from the ROM's SHA-1; there is no `--game` flag.

| Flag | Meaning |
|---|---|
| `--mode smoke\|trace` | `smoke` is a short diagnostic run; `trace` is a full recording |
| `--rom`, `--movie`, `--output` | ROM, BK2 movie, destination. `--output` must **not** already exist |
| `--trace-profile <name>` | Per-game capture profile (see the specs in `docs/`) |
| `--gameplay-segment <n>` | S2 only — selects one segment of a multi-segment movie |
| `--run-id <id>` | Run mode: emits `run_manifest.json` and per-segment directories |
| `--effective-movie-length <n>` | Run mode only — overrides the movie-length signal |
| `--load-queue-state` | Trace mode only — records complete per-frame physical load-queue diagnostics and advertises `load_queue_state_per_frame`; off by default so legacy differential fixtures remain byte-identical |
| `--no-compress` | Trace mode only: publish the payloads uncompressed (compression is the default) |
| `--compress` | States the default explicitly; mutually exclusive with `--no-compress` |
| `--compress-threshold <bytes>` | Size floor for compressing a payload (default 1048576) |
| `--max-frames <n>`, `--bk2-frame-offset <n>` | Smoke mode only |

Output is published all-or-nothing: files are staged and only linked into
`--output` once the whole capture succeeds, so a failed run never leaves a
half-written trace behind.

The two payloads **stream** into their staging files as the capture produces
them, for every run-mode capture (S1, S2 and S3K) — nothing holds a segment,
let alone a run, in memory. Plain trace mode still buffers, because the
`level_gated_reset_aware` profile can throw an armed recording away and has to
be able to.

### Output contract

- `physics.csv` — per-frame physics rows
- `aux_state.jsonl` — per-frame auxiliary events
- `metadata.json` — capture identity, profile, offsets, versions
- `run_manifest.json` — run mode only: segment inventory and transitions

**The two payloads are gzipped at publication by default**, landing as
`physics.csv.gz` and `aux_state.jsonl.gz` once they reach `--compress-threshold`
(default 1 MiB). `metadata.json` and `run_manifest.json` are never compressed,
matching the committed fixture layout. Below the threshold a payload keeps its
plain name.

Compression happens *inside* the same all-or-nothing publication, and for a
streamed payload it happens *during* it: the bytes are written **through** a
gzip stream into a `.gz` staging file, so the uncompressed form never exists on
disk at all. A complete-run capture that used to stage 2.84 GB now stages
roughly a tenth of that.

The verify-before-destroy guarantee is preserved exactly rather than traded
away for the streaming. The plaintext is SHA-256'd and counted on its way into
the compressor; when the file closes, the finished gzip is decompressed and
compared against those values by hash **and** length before it joins the
publication set. A payload that turns out to be below the threshold is expanded
back to its plain name by that same verifying decompression, so the threshold
rule is unchanged. A buffered payload (plain trace mode) takes the original
route: gzip to a second staging file, decompress, compare against the source,
then discard the source.

Either way a verification failure publishes nothing at all — no final is
linked, not even for a payload that compressed cleanly. Each compressed payload
is reported on stdout after publication commits.

The default is on because the risk is a **commit**, not disk space. A full
complete-run `aux_state.jsonl` measures ~254 MB raw against ~12 MB gzipped
(~21x); uncompressed it is past GitHub's 100 MB per-file hard limit, so it cannot
be pushed at all. An opt-in flag fails exactly when a human installing a fixture
forgets it. Pairing the default with the 1 MiB threshold makes the harness and
the repo's commit policy (`.githooks/validate-policy.sh` — same two name
patterns, same threshold) agree by construction. A repo-level guard,
`TestTraceFixtureCompressionGuard`, enforces the same rule on the fixture tree
whatever produced the file.

Output is deterministic: the gzip carries no timestamp (`gzip -n` equivalent), so
the same capture compresses to the same bytes and a fixture commit shows no noise
diff. Streaming does not weaken that — a streamed payload's `.gz` is
byte-identical to the bulk-compressed one for the same content, pinned by a test,
because nothing is flushed through the deflater mid-stream and the container
therefore cannot depend on the caller's write pattern. Container bytes could not
affect the gates in any case; they hash decompressed content.

**`--no-compress`** opts out, for consumers that read a capture by its plain name
and never commit it. Every ROM-backed differential gate here passes it: they
capture into a temp directory and compare raw bytes.

[`../traces/compress-traces.ps1`](../traces/compress-traces.ps1) implements the
same semantics for scratch output from the **Windows Lua diagnostic route**,
whose recorders still write uncompressed output. That output is corroborative
only and must never be installed as a canonical fixture.

## Publishing a canonical fixture

1. Establish the native recorder's correctness from named ROM/disassembly
   semantics, behavioral and unit tests, and independent review. Treat existing
   fixture vectors and Lua parity as optional corroboration.
2. Capture with the unchanged reviewed native implementation into scratch.
3. Record literal SHA-256 digests, byte lengths, metadata versions, segment
   inventories, row/event counts, ordering, ranges, and the named cause of every
   byte-level delta. Stop on any unexplained delta.
4. Obtain explicit user approval for those exact candidate bytes and reported
   deltas, then install the native output byte-for-byte with no hand edits.
5. Re-run native gates and repository fixture guards, then measure and record
   replay-frontier movement.

Publication tests assert the frozen literal evidence; they never rerun capture
to derive expectations. Until the exact-byte approval in Step 4, committed
fixtures remain read-only ground truth.

## The differential gates

Each gate captures with this harness and compares against a committed fixture:
`physics.csv`, `aux_state.jsonl` and `run_manifest.json` by raw sha256 with **zero
normalization**, and `metadata.json` line-for-line with only `recording_date` and
an exactly-pinned version line permitted to differ. Fixtures are decompressed into
the test's temp directory and hashed there — the gates never modify them.

These gates provide strong regression and cross-implementation evidence, but
they do not let the recorder certify a newly proposed fixture. Treat a
pre-publication gate failure as a defect in the recorder or its proposed
contract, never as a reason to relax the comparison or silently replace a
fixture.

## Specs

`docs/` holds the byte-level porting contracts — RAM maps, format strings,
emission order, profile predicates, publication and manifest layout, per-fixture
permitted deltas:

| Game | Specs |
|---|---|
| S1 | `s1-trace-recorder-behavior.md`, `s1-complete-run-behavior.md`, `s1-run-mode-behavior.md` |
| S2 | `s2-trace-recorder-behavior.md`, `s2-run-mode-behavior.md` (§11 = complete-run) |
| S3K standard | `s3k-trace-recorder-behavior.md`, `s3k-aux-events.md`, `s3k-profiles-and-hooks.md` |
| S3K complete-run | `s3k-complete-run-behavior.md`, `s3k-completerun-profiles.md`, `s3k-run-publication.md` |

Where sources disagree, resolve the behavior against the ROM/disassembly and
update the spec, implementation, or optional Lua corroboration accordingly.

## Layout

```
src/Bk2/          BK2 movie reader (input log, header, sync settings)
src/Bootstrap/    BizHawk installation discovery, ROM identity/SHA-1 validation
src/Core/         GpgxHost — the emulator core wrapper and controller
src/Recording/    Per-game capture runners, CSV/aux/metadata writers, publisher
src/Program.cs    CLI entry point and per-game dispatch
tests/            Dependency-free console runner: TestMain (registry, test
                  metadata), TestOptions (CLI), TestRunner (scheduling and
                  buffered output), TestConsoleRouter, TestTimings,
                  TestScratch, AssertEx
.scratch/         Per-test capture scratch, created and deleted per gate
docs/             Byte-level porting specs
fixtures/         Small synthetic inputs for unit tests
```

## Contributing

Read [`CLAUDE.md`](CLAUDE.md) before changing anything here — it carries the
constraints that are expensive to discover by trial: the two `.csproj` files that
both need editing, the test registry that silently drops unregistered classes,
the `.gitignore` rule that makes new files here invisible to `git status`, and
which kinds of test cannot run beside anything else.
