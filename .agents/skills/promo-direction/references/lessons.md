# Lessons from the OpenGGF-Media archive

Paths are relative to the
[OpenGGF-Media](https://github.com/OpenGGF/OpenGGF-Media) `projects/`
directory. The finished videos are external; the edit code and READMEs are in
the archive.

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
| Second Strike reels (flat reference) | DejaVu `drawtext` at `x=40`, a solid pad, hard concat, a text-only end card | Use this as the counter-example when choosing type and composition. |

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
