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

Two things keep the Lua recorders around, and neither is a parity obligation:

1. **They are the regeneration oracle.** When a recorder must change, the sequence
   is: fix the Lua, recapture with it, then make this harness independently
   reproduce those bytes. That last step is the check. Capture a replacement
   fixture *with this harness* and the gate compares the port against its own
   output, which proves nothing. Keep the Lua runnable — frozen and unmaintained
   is fine — so that check survives.
2. **They are the substrate for ad-hoc debugging.** The `event.onmemoryexecute` /
   `onmemorywrite` families live only there, and a twenty-line throwaway script
   beats adding a `.cs` to two non-SDK csproj files for something you intend to
   delete within the hour.

So: when the two disagree today, the Lua is right by definition and the port is
fixed. That is a statement about which artifact is the oracle, not a commitment
to maintaining two recorders forever.

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
./test.sh [--filter <substr>]    # the differential + unit suite
```

`test.sh` skips cleanly when a ROM variable or the BizHawk distribution is
absent, and fails loudly when one is present but wrong (it re-verifies the ROM
SHA-1 rather than trusting the path). `--filter` narrows to matching test names,
which matters because a full run takes several minutes — the ROM-backed
differential gates replay entire movies.

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
same semantics for a directory and remains the path for the **Windows Lua route**,
whose recorders still write uncompressed output.

## The differential gates

Each gate captures with this harness and compares against a committed fixture:
`physics.csv`, `aux_state.jsonl` and `run_manifest.json` by raw sha256 with **zero
normalization**, and `metadata.json` line-for-line with only `recording_date` and
an exactly-pinned version line permitted to differ. Fixtures are decompressed into
the test's temp directory and hashed there — the gates never modify them.

That is the whole value of the harness: it is not "a recorder that looks right",
it is a recorder proven to reproduce the authority's bytes. Treat a gate failure
as a defect in this code, never as a reason to relax the comparison or to
regenerate a fixture.

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

Where a spec and the Lua disagree, **the Lua wins** and the spec is corrected.

## Layout

```
src/Bk2/          BK2 movie reader (input log, header, sync settings)
src/Bootstrap/    BizHawk installation discovery, ROM identity/SHA-1 validation
src/Core/         GpgxHost — the emulator core wrapper and controller
src/Recording/    Per-game capture runners, CSV/aux/metadata writers, publisher
src/Program.cs    CLI entry point and per-game dispatch
tests/            Dependency-free console runner (TestMain registry + AssertEx)
docs/             Byte-level porting specs
fixtures/         Small synthetic inputs for unit tests
```

## Contributing

Read [`CLAUDE.md`](CLAUDE.md) before changing anything here — it carries the
constraints that are expensive to discover by trial: the two `.csproj` files that
both need editing, the test registry that silently drops unregistered classes, and
the `.gitignore` rule that makes new files here invisible to `git status`.
