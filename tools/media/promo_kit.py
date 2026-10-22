#!/usr/bin/env python3
"""Beat-grid promo compositor: shots, split layouts, motion typography, transitions and an
audio bed, rendered frame by frame with numpy/Pillow and encoded by ffmpeg.

Purpose: give promo edits a tested vocabulary of moves (slanted splits, pop-in grids, title-card
headers, banded wipes, iris, directional pushes, shutters, punch zooms, kinetic words, counters)
so an edit file only states creative decisions. See the promo-direction skill.
Inputs: an edit module defining build(edit) (see `promo_kit.py template`), lossless PNG frame
directories from GameplayCaptureTool/ExampleModCapture/trace-capture, optional WAV stems/music.
Origin: distilled from the Slay the Robotnik promo compositor (OpenGGF-Media
projects/slay-the-robotnik/sources/promo/tools/promo.py, 2026-10-05), generalised 2026-10-21.

  promo_kit.py template OUT.py           write a starter edit file
  promo_kit.py demo OUT_DIR              render the synthetic demo edit (no ROM needed)
  promo_kit.py fonts                     show which font each typography role resolved to
  promo_kit.py fetch-fonts DIR           download the OFL display faces (then PROMO_FONT_DIRS=DIR)
  promo_kit.py beats TRACK.wav           tempo, first beat and section changes (in beats)
  promo_kit.py sheet EDIT.py [step]      contact sheet of output frames
  promo_kit.py frames EDIT.py N...       single output frames as PNG
  promo_kit.py audio EDIT.py             mix the soundtrack only
  promo_kit.py render EDIT.py            full render (video + audio + faststart MP4)
"""
import importlib.util
import math
import os
import shutil
import subprocess
import sys
import wave
from functools import lru_cache
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ----------------------------------------------------------------------------------- palette

WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
YELLOW = (255, 210, 31)
NAVY = (8, 16, 56)
BLUE = (22, 56, 184)
RED = (224, 32, 32)
GOLD = (232, 168, 16)
SKY = (108, 144, 218)

# -------------------------------------------------------------------------------- easing/math


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease_out(u):
    u = clamp(u)
    return 1 - (1 - u) ** 3


def ease_in(u):
    u = clamp(u)
    return u ** 3


def smooth(u):
    u = clamp(u)
    return u * u * (3 - 2 * u)


def back(u, s=1.6):
    """Overshoot then settle: the 'pop' of a panel or word landing."""
    u = clamp(u) - 1
    return u * u * ((s + 1) * u + s) + 1


# ---------------------------------------------------------------------------------- timeline


class Beats:
    """The edit's clock. Shots and overlays are placed in beats of the soundtrack, so cuts land
    on the music; bf(k) is the output frame of beat k (fractions allowed)."""

    def __init__(self, bpm, first=0.0, fps=60):
        self.bpm, self.first, self.fps = bpm, first, fps
        self.period = 60.0 / bpm

    def bt(self, k):
        return self.first + k * self.period

    def bf(self, k):
        return int(round(self.bt(k) * self.fps))


# ------------------------------------------------------------------------------------ images


def arr(img):
    return np.asarray(img, dtype=np.float32)


def resample(img, x0, y0, vw, vh, ow, oh):
    """Pixel-art scaling: nearest-neighbour up by an integer factor, then an area filter down to
    the output, so pixels stay square and sharp at any zoom or sub-pixel camera position."""
    k = max(1, math.ceil(ow / vw - 1e-9))
    ix0, iy0 = math.floor(x0), math.floor(y0)
    ix1, iy1 = min(img.width, math.ceil(x0 + vw)), min(img.height, math.ceil(y0 + vh))
    crop = img.crop((ix0, iy0, ix1, iy1)).resize(((ix1 - ix0) * k, (iy1 - iy0) * k), Image.NEAREST)
    box = ((x0 - ix0) * k, (y0 - iy0) * k, (x0 - ix0 + vw) * k, (y0 - iy0 + vh) * k)
    return crop.resize((ow, oh), Image.BOX, box=box)


def game_view(img, cx=None, cy=None, zoom=1.0, ow=1920, oh=1080):
    """A camera onto a native game frame: centre (cx, cy) in game pixels, zoom >= 1 crops in.
    The view fills ow x oh (cropping the long axis, never stretching)."""
    cx = img.width / 2 if cx is None else cx
    cy = img.height / 2 if cy is None else cy
    vh = img.height / zoom
    vw = vh * ow / oh
    if vw > img.width:
        vw = img.width / zoom
        vh = vw * oh / ow
    x0 = clamp(cx - vw / 2, 0, img.width - vw)
    y0 = clamp(cy - vh / 2, 0, img.height - vh)
    return resample(img, x0, y0, vw, vh, ow, oh)


def paste(canvas, rgba, x, y, alpha=1.0):
    """Alpha-composites a float RGBA array onto the float RGB canvas at (x, y)."""
    if alpha <= 0:
        return
    x, y = int(round(x)), int(round(y))
    h, w = rgba.shape[:2]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(canvas.shape[1], x + w), min(canvas.shape[0], y + h)
    if x0 >= x1 or y0 >= y1:
        return
    src = rgba[y0 - y:y1 - y, x0 - x:x1 - x]
    a = src[..., 3:4] * (alpha / 255.0)
    region = canvas[y0:y1, x0:x1]
    region *= 1 - a
    region += src[..., :3] * a


def scaled(rgba, s):
    """A float RGBA array resized by s (for pops)."""
    if abs(s - 1) < 1e-3:
        return rgba
    im = Image.fromarray(rgba.astype(np.uint8), "RGBA")
    w, h = max(1, int(im.width * s)), max(1, int(im.height * s))
    return arr(im.resize((w, h), Image.BILINEAR))


def rgba_of(img):
    """Any PIL image as a float RGBA array."""
    return arr(img.convert("RGBA"))


def framed(img, border=8, colour=YELLOW):
    """A solid-colour border around a picture, as a float RGBA array."""
    a = np.zeros((img.height + 2 * border, img.width + 2 * border, 4), np.float32)
    a[..., :3] = colour
    a[..., 3] = 255
    a[border:-border, border:-border, :3] = arr(img.convert("RGB"))
    return a


@lru_cache(maxsize=None)
def rounded(w, h, r, fill, alpha=255):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=fill + (alpha,))
    return arr(im)


class Frames:
    """A directory of numbered PNG frames (one per game tick). Indexes clamp to the clip, so a
    shot that outlives its source holds the last frame rather than failing."""

    def __init__(self, path, pattern="*.png"):
        self.path, self.pattern = Path(path), pattern

    @property
    @lru_cache(maxsize=None)
    def files(self):
        files = sorted(self.path.glob(self.pattern))
        if not files:
            raise FileNotFoundError(f"no frames matching {self.pattern} in {self.path}")
        return files

    def __len__(self):
        return len(self.files)

    @lru_cache(maxsize=96)
    def frame(self, i):
        i = int(clamp(i, 0, len(self) - 1))
        return Image.open(self.files[i]).convert("RGB")


def extract(video, out_dir, start, frames, vf=None):
    """Cuts frames from a finished video (for example an earlier promo master) into PNGs."""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y", "-ss", str(start), "-i", str(video),
           "-frames:v", str(frames)]
    if vf:
        cmd += ["-vf", vf]
    subprocess.run(cmd + [str(out / "%05d.png")], check=True)
    return Frames(out)


# -------------------------------------------------------------------------------- typography

FONT_DIRS = [d for d in os.environ.get("PROMO_FONT_DIRS", "").split(os.pathsep) if d] + [
    str(Path.home() / ".local/share/fonts"), str(Path.home() / ".fonts"), "/usr/share/fonts",
    "/usr/local/share/fonts", "/Library/Fonts", str(Path.home() / "Library/Fonts"),
    "C:/Windows/Fonts"]

