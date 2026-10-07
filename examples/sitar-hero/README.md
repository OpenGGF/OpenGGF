# Sitar Hero

A source-first rhythm mod for the unpublished OpenGGF Mod API 0.7 candidate.
Play a career tour, quick song or no-fail practice, or share a performance in local
or direct-connect co-op and score duels. Seven cosmetic Sonic performers play
four instruments, with native ROM hands and arms moving on successful notes.
Music, stages and characters come from your ROMs at runtime; the jar contains
code, chart selections and original pixel instrument props.

## Build and play

Use Java 21 and Maven from this checkout:

```bash
python3 examples/sitar-hero/build.py
python3 examples/sitar-hero/build.py --run --s1 /absolute/path/s1.gen --s2 /absolute/path/s2.gen --s3k /absolute/path/s3k.gen
```

Supply any nonempty subset of those three options. The script writes an isolated
configuration under `target/examples/sitar-hero/run/` and produces
`target/examples/sitar-hero/sitar-hero.jar`. `--skip-engine` reuses a compiled
engine. With ROM paths already configured, `python3 examples/build_example.py
sitar-hero --run` also works. Code mods use the JVM engine's normal trust flow.

The 79-song library combines every supplied game: eleven Sonic 1, twenty-two
Sonic 2 and forty-six Sonic 3 & Knuckles selections. Knuckles can perform Green Hill, for
example. Sonic and Robotnik are available from any supported ROM; Sonic 2 adds
Tails and Silver Sonic, and Sonic 3 & Knuckles adds Tails, Knuckles, Mecha Sonic
and Sky Sanctuary Egg Robo.

Looping songs last at least two complete outer loops including the opening intro,
or two minutes, whichever is longer. Scrap Brain therefore lasts 144 seconds.
Non-looping ending and credits tracks play once from their beginning to their
native final stop, including medleys and tempo changes. Sonic 3 Credits loops
in the ROM and follows the ordinary full-song duration rule. Short title, Continue,
power-up, result and short Knuckles character cues are excluded. Missing percussion or PSG parts are shown
as unavailable rather than replaced with invented notes. Each full and selected
part mix prepares in the background with progress and cancellation before play.
Songs without a supported scenery profile use an available concert stage from
the same ROM; their music and chart keep the selected song's identity.

## Modes and difficulty

Career journeys through each installed game's worlds in their original order.
Clear every main-act song at a stop to open the next; bonus stages, alternate
themes and competition tracks are optional side gigs. Instrument, difficulty
and performer changes retain tour progress. Required songs with an absent part
offer an instrument change rather than disappearing from the journey.

Robotnik promotes the shared tour with a takeover scheme behind the staging.
Sonic stays knowingly for the crowds, Tails brings both enthusiasm and his own
judgment to the equipment, and Knuckles investigates what the tour is doing to
his island. Short authored intermissions give them room to disagree, cooperate
and enjoy a show, with performer-specific quips. Enter/A advances each exchange,
Escape/B skips, and R/gamepad Y or the world-board button replays its scene.
After a tour is complete, F/gamepad X or its button replays the finale.
Concert success remains a win when performing
as Robotnik, even when his offstage scheme fails.

Quick play opens the whole library and saves separate highest-score and
best-star records for each song, instrument and difficulty, as Career does.
Only completed earned Career performances advance the story journal; Quick
Play records do not skip worlds. Practice suppresses failure and saves no
records. Tool autoplay is labelled DEMO and also saves no earned progress.

Easy uses three melodic frets and sparse single notes; Medium uses four, and Hard
and Expert use five with progressively denser genuine attacks. Bongos retains its
four pads plus kick, simplifying fills on Easy. Sitar selects the FM melody,
Harp the FM accompaniment and Synth the PSG part. Authored voice handoffs repeat
with the music's form. Chords require real simultaneous pitches; source attacks
retain their ROM sample positions at every difficulty.

Local co-op and score duel use one audio clock with two independent control
profiles, judgments, scores and animated performers. Both players choose their
performer and share a song, part and difficulty. Co-op fails only when both rock
meters reach zero; score duel compares the two scores at the end. Multiplayer
attempts do not affect solo records.

## Controls

Menus accept arrows/Enter/Escape, ordinary gamepad D-pad/A/B/Start, or the mouse.
Wheel scrolls long lists; left-click activates a visible action and right-click
returns. Tab in selection opens settings.
On a career setlist, I/gamepad X or the Part & level button changes instrument
and difficulty and returns to the
same gig; Back returns to the world board. Scores remain independent of the
shared tour progression.
The scene stores its bounded story journal in `career.txt` separately from
`profile.txt` personal records, under its normal mod save root. Old score-only
records do not manufacture career clears. Skipped or viewed intermissions stay
replayable, and a pending tour finale is recovered when reopening that tour.

| Action | Player 1 keyboard | Player 2 keyboard | Gamepad |
|---|---|---|---|
| Frets / pads 1–4 | A, S, D, F | J, K, L, ; | Left trigger, LB, RB, right trigger |
| Fret 5 / kick | G / Space | ' / P | A |
| Strum up / down | Up / Down | [ / ] | D-pad up / down |
| Whammy | Space | Right Shift | Right stick up |
| Star Power | Left Shift | Right Alt | Back / View |
| Pause | Escape | Escape | Start / Menu |

