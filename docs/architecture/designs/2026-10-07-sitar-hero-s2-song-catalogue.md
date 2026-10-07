# Sitar Hero: the complete Sonic 2 song catalogue

Origin: the 2026-10-07 full-song task, shared arrangement contract
`cabd66f442af65e62711a52eaad7a1cbbbe4e4a3`, isolated branch
`feature/ai-sitar-hero-full-s2`. This worker owns the S2 provider and its tests;
the parent owns registration, the shared curator and the expanded preparation cap.

## ROM and reference identity

The input is the main workspace’s `s2.gen`: 1,048,576 bytes,
CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`
(World REV01). Runtime music, voices, PSG envelopes and DAC samples come only
through `Sonic2SmpsLoader` and the ROM pipeline. No disassembly asset is packaged.

Research uses the existing main-workspace `docs/s2disasm` reference at
`380f37a731bfc720bb0371a35a593184a7ec5e43`. Its existing local changes remain
untouched. The reference's filename/`MusID_*` numbering is **not** the loader's
dispatch numbering: `zMasterPlaylist` remaps logical requests to packed song-bank
entries. The IDs below are the existing `Sonic2Music`/`Sonic2SmpsLoader` IDs that
`SceneMusic.prepare` actually accepts; e.g. EHZ is `$81`, CPZ `$8C`, Credits `$BD`.
The `82 - EHZ.asm` filename is not an instruction to request `$82` from the host.

## Inclusion inventory and complete forms

All eleven single-player zone songs, three distinct two-player zone songs,
the unused-but-ROM-present Hidden Palace song, Options, Special Stage, Boss,
Final Battle, the substantial two-player menu/results composition, Ending and
Credits are included: **22 songs**. Shared act music is one song, not a duplicate
entry per act. The original four S2 draft profiles are retained as evidence for
EHZ, CPZ, ARZ and CNZ; this inventory is not constrained by that draft.

`I/L` are intro/body lengths in native duration ticks **after** the header
divider, obtained by executing calls, counted loops, duration reuse and top-level
jumps. They are not PCM repetitions. `D/T` is header divider/hex tempo; `Q` is
the authored quarter grouping in **raw** SMPS duration units. Frame pairs are
`introFrames/loopFrames`. Every looping entry plays 7,200 NTSC service frames:
`max(7200, introFrames + 2*loopFrames)`; each full form below fits inside that
duration twice. A loop's frame count is rounded up to cover either accumulator
phase, not fitted to an audio file.

| Host ID | Song / provider label | I/L ticks | D/T | Q | Frames I/L | Stage zone/act |
|---|---|---|---|---|---|---|
| `$81` | Emerald Hill | 128/1536 | 1/9E | 16 | 208/2489 | 0/0 |
| `$8C` | Chemical Plant | 768/2688 | 1/EE | 24 | 826/2892 | 1/0 |
| `$86` | Aquatic Ruin | 12/1920 | 1/E0 | 24 | 13/2195 | 2/0 |
| `$83` | Casino Night | 24/960 | 1/48 | 12 | 87/3414 | 3/0 |
| `$94` | Hill Top | 0/1920 | 1/BE | 24 | 0/2587 | 4/0 |
| `$84` | Mystic Cave | 96/2304 | 1/B6 | 24 | 135/3241 | 5/0 |
| `$8F` | Oil Ocean | 300/2016 | 2/D0 | 12 | 369/2482 | 6/0 |
| `$82` | Metropolis | 384/2304 | 1/EA | 24 | 420/2521 | 7/0 |
| `$8E` | Sky Chase | 96/768 | 1/5B | 12 | 271/2161 | 8/0 |
| `$90` | Wing Fortress | 18/1440 | 1/88 | 12 | 34/2711 | 9/0 |
| `$87` | Death Egg | 0/1152 | 1/60 | 12 | 0/3072 | 10/0 |
| `$91` | Emerald Hill (2P) | 48/768 | 1/5B | 12 | 136/2161 | 0/0 |
| `$85` | Mystic Cave (2P) | 24/2880 | 1/EC | 24 | 26/3125 | 5/0 |
| `$80` | Casino Night (2P) | 96/1920 | 1/BD | 24 | 130/2601 | 3/0 |
| `$9B` | Hidden Palace (S2) | 144/2592 | 2/E0 | 12 | 164/2963 | 0/0 concert |
| `$89` | Options (S2) | 0/192 | 1/87 | 12 | 0/365 | 0/0 concert |
| `$88` | Special Stage (S2) | 672/2304 | 1/FF | 24 | 674/2314 | 0/0 concert |
| `$8D` | Boss (S2) | 0/1920 | 1/E3 | 24 | 0/2166 | 0/1 EHZ boss stage |
| `$8B` | Final Battle (S2) | 0/2208 | 1/A9 | 24 | 0/3345 | 10/0 Death Egg |
| `$92` | 2 Player Menu (S2) | 0/1008 | 1/68 | 12 | 0/2482 | 0/0 concert |
| `$8A` | Ending (S2) | finite: 2598 ticks | 2/97 | 12 | **4406 total** | 0/0 concert |
| `$BD` | Credits (S2) | finite: 7977 ticks | 1/F0, changes below | 24 | **9527 total** | 0/0 concert |

Zone/act indices are the public `Sonic2ZoneRegistry` indices, not raw ROM zone
IDs. Each ordinary zone song uses its matching registered zone and Act 1;
two-player variants use their matching zone. Non-zone songs explicitly use the
compatible EHZ concert stage. REV01's removed HPZ has music but no loadable stage;
it also uses that explicit EHZ concert stage, rather than passing the unused raw
HPZ index as if the host supported it. The test checks every stage/act exists.

The shared contract has integer `introBeats`; ARZ's half-quarter pickup, OOZ's
12.5-quarter introduction, and WFZ's 1.5-quarter pickup therefore use a ceiling
in that coarse field. Their exact native tick/frame boundaries remain above and
in `introFrames`. Do not reconstruct those exact timings from `introBeats`.
These providers' ownership sections do not switch at the rounded pickup boundary.
Ending and Credits similarly use a ceiling for their final fractional quarter;
their end frames, not rounded beat counts, determine the natural stop.

## Explicit exclusions

This accounts for all other nine songs in the 31-entry music inventory.

| Host ID | Cue | Reason |
|---|---|---|
| `$93` | Super Sonic | Explicitly excluded power-up music, even though it contains a developed 1152-tick pattern. |
| `$99` | Invincibility | Temporary power-up loop (48-tick pickup + 576-tick body). |
| `$DC` | Drowning | Accelerating short warning/countdown, ending at 486 ticks. |
| `$98` | Extra Life | Brief 1-up fanfare followed by the native previous-music restoration flag. |
| `$96` | Title Screen | Finite 432-tick/540-frame title fanfare, about nine seconds; a jingle, not a full title song. Never artificially repeated. |
| `$97` | Stage Clear | Short result cue, 240 native ticks after divider. |
| `$B8` | Game Over | Short finite failure cue; longest track stops at 672 ticks. |
| `$9C` | Continue | Short finite countdown-screen cue, 480 ticks. |
| `$BA` | Got an Emerald | Brief reward jingle; longest delayed PSG tail ends at 143 ticks. |

The **2 Player Menu** is included despite the host enum's `RESULTS_2P` name and
the source's `Results_screen_2p_*` labels: its 1008-tick, 84-quarter composition
contains several developed melodic and harmonic sections and a complete perpetual
song form. It serves the two-player menu/results context and is not a short result
jingle. A screen name alone is not grounds to discard a substantial song.

## Owning routines and form evidence

References are labels in `s2.sounddriver.asm` and the corresponding file in
`sound/music/`, rather than byte-perfect line-number assumptions:

- `zBGMLoad` (`.nospeedshoes`, lines 1820–1822 in the inspected tree) seeds
  `CurrentTempo = TempoTimeout = header tempo` and installs header pointers/divider.
- `zUpdateMusic` and `TempoWait` (545–619) use the shipped `FixDriverBugs=0`
  branch: add the tempo before the track walk; on no carry increment every
  music duration before the walk decrements it. The fixed branch moves this
  service and changes the first-note delay; it is not the branch modeled here.
- `zDACUpdateTrack`, `zFMDoNext`, `zSetDuration`, `zFinishTrackUpdate` and
  `zPSGUpdateTrack` (759–957, 1123 onward) own note/duration reuse and ties.
  Explicit duration bytes are multiplied once by the current track divider with
  native byte semantics; implicit duration reuse reads the already-scaled byte.
- `cfJumpTo`, `cfRepeatAtPos` and `cfJumpToGosub` own unconditional jumps,
  indexed counted loops and calls/returns. A complete form expands these;
  the first reused subroutine or repeated note is not a song boundary.
- `cfSetTempo`/`cfSetTempoMod` own Credits' DAC-issued tempo changes.
- `cfStopTrack` and `zDACStopTrack` establish natural completion. Last delayed
  PSG stops are included: Ending's FM/DAC end is 2592, but PSG1 ends at 2598;
  Credits' FM1–4 end at 7968, but PSG1 ends at 7977.

Examples that killed shorter-loop interpretations:

- `MCZ_DAC` repeats 192 ticks while `MCZ_Jump01`/the full FM form spans 2304.
- `End_Boss_DAC` repeats 96 ticks; the full Final Battle is 2208.
- `DEZ_Jump00` is a 192-tick bass pattern inside the 1152-tick song.
- `SpecStg_Loop0A`, `Loop08` and `Loop05` establish the 2304-tick whole melodic
  and harmonic form after the 672-tick introduction. FM1 executes seven identical
  384-tick bass passes and FM5 28 identical 96-tick ostinato passes inside 2688-tick
  jump groups. The backing **musical events** also repeat at 2304 ticks, independently
  verified over units 672–2975 and 2976–5279. Treating the loop-counter groups as
  different musical material would manufacture a 16128-tick LCM-sized arrangement.
- HPZ has deliberately staggered loop entries: FM1 at 48, FM2 at 144, FM3/5
  at 72, FM4 at 60 ticks. Use the last distinct introductory material's 144-tick
  boundary with a full 2592-tick body, preserving the voices' existing offsets.

For constant tempo `T`, native unit `U` occurs at zero-based service frame
`ceil((U+1)*256/T)-2`. Intro zero stays zero in arrangement metadata; the actual
first service may include the native one-frame delay. Conservative body duration
is `ceil(L*256/T)`. Tests separately walk the ROM stream and the production
sequencer on every service frame, comparing each attack's channel, source pointer
and service frame through the complete 120-second window or the natural ending.

## Arrangement ownership

Channels in this table are **one-based source labels**; provider sections use
zero-based FM/PSG indices. Paired voices are genuine harmony/doubles, never
pitch-ranked alternatives. Chords still require concurrent distinct pitches in
the shared curator. Delayed or detuned unison doubles are marked non-chorded.

| Song | Sitar lead | Harp accompaniment | Synth |
|---|---|---|---|
| EHZ | FM2/3 melodic duet | FM4/5 harmony | PSG1/2 harmony |
| CPZ | FM1 lead | FM4/5 stabs | **PSG3 noise**, no pitched PSG1/2 |
| ARZ | FM2, FM3 delayed echo | FM4/5 harmony | PSG1/2 |
| CNZ | FM2/5 melodic harmony | FM3/4 harmony | PSG1/2 |
| HTZ | FM1 bending melody | FM4/5 harmonic response | **Absent: all PSG tracks stop** |
| MCZ | FM4, FM5 delayed double | FM2 bass/groove | PSG2 pitched countermelody |
| OOZ | FM1 instrument-changing lead | FM4/5 harmony | PSG2 lead response |
| MTZ | FM5 riff | FM3/4 harmony | PSG1/2 octave response |
| SCZ | FM1 melody | FM4 arpeggiation | PSG2 accompaniment |
| WFZ | FM1, FM5 melodic double | FM3 stabs | **Absent: all PSG tracks stop** |
| DEZ | FM1 arpeggio | FM3, FM4 detuned double | PSG1, PSG2 detuned double |
| EHZ 2P | FM1/3 melodic harmony | FM4/5 accompaniment | PSG1/2 |
| MCZ 2P | FM3 melodic line | FM1/4 response | PSG2 pitched response |
| CNZ 2P | FM2, FM4 double | FM3/5 harmonic response | PSG1/2 |
| HPZ | FM1, FM4 delayed double | FM3, FM5 alternate voice | **PSG3 pitched arpeggio**, not noise |
| Options | FM2, FM5 delayed double | FM3 arpeggio | PSG1 arpeggio |
| Special Stage | FM2 melody | FM3/4 chord responses | PSG1, PSG2 delayed echo |
| Boss | FM5, FM4 detuned double | FM2/3 harmony | **PSG3 noise**, PSG1/2 stop |
| Final Battle | FM3, FM5 double | FM1/2 dissonant ostinato | PSG1/2 pitched response |
| 2 Player Menu | FM1/5 lead and response | FM3/4 harmony | PSG1/2 |
| Ending | FM1 melody | FM4/5 harmony | **PSG3 pitched melody**, not noise |
| Credits | FM1 medley lead | FM3/4 harmony | PSG1/2 pads/response |

Every included song has a real DAC stream and supports Bongos. Each provider's
`firstDacUnits` contains exactly the first seven positive inter-attack durations,
before header scaling; the ROM oracle checks all 154 entries. In particular EHZ's
initial duration-only/hold sequence is `12,32,20,4,8,4,8`; DAC does not acquire
the pitched tracks' no-attack semantics.

The owning `zDACPtrTbl` and `zDACMasterPlaylist` (3879–3931) select seven actual
sample families: `$81` Kick, `$82` Snare, `$83` Clap, `$84` Scratch,
`$85/$88/$89/$8A/$8B` Timpani at different native playback rates,
`$86/$8C/$8D/$8E` Tom at different rates, and
`$87/$8F/$90/$91` **Bongo** at different rates. SMPS macro names such as
`dVLowClap` are aliases, not proof that the last family contains a clap sample.
The shared curator must cover scratch, timpani and bongo hits using these real
families; this provider does not own pad-family mapping.

## Credits tempo boundaries and shared-curator integration

Credits begins at `$F0` and its DAC track changes the global tempo as follows.
These frame boundaries come from the native accumulator walk, and the complete
production progression agrees. The provider marks those native quarter section
boundaries explicitly, retaining FM1 ownership throughout.

| Native unit | Quarter at Q=24 | Service frame | New tempo |
|---|---|---|---|
| 864 | 36 | 921 | `$EA` |
| 4128 | 172 | 4492 | `$CD` |
| 4704 | 196 | 5211 | `$C5` |
| 6144 | 256 | 7083 | `$C0` |

A global `firstDacUnits`/samples-per-beat calibration is insufficient to locate
all these later quarters. Parent integration must use actual prepared native
progression for those later authored sections, never extrapolate the first tempo
across the medley. The provider's natural duration is independently verified and
does not depend on that chart-grid integration. No natural form approaches the
36000-frame cap; the longest included song is Credits at 9527 frames.

## Verification and boundaries

Worktree: `.worktrees/ai-sitar-hero-full-s2`, base `cabd66f44` plus this task's
owned provider/tests. Java is OpenJDK 21.0.12.1. Hooks installed with
`tools/testing/install-hooks.sh`; main branch and dirty submodules preserved.
The automatic worktree hook created ROM symlinks; this worker removed only its
new generated ROM links and all tests use absolute main-ROM paths.

Commands below use a policy-compatible variable for the exact absolute main-ROM
paths used by the completed runs. Only the machine-local path spelling is normalized.

```bash
OPENGGF_ROM_ROOT="$(git rev-parse --path-format=absolute --git-common-dir)/.."
OPENGGF_ROM_ROOT="$(cd "$OPENGGF_ROM_ROOT" && pwd -P)"
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSitarHeroS2SongCatalogue \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" test -B
python3 tools/testing/run_categories.py --base cabd66f44
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base cabd66f44 --preflight
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSitarHeroS2SongCatalogue,TestSitarHeroCharts,TestSitarHeroModel \
  -Dsonic1.rom.path="$OPENGGF_ROM_ROOT/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_ROM_ROOT/s3k.gen" test -B
