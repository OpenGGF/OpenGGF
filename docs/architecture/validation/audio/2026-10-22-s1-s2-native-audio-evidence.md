# S1/S2 native audio evidence and producer boundary (2026-10-22)

Scope: audio evidence lane of the native gap closure swarm, isolated branch
`feature/ai-gap-audio-evidence-20261022`, based on `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
No complete-game or all-route parity is asserted. Reference rows never supply
engine gameplay values or audio requests in gameplay integration.

## Final bounded outcome

Both latest production captures ran from clean
`6c245461628f365e30402947fe7548b7146beef7`, with a compile phase refreshing
build identity before the ordinary capture entry point. They reused unchanged
native controls and original movies, with no later mode forced. Each has600
presentations,441568 stereo frames, one native stage session and exactly the
native per-row sample cadence. Both strict `--start-row 0 --rows 600`
comparisons remain **DIFFERENT / SAMPLE_MISMATCH** only; sample counts and frame
shapes match.

| Latest production window | First different stereo frame | Native / engine value | Equal stereo frames | Production modes | Queue / Maven seconds |
| --- | ---: | --- | ---: | --- | --- |
| S1, `s1-engine-600-source-phases` | 55205 | `(0,0)` / `(1,1)` | 135382 | `TITLE_SCREEN` throughout | 69 / 21.566 |
| S2, `s2-engine-600-source-phases` | 77285 | `(0,0)` / `(3,3)` | 197665 | Title screen0..498, title card499..583, level584..599 | 5 / 22.443 |

Latest engine PCM SHA-256 values are S1
`1491f62e5553d7e0270bd7feca92a8ea8b9d9189eac8b3e8388c541b9e65ea46`
and S2 `02443927a8abf9e85ed1ac3ac65aefa589ba07f6bd3f364ff08e000ba2e04f36`.
S1 is byte-identical to its prior clean70de3bfe capture. S2's first waveform
difference moved earlier from92739 after removing its unsupported entry fade;
this is not an overall startup timing improvement. Its chant now completes:
the earlier capture emitted3819 SEGA DAC bytes in rows126..139 before the
movie's face-button input canceled it; the latest emits all24948 native sample
bytes in rows105..196. Both begin with actual ROM bytes `7B8789828E`. That
repairs input ownership and truncation without aligning absolute power-on time.

S1 still emits its27000 chant bytes in rows75..174. Authentic coldboot,
prior-screen fades, direct loading, S1 CPU-wait elapsed cost and S2 visual/input
polling details remain open. No offset was fitted to hide missing owners. The
prior70de3bfe S2 PCM was byte-identical before/after the native-pause read fix;
that old control remains attributed to its original source. Both latest
captures print the existing exec-plugin Discord worker shutdown warning before
Maven exits successfully; their owning command session is closed. No new
native capture or full-route certificate was produced.

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

Later production Super audio measurements must include caller commit
`f85bb87469f4552f2338e77d1ef44ccfbda307fd` in their recorded integrated source.
Inspection of that separate lane's committed code confirms fixed Obj7E slot
129 publication and timed-invincibility clearing at transformation start,
before `SPECIAL_SFX $DF` and semantic `GameMusic.SUPER` (engine `$93`).
Palette completion no longer repeats those requests. Ordinary/donor music
namespaces and the audio loader/profile/adapter are unchanged by that commit.
The caller owner reports six focused actual-ROM cases passing with zero
skips, including Super/Title loader-pointer distinction, full general dynamic
allocation, independent normal stars at 136, retained same-owner stars at 129
and restore/forward behavior. Those tests were not rerun by the audio lane,
and no PCM capture accompanied the caller change.

The latest 600-row startup measurements above use `6c2454616` and do not
include this separate caller composition. They cannot certify its later Super
boundary. The caller lane's subsequent normal-star world-restore correction
`0f595ef8833d1e52cb00ab0a23b90de1fb6469ba` is now included with the caller in
the route-owner source reported below. These source dependencies do not supply
later Super state/register/PCM observations or certify a natural row-zero route.

The S2 chain owner reports retained-chain job41514 on
`9854f02b146a8cbe1a4d656f54778a5918ee2852`: ten required trace cases executed,
eight passed, two failed, zero skipped. Seven special-stage cases and the
independent ARZ2 case passed. Starting from retained cursor769, the emerald
chain naturally completed ARZ1 segment18 with zero errors, then completed ARZ2
segment19 with13495 errors, first at frame5084 `sidekick_y` (reported pair
`048A/0489`). The following CNZ handoff had cursor113342 against first113340,
blocking the next admission. The owner reports the twelve special-stage
return/level-art axes unchanged. These are owner-observed route results; the
audio lane did not rerun the job.

Read-only inspection of the reported source's commit ancestry confirms
`f85bb8746`, `0f595ef88` and graphics dependency bundle `91f3fd4bb` precede
`9854f02b1`. The owner also identifies the composed special-stage geometry,
ROM-palette and leaf-phase work in that source. Semantic
`GameMusic.SUPER` remains engine93. This updates the real retained-route
frontier for future audio evidence; it provides no new row-zero prefix,
startup waveform, effects capture or later Super audio result. No audio or
native job was requested or launched for this handoff. All four full native
captures remain held behind the existing route, clock and publication gates.

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


## S1 startup causal slice: instruction work is not presentation time

The follow-up on `6bae77249` reused the original stock-core 600-row controls
and the inert `s1-sega-probe` callbacks above. No new native capture was run.
Both startup waveform comparisons remain failures at their recorded source
identities. In particular, this work does not replace a missing boot phase with
an absolute 70/90-row delay or replace the chant wait with the observed 134 rows.

| Phase | Native cause and clock | Existing production owner | Remaining authority gap |
| --- | --- | --- | --- |
| Cold reset through first GM_Sega | EntryPoint hardware setup, checksum over ROM header's inclusive end, RAM clears, VDP setup, DACDriverLoad and JoypadInit; CPU instructions plus bus, reset and peripheral work | Engine opens the ROM/session; GameLoop.initializeTitleScreenMode immediately initializes the title provider | There is no production cold-reset CPU job or native reset/frontier clock. Host ROM loading time is not that clock. Native GM_Sega callback row62 is an observation, not an admissible duration. |
| Prior-screen fade | GM_Sega queues Stop, ClearPLC, then PaletteFadeOut: 22 WaitForVBlank/fade/RunPLC iterations on the previous palette | GameLoopTitleEntryFade covers a retained level-exit picture; SegaPaletteFade supplies palette arithmetic | Fresh title initialization does not retain/execute this GM_Sega entry lifecycle. The existing exit-picture owner is not evidence that cold entry has run the fade. First/last callbacks62/83 do not authorize a21-row offset. |
| SEGA art/map load | ClearScreen, direct NemDec, EniDec and tilemap/VDP writes with interrupts disabled, then PalLoad into the active palette | Sonic1TitleScreenDataLoader synchronously reads and decompresses real ROM data, then the manager starts the logo scan | This correct data pipeline has no admitted direct-load CPU/VDP service cost. Generic TITLE_SCREEN PLC servicing is not this direct NemDec/EniDec job. Native after-fade83 to first Sega_WaitPal90 is a boundary interval, not a replacement seven-row timer. |
| Logo scan | WaitForVBlank/VBlank02 then PalCycle_Sega; 51 scan calls plus five five-call boundaries | Manager and ROM-backed SegaLogoPaletteCycleState | Phase-relative palette progression is covered. Absolute row-zero alignment is not. |
| Chant request and CPU wait | E1 is serviced by UpdateMusic inside VBlank14; PlaySegaSound queues Z80 sample88 and blocks the 68000 in its own NOP/DBF loop | Manager submits E1; AudioManager/resolver/voice registry starts SmpsDriverSession's ROM PCM transport | The session tracks PCM exhaustion, but has no separate S1 CPU-busy completion identity. The manager's fixed98 updates are not this loop. Native E1 request165 to return299 includes unclosed overhead. |
| Post-chant | The return from that VBlank permits writing generic timer30; subsequent VBlank02 decrements it; Sega_WaitEnd tests only P1 pressed Start | Manager currently counts30 after its approximate98 wait | The30-loop cause is known, but its start still depends on the unmodeled CPU completion, not on PCM exhaustion. |
| STP and main title | GM_Title stops sound/clears PLC, fades SEGA out, reloads DAC driver and STP assets, fades STP in, loads main-title assets with interrupts disabled, fades STP out, then assembles the title | Manager's exit fade and intro phases; DataLoader loads all assets up front | The fixed96 hold approximates real asset work. No source timer or input-skip branch owns it. Relative fades do not certify the intervening load cost. |

Owning source: EntryPoint and CheckSumCheck (`sonic.asm:213-423`),
DACDriverLoad (`sonic.asm:1225-1242`), VBlank_Sega/VBlank_SegaPCM
(`sonic.asm:756-771`), GM_Sega (`sonic.asm:1808-1887`), and GM_Title
(`sonic.asm:1893-2006`). PaletteFadeOut (`_inc/Palette Fading.asm:134-146`)
waits for VBlank, changes both palette buffers, and runs PLC once per iteration.
This is shipped REV01 with FixBugs=0 and its checksum enabled.

The reusable read-only `tools/audio/s1_startup_instruction_audit.py` verifies
REV01 identity and opcode/control-flow shapes before reporting instruction-only
components. It uses the existing Motorola-table decoder, not measured rows or
waveform shifts. A valid report is deliberately **INCOMPLETE**, exit2: reset,
DMA/FIFO, Z80 bus contention, refresh, dispatch and exact VInt endpoints are not
closed by these components, and none of its output is runtime readiness input.

Actual checksum instructions at `$031C` start at `$0200`, read the inclusive
end from header `$01A4` (`$0007FFFF`), and add 261888 words. The independently
summed/stored checksum is `$AFC7`. ADD.W `(A0)+,D1` at `$032C` costs8,
CMP.L `A0,D0` at `$032E` costs6, and BHS at `$0330` costs10 taken/8 falling
through: the loop alone costs6285310 no-wait 68000 cycles. RAM-clear iterations
are sourced separately from ROM operands; they are not inferred from callback62.

The chant handler begins `$071FAC: 13FC008800A01FFF`, queuing sample88.
After starting Z80, `$071FBC:323C0011` seeds18 outer passes;
`$071FC0:303CFFFF4E7151C8FFFC51C9FFF4` loads65536 inner iterations of
NOP/DBF per pass. The nested loop costs16515472 cycles; the initial D1 load
adds8. At `$071FCE:584F4E75`, ADDQ skips the sound caller's stack return before
RTS. This is CPU work rather than a poll of sample completion
(PlaySegaSound, `s1.sounddriver.asm:733-747`). No-wait instruction cycles alone
must not be rounded to a presentation count.

The independent Z80 sample path is ROM DACDriver at `$072E7C`; the decoded
loop contains `$00B9:1188962178690E2A` and `$00C8:060B10FE`. Sample88 is
27000 bytes at `$079688`, SHA-256
`bc99efa76997dfa715127b974d55451a7ef5373e517bbeda62cbd3cf61feb3e5`.
Its220-Z80-cycle byte cadence is distinct from the 68000 busy loop. The current
`SmpsDriverSession.serviceForward()` blocks while transport exists, then becomes
eligible after exhaustion; it has no longer-lived S1 CPU-wait state. Therefore
using `segaPcmTransportActive()` as the title's release condition was rejected.
Changing the shared session, snapshots, request admission or clock contract
requires a separate concrete reservation and proof of that boundary; no such
shared mutation was made in this slice.

The independently justified game-local correction removes input branches that
the ROM does not execute: Sega_WaitEnd accepts only P1 Start, and the three STP
fade/load/fade phases have no input-triggered jump to the main title. ACTIVE
title confirmation is unchanged. This does not repair fixed98/fixed96 timing.
The new ROM-free input tests were run with the manager reverted to clean
`6bae77249`: 19 executed, eight assertion failures, zero errors/skips (immediate
queue admission, Maven81s). The failures distinguish all three STP phases plus
A/B/C, keyboard Jump and host menu accept at the SEGA post-wait boundary.


With the corrected manager, the exact affected selection passed **29 tests,
zero failures/errors/skips**, immediate queue admission and Maven85s:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dsonic1.rom.path=<absolute existing S1 World REV01 ROM>' \
  '-Dtest=TestSonic1IntroInputPhases,TestSonic1SegaScreenFadeTiming,TestSonic1SegaScreenNativePhases,TestSonic1SegaLogoScanMap,TestSonic1TitleScreenPaletteFade' test
```

