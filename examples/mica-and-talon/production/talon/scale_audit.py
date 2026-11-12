#!/usr/bin/env python3
"""Measure TALON's figure scale per frame and rebuild the drawings that are off his 37 px rung.

Purpose: the owner found his poses "still seem to have varying sizes". Frame height cannot tell: a
spread wing or a crouch changes it without changing the bird. This measures rigid parts of his head
instead (goggle lens and beak) on each frame's high-resolution source drawing, divides them by that
frame's own reduction factor and compares them with WAIT. Drawings outside the tolerance are reduced
again from the same source at the corrected factor, so no pixel is invented; the one drawing with
no source (SPRING) is resized from its installed pixels by the rung's route 2.
Inputs: scale-audit.json beside this file (the measurement table, decisions and rebuilt sizes).
`measure` and `rebuild` also need the external source drawings the production records name
(family-generation.json sources under $HOME/.codex/generated_images, and the lanes' scratch under
$HOME/scratch/agent-tmp: talon-art-20261109, talon-art-r2-20261109, talon-object-poses-20261110,
mt-carry-20261110). They are not in the repository; --check needs none of them.
Origin: owner size feedback on PR #287, 2026-11-12. Pillow; no ROM or Java needed.
Run from the repository root:
  scale_audit.py measure   re-measure every source and rewrite the table in scale-audit.json
  scale_audit.py rebuild   reduce the out-of-tolerance drawings again and hand them to the cleanups
                           (run pose_cleanup.py and tonal_reshade.py after it; repeating it is safe)
  scale_audit.py --check   the tree still holds the rebuilt drawings the record describes

Measure. Two rigid head parts, each divided by the frame's reduction factor and by WAIT's median:
  band  thickness of the largest goggle lens (the orange blob; the beak is yellower, the strap
        browner): its pixels are projected on the blob's own principal axes and the 3rd-97th
        percentile extent across the short axis is taken. Independent of head tilt and of a
        profile or front view, but sheets draw the band thicker or thinner (WALK reads 1.09-1.17
        while standing exactly the reference height).
  span  that lens's centre to the far tip of the beak. Independent of how the band is drawn, but
        it shortens when the beak turns to or from the viewer, so a span out of proportion to
        its own band (outside 0.6-1.5) is rejected.
`head` is the geometric mean of the two (the one that exists where only one does): on WALK and
RUN, the two loops whose standing height is known to be right, it reads 1.05 and 1.03. Either
measure alone is good to about 15%, the mean to roughly 8%.

Decision, per source group (one animation's frames from one sheet):
  multiplier  the group's head (medians of its bands and spans) when every measured frame is
              within 8% of it, or the group was reduced at one factor: the animation keeps its
              internal proportions. Otherwise each frame's own head. An object pose needs a clean
              view of both parts: its multiplier is the mean of its own head and of `set`, the object
              set's median band and span over the frame's own factor.
  rebuild     when the multiplier is more than 8% above 1, except groups in UPRIGHT whose standing
              height is within 8% of 37 px. Ball drawings (ROLL, SPINDASH) have no comparable head.
              A reading more than 8% below 1 is listed but not acted on: both measures fail low (a
              part-hidden lens, a foreshortened beak), so a drawing is enlarged only by hand.
  by hand     HAND rejects a lens reading, HAND_KEEP a rebuild and HAND_SCALE sets a multiplier;
              HAND_READ holds the drawings with no usable source, read on the installed frame.
              Each entry says what was seen.
New factor = installed factor x multiplier. Anchors follow rescale-37.json: grounded and carrying
frames keep the sole's distance below the hotspot, hanging and carried frames keep their top, and
airborne and object poses scale about the hotspot.

Rebuild writes each drawing's new uncleaned rows into pose-cleanup.json or tonal-reshade.json (the
flight set, with its wing-tip boxes and shoe row moved to the new size), then pose_cleanup.py and
tonal_reshade.py finish them exactly as they finish the rest of the sheet.
"""
import argparse
import colorsys
import hashlib
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/talon/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/talon'
AUDIT = PROD / 'scale-audit.json'
HOME = Path.home()
SCRATCH = HOME / 'scratch/agent-tmp'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
import pipeline  # noqa: E402
from derive_slopes import scale2x  # noqa: E402

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
LENS_HUE = (0.085, 0.114)   # median hue of a lens blob; the beak is yellower (0.119-0.136), the strap browner
BOXED_HUE = 0.125           # inside a recorded goggle box there is no beak to mistake, and DROWN's lenses read 0.119
RUNG = 37 / 31            # the records hold factors in source pixels per 31 px rung pixel
TOLERANCE = 0.08
UPRIGHT = {'WAIT', 'WALK', 'RUN', 'LOOK_UP', 'BLINK', 'PUSH', 'BALANCE', 'BALANCE3', 'HANG2', 'SKID', 'HURT'}
BALL = {'ROLL', 'SPINDASH'}
TOP = {'HANG', 'TAILS_CARRIED'}
SOLE = {'VICTORY', 'BALANCE2', 'BALANCE4', 'DUCK', 'GET_UP', 'TAILS_FLY_CARRY', 'TAILS_FLY_CARRY_ASCEND',
        'TAILS_SWIM_CARRY'}
