# GPGX buffered audio observer

This directory pins an observation-only patch for BizHawk 2.11's GPGX
Waterbox core. It records bounded, tokenized service, FM, PSG, reset, and Z80
RAM snapshot events without changing chip writes, emulated cycles, CPU results,
or savestated state. The observer is disabled until explicitly configured.

The supported managed integration is `REFLECTION` against the exact stock
BizHawk assemblies locked by Task 6. No patched managed DLL is built or shipped.
The native API reports ABI v2 while continuing to accept exact legacy v1
configurations. Events remain little-endian, 32 bytes each, with a fixed
capacity of 65,536. ABI v2 adds profile-gated pre-arm filtering and a one-shot
publication-epoch transition: prepublication frames are fully validated and
drained without aging continuation budgets, then a drained, proof-armed READY
boundary resets only publication ages while preserving active tokens and chip
latches.

`gpgx_audio_trace_first_fault` returns a read-only, packed 16-byte snapshot of
the first runtime fault in the configured session: stable reason, source CPU,
instruction-start PC, active kind/depth, and continuation count/limit. It does
not append an event or mutate emulation/observer state. Frame abort preserves
the diagnostic; disable ends the session and clears it. Reason values are:

| Value | Reason |
|---:|---|
| 0 | none |
| 1 | token allocation |
| 2 | snapshot capture |
| 3 | service stack |
| 4 | service transition |
| 5 | hook/opcode proof |
| 6 | proof-arm transition |
| 7 | chip-write ownership |
| 8 | event capacity |
| 9 | continuation limit |

`artifact-lock.json` is the authority for all artifact hashes.

From a fresh checkout at the repository root, create durable inputs and build
outputs beneath the ignored `target/audio-parity/native/` tree. The package
directory is caller-supplied and must contain the filenames and bytes
listed by `prepare-toolchain.sh`; the script checks every SHA-256 against
`toolchain-lock.json` before publishing the toolchain.

```bash
observer=$PWD/tools/bizhawk-headless/native/gpgx-audio-observer
native=$PWD/target/audio-parity/native/task7-reproduction
packages=/absolute/path/to/locked-package-input
stock=/absolute/path/to/BizHawk-2.11-linux-x64
mkdir -p "$native"

"$observer/fetch-source.sh" --output "$native/source"
"$observer/prepare-toolchain.sh" \
  --source "$native/source" \
  --packages "$packages" \
  --output "$native/toolchain"
"$observer/build-core.sh" \
  --source "$native/source" \
  --toolchain "$native/toolchain" \
  --stock "$stock" \
  --output "$native/build"
```

Install beside, never over, the stock distribution:

```bash
"$observer/install-core.sh" \
  --build "$native/build" \
  --stock "$stock" \
  --output "$native/install"
```

All four output destinations must be absent. Installation output is restricted to this
repository's ignored `target/audio-parity/native/` or harness `.scratch/`
tree. The installation includes the complete
corresponding normalized source archive, literal patch, build evidence, and
verbatim notices. Genesis Plus GX's license prohibits commercial use and
requires complete corresponding source for modified distributions; read the
installed `GPGX-LICENSE.txt` in full before redistributing.

Task 7 validates only the generic native observer artifact and its deterministic
build. Game-specific S2/S3K hooks and real capture capability belong to Task 8.