This selection includes the real-ROM palette/chant-request progression and
reset tests. It is bounded validation of these input branches and existing
phase-relative palette behavior. The current S1 movie has no Start or action
press in its first600 rows, so this patch does not claim to move either observed
waveform frontier. The preserved captures remain attributed to their original
runtime source70de3bfe, rather than being relabeled as a capture of this patch.

A further source-only caller discrepancy is reserved for coordination:
`initialize()` currently queues Main PLC0 before SEGA, while native GM_Sega and
GM_Title begin with ClearPLC, and NewPLC(Main) occurs only at
`GM_Title (sonic.asm:2053-2054)` before main-title PaletteFadeIn. The existing
producer test expects the premature initialization boundary. Neither that test
nor the caller/service was changed in this input-only slice. A follow-up needs
operation-specific retry state and agreement with the S1 chain owner; moving a
call does not itself close direct NemDec/EniDec load timing.


The audit's exact ROM-free selection
`python3 -m unittest discover -s tools/audio/tests -p test_s1_startup_instruction_audit.py`
passed19 tests, zero skips. Independent counter interpreters and checksum vectors
cover wrap/bounds, unsigned BHS outcomes, nested DBF terminal branches, opcode
corruption, identity-first rejection and incomplete/error CLI contracts. Running
`python3 tools/audio/s1_startup_instruction_audit.py --rom` with the original
absolute REV01 path returned INCOMPLETE/exit2 with five components and null
whole-startup total/frame conversion. No generated report or ROM copy is stored.


