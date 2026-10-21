---
name: promo-direction
description: Creative direction for OpenGGF promos, trailers and mod showcases. Covers the brief, beat-grid storyboard, typography, split and slice layouts, directional motion, transitions, soundtrack, pacing and the self-review that keeps a promo from going flat. Use with gameplay-highlights, which covers evidence reels, provenance and archiving.
---

# Promo direction

A promo sells one idea through gameplay, on the music, with type that looks like it
belongs to the game. Correct footage cut together with plain captions is a work
log, not a promo. This skill is about the creative layer. Capture footage with
[gameplay-capture](../gameplay-capture/SKILL.md),
[bk2-input-authoring](../bk2-input-authoring/SKILL.md) or
[trace-capture](../trace-capture/SKILL.md). Then follow
[gameplay-highlights](../gameplay-highlights/SKILL.md) for provenance, honest
labelling, verification and the required OpenGGF-Media archive.

Compose with [`tools/media/promo_kit.py`](../../../tools/media/promo_kit.py). It
is a tested beat-grid compositor, so you write creative decisions rather than
maths. Its moves are catalogued in [references/moves.md](references/moves.md).
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

## Workflow

### 1. Study two prior promos before writing anything

Read the edit code of
[Slay the Robotnik](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/slay-the-robotnik/sources/promo):
its `tools/promo.py` `build()` is a whole 86 s promo in about 140 lines. Also
read one of the following:

- Putt-Putt Paradise `promo-opus-20261006/final-verified/render_promo.py`,
  for its Sonic 2 title-card captions and cup iris.
- Flappy Tails `promo_plan.py`, for trailer slams.
- Starpost Valley `promo-polish/edit.py`, for its diagonal season gallery.

The finished videos are external. Their contact sheets and chapter lists are
your reference. State in the brief which reference you are following and what
you will do differently.

### 2. Write the brief at the top of the edit file

Write one line each:

- **Audience.** For example: general Sonic fans, or modders.
- **Promise.** One sentence. For example: "a Sonic deck-building roguelike,
  read from your own ROM".
- **Selling points.** Three to six, each something you can *show in motion*.
  "Configurable" is shown by toggling, then by the result. It is never shown
  by an options page.
- **Hero moment.** The single shot people will remember. Plan the climax
  around it.
- **Music.** Bed track, BPM and first beat, jingle for the climax, calmer
  track for the end card.
- **Palette.** Three or four colours *sampled from the game itself*: its title
  card, logo or HUD.
- **Title face.** Prefer the game's or mod's own letterforms: a ROM title-card
  font, or a mod `art/font.txt` drawn with `BitmapFont`. Otherwise use a heavy
  condensed TTF.
- **End card.** Logo, one line, URL, status line, and the disclaimer "Fan-made
  and free. Not affiliated with SEGA. Requires your own ROM."

### 3. Choose the music first and storyboard in beats

1. Render the bed through OpenGGF's own SMPS driver from the ROM. See Slay's
   `render_audio.py`. Never use unlicensed external music.
2. Run `python3 tools/media/promo_kit.py beats bed.wav` to get the BPM, beat
   phase and section changes. Confirm the phase by ear or against a known
   onset.
3. Write a beat sheet before capturing: chapter, beats, layout, type, sound
   cue. Put chapter changes, header wipes and the title drop on **section
   changes**. Put ordinary cuts on bar lines (every four beats) or phrase
   lines (every eight). An effect's arrival or a contact should land *on* a
   beat.

A typical 60–90 s shape at 120–175 BPM:

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

- At least 80% of the film should be moving gameplay. Menus take ≤15% and
  only appear as the cause of something you then show.
- Each shot shows the approach, the interaction *and* the consequence. Do
  not cut away from the result.
- Prefer one take across a natural chain over cutting it up. Good chains are
  menu → title card → level, or map → node → fight → victory.
- Capture lossless PNG every tick. For a mod scene, capture with music
  stopped so the clip audio is SFX only and the bed is laid separately.
- Start positions must be real for the act, and the setup must be honest.
  Note debug jumps and positioned starts in the provenance.
- Make a contact sheet of each clip, for example Slay's `sheet.py` or
  `strips.py`, and pick in-points by frame number.

### 5. Compose with promo_kit

```bash
python3 tools/media/promo_kit.py fonts                 # every role must say ok, not FALLBACK
python3 tools/media/promo_kit.py fetch-fonts /task/fonts && export PROMO_FONT_DIRS=/task/fonts
python3 tools/media/promo_kit.py template /task/promo/edit.py
python3 tools/media/promo_kit.py demo /task/kit-demo   # shows every move on synthetic footage
```

Keep fonts in the task directory, never in a repository. `DejaVu` in the
`fonts` report means the result will look flat. Fix the fonts before
composing.

Apply the screen grammar below in `build(e)`. Iterate with `sheet` and
`frames`, not full renders.

### 6. Review like an editor, then verify like an engineer

Do the steps in this order:

