# Lessons from the OpenGGF-Media archive

Paths are relative to the
[OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media) `projects/`
directory. The finished videos are external; the edit code and READMEs are in
the archive.

These examples record particular briefs and feedback. Preserve their craft
lessons without turning the ≥80% gameplay mix, a single bed, moving sales copy or
3–5 s showcase rhythm into requirements for a staged dialogue film. A narrative
may need stable speech panels, longer reactions and intentional silence.

## Reference productions

| Production | What to copy | Where |
|---|---|---|
| **Slay the Robotnik promo** (86 s, the model edit) | The timeline is laid out in beats of HCZ2 (171.107 BPM), with chapters on the track's section changes. Openings: a cold open on the OpenGGF badge, then S1/S2/S3K in three slanted slices dropping in on beats, then a "NOW, / SOMETHING / NEW." word stack. Title drop: flash, Giant Ring stinger, slam-out. Chapters use ROM title-card banner headers with motion modelled on `ObjArray_TtlCard`. Splits and grids: a 2×2 boss grid popping in three beats apart, a 4×2 event mosaic, and three rooms side by side followed by counters. Engine pitch: "EVERY SPRITE / SOUND / STAGE" over a vertical-slice wall, and a typed code panel beside the live scene. Climax: the final blow runs in one take into Act Clear. End card: the logo redrawn from the mod's `font.txt` with a shine. There are only 15 transitions, and banded wipes appear at chapter changes only. | `slay-the-robotnik/sources/promo/tools/promo.py`, `README.md`, `chapters.txt` |
| **Putt-Putt Paradise r5** (88.8 s) | Captions in the Sonic 2 title-card style: a red serrated tab drops, the blue band wipes, the lines stagger in, and a yellow strip arrives from the opposite side. Colours are sampled from the captured title card, and the iris is shaped like a golf cup. The title is one take: menu → START → Sonic 2's title card → first tee. Cuts are aligned to the beat within half a beat. Rewinds play reversed audio. | `putt-putt-paradise/sources/media/promo-opus-20261006/final-verified/` |
| **Flappy Tails / Typing of the Hedgehog** | Trailer grammar: slam cards (scale overshoot, decaying shake and a white flash), two-frame cut flashes, zoom punches on exact impact ticks, one labelled half-speed moment with its pitch shifted, and in-character "review" gags. Typing cuts title-card letters out of S3K zone-name art. | `flappy-tails/sources/promo/`, `typing-of-the-hedgehog/sources/promo/` (`PLAN.md`, `make_cards.py`) |
| **Starpost Valley final** | A beat-grid edit, with the music slowed by rubberband. A diagonal four-season gallery: tilted panels with gold separators, cross-fading farm → town → lake. Lower thirds rise and headers slide in with a gold accent bar. Background rule: never wrap a short slice, because it resets the camera. | `starpost-valley/sources/media/promo-polish/edit.py`, `README.md` |
| **Sitar Hero edit-v2** | Restrained but crafted. It uses the mod's own outlined pixel font, a framed stage and long chapters joined by short dissolves. | `sitar-hero/sources/promo/edit-v2/` |
| **Chaos Mode revisions 2–4** | Revision 2 cuts every 2–4 beats with slices and quads, and inserts 8-beat payoffs to rest the eye. Its rule: an effect's first appearance must be FULL or PAIR. Revisions 3 and 4 form a calm single-card gallery, cut from 48 effects to 20. | `sonic3k-chaos-mode/sources/media/revision-*/PLAN.md` |

## What the user rejected, and what fixed it

