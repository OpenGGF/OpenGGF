# Native-compatible PCM output stage: component tier (2026-10-16)

**Completion boundary.** Rendered ending PCM stays **open**. This stage is
capture and diagnostic output. It does not close production rendered-audio
parity on its own. The byte-exact results below cover one tier: the output
stage on an identical strobe schedule. Every declared ending product,
contiguous transitions, PAL, the Knuckles/MAME core, PSG and DAC timing each
still need their own evidence.

Source: lane `pcm-output`, branch `feature/ai-s3k-r2-pcm-native-output`, based
on integration `f96d31379` plus `feature/ai-s3k-r2-ending-audio-ledger`.

## What was built

- `com.openggf.audio.synth.blip.BlipBuf` is a Java port of blip_buf 1.1.0 in the
  stereo form Genesis Plus GX ships. The source is `core/sound/blip_buf.c` at
  BizHawk `427556b5`. The port keeps its LGPL-2.1-or-later headers and follows
  the Nuked port's GPL-3 combination pattern (`NOTICE.md`).
- `NativeCompatiblePcmOutput` is fed by the production `Ym2612Chip`. On each
  internal clock it takes the chip's held 24-cycle pin sum, multiplies it by 11
  (Nuked) and by `fm_preamp` 100/100, and adds the change as a blip delta at
  `start + k * 42` master clocks. NTSC frames are 3420 * 262 master clocks;
  rates are 53,693,175 Hz to 44,100 Hz, and a frame-end read is followed by
  the movie filter.
  - The filter is None, LowPass (16.16 `lp_range` with 16-bit carries), or
    ThreeBand (`eq.c`, public domain, with gains `(short)(gain * 100) / 100.0`).
  - The behaviour is modelled from GPGX `sound.c`/`system.c` arithmetic, with
    citations in the class. No `sound.c` text is copied.
- `MuslSin` ports the `sin` the BizHawk core links. The ThreeBand cutoffs need
  it (see below).
- Production wiring: `SmpsPhysicalDevice.Settings` gains an optional
  `nativePcmOutput` component; the default is `null`, so no stage exists. The
  stage is installed at device construction, which puts its clock origin at
  the chip's power-on.
  - `SmpsDriverSession.drainNativeCompatiblePcm` and
    `nativeCompatibleFramesEnded` expose it, as does `OwnedSmpsAudioStream`.
  - The accurate core is required. The FAST core is rejected, never
    substituted.
  - The ordinary output path, the 48 kHz presentation and the user defaults
    are unchanged. A test proves the ordinary chip frames and the session's
    presentation PCM are identical with and without the stage. No `@ModApi`
    type changed.
- Rewind: the chip's `Snapshot` and its `MutationBackup` both carry the stage's
  complete mutable state:
  - blip offset, factor, both integrators and both buffers;
  - the held FM level and the previous delta level per side;
  - the master-clock position and the frames-ended count;
  - the LowPass carries and both EQ histories;
  - the pending output queue.

  Restoring a state whose stage presence or configuration differs fails before
  any field changes.

## Evidence (`TestNativeCompatiblePcmOutput`, vectors in `src/test/resources/audio/native-compatible-pcm/v1/`)

**[OUTPUT, FM + DAC, identical strobe schedule]** The production chip drives
a synthetic FM + DAC stream: two channels, a pitch change, key-offs and
twenty `0x2A` DAC writes. It produces 194 bus strobes. GPGX's own `ym3438.c`,
`blip_buf.c` and `eq.c` replay that schedule in a native-source harness. Its
output equals the stage's 5,887 stereo samples (8 NTSC frames) byte for byte
in all four configurations:

- None;
- ThreeBand at the Sonic/Tails movie's SyncSettings (880/5000 Hz, unity gains);
- LowPass `0x6666`;
- ThreeBand with gains 150/50/200.

The README records the generator hashes and commands.

**Perturbations that must fail:**

- a +1 pin-sum unit in one frame;
- a one-clock (42 master clocks) origin shift;
- mid gain 1.01;
- a low cutoff of 881 Hz;
- `lp_range + 1`;
- the unfiltered output against the ThreeBand vector.

**Rewind:**

