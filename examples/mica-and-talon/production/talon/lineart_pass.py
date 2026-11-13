#!/usr/bin/env python3
"""Redraw TALON's off-style poses from his reference pose's parts.

Purpose: the owner found poses that "look like a completely different artist drew them" beside
the crisp standing and flying ones. Every pose was reduced on its own from a separately drawn
source, so each sheet arrived with its own face (a plain white mask where WAIT has a cream cheek,
brow and eye stripe), its own goggles and beak, and a dark plumage rim that thickens the outline.
Cleaning pixels cannot fix a different drawing. This pass gives every pose the same head: WAIT's,
cut from his cleaned wait-00, placed on the pose's own goggle lens, turned to the pose's head
pitch and mirrored where the pose faces away; an open-beak variant keeps the gasp and shout
poses. The body keeps its pose and size and has its dark rim thinned to WAIT's line weight.
Inputs: lineart-pass.json beside this file (per drawing: where the head goes, or why it gets
none). `measure` also needs the external source drawings scale_audit.py names, to read each
pose's lens and beak; nothing else does.
Origin: owner line-art feedback on PR #287, 2026-11-12. Pillow; no ROM or Java needed.
Run from the repository root:
  lineart_pass.py measure   re-read the head landmarks from the sources into lineart-pass.json
  lineart_pass.py --check   every drawing has a head or a reason, and the record's decisions hold
pose_cleanup.py applies the pass (restyle) after its own cleanup and writes the frames, so
`pose_cleanup.py --check` is the reproduction check.

Head. The donor is every pixel of cleaned wait-00 above its neck (rows 0-16). A pose's own
drawing gives two points, its near goggle lens centre and its beak tip (read on the source drawing
and mapped into the frame); the donor's lens is put on the pose's lens and the head is turned by
the angle between the two lens-to-beak directions. Turns of 8 degrees or less are not made (a
straight head is crisper than a slightly turned one); larger ones use the slope method (contour
lifted, Scale2x three times, nearest rotation, commonest index per block, cleanup, ink).
The head is mirrored when the pose's head mass lies on the other side of its lens-to-beak line.
Old head pixels within two pixels of the new head are cleared, except beside the neck, and any
piece left detached is dropped; then the silhouette is inked.
No head is placed where the pose has none to match: a front or back view, a ball, a hidden lens
or beak. Those drawings are listed with the reason and only get the body rule.

Body. These drawings carry a slate-deep rim just inside the ink all the way round, which makes
their outline read two pixels thick; WAIT keeps that shade for the shadow side only. A slate-deep
pixel outside any 2x2 slate-deep block, with ink to its left or above and none to its right or
below, becomes slate-shadow: the sheet is lit from the upper left. Solid shadows stay.

Not touched: wait-00 (the donor), WALK, RUN and the flight and carry sets (the references and their
own tools), ROLL and SPINDASH (balls), and anything HAND lists with what was seen.

The idle, blink and victory drawings (WAIT but wait-00, BLINK, VICTORY) came later, from
production/idle_sign_victory.py's lane: their entries were placed by hand on the reduced frames, and
`measure` carries them over. Two head options exist for them: `eye: shut` (the straight head with a
lid line, for a wink or a wind-up) and `over` boxes (a hand the pose holds in front of its face is
drawn back over the head, so a salute stays in front of the goggles).
"""
import argparse
import json
import math
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
SHEET = ROOT / 'examples/mica-and-talon/art/talon/sheet'
PROD = ROOT / 'examples/mica-and-talon/production/talon'
RECORD = PROD / 'lineart-pass.json'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
sys.path.insert(0, str(PROD))

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
DONOR, NECK = 'wait-00', 16          # rows 0..NECK of the donor are head
LENS, TIP = (14, 5), (21, 16)        # donor landmarks, frame pixels
STRAIGHT = 8                         # degrees: smaller turns are not made
FAR_TURN = 42                        # degrees: beyond this the pose tumbles or hangs inverted and keeps its head
CANVAS = 64                          # a turned head is built with its lens at the centre of this square
REFERENCE = {'WALK', 'RUN', 'TAILS_FLY', 'TAILS_FLY_ASCEND', 'TAILS_FLY_CARRY', 'TAILS_FLY_CARRY_ASCEND'}   # and DONOR itself
# Entered with their drawings by production/idle_sign_victory.py's lane (2026-11-12): they have no source drawing in
# scale-audit.json, so `measure` carries their entries over instead of reading them.
ENTERED = {'WAIT', 'BLINK', 'VICTORY'}
NO_HEAD = {'ROLL': 'ball drawing', 'SPINDASH': 'ball drawing', 'BLANK': 'empty frame'}
AMBER, RED = (14, 15), (10, 11)
# The open beak on the straight head: the donor's upper mandible stays, a red mouth and a dropped lower
# mandible replace the closed one. x, y, index on the donor's grid.
OPEN_BEAK = (
    (16, 15, 15), (17, 15, 11), (18, 15, 11), (19, 15, 11), (20, 15, 1), (21, 15, 0), (22, 15, 0),
    (15, 16, 7), (16, 16, 7), (17, 16, 11), (18, 16, 10), (19, 16, 11), (20, 16, 1), (21, 16, 0),
    (14, 17, 1), (15, 17, 1), (16, 17, 15), (17, 17, 15), (18, 17, 15), (19, 17, 14), (20, 17, 1),
    (16, 18, 1), (17, 18, 1), (18, 18, 1), (19, 18, 1),
)
# The shut eye on the straight head (a wink, a wind-up): the white and pupil go, a lid line stays.
SHUT_EYE = ((14, 10, 4), (15, 10, 4), (14, 11, 4), (15, 11, 4), (16, 11, 3), (17, 11, 3),
            (13, 12, 1), (14, 12, 1), (15, 12, 1), (16, 12, 1), (14, 13, 4), (15, 13, 4))
