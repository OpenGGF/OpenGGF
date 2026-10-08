# Sitar Hero fumble feedback implementation plan

Goal: audible, instrument-specific mistakes with reliable solo, local and direct-connect behavior.

Base: `c039c009131be4548c3ab40eb809a0f85c2808f1`, fetched/fast-forward checked on develop. User authorized planning and execution plus a bounded Opus ideas consultation. Parent owns all implementation and delivery.

Current checkpoint: implementation is integrated into develop. The completed
actual-main ordinary lane has unchanged baseline failures and skips, and a separate
fresh actual-main invocation passes all 674 guards. Final evidence publication and
owned worktree/branch cleanup remain pending.

## Design

Keep judgment and scoring inside the mod. A resolved miss or wrong strike mutes its part until a successful hit. Dropping a sustain is quiet. Each mistake emits one short cue, with rate limiting to avoid chords or dense misses becoming a wall of noise. The cue is a pitched, enveloped fragment of the selected ROM part, retaining the current song timbre. Existing roles are Sitar, Harp, Synth and Bongos.

Add a generic, bounded `SceneMusicPlayer.cuePart(long sourceSample, int durationSamples, double startRate, double endRate, double gain, double pan)` capability, not an engine concept of a rhythm-game mistake. It reads the already prepared full-minus-masked residual without exposing PCM or synthesizing replacement assets. Duration at most 250 ms, rates 0.5..2, gain 0..1, pan -1..1; finite arguments and song bounds validated. Six bounded cue voices; allocation-free mixing during render, short attack/release envelope, stereo headroom. Pause freezes voices; stop and retired handles release them. Source exhaustion is silent, not wrapped. Existing API defaults remain compatible, and the mutable unpublished 0.7 pin/descriptor documentation are updated together.

Mod feedback events identify success, miss, wrong strike and quiet tail release independently of score. Mod `PerformanceAudio` owns ROM-attack selection, per-role cue presets, cooldowns, pan and audibility. Solo and online duel use local muting. Local modes retain one shared instrumental performance: either successful player keeps the part sounding. Online co-op applies the same OR rule across peers; remote audibility is presentation data only. Peer cues are quieter in duels, with centered local cues and right-balanced peer cues. This change does not add independent instrument selection.

Peer presentation packets carry round, independent sequence, song sample position, audibility, mistake kind and bounded chart-note index. Validate all fields before accepting; old rounds/duplicates cannot replay, rematches clear state/queues, and pause drops queued remote transients. Older than 250 ms cues are discarded while the latest audibility state still applies; implausibly future cues are discarded. Scores and local judgments are never derived from these packets. Periodic cue-free snapshots recover current audibility. Protocol is versioned to reject mixed mod versions clearly.

## Implementation and checks

- [x] Consult Opus on cue feel, multiplayer semantics, API edge cases; record adopted/rejected ideas.
- [x] Add regression checks for model event identities, grouped strikes and quiet sustain release; observe missing behavior, then implement.
- [x] Add generic host cue mixer with PCM tests for envelope/pitch/pan, clipping, bounds, polyphony, pause/stop and actual ROM residual timbres; verify the API pin and packaged consumers.
- [x] Add sequenced peer presentation messages and tests for duplicates, malformed fields, stale/rematch/pause, paired co-op, independent scoring and production loopback.
- [x] Wire the packaged scene through mod-owned `PerformanceAudio`; cover solo, local players, online co-op/duel, quiet tail, retry and pause.
- [x] Record README/creator recipes, candidate API rationale, changelog, audio evidence and validation limitations.
- [x] Run focused affected/API tests, inspect selection and preflight; freeze source then normal combined candidate validation and fresh guards.
- [ ] Merge into current develop without switching it, run mandatory integrated selection, compare concrete failures/skips, push only develop and clean owned worktree/branch/Opus resources.

## Verification baseline and review focus

The complete actual-main baseline is run `20261008T053400Z-a3c0531f` at `5d1ff9b8206594ee1c979ae5174328dd9134ffd4`: 3027 selected / 3025 reports / 26422 cases / 27 inherited failures / 0 errors / 62 inherited skips, fresh guards 86 reports / 672 cases / zero failures/errors/skips. Its exact command and full comparison are in the existing stock-parity audit. Independently checked `5d1ff9b..c039c0091` has empty source/test/resources/examples/POM/hooks/testing-tool diff: only qualified evidence/status prose changed. Reuse is attributed to that actual run, not a new c039 run. Candidate and integrated normal executions remain required because this changes a public audio contract.

