# Sitar Hero presentation, audio and learning-path polish — 2026-10-08

Branch `feature/ai-sitar-hero-polish`, worktree `.worktrees/ai-sitar-hero-polish`,
created at `a87271f43` (develop after the full-version integration). The parent
owns integration, broad gates, push and cleanup. Repertoire, charts, judgment
windows, the audible sample clock, multiplayer protocol and earned-progress
rules are out of scope and must not change.

## Audit findings at `a87271f43`

Presentation

1. The title is a list panel over a backdrop dimmed by a 68% overlay and a
   checkerboard. ROM scenery is barely visible and there is no composition: one
   performer stands in a side box. The header shows `TOUR`, a leak of the default
   mode, and `4 LEVELS` is ambiguous.
2. Performers float. Play, loading, story and results draw them at fixed rows
   unrelated to the level floor, so the AIZ play screen shows Sonic in mid-air
   in front of the trees.
3. Every screen change is a hard cut. The selection highlight jumps, list pages
   jump, and nothing carries continuity between steps.
4. Story intermissions show the speaker alone over the dimmed backdrop. The
   listener is absent and speaker changes have no emphasis.
5. Loading shows a static panel with the performer floating over it. Count-in is
   a plain digit with no beat cue. Results are static: no tally, no star reveal.

Audio

6. Menus are silent. The scene only calls `stopMusic()` in `enter`. It plays no
   menu theme, navigation/confirm/back/denied cues, start cue, results jingle or
   failure cue.
7. An active `SceneMusicPlayer` replaces stock driver output entirely
   (`ManagedSceneMusic.Player.render` overwrites the frame), and player pause
   pauses all audio. Stock SFX during a performance are therefore inaudible, and
   a menu theme left running under a performance becomes audible mid-phrase when
   the player stops. The creator guide does not mention either effect.

Code and API

8. `SitarScene` (1,134 lines) mixes flow, menu input, loading, the performance
   loop, settings, online and every screen's drawing.
9. `PerformerArt.draw` selects its role from an instrument label string, and the
   scene derives that string with `label().split(" / ")[0]`.
10. The manifest description still advertises three songs. The version is 0.1.0.
11. `SceneMusicPlayer.setWhammy` names a guitar-controller concept in a generic
    API. Renaming it is a signature change, so it is only proposed to the parent;
    see "API proposals".
12. Tests and `SitarHeroCapture` read many private scene fields reflectively.
    Extraction must keep those names, or update the tests in the same commit.

Documentation

13. The README is a feature inventory plus a player manual. It has no reading
    order, no lifecycle walkthrough (manifest → `GgfMod.register` →
    `ModSceneFactory` → `ModScene` calls), no mod-versus-host boundary and no
    recipes for changing a song, performer, gig, menu or sounds.

## Design

**State changes are immediate; visuals ease.** Screen and selection changes keep
their current tick semantics, so tests, input and the music clock are unaffected.
Transitions are draw-only functions of the tick at which a screen or selection
last changed. No transition delays input, hides controls or touches audio timing.
Reduced flashes removes light shimmer and highlight pulses but keeps slides and
fades, which are motion rather than flashes.

1. **Concert stage** (`stage/ConcertStage`). It composes a zone backdrop with
   slow parallax pan, a level-foreground strip built once and panned within, and
   performers standing with their lowest opaque native pixel on the real
   `levelStages` floor. A vignette keeps text readable. Venues are the installed
   ROM pictures (S1 GHZ1, S2 CPZ1, S3K AIZ1/AIZ2/HCZ1/LBZ1/SSZ1). Unsupported song
   zones keep the existing same-ROM fallback.
2. **Main menu tableau** (`stage/TitleStage`). The title has a large logo and a
   left-hand menu column with an eased cursor. The band plays on the real stage:
   one performer per instrument, chosen from the installed roster (S1 gives a
   duo, S3K a quartet). Members tune up with sparse, staggered rehearsal
   gestures. These use a separate band instance, so menu motion never reaches a
   judged performance. The venue rotates among installed venues on each return
   to the title. Stage lights are static, with a slow sweep that reduced
   flashes disables.
