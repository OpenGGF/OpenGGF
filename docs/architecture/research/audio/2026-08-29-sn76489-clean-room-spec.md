# SN76489-family PSG: clean-room hardware specification (Mega Drive variant)

Date: 2026-08-29
Scope: the programmable sound generator integrated into the Sega 315-5313 VDP (Mega
Drive / Genesis), which is a licensed reimplementation of the Texas Instruments
SN76489 family as first integrated into the Sega Master System VDP.

## Source rule

This document was written from public hardware documentation and general knowledge of
the chip only:

- Maxim, "SN76489" on SMS Power! (https://www.smspower.org/Development/SN76489) —
  the primary reference for the Sega-integrated variant, including the LFSR
  measurements.
- Texas Instruments, *SN76489 / SN76489A / SN76496 Programmable Tone/Noise Generator*
  datasheet — register formats, attenuator step, noise shift rates.
- Sega, *Mega Drive / Genesis Hardware Manual* — clock derivation, port addresses.
- Published hardware measurements of the SMS and Mega Drive PSG (SMS Power! forum
  measurements of the LFSR sequence and tone-period-0 behaviour).
- The SMPS sound-driver disassemblies under `docs/` (for the "why SMPS relies on it"
  paragraph only; those are game code, not emulator code).

No emulator source was consulted (not the engine's own `PsgChip.java`, not Genesis Plus
GX, not libvgm/MAME, not BizHawk). Points where memory of the public sources was not
certain enough to state as fact are listed under *Known ambiguities* and in the
open-questions list returned with this task, instead of being resolved by reading an
emulator.

## 1. Clocking

| Region | Master clock | PSG input clock (master ÷ 15) | Internal tick (input ÷ 16) |
|---|---|---|---|
| NTSC | 53,693,175 Hz | **3,579,545 Hz** | **223,721.5625 Hz** |
| PAL | 53,203,424 Hz | **3,546,893 Hz** | **221,680.8125 Hz** |

- The PSG input clock is the same 3.58 MHz / 3.55 MHz clock that drives the Z80.
- Everything the sound generators do — tone counters, noise counter, LFSR shifting —
  happens on the **÷16 internal tick**. Nothing observable changes between ticks except
  register contents (which are written asynchronously by the CPU and are sampled by the
  generators on the next tick).
- One tick is 16 input clocks = 4.470 µs NTSC. The tone frequency formula that follows
  from this is the datasheet's `f = clock / (32 × N)`: one counter expiry per `N` ticks,
  two expiries per waveform period.

## 2. Write protocol

The PSG has a single write-only 8-bit port. On the Mega Drive it sits inside the VDP:

| Bus | Address |
|---|---|
| 68000 | `$C00011` (byte write; `$C00010`–`$C00017` mirror, odd byte) |
| Z80 | `$7F11` (VDP window `$7F00`–`$7F1F`, odd byte) |

Bit 7 of the byte selects one of two formats.

### 2.1 Latch/data byte (`bit 7 = 1`)

```
 7   6   5   4   3   2   1   0
 1   c   c   t   d   d   d   d
```

- `cc` — channel: `00` tone 0, `01` tone 1, `10` tone 2, `11` noise.
- `t`  — type: `0` = tone period (or noise control for channel 3), `1` = attenuation.
- `dddd` — 4 bits of data.

Effects of a latch/data byte, in order:

1. The **latch** is updated to `(channel, type)`. It persists until the next byte with
   bit 7 set. There is no timeout and it survives arbitrary numbers of data bytes.
2. The data nibble is written:
   - type = tone period, channel 0–2: the **low 4 bits** (bits 3–0) of that channel's
     10-bit period register are replaced; bits 9–4 are unchanged.
   - type = noise, channel 3: bits 2–0 replace the 3-bit noise control register
     (`bit 2` = feedback mode, `bits 1–0` = shift rate). Bit 3 is ignored. The LFSR is
     reset (see §4.5).
   - type = attenuation, any channel: all 4 bits replace that channel's attenuation
     register.

### 2.2 Data byte (`bit 7 = 0`)

```
 7   6   5   4   3   2   1   0
 0   -   d   d   d   d   d   d
```

- Bit 6 is ignored.
- The write goes to whatever the latch currently names:
  - latch = tone period, channel 0–2: `dddddd` replaces the **high 6 bits** (bits 9–4)
    of that channel's period register; bits 3–0 keep the value the latch byte left.
  - latch = noise: bits 2–0 replace the noise control register (bits 5–3 ignored), and
    the LFSR is reset, exactly as for a latch byte to the noise register.
  - latch = attenuation: bits 3–0 replace the attenuation register (bits 5–4 ignored).

### 2.3 Consequences relied on by drivers

- A full tone period is normally written as **two bytes**: `1cc0dddd` then `00dddddd`
  (low nibble first, then high six bits). Because the two halves are written
  separately, the period register passes through an intermediate value between the two
  bytes. On hardware the generator can act on that intermediate value if a tick falls
  between the writes; an accurate emulator applies each byte immediately at its own
  write time rather than coalescing the pair.
- **Volume needs only one byte**, `1cc1dddd`. Writing a second byte after it is legal
  and simply rewrites the same 4 bits.
- Once a channel's tone period is latched, a stream of `00dddddd` bytes changes only
  the coarse period — this is used for cheap vibrato/portamento. SMPS drivers do not
  rely on this; they always send the pair.
- The noise register accepts either a `1110-mrr` latch byte or a data byte while the
  noise register is latched; **both** reset the LFSR.
- A `1111dddd` byte (noise attenuation) does **not** reset the LFSR.

### 2.4 Access timing

Writes are accepted at any time; the VDP does not stall the CPU for PSG writes. The
register update is visible to the generators from the next ÷16 tick. For an emulator
running at sample resolution this means a write must be timestamped in input-clock (or
at least ÷16-tick) units and applied to the generator state before the tick that
follows it — a write cannot be quantised to the output sample period without
audible error at the 1–2 kHz row rate SMPS uses for PSG envelopes and, especially,
for sample-style volume tricks (§3.3).

## 3. Tone channels (0, 1, 2)

Each tone channel has:

| Register | Width | Reset |
|---|---|---|
| Period `N` | 10 bits (0x000–0x3FF) | see §7 |
| Attenuation `A` | 4 bits | 0xF |
| Counter | 10 bits | — |
| Output polarity | 1 bit | high (see §7) |

### 3.1 Counter semantics

On every ÷16 tick:

1. The counter is decremented.
2. When it reaches zero it is reloaded with `N` and the output polarity is flipped.

Equivalently: the polarity flips once every `N` ticks, so the square wave has a
half-period of `N` ticks and a full period of `2N` ticks:

```
f = 3,579,545 / (32 × N)  Hz (NTSC)      f = 3,546,893 / (32 × N)  Hz (PAL)
```

| N | NTSC frequency | PAL frequency |
|---|---|---|
| 0x3FF (1023) | 109.35 Hz | 108.35 Hz |
| 0x1AC (428) | 261.36 Hz | 258.97 Hz |
| 0x010 (16) | 6,991.30 Hz | 6,927.53 Hz |
| 0x001 | (111,860.78 Hz nominal — see §3.2) | (110,840.41 Hz nominal) |

Changing `N` while a channel is sounding does not reset the counter or the polarity;
the new value is used at the next reload. (Whether a new `N` smaller than the
current count truncates the current half-period is a known ambiguity, §9.)

### 3.2 Period 0 and period 1 on the Sega-integrated variant

This is the most important behavioural difference between the Sega-integrated chip and
the discrete TI part.

- **Discrete TI SN76489/SN76489A/SN76496:** a period of 0 behaves as 0x400 (the 10-bit
  counter wraps), producing a 109.3 Hz tone one step below 0x3FF. Period 1 produces a
  flip every tick (an ultrasonic 111.9 kHz square wave, heard as silence with a DC
  level of half amplitude after any low-pass filtering).
- **Sega-integrated variant (SMS VDP, Mega Drive 315-5313):** a period of **0 or 1
  produces a constant output of "high" (+1)** — the polarity does not flip. The channel
  then emits a DC level equal to the attenuator's output for the "high" state.

The Sega behaviour is what makes the attenuator usable as a 4-bit DAC: set `N ≤ 1`,
then write attenuation values at sample rate. It is also why SMS and Mega Drive
titles that play PCM samples through the PSG work on Sega hardware and produce a
109 Hz drone on a discrete SN76489.

### 3.3 Why SMPS relies on it

The SMPS PSG note table (`PSGFrequencies` in `docs/s1disasm/s1.sounddriver.asm`,
the same shape in the S2 and S3K drivers) is generated as
`min(0x3FF, round(PSG_Sample_Rate / (f × 2)))` with `PSG_Sample_Rate = 223,721.56`,
i.e. exactly the ÷16 tick rate above. The table's top entries are generated from a
nominal frequency of **223,721.56 Hz**, which yields a period of **0 (or 1, depending
on how 0.5 rounds)** — deliberately the DC case. Those entries are the driver's
"highest note", and the Sonic 3 table uses the same trick at both ends: its bottom
octave is clamped to 0x3FF (109.34 Hz) and its top two semitones are the DC value.
When a track plays one of those notes (or when SMPS modulation/detune arithmetic
pushes a period to 0 or 1), the Sega variant produces a constant level at the
current envelope volume instead of a 109 Hz tone. Any emulator that implements the
TI wrap-to-0x400 rule will play an audible low buzz where the ROM plays a DC hold, so
the `N ≤ 1 ⇒ constant high` rule is **required** for SMPS parity, not optional.

SMPS rests (`.restpsg`) do not use this path; they set the period to -1 in the
track RAM and send attenuation 0xF (`PSGNoteOff`), so a rest is a true mute.

### 3.4 Output of a tone channel

The channel's contribution at any instant is:

```
out(ch) = polarity ? level[A] : 0
```

i.e. a **unipolar** square wave between 0 and `level[A]` (§5). It is not a ±1 signal;
the chip output is single-ended and positive. Emulators that model ±1 per channel are
adding a DC shift that the real chip does not have (see §6 for why this matters little
for AC content but matters for the DAC trick and for mixing).

## 4. Noise channel (3)

Registers:

| Register | Width | Reset |
|---|---|---|
| Noise control | 3 bits: `m rr` | see §7 |
| Attenuation | 4 bits | 0xF |
| Noise counter | 10 bits | — |
| Noise "tone" polarity | 1 bit | — |
| LFSR | 16 bits (Sega) / 15 bits (TI) | 0x8000 / 0x4000 |

### 4.1 Shift rate (`rr`, bits 1–0)

The noise channel has its own down-counter that works exactly like a tone counter
(decrement each ÷16 tick, reload and flip polarity at zero). Its reload value is:

| `rr` | Reload (ticks) | Datasheet name | NTSC shift rate |
|---|---|---|---|
| `00` | 0x10 (16) | clock ÷ 512 | 6,991.30 Hz |
| `01` | 0x20 (32) | clock ÷ 1024 | 3,495.65 Hz |
| `10` | 0x40 (64) | clock ÷ 2048 | 1,747.82 Hz |
| `11` | tone channel 2's period `N₂` | "tone 2 rate" | `3,579,545 / (32 × N₂)` |

The LFSR is **shifted once per full period** of that internal square wave, i.e. on
every **low-to-high transition** of the noise counter's polarity (one shift per two
reloads). That is why "÷16" in the register description corresponds to a shift every
32 ticks = 512 input clocks, matching the datasheet's `clock/512`.