| Draft | User's words, or the recorded reason | Fix that was accepted |
|---|---|---|
| Putt-Putt v5 (50 s, 16 segments) | "looks amazing but doesn't leave long enough on each section. very rushed." | Fewer and longer takes. Physics shots grew from 1.4–2.6 s to about 4 s. No frozen padding. |
| Putt-Putt paced cut | "consider an intro to the promo. Fade in? audio?" | A 7.7 s cold open over real footage, with words on fanfare beats and the downbeat starting the bed. |
| Mutator Lab expansion promo | "thought I asked for 1 min of footage of menus and menu blips, when I wanted a promo video of the mutators… show them… stacked and toggled… how mad the rings can get" | Gameplay is always the subject, with one 2–4 word action caption, ≥80% moving gameplay, and "configurable" shown as toggle → consequence. |
| Mutator Lab showcase original (76 cuts, about 1.2 s per shot) | "cluttered audio and rapid cuts" | 29 shots of about 4 s each, and 3 cuts in the last 20 s instead of 16. One audio owner per take, menus ducked by 7 dB and low-passed, a quiet close. |
| Sitar Hero edit-v1 | A 1.5 s silent count-in | Dead air removed. |
| Starpost Valley first cuts | 800×448, uniform crossfades, a caption on nearly every shot | Larger cards, cinema crops, the diagonal gallery and a slower bed. |
| Chaos Mode revision 3 | "retain a coherent HCZ soundtrack…; do not fade into the ghost source's DEZ music… omit minigames and related challenge/chat spoilers" | One bed throughout, curated effects and no spoilers. |
| Emerald Remix rough cut (coached Sol draft, 2026-10-22, 41 s) | "it's use of palettes with the text is horrible, particularly around the drop shadow" | Keep the ROM glyph's own colours, and sample the shadow from the native card. Effects go in whole glyph pixels, with no invented outline. `BitmapFont` now enforces the grid. |
| Same draft | "the video is just very... boring... very lazy with long stretches of level showcased without any accompaniment" (14 s of bare Clover Valley) | Pending at the time of writing. Accompany each bar or two (caption beat, callout, counter, event-synced punch or stinger), and sell the mod's idea, not only its levels. `promo_kit.py check` flags bare stretches over 4 s. |
| Second Strike reels (flat reference) | DejaVu `drawtext` at `x=40`, a solid pad, hard concat, a text-only end card | Use this as the counter-example when choosing type and composition. |

## Stageplay dialogue revision

The 2026-10-21 Stageplay review identified visibly poor fractional
nearest-neighbour zoom, an idle cast and unclear speaker identity. The response
is to author purposeful movement and reactions, identify speakers with named
dialogue and use stable held zoom, pans and cuts. The dialogue work adds reusable
reveal typers and skins rather than relying on plain caption overlays.

Revision three is a rendered 51-second short with fourteen named lines, held
zoom one, active blocking and original panel skins. Its complete playback and
transition sequences received bounded agent review; decoded exports contain
3,060 frames with no reported browser drops. Production sources and review limits
are preserved in [OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/stageplay).
The user subsequently rejected the implausible manually positioned jump and asked
for default floor contact and native physical direction. Revision three is therefore
a superseded take, despite its successful decoding and bounded agent review.
The terrain follow-up uses `TERRAIN_ONLY` Puppet mode: destination/urgency and
jump commands feed native inputs, with actual terrain collision, gravity and
landing. The later `LIVE` policy runs actors, placed objects, rings and events in
one native world, with authored shot spawns, routes and bounded waits. Poser mode retains manual
position, animation and frame control for intentional nonphysical staging. Inspect
simulation state before rendering and review the resulting movement; never substitute
a fitted position curve for a requested native jump. Code and codec checks alone
cannot establish creative acceptance.

## Stageplay vertical-camera correction

On 2026-10-22 the user reported “something's wrong with vertical camera movement
relative the level,” specifically the ring-spill fall in revision five. Native
actors were grounded, but foreground scroll used a different vertical origin,
so a landing appeared in empty space. Codec checks, telemetry and the earlier
bounded agent review all missed it. Revision six aligns foreground, priority
masks and sprites in the directed view; all 4,860 native state records remain
unchanged. The exact source and replacement captures are preserved in the
[Stageplay archive](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/stageplay).

Review feet and supporting surfaces through vertical pans and follow handoffs,
including the landing consequence. Keep native background behavior distinct from
foreground alignment: Death Egg is intentionally stationary, and current LIVE
backgrounds retain the native camera phase. A transparent native floor-art tile
can still have collision. Diagnose the rendered transform and native art before
changing movement, adding fake terrain or making every background scroll alike.

## Recurring hazards

