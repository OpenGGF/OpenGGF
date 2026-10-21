# Author Stageplay from a terminal

`tools/stageplay/stageplay.py` exposes the directing framework without opening editor
menus. It compiles a shot-local screenplay into the same `.stageplay` document used
by the in-engine editor, OpenGGF Editor and mod registration. Java 25 is required;
movie capture additionally needs FFmpeg and the platform's headless GL prerequisites.

```bash
python3 tools/stageplay/stageplay.py --build help
python3 tools/stageplay/stageplay.py init --out /your/production/first.script.json
python3 tools/stageplay/stageplay.py catalog --game s2 --rom /your/existing-s2.gen --character tails --zone 0 --act 0
python3 tools/stageplay/stageplay.py compile --script /your/production/first.script.json --out /your/production/first.stageplay
python3 tools/stageplay/stageplay.py inspect --scene /your/production/first.stageplay --frame 120
python3 tools/stageplay/stageplay.py render --scene /your/production/first.stageplay --out /your/production/first.mkv --s2-rom /your/existing-s2.gen
```

Build once, then omit `--build` until engine sources change. It packages with tests
skipped; this is not a test result. `--jar PATH` selects a supplied packaged engine.
All command paths are relative to the caller. Output is one JSON `{ok,result}` or
`{ok:false,error}` object; engine diagnostics go to stderr. Exit 0 means success,
2 means invalid input or failure, and cancellation exits 130. Outputs are created
without replacing existing files. Choose a new output name for each take.

`validate --scene PATH` checks the complete structural contract without ROMs.
`inspect` includes cast, cues, changes, effects and optionally one sampled frame.
`timeline --scene PATH --out state.jsonl` streams every evaluated frame with its
seconds, active set, poses, camera, transition and cues. `--every N` chooses a
coarser inspection interval; it does not change the movie cadence. This is authored
directing state, not native simulation telemetry. Keep bulk timelines beside the movie.
`catalog` verifies the requested ROM identity and reports native registry metadata,
character animation IDs, delays and mapping frames. Add both `--zone` and `--act`
to decode one detached set and report bounds. A registry entry alone does not certify
set availability. Rendering eagerly validates resources before encoding.

The render command accepts `--s1-rom`, `--s2-rom`, `--s3k-rom`, optional `--rom` and
`--game` for the boot ROM, `--scale` (1..8), `--ffmpeg`, and `--staging-dir` (empty or
new owned staging directory). It produces lossless FFV1/FLAC MKV at the document's
60 Hz cadence using offline audio. Output dimensions are the scene dimensions multiplied by `--scale`, using integer
nearest-neighbour scaling (the default is 1).
Linux uses surfaceless EGL with desktop display variables removed. Other platforms
use the capture host's hidden native context; macOS uses Java's first thread.

## Screenplay format

The screenplay format has its own `version:1`; the generated scene uses Stageplay
version 3. JSON numbers retain their types: numeric strings, duplicate fields,
unknown fields and trailing input are rejected. Maximum input size is 8 MiB.

```json
{
  "version": 1,
  "name": "A change of scenery",
  "width": 400,
  "height": 224,
  "cast": [{"id":"tails","name":"Tails","gameId":"s2","character":"tails"}],
  "shots": [
    {
      "id":"hello", "durationFrames":240,
      "set":{"gameId":"s2","zone":0,"act":0},
      "camera":[{"frame":0,"x":0,"y":500},{"frame":239,"x":40,"y":500,"zoom":1}],
      "actors":{"tails":[{"frame":0,"x":80,"y":655,"animation":0},
                         {"frame":50,"x":140,"y":648,"animation":5}]},
      "dialogue":[{"startFrame":60,"endFrame":220,"speakerActor":"tails",
                   "text":"A small change of scenery..."}]
    },
    {
      "id":"elsewhere", "durationFrames":240,
      "set":{"gameId":"s1","zone":0,"act":0},
      "transition":{"kind":"FADE_WHITE","durationFrames":30},
      "camera":[{"frame":0,"x":0,"y":800}],
      "actors":{"tails":[{"frame":0,"x":140,"y":944,"animation":5}]},
      "cameraEffects":[{"startFrame":20,"endFrame":50,"amplitudeX":3,"amplitudeY":2,"frequency":8,"seed":7}],
      "dialogue":[{"startFrame":60,"endFrame":220,"speakerActor":"tails",
                   "text":"...a very small change."}]
    }
  ]
}
```

The example illustrates the format; inspect the chosen set and framing before
calling it a finished shot. Width defaults to 400 and height to 224. There may be
32 cast members and 129 shots, with a combined maximum of 36,000 frames. IDs are
unique, bounded scene identifiers. Namespaced event IDs include their shot prefix,
so use short shot/event names.

Each shot requires `id`, positive `durationFrames`, `set` and `camera`. Sets use
`gameId`, zero-based `zone` and `act`; optional `romSha1` pins exact lowercase SHA-1,
and `levelProject` references a relative `.ogfproject`. Both default to empty strings.
Each camera and present actor track starts at local frame zero, with strictly ascending
keys before the shot's end. Missing actors are hidden, with a boundary key inserted.
A shot is an explicit cut even when it reuses the preceding set.

