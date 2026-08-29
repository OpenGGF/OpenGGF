# PSG clean-room core: capture comparison (round 1)

**Worktree:** `feature/ai-psg-clean-room` at `2bdd32746` (new core landed in
`cdc4fdb0d`), based on `develop` `8290558c4`.
**Contract:** [`../designs/2026-08-29-psg-clean-room-contract.md`](../designs/2026-08-29-psg-clean-room-contract.md).
**Verdict:** **FAIL** — pitch, attenuation ladder, onset timing, DC and the
white/periodic noise *sequence* all match the pinned reference within the
tolerances below, but the new core silences the noise channel whenever the
noise clock is linked to tone 2 and tone 2's period is 0 or 1. The reference
(and MAME, and the previous engine core) produce maximum-rate noise there. The
shipped SMPS note tables put period 0 at the top of the PSG range, so this is
not a corner case: **22 of the 68 noise-carrying SFX across the three ROMs
drive that state**, among them S1/S2 `AA` Splash, S2 `A2` Spike Switch and the
`B6` Spikes Move target of this round. Details in §6.

## 1. What was compared, and against what

The contract's listed reference captures (`2f-shield`, `3c-spindash-release`,
`a6`, `af`, `b5`, `be`, `c6`, `ce`, `inhaling-bubble`, `ring`, `s2-4f`,
`Signpost*`) are FM-only per the disassembly SFX headers, so they cannot
validate the PSG. They were used only as the no-regression control in §7.

The PSG comparison therefore rests on two references the contract names:

1. **Pinned Genesis Plus GX `core/sound/psg.c` + `blip_buf.c`** at GPGX
   `051d430d3d1b54625f9900c8f152d7f232e06daf` — the GPGX commit
   `tools/bizhawk-headless/native/gpgx-audio-observer/fetch-source.sh` pins for
   BizHawk `427556b5ef3ac437eba754d90c5e7e9096c9a8df`. The two files were fetched
   from that exact commit (sha256 of `psg.c`:
   `8ce153597bd02b34472e6b74b29be9eddce796e8a269049eda9bad815b766700`; note the
   untracked `docs/gensplusgx/psg.c` in the main checkout is a *different*
   revision and was not used) and built into a 40-line standalone harness
   (appendix A) that drives `psg_init(PSG_INTEGRATED)`, `psg_config(100, 0xFF)`,
   `psg_write`, `psg_end_frame` and `blip_read_samples` from a
   `w <byte>` / `r <samples>` script, at any output rate.
2. **The ROM SFX themselves**, rendered through the real driver by a new
   headless tool (§2), whose PSG write log becomes the script fed to (1), to
   the new `PsgChip`, and — as a control — to the `develop` `PsgChip` compiled
   under another name in a scratch build (it needed no source changes beyond
   the rename; its `BlipDeltaBuffer` dependency is unchanged on this branch).

The BizHawk-native `gpgx_s3k_pcm_psg_sample` tap (contract stage 2 reference)
was **not** exercised: it needs a BK2 per SFX played over silence and a
headless BizHawk run, which this round did not have budget for. Because the
tap samples the same `psg.c` update loop the harness runs, the harness is a
strictly tighter comparison of the chip; what it cannot check is the driver's
write *timing* relative to the real Z80/68k, which is out of this round's scope.

Streams were rendered at **44 100 Hz**, **48 000 Hz**, and at the chip's own
**÷16 tick rate (223 721.5625 Hz)**, the last so that one sample equals one
generator tick and lags are exact tick counts. The reference emits
`PSG_MAX_VOLUME = 2800` full scale, the new core `8191`; every level metric is
in dB relative to each core's own full scale, and the sample fits below scale
the reference by `8191/2800 = 2.9254`.

## 2. Tooling added

`src/main/java/com/openggf/tools/audio/PsgSfxRenderTool.java` generalises the
S2-only `AudioReferenceGenerator` test utility to all three loaders. For one
SFX id it writes the full driver mix and an FM-muted (PSG-only) render as
16-bit stereo WAV, plus `<game>-<id>-psg-writes.txt` listing every PSG byte
with the number of output frames rendered before it landed (the driver is read
one frame at a time, so the position is exact). Invocation:

