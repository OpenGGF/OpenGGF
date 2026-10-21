# promo_kit moves

Every call below is in `tools/media/promo_kit.py`. An edit file defines
`EDIT = dict(bpm=..., first=..., out=..., work=...)` and `build(e)`. Shots and
overlays take output frames, so place them with `b = e.bf`, which converts beats
to frames. Run `promo_kit.py demo DIR` to see every move on synthetic footage.

These are editorial compositor operations on captured images, not a list of
Stageplay engine capabilities. For staged performances, first author and export
with [stageplay-authoring](../../stageplay-authoring/SKILL.md). Keep its authored
timeline and source soundtrack distinct from any compositor overlays or extra
sound layers. A direct Stageplay film does not require this compositor.

## Sources and cameras

| Call | Use |
|---|---|
| `Frames(dir, "frame-*.png")` | A captured clip, one PNG per tick. Indexes clamp, so an overrun holds the last frame (`e.check()` warns). |
| `extract(video, dir, start, frames, vf)` | Cut frames from an earlier master, for example the 0.6 engine promos. |
| `game_view(img, cx, cy, zoom, ow, oh)` | Cropped editorial framing: integer nearest-neighbour enlargement followed by BOX area filtering to output size. Fractional framing is filtered, not pixel-exact integer-only scaling. Preserves aspect ratio. |
| `cam_move((cx,cy,z),(cx,cy,z))` | Compositor drift or push over captured frames, for example `(200,112,1.06)→(200,112,1.0)`. Review moving output before choosing it. |
| `cam_slam(1.3, 26, total)` | Compositor title-drop framing: starts tight and slams out over 26 frames. |
| `e.punches.append((frame, 0.1))` | Compositor zoom kick decaying over 14 frames, on a hit or a boss landing. |

Stageplay camera x/y can interpolate, but zoom holds until its next key. Use held
integer framing, pans and shot cuts for new staged scenes; imported static
fractional values remain compatible. Do not translate a compositor `cam_move`
example into animated nearest-neighbour world zoom. Review sprites, scenery and
text through the whole move: final upscaling/filtering cannot repair stepping
already baked into the source render. A smooth engine zoom path would need its
own implementation and moving-footage review.

## Shots

```python
e.S(Game(b(20), b(36), title, src=760, cam=cam_slam(1.3, 26, b(36) - b(20)), trans="wipe", tdur=14))
e.S(Custom(b(88), b(103), lambda f, s, ed: grid(ed, f, cells, 2, 2), trans="push", tdur=14, tdir="left"))
```

`Game(..., dim=0.45)` darkens the picture under display type. `hold=N` freezes on
clip frame N. Use it only on a deliberate final pose, never to pad time.

## Layouts (call inside `Custom`)

| Call | Example |
|---|---|
| `slanted_split(e, f, panes, slant=0.22)` | `panes = [(b(4), lambda lf: arr(game_view(s1.frame(lf), 120, None, 1.0, e.W, e.H)), "SONIC THE HEDGEHOG"), ...]`. Each pane's screen centre is moved to its slice centre, so frame the subject at screen centre. Each slice drops in on its start beat. |
| `grid(e, f, cells, cols, rows)` | `cells = [(b(88 + 3*i), clip, src, "LABEL" or None, (cx, cy, zoom) or None), ...]`. Cells pop in with overshoot and labels appear beneath. |
| `vertical_slices(e, f - s.f0, parts, u=s.u(f))` | A dimmed wall behind a statement. |
| `pop_panel(c, img, x, y, f, t0, label)` | Hand-placed framed views, for rooms or code plus game. |
| `stripes(e, f)` | A moving diagonal backdrop for split and grid screens. |
| `blurred(img, e)` | The end-card background. |

## Typography

These are promotional title/label treatments. Stageplay speaker names and dialogue
body text should retain stable readable placement in their authored panel; use its
typers/skins for reveal and appearance rather than adding a title animation to
every line. `Typed` below is the compositor's code-panel effect, not a Stageplay
dialogue typer.

| Call | Example |
|---|---|
| `text(s, role, size, fill, stroke, shadow, tracking)` | `text("BOSSES DROP IN", "title", 150, WHITE, stroke=8, shadow=9)` |
| `Txt(f0, f1, img, x, y, enter, leave, centre)` | Entrances: `fade up down left right pop slam wipe`. Exits: `fade left right pop`. |
| `Header(f_in, f_out, title, sub, banner=rom_banner)` | The S3K title-card header. Pass the ROM banner art as an RGBA array when you have it. |
| `Counter(f0, f1, 139, "CARDS", cx)` | A count-up number with a label. Stagger several by 8 frames. |
| `Typed(f0, f1, lines, x, y, title="HelloSceneMod.java")` | A code panel typed on, with light syntax colouring. |
| `chip(label)` | A yellow pill label. |
| `BitmapFont(path).render(word, scale, fill, outline, drop, upscale)` | A game- or mod-native logo word, drawn crisp at integer scale. |

Word stack on beats:

```python
for k, word in enumerate(["EVERY SPRITE.", "EVERY SOUND.", "EVERY STAGE."]):
    e.O(Txt(b(149 + k * 1.5), b(160.5), text(word, "title", 150, WHITE, stroke=8, shadow=9),
            e.W / 2, 230 + k * 170, enter="pop", centre=True, dur=9))
    e.X(b(149 + k * 1.5), "sfx/ring.wav", 0.4)
```

## Transitions and accents

Stageplay set changes support cuts, black/white fades and left/right wipes.
The branded wipes, iris, pushes and other operations below belong to the
compositor and must not be advertised as equivalent Stageplay settings.

| Name in `trans=` | Look | Budget |
|---|---|---|
| `wipe` | Three slanted colour bands sweep, and the new shot follows them | Chapter changes |
| `iris` | A gold ring opens from the centre | Reveals |
| `push` | The new shot shoves the old one off with a coloured seam (`tdir`) | Continuing motion |
| `whip` | A push with motion blur | A fast change of place |
| `slash` | A single hard diagonal sweep with a bright edge | Inside montages |
| `shutter` | Horizontal bands slide in from alternating sides | Inside montages |
| `xfade` | Dissolve | The end card, calm passages |

Overlays: `Flash(frame, 18)` lands an impact; `Fade(f0, f1)` fades to black;
`Shade(f0, f1, "top"|"bottom")` puts a gradient behind a line; `Fn(f0, f1, fn)`
does anything else.

## Audio

```python
e.bed("audio/hcz2.wav", 0, 1.0, length=e.beats.bt(211), fade_out=0.01)  # bed from frame 0, seconds
e.bed("audio/actclear.wav", b(211) - 5, 1.0, length=5.2, fade_out=0.05)
e.stem("clips/p06-boss.wav", src_frame=2, at_frame=b(91), frames=b(103) - b(91), gain=0.7)  # SFX under a grid
e.X(b(36) - 6, "sfx/spindash.wav", 0.8)  # a stinger just before a wipe
Game(..., wav="clips/p13-journey.wav", stems=0.9)  # a full-screen clip's own SFX
```

`promo_kit.py beats bed.wav` prints the BPM, beat phase and section changes.
`e.mix()` masters to about −16 dBFS RMS with a soft knee. Measure the encoded
AAC true peak as well.

## Commands

`fonts`, `fetch-fonts DIR`, `template OUT.py`, `demo DIR`, `beats WAV`,
`sheet EDIT [step first last]`, `frames EDIT N...`, `audio EDIT`,
`render EDIT`. Each edit command prints `check()` warnings and a `pacing:`
summary: shot count, mean shot length, the busiest 20 s window and the cuts in
the final 20 s.
