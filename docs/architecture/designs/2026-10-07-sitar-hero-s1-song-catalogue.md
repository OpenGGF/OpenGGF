# Sitar Hero: complete Sonic 1 song catalogue (2026-10-07)

This work supplies `sitarhero.catalogue.Sonic1Catalogue.all()` and S1-specific
reference/production checks on `feature/ai-sitar-hero-full-s1`, based on shared
contract `cabd66f442af65e62711a52eaad7a1cbbbe4e4a3`. The later continuation on
`f71d88a3f` explicitly owns the generic PSG rest-sentinel repair in
`SmpsSequencer`, its narrow generated regressions and this evidence. The parent
owns combined book registration, chart curation, the long preparation host,
other shared repairs, release prose, integration and cleanup. Main `develop`
and its dirty disassemblies remain untouched by this worker.

## Inputs and authority

The input is the existing `${OPENGGF_ROM_ROOT}/s1.gen`, World REV01.
`OPENGGF_ROM_ROOT` denotes the absolute main-checkout ROM directory (machine-local
home paths are normalized here to meet repository resource policy):
524,288 bytes, CRC32 `AFE05EEE`, SHA-1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`. The reference submodule's recorded HEAD
is `f6ece657c1cf253404312137dfcb8ec15fa42318`; it is research only. No music, sample,
voice or picture bytes are embedded in the provider or exported as runtime assets.
Actual preparation uses the production ROM loader and synthesizer.

`MusicIndex` (`s1.sounddriver.asm:99`; REV01 table `$071A9C`) contains all 19 IDs
`$81..$93`. `tools/audio/s1_song_forms.py` reads that table and track pointers from
this ROM and independently executes duration/control-flow semantics. It emits
metadata, never ROM data. The maintained safety tests exercise scaled duration
reuse, explicit duration scaling, finite pattern repeats and incomplete prefixes.
The native owners are:

- `DACUpdateTrack` (`:277..319`), `FMDoNext` (`:367..394`),
  `SetDuration` (`:411..426`) and `FinishTrackUpdate` (`:436..450`): explicit raw
  duration is multiplied by the current divider in byte arithmetic; omitted
  durations reuse the **already scaled** `SavedDuration`.
- `TempoWait` (`:1549..1561`): one hold every header-tempo services, while the track
  walk continues. `cfSetTempo` (`:2256..2259`) resets the countdown when tempo changes.
- `cfSetTempoDividerAll` (`:2262..2274`), `cfJumpTo` (`:2587..2594`),
  `cfRepeatAtPos` (`:2596..2609`), `cfJumpToGosub` (`:2611..2619`), and `cfStopTrack`
  (`:2489`): global divider, signed relative branches, bounded inner repeats,
  subroutines and native termination.
- `sound/_smps2asm_inc.asm:484..500`: `smpsSetTempoMod` emits `$EA`; `smpsSetTempoDiv`
  emits `$EB`. Macro names alone are insufficient to distinguish those operations.

The independent interpreter omits synthesis/modulation/note-fill releases because
those do not set whole-form duration. It is a control-flow reference, not a hardware
PCM parity claim. Its attack coordinates are compared with **every** production
semantic attack for each selected song through its requested duration. Native note
IDs and source offsets are retained. No repeated waveform or audio rip determines
a loop, tempo or end.

## Complete bank inventory

All 13 substantive selections are included. A complete title or menu arrangement
is retained even when short: the selection policy is musical/function-based, not
membership in the former twelve-song draft or an arbitrary minimum length. The
complete composed cadences of Title and Continue are not artificially repeated.
Ending is included from the first service through its natural end despite being
shorter than the zone songs.

| ID | Native title | Selection ID / display label | Decision and rationale |
|---|---|---|---|
|81|Green Hill Zone|`green-hill` / Green Hill|Include: complete zone song.|
|82|Labyrinth Zone|`labyrinth` / Labyrinth|Include: complete zone song.|
|83|Marble Zone|`marble` / Marble|Include: complete zone song.|
|84|Star Light Zone|`star-light` / Star Light|Include: complete zone song.|
|85|Spring Yard Zone|`spring-yard` / Spring Yard|Include: complete zone song.|
|86|Scrap Brain Zone|`scrap-brain` / Scrap Brain|Include: complete zone song, including transition and reprise.|
|87|Invincibility|—|Exclude: temporary powerup loop.|
|88|Extra Life|—|Exclude: short reward/restoration jingle.|
|89|Special Stage|`s1-special-stage` / Sonic 1 Special Stage|Include: complete special-stage song; real pitched FM6, no DAC.|
|8A|Title Screen|`s1-title` / Sonic 1 Title|Include: complete title-theme arrangement with melody, accompaniment and composed cadence.|
|8B|Ending|`s1-ending` / Sonic 1 Ending|Include: complete ending arrangement, never looped.|
|8C|Boss|`s1-boss` / Sonic 1 Boss|Include: substantive boss song, not a result cue.|
|8D|Final Zone|`final-zone` / Final Zone|Include: distinct final-arena song, not merged with Boss.|
|8E|Sonic Got Through|—|Exclude: act-clear/result cue.|
|8F|Game Over|—|Exclude: failure/result cue, even though it quotes the title melody.|
|90|Continue Screen|`s1-continue` / Sonic 1 Continue|Include: complete menu arrangement, three transposing phrases and final cadence.|
|91|Credits|`s1-credits` / Sonic 1 Credits|Include: complete through-composed medley and shipped final-track tail, never looped.|
|92|Drowning|—|Exclude: temporary countdown/warning cue.|
|93|Get Emerald|—|Exclude: short reward/result jingle.|

No separate Super, level-select or additional title/menu song exists in this REV01
music table. Speed shoes change a song's tempo; they are not a second composition.
There is one shared song across the acts of each S1 zone.

## Forms and native service durations

Frame zero is the first sequencer service/prepared packet. A finite song whose last
track executes `cfStopTrack` at service N requests **N+1 packets**, including that
terminal service. Looping audio ends at the next form boundary; it does not include
the first attack of an unwanted additional form. Quantized loop periods alternate
where the integer tempo countdown requires it. The metadata uses their conservative
ceiling, without fitting timestamps.

Raw units below use the song's primary duration grouping, with header dividers
explicitly recorded. Source files are `sound/music/Mus<ID> - <native name>.asm`.
`loopBeats` is the complete form's quarter count; on finite selections it is the
complete score length (the existing record requires it to be positive).

| ID | Header divider / tempo | Intro + full-loop raw units | Intro + loop quarters | Intro frames / loop frames | Natural stop service | Requested frames / seconds |
|---|---|---|---|---|---|---|
|81|1 / 3|576 + 1536|36 + 96, quarter16|864 / 2304|—|7200 / 120|
|82|2 / 6|48 + 864|4 + 72, quarter12|115 / 2074 (2073 or2074)|—|7200 / 120|
|83|2 / 9|60 + 960|5 + 80, quarter12|135 / 2160|—|7200 / 120|
|84|2 / 6|48 + 1056|4 + 88, quarter12|115 / 2535 (2534 or2535)|—|7200 / 120|
|85|2 / 3|48 + 768|4 + 64, quarter12|144 / 2304|—|7200 / 120|
|86|2 / 5|0 + 1728|0 + 144, quarter12|0 / 4320|—|8640 / 144|
|89|2 / 8|0 + 864|0 + 36, quarter24|0 / 1975 (1974 or1975)|—|7200 / 120|
|8A|1 / 5|432 total|18, quarter24|finite|540|541 / 9.016667|
|8B|1 / 5|864 total|36, quarter24|finite|1080|1081 / 18.016667|
|8C|2 / 4|0 + 480|0 + 40, quarter12|0 / 1280|—|7200 / 120|
|8D|2 / 6|48 + 480|4 + 40, quarter12|115 / 1152|—|7200 / 120|
|90|1 / 7|480 total|20, quarter24|finite|560|561 / 9.35|
|91|variable, see below|6696 final scaled countdown units|275 quarters, including late native tail|finite|7600|7601 / 126.683333|

Whole-form boundaries are outer **melodic** returns, corroborated across the live
tracks. GHZ's PSG3 repeats after eight raw units, and its DAC repeats an inner
512-unit rhythmic pattern; neither is the 1536-unit melodic form. The outer DAC
control loop groups two copies, also not a new song boundary. MZ's lead enters its
loop at36 units, while the accompaniment enters at60; the common intro retains the
24-unit melodic pickup rather than treating an inner lead entry as the whole
arrangement restart. Delayed doubles remain delayed (GHZ FM4/5 one unit; MZ PSG2 two
units). SYZ's opening per-track divider adjustments are normalized against scaled
countdown units; the common intro is96 scaled units / four quarters.

The SBZ outer return includes opening descent, main melody, arpeggio transition and
reprise. Its complete4320-service form is72 seconds: two full forms require144
seconds, so it exceeds the120-second minimum. Every looping song requests
`max(7200, introFrames + 2*loopFrames)`. No selected natural form exceeds ten minutes.

### Credits tempo anchors and shipped bugs

`Sonic1Catalogue.tempoAnchors("s1-credits")` supplies the parent with a musical
clock independent of the average opening DAC period. Other IDs return an empty
list. `TempoAnchor(beat, serviceFrame, unitsPerBeat)` uses **raw** duration units per
quarter. Divider scaling is listed separately:

| Beat | Native service | Raw quarter units | Effective divider / scaled quarter | Native operation |
|---|---|---|---|---|
|0|0|24|1 / 24|Header tempo51.|
|84|2056|12|2 / 24|DAC `$EB02`; FM1 `$EA0F`.|
|172|4318|12|2 / 24|FM1 `$EA0A`.|
|204|5171|12|2 / 24|FM1 `$EA07`.|
|212|5394|16|2 / 32 for the GHZ form|FM1 `$EA03`; brief per-track divider changes below.|
|224|5969|24|1 / 24|DAC `$EB01`; FM1 `$EA04`, final title/cadence section.|
|275|7600|24|1 / 24|Final native owner PSG2 reaches `$F2`.|

The final score coordinate is exact:2016 scaled units at quarter24 (84 beats),
then3072 at quarter24 (128 beats),384 at quarter32 (12 beats), then1224 at quarter24
(51 beats), totaling275. FM1/2 end at6432 scaled units /264 quarters; the shipped
late PSG2 tail accounts for the remaining11 quarters. PSG1 briefly uses divider1
at5394 and returns to2 at5531; FM4/5 do so at5395 and5533. Their shorter local
stream grouping does not redefine the whole composition's quarter.

`Mus91 - Credits.asm:738..746` under `FixMusicAndSFXDataBugs=0` contains three
extra rests and an erroneous FM-only volume command which makes the subsequent
PSG2 notes inaudible. Those shipped quirks are preserved. The late track must still
reach its native stop; shortening to the FM/DAC stop silently fixes the ROM data.

An initial production observation of final stop7250 was **rejected**. The independent
ROM interpreter showed PSG2 stopping7600; production rescaled the prior raw duration
after `$EB01`, rather than reusing scaled `SavedDuration`. On contract commit
`cabd66f44`, the first divergent attack is PSG2 index258:

- Native service7520, song-relative offset2892 (`$B4C`), native note205 (`$CD`).
- Production service6752 at the same offset/note:768 services early.
- Production PSG2 stop6832 versus native7600; FM3/4 stop7250, explaining the incorrect
  earlier song-wide end. Other track attacks and stops agree.

The parent reproduced this with generated FM/PSG/DAC control programs and fixed
`reuseDuration` in `741fdb3c7`: copy the saved scaled duration directly. This S1
branch was then fast-forwarded to parent `f71d88a3f` (including catalogue commit
`e9decec628` and that repair) for the explicitly assigned sequencer continuation.

The next bounded divergence is independent of duration scaling. Against compiled
native-check tree `59c8be076`, native PSG3 (logical PSG/2, the noise part) attack359
is service5282, song-relative offset3067 (`$BFB`), note198 (`$C6`). Production
instead emits service4318, offset3040 (`$BE0`), note128 (`$80`, a rest). The preceding
rest starts service4241 at offset3037 (`$BDD`), with saved frequency `$FFFF`, divider2
and saved duration72. At4318, `Mus91_Credits_Loop1E`'s positive `$03` duration sets
saved duration6 and production clears the rest bit, then incorrectly emits an
attack while the saved frequency is still invalid. Its32 repetitions contain
three duration-only units each: exactly96 spurious attacks (599 versus native503).

Native `PSGDoNext` clears the rest bit at `s1.sounddriver.asm:1833`, and a positive
byte goes through `SetDuration`/`FinishTrackUpdate` without replacing the saved
frequency (:1845..1858). `PSGSetFreq.restpsg` stores `$FFFF` (:1874..1878).
`PSGDoNoteOn` then loads the saved word and executes `bmi.s PSGSetRest`
(:1883..1885); `PSGSetRest` sets the rest bit (:1918..1920). The generic repair
checks that signed saved word before a duration-only PSG attack under the existing
`DelayFreq.RESET` policy, also advancing the resting envelope where the owning
driver does. S3K's `DelayFreq.KEEP` preserves its earlier frequency and can
re-attack a positive duration after a rest, so that branch remains distinct.

After this repair every real attack tuple agrees for all13 included S1 songs,
including all503 PSG3 noise attacks. Every Credits stop matches the native owner:
DAC and PSG3 at6992; FM1/2 at7248; FM3/4 at7250; FM5 at6930; PSG1 at6968; final
PSG2 (logical PSG/1) at7600. The6992 noise stop is not the final PSG2 tail; there
is no608-service song-wide gap. The complete7601 serviced packets, native pitches,
form counts, stage bindings and tempo anchors are unchanged.

A cross-game check exposed a separate **S2 reference error**, retained for the
parent/S2 owner rather than hidden by a production carve-out. S2 ROM`$8F` (Oil
Ocean), logical PSG/0, has three oracle-only rest attacks at services613,3094,5575,
all song-relative offset779 (`$30B`), note128. At each, production has saved
frequency`$FFFF`, resting=true, saved duration192 and divider2. There are no
production-only tuples for this track. Native `zPSGDoNext` clears the rest bit and
parses positive durations (`s2.sounddriver.asm:1145..1169`), `.restpsg` writes both
frequency bytes`$FF` (:1190..1196), and `zPSGDoNoteOn` executes `bit 7,FreqHigh` /
`jr nz,zSetRest` (:1202..1204). Thus293 actual attacks are authentic; the former296
reference count includes three silent units. `SitarHeroS2NativeProgram:76` wrongly
admits duration-only rest continuations for every non-DAC track. Restrict that
exception to FM, or model the saved PSG frequency validity explicitly. Its
raw-duration rescaling at:74 also needs the native saved-scaled-byte semantics
when an omitted duration follows a divider change. S2-owned files are untouched;
the parent owns correction and combined verification.

## Authored parts and compatible stage pictures

All channel numbers in the provider are zero based; names below use native one-based
labels. Doubles are real selected voices, but only distinct simultaneous pitches
can become a chart chord. Delayed/detuned doubles are not fabricated harmony attacks.

| Song | Lead | Rhythm / harmony | Synth | Drums | Stage (logical zone / zero-based act) |
|---|---|---|---|---|---|
|Green Hill|FM1+3 arpeggio → FM4+5 intro melody → FM1|FM2 bass → arpeggio → FM4+5 harmonic responses|PSG2 intro → PSG1+2 main|DAC|GHZ Act1,0/0|
|Marble|FM1+3, detuned double|FM4+5 actual response|PSG1+2 delayed double|DAC|MZ Act1,1/0|
|Spring Yard|FM1|FM4+5 stabs|PSG1; PSG2 stopped|DAC|SYZ Act1,2/0|
|Labyrinth|FM1|FM5 stabs; FM3/4 sparse pads are not substituted as a dense part|PSG1+2|DAC|LZ Act1,3/0|
|Star Light|FM1+5 early double and later response|FM3+4 harmony|PSG1+2|DAC|SLZ Act1,4/0|
|Scrap Brain|FM1+3 melody / double|FM4+5|PSG2 primary and sparse PSG1 response|DAC|SBZ Act1,5/0|
|Special Stage|FM6, FM1 lower melodic double|FM3+4 chord responses|PSG1+2|Absent: DAC immediately stops|Explicit GHZ Act1 concert,0/0|
|Title|FM1+5|FM3+4|PSG3 **noise** hi-hat, no invented melody|DAC|Explicit GHZ Act1 concert,0/0|
|Ending|FM1+5 → FM4+5 at beat20|FM3|Real PSG1+2 melody enters after service600|DAC|Explicit GHZ Act1 meadow concert,0/0|
|Boss|FM5 driving melody|FM1+4 accompaniment doubles|PSG1+2|DAC|Actual GHZ Act3 boss stage,0/2|
|Final Zone|FM1+5 detuned double|FM3+4|Absent: every PSG immediately stops|DAC|Actual Final Zone,6/0|
|Continue|FM1 transposing melody|FM3+4|Absent: every PSG immediately stops|DAC|Explicit GHZ Act1 concert,0/0|
|Credits|FM4+5 opening → FM1, FM2 during the transition rest, final FM4 handoff|Native bass, chord and counterline handoffs|Real pitched PSG1, with early PSG2 double; late inaudible PSG2 is not charted as a melody|DAC|Explicit GHZ Act1 concert,0/0|

The stage indices are checked against the production `Sonic1ZoneRegistry`, including
its actual music IDs and `LevelData.S1_GREEN_HILL_1/3` bindings. S1 registry order is
GHZ,MZ,SYZ,LZ,SLZ,SBZ,FZ; it is not the music-table/native header order. Special Stage
uses an explicitly compatible concert backdrop because the ordinary zone-picture
service does not represent its rotating special-stage renderer. Title, Continue,
Ending and Credits similarly use the explicit GHZ concert, without implying that
these songs are ordinary GHZ act music. Parent integration owns rendered picture QA.

Native `DACUpdateTrack:300..319` and `DAC_sample_rate:337..343` establish sample
identities: `$81` kick, `$82` snare, `$83` timpani; `$88..$8B` select pitch/rate
variants of **that same timpani sample**. SBZ and Credits use timpani. No note rank
is interpreted as a different physical drum family. The shared parent's drum lane
mapping will include those identities. `firstDacUnits` is the first seven positive
inter-attack durations from each real stream; Special Stage's list is empty.

## Verification, rejected approaches and delivery state

Commands run in `.worktrees/ai-sitar-hero-full-s1`, Java21.0.12.1, against
`cabd66f44` plus the owned candidate files:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests compile test-compile -B
python3 -m unittest discover -s tools/audio/tests -p test_s1_song_forms.py
python3 tools/audio/s1_song_forms.py --rom ${OPENGGF_ROM_ROOT}/s1.gen
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSitarHeroS1SongCatalogue \
  -Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen test -B
python3 tools/testing/run_categories.py --base cabd66f44
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base cabd66f44 --preflight
```

