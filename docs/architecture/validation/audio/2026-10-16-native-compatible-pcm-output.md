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
  every 16-bit cutoff: `MuslSin` reproduces every value, and `StrictMath.sin`
  differs on 792 of the 65,536.
- Correction (2026-10-17): the first dump formed its arguments with the host's
  double `M_PI` and pinned `0x3fc009b8777f3582` at 880 Hz. That is not the
  core's argument. The core's `audio_reset` multiplies `(f / 44100.0)` by the
  constant at `0x36f001c8a08`, `0x400921FB60000000` (3.1415927410125732). That is
  GPGX `core/macros.h:36-38`, `#define M_PI 3.14159265358979323846264338327f`, a
  float literal used because that build's libc defines no `M_PI`. On the core's
  own argument its `sin` gives `lf` = `0x3fc009b87ef9877a` (880 Hz) and `hf` =
  `0x3fe65131439abe44` (5000 Hz). `ThreeBandEq.NATIVE_PI` is that literal, and
  the test pins the corrected dump (`7a13007b...234a5`) and both cutoffs.

The stock core's per-sample EQ is inlined into `gpgx_advance`
(`0x36f001bd6e0`-`0x36f001bd98a`, both channels in SSE2 lanes). It performs
`eq.c`'s operations with commutative reordering only (`l + h`, `(m + l) + h`),
then truncates with `cvttpd2dq` and clips to 16 bits. Its `vsa` constant is
`1.0 / 4294967295.0`.

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
  0-4, none from row 5. Neither side has a write before row 14. These hypotheses
  were killed:
  1. A YM power-on clock-phase offset: no first-sweep phase from -23 to +96 clocks
     reproduces native rows 0-12, while phase 0 reproduces the engine exactly.
  2. FMA contraction: the stock core contains no FMA instructions.
  3. Equaliser operation order: the core's vectorised inlined `do_3band`
     (`gpgx_advance`, near `0x36f001bd660`) evaluates
     `f1p0 += lf*(s-f1p0)+vsa` and `(l+m)+h` with commutative reordering only,
     which is bit-identical to `eq.c` and to the Java port.
  4. The 68k's power-on events (2026-10-17). GPGX resets the YM2612 on every
     Z80-reset edge (`genesis.c` `gen_zreset_w`, including `EntryPoint`'s writes at
     sonic3k.asm:146-147, 157-159). An `OPN2_Reset` at any internal clock from 0 to
     700, keeping the wrapper's 24-slot phase as `sound.c` does, leaves the output
     identical (701 of 701): the idle ladder output is the same in every slot. The
     PSG powers on with `chanOut` 0, so its output is silent until a volume write,
     and `Init_InputPSG`'s attenuation writes change nothing.
- Attributed and fixed (2026-10-17): the equaliser's cutoff constant.
  - A write-free GPGX-source power-on (the vector driver, no strobes or PSG bytes)
    equals the engine over rows 0-13 exactly (10,290 stereo frames). So the engine's
    frame-0 driver writes are inaudible, and its chip output, mixing, resampling and
    presentation timing equal the source model there.
  - At all 47 differing frames the model's EQ sum `l + m + h` lies within 1.14e-13
    of the input sample, and native truncates to the other side. With unity gains
    the equaliser returns its input up to the last bits, so only the cutoffs' last
    bits can move these flips.
  - Replaying the model's raw (unfiltered) output through `eq.c` with the stock
    core's float-pi cutoffs reproduces native rows 0-13 exactly. With the double pi
    it gives the same 47 differences as the engine.
  - The vector generator now compiles `eq.c` as strict ISO C so `macros.h` supplies
    the float pi. Only `native-threeband-unity.s16` changed; the other three
    vectors regenerated byte for byte. The old Java source failed it at index 22
    (stereo frame 11: expected 773, was 774) before `ThreeBandEq` took
    `NATIVE_PI`.
  - Real window after the fix (844b19f02, same title-start command, Maven finished
    00:58:05Z): stereo frames 0-11,784 (rows 0-15) equal native exactly. The first
    difference is stereo frame 11,785, native (0, 0) against engine (1, 1), at row 16
    where the engine's SEGA PCM begins; native's starts at row 63 (open below). One
    stage session for all 600 presentations. No shift, normalisation or fitted bias
    was applied.
