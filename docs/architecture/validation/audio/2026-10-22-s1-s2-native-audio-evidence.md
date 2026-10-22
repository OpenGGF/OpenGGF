# S1/S2 native audio evidence and producer boundary (2026-10-22)

Scope: audio evidence lane of the native gap closure swarm, isolated branch
`feature/ai-gap-audio-evidence-20261022`, based on `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
No complete-game or all-route parity is asserted. Reference rows never supply
engine gameplay values or audio requests in gameplay integration.

## Source and capture identities

The current emerald movies are the run-local recordings, not the legacy movies
under `_movies/`:

| Game | ROM SHA-1 | Movie SHA-256 | Movie rows | Movie output |
| --- | --- | --- | ---: | --- |
| S1 World REV01 | `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` | `f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b` | 225101 | Nuked YM2612, ThreeBand 880/5000, unity gains |
| S2 World REV01 | `8bca5dcef1af3e00098666fd892dc1c2a76333f9` | `e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5` | 259590 | Nuked YM2612, no filter |

Native diagnostics use the existing source-owned TraceChaser `322851cb` recorder
and official BizHawk 2.11. Its loaded stock `gpgx.wbx.zst` has SHA-256
`c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12`;
`BizHawk.Emulation.Cores.dll` is version 2.11.0.0, SHA-256
`0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7`.
ROM identities were verified against the repository identity table. No ROM was
copied, linked or renamed for these diagnostics, no emulator state was written,
and no native observer core was substituted for stock PCM capture.

Both independent native captures of rows 0 through 599 produced byte-identical
PCM, frame tables and completion metadata. Each has 441568 stereo frames
(1766272 bytes). S1 has 312 lag rows; S2 has 385. Lag rows still generate sound.

| Game | PCM SHA-256 | Frame table SHA-256 |
| --- | --- | --- |
| S1 | `b1d76d71294b1616d48c6f8953dfd881cbe28855d4c7300c15c02720550e90d7` | `7592c2185ba66ebf9fc72aff3b9349c5b9f25faf2a208f8c2490e756444e1c73` |
| S2 | `34ea534db78e6c76c2f22ae29e36443b8af4288371e0bc62e46d86c281057bad` | `84b50dcb3ff821a6359a68b144a78c485a8a9b6797403bb1625afb033b093d30` |

Captures live outside the repository under
`$AUDIO_EVIDENCE_ROOT/`.
For each game, `*-native-600` and `*-native-600-duplicate` are the independent
runs. `metadata.json` was read and its counts and file digests were checked.

Invocation, repeated into a distinct create-new output for each game:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  BIZHAWK_HOME="$BIZHAWK_HOME" \
  tools/tracechaser/bizhawk-headless/run.sh --final-pcm-window \
  --rom '<absolute verified ROM>' --movie '<absolute run-local emerald BK2>' \
  --output '<external task directory>/<game>-native-600' --frames 600 \
  --tracechaser-root "$PWD/tools/tracechaser" --input-repository-root "$PWD" \
  --fixture-root "$PWD/src/test/resources/traces"
```

## Producer boundaries

The S1 profile's previously absent producers now have a strict native raw.v1
conversion path and the fixed production startup adapter. The S2 reference
producer now connects input validation, installed reference identity validation,
native execution and transactional projection. The reference home is one
authenticated bundle containing the BizHawk installation and source-owned
`bizhawk-headless/` launcher/harness/fixtures; the subprocess explicitly uses
that root as `BIZHAWK_HOME`, rather than an unauthenticated nested dependency. Its production producer uses the
same fixed startup owner. A frozen profile identity is a declaration, not executable attestation. The
accepted [detached attestation prerequisite](../../designs/audio/2026-09-01-openggf-producer-artifact-attestation-design.md) remains open.
No producer binding or runtime/observer pin is invented
by this work.

`Engine.preparePresentationForLaunch` used to replace the exact caller-owned
headless backend during configured game startup. It now retains that backend.
The actual configured-startup regression exercises real S2 ROM detection,
session opening and title entry in a reused JVM with synthesis enabled and
with synthesis disabled. A device-construction trap makes the historical
replacement fail before physical audio initialization.

The native PCM comparator previously used the shorter sample length and could
report MATCH for an identical truncated prefix. It now checks row continuity,
cumulative sample counts, complete stereo frames, the native completion marker
and file digests. `--rows` requires the intended presentation count, rejecting
an internally consistent premature end as well as torn data. Without it, a
match describes only the inferred observed span. It compares exactly the
declared row span and reports any
remaining native rows. Exit status is 0 for equality, 3 for a valid difference,
and 2 for invalid capture evidence. It applies no shifts, fitted tolerances,
gain changes or normalization.

