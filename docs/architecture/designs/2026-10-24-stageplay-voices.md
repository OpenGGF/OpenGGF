# Stageplay character speech noises

Task: per-character voices and an audition before re-rendering Day Off. Engine base
`ac46111bcbbbd0812fed4c45d23159a22f9d8360`, branch `feature/ai-stageplay-voices`;
desktop Editor base `6812543`, matching local branch. No push, merge or Day Off
re-render is part of this delivery. The concurrent facing/priority work is left
structurally intact; object-cast edits only attach identity to observations.

Commands use `ENGINE_MAIN` for the shared engine checkout, `ENGINE_ROOT` and
`EDITOR_ROOT` for the requested feature worktrees, and `AUDITION_DIR` for the
external audition folder. The task's external README records the concrete local
paths and commands; repository documentation uses portable variables.

## Decision

Use the engine's SN76489 `PsgChip` to render short deterministic register
performances, then admit their copied PCM through the existing scene-sample scope.
This shares the final music/SFX mix, capture, no-device export and preview lifetime
without commandeering a ROM music channel. Square, octave chime, open-fifth hollow
and periodic-noise buzz voices give distinct registers with six director-facing
controls: tone/off, word/letter rhythm, MIDI pitch, semitone variation, maximum
length in frames and percentage level. The chip's waveform is followed by short
attack/release ramps; there is no separate oscillator, generated runtime asset or
external synthesis service.

Alternatives rejected:

- Importing more WAV cues, as Day Off's original Motobug production script did,
  leaves voice ownership outside the document and requires regeneration whenever
  typing changes. The requested two-way authoring and all-host behavior exclude it.
- A separate square-wave synthesizer duplicates the engine's existing hardware
  output and misses the actual PSG's counters, noise generator and output filter.
- Borrowing live PSG/FM driver channels can steal ROM music/SFX channels and couple
  speech to mutable driver state. Dedicated chip instances isolate their register
  performances. FM patches add a second synthesis design without a clear need for
  this first audition; the four PSG timbres are a deliberately bounded starting set.

## Document and transport

Canonical `voices` contains per-speaker overrides keyed by exactly one player/object
cast ID or exact offscreen name. An absent override selects a default; an explicit
entry contains a complete setting set. The screenplay compiler supplies defaults
for omitted fields, and decompilation retains exact values/order. Canonical v7 and
screenplay v6 change directly: canonical `voices` is required and no old shape is
migrated. The mutable candidate remains 0.7.0; its signature pin and runtime contract description accompany the change. The
strict key/value release descriptor is reviewed and retains its existing candidate
version/status; it accepts no descriptive comments.

`StageplaySpeech.plan` derives its events from `StageplayDialogue.revealFrames`,
not another typing clock. It recognises Unicode letter/digit graphemes, keeps
apostrophes/hyphens inside words, leaves punctuation/space silent and admits at most
one blip per line per revealed frame. Each blip is capped by the next blip/line end.
Stable integer pitch variation depends on the text and spoken ordinal. The plan
is capped at 100,000 events. `timeline` exposes the same production speech plan.

Forward playback owns a separate speech cursor. Repeated sampling/drawing and
seeking never admit a blip. Seek/stop drop tails; resumed playback admits future
reveals only. Native shot replacement renews the sample scope and cursor without
replaying earlier speech. A 32-entry synthesis cache is bounded; transient PCM is
copied, capped at half a second and shares the existing 32-overlap limit. Overloaded
scene-sample blocks scale to 30,000 PCM peak rather than hard-clipping against the
16-bit rails. This is a block headroom guard, not a perceptual loudness guarantee.

Object dialogue resolves name/validation through the object cast. Native object
observations now carry the cast ID, including unplaced Free roles, so tails follow
the actual root position rather than a guessed authored anchor or ambiguous layout
index. Missing/hidden/removed or unresolved objects have no invented pointer.

## Authoring and defaults

Both frontends use the same six-field form and Undo/Redo history. `DEFAULT` removes
the override; `OFF` silences it. A line's menu also reaches its speaker's voice.
Object roles appear in the speaker chooser. Deleting an object removes its dialogue
and voice atomically; detaching a player as offscreen speech preserves its sound.

Initial audition defaults, before the user listening selection below:

| Speaker | Initial default |
| --- | --- |
| Sonic | Square, words, note 72, variation 5, 5 frames, 80% |
| Tails | Chime, letters, note 84, variation 3, 2 frames, 72% |
| Knuckles | Hollow, words, note 55, variation 2, 8 frames, 88% |
| S1 Motobug | Buzz, words, note 69, variation 4, 8 frames, 80% |
| S1 Buzz Bomber | Chime, letters, note 75, variation 4, 3 frames, 80% |
| Boss roles | Hollow, words, note 48, variation 3, 11 frames, 90% |
| Offscreen name | Square, words, note 64, variation 2, 7 frames, 75% |