```
java -cp target/classes:$(mvn -Dmse=off dependency:build-classpath ...) \
  com.openggf.tools.audio.PsgSfxRenderTool --game s1 --rom /abs/s1.gen --sfx A0 --out /task/dir
```

Cross-check that the write log fully represents the engine path: for all nine
PSG SFX, the PSG-only WAV equals `(replay of the log through a fresh PsgChip) >> 1`
plus a constant 384 LSB from sample 16 onward (the 384 is the muted YM2612
path's resting level after `MASTER_GAIN_SHIFT`; the same constant is present
in the FM-only controls and is unrelated to the PSG). So the chip-level
comparisons below are comparisons of what the engine actually emits.

Analysis was pure Python 3 (numpy/scipy are not installed on this machine):
zero-crossing fundamental per write-delimited segment, RMS level per segment in
dB, band-limited transition counts, a radix-2 FFT for spectral peak / spectral
flatness and for cross-correlation lag search (±2048 ticks over a 32 768-tick
window), onset = first sample deviating from the pre-write level by more than
1 % of full scale.

## 3. Vectors

Driver renders (the contract's stage-2 table; complete = the driver reported
the SFX finished):

| SFX | Frames @44.1k | PSG writes | Channels seen |
|---|---|---|---|
| S1 `A0` Jump | 19 845 | 46 | PSG1 |
| S1 `A4` Skid | 22 050 | 113 | PSG2 + PSG3 alternating |
| S1 `CD` Switch | 2 205 | 7 | PSG3 |
| S1 `B6` Spikes Move | 14 700 | 66 | noise `E7`, tone-2 linked |
| S2 `A0` Jump | 19 845 | 50 | PSG1 |
| S2 `A4` Skidding | 22 050 | 113 | PSG2 + PSG3 |
| S2 `BC` Spin Dash Release | 63 945 | 123 | FM5 + noise `E7` swept via tone 2 |
| S3K `62` Jump | 19 845 | 52 | PSG1 |
| S3K `36` Skid | 22 050 | 113 | PSG1 + PSG2 |

