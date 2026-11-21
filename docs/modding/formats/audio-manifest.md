# Audio manifest

`audio/audio-manifest.yaml` is present when music or SFX assets are declared. Its
authoritative field sets are `ModAudioManifestParser.ROOT`, `TRACK`, `PACKAGE_TRACK`, and `SFX` in
[`ModAudioManifestParser`](../../../src/main/java/com/openggf/mods/ModAudioManifestParser.java).
Decode, duration, PCM-byte, sample-rate, channel, and gain bounds come from
[`ModInputLimits`](../../../src/main/java/com/openggf/io/ModInputLimits.java).

Streamed tracks in versions 1 and 2 require `id`, `assetPath`, `loop`, `loopStartFrame`, `gain`, and
`tempoEffects`; `loopEndFrame` may appear only for a looping track. A non-looping
track requires start frame 0 and no end frame. SFX entries require `id`, `assetPath`,
and `gain`. Assets are normalized contained WAV/Ogg paths. Ids are local names; the
manifest owner supplies the namespace.

An SFX entry may add a `dac` mapping to play a retro DAC rendering of its asset instead of the
recording itself. The host derives it while preparing the mod's audio, so a mod ships one clean
file and declares a second id for the period-style version:

```yaml
sfx:
  - id: voice-title
    assetPath: audio/voice/title.ogg
    gain: 1.0
  - id: voice-title-md
    assetPath: audio/voice/title.ogg
    gain: 1.0
    dac:
      sampleRate: 8000        # sample-and-hold rate, 2000..32000
      bits: 7                 # signed quantisation bits, 2..15
      lowpassHz: 3400         # anti-alias low-pass before the hold, 200..16000
      recoveryMix: 0.1        # share of the high-passed input mixed back for consonants, 0..1
      recoveryHighpassHz: 2400
```

All five fields are required. The rendering keeps the asset's length and is cached beside its
clean decode; it is an artistic effect, not emulation of a particular chip. Typing of the
Hedgehog uses it for its Mega Drive voice option.

The [music sample](../samples/phase4-gallery-music-pack/README.md) exercises tracks;
the [standalone sample](../../../src/test/resources/mods/sample-standalone-src/README.md)
exercises both a track and standalone one-shot SFX. Patch startup scenes can play their
own declared SFX with `SceneAudio.playSfx(String)`; the host supplies ownership and
launch preparation enforces the same decode/cache bounds. See
[scene audio](../guides/mod-scenes.md#5-audio-and-storage) and the
[Eggman's Sky bank](../../../examples/eggmans-sky/README.md#system-voice).
Base-game SFX override mapping is not part of manifest v1 today.

## Version 2 native music

With `formatVersion: 2`, a track may use `smpsPackage` in place of the
streamed path and loop fields. Its fields are `id`, `smpsPackage`, `gain` and
`tempoEffects`. The package path is a lower-case contained directory of the
form `audio/smps/<id>/`. The slash is required. `gain` is in 0–4.
The patch's `baseGame` must match the package driver (`s1`, `s2` or `s3k`).
When `tempoEffects` is true, S1 and S2 use the package's
`driverOptions.speedUpTempo` byte if provided, and S3K uses its native
speed-shoes multiplier. SFX declarations and version 1 manifests keep their
existing semantics.

See the [SMPS package format](smps-package.md) for the contents of that directory.