## S2 startup source attribution after the input-ownership correction

The preserved S2 engine control starts writing the correct ROM chant bytes
`7B8789828E` in packet126; the first differing PCM sample92739 is in that
packet. The native control first leaves its post-boot zero run at sample183298,
packet249. No matching native FA execute callback was captured, so these output
boundaries do not establish a native FA request timestamp.

Actual REV01 ROM `$0338:D258B08864FA` is the checksum ADD.W/CMP.L/BHS loop;
header end `$01A4` is `$000FFFFF`. It scans524032 words from `$0200` and
costs12576766 Motorola instruction-only cycles. Production begins at an
initialized title manager, with no cold-reset checksum/VDP/Z80 owner. Neither
the instruction budget nor the PCM gap authorizes a fitted startup delay.

SegaScreen (`s2.asm:4095-4170`) fades the previous screen, loads art/map and
scaling work, then loads the active palette directly. At `$0038CE`,
`70006100EE5C` is the palette selection and PalLoad_Now call; a later generic
fade-in is unsupported. ObjB0's movement/palette-script owner is separate
(`s2.asm:78399-78620`); actual script headers `$03A49E/$03A514` begin
`040710FFFB10` / `040710FFFB00`. The existing106-update visual approximation
has not been certified as that state machine.

In the shipped fixBugs=0 path, both ObjB0 and its caller requestFA; the single
sound mailbox prevents duplicate playback. The caller's second FA is
`$003928:103C00FA`, followed by PlaySound at `$00392C:6100DA42`, SEGA VInt
selection at `$003930`, WaitForVint at `$003936`, and the180 timer at
`$00393A`. Only the subsequent PCM VInt end loop tests Start from either pad
(Sega_WaitPalette/Sega_WaitEnd, `s2.asm:4177-4201`). This is not S1's 68000
busy loop: S2 keeps waiting through VBlanks, and its screen wait is independent
of PCM exhaustion. The source also has no input exit during the text screen's
fade/load/fade. The existing96 hold remains a load-cost approximation.

These disjoint S2 SEGA/STP phase methods were coordinated with NativeModes
before mutation. No ordinary music namespace, native request remap, ACTIVE
menu control, attract routing, Super caller or shared audio service is changed
by that reservation. Handoff validation must exercise real production
presentation admission: a source WaitForVint cannot be replaced by a diagnostic
counter, sample count or a successful-music query. S2 startup waveform and load
costs remain open until remeasured at the resulting runtime identity.


## S1 title PLC caller ordering

After parent approval and S1-chain acknowledgment of the exact two caller
methods and title coverage cases, initialization now uses the existing
`Sonic1PlcService.clearQueued()`. Main0 is submitted only after the title's
object/background initialization and Title music request in
`transitionToMainScreen()`, before its palette fade. The private pending
operation distinguishes CLEAR from MAIN, so a rejected clear cannot later
publish Main at SEGA entry; Main retry cannot replay title objects or music.
Reset clears that caller-owned pending operation. Service/queue decoder policy,
arm ordinals, scheduling and hardware admission were not changed.

