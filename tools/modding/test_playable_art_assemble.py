"""Indexed playable-sheet assembly preserves source pixels, anchors and slope order."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('assemble', Path(__file__).parent/'playable_art/assemble.py')
assembler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assembler)


class PlayableArtAssemblyTest(unittest.TestCase):
    def test_roundtrip_pixels_hotspots_slopes_and_custom_hook(self):
        art = ROOT/'examples/mica-and-talon/art/mica'
        source = json.loads((art/'frames.yaml').read_text())['frames']
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            manifest, custom, stats = assembler.assemble(art, out)
            before = {p.name:p.read_bytes() for p in out.iterdir()}
            committed = ROOT/'examples/mica-and-talon/src/main/mod'
            self.assertEqual(before, {name:(committed/name).read_bytes() for name in before})
            assembler.assemble(art, out)
            self.assertEqual(before, {p.name:p.read_bytes() for p in out.iterdir()})
            self.assertEqual(stats, dict(frames=99, maxTilesPerFrame=36, animations=18))
            self.assertEqual(len(custom['HOMING_DIVE']), 4)
            self.assertEqual(custom['HOMING_DIVE'], manifest['animations']['HOMING_DIVE']['frames'])
            with Image.open(out/'mica.png') as atlas:
                for frame, baked in zip(source, manifest['frames']):
                    original = Image.open(art/frame['file'])
                    restored = Image.new('P', (64,48))
                    for piece in baked['pieces']:
                        x, y = piece['xOffset']+frame['hotspot'][0], piece['yOffset']+frame['hotspot'][1]
                        w, h = piece['widthPixels'], piece['heightPixels']
                        self.assertTrue(w <= 32 and h <= 32 and w % 8 == h % 8 == 0)
                        sx, sy = piece['sourceX'], piece['sourceY']
                        restored.paste(atlas.crop((sx,sy,sx+w,sy+h)), (x,y))
                    self.assertEqual(original.tobytes(), restored.crop((0,0,*original.size)).tobytes())
            for name, stride in [('WALK',8), ('RUN',4)]:
                cycles = manifest['animations'][name]['slopes']
                self.assertEqual([stride]*4, list(map(len,cycles)))
                for slope, cycle in zip(assembler.SLOPES,cycles):
                    self.assertTrue(all(source[i]['angle_set']==slope for i in cycle))
            idle = manifest['animations']['WAIT']
            self.assertEqual('loop', idle['end'])
            self.assertEqual(186, sum(idle['durations'][:8]))
            self.assertEqual([0,1,0,1,2,0,1,0],
                             [source[i]['order'] for i in idle['frames'][:8]])
            self.assertEqual(6, len(set(idle['frames'])))
            self.assertEqual(len(idle['frames']), len(idle['durations']))
            self.assertEqual('hold',manifest['animations']['DUCK']['end'])
            self.assertEqual('WALK',manifest['animations']['SPRING']['next'])
            self.assertEqual([48],manifest['animations']['SPRING']['durations'])
            by_id = {f['id']:i for i,f in enumerate(source)}
            roll = manifest['animations']['ROLL']
            self.assertTrue(roll['speedDriven'])
            self.assertEqual([by_id[k] for i in range(4) for k in (f'roll-{i:02}', 'roll-blur')],roll['frames'])
            charge = manifest['animations']['SPINDASH']
            self.assertEqual([by_id[k] for i in range(5) for k in ('spindash-blur',f'spindash-{i:02}')],charge['frames'])
            self.assertEqual([1]*10,charge['durations'])
            self.assertEqual([by_id['hurt-00']],manifest['animations']['HURT']['frames'])


if __name__ == '__main__':
    unittest.main()


class SlopeContactTests(unittest.TestCase):
    def test_native_contact_and_hotspot_in_all_rotated_phases(self):
        import math
        art = ROOT / 'examples/mica-and-talon/art/mica'
        manifest = json.loads((art / 'frames.yaml').read_text())
        provenance = json.loads((art / 'native-pose-core.json').read_text())['frames']
        slopes = [f for f in manifest['frames'] if f['angle_set'] != 'flat']
        self.assertEqual(len(slopes), 36)
        for f in slopes:
            with self.subTest(frame=f['id']):
                source = provenance[f['id']]
                hx, hy = f['hotspot']
                geometry = source['geometry_reference']
                self.assertEqual(f['hotspot'], geometry['hotspot'])
                angle = math.radians({'up45': 45, 'steep': 90, 'inverted': 135}[f['angle_set']])
                with Image.open(art / f['file']) as im:
                    self.assertEqual(list(im.size), geometry['size'])
                    support = max((x-hx)*math.sin(angle) + (y-hy)*math.cos(angle)
                                  for y in range(im.height) for x in range(im.width)
                                  if im.getpixel((x, y)))
                self.assertLessEqual(support, source['native_support'] + 0.00001)
                self.assertLessEqual(source['native_support'] - support, 0.71)
