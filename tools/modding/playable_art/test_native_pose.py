"""Bounded Python checks for the W7 native reference/export contract; no Java."""
import importlib.util
from pathlib import Path
import unittest
import json
from PIL import Image

SPEC=importlib.util.spec_from_file_location('native_pose',Path(__file__).with_name('native_pose.py'))
native=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(native)

class NativePoseTests(unittest.TestCase):
    def test_reference_cannot_be_written_in_repository(self):
        with self.assertRaisesRegex(ValueError,'/tmp'):
            native.references(native.ROOT/'target/native-pose-forbidden')

    def test_s2_walk_mapping_preserves_origin(self):
        for index in (15,16,17,18,19,20,13,14):
            image,hotspot,label=native.player(index)
            self.assertEqual(image.getbbox(),(0,0,*image.size))
            self.assertGreater(image.getchannel('A').getextrema()[1],0)
            self.assertTrue(0<=hotspot[0]<image.width)
            self.assertTrue(0<=hotspot[1]<image.height)
            self.assertTrue(label.startswith('Map_Sonic_'))

class PilotHandoffTests(unittest.TestCase):
    def test_native_bounds_palette_and_ink(self):
        art=native.ROOT/'examples/mica-and-talon/art/mica'
        pilot=art/'native-pose-pilot'
        manifest=json.loads((pilot/'frames.yaml').read_text())
        refs={r['id']:r for r in json.loads((pilot/'provenance.json').read_text())['frames']}
        colors=json.loads((art/'palette.yaml').read_text())['colors']
        self.assertEqual(len(manifest['frames']),26)
        for frame in manifest['frames']:
            with self.subTest(frame=frame['id']):
                im=Image.open(pilot/frame['file'])
                self.assertEqual(im.mode,'P')
                self.assertEqual(im.getpalette()[:48],sum(colors,[]))
                self.assertEqual(im.info['transparency'],0)
                self.assertLess(max(im.get_flattened_data()),16)
                self.assertEqual(list(im.size),refs[frame['id']]['size'])
                self.assertEqual(frame['hotspot'],refs[frame['id']]['hotspot'])
                for y in range(im.height):
                    for x in range(im.width):
                        if im.getpixel((x,y)) and any(
                            not (0<=a<im.width and 0<=b<im.height) or im.getpixel((a,b))==0
                            for a,b in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]):
                            self.assertEqual(im.getpixel((x,y)),1)

if __name__=='__main__':unittest.main()