- Queued compilation/test compilation passed (1m10s). Python reference safety:
  four tests passed, no skips.
- Initial focused Java run on `cabd66f44`:18 tests,17 passed, one inherited
  Credits SavedDuration failure, zero errors, zero skips. Every semantic attack through the requested
  duration agrees for the other12 songs; the longest two-loop SBZ run executes8640
  services. Native reference survey spans9001 services, and validates all19 bank IDs.
- Short actual production preparation verifies every declared source role on all13
  songs. It runs480 services each except the complete1081-service Ending, whose
  melodic PSG enters late. These checks pass, including native FM6 ownership,
  absent DAC/PSG parts, exact sample lengths, and bounded note tails. They are not
  a claim of full-length prepared PCM or shared-curator validation.
- The first short Ending probe was too short to reach its actual PSG entrance;
  increasing only that preparation to its natural end removed this test-coverage
  failure. The earlier strict requirement that a song execute the first service of
  its third loop was rejected: two complete loops end **at** that next-service
  boundary; the catalogue must not add the third-loop attack.
- The change-based plan selects3007 ordinary classes plus guards because the new
  provider/probe paths are unclassified. This additive, isolated metadata/probe
  branch changes no shared algorithm, API, build or selection policy. Focused
  whole-form reference/production and real preparation checks directly cover its
  behavior. The parent owns combined validation of the shared timing/curator/host
  changes; no local broad engine run is claimed.
