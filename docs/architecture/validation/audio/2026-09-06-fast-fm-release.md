# Fast FM 0.6 delivery evidence

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