- *Snapshot.* A mid-buffer, mid-sweep capture is restored into a fresh chip,
  and the forward replay is byte-identical. Negatives: replacing any single
  stage field with the restoring chip's value changes the replay. The fields
  are the blip offset, both integrators, both buffers, the clock position,
  held and previous levels per side, frames ended, pending samples, and the
  EQ histories or LowPass carries.
- *Mutation backup.* After a 1,200-sweep mutation, rollback restores an
  identical stage and identical subsequent output. Negatives: leaving any one
  field at its mutated value changes the replay.
- The blip factor is configuration-derived and equal in both states, so
  omitting it is undetectable and harmless.

## Measurement hazard: the libm under the equaliser

A first harness linked the host glibc `sin`. For 880 Hz at 44.1 kHz,
`2 * sin(pi * 880 / 44100)` came out as `0x3fc009b8777f3581`, and 916 of
11,774 unity-gain ThreeBand samples differed. The other three vectors matched.

The actual native core links musl. Evidence:

- The stock BizHawk 2.11 `dll/gpgx.wbx.zst` (sha256 `c4231296...b220d12`) is
  the TraceChaser source-lock "stock" core. Its decompressed ELF is not
  stripped.
- Its `audio_reset` inlines `init_3band_state` and calls musl `sin` at
  `0x36f00004e14`.
- Its `sin`, `__sin`, `__cos` and `__rem_pio2` machine code follows musl 1.2.0
  (`waterbox/musl` at `2063abc4`) in operation order and constants.
- Running that machine code, with the ELF mapped at its fixed addresses, for
  every 16-bit cutoff gives `0x3fc009b8777f3582` at 880 Hz. The
  65,536-value dump hash is pinned in the test, and `MuslSin` reproduces every
  value.
- `StrictMath.sin` differs on 792 of the 65,536 values.

The stock core's inlined per-sample EQ code has not been audited at machine
level; only its source `eq.c` has.

## Step 3 (first real window): not executed — blockers

No real gameplay window was compared, and the PCM-equality tier is unmeasured.

1. **Engine side: live presentation is not wired.** Production gameplay audio
   builds its session `Settings` inside `AudioManager`, which the ending-audio
   lane owns. Enabling the stage there, and draining it in
   `GameplayCaptureTool --audio`, needs a coordinated `AudioManager` change. It
   was not made.
2. **Native side: no strobe schedule or per-frame PCM producer exists.**
   - The pinned v2 oracle partitions YM/PSG writes per zVInt service without
     master-clock timestamps. No reviewed observer emits per-strobe M-cycle
     times.
   - `GpgxHost.DrainDiagnosticAudio` can supply final PCM, but no runner for
     the Sonic/Tails movie exists.
3. **Structural [SCHEDULE] divergence, known in advance.** The live
   presentation renders 800 samples per frame (48 kHz at 60 frames). That
   advances the chip by 800 * 53,267.03 / 48,000 * 24 ≈ 21,306.8 YM clocks per
   frame; native frames are 896,040 / 42 ≈ 21,334.3.
   - The wiring test drained 2,943 stereo samples (four NTSC frames) after
     four 800-sample frames, because the resampler's look-ahead clocks past
     the fourth boundary. That look-ahead is a fixed offset; over a window the
     rate gap of about 27.5 clocks per frame dominates.
   - Engine writes queue during the service and are applied with bus pacing at
     the next render. Native writes land at Z80 bus times inside the frame.
   - So once a native per-strobe schedule exists, the first schedule
     divergence is expected at or near the first strobe. Its value is
     unmeasured until then.

PSG is excluded from the stage. Native output mixes PSG into the same buffer,
so even a matching FM schedule cannot match native PCM while the PSG sounds.

## Remaining open items

- [SCHEDULE] A native per-strobe master-clock observer and a Sonic/Tails PCM
  runner.
- [SCHEDULE] Live-path wiring (owned by `AudioManager`) and a first-divergence
  report.
- [OUTPUT] A GPGX-equivalent PSG contribution (`psg_preamp` 150, `hq_psg`).
- [CORE] A MAME YM2612 output path for the Knuckles movie; Nuked is never
  substituted.
- PAL, DAC timing, carry state at an ending window, and every ending product.