GLOVE = (12, 13)                     # a hand held in front of the face is drawn back over the head ('over' boxes)
# Decisions by eye, after seeing the automatic result beside WAIT: frame -> (head or None, what was seen).
# A head may be given whole (lens, angle, mirror, beak) where the source drawing hides a landmark.
HAND = {
    'duck-00': (None, 'head tucked into the crouch: an upright reference head breaks the pose'),
    'object5-3f': (None, 'seen from behind and above: the lens and beak readings are the satchel and a claw, no face shows'),
    'object5-4b': (None, 'lying on his back with the face foreshortened: the reference head read as a white blob'),
}
# Landmarks read by hand on the frame where the source drawing hides one (a beak the detector does not see,
# or a drawing with no usable source). Frame pixels before this pass; `angle` fixes a loop to one turn.
HAND_MARKS = {
    'family-balance4-00': dict(tip=[21.5, 13.5]),
    'family-balance4-01': dict(tip=[22.5, 11.0]),
    'family-balance4-04': dict(tip=[24.5, 13.5]),
    'family-get-up-00': dict(tip=[41.5, 16.5]),
    'family-get-up-01': dict(tip=[21.5, 12.0]),
    'fly_tired-00': dict(lens=[21.5, 6.3], far=[27.5, 6.0], tip=[30.5, 12.5], angle=-22),
    'fly_tired-01': dict(lens=[23.3, 6.2], far=[29.0, 6.0], tip=[31.5, 12.5], angle=-22),
    'fly_tired-02': dict(lens=[22.3, 6.0], far=[28.0, 6.0], tip=[30.5, 12.5], angle=-22),
    'fly_tired-03': dict(lens=[20.5, 6.0], far=[25.5, 6.0], tip=[29.5, 12.3], angle=-22),
    'fly_tired-04': dict(lens=[22.8, 6.0], far=[28.0, 6.0], tip=[31.5, 12.3], angle=-22),
    'fly_tired-05': dict(lens=[21.8, 6.2], far=[27.5, 6.0], tip=[30.5, 12.3], angle=-22),
}
SNAP = 4   # degrees: an animation's turns this close together share one turned head