# Each role lists files in order of preference. The look depends on a heavy *condensed* display
# face for titles; a plain regular sans for everything is what makes a promo read as flat.
ROLES = {
    "title": ["FiraSansCompressed-Heavy.ttf", "FiraSansExtraCondensed-Black.ttf", "Anton-Regular.ttf",
              "BebasNeue-Regular.ttf", "Oswald-Bold.ttf", "LeagueGothic-Regular.otf", "Impact.ttf",
              "impact.ttf", "DejaVuSansCondensed-Bold.ttf", "DejaVuSans-Bold.ttf"],
    "title_it": ["FiraSansCompressed-HeavyItalic.ttf", "FiraSansExtraCondensed-BlackItalic.ttf",
                 "FiraSansCondensed-BlackItalic.ttf", "DejaVuSansCondensed-BoldOblique.ttf",
                 "DejaVuSans-BoldOblique.ttf", "DejaVuSans-Bold.ttf"],
    "sub": ["FiraSansCondensed-BoldItalic.ttf", "FiraSansExtraCondensed-BoldItalic.ttf",
            "RobotoCondensed-BoldItalic.ttf", "DejaVuSansCondensed-BoldOblique.ttf",
            "DejaVuSans-BoldOblique.ttf", "DejaVuSans-Bold.ttf"],
    "body": ["FiraSans-Bold.ttf", "Inter-Bold.ttf", "SourceSans3-Bold.ttf", "Roboto-Bold.ttf",
             "DejaVuSans-Bold.ttf"],
    "body_it": ["FiraSans-BoldItalic.ttf", "Inter-BoldItalic.ttf", "Roboto-BoldItalic.ttf",
                "DejaVuSans-BoldOblique.ttf", "DejaVuSans-Bold.ttf"],
    "mono": ["Hack-Regular.ttf", "JetBrainsMono-Regular.ttf", "FiraMono-Regular.ttf",
             "DejaVuSansMono.ttf"],
    "mono_b": ["Hack-Bold.ttf", "JetBrainsMono-Bold.ttf", "FiraMono-Bold.ttf", "DejaVuSansMono-Bold.ttf"],
}
FALLBACK_NAMES = {"DejaVuSansCondensed-Bold.ttf", "DejaVuSansCondensed-BoldOblique.ttf", "DejaVuSans-Bold.ttf",
                  "DejaVuSans-BoldOblique.ttf", "DejaVuSansMono.ttf", "DejaVuSansMono-Bold.ttf"}


@lru_cache(maxsize=None)
def _font_index():
    index = {}
    for d in FONT_DIRS:
        p = Path(d)
        if p.is_dir():
            for f in p.rglob("*"):
                if f.suffix.lower() in (".ttf", ".otf"):
                    index.setdefault(f.name, f)
    return index


@lru_cache(maxsize=None)
def font_path(role):
    """The first installed file for a role (or a literal path). Set PROMO_FONT_DIRS to add a
    folder of downloaded display faces; `promo_kit.py fonts` reports what resolved."""
    if Path(role).suffix.lower() in (".ttf", ".otf"):
        return Path(role)
    index = _font_index()
    for name in ROLES[role]:
        if name in index:
            return index[name]
    raise FileNotFoundError(f"no font for role {role}; install one of {ROLES[role]} or set PROMO_FONT_DIRS")


def fonts_report():
    rows = []
    for role in ROLES:
        try:
            p = font_path(role)
            rows.append((role, p.name, "FALLBACK (flat)" if p.name in FALLBACK_NAMES else "ok"))
        except FileNotFoundError:
            rows.append((role, "-", "MISSING"))
    return rows


# SIL Open Font License faces from the google/fonts repository, pinned by commit-free path on the
# jsdelivr mirror. Fetched into a task directory, never committed.
FETCH = ["firasansextracondensed/FiraSansExtraCondensed-Black", "firasansextracondensed/FiraSansExtraCondensed-BlackItalic",
         "firasanscondensed/FiraSansCondensed-BoldItalic", "firasans/FiraSans-Bold", "firasans/FiraSans-BoldItalic",
         "anton/Anton-Regular"]


def fetch_fonts(dest):
    import urllib.request
    dest = Path(dest)
    dest.mkdir(parents=True, exist_ok=True)
    for rel in FETCH:
        out = dest / (rel.split("/")[1] + ".ttf")
        if not out.exists():
            url = f"https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/{rel}.ttf"
            with urllib.request.urlopen(url, timeout=60) as r:
                out.write_bytes(r.read())
        print(out)


@lru_cache(maxsize=None)
def font(role, size):
    return ImageFont.truetype(str(font_path(role)), size)


@lru_cache(maxsize=512)
def text(s, role="title", size=120, fill=WHITE, stroke=0, sfill=BLACK, shadow=0, scol=(0, 0, 40, 170),
         tracking=0):
    """Pre-rendered text as a float RGBA array: a stroke (outline) and an offset hard shadow are
    what keep type legible over moving gameplay without a box behind it. tracking adds pixels
    between letters (wide-tracked small caps for kickers and labels)."""
    f = font(role, size)
    l, t, r, b = f.getbbox(s, stroke_width=stroke)
    if tracking:
        xs, x = [], 0.0
        for ch in s:
            xs.append(x)
            x += f.getlength(ch) + tracking
        r = l + int(x - tracking) + 2 * stroke
        runs = list(zip(xs, s))
    else:
        runs = [(0.0, s)]
    pad = stroke + 6
    w, h = r - l + 2 * pad + shadow, b - t + 2 * pad + shadow

    def layer(dx, colour, outline):
        im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for x0, run in runs:
            d.text((pad - l + dx + x0, pad - t + dx), run, font=f, fill=colour, stroke_width=stroke,
                   stroke_fill=outline)
        return im

    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if shadow:
        base = Image.alpha_composite(base, layer(shadow, scol, scol))
    return arr(Image.alpha_composite(base, layer(0, fill + (255,), sfill + (255,))))


@lru_cache(maxsize=None)
def chip(label, size=50, fg=NAVY, bg=YELLOW, role="title_it"):
    """A rounded pill label (game names under split slices, tags on grid cells)."""
    t = Image.fromarray(text(label, role, size, fg).astype(np.uint8), "RGBA")
    pill = Image.fromarray(rounded(t.width + 44, t.height + 12, 14, bg).astype(np.uint8), "RGBA")
    pill.alpha_composite(t, (22, 6))
    return arr(pill)


class BitmapFont:
    """A pixel font in the example mods' art/font.txt layout: '= X' starts glyph X, then rows of
    '#' (ink) and '.' (paper). Drawing at an integer scale keeps it crisp; outline() gives the
    thick dark border that classic Sonic logos use. Prefer a font the game or mod already owns
    to a generic TTF for the hero title."""

    def __init__(self, path, space=4):
        self.glyphs, name, rows = {}, None, []
        for line in Path(path).read_text().splitlines():
            if line.startswith("= ") and len(line) >= 3:
                if name is not None and rows:
                    self.glyphs[name] = rows
                name, rows = line[2:3], []
            elif name is not None and line and line[0] in "#.":
                rows.append(line)
        if name is not None and rows:
            self.glyphs[name] = rows
        self.space = space

    def width(self, s):
        return max(0, sum(self.space if ch == " " else len(self.glyphs[ch][0]) + 1 for ch in s) - 1)

    def height(self):
        return max(len(rows) for rows in self.glyphs.values())

    def draw(self, px, s, x, y, colour, scale=1):
        for ch in s:
            if ch == " ":
                x += self.space * scale
                continue
            for gy, row in enumerate(self.glyphs[ch]):
                for gx, v in enumerate(row):
                    if v == "#":
                        px[y + gy * scale:y + (gy + 1) * scale, x + gx * scale:x + (gx + 1) * scale] = colour
            x += (len(self.glyphs[ch][0]) + 1) * scale

    def render(self, s, scale=4, fill=WHITE, outline=NAVY, outline_px=1, drop=(0x90, 0, 0), drop_px=1,
               upscale=1):
        """Logo-style word: outline ring, a darker drop under the body, then the body; returned as
        a float RGBA array, nearest-neighbour upscaled by `upscale` for output size."""
        pad = outline_px + drop_px + 1
        w, h = self.width(s) * scale + 2 * pad, self.height() * scale + 2 * pad
        px = np.zeros((h, w, 4), np.uint8)
        if outline:
            for dy in range(-outline_px, outline_px + 1):
                for dx in range(-outline_px, outline_px + 1):
                    self.draw(px, s, pad + dx, pad + dy + (drop_px if dy > 0 else 0), outline + (255,), scale)
        if drop:
            self.draw(px, s, pad, pad + drop_px, drop + (255,), scale)
        self.draw(px, s, pad, pad, fill + (255,), scale)
        im = Image.fromarray(px, "RGBA")
        if upscale > 1:
            im = im.resize((im.width * upscale, im.height * upscale), Image.NEAREST)
        return arr(im)