This order is verified directly in REV01 ROM: `$002DDA:6100E7FA` calls
ClearPLC `$0015D6` before prior fade; `$0030DC:103C008A6100E2B2` requests
Title music; `$00312A:4EB90000D9C6` executes title objects and
`$003134:4EB90000DCEC` builds their sprites; then
`$00313A:70006100E46C` selects Main0 and calls NewPLC `$0015AA`.
GM_Title (`sonic.asm:2021-2054`) explains those owners. The actual service
continues to reject clear/replacement during an active decoder; tests preserve
that state and retry the same operation after its normal completion.

The revised ROM-backed caller tests seed distinct real PLC descriptors and
compare the queue at each operation boundary. They check genuine clearing,
no Main submission during SEGA palette work, title-object/music-before-Main
ordering, active-decoder preservation, Clear-versus-Main retry identity, and
no repeated Main or music after success. They do not infer a whole title queue
is empty from an elapsed number of presentations. This corrects submission
ownership; direct logo/STP loading and coldboot elapsed timing remain open.


First focused PLC check on `7d3d5a972` plus the caller/test diff:
`TestSonic1PlcProducerCoverage,TestPlcProducerCoverageGuard,TestSonic1SegaScreenNativePhases`
executed25 cases: 24 passed, one guard assertion failed, zero errors/skips;
queue139s and Maven78s. All19 ROM-backed producer cases and both native phase
cases passed. The sole failure was the registry's old S1_TITLE method anchor,
not a queue/value mismatch. Parent approved changing only that test-registry
anchor to `titleScreenClearsPriorWorkAndPublishesMainAfterObjectsAndMusic`;
route keys/cardinality/source owners/submission-token requirements remain intact.
The affected guard plus renamed case are rerun together; unchanged ROM producer
and phase results are reused rather than launching a broader repeat.


The registry update plus renamed ROM case passed5/5, zero skips (queue59s,
Maven88s). A subsequent independent source review caught an over-specific wrong
assertion in the new case: native music at `$30DC/$30E0` precedes objects at
`$312A`, deformation `$3130` and sprite build `$3134`. The existing manager ran
objects before music, and the new assertion had entrenched that order. The
caller now queues music first, then executes the first object/background pass,
then submits Main. The regression observes pre-object routine0 at the music
request and routine2 after title setup. The earlier5-pass result predates this
order correction; the affected music/order/retry cases are validated again.

S2's approved local handoff now models VISUAL → FA submission/EXTRA_VINT →
END_WAIT, with a return at each native wait boundary. The next admitted title
iteration consumes the extra SEGA VInt and seeds180; the following PCM iteration
decrements the timer and tests P1/P2 pressed Start. The visual counter freezes
at106. No audio-success, PCM-exhaustion or diagnostic counter supplies authority;
muted, SILENT and unavailable-chant paths still progress through game-loop
admission. The entry palette is active directly, and the three text phases no
longer accept input skips. New state resets at initialize/reset/text entry.
ACTIVE/options/attract/title-VInt timer logic and every shared audio service are
unchanged. The native16-VBlank controller polling cadence remains a separate
unclosed obligation.


The final source-phase selection passed **17 tests, zero failures/errors/skips**,
immediate admission and Maven74s. It ran the two changed S1 music/order/retry
cases plus all three new actual-Engine S2 admission cases, six S2 data cases and
six S2 fade/intro cases. Exact invocation (original verified absolute ROMs were
supplied; placeholders below avoid machine-local paths):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dsonic1.rom.path=<absolute existing S1 World REV01 ROM>' \
  '-Dsonic2.rom.path=<absolute existing S2 World REV01 ROM>' \
  '-Dtest=TestSonic1PlcProducerCoverage#titleScreenClearsPriorWorkAndPublishesMainAfterObjectsAndMusic+mainTitleRetriesOnlyMainWithoutReplayingObjectsOrMusic,TestSonic2IntroInputPhases,TestSonic2SegaScreenFadeTiming,TestSonic2SegaScreenData' test