Before longer native captures, PCM comparison was changed to sequential reads
of at most 64 KiB rather than complete-file byte strings and sample arrays.
The complete native stream is still hashed, including unselected prefix and
tail bytes; selected common samples are compared exactly. Integer sums of
squares preserve the existing descriptive RMS calculation across chunk
boundaries. Per-row TSV inventories remain retained, so memory still grows
with the number of rows rather than the much larger PCM sample count.
The exact Python selection passed **37 tests** (27 existing and ten new),
zero failures or skips, including chunk-boundary/final-sample mutations,
unselected-byte corruption, truncated streams and independently calculated
statistics across six chunk sizes. Both preserved 600-row comparison reports
remained field-for-field identical and `DIFFERENT` after this change.

## Production S1 startup measurement

The actual `GameplayCaptureTool --title-screen --audio --native-pcm` ran the
original emerald movie from row zero for 600 rows, with no positioned setup,
forced mode, trace state, donor, or gameplay hydration. Its no-device EGL
session produced 441568 stereo frames in one stage session, with exactly the
native per-row sample cadence. All 600 state rows remain `TITLE_SCREEN`.
The queued invocation waited 364 seconds, then Maven took 21.123 seconds.

The strict comparator used `--start-row 0 --rows 600` and returned exit 3,
`DIFFERENT / SAMPLE_MISMATCH`: first difference at stereo frame **70660**,
native `(0,0)`, engine `(1,1)`. Stereo frames 0..70659 agree exactly with no
shift or tolerance. The engine ledger first writes SEGA DAC bytes in row 96;
native's first post-DC PCM above amplitude 20 is in row 166. These are different
measurements: the latter is a waveform onset, not an observed native command
submission. No 70-frame delay was fitted into gameplay or the audio driver.

Production command (same absolute inputs as the native rows):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl exec:java \
  -Dexec.mainClass=com.openggf.tools.GameplayCaptureTool \
  "-Dexec.args=--game s1 --rom '<absolute verified S1 ROM>' --zone ghz --act 1 \
  --title-screen --input '<absolute S1 emerald BK2>' --frames 600 --audio \
  --native-pcm --no-video --stop-on-death false --out-dir '<task>/s1-engine-600'"
python3 tools/audio/compare_native_pcm_window.py \
  --native-dir '<task>/s1-native-600' --start-row 0 --rows 600 \
  --engine-dir '<task>/s1-engine-600'