# ----------------------------------------------------------------------------------- shots


class Shot:
    def __init__(self, f0, f1, trans=None, tdur=0, tdir=None):
        self.f0, self.f1 = f0, f1
        self.trans, self.tdur, self.tdir = trans, tdur, tdir

    def u(self, f):
        return (f - self.f0) / max(1, self.f1 - self.f0)


def cam_static(cx=None, cy=None, z=1.0):
    return lambda u: (cx, cy, z)


def cam_move(a, b, ease=smooth):
    """Drift from camera a to b over the shot: (cx, cy, zoom) each, game pixels."""
    return lambda u: tuple(a[k] + (b[k] - a[k]) * ease(u) for k in range(3))


def cam_slam(z0, frames, total_frames, cx=None, cy=None):
    """Starts zoomed in at z0 and slams out to 1.0 over `frames` (a title drop)."""
    def cam(u):
        f = u * total_frames
        return (cx, cy, 1 + (z0 - 1) * (1 - ease_out(f / frames)) if f < frames else 1.0)
    return cam


class Game(Shot):
    """A recorded clip filling the screen. src is the clip frame at the shot's first frame;
    cam(u) gives (cx, cy, zoom); hold freezes on a clip frame; dim darkens for text on top;
    stems is the gain of the clip's own audio (0 for none)."""

    def __init__(self, f0, f1, clip, src=0, cam=None, hold=None, dim=0.0, stems=0.0, trans=None, tdur=0,
                 tdir=None, wav=None):
        super().__init__(f0, f1, trans, tdur, tdir)
        self.clip, self.src, self.cam, self.hold, self.dim = clip, src, cam, hold, dim
        self.stems, self.wav = stems, wav

    def src_frame(self, f):
        i = self.src + (f - self.f0)
        return min(i, self.hold) if self.hold is not None else i

    def image(self, f, edit):
        cx, cy, z = self.cam(self.u(f)) if self.cam else (None, None, 1.0)
        c = arr(game_view(self.clip.frame(self.src_frame(f)), cx, cy, z * edit.punch(f), edit.W, edit.H))
        if self.dim:
            c *= 1 - self.dim
        return c


class Custom(Shot):
    """A composed screen: fn(f, shot, edit) returns a float RGB (H, W, 3) array."""

    def __init__(self, f0, f1, fn, trans=None, tdur=0, tdir=None):
        super().__init__(f0, f1, trans, tdur, tdir)
        self.fn = fn

    def image(self, f, edit):
        return self.fn(f, self, edit)


# -------------------------------------------------------------------------------- overlays


class Overlay:
    def __init__(self, f0, f1):
        self.f0, self.f1 = f0, f1


class Txt(Overlay):
    """A pre-rendered text/image with an entrance and an exit. enter: fade, left, right, up, down,
    pop (overshoot scale), slam (lands big, shakes, decays: trailer cards), wipe (reveals left to
    right); leave: fade, left, right, pop. Anchor is top-left unless centre=True."""

    def __init__(self, f0, f1, img, x, y, enter="fade", leave="fade", centre=False, dur=10, alpha=1.0, out_dur=8):
        super().__init__(f0, f1)
        self.img, self.x, self.y, self.enter, self.leave = img, x, y, enter, leave
        self.centre, self.dur, self.alpha, self.out_dur = centre, dur, alpha, out_dur

    def draw(self, c, f, edit):
        img = self.img
        u_in = (f - self.f0) / self.dur
        u_out = (self.f1 - f) / self.out_dur
        a, x, y, s = self.alpha, self.x, self.y, 1.0
        if u_in < 1:
            if self.enter == "fade":
                a *= smooth(u_in)
            elif self.enter == "left":
                x -= (1 - ease_out(u_in)) * (img.shape[1] + 200)
                a *= clamp(u_in * 3)
            elif self.enter == "right":
                x += (1 - ease_out(u_in)) * 700
                a *= clamp(u_in * 3)
            elif self.enter == "up":
                y += (1 - ease_out(u_in)) * 80
                a *= smooth(u_in)
            elif self.enter == "down":
                y -= (1 - ease_out(u_in)) * 80
                a *= smooth(u_in)
            elif self.enter == "pop":
                s = 0.4 + 0.6 * back(u_in, 2.2)
                a *= clamp(u_in * 4)
            elif self.enter == "slam":
                t = f - self.f0
                s = 1 + max(0, 8 - t) * 0.22 / 8 * 2.5
                x += math.sin(t * 2.5) * max(0, 12 - t) * 1.8
                a *= clamp(u_in * 6)
            elif self.enter == "wipe":
                img = img.copy()
                img[:, int(img.shape[1] * ease_out(u_in)):, 3] = 0
        if u_out < 1:
            if self.leave == "left":
                x -= (1 - smooth(u_out)) * (img.shape[1] + 300)
            elif self.leave == "right":
                x += (1 - smooth(u_out)) * (edit.W - x + 100)
            elif self.leave == "pop":
                s *= 1 + (1 - u_out) * 0.25
                a *= smooth(u_out)
            else:
                a *= smooth(u_out)
        if s != 1.0:
            img = scaled(img, s)
        if self.centre:
            x, y = x - img.shape[1] / 2, y - img.shape[0] / 2
        paste(c, img, x, y, a)


class Header(Overlay):
    """Section header in the manner of a Sonic 3 & Knuckles title card: a banner (the ROM's own
    title-card art if you dumped it, else a red bar) drops from the top at constant speed while
    the title then the subtitle slide in from the right; everything leaves at double speed."""

    def __init__(self, f_in, f_out, title, sub=None, banner=None, x_end=300, base_y=1000):
        super().__init__(f_in, f_out + 16)
        self.out, self.x_end, self.base_y = f_out, x_end, base_y
        self.title = text(title, "title", 150, WHITE, stroke=8, shadow=9)
        self.sub = text(sub, "sub", 50, YELLOW, stroke=4, shadow=5) if sub else None
        self.banner = banner

    def draw(self, c, f, edit):
        t, e = f - self.f0, f - self.out
        paste(c, edit.bottom_shade(), 0, 0, clamp(t / 10) * (1 - clamp(e / 12)))
        ban = self.banner if self.banner is not None else edit.red_banner()
        y_end = self.base_y + 12 - ban.shape[0]
        y = min(y_end, -ban.shape[0] + 68 * t)
        if e > 0:
            y = y_end - 140 * e
        paste(c, ban, 72, y)
        tx = max(self.x_end, edit.W + 40 - 125 * (t - 3))
        sx = max(self.x_end + 8, edit.W + 40 - 125 * (t - 7))
        if e > 0:
            tx = self.x_end + 260 * max(0, e - 2)
            sx = self.x_end + 8 + 260 * e
        ty = self.base_y - (self.sub.shape[0] if self.sub is not None else 0) - self.title.shape[0] + 26
        paste(c, self.title, tx, ty)
        if self.sub is not None:
            paste(c, self.sub, sx, ty + self.title.shape[0] - 22)


class Flash(Overlay):
    """A white (or coloured) hit frame that decays: lands a title drop or a boss hit."""

    def __init__(self, f, dur=12, peak=1.0, colour=WHITE):
        super().__init__(f, f + dur)
        self.peak, self.colour = peak, np.array(colour, np.float32)

    def draw(self, c, f, edit):
        a = self.peak * (1 - (f - self.f0) / (self.f1 - self.f0)) ** 2
        c *= 1 - a
        c += self.colour * a