```

The S2 production cases use the configured headless Engine and a no-device
backend with a physical-device construction trap. Real GameLoop steps under
recorded-input ownership and real outer audio presentations show FA's first
five DAC bytes equal the native ROM sample before cancellation is allowed.
They cover the separate extra-VInt iteration, each pad's Start edge, held Start,
A/B/C negatives, natural text/twinkle progression, reset, SILENT/muted operation
and an unavailable sample. Diagnostic observations belong only to assertions;
production phase behavior reads no diagnostic stream or sample clock.

Citation checking of the changed S2 file still reports the inherited untouched
DeleteObject anchor at the flashing-star handler (old source line832, now838;
its citation `s2.asm:26748-26750` is a caller site, while the checker finds the
DeleteObject label at30330). The line and behavior are byte-identical in
`7d3d5a972`; this is outside the reserved SEGA/STP methods. New phase references
were checked against actual pinned source and ROM. No S3K/disassembly pin was
changed, no native capture was launched, and no full-route proof is claimed.


## Source-phase production remeasurement

The two latest source-phase captures above use the same fixed title-startup
command as the earlier production measurements, now including `compile` before
`exec:java` to refresh the clean source identity. Inputs are the unchanged
verified ROMs and run-local emerald BK2s; output directories are
`s1-engine-600-source-phases` and `s2-engine-600-source-phases` under the same
external task directory. Both invocations use Java25, Lua5.4, no DISPLAY or
WAYLAND_DISPLAY, surfaceless EGL, ordinary GameplayCaptureSession and the
production no-device audio backend. They recorded rows0..599 from real startup,
with no positioned setup, forced later mode or trace-supplied gameplay state.

Each strict comparator invocation used its game's preserved `*-native-600`
control and `--start-row 0 --rows 600`; both returned exit3 for a valid sample
mismatch, rather than incomplete/invalid evidence. S1's complete PCM digest is
unchanged by the caller fix. S2's correct24948-byte chant delivery replaces the
former3819-byte truncation at the movie's row140 A input; Start-only admission
prevents that unsupported cancellation. This is measured single-chant behavior
on the original input, not full-route audio parity. Native FA timing is still
not inferred from PCM onset. The four full native captures remain held behind
production-route, clock and publication/trust prerequisites.


## Prior GM_Sega fade: conditional local feasibility and required seam

The next bounded slice inspected clean `2ff5e37c4` for a genuine prior-screen
PaletteFadeOut using existing admitted title iterations and palette ownership.
The conditional implementation is **not supported by the current local owner**.
No runtime source, test, timing, audio or asset loader was changed. No Maven,
600-row repeat, native or full capture was run for this source-only decision.
The four full native captures remain held.

The native work is unambiguous and is distinct from the removed logo fade-in:
GM_Sega (`sonic.asm:1808-1813`) and SegaScreen (`s2.asm:4095-4100`) stop sound,
ClearPLC and fade the **previous** palette/picture before screen clearing,
direct logo loading and PalLoad. PaletteFadeOut
(`_inc/Palette Fading.asm:134-170`) and Pal_FadeToBlack
(`s2.asm:3370-3420`) perform22 admitted VInt waits with red, then green, then
blue decrements of all64 normal and underwater colors. RunPLC/RunPLC_RAM is
inside each native loop. Their first action is publication/wait, followed by
one color pass; the final all-black pass is a no-op, not permission to skip a
native iteration. No absolute boot offset follows from this work.
Read-only verification of the original pinned ROMs confirms S1
`$2DD2:103C00E4`, sound call `$2DD6:6100E5BC` → `$1394`, ClearPLC
`$2DDA:6100E7FA` → `$15D6`, prior fade `$2DDE:6100F06A` → `$1E4A`;
S2 `$37B8:103C00FD`, sound call `$37BC:6100DBA0` → `$135E`, ClearPLC
`$37C0:6100DEBA` → `$167C`, prior fade `$37C4:6100ECA4` → `$246A`.

The precise missing ownership is visible in the production path:

- `GraphicsManager.setPaletteFadePresentation()` and `PaletteFadePresentation`
  already provide native per-channel arithmetic and re-upload cached rows. The
  remembered palette views are private, potentially mutable aliases. The API
  neither acquires a frozen prior native palette pair nor retains a prior
  indexed scene. `clearPaletteFadePresentation()` re-uploads the old owners,
  so clearing it before the setup handoff can reveal those old colors again.
- S1 `drawSegaLogo()` calls `cacheSegaLogoToGpu()`; S2 also calls
  `applySegaLogoPaletteForFrame()`. Every SEGA draw replaces the prior palette
  and draws the new logo. Applying the shared transform there would fade the
  newly loaded logo, which is the wrong native owner.
- `Engine.resetForGameplayFromMasterTitle()` clears the old session and ROM,
  resets RenderContext and calls `clearPaletteTextures()`. New configured boot
  likewise supplies no previous native palette/scene owner. An empty cache or
  a host hub picture is not an authentic reset palette.
- `GameLoop.initializeTitleScreenMode()` changes mode and zeros the camera
  before calling provider initialization. Engine then clears and draws through
  the new title provider. A manager cannot recover the previous scene after
  that boundary using the permitted init/reset/update hunks.
- `GameLoopTitleEntryFade` / `TitleEntryFadeSource` retain a level picture only
  through a zone-runtime owner. That existing path does not supply a general
  native prior-scene handoff to fresh S1/S2 SEGA entry.
- `FadeManager.startFadeToBlack()` is an alpha-overlay black fade. It does not
  supply the missing native palette mutation or retained indexed scene.

Consequently, a manager-only22-update phase would either be a blind hold,
apply native color arithmetic to the new logo, or fail to preserve what those
colors belong to. None was installed. S1's existing ClearPLC/Main split remains
intact. S2's current early Std1 submission and data-cache setup would also need
to be deferred as part of the eventual owner-correct lifecycle; the existing
`Sonic2PlcService.clearOperation()` can express its native clear without a
service/queue contract change. Std1 belongs to its actual main-title boundary
(TitleScreen, `s2.asm:4415-4421`), not to a manufactured prior fade.

The concrete proposed seam is an **engine-internal retained native presentation
lease**, provisionally `com.openggf.game.internal.NativeTitlePriorPresentation`.
It is a proposal, not an implemented API or an approved shared mutation:

```java
interface NativeTitlePriorPresentation extends AutoCloseable {
    void publishPaletteAtAdmittedVInt();
    void fadeToBlackOnePass();
    void setRetainedClearColor();
    void drawRetained();
    @Override void close();
}
```

Entry composition must acquire and inject this lease **before**
`Engine.resetForGameplayFromMasterTitle()` discards the previous session/ROM/
palette resources and before `GameLoop.initializeTitleScreenMode()` changes
mode/camera. Its input is an actual previous native indexed scene, backdrop,
normal/underwater64-color basis and displayed publication generation. An
actual reset-palette owner may instead explicitly declare the native blank
scene/buffers. Missing ownership must stay unavailable; never infer this from
an empty cache, host RGB image, fixture or a guessed coldboot-ready time.

The graphics/palette owner must detach the native color basis from mutable old
writers, retain indexed scene resources through the lease, and apply the
existing RGB decrement algorithm to **both logical native buffers**. The
publication method uses the existing VInt palette path. Draw/backdrop remain
those of the retained owner; a filtered RGB screenshot/overlay is not a
substitute for indexed scene plus native palette. Logical colors, fade-pass
state and owner/publication identity need a registered rewind adapter with
resource rebinding; GPU names and live old-world references must not become
logical rewind state. Reset, cancel and teardown release only the lease's own
resources and cannot leave a global transform on another scene.

The game-local lifecycle then owns the22 source iterations, each consuming one
already-admitted title VInt: existing sound stop, existing ClearPLC, retain the
prior scene, publish previous colors, perform one native fade pass and service
the existing fade-loop PLC boundary. No SEGA upload, logo palette scan, FA/E1
request, new-scene object update or skip-input branch runs during that phase.
After its final pass, setup/PalLoad takes ownership exactly once, with atomic
palette/scene handoff so releasing the prior lease cannot re-upload old colors.
The source-backed logo palcycle and chant phases begin afterward. No host-wall
clock, PCM-success predicate, new CPU timing kind or checksum delay is involved.

Required shared reservations are concrete: the retained-presentation acquisition/
release hunk around the two named Engine/GameLoop entry methods; an internal
palette/scene lease implementation beside `GraphicsManager` /
`PaletteUploadPresentation`; and its existing gameplay-mode rewind registration.
The title managers then need only prior-phase fields/init/reset/update and
retained drawing dispatch. This is broader than the approved game-local scope;
no such shared file was edited or reservation assumed. No SMPS/chip/audio
producer or ROM loader expansion is proposed for this seam.

Acceptance must use a distinct real prior scene and native color basis, proving
old-scene visibility and R→G→B changes through22 admitted production iterations,
including underwater colors and the first-publication/final-no-op boundaries.
It must reject new-logo/PalLoad/palcycle/chant work before handoff, verify
ClearPLC/stop-before-fade and one setup afterward, ignore both pads' skip
inputs, and cover mute/unavailable audio plus reset/cancel/restore-forward and
foreign-owner isolation. Old source should fail those **owner/visibility/color**
assertions; a test that only observes22 elapsed updates is insufficient. Those
regressions were not claimed executed while the needed capability is absent.
Coldboot checksum/reset readiness, direct logo load elapsed cost, CPU chant
wait and the existing98/96/106 approximations remain open independently.

## Native pause: verified missing ingress and service semantics

The competition pause caller requested an injected native-audio port. A bounded
read-only audit at `a0b1b73955a944b884542b1be241ac6da984f826` confirms that
host `AudioManager.pause()` / `resume()` cannot supply it: they pause/resume
the speaker and scene PCM source. Native pause instead leaves the physical
chip/DAC clock running and changes driver service plus chip registers. No
shared runtime edit, regression execution or new native capture was performed
for this audit. Shared mutation reservations were sent to the parent and S3K
audio owner; this section records a proposed contract, not implemented closure.

The existing S2 `Sonic2SoundRequestPipeline.bridgeMusic()` converts FE to
`stopMusic=7F` and FF to80, and its snapshot retains that byte. However,
`Sonic2SoundRequestService.ForwardBoundary.service()` ignores the music bridge
kind, continues queue dispatch and advances spindash state. No runtime consumer
reads `stopMusic()`. `TestSonic2SoundRequestPipeline` checks only that logical
conversion; it does not establish physical silence, service suspension or
resume behavior. The FE/FF `StopMusic` values in
`AudioManager.commandForMusicOrSystemRequest()` are expressly placeholders that
the bridge intercepts, rather than an implemented native pause consequence.

An independent read-only S2 audit verified the existing REV01 ROM identity and
decoded the source-owned Saxman archive in memory: LEA at `$EC04A` selects
`$EC0E8`, compressed length `$0F64`, decompressed4872 bytes, SHA-256
`5fd429a9e64fe5b2975dae05bb44ced601f1662df1006dd5a8d1c8e505d79e75`.
The decoded zVInt (`s2.sounddriver.asm:393-408`) at `$0038` reads `StopMusic`
at `$1B83`; nonzero calls
zPauseMusic (`s2.sounddriver.asm:1422-1463`) at `$0600` and then jumps to the
DAC loop at `$00CC`. First
pause sets the separate `$1307` latch toFF, calls FM silence at `$0B36`
and PSG silence at `$05F4`. FM silence keys off all six channels and writes
FF through registers `$30..$8F` on both FM parts, as zFMSilenceAll
(`s2.sounddriver.asm:2518-2541`) specifies; PSG silence writes all four
volume latches. Repeated pause does not repeat these writes. Resume at `$0613`
clears both flags and restores voices/panning only for active, non-overridden
music FM/DAC and SFX FM tracks. **The resume invocation also skips ordinary
sequencing.** Its following invocation resumes normal work. The original
PauseGame (`s2.asm:1571-1639`) overwrites Music0 directly; ordinary PlayMusic's alternate
mailbox fallback is not an exact replacement for that caller operation.

S3K uses a different native latch. Pause_Game (`sonic3k.asm:1550-1627`)
writes1 on entry and80 on resume or slow-frame advance. The verified locked
ROM SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6` contains
`13FC000100A01C10` at `$1420`, and `13FC008000A01C10` at `$14A4` and
`$14D4`; these are the S&K-half writes to `$A01C10`. They were read from
the existing absolute ROM, without copying it. Source-owned Kosinski decoding
of `$F6960` in memory consumes3580 bytes and produces4459 bytes, SHA-256
`ec9cd3c080d0be6d29bf39b1fedcdc907e34b82e285d47a79291092643d5163f`.
The decoded `$07FF` starts `21101C7EB7C8FA1008D13DC03602C38D09`,
corroborating the flag pointer, zero/negative branches, removed service return,
and1→2 transition before pause writes at `$098D`.