CARRY_ALLOWED = (2, 4, 6, 7, 8, 9, 10, 11, 12, 14)   # mt-carry-20261110/reduce.py
CARRY_SOLE = (254, 254, 254, 744, 744, 744)
STAMP = 'Scale rebuild 2026-11-12'
NOTE = (' Scale rebuild 2026-11-12 (scale_audit.py): redrawn from its source at x{factor:.2f} of its size, '
        'to the 37 px rung; see scale-audit.json.')


# ---------------------------------------------------------------- lens band on a source drawing
def lens_colour(p):
    r, g, b, a = p
    if a < 128:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.04 <= h <= 0.17 and s > 0.45 and v > 0.55


def glint(p):
    return p[3] >= 128 and min(p[0], p[1]) > 215


def components(mask):
    seen, out = set(), []
    for start in mask:
        if start in seen:
            continue
        patch, todo = [start], [start]
        seen.add(start)
        while todo:
            cx, cy = todo.pop()
            for a, b in N4:
                near = (cx + a, cy + b)
                if near in mask and near not in seen:
                    seen.add(near)
                    patch.append(near)
                    todo.append(near)
        out.append(patch)
    return out


def thickness(points):
    n = len(points)
    mx, my = sum(p[0] for p in points) / n, sum(p[1] for p in points) / n
    sxx = sum((p[0] - mx) ** 2 for p in points) / n
    syy = sum((p[1] - my) ** 2 for p in points) / n
    sxy = sum((p[0] - mx) * (p[1] - my) for p in points) / n
    angle = 0.5 * math.atan2(2 * sxy, sxx - syy)
    ux, uy = math.cos(angle), math.sin(angle)
    along = sorted((p[0] - mx) * ux + (p[1] - my) * uy for p in points)
    across = sorted(-(p[0] - mx) * uy + (p[1] - my) * ux for p in points)

    def extent(values):
        return values[int(0.97 * (n - 1))] - values[int(0.03 * (n - 1))] + 1
    return min(extent(along), extent(across))


def beak_colour(p):
    if p[3] < 128:
        return False
    h, s, v = colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)
    return 0.115 <= h <= 0.17 and s > 0.6 and v > 0.75


