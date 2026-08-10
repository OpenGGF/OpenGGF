# GPGX buffered audio observer

This directory pins an observation-only patch for BizHawk 2.11's GPGX
Waterbox core. It records bounded, tokenized service, FM, PSG, reset, and Z80
RAM snapshot events without changing chip writes, emulated cycles, CPU results,
or savestated state. The observer is disabled until explicitly configured.

The supported managed integration is `REFLECTION` against the exact stock
BizHawk assemblies locked by Task 6. No patched managed DLL is built or shipped.
The native ABI is little-endian v1 with 32-byte events and a fixed capacity of
65,536 events. `artifact-lock.json` is the authority for all artifact hashes.

Build from an exact clean Task 6 source and prepared toolchain:

```bash
./build-core.sh --source /absolute/locked-source \
  --toolchain /absolute/locked-toolchain \
  --stock /absolute/BizHawk-2.11-linux-x64 \
  --output /absolute/new-build-output
```

Install beside, never over, the stock distribution:

```bash
./install-core.sh --build /absolute/new-build-output \
  --stock /absolute/BizHawk-2.11-linux-x64 \
  --output /absolute/OpenGGF/target/audio-parity/native/task7-run/install
```

Both destinations must be absent. Installation output is restricted to this
repository's ignored `target/audio-parity/native/` or harness `.scratch/`
tree. The installation includes the complete
corresponding normalized source archive, literal patch, build evidence, and
verbatim notices. Genesis Plus GX's license prohibits commercial use and
requires complete corresponding source for modified distributions; read the
installed `GPGX-LICENSE.txt` in full before redistributing.

Task 7 validates only the generic native observer artifact and its deterministic
build. Game-specific S2/S3K hooks and real capture capability belong to Task 8.