def to_grid(rows):
    return [[int(v, 16) for v in row] for row in rows]


def to_rows(grid):
    return [''.join(format(v, 'x') for v in row) for row in grid]


def to_image(grid, colors):
    image = Image.new('P', (len(grid[0]), len(grid)))
    image.putpalette(sum(colors, []) + [0] * (768 - 48))
    image.putdata([v for row in grid for v in row])
    return image


def of_image(image):
    pixels = image.load()
    return [[pixels[x, y] for x in range(image.width)] for y in range(image.height)]


def ink(grid):
    """The pipeline's outline: every opaque pixel beside transparency (or the frame edge) is ink."""
    height, width = len(grid), len(grid[0])
    out = [row[:] for row in grid]
    for y in range(height):
        for x in range(width):
            if grid[y][x] and any(not (0 <= x + a < width and 0 <= y + b < height) or not grid[y + b][x + a]
                                  for a, b in N4):
                out[y][x] = 1
    return out


def thin_rim(grid):
    """Body rule: a thin slate-deep rim on a lit edge (ink to its left or above, none to its right or below)
    becomes slate-shadow. The sheet is lit from the upper left, so the rim stays where it is shadow."""
    height, width = len(grid), len(grid[0])

    def at(x, y):
        return grid[y][x] if 0 <= x < width and 0 <= y < height else 0
    out = [row[:] for row in grid]
    for y in range(height):
        for x in range(width):
            if grid[y][x] != 2 or any(all(at(x + a + i, y + b + j) == 2 for i in (0, 1) for j in (0, 1))
                                      for a in (-1, 0) for b in (-1, 0)):
                continue
            lit = at(x - 1, y) == 1 or at(x, y - 1) == 1
            shaded = at(x + 1, y) == 1 or at(x, y + 1) == 1
            if lit and not shaded:
                out[y][x] = 3
    return out


def in_head(dx, dy, angle, opened):
    """Whether a pixel dx, dy from the lens of a head turned by angle lies above the donor's neck."""
    cs, sn = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    col, row = LENS[0] + dx * cs + dy * sn, LENS[1] - dx * sn + dy * cs
    return row <= NECK + 0.5 or (opened and col >= 14.5 and row <= NECK + 2.6)


def head_for(spec, donor, kit):
    """(pixels relative to the lens, as {(dx, dy): index}) of the kit head a drawing takes."""
    opened = spec['beak'] == 'open'
    if spec['angle']:
        made = kit['turned'][spec['beak']][str(spec['angle'])]
        grid, lens = to_grid(made['rows']), made['lens']
    else:
        grid = [row[:] if y <= NECK else [0] * len(row) for y, row in enumerate(donor)]
        grid += [[0] * len(donor[0]) for _ in range(3)]
        if opened:
            for x, y, index in OPEN_BEAK:
                grid[y][x] = index
        if spec.get('eye') == 'shut':
            for x, y, index in SHUT_EYE:
                grid[y][x] = index
        lens = LENS
    pixels = {(x - lens[0], y - lens[1]): v for y, row in enumerate(grid) for x, v in enumerate(row) if v}
    if spec['mirror']:
        pixels = {(-dx, dy): v for (dx, dy), v in pixels.items()}
    return pixels