```

## S1 SEGA entry phase corroboration and bounded correction

On plumbing commit `1243fa2ed`, one additional 600-row stock-core pass registered
read-only 68000 execute callbacks around `GM_Sega`; it applied only the original
movie buttons and made no RAM, register or savestate writes. The temporary
external probe is `$AUDIO_EVIDENCE_ROOT/s1-sega-probe/S1SegaPhaseProbe.cs`;
`capture/events.tsv`, `capture/frames.tsv`, `capture/pcm.s16` and
`capture/completed.txt` contain its observations under the external
`$AUDIO_EVIDENCE_ROOT` task directory.
The capture reproduced the uninstrumented S1 PCM SHA-256
`b1d76d71294b1616d48c6f8953dfd881cbe28855d4c7300c15c02720550e90d7`,
441568 stereo frames and 312 lag rows. Thus these callbacks preserved the
previously observed waveform over this bounded pass.

| Native callback | Count | Original BK2 row and observed state |
| --- | ---: | --- |
| `GM_Sega`, `$002DD2` | 1 | 62 |
| Before `PaletteFadeOut`, `$002DDE` | 1 | 62 |
| After fade, before logo setup, `$002DE2` | 1 | 83 |
| `Sega_WaitPal`, `$002EAC` | 76 | First: 90, `v_pcyc_num=-10`, `v_pcyc_time=$0000`; last: 164, position 36, timer `$0001` |
| Chant queue call, `$002EC0` | 1 | 165, `D0=$E1`, position 36, timer `$0401` |
| Return after PCM wait, `$002ECE` | 1 | 299 |
| Set title mode, `$002EEC` | 1 | 329 |

There are 82 callback events. The first two `Sega_WaitPal` entries occur in row
90; its count is not a claim of 76 distinct BK2 rows. The command observation
at row 165 independently precedes the waveform onset measured in row 166.

The existing listing offsets were checked against the verified REV01 ROM:
`$002DDE: 61 00 F0 6A` calls `PaletteFadeOut`; `$002E80: 70 00 61 00 F2 8C`
selects and loads the active palette; `$002E86: 31 FC FF F6 F6 32` and
`$002E8C: 31 FC 00 00 F6 34` initialize the palette position and timer;
`$002EAC: 11 FC 00 02 F6 2A` enters the ordinary SEGA VBlank loop;
`$002EBC: 10 3C 00 E1 61 00 E4 D2` loads E1 and calls `QueueSound2`.
`GM_Sega (sonic.asm:1808-1867)` fades the previous screen out before loading
the SEGA art and active palette. It has no separate generic fade-in after
that setup. `PalCycle_Sega (sonic.asm:1565-1643)` supplies the logo's own
light scan and subsequent palette fade: positions start at -10, advance by
two, skip transparent colors, and reach `$64` after 51 calls. The next five
five-call boundaries select palette byte offsets 0/12/24/36 and then return
zero at the exclusive `$30` end: 76 calls in total.

The bounded implementation correction removes only the manager's unsupported
generic entry fade, which previously delayed the first real palette update
by 21 updates because its 22nd fade update fell through. Initialization and
reset now select the directly loaded active palette. The existing 76-call
palette sequence, 98-update chant wait, 30-update post-chant wait and exit
fade ownership remain intact. This fixes a phase-order discrepancy; it does
not align power-on row zero or assert end-to-end waveform parity. Cold boot,
the preceding fade, loading costs and the observed native PCM-wait interval
165..299 still need their owning runtime model. No constant delay was fitted
from these rows.

The new ROM-backed manager regressions inspect the first actual scan color
and all four fade sets against independently addressed ROM palette bytes,
then require exactly one E1 submission at the native palette terminal. They
also exercise reset/reinitialization. The four exact affected S1 title classes
passed ten tests with zero skips in the combined production-clock diagnostic
run described below; the native probe itself did not launch Maven.

Probe compile and invocation (the same existing ROM and run-local BK2 paths
shown above, no recorder rebuild or pin change):

```bash
mcs -out:"$AUDIO_EVIDENCE_ROOT/s1-sega-probe/S1SegaPhaseProbe.exe" \
  -r:/usr/lib/mono/4.7.2-api/Facades/netstandard.dll \
  -r:"$PWD/tools/tracechaser/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe" \
  -r:"$BIZHAWK_HOME/dll/BizHawk.Emulation.Cores.dll" \
  -r:"$BIZHAWK_HOME/dll/Newtonsoft.Json.dll" \
  "$AUDIO_EVIDENCE_ROOT/s1-sega-probe/S1SegaPhaseProbe.cs"
env -u DISPLAY -u WAYLAND_DISPLAY BIZHAWK_HOME="$BIZHAWK_HOME" \
  MONO_PATH="$PWD/tools/tracechaser/bizhawk-headless/bin/Release:$BIZHAWK_HOME/dll" \
  LD_LIBRARY_PATH="$BIZHAWK_HOME/dll" \
  mono "$AUDIO_EVIDENCE_ROOT/s1-sega-probe/S1SegaPhaseProbe.exe" \
  '<absolute verified S1 REV01 ROM>' \
  "$PWD/src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/sonic1-complete-withemeralds.bk2" \
  "$BIZHAWK_HOME/dll/gpgx.wbx.zst" \
  "$AUDIO_EVIDENCE_ROOT/s1-sega-probe/capture"