In mode `11` the noise counter is clocked from tone 2's period register value but has
its **own** counter and polarity; it does not read tone 2's live counter. Tone 2 keeps
sounding normally (drivers usually mute tone 2 with attenuation 0xF while using it
this way — SMPS's "PSG noise" tracks do exactly that). If `N₂ ≤ 1`, the Sega
period-0/1 rule applies to the noise counter as well and the LFSR stops shifting
(known ambiguity, §9).

### 4.2 Feedback mode (`m`, bit 2)

- `m = 0` — **periodic ("synchronous") noise**: the bit shifted in is the bit shifted
  out (output bit). With the reset pattern of a single 1, the output is a single 1
  every 16 shifts (Sega) or 15 shifts (TI): a pulse wave with a 1/16 duty cycle at
  `shift_rate / 16`. SMPS uses this for the "tuned noise" bass/drum sounds.
- `m = 1` — **white noise**: the bit shifted in is the XOR (parity) of the tapped bits.

### 4.3 Shift register: Sega-integrated variant vs original TI part

| | Sega VDP PSG (SMS, Mega Drive) | TI SN76489 / SN76489A |
|---|---|---|
| Width | 16 bits | 15 bits |
| Tap mask (white) | `0x0009` (bits 0 and 3) | `0x0003` (bits 0 and 1) |
| Feedback insertion | bit 15 | bit 14 |
| Reset value | `0x8000` | `0x4000` |
| Output bit | bit 0 | bit 0 |
| Periodic-mode period | 16 shifts | 15 shifts |
| White-mode period from reset | **57,337 shifts** (the 0x0009 polynomial is not maximal; 0x8000 lies on a 57,337-state cycle, not the full 65,535) | 32,767 shifts (maximal) |

Shift step (Sega, white):

```
fb   = parity(lfsr & 0x0009)         // XOR of bit 0 and bit 3
lfsr = (lfsr >> 1) | (fb << 15)
out  = lfsr & 1
```

Shift step (Sega, periodic):

```
fb   = lfsr & 1
lfsr = (lfsr >> 1) | (fb << 15)
out  = lfsr & 1
```

The output level of the noise channel is `out ? level[A] : 0` — unipolar, like the
tone channels. The noise output is read from bit 0 **after** the shift (known
ambiguity, §9 — but see the test vectors, which only differ by one sample of delay).

### 4.4 Which bit is output

Bit 0 (the bit that will next be shifted out). Because the reset pattern is a single
1 at the top, the first 14 (Sega) or 13 (TI) outputs after a reset are 0 whatever the
mode. That silence-after-reset is audible when a driver resets the noise register at
a high rate; SMPS PSG SFX that retrigger noise every few frames rely on it being
exactly this long.

### 4.5 When the LFSR resets

- On any write that lands in the noise control register: a `1110xxxx` latch byte, or
  a `0xxxxxxx` data byte while the latch names the noise register — regardless of
  whether the value actually changed.
- On chip reset / power-up (§7).
- **Not** on noise attenuation writes (`1111xxxx`), not on tone writes, not when the
  rate changes because tone 2's period changed in mode `11`.

Resetting the LFSR does not reset the noise down-counter; the next shift happens at
the next rising edge of the existing noise square wave.

## 5. Attenuation

Each channel's 4-bit attenuation register `A` selects an output level in **2 dB
steps**; `A = 0xF` is fully off (not −30 dB — the attenuator is switched out and the
channel contributes exactly 0, so 0xF is a true mute with no DC).

```
level[A] = 10^(−2A / 20) × full_scale     for A in 0..14
level[15] = 0
```

| A | dB | linear | ×8191 | ×32767 |
|---|---|---|---|---|
| 0 | 0 | 1.0000 | 8191 | 32767 |
| 1 | −2 | 0.7943 | 6506 | 26028 |
| 2 | −4 | 0.6310 | 5168 | 20675 |
| 3 | −6 | 0.5012 | 4105 | 16422 |
| 4 | −8 | 0.3981 | 3261 | 13045 |
| 5 | −10 | 0.3162 | 2590 | 10362 |
| 6 | −12 | 0.2512 | 2057 | 8231 |
| 7 | −14 | 0.1995 | 1634 | 6538 |
| 8 | −16 | 0.1585 | 1298 | 5193 |
| 9 | −18 | 0.1259 | 1031 | 4125 |
| 10 | −20 | 0.1000 | 819 | 3277 |
| 11 | −22 | 0.0794 | 651 | 2603 |
| 12 | −24 | 0.0631 | 517 | 2067 |
| 13 | −26 | 0.0501 | 411 | 1642 |
| 14 | −28 | 0.0398 | 326 | 1304 |
| 15 | off | 0.0000 | 0 | 0 |

(Values are rounded to nearest; the scaled columns are conveniences for fixed-point
implementations, the linear column is the specification.)

Attenuation changes take effect immediately — they are not synchronised to the
÷16 tick or to the channel's polarity flip. A volume write in the middle of a "high"
half-period changes the output level mid-pulse. This is what the DAC trick (§3.2)
depends on.

The nominal step is exactly 2 dB on the datasheet; measured Sega chips deviate by up
to a few tenths of a dB per step, and the top step (0→1) is often reported slightly
larger than 2 dB (known ambiguity, §9). The 2 dB ideal is the specification here.

## 6. Output stage, DC level and channel summing

- The four channel outputs are **summed linearly** and appear on a single analogue
  pin. There is no per-channel panning and no stereo; on the Mega Drive the PSG
  output is mixed equally into both stereo channels of the YM2612/PSG mix.
- Each channel is unipolar (0 or `level[A]`), so the sum ranges from 0 to
  `4 × full_scale` and carries a DC component that depends on how many channels are
  "high". The AC-coupling in the console's audio path removes the DC, so it is not
  heard as such — but it does mean that:
  - a channel at `N ≤ 1` (constant high) with attenuation `A` raises the DC level by
    `level[A]`, which after AC coupling is heard only when it *changes* (the DAC
    trick);
  - muting a channel by writing 0xF while its polarity is high produces a step
    (a click) of `level[A]`; this is real chip behaviour and a well-known PSG artefact.
- **Idle level.** With all four attenuators at 0xF the pin sits at the 0 level.
  With attenuators open and `N ≤ 1` everywhere, it sits at `Σ level[A]`.
- **Relative loudness.** On the Mega Drive the PSG is mixed into the YM2612 output
  at a fixed analogue ratio in the console's mixer; a full-scale PSG channel
  (`A = 0`, one of four) is quieter than a full-scale YM2612 channel. The exact ratio
  varies by board revision and is not a property of the PSG (known ambiguity, §9).

For a bipolar emulator representation the recommended mapping is
`out = level[A] × (polarity ? +1 : −1) / 2` **plus** a constant `level[A] / 2`, i.e.
keep the unipolar form and let the output high-pass filter remove the DC, so the DAC
trick and the mute-click both come out right.

## 7. Reset state

After a hardware reset (and on power-up of the VDP):

| State | Value |
|---|---|
| Attenuation, all channels | 0xF (muted) |
| Tone periods | 0x000 (see ambiguity: some sources report 0x3FF or undefined) |
| Noise control | `m = 0, rr = 00` (periodic, ÷16) — i.e. register value 0 |
| Latch | tone 0 period |
| LFSR | 0x8000 (Sega) |
| Tone counters | 0 (reload on first tick) |
| Polarities | high |

Because every channel is muted, none of the other values are observable until a
driver writes them; every Mega Drive game's boot code and SMPS's `PSGNoteOff` /
silence-all path writes `$9F, $BF, $DF, $FF` (attenuation 0xF to all four channels)
before anything else, so the practical reset contract for an emulator is "muted, latch
= tone 0 period, LFSR = 0x8000".

The discrete TI part has no reset pin; its power-up state is undefined (it is the
reason the SMS BIOS and every cartridge mute the PSG first).

## 8. Timing granularity for an emulator

Facts an emulator has to respect, in decreasing order of audibility:

1. **All generator events happen on the ÷16 tick** (223,721.5625 Hz NTSC). A tone
   polarity flip, a noise counter flip, or an LFSR shift never falls between ticks.
   The tick grid is fixed relative to the input clock; it does not resynchronise on a
   write.
2. **Register writes are asynchronous** and are applied at their own time (input-clock
   resolution, but only the tick they precede matters). Two writes within one tick are
   both honoured; the generator sees the final register contents at the tick.
3. **Output sample rate ≪ tick rate.** At 44.1 kHz there are 5.07 ticks per sample
   (NTSC); at 48 kHz, 4.66. A tone at `N = 0x1AC` flips every 428 ticks = 6,848 input
   clocks = 84.37 output samples at 44.1 kHz, so the flip position within a sample
   drifts by 0.37 sample per half-period. Rounding flips to sample boundaries produces
   a jitter that is audible as pitch/phase noise on sustained PSG leads, and turns
   high notes (`N < 32`, where the half-period is under 7 samples) into aliased
   garbage. **Polarity flips must therefore be placed at their exact sub-sample
   position and band-limited** (BLEP/BLIT, or run the generators at the tick rate and
   decimate through a proper low-pass), never rounded to the sample clock.
4. **The noise shift rate at `rr = 00` is 6,991 Hz** — every 32 ticks — so at
   44.1 kHz an LFSR step happens every 6.3 samples; white noise emulated with a
   per-sample "did the counter cross" test aliases badly. The same sub-sample
   treatment as the tone flips applies (each LFSR step is a step in the output level
   whenever the output bit changes).
5. **Attenuation writes are instantaneous** and can arrive at up to the CPU's write
   rate; for the DAC trick the write rate is 8–26 kHz. They are level steps at the
   write time and need the same band-limiting treatment as polarity flips.
6. **Period 0/1 channels never flip**: an emulator must not generate a 111.9 kHz
   (`N = 1`) or 109 Hz (`N = 0` wrapped) tone on the Sega variant.

## 9. Test vectors

Generated with the reference procedure below; every value is reproducible from this
document alone.

### 9.1 LFSR sequences from reset

Each entry is the output bit (bit 0) read **after** each shift; the hex row is the
register content after that shift. Shift 1 is the first shift after reset.

**Sega (16-bit, taps 0x0009, reset 0x8000), white noise (`m = 1`), first 32 shifts:**

```
bits : 0000 0000 0000 0010 0000 0000 0001 0010
       (ones at shifts 15, 28 and 31)
state: 4000 2000 1000 0800 0400 0200 0100 0080
       0040 0020 0010 0008 8004 4002 2001 9000
       4800 2400 1200 0900 0480 0240 0120 0090
       0048 8024 4012 2009 1004 0802 0401 8200
```

**Sega, periodic noise (`m = 0`), first 32 shifts:**

```
bits : 0000 0000 0000 0010 0000 0000 0000 0010
       (ones at shifts 15 and 31; period 16)
state: 4000 2000 1000 0800 0400 0200 0100 0080
       0040 0020 0010 0008 0004 0002 0001 8000
       4000 2000 1000 0800 0400 0200 0100 0080
       0040 0020 0010 0008 0004 0002 0001 8000
```

**TI discrete (15-bit, taps 0x0003, reset 0x4000), white noise, first 32 shifts:**

```
bits : 0000 0000 0000 0100 0000 0000 0001 1000
       (ones at shifts 14, 28 and 29)
state: 2000 1000 0800 0400 0200 0100 0080 0040
       0020 0010 0008 0004 0002 4001 6000 3000
       1800 0C00 0600 0300 0180 00C0 0060 0030
       0018 000C 0006 4003 2001 5000 2800 1400
```

**TI discrete, periodic noise, first 32 shifts:**

```
bits : 0000 0000 0000 0100 0000 0000 0000 1000
       (ones at shifts 14 and 29; period 15)
```

Cycle lengths from reset: Sega white 57,337; Sega periodic 16; TI white 32,767; TI
periodic 15.

If an implementation reads the output bit *before* shifting instead, its bit stream
is the same sequence delayed by one shift with a leading 0 (reset value 0x8000 has
bit 0 clear). A test should therefore assert on the sequence with an explicit phase
convention rather than on absolute indices alone.

### 9.2 Attenuation table

The `linear` column of §5:

```
A   : 0      1      2      3      4      5      6      7
lin : 1.0000 0.7943 0.6310 0.5012 0.3981 0.3162 0.2512 0.1995
A   : 8      9      10     11     12     13     14     15
lin : 0.1585 0.1259 0.1000 0.0794 0.0631 0.0501 0.0398 0.0000
```

Invariant checks: `lin[A] / lin[A+1] = 10^(0.1) = 1.2589…` for `A` in 0..13;
`lin[10] = 0.1` exactly; `lin[15] = 0` exactly (not `0.0316`).

### 9.3 Polarity-flip timing

Starting from a reload of counter = `N` and polarity high at tick `t₀`
(NTSC input clock 3,579,545 Hz, tick = 16 clocks):

| N | Flip every | In input clocks | In µs | Half-periods per second | Output samples per flip @44.1 kHz |
|---|---|---|---|---|---|
| 0x1AC (428) | 428 ticks | 6,848 | 1,913.1 | 522.71 | 84.367 |
| 0x3FF (1023) | 1,023 ticks | 16,368 | 4,572.6 | 218.69 | 201.654 |
| 0x010 (16) | 16 ticks | 256 | 71.5 | 13,982.6 | 3.154 |
| 0x002 (2) | 2 ticks | 32 | 8.94 | 111,860.8 | 0.394 |
| 0x001 / 0x000 | never (constant high) | — | — | — | — |

Expected event sequence for `N = 0x1AC` after a write pair landing just before tick
`t₀` with the counter at 0: flips at `t₀ + 428`, `t₀ + 856`, `t₀ + 1284`, … ticks;
the waveform period is 856 ticks = 13,696 input clocks = 261.36 Hz.

Expected event sequence for the noise channel at `rr = 00` after a write pair:
counter flips every 16 ticks; LFSR shifts on every second flip (the rising one), i.e.
every 32 ticks = 512 clocks = 6,991.30 Hz; the first *non-zero* noise output after a
reset is at the 15th shift = 480 ticks ≈ 2.15 ms after the reset for white or
periodic mode alike.

### 9.4 Write-protocol vectors

| Byte sequence | Resulting state |
|---|---|
| `$8C $2A` | latch = tone 0 period; tone 0 `N = 0x2AC` (= `0x2A << 4 \| 0xC`) |
| `$8C` `$2A` `$1A` | tone 0 `N = 0x1AC` (second data byte replaces bits 9–4 only) |
| `$90` | tone 0 attenuation 0; latch = tone 0 attenuation |
| `$90 $0F` | tone 0 attenuation 0xF (data byte to an attenuation latch takes bits 3–0) |
| `$E7` | noise: white, tone-2 rate; LFSR = 0x8000; latch = noise |
| `$E7 $03` | noise: periodic, tone-2 rate; LFSR reset again by the data byte |
| `$FF` | noise attenuation 0xF; LFSR untouched |
| `$A0 $3F $BF` | tone 1 `N = 0x3F0`, then tone 1 muted; latch = tone 1 attenuation |
| `$C1 $00` | tone 2 `N = 0x001` → constant high output at tone 2's attenuation |

### 9.5 Reference generator

```python
def lfsr(width, taps, reset, white, n):
    reg, out = reset, []
    for _ in range(n):
        fb = bin(reg & taps).count("1") & 1 if white else reg & 1
        reg = (reg >> 1) | (fb << (width - 1))
        out.append(reg & 1)
    return out

sega_white    = lfsr(16, 0x0009, 0x8000, True,  32)
sega_periodic = lfsr(16, 0x0009, 0x8000, False, 32)
ti_white      = lfsr(15, 0x0003, 0x4000, True,  32)
level = [0.0 if a == 15 else 10 ** (-2 * a / 20) for a in range(16)]
```

## 10. Known ambiguities

Points where the public sources disagree, are silent, or where memory of them is not
certain enough to specify. Each should be settled by a hardware measurement (or a
documented, cited measurement), not by copying an emulator.

1. **Period 0 on the Sega variant: constant-high vs. "behaves as 1".** Maxim
   documents both 0 and 1 as producing a constant +1 on the SMS. Some measurements
   describe period 0 as "the counter wraps but the polarity latch is held", which is
   observationally identical. No known source claims 0 wraps to 0x400 on a Sega chip.
   This spec uses "0 and 1 both constant high".
2. **Period 0 on the discrete TI part:** the datasheet is silent; measurements report
   0 behaving as 0x400. Irrelevant to the Mega Drive but recorded because emulators of
   both chips exist.
3. **Output-bit sampling phase:** whether the noise output is bit 0 before or after
   the shift changes the stream by one shift (§9.1). The test vectors are stated for
   "after".
4. **New period smaller than the current count:** whether a tone counter that has
   already counted below the newly written `N` continues to zero (this spec) or is
   clamped/reloaded immediately. Audible only as a one-off half-period glitch on
   downward pitch changes.
5. **Noise counter at `N₂ ≤ 1` in tone-2-linked mode:** whether the Sega
   constant-high rule stops the LFSR clock (this spec) or the noise counter flips every
   tick. Audible on any SMPS PSG SFX that sweeps tone 2 to the top of the table while
   the noise channel is linked.
6. **Reset value of the tone period registers** (0x000 in this spec vs 0x3FF or
   undefined) — unobservable while muted, but visible if a driver un-mutes a channel
   before writing a period.
7. **Exact attenuator curve:** the datasheet's 2 dB steps versus measured Sega
   chips, where the 0→1 step and the last few steps deviate by a few tenths of a dB
   and the whole ladder is slightly non-monotonic in step size. Also whether
   `A = 14` is −28 dB or somewhat lower on the integrated part.
8. **Whether attenuation changes are truly asynchronous** or are synchronised to the
   ÷16 tick. Difference is at most 4.5 µs of timing.
9. **PSG-to-YM2612 mix ratio on the Mega Drive** (board-revision dependent; not a PSG
   property). Needed for output calibration, not for the core.
10. **Whether the `PSGFrequencies` top entry assembles to 0 or 1** (`round(0.5)`),
    which the S1 disassembly's macro leaves to the assembler. Either value produces
    the same output on the Sega variant, so only the register-readback value differs.
11. **Whether a data byte following a noise latch byte resets the LFSR when it writes
    the same value** — this spec says any write resets; a "reset only on change" rule
    has been suggested but not, to this author's knowledge, measured.
12. **The white-noise cycle length.** 57,337 is what the stated polynomial and reset
    value give; if the true hardware output were ever measured as a 65,535-cycle, the
    taps or width in Maxim's page would have to be revisited. The 0x0009 / 16-bit /
    0x8000 description is the one all published Sega measurements report.
