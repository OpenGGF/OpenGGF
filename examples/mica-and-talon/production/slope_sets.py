#!/usr/bin/env python3
"""Derive every rotation-derived angle set of MICA and TALON with the mode reduction.

Purpose: one maintained derivation for the 45 degree frames that are turned from a flat drawing
rather than drawn: MICA's WALK/RUN slopes, her homing-dive directions, her Open Cone (Gilded and
Hyper) slopes and Solar TALON's slopes. Each is rebuilt from the flat in the tree with
tools/modding/playable_art/derive_slopes.py method "mode": the flat's silhouette contour is lifted,
the drawing is enlarged with Scale2x three times, turned 45 degrees and reduced to the commonest
index of each 8x8 block, speckle is folded into the surrounding shade of its own material
(despeckle.py, the rules of TALON's walk cleanup), and only then is the silhouette inked. Steep is
the flat's exact quarter turn and inverted the quarter turn of up45, as before. Flat drawings are
never changed. TALON's normal WALK/RUN slopes use the same steps in talon/walk_cleanup.py, which
also cleans their flats.
Inputs: the flats and manifests under art/; the support distance each slope frame keeps
(mica/native-pose-core.json native_support, or support_kept in the powered generation records).
Writes the slope PNGs and keeps manifests, recipe rows and generation records in step.
Origin: owner question after the TALON walk cleanup, 2026-11-12 ("shouldn't we be using this for
all of them now then?"). Pillow; no ROM, Java or source generation needed.
Run from the repository root; --check compares without writing.

Per set: `ring` is the indices that form the flat's silhouette contour (ink; a powered bank also
uses its contour slot 2, and Open Cone a brown 15), and `contour` the slot a powered bank moves the
new ink to, as its flats do. `material` lists what may be despeckled; everything else (ink, coral,
goggles, shoes, flame tips, the Solar bronze bars) is carried through the turn untouched, and
materials in `protect` are never darkened.
"""
import argparse
import json
import math
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ART = ROOT / 'examples/mica-and-talon/art'
PROD = ROOT / 'examples/mica-and-talon/production'
sys.path.insert(0, str(ROOT / 'tools/modding/playable_art'))
from derive_slopes import ANGLES, derive, rotate  # noqa: E402
from despeckle import despeckle  # noqa: E402

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
LABELS = ('up45', 'steep', 'inverted')
# MICA, normal and Open Cone: scales or plates 2-6, sand face and belly 7-9, white gloves 12-13.
MICA = dict(material={2: 'scales', 3: 'scales', 4: 'scales', 5: 'scales', 6: 'scales',
                      7: 'sand', 8: 'sand', 9: 'sand', 12: 'white', 13: 'white'}, protect=('sand', 'white'))
# Solar TALON: white-gold plumage 3-5, cream/white 7, 8, 12, 13. Bronze 2 (bars, moustache) and flame tip 6 stay.
SOLAR = dict(material={3: 'plumage', 4: 'plumage', 5: 'plumage', 7: 'light', 8: 'light', 12: 'light', 13: 'light'},
             protect=('light',))
HOMING = {'HOMING_DIVE_FORWARD': 45, 'HOMING_DIVE_FORWARD_UP': 90, 'HOMING_DIVE_UP': 135, 'HOMING_DIVE_DOWN': -45}
STAMP = 'slope_sets.py'
MICA_NOTE = ('Deterministic Scale2x slope from {base}; +{angle} degrees CCW, mode reduction with the silhouette ink '
             'lifted and material despeckle before inking (slope_sets.py, 2026-11-12); native support, integer '
             'contact alignment.')
HOMING_NOTE = ' Mode reduction and material despeckle before inking since 2026-11-12 (slope_sets.py).'
POWERED_NOTE = 'mode: contour lifted, Scale2x x3, commonest index per block, material despeckle, contour slot (slope_sets.py, 2026-11-12)'


def to_grid(image):
    pixels = image.load()
    return [[pixels[x, y] for x in range(image.width)] for y in range(image.height)]


def rows_of(image):
    return [''.join(format(v, 'x') for v in row) for row in to_grid(image)]


def finisher(style):
    def finish(image):
        palette = image.getpalette()
        lum = [0.299 * palette[i * 3] + 0.587 * palette[i * 3 + 1] + 0.114 * palette[i * 3 + 2] for i in range(16)]
        grid = despeckle(to_grid(image), lum, style['material'], style['protect'])
        out = Image.new('P', image.size)
        out.putpalette(palette)
        out.putdata([v for row in grid for v in row])
        return out
    return finish


def on_contour_slot(image, slot):
    """A powered bank draws its outer silhouette on the cycling contour slot, never on fixed ink."""
    out = image.copy()
    pixels, target = image.load(), out.load()
    for y in range(image.height):
        for x in range(image.width):
            if pixels[x, y] == 1 and any(
                    not (0 <= x + a < image.width and 0 <= y + b < image.height) or pixels[x + a, y + b] == 0
                    for a, b in N4):
                target[x, y] = slot
    return out


def support_of(image, hotspot, angle):
    sn, cs = math.sin(math.radians(angle)), math.cos(math.radians(angle))
    return max((x - hotspot[0]) * sn + (y - hotspot[1]) * cs
               for y in range(image.height) for x in range(image.width) if image.getpixel((x, y)))


def slope(flat, hotspot, label, support, style, ring=(1,), contour=None):
    """One slope frame from its flat: (image, hotspot)."""
    image, hot = derive(flat, hotspot, ANGLES[label], support, 'mode', finisher(style), ring)
    if contour and label != 'steep':
        image = on_contour_slot(image, contour)
    return image, hot