3. **Transitions** (`ui/Motion`). Content enters with a short eased slide-and-fade
   (about 10 ticks). The selection highlight glides. Loading progress eases.
   Count-in digits pulse on the audible beat derived from the sample position.
   Results tally the score with the ROM tally convention and reveal stars in
   sequence. Story lines slide in and both conversation partners stand on the
   world's stage, with the speaker emphasised. Pause fades its veil in. Each
   step stays responsive from its first tick.
4. **House sounds** (`audio/HouseAudio`). One owner holds the stock-driver music
   state using the running game's own cues, cited from each ROM's disassembly
   usage:
   - menu cursor: `sfx_Switch`/`SndID_Blip`;
   - confirm: ring;
   - denied: the ROM error cue where one exists (S2 `$ED`, S3K `$B2`); S1 has none,
     so it is silent;
   - start gig: giant/big ring;
   - tally: switch ticks every 4 frames, then the cash register/tally end;
   - failure: ring loss;
   - results: the act-clear jingle;
   - menu theme: S3K Data Select `$2F`, S2 Options `$89`, S1 Special Stage `$89`
     (S1 has no menu track; its looping Special Stage theme is the gentlest).

   The theme is idempotent and never restarted while current. It fades when
   loading starts or the scene exits and stops before any `SceneMusicPlayer`
   starts. Cues are rate-limited. No cues play during performance, pause or
   calibration, because they would be inaudible and could leak.
5. **Encapsulation.** Screen drawing moves into small view classes. Menu input
   helpers move to `ui/MenuInput`. `Role.instrument()` replaces label splitting
   and `PerformerArt.draw` takes a `Role`. Reflected scene fields keep their
   names. No engine change is required for these goals.
6. **Docs.** The README becomes a learning path: play, then
   `./launch-sitar-hero.sh` (the parent-owned root launcher) or `build.py`, then
   the lifecycle, a source map with reading order, the mod/host boundary,
   recipes, real limitations and capture commands. The creator guide gains the
   SceneMusic-overrides-stock-audio rule and the house-sound pattern.

## Acceptance

- All 12 existing Sitar test classes pass without loosened assertions. New
  focused tests cover:
  - feet-on-floor placement;
  - band selection for every installed subset;
  - draw-only transitions, with screen identical on the change tick;
  - house-audio ownership: menu theme idempotent; stopped before player start;
    resumed after results/pause-to-menu; faded on exit; no cues during play;
    rate limit.
- `SitarHeroCapture` passes for all, S1, S2 and S3K subsets. New title captures
  show the tableau per subset. `ExampleModCapture` sequences show transitions
  mid-flight, and audio WAVs show the menu theme, navigation cues and results
  jingle at the scripted ticks. Offline captures prove scene behaviour, not
  speaker latency.

## As built (`fd39e29bc`, merged with develop as `676a433c4`)

The design held, with these differences:

- **Venues.** The title rotates through Angel Island 1, Green Hill, Chemical
  Plant, Launch Base and Angel Island 2, as installed. Sky Sanctuary was dropped
  from the rotation: its pictured state is a flat sky. Hydrocity's pictured state
  is underwater. Neither reads as a concert.
- **Layout.** The wordmark and menu share one card on the left; the band plays on
  the right. The camera sways ±16 pixels over 20 seconds.
- **Lighting.** Readability comes from the card and a light top haze rather than a
  vignette. The light cones sweep slowly, or hold still with reduced flashes.
- **Caching.** Venues and band members are cached on the scene instance, so returning
  to the title does not decode them again.
- **Code split.** `SitarScene` (rules) and `SitarScreens` (pictures) replace the
  planned per-screen view classes. The screens read the scene's package-private
  fields and never write them.
- **Not done.** Menu input helpers stayed in the scene. A separate `MenuInput` would
  have touched every hit-tested coordinate for little gain.
- **Additions:**
  - the highway bursts each struck lane and marks 50-note streak milestones;
  - a new-best badge follows the results tally;
  - character and instrument previews settle onto their stage line;
  - venues dissolve into each other over 16 ticks;
  - the loading performer label sits below the ROM title card, and the mod
    header is hidden while the card owns the screen.

Offline audio evidence (`ExampleModCapture` WAVs, running game S3K, all ROMs):

- the Data Select theme plays at the title at about -30 dBFS RMS;
- cursor and confirm cues land on their scripted ticks;
- the big-ring cue is followed by a gradual S3K fade from 48 ticks;
- cancelling after the fade restarts the theme, and cancelling before it keeps the
  theme without a restart;
