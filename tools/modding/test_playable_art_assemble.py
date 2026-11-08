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
            self.assertEqual(stats, dict(frames=98, maxTilesPerFrame=36, animations=17))
            self.assertEqual(len(custom['HOMING_DIVE']), 4)
            self.assertNotIn('HOMING_DIVE', manifest['animations'])
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
            self.assertEqual([24,24],manifest['animations']['SPRING']['durations'])


if __name__ == '__main__':
    unittest.main()