| Track | Fields and defaults |
| --- | --- |
| Camera key | `frame,x,y`; `zoom:1`, `interpolation:"LINEAR"` |
| Actor key | `frame`; `x:0,y:0,animation:0,pose:-1,flipX:false,visible:true,scale:1,interpolation:"LINEAR"` |
| Dialogue | `startFrame,endFrame,text` and `speakerActor` or `speakerName`; optional profiles, timing and panel fields below |
| Caption | `startFrame,endFrame,text`; `x:12,y:12,argb:-1`, optional `id` |
| Audio | `frame,kind`; `gameId` defaults to the shot's game, `soundId:0,asset:"",gain:1`, optional `id` |
| Camera effect | `startFrame,endFrame,amplitudeX,amplitudeY,frequency`; `seed:0`, optional `id` |
| Incoming transition | `kind` (`CUT`, `FADE_BLACK`, `FADE_WHITE`, `WIPE_LEFT`, `WIPE_RIGHT`), `durationFrames` |

Interpolation is `HOLD`, `LINEAR` or `SMOOTH`. Actor `pose:-1` selects its animation;
nonnegative values hold a mapping frame. Positions use sprite origins. Captions are
screen-space pixels. Audio kinds are `ROM_MUSIC`, `ROM_SFX`, `VOICE`, `MUSIC_STOP`;
voices reference bounded WAV/Ogg files beside/below the scene. Relative resource scenes
must compile beside the screenplay to preserve their root.

An absent transition means `CUT` with duration zero. Non-cut transitions last 2..600
frames and straddle the incoming shot's start: the set changes at the fully covered
midpoint. The first shot cannot have an incoming transition. Leave sufficient room
on both sides; transitions cannot overlap. Events use half-open local intervals and
must stay within their shot. Shake is deterministic sine motion in stage pixels at
1/60-second steps; `frequency` is Hz, greater than zero and at most 30.

The compiler flattens offsets, adds all boundary anchors and prevents interpolation
across them. Both editors can subsequently edit the canonical scene. Exporting it
back to a shot-local screenplay is not currently supported; keep the screenplay as
the source when you want to revise it programmatically.


## Dialogue profiles and decorations

Use `dialogue` for speech and `captions` for titles or ordinary overlays. A dialogue
cue names its `speakerActor`; its displayed name comes from that cast member.
`speakerName` overrides the label or identifies an offscreen speaker when the actor
reference is empty. The name appears immediately, independently of text reveal.
An optional pointer follows an onstage speaker through camera motion.

Define up to 64 reusable `dialogueTypers` and 64 `dialogueSkins` at screenplay root:

```json
{
  "dialogueTypers": [{"id":"excited","framesPerGrapheme":1,"punctuationPauseFrames":4,"spacePauseFrames":0}],
  "dialogueSkins": [{"id":"blue","nameArgb":-9060353,"borderArgb":-9060353,
    "decorationAsset":"decor/blue.png","decorationBorder":5,"padding":8}]
}
```

These fields join the normal screenplay root; this fragment is not a complete film.
A shot's line selects them with `typerId:"excited"` and `skinId:"blue"`. IDs are local
to the document, so two installed mods do not compete for a global style name.
Unknown references fail validation. Empty IDs use the built-in profiles.

| Definition | Fields and compiler defaults |
| --- | --- |
| Typer | `id`; `framesPerGrapheme:2` (0–30), `punctuationPauseFrames:6` and `spacePauseFrames:0` (0–120) |
| Skin | `id`; `textArgb:-593700`, `nameArgb:-11172`, `panelArgb:-15722457`, `borderArgb:-7826009`, `padding:6`, `decorationAsset:""`, `decorationBorder:0`, `pointer:true` |
| Dialogue cue | `startFrame,endFrame,text`; `speakerActor:""`, `speakerName:""`, `skinId:""`, `typerId:""`, `x:12,y:12,width:filmWidth-24,height:min(70,filmHeight-24)`, `portraitAsset:""`, `revealFrames:[]`, optional `id` |

Colour values are signed 32-bit ARGB integers. The canonical document requires all
fields; the screenplay compiler supplies these defaults. Typing is deterministic at
60 Hz: the first grapheme appears at elapsed frame zero, followed by the selected
cadence and pauses. A rate of zero displays the whole line immediately. `revealFrames`
can override the profile with one nondecreasing elapsed-frame offset per Unicode
grapheme. This supports manually timed bursts and pauses without executable scripts.
All text must finish before the line ends. Scrubbing and drawing emit no sounds;
use explicit audio cues for authored sound effects.

The complete text wraps before revealing, so words stay in place. The panel must
fit inside the film and contain the speaker name and all wrapped rows; overflow is
an error. The existing engine font and its glyph coverage are unchanged. These are
portable reveal profiles, not arbitrary glyph-animation callbacks or custom fonts.

A decoration is a creator PNG interpreted as a nine-slice panel. `decorationBorder`
sets equal edge/corner insets in source pixels; it must leave a nonempty middle and
fit the panel. An optional portrait PNG fits a 32×32 region beside the text. PNGs
are limited to 1024×1024 each and four million decoded pixels across the scene.
Keep PNGs beside or beneath the scene, compile beside the screenplay, and move the
whole package together. In mods, images use the registering owner's bounded asset
root and typed loads, sharing the existing encoded asset budget with voices/sets.
The same definitions work in both editors and offline rendering without mod code.

## Pixel stability and performance

Camera positions interpolate; camera zoom holds its value until the next key.
The current renderer does not animate zoom. Use held integer scales, cuts and pans
for framing; a future smooth world-compositing pass would be needed for animated
fractional zoom. A larger export `--scale` cannot repair aliasing already present
in the authored frame. Stage entrances, exits, turns and listener reactions, then
review the entire movie in motion at its intended output size.