def transplant(grid, spec, marks, donor, kit):
    """Put the kit head on a drawing; returns (grid, (dx, dy)) with the drawing's origin shift."""
    head = head_for(spec, donor, kit)
    placed = {(dx + spec['lens'][0], dy + spec['lens'][1]): v for (dx, dy), v in head.items()}
    height, width = len(grid), len(grid[0])
    x0, y0 = min(0, min(p[0] for p in placed)) - 1, min(0, min(p[1] for p in placed)) - 1
    x1, y1 = max(width - 1, max(p[0] for p in placed)) + 1, max(height - 1, max(p[1] for p in placed)) + 1
    wide, tall = x1 - x0 + 1, y1 - y0 + 1          # one transparent pixel all round, for the flood below
    out = [[0] * wide for _ in range(tall)]
    for y in range(height):
        for x in range(width):
            out[y - y0][x - x0] = grid[y][x]
    # The old head's goggles and beak where the new head does not cover them: amber and mouth red within
    # reach of the old lens, far lens and beak tip. Removed where that opens to the outside, else plumage.
    spots = [(point, reach) for point, reach in ((marks['lens'], 5), (marks.get('far'), 5), (marks['tip'], 6)) if point]
    left = [(x, y) for y in range(height) for x in range(width)
            if grid[y][x] in AMBER + RED and (x, y) not in placed and any(
                math.hypot(x - point[0], y - point[1]) <= reach for point, reach in spots)]
    for x, y in left:
        out[y - y0][x - x0] = 0
    for (x, y), value in placed.items():
        out[y - y0][x - x0] = value
    # a hand the pose holds in front of its face stays in front: its glove pixels and the ink round them
    for bx0, by0, bx1, by1 in spec.get('over', ()):
        hand = {(x, y) for y in range(max(0, by0), min(height, by1 + 1)) for x in range(max(0, bx0), min(width, bx1 + 1))
                if grid[y][x] in GLOVE}
        ring = {(x + a, y + b) for x, y in hand for a in (-1, 0, 1) for b in (-1, 0, 1)
                if 0 <= x + a < width and 0 <= y + b < height and grid[y + b][x + a] == 1}
        for x, y in hand | ring:
            out[y - y0][x - x0] = grid[y][x]
    outside, todo = {(0, 0)}, [(0, 0)]
    while todo:
        cx, cy = todo.pop()
        for a, b in N4:
            near = (cx + a, cy + b)
            if 0 <= near[0] < wide and 0 <= near[1] < tall and not out[near[1]][near[0]] and near not in outside:
                outside.add(near)
                todo.append(near)
    for x, y in left:
        if (x - x0, y - y0) not in outside and not out[y - y0][x - x0]:
            out[y - y0][x - x0] = 3
    # pieces left detached from the bird are dropped
    seen, parts = set(), []
    for y in range(tall):
        for x in range(wide):
            if out[y][x] and (x, y) not in seen:
                part, todo = [(x, y)], [(x, y)]
                seen.add((x, y))
                while todo:
                    cx, cy = todo.pop()
                    for a, b in N4:
                        near = (cx + a, cy + b)
                        if 0 <= near[0] < wide and 0 <= near[1] < tall and out[near[1]][near[0]] and near not in seen:
                            seen.add(near)
                            part.append(near)
                            todo.append(near)
                parts.append(part)
    keep = max(parts, key=len)
    for part in parts:
        if part is not keep:
            for x, y in part:
                out[y][x] = 0
    rows = [i for i, row in enumerate(out) if any(row)]
    cols = [i for i in range(wide) if any(row[i] for row in out)]
    out = [row[cols[0]:cols[-1] + 1] for row in out[rows[0]:rows[-1] + 1]]
    return out, (x0 + cols[0], y0 + rows[0])


def restyle(cleaned, colors, clean):
    """cleaned: id -> rows after pose_cleanup's own rules.
    Returns id -> (rows, hotspot) for this pass's frames, the hotspot moved with the drawing's origin."""
    from scale_audit import turned
    record = json.loads(RECORD.read_text())
    donor = to_grid(cleaned[DONOR])
    out = {}
    for fid, spec in record['frames'].items():
        if 'copy' in spec:
            continue
        grid = thin_rim(to_grid(cleaned[fid]))
        shift = (0, 0)
        if spec.get('head'):
            grid, shift = transplant(grid, spec['head'], spec['landmarks'], donor, record['kit'])
        out[fid] = (to_rows(ink(grid)), [spec['hotspot_before'][0] - shift[0], spec['hotspot_before'][1] - shift[1]])
    for fid, spec in record['frames'].items():
        if 'copy' in spec:
            rows, hot = out[spec['copy']['source']]
            image, hot = turned(to_image(to_grid(rows), colors), hot, spec['copy']['angle'], spec['copy']['flip'])
            out[fid] = (to_rows(of_image(image)), hot)
    return out