zUpdateEverything (`Sound/Z80 Sound Driver.asm:653-701`) calls zPauseUnpause
before SFX, music fades and request consumption. zPauseUnpause
(`Sound/Z80 Sound Driver.asm:2232-2301`) changes1 to2 and skips the remaining
service;2 skips without repeating physical writes. Negative80 clears the
flag, restores panning, then **continues that same service**. A pending fade
instead takes its native global-stop branch. zPauseAudio
(`Sound/Z80 Sound Driver.asm:2541-2592`) silences all four PSG channels both
before and after its FM writes under shipped `fix_sndbugs=0`, clears FM1..5
panning and sends the native key-off selectors including the channel gap.
FM6/DAC remains outside that pause path. zVInt
(`Sound/Z80 Sound Driver.asm:470-529`) still returns to digital audio after
the driver service; a host PCM pause would suppress the wrong clock.

The shipped resume traversal must not be silently replaced with its fixed
branch. It includes the extra PSG slot in the music walk, whose writes still
pass through zWriteFMIorII's voice/override checks, and starts seven SFX reads
at `$1F40`, the **end** of the SFX table, with `$30` stride. The existing
ownership projection describes live tracks, not this tail/address space or
raw Z80 memory. No missing byte is assumed zero, and no claim is made here
about the resulting out-of-table writes. The S3K owner must resolve the
required native-memory projection before exact shipped resume is certified.
The source comments alone do not prove an audible pan defect.