- Write ledger (native partial v2 ledger, frames 14-63; the producer faults later at
  frame 146):
  - Native row 14 is the `driver_init` service with 88 writes: 4 PSG silence writes
    from the 68k side, then the Z80's `zStopAllSound` (84 writes: YM release, TL `7Fh`,
    key-off and SSG-EG clear per channel, PSG silence, `2Bh = 0`, `27h = 0`).
  - Correction (fresh 230-frame capture whose ledger records boot writes): the engine
    does run the ROM driver initialisation. `SmpsDriverSession.install` applies the S3K
    policy's boot program (`zStopAllSound`) and then the DAC idle-loop entry
    `2Bh = 0`. On the title path that happens at the first presentation, so engine
    frame 0 holds 85 writes. They equal native row 14's 84 Z80 writes and row 15's
    first write, in identical order. The earlier ledger missed them because its
    observer was attached after boot.
  - The remaining init differences were timing (engine frame 0 against native rows
    14-15) and 8 PSG silence writes the engine did not emit: the 68k-side 4 at row 14
    and 4 Z80 writes at row 15.
  - Native row 15's `2Bh = 0` is `zPlayDigitalAudio`'s entry write (Z80 driver
    :4290-4294), which the recorder files with the first `vint` service.
  - The Z80-side 4 (2026-10-17, fixed) come from ROM `Title_Screen`'s first request,
    `Play_Music(cmd_FadeOut)` (sonic3k.asm:5425-5426, written straight into
    `zMusicNumber`, :1493-1497). The first VInt services it: `zFadeOutMusic` arms the
    fade counters and falls through `zHaltDACPSG` into `zPSGSilenceAll` (Z80 driver
    :2329-2347, :2609-2619), so the PSG writes `9Fh BFh DFh FFh` with no song playing.
    `Sonic3kTitleScreenManager.initialize`, which every engine entry to the title game
    mode calls, now issues that request; the engine's S3K fade policy already armed
    the counters and wrote `zPSGSilenceAll` without a song. A title-start capture's
    startup writes are now the 84 init writes, `2Bh = 0`, then the four PSG writes, in
    native order (`TestNativePcmCaptureLive.titleStartServicesTitleScreensFadeOutAfterTheDriverInit`,
    red before the fix).
  - The 68k-side 4 stay open. They are `EntryPoint`'s `Init_InputPSG`, which writes
    `PSGInitValues` (`9Fh BFh DFh FFh`, sonic3k.asm:177-180, :266-267) only on a cold
    power-on; a soft reset branches to `Init_SkipPowerOn`. The engine models no 68k
    power-on. The S3K physical policy's `boot()` is not that owner: it is the Z80
    driver's `zInitAudioDriver` program and also runs on in-engine `HardReset`
    boundaries, which are not power-ons. No write was injected.
  - The native SEGA PCM starts in the service at row 63. The engine's SEGA bytes start
    at frame 16.
