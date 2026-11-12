# Java Nuked run clocking: about 45% less FM rendering time

Date: 2026-11-11. Base: `eb08b374dccc2d2ddb93da0ab7fc2882dafe4bf5`
(`feature/ai-nuked-tables`, the folded operator tables, on develop
`c06efe67da766c1de1a68dfb327b85e400940fb8`). Branch:
`feature/ai-nuked-batch-kernel`.

## Decision and limits

The accurate core gains `NukedOpn2.clocks(count, pinSums)`: it advances the
chip by `count` internal cycles and leaves the state and the summed pins that
`count` calls of `clock` leave. `clock` is unchanged and stays the
function-for-function port that the pinned-C cycle scripts certify.
`Ym2612Chip` hands `clocks` every stretch of cycles on which the adapter has
nothing of its own to do. Rendered PCM, status reads, snapshots and rewind
state are identical; no fidelity mode, clock skipping or native code is
involved, and Java Nuked remains the accurate core.

This is a steady-state rendering gain on HotSpot. It is not a gameplay FPS
claim, and GraalVM native-image, lower-end CPUs and other JVMs are unmeasured.
The warm-up cost described below is real and is the price of the change.

## What `clocks` does

`OPN2_Clock` (`ym3438.c:1209`) spends most of its instructions moving the
chip-wide scalars through the state struct and re-testing conditions that are
almost never true. `clocks` takes the cycles on which none of the rare
conditions holds through one loop nest, `clockBatch`, and every other cycle
through `clock`:

- **Eligibility** is decided once per run: no strobe waiting for `doIo`, no
  LSI test bit, no CSM, and the latched FM data write has nothing left to
  change. `doRegWrite` re-applies a latched data byte to its slot or channel
  register on every pass of the sequencer; once the register holds the decoded
  value every later pass stores the same value, so `latchedWriteApplied`
  compares instead of storing and the loop carries no register decode. A write
  that has not landed yet is clocked singly until it has (at most 12 cycles).
- **Scalars** the stages hand to each other live in locals for the run.
- **`doIo`, the busy counter and `status_time`** do not feed the pipeline and
  are advanced by `count` after the loop. **Timers** are clocked on cycles 1
  and 2 and while an overflow, load or reset is in flight; they change nothing
  otherwise.
- **Order.** The cycles are taken in chunks of at most 20, and within a chunk
  each stage group runs for all cycles of the chunk before the next group
  starts: key-on latch; envelope generator with the `OPN2_Clock` head counters;
  operator and channel output; phase generator. A stage works on a slot a
  fixed distance behind the cycle counter, so two stages meet on a slot at a
  fixed cycle offset. The stores that cross groups (`eg_kon_latch`, `eg_out`,
  `pg_reset`, `pg_phase`) would be seen too early only by a stage instance 20
  to 24 cycles away, which a 20-cycle chunk never contains. The derivation is
  in the `clockBatch` Javadoc. The bound is tight: a chunk of 21 fails the
  equivalence test.
- **`fmPrepare` and the sign of `fmGenerate`** use masks instead of branches;
  with test bit `mode_test_21[4]` off, `~output + 1` followed by
  `SIGN_EXTEND(13)` is plain negation because `output` is at most `0x1ffc`.

`Ym2612Chip.clockRun` computes how many cycles ahead are quiet for the
adapter (to the end of the output frame, to the next step of the DAC cadence,
to the cycle a waiting strobe goes out) and runs them as one `clocks` call.
Mutes, the interpolated DAC write and the native-compatible output stage keep
the single-cycle path.

## Measurement

AMD Ryzen 9 9950X, Linux, OpenJDK 25.0.5.0.3, CPU affinity 10, a shared
desktop under load (load average about 10, some swapping), not a reserved
host. Four paired fresh-JVM forks per arm, order reversed between adjacent
rounds. Values are per-fork medians; the aggregate is their median.

### Core, six sustained voices with LFO

`tools/audio/fm-core-benchmark/JavaNukedBenchmark.java`, 53,267 native frames
per iteration, 40 warmups, 9 timed iterations; ns per native stereo frame.
Every process printed checksum `11377993598302072597`, zero snapshot errors
and 114 changed frames under the key-off control.

| Arm | Fork medians | Aggregate |
| --- | --- | ---: |
| develop `c06efe67d`, `clock` | 876.12, 880.60, 895.68, 876.64 | 878.62 |
| base `eb08b374d`, `clock` | 842.24, 865.54, 844.91, 844.21 | 844.56 |
| this change, `clock` (`--single-clocks`) | 845.74, 846.96, 845.19, 853.99 | 846.35 |
| this change, `clocks` | 481.49, 495.77, 482.21, 481.72 | 481.97 |