1. `promo_kit.py sheet edit.py 30`: a five-column sheet, one frame every half
   second.
2. `promo_kit.py frames edit.py N…`: render the middle of every transition,
   every header at full extension, and every grid once it is full.
3. Read the `pacing:` line. Fix anything at or beyond 10 cuts in 20 s, or a
   mean shot under 2 s, unless that rhythm was asked for.
4. Run the **flatness test** below, and fix every "no".
5. Render. Then run the measurements from gameplay-highlights: `ffprobe`, a
   full decode, chapters, true peak *after* AAC encode, and a full 1×
   playback.
6. Archive in OpenGGF-Media as gameplay-highlights requires: the edit file,
   brief, beat sheet, capture scripts and chapters.

## Screen grammar

### Typography: three tiers, always outlined, always moving

| Tier | Role and size at 1080p | Treatment |
|---|---|---|
| Display | `title`, 130–190 px | White or yellow, stroke 8–9, hard shadow 9–10. Words pop in one beat apart ("NOW, / SOMETHING / NEW."). |
| Header | `title` 150 px + `sub` 50 px | Title-card header: the banner drops from the top while the title slides in from the right, and the subtitle follows four frames later. Both leave at double speed. |
| Label | `title_it` 40–50 px | On yellow pills under split slices, or beneath grid cells. |
| Line | `title_it`/`body_it` 44–74 px | A promise or explanation at the top or bottom, on a shade gradient, entering upward. |

- At most one idea on screen at a time, in ≤8 words. A caption is a story
  beat ("One hit…" → "…every ring comes out"), not a label for what is
  plainly visible.
- Every text element enters and leaves. Entrances take 10–16 frames with an
  ease-out or overshoot. Exits take 8 frames and are faster than entrances.
- Use stroke plus a hard shadow over gameplay instead of a box. Use a shade
  gradient (`Shade`, header shades) when a full line sits over busy art.
- Use a wide-tracked small kicker (`tracking=8–12`) sparingly, above a
  display line.
- Hero logos are redrawn from the game's or mod's own font at integer scale,
  with an outline, a drop and a sweeping shine. Banners come from the ROM's
  own title-card art, dumped by tests, never from the disassembly.

### Composition: show several things at once instead of cutting fast

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
- The camera drifts slowly, from zoom 1.0 to 1.15 or a small pan, over a take.
  The title slams out from 1.3. A punch zoom of 0.08–0.10 marks a hit.
- Always use nearest-neighbour integer scaling (`game_view`). Never scale
  pixel art bilinearly. Never stretch the aspect ratio.

### Transitions are punctuation

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

- Mean shot is 3–5 s. No more than 10 cuts in any 20 s window. After a busy
  passage, leave an 8-beat rest on one full-screen take.
- Start the music on frame 0. There is no dead air anywhere, and no frozen
  frames used to pad time. If a section is too short, choose a longer take.
- Loading waits, idle stands and debug UI are cut, and never described as
  real time.
- Curate rather than catalogue. Twenty representative effects beat 48, and
  the film ran 106 s instead of 241 s. Avoid spoilers (minigames, the final
  boss's secrets) unless they are asked for.

### Sound

- Use one bed with chapter changes on its sections, a jingle under the
  climax, and a calmer track under the end card. Do not splice in another
  zone's music because a clip happened to contain it.
- Use ROM sound effects only as punctuation: a ring on a pop, a spin dash
  before a wipe, a Giant Ring under an iris, a boss hit on a punch.
- Clip audio is SFX only, with one audio owner per moment. Duck menu
  stretches. Keep genuine native audio when it is the subject, for example a
  speed effect or an announcer.
- Master to about −16 LUFS (`master()`). Then measure the true peak of the
  AAC-encoded file, not only of the PCM.

## Flatness test (answer every item before delivery)

1. Does the first second move, and does the promise land by 6 s?
2. Is the title face game-native or heavy condensed, never a regular system
   sans?
3. Does every text element have stroke and shadow, and enter and leave?
4. Is there at least one split or grid where several things play at once?
5. Do cuts land on beats, and chapters on music sections? Check the beat
   sheet against the frames.
6. Is there one consistent direction for sweeps, pushes and dividers?
7. Is there a signature transition used only at chapter changes?
8. Is the mean shot 3–5 s, with rest beats after busy passages, and no
   stretch that would read as rushed?
9. Is it ≥80% moving gameplay, with every selling point *shown* happening?
10. Is there one coherent soundtrack, stingers only on punctuation, and no
    dead air?
11. Does the end card have a logo, a promise, the URL, the status and the
    disclaimer, held for 5–7 s?
12. Is it honest? Positioned or debug setups are labelled in the provenance,
    nothing claims to be online or real time when it is not, and any "reviews"
    are obviously in-character jokes, never presented as real quotes.

When the user gives feedback, change the cause, not the symptom. If they say
"rushed", fewer and longer takes are the fix; a faster cut with more captions
is not. Record their words and the response in the production README, as the
archive does for every revision.