```

Initial focused provider run: **3 tests, zero failures/errors/skips**, 51.46s
test time, 1m13s Maven time. It compiles/packages the actual example, verifies all
22 metadata entries, compares each song's full native progression with production,
and prepares 2400 frames per song through `SceneMusicFactory` to corroborate real
selected primary/harmony/noise/DAC parts and the two absent PSG roles.
Final combined run: **18 tests, zero failures/errors/skips**, BUILD SUCCESS,
1m22s Maven time. `TestSitarHeroCharts`: 5 tests/11.44s;
`TestSitarHeroModel`: 10 tests/0.116s;
`TestSitarHeroS2SongCatalogue`: 3 tests/50.15s. All three ROM properties pointed
to verified existing absolute main-workspace ROM paths; no ROM tests skipped.
After adding the explicit registry-music equality assertion for zone pictures
and reference waveform-mode assertions for every synth selector, the metadata
and native progression methods were rerun separately (command below). The
unchanged PCM preparation checks were not unnecessarily repeated.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSitarHeroS2SongCatalogue#completeInventoryUsesCompatibleStagesAndHonestRolePresence+nativeControlFlowAndServiceProgressionMatchEveryProductionSong' \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" test -B
```

Final metadata/native rerun: **2 tests, zero failures/errors/skips**, BUILD
SUCCESS, 1.125s test time and 23.443s Maven time. Evidence-document fence syntax,
owned-file paths, all cited owning routine labels and `git diff --check` passed.

