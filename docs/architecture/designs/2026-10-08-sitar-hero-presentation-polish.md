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