Review focus: one cue per mistake despite batched input; no cue on harmless held-tail release; selected source must correspond to the current instrument/section; remote data must never award score or restore a stale round; pause/stop/EOF must not release old voices into a retry. Baseline failures compared by complete identity and concrete assertion; SSZ permits only its already verified named blob-hash normalization. Every ROM skip and omission inspected.

## Opus consultation (Claude Opus 5.5, xhigh)

Read-only task `sitar-fumble-opus-ideas-20261008-v1` completed. Adopt: downward rate glides; short role-specific durations and restrained gains; balance pan preserves original ROM stereo; cue only the first miss of a run, every cooldown-qualified wrong strike, never harmless tail release; 4 ms part-audibility smoothing; 50 ms batched peer presentation and periodic audibility refresh; SH2 version. Adopt a generic PLAYHEAD source sentinel for a choke at the render cursor, with the normal short attack to complement the mute ramp. Six cue voices reject excess instead of voice stealing; this avoids another fading-voice lifetime and cooldowns keep normal use below capacity. Keep audibility across pause/resume rather than resetting it to true: a missed part must remain muted until a hit. Keep peer feedback distinct from display STATE so existing score telemetry remains unchanged; the additional stream is coalesced and bounded at 20 packets/second. Reject treating subtraction as an exact independently emulated stem: separate chip mixes and output clipping mean this is presentation residual processing, as with existing whammy. No synthetic buzz/scrape asset is fabricated. Slow-attack sources need ROM audio evidence before delivery.

## Focused development evidence

Meaningful RED/GREEN checks exercised missing model feedback and host cue mixing,
then the packaged scene and peer presentation stream. The first combined consumer
run passed 82 cases with no errors or skips. Later edge checks reproduced stale
round chart-bound validation, wrong strikes after every gem was missed, and
calibrated live cues incorrectly classified against the raw song clock. Repairs
ignore old-round metadata before current-chart validation, use the final chart
gem as an outro source, index native attacks once per section, compare local
freshness in each session's judgment coordinates, and translate peer timestamps
onto the audible song clock. Five actual mod/paired audio checks pass through a
fresh Java 21 compile; these direct checks are not a Maven result.

The 103-case intermediate Maven invocation passed 102 cases and errored in a
new Sonic 2 cue fixture that assumed PSG channel zero was sounding. Selecting the
first real PSG attack fixed that fixture; its one-case queued rerun passed without
skips. No engine behavior changed for the fixture. The next unstarted focused
request was cancelled by verified owning PID/cwd/argv, exit 130 without Maven
execution, to repair the newly reproduced calibration issue. The replacement
focused invocation includes all Sitar classes, actual ROM cue paths, SDK/Javadoc,
API pin and documentation links. Its terminal result and the corrected fixture
qualification are recorded below.

All twelve measured ROM/family cue differences have AC RMS 43–292 sample units,
with no constant-DC-only evidence: S1 96.81/108.54/94.21/291.90, S2
85.98/53.29/43.05/ 276.88, S3K 76.45/52.06/47.24/245.44. Captures compare muted
backing followed by the same backing plus a short cue at 8 kHz. This proves
content and envelope behavior, not subjective listening quality or physical
speaker latency. Regenerable WAV evidence is outside the repository at
`${CAPTURE_ROOT}/sitar-fumble/audio-v1/` (a task directory outside the repository).

### Updated destination qualification

Published develop advanced to `098053c4a01c2af283ca6797463bb5051442ef0b`.
Independent comparison from c039 shows no engine runtime, examples, API, POM,
hook or category-selection changes. The only ordinary Java changes are two SOZ
capture fixtures, separately qualified by their owner: matched 32 cases pass,
final integrated 23 changed/control cases pass without skips, and the unchanged
nine full routes passed both arms. The standalone JFR summarizer was exercised
on real recordings and invalid arguments. Evidence is in
[ordinary-suite memory research](../research/2026-10-07-ordinary-suite-memory-cause.md).
Reuse of the completed 5d baseline is therefore attributed to unchanged runtime
plus the bounded changed-fixture qualification; it is not a full 098 base pass.
The task's actual composed candidate and post-integration main still require
normal full ordinary and fresh guards. Preliminary plan selects 3029 classes;
final composed plan will be inspected before submission.

