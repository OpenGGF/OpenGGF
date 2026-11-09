"""Owner slope correction: lossless quarter turns, EPX corners and reproducibility."""
import importlib.util
import json
import sys
from pathlib import Path
import unittest
from PIL import Image

HERE=Path(__file__).parent
sys.path.insert(0,str(HERE/'playable_art'))
import derive_slopes as slopes


class DeterministicSlopeTests(unittest.TestCase):
    def image(self):
        im=Image.new('P',(9,9));im.putpalette([i for i in range(256) for _ in range(3)])
        im.putpixel((3,2),4);im.putpixel((4,2),5);im.putpixel((4,3),10)
        return im

    def test_quarter_turn_is_exact_and_four_turns_restore_bytes(self):
        im=self.image();turned=slopes.rotate(im,90)
        self.assertEqual(turned.getpixel((2,5)),4)
        for _ in range(3):turned=slopes.rotate(turned,90)
        self.assertEqual(im.tobytes(),turned.tobytes())
        self.assertEqual(im.getpalette(),turned.getpalette())

    def test_inverted_is_lossless_quarter_turn_of_up45(self):
        im=self.image()
        self.assertEqual(slopes.rotate(im,135).tobytes(),
                         slopes.rotate(im,45).transpose(Image.Transpose.ROTATE_90).tobytes())
        self.assertLessEqual(set(slopes.rotate(im,45).get_flattened_data()),{0,1,4,5,10})

    def test_scale2x_corner_rule_preserves_original_indices(self):
        im=Image.new('P',(3,3));im.putpixel((1,0),4);im.putpixel((0,1),4);im.putpixel((1,1),5)
        out=slopes.scale2x(im)
        self.assertEqual(out.getpixel((2,2)),4)
        self.assertEqual(out.getpixel((3,2)),5)

    def test_every_shipped_slope_rederives_from_flat_pixels(self):
        root=HERE.parents[1]/'examples/mica-and-talon/art/mica'
        m=json.loads((root/'frames.yaml').read_text());meta=json.loads((root/'native-pose-core.json').read_text())['frames']
        by_id={f['id']:f for f in m['frames']}
        for f in m['frames']:
            if f['angle_set']=='flat':continue
            with self.subTest(frame=f['id']):
                group,slope,phase=f['id'].split('-');base=by_id[f'{group}-{phase}']
                with Image.open(root/base['file']) as flat:
                    im,hot=slopes.derive(flat,base['hotspot'],slopes.ANGLES[slope],meta[f['id']]['native_support'])
                self.assertEqual(f['hotspot'],hot)
                with Image.open(root/f['file']) as actual:
                    self.assertEqual(im.size,actual.size)
                    self.assertEqual(im.tobytes(),actual.tobytes())