class Fade(Overlay):
    def __init__(self, f0, f1, out=True):
        super().__init__(f0, f1)
        self.out = out

    def draw(self, c, f, edit):
        u = smooth((f - self.f0) / max(1, self.f1 - self.f0))
        c *= (1 - u) if self.out else u


class Shade(Overlay):
    """A gradient behind a line of text: 'top' or 'bottom', fading in and out with the text."""

    def __init__(self, f0, f1, where="bottom"):
        super().__init__(f0, f1)
        self.where = where

    def draw(self, c, f, edit):
        g = edit.top_shade() if self.where == "top" else edit.bottom_shade()
        paste(c, g, 0, 0, clamp((f - self.f0) / 10) * (1 - clamp((f - self.f1 + 10) / 10)))


class Counter(Overlay):
    """A number that counts up with ease-out, with a label beneath: the 'by the numbers' beat."""

    def __init__(self, f0, f1, value, label, cx, y=620, dur=26, colour=YELLOW):
        super().__init__(f0, f1)
        self.value, self.label, self.cx, self.y, self.dur, self.colour = value, label, cx, y, dur, colour

    def draw(self, c, f, edit):
        u = clamp((f - self.f0) / self.dur)
        a = clamp((f - self.f0) / 6) * (1 - clamp((f - self.f1 + 8) / 8))
        n = text(str(int(round(self.value * ease_out(u)))), "title", 190, self.colour, stroke=8, shadow=8)
        paste(c, n, self.cx - n.shape[1] / 2, self.y, a)
        lab = text(self.label, "title_it", 50, WHITE, stroke=5, shadow=5)
        paste(c, lab, self.cx - lab.shape[1] / 2, self.y + n.shape[0] + 20, a)


class Typed(Overlay):
    """Monospace lines typed on at `cps` characters per second inside a dark code panel.
    lines: (kind, text) with kind 'c' comment, 'k' keyword line, 'n' normal."""

    KEYWORDS = ("public", "final", "class", "implements", "void", "return", "new", "def", "import")

    def __init__(self, f0, f1, lines, x, y, w=900, h=600, cps=7.5 * 60 / 8, title=""):
        super().__init__(f0, f1)
        self.lines, self.x, self.y, self.w, self.h, self.cps, self.title = lines, x, y, w, h, cps, title

    def panel(self, chars):
        im = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((0, 0, self.w - 1, self.h - 1), 18, fill=(14, 18, 34, 235), outline=(70, 90, 160, 255),
                            width=3)
        for k, col in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
            d.ellipse((22 + k * 30, 18, 40 + k * 30, 36), fill=col)
        if self.title:
            d.text((120, 14), self.title, font=font("mono", 22), fill=(150, 160, 190))
        f = font("mono", 25)
        left, y = chars, 66
        for kind, line in self.lines:
            shown = line[:max(0, left)]
            left -= len(line) + 1
            x = 26
            if kind == "c":
                d.text((x, y), shown, font=f, fill=(110, 130, 120))
            else:
                for tok in shown.split(" "):
                    colour = (255, 150, 70) if tok in self.KEYWORDS else (120, 190, 255) if tok[:1].isupper() \
                        else (225, 230, 240)
                    if '"' in tok:
                        colour = (160, 220, 120)
                    d.text((x, y), tok, font=f, fill=colour)
                    x += f.getlength(tok + " ")
            y += 46
        return arr(im)

    def draw(self, c, f, edit):
        chars = int((f - self.f0) * self.cps / edit.fps)
        paste(c, self.panel(chars), self.x, self.y, clamp((f - self.f0) / 6))


class Fn(Overlay):
    """Anything else: fn(canvas, f, overlay, edit) draws in place."""

    def __init__(self, f0, f1, fn):
        super().__init__(f0, f1)
        self.fn = fn

    def draw(self, c, f, edit):
        self.fn(c, f, self, edit)


# ----------------------------------------------------------------------------- transitions
# Each takes (old, new, f, t0, dur, edit, direction) and returns the blended frame.


def wipe(old, new, f, t0, dur, edit, direction=None, colours=(BLUE, YELLOW, RED), lean=0.42, band=70):
    """Slanted colour bands sweep left to right and the new shot follows them: the chapter marker."""
    xs, ys, _ = edit.grids()
    u = smooth((f - t0) / dur)
    total = band * len(colours) + 600
    edge = -total + (edit.W + total + 600) * u
    bound = xs - (edge + lean * (ys - edit.H / 2))
    if direction == "left":
        bound = (edit.W - xs) - (edge + lean * (ys - edit.H / 2))
    out = np.where((bound > 0)[..., None], old, new)
    for k, col in enumerate(colours):
        m = (bound <= -k * band) & (bound > -(k + 1) * band - (25 if k == len(colours) - 1 else 0))
        out[m] = np.array(col, np.float32)
    return out


def iris(old, new, f, t0, dur, edit, direction=None, colour=GOLD, highlight=(255, 244, 160)):
    """A Giant Ring opening from the centre: the new shot inside a gold ring that grows."""
    _, _, dist = edit.grids()
    u = ease_out((f - t0) / dur)
    r = (math.hypot(edit.W, edit.H) / 2 + 40) * u  # the ring leaves the corners before the end
    out = np.where((dist < r)[..., None], new, old)
    out[(dist >= r - 34) & (dist < r)] = np.array(colour, np.float32)
    out[(dist >= r - 22) & (dist < r - 12)] = np.array(highlight, np.float32)
    return out


def crossfade(old, new, f, t0, dur, edit, direction=None):
    u = smooth((f - t0) / dur)
    return old * (1 - u) + new * u


