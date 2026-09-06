# Fast FM 0.6 delivery evidence

The combined fast FM candidate now passes all 178 supported scripts without
deferrals and all 280 focused checks. Final integrated suites, performance
measurements and delivery are pending; older counts below are development
checkpoints, not the current acceptance policy.

## Imported development evidence

The measurements below were reported by BG on `feature/ai-perf-p3-fixes`
at `1c653223a`; they do not certify the new 0.6 integration branch.
Fresh acceptance and integrated verification will be recorded separately.

## P7: clean-room fast FM core (third and fourth commits)

`TraceBenchmarkTool --mode update --warmup-frames 500 --measure-frames 3000
--fm-core <core>`, this host, same trajectory digest for both cores on each trace:

| Trace | Core | frame p50 | frame p99 | `audio` median | fps |
| --- | --- | ---: | ---: | ---: | ---: |
| S1 `ghz1_fullrun` | accurate | 0.985 ms | 1.578 ms | 0.936 ms | 982 |
| S1 `ghz1_fullrun` | fast | 0.214 ms | 0.717 ms | 0.170 ms | 4237 |
| S3K `aiz_completerun` | accurate | 0.949 ms | 1.786 ms | 0.881 ms | 1002 |
| S3K `aiz_completerun` | fast | 0.246 ms | 1.155 ms | 0.179 ms | 3348 |
| S1 `ghz1_fullrun` | fast, after output-identical optimisation | 0.164 ms | 0.428 ms | 0.137 ms | 5731 |
| S3K `aiz_completerun` | fast, after output-identical optimisation | 0.164 ms | 0.429 ms | 0.132 ms | 5644 |

The optimisation pass (cached effective rates, timer and released-operator
early-outs, an SSG-enabled slot mask, explicit per-algorithm routing in the
hardware evaluation order, no phase advance on silent channels) was proven
output-identical by the per-script FNV digest the oracle test now prints:
all 183 digests unchanged. A 2 ms JFR sample before the pass put the FM DSP at
about 70 % of the audio section, the PSG at 9 % and the resampler at 2 %.

Fidelity oracle (`TestFastFmCoreTolerance`, both cores fed the same register
script, mono sums compared DC-free by normalised cross-correlation with a
±64-frame lag search and RMS level ratio): after CS's hardware review
(keycode from the raw F-number, SSG boundary tested every frame with phase
reset only in the non-ALT repeat modes, LFO divider terminal counts) and the
oracle-established SSG-EG restart timing (a boundary restart's ALT toggle or
phase reset lands when the restarted attack completes, at once for rates
62–63; every SSG mode with a real attack moved from about 0.5 to 0.81–0.95),
and the decay-to-sustain transition as a level comparison (a D1R=0, SL=0
voice held at full level and made three S3K effects 1.5–2.7× too loud; the
cause was isolated with synthetic instrument variants), 129 of 183 scripts are
within correlation ≥ 0.9 and level ratio 0.8–1.25 or silent on both cores; all
SMPS music logs 0.967–0.986 and no effect is outside the level bounds any
more. The 54 deferred scripts are
enumerated in the test and reported as skipped, not passed; the open classes
are in the design document. The default core remains `accurate`.


## CS timer and facade corrections

CS release branch `feature/ai-fast-fm-release` starts at `f658b6fac` and
imports only P7 changes through BG `7474fa9fe` (local `30a6827c7`). Facade
fix `c979e5f82` isolates public DSP snapshots and corrects diagnostic bank
ports and elapsed clocks, with replay and transaction rollback checks.

