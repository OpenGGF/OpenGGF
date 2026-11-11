"""Authored title banks must package every selector, palette and plane mask."""
import json
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / 'examples/mica-and-talon/art'

class TitleArtTest(unittest.TestCase):
    def test_both_characters_have_complete_native_title_banks(self):
        for character in ('mica', 'talon'):
            root = ROOT / character / 'titles'
            manifest = json.loads((root / f'../../../production/{character}/titles/manifest.json').read_text())['hosts']
            expected = {
                'S1': {f's1-{i}.png' for i in range(8)},
                'S2': {f's2-{i}.png' for i in (0,1,2,3,4,5,6,7,9,18,19)},
                'S3K': {f's3k-plane-{i}-{half}.png' for i in range(1,14) for half in ('left','right')}
                       | {f's3k-sprite-{i}.png' for i in range(5)}
                       | {f's3k-plane-{i}.mask' for i in range(1,14)}
                       | {'s3k-plane-13.clear'},
            }
            colors = json.loads((ROOT / character / 'sheet/palette.yaml').read_text())['colors']
            for host, files in expected.items():
                host_colors = colors if "palette" not in manifest[host] else json.loads(
                    (root / f"../../../production/{character}/titles" / manifest[host]["palette"]).read_text())["colors"]
                self.assertEqual(files, {Path(f).name for f in manifest[host]['files']})
                for name in files:
                    with self.subTest(character=character, host=host, name=name):
                        path = root / name
                        if path.suffix in ('.mask', '.clear'):
                            mask = path.read_bytes()
                            self.assertEqual(320*224, len(mask))
                            self.assertLessEqual(set(mask), {0,1} if path.suffix == '.mask' else {0,15})
                            if path.suffix == '.clear':
                                ownership=(root / 's3k-plane-13.mask').read_bytes()
                                self.assertTrue(all(not ink or owned for ink,owned in zip(mask,ownership)))
                        else:
                            with Image.open(path) as image:
                                self.assertEqual('P', image.mode)
                                self.assertEqual(sum(host_colors, []), image.getpalette()[:48])
                                self.assertLessEqual(max(image.get_flattened_data()),15)
                                self.assertEqual(0,image.info['transparency'])

if __name__ == '__main__':
    unittest.main()
