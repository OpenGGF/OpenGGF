# Fast FM core: seam and clean-room techniques specification (2026-09-06)

Department problem P7. Motivation: whole-game benchmarks put FM/PSG synthesis
at 90–97 % of frame CPU (0.85–0.93 ms per frame on this host); the Java
Nuked-OPN2 port costs about 930 ns per output frame against roughly 170 ns for
register-level designs ([2026-09-04 FM core performance exploration](../research/audio/2026-09-04-fm-core-performance-exploration.md)).
The retired Java core was a port of GPGX `ym2612.c` (non-commercial licence)
and is not resurrected or read.

## Clean-room protocol

- **Spec author (BG)** surveyed public documentation and permissively licensed
  cores and describes techniques here in words. No source code from any core
  appears in this document.
- **Implementer (CS)** writes `com.openggf.audio.synth.fast.FastYm2612Dsp`
  from this document plus public hardware documentation only: the Yamaha
  YM2608/YM2612 manuals, Nemesis's SpritesMind write-ups
  (`gendev.spritesmind.net/forum/viewtopic.php?t=386`, in particular the
  envelope-generator and phase-generator posts), and the jt12 copy of
  Nemesis's header notes. The implementer does not open ymfm, MAME, Genesis
  Plus GX, fmgen, BlastEm, the retired core, or the in-tree Nuked port.
- **Oracle**: the in-tree Nuked-OPN2 port stays the accurate core and the test
  oracle; the fast core is judged by tolerance tests against it, not by
  reading it.
- Record the sources actually consulted in the class header.

## Seam (delivered with this document)

| Piece | Role |
| --- | --- |
| `FmChip` | The chip surface `VirtualSynthesizer` drives: writes, DAC, mutes, render, mutation backup, snapshot, SFX admission. Implemented by `Ym2612Chip` (accurate) and `FastYm2612Chip` (fast). |
| `fast.FmDsp` | The DSP contract: `reset`, `writeRegister(port, register, value)`, `renderFrame(int[6])` (one internal frame, six pre-pan channel outputs in `-8192..8191`, channel 6 = DAC sample while `0x2B` bit 7 is set, no allocation), `readStatus`, `copyStateTo`, `newInstance`, value `equals`/`hashCode`. |
| `FastYm2612Chip` | Facade: pending-write queue flushed at render (so SFX admission can withdraw writes), B4–B6 pan masks, per-channel mute, 3/4 scaling to the mixer's 6144 full scale, DAC streaming as `0x2A` writes at the ROM byte cadence, `BlipResampler` to the output rate, snapshots and rewind. |
| `fast.FastFmCores` | Binding point; `bind(Supplier<FmDsp>)` from the DSP's static wiring. Selecting `fast` without a binding fails at construction. |
| `FmCoreSelection`, `audio.fmCore` | `accurate` (default) or `fast`; read once per physical device. `PhysicalChipCapture` refuses fast snapshots. |

Default stays `accurate` until the tolerance tests pass and the parity/oracle
tests that must pin the accurate core have been enumerated; flipping the
default is a separate commit.

## Hardware facts the DSP must honour (public documentation)

- 6 channels × 4 operators, 24 operator slots serviced per internal frame; the
  frame rate is the FM clock (master / 6) divided by 24 = 53 267 Hz NTSC.
- Phase generator: 11-bit F-number, 3-bit block, 3-bit detune, 4-bit multiple;
  20-bit phase accumulator, top 10 bits address the sine quarter-wave; key code
  (5 bits) derives from block and the top F-number bits and drives detune and
  rate scaling. Channel 3 special mode gives each operator its own
  F-number/block; CSM keys channel 3 on from timer A overflow.
