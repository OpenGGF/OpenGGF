"""Whole-lash title patch regression, with seam/root/ownership negative controls.
Inputs: maintained example indexed assets, no ROM. Origin: split lashes report,
2026-11-10. Semantic ink paths supplement real-capture visual review.
"""
import sys,unittest
try:
    import PIL  # noqa: F401  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')
from pathlib import Path
ART=Path(__file__).resolve().parents[2]/'examples/mica-and-talon/art/mica/titles'
sys.path.insert(0,str(ART/'../../../production/mica/titles'))
import s3k_wink_continuity as wink
from s3k_hand_ownership import points_from_spans

class S3kTitleWinkContinuityTest(unittest.TestCase):
    def setUp(self):self.plane,self.frames,self.contract=wink.inputs(ART)
    def test_complete_lashes_and_border_continuity(self):
        self.assertGreater(len(points_from_spans(self.contract['old_lash_rows'])),100)
        self.assertEqual([],wink.errors(self.plane,self.frames,self.contract))
    def test_severed_lash_root_is_rejected(self):
        root=tuple(self.contract['states']['3']['root']);self.frames[3].putpixel(root,5)
        self.assertTrue(any(e[0]=='disconnected lash' for e in wink.errors(self.plane,self.frames,self.contract)))
    def test_unowned_old_lash_is_rejected(self):
        point=next(iter(points_from_spans(self.contract['old_lash_rows'])));self.frames[2].putpixel(point,0)
        self.assertTrue(any(e[0]=='unowned' for e in wink.errors(self.plane,self.frames,self.contract)))
    def test_new_ink_at_context_border_is_rejected(self):
        points=points_from_spans(self.contract['ownership_rows']);point=next(pt for pt in points if any(n not in points for n in wink.neighbours(pt)) and self.frames[2].getpixel(pt) not in (1,2));self.frames[2].putpixel(point,1)
        self.assertTrue(any(e[0]=='ink seam' for e in wink.errors(self.plane,self.frames,self.contract)))
    def test_wink_never_owns_the_hand_region(self):
        import json
        from PIL import Image
        hand=json.loads((ART/'../../../production/mica/titles/s3k-hand-ownership.json').read_text());handpoints={(x+160,y+60) for x,y in points_from_spans(hand['ownership_rows'])};ox,oy=self.contract['origin'];winkpoints={(x+ox,y+oy) for x,y in points_from_spans(self.contract['ownership_rows'])};self.assertFalse(handpoints & winkpoints)
        # Derived poses can extend past the old ownership footprint. They, too,
        # must remain wholly visible when a wink is drawn afterwards.
        for selector in (0,1,4):
            frame=Image.open(ART/f's3k-sprite-{selector}.png');glove={(x+160,y+60) for y in range(frame.height) for x in range(frame.width) if frame.getpixel((x,y))>=8};self.assertFalse(glove & winkpoints)
if __name__=='__main__':unittest.main()