## Final Opus review and rulings

The bounded read-only Opus 5.5 xhigh review completed without reported blockers.
Its choking-strike observation reproduced a failed queued regression; the
initial pause RED used the wrong input binding and is rejected below. The
corrected pause control independently reproduced the production defect. Apply
already judged feedback before pausing, and give Sitar/Harp chokes a downward
glide even when triggered by a wrong strike. Supporting hosts validate cue arguments; unsupported
legacy defaults simply return false. The API/guide now explicitly include
PLAYHEAD before song start among declined states. Online placement wording is
centered local/right-balanced peer, preserving native stereo rather than making
an unimplemented left/right promise.

The review overlapped the calibration edit only after the owning *waiting*
request had been verified and cancelled without execution; its replacement was
cancelled before admission to add the reviewed regressions. No result from
before that edit is claimed to cover calibration. The final reviewed focused
request freezes all source/test inputs through terminal.

Rejected concurrency repair: the proposed PLAYHEAD race assumed rendering on an
independent audio thread. The real `AudioPresentationProducer.present` calls
`assertOwnerThread`, invokes the scene source synchronously, and rejects reentry;
scene callbacks use that same engine owner. Concurrent scene-player calls are
not a supported contract. No buffer-lock/cursor protocol is added for an
unreachable production interleaving. Native checks instead compare PLAYHEAD
against an explicit next-buffer source after a lead-in, pause in the middle of
accepted voices, and verify no stopped voice reaches a replacement player.

The transport's existing 256 pending-message budget intentionally disconnects
an unresponsive consumer rather than growing memory. The extra feedback stream
is coalesced at 20 Hz maximum, with cue-free idle refresh at 4 Hz; a prolonged
active receiver stall can fill the budget in roughly ten seconds. Existing
transport-capacity checks and new protocol burst checks cover the bounded owners.
Disconnect/error is handled before performance by `SitarScene.step`: it stops
music and enters the error screen, so retained peer audibility cannot keep a
failed connection playing. A peer stall before transport failure retains the
last display state, as existing score telemetry does; no remote judgment or
score is inferred from silence.

The reviewed focused invocation terminated with **195 cases / 194 passes /
1 failure / 0 errors/skips**, 11:43 Maven execution. The sole failure was the
pause fixture: its SPACE binding is whammy for melodic roles, not strum, so it
had not exercised the intended wrong strike. This earlier RED is discarded as
pause evidence. Corrected UP-strum controls were freshly compiled and packaged
against two owned temporary source copies: removing the pause feedback flush
reproduced the exact audibility assertion, retaining it passed. Both source
copies/probes are temporary under the task target and are removed after use.
The bridge now uses the actual mapped strum, covers immediate and deferred peer
pause paths, and calibration fixtures exercise the supported ±250 ms settings.
The narrowed correction selects Arcade/Feedback/OnlineMatch only; unchanged
catalogue/native/API/SDK/Javadoc checks are not repeated before the broad run.

Fresh native cue checks passed all three ROMs and all four selected families,
including mid-cue pause/resume sample equality, lead-in PLAYHEAD rejection,
next-buffer PLAYHEAD equality and stop/replacement isolation. Fresh API pin9,
SDK10, Javadoc7 and handbook links1 pass with zero skips. The exact inspected
class counts will accompany the final source checkpoint.

Corrective queued Maven at the final mod-side source passed **67 cases, zero
failures/errors/skips**: Arcade38, Feedback4, OnlineMatch25. No source/test edits
occurred between admission and terminal. The public pin has exactly two additions
and zero removals. Mod manifest version0.3.0 records the incompatible SH2 wire;
the unpublished engine API remains0.7.0.

The final EOF check exercises cues already accepted before the song ends and
fragments that exhaust their source window. Its outcome is pending; any repair
will be verified through the changed host tests before the combined source
checkpoint. This is separate from the already passed stop/retry isolation.

