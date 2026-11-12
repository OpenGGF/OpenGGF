"""Keep the owner-picked powered palettes (Open Cone, Prism Gilt, Corona Crest) and silhouettes free of fixed black ink."""
import colorsys
import json
from pathlib import Path
import unittest
try:
    from PIL import Image  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')

ROOT=Path(__file__).resolve().parents[2]
ART=ROOT/'examples/mica-and-talon/art'
STEPS=(0,36,73,109,146,182,219,255)  # Mega Drive channel levels, as production/powered_palettes.py

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

    @staticmethod
    def prism_phase(words):
        # Classify by the mid plate colour (slot 4), hue first. Jade is green-led: its hue lies past
        # yellow (every gold and white-hot plate sits at or below 60 degrees) and it keeps enough
        # saturation to read as green, however pale -- the owner's jade is a pastel mint, so brightness
        # cannot tell it from white-hot. A mid plate too washed to count as green is white, not jade.
        # Rose is a red-orange hue; everything else is the gold family.
        word=words[4]; r,g,b=((word&15)*255//14,((word>>4)&15)*255//14,((word>>8)&15)*255//14)
        h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
        if 0.19<h<0.6 and s>=0.15: return 'jade'
        if s<0.32 and v>0.9: return 'white'
        if (h<0.07 or h>0.9) and s>0.3: return 'rose'
        return 'gold'

    def assert_prism_rule(self,rows):
        phases=[self.prism_phase(words) for words in rows]
        self.assertTrue({'gold','white','jade','rose'}<=set(phases),phases)
        # Photosensitivity rule: white-hot and jade never meet frame to frame, loop seam included.
        for a,b in zip(phases,phases[1:]+phases[:1]):
            self.assertNotEqual({'white','jade'},{a,b},phases)
        return phases

    def test_prism_gilt_holds_each_phase_and_never_flashes_white_against_jade(self):
        rows=[words for _,words in self.rows('mica',('hyper',))]
        phases=self.assert_prism_rule(rows)
        # The pistachio steps into and out of jade are green-led too, so gold must sit between them
        # and white-hot: three jade rows in a run, with gold on both sides.
        start=phases.index('jade')
        self.assertEqual(['gold','jade','jade','jade','gold'],phases[start-1:start+4],phases)

    def test_prism_rule_rejects_white_beside_jade_and_a_missing_phase(self):
        # The rule must fail on a bad cycle, or the test above proves nothing.
        rows=[words for _,words in self.rows('mica',('hyper',))]
        phases=[self.prism_phase(words) for words in rows]
        white,jade=phases.index('white'),phases.index('jade')
        peak=jade+1                                         # transition, peak, transition
        for bad in (rows[:jade]+[rows[white]]+rows[jade:],          # white-hot straight into the jade run
                    rows[:peak]+[rows[white]]+rows[peak:],          # white-hot against the pastel peak
                    rows[:white+1]+[rows[peak]]+rows[white+1:],     # pastel peak dropped into white-hot
                    [rows[peak]]+rows[:jade]+rows[jade+3:]+[rows[white]]):  # meeting only at the loop seam
            with self.assertRaises(AssertionError,msg=[self.prism_phase(w) for w in bad]):
                self.assert_prism_rule(bad)
        with self.assertRaises(AssertionError):                     # no jade phase at all
            self.assert_prism_rule(rows[:jade]+rows[jade+3:])
        washed=list(rows[peak]); washed[4]=0xCEC                    # a mint too pale to be green is white
        self.assertEqual('white',self.prism_phase(washed))
        with self.assertRaises(AssertionError):
            self.assert_prism_rule(rows[:jade]+[washed]*3+rows[jade+3:])
        deep=list(rows[peak]); deep[4]=0x680                        # the old deep teal still reads as jade
        self.assertEqual('jade',self.prism_phase(deep))

    def test_prism_gilt_jade_is_a_pale_pastel_with_a_readable_plate_ramp(self):
        # Owner feedback 2026-11-12: jade is a pale pastel mint, not a deep teal. Slot 2 stays the darkest
        # plate shade (it is also the plate contour), only much lighter; the plates still step up to slot 6.
        def colour(word): return ((word&15)*255//14,((word>>4)&15)*255//14,((word>>8)&15)*255//14)
        def luma(word): r,g,b=colour(word); return 0.299*r+0.587*g+0.114*b
        jade=[(line,words) for line,words in self.rows('mica',('hyper',)) if self.prism_phase(words)=='jade']
        self.assertEqual(3,len(jade))
        for line,words in jade:
            ramp=[luma(word) for word in words[2:7]]
            self.assertEqual(sorted(ramp),ramp,line)
            self.assertGreaterEqual(ramp[0],100,line)                 # the old underside was 51
            self.assertGreaterEqual(ramp[-1]-ramp[0],90,line)         # plates stay readable
            for word in words[3:7]:
                r,g,b=colour(word); h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
                self.assertGreaterEqual(v,0.7,line); self.assertLessEqual(s,0.5,line)
            r,g,b=colour(words[6])
            self.assertFalse(r==g==b,line)                            # the edge keeps a tint; white-hot's is white
        # The strike holds the jade peak, and its cool ember and fringe are that row's slots 4 and 3.
        effects=(ART/'mica/powered/effects/effects.txt').read_text().splitlines()
        held=[[int(w,16) for w in line.split()[2:]] for line in effects if line.startswith('strike-palette hyper ')]
        self.assertEqual([jade[1][1]],held)
        def hexrgb(word): return '%02X%02X%02X'%tuple(STEPS[c//2] for c in (word&15,(word>>4)&15,(word>>8)&15))
        trail=next(line.split() for line in effects if line.startswith('trail hyper '))
        self.assertEqual(hexrgb(jade[1][1][4]),trail[4])
        self.assertTrue(any(line.startswith('front hyper '+hexrgb(jade[1][1][3])+' ') for line in effects))

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