Synthetic chip-level scripts (the contract's stage-1 list): tone period
`0x200` at attenuation 0; periods 0, 1, 2, `0x3FF`; white noise ÷16 and ÷64;
white and periodic noise linked to tone 2 = `0x40`; periodic ÷16; a noise
register rewrite mid-stream (`E4` → `E6` → `E7` → `E5`); a 16-step attenuation
sweep on tone 0 and on the noise channel; three simultaneous tones; the ROM
`silenceAll` sequence followed by a tone; and tone 2 = 1 with linked white noise.

## 4. Results — tone channels

**Pitch.** Every write-delimited segment with a single audible tone channel was
measured by zero crossings and compared with `f = 3 579 545 / (32 × N)` from the
period in the write log. Tolerance: ±0.5 % of expected (zero-crossing
resolution on a 735-sample segment is ≈0.3 %), and new-vs-reference within
0.3 % of each other. Every one of the 20 (A0) + 22 (S2 A0) + 22 (S3K 62) +
15 (A4/36 tone segments) + 1 (CD) segments passed; worst cases at 44.1 kHz:

| Vector | N | expected Hz | reference Hz | new Hz |
|---|---|---|---|---|
| S1 A0 seg 1 | 320 | 349.56 | 349.50 | 349.50 |
| S1 A0 seg 3 | 231 | 484.25 | 483.86 | 484.62 |
| S1 A0 seg 20 | 95 | 1177.48 | 1176.92 | 1178.77 |
| S1 CD | 31 | 3608.41 | 3605.07 | 3607.62 |
| S1 A4 PSG2 | 120 | 932.17 | 931.22–932.63 | 931.22–932.63 |
| sweep | 256 | 436.96 | 436.83–437.05 | 436.83–437.05 |

At tick rate, tone segments cross-correlate with the reference at 0.997–0.999
with a lag of exactly **1 tick** (GPGX rounds a write time up to the next tick;
the new core applies it on the boundary), which is 4.5 µs and constant.

**Attenuation ladder.** Tone-0 sweep, RMS per 4410-sample step, reference /
new (dBFS of each core's own scale): −5.87/−5.87, −8.12/−8.12, −10.12/−10.12,
−12.14/−12.13, −14.13/−14.12, −16.12/−16.12, −18.13/−18.13, −20.14/−20.13,
−22.12/−22.11, −24.14/−24.13, −26.13/−26.13, −28.13/−28.11, −30.16/−30.12,
−32.15/−32.12, −34.16/−34.12, then off. Steps are 2.00 ± 0.02 dB in both cores;
new-vs-reference agrees within **0.04 dB** everywhere (tolerance 0.1 dB: the
reference truncates `2800 × 10^(−A/10)` to `uint16` while the new core rounds
`8191 × …`, worst case 0.02 dB). The best-fit gain of new against reference over
whole tone vectors is 2.917–2.924 against the ideal 2.925, residual −22 to −30 dB
relative to signal — i.e. the same band-limited waveform.

**Onset timing.** For every write group in every tone SFX the first sample that
deviates from the pre-write level is the same in reference and new (e.g. S1 A0:
739/739, 4407/4407 … 19842/19842; 20 of 20 groups, all three games). The
`develop` control is 7 samples *early* on the first note (732) because it
starts its counter differently; the new core does not have that artefact.

**DC.** Whole-stream mean: reference 0.000, new 0.000–0.018 LSB; the `develop`
control carries 31–345 LSB of DC (a pre-existing defect the rewrite removes).
The decay after a channel is silenced (the delta buffer's high-pass discharging
the last unipolar step) has identical RMS in reference and new (e.g. A0 tail
−18.81/−18.81 dB, A4 tail −31.11/−31.25 dB, sweep tail −46.99/−46.95 dB).

**Stereo.** Left == right for every sample of every render in every core.

**Start phase (minor, not a defect).** Where a tone's period is written while the
channel sits at period 0 — every SMPS note-on — GPGX has been toggling its
flip-flop every tick since reset, so whether its first flip after the write is
high→low or low→high depends on the parity of ticks elapsed since power-on.
The new core always starts high and flips one period later (spec §3.2/§9.3).
The result is that some channels come out inverted relative to GPGX: S1/S2 `A4`
and S3K `36` PSG3 segments correlate at −0.985 at lag 0 and +0.961 at lag 136
(= half of period 135); the synthetic `silenceAll`-then-tone vector inverts
(gain −2.894) while the otherwise identical `A0` Jump does not (+2.917). A
half-period phase offset on a square wave is inaudible in isolation and is not
adjudicable against GPGX, whose own phase is a reset-parity accident. It would
only matter for two PSG channels sounding together, and there it is the
hardware's phase that is unknown, not the model's.

## 5. Results — noise channel

**Level ladder.** Noise sweep, reference / new: −6.36/−6.35, −8.58/−8.58,
−10.57/−10.57, −12.54/−12.54, −14.56/−14.56, −16.56/−16.55, −18.51/−18.50,
−20.55/−20.55, −22.53/−22.52, −24.56/−24.56, −26.54/−26.54, −28.53/−28.52,
−30.54/−30.50, −32.56/−32.53, −34.51/−34.48 dB: 2 dB steps, agreement ≤ 0.04 dB.

**Character.** White ÷16, 2 s: band-limited transition count 7018 (ref) vs 7027
(new), spectral peak bin 2153.3 Hz in both, spectral flatness equal to three
decimals; white ÷64: 1742 vs 1745 transitions, peak 538.3 Hz in both. Periodic
÷16 and periodic-linked-to-tone-2 = 0x40 show the expected single line at the
shift rate ÷16 (437 Hz / 109 Hz) in both cores.

**Sequence identity.** At tick rate the new core's noise output cross-correlates
with the reference at **0.993–0.999** at a *constant* lag per vector: 33 ticks
for ÷16 (white, periodic, sweep, and the first segment of the rewrite vector),
129 ticks for tone-2 = 0x40 (white and periodic), 5 ticks in `B6`'s first two
segments (tone 2 = 22 and 6), 14 ticks throughout `BC`, and 1 tick after a
mid-stream noise-register rewrite. So the LFSR taps, width, reset value and
positive-edge clocking produce the *same bit sequence*; only its phase differs,
and the offsets are exactly explained:

- 33 = 32 + 1 and 129 = 128 + 1: GPGX resets with polarity −1 and a zero
  counter, so it takes a positive edge — and shifts — at time 0; the new core
  starts high and takes its first positive edge one full noise period later
  (spec §7). A one-period offset from power-on, never re-introduced (the
  post-rewrite lag is 1).
- 5 and 14: on a noise-control write with `rr = 11` GPGX copies tone 2's
  counter phase into the noise counter; the new core keeps the noise counter's
  own phase (spec §10.13/§10.14). Sub-period, constant, inaudible.

**Every-toggle mode** was not re-measured here; it has no GPGX counterpart and
is covered by the 2× transition property in `TestPsgChipReferenceParity`.

## 6. The divergence: linked noise with tone 2 period ≤ 1

`S1 B6` Spikes Move (a stage-2 target) ends with twelve one-tick notes whose
write groups are `F<n> C0 00 F<n>` — attenuation step, then tone 2 period **0**,
with the noise register still `E7` (white, tone-2 linked). At tick rate:

| Segment (tone 2 = 0, attenuation 0…11) | reference | new |
|---|---|---|
| RMS | −7.52 → −29.56 dB, stepping 2 dB | −45.7 / −26.2 → −46.2 dB (residual step only) |
| Transitions per 3729 ticks | 906–943 | 1 |

The reference produces white noise shifted every 2 ticks (GPGX treats a period
of 0 as 1 on the integrated part: `zeroFreqInc = 1 × PSG_MCYCLES_RATIO`, so the
noise clock linked to it has a positive edge every second tick). The new core
applies the Sega "period 0/1 holds high" rule to the noise clock as well
(`tickNoise`: `hold = linked && holdsHigh(period)` → no edge → no shift), so
the LFSR freezes and the tail is silent apart from the attenuation steps
discharging. The `develop` control behaves like the reference (−18.4 → −45.8 dB
in 2 dB steps). The synthetic `tone2 = 1, E7, F0` vector isolates it: reference
−7.60 dB with 8225 transitions per 223 722 ticks, new −∞ dB (all zero).

This is exactly the spec's open item §10.5 ("Noise counter at N₂ ≤ 1 in
tone-2-linked mode … still unmeasured on the Sega part"), which the spec
resolved by reasoning from the TI datasheet wiring. Three things argue the
other way and make this a release-affecting defect rather than a modelling
nicety:

1. **The reference core disagrees**, and so does MAME `sn76496.cpp` (fetched
   from `mamedev/mame` master for this check): for a Sega-style PSG it stores a
   register value of 0 as period 0 (`if ((m_register[r] != 0) || m_sega_style_psg)
   m_period[c] = m_register[r]`), derives `m_period[3] = m_period[2] << 1 = 0`,
   and its update loop then reloads a zero count every tick, i.e. shifts the LFSR
   every tick. Both emulators have been listened to against hardware by many
   people for these exact games; neither models a frozen LFSR.
2. **The ROMs use the state deliberately.** Rendering every SFX of all three
   games (284 ids; 84 write the PSG, 68 use the noise channel) and scanning the
   write logs for "noise audible, `rr = 11`, tone 2 ≤ 1" hits 22 SFX:
   S1 `A2`, `AA` Splash, `AB`, `AE` Fireball, `B6` Spikes Move;
   S2 `A2` Spike Switch, `AA` Splash, `AB` Swish, `AE` Lava Ball, `B6` Spikes
   Move, `D4` OOZ Lid Pop;
   S3K `42` Insta-Shield Attack, `47` Sand Wall Rise, `4E` Missile Explode,
   `66` All Spheres Collected, `70` Lava Ball, `7E` Ground Slide, `8D`, `97`
   Enemy Breath, `A0` Missile Shoot, `D1`, `DB` Water Skid.
   For S1/S2 `AA` Splash the condition holds for 46 consecutive write groups
   (frames 4409–80114, ≈1.7 s) — the whole body of the sound. Sound designers
   who wanted silence had `PSGNoteOff` (attenuation 0xF); writing the top
   note-table entry into tone 2 under a linked noise track is the SMPS idiom
   for the *highest* noise pitch, and it is what those SFX are.
3. **It is audible**, not a phase or DC subtlety: ≈−8 dBFS of hiss versus
   silence, for up to 1.7 s at a time, in some of the most frequent SFX in the
   games.

Recommended fix (for the core lane, not applied here): keep the constant-high
rule for the *tone* output, but let the noise counter in `rr = 11` mode reload
with `max(period, 1)` on the integrated part so its clock keeps running at one
tick per half-period — the reading under which the noise clock is driven by the
tone-2 counter's underflow rather than its held output latch. Then re-run
`syn-tone2-1-noise-linked`, `syn-noise-rewrite-midstream` segment 3 and
`s1-b6`: the expected result is the reference's ≈−7.5 dB / ≈920 transitions
per 3729 ticks, cross-correlating at ≥ 0.99 at a constant lag. Record the
choice in the spec's §10.5 and in `docs/status/known-discrepancies.md` if the
hardware question stays open.

## 7. No-regression control for the mix

The FM-only captures' SFX (`A6`, `AF`, `B5`, `BE`, `C6`, `CE`, `CF`) were
rendered through the same tool: each issues **zero** PSG writes, and the
FM-muted render is the constant 384 LSB YM resting level from sample 16 onward
with no other content — the PSG contributes nothing to those mixes on this
branch. The commit diff confirms `VirtualSynthesizer` is untouched
(`git diff develop HEAD --stat -- src/main/java/com/openggf/audio/synth/` lists
only `PsgChip.java` and the additive `clocksNeeded` in `BlipDeltaBuffer.java`),
and the previous core also emits exactly zero for a chip that has only seen
`silenceAll`, so those mixes are byte-identical before and after by
construction. Byte comparison against the captures themselves is not
meaningful (they have their own resampling and the FM core is out of scope).

## 8. Verdict against the contract's tolerances

| Criterion | Tolerance | Result |
|---|---|---|
| Tone fundamental vs ROM period | ±0.5 %; new vs ref ±0.3 % | pass, all segments, 3 rates |
| Attenuation ladder, tone and noise | 2 dB ± 0.1; new vs ref ± 0.1 dB | pass (≤ 0.04 dB) |
| Onset sample after each write | same sample ±1 | pass (identical), tone and noise |
| DC after high-pass | ≤ 1 LSB mean | pass (≤ 0.02) |
| Noise sequence | xcorr ≥ 0.98 at a constant lag; transitions ±2 %; same spectral peak | pass for ÷16, ÷32, ÷64 and tone-2-linked N ≥ 2 |
| Noise with tone-2-linked N ≤ 1 | as above | **fail**: silence vs white noise, 22 SFX affected |
| Stereo identity, chunking, rates | L == R; 44.1k/48k/tick | pass |

**pass = false.** Everything the contract's tolerances cover matches except one
behaviour, and that behaviour is audible in the target SFX and in a fifth of
the noise-carrying SFX of the three games.

## Appendix A — reference harness

Built with `gcc -O2 -o psg_ref psg_ref.c blip_buf.c` next to the two pinned
GPGX files. `shared.h` stub:

```c
#include <stdint.h>
#include <string.h>
typedef uint8_t uint8; typedef uint16_t uint16; typedef uint32_t uint32;
typedef int8_t int8; typedef int16_t int16; typedef int32_t int32;
#include "blip_buf.h"
#include "psg.h"
struct harness_snd { blip_t* blips[3]; };
struct harness_config { int hq_psg; };
extern struct harness_snd snd;
extern struct harness_config config;
#define save_param(p,s) do { memcpy(&state[bufferptr], (p), (s)); bufferptr += (s); } while (0)
#define load_param(p,s) do { memcpy((p), &state[bufferptr], (s)); bufferptr += (s); } while (0)
```

`psg_ref.c`:

```c
#include <stdio.h>
#include <stdlib.h>
#include "shared.h"
struct harness_snd snd;
struct harness_config config;
#include "psg.c"
#define MCLK 53693175.0
int main(int argc, char** argv) {
  double rate = argc > 1 ? atof(argv[1]) : 44100.0;
  snd.blips[0] = blip_new(8192);
  blip_set_rates(snd.blips[0], MCLK, rate);
  blip_clear(snd.blips[0]);
  config.hq_psg = 1;
  psg_init(PSG_INTEGRATED); psg_reset(); psg_config(0, 100, 0xFF);
  char cmd[16]; long a, b; static short out[2 * 8192];
  while (scanf("%15s", cmd) == 1) {
    if (!strcmp(cmd, "hq")) { scanf("%ld", &a); config.hq_psg = (int)a; }
    else if (!strcmp(cmd, "w")) { scanf("%lx", &a); psg_write(0, (unsigned)a); }
    else if (!strcmp(cmd, "cfg")) { scanf("%ld %lx", &a, &b); psg_config(0, (unsigned)a, (unsigned)b); }
    else if (!strcmp(cmd, "r")) {
      scanf("%ld", &a);
      while (a > 0) {
        int n = a > 4000 ? 4000 : (int)a;
        int clocks = blip_clocks_needed(snd.blips[0], n);
        psg_end_frame((unsigned)clocks);
        blip_end_frame(snd.blips[0], (unsigned)clocks);
        int got = blip_read_samples(snd.blips[0], out, n);
        for (int i = 0; i < got; i++) printf("%d %d\n", out[2*i], out[2*i+1]);
        a -= got; if (got == 0) return 1;
      }
    } else return 1;
  }
  return 0;
}
```

The Java side is the same script language driving `new PsgChip(rate,
INTEGRATED)` with `setNoiseShiftOnEveryToggle(false)` and printing
`renderStereo` output; the `develop` control is the pre-rewrite class renamed
to `OldPsgChip` in package `com.openggf.audio.synth` with `setHqMode(true)`.
Scripts are the write logs from §2 turned into `r <gap>` / `w <byte>` lines
plus 0.1 s of tail; for the tick-rate run every `r` count is scaled by
223 721.5625 / 44 100.

## 9. Round 1 re-run: linked noise at tone 2 period ≤ 1 (fix applied)

`PsgChip.tickNoise` now reloads the tone-2-linked noise counter with
`max(N₂, 1)` on the integrated part — the noise clock follows tone 2's counter
expiry rather than its held output latch — while the tone channel keeps the
constant-high rule (spec §4.1 and §10.5 updated; `docs/status/known-discrepancies.md`
§31 records the choice while the hardware question stays open). The three
vectors named in §6 were re-rendered through the same reference harness
(Appendix A) and the same scripts, at tick rate and additionally at 8× tick
rate (1,789,772.5 Hz, every `r` count × 8). The 8× rate is needed for a
meaningful cross-correlation: at tick rate a shift every 2 ticks is a signal at
fs/4, where the two band-limiting kernels (GPGX `blip_buf` vs `BlipDeltaBuffer`)
differ in ringing and cap the correlation at ≈0.90 even for identical bit
streams — visible before the fix on the *unchanged* `N₂ = 22 / 6` segments
(0.9956 / 0.9855 at tick rate, 0.9972 / 0.9976 at 8×). At 8× the same 2-tick
square wave sits at fs/32, inside both kernels' pass-bands, and only the model
is being compared.

Tick-rate levels and transitions (reference / new, RMS in dBFS of each core's
own scale, transitions of the mean-removed signal per segment):

| Segment | reference | new (before) | new (after) |
|---|---|---|---|
| `syn-tone2-1-noise-linked` (`C1 00 E7 F0`, 223,722 ticks) | −7.59 dB, 55,909 | −∞, 0 | −7.59 dB, 55,935 |
| `syn-noise-rewrite-midstream` seg 3 (`E7`, tone 2 = 0) | −7.59 dB, 27,952 | −∞, 0 | −7.59 dB, 27,947 |
| `s1-b6` tone 2 = 0, attenuation 0 | −7.54 dB, 925 | −45.7 dB, 1 | −7.14 dB, 925 |
| `s1-b6` attenuation 1 … 11 (eleven steps) | −9.60 → −29.56 dB, 901–951 | −26.2 → −46.2 dB, 1 | −9.60 → −29.54 dB, identical counts |
| `s1-b6` release (attenuation 15) | −48.20 dB, 1 | — | −48.17 dB, 1 |

(The attenuation-0 segment of `B6` reads 0.4 dB apart, and the difference is
confined to its first 256 ticks — reference −8.25 dB, new −3.97 dB; every later
256-tick block agrees to within the block-to-block scatter and the transition
counts per block are identical. What differs there is the high-pass memory
each core carries into the segment: the preceding tone 2 = 1014 / 998 / 982
segments shift their LFSR at different times in the two cores, since the
power-on noise-clock polarity offset recorded in §5 (a half noise period)
scales with the period — ≈5 ticks at `N₂ = 6`, ≈1,000 ticks at `N₂ ≈ 1,000` —
so the two cores enter the fast-noise segment from different points of a slow
decay. That offset is pre-existing, documented, and untouched by this fix; the
cross-correlation is positive (0.9904 here, 0.9998–0.9999 from the next step
on), so the two cores are not inverted relative to each other.)

Cross-correlation at 8× tick rate, 32,768-sample windows, best lag searched
over ±6 ticks (±40 ticks where the documented power-on and re-phase offsets
demand it):

| Segment | xcorr | lag (ticks) |
|---|---|---|
| `syn-tone2-1-noise-linked` | 0.9985 | −3.5, constant |
| `syn-noise-rewrite-midstream` seg 1 (÷16 from reset) | 0.9946 | −33.5 (the §5 power-on offset) |
| `syn-noise-rewrite-midstream` seg 2 (÷64) | 0.9980 | −1.5 |
| `syn-noise-rewrite-midstream` seg 3 (`E7`, tone 2 = 0) | 0.9927 / 0.9941 / 0.9945 skipping 32 / 128 / 1024 ticks | −23.5, constant (the §5 `rr = 11` re-phase) |
| `syn-noise-rewrite-midstream` seg 4 (÷32) | 0.9996 | −1.5 |
| `s1-b6` tone 2 = 22, 6 | 0.9972, 0.9976 | −5.5 |
| `s1-b6` tone 2 = 0, attenuation 0 … 11 | 0.9904, then 0.9998–0.9999 | −0.5, constant across all twelve |
| `s1-b6` release | 0.9998 | 0 |

The three `B6` segments with tone 2 = 1014 / 998 / 982 (2–5 transitions per
segment) carry too few events for a windowed correlation and are unchanged by
the fix (their path, `N₂ ≥ 2`, is not touched).

Verdict for the re-run: the §8 "Noise with tone-2-linked N ≤ 1" row moves to
**pass** — levels within 0.02 dB from the second step on, transition counts
identical or within 0.05 %, cross-correlation ≥ 0.99 at a constant lag on every
segment the finding named. Tests: `TestPsgChipHardwareBehaviour` (29, the
linked-noise vector now asserting one shift per two ticks at `N₂ = 1` and
`N₂ = 0` with tone 2 held high), the nine PSG-touching audio classes (119
tests) and the `-Pguards` profile.
