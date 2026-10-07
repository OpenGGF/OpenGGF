# Sitar Hero arcade proof of concept

## Authorized outcome

Build a maintained example mod demonstrating a complete change of behaviour in
OpenGGF. Arcade selects a cosmetic performer, musical role and song, plays a
finite performance, then offers results and retry. The accepted library is Green
Hill (S1), Chemical Plant (S2), and Angel Island Act 1 (S3K), filtered by supplied
ROMs. All seven nonempty ROM combinations share one library and roster.

Sonic and Robotnik are available with any game; Tails with S2 or S3K; S2's
Silver/Mecha Sonic with S2; Knuckles, S3K Mecha Sonic and Sky Sanctuary Egg Robo
with S3K. Performers have identical rules and can cross game boundaries.

Sitar is FM lead, Harp FM accompaniment, Synth PSG, and Bongos DAC. Melodic
roles follow GH III's five frets and strum, chords, sustains, HOPO, whammy and
Star Power. Bongos deliberately uses four direct-hit pads and a kick pedal,
with no strum. One curated difficulty is delivered. Original voices remain;
instrument names and new pixel props do not replace the ROM music.

## Ownership and boundaries

The host supplies bounded ROM content access, physical input and an audible
sample transport. The example owns song/section role selection, chart curation,
rhythm judgment, scoring, settings and presentation. Runtime bytes come through
the ROM pipeline, never the disassembly or SMPS-rips directory. The
`smps-rips-master` archive found on the mounted NTFS research drive is research only.
Charts and new UI/props may ship; ROM audio and sprites may not.

Chart attacks must come from sequencer semantics, including same-pitch and
duration-only retriggers, while ties and rests remain distinct. Per-song role
metadata selects meaningful tracks/sections and reduces density deliberately.
DAC families use the owning game's actual sample table rather than generic
equate names. Unused bongo lanes in kick/snare-only songs are expected.

The delivered audio design uses bounded preparation from runtime ROM synthesis. Preparation is acceptable only with
measured full-song progression, part isolation/recombination and bounded
resources. The gameplay clock must track consumed audio, not generated samples,
render frames, trace rows or diagnostic observations. Timestamped input maps to
that coordinate with persisted calibration. Pause, retry and device underruns
must preserve its meaning; misses mute the selected part without stopping its
sequencer or backing. Hardware latency remains a separately measured boundary.

Existing startup scenes retain their legacy inputs/audio/art. New capabilities
are reusable candidate Mod API facades. Candidate version stays unpublished
0.7.0; reachable types, signature pin, SDK and handbook change together.

Career, local multiplayer and direct-connect multiplayer are future full-version
requirements. The PoC's chart/session and input records remain independent of
rendering and devices. It does not add networking or career infrastructure.

## Reference fidelity