# ---------------------------------------------------------------- reading the landmarks (needs the sources)
def measure():
    import colorsys
    import scale_audit as audit
    colors = audit.palette()
    table = json.loads(audit.AUDIT.read_text())['frames']
    listed = {f['id']: f for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    uncleaned = {d['id']: d['source_rows'] for d in json.loads((PROD / 'pose-cleanup.json').read_text())['drawings']}
    record = json.loads(RECORD.read_text()) if RECORD.exists() else {}
    before = record.get('frames', {})
    read = {}
    for source in audit.sources(colors):
        fid = source['id']
        if fid == DONOR:
            donor_figure = source['figure']
        if fid not in uncleaned or fid == DONOR or listed[fid]['animation'] in REFERENCE | ENTERED:
            continue
        figure = source['figure']
        px = figure.load()
        box = source['box']
        x0, y0, x1, y1 = 0, 0, figure.width, figure.height
        if box:
            x0, y0 = max(0, box[0] - 10), max(0, box[1] - 10)
            x1, y1 = min(figure.width, box[2] + 10), min(figure.height, box[3] + 10)
        mask = {(x, y) for y in range(y0, y1) for x in range(x0, x1) if audit.lens_colour(px[x, y])}
        blobs = []
        for patch in audit.components(mask):
            if len(patch) < 120:
                continue
            hues = sorted(colorsys.rgb_to_hsv(*(v / 255 for v in px[q][:3]))[0] for q in patch if not audit.glint(px[q]))
            if hues and audit.LENS_HUE[0] <= hues[len(hues) // 2] <= (audit.BOXED_HUE if box else audit.LENS_HUE[1]):
                blobs.append(patch)
        if not blobs:
            continue
        blobs.sort(key=len, reverse=True)

        def centre(points):
            return sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points)
        lens = centre(blobs[0])
        far = centre(blobs[1]) if len(blobs) > 1 and len(blobs[1]) > 0.25 * len(blobs[0]) else None
        yellow = {(x, y) for y in range(figure.height) for x in range(figure.width) if audit.beak_colour(px[x, y])}
        yellow -= set().union(*map(set, blobs))
        beaks = [c for c in audit.components(yellow) if len(c) >= 150]
        tip = max(max(beaks, key=len), key=lambda p: math.hypot(p[0] - lens[0], p[1] - lens[1])) if beaks else None
        rows = uncleaned[fid]
        fx, fy = figure.width / len(rows[0]), figure.height / len(rows)

        def frame(point):
            return point and [round(point[0] / fx, 1), round(point[1] / fy, 1)]
        read[fid] = dict(lens=frame(lens), far=frame(far), tip=frame(tip), band=table[fid]['band'], span=table[fid]['span'])
    frames = {}
    for fid, rows in uncleaned.items():
        animation = listed[fid]['animation']
        if fid == DONOR or animation in REFERENCE:
            continue
        if animation in ENTERED:
            frames[fid] = before[fid]
            continue
        kept = before.get(fid, {})
        marks = dict(read.get(fid) or dict(lens=None, far=None, tip=None))
        if fid in HAND_MARKS:
            marks.update(HAND_MARKS[fid], by_hand=sorted(HAND_MARKS[fid]))
        entry = dict(animation=animation, hotspot_before=kept.get('hotspot_before') or listed[fid]['hotspot'],
                     landmarks=marks if marks['lens'] else None)
        entry['head'], entry['why'] = decide(fid, animation, entry['landmarks'], to_grid(rows))
        frames[fid] = entry
    poses = json.loads((PROD / 'object-poses/provenance.json').read_text())['frames']
    by_mapping = {f['mapping']: f['id'] for f in poses if 'source' not in f}
    for meta in poses:                 # an object pose that is another one turned or mirrored follows it
        if 'source' in meta and meta['id'] in frames:
            parent = by_mapping[meta['source']]
            frames[meta['id']].update(head=None, copy=dict(source=parent, angle=meta['angle'], flip=meta['flip']),
                                      why=f"{parent} turned {meta['angle']} degrees" +
                                      (' and mirrored' if meta['flip'] else '') + ': redrawn from it after this pass')
    loops = {}
    for entry in frames.values():      # turns of one animation within SNAP degrees share their median
        if entry['head'] and entry['head']['angle'] and entry['animation'] != 'NATIVE_POSE':
            loops.setdefault(entry['animation'], []).append(entry)
    for members in loops.values():
        members.sort(key=lambda e: e['head']['angle'])
        group = [members[0]]
        for entry in members[1:] + [None]:
            if entry and entry['head']['angle'] - group[-1]['head']['angle'] <= SNAP:
                group.append(entry)
                continue
            shared = sorted(e['head']['angle'] for e in group)[len(group) // 2]
            for e in group:
                e['head']['angle'] = shared
            group = [entry]
    needed = sorted({(f['head']['beak'], f['head']['angle']) for f in frames.values() if f['head'] and f['head']['angle']})
    record['kit'] = dict(donor=DONOR, neck=NECK, lens=list(LENS), tip=list(TIP),
                         turned=turned_heads(donor_figure, needed, colors))
    record.update(frames=dict(sorted(frames.items())))
    RECORD.write_text(json.dumps(record, indent=1) + '\n')
    placed = sum(1 for f in frames.values() if f['head'])
    print(f'{len(frames)} drawings: {placed} take the reference head ({len(needed)} turned heads built), '
          f'{len(frames) - placed} keep their own')


def turned_heads(figure, needed, colors):
    """The donor's head at each turn a pose needs, reduced from the donor's own source drawing turned about
    its lens: {beak: {angle: {rows, lens}}}. The open beak is made on the source first: the lower mandible
    is moved down two frame pixels and the gap filled with mouth red."""
    import pipeline
    from walk_cleanup import clean
    lum = [0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] for c in colors]
    fx, fy = figure.width / 24, figure.height / 37       # the donor frame is 24x37
    factor = (fx + fy) / 2
    opened = figure.copy()
    jaw = figure.crop((round(15.5 * fx), round(15.4 * fy), figure.width, round(17.6 * fy)))
    opened.paste((0, 0, 0, 0), (round(15.5 * fx), round(15.4 * fy), figure.width, round(19.8 * fy)))
    opened.paste((182, 36, 36, 255), (round(16.4 * fx), round(15.2 * fy), round(20.2 * fx), round(17.6 * fy)))
    opened.alpha_composite(jaw, (round(15.5 * fx), round(17.4 * fy)))
    out = {}
    for beak, angle in needed:
        source = opened if beak == 'open' else figure
        size = round(CANVAS * factor)
        centre = ((CANVAS // 2 + 0.5) * factor, (CANVAS // 2 + 0.5) * factor)
        canvas = Image.new('RGBA', (size, size))
        canvas.alpha_composite(source, (round(centre[0] - (LENS[0] + 0.5) * fx), round(centre[1] - (LENS[1] + 0.5) * fy)))
        canvas = canvas.rotate(-angle, resample=Image.Resampling.BICUBIC, center=centre)
        small = pipeline.outline(pipeline.indexed(canvas.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS), colors))
        grid = clean(of_image(small), lum)
        half = CANVAS // 2
        grid = [[v if v and in_head(x - half, y - half, angle, beak == 'open') else 0 for x, v in enumerate(row)]
                for y, row in enumerate(grid)]
        rows = [i for i, row in enumerate(grid) if any(row)]
        cols = [i for i in range(CANVAS) if any(row[i] for row in grid)]
        out.setdefault(beak, {})[str(angle)] = dict(
            rows=to_rows([row[cols[0]:cols[-1] + 1] for row in grid[rows[0]:rows[-1] + 1]]),
            lens=[half - cols[0], half - rows[0]])
    return out


def decide(fid, animation, marks, grid):
    """(head spec or None, why) from a drawing's landmarks."""
    if fid in HAND:
        return HAND[fid]
    if animation in NO_HEAD:
        return None, NO_HEAD[animation]
    if not marks or not marks['lens']:
        return None, 'no goggle lens in view (back view, curled or hidden head)'
    if not marks['tip']:
        return None, 'no beak in view to set the head by'
    lens, tip, far = marks['lens'], marks['tip'], marks['far']
    span = math.hypot(tip[0] - lens[0], tip[1] - lens[1])
    donor_span = math.hypot(TIP[0] - LENS[0], TIP[1] - LENS[1])
    if not 0.7 <= span / donor_span <= 1.35:
        return None, f'beak {span:.1f} px from the lens against the reference {donor_span:.1f}: turned to or from the viewer'
    if far:
        ax, ay = far[0] - lens[0], far[1] - lens[1]
        along = ((tip[0] - lens[0]) * ax + (tip[1] - lens[1]) * ay) / (ax * ax + ay * ay)
        if 0.15 < along < 0.85:
            return None, 'front view: the beak sits between the two lenses, and the reference head is three-quarter'
    # which side of the lens-to-beak line the head's mass lies on: the donor's is on the left of that direction
    height, width = len(grid), len(grid[0])
    mass = [(x, y) for y in range(height) for x in range(width)
            if grid[y][x] and math.hypot(x - lens[0], y - lens[1]) <= 11]
    cx, cy = sum(p[0] for p in mass) / len(mass), sum(p[1] for p in mass) / len(mass)
    side = (tip[0] - lens[0]) * (cy - lens[1]) - (tip[1] - lens[1]) * (cx - lens[0])
    mirror = side < 0
    donor = math.degrees(math.atan2(TIP[1] - LENS[1], TIP[0] - LENS[0]))
    if mirror:
        donor = 180 - donor
    angle = (math.degrees(math.atan2(tip[1] - lens[1], tip[0] - lens[0])) - donor + 180) % 360 - 180
    angle = marks['angle'] if 'angle' in marks else 0 if abs(angle) <= STRAIGHT else round(angle)
    if abs(angle) > FAR_TURN:
        return None, (f'head turned {angle} degrees (tumbling, diving or inverted): seen briefly, and a reference head '
                      'turned that far read no better than its own')
    # an open beak shows red beside the beak tip
    red = sum(1 for y in range(height) for x in range(width)
              if grid[y][x] in (10, 11) and math.hypot(x - tip[0], y - tip[1]) <= 7)
    spec = dict(lens=[round(lens[0]), round(lens[1])], angle=angle, mirror=mirror, beak='open' if red >= 3 else 'closed')
    return spec, 'reference head on the pose\'s lens' + (f', turned {angle} degrees' if angle else '') + \
        (', mirrored' if mirror else '') + (', open beak' if spec['beak'] == 'open' else '')


def check():
    record = json.loads(RECORD.read_text())
    listed = {f['id']: f for f in json.loads((SHEET / 'frames.yaml').read_text())['frames']}
    drawings = {d['id'] for d in json.loads((PROD / 'pose-cleanup.json').read_text())['drawings']}
    wrong = []
    for fid in drawings:
        reference = fid == DONOR or listed[fid]['animation'] in REFERENCE
        if reference == (fid in record['frames']):
            wrong.append(fid + (' is a reference and must not be listed' if reference else ' has no entry'))
    for fid, entry in record['frames'].items():
        if entry['head'] and not {'lens', 'angle', 'mirror', 'beak'} <= entry['head'].keys():
            wrong.append(fid + ' head is incomplete')
        if not entry.get('why'):
            wrong.append(fid + ' has no reason')
    if wrong:
        raise SystemExit('Line-art record: ' + '; '.join(sorted(wrong)))
    placed = sum(1 for f in record['frames'].values() if f['head'])
    print(f"{len(record['frames'])} drawings: {placed} carry the reference head, "
          f"{len(record['frames']) - placed} keep their own with a reason")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('mode', nargs='?', choices=('measure',))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.mode == 'measure':
        measure()
    if args.check or not args.mode:
        check()


if __name__ == '__main__':
    main()
