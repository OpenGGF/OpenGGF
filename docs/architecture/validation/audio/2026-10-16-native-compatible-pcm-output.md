# Native-compatible PCM output stage: component tier (2026-10-16)

**Completion boundary.** Rendered ending PCM stays **open**. This stage is
capture and diagnostic output. It does not close production rendered-audio
parity on its own. The byte-exact results below cover one tier: the output
stage on an identical strobe schedule. Every declared ending product,
contiguous transitions, PAL, the Knuckles/MAME core, PSG/DAC write timing each
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

## First real window (2026-10-16): measured tiers

The tiers below are kept separate. None of them is waveform parity.

**[NATIVE OBSERVATION]** TraceChaser `--final-pcm-window` (`feaf6fb0c`, local)
ran `s3k-sonic-tails-complete-emeralds.bk2` (sha256 `ad40fb0b...`) on the
stock BizHawk 2.11 core (`c4231296...`) under the movie's own settings:
`Nuked_YM2612`, ThreeBand 880/5000 Hz, gains 100. Rows 0-2399 produced
1,766,274 stereo frames (735.9475 per row, 228 lag rows), pcm sha256
`6fc81f48...aaa2`, in 2 s.

**[ENGINE LIVE PATH]** `GameplayCaptureTool --audio --native-pcm` enables
the stage through
`AudioManager.setNativeCompatiblePcmCapture` (default `null`) and drains it
after every presented frame. The capture booted AIZ1 with Sonic and Tails and
replayed the same movie from row 577, the `aiz` segment's `bk2_frame_offset`,
for 600 frames: one stage session, 440,832 stereo frames. Maven finished
20:15:41Z after a 1,261 s queue wait.

**[CADENCE] — a structural schedule defect, measured.**
- Native: rows 577-1176 hold 441,569 stereo frames, one 3420 x 262 =
  896,040-master-clock NTSC frame per row. No row in that range is a lag
  row, and lag rows also run a full hardware frame.