The two-case EOF RED request was still unadmitted after more than twelve minutes.
Its exact owning PID/cwd/argv and absence of a Maven child were verified, then
only that waiting wrapper was interrupted (exit 130, no Maven result). The
identical selectors/ROM input were resubmitted through the supported `--lean`
focused lane (1 GiB JVM heaps). Candidate and integrated broad runs retain normal
execution; no queue policy, foreign request or lock file was changed.

A fresh 256 MiB direct Java21 host harness reproduced both EOF defects against
the actual compiled classes: last exhausted-source sample2481 and439 nonzero
post-taper frames using the real S1 ROM. Fresh compilation of the two repaired
host classes yields0 and0. The source now fades exhausted fragments, applies the
finite5ms tail to combined PCM, and retires voices at EOF. The still-unstarted
lean RED wrapper was verified/cancelled before editing (exit 130, no Maven result);
the direct execution supplies the RED/GREEN evidence. The coherent final focused
request selects SceneMusicRom13, PartCues5, Arcade38 and Feedback4 (60 expected),
through the supported lean lane with all three verified original ROM paths.
It also packages the now-explicit mod-only cue preset record; parameters are
unchanged, with no added public API or static preset objects. The terminal result is recorded below; normal candidate/main qualification
remains mandatory.

### Final focused source checkpoint

At unchanged implementation/test inputs, the supported lean focused invocation
completed at 2026-10-08T09:12:52Z, exit 0 / BUILD SUCCESS, Maven execution 1:31.
Fresh XML independently inspected: SceneMusicRom13, PartCues5, Arcade38,
Feedback4; total 60 cases / zero failures, errors or skips. All three ROM
properties name the verified original main files. The real native checks prove
accepted cues end at finite EOF, in addition to lead-in, pause/resume and
stop/replacement behavior. The consumed one-off EOF harness and temporary logs
are removed; the meaningful assertions remain in the host JUnit tests.

The preceding reviewed invocation covers unchanged catalogue, model, story,
career, multiplayer, creator SDK, API pins, Javadoc and links, with its one bad
pause fixture resolved by the 67-case correction above. These are focused results,
not a broad-suite pass. Combined candidate and mandatory integrated execution
remain pending.

## Complete first candidate qualification

Source checkpoint 72f1929ca was composed with published develop 098053c4a as
`3afd818796af04333c37f1467830542b19c5f92f`. Its tracked source stayed clean
through the terminal run `20261008T091539Z-0dbb4a60`, completed at
2026-10-08T10:38:49Z. Actual normal command in the task worktree:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 098053c4a01c2af283ca6797463bb5051442ef0b --max-minutes 150 --run
```

The actual plan selected 3029/3029 ordinary classes, one worker and fresh guards.
Ordinary: 3027 reports / 26443 cases / 27 failures / 0 errors / 62 skips, exit 1,
4774.65 seconds. Fresh guards: 86 reports / 672 cases / zero failures, errors or
skips, exit 0, 215.70 seconds. Both lanes completed without timeout; failed-case
and skipped-case omission counts are zero. The runner exits1 for the inherited
failures; this is qualified baseline parity, not a green whole-suite claim.

The complete identity/assertion comparison against the qualified actual 5d
baseline has no new, resolved or changed failures: 26 full first lines literally
match; the complete 2952-character SSZ line matches after ONLY exception-prefix
removal and the independently verified `RewindObjectStateBlob@hex` normalization
(2907 characters). All 62 skip identities and first causal reasons match literally;
there are no missing-ROM or RequiresRom skips. All 13 Sitar XML reports were
independently read before automatic raw-report cleanup: 151 cases passed without
skips. The launch auto-discovered the three verified original main ROM filenames;
SHA1/CRC identities match the documented REV01/locked-on inputs.

Plan fingerprint:
`c2216285aed4caa797ad6fde491e296ada2e7f89fcd97c8b933666a0e513afbd`.
After complete consumption, the exact run was acknowledged with exit 0 and its
diagnostics deleted. Temporary wrapper output is consumed rather than archived.

### Framework change at the first checkpoint

During the first candidate run, the framework owner integrated a new actual-main
source at `bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a`. Its main validation freeze
is preserved. This is a substantial framework/creator-helper change, including
Sitar input/UI helper migrations and a narrower public API closure; it is not
source-equivalent to 098. The scene-music player/mixer source files are unchanged.
The completed first candidate result qualifies the original feature source and
base, not an unexecuted composition or a completed bf7 baseline.

Next: compose privately, preserve both sets of Sitar changes, regenerate the
combined candidate signature pin from fresh compiled classes, and exercise real
packaging/API/input/audio consumers. Use the framework owner's terminal base
qualification when available. The final actual destination still needs its
mandatory normal combined ordinary and fresh-guard execution before push; no
main integration, delivery or cleanup completion is claimed here.


## Private framework composition checkpoint

The private merge with frozen framework source
`bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a` preserves its shared action-binding,
physical binding-capture and UI helper migrations alongside the fumble rules.
Only `implementation-pitfalls.md` conflicted: the complete upstream document was
retained and the independently authored finite-audio-tail lesson appended. There
are no source conflicts or removed upstream helper changes. The scene-music and
network implementation inputs remain those of the completed first candidate.

The original compile-only queue request was verified unadmitted with no child
process, then cancelled with exit 130 after 1,023 seconds; no Maven result is claimed.
It was replaced once by the supported focused test lane, which both compiles the
composition and checks its public contract. Actual command below is normalized
only for the three machine-local ROM paths:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean \
  -Dmse=off \
  -Dtest=TestSitarHeroArcade,TestSitarHeroCareer,TestSitarHeroCharts,TestSitarHeroControls,TestSitarHeroFeedback,TestSitarHeroModel,TestSitarHeroOnlineMatch,TestSitarHeroPerformers,TestSitarHeroS1SongCatalogue,TestSitarHeroS2SongCatalogue,TestSitarHeroS3kSongCatalogue,TestSitarHeroStory,TestSitarHeroWorldTour,TestSceneMusicRom,TestScenePartCues,TestModApiSignatureSurface,TestModApiSdkPackager,TestModApiJavadocTool,TestModdingDocumentationLinks,TestModApiReleasePolicy \
  "-Dsonic1.rom.path=${S1_ROM}" "-Dsonic2.rom.path=${S2_ROM}" \
  "-Ds3k.rom.path=${S3K_ROM}" test -B
```