A preserved primary-source check resolves the address mapping, without
supplying missing runtime values. GPGX `core/memz80.c:120-127`,
`z80_memory_r()`, maps both `$0000..$1FFF` and `$2000..$3FFF` to
`zram[address & 0x1FFF]`; `core/genesis.c:143-152`, `gen_init()`, installs
that handler and the matching fetch mirror. Thus the final three slot bases
`$2000/$2030/$2060` alias `$0000/$0030/$0060`. The earlier four bases still
read actual upper RAM. The preserved source is under the external
`native-reset-prefix-20261021/source/waterbox/gpgx/Genesis-Plus-GX` task tree;
its install-qualified source-lock/identity pair names GPGX
`051d430d3d1b54625f9900c8f152d7f232e06daf` and BizHawk
`427556b5ef3ac437eba754d90c5e7e9096c9a8df`. The export has no `.git`, so
that revision attribution comes from its lock, not a local Git checkout.
This source observation does not attest the complete native runtime or
measure pause PCM, and the mirror is not permission to invent upper-RAM bytes.

The proposed injected port is non-Mod-API `AudioNativePauseAccess` bound to
the caller's injected AudioManager, exposing `requestPause()` and
`requestResume()`. Domain code owns its native previous-input planner and
pause/frame-advance call order. Game-owned policy translates those semantics
to S2's mailbox/latches or S3K's1/80 flag; shared code must not branch on a
game name. Internal typed presentation/session consequences belong inside the
existing composite forward transaction, including rollback and held-rewind
staging. Adding an ordinary public `AudioCommand`, calling host pause/resume,
muting a sink or installing a no-op port would not close this contract.

Required reservations cover the internal AudioManager ingress/staging,
presentation command/applier and forward-service consequence bridge, session
command/service and snapshot, driver service gate/projection and snapshot,
plus the game-owned policies and S2 bridge consumer. The existing native
music alias adapter, sequencer and chip core need no proposed mutation.
Pending/held/resume latch state and chip state must capture/restore together;
request retention and source load/SEGA transport ordering require explicit
tests rather than a blanket early return. Acceptance needs exact first-pause
register writes, idempotent hold, each game's distinct resume service rule,
active/overridden voice selection, queued-request retention, continuing
non-device PCM/DAC time, and snapshot/rollback/forward equivalence. These
regressions remain unexecuted until the shared implementation is reserved.
The original startup600 failures and all full-route/publication dependencies
remain unchanged; all four full native captures remain held.


## Native pause implementation checkpoint

The parent subsequently reserved the internal pause ingress, session transaction,
source bridge, private timeline and exact prune-owner hunks. The implementation
adds `com.openggf.audio.AudioNativePauseAccess.bind(AudioManager)` returning an
injected `Port` with `void requestPause()` / `void requestResume()`. These calls
accept a pending native mailbox write; they do not claim immediate service,
pause a speaker, alter PCM rate or use an AudioManager singleton. Unsupported
host profiles reject before mutation. No public Mod API command, timeline entry,
logical snapshot signature or GameAudioProfile shape is expanded.

S2's port overwrites Music0 directly with FE/FF. Its normal bridge still owns
whether that source can transfer; blocked compressed loads retain the unbridged
write. After transfer, StopMusic gates queue/priority/spindash work while the
session retains its pending/held latch. The session emits the native202-write
mute once, idles without sequencing while held, and emits validated native
resume voice writes before skipping that resume invocation. Chip/DAC rendering
continues independently. PCM transport completion is not a pause acknowledgement:
only an actual session service receipt lets the source clear a consumed80.
Queued commands, native request state and physical state share the existing
composite forward rollback boundary. A native event enters history only when
that consumed service and its source outcome commit.

`Sonic2NativePausePolicy` supplies ordered writes from the current foreground
logical program and track state: six descending native key-off selectors, both
FM parts' register clear, four PSG attenuations; then DAC/music FM and SFX FM
resume slots in native order. Active non-overridden resting tracks remain
eligible; PSG and saved overrides are not restored. A null FM/DAC cache resolves
its native current/default instrument index from the selected ROM-backed
program. Missing voices and ambiguous native owners reject the complete plan
before writes. This preserves the shipped eight-bit TL add and duplicated pan
writes. Existing snapshots omit reserved raw pan bits, so arbitrary script
values using those unrepresented bits are outside the verified resume projection.