The change-based plan selects 3007 ordinary classes plus guards because the new
example catalogue and oracle helper are unclassified. That broad engine run is
disproportionate to this pure game-specific metadata provider; there are no shared
algorithm, timing, physics or public API changes in this branch. Focused native
control-flow/service progression, actual ROM preparation, catalogue/stage checks
and existing example-model/chart regressions cover its production consumers.
This is focused validation, not a full-suite claim. Initial preflight correctly
rejected the system `lua` version; explicit `LUA_BIN=/usr/bin/lua5.4` passed Java,
Lua and PowerShell prerequisites. No category engine run was started and no
category diagnostic receipt exists to acknowledge.

Temporary assembly-count and diagnostic outputs stay under `target/` and are
regenerable from the ROM/disassembly in minutes. The independent regression
oracle is retained as the game-specific test helper `SitarHeroS2NativeProgram`,
with purpose, input and originating-task header. No playlist rip, waveform-loop
measurement, ROM copy or runtime fallback has been introduced.

Parent delivery still owns unified registration/curation, full-duration PCM
preparation once its old 5400-frame cap is expanded, integration/push and cleanup.
This worker does not modify the shared draft, common design or changelog, does
not push or integrate main, and leaves its accounted worktree for the parent.

## Native saved-frequency and saved-duration reference correction