The wrapper admitted normally after 3,621 seconds with its own budget of 4 GiB
and four cores; 3,770 production and 3,627 test sources freshly compiled at Java 21.
It completed at `2026-10-08T12:18:31Z`, exit 0 / BUILD SUCCESS, Maven execution
10:48. All 20 fresh XML reports were independently inspected (mtime 12:09–12:18Z):
210 cases, zero failures, errors or skips. Sitar's 13 classes contribute 152 cases
(Arcade's 39 include one upstream helper check); native music/cues 18, API
signatures 9, SDK 10, Javadoc 7, links 1, release policy 13. The XML ROM properties
point at the three original main
filenames; SHA1/CRC hashes independently match the documented REV01/locked-on
identities. This is focused composition verification, not a new full-suite pass.

The tested source index tree remained exactly
`1af721093e5f4414770fac80df632f982b5dacc4` throughout execution. A separate invocation
of freshly compiled `ModApiSignatureSurface --snapshot` generated 17,274 lines,
exactly equal to the merged candidate pin. Against bf7 it has precisely the
`SceneMusicPlayer.PLAYHEAD` field and `cuePart(long,int,double,double,double,double)`
method additions, zero removals and no additional exported types. No manual
candidate version/schema change or published-pin edit is introduced.

At this checkpoint, the updated change plan selected 3,058/3,058 ordinary classes,
one worker and fresh guards; Java 21/Lua 5.4/PowerShell tool preflight passed.
Normal combined candidate validation retained the 150-minute execution cap (queue wait excluded,
10-minute no-output rule) and compare complete failures/skips with the framework
owner's qualified updated base. Main remains under that owner's kit/evidence/push
freeze. Mandatory actual-main qualification, develop push and owned cleanup remain
pending; this private checkpoint does not claim delivery.

## Updated framework candidate qualification

The complete updated baseline is the framework owner's actual-main run
`20261008T103708Z-730bd986`, at
`bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a`: 3,056 selected ordinary classes,
3,054 reports / 26,514 cases / 27 failures / zero errors / 62 skips, followed by
87 fresh guard reports / 674 passing cases without skips. Attribution and the
complete uncapped failure/skip tables are in the
[framework readiness plan](2026-10-07-mod-framework-product-readiness.md).
Those tables were read in full and compared by identity and concrete assertion,
not merely by totals.

The updated private candidate was frozen clean at
`984cb8e2e317848344883879088762ebac7cbc88`. Its actual command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base bf7c56e1986fd8ca4a4c5e0cafdf3087e73b545a --max-minutes 150 --run
```

Run `20261008T123028Z-dbe57c8c` admitted immediately at `12:30:28Z`, selecting
all 3,058 ordinary classes with one worker and fresh guards. Ordinary completed
in 4,978.39 seconds: 3,056 reports / 26,535 cases / 27 failures / zero errors /
62 skips, exit 1. Fresh guards completed in 222.71 seconds: 87 reports / 674
cases / zero failures, errors or skips, exit 0. The outer runner terminated with
exit 1 and failed status before the `13:58:02Z` inspection. Both lanes completed;
there was no timeout and all failure/skip omission counts were zero. This is
qualification with inherited failures, not an all-green suite claim.

All 27 failure identities and exception types match the baseline. Twenty-six
full assertion first lines match literally. The complete SSZ first line is
2,951 characters; after only its exception prefix and the previously verified
`RewindObjectStateBlob@hex` suffix normalization, all 2,907 remaining characters
match. No concrete field is removed. All 62 skip identities and first causal
reason lines match literally, with zero added, removed or changed skips and no
ROM-prerequisite skip. There are no new, worsened, resolved or unattributed
failures in this candidate comparison.

Fresh ordinary XML was inspected before cleanup: all 13 Sitar classes / 152
cases, native scene music and part cues / 18, API signatures / 9, SDK / 10 and
Javadoc / 7 passed without skips. Required S3K AIZ / 8, bootstrap / 6, decoding /
3 and both level-loading classes / 36 + 7 also passed without skips. The actual
launch properties used the three original absolute main ROM filenames; their
SHA-1 and CRC32 values were independently rehashed and match the required
REV01/locked-on identities.

HEAD and clean tracked status were rechecked after termination. The launch
working-tree fingerprint was
`45d754079c61a71e8e2f6be15f2e5e3f55c903b68262f5255a7eab6f2ccd14f8`.
After complete consumption, the exact run's normal acknowledgment exited 0 and
its directory was verified absent. Raw diagnostics are not archived.

The framework owner subsequently published
`d740b7a0fadd97b2e7c104d56481a0235bdffb4c` and released main's freeze after owned
cleanup. Its successor delta from bf7 is confined to content-policy tooling and
prose, with no engine, Java-test or API delta. The owner reports 32 policy checks
and 137 focused Java guards passing on that policy repair. This is attributed
owner evidence, not a second local execution. Reconcile that successor before
main integration, then run the mandatory actual-main ordinary and fresh guards
against the actual pre-integration develop commit. Develop push and owned
worktree/branch cleanup remain pending.

## Published policy successor composition

After the framework owner's explicit freeze release, main was fetched and its
current `develop` branch fast-forward checked: already at published
`d740b7a0fadd97b2e7c104d56481a0235bdffb4c`. All three unrelated dirty submodules
and four known untracked paths were preserved. No main branch switch occurred.
The published successor merged privately without conflicts. Index comparisons
show no delta from tested `984cb8e2e` in engine, Java tests, examples, POM, API
policy or Maven settings; the composed hook and snapshot-policy inputs match
d740 exactly. The candidate pin and its two generic part-cue members are retained.
The updated owner's policy evidence applies to those identical policy inputs;
the upcoming mandatory actual-main run will also execute fresh structural guards.
The actual pre-integration base is d740, subject to a final main-head check before
integration. This composition is not yet a develop delivery.

## Actual-main qualification

The feature merged into main's existing `develop` branch as
`eaafa6ee053f5624c00a78652e841d8f78598f5d`, against pre-integration base
`d740b7a0fadd97b2e7c104d56481a0235bdffb4c`. No branch switch occurred. All three
unrelated dirty disassembly submodules and four known untracked paths were preserved.

Two interrupted main invocations provide no delivery qualification. A concurrent
SOZ fixture merge changed executable test/BK2 inputs during the first invocation;
only the verified owning runner was cancelled, exit 130, and its incomplete run
was acknowledged. The replacement, run `20261008T150646Z-9377cfcd`, lost its native
command session with exit 143 before completion. The origin of that signal was
not established. Its exact surviving Maven/test process group was identified by
PID, working directory and arguments, stopped, and verified absent before the
incomplete run was acknowledged. No partial count is treated as a pass. A bounded
supervisor retained the next normal invocation, its explicit terminal exit and
the runner's normal 150-minute admission-excluded limit.

The successful ordinary execution was frozen at
`6124a524eef9b42efb800d5bcb95376147507c9e`, preserving the separately qualified SOZ
fixture changes. Its command was:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base d740b7a0fadd97b2e7c104d56481a0235bdffb4c --max-minutes 150 --run
```

Run `20261008T153213Z-5cd93bac` admitted at `15:32:13Z` and completed ordinary at
`16:57:28Z`: all 3,058 selected classes, 3,056 reports / 26,535 cases / 27 failures /
zero errors / 62 skips, 5,115.06 seconds, Maven exit 1. All diagnostic omission
counts were zero; there was no timeout. All 27 failure identities and exception
types match the updated bf7 framework baseline. Twenty-six full assertion first
lines match literally. The complete SSZ first line is 2,952 characters and matches
all 2,907 normalized characters after only its exception prefix and the previously
verified `RewindObjectStateBlob@hex` suffix normalization. All 62 skip identities
and first causal reasons match literally, with no ROM skips. There are no new,
worsened, resolved or unattributed failures.

Fresh ordinary XML independently confirms all 13 Sitar classes / 152 cases,
ROM scene music and part cues / 18, API signatures / 9, S3K AIZ / 8, bootstrap / 6,
decoding / 3, both level-loading classes / 36 + 7, SOZ / 9 and documentation links /
1 pass without skips. Infinite Sonic has 238 cases: 206 pass and the same 32
accepted skips. SDK / 10 and Javadoc / 7 also pass without skips, as established
by their fresh Maven completion lines; their main XML was not independently read
before automatic cleanup. The three actual absolute ROM paths identify the
original main filenames; rehashed SHA-1/CRC32 values match the documented
REV01/locked-on inputs.

During that run, develop advanced to published
`17ae561add0c88ec6e2e5c84d55a7457066b1a3b`. The seven changed paths contain only
GraalVM feasibility research, standalone probes outside Maven inputs, and related
prose. The exact 6124-to-17ae diff is empty for runtime, Java tests/resources,
examples, POM, Maven settings, hooks, API policy and testing tools. The research
owner separately qualified the probes' syntax, command behavior, negative controls
and actual JVM/native registration; that is attributed owner evidence. Reconstructing
the launch fingerprint using the original HEAD and current dirty/untracked inputs
matches `4a9c8073fc3350ab40178f80b38533b6bb77f1ba4294a644f6385b00150b3ace` exactly.
The ordinary result therefore qualifies unchanged executable inputs. The category
runner correctly refused guards after its HEAD fingerprint changed: outer exit 2,
status incomplete. This is not a completed combined-run claim. The exact consumed
ordinary run was acknowledged, exit 0, and its directory verified absent.

A separate fresh normal guard invocation qualified actual main at 17ae:

```bash
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py \
  -Dmse=off -Pguards test -B \
  -Dopenggf.surefire.reports=target/sitar-fumble-main-guards-reports \
  -Dopenggf.test.tmpdir=target/sitar-fumble-main-guards-tmp
```

The actual two output arguments were absolute main `target/` paths. The retained
supervisor launched once at `17:00:45Z`, with a six-hour outer lifetime including
queue wait. Admission waited 590 seconds; Maven completed at `17:15:24Z` in
04:46, exit 0 / BUILD SUCCESS. All 87 fresh XML reports were independently read:
674 cases / zero failures, errors or skips. No selection was narrowed. HEAD,
known dirt and the complete guard launch fingerprint
`4c515b6c756b4b928037b53f4cb5454a8685c7a986a3fe0e3ab5104b7a21a037` were unchanged
after termination. Both supervisors and all identified owned child processes were
verified absent. Raw reports and logs remain temporary until final cleanup.

Together, the source-equivalent complete ordinary lane and fresh actual-main guards
satisfy the integration qualification. The whole ordinary suite retains its 27
known failures; it is not all green. Remaining follow-ups change evidence prose only
and require documentation/policy checks, not another unchanged engine execution.