- Earliest unmatched owners:
  1. Native rows 0-12 are power-on lag rows (68k init and Z80 driver load), and the
     engine starts at `Sonic3kTitleScreenManager` `Phase.SEGA_FADE_IN` with no
     equivalent. Genesis CPU power-on is not modelled.
  2. Before `cmd_SEGA`, ROM `Title_Screen` (sonic3k.asm:5424-5517) runs `cmd_FadeOut`,
     `Pal_FadeToBlack`, the logo `Kos_Decomp`/`Eni_Decomp`, the plane map and
     `Pal_FadeFromBlack`. The engine ran a fixed 16-frame `SEGA_FADE_IN`
     (`SEGA_FADE_DURATION`) and issued `CMD_SEGA` at frame 16, after reading Start.
     - Fixed (2026-10-17, 225cffe5b): both fades are 22 `Wait_VSync` iterations
       (`move.w #$15,d4` / `dbf`). `Pal_FadeFromBlack` steps line 0 toward
       `Pal_TitleSonic1` with `Pal_AddColor`, uploaded per VBlank, and `cmd_SEGA`
       follows the 22nd step before `Wait_SegaS3K` first reads Start. So a press on
       that first frame issues `cmd_SEGA` then `cmd_StopSEGA` instead of a lone stop.
       `TestSonic3kTitleScreenSegaTiming` failed 4 of 4 on the old source.
     - Start in `Wait_SegaS3K` leaves through `loc_3FE4` like the timeout
       (sonic3k.asm:5522-5532): `cmd_StopSEGA`, then the `Pal_Title` loop, which reads
       no input (:5533-5544), then `mus_TitleScreen`. The engine had jumped to the
       white flash and started the title music in the same frame (fixed aec7836ce).
     - Title entry after the ending (ending owner, a1afac243):
       `GameLoopTitleEntryFade.beginAfterLevelPass` still calls `stopMusic`, while ROM
       `Title_Screen` only issues `cmd_FadeOut`, which the manager now issues on
       every title entry. The closing music is still an actual PCM gap; the fix
       belongs at that title-entry seam, by agreement with the ending owner and lead.
     - The native lag column dates the same structure from the BK2's own rows
       (comparison only): rows 13-34 polled (22, `Pal_FadeToBlack`), rows 35-40 lag
       (6, the masked span), rows 41-62 polled (22, `Pal_FadeFromBlack`), and the
       SEGA PCM is first audible in row 63 at stereo sample 504 of that row.
     - Still open, with owners: (a) the cold boot before `Title_Screen`, rows 0-12
       (68000 `EntryPoint`, `Init_ClearRAM`, `Test_LockOn`, `GameProgram`,
       `SndDrvInit`), which the engine does not run; (b) the interrupt-masked span
       (sonic3k.asm:5432-5515), whose hardware frames need the 68000 cost of
       `Kos_Decomp`, `Eni_Decomp`, `Plane_Map_To_VRAM` and the RAM clears, which the
       engine has no model of. The title-loop Kos manifests time `Process_Kos_Queue`
       services and do not apply to this synchronous decode. (c) The Z80's service
       time inside the frame: native's onset is 504 samples into its row, the
       engine's transport starts 10 samples into its packet. No frames were inserted
       for (a) or (b), and no native row was hard-coded.
     - Real window after the fixes (aec7836ce, same title-start command, Maven finished
       01:48:06Z): stereo frames 0-31,655 (rows 0-42) equal native. The first difference
       is stereo frame 31,656, packet 43 sample 11, native (0, 0) against engine (1, 1):
       the engine's SEGA PCM onset, 43 = 2 x 22 fade VSyncs - 1, against native's row 63
       (owners (a)-(c) above). One stage session. The engine now enters DATA_SELECT at
       frame 195 and AIZ1 at 238 (was 208). An earlier capture at 225cffe5b is void: it
       ran classes the preceding old-source red run had recompiled.
  3. At engine frame 208 the movie leaves DATA_SELECT for AIZ1
     (`NativeDataSelectLaunchFlow.launch` -> `loadZoneAndActForFreshRuntime` ->
     `LevelManager.configureAudio`). That call re-installs the same ROM and audio
     profile instances, and each call rebuilds the presentation through
     `replaceShadowPresentationForBaseMutation`. The stage session counter goes 1 to
     3, so the production chip restarts from reset while native's chip continues.
     - Red first, production path (22:07:41Z):
       `TestNativePcmCaptureLive.dataSelectLevelEntryKeepsThePresentationDevice`
       reported "session rebuilt at frame 208, expected 1 but was 3".
     - Fixed (2026-10-17). `AudioManager.setRom`/`setAudioProfile` keep the
       presentation when they would re-install the very source already installed:
       the same profile and ROM instances, with its loader built. A new ROM or
       profile instance, a reset, or a donor change still goes through the full
       replacement (`TestAudioManagerRuntimeInstallation`: two identity cycles, ROM,
       profile and reset replacements; the donor control is unchanged). The witness
       now passes: one stage session and blip_buf's exact count for 230
       presentations across the frame-208 entry.
     - The first attempt broke `TestS3kEndingAudioRequestLedger` 3/3 ("mus_SSZ
       ticks before the fade"), because that harness stepped the title for hundreds
       of frames without presenting audio. The rebuild had been discarding the
       resulting command backlog. The harness was wrong, not the queue: production
       (`Engine.display`, `GameplayCaptureTool`) presents one outer audio frame after
       every simulation step. The harness now opens its capture lease before the
       title and presents and drains every title step in the tool's order (step,
       `presentOuterFrame`, drain). A new control asserts `mus_Title` is serviced on
       the title's presented frames, still at its own cadence when the title ends.
       All three products pass unchanged, including the `mus_SSZ` control.
     - Rewind of the kept device: `TestS3kLevelEntryAudioDeviceRestore` (S3K ROM,
       accurate core, ThreeBand stage) takes a snapshot before a same-source
       re-install, presents 90 frames, restores, and replays the stage output byte
       for byte; a window shifted by one hardware frame must differ.
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
- [POWER-ON] The 68k `Init_InputPSG` cold power-on writes (no engine owner), the
  cold boot before `Title_Screen` (native rows 0-12) and `Title_Screen`'s
  interrupt-masked decompression span (native rows 35-40). Both need a 68000 cost
  model.
