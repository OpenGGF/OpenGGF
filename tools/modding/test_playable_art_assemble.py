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
            self.assertEqual(stats, dict(frames=132, maxTilesPerFrame=30, animations=22))
            self.assertEqual('hang-00', source[manifest['nativeFrames']['hang/0']]['id'])
            self.assertEqual('wait-03', source[manifest['nativeFrames']['wait/fidget']]['id'])
            self.assertEqual('native-pose-97', source[manifest['nativeFrames']['roll/1']]['id'])
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
            native = json.loads((art/'round3/native-scripts.json').read_text())
            self.assertEqual(native['WAIT']['mappingSequence'],
                             [source[i]['order']+1 for i in idle['frames']])
            self.assertEqual([6]*len(idle['frames']), idle['durations'])
            self.assertEqual(9, len(set(idle['frames'])))
            for name in ('ROLL', 'SPINDASH'):
                self.assertEqual(native[name]['ids'],
                                 [source[i]['id'] for i in manifest['animations'][name]['frames']])
            self.assertEqual([1]*10, manifest['animations']['SPINDASH']['durations'])
            self.assertEqual('hold',manifest['animations']['DUCK']['end'])
            self.assertEqual('WALK',manifest['animations']['SPRING']['next'])
            self.assertEqual([48],manifest['animations']['SPRING']['durations'])


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


class TalonAssemblyTests(unittest.TestCase):
    def test_original_talon_sheet_timing_and_palette(self):
        art = ROOT/'examples/mica-and-talon/art/talon'
        source = json.loads((art/'frames.yaml').read_text())
        colors = json.loads((art/'palette.yaml').read_text())['colors']
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            manifest, custom, stats = assembler.assemble(art, out)
            self.assertEqual(82, stats['frames'])
            self.assertEqual(13, stats['animations'])
            self.assertFalse((out/'mica.png').exists())
            committed = ROOT/'examples/mica-and-talon/src/main/mod'
            for file in out.iterdir():
                self.assertEqual(file.read_bytes(), (committed/file.name).read_bytes())
            self.assertEqual([2]*6,manifest['animations']['TAILS_FLY']['durations'])
            self.assertEqual(6,len(set(manifest['animations']['TAILS_FLY']['frames'])))
            self.assertEqual([4]*6,manifest['animations']['TAILS_FLY_TIRED']['durations'])
            self.assertEqual(6,len(set(manifest['animations']['TAILS_FLY_TIRED']['frames'])))
            for name in ('ROLL','SPINDASH'):
                seq=manifest['animations'][name]['frames']
                self.assertEqual(6,len(seq))
                self.assertEqual(1,len(set(seq[1::2])))
            self.assertEqual([48],manifest['animations']['SPRING']['durations'])
            self.assertEqual([4],manifest['animations']['HURT']['durations'])
            with Image.open(out/'talon.png') as sheet:
                self.assertEqual(sum(colors,[]),sheet.getpalette()[:48])
                for f,baked in zip(source['frames'],manifest['frames']):
                    original=Image.open(art/f['file'])
                    restored=Image.new('P',original.size)
                    for p in baked['pieces']:
                        sx,sy=p['sourceX'],p['sourceY']
                        restored.paste(sheet.crop((sx,sy,sx+p['widthPixels'],sy+p['heightPixels'])),
                                       (p['xOffset']+f['hotspot'][0],p['yOffset']+f['hotspot'][1]))
                    self.assertEqual(original.tobytes(),restored.tobytes())
            for name,n in [('WALK',8),('RUN',4)]:
                self.assertEqual([n]*4,list(map(len,manifest['animations'][name]['slopes'])))
