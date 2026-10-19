# Super Sonic Bros. — voice brief

The brief for recording or generating the game's voices: one announcer, plus three lines for
each of the seven fighters. Every line has a fixed id; deliver each as
`src/main/resources/audio/voice/<id>.ogg` and declare it in
`src/main/resources/audio/audio-manifest.yaml` (see [Delivery](#delivery)). The game will play
them by id with `SceneAudio.playSfx("<id>")` at the moments listed.

All performances must be original. Do not clone, imitate or reference any real voice actor,
including anyone who has voiced these characters in games, cartoons or films.

## Announcer

### Style prompt

> A booming, larger-than-life arena announcer for a 1990s-style fighting game. Deep, resonant
> adult male voice with a slight gravel, delivered with huge energy and drama, as if calling a
> championship bout to a packed stadium. Every line is short, punchy and shouted with
> confidence; stretch key vowels for drama ("Gooo!", "Gaaame!") and land hard on the final
> consonant. Clear, crisp diction so each word reads instantly over loud music and sound
> effects. Character names are announced like a wrestler's ring entrance: a rising build into a
> big finish. Neutral, broadly international English accent. No laughter, no breaths, no
> filler, no extra words beyond the script. Close-miked and dry with only a very short, tight
> arena slap (no long reverb tail), so the line ends cleanly.

### Lines

| Id | Line | When it plays | Max length | Direction |
| --- | --- | --- | --- | --- |
| `ann-title` | "Super Sonic Bros.!" | Title screen appears | 2.0 s | The game's name, grand and triumphant; "Bros." is the big finish |
| `ann-versus` | "Versus!" | Main menu: Versus chosen | 1.0 s | Punchy |
| `ann-classic` | "Classic mode!" | Main menu: Classic chosen | 1.4 s | Inviting, adventurous |
| `ann-choose-fighter` | "Choose your fighter!" | Character select opens | 1.6 s | Commanding |
| `ann-ready` | "Ready to fight!" | Character select: the READY banner appears | 1.4 s | Rising excitement |
| `ann-choose-stage` | "Choose your stage!" | Stage select opens | 1.6 s | Commanding |
| `ann-name-sonic` | "Sonic!" | A player picks Sonic; announcing Sonic as the winner | 1.0 s | Ring-entrance build |
| `ann-name-tails` | "Tails!" | As above, Tails | 1.0 s | Ring-entrance build |
| `ann-name-knuckles` | "Knuckles!" | As above, Knuckles | 1.0 s | Ring-entrance build, heavy |
| `ann-name-robotnik` | "Robotnik!" | As above, Robotnik | 1.2 s | Ring-entrance build, villainous weight |
| `ann-name-mecha` | "Mecha Sonic!" | As above, Mecha Sonic | 1.3 s | Ring-entrance build, ominous |
| `ann-name-eggrobo` | "Egg Robo!" | As above, Egg Robo | 1.1 s | Ring-entrance build |
| `ann-name-rhinobot` | "Rhinobot!" | As above, Rhinobot | 1.1 s | Ring-entrance build |
| `ann-random` | "Random!" | A player picks the random slot | 1.0 s | Playful |
| `ann-stage-aiz` | "Angel Island!" | Stage select: Angel Island chosen | 1.3 s | Grand |
| `ann-stage-hcz` | "Hydrocity!" | As above, Hydrocity | 1.3 s | Grand |
| `ann-stage-mgz` | "Marble Garden!" | As above, Marble Garden | 1.3 s | Grand |
| `ann-stage-cnz` | "Carnival Night!" | As above, Carnival Night | 1.3 s | Grand |
| `ann-stage-icz` | "IceCap!" | As above, IceCap | 1.3 s | Grand |
| `ann-stage-lbz` | "Launch Base!" | As above, Launch Base | 1.3 s | Grand |
| `ann-stage-mhz` | "Mushroom Hill!" | As above, Mushroom Hill | 1.3 s | Grand |
| `ann-stage-soz` | "Sandopolis!" | As above, Sandopolis | 1.3 s | Grand |
| `ann-stage-ssz` | "Sky Sanctuary!" | As above, Sky Sanctuary | 1.3 s | Grand |
| `ann-stage-dez` | "Death Egg!" | As above, Death Egg | 1.3 s | Grand, ominous |
| `ann-three` | "Three!" | Match countdown | 0.8 s | Sharp; the countdown steps are one second apart |
| `ann-two` | "Two!" | Match countdown | 0.8 s | Sharp, same intensity as "Three" |
| `ann-one` | "One!" | Match countdown | 0.8 s | Sharp, same intensity |
| `ann-go` | "Go!" | Match starts | 1.0 s | The biggest shout so far, stretched: "Gooo!" |
| `ann-sudden-death` | "Sudden death!" | A timed match ends in a tie | 1.5 s | Tense, dramatic |
| `ann-game` | "Game!" | Stock or stamina match decided | 1.0 s | Final, emphatic: "Gaaame!" |
| `ann-time` | "Time!" | Timed match runs out | 1.0 s | Final, emphatic |
| `ann-winner-is` | "The winner is..." | Results screen, before the winner's name | 1.6 s | Drawn-out suspense, trailing off |
| `ann-team-red` | "Red team!" | Results: red team wins (follows `ann-winner-is`) | 1.2 s | Triumphant |
| `ann-team-blue` | "Blue team!" | Results: blue team wins | 1.2 s | Triumphant |
| `ann-team-green` | "Green team!" | Results: green team wins | 1.2 s | Triumphant |
| `ann-no-contest` | "No contest!" | Results: a draw | 1.3 s | Flat, final |
| `ann-round-1` | "Round one!" | Classic: round card | 1.2 s | Building |
| `ann-round-2` | "Round two!" | Classic: round card | 1.2 s | Building |
| `ann-round-3` | "Round three!" | Classic: round card | 1.2 s | Building |
| `ann-round-4` | "Round four!" | Classic: round card | 1.2 s | Building |
| `ann-round-5` | "Round five!" | Classic: round card | 1.2 s | Building |
| `ann-final-round` | "Final round!" | Classic: last round card | 1.4 s | Maximum stakes |
| `ann-tag-team` | "Tag team!" | Classic: tag-team round card, after the round number | 1.1 s | Upbeat |
| `ann-handicap` | "Handicap match!" | Classic: two-on-one round card | 1.4 s | Ominous |
| `ann-vs` | "Versus!" | Classic: round card, between the two sides | 1.0 s | Punchy (may reuse `ann-versus`'s take with a harder hit) |
| `ann-continue` | "Continue?" | Classic: continue screen | 1.2 s | Questioning, a little urgent |
| `ann-game-over` | "Game over." | Classic: declined continue | 1.4 s | Low, final, no shout |
| `ann-congratulations` | "Congratulations!" | Classic: ladder cleared | 1.8 s | Warm, triumphant |

## Fighters

Each fighter has three lines:

- **select** — when a player picks them on the character select (also Classic setup);
- **intro** — as the match countdown begins (all fighters' intros play one after another, so
  keep them short);
- **win** — on the results screen, after the announcer names them.

Max lengths: select 2.0 s, intro 2.0 s, win 2.5 s.

### Sonic

> Style prompt: a cocky, upbeat teenage hero who lives for speed. Bright, youthful male voice,
> fast and breezy with a grin you can hear; playful confidence that never turns mean. Quick
> delivery with snappy rhythm, like he is already halfway out the door.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-sonic-select` | "Fastest thing alive, ready to roll!" | Bursting with energy |
| `voice-sonic-intro` | "Try to keep up!" | Taunting, relaxed |
| `voice-sonic-win` | "Too easy! Catch you later!" | Laughing it off, already leaving |

### Tails

> Style prompt: an eager, good-hearted young inventor, about eight years old. Light, bright
> child's voice full of enthusiasm and a little nervous excitement; sincere and optimistic,
> proud when he pulls something off. Clear and quick.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-tails-select` | "Okay! I'm ready to fly!" | Excited, a touch breathless |
| `voice-tails-intro` | "Let's see what these tails can do!" | Determined, psyching himself up |
| `voice-tails-win` | "I did it! Did you see that?!" | Overjoyed, surprised at himself |

### Knuckles

> Style prompt: a proud, hot-headed guardian with a short fuse and a big heart. Strong,
> slightly gruff young male voice, blunt and confident; few words, every one meant. Tough
> rather than villainous.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-knuckles-select` | "The guardian is here." | Firm, arms-crossed confidence |
| `voice-knuckles-intro` | "You picked the wrong island to fight on!" | Challenging, cracking his knuckles |
| `voice-knuckles-win` | "Hmph. Nobody gets past these fists." | Satisfied, gruff |

### Robotnik

> Style prompt: a bombastic, theatrical evil genius who adores the sound of his own voice.
> Big, rich, adult male voice with grand villain flourishes, gleeful arrogance and an
> exaggerated, almost operatic delivery; switches from smug purr to booming proclamation.
> Comic menace rather than horror.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-robotnik-select` | "Ahh, an excellent choice. The genius has arrived!" | Smug, then grand |
| `voice-robotnik-intro` | "Prepare to be crushed by superior engineering!" | Booming proclamation |
| `voice-robotnik-win` | "Ho ho ho! Another triumph for the Robotnik Empire!" | Gleeful villain laugh into the boast |

### Mecha Sonic

> Style prompt: a cold, menacing combat robot built as Sonic's rival. Flat, emotionless,
> synthesized male voice: clipped machine cadence, precise syllables, a low metallic resonance
> with a faint digital buzz. Never shouts; the threat is in the calm.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-mecha-select` | "Mecha Sonic. Online." | Systems booting up, slow and deliberate |
| `voice-mecha-intro` | "Target acquired. Commencing elimination." | Cold, even |
| `voice-mecha-win` | "Opponent neutralized. I am superior." | Detached certainty |

### Egg Robo

> Style prompt: an obedient robot foot-soldier from a cartoon villain's army. Tinny,
> slightly nasal synthesized voice with a cheerful, overeager military bark; a little comical,
> like it is reading from a manual. Short robotic pauses between phrases.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-eggrobo-select` | "Egg Robo, reporting for duty! Glory to Doctor Robotnik!" | Saluting, overeager |
| `voice-eggrobo-intro` | "Initiating combat protocol!" | Mechanical bark |
| `voice-eggrobo-win` | "Mission complete! Awaiting further orders!" | Proud, beeping cadence |

### Rhinobot

Rhinobot is a robot rhinoceros and does not speak: its lines are non-verbal vocalizations.

> Style prompt: a charging robot rhinoceros. Non-verbal only — no words. A blend of a
> revving small engine, a snorting animal and metal grinding: deep rumbles, sharp snorts,
> hissing steam and a trumpet-like mechanical bellow. Aggressive but cartoonish, not
> frightening.

| Id | Line | Direction |
| --- | --- | --- |
| `voice-rhinobot-select` | *(an engine revving up into two quick snorts and a short metallic bellow)* | Eager to charge |
| `voice-rhinobot-intro` | *(a low growling engine idle building into an angry snort and a hoof scrape)* | Squaring up |
| `voice-rhinobot-win` | *(a triumphant mechanical trumpeting bellow ending in a satisfied puff of steam)* | Victorious |

## Delivery

- **Format:** mono Ogg/Vorbis (quality 4 is plenty), 48 kHz or 44.1 kHz. The engine accepts
  WAV too, but Ogg keeps the jar small.
- **Levels:** peaks no higher than −1 dBFS; announcer around −14 LUFS, fighters around
  −16 LUFS, all lines consistent with each other.
- **Edits:** trim silence to under 50 ms before the first word and under 150 ms after the
  tail; no music, no ambience, no long reverb. Each clip must stay under its max length (and
  well under the engine's 30-second limit for a single clip).
- **Files:** `examples/super-sonic-bros/src/main/resources/audio/voice/<id>.ogg`, file name
  exactly the id.
- **Manifest:** `examples/super-sonic-bros/src/main/resources/audio/audio-manifest.yaml`, one
  `sfx` entry per clip (format: [audio manifest v1](../../docs/modding/formats/audio-manifest.md)):

```yaml
formatVersion: 1
tracks: []
sfx:
  - id: ann-go
    assetPath: audio/voice/ann-go.ogg
    gain: 1.0
  - id: voice-sonic-select
    assetPath: audio/voice/voice-sonic-select.ogg
    gain: 1.0
  # ...one entry for every id above
```

- **Check:** `python3 examples/super-sonic-bros/build.py` must still report
  `Validation passed: 0 findings`.

The ids above are the contract: the game's triggers use exactly these names, so keep them as
written even if a line's wording changes.