The timer correction uses the [Sega YM2612 manual, page 12](https://www.smspower.org/maxim/Documents/YM2612):
18 and 288 microseconds per unit at 8 MHz correspond to 144 and 2304 input
clocks, hence 1 and 16 internal frames. The previous 3/48 multipliers mixed
this clock with the envelope tick. The public timer test observes both
periods and status acknowledgement. A second test reproduced a CSM note
stranded by stopping the timer immediately after overflow; pending key-off
now finishes even with the counter stopped or CSM disabled.

`mvn -Dmse=off -Dtest=TestFastFmTimersAndChannelThree,TestFastFmCoreTolerance test -B`
completed on the timer working tree: three new contract cases passed and
all 183 tolerance vectors executed, with the existing 54 deferrals still
reported in the class XML. CSM improved from correlation 0.352 to 0.997
(level ratio 0.943). Compared with the saved default-fast audit, **only the
CSM vector changed its PCM digest**; the other 182 stayed identical.
Removing the now-passing CSM deferral remains with BG's test ownership.

Four single-carrier channel-3 probes match the accurate core's frequency
crossing counts independently. A diagnostic copy of `ch3-special` changing
only B2 from FA (algorithm 2, feedback 7) to C7 (algorithm 7, no feedback)
passes the original thresholds at correlation 0.988 and level ratio 0.975.
This narrows the original composite failure to modulation; it does not
justify changing the verified channel-3 frequency mapping.

A proposed BG contour-only acceptance change (`f5393ea9e`) is **not imported**:
it needs negative controls for wrong pitch/routing and evidence that a lone
high-feedback write cannot weaken unrelated parts of a script. Current
release acceptance remains unresolved while the original deferrals remain.

## Default selection and benchmark integration

New configuration defaults and bundled YAML select fast; an explicit accurate
selection survives save/reload. Legacy direct synthesizer constructors retain
accurate selection, and `FmSfxRenderTool` now supplies it explicitly for oracle
exports. `PhysicalChipCapture` rejects fast snapshots. The focused invocation
`mvn -Dmse=off -Dtest=TestFastFmConfiguration,TestTraceBenchmarkToolArgs,TestPhysicalChipCapture,TestFastFmReleaseContracts,TestFastYm2612Chip test -B`
completed with 33 passing cases and no skips.

The baseline benchmark at `f658b6fac` reproduced zero measured frames on pass
one and a closed-ROM exception on pass two. The release branch imports BG's
prerequisite intent from `0c742ea32`: consume initial title-card requests like
the replay driver, and set the current ROM before constructing its audio
profile. A mistyped `--fm-core` is rejected instead of silently measuring the
accurate fallback. Unrelated P3/P5 allocation changes are not imported.

Fresh local benchmark commands use Java 21 and `TraceBenchmarkTool --trace
<trace> --mode update --warmup-frames 500 --measure-frames 3000 --iterations 3
--fm-core <core> --json <report>`. All twelve iterations completed, each
measuring exactly 3,000 frames. Per-iteration audio medians (milliseconds):

| Trace | Accurate | Fast | Trajectory digest, all six iterations |
| --- | --- | --- | --- |
| `ghz1_fullrun` | 0.9978 / 1.0028 / 1.0023 | 0.1481 / 0.1444 / 0.1414 | `f87df78271bbb0d7` |
| `aiz_completerun` | 1.0023 / 0.9873 / 0.9681 | 0.1495 / 0.1541 / 0.1576 | `aa99e56f4ccbf249` |

These measurements were compiled from `30a6827c7` plus the facade/default/
benchmark integration changes, before the timer correction. They are local
CPU measurements, not cross-platform or final-release certification. Raw
commands and reports are in the task worktree's `target/fast-fm-release/`.

Fresh baseline ordinary Maven summary: 16,687 tests, zero failures/errors,
23 skips; separate guards: 610, zero failures/errors/skips. The first
candidate audit reproduced one new failure (Blue Sphere diagnostic bank
reporting), subsequently fixed by `c979e5f82`. Its class XML also reports 54
fast-fidelity skips in addition to the 23 inherited skips; Maven's final
aggregate omitted the dynamic skips. Nested suite attributes and testcase
counts also differ. Compare identities and outcomes from individual cases,
not aggregate totals. Final candidate/integrated runs remain pending.

## LFO follow-up

BG `152051dd5` replaces the cents-based triangular pitch modulation with a
stepped, signed F-number offset measured using independent single-carrier
oracle probes at several F-numbers. A disabled LFO retains maximum amplitude
attenuation. The release branch imports that behavior and its diagnostic
frequency probe, while retaining the original waveform/level acceptance
criterion; the unvalidated contour fallback is excluded. The timer/CSM fix
remains present. Follow-up strict-vector verification is pending.

## Detune measurement and correction

An independent public-facade scan used an isolated OP1 carrier, MUL1, AR31,
all eight blocks and four representative F-numbers (`200`, `380`, `400`,
`480` hex), DT0..3, and 160,000 steady samples per arm. Linear regression
of interpolated rising-crossing times measures pitch; subtracting DT0 and
multiplying by `2^20/internalRate` recovers the detune increment. Maximum
rounding residual was 0.00554 phase-increment LSB. The original table had
43 differing entries; this correction uses the measured integers.
`TestFastFmDetune` independently compares positive and negative detune pitch
at five representative keycodes through public facades, without reading or
repeating either implementation's tables. The red run reproduced DT1 errors
at keycodes 12, 20 and 28 (1/2, 2/4 and 5/8 LSB respectively).

On `14779f2eb` plus the table and regression, the focused Maven run completed:
five detune and six facade/timer cases pass; the 183-vector tolerance class
still lists 51 deferrals and exposes one new enforced failure, `dt7-mul15`
(correlation 0.894). All 16 original algorithm/feedback vectors now meet the
unchanged waveform and level thresholds; `alg1-fb7` improves 0.104 to 0.924,
`alg2-fb7` 0.117 to 0.912, and `alg5-fb7` 0.444 to 0.929. LFO and remaining
effects still need work. This is intermediate evidence, not release approval;
the high-frequency composite failure must be resolved before integration.

## Bounded fractional waveform alignment

The integer-only lag search is sensitive to a fraction of an internal sample
when several high-frequency carriers are mixed. A separate test helper keeps
the original waveform correlation and unfiltered RMS bounds, retains the best
integer result, and refines at 1/16-sample steps within one sample of that lag
and the original ±64 bound. One constant shift applies to the whole waveform.
A 32-tap windowed-sinc interpolation uses zero-padded edges and the full original
overlap: no attack trimming, per-channel alignment, pitch changes or time warp.

Four helper controls pass: a known fractional delay of a three-tone mixture
is recovered; a semitone error, missing carrier and silence are rejected; the
search cannot escape its original bound. Independent digital-reference script
probes at `f495230d2` plus this helper give `dt5/6/7-mul15` correlations
0.9474 / 0.9481 / 0.9492 (previously 0.8986 / 0.8957 / 0.8942), all at lag
52.6875. Eight real scripts were also rendered with deliberately wrong pitch
and routing: none reaches 0.9 (maxima 0.1233 and 0.5351 respectively). Remaining
LFO, channel-3 and effect mismatches stay below threshold. This refinement does
not certify those cases. Delayed-accurate controls yield 1.0 on the detune
vectors, but only 0.4584 on `s2-sfx-bc`, so that effect needs separate timing
sensitivity investigation. Earlier exploratory probes trimmed 128 edge samples;
those exploratory values are not this helper's acceptance evidence.

## Modulation history and pitch transitions

On `9a5530b3f` plus this correction, public-facade controls distinguish the
modulation path ages and cached phase coordinates described in the design.
Algorithms 3–7 correlate approximately 1.000; algorithms 0–2 reach 0.962–0.974.
Channel-3 special mode and S1 C6 now meet the existing bounds. Correcting
pitch-transition phase clears S1 AC, S2 AC and S1 BE; S2 BC remains outside
at 0.230 (its feedback-zero-through-five controls reach 0.998). Sixteen
supported vectors still miss: eight LFO rates, LFO toggle, both DAC ramps,
pan/TL, S1/S2 CF, S2 BC and S3K 3C. Five test-register/bus/fuzz diagnostics
are outside the intended contract; their current default skips still require
separation from release acceptance.

`TestFastFmFrequencyTransitions` uses independently generated up/down pitch
sequences on all six channels at blocks 4 and 7 with feedback 5. Its twelve
waveform cases fail before the change and pass after it, with correlation
above 0.99 and raw level within five percent. A thirteenth continuation case
fails if the added OP1 history is omitted from snapshots and passes with
state copying. The completed Java 21 command was:

```sh
mvn -Dmse=off -Dtest=TestFastFmFrequencyTransitions,TestFastFmCoreTolerance,TestFastFmReleaseContracts,TestFastFmTimersAndChannelThree,TestFastFmDetune,TestFastFmWaveformAlignment,TestFastYm2612Chip test -B
```

It finished at 2026-09-06 04:56:40 BST with Maven reporting 193 tests and no
failures/errors. Individual tolerance XML contains 183 vectors, 157 passed
and 26 skipped; Maven omits those dynamic skips from its aggregate. This is
focused intermediate evidence, not final suite or release approval. Log:
`target/fast-fm-release/frequency-transition-green.log` in the release worktree.

Source boundary: implementation corrections were derived from public-facade
PCM measurements. No excluded emulator implementation file was opened. Web
search results for the public hardware forum did incidentally display mixed
posts containing emulator snippets; those snippets were not used to derive
code. Sauraen's [pipeline discussion](https://gendev.spritesmind.net/forum/viewtopic.php?start=780&t=386)
and [buffer discussion](https://gendev.spritesmind.net/forum/viewtopic.php?start=825&t=386)
support the existence of pipeline delays and modulation buffers. They do not
establish our exact Java per-edge sample ages: those are oracle-derived.
This records actual exposure rather than asserting a blanket certification.

## Carrier/channel output and key-on sampling

Public-facade isolated tones distinguish one older OP1 carrier value and one
additional frame of channel output history on zero-based channels 1/3/5.
Applying only the latter clears S1/S2 CF but independent six-channel mixtures
still fail. A second probe varies register-burst length through every internal
cycle residue and records only public physical bus strobes and PCM. Key-on
phase advances one step when its data strobe precedes the channel's sampling
slot; a strobe at cycle 24 crosses into the next frame. The facade now passes
that strobe offset through an optional DSP write overload. Untimed DSP clients
and forced status-read flushing retain their immediate convention. These
exact sample coordinates are measured oracle behavior, consistent with the
publicly described multiplexed accumulator; no emulator implementation is
read or imported.

On `ba09b680b` plus this correction, twelve independently generated carrier
and channel mixtures reach correlation above 0.995 with raw levels within
one percent. Six cases each exercise all 24 key-on write offsets and require
a common integer lag of -3, rather than independently accommodating changing
phase offsets. All eighteen cases fail before the correction. Three snapshot
continuation cases pass; deliberately omitting the channel-output history from
state copying fails all three at the first sample (expected -1552, actual 0).

The Java 21 focused command from the previous section, with
`TestFastFmOutputTiming` added, finishes at 2026-09-06 05:16:59 BST: Maven
reports 214 tests, zero failures/errors, while the tolerance XML still has
26 explicit dynamic skips. S1/S2 CF and DT5/6/7 MUL15 now correlate 1.000
using integer alignment. Fourteen supported corpus failures remain: nine LFO
cases, DAC enable/disable ramps, pan/TL, S2 BC and S3K 3C. Log:
`target/fast-fm-release/output-timing-green.log`. This is intermediate evidence;
final acceptance, suites and benchmarks remain pending.

## Frequency-register sampling

An isolated pitch-step scan on channels 0 and 5, four register slots and all
24 data-strobe offsets shows phase changes in exact one-increment units. The
measured frequency-sampling boundaries in logical operator order are
`{18, 6, 0, 12} + channel`. Writes now replace the cached phase contribution
according to that boundary, retaining the existing default refresh path for
LFO and other parameter changes. Independent regression sequences use a
different multiple and cover all six channels, all four operators and all
24 write offsets (576 combinations). Each channel case fails before the
correction and passes after it at correlation above 0.995.

At `e1127b1f9` plus this correction, the preceding focused command with
`TestFastFmFrequencySampling` added finishes at 2026-09-06 05:24:08 BST: 220
reported tests, zero failures/errors; the unchanged factory still reports
26 dynamic skips in its XML. S3K 3C improves from 0.844 to 0.937, with raw
level ratio 1.000, and no previously enforced vector regresses. Thirteen
supported corpus failures remain. Evidence:
`target/fast-fm-release/frequency-sampling-{red,green}.log` and the independent
`frequency-offsets/results.json`. Final release verification remains pending.

## Envelope-output sampling

Paired held/decaying public-facade tones show that OP2..4 expose a new decay
value one frame too early; OP1 already has its carrier history. Those three
operators now sample the pre-tick envelope on an envelope-update frame. The
cache is recorded only on envelope ticks and participates in reset and state
copying. A separate all-channel/all-carrier test derives attenuation from
paired PCM amplitudes. Its comparison accounts explicitly for the oracle's
48-unit mono output quantization and the fast facade's two-unit rounding.
An initial fixed two-attenuation-unit measurement bound falsely failed quiet
OP1 samples; correcting that measurement does not alter corpus acceptance.

At `b305e0013` plus this fix, the other 220 focused tests have no new failures;
the corrected six envelope cases pass in the completed command
`mvn -Dmse=off -Dtest=TestFastFmEnvelopeSampling test -B` at 2026-09-06
05:35:09 BST. Running those exact six cases with the previous DSP fails every
channel on the first tested OP2/OP3 decay boundary (approximately 40 versus
48 attenuation units). DAC-disabled improves 0.877 to 0.904; twelve supported
corpus failures remain. Logs: `envelope-sampling-green.log`,
`envelope-sampling-quantized-green.log`, and `envelope-sampling-negative/results.log`
under the task's `target/fast-fm-release/`. Final verification remains pending.

## FM/DAC output boundary

An independent sequence alternates an FM carrier and short DAC bursts. It
exposes the common three-frame FM delay before DAC selection: delaying the
whole mixed stream cannot preserve their relative timing. The FM pipeline
now retains those frames before channel-6 selection. Explicit common-latency
checks therefore move from -3 to zero; envelope comparisons use equal sample
indices. The pipeline cursor and samples participate in reset and snapshots.

Further public DAC steps sweep all 24 bus offsets for registers 2A and 2B.
They distinguish three output contributions: data admitted through cycles
4/5/6 yields a full/two-thirds/one-third changed sample, and later strobes
affect the next frame. The DSP preserves these contributions separately for
the current frame, including enable changes; untimed DAC streams retain
immediate semantics. The independent alternating-FM/DAC test initially
remained below its 0.995 bound (0.989) after adding only the FM delay, then
passes with DAC sampling modeled. The bound was not lowered.

At `7a946548c` plus this correction, the completed Java 21 focused command
from the previous sections passes all 227 reported tests at 2026-09-06
05:54:47 BST; the tolerance XML still reports its 26 intermediate skips.
An additional `mvn -Dmse=off -Dtest=TestFastFmOutputTiming test -B` completes
at 05:56:22 BST with all 25 cases passing, including exact public-output
equality for all 48 DAC register/offset combinations. Omitting the partial
DAC state from copying makes its continuation regression fail: expected
4096, actual 6144. Logs: `fm-dac-sampling-green.log`,
`dac-all-offsets-green.log`, and `dac-sampling-negative/results.log` under
the task evidence directory. DAC-enabled meets the unchanged corpus bound;
eleven supported failures remain (nine LFO, pan/TL and S2 BC).


## Scheduled register admission and feedback continuity

At `85cc35389` plus this correction, isolated public digital PCM exposes one
wrong pitch-transition sample even without feedback; feedback amplifies that
single error into a persistently different sequence. Scheduling increments,
TL and keys at operator boundaries replaces retrospective phase jumps and
the completed-audio delay. The envelope counter retains its pre-tick coordinate,
and instant attack holds decay until envelope key admission. S2 BC now
correlates 1.000, pan/TL 0.929, and S3K 3C 1.000. No enforced corpus vector
regresses. Nine LFO cases remain outside tolerance; the factory's 26 existing
dynamic deferrals are intermediate and must still be removed or explicitly
separated by supported scope before final release acceptance.

The completed Java 21 command at 2026-09-06 06:16:28 BST is:

```sh
mvn -Dmse=off -Dtest=TestFastFmFeedbackTransitions,TestFastFmOutputTiming,TestFastFmEnvelopeSampling,TestFastFmFrequencySampling,TestFastFmFrequencyTransitions,TestFastFmCoreTolerance,TestFastFmReleaseContracts,TestFastFmTimersAndChannelThree,TestFastFmDetune,TestFastFmWaveformAlignment,TestFastYm2612Chip test -B
```

Maven reports 236 tests with no failures/errors; the tolerance XML separately
contains 26 dynamic skips. Six new feedback cases cover 288 combinations
(all channels, all 24 padding offsets, held/decaying tones) and require every
sample to match after the public digital output's 48-unit mono quantization.
The previous DSP fails these tests. A subsequent output-timing-only run at
06:18:06 BST passes all 26 cases, adding snapshot/reset coverage with pending
operator writes. Deliberately omitting the scheduled state from copying fails
that exact test at sample 2: expected 3280, actual 968. Logs under the task's
`target/fast-fm-release/`: `scheduled-pipeline-green.log`,
`scheduled-snapshot-green.log`, `scheduled-snapshot-negative/results.log`,
and `feedback-transitions-red.log`. Final suites and benchmarks remain pending.


## Supported acceptance restored (LFO still red)

The fast factory now enforces all 178 supported scripts with the unchanged
waveform/level criteria and no deferrals. Exactly `bus-edges`, `fuzz-s0`,
`fuzz-s1`, `fuzz-s2`, and `test-regs` are outside the fast contract and run
only in the separate opt-in diagnostic factory:

```sh
mvn -Dmse=off -Dtest=TestFastFmCoreTolerance -Dopenggf.fastfm.only=bus-edges,fuzz-s0,fuzz-s1,fuzz-s2,test-regs -Dopenggf.fastfm.outOfScopeDiagnostics=true test -B
```

The accurate-core 183-script oracle is unchanged. The restored default fast
factory completes at 06:26:38 BST on `43c8ccf57` plus the test edit with
178 XML cases, exactly nine LFO failures, zero errors and zero skips. Surefire's
console collapses dynamic invocations into one failure; the XML and printed
script metrics retain the full evidence. The explicit five-script diagnostic
command completes at 06:29:14 BST with five cases and no errors or skips;
those are diagnostic executions, not fidelity acceptance.

An independent LFO test uses FNUM 977, block 5, PMS 1/3/7, all eight rates,
and enable/disable/re-enable intervals distinct from the corpus. Its eight
rate cases currently fail their zero-lag 0.995 correlation requirement; its
rewind case passes. Completed at 06:28:02 BST. These red checks explicitly
block integration until the pending LFO correction passes. Evidence: task
`target/fast-fm-release/{supported-acceptance-red,lfo-transitions-red}.{log,xml}`
and `out-of-scope-diagnostics.log`.


## Independent LFO and AM closure probes

Brainpipe BG measured periods `{108,77,71,67,62,44,8,5}`, a free-running
mask-containment prescaler, and fractional PM. CS independently predicted
new enable-time offsets with the same containment rule. Terminal-edge probes
then distinguished counter/control ordering; BG's final compare-before-advance
implementation also reproduces those edges. CS's independently derived
whole/half/quarter F-number partial-sum model reduced composite-PM phase-fit
residuals from about 0.0076 cycles to 0.0003; BG independently verified its
plateau table and 12-bit wrap before committing the PM correction. The
standalone probes used copied public-facade measurement helpers, without
reading the excluded emulator implementations.

Removing AM from task-local corpus copies brings all three sampled LFO
cases within tolerance, while removing PM leaves the failures. Paired held/AM
tones establish the symmetric `63..0,0..63` triangle, discrete AMS shifts and
operator output history. A prototype with those changes and the BG counter
snapshot passes all 178 supported scripts, no failures or deferrals. This is
combined prototype evidence, not yet the production branch's final gate.

The permanent AM regression independently varies all six channels, four
carriers and three depths (72 combinations), comparing attenuation intervals
that account for public 48-unit oracle and two-unit fast output quantization.
The original DSP fails all six channel cases. The combined prototype passes
all six plus rewind/reset; removing AM history from state copying then fails
the rewind test at sample zero: expected -1866, actual -1062. The initial
OP1-only snapshot did not retain a needed historical sample and correctly
failed to detect that mutation; using the OP4 carrier makes it effective.

Evidence under task `target/fast-fm-release/`: `lfo-prescaler-independent/`,
`lfo-pm-partials-probe/`, `lfo-am-sampling/`,
`lfo-am-current-delay12-variant/`, `lfo-am-snapshot-negative/`,
`am-modulation-red.log`, and `am-production-contracts.log`. The AM patch
requires the matching BG PM/counter correction before final acceptance.

The AM-only production patch passes all 80 non-LFO focused contracts at
2026-09-06 07:03:30 BST with Java 21: the preceding focused command omits
`TestFastFmCoreTolerance`, `TestFastFmLfoTransitions` and the AM regression
until the matching PM/counter correction is imported. This bounded check
is not substituted for combined acceptance.


## Combined production acceptance and global PM refinement

BG `d06f0385e` imports as `0c6c5639f` above CS AM `35120aa79`. Conflicts
were adjacent constants and release/status prose: both measured models were
retained. At that commit, the completed Java 21 combined focused run passes
274 cases, including the complete 178-script factory with zero skips, at
2026-09-06 07:06:13 BST (`combined-lfo-am-focused.log`).

A new high-FNUM guard then exposes one frame of late PM admission in OP1..3:
FNUM 2032/2047 correlates about 0.992 there versus 0.999994 on OP4. Global
LFO PM now uses admission `{1,2,2,2}` instead of frequency-register coordinates.
The final expanded regression covers six channels, four carriers and three
F-numbers (72 combinations), retaining its zero-lag 0.999 bound. Running the
exact six channel cases against the preceding DSP fails every channel at
FNUM 2032; the corrected implementation passes them all. No bound was lowered.

At `0c6c5639f` plus this refinement, the completed Java 21 command passes
280 cases, zero failures/errors/skips, at 2026-09-06 07:12:33 BST:

```sh
mvn -Dmse=off -Dtest=TestFastFmCoreTolerance,TestFastFmLfoTransitions,TestFastFmAmplitudeModulation,TestFastFmFeedbackTransitions,TestFastFmOutputTiming,TestFastFmEnvelopeSampling,TestFastFmFrequencySampling,TestFastFmFrequencyTransitions,TestFastFmReleaseContracts,TestFastFmTimersAndChannelThree,TestFastFmDetune,TestFastFmWaveformAlignment,TestFastYm2612Chip test -B
```

Evidence: `pm-operator-admission-green.log`, `lfo-final-boundaries.log` (red),
`lfo-high-fnum-timing/` and `pm-admission-negative/results.log` under the task's
`target/fast-fm-release/`. These focused results precede final ordinary, guard,
trace, benchmark and packaging checks.

The earlier full ordinary run at `e4767cea9`, completed 06:37:57 BST, preserved
all 15,738 baseline test identities' outcomes and added 93 identities. Its
only failures were the then-open nine corpus and eight independent LFO cases
(17 XML failures; Maven collapses the factory and reports nine). Thirteen
existing all-pass nested identities have duplicate-row count differences,
without changed outcomes. All 23 skips classify as allowed with graphics
required/present; no required or unclassified skip exists. This intermediate
red run is retained under `pre-lfo-full-check/` and is not final certification.