```

## Production S2 startup measurement

The same production title-first path replayed the S2 emerald movie from row
zero for 600 presentations, Sonic with Tails. It waited 370 seconds for
admission and Maven took 21.478 seconds. The state rows are `TITLE_SCREEN`
through row 498 and `TITLE_CARD` from row 499. No later mode was forced.

The strict `--start-row 0 --rows 600` comparison returns exit 3 with
`SAMPLE_COUNT_MISMATCH`, `FRAME_SHAPE_MISMATCH` and `SAMPLE_MISMATCH`.
Native has 441568 stereo frames; the engine has **404771** in one physical
stage session. The ordinary 48 kHz WAV still has all 480000 stereo frames.
The first waveform difference is stereo frame **92739**, native `(0,0)` versus
engine `(1,1)`.

The first absent native-compatible packet is at presentation **506**, where
native has 736 stereo frames and the engine has zero. Exactly 50 rows drain
zero native PCM: 506..515, 524..531, 539..544, 554..558, 563..566, 571..575,
580..582, 587..591 and 596..599. Fifty hardware frames account for the total
sample deficit. This is a measured production load/transition boundary, not a
missing output-file tail; all requested state rows and ordinary PCM packets
were written.

A candidate shared owner is the transactional typed-request failure path in
`AudioPresentationProducer.presentSessionForward`: it can return
`REQUEST_REJECTED` before chip rendering, restoring the presentation clock.
That hypothesis was subsequently **disproved** on `1243fa2ed` plus the S1
entry-phase correction. A production-only 600-row diagnostic sampled each
resolved presentation mode, parity counters and producer clock before and
after presentation. It reproduced 404771 native stereo frames and the exact
50 zero-drain rows. Every zero-drain row resolved to `SILENT`; every producer
clock advanced by 800 ordinary samples. The totals were 600 committed
presentations, 550 forward, 50 silent and 480000 ordinary stereo frames, with
one native stage session. No rejected-request clock rollback accounts for
these gaps, and no shared SMPS behavior was changed.

`GameplayCaptureSession.step` supplies recorded logical input directly, while
`GameLoopPauseInput.nextUserPaused` excludes only the existing trace/playback
owners from its logical-Start host-pause branch. Repeated recorded Start
presses in `TITLE_CARD` therefore toggle host pause in this capture. This
contaminates the startup comparison and requires a scoped recorded-input
ownership correction before attributing the silent intervals to native audio.
Parent and the native-menu owner received the measured cause and proposed
interface reservation. This diagnosis does not make the measured comparison
pass or attribute its earlier waveform difference to the sound-test remap.

The accepted correction is `RecordedInputOwnership`, an engine-internal lease
attached to the exact `InputHandler`. Acquisition is nonnested and both query
and release require the acquiring thread. It changes ownership only: recorded
controller edges remain intact for native consumers; no mode, frame clock,
gameplay state or comparison row enters through it. `GameLoopPauseInput`
excludes an active recorded owner only from the logical-Start host-pause
branch. The physical keyboard pause branch is unchanged. Capture steps and
the fixed production audio runner hold the lease around input publication and
the production step, then release it even when that step fails. This lease is
separate from foreign frame/input owners, so the runner still rejects foreign
drivers without rejecting its own recorded input. It is temporary host state
and is not part of gameplay rewind snapshots.

The exact affected queued Java selection passed **71 tests, zero skipped**
on `6211f4fc7` plus this correction (43 seconds queue wait, 93 seconds Maven).
It selected `TestRecordedInputOwnership`, `TestRecordedInputHostPause`,
`TestS2NativePcmPresentationClock`, `TestProductionBk2AudioRunner`,
`TestCompleteRunAudioAuthorityGuard`, `TestGameLoopTitleCardPause`,
`TestModApiSignatureSurface`, `TestMutatorGameplayCaptureTool`,
`TestGameplayCaptureSkippedTitles`,
`TestGameplayCaptureFrameRendering#interactiveCaptureUsesTheProductionPresentationCallbackOncePerFrame`
and `TestGameplayCaptureFadeOwnership`, with Java 25, unset display variables,
`LUA_BIN=/usr/bin/lua5.4`, `-Dmse=off`, the surfaceless-EGL property and all three
verified absolute ROM properties. Independent contracts cover handler/thread
isolation, nonnested acquisition, exception cleanup, physical versus recorded
Start, and foreign-driver refusal. The actual S2 route now commits 600 forward
presentations and the independently calculated NTSC PCM cadence on every row,
441568 stereo frames in one session. This closes the measured missing-clock
symptom; it is not a waveform match or complete-route proof.
The S2 command is the S1 command above with `--game s2 --zone ehz --sidekick tails`
and the verified S2 ROM/movie/output. Its comparator command is identical with
S2 directories and the same explicit 600-row target.

## Coverage and remaining dependencies

| Obligation | S1 | S2 |
| --- | --- | --- |
| Current emerald movie native startup PCM, rows 0..599 | Duplicate native capture verified | Duplicate native capture verified |
| Matched production startup PCM and exact first divergence | 600 rows compared; first PCM difference at stereo frame 70660 | 600 rows compared; first cadence difference at presentation 506, first PCM difference at stereo frame 92739 |
| Continuous boss, underwater, speed shoes, special stage, results and credits state/register/PCM | Open | Open |
| Canonical complete-run reference publication | Unavailable: native raw terminal/frontier and installed identity evidence remain required | Unavailable: reviewed full duplicate raw capture and installed identities remain required |
| Authenticated production publication | Unavailable: independently provisioned artifact attestation trust root absent | Same |