- a song's PCM replaces the driver for its duration;
- after the song, the Act Clear jingle plays with the tally clicks and the register
  (results opened at tick ≈2590 in the paced probe).

One misreading is worth recording. An apparently silent jingle came from treating
the end of the song's audio as the start of results. Extracted frames showed results
opening about 260 ticks earlier, and those ticks were the jingle. Check frames for
the screen state before concluding that audio failed.

After the jingle, the driver's idle output holds a small DC level (sample 120,
-48.7 dBFS). This is driver state, not a cue.

`AudioManager.stopMusic` stops sound effects as well as music. When a song is already
prepared and its player starts within the cue's length, `beforePlayback` cuts the
start cue short. This is accepted rather than hidden behind an artificial loading
delay, and the README states it.

`ExampleModCapture` does not pace ticks to the wall clock. Without video or PNG
output it runs about 7,000 ticks per second, so an asynchronously prepared song may
never start inside a short capture. The README records this.

## Verification at the source freeze (`676a433c4`)

These are focused checks, not a combined-suite pass. At child handoff the change-based
plan selected the full ordinary suite and guards because `examples/` falls back to
shared categories. The parent owns the final scope decision and destination checks.

- **Focused Maven run.** Command:
  `maven_queue.py -Dmse=off "-Dtest=TestSitarHero*,TestModdingDocumentationLinks"`,
  with the three ROM properties naming the original main ROM files, then `test`.
  - It covered 13 classes and 139 tests: 0 failures, 0 errors, 0 skipped.
  - All 13 surefire XML reports were written by this run, and their totals match.
  - New regressions: subset band casting and venue rotation; running-driver cue IDs;
    same-tick priority and the soft rate limit; loading cancel, fade and failure
    returning to an audible menu; pause, results, retry and exit with no latent
    theme; the leave latch; and floor placement for the title band, solo and local
    performers. `PerformerChecks` adds a check that feet rest on the lowest native
    pixel.
  - An earlier draft iteration ran 138 tests with 0 failures and 0 skips.
- **`SitarHeroCapture`, one fresh JVM per subset.** Every subset passed with the same
  picture counts as the pre-polish acceptance:

  | Subset | PNGs | Songs | Performers |
  |---|---|---|---|
  | all | 213 | 79 | 7 |
  | S1 | 66 | 11 | 2 |
  | S2 | 92 | 22 | 4 |
  | S3K | 104 | 46 | 6 |

  The title tableaux show the S1 duo on Green Hill, the S2 quartet (Silver Sonic on
  drums) on Chemical Plant, and the S3K and all-ROM quartets on Angel Island. Media and
  provenance are in `$SITAR_POLISH_ROOT/acceptance-v1`.
- **Promo.** A 71.2-second 1080p60 promo was recorded from this freeze with the 40
  captured mod source files hashed. It passes a full decode at -16.0 LUFS, LRA 4.7 LU
  and true peak -1.5 dBFS. Media, edit list and chapter map are in
  `$SITAR_POLISH_ROOT/promo/edit-v2`.

## Parent delivery scope

The parent fetched and fast-forward checked `develop` at `33d3976c5`. The final
candidate is `98c5eef50`; its only changes after the captured code freeze are prose.
An independently inspected plan for that candidate against the updated destination
selects 3,022 ordinary classes and guards. The full selection comes from the example
paths and the capture helper's fallback classification.

The repository's proportionate-validation exception applies to this presentation
pass. Production changes are entirely inside the mod: stage composition, drawing,
menu-audio ownership and cue choices. There is no engine Java, POM, hook, API
descriptor, signature-pin, sequencer, chart-timing, judgment or protocol change.
Packaged scene tests directly cover navigation, loading, cancellation, failure,
pause, retry, results and exit; new checks cover cue priority and audio cleanup.
The four installed-ROM subsets and native performer placement have fresh rendered
captures. The destination qualification uses the same focused selector on
updated develop and integrated develop, plus fresh structural guards after integration.
This replaces a new broad ordinary run for this bounded change; it does not claim
that the engine's full ordinary suite passed.