- Correct footage does not make good pacing. Count the cuts and compare with
  an accepted reel.
- Positioned or debug entries need disclosure. Never relabel local footage as
  online.
- Unpaced mod captures can outrun song preparation. Check the PCM before
  blaming the audio, and cut the loading waits.
- Measure the true peak on the decoded AAC. A normalized PCM still reached
  +0.41 dB after encoding.
- Takes are rejected when the subject leaves the viewport or the effect is
  hard to read (ring repulsion, an invisible boss). Reframe or recapture.
- Cutting every shot from the same idle point makes the reel look like "the
  same jump with a new label". Vary the entry points.
- Staged performance needs direction. `TERRAIN_ONLY` Puppets run native player
  physics against detached static terrain without placed-object AI, rings or zone
  events. `LIVE` runs those production world owners together; destinations can
  still be blocked and do not add universal pathfinding. Author explicit jumps,
  ability intents and bounded waits. Poser mode keeps manual choreography.
  Check actual entrances, exits, floor contact, facing and reactions in motion.
- Names and consistent placement identify a speaker; color alone does not,
  particularly when several roles use the same stock sprite. Give reveal,
  reading and response their own time instead of forcing each line onto a beat.
- Do not confuse the compositor's filtered camera with engine world rendering.
  `promo_kit.game_view` enlarges with integer nearest-neighbour and applies a BOX
  area filter. `TERRAIN_ONLY` zoom holds between keys; `LIVE` keeps zoom 1. Avoid animated fractional
  nearest-neighbour source zoom. Scaling the finished frame cannot reconstruct
  stable edges that were lost to changing pixel steps in the source.
- Authored `timeline` JSONL describes directing intent with physical actors
  `UNRESOLVED`. `simulate` JSONL resolves either `TERRAIN_ONLY` native actor motion
  on static terrain or `LIVE` native world state/events in `sample.world`. Normal
  gameplay CSV describes an observed gameplay run; retain its input and actual
  entry method. Authored spawns, positioned starts and cuts cannot establish a
  continuous cold completion, and none of these alone proves ROM parity. Keep
  those evidence kinds distinct and retain the required source archive.

## Stageplay first-creator tutorial

On 2026-10-22 the user was pleased with the working features but rejected the
story as making little sense and consisting of characters jumping around. They
requested a tutorial/promo for first-time creators. Revision seven therefore uses
one small conflict: Sonic rushes ahead, leaves Tails behind, comes back, and crosses
with him. Movement needs a reason and a consequence; a sequence of native ability
checks is not automatically a story. The earlier technical checks remain valid
within their scope and do not establish creative acceptance.

For a conceptual tutorial, connect each directing choice to its visible result.
Use a coherent sample, show the actual settings and allow time to read them while
the performance continues. Label editorial notes and track diagrams honestly; do
not imitate controls the editor does not have. Let viewers judge the complete scene
without instructional interruptions; its position in the film should serve the
lesson. Keep an editable sample and a direct path from the lesson to opening it.
A first lesson does not need every available ability or a second unrelated showcase.

An Opus 5.5 aesthetic review found that revision seven repeated the same short
action under seven lessons before showing the story, used small static notes, and
stepped up roughly 7–8 LU at the story boundary. The approved revision-eight
direction leads with the short, follows it with one continuous annotated pass,
and changes one actual setting for a measured comparison. Enlarge the game and
use one readable note per beat. Label any instructional freeze; do not silently
retime native movement. A new moving tail can carry the invitation without
replaying the climax. This is a response to this production, not a mandatory
sequence for every tutorial.

Performance details matter: acknowledge a character ability when its omission
would undermine the premise (Tails can fly), give a listener a visible native
reaction, and synchronize a count with actual takeoff rather than its authored
command alone. Check dialogue against the whole jump arc and supporting surfaces;
blindly moving every panel into the top band can obscure an apex. Use one music
owner with genuine SFX stems and measure the encoded sections, since a whole-film
loudness average can conceal a jump in level. The aesthetic review used sampled
frames and source/audio measurements, not full-motion playback or physical hearing;
its proposals still require those applicable checks and user creative acceptance.