`clocks` against its base: 42.9% less time, 1.75 times the throughput.
Against develop: 45.1% less time, 1.82 times the throughput.

### S3K music through the session stream

A scratch harness (not committed) loads the music with `Sonic3kSmpsLoader`,
plays it through `SmpsDriverTestAccess` (sequencer, FM, PSG, DAC, resampler,
accurate FM core) at 44,100 Hz in 256-frame reads, ten seconds per render, six
warmup and seven timed renders; ns per output frame. All arms of one song
produced the same PCM hash.

| Song | develop | base | this change | less time vs develop |
| --- | ---: | ---: | ---: | ---: |
| AIZ1 (music 1), PCM `150090146716224451` | 1374.6 | 1294.5 | 737.3 | 46.4% |
| HCZ1 (music 3), PCM `8053472638584901649` | 1384.9 | 1274.0 | 728.7 | 47.4% |

Fork medians, AIZ1: develop 1375.2, 1373.9, 1373.0, 1394.3; base 1283.8,
1357.0, 1290.9, 1298.1; this change 733.6, 808.0, 737.1, 737.5. HCZ1: develop
1416.1, 1371.3, 1398.5, 1350.2; base 1271.6, 1276.3, 1304.1, 1255.0; this
change 749.5, 720.5, 730.7, 726.7. In a profile of the AIZ1 render with this
change, 79% of samples were still inside `clockBatch`.

### Warm-up

`clockBatch` is one 3,900-bytecode method. C2 needs about half a second to
compile it, and recompiles it once or twice after the first uncommon traps.
Until then it runs as C1 code at about 2,700 ns per native frame, three times
the old steady state. With no warmup, 20,000-frame iterations in a fresh JVM
read 3,520, 2,780, ... for the first 0.6 to 0.8 seconds of wall time and 480
from then on; the per-cycle path reaches its 880 within 0.1 seconds. A JVM
that renders less than about a second of FM is therefore slower with this
change, and a game session pays roughly 15% of one core for its first second
of sound instead of 5%. Replacing the loop-invariant branches (channel 3 mode,
DAC enable) with a cycle bit mask and a window number removed two loop
unswitches and shortened the phase by roughly a fifth.

## Verification

- `TestNukedOpn2BatchClock`: two chips, the same random bus writes, test-pin
  changes and direct state edits, one advanced by `clock` and one by `clocks`
  in runs of 0 to 3,000 cycles; summed pins and the complete
  `NukedOpn2State` compared after every run, over all four chip-type values.
  The script mixes the registers games write with LSI test bits and CSM so
  both sides of the eligibility hand-over are crossed.
- `NukedOpn2ScriptRunner` now carries a shadow chip advanced by `clocks`
  through every script of the pinned-C suite and fails a script on any
  difference in sums or state.
- `TestYm2612ChipBatchClocking`: two adapters, one with runs switched off,
  receive the same writes, voices, DAC streams, mutes, forced silences, status
  reads and snapshot exchanges; rendered samples and snapshots compared at
  44,100 Hz and at the internal rate.
- Each comparison was broken on purpose before it was trusted. Against the
  core test: a wrong accumulator shift, a dropped `eg_read` store, a wrong slot
  offset, an off-by-one `status_time`, timers skipped on cycle 2, a chunk of
  21 and of 23, a dropped comparison in `latchedWriteApplied` (SL, `reg_a4`,
  `write_data`), a missing `lfo_pm` hand-over. All failed.

## Approaches measured and not kept

Instruction counts are from a `gdb` single-step trace of the C2 code; times
are core-benchmark ns per native frame on the same host.

- **One loop in `OPN2_Clock` order** (stages inlined, scalars in locals): 508.
  The trace showed 682 instructions per cycle at an IPC near 6, of them 244
  stack references and 61 bounds checks. Regrouping the stages into four loops
  gave 482 with the same instruction count; the gain is not explained by the
  count.
- **One packed working array** for all per-slot and per-channel state,
  gathered on entry and scattered on exit: 437 in 2,400-cycle runs, 541 in
  the 24-cycle runs the adapter makes. The copy costs about 100 ns per call.
  The loops still spent three instructions on each bounds check because C2
  cannot know the length of an array loaded from a field.
- **A separate loop for the head counters, timers and `updateLfo`**: 520,
  slower than leaving them in the envelope loop.
- **Wrap-free counted segments** so that slot indices are affine in the loop
  variable: 512. C2 did not hoist the range checks.
- **One method per stage group**: C2 compiles each in about 70 ms and the
  warm-up shrinks to about 0.4 seconds, but steady state is 590.

The remaining cost is instruction count, not a hotspot: about 330 instructions
per cycle in the envelope loop, 200 in the operator loop and 125 in the phase
loop. Larger gains would need the envelope stages restated around what
changes per frame rather than per cycle, which is how the fast core is built.
