---
name: promo-direction
description: Creative direction for OpenGGF promos, trailers, mod showcases and staged story films. Choose gameplay or Stageplay sources, then direct performance, typography, composition, motion, sound and pacing. Use with gameplay-highlights for provenance, verification and required archiving.
---

# Promo direction

A promo sells one idea through visible action, deliberate pacing and type that
belongs to the game. Choose the source workflow from what the film must show:

- For staged stories, animatics and choreographed promotional scenes, use
  [stageplay-authoring](../stageplay-authoring/SKILL.md) and its shared offline
  `tools/stageplay/stageplay.py` compiler, timeline inspection and renderer.
- For ordinary gameplay, continuous routes and gameplay feature captures, use
  [gameplay-capture](../gameplay-capture/SKILL.md), with
  [bk2-input-authoring](../bk2-input-authoring/SKILL.md) when inputs need scripting.
  Use [trace-capture](../trace-capture/SKILL.md) for an existing trace replay.
- A hybrid can combine these, with provenance identifying each source. Stageplay
  `timeline` samples describe authored intent; physical actors remain `UNRESOLVED`.
  `simulate` with `TERRAIN_ONLY` measures native actor physics on detached static
  terrain, without placed-object AI, rings or zone events. With `LIVE`, it measures
  a shared native world: actors, placed objects, rings and observed native events.
  LIVE evidence supports the interactions actually observed from authored shot
  setups; it does not establish a continuous cold route or ROM parity.

Follow [gameplay-highlights](../gameplay-highlights/SKILL.md) for honest labelling,
verification and the **required OpenGGF-Media archive** in every promo workflow.

Use [`tools/media/promo_kit.py`](../../../tools/media/promo_kit.py) when the edit
needs a beat-grid compositor. A staged story can remain a direct Stageplay render;
it does not need an added montage. The compositor's moves are catalogued in
[references/moves.md](references/moves.md); they are not Stageplay engine features.
What the archive teaches, including what the user rejected, is in
[references/lessons.md](references/lessons.md). Read the lessons once before you
storyboard.

## What flat looks like (do not ship it)

The user has rejected or complained about every item on this list:

- A system sans (DejaVu, Noto, Arial) at one weight, placed with ffmpeg
  `drawtext` at a fixed `x=40`, static, white or grey.
- Captions centred on a black box, with no stroke and no motion, appearing on
  every shot.
- Hard `concat` or uniform 0.35 s crossfades between every shot, with no beat
  grid.
- Footage letterboxed in the middle of a solid pad for the whole film.
- A feature inventory, such as "a slide deck" of menus, option pages, title
  screens, held UI frames or captions that promise an effect the picture does
  not show.
- Frantic cutting. The Mutator Lab original averaged about 1.2 s per shot and
  put 16 cuts in its last 20 s. It was rejected for "rapid cuts".
- Cluttered audio: every clip's own music spliced together, or a silent
  count-in.

Being bold does not mean being busy. The two most common rejections were
"very rushed" (Putt-Putt Paradise v5) and "rapid cuts / cluttered audio"
(Mutator Lab).

Being restrained does not mean being safe, either. A coached plan that avoided
every rejection above still came out flat:

- The footage was inset in a frame over a blurred copy of itself for the whole
  film. Full-bleed `game_view` is the default canvas. An inset frame is a
  layout for a particular beat.
- The title drop had no flash, no stinger and no camera move, and the
  plan said to "avoid zoom punches".
- Editorial type re-announced a zone that the game's own title card
  announces better.
- The opening showed establishing scenery and held the hero shot back until 13 s.
- Shots named "keep the momentum" and "leave room for discovery" sold nothing.
- Requirements and caveats (Java version, ROM revision) crowded the end card.

For a gameplay showcase, the treatments in this skill are the **defaults**:
a cold open from the hero footage, a title drop with flash, stinger and slam,
ROM title-card headers, banded wipes at chapter changes, full-bleed footage and
splits for comparisons. If your plan drops one, the brief says why in one line.

