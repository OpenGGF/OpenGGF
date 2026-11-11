"""Keep the example's powered plumage luminous and its silhouette free of fixed black ink."""
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
ART=ROOT/'examples/mica-and-talon/art'

class PoweredCharacterGlowTest(unittest.TestCase):
    def test_gilded_cycle_never_uses_orange_brown_body_shadows(self):
        for line in (ART/'mica/powered/palettes.txt').read_text().splitlines():
            if not line.startswith(('s2 ', 's3k ')): continue
            words=[int(w,16) for w in line.split()[1:]]
            for index in range(2,7):
                self.assertEqual(14,words[index]&15,(line,index))
                self.assertGreaterEqual((words[index]>>4)&15,12,(line,index))
            self.assertLessEqual(words[2]>>8,words[6]>>8)

    def test_solar_body_is_warm_and_bright_without_brown_cycle_phases(self):
        for line in (ART/'talon/powered/palettes.txt').read_text().splitlines():
            if not line.startswith(('s2 ', 's3k ')): continue
            words=[int(w,16) for w in line.split()[1:]]
            for index in range(2,6):
                self.assertEqual(14,words[index]&15,(line,index))
                self.assertGreaterEqual((words[index]>>4)&15,10,(line,index))

    def test_powered_banks_have_rolling_and_no_fixed_ink_on_the_outer_silhouette(self):
        seen=set()
        for character in ('mica','talon'):
            for game in ('s2','s3k'):
                root=ART/character/'powered'/game
                handoff=json.loads((root/'frames.yaml').read_text())
                self.assertIn('ROLL',handoff['scripts'])
                for frame in handoff['frames']:
                    path=(root/frame['file']).resolve()
                    if path in seen: continue
                    seen.add(path)
                    with Image.open(path) as image:
                        for y in range(image.height):
                            for x in range(image.width):
                                if image.getpixel((x,y))!=1: continue
                                boundary=any(not (0<=a<image.width and 0<=b<image.height) or image.getpixel((a,b))==0
                                             for a,b in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)))
                                self.assertFalse(boundary,(path.name,x,y))

if __name__=='__main__': unittest.main()