The initial owned commit `f975bc90300bfe919746ad64ec51e088b192913f` was merged
by the parent. Its attack comparison passed with the original production path,
but that agreement missed a native PSG guard in both implementations. The parent
then supplied production repair `03e0c2ac04f561fc271b097434579e140d74cabb`,
based on `f71d88a3f`; the clean owned S2 branch fast-forwarded to that repair.
This follow-up changes only the S2 comparison helper, S2 tests and this evidence
document. It neither changes the shared sequencer nor fits a song-specific count.

The inspected owning instructions establish two distinct saved-RAM semantics:

- `zPSGDoNext` (1147–1170) clears PlaybackControl's rest bit, but a positive
  duration byte bypasses `zPSGSetFreq` and therefore does not change Freq.
  `zPSGSetFreq.restpsg` (1190–1196) stores `$FF` in both frequency bytes for an
  explicit `$80` rest. `zPSGDoNoteOn` (1202–1204) tests FreqHigh bit 7 and jumps
  to `zSetRest`. Clearing the rest bit alone does not make `$FFFF` playable.
  By contrast, `zFMDoRest` (918–923) stores `$0000`, and FM has no corresponding
  saved-frequency sign guard. `zNoteFillUpdate` (972 onward) cuts off a note
  without replacing Freq, so a later positive byte can reattack that saved pitch.