- [PAUSE] The engine's 161 silent packets at frames 328-488 of the title-start
  capture are its host pause (`GameLoop.userPaused`, SILENT presentation,
  `host_paused = 1`) on the movie's row-328 Start. Native does not pause there: its
  rows 322-499 carry full audio, and its AIZ1 segment begins at BK2 row 577, so the
  movie's rows 200-328 are data-select input natively while the engine is already in
  the level (front-end timing). The ROM's own pause, `Pause_Game`
  (sonic3k.asm:1550-1570: `zPauseFlag = 1`, so the Z80 runs `zPauseAudio` and skips
  track updates while the YM2612 keeps clocking), still differs from the engine's
  SILENT presentation, which writes nothing and freezes the chip.
  - ROM semantics (2026-10-17). `Pause_Game` writes `zPauseFlag = 1` on the pause
    press and `$80` on the unpause press; `Pause_Loop` runs only `Wait_VSync`.
    `zPauseUnpause` (Z80 driver :2232-2305) runs first in `zUpdateEverything`.
    - Flag 1: the flag becomes 2 and `zPauseAudio` (:2574-2608, `fix_sndbugs = 0`)
      writes 19 times: PSG `9F BF DF FF`, `B4/B5/B6 = 0` on part I and `B4/B5 = 0`
      on part II, `28h = 00..05`, then PSG `9F BF DF FF` again. FM6/DAC is left
      alone.
    - Flag 2: the routine pops its return, so no SFX or music update runs, and
      nothing is written.
    - Flag `$80`: the flag clears; with `zFadeOutTimeout` set it runs
      `zStopAllSound`, otherwise it rewrites `B4+ch` for each halted or playing FM
      track (PSG1 is a no-op through `zWriteFMIorII`). The shipped SFX loop then
      reads seven "tracks" from `zTracksSFXEnd` onwards, memory the engine cannot
      model. Updates resume in that same service.
    - The YM2612 and PSG keep clocking throughout.
  - Engine. A level Start in production toggles `GameLoop.userPaused` (the host
    pause), and `OuterFramePresentation` then presents SILENT: zeros, no service, chips
    frozen. The engine's ROM pause (`GameStateManager.applyPauseToggle` from
    `LevelFrameStep`/`LevelIterationAdmissionController`) gates only the level update:
    `GameLoop.isPresentationPaused` does not read it, so those frames present FORWARD
    and the driver keeps servicing the music.
  - Minimal hunks (not landed; they exceed one bounded slice and touch shared loop
    code). Audio/session: a policy pause program and unpause rule, a session
    sound-pause state that skips `serviceOuterFrame` while the chip renders, its
    snapshot field, and a command-timeline path so rewind stays deterministic. Shared
    loop (lead and ending owner): drive that from `GameStateManager` pause
    transitions, decide whether a level Start is the ROM pause or the host pause, and
    keep the modal picker, frame-step and application mute SILENT. Validating it
    against native needs a window with a native in-level pause; the title-start
    window has none.
- [CORE] A MAME YM2612 output path for the Knuckles movie; Nuked is never
  substituted.
- PAL, DAC timing, carry state at an ending window, and every ending product.
