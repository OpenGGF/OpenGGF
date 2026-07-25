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

The Lua recorders remain the **behavioural authority**: when the two disagree,
the Lua is right by definition and the port is fixed. They are also still needed
for the hook-driven aux event families this harness deliberately defers, and on
platforms where the harness does not run.

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
| `--max-frames <n>`, `--bk2-frame-offset <n>` | Smoke mode only |

Output is published all-or-nothing: files are staged and only linked into
`--output` once the whole capture succeeds, so a failed run never leaves a
half-written trace behind.

### Output contract

- `physics.csv` — per-frame physics rows
- `aux_state.jsonl` — per-frame auxiliary events
- `metadata.json` — capture identity, profile, offsets, versions
- `run_manifest.json` — run mode only: segment inventory and transitions

**These are written uncompressed.** The committed fixtures under
`src/test/resources/traces/` store `physics.csv.gz` and `aux_state.jsonl.gz`, so
installing a fresh capture as a fixture currently requires a separate gzip step —
[`../traces/compress-traces.ps1`](../traces/compress-traces.ps1) does this for the
Lua output directory (compressing payloads above a 1 MiB threshold and verifying
by decompress-and-hash before deleting the original). Folding that into the
harness's publisher is tracked work; until then, compress deliberately and verify
the round trip.

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
