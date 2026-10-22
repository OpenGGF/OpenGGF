# S1/S2 native audio evidence and producer boundary (2026-10-22)

Scope: audio evidence lane of the native gap closure swarm, isolated branch
`feature/ai-gap-audio-evidence-20261022`, based on `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
No complete-game or all-route parity is asserted. Reference rows never supply
engine gameplay values or audio requests in gameplay integration.

## Final bounded outcome

Both final production captures ran from clean `70de3bfe04120306d35c5a2c75ee9534c294e5ad`,
with a compile phase refreshing build identity before the ordinary capture
entry point. They reused the unchanged native controls and original movies,
with no later mode forced. Each has 600 presentations, 441568 stereo frames,
one native stage session and exactly the native per-row sample cadence.
Both strict `--start-row 0 --rows 600` comparisons remain **DIFFERENT**, with
`SAMPLE_MISMATCH` only. There is no sample-count or frame-shape mismatch.

| Final production window | First different stereo frame | Native / engine value | Equal stereo frames | Production modes | Queue / Maven seconds |
| --- | ---: | --- | ---: | --- | --- |
| S1, `s1-engine-600-final-entry` | 55205 | `(0,0)` / `(1,1)` | 135382 | `TITLE_SCREEN` throughout | 365 / 21.545 |
| S2, `s2-engine-600-final-input` | 92739 | `(0,0)` / `(1,1)` | 173162 | Title screen 0..498, title card 499..583, level 584..599 | 319 / 21.948 |

Final engine PCM SHA-256 values are S1
`1491f62e5553d7e0270bd7feca92a8ea8b9d9189eac8b3e8388c541b9e65ea46`
and S2 `f8ed5cea80d5bd884db3d4c6b335f7fd256e426b621a88118c0e147c3c863bf2`.
S2's PCM is byte-identical before and after the separately verified native
pause read correction over this particular prefix. S1's first engine DAC
write is now row 75 instead of 96; the supported phase-order correction moves
the first waveform difference earlier than the original 70660. This is not
an overall startup parity improvement: authentic power-on, prior fade,
loading and native chant-wait costs remain open. No offset was fitted to
hide those missing owners. Both captures still print the existing exec-plugin
Discord worker shutdown warning before Maven exits successfully; no process
or owned validation job remains running.

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

The subsequent production recapture on clean `9b898be5f` reproduced the full
600-row native cadence and one session. Reusing the existing native control,
strict comparison now reports only `SAMPLE_MISMATCH`: first stereo frame
92739, native `(0,0)`, engine `(1,1)`, with 173162 equal stereo frames overall.
It waited 317 seconds, then Maven took 21.614 seconds. Its modes are title
screen through 498, title card 499..583 and level 584..599. This recapture
precedes the separately reviewed native Pause_Game input read correction;
the lease alone does not certify every recorded Start consumer.

Independent review then found that `GameLoop.prepareAdmittedIteration` passed
only physical or forced-playback Start to native Pause_Game. The correction
adds the lease-owned logical edge to that existing OR, preserving every
admission/title/setup gate. `TestRecordedInputNativePause` failed first on
clean `9b898be5f` at the native paused-state assertion (one failure, zero skips;
no queue wait, 28.749 seconds Maven). With the read corrected, the actual
production loop pauses once on the edge, skips sprite iterations while held
and released, and resumes the native gameplay iteration on the next edge.
Host pause stays false and audio remains forward. The exact affected selection
of that test, `TestLevelIterationAdmissionController`,
`TestGameLoopTitleCardPause`, `TestRecordedInputHostPause`,
`TestS2NativePcmPresentationClock`, `TestCompleteRunAudioAuthorityGuard` and
`TestInGamePause` passed **46 tests, zero skips**, after 349 seconds queue wait
and 79 seconds Maven execution, using the same Java 25/EGL/Lua environment
and verified absolute S2 ROM property. No shared audio producer, sequencer or
chip implementation changed.
The S2 command is the S1 command above with `--game s2 --zone ehz --sidekick tails`
and the verified S2 ROM/movie/output. Its comparator command is identical with
S2 directories and the same explicit 600-row target.

## Coverage and remaining dependencies

The final-PCM diagnostic's 216000-row operational ceiling is shorter than
either current movie. A separate clean TraceChaser checkout now contains
`3799bc63f01f7abd6012643f856f9bfcb43a5d31`, based on `322851cb`, raising only
that ceiling to 432000 rows (two hours at 60 Hz). The unrelated ledger cap
remains 216000. Exact requested/movie/completed-row checks, streamed outputs,
create-new/external paths, on-disk hashes and last-marker publication remain.
Eleven focused `FinalPcmWindowRunnerTests` passed with zero skips; the four
new acceptance bounds first failed against the old ceiling. These are
synthetic boundary/corruption controls, not new native captures.

Its built harness SHA-256 is
`3da284d7756f75abd00c3aa795d4212b2cc145b890a91607d3082cd008b9b9b2`.
The stock runtime remains BizHawk 2.11.0.0, with `EmuHawk.exe`
`b2d4be5e2a766a5161cc26f3af2a90753c39d64c91c54a9884171aed09e21df3`,
managed cores `0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7`
and stock `gpgx.wbx.zst`
`c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12`.
The outer gitlink is deliberately uncommitted pending parent composition;
historical capability hashes were not rewritten to bless this build.
Before four future full native captures (two per movie), parent must freeze
source/build/runtime/core/input identities, deduplicate jobs and approve the
schedule. Compare each full result's first 600 rows against the preserved
control rather than launching another unchanged native startup capture.
Diagnostic final-PCM v2 does not provide canonical host attestation, missing
S1 terminal frontiers or permission to enable either unavailable binding.

| Obligation | S1 | S2 |
| --- | --- | --- |
| Current emerald movie native startup PCM, rows 0..599 | Duplicate native capture verified | Duplicate native capture verified |
| Paired production startup PCM and exact first divergence | Final 600 rows compared; cadence matches, first PCM difference at stereo frame 55205 | Final 600 rows compared; former missing cadence repaired, first PCM difference remains at stereo frame 92739 |
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

## S2 Super request and engine playlist namespaces

The native CheckGoSuper request is `$96`: verified REV01 bytes at `$01AB9A`
are `30 3C 00 96`. That does not make `$96` the ordinary engine music key.
An independent read-only ROM audit decompressed the driver from `$0EC0E8`,
length `$0F64`, matching the load operands at `$0EC04A`
(`4D FA 00 9C 3E 3C 0F 64`). Its `zMasterPlaylist` begins at Z80 `$11F5`.
`zBGMLoad (s2.sounddriver.asm:1738-1778)` translates the native request through
that playlist before masking the engine key with `$1F` and indexing the ROM
pointer table. Actual decoded bytes and little-endian ROM pointers are:

| Native request | Playlist byte | ROM pointer bytes | Selected music |
| --- | --- | --- | --- |
| `$96` | `$93` at Z80 `$120A` | `$0F8026: BC CB` | Super, `$0FCBBC` |
| `$93` | `$8D` at Z80 `$1207` | `$0F801A: 1E B8` | Boss, `$0FB81E` |
| `$99` | `$96` at Z80 `$120D` | `$0F802C: 93 D1` | Title, `$0FD193` |

Thus `GameMusic.SUPER -> Sonic2Music.SUPER_SONIC.id == $93` is correct for the
existing engine loader. Replacing it with `$96`, or passing native `$96` to
ordinary `playMusic(int)`, would select Title. The distinguished existing
`playMusicMailboxNativeRequest(int)` ingress retains observed native `$96`
while the separately owned native mailbox adapter resolves content to engine
`$93`. Ordinary enum music and donor-specific semantic resolution must retain
their existing namespaces; raw S2 `$96` cannot be sent unchanged to arbitrary
donor loaders. No profile-wide alias change was justified or made. The S2
chain owner retains the source-backed immediate Super caller/stars/invincibility
correction; this audit did not duplicate it or claim later Super PCM coverage.

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
