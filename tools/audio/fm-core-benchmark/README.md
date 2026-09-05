# FM core benchmark

This opt-in tool compares OpenGGF's production Java Nuked-OPN2 port with the
pinned C Nuked reference and pinned C++ ymfm on one ROM-free synthetic six-FM
workload. It is a correctness/provenance harness with local timing diagnostics,
not an audio-fidelity gate or a backend-selection benchmark.

The entry point verifies every compiled upstream input, builds only below the
invoking worktree's `target/`, checks matching Java and C Nuked 64-bit FNV-1a
hashes over the ordered, signed, interleaved stereo samples, compares snapshot
replays sample by sample for all three implementations, and proves each
snapshot check is live by keying off a channel as an active negative control. The final
`result.json` records source pins, tool versions, host identity, Git state,
measurement dimensions, timings, and validation outcomes. It always marks
timings `publishable: false`; publication requires a separately reviewed run on
a quiet, documented host.

## Requirements

- JDK 21 (`java` and `javac`)
- Python 3
- a C compiler and a C++14 compiler (`CC` / `CXX` may override `cc` / `c++`)
- Git and network access only when fetching sources
- optional Linux `taskset` when `--cpu` is supplied

Fetch the exact external revisions into disposable build output:

```bash
tools/audio/fm-core-benchmark/fetch-sources.sh \
  --output "$PWD/target/fm-core-sources"
```

Run the default local diagnostic:

```bash
tools/audio/fm-core-benchmark/run.sh \
  --output "$PWD/target/fm-core-benchmark/run-1" \
  --nuked-source "$PWD/target/fm-core-sources/nuked" \
  --ymfm-source "$PWD/target/fm-core-sources/ymfm"
```

`--frames`, `--warmups`, and `--iterations` change measurement dimensions.
`--cpu N` is optional; without it there is no affinity assumption. Results,
objects, executables, fetched sources, and class files remain below `target/`.

Run the fast malformed-input and failure-control tests with:

```bash
tools/audio/fm-core-benchmark/tests/test-tool.sh
```

## Pins, licensing, and exclusions

Nuked-OPN2 is pinned at commit
`335747d78cb0abbc3b55b004e62dad9763140115`, tree
`6637a500d1da3b08cbc0cec1532ab305197b8978`, under
`LGPL-2.1-or-later`. This matches the existing production-port pin in
`tools/audio/nuked-opn2/PIN.md`. ymfm is pinned at commit
`81aec25ccbb98f4873a255f7551ac4dadac59b4a`, tree
`03f76ed27b1281357c91005e99d043eebd5119c1`, under `BSD-3-Clause`.
`nuked.lock` and `ymfm.lock` hash the licence and every source/header consumed
by these builds. Do not distribute generated linked binaries without satisfying
the applicable licence obligations.

The harness accepts no ROM or movie input. Do not commit fetched upstream
trees, ROMs, BK2s, expanded register streams, generated PCM/WAV, objects,
executables, JDKs, profiles, or benchmark logs. In particular, the old
engine-derived stream experiment omitted the fixtures' internally streamed DAC
bytes; that limitation belongs only to those fixtures. Current production
`playDac` callbacks do observe real streamed DAC bytes (since `0ae29a261`).

The order-, sign-, and channel-sensitive Java/C stream hash is a compact guard
for this synthetic stream, not a collision-free sample proof; the repository's
bit-exact port tests carry the independent sample-level proof. ymfm has a
different output model, scale, and phase, so its hash is intentionally not
compared with Nuked. The harness does not
exercise timers, IRQ/CSM integration, physical output transfer, resampling,
audio scheduling, full-game load, listening quality, or non-Linux portability.