def homing(base, hotspot, angle, style):
    canvas = Image.new('P', (128, 128))
    canvas.putpalette(base.getpalette())
    canvas.paste(base, (64 - hotspot[0], 64 - hotspot[1]))
    finish = finisher(style)
    turned = rotate(canvas, 45, 'mode', finish).transpose(Image.Transpose.ROTATE_270) if angle == -45 \
        else rotate(canvas, angle, 'mode', finish)
    box = turned.getbbox()
    return turned.crop(box), [64 - box[0], 64 - box[1]]


def base_file(name):
    for label in LABELS:
        name = name.replace(f'-{label}-', '-')
    return name


class Job:
    """Collects wanted pixels and JSON edits, then writes or checks them."""

    def __init__(self, check):
        self.check, self.stale, self.frames, self.docs = check, [], 0, {}

    def json(self, path, indent=2):
        if path not in self.docs:
            text = path.read_text()
            self.docs[path] = [text, json.loads(text), indent]
        return self.docs[path][1]

    def png(self, path, image):
        self.frames += 1
        with Image.open(path) as actual:
            same = actual.mode == 'P' and actual.size == image.size and \
                list(actual.get_flattened_data()) == list(image.get_flattened_data()) and \
                actual.getpalette()[:48] == image.getpalette()[:48]
        if not same:
            self.stale.append(path)
            if not self.check:
                image.save(path, transparency=0, optimize=True, bits=4)

    def finish(self):
        for path, (text, data, indent) in self.docs.items():
            fresh = json.dumps(data, indent=indent) + '\n'
            if fresh != text:
                self.stale.append(path)
                if not self.check:
                    path.write_text(fresh)


def mica_sheet(job):
    sheet = ART / 'mica/sheet'
    manifest = job.json(sheet / 'frames.yaml')
    recipe = {f['id']: f for f in job.json(PROD / 'mica/recipe.json')['frames']}
    core = job.json(PROD / 'mica/native-pose-core.json')['frames']
    by_id = {f['id']: f for f in manifest['frames']}
    mirror = PROD / 'mica/homing-directions'
    mirror_manifest = {f['id']: f for f in job.json(mirror / 'frames.yaml')['frames']}
    mirror_recipe = {f['id']: f for f in job.json(mirror / 'recipe.json')['frames']}
    for frame in manifest['frames']:
        name = frame['id']
        if frame['animation'] in ('WALK', 'RUN') and frame['angle_set'] != 'flat':
            group, label, phase = name.split('-')
            base = by_id[f'{group}-{phase}']
            with Image.open(sheet / base['file']) as flat:
                image, hot = slope(flat, base['hotspot'], label, core[name]['native_support'], MICA)
            note = MICA_NOTE.format(base=base['id'], angle=ANGLES[label]) if label != 'steep' else frame['notes']
            core[name].update(geometry_reference={'size': list(image.size), 'hotspot': hot},
                              native_size=list(image.size), hotspot=hot, owner_fix=note)
            targets = [(frame, recipe[name])]
        elif frame['animation'] in HOMING:
            angle = HOMING[frame['animation']]
            base = by_id['homing_dive-' + name.rsplit('-', 1)[1]]
            with Image.open(sheet / base['file']) as flat:
                image, hot = homing(flat, base['hotspot'], angle, MICA)
            note = frame['notes'] if STAMP in frame['notes'] or angle == 90 else frame['notes'] + HOMING_NOTE
            targets = [(frame, recipe[name]), (mirror_manifest[name], mirror_recipe[name])]
            job.png(mirror / 'frames' / (name + '.png'), image)
        else:
            continue
        job.png(sheet / frame['file'], image)
        for listed, pinned in targets:
            listed['hotspot'] = hot
            listed['notes'] = pinned['notes'] = note
            pinned['native_pixels'] = {'rows': rows_of(image), 'anchor': hot}


def powered(job, character, host, style, ring, record_path):
    root = ART / character / 'powered' / host
    manifest = job.json(root / 'frames.yaml')
    by_file = {Path(f['file']).name: f for f in manifest['frames']}
    records = {r['file']: r for r in job.json(record_path)['frames']}
    for frame in manifest['frames']:
        label = frame.get('angle_set', 'flat')
        if label == 'flat':
            continue
        path = (root / frame['file']).resolve()
        record = records[str(path.relative_to(ART))]
        base = by_file[base_file(Path(frame['file']).name)]
        if 'support_kept' not in record:  # first run only: keep the support of the frame being replaced
            if job.check:
                raise SystemExit(f'No support_kept recorded for {record["file"]}')
            with Image.open(path) as old:
                record['support_kept'] = support_of(old, frame['hotspot'], ANGLES[label])
        with Image.open((root / base['file']).resolve()) as flat:
            image, hot = slope(flat, base['hotspot'], label, record['support_kept'], style, ring, contour=2)
        job.png(path, image)
        frame['hotspot'] = hot
        record.update(hotspot=hot, size=list(image.size), selected_pixels={'rows': rows_of(image), 'anchor': hot})
        if 'authored_area' in record:
            record['authored_area'] = sum(1 for v in image.get_flattened_data() if v)
        if label != 'steep':
            record['reduction'] = POWERED_NOTE


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    job = Job(parser.parse_args().check)
    mica_sheet(job)
    for host in ('s2', 's3k'):
        powered(job, 'mica', host, MICA, (1, 2, 15), PROD / 'mica/powered/powered-concepts-generation.json')
        powered(job, 'talon', host, SOLAR, (1, 2), PROD / 'talon/powered-concepts-generation.json')
    job.finish()
    if job.check and job.stale:
        raise SystemExit('Stale slope sets: ' + ', '.join(str(p.relative_to(ROOT)) for p in job.stale))
    print(f'{job.frames} derived frames ' + ('match' if job.check else f'written ({len(job.stale)} files changed)'))


if __name__ == '__main__':
    main()