def push(old, new, f, t0, dur, edit, direction="left", seam=YELLOW, seam_px=10):
    """The new shot shoves the old one off screen (direction it travels: left/right/up/down), with
    a coloured seam between them. Use the direction of on-screen motion (running right -> push left)."""
    u = ease_out((f - t0) / dur) if dur else 1.0
    out = np.empty_like(old)
    if direction in ("left", "right"):
        d = int(round(edit.W * u))
        if direction == "left":
            out[:, :edit.W - d] = old[:, d:]
            out[:, edit.W - d:] = new[:, :d]
            s = edit.W - d
        else:
            out[:, d:] = old[:, :edit.W - d]
            out[:, :d] = new[:, edit.W - d:]
            s = d
        if 0 < d < edit.W:
            out[:, max(0, s - seam_px // 2):min(edit.W, s + seam_px // 2)] = np.array(seam, np.float32)
    else:
        d = int(round(edit.H * u))
        if direction == "up":
            out[:edit.H - d] = old[d:]
            out[edit.H - d:] = new[:d]
            s = edit.H - d
        else:
            out[d:] = old[:edit.H - d]
            out[:d] = new[edit.H - d:]
            s = d
        if 0 < d < edit.H:
            out[max(0, s - seam_px // 2):min(edit.H, s + seam_px // 2)] = np.array(seam, np.float32)
    return out


def shutter(old, new, f, t0, dur, edit, direction=None, bands=8, stagger=0.5):
    """Horizontal slices of the new shot slide in from alternating sides, top band first."""
    xs, ys, _ = edit.grids()
    out = old.copy()
    bh = edit.H / bands
    span = max(1e-6, 1 - stagger)
    for k in range(bands):
        u = ease_out(((f - t0) / dur - stagger * k / max(1, bands - 1)) / span)
        if u <= 0:
            continue
        y0, y1 = int(k * bh), int((k + 1) * bh)
        reach = int(edit.W * u)
        if reach <= 0:
            continue
        if k % 2 == 0:
            out[y0:y1, :reach] = new[y0:y1, edit.W - reach:]
        else:
            out[y0:y1, edit.W - reach:] = new[y0:y1, :reach]
    return out


def slash(old, new, f, t0, dur, edit, direction=None, lean=0.6, colour=WHITE):
    """One hard diagonal cut that sweeps across with a bright edge: faster and sharper than wipe."""
    xs, ys, _ = edit.grids()
    u = ease_in((f - t0) / dur) * 0.4 + smooth((f - t0) / dur) * 0.6
    span = edit.W + lean * edit.H
    edge = -lean * edit.H / 2 + span * u
    bound = xs - (edge + lean * (ys - edit.H / 2))
    out = np.where((bound > 0)[..., None], old, new)
    out[np.abs(bound) < 6] = np.array(colour, np.float32)
    return out


def whip(old, new, f, t0, dur, edit, direction="left", blur=18):
    """A push with horizontal motion blur through the middle of the move: a fast camera whip."""
    out = push(old, new, f, t0, dur, edit, direction, seam_px=0)
    u = (f - t0) / max(1, dur)
    if 0.15 < u < 0.85:
        im = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))
        k = int(blur * math.sin(math.pi * (u - 0.15) / 0.7)) or 1
        im = im.resize((max(1, edit.W // k), edit.H), Image.BOX).resize((edit.W, edit.H), Image.BILINEAR)
        out = arr(im)
    return out


TRANSITIONS = {"wipe": wipe, "whip": whip, "iris": iris, "xfade": crossfade, "push": push, "shutter": shutter,
               "slash": slash}

# ------------------------------------------------------------------------------- layouts


def stripes(edit, f, c0=(10, 20, 64), c1=(16, 30, 90), width=70, speed=2.5):
    """Moving diagonal stripes: a backdrop with energy for split and grid screens."""
    xs, ys, _ = edit.grids()
    m = (((xs + ys * 0.6 + f * speed) // width) % 2)[..., None]
    return np.where(m > 0, np.array(c1, np.float32), np.array(c0, np.float32)).astype(np.float32)


def slanted_split(edit, f, panes, slant=0.22, divider=YELLOW, divider_px=14, drop_frames=16, bg=None):
    """N full-height slices with leaning dividers. panes: list of (start_frame, image_fn, label)
    where image_fn(local_frame) returns a full-screen float RGB array. Each pane is shifted so its
    screen centre sits in the middle of its slice (frame the subject at screen centre); slices
    drop in from above when they start; labels sit on a pill near the bottom."""
    c = stripes(edit, f) if bg is None else bg.copy()
    xs, ys, _ = edit.grids()
    n = len(panes)
    edges = [k * edit.W / n + slant * (ys - edit.H / 2) for k in range(1, n)]
    for i, (t0, image_fn, label) in enumerate(panes):
        if f < t0:
            continue
        drop = int(round(-(1 - ease_out((f - t0) / drop_frames)) * edit.H))
        shift = int(round((i + 0.5) * edit.W / n - edit.W / 2))
        src = np.roll(np.roll(image_fn(f - t0), shift, axis=1), drop, axis=0)
        lo = edges[i - 1] if i > 0 else -np.inf
        hi = edges[i] if i < n - 1 else np.inf
        m = (xs >= lo) & (xs < hi) & (ys < edit.H + drop)
        c[m] = src[m]
        if i > 0:
            c[(np.abs(xs - edges[i - 1]) < divider_px / 2) & (ys < edit.H + drop)] = np.array(divider, np.float32)
        if label:
            pill = chip(label)
            cx = (i + 0.5) * edit.W / n + slant * (edit.H * 0.91 - edit.H / 2)
            paste(c, pill, cx - pill.shape[1] / 2, edit.H * 0.89 + drop, clamp((f - t0 - 8) / 8))
    return c


def stacked_split(edit, f, panes, divider=YELLOW, divider_px=10, slide_frames=16, bg=None):
    """Full-width strips stacked top to bottom, for comparing horizontal routes. A slanted split
    crops each pane to a narrow slice; a strip keeps the whole run in view, so two 800x224
    captures (--width 800) fill 1920x1080 at 2.4x. panes: (start_frame, clip, src, label, cam)
    with cam=(cx, cy, zoom) or None. Each strip slides in from the left on its start frame."""
    c = stripes(edit, f) if bg is None else bg.copy()
    n = len(panes)
    sh = (edit.H - (n - 1) * divider_px) // n
    for i, (t0, clip, src, label, cam) in enumerate(panes):
        if f < t0:
            continue
        cx, cy, z = cam if cam else (None, None, 1.0)
        strip = arr(game_view(clip.frame(src + f - t0), cx, cy, z, edit.W, sh))
        x = int(round(-(1 - ease_out((f - t0) / slide_frames)) * edit.W))
        y = i * (sh + divider_px)
        vis = edit.W + x
        if vis > 0:
            c[y:y + sh, :vis] = strip[:, -x:]
            if i > 0:
                c[y - divider_px:y, :vis] = np.array(divider, np.float32)
        if label:
            pill = chip(label)
            paste(c, pill, 40 + x, y + sh - pill.shape[0] - 24, clamp((f - t0 - 8) / 8))
    return c


def pop_panel(c, img, x, y, f, t0, label=None, border=5, colour=SKY, dur=10):
    """A picture in a coloured frame that pops (overshoot scale) into place at t0, with an
    optional label fading in beneath."""
    u = (f - t0) / dur
    fr = framed(img, border, colour)
    s = 0.86 + 0.14 * back(u, 2.0) if u < 1 else 1.0
    if s != 1.0:
        fr = scaled(fr, s)
    w, h = img.width + 2 * border, img.height + 2 * border
    paste(c, fr, x + (w - fr.shape[1]) / 2, y + (h - fr.shape[0]) / 2, clamp(u * 3))
    if label:
        lab = text(label, "title_it", 44, WHITE, stroke=4, shadow=4)
        paste(c, lab, x + w / 2 - lab.shape[1] / 2, y + h + 10, clamp((u - 0.4) * 2))


def grid(edit, f, cells, cols, rows, gap=30, margin=14, label_h=None, bg=None, border=5, colour=SKY):
    """Cells pop in one by one on their own start frames. cells: (start_frame, clip, src, label,
    cam) with cam=(cx, cy, zoom) or None. Cell size keeps 16:9 inside the available space."""
    c = stripes(edit, f) if bg is None else bg.copy()
    if label_h is None:
        label_h = 70 if any(cell[3] for cell in cells) else 0
    cw = (edit.W - 2 * margin - (cols - 1) * gap) / cols - 2 * border
    ch = (edit.H - 2 * margin - (rows - 1) * (gap + label_h)) / rows - 2 * border - label_h
    cw, ch = int(min(cw, ch * 16 / 9)), int(min(ch, cw * 9 / 16))
    left = (edit.W - cols * (cw + 2 * border) - (cols - 1) * gap) / 2
    top = (edit.H - rows * (ch + 2 * border + label_h) - (rows - 1) * gap) / 2
    for i, (t0, clip, src, label, cam) in enumerate(cells):
        if f < t0:
            continue
        cx, cy, z = cam if cam else (None, None, 1.0)
        img = game_view(clip.frame(src + f - t0), cx, cy, z, cw, ch)
        x = left + (i % cols) * (cw + 2 * border + gap)
        y = top + (i // cols) * (ch + 2 * border + label_h + gap)
        pop_panel(c, img, x, y, f, t0, label, border, colour)
    return c


def vertical_slices(edit, f, parts, dim=0.38, drift=20, zoom=0.08, u=0.0):
    """Equal vertical strips of different clips in motion, dimmed: a wall behind a statement.
    parts: (clip, src, cx, cy) per strip; u is shot progress for the slow drift."""
    c = np.zeros((edit.H, edit.W, 3), np.float32)
    sw = edit.W // len(parts)
    for k, (clip, src, cx, cy) in enumerate(parts):
        im = game_view(clip.frame(src + f), cx - drift / 2 + drift * u, cy, 1.0 + zoom * u, sw, edit.H)
        c[:, k * sw:(k + 1) * sw] = arr(im)
    return c * dim


def blurred(img, edit, radius=3, dim=0.42, cx=None, cy=None, zoom=1.35):
    """A blurred, darkened, pushed-in backdrop for an end card."""
    return arr(game_view(img.filter(ImageFilter.GaussianBlur(radius)), cx, cy, zoom, edit.W, edit.H)) * dim


# ---------------------------------------------------------------------------------- audio

SR = 48000


def read_wav(path):
    """A 16-bit WAV as float stereo at its own rate, DC removed."""
    with wave.open(str(path)) as w:
        ch, rate = w.getnchannels(), w.getframerate()
        a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    a = a.reshape(-1, ch)
    if ch == 1:
        a = np.repeat(a, 2, axis=1)
    if rate != SR:
        n = int(len(a) * SR / rate)
        t = np.linspace(0, len(a) - 1, n)
        a = np.stack([np.interp(t, np.arange(len(a)), a[:, k]) for k in range(2)], axis=1).astype(np.float32)
    return a - a.mean(axis=0)


def write_wav(path, a):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(a, -1, 1) * 32767).astype(np.int16).tobytes())


def master(out, target_rms_db=-16.0, knee=0.80, ceiling=0.93):
    """Loudness to a target RMS, then a tanh soft knee and a ceiling, so stacked SFX on a music
    bed never clip. Measure the encoded AAC peak afterwards as well."""
    rms = np.sqrt((out ** 2).mean())
    if rms > 0:
        out = out * (10 ** (target_rms_db / 20) / rms)
    over = np.abs(out) > knee
    out[over] = np.sign(out[over]) * (knee + (1 - knee) * np.tanh((np.abs(out[over]) - knee) / (1 - knee)))
    return out * ceiling


def onsets(x, sr=SR, hop=480):
    n = 2048
    frames = np.lib.stride_tricks.sliding_window_view(x, n)[::hop] * np.hanning(n)
    mag = np.log1p(np.abs(np.fft.rfft(frames, axis=1)) * 10)
    flux = np.maximum(mag[1:] - mag[:-1], 0).sum(axis=1)
    flux -= np.convolve(flux, np.ones(16) / 16, mode="same")
    return np.maximum(flux, 0), sr / hop


def tempo(x, sr=SR, start=0.0, length=40.0, lo_bpm=70, hi_bpm=200):
    """BPM and beat phase (the first beat at or after `start`) of a mono signal: spectral-flux
    onsets, autocorrelation for the period, then a fine period/phase comb search. Flux frames are
    indexed by window start, so the phase is shifted by half a window plus one hop (about 31 ms
    at 48 kHz) to land on the onset itself. The archived Slay beats.py indexes the same way without
    the shift; confirm any inherited phase by ear."""
    seg = x[int(start * sr):int((start + length) * sr)]
    env, fps = onsets(seg, sr)
    ac = np.correlate(env, env, mode="full")[len(env) - 1:]
    lags = np.arange(len(ac)) / fps
    ok = (lags > 60 / hi_bpm) & (lags < 60 / lo_bpm)
    lag = np.argmax(np.where(ok, ac, 0))
    best = None
    for period in np.linspace(lag / fps * 0.97, lag / fps * 1.03, 400):
        for ph in np.linspace(0, period, 60, endpoint=False):
            idx = (np.arange(ph, len(env) / fps, period) * fps).astype(int)
            idx = idx[idx < len(env)]
            s = env[idx].mean()
            if best is None or s > best[0]:
                best = (s, period, ph)
    _, period, ph = best
    ph = (ph + (2048 / 2 + 480) / sr) % period
    return 60 / period, start + ph


def sections(x, bpm, first, sr=SR, k=8, top=14):
    """Section changes, in beats: checkerboard novelty over beat-synchronous spectra. Put chapter
    changes (headers, wipes) on these; put ordinary cuts on bars between them."""
    p = 60 / bpm
    beats = np.arange(first, len(x) / sr - p, p)
    feats = []
    for a in beats:
        seg = x[int(a * sr):int((a + p) * sr)]
        s = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), 8192))[:2000]
        s = np.log1p(s.reshape(100, 20).sum(axis=1))
        feats.append(s / (np.linalg.norm(s) + 1e-9))
    F = np.array(feats)
    S = F @ F.T
    g = np.outer(np.r_[-np.ones(k), np.ones(k)], np.r_[-np.ones(k), np.ones(k)])
    nov = np.zeros(len(F))
    for i in range(k, len(F) - k):
        nov[i] = (S[i - k:i + k, i - k:i + k] * g).sum()
    picked = []
    for i in np.argsort(nov)[::-1]:
        if all(abs(i - j) > k for j in picked):
            picked.append(int(i))
        if len(picked) >= top:
            break
    return [(i, float(beats[i]), float(nov[i])) for i in sorted(picked)]


# ------------------------------------------------------------------------------------- edit


class Edit:
    """The whole promo: shots, overlays, sound effects and audio beds on one beat clock.
    The edit file's build(edit) fills it in; everything else is rendering."""

    def __init__(self, bpm=120.0, first=0.0, fps=60, size=(1920, 1080), out="promo.mp4", work="build"):
        self.beats = Beats(bpm, first, fps)
        self.fps, (self.W, self.H) = fps, size
        self.out, self.work = Path(out), Path(work)
        self.shots, self.overlays, self.sfx, self.beds, self.stems, self.punches = [], [], [], [], [], []
        self.total = None

    # placement helpers
    def bf(self, k):
        return self.beats.bf(k)

    def S(self, shot):
        self.shots.append(shot)
        return shot

    def O(self, overlay):
        self.overlays.append(overlay)
        return overlay

    def X(self, frame, wav, gain=1.0, length=None):
        """A sound effect (WAV path) starting at an output frame."""
        self.sfx.append((frame, str(wav), gain, length))

    def bed(self, wav, at_frame=0, gain=1.0, src_seconds=0.0, length=None, fade_in=0.0, fade_out=0.05):
        """A music bed: plays wav from src_seconds, starting at an output frame."""
        self.beds.append((str(wav), at_frame, gain, src_seconds, length, fade_in, fade_out))

    def stem(self, wav, src_frame, at_frame, frames, gain=0.7):
        """A clip's own audio under a composed screen (grid/split), aligned by clip frame."""
        self.stems.append((str(wav), src_frame, at_frame, frames, gain))

    def punch(self, f):
        """Zoom multiplier from the punch list: a quick kick on a hit that decays over 14 frames."""
        z = 1.0
        for pf, amp in self.punches:
            d = f - pf
            if 0 <= d < 14:
                z += amp * (1 - d / 14) ** 2
        return z

    # cached screen-space helpers
    @lru_cache(maxsize=None)
    def grids(self):
        ys, xs = np.mgrid[0:self.H, 0:self.W].astype(np.float32)
        return xs, ys, np.hypot(xs - self.W / 2, ys - self.H / 2)

    @lru_cache(maxsize=None)
    def top_shade(self, reach=0.3, peak=190):
        a = np.zeros((self.H, self.W, 4), np.float32)
        a[..., 3] = (np.clip(1 - np.arange(self.H) / (self.H * reach), 0, 1) ** 1.3 * peak)[:, None]
        return a

    @lru_cache(maxsize=None)
    def bottom_shade(self, reach=0.45, peak=200):
        a = np.zeros((self.H, self.W, 4), np.float32)
        ramp = np.clip((np.arange(self.H) - self.H * (1 - reach)) / (self.H * reach), 0, 1) ** 1.2 * peak
        a[..., 3] = ramp[:, None]
        return a

    @lru_cache(maxsize=None)
    def red_banner(self):
        """Stand-in for the ROM title-card banner: a tall red bar with a dark inner edge."""
        a = np.zeros((560, 170, 4), np.float32)
        a[..., :3] = RED
        a[..., 3] = 255
        a[:, -18:, :3] = (150, 10, 10)
        return a

    def __hash__(self):
        return id(self)

    # rendering
    def end(self):
        return self.total if self.total is not None else max(s.f1 for s in self.shots)

    def shot_at(self, f):
        cur = None
        for s in self.shots:
            if s.f0 <= f < s.f1:
                cur = s
        return cur

    def render_frame(self, f):
        s = self.shot_at(f)
        if s is None:
            c = np.zeros((self.H, self.W, 3), np.float32)
        else:
            c = s.image(f, self)
            idx = self.shots.index(s)
            if s.trans and f < s.f0 + s.tdur and idx > 0:
                prev = self.shots[idx - 1]
                c = TRANSITIONS[s.trans](prev.image(f, self), c, f, s.f0, s.tdur, self, s.tdir)
        for o in self.overlays:
            if o.f0 <= f < o.f1:
                o.draw(c, f, self)
        return np.clip(c, 0, 255).astype(np.uint8)

    def check(self):
        """Edit-list sanity: ordered, gap-free shots; returns warnings rather than raising."""
        warn = []
        prev = None
        for s in self.shots:
            if s.f1 <= s.f0:
                warn.append(f"empty shot at {s.f0}")
            if prev is not None and s.f0 > prev.f1:
                warn.append(f"gap {prev.f1}-{s.f0} (black frames)")
            if prev is not None and s.f0 < prev.f0:
                warn.append(f"shot at {s.f0} starts before the previous shot")
            if isinstance(s, Game) and s.hold is None and s.src + (s.f1 - s.f0) > len(s.clip):
                warn.append(f"shot {s.f0}-{s.f1} runs {s.src + s.f1 - s.f0 - len(s.clip)} frames past "
                            f"{s.clip.path.name} (holds last frame)")
            prev = s
        warn += self.pacing()[1]
        return warn

    def pacing(self, window_s=20, max_cuts=10, min_mean_s=2.0):
        """Cut density: the archived promos that were rejected as 'rushed' or 'cluttered' averaged
        about 1.2 s a shot and had 16 cuts in their last 20 s; their accepted revisions held
        shots for about 4 s. Returns (stats, warnings)."""
        cuts = sorted(s.f0 for s in self.shots[1:])
        total = self.end()
        mean = total / self.fps / max(1, len(self.shots))
        busiest = max([sum(1 for c in cuts if a <= c < a + window_s * self.fps) for a in cuts] or [0])
        last = sum(1 for c in cuts if c >= total - window_s * self.fps)
        warn = []
        if busiest > max_cuts:
            warn.append(f"{busiest} cuts within {window_s} s: likely to read as rushed")
        if mean < min_mean_s:
            warn.append(f"mean shot {mean:.2f} s: below {min_mean_s} s")
        return {"shots": len(self.shots), "mean_shot_s": round(mean, 2), "busiest_window_cuts": busiest,
                "cuts_in_last_window": last, "seconds": round(total / self.fps, 2)}, warn

    def mix(self):
        total = self.end()
        spf = SR // self.fps
        out = np.zeros((total * spf + SR, 2), np.float32)

        def add(a, at, gain=1.0, fin=0, fout=0):
            at = int(at)
            if at < 0:
                a, at = a[-at:], 0
            a = a[:max(0, len(out) - at)].copy()
            if not len(a):
                return
            fin, fout = min(fin, len(a)), min(fout, len(a))
            if fin:
                a[:fin] *= np.linspace(0, 1, fin)[:, None]
            if fout:
                a[-fout:] *= np.linspace(1, 0, fout)[:, None]
            out[at:at + len(a)] += a * gain

        for wav, at, gain, src, length, fin, fout in self.beds:
            a = read_wav(wav)[int(src * SR):]
            if length is not None:
                a = a[:int(length * SR)]
            add(a, at * spf, gain, int(fin * SR), int(fout * SR))
        for s in self.shots:
            if isinstance(s, Game) and s.stems and s.wav:
                a = read_wav(s.wav)
                a0 = s.src_frame(s.f0) * spf
                add(a[a0:a0 + (s.f1 - s.f0) * spf], s.f0 * spf, s.stems, 240, 240)
        for wav, src, at, n, gain in self.stems:
            add(read_wav(wav)[src * spf:(src + n) * spf], at * spf, gain, 240, 240)
        for f, wav, gain, length in self.sfx:
            a = read_wav(wav)
            if length is not None:
                a = a[:int(length * SR)]
            add(a, f * spf, gain, 0, min(2400, len(a)))
        return master(out[:total * spf])

    def contact_sheet(self, path, step=60, first=0, last=None, workers=None):
        last = self.end() if last is None else min(last, self.end())
        fs = list(range(first, last, step))
        ims = [self.render_frame(f) for f in fs] if workers == 0 else _pool_map(self, fs, workers)
        cols, tw, th = 5, 384, 216
        sheet = Image.new("RGB", (tw * cols, th * ((len(fs) + cols - 1) // cols)))
        for i, (f, im) in enumerate(zip(fs, ims)):
            t = Image.fromarray(im).resize((tw, th), Image.BILINEAR)
            ImageDraw.Draw(t).text((4, 2), f"{f} ({f / self.fps:.2f}s)", fill="yellow")
            sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
        sheet.save(path)
        return path

    def render(self, workers=None, crf=14):
        self.work.mkdir(parents=True, exist_ok=True)
        total = self.end()
        audio = self.work / "promo-audio.wav"
        write_wav(audio, self.mix())
        video = self.work / "promo-video.mp4"
        enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                                "-s", f"{self.W}x{self.H}", "-r", str(self.fps), "-i", "-", "-c:v", "libx264",
                                "-preset", "slow", "-crf", str(crf), "-tune", "animation", "-pix_fmt", "yuv420p",
                                str(video)], stdin=subprocess.PIPE)
        if workers == 0:
            for f in range(total):
                enc.stdin.write(self.render_frame(f).tobytes())
        else:
            with Pool(workers or min(8, os.cpu_count() or 1), initializer=_init_worker, initargs=(_EDIT_SPEC,)) as pool:
                for k, b in enumerate(pool.imap(_frame_bytes, range(total), chunksize=12)):
                    enc.stdin.write(b)
                    if k % 600 == 0:
                        print(f"frame {k}/{total}", flush=True)
        enc.stdin.close()
        if enc.wait():
            raise RuntimeError("ffmpeg video encode failed")
        self.out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-i", str(audio), "-c:v", "copy",
                        "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", "-shortest", str(self.out)],
                       check=True)
        return self.out


# ------------------------------------------------------------------- loading and workers

_EDIT_SPEC = None
_WORKER_EDIT = None


def load_edit(path):
    """Imports an edit file and runs its build(edit); the file may set EDIT = dict(...) for the
    Edit constructor arguments (bpm, first, fps, size, out, work)."""
    global _EDIT_SPEC
    path = Path(path).resolve()
    spec = importlib.util.spec_from_file_location(f"promo_edit_{abs(hash(str(path)))}", path)
    mod = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(path.parent))
    spec.loader.exec_module(mod)
    kwargs = dict(getattr(mod, "EDIT", {}))
    kwargs.setdefault("out", path.parent / "promo.mp4")
    kwargs.setdefault("work", path.parent / "build")
    edit = Edit(**kwargs)
    total = mod.build(edit)
    if total is not None:
        edit.total = total
    _EDIT_SPEC = str(path)
    return edit


def _init_worker(spec):
    global _WORKER_EDIT
    _WORKER_EDIT = load_edit(spec)


def _frame_bytes(f):
    return _WORKER_EDIT.render_frame(f).tobytes()


def _frame_array(f):
    return _WORKER_EDIT.render_frame(f)


def _pool_map(edit, fs, workers):
    if _EDIT_SPEC is None:
        return [edit.render_frame(f) for f in fs]
    with Pool(workers or min(8, os.cpu_count() or 1), initializer=_init_worker, initargs=(_EDIT_SPEC,)) as pool:
        return pool.map(_frame_array, fs)


# ------------------------------------------------------------------------------- demo/template

TEMPLATE = '''#!/usr/bin/env python3
"""<Production> promo edit. Rebuild: python3 tools/media/promo_kit.py render THIS_FILE.
Creative brief (keep it here, one line each):
  audience / promise / hero moment / music + BPM / palette / title face / end card.
"""
import sys
sys.path.insert(0, "/ABSOLUTE/PATH/TO/OpenGGF/tools/media")
from promo_kit import *

# bpm/first come from `promo_kit.py beats music.wav`; place every cut in beats.
EDIT = dict(bpm=171.107, first=0.316, fps=60, size=(1920, 1080), out="out/promo.mp4", work="build")

CAP = "/ABSOLUTE/task/captures"
title = Frames(f"{CAP}/title/frames")
run = Frames(f"{CAP}/run/frames")


def build(e):
    b = e.bf
    # A. cold open: strongest motion first, a promise in one line
    e.S(Game(0, b(8), run, src=120, cam=cam_move((200, 112, 1.25), (230, 112, 1.0)), dim=0.35))
    for k, word in enumerate(["FASTER.", "LOUDER.", "YOURS."]):
        e.O(Txt(b(1 + k * 1.5), b(8), text(word, "title", 190, WHITE, stroke=9, shadow=10),
                e.W / 2, 300 + k * 210, enter="pop", centre=True))
    # B. title drop on the downbeat of a new section
    e.S(Game(b(8), b(24), title, src=0, cam=cam_slam(1.3, 26, b(24) - b(8)), trans="wipe", tdur=14))
    e.O(Flash(b(8), 18))
    # C. chapters: Header + one take each; splits/grids for short pieces
    # D. end card: logo, one line, URL, disclaimer; hold 5-7 s, fade out
    # e.bed("audio/music.wav", 0, 1.0, fade_out=2.0)   # one bed, chapter changes on its sections
    # e.X(b(8), "audio/sfx-spindash.wav", 0.8)          # ROM stingers on transitions only
    return b(24)
'''


def synth_clip(path, n, hue, size=(400, 224)):
    """Deterministic stand-in footage: a scrolling checker 'level', a ground band and a 'player'
    block, so layouts and timing can be checked without a ROM."""
    path = Path(path)
    path.mkdir(parents=True, exist_ok=True)
    w, h = size
    ys, xs = np.mgrid[0:h, 0:w]
    base = np.array([(hue * 97) % 256, (hue * 53 + 80) % 256, (hue * 29 + 160) % 256], np.uint8)
    for i in range(n):
        p = path / f"frame-{i:05d}.png"
        if p.exists():
            continue
        chk = (((xs + i * 3) // 16 + ys // 16) % 2).astype(np.uint8)
        img = np.where(chk[..., None] > 0, base, (base * 0.6).astype(np.uint8))
        img[h - 40:] = (40, 160, 40)
        bx = int(w / 2 + 120 * math.sin(i / 25))
        img[h - 72:h - 40, bx - 12:bx + 12] = (20, 60, 230)
        Image.fromarray(img.astype(np.uint8)).save(p)
    return Frames(path)


def synth_click(path, bpm, seconds, first=0.25):
    """A click track with an accent each bar and a timbre change half way (a 'section')."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros(n, np.float32)
    period = 60 / bpm
    k = 0
    while first + k * period < seconds:
        s = int((first + k * period) * SR)
        ln = min(n - s, 2400)
        freq = (1200 if k % 4 == 0 else 800) * (1.5 if first + k * period > seconds / 2 else 1.0)
        out[s:s + ln] += np.sin(2 * np.pi * freq * t[:ln]) * np.exp(-t[:ln] * 60) * 0.6
        k += 1
    write_wav(path, np.stack([out, out], axis=1))
    return path


def demo_edit(e, root):
    """The vocabulary on synthetic footage: every transition, split, grid and type move once."""
    root = Path(root)
    clips = [synth_clip(root / f"clip{k}", 240, k + 1) for k in range(4)]
    click = synth_click(root / "click.wav", e.beats.bpm, 30, e.beats.first)
    b = e.bf
    e.bed(click, 0, 0.8, fade_out=0.5)
    e.S(Game(0, b(4), clips[0], cam=cam_move((200, 112, 1.3), (200, 112, 1.0)), dim=0.4))
    for k, word in enumerate(["EVERY", "MOVE,", "ONCE."]):
        e.O(Txt(b(k * 1.0), b(4), text(word, "title", 170, WHITE, stroke=9, shadow=10), e.W / 2, 300 + k * 200,
                enter="pop", centre=True))
    panes = [(b(4 + 2 * i), (lambda lf, c=c: arr(game_view(c.frame(lf), None, None, 1.0, e.W, e.H))), name)
             for i, (c, name) in enumerate(zip(clips[:3], ["SONIC 1", "SONIC 2", "SONIC 3 & K"]))]
    e.S(Custom(b(4), b(12), lambda f, s, ed: slanted_split(ed, f, panes), trans="slash", tdur=12))
    e.S(Game(b(12), b(18), clips[1], cam=cam_slam(1.3, 26, b(18) - b(12)), trans="wipe", tdur=14))
    e.O(Flash(b(12), 18))
    e.O(Header(b(12.5), b(17), "CHAPTER TITLE", "A subtitle that sells the feature"))
    cells = [(b(18 + i), clips[i % 4], 0, f"CELL {i + 1}", None) for i in range(4)]
    e.S(Custom(b(18), b(24), lambda f, s, ed: grid(ed, f, cells, 2, 2), trans="push", tdur=14, tdir="left"))
    e.S(Custom(b(24), b(30), lambda f, s, ed: vertical_slices(ed, f - s.f0, [(c, 0, 200, 112) for c in clips[:3]],
                                                               u=s.u(f)), trans="shutter", tdur=18))
    for k, (num, label) in enumerate([(139, "CARDS"), (55, "RELICS"), (22, "EVENTS")]):
        e.O(Counter(b(24) + k * 8, b(30), num, label, 320 + k * 640, 360))
    e.S(Game(b(30), b(36), clips[3], trans="iris", tdur=20))
    e.O(Txt(b(30) + 8, b(36), text("MAKE YOUR OWN", "title", 130, WHITE, stroke=8, shadow=9), 60, 70, enter="left"))
    e.O(Txt(b(31), b(36), text("WIDE-TRACKED KICKER", "sub", 40, YELLOW, stroke=3, shadow=4, tracking=10), 64, 232,
            enter="wipe", dur=16))
    e.O(Fade(b(36) - 30, b(36) + 1))
    return b(36)


def main(argv):
    cmd = argv[1] if len(argv) > 1 else ""
    if cmd == "template":
        Path(argv[2]).write_text(TEMPLATE)
        print(argv[2])
    elif cmd == "fonts":
        for role, name, state in fonts_report():
            print(f"{role:9s} {name:42s} {state}")
    elif cmd == "fetch-fonts":
        fetch_fonts(argv[2])
        print(f"export PROMO_FONT_DIRS={Path(argv[2]).resolve()}")
    elif cmd == "beats":
        x = read_wav(argv[2]).mean(axis=1)
        bpm, first = tempo(x)
        print(f"{bpm:.3f} BPM, first beat {first:.3f}s, beat period {60 / bpm:.4f}s")
        for i, t, nov in sections(x, bpm, first):
            print(f"section change at beat {i:4d}  t={t:6.2f}s  novelty={nov:.2f}")
    elif cmd == "demo":
        root = Path(argv[2])
        e = Edit(bpm=120, first=0.25, out=root / "demo.mp4", work=root / "build")
        e.total = demo_edit(e, root)
        for w in e.check():
            print("warning:", w)
        print("pacing:", e.pacing()[0])
        e.contact_sheet(root / "demo-sheet.png", step=30, workers=0)
        print(e.render(workers=0))
    elif cmd in ("sheet", "frames", "audio", "render", "check"):
        e = load_edit(argv[2])
        for w in e.check():
            print("warning:", w)
        print("pacing:", e.pacing()[0])
        if cmd == "sheet":
            step = int(argv[3]) if len(argv) > 3 else 60
            first = int(argv[4]) if len(argv) > 4 else 0
            last = int(argv[5]) if len(argv) > 5 else None
            e.work.mkdir(parents=True, exist_ok=True)
            print(e.contact_sheet(e.work / f"sheet-{first}.png", step, first, last))
        elif cmd == "frames":
            e.work.mkdir(parents=True, exist_ok=True)
            for a in argv[3:]:
                p = e.work / f"frame-{int(a):05d}.png"
                Image.fromarray(e.render_frame(int(a))).save(p)
                print(p)
        elif cmd == "audio":
            e.work.mkdir(parents=True, exist_ok=True)
            a = e.mix()
            write_wav(e.work / "promo-audio.wav", a)
            print(f"audio {len(a) / SR:.2f}s peak {np.abs(a).max():.3f}")
        elif cmd == "render":
            print(e.render(), f"{e.end() / e.fps:.2f}s")
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    if not shutil.which("ffmpeg"):
        print("warning: ffmpeg not found; render will fail", file=sys.stderr)
    sys.exit(main(sys.argv))