- Engine: 600 presentations completed exactly blip_buf's sample count for
  599 NTSC frames (440,832), and one presentation drained nothing. The live
  presentation clock (`AudioPresentationProducer`'s `AudioFrameClock`) takes
  `sampleRate / FrameRateResolver.effective()`, which is 48,000 / 60 = 800
  samples per presentation. That advances the production chip by
  800 * 24 * 53,267.03 / 48,000 = 21,306.8 YM clocks, or 894,886.2 master
  clocks per presentation. The ROM's frame is 896,040, so each presentation
  is 1,153.8 master clocks (1.29 per mille) short.
- The initial fractional phase is accounted for. Both blip buffers start at
  `factor / 2`, and the engine's chip look-ahead ended NTSC frame 0 inside
  presentation 0. 600 * 894,886.2 / 896,040 = 599.23 frames, consistent
  with 599 frames ending.
- The owner is the presentation clock (`AudioFrameClock` from
  `FrameRateResolver`: configured FPS 60 for NTSC, 50 for PAL against the
  ROM's 49.70 Hz). It is not the stage.

**[CADENCE] — corrected at the owner (lead-directed, 2026-10-16).**
`AudioManager.ensureShadowPresentation` now drives the live session's sound
device at `SmpsHardwareFrameBudget.synthesisRate`. The rate is
`sampleRate * 53,693,175 / (frameRate * 896,040)`, which is 47,938.19 Hz for
48 kHz at 60 Hz. Each presentation's `sampleRate / frameRate` output samples
then span exactly one NTSC hardware frame of chip time. The rate is derived
from the NTSC line and frame constants, not fitted.
- Red first: `TestNativePcmCaptureLive.everyPresentationAdvancesExactlyOneHardwareFrame`
  ran 600 production presentations from the emeralds movie. It expected
  441,568 stereo frames (600 NTSC frames) and got 440,832 (599), Maven
  finished 20:58:51Z.
- The regression also requires every presentation to cross exactly one
  hardware-frame boundary (735 or 736 samples, never 0). A control requires
  the ordinary 48 kHz capture to keep 800 samples per presentation.
- Effect: at a 60 Hz presentation, ordinary sound is about 1.29 per mille
  higher in pitch and tempo per wall-clock second, and the same per gameplay
  frame.
- PAL is unchanged: the engine's chips use NTSC clocks, so a PAL budget
  needs PAL chip clocks. That remains open.
- The SEGA PCM transport's whole-hertz rounding is corrected; see [TRANSPORT] below.

**[CARRY / INGRESS] — exploratory, not attributable.** The engine capture
boots AIZ1 directly and replays inputs from row 577. Native ran rows 0-576
first (SEGA, title, menus), so chip, driver, blip and EQ history differ. The
capture session also forces `S3K_SKIP_INTROS`
(`GameplayCaptureSession`), so the engine does not play the AIZ intro route
that native plays from row 577.
`compare_native_pcm_window.py` reports DIFFERENT at stereo frame 0: native
(792, 1002) against engine (0, 0). The earliest unmatched ingress owner is
the capture entry itself: `GameplayCaptureSession.boot(zone, act)` creates a
fresh presentation session at level boot, with no power-on, title or menu
audio history. A production S3K SEGA/title entry does exist:
`GameplayCaptureTool --title-screen` (`GameplayCaptureSession.startAtTitle`)
enters `Sonic3kTitleScreenManager` at `Phase.SEGA_FADE_IN`, and its SEGA hold
issues the SEGA chant and stop commands. `S3K_SKIP_INTROS` is an AIZ level
flag only. What may be unmodelled is Genesis CPU power-on equivalence, the
native frames before the SEGA screen. The startup comparison below measures
that boundary. Waveform equality and musical first-cause attribution need an
authentic production entry from power-on or title matched to native rows,
and that does not exist yet.

**[STARTUP] — the production title-start window from row 0 (2026-10-16).**
`GameplayCaptureTool --title-screen --input-start 0 --frames 600 --audio --native-pcm`
replayed the original emeralds BK2 (sha256 `ad40fb0b...`; SyncSettings Nuked_YM2612,
ThreeBand 880/5000 Hz, gains 1.0, Region 0, which autodetects to USA from the header
`JUE`). The stage was attached before boot. The engine ran at 650a823ea plus the
docs-only edit; Maven finished 21:24:47Z. It was compared with the preserved native
capture of rows 0-2399 with no realignment:
- PCM: identical to stereo frame 10. The first difference is stereo frame 11, native
  (773, 773) against engine (774, 774), inside the power-on DC transient both sides
  start at sample 3. The differences are isolated ±1 values: 47 per side over rows
  0-4, none from row 5. Neither side has a write before row 14. The cause is
  unattributed, and three hypotheses were killed:
  1. A YM power-on clock-phase offset: no first-sweep phase from -23 to +96 clocks
     reproduces native rows 0-12, while phase 0 reproduces the engine exactly.
  2. FMA contraction: the stock core contains no FMA instructions.
  3. Equaliser operation order: the core's vectorised inlined `do_3band`
     (`gpgx_advance`, near `0x36f001bd660`) evaluates
     `f1p0 += lf*(s-f1p0)+vsa` and `(l+m)+h` with commutative reordering only,
     which is bit-identical to `eq.c` and to the Java port.
- Write ledger (native partial v2 ledger, frames 14-63; the producer faults later at
  frame 146):
  - Native row 14 is the Z80 driver's `driver_init` service with 88 writes: PSG
    silence `9F/BF/DF/FF`, then YM key-off, TL `7Fh` and RR `FFh` on every operator.
    Engine frame 14 has no writes. The engine session issues no driver-initialisation
    program, which is the first ledger divergence.
  - Native row 15 is the first `vint` service, which writes `2Bh = 0`.
  - The native SEGA PCM starts in the service at row 63. The engine's SEGA bytes start
    at frame 16.
- Earliest unmatched owners:
  1. Native rows 0-12 are power-on lag rows (68k init and Z80 driver load), and the
     engine starts at `Sonic3kTitleScreenManager` `Phase.SEGA_FADE_IN` with no
     equivalent. Genesis CPU power-on is not modelled.
  2. Before `cmd_SEGA`, ROM `Title_Screen` (sonic3k.asm:5392-5485) runs `cmd_FadeOut`,
     `Pal_FadeToBlack`, the logo `Kos_Decomp`/`Eni_Decomp`, the plane map and
     `Pal_FadeFromBlack`. The engine runs a fixed 16-frame `SEGA_FADE_IN`
     (`SEGA_FADE_DURATION`) and issues `CMD_SEGA` at frame 16.
  3. At engine frame 208 the title hands off to DATA_SELECT, the game module
     re-initialises, and `AudioManager.replaceShadowPresentationForBaseMutation`
     rebuilds the presentation session. The stage session counter goes 1 to 3, so
     the production chip restarts from reset while native's chip continues.
- Waveform equality from row 0 therefore cannot hold past the startup boundary. No
  offset was fitted and no chip state was hydrated.

**[TRANSPORT] — SEGA PCM byte cadence is exact (2026-10-16).** The SEGA PCM bytes are
ordinary YM `2Ah` writes into the production chip, so the native-compatible stage
already carries them. Their cadence was wrong: `segaPcmByteCost` rounded the device
rate to whole hertz.
- On the live hardware-frame budget (47,938.19 Hz), a pre-fix scratch probe with
  24,000 bytes put 3,892 of them one sample early. The first was byte 221, at render
  734 instead of 735 (21:25:15Z).
- Fix: the device settings now carry the rate as an exact rational,
  357,954,500 / 7,467 Hz, through `SmpsHardwareFrameBudget.Rate`. The transport counts
  Z80 cycles times the numerator, so a byte costs `248 * n` and an output frame adds
  `3,579,545 * d`.
- `TestSmpsSegaPcmTransportCadence` checks:
  - every byte's render index against `ceil(k * 248 * 800 / 59,736)`, where 59,736 is
    the Z80 cycles in one NTSC hardware frame;
  - a whole-hertz control that misplaces bytes;
  - the phase across a session snapshot restore;
  - that the transport is audible in the stage at one hardware frame per presentation.
- Rates given only as a double keep whole-hertz rounding, which is exact for integer
  rates.

**[PER-SERVICE LEDGER] — unavailable for this window.**
- The existing per-service oracle (`s3k-aiz1-intro-reference-v2`) is a
  different movie, `s3k-complete-sonic-tails.bk2`, recorded with MAME_YM2612
  and no filter. Its inputs diverge from the emeralds movie at row 113.
- The existing v2 producer (`S3kAudioOracleReferenceCaptureV2`, lock-matching
  observer core `25ee305d...`) faults on the emeralds movie: EndFrame -3 at
  frame 146, reason 5, 68k PC `$108A`. That PC is inside HInt2's
  palette-transfer `move.l` (sonic3k.lst:5068).
- The engine's per-frame logical chip writes are recorded
  (`chip-writes.jsonl`) for a later comparison.

**[STROBE CLOCK] — not measured.** No reviewed observer gives native
per-strobe master-clock times, so per-strobe timing is unmeasured. That does
not block the PCM and cadence tiers above.

**Core selection.** `--native-pcm` sets the accurate (Nuked) FM core as a
session override for its own capture. The stage taps that chip's pins.
The shipped default is `audio.fmCore=fast`. Without the override, that FAST
core was refused inside level load (`TestNativePcmCaptureLive`, first run).
The exploratory capture above logged `FM core: accurate` from its own JVM
configuration.

**Region.** The stage is NTSC-only. `--native-pcm` refuses a PAL engine
region and a BK2 `Region` that GPGX resolves to PAL. Autodetect follows
`get_region`'s USA > Japan > Europe order on the ROM header; the locked-on
S3K header reads `JUE`, which resolves to USA.

The exploratory capture ran before the stage's PSG half existed, so its
engine stream is FM and DAC only.

## PSG half (component tier, 2026-10-16)

`NativeCompatiblePsgOutput` models GPGX `psg.c`'s documented behaviour; no
text is copied. It takes the session's PSG data-bus bytes at the master-clock
time of the chip's next YM clock and adds band-limited deltas at 240-master-
clock generator steps to the same buffer.
- Integrated part: 15-bit noise, feedback mask `0x9`, a zero period acts as 1.
- Levels: `2800 * 10^(-2k/20)` truncated, times preamp 150.
- Writes: a volume change made while a channel is high lands at the next
  240-cycle boundary. A noise-control write resets the shift register.

The vector stream adds 25 PSG bytes: tone frequency latch/data pairs,
attenuation changes, white noise, tone-3-driven periodic noise and silencing.
GPGX's own `psg.c` was linked into the harness against a stub `shared.h`. All
four vectors (None, ThreeBand unity, LowPass `0x6666`, ThreeBand 150/50/200)
remain byte-identical (5,887 stereo samples) with PSG mixed in. FM strobes
are unchanged.
- Perturbations: one flipped PSG data bit, and a level write moved a quarter
  NTSC frame later, each break the None vector. Fine-scale PSG timing is not
  proven by these vectors. A one-YM-clock move of a frequency write and a
  960-clock move of the level write were both invisible, because GPGX
  applies PSG effects on 240-clock steps and a low channel's level change
  waits for its next flip.
- Rewind: the PSG generator state rides in both the snapshot and the
  mutation-backup paths, and omitting it changes the replay.
- Scope: this proves the PSG output stage on an identical write schedule
  only. Production PSG write timing is the session's own, and it lands
  between frames.

## Remaining open items

- [SCHEDULE] A native per-strobe master-clock observer, and a per-service
  producer that runs the emeralds movie.
- [CADENCE] A decision on the 60/50 Hz presentation clock against the ROM's
  59.92/49.70 Hz hardware frame.
- [CARRY] An authentic production entry with native-matched history before
  any waveform-equality claim.
- [SCHEDULE] PSG write timing: engine PSG bytes land at the next YM clock after
  a service; native bytes land at Z80/68k bus times.
- [CORE] A MAME YM2612 output path for the Knuckles movie; Nuked is never
  substituted.
- PAL, DAC timing, carry state at an ending window, and every ending product.
