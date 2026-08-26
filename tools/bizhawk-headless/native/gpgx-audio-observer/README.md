# GPGX buffered audio observer

This directory pins an observation-only patch for BizHawk 2.11's GPGX
Waterbox core. It records bounded, tokenized service, FM, PSG, reset, and Z80
RAM snapshot events without changing chip writes, emulated cycles, CPU results,
or savestated state. The observer is disabled until explicitly configured.

The supported managed integration is `REFLECTION` against the exact stock
BizHawk assemblies locked by Task 6. No patched managed DLL is built or shipped.
The native API reports ABI v4 while continuing to accept exact legacy v1,
v2, and v3 configurations. Events remain little-endian, 32 bytes each, with a fixed
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
without depending on native event coordinates. Action 11 reserves one future
service begin while an exact non-child-bearing root blocker remains active.
Every matching callback emits marker value 4 without changing the stack. The
reservation may remain on the origin blocker, or one exact configured action-4
tail may atomically emit the origin END and successor BEGIN before rebinding
its current owner. Action 12 alone consumes the reservation and emits the
target as a child of that exact current owner. Repeated callbacks coalesce by
immutable origin token, hook, opcode proof, target kind, A7, and return
identity; neither transfer nor consumption is fitted to a recorded retry
count.

ABI v4 gives action-7 M68K observation markers one exact contemporaneous A7
sample: `payload_length` is 4 and `payload[0..3]` stores the full register in
little-endian order; `payload[4..7]` stays zero. ABI v1-v3 action-7 markers and
every non-action-7 marker retain zero payload length and bytes. The sample is
taken at the reviewed instruction boundary after the managed execute callback
and before opcode execution, without mutating emulated state.

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

The reviewed action-11/action-12 tail-transfer freeze has raw core SHA-256
`f57b7a94237653879fb99af197937500a8b591f801f56284b4d2f53ca7ea6b0c`,
compressed core SHA-256
`e65315743a6a122843907a85314e380eee03fdc06bf0885b44c3dbc3bab88c6d`,
Build ID `cba4d8c88cf968a9`, compressed source-bundle SHA-256
`de73c512b2120f63f064f5e8fd59dee230f0ff50d0debbd648a9112efe18b83b`,
build-recipe SHA-256
`f419cc73426f1356c30577c04231a0cc3356bdd99bc4760dfba55abecefdf748`,
and observer identity SHA-256
`815bfde02d78fd6caa1b127ddefe7be28cc84d6fdeef5a75cecc31f186f84d86`.
These values are one identity family: consumers must not mix them with an
earlier patch, recipe, core, source archive, or capability fixture.

Task 7 validates only the generic native observer artifact and its deterministic
build. Game-specific S2/S3K hooks and real capture capability belong to Task 8.