The parent also checked all 40 recorded source/resource hashes against both the
captured commit and final candidate, inspected the subset title tableaux and decoded
promo scenes, and observed browser playback reach the final frame without a media
error. Independent `ffprobe` confirms 71.2 seconds, 1920×1080 at 60 fps, stereo AAC
at 48 kHz and twelve chapters. A complete `ffmpeg -xerror` video/audio decode exited
zero. The final MP4 SHA-256 is
`54903ea2ac7d360e24c903b5f21d8dea8eb4cbfabcf4a2d2789f4d98603f875a`.

Updated-base focused comparison at exact `33d3976c5` completed with Maven exit zero
at 2026-10-08 03:22:41 UTC: fourteen fresh XML suites, 141 tests, zero failures,
errors or skips. The command was
`python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestSitarHero*,TestModdingDocumentationLinks,TestModApiSignatureSurface"`
with all three verified absolute original main ROM filenames as their test properties,
followed by `test -B`. The parent inspected all 141 testcase identities and statuses;
this is focused baseline evidence, not a new full-suite baseline.

The integrated destination at `ae2dfd3dd` completed the same focused command at
2026-10-08 03:34:23 UTC, Maven exit zero: fourteen fresh XML suites, 148 tests,
zero failures, errors or skips. All 141 baseline testcase identities and passing
statuses are retained, with exactly seven new passing presentation/audio regressions
and no removed or worsened case. The parent independently inspected the per-case
comparison. API signature checks passed all nine cases and packaging exercised the
actual mod sources.

A separate fresh JVM then ran
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards`
with the same three verified absolute ROM properties, followed by `test -B`.
It completed at 2026-10-08 03:40:00 UTC, exit zero: 86 fresh XML suites, 672 tests,
zero failures, errors or skips. The parent inspected the full fresh report inventory;
older reports were excluded by invocation start time. Both owning invocations ended,
and the known three dirty submodules and four unrelated untracked paths remained
unchanged.

The final follow-up is prose only: this terminal evidence, the unpaced-capture hazard
in the existing measurement catalogue, and clarification that explicit `build.py`
ROM selection prefers S1, then S2, then S3K regardless of option order. No captured
or verified Java/resource byte changed, so these edits do not require another engine
test invocation. The task's final media and reproducible source/edit archive remain
under `$SITAR_POLISH_ROOT`; draft takes, consumed logs and build copies can be discarded.

## Considered and rejected

- **Song previews on song select.** A preview needs a `ctx.music()` player. While a
  player exists, its PCM replaces the driver, so the menu theme and every cursor cue
  would go silent. Each preview also renders its PCM twice (the full mix, then the
  selected part). Deferred: there is no host mixing contract.
- **Count-in clicks through `ctx.audio()`.** They would be inaudible: the player's
  silent lead-in replaces the driver output. The count-in stays visual and follows the
  audible clock.
- **Typewriter story text.** It would add a press, or a wait, before each line can be
  dismissed. Lines slide in over six ticks instead and advance on the first press.
- **Mirrored performers facing the band's centre.** The prop and hand geometry is
  authored facing right. Mirroring would need a second rig per performer for a
  cosmetic gain.
- **Idle breathing on standing performers.** It lifted planted feet off the floor every
  two seconds. Only the hovering Eggmobile now drifts; the rest of the idle life comes
  from Tails' tails and the title's rehearsal licks.

## Promo video (added in flight, 2026-10-08)

After polish and focused verification, produce a 60–90 second promo from the frozen
candidate with `ExampleModCapture` (and `SitarHeroCapture` scenarios). It must show:

- the title tableau;
- a readable career intermission;
- judged instrument animation across roles and games;
- local two-player play;
- Star Power, the results tally and replay;
- a launch outro.

Use real-speed moving footage with the matching ROM audio, nearest-neighbour scaling and
honest labels: autoplay keeps its DEMO label. Show direct-connect only if an actual
two-peer session was captured, and make no latency or NAT claims. Media and per-clip
recipes, edit list, chapter map and provenance go under
`$SITAR_POLISH_ROOT/promo` (an explicit task directory outside the repository). Verify with `ffprobe`
and a full `ffmpeg -xerror` decode.

## API proposals (not implemented; parent declined for this pass)

- `SceneMusicPlayer.setWhammy(double)` → `setPartVibrato(double depth)`. This
  would touch the candidate signature pins, the descriptor and every consumer.
  The current name works; the change is only cosmetic.