The [original GH III manual](https://device.report/m/eb146945a54d423e76422a3560d9a3c44b96267e6de14e921ca9622c7f987dbb.pdf)
supports the core mechanics, multiplier, Star Power and calibration behaviour.
The [GH III tools author's research](https://github.com/ZedekThePlagueDoctor/Guitar-Hero-III-Tools)
documents configurable HOPO assignment. Neither establishes every engine judgment
constant. The implementation must distinguish implemented rules from unverified
numeric parity; the PoC is not certified as an exact executable GH III port.

## Delivery and work allocation

Pinned updated base: `2d91ef756e3289080bf463ad3e33773ebe6b7508` on develop.
Worktree: `.worktrees/ai-sitar-hero`, branch `feature/ai-sitar-hero`.

- Audio worker: production note extraction and music transport, its facade and tests.
- ROM worker: supplied-game catalog, cross-ROM art and manifest eligibility.
- Input worker: timestamped physical keyboard/gamepad seam and tests.
- Integrator: shared scene wiring, rhythm model, charts, arcade UI, docs and delivery.

Workers settle interfaces before consumers and own disjoint files. The integrator
queues builds centrally; outputs remain in this worktree's target directory.

Verification covers all ROM subsets/roster gates, finite chart timing and musical
roles for all three songs, repeated attacks/ties/rests, judgment boundaries,
strum/chords/HOPO, pad/kick edges, sustain release, phrase completion/Star Power,
pause/retry, saved remaps/calibration, full-capture AC PCM health and stage art.
Run the maintained sample through actual packaging/validation and capture its
selection, performance and results screens. Baseline, candidate and integrated
engine suites compare failures by identity; API/SDK/guard checks remain required.
Then merge and push develop, preserve unrelated main-tree changes, and remove
only the fully accounted task branch/worktree. Record results and rejected paths
in this artifact; raw logs are temporary.

## Verification record

### Landed architecture and arrangement choices

Preparation uses the production ROM SMPS driver/chip path, then the existing final
PCM speaker/capture/history pipeline. A separate OpenAL source was rejected: it
would bypass capture/history and make timing ownership ambiguous. Adding isolated
stems was rejected because DAC/FM6 and PSG tone/noise interact on the physical
chip. One full mix and one independently synthesized masked mix preserve those
interactions; the selected residual supplies the PoC whammy presentation. Only
one song and one selected arrangement are cached (about 26 MB for a 65-second
stereo 48 kHz song), with a 90-second preparation cap. Initial synthesis measured
several seconds per mix; identical retry reuses prepared buffers.

| Song | Driver frames | Samples at 48 kHz | Intro + first full cycle |
|---|---:|---:|---:|
| Green Hill | 3168 | 2,534,400 | 52.8 s / 132 quarter beats |
| Chemical Plant | 3717 | 2,973,600 | 61.95 s / 144 quarter beats |
| Angel Island 1 | 3932 | 3,145,600 | 65.5333 s / 144 quarter beats |

Green Hill's intro arpeggio (FM1+3) gives way to FM4+5 melody at beat 4 and
FM1 lead at beat 36; accompaniment moves through bass, arpeggios and FM4+5
rhythm. Authored boundaries snap to actual observed attacks. CPZ lead is FM1,
accompaniment FM4+5, and its only PSG part is PSG3 noise; inventing pitched PSG
notes was rejected because PSG1/2 stop. AIZ melody selects FM2+3, accompaniment
FM4+5 and PSG1+2. Charts use observed attacks and duration, authored local pitch
contours and density limits, real simultaneous chords, clipped tails, HOPO flags
and separate power phrases. A song's complete-cycle beat count avoids drift from
estimating tempo from only the first few quantized DAC intervals.

GHZ/CPZ DAC use kick/snare only. AIZ maps its actual kick/snare, tom families and
metal hits across four pads plus pedal. The named NTFS SMPS-rips research helped
identify families, but the owning ROM tables determine sample identity; generic
S2 equate aliases are insufficient evidence of physical sample identity.

Physical transition events preserve short taps and independent alternatives.
Raw A/B menu input is interpreted once, without its overlapping Genesis aliases.
The final song boundary retains a calibrated silent judgment tail so no final gem
is silently left unresolved. Manual pause freezes that tail too. GH III head,
tail, chain and power mechanics are implemented; ±100 ms windows, rock weights
and residual vibrato remain explicit unverified executable/DSP choices.

### Audio measurements

Whole-cycle final PCM was captured through production
`AudioManager.beginLiveCaptureAudio`, not a diagnostic observer. A bounded scratch
probe prepared each ROM/song with the frame counts above and presented 800 samples
per host frame; three WAVs remain outside the repo under
`~/scratch/captures/sitar-hero-2026-10-06/audio/`. Those captures reached
the exact sample EOF, emitted zero on an extra post-EOF frame, ended with zero
left/right samples after the 5 ms taper, and contained no clipped samples.

| Song | Attacks in first/middle/final thirds | Minimum 1-second full-mix AC variance |
|---|---|---:|
| GHZ | 472 / 462 / 419 | 1,034,723 |
| CPZ | 510 / 599 / 458 | 737,392 |
| AIZ1 | 1005 / 747 / 862 | 808,323 |

All twelve role-muted full cycles retained varying backing in every one-second
window (minimum AC variance 333,903); selected residual RMS ranged 231.7–817.9.
CPZ Synth's residual was 231.7, corroborating real noise selection. A failed
single-packet assertion was rejected with ROM evidence: CPZ's masked frames
58–63 contain an authentic constant/DC rest, then frame 64 resumes AC variance
4,694,199. The regression now observes continued backing across musical rests.

Actual OpenAL Soft 1.23.1 null-backend observations established a monotonic consumed
cursor, 200 ms frozen pause and exact 96,000-sample EOF for a two-second song, with
`AL_SOFT_source_latency` available. A deliberate 160 ms producer hitch exposed an
underrun detector that incorrectly compared consumed samples to produced samples
despite a partial software packet; the sink now reports the stopped-after-play
transition directly. Fresh-code null-backend verification passed: exactly one reported underrun after
the hitch, monotonic cursor, frozen 200 ms manual pause, exact EOF after 129
presented frames, and a fresh zero-cursor/zero-counter restart reaching EOF after
127 frames without underruns.
Null output tests queue/cursor behavior, not physical speaker arrival. AL 1.1
fallback and user calibration remain necessary, and queued miss/whammy feedback
can lag by roughly the 3×1024 packet queue (64 ms at 48 kHz) plus scheduling.

### Arcade, chart and presentation acceptance

In the commands below, `OPENGGF_ROM_ROOT` denotes the absolute main-checkout ROM
directory; the supplied S1/S2/S3K hashes match the repository's required revisions.
The focused ROM/transport/stage/arcade command
`python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestDetachedStockScenePictures,TestSceneMusicRom,TestOpenAlPcmSink,TestSitarHeroArcade'
-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/s1.gen
-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/s2.gen
-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/s3k.gen
test dependency:build-classpath -Dmdep.outputFile=target/sitar-classpath.txt`
completed with 28 tests, no failures, errors or skips. This followed a broader
179-test focused run that exposed two issues: CPZ's detached picture had not
primed its native animated art, and a backing-audio assertion confused a genuine
constant/DC rest with stopped sequencing. Detached CPZ now primes the shared ROM
AniPLC scripts without live DMA/GPU publication; an exact ROM-pixel regression
covers the missing tiles. The audio assertion observes AC progression through
musical rests rather than demanding every individual packet vary.

`SitarHeroCapture` ran in four separate JVMs with `--subset s1`, `s2`, `s3k` and
`all`, using the production packaged mod, unmodified root ROM paths and the
compiled test classpath. All four visits exited successfully: 53 screenshots at
400×224 establish all seven visible performers, the three ROM stage pictures,
four instrument props, cross-game play, pause/resume, finite results and retry.
A transparent S3K Tails standing-tail frame found during inspection was replaced
with its native standing-tail animation and recaptured. Tool autoplay completed
Green Hill at 105/105 chart hits with a 34,908 score; DEMO did not save a record.

An actual-ROM AIZ Bongos probe using production `SceneMusicFactory` and the
packaged `ChartCurator` found 375 chart notes. The four-pad/kick histogram is
`[31, 55, 61, 122, 106]`, lane union 31: every input has genuine DAC attacks.
Green Hill and Chemical Plant retain their smaller authentic kick/snare kits.

The durable visual captures and a 15-second 800×448/60 fps H.264/AAC demo remain
outside the repository under
`~/scratch/agent-tmp/codex/sitar-hero/`. The demo's production 48 kHz
stereo capture has the expected three-second lead-in silence; each checked
post-countdown one-second window has AC RMS 1,200–1,470, peak 7,468. Offline capture
proves artwork, scene progression and audio content, not physical speaker latency.
Owned generated build/save/probe files were removed; PNGs, MP4, WAV and the demo
configuration were retained. `python3 examples/sitar-hero/build.py --skip-engine`
also packaged the mod successfully; its 23-entry jar contains class files and
the manifest, with no ROM bytes.

### Focused review corrections

A bounded independent review identified two concrete host defects before the
candidate broad run. Historical timestamp mapping projected at nominal sample
rate despite a later observation proving the cursor had stalled; for example,
observations at 150/166 ms both at 2,400 samples must map a tap at 155 ms to 2,400,
not to a subsequently resumed cursor. The mapping now bounds projection by the
next observed cursor and clamps to finite EOF. This retains live sub-frame mapping
while respecting a recorded stall.

Opaque handles also retained obsolete full/masked PCM arrays across preparations
and arrangement retries. The explicit contract is one current preparation and
one masked arrangement per scene: a different preparation stops and retires the
old one; old note/length/sample-rate metadata remains readable, but stale start
requests fail. Stopped players release their arrangement reference and retirement
releases PCM and render-only source data. Host close retires the remaining cache.
The review found no further material fault-boundary or mixed-ROM ownership issue.

A root-side dependency check also found that placing physical input values in
`mods.scene` made the native input owner depend back on `mods`, contrary to the
documented `mods -> control` boundary. The values now live in canonical `control`
as `PhysicalInput`, `PhysicalInputEvent` and `PhysicalGamepad`. The scene host
consumes that same model; no duplicate input model, cycle exception or frozen
baseline expansion is needed.

A fresh architecture run then rejected seven concrete Sonic provider
constructions in the scene hosts. Module/audio selection now uses the existing
`BuiltInRomDetectors` composition root; private character/tail decoding and
palettes use `GameModule.getCrossGameDonorProvider()`. This preserves ROM-owned
decoding without calling the active module's PLC-resetting `createGame`.
`TestSceneMusicRom,TestSceneRomLibrary,TestSitarHeroArcade,TestSitarHeroCharts,
TestDetachedStockScenePictures` passed 25 tests, and a fresh `-Pguards
-Dtest=TestArchUnitRules` JVM passed all 29 architecture checks. Neither guard
exceptions nor frozen violation baselines were widened.
The all-ROM acceptance visit then passed again through the canonical capture
helper. Native palettes, Tails' separate tail, cross-game performances and
105/105 Green Hill results remained visible; its refreshed PNGs live under
`~/scratch/agent-tmp/codex/sitar-hero/provider-review/all/`.
Its owned build/save/empty-ROM directories were removed.

The model's Star Power check also established a rules defect: an extra strike
between successful phrase heads still granted the phrase bonus. Both melodic
strums and direct drum strikes now break an unfinished phrase. The standalone
Java 21 model checks reproduced the defect (`0 != 25` charge percentage), then
all ten checks passed after correction; normal phrase/whammy behavior remains
covered by the same run.

### UI reference refinement

The user requested cues from the existing mods and master title. The arcade UI
uses `MenuStyle`'s navy panels, low-contrast checkerboard, cyan focus frames and
gold header divider. Slay the Robotnik contributes a two-tone pixel wordmark and
ROM scenery behind a shaded menu column; Putt Putt Paradise contributes warm
gold accents and compact code-drawn text. A dedicated creator-side `SitarUi`
keeps these drawing primitives separate from the song/session rules. Primary
choices retain the host menu font; secondary labels use the example golf font's
5x7 glyph design, with no embedded ROM artwork. The three selection steps share
a left choice pane, a right ROM performer pane and a consistent controls footer.
Performance meters, score and performer cards share that hierarchy without
covering the note highway. Capture all choice screens, each musical role and
stage, pause/results, remapping and calibration before freezing the candidate.

The first refreshed capture pass completed four separate JVM visits (S1, S2,
S3K and all installed): 53 PNGs at 400×224. The all-ROM visit inspected every
performer, role and song menu, all three native stages, all four role props,
pause/resume and a finite 105/105 Green Hill demo result. Original PNGs and a
pixel-preserving HTML gallery were shared with the user outside the repository.
The recompiled helper added seven remapping/calibration captures, bringing the
shared gallery to 60 PNGs. Keyboard and gamepad bindings, capture/cancel, offset
selection, eight distinct real tap edges and saving/returning all passed. The
refreshed example also passed `TestSitarHeroArcade,TestSitarHeroControls,
TestSitarHeroModel`: 26 tests, no failures/errors/skips. One unsupported semicolon
in calibration prose was changed to a supported full stop and recaptured.
Reviewing the Harp capture found Mecha Sonic’s last label line outside its card;
compact wrapping now uses the same final-glyph width as text fitting, so the
65-pixel `MECHA SONIC` line fits on the first row and `(S3K)` on the second. This presentation correction
was rebuilt and recaptured before any candidate broad invocation started. The
compact font also includes the semicolon used by speaker-recovery notices;
glyph-table dimensions were checked and the code-only jar rebuilt. These are offline art/flow
checks, not physical speaker/controller timing measurements. The refreshed
15.0167-second demo is 800×448 at 60 fps (901 H.264 frames) with 48 kHz stereo
AAC. Its separate production WAV has varying AC audio in every one-second
window after the music begins (RMS 1,153–1,473.5 for seconds 7–14; peak 7,468).
The demo and gallery were shared directly with the user.

### Full-suite baseline

Origin advanced from the initial `2d91ef756` base to `a54dcf56f` while the
allocation fix was waiting for local memory capacity. Main stayed on `develop`
and fast-forwarded, preserving its three dirty disassemblies and unrelated
untracked files. The task tree then fast-forwarded and reapplied its owned
changes. All 97 task paths survived; 91 non-overlapping files retained identical
contents, and the six shared documentation/comment/signature files merged both
projects' additions. The combined pin contains 20,044 sorted unique entries,
including Survivors' six entries and Sitar Hero's 183. No product-source conflict
required a behavior choice. Final baseline, candidate and integration checks use
the actual updated destination `a54dcf56f`, rather than hiding committed changes
behind HEAD or treating the original baseline as current.

The first candidate all-category run was stopped early after a new
`TestAudioPresentationAllocationBudget#warmedForwardMixAllocatesNoPerFrameVoiceOrPacketArrays`
failure, rather than spending another long route sweep on a known regression.
No lane completed; that invocation is incomplete and its inspected diagnostics
were acknowledged and deleted. The old boxed FIFO-gap ledger allocated exactly
8,016 bytes: 167 measured overruns times two 24-byte boxed `Long` values.
The same method-only queued command reproduced 8,016 bytes on the candidate;
the identical command at unchanged main commit `2d91ef756` passed with zero
allocated bytes (one test, no skips in each invocation).
This is a production sink allocation defect, not JIT warm-up noise or an unused
scene-PCM branch. The replacement ledger has 64 preallocated primitive boundaries,
coalesces drops at the same boundary, and retires consumed entries during device
updates even when no scene reads the cursor. The 65th distinct unconsumed boundary
reports a speaker failure instead of growing storage or inventing audible time.
The native OpenAL Soft query also uses stack addresses without allocating a
`LongBuffer` view.

Reviewing that failure path exposed another defect: replacement requested inside
`accept()` violated the producer's active-frame boundary, and a scene could then
advance on a silent fallback's generated cursor. Replacement now waits until the
frame's capture/history publication finishes. A scoped player receives a speaker
failure callback, freezes its last observed audible coordinate, and exposes an
interruption; that attempt cannot resume. New attempts remain paused while output
is unavailable, while deliberately device-free captures retain their rendered
PCM clock. Sitar Hero keeps its pause menu visible until a fresh playable attempt.

The updated-base focused command (`OPENGGF_MAVEN_QUEUE=serial python3
tools/testing/maven_queue.py -Dmse=off
-Dtest=TestAudioPresentationAllocationBudget,TestOpenAlPcmSink,TestSceneMusicRom,TestAudioPresentationProducer,TestSitarHeroArcade
test`, with all three absolute ROM properties) passed 67 tests with no failures,
errors or skips. The existing warmed-forward budget and the new warmed-cursor
budget both require exactly zero allocated bytes. Further checks cover 256
consumed gaps without scene readers, coalescing at capacity, explicit fail-once
overflow, unsupported cursors, deferred replacement during frame publication,
and frozen player/visible pause behavior after output failure.
The normal resource-aware queue could not admit either invocation while available
RAM was below its 9 GiB threshold. This bounded focused check used the documented
per-invocation exclusive queue mode after comparing prior task focused peaks
(maximum 3.634 GiB) with available RAM; no shared policy or lock was changed.
The completed run's sampled peak was 2.816 GiB. An owned two-second memory watcher
would stop only that job below 2 GiB available; no cancellation was necessary.
Broad baseline/candidate/integration checks retain normal admission.

A bounded actual OpenAL Soft/null probe then passed at 48 kHz with the source
latency extension enabled: the paused cursor stayed 0 across 100 ms; a 48,000-frame
drop behind 3,072 queued frames did not jump the early audible coordinate; after
refill and consumption the raw coordinate 4,224 mapped to 52,224; flush reset it
to zero. Two deliberately starved intervals reported two underruns. The first
temporary protocol incorrectly assumed that wall-clock sleep would advance a
stopped latency-subtracted cursor: it remained 1,152 (3,072 minus 1,920 latency)
until refill. The corrected probe waits for actual consumed PCM, without a
production change. Its temporary source, native extraction and watcher were
removed; durable whole-song captures were preserved.

The final clean focused command selected `TestSceneMusicRom`,
`TestScenePhysicalInput`, `TestSitarHeroModel`, `TestSitarHeroArcade`,
`TestSitarHeroControls`, `TestSitarHeroCharts`, `TestModSceneHost` and
`TestSonic2PatternAnimatorGraphAdapter`, with the three absolute ROM properties,
`clean test dependency:build-classpath` through `maven_queue.py`. All 66 tests
passed with no skips, including the new clock/resource/phrase regressions.
The clean compilation also verified the capture helper's canonical
`com.openggf.tools` package and removed obsolete input DTO class files.

The initial API snapshot has 20,038 sorted unique lines: 183 additions and no
removals relative to the pinned 0.7 candidate base. Its physical input signatures
use canonical `com.openggf.control` types. The queued focused API/Javadoc/SDK,
eight maintained gallery samples, Hello Scene and Slay the Robotnik checks
(`TestModApiSignatureSurface,TestModApiJavadocTool,TestModApiSdkPackager,
TestSampleModsPackage,TestHelloSceneExample,TestSlayTheRobotnikExample`) passed
152 tests with no skips. The freshly compiled example again packaged through
the production validator; jar entries contain classes and its manifest only.
After upstream reconciliation and fresh compilation, the exact snapshot matches
the combined 20,044-entry pin, still 183 additions and no removals relative to
`a54dcf56f`. The rebuilt example is 53,025 bytes with 23 files, all classes or
the manifest; runtime ROM data is absent.
The same six-class API/Javadoc/SDK/gallery/Hello/Slay selection then passed again
on the combined updated-base tree: 152 tests, no failures/errors/skips, through
the documented per-invocation exclusive queue mode. Main's full baseline stays
on normal admission. These focused passes do not establish a full-suite pass.

The first unchanged-base all-category run (`LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/run_categories.py --base 2d91ef756 --category all --run`, main
workspace at the pinned base) reached the 40-minute invocation timeout. It is
incomplete: 2,788 reports, 24,457 tests, three failures, no errors and 58 skips;
guards did not start. Skips include explicitly opt-in diagnostic/soak/capture
tests, unavailable OpenGL/EGL checks and route prerequisites. None is a missing
S1/S2/S3K ROM assumption. The retained tail was progressing through long route
captures, so the necessary full baseline is retried with a 90-minute invocation
limit rather than treating a timed-out suite as passing.

The retry, with `--max-minutes 90`, completed at the same pinned commit:
25,998 ordinary tests, 28 assertion failures, no errors and 61 skips (75.76
minutes), followed by 672 guard tests with no failures, errors or skips (3.65
minutes). The 61 skips cover opt-in measurements/captures, unavailable native
graphics, absent platform/flyer route prerequisites, one uncaptured spin tube,
and optional local BizHawk evidence; none indicates a missing installed ROM.
Both consumed diagnostic directories were acknowledged and deleted. This is a
completed failing baseline, not a green suite.

The updated-base all-category run at `a54dcf56f` (`--category all
--max-minutes 120`, run `20261006T224220Z-67aa5796`) exited through the runner's
interruption handler after about 68 minutes, while the Sandopolis route output
was fresh. It did not reach either configured timeout. The sender is unknown;
this task did not request its cancellation. Its last observed completed-report
poll contained 2,821 reports / 24,672 tests, 20 assertion failures, no errors and
59 skips, with those failures matching the earlier baseline by test identity.
The runner removed temporary XML on interruption and marked the run incomplete;
no final ordinary summary or guard result exists. It cannot replace the required
updated-base baseline, which is restarted without narrowing its selection in
an immutable detached checkout at `a54dcf56f`, isolated from concurrent develop
work. Its hook-created resource links resolve to the same three canonical main
ROM paths; no ROM copies or manual links were created. The interrupted run was
acknowledged and its diagnostics deleted.

Observed pre-existing failures (all `AssertionFailedError`; the long SSZ object
snapshot is summarized by its owning fields rather than ephemeral blob hashes):

- `com.openggf.tests.TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice`: SOZ restore cycle 0 sprites: [sprites.sprites[0].state.playerExtra.instaShieldRegistered: A=false B=true] ==> expected: <true> but was: <false>
- `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]`: late pulley owns Tails ==> expected: <true> but was: <false>
- `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]`: late pulley owns Sonic ==> expected: <true> but was: <false>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding`: expected: <96> but was: <0>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`: expected: <96> but was: <0>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]`: death at 26706 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]`: death at 26750 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding`: expected: <96> but was: <0>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday`: death at 53897 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding`: expected: <96> but was: <0>
- `com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState`: death at input 36526 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState`: death at input 36526 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace`: expected: <1069> but was: <899>
- `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff`: death at input 19460 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor`: death at input 19460 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace`: death at input 19460 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff`: death at input 19460 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld`: expected: <2796> but was: <524>
- `com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad`: the route must observe the actual history-reset boundary ==> expected: <true> but was: <false>
- `com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay`: [wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false>
- `com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents`: death at input 7311 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport`: death at input 7311 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches`: death at 7671 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory`: death at 7671 ==> expected: <false> but was: <true>
- `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]`: expected: <48> but was: <0>
- `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]`: expected: <48> but was: <0>
- `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]`: replay at 4018: object-manager used slots differ; EggRobo flame/arm children are missing after replay and later dynamic slots differ.
- `com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineCli#shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup`: expected: <0> but was: <4>

### Completed updated-base and candidate validation (2026-10-07)

The immutable `a54dcf56f` checkout completed the required baseline with
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
a54dcf56f --category all --max-minutes 120 --run`. Its full selection contained
2,993 ordinary candidate classes and fresh-JVM guards. The frozen Sitar Hero
tree (`364ddf49e3dad1253cdd928c96f948c4cd8b4e62`) then completed
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
a54dcf56f --max-minutes 120 --run`, selecting all 3,003 ordinary classes and
the same guards. Both commands supplied the three canonical ROM paths through
the runner's normal discovery.

| Tree / lane | Reports | Tests | Failures | Errors | Skips | Minutes |
|---|---:|---:|---:|---:|---:|---:|
| Updated base / ordinary | 2,991 | 26,054 | 28 | 0 | 61 | 75.91 |
| Updated base / guards | 86 | 672 | 0 | 0 | 0 | 3.51 |
| Candidate / ordinary | 3,001 | 26,126 | 28 | 0 | 61 | 75.16 |
| Candidate / guards | 86 | 672 | 0 | 0 | 0 | 3.45 |

All 28 candidate failures match the updated baseline by class, test, failure
kind/type and the complete XML assertion message. Comparison normalizes only
Java identity suffixes of `RewindObjectStateBlob@...`; the normalized complete
messages have equal SHA-256 values, so the runner's displayed-message length
limit does not hide later differences. No gameplay fields, frame indices or
state hashes are suppressed. The updated base also matches the earlier
`2d91ef756` failure list above after that same identity normalization.
All 61 skipped test identities match, with no missing-ROM skips and no omitted
failure/skip entries. All 58 tests in the ten newly added test classes pass
without skips. These are completed failing full ordinary suites with clean
guards, not green-suite claims. Both consumed diagnostic runs were acknowledged
and deleted.

While validation was frozen, develop gained Robotnik Tower Defence
(`fe90b53da`, merge `aaf088e48`) and its documentation-only delivery record
(`79eb0d91a`). These changes add an example, two test classes and documentation;
they do not alter the shared engine or API pin. Before integration, its two
classes passed 23 tests with no failures, errors or skips through
`maven_queue.py -Dmse=off -Dtest=TestRobotnikTowerDefenseExample,TestRobotnikTowerDefenseScene`
with the three absolute ROM properties. The combined post-integration full run
will include that upstream example as well as Sitar Hero.

The retained image index includes all 143 task PNGs, including earlier art/UI
iterations; the final UI gallery contains 60 screens. An initial relative-link
gallery failed in the application's HTML viewer. Both galleries now embed their
PNG data and download links; browser decoding verified all 143 and all 60 images
with zero broken images. The final 15-second MP4 and original PNGs remain outside
the repository alongside the self-contained galleries.