Ordinary object defaults derive from object ID/subtype and semantic boss role,
never the editable cast/display name. Every possible object type is not promised a
unique timbre; the director can distinguish additional roles explicitly.

## Verification and audition

The initial object-speaker regression reproduced `Unknown dialogue speaker: moto`
on the base. Focused validation on the implementation ran 152 tests with zero
failures/errors/skips:

```bash
python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplaySpeech,TestStageplaySpeechAudio,TestStageplayDialogue,TestStageplayDialogueWorkspace,TestStageplayDialogueRendering,TestStageplayScriptCompiler,TestStageplayScriptDecompiler,TestStageplayScriptRoundTrip,TestStageplayObjectScriptCompiler,TestStageplayStore,TestStageplayDocument,TestStageplayObjectDocument,TestStageplayTimeline,TestStageplayAudioSamples,TestStageplayAudioLifecycle,TestStageplayCueDispatcher,TestStageplayCaptureTool,TestStageplayTool' -Dsonic2.rom.path=$ENGINE_MAIN/s2.gen test -B
```

This includes actual final PCM reproducibility/onset/seek/headroom checks, shared
form edits and object selection/deletion, pointer identity and every shipped
canonical/screenplay example. It is bounded validation, not a broad smoke or
full-suite result. The lead schedules broad validation. Further guard, Editor and
export results are recorded below after completion.

The external audition directory is
`$AUDITION_DIR/`. Its generator reads two lines
per character from Day Off's existing screenplay and authors fifteen eight-second
reads: default and two alternatives for Sonic, Tails, Knuckles, S1 Motobug and S1
Buzz Bomber. First line is solo; the second uses ROM Green Hill music. The combined
scene uses actual Stageplay export, then FFmpeg makes MP4/WAV delivery encodings.
`index.html` supplies labels, notes, playback and a local shortlist. This is staged
voice casting, with no gameplay/parity claim or public promo publication.

Audio is checked by PCM/event tests and encoded loudness/true-peak measurements.
The agent cannot hear; timbral appeal, fatigue and perceived music balance require
the user's listening pass. The original Day Off sources and film remain untouched.

### Completed checks

- The full `-Pguards` run executed 770 checks, with one new failure in
  `TestArchUnitRules.stageplay_boundaries_have_explicit_owners`. The new planner
  reads ROM-free object role metadata and the synthesizer owns chip access; their
  exact consumers/owner dependencies are now declared, with negative assertions
  keeping synthesis out of the editor and audio out of the planner. All 34 checks
  in `TestArchUnitRules` passed on the corrected declarations. Other passing guards
  were not repeated. This is a full guard selection plus a bounded corrected-check
  rerun, not a single unchanged all-green invocation.
- The bounded follow-up below passed the shared forms, 16 object-cast checks
  (including ROM-backed cases), 11 API signature checks, 12 SDK checks and all 34
  architecture checks. Its only error was the attempted descriptive comment in the
  strict release descriptor. That comment was removed; the 15 release-policy tests
  passed in the separate lean rerun. The descriptor retains candidate 0.7.0.
- The final mixer check ran 10 tests with no failures/errors/skips, including real
  captured PCM, deterministic chip timbres and an overload guard. A new zero-packet
  assertion keeps silent overlays from reducing the borrowed soundtrack.
- `scripts/install-engine.sh` packaged and installed the worktree engine into the
  Editor's private Maven repository. The Editor package and 19 focused checks
  passed with no skips. The shortened help line was inspected in a fresh offscreen
  voice-form image, with its separate package/picture check passing (one test).

```bash
# Full structural selection: 769 pass, one new explicit-owner failure, corrected below.
env -u DISPLAY -u WAYLAND_DISPLAY python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
# Corrected architecture declaration, object runtime, API and SDK checks.
python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestArchUnitRules,TestModApiSignatureSurface,TestModApiReleasePolicy,TestModApiSdkPackager,TestModSdkArtifactVerifier,TestStageplayObjectCast,TestStageplayObjectCastRom,TestStageplayDialogueWorkspace' -Dsonic1.rom.path=$ENGINE_MAIN/s1.gen -Dsonic2.rom.path=$ENGINE_MAIN/s2.gen -Ds3k.rom.path=$ENGINE_MAIN/s3k.gen -Dopenggf.headless.gl=surfaceless-egl test -B
# Strict descriptor correction: 15 pass.
python3 $ENGINE_MAIN/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestModApiReleasePolicy' test -B
# Final mixer edge case: 10 pass.
python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplaySpeechAudio,TestStageplayAudioSamples,TestStageplaySpeech' test -B
# From the Editor worktree: 19 pass, plus package.
env -u DISPLAY -u WAYLAND_DISPLAY python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayChrome,TestStageplayChromePictures#captureCompactObjectVoiceForm' -Dopenggf.stageplay.desktopPictures=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=$ENGINE_MAIN/s2.gen package -B
# After the help-text correction: one fresh picture check and package pass.
env -u DISPLAY -u WAYLAND_DISPLAY python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayChromePictures#captureCompactObjectVoiceForm' -Dopenggf.stageplay.desktopPictures=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=$ENGINE_MAIN/s2.gen package -B
```