The existing AudioCommandTimeline has one private ordered tagged history.
Internal events retain the committed source snapshot and do not alter public
command indices, orders or counts. The non-Mod-API session snapshot carries its
internal cursor. Keyframe replay traverses both kinds in service order; held
replay borrows the selected session/source state, captures a detached result,
then rolls live state back before publishing the selection. Replacing that
selection invalidates an older prepared restore. Reverse release restores the
selected source state before the irreversible producer commit and rolls it back
on a pre-commit failure. Pruning uses the same earliest retained audio
snapshot's exact internal cursor after the original public prune; a public
command count cannot identify native events in a command-free interval.

S3K exact native pause/resume remains **unsupported**, not a no-op binding or
a fixed-branch approximation. The parent's independent ROM/source audit narrows
the missing projection: `$1F40/$1F70` are the actual retained1-up backup
PSG2/PSG3 slots, whereas `$1FA0/$1FD0` fall in the Z80 stack region. The
resume walk conditionally reads playing at00, voice at01 and pan at0A; its FM
write helper rechecks00/01. The twelve upper-RAM bytes therefore need immutable
values **and knownness** at the actual service, captured with driver state.
Backup bytes must derive from the actual retained save, never current live PSG.
Stack bytes need actual writer tracking or a proved non-write bound, not
zero-filled fields. The existing mirror handles the remaining slot bases.

The native audit corroborates cold RAM clear through `$1FFF`, SP=`$2000`, and
StopAllSound's shipped over-clear through `$1FD3`; `$1FDA` is outside that
clear. A normal1-up save clears bit7 on its saved tracks, but unchecked indexed
writers were not proved unable to change them. CALL/PUSH/interrupt history at
resume remains unrepresented. Source identity remains the verified locked ROM
and driver digest above. This missing projection is a concrete separate seam;
no stack bytes, paused gameplay values or native callback attestation were
invented. Domain caller composition belongs to Mode/parent and is not part of
this audio commit. Startup600 comparisons remain failures and four full native
captures remain held.

Qualification used Java25.0.5, Lua5.4, no DISPLAY/WAYLAND and the original
absolute S2 REV01 ROM. All commands used `maven_queue.py --lean -Dmse=off`.
The first exact15-class run reported158 tests:152 passed,2 failed,4 errored,
0 skipped (queue283s, Maven56.261s). It compiled before the final rewind-stage
corrections and is **not** a current-source pass. The new policy's19 cases,
private timeline's7 cases, existing public timeline/index/keyframe cases,
Mod API signature check and session/presentation architecture guards passed.
Native pause was initially sent to a registry that owns voice metadata rather
than control; the applier now leaves that registry intact for this command.

The six-class affected run used
`-Dtest=TestAudioNativePause,TestSmpsNativePauseSession,TestAudioPresentationSnapshotParity,TestAudioKeyframeReplay,TestSmpsSessionSnapshot,TestSonic2RequestConsequencesRom`
and the absolute `-Dsonic2.rom.path` documented above. It reported49 tests:
47 passed,2 failed,0 errors/skips (queue390s, Maven86s). This run tested the
final production source: actual-ROM mailbox/voice writes, blocked transport
and compressed-load retention, failed forward commit, selected reverse staging,
both sides of reverse release, native-only history floor/prune/rebranch and
existing snapshot/keyframe/request consequences. The remaining failures were
new test observations: a capture drain counts requested consumer frames even
without fresh producer PCM, and chip snapshot/strobe flush can preclock the
core and fill its output cache. Neither establishes new core-clock progress.

Only those observations changed afterward. The manager fixture now requires
an actual fresh producer packet before draining it. The DAC fixture drains the
finite cached PCM until actual DAC index progress, while requiring nonzero PCM
and unchanged logical sequencing. The exact final selection was
`-Dtest=com.openggf.audio.TestAudioNativePause#actualMailboxPauseHoldsSequencingAndResumeSkipsExactlyItsOwnInvocation+failedForwardCommitKeepsPendingRequestWithoutPublishingHistoryOrPcm,com.openggf.audio.session.TestSmpsNativePauseSession#heldNativePauseContinuesDacClockAndProducesPcmWithoutAdvancingTracks`
with the same original ROM:3 passed,0 failures/errors/skips (queue335s,
Maven31.119s). Production source was unchanged between these two runs.

The actual prune-owner check used
`-Dtest=com.openggf.game.rewind.TestRewindControllerAudioSuppression,com.openggf.game.rewind.TestRewindController#pruneHistoryAlignsToEarlierKeyframeBoundary+pruneHistoryDoesNotDiscardWhenWithinRetentionWindow`:
10 passed,0 failures/errors/skips (queue355s, Maven29.152s). Earlier unaffected
passes are reused by their tested source, rather than claiming one all-green
combined run. `git diff --check` passed. The citation check retains one
inherited, byte-identical stale zUpdateEverything anchor in SmpsDriverSession's
pre-existing load comment; the new policy/document citations add no stale
anchor. No new native, full-capture, broad smoke or release-suite run was
launched. Parent owns combined composition validation.

The first commit attempt correctly stopped at the installed policy gate: its
source approximation treated the new private HistoryEvent declaration as
exposed API despite the passing exact signature check. Parent-owned bccb34a88
and4d9a63a57 dependencies were imported as bd934e15f and97cf08bfc, respectively;
the latter import contains only the two mirrored hooks and Python regressions,
excluding the parent's plan. The parent qualified the final repair with51
Python safety cases including installed PowerShell. No signature pin, version,
public API or runtime behavior was changed to satisfy this false positive.