- Envelope generator: 10-bit attenuation (0 = loudest, 0x3FF = silent),
  0.09375 dB per step (bit 9 ≈ 48 dB), so the 12-bit operator attenuation is
  the envelope value shifted left by two plus the log-sine term. Rate =
  2·R + Rks (rate scaling from key code shifted by 3 − RS), R = 0 forces
  rate 0, cap 63. The EG is clocked at master/432, one tick every three
  internal frames (Nemesis's later digital measurements, thread page from
  post 405, supersede the earlier master/351 figure). A per-rate counter
  shift (11 down to 0 by rate/4) decides how often a rate updates and an
  8-entry increment pattern per rate supplies the step; these patterns are
  measured hardware behaviour (the early text tables in the thread were of
  MAME provenance and are not to be imported; derive the pattern from the
  description: below rate 48 the pattern repeats by rate mod 4, above it the
  base step doubles every four rates and rates 60–63 step by 8). Attack:
  `attenuation += ((~attenuation) * increment) >> 4` (moves toward 0);
  entering attack at rate 62 or 63 zeroes the envelope, and changing to 62/63
  during attack stalls it. Decay, sustain and release are linear
  `attenuation += increment`. Sustain level is 4 bits in 3 dB steps with 15
  meaning code 31 (992); release rate is `2·RR + 1`.
- SSG-EG: enable, attack (inversion at decay start), alternate (toggle
  inversion each pass), hold; while enabled the decay-side increment is
  multiplied by 4, the inversion is `(512 − attenuation) & 1023` applied
  before TL and AM, and the envelope restarts/inverts at 0x200.
- Operator: input phase + modulation (10 bits) → quarter-wave log-sine table
  (256 entries, 12-bit output) → add envelope attenuation + total level
  (7 bits, 0.75 dB per step) + AM → exp/power table (256 entries, 11-bit
  mantissa, exponent from the integer part) → 14-bit signed operator output.
  Yamaha's trick: attenuations add in the log domain so there is no multiply.
- Feedback on operator 1: average of its two previous outputs, shifted by the
  3-bit feedback register (0 = off, 1..7 = π/16 .. 4π of phase).
- Algorithms: the eight standard OPN connection graphs; modulator outputs feed
  successors' phase input (summed 14-bit outputs, then halved into the 10-bit
  phase). Public-facade probes supersede the initial uniform previous-frame
  approximation: in this implementation's frame coordinates OP1→OP3 uses
  two-old output, OP3→OP4 current output, and other edges previous output.
  Carrier outputs sum into the channel output. These precise ages are probe
  results, not exact cycle labels from Sauraen's pipeline research.
