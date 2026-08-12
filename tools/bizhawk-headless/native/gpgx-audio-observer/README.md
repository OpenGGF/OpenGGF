# GPGX buffered audio observer

This directory pins an observation-only patch for BizHawk 2.11's GPGX
Waterbox core. It records bounded, tokenized service, FM, PSG, reset, and Z80
RAM snapshot events without changing chip writes, emulated cycles, CPU results,
or savestated state. The observer is disabled until explicitly configured.

The supported managed integration is `REFLECTION` against the exact stock
BizHawk assemblies locked by Task 6. No patched managed DLL is built or shipped.
The native API reports ABI v3 while continuing to accept exact legacy v1 and
v2 configurations. Events remain little-endian, 32 bytes each, with a fixed
capacity of 65,536. ABI v2 added profile-gated pre-arm filtering and a one-shot
publication-epoch transition: prepublication frames are fully validated and
drained without aging continuation budgets, then a drained, proof-armed READY
boundary resets only publication ages while preserving active tokens and chip
latches. ABI v3 adds two narrowly configured direct-parent-close actions.
Action 8 always atomically snapshots and closes the direct parent below the
current top, compacts the stack, and emits the adjacent `SERVICE_PROMOTE` event
that proves the surviving child's effective parent/depth change. Action 9 first
applies the same direct-work-RAM M68K return-address predicate as action 5. A
listed return emits the existing exact KEEP observation marker and leaves the
stack unchanged; an unlisted return performs the same atomic close and
promotion as action 8. Both actions require exact expected-child and
direct-parent kinds; there is no wildcard alternative. Action 10 emits the
existing retry marker bound to the direct parent beneath a typed async top,
without allocating, snapshotting, closing, promoting, or mutating the stack.
It is valid only as the sole paired override for a source-identical ordinary
begin; the override wins exactly when its declared direct parent is present,
and otherwise the ordinary begin remains authoritative. Begin ancestry remains
immutable; managed and stored diagnostics retain the bounded transition
history, while producer-neutral semantic records retain the effective ancestry
without depending on native event coordinates.

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

The capability fixture avoids a self-referential executable hash without
weakening it. Its S2 profile authenticates a raw-byte template SHA-256 after
requiring exactly one lowercase-hex
`task8_harness_executable_sha256` field and replacing only that 64-byte value
with ASCII zeroes. Every other byte remains identity-sensitive. The field is
then independently required to equal the SHA-256 of the production
`BizHawk.Headless.Gpgx` executable. Java metadata continues to pin the complete,
unnormalized capability-file SHA-256.

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
