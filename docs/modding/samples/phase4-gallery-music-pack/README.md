# Phase 4 gallery music-pack sample

This directory is source input, not a ready-to-copy mod JAR. Generate the asset,
then package the directory contents so `META-INF/openggf-mod.yaml` is at the JAR
root:

```sh
python generate-assets.py
jar --create --file openggf-gallery-music-sample.jar META-INF audio
```

Copy the resulting JAR into OpenGGF's `mods/` directory, enable it in the Mod
Manager, save, and restart. It replaces Sonic 2 Emerald Hill (`0x81`, decimal
`129`) with a quiet two-second looping tone.

## Provenance and license

`gallery-theme.wav` is generated entirely by `generate-assets.py` from the sum
of 220 Hz and 330 Hz sine functions. It is original test data created for this
repository: no Sega, ROM, disassembly, recording, sample-library, or other
third-party audio is used. The generator and its generated waveform are
dedicated under CC0 1.0; see [`LICENSE`](LICENSE). Gallery CI can regenerate the
asset directly from this source. The generated WAV is intentionally not checked in.