### Audition evidence

The final source is `audition-v2.screenplay.json` / `audition-v2.stageplay` in the
external task directory. The first staging take used animation 0 and showed a
walk cycle in place; visual review rejected it. The final take uses idle animation
5, centres at X=704 on the grass, smaller dialogue panels and explicit default
labels. No original Day Off file was edited. The real export command was:

```bash
python3 tools/stageplay/stageplay.py render --scene $AUDITION_DIR/audition-v2.stageplay --out $AUDITION_DIR/audition-lossless.mkv --scale 4 --s1-rom $ENGINE_MAIN/s1.gen --s2-rom $ENGINE_MAIN/s2.gen --s3k-rom $ENGINE_MAIN/s3k.gen
```

The 120-second MP4 is 1920×1080 at 60 fps, with 48 kHz stereo AAC. It measures
−23.22 LUFS integrated and −8.88 dBTP. Individual encoded clips range from −24.61
to −21.16 LUFS and −10.71 to −8.88 dBTP; all lossless clip PCM has zero rail samples.
All 263 speech-plan events match reveal frames. Every solo line's first PCM begins
three samples (0.0625 ms) after the scheduled reveal sample, within the correct
frame. Per-voice/per-section figures are in external `measurements.json`.

Two independent real 120-frame exports matched both decoded audio and video-frame
hashes; their PCM also matches the audition master's first 96,000 stereo sample
frames. The external `determinism.json` records the hashes. Cut exact decoded PCM
sample ranges for the individual WAVs/MP4 audio; a fast FLAC input seek moved the
cut audio early and was rejected. That hazard is recorded in the existing
measurement catalogue. FFmpeg decoded all sixteen delivered MP4s with `-xerror`.

The browser index exposes all fifteen clips, unique posters and local shortlist
controls. DOM checks confirmed controls, dimensions, duration and saved/restored
shortlist behavior, and the combined video completed muted playback. Browser
screenshots timed out; the page layout was inspected through HTML preview, and
native contact sheets/form images were inspected separately. No listening pass
was possible. Subjective timbre, fatigue and perceived music balance remain for
the user, and broad smoke/integration remain with the lead.

The final implementation was packaged/installed again after the mixer edge-case
fix, and the Editor's final build-only package passed. A second complete native
reel export from that final engine matched every one of the earlier reel's 7,200
decoded video frames and all 23,040,000 PCM bytes. The existing delivery encodings
and measurements therefore remain exact; the retained lossless master is the final
export. This also establishes full-reel export reproducibility, in addition to the
short independent probes. All task-owned review servers/tabs and consumed raw
probe captures are retired; the two requested worktrees are retained for the lead.

## Listening selection follow-up

The user chose Soft Cool for Sonic, Workshop for Tails, Red Rattle for Knuckles,
Patrol Buzz for Motobug and Wing Chime for Buzz Bomber. These supersede the initial
player defaults above. The fifteen original performances are preserved in
`StageplaySpeech.Preset` with their exact audition parameters. Both editors expose
that library through Tone -> PRESETS in the existing six-control form. Choosing a
preset copies editable values; no wire-format or candidate API change is needed.
Explicit scene overrides remain unchanged. Object defaults use canonical registry
character identity rather than an editable cast name or subtype-derived register.
No film or audition is re-rendered for this follow-up. Focused checks cover all
fifteen values, all preset selections, undo/default reset, offscreen binding,
serialization/screenplay round trips and the existing architecture boundary.

The follow-up engine selection passed 104 tests with no failures, errors or skips.
`scripts/install-engine.sh $ENGINE_ROOT` rebuilt and installed the updated engine;
the Editor then passed its package and 18 focused checks with no skips. Commands,
from the engine and Editor task worktrees respectively:

```bash
python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplaySpeech,TestStageplaySpeechAudio,TestStageplayDialogueWorkspace,TestStageplayScriptRoundTrip,TestModApiSignatureSurface,TestArchUnitRules' test -B
scripts/install-engine.sh $ENGINE_ROOT
env -u DISPLAY -u WAYLAND_DISPLAY python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayChrome' package -B
```

The separate structural guard profile passed all 770 tests with no failures, errors
or skips on the same follow-up code. No broad smoke, film export or audition export
was run. The guide's local links, selected-default/preset names and identical skill
mirrors also passed their checks. The delivery remains on the two local feature
branches, without push or merge.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 $ENGINE_MAIN/tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
```