Gamepad profiles default to device 0 for player 1 and device 1 for player 2.
Each player can remap keyboard and pad alternatives independently for melodic
and bongo instruments. Hold matching melodic frets and strum at the line; hold
sustain tails. White-centred gems can be hammered on after a successful chain.
Lower frets may stay held beneath one higher fret; chords require exact frets.
Bongos strikes on new pad presses without strumming or holding tails.

In settings, Enter/A captures a binding, Tab changes device type and Delete clears
it. Select the player and instrument profile before editing. Adjust input and
display offsets, lefty flip, reduced flashes and highway speed independently.
Tap the selected player's kick key or that player's pad A to the original drum
hits at least eight times, then Enter saves the median input offset. Positive
input offset compensates late observed taps; display offset moves note travel.

Notes score 50 per gem, sustains add 25 ticks per musical beat, streaks build a
multiplier up to 4×, and Star Power doubles it. Complete bright phrases to charge
power; whammy their tails for extra charge and activate at half a meter. Misses
and extra strikes lower ROCK and mute your part; the next hit restores it while
the backing continues. Successful judgments drive native arm/hand strokes and
small sound waves, including independent gestures when both local players choose
the same character. Pausing clears transient gestures and freezes the song.
Audio/input interruption pauses with a notice. Failed speaker output stays
frozen; restore output and begin a fresh attempt.

## Direct-connect

Choose Direct-connect, enter `IP:PORT` (default `127.0.0.1:34917`), then host co-op
or a score duel, or join a host. The address field accepts letters, digits, periods
and `;` for a colon. Both players need matching mod builds, audio sample rates and
at least one matching ROM. The host selects the shared song, part and difficulty;
the guest confirms the offer after choosing a cosmetic performer. Both prepare
the ROM audio before the synchronized count-in.

The connection uses TCP on the entered port: LAN, localhost, or an explicitly
forwarded port. There is no discovery, NAT traversal or Internet account service.
Only explicit IP literals and localhost are accepted; transport is plaintext.
Each peer judges its own timestamped inputs against its consumed audio clock.
Remote progress is display data and never creates solo records. Pause/resume is
coordinated: each player releases their own pause request, so both must release
if both paused. Rematches are offered by the host. A mismatch, disconnect or
malformed message stops the match with a recoverable notice.

## Learn from this example

- [SitarScene](src/main/java/sitarhero/SitarScene.java) owns menus, loading, modes,
  results, records, player settings and lifecycle cleanup.
- [CareerTours](src/main/java/sitarhero/model/CareerTours.java) authors the native
  world route; [CareerJournal](src/main/java/sitarhero/model/CareerJournal.java)
  keeps bounded shared story progress independently of personal scores.
- [CareerStory](src/main/java/sitarhero/story/CareerStory.java) contains original
  intermissions and contextual performer quips, built without static shared state.
- [RhythmSession](src/main/java/sitarhero/model/RhythmSession.java) judges physical
  inputs in sample coordinates independently of drawing and performer choice.
- [ChartCurator](src/main/java/sitarhero/chart/ChartCurator.java) combines native
  song forms and tempo maps with role selections, difficulty, chords and tails.
- [MappedControls](src/main/java/sitarhero/controls/MappedControls.java) preserves
  raw press/release edges and independent keyboard/pad alternatives.
- [PerformerArt](src/main/java/sitarhero/PerformerArt.java) layers native ROM body,
  arm and hand cutouts around instrument props and successful-hit gestures.
- [OnlineMatch](src/main/java/sitarhero/net/OnlineMatch.java) coordinates peer
  readiness, clock sampling, starts, controls and bounded progress reports.

The host synthesizes bounded PCM with the ROM's SMPS driver and chip voices.
The full and masked mixes retain shared-chip interaction. Full songs and parts
load in cancellable jobs with a ten-minute cap and a 256 MiB combined PCM budget.
Playback and input share the consumed audio coordinate, with optional OpenAL Soft
latency correction and saved calibration. Closing the scene cancels workers and
releases its playback, connection and additionally opened ROMs.

The [full-version design and verification record](../../docs/architecture/designs/2026-10-07-sitar-hero-full-version.md)
separates observed checks from remaining fidelity limits. GH III is the rules
reference; the ±100 ms judgment window, rock weights and residual vibrato whammy
are explicit tuning choices without original executable/DSP certification.
Queued audio delays miss/whammy feedback by the device buffer. Pad polling has
an observation timestamp. Concert stages are static ROM rasterizations.

For reproducible artwork and arcade captures, compile engine test classes and
run `com.openggf.tools.SitarHeroCapture --rom-root /absolute/rom/folder --out
/absolute/capture/folder --subset all` on
`target/test-classes:target/classes:<test dependencies>`. The helper uses existing
`s1.gen`, `s2.gen`, `s3k.gen` paths and accepts individual subsets in separate JVMs.
Captures use labelled autoplay and offline PCM; they show assets and flow rather
than physical speaker/controller timing. `ExampleModCapture` also accepts scripted
mouse/key actions and scene debug commands for individual screens.
