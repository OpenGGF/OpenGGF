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
This is **not a causal attribution** without admission evidence. Parent and
the S3K audio coordinator received the exact interval before any proposed
shared driver/sequencer/chip edit; the S2 native-title/request-remap owner also
needs to reconcile this boundary. No shared SMPS behavior was changed here.
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
