# Sitar Hero

A rhythm game built as an OpenGGF mod. Play full songs from your own Sonic 1, Sonic 2 and
Sonic 3 & Knuckles ROMs on sitar, bongos, synth or harp, tour every world in a satirical
career, or share a stage in local co-op, local duels and direct-connect matches. Seven
Sonic characters perform with their native ROM sprites; their arms and hands move on the
notes you hit.

The mod ships only code, chart choices and a few original pixel props (the instruments,
a drum riser, stage lights). Every song, stage, character and sound effect is read from
the ROMs you supply, at runtime. It uses the unpublished Mod API 0.7 candidate.

**On this page:** [Play it](#play-it) · [How to play](#how-to-play) ·
[How it is made](#how-it-is-made) · [Recipes](#recipes) · [Testing and captures](#testing-and-captures) ·
[Limitations](#limitations)

## Play it

You need Java 21, Maven and Python 3 in this checkout, and at least one of these ROM
dumps (any subset works):

| Game | Image | CRC32 |
|---|---|---|
| Sonic the Hedgehog | World REV01 | `AFE05EEE` |
| Sonic the Hedgehog 2 | World REV01 | `7B905383` |
| Sonic 3 & Knuckles | locked-on | `63522553` |

The quickest start uses the ROMs your engine is already configured with. In
`config.yaml` at the checkout root, `roms: sonic1/sonic2/sonic3k` name the files
(relative to the checkout) and `default` names the game to launch. Then, from the
checkout root:

```bash
./launch-sitar-hero.sh                 # build the engine and the mod, then play
./launch-sitar-hero.sh --skip-engine   # reuse an engine you have already compiled
```

To try a particular subset without touching your own configuration, name the files
explicitly. This writes an isolated configuration under `target/examples/sitar-hero/run/`:

```bash
python3 examples/sitar-hero/build.py --run --s1 /path/to/s1.gen --s3k /path/to/s3k.gen
```

Both build `target/examples/sitar-hero/sitar-hero.jar` and run it as a development mod.
Code mods use the JVM engine's normal trust prompt.

The library combines every ROM you supply: 11 Sonic 1, 22 Sonic 2 and 46 Sonic 3 &
Knuckles songs. Any performer can play any game's songs. Sonic and Robotnik come from
any ROM; Sonic 2 adds Tails and Silver Sonic; Sonic 3 & Knuckles adds Tails, Knuckles,
Mecha Sonic and an Egg Robo.

The game the mod launches with is the **running game**: `default` in `config.yaml` when
that ROM is available (otherwise the first available of Sonic 1, 2 and 3 & Knuckles), or
the first supplied ROM in Sonic 1, Sonic 2, Sonic 3 & Knuckles order with `build.py`.
Its own sound driver supplies the menu music and
the menu sound effects, whichever ROM a song or stage comes from:

| Running game | Menu theme | Cursor | Confirm | Start | Results |
|---|---|---|---|---|---|
| Sonic 1 | Special Stage | Switch | Ring | Giant ring | Act clear |
| Sonic 2 | Options | Blip | Ring | Giant ring | Act clear |
| Sonic 3 & K | Data Select | Switch | Ring | Big ring | Act clear |

## How to play

### Modes

- **Career tour** visits each installed game's worlds in their original order. Clear every
  main-act song at a stop to open the next; bonus stages, alternate themes and
  competition tracks are optional side gigs. Changing instrument, difficulty or performer
  keeps your tour progress.
- **Quick play** opens the whole library and saves your best score and stars for each
  song, instrument and difficulty.
- **Practice** never fails you and never saves records.
- **Local co-op** and **local duel** put two players on one song, each with their own
  controls, performer, score and rock meter. Co-op fails only when both meters empty; a
  duel compares scores.
- **Direct-connect** plays co-op or a duel with a peer over a LAN or a forwarded TCP port
  (see [below](#direct-connect)).

The career is a satirical story: Robotnik promotes the tour with a takeover scheme behind
the staging. Sonic plays for the crowds, Tails tinkers with the equipment and his own
ideas, and Knuckles investigates what the tour is doing to his island. Short
intermissions are staged on each world's own level. Enter/A advances a line, Escape/B
skips the scene, and R (gamepad Y) or the world board's button replays it. After a tour,
F (gamepad X) replays the finale. Winning a concert as Robotnik is still a win, even when
his offstage scheme fails.

Only completed career performances advance the story. Quick play records never skip
worlds. Captures and tools may run an autoplay performance; it is labelled **DEMO** and
saves nothing.

### Instruments and difficulty

Sitar plays the FM melody, Harp the FM accompaniment, Synth the PSG part and Bongos the
drum samples. Easy uses three melodic frets and sparse single notes, Medium four, Hard
and Expert five with progressively denser genuine attacks. Bongos keeps four pads plus a
kick at every level and simplifies fills on Easy. Chords only appear where the ROM plays
simultaneous pitches. A song with no part for your instrument offers an instrument change
instead.

A song that loops in the ROM plays its intro plus two complete outer loops, or two
minutes, whichever is longer (Scrap Brain therefore lasts 144 seconds). A song that ends
in the ROM, such as the ending and credits medleys, plays once from its start to its
native stop, tempo changes included. Sonic 3's Credits loops in the ROM, so it follows
the looping rule. Short title, Continue, power-up, results and Knuckles cues are not in
the library. If your ROM has no picture for a song's zone, the song is staged on another
act from the same ROM.

### Controls

Menus take arrows/Enter/Escape, a gamepad's D-pad/A/B/Start, or the mouse (wheel scrolls,
left-click chooses, right-click goes back). Tab opens settings from the selection screens.
On a career setlist, I (gamepad X) or the **Part & level** button changes instrument and
difficulty for that gig.

| Action | Player 1 keyboard | Player 2 keyboard | Gamepad |
|---|---|---|---|
| Frets / pads 1–4 | A, S, D, F | J, K, L, ; | Left trigger, LB, RB, right trigger |
| Fret 5 / kick | G / Space | ' / P | A |
| Strum up / down | Up / Down | [ / ] | D-pad up / down |
| Whammy | Space | Right Shift | Right stick up |
| Star Power | Left Shift | Right Alt | Back / View |
| Pause | Escape | Escape | Start / Menu |

Gamepad profiles default to device 0 for player 1 and device 1 for player 2.

Hold the matching frets and strum as a gem reaches the line, and hold sustain tails.
White-centred gems can be hammered on after a successful chain. Lower frets may stay held
under one higher fret, but chords need exact frets. Bongos strike on new pad presses, with
no strum and no held tails.

Each gem scores 50, sustains add 25 per beat, and streaks build a multiplier up to 4×.
Completing bright phrases charges Star Power; whammy their tails for extra charge, and
activate at half a meter to double the multiplier. A miss or an extra strike lowers ROCK
and mutes your part until your next hit, while the band plays on. Your first miss
in a run makes the instrument die away with a small downward bend; a wrong strike
makes a short plink or dull drum hit using that song's own ROM timbre. Further idle
misses stay quiet, and releasing a sustain quietly stops the part. Rapid mistakes
are rate limited rather than producing a wall of sounds.

In local co-op and local duels, both players share one instrumental part: either
player's success keeps it playing, and each owns their fumble cues with a small
left/right balance. Online co-op now follows the same shared-part rule. In an online
duel your own success controls your mix; peer fumbles are quieter. Peer feedback
is presentation data only: it never judges your notes or changes your score. Old,
duplicate and delayed cue packets cannot replay a fumble into another round.

**Settings** remaps keyboard and pad controls per player and per instrument (Enter/A
captures a binding, Tab switches device, Delete clears it). It also has input and display
offsets, lefty flip, reduced flashes (stage lights hold still, hit bursts stay dim and the
highway never flashes) and highway speed. Tap calibration plays the ROM's
own drums: tap the kick on the beat at least eight times, then Enter saves the median as
your input offset.

Pausing freezes the song and score. If audio or input is interrupted, the game pauses
with a notice. If the speaker output fails, the attempt stays frozen: restore output and
start again.

Saves live in the mod's own save folder: `settings.txt` and `settings-p2.txt` (controls
and calibration), `profile.txt` (scores), `career.txt` (story progress) and
`online-address.txt`.

### Direct-connect

Choose Direct-connect, enter `IP:PORT` (default `127.0.0.1:34917`; type `;` for the
colon), then host co-op or a score duel, or join a host. Mod version 0.3.0 uses the SH2 match protocol and requires matching mod
builds and audio sample rates, and at least one ROM in common. The host picks the song,
part and difficulty; the guest picks a performer and confirms. Both prepare the music
before a synchronised count-in.

It is plain TCP to an explicit IP literal or localhost: no discovery, NAT traversal,
accounts or encryption. Each peer judges its own timestamped inputs against its own audio
clock; the other player's score is display only and never creates a solo record. Either
player can pause, and both must release a pause they requested. A mismatch, disconnect or
malformed message ends the match with a notice.

## How it is made

This section is a guided tour for creators. It assumes you have read the
[mod scenes guide](../../docs/modding/guides/mod-scenes.md) or skimmed
[hello-scene](../hello-scene/README.md).

### 1. From manifest to scene

A mod is a jar with a manifest and an entry point:

```yaml
# src/main/resources/META-INF/openggf-mod.yaml (abridged)
id: sitar-hero
type: patch
baseGame: any                    # runs on whichever supported ROM the player launches
entrypoint: sitarhero.SitarHeroMod
```

```java
public final class SitarHeroMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.requireDisplayWidth(400);               // lay out for a 400x224 screen
        context.registerStartupScene(SitarScene::new);  // replaces the game's title screen
    }
}
```

The engine creates a fresh `SitarScene` each time the scene opens. It calls `enter` once,
`update` 60 times a second, `draw` once per presented frame, and `exit` when the scene
closes for any reason. Everything a scene can touch arrives through `SceneContext`: input,
ROM art, two audio sources, storage, peer networking and the ways out.

### 2. Reading order

Read these in order; each builds on the one before.

| # | File | What it teaches |
|---|---|---|
| 1 | [SitarHeroMod](src/main/java/sitarhero/SitarHeroMod.java) | Registration: one startup scene, a fixed width |
| 2 | [SitarScene](src/main/java/sitarhero/SitarScene.java) | The rules: screens, flow, loading, the performance loop, settings, cleanup |
| 3 | [SitarScreens](src/main/java/sitarhero/SitarScreens.java) | The pictures: every screen drawn from the scene's state, never changing it |
| 4 | [stage/ConcertStage](src/main/java/sitarhero/stage/ConcertStage.java) | A real ROM act as a venue: parallax background, terrain, a floor to stand on |
| 5 | [stage/TitleStage](src/main/java/sitarhero/stage/TitleStage.java) | The main menu tableau: venue rotation, band casting, rehearsal |
| 6 | [stage/PerformerArt](src/main/java/sitarhero/stage/PerformerArt.java) | Native ROM sprites cut into body, arms and hands around a pixel-art prop |
| 7 | [audio/HouseAudio](src/main/java/sitarhero/audio/HouseAudio.java) | Menu music and cues from the running game's driver, and when not to play them |
| 8 | [ui/Motion](src/main/java/sitarhero/ui/Motion.java) | Draw-only easing: transitions that never delay input |
| 9 | [model/RhythmSession](src/main/java/sitarhero/model/RhythmSession.java) | Judging inputs in audio-sample time, independent of drawing |
| 10 | [chart/ChartCurator](src/main/java/sitarhero/chart/ChartCurator.java) | Turning the ROM's note events into a playable chart |
| 11 | [model/CareerTours](src/main/java/sitarhero/model/CareerTours.java) and [story/CareerStory](src/main/java/sitarhero/story/CareerStory.java) | The career route and its scenes |
| 12 | [controls/MappedControls](src/main/java/sitarhero/controls/MappedControls.java) | Timestamped key and pad edges, two players, remapping |
| 13 | [net/OnlineMatch](src/main/java/sitarhero/net/OnlineMatch.java) | A small peer protocol over the scene's network port |

The song catalogues in `catalogue/` are data. Each entry records a ROM song's form and
which channels each instrument plays, with evidence in the
[catalogue design records](../../docs/architecture/designs/).

### 3. What the mod owns and what the engine provides

| The mod decides | The engine provides (Mod API) |
|---|---|
| Menus, modes, career route, story text | `ModScene` lifecycle, fault boundary, `SceneContext` |
| Which songs, their channels per instrument, chart density | `ctx.music()`: finite ROM synthesis, part masks, an audible sample clock |
| Judgment windows, scoring, Star Power, rock meter | `ctx.physicalInput()`: timestamped key, button and axis events |
| Which ROM sprites form each performer, and how arms move | `SceneRomArt`: sprites, palettes, characters, zone pictures, title cards |
| Instrument, riser and light props (original pixel art) | `SceneCanvas`: fills, images, sprites, backdrops, text |
| Fumble presets, cooldowns and multiplayer mix policy | `ctx.music()`: bounded, pitch-gliding ROM-part cues without exposing PCM |
| Menu music and cue choices | `ctx.audio()`: the running game's music and SFX by driver ID |
| Save file contents | `ctx.storage()`: small owner-scoped text files |
| The peer protocol | `ctx.network()`: one explicit, bounded peer connection |

Nothing in the engine knows about Sitar Hero. The mod uses only `@ModApi` types, holds no
static state except literal constants, and reads every asset byte from the player's ROMs.

### 4. Time, sound and cleanup: the rules that matter

- **The song clock is the audio.** `SceneMusicPlayer.samplePositionAt(timestamp)` maps a
  physical input's timestamp onto the samples the player is hearing. Judgments use that
  number, never frame counts, so dropped or repeated frames cannot move a note.
- **Drawing never decides anything.** `draw` may be skipped or repeated. Transitions,
  gliding highlights, the results tally and the count-in are all pure functions of the
  current tick and state. A screen changes on the tick its key arrives, and its content
  eases in afterwards.
- **There are two audio sources, and only one is audible at a time.** `ctx.audio()` drives
  the running game's own sound driver: menu theme and cues. `ctx.music()` plays prepared
  song PCM. While a song player exists, its PCM *replaces* the driver's output, and
  pausing it pauses all scene audio. So `HouseAudio` stops the menu theme before a song
  starts (otherwise it would resurface mid-phrase afterwards), drops cues while a player
  exists, and asks for the theme again on every menu.
- **Clean up on every path.** `stop()` cancels a preparation and stops a player on retry,
  back, failure and exit. `exit()` silences the driver and saves settings and career.
  The engine then closes the scene's music, network connection and extra ROMs.

## Recipes

Each recipe is a change to this mod only. Rebuild with `./launch-sitar-hero.sh --skip-engine`.

**Stage the main menu somewhere else.** `TitleStage.venues` lists the acts the title
rotates through. Any act for which `SceneRomArt.hasZonePictures` is true works:

```java
new Venue("s3k", 1, 0, "Hydrocity"),   // add to the list in TitleStage.venues
```

**Recast the band.** `TitleStage.cast` puts your chosen performer on sitar and fills
the other instruments from preference lists, skipping anyone the installed ROMs cannot
supply. Reorder a list to change who plays drums, synth or harp.

**Change a menu sound.** `HouseAudio.sfx` and `HouseAudio.music` map each cue to a driver
ID per running game. The engine's per-game tables list the IDs (for example
`src/main/java/com/openggf/game/sonic3k/audio/Sonic3kSfx.java` and `Sonic3kMusic.java`), and
the desktop `com.openggf.audio.debug.SoundTestApp` plays them. Keep `-1` where a game has
no suitable sound.

**Move a song in the career.** In `CareerTours.all`, a world's third argument lists its
main acts (required to advance) and the remaining arguments are side gigs:

```java
world("s1-spring-yard", "Spring Yard", List.of("spring-yard", "s1-boss")),  // boss now required
```

**Add a performer.** Add a `Roster` constant with its ROM donor rule. In the
`PerformerArt` constructor, build its layers from a `RomSpriteRequest` (art, mappings and
palette addresses from the game's disassembly). Then give `PerformerRig.geometry` the arm
masks and pivots that cut its native hands free. `PerformerChecks` and `SitarHeroCapture`
check that every native pixel survives the cut.

**Add a song.** Add a `SongArrangement` to the game's catalogue: music ID, zone and act
for staging, the loop form in frames and beats, and the FM/PSG channel sections each
instrument plays. Derive these from the ROM, not by ear. The catalogue design records
describe the probes used. Then place it in a world.

## Testing and captures

The engine's test suite builds this mod from source and exercises it: `TestSitarHero*`
in `src/test/java/com/openggf/mods/code/`, plus the mod's own `*Checks` classes under
`src/test/java/`. For example, with your absolute ROM paths:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestSitarHero*" \
  "-Dsonic1.rom.path=/abs/s1.gen" "-Dsonic2.rom.path=/abs/s2.gen" "-Ds3k.rom.path=/abs/s3k.gen" test
```

For pictures, compile the engine's test classes and write their classpath once:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile \
  dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
CP="target/test-classes:target/classes:$(cat target/test-classpath.txt)"
```

`SitarHeroCapture` walks every screen, performer, role, difficulty and career scene for
one installed subset, using the `s1.gen`, `s2.gen` and `s3k.gen` files in a ROM folder.
It records source and ROM hashes beside its pictures:

```bash
java -cp "$CP" com.openggf.tools.SitarHeroCapture --rom-root /abs/rom/folder --out /abs/out --subset all
```

`ExampleModCapture` plays a script of inputs or debug jumps and records PNGs, an MP4 and a
WAV of the scene's music and sound. `SitarScene.debugJump` documents the commands:

```bash
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/sitar-hero \
  --out /abs/out --script "120:down 150:enter" --audio /abs/out/menu.wav --video /abs/out/menu.mp4
```

`ExampleModCapture` runs ticks as fast as it can rather than in real time, while ROM
music renders on a worker thread at its own pace. A capture without `--video` or frequent
PNGs can therefore finish before a song has loaded; give it frames or plenty of ticks.
Captures use autoplay and offline audio. They show the scene's flow, art and sound, not
real speaker latency or controller timing.

## Adding performance feedback to your own mod

Read `model/RhythmSession.java` for consumable hit/miss/strike/tail-release events,
then `audio/PerformanceAudio.java` for the sound choices. Drain events once after
processing a frame's inputs and advances. Keep a previous audibility state so only
the first miss in a run makes a sound; wrong strikes can still cue while muted.
Choose a real attack inside the relevant `SceneMusicPart` section, or use
`SceneMusicPlayer.PLAYHEAD` for a dying note. `cuePart` envelopes and mixes that
ROM fragment while leaving the backing and sample clock intact.

`net/OnlineMatch.java` batches presentation packets at most every 50 ms and sends
cue-free audibility refreshes when idle. Each packet is scoped to a round and an
independent sequence; cue note indices are checked against the agreed chart. The
receiver drops transients on pause and filters old cue times. Score `STATE`
telemetry remains separate. Keep those responsibilities inside your own mod when
copying this pattern; the engine only owns bounded text transport and PCM mixing.

## Limitations

- Judgment windows (±100 ms), rock weights and the whammy vibrato are tuning choices
  modelled on Guitar Hero III's rules, not measured from that game.
- A miss is decided after its ±100 ms judgment window. The first part of that
  attack can therefore be heard before the fumble; muting never predicts a miss.
  Queued audio adds output-buffer delay to muting, fumbles and whammy.
  Calibration corrects judgment, not that feedback.
- Stages are still pictures of a real act (animated tiles show their first frame).
  Supported acts are those `hasZonePictures` lists; others use another act from the same
  ROM.
- Menu cues and music come from the running game only, and are silent while a song plays.
  The driver is stopped (music and sound effects) just before a song starts, so when a
  song is already prepared and starts at once, the giant-ring start cue is cut short.
- Title cards appear for Sonic 3 & Knuckles zone themes only; the other games' cards are
  not yet in the API.
- Direct-connect is unencrypted, has no discovery and needs a reachable port.

The [full-version design and verification record](../../docs/architecture/designs/2026-10-07-sitar-hero-full-version.md)
and the [presentation polish record](../../docs/architecture/designs/2026-10-08-sitar-hero-presentation-polish.md)
hold the engineering evidence. Pad polling has an observation timestamp; captures
do not certify physical speaker or controller timing.

## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md) for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py sitar-hero --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Use `--s1`, `--s2`, or `--s3k` for the games this example consumes. Explicit paths create isolated development configuration and saves; no ROM is copied or linked. The creator kit exports this example with a portable POM and `tools/build_project.py`; it needs no engine source checkout. Only production sources/resources enter the validated mod jar. Read [recipient installation](../../docs/modding/installing-mods.md) before sharing.
