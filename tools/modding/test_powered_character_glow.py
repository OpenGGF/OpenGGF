"""Keep the owner-picked powered palettes (Open Cone, Prism Gilt, Corona Crest) and silhouettes free of fixed black ink."""
import json
from pathlib import Path
import unittest
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
ART=ROOT/'examples/mica-and-talon/art'

class PoweredCharacterGlowTest(unittest.TestCase):
    @staticmethod
    def rows(character, banks):
        for line in (ART/character/'powered/palettes.txt').read_text().splitlines():
            if line.startswith(tuple(bank+' ' for bank in banks)):
                yield line, [int(w,16) for w in line.split()[1:]]

    def test_open_cone_plates_keep_a_warm_dark_underside_and_sheet_colours(self):
        # Owner pick 2026-11-11: plates run from a deep amber underside (slot 2) to a pale-gold edge;
        # face, belly, gloves and boots keep MICA's sheet colours in every powered phase.
        sheet=[(b//36*2)<<8|(g//36*2)<<4|(r//36*2) for r,g,b in json.loads((ART/'mica/sheet/palette.yaml').read_text())['colors']]
        for line,words in self.rows('mica',('s2','s3k')):
            self.assertEqual(14,words[3]&15,line)
            self.assertLessEqual((words[2]>>4)&15,8,line)       # underside never pales
            self.assertLessEqual(words[2]>>8,2,line)            # and stays warm
            self.assertLessEqual((words[2]>>4)&15,(words[6]>>4)&15,line)
            self.assertEqual(sheet[7:],words[7:],line)

    def test_prism_gilt_holds_each_phase_and_never_flashes_white_against_jade(self):
        phases=[]
        for line,words in self.rows('mica',('hyper',)):
            body=words[2:7]
            phase='jade' if body[4]>>8>=0xA and body[4]&15<=6 else 'white' if body[4]==0xEEE and body[0]>>8>=4 else \
                'rose' if body[0]&15<=8 and (body[0]>>8)>=2 else 'gold'
            phases.append(phase)
        self.assertTrue({'gold','white','jade','rose'}<=set(phases),phases)
        for a,b in zip(phases,phases[1:]+phases[:1]):
            self.assertNotEqual({'white','jade'},{a,b},phases)

    def test_corona_crest_keeps_dark_bars_and_bright_plumage(self):
        for line,words in self.rows('talon',('s2','s3k')):
            self.assertLessEqual(words[2]&15,6,line)               # dark bronze bars/moustache
            self.assertLessEqual((words[2]>>4)&15,4,line)
            for index in range(3,6):
                self.assertEqual(14,words[index]&15,(line,index))
                self.assertGreaterEqual((words[index]>>4)&15,12,(line,index))
            self.assertEqual(14,words[6]&15,line); self.assertEqual(0,words[6]>>8,line)  # orange flame tip

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