The existing S1 song windows under the external audio fixture root use legacy
movie `f744c814d8e00d6c367f7fe83bb663cab123b5a4ed385a320d71b74d63146bde`
(195493 rows). They remain useful isolated driver contracts; they cannot prove
current emerald-route audio. S2 driver-state windows 10150..12400 and
20107..23600 name the current emerald movie, while CPZ windows name a separate
recording. The existing one-up metadata reports 259593 movie rows despite the
matching movie having 259590; this provenance discrepancy was observed and
was not normalized into a passing complete-run claim.

Retained-row trace-chain execution starts from its own certified boundary; it
cannot establish natural production startup from row zero. Later integrated
phases depend on the S1/S2 chain owners' current production frontiers and on
native reference publication. No later mode was forced to manufacture proof.

## Verification

The first focused Java run, from `94c40570` plus the lane changes, executed 65
outcomes: 62 passed, three failed, zero skipped. It waited 351 seconds for
admission and Maven then ran for 84 seconds. The two real-ROM configured Engine
startup tests, six existing configured-startup tests, five headless audio
ownership tests, eight production row-runner tests, six capture-reducer tests,
and the producer support/configuration checks passed. The three failures were
pending S1 source integration, a reference-metadata test placed under the
engine-authority guard, and the changed unavailable-exception type in the S2
producer test. Their corrections and final focused outcomes follow below.

The initial build-only compile found that `OwnedTemporaryDirectory` is not an
`AutoCloseable`; explicit owned cleanup replaced the invalid try-with-resources.
The subsequent focused run compiled all sources successfully on Java 25.

Python comparator verification:

```bash
python3 -m unittest discover -s tools/audio/tests -p test_compare_native_pcm_window.py
```

No broad smoke or release full suite is owned by this lane.

Final plumbing checkpoint verification on `5e4fe3130` plus the lane changes:

- `TestS2CompleteRunReferenceProducer` (6) and `TestTraceChaserAudioProcess` (7)
  pass with zero skips in the 44-outcome follow-up. Its sole remaining failure
  was a negative REFERENCE-kind vector still located in the engine-authority
  test file. Moving that vector to the metadata test preserved the rejection
  assertion without weakening the guard.
- Exact affected authority/support/metadata rerun: **32 passed, zero skipped**;
  15 seconds queue wait, 31.379 seconds Maven execution.
- Fixed producer dispatch and fresh-JVM CLI: **13 passed, zero skipped**;
  2 seconds queue wait, 28.883 seconds Maven execution.
- Strict PCM comparator: **27 Python tests passed**. They include a complete
  declared span, sample/count/cadence mutations, malformed/corrupt completion
  data, and an internally consistent ten-row engine capture rejected against
  an explicitly requested 600-row window.
- S1 worker `d5bc43583c29f9d410f12889f96d45dca4c180cc`, integrated locally as
  `5e4fe3130`, passed **18** final raw/projector/producer tests (zero skips),
  including a fixture-independent full 224241-row retained interval. Its earlier
  exact selection additionally passed four fixture and 17 normalizer tests.
  This is synthetic/contract coverage, not a measured full native capture.

The exact parent-side focused invocations were:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestCompleteRunAudioAuthorityGuard,TestCompleteRunAudioOpenGgfProducerSupport,TestCompleteRunAudioProducerMetadata' test -B
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestCompleteRunAudioCli' test -B
```

The initial exact ten-class command also covered
`TestCompleteRunAudioOpenGgfCapture`, `TestEngineConfiguredHeadlessStartup`,
`TestEngineConfiguredHeadlessStartupRom`, `TestHeadlessAudioOwnership`,
`TestHeadlessGameBootAudioIdentity`, `TestProductionBk2AudioRunner`, and
`TestS2CompleteRunOpenGgfProducer`, using `-Dmse=off`, the EGL property,
`LUA_BIN=/usr/bin/lua5.4` and the verified absolute `sonic2.rom.path`.
The separate five-class affected run included the authority, support, metadata,
S2 reference producer and TraceChaser subprocess tests with the same flags.

S1 child worktree `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-ae08be7b` deliberately
retains five authorized temporary copies of the parent's support/metadata,
preflight, registry and S2 producer files, excluded from its commit. Parent owns
lifecycle accounting; no worktree or local branch was removed by this lane.
