# Nuked-OPN2 adapter parity harness

`adapter_parity_harness.c` drives the pinned upstream `ym3438.c` (fetched with
`../fetch-source.sh`, never vendored) with exactly the bus pacing and frame
summation that `com.openggf.audio.synth.Ym2612Chip` applies, so the Java
facade can be pinned sample-for-sample against the C build without a ROM.

`generate-adapter-scripts.py` writes the register scripts that
`TestYm2612ChipNukedParity` runs; `regenerate-expectations.sh` rebuilds the
harness, runs every script through it and rewrites `expected.txt` next to the
scripts (`src/test/resources/audio/nuked-opn2/adapter/`). Regenerate only when
the scripts or the adapter's documented pacing change, and say so in the
commit; a changed checksum with unchanged scripts means the facade or the
port drifted from the C build.

```bash
tools/audio/nuked-opn2/fetch-source.sh --output /abs/path/to/nuked-src
tools/audio/nuked-opn2/harness/regenerate-expectations.sh /abs/path/to/nuked-src
```
