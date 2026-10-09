# Audio manifest v1

`audio/audio-manifest.yaml` is present only when streamed audio is declared. Its
authoritative field sets are `ModAudioManifestParser.ROOT`, `TRACK`, and `SFX` in
[`ModAudioManifestParser`](../../../src/main/java/com/openggf/mods/ModAudioManifestParser.java).
Decode, duration, PCM-byte, sample-rate, channel, and gain bounds come from
[`ModInputLimits`](../../../src/main/java/com/openggf/io/ModInputLimits.java).

Tracks require `id`, `assetPath`, `loop`, `loopStartFrame`, `gain`, and
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
