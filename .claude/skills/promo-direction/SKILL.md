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
- For actual gameplay or claims about working gameplay features, use
  [gameplay-capture](../gameplay-capture/SKILL.md), with
  [bk2-input-authoring](../bk2-input-authoring/SKILL.md) when inputs need scripting.
  Use [trace-capture](../trace-capture/SKILL.md) for an existing trace replay.
- A hybrid can combine these, with provenance identifying each source. Authored
  Stageplay poses and timeline JSONL are directing data, not measured gameplay
  state or proof of native AI, collision, physics or parity.

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
- **Hero moment.** The single shot people will remember. Plan the climax
  around it.
- **Sound.** Music structure and, where relevant, BPM/first beat; dialogue,
  effects and intended pauses. A jingle or separate end-card track is a choice,
  not a requirement.
- **Palette.** Three or four colours *sampled from the game itself*: its title
  card, logo or HUD.
- **Title face.** Prefer the game's or mod's own letterforms: a ROM title-card
  font, or a mod `art/font.txt` drawn with `BitmapFont`. Otherwise use a heavy
  condensed TTF.
- **End card.** Logo, one line, URL, status line, and the disclaimer "Fan-made
  and free. Not affiliated with SEGA. Requires your own ROM."

### 3. Storyboard to the film's rhythm

For a music-led showcase, choose the music first and build a beat sheet:

1. Render the bed through OpenGGF's own SMPS driver from the ROM. See Slay's
   `render_audio.py`. Never use unlicensed external music.
2. Run `python3 tools/media/promo_kit.py beats bed.wav` to get the BPM, beat
   phase and section changes. Confirm the phase by ear or against a known
   onset.
3. Write a beat sheet before capturing: chapter, beats, layout, type, sound
   cue. Put chapter changes, header wipes and the title drop on **section
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
  For gameplay clips under an editorial bed, capture SFX separately from music.
  Stageplay exports use their authored audio cues; avoid mixing that soundtrack
  with a second unplanned bed in the compositor.
- Gameplay start positions must be real for the act; disclose debug jumps and
  positioned starts. Stageplay uses detached staged sets, so retain its screenplay,
  canonical scene and authored timeline JSONL rather than calling it native play.
- Make a contact sheet of each clip, for example Slay's `sheet.py` or
  `strips.py`, and pick in-points by frame number.

For Stageplay blocking, give characters purposeful entrances, approaches, turns,
gestures, reactions and exits as the scene needs. Match movement to catalogued
walk/run animation, place feet against the set, and check facing and accessories
through the motion. Detached staging does not invent gameplay AI or collision to
fill in missing performance. A listening character should visibly react where
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

Keep fonts in the task directory, never in a repository. `DejaVu` in the
`fonts` report means the result will look flat. Fix the fonts before
composing.

Apply the relevant screen grammar in `build(e)`. Iterate composition with `sheet`
and `frames`, then render short moving previews for camera, animation, dialogue
and transition timing. Contact sheets cannot reveal shimmer or an idle performance.

### 6. Review like an editor, then verify like an engineer

Do the steps in this order:

1. For a compositor edit, `promo_kit.py sheet edit.py 30` makes a five-column
   sheet, one frame every half second. For Stageplay, inspect authored timeline
   samples and source frames at the action, speaking and transition boundaries.
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
   canonical scene and authored timeline when used. A direct Stageplay render does
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
- Hero logos are redrawn from the game's or mod's own font at integer scale,
  with an outline, a drop and a sweeping shine. Banners come from the ROM's
  own title-card art, dumped by tests, never from the disassembly.

### Composition: show several things at once instead of cutting fast

Use these layouts for comparison or breadth. A dialogue scene can stay full-screen
to preserve eyelines, speaker identity and the timing of a reaction.

- **Slanted split (2–3 slices).** For comparisons and "all three games". The
  dividers lean like title-card speed lines (slant ≈0.22), each slice drops in
  on its beat, and a pill label names each slice.
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
- In Stageplay, camera x/y can pan while zoom holds until its next key. Author
  stable integer framing and use cuts to change it. Imported static fractional
  values remain compatible; do not animate fractional nearest-neighbour zoom.
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
- Under an editorial bed, clip audio is SFX only, with one owner for each intended
  music/effect layer. Keep speech intelligible and avoid duplicating Stageplay's
  authored audio in the edit. Duck menu
  stretches. Keep genuine native audio when it is the subject, for example a
  speed effect or an announcer.
- `master()` targets about −16 dBFS RMS, not measured −16 LUFS. Measure integrated
  loudness as needed and the true peak of the AAC-encoded file, not only the PCM.

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
10. Is the soundtrack coherent, speech clear and every silence intentional?
11. Does the closing identification provide the logo, promise, URL, status and
    disclaimer? A showcase end card typically needs 5–7 s; a story's closing
    treatment still needs enough time to read its information.
12. Is it honest? Staged, positioned or debug setups are labelled in the provenance,
    nothing claims to be online or real time when it is not, and any "reviews"
    are obviously in-character jokes, never presented as real quotes.
13. Are sprites, scenery and text stable through motion at final cadence and size?
    Distinguish compositor filtering from Stageplay's held zoom. No final upscale
    can serve as a substitute for correcting shimmer in the source render.

When the user gives feedback, change the cause, not the symptom. If they say
"rushed", fewer and longer takes are the fix; a faster cut with more captions
is not. Record their words and the response in the production README, as the
archive does for every revision.