- `zSetDuration` (929–941) multiplies only a newly supplied duration, storing
  the byte result in SavedDuration and DurationTimeout. `zFinishTrackUpdate`
  (948–952), and the DAC note-without-duration path (779–783), copy that saved
  byte into DurationTimeout without another multiplication. `cfSetTempoDivider`
  (3168–3170) changes TempoDivider only. A divider change therefore affects the
  next explicit duration, not an implicit reuse of SavedDuration.

The independent ROM probe on the original helper reproduced Oil Ocean's three
reference-only PSG0 tuples. Decompressed ROM program bytes `$306..$310` are
`EC 03 80 60 60 F5 00 EC FD F8 41`; the owning source is `OOZ_Jump02` in
`sound/music/84 - OOZ.asm`, specifically `nRst,$60,$60`. The rest's first `$60`
stores `$C0` (192 ticks) after divider 2; the second `$60` extends the silence.
The pointer after that positive duration is `$30B` in each complete loop.

| Native unit | Service frame | Source pointer after duration | Incorrect saved note |
|---|---|---|---|
| 498 | 613 | `$30B` | `$80` |
| 2514 | 3094 | `$30B` | `$80` |
| 4530 | 5575 | `$30B` | `$80` |

The S1 worker's repaired-production probe reported Freq=`$FFFF`, rest=true,
SavedDuration=192 and divider=2 at those tuples, with no production-only tuples.
The S2 helper now retains PSG saved-frequency validity independently of the rest
bit, initialized from `zInitMusicPlayback`'s zeroed track RAM. Only a new pitched
byte replaces an explicit rest's sentinel. It also stores the scaled duration
once rather than rescaling its raw predecessor after E5. Oil Ocean PSG0 has 293
authentic attacks in the requested window; the previous 296 included those three
silent units. No catalogue timing, role selector or natural-end metadata changes.