- LFO: 3-bit rate; the manual's 3.98–72.2 Hz table is quoted for an 8 MHz
  clock and corresponds to 109/78/72/68/63/45/9/6 internal frames per step of
  a 128-step cycle (nearest integers, analytical). PM, measured black-box
  from the oracle with single-operator probe tones: a 32-position cycle (one
  position per four LFO steps) whose quarter is the stepped table
  {0,0,32,48,64,64,80,96} units at PMS 7, halving per PMS down to
  {0,0,0,0,4,4,4,4} at PMS 1, applied as the integer F-number offset
  `((fnum >> 4) * units) >> 7` (verified at top-seven-bit values 16, 38, 47
  and 58; PMS 7 peaks at +4.62 %, the manual's ±80 cents). A disabled LFO
  holds its counter at zero, the AM triangle's maximum attenuation; AM depth
  per channel (2 bits: 0, 1.4, 5.9, 11.8 dB) applied to operators with AM
  enabled, PM depth per channel (3 bits) applied as an F-number offset.
- Output: per-channel L/R enables (0xB4–0xB6 bits 7/6); the real chip's DAC is
  9-bit time-multiplexed, so per-channel 14-bit values are the digital output
  before that stage. The YM2612's "ladder effect" crossover distortion is an
  analog artefact; out of scope for the fast core.
- Timers A (10-bit) and B (8-bit) with status bits 0/1, reset/enable/load via
  0x27; DAC enable 0x2B bit 7, data 0x2A (unsigned byte, centred at 0x80).

## Techniques worth adopting (from the survey)

Ranked by cost/benefit for a register-level core:

1. **Log-domain operator math with two 256-entry tables** (ymfm, fmgen,
   hardware): quarter-wave log-sine (12-bit) and exp (11-bit mantissa). One
   table lookup, one add, one lookup and a shift per operator per sample; no
   multiply, no floating point. Generate the tables at class-load from the
   formulas, do not embed constants.
2. **Precomputed phase step per operator, refreshed on register write only**
   (ymfm: 768-entry block/keycode/fraction table plus a 32×4 signed detune
   table; fmgen: per-operator cached `pg_diff`). Recompute an operator's phase
   increment when its F-number, block, detune, multiple or the LFO PM input
   changes; otherwise the per-sample phase update is one add.
3. **Envelope evaluated every third internal frame, not every sample**
   (hardware EG clock master/351; ymfm approximates with a fixed divider and a
   2-bit fractional counter). The per-sample envelope cost becomes a table
   fetch of the cached attenuation.
4. **Cached per-operator parameters** (ymfm): effective rates after rate
   scaling, key-scale shift, total level, sustain level, and the AM enable, all
   recomputed on register writes. Per sample the operator reads plain ints.
5. **Silent-voice skipping** (ymfm `prepare`, fmgen channel bitmask): a channel
   whose four operators are all at maximum attenuation with key off and no
   pending key-on contributes nothing; skip its four operator evaluations that
   frame. This is the largest win on SMPS music, where several channels sit
   idle between notes.
6. **Branch-free routing** (ymfm): encode each algorithm's connections as a
   small bitfield/table so the per-sample loop indexes rather than switches;
   store operator outputs in a fixed 4-slot array and sum carriers by mask.
7. **AM/PM precomputed per LFO step** (fmgen): compute the LFO's AM offset and
   PM F-number delta once per LFO advance, then add per operator.
8. **State in flat primitive arrays** so `copyStateTo` is a handful of
   `System.arraycopy` calls and `equals` is `Arrays.equals`; no per-sample
   allocation; `renderFrame` writes into the caller's array.
9. **Attack curve by the hardware formula, not by an exponential table**:
   Nemesis's formula above is both accurate and cheap.

Not adopted: cycle-exact operator pipelining and bus timing (Nuked), the
YM2612 ladder-effect model (analog; the facade scale is defined at the
digital output), busy-flag timing (facade does not model it).

## Acceptance (status 2026-09-06: benchmark met, oracle 108/183 in tolerance, default unchanged)

- `TestFastYm2612Chip` (facade contract, scripted DSP) — delivered.
- Tolerance test to add with the DSP: render the `TestNukedOpn2BitExactScripts`
  register scripts through both cores at the internal rate; require per-script
  RMS error and cross-correlation bounds agreed in P7, not sample equality.
- `TraceBenchmarkTool` audio section ≤ 0.25 ms/frame with `fast` on the S1
  GHZ1 and S3K AIZ traces (current accurate: 0.85–0.93 ms).
- Ordinary and guard suites green with `audio.fmCore=accurate` (unchanged
  default), then an audit run with `fast` to list the tests that must pin
  `accurate` before any default change.

## Sources consulted by the spec author

- ymfm (BSD-3-Clause, Aaron Giles): `GeneralInfo.md`, `src/ymfm_fm.ipp` —
  read for techniques 1, 2, 3, 4, 5, 6.
- fmgen (cisc, MIT-compatible, via libOPNMIDI): `fmgen_fmgen.cpp` — read for
  techniques 1, 5, 7 and its 1024-entry sine / envelope step-table variant.
- Nemesis, SpritesMind thread t=386 (public research): envelope generator
  facts quoted above, corrected on peer review against the later digital
  measurements from post 405 onward (EG clock, SSG multiplier, SL 15). jt12
  `doc/nemesis/YM2612/YM2612.h`: bit widths and table sizes.
- libOPNMIDI README: core inventory and licences (MAME GPL-2, Nuked LGPL-2.1,
  GENS LGPL-2.1, ymfm BSD, fmgen MIT-compatible).
- Wikipedia "Yamaha YM2612": 9-bit DAC, YM3438 differences.
- Not read for this document: MAME `fm.c`, Genesis Plus GX, BlastEm, the
  retired OpenGGF core, and the in-tree Nuked port's synthesis code.

## Phase-transition refinement (release branch)

Pitch changes must replace the increment contribution already included in the
cached phase. Isolated public-facade pitch-step probes establish lookahead
coordinates of 2/1/1/2 frames for logical OP1/OP2/OP3/OP4. The replacement
applies to every increment change, including F-number, detune, multiple and
LFO. Independent sequences across all six channels and two octaves verify
modulation continuity; rewind includes the additional OP1 history.

The non-ALT, non-hold SSG repeat modes hold phase at zero whenever attenuation
is at least 512, including attack. The initial attack-completion reset model
is superseded. All 16 SSG vectors meet the existing waveform/level bounds.
The release validation record tracks remaining failures; this design's earlier
acceptance counts describe development checkpoints, not final certification.