def head(figure, box=None, margin=10):
    """(lens band thickness, lens centre to beak tip), source pixels; None where the part is not in view.
    box is a recorded goggle box in the figure, where the production record has one."""
    width, height = figure.size
    px = figure.load()
    x0, y0, x1, y1 = 0, 0, width, height
    if box:
        x0, y0 = max(0, box[0] - margin), max(0, box[1] - margin)
        x1, y1 = min(width, box[2] + margin), min(height, box[3] + margin)
    mask = {(x, y) for y in range(y0, y1) for x in range(x0, x1) if lens_colour(px[x, y])}
    for _ in range(3):  # glints touching lens colour belong to the lens
        grown = {(x + a, y + b) for x, y in mask for a, b in N4
                 if x0 <= x + a < x1 and y0 <= y + b < y1 and (x + a, y + b) not in mask and glint(px[x + a, y + b])}
        if not grown:
            break
        mask |= grown
    best, lenses = None, set()
    for patch in components(mask):
        if len(patch) < 120:
            continue
        solid = set(patch)
        bx0, by0 = min(p[0] for p in patch), min(p[1] for p in patch)
        bx1, by1 = max(p[0] for p in patch), max(p[1] for p in patch)
        inside = {(x, y) for x in range(bx0, bx1 + 1) for y in range(by0, by1 + 1)} - solid
        border = [p for p in inside if p[0] in (bx0, bx1) or p[1] in (by0, by1)]
        seen, todo = set(border), list(border)
        while todo:
            cx, cy = todo.pop()
            for a, b in N4:
                near = (cx + a, cy + b)
                if near in inside and near not in seen:
                    seen.add(near)
                    todo.append(near)
        full = list(solid | (inside - seen))
        hues = sorted(colorsys.rgb_to_hsv(*(v / 255 for v in px[q][:3]))[0] for q in full if not glint(px[q]))
        if hues and LENS_HUE[0] <= hues[len(hues) // 2] <= (BOXED_HUE if box else LENS_HUE[1]):
            lenses.update(full)
            if best is None or len(full) > len(best):
                best = full
    if not best:
        return None, None
    cx, cy = sum(p[0] for p in best) / len(best), sum(p[1] for p in best) / len(best)
    yellow = {(x, y) for y in range(height) for x in range(width) if beak_colour(px[x, y])} - lenses
    beaks = [c for c in components(yellow) if len(c) >= 150]
    if not beaks:
        return thickness(best), None
    beak = max(beaks, key=len)
    return thickness(best), max(math.hypot(x - cx, y - cy) for x, y in beak)


# ---------------------------------------------------------------- sources and their reductions
def palette():
    return json.loads((SHEET / 'palette.yaml').read_text())['colors']


def inked(figure, factor, colors):
    size = (round(figure.width / factor), round(figure.height / factor))
    return pipeline.outline(pipeline.indexed(figure.resize(size, Image.Resampling.LANCZOS), colors))


def strip_runs(raw):
    alpha = raw.getchannel('A')
    cols = [x for x in range(raw.width) if any(alpha.getpixel((x, y)) > 128 for y in range(0, raw.height, 2))]
    runs, start, prev = [], cols[0], cols[0]
    for x in cols[1:]:
        if x - prev > 24:
            runs.append((start, prev + 1))
            start = x
        prev = x
    runs.append((start, prev + 1))
    return runs


def find_raw(name):
    hits = list((HOME / '.codex/generated_images').glob(f'*/{name}')) + list(SCRATCH.glob(f'**/{name}'))
    return hits[0] if hits else None


def sources(colors):
    """Every drawing with a recorded source: dict(id, group, kind, figure, box, factor, reduce, origin).
    factor is source pixels per 31 px rung pixel as first installed; reduce(f) takes a 37 px rung factor."""
    old = {f['id']: f for f in json.loads((PROD / 'rescale-37.json').read_text())['frames']}

    # core strips: wait, walk, run, roll, spindash and the pose strip
    for fid, meta in json.loads((PROD / 'provenance.json').read_text())['frames'].items():
        if meta['group'] not in ('wait', 'walk', 'run', 'poses', 'roll', 'spindash') or fid not in old:
            continue
        lane = 'talon-art-r2-20261109' if meta.get('round') == 2 else 'talon-art-20261109'
        path = SCRATCH / lane / f"{meta['group']}-attempt-{meta['attempt']}.png"
        raw = Image.open(path).convert('RGBA')
        runs = strip_runs(raw)
        if meta['cell'] >= len(runs):
            continue
        x0, x1 = runs[meta['cell']]
        figure = pipeline.largest_component(raw.crop((x0, 0, x1, raw.height)))
        figure = figure.crop(figure.getbbox())
        w0, h0 = old[fid]['old_size']

        def reduce(factor, figure=figure):
            w, h = round(figure.width / factor), round(figure.height / factor)
            scale = max(figure.width / w, figure.height / h)
            pad = Image.new('RGBA', (round(w * scale), round(h * scale)))
            pad.alpha_composite(figure, ((pad.width - figure.width) // 2, pad.height - figure.height))
            return pipeline.outline(pipeline.indexed(pad.resize((w, h), Image.Resampling.LANCZOS), colors))
        group = fid.rsplit('-', 1)[0] if meta['group'] == 'poses' else meta['group']
        yield dict(id=fid, group=f"core/{group}", kind='core', figure=figure, box=None,
                   factor=max(figure.width / w0, figure.height / h0), reduce=reduce,
                   origin=f'{lane}/{path.name} figure {meta["cell"]}')

    # family sheets
    family = json.loads((PROD / 'family-generation.json').read_text())['frames']
    for fid, meta in family.items():
        if 'fly' in fid:   # replaced by the feedback flight set and the joint carry generation below
            continue
        sheet = Image.open(meta['source'].replace('$HOME', str(HOME))).convert('RGBA')
        cols, rows = meta['grid']
        c, r = meta['cell']
        figure = pipeline.largest_component(sheet.crop((c * sheet.width // cols, r * sheet.height // rows,
                                                        (c + 1) * sheet.width // cols, (r + 1) * sheet.height // rows)))
        yield dict(id=fid, group=f"family/{fid[len('family-'):].rsplit('-', 1)[0]}@{Path(meta['source']).stem[-8:]}",
                   kind='family', figure=figure,
                   box=meta.get('goggleSourceBox'), factor=meta.get('finalScale') or meta['scale'],
                   reduce=lambda factor, figure=figure: inked(figure, factor, colors),
                   origin=f"{Path(meta['source']).name} cell {c},{r} of {cols}x{rows}; sha256 {meta['sha256'][:12]}")

    # object poses: 4x3 sheets, alpha cut at 240
    lane = SCRATCH / 'talon-object-poses-20261110'
    for meta in json.loads((PROD / 'object-poses/provenance.json').read_text())['frames']:
        made = meta.get('reduction')
        if not made or 'group' not in made or meta.get('attempt') == 3:
            continue
        ids = json.loads((lane / f"roots-{made['group']}.json").read_text())
        k = ids.index(meta['mapping'])
        sheet = Image.open(lane / f"attempt{meta['attempt']}-{made['group']}.png").convert('RGBA')
        cell = sheet.crop((k % 4 * sheet.width // 4, k // 4 * sheet.height // 3,
                           (k % 4 + 1) * sheet.width // 4, (k // 4 + 1) * sheet.height // 3))
        cell.putalpha(cell.getchannel('A').point(lambda a: 255 if a >= 240 else 0))
        figure = pipeline.largest_component(cell)
        yield dict(id=meta['id'], group=f"object/attempt{meta['attempt']}-{made['group']}", kind='object',
                   figure=figure, box=None, factor=1 / made['scale'],
                   reduce=lambda factor, figure=figure: inked(figure, factor, colors),
                   origin=f"talon-object-poses-20261110/attempt{meta['attempt']}-{made['group']}.png cell {k}; "
                          f"sha256 {meta['raw_sha256'][:12]}")

    # the feedback flight set: nearest reduction, material contour (tonal_reshade.py inks it)
    feedback = json.loads((PROD / 'feedback-generation.json').read_text())
    for meta in feedback['frames']:
        if 'source_crop' not in meta or 'ascend' in meta['id']:
            continue
        group = next(g for g in feedback['groups'] if g['name'] == meta['group'])
        name = group['attempts'][meta['attempt'] - 1]['file']
        sheet = Image.open(find_raw(name)).convert('RGBA')
        cols, rows = meta.get('grid', [1, 1])
        ox, oy = round(meta['cell'] % cols * sheet.width / cols), round(meta['cell'] // cols * sheet.height / rows)
        crop = meta['source_crop']
        figure = sheet.crop((ox + crop[0], oy + crop[1], ox + crop[2], oy + crop[3]))
        figure.putalpha(figure.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
        figure = figure.crop(figure.getbbox())

        def reduce(factor, figure=figure):
            size = (round(figure.width / factor), round(figure.height / factor))
            return pipeline.indexed(figure.resize(size, Image.Resampling.NEAREST), colors)
        yield dict(id=meta['id'], group='flight', kind='flight', figure=figure, box=None,
                   factor=meta['source_pixels_per_native_pixel'], reduce=reduce,
                   origin=f"{name} cell {meta['cell']} crop {crop}; sha256 {meta['raw_sha256'][:12]}")

    # the joint carry generation: TALON is everything above the shared sole line of each 512 px cell
    carry = json.loads((PROD / 'carry-generation.json').read_text())
    sheet = Image.open(SCRATCH / 'mt-carry-20261110/attempt5.png').convert('RGBA')
    for i in range(6):
        cx, cy = i % 3 * 512, i // 3 * 512
        cut = sheet.crop((cx, cy, cx + 512, cy + CARRY_SOLE[i] - cy))
        figure = pipeline.largest_component(cut.copy())

        def reduce(factor, cut=cut):
            small = pipeline.indexed(cut.resize((round(cut.width / factor), round(cut.height / factor)),
                                                Image.Resampling.NEAREST), colors)
            nearest = {v: min(CARRY_ALLOWED, key=lambda k: sum((colors[v][q] - colors[k][q]) ** 2 for q in range(3)))
                       for v in range(1, 16)}
            nearest[0] = 0
            small.putdata([nearest[v] for v in small.get_flattened_data()])
            return small.crop(small.getbbox())
        yield dict(id=f'family-tails-fly-carry-0{i}', group='carry', kind='carry', figure=figure, box=None,
                   factor=carry['scale'], reduce=reduce,
                   origin=f"mt-carry-20261110/attempt5.png cell {i} above the sole line; "
                          f"sha256 {carry['attempts'][4]['sha256'][:12]}")


def route2(installed, colors, resize):
    """No source drawing: the fallback of rescale-37.json. The installed pixels are enlarged with Scale2x three
    times (an 8x smooth master), reduced to the new size, and the contours put back from the original: each
    new silhouette pixel takes the nearest original contour colour and a doubled second contour layer returns
    to the original interior colour."""
    big = installed
    for _ in range(3):
        big = scale2x(big)
    big.info['transparency'] = 0
    w, h = round(installed.width * resize), round(installed.height * resize)
    new = pipeline.indexed(big.convert('RGBA').resize((w, h), Image.Resampling.LANCZOS), colors)
    ow, oh = installed.size
    old = installed.load()

    def edge(image, x, y):
        return image.getpixel((x, y)) != 0 and any(
            not (0 <= a < image.width and 0 <= b < image.height) or image.getpixel((a, b)) == 0
            for a, b in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    contour = {(x, y) for y in range(oh) for x in range(ow) if edge(installed, x, y)}
    px, edits = new.load(), []
    for y in range(h):
        for x in range(w):
            if not px[x, y]:
                continue
            ox, oy = min(ow - 1, int((x + 0.5) * ow / w)), min(oh - 1, int((y + 0.5) * oh / h))
            if edge(new, x, y):
                near = [(abs(a - ox) + abs(b - oy), (a, b)) for a in range(ox - 1, ox + 2) for b in range(oy - 1, oy + 2)
                        if (a, b) in contour]
                if near:
                    edits.append((x, y, old[min(near)[1]]))
            elif (ox, oy) not in contour and old[ox, oy]:
                outward = [(a, b) for a, b in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                           if 0 <= a < w and 0 <= b < h and edge(new, a, b)]
                if outward and all(px[a, b] == px[x, y] for a, b in outward):
                    edits.append((x, y, old[ox, oy]))
    for x, y, value in edits:
        px[x, y] = value
    return new


# ---------------------------------------------------------------- the table
# Lens readings rejected by eye: the detector found a blob, but it is not a lens seen whole.
HAND = {
    'family-float2-05': 'back view: the blob is the strap buckle, no lens faces the viewer',
    'family-float2-06': 'back view: the blob is the strap buckle, no lens faces the viewer',
    'family-float2-07': 'back view: the blob is the strap buckle, no lens faces the viewer',
}
# Readings overruled by eye, each source drawn beside WAIT at the scale the readings asked for.
# HAND_SCALE replaces a multiplier, HAND_KEEP refuses a rebuild; the text says what was seen.
HAND_SCALE = {
    'duck-00': (0.87, 'head tucked into the crouch, the beak points at the ground and reads short: the band alone'),
}
# Drawings with no usable source drawing (no recorded strip, or a composite the strip does not reproduce),
# read by hand on the installed frame against WAIT-00: goggle span 11 px, eye 4 px tall, beak 7 px long.
HAND_READ = {
    'spring-00': (0.87, 'goggle span 10 px, eye 3 px, beak 5 px (pointing up, so it reads short): about 0.87'),
    **{f'fly_tired-0{i}': (1.0, 'goggle span 11 px, near lens 5x3 px as WAIT; stands 37-38 px') for i in range(6)},
}
HAND_KEEP = {
    'object5-3d': 'standing pose 37 px tall: the standing height already holds and the head is drawn large for the '
                  'body (head 1.21), so shrinking it left a 31 px bird; only a new drawing fixes both',
    'death-00': 'front view: the lens is drawn thin (band 0.86) while the span reads 0.98; beside WAIT the head '
                'already matches, and enlarged it stood a head taller',
    'family-blink-04': 'seated yawn with the head turned, so both readings run short; blink-03 beside it on the same '
                       'sheet at the same factor stands 39 px, so the sheet is not small',
}


def mean(*values):
    values = [v for v in values if v]
    return math.exp(sum(math.log(v) for v in values) / len(values)) if values else None


def measure():
    colors = palette()
    listed = {f['id']: f for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    record = json.loads(AUDIT.read_text()) if AUDIT.exists() else {}
    before = record.get('frames', {})
    rows = {}
    for source in sources(colors):
        lens, span = (None, None) if source['id'] in HAND else head(source['figure'], source['box'])
        rows[source['id']] = dict(group=source['group'], kind=source['kind'], origin=source['origin'],
                                  factor=round(source['factor'], 4), band_source_px=lens and round(lens, 1),
                                  span_source_px=span and round(span, 1))
        if source['id'] in HAND:
            rows[source['id']]['hand'] = HAND[source['id']]
    uncleaned = {d['id']: d['source_rows'] for d in json.loads((PROD / 'pose-cleanup.json').read_text())['drawings']}
    for fid, (value, seen) in HAND_READ.items():
        kept = before.get(fid, {})
        rows[fid] = dict(group='hand/' + fid.rsplit('-', 1)[0], kind='hand', factor=None, band_source_px=None,
                         span_source_px=None, origin='no usable source drawing: read by hand on the installed frame',
                         hand=seen, hand_head=value, before_rows=kept.get('before_rows') or uncleaned[fid])
    wait = [r for r in rows.values() if r['group'] == 'core/wait']
    ref_band = statistics.median(r['band_source_px'] / r['factor'] for r in wait if r['band_source_px'])
    ref_span = statistics.median(r['span_source_px'] / r['factor'] for r in wait if r['span_source_px'])
    members = defaultdict(list)
    for fid, row in rows.items():
        if row['kind'] == 'hand':
            row.update(band=None, span=None)
            members[fid].append(fid)
            continue
        row['band'] = row['band_source_px'] and round(row['band_source_px'] / row['factor'] / ref_band, 3)
        row['span'] = row['span_source_px'] and round(row['span_source_px'] / row['factor'] / ref_span, 3)
        if row['span'] and row['band'] and not 0.6 <= row['span'] / row['band'] <= 1.5:
            row.update(span=None, span_rejected='not a beak in profile: the span is out of proportion to the lens')
        members['object' if row['kind'] == 'object' else row['group']].append(fid)
    # Object sheets are all "native pose at 8x": one figure scale for the set. Its band is the median of the
    # sheets' medians (many object poses hide or foreshorten the lens), its span the median of every reading.
    sheets = defaultdict(list)
    for fid in members['object']:
        if rows[fid]['band_source_px']:
            sheets[rows[fid]['group']].append(rows[fid]['band_source_px'])
    object_band = statistics.median(statistics.median(v) for v in sheets.values() if len(v) >= 5)
    object_span = statistics.median(rows[i]['span_source_px'] for i in members['object'] if rows[i]['span_source_px'])
    for group, ids in members.items():
        bands = [rows[i]['band'] for i in ids if rows[i]['band']]
        spans = [rows[i]['span'] for i in ids if rows[i]['span']]
        group_band = statistics.median(bands) if bands else None
        group_span = statistics.median(spans) if spans else None
        whole = mean(group_band, group_span)
        for fid in ids:
            row = rows[fid]
            if row['kind'] == 'hand':
                row['head'] = row.pop('hand_head')
            elif group == 'object':
                row['set'] = round(mean(object_band / row['factor'] / ref_band, object_span / row['factor'] / ref_span), 3)
                own = mean(row['band'], row['span']) if row['band'] and row['span'] else None
                row['head'] = own and round(own, 3)
            else:
                value = mean(row['band'], row['span']) or whole
                row['head'] = value and round(value, 3)
        own = [rows[i]['head'] for i in ids if rows[i]['band']]
        together = group != 'object' and whole and rows[ids[0]]['kind'] != 'hand' and (
            len({rows[i]['factor'] for i in ids}) == 1 or all(abs(v / whole - 1) <= TOLERANCE for v in own))
        for fid in ids:
            row, frame = rows[fid], listed[fid]
            kept = before.get(fid, {})
            with Image.open(SHEET / frame['file']) as image:
                row.update(animation=frame['animation'], before_size=kept.get('before_size') or list(image.size),
                           before_hotspot=kept.get('before_hotspot') or frame['hotspot'])
            height = row['before_size'][1]
            if fid in HAND_SCALE:
                row['multiplier'] = HAND_SCALE[fid][0]
            elif group == 'object':
                row['multiplier'] = row['head'] and round(mean(row['head'], row['set']), 3)
            else:
                row['multiplier'] = row['head'] and round(whole if together else row['head'], 3)
            if fid in HAND_SCALE:
                row.update(decision='rebuild', why='by eye: ' + HAND_SCALE[fid][1])
            elif row['kind'] == 'hand':
                row.update(decision='keep' if abs(row['multiplier'] - 1) <= TOLERANCE else 'rebuild',
                           why='by hand: ' + row['hand'])
            elif fid in HAND_KEEP:
                row.update(decision='keep', why='by eye: ' + HAND_KEEP[fid])
            elif frame['animation'] in BALL:
                row.update(decision='keep', why='ball drawing: no comparable head')
            elif row['multiplier'] is None:
                row.update(decision='keep', why='no clean view of both lens and beak' if group == 'object'
                           else 'no lens in view on this sheet')
            elif abs(row['multiplier'] - 1) <= TOLERANCE:
                row.update(decision='keep', why='within 8%')
            elif frame['animation'] in UPRIGHT and abs(height / 37 - 1) <= TOLERANCE:
                row.update(decision='keep', why=f'upright at {height} px: standing height holds')
            elif row['multiplier'] < 1:
                row.update(decision='keep', why='small reading: both measures fail low (part-hidden lens, foreshortened '
                                                'beak), so a drawing is enlarged only by hand')
            elif group == 'object' and row['head'] <= 1 + TOLERANCE:
                row.update(decision='keep', why='its own head is within 8%; only the set reading is large')
            else:
                row.update(decision='rebuild', why='own head and the set' if group == 'object' else
                           'group' if together else 'own head')
            if 'rebuilt' in kept:
                row['rebuilt'] = kept['rebuilt']
    for row in rows.values():
        row['after'] = row['head'] and round(row['head'] / row['multiplier'], 3) if row['decision'] == 'rebuild' else row['head']
    record['animations'] = animations(rows)
    record['unmeasured'] = unmeasured(rows, listed)
    record['mica'] = mica()
    record.update(reference=dict(frames='WAIT', band_31_rung_px=round(ref_band, 3), span_31_rung_px=round(ref_span, 3),
                                 object_set_band_px=round(object_band, 1), object_set_span_px=round(object_span, 1)),
                  tolerance=TOLERANCE, frames=dict(sorted(rows.items())))
    AUDIT.write_text(json.dumps(record, indent=1) + '\n')
    counts = defaultdict(int)
    for row in rows.values():
        counts[row['decision']] += 1
    print(f"WAIT band {ref_band:.2f} px, lens to beak tip {ref_span:.2f} px at the 31 px rung; "
          f"{len(rows)} drawings measured: {dict(counts)}")


def animations(rows):
    """Per animation: how many drawings have a head reading, and its spread before and after."""
    groups = defaultdict(list)
    for row in rows.values():
        groups[row['animation']].append(row)
    out = {}
    for name, members in sorted(groups.items()):
        before = sorted(r['head'] for r in members if r['head'])
        after = sorted(r['after'] for r in members if r['after'])
        out[name] = dict(drawings=len(members), read=len(before), rebuilt=sum(r['decision'] == 'rebuild' for r in members),
                         before=before and [before[0], round(statistics.median(before), 3), before[-1]],
                         after=after and [after[0], round(statistics.median(after), 3), after[-1]])
    return out


def unmeasured(rows, listed):
    """Flat drawings with no row of their own, and why."""
    shared = {name: d['id'] for d in json.loads((PROD / 'tonal-reshade.json').read_text())['drawings'] for name in d['frames']}
    poses = json.loads((PROD / 'object-poses/provenance.json').read_text())['frames']
    by_mapping = {f['mapping']: f['id'] for f in poses if 'source' not in f}
    why = {}
    for meta in poses:
        if 'source' in meta:
            why[meta['id']] = f"{by_mapping[meta['source']]} turned {meta['angle']} degrees" + \
                              (' and mirrored' if meta['flip'] else '') + '; follows it'
        elif 'reduction' not in meta or 'group' not in meta['reduction'] or meta.get('attempt') == 3:
            why.setdefault(meta['id'], 'object pose reduced outside the 4x3 sheets: no source cell to read')
    out = {}
    for fid, frame in listed.items():
        if frame.get('angle_set', 'flat') != 'flat' or fid in rows:
            continue
        if fid in shared and shared[fid] != fid:
            out[fid] = f'the same drawing as {shared[fid]}'
        elif frame['animation'] == 'BLANK':
            out[fid] = 'empty frame'
        else:
            out[fid] = why.get(fid, 'no recorded source')
    return out


def mica():
    """MICA has no goggles or beak: her spread is read from the hand-marked landmarks of her own record."""
    root = ROOT / 'examples/mica-and-talon'
    marks = json.loads((root / 'production/mica/consistency-landmarks.json').read_text())
    flats = [f for f in json.loads((root / 'art/mica/sheet/frames.yaml').read_text())['frames']
             if f.get('angle_set', 'flat') == 'flat']
    reference = marks['frames'][marks['reference']]['after']['metrics'][0]
    groups, off, unread, unmarked = defaultdict(list), {}, [], []
    for frame in flats:
        entry = marks['frames'].get(frame['id'])
        if not entry:
            unmarked.append(frame['id'])
            continue
        skull = (entry.get('after') or {}).get('metrics', [None])[0]
        if not skull:
            unread.append(frame['id'])
            continue
        scale = round(skull / reference, 3)
        groups[frame['animation']].append(scale)
        if abs(scale - 1) > TOLERANCE:
            off[frame['id']] = scale
    every = sorted(v for values in groups.values() for v in values)
    return dict(
        method=f"{marks['metricNames'][0]} in px from production/mica/consistency-landmarks.json (hand-marked, about 1 px, "
               f"so one step is {1 / reference:.1%}), over {marks['reference']}'s {reference} px. Her frames are not changed.",
        flats=len(flats), read=len(every), spread=[every[0], statistics.median(every), every[-1]],
        within_tolerance=sum(abs(v - 1) <= TOLERANCE for v in every),
        animations={name: [min(values), max(values), len(values)] for name, values in sorted(groups.items())},
        beyond_tolerance=dict(sorted(off.items(), key=lambda kv: kv[1])),
        landmarks_hidden=len(unread),
        unmarked=dict(count=len(unmarked), families=sorted({i.rsplit('-', 1)[0] for i in unmarked}),
                      note='added after the landmark pass; an eye-white check at native size reads 3-6 px on them, '
                           'the range of her own WAIT (4-6 px), which rules out a gross error but cannot resolve 8%'))


# ---------------------------------------------------------------- rebuild
TURN = {90: Image.Transpose.ROTATE_90, 180: Image.Transpose.ROTATE_180, 270: Image.Transpose.ROTATE_270}


def rows_of(image):
    pixels = image.load()
    return [''.join(format(pixels[x, y], 'x') for x in range(image.width)) for y in range(image.height)]


def digest(rows):
    return hashlib.sha256('\n'.join(rows).encode()).hexdigest()[:16]


def anchor(animation, old_size, hot, new_size):
    x = round(hot[0] * new_size[0] / old_size[0])
    if animation in TOP:
        return [x, hot[1]]
    if animation in SOLE:
        return [x, new_size[1] - (old_size[1] - hot[1])]
    return [x, round(hot[1] * new_size[1] / old_size[1])]


def turned(image, hot, angle, flip):
    """An object pose that is another one turned or mirrored (object-poses/provenance.json)."""
    x, y = hot
    if flip:
        image, x = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT), image.width - 1 - x
    if angle == 90:
        x, y = y, image.width - 1 - x
    elif angle == 180:
        x, y = image.width - 1 - x, image.height - 1 - y
    elif angle == 270:
        x, y = image.height - 1 - y, x
    return (image.transpose(TURN[angle]) if angle else image), [x, y]


def rebuild():
    colors = palette()
    record = json.loads(AUDIT.read_text())
    table = record['frames']
    docs = {name: json.loads((path).read_text()) for name, path in (
        ('manifest', SHEET / 'frames.yaml'), ('recipe', PROD / 'recipe.json'), ('pose', PROD / 'pose-cleanup.json'),
        ('tonal', PROD / 'tonal-reshade.json'), ('rescale', PROD / 'rescale-37.json'),
        ('feedback', PROD / 'feedback-generation.json'))}
    listed = {f['id']: f for f in docs['manifest']['frames']}
    pinned = {f['id']: f for f in docs['recipe']['frames']}
    pose = {d['id']: d for d in docs['pose']['drawings']}
    tonal = {d['id']: d for d in docs['tonal']['drawings']}
    ledger = {f['id']: f for f in docs['rescale']['frames']}
    selected = {f['id']: f for f in docs['feedback']['frames']}
    done = {}

    def place(fid, image, hot, factor, uncleaned=True):
        """Hand one rebuilt drawing to its cleanup record and move every hotspot that names it."""
        rows = rows_of(image)
        ids = [fid]
        if fid in tonal:
            drawing = tonal[fid]
            old = (len(drawing['source_rows'][0]), len(drawing['source_rows']))
            sx, sy = image.width / old[0], image.height / old[1]
            drawing['wing_tip_boxes'] = [[math.floor(a * sx), math.floor(b * sy), math.ceil(c * sx), math.ceil(d * sy)]
                                         for a, b, c, d in drawing['wing_tip_boxes']]
            drawing['first_shoe_row'] = round(drawing['first_shoe_row'] * sy)
            drawing['source_rows'] = rows
            drawing.pop('patches', None)
            ids = drawing['frames']
        elif fid in pose:
            pose[fid]['source_rows'] = rows
            pose[fid].pop('patches', None)
        else:   # a drawing no cleanup touches (the white transformation flash): written as reduced
            saved = image.copy()
            saved.putpalette(sum(colors, []) + [0] * (768 - 48))
            saved.save(SHEET / listed[fid]['file'], transparency=0, optimize=True, bits=4)
            pinned[fid]['native_pixels']['rows'] = rows
        for name in ids:
            listed[name]['hotspot'] = hot
            for holder in (listed[name], pinned[name]):
                if STAMP not in (holder.get('notes') or ''):
                    holder['notes'] = ((holder.get('notes') or '') + NOTE.format(factor=factor)).strip()
            pinned[name]['native_pixels']['anchor'] = hot
            ledger[name]['scale_rebuild'] = dict(size=list(image.size), hotspot=hot, note='2026-11-12: scale_audit.py')
            if name in selected:
                selected[name].update(size=list(image.size), hotspot=hot, scale_rebuild=(
                    f'2026-11-12: reduced again at x{factor:.3f} of the size this record describes (scale-audit.json)'))
                if 'authored_area' in selected[name]:
                    selected[name]['authored_area'] = sum(1 for v in image.get_flattened_data() if v)
        done[fid] = (image, hot)
        return dict(size=list(image.size), hotspot=hot, uncleaned_sha256=digest(rows), frames=ids)

    for source in sources(colors):
        row = table[source['id']]
        row.pop('rebuilt', None)
        if row['decision'] != 'rebuild':
            continue
        factor = source['factor'] / RUNG * row['multiplier']
        image = source['reduce'](factor)
        hot = anchor(row['animation'], row['before_size'], row['before_hotspot'], image.size)
        row['rebuilt'] = dict(factor_37=round(factor, 4), resize=round(1 / row['multiplier'], 3),
                              **place(source['id'], image, hot, 1 / row['multiplier']))

    for fid, row in table.items():   # no source drawing: route 2 on the pixels as they were before this pass
        if row['kind'] != 'hand':
            continue
        row.pop('rebuilt', None)
        if row['decision'] == 'rebuild':
            was = Image.new('P', (len(row['before_rows'][0]), len(row['before_rows'])))
            was.putpalette(sum(colors, []) + [0] * (768 - 48))
            was.putdata([int(v, 16) for line in row['before_rows'] for v in line])
            image = route2(was, colors, 1 / row['multiplier'])
            hot = anchor(row['animation'], row['before_size'], row['before_hotspot'], image.size)
            row['rebuilt'] = dict(route=2, resize=round(1 / row['multiplier'], 3),
                                  **place(fid, image, hot, 1 / row['multiplier']))

    by_mapping = {f['mapping']: f for f in json.loads((PROD / 'object-poses/provenance.json').read_text())['frames']}
    record['derived'] = {}
    for meta in by_mapping.values():
        parent = by_mapping.get(meta.get('source'), {}).get('id')
        if parent in done:
            image, hot = turned(*done[parent], meta['angle'], meta['flip'])
            record['derived'][meta['id']] = dict(source=parent, angle=meta['angle'], flip=meta['flip'],
                                                 **place(meta['id'], image, hot, 1 / table[parent]['multiplier']))

    for name, path, indent in (('manifest', SHEET / 'frames.yaml', 2), ('recipe', PROD / 'recipe.json', 2),
                               ('pose', PROD / 'pose-cleanup.json', 1), ('tonal', PROD / 'tonal-reshade.json', 1),
                               ('rescale', PROD / 'rescale-37.json', 1), ('feedback', PROD / 'feedback-generation.json', 2)):
        path.write_text(json.dumps(docs[name], indent=indent) + '\n')
    AUDIT.write_text(json.dumps(record, indent=1) + '\n')
    print(f"{len(done) - len(record['derived'])} drawings rebuilt, {len(record['derived'])} turned from them; "
          'now run pose_cleanup.py and tonal_reshade.py')


def check():
    record = json.loads(AUDIT.read_text())
    listed = {f['id']: f for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    pinned = {f['id']: f for f in json.loads((PROD / 'recipe.json').read_text())['frames']}
    held = {d['id']: d['source_rows'] for name in ('pose-cleanup.json', 'tonal-reshade.json')
            for d in json.loads((PROD / name).read_text())['drawings']}
    stale, count = [], 0
    rebuilt = {fid: row['rebuilt'] for fid, row in record['frames'].items() if row['decision'] == 'rebuild'}
    rebuilt.update(record.get('derived', {}))
    # lineart_pass.py redraws heads after this rebuild and may move a drawing's origin: it keeps the hotspot
    # this rebuild gave as hotspot_before, and a turned copy is turned again from its redrawn source.
    lineart = PROD / 'lineart-pass.json'
    redrawn = json.loads(lineart.read_text())['frames'] if lineart.exists() else {}
    for fid, made in rebuilt.items():
        count += 1
        rows = held.get(fid) or pinned[fid]['native_pixels']['rows']
        if digest(rows) != made['uncleaned_sha256'] or [len(rows[0]), len(rows)] != made['size']:
            stale.append(fid + ' rows')
        for name in made['frames']:
            if 'copy' in redrawn.get(name, {}):
                continue
            hotspot = redrawn[name]['hotspot_before'] if name in redrawn else listed[name]['hotspot']
            if hotspot != made['hotspot'] or (name not in redrawn and
                                              pinned[name]['native_pixels']['anchor'] != made['hotspot']):
                stale.append(name)
    if stale:
        raise SystemExit('Stale scale rebuild: ' + ', '.join(stale))
    print(f'{count} rebuilt drawings match')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('mode', nargs='?', choices=('measure', 'rebuild'))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.mode == 'measure':
        measure()
    elif args.mode == 'rebuild':
        rebuild()
    if args.check or not args.mode:
        check()


if __name__ == '__main__':
    main()