**Long takes need accompaniment.** The next coached draft fixed every point above
and was rejected as "very... boring... very lazy with long stretches of level
showcased without any accompaniment". It held 14 s of a new zone with nothing
on it. The fix for "rushed" is a longer take with something happening on it,
never bare level. About every bar or two, give the viewer something new. In order
of preference:

1. A punch-in, stinger or flash synced to an in-game event: a spring, a ring
   burst, a badnik pop or a landing. Find the frame in the state CSV.
2. A counter, callout or arrow tied to what is on screen: rings collected, crests
   passed, or "NEW" on a feature as it enters.
3. A change of layout: a split opening, a picture-in-picture or a map strip.
4. Cutting the take down to its best moments. A take that is still dull with
   accompaniment is too long.

A caption is the weakest accompaniment. Use at most one per chapter, and only
when it moves the pitch on ("Same hill." → "New route."). The next draft answered
the bare-stretch warning with "TAKE THE HIGH ROAD", "FOLLOW THE GOLD",
"ONE MORE LEAP" and "YOUR TURN" over the same 13 s take. The user agreed it was
"minimal effort... to achieve the goals". `promo_kit.py check` flags full-screen
gameplay over 4 s with no cut, graphic overlay, stinger, punch or camera move
(captions don't count), and more than three captions over gameplay in 20 s.

**A small mod needs an idea, not just footage.** When the change is modest (a
remixed act, a new tileset), the level alone won't sell it. Sell how it was made,
and make the viewer play along. For example, a remix built from strips can show
the strips flying into place on a map before the run. A generated zone can show
its tiles drawing themselves across the screen. A "spot the difference" beat asks
the viewer to look. Find the cleverest true thing about the mod and give it a
beat that is big enough to remember. A 16-pixel tile in a corner box is not that beat.

**A passing check is a floor, not the goal.** An empty warning list proves only
that the known failures are absent. Drafts that passed every check were still
rejected as "boring" and "minimal effort". The cheapest move that silences a
warning is usually the wrong one, and the user can tell. Work toward the viewer:

- For each weak section, sketch two or three treatments, at least one of them
  ambitious, and build the one a viewer would remember. Note the rejected ones in
  one line each.
- Before stopping, do a **viewer pass**: a table with one row for every 4 s of
  output, saying what is new on screen and why a viewer keeps watching. A row
  whose answer is "a caption" or "the level continues" is the next thing to fix.
- You cannot hear the film. Playing it in a muted browser is not a listening pass;
  say plainly that sound was checked by measurement only. Measure what can be measured:
  `check`'s stinger levels and panning, `dac-enables` for S3K music, and each synced
  sound's frame against its event frame. In the A/B, the user heard every audio fault
  that both agents' passes missed.

These are recorded problems with particular promo drafts. Preserve the preference
for crafted type, readable action and deliberate rhythm; do not turn it into a
ban on a stable dialogue panel, a purposeful reaction hold or a quiet story beat.

## Workflow

### 1. Choose a relevant production reference

Read the edit code of
[Slay the Robotnik](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/slay-the-robotnik/sources/promo):
its `tools/promo.py` `build()` is a whole 86 s promo in about 140 lines. For a
music-led showcase it is a useful model. Choose another reference when its
treatment serves the brief:

- Putt-Putt Paradise `promo-opus-20261006/final-verified/render_promo.py`,
  for its Sonic 2 title-card captions and cup iris.
- Flappy Tails `promo_plan.py`, for trailer slams.
- Starpost Valley `promo-polish/edit.py`, for its diagonal season gallery.

The finished videos are external. Contact sheets, chapter lists and edit code
help study composition; they do not establish motion quality. State which
treatment you are drawing on. A narrative dialogue film need not copy a showcase's
grid, title drop or chapter structure.

### 2. Write the brief at the top of the edit file

The plan is this brief, in the docstring of `edit.py` from `promo_kit.py template`,
plus the beat sheet. Do not write separate essays, storyboard sites or
rejected-alternative surveys. Give each choice its reason in one clause.

Write one line each:

- **Audience.** For example: general Sonic fans, or modders.
- **Form and evidence.** Gameplay showcase, staged story or hybrid; identify
  which claims require native gameplay footage.
- **Promise.** One sentence. For example: "a Sonic deck-building roguelike,
  read from your own ROM".
- **Selling points or story beats.** For a showcase, choose a few things you can *show in motion*.
  "Configurable" is shown by toggling, then by the result. It is never shown
  by an options page. For a story, describe the action, speaking turns and reactions
  that make the premise clear.
  The ban on a feature inventory is about static menus, not about leaving the features
  out. List what makes this feature different from the stock game, and give each
  difference a moving proof unless the brief excludes it. One angle can carry them all.
  In the 2026-10-23 Time Attack A/B, one film chose "identity beside you" and dropped
  the no-lives retry, the vote and the room size as "inventory". What remained was a
  Green Hill run with ghosts, which any Sonic race could show. The other film followed
  one player from naming to the rematch and proved each difference on the way.
- **Name the relationship you are showing.** When the novelty is a relationship ("the
  lobby is a level", "your colour in every game"), show it in one continuous shot or
  one split, and say it once. A menu that disappears into play does not tell a
  first-time viewer that they are now in the lobby.
- **Proof.** For each selling point, name what the viewer will *see* that
  proves it. A claim that something changed (a remix, a fix, a new version,
  "now with") is a comparison, so plan the stock version beside the new one in a
  split. Shown alone, a remixed level just looks like the level.
- **Hero moment.** The single shot people will remember. Plan the climax
  around it, and cut a one- or two-second fragment of it into the cold open.
- **Sound.** Music structure and, where relevant, BPM/first beat; dialogue,
  effects and intended pauses. A jingle or separate end-card track is a choice,
  not a requirement.
- **Palette.** Three or four colours *sampled from the game itself*: its title
  card, logo or HUD.
- **Title face.** Prefer the game's or mod's own letterforms: a ROM title-card
  font, or a mod `art/font.txt` drawn with `BitmapFont`. A mod without a font
  still has the base game's letters. The simplest native type is the game's
  own presentation as it ships, filmed: a title card, a results tally or a menu reveal in one
  take into play. Do not cover a native card with an editorial copy of it.
  Use a heavy condensed TTF only for lines the game cannot supply.
- **End card.** Logo, one line, URL, status line, and the disclaimer "Fan-made
  and free. Not affiliated with SEGA. Requires your own ROM." Nothing else.
  Build requirements and ROM revisions belong in the linked README.

### 3. Storyboard to the film's rhythm

For a music-led showcase, choose the music first and build a beat sheet:

1. Render the bed through OpenGGF's own SMPS driver from the ROM with
   `com.openggf.tools.audio.FmSfxRenderTool --game s2 --rom <rom> --music 91
   --rate 48000 --max-seconds 80 --out <dir> --paced`. `--sfx <id>` renders a
   stinger the same way. `--paced` plays it as the game does and writes
   `<game>-<kind>-<id>-paced.wav`. Without it, S3K music has no drums: the
   default render never enables the DAC, and the tool reports `dac-enables=0`.
   Never use unlicensed external music. The chip output starts on a DC offset,
   so high-pass it before checking the first onset.
2. Run `python3 tools/media/promo_kit.py beats bed.wav` to get the BPM, beat
   phase and section changes. Confirm the phase by ear or against a known
   onset.
3. Write a beat sheet before capturing: chapter, beats, layout, type, sound
   cue, and **what the shot proves**. A shot whose purpose is "keep the
   momentum", "breathe" or "leave room" is padding. Cut it, or spend its bars on
   a comparison or the hero. Put chapter changes, header wipes and the title drop on **section
   changes**. Put ordinary cuts on bar lines (every four beats) or phrase
   lines (every eight). An effect's arrival or a contact should land *on* a
   beat where that preserves a readable complete action.

For a staged dialogue film, block the performance and reading time first. Use
music and effects to support turns, reactions and changes of scene; do not shorten
a line or force every cut onto the beat. Record shot-local frame timings in the
screenplay and inspect the compiled timeline with Stageplay's shared offline tool.

An example 60–90 s showcase shape at 120–175 BPM, not a required narrative formula:

| Part | Length | Purpose |
|---|---|---|
| Cold open | ≤6 s | The strongest motion first, then a one-line promise. No logo-first opening, no silent ratings card. |
| Title drop | 4–6 s | Lands on a section downbeat, with a flash, a stinger and a slam-out camera. |
| Chapters ×3–6 | 6–12 s each | Header, then one continuous take of a complete action. Several short pieces share one split or grid screen instead of being cut together. |
| Numbers (optional) | 3–5 s | Two to four counters, one beat apart. |
| Engine/API pitch (optional) | 5–8 s | A wall of vertical slices behind a three-word stack, then a code panel beside a live view. |
| Climax | 10–20 s | The hero moment, in one take into the victory, under a jingle. |
| End card | 5–7 s | Over blurred gameplay. Fade to black. |

For a tutorial/promo, teach a small set of choices through one coherent example.
Give the complete scene room to establish its premise, then connect each directing
choice to a visible result in a continuous annotated pass when that serves the
lesson. Avoid restarting the same action for every concept. Use real values and a
measured single-setting comparison where useful; distinguish explanatory graphics
from actual editor UI. Give viewers an editable source and a clear first step.
Read the Stageplay first-creator lesson before choosing the teaching sequence.

### 4. Shot list and capture

- For a gameplay feature showcase, the archive's ≥80% moving gameplay and
  ≤15% menus are useful defaults: menus appear as the cause of something shown.
  A staged story has no gameplay quota. Its action and dialogue must carry the
  story, and its source remains labelled as authored performance.
- Let an action and its consequence read before cutting. Reaction shots can
  complete that exchange; every shot need not repeat the whole approach.
- Prefer one take across a natural chain over cutting it up. Good chains are
  menu → title card → level, or map → node → fight → victory.
- Preserve lossless source frames where the capture workflow supports them.
  `GameplayCaptureTool --audio` records music and SFX as one mix, and there is
  no SFX-only stem. Under an editorial bed, capture silently and add ROM
  stingers rendered with `FmSfxRenderTool --sfx <id> --paced`.
  Stageplay exports combine authored cues with native SFX in LIVE shots; avoid mixing that soundtrack
  with a second unplanned bed in the compositor.
- Gameplay start positions must be real for the act; disclose debug jumps and
  positioned starts. Stageplay shots have authored spawns and cuts under either
  `TERRAIN_ONLY` or `LIVE`; retain the screenplay, canonical scene, per-shot world
  policy and labelled timeline/simulation JSONL. Normal gameplay CSV records a
  gameplay run with its actual input and entry method. Do not imply uninterrupted
  gameplay from staged native interactions or positioned excerpts.
- Make a contact sheet of each clip, for example Slay's `sheet.py` or
  `strips.py`, and pick in-points by frame number.
- **Frame the claim.** If it is a count, a shape or a length (three
  corkscrews, a longer route), the frame has to be able to show it. When only one
  hump fits on screen, the viewer can't count to three. `--width 800` shows
  twice the native view, and `stacked_split` compares horizontal routes without
  cropping them to slices.
- **Check the hero's pose, not just its path.** The CSV proves the route, and the
  frames have to show the character at its best. For example, a spindash
  carried through a corkscrew rolls as a ball and hides the corkscrew tumble.
  Build the speed so the character runs through, for instance by landing from a
  jump, and check the CSV's roll column across the feature.
- **Film presentation as it ships.** A native card or screen that needs a
  non-default preference, a debug option or an engine repair is not what players
  see. Use editorial type in the game's letterforms, and record the gap.
  Don't turn a promo into engine work.

For Stageplay blocking, give characters purposeful entrances, approaches, turns,
gestures, reactions and exits as the scene needs. Prefer physical puppets for player
movement: destinations/urgency and jump button holds drive native acceleration,
terrain collision, animation and landing. Do not draw a fake jump arc with position
keys. Inspect `simulate` JSONL for actual arrival/blocked state, then review feet,
facing and accessories in motion. Poser mode is for deliberate nonphysical staging;
there is no universal pathfinder. `TERRAIN_ONLY` omits placed-object AI, rings and
zone events; `LIVE` runs the shared native world through production owners. Author
routes and bounded waits, then check native world telemetry for support, pickups,
hurt and abilities. A listening character should visibly react where
the line calls for it; an entire idle cast weakens a story about their interaction.

Identify the speaker when the line first appears. Prefer the dialogue track's
actor or named offscreen speaker, readable nameplate and optional pointer,
supported by framing and facing. Color alone is insufficient, especially when
roles share a sprite. Use reusable typers for reveal cadence and pauses and skins
for panel appearance/decorations; keep captions for titles and overlays. Leave
time after reveal to read and react, keep lines clear of transition cover, and
review the supported features of the build used to render. See stageplay-authoring
for the schema, resource bounds and commands rather than duplicating them here.

### 5. Add editorial composition where it serves the film

For a `promo_kit` edit:

```bash
python3 tools/media/promo_kit.py fonts                 # every role must say ok, not FALLBACK
python3 tools/media/promo_kit.py fetch-fonts /task/fonts && export PROMO_FONT_DIRS=/task/fonts
python3 tools/media/promo_kit.py template /task/promo/edit.py
python3 tools/media/promo_kit.py demo /task/kit-demo   # shows every move on synthetic footage
```

`promo_kit` needs numpy and Pillow. If the system Python lacks them, use a venv
under the checkout's `target/`. Keep fonts in the task directory, never in a repository. `DejaVu` in the
`fonts` report means the result will look flat. Fix the fonts before
composing.

Apply the relevant screen grammar in `build(e)`. Iterate composition with `sheet`
and `frames`, then render short moving previews for camera, animation, dialogue
and transition timing. Contact sheets cannot reveal shimmer or an idle performance.

### 6. Review like an editor, then verify like an engineer

Do the steps in this order:

1. For a compositor edit, `promo_kit.py sheet edit.py 30` makes a five-column
   sheet, one frame every half second. For Stageplay, inspect authored timeline
   samples, policy-specific simulation JSONL and source frames at the action,
   speaking and transition boundaries. LIVE claims need `sample.world` evidence.
2. Render transition midpoints and full layouts, then watch their complete motion
   at the intended output size and cadence. Include camera travel, character and
   accessory animation, entrances/exits, dialogue reveal and listener reactions.
3. Read the compositor's `pacing:` line when used. Ten cuts in 20 s or a mean
   shot under 2 s flags a potentially rushed showcase; dialogue reading and
   performance timing matter more than fitting a numerical target.
4. Apply the **editorial review** below to the chosen form.
5. Render. Then run the measurements from gameplay-highlights: `ffprobe`, a
   full decode, chapters, true peak *after* AAC encode, and a full 1×
   playback.
6. Archive in OpenGGF-Media as gameplay-highlights requires: applicable edit file,
   brief, beat/shot sheet, capture scripts and chapters, plus Stageplay screenplay,
   canonical scene and labelled authored/policy-specific simulation JSONL when used. A direct Stageplay render does
   not waive the promo archive requirement.

## Screen grammar

### Promotional typography: game-native, outlined and deliberately animated

These tiers describe editorial titles and sales copy. Dialogue names, dialogue
body text and accessibility subtitles need stable, readable placement; a styled
panel is appropriate and does not need to fly on and off with each speaking turn.

| Tier | Role and size at 1080p | Treatment |
|---|---|---|
| Display | `title`, 130–190 px | White or yellow, stroke 8–9, hard shadow 9–10. Words pop in one beat apart ("NOW, / SOMETHING / NEW."). |
| Header | `title` 150 px + `sub` 50 px | Title-card header: the banner drops from the top while the title slides in from the right, and the subtitle follows four frames later. Both leave at double speed. |
| Label | `title_it` 40–50 px | On yellow pills under split slices, or beneath grid cells. |
| Line | `title_it`/`body_it` 44–74 px | A promise or explanation at the top or bottom, on a shade gradient, entering upward. |

- At most one idea on screen at a time, in ≤8 words. A caption is a story
  beat ("One hit…" → "…every ring comes out"), not a label for what is
  plainly visible.
- Promotional text enters and leaves. Entrances take 10–16 frames with an
  ease-out or overshoot. Exits take 8 frames and are faster than entrances.
- For promotional overlays, use stroke plus a hard shadow over gameplay. Use a shade
  gradient (`Shade`, header shades) when a full line sits over busy art.
- Use a wide-tracked small kicker (`tracking=8–12`) sparingly, above a
  display line.
- Keep editorial graphics off the native HUD. A header tab cutting through
  `TIME`, or a split label beside two stacked HUDs, reads as an accident. Place
  the labels on the side away from the HUD, or crop the HUD out deliberately.
- **The film's voice must not look like the game's UI.** Captions, headers, counters and labels
  that the film says to the viewer need a treatment the game never shows during play. A Time
  Attack draft set every caption in the feature's own name font on the HUD's dark plates, and the
  user could not tell "if the promo is talking to me, or the game displays that text in normal
  play". The fix used the display face with stroke and hard shadow for lines and pills for labels,
  and kept the game's font only for player names and captured footage. A ROM title-card font is
  still a good title face: it is the game's presentation type, not its in-play UI.
- **Native UI is already type.** Multiplayer footage carries a HUD, a room panel and a
  nameplate on every racer. Count them before adding anything. One A/B film opened with
  its tagline wedged into the `TIME`/`RINGS` corner, beside the room panel, six
  nameplates and a disclosure strip: five layers of text in the first second. Over
  busy native UI, add one editorial element at most, in the region the game leaves
  empty, or cut to a shot that leaves room.
- **Crop at UI boundaries.** A crop keeps each native UI element whole or removes it
  whole. The same film's close crops cut `TIME` to `ME` and sliced the room panel's
  columns mid-word on almost every race shot. A title slab over gameplay must hide the
  HUD completely, not leave fragments of it showing above and below.
- **Labels must read at a glance.** A label you add under gameplay needs at least about
  50 px of cap height at 1080p, white or near-white text on a dark plate, and the
  player's colour as a bar rather than as the text colour. Dark blue and violet names
  on a dark plate were "hard to see". Bring labels in once their subjects have settled,
  and keep them from touching each other or the game's own text.
- A native bitmap font shows its construction at display scale. Sonic 2's
  title-card diagonals are split strokes, which can read as "E/MERALD" at 9×.
  Judge every word at final size, and choose the scale or the word accordingly.
- **Give a native font the game's own treatment.** The stroke-and-shadow row
  in the table above is for TTF type. A coached draft reduced Sonic 2's
  title-card glyphs to one colour, then added a navy outline and a red drop
  a third of a game pixel thick. The user called its "use of palettes with the
  text... horrible, particularly around the drop shadow". Instead:
  - Keep the glyph's palette indices when you extract it ('#' face, other
    characters for the extra colours, passed as `shades=`).
  - Sample the face, edge and shadow colours, and the shadow's offset, from a
    native frame of the game's own card.
  - Let `BitmapFont.render` draw every effect in whole glyph pixels.
  - Add an outline only if the game's type has one.
  - A native font comes with its native ground. Sonic 2's card letters are
    drawn to sit on the card's solid blue band, and over grass and clouds they
    lose their shape. Put display type on that band (with the red tab and yellow
    strip when they help) rather than straight over busy gameplay.
- Hero logos are redrawn from the game's or mod's own font at integer scale,
  with the game's own treatment and an optional sweeping shine. Banners come
  from the ROM's own title-card art, dumped by tests, never from the disassembly.

### Composition: show several things at once instead of cutting fast

Use these layouts for comparison or breadth. A dialogue scene can stay full-screen
to preserve eyelines, speaker identity and the timing of a reaction.

- **Slanted split (2–3 slices).** For comparisons and "all three games". The
  dividers lean like title-card speed lines (slant ≈0.22), each slice drops in
  on its beat, and a pill label names each slice. A slice shows only the middle
  of its pane, so check that the subject is inside it in a still of every pane.
  `game_view` clamps at the frame edge: a hero standing near the level's left
  boundary (x≈80) fell outside the S1 slice, and the user found Sonic "off screen
  for Sonic 1". Pad the frame horizontally and centre on the subject.
- **Grid (2×2, 3×1, 4×2).** For "many at once": bosses, events, rooms. Cells
  pop in one beat apart in reading order, on moving stripes, with a stinger
  on each pop. A cell must stay readable: at least about 2.3× native scale.
- **Vertical slice wall.** Three dimmed strips of different clips drifting
  slowly behind a three-word stack ("EVERY SPRITE. / EVERY SOUND. / EVERY
  STAGE.").
- **Code + live view.** A typed code panel on the left and a framed game view
  on the right: the Mod API beat.
- **Full screen.** For the hero moment and every effect's *first* appearance.
  A split is never used for an effect the viewer has not yet seen
  full-screen.

### Direction: keep one consistent screen logic

- Sweeps travel left → right (wipes, header exits). Banners come from the top
  and titles come from the right. Choose one direction for the film and keep
  it.
- A push transition travels the way the action moves. If Sonic runs right,
  the next shot pushes in from the right, using `tdir="left"`.
- Divider lean, stripe angle and wipe slant agree with each other.
- In Stageplay `TERRAIN_ONLY`, camera x/y can pan while zoom holds until its next
  key. Author stable integer framing and use cuts to change it. Imported static
  fractional values remain compatible. `LIVE` uses fixed zoom 1 with authored pans
  or actor/group follow; use export scale for larger output. Do not animate
  fractional nearest-neighbour source zoom.
- The compositor's `cam_move`, `cam_slam` and punch zooms are editorial effects
  on captured frames. `game_view` uses an integer nearest-neighbour enlargement
  followed by a BOX area filter, so its fractional framing is filtered, not
  pixel-exact integer-only scaling. Review the whole move for blur or shimmer;
  a catalogued effect is not proof that a particular clip looks good.
- Keep aspect ratio intact. Final frame upscaling or filtering cannot repair
  stepping already baked into source sprites/scenery by changing nearest-neighbour
  zoom. Re-render stable source framing, or undertake a separately reviewed smooth
  rendering implementation before relying on animated zoom.

### Transitions are punctuation

The following are compositor choices. Stageplay's set transitions are cuts,
black/white fades and left/right wipes; an iris, whip or branded banded wipe below
does not imply an equivalent Stageplay setting.

| Move | Use for | Budget |
|---|---|---|
| Hard cut on a beat | Within a chapter | Default |
| `wipe` (blue/yellow/red bands) | Chapter changes only | About one per chapter. Slay used 15 transitions in 86 s. |
| `iris` (gold ring) | A reveal: "something new", "make your own" | 1–2 per film |
| `Flash` | Title drop, boss hit, a slam card | On impacts only |
| `push`/`whip` | Travel that continues on-screen motion | Occasional |
| `slash`/`shutter` | An energetic change of scene inside a montage | Occasional |
| `xfade` | Into the end card, or calm galleries | Sparingly |

Never pick a different transition per cut for variety. Repetition of one
signature move is what makes it a brand.

### Pacing

- For a music-led showcase, aim around 3–5 s per shot, watch the 10-cuts-in-20-s
  warning and give the eye a rest after a busy passage. A dialogue take can be
  longer to complete the exchange; do not force it into a grid or cut quota.
- Give the opening an intentional sound and visual cue. Remove accidental dead
  air and frozen padding. A deliberate silence, held pose or listening pause
  can serve a story when performance and context make its purpose clear.
- Cut loading waits, accidental idle stands and debug UI from promo takes.
  Never describe authored or edited time as real-time gameplay evidence.
- Curate rather than catalogue. Twenty representative effects beat 48, and
  the film ran 106 s instead of 241 s. Avoid spoilers (minigames, the final
  boss's secrets) unless they are asked for.

### Sound

- For a showcase, one coherent bed with chapters on its sections is a strong
  default; a climax jingle or calmer end card can support its arc. A staged story
  can use scene-led music, ambience, voice and silence. Make changes intentional,
  never incidental switches to music embedded in the next source clip.
- Use editorial stingers as punctuation: a ring on a pop, a spin dash before
  a wipe, a Giant Ring under an iris, a boss hit on a punch. A staged story can
  retain sounds motivated by its action as well as deliberate musical accents.
- **A game sound means its event.** A jump sound tells the viewer someone jumped.
  Play it only when the jump is on screen, on the take-off frame from the state CSV,
  not on the beat or at the apex. The user called jumps used as punctuation
  "confusing". Punctuate graphics with sounds that cannot contradict the picture:
  Giant Ring, Register, a spin dash release.
- **Sync to the frame, not the plan.** Typing ticks, presses and landings land on the
  frame where the picture changes. Find it in the CSV or by differencing source
  frames. One draft's ticks came 36 frames before the letters they announced.
- **Stingers sit at game level.** In the game, music and effects share one driver at
  comparable loudness. Both A/B films mixed stingers 8–18 dB under full-level music,
  and the user heard the music as "way louder than the SFX". Start with stingers near
  gain 1 and the bed around 0.75. `promo_kit.py check` warns when a stinger sits more
  than 9 dB under the music.
- **Mind hard-panned effects.** Some ROM effects play from one speaker. The S1 ring is
  right-only, and S3K alternates its right and left ring sounds (`$33`/`$34`) on every
  ring. A draft that used `$33` alone put every ring "stuck on the right channel".
  Alternate the pair as the game does, or centre a lone stinger. `check` warns about a
  one-sided stinger that is not part of an alternating run.
- Under an editorial bed, clip audio is SFX only, with one owner for each intended
  music/effect layer. Keep speech intelligible and avoid duplicating Stageplay's
  authored audio in the edit. Duck menu
  stretches. Keep genuine native audio when it is the subject, for example a
  speed effect or an announcer.
- `master()` targets about −16 dBFS RMS, not measured −16 LUFS. Measure integrated
  and sectional loudness across story/tutorial boundaries and the true peak of the
  AAC-encoded file, not only the PCM. A whole-film average can hide an abrupt level
  step; continuous music needs genuine SFX-only stems beneath it.

## Editorial review before delivery

Use the questions for the chosen form. Showcase defaults are diagnostic aids,
not mandatory ingredients for a narrative film.

1. Does the opening establish its subject promptly? A showcase should move from
   the first second and land its promise by about 6 s; a story opening needs a
   deliberate action or dramatic question rather than accidental waiting.
2. Is the title face game-native or heavy condensed, never a regular system
   sans?
3. Is promotional type crafted and animated, and dialogue stably readable with
   an immediately identifiable speaker and enough reveal/reading time?
4. Where comparison matters, would a split or grid show it more clearly?
   Where character interaction matters, do framing and eyelines preserve it?
5. Does a music-led edit respect beats and section changes? Does a story edit
   give actions, lines and reactions their intended time?
6. Where used, do sweeps, pushes and dividers share a consistent direction?
7. Are transitions consistent punctuation rather than an assortment of effects?
8. Does moving footage feel readable rather than rushed or padded? Check
   reactions and whole camera moves, not only shot-length averages or stills.
9. Are gameplay claims shown in actual gameplay? For staged scenes, do deliberate
   blocking and named dialogue carry the story rather than an idle cast?
10. Is the soundtrack coherent, speech clear and every silence intentional? Does each
    game sound mean what it sounds like, land on its event's frame and sit at game
    level beside the music, from the speaker the game would use?
11. Does the closing identification provide the logo, promise, URL, status and
    disclaimer? A showcase end card typically needs 5–7 s; a story's closing
    treatment still needs enough time to read its information.
12. Is it honest? Staged, positioned or debug setups are labelled in the provenance,
    nothing claims to be online or real time when it is not, and any "reviews"
    are obviously in-character jokes, never presented as real quotes.
13. Is every native UI element either whole or gone, with no editorial type on it, and
    is every split's subject inside its slice?
14. Are sprites, scenery and text stable through motion at final cadence and size?
    Distinguish compositor filtering from Stageplay's held zoom. No final upscale
    can serve as a substitute for correcting shimmer in the source render.

When the user gives feedback, change the cause, not the symptom. If they say
"rushed", fewer and longer takes are the fix; a faster cut with more captions
is not. Record their words and the response in the production README, as the
archive does for every revision.

For Stageplay LIVE promos, require native world telemetry beside the source:
boarding/riding/leaving support, collision pickups, hazard hurt/ring loss/recovery,
and flight/glide/landing. Do not advertise an animation, placement or counter as an
interaction without that evidence. Keep zoom one and watch complete moving footage.