- Default tool preflight initially rejected the installed default Lua version;
  setting `LUA_BIN=/usr/bin/lua5.4` passed Java/Lua/PowerShell prerequisites.
  Preflight executed no engine tests. No consumed category run diagnostics were
  produced. Temporary survey JSON, one-off Java probe and raw Maven logs are removed
  after their summaries are inspected; the reusable interpreter and evidence remain.

The initial four S1 profile choices from the interrupted
`.worktrees/ai-sitar-hero-catalogue` were read for channel/quarter evidence only and
left unchanged. The former excerpt-sized durations, first repeated drum/noise
patterns and production7250 Credits end were rejected as complete-song evidence.

Parent integration should add this one-line entry to the existing shared workflow
README Tools list (kept out of this branch's file ownership):

> `tools/audio/s1_song_forms.py`: survey all S1 REV01 ROM music forms, tempo/divider
> boundaries, native stops and attack coordinates; inputs are an existing absolute
> `--rom` path, optional `--id`, and bounded `--frames` (2026-10-07 Sitar Hero).

The owned branch is committed locally for the parent's merge. No task branch push,
main integration or worktree deletion is performed by this sub-agent.
The 2026-10-07 sequencer continuation used the following queued focused command
on `f71d88a3f` plus the owned repair (absolute main-ROM inputs; the shell variable
below denotes the existing main checkout):

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen \
  -Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen \
  -Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen \
  -Dtest=TestSmpsSavedDurationRest,TestSmpsSequencerCadence,TestSmpsSequencerSnapshot,TestSitarHeroS1SongCatalogue,TestSitarHeroS2SongCatalogue test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base f71d88a3f --preflight
python3 tools/testing/run_categories.py --base f71d88a3f
```

The completed Maven run reports45 tests:44 passed, one S2 oracle failure above,
zero errors, zero skips. Per-class XML: saved/rest regression12/12; cadence10/10;
snapshot2/2; S1 catalogue18/18; S2 catalogue2/3. The twelve new generated tests
exercise FM/PSG/DAC on all three actual driver configurations, explicit and
omitted durations around rests and divider changes, saved duration/frequency
snapshot replay, and both tone/noise PSG RESET versus KEEP semantics. S3K uses
its native`$FF04` divider command rather than S1/S2`$E5`. The four unchanged
Python reference safety tests also pass. The failed S2 count is not reported as
a full pass, and later S2 bank entries were not reached by that failed test.

A temporary direct Java21 probe against the existing compiled native-check tree
established the first post-741 divergence and all track stops before the repair.
A second direct Java21 probe against this candidate established the three S2
oracle-only tuples above. These are comparison-only observations, not a JUnit
suite claim; temporary sources/classes and raw logs are removed after inspection.
The improved S1 test compares tuple prefixes before counts, so a future count
failure also exposes the earliest differing service/offset/native pitch.

Tool preflight passed. The change-based plan selects1131 ordinary classes in
`audio,common,mods,rewind,tooling` plus structural guards out of3012 inventoried
classes before the evidence-only documentation update. This continuation changes
a shared sequencer branch and requires the parent's combined normal validation;
no local broad suite is claimed. Two owned queue requests were cancelled while
still waiting to prepare the bounded diagnosis and correct the generated S3K
opcode, without changing parent jobs or lock files. Candidate sources stayed
frozen during the completed actual Maven build. No already completed unchanged
engine checks were repeated. Parent integration, S2 oracle reconciliation, combined
verification, upstream push and accounted-tree cleanup remain parent-owned.