Two new instruction-derived regressions first failed against the unchanged S2
helper on the repaired production base: **2 tests, 2 failures, zero errors/skips**,
0.966s test time, 1m14s Maven time (plus 33s queue admission). The sentinel case
incorrectly admitted units 12 and 16 inside an F7 duration-only loop. The divider
case produced `[0,6,18,26,28]` instead of native `[0,6,12,20,28]`; its total end
time coincidentally remained 31, demonstrating why an end/count check alone is
insufficient. After correction both regressions compare complete source-pointer
and service-frame attacks with production. The sentinel regression also observes
`$FFFF` and rest=true across the loop, recovery on a new pitched byte, FM's
different behavior, and a subsequent duration-only attack reusing that recovered
PSG pitch.

An intermediate run on the corrected helper passed the complete 22-song method
and divider regression but failed an extra exploratory note-fill state assertion:
**3 tests, 1 failure, zero errors/skips**, 1.070s test time, 21.982s Maven time.
The extra fixture used `E8,1` followed by duration-only data; production's PSG
`resting` flag remained false before the next stream unit, whereas native
`zNoteFillUpdate` sets that bit. The S2 continuing-note branch of
`SmpsSequencer` already explicitly documents its elapsed-comparison note-fill
implementation as unverified against native semantics and calls `stopNote`
without setting rest. This is a pre-existing state-parity gap, outside the owned
reference-omission repair, and was reported to the coordinator. The final sentinel
fixture omits E8 and tests only explicit-rest sentinel persistence and recovery;
it does not claim to certify note-fill state or chip-register parity.

The bounded verification command uses Java 21.0.12.1, Lua 5.4.9 and the same
verified absolute main S2 ROM path; it runs the two new regressions plus all 22
complete requested streams and their natural stops, through the native service
clock rather than a waveform:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSitarHeroS2SongCatalogue#psgRestSentinelSurvivesDurationOnlyLoopButNoteFillRetainsPitch+nativeSavedDurationReusesScaledByteAcrossDividerChanges' \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" test -B
# The intermediate run added +nativeControlFlowAndServiceProgressionMatchEveryProductionSong
# to the same then-current method names above. The final fixture/method is below.
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSitarHeroS2SongCatalogue#psgRestSentinelSurvivesDurationOnlyLoopAndRecoversOnPitch+nativeSavedDurationReusesScaledByteAcrossDividerChanges+nativeControlFlowAndServiceProgressionMatchEveryProductionSong' \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" test -B
```

Final corrected run on the production repair base plus this owned patch:
**3 tests, zero failures/errors/skips**, BUILD SUCCESS, 1.144s test time,
23.053s Maven time after 579s queue admission. Every requested S2 stream agrees
at each attack's channel, source pointer and native service frame. All 22 form,
intro, DAC-duration and role-waveform assertions pass; Ending remains 4406
frames and Credits remains 9527 frames, with every production track naturally
stopped at the end of each finite comparison. The absolute S2 ROM identity was
rechecked as 1,048,576 bytes, CRC32 `7B905383` and the SHA-1 above.
`git diff --check`, evidence fence/path checks and owning-label checks pass.

Unchanged model, transport, S1, cadence, snapshot, catalogue-registration and
short PCM-preparation checks are deliberately not repeated in this follow-up.
The change-based plan against `03e0c2ac04f561fc271b097434579e140d74cabb`
selects 3012 ordinary classes plus guards solely because the test oracle is
unclassified. This test-only reference repair has bounded S2 consumers; the
requested focused validation exercises those consumers and both instruction
semantics directly. No broad engine run is started or claimed. The short
one-off ROM-byte/tuple probe lives
under `target/s2-song-research`; its recurring control-flow logic remains the
committed S2 comparison helper. No ROM is copied or linked, and no waveform or
playlist rip supplies durations. Integration, push and tree cleanup remain with
the parent.
